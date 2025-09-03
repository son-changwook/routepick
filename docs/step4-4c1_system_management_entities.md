# Step 4-4c1: 시스템 관리 엔티티 설계

> **RoutePickr 시스템 관리** - 댓글 상호작용, 메시지 시스템, 루트 태깅  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-4c1 (JPA 엔티티 50개 - 시스템 관리 3개)  
> **분할**: step4-4c_system_final.md → 시스템 관리 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 시스템 관리 엔티티**를 담고 있습니다.

### 🎯 주요 특징
- **댓글 상호작용**: 댓글 좋아요 시스템, 중복 방지, 통계 집계
- **메시지 시스템**: 사용자 간 메시지 교환, 읽음 상태 관리
- **루트 공유**: 메시지에 루트 정보 첨부, 추천 점수 시스템
- **성능 최적화**: 복합 인덱스, 중복 방지, 효율적 조회

### 📊 엔티티 목록 (3개)
1. **CommentLike** - 댓글 좋아요 (중복 방지, 통계 집계)
2. **Message** - 메시지 (사용자 간 메시지 교환)
3. **MessageRouteTag** - 메시지 루트 태깅 (루트 공유, 추천)

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
    @Column(name = "comment_like_id")
    private Long commentLikeId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;
    
    // ===== 좋아요 정보 =====
    
    @NotNull
    @Column(name = "liked_at", nullable = false)
    private LocalDateTime likedAt = LocalDateTime.now();
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // 클라이언트 IP (추적용)
    
    // ===== 생성자 =====
    
    public static CommentLike create(User user, Comment comment, String clientIp) {
        return CommentLike.builder()
                .user(user)
                .comment(comment)
                .clientIp(clientIp)
                .likedAt(LocalDateTime.now())
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 좋아요 유효성 검증
     */
    public boolean isValidLike() {
        return user != null && comment != null && 
               !user.equals(comment.getUser()); // 자기 댓글 좋아요 방지
    }
    
    /**
     * 좋아요 정보 요약
     */
    @Transient
    public String getLikeSummary() {
        return String.format("사용자 %s님이 댓글에 좋아요", user.getNickName());
    }
    
    @Override
    public Long getId() {
        return commentLikeId;
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
    
    public static Message createSystemMessage(User receiver, String content, String title) {
        return Message.builder()
                .receiver(receiver)
                .messageType(MessageType.SYSTEM)
                .content(content)
                .title(title)
                .sentAt(LocalDateTime.now())
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 메시지 타입 한글 표시
     */
    @Transient
    public String getMessageTypeKorean() {
        if (messageType == null) return "일반";
        
        return switch (messageType) {
            case TEXT -> "일반 메시지";
            case ROUTE_SHARE -> "루트 공유";
            case CLIMB_INVITE -> "클라이밍 초대";
            case FOLLOW_REQUEST -> "팔로우 요청";
            case SYSTEM -> "시스템 알림";
            default -> "기타";
        };
    }
    
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
        // 시스템 메시지인 경우 sender가 null일 수 있음
        if (messageType == MessageType.SYSTEM) {
            return receiver != null && content != null && !content.trim().isEmpty();
        }
        
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
        if (messageType == MessageType.SYSTEM) {
            return null; // 시스템 메시지는 상대방이 없음
        }
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
     * 루트 공유 메시지 여부
     */
    @Transient
    public boolean isRouteMessage() {
        return messageType == MessageType.ROUTE_SHARE && 
               messageRouteTags != null && !messageRouteTags.isEmpty();
    }
    
    /**
     * 시스템 메시지 여부
     */
    @Transient
    public boolean isSystemMessage() {
        return messageType == MessageType.SYSTEM;
    }
    
    /**
     * 메시지 요약 정보
     */
    @Transient
    public String getMessageSummary() {
        String preview = content.length() > 50 ? content.substring(0, 47) + "..." : content;
        
        if (isSystemMessage()) {
            return String.format("시스템 -> %s: %s", receiver.getNickName(), preview);
        }
        
        return String.format("%s -> %s: %s", sender.getNickName(), receiver.getNickName(), preview);
    }
    
    /**
     * 메시지 발송 후 시간 계산
     */
    @Transient
    public String getTimeSinceSent() {
        LocalDateTime now = LocalDateTime.now();
        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(sentAt, now);
        
        if (minutes < 1) return "방금 전";
        if (minutes < 60) return minutes + "분 전";
        
        long hours = minutes / 60;
        if (hours < 24) return hours + "시간 전";
        
        long days = hours / 24;
        if (days < 7) return days + "일 전";
        
        return sentAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    
    @Override
    public Long getId() {
        return messageId;
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
    
    @Column(name = "sender_rating")
    private Integer senderRating; // 발송자의 이 루트에 대한 평점 (1-5)
    
    @Column(name = "difficulty_match_score", precision = 5, scale = 2)
    private BigDecimal difficultyMatchScore; // 난이도 적합성 점수
    
    @Column(name = "style_match_score", precision = 5, scale = 2)
    private BigDecimal styleMatchScore; // 스타일 적합성 점수
    
    // ===== 생성자 =====
    
    public static MessageRouteTag createRouteTag(Message message, Route route, BigDecimal score) {
        return MessageRouteTag.builder()
                .message(message)
                .route(route)
                .recommendationScore(score)
                .build();
    }
    
    public static MessageRouteTag createRouteTagWithTag(Message message, Route route, Tag tag, 
                                                      BigDecimal score, String reason) {
        return MessageRouteTag.builder()
                .message(message)
                .route(route)
                .tag(tag)
                .recommendationScore(score)
                .recommendationReason(reason)
                .build();
    }
    
    public static MessageRouteTag createDetailedRouteTag(Message message, Route route, Tag tag,
                                                        BigDecimal score, String reason, Integer rating,
                                                        BigDecimal difficultyMatch, BigDecimal styleMatch) {
        return MessageRouteTag.builder()
                .message(message)
                .route(route)
                .tag(tag)
                .recommendationScore(score)
                .recommendationReason(reason)
                .senderRating(rating)
                .difficultyMatchScore(difficultyMatch)
                .styleMatchScore(styleMatch)
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
     * 중간 추천 여부
     */
    @Transient
    public boolean isMediumRecommendation() {
        return recommendationScore != null && 
               recommendationScore.compareTo(new BigDecimal("40")) >= 0 &&
               recommendationScore.compareTo(new BigDecimal("70")) < 0;
    }
    
    /**
     * 낮은 추천 여부
     */
    @Transient
    public boolean isLowRecommendation() {
        return recommendationScore != null && 
               recommendationScore.compareTo(new BigDecimal("40")) < 0;
    }
    
    /**
     * 태그 기반 추천 여부
     */
    @Transient
    public boolean isTagBasedRecommendation() {
        return tag != null;
    }
    
    /**
     * 추천 등급 반환
     */
    @Transient
    public String getRecommendationGrade() {
        if (recommendationScore == null) return "없음";
        
        if (isHighRecommendation()) return "강력 추천";
        if (isMediumRecommendation()) return "추천";
        return "참고";
    }
    
    /**
     * 추천 이유 한글 표시
     */
    @Transient
    public String getRecommendationReasonKorean() {
        if (recommendationReason == null || recommendationReason.isEmpty()) {
            if (tag != null) {
                return tag.getTagName() + " 스타일 매칭";
            }
            return "일반 추천";
        }
        return recommendationReason;
    }
    
    /**
     * 전체 매치 점수 계산
     */
    @Transient
    public BigDecimal getOverallMatchScore() {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        
        if (difficultyMatchScore != null) {
            total = total.add(difficultyMatchScore);
            count++;
        }
        
        if (styleMatchScore != null) {
            total = total.add(styleMatchScore);
            count++;
        }
        
        if (recommendationScore != null) {
            total = total.add(recommendationScore);
            count++;
        }
        
        return count > 0 ? total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP) 
                        : BigDecimal.ZERO;
    }
    
    /**
     * 추천 태그 정보
     */
    @Transient
    public String getRecommendationInfo() {
        StringBuilder info = new StringBuilder();
        info.append(String.format("루트: %s", route.getRouteName()));
        
        if (recommendationScore != null) {
            info.append(String.format(" (점수: %.1f)", recommendationScore));
        }
        
        if (tag != null) {
            info.append(String.format(", 태그: %s", tag.getTagName()));
        }
        
        if (senderRating != null) {
            info.append(String.format(", 평점: %d/5", senderRating));
        }
        
        return info.toString();
    }
    
    /**
     * 발송자 평점 한글 표시
     */
    @Transient
    public String getSenderRatingKorean() {
        if (senderRating == null) return "평가 없음";
        
        return switch (senderRating) {
            case 1 -> "별로임";
            case 2 -> "그저그럼";
            case 3 -> "보통";
            case 4 -> "좋음";
            case 5 -> "매우 좋음";
            default -> "평가 없음";
        };
    }
    
    @Override
    public Long getId() {
        return messageRouteTagId;
    }
}
```

---

## ✅ 설계 완료 체크리스트

### 시스템 관리 엔티티 (3개)
- [x] **CommentLike** - 댓글 좋아요 (중복 방지, IP 추적, 자기 댓글 좋아요 방지)
- [x] **Message** - 메시지 (타입별 분류, 읽음 상태, 시스템 메시지 지원)
- [x] **MessageRouteTag** - 메시지 루트 태깅 (추천 점수, 매칭 분석, 상세 정보)

### 댓글 상호작용 시스템
- [x] 사용자-댓글 좋아요 중복 방지 (UNIQUE 인덱스)
- [x] 자기 댓글 좋아요 방지 검증
- [x] IP 주소 추적으로 어뷰징 방지
- [x] 좋아요 통계 집계 최적화

### 메시지 시스템
- [x] 다양한 메시지 타입 (일반, 루트공유, 초대, 팔로우요청, 시스템)
- [x] 읽음/안읽음 상태 관리 및 추적
- [x] 대화 상대방 자동 인식
- [x] 시스템 메시지 별도 처리

### 루트 공유 시스템
- [x] 메시지에 루트 정보 첨부
- [x] 추천 점수 및 등급 시스템 (강력추천/추천/참고)
- [x] 난이도/스타일 매칭 점수 분석
- [x] 발송자 평점 및 추천 이유 포함

### 성능 최적화
- [x] 댓글별 좋아요 조회 인덱스
- [x] 대화별 메시지 정렬 인덱스
- [x] 읽지 않은 메시지 검색 최적화
- [x] 루트 태깅 중복 방지 인덱스

### 사용자 경험
- [x] 메시지 발송 후 시간 표시
- [x] 메시지 미리보기 및 요약
- [x] 추천 등급 한글 표시
- [x] 상세한 루트 추천 정보

---

**다음 단계**: step4-4c2_system_logging_entities.md (시스템 로깅 엔티티)  
**완료일**: 2025-08-20  
**핵심 성과**: 3개 시스템 관리 엔티티 + 댓글 상호작용 + 메시지 시스템 + 루트 공유 완성