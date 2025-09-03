# Step 7-2b2: 팔로우 분석 및 추천 시스템

> 팔로우 분석, 추천, 상호 팔로우 관리 시스템  
> 생성일: 2025-08-25  
> 단계: 7-2b2 (Controller 레이어 - 팔로우 확장 기능)  
> 연관: step7-2b1_follow_controller_core.md

---

## 🎯 설계 목표

- **상호 팔로우**: 서로 팔로우하는 사용자 관리
- **추천 시스템**: AI 기반 팔로우 추천
- **분석 시스템**: 팔로우 트렌드 및 통계 분석
- **실시간 처리**: Redis 기반 실시간 분석

---

## 🤝 상호 팔로우 및 추천 Controller

### FollowController.java (Extended Features)
```java
package com.routepick.controller.user;

import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.user.response.*;
import com.routepick.service.user.FollowService;
import com.routepick.service.user.FollowRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.web.bind.annotation.*;

/**
 * 팔로우 확장 기능 Controller
 * - 상호 팔로우 관리
 * - 팔로우 추천
 * - 팔로우 분석
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FollowExtendedController {
    
    private final FollowService followService;
    private final FollowRecommendationService recommendationService;
    
    // ===== 상호 팔로우 =====
    
    /**
     * 상호 팔로우 목록
     * - 서로 팔로우하는 사용자들
     * - 추천 친구 후보
     */
    @GetMapping("/{userId}/mutual-follows")
    @Operation(summary = "상호 팔로우 목록", description = "서로 팔로우하는 사용자 목록")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "목록 조회 성공"),
        @SwaggerApiResponse(responseCode = "403", description = "권한 없음")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MutualFollowListResponse>> getMutualFollows(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "followedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Long requesterId) {
        
        log.info("상호 팔로우 목록 조회: userId={}, requesterId={}", userId, requesterId);
        
        // 본인만 조회 가능
        if (!requesterId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED", "본인의 상호 팔로우 목록만 조회 가능합니다."));
        }
        
        Page<MutualFollowResponse> mutualFollows = followService.getMutualFollows(userId, pageable);
        
        MutualFollowListResponse response = MutualFollowListResponse.builder()
            .mutualFollows(mutualFollows.getContent())
            .totalCount(mutualFollows.getTotalElements())
            .pagination(PageResponse.of(mutualFollows))
            .build();
        
        log.info("상호 팔로우 목록 조회 완료: userId={}, count={}", userId, mutualFollows.getTotalElements());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 상호 팔로우 통계
     */
    @GetMapping("/{userId}/mutual-follow-stats")
    @Operation(summary = "상호 팔로우 통계", description = "상호 팔로우 관련 통계")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MutualFollowStatsResponse>> getMutualFollowStats(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @AuthenticationPrincipal Long requesterId) {
        
        log.info("상호 팔로우 통계 조회: userId={}, requesterId={}", userId, requesterId);
        
        // 본인만 조회 가능
        if (!requesterId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED", "본인의 통계만 조회 가능합니다."));
        }
        
        MutualFollowStatsResponse stats = followService.getMutualFollowStats(userId);
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
    
    // ===== 팔로우 추천 =====
    
    /**
     * 팔로우 추천
     * - 상호 팔로우 기반 추천
     * - 클라이밍 레벨 유사도
     * - 활동 지역 기반
     */
    @GetMapping("/follow-recommendations")
    @Operation(summary = "팔로우 추천", description = "추천 사용자 목록")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "추천 목록 성공")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FollowRecommendationResponse>> getFollowRecommendations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("팔로우 추천 요청: userId={}, limit={}", userId, limit);
        
        FollowRecommendationResponse recommendations = recommendationService.getFollowRecommendations(userId, limit);
        
        log.info("팔로우 추천 완료: userId={}, recommendationCount={}", 
                userId, recommendations.getRecommendations().size());
        
        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }
    
    /**
     * 친구의 친구 추천
     */
    @GetMapping("/friend-of-friend-recommendations")
    @Operation(summary = "친구의 친구 추천", description = "2단계 연결 기반 추천")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FriendOfFriendRecommendationResponse>> getFriendOfFriendRecommendations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("친구의 친구 추천 요청: userId={}, limit={}", userId, limit);
        
        FriendOfFriendRecommendationResponse recommendations = 
            recommendationService.getFriendOfFriendRecommendations(userId, limit);
        
        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }
    
    /**
     * 지역 기반 추천
     */
    @GetMapping("/location-based-recommendations")
    @Operation(summary = "지역 기반 추천", description = "활동 지역이 유사한 사용자 추천")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LocationBasedRecommendationResponse>> getLocationBasedRecommendations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "50.0") double radiusKm,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("지역 기반 추천 요청: userId={}, radius={}km, limit={}", userId, radiusKm, limit);
        
        LocationBasedRecommendationResponse recommendations = 
            recommendationService.getLocationBasedRecommendations(userId, radiusKm, limit);
        
        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }
    
    /**
     * 클라이밍 레벨 기반 추천
     */
    @GetMapping("/level-based-recommendations")
    @Operation(summary = "레벨 기반 추천", description = "클라이밍 레벨이 유사한 사용자 추천")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LevelBasedRecommendationResponse>> getLevelBasedRecommendations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("레벨 기반 추천 요청: userId={}, limit={}", userId, limit);
        
        LevelBasedRecommendationResponse recommendations = 
            recommendationService.getLevelBasedRecommendations(userId, limit);
        
        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }
    
    // ===== 팔로우 분석 =====
    
    /**
     * 팔로우 활동 분석
     */
    @GetMapping("/{userId}/follow-activity")
    @Operation(summary = "팔로우 활동 분석", description = "팔로우 활동 패턴 분석")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FollowActivityResponse>> getFollowActivity(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal Long requesterId) {
        
        log.info("팔로우 활동 분석: userId={}, days={}, requesterId={}", userId, days, requesterId);
        
        // 본인만 조회 가능
        if (!requesterId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED", "본인의 활동 분석만 조회 가능합니다."));
        }
        
        FollowActivityResponse activity = followService.getFollowActivity(userId, days);
        
        return ResponseEntity.ok(ApiResponse.success(activity));
    }
    
    /**
     * 팔로우 네트워크 분석
     */
    @GetMapping("/{userId}/follow-network")
    @Operation(summary = "팔로우 네트워크 분석", description = "팔로우 네트워크 구조 분석")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FollowNetworkResponse>> getFollowNetwork(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @RequestParam(defaultValue = "2") int depth,
            @AuthenticationPrincipal Long requesterId) {
        
        log.info("팔로우 네트워크 분석: userId={}, depth={}, requesterId={}", userId, depth, requesterId);
        
        // 본인만 조회 가능
        if (!requesterId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED", "본인의 네트워크만 분석 가능합니다."));
        }
        
        FollowNetworkResponse network = followService.getFollowNetwork(userId, depth);
        
        return ResponseEntity.ok(ApiResponse.success(network));
    }
}
```

