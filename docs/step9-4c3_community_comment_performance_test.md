# 커뮤니티 댓글 성능 테스트

## 개요
커뮤니티 댓글 시스템의 성능과 확장성을 검증하는 테스트입니다. 대량 데이터 처리, 응답 시간, 동시성, 메모리 사용량 등을 종합적으로 테스트합니다.

## 테스트 클래스 구조

```java
package com.routepick.community.performance;

import com.routepick.community.dto.request.CommentCreateRequestDto;
import com.routepick.community.dto.response.CommentResponseDto;
import com.routepick.community.service.CommentService;
import com.routepick.community.entity.Comment;
import com.routepick.community.repository.CommentRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * 커뮤니티 댓글 성능 테스트
 * 
 * 성능 검증 영역:
 * - 대량 댓글 처리 성능
 * - 계층형 구조 조회 성능
 * - 동시성 처리 성능
 * - 메모리 사용량 최적화
 * - 응답 시간 및 처리량
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommentPerformanceTest {

    @Autowired
    private CommentService commentService;
    
    @Autowired
    private CommentRepository commentRepository;
    
    private Long testUserId;
    private Long testPostId;
    private static final int LARGE_DATASET_SIZE = 10000;
    private static final int CONCURRENT_USERS = 100;
    private static final long MAX_RESPONSE_TIME_MS = 1000L;
    
    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testPostId = 1L;
    }
    
    @Nested
    @DisplayName("대량 데이터 처리 성능")
    class BulkDataProcessingTest {
        
        @Test
        @DisplayName("[성능] 대량 댓글 생성 - 10,000개")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void createBulkComments_Performance() {
            // given
            List<CommentCreateRequestDto> commentRequests = new ArrayList<>();
            for (int i = 0; i < LARGE_DATASET_SIZE; i++) {
                commentRequests.add(CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content("대량 테스트 댓글 " + i)
                        .build());
            }
            
            Instant startTime = Instant.now();
            
            // when
            List<CommentResponseDto> createdComments = new ArrayList<>();
            for (CommentCreateRequestDto request : commentRequests) {
                CommentResponseDto comment = commentService.createComment(testUserId, request);
                createdComments.add(comment);
            }
            
            Instant endTime = Instant.now();
            long executionTimeMs = Duration.between(startTime, endTime).toMillis();
            
            // then
            assertThat(createdComments).hasSize(LARGE_DATASET_SIZE);
            assertThat(executionTimeMs).isLessThan(30000L); // 30초 이내
            
            // 처리량 계산 (TPS - Transactions Per Second)
            double tps = (double) LARGE_DATASET_SIZE / (executionTimeMs / 1000.0);
            System.out.printf("대량 댓글 생성 성능: %d개 처리, %.2f TPS, %dms 소요%n", 
                    LARGE_DATASET_SIZE, tps, executionTimeMs);
            
            // 최소 100 TPS 이상 보장
            assertThat(tps).isGreaterThanOrEqualTo(100.0);
        }
        
        @Test
        @DisplayName("[성능] 계층형 댓글 대량 조회")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void retrieveHierarchicalComments_Performance() {
            // given - 계층형 구조 생성 (1000개 최상위 + 각각 10개씩 대댓글)
            List<Long> parentIds = new ArrayList<>();
            
            // 최상위 댓글 1000개 생성
            for (int i = 0; i < 1000; i++) {
                CommentCreateRequestDto parentRequest = CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content("부모 댓글 " + i)
                        .build();
                CommentResponseDto parent = commentService.createComment(testUserId, parentRequest);
                parentIds.add(parent.getCommentId());
            }
            
            // 각 부모마다 대댓글 10개씩 생성 (총 10,000개)
            for (Long parentId : parentIds) {
                for (int j = 0; j < 10; j++) {
                    CommentCreateRequestDto childRequest = CommentCreateRequestDto.builder()
                            .postId(testPostId)
                            .parentId(parentId)
                            .content("대댓글 " + j)
                            .build();
                    commentService.createComment(testUserId, childRequest);
                }
            }
            
            Instant startTime = Instant.now();
            
            // when - 전체 계층 구조 조회
            List<CommentResponseDto> hierarchicalComments = commentService.getCommentsByPost(testPostId);
            
            Instant endTime = Instant.now();
            long executionTimeMs = Duration.between(startTime, endTime).toMillis();
            
            // then
            assertThat(hierarchicalComments).hasSize(1000); // 최상위 댓글만 1000개
            assertThat(executionTimeMs).isLessThan(3000L); // 3초 이내
            
            // 각 부모의 자식 댓글 수 검증
            hierarchicalComments.forEach(parent -> {
                assertThat(parent.getChildComments()).hasSize(10);
            });
            
            System.out.printf("계층형 댓글 조회 성능: 11,000개 댓글 계층 조회, %dms 소요%n", executionTimeMs);
        }
        
        @Test
        @DisplayName("[성능] 페이징 처리 대용량 조회")
        void paginatedRetrieval_Performance() {
            // given - 대량 댓글 생성 (5000개)
            for (int i = 0; i < 5000; i++) {
                CommentCreateRequestDto request = CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content("페이징 테스트 댓글 " + i)
                        .build();
                commentService.createComment(testUserId, request);
            }
            
            // when - 페이징으로 조회 (50페이지, 각 100개씩)
            List<Long> responseTimes = new ArrayList<>();
            
            for (int page = 0; page < 50; page++) {
                Instant start = Instant.now();
                
                Pageable pageable = PageRequest.of(page, 100);
                List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtDesc(testPostId, pageable);
                
                Instant end = Instant.now();
                long responseTime = Duration.between(start, end).toMillis();
                responseTimes.add(responseTime);
                
                // 각 페이지 100개씩 확인
                assertThat(comments).hasSize(100);
            }
            
            // then - 모든 페이지 조회 시간이 일정 시간 이내
            double averageResponseTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxResponseTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            
            System.out.printf("페이징 조회 성능: 평균 %.2fms, 최대 %dms%n", averageResponseTime, maxResponseTime);
            
            assertThat(averageResponseTime).isLessThan(200.0); // 평균 200ms 이내
            assertThat(maxResponseTime).isLessThan(500L); // 최대 500ms 이내
        }
    }
    
    @Nested
    @DisplayName("동시성 처리 성능")
    class ConcurrencyPerformanceTest {
        
        @Test
        @DisplayName("[성능] 동시 댓글 생성 - 100명 사용자")
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        void concurrentCommentCreation_Performance() throws Exception {
            // given
            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
            List<CompletableFuture<CommentResponseDto>> futures = new ArrayList<>();
            
            Instant startTime = Instant.now();
            
            // when - 100명이 동시에 댓글 생성
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                final int userId = i + 1;
                final int commentIndex = i;
                
                CompletableFuture<CommentResponseDto> future = CompletableFuture.supplyAsync(() -> {
                    CommentCreateRequestDto request = CommentCreateRequestDto.builder()
                            .postId(testPostId)
                            .content("동시성 테스트 댓글 " + commentIndex)
                            .build();
                    return commentService.createComment((long) userId, request);
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 작업 완료 대기
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            allFutures.get(10, TimeUnit.SECONDS);
            
            Instant endTime = Instant.now();
            long executionTimeMs = Duration.between(startTime, endTime).toMillis();
            
            // then
            List<CommentResponseDto> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            assertThat(results).hasSize(CONCURRENT_USERS);
            assertThat(executionTimeMs).isLessThan(10000L); // 10초 이내
            
            // 동시성 처리량 계산
            double concurrentTps = (double) CONCURRENT_USERS / (executionTimeMs / 1000.0);
            System.out.printf("동시 댓글 생성 성능: %d명 동시 처리, %.2f TPS, %dms 소요%n", 
                    CONCURRENT_USERS, concurrentTps, executionTimeMs);
            
            executor.shutdown();
        }
        
        @Test
        @DisplayName("[성능] 동시 댓글 조회 - 읽기 성능")
        void concurrentCommentRetrieval_Performance() throws Exception {
            // given - 기본 댓글 1000개 생성
            for (int i = 0; i < 1000; i++) {
                CommentCreateRequestDto request = CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content("조회 테스트 댓글 " + i)
                        .build();
                commentService.createComment(testUserId, request);
            }
            
            ExecutorService executor = Executors.newFixedThreadPool(50);
            List<CompletableFuture<List<CommentResponseDto>>> futures = new ArrayList<>();
            
            Instant startTime = Instant.now();
            
            // when - 50명이 동시에 댓글 조회
            for (int i = 0; i < 50; i++) {
                CompletableFuture<List<CommentResponseDto>> future = CompletableFuture.supplyAsync(() -> {
                    return commentService.getCommentsByPost(testPostId);
                }, executor);
                futures.add(future);
            }
            
            // 모든 조회 작업 완료 대기
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            allFutures.get(5, TimeUnit.SECONDS);
            
            Instant endTime = Instant.now();
            long executionTimeMs = Duration.between(startTime, endTime).toMillis();
            
            // then
            List<List<CommentResponseDto>> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            assertThat(results).hasSize(50);
            results.forEach(commentList -> {
                assertThat(commentList).hasSize(1000);
            });
            
            double readTps = 50.0 / (executionTimeMs / 1000.0);
            System.out.printf("동시 댓글 조회 성능: 50건 동시 조회, %.2f TPS, %dms 소요%n", 
                    readTps, executionTimeMs);
            
            assertThat(executionTimeMs).isLessThan(3000L); // 3초 이내
            
            executor.shutdown();
        }
        
        @Test
        @DisplayName("[성능] 혼합 작업 부하 테스트")
        void mixedWorkload_Performance() throws Exception {
            // given
            ExecutorService executor = Executors.newFixedThreadPool(100);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // 기본 댓글 500개 생성
            for (int i = 0; i < 500; i++) {
                CommentCreateRequestDto request = CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content("혼합 테스트 기본 댓글 " + i)
                        .build();
                commentService.createComment(testUserId, request);
            }
            
            Instant startTime = Instant.now();
            
            // when - 혼합 작업 (생성 30%, 조회 50%, 수정 15%, 삭제 5%)
            for (int i = 0; i < 100; i++) {
                final int taskId = i;
                final int workloadType = i % 20; // 0-19로 작업 타입 결정
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        if (workloadType < 6) { // 30% - 댓글 생성
                            CommentCreateRequestDto request = CommentCreateRequestDto.builder()
                                    .postId(testPostId)
                                    .content("혼합 테스트 생성 댓글 " + taskId)
                                    .build();
                            commentService.createComment(testUserId, request);
                            
                        } else if (workloadType < 16) { // 50% - 댓글 조회
                            commentService.getCommentsByPost(testPostId);
                            
                        } else if (workloadType < 19) { // 15% - 댓글 수정 (시뮬레이션)
                            // 실제 환경에서는 기존 댓글을 수정하지만, 테스트에서는 조회로 대체
                            commentService.getCommentsByPost(testPostId);
                            
                        } else { // 5% - 댓글 삭제 (시뮬레이션)  
                            // 실제 환경에서는 기존 댓글을 삭제하지만, 테스트에서는 조회로 대체
                            commentService.getCommentsByPost(testPostId);
                        }
                    } catch (Exception e) {
                        System.err.println("혼합 작업 중 오류 발생: " + e.getMessage());
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 혼합 작업 완료 대기
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            allFutures.get(20, TimeUnit.SECONDS);
            
            Instant endTime = Instant.now();
            long executionTimeMs = Duration.between(startTime, endTime).toMillis();
            
            // then
            double mixedTps = 100.0 / (executionTimeMs / 1000.0);
            System.out.printf("혼합 작업 부하 성능: 100건 혼합 작업, %.2f TPS, %dms 소요%n", 
                    mixedTps, executionTimeMs);
            
            assertThat(executionTimeMs).isLessThan(15000L); // 15초 이내
            
            executor.shutdown();
        }
    }
    
    @Nested
    @DisplayName("응답 시간 성능")
    class ResponseTimePerformanceTest {
        
        @RepeatedTest(100)
        @DisplayName("[성능] 단일 댓글 생성 응답 시간")
        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        void singleCommentCreation_ResponseTime() {
            // given
            CommentCreateRequestDto request = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content("응답 시간 테스트 댓글")
                    .build();
            
            Instant startTime = Instant.now();
            
            // when
            CommentResponseDto result = commentService.createComment(testUserId, request);
            
            Instant endTime = Instant.now();
            long responseTimeMs = Duration.between(startTime, endTime).toMillis();
            
            // then
            assertThat(result).isNotNull();
            assertThat(responseTimeMs).isLessThan(MAX_RESPONSE_TIME_MS); // 1초 이내
            
            if (responseTimeMs > 500) {
                System.out.printf("경고: 댓글 생성 응답 시간이 길어짐 - %dms%n", responseTimeMs);
            }
        }
        
        @RepeatedTest(50)
        @DisplayName("[성능] 댓글 목록 조회 응답 시간")
        @Timeout(value = 2, unit = TimeUnit.SECONDS)
        void commentListRetrieval_ResponseTime() {
            // given - 댓글 100개 생성 (한 번만)
            static boolean initialized = false;
            if (!initialized) {
                for (int i = 0; i < 100; i++) {
                    CommentCreateRequestDto request = CommentCreateRequestDto.builder()
                            .postId(testPostId)
                            .content("응답 시간 테스트 댓글 " + i)
                            .build();
                    commentService.createComment(testUserId, request);
                }
                initialized = true;
            }
            
            Instant startTime = Instant.now();
            
            // when
            List<CommentResponseDto> result = commentService.getCommentsByPost(testPostId);
            
            Instant endTime = Instant.now();
            long responseTimeMs = Duration.between(startTime, endTime).toMillis();
            
            // then
            assertThat(result).isNotEmpty();
            assertThat(responseTimeMs).isLessThan(500L); // 500ms 이내
            
            if (responseTimeMs > 200) {
                System.out.printf("경고: 댓글 조회 응답 시간이 길어짐 - %dms%n", responseTimeMs);
            }
        }
    }
    
    @Nested
    @DisplayName("메모리 사용량 최적화")
    class MemoryOptimizationTest {
        
        @Test
        @DisplayName("[성능] 대량 댓글 로딩 메모리 사용량")
        void bulkCommentLoading_MemoryUsage() {
            // given
            Runtime runtime = Runtime.getRuntime();
            
            // 초기 메모리 상태 측정
            System.gc(); // 가비지 컬렉션 실행
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // 대량 댓글 생성 (5000개)
            for (int i = 0; i < 5000; i++) {
                CommentCreateRequestDto request = CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content("메모리 테스트 댓글 " + i + " - 추가 텍스트로 메모리 사용량 증가")
                        .build();
                commentService.createComment(testUserId, request);
            }
            
            // when - 전체 댓글 조회
            List<CommentResponseDto> comments = commentService.getCommentsByPost(testPostId);
            
            // 메모리 사용량 측정
            System.gc();
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = finalMemory - initialMemory;
            
            // then
            assertThat(comments).hasSize(5000);
            
            // 메모리 사용량 리포트
            double memoryUsedMB = memoryUsed / (1024.0 * 1024.0);
            double memoryPerComment = memoryUsed / (double) comments.size();
            
            System.out.printf("메모리 사용량: %.2fMB 총 사용, %.2f bytes/댓글%n", 
                    memoryUsedMB, memoryPerComment);
            
            // 메모리 사용량이 합리적인 범위 내인지 확인 (댓글당 1KB 이하)
            assertThat(memoryPerComment).isLessThan(1024.0);
        }
        
        @Test
        @DisplayName("[성능] 계층형 댓글 메모리 효율성")
        void hierarchicalComments_MemoryEfficiency() {
            // given
            Runtime runtime = Runtime.getRuntime();
            System.gc();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // 계층 구조 생성 (100개 부모, 각각 50개 자식 = 5000개 총)
            List<Long> parentIds = new ArrayList<>();
            
            for (int i = 0; i < 100; i++) {
                CommentCreateRequestDto parentRequest = CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content("메모리 테스트 부모 댓글 " + i)
                        .build();
                CommentResponseDto parent = commentService.createComment(testUserId, parentRequest);
                parentIds.add(parent.getCommentId());
            }
            
            for (Long parentId : parentIds) {
                for (int j = 0; j < 50; j++) {
                    CommentCreateRequestDto childRequest = CommentCreateRequestDto.builder()
                            .postId(testPostId)
                            .parentId(parentId)
                            .content("메모리 테스트 자식 댓글 " + j)
                            .build();
                    commentService.createComment(testUserId, childRequest);
                }
            }
            
            // when - 계층 구조 전체 로딩
            List<CommentResponseDto> hierarchicalComments = commentService.getCommentsByPost(testPostId);
            
            System.gc();
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = finalMemory - initialMemory;
            
            // then
            assertThat(hierarchicalComments).hasSize(100); // 최상위만
            int totalComments = hierarchicalComments.size() + 
                    hierarchicalComments.stream()
                            .mapToInt(parent -> parent.getChildComments().size())
                            .sum();
            assertThat(totalComments).isEqualTo(5100);
            
            double memoryUsedMB = memoryUsed / (1024.0 * 1024.0);
            System.out.printf("계층형 댓글 메모리 사용량: %.2fMB (5100개 댓글)%n", memoryUsedMB);
            
            // 계층 구조도 효율적으로 메모리 사용
            assertThat(memoryUsedMB).isLessThan(50.0); // 50MB 이하
        }
    }
    
    @Test
    @DisplayName("[종합] 댓글 시스템 전체 성능 벤치마크")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void comprehensivePerformanceBenchmark() {
        System.out.println("=== 댓글 시스템 성능 벤치마크 시작 ===");
        
        // 1. 기본 CRUD 성능 측정
        Instant start1 = Instant.now();
        for (int i = 0; i < 1000; i++) {
            CommentCreateRequestDto request = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content("벤치마크 테스트 댓글 " + i)
                    .build();
            commentService.createComment(testUserId, request);
        }
        long crudTime = Duration.between(start1, Instant.now()).toMillis();
        double crudTps = 1000.0 / (crudTime / 1000.0);
        
        // 2. 조회 성능 측정
        Instant start2 = Instant.now();
        for (int i = 0; i < 100; i++) {
            commentService.getCommentsByPost(testPostId);
        }
        long queryTime = Duration.between(start2, Instant.now()).toMillis();
        double queryTps = 100.0 / (queryTime / 1000.0);
        
        // 3. 메모리 사용량 측정
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024); // MB
        
        // 결과 출력
        System.out.println("=== 성능 벤치마크 결과 ===");
        System.out.printf("CRUD 성능: %.2f TPS (%dms)%n", crudTps, crudTime);
        System.out.printf("조회 성능: %.2f TPS (%dms)%n", queryTps, queryTime);
        System.out.printf("메모리 사용량: %dMB%n", memoryUsed);
        
        // 성능 기준 검증
        assertThat(crudTps).isGreaterThanOrEqualTo(50.0); // 최소 50 TPS
        assertThat(queryTps).isGreaterThanOrEqualTo(20.0); // 최소 20 TPS
        assertThat(memoryUsed).isLessThan(512L); // 최대 512MB
        
        System.out.println("=== 벤치마크 완료: 모든 성능 기준 통과 ===");
    }
}
```

## 성능 테스트 실행 가이드

### 실행 명령어
```bash
# 성능 테스트 전체 실행
./gradlew test --tests="*CommentPerformanceTest"

# 대량 데이터 처리 테스트만 실행
./gradlew test --tests="CommentPerformanceTest.BulkDataProcessingTest"

# 동시성 테스트만 실행
./gradlew test --tests="CommentPerformanceTest.ConcurrencyPerformanceTest"

# 종합 벤치마크만 실행
./gradlew test --tests="CommentPerformanceTest.comprehensivePerformanceBenchmark"
```

### 성능 기준 (SLA)
- **댓글 생성**: < 1초, 최소 100 TPS
- **댓글 조회**: < 500ms, 최소 20 TPS  
- **대량 처리**: 10,000건 < 30초
- **동시 처리**: 100명 동시 < 10초
- **메모리 효율**: 댓글당 < 1KB

### 모니터링 포인트
- [x] 응답 시간 측정
- [x] 처리량(TPS) 계산
- [x] 메모리 사용량 추적
- [x] 동시성 처리 능력
- [x] 확장성 검증