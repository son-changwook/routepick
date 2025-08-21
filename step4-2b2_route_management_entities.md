# Step 4-2b2: 루트 관리 엔티티 설계

> **RoutePickr 루트 관리 시스템** - 클라이밍 루트, 세터, 미디어, 커뮤니티 관리  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-2b2 (JPA 엔티티 50개 - 루트 관리 7개)  
> **분할**: step4-2b_gym_route_entities.md → 루트 관리 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 루트 관리 시스템**을 담고 있습니다.

### 🎯 주요 특징
- **V등급/YDS등급 지원**: 클라이밍 표준 등급 시스템
- **인기도 알고리즘**: 성공률, 시도횟수, 댓글수 기반 계산
- **미디어 관리**: AWS S3 연동 이미지/비디오 시스템
- **커뮤니티 기능**: 계층형 댓글, 난이도 투표, 스크랩 시스템

### 📊 엔티티 목록 (7개)
1. **Route** - 클라이밍 루트 정보 (인기도 알고리즘, 등급 관리)
2. **RouteSetter** - 루트 세터 정보 (소셜미디어 연동)
3. **RouteImage** - 루트 이미지 (AWS S3 연동)
4. **RouteVideo** - 루트 비디오 (베타 영상 관리)
5. **RouteComment** - 루트 댓글 시스템 (계층형 구조)
6. **RouteDifficultyVote** - 난이도 투표 시스템 (1-10 점수)
7. **RouteScrap** - 루트 스크랩 기능 (북마크, 개인 메모)

---

## 🧗 1. Route 엔티티 - 클라이밍 루트 정보

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.GradeSystem;
import com.routepick.domain.gym.entity.Wall;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 클라이밍 루트 정보 엔티티
 * - V등급/5.등급 지원
 * - 태그 시스템과 연동
 */
