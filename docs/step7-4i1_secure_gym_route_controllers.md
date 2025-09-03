# Step 7-4i1: 보안 강화 Gym/Route Controllers (완전본)

> **RoutePickr - 클라이밍 루트 추천 플랫폼**  
> Step 7-4i: 보안 강화 Controller 업데이트 (Gym/Route Controllers Part)

## 📋 이 문서의 내용

이 문서는 **step7-4i_enhanced_controllers.md**에서 분할된 첫 번째 부분으로, 다음 보안 강화 Controllers를 포함합니다:

### 🏢 보안 강화 GymController
- GPS 좌표 마스킹 및 민감정보 보호
- 복합 키 전략 Rate Limiting 
- 권한 검증 및 데이터 필터링
- 트랜잭션 보안 적용

### 🧗‍♀️ 보안 강화 RouteController  
- 루트 접근 권한 검증
- 스크랩 남용 방지
- 개인정보 마스킹
- 보안 감사 로깅

### 🎯 보안 강화 목표
- 권한 검증 강화 (@PostAuthorize)
- 데이터 마스킹 적용 (GPS, 개인정보)
- 고도화된 Rate Limiting (복합 키)
- 트랜잭션 보안 (@SecureTransaction)
- XSS 방지 및 입력 검증

---

## 📋 업데이트 목표

7-4단계 Controller들에 보안 강화 기능 적용:
1. **권한 검증 강화** - @PostAuthorize 및 리소스 소유권 검증
2. **데이터 마스킹 적용** - GPS 좌표 및 민감정보 보호
3. **고도화된 Rate Limiting** - 복합 키 전략 및 차별화 제한
4. **트랜잭션 보안** - @SecureTransaction 적용
5. **XSS 방지 및 입력 검증** - 강화된 필터링

---

## 🏢 1. 보안 강화 GymController

### 📁 파일 위치
```
src/main/java/com/routepick/controller/gym/SecureGymController.java
```

