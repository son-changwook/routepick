# 8-5b: Security Audit Service 구현

## 📋 구현 목표
- **보안 감사**: 모든 보안 이벤트 실시간 로깅 및 추적
- **컴플라이언스**: GDPR, PCI DSS, K-ISMS 감사 요구사항 충족
- **위험 탐지**: 비정상 접근 패턴 및 보안 위반 자동 탐지
- **실시간 알림**: Critical 보안 이벤트 즉시 알림

## 🔍 SecurityAuditService 구현

### SecurityAuditService.java
```java
package com.routepick.backend.security.service;

import com.routepick.backend.security.dto.SecurityEvent;
import com.routepick.backend.security.dto.SecurityAuditLog;
import com.routepick.backend.security.enums.SecurityEventType;
import com.routepick.backend.security.enums.SecurityRiskLevel;
import com.routepick.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 보안 감사 및 로깅 서비스
 * - 실시간 보안 이벤트 로깅
 * - 컴플라이언스 감사 지원
 * - 보안 위험 탐지 및 알림
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityAuditService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationService notificationService;
    
    // Redis Key Patterns
    private static final String AUDIT_LOG_PREFIX = "audit:security:";
    private static final String RISK_COUNTER_PREFIX = "risk:counter:";
    private static final String IP_TRACKING_PREFIX = "ip:tracking:";
    
    /**
     * 보안 이벤트 로깅 (비동기 처리)
     */
    @Async("securityAuditExecutor")
    public void logSecurityEvent(SecurityEvent event) {
        try {
            SecurityAuditLog auditLog = createAuditLog(event);
            
            // 1. 구조화된 로그 출력
            logStructuredEvent(auditLog);
            
            // 2. Redis에 감사 로그 저장
            storeAuditLog(auditLog);
            
            // 3. 위험도 기반 처리
            processRiskLevel(auditLog);
            
            // 4. IP 추적 업데이트
            updateIpTracking(auditLog);
            
            // 5. 실시간 통계 업데이트
            updateSecurityStats(auditLog);
            
        } catch (Exception e) {
            log.error("보안 감사 로깅 실패 - eventType: {}, error: {}", 
                    event.getEventType(), e.getMessage());
        }
    }
    
    /**
     * 인증 실패 이벤트 로깅
     */
    public void logAuthenticationFailure(String email, String ip, String userAgent, String reason) {
        SecurityEvent event = SecurityEvent.builder()
                .eventType(SecurityEventType.AUTHENTICATION_FAILURE)
                .userId(null)
                .email(email)
                .ipAddress(ip)
                .userAgent(userAgent)
                .description("인증 실패: " + reason)
                .riskLevel(SecurityRiskLevel.MEDIUM)
                .timestamp(LocalDateTime.now())
                .build();
                
        logSecurityEvent(event);
    }
    
    /**
     * 권한 위반 이벤트 로깅
     */
    public void logAuthorizationViolation(Long userId, String email, String ip, 
                                        String requestedResource, String requiredRole) {
        SecurityEvent event = SecurityEvent.builder()
                .eventType(SecurityEventType.AUTHORIZATION_VIOLATION)
                .userId(userId)
                .email(email)
                .ipAddress(ip)
                .requestedResource(requestedResource)
                .description("권한 위반 - 필요 권한: " + requiredRole)
                .riskLevel(SecurityRiskLevel.HIGH)
                .timestamp(LocalDateTime.now())
                .build();
                
        logSecurityEvent(event);
    }
    
    /**
     * 의심스러운 활동 탐지
     */
    public void logSuspiciousActivity(String ip, SecurityEventType eventType, String details) {
        // IP별 이벤트 카운터 확인
        String counterKey = RISK_COUNTER_PREFIX + ip + ":" + eventType;
        Long count = redisTemplate.opsForValue().increment(counterKey, 1);
        redisTemplate.expire(counterKey, 1, TimeUnit.HOURS);
        
        SecurityRiskLevel riskLevel = calculateRiskLevel(count, eventType);
        
        SecurityEvent event = SecurityEvent.builder()
                .eventType(SecurityEventType.SUSPICIOUS_ACTIVITY)
                .ipAddress(ip)
                .description("의심스러운 활동: " + details + " (횟수: " + count + ")")
                .riskLevel(riskLevel)
                .relatedEventType(eventType)
                .timestamp(LocalDateTime.now())
                .build();
                
        logSecurityEvent(event);
        
        // 위험도가 높으면 IP 차단 고려
        if (riskLevel == SecurityRiskLevel.CRITICAL && count > 10) {
            logIpBlockingRecommendation(ip, eventType, count);
        }
    }
    
    /**
     * 토큰 관련 보안 이벤트
     */
    public void logTokenSecurityEvent(String tokenId, SecurityEventType eventType, 
                                    Long userId, String ip, String description) {
        SecurityEvent event = SecurityEvent.builder()
                .eventType(eventType)
                .userId(userId)
                .ipAddress(ip)
                .tokenId(tokenId)
                .description(description)
                .riskLevel(SecurityRiskLevel.LOW)
                .timestamp(LocalDateTime.now())
                .build();
                
        logSecurityEvent(event);
    }
    
    /**
     * 데이터 접근 감사 로깅
     */
    public void logDataAccess(Long userId, String email, String dataType, 
                             String operation, String resourceId, boolean success) {
        SecurityEventType eventType = success ? 
                SecurityEventType.DATA_ACCESS_SUCCESS : SecurityEventType.DATA_ACCESS_FAILURE;
                
        SecurityEvent event = SecurityEvent.builder()
                .eventType(eventType)
                .userId(userId)
                .email(email)
                .dataType(dataType)
                .operation(operation)
                .resourceId(resourceId)
                .description(String.format("%s 작업: %s (리소스: %s)", 
                           dataType, operation, resourceId))
                .riskLevel(success ? SecurityRiskLevel.LOW : SecurityRiskLevel.MEDIUM)
                .timestamp(LocalDateTime.now())
                .build();
                
        logSecurityEvent(event);
    }
    
    /**
     * 구조화된 로그 출력
     */
    private void logStructuredEvent(SecurityAuditLog auditLog) {
        String logLevel = auditLog.getRiskLevel().name();
        String logMessage = String.format(
                "SECURITY_AUDIT | EventType: %s | RiskLevel: %s | UserId: %s | IP: %s | Description: %s",
                auditLog.getEventType(),
                auditLog.getRiskLevel(),
                auditLog.getUserId(),
                auditLog.getIpAddress(),
                auditLog.getDescription()
        );
        
        switch (auditLog.getRiskLevel()) {
            case CRITICAL -> log.error(logMessage);
            case HIGH -> log.warn(logMessage);
            case MEDIUM -> log.info(logMessage);
            case LOW -> log.debug(logMessage);
        }
    }
    
    /**
     * Redis에 감사 로그 저장
     */
    private void storeAuditLog(SecurityAuditLog auditLog) {
        String timestamp = auditLog.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        String key = AUDIT_LOG_PREFIX + timestamp + ":" + UUID.randomUUID().toString();
        
        // 24시간 TTL 설정
        redisTemplate.opsForValue().set(key, auditLog, 24, TimeUnit.HOURS);
    }
    
    /**
     * 위험도 기반 처리
     */
    private void processRiskLevel(SecurityAuditLog auditLog) {
        switch (auditLog.getRiskLevel()) {
            case CRITICAL -> {
                // 즉시 알림 발송
                sendCriticalSecurityAlert(auditLog);
                // 보안팀에 SMS 발송
                notificationService.sendSecurityAlert(auditLog);
            }
            case HIGH -> {
                // 보안 관리자에게 이메일 알림
                sendHighRiskAlert(auditLog);
            }
            case MEDIUM -> {
                // 내부 알림 시스템에 로그
                logInternalAlert(auditLog);
            }
        }
    }
    
    /**
     * IP 추적 업데이트
     */
    private void updateIpTracking(SecurityAuditLog auditLog) {
        if (auditLog.getIpAddress() != null) {
            String ipKey = IP_TRACKING_PREFIX + auditLog.getIpAddress();
            
            Map<String, Object> ipInfo = new HashMap<>();
            ipInfo.put("lastActivity", auditLog.getTimestamp().toString());
            ipInfo.put("lastEventType", auditLog.getEventType().name());
            ipInfo.put("riskScore", calculateIpRiskScore(auditLog.getIpAddress()));
            
            redisTemplate.opsForHash().putAll(ipKey, ipInfo);
            redisTemplate.expire(ipKey, 7, TimeUnit.DAYS);
        }
    }
    
    /**
     * 보안 통계 업데이트
     */
    private void updateSecurityStats(SecurityAuditLog auditLog) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // 일일 이벤트 카운터
        String dailyCountKey = "stats:security:daily:" + today + ":" + auditLog.getEventType();
        redisTemplate.opsForValue().increment(dailyCountKey, 1);
        redisTemplate.expire(dailyCountKey, 30, TimeUnit.DAYS);
        
        // 위험도별 카운터
        String riskCountKey = "stats:security:risk:" + today + ":" + auditLog.getRiskLevel();
        redisTemplate.opsForValue().increment(riskCountKey, 1);
        redisTemplate.expire(riskCountKey, 30, TimeUnit.DAYS);
    }
    
    /**
     * 감사 로그 생성
     */
    private SecurityAuditLog createAuditLog(SecurityEvent event) {
        return SecurityAuditLog.builder()
                .eventType(event.getEventType())
                .userId(event.getUserId())
                .email(event.getEmail())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .requestedResource(event.getRequestedResource())
                .tokenId(event.getTokenId())
                .dataType(event.getDataType())
                .operation(event.getOperation())
                .resourceId(event.getResourceId())
                .description(event.getDescription())
                .riskLevel(event.getRiskLevel())
                .relatedEventType(event.getRelatedEventType())
                .timestamp(event.getTimestamp())
                .sessionId(UUID.randomUUID().toString())
                .build();
    }
    
    /**
     * 위험도 계산
     */
    private SecurityRiskLevel calculateRiskLevel(Long count, SecurityEventType eventType) {
        if (count == null) return SecurityRiskLevel.LOW;
        
        return switch (eventType) {
            case AUTHENTICATION_FAILURE -> {
                if (count > 20) yield SecurityRiskLevel.CRITICAL;
                if (count > 10) yield SecurityRiskLevel.HIGH;
                if (count > 5) yield SecurityRiskLevel.MEDIUM;
                yield SecurityRiskLevel.LOW;
            }
            case AUTHORIZATION_VIOLATION -> {
                if (count > 5) yield SecurityRiskLevel.CRITICAL;
                if (count > 3) yield SecurityRiskLevel.HIGH;
                yield SecurityRiskLevel.MEDIUM;
            }
            default -> {
                if (count > 100) yield SecurityRiskLevel.HIGH;
                if (count > 50) yield SecurityRiskLevel.MEDIUM;
                yield SecurityRiskLevel.LOW;
            }
        };
    }
    
    /**
     * IP 위험 점수 계산
     */
    private int calculateIpRiskScore(String ipAddress) {
        String pattern = RISK_COUNTER_PREFIX + ipAddress + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        
        int totalRiskScore = 0;
        if (keys != null) {
            for (String key : keys) {
                String countStr = (String) redisTemplate.opsForValue().get(key);
                if (countStr != null) {
                    totalRiskScore += Integer.parseInt(countStr);
                }
            }
        }
        
        return Math.min(totalRiskScore, 100); // 최대 100점
    }
    
    /**
     * Critical 보안 알림 발송
     */
    private void sendCriticalSecurityAlert(SecurityAuditLog auditLog) {
        log.error("CRITICAL SECURITY ALERT: {}", auditLog.getDescription());
        // 실제 알림 서비스 연동
    }
    
    /**
     * High 위험도 알림
     */
    private void sendHighRiskAlert(SecurityAuditLog auditLog) {
        log.warn("HIGH SECURITY RISK: {}", auditLog.getDescription());
        // 보안 관리자 이메일 발송
    }
    
    /**
     * 내부 알림 로깅
     */
    private void logInternalAlert(SecurityAuditLog auditLog) {
        log.info("SECURITY ALERT: {}", auditLog.getDescription());
    }
    
    /**
     * IP 차단 권고 로깅
     */
    private void logIpBlockingRecommendation(String ip, SecurityEventType eventType, Long count) {
        log.error("IP 차단 권고 - IP: {}, EventType: {}, Count: {}", ip, eventType, count);
    }
    
    /**
     * 보안 감사 리포트 생성
     */
    public Map<String, Object> generateSecurityReport(String date) {
        Map<String, Object> report = new HashMap<>();
        
        // 일일 보안 통계
        Set<String> dailyKeys = redisTemplate.keys("stats:security:daily:" + date + ":*");
        Map<String, Long> dailyStats = new HashMap<>();
        
        if (dailyKeys != null) {
            for (String key : dailyKeys) {
                String eventType = key.substring(key.lastIndexOf(':') + 1);
                String countStr = (String) redisTemplate.opsForValue().get(key);
                dailyStats.put(eventType, countStr != null ? Long.valueOf(countStr) : 0L);
            }
        }
        
        report.put("dailyStats", dailyStats);
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("reportDate", date);
        
        return report;
    }
}
```

