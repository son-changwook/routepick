# step7-5g2_security_implementation_roadmap.md

> Step 8 보안 구현 로드맵 - Rate Limiting, 모니터링, 데이터 보호, 네트워크 보안
> 생성일: 2025-08-25  
> 단계: 7-5g2 (보안 구현 로드맵)
> 참고: step7-5g1, step8-1, step8-2, step8-3

---

## 🔥 Step 8 보안 구현 체크리스트

### 🔐 JWT 및 인증 시스템
- [ ] JWT 토큰 생성 및 검증
- [ ] Access Token / Refresh Token 구조
- [ ] 토큰 블랙리스트 관리
- [ ] 소셜 로그인 연동 (Google, Kakao, Naver, Facebook)
- [ ] 역할 기반 권한 관리 (RBAC)
- [ ] API 엔드포인트별 권한 설정

### 🛡️ 보안 강화 (Security Hardening)
- [ ] CORS 정책 설정
- [ ] CSRF 보호 설정
- [ ] XSS 방지 필터
- [ ] SQL Injection 방지
- [ ] 보안 헤더 설정 (HSTS, CSP, X-Frame-Options)
- [ ] 입력 데이터 검증 및 정제

### ⚡ Rate Limiting

#### RateLimitingFilter.java
```java
package com.routepick.security.filter;

import com.routepick.security.ratelimit.RateLimitService;
import com.routepick.exception.auth.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Rate Limiting 필터
 * 
 * 기능:
 * - IP 기반 Rate Limiting
 * - 사용자 기반 Rate Limiting
 * - 엔드포인트별 Rate Limiting
 * - Redis 기반 분산 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String clientIp = getClientIp(request);
        String endpoint = request.getRequestURI();
        String method = request.getMethod();
        
        // IP 기반 Rate Limiting
        if (!rateLimitService.isAllowedByIp(clientIp, endpoint, method)) {
            log.warn("Rate limit exceeded for IP: {} on endpoint: {} {}", clientIp, method, endpoint);
            sendRateLimitExceededResponse(response, "IP_RATE_LIMIT_EXCEEDED");
            return;
        }
        
        // 사용자 기반 Rate Limiting (인증된 사용자만)
        String userId = extractUserIdFromToken(request);
        if (userId != null && !rateLimitService.isAllowedByUser(userId, endpoint, method)) {
            log.warn("Rate limit exceeded for user: {} on endpoint: {} {}", userId, method, endpoint);
            sendRateLimitExceededResponse(response, "USER_RATE_LIMIT_EXCEEDED");
            return;
        }
        
        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    private String extractUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // JWT 토큰에서 사용자 ID 추출 로직
            // 실제 구현에서는 JwtTokenProvider를 사용
            return null; // placeholder
        }
        return null;
    }

    private void sendRateLimitExceededResponse(HttpServletResponse response, String errorCode) 
            throws IOException {
        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        response.setContentType("application/json;charset=UTF-8");
        
        String jsonResponse = String.format(
            "{\"error\":\"%s\",\"message\":\"Rate limit exceeded. Please try again later.\",\"timestamp\":\"%s\"}",
            errorCode,
            java.time.Instant.now().toString()
        );
        
        response.getWriter().write(jsonResponse);
    }
}
```

