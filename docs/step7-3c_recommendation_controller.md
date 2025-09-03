# Step 7-3c: RecommendationController 구현

## 📋 구현 목표
AI 기반 개인화 추천 시스템 REST API Controller 구현:
1. **개인화된 루트 추천** - 태그 매칭 70% + 레벨 매칭 30%
2. **추천 재계산 API** - 실시간 추천 업데이트
3. **추천 이력 관리** - 사용자별 추천 히스토리
4. **유사 사용자 발견** - 비슷한 선호도의 다른 사용자
5. **성능 최적화** - Redis 캐싱 1시간 TTL

---

## 🤖 RecommendationController 구현

### 📁 파일 위치
```
src/main/java/com/routepick/controller/recommendation/RecommendationController.java
```

### 📝 구현 코드
```java
package com.routepick.controller.recommendation;

import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.recommendation.request.RecommendationRefreshRequest;
import com.routepick.dto.recommendation.request.RouteRecommendationRequest;
import com.routepick.dto.recommendation.response.RecommendationHistoryResponse;
import com.routepick.dto.recommendation.response.RouteRecommendationResponse;
import com.routepick.dto.recommendation.response.SimilarUserResponse;
import com.routepick.service.recommendation.RecommendationService;
import com.routepick.annotation.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 추천 시스템 Controller
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendation System", description = "AI 기반 개인화 추천 시스템 API")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 개인화된 루트 추천
     */
    @GetMapping("/routes")
    @Operation(summary = "개인화된 루트 추천", 
               description = "사용자의 선호도와 실력을 기반으로 개인화된 루트를 추천합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "추천 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "추천 데이터 부족"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<List<RouteRecommendationResponse>>> getPersonalizedRoutes(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "추천 루트 수")
            @RequestParam(defaultValue = "20") 
            @Min(value = 1, message = "최소 1개 이상이어야 합니다") 
            @Max(value = 50, message = "최대 50개까지 가능합니다") Integer limit,
            
            @Parameter(description = "완등한 루트 포함 여부")
            @RequestParam(defaultValue = "false") Boolean includeCompleted,
            
            @Parameter(description = "특정 체육관 필터")
            @RequestParam(required = false) Long gymId,
            
            @Parameter(description = "난이도 범위 필터")
            @RequestParam(required = false) String difficultyRange,
            
            @Parameter(description = "추천 타입 (PERSONALIZED, POPULAR, SIMILAR_USERS)")
            @RequestParam(defaultValue = "PERSONALIZED") String recommendationType) {
        
        log.debug("Getting personalized routes for user: userId={}, limit={}, type={}", 
                 userId, limit, recommendationType);
        
        RouteRecommendationRequest request = RouteRecommendationRequest.builder()
            .limit(limit)
            .includeCompleted(includeCompleted)
            .gymId(gymId)
            .difficultyRange(difficultyRange)
            .recommendationType(recommendationType)
            .build();
        
        List<RouteRecommendationResponse> recommendations = recommendationService
            .getPersonalizedRoutes(userId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            recommendations, 
            String.format("%s 방식으로 %d개의 루트를 추천했습니다", 
                         recommendationType, recommendations.size())));
    }

    /**
     * 추천 재계산 요청
     */
    @PostMapping("/refresh")
    @Operation(summary = "추천 재계산", 
               description = "사용자의 추천 결과를 재계산합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "재계산 시작"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "429", description = "너무 많은 요청"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 5, period = 300) // 5분간 5회 (재계산은 리소스 집약적)
    public ResponseEntity<ApiResponse<RecommendationRefreshResponse>> refreshRecommendations(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "추천 재계산 옵션")
            @Valid @RequestBody(required = false) RecommendationRefreshRequest request) {
        
        log.info("Refreshing recommendations for user: userId={}, forceRecalculate={}", 
                userId, request != null ? request.isForceRecalculate() : false);
        
        // 비동기로 추천 재계산 실행
        CompletableFuture<Void> refreshTask = recommendationService.refreshUserRecommendationsAsync(
            userId, request != null ? request.isForceRecalculate() : false);
        
        RecommendationRefreshResponse response = RecommendationRefreshResponse.builder()
            .userId(userId)
            .refreshStartedAt(java.time.LocalDateTime.now())
            .estimatedCompletionTime(java.time.LocalDateTime.now().plusMinutes(5))
            .isAsync(true)
            .build();
        
        return ResponseEntity.ok(ApiResponse.success(
            response, 
            "추천 재계산이 시작되었습니다. 약 5분 후 완료됩니다"));
    }

    /**
     * 추천 이력 조회
     */
    @GetMapping("/history")
    @Operation(summary = "추천 이력 조회", 
               description = "사용자의 과거 추천 이력을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 60) // 1분간 30회
    public ResponseEntity<ApiResponse<PageResponse<RecommendationHistoryResponse>>> getRecommendationHistory(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "페이징 정보")
            @PageableDefault(size = 20, sort = "generatedAt") Pageable pageable,
            
            @Parameter(description = "기간 필터 (일 수)")
            @RequestParam(defaultValue = "30") 
            @Min(value = 1, message = "최소 1일 이상이어야 합니다") 
            @Max(value = 365, message = "최대 365일까지 가능합니다") Integer days) {
        
        log.debug("Getting recommendation history for user: userId={}, days={}", userId, days);
        
        Page<RecommendationHistoryResponse> historyPage = recommendationService
            .getRecommendationHistory(userId, days, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
            PageResponse.of(historyPage), 
            String.format("최근 %d일간 추천 이력을 조회했습니다", days)));
    }

    /**
     * 유사 선호도 사용자 조회
     */
    @GetMapping("/similar-users")
    @Operation(summary = "유사 선호도 사용자 조회", 
               description = "비슷한 선호도를 가진 다른 사용자들을 찾습니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "유사 사용자 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 20, period = 60) // 1분간 20회
    public ResponseEntity<ApiResponse<List<SimilarUserResponse>>> getSimilarUsers(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "최소 유사도 점수 (0.0 ~ 1.0)")
            @RequestParam(defaultValue = "0.7") 
            @Min(value = 0, message = "0 이상이어야 합니다")
            @Max(value = 1, message = "1 이하여야 합니다") Double minSimilarityScore,
            
            @Parameter(description = "최대 결과 수")
            @RequestParam(defaultValue = "10") 
            @Min(value = 1, message = "최소 1명 이상이어야 합니다") 
            @Max(value = 50, message = "최대 50명까지 가능합니다") Integer limit) {
        
        log.debug("Getting similar users: userId={}, minScore={}, limit={}", 
                 userId, minSimilarityScore, limit);
        
        List<SimilarUserResponse> similarUsers = recommendationService
            .getSimilarUsers(userId, minSimilarityScore, limit);
        
        return ResponseEntity.ok(ApiResponse.success(
            similarUsers, 
            String.format("유사도 %.1f 이상인 %d명의 사용자를 찾았습니다", 
                         minSimilarityScore, similarUsers.size())));
    }

    /**
     * 트렌딩 루트 조회
     */
    @GetMapping("/trending")
    @Operation(summary = "트렌딩 루트 조회", 
               description = "최근 인기 있는 트렌딩 루트들을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 100, period = 60) // 1분간 100회 (공개 API)
    public ResponseEntity<ApiResponse<List<TrendingRouteResponse>>> getTrendingRoutes(
            @Parameter(description = "기간 설정 (DAILY, WEEKLY, MONTHLY)")
            @RequestParam(defaultValue = "WEEKLY") String period,
            
            @Parameter(description = "특정 체육관 필터")
            @RequestParam(required = false) Long gymId,
            
            @Parameter(description = "난이도 범위")
            @RequestParam(required = false) String difficultyRange,
            
            @Parameter(description = "최대 결과 수")
            @RequestParam(defaultValue = "20") 
            @Min(value = 1, message = "최소 1개 이상이어야 합니다") 
            @Max(value = 100, message = "최대 100개까지 가능합니다") Integer limit) {
        
        log.debug("Getting trending routes: period={}, gymId={}, limit={}", period, gymId, limit);
        
        List<TrendingRouteResponse> trendingRoutes = recommendationService
            .getTrendingRoutes(period, gymId, difficultyRange, limit);
        
        return ResponseEntity.ok(ApiResponse.success(
            trendingRoutes, 
            String.format("%s 기간 트렌딩 루트 %d개를 조회했습니다", period, trendingRoutes.size())));
    }

    /**
     * 추천 피드백 제출
     */
    @PostMapping("/feedback")
    @Operation(summary = "추천 피드백 제출", 
               description = "추천 결과에 대한 사용자 피드백을 제출합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "피드백 제출 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 피드백 데이터"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<Void>> submitRecommendationFeedback(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "추천 피드백 정보", required = true)
            @Valid @RequestBody RecommendationFeedbackRequest request) {
        
        log.info("Submitting recommendation feedback: userId={}, routeId={}, rating={}", 
                userId, request.getRouteId(), request.getRating());
        
        recommendationService.submitFeedback(userId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            null, 
            "추천 피드백이 제출되었습니다. 향후 추천 품질 향상에 활용됩니다"));
    }

    // ========== Response DTO Classes ==========

    /**
     * 추천 재계산 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "추천 재계산 응답")
    public static class RecommendationRefreshResponse {
        @Schema(description = "사용자 ID")
        private Long userId;
        
        @Schema(description = "재계산 시작 시간")
        private java.time.LocalDateTime refreshStartedAt;
        
        @Schema(description = "예상 완료 시간")
        private java.time.LocalDateTime estimatedCompletionTime;
        
        @Schema(description = "비동기 처리 여부")
        private Boolean isAsync;
        
        @Schema(description = "진행률 (%)")
        private Double progress;
    }

    /**
     * 트렌딩 루트 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "트렌딩 루트 응답")
    public static class TrendingRouteResponse {
        @Schema(description = "루트 기본 정보")
        private com.routepick.dto.route.response.RouteBasicResponse route;
        
        @Schema(description = "트렌딩 점수")
        private Double trendingScore;
        
        @Schema(description = "최근 완등 횟수")
        private Integer recentClimbCount;
        
        @Schema(description = "평균 평점")
        private Double averageRating;
        
        @Schema(description = "인기 상승률 (%)")
        private Double popularityGrowthRate;
        
        @Schema(description = "트렌딩 기간")
        private String trendingPeriod;
        
        @Schema(description = "순위 변동")
        private Integer rankChange;
    }

    /**
     * 추천 피드백 요청 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "추천 피드백 요청")
    public static class RecommendationFeedbackRequest {
        @Schema(description = "루트 ID", required = true)
        @jakarta.validation.constraints.NotNull(message = "루트 ID는 필수입니다")
        private Long routeId;
        
        @Schema(description = "추천 ID")
        private Long recommendationId;
        
        @Schema(description = "평점 (1-5)", required = true)
        @jakarta.validation.constraints.NotNull(message = "평점은 필수입니다")
        @jakarta.validation.constraints.Min(value = 1, message = "평점은 1 이상이어야 합니다")
        @jakarta.validation.constraints.Max(value = 5, message = "평점은 5 이하여야 합니다")
        private Integer rating;
        
        @Schema(description = "피드백 타입 (LIKE, DISLIKE, COMPLETED, NOT_INTERESTED)")
        @jakarta.validation.constraints.NotBlank(message = "피드백 타입은 필수입니다")
        private String feedbackType;
        
        @Schema(description = "추가 코멘트")
        @jakarta.validation.constraints.Size(max = 500, message = "코멘트는 500자 이내여야 합니다")
        private String comment;
        
        @Schema(description = "추천 정확도 점수 (1-5)")
        @jakarta.validation.constraints.Min(value = 1, message = "정확도 점수는 1 이상이어야 합니다")
        @jakarta.validation.constraints.Max(value = 5, message = "정확도 점수는 5 이하여야 합니다")
        private Integer accuracyScore;
    }
}
```

