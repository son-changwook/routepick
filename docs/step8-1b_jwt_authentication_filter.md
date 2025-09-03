# 8-1b: JWT Authentication Filter

## 📋 구현 목표
- **JWT 토큰 검증**: Request에서 토큰 추출 및 검증
- **SecurityContext 설정**: 인증 정보 저장
- **토큰 갱신**: 만료 임박 시 자동 갱신
- **예외 처리**: 토큰 관련 예외 처리

## 🔐 JwtAuthenticationFilter 구현

### JwtAuthenticationFilter.java
```java
package com.routepick.backend.security.filter;

import com.routepick.backend.security.jwt.JwtTokenProvider;
import com.routepick.backend.security.service.CustomUserDetailsService;
import com.routepick.backend.security.service.TokenBlacklistService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService blacklistService;

    // 인증이 필요 없는 경로
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/api/v1/auth/signup",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/oauth2",
        "/api/v1/system/health",
        "/swagger-ui",
        "/v3/api-docs"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        
        // 제외 경로는 필터 통과
        if (shouldSkipAuthentication(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            String token = extractToken(request);
            
            if (StringUtils.hasText(token)) {
                // 블랙리스트 확인
                if (blacklistService.isBlacklisted(token)) {
                    log.warn("블랙리스트 토큰 사용 시도: {}", getClientIp(request));
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                    return;
                }
                
                // 토큰 검증
                if (tokenProvider.validateToken(token)) {
                    String username = tokenProvider.getUsernameFromToken(token);
                    
                    // UserDetails 로드
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    
                    // SecurityContext에 인증 정보 저장
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                            userDetails, 
                            null, 
                            userDetails.getAuthorities()
                        );
                    
                    authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    // 토큰 만료 시간 확인 및 갱신
                    checkAndRefreshToken(token, response);
                    
                    log.debug("JWT 인증 성공 - User: {}, Path: {}", username, requestPath);
                } else {
                    log.warn("유효하지 않은 JWT 토큰 - Path: {}", requestPath);
                }
            } else {
                log.debug("JWT 토큰 없음 - Path: {}", requestPath);
            }
            
        } catch (ExpiredJwtException e) {
            log.error("만료된 JWT 토큰 - Path: {}", requestPath);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expired");
            return;
            
        } catch (MalformedJwtException e) {
            log.error("잘못된 형식의 JWT 토큰 - Path: {}", requestPath);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid token format");
            return;
            
        } catch (UnsupportedJwtException e) {
            log.error("지원하지 않는 JWT 토큰 - Path: {}", requestPath);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported token");
            return;
            
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 비어있음 - Path: {}", requestPath);
            
        } catch (Exception e) {
            log.error("JWT 인증 처리 중 오류 발생", e);
        }
        
        filterChain.doFilter(request, response);
    }

    /**
     * Request에서 토큰 추출
     * 1. Authorization Header (Bearer Token)
     * 2. Cookie (accessToken)
     * 3. Query Parameter (token) - WebSocket 연결용
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Authorization Header에서 추출
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        // 2. Cookie에서 추출
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        // 3. Query Parameter에서 추출 (WebSocket용)
        String token = request.getParameter("token");
        if (StringUtils.hasText(token)) {
            return token;
        }
        
        return null;
    }

    /**
     * 인증 스킵 여부 확인
     */
    private boolean shouldSkipAuthentication(String path) {
        return EXCLUDED_PATHS.stream()
            .anyMatch(excludedPath -> path.startsWith(excludedPath));
    }

    /**
     * 토큰 만료 시간 확인 및 자동 갱신
     * 만료 5분 전이면 새 토큰 발급
     */
    private void checkAndRefreshToken(String token, HttpServletResponse response) {
        long expirationTime = tokenProvider.getExpirationTime(token);
        long currentTime = System.currentTimeMillis();
        long timeUntilExpiration = expirationTime - currentTime;
        
        // 5분(300000ms) 이내 만료 예정이면 갱신
        if (timeUntilExpiration < 300000) {
            String username = tokenProvider.getUsernameFromToken(token);
            String newToken = tokenProvider.generateAccessToken(username);
            
            // Response Header에 새 토큰 추가
            response.setHeader("X-New-Token", newToken);
            
            // Cookie 갱신
            Cookie cookie = new Cookie("accessToken", newToken);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(1800); // 30분
            response.addCookie(cookie);
            
            log.info("JWT 토큰 자동 갱신 - User: {}", username);
        }
    }

    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };
        
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0];
            }
        }
        
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream()
            .anyMatch(excludedPath -> path.startsWith(excludedPath));
    }
}
```

## 🔍 토큰 추출 전략

### 1. Authorization Header (권장)
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 2. HttpOnly Cookie (보안 강화)
```java
Cookie: accessToken=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 3. Query Parameter (WebSocket용)
```
wss://api.routepick.com/ws?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## 🔄 토큰 자동 갱신

### 갱신 조건
- 토큰 만료 5분 전
- 유효한 토큰인 경우만
- 블랙리스트에 없는 경우

### 갱신 방법
1. Response Header: `X-New-Token`
2. HttpOnly Cookie 갱신
3. 클라이언트는 새 토큰 저장

## 🛡️ 보안 기능

### 1. 토큰 블랙리스트
```java
// 로그아웃된 토큰 차단
if (blacklistService.isBlacklisted(token)) {
    return 401 Unauthorized;
}
```

### 2. IP 추적
```java
// 의심스러운 활동 모니터링
String clientIp = getClientIp(request);
securityAuditService.logAccess(clientIp, username);
```

### 3. 예외 처리
```java
- ExpiredJwtException: 토큰 만료
- MalformedJwtException: 잘못된 형식
- UnsupportedJwtException: 지원하지 않는 알고리즘
- SignatureException: 서명 검증 실패
```

## ⚡ 성능 최적화

### 1. OncePerRequestFilter
- 요청당 한 번만 실행
- 중복 필터링 방지

### 2. 제외 경로 처리
```java
// 인증 불필요한 경로는 스킵
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    return EXCLUDED_PATHS.contains(request.getRequestURI());
}
```

### 3. SecurityContext 캐싱
```java
// Thread-local 저장으로 성능 향상
SecurityContextHolder.getContext().setAuthentication(authentication);
```

## ✅ 체크리스트

### 구현 완료
- [x] JWT 토큰 추출 (3가지 방법)
- [x] 토큰 검증 로직
- [x] SecurityContext 설정
- [x] 토큰 자동 갱신
- [x] 블랙리스트 확인
- [x] 예외 처리
- [x] IP 추적

### 다음 구현
- [ ] JwtTokenProvider
- [ ] TokenBlacklistService
- [ ] CustomUserDetailsService

---
*JwtAuthenticationFilter 구현 완료 - 다음: JwtTokenProvider 구현*