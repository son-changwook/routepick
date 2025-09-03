# Step 7-5h: Message Controller 구현 (긴급 보완)

> 개인 메시지, 루트 태깅 메시지 Controller - 실시간 메시징, 암호화, 스팸 방지
> 생성일: 2025-08-25
> 단계: 7-5h (Controller 레이어 - 메시지 시스템)
> 참고: step6-4d, step5-4f2, step4-4a1

---

## 🚨 긴급 구현 사유

step7-5_reference_files.md에서 계획된 5개 Controller 중 **MessageController가 누락**되어 즉시 보완합니다.

---

## 🎯 설계 목표

- **개인 메시징**: 1:1 메시지, 그룹 메시지 지원
- **루트 태깅 메시지**: 루트 추천, 클라이밍 정보 공유
- **실시간 처리**: WebSocket 연동, 즉시 알림
- **메시지 보안**: 내용 암호화, 스팸 방지, 신고 시스템
- **대량 발송**: 시스템 공지, 이벤트 알림

---

## 💬 MessageController 구현

### MessageController.java
```java
package com.routepick.controller.api.v1.message;

import com.routepick.common.annotation.RateLimited;
import com.routepick.common.annotation.SecureTransaction;
import com.routepick.common.dto.ApiResponse;
import com.routepick.common.enums.MessageType;
import com.routepick.common.enums.MessageStatus;
import com.routepick.service.message.MessageService;
import com.routepick.service.message.MessageEncryptionService;
import com.routepick.dto.message.request.*;
import com.routepick.dto.message.response.*;

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
 * 메시지 관리 Controller
 * - 개인 메시지 송수신
 * - 루트 태깅 메시지
 * - 메시지 암호화 및 보안
 * - 스팸 방지 및 신고 시스템
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Validated
@Tag(name = "Message", description = "메시지 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {

    private final MessageService messageService;
    private final MessageEncryptionService encryptionService;

    /**
     * 메시지 발송
     * POST /api/v1/messages
     */
    @PostMapping
    @Operation(summary = "메시지 발송", description = "개인 또는 그룹 메시지를 발송합니다.")
    @PreAuthorize("isAuthenticated()")
    @SecureTransaction
    @RateLimits({
        @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 100, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP),
        @RateLimited(requests = 200, period = 86400, keyStrategy = RateLimited.KeyStrategy.USER_ID) // 일일 제한
    })
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @Valid @RequestBody MessageSendRequest request,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("메시지 발송 요청: senderId={}, recipientCount={}, type={}", 
                    userId, 
                    request.getRecipientIds() != null ? request.getRecipientIds().size() : 1,
                    request.getMessageType());
            
            // 스팸 메시지 검사
            messageService.validateSpamContent(request.getContent(), userId);
            
            // 수신자 차단 목록 검증
            messageService.validateRecipientBlockList(userId, request.getRecipientIds());
            
            // 메시지 발송
            MessageResponse response = messageService.sendMessage(userId, request);
            
            log.info("메시지 발송 완료: messageId={}, status={}", 
                    response.getMessageId(), response.getStatus());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("메시지 발송 실패: senderId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 메시지 목록 조회
     * GET /api/v1/messages
     */
    @GetMapping
    @Operation(summary = "메시지 목록 조회", description = "사용자의 메시지 목록을 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Page<MessageListResponse>>> getMessages(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) MessageType messageType,
            @RequestParam(required = false) MessageStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String conversationWith,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        try {
            log.info("메시지 목록 조회: userId={}, type={}, unreadOnly={}", 
                    userId, messageType, unreadOnly);
            
            // 메시지 목록 조회
            Page<MessageListResponse> response = messageService.getMessages(
                    userId, messageType, status, unreadOnly, conversationWith, pageable);
            
            log.info("메시지 목록 조회 완료: userId={}, totalElements={}, unreadCount={}", 
                    userId, response.getTotalElements(), 
                    response.getContent().stream().mapToLong(m -> m.isRead() ? 0 : 1).sum());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("메시지 목록 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 메시지 상세 조회
     * GET /api/v1/messages/{messageId}
     */
    @GetMapping("/{messageId}")
    @Operation(summary = "메시지 상세 조회", description = "특정 메시지의 상세 정보를 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 200, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<MessageDetailResponse>> getMessageDetail(
            @PathVariable @NotNull Long messageId,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("메시지 상세 조회: userId={}, messageId={}", userId, messageId);
            
            // 메시지 권한 검증 및 상세 조회
            MessageDetailResponse response = messageService.getMessageDetail(userId, messageId);
            
            // 메시지 자동 읽음 처리 (발신자가 아닌 경우)
            if (!response.getSender().getSenderId().equals(userId)) {
                messageService.markAsRead(userId, messageId);
            }
            
            log.info("메시지 상세 조회 완료: messageId={}, encrypted={}", 
                    messageId, response.isEncrypted());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("메시지 상세 조회 실패: userId={}, messageId={}, error={}", 
                    userId, messageId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 메시지 읽음 처리
     * PUT /api/v1/messages/{messageId}/read
     */
    @PutMapping("/{messageId}/read")
    @Operation(summary = "메시지 읽음 처리", description = "특정 메시지를 읽음으로 표시합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 500, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Void>> markMessageAsRead(
            @PathVariable @NotNull Long messageId,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("메시지 읽음 처리: userId={}, messageId={}", userId, messageId);
            
            // 메시지 권한 검증 및 읽음 처리
            messageService.markAsRead(userId, messageId);
            
            log.info("메시지 읽음 처리 완료: userId={}, messageId={}", userId, messageId);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            log.error("메시지 읽음 처리 실패: userId={}, messageId={}, error={}", 
                    userId, messageId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 메시지 삭제
     * DELETE /api/v1/messages/{messageId}
     */
    @DeleteMapping("/{messageId}")
    @Operation(summary = "메시지 삭제", description = "특정 메시지를 삭제합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimits({
        @RateLimited(requests = 50, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 200, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP)
    })
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable @NotNull Long messageId,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("메시지 삭제: userId={}, messageId={}", userId, messageId);
            
            // 메시지 권한 검증 및 삭제 (soft delete)
            messageService.deleteMessage(userId, messageId);
            
            log.info("메시지 삭제 완료: userId={}, messageId={}", userId, messageId);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            log.error("메시지 삭제 실패: userId={}, messageId={}, error={}", 
                    userId, messageId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 루트 태깅 메시지 발송
     * POST /api/v1/messages/route-tag
     */
    @PostMapping("/route-tag")
    @Operation(summary = "루트 태깅 메시지", description = "특정 루트와 관련된 메시지를 발송합니다.")
    @PreAuthorize("isAuthenticated()")
    @SecureTransaction
    @RateLimits({
        @RateLimited(requests = 20, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 50, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP)
    })
    public ResponseEntity<ApiResponse<RouteTagMessageResponse>> sendRouteTagMessage(
            @Valid @RequestBody RouteTagMessageRequest request,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("루트 태깅 메시지 발송: senderId={}, routeId={}, recipientCount={}", 
                    userId, request.getRouteId(), 
                    request.getRecipientIds() != null ? request.getRecipientIds().size() : 0);
            
            // 루트 존재 및 접근 권한 검증
            messageService.validateRouteAccess(userId, request.getRouteId());
            
            // 스팸 검사 (루트 태깅은 더 엄격)
            messageService.validateRouteTagSpam(request.getContent(), userId, request.getRouteId());
            
            // 루트 태깅 메시지 발송
            RouteTagMessageResponse response = messageService.sendRouteTagMessage(userId, request);
            
            log.info("루트 태깅 메시지 발송 완료: messageId={}, routeId={}", 
                    response.getMessageId(), request.getRouteId());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("루트 태깅 메시지 발송 실패: senderId={}, routeId={}, error={}", 
                    userId, request.getRouteId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 대화방 목록 조회
     * GET /api/v1/messages/conversations
     */
    @GetMapping("/conversations")
    @Operation(summary = "대화방 목록", description = "사용자의 대화방 목록을 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Page<ConversationResponse>>> getConversations(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "lastMessageAt") Pageable pageable) {
        
        try {
            log.info("대화방 목록 조회: userId={}", userId);
            
            // 대화방 목록 조회
            Page<ConversationResponse> response = messageService.getConversations(userId, pageable);
            
            log.info("대화방 목록 조회 완료: userId={}, conversationCount={}", 
                    userId, response.getTotalElements());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("대화방 목록 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 메시지 신고
     * POST /api/v1/messages/{messageId}/report
     */
    @PostMapping("/{messageId}/report")
    @Operation(summary = "메시지 신고", description = "스팸 또는 부적절한 메시지를 신고합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimits({
        @RateLimited(requests = 10, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 30, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP)
    })
    public ResponseEntity<ApiResponse<MessageReportResponse>> reportMessage(
            @PathVariable @NotNull Long messageId,
            @Valid @RequestBody MessageReportRequest request,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("메시지 신고: reporterId={}, messageId={}, reason={}", 
                    userId, messageId, request.getReportReason());
            
            // 메시지 신고 처리
            MessageReportResponse response = messageService.reportMessage(userId, messageId, request);
            
            log.info("메시지 신고 완료: reportId={}, messageId={}", 
                    response.getReportId(), messageId);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("메시지 신고 실패: reporterId={}, messageId={}, error={}", 
                    userId, messageId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 사용자 차단
     * POST /api/v1/messages/block/{targetUserId}
     */
    @PostMapping("/block/{targetUserId}")
    @Operation(summary = "사용자 차단", description = "특정 사용자의 메시지를 차단합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 20, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<UserBlockResponse>> blockUser(
            @PathVariable @NotNull Long targetUserId,
            @Valid @RequestBody UserBlockRequest request,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("사용자 차단: blockerId={}, targetUserId={}, reason={}", 
                    userId, targetUserId, request.getBlockReason());
            
            // 자기 자신 차단 방지
            if (userId.equals(targetUserId)) {
                throw new IllegalArgumentException("자기 자신은 차단할 수 없습니다");
            }
            
            // 사용자 차단 처리
            UserBlockResponse response = messageService.blockUser(userId, targetUserId, request);
            
            log.info("사용자 차단 완료: blockId={}, targetUserId={}", 
                    response.getBlockId(), targetUserId);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("사용자 차단 실패: blockerId={}, targetUserId={}, error={}", 
                    userId, targetUserId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 읽지 않은 메시지 개수
     * GET /api/v1/messages/unread-count
     */
    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 메시지 개수", description = "읽지 않은 메시지 개수를 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 200, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<MessageUnreadCountResponse>> getUnreadMessageCount(
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("읽지 않은 메시지 개수 조회: userId={}", userId);
            
            // 읽지 않은 메시지 개수 조회
            MessageUnreadCountResponse response = messageService.getUnreadMessageCount(userId);
            
            log.info("읽지 않은 메시지 개수 조회 완료: userId={}, unreadCount={}", 
                    userId, response.getTotalUnreadCount());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("읽지 않은 메시지 개수 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 관리자 - 메시지 통계
     * GET /api/v1/messages/admin/stats
     */
    @GetMapping("/admin/stats")
    @Operation(summary = "[관리자] 메시지 통계", description = "메시지 발송 및 사용 통계를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<MessageStatsResponse>> getMessageStats(
            @RequestParam(required = false, defaultValue = "7") int days) {
        
        try {
            log.info("메시지 통계 조회: days={}", days);
            
            // 메시지 통계 조회
            MessageStatsResponse response = messageService.getMessageStats(days);
            
            log.info("메시지 통계 조회 완료: totalMessages={}, activeUsers={}", 
                    response.getTotalMessages(), response.getActiveUsers());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("메시지 통계 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }
}
```

