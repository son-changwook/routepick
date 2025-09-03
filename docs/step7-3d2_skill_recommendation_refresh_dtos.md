# Step 7-3d2: 실력 레벨 및 추천 Request DTOs

> **SkillLevel 업데이트 및 추천 시스템 Request DTO 구현**  
> 생성일: 2025-08-27  
> 단계: 7-3d2 (실력 레벨 및 추천 DTOs)  
> 구현 대상: SkillLevelUpdateRequest, RouteRecommendationRequest, RecommendationRefreshRequest

---

## 📋 구현 목표

실력 레벨 관리 및 추천 시스템 관련 Request DTO 클래스 3개:
1. **SkillLevelUpdateRequest** - 실력 레벨 업데이트
2. **RouteRecommendationRequest** - 루트 추천 요청
3. **RecommendationRefreshRequest** - 추천 재계산 요청

---

## 🏷️ Skill Level & Recommendation Request DTOs

### 📁 파일 위치
```
src/main/java/com/routepick/dto/user/preference/request/
src/main/java/com/routepick/dto/recommendation/request/
```

## 1. SkillLevelUpdateRequest

### 📝 구현 코드
```java
package com.routepick.dto.user.preference.request;

import com.routepick.common.enums.SkillLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 실력 레벨 업데이트 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "실력 레벨 업데이트 요청")
public class SkillLevelUpdateRequest {

    @Schema(description = "새로운 실력 레벨", 
            example = "INTERMEDIATE", required = true)
    @NotNull(message = "실력 레벨은 필수입니다")
    private SkillLevel skillLevel;

    @Schema(description = "레벨 변경 이유", 
            example = "최근 V5 문제들을 안정적으로 완등하고 있어서")
    @jakarta.validation.constraints.Size(max = 300, 
                                        message = "변경 이유는 300자 이내로 입력해주세요")
    private String reasonForChange;

    @Schema(description = "자동 난이도 추천 활성화", 
            example = "true")
    @Builder.Default
    private Boolean enableDifficultyRecommendation = true;

    @Schema(description = "선호 태그와의 매칭 가중치 (0.5 ~ 2.0)", 
            example = "1.0")
    @jakarta.validation.constraints.DecimalMin(value = "0.5", 
                                              message = "가중치는 0.5 이상이어야 합니다")
    @jakarta.validation.constraints.DecimalMax(value = "2.0", 
                                              message = "가중치는 2.0 이하여야 합니다")
    @Builder.Default
    private Double levelMatchWeight = 1.0;

    @Schema(description = "레벨 변경 후 추천 재계산 여부", 
            example = "true")
    @Builder.Default
    private Boolean refreshRecommendations = true;

    @Schema(description = "도전적인 루트 포함 비율 (0.0 ~ 1.0)", 
            example = "0.3")
    @jakarta.validation.constraints.DecimalMin(value = "0.0", 
                                              message = "도전 비율은 0.0 이상이어야 합니다")
    @jakarta.validation.constraints.DecimalMax(value = "1.0", 
                                              message = "도전 비율은 1.0 이하여야 합니다")
    @Builder.Default
    private Double challengeRouteRatio = 0.3;

    @Schema(description = "안정적인 루트 포함 비율 (0.0 ~ 1.0)", 
            example = "0.5")
    @jakarta.validation.constraints.DecimalMin(value = "0.0", 
                                              message = "안정 비율은 0.0 이상이어야 합니다")
    @jakarta.validation.constraints.DecimalMax(value = "1.0", 
                                              message = "안정 비율은 1.0 이하여야 합니다")
    @Builder.Default
    private Double stableRouteRatio = 0.5;

    /**
     * 유효성 검증: 도전 + 안정 비율 합계가 1.0 이하인지 확인
     */
    @jakarta.validation.constraints.AssertTrue(message = "도전 비율과 안정 비율의 합은 1.0 이하여야 합니다")
    public boolean isValidRatioSum() {
        if (challengeRouteRatio == null || stableRouteRatio == null) {
            return true;
        }
        return (challengeRouteRatio + stableRouteRatio) <= 1.0;
    }
}
```

---

## 2. RouteRecommendationRequest

