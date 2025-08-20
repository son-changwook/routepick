# Step 4-3c: 클라이밍 및 활동 엔티티 설계

> 클라이밍 등급 시스템, 신발 정보, 클라이밍 기록, 팔로우 관계 엔티티 완전 설계  
> 생성일: 2025-08-19  
> 기반: step4-3b_route_entities.md, step4-1_base_user_entities.md

---

## 🎯 설계 목표

- **전문 등급 시스템**: V등급/5.등급/프랑스 등급 통합 매핑
- **장비 관리**: 클라이밍 신발 데이터베이스 및 사용자 매칭
- **활동 추적**: 상세 클라이밍 기록 및 진행 상황 분석
- **소셜 기능**: 팔로우 관계 및 상호 팔로우 관리

---

## 🎯 1. ClimbingLevel 엔티티 - 클라이밍 등급 시스템

```java
package com.routepick.domain.climb.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 클라이밍 등급 시스템
 * - V등급(볼더링), YDS(5.등급), 프랑스 등급 통합 관리
 * - 등급 간 매핑 및 난이도 점수 제공
 */
@Entity
@Table(name = "climbing_levels", indexes = {
    @Index(name = "idx_level_grade", columnList = "v_grade, french_grade"),
    @Index(name = "idx_level_yds", columnList = "yds_grade"),
    @Index(name = "idx_level_score", columnList = "difficulty_score"),
    @Index(name = "idx_level_type", columnList = "climbing_type"),
    @Index(name = "idx_level_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ClimbingLevel extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "level_id")
    private Long levelId;
    
    // ===== 등급 시스템 =====
    
    @Column(name = "v_grade", length = 10)
    private String vGrade; // V0, V1, V2, ..., V17
    
    @Column(name = "yds_grade", length = 10)
    private String ydsGrade; // 5.4, 5.10a, 5.12d, 5.15d
    
    @Column(name = "french_grade", length = 10)
    private String frenchGrade; // 4a, 6a+, 7c, 9c
    
    @Column(name = "uk_grade", length = 10)
    private String ukGrade; // E1, E2, ..., E11 (영국 등급)
    
    @Column(name = "australian_grade", length = 10)
    private String australianGrade; // 호주 등급
    
    // ===== 난이도 점수 =====
    
    @NotNull
    @DecimalMin(value = "0.0", message = "난이도 점수는 0.0 이상이어야 합니다")
    @DecimalMax(value = "30.0", message = "난이도 점수는 30.0 이하여야 합니다")
    @Column(name = "difficulty_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal difficultyScore; // 통합 난이도 점수 (V0=0, V1=1, ...)
    
    @Column(name = "relative_difficulty")
    private Float relativeDifficulty; // 상대적 난이도 (0.0~1.0)
    
    // ===== 등급 정보 =====
    
    @NotNull
    @Column(name = "climbing_type", nullable = false, length = 20)
    private String climbingType; // BOULDER, SPORT, TRAD, MIXED
    
    @Column(name = "grade_description", columnDefinition = "TEXT")
    private String gradeDescription; // 등급 설명
    
    @Column(name = "typical_holds", length = 200)
    private String typicalHolds; // 일반적인 홀드 타입
    
    @Column(name = "required_skills", length = 200)
    private String requiredSkills; // 필요한 기술
    
    @Column(name = "average_time_to_send")
    private Integer averageTimeToSend; // 평균 완등 소요시간 (분)
    
    // ===== 분류 정보 =====
    
    @NotNull
    @Min(value = 1, message = "등급 순서는 1 이상이어야 합니다")
    @Column(name = "grade_order", nullable = false)
    private Integer gradeOrder; // 등급 순서 (1, 2, 3, ...)
    
    @Column(name = "beginner_friendly", nullable = false)
    private boolean beginnerFriendly = false; // 초보자 친화적
    
    @Column(name = "is_competition_grade", nullable = false)
    private boolean isCompetitionGrade = false; // 대회 등급
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 등급
    
    @Column(name = "color_code", length = 7)
    private String colorCode; // 등급별 색상 코드
    
    // ===== 통계 정보 =====
    
    @Column(name = "route_count")
    private Integer routeCount = 0; // 해당 등급 루트 수
    
    @Column(name = "user_count")
    private Integer userCount = 0; // 해당 등급 달성 사용자 수
    
    @Column(name = "average_success_rate")
    private Float averageSuccessRate = 0.0f; // 평균 성공률
    
    @Column(name = "total_attempts")
    private Long totalAttempts = 0L; // 총 시도 횟수
    
    @Column(name = "total_sends")
    private Long totalSends = 0L; // 총 완등 횟수
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "climbingLevel", fetch = FetchType.LAZY)
    private List<Route> routes = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 등급 한글 표시
     */
    @Transient
    public String getGradeKorean() {
        if (vGrade != null) {
            return vGrade + "급";
        } else if (ydsGrade != null) {
            return ydsGrade + "등급";
        } else if (frenchGrade != null) {
            return frenchGrade + "등급";
        }
        return "미설정";
    }
    
    /**
     * 난이도 수준 분류
     */
    @Transient
    public String getDifficultyLevel() {
        if (difficultyScore == null) return "미설정";
        
        BigDecimal score = difficultyScore;
        if (score.compareTo(new BigDecimal("3")) <= 0) return "입문";
        else if (score.compareTo(new BigDecimal("6")) <= 0) return "초급";
        else if (score.compareTo(new BigDecimal("9")) <= 0) return "중급";
        else if (score.compareTo(new BigDecimal("12")) <= 0) return "고급";
        else if (score.compareTo(new BigDecimal("15")) <= 0) return "전문가";
        else return "엘리트";
    }
    
    /**
     * 등급 색상 (기본값 제공)
     */
    @Transient
    public String getDisplayColor() {
        if (colorCode != null) return colorCode;
        
        // 난이도에 따른 기본 색상
        String level = getDifficultyLevel();
        return switch (level) {
            case "입문" -> "#4CAF50"; // 초록
            case "초급" -> "#8BC34A"; // 연두
            case "중급" -> "#FF9800"; // 주황
            case "고급" -> "#F44336"; // 빨강
            case "전문가" -> "#9C27B0"; // 보라
            case "엘리트" -> "#000000"; // 검정
            default -> "#9E9E9E"; // 회색
        };
    }
    
    /**
     * 다음 등급 조회
     */
    @Transient
    public ClimbingLevel getNextLevel() {
        // Repository에서 구현 (gradeOrder + 1)
        return null;
    }
    
    /**
     * 이전 등급 조회
     */
    @Transient
    public ClimbingLevel getPreviousLevel() {
        // Repository에서 구현 (gradeOrder - 1)
        return null;
    }
    
    /**
     * 통계 정보 업데이트
     */
    public void updateStatistics() {
        if (totalAttempts > 0) {
            this.averageSuccessRate = (float) totalSends / totalAttempts * 100;
        }
        // routeCount, userCount는 Repository에서 계산
    }
    
    /**
     * 시도/완등 기록 추가
     */
    public void recordAttempt(boolean success) {
        this.totalAttempts = (totalAttempts == null ? 0L : totalAttempts) + 1;
        if (success) {
            this.totalSends = (totalSends == null ? 0L : totalSends) + 1;
        }
        updateStatistics();
    }
    
    /**
     * V등급을 YDS로 변환 (근사치)
     */
    @Transient
    public String getApproximateYDS() {
        if (vGrade == null) return null;
        
        return switch (vGrade) {
            case "V0" -> "5.10a";
            case "V1" -> "5.10c";
            case "V2" -> "5.11a";
            case "V3" -> "5.11c";
            case "V4" -> "5.12a";
            case "V5" -> "5.12b";
            case "V6" -> "5.12d";
            case "V7" -> "5.13a";
            case "V8" -> "5.13b";
            case "V9" -> "5.13d";
            case "V10" -> "5.14a";
            case "V11" -> "5.14b";
            case "V12" -> "5.14d";
            case "V13" -> "5.15a";
            case "V14" -> "5.15b";
            case "V15" -> "5.15c";
            default -> "5.10a";
        };
    }
    
    @Override
    public Long getId() {
        return levelId;
    }
}
```

