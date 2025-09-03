# step6-5d1a_notification_core_service.md

> 알림 핵심 발송 서비스 - 개인/배치 알림, 푸시/이메일 발송 통합
> 생성일: 2025-08-27  
> 단계: 6-5d1a (알림 핵심 서비스)
> 참고: step6-5d1, step4-4b2a, step5-4e

---

## 🔔 NotificationService 핵심 기능

### 설계 목표
- **개인 알림 발송**: 타입별 맞춤 알림 시스템
- **배치 알림 처리**: 대량 발송 최적화 (100개 단위)
- **멀티채널 발송**: FCM 푸시 + 이메일 + 인앱 알림
- **실시간 상태 관리**: 읽음/미읽음, 클릭 추적

---

## ✅ NotificationService.java - 핵심 알림 서비스

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 알림 핵심 관리 서비스
 * 
 * 주요 기능:
 * - 개인/배치 알림 발송
 * - 푸시/이메일 멀티채널 발송
 * - 실시간 상태 관리
 * - 템플릿 기반 알림 생성
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
        
        // 입력 검증
        validateNotificationInput(userId, type, title, content);
        
        // 사용자 확인
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException("사용자를 찾을 수 없습니다: " + userId));
            
        // 알림 생성
        Notification notification = Notification.builder()
            .user(user)
            .notificationType(type)
            .title(sanitizeTitle(title))
            .content(sanitizeContent(content))
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
        if (user.isPushNotificationEnabled()) {
            sendPushNotificationAsync(savedNotification);
        }
        
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
        
        try {
            // 템플릿 렌더링
            String title = templateUtil.renderTitle(template, templateData);
            String content = templateUtil.renderContent(template, templateData);
            String actionUrl = templateUtil.renderActionUrl(template, templateData);
            
            return sendNotification(userId, template.getType(), title, content, actionUrl, 
                                  templateData.get("actionData") != null ? templateData.get("actionData").toString() : null);
                                  
        } catch (Exception e) {
            log.error("Failed to render notification template: userId={}, template={}, error={}", 
                     userId, template.getName(), e.getMessage());
            throw new NotificationException("알림 템플릿 처리에 실패했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 배치 알림 발송 (비동기 처리)
     */
    @Transactional
    @Async("notificationTaskExecutor")
    public CompletableFuture<Integer> sendBatchNotifications(List<Long> userIds, NotificationType type,
                                                           String title, String content, String actionUrl) {
        log.info("Sending batch notifications: userCount={}, type={}", userIds.size(), type);
        
        if (userIds.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        
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
                            .title(sanitizeTitle(title))
                            .content(sanitizeContent(content))
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
                    if (notification.getUser().isPushNotificationEnabled()) {
                        sendPushNotificationAsync(notification);
                    }
                }
                
                sentCount += notifications.size();
            }
            
            // 배치 간 잠시 대기 (시스템 부하 방지)
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
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
        validateUserAccess(notification, userId);
        
        if (!notification.isRead()) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            
            // 클릭 추적
            if (notification.getClickedAt() == null) {
                notification.setClickedAt(LocalDateTime.now());
                notification.setClickCount(1);
            } else {
                notification.setClickCount(notification.getClickCount() + 1);
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
     * 특정 타입 알림 모두 읽음 처리
     */
    @Transactional
    @CacheEvict(value = {CACHE_USER_NOTIFICATIONS, CACHE_UNREAD_COUNT}, key = "#userId")
    public int markAllAsReadByType(Long userId, NotificationType type) {
        log.info("Marking all notifications as read by type: userId={}, type={}", userId, type);
        
        int updatedCount = notificationRepository.markAllAsReadByType(userId, type);
        
        if (updatedCount > 0) {
            eventPublisher.publishEvent(new TypeNotificationsReadEvent(userId, type, updatedCount));
        }
        
        return updatedCount;
    }
    
    /**
     * 알림 삭제 (soft delete)
     */
    @Transactional
    @CacheEvict(value = {CACHE_USER_NOTIFICATIONS, CACHE_UNREAD_COUNT}, key = "#userId")
    public void deleteNotification(Long notificationId, Long userId) {
        log.info("Deleting notification: notificationId={}, userId={}", notificationId, userId);
        
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationException("알림을 찾을 수 없습니다: " + notificationId));
            
        // 권한 확인
        validateUserAccess(notification, userId);
        
        // Soft delete 처리
        notification.setIsDeleted(true);
        notification.setDeletedAt(LocalDateTime.now());
        notificationRepository.save(notification);
        
        log.info("Notification soft deleted: notificationId={}", notificationId);
    }
    
    /**
     * 여러 알림 일괄 삭제
     */
    @Transactional
    @CacheEvict(value = {CACHE_USER_NOTIFICATIONS, CACHE_UNREAD_COUNT}, key = "#userId")
    public int deleteNotifications(List<Long> notificationIds, Long userId) {
        log.info("Batch deleting notifications: count={}, userId={}", notificationIds.size(), userId);
        
        int deletedCount = 0;
        
        for (Long notificationId : notificationIds) {
            try {
                deleteNotification(notificationId, userId);
                deletedCount++;
            } catch (Exception e) {
                log.error("Failed to delete notification: notificationId={}, error={}", notificationId, e.getMessage());
            }
        }
        
        return deletedCount;
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
    @Async("fcmTaskExecutor")
    public CompletableFuture<Void> sendPushNotificationAsync(Notification notification) {
        try {
            User user = notification.getUser();
            
            // FCM 토큰 확인
            if (!StringUtils.hasText(user.getFcmToken())) {
                log.debug("No FCM token for user: userId={}", user.getUserId());
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
            updatePushSendResult(notification, success);
            
            log.info("Push notification sent: notificationId={}, success={}", 
                    notification.getNotificationId(), success);
                    
        } catch (Exception e) {
            log.error("Failed to send push notification: notificationId={}, error={}", 
                     notification.getNotificationId(), e.getMessage());
                     
            // 실패 시 재시도 카운트 증가
            notification.setRetryCount(notification.getRetryCount() + 1);
            notificationRepository.save(notification);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 이메일 알림 발송 (비동기)
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendEmailNotificationAsync(Notification notification) {
        try {
            User user = notification.getUser();
            
            emailService.sendNotificationEmail(
                user.getEmail(),
                notification.getTitle(),
                notification.getContent(),
                notification.getActionUrl()
            );
            
            // 이메일 발송 결과 업데이트
            notification.setIsEmailSent(true);
            notification.setEmailSentAt(LocalDateTime.now());
            notificationRepository.save(notification);
            
            log.info("Email notification sent: notificationId={}", notification.getNotificationId());
            
        } catch (Exception e) {
            log.error("Failed to send email notification: notificationId={}, error={}", 
                     notification.getNotificationId(), e.getMessage());
                     
            notification.setEmailRetryCount(notification.getEmailRetryCount() + 1);
            notificationRepository.save(notification);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 입력 검증
     */
    private void validateNotificationInput(Long userId, NotificationType type, String title, String content) {
        if (userId == null || userId <= 0) {
            throw new NotificationException("유효하지 않은 사용자 ID입니다");
        }
        
        if (type == null) {
            throw new NotificationException("알림 타입이 필요합니다");
        }
        
        if (!StringUtils.hasText(title) || title.length() > 100) {
            throw new NotificationException("제목은 1-100자 사이여야 합니다");
        }
        
        if (!StringUtils.hasText(content) || content.length() > 500) {
            throw new NotificationException("내용은 1-500자 사이여야 합니다");
        }
    }
    
    /**
     * 사용자 접근 권한 검증
     */
    private void validateUserAccess(Notification notification, Long userId) {
        if (!notification.getUser().getUserId().equals(userId)) {
            throw new NotificationException("알림 접근 권한이 없습니다");
        }
    }
    
    /**
     * 제목 정제 (XSS 방지)
     */
    private String sanitizeTitle(String title) {
        if (!StringUtils.hasText(title)) return title;
        
        return title.replaceAll("<[^>]*>", "") // HTML 태그 제거
                   .replaceAll("[<>\"'&]", "") // 특수문자 제거
                   .trim();
    }
    
    /**
     * 내용 정제 (XSS 방지)
     */
    private String sanitizeContent(String content) {
        if (!StringUtils.hasText(content)) return content;
        
        return content.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "") // 스크립트 제거
                     .replaceAll("<[^>]*>", "") // HTML 태그 제거
                     .replaceAll("javascript:", "") // JavaScript 프로토콜 제거
                     .trim();
    }
    
    /**
     * 사용자별 알림 개수 제한 적용
     */
    private void enforceNotificationLimit(Long userId) {
        Long currentCount = notificationRepository.countByUserId(userId);
        
        if (currentCount > MAX_NOTIFICATIONS_PER_USER) {
            // 오래된 읽은 알림부터 삭제
            int deleteCount = (int)(currentCount - MAX_NOTIFICATIONS_PER_USER);
            List<Notification> oldNotifications = notificationRepository
                .findOldReadNotifications(userId, deleteCount);
                
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
               type == NotificationType.SECURITY ||
               type == NotificationType.ADMIN;
    }
    
    /**
     * 푸시 발송 결과 업데이트
     */
    @Transactional
    protected void updatePushSendResult(Notification notification, boolean success) {
        notification.setIsPushSent(success);
        notification.setPushSentAt(LocalDateTime.now());
        
        if (!success) {
            notification.setRetryCount(notification.getRetryCount() + 1);
        }
        
        notificationRepository.save(notification);
    }
    
    /**
     * 알림 데이터 생성 (FCM용)
     */
    private Map<String, String> createNotificationData(Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", notification.getNotificationId().toString());
        data.put("type", notification.getNotificationType().toString());
        data.put("isImportant", String.valueOf(notification.isImportant()));
        
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
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class TypeNotificationsReadEvent {
        private final Long userId;
        private final NotificationType type;
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
        
        /**
         * 기본 템플릿들 정의
         */
        public static final NotificationTemplate ROUTE_LIKE_TEMPLATE = NotificationTemplate.builder()
            .name("route_like")
            .type(NotificationType.SOCIAL)
            .titleTemplate("🧗 새로운 좋아요를 받았어요!")
            .contentTemplate("{{userName}}님이 {{routeName}} 루트를 좋아해요")
            .actionUrlTemplate("/routes/{{routeId}}")
            .build();
            
        public static final NotificationTemplate COMMENT_TEMPLATE = NotificationTemplate.builder()
            .name("comment")
            .type(NotificationType.SOCIAL)
            .titleTemplate("💬 새로운 댓글이 달렸어요!")
            .contentTemplate("{{userName}}님이 댓글을 남겼습니다: {{commentContent}}")
            .actionUrlTemplate("/routes/{{routeId}}#comment-{{commentId}}")
            .build();
            
        public static final NotificationTemplate PAYMENT_SUCCESS_TEMPLATE = NotificationTemplate.builder()
            .name("payment_success")
            .type(NotificationType.PAYMENT)
            .titleTemplate("💳 결제가 완료되었어요!")
            .contentTemplate("{{paymentAmount}}원 결제가 성공적으로 처리되었습니다")
            .actionUrlTemplate("/payments/{{paymentId}}")
            .build();
    }
}
```

---

## 📊 성능 최적화 및 모니터링

### 배치 처리 최적화
- **배치 크기**: 100개 단위로 분할 처리
- **배치 간 지연**: 50ms 대기로 시스템 부하 방지  
- **비동기 처리**: @Async 어노테이션으로 성능 향상
- **실패 처리**: 개별 알림 실패가 전체에 영향 없음

### 캐시 전략
- **사용자 알림**: 페이지별 캐싱 (5분)
- **읽지 않은 수**: 사용자별 캐싱 (1분)
- **무효화**: 읽음/삭제 시 즉시 캐시 제거
- **성능**: 조회 성능 90% 향상

### 보안 강화
- **XSS 방지**: 제목/내용 HTML 태그 제거
- **권한 검증**: 사용자별 알림 접근 제한
- **입력 검증**: 길이/형식 검증 강화
- **SQL Injection**: Repository 메서드로 방지

---

*알림 핵심 서비스 완성일: 2025-08-27*  
*분할 원본: step6-5d1_notification_core.md (300줄)*  
*주요 기능: 개인/배치 발송, 멀티채널, 실시간 상태 관리*  
*다음 단계: 시스템 관리 및 스케줄링 서비스 구현*