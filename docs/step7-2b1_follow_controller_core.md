# Step 7-2b1: FollowController 구현 - 핵심 팔로우 관리

> 팔로우 관리 RESTful API Controller - 팔로우/언팔로우, 목록 조회  
> 생성일: 2025-08-25  
> 단계: 7-2b1 (Controller 레이어 - 팔로우 핵심 기능)  
> 기반: step6-1c_user_service.md, 팔로우 시스템

---

## 🎯 설계 원칙

- **팔로우/언팔로우**: 중복 방지, 자기 자신 팔로우 차단
- **권한 관리**: 인증된 사용자만 팔로우 가능
- **성능 최적화**: 팔로우 카운트 캐싱
- **알림 연동**: 팔로우 시 실시간 알림

---

## 👥 FollowController 핵심 구현

### FollowController.java (Core)
```java
package com.routepick.controller.user;

import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.user.response.*;
import com.routepick.service.user.UserService;
import com.routepick.service.user.FollowService;
import com.routepick.service.notification.NotificationService;
import com.routepick.annotation.RateLimited;
import com.routepick.exception.user.UserNotFoundException;
import com.routepick.exception.user.SelfFollowException;
import com.routepick.exception.user.AlreadyFollowingException;
import com.routepick.exception.user.NotFollowingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 팔로우 관리 Controller - 핵심 기능
 * - 팔로우/언팔로우
 * - 팔로워/팔로잉 목록
 * - 팔로우 통계
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "팔로우 관리", description = "사용자 팔로우 관계 관리 API")
public class FollowController {
    
    private final UserService userService;
    private final FollowService followService;
    private final NotificationService notificationService;
    
    // ===== 팔로우/언팔로우 =====
    
    /**
     * 팔로우
     * - 본인 팔로우 차단
     * - 중복 팔로우 방지
     * - 실시간 알림 발송
     */
    @PostMapping("/{userId}/follow")
    @Operation(summary = "사용자 팔로우", description = "특정 사용자를 팔로우")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "팔로우 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "본인 팔로우 불가"),
        @SwaggerApiResponse(responseCode = "409", description = "이미 팔로우 중"),
        @SwaggerApiResponse(responseCode = "404", description = "사용자 없음")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 60) // 1분간 30회 제한
    public ResponseEntity<ApiResponse<FollowResponse>> followUser(
            @Parameter(description = "팔로우할 사용자 ID") @PathVariable Long userId,
            @AuthenticationPrincipal Long followerId) {
        
        log.info("팔로우 요청: followerId={}, targetUserId={}", followerId, userId);
        
        // 본인 팔로우 차단
        if (followerId.equals(userId)) {
            throw new SelfFollowException("자기 자신을 팔로우할 수 없습니다.");
        }
        
        // 이미 팔로우 중인지 확인
        if (followService.isFollowing(followerId, userId)) {
            throw new AlreadyFollowingException("이미 팔로우 중인 사용자입니다.");
        }
        
        // 팔로우 처리
        FollowResponse response = followService.follow(followerId, userId);
        
        // 팔로우 알림 발송 (비동기)
        notificationService.sendFollowNotification(userId, followerId);
        
        log.info("팔로우 성공: followerId={}, targetUserId={}, followedAt={}", 
                followerId, userId, response.getFollowedAt());
        
        return ResponseEntity.ok(ApiResponse.success(response, "팔로우가 완료되었습니다."));
    }
    
    /**
     * 언팔로우
     * - 팔로우 관계 확인 후 제거
     * - 카운트 업데이트
     */
    @DeleteMapping("/{userId}/follow")
    @Operation(summary = "사용자 언팔로우", description = "특정 사용자 팔로우 해제")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "언팔로우 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "팔로우 중이 아님"),
        @SwaggerApiResponse(responseCode = "404", description = "사용자 없음")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 60) // 1분간 30회 제한
    public ResponseEntity<ApiResponse<Void>> unfollowUser(
            @Parameter(description = "언팔로우할 사용자 ID") @PathVariable Long userId,
            @AuthenticationPrincipal Long followerId) {
        
        log.info("언팔로우 요청: followerId={}, targetUserId={}", followerId, userId);
        
        // 팔로우 관계 확인
        if (!followService.isFollowing(followerId, userId)) {
            throw new NotFollowingException("팔로우 중이 아닌 사용자입니다.");
        }
        
        // 언팔로우 처리
        followService.unfollow(followerId, userId);
        
        log.info("언팔로우 성공: followerId={}, targetUserId={}", followerId, userId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "언팔로우가 완료되었습니다."));
    }
    
    // ===== 팔로워 목록 =====
    
    /**
     * 팔로워 목록 조회
     * - 프로필 공개 설정에 따른 접근 제어
     * - 페이징 지원
     * - 상호 팔로우 표시
     */
    @GetMapping("/{userId}/followers")
    @Operation(summary = "팔로워 목록", description = "특정 사용자의 팔로워 목록 조회")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "목록 조회 성공"),
        @SwaggerApiResponse(responseCode = "403", description = "비공개 프로필"),
        @SwaggerApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<ApiResponse<FollowerListResponse>> getFollowers(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "followedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Long requesterId) {
        
        log.info("팔로워 목록 조회: userId={}, requesterId={}, page={}, size={}", 
                userId, requesterId, pageable.getPageNumber(), pageable.getPageSize());
        
        // 프로필 접근 권한 확인
        if (!canViewFollowList(userId, requesterId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("PROFILE_ACCESS_DENIED", 
                    "비공개 프로필입니다. 팔로우 후 조회 가능합니다."));
        }
        
        // 팔로워 목록 조회
        Page<FollowerResponse> followers = followService.getFollowers(userId, requesterId, pageable);
        
        FollowerListResponse response = FollowerListResponse.builder()
            .followers(followers.getContent())
            .totalCount(followers.getTotalElements())
            .pagination(PageResponse.of(followers))
            .build();
        
        log.info("팔로워 목록 조회 완료: userId={}, totalFollowers={}", 
                userId, followers.getTotalElements());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    // ===== 팔로잉 목록 =====
    
    /**
     * 팔로잉 목록 조회
     * - 프로필 공개 설정에 따른 접근 제어
     * - 페이징 지원
     * - 온라인 상태 표시
     */
    @GetMapping("/{userId}/following")
    @Operation(summary = "팔로잉 목록", description = "특정 사용자가 팔로우하는 사용자 목록 조회")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "목록 조회 성공"),
        @SwaggerApiResponse(responseCode = "403", description = "비공개 프로필"),
        @SwaggerApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<ApiResponse<FollowingListResponse>> getFollowing(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "followedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Long requesterId) {
        
        log.info("팔로잉 목록 조회: userId={}, requesterId={}, page={}, size={}", 
                userId, requesterId, pageable.getPageNumber(), pageable.getPageSize());
        
        // 프로필 접근 권한 확인
        if (!canViewFollowList(userId, requesterId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("PROFILE_ACCESS_DENIED", 
                    "비공개 프로필입니다. 팔로우 후 조회 가능합니다."));
        }
        
        // 팔로잉 목록 조회
        Page<FollowingResponse> following = followService.getFollowing(userId, requesterId, pageable);
        
        FollowingListResponse response = FollowingListResponse.builder()
            .following(following.getContent())
            .totalCount(following.getTotalElements())
            .pagination(PageResponse.of(following))
            .build();
        
        log.info("팔로잉 목록 조회 완료: userId={}, totalFollowing={}", 
                userId, following.getTotalElements());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    // ===== 팔로우 통계 =====
    
    /**
     * 팔로우 통계 조회
     * - 팔로워/팔로잉 수
     * - 상호 팔로우 수
     * - 최근 활동
     */
    @GetMapping("/{userId}/follow-stats")
    @Operation(summary = "팔로우 통계", description = "팔로워/팔로잉 통계 정보")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "통계 조회 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<ApiResponse<FollowStatsResponse>> getFollowStats(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @AuthenticationPrincipal Long requesterId) {
        
        log.info("팔로우 통계 조회: userId={}, requesterId={}", userId, requesterId);
        
        // 사용자 존재 확인
        if (!userService.existsById(userId)) {
            throw new UserNotFoundException("존재하지 않는 사용자입니다.");
        }
        
        // 팔로우 통계 조회
        FollowStatsResponse stats = followService.getFollowStats(userId, requesterId);
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 팔로우 목록 조회 권한 확인
     */
    private boolean canViewFollowList(Long targetUserId, Long requesterId) {
        // 본인은 항상 조회 가능
        if (requesterId != null && requesterId.equals(targetUserId)) {
            return true;
        }
        
        // 프로필 공개 설정 확인
        if (userService.isProfilePublic(targetUserId)) {
            return true;
        }
        
        // 비공개 프로필은 팔로워만 조회 가능
        if (requesterId != null) {
            return followService.isFollowing(requesterId, targetUserId);
        }
        
        return false;
    }
}
```

