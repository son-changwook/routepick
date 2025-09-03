# Step 6-3d2: RecommendationService 구현 - 배치 처리 및 분석

> 추천 시스템 배치 처리, 스케줄링, 통계 분석, 프로시저 연동  
> 생성일: 2025-08-22  
> 단계: 6-3d2 (Service 레이어 - 추천 시스템 확장)  
> 연관: step6-3d1_recommendation_algorithm_core.md

---

## 🎯 설계 목표

- **배치 처리**: 대량 사용자 추천 계산 스케줄링 (매일 새벽 2시)
- **프로시저 연동**: MySQL 저장 프로시저를 활용한 고성능 계산
- **통계 분석**: 추천 성과 및 사용자 패턴 분석
- **시스템 설정**: 비동기 처리 및 스케줄러 최적화
- **모니터링**: 추천 품질 및 성능 지표 추적

---

## 📊 RecommendationService - 배치 처리 및 분석 확장

### RecommendationService.java (Part 2 - 배치 및 분석)
```java
// 앞의 import 구문들은 step6-3d1과 동일
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;

/**
 * 추천 시스템 배치 처리 및 분석 확장 서비스
 * 
 * 확장 기능:
 * - 배치 추천 계산 스케줄링
 * - MySQL 프로시저 연동
 * - 추천 통계 및 분석
 * - 성능 모니터링
 */
public class RecommendationService {
    // ... 기본 필드들은 step6-3d1과 동일 ...
    
    private final JdbcTemplate jdbcTemplate;

    // ===== 배치 처리 시스템 =====

    /**
     * 배치 추천 계산 (매일 새벽 2시)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void batchCalculateRecommendations() {
        log.info("Starting batch recommendation calculation");
        
        LocalDateTime batchStartTime = LocalDateTime.now();
        
        // 활성 사용자 목록 조회 (30일 내 활동)
        List<Long> activeUserIds = userRepository.findActiveUserIds(
            LocalDateTime.now().minusDays(30)
        );
        
        log.info("Found {} active users for batch recommendation", activeUserIds.size());
        
        if (activeUserIds.isEmpty()) {
            log.info("No active users found for batch processing");
            return;
        }
        
        // 사용자를 그룹으로 나누어 처리 (메모리 효율성)
        int batchSize = 100;
        int totalBatches = (int) Math.ceil((double) activeUserIds.size() / batchSize);
        
        List<CompletableFuture<BatchResult>> batchFutures = new ArrayList<>();
        
        for (int i = 0; i < totalBatches; i++) {
            int startIndex = i * batchSize;
            int endIndex = Math.min(startIndex + batchSize, activeUserIds.size());
            
            List<Long> batchUserIds = activeUserIds.subList(startIndex, endIndex);
            
            CompletableFuture<BatchResult> batchFuture = processBatchUsers(batchUserIds, i + 1);
            batchFutures.add(batchFuture);
        }
        
        // 모든 배치 작업 완료 대기 및 결과 집계
        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
            .thenAccept(v -> {
                BatchSummary summary = aggregateBatchResults(batchFutures, batchStartTime);
                logBatchSummary(summary);
                
                // 배치 완료 이벤트 발행
                // applicationEventPublisher.publishEvent(new BatchRecommendationCompletedEvent(summary));
            });
    }

    /**
     * 사용자 그룹 배치 처리
     */
    @Async
    private CompletableFuture<BatchResult> processBatchUsers(List<Long> userIds, int batchNumber) {
        log.info("Processing batch {} with {} users", batchNumber, userIds.size());
        
        LocalDateTime startTime = LocalDateTime.now();
        int successCount = 0;
        int failureCount = 0;
        int totalRecommendations = 0;
        
        for (Long userId : userIds) {
            try {
                CompletableFuture<Integer> result = calculateUserRecommendations(userId);
                int recommendations = result.get(); // 동기 대기
                
                successCount++;
                totalRecommendations += recommendations;
                
                // 메모리 확보를 위한 주기적 가비지 컬렉션 힌트
                if (successCount % 50 == 0) {
                    System.gc();
                }
                
            } catch (Exception e) {
                log.error("Failed to process user {} in batch {}: {}", 
                         userId, batchNumber, e.getMessage());
                failureCount++;
            }
        }
        
        LocalDateTime endTime = LocalDateTime.now();
        
        BatchResult result = BatchResult.builder()
            .batchNumber(batchNumber)
            .userCount(userIds.size())
            .successCount(successCount)
            .failureCount(failureCount)
            .totalRecommendations(totalRecommendations)
            .startTime(startTime)
            .endTime(endTime)
            .processingTimeMs(java.time.Duration.between(startTime, endTime).toMillis())
            .build();
        
        log.info("Batch {} completed: {} users, {} success, {} failures, {} recommendations",
                batchNumber, userIds.size(), successCount, failureCount, totalRecommendations);
        
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 배치 결과 집계
     */
    private BatchSummary aggregateBatchResults(List<CompletableFuture<BatchResult>> batchFutures,
                                              LocalDateTime batchStartTime) {
        int totalUsers = 0;
        int totalSuccess = 0;
        int totalFailures = 0;
        int totalRecommendations = 0;
        long maxProcessingTime = 0;
        
        for (CompletableFuture<BatchResult> future : batchFutures) {
            try {
                BatchResult result = future.get();
                totalUsers += result.userCount;
                totalSuccess += result.successCount;
                totalFailures += result.failureCount;
                totalRecommendations += result.totalRecommendations;
                maxProcessingTime = Math.max(maxProcessingTime, result.processingTimeMs);
            } catch (Exception e) {
                log.error("Failed to get batch result: {}", e.getMessage());
            }
        }
        
        return BatchSummary.builder()
            .startTime(batchStartTime)
            .endTime(LocalDateTime.now())
            .totalUsers(totalUsers)
            .successCount(totalSuccess)
            .failureCount(totalFailures)
            .totalRecommendations(totalRecommendations)
            .batchCount(batchFutures.size())
            .maxProcessingTimeMs(maxProcessingTime)
            .totalProcessingTimeMs(java.time.Duration.between(batchStartTime, LocalDateTime.now()).toMillis())
            .build();
    }

    /**
     * 배치 요약 로깅
     */
    private void logBatchSummary(BatchSummary summary) {
        log.info("===== BATCH RECOMMENDATION SUMMARY =====");
        log.info("Total Users: {}", summary.totalUsers);
        log.info("Successful: {}", summary.successCount);
        log.info("Failed: {}", summary.failureCount);
        log.info("Success Rate: {}%", 
                summary.totalUsers > 0 ? (summary.successCount * 100.0 / summary.totalUsers) : 0);
        log.info("Total Recommendations: {}", summary.totalRecommendations);
        log.info("Avg Recommendations per User: {}", 
                summary.successCount > 0 ? (summary.totalRecommendations / summary.successCount) : 0);
        log.info("Total Processing Time: {}ms", summary.totalProcessingTimeMs);
        log.info("Max Batch Processing Time: {}ms", summary.maxProcessingTimeMs);
        log.info("Batch Count: {}", summary.batchCount);
        log.info("==========================================");
    }

    // ===== MySQL 프로시저 연동 =====

    /**
     * 프로시저 기반 추천 계산 (대량 처리)
     */
    @Transactional
    @CacheEvict(value = CACHE_USER_RECOMMENDATIONS, allEntries = true)
    public void callRecommendationProcedure(Long userId) {
        log.info("Calling CalculateUserRouteRecommendations procedure for user {}", userId);
        
        try {
            long startTime = System.currentTimeMillis();
            
            jdbcTemplate.update("CALL CalculateUserRouteRecommendations(?)", userId);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Procedure executed successfully for user {} in {}ms", 
                    userId, processingTime);
                    
        } catch (Exception e) {
            log.error("Failed to execute recommendation procedure for user {}: {}", 
                     userId, e.getMessage());
            throw new RuntimeException("추천 계산 프로시저 실행 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 전체 사용자 프로시저 기반 계산
     */
    @Transactional
    @CacheEvict(value = CACHE_USER_RECOMMENDATIONS, allEntries = true)
    public void callBatchRecommendationProcedure() {
        log.info("Calling batch recommendation procedure for all users");
        
        try {
            long startTime = System.currentTimeMillis();
            
            // -1은 전체 사용자를 의미
            jdbcTemplate.update("CALL CalculateUserRouteRecommendations(?)", -1);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Batch procedure executed successfully in {}ms", processingTime);
            
        } catch (Exception e) {
            log.error("Failed to execute batch recommendation procedure: {}", e.getMessage());
            throw new RuntimeException("배치 추천 계산 프로시저 실행 실패: " + e.getMessage(), e);
        }
    }

    // ===== 추천 통계 및 분석 =====

    /**
     * 추천 통계 조회
     */
    @Cacheable(value = CACHE_RECOMMENDATION_STATS, key = "#userId")
    public RecommendationStats getUserRecommendationStats(Long userId) {
        Long totalCount = recommendationRepository.countByUserId(userId);
        Long activeCount = recommendationRepository.countActiveByUserId(userId);
        BigDecimal avgScore = recommendationRepository.getAverageScore(userId);
        LocalDateTime lastCalculated = recommendationRepository
            .getLastCalculatedTime(userId)
            .orElse(null);
        
        // 태그별 추천 분포
        Map<String, Long> tagDistribution = recommendationRepository
            .getRecommendationTagDistribution(userId);
        
        // 난이도별 추천 분포
        Map<Integer, Long> levelDistribution = recommendationRepository
            .getRecommendationLevelDistribution(userId);
            
        return RecommendationStats.builder()
            .userId(userId)
            .totalRecommendations(totalCount)
            .activeRecommendations(activeCount)
            .averageScore(avgScore)
            .lastCalculated(lastCalculated)
            .tagDistribution(tagDistribution)
            .levelDistribution(levelDistribution)
            .build();
    }

    /**
     * 전체 시스템 추천 통계
     */
    @Cacheable(value = "system-recommendation-stats")
    public SystemRecommendationStats getSystemRecommendationStats() {
        long totalUsers = userRepository.count();
        long usersWithRecommendations = recommendationRepository.countUsersWithRecommendations();
        long totalRecommendations = recommendationRepository.count();
        long activeRecommendations = recommendationRepository.countActiveRecommendations();
        
        BigDecimal avgRecommendationsPerUser = totalUsers > 0 ?
            BigDecimal.valueOf(totalRecommendations).divide(
                BigDecimal.valueOf(totalUsers), 2, RoundingMode.HALF_UP
            ) : BigDecimal.ZERO;
        
        BigDecimal systemAvgScore = recommendationRepository.getSystemAverageScore();
        
        // 품질 분포
        Map<String, Long> qualityDistribution = recommendationRepository.getQualityDistribution();
        
        // 최근 24시간 통계
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        long recentCalculations = recommendationRepository.countCalculatedAfter(last24Hours);
        
        return SystemRecommendationStats.builder()
            .totalUsers(totalUsers)
            .usersWithRecommendations(usersWithRecommendations)
            .coverageRate(totalUsers > 0 ? 
                (double) usersWithRecommendations / totalUsers * 100 : 0.0)
            .totalRecommendations(totalRecommendations)
            .activeRecommendations(activeRecommendations)
            .avgRecommendationsPerUser(avgRecommendationsPerUser)
            .systemAverageScore(systemAvgScore)
            .qualityDistribution(qualityDistribution)
            .recentCalculationsLast24h(recentCalculations)
            .build();
    }

    /**
     * 추천 성과 분석
     */
    @Cacheable(value = "recommendation-performance", key = "#days")
    public RecommendationPerformanceDto getRecommendationPerformance(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        // 추천 대비 실제 클라이밍 비율
        long recommendedRoutes = recommendationRepository.countRecommendationsAfter(startDate);
        long climbedRecommendedRoutes = recommendationRepository.countClimbedRecommendationsAfter(startDate);
        
        double clickThroughRate = recommendedRoutes > 0 ? 
            (double) climbedRecommendedRoutes / recommendedRoutes * 100 : 0.0;
        
        // 평균 피드백 점수
        BigDecimal avgFeedbackScore = recommendationRepository.getAverageFeedbackScore(startDate);
        
        // 일별 추천 트렌드
        List<DailyRecommendationTrend> dailyTrends = recommendationRepository
            .getDailyRecommendationTrends(startDate);
        
        return RecommendationPerformanceDto.builder()
            .analysisStartDate(startDate)
            .analysisEndDate(LocalDateTime.now())
            .totalRecommendations(recommendedRoutes)
            .climbedRecommendations(climbedRecommendedRoutes)
            .clickThroughRate(clickThroughRate)
            .averageFeedbackScore(avgFeedbackScore)
            .dailyTrends(dailyTrends)
            .build();
    }

    /**
     * 추천 품질 모니터링
     */
    @Scheduled(fixedDelay = 3600000) // 1시간마다
    public void monitorRecommendationQuality() {
        log.info("Starting recommendation quality monitoring");
        
        SystemRecommendationStats stats = getSystemRecommendationStats();
        
        // 커버리지 체크
        if (stats.coverageRate < 80.0) {
            log.warn("Low recommendation coverage: {}%", stats.coverageRate);
        }
        
        // 평균 점수 체크
        if (stats.systemAverageScore.compareTo(new BigDecimal("0.5")) < 0) {
            log.warn("Low system average score: {}", stats.systemAverageScore);
        }
        
        // 최근 계산 활동 체크
        if (stats.recentCalculationsLast24h < stats.totalUsers * 0.1) {
            log.warn("Low recent calculation activity: {} calculations in last 24h", 
                    stats.recentCalculationsLast24h);
        }
        
        log.info("Quality monitoring completed - Coverage: {}%, Avg Score: {}", 
                stats.coverageRate, stats.systemAverageScore);
    }

    // ===== DTO 클래스 =====

    /**
     * 배치 처리 결과 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class BatchResult {
        private final int batchNumber;
        private final int userCount;
        private final int successCount;
        private final int failureCount;
        private final int totalRecommendations;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final long processingTimeMs;
    }

    /**
     * 배치 처리 요약 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class BatchSummary {
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final int totalUsers;
        private final int successCount;
        private final int failureCount;
        private final int totalRecommendations;
        private final int batchCount;
        private final long maxProcessingTimeMs;
        private final long totalProcessingTimeMs;
    }

    /**
     * 추천 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class RecommendationStats {
        private final Long userId;
        private final Long totalRecommendations;
        private final Long activeRecommendations;
        private final BigDecimal averageScore;
        private final LocalDateTime lastCalculated;
        private final Map<String, Long> tagDistribution;
        private final Map<Integer, Long> levelDistribution;
    }

    /**
     * 시스템 추천 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class SystemRecommendationStats {
        private final long totalUsers;
        private final long usersWithRecommendations;
        private final double coverageRate;
        private final long totalRecommendations;
        private final long activeRecommendations;
        private final BigDecimal avgRecommendationsPerUser;
        private final BigDecimal systemAverageScore;
        private final Map<String, Long> qualityDistribution;
        private final long recentCalculationsLast24h;
    }

    /**
     * 추천 성과 분석 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class RecommendationPerformanceDto {
        private final LocalDateTime analysisStartDate;
        private final LocalDateTime analysisEndDate;
        private final long totalRecommendations;
        private final long climbedRecommendations;
        private final double clickThroughRate;
        private final BigDecimal averageFeedbackScore;
        private final List<DailyRecommendationTrend> dailyTrends;
    }

    /**
     * 일별 추천 트렌드 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class DailyRecommendationTrend {
        private final LocalDate date;
        private final long recommendationsGenerated;
        private final long recommendationsClimbed;
        private final BigDecimal averageScore;
        private final double clickThroughRate;
    }
}
```

