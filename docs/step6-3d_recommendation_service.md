# Step 6-3d: RecommendationService 구현

> AI 기반 개인화 추천 서비스 - 태그 매칭 70% + 레벨 매칭 30%
> 생성일: 2025-08-22
> 단계: 6-3d (Service 레이어 - 추천 시스템)
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI 기반 개인화 추천 서비스
 * - 태그 매칭 기반 추천 (70%)
 * - 레벨 매칭 기반 추천 (30%)
 * - 실시간 & 배치 추천 계산
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
    private final JdbcTemplate jdbcTemplate;
    
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
    
    /**
     * 사용자별 추천 루트 조회 (캐싱)
     * @param userId 사용자 ID
     * @param pageable 페이징
     * @return 추천 루트 페이지
     */
    @Cacheable(value = CACHE_USER_RECOMMENDATIONS, 
              key = "#userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<UserRouteRecommendation> getUserRecommendations(Long userId, Pageable pageable) {
        log.info("Fetching recommendations for user {}", userId);
        
        // 활성 추천만 조회
        return recommendationRepository.findActiveRecommendations(userId, pageable);
    }
    
    /**
     * 사용자별 추천 계산 (비동기)
     * @param userId 사용자 ID
     * @return 계산된 추천 수
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
    
    /**
     * 추천 점수 계산
     * @param user 사용자
     * @param route 루트
     * @param preferences 선호 태그 목록
     * @return 추천 점수 (0.0-1.0)
     */
    private BigDecimal calculateRecommendationScore(User user, Route route, 
                                                   List<UserPreferredTag> preferences) {
        // 1. 태그 매칭 점수 계산
        BigDecimal tagScore = calculateTagMatchScore(route, preferences);
        
        // 2. 레벨 매칭 점수 계산
        BigDecimal levelScore = calculateLevelMatchScore(user, route);
        
        // 3. 가중 평균 계산
        BigDecimal totalScore = tagScore.multiply(TAG_WEIGHT)
            .add(levelScore.multiply(LEVEL_WEIGHT))
            .setScale(3, RoundingMode.HALF_UP);
            
        return totalScore;
    }
    
    /**
     * 태그 매칭 점수 계산
     * @param route 루트
     * @param preferences 선호 태그
     * @return 태그 매칭 점수 (0.0-1.0)
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
            }
        }
        
        // 매칭된 태그가 최소 개수 이상인 경우만 점수 부여
        if (matchCount < MIN_TAG_MATCHES) {
            return BigDecimal.ZERO;
        }
        
        // 평균 매칭 점수 (0-1 정규화)
        return totalMatchScore.divide(
            BigDecimal.valueOf(Math.max(matchCount, 1)),
            3, RoundingMode.HALF_UP
        );
    }
    
    /**
     * 레벨 매칭 점수 계산
     * @param user 사용자
     * @param route 루트
     * @return 레벨 매칭 점수 (0.0-1.0)
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
            
        // 점수 계산 (차이가 작을수록 높은 점수)
        // 0 차이 = 1.0, 5 이상 차이 = 0.0
        if (levelDiff.compareTo(BigDecimal.valueOf(5)) >= 0) {
            return BigDecimal.ZERO;
        }
        
        return BigDecimal.ONE.subtract(
            levelDiff.divide(BigDecimal.valueOf(5), 3, RoundingMode.HALF_UP)
        );
    }
    
    /**
     * 선호도 레벨을 점수로 변환
     * @param level 선호도 레벨
     * @return 점수 (0.0-1.0)
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
    
    /**
     * 배치 추천 계산 (매일 새벽 2시)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void batchCalculateRecommendations() {
        log.info("Starting batch recommendation calculation");
        
        // 활성 사용자 목록 조회
        List<Long> activeUserIds = userRepository.findActiveUserIds(
            LocalDateTime.now().minusDays(30)
        );
        
        log.info("Found {} active users for batch recommendation", activeUserIds.size());
        
        // 각 사용자별 추천 계산 (비동기)
        List<CompletableFuture<Integer>> futures = activeUserIds.stream()
            .map(this::calculateUserRecommendations)
            .collect(Collectors.toList());
            
        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("Batch recommendation calculation completed"));
    }
    
    /**
     * 사용자 활동 기반 추천 업데이트
     * @param event 사용자 클라이밍 이벤트
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
     * @param userId 사용자 ID
     * @param routeId 루트 ID
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
            }
        }
    }
    
    /**
     * 추천 통계 조회
     * @param userId 사용자 ID
     * @return 추천 통계
     */
    @Cacheable(value = CACHE_RECOMMENDATION_STATS, key = "#userId")
    public RecommendationStats getUserRecommendationStats(Long userId) {
        Long totalCount = recommendationRepository.countByUserId(userId);
        Long activeCount = recommendationRepository.countActiveByUserId(userId);
        BigDecimal avgScore = recommendationRepository.getAverageScore(userId);
        LocalDateTime lastCalculated = recommendationRepository
            .getLastCalculatedTime(userId)
            .orElse(null);
            
        return RecommendationStats.builder()
            .userId(userId)
            .totalRecommendations(totalCount)
            .activeRecommendations(activeCount)
            .averageScore(avgScore)
            .lastCalculated(lastCalculated)
            .build();
    }
    
    /**
     * 추천 피드백 처리
     * @param userId 사용자 ID
     * @param routeId 루트 ID
     * @param liked 좋아요 여부
     */
    @Transactional
    public void processRecommendationFeedback(Long userId, Long routeId, boolean liked) {
        log.info("Processing recommendation feedback: user={}, route={}, liked={}", 
                userId, routeId, liked);
                
        // 추천 조회
        UserRouteRecommendation recommendation = recommendationRepository
            .findByUserIdAndRouteId(userId, routeId)
            .orElse(null);
            
        if (recommendation == null) {
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
        
        // 선호도 업데이트
        if (liked) {
            updatePreferencesFromClimb(userId, routeId);
        }
    }
    
    // 이벤트 클래스
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class UserClimbEvent {
        private final Long userId;
        private final Long routeId;
        private final boolean success;
    }
    
    // 통계 DTO
    @lombok.Builder
    @lombok.Getter
    public static class RecommendationStats {
        private final Long userId;
        private final Long totalRecommendations;
        private final Long activeRecommendations;
        private final BigDecimal averageScore;
        private final LocalDateTime lastCalculated;
    }
}
```

---

## 🔧 설정 및 통합

### application.yml 추가 설정
```yaml
# 추천 시스템 설정
app:
  recommendation:
    cache-ttl: 1h  # 추천 캐시 TTL
    batch-size: 500  # 배치 처리 크기
    min-score: 0.3  # 최소 추천 점수
    max-results: 20  # 최대 추천 결과 수
    weights:
      tag-match: 0.7  # 태그 매칭 가중치
      level-match: 0.3  # 레벨 매칭 가중치
    schedule:
      enabled: true
      cron: "0 0 2 * * ?"  # 매일 새벽 2시