---

## 👟 2. ClimbingShoe 엔티티 - 클라이밍 신발 정보

```java
package com.routepick.domain.climb.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 클라이밍 신발 정보
 * - 브랜드, 모델, 타입별 분류
 * - 사이즈 범위 및 특성 정보
 */
@Entity
@Table(name = "climbing_shoes", indexes = {
    @Index(name = "idx_shoe_brand_model", columnList = "brand, model"),
    @Index(name = "idx_shoe_type", columnList = "shoe_type"),
    @Index(name = "idx_shoe_rating", columnList = "average_rating DESC"),
    @Index(name = "idx_shoe_price", columnList = "price_range"),
    @Index(name = "idx_shoe_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ClimbingShoe extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shoe_id")
    private Long shoeId;
    
    // ===== 기본 정보 =====
    
    @NotNull
    @Size(min = 2, max = 50, message = "브랜드명은 2-50자 사이여야 합니다")
    @Column(name = "brand", nullable = false, length = 50)
    private String brand; // La Sportiva, Scarpa, Five Ten, Solution 등
    
    @NotNull
    @Size(min = 2, max = 100, message = "모델명은 2-100자 사이여야 합니다")
    @Column(name = "model", nullable = false, length = 100)
    private String model; // Solution, Miura, Instinct, Python 등
    
    @Column(name = "model_year")
    private Integer modelYear; // 출시 연도
    
    @Column(name = "model_code", length = 50)
    private String modelCode; // 모델 코드
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 신발 설명
    
    // ===== 신발 특성 =====
    
    @NotNull
    @Column(name = "shoe_type", nullable = false, length = 30)
    private String shoeType; // AGGRESSIVE, MODERATE, COMFORT, CRACK, SLAB
    
    @Column(name = "closure_type", length = 30)
    private String closureType; // LACE, VELCRO, SLIP_ON
    
    @Column(name = "sole_material", length = 50)
    private String soleMaterial; // Vibram XS Edge, XS Grip2, C4 등
    
    @Column(name = "upper_material", length = 100)
    private String upperMaterial; // 합성피혁, 천연가죽, 패브릭 등
    
    @Column(name = "last_type", length = 30)
    private String lastType; // 발볼 형태 (NARROW, MEDIUM, WIDE)
    
    @Column(name = "asymmetry_level")
    private Integer asymmetryLevel; // 비대칭 정도 (1-5, 5가 가장 비대칭)
    
    @Column(name = "downturn_level")
    private Integer downturnLevel; // 다운턴 정도 (1-5, 5가 가장 다운턴)
    
    @Column(name = "stiffness_level")
    private Integer stiffnessLevel; // 강성 (1-5, 5가 가장 딱딱함)
    
    // ===== 사이즈 정보 =====
    
    @Min(value = 200, message = "최소 사이즈는 200mm입니다")
    @Max(value = 320, message = "최대 사이즈는 320mm입니다")
    @Column(name = "min_size_mm")
    private Integer minSizeEU; // EU 사이즈 최소
    
    @Min(value = 200, message = "최소 사이즈는 200mm입니다")
    @Max(value = 320, message = "최대 사이즈는 320mm입니다")
    @Column(name = "max_size_mm")
    private Integer maxSizeEU; // EU 사이즈 최대
    
    @Column(name = "size_advice", columnDefinition = "TEXT")
    private String sizeAdvice; // 사이즈 조언 (평소보다 1사이즈 작게 등)
    
    @Column(name = "stretch_potential")
    private Float stretchPotential; // 늘어남 정도 (0.0~2.0)
    
    // ===== 가격 정보 =====
    
    @Column(name = "price_range", length = 30)
    private String priceRange; // LOW, MID, HIGH, PREMIUM
    
    @Column(name = "retail_price")
    private Integer retailPrice; // 정가 (원)
    
    @Column(name = "currency", length = 10)
    private String currency = "KRW"; // 통화
    
    // ===== 성능 지표 =====
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "edging_performance")
    private Integer edgingPerformance; // 에징 성능 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "smearing_performance")
    private Integer smearingPerformance; // 스미어링 성능 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "hooking_performance")
    private Integer hookingPerformance; // 후킹 성능 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "crack_performance")
    private Integer crackPerformance; // 크랙 성능 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "comfort_level")
    private Integer comfortLevel; // 편안함 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "durability_level")
    private Integer durabilityLevel; // 내구성 (1-5)
    
    // ===== 메타 정보 =====
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 모델
    
    @Column(name = "is_discontinued", nullable = false)
    private boolean isDiscontinued = false; // 단종 여부
    
    @Column(name = "gender_type", length = 20)
    private String genderType; // UNISEX, MEN, WOMEN
    
    @Column(name = "target_skill_level", length = 30)
    private String targetSkillLevel; // BEGINNER, INTERMEDIATE, ADVANCED, PRO
    
    @Column(name = "best_for", length = 200)
    private String bestFor; // 최적 용도 (볼더링, 스포츠클라이밍, 멀티피치 등)
    
    // ===== 통계 정보 =====
    
    @Column(name = "average_rating")
    private Float averageRating = 0.0f; // 평균 평점
    
    @Column(name = "review_count")
    private Integer reviewCount = 0; // 리뷰 수
    
    @Column(name = "user_count")
    private Integer userCount = 0; // 사용자 수
    
    @Column(name = "popularity_score")
    private Integer popularityScore = 0; // 인기 점수
    
    // ===== 이미지 정보 =====
    
    @Column(name = "main_image_url", length = 500)
    private String mainImageUrl; // 대표 이미지
    
    @Column(name = "gallery_images", columnDefinition = "TEXT")
    private String galleryImages; // 갤러리 이미지 (JSON 배열)
    
    @Column(name = "brand_logo_url", length = 500)
    private String brandLogoUrl; // 브랜드 로고
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "climbingShoe", fetch = FetchType.LAZY)
    private List<UserClimbingShoe> userClimbingShoes = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 신발 타입 한글 표시
     */
    @Transient
    public String getShoeTypeKorean() {
        if (shoeType == null) return "일반";
        
        return switch (shoeType) {
            case "AGGRESSIVE" -> "어그레시브";
            case "MODERATE" -> "모더레이트";
            case "COMFORT" -> "컴포트";
            case "CRACK" -> "크랙 전용";
            case "SLAB" -> "슬랩 전용";
            default -> "일반";
        };
    }
    
    /**
     * 전체 성능 점수 계산
     */
    @Transient
    public float getOverallPerformance() {
        int total = 0;
        int count = 0;
        
        if (edgingPerformance != null) { total += edgingPerformance; count++; }
        if (smearingPerformance != null) { total += smearingPerformance; count++; }
        if (hookingPerformance != null) { total += hookingPerformance; count++; }
        if (crackPerformance != null) { total += crackPerformance; count++; }
        
        return count > 0 ? (float) total / count : 0.0f;
    }
    
    /**
     * 권장 사이즈 계산 (EU 기준)
     */
    @Transient
    public String getRecommendedSizing(int userFootSize) {
        if (stretchPotential == null) stretchPotential = 0.5f;
        
        float recommendedSize = userFootSize - stretchPotential;
        
        if (shoeType.equals("AGGRESSIVE")) {
            recommendedSize -= 1.0f; // 어그레시브는 더 타이트하게
        } else if (shoeType.equals("COMFORT")) {
            recommendedSize += 0.5f; // 컴포트는 여유있게
        }
        
        return String.format("%.1f", recommendedSize);
    }
    
    /**
     * 평점 업데이트
     */
    public void updateRating(float newRating) {
        if (averageRating == null || reviewCount == null) {
            this.averageRating = newRating;
            this.reviewCount = 1;
            return;
        }
        
        float totalRating = averageRating * reviewCount + newRating;
        this.reviewCount = reviewCount + 1;
        this.averageRating = totalRating / reviewCount;
    }
    
    /**
     * 인기도 업데이트
     */
    public void updatePopularity() {
        // 사용자 수, 리뷰 수, 평점을 종합한 인기 점수
        int score = (userCount != null ? userCount : 0) * 2 +
                   (reviewCount != null ? reviewCount : 0) * 3 +
                   (int)((averageRating != null ? averageRating : 0.0f) * 20);
        
        this.popularityScore = score;
    }
    
    /**
     * 단종 처리
     */
    public void discontinue() {
        this.isDiscontinued = true;
        this.isActive = false;
    }
    
    @Override
    public Long getId() {
        return shoeId;
    }
}
```