---

## 📊 팔로우 분석 서비스

### FollowAnalyticsService.java
```java
package com.routepick.service.analytics;

import com.routepick.dto.user.response.FollowAnalyticsResponse;
import com.routepick.dto.user.response.FollowTrendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 팔로우 분석 서비스
 * - 팔로우 트렌드 분석
 * - 인기 사용자 분석
 * - 실시간 팔로우 통계
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowAnalyticsService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String FOLLOW_STATS_PREFIX = "follow:stats:";
    private static final String FOLLOW_TREND_PREFIX = "follow:trend:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /**
     * 팔로우 통계 기록
     */
    public void recordFollow(Long followerId, Long followingId) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        
        // 일별 팔로우 수 증가
        String dailyKey = FOLLOW_STATS_PREFIX + "daily:" + date;
        redisTemplate.opsForHash().increment(dailyKey, "total", 1);
        redisTemplate.expire(dailyKey, 90, TimeUnit.DAYS); // 90일 보관
        
        // 사용자별 팔로우 활동
        String userFollowingKey = FOLLOW_STATS_PREFIX + "user:" + followerId + ":following:" + date;
        redisTemplate.opsForHash().increment(userFollowingKey, "count", 1);
        redisTemplate.expire(userFollowingKey, 30, TimeUnit.DAYS); // 30일 보관
        
        String userFollowerKey = FOLLOW_STATS_PREFIX + "user:" + followingId + ":followers:" + date;
        redisTemplate.opsForHash().increment(userFollowerKey, "count", 1);
        redisTemplate.expire(userFollowerKey, 30, TimeUnit.DAYS); // 30일 보관
        
        // 시간대별 통계 (실시간 분석용)
        String hourlyKey = FOLLOW_STATS_PREFIX + "hourly:" + date + ":" + LocalDateTime.now().getHour();
        redisTemplate.opsForHash().increment(hourlyKey, "follows", 1);
        redisTemplate.expire(hourlyKey, 7, TimeUnit.DAYS); // 7일 보관
        
        log.debug("팔로우 통계 기록: followerId={}, followingId={}, date={}", followerId, followingId, date);
    }
    
    /**
     * 언팔로우 통계 기록
     */
    public void recordUnfollow(Long followerId, Long followingId) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        
        // 일별 언팔로우 수 증가
        String dailyKey = FOLLOW_STATS_PREFIX + "daily:" + date;
        redisTemplate.opsForHash().increment(dailyKey, "unfollows", 1);
        
        // 시간대별 통계
        String hourlyKey = FOLLOW_STATS_PREFIX + "hourly:" + date + ":" + LocalDateTime.now().getHour();
        redisTemplate.opsForHash().increment(hourlyKey, "unfollows", 1);
        
        log.debug("언팔로우 통계 기록: followerId={}, followingId={}, date={}", followerId, followingId, date);
    }
    
    /**
     * 일별 팔로우 트렌드 조회
     */
    public List<FollowTrendResponse> getDailyFollowTrend(int days) {
        List<FollowTrendResponse> trends = new ArrayList<>();
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateStr = date.format(DATE_FORMATTER);
            String key = FOLLOW_STATS_PREFIX + "daily:" + dateStr;
            
            Map<Object, Object> stats = redisTemplate.opsForHash().entries(key);
            
            long follows = Long.parseLong(stats.getOrDefault("total", "0").toString());
            long unfollows = Long.parseLong(stats.getOrDefault("unfollows", "0").toString());
            
            trends.add(FollowTrendResponse.builder()
                .date(date)
                .followCount(follows)
                .unfollowCount(unfollows)
                .netGrowth(follows - unfollows)
                .build());
        }
        
        return trends;
    }
    
    /**
     * 시간대별 팔로우 활동 패턴
     */
    public Map<Integer, Long> getHourlyFollowPattern(int days) {
        Map<Integer, Long> hourlyPattern = new HashMap<>();
        
        for (int hour = 0; hour < 24; hour++) {
            long totalFollows = 0;
            
            for (int day = 0; day < days; day++) {
                LocalDate date = LocalDate.now().minusDays(day);
                String dateStr = date.format(DATE_FORMATTER);
                String key = FOLLOW_STATS_PREFIX + "hourly:" + dateStr + ":" + hour;
                
                Map<Object, Object> stats = redisTemplate.opsForHash().entries(key);
                totalFollows += Long.parseLong(stats.getOrDefault("follows", "0").toString());
            }
            
            hourlyPattern.put(hour, totalFollows);
        }
        
        return hourlyPattern;
    }
    
    /**
     * 사용자별 팔로우 활동 분석
     */
    public FollowAnalyticsResponse getUserFollowAnalytics(Long userId, int days) {
        long totalFollowing = 0;
        long totalFollowers = 0;
        List<LocalDate> activeDates = new ArrayList<>();
        
        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateStr = date.format(DATE_FORMATTER);
            
            // 팔로잉 활동
            String followingKey = FOLLOW_STATS_PREFIX + "user:" + userId + ":following:" + dateStr;
            Map<Object, Object> followingStats = redisTemplate.opsForHash().entries(followingKey);
            long dailyFollowing = Long.parseLong(followingStats.getOrDefault("count", "0").toString());
            
            // 팔로워 증가
            String followerKey = FOLLOW_STATS_PREFIX + "user:" + userId + ":followers:" + dateStr;
            Map<Object, Object> followerStats = redisTemplate.opsForHash().entries(followerKey);
            long dailyFollowers = Long.parseLong(followerStats.getOrDefault("count", "0").toString());
            
            totalFollowing += dailyFollowing;
            totalFollowers += dailyFollowers;
            
            if (dailyFollowing > 0 || dailyFollowers > 0) {
                activeDates.add(date);
            }
        }
        
        return FollowAnalyticsResponse.builder()
            .userId(userId)
            .analysisperiod(days)
            .totalFollowingActivity(totalFollowing)
            .totalFollowerGrowth(totalFollowers)
            .activeDays(activeDates.size())
            .activeDates(activeDates)
            .averageDailyFollowing(totalFollowing / (double) days)
            .averageDailyFollowerGrowth(totalFollowers / (double) days)
            .build();
    }
}
```

