# step8-3 통합: Security Filter Chain 순서 정의

## 🔗 Security Filter Chain 최적화

### 1. SecurityFilterChainOrder 정의
```java
package com.routepick.backend.config.security;

/**
 * Security Filter Chain 순서 정의
 * 숫자가 낮을수록 먼저 실행됨
 */
public class SecurityFilterOrder {
    
    // 1. 기본 요청 검증 (가장 먼저)
    public static final int CORS_FILTER_ORDER = -100;
    public static final int OAUTH2_CORS_FILTER_ORDER = -95;
    public static final int IP_BLOCKING_FILTER_ORDER = -90;
    
    // 2. 보안 헤더 설정
    public static final int SECURITY_HEADERS_FILTER_ORDER = -80;
    public static final int CSP_FILTER_ORDER = -75;
    
    // 3. 요청 검증 및 정화
    public static final int RATE_LIMITING_FILTER_ORDER = -70;
    public static final int XSS_PROTECTION_FILTER_ORDER = -60;
    public static final int INPUT_VALIDATION_FILTER_ORDER = -55;
    
    // 4. 인증/인가 (Spring Security 기본)
    public static final int CSRF_FILTER_ORDER = -50;         // CsrfFilter
    public static final int LOGOUT_FILTER_ORDER = -40;       // LogoutFilter  
    public static final int OAUTH2_FILTER_ORDER = -30;       // OAuth2LoginAuthenticationFilter
    public static final int JWT_FILTER_ORDER = -20;          // JwtAuthenticationFilter
    public static final int BASIC_AUTH_FILTER_ORDER = -10;   // BasicAuthenticationFilter
    
    // 5. 응답 처리 (가장 마지막)
    public static final int DATA_MASKING_FILTER_ORDER = 10;
    public static final int LOGGING_FILTER_ORDER = 20;
    public static final int MONITORING_FILTER_ORDER = 30;
}
```

### 2. 통합 SecurityConfig
```java
package com.routepick.backend.config.security;

import com.routepick.backend.security.filter.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class IntegratedSecurityConfig {
    
    // CORS 설정
    private final CorsConfigurationSource corsConfigurationSource;
    private final CorsConfigurationSource oauth2CorsConfigurationSource;
    
    // 보안 필터들
    private final SecurityHeadersFilter securityHeadersFilter;
    private final XssProtectionFilter xssProtectionFilter;
    private final DataMaskingFilter dataMaskingFilter;
    private final OAuth2CorsFilter oauth2CorsFilter;
    private final RateLimitingFilter rateLimitingFilter;
    
    // JWT 및 CSRF
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomCsrfTokenRepository csrfTokenRepository;
    
    /**
     * 메인 Security Filter Chain
     */
    @Bean
    public SecurityFilterChain mainSecurityFilterChain(HttpSecurity http) throws Exception {
        
        log.info("Security Filter Chain 초기화 시작...");
        
        http
            // 1. Session 설정 (Stateless)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 2. CORS 설정 (가장 먼저)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            
            // 3. CSRF 설정
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .ignoringRequestMatchers(
                    "/api/public/**",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/oauth2/**",
                    "/login/oauth2/**"
                )
            )
            
            // 4. 요청 권한 설정
            .authorizeHttpRequests(authz -> authz
                // Public 엔드포인트
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                
                // Health Check
                .requestMatchers("/actuator/health").permitAll()
                
                // Admin 엔드포인트
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // 나머지는 인증 필요
                .anyRequest().authenticated()
            )
            
            // 5. OAuth2 설정
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization")
                .defaultSuccessUrl("/oauth2/redirect")
                .failureUrl("/oauth2/redirect?error=true")
            )
            
            // 6. Security Filter 순서 설정
            .addFilterBefore(oauth2CorsFilter, CsrfFilter.class)                    // OAuth2 CORS 먼저
            .addFilterBefore(securityHeadersFilter, HeaderWriterFilter.class)       // 보안 헤더
            .addFilterBefore(rateLimitingFilter, CsrfFilter.class)                  // Rate Limiting  
            .addFilterBefore(xssProtectionFilter, UsernamePasswordAuthenticationFilter.class) // XSS 방지
            .addFilterAfter(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class); // JWT 인증
        
        log.info("Security Filter Chain 초기화 완료");
        
        return http.build();
    }
    
    /**
     * Data Masking Filter 등록 (Servlet Filter)
     */
    @Bean
    public FilterRegistrationBean<DataMaskingFilter> dataMaskingFilterRegistration() {
        FilterRegistrationBean<DataMaskingFilter> registration = 
            new FilterRegistrationBean<>(dataMaskingFilter);
        
        registration.addUrlPatterns("/api/*");
        registration.setOrder(SecurityFilterOrder.DATA_MASKING_FILTER_ORDER);
        registration.setName("DataMaskingFilter");
        
        log.info("DataMaskingFilter 등록: Order={}", SecurityFilterOrder.DATA_MASKING_FILTER_ORDER);
        
        return registration;
    }
    
    /**
     * Logging Security Filter 등록
     */
    @Bean  
    public FilterRegistrationBean<LoggingSecurityFilter> loggingSecurityFilterRegistration() {
        FilterRegistrationBean<LoggingSecurityFilter> registration = 
            new FilterRegistrationBean<>(new LoggingSecurityFilter(null));
        
        registration.addUrlPatterns("/api/*", "/oauth2/*", "/login/oauth2/*");
        registration.setOrder(SecurityFilterOrder.LOGGING_FILTER_ORDER);
        registration.setName("LoggingSecurityFilter");
        
        return registration;
    }
    
    /**
     * Rate Limiting Filter 등록 (Servlet Filter)
     */
    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration() {
        FilterRegistrationBean<RateLimitingFilter> registration = 
            new FilterRegistrationBean<>(rateLimitingFilter);
        
        registration.addUrlPatterns("/api/*");
        registration.setOrder(SecurityFilterOrder.RATE_LIMITING_FILTER_ORDER);
        registration.setName("RateLimitingFilter");
        
        return registration;
    }
}
```

