# Step 7-3e: Tag System Response DTOs

## 📋 구현 목표
태그 시스템 관련 Response DTO 클래스 6개 구현:
1. **TagResponse** - 태그 기본 정보
2. **UserPreferredTagResponse** - 사용자 선호 태그 정보
3. **RouteRecommendationResponse** - 루트 추천 정보
4. **TaggedRouteResponse** - 태그된 루트 정보
5. **SimilarUserResponse** - 유사 사용자 정보
6. **RecommendationHistoryResponse** - 추천 이력 정보

---

## 🏷️ Tag System Response DTOs

### 📁 파일 위치
```
src/main/java/com/routepick/dto/tag/response/
src/main/java/com/routepick/dto/user/preference/response/
src/main/java/com/routepick/dto/recommendation/response/
```

## 1. TagResponse

### 📝 구현 코드
```java
package com.routepick.dto.tag.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routepick.common.enums.TagType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 태그 기본 정보 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "태그 기본 정보 응답")
public class TagResponse {

    @Schema(description = "태그 ID", example = "15")
    @JsonProperty("id")
    private Long id;

    @Schema(description = "태그 이름", example = "오버행")
    @JsonProperty("tagName")
    private String tagName;

    @Schema(description = "태그 타입", example = "WALL_ANGLE")
    @JsonProperty("tagType")
    private TagType tagType;

    @Schema(description = "태그 설명", example = "벽면이 뒤로 기울어진 각도의 홀드")
    @JsonProperty("description")
    private String description;

    @Schema(description = "사용자 선택 가능 여부", example = "true")
    @JsonProperty("isUserSelectable")
    private Boolean isUserSelectable;

    @Schema(description = "루트 태깅 가능 여부", example = "true")
    @JsonProperty("isRouteTaggable")
    private Boolean isRouteTaggable;

    @Schema(description = "사용 횟수", example = "1247")
    @JsonProperty("usageCount")
    private Long usageCount;

    @Schema(description = "표시 순서", example = "10")
    @JsonProperty("displayOrder")
    private Integer displayOrder;

    @Schema(description = "태그 색상 코드", example = "#FF5722")
    @JsonProperty("colorCode")
    private String colorCode;

    @Schema(description = "태그 아이콘 URL", example = "https://cdn.routepick.com/icons/wall-angle/overhang.svg")
    @JsonProperty("iconUrl")
    private String iconUrl;

    @Schema(description = "활성화 상태", example = "true")
    @JsonProperty("isActive")
    private Boolean isActive;

    @Schema(description = "인기도 점수", example = "8.7")
    @JsonProperty("popularityScore")
    private Double popularityScore;

    @Schema(description = "최근 30일 사용 횟수", example = "89")
    @JsonProperty("recentUsageCount")
    private Integer recentUsageCount;

    @Schema(description = "생성일시")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    @Schema(description = "관련 태그들")
    @JsonProperty("relatedTags")
    private java.util.List<RelatedTagInfo> relatedTags;

    /**
     * 관련 태그 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "관련 태그 정보")
    public static class RelatedTagInfo {
        @Schema(description = "관련 태그 ID")
        @JsonProperty("tagId")
        private Long tagId;

        @Schema(description = "관련 태그 이름")
        @JsonProperty("tagName")
        private String tagName;

        @Schema(description = "연관성 점수 (0.0 ~ 1.0)")
        @JsonProperty("relationScore")
        private Double relationScore;

        @Schema(description = "관계 유형 (SIMILAR, OPPOSITE, COMPLEMENTARY)")
        @JsonProperty("relationType")
        private String relationType;
    }
}
```

---

## 2. UserPreferredTagResponse

