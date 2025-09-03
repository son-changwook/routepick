# Step 7-2i: 감사 로깅 시스템 구현

## 📋 구현 목표
사용자의 민감한 행동과 보안 이벤트를 추적하는 포괄적인 감사 로깅 시스템:
1. **보안 이벤트 로깅** - 로그인, 권한 변경, 민감정보 접근
2. **사용자 행동 추적** - 프로필 조회, 팔로우 관계, 검색 기록
3. **비동기 로깅** - 성능 영향 최소화
4. **로그 분석** - 의심스러운 패턴 탐지

---

## 📊 AuditLogService 구현

### 📁 파일 위치
```
src/main/java/com/routepick/service/audit/AuditLogService.java
```

### 📝 구현 코드
```java
package com.routepick.service.audit;

import com.routepick.entity.audit.AuditLog;
import com.routepick.entity.audit.SecurityAuditLog;
import com.routepick.entity.audit.UserActivityLog;
import com.routepick.repository.audit.AuditLogRepository;
import com.routepick.repository.audit.SecurityAuditLogRepository;
import com.routepick.repository.audit.UserActivityLogRepository;
import com.routepick.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 감사 로깅 서비스
 * 
 * @author RoutePickProj  
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final SecurityAuditLogRepository securityAuditLogRepository;
    private final UserActivityLogRepository userActivityLogRepository;
    private final AuditLogAnalyzer auditLogAnalyzer;

    /**
     * 보안 이벤트 로깅
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSecurityEvent(SecurityEventType eventType, Long userId, 
                                String description, HttpServletRequest request) {
        try {
            SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .eventType(eventType.name())
                .userId(userId)
                .description(description)
                .ipAddress(getClientIpAddress(request))
                .userAgent(getUserAgent(request))
                .sessionId(getSessionId(request))
                .timestamp(LocalDateTime.now())
                .severity(eventType.getSeverity())
                .build();

            securityAuditLogRepository.save(auditLog);
            
            // 고위험 이벤트는 즉시 분석
            if (eventType.isHighRisk()) {
                auditLogAnalyzer.analyzeImmediately(auditLog);
            }

            log.info("Security event logged: type={}, user={}, ip={}", 
                    eventType, userId, auditLog.getIpAddress());

        } catch (Exception e) {
            log.error("Failed to log security event: type={}, user={}", 
                     eventType, userId, e);
        }
    }

    /**
     * 민감정보 접근 로깅
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSensitiveAccess(Long accessorUserId, Long targetUserId, 
                                  String accessType, HttpServletRequest request) {
        try {
            SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .eventType(SecurityEventType.SENSITIVE_DATA_ACCESS.name())
                .userId(accessorUserId)
                .description(String.format("Accessed %s of user %d", accessType, targetUserId))
                .ipAddress(getClientIpAddress(request))
                .userAgent(getUserAgent(request))
                .sessionId(getSessionId(request))
                .timestamp(LocalDateTime.now())
                .severity("MEDIUM")
                .additionalData(Map.of(
                    "targetUserId", targetUserId.toString(),
                    "accessType", accessType,
                    "relationship", getRelationship(accessorUserId, targetUserId)
                ))
                .build();

            securityAuditLogRepository.save(auditLog);

            // 비정상적인 접근 패턴 체크
            auditLogAnalyzer.checkAbnormalAccessPattern(accessorUserId, targetUserId);

            log.debug("Sensitive access logged: accessor={}, target={}, type={}", 
                     accessorUserId, targetUserId, accessType);

        } catch (Exception e) {
            log.error("Failed to log sensitive access: accessor={}, target={}", 
                     accessorUserId, targetUserId, e);
        }
    }

    /**
     * 사용자 활동 로깅
     */
    @Async("auditExecutor") 
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserActivity(UserActivityType activityType, Long userId,
                               String description, HttpServletRequest request) {
        try {
            UserActivityLog activityLog = UserActivityLog.builder()
                .activityType(activityType.name())
                .userId(userId)
                .description(description)
                .ipAddress(getClientIpAddress(request))
                .userAgent(getUserAgent(request))
                .timestamp(LocalDateTime.now())
                .build();

            userActivityLogRepository.save(activityLog);

            log.debug("User activity logged: type={}, user={}", activityType, userId);

        } catch (Exception e) {
            log.error("Failed to log user activity: type={}, user={}", 
                     activityType, userId, e);
        }
    }

    /**
     * 프로필 조회 로깅
     */
    @Async("auditExecutor")
    public void logProfileView(Long viewerUserId, Long targetUserId, HttpServletRequest request) {
        // 본인 프로필 조회는 로깅하지 않음
        if (viewerUserId.equals(targetUserId)) {
            return;
        }

        logUserActivity(
            UserActivityType.PROFILE_VIEW,
            viewerUserId,
            String.format("Viewed profile of user %d", targetUserId),
            request
        );

        // 민감정보 접근으로도 기록
        logSensitiveAccess(viewerUserId, targetUserId, "PROFILE", request);
    }

    /**
     * 파일 업로드 로깅
     */
    @Async("auditExecutor")
    public void logFileUpload(Long userId, String fileName, long fileSize, 
                             String fileType, HttpServletRequest request) {
        logSecurityEvent(
            SecurityEventType.FILE_UPLOAD,
            userId,
            String.format("Uploaded file: name=%s, size=%d, type=%s", 
                         fileName, fileSize, fileType),
            request
        );
    }

    /**
     * 계정 비활성화 로깅
     */
    @Async("auditExecutor")
    public void logAccountDeactivation(Long userId, String reason, String deactivationType,
                                     HttpServletRequest request) {
        logSecurityEvent(
            SecurityEventType.ACCOUNT_DEACTIVATION,
            userId,
            String.format("Account deactivated: type=%s, reason=%s", deactivationType, reason),
            request
        );
    }

    /**
     * 팔로우 관계 변경 로깅
     */
    @Async("auditExecutor")
    public void logFollowAction(Long followerUserId, Long followingUserId, 
                               FollowActionType actionType, HttpServletRequest request) {
        logUserActivity(
            actionType == FollowActionType.FOLLOW ? 
                UserActivityType.FOLLOW_USER : UserActivityType.UNFOLLOW_USER,
            followerUserId,
            String.format("%s user %d", actionType.name().toLowerCase(), followingUserId),
            request
        );
    }

    /**
     * 검색 활동 로깅
     */
    @Async("auditExecutor")
    public void logSearchActivity(Long userId, String searchKeyword, String searchType,
                                 int resultCount, HttpServletRequest request) {
        logUserActivity(
            UserActivityType.SEARCH,
            userId,
            String.format("Searched: keyword=%s, type=%s, results=%d", 
                         maskSearchKeyword(searchKeyword), searchType, resultCount),
            request
        );
    }

    /**
     * 로그인 시도 로깅
     */
    @Async("auditExecutor")
    public void logLoginAttempt(String email, boolean success, String failureReason,
                               HttpServletRequest request) {
        SecurityEventType eventType = success ? 
            SecurityEventType.LOGIN_SUCCESS : SecurityEventType.LOGIN_FAILURE;

        logSecurityEvent(
            eventType,
            null, // 실패한 경우 userId는 null
            success ? "Login successful" : "Login failed: " + failureReason,
            request
        );
    }

    /**
     * 권한 변경 로깅
     */
    @Async("auditExecutor")
    public void logPermissionChange(Long userId, String permissionType, 
                                   String oldValue, String newValue,
                                   HttpServletRequest request) {
        logSecurityEvent(
            SecurityEventType.PERMISSION_CHANGE,
            userId,
            String.format("Permission changed: type=%s, old=%s, new=%s", 
                         permissionType, oldValue, newValue),
            request
        );
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty() && !"unknown".equalsIgnoreCase(xRealIP)) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }

    /**
     * User-Agent 추출
     */
    private String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }
        
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 500)) : "UNKNOWN";
    }

    /**
     * 세션 ID 추출
     */
    private String getSessionId(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }
        
        return request.getSession(false) != null ? request.getSession().getId() : "NO_SESSION";
    }

    /**
     * 사용자 관계 조회
     */
    private String getRelationship(Long accessorUserId, Long targetUserId) {
        if (accessorUserId.equals(targetUserId)) {
            return "SELF";
        }
        
        if (SecurityContextHolder.isFollowing(accessorUserId, targetUserId)) {
            if (SecurityContextHolder.isFollowing(targetUserId, accessorUserId)) {
                return "MUTUAL";
            }
            return "FOLLOWING";
        }
        
        return "STRANGER";
    }

    /**
     * 검색 키워드 마스킹 (개인정보 보호)
     */
    private String maskSearchKeyword(String keyword) {
        if (keyword == null || keyword.length() <= 2) {
            return "***";
        }
        
        // 이메일 형태는 완전 마스킹
        if (keyword.contains("@")) {
            return "***@***";
        }
        
        // 한국 휴대폰 번호 형태는 완전 마스킹  
        if (keyword.matches("^01[0-9]-\\d{3,4}-\\d{4}$")) {
            return "010-****-****";
        }
        
        // 일반 키워드는 부분 마스킹
        return keyword.charAt(0) + "***" + keyword.charAt(keyword.length() - 1);
    }

    /**
     * 보안 이벤트 타입
     */
    public enum SecurityEventType {
        LOGIN_SUCCESS("LOW", false),
        LOGIN_FAILURE("MEDIUM", true),
        LOGOUT("LOW", false),
        PASSWORD_CHANGE("MEDIUM", false),
        EMAIL_CHANGE("MEDIUM", false),
        PHONE_CHANGE("MEDIUM", false),
        PERMISSION_CHANGE("HIGH", true),
        ACCOUNT_DEACTIVATION("HIGH", true),
        ACCOUNT_REACTIVATION("MEDIUM", false),
        FILE_UPLOAD("MEDIUM", false),
        SENSITIVE_DATA_ACCESS("MEDIUM", false),
        MULTIPLE_LOGIN_ATTEMPTS("HIGH", true),
        SUSPICIOUS_ACTIVITY("HIGH", true);

        private final String severity;
        private final boolean highRisk;

        SecurityEventType(String severity, boolean highRisk) {
            this.severity = severity;
            this.highRisk = highRisk;
        }

        public String getSeverity() { return severity; }
        public boolean isHighRisk() { return highRisk; }
    }

    /**
     * 사용자 활동 타입
     */
    public enum UserActivityType {
        PROFILE_VIEW,
        PROFILE_UPDATE,
        FOLLOW_USER,
        UNFOLLOW_USER,
        SEARCH,
        POST_CREATE,
        POST_VIEW,
        COMMENT_CREATE,
        MESSAGE_SEND,
        ROUTE_RECORD,
        GYM_VISIT
    }

    /**
     * 팔로우 액션 타입
     */
    public enum FollowActionType {
        FOLLOW,
        UNFOLLOW
    }
}
```

