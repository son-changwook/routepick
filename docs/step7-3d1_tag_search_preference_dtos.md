# Step 7-3d1: Tag 검색 및 선호도 Request DTOs

> **TagSearch 및 UserPreference Request DTO 구현**  
> 생성일: 2025-08-27  
> 단계: 7-3d1 (Tag 검색 및 선호도 DTOs)  
> 구현 대상: TagSearchRequest, UserPreferredTagRequest, PreferredTagBatchRequest

---

## 📋 구현 목표

태그 검색 및 사용자 선호도 관련 Request DTO 클래스 3개:
1. **TagSearchRequest** - 태그 검색 조건
2. **UserPreferredTagRequest** - 선호 태그 설정
3. **PreferredTagBatchRequest** - 선호 태그 배치 설정

---

## 🏷️ Tag Search & Preference Request DTOs

### 📁 파일 위치
```
src/main/java/com/routepick/dto/tag/request/
src/main/java/com/routepick/dto/user/preference/request/
```

## 1. TagSearchRequest

### 📝 구현 코드
```java
package com.routepick.dto.tag.request;

import com.routepick.common.enums.TagType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 태그 검색 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "태그 검색 요청")
public class TagSearchRequest {

    @Schema(description = "검색 키워드", 
            example = "오버행", required = true)
    @NotBlank(message = "검색 키워드는 필수입니다")
    @Size(min = 1, max = 50, message = "검색 키워드는 1-50자여야 합니다")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s\\-_]+$", 
             message = "검색 키워드는 한글, 영문, 숫자, 공백, -, _만 사용 가능합니다")
    private String keyword;

    @Schema(description = "태그 타입 필터", 
            example = "WALL_ANGLE")
    private TagType tagType;

    @Schema(description = "사용자 선택 가능 필터", 
            example = "true")
    private Boolean isUserSelectable;

    @Schema(description = "루트 태깅 가능 필터", 
            example = "true")
    private Boolean isRouteTaggable;

    @Schema(description = "검색 결과 수 제한", 
            example = "20")
    @Min(value = 1, message = "최소 1개 이상이어야 합니다")
    @Max(value = 100, message = "최대 100개까지 가능합니다")
    @Builder.Default
    private Integer limit = 20;

    @Schema(description = "정렬 기준 (NAME, USAGE_COUNT, DISPLAY_ORDER)", 
            example = "USAGE_COUNT")
    @Pattern(regexp = "^(NAME|USAGE_COUNT|DISPLAY_ORDER)$", 
             message = "정렬 기준은 NAME, USAGE_COUNT, DISPLAY_ORDER 중 하나여야 합니다")
    @Builder.Default
    private String sortBy = "USAGE_COUNT";

    @Schema(description = "정렬 방향 (ASC, DESC)", 
            example = "DESC")
    @Pattern(regexp = "^(ASC|DESC)$", 
             message = "정렬 방향은 ASC 또는 DESC여야 합니다")
    @Builder.Default
    private String sortDirection = "DESC";

    @Schema(description = "자동완성 모드 여부", 
            example = "false")
    @Builder.Default
    private Boolean autocompleteMode = false;

    @Schema(description = "최소 사용 횟수 필터", 
            example = "1")
    @Min(value = 0, message = "0 이상이어야 합니다")
    private Integer minUsageCount;
}
```

---

## 2. UserPreferredTagRequest

### 📝 구현 코드
```java
package com.routepick.dto.user.preference.request;

import com.routepick.common.enums.PreferenceLevel;
import com.routepick.common.enums.SkillLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자 선호 태그 설정 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 선호 태그 설정 요청")
public class UserPreferredTagRequest {

    @Schema(description = "태그 ID", 
            example = "15", required = true)
    @NotNull(message = "태그 ID는 필수입니다")
    private Long tagId;

    @Schema(description = "선호도 레벨", 
            example = "HIGH", required = true)
    @NotNull(message = "선호도 레벨은 필수입니다")
    private PreferenceLevel preferenceLevel;

    @Schema(description = "해당 태그에 대한 실력 레벨", 
            example = "INTERMEDIATE")
    private SkillLevel skillLevel;

    @Schema(description = "선호 이유/메모", 
            example = "이런 스타일의 문제를 좋아함")
    @jakarta.validation.constraints.Size(max = 200, 
                                        message = "메모는 200자 이내로 입력해주세요")
    private String preferenceReason;

    @Schema(description = "자동 추천 활성화 여부", 
            example = "true")
    @Builder.Default
    private Boolean enableRecommendations = true;

    @Schema(description = "가중치 조정 (0.1 ~ 2.0)", 
            example = "1.0")
    @jakarta.validation.constraints.DecimalMin(value = "0.1", 
                                              message = "가중치는 0.1 이상이어야 합니다")
    @jakarta.validation.constraints.DecimalMax(value = "2.0", 
                                              message = "가중치는 2.0 이하여야 합니다")
    @Builder.Default
    private Double weightMultiplier = 1.0;
}
```