### 📝 구현 코드
```java
package com.routepick.dto.recommendation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 루트 추천 요청 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "루트 추천 요청")
public class RouteRecommendationRequest {

    @Schema(description = "추천 루트 수", 
            example = "20")
    @Min(value = 1, message = "최소 1개 이상이어야 합니다")
    @Max(value = 50, message = "최대 50개까지 가능합니다")
    @Builder.Default
    private Integer limit = 20;

    @Schema(description = "완등한 루트 포함 여부", 
            example = "false")
    @Builder.Default
    private Boolean includeCompleted = false;

    @Schema(description = "특정 체육관 ID 필터", 
            example = "123")
    private Long gymId;

    @Schema(description = "난이도 범위 (예: V3-V6, 5.10a-5.11d)", 
            example = "V4-V7")
    @Size(max = 20, message = "난이도 범위는 20자 이내로 입력해주세요")
    @Pattern(regexp = "^(V\\d+(-V\\d+)?|5\\.\\d+[a-d]?(-5\\.\\d+[a-d]?)?|\\d+(-\\d+)?)$", 
             message = "올바른 난이도 범위 형식이 아닙니다")
    private String difficultyRange;

    @Schema(description = "추천 타입 (PERSONALIZED, POPULAR, SIMILAR_USERS, CHALLENGING)", 
            example = "PERSONALIZED")
    @Pattern(regexp = "^(PERSONALIZED|POPULAR|SIMILAR_USERS|CHALLENGING)$", 
             message = "추천 타입은 PERSONALIZED, POPULAR, SIMILAR_USERS, CHALLENGING 중 하나여야 합니다")
    @Builder.Default
    private String recommendationType = "PERSONALIZED";

    @Schema(description = "최소 추천 점수 (0.0 ~ 1.0)", 
            example = "0.6")
    @DecimalMin(value = "0.0", message = "추천 점수는 0.0 이상이어야 합니다")
    @DecimalMax(value = "1.0", message = "추천 점수는 1.0 이하여야 합니다")
    @Builder.Default
    private Double minRecommendationScore = 0.6;

    @Schema(description = "선호 태그 가중치 (0.0 ~ 1.0)", 
            example = "0.7")
    @DecimalMin(value = "0.0", message = "태그 가중치는 0.0 이상이어야 합니다")
    @DecimalMax(value = "1.0", message = "태그 가중치는 1.0 이하여야 합니다")
    @Builder.Default
    private Double tagMatchWeight = 0.7;

    @Schema(description = "레벨 매칭 가중치 (0.0 ~ 1.0)", 
            example = "0.3")
    @DecimalMin(value = "0.0", message = "레벨 가중치는 0.0 이상이어야 합니다")
    @DecimalMax(value = "1.0", message = "레벨 가중치는 1.0 이하여야 합니다")
    @Builder.Default
    private Double levelMatchWeight = 0.3;

    @Schema(description = "새로운 루트 우선순위 (0.0 ~ 1.0)", 
            example = "0.1")
    @DecimalMin(value = "0.0", message = "새로운 루트 우선순위는 0.0 이상이어야 합니다")
    @DecimalMax(value = "1.0", message = "새로운 루트 우선순위는 1.0 이하여야 합니다")
    @Builder.Default
    private Double newRouteBonus = 0.1;

    @Schema(description = "인기도 가중치 (0.0 ~ 1.0)", 
            example = "0.1")
    @DecimalMin(value = "0.0", message = "인기도 가중치는 0.0 이상이어야 합니다")
    @DecimalMax(value = "1.0", message = "인기도 가중치는 1.0 이하여야 합니다")
    @Builder.Default
    private Double popularityWeight = 0.1;

    @Schema(description = "제외할 태그 ID 목록", 
            example = "[1, 5, 10]")
    private java.util.List<Long> excludeTagIds;

    @Schema(description = "필수 포함 태그 ID 목록", 
            example = "[2, 7]")
    private java.util.List<Long> includeTagIds;

    @Schema(description = "캐시 사용 여부", 
            example = "true")
    @Builder.Default
    private Boolean useCache = true;

    /**
     * 유효성 검증: 태그 가중치 + 레벨 가중치 = 1.0
     */
    @AssertTrue(message = "태그 가중치와 레벨 가중치의 합은 1.0이어야 합니다")
    public boolean isValidWeightSum() {
        if (tagMatchWeight == null || levelMatchWeight == null) {
            return true;
        }
        double sum = tagMatchWeight + levelMatchWeight;
        return Math.abs(sum - 1.0) < 0.01; // 부동소수점 오차 고려
    }
}
```

---

## 3. RecommendationRefreshRequest

