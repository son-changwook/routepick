# step8-4b1_security_monitoring_service.md

## 🔍 SecurityMonitoringService 구현

> RoutePickr 실시간 보안 위협 탐지 및 분석 시스템  
> 생성일: 2025-08-27  
> 분할: step8-4b_security_monitoring_system.md → 3개 파일  
> 담당: 실시간 위협 탐지, 자동 대응, 시스템 보호

---

## 🎯 보안 모니터링 시스템 개요

### 설계 원칙
- **실시간 탐지**: 보안 위협 즉시 감지 및 대응
- **다채널 알림**: Slack, 이메일, SMS 동시 알림 지원
- **위협 수준별**: LOW/MEDIUM/HIGH/CRITICAL 4단계 차등 대응
- **자동 대응**: 심각한 위협 시 자동 IP 차단 및 서비스 보호
- **메트릭 수집**: Prometheus 연동 실시간 보안 지표

### 모니터링 아키텍처
```
┌─────────────────────┐
│ SecurityMonitoringService │  ← 실시간 위협 탐지 엔진
│ (위협 분석 & 대응 결정)     │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│ SecurityAlertService │  ← 다채널 알림 시스템
│ (Slack/Email/SMS)    │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│ SecurityMetricsCollector │  ← Micrometer 메트릭 수집
│ (Prometheus 연동)     │
└─────────────────────┘
```

---

## 🔍 SecurityMonitoringService 구현

