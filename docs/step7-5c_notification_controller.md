# Step 7-5c: Notification Controller 구현

> 알림, 공지사항, 배너 관리 Controller - FCM 푸시 알림, 실시간 알림, 개인화 설정
> 생성일: 2025-08-25
> 단계: 7-5c (Controller 레이어 - 알림 시스템)
> 참고: step6-5d, step4-4b2a, step4-4b2b1, step4-4b2b2, step5-4e

---

## 🎯 설계 목표

- **개인 알림**: 읽음/삭제, 알림 설정 관리
- **푸시 알림**: FCM 토큰 관리, 실시간 발송
- **공지사항**: 공지사항 조회, 중요 공지 처리
- **배너/팝업**: 앱 배너, 팝업 노출 관리
- **알림 통계**: 발송 현황, 읽음율 분석

---

## 🔔 NotificationController 구현

### NotificationController.java
```java
package com.routepick.controller.api.v1.notification;

import com.routepick.common.annotation.RateLimited;
import com.routepick.common.dto.ApiResponse;
import com.routepick.common.enums.NotificationType;
import com.routepick.common.enums.NotificationStatus;
import com.routepick.service.notification.NotificationService;
import com.routepick.service.notification.NotificationSettingsService;
import com.routepick.service.notification.FCMService;
import com.routepick.dto.notification.request.*;
import com.routepick.dto.notification.response.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 알림 관리 Controller
 * - 개인 알림 조회/읽음/삭제
 * - 알림 설정 관리
 * - FCM 토큰 관리
 * - 공지사항, 배너, 팝업 조회
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
@Tag(name = "Notification", description = "알림 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSettingsService notificationSettingsService;
    private final FCMService fcmService;

    /**
     * 알림 목록 조회
     * GET /api/v1/notifications
     */
    @GetMapping
    @Operation(summary = "알림 목록 조회", description = "사용자의 알림 목록을 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        try {
            log.info("알림 목록 조회: userId={}, type={}, status={}, unreadOnly={}", 
                    userId, type, status, unreadOnly);
            
            // 알림 목록 조회
            Page<NotificationResponse> response = notificationService.getNotifications(
                    userId, type, status, unreadOnly, pageable);
            
            log.info("알림 목록 조회 완료: userId={}, totalElements={}, unreadCount={}", 
                    userId, response.getTotalElements(), 
                    response.getContent().stream().mapToLong(n -> n.isRead() ? 0 : 1).sum());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("알림 목록 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 읽지 않은 알림 개수 조회
     * GET /api/v1/notifications/unread-count
     */
    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 알림 개수", description = "읽지 않은 알림의 개수를 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 200, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("읽지 않은 알림 개수 조회: userId={}", userId);
            
            // 읽지 않은 알림 개수 조회
            UnreadCountResponse response = notificationService.getUnreadCount(userId);
            
            log.info("읽지 않은 알림 개수 조회 완료: userId={}, unreadCount={}", 
                    userId, response.getUnreadCount());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("읽지 않은 알림 개수 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 알림 읽음 처리
     * PUT /api/v1/notifications/{notificationId}/read
     */
    @PutMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음으로 표시합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable @NotNull Long notificationId,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("알림 읽음 처리: userId={}, notificationId={}", userId, notificationId);
            
            // 알림 소유권 검증 및 읽음 처리
            notificationService.markAsRead(userId, notificationId);
            
            log.info("알림 읽음 처리 완료: userId={}, notificationId={}", userId, notificationId);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            log.error("알림 읽음 처리 실패: userId={}, notificationId={}, error={}", 
                    userId, notificationId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 모든 알림 읽음 처리
     * PUT /api/v1/notifications/read-all
     */
    @PutMapping("/read-all")
    @Operation(summary = "모든 알림 읽음", description = "사용자의 모든 읽지 않은 알림을 읽음으로 표시합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimits({
        @RateLimited(requests = 10, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 30, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP)
    })
    public ResponseEntity<ApiResponse<ReadAllResponse>> markAllAsRead(
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("모든 알림 읽음 처리: userId={}", userId);
            
            // 모든 알림 읽음 처리
            ReadAllResponse response = notificationService.markAllAsRead(userId);
            
            log.info("모든 알림 읽음 처리 완료: userId={}, markedCount={}", 
                    userId, response.getMarkedCount());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("모든 알림 읽음 처리 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 알림 삭제
     * DELETE /api/v1/notifications/{notificationId}
     */
    @DeleteMapping("/{notificationId}")
    @Operation(summary = "알림 삭제", description = "특정 알림을 삭제합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable @NotNull Long notificationId,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("알림 삭제: userId={}, notificationId={}", userId, notificationId);
            
            // 알림 소유권 검증 및 삭제
            notificationService.deleteNotification(userId, notificationId);
            
            log.info("알림 삭제 완료: userId={}, notificationId={}", userId, notificationId);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            log.error("알림 삭제 실패: userId={}, notificationId={}, error={}", 
                    userId, notificationId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 알림 설정 조회
     * GET /api/v1/notifications/settings
     */
    @GetMapping("/settings")
    @Operation(summary = "알림 설정 조회", description = "사용자의 알림 설정을 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getNotificationSettings(
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("알림 설정 조회: userId={}", userId);
            
            // 알림 설정 조회
            NotificationSettingsResponse response = notificationSettingsService.getSettings(userId);
            
            log.info("알림 설정 조회 완료: userId={}, pushEnabled={}, emailEnabled={}", 
                    userId, response.isPushNotificationEnabled(), response.isEmailNotificationEnabled());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("알림 설정 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 알림 설정 변경
     * PUT /api/v1/notifications/settings
     */
    @PutMapping("/settings")
    @Operation(summary = "알림 설정 변경", description = "사용자의 알림 설정을 변경합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 20, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
            @Valid @RequestBody NotificationSettingsRequest request,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("알림 설정 변경: userId={}, pushEnabled={}, emailEnabled={}", 
                    userId, request.isPushNotificationEnabled(), request.isEmailNotificationEnabled());
            
            // 알림 설정 변경
            NotificationSettingsResponse response = notificationSettingsService.updateSettings(userId, request);
            
            log.info("알림 설정 변경 완료: userId={}, pushEnabled={}, emailEnabled={}", 
                    userId, response.isPushNotificationEnabled(), response.isEmailNotificationEnabled());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("알림 설정 변경 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * FCM 토큰 등록
     * POST /api/v1/notifications/fcm-token
     */
    @PostMapping("/fcm-token")
    @Operation(summary = "FCM 토큰 등록", description = "푸시 알림을 위한 FCM 토큰을 등록합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 10, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Void>> registerFcmToken(
            @Valid @RequestBody FCMTokenRequest request,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("FCM 토큰 등록: userId={}, deviceType={}", userId, request.getDeviceType());
            
            // FCM 토큰 등록
            fcmService.registerToken(userId, request.getToken(), request.getDeviceType());
            
            log.info("FCM 토큰 등록 완료: userId={}", userId);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            log.error("FCM 토큰 등록 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 공지사항 목록 조회
     * GET /api/v1/notifications/notices
     */
    @GetMapping("/notices")
    @Operation(summary = "공지사항 조회", description = "공지사항 목록을 조회합니다.")
    @RateLimited(requests = 50, period = 300, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    public ResponseEntity<ApiResponse<Page<NoticeResponse>>> getNotices(
            @RequestParam(required = false, defaultValue = "false") boolean importantOnly,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        try {
            log.info("공지사항 조회: importantOnly={}", importantOnly);
            
            // 공지사항 목록 조회
            Page<NoticeResponse> response = notificationService.getNotices(importantOnly, pageable);
            
            log.info("공지사항 조회 완료: totalElements={}, importantCount={}", 
                    response.getTotalElements(), 
                    response.getContent().stream().mapToLong(n -> n.isImportant() ? 1 : 0).sum());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("공지사항 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 배너 목록 조회
     * GET /api/v1/notifications/banners
     */
    @GetMapping("/banners")
    @Operation(summary = "배너 조회", description = "앱 배너 목록을 조회합니다.")
    @RateLimited(requests = 100, period = 300, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getBanners(
            @RequestParam(required = false) String position) {
        
        try {
            log.info("배너 조회: position={}", position);
            
            // 배너 목록 조회 (현재 활성화된 배너만)
            List<BannerResponse> response = notificationService.getActiveBanners(position);
            
            log.info("배너 조회 완료: count={}", response.size());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("배너 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 팝업 목록 조회
     * GET /api/v1/notifications/popups
     */
    @GetMapping("/popups")
    @Operation(summary = "팝업 조회", description = "앱 팝업 목록을 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<List<PopupResponse>>> getPopups(
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("팝업 조회: userId={}", userId);
            
            // 사용자별 팝업 조회 (타겟팅 적용)
            List<PopupResponse> response = notificationService.getUserPopups(userId);
            
            log.info("팝업 조회 완료: userId={}, count={}", userId, response.size());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("팝업 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 팝업 표시 확인 (사용자가 팝업을 본 것으로 처리)
     * POST /api/v1/notifications/popups/{popupId}/viewed
     */
    @PostMapping("/popups/{popupId}/viewed")
    @Operation(summary = "팝업 조회 처리", description = "사용자가 팝업을 확인했음을 표시합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Void>> markPopupAsViewed(
            @PathVariable @NotNull Long popupId,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("팝업 조회 처리: userId={}, popupId={}", userId, popupId);
            
            // 팝업 조회 처리 (중복 노출 방지)
            notificationService.markPopupAsViewed(userId, popupId);
            
            log.info("팝업 조회 처리 완료: userId={}, popupId={}", userId, popupId);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            log.error("팝업 조회 처리 실패: userId={}, popupId={}, error={}", 
                    userId, popupId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 관리자 - 알림 발송
     * POST /api/v1/notifications/admin/send
     */
    @PostMapping("/admin/send")
    @Operation(summary = "[관리자] 알림 발송", description = "사용자에게 알림을 발송합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 100, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<NotificationSendResponse>> sendNotification(
            @Valid @RequestBody NotificationSendRequest request) {
        
        try {
            log.info("관리자 알림 발송: type={}, targetUserCount={}", 
                    request.getNotificationType(), 
                    request.getTargetUserIds() != null ? request.getTargetUserIds().size() : "ALL");
            
            // 알림 발송
            NotificationSendResponse response = notificationService.sendBulkNotification(request);
            
            log.info("관리자 알림 발송 완료: sentCount={}, failedCount={}", 
                    response.getSentCount(), response.getFailedCount());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("관리자 알림 발송 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 관리자 - 알림 통계
     * GET /api/v1/notifications/admin/stats
     */
    @GetMapping("/admin/stats")
    @Operation(summary = "[관리자] 알림 통계", description = "알림 발송 및 읽음 통계를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<NotificationStatsResponse>> getNotificationStats(
            @RequestParam(required = false, defaultValue = "7") int days) {
        
        try {
            log.info("알림 통계 조회: days={}", days);
            
            // 알림 통계 조회
            NotificationStatsResponse response = notificationService.getNotificationStats(days);
            
            log.info("알림 통계 조회 완료: totalSent={}, readRate={}%", 
                    response.getTotalSent(), response.getReadRate());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("알림 통계 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }
}
```

