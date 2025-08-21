# Step 4-3b1: 루트 핵심 엔티티 설계

> **RoutePickr 루트 핵심 시스템** - Route, RouteSetter, RouteImage, RouteVideo 엔티티  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-3b1 (JPA 엔티티 50개 - 루트 핵심 4개)  
> **분할**: step4-3b_route_entities.md → 루트 핵심 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 핵심 클라이밍 루트 시스템 4개 엔티티**를 담고 있습니다.

### 🎯 설계 목표
- **클라이밍 전문성**: V등급/5.등급 시스템, 루트 세터 관리
- **미디어 최적화**: 이미지/동영상 관리, CDN 연동
- **검색 최적화**: 난이도별, 세터별, 상태별 복합 인덱스
- **통계 관리**: 완등률, 조회수, 평점 시스템

### 📊 엔티티 목록 (4개)
1. **Route** - 클라이밍 루트 기본 정보 (V등급/5.등급 시스템)
2. **RouteSetter** - 루트 세터 정보 (경력, 평가, 통계)
3. **RouteImage** - 루트 이미지 (타입별 분류, 썸네일 관리)
4. **RouteVideo** - 루트 동영상 (베타 영상, 재생 통계)

---

## 🧗‍♀️ 1. Route 엔티티 - 클라이밍 루트 기본 정보

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.RouteStatus;
import com.routepick.domain.climb.entity.ClimbingLevel;
import com.routepick.domain.gym.entity.Wall;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 클라이밍 루트 기본 정보
 * - V등급/5.등급 시스템 지원
 * - 세터, 난이도, 상태 관리
 * - 홀드 색상/테이프 정보
 */