---

## 👟📝 3. UserClimbingShoe 엔티티 - 사용자 신발 정보

```java
package com.routepick.domain.climb.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

/**
 * 사용자 클라이밍 신발 정보
 * - 개인 신발 컬렉션 관리
 * - 사용 후기 및 평가
 */
@Entity
@Table(name = "user_climbing_shoes", indexes = {
    @Index(name = "idx_user_shoe", columnList = "user_id, shoe_id"),
    @Index(name = "idx_user_shoes", columnList = "user_id"),
    @Index(name = "idx_shoe_users", columnList = "shoe_id"),
    @Index(name = "idx_user_shoe_rating", columnList = "user_id, review_rating DESC"),
    @Index(name = "idx_shoe_purchase", columnList = "purchase_date DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserClimbingShoe extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_shoe_id")
    private Long userShoeId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shoe_id", nullable = false)
    private ClimbingShoe climbingShoe;
    
    // ===== 사이즈 정보 =====
    
    @NotNull
    @Min(value = 200, message = "신발 사이즈는 200mm 이상이어야 합니다")
    @Max(value = 320, message = "신발 사이즈는 320mm 이하여야 합니다")
    @Column(name = "shoe_size", nullable = false)
    private Integer shoeSize; // EU 사이즈
    
    @Column(name = "size_feeling", length = 20)
    private String sizeFeeling; // TOO_SMALL, PERFECT, TOO_BIG
    
    @Column(name = "width_feeling", length = 20)
    private String widthFeeling; // TOO_NARROW, PERFECT, TOO_WIDE
    
    @Column(name = "actual_stretch")
    private Float actualStretch; // 실제 늘어난 정도
    
    // ===== 구매 정보 =====
    
    @Column(name = "purchase_date")
    private LocalDate purchaseDate; // 구매일
    
    @Column(name = "purchase_price")
    private Integer purchasePrice; // 구매 가격
    
    @Column(name = "purchase_store", length = 100)
    private String purchaseStore; // 구매처
    
    @Column(name = "is_new_purchase", nullable = false)
    private boolean isNewPurchase = true; // 신품 구매 여부
    
    // ===== 사용 정보 =====
    
    @Column(name = "first_use_date")
    private LocalDate firstUseDate; // 첫 사용일
    
    @Column(name = "last_use_date")
    private LocalDate lastUseDate; // 마지막 사용일
    
    @Column(name = "total_use_days")
    private Integer totalUseDays = 0; // 총 사용 일수
    
    @Column(name = "total_climb_hours")
    private Integer totalClimbHours = 0; // 총 클라이밍 시간
    
    @Column(name = "current_condition", length = 30)
    private String currentCondition; // NEW, GOOD, FAIR, WORN, RETIRED
    
    @Column(name = "is_currently_using", nullable = false)
    private boolean isCurrentlyUsing = true; // 현재 사용 중
    
    // ===== 성능 평가 =====
    
    @Min(value = 1, message = "리뷰 평점은 1점 이상이어야 합니다")
    @Max(value = 5, message = "리뷰 평점은 5점 이하여야 합니다")
    @Column(name = "review_rating")
    private Integer reviewRating; // 개인 평점 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "comfort_rating")
    private Integer comfortRating; // 편안함 평점
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "performance_rating")
    private Integer performanceRating; // 성능 평점
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "durability_rating")
    private Integer durabilityRating; // 내구성 평점
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "value_rating")
    private Integer valueRating; // 가성비 평점
    
    // ===== 상세 리뷰 =====
    
    @Size(max = 1000, message = "리뷰는 최대 1000자입니다")
    @Column(name = "review_text", columnDefinition = "TEXT")
    private String reviewText; // 리뷰 내용
    
    @Column(name = "pros", columnDefinition = "TEXT")
    private String pros; // 장점
    
    @Column(name = "cons", columnDefinition = "TEXT")
    private String cons; // 단점
    
    @Column(name = "best_use_cases", length = 200)
    private String bestUseCases; // 최적 사용 용도
    
    @Column(name = "sizing_advice", columnDefinition = "TEXT")
    private String sizingAdvice; // 사이즈 조언
    
    // ===== 추천 정보 =====
    
    @Column(name = "would_recommend", nullable = false)
    private boolean wouldRecommend = true; // 추천 여부
    
    @Column(name = "recommend_reason", columnDefinition = "TEXT")
    private String recommendReason; // 추천/비추천 이유
    
    @Column(name = "target_level", length = 50)
    private String targetLevel; // 추천 대상 수준
    
    // ===== 관리 정보 =====
    
    @Column(name = "maintenance_notes", columnDefinition = "TEXT")
    private String maintenanceNotes; // 관리 메모
    
    @Column(name = "retirement_reason", length = 200)
    private String retirementReason; // 은퇴 사유
    
    @Column(name = "replacement_shoe_id")
    private Long replacementShoeId; // 교체한 신발 ID
    
    @Column(name = "is_public_review", nullable = false)
    private boolean isPublicReview = true; // 공개 리뷰 여부
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 사이즈 만족도 한글 표시
     */
    @Transient
    public String getSizeFeelingKorean() {
        if (sizeFeeling == null) return "적당함";
        
        return switch (sizeFeeling) {
            case "TOO_SMALL" -> "너무 작음";
            case "PERFECT" -> "완벽함";
            case "TOO_BIG" -> "너무 큼";
            default -> "적당함";
        };
    }
    
    /**
     * 신발 상태 한글 표시
     */
    @Transient
    public String getConditionKorean() {
        if (currentCondition == null) return "양호";
        
        return switch (currentCondition) {
            case "NEW" -> "새 제품";
            case "GOOD" -> "양호";
            case "FAIR" -> "보통";
            case "WORN" -> "마모됨";
            case "RETIRED" -> "은퇴";
            default -> "양호";
        };
    }
    
    /**
     * 전체 평점 계산
     */
    @Transient
    public float getOverallRating() {
        int total = 0;
        int count = 0;
        
        if (comfortRating != null) { total += comfortRating; count++; }
        if (performanceRating != null) { total += performanceRating; count++; }
        if (durabilityRating != null) { total += durabilityRating; count++; }
        if (valueRating != null) { total += valueRating; count++; }
        
        return count > 0 ? (float) total / count : 0.0f;
    }
    
    /**
     * 사용 기간 계산 (개월)
     */
    @Transient
    public long getUsageMonths() {
        if (firstUseDate == null) return 0;
        
        LocalDate endDate = lastUseDate != null ? lastUseDate : LocalDate.now();
        return java.time.temporal.ChronoUnit.MONTHS.between(firstUseDate, endDate);
    }
    
    /**
     * 월평균 사용 일수
     */
    @Transient
    public float getMonthlyUsageDays() {
        long months = getUsageMonths();
        if (months == 0 || totalUseDays == null) return 0.0f;
        
        return (float) totalUseDays / months;
    }
    
    /**
     * 사용 기록 추가
     */
    public void recordUsage(int hoursUsed) {
        this.totalUseDays = (totalUseDays == null ? 0 : totalUseDays) + 1;
        this.totalClimbHours = (totalClimbHours == null ? 0 : totalClimbHours) + hoursUsed;
        this.lastUseDate = LocalDate.now();
        
        if (firstUseDate == null) {
            this.firstUseDate = LocalDate.now();
        }
    }
    
    /**
     * 리뷰 업데이트
     */
    public void updateReview(Integer rating, String reviewText, boolean recommend) {
        this.reviewRating = rating;
        this.reviewText = reviewText;
        this.wouldRecommend = recommend;
        this.reviewRating = (int) getOverallRating();
    }
    
    /**
     * 신발 은퇴 처리
     */
    public void retireShoe(String reason, Long replacementShoeId) {
        this.isCurrentlyUsing = false;
        this.currentCondition = "RETIRED";
        this.retirementReason = reason;
        this.replacementShoeId = replacementShoeId;
    }
    
    /**
     * 상태 업데이트
     */
    public void updateCondition(String newCondition) {
        this.currentCondition = newCondition;
        
        if ("RETIRED".equals(newCondition)) {
            this.isCurrentlyUsing = false;
        }
    }
    
    @Override
    public Long getId() {
        return userShoeId;
    }
}
```

