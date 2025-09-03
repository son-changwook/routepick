# Step 7-1h: Rate Limiting 구현체

> @RateLimited 어노테이션 실제 동작 구현  
> 생성일: 2025-08-25  
> 목표: Redis 기반 토큰 버킷 알고리즘으로 엔터프라이즈급 Rate Limiting

---

## 🎯 구현 목표

### 1. 토큰 버킷 알고리즘 구현
### 2. Redis 기반 분산 환경 지원
### 3. 다양한 키 전략 (IP, User, Global)
### 4. 실시간 메트릭 수집
### 5. 동적 설정 변경 지원

---

## 🚀 RateLimitingAspect 구현

### RateLimitingAspect.java
```java
package com.routepick.aspect;

import com.routepick.annotation.RateLimited;
import com.routepick.service.ratelimit.RateLimitingService;
import com.routepick.exception.auth.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Rate Limiting AOP
 * - @RateLimited 어노테이션 처리
 * - 토큰 버킷 알고리즘 적용
 * - 다양한 키 전략 지원
 */
@Slf4j
@Aspect
@Component
@Order(1) // 최우선 실행
@RequiredArgsConstructor
public class RateLimitingAspect {
    
    private final RateLimitingService rateLimitingService;
    
    @Around("@annotation(rateLimited)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        
        // 키 생성
        String key = generateKey(rateLimited.keyStrategy(), joinPoint);
        
        // Rate Limit 검사
        RateLimitResult result = rateLimitingService.isAllowed(
            key,
            rateLimited.requests(),
            rateLimited.period()
        );
        
        if (!result.isAllowed()) {
            log.warn("Rate limit exceeded: key={}, requests={}, period={}, remaining={}", 
                    key, rateLimited.requests(), rateLimited.period(), result.getRemainingTokens());
            
            throw new RateLimitExceededException(
                String.format("Rate limit exceeded. Try again in %d seconds.", 
                    result.getRetryAfterSeconds()),
                result.getRetryAfterSeconds()
            );
        }
        
        log.debug("Rate limit check passed: key={}, remaining={}, resetTime={}", 
                key, result.getRemainingTokens(), result.getResetTime());
        
        // 메서드 실행
        Object response = joinPoint.proceed();
        
        // 성공 메트릭 기록
        rateLimitingService.recordSuccess(key);
        
        return response;
    }
    
    /**
     * Rate Limit 키 생성
     */
    private String generateKey(RateLimited.KeyStrategy strategy, ProceedingJoinPoint joinPoint) {
        String baseKey = joinPoint.getSignature().toShortString();
        
        return switch (strategy) {
            case IP -> generateIpKey(baseKey);
            case USER -> generateUserKey(baseKey);
            case IP_AND_USER -> generateIpUserKey(baseKey);
            case GLOBAL -> baseKey;
        };
    }
    
    /**
     * IP 기반 키 생성
     */
    private String generateIpKey(String baseKey) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = extractClientIp(request);
        return String.format("rate_limit:ip:%s:%s", clientIp, baseKey);
    }
    
    /**
     * 사용자 기반 키 생성
     */
    private String generateUserKey(String baseKey) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String userId = auth.getName();
            return String.format("rate_limit:user:%s:%s", userId, baseKey);
        }
        
        // 인증되지 않은 경우 IP로 폴백
        return generateIpKey(baseKey);
    }
    
    /**
     * IP + 사용자 조합 키 생성
     */
    private String generateIpUserKey(String baseKey) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = extractClientIp(request);
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String userId = auth.getName();
            return String.format("rate_limit:ip_user:%s:%s:%s", clientIp, userId, baseKey);
        }
        
        return String.format("rate_limit:ip:%s:%s", clientIp, baseKey);
    }
    
    /**
     * 현재 HTTP 요청 가져오기
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("No current HTTP request found");
        }
        return attrs.getRequest();
    }
    
    /**
     * 클라이언트 IP 추출
     */
    private String extractClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP", 
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR",
            "X-Real-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
```

---

## ⚙️ RateLimitingService 구현

