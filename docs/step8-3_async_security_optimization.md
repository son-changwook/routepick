# step8-3 보완: 비동기 보안 처리 최적화

## 🚀 보안 로깅 및 통계 비동기 처리 최적화

### 1. 비동기 보안 이벤트 시스템
```java
package com.routepick.backend.security.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 보안 이벤트 기본 클래스
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class SecurityEvent {
    
    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String clientIp;
    private String userAgent;
    private String requestUri;
    private String userId;
    
    public SecurityEvent(String eventType) {
        this.eventId = generateEventId();
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
    }
    
    private String generateEventId() {
        return String.format("%s-%d", 
            eventType != null ? eventType : "UNKNOWN",
            System.currentTimeMillis()
        );
    }
}
```

```java
package com.routepick.backend.security.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CORS 위반 이벤트
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CorsViolationEvent extends SecurityEvent {
    
    private String origin;
    private String method;
    private String blockedReason;
    
    public CorsViolationEvent(String origin, String method, String blockedReason) {
        super("CORS_VIOLATION");
        this.origin = origin;
        this.method = method;
        this.blockedReason = blockedReason;
    }
}
```

```java
package com.routepick.backend.security.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * XSS 공격 시도 이벤트
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class XssAttackEvent extends SecurityEvent {
    
    private String maliciousPattern;
    private String inputField;
    private String sanitizedContent;
    
    public XssAttackEvent(String maliciousPattern, String inputField) {
        super("XSS_ATTACK");
        this.maliciousPattern = maliciousPattern;
        this.inputField = inputField;
    }
}
```

```java
package com.routepick.backend.security.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 민감정보 마스킹 이벤트
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataMaskingEvent extends SecurityEvent {
    
    private String dataType;
    private String fieldName;
    private int maskedCount;
    
    public DataMaskingEvent(String dataType, String fieldName, int maskedCount) {
        super("DATA_MASKING");
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.maskedCount = maskedCount;
    }
}
```

