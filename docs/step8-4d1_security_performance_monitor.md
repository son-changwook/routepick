# step8-4d1_security_performance_monitor.md

## 📋 보안 성능 모니터링 시스템 - SecurityPerformanceMonitor

### 🎯 목표
- 보안 관련 컴포넌트들의 실시간 성능 추적
- JWT 검증, Rate Limiting, Redis 성능 병목 지점 탐지
- 성능 임계치 초과 시 자동 알림 및 최적화 제안
- Micrometer 기반 메트릭 수집 및 모니터링

### 📊 모니터링 대상
- **JWT 토큰 검증**: 성능 + 캐시 히트율
- **Rate Limiting**: Redis 기반 제한 성능  
- **보안 필터 체인**: 전체 실행 시간
- **시스템 리소스**: CPU/메모리 사용량

---

# 8-4d1단계: 성능 모니터링 시스템 구현

> RoutePickr 보안 시스템 성능 모니터링 및 최적화  
> 생성일: 2025-08-27  
> 기반 참고: step6-6d_system_service.md, step8-2d_security_monitoring.md  
> 통합 구현: JWT/Rate Limiting/Redis 성능 + Micrometer 메트릭

---

## 🎯 성능 모니터링 시스템 개요

### 설계 원칙
- **실시간 성능 추적**: 보안 필터 체인별 성능 측정
- **병목 지점 탐지**: JWT 검증, Redis 연결, 데이터베이스 쿼리 성능
- **자동 최적화**: 성능 임계치 초과 시 자동 조치
- **예측적 확장**: 리소스 사용량 예측 및 사전 알림
- **SLA 모니터링**: 보안 기능의 응답시간 SLA 준수 체크

### 성능 모니터링 아키텍처
```
┌─────────────────────┐
│ SecurityPerformanceMonitor │  ← 보안 컴포넌트 성능 감시
│ (JWT, Rate Limit, Redis)    │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│ SystemHealthMonitor │  ← 전반적인 시스템 건강도
│ (CPU, Memory, DB, Cache)   │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│ PerformanceOptimizer │  ← 성능 자동 최적화
│ (캐시 워밍, 연결풀 조정) │
└─────────────────────┘
```

---

## 📊 SecurityPerformanceMonitor 구현