@Entity
@Table(name = "routes", indexes = {
    @Index(name = "idx_route_wall_difficulty", columnList = "wall_id, difficulty, route_status"),
    @Index(name = "idx_route_setter", columnList = "route_setter_id"),
    @Index(name = "idx_route_level", columnList = "level_id"),
    @Index(name = "idx_route_status", columnList = "route_status"),
    @Index(name = "idx_route_grade_system", columnList = "grade_system, difficulty"),
    @Index(name = "idx_route_created", columnList = "created_at DESC"),
    @Index(name = "idx_route_popularity", columnList = "climb_count DESC, like_count DESC")
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wall_id", nullable = false)
    private Wall wall;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_setter_id", nullable = false)
    private RouteSetter routeSetter;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id", nullable = false)
    private ClimbingLevel climbingLevel;
    
    @NotNull
    @Size(min = 1, max = 100, message = "루트명은 1-100자 사이여야 합니다")
    @Column(name = "route_name", nullable = false, length = 100)
    private String routeName; // 루트명
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 루트 설명/팁
    
    // ===== 난이도 정보 =====
    
    @NotNull
    @Column(name = "grade_system", nullable = false, length = 20)
    private String gradeSystem; // V_SCALE, YDS, FRENCH
    
    @NotNull
    @Size(min = 1, max = 10, message = "난이도는 1-10자 사이여야 합니다")
    @Column(name = "difficulty", nullable = false, length = 10)
    private String difficulty; // V0, V1, 5.10a 등
    
    @DecimalMin(value = "0.0", message = "난이도 점수는 0.0 이상이어야 합니다")
    @DecimalMax(value = "20.0", message = "난이도 점수는 20.0 이하여야 합니다")
    @Column(name = "difficulty_score", precision = 4, scale = 2)
    private java.math.BigDecimal difficultyScore; // 숫자 변환 점수 (V0=0, V1=1, ...)
    
    @Column(name = "suggested_difficulty", length = 10)
    private String suggestedDifficulty; // 사용자 투표 기반 제안 난이도
    
    @Column(name = "difficulty_vote_count")
    private Integer difficultyVoteCount = 0; // 난이도 투표 수
    
    // ===== 홀드/테이프 정보 =====
    
    @Column(name = "hold_color", length = 30)
    private String holdColor; // 홀드 색상 (RED, BLUE, GREEN, YELLOW, BLACK, WHITE)
    
    @Column(name = "tape_color", length = 30)
    private String tapeColor; // 테이프 색상
    
    @Column(name = "start_hold_info", length = 200)
    private String startHoldInfo; // 시작 홀드 설명
    
    @Column(name = "finish_hold_info", length = 200)
    private String finishHoldInfo; // 피니시 홀드 설명
    
    @Column(name = "hold_count")
    private Integer holdCount; // 홀드 개수
    
    // ===== 루트 특성 =====
    
    @Column(name = "route_type", length = 30)
    private String routeType; // BOULDER, LEAD, TOP_ROPE
    
    @Column(name = "route_style", length = 50)
    private String routeStyle; // TECHNICAL, POWER, ENDURANCE, BALANCE
    
    @Column(name = "movement_style", length = 100)
    private String movementStyle; // DYNO, SLAB, OVERHANG, COORDINATION
    
    @Column(name = "key_moves", columnDefinition = "TEXT")
    private String keyMoves; // 핵심 동작 설명
    
    @Column(name = "beta_info", columnDefinition = "TEXT")
    private String betaInfo; // 베타(공략법) 정보
    
    // ===== 운영 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "route_status", nullable = false, length = 20)
    private RouteStatus routeStatus = RouteStatus.ACTIVE;
    
    @Column(name = "set_date")
    private LocalDate setDate; // 루트 설정일
    
    @Column(name = "expected_remove_date")
    private LocalDate expectedRemoveDate; // 예상 철거일
    
    @Column(name = "actual_remove_date")
    private LocalDate actualRemoveDate; // 실제 철거일
    
    @Column(name = "is_featured", nullable = false)
    private boolean isFeatured = false; // 추천 루트
    
    @Column(name = "is_competition_route", nullable = false)
    private boolean isCompetitionRoute = false; // 대회 루트
    
    // ===== 통계 정보 =====
    
    @Column(name = "climb_count")
    private Integer climbCount = 0; // 완등 횟수
    
    @Column(name = "attempt_count")
    private Integer attemptCount = 0; // 시도 횟수
    
    @Column(name = "like_count")
    private Integer likeCount = 0; // 좋아요 수
    
    @Column(name = "scrap_count")
    private Integer scrapCount = 0; // 스크랩 수
    
    @Column(name = "comment_count")
    private Integer commentCount = 0; // 댓글 수
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 조회 수
    
    @Column(name = "success_rate")
    private Float successRate = 0.0f; // 완등률 (%)
    
    @Column(name = "average_rating")
    private Float averageRating = 0.0f; // 평균 평점
    
    @Column(name = "rating_count")
    private Integer ratingCount = 0; // 평점 개수
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RouteImage> routeImages = new ArrayList<>();
    
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RouteVideo> routeVideos = new ArrayList<>();
    
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RouteComment> routeComments = new ArrayList<>();
    
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RouteDifficultyVote> difficultyVotes = new ArrayList<>();
    
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RouteScrap> routeScraps = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 루트 활성 상태 확인
     */
    public boolean isActive() {
        return routeStatus == RouteStatus.ACTIVE;
    }
    
    /**
     * 완등률 계산 및 업데이트
     */
    public void updateSuccessRate() {
        if (attemptCount == null || attemptCount == 0) {
            this.successRate = 0.0f;
            return;
        }
        this.successRate = ((float) climbCount / attemptCount) * 100;
    }
    
    /**
     * 완등 기록 추가
     */
    public void recordClimb() {
        this.climbCount = (climbCount == null ? 0 : climbCount) + 1;
        this.attemptCount = (attemptCount == null ? 0 : attemptCount) + 1;
        updateSuccessRate();
    }
    
    /**
     * 시도 기록 추가 (완등 실패)
     */
    public void recordAttempt() {
        this.attemptCount = (attemptCount == null ? 0 : attemptCount) + 1;
        updateSuccessRate();
    }
    
    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
    }
    
    /**
     * 난이도 투표 반영
     */
    public void updateSuggestedDifficulty(String newDifficulty) {
        this.suggestedDifficulty = newDifficulty;
        this.difficultyVoteCount = (difficultyVoteCount == null ? 0 : difficultyVoteCount) + 1;
    }
    
    /**
     * 루트 만료 처리
     */
    public void expireRoute() {
        this.routeStatus = RouteStatus.EXPIRED;
        this.actualRemoveDate = LocalDate.now();
    }
    
    /**
     * 루트 제거 처리
     */
    public void removeRoute() {
        this.routeStatus = RouteStatus.REMOVED;
        this.actualRemoveDate = LocalDate.now();
    }
    
    /**
     * 대표 이미지 URL 조회
     */
    @Transient
    public String getMainImageUrl() {
        return routeImages.stream()
                .filter(img -> img.getDisplayOrder() == 1)
                .findFirst()
                .map(RouteImage::getImageUrl)
                .orElse(null);
    }
    
    /**
     * 난이도 한글 표시
     */
    @Transient
    public String getDifficultyKorean() {
        if (difficulty == null) return "미설정";
        
        if (gradeSystem.equals("V_SCALE")) {
            return difficulty.replace("V", "V") + "급";
        } else if (gradeSystem.equals("YDS")) {
            return "5." + difficulty.substring(2);
        }
        return difficulty;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 루트 세터 정보
 * - 세터 프로필 및 경력 관리
 * - 세팅 통계 및 평가
 */
@Entity
@Table(name = "route_setters", indexes = {
    @Index(name = "idx_setter_name", columnList = "setter_name"),
    @Index(name = "idx_setter_level", columnList = "setter_level"),
    @Index(name = "idx_setter_active", columnList = "is_active"),
    @Index(name = "idx_setter_rating", columnList = "average_rating DESC"),
    @Index(name = "idx_setter_experience", columnList = "experience_years DESC")
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
    
    @NotNull
    @Size(min = 2, max = 50, message = "세터명은 2-50자 사이여야 합니다")
    @Column(name = "setter_name", nullable = false, length = 50)
    private String setterName; // 세터 이름
    
    @Column(name = "english_name", length = 50)
    private String englishName; // 영문 이름
    
    @Column(name = "nickname", length = 30)
    private String nickname; // 별명/닉네임
    
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl; // 프로필 이미지
    
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio; // 소개글
    
    // ===== 세터 등급 및 경력 =====
    
    @NotNull
    @Min(value = 1, message = "세터 레벨은 1 이상이어야 합니다")
    @Max(value = 10, message = "세터 레벨은 10 이하여야 합니다")
    @Column(name = "setter_level", nullable = false)
    private Integer setterLevel = 1; // 세터 등급 (1-10)
    
    @Column(name = "experience_years")
    private Integer experienceYears; // 경력 연수
    
    @Column(name = "certification", length = 100)
    private String certification; // 자격증/인증
    
    @Column(name = "specialty_style", length = 100)
    private String specialtyStyle; // 특기 스타일
    
    @Column(name = "specialty_difficulty", length = 50)
    private String specialtyDifficulty; // 특기 난이도대
    
    // ===== 개인 기록 =====
    
    @Column(name = "max_boulder_grade", length = 10)
    private String maxBoulderGrade; // 최고 볼더링 등급
    
    @Column(name = "max_lead_grade", length = 10)
    private String maxLeadGrade; // 최고 리드 등급
    
    @Column(name = "climbing_years")
    private Integer climbingYears; // 클라이밍 경력
    
    @Column(name = "start_setting_date")
    private LocalDate startSettingDate; // 세팅 시작일
    
    // ===== 연락처 및 소셜 =====
    
    @Column(name = "email", length = 100)
    private String email; // 이메일
    
    @Column(name = "instagram_url", length = 200)
    private String instagramUrl; // 인스타그램
    
    @Column(name = "youtube_url", length = 200)
    private String youtubeUrl; // 유튜브
    
    @Column(name = "website_url", length = 200)
    private String websiteUrl; // 개인 웹사이트
    
    // ===== 활동 정보 =====
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활동 중 여부
    
    @Column(name = "is_freelancer", nullable = false)
    private boolean isFreelancer = false; // 프리랜서 여부
    
    @Column(name = "main_gym_name", length = 100)
    private String mainGymName; // 주 활동 암장
    
    @Column(name = "available_locations", columnDefinition = "TEXT")
    private String availableLocations; // 활동 가능 지역
    
    // ===== 통계 정보 =====
    
    @Column(name = "total_routes_set")
    private Integer totalRoutesSet = 0; // 총 세팅 루트 수
    
    @Column(name = "monthly_routes_set")
    private Integer monthlyRoutesSet = 0; // 월간 세팅 루트 수
    
    @Column(name = "average_rating")
    private Float averageRating = 0.0f; // 평균 평점
    
    @Column(name = "rating_count")
    private Integer ratingCount = 0; // 평가 수
    
    @Column(name = "follower_count")
    private Integer followerCount = 0; // 팔로워 수
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 프로필 조회수
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "routeSetter", fetch = FetchType.LAZY)
    private List<Route> routes = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 세터 등급 한글 표시
     */
    @Transient
    public String getSetterLevelKorean() {
        if (setterLevel == null) return "미설정";
        
        return switch (setterLevel) {
            case 1, 2 -> "초급 세터";
            case 3, 4 -> "중급 세터";
            case 5, 6 -> "고급 세터";
            case 7, 8 -> "전문 세터";
            case 9, 10 -> "마스터 세터";
            default -> "세터";
        };
    }
    
    /**
     * 활동 기간 계산 (개월)
     */
    @Transient
    public long getActiveMonths() {
        if (startSettingDate == null) return 0;
        
        return java.time.temporal.ChronoUnit.MONTHS.between(
            startSettingDate, LocalDate.now()
        );
    }
    
    /**
     * 월평균 세팅 루트 수
     */
    @Transient
    public float getMonthlyAverageRoutes() {
        long activeMonths = getActiveMonths();
        if (activeMonths == 0 || totalRoutesSet == null) return 0.0f;
        
        return (float) totalRoutesSet / activeMonths;
    }
    
    /**
     * 세팅 루트 수 증가
     */
    public void incrementRouteCount() {
        this.totalRoutesSet = (totalRoutesSet == null ? 0 : totalRoutesSet) + 1;
        this.monthlyRoutesSet = (monthlyRoutesSet == null ? 0 : monthlyRoutesSet) + 1;
    }
    
    /**
     * 월간 루트 수 리셋
     */
    public void resetMonthlyRouteCount() {
        this.monthlyRoutesSet = 0;
    }
    
    /**
     * 평점 업데이트
     */
    public void updateRating(float newRating) {
        if (averageRating == null || ratingCount == null) {
            this.averageRating = newRating;
            this.ratingCount = 1;
            return;
        }
        
        float totalRating = averageRating * ratingCount + newRating;
        this.ratingCount = ratingCount + 1;
        this.averageRating = totalRating / ratingCount;
    }
    
    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
    }
    
    /**
     * 세터 비활성화
     */
    public void deactivate(String reason) {
        this.isActive = false;
        this.bio = (bio == null ? "" : bio + "\n") + 
                  "비활성화: " + LocalDate.now() + " - " + reason;
    }
    
    @Override
    public Long getId() {
        return setterId;
    }
}
```

---

## 🖼️ 3. RouteImage 엔티티 - 루트 이미지

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 루트 이미지 관리
 * - 루트별 여러 이미지 업로드
 * - 이미지 타입별 분류 (MAIN, PROBLEM, SOLUTION, HOLD)
 */
@Entity
@Table(name = "route_images", indexes = {
    @Index(name = "idx_route_image_order", columnList = "route_id, display_order"),
    @Index(name = "idx_route_image_type", columnList = "route_id, image_type"),
    @Index(name = "idx_route_image_active", columnList = "is_active"),
    @Index(name = "idx_route_image_uploader", columnList = "uploader_id")
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    private User uploader;
    
    @NotNull
    @Size(min = 10, max = 500, message = "이미지 URL은 10-500자 사이여야 합니다")
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl; // 이미지 URL
    
    @Column(name = "image_type", length = 30)
    private String imageType; // MAIN, PROBLEM, SOLUTION, HOLD, BETA
    
    @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다")
    @Max(value = 100, message = "표시 순서는 100 이하여야 합니다")
    @Column(name = "display_order")
    private Integer displayOrder = 1; // 표시 순서
    
    @Size(max = 200, message = "이미지 제목은 최대 200자입니다")
    @Column(name = "title", length = 200)
    private String title; // 이미지 제목
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 이미지 설명
    
    @Column(name = "alt_text", length = 200)
    private String altText; // 대체 텍스트
    
    @Column(name = "file_name", length = 200)
    private String fileName; // 원본 파일명
    
    @Column(name = "file_size")
    private Long fileSize; // 파일 크기 (bytes)
    
    @Column(name = "width")
    private Integer width; // 이미지 가로
    
    @Column(name = "height")
    private Integer height; // 이미지 세로
    
    @Column(name = "mime_type", length = 50)
    private String mimeType; // MIME 타입
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "is_main", nullable = false)
    private boolean isMain = false; // 대표 이미지
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 조회수
    
    @Column(name = "like_count")
    private Integer likeCount = 0; // 좋아요 수
    
    @Column(name = "upload_ip", length = 45)
    private String uploadIp; // 업로드 IP
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 이미지 타입 한글명
     */
    @Transient
    public String getImageTypeKorean() {
        if (imageType == null) return "기본";
        
        return switch (imageType) {
            case "MAIN" -> "대표 이미지";
            case "PROBLEM" -> "문제 이미지";
            case "SOLUTION" -> "해답 이미지";
            case "HOLD" -> "홀드 상세";
            case "BETA" -> "베타 설명";
            default -> "기타";
        };
    }
    
    /**
     * 대표 이미지로 설정
     */
    public void setAsMain() {
        this.isMain = true;
        this.displayOrder = 1;
        this.imageType = "MAIN";
    }
    
    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
    }
    
    /**
     * 좋아요 증가
     */
    public void increaseLikeCount() {
        this.likeCount = (likeCount == null ? 0 : likeCount) + 1;
    }
    
    @Override
    public Long getId() {
        return imageId;
    }
}
```

---

## 🎥 4. RouteVideo 엔티티 - 루트 동영상

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 루트 동영상 관리
 * - 완등 영상, 베타 영상, 실패 영상 등
 * - 썸네일 및 재생 통계 관리
 */
@Entity
@Table(name = "route_videos", indexes = {
    @Index(name = "idx_route_video", columnList = "route_id, created_at DESC"),
    @Index(name = "idx_route_video_type", columnList = "route_id, video_type"),
    @Index(name = "idx_route_video_uploader", columnList = "uploader_id"),
    @Index(name = "idx_route_video_active", columnList = "is_active"),
    @Index(name = "idx_route_video_popular", columnList = "view_count DESC, like_count DESC")
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    private User uploader;
    
    @NotNull
    @Size(min = 10, max = 500, message = "동영상 URL은 10-500자 사이여야 합니다")
    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl; // 동영상 URL
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl; // 썸네일 URL
    
    @Column(name = "video_type", length = 30)
    private String videoType; // SUCCESS, ATTEMPT, BETA, ANALYSIS, FAIL
    
    @Size(max = 200, message = "동영상 제목은 최대 200자입니다")
    @Column(name = "title", length = 200)
    private String title; // 동영상 제목
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 동영상 설명
    
    @Min(value = 1, message = "재생시간은 1초 이상이어야 합니다")
    @Max(value = 3600, message = "재생시간은 3600초 이하여야 합니다")
    @Column(name = "duration")
    private Integer duration; // 재생시간 (초)
    
    @Column(name = "file_name", length = 200)
    private String fileName; // 원본 파일명
    
    @Column(name = "file_size")
    private Long fileSize; // 파일 크기 (bytes)
    
    @Column(name = "video_width")
    private Integer videoWidth; // 동영상 가로
    
    @Column(name = "video_height")
    private Integer videoHeight; // 동영상 세로
    
    @Column(name = "video_format", length = 20)
    private String videoFormat; // MP4, WEBM, AVI
    
    @Column(name = "video_quality", length = 20)
    private String videoQuality; // HD, FHD, 4K
    
    @Column(name = "frame_rate")
    private Float frameRate; // 프레임 레이트
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "is_featured", nullable = false)
    private boolean isFeatured = false; // 추천 영상
    
    @Column(name = "is_success_video", nullable = false)
    private boolean isSuccessVideo = false; // 성공 영상
    
    // ===== 통계 정보 =====
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 조회수
    
    @Column(name = "like_count")
    private Integer likeCount = 0; // 좋아요 수
    
    @Column(name = "share_count")
    private Integer shareCount = 0; // 공유 수
    
    @Column(name = "comment_count")
    private Integer commentCount = 0; // 댓글 수
    
    @Column(name = "total_play_time")
    private Long totalPlayTime = 0L; // 총 재생시간 (초)
    
    @Column(name = "average_watch_time")
    private Float averageWatchTime = 0.0f; // 평균 시청시간 (초)
    
    @Column(name = "completion_rate")
    private Float completionRate = 0.0f; // 완주율 (%)
    
    @Column(name = "upload_ip", length = 45)
    private String uploadIp; // 업로드 IP
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 동영상 타입 한글명
     */
    @Transient
    public String getVideoTypeKorean() {
        if (videoType == null) return "기본";
        
        return switch (videoType) {
            case "SUCCESS" -> "성공 영상";
            case "ATTEMPT" -> "시도 영상";
            case "BETA" -> "베타 영상";
            case "ANALYSIS" -> "분석 영상";
            case "FAIL" -> "실패 영상";
            default -> "기타";
        };
    }
    
    /**
     * 재생시간 포맷 (mm:ss)
     */
    @Transient
    public String getFormattedDuration() {
        if (duration == null) return "00:00";
        
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    /**
     * 파일 크기 정보 (가독성)
     */
    @Transient
    public String getFileSizeInfo() {
        if (fileSize == null) return "알 수 없음";
        
        if (fileSize < 1024 * 1024) return (fileSize / 1024) + "KB";
        else if (fileSize < 1024 * 1024 * 1024) return (fileSize / (1024 * 1024)) + "MB";
        else return (fileSize / (1024 * 1024 * 1024)) + "GB";
    }
    
    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
    }
    
    /**
     * 재생 기록 추가
     */
    public void recordPlayTime(int watchedSeconds) {
        this.totalPlayTime = (totalPlayTime == null ? 0L : totalPlayTime) + watchedSeconds;
        
        // 평균 시청시간 업데이트
        if (viewCount > 0) {
            this.averageWatchTime = (float) totalPlayTime / viewCount;
        }
        
        // 완주율 계산
        if (duration != null && duration > 0) {
            this.completionRate = (averageWatchTime / duration) * 100;
            if (completionRate > 100) completionRate = 100.0f;
        }
    }
    
    /**
     * 좋아요 증가
     */
    public void increaseLikeCount() {
        this.likeCount = (likeCount == null ? 0 : likeCount) + 1;
    }
    
    /**
     * 공유 수 증가
     */
    public void increaseShareCount() {
        this.shareCount = (shareCount == null ? 0 : shareCount) + 1;
    }
    
    @Override
    public Long getId() {
        return videoId;
    }
}
```

---

## ✅ 설계 완료 체크리스트

### 루트 핵심 엔티티 (4개)
- [x] **Route** - 클라이밍 루트 기본 정보 (V등급/5.등급 시스템, 완등 통계)
- [x] **RouteSetter** - 루트 세터 정보 (경력 관리, 평가 시스템)
- [x] **RouteImage** - 루트 이미지 (타입별 분류, 대표 이미지 관리)
- [x] **RouteVideo** - 루트 동영상 (베타 영상, 재생 통계)

### 클라이밍 전문성
- [x] V등급(V0-V17) / 5.등급(5.0-5.15d) / 프랑스 등급 지원
- [x] 홀드 색상, 테이프 색상, 시작/피니시 홀드 정보
- [x] 루트 스타일 (TECHNICAL, POWER, ENDURANCE, BALANCE)
- [x] 동작 스타일 (DYNO, SLAB, OVERHANG, COORDINATION)

### 세터 관리 시스템
- [x] 세터 등급 시스템 (1-10급, 초급~마스터)
- [x] 경력 관리 (세팅 시작일, 경력 연수, 자격증)
- [x] 평점 및 팔로워 시스템
- [x] 월간/총 세팅 루트 수 통계

### 미디어 관리
- [x] 이미지 타입별 분류 (MAIN, PROBLEM, SOLUTION, HOLD, BETA)
- [x] 동영상 타입별 분류 (SUCCESS, ATTEMPT, BETA, ANALYSIS, FAIL)
- [x] 썸네일 및 메타데이터 관리
- [x] 재생 통계 (조회수, 완주율, 평균 시청시간)

### 성능 최적화
- [x] 복합 인덱스 (wall_id + difficulty + status)
- [x] 통계 정보 인덱스 (인기도, 평점순)
- [x] 미디어 검색 최적화
- [x] LAZY 로딩으로 성능 최적화

---

**다음 단계**: step4-3b2_route_interaction_entities.md (루트 상호작용 엔티티)  
**완료일**: 2025-08-20  
**핵심 성과**: 4개 루트 핵심 엔티티 + 클라이밍 전문성 + 미디어 관리 완성