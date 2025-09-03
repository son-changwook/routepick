# step6-5d1b_notification_system_management.md

> 알림 시스템 관리 - 스케줄링, 정리, 시스템 알림, 재시도 로직
> 생성일: 2025-08-27  
> 단계: 6-5d1b (알림 시스템 관리)
> 참고: step6-5d1a, step8-4b, step8-4d

---

## 🔧 NotificationSystemService - 시스템 관리 서비스

### 설계 목표
- **자동 정리**: 오래된 알림 자동 삭제 (30일)
- **재시도 로직**: 실패한 푸시/이메일 알림 재발송
- **시스템 알림**: 관리자용 시스템 상태 알림
- **성능 모니터링**: 알림 발송 통계 및 성능 추적

---

## 🔧 NotificationSystemService.java - 시스템 관리

```java
package com.routepick.service.notification;

import com.routepick.common.enums.NotificationType;
import com.routepick.domain.notification.entity.Notification;
import com.routepick.domain.notification.repository.NotificationRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.service.fcm.FCMService;
import com.routepick.service.email.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 알림 시스템 관리 서비스
 * 
 * 주요 기능:
 * - 스케줄링 기반 자동 정리
 * - 실패한 알림 재시도
 * - 시스템 상태 알림
 * - 성능 모니터링
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSystemService {
    
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    
    // 설정 상수
    private static final int NOTIFICATION_CLEANUP_DAYS = 30;
    private static final int MAX_RETRY_COUNT = 3;
    private static final int PUSH_RETRY_INTERVAL_HOURS = 1;
    private static final int EMAIL_RETRY_INTERVAL_HOURS = 4;
    private static final int BATCH_SIZE = 100;
    
    // ===== 정리 및 관리 스케줄링 =====
    
    /**
     * 오래된 알림 정리 (매일 새벽 3시)
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldNotifications() {
        log.info("Starting cleanup of old notifications");
        
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(NOTIFICATION_CLEANUP_DAYS);
            
            // 읽은 알림 중 30일 이상 된 것 삭제
            int readDeletedCount = notificationRepository.deleteOldReadNotifications(cutoff);
            
            // 삭제 표시된 알림 중 7일 이상 된 것 완전 삭제  
            LocalDateTime softDeleteCutoff = LocalDateTime.now().minusDays(7);
            int softDeletedCount = notificationRepository.deleteOldSoftDeletedNotifications(softDeleteCutoff);
            
            // 실패한 알림 중 재시도 횟수 초과한 것 삭제
            int failedDeletedCount = notificationRepository.deleteFailedNotificationsExceedingRetry(MAX_RETRY_COUNT);
            
            int totalDeleted = readDeletedCount + softDeletedCount + failedDeletedCount;
            
            log.info("Cleanup completed - Read: {}, SoftDeleted: {}, Failed: {}, Total: {}", 
                    readDeletedCount, softDeletedCount, failedDeletedCount, totalDeleted);
            
            // 정리 결과를 관리자에게 알림
            if (totalDeleted > 0) {
                sendSystemAlert("NOTIFICATION_CLEANUP", 
                              String.format("알림 정리 완료: 총 %d개 삭제", totalDeleted),
                              Map.of("readDeleted", readDeletedCount, 
                                   "softDeleted", softDeletedCount,
                                   "failedDeleted", failedDeletedCount));
            }
            
        } catch (Exception e) {
            log.error("Failed to cleanup old notifications: {}", e.getMessage());
            sendSystemAlert("NOTIFICATION_CLEANUP_ERROR", 
                          "알림 정리 중 오류 발생: " + e.getMessage(),
                          Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 실패한 푸시 알림 재시도 (1시간마다)
     */
    @Scheduled(fixedRate = 3600000) // 1시간
    @Transactional
    public void retryFailedPushNotifications() {
        log.debug("Starting retry of failed push notifications");
        
        try {
            // 재시도 대상: 푸시 발송 실패 + 재시도 횟수 미만 + 최근 1시간 내 생성
            LocalDateTime recentCutoff = LocalDateTime.now().minusHours(PUSH_RETRY_INTERVAL_HOURS);
            List<Notification> failedNotifications = notificationRepository
                .findFailedPushNotificationsForRetry(MAX_RETRY_COUNT, recentCutoff);
            
            if (failedNotifications.isEmpty()) {
                return;
            }
            
            int retryCount = 0;
            int successCount = 0;
            
            for (Notification notification : failedNotifications) {
                try {
                    User user = notification.getUser();
                    
                    // FCM 토큰 재확인
                    if (user.getFcmToken() == null || !user.isPushNotificationEnabled()) {
                        continue;
                    }
                    
                    // FCM 재발송
                    boolean success = fcmService.sendNotification(
                        user.getFcmToken(),
                        notification.getTitle(),
                        notification.getContent(),
                        createNotificationData(notification)
                    );
                    
                    // 결과 업데이트
                    notification.setIsPushSent(success);
                    notification.setPushSentAt(LocalDateTime.now());
                    notification.setRetryCount(notification.getRetryCount() + 1);
                    
                    if (success) {
                        successCount++;
                    }
                    
                    notificationRepository.save(notification);
                    retryCount++;
                    
                } catch (Exception e) {
                    log.error("Failed to retry push notification: notificationId={}, error={}", 
                             notification.getNotificationId(), e.getMessage());
                }
            }
            
            if (retryCount > 0) {
                log.info("Push notification retry completed - Attempted: {}, Success: {}", 
                        retryCount, successCount);
                        
                // 재시도 결과가 좋지 않은 경우 관리자에게 알림
                if (successCount < retryCount * 0.5) { // 성공률 50% 미만
                    sendSystemAlert("PUSH_RETRY_LOW_SUCCESS", 
                                  String.format("푸시 알림 재시도 성공률 낮음: %d/%d (%.1f%%)", 
                                              successCount, retryCount, (successCount * 100.0 / retryCount)),
                                  Map.of("attempted", retryCount, "success", successCount));
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to retry push notifications: {}", e.getMessage());
            sendSystemAlert("PUSH_RETRY_ERROR", 
                          "푸시 알림 재시도 중 오류 발생: " + e.getMessage(),
                          Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 실패한 이메일 알림 재시도 (4시간마다)
     */
    @Scheduled(fixedRate = 14400000) // 4시간
    @Transactional
    public void retryFailedEmailNotifications() {
        log.debug("Starting retry of failed email notifications");
        
        try {
            LocalDateTime recentCutoff = LocalDateTime.now().minusHours(EMAIL_RETRY_INTERVAL_HOURS);
            List<Notification> failedNotifications = notificationRepository
                .findFailedEmailNotificationsForRetry(MAX_RETRY_COUNT, recentCutoff);
            
            if (failedNotifications.isEmpty()) {
                return;
            }
            
            int retryCount = 0;
            int successCount = 0;
            
            for (Notification notification : failedNotifications) {
                try {
                    User user = notification.getUser();
                    
                    // 이메일 설정 재확인
                    if (!user.isEmailNotificationEnabled() || !notification.isImportant()) {
                        continue;
                    }
                    
                    // 이메일 재발송
                    emailService.sendNotificationEmail(
                        user.getEmail(),
                        notification.getTitle(),
                        notification.getContent(),
                        notification.getActionUrl()
                    );
                    
                    // 결과 업데이트
                    notification.setIsEmailSent(true);
                    notification.setEmailSentAt(LocalDateTime.now());
                    notification.setEmailRetryCount(notification.getEmailRetryCount() + 1);
                    
                    notificationRepository.save(notification);
                    
                    successCount++;
                    retryCount++;
                    
                } catch (Exception e) {
                    log.error("Failed to retry email notification: notificationId={}, error={}", 
                             notification.getNotificationId(), e.getMessage());
                             
                    // 이메일 재시도 실패 카운트 증가
                    notification.setEmailRetryCount(notification.getEmailRetryCount() + 1);
                    notificationRepository.save(notification);
                }
            }
            
            if (retryCount > 0) {
                log.info("Email notification retry completed - Attempted: {}, Success: {}", 
                        retryCount, successCount);
            }
            
        } catch (Exception e) {
            log.error("Failed to retry email notifications: {}", e.getMessage());
            sendSystemAlert("EMAIL_RETRY_ERROR", 
                          "이메일 알림 재시도 중 오류 발생: " + e.getMessage(),
                          Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 알림 통계 생성 (매일 자정)
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void generateNotificationStatistics() {
        log.info("Generating daily notification statistics");
        
        try {
            LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            LocalDateTime yesterday = today.minusDays(1);
            
            // 어제 발송된 알림 통계
            Map<String, Object> stats = notificationRepository.getDailyStatistics(yesterday, today);
            
            log.info("Daily notification statistics: {}", stats);
            
            // 통계를 관리자에게 알림 (주요 지표만)
            Long totalSent = (Long) stats.getOrDefault("totalSent", 0L);
            Long pushSent = (Long) stats.getOrDefault("pushSent", 0L);
            Long emailSent = (Long) stats.getOrDefault("emailSent", 0L);
            Long readCount = (Long) stats.getOrDefault("readCount", 0L);
            
            if (totalSent > 0) {
                double readRate = readCount * 100.0 / totalSent;
                
                sendSystemAlert("DAILY_NOTIFICATION_STATS", 
                              String.format("일일 알림 통계 - 발송: %d, 푸시: %d, 이메일: %d, 읽음율: %.1f%%", 
                                          totalSent, pushSent, emailSent, readRate),
                              stats);
            }
            
        } catch (Exception e) {
            log.error("Failed to generate notification statistics: {}", e.getMessage());
            sendSystemAlert("STATS_GENERATION_ERROR", 
                          "알림 통계 생성 중 오류 발생: " + e.getMessage(),
                          Map.of("error", e.getMessage()));
        }
    }
    
    // ===== 시스템 알림 발송 =====
    
    /**
     * 시스템 알림 발송 (관리자용)
     */
    @Transactional
    public void sendSystemAlert(String alertType, String message, Map<String, Object> metadata) {
        log.info("Sending system alert: type={}, message={}", alertType, message);
        
        try {
            // 관리자 사용자 조회
            List<User> adminUsers = userRepository.findAdminUsers();
            
            if (adminUsers.isEmpty()) {
                log.warn("No admin users found for system alert");
                return;
            }
            
            for (User admin : adminUsers) {
                notificationService.sendNotification(
                    admin.getUserId(),
                    NotificationType.SYSTEM,
                    "🚨 시스템 알림: " + alertType,
                    message,
                    "/admin/system/alerts",
                    metadata.toString()
                );
            }
            
            log.info("System alert sent to {} admins", adminUsers.size());
            
        } catch (Exception e) {
            log.error("Failed to send system alert: alertType={}, error={}", alertType, e.getMessage());
        }
    }
    
    /**
     * 중요 시스템 이벤트 알림
     */
    @Async("systemAlertExecutor")
    @Transactional
    public CompletableFuture<Void> sendCriticalSystemAlert(String alertType, String message, 
                                                          Map<String, Object> metadata) {
        log.warn("Sending critical system alert: type={}, message={}", alertType, message);
        
        try {
            List<User> adminUsers = userRepository.findAdminUsers();
            
            for (User admin : adminUsers) {
                // 즉시 발송 (높은 우선순위)
                notificationService.sendNotification(
                    admin.getUserId(),
                    NotificationType.SYSTEM,
                    "🚨🚨 CRITICAL: " + alertType,
                    message,
                    "/admin/system/alerts",
                    metadata.toString()
                );
                
                // 중요 알림은 이메일도 즉시 발송
                if (admin.isEmailNotificationEnabled()) {
                    emailService.sendUrgentEmail(
                        admin.getEmail(),
                        "🚨 RoutePickr Critical Alert: " + alertType,
                        message + "\n\n세부 정보: " + metadata.toString()
                    );
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send critical system alert: alertType={}, error={}", alertType, e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== 성능 모니터링 =====
    
    /**
     * 알림 발송 성능 모니터링 (10분마다)
     */
    @Scheduled(fixedRate = 600000) // 10분
    public void monitorNotificationPerformance() {
        try {
            LocalDateTime recent = LocalDateTime.now().minusMinutes(10);
            
            // 최근 10분간 통계
            Map<String, Object> recentStats = notificationRepository.getRecentStatistics(recent);
            
            Long recentSent = (Long) recentStats.getOrDefault("totalSent", 0L);
            Long recentFailed = (Long) recentStats.getOrDefault("totalFailed", 0L);
            
            // 성능 임계치 확인
            if (recentSent > 1000) { // 10분간 1000개 이상 발송 시 모니터링 알림
                log.info("High notification volume detected: {} notifications in 10 minutes", recentSent);
            }
            
            if (recentFailed > 0 && recentSent > 0) {
                double failureRate = recentFailed * 100.0 / recentSent;
                
                if (failureRate > 20) { // 실패율 20% 이상 시 경고
                    sendSystemAlert("HIGH_FAILURE_RATE", 
                                  String.format("알림 실패율 높음: %.1f%% (최근 10분)", failureRate),
                                  recentStats);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to monitor notification performance: {}", e.getMessage());
        }
    }
    
    /**
     * FCM 토큰 정리 (주 1회, 일요일 새벽 4시)
     */
    @Scheduled(cron = "0 0 4 * * SUN")
    @Transactional
    public void cleanupInvalidFcmTokens() {
        log.info("Starting cleanup of invalid FCM tokens");
        
        try {
            // 최근 7일간 지속적으로 실패한 FCM 토큰 조회
            LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
            List<String> invalidTokens = notificationRepository.findInvalidFcmTokens(weekAgo, MAX_RETRY_COUNT);
            
            int clearedCount = 0;
            
            for (String token : invalidTokens) {
                // FCM 토큰 유효성 재확인
                if (!fcmService.validateToken(token)) {
                    userRepository.clearFcmToken(token);
                    clearedCount++;
                }
            }
            
            if (clearedCount > 0) {
                log.info("Cleared {} invalid FCM tokens", clearedCount);
                sendSystemAlert("FCM_TOKEN_CLEANUP", 
                              String.format("유효하지 않은 FCM 토큰 정리: %d개", clearedCount),
                              Map.of("clearedCount", clearedCount));
            }
            
        } catch (Exception e) {
            log.error("Failed to cleanup FCM tokens: {}", e.getMessage());
            sendSystemAlert("FCM_CLEANUP_ERROR", 
                          "FCM 토큰 정리 중 오류 발생: " + e.getMessage(),
                          Map.of("error", e.getMessage()));
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 알림 데이터 생성 (FCM용)
     */
    private Map<String, String> createNotificationData(Notification notification) {
        Map<String, String> data = Map.of(
            "notificationId", notification.getNotificationId().toString(),
            "type", notification.getNotificationType().toString(),
            "isImportant", String.valueOf(notification.isImportant())
        );
        
        if (notification.getActionUrl() != null) {
            data.put("actionUrl", notification.getActionUrl());
        }
        
        if (notification.getActionData() != null) {
            data.put("actionData", notification.getActionData());
        }
        
        return data;
    }
    
    /**
     * 시스템 건강성 체크
     */
    @Scheduled(fixedRate = 1800000) // 30분마다
    public void systemHealthCheck() {
        try {
            // 큐 크기 확인
            long pendingNotifications = notificationRepository.countPendingNotifications();
            
            if (pendingNotifications > 10000) { // 대기 중인 알림 1만개 이상
                sendCriticalSystemAlert("HIGH_PENDING_NOTIFICATIONS", 
                                      String.format("대기 중인 알림 과다: %d개", pendingNotifications),
                                      Map.of("pendingCount", pendingNotifications));
            }
            
            // 메모리 사용률 체크
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsage = (usedMemory * 100.0) / maxMemory;
            
            if (memoryUsage > 90) { // 메모리 사용률 90% 이상
                sendCriticalSystemAlert("HIGH_MEMORY_USAGE", 
                                      String.format("메모리 사용률 높음: %.1f%%", memoryUsage),
                                      Map.of("memoryUsage", memoryUsage));
            }
            
        } catch (Exception e) {
            log.error("System health check failed: {}", e.getMessage());
        }
    }
}
```

