# 🔔 Step 4-4b2b1: 공지사항 & 배너 엔티티 설계

> **RoutePickr 공지/배너 관리** - 공지사항, 배너 표시 시스템
> 
> **생성일**: 2025-08-20  
> **단계**: 4-4b2b1 (JPA 엔티티 50개 - 공지/배너 2개)  
> **분할**: step4-4b2b_system_notification_entities.md에서 세분화
> **연관**: step4-4b2b2_app_popup_entities.md

---

## 📋 파일 개요

이 파일은 **RoutePickr 공지사항 및 배너 관리의 2개 엔티티**를 담고 있습니다.

### 🎯 주요 특징
- **공지사항**: 전체 사용자 대상, 중요도별 분류, 게시 기간 관리
- **배너 관리**: 메인 화면 배너, 클릭 통계 추적, 반응형 지원

### 📊 엔티티 목록 (2개)
1. **Notice** - 공지사항  
2. **Banner** - 배너 관리

---

## 📢 1. Notice 엔티티 - 공지사항

```java
package com.routepick.domain.notification.entity;

import com.routepick.common.entity.SoftDeleteEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 공지사항
 * - 전체 사용자 대상 공지
 * - 중요도별 분류
 * - 게시 기간 관리
 */
@Entity
@Table(name = "notices", indexes = {
    @Index(name = "idx_notice_date", columnList = "is_important, start_date DESC"),
    @Index(name = "idx_notice_active", columnList = "is_active, end_date DESC"),
    @Index(name = "idx_notice_type", columnList = "notice_type"),
    @Index(name = "idx_notice_author", columnList = "author_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notice extends SoftDeleteEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author; // 작성자 (관리자)
    
    // ===== 공지 기본 정보 =====
    
    @NotNull
    @Size(min = 2, max = 200, message = "공지 제목은 2-200자 사이여야 합니다")
    @Column(name = "title", nullable = false, length = 200)
    private String title; // 공지 제목
    
    @NotNull
    @Size(min = 10, message = "공지 내용은 최소 10자 이상이어야 합니다")
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content; // 공지 내용
    
    @Column(name = "summary", length = 500)
    private String summary; // 요약
    
    @Column(name = "notice_type", length = 30)
    private String noticeType = "GENERAL"; // GENERAL, MAINTENANCE, EVENT, UPDATE, URGENT
    
    // ===== 중요도 및 표시 =====
    
    @Column(name = "is_important", nullable = false)
    private boolean isImportant = false; // 중요 공지
    
    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false; // 상단 고정
    
    @Column(name = "is_popup", nullable = false)
    private boolean isPopup = false; // 팝업 표시
    
    @Column(name = "is_push", nullable = false)
    private boolean isPush = false; // 푸시 알림 발송
    
    @Column(name = "importance_level")
    private Integer importanceLevel = 3; // 중요도 (1: 매우 높음, 5: 낮음)
    
    // ===== 게시 기간 =====
    
    @Column(name = "start_date")
    private LocalDateTime startDate; // 게시 시작일
    
    @Column(name = "end_date")
    private LocalDateTime endDate; // 게시 종료일
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt; // 게시일
    
    // ===== 대상 설정 =====
    
    @Column(name = "target_user_type", length = 20)
    private String targetUserType = "ALL"; // ALL, NORMAL, ADMIN, GYM_ADMIN
    
    @Column(name = "target_app_version", length = 20)
    private String targetAppVersion; // 특정 앱 버전 대상
    
    @Column(name = "target_platform", length = 20)
    private String targetPlatform; // ANDROID, IOS, WEB, ALL
    
    @Column(name = "target_region", length = 100)
    private String targetRegion; // 특정 지역 대상
    
    // ===== 이미지 및 첨부 파일 =====
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl; // 썸네일 이미지
    
    @Column(name = "banner_image_url", length = 500)
    private String bannerImageUrl; // 배너 이미지
    
    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl; // 첨부 파일
    
    @Column(name = "attachment_name", length = 200)
    private String attachmentName; // 첨부 파일명
    
    // ===== 링크 정보 =====
    
    @Column(name = "link_url", length = 500)
    private String linkUrl; // 관련 링크
    
    @Column(name = "link_text", length = 100)
    private String linkText; // 링크 텍스트
    
    @Column(name = "external_link", nullable = false)
    private boolean externalLink = false; // 외부 링크 여부
    
    // ===== 통계 정보 =====
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 조회 수
    
    @Column(name = "click_count")
    private Integer clickCount = 0; // 클릭 수
    
    @Column(name = "push_sent_count")
    private Integer pushSentCount = 0; // 푸시 발송 수
    
    @Column(name = "push_success_count")
    private Integer pushSuccessCount = 0; // 푸시 성공 수
    
    // ===== 관리 정보 =====
    
    @Column(name = "approved_by")
    private Long approvedBy; // 승인자 ID
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 승인일
    
    @Column(name = "admin_memo", columnDefinition = "TEXT")
    private String adminMemo; // 관리자 메모
    
    @Column(name = "tags", length = 500)
    private String tags; // 태그 (쉼표 구분)
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 공지 타입 한글 표시
     */
    @Transient
    public String getNoticeTypeKorean() {
        if (noticeType == null) return "일반";
        
        return switch (noticeType) {
            case "GENERAL" -> "일반";
            case "MAINTENANCE" -> "점검";
            case "EVENT" -> "이벤트";
            case "UPDATE" -> "업데이트";
            case "URGENT" -> "긴급";
            default -> noticeType;
        };
    }
    
    /**
     * 중요도 한글 표시
     */
    @Transient
    public String getImportanceLevelKorean() {
        if (importanceLevel == null) return "보통";
        
        return switch (importanceLevel) {
            case 1 -> "매우 높음";
            case 2 -> "높음";
            case 3 -> "보통";
            case 4 -> "낮음";
            case 5 -> "매우 낮음";
            default -> "보통";
        };
    }
    
    /**
     * 대상 사용자 타입 한글 표시
     */
    @Transient
    public String getTargetUserTypeKorean() {
        if (targetUserType == null) return "전체";
        
        return switch (targetUserType) {
            case "ALL" -> "전체 사용자";
            case "NORMAL" -> "일반 사용자";
            case "ADMIN" -> "관리자";
            case "GYM_ADMIN" -> "암장 관리자";
            default -> targetUserType;
        };
    }
    
    /**
     * 현재 게시 중인지 확인
     */
    @Transient
    public boolean isCurrentlyActive() {
        if (!isActive) return false;
        
        LocalDateTime now = LocalDateTime.now();
        
        boolean afterStart = startDate == null || !startDate.isAfter(now);
        boolean beforeEnd = endDate == null || !endDate.isBefore(now);
        
        return afterStart && beforeEnd;
    }
    
    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
    }
    
    /**
     * 클릭 수 증가
     */
    public void increaseClickCount() {
        this.clickCount = (clickCount == null ? 0 : clickCount) + 1;
    }
    
    /**
     * 게시 승인
     */
    public void approve(Long adminId) {
        this.approvedBy = adminId;
        this.approvedAt = LocalDateTime.now();
        this.isActive = true;
        
        if (publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 게시 중단
     */
    public void deactivate() {
        this.isActive = false;
        this.endDate = LocalDateTime.now();
    }
    
    /**
     * 고정/해제
     */
    public void pin() {
        this.isPinned = true;
    }
    
    public void unpin() {
        this.isPinned = false;
    }
    
    /**
     * 푸시 발송 통계 업데이트
     */
    public void updatePushStats(int sentCount, int successCount) {
        this.pushSentCount = sentCount;
        this.pushSuccessCount = successCount;
    }
    
    /**
     * 태그 목록 반환
     */
    @Transient
    public java.util.List<String> getTagList() {
        if (tags == null || tags.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return java.util.Arrays.asList(tags.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public Long getId() {
        return noticeId;
    }
}
```