### 실시간 보안 위협 탐지 서비스
```java
package com.routepick.monitoring;

import com.routepick.exception.security.*;
import com.routepick.security.ThreatIntelligenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 보안 모니터링 서비스
 * 실시간 보안 위협 탐지 및 자동 대응
 * 
 * 주요 기능:
 * - 실시간 위협 패턴 분석
 * - 위협 수준별 자동 대응
 * - IP 기반 자동 차단
 * - 보안 상태 종합 분석
 * - 위협 인텔리전스 연동
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityMonitoringService {
    
    private final SecurityAlertService alertService;
    private final SecurityMetricsCollector metricsCollector;
    private final ThreatIntelligenceService threatIntelligence;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${app.security.auto-blocking:true}")
    private boolean autoBlockingEnabled;
    
    @Value("${app.security.threat-analysis:true}")
    private boolean threatAnalysisEnabled;
    
    @Value("${app.security.threat-threshold.critical:5}")
    private int criticalThreatThreshold;
    
    @Value("${app.security.threat-threshold.high:10}")
    private int highThreatThreshold;
    
    // 위협 수준 카운터
    private final AtomicInteger criticalThreats = new AtomicInteger(0);
    private final AtomicInteger highThreats = new AtomicInteger(0);
    private final AtomicInteger mediumThreats = new AtomicInteger(0);
    private final AtomicInteger lowThreats = new AtomicInteger(0);
    
    /**
     * 실시간 위협 탐지 및 분석
     */
    @EventListener
    @Async
    public CompletableFuture<Void> detectThreat(SecurityEvent event) {
        try {
            log.debug("Analyzing security event: {}", event.getEventType());
            
            // 1. 위협 수준 분석
            ThreatLevel threatLevel = analyzeThreatLevel(event);
            
            // 2. 메트릭 업데이트
            metricsCollector.recordSecurityEvent(event, threatLevel);
            
            // 3. 위협 수준별 대응
            handleThreatResponse(event, threatLevel);
            
            // 4. 위협 인텔리전스 업데이트
            if (threatAnalysisEnabled) {
                threatIntelligence.updateThreatPattern(event);
            }
            
            log.info("Security event processed: {} with threat level: {}", 
                event.getEventType(), threatLevel);
            
        } catch (Exception e) {
            log.error("Failed to process security event: {}", event, e);
            metricsCollector.recordMonitoringError("threat_detection_failed");
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 위협 수준 분석
     */
    private ThreatLevel analyzeThreatLevel(SecurityEvent event) {
        String clientIp = event.getClientIp();
        String eventType = event.getEventType();
        
        // 1. 기본 위협 수준 결정
        ThreatLevel baseThreatLevel = getBaseThreatLevel(eventType);
        
        // 2. IP 기반 위협 이력 분석
        int ipViolationCount = getIpViolationCount(clientIp);
        if (ipViolationCount > 10) {
            baseThreatLevel = ThreatLevel.CRITICAL;
        } else if (ipViolationCount > 5) {
            baseThreatLevel = ThreatLevel.upgradeLevel(baseThreatLevel);
        }
        
        // 3. 시간대별 패턴 분석
        if (isAbnormalTimePattern(event)) {
            baseThreatLevel = ThreatLevel.upgradeLevel(baseThreatLevel);
        }
        
        // 4. 공격 패턴 복잡도 분석
        if (isComplexAttackPattern(event)) {
            baseThreatLevel = ThreatLevel.CRITICAL;
        }
        
        // 5. 위협 인텔리전스 연동
        if (threatIntelligence.isKnownThreatSource(clientIp)) {
            baseThreatLevel = ThreatLevel.CRITICAL;
        }
        
        return baseThreatLevel;
    }
    
    /**
     * 위협 수준별 자동 대응
     */
    private void handleThreatResponse(SecurityEvent event, ThreatLevel threatLevel) {
        String clientIp = event.getClientIp();
        
        switch (threatLevel) {
            case CRITICAL:
                // 즉시 차단 + 관리자 알림
                if (autoBlockingEnabled) {
                    applySecurityPenalty(clientIp, "CRITICAL_THREAT", 3600); // 1시간
                }
                alertService.sendCriticalAlert(event, threatLevel);
                criticalThreats.incrementAndGet();
                
                // 시스템 보호 모드 활성화 검토
                if (criticalThreats.get() > criticalThreatThreshold) {
                    activateProtectionMode();
                }
                break;
                
            case HIGH:
                // 장기 차단 + 보안팀 알림
                if (autoBlockingEnabled) {
                    applySecurityPenalty(clientIp, "HIGH_THREAT", 1800); // 30분
                }
                alertService.sendHighPriorityAlert(event, threatLevel);
                highThreats.incrementAndGet();
                break;
                
            case MEDIUM:
                // 단기 차단 + 일반 알림
                if (autoBlockingEnabled && getIpViolationCount(clientIp) > 3) {
                    applySecurityPenalty(clientIp, "MEDIUM_THREAT", 300); // 5분
                }
                alertService.sendMediumPriorityAlert(event, threatLevel);
                mediumThreats.incrementAndGet();
                break;
                
            case LOW:
                // 기록만 + 배치 알림
                alertService.sendLowPriorityAlert(event, threatLevel);
                lowThreats.incrementAndGet();
                break;
        }
        
        // 위반 이력 업데이트
        updateViolationHistory(event, threatLevel);
    }
    
    /**
     * 보안 페널티 적용
     */
    private void applySecurityPenalty(String clientIp, String penaltyType, int durationSeconds) {
        try {
            SecurityPenalty penalty = SecurityPenalty.builder()
                .clientIp(clientIp)
                .penaltyType(penaltyType)
                .appliedTime(LocalDateTime.now())
                .expiryTime(LocalDateTime.now().plusSeconds(durationSeconds))
                .durationSeconds(durationSeconds)
                .violationCount(getIpViolationCount(clientIp) + 1)
                .reason("Automated security response")
                .build();
            
            // Redis에 페널티 정보 저장
            String penaltyKey = "security:penalty:" + clientIp;
            redisTemplate.opsForValue().set(penaltyKey, penalty, Duration.ofSeconds(durationSeconds));
            
            // 메트릭 기록
            metricsCollector.recordSecurityPenalty(penalty);
            
            log.warn("Security penalty applied: {} blocked for {} seconds due to {}", 
                maskIp(clientIp), durationSeconds, penaltyType);
            
        } catch (Exception e) {
            log.error("Failed to apply security penalty for IP: {}", maskIp(clientIp), e);
        }
    }
    
    /**
     * 시스템 보호 모드 활성화
     */
    private void activateProtectionMode() {
        if (isProtectionModeActive()) {
            log.debug("Protection mode already active");
            return;
        }
        
        try {
            SystemProtectionMode protectionMode = SystemProtectionMode.builder()
                .active(true)
                .activatedTime(LocalDateTime.now())
                .reason("Critical threat threshold exceeded")
                .threatCount(criticalThreats.get() + highThreats.get())
                .protectionLevel("HIGH")
                .build();
            
            // Redis에 보호 모드 상태 저장 (30분)
            redisTemplate.opsForValue().set("security:protection_mode", protectionMode, Duration.ofMinutes(30));
            
            // 메트릭 업데이트
            metricsCollector.updateProtectionModeStatus(true);
            
            // 긴급 알림 발송
            alertService.sendSystemProtectionAlert(protectionMode);
            
            log.error("SYSTEM PROTECTION MODE ACTIVATED - Critical threats: {}, High threats: {}", 
                criticalThreats.get(), highThreats.get());
            
        } catch (Exception e) {
            log.error("Failed to activate protection mode", e);
        }
    }
    
    /**
     * 위반 이력 업데이트
     */
    private void updateViolationHistory(SecurityEvent event, ThreatLevel threatLevel) {
        try {
            String historyKey = "security:history:" + event.getClientIp();
            SecurityViolationHistory history = (SecurityViolationHistory) 
                redisTemplate.opsForValue().get(historyKey);
            
            if (history == null) {
                history = SecurityViolationHistory.builder()
                    .clientIp(event.getClientIp())
                    .firstViolation(event.getTimestamp())
                    .violationCount(0)
                    .violationDetails(new ArrayList<>())
                    .build();
            }
            
            // 위반 상세 정보 추가
            SecurityViolationDetail detail = SecurityViolationDetail.builder()
                .eventType(event.getEventType())
                .threatLevel(threatLevel)
                .timestamp(event.getTimestamp())
                .userAgent(event.getUserAgent())
                .requestPath(event.getRequestPath())
                .build();
            
            history.getViolationDetails().add(detail);
            history.setLastViolation(event.getTimestamp());
            history.setViolationCount(history.getViolationCount() + 1);
            
            // 최근 100건만 유지
            if (history.getViolationDetails().size() > 100) {
                history.setViolationDetails(
                    history.getViolationDetails().subList(
                        history.getViolationDetails().size() - 100,
                        history.getViolationDetails().size()
                    )
                );
            }
            
            // Redis에 30일간 보관
            redisTemplate.opsForValue().set(historyKey, history, Duration.ofDays(30));
            
        } catch (Exception e) {
            log.error("Failed to update violation history for IP: {}", maskIp(event.getClientIp()), e);
        }
    }
    
    /**
     * 보안 상태 조회
     */
    public SecurityStatus getSecurityStatus() {
        return SecurityStatus.builder()
            .timestamp(LocalDateTime.now())
            .criticalThreats(criticalThreats.get())
            .highThreats(highThreats.get())
            .mediumThreats(mediumThreats.get())
            .lowThreats(lowThreats.get())
            .totalThreats(criticalThreats.get() + highThreats.get() + mediumThreats.get() + lowThreats.get())
            .protectionModeActive(isProtectionModeActive())
            .blockedIpsCount(getBlockedIpsCount())
            .systemHealthStatus(getSystemHealthStatus())
            .build();
    }
    
    /**
     * 보안 이벤트 기록 (공통 메서드들)
     */
    @Async
    public void recordXssAttack(XssViolationException ex, HttpServletRequest request) {
        SecurityEvent event = SecurityEvent.builder()
            .eventType("XSS_ATTACK")
            .clientIp(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .requestPath(request.getRequestURI())
            .eventData(Map.of(
                "sanitized", ex.getSanitizedContent(),
                "originalLength", ex.getOriginalContent().length()
            ))
            .timestamp(LocalDateTime.now())
            .build();
        
        detectThreat(event);
    }
    
    @Async
    public void recordCsrfViolation(CsrfViolationException ex, HttpServletRequest request) {
        SecurityEvent event = SecurityEvent.builder()
            .eventType("CSRF_VIOLATION")
            .clientIp(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .requestPath(request.getRequestURI())
            .eventData(Map.of(
                "expectedToken", "***",
                "receivedToken", "***",
                "sessionValid", ex.isSessionValid()
            ))
            .timestamp(LocalDateTime.now())
            .build();
        
        detectThreat(event);
    }
    
    @Async
    public void recordCorsViolation(CorsViolationException ex, HttpServletRequest request) {
        SecurityEvent event = SecurityEvent.builder()
            .eventType("CORS_VIOLATION")
            .clientIp(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .requestPath(request.getRequestURI())
            .eventData(Map.of(
                "origin", ex.getRequestOrigin(),
                "allowedOrigins", ex.getAllowedOrigins()
            ))
            .timestamp(LocalDateTime.now())
            .build();
        
        detectThreat(event);
    }
    
    @Async
    public void recordSqlInjection(SqlInjectionException ex, HttpServletRequest request) {
        String attackPattern = ex.getDetectedPattern();
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType("SQL_INJECTION")
            .clientIp(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .requestPath(request.getRequestURI())
            .eventData(Map.of(
                "attackPattern", attackPattern.substring(0, Math.min(100, attackPattern.length())),
                "patternLength", attackPattern.length()
            ))
            .timestamp(LocalDateTime.now())
            .build();
        
        detectThreat(event);
    }
    
    @Async
    public void recordRateLimitViolation(RateLimitViolationException ex, HttpServletRequest request) {
        SecurityEvent event = SecurityEvent.builder()
            .eventType("RATE_LIMIT_VIOLATION")
            .clientIp(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .requestPath(request.getRequestURI())
            .eventData(Map.of(
                "limit", ex.getLimit(),
                "violationCount", ex.getViolationCount(),
                "limitType", ex.getLimitType()
            ))
            .timestamp(LocalDateTime.now())
            .build();
        
        detectThreat(event);
    }
    
    // ========== 보조 메서드 ==========
    
    private String getClientIp(HttpServletRequest request) {
        // IP 추출 로직
        return request.getRemoteAddr();
    }
    
    private ThreatLevel getBaseThreatLevel(String eventType) {
        Map<String, ThreatLevel> threatMap = Map.of(
            "XSS_ATTACK", ThreatLevel.HIGH,
            "CSRF_VIOLATION", ThreatLevel.HIGH,
            "SQL_INJECTION", ThreatLevel.CRITICAL,
            "CORS_VIOLATION", ThreatLevel.MEDIUM,
            "RATE_LIMIT_VIOLATION", ThreatLevel.LOW
        );
        
        return threatMap.getOrDefault(eventType, ThreatLevel.LOW);
    }
    
    private int getIpViolationCount(String clientIp) {
        // Redis에서 IP 위반 횟수 조회
        String key = "security:history:" + clientIp;
        SecurityViolationHistory history = (SecurityViolationHistory) 
            redisTemplate.opsForValue().get(key);
        return history != null ? history.getViolationCount() : 0;
    }
    
    private boolean isAbnormalTimePattern(SecurityEvent event) {
        // 비정상적인 시간대 패턴 분석 (예: 새벽 시간대 과도한 요청)
        int hour = event.getTimestamp().getHour();
        return hour >= 2 && hour <= 5; // 새벽 2-5시
    }
    
    private boolean isComplexAttackPattern(SecurityEvent event) {
        // 복잡한 공격 패턴 탐지
        String eventData = event.getEventData().toString();
        return eventData.length() > 1000 || eventData.contains("script") && eventData.contains("eval");
    }
    
    private boolean isProtectionModeActive() {
        return redisTemplate.hasKey("security:protection_mode");
    }
    
    private int getBlockedIpsCount() {
        // Redis에서 현재 차단된 IP 수 계산
        return redisTemplate.keys("security:penalty:*").size();
    }
    
    private String getSystemHealthStatus() {
        // 시스템 전반적인 건강 상태 판단
        int totalThreats = criticalThreats.get() + highThreats.get();
        if (totalThreats > 50) return "CRITICAL";
        if (totalThreats > 20) return "WARNING";
        return "HEALTHY";
    }
    
    private String maskIp(String ip) {
        if (ip == null) return "N/A";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + "***";
        }
        return "***.***.***." + "***";
    }
}
```

