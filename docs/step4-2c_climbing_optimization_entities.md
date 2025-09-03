# Step 4-2c: 클라이밍 최적화 엔티티

> 클라이밍 전문 엔티티 및 성능 최적화 설정  
> 생성일: 2025-08-20  
> 분할: step4-2_tag_business_entities.md → 클라이밍 부분 추출  
> 기반: step1-2_tag_system_analysis.md, 성능 최적화 가이드

---

## 🧗‍♀️ 클라이밍 관련 엔티티 (3개)

### ClimbingLevel.java - 클라이밍 등급 시스템
```java
package com.routepick.domain.climb.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.GradeSystem;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * 클라이밍 등급 시스템 엔티티
 * - V등급(볼더링), 5.등급(스포츠) 지원
 * - 한국 특화 등급 매핑
 */
@Entity
@Table(name = "climbing_levels", indexes = {
    @Index(name = "idx_levels_system_numeric", columnList = "grade_system, numeric_level"),
    @Index(name = "idx_levels_grade_text", columnList = "grade_text"),
    @Index(name = "idx_levels_korean", columnList = "korean_grade"),
    @Index(name = "uk_grade_system_text", columnList = "grade_system, grade_text", unique = true)
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
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "grade_system", nullable = false, length = 10)
    private GradeSystem gradeSystem; // V_SCALE, YDS_SCALE
    
    @NotBlank
    @Column(name = "grade_text", nullable = false, length = 10)
    private String gradeText; // V0, V1, 5.10a, 5.11d 등
    
    @Column(name = "korean_grade", length = 10)
    private String koreanGrade; // 한국식 표기 (선택사항)
    
    @NotNull
    @Min(value = 1, message = "수치 등급은 1 이상이어야 합니다")
    @Max(value = 50, message = "수치 등급은 50 이하여야 합니다")
    @Column(name = "numeric_level", nullable = false)
    private Integer numericLevel; // 비교용 수치 (V0=1, V1=2, 5.10a=15 등)
    
    @Column(name = "difficulty_description", length = 200)
    private String difficultyDescription; // 난이도 설명
    
    @Column(name = "beginner_friendly")
    @ColumnDefault("false")
    private boolean beginnerFriendly = false; // 초보자 친화적 여부
    
    @Column(name = "color_code", length = 7)
    private String colorCode; // UI 표시용 색상 (HEX)
    
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    // 연관관계
    @OneToMany(mappedBy = "level", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Route> routes = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 등급 간 거리 계산 (추천 알고리즘용)
     */
    public int calculateDistance(ClimbingLevel otherLevel) {
        if (otherLevel == null) return Integer.MAX_VALUE;
        if (!this.gradeSystem.equals(otherLevel.gradeSystem)) return Integer.MAX_VALUE;
        
        return Math.abs(this.numericLevel - otherLevel.numericLevel);
    }
    
    /**
     * 난이도 카테고리 반환
     */
    @Transient
    public String getDifficultyCategory() {
        if (gradeSystem == GradeSystem.V_SCALE) {
            if (numericLevel <= 3) return "BEGINNER"; // V0-V2
            if (numericLevel <= 6) return "INTERMEDIATE"; // V3-V5
            if (numericLevel <= 10) return "ADVANCED"; // V6-V9
            return "EXPERT"; // V10+
        } else {
            if (numericLevel <= 10) return "BEGINNER"; // 5.6-5.9
            if (numericLevel <= 20) return "INTERMEDIATE"; // 5.10a-5.11d
            if (numericLevel <= 30) return "ADVANCED"; // 5.12a-5.13d
            return "EXPERT"; // 5.14a+
        }
    }
    
    /**
     * 추천 매칭용 호환성 점수 (0-100)
     */
    public int getCompatibilityScore(ClimbingLevel targetLevel) {
        if (targetLevel == null || !gradeSystem.equals(targetLevel.gradeSystem)) return 0;
        
        int distance = calculateDistance(targetLevel);
        if (distance == 0) return 100; // 정확히 일치
        if (distance == 1) return 80; // 1등급 차이
        if (distance == 2) return 60; // 2등급 차이
        if (distance <= 3) return 40; // 3등급 차이
        if (distance <= 5) return 20; // 5등급 차이
        return 0; // 5등급 초과 차이
    }
    
    /**
     * 표시용 등급명 반환 (한국어 우선)
     */
    @Transient
    public String getDisplayGrade() {
        return koreanGrade != null && !koreanGrade.trim().isEmpty() ? koreanGrade : gradeText;
    }
    
    /**
     * V등급 여부 확인
     */
    @Transient
    public boolean isVScale() {
        return gradeSystem == GradeSystem.V_SCALE;
    }
    
    /**
     * 5.등급 여부 확인
     */
    @Transient
    public boolean isYdsScale() {
        return gradeSystem == GradeSystem.YDS_SCALE;
    }
    
    @Override
    public Long getId() {
        return levelId;
    }
}
```

