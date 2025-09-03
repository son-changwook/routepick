# 🔔 Step 6-5d1: Notification Core Service

> 알림 발송 핵심 로직 및 개인 알림 관리  
> 생성일: 2025-09-01  
> 분할 기준: 핵심 알림 발송 및 관리 기능

---

## 🎯 설계 목표

- **개인 알림**: Notification 엔티티 관리
- **알림 발송**: 개별/배치 알림 발송 시스템
- **읽음 처리**: 실시간 읽음 상태 관리
- **알림 템플릿**: 타입별 알림 템플릿 시스템

---

## ✅ NotificationService.java (핵심 기능)

```java
package com.routepick.service.notification;

import com.routepick.common.enums.NotificationType;
import com.routepick.common.enums.NotificationStatus;
import com.routepick.domain.notification.entity.Notification;
import com.routepick.domain.notification.repository.NotificationRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.notification.NotificationException;
import com.routepick.exception.user.UserException;
import com.routepick.service.fcm.FCMService;
import com.routepick.service.email.EmailService;
import com.routepick.util.NotificationTemplateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 알림 핵심 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationTemplateUtil templateUtil;
    
    // 캐시 설정
    private static final String CACHE_USER_NOTIFICATIONS = "userNotifications";
    private static final String CACHE_UNREAD_COUNT = "unreadCount";
    
    // 제한 설정
    private static final int MAX_NOTIFICATIONS_PER_USER = 1000;
    private static final int NOTIFICATION_CLEANUP_DAYS = 30;
    private static final int BATCH_SIZE = 100;
    
    // ===== 개인 알림 발송 =====
    
    /**
     * 개인 알림 발송
     */
    @Transactional
    @CacheEvict(value = {CACHE_USER_NOTIFICATIONS, CACHE_UNREAD_COUNT}, key = "#userId")
    public Notification sendNotification(Long userId, NotificationType type, String title, 
                                       String content, String actionUrl, String actionData) {
        log.info("Sending notification: userId={}, type={}, title={}", userId, type, title);
        
        // 사용자 확인
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException("사용자를 찾을 수 없습니다: " + userId));
            
        // 알림 생성
        Notification notification = Notification.builder()
            .user(user)
            .notificationType(type)
            .title(title)
            .content(content)
            .actionUrl(actionUrl)
            .actionData(actionData)
            .isRead(false)
            .isImportant(isImportantNotification(type))
            .isPushSent(false)
            .retryCount(0)
            .build();
            
        Notification savedNotification = notificationRepository.save(notification);
        
        // 사용자 알림 개수 제한 확인
        enforceNotificationLimit(userId);
        
        // 푸시 알림 발송 (비동기)
        sendPushNotificationAsync(savedNotification);
        
        // 이메일 알림 발송 (중요 알림만)
        if (savedNotification.isImportant() && user.isEmailNotificationEnabled()) {
            sendEmailNotificationAsync(savedNotification);
        }
        
        // 이벤트 발행
        eventPublisher.publishEvent(new NotificationSentEvent(savedNotification));
        
        log.info("Notification sent successfully: notificationId={}", savedNotification.getNotificationId());
        return savedNotification;
    }
    
    /**
     * 템플릿 기반 알림 발송
     */
    @Transactional
    public Notification sendTemplateNotification(Long userId, NotificationTemplate template, 
                                                Map<String, Object> templateData) {
        log.info("Sending template notification: userId={}, template={}", userId, template.getName());
        
        // 템플릿 렌더링
        String title = templateUtil.renderTitle(template, templateData);
        String content = templateUtil.renderContent(template, templateData);
        String actionUrl = templateUtil.renderActionUrl(template, templateData);
        
        return sendNotification(userId, template.getType(), title, content, actionUrl, 
                              templateData.get("actionData") != null ? templateData.get("actionData").toString() : null);
    }
    
    /**
     * 배치 알림 발송
     */
    @Transactional
    @Async
    public CompletableFuture<Integer> sendBatchNotifications(List<Long> userIds, NotificationType type,
                                                           String title, String content, String actionUrl) {
        log.info("Sending batch notifications: userCount={}, type={}", userIds.size(), type);
        
        int sentCount = 0;
        
        // 배치 크기로 분할 처리
        for (int i = 0; i < userIds.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, userIds.size());
            List<Long> batch = userIds.subList(i, endIndex);
            
            List<Notification> notifications = new ArrayList<>();
            
            for (Long userId : batch) {
                try {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        Notification notification = Notification.builder()
                            .user(user)
                            .notificationType(type)
                            .title(title)
                            .content(content)
                            .actionUrl(actionUrl)
                            .isRead(false)
                            .isImportant(isImportantNotification(type))
                            .isPushSent(false)
                            .retryCount(0)
                            .build();
                            
                        notifications.add(notification);
                    }
                } catch (Exception e) {
                    log.error("Failed to create notification for user {}: {}", userId, e.getMessage());
                }
            }
            
            // 배치 저장
            if (!notifications.isEmpty()) {
                notificationRepository.saveAll(notifications);
                
                // 푸시 알림 발송
                for (Notification notification : notifications) {
                    sendPushNotificationAsync(notification);
                }
                
                sentCount += notifications.size();
            }
        }
        
        log.info("Batch notifications sent: total={}", sentCount);
        return CompletableFuture.completedFuture(sentCount);
    }
    
    // ===== 알림 상태 관리 =====
    
    /**
     * 알림 읽음 처리
     */
    @Transactional
    @CacheEvict(value = {CACHE_USER_NOTIFICATIONS, CACHE_UNREAD_COUNT}, key = "#userId")
    public Notification markAsRead(Long notificationId, Long userId) {
        log.info("Marking notification as read: notificationId={}, userId={}", notificationId, userId);
        
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationException("알림을 찾을 수 없습니다: " + notificationId));
            
        // 권한 확인
        if (!notification.getUser().getUserId().equals(userId)) {
            throw new NotificationException("알림 읽음 처리 권한이 없습니다");
        }
        
        if (!notification.isRead()) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            
            // 클릭 횟수 증가
            if (notification.getClickedAt() == null) {
                notification.setClickedAt(LocalDateTime.now());
                notification.setClickCount(1);
            }
            
            notificationRepository.save(notification);
            
            // 이벤트 발행
            eventPublisher.publishEvent(new NotificationReadEvent(notification));
        }
        
        return notification;
    }
    
    /**
     * 모든 알림 읽음 처리
     */
    @Transactional
    @CacheEvict(value = {CACHE_USER_NOTIFICATIONS, CACHE_UNREAD_COUNT}, key = "#userId")
    public int markAllAsRead(Long userId) {
        log.info("Marking all notifications as read: userId={}", userId);
        
        int updatedCount = notificationRepository.markAllAsRead(userId);
        
        if (updatedCount > 0) {
            eventPublisher.publishEvent(new AllNotificationsReadEvent(userId, updatedCount));
        }
        
        return updatedCount;
    }
    
    /**
     * 알림 삭제
     */
    @Transactional
    @CacheEvict(value = {CACHE_USER_NOTIFICATIONS, CACHE_UNREAD_COUNT}, key = "#userId")
    public void deleteNotification(Long notificationId, Long userId) {
        log.info("Deleting notification: notificationId={}, userId={}", notificationId, userId);
        
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationException("알림을 찾을 수 없습니다: " + notificationId));
            
        // 권한 확인
        if (!notification.getUser().getUserId().equals(userId)) {
            throw new NotificationException("알림 삭제 권한이 없습니다");
        }
        
        notificationRepository.delete(notification);
    }
    
    // ===== 알림 조회 =====
    
    /**
     * 사용자 알림 목록 조회
     */
    @Cacheable(value = CACHE_USER_NOTIFICATIONS,
              key = "#userId + '_' + #type + '_' + #unreadOnly + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<Notification> getUserNotifications(Long userId, NotificationType type, 
                                                  boolean unreadOnly, Pageable pageable) {
        log.debug("Getting user notifications: userId={}, type={}, unreadOnly={}", userId, type, unreadOnly);
        
        if (type != null && unreadOnly) {
            return notificationRepository.findByUserIdAndTypeAndUnread(userId, type, pageable);
        } else if (type != null) {
            return notificationRepository.findByUserIdAndType(userId, type, pageable);
        } else if (unreadOnly) {
            return notificationRepository.findByUserIdAndUnread(userId, pageable);
        } else {
            return notificationRepository.findByUserId(userId, pageable);
        }
    }
    
    /**
     * 읽지 않은 알림 수 조회
     */
    @Cacheable(value = CACHE_UNREAD_COUNT, key = "#userId")
    public Long getUnreadNotificationCount(Long userId) {
        log.debug("Getting unread notification count: userId={}", userId);
        
        return notificationRepository.countUnreadByUserId(userId);
    }
    
    /**
     * 타입별 읽지 않은 알림 수 조회
     */
    public Map<NotificationType, Long> getUnreadCountByType(Long userId) {
        log.debug("Getting unread count by type: userId={}", userId);
        
        Map<NotificationType, Long> countMap = new HashMap<>();
        
        for (NotificationType type : NotificationType.values()) {
            Long count = notificationRepository.countUnreadByUserIdAndType(userId, type);
            countMap.put(type, count);
        }
        
        return countMap;
    }
    
    /**
     * 알림 상세 조회
     */
    public Optional<Notification> getNotification(Long notificationId, Long userId) {
        log.debug("Getting notification: notificationId={}, userId={}", notificationId, userId);
        
        return notificationRepository.findByIdAndUserId(notificationId, userId);
    }
    
    // ===== 푸시 알림 발송 =====
    
    /**
     * 푸시 알림 발송 (비동기)
     */
    @Async
    public CompletableFuture<Void> sendPushNotificationAsync(Notification notification) {
        try {
            User user = notification.getUser();
            
            // FCM 토큰 확인
            if (!StringUtils.hasText(user.getFcmToken())) {
                log.debug("No FCM token for user: userId={}", user.getUserId());
                return CompletableFuture.completedFuture(null);
            }
            
            // 푸시 알림 설정 확인
            if (!user.isPushNotificationEnabled()) {
                log.debug("Push notification disabled for user: userId={}", user.getUserId());
                return CompletableFuture.completedFuture(null);
            }
            
            // FCM 발송
            boolean success = fcmService.sendNotification(
                user.getFcmToken(),
                notification.getTitle(),
                notification.getContent(),
                createNotificationData(notification)
            );
            
            // 발송 결과 업데이트
            notification.setIsPushSent(success);
            notification.setPushSentAt(LocalDateTime.now());
            
            if (!success) {
                notification.setRetryCount(notification.getRetryCount() + 1);
            }
            
            notificationRepository.save(notification);
            
            log.info("Push notification sent: notificationId={}, success={}", 
                    notification.getNotificationId(), success);
                    
        } catch (Exception e) {
            log.error("Failed to send push notification: notificationId={}, error={}", 
                     notification.getNotificationId(), e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 이메일 알림 발송 (비동기)
     */
    @Async
    public CompletableFuture<Void> sendEmailNotificationAsync(Notification notification) {
        try {
            User user = notification.getUser();
            
            emailService.sendNotificationEmail(
                user.getEmail(),
                notification.getTitle(),
                notification.getContent(),
                notification.getActionUrl()
            );
            
            log.info("Email notification sent: notificationId={}", notification.getNotificationId());
            
        } catch (Exception e) {
            log.error("Failed to send email notification: notificationId={}, error={}", 
                     notification.getNotificationId(), e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== 시스템 알림 발송 =====
    
    /**
     * 시스템 알림 발송 (관리자용)
     */
    @Transactional
    public void sendSystemAlert(String alertType, String message, Map<String, Object> metadata) {
        log.info("Sending system alert: type={}, message={}", alertType, message);
        
        // 관리자 사용자 조회
        List<User> adminUsers = userRepository.findAdminUsers();
        
        for (User admin : adminUsers) {
            sendNotification(
                admin.getUserId(),
                NotificationType.SYSTEM,
                "시스템 알림: " + alertType,
                message,
                null,
                metadata.toString()
            );
        }
    }
    
    // ===== 정리 및 관리 =====
    
    /**
     * 오래된 알림 정리 (매일 새벽 3시)
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldNotifications() {
        log.info("Cleaning up old notifications");
        
        LocalDateTime cutoff = LocalDateTime.now().minusDays(NOTIFICATION_CLEANUP_DAYS);
        
        // 읽은 알림 중 30일 이상 된 것 삭제
        int deletedCount = notificationRepository.deleteOldReadNotifications(cutoff);
        
        log.info("Cleaned up {} old notifications", deletedCount);
    }
    
    /**
     * 실패한 푸시 알림 재시도 (1시간마다)
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void retryFailedPushNotifications() {
        log.debug("Retrying failed push notifications");
        
        List<Notification> failedNotifications = notificationRepository
            .findFailedPushNotifications(3); // 3회 미만 시도
        
        for (Notification notification : failedNotifications) {
            sendPushNotificationAsync(notification);
        }
        
        if (!failedNotifications.isEmpty()) {
            log.info("Retried {} failed push notifications", failedNotifications.size());
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 사용자별 알림 개수 제한 적용
     */
    private void enforceNotificationLimit(Long userId) {
        Long currentCount = notificationRepository.countByUserId(userId);
        
        if (currentCount > MAX_NOTIFICATIONS_PER_USER) {
            // 오래된 읽은 알림부터 삭제
            List<Notification> oldNotifications = notificationRepository
                .findOldReadNotifications(userId, currentCount - MAX_NOTIFICATIONS_PER_USER);
                
            notificationRepository.deleteAll(oldNotifications);
            
            log.info("Enforced notification limit for user: userId={}, deleted={}", 
                    userId, oldNotifications.size());
        }
    }
    
    /**
     * 중요 알림 여부 확인
     */
    private boolean isImportantNotification(NotificationType type) {
        return type == NotificationType.SYSTEM ||
               type == NotificationType.PAYMENT ||
               type == NotificationType.SECURITY;
    }
    
    /**
     * 알림 데이터 생성 (FCM용)
     */
    private Map<String, String> createNotificationData(Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", notification.getNotificationId().toString());
        data.put("type", notification.getNotificationType().toString());
        
        if (StringUtils.hasText(notification.getActionUrl())) {
            data.put("actionUrl", notification.getActionUrl());
        }
        
        if (StringUtils.hasText(notification.getActionData())) {
            data.put("actionData", notification.getActionData());
        }
        
        return data;
    }
    
    // ===== 이벤트 클래스들 =====
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class NotificationSentEvent {
        private final Notification notification;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class NotificationReadEvent {
        private final Notification notification;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class AllNotificationsReadEvent {
        private final Long userId;
        private final Integer count;
    }
    
    // ===== 알림 템플릿 클래스 =====
    
    @lombok.Getter
    @lombok.Builder
    public static class NotificationTemplate {
        private final String name;
        private final NotificationType type;
        private final String titleTemplate;
        private final String contentTemplate;
        private final String actionUrlTemplate;
    }
}
```

---

## 📈 주요 특징

### 1. **개인 알림 관리**
- 타입별 알림 발송
- 템플릿 기반 알림 생성
- 읽음 상태 실시간 관리
- 개수 제한 자동 적용

### 2. **배치 알림 처리**
- 대량 발송 최적화
- 100개씩 배치 처리
- 비동기 처리로 성능 향상
- 실패 처리 및 로깅

### 3. **푸시 알림 시스템**
- FCM 연동 푸시 발송
- 사용자 설정 기반 필터링
- 재시도 로직 구현
- 발송 결과 추적

### 4. **시스템 관리**
- 오래된 알림 자동 정리
- 실패한 푸시 재시도
- 관리자 시스템 알림
- 캐시 기반 성능 최적화

---

**📝 연관 파일**: 
- step6-5d2_notification_channels.md (채널별 구현)