---

## 📈 4. UserClimb 엔티티 - 클라이밍 기록

```java
package com.routepick.domain.activity.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 사용자 클라이밍 기록
 * - 개별 루트 도전 기록
 * - 성공/실패, 시도 횟수, 소요 시간 등
 */
@Entity
@Table(name = "user_climbs", indexes = {
    @Index(name = "idx_climb_user_date", columnList = "user_id, climb_date DESC"),
    @Index(name = "idx_climb_route", columnList = "route_id"),
    @Index(name = "idx_climb_user_route", columnList = "user_id, route_id"),
    @Index(name = "idx_climb_success", columnList = "is_successful, climb_date DESC"),
    @Index(name = "idx_climb_rating", columnList = "difficulty_rating DESC"),
    @Index(name = "idx_climb_branch", columnList = "branch_id, climb_date DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserClimb extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "climb_id")
    private Long climbId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    // ===== 클라이밍 기본 정보 =====
    
    @NotNull
    @Column(name = "climb_date", nullable = false)
    private LocalDate climbDate; // 클라이밍 날짜
    
    @Column(name = "start_time")
    private LocalTime startTime; // 시작 시간
    
    @Column(name = "end_time")
    private LocalTime endTime; // 종료 시간
    
    @Column(name = "is_successful", nullable = false)
    private boolean isSuccessful = false; // 성공 여부
    
    @Min(value = 1, message = "시도 횟수는 1회 이상이어야 합니다")
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1; // 시도 횟수
    
    @Column(name = "success_attempt")
    private Integer successAttempt; // 성공한 시도 번호
    
    // ===== 성과 정보 =====
    
    @Column(name = "climb_type", length = 30)
    private String climbType; // FLASH, ONSIGHT, REDPOINT, REPEAT
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "difficulty_rating")
    private Integer difficultyRating; // 체감 난이도 평점 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "enjoyment_rating")
    private Integer enjoymentRating; // 재미 평점 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "quality_rating")
    private Integer qualityRating; // 루트 품질 평점 (1-5)
    
    @Column(name = "personal_record", nullable = false)
    private boolean personalRecord = false; // 개인 기록 여부
    
    // ===== 상세 기록 =====
    
    @Column(name = "total_time_minutes")
    private Integer totalTimeMinutes; // 총 소요 시간 (분)
    
    @Column(name = "rest_time_minutes")
    private Integer restTimeMinutes; // 휴식 시간 (분)
    
    @Column(name = "fall_count")
    private Integer fallCount = 0; // 추락 횟수
    
    @Column(name = "key_holds_missed")
    private String keyHoldsMissed; // 놓친 핵심 홀드
    
    @Column(name = "beta_used", columnDefinition = "TEXT")
    private String betaUsed; // 사용한 베타
    
    @Column(name = "technique_notes", columnDefinition = "TEXT")
    private String techniqueNotes; // 기술 메모
    
    // ===== 환경 정보 =====
    
    @Column(name = "branch_id")
    private Long branchId; // 암장 지점 ID (비정규화)
    
    @Column(name = "wall_condition", length = 50)
    private String wallCondition; // 벽면 상태
    
    @Column(name = "weather_condition", length = 50)
    private String weatherCondition; // 날씨 (실외인 경우)
    
    @Column(name = "crowd_level", length = 20)
    private String crowdLevel; // 혼잡도 (EMPTY, LOW, MODERATE, HIGH, CROWDED)
    
    // ===== 신체/장비 정보 =====
    
    @Column(name = "climbing_shoe_id")
    private Long climbingShoeId; // 사용한 신발 ID
    
    @Column(name = "chalk_type", length = 30)
    private String chalkType; // 사용한 초크 종류
    
    @Column(name = "physical_condition", length = 30)
    private String physicalCondition; // 컨디션 (EXCELLENT, GOOD, FAIR, POOR)
    
    @Column(name = "injury_notes", length = 200)
    private String injuryNotes; // 부상 메모
    
    // ===== 소셜/공유 정보 =====
    
    @Column(name = "climb_notes", columnDefinition = "TEXT")
    private String climbNotes; // 클라이밍 메모
    
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true; // 공개 여부
    
    @Column(name = "shared_with_community", nullable = false)
    private boolean sharedWithCommunity = false; // 커뮤니티 공유 여부
    
    @Column(name = "climb_partners", length = 200)
    private String climbPartners; // 함께한 파트너들
    
    @Column(name = "witness_count")
    private Integer witnessCount = 0; // 목격자 수
    
    // ===== 메타데이터 =====
    
    @Column(name = "gps_latitude", precision = 10, scale = 8)
    private java.math.BigDecimal gpsLatitude; // GPS 위도
    
    @Column(name = "gps_longitude", precision = 11, scale = 8)
    private java.math.BigDecimal gpsLongitude; // GPS 경도
    
    @Column(name = "recorded_device", length = 50)
    private String recordedDevice; // 기록 디바이스
    
    @Column(name = "session_id", length = 100)
    private String sessionId; // 세션 ID (같은 날 같은 장소)
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 클라이밍 타입 한글 표시
     */
    @Transient
    public String getClimbTypeKorean() {
        if (climbType == null) return "일반";
        
        return switch (climbType) {
            case "FLASH" -> "플래시";
            case "ONSIGHT" -> "온사이트";
            case "REDPOINT" -> "레드포인트";
            case "REPEAT" -> "반복 완등";
            default -> "일반";
        };
    }
    
    /**
     * 성공률 계산
     */
    @Transient
    public float getSuccessRate() {
        if (attemptCount == null || attemptCount == 0) return 0.0f;
        return isSuccessful ? (1.0f / attemptCount * 100) : 0.0f;
    }
    
    /**
     * 순수 클라이밍 시간 계산
     */
    @Transient
    public Integer getActiveClimbTime() {
        if (totalTimeMinutes == null) return null;
        if (restTimeMinutes == null) return totalTimeMinutes;
        
        return Math.max(0, totalTimeMinutes - restTimeMinutes);
    }
    
    /**
     * 컨디션 점수 계산 (1-100)
     */
    @Transient
    public int getConditionScore() {
        if (physicalCondition == null) return 50;
        
        return switch (physicalCondition) {
            case "EXCELLENT" -> 90;
            case "GOOD" -> 70;
            case "FAIR" -> 50;
            case "POOR" -> 30;
            default -> 50;
        };
    }
    
    /**
     * 전체 만족도 계산
     */
    @Transient
    public float getOverallSatisfaction() {
        int total = 0;
        int count = 0;
        
        if (difficultyRating != null) { total += difficultyRating; count++; }
        if (enjoymentRating != null) { total += enjoymentRating; count++; }
        if (qualityRating != null) { total += qualityRating; count++; }
        
        return count > 0 ? (float) total / count : 0.0f;
    }
    
    /**
     * 성공 기록 처리
     */
    public void recordSuccess(int attemptNumber, String beta, String notes) {
        this.isSuccessful = true;
        this.successAttempt = attemptNumber;
        this.betaUsed = beta;
        this.techniqueNotes = notes;
        this.personalRecord = checkPersonalRecord();
    }
    
    /**
     * 개인 기록 확인
     */
    private boolean checkPersonalRecord() {
        // Service Layer에서 구현
        // 해당 사용자의 이전 기록과 비교
        return false;
    }
    
    /**
     * 시간 기록 설정
     */
    public void setTimeRecord(LocalTime start, LocalTime end, Integer restMinutes) {
        this.startTime = start;
        this.endTime = end;
        this.restTimeMinutes = restMinutes;
        
        if (start != null && end != null) {
            long totalMinutes = java.time.Duration.between(start, end).toMinutes();
            this.totalTimeMinutes = (int) totalMinutes;
        }
    }
    
    /**
     * 평점 업데이트
     */
    public void updateRatings(Integer difficulty, Integer enjoyment, Integer quality) {
        this.difficultyRating = difficulty;
        this.enjoymentRating = enjoyment;
        this.qualityRating = quality;
    }
    
    /**
     * 커뮤니티 공유
     */
    public void shareWithCommunity(String partners, String notes) {
        this.sharedWithCommunity = true;
        this.climbPartners = partners;
        this.climbNotes = notes;
        this.isPublic = true;
    }
    
    @Override
    public Long getId() {
        return climbId;
    }
}
```

