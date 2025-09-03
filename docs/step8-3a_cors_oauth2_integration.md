# step8-3a 보완: OAuth2 CORS 연동 통합

## 🎯 OAuth2 소셜 로그인 CORS 보안 강화

### 1. OAuth2CorsConfig 추가
```java
package com.routepick.backend.config.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class OAuth2CorsConfig {
    
    /**
     * OAuth2 전용 CORS 설정
     * 소셜 로그인 Provider별 특별 처리
     */
    @Bean
    public CorsConfigurationSource oauth2CorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // OAuth2 Provider Origins
        List<String> oauth2Origins = Arrays.asList(
            // Google OAuth2
            "https://accounts.google.com",
            "https://oauth2.googleapis.com",
            
            // Kakao OAuth2  
            "https://kauth.kakao.com",
            "https://kapi.kakao.com",
            
            // Naver OAuth2
            "https://nid.naver.com", 
            "https://openapi.naver.com",
            
            // Facebook OAuth2
            "https://www.facebook.com",
            "https://graph.facebook.com",
            
            // Development (localhost)
            "http://localhost:3000",
            "http://localhost:19006", // Expo dev server
            
            // Production domains
            "https://routepick.co.kr",
            "https://app.routepick.co.kr",
            "https://admin.routepick.co.kr"
        );
        
        configuration.setAllowedOrigins(oauth2Origins);
        
        // OAuth2 전용 메서드 (GET, POST만 허용)
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS"));
        
        // OAuth2 필수 헤더
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type", 
            "Accept",
            "Origin",
            "X-Requested-With",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "X-OAuth-State",
            "X-OAuth-Provider"
        ));
        
        // 인증 정보 포함 허용 (쿠키, Authorization 헤더)
        configuration.setAllowCredentials(true);
        
        // OAuth2는 짧은 캐시 (5분)
        configuration.setMaxAge(300L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        
        // OAuth2 엔드포인트별 설정
        source.registerCorsConfiguration("/oauth2/**", configuration);
        source.registerCorsConfiguration("/login/oauth2/**", configuration);
        source.registerCorsConfiguration("/api/auth/oauth2/**", configuration);
        
        log.info("OAuth2 CORS 설정 완료: {} origins", oauth2Origins.size());
        
        return source;
    }
    
    /**
     * OAuth2 Callback 전용 CORS (더 엄격한 설정)
     */
    @Bean
    public CorsConfigurationSource oauth2CallbackCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Callback은 오직 우리 도메인만 허용
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "https://routepick.co.kr",
            "https://app.routepick.co.kr"
        ));
        
        // Callback은 GET만 허용
        configuration.setAllowedMethods(Arrays.asList("GET", "OPTIONS"));
        
        // 최소한의 헤더만 허용
        configuration.setAllowedHeaders(Arrays.asList(
            "Content-Type",
            "Authorization",
            "X-Requested-With"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(300L); // 5분 캐시
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/login/oauth2/callback/**", configuration);
        
        return source;
    }
}
```

### 2. OAuth2SecurityConfig 통합
```java
package com.routepick.backend.config.security;

import com.routepick.backend.security.oauth2.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class OAuth2SecurityConfig {
    
    private final OAuth2CorsConfig oauth2CorsConfig;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    
    @Bean
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        http
            // OAuth2 전용 CORS 설정
            .cors(cors -> cors
                .configurationSource(oauth2CorsConfig.oauth2CorsConfigurationSource())
            )
            
            // OAuth2 로그인 설정
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization")
                .authorizationEndpoint(authorization -> authorization
                    .baseUri("/oauth2/authorization")
                )
                .redirectionEndpoint(redirection -> redirection
                    .baseUri("/login/oauth2/callback/*")
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            )
            
            // OAuth2 엔드포인트 접근 권한
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/api/auth/oauth2/**").permitAll()
                .anyRequest().authenticated()
            );
            
        return http.build();
    }
}
```

### 3. OAuth2CorsFilter 추가
```java
package com.routepick.backend.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2CorsFilter implements Filter {
    
    // 신뢰할 수 있는 OAuth2 Provider 도메인
    private static final List<String> TRUSTED_OAUTH2_DOMAINS = Arrays.asList(
        "accounts.google.com",
        "kauth.kakao.com", 
        "nid.naver.com",
        "www.facebook.com"
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestURI = httpRequest.getRequestURI();
        
        // OAuth2 관련 요청만 처리
        if (isOAuth2Request(requestURI)) {
            
            String origin = httpRequest.getHeader("Origin");
            String referer = httpRequest.getHeader("Referer");
            
            // OAuth2 Provider에서 오는 요청 검증
            if (isFromTrustedOAuth2Provider(origin, referer)) {
                
                // 특별한 OAuth2 헤더 추가
                httpResponse.addHeader("X-OAuth2-Verified", "true");
                httpResponse.addHeader("X-Frame-Options", "DENY");
                
                log.debug("OAuth2 요청 허용: URI={}, Origin={}", requestURI, origin);
                
            } else {
                log.warn("의심스러운 OAuth2 요청: URI={}, Origin={}, Referer={}", 
                    requestURI, origin, referer);
                
                // 의심스러운 OAuth2 요청 차단
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.getWriter().write("{\"error\":\"OAuth2 request not allowed\"}");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }
    
    private boolean isOAuth2Request(String requestURI) {
        return requestURI.startsWith("/oauth2/") || 
               requestURI.startsWith("/login/oauth2/") ||
               requestURI.contains("/api/auth/oauth2/");
    }
    
    private boolean isFromTrustedOAuth2Provider(String origin, String referer) {
        if (origin == null && referer == null) {
            return false; // 둘 다 없으면 의심스러움
        }
        
        // Origin 검증
        if (origin != null) {
            for (String trustedDomain : TRUSTED_OAUTH2_DOMAINS) {
                if (origin.contains(trustedDomain)) {
                    return true;
                }
            }
        }
        
        // Referer 검증 (Origin이 없는 경우)
        if (referer != null) {
            for (String trustedDomain : TRUSTED_OAUTH2_DOMAINS) {
                if (referer.contains(trustedDomain)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
```