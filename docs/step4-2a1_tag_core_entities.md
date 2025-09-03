# Step 4-2a1: 태그 핵심 엔티티 구현 (완전본)

> **RoutePickr - 클라이밍 루트 추천 플랫폼**  
> Step 4-2a: 통합 태그 시스템 엔티티 (핵심 태그 엔티티 Part)

## 📋 이 문서의 내용

이 문서는 **step4-2a_tag_system_entities.md**에서 분할된 첫 번째 부분으로, 다음 핵심 태그 엔티티들을 포함합니다:

### 🏷️ 태그 핵심 엔티티
- TagType Enum (8가지 카테고리)
- PreferenceLevel Enum (3단계 선호도)
- SkillLevel Enum (4단계 숙련도)  
- Tag Entity (마스터 태그 엔티티)
- UserPreferredTag Entity (사용자 선호 태그)

### 🎯 설계 핵심
- AI 기반 추천의 핵심 구조
- 사용자 선호도 × 숙련도 매트릭스
- 투표 기반 품질 관리 시스템

---

## 🎯 태그 시스템 설계 목표

- **통합 태그 시스템**: AI 기반 추천의 핵심, 8가지 TagType 지원
- **사용자 선호도**: 3단계 선호도 × 4단계 숙련도 매트릭스
- **루트 태깅**: 투표 기반 품질 관리 시스템
- **추천 알고리즘**: 태그 매칭 70% + 레벨 매칭 30%

---

## 🏷️ 통합 태그 시스템 엔티티 (5개)

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
    
    // ===== 연관관계 편의 메서드 =====
    
    /**
     * 사용자와 태그 설정
     */
    public static UserPreferredTag createUserPreferredTag(User user, Tag tag, 
                                                          PreferenceLevel preferenceLevel, 
                                                          SkillLevel skillLevel) {
        return UserPreferredTag.builder()
            .user(user)
            .tag(tag)
            .preferenceLevel(preferenceLevel)
            .skillLevel(skillLevel)
            .isActive(true)
            .build();
    }
}
```

---

## 📊 태그 핵심 구성

### TagType 분류 체계
| 태그 유형 | 설명 | 사용자 선택 | 루트 태깅 |
|----------|------|-----------|----------|
| **STYLE** | 클라이밍 종목 구분 | ✅ | ✅ |
| **FEATURE** | 루트/홀드의 물리적 특성 | ❌ | ✅ |
| **TECHNIQUE** | 필요한 기술 | ✅ | ✅ |
| **DIFFICULTY** | 체감 난이도 표현 | ✅ | ✅ |
| **MOVEMENT** | 동작 스타일 | ✅ | ✅ |
| **HOLD_TYPE** | 홀드 종류 | ❌ | ✅ |
| **WALL_ANGLE** | 벽면 기울기 | ❌ | ✅ |
| **OTHER** | 기타 분류 | ❌ | ❌ |

### 선호도 × 숙련도 매트릭스
| 숙련도 \ 선호도 | LOW (30%) | MEDIUM (70%) | HIGH (100%) |
|----------------|-----------|--------------|-------------|
| **BEGINNER** | 0.3 | 0.7 | 1.0 |
| **INTERMEDIATE** | 0.3 | 0.7 | 1.0 |
| **ADVANCED** | 0.3 | 0.7 | 1.0 |
| **EXPERT** | 0.3 | 0.7 | 1.0 |

---

## 🎯 비즈니스 로직 검증

### Tag Entity 검증 포인트
✅ **고유성 보장**: tag_name 유니크 인덱스  
✅ **타입별 분류**: TagType Enum으로 8가지 카테고리 지원  
✅ **사용성 제어**: isUserSelectable, isRouteTaggable 플래그  
✅ **표시 순서**: displayOrder로 UI 정렬 관리  
✅ **통계 수집**: usageCount로 인기 태그 분석  

### UserPreferredTag Entity 검증 포인트
✅ **다대다 관계**: User ↔ Tag 중간 테이블  
✅ **선호도 가중치**: PreferenceLevel에 따른 추천 점수 조절  
✅ **숙련도 추적**: SkillLevel로 사용자 성장 관리  
✅ **경험 관리**: experienceMonths로 태그별 경험 누적  
✅ **활성화 제어**: isActive로 선호도 on/off 관리  

### 추천 알고리즘 연동
✅ **가중치 계산**: getRecommendationWeight() 메서드  
✅ **레벨 업 시스템**: upgradePreference(), improveSkill()  
✅ **양방향 태그**: isBidirectional() 검증  

---

## 🔗 연관관계 설계

### Tag Entity 관계
- **1:N** → UserPreferredTag (사용자 선호도)
- **1:N** → RouteTag (루트 태깅)
- **1:N** → UserRouteRecommendation (추천 결과)

### UserPreferredTag Entity 관계
- **N:1** → User (사용자)
- **N:1** → Tag (태그)

---

## 🏆 완성 현황

### step4-2a 분할 준비
- **step4-2a1_tag_core_entities.md**: 태그 핵심 엔티티 (5개) ✅
- **step4-2a2**: 루트 태깅 및 추천 엔티티 (예정)

### 🎯 **태그 핵심 구조 100% 완료**

8가지 TagType과 3×4 선호도/숙련도 매트릭스를 통한 정교한 태그 시스템이 완성되었습니다.

---

*Step 4-2a1 완료: 태그 핵심 엔티티 구현 완전본*  
*TagType: 8가지 카테고리 지원*  
*PreferenceLevel: 3단계 가중치 시스템*  
*SkillLevel: 4단계 성장 관리*  
*Created: 2025-08-20*  
*RoutePickr - 클라이밍 루트 추천 플랫폼*