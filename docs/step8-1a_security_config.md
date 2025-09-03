# 8-1a: Spring Security 6.x Configuration

## 📋 구현 목표
- **Spring Security 6.x**: 최신 보안 설정 적용
- **JWT 인증**: 토큰 기반 Stateless 인증
- **Method Security**: @PreAuthorize, @PostAuthorize 활성화
- **Password Encoding**: BCrypt 암호화

## 🔐 SecurityConfig 구현

### SecurityConfig.java
```java
package com.routepick.backend.config.security;

import com.routepick.backend.security.filter.JwtAuthenticationFilter;
import com.routepick.backend.security.handler.CustomAccessDeniedHandler;
import com.routepick.backend.security.handler.CustomAuthenticationEntryPoint;
import com.routepick.backend.security.oauth2.CustomOAuth2UserService;
import com.routepick.backend.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.routepick.backend.security.oauth2.OAuth2AuthenticationFailureHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
    prePostEnabled = true,  // @PreAuthorize, @PostAuthorize 활성화
    securedEnabled = true,  // @Secured 활성화
    jsr250Enabled = true    // @RolesAllowed 활성화
)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;

    /**
     * Spring Security 6.x SecurityFilterChain 설정
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 비활성화 (JWT 사용)
            .csrf(AbstractHttpConfigurer::disable)
            
            // CORS 설정
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 세션 정책: STATELESS (JWT 사용)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 요청 인가 규칙
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/oauth2/**",
                    "/api/v1/system/health",
                    "/api/v1/system/agreements",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                
                // Admin endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/system/api-configs").hasAnyRole("ADMIN", "SYSTEM_MANAGER")
                
                // Gym owner endpoints
                .requestMatchers("/api/v1/gym/manage/**").hasAnyRole("GYM_OWNER", "ADMIN")
                
                // Authenticated endpoints
                .anyRequest().authenticated()
            )
            
            // OAuth2 로그인 설정
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint -> endpoint
                    .baseUri("/oauth2/authorize")
                )
                .redirectionEndpoint(endpoint -> endpoint
                    .baseUri("/oauth2/callback/*")
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            )
            
            // 예외 처리
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            
            // JWT 필터 추가
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    /**
     * Password Encoder Bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // 강도 12 (기본 10보다 강력)
    }

    /**
     * AuthenticationManager Bean
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * CORS Configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 허용 출처
        configuration.setAllowedOrigins(Arrays.asList(
            "https://routepick.com",
            "https://admin.routepick.com",
            "http://localhost:3000",      // React Admin
            "http://localhost:8081"       // React Native
        ));
        
        // 허용 메서드
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // 허용 헤더
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "X-CSRF-Token"
        ));
        
        // 노출 헤더
        configuration.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "Authorization",
            "X-Total-Count"
        ));
        
        // 인증 정보 포함 허용
        configuration.setAllowCredentials(true);
        
        // Pre-flight 캐시 시간 (1시간)
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
```

## 🛡️ Security 설정 상세

### 1. Method Security 활성화
```java
@EnableMethodSecurity(
    prePostEnabled = true,  // @PreAuthorize, @PostAuthorize
    securedEnabled = true,  // @Secured
    jsr250Enabled = true    // @RolesAllowed
)
```

### 2. 인가 규칙 체계
```java
// Public endpoints (인증 불필요)
- /api/v1/auth/** : 로그인, 회원가입
- /api/v1/oauth2/** : 소셜 로그인
- /api/v1/system/health : 헬스체크
- /swagger-ui/** : API 문서

// Role-based endpoints
- ROLE_ADMIN : 시스템 관리
- ROLE_GYM_OWNER : 체육관 관리
- ROLE_USER : 일반 사용자
```

### 3. OAuth2 Provider 지원
```java
// 지원 Provider
- Google
- Kakao
- Naver
- Facebook

// Callback URL
/oauth2/callback/{provider}
```

### 4. CORS 정책
```java
// Production
https://routepick.com
https://admin.routepick.com

// Development
http://localhost:3000 (Admin Web)
http://localhost:8081 (Mobile App)
```

## 🔒 보안 강화 기능

### 1. Password Encoding
```java
// BCrypt with strength 12
- 기본값(10)보다 강력한 암호화
- Salt 자동 생성
- Rainbow Table 공격 방어
```

### 2. Session Management
```java
// STATELESS 정책
- 서버 세션 미사용
- JWT 토큰 기반 인증
- 확장성 향상
```

### 3. Security Headers (다음 파일에서 구현)
```java
// SecurityHeadersFilter에서 추가 예정
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000
```

## ✅ 체크리스트

### 구현 완료
- [x] Spring Security 6.x FilterChain 설정
- [x] JWT 필터 체인 구성
- [x] OAuth2 로그인 설정
- [x] CORS 정책 설정
- [x] Method Security 활성화
- [x] Password Encoder 설정
- [x] AuthenticationManager Bean 등록

### 다음 구현
- [ ] JwtAuthenticationFilter
- [ ] CustomAuthenticationEntryPoint
- [ ] CustomAccessDeniedHandler
- [ ] OAuth2 Handlers

---
*SecurityConfig 구현 완료 - 다음: JwtAuthenticationFilter 구현*