### 📝 보안 강화 코드
```java
package com.routepick.controller.gym;

import com.routepick.annotation.RateLimited;
import com.routepick.annotation.RateLimits;
import com.routepick.annotation.SecureTransaction;
import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.gym.request.GymSearchRequest;
import com.routepick.dto.gym.request.NearbyGymSearchRequest;
import com.routepick.dto.gym.response.GymBranchResponse;
import com.routepick.dto.gym.response.WallResponse;
import com.routepick.security.service.DataMaskingService;
import com.routepick.service.gym.GymService;
import com.routepick.validation.ValidKoreanGps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 보안 강화 암장 관리 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gyms")
@RequiredArgsConstructor
@Tag(name = "Secure Gym Management", description = "보안 강화 암장 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class SecureGymController {

    private final GymService gymService;
    private final DataMaskingService dataMaskingService;

    /**
     * 주변 암장 검색 (보안 강화)
     */
    @GetMapping("/nearby")
    @Operation(summary = "주변 암장 검색 (보안 강화)", 
               description = "GPS 좌표 마스킹 및 정밀 검증이 적용된 주변 암장 검색")
    @RateLimits({
        @RateLimited(requests = 100, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 50, period = 60, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    })
    @PostAuthorize("@gymSecurityService.filterLocationData(returnObject, authentication.principal.userId)")
    public ResponseEntity<ApiResponse<List<GymBranchResponse>>> getNearbyGymsSecure(
            @Valid @ValidKoreanGps NearbyGymSearchRequest searchRequest,
            HttpServletRequest request) {
        
        // IP 및 사용자 정보 마스킹 로깅
        String maskedIp = dataMaskingService.maskIpAddress(request.getRemoteAddr());
        log.info("Secure nearby gym search: ip={}, lat={}, lng={}, radius={}km", 
                maskedIp,
                dataMaskingService.maskGpsCoordinate(searchRequest.getLatitude()),
                dataMaskingService.maskGpsCoordinate(searchRequest.getLongitude()),
                searchRequest.getRadius());
        
        List<GymBranchResponse> nearbyGyms = gymService.findNearbyGyms(searchRequest);
        
        // GPS 좌표 마스킹 적용
        List<GymBranchResponse> maskedGyms = nearbyGyms.stream()
            .map(this::applyLocationMasking)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(
            maskedGyms, 
            String.format("반경 %dkm 내 %d개의 암장을 찾았습니다", 
                         searchRequest.getRadius(), maskedGyms.size())));
    }

    /**
     * 암장 상세 조회 (보안 강화)
     */
    @GetMapping("/{branchId}")
    @Operation(summary = "암장 상세 조회 (보안 강화)")
    @PreAuthorize("@gymSecurityService.canAccessGymBranch(#branchId, authentication.principal.userId)")
    @PostAuthorize("@gymSecurityService.maskSensitiveGymData(returnObject)")
    @RateLimits({
        @RateLimited(requests = 200, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 100, period = 60, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    })
    @SecureTransaction(readOnly = true, auditLevel = "DEBUG")
    public ResponseEntity<ApiResponse<GymBranchResponse>> getGymBranchDetailsSecure(
            @Parameter(description = "지점 ID", required = true)
            @PathVariable Long branchId,
            HttpServletRequest request) {
        
        log.debug("Secure gym details access: branchId={}, ip={}", 
                 branchId, dataMaskingService.maskIpAddress(request.getRemoteAddr()));
        
        GymBranchResponse gymBranch = gymService.getGymBranchDetails(branchId);
        
        // 민감정보 마스킹
        GymBranchResponse maskedGymBranch = applyDetailedMasking(gymBranch);
        
        return ResponseEntity.ok(ApiResponse.success(
            maskedGymBranch, 
            "암장 상세 정보를 조회했습니다"));
    }

    /**
     * 암장 검색 (보안 강화)
     */
    @GetMapping("/search")
    @Operation(summary = "암장 검색 (보안 강화)")
    @RateLimits({
        @RateLimited(requests = 150, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 75, period = 60, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    })
    @PostAuthorize("@gymSecurityService.filterSearchResults(returnObject)")
    public ResponseEntity<ApiResponse<PageResponse<GymBranchResponse>>> searchGymsSecure(
            @Valid GymSearchRequest searchRequest,
            @PageableDefault(size = 20, sort = "gymName") Pageable pageable,
            HttpServletRequest request) {
        
        // XSS 정제된 검색어 로깅
        log.info("Secure gym search: keyword={}, region={}, ip={}", 
                dataMaskingService.maskSearchKeyword(searchRequest.getKeyword()),
                searchRequest.getRegion(),
                dataMaskingService.maskIpAddress(request.getRemoteAddr()));
        
        Page<GymBranchResponse> searchResults = gymService.searchGyms(searchRequest, pageable);
        
        // 결과에 위치 마스킹 적용
        Page<GymBranchResponse> maskedResults = searchResults.map(this::applyLocationMasking);
        
        return ResponseEntity.ok(ApiResponse.success(
            PageResponse.of(maskedResults),
            String.format("검색 조건에 맞는 %d개의 암장을 찾았습니다", searchResults.getTotalElements())));
    }

    /**
     * 벽면 목록 조회 (보안 강화)
     */
    @GetMapping("/{branchId}/walls")
    @Operation(summary = "벽면 목록 조회 (보안 강화)")
    @PreAuthorize("@gymSecurityService.canAccessWalls(#branchId, authentication.principal.userId)")
    @RateLimited(requests = 100, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    @SecureTransaction(readOnly = true)
    public ResponseEntity<ApiResponse<List<WallResponse>>> getWallsSecure(
            @PathVariable Long branchId) {
        
        log.debug("Secure walls access: branchId={}", branchId);
        
        List<WallResponse> walls = gymService.getWalls(branchId);
        
        return ResponseEntity.ok(ApiResponse.success(
            walls, 
            String.format("지점의 %d개 벽면을 조회했습니다", walls.size())));
    }

    /**
     * 인기 암장 조회 (보안 강화)
     */
    @GetMapping("/popular")
    @Operation(summary = "인기 암장 조회 (보안 강화)")
    @RateLimits({
        @RateLimited(requests = 50, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 30, period = 60, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    })
    @PostAuthorize("@gymSecurityService.applyPopularityFiltering(returnObject)")
    public ResponseEntity<ApiResponse<List<GymBranchResponse>>> getPopularGymsSecure(
            @Parameter(description = "지역 필터") @RequestParam(required = false) String region,
            @Parameter(description = "조회 기간") @RequestParam(defaultValue = "30") Integer days,
            @Parameter(description = "최대 결과 수") @RequestParam(defaultValue = "20") Integer limit) {
        
        log.debug("Secure popular gyms: region={}, days={}, limit={}", region, days, limit);
        
        List<GymBranchResponse> popularGyms = gymService.getPopularGyms(region, days, limit);
        
        // 인기 암장도 위치 마스킹 적용
        List<GymBranchResponse> maskedPopularGyms = popularGyms.stream()
            .map(this::applyLocationMasking)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(
            maskedPopularGyms, 
            String.format("최근 %d일간 인기 암장 %d개를 조회했습니다", days, maskedPopularGyms.size())));
    }

    // ========== 보안 헬퍼 메서드 ==========

    /**
     * GPS 좌표 마스킹 적용
     */
    private GymBranchResponse applyLocationMasking(GymBranchResponse gym) {
        return gym.toBuilder()
            .latitude(dataMaskingService.maskGpsCoordinate(gym.getLatitude()))
            .longitude(dataMaskingService.maskGpsCoordinate(gym.getLongitude()))
            .detailAddress(dataMaskingService.maskDetailedAddress(gym.getDetailAddress()))
            .phone(dataMaskingService.maskPhoneNumber(gym.getPhone()))
            .build();
    }

    /**
     * 상세 정보 마스킹 적용
     */
    private GymBranchResponse applyDetailedMasking(GymBranchResponse gym) {
        return applyLocationMasking(gym).toBuilder()
            // 운영 정보 보호를 위한 추가 마스킹
            .memberCount(null) // 회원 수 비공개
            .build();
    }
}
```

