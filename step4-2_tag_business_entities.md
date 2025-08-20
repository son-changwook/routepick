# Step 4-2: 태그 시스템 및 핵심 비즈니스 엔티티 설계

> 통합 태그 시스템, 암장/루트, 클라이밍 관련 엔티티 완전 설계  
> 생성일: 2025-08-19  
> 기반: step4-1_base_user_entities.md, step1-2_tag_system_analysis.md

---

## 🎯 설계 목표

- **통합 태그 시스템**: AI 기반 추천의 핵심, 8가지 TagType 지원
- **암장 및 루트 관리**: 한국 특화 지점 관리, 계층형 구조
- **클라이밍 전문 기능**: V등급/5.등급, 신발 관리, 난이도 시스템
- **성능 최적화**: Spatial Index, 복합 인덱스, N+1 문제 해결

---

## 🏷️ 1. 통합 태그 시스템 엔티티 (4개)

### TagType.java - 태그 유형 Enum
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 태그 유형 - 8가지 카테고리로 클라이밍 루트 특성 분류
 */
@Getter
@RequiredArgsConstructor
public enum TagType {
    STYLE("스타일", "클라이밍 종목 구분", 1),
    FEATURE("특징", "루트/홀드의 물리적 특성", 2),
    TECHNIQUE("테크닉", "필요한 기술", 3),
    DIFFICULTY("난이도", "체감 난이도 표현", 4),
    MOVEMENT("무브먼트", "동작 스타일", 5),
    HOLD_TYPE("홀드 타입", "홀드 종류", 6),
    WALL_ANGLE("벽 각도", "벽면 기울기", 7),
    OTHER("기타", "기타 분류", 8);
    
    private final String displayName;
    private final String description;
    private final int sortOrder;
    
    /**
     * 사용자 선택 가능한 태그 타입 목록
     */
    public static List<TagType> getUserSelectableTypes() {
        return Arrays.asList(STYLE, TECHNIQUE, MOVEMENT, DIFFICULTY);
    }
    
    /**
     * 루트 태깅 가능한 태그 타입 목록
     */
    public static List<TagType> getRouteTaggableTypes() {
        return Arrays.stream(values())
            .filter(type -> type != OTHER)
            .collect(Collectors.toList());
    }
}
```

### PreferenceLevel.java - 선호도 Enum
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 태그 선호도 수준
 */
@Getter
@RequiredArgsConstructor
public enum PreferenceLevel {
    LOW("낮음", 30, "별로 좋아하지 않음"),
    MEDIUM("보통", 70, "평균적으로 선호"),
    HIGH("높음", 100, "매우 선호함");
    
    private final String displayName;
    private final int weight; // 추천 알고리즘 가중치
    private final String description;
    
    /**
     * 가중치 백분율 반환
     */
    public double getWeightPercentage() {
        return weight / 100.0;
    }
}
```

### SkillLevel.java - 숙련도 Enum
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 기술 숙련도 수준
 */
@Getter
@RequiredArgsConstructor
public enum SkillLevel {
    BEGINNER("초급자", 1, "태그 관련 기술을 처음 배우는 단계"),
    INTERMEDIATE("중급자", 2, "어느 정도 익숙한 단계"),
    ADVANCED("고급자", 3, "능숙하게 사용 가능한 단계"),
    EXPERT("전문가", 4, "해당 기술의 전문가 수준");
    
    private final String displayName;
    private final int level;
    private final String description;
    
    /**
     * 레벨 차이 계산
     */
    public int getDifference(SkillLevel other) {
        return Math.abs(this.level - other.level);
    }
}
```

### Tag.java - 마스터 태그 엔티티
```java
package com.routepick.domain.tag.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.TagType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * 마스터 태그 엔티티
 * - 8가지 TagType 지원
 * - 추천 알고리즘의 핵심
 */
