# Step 7-4b: RouteController 구현

## 📋 구현 목표
루트 관리를 위한 RESTful API Controller 구현:
1. **루트 검색** - 난이도, 태그, 지점별 복합 검색
2. **루트 상세 조회** - 상세 정보 및 통계
3. **인기 루트** - 이용량과 평점 기반 인기 루트
4. **루트 스크랩** - 개인 북마크 기능
5. **루트 태깅** - 태그 추가/조회 시스템
6. **난이도 투표** - 사용자 참여형 난이도 보정

---

## 🧗‍♀️ RouteController 구현

### 📁 파일 위치
```
src/main/java/com/routepick/controller/route/RouteController.java
```

### 📝 구현 코드
```java
package com.routepick.controller.route;

import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.common.enums.GradeSystem;
import com.routepick.common.enums.RouteStatus;
import com.routepick.dto.route.request.DifficultyVoteRequest;
import com.routepick.dto.route.request.RouteSearchRequest;
import com.routepick.dto.route.request.RouteTagRequest;
import com.routepick.dto.route.response.RouteResponse;
import com.routepick.dto.tag.response.TagResponse;
import com.routepick.service.route.RouteService;
import com.routepick.service.route.RouteTaggingService;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 루트 관리 Controller
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Route Management", description = "루트 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class RouteController {

    private final RouteService routeService;
    private final RouteTaggingService routeTaggingService;

    /**
     * 루트 검색
     */
    @GetMapping("/search")
    @Operation(summary = "루트 검색", 
               description = "난이도, 태그, 지점 등의 조건으로 루트를 검색합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "검색 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 검색 조건"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 200, period = 60) // 1분간 200회
    public ResponseEntity<ApiResponse<PageResponse<RouteResponse>>> searchRoutes(
            @Parameter(description = "지점 ID")
            @RequestParam(required = false) Long branchId,
            
            @Parameter(description = "벽면 ID")
            @RequestParam(required = false) Long wallId,
            
            @Parameter(description = "최소 난이도 (V0=1, V16=17)")
            @RequestParam(required = false) @Min(value = 1, message = "난이도는 1 이상이어야 합니다")
            @Max(value = 20, message = "난이도는 20 이하여야 합니다") Integer minDifficulty,
            
            @Parameter(description = "최대 난이도 (V0=1, V16=17)")
            @RequestParam(required = false) @Min(value = 1, message = "난이도는 1 이상이어야 합니다")
            @Max(value = 20, message = "난이도는 20 이하여야 합니다") Integer maxDifficulty,
            
            @Parameter(description = "등급 시스템")
            @RequestParam(required = false) GradeSystem gradeSystem,
            
            @Parameter(description = "루트 상태")
            @RequestParam(required = false) RouteStatus routeStatus,
            
            @Parameter(description = "태그 ID 목록 (콤마 구분)")
            @RequestParam(required = false) List<Long> tags,
            
            @Parameter(description = "색상 (헥스 코드)")
            @RequestParam(required = false) String color,
            
            @Parameter(description = "페이징 정보")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        log.debug("Searching routes: branchId={}, wallId={}, difficulty={}-{}, tags={}", 
                 branchId, wallId, minDifficulty, maxDifficulty, tags);
        
        RouteSearchRequest searchRequest = RouteSearchRequest.builder()
            .branchId(branchId)
            .wallId(wallId)
            .minDifficulty(minDifficulty)
            .maxDifficulty(maxDifficulty)
            .gradeSystem(gradeSystem)
            .routeStatus(routeStatus)
            .tags(tags)
            .color(color)
            .build();
        
        Page<RouteResponse> searchResults = routeService.searchRoutes(searchRequest, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
            PageResponse.of(searchResults),
            String.format("검색 조건에 맞는 %d개의 루트를 찾았습니다", searchResults.getTotalElements())));
    }

    /**
     * 루트 상세 조회
     */
    @GetMapping("/{routeId}")
    @Operation(summary = "루트 상세 조회", 
               description = "특정 루트의 상세 정보를 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "루트를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 300, period = 60) // 1분간 300회
    public ResponseEntity<ApiResponse<RouteResponse>> getRouteDetails(
            @Parameter(description = "루트 ID", required = true)
            @PathVariable Long routeId) {
        
        log.debug("Getting route details: routeId={}", routeId);
        
        RouteResponse route = routeService.getRouteDetails(routeId);
        
        return ResponseEntity.ok(ApiResponse.success(
            route, 
            "루트 상세 정보를 조회했습니다"));
    }

    /**
     * 인기 루트 조회
     */
    @GetMapping("/popular")
    @Operation(summary = "인기 루트 조회", 
               description = "이용량과 평점을 기반으로 인기 루트들을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<List<RouteResponse>>> getPopularRoutes(
            @Parameter(description = "지점 ID 필터")
            @RequestParam(required = false) Long branchId,
            
            @Parameter(description = "난이도 범위 시작")
            @RequestParam(required = false) @Min(1) @Max(20) Integer minDifficulty,
            
            @Parameter(description = "난이도 범위 끝")
            @RequestParam(required = false) @Min(1) @Max(20) Integer maxDifficulty,
            
            @Parameter(description = "조회 기간 (일)")
            @RequestParam(defaultValue = "7") @Min(value = 1, message = "최소 1일 이상이어야 합니다")
            @Max(value = 90, message = "최대 90일까지 가능합니다") Integer days,
            
            @Parameter(description = "최대 결과 수")
            @RequestParam(defaultValue = "20") @Min(value = 1) @Max(value = 50) Integer limit) {
        
        log.debug("Getting popular routes: branchId={}, difficulty={}-{}, days={}, limit={}", 
                 branchId, minDifficulty, maxDifficulty, days, limit);
        
        List<RouteResponse> popularRoutes = routeService.getPopularRoutes(
            branchId, minDifficulty, maxDifficulty, days, limit);
        
        return ResponseEntity.ok(ApiResponse.success(
            popularRoutes, 
            String.format("최근 %d일간 인기 루트 %d개를 조회했습니다", days, popularRoutes.size())));
    }

    /**
     * 루트 스크랩
     */
    @PostMapping("/{routeId}/scrap")
    @Operation(summary = "루트 스크랩", 
               description = "루트를 개인 북마크에 추가합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "스크랩 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "이미 스크랩된 루트"),
        @SwaggerApiResponse(responseCode = "404", description = "루트를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<Void>> scrapRoute(
            @Parameter(description = "루트 ID", required = true)
            @PathVariable Long routeId,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Scrapping route: userId={}, routeId={}", userId, routeId);
        
        routeService.scrapRoute(userId, routeId);
        
        return ResponseEntity.ok(ApiResponse.success(
            null, 
            "루트가 스크랩되었습니다"));
    }

    /**
     * 루트 스크랩 해제
     */
    @DeleteMapping("/{routeId}/scrap")
    @Operation(summary = "루트 스크랩 해제", 
               description = "루트를 개인 북마크에서 제거합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "스크랩 해제 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "스크랩을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<Void>> unscrapRoute(
            @Parameter(description = "루트 ID", required = true)
            @PathVariable Long routeId,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Unscrapping route: userId={}, routeId={}", userId, routeId);
        
        routeService.unscrapRoute(userId, routeId);
        
        return ResponseEntity.ok(ApiResponse.success(
            null, 
            "루트 스크랩이 해제되었습니다"));
    }

    /**
     * 루트 태깅
     */
    @PostMapping("/{routeId}/tags")
    @Operation(summary = "루트 태깅", 
               description = "루트에 태그를 추가합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "태그 추가 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 태그 정보"),
        @SwaggerApiResponse(responseCode = "404", description = "루트 또는 태그를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<Void>> tagRoute(
            @Parameter(description = "루트 ID", required = true)
            @PathVariable Long routeId,
            
            @Parameter(description = "태그 정보", required = true)
            @Valid @RequestBody RouteTagRequest request,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Tagging route: userId={}, routeId={}, tagId={}, relevance={}", 
                userId, routeId, request.getTagId(), request.getRelevanceScore());
        
        routeTaggingService.tagRoute(userId, routeId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            null, 
            "루트에 태그가 추가되었습니다"));
    }

    /**
     * 루트 태그 조회
     */
    @GetMapping("/{routeId}/tags")
    @Operation(summary = "루트 태그 조회", 
               description = "루트에 연결된 태그 목록을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "루트를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 200, period = 60) // 1분간 200회
    public ResponseEntity<ApiResponse<List<TagResponse>>> getRouteTags(
            @Parameter(description = "루트 ID", required = true)
            @PathVariable Long routeId) {
        
        log.debug("Getting route tags: routeId={}", routeId);
        
        List<TagResponse> tags = routeTaggingService.getRouteTags(routeId);
        
        return ResponseEntity.ok(ApiResponse.success(
            tags, 
            String.format("루트의 %d개 태그를 조회했습니다", tags.size())));
    }

    /**
     * 난이도 투표
     */
    @PostMapping("/{routeId}/vote-difficulty")
    @Operation(summary = "난이도 투표", 
               description = "루트의 체감 난이도에 대해 투표합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "투표 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 투표 정보"),
        @SwaggerApiResponse(responseCode = "404", description = "루트를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "409", description = "이미 투표한 루트"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 60) // 1분간 30회
    public ResponseEntity<ApiResponse<DifficultyVoteResponse>> voteDifficulty(
            @Parameter(description = "루트 ID", required = true)
            @PathVariable Long routeId,
            
            @Parameter(description = "난이도 투표 정보", required = true)
            @Valid @RequestBody DifficultyVoteRequest request,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Voting difficulty: userId={}, routeId={}, suggested={}, reason={}", 
                userId, routeId, request.getSuggestedDifficulty(), request.getVoteReason());
        
        DifficultyVoteResponse voteResult = routeService.voteDifficulty(userId, routeId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            voteResult, 
            "난이도 투표가 완료되었습니다"));
    }

    // ========== 내부 DTO 클래스 ==========

    /**
     * 난이도 투표 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "난이도 투표 응답")
    public static class DifficultyVoteResponse {
        @Schema(description = "투표 ID")
        private Long voteId;
        
        @Schema(description = "제안된 난이도")
        private Integer suggestedDifficulty;
        
        @Schema(description = "현재 평균 난이도")
        private Double currentAverageDifficulty;
        
        @Schema(description = "총 투표 수")
        private Integer totalVotes;
        
        @Schema(description = "투표 완료 시간")
        private java.time.LocalDateTime votedAt;
    }

    /**
     * 인기 루트 필터 요청 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "인기 루트 필터 요청")
    public static class PopularRouteFilterRequest {
        @Schema(description = "지점 ID 필터")
        private Long branchId;
        
        @Schema(description = "최소 난이도")
        @Min(1) @Max(20)
        private Integer minDifficulty;
        
        @Schema(description = "최대 난이도")
        @Min(1) @Max(20)
        private Integer maxDifficulty;
        
        @Schema(description = "조회 기간 (일)")
        @Min(value = 1) @Max(value = 90)
        private Integer days;
        
        @Schema(description = "최대 결과 수")
        @Min(value = 1) @Max(value = 50)
        private Integer limit;
    }
}
```

