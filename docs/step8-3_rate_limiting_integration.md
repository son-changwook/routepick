# step8-3 보완: Rate Limiting 통합

## 🛡️ 8-2 Rate Limiting과 8-3 보안 필터 통합

### 1. 통합 Rate Limiting 서비스
```java
package com.routepick.backend.security.service;

import com.routepick.backend.security.event.SecurityEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 통합 Rate Limiting 서비스
 * - 8-2 기본 Rate Limiting + 8-3 보안 위반 연동
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegratedRateLimitingService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final SecurityEventPublisher securityEventPublisher;
    
    // 기본 Rate Limiting 설정
    private static final int DEFAULT_REQUESTS_PER_MINUTE = 60;
    private static final int DEFAULT_REQUESTS_PER_HOUR = 1000;
    
    // 보안 위반별 패널티 설정
    private static final int CORS_VIOLATION_PENALTY_MINUTES = 10;
    private static final int XSS_ATTACK_PENALTY_MINUTES = 30;
    private static final int CSRF_VIOLATION_PENALTY_MINUTES = 15;
    
    /**
     * 기본 Rate Limiting 검사
     */
    public boolean isAllowed(String clientIp, String endpoint) {
        return isAllowed(clientIp, endpoint, DEFAULT_REQUESTS_PER_MINUTE, Duration.ofMinutes(1));
    }
    
    /**
     * 커스텀 Rate Limiting 검사
     */
    public boolean isAllowed(String clientIp, String endpoint, int maxRequests, Duration window) {
        if (clientIp == null || endpoint == null) {
            return false;
        }
        
        try {
            // 1. 패널티 상태 확인
            if (isPenalized(clientIp)) {
                log.debug("Rate Limiting 거부 - 패널티 상태: IP={}", clientIp);
                return false;
            }
            
            // 2. 일반 Rate Limiting 검사
            String key = generateRateLimitKey(clientIp, endpoint);
            Long currentCount = redisTemplate.opsForValue().increment(key);
            
            if (currentCount == 1) {
                redisTemplate.expire(key, window);
            }
            
            boolean allowed = currentCount != null && currentCount <= maxRequests;
            
            if (!allowed) {
                log.warn("Rate Limit 초과: IP={}, endpoint={}, count={}/{}", 
                    clientIp, endpoint, currentCount, maxRequests);
                    
                // Rate Limit 초과 이벤트 발행 (비동기)
                publishRateLimitExceededEvent(clientIp, endpoint, currentCount.intValue(), maxRequests);
            }
            
            return allowed;
            
        } catch (Exception e) {
            log.error("Rate Limiting 검사 중 오류 발생", e);
            return true; // 오류 시 허용 (fail-open)
        }
    }
    
    /**
     * CORS 위반 패널티 적용
     */
    public void applyCorsViolationPenalty(String clientIp, String origin) {
        applyPenalty(clientIp, "CORS_VIOLATION", Duration.ofMinutes(CORS_VIOLATION_PENALTY_MINUTES));
        
        log.warn("CORS 위반 패널티 적용: IP={}, Origin={}, Duration={}분", 
            clientIp, origin, CORS_VIOLATION_PENALTY_MINUTES);
    }
    
    /**
     * XSS 공격 패널티 적용
     */
    public void applyXssAttackPenalty(String clientIp, String attackPattern) {
        applyPenalty(clientIp, "XSS_ATTACK", Duration.ofMinutes(XSS_ATTACK_PENALTY_MINUTES));
        
        log.warn("XSS 공격 패널티 적용: IP={}, Pattern={}, Duration={}분", 
            clientIp, maskSensitiveData(attackPattern), XSS_ATTACK_PENALTY_MINUTES);
    }
    
    /**
     * CSRF 위반 패널티 적용
     */
    public void applyCsrfViolationPenalty(String clientIp, String endpoint) {
        applyPenalty(clientIp, "CSRF_VIOLATION", Duration.ofMinutes(CSRF_VIOLATION_PENALTY_MINUTES));
        
        log.warn("CSRF 위반 패널티 적용: IP={}, Endpoint={}, Duration={}분", 
            clientIp, endpoint, CSRF_VIOLATION_PENALTY_MINUTES);
    }
    
    /**
     * 보안 위반 패널티 일반 적용
     */
    public void applySecurityViolationPenalty(String clientIp, String violationType, Duration duration) {
        applyPenalty(clientIp, violationType, duration);
        
        log.warn("보안 위반 패널티 적용: IP={}, Type={}, Duration={}초", 
            clientIp, violationType, duration.getSeconds());
    }
    
    /**
     * 패널티 적용
     */
    private void applyPenalty(String clientIp, String violationType, Duration duration) {
        try {
            String penaltyKey = generatePenaltyKey(clientIp, violationType);
            
            // 기존 패널티가 있으면 시간 연장
            Long currentTtl = redisTemplate.getExpire(penaltyKey, TimeUnit.SECONDS);
            long newTtl = duration.getSeconds();
            
            if (currentTtl != null && currentTtl > 0) {
                newTtl = Math.max(newTtl, currentTtl); // 더 긴 시간으로 설정
            }
            
            redisTemplate.opsForValue().set(penaltyKey, violationType, Duration.ofSeconds(newTtl));
            
            // 패널티 통계 업데이트
            updatePenaltyStats(clientIp, violationType);
            
        } catch (Exception e) {
            log.error("패널티 적용 중 오류 발생", e);
        }
    }
    
    /**
     * 패널티 상태 확인
     */
    private boolean isPenalized(String clientIp) {
        try {
            // 모든 패널티 타입 확인
            String[] penaltyTypes = {"CORS_VIOLATION", "XSS_ATTACK", "CSRF_VIOLATION", "GENERAL"};
            
            for (String penaltyType : penaltyTypes) {
                String penaltyKey = generatePenaltyKey(clientIp, penaltyType);
                if (Boolean.TRUE.equals(redisTemplate.hasKey(penaltyKey))) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("패널티 상태 확인 중 오류 발생", e);
            return false;
        }
    }
    
    /**
     * 화이트리스트 IP 확인
     */
    public boolean isWhitelisted(String clientIp) {
        try {
            String whitelistKey = "rate_limit:whitelist";
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(whitelistKey, clientIp));
            
        } catch (Exception e) {
            log.error("화이트리스트 확인 중 오류 발생", e);
            return false;
        }
    }
    
    /**
     * IP 화이트리스트 추가
     */
    public void addToWhitelist(String clientIp, Duration duration) {
        try {
            String whitelistKey = "rate_limit:whitelist";
            redisTemplate.opsForSet().add(whitelistKey, clientIp);
            
            if (duration != null) {
                redisTemplate.expire(whitelistKey, duration);
            }
            
            log.info("IP 화이트리스트 추가: {}, Duration: {}", clientIp, duration);
            
        } catch (Exception e) {
            log.error("화이트리스트 추가 중 오류 발생", e);
        }
    }
    
    /**
     * IP 블랙리스트 확인
     */
    public boolean isBlacklisted(String clientIp) {
        try {
            String blacklistKey = "rate_limit:blacklist";
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(blacklistKey, clientIp));
            
        } catch (Exception e) {
            log.error("블랙리스트 확인 중 오류 발생", e);
            return false;
        }
    }
    
    /**
     * IP 블랙리스트 추가
     */
    public void addToBlacklist(String clientIp, Duration duration) {
        try {
            String blacklistKey = "rate_limit:blacklist";
            redisTemplate.opsForSet().add(blacklistKey, clientIp);
            
            if (duration != null) {
                redisTemplate.expire(blacklistKey, duration);
            }
            
            log.warn("IP 블랙리스트 추가: {}, Duration: {}", clientIp, duration);
            
        } catch (Exception e) {
            log.error("블랙리스트 추가 중 오류 발생", e);
        }
    }
    
    /**
     * Rate Limit 현재 상태 조회
     */
    public RateLimitStatus getRateLimitStatus(String clientIp, String endpoint) {
        try {
            String key = generateRateLimitKey(clientIp, endpoint);
            Long currentCount = (Long) redisTemplate.opsForValue().get(key);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            
            boolean penalized = isPenalized(clientIp);
            boolean whitelisted = isWhitelisted(clientIp);
            boolean blacklisted = isBlacklisted(clientIp);
            
            return RateLimitStatus.builder()
                .clientIp(clientIp)
                .endpoint(endpoint)
                .currentCount(currentCount != null ? currentCount.intValue() : 0)
                .maxRequests(DEFAULT_REQUESTS_PER_MINUTE)
                .resetTimeSeconds(ttl != null ? ttl.intValue() : 0)
                .penalized(penalized)
                .whitelisted(whitelisted)
                .blacklisted(blacklisted)
                .build();
                
        } catch (Exception e) {
            log.error("Rate Limit 상태 조회 중 오류 발생", e);
            return null;
        }
    }
    
    private String generateRateLimitKey(String clientIp, String endpoint) {
        return String.format("rate_limit:%s:%s", clientIp, endpoint.hashCode());
    }
    
    private String generatePenaltyKey(String clientIp, String violationType) {
        return String.format("rate_limit:penalty:%s:%s", clientIp, violationType);
    }
    
    private void publishRateLimitExceededEvent(String clientIp, String endpoint, int currentCount, int maxRequests) {
        // 비동기 이벤트 발행
        securityEventPublisher.publishRateLimitExceeded(clientIp, endpoint, currentCount, maxRequests);
    }
    
    private void updatePenaltyStats(String clientIp, String violationType) {
        try {
            String statsKey = "rate_limit:penalty_stats:" + 
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            redisTemplate.opsForHash().increment(statsKey, violationType, 1);
            redisTemplate.expire(statsKey, Duration.ofDays(30));
            
        } catch (Exception e) {
            log.error("패널티 통계 업데이트 실패", e);
        }
    }
    
    private String maskSensitiveData(String data) {
        if (data == null || data.length() <= 20) {
            return data;
        }
        return data.substring(0, 10) + "***" + data.substring(data.length() - 5);
    }
    
    /**
     * Rate Limit 상태 정보
     */
    public static class RateLimitStatus {
        private String clientIp;
        private String endpoint;
        private int currentCount;
        private int maxRequests;
        private int resetTimeSeconds;
        private boolean penalized;
        private boolean whitelisted;
        private boolean blacklisted;
        
        public static RateLimitStatusBuilder builder() {
            return new RateLimitStatusBuilder();
        }
        
        // Getters, Builder 등 생략 (Lombok 사용)
    }
}
```

