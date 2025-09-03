# Step 7-2a: UserController 구현

> 사용자 및 프로필 관리 RESTful API Controller  
> 생성일: 2025-08-25  
> 기반: step6-1c_user_service.md, 프로필 조회/수정/검색

---

## 🎯 설계 원칙

- **RESTful API**: 표준 HTTP 메서드 사용
- **보안 강화**: 본인 확인, 프로필 공개 설정
- **입력 검증**: 한국 특화 검증 (닉네임, 휴대폰)
- **성능 최적화**: 캐싱, 페이징
- **표준 응답**: ApiResponse 통일 구조

---

## 👤 UserController 구현

### UserController.java
```java
package com.routepick.controller.user;

import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.user.request.*;
import com.routepick.dto.user.response.*;
import com.routepick.service.user.UserService;
import com.routepick.service.user.UserProfileService;
import com.routepick.service.storage.ImageStorageService;
import com.routepick.annotation.RateLimited;
import com.routepick.exception.user.UserNotFoundException;
import com.routepick.exception.user.ProfileAccessDeniedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 사용자 및 프로필 관리 Controller
 * - 프로필 조회/수정
 * - 프로필 이미지 업로드
 * - 사용자 검색
 * - 계정 비활성화
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "사용자 관리", description = "사용자 프로필 및 계정 관리 API")
public class UserController {
    
    private final UserService userService;
    private final UserProfileService userProfileService;
    private final ImageStorageService imageStorageService;
    
    // ===== 프로필 조회 =====
    
    /**
     * 내 프로필 조회
     * - 인증된 사용자 본인 프로필
     * - 모든 정보 포함
     */
    @GetMapping("/profile")
    @Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 프로필 정보 조회")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "프로필 조회 성공",
                content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "프로필 없음")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Long userId) {
        
        log.info("프로필 조회 요청: userId={}", userId);
        
        // 프로필 조회
        UserProfileResponse profile = userProfileService.getCompleteProfile(userId);
        
        log.info("프로필 조회 성공: userId={}", userId);
        
        return ResponseEntity.ok(ApiResponse.success(profile));
    }
    
    /**
     * 다른 사용자 프로필 조회
     * - 공개 프로필만 조회 가능
     * - 팔로우 관계에 따라 정보 제한
     */
    @GetMapping("/profile/{targetUserId}")
    @Operation(summary = "사용자 프로필 조회", description = "특정 사용자의 공개 프로필 조회")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "프로필 조회 성공"),
        @SwaggerApiResponse(responseCode = "403", description = "비공개 프로필"),
        @SwaggerApiResponse(responseCode = "404", description = "사용자 없음")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable Long targetUserId,
            @AuthenticationPrincipal Long userId) {
        
        log.info("사용자 프로필 조회: targetUserId={}, requesterId={}", targetUserId, userId);
        
        // 프로필 공개 설정 확인
        if (!userProfileService.isProfilePublic(targetUserId)) {
            // 팔로우 관계 확인
            if (userId == null || !userService.isFollowing(userId, targetUserId)) {
                throw new ProfileAccessDeniedException("비공개 프로필입니다. 팔로우 후 조회 가능합니다.");
            }
        }
        
        // 프로필 조회 (제한된 정보)
        UserProfileResponse profile = userProfileService.getPublicProfile(targetUserId, userId);
        
        return ResponseEntity.ok(ApiResponse.success(profile));
    }
    
    // ===== 프로필 수정 =====
    
    /**
     * 프로필 정보 수정
     * - 본인만 수정 가능
     * - 닉네임 중복 검사
     * - 한국 특화 검증
     */
    @PutMapping("/profile")
    @Operation(summary = "프로필 수정", description = "사용자 프로필 정보 수정")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "프로필 수정 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력값"),
        @SwaggerApiResponse(responseCode = "409", description = "닉네임 중복")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UserProfileUpdateRequest request,
            @AuthenticationPrincipal Long userId) {
        
        log.info("프로필 수정 요청: userId={}", userId);
        
        // 닉네임 변경 시 중복 검사
        if (request.getNickname() != null) {
            if (!userService.isNicknameAvailable(request.getNickname(), userId)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("NICKNAME_DUPLICATE", "이미 사용 중인 닉네임입니다."));
            }
        }
        
        // 프로필 업데이트
        UserProfileResponse updatedProfile = userProfileService.updateProfile(userId, request);
        
        log.info("프로필 수정 성공: userId={}", userId);
        
        return ResponseEntity.ok(ApiResponse.success(updatedProfile, "프로필이 수정되었습니다."));
    }
    
    // ===== 프로필 이미지 =====
    
    /**
     * 프로필 이미지 업로드
     * - 이미지 검증 (크기, 형식)
     * - S3 업로드
     * - 썸네일 생성
     */
    @PostMapping(value = "/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "프로필 이미지 업로드", description = "프로필 또는 배경 이미지 업로드")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "이미지 업로드 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 이미지 파일"),
        @SwaggerApiResponse(responseCode = "413", description = "파일 크기 초과")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 5, period = 300) // 5분간 5회 제한
    public ResponseEntity<ApiResponse<ProfileImageResponse>> uploadProfileImage(
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam(value = "imageType", defaultValue = "PROFILE") String imageType,
            @AuthenticationPrincipal Long userId) {
        
        log.info("프로필 이미지 업로드: userId={}, imageType={}, size={}", 
                userId, imageType, imageFile.getSize());
        
        // 파일 검증
        validateImageFile(imageFile);
        
        // 이미지 업로드 및 URL 생성
        String imageUrl = imageStorageService.uploadProfileImage(userId, imageFile, imageType);
        
        // 프로필 이미지 URL 업데이트
        if ("PROFILE".equals(imageType)) {
            userProfileService.updateProfileImage(userId, imageUrl);
        } else if ("BACKGROUND".equals(imageType)) {
            userProfileService.updateBackgroundImage(userId, imageUrl);
        }
        
        ProfileImageResponse response = ProfileImageResponse.builder()
            .imageUrl(imageUrl)
            .imageType(imageType)
            .uploadedAt(LocalDateTime.now())
            .build();
        
        log.info("프로필 이미지 업로드 성공: userId={}, url={}", userId, imageUrl);
        
        return ResponseEntity.ok(ApiResponse.success(response, "이미지가 업로드되었습니다."));
    }
    
    /**
     * 프로필 이미지 삭제
     */
    @DeleteMapping("/profile/image")
    @Operation(summary = "프로필 이미지 삭제", description = "프로필 이미지를 기본 이미지로 변경")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "이미지 삭제 성공")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteProfileImage(
            @RequestParam(value = "imageType", defaultValue = "PROFILE") String imageType,
            @AuthenticationPrincipal Long userId) {
        
        log.info("프로필 이미지 삭제: userId={}, imageType={}", userId, imageType);
        
        if ("PROFILE".equals(imageType)) {
            userProfileService.deleteProfileImage(userId);
        } else if ("BACKGROUND".equals(imageType)) {
            userProfileService.deleteBackgroundImage(userId);
        }
        
        return ResponseEntity.ok(ApiResponse.success(null, "이미지가 삭제되었습니다."));
    }
    
    // ===== 사용자 검색 =====
    
    /**
     * 사용자 검색
     * - 닉네임 기반 검색
     * - 페이징 지원
     * - 팔로우 관계 표시
     */
    @GetMapping("/search")
    @Operation(summary = "사용자 검색", description = "닉네임으로 사용자 검색")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "검색 성공")
    })
    public ResponseEntity<ApiResponse<UserSearchResponse>> searchUsers(
            @Valid UserSearchRequest request,
            @PageableDefault(size = 20, sort = "nickname", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal Long userId) {
        
        log.info("사용자 검색: keyword={}, page={}, size={}", 
                request.getKeyword(), pageable.getPageNumber(), pageable.getPageSize());
        
        // 사용자 검색
        Page<UserSummaryResponse> searchResults = userService.searchUsers(
            request.getKeyword(), 
            userId,
            pageable
        );
        
        UserSearchResponse response = UserSearchResponse.builder()
            .users(searchResults.getContent())
            .pagination(PageResponse.of(searchResults))
            .build();
        
        log.info("사용자 검색 완료: keyword={}, results={}", 
                request.getKeyword(), searchResults.getTotalElements());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    // ===== 계정 관리 =====
    
    /**
     * 계정 비활성화 (소프트 삭제)
     * - 비밀번호 재확인
     * - 탈퇴 사유 기록
     * - 30일 유예 기간
     */
    @PostMapping("/deactivate")
    @Operation(summary = "계정 비활성화", description = "회원 탈퇴 (30일 유예)")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "계정 비활성화 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "비밀번호 불일치")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 1, period = 300) // 5분간 1회 제한
    public ResponseEntity<ApiResponse<AccountDeactivateResponse>> deactivateAccount(
            @Valid @RequestBody AccountDeactivateRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {
        
        log.info("계정 비활성화 요청: userId={}, reason={}", userId, request.getReason());
        
        // 비밀번호 확인
        if (!userService.checkPassword(userId, request.getConfirmPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다."));
        }
        
        // IP 주소 기록
        String clientIp = extractClientIp(httpRequest);
        
        // 계정 비활성화 처리
        AccountDeactivateResponse response = userService.deactivateAccount(
            userId, 
            request.getReason(),
            clientIp
        );
        
        log.info("계정 비활성화 성공: userId={}, deactivatedAt={}", 
                userId, response.getDeactivatedAt());
        
        return ResponseEntity.ok(ApiResponse.success(response, 
            "계정이 비활성화되었습니다. 30일 이내에 로그인하시면 계정이 복구됩니다."));
    }
    
    /**
     * 계정 복구
     * - 30일 이내 재로그인 시 자동 복구
     */
    @PostMapping("/reactivate")
    @Operation(summary = "계정 복구", description = "비활성화된 계정 복구")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "계정 복구 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "복구 기간 만료")
    })
    public ResponseEntity<ApiResponse<UserResponse>> reactivateAccount(
            @RequestParam String email,
            @RequestParam String password) {
        
        log.info("계정 복구 요청: email={}", email);
        
        UserResponse user = userService.reactivateAccount(email, password);
        
        log.info("계정 복구 성공: userId={}", user.getId());
        
        return ResponseEntity.ok(ApiResponse.success(user, "계정이 복구되었습니다."));
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 이미지 파일 검증
     */
    private void validateImageFile(MultipartFile file) {
        // 파일 크기 검증 (최대 10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("파일 크기는 10MB를 초과할 수 없습니다.");
        }
        
        // 파일 형식 검증
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
        
        // 확장자 검증
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            if (!List.of("jpg", "jpeg", "png", "gif", "webp").contains(extension)) {
                throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
            }
        }
    }
    
    /**
     * 클라이언트 IP 추출
     */
    private String extractClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR",
            "X-Real-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
```