### RateLimitingService.java
```java
package com.routepick.service.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Rate Limiting 서비스
 * - 토큰 버킷 알고리즘 구현
 * - 분산 환경 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitingService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> rateLimitScript;
    
    /**
     * Rate Limit 검사
     */
    public RateLimitResult isAllowed(String key, int maxRequests, int windowSeconds) {
        
        long windowStart = Instant.now().getEpochSecond();
        long windowEnd = windowStart + windowSeconds;
        
        // Lua 스크립트로 원자성 보장
        List<Long> result = redisTemplate.execute(
            rateLimitScript,
            Arrays.asList(key),
            String.valueOf(maxRequests),
            String.valueOf(windowStart),
            String.valueOf(windowEnd),
            String.valueOf(windowSeconds)
        );
        
        if (result == null || result.size() < 3) {
            log.error("Rate limit script returned invalid result: {}", result);
            return RateLimitResult.denied(0, windowSeconds);
        }
        
        long currentCount = result.get(0);
        long resetTime = result.get(1);
        long allowed = result.get(2);
        
        boolean isAllowed = allowed == 1;
        int remainingTokens = Math.max(0, (int) (maxRequests - currentCount));
        int retryAfterSeconds = isAllowed ? 0 : (int) (resetTime - windowStart);
        
        // 메트릭 기록
        recordMetrics(key, isAllowed, remainingTokens);
        
        return RateLimitResult.builder()
            .allowed(isAllowed)
            .remainingTokens(remainingTokens)
            .resetTime(resetTime)
            .retryAfterSeconds(retryAfterSeconds)
            .build();
    }
    
    /**
     * 성공 요청 기록
     */
    public void recordSuccess(String key) {
        String successKey = key + ":success";
        redisTemplate.opsForValue().increment(successKey);
        redisTemplate.expire(successKey, 1, TimeUnit.HOURS);
    }
    
    /**
     * 메트릭 기록
     */
    private void recordMetrics(String key, boolean allowed, int remainingTokens) {
        // 성공/실패 카운터
        String metricKey = allowed ? key + ":allowed" : key + ":denied";
        redisTemplate.opsForValue().increment(metricKey);
        redisTemplate.expire(metricKey, 1, TimeUnit.HOURS);
        
        // 남은 토큰 수 기록
        String tokenKey = key + ":tokens";
        redisTemplate.opsForValue().set(tokenKey, String.valueOf(remainingTokens), 1, TimeUnit.HOURS);
    }
    
    /**
     * Rate Limit 정보 조회
     */
    public RateLimitInfo getRateLimitInfo(String key) {
        String allowedKey = key + ":allowed";
        String deniedKey = key + ":denied";
        String successKey = key + ":success";
        String tokenKey = key + ":tokens";
        
        String allowed = redisTemplate.opsForValue().get(allowedKey);
        String denied = redisTemplate.opsForValue().get(deniedKey);
        String success = redisTemplate.opsForValue().get(successKey);
        String tokens = redisTemplate.opsForValue().get(tokenKey);
        
        return RateLimitInfo.builder()
            .key(key)
            .allowedRequests(allowed != null ? Long.parseLong(allowed) : 0)
            .deniedRequests(denied != null ? Long.parseLong(denied) : 0)
            .successfulRequests(success != null ? Long.parseLong(success) : 0)
            .remainingTokens(tokens != null ? Integer.parseInt(tokens) : 0)
            .build();
    }
}
```

---

## 📜 Redis Lua 스크립트

### RateLimitScript.java
```java
package com.routepick.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

/**
 * Redis Lua 스크립트 설정
 */
@Configuration
public class RateLimitScriptConfig {
    
    @Bean
    public RedisScript<List> rateLimitScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate_limit.lua")));
        script.setResultType(List.class);
        return script;
    }
}
```

### rate_limit.lua
```lua
-- Rate Limiting Lua Script (Token Bucket Algorithm)
-- KEYS[1]: rate limit key
-- ARGV[1]: max requests
-- ARGV[2]: current window start
-- ARGV[3]: current window end  
-- ARGV[4]: window seconds

local key = KEYS[1]
local max_requests = tonumber(ARGV[1])
local current_time = tonumber(ARGV[2])
local window_end = tonumber(ARGV[3])
local window_seconds = tonumber(ARGV[4])

-- Redis keys
local count_key = key .. ":count"
local reset_key = key .. ":reset"

-- Get current count and reset time
local current_count = redis.call('GET', count_key)
local reset_time = redis.call('GET', reset_key)

-- Initialize if not exists
if not current_count then
    current_count = 0
    reset_time = window_end
else
    current_count = tonumber(current_count)
    reset_time = tonumber(reset_time)
end

-- Check if window has expired
if current_time >= reset_time then
    -- Reset window
    current_count = 0
    reset_time = window_end
    redis.call('DEL', count_key)
    redis.call('DEL', reset_key)
end

-- Check if request is allowed
local allowed = 0
if current_count < max_requests then
    -- Allow request
    allowed = 1
    current_count = current_count + 1
    
    -- Update counters
    redis.call('SET', count_key, current_count)
    redis.call('EXPIRE', count_key, window_seconds)
    redis.call('SET', reset_key, reset_time)
    redis.call('EXPIRE', reset_key, window_seconds)
end

-- Return: [current_count, reset_time, allowed]
return {current_count, reset_time, allowed}
```

