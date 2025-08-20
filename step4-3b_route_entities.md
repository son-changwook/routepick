# Step 4-3b: 루트 관련 엔티티 설계

> 클라이밍 루트, 세터, 미디어, 댓글, 투표, 스크랩 엔티티 완전 설계  
> 생성일: 2025-08-19  
> 기반: step4-3a_gym_entities.md, step4-1_base_user_entities.md

---

## 🎯 설계 목표

- **클라이밍 전문성**: V등급/5.등급 시스템, 루트 세터 관리
- **미디어 최적화**: 이미지/동영상 관리, CDN 연동
- **사용자 참여**: 계층형 댓글, 난이도 투표, 스크랩
- **검색 최적화**: 난이도별, 세터별, 상태별 복합 인덱스

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

## 💬 5. RouteComment 엔티티 - 루트 댓글 (계층형)

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.SoftDeleteEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 루트 댓글 관리 (계층형 구조)
 * - 부모-자식 관계로 대댓글 지원
 * - 소프트 삭제 적용
 */
@Entity
@Table(name = "route_comments", indexes = {
    @Index(name = "idx_comment_route_parent", columnList = "route_id, parent_id, created_at DESC"),
    @Index(name = "idx_comment_user", columnList = "user_id"),
    @Index(name = "idx_comment_parent", columnList = "parent_id"),
    @Index(name = "idx_comment_active", columnList = "is_deleted, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteComment extends SoftDeleteEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private RouteComment parent; // 부모 댓글 (대댓글인 경우)
    
    @NotNull
    @Size(min = 1, max = 1000, message = "댓글은 1-1000자 사이여야 합니다")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content; // 댓글 내용
    
    @Column(name = "comment_type", length = 30)
    private String commentType; // NORMAL, BETA, TIP, QUESTION, COMPLIMENT
    
    @Column(name = "is_spoiler", nullable = false)
    private boolean isSpoiler = false; // 스포일러 여부 (베타 공개)
    
    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous = false; // 익명 댓글 여부
    
    @Column(name = "like_count")
    private Integer likeCount = 0; // 좋아요 수
    
    @Column(name = "reply_count")
    private Integer replyCount = 0; // 답글 수
    
    @Column(name = "report_count")
    private Integer reportCount = 0; // 신고 수
    
    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false; // 고정 댓글
    
    @Column(name = "is_author_comment", nullable = false)
    private boolean isAuthorComment = false; // 세터 댓글
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // 작성자 IP
    
    @Column(name = "user_agent", length = 500)
    private String userAgent; // User Agent
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RouteComment> children = new ArrayList<>(); // 자식 댓글들
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 최상위 댓글인지 확인
     */
    @Transient
    public boolean isRootComment() {
        return parent == null;
    }
    
    /**
     * 댓글 깊이 계산
     */
    @Transient
    public int getDepth() {
        if (parent == null) return 0;
        return parent.getDepth() + 1;
    }
    
    /**
     * 댓글 타입 한글명
     */
    @Transient
    public String getCommentTypeKorean() {
        if (commentType == null) return "일반";
        
        return switch (commentType) {
            case "NORMAL" -> "일반";
            case "BETA" -> "베타";
            case "TIP" -> "팁";
            case "QUESTION" -> "질문";
            case "COMPLIMENT" -> "칭찬";
            default -> "기타";
        };
    }
    
    /**
     * 표시용 작성자명
     */
    @Transient
    public String getDisplayAuthorName() {
        if (isAnonymous) return "익명";
        return user.getNickName();
    }
    
    /**
     * 좋아요 수 증가
     */
    public void increaseLikeCount() {
        this.likeCount = (likeCount == null ? 0 : likeCount) + 1;
    }
    
    /**
     * 답글 수 증가
     */
    public void increaseReplyCount() {
        this.replyCount = (replyCount == null ? 0 : replyCount) + 1;
        
        // 부모 댓글의 답글 수도 증가
        if (parent != null) {
            parent.increaseReplyCount();
        }
    }
    
    /**
     * 신고 수 증가
     */
    public void increaseReportCount() {
        this.reportCount = (reportCount == null ? 0 : reportCount) + 1;
    }
    
    /**
     * 댓글 고정
     */
    public void pin() {
        this.isPinned = true;
    }
    
    /**
     * 댓글 고정 해제
     */
    public void unpin() {
        this.isPinned = false;
    }
    
    /**
     * 세터 댓글 표시
     */
    public void markAsAuthorComment() {
        this.isAuthorComment = true;
    }
    
    /**
     * 댓글 수정
     */
    public void updateContent(String newContent) {
        this.content = newContent;
    }
    
    @Override
    public Long getId() {
        return commentId;
    }
}
```

---

## 🗳️ 6. RouteDifficultyVote 엔티티 - 난이도 투표

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 루트 난이도 투표
 * - 사용자가 체감한 난이도 투표
 * - 투표 이유 및 근거 제공
 */
@Entity
@Table(name = "route_difficulty_votes", indexes = {
    @Index(name = "idx_vote_route_user", columnList = "route_id, user_id", unique = true),
    @Index(name = "idx_vote_route", columnList = "route_id"),
    @Index(name = "idx_vote_user", columnList = "user_id"),
    @Index(name = "idx_vote_difficulty", columnList = "suggested_difficulty"),
    @Index(name = "idx_vote_created", columnList = "created_at DESC")
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @Size(min = 1, max = 10, message = "제안 난이도는 1-10자 사이여야 합니다")
    @Column(name = "suggested_difficulty", nullable = false, length = 10)
    private String suggestedDifficulty; // 제안하는 난이도 (V0, V1, 5.10a 등)
    
    @Column(name = "original_difficulty", length = 10)
    private String originalDifficulty; // 원래 난이도 (투표 당시)
    
    @Column(name = "difficulty_change")
    private Integer difficultyChange; // 난이도 변화 (-2: 매우 쉬움, 0: 적정, +2: 매우 어려움)
    
    @Column(name = "vote_reason", columnDefinition = "TEXT")
    private String voteReason; // 투표 이유
    
    @Column(name = "user_max_grade", length = 10)
    private String userMaxGrade; // 투표자 최고 등급 (신뢰도 측정용)
    
    @Column(name = "user_experience_level", length = 20)
    private String userExperienceLevel; // 투표자 경력 수준
    
    @Column(name = "climb_attempt_count")
    private Integer climbAttemptCount; // 시도 횟수
    
    @Column(name = "is_successful_climb", nullable = false)
    private boolean isSuccessfulClimb = false; // 완등 여부
    
    @Column(name = "confidence_level")
    private Integer confidenceLevel; // 확신도 (1-5)
    
    @Column(name = "vote_weight")
    private Float voteWeight = 1.0f; // 투표 가중치
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 투표
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // 투표자 IP
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 난이도 변화 한글 표시
     */
    @Transient
    public String getDifficultyChangeKorean() {
        if (difficultyChange == null) return "적정";
        
        return switch (difficultyChange) {
            case -2 -> "매우 쉬움";
            case -1 -> "쉬움";
            case 0 -> "적정";
            case 1 -> "어려움";
            case 2 -> "매우 어려움";
            default -> "알 수 없음";
        };
    }
    
    /**
     * 경험 수준 한글 표시
     */
    @Transient
    public String getExperienceLevelKorean() {
        if (userExperienceLevel == null) return "미설정";
        
        return switch (userExperienceLevel) {
            case "BEGINNER" -> "초급자";
            case "INTERMEDIATE" -> "중급자";
            case "ADVANCED" -> "고급자";
            case "EXPERT" -> "전문가";
            default -> userExperienceLevel;
        };
    }
    
    /**
     * 확신도 한글 표시
     */
    @Transient
    public String getConfidenceLevelKorean() {
        if (confidenceLevel == null) return "보통";
        
        return switch (confidenceLevel) {
            case 1 -> "매우 낮음";
            case 2 -> "낮음";
            case 3 -> "보통";
            case 4 -> "높음";
            case 5 -> "매우 높음";
            default -> "보통";
        };
    }
    
    /**
     * 투표 신뢰도 계산
     */
    @Transient
    public float getVoteReliability() {
        float reliability = voteWeight;
        
        // 완등 여부에 따른 가중치
        if (isSuccessfulClimb) {
            reliability += 0.3f;
        }
        
        // 시도 횟수에 따른 가중치
        if (climbAttemptCount != null && climbAttemptCount > 3) {
            reliability += 0.2f;
        }
        
        // 확신도에 따른 가중치
        if (confidenceLevel != null && confidenceLevel >= 4) {
            reliability += 0.1f;
        }
        
        return Math.min(reliability, 2.0f); // 최대 2.0까지
    }
    
    /**
     * 투표 수정
     */
    public void updateVote(String newDifficulty, String reason, Integer confidence) {
        this.suggestedDifficulty = newDifficulty;
        this.voteReason = reason;
        this.confidenceLevel = confidence;
    }
    
    /**
     * 투표 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * 완등 기록 업데이트
     */
    public void recordClimbSuccess(int attemptCount) {
        this.isSuccessfulClimb = true;
        this.climbAttemptCount = attemptCount;
        
        // 완등 시 가중치 증가
        this.voteWeight = Math.min(voteWeight + 0.2f, 2.0f);
    }
    
    @Override
    public Long getId() {
        return voteId;
    }
}
```

---

## 📌 7. RouteScrap 엔티티 - 루트 스크랩

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 루트 스크랩 (북마크)
 * - 사용자가 나중에 도전할 루트 저장
 * - 개인 메모 및 태그 지원
 */
@Entity
@Table(name = "route_scraps", indexes = {
    @Index(name = "idx_scrap_user_route", columnList = "user_id, route_id", unique = true),
    @Index(name = "idx_scrap_user", columnList = "user_id"),
    @Index(name = "idx_scrap_route", columnList = "route_id"),
    @Index(name = "idx_scrap_folder", columnList = "user_id, folder_name"),
    @Index(name = "idx_scrap_created", columnList = "created_at DESC")
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @Size(max = 50, message = "폴더명은 최대 50자입니다")
    @Column(name = "folder_name", length = 50)
    private String folderName; // 스크랩 폴더명 (기본값: "기본 폴더")
    
    @Column(name = "personal_memo", columnDefinition = "TEXT")
    private String personalMemo; // 개인 메모
    
    @Column(name = "personal_tags", length = 200)
    private String personalTags; // 개인 태그 (쉼표 구분)
    
    @Column(name = "priority_level")
    private Integer priorityLevel = 3; // 우선순위 (1: 높음, 3: 보통, 5: 낮음)
    
    @Column(name = "target_date")
    private java.time.LocalDate targetDate; // 목표 도전일
    
    @Column(name = "scrap_reason", length = 100)
    private String scrapReason; // 스크랩 이유 (TO_TRY, FAVORITE, REFERENCE, GOAL)
    
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false; // 공개 스크랩 (다른 사용자가 볼 수 있음)
    
    @Column(name = "is_notification_enabled", nullable = false)
    private boolean isNotificationEnabled = false; // 알림 설정
    
    @Column(name = "view_count")
    private Integer viewCount = 0; // 조회 횟수 (개인 통계)
    
    @Column(name = "last_viewed_at")
    private java.time.LocalDateTime lastViewedAt; // 마지막 조회일
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 스크랩 이유 한글 표시
     */
    @Transient
    public String getScrapReasonKorean() {
        if (scrapReason == null) return "일반";
        
        return switch (scrapReason) {
            case "TO_TRY" -> "도전 예정";
            case "FAVORITE" -> "즐겨찾기";
            case "REFERENCE" -> "참고용";
            case "GOAL" -> "목표 루트";
            default -> "기타";
        };
    }
    
    /**
     * 우선순위 한글 표시
     */
    @Transient
    public String getPriorityLevelKorean() {
        if (priorityLevel == null) return "보통";
        
        return switch (priorityLevel) {
            case 1 -> "높음";
            case 2 -> "약간 높음";
            case 3 -> "보통";
            case 4 -> "약간 낮음";
            case 5 -> "낮음";
            default -> "보통";
        };
    }
    
    /**
     * 개인 태그 목록 반환
     */
    @Transient
    public java.util.List<String> getPersonalTagList() {
        if (personalTags == null || personalTags.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return java.util.Arrays.asList(personalTags.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 목표일까지 남은 일수
     */
    @Transient
    public long getDaysUntilTarget() {
        if (targetDate == null) return -1;
        return java.time.LocalDate.now().until(targetDate).getDays();
    }
    
    /**
     * 조회 기록
     */
    public void recordView() {
        this.viewCount = (viewCount == null ? 0 : viewCount) + 1;
        this.lastViewedAt = java.time.LocalDateTime.now();
    }
    
    /**
     * 메모 업데이트
     */
    public void updateMemo(String memo) {
        this.personalMemo = memo;
    }
    
    /**
     * 태그 업데이트
     */
    public void updateTags(java.util.List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            this.personalTags = null;
            return;
        }
        this.personalTags = String.join(",", tags);
    }
    
    /**
     * 폴더 이동
     */
    public void moveToFolder(String newFolderName) {
        this.folderName = newFolderName;
    }
    
    /**
     * 우선순위 변경
     */
    public void changePriority(int newPriority) {
        if (newPriority < 1 || newPriority > 5) {
            throw new IllegalArgumentException("우선순위는 1-5 사이여야 합니다");
        }
        this.priorityLevel = newPriority;
    }
    
    /**
     * 목표일 설정
     */
    public void setTargetDate(java.time.LocalDate date) {
        if (date != null && date.isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("목표일은 현재 날짜 이후여야 합니다");
        }
        this.targetDate = date;
    }
    
    @Override
    public Long getId() {
        return scrapId;
    }
}
```

---

## ⚡ 8. 성능 최적화 전략

### 복합 인덱스 DDL 추가
```sql
-- 루트 검색 최적화 (벽별 + 난이도 + 상태)
CREATE INDEX idx_route_search_optimal 
ON routes(wall_id, route_status, difficulty_score, climb_count DESC);

-- 세터별 루트 통계
CREATE INDEX idx_route_setter_stats 
ON routes(route_setter_id, route_status, created_at DESC);

-- 인기 루트 조회
CREATE INDEX idx_route_popularity_complex 
ON routes(route_status, climb_count DESC, like_count DESC, created_at DESC);

-- 댓글 계층 구조 최적화
CREATE INDEX idx_comment_hierarchy 
ON route_comments(route_id, parent_id, is_deleted, created_at DESC);

-- 사용자 스크랩 폴더별 정렬
CREATE INDEX idx_scrap_user_folder_priority 
ON route_scraps(user_id, folder_name, priority_level, created_at DESC);
```

### N+1 문제 해결 쿼리 예시
```java
// Repository에서 Fetch Join 활용
@Query("SELECT r FROM Route r " +
       "LEFT JOIN FETCH r.routeSetter " +
       "LEFT JOIN FETCH r.wall w " +
       "LEFT JOIN FETCH w.branch b " +
       "LEFT JOIN FETCH b.gym " +
       "WHERE r.routeStatus = 'ACTIVE' " +
       "AND r.wall.branch.branchStatus = 'ACTIVE'")
List<Route> findActiveRoutesWithDetails();
```

---

## ✅ 설계 완료 체크리스트

### 루트 관련 엔티티 (7개)
- [x] **Route** - 클라이밍 루트 기본 정보 (난이도, 세터, 홀드 정보)
- [x] **RouteSetter** - 루트 세터 정보 (경력, 등급, 통계)
- [x] **RouteImage** - 루트 이미지 (타입별 분류, 순서 관리)
- [x] **RouteVideo** - 루트 동영상 (완등 영상, 베타, 썸네일)
- [x] **RouteComment** - 루트 댓글 (계층형 구조, 소프트 삭제)
- [x] **RouteDifficultyVote** - 난이도 투표 (사용자 제안, 가중치)
- [x] **RouteScrap** - 루트 스크랩 (개인 폴더, 메모, 태그)

### 전문 기능
- [x] V등급/5.등급 시스템 지원
- [x] 홀드 색상/테이프 정보 관리
- [x] 루트 세터 등급 시스템 (1-10급)
- [x] 계층형 댓글 (부모-자식 구조)
- [x] 난이도 투표 가중치 시스템

### 성능 최적화
- [x] 복합 인덱스 (벽별 + 난이도 + 상태)
- [x] 미디어 파일 최적화 (썸네일, CDN)
- [x] 계층형 댓글 인덱스
- [x] 통계 정보 캐시 (완등률, 조회수)

### 사용자 경험
- [x] 개인 스크랩 폴더 시스템
- [x] 베타 스포일러 처리
- [x] 익명 댓글 지원
- [x] 우선순위별 스크랩 관리

---

**다음 단계**: Step 4-3c 커뮤니티 및 시스템 엔티티 설계  
**완료일**: 2025-08-19  
**핵심 성과**: 7개 루트 엔티티 + 계층형 댓글 + 난이도 투표 시스템