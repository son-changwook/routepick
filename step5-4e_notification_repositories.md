# Step 5-4e: 알림 시스템 Repository 생성

## 개요
- **목적**: 알림 시스템 Repository 생성 (실시간 처리 및 대용량 배치 최적화)
- **대상**: NotificationRepository, NoticeRepository, BannerRepository, AppPopupRepository
- **최적화**: 실시간 알림 처리, 대용량 알림 배치 처리, 읽음 상태 관리

## 1. NotificationRepository (개인 알림 최적화)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.notification;

import com.routepick.backend.domain.entity.notification.Notification;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 개인 알림 Repository
 * - 실시간 알림 처리 최적화
 * - 읽음/안읽음 상태 관리
 * - FCM 푸시 알림 지원
 */
@Repository
public interface NotificationRepository extends BaseRepository<Notification, Long> {
    
    // 기본 조회 메서드
    List<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(
        Long userId, boolean isRead);
    
    Page<Notification> findByUserIdOrderByCreatedAtDesc(
        Long userId, Pageable pageable);
    
    // 안읽은 알림 카운트
    long countByUserIdAndIsReadFalse(Long userId);
    
    @Query("SELECT COUNT(n) FROM Notification n " +
           "WHERE n.userId = :userId AND n.isRead = false " +
           "AND n.createdAt >= :since")
    long countUnreadNotificationsSince(
        @Param("userId") Long userId,
        @Param("since") LocalDateTime since);
    
    // 알림 타입별 조회
    List<Notification> findByUserIdAndNotificationTypeOrderByCreatedAtDesc(
        Long userId, String notificationType);
    
    @Query("SELECT n FROM Notification n " +
           "WHERE n.userId = :userId " +
           "AND n.notificationType IN :types " +
           "AND n.createdAt >= :since " +
           "ORDER BY n.isRead ASC, n.createdAt DESC")
    List<Notification> findByUserIdAndTypesAndSince(
        @Param("userId") Long userId,
        @Param("types") List<String> types,
        @Param("since") LocalDateTime since);
    
    // 중요 알림 조회
    @Query("SELECT n FROM Notification n " +
           "WHERE n.userId = :userId " +
           "AND n.isImportant = true " +
           "AND n.isRead = false " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findImportantUnreadNotifications(@Param("userId") Long userId);
    
    // 읽음 처리
    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.id = :notificationId AND n.userId = :userId")
    int markAsRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId);
    
    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);
    
    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.id IN :notificationIds AND n.userId = :userId")
    int markMultipleAsRead(@Param("notificationIds") List<Long> notificationIds, 
                          @Param("userId") Long userId);
    
    // 알림 삭제
    @Transactional
    @Modifying
    @Query("DELETE FROM Notification n " +
           "WHERE n.userId = :userId AND n.createdAt < :beforeDate")
    int deleteOldNotifications(@Param("userId") Long userId, 
                              @Param("beforeDate") LocalDateTime beforeDate);
    
    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.isDeleted = true, n.deletedAt = CURRENT_TIMESTAMP " +
           "WHERE n.id = :notificationId AND n.userId = :userId")
    int softDeleteNotification(@Param("notificationId") Long notificationId, 
                              @Param("userId") Long userId);
    
    // 푸시 알림 관련
    @Query("SELECT n FROM Notification n " +
           "WHERE n.isPushSent = false " +
           "AND n.shouldSendPush = true " +
           "AND n.createdAt >= :since " +
           "ORDER BY n.createdAt ASC")
    List<Notification> findPendingPushNotifications(@Param("since") LocalDateTime since);
    
    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.isPushSent = true, n.pushSentAt = CURRENT_TIMESTAMP " +
           "WHERE n.id IN :notificationIds")
    int markPushSent(@Param("notificationIds") List<Long> notificationIds);
    
    // 알림 통계
    @Query("SELECT n.notificationType, COUNT(n), " +
           "SUM(CASE WHEN n.isRead = true THEN 1 ELSE 0 END) as readCount " +
           "FROM Notification n " +
           "WHERE n.userId = :userId AND n.createdAt >= :since " +
           "GROUP BY n.notificationType")
    List<Object[]> getNotificationStatistics(@Param("userId") Long userId, 
                                            @Param("since") LocalDateTime since);
    
    // 대용량 배치 생성
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO notifications (user_id, notification_type, title, content, " +
                   "is_read, is_important, created_at) " +
                   "SELECT u.id, :notificationType, :title, :content, false, :isImportant, NOW() " +
                   "FROM users u WHERE u.is_active = true AND u.id IN :userIds",
           nativeQuery = true)
    int batchCreateNotifications(@Param("userIds") List<Long> userIds,
                                @Param("notificationType") String notificationType,
                                @Param("title") String title,
                                @Param("content") String content,
                                @Param("isImportant") boolean isImportant);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.notification.custom;

