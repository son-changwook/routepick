# Step 4-4b-2: 시스템 관리 엔티티 완성

> 마지막 6개 시스템 관리 엔티티 설계 (최종 완성)  
> 생성일: 2025-08-20  
> 기반: step4-4b_payment_notification.md, 전체 50개 엔티티 달성

---

## 🎯 설계 목표

- **시스템 로깅**: API 호출, 웹훅, 외부 API 연동 추적
- **메시지 시스템**: 사용자 간 메시지, 루트 태깅 지원
- **커뮤니티 상호작용**: 댓글 좋아요 시스템
- **성능 최적화**: 로그 검색, 메시지 조회, 상호작용 통계

---

## 💬 1. CommentLike 엔티티 - 댓글 좋아요

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 댓글 좋아요
 * - 댓글별 좋아요 관리
 * - 중복 좋아요 방지
 * - 통계 집계 최적화
 */
@Entity
@Table(name = "comment_likes", indexes = {
    @Index(name = "idx_comment_like_user_comment", columnList = "user_id, comment_id", unique = true),
    @Index(name = "idx_comment_like_comment", columnList = "comment_id"),
    @Index(name = "idx_comment_like_user", columnList = "user_id"),
    @Index(name = "idx_comment_like_created", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CommentLike extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long likeId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;
    
    @NotNull
    @Column(name = "liked_at", nullable = false)
    private LocalDateTime likedAt = LocalDateTime.now();
    
    // ===== 생성자 =====
    
    public static CommentLike createLike(User user, Comment comment) {
        return CommentLike.builder()
                .user(user)
                .comment(comment)
                .likedAt(LocalDateTime.now())
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 좋아요 유효성 검증
     */
    public boolean isValidLike() {
        return user != null && comment != null && likedAt != null;
    }
    
    /**
     * 본인 댓글 좋아요 여부 확인
     */
    @Transient
    public boolean isOwnCommentLike() {
        return comment != null && comment.getUser().equals(user);
    }
    
    /**
     * 좋아요 설명 정보
     */
    @Transient
    public String getLikeInfo() {
        return String.format("사용자 %s님이 댓글에 좋아요", user.getNickName());
    }
}
```

---

## 📨 2. Message 엔티티 - 메시지

```java
package com.routepick.domain.message.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.MessageType;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 메시지
 * - 사용자 간 메시지 교환
 * - 루트 정보 포함 메시지 지원
 * - 읽음 상태 관리
 */
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_message_sender", columnList = "sender_id"),
    @Index(name = "idx_message_receiver", columnList = "receiver_id"),
    @Index(name = "idx_message_conversation", columnList = "sender_id, receiver_id, sent_at DESC"),
    @Index(name = "idx_message_unread", columnList = "receiver_id, is_read, sent_at"),
    @Index(name = "idx_message_type", columnList = "message_type"),
    @Index(name = "idx_message_sent", columnList = "sent_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Message extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;
    
    // ===== 메시지 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private MessageType messageType = MessageType.TEXT;
    
    @NotBlank
    @Size(min = 1, max = 1000, message = "메시지 내용은 1-1000자 사이여야 합니다")
    @Column(name = "content", nullable = false, length = 1000)
    private String content;
    
    @Column(name = "title", length = 100)
    private String title; // 메시지 제목 (선택사항)
    
    // ===== 상태 정보 =====
    
    @NotNull
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;
    
    @Column(name = "read_at")
    private LocalDateTime readAt;
    
    @NotNull
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();
    
    // ===== 연관 관계 =====
    
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MessageRouteTag> messageRouteTags = new ArrayList<>();
    
    // ===== 생성자 =====
    
    public static Message createTextMessage(User sender, User receiver, String content) {
        return Message.builder()
                .sender(sender)
                .receiver(receiver)
                .messageType(MessageType.TEXT)
                .content(content)
                .sentAt(LocalDateTime.now())
                .build();
    }
    
    public static Message createRouteMessage(User sender, User receiver, String content, String title) {
        return Message.builder()
                .sender(sender)
                .receiver(receiver)
                .messageType(MessageType.ROUTE_SHARE)
                .content(content)
                .title(title)
                .sentAt(LocalDateTime.now())
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 메시지 읽음 처리
     */
    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }
    
    /**
     * 메시지 유효성 검증
     */
    public boolean isValidMessage() {
        return sender != null && receiver != null && 
               content != null && !content.trim().isEmpty() &&
               !sender.equals(receiver);
    }
    
    /**
     * 루트 태그 추가
     */
    public void addRouteTag(MessageRouteTag routeTag) {
        messageRouteTags.add(routeTag);
        routeTag.setMessage(this);
    }
    
    /**
     * 대화 상대방 반환
     */
    @Transient
    public User getOtherUser(User currentUser) {
        return sender.equals(currentUser) ? receiver : sender;
    }
    
    /**
     * 읽지 않은 메시지 여부
     */
    @Transient
    public boolean isUnread() {
        return !isRead;
    }
    
    /**
     * 메시지 요약 정보
     */
    @Transient
    public String getMessageSummary() {
        String preview = content.length() > 50 ? content.substring(0, 47) + "..." : content;
        return String.format("%s -> %s: %s", sender.getNickName(), receiver.getNickName(), preview);
    }
}
```

---

## 🏷️ 3. MessageRouteTag 엔티티 - 메시지 루트 태깅

```java
package com.routepick.domain.message.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.tag.entity.Tag;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * 메시지 루트 태깅
 * - 메시지에 루트 정보 첨부
 * - 추천 점수 포함
 * - 루트 공유 최적화
 */
@Entity
@Table(name = "message_route_tags", indexes = {
    @Index(name = "idx_message_route_tag_message", columnList = "message_id"),
    @Index(name = "idx_message_route_tag_route", columnList = "route_id"),
    @Index(name = "idx_message_route_tag_tag", columnList = "tag_id"),
    @Index(name = "idx_message_route_score", columnList = "message_id, recommendation_score DESC"),
    @Index(name = "idx_message_route_combo", columnList = "message_id, route_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MessageRouteTag extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_route_tag_id")
    private Long messageRouteTagId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag; // 연관 태그 (선택사항)
    
    // ===== 추천 정보 =====
    
    @DecimalMin(value = "0.0", message = "추천 점수는 0 이상이어야 합니다")
    @DecimalMax(value = "100.0", message = "추천 점수는 100 이하여야 합니다")
    @Column(name = "recommendation_score", precision = 5, scale = 2)
    private BigDecimal recommendationScore; // 추천 점수 (0-100)
    
    @Column(name = "recommendation_reason", length = 200)
    private String recommendationReason; // 추천 이유
    
    // ===== 생성자 =====
    
    public static MessageRouteTag createRouteTag(Message message, Route route, BigDecimal score) {
        return MessageRouteTag.builder()
                .message(message)
                .route(route)
                .recommendationScore(score)
                .build();
    }
    
    public static MessageRouteTag createRouteTagWithTag(Message message, Route route, Tag tag, BigDecimal score, String reason) {
        return MessageRouteTag.builder()
                .message(message)
                .route(route)
                .tag(tag)
                .recommendationScore(score)
                .recommendationReason(reason)
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 유효한 루트 태깅인지 검증
     */
    public boolean isValidRouteTag() {
        return message != null && route != null && 
               recommendationScore != null && 
               recommendationScore.compareTo(BigDecimal.ZERO) >= 0;
    }
    
    /**
     * 고점수 추천 여부
     */
    @Transient
    public boolean isHighRecommendation() {
        return recommendationScore != null && 
               recommendationScore.compareTo(new BigDecimal("70")) >= 0;
    }
    
    /**
     * 태그 기반 추천 여부
     */
    @Transient
    public boolean isTagBasedRecommendation() {
        return tag != null;
    }
    
    /**
     * 추천 태그 정보
     */
    @Transient
    public String getRecommendationInfo() {
        String info = String.format("루트: %s (점수: %.1f)", 
                route.getRouteName(), recommendationScore);
        if (tag != null) {
            info += String.format(", 태그: %s", tag.getTagName());
        }
        return info;
    }
}
```

---

## 📊 4. ApiLog 엔티티 - API 호출 로그

```java
package com.routepick.domain.system.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.ApiLogLevel;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * API 호출 로그
 * - REST API 호출 추적
 * - 성능 모니터링
 * - 에러 추적 및 분석
 */
@Entity
@Table(name = "api_logs", indexes = {
    @Index(name = "idx_api_log_endpoint", columnList = "endpoint"),
    @Index(name = "idx_api_log_user", columnList = "user_id"),
    @Index(name = "idx_api_log_status", columnList = "response_status"),
    @Index(name = "idx_api_log_level", columnList = "log_level"),
    @Index(name = "idx_api_log_method", columnList = "http_method"),
    @Index(name = "idx_api_log_time", columnList = "request_time DESC"),
    @Index(name = "idx_api_log_duration", columnList = "duration_ms DESC"),
    @Index(name = "idx_api_log_error", columnList = "log_level, response_status, request_time DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ApiLog extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // 호출 사용자 (비로그인 시 null)
    
    // ===== 요청 정보 =====
    
    @NotBlank
    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint; // API 엔드포인트
    
    @NotBlank
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod; // GET, POST, PUT, DELETE 등
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // IPv4/IPv6 지원
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @NotNull
    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime = LocalDateTime.now();
    
    // ===== 응답 정보 =====
    
    @Min(value = 100, message = "HTTP 상태 코드는 100 이상이어야 합니다")
    @Max(value = 599, message = "HTTP 상태 코드는 599 이하여야 합니다")
    @Column(name = "response_status")
    private Integer responseStatus; // HTTP 상태 코드
    
    @Min(value = 0, message = "응답 시간은 0ms 이상이어야 합니다")
    @Column(name = "duration_ms")
    private Long durationMs; // 응답 시간 (밀리초)
    
    @Column(name = "response_size")
    private Long responseSize; // 응답 크기 (바이트)
    
    // ===== 로그 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "log_level", nullable = false, length = 10)
    private ApiLogLevel logLevel = ApiLogLevel.INFO;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage; // 에러 메시지
    
    @Column(name = "exception_class", length = 200)
    private String exceptionClass; // 예외 클래스명
    
    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams; // 요청 파라미터 (JSON)
    
    // ===== 생성자 =====
    
    public static ApiLog createInfoLog(String endpoint, String method, String clientIp) {
        return ApiLog.builder()
                .endpoint(endpoint)
                .httpMethod(method)
                .clientIp(clientIp)
                .logLevel(ApiLogLevel.INFO)
                .requestTime(LocalDateTime.now())
                .build();
    }
    
    public static ApiLog createErrorLog(String endpoint, String method, String errorMessage, String exceptionClass) {
        return ApiLog.builder()
                .endpoint(endpoint)
                .httpMethod(method)
                .logLevel(ApiLogLevel.ERROR)
                .errorMessage(errorMessage)
                .exceptionClass(exceptionClass)
                .requestTime(LocalDateTime.now())
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 응답 완료 처리
     */
    public void completeResponse(int status, long duration, long size) {
        this.responseStatus = status;
        this.durationMs = duration;
        this.responseSize = size;
        
        // 상태 코드별 로그 레벨 자동 설정
        if (status >= 500) {
            this.logLevel = ApiLogLevel.ERROR;
        } else if (status >= 400) {
            this.logLevel = ApiLogLevel.WARN;
        }
    }
    
    /**
     * 에러 정보 설정
     */
    public void setErrorInfo(String message, String exceptionClass) {
        this.errorMessage = message;
        this.exceptionClass = exceptionClass;
        this.logLevel = ApiLogLevel.ERROR;
    }
    
    /**
     * 느린 API 여부 (1초 이상)
     */
    @Transient
    public boolean isSlowApi() {
        return durationMs != null && durationMs > 1000;
    }
    
    /**
     * 에러 로그 여부
     */
    @Transient
    public boolean isErrorLog() {
        return logLevel == ApiLogLevel.ERROR || 
               (responseStatus != null && responseStatus >= 400);
    }
    
    /**
     * 성공 응답 여부
     */
    @Transient
    public boolean isSuccessResponse() {
        return responseStatus != null && responseStatus >= 200 && responseStatus < 300;
    }
    
    /**
     * 로그 요약 정보
     */
    @Transient
    public String getLogSummary() {
        return String.format("%s %s - %d (%dms)", 
                httpMethod, endpoint, responseStatus, durationMs);
    }
}
```

---

## ⚙️ 5. ExternalApiConfig 엔티티 - 외부 API 설정

```java
package com.routepick.domain.system.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.ApiProviderType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 외부 API 설정
 * - 소셜 로그인, 결제, 지도 등 외부 API 설정 관리
 * - API 키, 엔드포인트, 제한사항 관리
 * - 환경별 설정 분리
 */
@Entity
@Table(name = "external_api_configs", indexes = {
    @Index(name = "idx_external_api_provider", columnList = "provider_type"),
    @Index(name = "idx_external_api_environment", columnList = "environment"),
    @Index(name = "idx_external_api_active", columnList = "is_active"),
    @Index(name = "idx_external_api_provider_env", columnList = "provider_type, environment", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExternalApiConfig extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;
    
    // ===== 제공자 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private ApiProviderType providerType;
    
    @NotBlank
    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName; // GOOGLE, KAKAO, NAVER, FACEBOOK, INICIS 등
    
    @NotBlank
    @Column(name = "environment", nullable = false, length = 20)
    private String environment; // DEV, STAGING, PROD
    
    // ===== API 설정 =====
    
    @NotBlank
    @Column(name = "api_key", nullable = false, length = 200)
    private String apiKey; // 암호화된 API 키
    
    @Column(name = "api_secret", length = 200)
    private String apiSecret; // 암호화된 API 시크릿
    
    @NotBlank
    @Column(name = "base_url", nullable = false, length = 200)
    private String baseUrl; // 기본 URL
    
    @Column(name = "callback_url", length = 200)
    private String callbackUrl; // 콜백 URL (소셜 로그인용)
    
    // ===== 제한 설정 =====
    
    @Min(value = 1, message = "시간당 호출 제한은 1 이상이어야 합니다")
    @Max(value = 1000000, message = "시간당 호출 제한은 1,000,000 이하여야 합니다")
    @Column(name = "rate_limit_per_hour")
    private Integer rateLimitPerHour; // 시간당 호출 제한
    
    @Min(value = 1000, message = "타임아웃은 1000ms 이상이어야 합니다")
    @Max(value = 300000, message = "타임아웃은 300초 이하여야 합니다")
    @Column(name = "timeout_ms")
    private Integer timeoutMs = 30000; // 타임아웃 (밀리초)
    
    @Min(value = 0, message = "재시도 횟수는 0 이상이어야 합니다")
    @Max(value = 10, message = "재시도 횟수는 10 이하여야 합니다")
    @Column(name = "retry_count")
    private Integer retryCount = 3; // 재시도 횟수
    
    // ===== 상태 정보 =====
    
    @NotNull
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "last_health_check")
    private LocalDateTime lastHealthCheck; // 마지막 상태 확인
    
    @Column(name = "health_status", length = 20)
    private String healthStatus; // HEALTHY, UNHEALTHY, UNKNOWN
    
    @Column(name = "description", length = 500)
    private String description; // 설정 설명
    
    // ===== 생성자 =====
    
    public static ExternalApiConfig createSocialLogin(ApiProviderType type, String providerName, 
                                                     String environment, String apiKey, String baseUrl, String callbackUrl) {
        return ExternalApiConfig.builder()
                .providerType(type)
                .providerName(providerName)
                .environment(environment)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .callbackUrl(callbackUrl)
                .rateLimitPerHour(1000)
                .timeoutMs(30000)
                .retryCount(3)
                .build();
    }
    
    public static ExternalApiConfig createPaymentGateway(String providerName, String environment, 
                                                        String apiKey, String apiSecret, String baseUrl) {
        return ExternalApiConfig.builder()
                .providerType(ApiProviderType.PAYMENT)
                .providerName(providerName)
                .environment(environment)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .baseUrl(baseUrl)
                .rateLimitPerHour(10000)
                .timeoutMs(60000)
                .retryCount(5)
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * API 설정 활성화
     */
    public void activate() {
        this.isActive = true;
    }
    
    /**
     * API 설정 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * 헬스 체크 업데이트
     */
    public void updateHealthStatus(String status) {
        this.healthStatus = status;
        this.lastHealthCheck = LocalDateTime.now();
    }
    
    /**
     * 유효한 설정인지 확인
     */
    public boolean isValidConfig() {
        return providerType != null && apiKey != null && !apiKey.trim().isEmpty() &&
               baseUrl != null && !baseUrl.trim().isEmpty() && isActive;
    }
    
    /**
     * 프로덕션 환경 여부
     */
    @Transient
    public boolean isProduction() {
        return "PROD".equalsIgnoreCase(environment);
    }
    
    /**
     * 헬스 체크 필요 여부 (1시간마다)
     */
    @Transient
    public boolean needsHealthCheck() {
        return lastHealthCheck == null || 
               lastHealthCheck.isBefore(LocalDateTime.now().minusHours(1));
    }
    
    /**
     * 설정 요약 정보
     */
    @Transient
    public String getConfigSummary() {
        return String.format("%s (%s) - %s 환경", 
                providerName, providerType, environment);
    }
}
```

---

## 🔗 6. WebhookLog 엔티티 - 웹훅 로그

```java
package com.routepick.domain.system.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.WebhookStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 웹훅 로그
 * - 외부 시스템으로의 웹훅 호출 추적
 * - 결제, 알림 등 이벤트 전송 로그
 * - 재시도 및 실패 추적
 */
@Entity
@Table(name = "webhook_logs", indexes = {
    @Index(name = "idx_webhook_log_event", columnList = "event_type"),
    @Index(name = "idx_webhook_log_status", columnList = "webhook_status"),
    @Index(name = "idx_webhook_log_url", columnList = "target_url"),
    @Index(name = "idx_webhook_log_time", columnList = "sent_at DESC"),
    @Index(name = "idx_webhook_log_retry", columnList = "retry_count"),
    @Index(name = "idx_webhook_log_failed", columnList = "webhook_status, sent_at DESC"),
    @Index(name = "idx_webhook_log_duration", columnList = "response_time_ms DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class WebhookLog extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "webhook_log_id")
    private Long webhookLogId;
    
    // ===== 이벤트 정보 =====
    
    @NotBlank
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // PAYMENT_SUCCESS, USER_REGISTER, ROUTE_CREATED 등
    
    @Column(name = "event_id", length = 100)
    private String eventId; // 이벤트 고유 ID
    
    @NotBlank
    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl; // 웹훅 대상 URL
    
    // ===== 요청 정보 =====
    
    @NotBlank
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod = "POST";
    
    @Column(name = "request_headers", columnDefinition = "TEXT")
    private String requestHeaders; // 요청 헤더 (JSON)
    
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody; // 요청 본문 (JSON)
    
    @NotNull
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();
    
    // ===== 응답 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "webhook_status", nullable = false, length = 20)
    private WebhookStatus webhookStatus = WebhookStatus.PENDING;
    
    @Min(value = 100, message = "HTTP 상태 코드는 100 이상이어야 합니다")
    @Max(value = 599, message = "HTTP 상태 코드는 599 이하여야 합니다")
    @Column(name = "response_status")
    private Integer responseStatus; // HTTP 응답 상태
    
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody; // 응답 본문
    
    @Min(value = 0, message = "응답 시간은 0ms 이상이어야 합니다")
    @Column(name = "response_time_ms")
    private Long responseTimeMs; // 응답 시간 (밀리초)
    
    // ===== 재시도 정보 =====
    
    @Min(value = 0, message = "재시도 횟수는 0 이상이어야 합니다")
    @Max(value = 10, message = "재시도 횟수는 10 이하여야 합니다")
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    private Integer maxRetries = 3;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt; // 다음 재시도 시각
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage; // 에러 메시지
    
    // ===== 생성자 =====
    
    public static WebhookLog createWebhook(String eventType, String eventId, String targetUrl, String requestBody) {
        return WebhookLog.builder()
                .eventType(eventType)
                .eventId(eventId)
                .targetUrl(targetUrl)
                .requestBody(requestBody)
                .sentAt(LocalDateTime.now())
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 웹훅 성공 처리
     */
    public void markSuccess(int responseStatus, String responseBody, long responseTime) {
        this.webhookStatus = WebhookStatus.SUCCESS;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseTimeMs = responseTime;
    }
    
    /**
     * 웹훅 실패 처리
     */
    public void markFailure(String errorMessage, Integer responseStatus) {
        this.webhookStatus = WebhookStatus.FAILED;
        this.errorMessage = errorMessage;
        this.responseStatus = responseStatus;
        
        // 재시도 가능한 경우 스케줄링
        if (canRetry()) {
            scheduleRetry();
        }
    }
    
    /**
     * 재시도 스케줄링
     */
    public void scheduleRetry() {
        this.retryCount++;
        this.webhookStatus = WebhookStatus.RETRY_SCHEDULED;
        
        // 지수 백오프: 2^retryCount 분 후 재시도
        long delayMinutes = (long) Math.pow(2, retryCount);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
    }
    
    /**
     * 재시도 가능 여부
     */
    public boolean canRetry() {
        return retryCount < maxRetries && 
               (responseStatus == null || responseStatus >= 500 || responseStatus == 429);
    }
    
    /**
     * 재시도 필요 여부
     */
    public boolean needsRetry() {
        return webhookStatus == WebhookStatus.RETRY_SCHEDULED &&
               nextRetryAt != null && 
               nextRetryAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * 성공 응답 여부
     */
    @Transient
    public boolean isSuccessResponse() {
        return responseStatus != null && responseStatus >= 200 && responseStatus < 300;
    }
    
    /**
     * 최종 실패 여부
     */
    @Transient
    public boolean isFinalFailure() {
        return webhookStatus == WebhookStatus.FAILED && !canRetry();
    }
    
    /**
     * 느린 웹훅 여부 (5초 이상)
     */
    @Transient
    public boolean isSlowWebhook() {
        return responseTimeMs != null && responseTimeMs > 5000;
    }
    
    /**
     * 웹훅 로그 요약
     */
    @Transient
    public String getWebhookSummary() {
        return String.format("%s -> %s (%s, %d회 시도)", 
                eventType, targetUrl, webhookStatus, retryCount + 1);
    }
}
```

---

## 📋 완성된 엔티티 검증

### 🎯 총 50개 엔티티 달성 확인

#### 1단계: 기본 사용자 엔티티 (5개) ✅
- User, UserProfile, UserVerification, UserAgreement, SocialAccount

#### 2단계: 태그 및 비즈니스 엔티티 (9개) ✅
- Tag, UserPreferredTag, RouteTag, UserRouteRecommendation, ApiToken, AgreementContent, Gym, GymBranch, GymMember

#### 4-3a단계: 암장 관련 엔티티 (5개) ✅
- Gym, GymBranch, GymMember, Wall, BranchImage

#### 4-3b단계: 루트 관련 엔티티 (7개) ✅
- Route, RouteSetter, RouteImage, RouteVideo, RouteComment, RouteDifficultyVote, RouteScrap

#### 4-3c단계: 클라이밍 및 활동 엔티티 (5개) ✅
- ClimbingLevel, ClimbingShoe, UserClimbingShoe, UserClimb, UserFollow

#### 4-4a단계: 커뮤니티 엔티티 (8개) ✅
- BoardCategory, Post, PostImage, PostVideo, PostRouteTag, PostLike, PostBookmark, Comment

#### 4-4b-1단계: 결제 및 알림 엔티티 (8개) ✅
- PaymentRecord, PaymentDetail, PaymentItem, PaymentRefund, Notification, Notice, Banner, AppPopup

#### 4-4b-2단계: 시스템 관리 엔티티 (6개) ✅
- CommentLike, Message, MessageRouteTag, ApiLog, ExternalApiConfig, WebhookLog

### ✅ 총합: 53개 엔티티 (중복 제거 시 50개)

---

## 🔍 최종 검증 사항

### 1. BaseEntity 상속 ✅
- 모든 엔티티가 BaseEntity 상속
- createdAt, updatedAt, createdBy, updatedBy 자동 관리

### 2. LAZY 로딩 적용 ✅
- 모든 @ManyToOne, @OneToMany, @OneToOne 관계에 FetchType.LAZY 적용
- 성능 최적화 및 N+1 쿼리 방지

### 3. 인덱스 최적화 ✅
- 단일 인덱스: 조회 최적화
- 복합 인덱스: 정렬 및 범위 검색 최적화
- 유니크 인덱스: 데이터 무결성 보장

### 4. 한국 특화 검증 ✅
- GPS 좌표 범위 (위도: 33-43, 경도: 124-132)
- 휴대폰 번호 패턴 (010-0000-0000)
- 한글 닉네임 지원 (2-10자)
- 한국 PG사 연동 (이니시스, 토스, 카카오페이, 네이버페이)

### 5. 비즈니스 메서드 포함 ✅
- 엔티티별 핵심 비즈니스 로직 메서드
- @Transient 계산 메서드
- 생성자 패턴 (정적 팩토리 메서드)

---

## 🚀 다음 단계 (5단계)

- Repository 레이어 설계
- JPA 쿼리 메서드 정의
- QueryDSL 동적 쿼리 구현
- 성능 테스트 및 최적화

---

*RoutePickProj 4단계 완료 - 총 50개 엔티티 설계 완성*
*다음: 5단계 Repository 레이어 구현*