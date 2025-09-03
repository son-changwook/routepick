# 7-5e단계: Request/Response DTO 구현 (20개)

## 📋 구현 개요
- **Request DTOs**: 10개 (커뮤니티, 결제, 알림, 시스템 도메인별 요청 DTO)
- **Response DTOs**: 10개 (해당 도메인별 응답 DTO)
- **한국어 검증**: 커스텀 Validator 적용
- **보안 강화**: XSS 방지, 민감정보 마스킹

## 🎯 Request DTOs (10개)

### 1. PostCreateRequest (게시글 작성)
```java
package com.routepick.backend.dto.request.community;

import com.routepick.backend.common.validation.KoreanText;
import com.routepick.backend.common.validation.SafeHtml;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class PostCreateRequest {
    
    @NotBlank(message = "제목을 입력해주세요")
    @Size(min = 2, max = 100, message = "제목은 2-100자 사이로 입력해주세요")
    @KoreanText(message = "제목에 적절하지 않은 문자가 포함되어 있습니다")
    @SafeHtml
    private String title;
    
    @NotBlank(message = "내용을 입력해주세요")
    @Size(min = 5, max = 5000, message = "내용은 5-5000자 사이로 입력해주세요")
    @SafeHtml
    private String content;
    
    @NotNull(message = "게시판 카테고리를 선택해주세요")
    @Positive(message = "올바른 카테고리를 선택해주세요")
    private Long categoryId;
    
    @Size(max = 10, message = "이미지는 최대 10개까지 업로드 가능합니다")
    private List<MultipartFile> images;
    
    @Size(max = 3, message = "동영상은 최대 3개까지 업로드 가능합니다")
    private List<MultipartFile> videos;
    
    @Size(max = 20, message = "태그는 최대 20개까지 입력 가능합니다")
    private List<@NotNull @Positive Long> routeTagIds;
    
    private boolean isNoticePost = false;
}
```

### 2. CommentCreateRequest (댓글 작성)
```java
package com.routepick.backend.dto.request.community;

import com.routepick.backend.common.validation.KoreanText;
import com.routepick.backend.common.validation.SafeHtml;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CommentCreateRequest {
    
    @NotNull(message = "게시글 ID를 입력해주세요")
    @Positive(message = "올바른 게시글 ID를 입력해주세요")
    private Long postId;
    
    @Positive(message = "올바른 부모 댓글 ID를 입력해주세요")
    private Long parentId;
    
    @NotBlank(message = "댓글 내용을 입력해주세요")
    @Size(min = 1, max = 1000, message = "댓글은 1-1000자 사이로 입력해주세요")
    @KoreanText(message = "댓글에 적절하지 않은 문자가 포함되어 있습니다")
    @SafeHtml
    private String content;
    
    private boolean isPrivate = false;
}
```

### 3. PaymentProcessRequest (결제 처리)
```java
package com.routepick.backend.dto.request.payment;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class PaymentProcessRequest {
    
    @NotBlank(message = "결제 수단을 선택해주세요")
    @Pattern(regexp = "^(TOSS|KAKAO|NAVER|CARD|BANK_TRANSFER)$", 
             message = "지원하지 않는 결제 수단입니다")
    private String paymentMethod;
    
    @NotNull(message = "결제 금액을 입력해주세요")
    @DecimalMin(value = "100", message = "최소 결제 금액은 100원입니다")
    @DecimalMax(value = "1000000", message = "최대 결제 금액은 1,000,000원입니다")
    private BigDecimal amount;
    
    @NotBlank(message = "상품 타입을 입력해주세요")
    @Pattern(regexp = "^(GYM_MEMBERSHIP|PREMIUM_FEATURE|ROUTE_UNLOCK)$",
             message = "올바른 상품 타입을 선택해주세요")
    private String itemType;
    
    @NotNull(message = "상품 ID를 입력해주세요")
    @Positive(message = "올바른 상품 ID를 입력해주세요")
    private Long itemId;
    
    @Size(max = 200, message = "주문명은 200자 이하로 입력해주세요")
    private String orderName;
    
    @Pattern(regexp = "^[0-9a-zA-Z-_]{10,50}$",
             message = "주문번호 형식이 올바르지 않습니다")
    private String orderId;
    
    private Map<String, String> metadata;
}
```