---

## 🎨 2. Banner 엔티티 - 배너 관리

```java
package com.routepick.domain.notification.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 배너 관리
 * - 앱 메인 화면 배너
 * - 표시 순서 및 기간 관리
 * - 클릭 통계 추적
 */
@Entity
@Table(name = "banners", indexes = {
    @Index(name = "idx_banner_active_order", columnList = "is_active, display_order, start_date DESC"),
    @Index(name = "idx_banner_position", columnList = "banner_position"),
    @Index(name = "idx_banner_date", columnList = "start_date DESC, end_date DESC"),
    @Index(name = "idx_banner_type", columnList = "banner_type")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Banner extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "banner_id")
    private Long bannerId;
    
    // ===== 배너 기본 정보 =====
    
    @NotNull
    @Size(min = 2, max = 200, message = "배너 제목은 2-200자 사이여야 합니다")
    @Column(name = "banner_title", nullable = false, length = 200)
    private String bannerTitle; // 배너 제목
    
    @Column(name = "banner_subtitle", length = 300)
    private String bannerSubtitle; // 부제목
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 배너 설명
    
    @Column(name = "banner_type", length = 30)
    private String bannerType = "PROMOTION"; // PROMOTION, EVENT, NOTICE, AD, FEATURE
    
    // ===== 이미지 정보 =====
    
    @NotNull
    @Size(min = 10, max = 500, message = "배너 이미지 URL은 10-500자 사이여야 합니다")
    @Column(name = "banner_image_url", nullable = false, length = 500)
    private String bannerImageUrl; // 배너 이미지
    
    @Column(name = "mobile_image_url", length = 500)
    private String mobileImageUrl; // 모바일용 이미지
    
    @Column(name = "tablet_image_url", length = 500)
    private String tabletImageUrl; // 태블릿용 이미지
    
    @Column(name = "background_color", length = 7)
    private String backgroundColor; // 배경색 (#FFFFFF)
    
    @Column(name = "text_color", length = 7)
    private String textColor; // 텍스트 색상
    
    // ===== 링크 정보 =====
    
    @Column(name = "link_url", length = 500)
    private String linkUrl; // 클릭 시 이동할 URL
    
    @Column(name = "link_type", length = 30)
    private String linkType = "INTERNAL"; // INTERNAL, EXTERNAL, DEEPLINK
    
    @Column(name = "link_target", length = 30)
    private String linkTarget = "_self"; // _self, _blank, _parent
    
    @Column(name = "action_type", length = 30)
    private String actionType; // ROUTE, POST, USER, PRODUCT, EXTERNAL
    
    @Column(name = "action_data", length = 200)
    private String actionData; // 액션 관련 데이터
    
    // ===== 표시 설정 =====
    
    @NotNull
    @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다")
    @Max(value = 999, message = "표시 순서는 999 이하여야 합니다")
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1; // 표시 순서
    
    @Column(name = "banner_position", length = 30)
    private String bannerPosition = "MAIN_TOP"; // MAIN_TOP, MAIN_MIDDLE, MAIN_BOTTOM, DETAIL_TOP
    
    @Column(name = "banner_size", length = 20)
    private String bannerSize = "LARGE"; // SMALL, MEDIUM, LARGE, FULL
    
    @Column(name = "auto_play", nullable = false)
    private boolean autoPlay = true; // 자동 재생 (슬라이드)
    
    @Column(name = "play_duration")
    private Integer playDuration = 5; // 재생 시간 (초)
    
    // ===== 게시 기간 =====
    
    @Column(name = "start_date")
    private LocalDateTime startDate; // 게시 시작일
    
    @Column(name = "end_date")
    private LocalDateTime endDate; // 게시 종료일
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "is_always_show", nullable = false)
    private boolean isAlwaysShow = false; // 항상 표시
    
    // ===== 대상 설정 =====
    
    @Column(name = "target_user_type", length = 20)
    private String targetUserType = "ALL"; // ALL, NORMAL, ADMIN, GYM_ADMIN
    
    @Column(name = "target_platform", length = 20)
    private String targetPlatform = "ALL"; // ANDROID, IOS, WEB, ALL
    
    @Column(name = "target_app_version", length = 20)
    private String targetAppVersion; // 특정 앱 버전
    
    @Column(name = "target_region", length = 100)
    private String targetRegion; // 특정 지역
    
    // ===== 통계 정보 =====
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 노출 수
    
    @Column(name = "click_count")
    private Integer clickCount = 0; // 클릭 수
    
    @Column(name = "ctr")
    private Float ctr = 0.0f; // 클릭률 (Click Through Rate)
    
    @Column(name = "conversion_count")
    private Integer conversionCount = 0; // 전환 수
    
    @Column(name = "first_shown_at")
    private LocalDateTime firstShownAt; // 첫 노출일
    
    @Column(name = "last_clicked_at")
    private LocalDateTime lastClickedAt; // 마지막 클릭일
    
    // ===== 관리 정보 =====
    
    @Column(name = "created_by")
    private Long createdBy; // 생성자 ID
    
    @Column(name = "approved_by")
    private Long approvedBy; // 승인자 ID
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 승인일
    
    @Column(name = "admin_memo", columnDefinition = "TEXT")
    private String adminMemo; // 관리자 메모
    
    @Column(name = "priority_score")
    private Integer priorityScore = 0; // 우선순위 점수
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 배너 타입 한글 표시
     */
    @Transient
    public String getBannerTypeKorean() {
        if (bannerType == null) return "일반";
        
        return switch (bannerType) {
            case "PROMOTION" -> "프로모션";
            case "EVENT" -> "이벤트";
            case "NOTICE" -> "공지";
            case "AD" -> "광고";
            case "FEATURE" -> "기능 소개";
            default -> bannerType;
        };
    }
    
    /**
     * 배너 위치 한글 표시
     */
    @Transient
    public String getBannerPositionKorean() {
        if (bannerPosition == null) return "메인 상단";
        
        return switch (bannerPosition) {
            case "MAIN_TOP" -> "메인 상단";
            case "MAIN_MIDDLE" -> "메인 중간";
            case "MAIN_BOTTOM" -> "메인 하단";
            case "DETAIL_TOP" -> "상세 페이지 상단";
            case "CATEGORY_TOP" -> "카테고리 상단";
            default -> bannerPosition;
        };
    }
    
    /**
     * 현재 표시 중인지 확인
     */
    @Transient
    public boolean isCurrentlyVisible() {
        if (!isActive) return false;
        if (isAlwaysShow) return true;
        
        LocalDateTime now = LocalDateTime.now();
        
        boolean afterStart = startDate == null || !startDate.isAfter(now);
        boolean beforeEnd = endDate == null || !endDate.isBefore(now);
        
        return afterStart && beforeEnd;
    }
    
    /**
     * 노출 수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
        
        if (firstShownAt == null) {
            this.firstShownAt = LocalDateTime.now();
        }
        
        updateCtr();
    }
    
    /**
     * 클릭 수 증가
     */
    public void increaseClickCount() {
        this.clickCount = (clickCount == null ? 0 : clickCount) + 1;
        this.lastClickedAt = LocalDateTime.now();
        
        updateCtr();
    }
    
    /**
     * CTR 업데이트
     */
    private void updateCtr() {
        if (viewCount != null && viewCount > 0) {
            this.ctr = ((float) (clickCount == null ? 0 : clickCount) / viewCount) * 100;
        }
    }
    
    /**
     * 전환 수 증가
     */
    public void increaseConversionCount() {
        this.conversionCount = (conversionCount == null ? 0 : conversionCount) + 1;
    }
    
    /**
     * 배너 승인
     */
    public void approve(Long adminId) {
        this.approvedBy = adminId;
        this.approvedAt = LocalDateTime.now();
        this.isActive = true;
    }
    
    /**
     * 배너 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        this.endDate = LocalDateTime.now();
    }
    
    /**
     * 우선순위 점수 계산
     */
    public void calculatePriorityScore() {
        int score = 0;
        
        // 표시 순서 기반 점수 (낮을수록 높은 점수)
        score += Math.max(0, 100 - displayOrder);
        
        // CTR 기반 점수
        if (ctr != null) {
            score += (int) (ctr * 10);
        }
        
        // 최신성 점수 (최근 생성일수록 높은 점수)
        if (getCreatedAt() != null) {
            long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(
                getCreatedAt().toLocalDate(), LocalDateTime.now().toLocalDate());
            score += Math.max(0, 30 - (int) daysAgo);
        }
        
        this.priorityScore = score;
    }
    
    @Override
    public Long getId() {
        return bannerId;
    }
}
```