---

## 📋 Message Request DTOs

### MessageSendRequest.java
```java
package com.routepick.dto.message.request;

import com.routepick.common.enums.MessageType;
import com.routepick.common.enums.MessagePriority;
import com.routepick.common.validation.korean.KoreanContent;
import com.routepick.common.validation.korean.NoXSS;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 메시지 발송 요청 DTO
 */
@Data
@Schema(description = "메시지 발송 요청")
public class MessageSendRequest {

    @NotNull(message = "메시지 타입은 필수입니다")
    @Schema(description = "메시지 타입", example = "PERSONAL")
    private MessageType messageType;

    @Schema(description = "수신자 ID 목록 (1:1 메시지시 1개)")
    @Size(min = 1, max = 100, message = "수신자는 1~100명까지 가능합니다")
    private List<@Min(value = 1, message = "사용자 ID는 양수여야 합니다") Long> recipientIds;

    @NotBlank(message = "메시지 내용은 필수입니다")
    @Size(min = 1, max = 2000, message = "메시지는 1~2000자 사이여야 합니다")
    @KoreanContent(allowEnglish = true, allowSpecialChars = true, allowEmoji = true,
                  message = "메시지에 허용되지 않은 문자가 포함되어 있습니다")
    @NoXSS(message = "메시지에 스크립트 코드가 포함될 수 없습니다")
    @Schema(description = "메시지 내용", example = "안녕하세요! 오늘 클라이밍 어땠나요? 😊")
    private String content;

    @Schema(description = "메시지 제목 (그룹 메시지시)")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다")
    @KoreanContent(allowEnglish = true, message = "제목에 허용되지 않은 문자가 포함되어 있습니다")
    private String subject;

    @Schema(description = "메시지 우선순위", example = "NORMAL")
    private MessagePriority priority = MessagePriority.NORMAL;

    @Schema(description = "예약 발송 시간")
    @Future(message = "예약 시간은 현재 시간보다 이후여야 합니다")
    private LocalDateTime scheduledAt;

    @Schema(description = "첨부 파일 URL 목록")
    @Size(max = 5, message = "첨부 파일은 최대 5개까지 가능합니다")
    private List<@Pattern(regexp = "^https?://.*\\.(jpg|jpeg|png|gif|pdf|txt)$", 
                          message = "지원되는 파일 형식이 아닙니다") String> attachmentUrls;

    @Schema(description = "암호화 여부", example = "false")
    private boolean encrypted = false;

    @Schema(description = "읽음 확인 요청 여부", example = "true")
    private boolean readReceiptRequested = true;

    @Schema(description = "추가 메타데이터")
    @Size(max = 10, message = "메타데이터는 최대 10개까지 가능합니다")
    private Map<@Size(max = 50) String, @Size(max = 200) String> metadata;

    @Schema(description = "자동 삭제 시간 (시간)", example = "168")
    @Min(value = 1, message = "자동 삭제 시간은 1시간 이상이어야 합니다")
    @Max(value = 8760, message = "자동 삭제 시간은 1년(8760시간) 이하여야 합니다")
    private Integer autoDeleteHours;
}
```

