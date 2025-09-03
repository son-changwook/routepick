# 8-5c: Security Metrics Service 구현

## 📋 구현 목표
- **실시간 메트릭**: 보안 성능 지표 실시간 수집 및 분석
- **Prometheus 연동**: Micrometer 기반 메트릭 익스포트  
- **대시보드**: Grafana 대시보드용 구조화된 메트릭
- **SLA 모니터링**: 보안 컴포넌트별 SLA 준수 여부 추적

## 📈 SecurityMetricsService 구현

### SecurityMetricsService.java
```java
package com.routepick.backend.security.service;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 보안 메트릭 수집 및 분석 서비스
 * - 실시간 보안 성능 지표 수집
 * - Prometheus/Grafana 연동
 * - SLA 모니터링 및 알림
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityMetricsService {
    
    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 메트릭 카운터들
    private final Counter authenticationSuccessCounter;
    private final Counter authenticationFailureCounter;
    private final Counter authorizationViolationCounter;
    private final Counter tokenBlacklistCounter;
    private final Counter rateLimitExceededCounter;
    private final Counter xssAttemptCounter;
    private final Counter sqlInjectionAttemptCounter;
    
    // 메트릭 타이머들
    private final Timer jwtValidationTimer;
    private final Timer rateLimitCheckTimer;
    private final Timer securityFilterTimer;
    private final Timer corsValidationTimer;
    
    // 메트릭 게이지들
    private final AtomicInteger activeSessionsGauge = new AtomicInteger(0);
    private final AtomicInteger blacklistedTokensGauge = new AtomicInteger(0);
    private final AtomicLong securityThreatScoreGauge = new AtomicLong(0);
    
    // Redis Keys
    private static final String METRICS_PREFIX = "metrics:security:";
    private static final String SLA_PREFIX = "sla:security:";
    
    public SecurityMetricsService(MeterRegistry meterRegistry, 
                                 RedisTemplate<String, Object> redisTemplate) {
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
        
        // 카운터 초기화
        this.authenticationSuccessCounter = Counter.builder("security.authentication.success")
                .description("Successful authentication attempts")
                .register(meterRegistry);
                
        this.authenticationFailureCounter = Counter.builder("security.authentication.failure")
                .description("Failed authentication attempts")
                .tag("type", "login")
                .register(meterRegistry);
                
        this.authorizationViolationCounter = Counter.builder("security.authorization.violation")
                .description("Authorization violations")
                .register(meterRegistry);
                
        this.tokenBlacklistCounter = Counter.builder("security.token.blacklist")
                .description("Blacklisted tokens")
                .register(meterRegistry);
                
        this.rateLimitExceededCounter = Counter.builder("security.ratelimit.exceeded")
                .description("Rate limit exceeded attempts")
                .register(meterRegistry);
                
        this.xssAttemptCounter = Counter.builder("security.xss.attempt")
                .description("XSS attack attempts")
                .register(meterRegistry);
                
        this.sqlInjectionAttemptCounter = Counter.builder("security.sqli.attempt")
                .description("SQL Injection attempts")
                .register(meterRegistry);
        
        // 타이머 초기화
        this.jwtValidationTimer = Timer.builder("security.jwt.validation.duration")
                .description("JWT validation duration")
                .register(meterRegistry);
                
        this.rateLimitCheckTimer = Timer.builder("security.ratelimit.check.duration")
                .description("Rate limit check duration")
                .register(meterRegistry);
                
        this.securityFilterTimer = Timer.builder("security.filter.duration")
                .description("Security filter processing duration")
                .register(meterRegistry);
                
        this.corsValidationTimer = Timer.builder("security.cors.validation.duration")
                .description("CORS validation duration")
                .register(meterRegistry);
        
        // 게이지 등록
        Gauge.builder("security.sessions.active")
                .description("Active user sessions")
                .register(meterRegistry, activeSessionsGauge, AtomicInteger::get);
                
        Gauge.builder("security.tokens.blacklisted")
                .description("Number of blacklisted tokens")
                .register(meterRegistry, blacklistedTokensGauge, AtomicInteger::get);
                
        Gauge.builder("security.threat.score")
                .description("Overall security threat score")
                .register(meterRegistry, securityThreatScoreGauge, AtomicLong::get);
    }
    
    /**
     * 인증 성공 메트릭 기록
     */
    public void recordAuthenticationSuccess(String method, Duration duration) {
        authenticationSuccessCounter.increment(
            Tags.of(
                "method", method,
                "status", "success"
            )
        );
        
        // 성능 메트릭도 함께 기록
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop("security.authentication.duration", "method", method);
        
        updateDailyMetrics("auth_success", 1);
        checkAuthenticationSLA(duration);
    }
    
    /**
     * 인증 실패 메트릭 기록
     */
    public void recordAuthenticationFailure(String method, String reason, String ip) {
        authenticationFailureCounter.increment(
            Tags.of(
                "method", method,
                "reason", reason,
                "ip_type", categorizeIp(ip)
            )
        );
        
        updateDailyMetrics("auth_failure", 1);
        updateThreatScore(ip, 5); // 인증 실패로 위험도 +5
    }
    
    /**
     * 권한 위반 메트릭 기록
     */
    public void recordAuthorizationViolation(Long userId, String resource, String requiredRole) {
        authorizationViolationCounter.increment(
            Tags.of(
                "resource", resource,
                "required_role", requiredRole,
                "user_id", String.valueOf(userId)
            )
        );
        
        updateDailyMetrics("authz_violation", 1);
        updateThreatScore(String.valueOf(userId), 10); // 권한 위반으로 위험도 +10
    }
    
    /**
     * JWT 검증 성능 메트릭
     */
    public void recordJwtValidation(Duration duration, boolean success) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(jwtValidationTimer);
        
        meterRegistry.counter("security.jwt.validation.total",
                "status", success ? "success" : "failure"
        ).increment();
        
        checkJwtValidationSLA(duration);
    }
    
    /**
     * Rate Limiting 메트릭
     */
    public void recordRateLimitCheck(Duration duration, boolean exceeded, String endpoint) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(rateLimitCheckTimer);
        
        if (exceeded) {
            rateLimitExceededCounter.increment(
                Tags.of("endpoint", endpoint)
            );
        }
        
        checkRateLimitSLA(duration);
    }
    
    /**
     * 보안 공격 시도 메트릭
     */
    public void recordSecurityAttack(String attackType, String ip, String userAgent) {
        switch (attackType.toLowerCase()) {
            case "xss" -> {
                xssAttemptCounter.increment(
                    Tags.of(
                        "ip", maskIp(ip),
                        "user_agent", categorizeUserAgent(userAgent)
                    )
                );
                updateThreatScore(ip, 15);
            }
            case "sqli", "sql_injection" -> {
                sqlInjectionAttemptCounter.increment(
                    Tags.of(
                        "ip", maskIp(ip),
                        "user_agent", categorizeUserAgent(userAgent)
                    )
                );
                updateThreatScore(ip, 20);
            }
        }
        
        updateDailyMetrics("security_attack", 1);
    }
    
    /**
     * 토큰 블랙리스트 메트릭
     */
    public void recordTokenBlacklist(String reason, Long userId) {
        tokenBlacklistCounter.increment(
            Tags.of(
                "reason", reason,
                "user_type", categorizeUser(userId)
            )
        );
        
        updateBlacklistedTokensGauge();
    }
    
    /**
     * 보안 필터 성능 메트릭
     */
    public Timer.Sample startSecurityFilterTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopSecurityFilterTimer(Timer.Sample sample, String filterName) {
        sample.stop("security.filter.duration", "filter", filterName);
    }
    
    /**
     * 활성 세션 업데이트
     */
    public void updateActiveSessionCount(int count) {
        activeSessionsGauge.set(count);
        
        // Redis에도 저장 (히스토리 추적용)
        String key = METRICS_PREFIX + "active_sessions:" + getCurrentHour();
        redisTemplate.opsForValue().set(key, count, Duration.ofHours(24));
    }
    
    /**
     * 위험도 점수 업데이트
     */
    private void updateThreatScore(String identifier, int scoreIncrease) {
        String key = "threat:score:" + identifier;
        Long currentScore = redisTemplate.opsForValue().increment(key, scoreIncrease);
        redisTemplate.expire(key, Duration.ofHours(24));
        
        // 전체 위험도 점수 업데이트
        securityThreatScoreGauge.addAndGet(scoreIncrease);
    }
    
    /**
     * 일일 메트릭 업데이트
     */
    private void updateDailyMetrics(String metricName, int value) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String key = METRICS_PREFIX + "daily:" + today + ":" + metricName;
        
        redisTemplate.opsForValue().increment(key, value);
        redisTemplate.expire(key, Duration.ofDays(30));
    }
    
    /**
     * SLA 체크 - 인증
     */
    private void checkAuthenticationSLA(Duration duration) {
        long millis = duration.toMillis();
        String slaKey = SLA_PREFIX + "authentication";
        
        if (millis > 2000) { // 2초 초과
            redisTemplate.opsForValue().increment(slaKey + ":violations", 1);
            log.warn("Authentication SLA 위반 - 소요시간: {}ms", millis);
        }
        
        redisTemplate.opsForValue().increment(slaKey + ":total", 1);
        redisTemplate.expire(slaKey + ":violations", Duration.ofDays(1));
        redisTemplate.expire(slaKey + ":total", Duration.ofDays(1));
    }
    
    /**
     * SLA 체크 - JWT 검증
     */
    private void checkJwtValidationSLA(Duration duration) {
        long millis = duration.toMillis();
        String slaKey = SLA_PREFIX + "jwt_validation";
        
        if (millis > 100) { // 100ms 초과
            redisTemplate.opsForValue().increment(slaKey + ":violations", 1);
            log.warn("JWT Validation SLA 위반 - 소요시간: {}ms", millis);
        }
        
        redisTemplate.opsForValue().increment(slaKey + ":total", 1);
    }
    
    /**
     * SLA 체크 - Rate Limiting
     */
    private void checkRateLimitSLA(Duration duration) {
        long millis = duration.toMillis();
        String slaKey = SLA_PREFIX + "rate_limit";
        
        if (millis > 50) { // 50ms 초과
            redisTemplate.opsForValue().increment(slaKey + ":violations", 1);
            log.warn("Rate Limit SLA 위반 - 소요시간: {}ms", millis);
        }
        
        redisTemplate.opsForValue().increment(slaKey + ":total", 1);
    }
    
    /**
     * 블랙리스트된 토큰 수 업데이트
     */
    private void updateBlacklistedTokensGauge() {
        Set<String> blacklistKeys = redisTemplate.keys("blacklist:token:*");
        int count = blacklistKeys != null ? blacklistKeys.size() : 0;
        blacklistedTokensGauge.set(count);
    }
    
    /**
     * 보안 메트릭 대시보드 데이터 조회
     */
    public Map<String, Object> getSecurityMetricsDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        // 실시간 카운터들
        dashboard.put("authSuccessCount", authenticationSuccessCounter.count());
        dashboard.put("authFailureCount", authenticationFailureCounter.count());
        dashboard.put("authzViolationCount", authorizationViolationCounter.count());
        dashboard.put("rateLimitExceededCount", rateLimitExceededCounter.count());
        
        // 현재 상태
        dashboard.put("activeSessions", activeSessionsGauge.get());
        dashboard.put("blacklistedTokens", blacklistedTokensGauge.get());
        dashboard.put("threatScore", securityThreatScoreGauge.get());
        
        // 성능 메트릭
        dashboard.put("avgJwtValidationTime", getAverageTime(jwtValidationTimer));
        dashboard.put("avgRateLimitCheckTime", getAverageTime(rateLimitCheckTimer));
        dashboard.put("avgSecurityFilterTime", getAverageTime(securityFilterTimer));
        
        // SLA 준수율
        dashboard.put("slaCompliance", calculateSLACompliance());
        
        return dashboard;
    }
    
    /**
     * 보안 트렌드 데이터 조회 (지난 24시간)
     */
    public List<Map<String, Object>> getSecurityTrends() {
        List<Map<String, Object>> trends = new ArrayList<>();
        
        for (int i = 23; i >= 0; i--) {
            LocalDateTime hour = LocalDateTime.now().minusHours(i);
            String hourKey = hour.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
            
            Map<String, Object> hourlyData = new HashMap<>();
            hourlyData.put("hour", hourKey);
            
            // 시간별 메트릭 조회
            String authSuccessKey = METRICS_PREFIX + "daily:" + hourKey + ":auth_success";
            String authFailureKey = METRICS_PREFIX + "daily:" + hourKey + ":auth_failure";
            String attackKey = METRICS_PREFIX + "daily:" + hourKey + ":security_attack";
            
            hourlyData.put("authSuccess", getRedisCountValue(authSuccessKey));
            hourlyData.put("authFailure", getRedisCountValue(authFailureKey));
            hourlyData.put("securityAttacks", getRedisCountValue(attackKey));
            
            trends.add(hourlyData);
        }
        
        return trends;
    }
    
    /**
     * 유틸리티 메소드들
     */
    private String categorizeIp(String ip) {
        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
            return "internal";
        }
        return "external";
    }
    
    private String categorizeUserAgent(String userAgent) {
        if (userAgent == null) return "unknown";
        if (userAgent.contains("bot")) return "bot";
        if (userAgent.contains("Chrome")) return "chrome";
        if (userAgent.contains("Firefox")) return "firefox";
        return "other";
    }
    
    private String categorizeUser(Long userId) {
        // 실제 구현에서는 사용자 역할 정보를 조회
        return userId != null && userId > 0 ? "registered" : "anonymous";
    }
    
    private String maskIp(String ip) {
        if (ip == null) return "unknown";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".XXX.XXX";
        }
        return "masked";
    }
    
    private String getCurrentHour() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
    }
    
    private double getAverageTime(Timer timer) {
        return timer.mean(TimeUnit.MILLISECONDS);
    }
    
    private Long getRedisCountValue(String key) {
        String value = (String) redisTemplate.opsForValue().get(key);
        return value != null ? Long.valueOf(value) : 0L;
    }
    
    private Map<String, Double> calculateSLACompliance() {
        Map<String, Double> compliance = new HashMap<>();
        
        // 인증 SLA
        Long authTotal = getRedisCountValue(SLA_PREFIX + "authentication:total");
        Long authViolations = getRedisCountValue(SLA_PREFIX + "authentication:violations");
        if (authTotal > 0) {
            compliance.put("authentication", (double) (authTotal - authViolations) / authTotal * 100);
        }
        
        // JWT SLA
        Long jwtTotal = getRedisCountValue(SLA_PREFIX + "jwt_validation:total");
        Long jwtViolations = getRedisCountValue(SLA_PREFIX + "jwt_validation:violations");
        if (jwtTotal > 0) {
            compliance.put("jwtValidation", (double) (jwtTotal - jwtViolations) / jwtTotal * 100);
        }
        
        // Rate Limit SLA
        Long rlTotal = getRedisCountValue(SLA_PREFIX + "rate_limit:total");
        Long rlViolations = getRedisCountValue(SLA_PREFIX + "rate_limit:violations");
        if (rlTotal > 0) {
            compliance.put("rateLimiting", (double) (rlTotal - rlViolations) / rlTotal * 100);
        }
        
        return compliance;
    }
}
```

