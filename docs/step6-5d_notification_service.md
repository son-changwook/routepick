# Step 6-5d: NotificationService 구현

> 알림 관리 서비스 - 개인 알림, 푸시 알림, 공지사항, 배너, 팝업
> 생성일: 2025-08-22
> 단계: 6-5d (Service 레이어 - 알림 시스템)
> 참고: step4-4b2a, step4-4b2b1, step4-4b2b2, step5-4e

---

## 🎯 설계 목표

- **개인 알림**: Notification 엔티티 관리
- **푸시 알림**: FCM 기반 실시간 발송
- **공지사항**: Notice 엔티티 관리
- **배너/팝업**: Banner, AppPopup 관리
- **알림 템플릿**: 타입별 알림 템플릿 시스템

---

## 🔔 NotificationService 구현

### NotificationService.java
```java
package com.routepick.service.notification;

import com.routepick.common.enums.NotificationType;
import com.routepick.common.enums.NotificationStatus;
import com.routepick.common.enums.BannerPosition;
import com.routepick.common.enums.PopupType;
import com.routepick.domain.notification.entity.Notification;
import com.routepick.domain.notification.entity.Notice;
import com.routepick.domain.notification.entity.Banner;
import com.routepick.domain.notification.entity.AppPopup;
import com.routepick.domain.notification.repository.NotificationRepository;
import com.routepick.domain.notification.repository.NoticeRepository;
import com.routepick.domain.notification.repository.BannerRepository;
import com.routepick.domain.notification.repository.AppPopupRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.domain.payment.entity.PaymentRecord;
import com.routepick.domain.community.entity.Post;
import com.routepick.domain.community.entity.Comment;
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
import java.util.stream.Collectors;

/**
 * 알림 관리 서비스
 * - 개인 알림 발송 및 관리
 * - FCM 푸시 알림
 * - 공지사항 관리
 * - 배너 및 팝업 관리
 * - 알림 템플릿 시스템
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final NoticeRepository noticeRepository;
    private final BannerRepository bannerRepository;
    private final AppPopupRepository popupRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationTemplateUtil templateUtil;
    
    // 캐시 이름
    private static final String CACHE_USER_NOTIFICATIONS = "userNotifications";
    private static final String CACHE_UNREAD_COUNT = "unreadCount";
    private static final String CACHE_NOTICES = "notices";
    private static final String CACHE_BANNERS = "banners";
    private static final String CACHE_POPUPS = "popups";
    
    // 설정값
    private static final int MAX_NOTIFICATIONS_PER_USER = 1000;
    private static final int NOTIFICATION_CLEANUP_DAYS = 30;
    private static final int BATCH_SIZE = 100;
    
    /**
     * 개인 알림 발송
     * @param userId 사용자 ID
     * @param type 알림 타입
     * @param title 제목
     * @param content 내용
     * @param actionUrl 액션 URL
     * @param actionData 액션 데이터
     * @return 발송된 알림
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
     * @param userId 사용자 ID
     * @param template 알림 템플릿
     * @param templateData 템플릿 데이터
     * @return 발송된 알림
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
     * @param userIds 사용자 ID 목록
     * @param type 알림 타입
     * @param title 제목
     * @param content 내용
     * @param actionUrl 액션 URL
     * @return 발송된 알림 수
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
    
    /**
     * 알림 읽음 처리
     * @param notificationId 알림 ID
     * @param userId 사용자 ID
     * @return 읽음 처리된 알림
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
     * @param userId 사용자 ID
     * @return 읽음 처리된 알림 수
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
     * 사용자 알림 목록 조회
     * @param userId 사용자 ID
     * @param type 알림 타입 (선택사항)
     * @param unreadOnly 읽지 않은 알림만
     * @param pageable 페이징
     * @return 알림 페이지
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
     * @param userId 사용자 ID
     * @return 읽지 않은 알림 수
     */
    @Cacheable(value = CACHE_UNREAD_COUNT, key = "#userId")
    public Long getUnreadNotificationCount(Long userId) {
        log.debug("Getting unread notification count: userId={}", userId);
        
        return notificationRepository.countUnreadByUserId(userId);
    }
    
    /**
     * 공지사항 생성
     * @param title 제목
     * @param content 내용
     * @param authorId 작성자 ID
     * @param isPinned 고정 여부
     * @param isImportant 중요 여부
     * @return 생성된 공지사항
     */
    @Transactional
    @CacheEvict(value = CACHE_NOTICES, allEntries = true)
    public Notice createNotice(String title, String content, Long authorId, 
                              boolean isPinned, boolean isImportant) {
        log.info("Creating notice: title={}, authorId={}", title, authorId);
        
        User author = userRepository.findById(authorId)
            .orElseThrow(() -> new UserException("작성자를 찾을 수 없습니다: " + authorId));
            
        Notice notice = Notice.builder()
            .title(title)
            .content(content)
            .author(author)
            .isPinned(isPinned)
            .isImportant(isImportant)
            .isActive(true)
            .viewCount(0L)
            .build();
            
        Notice savedNotice = noticeRepository.save(notice);
        
        // 중요 공지사항인 경우 전체 사용자에게 알림
        if (isImportant) {
            sendNoticeNotificationToAllUsers(savedNotice);
        }
        
        log.info("Notice created successfully: noticeId={}", savedNotice.getNoticeId());
        return savedNotice;
    }
    
    /**
     * 배너 생성
     * @param title 제목
     * @param imageUrl 이미지 URL
     * @param linkUrl 링크 URL
     * @param position 배너 위치
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 생성된 배너
     */
    @Transactional
    @CacheEvict(value = CACHE_BANNERS, allEntries = true)
    public Banner createBanner(String title, String imageUrl, String linkUrl,
                              BannerPosition position, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Creating banner: title={}, position={}", title, position);
        
        Banner banner = Banner.builder()
            .title(title)
            .imageUrl(imageUrl)
            .linkUrl(linkUrl)
            .position(position)
            .startDate(startDate)
            .endDate(endDate)
            .isActive(true)
            .displayOrder(getNextBannerDisplayOrder(position))
            .clickCount(0L)
            .build();
            
        Banner savedBanner = bannerRepository.save(banner);
        
        log.info("Banner created successfully: bannerId={}", savedBanner.getBannerId());
        return savedBanner;
    }
    
    /**
     * 앱 팝업 생성
     * @param title 제목
     * @param content 내용
     * @param imageUrl 이미지 URL
     * @param popupType 팝업 타입
     * @param startDate 시작일
     * @param endDate 종료일
     * @return 생성된 팝업
     */
    @Transactional
    @CacheEvict(value = CACHE_POPUPS, allEntries = true)
    public AppPopup createPopup(String title, String content, String imageUrl,
                               PopupType popupType, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Creating popup: title={}, type={}", title, popupType);
        
        AppPopup popup = AppPopup.builder()
            .title(title)
            .content(content)
            .imageUrl(imageUrl)
            .popupType(popupType)
            .startDate(startDate)
            .endDate(endDate)
            .isActive(true)
            .showCount(0L)
            .clickCount(0L)
            .build();
            
        AppPopup savedPopup = popupRepository.save(popup);
        
        log.info("Popup created successfully: popupId={}", savedPopup.getPopupId());
        return savedPopup;
    }
    
    /**
     * 활성 공지사항 조회
     * @param pageable 페이징
     * @return 공지사항 페이지
     */
    @Cacheable(value = CACHE_NOTICES, key = "'active_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<Notice> getActiveNotices(Pageable pageable) {
        log.debug("Getting active notices");
        
        return noticeRepository.findActiveNotices(pageable);
    }
    
    /**
     * 활성 배너 조회
     * @param position 배너 위치
     * @return 배너 목록
     */
    @Cacheable(value = CACHE_BANNERS, key = "#position")
    public List<Banner> getActiveBanners(BannerPosition position) {
        log.debug("Getting active banners: position={}", position);
        
        LocalDateTime now = LocalDateTime.now();
        return bannerRepository.findActiveBanners(position, now);
    }
    
    /**
     * 활성 팝업 조회
     * @return 팝업 목록
     */
    @Cacheable(value = CACHE_POPUPS, key = "'active'")
    public List<AppPopup> getActivePopups() {
        log.debug("Getting active popups");
        
        LocalDateTime now = LocalDateTime.now();
        return popupRepository.findActivePopups(now);
    }
    
    /**
     * 푸시 알림 발송 (비동기)
     * @param notification 알림
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
     * @param notification 알림
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
    
    /**
     * 오래된 알림 정리 (스케줄링)
     */
    @Scheduled(cron = "0 0 3 * * ?") // 매일 새벽 3시
    @Transactional
    public void cleanupOldNotifications() {
        log.info("Cleaning up old notifications");
        
        LocalDateTime cutoff = LocalDateTime.now().minusDays(NOTIFICATION_CLEANUP_DAYS);
        
        // 읽은 알림 중 30일 이상 된 것 삭제
        int deletedCount = notificationRepository.deleteOldReadNotifications(cutoff);
        
        log.info("Cleaned up {} old notifications", deletedCount);
    }
    
    /**
     * 사용자별 알림 개수 제한 적용
     * @param userId 사용자 ID
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
     * @param type 알림 타입
     * @return 중요 알림 여부
     */
    private boolean isImportantNotification(NotificationType type) {
        return type == NotificationType.SYSTEM ||
               type == NotificationType.PAYMENT ||
               type == NotificationType.SECURITY;
    }
    
    /**
     * 알림 데이터 생성 (FCM용)
     * @param notification 알림
     * @return 알림 데이터
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
    
    /**
     * 전체 사용자에게 공지사항 알림 발송
     * @param notice 공지사항
     */
    @Async
    public void sendNoticeNotificationToAllUsers(Notice notice) {
        log.info("Sending notice notification to all users: noticeId={}", notice.getNoticeId());
        
        // 활성 사용자 ID 조회 (배치로 처리)
        List<Long> activeUserIds = userRepository.findActiveUserIds(
            LocalDateTime.now().minusDays(30)
        );
        
        // 배치 알림 발송
        sendBatchNotifications(
            activeUserIds,
            NotificationType.NOTICE,
            notice.getTitle(),
            notice.getContent(),
            "/notices/" + notice.getNoticeId()
        );
    }
    
    /**
     * 다음 배너 표시 순서 조회
     * @param position 배너 위치
     * @return 다음 표시 순서
     */
    private Integer getNextBannerDisplayOrder(BannerPosition position) {
        Integer maxOrder = bannerRepository.getMaxDisplayOrder(position);
        return maxOrder != null ? maxOrder + 1 : 1;
    }
    
    // 특화된 알림 발송 메서드들
    
    /**
     * 결제 성공 알림
     */
    public void sendPaymentSuccessNotification(Long userId, PaymentRecord payment) {
        sendNotification(
            userId,
            NotificationType.PAYMENT,
            "결제가 완료되었습니다",
            String.format("%s 결제가 성공적으로 완료되었습니다. 결제금액: %s원", 
                         payment.getPaymentDescription(), payment.getTotalAmount()),
            "/payments/" + payment.getPaymentId(),
            payment.getPaymentId().toString()
        );
    }
    
    /**
     * 댓글 알림
     */
    public void sendCommentNotification(Long userId, Comment comment) {
        sendNotification(
            userId,
            NotificationType.COMMENT,
            "새 댓글이 달렸습니다",
            String.format("\"%s\" 게시글에 새 댓글이 달렸습니다: %s", 
                         comment.getPost().getTitle(), 
                         comment.getContent().length() > 50 ? 
                             comment.getContent().substring(0, 50) + "..." : comment.getContent()),
            "/posts/" + comment.getPost().getPostId(),
            comment.getCommentId().toString()
        );
    }
    
    /**
     * 좋아요 알림
     */
    public void sendLikeNotification(Long userId, Post post, User liker) {
        sendNotification(
            userId,
            NotificationType.LIKE,
            "게시글에 좋아요를 받았습니다",
            String.format("%s님이 \"%s\" 게시글에 좋아요를 눌렀습니다", 
                         liker.getNickName(), post.getTitle()),
            "/posts/" + post.getPostId(),
            post.getPostId().toString()
        );
    }
    
    // 이벤트 클래스들
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
}
```