### 2. 비동기 보안 이벤트 처리기
```java
package com.routepick.backend.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.backend.security.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * 비동기 보안 이벤트 처리 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncSecurityEventHandler {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String SECURITY_EVENT_KEY = "security:events";
    private static final String SECURITY_STATS_KEY = "security:stats";
    
    /**
     * CORS 위반 이벤트 비동기 처리
     */
    @Async("securityEventExecutor")
    @EventListener
    public CompletableFuture<Void> handleCorsViolationEvent(CorsViolationEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. 이벤트 로깅 (비동기)
                logSecurityEvent(event);
                
                // 2. 통계 업데이트 (비동기)
                updateSecurityStats("CORS_VIOLATION", event.getClientIp());
                
                // 3. 심각한 위반인 경우 즉시 알림
                if (isHighSeverityViolation(event)) {
                    sendImmediateAlert(event);
                }
                
                // 4. 위반 패턴 분석 (비동기)
                analyzeViolationPattern(event);
                
            } catch (Exception e) {
                log.error("CORS 위반 이벤트 처리 실패", e);
            }
        });
    }
    
    /**
     * XSS 공격 이벤트 비동기 처리
     */
    @Async("securityEventExecutor")
    @EventListener
    public CompletableFuture<Void> handleXssAttackEvent(XssAttackEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. 공격 패턴 분석 및 로깅
                logSecurityEvent(event);
                
                // 2. XSS 통계 업데이트
                updateSecurityStats("XSS_ATTACK", event.getClientIp());
                
                // 3. 공격 패턴 학습 (ML 기반 개선)
                learnAttackPattern(event.getMaliciousPattern());
                
                // 4. 자동 IP 블록킹 검토
                reviewAutoBlocking(event.getClientIp(), "XSS_ATTACK");
                
            } catch (Exception e) {
                log.error("XSS 공격 이벤트 처리 실패", e);
            }
        });
    }
    
    /**
     * 데이터 마스킹 이벤트 비동기 처리
     */
    @Async("securityEventExecutor")
    @EventListener
    public CompletableFuture<Void> handleDataMaskingEvent(DataMaskingEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. 마스킹 통계 업데이트
                updateMaskingStats(event.getDataType(), event.getMaskedCount());
                
                // 2. 민감정보 노출 패턴 분석
                analyzeSensitiveDataPattern(event);
                
                // 3. 컴플라이언스 보고서 업데이트
                updateComplianceReport(event);
                
            } catch (Exception e) {
                log.error("데이터 마스킹 이벤트 처리 실패", e);
            }
        });
    }
    
    /**
     * 보안 이벤트 로깅 (구조화된 로그)
     */
    private void logSecurityEvent(SecurityEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String logKey = SECURITY_EVENT_KEY + ":" + 
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // Redis에 구조화된 로그 저장 (24시간 TTL)
            redisTemplate.opsForList().leftPush(logKey, eventJson);
            redisTemplate.expire(logKey, Duration.ofHours(24));
            
            // 파일 로깅 (검색 가능한 JSON 형태)
            log.info("SECURITY_EVENT: {}", eventJson);
            
        } catch (Exception e) {
            log.error("보안 이벤트 로깅 실패", e);
        }
    }
    
    /**
     * 보안 통계 업데이트
     */
    private void updateSecurityStats(String eventType, String clientIp) {
        try {
            String dailyKey = SECURITY_STATS_KEY + ":daily:" + 
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // 일별 통계
            redisTemplate.opsForHash().increment(dailyKey, eventType, 1);
            redisTemplate.opsForHash().increment(dailyKey, "TOTAL", 1);
            redisTemplate.expire(dailyKey, Duration.ofDays(30));
            
            // IP별 통계 (1시간 윈도우)
            if (clientIp != null) {
                String hourlyIpKey = SECURITY_STATS_KEY + ":hourly_ip:" + clientIp + ":" +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
                
                redisTemplate.opsForHash().increment(hourlyIpKey, eventType, 1);
                redisTemplate.expire(hourlyIpKey, Duration.ofHours(2));
            }
            
        } catch (Exception e) {
            log.error("보안 통계 업데이트 실패", e);
        }
    }
    
    /**
     * 고심각도 위반 판단
     */
    private boolean isHighSeverityViolation(CorsViolationEvent event) {
        // 알려진 악성 도메인에서의 CORS 위반
        String[] maliciousDomains = {"malicious.com", "phishing.net", "spam.org"};
        
        if (event.getOrigin() != null) {
            for (String domain : maliciousDomains) {
                if (event.getOrigin().contains(domain)) {
                    return true;
                }
            }
        }
        
        // 짧은 시간 내 반복적 위반
        return isRepeatedViolation(event.getClientIp(), "CORS_VIOLATION", 5, Duration.ofMinutes(1));
    }
    
    /**
     * 반복적 위반 검사
     */
    private boolean isRepeatedViolation(String clientIp, String violationType, int threshold, Duration window) {
        if (clientIp == null) return false;
        
        try {
            String key = "violation_count:" + clientIp + ":" + violationType;
            Long count = redisTemplate.opsForValue().increment(key);
            
            if (count == 1) {
                redisTemplate.expire(key, window);
            }
            
            return count != null && count >= threshold;
            
        } catch (Exception e) {
            log.error("반복 위반 검사 실패", e);
            return false;
        }
    }
    
    /**
     * 즉시 알림 발송
     */
    private void sendImmediateAlert(SecurityEvent event) {
        // 실제 구현에서는 Slack, 이메일, SMS 등으로 알림
        log.error("🚨 HIGH SEVERITY SECURITY ALERT 🚨: {} from IP: {}", 
            event.getEventType(), event.getClientIp());
    }
    
    /**
     * 공격 패턴 학습 (향후 ML 연동)
     */
    private void learnAttackPattern(String pattern) {
        // 공격 패턴을 데이터베이스에 저장하여 향후 ML 모델 학습에 사용
        try {
            String patternKey = "attack_patterns:xss";
            redisTemplate.opsForSet().add(patternKey, pattern);
            redisTemplate.expire(patternKey, Duration.ofDays(90));
        } catch (Exception e) {
            log.error("공격 패턴 학습 실패", e);
        }
    }
    
    /**
     * 자동 IP 블록킹 검토
     */
    private void reviewAutoBlocking(String clientIp, String attackType) {
        if (clientIp == null) return;
        
        try {
            // 1시간 내 3번 이상 공격 시 자동 블록킹 검토
            if (isRepeatedViolation(clientIp, attackType, 3, Duration.ofHours(1))) {
                log.warn("자동 IP 블록킹 후보: {} (공격 유형: {})", clientIp, attackType);
                
                // 실제 블록킹은 관리자 승인 후 실행하거나 화이트리스트 검토 후 실행
                String blockingCandidateKey = "blocking_candidates:" + clientIp;
                redisTemplate.opsForValue().set(blockingCandidateKey, attackType, Duration.ofHours(24));
            }
        } catch (Exception e) {
            log.error("자동 IP 블록킹 검토 실패", e);
        }
    }
    
    /**
     * 마스킹 통계 업데이트
     */
    private void updateMaskingStats(String dataType, int count) {
        try {
            String dailyKey = "masking_stats:daily:" + 
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            redisTemplate.opsForHash().increment(dailyKey, dataType, count);
            redisTemplate.expire(dailyKey, Duration.ofDays(30));
            
        } catch (Exception e) {
            log.error("마스킹 통계 업데이트 실패", e);
        }
    }
    
    /**
     * 민감정보 패턴 분석
     */
    private void analyzeSensitiveDataPattern(DataMaskingEvent event) {
        // 민감정보가 자주 나타나는 필드/엔드포인트 분석
        try {
            String patternKey = "sensitive_data_patterns:" + event.getDataType();
            redisTemplate.opsForZSet().incrementScore(patternKey, event.getFieldName(), 1);
            redisTemplate.expire(patternKey, Duration.ofDays(7));
            
        } catch (Exception e) {
            log.error("민감정보 패턴 분석 실패", e);
        }
    }
    
    /**
     * 컴플라이언스 보고서 업데이트
     */
    private void updateComplianceReport(DataMaskingEvent event) {
        try {
            String complianceKey = "compliance_report:" + 
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            redisTemplate.opsForHash().increment(complianceKey, "masked_" + event.getDataType(), event.getMaskedCount());
            redisTemplate.expire(complianceKey, Duration.ofDays(365));
            
        } catch (Exception e) {
            log.error("컴플라이언스 보고서 업데이트 실패", e);
        }
    }
}
```