### ClimbingShoe.java - 클라이밍 신발 마스터
```java
package com.routepick.domain.climb.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.ShoeType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 클라이밍 신발 마스터 엔티티
 * - 브랜드, 모델별 신발 관리
 * - 사이즈, 특성 정보
 */
@Entity
@Table(name = "climbing_shoes", indexes = {
    @Index(name = "idx_shoes_brand_model", columnList = "brand, model"),
    @Index(name = "idx_shoes_type", columnList = "shoe_type"),
    @Index(name = "idx_shoes_active", columnList = "is_active"),
    @Index(name = "uk_brand_model", columnList = "brand, model", unique = true)
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
    
    @NotBlank
    @Column(name = "brand", nullable = false, length = 50)
    private String brand; // La Sportiva, Scarpa, Five Ten 등
    
    @NotBlank
    @Column(name = "model", nullable = false, length = 100)
    private String model; // Solution, Instinct, Dragon 등
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "shoe_type", nullable = false, length = 20)
    private ShoeType shoeType; // AGGRESSIVE, MODERATE, COMFORT
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 신발 특징 설명
    
    @DecimalMin(value = "200", message = "최소 사이즈는 200mm입니다")
    @DecimalMax(value = "320", message = "최대 사이즈는 320mm입니다")
    @Column(name = "min_size_mm", precision = 5, scale = 1)
    private BigDecimal minSizeMm; // 최소 사이즈 (mm)
    
    @DecimalMin(value = "200", message = "최소 사이즈는 200mm입니다")
    @DecimalMax(value = "320", message = "최대 사이즈는 320mm입니다")
    @Column(name = "max_size_mm", precision = 5, scale = 1)
    private BigDecimal maxSizeMm; // 최대 사이즈 (mm)
    
    @Column(name = "closure_type", length = 20)
    private String closureType; // LACE, VELCRO, SLIP_ON
    
    @Column(name = "rubber_type", length = 30)
    private String rubberType; // Vibram XS Edge, XS Grip 등
    
    @Column(name = "downturn_degree")
    private Integer downturnDegree; // 다운턴 정도 (도 단위)
    
    @Column(name = "asymmetry_level")
    private Integer asymmetryLevel; // 비대칭 정도 (1-5)
    
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "5.0")
    @Column(name = "stiffness_rating", precision = 2, scale = 1)
    private BigDecimal stiffnessRating; // 신발 경도 (0.0-5.0)
    
    @Column(name = "price_range", length = 20)
    private String priceRange; // BUDGET, MID, PREMIUM
    
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "image_url", length = 500)
    private String imageUrl; // 신발 이미지
    
    @Column(name = "manufacturer_url", length = 500)
    private String manufacturerUrl; // 제조사 페이지
    
    // 연관관계
    @OneToMany(mappedBy = "shoe", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserClimbingShoe> userShoes = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 사이즈 범위 확인
     */
    public boolean isSizeAvailable(BigDecimal sizeMm) {
        if (minSizeMm == null || maxSizeMm == null) return false;
        return sizeMm.compareTo(minSizeMm) >= 0 && sizeMm.compareTo(maxSizeMm) <= 0;
    }
    
    /**
     * 신발 스타일 점수 계산 (공격성 기준)
     */
    @Transient
    public int getAggressivenessScore() {
        int score = 0;
        
        // 신발 타입 점수
        switch (shoeType) {
            case AGGRESSIVE -> score += 40;
            case MODERATE -> score += 20;
            case COMFORT -> score += 0;
        }
        
        // 다운턴 점수 (최대 30점)
        if (downturnDegree != null) {
            score += Math.min(30, downturnDegree * 3);
        }
        
        // 비대칭 점수 (최대 15점)
        if (asymmetryLevel != null) {
            score += asymmetryLevel * 3;
        }
        
        // 강성 점수 (최대 15점) - 역수 적용
        if (stiffnessRating != null) {
            score += (int) (15 * (5.0 - stiffnessRating.doubleValue()) / 5.0);
        }
        
        return Math.min(100, score);
    }
    
    /**
     * 추천 점수 계산 (사용자 선호도 기반)
     */
    public int calculateRecommendationScore(ShoeType preferredType, 
                                          BigDecimal userFootSize, 
                                          String preferredClosure) {
        int score = 0;
        
        // 타입 매칭 (40점)
        if (shoeType.equals(preferredType)) {
            score += 40;
        }
        
        // 사이즈 가용성 (30점)
        if (userFootSize != null && isSizeAvailable(userFootSize)) {
            score += 30;
        }
        
        // 클로저 타입 매칭 (20점)
        if (closureType != null && closureType.equalsIgnoreCase(preferredClosure)) {
            score += 20;
        }
        
        // 활성 상태 (10점)
        if (isActive) {
            score += 10;
        }
        
        return score;
    }
    
    /**
     * 사이즈 범위 텍스트 반환
     */
    @Transient
    public String getSizeRangeText() {
        if (minSizeMm == null || maxSizeMm == null) return "사이즈 정보 없음";
        return String.format("%.1fmm - %.1fmm", minSizeMm, maxSizeMm);
    }
    
    /**
     * 풀네임 반환 (브랜드 + 모델)
     */
    @Transient
    public String getFullName() {
        return brand + " " + model;
    }
    
    @Override
    public Long getId() {
        return shoeId;
    }
}
```

