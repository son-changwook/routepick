# step6-3d2b_event_feedback_stats.md

## 🔄 이벤트 기반 업데이트 및 피드백 시스템

### 배치 추천 계산 및 이벤트 처리

```java
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

*RecommendationService 구현 완료: AI 기반 개인화 추천 시스템*