### 3. 비동기 실행 설정
```java
package com.routepick.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 처리 설정
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncSecurityConfig {
    
    /**
     * 보안 이벤트 처리용 Thread Pool
     */
    @Bean("securityEventExecutor")
    public Executor securityEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 기본 스레드 수
        executor.setCorePoolSize(2);
        
        // 최대 스레드 수
        executor.setMaxPoolSize(10);
        
        // 큐 용량
        executor.setQueueCapacity(100);
        
        // 스레드 이름 접두사
        executor.setThreadNamePrefix("SecurityEvent-");
        
        // 스레드 유지 시간
        executor.setKeepAliveSeconds(60);
        
        // 거부 정책: 호출자 스레드에서 실행
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 애플리케이션 종료 시 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info("Security Event Executor 초기화 완료: core={}, max={}, queue={}", 
            executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * 통계 처리용 Thread Pool (더 적은 리소스)
     */
    @Bean("securityStatsExecutor")
    public Executor securityStatsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("SecurityStats-");
        executor.setKeepAliveSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        
        executor.initialize();
        
        return executor;
    }
}
```

### 4. 이벤트 발행자 통합
```java
package com.routepick.backend.security.publisher;

import com.routepick.backend.security.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 보안 이벤트 발행자
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * CORS 위반 이벤트 발행
     */
    public void publishCorsViolation(String origin, String method, String reason, 
                                   String clientIp, String requestUri) {
        try {
            CorsViolationEvent event = new CorsViolationEvent(origin, method, reason);
            event.setClientIp(clientIp);
            event.setRequestUri(requestUri);
            
            eventPublisher.publishEvent(event);
            
        } catch (Exception e) {
            log.error("CORS 위반 이벤트 발행 실패", e);
        }
    }
    
    /**
     * XSS 공격 이벤트 발행
     */
    public void publishXssAttack(String maliciousPattern, String inputField,
                               String clientIp, String requestUri, String userId) {
        try {
            XssAttackEvent event = new XssAttackEvent(maliciousPattern, inputField);
            event.setClientIp(clientIp);
            event.setRequestUri(requestUri);
            event.setUserId(userId);
            
            eventPublisher.publishEvent(event);
            
        } catch (Exception e) {
            log.error("XSS 공격 이벤트 발행 실패", e);
        }
    }
    
    /**
     * 데이터 마스킹 이벤트 발행
     */
    public void publishDataMasking(String dataType, String fieldName, int maskedCount) {
        try {
            DataMaskingEvent event = new DataMaskingEvent(dataType, fieldName, maskedCount);
            eventPublisher.publishEvent(event);
            
        } catch (Exception e) {
            log.error("데이터 마스킹 이벤트 발행 실패", e);
        }
    }
}
```