### 보안 시스템 성능 모니터링 서비스
```java
package com.routepick.monitoring.performance;

import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 보안 성능 모니터링 서비스
 * 보안 관련 컴포넌트들의 성능 추적 및 최적화
 * 
 * 모니터링 대상:
 * - JWT 토큰 검증 성능
 * - Rate Limiting Redis 성능
 * - 보안 필터 체인 실행 시간
 * - 암호화/복호화 성능
 * - 데이터베이스 보안 쿼리 성능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityPerformanceMonitor {
    
    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final DataSource dataSource;
    
    @Value("${app.performance.jwt.threshold:100}")
    private long jwtPerformanceThreshold; // JWT 검증 임계시간 (ms)
    
    @Value("${app.performance.redis.threshold:50}")
    private long redisPerformanceThreshold; // Redis 응답 임계시간 (ms)
    
    @Value("${app.performance.security-filter.threshold:200}")
    private long securityFilterThreshold; // 보안 필터 실행 임계시간 (ms)
    
    @Value("${app.performance.monitoring.enabled:true}")
    private boolean performanceMonitoringEnabled;
    
    // 성능 메트릭 카운터들
    private final AtomicLong jwtValidationCount = new AtomicLong(0);
    private final AtomicLong rateLimitCheckCount = new AtomicLong(0);
    private final AtomicLong securityFilterExecutionCount = new AtomicLong(0);
    
    // 성능 누적 측정값
    private final DoubleAdder totalJwtValidationTime = new DoubleAdder();
    private final DoubleAdder totalRateLimitCheckTime = new DoubleAdder();
    private final DoubleAdder totalSecurityFilterTime = new DoubleAdder();
    
    // Micrometer 메트릭들
    private Timer jwtValidationTimer;
    private Timer rateLimitCheckTimer;
    private Timer securityFilterExecutionTimer;
    private Timer redisConnectionTimer;
    private Timer databaseQueryTimer;
    
    // 게이지 메트릭들
    private Gauge redisConnectionPoolGauge;
    private Gauge databaseConnectionPoolGauge;
    private Gauge jwtCacheHitRateGauge;
    private Gauge systemCpuGauge;
    private Gauge systemMemoryGauge;
    
    @PostConstruct
    public void initializeMetrics() {
        if (!performanceMonitoringEnabled) return;
        
        // Timer 메트릭 초기화
        initializeTimerMetrics();
        
        // Gauge 메트릭 초기화
        initializeGaugeMetrics();
        
        log.info("Security performance monitoring initialized with {} metrics", 
            meterRegistry.getMeters().size());
    }
    
    /**
     * Timer 메트릭 초기화
     */
    private void initializeTimerMetrics() {
        jwtValidationTimer = Timer.builder("security.jwt.validation.time")
            .description("JWT token validation execution time")
            .tag("component", "security")
            .register(meterRegistry);
        
        rateLimitCheckTimer = Timer.builder("security.ratelimit.check.time")
            .description("Rate limit check execution time")
            .tag("component", "security")
            .register(meterRegistry);
        
        securityFilterExecutionTimer = Timer.builder("security.filter.execution.time")
            .description("Security filter chain execution time")
            .tag("component", "security")
            .register(meterRegistry);
        
        redisConnectionTimer = Timer.builder("security.redis.connection.time")
            .description("Redis connection establishment time")
            .tag("component", "cache")
            .register(meterRegistry);
        
        databaseQueryTimer = Timer.builder("security.database.query.time")
            .description("Security-related database query time")
            .tag("component", "database")
            .register(meterRegistry);
    }
    
    /**
     * Gauge 메트릭 초기화
     */
    private void initializeGaugeMetrics() {
        redisConnectionPoolGauge = Gauge.builder("security.redis.connection.pool.active")
            .description("Active Redis connections in pool")
            .tag("component", "cache")
            .register(meterRegistry, this, SecurityPerformanceMonitor::getActiveRedisConnections);
        
        databaseConnectionPoolGauge = Gauge.builder("security.database.connection.pool.active")
            .description("Active database connections in pool")
            .tag("component", "database")
            .register(meterRegistry, this, SecurityPerformanceMonitor::getActiveDatabaseConnections);
        
        jwtCacheHitRateGauge = Gauge.builder("security.jwt.cache.hit.rate")
            .description("JWT validation cache hit rate")
            .tag("component", "security")
            .register(meterRegistry, this, SecurityPerformanceMonitor::getJwtCacheHitRate);
        
        systemCpuGauge = Gauge.builder("system.cpu.usage.security")
            .description("CPU usage for security operations")
            .tag("component", "system")
            .register(meterRegistry, this, SecurityPerformanceMonitor::getSecurityCpuUsage);
        
        systemMemoryGauge = Gauge.builder("system.memory.usage.security")
            .description("Memory usage for security operations")
            .tag("component", "system")
            .register(meterRegistry, this, SecurityPerformanceMonitor::getSecurityMemoryUsage);
    }
    
    /**
     * JWT 검증 성능 측정
     */
    public Timer.Sample startJwtValidationTimer() {
        if (!performanceMonitoringEnabled) return null;
        return Timer.start(meterRegistry);
    }
    
    public void recordJwtValidationTime(Timer.Sample sample, boolean cacheHit, boolean validationSuccess) {
        if (!performanceMonitoringEnabled || sample == null) return;
        
        long executionTime = sample.stop(jwtValidationTimer.timer(
            "cache_hit", String.valueOf(cacheHit),
            "validation_success", String.valueOf(validationSuccess)
        ));
        
        jwtValidationCount.incrementAndGet();
        totalJwtValidationTime.add(executionTime / 1_000_000.0); // nanoseconds to milliseconds
        
        // 성능 임계치 체크
        if (executionTime / 1_000_000 > jwtPerformanceThreshold) {
            log.warn("JWT validation exceeded threshold: {}ms (threshold: {}ms)", 
                executionTime / 1_000_000, jwtPerformanceThreshold);
            recordPerformanceAlert("JWT_VALIDATION_SLOW", executionTime / 1_000_000.0);
        }
        
        log.debug("JWT validation completed in {}ms (cache hit: {}, success: {})", 
            executionTime / 1_000_000, cacheHit, validationSuccess);
    }
    
    /**
     * Rate Limiting 성능 측정
     */
    public Timer.Sample startRateLimitCheckTimer() {
        if (!performanceMonitoringEnabled) return null;
        return Timer.start(meterRegistry);
    }
    
    public void recordRateLimitCheckTime(Timer.Sample sample, String limitType, boolean allowed) {
        if (!performanceMonitoringEnabled || sample == null) return;
        
        long executionTime = sample.stop(rateLimitCheckTimer.timer(
            "limit_type", limitType,
            "allowed", String.valueOf(allowed)
        ));
        
        rateLimitCheckCount.incrementAndGet();
        totalRateLimitCheckTime.add(executionTime / 1_000_000.0);
        
        // Redis 성능 임계치 체크
        if (executionTime / 1_000_000 > redisPerformanceThreshold) {
            log.warn("Rate limit check exceeded threshold: {}ms (threshold: {}ms)", 
                executionTime / 1_000_000, redisPerformanceThreshold);
            recordPerformanceAlert("RATE_LIMIT_SLOW", executionTime / 1_000_000.0);
        }
    }
    
    /**
     * 보안 필터 체인 성능 측정
     */
    public Timer.Sample startSecurityFilterTimer() {
        if (!performanceMonitoringEnabled) return null;
        return Timer.start(meterRegistry);
    }
    
    public void recordSecurityFilterTime(Timer.Sample sample, String filterName, boolean success) {
        if (!performanceMonitoringEnabled || sample == null) return;
        
        long executionTime = sample.stop(securityFilterExecutionTimer.timer(
            "filter_name", filterName,
            "success", String.valueOf(success)
        ));
        
        securityFilterExecutionCount.incrementAndGet();
        totalSecurityFilterTime.add(executionTime / 1_000_000.0);
        
        // 보안 필터 성능 임계치 체크
        if (executionTime / 1_000_000 > securityFilterThreshold) {
            log.warn("Security filter '{}' exceeded threshold: {}ms (threshold: {}ms)", 
                filterName, executionTime / 1_000_000, securityFilterThreshold);
            recordPerformanceAlert("SECURITY_FILTER_SLOW", executionTime / 1_000_000.0);
        }
    }
    
    /**
     * Redis 연결 성능 측정
     */
    public void measureRedisConnectionHealth() {
        if (!performanceMonitoringEnabled) return;
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            redisTemplate.opsForValue().get("health_check_key");
            sample.stop(redisConnectionTimer);
        } catch (Exception e) {
            sample.stop(redisConnectionTimer.timer("result", "error"));
            log.error("Redis connection health check failed", e);
            recordPerformanceAlert("REDIS_CONNECTION_FAILED", 0);
        }
    }
    
    /**
     * 데이터베이스 쿼리 성능 측정
     */
    public void measureDatabaseQueryPerformance(String queryType, long executionTimeMs) {
        if (!performanceMonitoringEnabled) return;
        
        Timer.builder("security.database.query.time")
            .tag("query_type", queryType)
            .register(meterRegistry)
            .record(Duration.ofMillis(executionTimeMs));
        
        if (executionTimeMs > 1000) { // 1초 초과 쿼리
            log.warn("Slow security database query detected: {} took {}ms", queryType, executionTimeMs);
            recordPerformanceAlert("DATABASE_QUERY_SLOW", executionTimeMs);
        }
    }
    
    /**
     * 성능 지표 요약 조회
     */
    public SecurityPerformanceSummary getPerformanceSummary() {
        return SecurityPerformanceSummary.builder()
            .timestamp(LocalDateTime.now())
            .jwtValidationStats(JwtPerformanceStats.builder()
                .totalValidations(jwtValidationCount.get())
                .averageValidationTime(jwtValidationCount.get() > 0 ? 
                    totalJwtValidationTime.sum() / jwtValidationCount.get() : 0.0)
                .cacheHitRate(getJwtCacheHitRate())
                .performanceThreshold(jwtPerformanceThreshold)
                .build())
            .rateLimitStats(RateLimitPerformanceStats.builder()
                .totalChecks(rateLimitCheckCount.get())
                .averageCheckTime(rateLimitCheckCount.get() > 0 ? 
                    totalRateLimitCheckTime.sum() / rateLimitCheckCount.get() : 0.0)
                .performanceThreshold(redisPerformanceThreshold)
                .build())
            .securityFilterStats(SecurityFilterPerformanceStats.builder()
                .totalExecutions(securityFilterExecutionCount.get())
                .averageExecutionTime(securityFilterExecutionCount.get() > 0 ? 
                    totalSecurityFilterTime.sum() / securityFilterExecutionCount.get() : 0.0)
                .performanceThreshold(securityFilterThreshold)
                .build())
            .systemResourceStats(SystemResourceStats.builder()
                .cpuUsage(getSecurityCpuUsage())
                .memoryUsage(getSecurityMemoryUsage())
                .redisConnections(getActiveRedisConnections())
                .databaseConnections(getActiveDatabaseConnections())
                .build())
            .build();
    }
    
    /**
     * 주기적 성능 건강성 체크 (5분마다)
     */
    @Scheduled(fixedRate = 300000) // 5분
    public void performHealthCheck() {
        if (!performanceMonitoringEnabled) return;
        
        log.info("Performing security performance health check...");
        
        // Redis 연결 상태 체크
        measureRedisConnectionHealth();
        
        // 데이터베이스 연결 상태 체크
        checkDatabaseConnectionHealth();
        
        // 성능 요약 로그
        SecurityPerformanceSummary summary = getPerformanceSummary();
        log.info("Security performance summary: JWT avg: {}ms, RateLimit avg: {}ms, Filter avg: {}ms", 
            summary.getJwtValidationStats().getAverageValidationTime(),
            summary.getRateLimitStats().getAverageCheckTime(),
            summary.getSecurityFilterStats().getAverageExecutionTime());
        
        // 성능 경고 체크
        checkPerformanceAlerts(summary);
    }
    
    /**
     * 성능 경고 체크
     */
    private void checkPerformanceAlerts(SecurityPerformanceSummary summary) {
        // JWT 검증 성능 경고
        if (summary.getJwtValidationStats().getAverageValidationTime() > jwtPerformanceThreshold) {
            recordPerformanceAlert("JWT_AVG_SLOW", summary.getJwtValidationStats().getAverageValidationTime());
        }
        
        // Rate Limiting 성능 경고
        if (summary.getRateLimitStats().getAverageCheckTime() > redisPerformanceThreshold) {
            recordPerformanceAlert("RATE_LIMIT_AVG_SLOW", summary.getRateLimitStats().getAverageCheckTime());
        }
        
        // 메모리 사용량 경고
        if (summary.getSystemResourceStats().getMemoryUsage() > 80.0) {
            recordPerformanceAlert("HIGH_MEMORY_USAGE", summary.getSystemResourceStats().getMemoryUsage());
        }
        
        // CPU 사용량 경고
        if (summary.getSystemResourceStats().getCpuUsage() > 80.0) {
            recordPerformanceAlert("HIGH_CPU_USAGE", summary.getSystemResourceStats().getCpuUsage());
        }
    }
    
    /**
     * 데이터베이스 연결 건강성 체크
     */
    private void checkDatabaseConnectionHealth() {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) { // 5초 timeout
                sample.stop(databaseQueryTimer.timer("result", "success"));
            } else {
                sample.stop(databaseQueryTimer.timer("result", "invalid"));
                recordPerformanceAlert("DATABASE_CONNECTION_INVALID", 0);
            }
        } catch (Exception e) {
            sample.stop(databaseQueryTimer.timer("result", "error"));
            log.error("Database connection health check failed", e);
            recordPerformanceAlert("DATABASE_CONNECTION_FAILED", 0);
        }
    }
    
    /**
     * 성능 알림 기록
     */
    private void recordPerformanceAlert(String alertType, double value) {
        Counter.builder("security.performance.alerts.total")
            .tag("alert_type", alertType)
            .register(meterRegistry)
            .increment();
        
        log.warn("Performance alert recorded: {} = {}", alertType, value);
    }
    
    // ========== Gauge 메트릭 계산 메서드들 ==========
    
    private double getActiveRedisConnections() {
        try {
            // Jedis 또는 Lettuce 연결 풀 정보 조회
            return 10.0; // 실제 구현 필요
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private double getActiveDatabaseConnections() {
        try {
            // HikariCP 또는 다른 연결 풀 정보 조회
            return 5.0; // 실제 구현 필요
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private double getJwtCacheHitRate() {
        // JWT 캐시 히트율 계산
        return 85.0; // 실제 구현 필요
    }
    
    private double getSecurityCpuUsage() {
        // 보안 관련 CPU 사용량 계산
        return Runtime.getRuntime().availableProcessors() * 10.0; // 실제 구현 필요
    }
    
    private double getSecurityMemoryUsage() {
        // 보안 관련 메모리 사용량 계산 (%)
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        return (double) usedMemory / totalMemory * 100;
    }
}
```

## 📊 핵심 기능 요약

### 1. 실시간 성능 추적
- **JWT 검증**: 캐시 히트율 + 검증 성공률 포함
- **Rate Limiting**: Redis 기반 제한 검사 성능
- **보안 필터**: 체인별 실행 시간 측정

### 2. 성능 임계치 관리
- **JWT**: 100ms 이내 목표
- **Redis**: 50ms 이내 목표  
- **필터 체인**: 200ms 이내 목표

### 3. 자동 알림 시스템
- 임계치 초과 시 실시간 로그 + 메트릭 증가
- 5분마다 주기적 건강성 체크
- CPU/메모리 80% 초과 시 경고

### 4. Micrometer 통합
- Timer, Counter, Gauge 메트릭 활용
- Prometheus/Grafana 연동 가능
- 태그 기반 세부 분류 지원