---

## 📋 구현 완료 사항
✅ **RecommendationController** - AI 기반 추천 시스템 완전한 REST API  
✅ **6개 주요 엔드포인트** - 개인화 추천, 재계산, 이력, 유사 사용자, 트렌딩, 피드백  
✅ **비동기 처리** - 추천 재계산의 백그라운드 처리  
✅ **다양한 추천 타입** - 개인화, 인기, 유사 사용자 기반  
✅ **피드백 루프** - 사용자 피드백을 통한 추천 품질 향상  
✅ **완전한 문서화** - Swagger 어노테이션 완벽 적용  

## 🎯 주요 특징
- **AI 기반 추천** - 태그 매칭(70%) + 레벨 매칭(30%) 알고리즘
- **실시간 추천** - 사용자 활동 기반 동적 업데이트
- **다양한 필터링** - 체육관, 난이도, 완등 여부별 필터
- **유사 사용자 매칭** - 선호도 기반 사용자 발견
- **트렌딩 시스템** - 실시간 인기 루트 추적
- **피드백 시스템** - 사용자 반응 기반 추천 개선

## ⚙️ Rate Limiting 전략
- **추천 조회**: 50회/분 (개인화 추천)
- **재계산 요청**: 5회/5분 (리소스 집약적)
- **이력/유사 사용자**: 20-30회/분 (분석 데이터)
- **트렌딩**: 100회/분 (공개 API)
- **피드백**: 100회/분 (실시간 반응)

## 🚀 성능 최적화
- **Redis 캐싱** - 1시간 TTL로 추천 결과 캐싱
- **비동기 처리** - CompletableFuture를 활용한 백그라운드 재계산
- **배치 처리** - 대량 추천 계산 시 청크 단위 처리
- **인덱스 최적화** - 추천 점수 기반 정렬 쿼리 최적화