---

## 🎯 엔티티 설계 특징

### 📢 Notice 엔티티 핵심 기능

#### 1. **공지사항 분류 체계**
- 공지 타입: 일반, 점검, 이벤트, 업데이트, 긴급
- 중요도 레벨: 1(매우 높음) ~ 5(매우 낮음)
- 표시 옵션: 상단 고정, 팝업, 푸시 알림

#### 2. **타겟팅 시스템**
- 사용자 타입: 전체, 일반, 관리자, 암장 관리자
- 플랫폼: Android, iOS, Web, 전체
- 앱 버전 및 지역별 세분화

#### 3. **게시 관리**
- 시작일/종료일: 자동 게시/숨김
- 승인 프로세스: 관리자 검토 후 게시
- 소프트 삭제: 데이터 보존하며 숨김

### 🎨 Banner 엔티티 핵심 기능

#### 1. **반응형 배너**
- 다중 이미지: 데스크톱, 모바일, 태블릿
- 위치 관리: 메인 상단/중간/하단, 상세 페이지
- 크기 옵션: Small, Medium, Large, Full

#### 2. **자동화 시스템**
- 자동 재생: 슬라이드 배너 지원
- 재생 시간: 커스텀 설정 가능
- 항상 표시: 기간 제한 무시 옵션