---

## 📋 Notification Request DTOs

### NotificationSettingsRequest.java
```java
package com.routepick.dto.notification.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 알림 설정 변경 요청 DTO
 */
@Data
@Schema(description = "알림 설정 변경 요청")
public class NotificationSettingsRequest {

    @NotNull(message = "푸시 알림 설정은 필수입니다")
    @Schema(description = "푸시 알림 활성화 여부", example = "true")
    private boolean pushNotificationEnabled;

    @NotNull(message = "이메일 알림 설정은 필수입니다")
    @Schema(description = "이메일 알림 활성화 여부", example = "true")
    private boolean emailNotificationEnabled;

    @NotNull(message = "SMS 알림 설정은 필수입니다")
    @Schema(description = "SMS 알림 활성화 여부", example = "false")
    private boolean smsNotificationEnabled;

    @Valid
    @Schema(description = "알림 타입별 상세 설정")
    private NotificationTypeSettings typeSettings;

    @Schema(description = "알림 수신 시간대 제한")
    private NotificationTimeSettings timeSettings;

    /**
     * 알림 타입별 설정
     */
    @Data
    @Schema(description = "알림 타입별 설정")
    public static class NotificationTypeSettings {
        
        @Schema(description = "게시글 좋아요 알림", example = "true")
        private boolean likeNotification = true;
        
        @Schema(description = "댓글 알림", example = "true")
        private boolean commentNotification = true;
        
        @Schema(description = "팔로우 알림", example = "true")
        private boolean followNotification = true;
        
        @Schema(description = "결제 관련 알림", example = "true")
        private boolean paymentNotification = true;
        
        @Schema(description = "시스템 공지 알림", example = "true")
        private boolean systemNotification = true;
        
        @Schema(description = "마케팅 알림", example = "false")
        private boolean marketingNotification = false;
    }

    /**
     * 알림 시간 설정
     */
    @Data
    @Schema(description = "알림 시간 설정")
    public static class NotificationTimeSettings {
        
        @Schema(description = "야간 알림 차단 (22:00~08:00)", example = "true")
        private boolean blockNightNotifications = true;
        
        @Schema(description = "주말 알림 차단", example = "false")
        private boolean blockWeekendNotifications = false;
        
        @Schema(description = "알림 시작 시간", example = "08:00")
        private String startTime = "08:00";
        
        @Schema(description = "알림 종료 시간", example = "22:00")
        private String endTime = "22:00";
    }
}
```

