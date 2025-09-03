# Step 8-4b2: 보안 알림 서비스 및 메트릭 수집

> 다채널 보안 알림 시스템 및 Micrometer 기반 메트릭 수집  
> 생성일: 2025-08-21  
> 단계: 8-4b2 (보안 알림 및 메트릭 수집)  
> 참고: step8-4b1, step8-2d

---

## 🚨 SecurityAlertService 구현

### 다채널 보안 알림 서비스
```java
package com.routepick.monitoring;

import com.routepick.monitoring.model.*;
import com.routepick.notification.SlackService;
import com.routepick.notification.EmailService;
import com.routepick.notification.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * 보안 알림 서비스
 * Slack, Email, SMS 다채널 알림 지원
 * 
 * 알림 정책:
 * - CRITICAL: Slack + Email + SMS 즉시 발송
 * - HIGH: Slack + Email 즉시 발송  
 * - MEDIUM: Slack 즉시 발송
 * - LOW: Slack 배치 발송 (1시간마다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAlertService {
    
    private final SlackService slackService;
    private final EmailService emailService;
    private final SmsService smsService;
    
    @Value("${app.security.alerts.slack.enabled:true}")
    private boolean slackEnabled;
    
    @Value("${app.security.alerts.email.enabled:true}")
    private boolean emailEnabled;
    
    @Value("${app.security.alerts.sms.enabled:false}")
    private boolean smsEnabled;
    
    @Value("${app.security.alerts.slack.channel:#{null}}")
    private String securitySlackChannel;
    
    @Value("${app.security.alerts.email.recipients}")
    private String[] alertEmailRecipients;
    
    @Value("${app.security.alerts.sms.recipients}")
    private String[] alertSmsRecipients;

    /**
     * 심각한 보안 알림 (CRITICAL)
     */
    @Async
    public CompletableFuture<Void> sendCriticalAlert(SecurityEvent event, ThreatLevel threatLevel) {
        String title = "🚨 CRITICAL 보안 위협 탐지";
        String message = buildCriticalAlertMessage(event, threatLevel);
        
        // 모든 채널로 즉시 알림
        if (slackEnabled) {
            slackService.sendAlert(securitySlackChannel, title, message, "danger");
        }
        
        if (emailEnabled) {
            emailService.sendUrgentAlert(alertEmailRecipients, title, message);
        }
        
        if (smsEnabled && alertSmsRecipients.length > 0) {
            String smsMessage = "CRITICAL 보안 위협: " + event.getEventType() + 
                              " (IP: " + maskIp(event.getClientIp()) + ")";
            smsService.sendBulkSms(alertSmsRecipients, smsMessage);
        }
        
        log.error("CRITICAL security alert sent: {}", event.getEventType());
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 높은 우선순위 보안 알림 (HIGH)
     */
    @Async
    public CompletableFuture<Void> sendHighPriorityAlert(SecurityEvent event, ThreatLevel threatLevel) {
        String title = "⚠️ HIGH 보안 위협";
        String message = buildHighPriorityAlertMessage(event, threatLevel);
        
        // Slack + Email 알림
        if (slackEnabled) {
            slackService.sendAlert(securitySlackChannel, title, message, "warning");
        }
        
        if (emailEnabled) {
            emailService.sendSecurityAlert(alertEmailRecipients, title, message);
        }
        
        log.warn("HIGH priority security alert sent: {}", event.getEventType());
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 중간 우선순위 보안 알림 (MEDIUM)
     */
    @Async
    public CompletableFuture<Void> sendMediumPriorityAlert(SecurityEvent event, ThreatLevel threatLevel) {
        String title = "📋 MEDIUM 보안 이벤트";
        String message = buildMediumPriorityAlertMessage(event, threatLevel);
        
        // Slack 알림만
        if (slackEnabled) {
            slackService.sendAlert(securitySlackChannel, title, message, "warning");
        }
        
        log.info("MEDIUM priority security alert sent: {}", event.getEventType());
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 낮은 우선순위 보안 알림 (LOW)
     */
    @Async
    public CompletableFuture<Void> sendLowPriorityAlert(SecurityEvent event, ThreatLevel threatLevel) {
        String title = "📝 LOW 보안 이벤트";
        String message = buildLowPriorityAlertMessage(event, threatLevel);
        
        // Slack 배치 알림 (1시간마다)
        if (slackEnabled) {
            slackService.queueBatchAlert(securitySlackChannel, title, message);
        }
        
        log.debug("LOW priority security alert queued: {}", event.getEventType());
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 보안 요약 알림
     */
    @Async
    public CompletableFuture<Void> sendSecuritySummaryAlert(SecurityStatus status) {
        String title = "📊 보안 상황 요약 알림";
        String message = buildSecuritySummaryMessage(status);
        
        if (slackEnabled) {
            slackService.sendAlert(securitySlackChannel, title, message, "good");
        }
        
        if (emailEnabled && status.getTotalThreats() > 20) {
            emailService.sendSecuritySummary(alertEmailRecipients, title, message, status);
        }
        
        log.info("Security summary alert sent: total threats={}", status.getTotalThreats());
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 시스템 보호 모드 알림
     */
    @Async
    public CompletableFuture<Void> sendSystemProtectionAlert(SystemProtectionMode protectionMode) {
        String title = "🛡️ 시스템 보호 모드 활성화";
        String message = buildSystemProtectionMessage(protectionMode);
        
        // 모든 채널로 즉시 알림
        if (slackEnabled) {
            slackService.sendAlert(securitySlackChannel, title, message, "danger");
        }
        
        if (emailEnabled) {
            emailService.sendUrgentAlert(alertEmailRecipients, title, message);
        }
        
        if (smsEnabled && alertSmsRecipients.length > 0) {
            String smsMessage = "시스템 보호 모드 활성화: 심각한 보안 위협으로 인한 자동 보호 조치";
            smsService.sendBulkSms(alertSmsRecipients, smsMessage);
        }
        
        log.error("System protection mode alert sent: {}", protectionMode);
        return CompletableFuture.completedFuture(null);
    }
    
    // ========== 알림 메시지 빌더 ==========
    
    private String buildCriticalAlertMessage(SecurityEvent event, ThreatLevel threatLevel) {
        return String.format("""
            🚨 **CRITICAL 보안 위협이 탐지되었습니다**
            
            **이벤트 유형**: %s
            **위협 수준**: %s
            **클라이언트 IP**: %s
            **요청 경로**: %s
            **탐지 시각**: %s
            **User Agent**: %s
            
            **즉시 조치사항**:
            - IP 자동 차단 적용됨
            - 추가 모니터링 강화
            - 보안팀 즉시 대응 필요
            
            **상세 정보**: %s
            """,
            event.getEventType(),
            threatLevel,
            maskIp(event.getClientIp()),
            event.getRequestPath(),
            event.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            maskUserAgent(event.getUserAgent()),
            getEventDetailsString(event));
    }
    
    private String buildHighPriorityAlertMessage(SecurityEvent event, ThreatLevel threatLevel) {
        return String.format("""
            ⚠️ **HIGH 보안 위협이 감지되었습니다**
            
            **이벤트**: %s
            **IP**: %s
            **경로**: %s
            **시간**: %s
            
            **적용된 조치**:
            - 임시 IP 차단 (30분)
            - 모니터링 강화
            
            **권장 조치**: 보안 로그 상세 검토
            """,
            event.getEventType(),
            maskIp(event.getClientIp()),
            event.getRequestPath(),
            event.getTimestamp().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
    }
    
    private String buildMediumPriorityAlertMessage(SecurityEvent event, ThreatLevel threatLevel) {
        return String.format("""
            📋 **보안 이벤트 기록**
            
            **유형**: %s | **IP**: %s | **시간**: %s
            **경로**: %s
            
            **상태**: 모니터링 강화 적용
            """,
            event.getEventType(),
            maskIp(event.getClientIp()),
            event.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")),
            event.getRequestPath());
    }
    
    private String buildLowPriorityAlertMessage(SecurityEvent event, ThreatLevel threatLevel) {
        return String.format("📝 %s | %s | %s",
            event.getEventType(),
            maskIp(event.getClientIp()),
            event.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")));
    }
    
    private String buildSecuritySummaryMessage(SecurityStatus status) {
        return String.format("""
            📊 **보안 상황 요약 (최근 1시간)**
            
            **위협 탐지 현황**:
            - 🚨 CRITICAL: %d건
            - ⚠️ HIGH: %d건  
            - 📋 MEDIUM: %d건
            - 📝 LOW: %d건
            - 📈 **총합**: %d건
            
            **시스템 상태**:
            - 차단된 IP: %d개
            - 보호 모드: %s
            - 시스템 상태: %s
            
            **권장 조치**: %s
            
            *업데이트: %s*
            """,
            status.getCriticalThreats(),
            status.getHighThreats(), 
            status.getMediumThreats(),
            status.getLowThreats(),
            status.getTotalThreats(),
            status.getBlockedIpsCount(),
            status.isProtectionModeActive() ? "🛡️ 활성" : "✅ 정상",
            status.getSystemHealthStatus(),
            getSecurityRecommendation(status),
            status.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    
    private String buildSystemProtectionMessage(SystemProtectionMode protectionMode) {
        return String.format("""
            🛡️ **시스템 보호 모드가 활성화되었습니다**
            
            **활성화 사유**: %s
            **활성화 시각**: %s
            **보호 수준**: %s
            **위협 횟수**: %d회
            
            **적용된 보호 조치**:
            - 모든 외부 요청 엄격 검증
            - 의심 활동 즉시 차단
            - 추가 로깅 활성화
            
            **예상 해제 시간**: 1시간 후 자동 해제
            **수동 해제**: 관리자 콘솔에서 가능
            
            ⚠️ **즉시 보안팀 확인 필요**
            """,
            protectionMode.getReason(),
            protectionMode.getActivatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            protectionMode.getLevel(),
            protectionMode.getThreatCount());
    }
    
    // ========== 유틸리티 메서드 ==========
    
    private String getEventDetailsString(SecurityEvent event) {
        if (event.getEventData() != null && !event.getEventData().isEmpty()) {
            return event.getEventData().toString().substring(0, 
                Math.min(200, event.getEventData().toString().length()));
        }
        return "상세 정보 없음";
    }
    
    private String getSecurityRecommendation(SecurityStatus status) {
        if (status.getCriticalThreats() > 5) {
            return "즉시 보안팀 회의 소집 및 추가 보안 조치 검토 필요";
        } else if (status.getHighThreats() > 10) {
            return "보안 정책 점검 및 방화벽 규칙 업데이트 권장";
        } else if (status.getTotalThreats() > 50) {
            return "정기 보안 점검 및 시스템 업데이트 권장";
        } else {
            return "현재 보안 상태 양호, 정기 모니터링 유지";
        }
    }
    
    private String maskIp(String ip) {
        if (ip == null) return "N/A";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + "***";
        }
        return "***.***.***." + "***";
    }
    
    private String maskUserAgent(String userAgent) {
        if (userAgent == null) return "N/A";
        if (userAgent.length() > 50) {
            return userAgent.substring(0, 50) + "...";
        }
        return userAgent;
    }
}
```

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
     * IP 차단 메트릭 기록
     */
    public void recordIpBlock(String clientIp, String violationType, int durationSeconds) {
        securityPenaltyCounter.increment(
            Tags.of(
                "violation_type", violationType,
                "duration_category", getDurationCategory(durationSeconds)
            )
        );
        
        // 차단된 IP 수 업데이트
        blockedIpsCount.incrementAndGet();
        
        log.debug("IP block metric recorded: {} blocked for {} seconds", 
            maskIp(clientIp), durationSeconds);
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

## 🔧 알림 채널 설정

### application.yml (알림 설정)
```yaml
app:
  security:
    alerts:
      slack:
        enabled: ${SLACK_ALERTS_ENABLED:true}
        channel: ${SLACK_SECURITY_CHANNEL:#security-alerts}
        webhook-url: ${SLACK_WEBHOOK_URL:}
        batch-interval: 3600000  # 1시간
        
      email:
        enabled: ${EMAIL_ALERTS_ENABLED:true}
        recipients: ${SECURITY_EMAIL_RECIPIENTS:security@company.com,admin@company.com}
        urgent-template: security-urgent
        summary-template: security-summary
        
      sms:
        enabled: ${SMS_ALERTS_ENABLED:false}
        recipients: ${SECURITY_SMS_RECIPIENTS:}
        provider: ${SMS_PROVIDER:twilio}

# Micrometer 메트릭 설정        
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
        step: 30s
    tags:
      application: routepick
      service: security-monitoring
```

---

## 📊 주요 메트릭

### **Prometheus 메트릭 목록**
- **security.events.total**: 총 보안 이벤트 수
- **security.threats.critical.total**: CRITICAL 위협 수
- **security.threats.high.total**: HIGH 위협 수  
- **security.threats.medium.total**: MEDIUM 위협 수
- **security.threats.low.total**: LOW 위협 수
- **security.penalties.total**: 적용된 보안 페널티 수
- **security.blocked.ips.current**: 현재 차단된 IP 수
- **security.protection.mode.active**: 보호 모드 활성 상태
- **security.events.processing.time**: 이벤트 처리 시간

### **이벤트 타입별 메트릭**
- **security.cors.violations.total**: CORS 위반 횟수
- **security.csrf.violations.total**: CSRF 공격 횟수
- **security.xss.attacks.total**: XSS 공격 횟수
- **security.ratelimit.violations.total**: Rate Limit 위반
- **security.sqlinjection.attempts.total**: SQL Injection 시도

---

## 🚨 알림 정책

### **1. 위협 수준별 알림**
- **CRITICAL**: Slack + Email + SMS (즉시)
- **HIGH**: Slack + Email (즉시)
- **MEDIUM**: Slack (즉시)
- **LOW**: Slack (배치, 1시간마다)

### **2. 알림 내용**
- **기본 정보**: 이벤트 타입, IP, 시간, 경로
- **위협 분석**: 위협 수준, 적용된 조치
- **권장 조치**: 수준별 대응 가이드
- **마스킹**: IP 부분 마스킹, 민감정보 보호

### **3. 배치 처리**
- **LOW 위협**: 1시간마다 요약 발송
- **시간별 요약**: 매시간 전체 현황 요약
- **일일 리포트**: 24시간 보안 상황 종합

---

## 🚀 **활용 방안**

**실시간 모니터링:**
- Grafana 대시보드 연동
- 위협 수준별 실시간 차트
- 시스템 보안 상태 한눈에 파악

**사고 대응:**
- 즉시 알림으로 신속 대응
- 상세 컨텍스트 정보 제공
- 자동 대응 조치 확인

**보안 분석:**
- 위협 패턴 분석
- IP별 공격 이력 추적
- 시간대별 보안 이벤트 분석

*step8-4b2 완성: 보안 알림 서비스 및 메트릭 수집 완료*