### 📝 구현 코드
```java
package com.routepick.dto.user.preference.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routepick.common.enums.PreferenceLevel;
import com.routepick.common.enums.SkillLevel;
import com.routepick.dto.tag.response.TagResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 선호 태그 정보 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "사용자 선호 태그 정보 응답")
public class UserPreferredTagResponse {

    @Schema(description = "선호 태그 ID", example = "123")
    @JsonProperty("id")
    private Long id;

    @Schema(description = "태그 정보")
    @JsonProperty("tag")
    private TagResponse tag;

    @Schema(description = "선호도 레벨", example = "HIGH")
    @JsonProperty("preferenceLevel")
    private PreferenceLevel preferenceLevel;

    @Schema(description = "해당 태그 실력 레벨", example = "INTERMEDIATE")
    @JsonProperty("skillLevel")
    private SkillLevel skillLevel;

    @Schema(description = "선호 이유/메모", example = "이런 스타일의 문제를 좋아함")
    @JsonProperty("preferenceReason")
    private String preferenceReason;

    @Schema(description = "가중치 배수", example = "1.5")
    @JsonProperty("weightMultiplier")
    private Double weightMultiplier;

    @Schema(description = "자동 추천 활성화 여부", example = "true")
    @JsonProperty("enableRecommendations")
    private Boolean enableRecommendations;

    @Schema(description = "선호도 점수 (계산된 값)", example = "85.5")
    @JsonProperty("calculatedScore")
    private Double calculatedScore;

    @Schema(description = "이 태그로 완등한 루트 수", example = "23")
    @JsonProperty("completedRoutesCount")
    private Integer completedRoutesCount;

    @Schema(description = "이 태그의 평균 성공률 (%)", example = "72.5")
    @JsonProperty("averageSuccessRate")
    private Double averageSuccessRate;

    @Schema(description = "최근 활동 점수", example = "9.2")
    @JsonProperty("recentActivityScore")
    private Double recentActivityScore;

    @Schema(description = "선호도 설정일시")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @Schema(description = "마지막 수정일시")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    @Schema(description = "추천 품질 통계")
    @JsonProperty("recommendationStats")
    private RecommendationStats recommendationStats;

    /**
     * 추천 품질 통계
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "추천 품질 통계")
    public static class RecommendationStats {
        @Schema(description = "이 태그 기반 추천 루트 수", example = "45")
        @JsonProperty("recommendedRoutesCount")
        private Integer recommendedRoutesCount;

        @Schema(description = "추천 루트 완등률 (%)", example = "68.9")
        @JsonProperty("recommendationSuccessRate")
        private Double recommendationSuccessRate;

        @Schema(description = "사용자 만족도 평균", example = "4.2")
        @JsonProperty("averageSatisfactionScore")
        private Double averageSatisfactionScore;

        @Schema(description = "마지막 추천일시")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @JsonProperty("lastRecommendedAt")
        private LocalDateTime lastRecommendedAt;
    }
}
```

---

## 3. RouteRecommendationResponse

### 📝 구현 코드
```java
package com.routepick.dto.recommendation.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routepick.dto.route.response.RouteBasicResponse;
import com.routepick.dto.tag.response.TagResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 루트 추천 정보 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "루트 추천 정보 응답")
public class RouteRecommendationResponse {

    @Schema(description = "추천 ID", example = "789")
    @JsonProperty("recommendationId")
    private Long recommendationId;

    @Schema(description = "루트 정보")
    @JsonProperty("route")
    private RouteBasicResponse route;

    @Schema(description = "전체 추천 점수 (0.0 ~ 1.0)", example = "0.87")
    @JsonProperty("recommendationScore")
    private Double recommendationScore;

    @Schema(description = "태그 매칭 점수 (0.0 ~ 1.0)", example = "0.92")
    @JsonProperty("tagMatchScore")
    private Double tagMatchScore;

    @Schema(description = "레벨 매칭 점수 (0.0 ~ 1.0)", example = "0.75")
    @JsonProperty("levelMatchScore")
    private Double levelMatchScore;

    @Schema(description = "추천 계산일시")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("calculatedAt")
    private LocalDateTime calculatedAt;

    @Schema(description = "매칭된 선호 태그들")
    @JsonProperty("matchingTags")
    private List<MatchingTagInfo> matchingTags;

    @Schema(description = "추천 이유")
    @JsonProperty("recommendationReasons")
    private List<String> recommendationReasons;

    @Schema(description = "추천 타입", example = "PERSONALIZED")
    @JsonProperty("recommendationType")
    private String recommendationType;

    @Schema(description = "도전 레벨 (EASY, MODERATE, CHALLENGING)", example = "MODERATE")
    @JsonProperty("challengeLevel")
    private String challengeLevel;

    @Schema(description = "예상 성공률 (%)", example = "73.2")
    @JsonProperty("expectedSuccessRate")
    private Double expectedSuccessRate;

    @Schema(description = "유사 사용자들의 완등률 (%)", example = "81.5")
    @JsonProperty("similarUsersSuccessRate")
    private Double similarUsersSuccessRate;

    @Schema(description = "인기도 점수", example = "8.4")
    @JsonProperty("popularityScore")
    private Double popularityScore;

    @Schema(description = "새로움 점수", example = "0.3")
    @JsonProperty("noveltyScore")
    private Double noveltyScore;

    @Schema(description = "추천 순위", example = "5")
    @JsonProperty("recommendationRank")
    private Integer recommendationRank;

    @Schema(description = "추천 만료일시")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("expiresAt")
    private LocalDateTime expiresAt;

    /**
     * 매칭된 태그 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "매칭된 태그 정보")
    public static class MatchingTagInfo {
        @Schema(description = "태그 정보")
        @JsonProperty("tag")
        private TagResponse tag;

        @Schema(description = "사용자 선호도", example = "HIGH")
        @JsonProperty("userPreferenceLevel")
        private String userPreferenceLevel;

        @Schema(description = "매칭 점수 (0.0 ~ 1.0)", example = "0.95")
        @JsonProperty("matchScore")
        private Double matchScore;

        @Schema(description = "가중치", example = "1.2")
        @JsonProperty("weight")
        private Double weight;

        @Schema(description = "기여도 (%)", example = "23.5")
        @JsonProperty("contribution")
        private Double contribution;
    }
}
```

