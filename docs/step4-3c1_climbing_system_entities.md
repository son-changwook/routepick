# Step 4-3c1: 클라이밍 시스템 엔티티 설계

> **RoutePickr 클라이밍 시스템** - 등급 시스템, 신발 데이터베이스, 사용자 장비 관리  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-3c1 (JPA 엔티티 50개 - 클라이밍 시스템 3개)  
> **분할**: step4-3c_climbing_activity_entities.md → 클라이밍 시스템 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 클라이밍 시스템**을 담고 있습니다.

### 🎯 주요 특징
- **통합 등급 시스템**: V등급, YDS, 프랑스 등급 매핑 및 난이도 점수
- **신발 데이터베이스**: 브랜드별 모델, 성능 지표, 사이즈 매칭
- **개인 장비 관리**: 신발 컬렉션, 사용 후기, 성능 평가
- **전문가 데이터**: 클라이밍 특화 성능 분석 및 추천

### 📊 엔티티 목록 (3개)
1. **ClimbingLevel** - 클라이밍 등급 시스템 (V/YDS/프랑스 통합)
2. **ClimbingShoe** - 클라이밍 신발 (브랜드, 모델, 성능)
3. **UserClimbingShoe** - 사용자 신발 (개인 평가, 사용 기록)

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
    
    // ===== 등급 표기 시스템 =====
    
    @Size(max = 10, message = "V 등급은 최대 10자입니다")
    @Column(name = "v_grade", length = 10)
    private String vGrade; // V0, V1, V2, ..., V17+
    
    @Size(max = 10, message = "YDS 등급은 최대 10자입니다")
    @Column(name = "yds_grade", length = 10)
    private String ydsGrade; // 5.6, 5.7, 5.8, ..., 5.15d
    
    @Size(max = 10, message = "프랑스 등급은 최대 10자입니다")
    @Column(name = "french_grade", length = 10)
    private String frenchGrade; // 4a, 4b, 4c, ..., 9c+
    
    @Size(max = 10, message = "영국 등급은 최대 10자입니다")
    @Column(name = "uk_grade", length = 10)
    private String ukGrade; // 4a, 5a, 5b, ..., E11
    
    // ===== 난이도 점수 시스템 =====
    
    @NotNull
    @DecimalMin(value = "0.0", message = "난이도 점수는 0.0 이상이어야 합니다")
    @DecimalMax(value = "1000.0", message = "난이도 점수는 1000.0 이하여야 합니다")
    @Column(name = "difficulty_score", precision = 6, scale = 2, nullable = false)
    private BigDecimal difficultyScore; // 0.00 ~ 1000.00 점수
    
    @Size(max = 30, message = "클라이밍 타입은 최대 30자입니다")
    @Column(name = "climbing_type", length = 30)
    private String climbingType; // BOULDER, SPORT, TRAD, MIXED, ICE
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 등급 여부
    
    // ===== 등급 설명 및 특징 =====
    
    @Size(max = 500, message = "등급 설명은 최대 500자입니다")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 등급에 대한 설명
    
    @Size(max = 200, message = "특징은 최대 200자입니다")
    @Column(name = "characteristics", length = 200)
    private String characteristics; // 해당 등급의 특징
    
    @Size(max = 100, message = "예상 기간은 최대 100자입니다")
    @Column(name = "expected_time_to_reach", length = 100)
    private String expectedTimeToReach; // 도달 예상 기간
    
    // ===== 통계 정보 =====
    
    @Min(value = 0, message = "사용자 수는 0 이상이어야 합니다")
    @Column(name = "user_count")
    private Integer userCount = 0; // 이 등급을 사용하는 사용자 수
    
    @Min(value = 0, message = "루트 수는 0 이상이어야 합니다")
    @Column(name = "route_count")
    private Integer routeCount = 0; // 이 등급의 루트 수
    
    @DecimalMin(value = "0.0", message = "성공률은 0.0 이상이어야 합니다")
    @DecimalMax(value = "100.0", message = "성공률은 100.0 이하여야 합니다")
    @Column(name = "average_success_rate", precision = 5, scale = 2)
    private BigDecimal averageSuccessRate; // 평균 성공률 (%)
    
    @Min(value = 0, message = "평균 시도 횟수는 0 이상이어야 합니다")
    @Column(name = "average_attempts")
    private Integer averageAttempts; // 평균 시도 횟수
    
    // ===== 색상 및 표시 정보 =====
    
    @Size(max = 7, message = "색상 코드는 최대 7자입니다")
    @Column(name = "color_code", length = 7)
    private String colorCode; // 등급별 색상 (#FF0000 형식)
    
    @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다")
    @Max(value = 1000, message = "표시 순서는 1000 이하여야 합니다")
    @Column(name = "display_order")
    private Integer displayOrder; // 표시 순서
    
    @Column(name = "icon_name", length = 50)
    private String iconName; // 아이콘 이름
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 클라이밍 타입 한글 표시
     */
    @Transient
    public String getClimbingTypeKorean() {
        if (climbingType == null) return "일반";
        
        return switch (climbingType) {
            case "BOULDER" -> "볼더링";
            case "SPORT" -> "스포츠 클라이밍";
            case "TRAD" -> "전통 클라이밍";
            case "MIXED" -> "믹스 클라이밍";
            case "ICE" -> "아이스 클라이밍";
            default -> "일반";
        };
    }
    
    /**
     * 메인 등급 표기 반환 (우선순위: V > YDS > French)
     */
    @Transient
    public String getPrimaryGrade() {
        if (vGrade != null && !vGrade.isEmpty()) return vGrade;
        if (ydsGrade != null && !ydsGrade.isEmpty()) return ydsGrade;
        if (frenchGrade != null && !frenchGrade.isEmpty()) return frenchGrade;
        return "N/A";
    }
    
    /**
     * 전체 등급 표기 (V등급/YDS/프랑스)
     */
    @Transient
    public String getFullGradeDisplay() {
        StringBuilder sb = new StringBuilder();
        
        if (vGrade != null && !vGrade.isEmpty()) {
            sb.append("V").append(vGrade);
        }
        
        if (ydsGrade != null && !ydsGrade.isEmpty()) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append("5.").append(ydsGrade);
        }
        
        if (frenchGrade != null && !frenchGrade.isEmpty()) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(frenchGrade);
        }
        
        return sb.length() > 0 ? sb.toString() : "N/A";
    }
    
    /**
     * 난이도 등급 반환 (점수 기준)
     */
    @Transient
    public String getDifficultyLevel() {
        if (difficultyScore == null) return "알 수 없음";
        
        double score = difficultyScore.doubleValue();
        
        if (score < 100) return "초급";
        if (score < 300) return "중급";
        if (score < 500) return "고급";
        if (score < 700) return "전문가";
        return "엘리트";
    }
    
    /**
     * 등급 비교 (점수 기준)
     */
    public int compareTo(ClimbingLevel other) {
        if (this.difficultyScore == null && other.difficultyScore == null) return 0;
        if (this.difficultyScore == null) return -1;
        if (other.difficultyScore == null) return 1;
        
        return this.difficultyScore.compareTo(other.difficultyScore);
    }
    
    /**
     * 다음 등급까지의 점수 차이
     */
    @Transient
    public BigDecimal getScoreGapToNext(ClimbingLevel nextLevel) {
        if (nextLevel == null || nextLevel.difficultyScore == null) return null;
        if (this.difficultyScore == null) return null;
        
        return nextLevel.difficultyScore.subtract(this.difficultyScore);
    }
    
    /**
     * 통계 업데이트
     */
    public void updateStatistics(int newUserCount, int newRouteCount, 
                               BigDecimal newSuccessRate, int newAttempts) {
        this.userCount = newUserCount;
        this.routeCount = newRouteCount;
        this.averageSuccessRate = newSuccessRate;
        this.averageAttempts = newAttempts;
    }
    
    @Override
    public Long getId() {
        return levelId;
    }
}
```

---

## 👟 2. ClimbingShoe 엔티티 - 클라이밍 신발

```java
package com.routepick.domain.climb.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 클라이밍 신발 정보
 * - 브랜드별 모델 정보
 * - 성능 지표 및 특성
 */
