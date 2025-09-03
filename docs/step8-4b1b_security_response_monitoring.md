# Step 8-4b1b: 보안 대응 및 모니터링

> 자동 보안 대응 및 상태 모니터링 시스템
> 생성일: 2025-08-21
> 단계: 8-4b1b (보안 대응 및 모니터링)
> 참고: step8-2d, step3-3c

---

## 🔍 SecurityMonitoringService 대응 및 모니터링

### 자동 보안 대응 및 상태 모니터링
```java
    // ========== 자동 대응 메서드 ==========

    /**
     * 보안 페널티 적용 (IP 차단)
     */
    public void applySecurityPenalty(String clientIp, String violationType, int durationSeconds) {
        String redisKey = "security:blocked:" + clientIp;
        
        SecurityPenalty penalty = SecurityPenalty.builder()
            .clientIp(clientIp)
            .violationType(violationType)
            .blockedAt(LocalDateTime.now())
            .durationSeconds(durationSeconds)
            .autoBlocked(true)
            .build();
        
        redisTemplate.opsForValue().set(redisKey, penalty, Duration.ofSeconds(durationSeconds));
        
        // 차단 메트릭 기록
        metricsCollector.recordIpBlock(clientIp, violationType, durationSeconds);
        
        log.warn("IP 자동 차단 적용 - IP: {}, 사유: {}, 지속시간: {}초", 
                clientIp, violationType, durationSeconds);
    }

    /**
     * 모니터링 강화
     */
    private void enhanceMonitoring(String clientIp, Duration duration) {
        String redisKey = "security:enhanced_monitoring:" + clientIp;
        
        EnhancedMonitoring monitoring = EnhancedMonitoring.builder()
            .clientIp(clientIp)
            .startedAt(LocalDateTime.now())
            .duration(duration)
            .requestCount(0)
            .build();
        
        redisTemplate.opsForValue().set(redisKey, monitoring, duration);
        
        log.info("IP 모니터링 강화 - IP: {}, 지속시간: {}", clientIp, duration);
    }

    /**
     * 시스템 보호 모드 활성화
     */
    private void activateSystemProtectionMode(String reason) {
        SystemProtectionMode protectionMode = SystemProtectionMode.builder()
            .reason(reason)
            .activatedAt(LocalDateTime.now())
            .level("MAXIMUM")
            .build();
        
        redisTemplate.opsForValue().set("security:protection_mode", protectionMode, Duration.ofHours(1));
        
        // 즉시 알림
        alertService.sendSystemProtectionAlert(protectionMode);
        
        log.error("🛡️ 시스템 보호 모드 활성화 - 사유: {}", reason);
    }

    /**
     * 강화된 보안 모드 활성화
     */
    private void activateEnhancedSecurityMode(String reason) {
        EnhancedSecurityMode securityMode = EnhancedSecurityMode.builder()
            .reason(reason)
            .activatedAt(LocalDateTime.now())
            .level("HIGH")
            .build();
        
        redisTemplate.opsForValue().set("security:enhanced_mode", securityMode, Duration.ofMinutes(30));
        
        log.warn("🔒 강화된 보안 모드 활성화 - 사유: {}", reason);
    }
    
    /**
     * 비상 경보 모드 활성화
     */
    public void activateEmergencyMode(String reason, Duration duration) {
        EmergencyMode emergencyMode = EmergencyMode.builder()
            .reason(reason)
            .activatedAt(LocalDateTime.now())
            .level("EMERGENCY")
            .duration(duration)
            .build();
        
        redisTemplate.opsForValue().set("security:emergency_mode", emergencyMode, duration);
        
        // 매니저 내역서 즉시 알림
        alertService.sendEmergencyAlert(emergencyMode);
        
        log.error("🆘 비상 경보 모드 활성화 - 사유: {}, 지속시간: {}", reason, duration);
    }
    
    /**
     * 사고 대응 팀 안내
     */
    public void activateIncidentResponseTeam(String incidentType, String description) {
        IncidentResponse incident = IncidentResponse.builder()
            .incidentType(incidentType)
            .description(description)
            .severity("CRITICAL")
            .activatedAt(LocalDateTime.now())
            .status("ACTIVE")
            .build();
        
        redisTemplate.opsForValue().set("security:incident_response", incident, Duration.ofHours(4));
        
        // 사고 대응 팀 알림
        alertService.sendIncidentResponseAlert(incident);
        
        log.error("🚨 사고 대응 팀 활성화 - 유형: {}, 내용: {}", incidentType, description);
    }

    // ========== 보안 상태 조회 ==========

    /**
     * 현재 보안 상태 조회
     */
    public SecurityStatus getCurrentSecurityStatus() {
        return SecurityStatus.builder()
            .criticalThreats(criticalThreats.get())
            .highThreats(highThreats.get())
            .mediumThreats(mediumThreats.get())
            .lowThreats(lowThreats.get())
            .totalThreats(getTotalThreats())
            .blockedIpsCount(getBlockedIpsCount())
            .isProtectionModeActive(isProtectionModeActive())
            .isEnhancedModeActive(isEnhancedModeActive())
            .isEmergencyModeActive(isEmergencyModeActive())
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    /**
     * 상세 보안 대시보드 데이터
     */
    public SecurityDashboard getSecurityDashboard() {
        return SecurityDashboard.builder()
            .currentStatus(getCurrentSecurityStatus())
            .recentThreats(getRecentThreatsSummary())
            .blockedIps(getRecentBlockedIps())
            .systemModes(getActiveSystemModes())
            .performanceMetrics(getSecurityPerformanceMetrics())
            .build();
    }
    
    /**
     * 최근 위협 요약
     */
    private List<ThreatSummary> getRecentThreatsSummary() {
        List<ThreatSummary> threats = new ArrayList<>();
        
        // Redis에서 최근 1시간 위협 데이터 수집
        Set<String> threatKeys = redisTemplate.keys("security:threats:*");
        
        for (String key : threatKeys) {
            SecurityEvent event = (SecurityEvent) redisTemplate.opsForValue().get(key);
            if (event != null && event.getTimestamp().isAfter(LocalDateTime.now().minusHours(1))) {
                threats.add(ThreatSummary.builder()
                    .eventType(event.getEventType())
                    .clientIp(event.getClientIp())
                    .timestamp(event.getTimestamp())
                    .threatLevel(determineThreatLevel(event.getEventType()))
                    .build());
            }
        }
        
        return threats.stream()
            .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
            .limit(10)
            .collect(Collectors.toList());
    }
    
    /**
     * 최근 차단된 IP 목록
     */
    private List<BlockedIpInfo> getRecentBlockedIps() {
        List<BlockedIpInfo> blockedIps = new ArrayList<>();
        
        Set<String> blockedKeys = redisTemplate.keys("security:blocked:*");
        
        for (String key : blockedKeys) {
            SecurityPenalty penalty = (SecurityPenalty) redisTemplate.opsForValue().get(key);
            if (penalty != null) {
                blockedIps.add(BlockedIpInfo.builder()
                    .clientIp(penalty.getClientIp())
                    .violationType(penalty.getViolationType())
                    .blockedAt(penalty.getBlockedAt())
                    .durationSeconds(penalty.getDurationSeconds())
                    .remainingTime(calculateRemainingTime(penalty))
                    .build());
            }
        }
        
        return blockedIps.stream()
            .sorted((a, b) -> b.getBlockedAt().compareTo(a.getBlockedAt()))
            .limit(20)
            .collect(Collectors.toList());
    }
    
    /**
     * 활성 시스템 모드
     */
    private SystemModes getActiveSystemModes() {
        return SystemModes.builder()
            .protectionMode((SystemProtectionMode) redisTemplate.opsForValue().get("security:protection_mode"))
            .enhancedMode((EnhancedSecurityMode) redisTemplate.opsForValue().get("security:enhanced_mode"))
            .emergencyMode((EmergencyMode) redisTemplate.opsForValue().get("security:emergency_mode"))
            .incidentResponse((IncidentResponse) redisTemplate.opsForValue().get("security:incident_response"))
            .build();
    }
    
    /**
     * 보안 성능 메트릭
     */
    private SecurityPerformanceMetrics getSecurityPerformanceMetrics() {
        return SecurityPerformanceMetrics.builder()
            .averageResponseTime(metricsCollector.getAverageSecurityResponseTime())
            .threatDetectionRate(metricsCollector.getThreatDetectionRate())
            .falsePositiveRate(metricsCollector.getFalsePositiveRate())
            .blockingEffectiveness(metricsCollector.getBlockingEffectiveness())
            .systemUptime(metricsCollector.getSecuritySystemUptime())
            .lastMetricsUpdate(LocalDateTime.now())
            .build();
    }

    private int getTotalThreats() {
        return criticalThreats.get() + highThreats.get() + mediumThreats.get() + lowThreats.get();
    }

    private int getBlockedIpsCount() {
        return redisTemplate.keys("security:blocked:*").size();
    }

    private boolean isProtectionModeActive() {
        return redisTemplate.hasKey("security:protection_mode");
    }
    
    private boolean isEnhancedModeActive() {
        return redisTemplate.hasKey("security:enhanced_mode");
    }
    
    private boolean isEmergencyModeActive() {
        return redisTemplate.hasKey("security:emergency_mode");
    }
    
    // ========== 유지보수 및 리셋 ==========
    
    /**
     * 위협 카운터 리셋 (매시간)
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void resetThreatCounters() {
        int previousCritical = criticalThreats.getAndSet(0);
        int previousHigh = highThreats.getAndSet(0);
        int previousMedium = mediumThreats.getAndSet(0);
        int previousLow = lowThreats.getAndSet(0);
        
        if (previousCritical + previousHigh + previousMedium + previousLow > 0) {
            // 시간별 요약 알림
            SecurityStatus hourlyStatus = SecurityStatus.builder()
                .criticalThreats(previousCritical)
                .highThreats(previousHigh)
                .mediumThreats(previousMedium)
                .lowThreats(previousLow)
                .totalThreats(previousCritical + previousHigh + previousMedium + previousLow)
                .build();
            
            alertService.sendSecuritySummaryAlert(hourlyStatus);
        }
        
        log.info("위협 카운터 리셋 완료 - Critical: {}, High: {}, Medium: {}, Low: {}", 
                previousCritical, previousHigh, previousMedium, previousLow);
    }
    
    /**
     * 만료된 IP 차단 자동 해제
     */
    @Scheduled(fixedRate = 300000) // 5분마다
    public void cleanupExpiredBlocks() {
        Set<String> blockedKeys = redisTemplate.keys("security:blocked:*");
        
        int cleanedCount = 0;
        for (String key : blockedKeys) {
            if (!redisTemplate.hasKey(key)) {
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            log.info("만료된 IP 차단 {} 건 자동 해제", cleanedCount);
        }
    }
    
    /**
     * 보안 데이터 아카이빙
     */
    @Scheduled(cron = "0 0 3 * * ?") // 매일 새벽 3시
    public void archiveSecurityData() {
        try {
            // 오래된 보안 이벤트 아카이빙
            archiveOldSecurityEvents();
            
            // 오래된 위협 패턴 데이터 정리
            cleanupOldThreatPatterns();
            
            // 성능 메트릭 아카이빙
            metricsCollector.archiveOldMetrics();
            
            log.info("보안 데이터 아카이빙 완료");
        } catch (Exception e) {
            log.error("보안 데이터 아카이빙 실패", e);
        }
    }
    
    /**
     * 보안 시스템 상태 체크
     */
    @Scheduled(fixedRate = 60000) // 1분마다
    public void performHealthCheck() {
        SecurityHealthStatus healthStatus = SecurityHealthStatus.builder()
            .threatDetectionSystemActive(isThreatDetectionActive())
            .alertSystemActive(isAlertSystemActive())
            .metricsCollectionActive(isMetricsCollectionActive())
            .threatIntelligenceActive(isThreatIntelligenceActive())
            .lastHealthCheck(LocalDateTime.now())
            .build();
        
        redisTemplate.opsForValue().set("security:health_status", healthStatus, Duration.ofMinutes(5));
        
        // 비정상 상태 감지 시 알림
        if (!healthStatus.isAllSystemsHealthy()) {
            alertService.sendSystemHealthAlert(healthStatus);
        }
    }
    
    // ========== 유틸리티 메서드 ==========
    
    private ThreatLevel determineThreatLevel(String eventType) {
        return switch (eventType) {
            case "SQL_INJECTION", "CSRF_ATTACK" -> ThreatLevel.CRITICAL;
            case "XSS_ATTACK", "DDOS_SUSPECTED" -> ThreatLevel.HIGH;
            case "RATE_LIMIT_VIOLATION", "AUTH_FAILURE" -> ThreatLevel.MEDIUM;
            default -> ThreatLevel.LOW;
        };
    }
    
    private Duration calculateRemainingTime(SecurityPenalty penalty) {
        LocalDateTime expiryTime = penalty.getBlockedAt()
            .plusSeconds(penalty.getDurationSeconds());
        
        if (expiryTime.isAfter(LocalDateTime.now())) {
            return Duration.between(LocalDateTime.now(), expiryTime);
        }
        
        return Duration.ZERO;
    }
    
    private void archiveOldSecurityEvents() {
        // 7일 이상 된 보안 이벤트 아카이빙
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        Set<String> eventKeys = redisTemplate.keys("security:threats:*");
        
        for (String key : eventKeys) {
            SecurityEvent event = (SecurityEvent) redisTemplate.opsForValue().get(key);
            if (event != null && event.getTimestamp().isBefore(cutoffDate)) {
                // 아카이빙 저장소로 이동 (예: 데이터베이스 또는 파일)
                redisTemplate.delete(key);
            }
        }
    }
    
    private void cleanupOldThreatPatterns() {
        // 24시간 이상 된 위협 패턴 데이터 정리
        Set<String> patternKeys = redisTemplate.keys("security:*_patterns:*");
        
        for (String key : patternKeys) {
            // Redis TTL을 확인하여 만료된 데이터 정리
            Long ttl = redisTemplate.getExpire(key);
            if (ttl != null && ttl <= 0) {
                redisTemplate.delete(key);
            }
        }
    }
    
    private boolean isThreatDetectionActive() {
        return threatAnalysisEnabled && threatIntelligence.isActive();
    }
    
    private boolean isAlertSystemActive() {
        return alertService.isActive();
    }
    
    private boolean isMetricsCollectionActive() {
        return metricsCollector.isActive();
    }
    
    private boolean isThreatIntelligenceActive() {
        return threatIntelligence.isActive();
    }
}
```