### RouteTagMessageRequest.java
```java
package com.routepick.dto.message.request;

import com.routepick.common.enums.RouteTagType;
import com.routepick.common.validation.korean.KoreanContent;
import com.routepick.common.validation.korean.NoXSS;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.*;
import java.util.List;

/**
 * 루트 태깅 메시지 요청 DTO
 */
@Data
@Schema(description = "루트 태깅 메시지 요청")
public class RouteTagMessageRequest {

    @NotNull(message = "루트 ID는 필수입니다")
    @Min(value = 1, message = "루트 ID는 양수여야 합니다")
    @Schema(description = "태깅할 루트 ID", example = "123")
    private Long routeId;

    @NotNull(message = "루트 태깅 타입은 필수입니다")
    @Schema(description = "루트 태깅 타입", example = "RECOMMENDATION")
    private RouteTagType tagType;

    @Schema(description = "수신자 ID 목록 (미지정시 루트 관심 사용자들에게 발송)")
    @Size(max = 50, message = "루트 태깅 메시지는 최대 50명까지 가능합니다")
    private List<@Min(value = 1, message = "사용자 ID는 양수여야 합니다") Long> recipientIds;

    @NotBlank(message = "메시지 내용은 필수입니다")
    @Size(min = 10, max = 1000, message = "루트 태깅 메시지는 10~1000자 사이여야 합니다")
    @KoreanContent(allowEnglish = true, allowSpecialChars = true, allowEmoji = true,
                  message = "메시지에 허용되지 않은 문자가 포함되어 있습니다")
    @NoXSS(message = "메시지에 스크립트 코드가 포함될 수 없습니다")
    @Schema(description = "메시지 내용", 
           example = "이 루트 정말 재미있어요! V4 난이도인데 홀드가 정말 좋습니다. 추천드려요! 💪")
    private String content;

    @Schema(description = "루트 평점 (1-5)", example = "5")
    @Min(value = 1, message = "평점은 1점 이상이어야 합니다")
    @Max(value = 5, message = "평점은 5점 이하여야 합니다")
    private Integer rating;

    @Schema(description = "난이도 평가", example = "적당함")
    @Pattern(regexp = "^(쉬움|적당함|어려움|매우어려움)$", 
             message = "난이도는 쉬움, 적당함, 어려움, 매우어려움 중 하나여야 합니다")
    private String difficultyFeedback;

    @Schema(description = "루트 태그 목록")
    @Size(max = 10, message = "태그는 최대 10개까지 가능합니다")
    private List<@Pattern(regexp = "^[가-힣a-zA-Z0-9_]{1,20}$", 
                          message = "태그는 한글, 영문, 숫자, 언더스코어만 가능하며 20자 이하여야 합니다") String> routeTags;

    @Schema(description = "루트 이미지 URL")
    @Pattern(regexp = "^https?://.*\\.(jpg|jpeg|png|gif|webp)$", 
             message = "지원되는 이미지 형식이 아닙니다")
    private String routeImageUrl;

    @Schema(description = "개인적인 팁이나 조언")
    @Size(max = 500, message = "팁은 500자 이하여야 합니다")
    private String personalTip;

    @Schema(description = "추천 대상 레벨", example = "V3-V5")
    @Pattern(regexp = "^V[0-9]{1,2}(-V[0-9]{1,2})?$", 
             message = "올바른 V등급 형식이 아닙니다 (예: V4, V3-V5)")
    private String recommendedLevel;

    @Schema(description = "공개 여부 (false시 수신자만 확인 가능)", example = "true")
    private boolean publicVisible = true;
}
```

