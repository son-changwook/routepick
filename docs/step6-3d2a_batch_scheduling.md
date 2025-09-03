# step6-3d2a_batch_scheduling.md

> AI 기반 개인화 추천 서비스 - 배치 처리 및 스케줄링  
> 생성일: 2025-08-22  
> 단계: 6-3d2a (Service 레이어 - 추천 배치 시스템)  
> 참고: step1-2, step4-2a, step5-2b

---

## 🎯 설계 목표

- **개인화 추천**: 사용자 선호 태그 기반 맞춤 추천
- **실시간 계산**: 활동 기반 추천 업데이트
- **배치 처리**: 스케줄링 기반 대량 추천 계산
- **캐싱 최적화**: Redis 1시간 TTL
- **점수 체계**: tag_match_score(70%) + level_match_score(30%)

---

## 🤖 RecommendationService 구현

### RecommendationService.java
```java
package com.routepick.service.recommendation;

import com.routepick.common.enums.PreferenceLevel;
import com.routepick.common.enums.RouteStatus;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.tag.entity.UserPreferredTag;
import com.routepick.domain.tag.entity.UserRouteRecommendation;
import com.routepick.domain.tag.repository.UserPreferredTagRepository;
import com.routepick.domain.tag.repository.UserRouteRecommendationRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.recommendation.RecommendationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 루트 추천 서비스
 * 
 * 추천 점수 계산:
 * - 태그 매칭: 70% (사용자 선호 태그와 루트 태그 매칭)
 * - 레벨 매칭: 30% (사용자 클라이밍 레벨과 루트 난이도)
 * 
 * 최종 점수 = (tag_match_score * 0.7) + (level_match_score * 0.3)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final UserPreferredTagRepository userPreferredTagRepository;
    private final UserRouteRecommendationRepository recommendationRepository;
    private final RouteTagRepository routeTagRepository;
    private final UserClimbRepository userClimbRepository;
    private final JdbcTemplate jdbcTemplate;

    // 캐시 키 상수
    private static final String CACHE_USER_RECOMMENDATIONS = "user_recommendations";
    private static final String CACHE_ROUTE_RECOMMENDATIONS = "route_recommendations";
    
    // 알고리즘 상수
    private static final BigDecimal TAG_WEIGHT = new BigDecimal("0.7");
    private static final BigDecimal LEVEL_WEIGHT = new BigDecimal("0.3");
    private static final BigDecimal MIN_RECOMMENDATION_SCORE = new BigDecimal("0.3");
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 20;
    
    // ===== 추천 조회 =====

    /**
     * 사용자별 추천 루트 조회
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 추천 루트 페이지
     */
    @Cacheable(value = CACHE_USER_RECOMMENDATIONS, 
               key = "#userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<UserRouteRecommendation> getUserRecommendations(Long userId, Pageable pageable) {
        log.debug("Getting recommendations for user: {}", userId);
        return recommendationRepository.findActiveRecommendations(userId, pageable);
    }
    
    /**
     * 특정 루트의 추천 대상 사용자 조회 
     * @param routeId 루트 ID
     * @param pageable 페이징 정보
     * @return 추천 대상 사용자 페이지
     */
    @Cacheable(value = CACHE_ROUTE_RECOMMENDATIONS,
               key = "#routeId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<UserRouteRecommendation> getRouteRecommendations(Long routeId, Pageable pageable) {
        log.debug("Getting users recommended for route: {}", routeId);
        return recommendationRepository.findByRouteIdAndIsActiveTrue(routeId, pageable);
    }

    // ===== 추천 계산 =====

    /**
     * 사용자별 루트 추천 계산 및 저장
     * @param userId 사용자 ID
     * @return 계산된 추천 수
     */
    @Async
    @Transactional
    @CacheEvict(value = CACHE_USER_RECOMMENDATIONS, key = "#userId + '*'")
    public CompletableFuture<Integer> calculateUserRecommendations(Long userId) {
        log.info("Calculating route recommendations for user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RecommendationException("사용자를 찾을 수 없습니다: " + userId));

        // 사용자 선호 태그 조회
        List<UserPreferredTag> preferences = userPreferredTagRepository
            .findByUserIdAndIsActiveTrue(userId);
            
        if (preferences.isEmpty()) {
            log.warn("No user preferences found for user: {}", userId);
            return CompletableFuture.completedFuture(0);
        }

        // 기존 추천 데이터 비활성화
        recommendationRepository.deactivateUserRecommendations(userId);

        // 활성 루트 조회 (사용자가 이미 완등한 루트 제외)
        List<Route> candidateRoutes = routeRepository
            .findActiveRoutesNotClimbedByUser(userId, RouteStatus.ACTIVE);

        int recommendationCount = 0;
        
        for (Route route : candidateRoutes) {
            try {
                // 추천 점수 계산
                BigDecimal recommendationScore = calculateRecommendationScore(user, route, preferences);
                
                // 최소 점수 이상인 경우만 저장
                if (recommendationScore.compareTo(MIN_RECOMMENDATION_SCORE) >= 0) {
                    UserRouteRecommendation recommendation = createRecommendation(
                        user, route, recommendationScore, preferences);
                    recommendationRepository.save(recommendation);
                    recommendationCount++;
                }
            } catch (Exception e) {
                log.error("Error calculating recommendation for route {}: {}", 
                         route.getRouteId(), e.getMessage());
            }
        }

        log.info("Generated {} recommendations for user: {}", recommendationCount, userId);
        return CompletableFuture.completedFuture(recommendationCount);
    }

    /**
     * 추천 점수 계산 (태그 매칭 70% + 레벨 매칭 30%)
     */
    private BigDecimal calculateRecommendationScore(User user, Route route, 
                                                   List<UserPreferredTag> preferences) {
        // 1. 태그 매칭 점수 계산 (70%)
        BigDecimal tagMatchScore = calculateTagMatchScore(route, preferences);
        
        // 2. 레벨 매칭 점수 계산 (30%)  
        BigDecimal levelMatchScore = calculateLevelMatchScore(user, route);
        
        // 3. 최종 점수 계산
        BigDecimal tagWeighted = tagMatchScore.multiply(TAG_WEIGHT);
        BigDecimal levelWeighted = levelMatchScore.multiply(LEVEL_WEIGHT);
        BigDecimal finalScore = tagWeighted.add(levelWeighted);
        
        return finalScore.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 태그 매칭 점수 계산
     */
    private BigDecimal calculateTagMatchScore(Route route, List<UserPreferredTag> preferences) {
        // 루트의 태그 조회
        List<RouteTag> routeTags = routeTagRepository
            .findByRouteIdOrderByRelevanceScoreDesc(route.getRouteId());
        
        if (routeTags.isEmpty() || preferences.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (UserPreferredTag preference : preferences) {
            // 해당 태그가 루트에 있는지 확인
            RouteTag matchingRouteTag = routeTags.stream()
                .filter(rt -> rt.getTag().getTagId().equals(preference.getTag().getTagId()))
                .findFirst()
                .orElse(null);

            if (matchingRouteTag != null) {
                // 선호도 점수 * 루트 태그 연관도 점수
                BigDecimal preferenceScore = getPreferenceScore(preference.getPreferenceLevel());
                BigDecimal relevanceScore = matchingRouteTag.getRelevanceScore();
                BigDecimal matchScore = preferenceScore.multiply(relevanceScore);
                
                totalScore = totalScore.add(matchScore);
                totalWeight = totalWeight.add(preferenceScore);
            }
        }

        // 가중 평균으로 최종 태그 매칭 점수 계산
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            return totalScore.divide(totalWeight, 3, RoundingMode.HALF_UP);
        }
        
        return BigDecimal.ZERO;
    }

    /**
     * 레벨 매칭 점수 계산
     */
    private BigDecimal calculateLevelMatchScore(User user, Route route) {
        Integer userLevel = getUserClimbingLevel(user.getUserId());
        Integer routeLevel = route.getLevel().getDifficultyScore();
        
        if (userLevel == null || routeLevel == null) {
            return new BigDecimal("0.5"); // 기본 점수
        }

        int levelDifference = Math.abs(userLevel - routeLevel);
        
        // 레벨 차이에 따른 점수 계산
        if (levelDifference == 0) {
            return BigDecimal.ONE;
        } else if (levelDifference == 1) {
            return new BigDecimal("0.8");
        } else if (levelDifference == 2) {
            return new BigDecimal("0.6");
        } else if (levelDifference == 3) {
            return new BigDecimal("0.4");
        } else {
            return new BigDecimal("0.2");
        }
    }

    /**
     * 사용자 클라이밍 레벨 조회
     */
    private Integer getUserClimbingLevel(Long userId) {
        // UserClimb 엔티티에서 최근 성공한 루트들의 평균 난이도 계산
        return 3; // 임시 구현
    }

    /**
     * 선호도 레벨을 점수로 변환
     */
    private BigDecimal getPreferenceScore(PreferenceLevel level) {
        switch (level) {
            case HIGH:
                return BigDecimal.valueOf(1.0);
            case MEDIUM:
                return BigDecimal.valueOf(0.6);
            case LOW:
                return BigDecimal.valueOf(0.3);
            default:
                return BigDecimal.ZERO;
        }
    }
    
    /**
     * 추천 엔티티 생성
     * @param user 사용자
     * @param route 루트  
     * @param score 추천 점수
     * @param preferences 선호 태그
     * @return UserRouteRecommendation
     */
    private UserRouteRecommendation createRecommendation(User user, Route route,
                                                        BigDecimal score,
                                                        List<UserPreferredTag> preferences) {
        // 태그 매칭 점수와 레벨 매칭 점수 분리 계산
        BigDecimal tagScore = calculateTagMatchScore(route, preferences);
        BigDecimal levelScore = calculateLevelMatchScore(user, route);
        
        return UserRouteRecommendation.builder()
            .user(user)
            .route(route)
            .recommendationScore(score)
            .tagMatchScore(tagScore)
            .levelMatchScore(levelScore)
            .calculatedAt(LocalDateTime.now())
            .isActive(true)
            .build();
    }
    
    /**
     * 프로시저 기반 추천 계산 (대량 처리)
     * @param userId 사용자 ID (-1이면 전체)
     */
    @Transactional
    public void callRecommendationProcedure(Long userId) {
        log.info("Calling CalculateUserRouteRecommendations procedure for user {}", userId);
        
        try {
            jdbcTemplate.update("CALL CalculateUserRouteRecommendations(?)", userId);
            log.info("Procedure executed successfully for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to execute recommendation procedure: {}", e.getMessage());
            throw new RuntimeException("추천 계산 프로시저 실행 실패", e);
        }
    }