---

## 🔧 시스템 설정 및 통합

### application.yml 설정
```yaml
# 추천 시스템 설정
app:
  recommendation:
    cache-ttl: 1h  # 추천 캐시 TTL
    batch:
      enabled: true
      size: 100  # 배치 처리 크기
      cron: "0 0 2 * * ?"  # 매일 새벽 2시
    thresholds:
      min-score: 0.3  # 최소 추천 점수
      min-tag-matches: 2  # 최소 태그 매칭 수
      max-results: 20  # 최대 추천 결과 수
    weights:
      tag-match: 0.7  # 태그 매칭 가중치
      level-match: 0.3  # 레벨 매칭 가중치
    monitoring:
      quality-check-interval: 1h
      min-coverage-rate: 80.0
      min-avg-score: 0.5

# Spring Task 설정
spring:
  task:
    scheduling:
      pool:
        size: 5
    execution:
      pool:
        core-size: 10
        max-size: 20
        queue-capacity: 500
```

### 스케줄러 및 비동기 설정
```java
@Configuration
@EnableScheduling
@EnableAsync
public class RecommendationConfig {
    
    @Bean
    @Primary
    public TaskScheduler recommendationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("recommendation-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.initialize();
        return scheduler;
    }
    
    @Bean
    @Qualifier("recommendationAsyncExecutor")
    public Executor recommendationAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-recommendation-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
```