### 2. 통합 Security Filter
```java
package com.routepick.backend.security.filter;

import com.routepick.backend.security.service.IntegratedRateLimitingService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 통합 보안 및 Rate Limiting 필터
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegratedSecurityFilter implements Filter {
    
    private final IntegratedRateLimitingService rateLimitingService;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIp = getClientIp(httpRequest);
        String requestURI = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        
        try {
            // 1. 블랙리스트 확인 (최우선)
            if (rateLimitingService.isBlacklisted(clientIp)) {
                log.warn("블랙리스트 IP 차단: {}", clientIp);
                sendBlockedResponse(httpResponse, "IP_BLACKLISTED");
                return;
            }
            
            // 2. 화이트리스트 확인 (Rate Limiting 우회)
            if (!rateLimitingService.isWhitelisted(clientIp)) {
                
                // 3. Rate Limiting 검사
                if (!rateLimitingService.isAllowed(clientIp, requestURI)) {
                    log.debug("Rate Limit 초과: IP={}, URI={}", clientIp, requestURI);
                    sendRateLimitResponse(httpResponse, clientIp, requestURI);
                    return;
                }
            }
            
            // 4. 요청 속성에 IP 정보 추가 (다른 필터에서 사용)
            httpRequest.setAttribute("clientIp", clientIp);
            httpRequest.setAttribute("rateLimitingService", rateLimitingService);
            
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("통합 보안 필터 처리 중 오류 발생", e);
            chain.doFilter(request, response);
        }
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
    
    private void sendRateLimitResponse(HttpServletResponse response, String clientIp, String endpoint) 
            throws IOException {
        
        // Rate Limit 상태 조회
        var status = rateLimitingService.getRateLimitStatus(clientIp, endpoint);
        
        response.setStatus(429); // Too Many Requests
        response.setContentType("application/json;charset=UTF-8");
        
        // Rate Limit 정보 헤더 추가
        response.addHeader("X-RateLimit-Limit", String.valueOf(status.getMaxRequests()));
        response.addHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, status.getMaxRequests() - status.getCurrentCount())));
        response.addHeader("X-RateLimit-Reset", String.valueOf(status.getResetTimeSeconds()));
        response.addHeader("Retry-After", String.valueOf(status.getResetTimeSeconds()));
        
        String jsonResponse = String.format(
            "{\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"요청 한도를 초과했습니다\",\"retryAfter\":%d}",
            status.getResetTimeSeconds()
        );
        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
    
    private void sendBlockedResponse(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(403); // Forbidden
        response.setContentType("application/json;charset=UTF-8");
        
        String jsonResponse = String.format(
            "{\"error\":\"%s\",\"message\":\"접근이 차단되었습니다\"}", reason
        );
        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
```

