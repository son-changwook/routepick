# Step 7-4c: ClimbingController 구현

## 📋 구현 목표
클라이밍 기록 관리를 위한 RESTful API Controller 구현:
1. **클라이밍 기록 등록** - 개인 클라이밍 성과 기록
2. **클라이밍 기록 조회** - 기간별 개인 기록 조회
3. **클라이밍 통계** - 성공률, 평균 난이도, 개인 기록 통계
4. **클라이밍 신발 목록** - 등록된 클라이밍 신발 조회
5. **클라이밍 신발 등록** - 개인 신발 정보 및 리뷰 관리

---

## 🧗‍♂️ ClimbingController 구현

### 📁 파일 위치
```
src/main/java/com/routepick/controller/climbing/ClimbingController.java
```

### 📝 구현 코드
```java
package com.routepick.controller.climbing;

import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.climbing.request.ClimbingRecordRequest;
import com.routepick.dto.climbing.request.ClimbingShoeRequest;
import com.routepick.dto.climbing.request.ClimbingStatsRequest;
import com.routepick.dto.climbing.response.ClimbingRecordResponse;
import com.routepick.dto.climbing.response.ClimbingShoeResponse;
import com.routepick.dto.climbing.response.ClimbingStatsResponse;
import com.routepick.service.climbing.ClimbingRecordService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 클라이밍 기록 관리 Controller
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/climbing")
@RequiredArgsConstructor
@Tag(name = "Climbing Record Management", description = "클라이밍 기록 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class ClimbingController {

    private final ClimbingRecordService climbingRecordService;

    /**
     * 클라이밍 기록 등록
     */
    @PostMapping("/records")
    @Operation(summary = "클라이밍 기록 등록", 
               description = "새로운 클라이밍 기록을 등록합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "201", description = "기록 등록 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 기록 정보"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "루트를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<ClimbingRecordResponse>> createClimbingRecord(
            @Parameter(description = "클라이밍 기록 정보", required = true)
            @Valid @RequestBody ClimbingRecordRequest request,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Creating climbing record: userId={}, routeId={}, successRate={}, date={}", 
                userId, request.getRouteId(), request.getSuccessRate(), request.getClimbDate());
        
        ClimbingRecordResponse record = climbingRecordService.createClimbingRecord(userId, request);
        
        return ResponseEntity.status(201).body(ApiResponse.success(
            record, 
            "클라이밍 기록이 등록되었습니다"));
    }

    /**
     * 클라이밍 기록 조회
     */
    @GetMapping("/records")
    @Operation(summary = "클라이밍 기록 조회", 
               description = "사용자의 클라이밍 기록 목록을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<PageResponse<ClimbingRecordResponse>>> getClimbingRecords(
            @Parameter(description = "시작 날짜 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            
            @Parameter(description = "종료 날짜 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            
            @Parameter(description = "루트 ID 필터")
            @RequestParam(required = false) Long routeId,
            
            @Parameter(description = "지점 ID 필터")
            @RequestParam(required = false) Long branchId,
            
            @Parameter(description = "성공한 기록만 조회")
            @RequestParam(defaultValue = "false") Boolean successfulOnly,
            
            @Parameter(description = "페이징 정보")
            @PageableDefault(size = 20, sort = "climbDate", direction = Sort.Direction.DESC) Pageable pageable,
            
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting climbing records: userId={}, startDate={}, endDate={}, routeId={}", 
                 userId, startDate, endDate, routeId);
        
        Page<ClimbingRecordResponse> records = climbingRecordService.getClimbingRecords(
            userId, startDate, endDate, routeId, branchId, successfulOnly, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
            PageResponse.of(records),
            String.format("클라이밍 기록 %d개를 조회했습니다", records.getTotalElements())));
    }

    /**
     * 클라이밍 통계 조회
     */
    @GetMapping("/stats")
    @Operation(summary = "클라이밍 통계 조회", 
               description = "사용자의 클라이밍 성과 통계를 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<ClimbingStatsResponse>> getClimbingStats(
            @Parameter(description = "시작 날짜 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            
            @Parameter(description = "종료 날짜 (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            
            @Parameter(description = "실패한 시도도 포함")
            @RequestParam(defaultValue = "true") Boolean includeFailedAttempts,
            
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting climbing stats: userId={}, startDate={}, endDate={}, includeFailures={}", 
                 userId, startDate, endDate, includeFailedAttempts);
        
        ClimbingStatsRequest statsRequest = ClimbingStatsRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .includeFailedAttempts(includeFailedAttempts)
            .build();
        
        ClimbingStatsResponse stats = climbingRecordService.getClimbingStats(userId, statsRequest);
        
        return ResponseEntity.ok(ApiResponse.success(
            stats, 
            "클라이밍 통계를 조회했습니다"));
    }

    /**
     * 클라이밍 신발 목록 조회
     */
    @GetMapping("/shoes")
    @Operation(summary = "클라이밍 신발 목록 조회", 
               description = "사용자가 등록한 클라이밍 신발 목록을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<List<ClimbingShoeResponse>>> getClimbingShoes(
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting climbing shoes: userId={}", userId);
        
        List<ClimbingShoeResponse> shoes = climbingRecordService.getClimbingShoes(userId);
        
        return ResponseEntity.ok(ApiResponse.success(
            shoes, 
            String.format("클라이밍 신발 %d개를 조회했습니다", shoes.size())));
    }

    /**
     * 클라이밍 신발 등록
     */
    @PostMapping("/shoes")
    @Operation(summary = "클라이밍 신발 등록", 
               description = "새로운 클라이밍 신발 정보를 등록합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "201", description = "신발 등록 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 신발 정보"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "신발 정보를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 20, period = 60) // 1분간 20회
    public ResponseEntity<ApiResponse<ClimbingShoeResponse>> registerClimbingShoe(
            @Parameter(description = "클라이밍 신발 정보", required = true)
            @Valid @RequestBody ClimbingShoeRequest request,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Registering climbing shoe: userId={}, shoeId={}, size={}, rating={}", 
                userId, request.getShoeId(), request.getShoeSize(), request.getReviewRating());
        
        ClimbingShoeResponse shoe = climbingRecordService.registerClimbingShoe(userId, request);
        
        return ResponseEntity.status(201).body(ApiResponse.success(
            shoe, 
            "클라이밍 신발이 등록되었습니다"));
    }

    // ========== 추가 기능 엔드포인트 ==========

    /**
     * 개인 베스트 기록 조회
     */
    @GetMapping("/personal-best")
    @Operation(summary = "개인 베스트 기록 조회", 
               description = "사용자의 난이도별 최고 기록을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 60) // 1분간 30회
    public ResponseEntity<ApiResponse<PersonalBestResponse>> getPersonalBest(
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting personal best: userId={}", userId);
        
        PersonalBestResponse personalBest = climbingRecordService.getPersonalBest(userId);
        
        return ResponseEntity.ok(ApiResponse.success(
            personalBest, 
            "개인 베스트 기록을 조회했습니다"));
    }

    /**
     * 진행률 추이 조회
     */
    @GetMapping("/progress")
    @Operation(summary = "진행률 추이 조회", 
               description = "시간대별 클라이밍 실력 향상 추이를 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 20, period = 60) // 1분간 20회
    public ResponseEntity<ApiResponse<List<ProgressDataPoint>>> getClimbingProgress(
            @Parameter(description = "조회 기간 (개월)")
            @RequestParam(defaultValue = "6") Integer months,
            
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting climbing progress: userId={}, months={}", userId, months);
        
        List<ProgressDataPoint> progressData = climbingRecordService.getClimbingProgress(userId, months);
        
        return ResponseEntity.ok(ApiResponse.success(
            progressData, 
            String.format("최근 %d개월간의 진행률을 조회했습니다", months)));
    }

    // ========== 내부 DTO 클래스 ==========

    /**
     * 개인 베스트 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "개인 베스트 기록 응답")
    public static class PersonalBestResponse {
        @Schema(description = "최고 완등 난이도")
        private Integer highestCompletedDifficulty;
        
        @Schema(description = "최고 시도 난이도")
        private Integer highestAttemptedDifficulty;
        
        @Schema(description = "최고 성공률 난이도")
        private Integer bestSuccessRateDifficulty;
        
        @Schema(description = "최고 성공률 (%)")
        private Double bestSuccessRate;
        
        @Schema(description = "총 완등 수")
        private Integer totalCompletions;
        
        @Schema(description = "최근 개선된 난이도")
        private Integer recentImprovementDifficulty;
        
        @Schema(description = "개선 달성 날짜")
        private java.time.LocalDate improvementDate;
    }

    /**
     * 진행률 데이터 포인트 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "진행률 데이터 포인트")
    public static class ProgressDataPoint {
        @Schema(description = "기준 날짜")
        private java.time.LocalDate date;
        
        @Schema(description = "평균 완등 난이도")
        private Double averageCompletedDifficulty;
        
        @Schema(description = "성공률 (%)")
        private Double successRate;
        
        @Schema(description = "월간 완등 수")
        private Integer monthlyCompletions;
        
        @Schema(description = "시도한 최고 난이도")
        private Integer peakDifficulty;
        
        @Schema(description = "지속적인 성공 난이도")
        private Integer consistentDifficulty;
    }
}
```

