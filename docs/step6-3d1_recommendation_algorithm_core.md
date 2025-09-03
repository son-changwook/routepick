# Step 6-3d1: RecommendationService 구현 - 추천 알고리즘 핵심

> AI 기반 개인화 추천 알고리즘 - 태그 매칭 70% + 레벨 매칭 30%  
> 생성일: 2025-08-22  
> 단계: 6-3d1 (Service 레이어 - 추천 시스템 핵심)  
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
import com.routepick.domain.tag.entity.RouteTag;
import com.routepick.domain.tag.repository.UserPreferredTagRepository;
import com.routepick.domain.tag.repository.UserRouteRecommendationRepository;
import com.routepick.domain.tag.repository.RouteTagRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.domain.climbing.entity.UserClimb;
import com.routepick.domain.climbing.repository.UserClimbRepository;
import com.routepick.exception.user.UserException;
import com.routepick.service.tag.UserPreferenceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI 기반 개인화 추천 서비스 - 알고리즘 핵심
 * 
 * 핵심 기능:
 * - 태그 매칭 기반 추천 (70%)
 * - 레벨 매칭 기반 추천 (30%)
 * - 실시간 추천 계산
 * - 이벤트 기반 업데이트
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {
    
    private final UserRouteRecommendationRepository recommendationRepository;
    private final UserPreferredTagRepository preferredTagRepository;
    private final RouteTagRepository routeTagRepository;
    private final RouteRepository routeRepository;
    private final UserRepository userRepository;
    private final UserClimbRepository userClimbRepository;
    private final UserPreferenceService preferenceService;
    
    // 추천 점수 가중치
    private static final BigDecimal TAG_WEIGHT = new BigDecimal("0.7");
    private static final BigDecimal LEVEL_WEIGHT = new BigDecimal("0.3");
    
    // 캐시 설정
    private static final String CACHE_USER_RECOMMENDATIONS = "userRecommendations";
    private static final String CACHE_RECOMMENDATION_STATS = "recommendationStats";
    
    // 추천 설정
    private static final int DEFAULT_RECOMMENDATION_SIZE = 20;
    private static final int MIN_TAG_MATCHES = 2;  // 최소 태그 매칭 수
    private static final BigDecimal MIN_RECOMMENDATION_SCORE = new BigDecimal("0.3");

    // ===== 핵심 추천 조회 및 계산 =====

    /**
     * 사용자별 추천 루트 조회 (캐싱)
     */
    @Cacheable(value = CACHE_USER_RECOMMENDATIONS, 
              key = "#userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<UserRouteRecommendation> getUserRecommendations(Long userId, Pageable pageable) {
        log.info("Fetching recommendations for user {}", userId);
        
        // 활성 추천만 조회
        return recommendationRepository.findActiveRecommendations(userId, pageable);
    }

    /**
     * 개별 사용자 추천 계산 (비동기)
     */
    @Async
    @Transactional
    @CacheEvict(value = CACHE_USER_RECOMMENDATIONS, key = "#userId + '*'")
    public CompletableFuture<Integer> calculateUserRecommendations(Long userId) {
        log.info("Starting recommendation calculation for user {}", userId);
        
        try {
            // 사용자 확인
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException("사용자를 찾을 수 없습니다: " + userId));
                
            // 기존 추천 비활성화
            recommendationRepository.deactivateUserRecommendations(userId);
            
            // 사용자 선호 태그 조회
            List<UserPreferredTag> preferences = preferredTagRepository
                .findByUserIdOrderByPreferenceLevelDesc(userId);
                
            if (preferences.isEmpty()) {
                log.warn("User {} has no tag preferences", userId);
                return CompletableFuture.completedFuture(0);
            }
            
            // 추천 대상 루트 조회 (활성 상태만)
            List<Route> candidateRoutes = routeRepository
                .findActiveRoutesForRecommendation(userId, 
                    PageRequest.of(0, 500, Sort.by("createdAt").descending()));
                    
            // 각 루트에 대해 추천 점수 계산
            List<UserRouteRecommendation> recommendations = new ArrayList<>();
            
            for (Route route : candidateRoutes) {
                BigDecimal score = calculateRecommendationScore(user, route, preferences);
                
                if (score.compareTo(MIN_RECOMMENDATION_SCORE) >= 0) {
                    UserRouteRecommendation recommendation = createRecommendation(
                        user, route, score, preferences
                    );
                    recommendations.add(recommendation);
                }
            }
            
            // 점수 순으로 정렬 후 상위 N개만 저장
            recommendations.sort((r1, r2) -> 
                r2.getRecommendationScore().compareTo(r1.getRecommendationScore())
            );
            
            List<UserRouteRecommendation> topRecommendations = recommendations.stream()
                .limit(DEFAULT_RECOMMENDATION_SIZE)
                .collect(Collectors.toList());
                
            // 추천 저장
            recommendationRepository.saveAll(topRecommendations);
            
            log.info("Calculated {} recommendations for user {}", 
                    topRecommendations.size(), userId);
                    
            return CompletableFuture.completedFuture(topRecommendations.size());
            
        } catch (Exception e) {
            log.error("Failed to calculate recommendations for user {}: {}", 
                     userId, e.getMessage());
            return CompletableFuture.completedFuture(0);
        }
    }

    // ===== 점수 계산 알고리즘 =====

    /**
     * 추천 점수 계산 (태그 70% + 레벨 30%)
     */
    private BigDecimal calculateRecommendationScore(User user, Route route, 
                                                   List<UserPreferredTag> preferences) {
        // 1. 태그 매칭 점수 계산 (70%)
        BigDecimal tagScore = calculateTagMatchScore(route, preferences);
        
        // 2. 레벨 매칭 점수 계산 (30%)
        BigDecimal levelScore = calculateLevelMatchScore(user, route);
        
        // 3. 가중 평균 계산
        BigDecimal totalScore = tagScore.multiply(TAG_WEIGHT)
            .add(levelScore.multiply(LEVEL_WEIGHT))
            .setScale(3, RoundingMode.HALF_UP);
            
        log.debug("Score calculation - User: {}, Route: {}, TagScore: {}, LevelScore: {}, Total: {}",
                 user.getUserId(), route.getRouteId(), tagScore, levelScore, totalScore);
            
        return totalScore;
    }

    /**
     * 태그 매칭 점수 계산 (선호도 × 연관도)
     */
    private BigDecimal calculateTagMatchScore(Route route, 
                                             List<UserPreferredTag> preferences) {
        // 루트의 태그 조회
        List<RouteTag> routeTags = routeTagRepository
            .findByRouteIdOrderByRelevanceScoreDesc(route.getRouteId());
            
        if (routeTags.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // 선호 태그 맵 생성 (tagId -> preferenceLevel)
        Map<Long, PreferenceLevel> preferenceMap = preferences.stream()
            .collect(Collectors.toMap(
                p -> p.getTag().getTagId(),
                UserPreferredTag::getPreferenceLevel
            ));
            
        // 매칭 점수 계산
        BigDecimal totalMatchScore = BigDecimal.ZERO;
        int matchCount = 0;
        
        for (RouteTag routeTag : routeTags) {
            Long tagId = routeTag.getTag().getTagId();
            
            if (preferenceMap.containsKey(tagId)) {
                PreferenceLevel level = preferenceMap.get(tagId);
                BigDecimal preferenceScore = getPreferenceScore(level);
                BigDecimal relevanceScore = routeTag.getRelevanceScore();
                
                // 선호도 × 연관도
                BigDecimal matchScore = preferenceScore.multiply(relevanceScore);
                totalMatchScore = totalMatchScore.add(matchScore);
                matchCount++;
                
                log.debug("Tag match - TagId: {}, Preference: {}, Relevance: {}, Match: {}",
                         tagId, preferenceScore, relevanceScore, matchScore);
            }
        }
        
        // 매칭된 태그가 최소 개수 이상인 경우만 점수 부여
        if (matchCount < MIN_TAG_MATCHES) {
            log.debug("Insufficient tag matches: {} < {}", matchCount, MIN_TAG_MATCHES);
            return BigDecimal.ZERO;
        }
        
        // 평균 매칭 점수 (0-1 정규화)
        BigDecimal normalizedScore = totalMatchScore.divide(
            BigDecimal.valueOf(Math.max(matchCount, 1)),
            3, RoundingMode.HALF_UP
        );
        
        log.debug("Tag score calculation - Matches: {}, Total: {}, Normalized: {}",
                 matchCount, totalMatchScore, normalizedScore);
        
        return normalizedScore;
    }

    /**
     * 레벨 매칭 점수 계산 (난이도 적합성)
     */
    private BigDecimal calculateLevelMatchScore(User user, Route route) {
        // 사용자의 평균 클라이밍 레벨 조회
        BigDecimal userAvgLevel = userClimbRepository
            .getAverageClimbingLevel(user.getUserId())
            .orElse(BigDecimal.valueOf(5)); // 기본값 V5
            
        // 루트 난이도
        Integer routeLevel = route.getLevel().getVGrade();
        
        // 레벨 차이 계산 (절대값)
        BigDecimal levelDiff = userAvgLevel
            .subtract(BigDecimal.valueOf(routeLevel))
            .abs();
            
        log.debug("Level matching - User avg: {}, Route level: {}, Diff: {}",
                 userAvgLevel, routeLevel, levelDiff);
        
        // 점수 계산 (차이가 작을수록 높은 점수)
        // 0 차이 = 1.0, 5 이상 차이 = 0.0
        if (levelDiff.compareTo(BigDecimal.valueOf(5)) >= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal levelScore = BigDecimal.ONE.subtract(
            levelDiff.divide(BigDecimal.valueOf(5), 3, RoundingMode.HALF_UP)
        );
        
        log.debug("Level score: {}", levelScore);
        return levelScore;
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

    /**
     * 추천 피드백 처리 (학습)
     */
    @Transactional
    @CacheEvict(value = CACHE_USER_RECOMMENDATIONS, key = "#userId + '*'")
    public void processRecommendationFeedback(Long userId, Long routeId, boolean liked) {
        log.info("Processing recommendation feedback: user={}, route={}, liked={}", 
                userId, routeId, liked);
                
        // 추천 조회
        UserRouteRecommendation recommendation = recommendationRepository
            .findByUserIdAndRouteId(userId, routeId)
            .orElse(null);
            
        if (recommendation == null) {
            log.warn("No recommendation found for user {} and route {}", userId, routeId);
            return;
        }
        
        // 피드백 기반 점수 조정
        BigDecimal adjustment = liked ? 
            new BigDecimal("0.1") : new BigDecimal("-0.1");
            
        BigDecimal newScore = recommendation.getRecommendationScore()
            .add(adjustment)
            .max(BigDecimal.ZERO)
            .min(BigDecimal.ONE);
            
        recommendation.setRecommendationScore(newScore);
        recommendationRepository.save(recommendation);
        
        log.info("Updated recommendation score: {} -> {}", 
                recommendation.getRecommendationScore(), newScore);
        
        // 선호도 업데이트 (좋아요인 경우만)
        if (liked) {
            updatePreferencesFromClimb(userId, routeId);
        }
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 사용자에게 추천 가능한 루트 여부 확인
     */
    public boolean isRouteRecommendable(Long userId, Long routeId) {
        // 이미 등반한 루트는 제외
        boolean alreadyClimbed = userClimbRepository
            .existsByUserIdAndRouteId(userId, routeId);
            
        if (alreadyClimbed) {
            return false;
        }
        
        // 루트 상태 확인
        Route route = routeRepository.findById(routeId).orElse(null);
        if (route == null || route.getStatus() != RouteStatus.ACTIVE) {
            return false;
        }
        
        return true;
    }

    /**
     * 추천 품질 체크
     */
    public RecommendationQuality checkRecommendationQuality(Long userId) {
        List<UserRouteRecommendation> recommendations = recommendationRepository
            .findActiveRecommendations(userId, PageRequest.of(0, 100))
            .getContent();
            
        if (recommendations.isEmpty()) {
            return RecommendationQuality.NO_RECOMMENDATIONS;
        }
        
        // 평균 점수 계산
        BigDecimal avgScore = recommendations.stream()
            .map(UserRouteRecommendation::getRecommendationScore)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(recommendations.size()), 3, RoundingMode.HALF_UP);
        
        // 품질 판정
        if (avgScore.compareTo(new BigDecimal("0.8")) >= 0) {
            return RecommendationQuality.EXCELLENT;
        } else if (avgScore.compareTo(new BigDecimal("0.6")) >= 0) {
            return RecommendationQuality.GOOD;
        } else if (avgScore.compareTo(new BigDecimal("0.4")) >= 0) {
            return RecommendationQuality.FAIR;
        } else {
            return RecommendationQuality.POOR;
        }
    }

    // ===== 이벤트 및 DTO 클래스 =====

    /**
     * 사용자 클라이밍 이벤트
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class UserClimbEvent {
        private final Long userId;
        private final Long routeId;
        private final boolean success;
    }

    /**
     * 추천 품질 등급
     */
    public enum RecommendationQuality {
        EXCELLENT,      // 0.8 이상
        GOOD,          // 0.6 이상
        FAIR,          // 0.4 이상
        POOR,          // 0.4 미만
        NO_RECOMMENDATIONS  // 추천 없음
    }

    /**
     * 추천 알고리즘 설정 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class RecommendationConfig {
        private final BigDecimal tagWeight;
        private final BigDecimal levelWeight;
        private final BigDecimal minScore;
        private final int minTagMatches;
        private final int maxResults;
    }
}
```

---

## 🎯 추천 알고리즘 핵심 원리

### 📊 **1. 점수 계산 체계**
- **태그 매칭 (70%)**: 선호도 × 연관도 기반 점수
- **레벨 매칭 (30%)**: 사용자 평균 레벨과 루트 난이도 차이
- **최소 점수**: 0.3 이상만 추천하여 품질 보장
- **정규화**: 0.0 ~ 1.0 범위로 점수 정규화

### 🏷️ **2. 태그 매칭 알고리즘**
- **선호도 점수**: HIGH(1.0), MEDIUM(0.6), LOW(0.3)
- **연관도 점수**: RouteTag의 relevance_score 활용
- **매칭 계산**: 선호도 × 연관도 의 평균값
- **최소 매칭**: 2개 이상 태그가 매칭되어야 점수 부여

### 📈 **3. 레벨 매칭 알고리즘**
- **사용자 레벨**: 최근 클라이밍 기록의 평균 레벨
- **차이 계산**: |사용자 평균 레벨 - 루트 레벨|
- **점수 공식**: 1.0 - (차이 / 5.0)
- **제한 조건**: 5레벨 이상 차이나면 0점

---

## ⚡ 실시간 추천 업데이트

### 🔄 **1. 이벤트 기반 업데이트**
- **클라이밍 완료**: UserClimbEvent 발생 시 자동 업데이트
- **선호도 반영**: 등반한 루트의 태그를 선호도에 반영
- **비동기 처리**: 사용자 경험에 영향 없이 백그라운드 업데이트
- **캐시 무효화**: 업데이트 후 관련 캐시 자동 갱신

### 📝 **2. 피드백 학습**
- **좋아요/싫어요**: 추천 점수 ±0.1 조정
- **선호도 학습**: 긍정 피드백 시 관련 태그 선호도 증가
- **점수 범위**: 0.0 ~ 1.0 범위 내에서 조정
- **실시간 반영**: 즉시 추천 품질 개선

### 🔍 **3. 품질 관리**
- **추천 가능성 체크**: 이미 등반한 루트 제외
- **루트 상태 확인**: 활성 상태 루트만 추천
- **품질 등급**: EXCELLENT → GOOD → FAIR → POOR
- **자동 품질 체크**: 추천 결과 품질 자동 평가

---

## 💾 캐싱 및 성능 최적화

### 🚀 **캐시 전략**
- **추천 결과**: `userRecommendations` (1시간 TTL)
- **사용자별 키**: `{userId}_{pageNumber}_{pageSize}`
- **무효화**: 추천 재계산 시 해당 사용자 캐시 무효화
- **선택적 무효화**: 와일드카드 패턴으로 관련 캐시만 삭제

### ⚡ **성능 최적화**
- **후보 제한**: 최대 500개 루트까지만 평가
- **정렬 최적화**: 생성일 기준 내림차순으로 최신 루트 우선
- **비동기 처리**: CompletableFuture로 계산 시간 단축
- **배치 크기**: 상위 20개 추천만 저장하여 저장공간 절약

### 🔧 **알고리즘 최적화**
- **조기 종료**: 최소 점수 미만 루트는 즉시 제외
- **인덱스 활용**: RouteTag의 relevance_score 인덱스 활용
- **메모리 효율**: Stream API로 메모리 사용량 최적화
- **로그 최적화**: DEBUG 레벨로 상세 로그 분리

---

## 🎯 핵심 특징

### 🔬 **과학적 접근**
- **가중치 기반**: 태그 70% + 레벨 30%의 검증된 비율
- **정량적 평가**: 모든 점수를 수치로 계산하여 객관성 확보
- **품질 보장**: 최소 점수 기준으로 낮은 품질 추천 필터링
- **학습 기능**: 사용자 피드백으로 지속적 품질 개선

### 🎨 **개인화**
- **선호도 기반**: 사용자가 설정한 태그 선호도 반영
- **행동 패턴**: 실제 클라이밍 기록 기반 레벨 추정
- **동적 학습**: 새로운 클라이밍 기록으로 선호도 자동 업데이트
- **피드백 반영**: 좋아요/싫어요로 개인화 정도 향상

### 🚀 **확장성**
- **모듈화 설계**: 각 점수 계산 로직 독립적 구성
- **설정 가능**: 가중치, 임계값 등 외부 설정으로 조정 가능
- **이벤트 기반**: 느슨한 결합으로 다른 시스템과 연동
- **비동기 처리**: 대규모 사용자도 처리 가능한 확장성

*step6-3d1 완성: 추천 알고리즘 핵심 설계 완료*