### 3. CORS Filter Rate Limiting 연동
```java
package com.routepick.backend.security.filter;

import com.routepick.backend.security.service.IntegratedRateLimitingService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * CORS 위반 시 Rate Limiting 연동
 */
@RequiredArgsConstructor
@Slf4j
public class EnhancedCorsFilter implements Filter {
    
    private final IntegratedRateLimitingService rateLimitingService;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String origin = httpRequest.getHeader("Origin");
        String clientIp = (String) httpRequest.getAttribute("clientIp");
        
        // CORS 검증 로직
        if (!isValidOrigin(origin)) {
            
            // CORS 위반 시 Rate Limiting 패널티 적용
            if (clientIp != null) {
                rateLimitingService.applyCorsViolationPenalty(clientIp, origin);
                log.warn("CORS 위반 및 패널티 적용: Origin={}, IP={}", origin, clientIp);
            }
            
            httpResponse.setStatus(403);
            httpResponse.getWriter().write("{\"error\":\"CORS_VIOLATION\"}");
            return;
        }
        
        chain.doFilter(request, response);
    }
    
    private boolean isValidOrigin(String origin) {
        // CORS Origin 검증 로직
        return origin != null && (
            origin.equals("http://localhost:3000") ||
            origin.equals("https://routepick.co.kr") ||
            origin.equals("https://app.routepick.co.kr")
        );
    }
}
```

