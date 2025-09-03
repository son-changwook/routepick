# step8-4b3_security_metrics_models.md

## 📊 SecurityMetricsCollector & 데이터 모델

> RoutePickr Micrometer 기반 보안 메트릭 수집 및 Prometheus 연동  
> 생성일: 2025-08-27  
> 분할: step8-4b_security_monitoring_system.md → 3개 파일  
> 담당: 보안 메트릭 수집, 데이터 모델, Prometheus 연동

---

## 📊 SecurityMetricsCollector 구현

### Micrometer 기반 보안 메트릭 수집기
```java
package com.routepick.monitoring;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 보안 메트릭 수집기
 * Micrometer 기반 Prometheus 연동
 * 
 * 수집 메트릭:
 * - 보안 이벤트 카운터
 * - 위협 수준별 분포
 * - IP 차단 통계
 * - 응답 시간 측정
 * - 시스템 보안 상태
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityMetricsCollector implements MeterBinder {
    
    private final MeterRegistry meterRegistry;
    
    // 메트릭 카운터들
    private Counter securityEventCounter;
    private Counter securityPenaltyCounter;
    private Counter monitoringErrorCounter;
    private Timer securityEventProcessingTime;
    private Gauge currentBlockedIpsGauge;
    private Gauge systemProtectionModeGauge;
    
    // 위협 수준별 카운터
    private Counter criticalThreatCounter;
    private Counter highThreatCounter;
    private Counter mediumThreatCounter;
    private Counter lowThreatCounter;
    
    // 이벤트 타입별 카운터
    private Counter corsViolationCounter;
    private Counter csrfViolationCounter;
    private Counter xssAttackCounter;
    private Counter rateLimitViolationCounter;
    private Counter sqlInjectionCounter;
    
    // 게이지를 위한 AtomicLong
    private final AtomicLong blockedIpsCount = new AtomicLong(0);
    private final AtomicLong protectionModeActive = new AtomicLong(0);
    
    @Override
    public void bindTo(MeterRegistry registry) {
        this.meterRegistry = registry;
        
        // 기본 보안 메트릭 초기화
        initializeSecurityMetrics(registry);
        
        // 위협 수준별 메트릭 초기화
        initializeThreatLevelMetrics(registry);
        
        // 이벤트 타입별 메트릭 초기화
        initializeEventTypeMetrics(registry);
        
        log.info("Security metrics collector initialized with {} meters", registry.getMeters().size());
    }
    
    /**
     * 기본 보안 메트릭 초기화
     */
    private void initializeSecurityMetrics(MeterRegistry registry) {
        // 보안 이벤트 총 카운터
        securityEventCounter = Counter.builder("security.events.total")
            .description("Total number of security events detected")
            .tag("component", "security-monitoring")
            .register(registry);
        
        // 보안 페널티 카운터
        securityPenaltyCounter = Counter.builder("security.penalties.total")
            .description("Total number of security penalties applied")
            .tag("component", "security-monitoring")
            .register(registry);
        
        // 모니터링 에러 카운터
        monitoringErrorCounter = Counter.builder("security.monitoring.errors.total")
            .description("Total number of security monitoring errors")
            .tag("component", "security-monitoring")
            .register(registry);
        
        // 보안 이벤트 처리 시간
        securityEventProcessingTime = Timer.builder("security.events.processing.time")
            .description("Time taken to process security events")
            .tag("component", "security-monitoring")
            .register(registry);
        
        // 현재 차단된 IP 수 게이지
        currentBlockedIpsGauge = Gauge.builder("security.blocked.ips.current")
            .description("Current number of blocked IP addresses")
            .tag("component", "security-monitoring")
            .register(registry, blockedIpsCount, AtomicLong::get);
        
        // 시스템 보호 모드 게이지
        systemProtectionModeGauge = Gauge.builder("security.protection.mode.active")
            .description("System protection mode status (1=active, 0=inactive)")
            .tag("component", "security-monitoring")
            .register(registry, protectionModeActive, AtomicLong::get);
    }
    
    /**
     * 위협 수준별 메트릭 초기화
     */
    private void initializeThreatLevelMetrics(MeterRegistry registry) {
        criticalThreatCounter = Counter.builder("security.threats.critical.total")
            .description("Total number of critical security threats")
            .register(registry);
        
        highThreatCounter = Counter.builder("security.threats.high.total")
            .description("Total number of high security threats")
            .register(registry);
        
        mediumThreatCounter = Counter.builder("security.threats.medium.total")
            .description("Total number of medium security threats")
            .register(registry);
        
        lowThreatCounter = Counter.builder("security.threats.low.total")
            .description("Total number of low security threats")
            .register(registry);
    }
    
    /**
     * 이벤트 타입별 메트릭 초기화
     */
    private void initializeEventTypeMetrics(MeterRegistry registry) {
        corsViolationCounter = Counter.builder("security.cors.violations.total")
            .description("Total number of CORS violations")
            .register(registry);
        
        csrfViolationCounter = Counter.builder("security.csrf.violations.total")
            .description("Total number of CSRF violations")
            .register(registry);
        
        xssAttackCounter = Counter.builder("security.xss.attacks.total")
            .description("Total number of XSS attacks")
            .register(registry);
        
        rateLimitViolationCounter = Counter.builder("security.ratelimit.violations.total")
            .description("Total number of rate limit violations")
            .register(registry);
        
        sqlInjectionCounter = Counter.builder("security.sqlinjection.attempts.total")
            .description("Total number of SQL injection attempts")
            .register(registry);
    }
    
    /**
     * 보안 이벤트 메트릭 기록
     */
    public void recordSecurityEvent(SecurityEvent event, ThreatLevel threatLevel) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // 기본 카운터 증가
            securityEventCounter.increment(
                Tags.of(
                    "event_type", event.getEventType(),
                    "threat_level", threatLevel.name(),
                    "client_ip_masked", maskIp(event.getClientIp())
                )
            );
            
            // 위협 수준별 카운터 증가
            switch (threatLevel) {
                case CRITICAL:
                    criticalThreatCounter.increment();
                    break;
                case HIGH:
                    highThreatCounter.increment();
                    break;
                case MEDIUM:
                    mediumThreatCounter.increment();
                    break;
                case LOW:
                    lowThreatCounter.increment();
                    break;
            }
            
            // 이벤트 타입별 카운터 증가
            recordEventTypeMetric(event.getEventType());
            
            log.debug("Security event metric recorded: {} - {}", event.getEventType(), threatLevel);
            
        } finally {
            // 처리 시간 기록
            sample.stop(securityEventProcessingTime);
        }
    }
    
    /**
     * 보안 페널티 메트릭 기록
     */
    public void recordSecurityPenalty(SecurityPenalty penalty) {
        securityPenaltyCounter.increment(
            Tags.of(
                "penalty_type", penalty.getPenaltyType(),
                "duration_category", getDurationCategory(penalty.getDurationSeconds()),
                "violation_count_category", getViolationCountCategory(penalty.getViolationCount())
            )
        );
        
        // 차단된 IP 수 업데이트
        blockedIpsCount.incrementAndGet();
        
        log.debug("Security penalty metric recorded: {} for {} seconds", 
            penalty.getPenaltyType(), penalty.getDurationSeconds());
    }
    
    /**
     * 모니터링 에러 메트릭 기록
     */
    public void recordMonitoringError(String errorType) {
        monitoringErrorCounter.increment(
            Tags.of("error_type", errorType)
        );
        
        log.warn("Monitoring error metric recorded: {}", errorType);
    }
    
    /**
     * 시스템 보호 모드 상태 업데이트
     */
    public void updateProtectionModeStatus(boolean active) {
        protectionModeActive.set(active ? 1 : 0);
        
        log.info("Protection mode status updated: {}", active ? "ACTIVE" : "INACTIVE");
    }
    
    /**
     * 차단된 IP 수 업데이트
     */
    public void updateBlockedIpsCount(long count) {
        blockedIpsCount.set(count);
        
        log.debug("Blocked IPs count updated: {}", count);
    }
    
    /**
     * 커스텀 보안 메트릭 기록
     */
    public void recordCustomSecurityMetric(String metricName, double value, Tags tags) {
        Gauge.builder("security.custom." + metricName)
            .description("Custom security metric: " + metricName)
            .tags(tags)
            .register(meterRegistry, value, v -> v);
    }
    
    /**
     * 보안 이벤트 처리 시간 측정
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopTimer(Timer.Sample sample, String eventType) {
        sample.stop(Timer.builder("security.events.processing.time")
            .tag("event_type", eventType)
            .register(meterRegistry));
    }
    
    // ========== 보조 메서드 ==========
    
    private void recordEventTypeMetric(String eventType) {
        switch (eventType) {
            case "CORS_VIOLATION":
                corsViolationCounter.increment();
                break;
            case "CSRF_VIOLATION":
                csrfViolationCounter.increment();
                break;
            case "XSS_ATTACK":
                xssAttackCounter.increment();
                break;
            case "RATE_LIMIT_VIOLATION":
                rateLimitViolationCounter.increment();
                break;
            case "SQL_INJECTION":
                sqlInjectionCounter.increment();
                break;
        }
    }
    
    private String getDurationCategory(int seconds) {
        if (seconds <= 300) return "short";      // 5분 이하
        if (seconds <= 1800) return "medium";    // 30분 이하
        if (seconds <= 3600) return "long";      // 1시간 이하
        return "extended";                        // 1시간 초과
    }
    
    private String getViolationCountCategory(int count) {
        if (count == 1) return "first";
        if (count <= 3) return "few";
        if (count <= 10) return "multiple";
        return "excessive";
    }
    
    private String maskIp(String ip) {
        if (ip == null) return "unknown";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***";
        }
        return "***";
    }
}
```