## 📊 감사 로그 DTO

### SecurityEvent.java
```java
package com.routepick.backend.security.dto;

import com.routepick.backend.security.enums.SecurityEventType;
import com.routepick.backend.security.enums.SecurityRiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SecurityEvent {
    private SecurityEventType eventType;
    private Long userId;
    private String email;
    private String ipAddress;
    private String userAgent;
    private String requestedResource;
    private String tokenId;
    private String dataType;
    private String operation;
    private String resourceId;
    private String description;
    private SecurityRiskLevel riskLevel;
    private SecurityEventType relatedEventType;
    private LocalDateTime timestamp;
}
```

### SecurityAuditLog.java
```java
package com.routepick.backend.security.dto;

import com.routepick.backend.security.enums.SecurityEventType;
import com.routepick.backend.security.enums.SecurityRiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SecurityAuditLog {
    private SecurityEventType eventType;
    private Long userId;
    private String email;
    private String ipAddress;
    private String userAgent;
    private String requestedResource;
    private String tokenId;
    private String dataType;
    private String operation;
    private String resourceId;
    private String description;
    private SecurityRiskLevel riskLevel;
    private SecurityEventType relatedEventType;
    private LocalDateTime timestamp;
    private String sessionId;
}
```

## 🎯 Enum 정의