@Entity
@Table(name = "routes", indexes = {
    @Index(name = "idx_routes_wall", columnList = "wall_id"),
    @Index(name = "idx_routes_level", columnList = "level_id"),
    @Index(name = "idx_routes_grade", columnList = "grade_system, grade_value"),
    @Index(name = "idx_routes_active", columnList = "is_active"),
    @Index(name = "idx_routes_popular", columnList = "popularity_score DESC"),
    @Index(name = "idx_routes_date", columnList = "set_date DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Route extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    private Long routeId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wall_id", nullable = false)
    private Wall wall;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private ClimbingLevel level;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setter_id")
    private RouteSetter setter;
    
    @NotBlank
    @Column(name = "route_name", nullable = false, length = 100)
    private String routeName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "grade_system", length = 10)
    private GradeSystem gradeSystem; // V_SCALE, YDS_SCALE
    
    @Column(name = "grade_value", length = 10)
    private String gradeValue; // V0, V1, 5.10a 등
    
    @Column(name = "color", length = 30)
    private String color; // 홀드 색상
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @NotNull
    @Column(name = "set_date", nullable = false)
    private LocalDate setDate;
    
    @Column(name = "removal_date")
    private LocalDate removalDate;
    
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "popularity_score")
    @ColumnDefault("0.0")
    private Double popularityScore = 0.0;
    
    @Column(name = "difficulty_votes")
    @ColumnDefault("0")
    private Integer difficultyVotes = 0;
    
    @Column(name = "average_difficulty")
    private Double averageDifficulty;
    
    @Column(name = "completion_count")
    @ColumnDefault("0")
    private Integer completionCount = 0;
    
    @Column(name = "attempt_count")
    @ColumnDefault("0")
    private Integer attemptCount = 0;
    
    // 연관관계
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RouteTag> routeTags = new ArrayList<>();
    
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RouteImage> images = new ArrayList<>();
    
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RouteVideo> videos = new ArrayList<>();
    
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RouteComment> comments = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 성공률 계산
     */
    @Transient
    public double getSuccessRate() {
        if (attemptCount == null || attemptCount == 0) return 0.0;
        if (completionCount == null) return 0.0;
        
        return (double) completionCount / attemptCount * 100.0;
    }
    
    /**
     * 인기도 업데이트
     */
    public void updatePopularity() {
        // 성공률, 시도 횟수, 댓글 수를 종합하여 인기도 계산
        double successRate = getSuccessRate();
        int totalAttempts = attemptCount != null ? attemptCount : 0;
        int commentCount = comments.size();
        
        // 가중 평균으로 인기도 계산
        this.popularityScore = (successRate * 0.4) + (Math.log(totalAttempts + 1) * 10 * 0.4) + (commentCount * 0.2);
    }
    
    /**
     * 완등 추가
     */
    public void addCompletion() {
        this.completionCount = (completionCount == null ? 0 : completionCount) + 1;
        this.attemptCount = (attemptCount == null ? 0 : attemptCount) + 1;
        updatePopularity();
    }
    
    /**
     * 시도 추가
     */
    public void addAttempt() {
        this.attemptCount = (attemptCount == null ? 0 : attemptCount) + 1;
        updatePopularity();
    }
    
    /**
     * 루트 제거
     */
    public void remove() {
        this.isActive = false;
        this.removalDate = LocalDate.now();
    }
    
    /**
     * 루트 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * 루트가 설정된 기간 계산 (일 단위)
     */
    @Transient
    public long getDaysSet() {
        LocalDate endDate = removalDate != null ? removalDate : LocalDate.now();
        return setDate.until(endDate).getDays();
    }
    
    @Override
    public Long getId() {
        return routeId;
    }
}
```

---

## 👨‍🎨 2. RouteSetter 엔티티 - 루트 세터 정보

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * 루트 세터 정보 엔티티
 * - 세터별 스타일 분석 가능
 */
@Entity
@Table(name = "route_setters", indexes = {
    @Index(name = "idx_setters_name", columnList = "setter_name"),
    @Index(name = "idx_setters_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteSetter extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setter_id")
    private Long setterId;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "setter_name", nullable = false, length = 100)
    private String setterName;
    
    @Size(max = 100)
    @Column(name = "english_name", length = 100)
    private String englishName;
    
    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;
    
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio; // 세터 소개
    
    @Column(name = "years_experience")
    private Integer yearsExperience; // 경력 년수
    
    @Column(name = "specialty_style", length = 100)
    private String specialtyStyle; // 전문 스타일
    
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;
    
    @Column(name = "instagram_handle", length = 50)
    private String instagramHandle;
    
    @Column(name = "youtube_channel", length = 100)
    private String youtubeChannel;
    
    // 연관관계
    @OneToMany(mappedBy = "setter", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Route> routes = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 활성 루트 수 조회
     */
    @Transient
    public int getActiveRouteCount() {
        return (int) routes.stream()
            .filter(Route::isActive)
            .count();
    }
    
    /**
     * 평균 루트 인기도 계산
     */
    @Transient
    public double getAverageRoutePopularity() {
        return routes.stream()
            .filter(Route::isActive)
            .mapToDouble(Route::getPopularityScore)
            .average()
            .orElse(0.0);
    }
    
    /**
     * 세터 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * 소셜 미디어 프로필 완성도 확인
     */
    @Transient
    public boolean hasSocialMedia() {
        return (instagramHandle != null && !instagramHandle.trim().isEmpty()) ||
               (youtubeChannel != null && !youtubeChannel.trim().isEmpty());
    }
    
    @Override
    public Long getId() {
        return setterId;
    }
}
```

---

## 📸 3. RouteImage 엔티티 - 루트 이미지

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * 루트 이미지 엔티티
 * - AWS S3 연동
 */
