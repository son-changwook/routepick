# 8-1d: JWT Properties & Security Headers

## 📋 구현 목표
- **JWT Properties**: 설정값 외부화 및 관리
- **Security Headers Filter**: 보안 헤더 적용
- **Token Cookie Config**: HttpOnly Cookie 설정
- **Security Constants**: 보안 상수 관리

## 🔐 JwtProperties 구현

### JwtProperties.java
```java
package com.routepick.backend.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    
    /**
     * JWT 서명 키 (Base64 인코딩)
     * 최소 256 bits (32 bytes) 이상
     */
    @NotBlank(message = "JWT secret key는 필수입니다")
    private String secret;
    
    /**
     * Access Token 유효 시간 (초)
     * 기본값: 1800 (30분)
     */
    @NotNull
    @Min(value = 60, message = "Access token 유효시간은 최소 60초 이상이어야 합니다")
    private Long accessTokenValiditySeconds = 1800L;
    
    /**
     * Refresh Token 유효 시간 (초)
     * 기본값: 604800 (7일)
     */
    @NotNull
    @Min(value = 3600, message = "Refresh token 유효시간은 최소 1시간 이상이어야 합니다")
    private Long refreshTokenValiditySeconds = 604800L;
    
    /**
     * Token 발행자
     */
    @NotBlank
    private String issuer = "routepick";
    
    /**
     * Token 헤더 이름
     */
    private String headerName = "Authorization";
    
    /**
     * Token 접두사
     */
    private String tokenPrefix = "Bearer ";
    
    /**
     * Cookie 설정
     */
    private CookieProperties cookie = new CookieProperties();
    
    /**
     * 토큰 자동 갱신 시간 (초)
     * 만료 전 갱신할 시간
     */
    @Min(value = 60)
    private Long autoRefreshBeforeSeconds = 300L; // 5분
    
    /**
     * 블랙리스트 TTL (초)
     * Redis에 저장될 블랙리스트 토큰의 TTL
     */
    @Min(value = 0)
    private Long blacklistTtlSeconds = 3600L; // 1시간
    
    @Getter
    @Setter
    public static class CookieProperties {
        /**
         * Cookie 이름
         */
        private String name = "accessToken";
        
        /**
         * HttpOnly 설정
         */
        private boolean httpOnly = true;
        
        /**
         * Secure 설정 (HTTPS only)
         */
        private boolean secure = true;
        
        /**
         * SameSite 설정
         */
        private String sameSite = "Strict";
        
        /**
         * Cookie 경로
         */
        private String path = "/";
        
        /**
         * Cookie 도메인
         */
        private String domain;
        
        /**
         * Max-Age (초)
         */
        private Integer maxAge;
    }
    
    /**
     * Access Token 유효시간 (밀리초)
     */
    public long getAccessTokenValidityMs() {
        return accessTokenValiditySeconds * 1000;
    }
    
    /**
     * Refresh Token 유효시간 (밀리초)
     */
    public long getRefreshTokenValidityMs() {
        return refreshTokenValiditySeconds * 1000;
    }
    
    /**
     * 자동 갱신 시간 (밀리초)
     */
    public long getAutoRefreshBeforeMs() {
        return autoRefreshBeforeSeconds * 1000;
    }
}
```

### SecurityHeadersFilter.java
```java
package com.routepick.backend.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // XSS Protection
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // Content Type Options
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // Frame Options
        response.setHeader("X-Frame-Options", "DENY");
        
        // HSTS (HTTP Strict Transport Security)
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", 
                "max-age=31536000; includeSubDomains; preload");
        }
        
        // Content Security Policy
        response.setHeader("Content-Security-Policy", 
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; " +
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
            "font-src 'self' https://fonts.gstatic.com; " +
            "img-src 'self' data: https:; " +
            "connect-src 'self' https://api.routepick.com wss://ws.routepick.com; " +
            "frame-ancestors 'none';"
        );
        
        // Referrer Policy
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Permissions Policy
        response.setHeader("Permissions-Policy", 
            "geolocation=(self), " +
            "camera=(), " +
            "microphone=(), " +
            "payment=(self), " +
            "usb=()"
        );
        
        // Cache Control for sensitive data
        if (isApiEndpoint(request)) {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isApiEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/");
    }
}
```

