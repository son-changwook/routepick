# Step 6-6d2: HealthCheck Service 구현

## 📋 구현 목표
- **종합 헬스체크**: 데이터베이스, Redis, 외부 API 상태 검증
- **Spring Boot Actuator 통합**: /actuator/health 엔드포인트 구현
- **응답시간 측정**: 각 컴포넌트별 응답시간 모니터링
- **장애 감지**: 서비스 장애 자동 감지 및 알림

## 🏥 HealthCheckService 구현

### HealthCheckService.java
```java
package com.routepick.backend.service.system;

import com.routepick.backend.dto.system.HealthCheckDto;
import com.routepick.backend.dto.system.SystemStatusDto;
import com.routepick.backend.service.cache.CacheService;
import com.routepick.backend.service.system.ExternalApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 시스템 헬스체크 서비스
 * - 데이터베이스, Redis, 외부 API 상태 확인
 * - Spring Boot Actuator 헬스 인디케이터 구현
 * - 응답시간 기반 성능 모니터링
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService implements HealthIndicator {
    
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExternalApiService externalApiService;
    private final CacheService cacheService;
    
    // 응답시간 임계치 (밀리초)
    private static final long DB_RESPONSE_THRESHOLD = 2000L; // 2초
    private static final long REDIS_RESPONSE_THRESHOLD = 1000L; // 1초
    private static final long API_RESPONSE_THRESHOLD = 5000L; // 5초
    
    /**
     * Spring Boot Actuator 헬스체크 구현
     */
    @Override
    public Health health() {
        try {
            SystemStatusDto systemStatus = performComprehensiveHealthCheck();
            
            Map<String, Object> details = new HashMap<>();
            details.put("database", systemStatus.getDatabaseHealth());
            details.put("redis", systemStatus.getRedisHealth());
            details.put("externalApis", systemStatus.getExternalApiHealth());
            details.put("overall", systemStatus.getOverallStatus());
            details.put("checkedAt", systemStatus.getCheckedAt());
            
            if (systemStatus.isHealthy()) {
                return Health.up()
                        .withDetails(details)
                        .build();
            } else {
                return Health.down()
                        .withDetails(details)
                        .build();
            }
            
        } catch (Exception e) {
            log.error("헬스체크 실패", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("checkedAt", LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * 종합 헬스체크 수행
     */
    public SystemStatusDto performComprehensiveHealthCheck() {
        try {
            // 병렬로 각 컴포넌트 헬스체크 실행
            CompletableFuture<HealthCheckDto> dbHealthFuture = 
                    CompletableFuture.supplyAsync(this::checkDatabaseHealth);
            
            CompletableFuture<HealthCheckDto> redisHealthFuture = 
                    CompletableFuture.supplyAsync(this::checkRedisHealth);
            
            CompletableFuture<HealthCheckDto> apiHealthFuture = 
                    CompletableFuture.supplyAsync(this::checkExternalApiHealth);
            
            // 모든 헬스체크 완료까지 최대 10초 대기
            CompletableFuture<Void> allChecks = CompletableFuture.allOf(
                    dbHealthFuture, redisHealthFuture, apiHealthFuture);
            
            allChecks.get(10, TimeUnit.SECONDS);
            
            // 결과 수집
            HealthCheckDto dbHealth = dbHealthFuture.get();
            HealthCheckDto redisHealth = redisHealthFuture.get();
            HealthCheckDto apiHealth = apiHealthFuture.get();
            
            // 전체 상태 판정
            boolean isHealthy = dbHealth.isHealthy() && redisHealth.isHealthy() && 
                               (apiHealth.isHealthy() || "DEGRADED".equals(apiHealth.getStatus()));
            
            String overallStatus = determineOverallStatus(dbHealth, redisHealth, apiHealth);
            
            return SystemStatusDto.builder()
                    .isHealthy(isHealthy)
                    .overallStatus(overallStatus)
                    .databaseHealth(dbHealth)
                    .redisHealth(redisHealth)
                    .externalApiHealth(apiHealth)
                    .checkedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("종합 헬스체크 실패", e);
            
            return SystemStatusDto.builder()
                    .isHealthy(false)
                    .overallStatus("DOWN")
                    .databaseHealth(createErrorHealthCheck("Database check failed: " + e.getMessage()))
                    .redisHealth(createErrorHealthCheck("Redis check failed: " + e.getMessage()))
                    .externalApiHealth(createErrorHealthCheck("API check failed: " + e.getMessage()))
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * 데이터베이스 헬스체크
     */
    public HealthCheckDto checkDatabaseHealth() {
        long startTime = System.currentTimeMillis();
        
        try {
            // 데이터베이스 연결 및 간단한 쿼리 실행
            try (Connection connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(5); // 5초 타임아웃
                long responseTime = System.currentTimeMillis() - startTime;
                
                if (isValid && responseTime < DB_RESPONSE_THRESHOLD) {
                    return HealthCheckDto.builder()
                            .status("UP")
                            .isHealthy(true)
                            .responseTimeMs(responseTime)
                            .message("Database connection successful")
                            .details(Map.of(
                                    "url", getMaskedDatabaseUrl(connection),
                                    "autoCommit", connection.getAutoCommit(),
                                    "catalog", connection.getCatalog() != null ? connection.getCatalog() : "N/A"
                            ))
                            .checkedAt(LocalDateTime.now())
                            .build();
                } else {
                    String message = !isValid ? "Database connection validation failed" : 
                                   String.format("Database response time too slow: %dms", responseTime);
                    
                    return HealthCheckDto.builder()
                            .status("DOWN")
                            .isHealthy(false)
                            .responseTimeMs(responseTime)
                            .message(message)
                            .checkedAt(LocalDateTime.now())
                            .build();
                }
            }
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("데이터베이스 헬스체크 실패", e);
            
            return HealthCheckDto.builder()
                    .status("DOWN")
                    .isHealthy(false)
                    .responseTimeMs(responseTime)
                    .message("Database connection error: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Redis 헬스체크
     */
    public HealthCheckDto checkRedisHealth() {
        long startTime = System.currentTimeMillis();
        
        try {
            // Redis 연결 상태 확인 및 간단한 읽기/쓰기 테스트
            String testKey = "health:check:" + System.currentTimeMillis();
            String testValue = "health-test";
            
            // 데이터 쓰기
            redisTemplate.opsForValue().set(testKey, testValue);
            
            // 데이터 읽기
            String result = (String) redisTemplate.opsForValue().get(testKey);
            
            // 테스트 키 삭제
            redisTemplate.delete(testKey);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (testValue.equals(result) && responseTime < REDIS_RESPONSE_THRESHOLD) {
                // Redis 추가 정보 수집
                Map<String, Object> details = collectRedisDetails();
                
                return HealthCheckDto.builder()
                        .status("UP")
                        .isHealthy(true)
                        .responseTimeMs(responseTime)
                        .message("Redis connection and operations successful")
                        .details(details)
                        .checkedAt(LocalDateTime.now())
                        .build();
            } else {
                String message = !testValue.equals(result) ? 
                        "Redis data consistency check failed" : 
                        String.format("Redis response time too slow: %dms", responseTime);
                
                return HealthCheckDto.builder()
                        .status("DOWN")
                        .isHealthy(false)
                        .responseTimeMs(responseTime)
                        .message(message)
                        .checkedAt(LocalDateTime.now())
                        .build();
            }
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("Redis 헬스체크 실패", e);
            
            return HealthCheckDto.builder()
                    .status("DOWN")
                    .isHealthy(false)
                    .responseTimeMs(responseTime)
                    .message("Redis connection error: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * 외부 API 헬스체크
     */
    public HealthCheckDto checkExternalApiHealth() {
        long startTime = System.currentTimeMillis();
        
        try {
            // 외부 API 상태 확인
            List<Map<String, Object>> apiHealthStatuses = externalApiService.getAllApiHealthStatus();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (responseTime > API_RESPONSE_THRESHOLD) {
                return HealthCheckDto.builder()
                        .status("DOWN")
                        .isHealthy(false)
                        .responseTimeMs(responseTime)
                        .message("External API health check timeout")
                        .checkedAt(LocalDateTime.now())
                        .build();
            }
            
            long totalApis = apiHealthStatuses.size();
            long healthyApis = apiHealthStatuses.stream()
                    .mapToLong(status -> Boolean.TRUE.equals(status.get("isHealthy")) ? 1 : 0)
                    .sum();
            
            double healthyRatio = totalApis > 0 ? (double) healthyApis / totalApis : 1.0;
            
            Map<String, Object> details = new HashMap<>();
            details.put("totalApis", totalApis);
            details.put("healthyApis", healthyApis);
            details.put("healthyRatio", Math.round(healthyRatio * 100) + "%");
            details.put("apiStatuses", apiHealthStatuses);
            
            if (healthyRatio >= 0.8) { // 80% 이상 정상
                return HealthCheckDto.builder()
                        .status("UP")
                        .isHealthy(true)
                        .responseTimeMs(responseTime)
                        .message(String.format("External APIs healthy: %d/%d", healthyApis, totalApis))
                        .details(details)
                        .checkedAt(LocalDateTime.now())
                        .build();
            } else if (healthyRatio >= 0.5) { // 50% 이상 정상
                return HealthCheckDto.builder()
                        .status("DEGRADED")
                        .isHealthy(false)
                        .responseTimeMs(responseTime)
                        .message(String.format("Some external APIs degraded: %d/%d healthy", healthyApis, totalApis))
                        .details(details)
                        .checkedAt(LocalDateTime.now())
                        .build();
            } else {
                return HealthCheckDto.builder()
                        .status("DOWN")
                        .isHealthy(false)
                        .responseTimeMs(responseTime)
                        .message(String.format("Most external APIs down: %d/%d healthy", healthyApis, totalApis))
                        .details(details)
                        .checkedAt(LocalDateTime.now())
                        .build();
            }
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("외부 API 헬스체크 실패", e);
            
            return HealthCheckDto.builder()
                    .status("DOWN")
                    .isHealthy(false)
                    .responseTimeMs(responseTime)
                    .message("External API health check failed: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * 캐시 시스템 헬스체크
     */
    public HealthCheckDto checkCacheHealth() {
        long startTime = System.currentTimeMillis();
        
        try {
            // 캐시 서비스를 통한 상태 확인
            Map<String, Object> cacheStats = cacheService.getCacheStatistics();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 캐시 히트율 확인 (70% 이상이면 정상)
            Double hitRate = (Double) cacheStats.get("hitRate");
            boolean isCacheHealthy = hitRate != null && hitRate >= 0.7;
            
            return HealthCheckDto.builder()
                    .status(isCacheHealthy ? "UP" : "DEGRADED")
                    .isHealthy(isCacheHealthy)
                    .responseTimeMs(responseTime)
                    .message(String.format("Cache hit rate: %.1f%%", hitRate != null ? hitRate * 100 : 0))
                    .details(cacheStats)
                    .checkedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("캐시 헬스체크 실패", e);
            
            return HealthCheckDto.builder()
                    .status("DOWN")
                    .isHealthy(false)
                    .responseTimeMs(responseTime)
                    .message("Cache health check failed: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * 전체 상태 판정
     */
    private String determineOverallStatus(HealthCheckDto dbHealth, HealthCheckDto redisHealth, HealthCheckDto apiHealth) {
        // 핵심 시스템 (DB, Redis) 중 하나라도 DOWN이면 전체 DOWN
        if (!dbHealth.isHealthy() || !redisHealth.isHealthy()) {
            return "DOWN";
        }
        
        // 외부 API가 DEGRADED면 전체 DEGRADED
        if ("DEGRADED".equals(apiHealth.getStatus())) {
            return "DEGRADED";
        }
        
        // 외부 API가 DOWN이어도 핵심 시스템이 정상이면 DEGRADED
        if (!apiHealth.isHealthy()) {
            return "DEGRADED";
        }
        
        return "UP";
    }
    
    /**
     * Redis 세부 정보 수집
     */
    private Map<String, Object> collectRedisDetails() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // Redis 정보 수집 (가능한 정보만)
            details.put("connected", true);
            details.put("testResult", "PASS");
            
            // 캐시 통계가 있다면 포함
            if (cacheService != null) {
                Map<String, Object> cacheStats = cacheService.getCacheStatistics();
                details.put("cacheStats", cacheStats);
            }
            
        } catch (Exception e) {
            details.put("error", "Could not collect Redis details: " + e.getMessage());
        }
        
        return details;
    }
    
    /**
     * 데이터베이스 URL 마스킹
     */
    private String getMaskedDatabaseUrl(Connection connection) {
        try {
            String url = connection.getMetaData().getURL();
            if (url != null && url.contains("@")) {
                // 패스워드 부분 마스킹
                return url.replaceAll("//[^@]*@", "//***:***@");
            }
            return url;
        } catch (Exception e) {
            return "URL not available";
        }
    }
    
    /**
     * 에러 헬스체크 DTO 생성
     */
    private HealthCheckDto createErrorHealthCheck(String message) {
        return HealthCheckDto.builder()
                .status("DOWN")
                .isHealthy(false)
                .responseTimeMs(0L)
                .message(message)
                .checkedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 간단한 헬스체크 (빠른 확인용)
     */
    public boolean isSystemHealthy() {
        try {
            // 간단한 체크만 수행 (타임아웃 1초)
            CompletableFuture<Boolean> quickCheck = CompletableFuture.supplyAsync(() -> {
                try {
                    // 데이터베이스 간단 체크
                    try (Connection connection = dataSource.getConnection()) {
                        if (!connection.isValid(1)) {
                            return false;
                        }
                    }
                    
                    // Redis 간단 체크
                    redisTemplate.opsForValue().get("health:quick:check");
                    
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
            
            return quickCheck.get(2, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.debug("Quick health check failed: {}", e.getMessage());
            return false;
        }
    }
}
```