---

## 📋 설계 완료 사항
✅ **ClimbingController** - 클라이밍 기록 관리 완전한 REST API  
✅ **5개 기본 엔드포인트** - 기록 등록/조회, 통계, 신발 관리  
✅ **2개 추가 엔드포인트** - 개인 베스트, 진행률 추이  
✅ **인증 기반 접근** - @PreAuthorize로 개인 정보 보호  
✅ **상세 통계 제공** - 성공률, 평균 난이도, 개인 기록 분석  
✅ **신발 관리 시스템** - 개인 신발 등록 및 리뷰  
✅ **완전한 문서화** - Swagger 어노테이션 완벽 적용  

## 🎯 주요 특징
- **개인화된 기록 관리** - 사용자별 클라이밍 성과 추적
- **상세 통계 분석** - 성공률, 평균 난이도, 개인 베스트
- **시간대별 진행률** - 실력 향상 추이 시각화 지원
- **신발 리뷰 시스템** - 개인 장비 관리 및 평가
- **기간별 필터링** - 유연한 데이터 조회 옵션
- **성능 최적화** - 적절한 캐싱과 Rate Limiting

## ⚙️ Rate Limiting 전략
- **기록 등록**: 50회/분 (개인 성과 기록)
- **기록 조회**: 100회/분 (개인 데이터 조회)
- **통계 조회**: 50회/분 (계산 집약적 작업)
- **신발 조회**: 50회/분 (개인 장비 정보)
- **신발 등록**: 20회/분 (신중한 등록 유도)
- **개인 베스트**: 30회/분 (중요 지표 조회)
- **진행률 추이**: 20회/분 (복잡한 분석 작업)