---

## 📊 배치 처리 시스템

### ⏰ **1. 스케줄링 배치**
- **실행 시간**: 매일 새벽 2시 (사용자 활동 최소 시간대)
- **대상 선정**: 최근 30일 내 활동한 사용자만 처리
- **배치 크기**: 100명씩 그룹화하여 메모리 효율성 확보
- **병렬 처리**: CompletableFuture로 동시 처리

### 🔄 **2. 배치 최적화**
- **메모리 관리**: 50명 처리마다 GC 힌트 제공
- **실패 처리**: 개별 사용자 실패가 전체에 영향 없음
- **진행 추적**: 배치별 상세 로그 및 통계 제공
- **결과 집계**: 전체 배치 완료 후 요약 정보 생성

### 📈 **3. 성과 측정**
- **성공률**: 처리 성공/실패 비율 추적
- **처리 시간**: 배치별, 전체 처리 시간 모니터링
- **추천 품질**: 생성된 추천 수, 평균 점수 분석
- **시스템 부하**: 최대 처리 시간, 리소스 사용량 추적

---

## 🗃️ MySQL 프로시저 연동

### 📦 **1. 프로시저 호출**
- **개별 사용자**: `CalculateUserRouteRecommendations(userId)`
- **전체 사용자**: `CalculateUserRouteRecommendations(-1)`
- **트랜잭션 관리**: Spring의 @Transactional로 원자성 보장
- **예외 처리**: 프로시저 실행 실패 시 명확한 에러 메시지