---

## 🤖 팔로우 추천 시스템

### FollowRecommendationService.java
```java
package com.routepick.service.user;

import com.routepick.dto.user.response.*;
import com.routepick.repository.user.UserFollowRepository;
import com.routepick.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 팔로우 추천 서비스
 * - AI 기반 사용자 추천
 * - 다양한 추천 알고리즘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowRecommendationService {
    
    private final UserFollowRepository userFollowRepository;
    private final UserService userService;
    
    /**
     * 종합 팔로우 추천
     */
    @Cacheable(value = "followRecommendations", key = "#userId + '_' + #limit")
    public FollowRecommendationResponse getFollowRecommendations(Long userId, int limit) {
        List<UserRecommendation> recommendations = new ArrayList<>();
        
        // 1. 친구의 친구 추천 (가중치: 0.4)
        List<UserRecommendation> friendOfFriends = getFriendOfFriendRecommendationsInternal(userId, limit / 2);
        friendOfFriends.forEach(rec -> rec.setScore(rec.getScore() * 0.4));
        recommendations.addAll(friendOfFriends);
        
        // 2. 지역 기반 추천 (가중치: 0.3)
        List<UserRecommendation> locationBased = getLocationBasedRecommendationsInternal(userId, 50.0, limit / 3);
        locationBased.forEach(rec -> rec.setScore(rec.getScore() * 0.3));
        recommendations.addAll(locationBased);
        
        // 3. 레벨 기반 추천 (가중치: 0.2)
        List<UserRecommendation> levelBased = getLevelBasedRecommendationsInternal(userId, limit / 4);
        levelBased.forEach(rec -> rec.setScore(rec.getScore() * 0.2));
        recommendations.addAll(levelBased);
        
        // 4. 활동 유사도 기반 추천 (가중치: 0.1)
        List<UserRecommendation> activityBased = getActivityBasedRecommendationsInternal(userId, limit / 10);
        activityBased.forEach(rec -> rec.setScore(rec.getScore() * 0.1));
        recommendations.addAll(activityBased);
        
        // 중복 제거 및 점수 합산
        Map<Long, UserRecommendation> mergedRecommendations = recommendations.stream()
            .collect(Collectors.groupingBy(
                UserRecommendation::getUserId,
                Collectors.reducing(null, (rec1, rec2) -> {
                    if (rec1 == null) return rec2;
                    rec1.setScore(rec1.getScore() + rec2.getScore());
                    rec1.getReasons().addAll(rec2.getReasons());
                    return rec1;
                })
            ));
        
        // 점수순 정렬 및 제한
        List<UserRecommendation> finalRecommendations = mergedRecommendations.values().stream()
            .filter(rec -> rec != null)
            .sorted((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()))
            .limit(limit)
            .collect(Collectors.toList());
        
        return FollowRecommendationResponse.builder()
            .recommendations(finalRecommendations)
            .totalCount(finalRecommendations.size())
            .algorithmVersion("v2.1")
            .build();
    }
    
    /**
     * 친구의 친구 추천
     */
    @Cacheable(value = "friendOfFriendRecommendations", key = "#userId + '_' + #limit")
    public FriendOfFriendRecommendationResponse getFriendOfFriendRecommendations(Long userId, int limit) {
        List<UserRecommendation> recommendations = getFriendOfFriendRecommendationsInternal(userId, limit);
        
        return FriendOfFriendRecommendationResponse.builder()
            .recommendations(recommendations)
            .totalCount(recommendations.size())
            .build();
    }
    
    private List<UserRecommendation> getFriendOfFriendRecommendationsInternal(Long userId, int limit) {
        // 사용자가 팔로우하는 사람들
        List<Long> followingIds = userFollowRepository.findFollowingIdsByUserId(userId);
        
        if (followingIds.isEmpty()) {
            return List.of();
        }
        
        // 팔로우하는 사람들이 팔로우하는 사람들 (2단계 연결)
        List<UserFollowCount> friendOfFriends = userFollowRepository
            .findFriendOfFriendRecommendations(userId, followingIds, limit * 2);
        
        return friendOfFriends.stream()
            .map(fof -> UserRecommendation.builder()
                .userId(fof.getUserId())
                .nickname(userService.getNickname(fof.getUserId()))
                .profileImageUrl(userService.getProfileImageUrl(fof.getUserId()))
                .score(calculateFriendOfFriendScore(fof.getCount(), followingIds.size()))
                .reasons(List.of("친구 " + fof.getCount() + "명이 팔로우"))
                .recommendationType(RecommendationType.FRIEND_OF_FRIEND)
                .build())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * 지역 기반 추천
     */
    @Cacheable(value = "locationBasedRecommendations", key = "#userId + '_' + #radiusKm + '_' + #limit")
    public LocationBasedRecommendationResponse getLocationBasedRecommendations(Long userId, double radiusKm, int limit) {
        List<UserRecommendation> recommendations = getLocationBasedRecommendationsInternal(userId, radiusKm, limit);
        
        return LocationBasedRecommendationResponse.builder()
            .recommendations(recommendations)
            .radiusKm(radiusKm)
            .totalCount(recommendations.size())
            .build();
    }
    
    private List<UserRecommendation> getLocationBasedRecommendationsInternal(Long userId, double radiusKm, int limit) {
        // 사용자의 주 활동 지역 조회
        UserLocation userLocation = userService.getPrimaryLocation(userId);
        if (userLocation == null) {
            return List.of();
        }
        
        // 반경 내 사용자 검색
        List<NearbyUser> nearbyUsers = userService.findNearbyUsers(
            userLocation.getLatitude(), userLocation.getLongitude(), radiusKm, userId, limit);
        
        return nearbyUsers.stream()
            .map(nearby -> UserRecommendation.builder()
                .userId(nearby.getUserId())
                .nickname(userService.getNickname(nearby.getUserId()))
                .profileImageUrl(userService.getProfileImageUrl(nearby.getUserId()))
                .score(calculateLocationScore(nearby.getDistanceKm(), radiusKm))
                .reasons(List.of(String.format("%.1fkm 거리", nearby.getDistanceKm())))
                .recommendationType(RecommendationType.LOCATION_BASED)
                .build())
            .collect(Collectors.toList());
    }
    
    /**
     * 클라이밍 레벨 기반 추천
     */
    @Cacheable(value = "levelBasedRecommendations", key = "#userId + '_' + #limit")
    public LevelBasedRecommendationResponse getLevelBasedRecommendations(Long userId, int limit) {
        List<UserRecommendation> recommendations = getLevelBasedRecommendationsInternal(userId, limit);
        
        return LevelBasedRecommendationResponse.builder()
            .recommendations(recommendations)
            .totalCount(recommendations.size())
            .build();
    }
    
    private List<UserRecommendation> getLevelBasedRecommendationsInternal(Long userId, int limit) {
        // 사용자의 클라이밍 레벨 조회
        UserClimbingLevel userLevel = userService.getClimbingLevel(userId);
        if (userLevel == null) {
            return List.of();
        }
        
        // 유사한 레벨의 사용자 검색
        List<SimilarLevelUser> similarUsers = userService.findSimilarLevelUsers(
            userLevel.getBoulderingLevel(), userLevel.getSportClimbingLevel(), userId, limit);
        
        return similarUsers.stream()
            .map(similar -> UserRecommendation.builder()
                .userId(similar.getUserId())
                .nickname(userService.getNickname(similar.getUserId()))
                .profileImageUrl(userService.getProfileImageUrl(similar.getUserId()))
                .score(calculateLevelSimilarityScore(similar.getLevelDifference()))
                .reasons(List.of("유사한 클라이밍 레벨"))
                .recommendationType(RecommendationType.LEVEL_BASED)
                .build())
            .collect(Collectors.toList());
    }
    
    // ===== 점수 계산 메서드 =====
    
    private double calculateFriendOfFriendScore(int mutualFriendCount, int totalFriendCount) {
        return (double) mutualFriendCount / totalFriendCount * 100.0;
    }
    
    private double calculateLocationScore(double distanceKm, double maxRadiusKm) {
        return Math.max(0, (maxRadiusKm - distanceKm) / maxRadiusKm * 100.0);
    }
    
    private double calculateLevelSimilarityScore(int levelDifference) {
        return Math.max(0, (5 - levelDifference) / 5.0 * 100.0);
    }
}
```