### 4. PaymentRefundRequest (환불 요청)
```java
package com.routepick.backend.dto.request.payment;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRefundRequest {
    
    @NotNull(message = "결제 ID를 입력해주세요")
    @Positive(message = "올바른 결제 ID를 입력해주세요")
    private Long paymentId;
    
    @NotNull(message = "환불 금액을 입력해주세요")
    @DecimalMin(value = "100", message = "최소 환불 금액은 100원입니다")
    private BigDecimal refundAmount;
    
    @NotBlank(message = "환불 사유를 입력해주세요")
    @Size(min = 5, max = 500, message = "환불 사유는 5-500자 사이로 입력해주세요")
    private String refundReason;
    
    @Pattern(regexp = "^(CUSTOMER_REQUEST|DEFECTIVE_PRODUCT|SYSTEM_ERROR)$",
             message = "올바른 환불 사유 코드를 선택해주세요")
    private String refundReasonCode;
}
```

### 5. NotificationSettingsRequest (알림 설정)
```java
package com.routepick.backend.dto.request.notification;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;
import java.util.Set;

@Data
public class NotificationSettingsRequest {
    
    @NotNull(message = "푸시 알림 설정을 선택해주세요")
    private Boolean pushEnabled;
    
    @NotNull(message = "이메일 알림 설정을 선택해주세요")
    private Boolean emailEnabled;
    
    @NotNull(message = "SMS 알림 설정을 선택해주세요")
    private Boolean smsEnabled;
    
    @NotNull(message = "야간 모드 설정을 선택해주세요")
    private Boolean nightModeEnabled;
    
    private LocalTime nightModeStartTime;
    
    private LocalTime nightModeEndTime;
    
    private Set<String> enabledTypes;
    
    private Set<String> disabledTypes;
}
```

### 6. NotificationBulkReadRequest (일괄 읽음)
```java
package com.routepick.backend.dto.request.notification;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class NotificationBulkReadRequest {
    
    @Pattern(regexp = "^(PERSONAL|NOTICE|BANNER|POPUP|ALL)$",
             message = "올바른 알림 타입을 선택해주세요")
    private String type;
    
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
             message = "날짜 형식은 YYYY-MM-DD 형식이어야 합니다")
    private String startDate;
    
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
             message = "날짜 형식은 YYYY-MM-DD 형식이어야 합니다")
    private String endDate;
    
    private boolean markAllRead = false;
}
```

### 7. SystemAgreementUpdateRequest (약관 업데이트)
```java
package com.routepick.backend.dto.request.system;

import com.routepick.backend.common.validation.SafeHtml;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemAgreementUpdateRequest {
    
    @NotBlank(message = "약관 타입을 입력해주세요")
    @Pattern(regexp = "^(TERMS|PRIVACY|MARKETING)$",
             message = "올바른 약관 타입을 선택해주세요")
    private String type;
    
    @NotBlank(message = "버전을 입력해주세요")
    @Pattern(regexp = "^v\\d+\\.\\d+\\.\\d+$",
             message = "버전 형식은 v1.0.0 형태여야 합니다")
    private String version;
    
    @NotBlank(message = "제목을 입력해주세요")
    @Size(min = 5, max = 100, message = "제목은 5-100자 사이로 입력해주세요")
    @SafeHtml
    private String title;
    
    @NotBlank(message = "내용을 입력해주세요")
    @Size(min = 50, max = 50000, message = "내용은 50-50000자 사이로 입력해주세요")
    @SafeHtml
    private String content;
    
    @NotNull(message = "시행일을 입력해주세요")
    @Future(message = "시행일은 현재 시간보다 미래여야 합니다")
    private LocalDateTime effectiveDate;
    
    private boolean requiresNewConsent = true;
}
```