#### 3. **성과 분석**
- CTR 자동 계산: 클릭률 실시간 업데이트
- 우선순위 점수: 표시순서 + CTR + 최신성
- 전환 추적: 목표 달성 측정

### 📊 인덱스 전략
- **Notice**: `(is_important, start_date DESC)`, `(is_active, end_date DESC)`
- **Banner**: `(is_active, display_order, start_date DESC)`, `banner_position`

### 🔒 보안 고려사항
- 승인 프로세스: 무단 게시 방지
- 외부 링크 검증: XSS 방지
- 관리자 메모: 내부 관리 용도

---

## 📈 성능 최적화

### 💾 캐싱 전략
- 활성 공지사항: Redis 캐싱 (30분)
- 배너 리스트: 메모리 캐싱 (10분)
- 우선순위 점수: 배치 업데이트 (1시간)

### 🚀 CDN 최적화
- 배너 이미지: CloudFront 캐싱
- 반응형 변환: 실시간 리사이징
- 압축: WebP 자동 변환

### 📱 모바일 최적화
- 이미지 Lazy Loading: 스크롤 시 로드
- 배너 프리로드: 다음 배너 미리 로드
- 오프라인 캐싱: PWA 지원

---

**📝 다음 단계**: step4-4b2b2_app_popup_entities.md에서 앱 팝업 엔티티 설계