---

## 👥 5. UserFollow 엔티티 - 팔로우 관계

```java
package com.routepick.domain.activity.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자 팔로우 관계
 * - 팔로워/팔로잉 관계 관리
 * - 상호 팔로우 확인
 */
@Entity
@Table(name = "user_follows", indexes = {
    @Index(name = "idx_follow_relationship", columnList = "follower_user_id, following_user_id", unique = true),
    @Index(name = "idx_follow_follower", columnList = "follower_user_id"),
    @Index(name = "idx_follow_following", columnList = "following_user_id"),
    @Index(name = "idx_follow_mutual", columnList = "is_mutual"),
    @Index(name = "idx_follow_date", columnList = "follow_date DESC"),
    @Index(name = "idx_follow_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserFollow extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "follow_id")
    private Long followId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_user_id", nullable = false)
    private User followerUser; // 팔로우 하는 사용자
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_user_id", nullable = false)
    private User followingUser; // 팔로우 받는 사용자
    
    @NotNull
    @Column(name = "follow_date", nullable = false)
    private LocalDateTime followDate; // 팔로우 시작일
    
    @Column(name = "unfollow_date")
    private LocalDateTime unfollowDate; // 언팔로우 일시
    
    @Column(name = "is_mutual", nullable = false)
    private boolean isMutual = false; // 상호 팔로우 여부
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 팔로우
    
    @Column(name = "follow_source", length = 50)
    private String followSource; // 팔로우 경로 (SEARCH, RECOMMENDATION, ROUTE, COMMENT 등)
    
    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = true; // 알림 설정
    
    @Column(name = "close_friend", nullable = false)
    private boolean closeFriend = false; // 친한 친구 표시
    
    @Column(name = "blocked", nullable = false)
    private boolean blocked = false; // 차단 여부
    
    @Column(name = "muted", nullable = false)
    private boolean muted = false; // 음소거 여부
    
    // ===== 통계 정보 =====
    
    @Column(name = "interaction_count")
    private Integer interactionCount = 0; // 상호작용 횟수
    
    @Column(name = "last_interaction_date")
    private LocalDateTime lastInteractionDate; // 마지막 상호작용 일시
    
    @Column(name = "mutual_climb_count")
    private Integer mutualClimbCount = 0; // 함께한 클라이밍 수
    
    @Column(name = "last_activity_view_date")
    private LocalDateTime lastActivityViewDate; // 마지막 활동 조회일
    
    // ===== 개인정보 =====
    
    @Column(name = "follow_note", length = 200)
    private String followNote; // 팔로우 메모
    
    @Column(name = "nickname", length = 50)
    private String nickname; // 개인적 별명
    
    @Column(name = "relationship_type", length = 30)
    private String relationshipType; // FRIEND, CLIMBING_PARTNER, INSPIRATION, OTHER
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 팔로우 관계 한글 표시
     */
    @Transient
    public String getRelationshipTypeKorean() {
        if (relationshipType == null) return "일반";
        
        return switch (relationshipType) {
            case "FRIEND" -> "친구";
            case "CLIMBING_PARTNER" -> "클라이밍 파트너";
            case "INSPIRATION" -> "영감을 주는 사람";
            case "OTHER" -> "기타";
            default -> "일반";
        };
    }
    
    /**
     * 팔로우 기간 계산 (일)
     */
    @Transient
    public long getFollowDurationDays() {
        if (followDate == null) return 0;
        
        LocalDateTime endDate = isActive ? LocalDateTime.now() : unfollowDate;
        if (endDate == null) endDate = LocalDateTime.now();
        
        return java.time.temporal.ChronoUnit.DAYS.between(followDate, endDate);
    }
    
    /**
     * 활성도 점수 계산 (0-100)
     */
    @Transient
    public int getActivityScore() {
        int score = 0;
        
        // 기본 팔로우 점수
        score += 20;
        
        // 상호 팔로우 보너스
        if (isMutual) score += 30;
        
        // 상호작용 점수
        if (interactionCount != null) {
            score += Math.min(interactionCount * 2, 30);
        }
        
        // 최근 활동 보너스
        if (lastInteractionDate != null) {
            long daysSinceLastInteraction = java.time.temporal.ChronoUnit.DAYS
                .between(lastInteractionDate, LocalDateTime.now());
            
            if (daysSinceLastInteraction <= 7) score += 20;
            else if (daysSinceLastInteraction <= 30) score += 10;
        }
        
        return Math.min(score, 100);
    }
    
    /**
     * 상호작용 기록
     */
    public void recordInteraction() {
        this.interactionCount = (interactionCount == null ? 0 : interactionCount) + 1;
        this.lastInteractionDate = LocalDateTime.now();
    }
    
    /**
     * 상호 팔로우 설정
     */
    public void setMutualFollow() {
        this.isMutual = true;
    }
    
    /**
     * 상호 팔로우 해제
     */
    public void unsetMutualFollow() {
        this.isMutual = false;
    }
    
    /**
     * 언팔로우 처리
     */
    public void unfollow() {
        this.isActive = false;
        this.unfollowDate = LocalDateTime.now();
        this.isMutual = false;
        this.notificationEnabled = false;
    }
    
    /**
     * 팔로우 재개
     */
    public void refollow() {
        this.isActive = true;
        this.unfollowDate = null;
        this.followDate = LocalDateTime.now(); // 새로운 팔로우 날짜
        this.notificationEnabled = true;
    }
    
    /**
     * 차단 처리
     */
    public void block() {
        this.blocked = true;
        this.isActive = false;
        this.notificationEnabled = false;
        this.isMutual = false;
    }
    
    /**
     * 차단 해제
     */
    public void unblock() {
        this.blocked = false;
    }
    
    /**
     * 음소거 처리
     */
    public void mute() {
        this.muted = true;
        this.notificationEnabled = false;
    }
    
    /**
     * 음소거 해제
     */
    public void unmute() {
        this.muted = false;
        this.notificationEnabled = true;
    }
    
    /**
     * 친한 친구 설정
     */
    public void setCloseFriend(boolean isCloseFriend) {
        this.closeFriend = isCloseFriend;
    }
    
    /**
     * 개인 메모 업데이트
     */
    public void updateNote(String note, String nickname, String relationshipType) {
        this.followNote = note;
        this.nickname = nickname;
        this.relationshipType = relationshipType;
    }
    
    /**
     * 함께한 클라이밍 기록
     */
    public void recordMutualClimb() {
        this.mutualClimbCount = (mutualClimbCount == null ? 0 : mutualClimbCount) + 1;
        recordInteraction();
    }
    
    /**
     * 마지막 활동 조회 업데이트
     */
    public void updateLastActivityView() {
        this.lastActivityViewDate = LocalDateTime.now();
    }
    
    @Override
    public Long getId() {
        return followId;
    }
}
```