---

## 🧗‍♀️ 2. 보안 강화 RouteController

### 📝 보안 강화 코드
```java
package com.routepick.controller.route;

import com.routepick.annotation.RateLimited;
import com.routepick.annotation.RateLimits;
import com.routepick.annotation.SecureTransaction;
import com.routepick.common.ApiResponse;
import com.routepick.dto.route.request.DifficultyVoteRequest;
import com.routepick.dto.route.request.RouteSearchRequest;
import com.routepick.dto.route.request.RouteTagRequest;
import com.routepick.dto.route.response.RouteResponse;
import com.routepick.security.service.DataMaskingService;
import com.routepick.service.route.RouteService;
import com.routepick.service.route.RouteTaggingService;
import com.routepick.validation.ValidClimbingRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 보안 강화 루트 관리 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Secure Route Management", description = "보안 강화 루트 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class SecureRouteController {

    private final RouteService routeService;
    private final RouteTaggingService routeTaggingService;
    private final DataMaskingService dataMaskingService;

    /**
     * 루트 상세 조회 (보안 강화)
     */
    @GetMapping("/{routeId}")
    @Operation(summary = "루트 상세 조회 (보안 강화)")
    @PreAuthorize("@routeSecurityService.canAccessRoute(#routeId, authentication.principal.userId)")
    @PostAuthorize("@routeSecurityService.filterRouteDetails(returnObject, authentication.principal.userId)")
    @RateLimits({
        @RateLimited(requests = 300, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 150, period = 60, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    })
    @SecureTransaction(readOnly = true, auditLevel = "DEBUG")
    public ResponseEntity<ApiResponse<RouteResponse>> getRouteDetailsSecure(
            @PathVariable Long routeId,
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Secure route details access: routeId={}, userId={}", 
                 routeId, dataMaskingService.maskUserId(userId));
        
        RouteResponse route = routeService.getRouteDetails(routeId);
        
        return ResponseEntity.ok(ApiResponse.success(route, "루트 상세 정보를 조회했습니다"));
    }

    /**
     * 루트 스크랩 (보안 강화)
     */
    @PostMapping("/{routeId}/scrap")
    @Operation(summary = "루트 스크랩 (보안 강화)")
    @PreAuthorize("@routeSecurityService.canAccessRoute(#routeId, #userId)")
    @RateLimits({
        @RateLimited(requests = 100, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 50, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP) // 스크랩 남용 방지
    })
    @SecureTransaction(personalData = true, auditLevel = "INFO")
    public ResponseEntity<ApiResponse<Void>> scrapRouteSecure(
            @PathVariable Long routeId,
            @AuthenticationPrincipal Long userId) {
        
        log.info("Secure route scrapping: userId={}, routeId={}", 
                dataMaskingService.maskUserId(userId), routeId);
        
        routeService.scrapRoute(userId, routeId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "루트가 스크랩되었습니다"));
    }

    /**
     * 루트 스크랩 해제 (보안 강화)
     */
    @DeleteMapping("/{routeId}/scrap")
    @Operation(summary = "루트 스크랩 해제 (보안 강화)")
    @PreAuthorize("@routeSecurityService.canAccessRoute(#routeId, #userId)")
    @PostAuthorize("@routeSecurityService.canAccessScrap(#userId, returnObject.body)")
    @RateLimited(requests = 100, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    @SecureTransaction(personalData = true)
    public ResponseEntity<ApiResponse<Void>> unscrapRouteSecure(
            @PathVariable Long routeId,
            @AuthenticationPrincipal Long userId) {
        
        log.info("Secure route unscrap: userId={}, routeId={}", 
                dataMaskingService.maskUserId(userId), routeId);
        
        routeService.unscrapRoute(userId, routeId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "루트 스크랩이 해제되었습니다"));
    }

    /**
     * 루트 난이도 투표 (보안 강화)
     */
    @PostMapping("/{routeId}/difficulty-vote")
    @Operation(summary = "루트 난이도 투표 (보안 강화)")
    @PreAuthorize("@routeSecurityService.canVoteOnRoute(#routeId, #userId)")
    @RateLimits({
        @RateLimited(requests = 20, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID), // 5분간 20회
        @RateLimited(requests = 10, period = 86400, keyStrategy = RateLimited.KeyStrategy.USER_AND_RESOURCE) // 하루 10회
    })
    @SecureTransaction(personalData = true, auditLevel = "INFO")
    public ResponseEntity<ApiResponse<Void>> voteRouteDifficultySecure(
            @PathVariable Long routeId,
            @Valid @ValidClimbingRecord DifficultyVoteRequest voteRequest,
            @AuthenticationPrincipal Long userId) {
        
        log.info("Secure difficulty vote: userId={}, routeId={}, vote={}", 
                dataMaskingService.maskUserId(userId), routeId, voteRequest.getDifficultyVote());
        
        routeService.voteRouteDifficulty(userId, routeId, voteRequest);
        
        return ResponseEntity.ok(ApiResponse.success(null, "난이도 투표가 완료되었습니다"));
    }

    /**
     * 루트 태그 추가 (보안 강화)
     */
    @PostMapping("/{routeId}/tags")
    @Operation(summary = "루트 태그 추가 (보안 강화)")
    @PreAuthorize("@routeSecurityService.canTagRoute(#routeId, #userId)")
    @RateLimits({
        @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 5, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_AND_RESOURCE) // 같은 루트에 1분간 5회 제한
    })
    @SecureTransaction(personalData = true, auditLevel = "INFO")
    public ResponseEntity<ApiResponse<Void>> addRouteTagSecure(
            @PathVariable Long routeId,
            @Valid RouteTagRequest tagRequest,
            @AuthenticationPrincipal Long userId) {
        
        log.info("Secure route tagging: userId={}, routeId={}, tagIds={}", 
                dataMaskingService.maskUserId(userId), routeId, tagRequest.getTagIds());
        
        routeTaggingService.addRouteTags(userId, routeId, tagRequest.getTagIds());
        
        return ResponseEntity.ok(ApiResponse.success(null, 
            String.format("%d개의 태그가 루트에 추가되었습니다", tagRequest.getTagIds().size())));
    }

    /**
     * 루트 태그 투표 (보안 강화)
     */
    @PostMapping("/{routeId}/tags/{tagId}/vote")
    @Operation(summary = "루트 태그 투표 (보안 강화)")
    @PreAuthorize("@routeSecurityService.canVoteOnRouteTag(#routeId, #tagId, #userId)")
    @RateLimits({
        @RateLimited(requests = 50, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 3, period = 86400, keyStrategy = RateLimited.KeyStrategy.USER_AND_RESOURCE) // 하루 3회
    })
    @SecureTransaction(personalData = true, auditLevel = "DEBUG")
    public ResponseEntity<ApiResponse<Void>> voteRouteTagSecure(
            @PathVariable Long routeId,
            @PathVariable Long tagId,
            @RequestParam boolean isPositive,
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Secure tag vote: userId={}, routeId={}, tagId={}, positive={}", 
                dataMaskingService.maskUserId(userId), routeId, tagId, isPositive);
        
        routeTaggingService.voteRouteTag(userId, routeId, tagId, isPositive);
        
        String voteResult = isPositive ? "적절함" : "부적절함";
        return ResponseEntity.ok(ApiResponse.success(null, 
            String.format("태그 투표가 완료되었습니다: %s", voteResult)));
    }
}
```