```

### 스케줄러 설정
```java
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
    
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("recommendation-");
        scheduler.initialize();
        return scheduler;
    }
    
    @Bean
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-recommendation-");
        executor.initialize();
        return executor;
    }
}
```

---

## 📊 주요 기능 요약

### 1. 추천 점수 계산
- **태그 매칭**: 70% 가중치, 선호도 × 연관도
- **레벨 매칭**: 30% 가중치, 난이도 적합성
- **최소 점수**: 0.3 이상만 추천

### 2. 실시간 추천
- **사용자 활동 기반**: 클라이밍 기록 반영
- **이벤트 리스너**: 즉시 추천 업데이트
- **피드백 처리**: 좋아요/싫어요 반영

### 3. 배치 처리
- **스케줄링**: 매일 새벽 2시 실행
- **활성 사용자**: 30일 내 활동 사용자 대상
- **비동기 처리**: 대량 계산 최적화

### 4. 캐싱 전략
- **추천 결과**: 1시간 TTL
- **통계 정보**: 주기적 갱신
- **캐시 무효화**: 재계산시 자동

### 5. 프로시저 연동
- **CalculateUserRouteRecommendations**: MySQL 프로시저 호출
- **대량 처리**: 전체 사용자 일괄 계산
- **트랜잭션 관리**: 원자성 보장

---

## ✅ 완료 사항
- ✅ 개인화 추천 알고리즘 (태그 70% + 레벨 30%)
- ✅ 실시간 추천 업데이트
- ✅ 배치 추천 계산 (스케줄링)
- ✅ Redis 캐싱 (1시간 TTL)
- ✅ 프로시저 연동
- ✅ 피드백 기반 학습
- ✅ 이벤트 기반 연동
- ✅ 추천 통계 제공

---

*RecommendationService 설계 완료: AI 기반 개인화 추천 시스템*