---

## 🔍 AuditLogAnalyzer 구현

### 📁 파일 위치
```
src/main/java/com/routepick/service/audit/AuditLogAnalyzer.java
```

### 📝 구현 코드
```java
package com.routepick.service.audit;

import com.routepick.entity.audit.SecurityAuditLog;
import com.routepick.repository.audit.SecurityAuditLogRepository;
import com.routepick.service.notification.SecurityNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 감사 로그 분석 서비스
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogAnalyzer {

    private final SecurityAuditLogRepository auditLogRepository;
    private final SecurityNotificationService securityNotificationService;

    // 임계값 설정
    private static final int MAX_LOGIN_ATTEMPTS_PER_HOUR = 5;
    private static final int MAX_PROFILE_VIEWS_PER_MINUTE = 10;
    private static final int MAX_SEARCH_REQUESTS_PER_MINUTE = 20;

    /**
     * 즉시 보안 분석
     */
    @Async("auditAnalyzerExecutor")
    public void analyzeImmediately(SecurityAuditLog auditLog) {
        log.info("Analyzing high-risk security event: type={}, user={}", 
                auditLog.getEventType(), auditLog.getUserId());

        switch (auditLog.getEventType()) {
            case "LOGIN_FAILURE" -> analyzeLoginFailures(auditLog);
            case "PERMISSION_CHANGE" -> analyzePermissionChanges(auditLog);
            case "SUSPICIOUS_ACTIVITY" -> analyzeSuspiciousActivity(auditLog);
            default -> log.debug("No specific analysis for event type: {}", auditLog.getEventType());
        }
    }

    /**
     * 비정상적인 접근 패턴 체크
     */
    @Async("auditAnalyzerExecutor")
    public void checkAbnormalAccessPattern(Long accessorUserId, Long targetUserId) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        
        // 1시간 내 동일 사용자에 대한 접근 횟수 확인
        int accessCount = auditLogRepository.countSensitiveAccessByUserAndTimeRange(
            accessorUserId, targetUserId, oneHourAgo, LocalDateTime.now());

        if (accessCount > MAX_PROFILE_VIEWS_PER_MINUTE) {
            createSuspiciousActivityAlert(
                accessorUserId,
                "EXCESSIVE_PROFILE_ACCESS",
                String.format("User accessed profile %d times in 1 hour", accessCount),
                Map.of("targetUserId", targetUserId.toString(), "accessCount", String.valueOf(accessCount))
            );
        }

        // 서로 다른 사용자들에 대한 연속적인 접근 체크
        checkConsecutiveProfileAccess(accessorUserId);
    }

    /**
     * 로그인 실패 분석
     */
    private void analyzeLoginFailures(SecurityAuditLog auditLog) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        String ipAddress = auditLog.getIpAddress();

        // 동일 IP에서의 로그인 실패 횟수 확인
        int failureCount = auditLogRepository.countLoginFailuresByIpAndTimeRange(
            ipAddress, oneHourAgo, LocalDateTime.now());

        if (failureCount >= MAX_LOGIN_ATTEMPTS_PER_HOUR) {
            createSuspiciousActivityAlert(
                null,
                "BRUTE_FORCE_ATTACK",
                String.format("IP %s attempted login %d times in 1 hour", ipAddress, failureCount),
                Map.of("ipAddress", ipAddress, "attempts", String.valueOf(failureCount))
            );

            // IP 차단 요청
            securityNotificationService.requestIpBlocking(ipAddress, "Multiple login failures");
        }
    }

    /**
     * 권한 변경 분석
     */
    private void analyzePermissionChanges(SecurityAuditLog auditLog) {
        // 권한 변경은 항상 알림
        securityNotificationService.sendSecurityAlert(
            auditLog.getUserId(),
            "PERMISSION_CHANGE",
            "계정 권한이 변경되었습니다",
            auditLog.getDescription()
        );

        // 최근 권한 변경 빈도 체크
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        int changeCount = auditLogRepository.countPermissionChangesByUserAndTimeRange(
            auditLog.getUserId(), oneDayAgo, LocalDateTime.now());

        if (changeCount > 3) {
            createSuspiciousActivityAlert(
                auditLog.getUserId(),
                "FREQUENT_PERMISSION_CHANGES",
                String.format("User changed permissions %d times in 24 hours", changeCount),
                Map.of("changeCount", String.valueOf(changeCount))
            );
        }
    }

    /**
     * 의심스러운 활동 분석
     */
    private void analyzeSuspiciousActivity(SecurityAuditLog auditLog) {
        log.warn("Suspicious activity detected: user={}, description={}", 
                auditLog.getUserId(), auditLog.getDescription());

        // 관리자에게 즉시 알림
        securityNotificationService.sendAdminAlert(
            "SUSPICIOUS_ACTIVITY",
            auditLog.getDescription(),
            auditLog
        );
    }

    /**
     * 연속적인 프로필 접근 체크
     */
    private void checkConsecutiveProfileAccess(Long userId) {
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        
        List<SecurityAuditLog> recentAccess = auditLogRepository
            .findSensitiveAccessByUserAndTimeRange(userId, tenMinutesAgo, LocalDateTime.now());

        // 서로 다른 사용자들에 대한 접근 수 계산
        long uniqueTargets = recentAccess.stream()
            .map(log -> log.getAdditionalData().get("targetUserId"))
            .distinct()
            .count();

        if (uniqueTargets > 20) { // 10분 내 20명 이상의 다른 사용자 프로필 접근
            createSuspiciousActivityAlert(
                userId,
                "PROFILE_SCRAPING",
                String.format("User accessed %d different profiles in 10 minutes", uniqueTargets),
                Map.of("uniqueTargets", String.valueOf(uniqueTargets))
            );
        }
    }

    /**
     * 의심스러운 활동 알림 생성
     */
    private void createSuspiciousActivityAlert(Long userId, String alertType, 
                                             String description, Map<String, String> additionalData) {
        SecurityAuditLog suspiciousLog = SecurityAuditLog.builder()
            .eventType("SUSPICIOUS_ACTIVITY")
            .userId(userId)
            .description(description)
            .severity("HIGH")
            .timestamp(LocalDateTime.now())
            .additionalData(additionalData)
            .build();

        auditLogRepository.save(suspiciousLog);

        // 보안팀에 즉시 알림
        securityNotificationService.sendSecurityTeamAlert(alertType, description, suspiciousLog);

        log.warn("Suspicious activity alert created: type={}, user={}, description={}", 
                alertType, userId, description);
    }

    /**
     * 정기 로그 분석 (스케줄러에서 호출)
     */
    @Async("auditAnalyzerExecutor")
    public void performScheduledAnalysis() {
        log.info("Starting scheduled audit log analysis");

        try {
            analyzeDailyPatterns();
            analyzeUserBehaviorTrends();
            detectAnomalousActivity();
            
            log.info("Scheduled audit log analysis completed");
        } catch (Exception e) {
            log.error("Error during scheduled audit log analysis", e);
        }
    }

    /**
     * 일일 패턴 분석
     */
    private void analyzeDailyPatterns() {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        
        // 로그인 실패율 분석
        int totalLoginAttempts = auditLogRepository.countByEventTypeAndTimestamp(
            "LOGIN_SUCCESS", oneDayAgo, LocalDateTime.now()) +
            auditLogRepository.countByEventTypeAndTimestamp(
                "LOGIN_FAILURE", oneDayAgo, LocalDateTime.now());
                
        int loginFailures = auditLogRepository.countByEventTypeAndTimestamp(
            "LOGIN_FAILURE", oneDayAgo, LocalDateTime.now());

        if (totalLoginAttempts > 0) {
            double failureRate = (double) loginFailures / totalLoginAttempts;
            if (failureRate > 0.3) { // 30% 이상 실패율
                log.warn("High login failure rate detected: {}%", failureRate * 100);
                securityNotificationService.sendSecurityTeamAlert(
                    "HIGH_FAILURE_RATE",
                    String.format("Login failure rate: %.1f%%", failureRate * 100),
                    null
                );
            }
        }
    }

    /**
     * 사용자 행동 트렌드 분석
     */
    private void analyzeUserBehaviorTrends() {
        // TODO: 머신러닝 기반 이상 패턴 탐지
        log.debug("Analyzing user behavior trends");
    }

    /**
     * 이상 활동 탐지
     */
    private void detectAnomalousActivity() {
        // TODO: 통계 기반 이상치 탐지
        log.debug("Detecting anomalous activity");
    }
}
```

