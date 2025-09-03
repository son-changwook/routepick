# step8-4b2_security_alert_service.md

## 🚨 SecurityAlertService 구현

> RoutePickr 다채널 보안 알림 및 메시지 템플릿 시스템  
> 생성일: 2025-08-27  
> 분할: step8-4b_security_monitoring_system.md → 3개 파일  
> 담당: Slack/이메일/SMS 알림, 우선순위별 발송, 메시지 템플릿

---

## 🚨 SecurityAlertService 구현

### 다채널 보안 알림 서비스
```java
package com.routepick.monitoring;

import com.routepick.notification.SlackNotificationService;
import com.routepick.notification.EmailNotificationService;
import com.routepick.notification.SmsNotificationService;
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
 * Slack, 이메일, SMS 다채널 알림 시스템
 * 
 * 알림 우선순위:
 * - CRITICAL: Slack + 이메일 + SMS (즉시)
 * - HIGH: Slack + 이메일 (즉시)
 * - MEDIUM: Slack (즉시)
 * - LOW: Slack (배치, 1시간마다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAlertService {
    
    private final SlackNotificationService slackService;
    private final EmailNotificationService emailService;
    private final SmsNotificationService smsService;
    
    @Value("${app.security.alerts.slack.enabled:true}")
    private boolean slackEnabled;
    
    @Value("${app.security.alerts.email.enabled:true}")
    private boolean emailEnabled;
    
    @Value("${app.security.alerts.sms.enabled:false}")
    private boolean smsEnabled;
    
    @Value("${app.security.alerts.slack.channel:#security-alerts}")
    private String securitySlackChannel;
    
    @Value("${app.security.alerts.email.recipients:admin@routepick.co.kr}")
    private String[] alertEmailRecipients;
    
    @Value("${app.security.alerts.sms.recipients:}")
    private String[] alertSmsRecipients;
    
    /**
     * 심각한 보안 위협 알림 (CRITICAL)
     */
    @Async
    public CompletableFuture<Void> sendCriticalAlert(SecurityEvent event, ThreatLevel threatLevel) {
        String title = "🚨 CRITICAL 보안 위협 탐지";
        String message = buildCriticalAlertMessage(event, threatLevel);
        
        // 1. Slack 즉시 알림
        if (slackEnabled) {
            slackService.sendAlert(securitySlackChannel, title, message, "danger");
        }
        
        // 2. 이메일 즉시 알림
        if (emailEnabled) {
            emailService.sendSecurityAlert(alertEmailRecipients, title, message, "critical");
        }
        
        // 3. SMS 즉시 알림
        if (smsEnabled && alertSmsRecipients.length > 0) {
            String smsMessage = String.format("CRITICAL 보안 위협: %s에서 %s 탐지", 
                event.getClientIp(), event.getEventType());
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
        String title = "⚠️ HIGH 우선순위 보안 이벤트";
        String message = buildHighPriorityAlertMessage(event, threatLevel);
        
        // 1. Slack 즉시 알림
        if (slackEnabled) {
            slackService.sendAlert(securitySlackChannel, title, message, "warning");
        }
        
        // 2. 이메일 알림
        if (emailEnabled) {
            emailService.sendSecurityAlert(alertEmailRecipients, title, message, "high");
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
            truncateString(event.getUserAgent(), 50),
            event.getEventData()
        );
    }
    
    private String buildHighPriorityAlertMessage(SecurityEvent event, ThreatLevel threatLevel) {
        return String.format("""
            ⚠️ **HIGH 우선순위 보안 이벤트**
            
            **이벤트 유형**: %s
            **위협 수준**: %s
            **클라이언트 IP**: %s
            **요청 경로**: %s
            **탐지 시각**: %s
            
            **대응 조치**:
            - 30분 IP 차단 적용
            - 보안팀 확인 필요
            
            **상세 정보**: %s
            """,
            event.getEventType(),
            threatLevel,
            maskIp(event.getClientIp()),
            event.getRequestPath(),
            event.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            event.getEventData()
        );
    }
    
    private String buildMediumPriorityAlertMessage(SecurityEvent event, ThreatLevel threatLevel) {
        return String.format("""
            📋 **MEDIUM 보안 이벤트**
            
            **이벤트**: %s
            **IP**: %s
            **경로**: %s
            **시각**: %s
            
            **조치**: 모니터링 중
            """,
            event.getEventType(),
            maskIp(event.getClientIp()),
            event.getRequestPath(),
            event.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }
    
    private String buildLowPriorityAlertMessage(SecurityEvent event, ThreatLevel threatLevel) {
        return String.format("📝 %s - %s (%s)",
            event.getEventType(),
            maskIp(event.getClientIp()),
            event.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }
    
    private String buildSecuritySummaryMessage(SecurityStatus status) {
        return String.format("""
            📊 **보안 상황 요약 알림**
            
            **📅 보고 시각**: %s
            **🔥 CRITICAL 위협**: %d건
            **⚠️ HIGH 위협**: %d건
            **📋 MEDIUM 위협**: %d건
            **📝 LOW 위협**: %d건
            **📈 총 위협**: %d건
            
            **🛡️ 시스템 상태**
            - 보호 모드: %s
            - 차단된 IP: %d개
            - 시스템 상태: %s
            
            **📝 권장사항**: %s
            """,
            status.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            status.getCriticalThreats(),
            status.getHighThreats(),
            status.getMediumThreats(),
            status.getLowThreats(),
            status.getTotalThreats(),
            status.isProtectionModeActive() ? "활성화" : "비활성화",
            status.getBlockedIpsCount(),
            getKoreanHealthStatus(status.getSystemHealthStatus()),
            getSecurityRecommendation(status)
        );
    }
    
    private String buildSystemProtectionMessage(SystemProtectionMode protectionMode) {
        return String.format("""
            🛡️ **시스템 보호 모드가 활성화되었습니다**
            
            **활성화 시각**: %s
            **활성화 사유**: %s
            **위협 수**: %d건
            **보호 수준**: %s
            
            **🚨 긴급 조치사항**:
            - 시스템 접근 제한 강화
            - 의심 트래픽 자동 차단
            - 보안팀 즉시 상황실 운영
            - 30분 후 자동 해제 예정
            
            **📞 비상연락**: 보안팀 즉시 소집
            """,
            protectionMode.getActivatedTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            protectionMode.getReason(),
            protectionMode.getThreatCount(),
            protectionMode.getProtectionLevel()
        );
    }
    
    // ========== 보조 메서드 ==========
    
    private String getKoreanHealthStatus(String status) {
        switch (status) {
            case "HEALTHY": return "정상";
            case "WARNING": return "주의";
            case "CRITICAL": return "심각";
            default: return "알 수 없음";
        }
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
    
    private String truncateString(String str, int maxLength) {
        if (str == null) return "N/A";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
}
```

