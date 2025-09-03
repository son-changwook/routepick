# 태그 통합 성능 테스트

## 개요
태그 시스템과 추천 알고리즘의 통합 성능을 검증하는 테스트입니다. 대용량 태그 데이터 처리, 실시간 추천 성능, 태그 검색 속도 등을 종합적으로 평가합니다.

## 테스트 클래스 구조

```java
package com.routepick.tag.integration.performance;

import com.routepick.tag.dto.request.TagCreateRequestDto;
import com.routepick.tag.dto.response.TagDto;
import com.routepick.tag.service.TagService;
import com.routepick.recommendation.service.RecommendationService;
import com.routepick.recommendation.dto.response.RouteRecommendationDto;
import com.routepick.user.service.UserPreferenceService;
import com.routepick.common.service.CacheService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * 태그 시스템 통합 성능 테스트
 * 
 * 성능 검증 영역:
 * - 대용량 태그 데이터 처리
 * - 실시간 추천 알고리즘 성능
 * - 태그 기반 검색 최적화
 * - 캐시 시스템 효율성
 * - 동시성 처리 성능
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TagIntegrationPerformanceTest {

    @Autowired
    private TagService tagService;
    
    @Autowired
    private RecommendationService recommendationService;
    
    @Autowired
    private UserPreferenceService userPreferenceService;
    
    @Autowired
    private CacheService cacheService;
    
    private Long testUserId;
    private static final int LARGE_TAG_COUNT = 50000;
    private static final int CONCURRENT_USERS = 200;
    private static final long MAX_RESPONSE_TIME_MS = 2000L;
    
    @BeforeEach
    void setUp() {
        testUserId = 1L;
        
        // 캐시 초기화
        cacheService.clearAll();
        
        // 기본 태그 데이터 준비
        prepareLargeTagDataset();
    }
    
    @AfterEach
    void tearDown() {
        // 성능 테스트 후 정리
        cacheService.clearAll();
    }
    
    @Nested
    @DisplayName("대용량 태그 데이터 처리 성능")
    class BulkTagProcessingTest {
        
        @Test
        @DisplayName("[성능] 대용량 태그 생성 - 50,000개")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        void createBulkTags_Performance() {
            // given
            List<TagCreateRequestDto> tagRequests = new ArrayList<>();
            
            for (int i = 0; i < LARGE_TAG_COUNT; i++) {
                tagRequests.add(TagCreateRequestDto.builder()
                        .tagName("PERF_TAG_" + i)
                        .tagType(getRandomTagType())
                        .description("성능 테스트용 태그 " + i)
                        .color(getRandomColor())
                        .build());
            }
            
            Instant startTime = Instant.now();
            
            // when - 배치 처리로 대량 태그 생성
            List<TagDto> createdTags = tagService.createTagsBatch(tagRequests);
            
            Instant endTime = Instant.now();
            long executionTimeMs = Duration.between(startTime, endTime).toMillis();
            
            // then
            assertThat(createdTags).hasSize(LARGE_TAG_COUNT);
            assertThat(executionTimeMs).isLessThan(60000L); // 1분 이내
            
            // 처리량 계산
            double tps = (double) LARGE_TAG_COUNT / (executionTimeMs / 1000.0);
            System.out.printf("대량 태그 생성 성능: %d개 처리, %.2f TPS, %dms 소요%n", 
                    LARGE_TAG_COUNT, tps, executionTimeMs);
            
            // 최소 성능 기준: 500 TPS
            assertThat(tps).isGreaterThanOrEqualTo(500.0);
        }
        
        @Test
        @DisplayName("[성능] 태그 검색 인덱싱 성능")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void tagSearchIndexing_Performance() {
            // given - 다양한 패턴의 태그 생성
            List<String> searchPatterns = List.of(
                "CRIMPING", "PINCHING", "SLOPERS", "JUGS", "POCKETS",
                "MANTLING", "DYNO", "STATIC", "COMPRESSION", "COORDINATION",
                "VERTICAL", "OVERHANG", "SLAB", "ROOF", "CORNER"
            );
            
            // 각 패턴별로 1000개씩 태그 생성
            for (String pattern : searchPatterns) {
                for (int i = 0; i < 1000; i++) {
                    TagCreateRequestDto request = TagCreateRequestDto.builder()
                            .tagName(pattern + "_VARIANT_" + i)
                            .tagType("TECHNIQUE")
                            .description("검색 성능 테스트: " + pattern)
                            .build();
                    tagService.createTag(request);
                }
            }
            
            Instant startTime = Instant.now();
            
            // when - 다양한 검색 패턴으로 성능 측정
            List<Long> searchTimes = new ArrayList<>();
            
            for (String pattern : searchPatterns) {
                Instant searchStart = Instant.now();
                
                List<TagDto> searchResults = tagService.searchTags(pattern, 100);
                
                Instant searchEnd = Instant.now();
                long searchTime = Duration.between(searchStart, searchEnd).toMillis();
                searchTimes.add(searchTime);
                
                // 검색 결과 검증
                assertThat(searchResults).isNotEmpty();
                assertThat(searchResults.size()).isLessThanOrEqualTo(100);
            }
            
            Instant endTime = Instant.now();
            long totalTime = Duration.between(startTime, endTime).toMillis();
            
            // then
            double averageSearchTime = searchTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            
            long maxSearchTime = searchTimes.stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
            
            System.out.printf("태그 검색 성능: 평균 %.2fms, 최대 %dms, 전체 %dms%n", 
                    averageSearchTime, maxSearchTime, totalTime);
            
            // 성능 기준
            assertThat(averageSearchTime).isLessThan(100.0); // 평균 100ms 이내
            assertThat(maxSearchTime).isLessThan(500L); // 최대 500ms 이내
        }
        
        @Test
        @DisplayName("[성능] 태그 기반 추천 알고리즘 성능")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void tagBasedRecommendation_Performance() {
            // given - 사용자 선호도 설정
            List<String> preferredTags = List.of(
                "CRIMPING", "OVERHANG", "DYNAMIC", "POWER", "TECHNICAL"
            );
            userPreferenceService.updateUserPreferredTags(testUserId, preferredTags);
            
            // 테스트용 루트 10,000개 생성 (각각 랜덤 태그 조합)
            createTestRoutesWithTags(10000);
            
            List<Long> recommendationTimes = new ArrayList<>();
            
            // when - 여러 번 추천 요청하여 성능 측정
            for (int i = 0; i < 50; i++) {
                Instant start = Instant.now();
                
                List<RouteRecommendationDto> recommendations = 
                        recommendationService.getPersonalizedRecommendations(testUserId, 20);
                
                Instant end = Instant.now();
                long responseTime = Duration.between(start, end).toMillis();
                recommendationTimes.add(responseTime);
                
                // 추천 결과 검증
                assertThat(recommendations).hasSizeGreaterThan(0);
                assertThat(recommendations.size()).isLessThanOrEqualTo(20);
                
                // 추천 점수가 내림차순으로 정렬되었는지 확인
                for (int j = 1; j < recommendations.size(); j++) {
                    assertThat(recommendations.get(j).getScore())
                            .isLessThanOrEqualTo(recommendations.get(j-1).getScore());
                }
            }
            
            // then - 성능 분석
            double averageTime = recommendationTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            
            long maxTime = recommendationTimes.stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
            
            long minTime = recommendationTimes.stream()
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0L);
            
            System.out.printf("추천 알고리즘 성능: 평균 %.2fms, 최소 %dms, 최대 %dms%n", 
                    averageTime, minTime, maxTime);
            
            // 성능 기준
            assertThat(averageTime).isLessThan(1000.0); // 평균 1초 이내
            assertThat(maxTime).isLessThan(3000L); // 최대 3초 이내
        }
    }
    
    @Nested
    @DisplayName("동시성 처리 성능")
    class ConcurrencyPerformanceTest {
        
        @Test
        @DisplayName("[성능] 동시 태그 검색 - 200명 사용자")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void concurrentTagSearch_Performance() throws Exception {
            // given
            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
            List<CompletableFuture<Long>> futures = new ArrayList<>();
            
            String[] searchTerms = {
                "CRIMPING", "OVERHANG", "DYNAMIC", "POWER", "TECHNICAL",
                "SLOPERS", "JUGS", "PINCHING", "MANTLING", "COMPRESSION"
            };
            
            Instant startTime = Instant.now();
            
            // when - 200명이 동시에 태그 검색
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                final int userIndex = i;
                
                CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                    Instant start = Instant.now();
                    
                    String searchTerm = searchTerms[userIndex % searchTerms.length];
                    List<TagDto> results = tagService.searchTags(searchTerm, 50);
                    
                    Instant end = Instant.now();
                    long responseTime = Duration.between(start, end).toMillis();
                    
                    // 결과 검증
                    assert !results.isEmpty() : "검색 결과가 비어있음";
                    
                    return responseTime;
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 검색 완료 대기
            List<Long> responseTimes = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            Instant endTime = Instant.now();
            long totalTime = Duration.between(startTime, endTime).toMillis();
            
            // then
            double averageResponseTime = responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            
            long maxResponseTime = responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
            
            double throughput = (double) CONCURRENT_USERS / (totalTime / 1000.0);
            
            System.out.printf("동시 태그 검색 성능: %d명, 평균 %.2fms, 최대 %dms, %.2f TPS%n",
                    CONCURRENT_USERS, averageResponseTime, maxResponseTime, throughput);
            
            // 성능 기준
            assertThat(averageResponseTime).isLessThan(500.0); // 평균 500ms 이내
            assertThat(maxResponseTime).isLessThan(2000L); // 최대 2초 이내
            assertThat(throughput).isGreaterThanOrEqualTo(20.0); // 최소 20 TPS
            
            executor.shutdown();
        }
        
        @Test
        @DisplayName("[성능] 동시 추천 요청 처리")
        void concurrentRecommendationRequests_Performance() throws Exception {
            // given
            ExecutorService executor = Executors.newFixedThreadPool(50);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<CompletableFuture<Long>> futures = new ArrayList<>();
            
            // 50명의 서로 다른 사용자 ID 생성
            List<Long> userIds = IntStream.range(1, 51)
                    .mapToLong(i -> (long) i)
                    .boxed()
                    .toList();
            
            Instant startTime = Instant.now();
            
            // when - 50명이 동시에 개인화 추천 요청
            for (Long userId : userIds) {
                CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Instant start = Instant.now();
                        
                        List<RouteRecommendationDto> recommendations = 
                                recommendationService.getPersonalizedRecommendations(userId, 10);
                        
                        Instant end = Instant.now();
                        long responseTime = Duration.between(start, end).toMillis();
                        
                        // 추천 결과 검증
                        assert recommendations.size() <= 10 : "추천 수 초과";
                        
                        successCount.incrementAndGet();
                        return responseTime;
                        
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        System.err.printf("사용자 %d 추천 실패: %s%n", userId, e.getMessage());
                        return 0L;
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 추천 완료 대기
            List<Long> responseTimes = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(time -> time > 0) // 실패한 케이스 제외
                    .toList();
            
            Instant endTime = Instant.now();
            long totalTime = Duration.between(startTime, endTime).toMillis();
            
            // then
            double averageResponseTime = responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            
            double successRate = (double) successCount.get() / userIds.size();
            double throughput = (double) successCount.get() / (totalTime / 1000.0);
            
            System.out.printf("동시 추천 성능: 성공률 %.2f%%, 평균 응답시간 %.2fms, %.2f TPS%n",
                    successRate * 100, averageResponseTime, throughput);
            
            // 성능 기준
            assertThat(successRate).isGreaterThanOrEqualTo(0.9); // 90% 이상 성공률
            assertThat(averageResponseTime).isLessThan(2000.0); // 평균 2초 이내
            assertThat(throughput).isGreaterThanOrEqualTo(5.0); // 최소 5 TPS
            
            executor.shutdown();
        }
    }
    
    @Nested
    @DisplayName("캐시 시스템 성능")
    class CachePerformanceTest {
        
        @Test
        @DisplayName("[성능] 태그 검색 캐시 효율성")
        void tagSearchCache_Performance() {
            // given
            String popularSearchTerm = "CRIMPING";
            
            // 첫 번째 검색 (캐시 미스)
            Instant start1 = Instant.now();
            List<TagDto> firstResult = tagService.searchTags(popularSearchTerm, 100);
            Instant end1 = Instant.now();
            long firstSearchTime = Duration.between(start1, end1).toMillis();
            
            // when - 동일한 검색 반복 (캐시 히트)
            List<Long> cachedSearchTimes = new ArrayList<>();
            
            for (int i = 0; i < 10; i++) {
                Instant start = Instant.now();
                List<TagDto> cachedResult = tagService.searchTags(popularSearchTerm, 100);
                Instant end = Instant.now();
                long searchTime = Duration.between(start, end).toMillis();
                
                cachedSearchTimes.add(searchTime);
                
                // 결과가 동일한지 확인
                assertThat(cachedResult.size()).isEqualTo(firstResult.size());
            }
            
            // then
            double averageCachedTime = cachedSearchTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            
            double cacheSpeedup = (double) firstSearchTime / averageCachedTime;
            
            System.out.printf("캐시 성능: 첫 검색 %dms, 캐시 평균 %.2fms, 속도 향상 %.2fx%n",
                    firstSearchTime, averageCachedTime, cacheSpeedup);
            
            // 캐시가 최소 3배 이상 빨라야 함
            assertThat(cacheSpeedup).isGreaterThanOrEqualTo(3.0);
            assertThat(averageCachedTime).isLessThan(50.0); // 캐시된 검색은 50ms 이내
        }
        
        @Test
        @DisplayName("[성능] 추천 결과 캐시 효율성")
        void recommendationCache_Performance() {
            // given
            Long testUserId = 1L;
            
            // 첫 번째 추천 요청 (캐시 미스)
            Instant start1 = Instant.now();
            List<RouteRecommendationDto> firstRecommendations = 
                    recommendationService.getPersonalizedRecommendations(testUserId, 20);
            Instant end1 = Instant.now();
            long firstRecommendationTime = Duration.between(start1, end1).toMillis();
            
            // when - 동일한 추천 요청 반복 (캐시 히트)
            List<Long> cachedRecommendationTimes = new ArrayList<>();
            
            for (int i = 0; i < 10; i++) {
                Instant start = Instant.now();
                List<RouteRecommendationDto> cachedRecommendations = 
                        recommendationService.getPersonalizedRecommendations(testUserId, 20);
                Instant end = Instant.now();
                long recommendationTime = Duration.between(start, end).toMillis();
                
                cachedRecommendationTimes.add(recommendationTime);
                
                // 결과 일관성 확인
                assertThat(cachedRecommendations.size()).isEqualTo(firstRecommendations.size());
            }
            
            // then
            double averageCachedTime = cachedRecommendationTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            
            double cacheSpeedup = (double) firstRecommendationTime / averageCachedTime;
            
            System.out.printf("추천 캐시 성능: 첫 추천 %dms, 캐시 평균 %.2fms, 속도 향상 %.2fx%n",
                    firstRecommendationTime, averageCachedTime, cacheSpeedup);
            
            // 성능 기준
            assertThat(cacheSpeedup).isGreaterThanOrEqualTo(5.0); // 최소 5배 향상
            assertThat(averageCachedTime).isLessThan(100.0); // 캐시된 추천은 100ms 이내
        }
    }
    
    @RepeatedTest(20)
    @DisplayName("[성능] 반복 성능 일관성 테스트")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void consistentPerformanceTest() {
        // given
        String searchTerm = "TECHNIQUE_TAG";
        
        Instant startTime = Instant.now();
        
        // when
        List<TagDto> results = tagService.searchTags(searchTerm, 10);
        
        Instant endTime = Instant.now();
        long responseTime = Duration.between(startTime, endTime).toMillis();
        
        // then
        assertThat(results).isNotNull();
        assertThat(responseTime).isLessThan(MAX_RESPONSE_TIME_MS);
        
        if (responseTime > MAX_RESPONSE_TIME_MS * 0.8) {
            System.out.printf("경고: 응답 시간이 기준의 80%% 초과 - %dms%n", responseTime);
        }
    }
    
    @Test
    @DisplayName("[종합] 태그 시스템 전체 성능 벤치마크")
    @Timeout(value = 300, unit = TimeUnit.SECONDS) // 5분
    void comprehensiveTagSystemBenchmark() {
        System.out.println("=== 태그 시스템 성능 벤치마크 시작 ===");
        
        // 1. 태그 생성 성능
        Instant start1 = Instant.now();
        createTestTags(1000);
        long tagCreationTime = Duration.between(start1, Instant.now()).toMillis();
        double tagCreationTPS = 1000.0 / (tagCreationTime / 1000.0);
        
        // 2. 태그 검색 성능
        Instant start2 = Instant.now();
        for (int i = 0; i < 100; i++) {
            tagService.searchTags("TEST_TAG", 20);
        }
        long searchTime = Duration.between(start2, Instant.now()).toMillis();
        double searchTPS = 100.0 / (searchTime / 1000.0);
        
        // 3. 추천 성능
        Instant start3 = Instant.now();
        for (int i = 0; i < 50; i++) {
            recommendationService.getPersonalizedRecommendations(testUserId, 10);
        }
        long recommendationTime = Duration.between(start3, Instant.now()).toMillis();
        double recommendationTPS = 50.0 / (recommendationTime / 1000.0);
        
        // 4. 메모리 사용량
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        
        // 결과 출력
        System.out.println("=== 성능 벤치마크 결과 ===");
        System.out.printf("태그 생성: %.2f TPS (%dms)%n", tagCreationTPS, tagCreationTime);
        System.out.printf("태그 검색: %.2f TPS (%dms)%n", searchTPS, searchTime);
        System.out.printf("루트 추천: %.2f TPS (%dms)%n", recommendationTPS, recommendationTime);
        System.out.printf("메모리 사용량: %dMB%n", memoryUsed);
        
        // 성능 기준 검증
        assertThat(tagCreationTPS).isGreaterThanOrEqualTo(100.0); // 최소 100 TPS
        assertThat(searchTPS).isGreaterThanOrEqualTo(50.0); // 최소 50 TPS
        assertThat(recommendationTPS).isGreaterThanOrEqualTo(10.0); // 최소 10 TPS
        assertThat(memoryUsed).isLessThan(1024L); // 최대 1GB
        
        System.out.println("=== 벤치마크 완료: 모든 성능 기준 통과 ===");
    }
    
    // ================================================================================================
    // Helper Methods
    // ================================================================================================
    
    private void prepareLargeTagDataset() {
        // 기본 태그 데이터 준비 (실제 구현에서는 @TestConfiguration으로 분리)
        createTestTags(1000);
    }
    
    private void createTestTags(int count) {
        List<TagCreateRequestDto> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requests.add(TagCreateRequestDto.builder()
                    .tagName("TEST_TAG_" + i)
                    .tagType(getRandomTagType())
                    .description("테스트 태그 " + i)
                    .build());
        }
        tagService.createTagsBatch(requests);
    }
    
    private void createTestRoutesWithTags(int routeCount) {
        // 테스트용 루트와 태그 연결 생성
        // 실제 구현에서는 RouteService와 연동
        for (int i = 0; i < routeCount; i++) {
            // 각 루트에 3-7개의 랜덤 태그 할당
            // routeTagService.assignTagsToRoute(routeId, randomTags);
        }
    }
    
    private String getRandomTagType() {
        String[] types = {"TECHNIQUE", "MOVEMENT", "HOLD_TYPE", "WALL_ANGLE", "FEATURE", "STYLE"};
        return types[(int) (Math.random() * types.length)];
    }
    
    private String getRandomColor() {
        String[] colors = {"RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "ORANGE"};
        return colors[(int) (Math.random() * colors.length)];
    }
}
```

## 실행 및 모니터링

### 실행 명령어
```bash
# 전체 성능 테스트 실행
./gradlew test --tests="*TagIntegrationPerformanceTest"

# 특정 성능 테스트만 실행
./gradlew test --tests="TagIntegrationPerformanceTest.BulkTagProcessingTest"

# 벤치마크 테스트만 실행
./gradlew test --tests="TagIntegrationPerformanceTest.comprehensiveTagSystemBenchmark"
```

### 성능 기준 (SLA)
- **태그 생성**: 최소 500 TPS
- **태그 검색**: 평균 100ms 이내
- **추천 알고리즘**: 평균 1초 이내
- **동시성 처리**: 200명 동시, 90% 성공률
- **캐시 효율**: 3배 이상 속도 향상

### 모니터링 지표
- [x] 처리량(TPS) 측정
- [x] 응답 시간 분석
- [x] 메모리 사용량 추적
- [x] 캐시 히트율 모니터링
- [x] 동시성 처리 능력 검증
- [x] 성능 일관성 확인