import com.routepick.backend.application.dto.notification.NotificationSearchCriteria;
import com.routepick.backend.application.dto.projection.NotificationSummaryProjection;
import com.routepick.backend.domain.entity.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 개인 알림 커스텀 Repository
 */
public interface NotificationRepositoryCustom {
    
    // 고급 검색
    Page<Notification> searchNotifications(NotificationSearchCriteria criteria, Pageable pageable);
    
    // 알림 그룹화 조회
    Map<String, List<Notification>> getGroupedNotifications(Long userId, LocalDateTime since);
    
    // 알림 요약 정보
    NotificationSummaryProjection getNotificationSummary(Long userId);
    
    // 실시간 알림 스트림
    List<Notification> getRealtimeNotifications(Long userId, Long lastNotificationId);
    
    // 대용량 배치 처리
    void batchSendNotifications(List<Long> userIds, Notification template);
    
    // 알림 집계
    Map<String, Long> aggregateNotificationsByType(Long userId, LocalDateTime startDate, LocalDateTime endDate);
}
```

## 2. NoticeRepository (공지사항 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.notification;

import com.routepick.backend.domain.entity.notification.Notice;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공지사항 Repository
 * - 중요 공지 우선 노출
 * - 기간별 공지 관리
 * - 조회수 추적
 */
@Repository
public interface NoticeRepository extends BaseRepository<Notice, Long> {
    
    // 중요 공지사항 조회
    List<Notice> findByIsImportantTrueOrderByStartDateDesc();
    
    @Query("SELECT n FROM Notice n " +
           "WHERE n.isImportant = true " +
           "AND n.isActive = true " +
           "AND n.startDate <= CURRENT_TIMESTAMP " +
           "AND (n.endDate IS NULL OR n.endDate >= CURRENT_TIMESTAMP) " +
           "ORDER BY n.displayOrder ASC, n.startDate DESC")
    List<Notice> findActiveImportantNotices();
    
    // 활성 공지사항 조회
    @Query("SELECT n FROM Notice n " +
           "WHERE n.isActive = true " +
           "AND n.startDate <= CURRENT_TIMESTAMP " +
           "AND (n.endDate IS NULL OR n.endDate >= CURRENT_TIMESTAMP) " +
           "ORDER BY n.isImportant DESC, n.displayOrder ASC, n.startDate DESC")
    Page<Notice> findActiveNotices(Pageable pageable);
    
    @Query("SELECT n FROM Notice n " +
           "WHERE n.isActive = true " +
           "AND n.noticeType = :noticeType " +
           "AND n.startDate <= CURRENT_TIMESTAMP " +
           "AND (n.endDate IS NULL OR n.endDate >= CURRENT_TIMESTAMP) " +
           "ORDER BY n.displayOrder ASC, n.startDate DESC")
    List<Notice> findActiveNoticesByType(@Param("noticeType") String noticeType);
    
    // 기간별 공지사항
    List<Notice> findByStartDateBetweenOrderByStartDateDesc(
        LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT n FROM Notice n " +
           "WHERE n.startDate <= :date " +
           "AND (n.endDate IS NULL OR n.endDate >= :date) " +
           "AND n.isActive = true " +
           "ORDER BY n.isImportant DESC, n.displayOrder ASC")
    List<Notice> findNoticesValidAt(@Param("date") LocalDateTime date);
    
    // 조회수 증가
    @Modifying
    @Query("UPDATE Notice n SET n.viewCount = n.viewCount + 1 " +
           "WHERE n.id = :noticeId")
    void incrementViewCount(@Param("noticeId") Long noticeId);
    
    // 카테고리별 조회
    List<Notice> findByCategoryAndIsActiveTrueOrderByStartDateDesc(String category);
    
    @Query("SELECT n.category, COUNT(n) FROM Notice n " +
           "WHERE n.isActive = true " +
           "GROUP BY n.category")
    List<Object[]> countByCategory();
    
    // 만료된 공지 비활성화
    @Modifying
    @Query("UPDATE Notice n SET n.isActive = false " +
           "WHERE n.endDate < CURRENT_TIMESTAMP AND n.isActive = true")
    int deactivateExpiredNotices();
    
    // 인기 공지사항
    @Query("SELECT n FROM Notice n " +
           "WHERE n.isActive = true " +
           "ORDER BY n.viewCount DESC")
    List<Notice> findPopularNotices(Pageable pageable);
    
    // 검색
    @Query("SELECT n FROM Notice n " +
           "WHERE n.isActive = true " +
           "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY n.startDate DESC")
    Page<Notice> searchNotices(@Param("keyword") String keyword, Pageable pageable);
}
```

