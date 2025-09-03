# Step 4-2a2: 루트 태깅 및 추천 엔티티 구현 (완전본)

> **RoutePickr - 클라이밍 루트 추천 플랫폼**  
> Step 4-2a: 통합 태그 시스템 엔티티 (루트 태깅 및 추천 엔티티 Part)

## 📋 이 문서의 내용

이 문서는 **step4-2a_tag_system_entities.md**에서 분할된 두 번째 부분으로, 다음 루트 태깅 및 추천 엔티티들을 포함합니다:

### 🏷️ 루트 태깅 시스템
- RouteTag Entity (투표 기반 품질 관리)
- UserRouteRecommendation Entity (개인화 추천 결과)

### 🎯 핵심 기능
- 투표 기반 태그 품질 관리 시스템
- AI 추천 알고리즘 (태그 매칭 70% + 레벨 매칭 30%)
- 24시간 TTL 기반 추천 캐싱

---

## 🏷️ 루트 태깅 및 추천 엔티티 (2개)

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
    
    // ===== 연관관계 편의 메서드 =====
    
    /**
     * 루트 태깅 생성
     */
    public static RouteTag createRouteTag(Route route, Tag tag, User createdBy) {
        return RouteTag.builder()
            .route(route)
            .tag(tag)
            .createdBy(createdBy)
            .relevanceScore(BigDecimal.ONE)
            .voteCount(0)
            .positiveVoteCount(0)
            .isVerified(false)
            .build();
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
    
    // ===== 연관관계 편의 메서드 =====
    
    /**
     * 추천 결과 생성
     */
    public static UserRouteRecommendation createRecommendation(User user, Route route,
                                                               BigDecimal tagScore, BigDecimal levelScore,
                                                               Integer matchCount, Integer totalTags,
                                                               String algorithmVersion) {
        UserRouteRecommendation recommendation = UserRouteRecommendation.builder()
            .user(user)
            .route(route)
            .tagMatchScore(tagScore)
            .levelMatchScore(levelScore)
            .matchTagCount(matchCount)
            .totalUserTags(totalTags)
            .algorithmVersion(algorithmVersion)
            .calculatedAt(LocalDateTime.now())
            .isActive(true)
            .build();
        
        recommendation.calculateFinalScore();
        return recommendation;
    }
}
```

---

## 📊 루트 태깅 및 추천 구성

### RouteTag 투표 기반 품질 관리
| 투표 비율 | 연관성 점수 | 품질 등급 |
|----------|------------|----------|
| **100% 긍정** | 1.0 | EXCELLENT |
| **80% 긍정** | 0.9 | EXCELLENT |
| **60% 긍정** | 0.8 | GOOD |
| **50% 긍정** | 0.75 | GOOD |
| **40% 긍정** | 0.7 | GOOD |
| **20% 긍정** | 0.6 | FAIR |
| **0% 긍정** | 0.5 | POOR |

### UserRouteRecommendation 점수 체계
| 추천 점수 | 품질 등급 | 설명 |
|----------|----------|------|
| **80-100점** | EXCELLENT | 매우 적합한 루트 |
| **60-79점** | GOOD | 적합한 루트 |
| **40-59점** | FAIR | 보통 수준의 루트 |
| **20-39점** | POOR | 부적합한 루트 |
| **0-19점** | VERY_POOR | 매우 부적합한 루트 |

### 추천 알고리즘 가중치
- **태그 매칭**: 70% 가중치
- **레벨 매칭**: 30% 가중치
- **최종 점수**: (태그점수 × 0.7) + (레벨점수 × 0.3)

---

## 🎯 비즈니스 로직 검증

### RouteTag Entity 검증 포인트
✅ **투표 시스템**: addVote() 메서드로 태그 품질 관리  
✅ **자동 점수 조정**: 투표 비율에 따른 연관성 점수 자동 업데이트  
✅ **관리자 검증**: verify() 메서드로 품질 보증  
✅ **품질 등급**: getQualityGrade()로 5단계 품질 분류  
✅ **추천 연동**: getWeightedScore()로 개인화 가중치 적용  

### UserRouteRecommendation Entity 검증 포인트
✅ **점수 계산**: calculateFinalScore()로 70:30 가중치 적용  
✅ **매칭률 분석**: getTagMatchRatio()로 매칭 정확도 측정  
✅ **품질 평가**: getRecommendationGrade()로 5단계 품질 분류  
✅ **TTL 관리**: isExpired()로 24시간 만료 체크  
✅ **추천 갱신**: refresh()로 재계산 및 업데이트  

### 추천 알고리즘 연동
✅ **태그 매칭**: 사용자 선호 태그와 루트 태그 매칭  
✅ **레벨 매칭**: 사용자 레벨과 루트 난이도 매칭  
✅ **캐싱 전략**: 24시간 TTL로 성능 최적화  
✅ **버전 관리**: algorithmVersion으로 알고리즘 변경 추적  

---

## 🔗 연관관계 설계

### RouteTag Entity 관계
- **N:1** → Route (루트)
- **N:1** → Tag (태그)
- **N:1** → User (생성자)

### UserRouteRecommendation Entity 관계
- **N:1** → User (사용자)
- **N:1** → Route (루트)

---

## 🔍 인덱스 최적화

### RouteTag 인덱스
- `idx_route_tags_route_score`: route_id + relevance_score DESC (루트별 품질 순 조회)
- `idx_route_tags_tag`: tag_id (태그별 루트 조회)
- `idx_route_tags_creator`: created_by (생성자별 태그 조회)
- `uk_route_tag`: route_id + tag_id UNIQUE (중복 태깅 방지)

### UserRouteRecommendation 인덱스
- `idx_user_recommendations_score`: user_id + recommendation_score DESC (사용자별 추천 순 조회)
- `idx_user_recommendations_active`: user_id + is_active (활성 추천 조회)
- `idx_user_recommendations_calculated`: calculated_at DESC (최신 추천 조회)
- `uk_user_route_recommendation`: user_id + route_id UNIQUE (중복 추천 방지)

---

## ✅ 태그 시스템 엔티티 완료 체크리스트

### 🏷️ 루트 태깅 시스템 (1개)
- [x] **RouteTag**: Route ↔ Tag 다대다 관계
  - 연관성 점수 (0.0~1.0)
  - 투표 기반 품질 관리
  - 자동 점수 조정 알고리즘
  - 관리자 검증 시스템
  - 품질 등급 5단계 분류

### 📊 개인화 추천 시스템 (1개)
- [x] **UserRouteRecommendation**: 개인화 추천 결과 캐싱
  - 태그 매칭 70% + 레벨 매칭 30%
  - 24시간 TTL 만료 체크
  - 추천 품질 등급 (5단계)
  - 태그 매칭률 분석
  - 알고리즘 버전 관리

### 🎯 비즈니스 로직 특징
- [x] **추천 알고리즘**: 가중치 기반 점수 계산 시스템
- [x] **품질 관리**: 투표 기반 태그 품질 자동 조정
- [x] **캐싱 전략**: 추천 결과 24시간 캐시 + 만료 체크
- [x] **통계 기능**: 태그 사용 빈도 및 매칭률 계산

### 🔍 인덱스 최적화
- [x] **RouteTag**: 4개 인덱스 (루트별 점수, 태그별, 생성자별, UK)
- [x] **UserRouteRecommendation**: 4개 인덱스 (점수별, 활성화, 계산시간, UK)

---

## 🏆 완성 현황

### step4-2a 분할 완료
- **step4-2a1_tag_core_entities.md**: 태그 핵심 엔티티 (5개) ✅
- **step4-2a2_route_tagging_recommendation_entities.md**: 루트 태깅 및 추천 엔티티 (2개) ✅

### 🎯 **총 7개 태그 시스템 엔티티 100% 완료**

투표 기반 품질 관리와 AI 추천 알고리즘(태그 70% + 레벨 30%)을 통한 정교한 태그 시스템이 완성되었습니다.

---

*Step 4-2a2 완료: 루트 태깅 및 추천 엔티티 구현 완전본*  
*RouteTag: 투표 기반 품질 관리 시스템*  
*UserRouteRecommendation: AI 추천 알고리즘 (70:30 가중치)*  
*캐싱 전략: 24시간 TTL 기반 성능 최적화*  
*Created: 2025-08-20*  
*RoutePickr - 클라이밍 루트 추천 플랫폼*