---

## 4. TaggedRouteResponse

### 📝 구현 코드
```java
package com.routepick.dto.recommendation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routepick.dto.route.response.RouteBasicResponse;
import com.routepick.dto.tag.response.TagResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 태그된 루트 정보 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "태그된 루트 정보 응답")
public class TaggedRouteResponse {

    @Schema(description = "루트 기본 정보")
    @JsonProperty("route")
    private RouteBasicResponse route;

    @Schema(description = "루트에 할당된 태그들")
    @JsonProperty("tags")
    private List<RouteTagInfo> tags;

    @Schema(description = "태그별 관련성 점수")
    @JsonProperty("relevanceScores")
    private Map<Long, Double> relevanceScores;

    @Schema(description = "태그 타입별 분포")
    @JsonProperty("tagTypeDistribution")
    private Map<String, Integer> tagTypeDistribution;

    @Schema(description = "평균 태그 일치율", example = "0.78")
    @JsonProperty("averageTagMatchRate")
    private Double averageTagMatchRate;

    @Schema(description = "태그 품질 점수", example = "8.9")
    @JsonProperty("tagQualityScore")
    private Double tagQualityScore;

    @Schema(description = "사용자 관련성 점수", example = "0.85")
    @JsonProperty("userRelevanceScore")
    private Double userRelevanceScore;

    @Schema(description = "추천 강도 (WEAK, MODERATE, STRONG)", example = "STRONG")
    @JsonProperty("recommendationStrength")
    private String recommendationStrength;

    /**
     * 루트 태그 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "루트 태그 정보")
    public static class RouteTagInfo {
        @Schema(description = "태그 정보")
        @JsonProperty("tag")
        private TagResponse tag;

        @Schema(description = "태그 신뢰도", example = "0.92")
        @JsonProperty("confidence")
        private Double confidence;

        @Schema(description = "투표 수", example = "23")
        @JsonProperty("voteCount")
        private Integer voteCount;

        @Schema(description = "평균 평점", example = "4.2")
        @JsonProperty("averageRating")
        private Double averageRating;

        @Schema(description = "태그 설정자 (USER, SETTER, ADMIN, AI)", example = "USER")
        @JsonProperty("source")
        private String source;

        @Schema(description = "사용자의 이 태그 선호도", example = "HIGH")
        @JsonProperty("userPreference")
        private String userPreference;

        @Schema(description = "개인 관련성 점수", example = "0.88")
        @JsonProperty("personalRelevance")
        private Double personalRelevance;
    }
}
```

---

## 5. SimilarUserResponse