---

## ⚡ 6. 성능 최적화 전략

### 복합 인덱스 DDL 추가
```sql
-- 클라이밍 기록 분석용 인덱스
CREATE INDEX idx_climb_user_success_date 
ON user_climbs(user_id, is_successful, climb_date DESC);

-- 루트별 성공률 계산용
CREATE INDEX idx_climb_route_success 
ON user_climbs(route_id, is_successful, attempt_count);

-- 팔로우 추천용 인덱스
CREATE INDEX idx_follow_mutual_activity 
ON user_follows(is_mutual, is_active, last_interaction_date DESC);

-- 신발 추천용 인덱스
CREATE INDEX idx_user_shoe_rating_size 
ON user_climbing_shoes(shoe_id, review_rating DESC, shoe_size);

-- 등급별 통계용 인덱스
CREATE INDEX idx_level_difficulty_stats 
ON climbing_levels(difficulty_score, climbing_type, is_active);
```

### 통계 정보 계산 쿼리 예시
```java
// Repository에서 사용할 통계 쿼리들
@Query("SELECT COUNT(*) FROM UserClimb uc " +
       "WHERE uc.route.id = :routeId AND uc.isSuccessful = true")
long countSuccessfulClimbs(@Param("routeId") Long routeId);

@Query("SELECT AVG(uc.attemptCount) FROM UserClimb uc " +
       "WHERE uc.route.id = :routeId AND uc.isSuccessful = true")
Double getAverageAttempts(@Param("routeId") Long routeId);

@Query("SELECT uf.followingUser FROM UserFollow uf " +
       "WHERE uf.followerUser.id = :userId " +
       "AND uf.isActive = true AND uf.isMutual = true " +
       "ORDER BY uf.lastInteractionDate DESC")
List<User> findMutualFollows(@Param("userId") Long userId);
```