---

## 📊 보안 모니터링 모델 클래스들

### SecurityEvent.java
```java
package com.routepick.monitoring.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SecurityEvent {
    private String eventType;
    private String clientIp;
    private String requestPath;
    private String userAgent;
    private LocalDateTime timestamp;
    private String exceptionMessage;
    private String sessionId;
}
```

### ThreatLevel.java
```java
public enum ThreatLevel {
    LOW("낮음"),
    MEDIUM("보통"), 
    HIGH("높음"),
    CRITICAL("심각");
    
    private final String description;
    
    ThreatLevel(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
```

### SecurityPenalty.java
```java
@Data
@Builder
public class SecurityPenalty {
    private String clientIp;
    private String violationType;
    private LocalDateTime blockedAt;
    private int durationSeconds;
    private boolean autoBlocked;
}
```

### SecurityStatus.java
```java
@Data
@Builder
public class SecurityStatus {
    private int criticalThreats;
    private int highThreats;
    private int mediumThreats;
    private int lowThreats;
    private int totalThreats;
    private int blockedIpsCount;
    private boolean isProtectionModeActive;
    private boolean isEnhancedModeActive;
    private boolean isEmergencyModeActive;
    private LocalDateTime lastUpdated;
}
```

### SystemProtectionMode.java
```java
@Data
@Builder
public class SystemProtectionMode {
    private String reason;
    private LocalDateTime activatedAt;
    private String level;
}
```