---

## 🔒 보안 및 권한 관리

### ProfileAccessService.java
```java
package com.routepick.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 프로필 접근 권한 관리 서비스
 */
@Service
@RequiredArgsConstructor
public class ProfileAccessService {
    
    private final UserService userService;
    
    /**
     * 프로필 조회 권한 확인
     */
    public boolean canViewProfile(Long viewerId, Long targetUserId) {
        // 본인은 항상 조회 가능
        if (viewerId != null && viewerId.equals(targetUserId)) {
            return true;
        }
        
        // 프로필 공개 설정 확인
        if (userService.isProfilePublic(targetUserId)) {
            return true;
        }
        
        // 비공개 프로필은 팔로워만 조회 가능
        if (viewerId != null) {
            return userService.isFollowing(viewerId, targetUserId);
        }
        
        return false;
    }
    
    /**
     * 프로필 수정 권한 확인
     */
    public boolean canEditProfile(Long editorId, Long targetUserId) {
        // 본인만 수정 가능
        return editorId != null && editorId.equals(targetUserId);
    }
}
```

---

## 📊 성능 최적화

### 캐싱 전략
```java
@Configuration
@EnableCaching
public class UserCacheConfig {
    
    @Bean
    public CacheManager userCacheManager() {
        RedisCacheManager.RedisCacheManagerBuilder builder = 
            RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(redisConnectionFactory);
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .entryTtl(Duration.ofMinutes(10));
        
        return builder
            .cacheDefaults(config)
            .withCacheConfiguration("userProfiles", 
                config.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("userSearchResults", 
                config.entryTtl(Duration.ofMinutes(5)))
            .build();
    }
}
```

---

## 📋 API 명세

### 1. 프로필 조회
- **URL**: `GET /api/v1/users/profile`
- **인증**: Required
- **응답**: UserProfileResponse

### 2. 프로필 수정
- **URL**: `PUT /api/v1/users/profile`
- **인증**: Required
- **요청**: UserProfileUpdateRequest
- **응답**: UserProfileResponse

### 3. 프로필 이미지 업로드
- **URL**: `POST /api/v1/users/profile/image`
- **인증**: Required
- **Rate Limit**: 5분간 5회
- **요청**: Multipart Form Data
- **응답**: ProfileImageResponse

### 4. 사용자 검색
- **URL**: `GET /api/v1/users/search`
- **요청**: UserSearchRequest
- **응답**: UserSearchResponse (페이징)

### 5. 계정 비활성화
- **URL**: `POST /api/v1/users/deactivate`
- **인증**: Required
- **Rate Limit**: 5분간 1회
- **요청**: AccountDeactivateRequest
- **응답**: AccountDeactivateResponse

---

*Step 7-2a 완료: UserController 구현 (5개 엔드포인트)*