## 🚨 SecurityAlertService 핵심 기능

### 1. 다채널 알림 시스템
- **CRITICAL**: 🚨 Slack + 이메일 + SMS (3채널 동시)
- **HIGH**: ⚠️ Slack + 이메일 (2채널 즉시)
- **MEDIUM**: 📋 Slack (1채널 즉시)
- **LOW**: 📝 Slack 배치 (1시간 단위)

### 2. 우선순위별 메시지 템플릿

#### CRITICAL 위협 알림 템플릿
```
🚨 CRITICAL 보안 위협이 탐지되었습니다

이벤트 유형: XSS_ATTACK
위협 수준: CRITICAL
클라이언트 IP: 192.168.***.***
요청 경로: /api/user/profile
탐지 시각: 2025-08-27 14:30:15
User Agent: Chrome/119.0...

즉시 조치사항:
- IP 자동 차단 적용됨
- 추가 모니터링 강화
- 보안팀 즉시 대응 필요
```

#### HIGH 우선순위 알림 템플릿
```
⚠️ HIGH 우선순위 보안 이벤트

이벤트 유형: CSRF_VIOLATION
위협 수준: HIGH
클라이언트 IP: 10.0.***.***
요청 경로: /api/payment/process
탐지 시각: 2025-08-27 14:25:30

대응 조치:
- 30분 IP 차단 적용
- 보안팀 확인 필요
```

#### 보안 상황 요약 알림
```
📊 보안 상황 요약 알림

보고 시각: 2025-08-27 15:00:00
🔥 CRITICAL 위협: 2건
⚠️ HIGH 위협: 8건
📋 MEDIUM 위협: 15건
📝 LOW 위협: 45건
📈 총 위협: 70건

🛡️ 시스템 상태
- 보호 모드: 비활성화
- 차단된 IP: 12개
- 시스템 상태: 주의

📝 권장사항: 정기 보안 점검 및 시스템 업데이트 권장
```

### 3. 시스템 보호 모드 긴급 알림
```
🛡️ 시스템 보호 모드가 활성화되었습니다

활성화 시각: 2025-08-27 14:35:00
활성화 사유: Critical threat threshold exceeded
위협 수: 6건
보호 수준: HIGH

🚨 긴급 조치사항:
- 시스템 접근 제한 강화
- 의심 트래픽 자동 차단
- 보안팀 즉시 상황실 운영
- 30분 후 자동 해제 예정
```

### 4. 알림 설정 및 제어
```yaml
app:
  security:
    alerts:
      slack:
        enabled: true
        channel: "#security-alerts"
      email:
        enabled: true
        recipients: "admin@routepick.co.kr,security@routepick.co.kr"
      sms:
        enabled: false
        recipients: "010-1234-5678,010-9876-5432"
```

### 5. 배치 알림 최적화
- **LOW 우선순위**: 1시간마다 배치 발송으로 알림 피로도 감소
- **메시지 집계**: 동일 IP/이벤트 타입 중복 제거
- **템플릿 최적화**: 간결한 형태로 정보 전달

---

*step8-4b2 완료: SecurityAlertService 다채널 알림 시스템*  
*다음: step8-4b3_security_metrics_models.md (메트릭 수집 및 데이터 모델)*