### UserClimbingShoe.java - 사용자 신발 관리
```java
package com.routepick.domain.climb.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.ShoeStatus;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 사용자 클라이밍 신발 관리 엔티티
 * - User ↔ ClimbingShoe 다대다 관계
 * - 개인별 신발 경험 관리
 */
@Entity
@Table(name = "user_climbing_shoes", indexes = {
    @Index(name = "idx_user_shoes_user", columnList = "user_id"),
    @Index(name = "idx_user_shoes_shoe", columnList = "shoe_id"),
    @Index(name = "idx_user_shoes_status", columnList = "shoe_status"),
    @Index(name = "idx_user_shoes_rating", columnList = "rating DESC"),
    @Index(name = "uk_user_shoe", columnList = "user_id, shoe_id", unique = true)
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
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shoe_id", nullable = false)
    private ClimbingShoe shoe;
    
    @NotNull
    @DecimalMin(value = "200", message = "신발 사이즈는 200mm 이상이어야 합니다")
    @DecimalMax(value = "320", message = "신발 사이즈는 320mm 이하여야 합니다")
    @Column(name = "user_size_mm", precision = 5, scale = 1, nullable = false)
    private BigDecimal userSizeMm; // 사용자가 신는 사이즈
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "shoe_status", nullable = false, length = 20)
    private ShoeStatus shoeStatus; // OWNED, TRIED, WANT_TO_TRY, SOLD
    
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;
    
    @Column(name = "purchase_price")
    private Integer purchasePrice; // 구매가격 (원)
    
    @Min(value = 1, message = "평점은 1 이상이어야 합니다")
    @Max(value = 5, message = "평점은 5 이하여야 합니다")
    @Column(name = "rating")
    private Integer rating; // 1-5 별점
    
    @Column(name = "review", columnDefinition = "TEXT")
    private String review; // 개인 리뷰
    
    @Column(name = "usage_months")
    private Integer usageMonths; // 사용 개월 수
    
    @Column(name = "recommended_for", length = 200)
    private String recommendedFor; // 추천 용도 (볼더링, 리드, 멀티피치 등)
    
    @Column(name = "fit_rating")
    private Integer fitRating; // 핏 만족도 (1-5)
    
    @Column(name = "comfort_rating")
    private Integer comfortRating; // 편안함 만족도 (1-5)
    
    @Column(name = "performance_rating")
    private Integer performanceRating; // 성능 만족도 (1-5)
    
    @Column(name = "durability_rating")
    private Integer durabilityRating; // 내구성 만족도 (1-5)
    
    @Column(name = "size_advice", length = 500)
    private String sizeAdvice; // 사이즈 조언 (다른 사용자들을 위한)
    
    @Column(name = "is_recommended")
    @ColumnDefault("true")
    private boolean isRecommended = true; // 다른 사용자에게 추천 여부
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 전체 만족도 계산 (평균)
     */
    @Transient
    public Double getOverallSatisfaction() {
        if (fitRating == null || comfortRating == null || 
            performanceRating == null || durabilityRating == null) {
            return null;
        }
        
        return (fitRating + comfortRating + performanceRating + durabilityRating) / 4.0;
    }
    
    /**
     * 신발 상태가 소유 중인지 확인
     */
    @Transient
    public boolean isOwned() {
        return shoeStatus == ShoeStatus.OWNED;
    }
    
    /**
     * 리뷰 완성도 점수 (0-100)
     */
    @Transient
    public int getReviewCompleteness() {
        int score = 0;
        
        if (rating != null) score += 20;
        if (review != null && !review.trim().isEmpty()) score += 20;
        if (fitRating != null) score += 15;
        if (comfortRating != null) score += 15;
        if (performanceRating != null) score += 15;
        if (durabilityRating != null) score += 15;
        
        return score;
    }
    
    /**
     * 사이즈 조언 있음 여부
     */
    @Transient
    public boolean hasSizeAdvice() {
        return sizeAdvice != null && !sizeAdvice.trim().isEmpty();
    }
    
    /**
     * 신발 사용 경험이 충분한지 확인 (추천 신뢰도용)
     */
    @Transient
    public boolean hasSignificantExperience() {
        return usageMonths != null && usageMonths >= 3; // 3개월 이상 사용
    }
    
    /**
     * 구매한 신발인지 확인
     */
    @Transient
    public boolean isPurchased() {
        return purchaseDate != null || purchasePrice != null;
    }
    
    /**
     * 사용 기간 텍스트 반환
     */
    @Transient
    public String getUsagePeriodText() {
        if (usageMonths == null) return "사용 기간 미입력";
        
        if (usageMonths < 12) {
            return usageMonths + "개월";
        } else {
            int years = usageMonths / 12;
            int months = usageMonths % 12;
            
            if (months == 0) {
                return years + "년";
            } else {
                return years + "년 " + months + "개월";
            }
        }
    }
    
    /**
     * 추천 가중치 계산 (다른 사용자 추천시 신뢰도)
     */
    @Transient
    public double getRecommendationWeight() {
        if (!isRecommended) return 0.0;
        
        double weight = 0.5; // 기본 가중치
        
        // 사용 경험 보너스
        if (hasSignificantExperience()) weight += 0.3;
        
        // 리뷰 완성도 보너스
        weight += (getReviewCompleteness() / 100.0) * 0.2;
        
        return Math.min(1.0, weight);
    }
    
    @Override
    public Long getId() {
        return userShoeId;
    }
}
```