---

## 📱 AuditLogEventListener 구현

### 📁 파일 위치
```
src/main/java/com/routepick/service/audit/AuditLogEventListener.java
```

### 📝 구현 코드
```java
package com.routepick.service.audit;

import com.routepick.event.user.ProfileAccessEvent;
import com.routepick.event.user.FollowEvent;
import com.routepick.event.user.FileUploadEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 감사 로그 이벤트 리스너
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    private final AuditLogService auditLogService;

    /**
     * 프로필 접근 이벤트 처리
     */
    @EventListener
    public void handleProfileAccessEvent(ProfileAccessEvent event) {
        auditLogService.logProfileView(
            event.getAccessorUserId(),
            event.getTargetUserId(),
            event.getRequest()
        );
    }

    /**
     * 팔로우 이벤트 처리
     */
    @EventListener
    public void handleFollowEvent(FollowEvent event) {
        AuditLogService.FollowActionType actionType = event.isFollow() ? 
            AuditLogService.FollowActionType.FOLLOW : 
            AuditLogService.FollowActionType.UNFOLLOW;

        auditLogService.logFollowAction(
            event.getFollowerUserId(),
            event.getFollowingUserId(),
            actionType,
            event.getRequest()
        );
    }

    /**
     * 파일 업로드 이벤트 처리
     */
    @EventListener
    public void handleFileUploadEvent(FileUploadEvent event) {
        auditLogService.logFileUpload(
            event.getUserId(),
            event.getFileName(),
            event.getFileSize(),
            event.getFileType(),
            event.getRequest()
        );
    }
}
```