## 🔧 SecurityMonitoringService 핵심 기능

### 1. 실시간 위협 탐지 (Event-Driven)
- **이벤트 리스너**: @EventListener로 보안 이벤트 실시간 수신
- **비동기 처리**: @Async로 성능 최적화, CompletableFuture 반환
- **5단계 위협 분석**: 기본 수준 → IP 이력 → 시간 패턴 → 복잡도 → 위협 DB

### 2. 위협 수준별 자동 대응
- **CRITICAL**: 1시간 즉시 차단 + 3채널 알림 + 보호 모드 검토
- **HIGH**: 30분 차단 + Slack/이메일 알림
- **MEDIUM**: 5분 차단 (3회 위반 시) + Slack 알림
- **LOW**: 기록만 + 배치 알림

### 3. 시스템 보호 모드
- **자동 활성화**: CRITICAL 위협 5개 초과 시
- **보호 기간**: 30분간 Redis 저장
- **긴급 알림**: 모든 채널 즉시 발송
- **메트릭 연동**: Prometheus 게이지 업데이트

### 4. IP 기반 위협 추적
- **위반 이력**: Redis 30일 보관, 최근 100건 상세 기록
- **자동 승급**: 위반 횟수별 위협 수준 상향 조정
- **패턴 분석**: 시간대, 복잡도, 위협 DB 연동

---

*step8-4b1 완료: SecurityMonitoringService 실시간 위협 탐지 시스템*  
*다음: step8-4b2_security_alert_service.md (다채널 알림 시스템)*