---

## 📊 확장 API 명세

### 6. 상호 팔로우 목록
- **URL**: `GET /api/v1/users/{userId}/mutual-follows`
- **인증**: Required
- **권한**: 본인만 조회 가능
- **응답**: MutualFollowListResponse (페이징)

### 7. 팔로우 추천
- **URL**: `GET /api/v1/users/follow-recommendations`
- **인증**: Required
- **파라미터**: `limit` (기본값: 10)
- **응답**: FollowRecommendationResponse

### 8. 친구의 친구 추천
- **URL**: `GET /api/v1/users/friend-of-friend-recommendations`
- **인증**: Required
- **파라미터**: `limit` (기본값: 10)
- **응답**: FriendOfFriendRecommendationResponse

### 9. 지역 기반 추천
- **URL**: `GET /api/v1/users/location-based-recommendations`
- **인증**: Required  
- **파라미터**: 
  - `radiusKm` (기본값: 50.0)
  - `limit` (기본값: 10)
- **응답**: LocationBasedRecommendationResponse

### 10. 팔로우 활동 분석
- **URL**: `GET /api/v1/users/{userId}/follow-activity`
- **인증**: Required
- **권한**: 본인만 조회 가능
- **파라미터**: `days` (기본값: 30)
- **응답**: FollowActivityResponse