### 3. Filter 실행 순서 모니터링
```java
package com.routepick.backend.security.monitor;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class FilterExecutionMonitor implements Filter {
    
    private final ConcurrentHashMap<String, AtomicInteger> filterExecutionCount = new ConcurrentHashMap<>();
    private final ThreadLocal<Long> requestStartTime = new ThreadLocal<>();
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestId = generateRequestId(httpRequest);
        
        requestStartTime.set(System.currentTimeMillis());
        
        log.debug("=== Filter Chain 시작 === RequestID: {}, URI: {}", 
            requestId, httpRequest.getRequestURI());
        
        try {
            // 필터 체인 실행
            chain.doFilter(request, response);
            
        } finally {
            long duration = System.currentTimeMillis() - requestStartTime.get();
            
            log.debug("=== Filter Chain 완료 === RequestID: {}, Duration: {}ms", 
                requestId, duration);
                
            // 성능 통계 수집
            if (duration > 1000) { // 1초 이상
                log.warn("느린 요청 감지: URI={}, Duration={}ms", 
                    httpRequest.getRequestURI(), duration);
            }
            
            requestStartTime.remove();
        }
    }
    
    private String generateRequestId(HttpServletRequest request) {
        return String.format("%s-%s-%d", 
            request.getMethod(),
            request.getRequestURI().hashCode(),
            System.currentTimeMillis() % 10000
        );
    }
    
    /**
     * 필터 실행 통계 조회
     */
    public ConcurrentHashMap<String, AtomicInteger> getFilterExecutionStats() {
        return new ConcurrentHashMap<>(filterExecutionCount);
    }
}
```

### 4. application.yml 필터 설정
```yaml
# Security Filter 설정
security:
  filter:
    order:
      cors: -100
      oauth2-cors: -95  
      security-headers: -80
      rate-limiting: -70
      xss-protection: -60
      csrf: -50
      jwt: -20
      data-masking: 10
      logging: 20
    
    performance:
      slow-request-threshold: 1000  # 1초
      monitoring-enabled: true
      
  chain:
    debug: false  # 운영환경에서는 false
    trace-requests: false
    
logging:
  level:
    com.routepick.backend.security: INFO
    org.springframework.security: WARN
    org.springframework.web.cors: WARN
```