---

## 🔄 FollowService 핵심 구현

### FollowService.java (Core Features)
```java
package com.routepick.service.user;

import com.routepick.dto.user.response.*;
import com.routepick.entity.user.UserFollow;
import com.routepick.repository.user.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 팔로우 관리 서비스 - 핵심 기능
 * - 팔로우/언팔로우 처리
 * - 팔로우 목록 조회
 * - 팔로우 통계
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {
    
    private final UserFollowRepository userFollowRepository;
    private final UserService userService;
    
    // ===== 팔로우 관리 =====
    
    /**
     * 팔로우 처리
     */
    @Transactional
    @CacheEvict(value = {"followStats", "userProfiles", "followStatus"}, allEntries = true)
    public FollowResponse follow(Long followerId, Long followingId) {
        // 사용자 존재 확인
        userService.validateUser(followerId);
        userService.validateUser(followingId);
        
        // 팔로우 관계 생성
        UserFollow follow = UserFollow.builder()
            .followerId(followerId)
            .followingId(followingId)
            .followedAt(LocalDateTime.now())
            .build();
        
        userFollowRepository.save(follow);
        
        // 팔로우 카운트 업데이트
        updateFollowCounts(followerId, followingId, true);
        
        log.info("팔로우 처리 완료: followerId={}, followingId={}", followerId, followingId);
        
        return FollowResponse.builder()
            .followerId(followerId)
            .followingId(followingId)
            .followedAt(follow.getFollowedAt())
            .isFollowing(true)
            .build();
    }
    
    /**
     * 언팔로우 처리
     */
    @Transactional
    @CacheEvict(value = {"followStats", "userProfiles", "followStatus"}, allEntries = true)
    public void unfollow(Long followerId, Long followingId) {
        int deletedCount = userFollowRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
        
        if (deletedCount > 0) {
            // 팔로우 카운트 업데이트
            updateFollowCounts(followerId, followingId, false);
            log.info("언팔로우 처리 완료: followerId={}, followingId={}", followerId, followingId);
        }
    }
    
    /**
     * 팔로우 관계 확인
     */
    @Cacheable(value = "followStatus", key = "#followerId + '_' + #followingId")
    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }
    
    // ===== 팔로우 목록 조회 =====
    
    /**
     * 팔로워 목록 조회
     */
    @Cacheable(value = "followerList", key = "#userId + '_' + #requesterId + '_' + #pageable.pageNumber")
    public Page<FollowerResponse> getFollowers(Long userId, Long requesterId, Pageable pageable) {
        return userFollowRepository.findFollowersByUserId(userId, requesterId, pageable)
            .map(userFollow -> {
                Long followerId = userFollow.getFollowerId();
                
                return FollowerResponse.builder()
                    .userId(followerId)
                    .nickname(userService.getNickname(followerId))
                    .profileImageUrl(userService.getProfileImageUrl(followerId))
                    .isOnline(userService.isOnline(followerId))
                    .followedAt(userFollow.getFollowedAt())
                    .isMutualFollow(isFollowing(userId, followerId))
                    .build();
            });
    }
    
    /**
     * 팔로잉 목록 조회
     */
    @Cacheable(value = "followingList", key = "#userId + '_' + #requesterId + '_' + #pageable.pageNumber")
    public Page<FollowingResponse> getFollowing(Long userId, Long requesterId, Pageable pageable) {
        return userFollowRepository.findFollowingByUserId(userId, requesterId, pageable)
            .map(userFollow -> {
                Long followingId = userFollow.getFollowingId();
                
                return FollowingResponse.builder()
                    .userId(followingId)
                    .nickname(userService.getNickname(followingId))
                    .profileImageUrl(userService.getProfileImageUrl(followingId))
                    .isOnline(userService.isOnline(followingId))
                    .followedAt(userFollow.getFollowedAt())
                    .isMutualFollow(isFollowing(followingId, userId))
                    .build();
            });
    }
    
    // ===== 팔로우 통계 =====
    
    /**
     * 팔로우 통계 조회
     */
    @Cacheable(value = "followStats", key = "#userId + '_' + #requesterId")
    public FollowStatsResponse getFollowStats(Long userId, Long requesterId) {
        long followerCount = userFollowRepository.countByFollowingId(userId);
        long followingCount = userFollowRepository.countByFollowerId(userId);
        long mutualFollowCount = userFollowRepository.countMutualFollows(userId);
        
        // 최근 팔로워 5명
        List<FollowerResponse> recentFollowers = userFollowRepository
            .findTop5ByFollowingIdOrderByFollowedAtDesc(userId)
            .stream()
            .map(userFollow -> {
                Long followerId = userFollow.getFollowerId();
                return FollowerResponse.builder()
                    .userId(followerId)
                    .nickname(userService.getNickname(followerId))
                    .profileImageUrl(userService.getProfileImageUrl(followerId))
                    .followedAt(userFollow.getFollowedAt())
                    .build();
            })
            .toList();
        
        // 요청자와의 관계 정보 (인증된 사용자만)
        Boolean isFollowingUser = null;
        Boolean isFollowedByUser = null;
        
        if (requesterId != null && !requesterId.equals(userId)) {
            isFollowingUser = isFollowing(requesterId, userId);
            isFollowedByUser = isFollowing(userId, requesterId);
        }
        
        return FollowStatsResponse.builder()
            .userId(userId)
            .followerCount(followerCount)
            .followingCount(followingCount)
            .mutualFollowCount(mutualFollowCount)
            .recentFollowers(recentFollowers)
            .isFollowingUser(isFollowingUser)
            .isFollowedByUser(isFollowedByUser)
            .build();
    }
    
    // ===== 내부 메서드 =====
    
    /**
     * 팔로우 카운트 업데이트
     */
    private void updateFollowCounts(Long followerId, Long followingId, boolean isFollow) {
        int delta = isFollow ? 1 : -1;
        
        // 팔로잉 카운트 업데이트 (팔로우하는 사람)
        userService.updateFollowingCount(followerId, delta);
        
        // 팔로워 카운트 업데이트 (팔로우받는 사람)
        userService.updateFollowerCount(followingId, delta);
    }
}
```

