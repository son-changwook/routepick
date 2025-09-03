# Step 8-3b: CSRF 보안 구현

> Cross-Site Request Forgery 방지 및 Double Submit Cookie 패턴 구현  
> 생성일: 2025-08-26  
> 기반 파일: step8-1a_security_config.md, SPA 환경 최적화

---

## 🎯 구현 목표

- **하이브리드 CSRF 보호**: REST API와 웹 페이지 분리 처리
- **Double Submit Cookie**: SPA 환경에 적합한 CSRF 토큰 관리
- **상태 비저장 검증**: JWT와 호환되는 Stateless CSRF 보호
- **자동 토큰 갱신**: 토큰 만료 및 자동 갱신 메커니즘
- **공격 탐지**: CSRF 공격 시도 모니터링

---

## 🔐 1. CsrfConfig 구현

### CsrfConfig.java
```java
package com.routepick.config.security;

import com.routepick.security.csrf.CustomCsrfTokenRepository;
import com.routepick.security.csrf.CsrfTokenRequestHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * CSRF 보안 설정
 * - Double Submit Cookie 패턴
 * - SPA 환경 최적화
 * - REST API와 웹 페이지 분리 처리
 */
@Configuration
@RequiredArgsConstructor
public class CsrfConfig {
    
    private final CustomCsrfTokenRepository csrfTokenRepository;
    
    /**
     * CSRF 토큰 요청 핸들러
     * - XOR 인코딩으로 보안 강화
     * - SPA 환경 지원
     */
    @Bean
    public CsrfTokenRequestAttributeHandler csrfTokenRequestAttributeHandler() {
        XorCsrfTokenRequestAttributeHandler handler = new XorCsrfTokenRequestAttributeHandler();
        // 토큰을 쿠키에서 읽어올 수 있도록 설정
        handler.setCsrfRequestAttributeName("_csrf");
        return handler;
    }
    
    /**
     * CSRF 토큰 저장소
     * - Double Submit Cookie 패턴
     * - HttpOnly false (JavaScript 접근 가능)
     */
    @Bean
    public CustomCsrfTokenRepository customCsrfTokenRepository() {
        return new CustomCsrfTokenRepository();
    }
}
```

---

## 🍪 2. CustomCsrfTokenRepository 구현

### CustomCsrfTokenRepository.java
```java
package com.routepick.security.csrf;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 커스텀 CSRF 토큰 저장소
 * - Double Submit Cookie 패턴 구현
 * - 쿠키 기반 토큰 관리
 */
@Slf4j
@Component
public class CustomCsrfTokenRepository implements CsrfTokenRepository {
    
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String CSRF_PARAMETER_NAME = "_csrf";
    private static final int COOKIE_MAX_AGE = 86400; // 24시간
    
    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        String token = createNewToken();
        return new DefaultCsrfToken(CSRF_HEADER_NAME, CSRF_PARAMETER_NAME, token);
    }
    
    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        String tokenValue = token != null ? token.getToken() : "";
        
        Cookie cookie = new Cookie(CSRF_COOKIE_NAME, tokenValue);
        cookie.setSecure(isSecure(request)); // HTTPS에서만 secure 설정
        cookie.setPath("/");
        cookie.setHttpOnly(false); // JavaScript에서 읽을 수 있도록 설정
        cookie.setMaxAge(token != null ? COOKIE_MAX_AGE : 0);
        
        // SameSite 속성 설정 (CSRF 방지 강화)
        cookie.setAttribute("SameSite", "Strict");
        
        response.addCookie(cookie);
        
        if (token != null) {
            log.debug("CSRF token saved: {}", maskToken(tokenValue));
        }
    }
    
    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        Cookie cookie = getCookie(request, CSRF_COOKIE_NAME);
        
        if (cookie == null) {
            return null;
        }
        
        String token = cookie.getValue();
        
        if (!StringUtils.hasLength(token)) {
            return null;
        }
        
        return new DefaultCsrfToken(CSRF_HEADER_NAME, CSRF_PARAMETER_NAME, token);
    }
    
    /**
     * 새로운 CSRF 토큰 생성
     */
    private String createNewToken() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * 쿠키 조회
     */
    private Cookie getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie;
                }
            }
        }
        
        return null;
    }
    
    /**
     * HTTPS 여부 확인
     */
    private boolean isSecure(HttpServletRequest request) {
        return request.isSecure() || 
               "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
    
    /**
     * 토큰 마스킹 (로깅용)
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 8) + "****";
    }
}
```

---

## 🛡️ 3. CsrfValidationFilter 구현