---

## 📋 보안 데이터 모델 정의

### SecurityEvent 및 관련 모델들
```java
// SecurityEvent.java
package com.routepick.monitoring.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
public class SecurityEvent {
    private String eventType;           // CORS_VIOLATION, XSS_ATTACK 등
    private String clientIp;            // 클라이언트 IP
    private String userAgent;           // User Agent
    private String requestPath;         // 요청 경로
    private Map<String, Object> eventData;  // 이벤트별 추가 데이터
    private LocalDateTime timestamp;    // 이벤트 발생 시각
    private String sessionId;          // 세션 ID (있는 경우)
    private Long userId;               // 사용자 ID (있는 경우)
}

// ThreatLevel.java
public enum ThreatLevel {
    LOW,
    MEDIUM, 
    HIGH,
    CRITICAL;
    
    public static ThreatLevel upgradeLevel(ThreatLevel current) {
        switch (current) {
            case LOW: return MEDIUM;
            case MEDIUM: return HIGH;
            case HIGH: return CRITICAL;
            case CRITICAL: return CRITICAL;
            default: return LOW;
        }
    }
}

// SecurityPenalty.java
@Getter
@Setter
@Builder
public class SecurityPenalty {
    private String clientIp;
    private String penaltyType;
    private LocalDateTime appliedTime;
    private LocalDateTime expiryTime;
    private int durationSeconds;
    private int violationCount;
    private String reason;
}

// SecurityStatus.java
@Getter
@Setter
@Builder
public class SecurityStatus {
    private LocalDateTime timestamp;
    private int criticalThreats;
    private int highThreats;
    private int mediumThreats;
    private int lowThreats;
    private int totalThreats;
    private boolean protectionModeActive;
    private int blockedIpsCount;
    private String systemHealthStatus;
}

// SystemProtectionMode.java
@Getter
@Setter
@Builder
public class SystemProtectionMode {
    private boolean active;
    private LocalDateTime activatedTime;
    private String reason;
    private int threatCount;
    private String protectionLevel;
}

// SecurityViolationHistory.java
@Getter
@Setter
@Builder
public class SecurityViolationHistory {
    private String clientIp;
    private LocalDateTime firstViolation;
    private LocalDateTime lastViolation;
    private int violationCount;
    private List<SecurityViolationDetail> violationDetails = new ArrayList<>();
}

// SecurityViolationDetail.java
@Getter
@Setter
@Builder
public class SecurityViolationDetail {
    private String eventType;
    private ThreatLevel threatLevel;
    private LocalDateTime timestamp;
    private String userAgent;
    private String requestPath;
}
```