---

## 📋 핵심 API 명세

### 1. 팔로우
- **URL**: `POST /api/v1/users/{userId}/follow`
- **인증**: Required (Bearer Token)
- **Rate Limit**: 1분간 30회
- **Body**: 없음
- **응답**: FollowResponse

### 2. 언팔로우  
- **URL**: `DELETE /api/v1/users/{userId}/follow`
- **인증**: Required (Bearer Token)
- **Rate Limit**: 1분간 30회
- **Body**: 없음
- **응답**: 성공 메시지

### 3. 팔로워 목록
- **URL**: `GET /api/v1/users/{userId}/followers`
- **인증**: Optional
- **파라미터**: 
  - `page`: 페이지 번호 (기본값: 0)
  - `size`: 페이지 크기 (기본값: 20, 최대 100)
  - `sort`: 정렬 기준 (기본값: followedAt,desc)
- **응답**: FollowerListResponse (페이징)

### 4. 팔로잉 목록
- **URL**: `GET /api/v1/users/{userId}/following`
- **인증**: Optional
- **파라미터**: page, size, sort
- **응답**: FollowingListResponse (페이징)

### 5. 팔로우 통계
- **URL**: `GET /api/v1/users/{userId}/follow-stats`
- **인증**: Optional
- **응답**: FollowStatsResponse