---

## ✅ 설계 완료 체크리스트

### 클라이밍 관련 엔티티 (3개)
- [x] **ClimbingLevel** - 등급 시스템 (V등급/YDS/프랑스 등급 통합)
- [x] **ClimbingShoe** - 신발 정보 (브랜드, 모델, 성능 지표)
- [x] **UserClimbingShoe** - 사용자 신발 (개인 평가, 사용 기록)

### 활동 추적 엔티티 (2개)
- [x] **UserClimb** - 클라이밍 기록 (상세 도전 기록, 성과 분석)
- [x] **UserFollow** - 팔로우 관계 (상호 팔로우, 활성도 추적)

### 전문 기능
- [x] 통합 등급 시스템 (V등급↔YDS↔프랑스 등급 매핑)
- [x] 신발 성능 평가 시스템 (에징, 스미어링, 후킹, 크랙)
- [x] 상세 클라이밍 기록 (플래시, 온사이트, 레드포인트)
- [x] 상호작용 기반 팔로우 시스템

### 성능 최적화
- [x] 사용자별 클라이밍 기록 조회 최적화
- [x] 팔로우 관계 검색 인덱스
- [x] 신발 추천 매칭 인덱스
- [x] 등급별 통계 계산 최적화

### 사용자 경험
- [x] 개인 기록 자동 인식
- [x] 신발 사용 후기 시스템
- [x] 친한 친구/파트너 분류
- [x] 상세 클라이밍 분석

---

**다음 단계**: Step 4-3d 커뮤니티 및 알림 시스템 엔티티 설계  
**완료일**: 2025-08-19  
**핵심 성과**: 5개 클라이밍/활동 엔티티 + 통합 등급 시스템 + 상세 기록 추적