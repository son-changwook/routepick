# Step 8-2a: Rate Limiting 구현

> Redis + Bucket4j 기반 Rate Limiting 및 접근 제한 설정 구현  
> 생성일: 2025-08-26  
> 기반 파일: step7-1g_rate_limiting.md, step6-6a_api_log_service.md

---

## 🎯 구현 목표

- **Redis 기반 Rate Limiting**: 토큰 버킷 알고리즘으로 분산 환경 지원
- **@RateLimited 어노테이션**: AOP 기반 메서드 레벨 제한
- **다중 키 전략**: IP, 사용자, 글로벌 기반 제한
- **동적 정책 설정**: API별 차등 제한 정책
- **실시간 모니터링**: 메트릭 수집 및 알림

---

## 📋 1. @RateLimited 어노테이션

### RateLimited.java
```java
package com.routepick.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rate Limiting 어노테이션
 * - 메서드 레벨 Rate Limiting 적용
 * - 다양한 키 전략 지원
 * - 동적 제한 설정
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    
    /**
     * 허용 요청 수 (기본: 100)
     */
    int requests() default 100;
    
    /**
     * 시간 윈도우 (초 단위, 기본: 60초)
     */
    int period() default 60;
    
    /**
     * 키 생성 전략
     */
    KeyStrategy keyStrategy() default KeyStrategy.IP;
    
    /**
     * 설명 (로깅용)
     */
    String description() default "";
    
    /**
     * 키 생성 전략 열거형
     */
    enum KeyStrategy {
        IP,           // IP 기반
        USER,         // 사용자 기반
        IP_AND_USER,  // IP + 사용자 조합
        GLOBAL        // 전역
    }
}
```

---

## 🚀 2. RateLimitingAspect 구현

### RateLimitingAspect.java
```java
package com.routepick.aspect;

import com.routepick.annotation.RateLimited;
import com.routepick.service.security.RateLimitingService;
import com.routepick.service.security.RateLimitResult;
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
     * 클라이언트 IP 추출 (step6-6a_api_log_service.md 기반)
     * 한국 환경 최적화: CDN, 로드밸런서 대응
     */
    private String extractClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",      // 일반적인 프록시
            "X-Real-IP",            // Nginx
            "Proxy-Client-IP",      // Apache
            "WL-Proxy-Client-IP",   // WebLogic
            "HTTP_CLIENT_IP",       // 클라이언트 IP
            "HTTP_X_FORWARDED_FOR", // HTTP X-Forwarded-For
            "CF-Connecting-IP",     // Cloudflare
            "X-Cluster-Client-IP"   // 클러스터
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 여러 IP가 있는 경우 첫 번째 IP 사용
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
```

---

## ⚙️ 3. RateLimitingService 구현

### RateLimitingService.java
```java
package com.routepick.service.security;

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
 * - 실시간 메트릭 수집
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
    
    /**
     * Rate Limit 초기화 (관리자용)
     */
    public void resetRateLimit(String key) {
        String pattern = key + "*";
        redisTemplate.delete(redisTemplate.keys(pattern));
        log.info("Rate limit reset for key: {}", key);
    }
}
```

---

## 🔧 4. Redis Lua 스크립트 설정

### RateLimitScriptConfig.java
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

### resources/lua/rate_limit.lua
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

## 📊 5. Rate Limit 결과 객체

### RateLimitResult.java
```java
package com.routepick.service.security;

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
package com.routepick.service.security;

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
    
    /**
     * 시간당 요청 수 계산
     */
    public long getRequestsPerHour() {
        return allowedRequests + deniedRequests;
    }
    
    /**
     * 상태 평가
     */
    public String getStatus() {
        double successRate = getSuccessRate();
        if (successRate >= 0.95) return "HEALTHY";
        if (successRate >= 0.8) return "WARNING";
        return "CRITICAL";
    }
}
```

---

## ⚠️ 6. 예외 처리

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

## 🎛️ 7. Rate Limiting 설정

### RateLimitConfig.java
```java
package com.routepick.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Rate Limiting 설정
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
    
    /**
     * Rate Limiting 전용 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> rateLimitRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
```

### RateLimitProperties.java
```java
package com.routepick.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Rate Limiting 설정 프로퍼티
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    
    /**
     * Rate Limiting 활성화 여부
     */
    @NotNull
    private Boolean enabled = true;
    
    /**
     * 기본 요청 제한 수
     */
    @Min(1)
    private Integer defaultRequests = 100;
    
    /**
     * 기본 시간 윈도우 (초)
     */
    @Min(1)
    private Integer defaultPeriod = 60;
    
    /**
     * API별 커스텀 제한 설정
     */
    private Map<String, ApiLimitConfig> apis;
    
    @Data
    public static class ApiLimitConfig {
        @Min(1)
        private Integer requests;
        
        @Min(1)
        private Integer period;
        
        private String description;
    }
}
```

### application.yml
```yaml
app:
  rate-limit:
    enabled: true
    default-requests: 100
    default-period: 60
    apis:
      login:
        requests: 5
        period: 60
        description: "로그인 시도"
      email:
        requests: 1
        period: 60
        description: "이메일 발송"
      recommendation:
        requests: 3
        period: 3600
        description: "추천 재계산"
      tag-setting:
        requests: 30
        period: 60
        description: "태그 설정"

spring:
  redis:
    rate-limit:
      database: 2  # Rate limiting 전용 DB
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 2
```

---

## 📈 8. 사용 예제

### Controller에서 사용
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    /**
     * 로그인 - IP별 5회/분 제한
     */
    @PostMapping("/login")
    @RateLimited(requests = 5, period = 60, keyStrategy = RateLimited.KeyStrategy.IP)
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // 로그인 로직
    }
    
    /**
     * 이메일 발송 - 사용자별 1회/분 제한  
     */
    @PostMapping("/send-verification")
    @RateLimited(requests = 1, period = 60, keyStrategy = RateLimited.KeyStrategy.USER)
    public ResponseEntity<Void> sendVerification(@RequestBody EmailRequest request) {
        // 이메일 발송 로직
    }
    
    /**
     * 추천 재계산 - 사용자별 3회/시간 제한
     */
    @PostMapping("/recalculate-recommendations")
    @RateLimited(requests = 3, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER)
    public ResponseEntity<Void> recalculateRecommendations() {
        // 추천 재계산 로직
    }
}
```

---

*Step 8-2a 완료: Rate Limiting 구현 (Redis + Lua + AOP)*
*다음 파일: step8-2b_ip_access_control.md*