---

## ⚡ 성능 최적화 설정

### 필수 Enum 클래스들
```java
// GradeSystem.java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GradeSystem {
    V_SCALE("V등급", "볼더링 전용"),
    YDS_SCALE("5.등급", "스포츠/트래드");
    
    private final String displayName;
    private final String description;
}

// ShoeType.java  
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShoeType {
    AGGRESSIVE("공격적", "오버행, 어려운 루트"),
    MODERATE("중간", "다양한 루트 타입"),
    COMFORT("편안함", "장시간 착용, 초보자");
    
    private final String displayName;
    private final String recommendedFor;
}

// ShoeStatus.java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShoeStatus {
    OWNED("보유중", "현재 소유하고 있음"),
    TRIED("체험함", "한번 신어봤음"),
    WANT_TO_TRY("체험희망", "신어보고 싶음"),
    SOLD("판매함", "소유했다가 판매함");
    
    private final String displayName;
    private final String description;
}
```

### @EntityGraph 준비 (N+1 문제 해결)
```java
// Repository 예시 - ClimbingLevelRepository.java
package com.routepick.domain.climb.repository;

import com.routepick.domain.climb.entity.ClimbingLevel;
import com.routepick.common.enums.GradeSystem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClimbingLevelRepository extends JpaRepository<ClimbingLevel, Long> {
    
    /**
     * 활성 등급 조회 (루트 개수 포함)
     */
    @EntityGraph(attributePaths = {"routes"})
    List<ClimbingLevel> findByIsActiveTrueOrderByNumericLevel();
    
    /**
     * 등급 시스템별 조회
     */
    List<ClimbingLevel> findByGradeSystemAndIsActiveTrueOrderByNumericLevel(GradeSystem gradeSystem);
    
    /**
     * 수치 범위로 등급 조회 (추천 알고리즘용)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.gradeSystem = :gradeSystem " +
           "AND cl.numericLevel BETWEEN :minLevel AND :maxLevel " +
           "AND cl.isActive = true " +
           "ORDER BY cl.numericLevel")
    List<ClimbingLevel> findByNumericLevelRange(
        @Param("gradeSystem") GradeSystem gradeSystem,
        @Param("minLevel") Integer minLevel,
        @Param("maxLevel") Integer maxLevel
    );
}

// ClimbingShoeRepository.java
package com.routepick.domain.climb.repository;

import com.routepick.domain.climb.entity.ClimbingShoe;
import com.routepick.common.enums.ShoeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ClimbingShoeRepository extends JpaRepository<ClimbingShoe, Long> {
    
    /**
     * 브랜드별 활성 신발 조회
     */
    List<ClimbingShoe> findByBrandAndIsActiveTrueOrderByModel(String brand);
    
    /**
     * 신발 타입별 조회
     */
    List<ClimbingShoe> findByShoeTypeAndIsActiveTrueOrderByBrandAscModelAsc(ShoeType shoeType);
    
    /**
     * 사이즈 범위로 신발 검색
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true " +
           "AND :userSize BETWEEN cs.minSizeMm AND cs.maxSizeMm " +
           "ORDER BY cs.brand, cs.model")
    List<ClimbingShoe> findBySizeRange(@Param("userSize") BigDecimal userSize);
    
    /**
     * 신발 추천 검색 (복합 조건)
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true " +
           "AND (:shoeType IS NULL OR cs.shoeType = :shoeType) " +
           "AND (:userSize IS NULL OR :userSize BETWEEN cs.minSizeMm AND cs.maxSizeMm) " +
           "AND (:closureType IS NULL OR cs.closureType = :closureType) " +
           "ORDER BY cs.brand, cs.model")
    List<ClimbingShoe> findRecommendedShoes(
        @Param("shoeType") ShoeType shoeType,
        @Param("userSize") BigDecimal userSize,
        @Param("closureType") String closureType
    );
}
```