### SecurityConstants.java
```java
package com.routepick.backend.security.constant;

public final class SecurityConstants {
    
    private SecurityConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // Token Types
    public static final String ACCESS_TOKEN_TYPE = "ACCESS";
    public static final String REFRESH_TOKEN_TYPE = "REFRESH";
    
    // Authorities
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_GYM_OWNER = "ROLE_GYM_OWNER";
    public static final String ROLE_SYSTEM_MANAGER = "ROLE_SYSTEM_MANAGER";
    
    // Headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String NEW_TOKEN_HEADER = "X-New-Token";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    
    // Cookie Names
    public static final String ACCESS_TOKEN_COOKIE = "accessToken";
    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    
    // Redis Keys
    public static final String BLACKLIST_PREFIX = "blacklist:token:";
    public static final String REFRESH_TOKEN_PREFIX = "refresh:token:";
    public static final String RATE_LIMIT_PREFIX = "rate:limit:";
    public static final String LOGIN_ATTEMPT_PREFIX = "login:attempt:";
    
    // Security Events
    public static final String EVENT_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String EVENT_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String EVENT_LOGOUT = "LOGOUT";
    public static final String EVENT_TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String EVENT_TOKEN_INVALID = "TOKEN_INVALID";
    public static final String EVENT_ACCESS_DENIED = "ACCESS_DENIED";
    
    // Rate Limiting
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final long LOGIN_ATTEMPT_TIMEOUT = 900; // 15 minutes
    public static final int GLOBAL_RATE_LIMIT = 1000;
    public static final int USER_RATE_LIMIT = 100;
    public static final int AUTH_RATE_LIMIT = 5;
    
    // Password Policy
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 128;
    public static final int BCRYPT_STRENGTH = 12;
    
    // Session
    public static final long SESSION_TIMEOUT = 1800; // 30 minutes
    public static final String SESSION_USER_KEY = "session:user:";
}
```

### TokenCookieUtil.java
```java
package com.routepick.backend.security.util;

import com.routepick.backend.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TokenCookieUtil {
    
    private final JwtProperties jwtProperties;
    
    /**
     * Access Token Cookie 생성
     */
    public void setAccessTokenCookie(HttpServletResponse response, String token) {
        JwtProperties.CookieProperties cookieProps = jwtProperties.getCookie();
        
        ResponseCookie cookie = ResponseCookie.from(cookieProps.getName(), token)
            .httpOnly(cookieProps.isHttpOnly())
            .secure(cookieProps.isSecure())
            .sameSite(cookieProps.getSameSite())
            .path(cookieProps.getPath())
            .maxAge(Duration.ofSeconds(jwtProperties.getAccessTokenValiditySeconds()))
            .build();
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    /**
     * Refresh Token Cookie 생성
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/v1/auth/refresh")
            .maxAge(Duration.ofSeconds(jwtProperties.getRefreshTokenValiditySeconds()))
            .build();
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    /**
     * Token Cookie 삭제
     */
    public void deleteTokenCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from(jwtProperties.getCookie().getName(), "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build();
        
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/v1/auth/refresh")
            .maxAge(0)
            .build();
        
        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
    }
}
```

## ⚙️ Application Properties

### application.yml
```yaml
jwt:
  secret: ${JWT_SECRET:dGhpcy1pcy1hLXZlcnktbG9uZy1zZWNyZXQta2V5LWZvci1yb3V0ZXBpY2stand0LXRva2VuLWdlbmVyYXRpb24}
  access-token-validity-seconds: 1800      # 30분
  refresh-token-validity-seconds: 604800   # 7일
  issuer: routepick
  auto-refresh-before-seconds: 300         # 5분
  blacklist-ttl-seconds: 3600             # 1시간
  cookie:
    name: accessToken
    http-only: true
    secure: true
    same-site: Strict
    path: /
    domain: ${COOKIE_DOMAIN:}
```

### application-prod.yml
```yaml
jwt:
  secret: ${JWT_SECRET}  # 환경변수에서 가져오기
  cookie:
    secure: true
    domain: .routepick.com  # 서브도메인 공유
```

### application-dev.yml
```yaml
jwt:
  access-token-validity-seconds: 3600  # 개발시 1시간
  cookie:
    secure: false  # 개발시 HTTP 허용
    domain: localhost
```

## 🔒 보안 헤더 설명

### 1. XSS Protection
```
X-XSS-Protection: 1; mode=block
```
- XSS 공격 차단

### 2. Content Type Options
```
X-Content-Type-Options: nosniff
```
- MIME 타입 스니핑 방지

### 3. Frame Options
```
X-Frame-Options: DENY
```
- Clickjacking 방지

### 4. HSTS
```
Strict-Transport-Security: max-age=31536000
```
- HTTPS 강제

### 5. CSP
```
Content-Security-Policy: default-src 'self'
```
- 리소스 로딩 제한

## ✅ Phase 1 (8-1) 완료!

### 설계 완료
- [x] SecurityConfig - Spring Security 설정
- [x] JwtAuthenticationFilter - JWT 인증 필터
- [x] JwtTokenProvider - 토큰 생성/검증
- [x] JwtProperties - JWT 설정 관리
- [x] SecurityHeadersFilter - 보안 헤더
- [x] SecurityConstants - 보안 상수
- [x] TokenCookieUtil - Cookie 관리

### 다음 Phase (8-2)
- [ ] CustomUserDetailsService
- [ ] OAuth2SuccessHandler
- [ ] AccessDeniedHandler
- [ ] AuthenticationEntryPoint

---
*Phase 1 (JWT & Security Configuration) 완료! - 다음: Phase 2 (Authentication Services)*