# 🔔 Step 6-5d2: Notification Channels & System Management

> 공지사항, 배너, 팝업 관리 및 채널별 알림 구현  
> 생성일: 2025-09-01  
> 분할 기준: 공지사항/배너/팝업 및 채널별 알림

---

## 🎯 설계 목표

- **공지사항**: Notice 엔티티 관리
- **배너/팝업**: Banner, AppPopup 관리  
- **채널별 알림**: FCM/이메일/인앱 채널 관리
- **특화 알림**: 도메인별 맞춤 알림 발송

---

## ✅ NotificationChannelService.java

```java
package com.routepick.service.notification;

import com.routepick.common.enums.BannerPosition;
import com.routepick.common.enums.PopupType;
import com.routepick.common.enums.NotificationType;
import com.routepick.domain.notification.entity.Notice;
import com.routepick.domain.notification.entity.Banner;
import com.routepick.domain.notification.entity.AppPopup;
import com.routepick.domain.notification.repository.NoticeRepository;
import com.routepick.domain.notification.repository.BannerRepository;
import com.routepick.domain.notification.repository.AppPopupRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.domain.payment.entity.PaymentRecord;
import com.routepick.domain.community.entity.Post;
import com.routepick.domain.community.entity.Comment;
import com.routepick.exception.user.UserException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 채널 및 시스템 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationChannelService {
    
    private final NoticeRepository noticeRepository;
    private final BannerRepository bannerRepository;
    private final AppPopupRepository popupRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    
    // 캐시 설정
    private static final String CACHE_NOTICES = "notices";
    private static final String CACHE_BANNERS = "banners";
    private static final String CACHE_POPUPS = "popups";
    
    // ===== 공지사항 관리 =====
    
    /**
     * 공지사항 생성
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
     * 공지사항 수정
     */
    @Transactional
    @CacheEvict(value = CACHE_NOTICES, allEntries = true)
    public Notice updateNotice(Long noticeId, String title, String content, 
                              boolean isPinned, boolean isImportant) {
        log.info("Updating notice: noticeId={}", noticeId);
        
        Notice notice = noticeRepository.findById(noticeId)
            .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));
        
        notice.updateTitle(title);
        notice.updateContent(content);
        notice.updatePinned(isPinned);
        notice.updateImportant(isImportant);
        
        return noticeRepository.save(notice);
    }
    
    /**
     * 공지사항 삭제 (비활성화)
     */
    @Transactional
    @CacheEvict(value = CACHE_NOTICES, allEntries = true)
    public void deleteNotice(Long noticeId) {
        log.info("Deleting notice: noticeId={}", noticeId);
        
        Notice notice = noticeRepository.findById(noticeId)
            .orElseThrow(() -> new RuntimeException("공지사항을 찾을 수 없습니다: " + noticeId));
        
        notice.markAsDeleted();
        noticeRepository.save(notice);
    }
    
    /**
     * 공지사항 조회수 증가
     */
    @Transactional
    public void incrementNoticeViewCount(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId).orElse(null);
        if (notice != null) {
            notice.incrementViewCount();
            noticeRepository.save(notice);
        }
    }
    
    /**
     * 활성 공지사항 조회
     */
    @Cacheable(value = CACHE_NOTICES, key = "'active_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<Notice> getActiveNotices(Pageable pageable) {
        log.debug("Getting active notices");
        return noticeRepository.findActiveNotices(pageable);
    }
    
    /**
     * 고정 공지사항 조회
     */
    @Cacheable(value = CACHE_NOTICES, key = "'pinned'")
    public List<Notice> getPinnedNotices() {
        log.debug("Getting pinned notices");
        return noticeRepository.findPinnedNotices();
    }
    
    // ===== 배너 관리 =====
    
    /**
     * 배너 생성
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
     * 배너 수정
     */
    @Transactional
    @CacheEvict(value = CACHE_BANNERS, allEntries = true)
    public Banner updateBanner(Long bannerId, String title, String imageUrl, String linkUrl,
                              LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Updating banner: bannerId={}", bannerId);
        
        Banner banner = bannerRepository.findById(bannerId)
            .orElseThrow(() -> new RuntimeException("배너를 찾을 수 없습니다: " + bannerId));
        
        banner.updateTitle(title);
        banner.updateImageUrl(imageUrl);
        banner.updateLinkUrl(linkUrl);
        banner.updatePeriod(startDate, endDate);
        
        return bannerRepository.save(banner);
    }
    
    /**
     * 배너 클릭 수 증가
     */
    @Transactional
    public void incrementBannerClickCount(Long bannerId) {
        Banner banner = bannerRepository.findById(bannerId).orElse(null);
        if (banner != null) {
            banner.incrementClickCount();
            bannerRepository.save(banner);
        }
    }
    
    /**
     * 활성 배너 조회
     */
    @Cacheable(value = CACHE_BANNERS, key = "#position")
    public List<Banner> getActiveBanners(BannerPosition position) {
        log.debug("Getting active banners: position={}", position);
        
        LocalDateTime now = LocalDateTime.now();
        return bannerRepository.findActiveBanners(position, now);
    }
    
    /**
     * 배너 표시 순서 변경
     */
    @Transactional
    @CacheEvict(value = CACHE_BANNERS, allEntries = true)
    public void updateBannerDisplayOrder(Long bannerId, Integer displayOrder) {
        Banner banner = bannerRepository.findById(bannerId)
            .orElseThrow(() -> new RuntimeException("배너를 찾을 수 없습니다: " + bannerId));
        
        banner.updateDisplayOrder(displayOrder);
        bannerRepository.save(banner);
    }
    
    // ===== 앱 팝업 관리 =====
    
    /**
     * 앱 팝업 생성
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
     * 팝업 수정
     */
    @Transactional
    @CacheEvict(value = CACHE_POPUPS, allEntries = true)
    public AppPopup updatePopup(Long popupId, String title, String content, String imageUrl,
                               LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Updating popup: popupId={}", popupId);
        
        AppPopup popup = popupRepository.findById(popupId)
            .orElseThrow(() -> new RuntimeException("팝업을 찾을 수 없습니다: " + popupId));
        
        popup.updateTitle(title);
        popup.updateContent(content);
        popup.updateImageUrl(imageUrl);
        popup.updatePeriod(startDate, endDate);
        
        return popupRepository.save(popup);
    }
    
    /**
     * 팝업 노출/클릭 수 증가
     */
    @Transactional
    public void incrementPopupStats(Long popupId, boolean isClick) {
        AppPopup popup = popupRepository.findById(popupId).orElse(null);
        if (popup != null) {
            if (isClick) {
                popup.incrementClickCount();
            } else {
                popup.incrementShowCount();
            }
            popupRepository.save(popup);
        }
    }
    
    /**
     * 활성 팝업 조회
     */
    @Cacheable(value = CACHE_POPUPS, key = "'active'")
    public List<AppPopup> getActivePopups() {
        log.debug("Getting active popups");
        
        LocalDateTime now = LocalDateTime.now();
        return popupRepository.findActivePopups(now);
    }
    
    /**
     * 타입별 활성 팝업 조회
     */
    @Cacheable(value = CACHE_POPUPS, key = "#popupType")
    public List<AppPopup> getActivePopupsByType(PopupType popupType) {
        log.debug("Getting active popups by type: {}", popupType);
        
        LocalDateTime now = LocalDateTime.now();
        return popupRepository.findActivePopupsByType(popupType, now);
    }
    
    // ===== 특화된 알림 발송 메서드들 =====
    
    /**
     * 결제 성공 알림
     */
    public void sendPaymentSuccessNotification(Long userId, PaymentRecord payment) {
        notificationService.sendNotification(
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
     * 결제 실패 알림
     */
    public void sendPaymentFailureNotification(Long userId, PaymentRecord payment, String errorMessage) {
        notificationService.sendNotification(
            userId,
            NotificationType.PAYMENT,
            "결제가 실패했습니다",
            String.format("%s 결제가 실패했습니다. 오류: %s", 
                         payment.getPaymentDescription(), errorMessage),
            "/payments/" + payment.getPaymentId(),
            payment.getPaymentId().toString()
        );
    }
    
    /**
     * 댓글 알림
     */
    public void sendCommentNotification(Long userId, Comment comment) {
        Post post = comment.getPost();
        User commenter = comment.getUser();
        
        notificationService.sendNotification(
            userId,
            NotificationType.COMMENT,
            "새 댓글이 달렸습니다",
            String.format("%s님이 \"%s\" 게시글에 댓글을 달았습니다: %s", 
                         commenter.getNickName(),
                         post.getTitle(), 
                         comment.getContent().length() > 50 ? 
                             comment.getContent().substring(0, 50) + "..." : comment.getContent()),
            "/posts/" + post.getPostId(),
            comment.getCommentId().toString()
        );
    }
    
    /**
     * 댓글의 댓글 알림 (대댓글)
     */
    public void sendReplyNotification(Long userId, Comment reply) {
        Comment parentComment = reply.getParentComment();
        User replier = reply.getUser();
        
        notificationService.sendNotification(
            userId,
            NotificationType.COMMENT,
            "댓글에 답글이 달렸습니다",
            String.format("%s님이 회원님의 댓글에 답글을 달았습니다: %s", 
                         replier.getNickName(),
                         reply.getContent().length() > 50 ? 
                             reply.getContent().substring(0, 50) + "..." : reply.getContent()),
            "/posts/" + reply.getPost().getPostId(),
            reply.getCommentId().toString()
        );
    }
    
    /**
     * 좋아요 알림
     */
    public void sendLikeNotification(Long userId, Post post, User liker) {
        notificationService.sendNotification(
            userId,
            NotificationType.LIKE,
            "게시글에 좋아요를 받았습니다",
            String.format("%s님이 \"%s\" 게시글에 좋아요를 눌렀습니다", 
                         liker.getNickName(), post.getTitle()),
            "/posts/" + post.getPostId(),
            post.getPostId().toString()
        );
    }
    
    /**
     * 팔로우 알림
     */
    public void sendFollowNotification(Long userId, User follower) {
        notificationService.sendNotification(
            userId,
            NotificationType.FOLLOW,
            "새로운 팔로워가 생겼습니다",
            String.format("%s님이 회원님을 팔로우하기 시작했습니다", 
                         follower.getNickName()),
            "/users/" + follower.getUserId(),
            follower.getUserId().toString()
        );
    }
    
    /**
     * 루트 추천 알림
     */
    public void sendRouteRecommendationNotification(Long userId, String routeTitle, Long routeId) {
        notificationService.sendNotification(
            userId,
            NotificationType.RECOMMENDATION,
            "새로운 루트를 추천드려요",
            String.format("회원님의 취향에 맞는 \"%s\" 루트를 추천드립니다!", routeTitle),
            "/routes/" + routeId,
            routeId.toString()
        );
    }
    
    /**
     * 시스템 점검 알림
     */
    public void sendMaintenanceNotification(String title, String content, 
                                          LocalDateTime startTime, LocalDateTime endTime) {
        // 모든 활성 사용자에게 알림
        List<Long> activeUserIds = userRepository.findActiveUserIds(
            LocalDateTime.now().minusDays(7) // 일주일 내 활동한 사용자
        );
        
        notificationService.sendBatchNotifications(
            activeUserIds,
            NotificationType.SYSTEM,
            title,
            content,
            null
        );
    }
    
    /**
     * 보안 알림
     */
    public void sendSecurityNotification(Long userId, String securityEvent, String ipAddress) {
        notificationService.sendNotification(
            userId,
            NotificationType.SECURITY,
            "보안 알림: " + securityEvent,
            String.format("비정상적인 접근이 감지되었습니다. IP: %s, 시간: %s", 
                         ipAddress, LocalDateTime.now()),
            "/settings/security",
            ipAddress
        );
    }
    
    // ===== 유틸리티 메서드들 =====
    
    /**
     * 전체 사용자에게 공지사항 알림 발송
     */
    @Async
    public void sendNoticeNotificationToAllUsers(Notice notice) {
        log.info("Sending notice notification to all users: noticeId={}", notice.getNoticeId());
        
        // 활성 사용자 ID 조회 (배치로 처리)
        List<Long> activeUserIds = userRepository.findActiveUserIds(
            LocalDateTime.now().minusDays(30)
        );
        
        // 배치 알림 발송
        notificationService.sendBatchNotifications(
            activeUserIds,
            NotificationType.NOTICE,
            notice.getTitle(),
            notice.getContent(),
            "/notices/" + notice.getNoticeId()
        );
    }
    
    /**
     * 다음 배너 표시 순서 조회
     */
    private Integer getNextBannerDisplayOrder(BannerPosition position) {
        Integer maxOrder = bannerRepository.getMaxDisplayOrder(position);
        return maxOrder != null ? maxOrder + 1 : 1;
    }
    
    // ===== 통계 및 분석 =====
    
    /**
     * 공지사항 통계 조회
     */
    public NoticeStatistics getNoticeStatistics() {
        long totalNotices = noticeRepository.count();
        long activeNotices = noticeRepository.countByIsActiveTrue();
        long pinnedNotices = noticeRepository.countByIsPinnedTrueAndIsActiveTrue();
        
        return NoticeStatistics.builder()
            .totalNotices(totalNotices)
            .activeNotices(activeNotices)
            .pinnedNotices(pinnedNotices)
            .build();
    }
    
    /**
     * 배너 통계 조회
     */
    public BannerStatistics getBannerStatistics() {
        long totalBanners = bannerRepository.count();
        long activeBanners = bannerRepository.countActiveNow(LocalDateTime.now());
        
        return BannerStatistics.builder()
            .totalBanners(totalBanners)
            .activeBanners(activeBanners)
            .build();
    }
    
    /**
     * 팝업 통계 조회
     */
    public PopupStatistics getPopupStatistics() {
        long totalPopups = popupRepository.count();
        long activePopups = popupRepository.countActiveNow(LocalDateTime.now());
        
        return PopupStatistics.builder()
            .totalPopups(totalPopups)
            .activePopups(activePopups)
            .build();
    }
    
    // ===== 통계 데이터 클래스들 =====
    
    @lombok.Getter
    @lombok.Builder
    public static class NoticeStatistics {
        private final long totalNotices;
        private final long activeNotices;
        private final long pinnedNotices;
    }
    
    @lombok.Getter
    @lombok.Builder
    public static class BannerStatistics {
        private final long totalBanners;
        private final long activeBanners;
    }
    
    @lombok.Getter
    @lombok.Builder
    public static class PopupStatistics {
        private final long totalPopups;
        private final long activePopups;
    }
}
```

---

## 📈 주요 특징

### 1. **공지사항 관리**
- 생성/수정/삭제 완전 지원
- 중요 공지 전체 알림 발송
- 고정 공지사항 관리
- 조회수 추적

### 2. **배너 시스템**
- 위치별 배너 관리
- 표시 기간 설정
- 표시 순서 관리
- 클릭 통계 수집

### 3. **팝업 관리**
- 타입별 팝업 분류
- 노출/클릭 통계
- 활성 기간 관리
- 다양한 팝업 타입 지원

### 4. **특화 알림**
- 도메인별 맞춤 알림
- 결제/댓글/좋아요/팔로우 알림
- 보안/시스템 점검 알림
- 루트 추천 알림

### 5. **통계 및 분석**
- 공지사항/배너/팝업 통계
- 활성 상태 모니터링
- 성과 분석 지원

---

**📝 연관 파일**: 
- step6-5d1_notification_core.md (알림 핵심)
- **완료일**: 2025-09-01