---

## 🏗️ Entity 클래스들

### 📝 SecurityAuditLog.java
```java
package com.routepick.entity.audit;

import com.routepick.entity.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "security_audit_logs", 
       indexes = {
           @Index(name = "idx_security_audit_user_time", columnList = "userId,timestamp"),
           @Index(name = "idx_security_audit_ip_time", columnList = "ipAddress,timestamp"),
           @Index(name = "idx_security_audit_event_time", columnList = "eventType,timestamp")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditLogId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 100)
    private String sessionId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 20)
    private String severity;

    @ElementCollection
    @CollectionTable(name = "security_audit_log_data", 
                    joinColumns = @JoinColumn(name = "audit_log_id"))
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value", length = 1000)
    private Map<String, String> additionalData;
}
```

### 📝 UserActivityLog.java
```java
package com.routepick.entity.audit;

import com.routepick.entity.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_activity_logs",
       indexes = {
           @Index(name = "idx_activity_user_time", columnList = "userId,timestamp"),
           @Index(name = "idx_activity_type_time", columnList = "activityType,timestamp")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long activityLogId;

    @Column(nullable = false, length = 50)
    private String activityType;

    @Column(nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
```

---

## 📊 Repository 클래스들

### 📝 SecurityAuditLogRepository.java
```java
package com.routepick.repository.audit;

import com.routepick.entity.audit.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {

    @Query("SELECT COUNT(s) FROM SecurityAuditLog s WHERE s.eventType = 'SENSITIVE_DATA_ACCESS' " +
           "AND s.userId = :userId AND JSON_EXTRACT(s.additionalData, '$.targetUserId') = :targetUserId " +
           "AND s.timestamp BETWEEN :startTime AND :endTime")
    int countSensitiveAccessByUserAndTimeRange(@Param("userId") Long userId,
                                             @Param("targetUserId") Long targetUserId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(s) FROM SecurityAuditLog s WHERE s.eventType = 'LOGIN_FAILURE' " +
           "AND s.ipAddress = :ipAddress AND s.timestamp BETWEEN :startTime AND :endTime")
    int countLoginFailuresByIpAndTimeRange(@Param("ipAddress") String ipAddress,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(s) FROM SecurityAuditLog s WHERE s.eventType = 'PERMISSION_CHANGE' " +
           "AND s.userId = :userId AND s.timestamp BETWEEN :startTime AND :endTime")
    int countPermissionChangesByUserAndTimeRange(@Param("userId") Long userId,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    @Query("SELECT s FROM SecurityAuditLog s WHERE s.eventType = 'SENSITIVE_DATA_ACCESS' " +
           "AND s.userId = :userId AND s.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY s.timestamp DESC")
    List<SecurityAuditLog> findSensitiveAccessByUserAndTimeRange(@Param("userId") Long userId,
                                                               @Param("startTime") LocalDateTime startTime,
                                                               @Param("endTime") LocalDateTime endTime);

    int countByEventTypeAndTimestamp(String eventType, LocalDateTime startTime, LocalDateTime endTime);
}
```

---

## 📋 설계 완료 사항
✅ **AuditLogService** - 종합적인 감사 로깅  
✅ **AuditLogAnalyzer** - 실시간 보안 분석  
✅ **AuditLogEventListener** - 이벤트 기반 로깅  
✅ **Entity & Repository** - 로그 데이터 저장소  

## 🎯 주요 특징
- **비동기 로깅** - 성능 영향 최소화
- **실시간 분석** - 의심스러운 활동 즉시 탐지
- **다층 보안** - 보안 이벤트, 사용자 활동, 민감정보 접근 분리
- **자동 알림** - 임계값 초과 시 자동 보안팀 알림
- **데이터 마스킹** - 로그에서도 민감정보 보호

## ⚙️ 설정 추가
```yaml
# application.yml  
spring:
  task:
    execution:
      pool:
        audit-executor:
          core-size: 2
          max-size: 10
          queue-capacity: 100
          thread-name-prefix: audit-
        audit-analyzer-executor:
          core-size: 1
          max-size: 5
          queue-capacity: 50
          thread-name-prefix: analyzer-
```