### 4. application.yml Rate Limiting 통합 설정
```yaml
# Rate Limiting 통합 설정
security:
  rate-limiting:
    enabled: true
    
    # 기본 제한
    default:
      requests-per-minute: 60
      requests-per-hour: 1000
      
    # 엔드포인트별 설정
    endpoints:
      "/api/auth/login":
        requests-per-minute: 5
        requests-per-hour: 50
      "/api/public/**":
        requests-per-minute: 100
        requests-per-hour: 2000
    
    # 패널티 설정
    penalties:
      cors-violation:
        duration-minutes: 10
        enabled: true
      xss-attack:
        duration-minutes: 30
        enabled: true
      csrf-violation:
        duration-minutes: 15
        enabled: true
        
    # 화이트리스트/블랙리스트
    whitelist:
      enabled: true
      ips:
        - "127.0.0.1"
        - "::1"
    
    blacklist:
      enabled: true
      auto-add-on-violations: 3  # 3회 위반시 자동 추가
      default-duration-hours: 24
      
  # 통합 필터 순서
  filter-order:
    integrated-security: -100
    cors-enhanced: -90
    rate-limiting: -80
    
logging:
  level:
    com.routepick.backend.security.service.IntegratedRateLimitingService: INFO
    com.routepick.backend.security.filter.IntegratedSecurityFilter: INFO
```