## 📊 Prometheus 메트릭 구성

### 1. 기본 보안 메트릭 (6개)
```yaml
# 보안 이벤트 총 카운터
security_events_total{component="security-monitoring", event_type, threat_level, client_ip_masked}

# 보안 페널티 카운터
security_penalties_total{component="security-monitoring", penalty_type, duration_category, violation_count_category}

# 모니터링 에러 카운터
security_monitoring_errors_total{component="security-monitoring", error_type}

# 보안 이벤트 처리 시간
security_events_processing_time{component="security-monitoring"}

# 현재 차단된 IP 수 (게이지)
security_blocked_ips_current{component="security-monitoring"}

# 시스템 보호 모드 상태 (게이지)
security_protection_mode_active{component="security-monitoring"}
```

### 2. 위협 수준별 메트릭 (4개)
```yaml
# 위협 수준별 카운터
security_threats_critical_total
security_threats_high_total
security_threats_medium_total
security_threats_low_total
```

### 3. 이벤트 타입별 메트릭 (5개)
```yaml
# 이벤트 타입별 카운터
security_cors_violations_total
security_csrf_violations_total
security_xss_attacks_total
security_ratelimit_violations_total
security_sqlinjection_attempts_total
```

### Grafana 대시보드 쿼리 예시
```promql
# 시간당 보안 이벤트 수
rate(security_events_total[1h])

# 위협 수준별 분포 (최근 24시간)
increase(security_threats_critical_total[24h])
increase(security_threats_high_total[24h])
increase(security_threats_medium_total[24h])
increase(security_threats_low_total[24h])

# 현재 차단된 IP 수
security_blocked_ips_current

# 시스템 보호 모드 상태
security_protection_mode_active

# 평균 보안 이벤트 처리 시간
rate(security_events_processing_time_sum[5m]) / rate(security_events_processing_time_count[5m])

# 이벤트 타입별 Top 5
topk(5, increase(security_events_total[1h]))
```