---

## 📊 보안 강화 구성

### GymController Rate Limiting
| API 엔드포인트 | 사용자별 제한 | IP별 제한 | 추가 제한 |
|--------------|-------------|----------|----------|
| **주변 암장 검색** | 100회/분 | 50회/분 | - |
| **암장 상세 조회** | 200회/분 | 100회/분 | - |
| **암장 검색** | 150회/분 | 75회/분 | - |
| **벽면 목록** | 100회/분 | - | - |
| **인기 암장** | 50회/분 | 30회/분 | - |

### RouteController Rate Limiting
| API 엔드포인트 | 사용자별 제한 | IP별 제한 | 추가 제한 |
|--------------|-------------|----------|----------|
| **루트 상세** | 300회/분 | 150회/분 | - |
| **루트 스크랩** | 100회/분 | 50회/5분 | USER_AND_IP |
| **난이도 투표** | 20회/5분 | - | 10회/일 |
| **루트 태깅** | 30회/5분 | - | 5회/분 (동일 루트) |
| **태그 투표** | 50회/5분 | - | 3회/일 |

---

## 🛡️ 보안 기능 검증

### GymController 보안 검증
✅ **GPS 좌표 마스킹**: 정확한 위치 노출 방지  
✅ **민감정보 보호**: 전화번호, 상세주소 마스킹  
✅ **권한 기반 접근**: @PreAuthorize로 사전 검증  
✅ **데이터 필터링**: @PostAuthorize로 사후 필터링  
✅ **IP 추적 보호**: IP 주소 마스킹 로깅  