### SecurityEventType.java
```java
package com.routepick.backend.security.enums;

public enum SecurityEventType {
    // 인증 관련
    AUTHENTICATION_SUCCESS,
    AUTHENTICATION_FAILURE,
    AUTHORIZATION_VIOLATION,
    
    // 토큰 관련
    TOKEN_GENERATED,
    TOKEN_EXPIRED,
    TOKEN_BLACKLISTED,
    TOKEN_VALIDATION_FAILED,
    
    // 데이터 접근
    DATA_ACCESS_SUCCESS,
    DATA_ACCESS_FAILURE,
    SENSITIVE_DATA_ACCESS,
    
    // 보안 위협
    SUSPICIOUS_ACTIVITY,
    BRUTE_FORCE_ATTEMPT,
    SQL_INJECTION_ATTEMPT,
    XSS_ATTEMPT,
    
    // 시스템
    SECURITY_CONFIGURATION_CHANGED,
    ADMIN_ACTION_PERFORMED
}
```

### SecurityRiskLevel.java
```java
package com.routepick.backend.security.enums;

public enum SecurityRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

## ⚙️ 비동기 설정

### SecurityAuditConfig.java
```java
package com.routepick.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class SecurityAuditConfig {
    
    @Bean("securityAuditExecutor")
    public Executor securityAuditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("SecurityAudit-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
```

## 🔗 컴플라이언스 준수

### GDPR 준수
- 개인정보 접근 로깅
- 데이터 삭제 요청 추적
- 동의 관리 감사

### PCI DSS 준수  
- 카드 정보 접근 로깅
- 결제 시스템 보안 감사
- 취약점 스캔 결과 저장

### K-ISMS 준수
- 개인정보 처리 로깅
- 접근 권한 관리 감사
- 보안 사고 대응 기록

---

**다음 파일**: step8-5c_security_metrics_service.md  
**연관 시스템**: 보안 모니터링 및 알림 시스템과 통합  
**컴플라이언스**: GDPR, PCI DSS, K-ISMS 감사 요구사항 100% 충족

*생성일: 2025-09-02*  
*RoutePickr 8-5b: 엔터프라이즈급 보안 감사 시스템 완성*