## 3. BannerRepository (배너 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.notification;

import com.routepick.backend.domain.entity.notification.Banner;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배너 Repository
 * - 순서 기반 배너 노출
 * - 기간별 배너 관리
 * - 클릭률 추적
 */
@Repository
public interface BannerRepository extends BaseRepository<Banner, Long> {
    
    // 활성 배너 조회 (순서대로)
    List<Banner> findByIsActiveTrueOrderByDisplayOrderAsc();
    
    @Query("SELECT b FROM Banner b " +
           "WHERE b.isActive = true " +
           "AND b.bannerType = :bannerType " +
           "ORDER BY b.displayOrder ASC")
    List<Banner> findActiveByType(@Param("bannerType") String bannerType);
    
    // 기간 유효 배너
    @Query("SELECT b FROM Banner b " +
           "WHERE b.startDate <= :currentDate " +
           "AND b.endDate >= :currentDate " +
           "AND b.isActive = true " +
           "ORDER BY b.displayOrder ASC")
    List<Banner> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
        @Param("currentDate") LocalDateTime currentDate);
    
    // 위치별 배너
    @Query("SELECT b FROM Banner b " +
           "WHERE b.position = :position " +
           "AND b.isActive = true " +
           "AND b.startDate <= CURRENT_TIMESTAMP " +
           "AND b.endDate >= CURRENT_TIMESTAMP " +
           "ORDER BY b.displayOrder ASC")
    List<Banner> findByPosition(@Param("position") String position);
    
    // 타겟 사용자별 배너
    @Query("SELECT b FROM Banner b " +
           "WHERE b.isActive = true " +
           "AND (b.targetUserType = 'ALL' OR b.targetUserType = :userType) " +
           "AND b.startDate <= CURRENT_TIMESTAMP " +
           "AND b.endDate >= CURRENT_TIMESTAMP " +
           "ORDER BY b.displayOrder ASC")
    List<Banner> findBannersForUserType(@Param("userType") String userType);
    
    // 클릭/노출 통계 업데이트
    @Modifying
    @Query("UPDATE Banner b SET b.impressionCount = b.impressionCount + 1 " +
           "WHERE b.id = :bannerId")
    void incrementImpressionCount(@Param("bannerId") Long bannerId);
    
    @Modifying
    @Query("UPDATE Banner b SET b.clickCount = b.clickCount + 1 " +
           "WHERE b.id = :bannerId")
    void incrementClickCount(@Param("bannerId") Long bannerId);
    
    // 클릭률 계산
    @Query("SELECT b.id, b.title, b.clickCount, b.impressionCount, " +
           "CASE WHEN b.impressionCount > 0 THEN (b.clickCount * 100.0 / b.impressionCount) ELSE 0 END as ctr " +
           "FROM Banner b " +
           "WHERE b.isActive = true " +
           "ORDER BY ctr DESC")
    List<Object[]> calculateClickThroughRates();
    
    // 순서 조정
    @Modifying
    @Query("UPDATE Banner b SET b.displayOrder = b.displayOrder + 1 " +
           "WHERE b.position = :position " +
           "AND b.displayOrder >= :fromOrder")
    void shiftDisplayOrder(@Param("position") String position, 
                          @Param("fromOrder") Integer fromOrder);
    
    // 만료 배너 비활성화
    @Modifying
    @Query("UPDATE Banner b SET b.isActive = false " +
           "WHERE b.endDate < CURRENT_TIMESTAMP AND b.isActive = true")
    int deactivateExpiredBanners();
    
    // 캠페인별 배너
    List<Banner> findByCampaignIdAndIsActiveTrueOrderByDisplayOrderAsc(String campaignId);
    
    // A/B 테스트 배너
    @Query("SELECT b FROM Banner b " +
           "WHERE b.isActive = true " +
           "AND b.testGroup = :testGroup " +
           "AND b.startDate <= CURRENT_TIMESTAMP " +
           "AND b.endDate >= CURRENT_TIMESTAMP " +
           "ORDER BY b.displayOrder ASC")
    List<Banner> findByTestGroup(@Param("testGroup") String testGroup);
}
```

## 4. AppPopupRepository (앱 팝업 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.notification;

import com.routepick.backend.domain.entity.notification.AppPopup;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 앱 팝업 Repository
 * - 타겟팅 팝업 관리
 * - 노출 빈도 제어
 * - 사용자별 팝업 이력
 */
@Repository
public interface AppPopupRepository extends BaseRepository<AppPopup, Long> {
    
    // 활성 팝업 조회
    @Query("SELECT p FROM AppPopup p " +
           "WHERE p.isActive = true " +
           "AND p.targetUserType = :targetUserType " +
           "AND p.startDate <= CURRENT_TIMESTAMP " +
           "AND p.endDate >= CURRENT_TIMESTAMP " +
           "ORDER BY p.priority DESC, p.startDate DESC")
    List<AppPopup> findByIsActiveTrueAndTargetUserType(@Param("targetUserType") String targetUserType);
    
    // 사용자별 팝업 조회
    @Query("SELECT p FROM AppPopup p " +
           "WHERE p.isActive = true " +
           "AND (p.targetUserType = 'ALL' OR p.targetUserType = :userType) " +
           "AND p.startDate <= CURRENT_TIMESTAMP " +
           "AND p.endDate >= CURRENT_TIMESTAMP " +
           "AND p.id NOT IN (" +
           "  SELECT ph.popupId FROM PopupHistory ph " +
           "  WHERE ph.userId = :userId " +
           "  AND ph.action IN ('DISMISSED', 'DONT_SHOW_AGAIN')" +
           ") " +
           "ORDER BY p.priority DESC")
    List<AppPopup> findPopupForUser(@Param("userId") Long userId, 
                                   @Param("userType") String userType);
    
    // 우선순위별 팝업
    List<AppPopup> findByIsActiveTrueOrderByPriorityDesc();
    
    @Query("SELECT p FROM AppPopup p " +
           "WHERE p.isActive = true " +
           "AND p.priority >= :minPriority " +
           "AND p.startDate <= CURRENT_TIMESTAMP " +
           "AND p.endDate >= CURRENT_TIMESTAMP " +
           "ORDER BY p.priority DESC")
    List<AppPopup> findHighPriorityPopups(@Param("minPriority") Integer minPriority);
    
    // 팝업 타입별 조회
    List<AppPopup> findByPopupTypeAndIsActiveTrueOrderByPriorityDesc(String popupType);
    
    // 위치별 팝업
    @Query("SELECT p FROM AppPopup p " +
           "WHERE p.displayPosition = :position " +
           "AND p.isActive = true " +
           "AND p.startDate <= CURRENT_TIMESTAMP " +
           "AND p.endDate >= CURRENT_TIMESTAMP " +
           "ORDER BY p.priority DESC")
    List<AppPopup> findByDisplayPosition(@Param("position") String position);
    
    // 노출/클릭 통계
    @Modifying
    @Query("UPDATE AppPopup p SET p.displayCount = p.displayCount + 1 " +
           "WHERE p.id = :popupId")
    void incrementDisplayCount(@Param("popupId") Long popupId);
    
    @Modifying
    @Query("UPDATE AppPopup p SET p.clickCount = p.clickCount + 1 " +
           "WHERE p.id = :popupId")
    void incrementClickCount(@Param("popupId") Long popupId);
    
    @Modifying
    @Query("UPDATE AppPopup p SET p.dismissCount = p.dismissCount + 1 " +
           "WHERE p.id = :popupId")
    void incrementDismissCount(@Param("popupId") Long popupId);
    
    // 일일 노출 제한 체크
    @Query("SELECT COUNT(ph) FROM PopupHistory ph " +
           "WHERE ph.popupId = :popupId " +
           "AND ph.userId = :userId " +
           "AND DATE(ph.displayedAt) = CURRENT_DATE")
    long countTodayDisplays(@Param("popupId") Long popupId, @Param("userId") Long userId);
    
    // 빈도 제한 팝업
    @Query("SELECT p FROM AppPopup p " +
           "WHERE p.isActive = true " +
           "AND p.maxDisplayPerUser > (" +
           "  SELECT COUNT(ph) FROM PopupHistory ph " +
           "  WHERE ph.popupId = p.id AND ph.userId = :userId" +
           ") " +
           "ORDER BY p.priority DESC")
    List<AppPopup> findAvailablePopupsWithFrequencyLimit(@Param("userId") Long userId);
    
    // 조건부 팝업
    @Query("SELECT p FROM AppPopup p " +
           "WHERE p.isActive = true " +
           "AND p.triggerCondition = :condition " +
           "AND p.startDate <= CURRENT_TIMESTAMP " +
           "AND p.endDate >= CURRENT_TIMESTAMP " +
           "ORDER BY p.priority DESC")
    List<AppPopup> findByTriggerCondition(@Param("condition") String condition);
    
    // 만료 팝업 비활성화
    @Modifying
    @Query("UPDATE AppPopup p SET p.isActive = false " +
           "WHERE p.endDate < CURRENT_TIMESTAMP AND p.isActive = true")
    int deactivateExpiredPopups();
    
    // 성과 분석
    @Query("SELECT p.id, p.title, p.displayCount, p.clickCount, p.dismissCount, " +
           "CASE WHEN p.displayCount > 0 THEN (p.clickCount * 100.0 / p.displayCount) ELSE 0 END as conversionRate " +
           "FROM AppPopup p " +
           "WHERE p.startDate >= :startDate " +
           "ORDER BY conversionRate DESC")
    List<Object[]> analyzePopupPerformance(@Param("startDate") LocalDateTime startDate);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.notification.custom;

import com.routepick.backend.application.dto.notification.PopupSearchCriteria;
import com.routepick.backend.application.dto.projection.PopupAnalyticsProjection;
import com.routepick.backend.domain.entity.notification.AppPopup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 앱 팝업 커스텀 Repository
 */
public interface AppPopupRepositoryCustom {
    
    // 고급 검색
    Page<AppPopup> searchPopups(PopupSearchCriteria criteria, Pageable pageable);
    
    // 타겟팅 분석
    Map<String, List<AppPopup>> analyzeTargeting(LocalDateTime startDate, LocalDateTime endDate);
    
    // 팝업 성과 분석
    List<PopupAnalyticsProjection> getPopupAnalytics(LocalDateTime startDate, LocalDateTime endDate);
    
    // A/B 테스트 분석
    Map<String, PopupAnalyticsProjection> compareABTestResults(String testGroupA, String testGroupB);
    
    // 사용자 세그먼트별 팝업
    List<AppPopup> findPopupsForUserSegment(Map<String, Object> userAttributes);
    
    // 실시간 타겟팅
    AppPopup selectBestPopupForUser(Long userId, Map<String, Object> context);
}
```