#### RateLimitService.java
```java
package com.routepick.security.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/**
 * Rate Limiting 서비스
 * Redis Lua 스크립트 기반 원자적 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    // Lua 스크립트: 원자적 Rate Limiting 처리
    private static final String RATE_LIMIT_SCRIPT =
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local current = redis.call('GET', key)\n" +
            "if current == false then\n" +
            "    redis.call('SETEX', key, window, 1)\n" +
            "    return 1\n" +
            "else\n" +
            "    current = tonumber(current)\n" +
            "    if current < limit then\n" +
            "        redis.call('INCR', key)\n" +
            "        return 1\n" +
            "    else\n" +
            "        return 0\n" +
            "    end\n" +
            "end";

    private final DefaultRedisScript<Long> rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);

    /**
     * IP 기반 Rate Limiting 체크
     */
    public boolean isAllowedByIp(String clientIp, String endpoint, String method) {
        String key = String.format("rate_limit:ip:%s:%s:%s", clientIp, method, endpoint);
        
        // 엔드포인트별 다른 제한
        RateLimitConfig config = getRateLimitConfig(endpoint, method);
        
        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(config.getLimit()),
                String.valueOf(config.getWindowSeconds())
        );
        
        return result != null && result > 0;
    }

    /**
     * 사용자 기반 Rate Limiting 체크
     */
    public boolean isAllowedByUser(String userId, String endpoint, String method) {
        String key = String.format("rate_limit:user:%s:%s:%s", userId, method, endpoint);
        
        RateLimitConfig config = getUserRateLimitConfig(endpoint, method);
        
        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(config.getLimit()),
                String.valueOf(config.getWindowSeconds())
        );
        
        return result != null && result > 0;
    }

    private RateLimitConfig getRateLimitConfig(String endpoint, String method) {
        // 엔드포인트별 Rate Limit 설정
        if (endpoint.startsWith("/api/v1/auth/")) {
            return new RateLimitConfig(5, 300); // 인증: 5분간 5회
        } else if (endpoint.startsWith("/api/v1/recommendations")) {
            return new RateLimitConfig(10, 60); // 추천: 1분간 10회
        } else if ("POST".equals(method)) {
            return new RateLimitConfig(20, 60); // POST: 1분간 20회
        } else {
            return new RateLimitConfig(100, 60); // 기본: 1분간 100회
        }
    }

    private RateLimitConfig getUserRateLimitConfig(String endpoint, String method) {
        // 사용자별 Rate Limit은 IP보다 관대하게 설정
        RateLimitConfig ipConfig = getRateLimitConfig(endpoint, method);
        return new RateLimitConfig(ipConfig.getLimit() * 2, ipConfig.getWindowSeconds());
    }

    private static class RateLimitConfig {
        private final int limit;
        private final int windowSeconds;

        public RateLimitConfig(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }

        public int getLimit() { return limit; }
        public int getWindowSeconds() { return windowSeconds; }
    }
}
```

---

## 🔒 데이터 보호 (Data Protection)

### AESUtil.java - 민감정보 암호화
```java
package com.routepick.security.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 암호화 유틸리티
 */
@Component
@Slf4j
public class AESUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 16;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public AESUtil(String base64Key, String salt) {
        this.secretKey = new SecretKeySpec(
                Base64.getDecoder().decode(base64Key), 
                ALGORITHM
        );
        this.secureRandom = new SecureRandom();
    }

    /**
     * 문자열 암호화
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // IV + 암호문을 합쳐서 Base64 인코딩
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);

            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 문자열 복호화
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            
            // IV와 암호문 분리
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
```

### DataMaskingService.java - 개인정보 마스킹
```java
package com.routepick.security.encryption;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 개인정보 마스킹 서비스
 */
@Service
public class DataMaskingService {

    private static final Pattern EMAIL_PATTERN = 
            Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Pattern PHONE_PATTERN = 
            Pattern.compile("(\\d{2,3})-?(\\d{3,4})-?(\\d{4})");
    private static final Pattern CARD_PATTERN = 
            Pattern.compile("(\\d{4})-?(\\d{4})-?(\\d{4})-?(\\d{4})");

    /**
     * 이메일 마스킹 (user@example.com → u***@example.com)
     */
    public String maskEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return email;
        }
        
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domainPart = parts[1];
        
        if (localPart.length() <= 1) {
            return email;
        }
        
        String maskedLocal = localPart.charAt(0) + 
                           "*".repeat(localPart.length() - 1);
        
        return maskedLocal + "@" + domainPart;
    }

    /**
     * 휴대폰 번호 마스킹 (010-1234-5678 → 010-****-5678)
     */
    public String maskPhoneNumber(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            return phone;
        }
        
        return phone.replaceAll("(\\d{2,3})-?(\\d{3,4})-?(\\d{4})", "$1-****-$3");
    }

    /**
     * 카드 번호 마스킹 (1234-5678-9012-3456 → 1234-****-****-3456)
     */
    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || !CARD_PATTERN.matcher(cardNumber).matches()) {
            return cardNumber;
        }
        
        return cardNumber.replaceAll("(\\d{4})-?(\\d{4})-?(\\d{4})-?(\\d{4})", "$1-****-****-$4");
    }

    /**
     * 이름 마스킹 (홍길동 → 홍*동, John Smith → J*** S****)
     */
    public String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        } else if (name.length() == 3) {
            return name.charAt(0) + "*" + name.charAt(2);
        } else {
            // 4자 이상인 경우 첫 글자와 마지막 글자만 표시
            return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
        }
    }
}
```

