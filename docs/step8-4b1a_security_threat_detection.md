# Step 8-4b1a: 보안 위협 탐지 서비스

> 실시간 보안 위협 탐지 및 자동 대응 시스템
> 생성일: 2025-08-21
> 단계: 8-4b1a (보안 위협 탐지 핵심)
> 참고: step8-2d, step3-3c

---

## 🔍 SecurityMonitoringService 핵심 기능

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
 * 보안 모니터링 서비스 - 위협 탐지 및 자동 대응
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

    // ========== 보안 이벤트 처리 ==========

    /**
     * 인증 예외 이벤트 처리
     */
    @EventListener
    @Async
    public void handleAuthException(AuthException ex, HttpServletRequest request) {
        SecurityEvent event = createSecurityEvent(ex, request, "AUTH_FAILURE");
        ThreatLevel threatLevel = analyzeThreatLevel(event, ex);
        
        // 위협 수준별 대응
        switch (threatLevel) {
            case CRITICAL:
                handleCriticalThreat(event, ex, request);
                break;
            case HIGH:
                handleHighThreat(event, ex, request);
                break;
            case MEDIUM:
                handleMediumThreat(event, ex, request);
                break;
            case LOW:
                handleLowThreat(event, ex, request);
                break;
        }
        
        // 메트릭 수집
        metricsCollector.recordSecurityEvent(event, threatLevel);
    }

    /**
     * 보안 위반 예외 이벤트 처리
     */
    @EventListener
    @Async
    public void handleSecurityViolation(SecurityViolationException ex, HttpServletRequest request) {
        SecurityEvent event = createSecurityEvent(ex, request, "SECURITY_VIOLATION");
        ThreatLevel threatLevel = analyzeThreatLevel(event, ex);
        
        // 심각한 보안 위반은 즉시 처리
        if (threatLevel == ThreatLevel.CRITICAL || threatLevel == ThreatLevel.HIGH) {
            handleCriticalThreat(event, ex, request);
        } else {
            handleMediumThreat(event, ex, request);
        }
        
        metricsCollector.recordSecurityEvent(event, threatLevel);
    }

    /**
     * Rate Limit 위반 이벤트 처리
     */
    @EventListener
    @Async
    public void handleRateLimitViolation(RateLimitViolationException ex, HttpServletRequest request) {
        SecurityEvent event = createSecurityEvent(ex, request, "RATE_LIMIT_VIOLATION");
        
        // DDoS 의심 패턴 분석
        String clientIp = extractClientIp(request);
        if (isDdosSuspected(clientIp)) {
            event.setEventType("DDOS_SUSPECTED");
            handleCriticalThreat(event, ex, request);
        } else {
            handleMediumThreat(event, ex, request);
        }
        
        metricsCollector.recordSecurityEvent(event, ThreatLevel.MEDIUM);
    }
    
    /**
     * XSS 공격 이벤트 처리
     */
    @EventListener
    @Async
    public void handleXssAttack(XssAttackException ex, HttpServletRequest request) {
        SecurityEvent event = createSecurityEvent(ex, request, "XSS_ATTACK");
        ThreatLevel threatLevel = ex.containsScriptTag() ? ThreatLevel.HIGH : ThreatLevel.MEDIUM;
        
        // XSS 공격 타입별 처리
        if (ex.containsScriptTag() || ex.containsEventHandler()) {
            handleHighThreat(event, ex, request);
        } else {
            handleMediumThreat(event, ex, request);
        }
        
        // XSS 공격 패턴 분석
        analyzeXssPattern(ex, extractClientIp(request));
        
        metricsCollector.recordSecurityEvent(event, threatLevel);
    }
    
    /**
     * SQL Injection 공격 이벤트 처리
     */
    @EventListener
    @Async
    public void handleSqlInjection(SqlInjectionException ex, HttpServletRequest request) {
        SecurityEvent event = createSecurityEvent(ex, request, "SQL_INJECTION");
        
        // SQL Injection은 상시 CRITICAL
        handleCriticalThreat(event, ex, request);
        
        // SQL 인젝션 패턴 분석 및 차단리스트 업데이트
        analyzeSqlInjectionPattern(ex, extractClientIp(request));
        
        metricsCollector.recordSecurityEvent(event, ThreatLevel.CRITICAL);
    }
    
    /**
     * CSRF 공격 이벤트 처리
     */
    @EventListener
    @Async
    public void handleCsrfAttack(CsrfAttackException ex, HttpServletRequest request) {
        SecurityEvent event = createSecurityEvent(ex, request, "CSRF_ATTACK");
        
        // CSRF 공격도 CRITICAL로 처리
        handleCriticalThreat(event, ex, request);
        
        metricsCollector.recordSecurityEvent(event, ThreatLevel.CRITICAL);
    }

    // ========== 위협 수준별 대응 ==========

    /**
     * CRITICAL 위협 대응
     */
    private void handleCriticalThreat(SecurityEvent event, Exception ex, HttpServletRequest request) {
        criticalThreats.incrementAndGet();
        
        String clientIp = extractClientIp(request);
        
        // 1. 즉시 IP 차단
        if (autoBlockingEnabled) {
            applySecurityPenalty(clientIp, event.getEventType(), 3600); // 1시간 차단
        }
        
        // 2. 위협 인텔리전스 업데이트
        if (threatAnalysisEnabled) {
            threatIntelligence.reportMaliciousIp(clientIp, event.getEventType());
        }
        
        // 3. 즉시 알림
        alertService.sendCriticalAlert(event, ThreatLevel.CRITICAL);
        
        // 4. 시스템 보호 모드 검토
        if (criticalThreats.get() >= criticalThreatThreshold) {
            activateSystemProtectionMode("CRITICAL_THREATS_EXCEEDED");
        }
        
        log.error("🚨 CRITICAL 보안 위협 처리 완료 - IP: {}, Type: {}", clientIp, event.getEventType());
    }

    /**
     * HIGH 위협 대응
     */
    private void handleHighThreat(SecurityEvent event, Exception ex, HttpServletRequest request) {
        highThreats.incrementAndGet();
        
        String clientIp = extractClientIp(request);
        
        // 1. 임시 IP 차단
        if (autoBlockingEnabled) {
            applySecurityPenalty(clientIp, event.getEventType(), 1800); // 30분 차단
        }
        
        // 2. 추가 모니터링 강화
        enhanceMonitoring(clientIp, Duration.ofHours(2));
        
        // 3. 알림 발송
        alertService.sendHighPriorityAlert(event, ThreatLevel.HIGH);
        
        // 4. 연속 위협 체크
        if (highThreats.get() >= highThreatThreshold) {
            activateEnhancedSecurityMode("HIGH_THREATS_PATTERN");
        }
        
        log.warn("⚠️ HIGH 보안 위협 처리 완료 - IP: {}, Type: {}", clientIp, event.getEventType());
    }

    /**
     * MEDIUM 위협 대응
     */
    private void handleMediumThreat(SecurityEvent event, Exception ex, HttpServletRequest request) {
        mediumThreats.incrementAndGet();
        
        String clientIp = extractClientIp(request);
        
        // 1. 모니터링 강화
        enhanceMonitoring(clientIp, Duration.ofMinutes(30));
        
        // 2. 알림 발송 (덜 긴급)
        alertService.sendMediumPriorityAlert(event, ThreatLevel.MEDIUM);
        
        log.info("📋 MEDIUM 보안 위협 기록 - IP: {}, Type: {}", clientIp, event.getEventType());
    }

    /**
     * LOW 위협 대응
     */
    private void handleLowThreat(SecurityEvent event, Exception ex, HttpServletRequest request) {
        lowThreats.incrementAndGet();
        
        // 1. 배치 알림만
        alertService.sendLowPriorityAlert(event, ThreatLevel.LOW);
        
        log.debug("📝 LOW 보안 위협 기록 - Type: {}", event.getEventType());
    }
    
    // ========== 위협 분석 메서드 ==========

    /**
     * 위협 수준 분석
     */
    private ThreatLevel analyzeThreatLevel(SecurityEvent event, Exception ex) {
        if (ex instanceof CsrfAttackException || ex instanceof SqlInjectionException) {
            return ThreatLevel.CRITICAL;
        }
        
        if (ex instanceof XssAttackException) {
            XssAttackException xss = (XssAttackException) ex;
            return xss.containsScriptTag() ? ThreatLevel.HIGH : ThreatLevel.MEDIUM;
        }
        
        if (ex instanceof AuthException) {
            return isRepeatedFailure(event.getClientIp()) ? ThreatLevel.HIGH : ThreatLevel.MEDIUM;
        }
        
        return ThreatLevel.LOW;
    }

    /**
     * DDoS 의심 패턴 감지
     */
    private boolean isDdosSuspected(String clientIp) {
        String redisKey = "security:request_count:" + clientIp;
        String countStr = (String) redisTemplate.opsForValue().get(redisKey);
        
        if (countStr != null) {
            int count = Integer.parseInt(countStr);
            // 1분에 100회 이상 요청 시 DDoS 의심
            return count > 100;
        }
        
        return false;
    }

    /**
     * 반복 실패 패턴 감지
     */
    private boolean isRepeatedFailure(String clientIp) {
        String redisKey = "security:auth_failures:" + clientIp;
        String countStr = (String) redisTemplate.opsForValue().get(redisKey);
        
        if (countStr != null) {
            int count = Integer.parseInt(countStr);
            // 5분에 5회 이상 인증 실패 시 의심
            return count >= 5;
        }
        
        return false;
    }
    
    /**
     * XSS 공격 패턴 분석
     */
    private void analyzeXssPattern(XssAttackException ex, String clientIp) {
        String redisKey = "security:xss_patterns:" + clientIp;
        
        XssPattern pattern = XssPattern.builder()
            .clientIp(clientIp)
            .attackType(ex.getAttackType())
            .containsScript(ex.containsScriptTag())
            .containsEventHandler(ex.containsEventHandler())
            .payload(ex.getPayload())
            .timestamp(LocalDateTime.now())
            .build();
        
        // 패턴 저장 및 분석
        redisTemplate.opsForList().leftPush(redisKey, pattern);
        redisTemplate.expire(redisKey, Duration.ofHours(24));
    }
    
    /**
     * SQL 인젝션 패턴 분석
     */
    private void analyzeSqlInjectionPattern(SqlInjectionException ex, String clientIp) {
        String redisKey = "security:sqli_patterns:" + clientIp;
        
        SqlInjectionPattern pattern = SqlInjectionPattern.builder()
            .clientIp(clientIp)
            .injectionType(ex.getInjectionType())
            .payload(ex.getPayload())
            .targetField(ex.getTargetField())
            .timestamp(LocalDateTime.now())
            .build();
        
        // 패턴 저장 및 자동 차단리스트 업데이트
        redisTemplate.opsForList().leftPush(redisKey, pattern);
        redisTemplate.expire(redisKey, Duration.ofHours(24));
        
        // 자동 차단리스트 등록
        threatIntelligence.addToBlacklist(clientIp, "SQL_INJECTION", Duration.ofDays(1));
    }
    
    // ========== 보안 이벤트 생성 ==========
    
    /**
     * 보안 이벤트 객체 생성
     */
    private SecurityEvent createSecurityEvent(Exception ex, HttpServletRequest request, String eventType) {
        return SecurityEvent.builder()
            .eventType(eventType)
            .clientIp(extractClientIp(request))
            .requestPath(request.getRequestURI())
            .userAgent(request.getHeader("User-Agent"))
            .timestamp(LocalDateTime.now())
            .exceptionMessage(ex.getMessage())
            .sessionId(request.getSession(false) != null ? 
                      request.getSession().getId() : "NO_SESSION")
            .build();
    }

    /**
     * 클라이언트 IP 추출
     */
    private String extractClientIp(HttpServletRequest request) {
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
}
```

---

## 📊 주요 보안 이벤트 탐지

### 1. 인증 및 접근 제어 위협
- **브루트포스 공격**: 5분에 5회 이상 인증 실패
- **비정상적 로그인 시도**: IP 기반 패턴 분석
- **세션 하이잭킹**: 비정상적 세션 사용 패턴
- **권한 승격**: 비인가된 리소스 접근 시도

### 2. 웹 애플리케이션 공격
- **XSS 공격**: 스크립트 태그, 이벤트 핸들러 감지
- **SQL 인젝션**: 악성 쿼리 패턴 감지
- **CSRF 공격**: 토큰 및 리퍼러 검증 실패
- **커맨드 인젝션**: 시스템 명령어 실행 시도

### 3. 네트워크 수준 공격
- **DDoS 공격**: 1분에 100회 이상 요청
- **소거 공격**: 비정상적 패킷 크기 또는 빈도
- **포트 스캔**: 비인가된 포트 접근 시도
- **프로토콜 남용**: HTTP 프로토콜 비정상 사용

---

## 🎯 위협 수준별 대응 전략

### CRITICAL 위협 (즉시 대응)
- **자동 대응**: 1시간 IP 차단
- **위협 인텔리전스**: 악성 IP 데이터베이스 등록
- **알림**: 매니저 즉시 통지
- **보호 모드**: 5회 달성 시 시스템 보호 모드 활성화

### HIGH 위협 (빠른 대응)
- **자동 대응**: 30분 IP 차단
- **모니터링 강화**: 2시간 집중 모니터링
- **알림**: 높은 우선순위 알림
- **보안 강화**: 10회 달성 시 보안 모드 강화

### MEDIUM 위협 (일반 대응)
- **모니터링**: 30분 강화 모니터링
- **알림**: 중간 우선순위 알림
- **로그**: 상세 보안 로그 기록

### LOW 위협 (기본 대응)
- **로그만**: 단순 로그 기록
- **배치 알림**: 낮은 우선순위 배치 알림

---

## ✅ 완료 사항
- ✅ 실시간 보안 이벤트 체계적 처리
- ✅ 위협 수준별 자동 대응 시스템
- ✅ 주요 보안 공격 패턴 감지
- ✅ IP 기반 자동 차단 메커니즘
- ✅ 위협 인텔리전스 연돐
- ✅ 전체 시스템 보호 모드
- ✅ 악성 패턴 분석 및 저장
- ✅ 실시간 알림 내려보내기

---

*SecurityMonitoringService 보안 위협 탐지 핵심 기능 구현 완료*