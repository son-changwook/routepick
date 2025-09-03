# E2E 장애 복구 테스트 시나리오

## End-to-End 장애 복구 테스트

### 1. 장애 복구 테스트 클래스

```java
package com.routepick.system.resilience;

import com.routepick.system.resilience.dto.FailureScenario;
import com.routepick.system.resilience.dto.RecoveryResult;
import com.routepick.system.resilience.enums.FailureType;
import com.routepick.system.resilience.enums.RecoveryStrategy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * End-to-End 장애 복구 테스트
 * 
 * 테스트 시나리오:
 * - 데이터베이스 연결 실패 및 복구
 * - Redis 캐시 장애 및 대체 메커니즘
 * - 외부 API 응답 불가 및 Fallback
 * - 메모리 부족 상황 및 해결
 * - 네트워크 분할 및 복구
 * - 복합 장애 상황 처리
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EndToEndFailureRecoveryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_failure_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private FailureRecoveryService failureRecoveryService;
    
    @Autowired
    private HealthCheckService healthCheckService;
    
    @Autowired
    private SystemMonitoringService monitoringService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private PaymentService paymentService;
    
    @BeforeEach
    void setUp() {
        // 시스템 상태 초기화 및 기준선 설정
        establishHealthyBaseline();
        monitoringService.startFailureRecoveryMonitoring();
    }
    
    @AfterEach
    void tearDown() {
        // 테스트 후 시스템 상태 복원
        restoreSystemToHealthyState();
        monitoringService.stopFailureRecoveryMonitoring();
    }
    
    @Nested
    @DisplayName("데이터베이스 장애 복구")
    class DatabaseFailureRecoveryTest {
        
        @Test
        @Timeout(60)
        @DisplayName("[복구] 데이터베이스 연결 실패 → 자동 복구")
        void recoverFromDatabaseConnectionFailure() {
            System.out.println("=== 데이터베이스 장애 복구 테스트 시작 ===");
            
            // 1. 정상 상태 확인
            assertThat(healthCheckService.checkDatabaseHealth()).isTrue();
            System.out.println("✅ 초기 데이터베이스 상태 정상");
            
            // 2. 데이터베이스 장애 시뮬레이션
            FailureScenario scenario = FailureScenario.builder()
                    .scenarioId("DB_FAILURE_001")
                    .failureType(FailureType.DATABASE_CONNECTION)
                    .duration(Duration.ofSeconds(30))
                    .severity("HIGH")
                    .build();
            
            // 3. 장애 발생 및 복구 테스트
            RecoveryResult result = failureRecoveryService.executeFailureScenario(scenario);
            
            // 4. 복구 결과 검증
            assertThat(result.isFailureDetected()).isTrue();
            assertThat(result.isRecoveryAttempted()).isTrue();
            System.out.println("✅ 장애 감지 및 복구 시도 확인");
            
            // 5. 복구 시간 검증 (30초 이내)
            assertThat(result.getTotalRecoveryTime()).isLessThan(30000);
            System.out.printf("⏱️ 복구 시간: %d ms%n", result.getTotalRecoveryTime());
            
            // 6. 서비스 연속성 확인 - 사용자 서비스 정상 동작
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                var userResponse = userService.getUserProfile(1L);
                assertThat(userResponse).isNotNull();
            });
            System.out.println("✅ 사용자 서비스 정상 동작 확인");
            
            // 7. 데이터 일관성 검증
            boolean dataConsistent = verifyDataConsistency();
            assertThat(dataConsistent).isTrue();
            System.out.println("✅ 데이터 일관성 유지 확인");
            
            System.out.println("=== 데이터베이스 장애 복구 테스트 완료 ===");
        }
        
        @Test
        @Timeout(45)
        @DisplayName("[복구] 데이터베이스 읽기 전용 모드 전환")
        void fallbackToReadOnlyMode() {
            // given - 쓰기 작업 불가 상황
            FailureScenario writeFailure = FailureScenario.builder()
                    .failureType(FailureType.DATABASE_WRITE_FAILURE)
                    .duration(Duration.ofSeconds(20))
                    .build();
            
            // when - 읽기 전용 모드로 복구
            RecoveryResult result = failureRecoveryService.executeFailureScenario(writeFailure);
            
            // then - 읽기 작업은 계속 가능
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                var userProfile = userService.getUserProfile(1L);
                assertThat(userProfile).isNotNull();
            });
            
            // 쓰기 작업은 지연되거나 큐에 저장
            assertThatNoException().isThrownBy(() -> {
                userService.updateUserProfile(1L, Map.of("nickname", "테스트"));
            });
            
            assertThat(result.isRecoverySuccessful()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("캐시 시스템 장애 복구")
    class CacheFailureRecoveryTest {
        
        @Test
        @Timeout(30)
        @DisplayName("[복구] Redis 장애 → 로컬 캐시 Fallback")
        void fallbackToLocalCacheOnRedisFailure() {
            System.out.println("=== Redis 캐시 장애 복구 테스트 시작 ===");
            
            // 1. Redis 정상 상태 확인
            assertThat(healthCheckService.checkRedisHealth()).isTrue();
            System.out.println("✅ 초기 Redis 상태 정상");
            
            // 2. Redis 장애 시뮬레이션
            FailureScenario redisFailure = FailureScenario.builder()
                    .failureType(FailureType.CACHE_FAILURE)
                    .duration(Duration.ofSeconds(15))
                    .build();
            
            // 3. 장애 복구 실행
            RecoveryResult result = failureRecoveryService.handleRedisFailure();
            
            // 4. 로컬 캐시로 대체 동작 확인
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                // 캐시가 필요한 작업이 계속 동작하는지 확인
                var popularRoutes = userService.getPopularRoutes(10);
                assertThat(popularRoutes).isNotEmpty();
            });
            System.out.println("✅ 로컬 캐시 Fallback 동작 확인");
            
            // 5. 성능 영향 측정
            long responseTimeWithoutRedis = measureResponseTime(() -> 
                    userService.getUserRecommendations(1L));
            
            // Redis 없이도 5초 이내 응답
            assertThat(responseTimeWithoutRedis).isLessThan(5000);
            System.out.printf("⏱️ Redis 없이 응답 시간: %d ms%n", responseTimeWithoutRedis);
            
            assertThat(result.isRecoverySuccessful()).isTrue();
            System.out.println("=== Redis 캐시 장애 복구 테스트 완료 ===");
        }
        
        @Test
        @Timeout(20)
        @DisplayName("[복구] 캐시 웜업 후 정상 성능 복원")
        void restorePerformanceAfterCacheWarmup() {
            // given - Redis 복구 후 상태
            failureRecoveryService.handleRedisFailure();
            
            // when - 캐시 웜업 실행
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(healthCheckService.checkRedisHealth()).isTrue();
            });
            
            // 주요 데이터 캐시 웜업
            userService.warmupUserCache();
            messageService.warmupMessageCache();
            
            // then - 성능 복원 확인
            long warmupResponseTime = measureResponseTime(() -> 
                    userService.getUserRecommendations(1L));
            
            // 웜업 후 빠른 응답 시간 (1초 이내)
            assertThat(warmupResponseTime).isLessThan(1000);
        }
    }
    
    @Nested
    @DisplayName("외부 서비스 장애 복구")
    class ExternalServiceFailureTest {
        
        @Test
        @Timeout(25)
        @DisplayName("[복구] 결제 API 장애 → 대체 결제 수단")
        void fallbackPaymentMethodOnApiFailure() {
            System.out.println("=== 외부 결제 API 장애 복구 테스트 시작 ===");
            
            // 1. 주 결제 API 장애 시뮬레이션
            FailureScenario paymentApiFailure = FailureScenario.builder()
                    .failureType(FailureType.EXTERNAL_API)
                    .targetService("PRIMARY_PAYMENT_API")
                    .duration(Duration.ofSeconds(15))
                    .build();
            
            // 2. 결제 처리 시도 (장애 상황에서)
            CompletableFuture<String> paymentResult = CompletableFuture.supplyAsync(() -> {
                try {
                    return paymentService.processPayment(
                            1L, "50000", "CARD", Map.of("cardNumber", "1234-****-****-5678")
                    );
                } catch (Exception e) {
                    return "PAYMENT_FAILED: " + e.getMessage();
                }
            });
            
            // 3. 대체 결제 API로 자동 전환
            RecoveryResult recoveryResult = failureRecoveryService.handleExternalApiFailure();
            
            // 4. 결제 성공 확인 (대체 수단 사용)
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                String result = paymentResult.join();
                assertThat(result).doesNotContain("PAYMENT_FAILED");
                assertThat(result).contains("SUCCESS");
            });
            System.out.println("✅ 대체 결제 API를 통한 결제 성공");
            
            // 5. 서킷 브레이커 상태 확인
            boolean circuitBreakerOpen = monitoringService.isCircuitBreakerOpen("payment-api");
            assertThat(circuitBreakerOpen).isTrue();
            System.out.println("✅ 서킷 브레이커 정상 동작 확인");
            
            assertThat(recoveryResult.isRecoverySuccessful()).isTrue();
            System.out.println("=== 외부 결제 API 장애 복구 테스트 완료 ===");
        }
        
        @Test
        @DisplayName("[복구] 지도 API 장애 → 캐시된 위치 데이터 사용")
        void useCachedLocationDataOnMapApiFailure() {
            // given - 지도 API 장애
            FailureScenario mapApiFailure = FailureScenario.builder()
                    .failureType(FailureType.EXTERNAL_API)
                    .targetService("MAP_API")
                    .build();
            
            // when - 위치 기반 암장 검색 시도
            failureRecoveryService.executeFailureScenario(mapApiFailure);
            
            // then - 캐시된 데이터로 검색 결과 제공
            var nearbyGyms = userService.findNearbyGyms(37.5665, 126.9780, 5000);
            
            assertThat(nearbyGyms).isNotEmpty();
            assertThat(nearbyGyms.size()).isGreaterThan(0);
            
            // 캐시 데이터임을 표시
            boolean usingCachedData = nearbyGyms.stream()
                    .anyMatch(gym -> gym.getDataSource().equals("CACHED"));
            assertThat(usingCachedData).isTrue();
        }
    }
    
    @Nested
    @DisplayName("리소스 부족 장애 복구")
    class ResourceShortageRecoveryTest {
        
        @Test
        @Timeout(40)
        @DisplayName("[복구] 메모리 부족 → 캐시 정리 및 스로틀링")
        void recoverFromMemoryShortage() {
            System.out.println("=== 메모리 부족 장애 복구 테스트 시작 ===");
            
            // 1. 메모리 사용량 증가 시뮬레이션
            simulateHighMemoryUsage();
            System.out.println("⚠️ 높은 메모리 사용량 시뮬레이션");
            
            // 2. 메모리 부족 감지 및 복구
            RecoveryResult result = failureRecoveryService.handleMemoryShortage();
            
            // 3. 메모리 회복 확인
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long maxMemory = Runtime.getRuntime().maxMemory();
                double memoryUsage = (double) usedMemory / maxMemory;
                
                assertThat(memoryUsage).isLessThan(0.8); // 80% 미만으로 회복
            });
            System.out.println("✅ 메모리 사용량 정상 범위 회복");
            
            // 4. 핵심 서비스 정상 동작 확인
            var userProfile = userService.getUserProfile(1L);
            assertThat(userProfile).isNotNull();
            System.out.println("✅ 핵심 서비스 정상 동작");
            
            // 5. 스로틀링 효과 확인
            boolean throttlingActive = monitoringService.isThrottlingActive();
            assertThat(throttlingActive).isTrue();
            System.out.println("✅ 메모리 절약을 위한 스로틀링 활성화");
            
            assertThat(result.isRecoverySuccessful()).isTrue();
            System.out.println("=== 메모리 부족 장애 복구 테스트 완료 ===");
        }
        
        @Test
        @DisplayName("[복구] CPU 과부하 → 요청 제한 및 우선순위 처리")
        void handleCpuOverload() {
            // given - CPU 집약적 작업으로 과부하 시뮬레이션
            simulateHighCpuUsage();
            
            // when - CPU 과부하 복구
            RecoveryResult result = failureRecoveryService.handleCpuOverload();
            
            // then - 요청 제한 및 우선순위 처리 확인
            assertThat(monitoringService.isRateLimitingActive()).isTrue();
            assertThat(monitoringService.isPriorityProcessingActive()).isTrue();
            
            // 핵심 기능은 계속 동작
            var healthStatus = healthCheckService.getSystemHealthStatus();
            assertThat(healthStatus.get("core_services")).isEqualTo("OPERATIONAL");
            
            assertThat(result.isRecoverySuccessful()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("네트워크 장애 복구")
    class NetworkFailureRecoveryTest {
        
        @Test
        @Timeout(60)
        @DisplayName("[복구] 네트워크 분할 → 로컬 모드 전환")
        void handleNetworkPartition() {
            System.out.println("=== 네트워크 분할 장애 복구 테스트 시작 ===");
            
            // 1. 네트워크 분할 시뮬레이션
            FailureScenario networkPartition = FailureScenario.builder()
                    .failureType(FailureType.NETWORK_PARTITION)
                    .duration(Duration.ofSeconds(30))
                    .build();
            
            // 2. 네트워크 분할 복구 처리
            RecoveryResult result = failureRecoveryService.handleNetworkPartition();
            
            // 3. 로컬 모드 동작 확인
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                boolean localModeActive = monitoringService.isLocalModeActive();
                assertThat(localModeActive).isTrue();
            });
            System.out.println("✅ 로컬 모드 전환 완료");
            
            // 4. 로컬 데이터로 서비스 연속성 확인
            var cachedUserData = userService.getUserProfile(1L);
            assertThat(cachedUserData).isNotNull();
            assertThat(cachedUserData.getDataSource()).isEqualTo("LOCAL_CACHE");
            System.out.println("✅ 로컬 데이터로 서비스 연속성 유지");
            
            // 5. 네트워크 복구 후 데이터 동기화
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
                // 네트워크 복구 시뮬레이션
                simulateNetworkRecovery();
                
                // 데이터 동기화 확인
                boolean syncCompleted = monitoringService.isDataSyncCompleted();
                assertThat(syncCompleted).isTrue();
            });
            System.out.println("✅ 네트워크 복구 후 데이터 동기화 완료");
            
            assertThat(result.isRecoverySuccessful()).isTrue();
            System.out.println("=== 네트워크 분할 장애 복구 테스트 완료 ===");
        }
    }
    
    @Nested
    @DisplayName("복합 장애 시나리오")
    class CompositeFailureScenarioTest {
        
        @Test
        @Timeout(120)
        @DisplayName("[복합] 데이터베이스 + Redis + 외부API 동시 장애")
        void handleMultipleSimultaneousFailures() {
            System.out.println("=== 복합 장애 시나리오 테스트 시작 ===");
            
            // 1. 복합 장애 시나리오 설정
            List<FailureScenario> multipleFailures = List.of(
                    FailureScenario.builder()
                            .failureType(FailureType.DATABASE_CONNECTION)
                            .duration(Duration.ofSeconds(45))
                            .build(),
                    FailureScenario.builder()
                            .failureType(FailureType.CACHE_FAILURE)
                            .duration(Duration.ofSeconds(30))
                            .build(),
                    FailureScenario.builder()
                            .failureType(FailureType.EXTERNAL_API)
                            .duration(Duration.ofSeconds(60))
                            .build()
            );
            
            // 2. 동시 다발적 장애 실행
            List<CompletableFuture<RecoveryResult>> recoveryTasks = multipleFailures.stream()
                    .map(scenario -> CompletableFuture.supplyAsync(() -> 
                            failureRecoveryService.executeFailureScenario(scenario)))
                    .toList();
            
            // 3. 모든 복구 작업 완료 대기
            List<RecoveryResult> results = recoveryTasks.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            // 4. 각 장애별 복구 성공 확인
            assertThat(results).hasSize(3);
            assertThat(results.stream().allMatch(RecoveryResult::isRecoverySuccessful)).isTrue();
            System.out.println("✅ 모든 개별 장애 복구 성공");
            
            // 5. 시스템 전체 건강성 확인
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                Map<String, String> systemHealth = healthCheckService.getSystemHealthStatus();
                
                assertThat(systemHealth.get("overall_status")).isEqualTo("HEALTHY");
                assertThat(systemHealth.get("database")).isEqualTo("HEALTHY");
                assertThat(systemHealth.get("cache")).isEqualTo("HEALTHY");
                assertThat(systemHealth.get("external_apis")).isEqualTo("HEALTHY");
            });
            System.out.println("✅ 시스템 전체 건강성 복원 완료");
            
            // 6. 서비스 기능 정상성 검증
            assertThatNoException().isThrownBy(() -> {
                userService.getUserProfile(1L);
                messageService.sendMessage(createTestMessage());
                paymentService.processPayment(1L, "10000", "CARD", Map.of());
            });
            System.out.println("✅ 모든 주요 서비스 정상 동작 확인");
            
            // 7. 복구 시간 분석
            long totalRecoveryTime = results.stream()
                    .mapToLong(RecoveryResult::getTotalRecoveryTime)
                    .max().orElse(0L);
            
            assertThat(totalRecoveryTime).isLessThan(90000); // 90초 이내 복구
            System.out.printf("⏱️ 전체 복구 시간: %d ms%n", totalRecoveryTime);
            
            System.out.println("=== 복합 장애 시나리오 테스트 완료 ===");
        }
        
        @Test
        @Timeout(90)
        @DisplayName("[극한] 시스템 리소스 고갈 상황 복구")
        void recoverFromSystemResourceExhaustion() {
            System.out.println("=== 시스템 리소스 고갈 복구 테스트 시작 ===");
            
            // 1. 극한 상황 시뮬레이션
            simulateSystemResourceExhaustion();
            
            // 2. 비상 복구 모드 활성화
            RecoveryResult emergencyRecovery = failureRecoveryService.activateEmergencyRecovery();
            
            // 3. 핵심 서비스만 유지 확인
            await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
                boolean emergencyModeActive = monitoringService.isEmergencyModeActive();
                assertThat(emergencyModeActive).isTrue();
                
                // 인증 서비스는 계속 동작
                assertThatNoException().isThrownBy(() -> 
                        userService.authenticateUser("test@example.com", "password"));
            });
            System.out.println("✅ 비상 모드에서 핵심 서비스 유지");
            
            // 4. 점진적 서비스 복원
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                boolean systemRecovered = healthCheckService.checkOverallSystemHealth();
                assertThat(systemRecovered).isTrue();
            });
            System.out.println("✅ 시스템 점진적 복원 완료");
            
            assertThat(emergencyRecovery.isRecoverySuccessful()).isTrue();
            System.out.println("=== 시스템 리소스 고갈 복구 테스트 완료 ===");
        }
    }
    
    @Test
    @Timeout(180)
    @DisplayName("[종합] End-to-End 장애 복구 전체 시나리오")
    void comprehensive_EndToEndFailureRecoveryScenario() {
        System.out.println("=== End-to-End 장애 복구 전체 시나리오 테스트 시작 ===");
        
        // 0. 초기 시스템 상태 기록
        Map<String, Object> initialMetrics = captureSystemMetrics();
        System.out.println("📊 초기 시스템 메트릭 수집 완료");
        
        // 1. 순차적 장애 발생 및 복구
        System.out.println("\n🔄 1단계: 데이터베이스 장애 복구");
        RecoveryResult dbRecovery = failureRecoveryService.handleDatabaseFailure();
        assertThat(dbRecovery.isRecoverySuccessful()).isTrue();
        System.out.printf("✅ DB 복구 완료 (%d ms)%n", dbRecovery.getTotalRecoveryTime());
        
        System.out.println("\n🔄 2단계: 캐시 시스템 장애 복구");
        RecoveryResult cacheRecovery = failureRecoveryService.handleRedisFailure();
        assertThat(cacheRecovery.isRecoverySuccessful()).isTrue();
        System.out.printf("✅ 캐시 복구 완료 (%d ms)%n", cacheRecovery.getTotalRecoveryTime());
        
        System.out.println("\n🔄 3단계: 외부 API 장애 복구");
        RecoveryResult apiRecovery = failureRecoveryService.handleExternalApiFailure();
        assertThat(apiRecovery.isRecoverySuccessful()).isTrue();
        System.out.printf("✅ API 복구 완료 (%d ms)%n", apiRecovery.getTotalRecoveryTime());
        
        System.out.println("\n🔄 4단계: 메모리 부족 복구");
        RecoveryResult memoryRecovery = failureRecoveryService.handleMemoryShortage();
        assertThat(memoryRecovery.isRecoverySuccessful()).isTrue();
        System.out.printf("✅ 메모리 복구 완료 (%d ms)%n", memoryRecovery.getTotalRecoveryTime());
        
        System.out.println("\n🔄 5단계: 네트워크 분할 복구");
        RecoveryResult networkRecovery = failureRecoveryService.handleNetworkPartition();
        assertThat(networkRecovery.isRecoverySuccessful()).isTrue();
        System.out.printf("✅ 네트워크 복구 완료 (%d ms)%n", networkRecovery.getTotalRecoveryTime());
        
        // 2. 전체 시스템 건강성 검증
        System.out.println("\n🏥 시스템 전체 건강성 검증");
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<String, String> finalHealth = healthCheckService.getSystemHealthStatus();
            assertThat(finalHealth.get("overall_status")).isEqualTo("HEALTHY");
            assertThat(finalHealth.get("database")).isEqualTo("HEALTHY");
            assertThat(finalHealth.get("cache")).isEqualTo("HEALTHY");
            assertThat(finalHealth.get("external_apis")).isEqualTo("HEALTHY");
            assertThat(finalHealth.get("memory")).isEqualTo("HEALTHY");
            assertThat(finalHealth.get("network")).isEqualTo("HEALTHY");
        });
        System.out.println("✅ 모든 시스템 컴포넌트 정상 상태");
        
        // 3. 비즈니스 로직 정상 동작 확인
        System.out.println("\n💼 비즈니스 로직 정상성 검증");
        
        // 사용자 관리
        var userProfile = userService.getUserProfile(1L);
        assertThat(userProfile).isNotNull();
        System.out.println("✅ 사용자 관리 기능 정상");
        
        // 메시지 시스템
        String messageId = messageService.sendMessage(createTestMessage());
        assertThat(messageId).isNotNull();
        System.out.println("✅ 메시지 시스템 정상");
        
        // 결제 시스템
        String paymentResult = paymentService.processPayment(
                1L, "5000", "CARD", Map.of("cardNumber", "1234-****-****-5678"));
        assertThat(paymentResult).contains("SUCCESS");
        System.out.println("✅ 결제 시스템 정상");
        
        // 추천 시스템
        var recommendations = userService.getUserRecommendations(1L);
        assertThat(recommendations).isNotEmpty();
        System.out.println("✅ 추천 시스템 정상");
        
        // 4. 성능 영향 분석
        System.out.println("\n📈 성능 영향 분석");
        Map<String, Object> finalMetrics = captureSystemMetrics();
        
        double responseTimeImpact = calculatePerformanceImpact(initialMetrics, finalMetrics);
        assertThat(responseTimeImpact).isLessThan(20.0); // 20% 미만 성능 영향
        System.out.printf("📊 전체 성능 영향: %.2f%% (허용 범위 내)%n", responseTimeImpact);
        
        // 5. 복구 시간 종합 분석
        System.out.println("\n⏱️ 복구 시간 종합 분석");
        long totalRecoveryTime = dbRecovery.getTotalRecoveryTime() +
                               cacheRecovery.getTotalRecoveryTime() +
                               apiRecovery.getTotalRecoveryTime() +
                               memoryRecovery.getTotalRecoveryTime() +
                               networkRecovery.getTotalRecoveryTime();
        
        System.out.printf("📊 총 복구 시간: %d ms%n", totalRecoveryTime);
        System.out.printf("📊 평균 복구 시간: %d ms%n", totalRecoveryTime / 5);
        
        // 6. SLA 목표 달성 확인
        assertThat(totalRecoveryTime).isLessThan(120000); // 2분 이내 전체 복구
        System.out.println("✅ SLA 목표 (2분 이내 복구) 달성");
        
        // 7. 데이터 무결성 검증
        boolean dataIntegrityMaintained = verifyDataIntegrityAfterRecovery();
        assertThat(dataIntegrityMaintained).isTrue();
        System.out.println("✅ 장애 복구 후 데이터 무결성 유지");
        
        System.out.println("\n=== 📋 End-to-End 장애 복구 최종 결과 ===");
        System.out.println("🔄 테스트된 장애 유형: 5가지");
        System.out.println("✅ 성공한 복구: 5/5 (100%)");
        System.out.println("⏱️ 총 복구 시간: " + totalRecoveryTime + " ms");
        System.out.println("📊 성능 영향: " + String.format("%.2f%%", responseTimeImpact));
        System.out.println("🏥 최종 시스템 상태: HEALTHY");
        System.out.println("🔒 데이터 무결성: 유지");
        System.out.println("💼 비즈니스 연속성: 보장");
        
        System.out.println("\n=== 🎉 시스템 복원력(Resilience) 검증 완료 ===");
        System.out.println("RoutePickr 시스템은 모든 주요 장애 시나리오에서 안정적인 복구 능력을 보여주었습니다.");
        System.out.println("자동 복구, 서킷 브레이커, Fallback 메커니즘이 모두 정상적으로 동작합니다.");
    }
    
    // Helper Methods
    private void establishHealthyBaseline() {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(healthCheckService.checkDatabaseHealth()).isTrue();
            assertThat(healthCheckService.checkRedisHealth()).isTrue();
            assertThat(healthCheckService.checkExternalApiHealth()).isTrue();
        });
    }
    
    private void restoreSystemToHealthyState() {
        failureRecoveryService.restoreAllSystemsToHealthyState();
        monitoringService.clearAllFailureFlags();
    }
    
    private boolean verifyDataConsistency() {
        // 데이터 무결성 검증 로직
        return userService.verifyUserDataConsistency() &&
               messageService.verifyMessageDataConsistency() &&
               paymentService.verifyPaymentDataConsistency();
    }
    
    private long measureResponseTime(Runnable operation) {
        long startTime = System.currentTimeMillis();
        operation.run();
        return System.currentTimeMillis() - startTime;
    }
    
    private void simulateHighMemoryUsage() {
        // 메모리 사용량 증가 시뮬레이션 (테스트용)
        monitoringService.simulateHighMemoryPressure();
    }
    
    private void simulateHighCpuUsage() {
        // CPU 사용량 증가 시뮬레이션 (테스트용)
        monitoringService.simulateHighCpuUsage();
    }
    
    private void simulateSystemResourceExhaustion() {
        // 전체 시스템 리소스 고갈 시뮬레이션
        monitoringService.simulateResourceExhaustion();
    }
    
    private void simulateNetworkRecovery() {
        // 네트워크 복구 시뮬레이션
        monitoringService.simulateNetworkRecovery();
    }
    
    private MessageCreateRequest createTestMessage() {
        return MessageCreateRequest.builder()
                .senderId(1L)
                .receiverId(2L)
                .messageType("PERSONAL")
                .title("테스트 메시지")
                .content("장애 복구 테스트용 메시지입니다.")
                .build();
    }
    
    private Map<String, Object> captureSystemMetrics() {
        return Map.of(
                "responseTime", monitoringService.getAverageResponseTime(),
                "throughput", monitoringService.getCurrentThroughput(),
                "errorRate", monitoringService.getCurrentErrorRate(),
                "memoryUsage", monitoringService.getMemoryUsagePercentage(),
                "cpuUsage", monitoringService.getCpuUsagePercentage()
        );
    }
    
    private double calculatePerformanceImpact(Map<String, Object> initial, Map<String, Object> final) {
        double initialResponseTime = (Double) initial.get("responseTime");
        double finalResponseTime = (Double) final.get("responseTime");
        
        return ((finalResponseTime - initialResponseTime) / initialResponseTime) * 100;
    }
    
    private boolean verifyDataIntegrityAfterRecovery() {
        // 장애 복구 후 데이터 무결성 검증
        return userService.verifyUserDataIntegrity() &&
               messageService.verifyMessageDataIntegrity() &&
               paymentService.verifyPaymentDataIntegrity();
    }
}
```

---

*분할된 파일: step9-6d2_e2e_failure_recovery_test.md → step9-6d2b_failure_recovery_test_scenarios.md*  
*내용: End-to-End 장애 복구 테스트 시나리오 및 테스트 클래스*  
*라인 수: 726줄*