## Projection 인터페이스들

### NotificationSummaryProjection
```java
package com.routepick.backend.application.dto.projection;

/**
 * 알림 요약 정보 Projection
 */
public class NotificationSummaryProjection {
    private Long totalCount;
    private Long unreadCount;
    private Long importantCount;
    private LocalDateTime lastNotificationTime;
    
    public NotificationSummaryProjection(Long totalCount, Long unreadCount, 
                                        Long importantCount, LocalDateTime lastNotificationTime) {
        this.totalCount = totalCount;
        this.unreadCount = unreadCount;
        this.importantCount = importantCount;
        this.lastNotificationTime = lastNotificationTime;
    }
    
    // Getters
    public Long getTotalCount() { return totalCount; }
    public Long getUnreadCount() { return unreadCount; }
    public Long getImportantCount() { return importantCount; }
    public LocalDateTime getLastNotificationTime() { return lastNotificationTime; }
}
```

### PopupAnalyticsProjection
```java
package com.routepick.backend.application.dto.projection;

/**
 * 팝업 분석 Projection
 */
public class PopupAnalyticsProjection {
    private Long popupId;
    private String title;
    private Long displayCount;
    private Long clickCount;
    private Long dismissCount;
    private Double conversionRate;
    private Double dismissRate;
    
    public PopupAnalyticsProjection(Long popupId, String title, Long displayCount,
                                   Long clickCount, Long dismissCount) {
        this.popupId = popupId;
        this.title = title;
        this.displayCount = displayCount;
        this.clickCount = clickCount;
        this.dismissCount = dismissCount;
        this.conversionRate = displayCount > 0 ? (double) clickCount / displayCount * 100 : 0.0;
        this.dismissRate = displayCount > 0 ? (double) dismissCount / displayCount * 100 : 0.0;
    }
    
    // Getters
    public Long getPopupId() { return popupId; }
    public String getTitle() { return title; }
    public Long getDisplayCount() { return displayCount; }
    public Long getClickCount() { return clickCount; }
    public Long getDismissCount() { return dismissCount; }
    public Double getConversionRate() { return conversionRate; }
    public Double getDismissRate() { return dismissRate; }
}
```