### 📝 구현 코드
```java
package com.routepick.dto.recommendation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routepick.dto.user.response.UserSummaryResponse;
import com.routepick.dto.tag.response.TagResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 유사 사용자 정보 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "유사 사용자 정보 응답")
public class SimilarUserResponse {

    @Schema(description = "유사 사용자 정보")
    @JsonProperty("user")
    private UserSummaryResponse user;

    @Schema(description = "전체 유사도 점수 (0.0 ~ 1.0)", example = "0.87")
    @JsonProperty("similarityScore")
    private Double similarityScore;

    @Schema(description = "공통 선호 태그 수", example = "12")
    @JsonProperty("commonTagsCount")
    private Integer commonTagsCount;

    @Schema(description = "선호도 매칭률 (%)", example = "73.5")
    @JsonProperty("preferenceMatchRate")
    private Double preferenceMatchRate;

    @Schema(description = "실력 레벨 차이", example = "1")
    @JsonProperty("skillLevelDifference")
    private Integer skillLevelDifference;

    @Schema(description = "공통 선호 태그들")
    @JsonProperty("commonTags")
    private List<CommonTagInfo> commonTags;

    @Schema(description = "추천 신뢰도", example = "0.82")
    @JsonProperty("recommendationCredibility")
    private Double recommendationCredibility;

    @Schema(description = "활동 패턴 유사도", example = "0.69")
    @JsonProperty("activityPatternSimilarity")
    private Double activityPatternSimilarity;

    @Schema(description = "이 사용자의 추천 정확도", example = "78.3")
    @JsonProperty("recommendationAccuracy")
    private Double recommendationAccuracy;

    @Schema(description = "팔로우 관계", example = "NOT_FOLLOWING")
    @JsonProperty("followStatus")
    private String followStatus;

    @Schema(description = "상호 친구 수", example = "5")
    @JsonProperty("mutualFriendsCount")
    private Integer mutualFriendsCount;

    @Schema(description = "지역 근접성", example = "SAME_CITY")
    @JsonProperty("locationProximity")
    private String locationProximity;

    @Schema(description = "추천 강도 (WEAK, MODERATE, STRONG)", example = "STRONG")
    @JsonProperty("recommendationStrength")
    private String recommendationStrength;

    /**
     * 공통 태그 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "공통 태그 정보")
    public static class CommonTagInfo {
        @Schema(description = "태그 정보")
        @JsonProperty("tag")
        private TagResponse tag;

        @Schema(description = "내 선호도", example = "HIGH")
        @JsonProperty("myPreferenceLevel")
        private String myPreferenceLevel;

        @Schema(description = "상대방 선호도", example = "MEDIUM")
        @JsonProperty("theirPreferenceLevel")
        private String theirPreferenceLevel;

        @Schema(description = "선호도 매칭 점수", example = "0.85")
        @JsonProperty("preferenceMatchScore")
        private Double preferenceMatchScore;

        @Schema(description = "이 태그의 중요도", example = "0.92")
        @JsonProperty("importance")
        private Double importance;
    }
}
```

---

## 6. RecommendationHistoryResponse

