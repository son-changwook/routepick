# Step 4-2a: 통합 태그 시스템 엔티티

> AI 기반 추천의 핵심인 통합 태그 시스템 엔티티 완전 설계  
> 생성일: 2025-08-20  
> 분할: step4-2_tag_business_entities.md → 태그 시스템 부분 추출  
> 기반: step4-1_base_user_entities.md, step1-2_tag_system_analysis.md

---

## 🎯 태그 시스템 설계 목표

- **통합 태그 시스템**: AI 기반 추천의 핵심, 8가지 TagType 지원
- **사용자 선호도**: 3단계 선호도 × 4단계 숙련도 매트릭스
- **루트 태깅**: 투표 기반 품질 관리 시스템
- **추천 알고리즘**: 태그 매칭 70% + 레벨 매칭 30%

---

## 🏷️ 통합 태그 시스템 엔티티 (4개)

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

## ✅ 태그 시스템 엔티티 완료 체크리스트

### 🏷️ 핵심 Enum 클래스 (3개)
- [x] **TagType**: 8가지 태그 카테고리 (STYLE ~ OTHER)
- [x] **PreferenceLevel**: 3단계 선호도 (LOW 30% ~ HIGH 100%)
- [x] **SkillLevel**: 4단계 숙련도 (BEGINNER ~ EXPERT)

### 📊 태그 마스터 엔티티 (1개)
- [x] **Tag**: 마스터 태그 (4개 인덱스, 사용자 선택/루트 태깅 플래그)
  - 태그명 UNIQUE 제약
  - 표시 순서 관리
  - 사용 빈도 통계
  - 양방향 태그 지원

### 🤝 관계 엔티티 (3개)
- [x] **UserPreferredTag**: User ↔ Tag 다대다 관계
  - 선호도 × 숙련도 매트릭스  
  - 경험 개월 수 추적
  - 선호도 업그레이드 로직
- [x] **RouteTag**: Route ↔ Tag 다대다 관계
  - 연관성 점수 (0.0~1.0)
  - 투표 기반 품질 관리
  - 자동 점수 조정 알고리즘
- [x] **UserRouteRecommendation**: 개인화 추천 결과 캐싱
  - 태그 매칭 70% + 레벨 매칭 30%
  - 24시간 TTL 만료 체크
  - 추천 품질 등급 (5단계)

### 🎯 비즈니스 로직 특징
- [x] **추천 알고리즘**: 가중치 기반 점수 계산 시스템
- [x] **품질 관리**: 투표 기반 태그 품질 자동 조정
- [x] **성장 시스템**: 선호도/숙련도 단계별 성장 로직
- [x] **캐싱 전략**: 추천 결과 24시간 캐시 + 만료 체크
- [x] **통계 기능**: 태그 사용 빈도 및 매칭률 계산

### 🔍 인덱스 최적화
- [x] **Tag**: 4개 인덱스 (타입별, 선택가능성, 태깅가능성, 이름)
- [x] **UserPreferredTag**: 4개 인덱스 (사용자별, 태그별, 숙련도, UK)
- [x] **RouteTag**: 4개 인덱스 (루트별 점수, 태그별, 생성자별, UK)
- [x] **UserRouteRecommendation**: 4개 인덱스 (점수별, 활성화, 계산시간, UK)

---

*분할 작업 1/3 완료: 통합 태그 시스템 엔티티 (4개)*  
*다음 파일: step4-2b_gym_route_entities.md*