### FCMTokenRequest.java
```java
package com.routepick.dto.notification.request;

import com.routepick.common.enums.DeviceType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * FCM 토큰 등록 요청 DTO
 */
@Data
@Schema(description = "FCM 토큰 등록 요청")
public class FCMTokenRequest {

    @NotBlank(message = "FCM 토큰은 필수입니다")
    @Size(min = 100, max = 500, message = "FCM 토큰 길이가 올바르지 않습니다")
    @Schema(description = "FCM 토큰", 
           example = "eJ7Y2K9qKZY:APA91bF8Q9vTn7X2mKpL3xR...")
    private String token;

    @NotNull(message = "디바이스 타입은 필수입니다")
    @Schema(description = "디바이스 타입", example = "ANDROID")
    private DeviceType deviceType;

    @Schema(description = "디바이스 ID")
    private String deviceId;

    @Schema(description = "앱 버전", example = "1.0.0")
    private String appVersion;

    @Schema(description = "OS 버전", example = "13")
    private String osVersion;
}
```

### NotificationSendRequest.java
```java
package com.routepick.dto.notification.request;

import com.routepick.common.enums.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 관리자 알림 발송 요청 DTO
 */
@Data
@Schema(description = "관리자 알림 발송 요청")
public class NotificationSendRequest {

    @NotNull(message = "알림 타입은 필수입니다")
    @Schema(description = "알림 타입", example = "SYSTEM")
    private NotificationType notificationType;

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다")
    @Schema(description = "알림 제목", example = "시스템 점검 안내")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    @Size(max = 1000, message = "내용은 1000자 이하여야 합니다")
    @Schema(description = "알림 내용", example = "2025년 8월 25일 02:00~04:00 시스템 점검이 있습니다.")
    private String content;

    @Schema(description = "대상 사용자 ID 목록 (null이면 전체 발송)")
    private List<Long> targetUserIds;

    @Schema(description = "사용자 필터 조건")
    private UserFilterCriteria filterCriteria;

    @Schema(description = "예약 발송 시간 (null이면 즉시 발송)")
    private LocalDateTime scheduledAt;

    @Schema(description = "링크 URL")
    private String linkUrl;

    @Schema(description = "이미지 URL")
    private String imageUrl;

    @Schema(description = "추가 데이터")
    private Map<String, String> additionalData;

    @Schema(description = "푸시 알림 발송 여부", example = "true")
    private boolean sendPush = true;

    @Schema(description = "이메일 발송 여부", example = "false")
    private boolean sendEmail = false;

    @Schema(description = "SMS 발송 여부", example = "false")
    private boolean sendSms = false;

    /**
     * 사용자 필터 조건
     */
    @Data
    @Schema(description = "사용자 필터 조건")
    public static class UserFilterCriteria {
        
        @Schema(description = "최근 접속일 기준 (일)", example = "30")
        private Integer recentLoginDays;
        
        @Schema(description = "최소 레벨", example = "1")
        private Integer minLevel;
        
        @Schema(description = "최대 레벨", example = "10")
        private Integer maxLevel;
        
        @Schema(description = "가입일 기준 (일)", example = "7")
        private Integer joinedWithinDays;
        
        @Schema(description = "특정 지역 사용자만")
        private List<String> regions;
        
        @Schema(description = "프리미엄 회원만", example = "false")
        private Boolean premiumOnly;
    }
}
```