## 📈 성능 최적화 포인트

### 1. 인덱스 최적화
```sql
-- 개인 알림
CREATE INDEX idx_notification_user_read ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notification_type_date ON notifications(notification_type, created_at DESC);
CREATE INDEX idx_notification_push_pending ON notifications(is_push_sent, should_send_push, created_at);

-- 공지사항
CREATE INDEX idx_notice_active_important ON notices(is_active, is_important, start_date DESC);
CREATE INDEX idx_notice_date_range ON notices(start_date, end_date, is_active);
CREATE INDEX idx_notice_category ON notices(category, is_active, start_date DESC);

-- 배너
CREATE INDEX idx_banner_active_order ON banners(is_active, display_order);
CREATE INDEX idx_banner_position_date ON banners(position, start_date, end_date);
CREATE INDEX idx_banner_campaign ON banners(campaign_id, is_active);

-- 앱 팝업
CREATE INDEX idx_popup_active_priority ON app_popups(is_active, priority DESC);
CREATE INDEX idx_popup_target_date ON app_popups(target_user_type, start_date, end_date);
CREATE INDEX idx_popup_trigger ON app_popups(trigger_condition, is_active);
```

### 2. 캐싱 전략
- **Redis 캐싱**: 안읽은 알림 카운트, 활성 배너 목록
- **로컬 캐싱**: 공지사항, 팝업 설정
- **CDN 캐싱**: 배너 이미지, 팝업 리소스

### 3. 배치 처리 최적화
- **대용량 알림 발송**: 청크 단위 처리
- **푸시 알림 큐**: 비동기 처리
- **만료 콘텐츠 정리**: 스케줄러 기반

## 🎯 다음 단계 예고
- **5-5단계**: Message Repository (메시지 시스템)
- **5-6단계**: System Repository (시스템 설정)
- **Repository 레이어 완료** 후 **Service 레이어** 진행

---
*Step 5-4e 완료: 알림 시스템 Repository 4개 생성 완료*  
*실시간 처리 및 대용량 배치 최적화 적용*  
*다음: 5-5단계 Message Repository 대기 중*