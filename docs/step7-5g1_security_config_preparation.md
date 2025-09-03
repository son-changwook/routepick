# step7-5g1_security_config_preparation.md

> Step 8 단계를 위한 보안 설정 준비 가이드 - Spring Security, JWT, 암호화, Rate Limiting
> 생성일: 2025-08-25  
> 단계: 7-5g1 (보안 설정 준비)
> 참고: step7-4g, step3-3b, step6-1a, step6-1d

---

## 🎯 Step 8 보안 설정 개요

Step 7에서 구현한 모든 Controller와 DTO가 완성됨에 따라, Step 8에서는 다음 보안 설정들이 필요합니다:

### 🔐 필수 보안 구성 요소
1. **Spring Security 설정** - 인증/인가, CORS, CSRF
2. **JWT 토큰 관리** - 토큰 검증, 갱신, 블랙리스트
3. **Rate Limiting** - API 호출 제한, DDoS 방지
4. **데이터 암호화** - 민감정보 암호화, 개인정보 마스킹
5. **보안 헤더** - XSS, CSRF, 콘텐츠 보안 정책
6. **감사 로깅** - 보안 이벤트 로깅, 접근 추적

---

## 🚀 Step 8에서 구현할 보안 설정들

### 1. Spring Security Configuration

#### SecurityConfig.java
```java
package com.routepick.config.security;

import com.routepick.security.filter.JwtAuthenticationFilter;
import com.routepick.security.filter.RateLimitingFilter;
import com.routepick.security.handler.CustomAuthenticationEntryPoint;
import com.routepick.security.handler.CustomAccessDeniedHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security 설정
 * 
 * 주요 보안 설정:
 * - JWT 인증
 * - CORS 설정
 * - CSRF 보호
 * - Rate Limiting
 * - 보안 헤더
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 설정
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/v1/auth/**", "/api/v1/oauth2/**")
            )
            
            // CORS 설정
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 세션 관리
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 인증 설정
            .authorizeHttpRequests(auth -> auth
                // 공개 엔드포인트
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/email/**").permitAll()
                .requestMatchers("/api/v1/oauth2/**").permitAll()
                .requestMatchers("/health", "/metrics").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // 인증 필요 엔드포인트
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/gyms/*/manage/**").hasAnyRole("ADMIN", "GYM_ADMIN")
                .anyRequest().authenticated()
            )
            
            // 예외 처리
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            
            // 보안 헤더 설정
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)
                )
            )
            
            // 필터 설정
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 허용 origin
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "https://*.routepick.com"
        ));
        
        // 허용 헤더
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // 허용 메서드
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // 인증 정보 포함
        configuration.setAllowCredentials(true);
        
        // 캐시 시간
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

### 2. JWT Token Provider

#### JwtTokenProvider.java
```java
package com.routepick.security.jwt;

import com.routepick.security.service.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT 토큰 생성/검증 프로바이더
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final Duration accessTokenValidityTime;
    private final Duration refreshTokenValidityTime;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-validity}") Duration accessTokenValidityTime,
            @Value("${app.jwt.refresh-token-validity}") Duration refreshTokenValidityTime) {
        
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenValidityTime = accessTokenValidityTime;
        this.refreshTokenValidityTime = refreshTokenValidityTime;
    }

    /**
     * Access Token 생성
     */
    public String generateAccessToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        Date expiryDate = new Date(System.currentTimeMillis() + accessTokenValidityTime.toMillis());

        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .setSubject(userPrincipal.getUserId().toString())
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .claim("authorities", authorities)
                .claim("email", userPrincipal.getEmail())
                .claim("nickname", userPrincipal.getNickname())
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Refresh Token 생성
     */
    public String generateRefreshToken(Long userId) {
        Date expiryDate = new Date(System.currentTimeMillis() + refreshTokenValidityTime.toMillis());

        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .claim("type", "refresh")
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }

    /**
     * 토큰 만료 시간 확인
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
                    
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
```

### 3. 암호화 설정

#### EncryptionConfig.java
```java
package com.routepick.config.security;

import com.routepick.security.encryption.AESUtil;
import com.routepick.security.encryption.DataMaskingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 데이터 암호화 설정
 */
@Configuration
@RequiredArgsConstructor
public class EncryptionConfig {

    @Value("${app.encryption.key}")
    private String encryptionKey;

    @Value("${app.encryption.salt}")
    private String encryptionSalt;

    /**
     * AES 암호화 유틸리티
     */
    @Bean
    public AESUtil aesUtil() {
        return new AESUtil(encryptionKey, encryptionSalt);
    }

    /**
     * 데이터 마스킹 서비스
     */
    @Bean
    public DataMaskingService dataMaskingService() {
        return new DataMaskingService();
    }
}
```