@Entity
@Table(name = "climbing_shoes", indexes = {
    @Index(name = "idx_shoe_brand_model", columnList = "brand, model_name"),
    @Index(name = "idx_shoe_type", columnList = "shoe_type"),
    @Index(name = "idx_shoe_rating", columnList = "average_rating DESC"),
    @Index(name = "idx_shoe_price", columnList = "price_range"),
    @Index(name = "idx_shoe_active", columnList = "is_active, is_discontinued")
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
    @Size(min = 1, max = 50, message = "브랜드명은 1-50자 사이여야 합니다")
    @Column(name = "brand", nullable = false, length = 50)
    private String brand; // La Sportiva, Scarpa, Five Ten 등
    
    @NotNull
    @Size(min = 1, max = 100, message = "모델명은 1-100자 사이여야 합니다")
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName; // Solution, Instinct, Anasazi 등
    
    @Size(max = 500, message = "설명은 최대 500자입니다")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 제품 설명
    
    @Size(max = 30, message = "신발 타입은 최대 30자입니다")
    @Column(name = "shoe_type", length = 30)
    private String shoeType; // AGGRESSIVE, MODERATE, COMFORT, CRACK, SLAB
    
    // ===== 사이즈 정보 =====
    
    @DecimalMin(value = "35.0", message = "최소 사이즈는 35.0이어야 합니다")
    @DecimalMax(value = "50.0", message = "최대 사이즈는 50.0이어야 합니다")
    @Column(name = "min_size", precision = 4, scale = 1)
    private java.math.BigDecimal minSize; // 35.0 (EU 사이즈)
    
    @DecimalMin(value = "35.0", message = "최대 사이즈는 35.0 이상이어야 합니다")
    @DecimalMax(value = "50.0", message = "최대 사이즈는 50.0이어야 합니다")
    @Column(name = "max_size", precision = 4, scale = 1)
    private java.math.BigDecimal maxSize; // 47.5 (EU 사이즈)
    
    @DecimalMin(value = "0.0", message = "늘어남 정도는 0.0 이상이어야 합니다")
    @DecimalMax(value = "3.0", message = "늘어남 정도는 3.0 이하여야 합니다")
    @Column(name = "stretch_potential", precision = 3, scale = 1)
    private java.math.BigDecimal stretchPotential; // 0.5 (신발이 늘어나는 정도)
    
    @Column(name = "half_size_available", nullable = false)
    private boolean halfSizeAvailable = true; // 하프 사이즈 제공 여부
    
    // ===== 가격 정보 =====
    
    @Min(value = 0, message = "가격은 0 이상이어야 합니다")
    @Column(name = "price_range", length = 20)
    private String priceRange; // "150000-200000" (원화 기준)
    
    @Column(name = "currency", length = 5)
    private String currency = "KRW"; // 통화 단위
    
    // ===== 성능 지표 (1-5점) =====
    
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
        if (stretchPotential == null) stretchPotential = new java.math.BigDecimal("0.5");
        
        float recommendedSize = userFootSize - stretchPotential.floatValue();
        
        if ("AGGRESSIVE".equals(shoeType)) {
            recommendedSize -= 1.0f; // 어그레시브는 더 타이트하게
        } else if ("COMFORT".equals(shoeType)) {
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
    
    // ===== 구매 및 소유 정보 =====
    
    @NotNull
    @DecimalMin(value = "35.0", message = "신발 사이즈는 35.0 이상이어야 합니다")
    @DecimalMax(value = "50.0", message = "신발 사이즈는 50.0 이하여야 합니다")
    @Column(name = "shoe_size", precision = 4, scale = 1, nullable = false)
    private java.math.BigDecimal shoeSize; // 실제 구매한 사이즈 (EU 기준)
    
    @Column(name = "purchase_date")
    private LocalDate purchaseDate; // 구매일
    
    @Column(name = "purchase_price")
    private Integer purchasePrice; // 구매 가격
    
    @Column(name = "purchase_location", length = 100)
    private String purchaseLocation; // 구매처
    
    @Column(name = "is_currently_owned", nullable = false)
    private boolean isCurrentlyOwned = true; // 현재 소유 여부
    
    @Column(name = "ownership_status", length = 20)
    private String ownershipStatus = "ACTIVE"; // ACTIVE, SOLD, LOST, RETIRED
    
    // ===== 사용 기록 =====
    
    @Column(name = "first_use_date")
    private LocalDate firstUseDate; // 첫 사용일
    
    @Column(name = "last_use_date")
    private LocalDate lastUseDate; // 마지막 사용일
    
    @Min(value = 0, message = "사용 횟수는 0 이상이어야 합니다")
    @Column(name = "use_count")
    private Integer useCount = 0; // 사용 횟수
    
    @Min(value = 0, message = "총 사용 시간은 0 이상이어야 합니다")
    @Column(name = "total_climb_hours")
    private Integer totalClimbHours = 0; // 총 클라이밍 시간 (시간)
    
    // ===== 개인 평가 (1-5점) =====
    
    @Min(value = 1, message = "평점은 1 이상이어야 합니다")
    @Max(value = 5, message = "평점은 5 이하여야 합니다")
    @Column(name = "review_rating")
    private Integer reviewRating; // 전체 평점 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "comfort_rating")
    private Integer comfortRating; // 편안함 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "performance_rating")
    private Integer performanceRating; // 성능 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "durability_rating")
    private Integer durabilityRating; // 내구성 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "value_rating")
    private Integer valueRating; // 가성비 (1-5)
    
    // ===== 맞춤 정보 =====
    
    @Column(name = "fit_feedback", length = 30)
    private String fitFeedback; // TOO_SMALL, PERFECT, TOO_BIG
    
    @Size(max = 1000, message = "후기는 최대 1000자입니다")
    @Column(name = "review_text", columnDefinition = "TEXT")
    private String reviewText; // 사용 후기
    
    @Column(name = "recommended_for", length = 200)
    private String recommendedFor; // 추천 용도
    
    @Column(name = "pros", columnDefinition = "TEXT")
    private String pros; // 장점
    
    @Column(name = "cons", columnDefinition = "TEXT")
    private String cons; // 단점
    
    // ===== 상태 정보 =====
    
    @Column(name = "current_condition", length = 20)
    private String currentCondition; // NEW, GOOD, FAIR, WORN, RETIRED
    
    @Column(name = "resoled_count")
    private Integer resoledCount = 0; // 리솔링 횟수
    
    @Column(name = "last_resole_date")
    private LocalDate lastResoleDate; // 마지막 리솔링 날짜
    
    @Column(name = "maintenance_notes", columnDefinition = "TEXT")
    private String maintenanceNotes; // 관리 메모
    
    // ===== 공개 설정 =====
    
    @Column(name = "is_review_public", nullable = false)
    private boolean isReviewPublic = true; // 후기 공개 여부
    
    @Column(name = "allow_recommendations", nullable = false)
    private boolean allowRecommendations = true; // 추천 허용 여부
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 맞춤 피드백 한글 표시
     */
    @Transient
    public String getFitFeedbackKorean() {
        if (fitFeedback == null) return "적당함";
        
        return switch (fitFeedback) {
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
     * 시간당 사용 빈도 계산
     */
    @Transient
    public float getUsageFrequency() {
        if (totalClimbHours == null || totalClimbHours == 0) return 0.0f;
        if (useCount == null || useCount == 0) return 0.0f;
        
        return (float) totalClimbHours / useCount;
    }
    
    /**
     * 가성비 점수 계산
     */
    @Transient
    public float getValueScore() {
        if (purchasePrice == null || purchasePrice == 0) return 0.0f;
        if (totalClimbHours == null || totalClimbHours == 0) return 0.0f;
        
        return (float) totalClimbHours / (purchasePrice / 10000.0f); // 만원당 시간
    }
    
    /**
     * 사용 기록 업데이트
     */
    public void recordUse(int hoursUsed) {
        this.useCount = (useCount == null ? 0 : useCount) + 1;
        this.totalClimbHours = (totalClimbHours == null ? 0 : totalClimbHours) + hoursUsed;
        this.lastUseDate = LocalDate.now();
        
        if (firstUseDate == null) {
            this.firstUseDate = LocalDate.now();
        }
    }
    
    /**
     * 리솔링 기록
     */
    public void recordResole() {
        this.resoledCount = (resoledCount == null ? 0 : resoledCount) + 1;
        this.lastResoleDate = LocalDate.now();
        this.currentCondition = "GOOD"; // 리솔링 후 상태 개선
    }
    
    /**
     * 후기 업데이트
     */
    public void updateReview(int rating, String reviewText, String pros, String cons) {
        this.reviewRating = rating;
        this.reviewText = reviewText;
        this.pros = pros;
        this.cons = cons;
    }
    
    /**
     * 신발 상태 업데이트
     */
    public void updateCondition(String newCondition, String maintenanceNote) {
        this.currentCondition = newCondition;
        this.maintenanceNotes = maintenanceNote;
        
        // 은퇴 처리
        if ("RETIRED".equals(newCondition)) {
            this.isCurrentlyOwned = false;
            this.ownershipStatus = "RETIRED";
        }
    }
    
    /**
     * 신발 판매 처리
     */
    public void sellShoe(String location) {
        this.isCurrentlyOwned = false;
        this.ownershipStatus = "SOLD";
        this.purchaseLocation = location; // 판매처로 업데이트
    }
    
    @Override
    public Long getId() {
        return userShoeId;
    }
}
```

---

## ✅ 설계 완료 체크리스트

### 클라이밍 시스템 엔티티 (3개)
- [x] **ClimbingLevel** - 클라이밍 등급 시스템 (V/YDS/프랑스 통합 매핑)
- [x] **ClimbingShoe** - 클라이밍 신발 (브랜드별 모델, 성능 지표)
- [x] **UserClimbingShoe** - 사용자 신발 (개인 평가, 사용 기록)

### 통합 등급 시스템
- [x] V등급, YDS, 프랑스 등급 통합 매핑
- [x] 난이도 점수 기반 정량적 비교
- [x] 등급별 통계 (성공률, 평균 시도 횟수)
- [x] 클라이밍 타입별 분류 (볼더링, 스포츠, 전통)

### 신발 데이터베이스 시스템
- [x] 브랜드별 모델 정보 및 사양
- [x] 성능 지표 (에징, 스미어링, 후킹, 크랙)
- [x] 사이즈 매핑 및 늘어남 정도
- [x] 가격 정보 및 인기도 점수

### 개인 장비 관리
- [x] 신발 컬렉션 관리 및 소유 상태
- [x] 사용 후기 및 5단계 평가 시스템
- [x] 사용 기록 추적 (시간, 횟수, 기간)
- [x] 리솔링 기록 및 유지보수 관리

### 전문가 기능
- [x] 맞춤 사이즈 추천 알고리즘
- [x] 사용 패턴 분석 및 가성비 계산
- [x] 신발별 장단점 및 추천 용도
- [x] 공개/비공개 후기 설정

---

**다음 단계**: step4-3c2_user_activity_entities.md (사용자 활동 엔티티)  
**완료일**: 2025-08-20  
**핵심 성과**: 3개 클라이밍 시스템 엔티티 + 통합 등급 시스템 + 신발 데이터베이스 완성