### ⚡ **2. 성능 최적화**
- **대량 처리**: 프로시저는 순수 SQL로 최적화된 대량 처리
- **캐시 무효화**: 프로시저 실행 후 관련 캐시 전체 무효화
- **실행 시간 측정**: 프로시저 실행 시간 모니터링
- **병행 실행**: Java 로직과 프로시저 선택적 사용

### 🔧 **3. 통합 전략**
- **하이브리드 접근**: 실시간은 Java, 배치는 프로시저
- **데이터 일관성**: 양쪽 로직 결과의 일관성 보장
- **백업 시스템**: 프로시저 실패 시 Java 로직으로 대체
- **점진적 전환**: 트래픽에 따라 처리 방식 동적 선택

---

## 📈 통계 분석 시스템

### 📊 **1. 사용자별 통계**
- **추천 현황**: 총 추천 수, 활성 추천 수, 평균 점수
- **분포 분석**: 태그별, 난이도별 추천 분포
- **마지막 계산**: 최근 추천 계산 시점 추적
- **개인화 품질**: 개별 사용자의 추천 품질 평가

### 🌐 **2. 시스템 전체 통계**
- **커버리지**: 추천을 받은 사용자 비율
- **평균 성과**: 시스템 전체 평균 추천 점수
- **품질 분포**: 우수/양호/보통/불량 추천 분포
- **활동 지표**: 최근 24시간 내 추천 계산 활동