---

## 📊 Rate Limit 결과 객체

### RateLimitResult.java
```java
package com.routepick.service.ratelimit;

import lombok.Builder;
import lombok.Getter;

/**
 * Rate Limit 검사 결과
 */
@Getter
@Builder
public class RateLimitResult {
    
    private final boolean allowed;
    private final int remainingTokens;
    private final long resetTime;
    private final int retryAfterSeconds;
    
    /**
     * 허용된 결과 생성
     */
    public static RateLimitResult allowed(int remainingTokens, long resetTime) {
        return RateLimitResult.builder()
            .allowed(true)
            .remainingTokens(remainingTokens)
            .resetTime(resetTime)
            .retryAfterSeconds(0)
            .build();
    }
    
    /**
     * 거부된 결과 생성
     */
    public static RateLimitResult denied(int retryAfterSeconds, long resetTime) {
        return RateLimitResult.builder()
            .allowed(false)
            .remainingTokens(0)
            .resetTime(resetTime)
            .retryAfterSeconds(retryAfterSeconds)
            .build();
    }
}
```

### RateLimitInfo.java
```java
package com.routepick.service.ratelimit;

import lombok.Builder;
import lombok.Getter;

/**
 * Rate Limit 통계 정보
 */
@Getter
@Builder
public class RateLimitInfo {
    
    private final String key;
    private final long allowedRequests;
    private final long deniedRequests;
    private final long successfulRequests;
    private final int remainingTokens;
    
    /**
     * 성공률 계산
     */
    public double getSuccessRate() {
        long total = allowedRequests + deniedRequests;
        return total > 0 ? (double) allowedRequests / total : 0.0;
    }
    
    /**
     * 요청 완료율 계산
     */
    public double getCompletionRate() {
        return allowedRequests > 0 ? (double) successfulRequests / allowedRequests : 0.0;
    }
}
```

---

## ⚠️ 예외 처리

### RateLimitExceededException.java
```java
package com.routepick.exception.auth;

import com.routepick.exception.BaseException;
import com.routepick.common.enums.ErrorCode;

/**
 * Rate Limit 초과 예외
 */
public class RateLimitExceededException extends BaseException {
    
    private final int retryAfterSeconds;
    
    public RateLimitExceededException(String message, int retryAfterSeconds) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
```

---

## 🎛️ 설정

### application.yml
```yaml
security:
  rate-limit:
    enabled: true
    default-requests: 100
    default-period: 60
    
  login:
    max-attempts: 5
    lockout-duration-minutes: 30
    ip-block-threshold: 10

spring:
  redis:
    rate-limit:
      database: 1  # Rate limiting 전용 DB
```

---

## 🔧 GlobalExceptionHandler 업데이트

### GlobalExceptionHandler.java (추가)
```java
/**
 * Rate Limit 예외 처리
 */
@ExceptionHandler(RateLimitExceededException.class)
public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(
        RateLimitExceededException ex, HttpServletRequest request) {
    
    log.warn("Rate limit exceeded: path={}, ip={}, retryAfter={}", 
            request.getRequestURI(), getClientIp(request), ex.getRetryAfterSeconds());
    
    // Retry-After 헤더 설정
    HttpHeaders headers = new HttpHeaders();
    headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
    headers.add("X-RateLimit-Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
    
    ApiResponse<Void> response = ApiResponse.error(
        ex.getErrorCode().name(),
        ex.getMessage()
    );
    
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .headers(headers)
            .body(response);
}
```

---

## 📈 모니터링 API

### RateLimitController.java
```java
package com.routepick.controller.admin;

import com.routepick.common.ApiResponse;
import com.routepick.service.ratelimit.RateLimitingService;
import com.routepick.service.ratelimit.RateLimitInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Rate Limit 모니터링 API (관리자용)
 */
@RestController
@RequestMapping("/api/v1/admin/rate-limit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RateLimitController {
    
    private final RateLimitingService rateLimitingService;
    
    /**
     * Rate Limit 정보 조회
     */
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<RateLimitInfo>> getRateLimitInfo(
            @RequestParam String key) {
        
        RateLimitInfo info = rateLimitingService.getRateLimitInfo(key);
        return ResponseEntity.ok(ApiResponse.success(info));
    }
}
```

---

*Step 7-1h 완료: Rate Limiting 구현체 (토큰 버킷 + Redis + Lua)*