---

## 🎯 추천 알고리즘

### 종합 추천 시스템
- **친구의 친구**: 40% 가중치
- **지역 기반**: 30% 가중치  
- **레벨 기반**: 20% 가중치
- **활동 유사도**: 10% 가중치

### 점수 계산
- **친구의 친구**: 공통 친구 수 / 총 친구 수 × 100
- **지역 기반**: (최대 거리 - 실제 거리) / 최대 거리 × 100
- **레벨 유사도**: (5 - 레벨 차이) / 5 × 100

### 필터링 조건
- 이미 팔로우 중인 사용자 제외
- 차단된 사용자 제외
- 비공개 프로필 사용자 제외 (설정에 따라)

---

## 📈 실시간 분석

### Redis 기반 통계
- **일별 팔로우**: 90일 보관
- **시간대별 활동**: 7일 보관
- **사용자별 활동**: 30일 보관

### 분석 지표
- 팔로우/언팔로우 트렌드
- 시간대별 활동 패턴
- 사용자별 활동 분석
- 네트워크 성장률

---

## 🚀 성능 최적화

### 캐싱 전략
- 추천 결과 4시간 캐싱
- 분석 결과 1시간 캐싱
- 통계 데이터 실시간 업데이트

### 배치 처리
- 대량 추천 계산 배치
- 통계 집계 배치 처리
- ML 모델 학습 배치

---

## 🎯 **연관 파일**

**이전 파일:**
- **step7-2b1_follow_controller_core.md**: 팔로우 핵심 기능

**다음 파일:**
- **step7-2c_user_request_dtos.md**: 사용자 관련 Request DTOs
- **step7-2d_user_response_dtos.md**: 사용자 관련 Response DTOs

*step7-2b2 완성: 팔로우 분석 및 추천 시스템 설계 완료*