---

## 📤 Notification Response DTOs

### NotificationResponse.java
```java
package com.routepick.dto.notification.response;

import com.routepick.common.enums.NotificationType;
import com.routepick.common.enums.NotificationStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 알림 응답 DTO
 */
@Data
@Schema(description = "알림 정보")
public class NotificationResponse {

    @Schema(description = "알림 ID", example = "123")
    private Long notificationId;

    @Schema(description = "알림 타입", example = "LIKE")
    private NotificationType type;

    @Schema(description = "알림 상태", example = "UNREAD")
    private NotificationStatus status;

    @Schema(description = "제목", example = "게시글에 좋아요가 눌렸습니다")
    private String title;

    @Schema(description = "내용", example = "홍길동님이 회원님의 게시글에 좋아요를 눌렀습니다.")
    private String content;

    @Schema(description = "읽음 여부", example = "false")
    private boolean read;

    @Schema(description = "링크 URL")
    private String linkUrl;

    @Schema(description = "이미지 URL")
    private String imageUrl;

    @Schema(description = "발송 시간")
    private LocalDateTime sentAt;

    @Schema(description = "읽은 시간")
    private LocalDateTime readAt;

    @Schema(description = "추가 데이터")
    private Map<String, String> additionalData;

    @Schema(description = "발신자 정보")
    private NotificationSenderInfo sender;

    /**
     * 발신자 정보
     */
    @Data
    @Schema(description = "발신자 정보")
    public static class NotificationSenderInfo {
        
        @Schema(description = "발신자 ID", example = "456")
        private Long senderId;
        
        @Schema(description = "발신자 닉네임", example = "홍길동")
        private String senderNickname;
        
        @Schema(description = "발신자 프로필 이미지")
        private String senderProfileImage;
    }
}
```

