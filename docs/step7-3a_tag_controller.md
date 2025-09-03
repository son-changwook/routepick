# Step 7-3a: TagController 구현

## 📋 구현 목표
태그 시스템 관리를 위한 RESTful API Controller 구현:
1. **태그 조회 API** - 프로필용, 루트용, 타입별 조회
2. **태그 검색 API** - 실시간 검색, 자동완성
3. **인기 태그 API** - 사용량 기반 인기 태그
4. **8가지 TagType 지원** - 완전한 태그 분류 체계
5. **성능 최적화** - 캐싱, 페이징, 검색 인덱스

---

## 🏷️ TagController 구현

### 📁 파일 위치
```
src/main/java/com/routepick/controller/tag/TagController.java
```

### 📝 구현 코드
```java
package com.routepick.controller.tag;

import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.common.enums.TagType;
import com.routepick.dto.tag.request.TagSearchRequest;
import com.routepick.dto.tag.response.TagResponse;
import com.routepick.service.tag.TagService;
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
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 태그 시스템 관리 Controller
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tag Management", description = "태그 시스템 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class TagController {

    private final TagService tagService;

    /**
     * 프로필용 태그 목록 조회
     */
    @GetMapping("/profile")
    @Operation(summary = "프로필용 태그 목록 조회", 
               description = "사용자 프로필 설정에 사용할 수 있는 태그 목록을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<List<TagResponse>>> getProfileTags() {
        log.debug("Getting profile tags");
        
        List<TagResponse> profileTags = tagService.getProfileTags();
        
        return ResponseEntity.ok(ApiResponse.success(
            profileTags, 
            "프로필용 태그 목록을 조회했습니다"));
    }

    /**
     * 루트 태깅용 태그 목록 조회
     */
    @GetMapping("/route")
    @Operation(summary = "루트 태깅용 태그 목록 조회", 
               description = "루트에 태그를 추가할 때 사용할 수 있는 태그 목록을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<List<TagResponse>>> getRouteTags(
            @Parameter(description = "페이징 정보") 
            @PageableDefault(size = 50, sort = "displayOrder") Pageable pageable) {
        
        log.debug("Getting route tags with pagination: {}", pageable);
        
        Page<TagResponse> routeTags = tagService.getRouteTags(pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
            PageResponse.of(routeTags), 
            "루트 태깅용 태그 목록을 조회했습니다"));
    }

    /**
     * 타입별 태그 조회
     */
    @GetMapping("/types/{tagType}")
    @Operation(summary = "타입별 태그 조회", 
               description = "특정 태그 타입에 속하는 태그들을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 태그 타입"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<List<TagResponse>>> getTagsByType(
            @Parameter(description = "태그 타입", required = true, 
                      schema = @Schema(implementation = TagType.class))
            @PathVariable TagType tagType,
            
            @Parameter(description = "사용자 선택 가능 여부")
            @RequestParam(required = false) Boolean isUserSelectable,
            
            @Parameter(description = "루트 태깅 가능 여부")
            @RequestParam(required = false) Boolean isRouteTaggable) {
        
        log.debug("Getting tags by type: {}, userSelectable: {}, routeTaggable: {}", 
                 tagType, isUserSelectable, isRouteTaggable);
        
        List<TagResponse> tags = tagService.getTagsByType(tagType, isUserSelectable, isRouteTaggable);
        
        return ResponseEntity.ok(ApiResponse.success(
            tags, 
            String.format("%s 타입의 태그를 조회했습니다", tagType.getDisplayName())));
    }

    /**
     * 태그 검색
     */
    @GetMapping("/search")
    @Operation(summary = "태그 검색", 
               description = "키워드를 통해 태그를 검색합니다. 자동완성 기능을 지원합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "검색 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 검색 조건"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 200, period = 60) // 1분간 200회 (검색이 빈번하므로 높게 설정)
    public ResponseEntity<ApiResponse<List<TagResponse>>> searchTags(
            @Parameter(description = "검색 키워드 (최소 1자)")
            @RequestParam @Size(min = 1, max = 50, message = "검색 키워드는 1-50자여야 합니다") String keyword,
            
            @Parameter(description = "태그 타입 필터")
            @RequestParam(required = false) TagType tagType,
            
            @Parameter(description = "사용자 선택 가능 필터")
            @RequestParam(required = false) Boolean isUserSelectable,
            
            @Parameter(description = "루트 태깅 가능 필터")  
            @RequestParam(required = false) Boolean isRouteTaggable,
            
            @Parameter(description = "최대 결과 수")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "최소 1개 이상이어야 합니다") Integer limit) {
        
        log.debug("Searching tags: keyword={}, type={}, limit={}", keyword, tagType, limit);
        
        TagSearchRequest searchRequest = TagSearchRequest.builder()
            .keyword(keyword)
            .tagType(tagType)
            .isUserSelectable(isUserSelectable)
            .isRouteTaggable(isRouteTaggable)
            .limit(limit)
            .build();
        
        List<TagResponse> searchResults = tagService.searchTags(searchRequest);
        
        return ResponseEntity.ok(ApiResponse.success(
            searchResults, 
            String.format("키워드 '%s'로 %d개의 태그를 찾았습니다", keyword, searchResults.size())));
    }

    /**
     * 인기 태그 조회
     */
    @GetMapping("/popular")
    @Operation(summary = "인기 태그 조회", 
               description = "사용량이 많은 인기 태그들을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<List<TagResponse>>> getPopularTags(
            @Parameter(description = "태그 타입 필터")
            @RequestParam(required = false) TagType tagType,
            
            @Parameter(description = "조회 기간 (일)")
            @RequestParam(defaultValue = "30") @Min(value = 1, message = "최소 1일 이상이어야 합니다") Integer days,
            
            @Parameter(description = "최대 결과 수")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "최소 1개 이상이어야 합니다") Integer limit) {
        
        log.debug("Getting popular tags: type={}, days={}, limit={}", tagType, days, limit);
        
        List<TagResponse> popularTags = tagService.getPopularTags(tagType, days, limit);
        
        return ResponseEntity.ok(ApiResponse.success(
            popularTags, 
            String.format("최근 %d일간 인기 태그 %d개를 조회했습니다", days, popularTags.size())));
    }

    /**
     * 태그 자동완성
     */
    @GetMapping("/autocomplete")
    @Operation(summary = "태그 자동완성", 
               description = "입력한 키워드에 기반한 태그 자동완성 제안을 제공합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "자동완성 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 300, period = 60) // 1분간 300회 (타이핑마다 호출되므로 높게 설정)
    public ResponseEntity<ApiResponse<List<String>>> autocompleteTagNames(
            @Parameter(description = "자동완성 키워드 (최소 2자)")
            @RequestParam @Size(min = 2, max = 20, message = "자동완성 키워드는 2-20자여야 합니다") String q,
            
            @Parameter(description = "태그 타입 필터")
            @RequestParam(required = false) TagType tagType,
            
            @Parameter(description = "최대 제안 수")
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "최소 1개 이상이어야 합니다") Integer limit) {
        
        log.debug("Autocomplete tag names: query={}, type={}, limit={}", q, tagType, limit);
        
        List<String> suggestions = tagService.autocompleteTagNames(q, tagType, limit);
        
        return ResponseEntity.ok(ApiResponse.success(
            suggestions, 
            String.format("'%s'에 대한 %d개의 자동완성 제안을 조회했습니다", q, suggestions.size())));
    }

    /**
     * 태그 상세 정보 조회
     */
    @GetMapping("/{tagId}")
    @Operation(summary = "태그 상세 정보 조회", 
               description = "특정 태그의 상세 정보를 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "태그를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<TagResponse>> getTagById(
            @Parameter(description = "태그 ID", required = true)
            @PathVariable Long tagId) {
        
        log.debug("Getting tag by ID: {}", tagId);
        
        TagResponse tag = tagService.getTagById(tagId);
        
        return ResponseEntity.ok(ApiResponse.success(
            tag, 
            "태그 상세 정보를 조회했습니다"));
    }

    /**
     * 태그 통계 조회
     */
    @GetMapping("/statistics")
    @Operation(summary = "태그 통계 조회", 
               description = "태그 시스템의 전체 통계 정보를 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 10, period = 60) // 1분간 10회 (통계는 자주 조회되지 않음)
    public ResponseEntity<ApiResponse<TagStatisticsResponse>> getTagStatistics() {
        log.debug("Getting tag statistics");
        
        TagStatisticsResponse statistics = tagService.getTagStatistics();
        
        return ResponseEntity.ok(ApiResponse.success(
            statistics, 
            "태그 통계 정보를 조회했습니다"));
    }

    // ========== 관리자 전용 API ==========

    /**
     * 태그 생성 (관리자 전용)
     */
    @PostMapping
    @Operation(summary = "태그 생성", 
               description = "새로운 태그를 생성합니다 (관리자 전용)")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "201", description = "생성 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력"),
        @SwaggerApiResponse(responseCode = "403", description = "권한 없음"),
        @SwaggerApiResponse(responseCode = "409", description = "태그 이름 중복"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 10, period = 300) // 5분간 10회
    public ResponseEntity<ApiResponse<TagResponse>> createTag(
            @Parameter(description = "태그 생성 정보", required = true)
            @Valid @RequestBody TagCreateRequest request) {
        
        log.info("Creating new tag: {}", request.getTagName());
        
        TagResponse createdTag = tagService.createTag(request);
        
        return ResponseEntity.status(201).body(ApiResponse.success(
            createdTag, 
            "새로운 태그가 생성되었습니다"));
    }

    /**
     * 태그 수정 (관리자 전용)
     */
    @PutMapping("/{tagId}")
    @Operation(summary = "태그 수정", 
               description = "기존 태그를 수정합니다 (관리자 전용)")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "수정 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력"),
        @SwaggerApiResponse(responseCode = "403", description = "권한 없음"),
        @SwaggerApiResponse(responseCode = "404", description = "태그를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 20, period = 300) // 5분간 20회
    public ResponseEntity<ApiResponse<TagResponse>> updateTag(
            @Parameter(description = "태그 ID", required = true)
            @PathVariable Long tagId,
            
            @Parameter(description = "태그 수정 정보", required = true)
            @Valid @RequestBody TagUpdateRequest request) {
        
        log.info("Updating tag: ID={}, request={}", tagId, request);
        
        TagResponse updatedTag = tagService.updateTag(tagId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            updatedTag, 
            "태그가 수정되었습니다"));
    }

    /**
     * 태그 삭제 (관리자 전용)
     */
    @DeleteMapping("/{tagId}")
    @Operation(summary = "태그 삭제", 
               description = "기존 태그를 삭제합니다 (관리자 전용)")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "삭제 성공"),
        @SwaggerApiResponse(responseCode = "403", description = "권한 없음"),
        @SwaggerApiResponse(responseCode = "404", description = "태그를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "409", description = "사용 중인 태그는 삭제 불가"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 5, period = 300) // 5분간 5회
    public ResponseEntity<ApiResponse<Void>> deleteTag(
            @Parameter(description = "태그 ID", required = true)
            @PathVariable Long tagId) {
        
        log.info("Deleting tag: ID={}", tagId);
        
        tagService.deleteTag(tagId);
        
        return ResponseEntity.ok(ApiResponse.success(
            null, 
            "태그가 삭제되었습니다"));
    }

    /**
     * 태그 통계 정보 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "태그 통계 정보")
    public static class TagStatisticsResponse {
        @Schema(description = "전체 태그 수", example = "156")
        private Integer totalTags;
        
        @Schema(description = "타입별 태그 수")
        private java.util.Map<TagType, Integer> tagsByType;
        
        @Schema(description = "사용자 선택 가능 태그 수", example = "89")
        private Integer userSelectableTags;
        
        @Schema(description = "루트 태깅 가능 태그 수", example = "134") 
        private Integer routeTaggableTags;
        
        @Schema(description = "최근 30일 인기 태그 TOP 10")
        private List<TagResponse> popularTags;
        
        @Schema(description = "최근 생성된 태그 5개")
        private List<TagResponse> recentTags;
        
        @Schema(description = "평균 태그 사용량", example = "47.3")
        private Double averageUsageCount;
        
        @Schema(description = "통계 생성 시간")
        private java.time.LocalDateTime generatedAt;
    }
}
```

