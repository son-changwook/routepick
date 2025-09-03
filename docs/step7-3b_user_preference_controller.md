# Step 7-3b: UserPreferenceController 구현

## 📋 구현 목표
사용자 선호도 관리를 위한 RESTful API Controller 구현:
1. **선호 태그 관리** - 조회, 설정, 삭제 API
2. **실력 레벨 관리** - 레벨 설정 및 업데이트
3. **3단계 선호도** - LOW(30%), MEDIUM(70%), HIGH(100%)
4. **4단계 실력 레벨** - BEGINNER → INTERMEDIATE → ADVANCED → EXPERT  
5. **배치 처리 지원** - 다중 선호 태그 한번에 설정

---

## 🎯 UserPreferenceController 구현

### 📁 파일 위치
```
src/main/java/com/routepick/controller/user/UserPreferenceController.java
```

### 📝 구현 코드
```java
package com.routepick.controller.user;

import com.routepick.common.ApiResponse;
import com.routepick.common.enums.PreferenceLevel;
import com.routepick.common.enums.SkillLevel;
import com.routepick.dto.user.preference.request.PreferredTagBatchRequest;
import com.routepick.dto.user.preference.request.SkillLevelUpdateRequest;
import com.routepick.dto.user.preference.request.UserPreferredTagRequest;
import com.routepick.dto.user.preference.response.UserPreferredTagResponse;
import com.routepick.dto.user.preference.response.UserSkillLevelResponse;
import com.routepick.service.user.UserPreferenceService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자 선호도 관리 Controller
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Preference Management", description = "사용자 선호도 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    /**
     * 사용자 선호 태그 조회
     */
    @GetMapping("/preferred-tags")
    @Operation(summary = "사용자 선호 태그 조회", 
               description = "현재 사용자가 설정한 선호 태그 목록을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 60) // 1분간 100회
    public ResponseEntity<ApiResponse<List<UserPreferredTagResponse>>> getUserPreferredTags(
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting preferred tags for user: {}", userId);
        
        List<UserPreferredTagResponse> preferredTags = userPreferenceService.getUserPreferredTags(userId);
        
        return ResponseEntity.ok(ApiResponse.success(
            preferredTags, 
            String.format("선호 태그 %d개를 조회했습니다", preferredTags.size())));
    }

    /**
     * 사용자 선호 태그 설정 (단일)
     */
    @PutMapping("/preferred-tags")
    @Operation(summary = "사용자 선호 태그 설정", 
               description = "사용자의 선호 태그를 설정합니다 (단일)")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "설정 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "태그를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<UserPreferredTagResponse>> setUserPreferredTag(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "선호 태그 설정 정보", required = true)
            @Valid @RequestBody UserPreferredTagRequest request) {
        
        log.info("Setting preferred tag for user: userId={}, tagId={}, level={}", 
                userId, request.getTagId(), request.getPreferenceLevel());
        
        UserPreferredTagResponse preferredTag = userPreferenceService.setUserPreferredTag(userId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            preferredTag, 
            "선호 태그가 설정되었습니다"));
    }

    /**
     * 사용자 선호 태그 배치 설정
     */
    @PutMapping("/preferred-tags/batch")
    @Operation(summary = "사용자 선호 태그 배치 설정", 
               description = "여러 선호 태그를 한번에 설정합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "설정 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "태그를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 10, period = 60) // 1분간 10회 (배치는 제한적)
    public ResponseEntity<ApiResponse<List<UserPreferredTagResponse>>> setUserPreferredTagsBatch(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "선호 태그 배치 설정 정보", required = true)
            @Valid @RequestBody PreferredTagBatchRequest request) {
        
        log.info("Setting preferred tags batch for user: userId={}, count={}", 
                userId, request.getPreferredTags().size());
        
        List<UserPreferredTagResponse> preferredTags = userPreferenceService
            .setUserPreferredTagsBatch(userId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            preferredTags, 
            String.format("선호 태그 %d개가 설정되었습니다", preferredTags.size())));
    }

    /**
     * 사용자 선호 태그 삭제
     */
    @DeleteMapping("/preferred-tags/{tagId}")
    @Operation(summary = "사용자 선호 태그 삭제", 
               description = "특정 선호 태그를 삭제합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "삭제 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "선호 태그를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 60) // 1분간 30회
    public ResponseEntity<ApiResponse<Void>> removeUserPreferredTag(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "삭제할 태그 ID", required = true)
            @PathVariable Long tagId) {
        
        log.info("Removing preferred tag for user: userId={}, tagId={}", userId, tagId);
        
        userPreferenceService.removeUserPreferredTag(userId, tagId);
        
        return ResponseEntity.ok(ApiResponse.success(
            null, 
            "선호 태그가 삭제되었습니다"));
    }

    /**
     * 사용자 실력 레벨 조회
     */
    @GetMapping("/skill-level")
    @Operation(summary = "사용자 실력 레벨 조회", 
               description = "현재 사용자의 클라이밍 실력 레벨 정보를 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<UserSkillLevelResponse>> getUserSkillLevel(
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting skill level for user: {}", userId);
        
        UserSkillLevelResponse skillLevel = userPreferenceService.getUserSkillLevel(userId);
        
        return ResponseEntity.ok(ApiResponse.success(
            skillLevel, 
            "사용자 실력 레벨을 조회했습니다"));
    }

    /**
     * 사용자 실력 레벨 설정
     */
    @PutMapping("/skill-level")
    @Operation(summary = "사용자 실력 레벨 설정", 
               description = "사용자의 클라이밍 실력 레벨을 설정합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "설정 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 20, period = 60) // 1분간 20회 (자주 변경되지 않음)
    public ResponseEntity<ApiResponse<UserSkillLevelResponse>> updateUserSkillLevel(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "실력 레벨 설정 정보", required = true)
            @Valid @RequestBody SkillLevelUpdateRequest request) {
        
        log.info("Updating skill level for user: userId={}, newLevel={}", userId, request.getSkillLevel());
        
        UserSkillLevelResponse updatedSkillLevel = userPreferenceService.updateUserSkillLevel(userId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            updatedSkillLevel, 
            String.format("실력 레벨이 %s로 설정되었습니다", request.getSkillLevel().getDisplayName())));
    }

    /**
     * 선호도 프로필 조회 (종합)
     */
    @GetMapping("/preference-profile")
    @Operation(summary = "선호도 프로필 조회", 
               description = "사용자의 선호 태그와 실력 레벨을 함께 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 60) // 1분간 50회
    public ResponseEntity<ApiResponse<UserPreferenceProfileResponse>> getUserPreferenceProfile(
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting preference profile for user: {}", userId);
        
        UserPreferenceProfileResponse preferenceProfile = userPreferenceService.getUserPreferenceProfile(userId);
        
        return ResponseEntity.ok(ApiResponse.success(
            preferenceProfile, 
            "선호도 프로필을 조회했습니다"));
    }

    /**
     * 추천 태그 조회
     */
    @GetMapping("/recommended-tags")
    @Operation(summary = "추천 태그 조회", 
               description = "사용자의 현재 선호도를 기반으로 새로운 태그를 추천합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 60) // 1분간 30회
    public ResponseEntity<ApiResponse<List<TagRecommendationResponse>>> getRecommendedTags(
            @AuthenticationPrincipal Long userId,
            
            @Parameter(description = "추천 태그 수")
            @RequestParam(defaultValue = "10") Integer limit) {
        
        log.debug("Getting recommended tags for user: {}, limit: {}", userId, limit);
        
        List<TagRecommendationResponse> recommendedTags = userPreferenceService
            .getRecommendedTags(userId, limit);
        
        return ResponseEntity.ok(ApiResponse.success(
            recommendedTags, 
            String.format("추천 태그 %d개를 조회했습니다", recommendedTags.size())));
    }

    /**
     * 선호도 분석 조회
     */
    @GetMapping("/preference-analysis")
    @Operation(summary = "선호도 분석 조회", 
               description = "사용자의 선호도 패턴을 분석한 결과를 제공합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "분석 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "분석 데이터 부족"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 20, period = 60) // 1분간 20회
    public ResponseEntity<ApiResponse<UserPreferenceAnalysisResponse>> getUserPreferenceAnalysis(
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting preference analysis for user: {}", userId);
        
        UserPreferenceAnalysisResponse analysis = userPreferenceService.getUserPreferenceAnalysis(userId);
        
        return ResponseEntity.ok(ApiResponse.success(
            analysis, 
            "선호도 분석 결과를 조회했습니다"));
    }

    // ========== Response DTO Classes ==========

    /**
     * 사용자 선호도 프로필 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "사용자 선호도 프로필 응답")
    public static class UserPreferenceProfileResponse {
        @Schema(description = "사용자 ID")
        private Long userId;
        
        @Schema(description = "현재 실력 레벨")
        private UserSkillLevelResponse skillLevel;
        
        @Schema(description = "선호 태그 목록")
        private List<UserPreferredTagResponse> preferredTags;
        
        @Schema(description = "선호도 완성률 (%)")
        private Double completionRate;
        
        @Schema(description = "추천 정확도 점수")
        private Double recommendationAccuracy;
        
        @Schema(description = "마지막 업데이트 시간")
        private java.time.LocalDateTime lastUpdatedAt;
    }

    /**
     * 태그 추천 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "태그 추천 응답")
    public static class TagRecommendationResponse {
        @Schema(description = "추천 태그 정보")
        private com.routepick.dto.tag.response.TagResponse tag;
        
        @Schema(description = "추천 점수 (0.0 ~ 1.0)")
        private Double recommendationScore;
        
        @Schema(description = "추천 이유")
        private String recommendationReason;
        
        @Schema(description = "유사 사용자들의 선택률 (%)")
        private Double popularityRate;
        
        @Schema(description = "예상 선호도")
        private PreferenceLevel expectedPreferenceLevel;
    }

    /**
     * 선호도 분석 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "사용자 선호도 분석 응답")
    public static class UserPreferenceAnalysisResponse {
        @Schema(description = "사용자 ID")
        private Long userId;
        
        @Schema(description = "주요 선호 태그 타입 분포")
        private java.util.Map<com.routepick.common.enums.TagType, Integer> tagTypeDistribution;
        
        @Schema(description = "선호도 레벨 분포")
        private java.util.Map<PreferenceLevel, Integer> preferenceLevelDistribution;
        
        @Schema(description = "클라이밍 스타일 점수")
        private ClimbingStyleScores climbingStyleScores;
        
        @Schema(description = "개선 제안")
        private List<String> suggestions;
        
        @Schema(description = "유사한 선호도를 가진 사용자 수")
        private Integer similarUsersCount;
        
        @Schema(description = "분석 생성 시간")
        private java.time.LocalDateTime analyzedAt;
    }

    /**
     * 클라이밍 스타일 점수 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "클라이밍 스타일 점수")
    public static class ClimbingStyleScores {
        @Schema(description = "기술적 클라이밍 선호도 (0-100)")
        private Double technicalScore;
        
        @Schema(description = "파워 클라이밍 선호도 (0-100)")
        private Double powerScore;
        
        @Schema(description = "지구력 클라이밍 선호도 (0-100)")
        private Double enduranceScore;
        
        @Schema(description = "정적 클라이밍 선호도 (0-100)")
        private Double staticScore;
        
        @Schema(description = "동적 클라이밍 선호도 (0-100)")
        private Double dynamicScore;
    }
}
```