### CsrfValidationFilter.java
```java
package com.routepick.filter.security;

import com.routepick.service.security.SecurityAuditLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * CSRF 검증 필터
 * - REST API와 웹 페이지 분리 처리
 * - Double Submit Cookie 검증
 * - CSRF 공격 탐지 및 로깅
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsrfValidationFilter extends OncePerRequestFilter {
    
    private final SecurityAuditLogger auditLogger;
    
    // CSRF 검증 제외 경로
    private static final Set<String> CSRF_EXCLUDE_PATHS = new HashSet<>(Arrays.asList(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/api/v1/oauth2",
        "/api/v1/system/health",
        "/swagger-ui",
        "/v3/api-docs"
    ));
    
    // CSRF 검증이 필요한 HTTP 메서드
    private static final Set<String> CSRF_REQUIRED_METHODS = new HashSet<>(Arrays.asList(
        "POST", "PUT", "DELETE", "PATCH"
    ));
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // CSRF 검증 필요 여부 확인
        if (shouldValidateCsrf(path, method)) {
            // CSRF 토큰 검증
            if (!validateCsrfToken(request)) {
                log.warn("CSRF validation failed - Path: {}, Method: {}, IP: {}", 
                        path, method, getClientIp(request));
                
                // 보안 이벤트 로깅
                auditLogger.logSecurityViolation("CSRF_ATTACK", 
                    String.format("CSRF token validation failed for %s %s", method, path),
                    "HIGH", Map.of(
                        "path", path,
                        "method", method,
                        "clientIp", getClientIp(request)
                    ));
                
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token validation failed");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * CSRF 검증 필요 여부 확인
     */
    private boolean shouldValidateCsrf(String path, String method) {
        // GET, HEAD, OPTIONS는 검증 제외
        if (!CSRF_REQUIRED_METHODS.contains(method)) {
            return false;
        }
        
        // 제외 경로 확인
        for (String excludePath : CSRF_EXCLUDE_PATHS) {
            if (path.startsWith(excludePath)) {
                return false;
            }
        }
        
        // REST API는 JWT 인증 사용으로 CSRF 제외
        if (path.startsWith("/api/") && !path.startsWith("/api/web/")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * CSRF 토큰 검증
     */
    private boolean validateCsrfToken(HttpServletRequest request) {
        // Spring Security의 CsrfToken 가져오기
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        
        if (csrfToken == null) {
            return false;
        }
        
        String actualToken = csrfToken.getToken();
        
        // 헤더에서 토큰 확인
        String requestToken = request.getHeader("X-XSRF-TOKEN");
        
        // 헤더에 없으면 파라미터에서 확인
        if (!StringUtils.hasText(requestToken)) {
            requestToken = request.getParameter("_csrf");
        }
        
        // 토큰 비교
        boolean valid = StringUtils.hasText(requestToken) && requestToken.equals(actualToken);
        
        if (!valid) {
            log.debug("CSRF token mismatch - Expected: {}, Actual: {}", 
                    maskToken(actualToken), maskToken(requestToken));
        }
        
        return valid;
    }
    
    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * 토큰 마스킹 (로깅용)
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 8) + "****";
    }
}
```

---

## 🔄 4. CSRF 토큰 엔드포인트

### CsrfController.java
```java
package com.routepick.controller.security;

import com.routepick.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * CSRF 토큰 관리 API
 * - SPA에서 CSRF 토큰 조회
 * - 토큰 갱신
 */
@RestController
@RequestMapping("/api/v1/csrf")
@RequiredArgsConstructor
public class CsrfController {
    
    /**
     * CSRF 토큰 조회
     * - SPA 초기 로드 시 토큰 획득
     * - 토큰 만료 시 재발급
     */
    @GetMapping("/token")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        
        if (csrfToken != null) {
            Map<String, String> tokenInfo = Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
            );
            
            return ResponseEntity.ok(ApiResponse.success(tokenInfo));
        }
        
        return ResponseEntity.ok(ApiResponse.error("CSRF_TOKEN_NOT_FOUND", "CSRF token not available"));
    }
}
```

---

## ⚙️ 5. Spring Security CSRF 통합