---

## 📤 Message Response DTOs

### MessageResponse.java
```java
package com.routepick.dto.message.response;

import com.routepick.common.enums.MessageType;
import com.routepick.common.enums.MessageStatus;
import com.routepick.common.enums.MessagePriority;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 메시지 발송 응답 DTO
 */
@Data
@Schema(description = "메시지 발송 응답")
public class MessageResponse {

    @Schema(description = "메시지 ID", example = "123")
    private Long messageId;

    @Schema(description = "메시지 타입", example = "PERSONAL")
    private MessageType messageType;

    @Schema(description = "메시지 상태", example = "SENT")
    private MessageStatus status;

    @Schema(description = "메시지 우선순위", example = "NORMAL")
    private MessagePriority priority;

    @Schema(description = "발신자 정보")
    private MessageSenderInfo sender;

    @Schema(description = "수신자 정보 목록")
    private List<MessageRecipientInfo> recipients;

    @Schema(description = "메시지 내용 (발신자에게만 전체 노출)")
    private String content;

    @Schema(description = "메시지 제목")
    private String subject;

    @Schema(description = "암호화 여부")
    private boolean encrypted;

    @Schema(description = "발송 시간")
    private LocalDateTime sentAt;

    @Schema(description = "예약 발송 시간")
    private LocalDateTime scheduledAt;

    @Schema(description = "만료 시간")
    private LocalDateTime expiresAt;

    @Schema(description = "첨부 파일 정보")
    private List<AttachmentInfo> attachments;

    @Schema(description = "읽음 확인 정보")
    private ReadReceiptInfo readReceipt;

    @Schema(description = "메타데이터")
    private Map<String, String> metadata;

    /**
     * 발신자 정보
     */
    @Data
    @Schema(description = "발신자 정보")
    public static class MessageSenderInfo {
        
        @Schema(description = "발신자 ID", example = "456")
        private Long senderId;
        
        @Schema(description = "발신자 닉네임", example = "클라이머123")
        private String senderNickname;
        
        @Schema(description = "발신자 프로필 이미지")
        private String senderProfileImage;
        
        @Schema(description = "발신자 레벨")
        private Integer senderLevel;
        
        @Schema(description = "인증 마크 여부")
        private boolean verified;
        
        @Schema(description = "온라인 상태")
        private boolean online;
        
        @Schema(description = "마지막 접속 시간")
        private LocalDateTime lastActiveAt;
    }

    /**
     * 수신자 정보
     */
    @Data
    @Schema(description = "수신자 정보")
    public static class MessageRecipientInfo {
        
        @Schema(description = "수신자 ID", example = "789")
        private Long recipientId;
        
        @Schema(description = "수신자 닉네임 (마스킹)", example = "클라***")
        private String maskedNickname;
        
        @Schema(description = "전송 상태", example = "DELIVERED")
        private String deliveryStatus;
        
        @Schema(description = "읽음 여부")
        private boolean read;
        
        @Schema(description = "읽은 시간")
        private LocalDateTime readAt;
        
        @Schema(description = "전송 시간")
        private LocalDateTime deliveredAt;
    }

    /**
     * 첨부파일 정보
     */
    @Data
    @Schema(description = "첨부파일 정보")
    public static class AttachmentInfo {
        
        @Schema(description = "파일 ID", example = "101")
        private Long fileId;
        
        @Schema(description = "파일명", example = "route_photo.jpg")
        private String fileName;
        
        @Schema(description = "파일 크기 (KB)", example = "1024")
        private Long fileSizeKb;
        
        @Schema(description = "파일 타입", example = "image/jpeg")
        private String fileType;
        
        @Schema(description = "파일 URL")
        private String fileUrl;
        
        @Schema(description = "썸네일 URL")
        private String thumbnailUrl;
    }

    /**
     * 읽음 확인 정보
     */
    @Data
    @Schema(description = "읽음 확인 정보")
    public static class ReadReceiptInfo {
        
        @Schema(description = "읽음 확인 요청 여부")
        private boolean requested;
        
        @Schema(description = "총 수신자 수")
        private int totalRecipients;
        
        @Schema(description = "읽은 수신자 수")
        private int readCount;
        
        @Schema(description = "읽음율 (%)", example = "75.0")
        private Double readRate;
        
        @Schema(description = "첫 읽음 시간")
        private LocalDateTime firstReadAt;
        
        @Schema(description = "마지막 읽음 시간")
        private LocalDateTime lastReadAt;
    }
}
```