@Entity
@Table(name = "route_images", indexes = {
    @Index(name = "idx_route_images_route", columnList = "route_id"),
    @Index(name = "idx_route_images_order", columnList = "route_id, display_order")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteImage extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @NotBlank
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;
    
    @Column(name = "display_order")
    @ColumnDefault("0")
    private Integer displayOrder = 0;
    
    @Column(name = "caption", length = 500)
    private String caption;
    
    @Override
    public Long getId() {
        return imageId;
    }
}
```

---

## 📹 4. RouteVideo 엔티티 - 루트 비디오

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * 루트 비디오 엔티티
 * - AWS S3 연동
 * - 베타 영상 관리
 */
@Entity
@Table(name = "route_videos", indexes = {
    @Index(name = "idx_route_videos_route", columnList = "route_id"),
    @Index(name = "idx_route_videos_order", columnList = "route_id, display_order")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteVideo extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_id")
    private Long videoId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @NotBlank
    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;
    
    @Column(name = "duration")
    private Integer duration; // 초 단위
    
    @Column(name = "display_order")
    @ColumnDefault("0")
    private Integer displayOrder = 0;
    
    @Column(name = "caption", length = 500)
    private String caption;
    
    @Override
    public Long getId() {
        return videoId;
    }
}
```

---

## 💬 5. RouteComment 엔티티 - 루트 댓글 시스템

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * 루트 댓글 시스템 엔티티
 * - 대댓글 지원 (계층형 구조)
 * - 베타 정보 공유
 */
@Entity
@Table(name = "route_comments", indexes = {
    @Index(name = "idx_route_comments_route", columnList = "route_id"),
    @Index(name = "idx_route_comments_user", columnList = "user_id"),
    @Index(name = "idx_route_comments_parent", columnList = "parent_id"),
    @Index(name = "idx_route_comments_created", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteComment extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private RouteComment parent; // 대댓글을 위한 부모 댓글
    
    @NotBlank
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "is_beta")
    @ColumnDefault("false")
    private boolean isBeta = false; // 베타 정보 여부
    
    @Column(name = "like_count")
    @ColumnDefault("0")
    private Integer likeCount = 0;
    
    @Column(name = "is_deleted")
    @ColumnDefault("false")
    private boolean isDeleted = false;
    
    @Column(name = "is_reported")
    @ColumnDefault("false")
    private boolean isReported = false;
    
    // 연관관계
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RouteComment> replies = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 대댓글 추가
     */
    public void addReply(RouteComment reply) {
        replies.add(reply);
        reply.setParent(this);
        reply.setRoute(this.route);
    }
    
    /**
     * 최상위 댓글 여부
     */
    @Transient
    public boolean isTopLevel() {
        return parent == null;
    }
    
    /**
     * 댓글 삭제 (소프트 삭제)
     */
    public void delete() {
        this.isDeleted = true;
        this.content = "삭제된 댓글입니다.";
    }
    
    /**
     * 좋아요 증가
     */
    public void incrementLike() {
        this.likeCount = (likeCount == null ? 0 : likeCount) + 1;
    }
    
    /**
     * 좋아요 감소
     */
    public void decrementLike() {
        this.likeCount = Math.max(0, (likeCount == null ? 0 : likeCount) - 1);
    }
    
    /**
     * 신고 처리
     */
    public void report() {
        this.isReported = true;
    }
    
    /**
     * 대댓글 개수 조회
     */
    @Transient
    public int getReplyCount() {
        return replies.size();
    }
    
    @Override
    public Long getId() {
        return commentId;
    }
}
```

---

## 🗳️ 6. RouteDifficultyVote 엔티티 - 난이도 투표 시스템

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 루트 난이도 투표 시스템
 * - 사용자별 체감 난이도 수집
 * - 평균 난이도 계산
 */
@Entity
@Table(name = "route_difficulty_votes", indexes = {
    @Index(name = "idx_difficulty_votes_route", columnList = "route_id"),
    @Index(name = "idx_difficulty_votes_user", columnList = "user_id"),
    @Index(name = "uk_user_route_vote", columnList = "user_id, route_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteDifficultyVote extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id")
    private Long voteId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @Min(value = 1, message = "난이도는 1 이상이어야 합니다")
    @Max(value = 10, message = "난이도는 10 이하여야 합니다")
    @Column(name = "difficulty_score", nullable = false)
    private Integer difficultyScore; // 1-10 점수
    
    @Column(name = "comment", length = 500)
    private String comment; // 난이도에 대한 의견
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 투표 업데이트
     */
    public void updateVote(Integer newScore, String newComment) {
        this.difficultyScore = newScore;
        this.comment = newComment;
    }
    
    @Override
    public Long getId() {
        return voteId;
    }
}
```

---

## 📌 7. RouteScrap 엔티티 - 루트 스크랩 기능

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 루트 스크랩 기능 엔티티
 * - 사용자별 관심 루트 저장
 * - 북마크 기능
 */
@Entity
@Table(name = "route_scraps", indexes = {
    @Index(name = "idx_route_scraps_user", columnList = "user_id"),
    @Index(name = "idx_route_scraps_route", columnList = "route_id"),
    @Index(name = "uk_user_route_scrap", columnList = "user_id, route_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteScrap extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long scrapId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes; // 개인적인 메모
    
    @Override
    public Long getId() {
        return scrapId;
    }
}
```

---

## ✅ 설계 완료 체크리스트

### 루트 관리 엔티티 (7개)
- [x] **Route** - 클라이밍 루트 정보 (V/YDS등급, 인기도 알고리즘, 성공률 계산)
- [x] **RouteSetter** - 루트 세터 정보 (소셜미디어 연동, 활성 루트 수 추적)
- [x] **RouteImage** - 루트 이미지 (AWS S3, 썸네일, 표시순서)
- [x] **RouteVideo** - 루트 비디오 (베타 영상, 지속시간, 썸네일)
- [x] **RouteComment** - 루트 댓글 시스템 (계층형 구조, 베타정보, 좋아요)
- [x] **RouteDifficultyVote** - 난이도 투표 시스템 (1-10점수, 의견, 중복방지)
- [x] **RouteScrap** - 루트 스크랩 기능 (북마크, 개인메모, 중복방지)

### 등급 시스템
- [x] V등급 시스템 (V0, V1, V2 등 볼더링 표준)
- [x] YDS 등급 시스템 (5.10a, 5.11c 등 스포츠 클라이밍 표준)
- [x] 등급별 복합 인덱스로 검색 최적화
- [x] 사용자 투표 기반 체감 난이도 수집

### 인기도 알고리즘
- [x] 성공률 40% 가중치 (완등수/시도수 * 100)
- [x] 시도횟수 40% 가중치 (로그 스케일 적용)
- [x] 댓글수 20% 가중치
- [x] 실시간 인기도 업데이트 로직

### 미디어 관리 시스템
- [x] AWS S3 연동 이미지/비디오 URL 관리
- [x] 썸네일 URL 분리로 로딩 성능 최적화
- [x] 표시 순서(display_order) 관리
- [x] 캡션 및 메타데이터 지원

### 커뮤니티 기능
- [x] 계층형 댓글 시스템 (parent_id 기반 대댓글)
- [x] 베타 정보 플래그로 정보성 댓글 구분
- [x] 좋아요 시스템 (증가/감소 로직)
- [x] 소프트 삭제 및 신고 기능

### 사용자 상호작용
- [x] 난이도 투표 (1-10점 + 의견, 사용자당 1회)
- [x] 루트 스크랩 (북마크 + 개인 메모)
- [x] 중복 방지 UNIQUE 제약 (사용자-루트 조합)
- [x] 개인화된 루트 관리 기능

### 성능 최적화
- [x] 루트별 인기도 정렬 인덱스 (popularity_score DESC)
- [x] 벽면별 루트 조회 인덱스
- [x] 설정일자 정렬 인덱스 (set_date DESC)
- [x] 댓글 생성일 정렬 인덱스 (created_at DESC)

### 세터 관리
- [x] 세터 프로필 (경력, 전문스타일, 소개)
- [x] 소셜미디어 연동 (Instagram, YouTube)
- [x] 세터별 평균 루트 인기도 계산
- [x] 활성 루트 수 자동 집계

---

**다음 단계**: step4-4b2_notification_entities.md 세분화 진행  
**완료일**: 2025-08-20  
**핵심 성과**: 7개 루트 관리 엔티티 + 인기도 알고리즘 + AWS S3 연동 + 커뮤니티 기능 완성