### 📉 **3. 성과 분석**
- **클릭률**: 추천 대비 실제 등반 비율
- **피드백 점수**: 사용자 피드백 기반 만족도
- **일별 트렌드**: 추천 생성 및 클릭 추이
- **개선 지표**: 시간에 따른 추천 품질 변화

---

## 🔍 모니터링 시스템

### ⚠️ **1. 품질 모니터링 (1시간마다)**
- **커버리지 체크**: 80% 미만 시 경고
- **평균 점수 체크**: 0.5 미만 시 경고  
- **계산 활동 체크**: 24시간 내 활동 10% 미만 시 경고
- **자동 알림**: 임계값 초과 시 관리자 알림

### 📈 **2. 성능 지표**
- **처리 시간**: 개별/배치 처리 시간 추적
- **메모리 사용량**: 배치 처리 중 메모리 모니터링
- **동시성**: 비동기 작업 큐 상태 모니터링
- **DB 부하**: 프로시저 실행이 DB에 미치는 영향

### 📝 **3. 로그 체계**
- **구조화된 로그**: JSON 형태 구조화된 로그
- **레벨별 로그**: DEBUG, INFO, WARN, ERROR 적절히 분리
- **성과 로그**: 배치 요약, 품질 지표 별도 로그
- **추적 가능**: 사용자별, 배치별 추적 가능한 로그

---

## 🚀 활용 시나리오

### ⏰ **운영 최적화**
- 새벽 시간대 배치로 서비스 영향 최소화
- 실시간 추천과 배치 추천의 하이브리드 운영
- 프로시저 활용으로 DB 서버 자원 최적 활용

### 📊 **데이터 드리븐**
- 추천 성과 데이터 기반 알고리즘 개선
- A/B 테스트를 위한 상세 통계 제공
- 사용자 행동 패턴 분석으로 서비스 개선

### 🔧 **시스템 운영**
- 자동화된 품질 모니터링으로 안정성 확보
- 확장 가능한 배치 처리로 사용자 증가 대응
- 상세한 로그와 통계로 문제 진단 및 해결

*step6-3d2 완성: 추천 시스템 배치 처리 및 분석 구현 완료*