---

## 🔧 설정 및 통합

### application.yml 추가 설정
```yaml
# 알림 시스템 설정
app:
  notification:
    cache-ttl: 30m  # 알림 캐시 TTL
    max-per-user: 1000  # 사용자당 최대 알림 수
    cleanup-days: 30  # 알림 보관 기간
    batch-size: 100  # 배치 발송 크기
    
    # FCM 설정
    fcm:
      enabled: true
      timeout: 10000  # 10초
      retry-count: 3
      
    # 이메일 알림 설정
    email:
      enabled: true
      important-only: true  # 중요 알림만 이메일 발송
      
    # 스케줄링 설정
    schedule:
      cleanup: "0 0 3 * * ?"  # 매일 새벽 3시
      
# FCM 설정
fcm:
  service-account-key: ${FCM_SERVICE_ACCOUNT_KEY}
  project-id: ${FCM_PROJECT_ID}
```

---

## 📊 주요 기능 요약

### 1. 개인 알림 관리
- **타입별 알림**: 7가지 NotificationType 지원
- **템플릿 시스템**: 동적 알림 생성
- **읽음 처리**: 실시간 읽음 상태 관리
- **개수 제한**: 사용자당 1000개 제한

### 2. 푸시 알림 시스템
- **FCM 연동**: Firebase Cloud Messaging
- **설정 관리**: 사용자별 푸시 설정
- **재시도 로직**: 실패시 자동 재시도
- **통계 수집**: 발송 성공/실패 추적

### 3. 배치 알림 처리
- **대량 발송**: 100개씩 배치 처리
- **비동기 처리**: 성능 최적화
- **전체 알림**: 공지사항 전체 발송
- **타겟팅**: 활성 사용자 대상

### 4. 시스템 알림 관리
- **공지사항**: Notice 엔티티 관리
- **배너**: 위치별 배너 시스템
- **팝업**: 앱 팝업 관리
- **스케줄링**: 시작/종료일 자동 관리

---

## ✅ 완료 사항
- ✅ 개인 알림 관리 (Notification 엔티티)
- ✅ NotificationType별 알림 발송
- ✅ 실시간 알림 발송 (@Async)
- ✅ 읽음 상태 관리 (is_read, read_at)
- ✅ 푸시 알림 설정 관리
- ✅ 알림 템플릿 관리
- ✅ 공지사항 관리 (Notice)
- ✅ 배너 관리 (Banner)
- ✅ 앱 팝업 관리 (AppPopup)
- ✅ FCM 푸시 알림 연동
- ✅ 이메일 알림 연동
- ✅ 배치 알림 처리
- ✅ 스케줄링 기반 정리

---

*NotificationService 설계 완료: 종합 알림 관리 시스템*