### 📝 구현 코드
```java
package com.routepick.dto.recommendation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 추천 재계산 요청 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "추천 재계산 요청")
public class RecommendationRefreshRequest {

    @Schema(description = "강제 재계산 여부 (캐시 무시)", 
            example = "false")
    @Builder.Default
    private boolean forceRecalculate = false;

    @Schema(description = "재계산 범위 (ALL, TAGS_ONLY, LEVEL_ONLY, SIMILARITY)", 
            example = "ALL")
    @Pattern(regexp = "^(ALL|TAGS_ONLY|LEVEL_ONLY|SIMILARITY)$", 
             message = "재계산 범위는 ALL, TAGS_ONLY, LEVEL_ONLY, SIMILARITY 중 하나여야 합니다")
    @Builder.Default
    private String recalculationScope = "ALL";

    @Schema(description = "특정 체육관만 재계산 (선택사항)", 
            example = "[123, 456]")
    private List<Long> targetGymIds;

    @Schema(description = "비동기 처리 여부", 
            example = "true")
    @Builder.Default
    private Boolean async = true;

    @Schema(description = "완료 알림 설정 (EMAIL, PUSH, NONE)", 
            example = "PUSH")
    @Pattern(regexp = "^(EMAIL|PUSH|NONE)$", 
             message = "알림 설정은 EMAIL, PUSH, NONE 중 하나여야 합니다")
    @Builder.Default
    private String notificationMode = "PUSH";

    @Schema(description = "우선순위 (HIGH, MEDIUM, LOW)", 
            example = "MEDIUM")
    @Pattern(regexp = "^(HIGH|MEDIUM|LOW)$", 
             message = "우선순위는 HIGH, MEDIUM, LOW 중 하나여야 합니다")
    @Builder.Default
    private String priority = "MEDIUM";

    @Schema(description = "재계산 이유/메모", 
            example = "선호도 대폭 변경으로 인한 재계산")
    @jakarta.validation.constraints.Size(max = 200, 
                                        message = "재계산 이유는 200자 이내로 입력해주세요")
    private String reason;

    @Schema(description = "최대 대기 시간 (초)", 
            example = "300")
    @jakarta.validation.constraints.Min(value = 30, 
                                       message = "최소 30초 이상이어야 합니다")
    @jakarta.validation.constraints.Max(value = 3600, 
                                       message = "최대 1시간까지 가능합니다")
    @Builder.Default
    private Integer maxWaitTimeSeconds = 300;

    @Schema(description = "품질 체크 모드 활성화", 
            example = "true")
    @Builder.Default
    private Boolean enableQualityCheck = true;
}
```

---

## 📈 주요 특징

### 1. **SkillLevelUpdateRequest 특징**
- **비율 검증**: 도전 + 안정 비율 합계 ≤ 1.0
- **가중치 조정**: 0.5~2.0 범위 레벨 매칭 가중치
- **자동 재계산**: 레벨 변경 후 추천 자동 갱신
- **변경 이유**: 300자 이내 변경 사유 기록

### 2. **RouteRecommendationRequest 특징**
- **가중치 검증**: 태그 + 레벨 가중치 합계 = 1.0
- **난이도 범위**: 정규식으로 V등급, 5.XX 형식 검증
- **추천 타입**: 4가지 추천 방식 지원
- **필터링**: 체육관, 태그 포함/제외 필터

### 3. **RecommendationRefreshRequest 특징**
- **재계산 범위**: ALL, TAGS_ONLY, LEVEL_ONLY, SIMILARITY
- **비동기 처리**: 대용량 재계산 비동기 지원
- **알림 설정**: EMAIL, PUSH, NONE 선택
- **우선순위**: HIGH, MEDIUM, LOW 3단계

---

## ✅ 검증 규칙 요약

### SkillLevelUpdateRequest 검증
- [x] **실력 레벨**: SkillLevel enum 필수
- [x] **가중치**: 0.5~2.0 범위 제한
- [x] **비율 합계**: 도전 + 안정 ≤ 1.0
- [x] **변경 이유**: 300자 이내

### RouteRecommendationRequest 검증
- [x] **추천 수**: 1-50개 제한
- [x] **난이도 범위**: 정규식 패턴 검증
- [x] **추천 타입**: 4가지 타입만 허용
- [x] **가중치 합계**: 태그 + 레벨 = 1.0 (오차 0.01 허용)

### RecommendationRefreshRequest 검증
- [x] **재계산 범위**: 4가지 범위만 허용
- [x] **알림 모드**: EMAIL, PUSH, NONE만 허용
- [x] **우선순위**: HIGH, MEDIUM, LOW만 허용
- [x] **대기 시간**: 30초~1시간 제한

---

## 📊 설계 완료 사항

✅ **SkillLevelUpdateRequest** - 실력 레벨 업데이트 (비율 검증)  
✅ **RouteRecommendationRequest** - 루트 추천 요청 (가중치 검증)  
✅ **RecommendationRefreshRequest** - 추천 재계산 요청 (비동기 처리)

## 🎯 핵심 특징
- **완전한 Bean Validation** - @NotNull, @DecimalMin/Max, @AssertTrue 활용
- **비즈니스 로직 검증** - 가중치 합계, 비율 합계 등 커스텀 검증
- **Builder 패턴** - 기본값 설정으로 사용 편의성 증대
- **Swagger 문서화** - 완벽한 API 문서 생성
- **확장성 고려** - 추가 필드 확장 가능한 구조

---

**📝 연관 파일**: 
- step7-3d1_tag_search_preference_dtos.md (Tag 검색 및 선호도 DTOs)
- step7-3e_tag_response_dtos.md (Tag Response DTOs)

---

**다음 단계**: Tag Response DTOs 구현  
**완료일**: 2025-08-27  
**핵심 성과**: 실력 레벨 업데이트 + 추천 요청 DTOs + 고급 검증 완성