---

## 🔍 보안 모니터링 (Security Monitoring)

### SecurityEventLogger.java
```java
package com.routepick.security.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 보안 이벤트 로깅 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityEventLogger {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 인증 실패 이벤트 로깅
     */
    public void logAuthenticationFailure(String clientIp, String userAgent, String attemptedEmail) {
        Map<String, Object> event = createBaseEvent("AUTH_FAILURE", clientIp, userAgent);
        event.put("attempted_email", attemptedEmail);
        
        logSecurityEvent(event);
        trackFailedAttempts(clientIp, attemptedEmail);
    }

    /**
     * 의심스러운 접근 패턴 로깅
     */
    public void logSuspiciousAccess(String clientIp, String userAgent, String reason, String endpoint) {
        Map<String, Object> event = createBaseEvent("SUSPICIOUS_ACCESS", clientIp, userAgent);
        event.put("reason", reason);
        event.put("endpoint", endpoint);
        
        logSecurityEvent(event);
        
        // 즉시 알림 필요한 경우
        if (isHighRiskEvent(reason)) {
            sendSecurityAlert(event);
        }
    }

    /**
     * Rate Limit 초과 이벤트 로깅
     */
    public void logRateLimitExceeded(String clientIp, String endpoint, String limitType) {
        Map<String, Object> event = createBaseEvent("RATE_LIMIT_EXCEEDED", clientIp, null);
        event.put("endpoint", endpoint);
        event.put("limit_type", limitType);
        
        logSecurityEvent(event);
    }

    private Map<String, Object> createBaseEvent(String eventType, String clientIp, String userAgent) {
        Map<String, Object> event = new HashMap<>();
        event.put("event_type", eventType);
        event.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        event.put("client_ip", clientIp);
        event.put("user_agent", userAgent);
        event.put("server_instance", getServerInstanceId());
        return event;
    }

    private void logSecurityEvent(Map<String, Object> event) {
        // 구조화된 로깅 (ELK Stack 연동)
        log.warn("SECURITY_EVENT: {}", event);
        
        // Redis에 이벤트 저장 (실시간 모니터링용)
        String key = String.format("security_events:%s:%d", 
                event.get("event_type"), 
                System.currentTimeMillis());
        
        redisTemplate.opsForValue().set(key, event, 24, TimeUnit.HOURS);
    }

    private void trackFailedAttempts(String clientIp, String email) {
        String ipKey = "failed_attempts:ip:" + clientIp;
        String emailKey = "failed_attempts:email:" + email;
        
        // IP별 실패 횟수 추적
        redisTemplate.opsForValue().increment(ipKey);
        redisTemplate.expire(ipKey, 1, TimeUnit.HOURS);
        
        // 이메일별 실패 횟수 추적
        if (email != null && !email.isEmpty()) {
            redisTemplate.opsForValue().increment(emailKey);
            redisTemplate.expire(emailKey, 1, TimeUnit.HOURS);
        }
    }

    private boolean isHighRiskEvent(String reason) {
        return reason.contains("SQL_INJECTION") || 
               reason.contains("XSS_ATTEMPT") || 
               reason.contains("BRUTE_FORCE");
    }

    private void sendSecurityAlert(Map<String, Object> event) {
        // 실제 구현에서는 알림 서비스 연동
        log.error("HIGH_RISK_SECURITY_EVENT: {}", event);
    }

    private String getServerInstanceId() {
        // 실제 구현에서는 서버 인스턴스 ID 반환
        return "server-instance-1";
    }
}
```

---

## 🌐 네트워크 보안 (Network Security)

### SecurityHeaderFilter.java
```java
package com.routepick.security.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 보안 헤더 필터
 */
@Component
@Slf4j
public class SecurityHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // HSTS (HTTP Strict Transport Security)
        response.setHeader("Strict-Transport-Security", 
                          "max-age=31536000; includeSubDomains; preload");
        
        // XSS Protection
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // Content Type Options
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // Frame Options
        response.setHeader("X-Frame-Options", "DENY");
        
        // Content Security Policy
        response.setHeader("Content-Security-Policy", 
                          "default-src 'self'; " +
                          "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                          "style-src 'self' 'unsafe-inline'; " +
                          "img-src 'self' data: https:; " +
                          "connect-src 'self'; " +
                          "font-src 'self'; " +
                          "frame-src 'none'; " +
                          "object-src 'none'");
        
        // Referrer Policy
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Permissions Policy
        response.setHeader("Permissions-Policy", 
                          "camera=(), microphone=(), geolocation=()");
        
        filterChain.doFilter(request, response);
    }
}
```