@Entity
@Table(name = "tags", indexes = {
    @Index(name = "idx_tags_type_order", columnList = "tag_type, display_order"),
    @Index(name = "idx_tags_user_selectable", columnList = "is_user_selectable, tag_type"),
    @Index(name = "idx_tags_route_taggable", columnList = "is_route_taggable, tag_type"),
    @Index(name = "idx_tags_name", columnList = "tag_name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Tag extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long tagId;
    
    @NotBlank
    @Size(max = 50)
    @Column(name = "tag_name", nullable = false, unique = true, length = 50)
    private String tagName;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false, length = 20)
    private TagType tagType;
    
    @Size(max = 50)
    @Column(name = "tag_category", length = 50)
    private String tagCategory; // 태그의 세부 분류
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @NotNull
    @Column(name = "is_user_selectable", nullable = false)
    @ColumnDefault("true")
    private boolean isUserSelectable = true; // 사용자가 선호 태그로 선택 가능
    
    @NotNull
    @Column(name = "is_route_taggable", nullable = false)
    @ColumnDefault("true")
    private boolean isRouteTaggable = true; // 루트에 태깅 가능
    
    @NotNull
    @Column(name = "display_order", nullable = false)
    @ColumnDefault("0")
    private Integer displayOrder = 0; // UI 표시 순서
    
    @Column(name = "usage_count")
    @ColumnDefault("0")
    private Integer usageCount = 0; // 사용 빈도 (통계용)
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 사용 횟수 증가
     */
    public void incrementUsageCount() {
        this.usageCount = (usageCount == null ? 0 : usageCount) + 1;
    }
    
    /**
     * 양방향 태그 여부 (사용자 선택 + 루트 태깅 모두 가능)
     */
    @Transient
    public boolean isBidirectional() {
        return isUserSelectable && isRouteTaggable;
    }
    
    /**
     * 태그 타입별 기본 표시 순서 설정
     */
    public void setDefaultDisplayOrder() {
        if (displayOrder == null || displayOrder == 0) {
            this.displayOrder = tagType.getSortOrder() * 100;
        }
    }
    
    @Override
    public Long getId() {
        return tagId;
    }
}
```

### UserPreferredTag.java - 사용자 선호 태그
```java
package com.routepick.domain.tag.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.PreferenceLevel;
import com.routepick.common.enums.SkillLevel;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * 사용자 선호 태그 엔티티
 * - User ↔ Tag 다대다 관계
 * - 추천 알고리즘의 기준 데이터
 */
@Entity
@Table(name = "user_preferred_tags", indexes = {
    @Index(name = "idx_user_preferred_user_pref", columnList = "user_id, preference_level"),
    @Index(name = "idx_user_preferred_tag", columnList = "tag_id"),
    @Index(name = "idx_user_preferred_skill", columnList = "user_id, skill_level"),
    @Index(name = "uk_user_tag", columnList = "user_id, tag_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserPreferredTag extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_tag_id")
    private Long userTagId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "preference_level", nullable = false, length = 20)
    @ColumnDefault("'MEDIUM'")
    private PreferenceLevel preferenceLevel = PreferenceLevel.MEDIUM;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", length = 20)
    @ColumnDefault("'BEGINNER'")
    private SkillLevel skillLevel = SkillLevel.BEGINNER;
    
    @Column(name = "experience_months")
    private Integer experienceMonths; // 해당 태그 경험 개월 수
    
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true; // 활성 선호도 여부
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 추천 점수 가중치 계산
     */
    @Transient
    public double getRecommendationWeight() {
        if (!isActive) return 0.0;
        return preferenceLevel.getWeightPercentage();
    }
    
    /**
     * 선호도 레벨 업그레이드
     */
    public void upgradePreference() {
        switch (preferenceLevel) {
            case LOW -> preferenceLevel = PreferenceLevel.MEDIUM;
            case MEDIUM -> preferenceLevel = PreferenceLevel.HIGH;
            case HIGH -> { /* 이미 최고 레벨 */ }
        }
    }
    
    /**
     * 스킬 레벨 향상
     */
    public void improveSkill() {
        switch (skillLevel) {
            case BEGINNER -> skillLevel = SkillLevel.INTERMEDIATE;
            case INTERMEDIATE -> skillLevel = SkillLevel.ADVANCED;
            case ADVANCED -> skillLevel = SkillLevel.EXPERT;
            case EXPERT -> { /* 이미 최고 레벨 */ }
        }
        
        // 경험 개월 수도 함께 증가
        if (experienceMonths != null) {
            experienceMonths += 6;
        }
    }
    
    /**
     * 선호도 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    @Override
    public Long getId() {
        return userTagId;
    }
}
```

### RouteTag.java - 루트 태깅 시스템
```java
package com.routepick.domain.tag.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

/**
 * 루트 태깅 시스템 엔티티
 * - Route ↔ Tag 다대다 관계
 * - relevance_score로 태그 적절성 측정
 */
@Entity
@Table(name = "route_tags", indexes = {
    @Index(name = "idx_route_tags_route_score", columnList = "route_id, relevance_score DESC"),
    @Index(name = "idx_route_tags_tag", columnList = "tag_id"),
    @Index(name = "idx_route_tags_creator", columnList = "created_by"),
    @Index(name = "uk_route_tag", columnList = "route_id, tag_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteTag extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_tag_id")
    private Long routeTagId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;
    
    @NotNull
    @DecimalMin(value = "0.0", message = "연관성 점수는 0.0 이상이어야 합니다")
    @DecimalMax(value = "1.0", message = "연관성 점수는 1.0 이하여야 합니다")
    @Column(name = "relevance_score", precision = 3, scale = 2, nullable = false)
    @ColumnDefault("1.00")
    private BigDecimal relevanceScore = BigDecimal.ONE;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy; // 태그를 생성한 사용자 (품질 관리용)
    
    @Column(name = "vote_count")
    @ColumnDefault("0")
    private Integer voteCount = 0; // 태그에 대한 투표 수
    
    @Column(name = "positive_vote_count")
    @ColumnDefault("0")
    private Integer positiveVoteCount = 0; // 긍정 투표 수
    
    @Column(name = "is_verified", nullable = false)
    @ColumnDefault("false")
    private boolean isVerified = false; // 관리자 검증 여부
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 투표 추가
     */
    public void addVote(boolean isPositive) {
        this.voteCount = (voteCount == null ? 0 : voteCount) + 1;
        if (isPositive) {
            this.positiveVoteCount = (positiveVoteCount == null ? 0 : positiveVoteCount) + 1;
        }
        
        // 투표 결과에 따른 연관성 점수 자동 조정
        updateRelevanceScore();
    }
    
    /**
     * 투표 결과 기반 연관성 점수 업데이트
     */
    private void updateRelevanceScore() {
        if (voteCount == null || voteCount == 0) return;
        
        double positiveRatio = (double) positiveVoteCount / voteCount;
        
        // 투표 비율에 따른 점수 조정 (0.5 ~ 1.0 범위)
        BigDecimal newScore = BigDecimal.valueOf(0.5 + (positiveRatio * 0.5))
            .setScale(2, BigDecimal.ROUND_HALF_UP);
        
        this.relevanceScore = newScore;
    }
    
    /**
     * 관리자 검증
     */
    public void verify() {
        this.isVerified = true;
    }
    
    /**
     * 점수 기반 품질 등급 반환
     */
    @Transient
    public String getQualityGrade() {
        double score = relevanceScore.doubleValue();
        if (score >= 0.9) return "EXCELLENT";
        if (score >= 0.7) return "GOOD";
        if (score >= 0.5) return "FAIR";
        return "POOR";
    }
    
    /**
     * 추천 알고리즘용 가중 점수 계산
     */
    @Transient
    public double getWeightedScore(double preferenceWeight) {
        return relevanceScore.doubleValue() * preferenceWeight;
    }
    
    @Override
    public Long getId() {
        return routeTagId;
    }
}
```

### UserRouteRecommendation.java - 개인화 추천 결과
```java
package com.routepick.domain.tag.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 개인화 추천 결과 엔티티
 * - 추천 알고리즘 결과 캐싱
 * - 태그 매칭 70% + 레벨 매칭 30%
 */
@Entity
@Table(name = "user_route_recommendations", indexes = {
    @Index(name = "idx_user_recommendations_score", columnList = "user_id, recommendation_score DESC"),
    @Index(name = "idx_user_recommendations_active", columnList = "user_id, is_active"),
    @Index(name = "idx_user_recommendations_calculated", columnList = "calculated_at DESC"),
    @Index(name = "uk_user_route_recommendation", columnList = "user_id, route_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserRouteRecommendation extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long recommendationId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @NotNull
    @DecimalMin(value = "0.0", message = "추천 점수는 0.0 이상이어야 합니다")
    @DecimalMax(value = "100.0", message = "추천 점수는 100.0 이하여야 합니다")
    @Column(name = "recommendation_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal recommendationScore;
    
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    @Column(name = "tag_match_score", precision = 5, scale = 2)
    private BigDecimal tagMatchScore; // 태그 매칭 점수 (70% 가중치)
    
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    @Column(name = "level_match_score", precision = 5, scale = 2)
    private BigDecimal levelMatchScore; // 레벨 매칭 점수 (30% 가중치)
    
    @NotNull
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
    
    @NotNull
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "match_tag_count")
    private Integer matchTagCount; // 매칭된 태그 개수
    
    @Column(name = "total_user_tags")
    private Integer totalUserTags; // 사용자 전체 선호 태그 개수
    
    @Column(name = "algorithm_version", length = 10)
    @ColumnDefault("'1.0'")
    private String algorithmVersion = "1.0"; // 추천 알고리즘 버전
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 최종 추천 점수 계산 (태그 70% + 레벨 30%)
     */
    public void calculateFinalScore() {
        if (tagMatchScore != null && levelMatchScore != null) {
            BigDecimal tagWeight = tagMatchScore.multiply(BigDecimal.valueOf(0.7));
            BigDecimal levelWeight = levelMatchScore.multiply(BigDecimal.valueOf(0.3));
            
            this.recommendationScore = tagWeight.add(levelWeight)
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        }
    }
    
    /**
     * 태그 매칭률 계산
     */
    @Transient
    public Double getTagMatchRatio() {
        if (totalUserTags == null || totalUserTags == 0) return 0.0;
        if (matchTagCount == null) return 0.0;
        
        return (double) matchTagCount / totalUserTags;
    }
    
    /**
     * 추천 품질 등급
     */
    @Transient
    public String getRecommendationGrade() {
        double score = recommendationScore.doubleValue();
        if (score >= 80.0) return "EXCELLENT";
        if (score >= 60.0) return "GOOD";
        if (score >= 40.0) return "FAIR";
        if (score >= 20.0) return "POOR";
        return "VERY_POOR";
    }
    
    /**
     * 추천 만료 여부 확인 (24시간 기준)
     */
    @Transient
    public boolean isExpired() {
        return calculatedAt.isBefore(LocalDateTime.now().minusHours(24));
    }
    
    /**
     * 추천 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * 추천 갱신
     */
    public void refresh(BigDecimal newTagScore, BigDecimal newLevelScore, 
                       Integer newMatchCount, Integer newTotalTags) {
        this.tagMatchScore = newTagScore;
        this.levelMatchScore = newLevelScore;
        this.matchTagCount = newMatchCount;
        this.totalUserTags = newTotalTags;
        this.calculatedAt = LocalDateTime.now();
        this.isActive = true;
        
        calculateFinalScore();
    }
    
    @Override
    public Long getId() {
        return recommendationId;
    }
}
```

---

## 🏢 2. 암장 및 루트 엔티티 (12개)

### Gym.java - 암장 마스터 정보
```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * 암장 마스터 정보 엔티티
 * - 암장 체인점 관리
 * - 1:N 관계로 여러 지점 보유
 */
@Entity
@Table(name = "gyms", indexes = {
    @Index(name = "idx_gyms_name", columnList = "gym_name"),
    @Index(name = "idx_gyms_business", columnList = "business_registration_number", unique = true),
    @Index(name = "idx_gyms_status", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Gym extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gym_id")
    private Long gymId;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "gym_name", nullable = false, length = 100)
    private String gymName;
    
    @Size(max = 12)
    @Column(name = "business_registration_number", unique = true, length = 12)
    private String businessRegistrationNumber; // 한국 사업자등록번호
    
    @Size(max = 50)
    @Column(name = "ceo_name", length = 50)
    private String ceoName;
    
    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;
    
    @Size(max = 500)
    @Column(name = "website_url", length = 500)
    private String websiteUrl;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @NotBlank
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    // 연관관계
    @OneToMany(mappedBy = "gym", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GymBranch> branches = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 지점 추가
     */
    public void addBranch(GymBranch branch) {
        branches.add(branch);
        branch.setGym(this);
    }
    
    /**
     * 활성 지점 수 조회
     */
    @Transient
    public int getActiveBranchCount() {
        return (int) branches.stream()
            .filter(GymBranch::isActive)
            .count();
    }
    
    /**
     * 암장 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        // 모든 지점도 비활성화
        branches.forEach(GymBranch::deactivate);
    }
    
    @Override
    public Long getId() {
        return gymId;
    }
}
```

### GymBranch.java - 암장 지점 정보
```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 암장 지점 정보 엔티티
 * - 한국 특화: 좌표계, 주소 체계
 * - Spatial Index 준비
 */
@Entity
@Table(name = "gym_branches", indexes = {
    @Index(name = "idx_branches_location", columnList = "latitude, longitude"),
    @Index(name = "idx_branches_address", columnList = "address"),
    @Index(name = "idx_branches_active", columnList = "is_active"),
    @Index(name = "idx_branches_gym", columnList = "gym_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GymBranch extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long branchId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "branch_name", nullable = false, length = 100)
    private String branchName;
    
    @NotBlank
    @Size(max = 200)
    @Column(name = "address", nullable = false, length = 200)
    private String address;
    
    @Size(max = 200)
    @Column(name = "detailed_address", length = 200)
    private String detailedAddress;
    
    // 한국 좌표계 (WGS84) - Spatial Index 적용 예정
    @NotNull
    @DecimalMin(value = "33.0", message = "위도는 33.0 이상이어야 합니다 (한국 최남단)")
    @DecimalMax(value = "38.5", message = "위도는 38.5 이하여야 합니다 (한국 최북단)")
    @Column(name = "latitude", precision = 10, scale = 8, nullable = false)
    private BigDecimal latitude;
    
    @NotNull
    @DecimalMin(value = "125.0", message = "경도는 125.0 이상이어야 합니다 (한국 최서단)")
    @DecimalMax(value = "132.0", message = "경도는 132.0 이하여야 합니다 (한국 최동단)")
    @Column(name = "longitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal longitude;
    
    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @Column(name = "operating_hours", length = 100)
    private String operatingHours;
    
    @Column(name = "day_pass_price")
    private Integer dayPassPrice; // 일일 이용료
    
    @Column(name = "monthly_pass_price")
    private Integer monthlyPassPrice; // 월 이용료
    
    @Column(name = "shoe_rental_price")
    private Integer shoeRentalPrice; // 신발 대여비
    
    @NotBlank
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "parking_available")
    @ColumnDefault("false")
    private boolean parkingAvailable = false;
    
    @Column(name = "shower_available")
    @ColumnDefault("false")
    private boolean showerAvailable = false;
    
    @Column(name = "wifi_available")
    @ColumnDefault("false")
    private boolean wifiAvailable = false;
    
    // 연관관계
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Wall> walls = new ArrayList<>();
    
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BranchImage> images = new ArrayList<>();
    
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GymMember> members = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 거리 계산 (Haversine 공식) - km 단위
     */
    @Transient
    public double calculateDistance(BigDecimal targetLat, BigDecimal targetLon) {
        double lat1 = latitude.doubleValue();
        double lon1 = longitude.doubleValue();
        double lat2 = targetLat.doubleValue();
        double lon2 = targetLon.doubleValue();
        
        final int R = 6371; // 지구 반지름 (km)
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * 활성 벽면 수 조회
     */
    @Transient
    public int getActiveWallCount() {
        return (int) walls.stream()
            .filter(Wall::isActive)
            .count();
    }
    
    /**
     * 지점 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        // 모든 벽면도 비활성화
        walls.forEach(Wall::deactivate);
    }
    
    /**
     * 전체 주소 반환
     */
    @Transient
    public String getFullAddress() {
        if (detailedAddress != null && !detailedAddress.trim().isEmpty()) {
            return address + " " + detailedAddress;
        }
        return address;
    }
    
    @Override
    public Long getId() {
        return branchId;
    }
}
```

### GymMember.java - 암장 회원 관리
```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.MembershipType;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;

/**
 * 암장 회원 관리 엔티티
 * - User ↔ GymBranch 다대다 관계
 * - 회원권 종류별 관리
 */
@Entity
@Table(name = "gym_members", indexes = {
    @Index(name = "idx_gym_members_user", columnList = "user_id"),
    @Index(name = "idx_gym_members_branch", columnList = "branch_id"),
    @Index(name = "idx_gym_members_active", columnList = "is_active"),
    @Index(name = "idx_gym_members_expiry", columnList = "membership_end_date"),
    @Index(name = "uk_user_branch", columnList = "user_id, branch_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GymMember extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "membership_type", nullable = false, length = 20)
    private MembershipType membershipType;
    
    @NotNull
    @Column(name = "membership_start_date", nullable = false)
    private LocalDate membershipStartDate;
    
    @NotNull
    @Column(name = "membership_end_date", nullable = false)
    private LocalDate membershipEndDate;
    
    @Column(name = "payment_amount")
    private Integer paymentAmount;
    
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "auto_renewal")
    @ColumnDefault("false")
    private boolean autoRenewal = false;
    
    @Column(name = "membership_number", length = 50)
    private String membershipNumber; // 암장별 회원번호
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes; // 특이사항
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 회원권 만료 여부 확인
     */
    @Transient
    public boolean isExpired() {
        return LocalDate.now().isAfter(membershipEndDate);
    }
    
    /**
     * 잔여 일수 계산
     */
    @Transient
    public long getRemainingDays() {
        LocalDate now = LocalDate.now();
        if (now.isAfter(membershipEndDate)) return 0;
        return now.until(membershipEndDate).getDays();
    }
    
    /**
     * 회원권 연장
     */
    public void extendMembership(int months) {
        this.membershipEndDate = membershipEndDate.plusMonths(months);
        this.isActive = true;
    }
    
    /**
     * 회원권 해지
     */
    public void cancel() {
        this.isActive = false;
        this.autoRenewal = false;
    }
    
    /**
     * 자동 갱신 활성화
     */
    public void enableAutoRenewal() {
        this.autoRenewal = true;
    }
    
    @Override
    public Long getId() {
        return memberId;
    }
}
```

### Wall.java - 벽면 정보
```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.WallType;
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
 * 벽면 정보 엔티티
 * - 암장의 개별 벽면 관리
 * - 경사각, 높이 등 물리적 특성
 */
@Entity
@Table(name = "walls", indexes = {
    @Index(name = "idx_walls_branch", columnList = "branch_id"),
    @Index(name = "idx_walls_type", columnList = "wall_type"),
    @Index(name = "idx_walls_angle", columnList = "wall_angle"),
    @Index(name = "idx_walls_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Wall extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wall_id")
    private Long wallId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;
    
    @NotBlank
    @Column(name = "wall_name", nullable = false, length = 50)
    private String wallName;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "wall_type", nullable = false, length = 20)
    private WallType wallType;
    
    @Min(value = -30, message = "벽 각도는 -30도 이상이어야 합니다")
    @Max(value = 180, message = "벽 각도는 180도 이하여야 합니다")
    @Column(name = "wall_angle")
    private Integer wallAngle; // 벽면 경사각 (도 단위)
    
    @Column(name = "wall_height")
    private Double wallHeight; // 벽 높이 (미터)
    
    @Column(name = "wall_width")
    private Double wallWidth; // 벽 너비 (미터)
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "route_capacity")
    private Integer routeCapacity; // 동시 설치 가능 루트 수
    
    @Column(name = "color", length = 7)
    private String color; // 벽면 색상 (HEX)
    
    // 연관관계
    @OneToMany(mappedBy = "wall", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
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
     * 루트 용량 여유분 확인
     */
    @Transient
    public int getAvailableCapacity() {
        if (routeCapacity == null) return Integer.MAX_VALUE;
        return Math.max(0, routeCapacity - getActiveRouteCount());
    }
    
    /**
     * 벽면 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        // 모든 루트도 비활성화
        routes.forEach(Route::deactivate);
    }
    
    /**
     * 벽면 각도 분류 반환
     */
    @Transient
    public String getAngleCategory() {
        if (wallAngle == null) return "UNKNOWN";
        
        if (wallAngle <= -10) return "OVERHANG_SEVERE";
        if (wallAngle <= 0) return "OVERHANG";
        if (wallAngle <= 15) return "SLAB";
        if (wallAngle <= 30) return "VERTICAL";
        if (wallAngle <= 45) return "STEEP";
        return "ROOF";
    }
    
    /**
     * 벽면 면적 계산
     */
    @Transient
    public Double getWallArea() {
        if (wallHeight == null || wallWidth == null) return null;
        return wallHeight * wallWidth;
    }
    
    @Override
    public Long getId() {
        return wallId;
    }
}
```

### BranchImage.java - 암장 이미지
```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.ImageType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * 암장 지점 이미지 엔티티
 * - AWS S3 연동
 * - 이미지 타입별 분류
 */
@Entity
@Table(name = "branch_images", indexes = {
    @Index(name = "idx_branch_images_branch", columnList = "branch_id"),
    @Index(name = "idx_branch_images_type", columnList = "image_type"),
    @Index(name = "idx_branch_images_order", columnList = "branch_id, display_order")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BranchImage extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 20)
    private ImageType imageType;
    
    @NotBlank
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;
    
    @Column(name = "original_filename", length = 255)
    private String originalFilename;
    
    @Column(name = "file_size")
    private Long fileSize; // 바이트 단위
    
    @Column(name = "image_width")
    private Integer imageWidth; // 픽셀
    
    @Column(name = "image_height")
    private Integer imageHeight; // 픽셀
    
    @Column(name = "display_order")
    @ColumnDefault("0")
    private Integer displayOrder = 0;
    
    @Column(name = "alt_text", length = 200)
    private String altText; // 접근성을 위한 대체 텍스트
    
    @Column(name = "caption", length = 500)
    private String caption; // 이미지 설명
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 파일 크기를 사람이 읽기 쉬운 형태로 변환
     */
    @Transient
    public String getFormattedFileSize() {
        if (fileSize == null) return "Unknown";
        
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }
    
    /**
     * 이미지 비율 계산
     */
    @Transient
    public Double getAspectRatio() {
        if (imageWidth == null || imageHeight == null || imageHeight == 0) return null;
        return (double) imageWidth / imageHeight;
    }
    
    /**
     * 썸네일 URL 반환 (없으면 원본 반환)
     */
    @Transient
    public String getDisplayUrl() {
        return thumbnailUrl != null ? thumbnailUrl : imageUrl;
    }
    
    @Override
    public Long getId() {
        return imageId;
    }
}
```

### Route.java - 클라이밍 루트 정보
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

### RouteSetter.java - 루트 세터 정보
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

### RouteImage.java & RouteVideo.java - 미디어 관리
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

### RouteComment.java - 루트 댓글 시스템
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

### RouteDifficultyVote.java - 난이도 투표 시스템
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

### RouteScrap.java - 루트 스크랩 기능
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

## 🧗‍♀️ 3. 클라이밍 관련 엔티티 (3개)

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

## ⚡ 4. 성능 최적화 설정

### 필수 Enum 클래스들
```java
// MembershipType.java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MembershipType {
    DAY_PASS("일일 이용권", 1),
    WEEK_PASS("주간 이용권", 7),
    MONTH_PASS("월 이용권", 30),
    QUARTER_PASS("3개월 이용권", 90),
    YEAR_PASS("연간 이용권", 365);
    
    private final String displayName;
    private final int validDays;
}

// WallType.java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WallType {
    VERTICAL("수직벽", 90),
    SLAB("슬랩", 105),
    OVERHANG("오버행", 75),
    ROOF("루프", 0),
    MULTI_ANGLE("복합각도", -1);
    
    private final String displayName;
    private final int defaultAngle; // -1은 가변각도
}

// ImageType.java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageType {
    MAIN("대표이미지", true),
    GALLERY("갤러리", false),
    INTERIOR("내부전경", false),
    EQUIPMENT("시설장비", false),
    ROUTE("루트사진", false);
    
    private final String displayName;
    private final boolean isMainImage;
}

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

### Spatial Index 설정 (MySQL 8.0+)
```sql
-- gym_branches 테이블에 공간 인덱스 추가 (추후 적용)
ALTER TABLE gym_branches ADD COLUMN location POINT NOT NULL;
UPDATE gym_branches SET location = POINT(longitude, latitude);
ALTER TABLE gym_branches ADD SPATIAL INDEX idx_branches_spatial_location (location);

-- 거리 기반 검색 쿼리 예시
-- DELIMITER //
-- CREATE PROCEDURE FindNearbyBranches(
--     IN user_lat DECIMAL(10,8), 
--     IN user_lon DECIMAL(11,8), 
--     IN radius_km INT
-- )
-- BEGIN
--     SELECT 
--         b.*,
--         ST_Distance_Sphere(
--             POINT(user_lon, user_lat),
--             b.location
--         ) / 1000 AS distance_km
--     FROM gym_branches b
--     WHERE ST_Distance_Sphere(
--             POINT(user_lon, user_lat),
--             b.location
--         ) / 1000 <= radius_km
--     ORDER BY distance_km;
-- END //
-- DELIMITER ;
```

### @EntityGraph 준비 (N+1 문제 해결)
```java
// Repository 예시 - RouteRepository.java
package com.routepick.domain.route.repository;

import com.routepick.domain.route.entity.Route;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {
    
    /**
     * 루트 상세 조회시 연관 엔티티 함께 로드
     */
    @EntityGraph(attributePaths = {"wall", "level", "setter", "routeTags.tag"})
    Optional<Route> findByIdWithDetails(Long routeId);
    
    /**
     * 벽면별 루트 목록 조회
     */
    @EntityGraph(attributePaths = {"level", "setter"})
    List<Route> findByWallIdAndIsActiveTrue(Long wallId);
    
    /**
     * 인기 루트 조회 (태그 정보 포함)
     */
    @Query("SELECT r FROM Route r " +
           "LEFT JOIN FETCH r.routeTags rt " +
           "LEFT JOIN FETCH rt.tag " +
           "WHERE r.isActive = true " +
           "ORDER BY r.popularityScore DESC")
    List<Route> findPopularRoutesWithTags(@Param("limit") int limit);
}
```

### 복합 인덱스 최적화 가이드
```sql
-- 추천 시스템 성능 최적화 인덱스
ALTER TABLE user_route_recommendations 
ADD INDEX idx_recommendations_complex (user_id, is_active, recommendation_score DESC, calculated_at);

-- 태그 매칭 최적화 인덱스  
ALTER TABLE route_tags 
ADD INDEX idx_route_tags_complex (route_id, relevance_score DESC, is_verified);

-- 사용자 선호 태그 최적화 인덱스
ALTER TABLE user_preferred_tags 
ADD INDEX idx_user_tags_complex (user_id, is_active, preference_level);

-- 루트 검색 최적화 인덱스
ALTER TABLE routes 
ADD INDEX idx_routes_search_complex (wall_id, is_active, popularity_score DESC, set_date DESC);
```

---

## 📊 엔티티 관계 요약

### 🏷️ 태그 시스템 (핵심)
```
Tag (1) ←→ (N) UserPreferredTag (N) ←→ (1) User
Tag (1) ←→ (N) RouteTag (N) ←→ (1) Route  
User (1) ←→ (N) UserRouteRecommendation (N) ←→ (1) Route
```

### 🏢 암장/루트 계층 구조
```
Gym (1) ←→ (N) GymBranch (1) ←→ (N) Wall (1) ←→ (N) Route
GymBranch (1) ←→ (N) BranchImage
GymBranch (1) ←→ (N) GymMember (N) ←→ (1) User
Route (1) ←→ (N) RouteImage/RouteVideo/RouteComment
```

### 🧗‍♀️ 클라이밍 전문 영역
```
ClimbingLevel (1) ←→ (N) Route
ClimbingShoe (1) ←→ (N) UserClimbingShoe (N) ←→ (1) User
RouteSetter (1) ←→ (N) Route
```

---

## 🎯 다음 단계 (5단계)

1. **Repository 레이어** (QueryDSL + JPA)
2. **Service 레이어** (비즈니스 로직)
3. **DTO 설계** (API 계층)
4. **Controller 구현** (REST API)
5. **테스트 코드** (단위 + 통합)

---

*Step 4-2 완료: 총 19개 엔티티 설계 완성*  
*태그 시스템(4) + 암장/루트(12) + 클라이밍(3) = **19개 엔티티***  
*다음: Repository 레이어 구현*

<function_calls>
<invoke name="TodoWrite">
<parameter name="todos">[{"id": "1", "content": "step4-1\uacfc step1-2 \ud30c\uc77c \uc77d\uae30", "status": "completed"}, {"id": "2", "content": "\ud1b5\ud569 \ud0dc\uadf8 \uc2dc\uc2a4\ud15c \uc5d4\ud2f0\ud2f0 \uc124\uacc4 (4\uac1c)", "status": "completed"}, {"id": "3", "content": "\uc554\uc7a5 \ubc0f \ub8e8\ud2b8 \uc5d4\ud2f0\ud2f0 \uc124\uacc4 (12\uac1c)", "status": "in_progress"}, {"id": "4", "content": "\ud074\ub77c\uc774\ubc0d \uad00\ub828 \uc5d4\ud2f0\ud2f0 \uc124\uacc4 (3\uac1c)", "status": "pending"}, {"id": "5", "content": "\uc131\ub2a5 \ucd5c\uc801\ud654 \uc124\uc815 \uc801\uc6a9", "status": "pending"}, {"id": "6", "content": "step4-2_tag_business_entities.md \ud30c\uc77c \uc0dd\uc131", "status": "pending"}]