## 📊 메트릭 대시보드 REST API

### SecurityMetricsController.java
```java
package com.routepick.backend.controller.admin;

import com.routepick.backend.security.service.SecurityMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 보안 메트릭 조회 API (관리자 전용)
 */
@RestController
@RequestMapping("/api/admin/security/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SecurityMetricsController {
    
    private final SecurityMetricsService securityMetricsService;
    
    /**
     * 실시간 보안 대시보드 데이터
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> dashboard = securityMetricsService.getSecurityMetricsDashboard();
        return ResponseEntity.ok(dashboard);
    }
    
    /**
     * 보안 트렌드 데이터 (24시간)
     */
    @GetMapping("/trends")
    public ResponseEntity<List<Map<String, Object>>> getTrends() {
        List<Map<String, Object>> trends = securityMetricsService.getSecurityTrends();
        return ResponseEntity.ok(trends);
    }
}
```

## 🎯 Grafana 대시보드 설정

### grafana-dashboard.json
```json
{
  "dashboard": {
    "title": "RoutePickr Security Metrics",
    "panels": [
      {
        "title": "Authentication Success Rate",
        "type": "stat",
        "targets": [
          {
            "expr": "rate(security_authentication_success_total[5m])"
          }
        ]
      },
      {
        "title": "JWT Validation Performance",
        "type": "graph", 
        "targets": [
          {
            "expr": "security_jwt_validation_duration_seconds"
          }
        ]
      },
      {
        "title": "Security Attacks by Type",
        "type": "piechart",
        "targets": [
          {
            "expr": "security_xss_attempt_total"
          },
          {
            "expr": "security_sqli_attempt_total"
          }
        ]
      }
    ]
  }
}
```

## ⚙️ 설정

### application.yml
```yaml
# Micrometer 설정
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles:
        "[http.server.requests]": 0.5, 0.95, 0.99
        "[security.jwt.validation.duration]": 0.5, 0.95, 0.99

# 보안 메트릭 설정
security:
  metrics:
    sla:
      authentication-max-duration: 2000 # 2초
      jwt-validation-max-duration: 100  # 100ms
      rate-limit-check-max-duration: 50 # 50ms
    retention:
      daily-metrics: 30 # 30일
      hourly-trends: 7   # 7일
```

---

**다음 단계**: step8 Security 설정 최종 통합 및 9단계 API 문서화 준비  
**연관 시스템**: Prometheus + Grafana 모니터링 스택과 완전 통합  
**성능 목표**: 메트릭 수집 오버헤드 5ms 이내, SLA 준수율 99.9% 이상

*생성일: 2025-09-02*  
*RoutePickr 8-5c: 엔터프라이즈급 보안 메트릭 시스템 완성*