### NotificationSettingsResponse.java
```java
package com.routepick.dto.notification.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 알림 설정 응답 DTO
 */
@Data
@Schema(description = "알림 설정 정보")
public class NotificationSettingsResponse {

    @Schema(description = "사용자 ID", example = "123")
    private Long userId;

    @Schema(description = "푸시 알림 활성화 여부", example = "true")
    private boolean pushNotificationEnabled;

    @Schema(description = "이메일 알림 활성화 여부", example = "true")
    private boolean emailNotificationEnabled;

    @Schema(description = "SMS 알림 활성화 여부", example = "false")
    private boolean smsNotificationEnabled;

    @Schema(description = "알림 타입별 설정")
    private NotificationTypeSettings typeSettings;

    @Schema(description = "알림 시간 설정")
    private NotificationTimeSettings timeSettings;

    @Schema(description = "FCM 토큰 등록 여부", example = "true")
    private boolean fcmTokenRegistered;

    @Schema(description = "마지막 설정 변경 시간")
    private LocalDateTime lastUpdatedAt;

    /**
     * 알림 타입별 설정
     */
    @Data
    @Schema(description = "알림 타입별 설정")
    public static class NotificationTypeSettings {
        private boolean likeNotification;
        private boolean commentNotification;
        private boolean followNotification;
        private boolean paymentNotification;
        private boolean systemNotification;
        private boolean marketingNotification;
    }

    /**
     * 알림 시간 설정
     */
    @Data
    @Schema(description = "알림 시간 설정")
    public static class NotificationTimeSettings {
        private boolean blockNightNotifications;
        private boolean blockWeekendNotifications;
        private String startTime;
        private String endTime;
    }
}
```