---

## 🔒 보안 강화 기능

### 1. 메시지 암호화 서비스
```java
/**
 * 메시지 내용 암호화 서비스
 */
@Service
@RequiredArgsConstructor
public class MessageEncryptionService {
    
    private final AESUtil aesUtil;
    
    /**
     * 메시지 내용 암호화
     */
    public String encryptMessage(String content) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        return aesUtil.encrypt(content);
    }
    
    /**
     * 메시지 내용 복호화
     */
    public String decryptMessage(String encryptedContent) {
        if (StringUtils.isEmpty(encryptedContent)) {
            return encryptedContent;
        }
        return aesUtil.decrypt(encryptedContent);
    }
}
```

### 2. 스팸 방지 시스템
```java
/**
 * 메시지 스팸 검증 서비스
 */
@Service
public class MessageSpamValidator {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 스팸 메시지 검증
     */
    public void validateSpamContent(String content, Long userId) {
        // 금지된 키워드 검사
        if (containsBannedKeywords(content)) {
            throw new MessageSpamException("금지된 내용이 포함되어 있습니다");
        }
        
        // 반복 메시지 검사
        if (isRepeatedMessage(content, userId)) {
            throw new MessageSpamException("동일한 메시지를 반복 발송할 수 없습니다");
        }
        
        // URL 스팸 검사
        if (containsSuspiciousUrls(content)) {
            throw new MessageSpamException("의심스러운 링크가 포함되어 있습니다");
        }
    }
}
```

### 3. 실시간 WebSocket 연동
```java
/**
 * 실시간 메시지 전송 서비스
 */
@Service
@RequiredArgsConstructor
public class RealtimeMessageService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 실시간 메시지 전송
     */
    public void sendRealtimeMessage(Long recipientId, MessageResponse message) {
        try {
            messagingTemplate.convertAndSendToUser(
                recipientId.toString(),
                "/queue/messages",
                message
            );
            
            // 읽지 않은 메시지 개수 업데이트
            updateUnreadCount(recipientId);
            
        } catch (Exception e) {
            log.error("실시간 메시지 전송 실패: recipientId={}, error={}", recipientId, e.getMessage());
        }
    }
}
```

---

*MessageController 긴급 설계 완료일: 2025-08-25*  
*구현 항목: 개인 메시징, 루트 태깅, 실시간 전송, 스팸 방지, 암호화*  
*보안 기능: Rate Limiting, 내용 암호화, 스팸 검증, 사용자 차단*  
*다음 단계: 나머지 보안 취약점 패치*