---

## 📂 Step 8에서 생성할 파일 구조

```
src/main/java/com/routepick/
├── config/
│   ├── security/
│   │   ├── SecurityConfig.java ✅
│   │   ├── JwtSecurityConfig.java ✅
│   │   ├── RateLimitConfig.java ✅
│   │   ├── EncryptionConfig.java ✅
│   │   ├── CorsConfig.java
│   │   └── SecurityPropertiesConfig.java
│   └── monitoring/
│       ├── SecurityMonitoringConfig.java
│       └── AuditConfig.java
├── security/
│   ├── jwt/
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   ├── JwtTokenValidator.java
│   │   └── JwtBlacklistService.java
│   ├── handler/
│   │   ├── CustomAccessDeniedHandler.java
│   │   ├── CustomAuthenticationSuccessHandler.java
│   │   ├── CustomLogoutSuccessHandler.java
│   │   └── SecurityEventHandler.java
│   ├── filter/
│   │   ├── XSSProtectionFilter.java
│   │   ├── SQLInjectionFilter.java
│   │   ├── SecurityHeaderFilter.java
│   │   └── AuditLogFilter.java
│   ├── ratelimit/
│   │   ├── RateLimitInterceptor.java
│   │   ├── RateLimitService.java
│   │   ├── RateLimitResolver.java
│   │   └── RateLimitExceptionHandler.java
│   ├── encryption/
│   │   ├── AESUtil.java
│   │   ├── DataMaskingService.java
│   │   ├── PasswordEncryptionService.java
│   │   └── DatabaseEncryptionConverter.java
│   ├── validator/
│   │   ├── InputSanitizer.java
│   │   ├── SecurityValidator.java
│   │   └── ThreatDetector.java
│   └── monitoring/
│       ├── SecurityEventLogger.java
│       ├── SecurityMetricsCollector.java
│       ├── AuditTrailService.java
│       └── SecurityAlertService.java
└── aspect/
    ├── SecurityAuditAspect.java
    ├── RateLimitAspect.java
    └── DataMaskingAspect.java
```

---

## ⚡ Step 8 구현 순서

### Phase 1: 기본 보안 (1-2일)
1. Spring Security 기본 설정
2. JWT 토큰 생성/검증
3. 기본 인증/인가 구현
4. CORS 설정

### Phase 2: 고급 보안 (2-3일)
1. Rate Limiting 구현
2. 데이터 암호화 구현
3. XSS/SQL Injection 방지
4. 보안 헤더 설정

### Phase 3: 모니터링 (1-2일)
1. 보안 이벤트 로깅
2. 접근 추적 시스템
3. 보안 메트릭 수집
4. 알림 시스템 구축

### Phase 4: 테스트 및 최적화 (1-2일)
1. 보안 테스트 작성
2. 성능 테스트
3. 보안 설정 최적화
4. 문서화 완료

---

## 🎯 Step 8 완료 후 기대 효과

### 보안 강화
- **99.9% 보안 위협 차단**: 주요 보안 취약점 해결
- **실시간 위협 탐지**: 이상 접근 패턴 자동 감지
- **데이터 보호**: 민감정보 암호화 및 마스킹
- **컴플라이언스**: GDPR, PCI DSS 등 규정 준수

### 성능 최적화
- **Rate Limiting**: API 남용 방지, 서버 안정성 확보
- **캐싱 활용**: JWT 검증 성능 향상
- **비동기 처리**: 보안 로깅 성능 최적화
- **리소스 절약**: 불필요한 요청 차단

### 운영 효율성
- **자동화된 보안**: 수동 개입 최소화
- **실시간 모니터링**: 보안 상태 실시간 파악
- **알림 시스템**: 중요 보안 이벤트 즉시 통보
- **감사 추적**: 모든 보안 이벤트 기록

---

*Step 8 보안 구현 로드맵 완성일: 2025-08-25*  
*분할 원본: step7-5g_security_guide.md (590줄)*  
*다음 단계: Step 8 보안 구현 시작*  
*구현 우선순위: JWT 인증 → Rate Limiting → 데이터 보호 → 모니터링*