## 📊 HealthCheck DTO

### HealthCheckDto.java
```java
package com.routepick.backend.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class HealthCheckDto {
    
    /**
     * 헬스체크 상태 (UP, DOWN, DEGRADED)
     */
    private String status;
    
    /**
     * 정상 여부
     */
    private boolean isHealthy;
    
    /**
     * 응답 시간 (밀리초)
     */
    private Long responseTimeMs;
    
    /**
     * 상태 메시지
     */
    private String message;
    
    /**
     * 세부 정보
     */
    private Map<String, Object> details;
    
    /**
     * 검사 시간
     */
    private LocalDateTime checkedAt;
}
```

### SystemStatusDto.java
```java
package com.routepick.backend.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SystemStatusDto {
    
    /**
     * 전체 시스템 정상 여부
     */
    private boolean isHealthy;
    
    /**
     * 전체 상태 (UP, DOWN, DEGRADED)
     */
    private String overallStatus;
    
    /**
     * 데이터베이스 헬스체크 결과
     */
    private HealthCheckDto databaseHealth;
    
    /**
     * Redis 헬스체크 결과
     */
    private HealthCheckDto redisHealth;
    
    /**
     * 외부 API 헬스체크 결과
     */
    private HealthCheckDto externalApiHealth;
    
    /**
     * 캐시 시스템 헬스체크 결과
     */
    private HealthCheckDto cacheHealth;
    
    /**
     * 검사 시간
     */
    private LocalDateTime checkedAt;
}
```

## ⚙️ 설정

### application.yml
```yaml
# Actuator 헬스체크 설정
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
      show-components: always
  health:
    defaults:
      enabled: true
    db:
      enabled: true
    redis:
      enabled: true

# 헬스체크 임계치
app:
  health:
    thresholds:
      database-response-ms: 2000
      redis-response-ms: 1000
      api-response-ms: 5000
    external-api:
      healthy-ratio-threshold: 0.8
      degraded-ratio-threshold: 0.5
```

---

**다음 파일**: step6-6d3_backup_management.md (백업 관리 서비스)  
**연관 시스템**: Spring Boot Actuator, ExternalApiService, CacheService  
**성능 목표**: 전체 헬스체크 10초 이내, 빠른 체크 2초 이내

*생성일: 2025-09-02*  
*RoutePickr 6-6d2: 종합 헬스체크 시스템 완성*