### 복합 인덱스 최적화 가이드
```sql
-- 클라이밍 레벨 최적화 인덱스
ALTER TABLE climbing_levels 
ADD INDEX idx_levels_recommend_complex (grade_system, is_active, numeric_level);

-- 신발 검색 최적화 인덱스  
ALTER TABLE climbing_shoes 
ADD INDEX idx_shoes_search_complex (is_active, shoe_type, min_size_mm, max_size_mm);

-- 사용자 신발 리뷰 최적화 인덱스
ALTER TABLE user_climbing_shoes 
ADD INDEX idx_user_shoes_review_complex (shoe_status, rating DESC, usage_months DESC);

-- 신발 추천 시스템 최적화 인덱스
ALTER TABLE user_climbing_shoes 
ADD INDEX idx_user_shoes_recommend_complex (shoe_id, is_recommended, rating DESC);
```

### 캐싱 전략
```java
@Service
@Transactional(readOnly = true)
public class ClimbingLevelService {
    
    @Cacheable(value = "climbing-levels", key = "'v-scale'")
    public List<ClimbingLevel> getVScaleLevels() {
        return climbingLevelRepository
            .findByGradeSystemAndIsActiveTrueOrderByNumericLevel(GradeSystem.V_SCALE);
    }
    
    @Cacheable(value = "climbing-levels", key = "'yds-scale'")
    public List<ClimbingLevel> getYdsScaleLevels() {
        return climbingLevelRepository
            .findByGradeSystemAndIsActiveTrueOrderByNumericLevel(GradeSystem.YDS_SCALE);
    }
    
    @CacheEvict(value = "climbing-levels", allEntries = true)
    public void refreshLevelCache() {
        // 관리자가 등급 체계 수정시 캐시 무효화
    }
}

@Service
@Transactional(readOnly = true)
public class ClimbingShoeService {
    
    @Cacheable(value = "climbing-shoes", key = "'brands'")
    public List<String> getAllBrands() {
        return climbingShoeRepository.findDistinctBrands();
    }
    
    @Cacheable(value = "climbing-shoes", key = "#brand")
    public List<ClimbingShoe> getShoesByBrand(String brand) {
        return climbingShoeRepository.findByBrandAndIsActiveTrueOrderByModel(brand);
    }
}
```