### UnreadCountResponse.java
```java
package com.routepick.dto.notification.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 읽지 않은 알림 개수 응답 DTO
 */
@Data
@Schema(description = "읽지 않은 알림 개수")
public class UnreadCountResponse {

    @Schema(description = "전체 읽지 않은 알림 개수", example = "5")
    private long unreadCount;

    @Schema(description = "타입별 읽지 않은 알림 개수")
    private Map<String, Long> unreadCountByType;

    @Schema(description = "최근 24시간 내 알림 개수", example = "3")
    private long recentCount;

    @Schema(description = "중요 알림 개수", example = "1")
    private long importantCount;
}
```

### NoticeResponse.java
```java
package com.routepick.dto.notification.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 공지사항 응답 DTO
 */
@Data
@Schema(description = "공지사항 정보")
public class NoticeResponse {

    @Schema(description = "공지사항 ID", example = "123")
    private Long noticeId;

    @Schema(description = "제목", example = "시스템 점검 안내")
    private String title;

    @Schema(description = "내용 요약", example = "2025년 8월 25일 02:00~04:00 시스템 점검...")
    private String summary;

    @Schema(description = "중요 공지 여부", example = "true")
    private boolean important;

    @Schema(description = "상단 고정 여부", example = "false")
    private boolean pinned;

    @Schema(description = "게시 시작 시간")
    private LocalDateTime startAt;

    @Schema(description = "게시 종료 시간")
    private LocalDateTime endAt;

    @Schema(description = "작성자", example = "관리자")
    private String author;

    @Schema(description = "조회수", example = "150")
    private long viewCount;

    @Schema(description = "작성 시간")
    private LocalDateTime createdAt;

    @Schema(description = "링크 URL")
    private String linkUrl;

    @Schema(description = "첨부파일 URL")
    private String attachmentUrl;
}
```

---

## 🎯 실시간 알림 기능

### WebSocket 알림 전송
```java
/**
 * 실시간 알림 WebSocket 서비스
 */
@Service
@RequiredArgsConstructor
public class RealtimeNotificationService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 실시간 알림 전송
     */
    public void sendRealtimeNotification(Long userId, NotificationResponse notification) {
        try {
            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications",
                notification
            );
            
            log.info("실시간 알림 전송 완료: userId={}, type={}", 
                    userId, notification.getType());
                    
        } catch (Exception e) {
            log.error("실시간 알림 전송 실패: userId={}, error={}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * 읽지 않은 개수 실시간 업데이트
     */
    public void updateUnreadCount(Long userId, long unreadCount) {
        try {
            UnreadCountResponse response = new UnreadCountResponse();
            response.setUnreadCount(unreadCount);
            
            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/unread-count",
                response
            );
            
        } catch (Exception e) {
            log.error("읽지 않은 개수 업데이트 실패: userId={}, error={}", userId, e.getMessage(), e);
        }
    }
}
```

---

*NotificationController 구현 완료일: 2025-08-25*
*구현 항목: 개인 알림, FCM 푸시, 공지사항, 배너/팝업, 실시간 알림*
*보안 기능: 사용자별 권한 검증, 알림 설정 관리, 스팸 방지*
*다음 단계: SystemController 구현*