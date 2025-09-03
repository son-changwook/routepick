# E2E 장애 복구 시스템 아키텍처

## 개요
RoutePickr 시스템의 전체적인 장애 대응 및 복구 능력을 검증하는 종합 테스트입니다. 다양한 장애 시나리오에서 시스템의 복원력(resilience)과 자동 복구 메커니즘을 테스트합니다.

## 장애 복구 시스템 아키텍처

### 1. 장애 감지 및 복구 컴포넌트

```java
package com.routepick.system.resilience;

import com.routepick.system.resilience.dto.FailureScenario;
import com.routepick.system.resilience.dto.RecoveryResult;
import com.routepick.system.resilience.enums.FailureType;
import com.routepick.system.resilience.enums.RecoveryStrategy;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.circuit.breaker.annotation.CircuitBreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.bulkhead.BulkheadRegistry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 시스템 장애 복구 관리 서비스
 * 
 * 주요 기능:
 * - 장애 감지 및 알림
 * - 자동 복구 메커니즘
 * - 서킷 브레이커 패턴
 * - 재시도 및 백오프 전략
 * - 벌크헤드 패턴으로 격리
 */
@Service
public class FailureRecoveryService {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    @Autowired
    private RetryRegistry retryRegistry;
    
    @Autowired
    private BulkheadRegistry bulkheadRegistry;
    
    @Autowired
    private HealthCheckService healthCheckService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private BackupService backupService;
    
    /**
     * 장애 시나리오 실행 및 복구 테스트
     */
    public RecoveryResult executeFailureScenario(FailureScenario scenario) {
        RecoveryResult result = new RecoveryResult();
        result.setScenarioId(scenario.getScenarioId());
        result.setFailureType(scenario.getFailureType());
        result.setStartTime(LocalDateTime.now());
        
        try {
            // 1. 장애 시뮬레이션
            simulateFailure(scenario);
            
            // 2. 장애 감지
            boolean failureDetected = detectFailure(scenario.getFailureType());
            result.setFailureDetected(failureDetected);
            
            // 3. 자동 복구 시도
            boolean recoveryAttempted = attemptAutoRecovery(scenario);
            result.setRecoveryAttempted(recoveryAttempted);
            
            // 4. 복구 결과 검증
            boolean systemHealthy = verifySystemHealth();
            result.setRecoverySuccessful(systemHealthy);
            
            // 5. 성능 영향 측정
            Map<String, Object> performanceMetrics = measurePerformanceImpact();
            result.setPerformanceImpact(performanceMetrics);
            
            result.setEndTime(LocalDateTime.now());
            result.setTotalRecoveryTime(calculateRecoveryTime(result));
            
        } catch (Exception e) {
            result.setRecoverySuccessful(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * 데이터베이스 장애 복구
     */
    @CircuitBreaker(name = "database", fallbackMethod = "fallbackDatabaseOperation")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public RecoveryResult handleDatabaseFailure() {
        RecoveryResult result = new RecoveryResult();
        result.setFailureType(FailureType.DATABASE_CONNECTION);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // 1. 데이터베이스 연결 상태 확인
            boolean dbHealthy = healthCheckService.checkDatabaseHealth();
            
            if (!dbHealthy) {
                // 2. 연결 풀 재시작
                restartConnectionPool();
                
                // 3. 읽기 전용 모드로 전환 (가능한 경우)
                enableReadOnlyMode();
                
                // 4. 캐시 활용으로 서비스 연속성 보장
                activateEmergencyCache();
                
                // 5. 백업 데이터베이스로 페일오버
                failoverToBackupDatabase();
            }
            
            // 6. 복구 검증
            dbHealthy = healthCheckService.checkDatabaseHealth();
            result.setRecoverySuccessful(dbHealthy);
            
            if (dbHealthy) {
                // 7. 정상 모드 복원
                restoreNormalMode();
            }
            
        } catch (Exception e) {
            result.setRecoverySuccessful(false);
            result.setErrorMessage("데이터베이스 복구 실패: " + e.getMessage());
        }
        
        result.setEndTime(LocalDateTime.now());
        return result;
    }
    
    /**
     * Redis 캐시 장애 복구
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "fallbackCacheOperation")
    public RecoveryResult handleRedisFailure() {
        RecoveryResult result = new RecoveryResult();
        result.setFailureType(FailureType.CACHE_FAILURE);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // 1. Redis 연결 상태 확인
            boolean redisHealthy = healthCheckService.checkRedisHealth();
            
            if (!redisHealthy) {
                // 2. 로컬 캐시로 대체
                activateLocalCache();
                
                // 3. 캐시 없이 직접 DB 조회 모드
                enableDirectDatabaseMode();
                
                // 4. Redis 재시작 시도
                restartRedisConnection();
                
                // 5. 캐시 웜업 (재시작 성공 시)
                if (healthCheckService.checkRedisHealth()) {
                    warmupCache();
                }
            }
            
            redisHealthy = healthCheckService.checkRedisHealth();
            result.setRecoverySuccessful(redisHealthy);
            
        } catch (Exception e) {
            result.setRecoverySuccessful(false);
            result.setErrorMessage("Redis 복구 실패: " + e.getMessage());
        }
        
        result.setEndTime(LocalDateTime.now());
        return result;
    }
    
    /**
     * 외부 API 장애 복구
     */
    @CircuitBreaker(name = "external-api", fallbackMethod = "fallbackExternalApi")
    @Retryable(value = {Exception.class}, maxAttempts = 5, backoff = @Backoff(delay = 2000, multiplier = 2))
    public RecoveryResult handleExternalApiFailure() {
        RecoveryResult result = new RecoveryResult();
        result.setFailureType(FailureType.EXTERNAL_API);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // 1. 대체 API 엔드포인트 시도
            boolean alternativeApiSuccess = tryAlternativeApiEndpoints();
            
            if (!alternativeApiSuccess) {
                // 2. 캐시된 데이터 활용
                boolean cacheDataAvailable = useCachedApiData();
                
                // 3. 기본값 또는 대체 로직 사용
                if (!cacheDataAvailable) {
                    useDefaultFallbackLogic();
                }
            }
            
            // 4. 서킷 브레이커 상태 확인 및 조정
            adjustCircuitBreakerThresholds();
            
            result.setRecoverySuccessful(true);
            
        } catch (Exception e) {
            result.setRecoverySuccessful(false);
            result.setErrorMessage("외부 API 복구 실패: " + e.getMessage());
        }
        
        result.setEndTime(LocalDateTime.now());
        return result;
    }
    
    /**
     * 메모리 부족 장애 복구
     */
    public RecoveryResult handleMemoryShortage() {
        RecoveryResult result = new RecoveryResult();
        result.setFailureType(FailureType.MEMORY_SHORTAGE);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // 1. 메모리 사용량 분석
            Map<String, Long> memoryAnalysis = analyzeMemoryUsage();
            
            // 2. 불필요한 캐시 정리
            clearNonEssentialCache();
            
            // 3. 메모리 집약적 작업 제한
            enableMemoryThrottling();
            
            // 4. 가비지 컬렉션 강제 실행
            System.gc();
            
            // 5. 메모리 회복 확인
            boolean memoryRecovered = checkMemoryRecovery();
            result.setRecoverySuccessful(memoryRecovered);
            
            if (!memoryRecovered) {
                // 6. 긴급 조치: 일부 서비스 임시 중단
                temporarilySuspendNonCriticalServices();
            }
            
        } catch (Exception e) {
            result.setRecoverySuccessful(false);
            result.setErrorMessage("메모리 복구 실패: " + e.getMessage());
        }
        
        result.setEndTime(LocalDateTime.now());
        return result;
    }
    
    /**
     * 네트워크 분할 장애 복구
     */
    public RecoveryResult handleNetworkPartition() {
        RecoveryResult result = new RecoveryResult();
        result.setFailureType(FailureType.NETWORK_PARTITION);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // 1. 네트워크 연결 상태 진단
            Map<String, Boolean> networkStatus = diagnoseNetworkConnectivity();
            
            // 2. 로컬 모드로 전환
            enableLocalOperationMode();
            
            // 3. 데이터 동기화 지연 처리
            handleDataSyncDelay();
            
            // 4. 충돌 해결 준비
            prepareConflictResolution();
            
            // 5. 네트워크 복구 대기 및 재연결
            waitForNetworkRecovery();
            
            // 6. 데이터 일관성 복원
            restoreDataConsistency();
            
            result.setRecoverySuccessful(true);
            
        } catch (Exception e) {
            result.setRecoverySuccessful(false);
            result.setErrorMessage("네트워크 분할 복구 실패: " + e.getMessage());
        }
        
        result.setEndTime(LocalDateTime.now());
        return result;
    }
    
    // Helper Methods
    private void simulateFailure(FailureScenario scenario) {
        switch (scenario.getFailureType()) {
            case DATABASE_CONNECTION -> simulateDatabaseFailure();
            case CACHE_FAILURE -> simulateRedisFailure();
            case EXTERNAL_API -> simulateApiFailure();
            case MEMORY_SHORTAGE -> simulateMemoryPressure();
            case NETWORK_PARTITION -> simulateNetworkPartition();
        }
    }
    
    private boolean detectFailure(FailureType failureType) {
        return switch (failureType) {
            case DATABASE_CONNECTION -> !healthCheckService.checkDatabaseHealth();
            case CACHE_FAILURE -> !healthCheckService.checkRedisHealth();
            case EXTERNAL_API -> !healthCheckService.checkExternalApiHealth();
            case MEMORY_SHORTAGE -> checkMemoryPressure();
            case NETWORK_PARTITION -> checkNetworkPartition();
        };
    }
    
    private long calculateRecoveryTime(RecoveryResult result) {
        return java.time.Duration.between(result.getStartTime(), result.getEndTime()).toMillis();
    }
    
    // Fallback Methods
    public RecoveryResult fallbackDatabaseOperation(Exception e) {
        return RecoveryResult.builder()
                .failureType(FailureType.DATABASE_CONNECTION)
                .recoverySuccessful(false)
                .errorMessage("데이터베이스 Fallback: " + e.getMessage())
                .build();
    }
    
    public RecoveryResult fallbackCacheOperation(Exception e) {
        return RecoveryResult.builder()
                .failureType(FailureType.CACHE_FAILURE)
                .recoverySuccessful(true) // 캐시는 fallback 가능
                .errorMessage("캐시 Fallback 활성화")
                .build();
    }
    
    public RecoveryResult fallbackExternalApi(Exception e) {
        return RecoveryResult.builder()
                .failureType(FailureType.EXTERNAL_API)
                .recoverySuccessful(true) // 기본값 사용
                .errorMessage("외부 API Fallback 활성화")
                .build();
    }
}
```

---

*분할된 파일: step9-6d2_e2e_failure_recovery_test.md → step9-6d2a_failure_recovery_system.md*  
*내용: 장애 복구 시스템 아키텍처 및 FailureRecoveryService 구현*  
*라인 수: 359줄*