### 통계 쿼리 최적화
```java
@Repository
public class ClimbingStatsRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    /**
     * 등급별 루트 분포 통계
     */
    public List<LevelDistributionDto> getLevelDistribution() {
        String jpql = """
            SELECT new com.routepick.dto.LevelDistributionDto(
                cl.gradeSystem,
                cl.gradeText,
                cl.numericLevel,
                COUNT(r.id)
            )
            FROM ClimbingLevel cl
            LEFT JOIN cl.routes r
            WHERE cl.isActive = true
            AND (r.isActive = true OR r.isActive IS NULL)
            GROUP BY cl.id, cl.gradeSystem, cl.gradeText, cl.numericLevel
            ORDER BY cl.gradeSystem, cl.numericLevel
            """;
            
        return entityManager.createQuery(jpql, LevelDistributionDto.class)
                           .getResultList();
    }
    
    /**
     * 신발 브랜드별 인기도 통계
     */
    public List<ShoeBrandStatsDto> getBrandPopularityStats() {
        String jpql = """
            SELECT new com.routepick.dto.ShoeBrandStatsDto(
                cs.brand,
                COUNT(DISTINCT cs.id) as modelCount,
                COUNT(DISTINCT ucs.id) as userCount,
                AVG(ucs.rating) as avgRating
            )
            FROM ClimbingShoe cs
            LEFT JOIN cs.userShoes ucs
            WHERE cs.isActive = true
            AND (ucs.shoeStatus = 'OWNED' OR ucs.shoeStatus IS NULL)
            GROUP BY cs.brand
            HAVING modelCount > 0
            ORDER BY userCount DESC, avgRating DESC
            """;
            
        return entityManager.createQuery(jpql, ShoeBrandStatsDto.class)
                           .getResultList();
    }
}
```

---

## 🎯 클라이밍 시스템 완료 체크리스트

### 🧗‍♀️ 클라이밍 엔티티 (3개)
- [x] **ClimbingLevel**: 등급 시스템 (V등급 + 5.등급)
  - V0~V17, 5.6~5.15d 지원
  - 한국식 표기 병행 지원
  - 추천 알고리즘용 호환성 점수 계산
  - 난이도 카테고리 자동 분류 (4단계)

- [x] **ClimbingShoe**: 신발 마스터 데이터
  - 브랜드 × 모델 조합 관리
  - 신발 특성 상세 정보 (다운턴, 비대칭, 강성 등)
  - 사이즈 범위 관리 (200-320mm)
  - 공격성 점수 자동 계산 시스템

- [x] **UserClimbingShoe**: 사용자 신발 경험 관리
  - 소유/체험/희망/판매 상태 관리
  - 5가지 만족도 평가 (핏/편안함/성능/내구성/종합)
  - 사이즈 조언 및 추천 시스템
  - 사용 경험 기반 신뢰도 가중치

### ⚡ 성능 최적화 준비
- [x] **복합 인덱스**: 검색/추천 성능 최적화
- [x] **@EntityGraph**: N+1 문제 해결 준비
- [x] **캐싱 전략**: 등급 시스템 및 브랜드 목록
- [x] **통계 쿼리**: 등급 분포 및 브랜드 인기도

### 🎯 비즈니스 로직 특징
- [x] **등급 호환성**: 시스템 간 거리 계산 (추천용)
- [x] **신발 추천**: 타입/사이즈/클로저 매칭 점수
- [x] **경험 가중치**: 사용 기간 기반 신뢰도 계산
- [x] **한국 특화**: 등급 표기법 및 사이즈 검증

### 🔍 인덱스 최적화
- [x] **ClimbingLevel**: 4개 인덱스 (시스템별, 수치별, 텍스트별, UK)
- [x] **ClimbingShoe**: 4개 인덱스 (브랜드별, 타입별, 활성별, UK)
- [x] **UserClimbingShoe**: 5개 인덱스 (사용자별, 신발별, 상태별, 평점별, UK)

---

*분할 작업 3/3 완료: 클라이밍 최적화 엔티티 (3개)*  
*전체 완료: step4-2_tag_business_entities.md → 3파일 분할 완성*  
*다음 파일: step4-4b_payment_notification.md (2,483라인)*

*설계 완료일: 2025-08-20*