### 8. SystemApiConfigUpdateRequest (API 설정 업데이트)
```java
package com.routepick.backend.dto.request.system;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Map;

@Data
public class SystemApiConfigUpdateRequest {
    
    @NotBlank(message = "API 제공업체를 입력해주세요")
    @Pattern(regexp = "^(TOSS|KAKAO|NAVER|GOOGLE|FIREBASE|AWS)$",
             message = "지원하지 않는 API 제공업체입니다")
    private String provider;
    
    @NotBlank(message = "API 타입을 입력해주세요")
    @Pattern(regexp = "^(PAYMENT|SOCIAL|CLOUD|NOTIFICATION)$",
             message = "올바른 API 타입을 선택해주세요")
    private String apiType;
    
    @Size(max = 500, message = "API 키는 500자 이하로 입력해주세요")
    private String apiKey;
    
    @Size(max = 500, message = "시크릿 키는 500자 이하로 입력해주세요")
    private String secretKey;
    
    @Pattern(regexp = "^https?://.*",
             message = "올바른 URL 형식을 입력해주세요")
    private String endpointUrl;
    
    @NotNull(message = "활성화 상태를 선택해주세요")
    private Boolean isActive;
    
    private Map<String, String> additionalConfig;
}
```

### 9. MessageCreateRequest (메시지 생성)
```java
package com.routepick.backend.dto.request.message;

import com.routepick.backend.common.validation.KoreanText;
import com.routepick.backend.common.validation.SafeHtml;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class MessageCreateRequest {
    
    @NotNull(message = "수신자 ID를 입력해주세요")
    @Positive(message = "올바른 수신자 ID를 입력해주세요")
    private Long receiverId;
    
    @NotBlank(message = "제목을 입력해주세요")
    @Size(min = 1, max = 100, message = "제목은 1-100자 사이로 입력해주세요")
    @KoreanText(message = "제목에 적절하지 않은 문자가 포함되어 있습니다")
    @SafeHtml
    private String title;
    
    @NotBlank(message = "내용을 입력해주세요")
    @Size(min = 1, max = 2000, message = "내용은 1-2000자 사이로 입력해주세요")
    @KoreanText(message = "내용에 적절하지 않은 문자가 포함되어 있습니다")
    @SafeHtml
    private String content;
    
    @Pattern(regexp = "^(GENERAL|SYSTEM|ROUTE_SHARE|EVENT)$",
             message = "올바른 메시지 타입을 선택해주세요")
    private String messageType = "GENERAL";
    
    private List<@NotNull @Positive Long> routeTagIds;
}
```

### 10. RouteTagCreateRequest (루트 태그 생성)
```java
package com.routepick.backend.dto.request.route;

import com.routepick.backend.common.validation.KoreanText;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RouteTagCreateRequest {
    
    @NotNull(message = "루트 ID를 입력해주세요")
    @Positive(message = "올바른 루트 ID를 입력해주세요")
    private Long routeId;
    
    @NotEmpty(message = "최소 1개 이상의 태그를 선택해주세요")
    @Size(max = 10, message = "태그는 최대 10개까지 선택 가능합니다")
    private List<@NotNull @Positive Long> tagIds;
    
    @Size(max = 200, message = "설명은 200자 이하로 입력해주세요")
    @KoreanText(message = "설명에 적절하지 않은 문자가 포함되어 있습니다")
    private String description;
}
```

## 🎯 Response DTOs (10개)

### 1. PostResponse (게시글 응답)
```java
package com.routepick.backend.dto.response.community;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostResponse {
    
    private Long id;
    private String title;
    private String content;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImage;
    private Long categoryId;
    private String categoryName;
    
    private int likeCount;
    private int bookmarkCount;
    private int commentCount;
    private int viewCount;
    
    private boolean isLiked;
    private boolean isBookmarked;
    private boolean isNotice;
    
    private List<PostImageResponse> images;
    private List<PostVideoResponse> videos;
    private List<RouteTagResponse> routeTags;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
```

### 2. CommentResponse (댓글 응답)
```java
package com.routepick.backend.dto.response.community;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CommentResponse {
    
    private Long id;
    private Long postId;
    private Long parentId;
    private String content;
    
    private Long authorId;
    private String authorNickname;
    private String authorProfileImage;
    
    private int likeCount;
    private boolean isLiked;
    private boolean isPrivate;
    private boolean isDeleted;
    private int depth;
    
    private List<CommentResponse> replies;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
```

### 3. PaymentProcessResponse (결제 처리 응답)
```java
package com.routepick.backend.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentProcessResponse {
    
    private Long paymentId;
    private String orderId;
    private String paymentKey;
    private String paymentMethod;
    private BigDecimal amount;
    private String currency = "KRW";
    
    private String paymentStatus; // PENDING, SUCCESS, FAILED, CANCELLED
    private String failureReason;
    
    private String itemType;
    private Long itemId;
    private String orderName;
    
    private String approvalUrl;
    private String failUrl;
    private String cancelUrl;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;
}
```