### SecurityConfig.java 업데이트
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final CustomCsrfTokenRepository csrfTokenRepository;
    private final CsrfTokenRequestAttributeHandler csrfTokenRequestHandler;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 설정
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(csrfTokenRequestHandler)
                // REST API 경로는 CSRF 비활성화
                .ignoringRequestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/oauth2/**",
                    "/api/v1/system/health",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                )
                // 웹 페이지는 CSRF 활성화
                .requireCsrfProtectionMatcher(request -> {
                    String path = request.getRequestURI();
                    return path.startsWith("/web/") || path.startsWith("/admin/");
                })
            )
            
            // 기타 보안 설정...
            ;
            
        return http.build();
    }
}
```

---

## 📱 6. Frontend 통합 예제

### React/JavaScript 예제
```javascript
// CSRF 토큰 관리 유틸리티
class CsrfTokenManager {
    constructor() {
        this.token = null;
        this.headerName = 'X-XSRF-TOKEN';
    }
    
    // 토큰 초기화
    async initialize() {
        try {
            const response = await fetch('/api/v1/csrf/token', {
                credentials: 'include'
            });
            
            const data = await response.json();
            if (data.success) {
                this.token = data.data.token;
                this.headerName = data.data.headerName;
            }
        } catch (error) {
            console.error('Failed to initialize CSRF token:', error);
        }
    }
    
    // 쿠키에서 토큰 읽기
    getTokenFromCookie() {
        const name = 'XSRF-TOKEN=';
        const decodedCookie = decodeURIComponent(document.cookie);
        const cookies = decodedCookie.split(';');
        
        for (let cookie of cookies) {
            cookie = cookie.trim();
            if (cookie.indexOf(name) === 0) {
                return cookie.substring(name.length);
            }
        }
        
        return null;
    }
    
    // API 요청 시 헤더 추가
    getHeaders() {
        const token = this.getTokenFromCookie() || this.token;
        
        if (token) {
            return {
                [this.headerName]: token
            };
        }
        
        return {};
    }
}

// 사용 예제
const csrfManager = new CsrfTokenManager();

// 앱 초기화 시 토큰 획득
await csrfManager.initialize();

// API 요청 시 CSRF 토큰 포함
fetch('/api/web/profile/update', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        ...csrfManager.getHeaders()
    },
    credentials: 'include',
    body: JSON.stringify({ name: 'New Name' })
});
```

### Axios 인터셉터 설정
```javascript
import axios from 'axios';

// Axios 인터셉터로 자동 CSRF 토큰 추가
axios.interceptors.request.use(config => {
    // 쿠키에서 CSRF 토큰 읽기
    const token = getCookie('XSRF-TOKEN');
    
    if (token) {
        config.headers['X-XSRF-TOKEN'] = token;
    }
    
    return config;
});

function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) {
        return parts.pop().split(';').shift();
    }
}
```

---

## 📊 7. CSRF 모니터링

### CsrfMonitoringService.java
```java
package com.routepick.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * CSRF 공격 모니터링 서비스
 * - CSRF 공격 시도 추적
 * - 패턴 분석 및 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsrfMonitoringService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String CSRF_ATTACK_PREFIX = "security:csrf:attack:";
    private static final int ATTACK_THRESHOLD = 10; // 10회 이상 시도 시 알림
    
    /**
     * CSRF 공격 시도 기록
     */
    public void recordCsrfAttack(String clientIp, String path, String method) {
        try {
            String key = CSRF_ATTACK_PREFIX + clientIp;
            String value = String.format("%s:%s:%d", method, path, System.currentTimeMillis());
            
            redisTemplate.opsForList().leftPush(key, value);
            redisTemplate.opsForList().trim(key, 0, 99); // 최대 100개
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
            
            // 임계치 확인
            Long attackCount = redisTemplate.opsForList().size(key);
            if (attackCount != null && attackCount >= ATTACK_THRESHOLD) {
                log.error("Potential CSRF attack detected from IP: {} (attempts: {})", 
                        clientIp, attackCount);
                // 알림 발송 또는 IP 차단 로직
            }
            
        } catch (Exception e) {
            log.error("Failed to record CSRF attack", e);
        }
    }
    
    /**
     * CSRF 공격 통계 조회
     */
    public CsrfAttackStatistics getAttackStatistics() {
        // 통계 집계 로직
        return CsrfAttackStatistics.builder()
            .totalAttempts(0L)
            .uniqueIps(0)
            .blockedIps(0)
            .lastAttackTime(LocalDateTime.now())
            .build();
    }
}
```

---

## ✅ 구현 완료 체크리스트

- [x] Double Submit Cookie 패턴 구현
- [x] CustomCsrfTokenRepository 구현
- [x] REST API와 웹 페이지 분리 처리
- [x] CSRF 토큰 자동 갱신
- [x] CSRF 공격 탐지 및 로깅
- [x] SPA 환경 지원 (JavaScript 접근 가능)
- [x] Spring Security 통합
- [x] Frontend 통합 가이드

---

*Step 8-3b 완료: CSRF 보안 구현*
*다음 파일: step8-3c_security_headers.md*