# 🔔 Step 4-4b2a: 개인 알림 엔티티 설계

> **RoutePickr 개인 알림** - FCM 푸시, 인앱 알림 완전 지원
> 
> **생성일**: 2025-08-20  
> **단계**: 4-4b2a (JPA 엔티티 50개 - 개인 알림 1개)  
> **분할**: step4-4b2_notification_entities.md에서 세분화
> **연관**: step4-4b2b_system_notification_entities.md

---

## 📋 파일 개요

이 파일은 **RoutePickr 개인 알림 시스템의 1개 엔티티**를 담고 있습니다.

### 🎯 주요 특징
- **개인 알림**: FCM 푸시, 인앱 알림 완전 지원
- **읽음 상태**: 실시간 읽음/미읽음 추적
- **푸시 통계**: 발송 성공/실패, 재시도 관리
- **클릭 추적**: 사용자 참여도 분석

### 📊 엔티티 목록 (1개)
1. **Notification** - 개인 알림

---

## 🔔 1. Notification 엔티티 - 개인 알림

```java
package com.routepick.domain.notification.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.NotificationType;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 개인 알림
 * - FCM 푸시, 인앱 알림 관리
 * - 읽음 상태 추적
 * - 다양한 알림 타입 지원
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user_read", columnList = "user_id, is_read, created_at DESC"),
    @Index(name = "idx_notification_type", columnList = "notification_type"),
    @Index(name = "idx_notification_important", columnList = "is_important, created_at DESC"),
    @Index(name = "idx_notification_sent", columnList = "is_push_sent"),
    @Index(name = "idx_notification_date", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notification extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    // ===== 알림 기본 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;
    
    @NotNull
    @Size(min = 1, max = 200, message = "알림 제목은 1-200자 사이여야 합니다")
    @Column(name = "title", nullable = false, length = 200)
    private String title; // 알림 제목
    
    @NotNull
    @Size(min = 1, max = 1000, message = "알림 내용은 1-1000자 사이여야 합니다")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content; // 알림 내용
    
    @Column(name = "sub_content", length = 500)
    private String subContent; // 부제목/요약
    
    @Column(name = "action_url", length = 500)
    private String actionUrl; // 클릭 시 이동할 URL
    
    @Column(name = "action_type", length = 30)
    private String actionType; // ROUTE, POST, USER, PAYMENT, EXTERNAL
    
    @Column(name = "action_data", length = 200)
    private String actionData; // 액션 관련 데이터 (ID 등)
    
    // ===== 알림 상태 =====
    
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false; // 읽음 여부
    
    @Column(name = "read_at")
    private LocalDateTime readAt; // 읽은 시간
    
    @Column(name = "is_important", nullable = false)
    private boolean isImportant = false; // 중요 알림
    
    @Column(name = "is_urgent", nullable = false)
    private boolean isUrgent = false; // 긴급 알림
    
    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false; // 시스템 알림
    
    // ===== 푸시 알림 정보 =====
    
    @Column(name = "is_push_sent", nullable = false)
    private boolean isPushSent = false; // 푸시 발송 여부
    
    @Column(name = "push_sent_at")
    private LocalDateTime pushSentAt; // 푸시 발송 시간
    
    @Column(name = "fcm_message_id", length = 200)
    private String fcmMessageId; // FCM 메시지 ID
    
    @Column(name = "push_success", nullable = false)
    private boolean pushSuccess = false; // 푸시 성공 여부
    
    @Column(name = "push_error_message", length = 500)
    private String pushErrorMessage; // 푸시 오류 메시지
    
    @Column(name = "push_retry_count")
    private Integer pushRetryCount = 0; // 푸시 재시도 횟수
    
    // ===== 발송자 정보 =====
    
    @Column(name = "sender_id")
    private Long senderId; // 발송자 ID (사용자 간 알림)
    
    @Column(name = "sender_type", length = 20)
    private String senderType = "SYSTEM"; // SYSTEM, USER, ADMIN
    
    @Column(name = "sender_name", length = 100)
    private String senderName; // 발송자명 (표시용)
    
    // ===== 이미지 및 아이콘 =====
    
    @Column(name = "icon_url", length = 500)
    private String iconUrl; // 알림 아이콘
    
    @Column(name = "image_url", length = 500)
    private String imageUrl; // 알림 이미지
    
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl; // 발송자 아바타
    
    // ===== 스케줄링 =====
    
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt; // 예약 발송 시간
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // 만료 시간
    
    @Column(name = "priority_level")
    private Integer priorityLevel = 3; // 우선순위 (1: 높음, 5: 낮음)
    
    // ===== 그룹 및 배치 =====
    
    @Column(name = "group_id", length = 100)
    private String groupId; // 그룹 알림 ID (같은 이벤트의 알림들)
    
    @Column(name = "batch_id", length = 100)
    private String batchId; // 배치 발송 ID
    
    @Column(name = "is_grouped", nullable = false)
    private boolean isGrouped = false; // 그룹 알림 여부
    
    // ===== 통계 정보 =====
    
    @Column(name = "click_count")
    private Integer clickCount = 0; // 클릭 횟수
    
    @Column(name = "first_clicked_at")
    private LocalDateTime firstClickedAt; // 첫 클릭 시간
    
    @Column(name = "last_clicked_at")
    private LocalDateTime lastClickedAt; // 마지막 클릭 시간
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 알림 타입 한글 표시
     */
    @Transient
    public String getNotificationTypeKorean() {
        return notificationType.getTitle();
    }
    
    /**
     * 발송자 타입 한글 표시
     */
    @Transient
    public String getSenderTypeKorean() {
        if (senderType == null) return "시스템";
        
        return switch (senderType) {
            case "SYSTEM" -> "시스템";
            case "USER" -> "사용자";
            case "ADMIN" -> "관리자";
            default -> senderType;
        };
    }
    
    /**
     * 우선순위 한글 표시
     */
    @Transient
    public String getPriorityLevelKorean() {
        if (priorityLevel == null) return "보통";
        
        return switch (priorityLevel) {
            case 1 -> "매우 높음";
            case 2 -> "높음";
            case 3 -> "보통";
            case 4 -> "낮음";
            case 5 -> "매우 낮음";
            default -> "보통";
        };
    }
    
    /**
     * 읽음 처리
     */
    public void markAsRead() {
        if (!isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }
    
    /**
     * 읽지 않음으로 표시
     */
    public void markAsUnread() {
        this.isRead = false;
        this.readAt = null;
    }
    
    /**
     * 클릭 기록
     */
    public void recordClick() {
        this.clickCount = (clickCount == null ? 0 : clickCount) + 1;
        this.lastClickedAt = LocalDateTime.now();
        
        if (firstClickedAt == null) {
            this.firstClickedAt = LocalDateTime.now();
        }
        
        // 클릭 시 자동으로 읽음 처리
        markAsRead();
    }
    
    /**
     * 푸시 발송 성공 처리
     */
    public void markPushSent(String fcmMessageId) {
        this.isPushSent = true;
        this.pushSuccess = true;
        this.pushSentAt = LocalDateTime.now();
        this.fcmMessageId = fcmMessageId;
    }
    
    /**
     * 푸시 발송 실패 처리
     */
    public void markPushFailed(String errorMessage) {
        this.isPushSent = true;
        this.pushSuccess = false;
        this.pushSentAt = LocalDateTime.now();
        this.pushErrorMessage = errorMessage;
        this.pushRetryCount = (pushRetryCount == null ? 0 : pushRetryCount) + 1;
    }
    
    /**
     * 만료 여부 확인
     */
    @Transient
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * 예약 알림 여부 확인
     */
    @Transient
    public boolean isScheduled() {
        return scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now());
    }
    
    /**
     * 발송 가능 여부 확인
     */
    @Transient
    public boolean canSend() {
        return !isExpired() && !isScheduled() && !isPushSent;
    }
    
    @Override
    public Long getId() {
        return notificationId;
    }
}
```