### 4. PaymentHistoryResponse (결제 이력 응답)
```java
package com.routepick.backend.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentHistoryResponse {
    
    private Long paymentId;
    private String orderId;
    private String paymentMethod;
    private String maskedCardNumber; // 마스킹된 카드번호
    private BigDecimal amount;
    private String paymentStatus;
    
    private String itemType;
    private String itemName;
    private String orderName;
    
    private boolean isRefunded;
    private BigDecimal refundedAmount;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;
}
```

### 5. NotificationResponse (알림 응답)
```java
package com.routepick.backend.dto.response.notification;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    
    private Long id;
    private String type; // PERSONAL, NOTICE, BANNER, POPUP
    private String title;
    private String content;
    private String imageUrl;
    
    private String priority; // HIGH, MEDIUM, LOW
    private String status; // UNREAD, READ
    
    private Long targetId;
    private String targetType;
    private String actionUrl;
    
    private boolean isPersistent;
    private int unreadCount;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
}
```

### 6. NotificationSettingsResponse (알림 설정 응답)
```java
package com.routepick.backend.dto.response.notification;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class NotificationSettingsResponse {
    
    private Long userId;
    private boolean pushEnabled;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean nightModeEnabled;
    
    @JsonFormat(pattern = "HH:mm")
    private LocalTime nightModeStartTime;
    
    @JsonFormat(pattern = "HH:mm")
    private LocalTime nightModeEndTime;
    
    private Set<String> enabledTypes;
    private Set<String> disabledTypes;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdated;
}
```

### 7. SystemHealthResponse (시스템 상태 응답)
```java
package com.routepick.backend.dto.response.system;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class SystemHealthResponse {
    
    private String status; // UP, DOWN, DEGRADED
    private String version;
    private String environment;
    
    private DatabaseHealthResponse database;
    private RedisHealthResponse redis;
    private Map<String, ExternalApiHealthResponse> externalApis;
    
    private SystemPerformanceResponse performance;
    private SystemResourceResponse resources;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    @Data
    @Builder
    public static class DatabaseHealthResponse {
        private String status;
        private int connectionCount;
        private long responseTimeMs;
        private String error;
    }
    
    @Data
    @Builder
    public static class RedisHealthResponse {
        private String status;
        private int connectionCount;
        private long responseTimeMs;
        private String error;
    }
}
```

### 8. SystemAgreementResponse (약관 응답)
```java
package com.routepick.backend.dto.response.system;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SystemAgreementResponse {
    
    private List<AgreementContentResponse> agreements;
    private String currentVersion;
    
    @Data
    @Builder
    public static class AgreementContentResponse {
        
        private Long id;
        private String type; // TERMS, PRIVACY, MARKETING
        private String version;
        private String title;
        private String content;
        
        private boolean isActive;
        private boolean isRequired;
        
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime effectiveDate;
        
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
```

### 9. MessageResponse (메시지 응답)
```java
package com.routepick.backend.dto.response.message;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MessageResponse {
    
    private Long id;
    private String title;
    private String content;
    private String messageType;
    
    private Long senderId;
    private String senderNickname;
    private String senderProfileImage;
    
    private Long receiverId;
    private String receiverNickname;
    
    private String status; // UNREAD, READ, ARCHIVED, DELETED
    private boolean isImportant;
    
    private List<RouteTagResponse> routeTags;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAt;
}
```

### 10. RouteTagResponse (루트 태그 응답)
```java
package com.routepick.backend.dto.response.route;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RouteTagResponse {
    
    private Long id;
    private Long routeId;
    private String routeName;
    
    private Long tagId;
    private String tagName;
    private String tagType;
    private String tagColor;
    private String tagIcon;
    
    private String description;
    private int usageCount;
    private boolean isUserCreated;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
```

## ✅ 설계 완료
- [x] Request DTOs (10개)
- [x] Response DTOs (10개)
- [x] 한국어 검증 적용 (@KoreanText)
- [x] XSS 방지 (@SafeHtml)
- [x] 민감정보 마스킹 (카드번호 등)
- [x] JSON 날짜 포맷 통일

---
*Request/Response DTO 20개 설계 완료 - 다음: 보안 패치 적용*