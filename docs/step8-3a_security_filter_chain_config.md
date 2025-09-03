# Step 8-3a: Security Filter Chain 핵심 설정

> Spring Security 통합 Filter Chain 및 순서 최적화  
> 생성일: 2025-08-21  
> 단계: 8-3a (Security 설정 - Filter Chain 핵심)  
> 참고: step8-1a, step8-2a, step8-3

---

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
            
            // 2. CORS 설정 (최우선)
            .cors(cors -> cors
                .configurationSource(corsConfigurationSource)
            )
            
            // 3. CSRF 보안 설정
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .ignoringRequestMatchers(
                    "/api/auth/login",
                    "/api/auth/signup", 
                    "/api/auth/refresh",
                    "/api/webhooks/**",
                    "/actuator/**"
                )
            )
            
            // 4. 인증 없이 접근 가능한 URL
            .authorizeHttpRequests(authz -> authz
                // 공개 API
                .requestMatchers("/", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                
                // OAuth2 관련
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                
                // Actuator (내부 네트워크만)
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                
                // 개발/테스트 전용 (Profile별 제어)
                .requestMatchers("/api/dev/**").hasRole("DEVELOPER")
                .requestMatchers("/h2-console/**").hasRole("DEVELOPER")
                
                // 그 외 모든 API는 인증 필요
                .anyRequest().authenticated()
            )
            
            // 5. OAuth2 Login 설정
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization/provider")
                .redirectionEndpoint(redirection -> redirection
                    .baseUri("/login/oauth2/code/*")
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(oauth2SuccessHandler)
                .failureHandler(oauth2FailureHandler)
            )
            
            // 6. 로그아웃 설정
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(customLogoutSuccessHandler)
                .deleteCookies("JSESSIONID", "remember-me")
                .invalidateHttpSession(true)
            )
            
            // 7. Exception Handling
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(customAuthenticationEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler)
            )
            
            // 8. 커스텀 필터 추가 (순서 중요!)
            .addFilterBefore(oauth2CorsFilter, CsrfFilter.class)
            .addFilterBefore(xssProtectionFilter, CsrfFilter.class)  
            .addFilterBefore(securityHeadersFilter, HeaderWriterFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // 9. 헤더 설정 (X-Frame-Options, X-Content-Type-Options 등)
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)
                )
            );
        
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

---

## 🔧 Filter Chain 구성 원칙

### **1. 실행 순서 최적화**
- **CORS 필터**: 가장 먼저 (-100) - preflight 요청 처리
- **보안 헤더**: 요청 초기 (-80) - 기본 보안 헤더 설정
- **Rate Limiting**: 요청 검증 (-70) - 과도한 요청 차단
- **XSS 보호**: 입력 검증 (-60) - 악성 스크립트 필터링
- **CSRF 보호**: Spring Security 기본 (-50)
- **JWT 인증**: 사용자 인증 (-20)
- **Data Masking**: 응답 후처리 (10) - 민감정보 마스킹

### **2. Performance 고려사항**
- **Early Exit**: 무거운 필터는 뒤쪽 배치
- **Caching**: 인증 결과 캐싱으로 중복 처리 방지
- **Async Processing**: 로깅 등 비동기 처리 가능한 작업 분리

### **3. 보안 계층화**
- **Network Layer**: CORS, IP Blocking
- **Request Layer**: Rate Limiting, Input Validation
- **Authentication Layer**: JWT, OAuth2
- **Response Layer**: Data Masking, Logging

---

## 🚀 **다음 단계**

**step8-3b 모니터링 연계:**
- Filter 실행 순서 모니터링
- 성능 통계 수집
- 보안 이벤트 로깅

*step8-3a 완성: Security Filter Chain 핵심 구성 완료*