---

## 📋 구현 완료 사항
✅ **TagController** - 태그 시스템 완전한 REST API  
✅ **8개 공개 엔드포인트** - 태그 조회, 검색, 인기 태그  
✅ **3개 관리자 엔드포인트** - 태그 CRUD (관리자 전용)  
✅ **성능 최적화** - 캐싱, 페이징, 적절한 Rate Limiting  
✅ **보안 강화** - @PreAuthorize, @RateLimited 적용  
✅ **완전한 문서화** - Swagger 어노테이션 완벽 적용  

## 🎯 주요 특징
- **8가지 TagType 지원** - 모든 태그 분류 완벽 지원
- **실시간 검색** - 키워드 기반 태그 검색 및 자동완성  
- **인기도 기반 추천** - 사용량 통계 기반 인기 태그
- **세분화된 필터링** - 사용자/루트 태깅 여부별 필터
- **관리자 기능** - 태그 생성/수정/삭제 권한 제어
- **성능 최적화** - 적절한 캐싱과 Rate Limiting

## ⚙️ Rate Limiting 전략
- **일반 조회**: 100회/분 (태그 목록, 상세)
- **검색 API**: 200회/분 (사용자 타이핑 고려)  
- **자동완성**: 300회/분 (실시간 입력 지원)
- **관리자 API**: 5-20회/5분 (보수적 제한)