---

## 📋 설계 완료 사항
✅ **UserPreferenceController** - 사용자 선호도 완전한 REST API  
✅ **8개 엔드포인트** - 선호 태그 CRUD, 실력 레벨 관리, 분석  
✅ **배치 처리 지원** - 다중 선호 태그 한번에 설정  
✅ **선호도 분석** - 사용자 패턴 분석 및 개선 제안  
✅ **보안 강화** - @PreAuthorize, @RateLimited 적용  
✅ **완전한 문서화** - Swagger 어노테이션 완벽 적용  

## 🎯 주요 특징
- **3단계 선호도 시스템** - LOW(30%), MEDIUM(70%), HIGH(100%)
- **4단계 실력 레벨** - BEGINNER → INTERMEDIATE → ADVANCED → EXPERT
- **개인화된 추천** - 현재 선호도 기반 새로운 태그 추천
- **종합 분석 제공** - 클라이밍 스타일 점수 및 개선 제안
- **배치 처리 최적화** - 다중 설정 시 트랜잭션 처리
- **실시간 업데이트** - 선호도 변경 시 추천 시스템 연동

## ⚙️ Rate Limiting 전략
- **일반 조회**: 50-100회/분 (프로필, 선호 태그)
- **설정 변경**: 20-50회/분 (개별 태그, 실력 레벨)
- **배치 처리**: 10회/분 (다중 태그 설정)
- **분석 요청**: 20-30회/분 (분석은 리소스 집약적)