### 📝 구현 코드
```java
package com.routepick.dto.recommendation.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 추천 이력 정보 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "추천 이력 정보 응답")
public class RecommendationHistoryResponse {

    @Schema(description = "이력 ID", example = "456")
    @JsonProperty("historyId")
    private Long historyId;

    @Schema(description = "추천 루트들")
    @JsonProperty("recommendations")
    private List<RouteRecommendationResponse> recommendations;

    @Schema(description = "추천 생성 일시")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("generatedAt")
    private LocalDateTime generatedAt;

    @Schema(description = "추천 만료 일시")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("expiresAt")
    private LocalDateTime expiresAt;

    @Schema(description = "추천 타입", example = "PERSONALIZED")
    @JsonProperty("recommendationType")
    private String recommendationType;

    @Schema(description = "추천 파라미터")
    @JsonProperty("recommendationParameters")
    private RecommendationParameters parameters;

    @Schema(description = "추천 품질 메트릭")
    @JsonProperty("qualityMetrics")
    private QualityMetrics qualityMetrics;

    @Schema(description = "사용자 피드백 통계")
    @JsonProperty("feedbackStats")
    private FeedbackStats feedbackStats;

    @Schema(description = "추천 상태 (ACTIVE, EXPIRED, REPLACED)", example = "ACTIVE")
    @JsonProperty("status")
    private String status;

    @Schema(description = "추천 성능 점수", example = "8.7")
    @JsonProperty("performanceScore")
    private Double performanceScore;

    /**
     * 추천 파라미터
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "추천 파라미터")
    public static class RecommendationParameters {
        @Schema(description = "요청된 추천 수", example = "20")
        @JsonProperty("requestedCount")
        private Integer requestedCount;

        @Schema(description = "태그 매칭 가중치", example = "0.7")
        @JsonProperty("tagMatchWeight")
        private Double tagMatchWeight;

        @Schema(description = "레벨 매칭 가중치", example = "0.3")
        @JsonProperty("levelMatchWeight")
        private Double levelMatchWeight;

        @Schema(description = "필터 조건들")
        @JsonProperty("filters")
        private java.util.Map<String, Object> filters;

        @Schema(description = "사용된 알고리즘 버전", example = "v2.1")
        @JsonProperty("algorithmVersion")
        private String algorithmVersion;
    }

    /**
     * 품질 메트릭
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "품질 메트릭")
    public static class QualityMetrics {
        @Schema(description = "평균 추천 점수", example = "0.84")
        @JsonProperty("averageRecommendationScore")
        private Double averageRecommendationScore;

        @Schema(description = "다양성 점수", example = "0.72")
        @JsonProperty("diversityScore")
        private Double diversityScore;

        @Schema(description = "새로움 점수", example = "0.65")
        @JsonProperty("noveltyScore")
        private Double noveltyScore;

        @Schema(description = "커버리지 점수", example = "0.89")
        @JsonProperty("coverageScore")
        private Double coverageScore;

        @Schema(description = "정확도 점수", example = "0.78")
        @JsonProperty("accuracyScore")
        private Double accuracyScore;
    }

    /**
     * 피드백 통계
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "피드백 통계")
    public static class FeedbackStats {
        @Schema(description = "총 피드백 수", example = "15")
        @JsonProperty("totalFeedbackCount")
        private Integer totalFeedbackCount;

        @Schema(description = "긍정적 피드백 수", example = "12")
        @JsonProperty("positiveFeedbackCount")
        private Integer positiveFeedbackCount;

        @Schema(description = "부정적 피드백 수", example = "2")
        @JsonProperty("negativeFeedbackCount")
        private Integer negativeFeedbackCount;

        @Schema(description = "중립적 피드백 수", example = "1")
        @JsonProperty("neutralFeedbackCount")
        private Integer neutralFeedbackCount;

        @Schema(description = "평균 만족도", example = "4.1")
        @JsonProperty("averageSatisfactionScore")
        private Double averageSatisfactionScore;

        @Schema(description = "완등률", example = "68.7")
        @JsonProperty("completionRate")
        private Double completionRate;

        @Schema(description = "클릭률", example = "45.3")
        @JsonProperty("clickThroughRate")
        private Double clickThroughRate;
    }
}
```

---

## 📋 구현 완료 사항
✅ **TagResponse** - 태그 기본 정보 + 관련 태그 + 통계  
✅ **UserPreferredTagResponse** - 선호 태그 + 추천 품질 통계  
✅ **RouteRecommendationResponse** - 루트 추천 + 매칭 상세 정보  
✅ **TaggedRouteResponse** - 태그된 루트 + 관련성 점수  
✅ **SimilarUserResponse** - 유사 사용자 + 공통 선호도 분석  
✅ **RecommendationHistoryResponse** - 추천 이력 + 품질 메트릭  

## 🎯 주요 특징
- **풍부한 메타데이터** - 각 응답에 관련 통계와 점수 정보 포함
- **중첩 구조 활용** - Inner class로 관련 정보 그룹화
- **성능 지표 제공** - 추천 품질, 사용자 만족도 등 측정 가능
- **시간 정보 포함** - 생성/수정/만료 시간으로 데이터 생명주기 관리
- **확장 가능한 구조** - 새로운 메트릭 추가 용이
- **완전한 문서화** - Swagger 스키마로 API 문서 자동 생성

## 🔧 데이터 구조 특징
1. **TagResponse**: 관련 태그, 인기도, 사용 통계 포함
2. **UserPreferredTagResponse**: 개인화된 통계와 추천 품질 메트릭
3. **RouteRecommendationResponse**: 상세한 추천 근거와 예상 성공률
4. **TaggedRouteResponse**: 태그별 신뢰도와 사용자별 관련성
5. **SimilarUserResponse**: 다차원 유사도 분석과 공통점 상세
6. **RecommendationHistoryResponse**: 시계열 추천 성능 분석

## 📊 메트릭 및 분석 기능
- **추천 품질 측정**: 정확도, 다양성, 새로움, 커버리지
- **사용자 행동 분석**: 클릭률, 완등률, 만족도
- **성능 모니터링**: 알고리즘 버전별 성능 비교
- **A/B 테스트 지원**: 파라미터별 추천 결과 비교