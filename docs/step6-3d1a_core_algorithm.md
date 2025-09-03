# step6-3d1a_core_algorithm.md

> AI 기반 개인화 추천 알고리즘 - 태그 매칭 70% + 레벨 매칭 30%  
> 생성일: 2025-08-22  
> 단계: 6-3d1a (Service 레이어 - 추천 알고리즘 핵심)  
> 참고: step1-2, step4-2a, step5-2b

---

## 🎯 설계 목표

- **개인화 추천 알고리즘**: 사용자 선호 태그 기반 맞춤 추천
- **점수 체계**: tag_match_score(70%) + level_match_score(30%)
- **실시간 계산**: 사용자 활동 기반 추천 즉시 업데이트
- **캐싱 최적화**: Redis 1시간 TTL, 성능 최적화
- **최소 품질 보장**: 최소 점수 0.3 이상만 추천

---

## 🤖 RecommendationService - 추천 알고리즘 핵심

### RecommendationService.java (Part 1 - 알고리즘 핵심)
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
import com.routepick.service.tag.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 루트 추천 서비스 - 핵심 알고리즘
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
    private final UserPreferenceService preferenceService;

    // 캐시 키 상수
    private static final String CACHE_USER_RECOMMENDATIONS = "user_recommendations";
    private static final String CACHE_ROUTE_RECOMMENDATIONS = "route_recommendations";
    private static final int CACHE_TTL_HOURS = 1;

    // 알고리즘 상수
    private static final BigDecimal TAG_WEIGHT = new BigDecimal("0.7");
    private static final BigDecimal LEVEL_WEIGHT = new BigDecimal("0.3");
    private static final BigDecimal MIN_RECOMMENDATION_SCORE = new BigDecimal("0.3");
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 20;

    // ===== 메인 추천 알고리즘 =====

    /**
     * 사용자별 루트 추천 계산 및 저장
     * 
     * @param userId 사용자 ID
     * @return 계산된 추천 수
     */
    @Transactional
    @CacheEvict(value = CACHE_USER_RECOMMENDATIONS, key = "#userId + '*'")
    public int calculateUserRecommendations(Long userId) {
        log.info("Calculating route recommendations for user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RecommendationException("사용자를 찾을 수 없습니다: " + userId));

        // 사용자 선호 태그 조회
        List<UserPreferredTag> preferences = userPreferredTagRepository
            .findByUserIdAndIsActiveTrue(userId);
            
        if (preferences.isEmpty()) {
            log.warn("No user preferences found for user: {}", userId);
            return 0;
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
        return recommendationCount;
    }

    // ===== 점수 계산 알고리즘 =====

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
     * 
     * @param route 루트
     * @param preferences 사용자 선호 태그 목록
     * @return 태그 매칭 점수 (0.0 ~ 1.0)
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
     * 
     * @param user 사용자
     * @param route 루트
     * @return 레벨 매칭 점수 (0.0 ~ 1.0)
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
            return BigDecimal.ONE; // 완벽한 매칭
        } else if (levelDifference == 1) {
            return new BigDecimal("0.8"); // 1단계 차이
        } else if (levelDifference == 2) {
            return new BigDecimal("0.6"); // 2단계 차이
        } else if (levelDifference == 3) {
            return new BigDecimal("0.4"); // 3단계 차이
        } else {
            return new BigDecimal("0.2"); // 4단계 이상 차이
        }
    }

    /**
     * 사용자 클라이밍 레벨 조회 (최근 성공 기록 기반)
     */
    private Integer getUserClimbingLevel(Long userId) {
        // UserClimb 엔티티에서 최근 성공한 루트들의 평균 난이도 계산
        // 실제 구현에서는 복잡한 로직으로 사용자 실력 추정
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

    // ===== 추천 엔티티 생성 =====

    /**
     * 추천 엔티티 생성 (세부 점수 분리 저장)
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

    // ===== 실시간 업데이트 =====

    /**
     * 사용자 활동 기반 추천 업데이트
     */
    @EventListener
    @Async
    public void onUserClimbEvent(UserClimbEvent event) {
        log.info("Updating recommendations based on user climb: {}", event);
        
        // 클라이밍 기록 기반 선호도 업데이트
        updatePreferencesFromClimb(event.getUserId(), event.getRouteId());
        
        // 추천 재계산 (비동기)
        calculateUserRecommendations(event.getUserId());
    }

    /**
     * 클라이밍 기록 기반 선호도 업데이트
     */
    private void updatePreferencesFromClimb(Long userId, Long routeId) {
        // 루트의 태그 조회
        List<RouteTag> routeTags = routeTagRepository
            .findByRouteIdOrderByRelevanceScoreDesc(routeId);
            
        // 높은 연관도 태그를 선호 태그에 반영
        for (RouteTag routeTag : routeTags) {
            if (routeTag.getRelevanceScore().compareTo(new BigDecimal("0.7")) >= 0) {
                preferenceService.adjustPreferenceLevel(
                    userId, 
                    routeTag.getTag().getTagId(),
                    true // 선호도 증가
                );
                
                log.debug("Updated preference for user {} - tag {}: increased",
                         userId, routeTag.getTag().getTagId());
            }
        }
    }