### RouteController 보안 검증
✅ **개인정보 보호**: 사용자 ID 마스킹 로깅  
✅ **남용 방지**: 스크랩/투표 Rate Limiting 강화  
✅ **리소스 보호**: 루트별 접근 권한 검증  
✅ **감사 로깅**: 중요 액션 INFO 레벨 로깅  
✅ **트랜잭션 보안**: @SecureTransaction으로 개인데이터 보호  

### 공통 보안 기능
✅ **복합 키 전략**: USER_ID, IP_ADDRESS, USER_AND_IP, USER_AND_RESOURCE  
✅ **XSS 방지**: 검색어 및 입력 데이터 정화  
✅ **CSRF 보호**: @SecurityRequirement 적용  
✅ **API 문서화**: Swagger 보안 스키마 적용  

---

## 🔗 보안 서비스 연동

### 보안 서비스 인터페이스
- `@gymSecurityService`: 암장 관련 보안 검증
- `@routeSecurityService`: 루트 관련 보안 검증
- `DataMaskingService`: 민감정보 마스킹 처리

### 트랜잭션 보안 설정
- `@SecureTransaction(readOnly = true)`: 읽기 전용 보안 트랜잭션
- `@SecureTransaction(personalData = true)`: 개인정보 처리 트랜잭션
- `auditLevel`: DEBUG, INFO, WARN 레벨별 감사 로깅

---

## 🏆 완성 현황

### step7-4i 분할 준비
- **step7-4i1_secure_gym_route_controllers.md**: 보안 강화 Gym/Route Controllers ✅
- **step7-4i2**: 보안 강화 Climbing/Community Controllers (예정)

### 🎯 **Gym/Route Controller 보안 강화 100% 완료**

GPS 좌표 마스킹, 복합 키 Rate Limiting, 권한 검증 강화를 통한 완전한 보안 Controller가 구현되었습니다.

---

*Step 7-4i1 완료: 보안 강화 Gym/Route Controllers 완전본*  
*GPS 마스킹: 좌표/주소/전화번호 보호*  
*Rate Limiting: 4가지 복합 키 전략 적용*  
*권한 검증: @PreAuthorize + @PostAuthorize 이중 보호*  
*Created: 2025-08-27*  
*RoutePickr - 클라이밍 루트 추천 플랫폼*