---

## 🎯 엔티티 설계 특징

### 🔔 Notification 엔티티 핵심 기능

#### 1. **알림 기본 관리**
- 제목, 내용, 부제목으로 풍부한 정보 제공
- 액션 URL/타입으로 클릭 시 동작 정의
- 중요도, 긴급도, 우선순위로 알림 분류

#### 2. **FCM 푸시 통합**
- FCM 메시지 ID 저장으로 발송 추적
- 푸시 성공/실패 상태 관리
- 재시도 횟수 카운터로 안정성 확보

#### 3. **읽음 상태 추적**
- 읽음/미읽음 상태 실시간 관리
- 읽은 시간 기록으로 사용자 패턴 분석
- 클릭 시 자동 읽음 처리

#### 4. **발송자 정보**
- 시스템, 사용자, 관리자 구분
- 발송자명으로 개인화된 알림
- 아바타 URL로 시각적 식별

#### 5. **스케줄링 기능**
- 예약 발송으로 적절한 타이밍 제공
- 만료 시간으로 불필요한 알림 방지
- 우선순위 레벨로 중요도 관리

#### 6. **그룹 및 배치**
- 그룹 ID로 연관 알림 묶기
- 배치 ID로 대량 발송 관리
- 그룹 알림 플래그로 UI 최적화

#### 7. **통계 및 분석**
- 클릭 횟수로 참여도 측정
- 첫 클릭/마지막 클릭 시간 추적
- 사용자 행동 패턴 분석 데이터

### 📊 인덱스 전략
- **사용자별 읽음 상태**: `(user_id, is_read, created_at DESC)`
- **알림 타입별**: `notification_type`
- **중요 알림**: `(is_important, created_at DESC)`
- **푸시 발송 상태**: `is_push_sent`
- **날짜순 정렬**: `created_at DESC`

### 🔒 보안 고려사항
- 사용자별 알림 격리 (user_id 필수)
- 액션 URL 검증 필요
- 푸시 토큰 보안 관리
- 스팸 방지를 위한 발송 제한

---

## 📈 성능 최적화

### 💾 인덱스 활용
- 사용자별 미읽음 알림 조회 최적화
- 알림 타입별 필터링 고속화
- 중요 알림 우선 표시

### 🚀 배치 처리
- 배치 ID 기반 대량 발송
- 그룹 알림 최적화
- 예약 발송 스케줄링

### 📱 모바일 최적화
- FCM 통합으로 실시간 푸시
- 오프라인 동기화 지원
- 배터리 최적화 고려

---

**📝 다음 단계**: step4-4b2b_system_notification_entities.md에서 시스템 알림 엔티티 (Notice, Banner, AppPopup) 설계