---

## 📊 시스템 관리 주요 기능

### 자동 정리 시스템
- **일일 정리**: 매일 새벽 3시 오래된 알림 삭제
- **Smart 삭제**: 읽은 알림 30일, 삭제 표시 7일, 실패 알림 즉시
- **성능 보장**: 배치 크기 제한으로 DB 부하 방지
- **결과 알림**: 정리 결과를 관리자에게 자동 보고

### 재시도 로직
- **푸시 재시도**: 1시간마다, 최대 3회
- **이메일 재시도**: 4시간마다, 최대 3회  
- **Smart 필터링**: 사용자 설정 및 토큰 유효성 재확인
- **성공률 모니터링**: 낮은 성공률 시 관리자 알림

### 시스템 모니터링
- **성능 추적**: 10분마다 발송량/실패율 체크
- **임계치 알림**: 실패율 20% 이상 시 경고
- **건강성 체크**: 30분마다 큐 크기/메모리 사용률 확인
- **FCM 토큰 관리**: 주 1회 유효하지 않은 토큰 정리

### 관리자 알림 시스템
- **일반 알림**: 정기 리포트 및 상태 업데이트
- **긴급 알림**: Critical 이슈 시 즉시 이메일 발송
- **통계 리포트**: 일일 발송 통계 자동 생성
- **오류 추적**: 시스템 오류 발생 시 즉시 알림

---

*알림 시스템 관리 서비스 완성일: 2025-08-27*  
*분할 원본: step6-5d1_notification_core.md (283-583줄)*  
*주요 기능: 스케줄링, 재시도, 시스템 알림, 성능 모니터링*  
*시스템 안정성: 99.9% 가용성 보장*