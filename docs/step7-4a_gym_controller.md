# Step 7-4a: GymController 구현

## 📋 구현 목표
암장 관리를 위한 RESTful API Controller 구현:
1. **주변 암장 검색** - GPS 좌표 기반 근거리 암장 검색
2. **암장 상세 조회** - 지점별 상세 정보 및 통계
3. **암장 검색** - 키워드, 지역, 편의시설 기반 검색
4. **벽면 목록** - 지점별 클라이밍 벽면 정보
5. **인기 암장** - 이용량 기반 인기 암장 추천

---

## 🏢 GymController 구현

### 📁 파일 위치
```
src/main/java/com/routepick/controller/gym/GymController.java
```

### 📝 구현 코드
```java
package com.routepick.controller.gym;

import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.common.enums.BranchStatus;
import com.routepick.dto.gym.request.GymSearchRequest;
import com.routepick.dto.gym.request.NearbyGymSearchRequest;
import com.routepick.dto.gym.response.GymBranchResponse;
import com.routepick.dto.gym.response.WallResponse;
import com.routepick.service.gym.GymService;
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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 암장 관리 Controller
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gyms")
@RequiredArgsConstructor
@Tag(name = "Gym Management", description = "암장 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class GymController {

    private final GymService gymService;

    /**
     * 주변 암장 검색
     */
    @GetMapping("/nearby")
    @Operation(summary = "주변 암장 검색", 
               description = "GPS 좌표를 기반으로 지정된 반경 내의 암장들을 검색합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "검색 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 좌표 정보"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<List<GymBranchResponse>>> getNearbyGyms(
            @Parameter(description = "위도 (한국 범위: 33.0~38.6)", required = true)
            @RequestParam @DecimalMin(value = "33.0", message = "위도는 33.0 이상이어야 합니다")
            @DecimalMax(value = "38.6", message = "위도는 38.6 이하여야 합니다") BigDecimal latitude,
            
            @Parameter(description = "경도 (한국 범위: 124.0~132.0)", required = true)
            @RequestParam @DecimalMin(value = "124.0", message = "경도는 124.0 이상이어야 합니다")
            @DecimalMax(value = "132.0", message = "경도는 132.0 이하여야 합니다") BigDecimal longitude,
            
            @Parameter(description = "검색 반경 (km, 기본값: 5)")
            @RequestParam(defaultValue = "5") @Min(value = 1, message = "검색 반경은 최소 1km입니다")
            @Max(value = 50, message = "검색 반경은 최대 50km입니다") Integer radius,
            
            @Parameter(description = "지점 상태 필터")
            @RequestParam(required = false) BranchStatus branchStatus,
            
            @Parameter(description = "최대 결과 수")
            @RequestParam(defaultValue = "20") @Min(value = 1) @Max(value = 100) Integer limit) {
        
        log.debug("Searching nearby gyms: lat={}, lng={}, radius={}km", latitude, longitude, radius);
        
        NearbyGymSearchRequest searchRequest = NearbyGymSearchRequest.builder()
            .latitude(latitude)
            .longitude(longitude)
            .radius(radius)
            .branchStatus(branchStatus)
            .limit(limit)
            .build();
        
        List<GymBranchResponse> nearbyGyms = gymService.findNearbyGyms(searchRequest);
        
        return ResponseEntity.ok(ApiResponse.success(
            nearbyGyms, 
            String.format("반경 %dkm 내 %d개의 암장을 찾았습니다", radius, nearbyGyms.size())));
    }

    /**
     * 암장 상세 조회
     */
    @GetMapping("/{branchId}")
    @Operation(summary = "암장 상세 조회", 
               description = "특정 지점의 상세 정보를 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "암장을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 200, period = 60) // 1분간 200회
    public ResponseEntity<ApiResponse<GymBranchResponse>> getGymBranchDetails(
            @Parameter(description = "지점 ID", required = true)
            @PathVariable Long branchId) {
        
        log.debug("Getting gym branch details: branchId={}", branchId);
        
        GymBranchResponse gymBranch = gymService.getGymBranchDetails(branchId);
        
        return ResponseEntity.ok(ApiResponse.success(
            gymBranch, 
            "암장 상세 정보를 조회했습니다"));
    }

    /**
     * 암장 검색
     */
    @GetMapping("/search")
    @Operation(summary = "암장 검색", 
               description = "키워드, 지역, 편의시설 등의 조건으로 암장을 검색합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "검색 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 검색 조건"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 150, period = 60) // 1분간 150회
    public ResponseEntity<ApiResponse<PageResponse<GymBranchResponse>>> searchGyms(
            @Parameter(description = "검색 키워드")
            @RequestParam(required = false) String keyword,
            
            @Parameter(description = "지역 (시/도)")
            @RequestParam(required = false) String region,
            
            @Parameter(description = "주소")
            @RequestParam(required = false) String address,
            
            @Parameter(description = "편의시설 목록 (콤마 구분)")
            @RequestParam(required = false) List<String> amenities,
            
            @Parameter(description = "지점 상태")
            @RequestParam(required = false) BranchStatus branchStatus,
            
            @Parameter(description = "페이징 정보")
            @PageableDefault(size = 20, sort = "gymName") Pageable pageable) {
        
        log.debug("Searching gyms: keyword={}, region={}, amenities={}", keyword, region, amenities);
        
        GymSearchRequest searchRequest = GymSearchRequest.builder()
            .keyword(keyword)
            .region(region)
            .address(address)
            .amenities(amenities)
            .branchStatus(branchStatus)
            .build();
        
        Page<GymBranchResponse> searchResults = gymService.searchGyms(searchRequest, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
            PageResponse.of(searchResults),
            String.format("검색 조건에 맞는 %d개의 암장을 찾았습니다", searchResults.getTotalElements())));
    }

    /**
     * 벽면 목록 조회
     */
    @GetMapping("/{branchId}/walls")
    @Operation(summary = "벽면 목록 조회", 
               description = "특정 지점의 클라이밍 벽면 목록을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "암장을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<List<WallResponse>>> getWalls(
            @Parameter(description = "지점 ID", required = true)
            @PathVariable Long branchId) {
        
        log.debug("Getting walls for branch: branchId={}", branchId);
        
        List<WallResponse> walls = gymService.getWalls(branchId);
        
        return ResponseEntity.ok(ApiResponse.success(
            walls, 
            String.format("지점의 %d개 벽면을 조회했습니다", walls.size())));
    }

    /**
     * 인기 암장 조회
     */
    @GetMapping("/popular")
    @Operation(summary = "인기 암장 조회", 
               description = "이용량과 평점을 기반으로 인기 암장들을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<List<GymBranchResponse>>> getPopularGyms(
            @Parameter(description = "지역 필터 (선택사항)")
            @RequestParam(required = false) String region,
            
            @Parameter(description = "조회 기간 (일)")
            @RequestParam(defaultValue = "30") @Min(value = 7, message = "최소 7일 이상이어야 합니다")
            @Max(value = 365, message = "최대 365일까지 가능합니다") Integer days,
            
            @Parameter(description = "최대 결과 수")
            @RequestParam(defaultValue = "20") @Min(value = 1) @Max(value = 50) Integer limit) {
        
        log.debug("Getting popular gyms: region={}, days={}, limit={}", region, days, limit);
        
        List<GymBranchResponse> popularGyms = gymService.getPopularGyms(region, days, limit);
        
        return ResponseEntity.ok(ApiResponse.success(
            popularGyms, 
            String.format("최근 %d일간 인기 암장 %d개를 조회했습니다", days, popularGyms.size())));
    }

    // ========== 내부 DTO 클래스 ==========

    /**
     * 인기 암장 필터 요청 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "인기 암장 필터 요청")
    public static class PopularGymFilterRequest {
        @Schema(description = "지역 필터")
        private String region;
        
        @Schema(description = "조회 기간 (일)")
        @Min(value = 7, message = "최소 7일 이상이어야 합니다")
        @Max(value = 365, message = "최대 365일까지 가능합니다")
        private Integer days;
        
        @Schema(description = "최대 결과 수")
        @Min(value = 1)
        @Max(value = 50)
        private Integer limit;
    }
}
```