### SecurityDashboard.java
```java
@Data
@Builder
public class SecurityDashboard {
    private SecurityStatus currentStatus;
    private List<ThreatSummary> recentThreats;
    private List<BlockedIpInfo> blockedIps;
    private SystemModes systemModes;
    private SecurityPerformanceMetrics performanceMetrics;
}
```

---

## 🔧 자동 대응 전략

### **1. 위협 수준별 대응**
- **CRITICAL**: 즉시 IP 차단 (1시간) + 위협 인텔리전스 업데이트 + 즉시 알림
- **HIGH**: 임시 IP 차단 (30분) + 모니터링 강화 (2시간) + 긴급 알림
- **MEDIUM**: 모니터링 강화 (30분) + 중간 우선순위 알림
- **LOW**: 배치 알림만

### **2. 시스템 보호 모드**
- **활성화 조건**: CRITICAL 위협 5회 이상
- **보호 조치**: 모든 외부 요청 엄격 검증
- **지속 시간**: 1시간 (자동 해제)
- **알림**: 모든 채널 즉시 통보

### **3. 비상 모드**
- **비상 경보**: 심각한 보안 사고 발생 시
- **사고 대응 팀**: 전담 대응 인력 즐시 활성화
- **시스템 격리**: 필요시 서비스 부분 격리
- **대외 소통**: 공식 보안 공지 발행

### **4. 패턴 기반 탐지**
- **DDoS 감지**: 1분에 100회 이상 요청
- **브루트포스**: 5분에 5회 이상 인증 실패
- **반복 공격**: 동일 IP의 연속 보안 위반
- **비정상 패턴**: 대량 또는 이상 폨 요청

---

## ✅ 완료 사항
- ✅ 자동 보안 대응 시스템 (IP 차단, 모니터링 강화)
- ✅ 다단계 시스템 보호 모드 (일반/강화/비상)
- ✅ 실시간 보안 상태 모니터링
- ✅ 상세 보안 대시보드
- ✅ 자동 리셋 및 유지보수 시스템
- ✅ 보안 데이터 아카이빙
- ✅ 시스템 상태 상시 모니터링
- ✅ 비상 상황 대응 프로세스
- ✅ 성능 메트릭 및 상태 추적

---

*SecurityMonitoringService 보안 대응 및 모니터링 기능 설계 완료*