---

## 3. PreferredTagBatchRequest

### 📝 구현 코드
```java
package com.routepick.dto.user.preference.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 사용자 선호 태그 배치 설정 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 선호 태그 배치 설정 요청")
public class PreferredTagBatchRequest {

    @Schema(description = "선호 태그 목록", 
            required = true)
    @NotEmpty(message = "선호 태그 목록은 비어있을 수 없습니다")
    @Size(min = 1, max = 50, message = "선호 태그는 1-50개까지 설정 가능합니다")
    @Valid
    private List<UserPreferredTagRequest> preferredTags;

    @Schema(description = "기존 선호 태그 모두 삭제 후 설정 여부", 
            example = "false")
    @Builder.Default
    private Boolean replaceAll = false;

    @Schema(description = "중복 태그 처리 방식 (SKIP, UPDATE, ERROR)", 
            example = "UPDATE")
    @jakarta.validation.constraints.Pattern(regexp = "^(SKIP|UPDATE|ERROR)$", 
                                           message = "중복 처리 방식은 SKIP, UPDATE, ERROR 중 하나여야 합니다")
    @Builder.Default
    private String duplicateHandling = "UPDATE";

    @Schema(description = "배치 설정 후 추천 자동 재계산 여부", 
            example = "true")
    @Builder.Default
    private Boolean autoRefreshRecommendations = true;

    @Schema(description = "배치 작업 설명/메모", 
            example = "초기 선호도 설정")
    @Size(max = 100, message = "설명은 100자 이내로 입력해주세요")
    private String batchDescription;

    /**
     * 유효성 검증: 태그 ID 중복 확인
     */
    @jakarta.validation.constraints.AssertTrue(message = "중복된 태그 ID가 있습니다")
    public boolean isValidTagIds() {
        if (preferredTags == null) {
            return true;
        }
        
        long uniqueTagIds = preferredTags.stream()
            .map(UserPreferredTagRequest::getTagId)
            .filter(tagId -> tagId != null)
            .distinct()
            .count();
        
        return uniqueTagIds == preferredTags.size();
    }
}
```

---

## 📈 주요 특징

### 1. **TagSearchRequest 특징**
- **한국어 지원**: 한글 키워드 검색 지원
- **다양한 필터**: TagType, 선택가능성, 태깅가능성
- **정렬 옵션**: 이름, 사용횟수, 표시순서
- **자동완성**: autocompleteMode 지원

### 2. **UserPreferredTagRequest 특징**
- **선호도 레벨**: LOW, MEDIUM, HIGH 3단계
- **실력 레벨**: 태그별 개별 실력 설정
- **가중치 조정**: 0.1~2.0 범위 미세 조정
- **메모 기능**: 선호 이유 기록

### 3. **PreferredTagBatchRequest 특징**
- **배치 처리**: 1-50개 태그 일괄 설정
- **중복 처리**: SKIP, UPDATE, ERROR 3가지 방식
- **자동 재계산**: 설정 후 추천 자동 갱신
- **중복 검증**: Stream API로 태그 ID 중복 체크

---

## ✅ 검증 규칙 요약

### TagSearchRequest 검증
- [x] **키워드**: 1-50자, 한글/영문/숫자만 허용
- [x] **결과 수**: 1-100개 제한
- [x] **정렬**: NAME, USAGE_COUNT, DISPLAY_ORDER만 허용
- [x] **방향**: ASC, DESC만 허용

### UserPreferredTagRequest 검증
- [x] **태그 ID**: 필수 입력
- [x] **선호도**: PreferenceLevel enum 필수
- [x] **가중치**: 0.1~2.0 범위 제한
- [x] **메모**: 200자 이내

### PreferredTagBatchRequest 검증
- [x] **태그 목록**: 1-50개 제한, 비어있으면 안됨
- [x] **중복 처리**: SKIP, UPDATE, ERROR만 허용
- [x] **태그 ID 중복**: Stream API로 검증
- [x] **설명**: 100자 이내

---

**📝 연관 파일**: 
- step7-3d2_skill_recommendation_refresh_dtos.md (SkillLevel 업데이트 및 추천 DTOs)

---

**다음 단계**: SkillLevelUpdateRequest, RouteRecommendationRequest, RecommendationRefreshRequest 구현  
**완료일**: 2025-08-27  
**핵심 성과**: Tag 검색 및 선호도 설정 DTOs + Bean Validation 완성