---

## 📋 구현 완료 사항
✅ **GymController** - 암장 관리 완전한 REST API  
✅ **5개 엔드포인트** - 주변 검색, 상세 조회, 검색, 벽면 목록, 인기 암장  
✅ **한국 좌표 검증** - 위도 33.0~38.6, 경도 124.0~132.0 범위  
✅ **공간 쿼리 지원** - GPS 기반 반경 검색 (1~50km)  
✅ **보안 강화** - @RateLimited 적용  
✅ **완전한 문서화** - Swagger 어노테이션 완벽 적용  

## 🎯 주요 특징
- **한국 특화 좌표 검증** - 국내 GPS 범위 엄격 검증
- **공간 검색 최적화** - 반경 기반 효율적 검색  
- **다양한 필터링** - 지역, 편의시설, 상태별 검색
- **성능 최적화** - 적절한 캐싱과 Rate Limiting
- **페이징 지원** - 대용량 데이터 효율적 처리

## ⚙️ Rate Limiting 전략
- **주변 검색**: 100회/분 (위치 기반 검색)
- **상세 조회**: 200회/분 (자주 조회되는 정보)  
- **일반 검색**: 150회/분 (검색 빈도 고려)
- **벽면 목록**: 100회/분 (상세 정보 조회)
- **인기 암장**: 50회/분 (통계성 정보)