## ✅ Step 8-4b 완료 체크리스트

### 🔍 SecurityMonitoringService 구현
- [x] **실시간 위협 탐지**: 이벤트 기반 비동기 처리로 성능 최적화
- [x] **위협 수준 분석**: IP 이력, 시간 패턴, 복잡도, 인텔리전스 4단계 분석
- [x] **자동 대응 시스템**: CRITICAL(1시간) ~ LOW(기록만) 4단계 차등 대응
- [x] **시스템 보호 모드**: 임계치 초과 시 자동 활성화 (30분간)
- [x] **위반 이력 관리**: Redis 기반 30일 보관, 최근 100건 상세 기록

### 🚨 SecurityAlertService 구현
- [x] **다채널 알림**: Slack + 이메일 + SMS 조합 지원
- [x] **우선순위별 정책**: CRITICAL(3채널) ~ LOW(배치) 차등 발송
- [x] **알림 템플릿**: 위협 수준별 상세한 한국어 메시지 템플릿
- [x] **배치 처리**: LOW 우선순위 알림의 1시간 단위 배치 발송
- [x] **긴급 알림**: 시스템 보호 모드 시 모든 채널 즉시 발송

### 📊 SecurityMetricsCollector 구현
- [x] **Prometheus 연동**: 15개 핵심 보안 메트릭 실시간 수집
- [x] **위협 수준별 분류**: CRITICAL/HIGH/MEDIUM/LOW 4단계 카운터
- [x] **이벤트 타입별**: CORS, CSRF, XSS, Rate Limit, SQL Injection 5가지
- [x] **성능 메트릭**: 보안 이벤트 처리 시간 Timer 측정
- [x] **시스템 상태**: 차단 IP 수, 보호 모드 상태 실시간 게이지

### 🛡️ 보안 강화 기능
- [x] **IP 기반 추적**: 위반 이력 및 패턴 분석으로 위협 수준 상향 조정
- [x] **시간 패턴 분석**: 새벽 시간대(2-5시) 이상 활동 탐지
- [x] **복잡 공격 탐지**: 1000자 초과 또는 script+eval 조합 패턴
- [x] **위협 인텔리전스**: 외부 위협 DB 연동으로 알려진 위협 소스 즉시 차단
- [x] **자동 에스컬레이션**: 임계치 초과 시 보호 모드 자동 활성화

### 📋 데이터 모델
- [x] **SecurityEvent**: 이벤트 타입, IP, 경로, 추가 데이터 포함 통합 모델
- [x] **ThreatLevel**: 4단계 위협 수준 + 자동 승급 로직
- [x] **SecurityPenalty**: IP 차단 정보 + 위반 횟수 추적
- [x] **SecurityStatus**: 전체 보안 상황 요약 대시보드 데이터
- [x] **ViolationHistory**: IP별 위반 이력 30일 보관 + 상세 기록

---

*step8-4b3 완료: SecurityMetricsCollector & 데이터 모델*  
*전체 step8-4b 완성: 보안 모니터링 시스템 (3개 파일 세분화)*  
*다음: step8-4c_exception_pattern_analysis.md (예외 패턴 분석)*