---

## 📋 구현 완료 사항
✅ **RouteController** - 루트 관리 완전한 REST API  
✅ **8개 엔드포인트** - 검색, 상세 조회, 인기 루트, 스크랩, 태깅, 난이도 투표  
✅ **V등급/YDS 지원** - 난이도 1~20 범위 (V0~V16+ 대응)  
✅ **태그 시스템 연동** - 루트-태그 매핑 및 관련도 점수  
✅ **사용자 참여 기능** - 스크랩, 태깅, 난이도 투표  
✅ **보안 강화** - @PreAuthorize, @RateLimited 적용  
✅ **완전한 문서화** - Swagger 어노테이션 완벽 적용  

## 🎯 주요 특징
- **복합 검색 지원** - 난이도, 태그, 지점, 색상 다중 필터
- **사용자 참여형** - 스크랩, 태깅, 난이도 투표 시스템
- **실시간 통계** - 인기도 기반 추천 및 랭킹
- **태그 연관도** - 0.0~1.0 점수 기반 태그 품질 관리
- **성능 최적화** - 적절한 캐싱과 Rate Limiting

## ⚙️ Rate Limiting 전략
- **루트 검색**: 200회/분 (복합 검색 지원)
- **상세 조회**: 300회/분 (자주 조회되는 정보)
- **인기 루트**: 50회/분 (통계성 정보)
- **스크랩**: 100회/분 (개인 북마크 관리)
- **태깅**: 50회/분 (품질 관리를 위한 제한)
- **난이도 투표**: 30회/분 (신중한 참여 유도)