---

## 🛡️ 보안 및 권한 관리

### 접근 제어
- **공개 프로필**: 모든 사용자가 팔로우 목록 조회 가능
- **비공개 프로필**: 팔로워만 팔로우 목록 조회 가능
- **본인 프로필**: 항상 조회 가능

### Rate Limiting
- **팔로우/언팔로우**: 1분간 30회 제한
- **스팸 방지**: 동일 사용자 반복 팔로우 방지
- **자기 팔로우 차단**: 본인 팔로우 불가

### 데이터 보호
- **민감 정보 마스킹**: 개인정보 보호
- **캐시 무효화**: 관계 변경 시 즉시 반영
- **트랜잭션 보장**: 팔로우 카운트 일관성

---

## 💾 캐싱 전략

### 캐시 키 구조
- **팔로우 상태**: `followStatus:{followerId}_{followingId}`
- **팔로워 목록**: `followerList:{userId}_{requesterId}_{page}`
- **팔로잉 목록**: `followingList:{userId}_{requesterId}_{page}`
- **팔로우 통계**: `followStats:{userId}_{requesterId}`

### 캐시 무효화
- **팔로우/언팔로우**: 관련 모든 캐시 무효화
- **프로필 변경**: 사용자 관련 캐시 무효화
- **선택적 무효화**: 영향받는 캐시만 정확히 무효화

---

## 🚀 성능 최적화

### 쿼리 최적화
- **인덱스 활용**: (followerId, followingId) 복합 인덱스
- **페이징 최적화**: 커서 기반 페이징 고려
- **배치 처리**: 대량 팔로우 처리 최적화

### 알림 최적화
- **비동기 처리**: 팔로우 알림 비동기 발송
- **큐 시스템**: 대량 알림 처리
- **중복 방지**: 동일 알림 중복 발송 방지

---

## 🎯 **다음 단계**

**연관 파일:**
- **step7-2b2_follow_analytics_recommendations.md**: 팔로우 분석 및 추천 시스템

*step7-2b1 완성: FollowController 핵심 기능 설계 완료*