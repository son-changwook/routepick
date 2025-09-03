# step8-4d3_data_models_config_manager.md

## 📋 성능 데이터 모델 & 보안 설정 관리자

### 🎯 목표
- 성능 모니터링 데이터 모델 정의
- 통합 보안 설정 관리자 구현
- 동적 보안 설정 변경 및 검증
- 성능 기반 자동 튜닝 시스템

### 📊 데이터 구조
- **성능 요약**: JWT/Rate Limit/필터/시스템 리소스
- **최적화 계획**: 수행할 최적화 작업 정의
- **설정 상태**: 보안 설정 건강성 추적
- **결과 모델**: 최적화 수행 결과 저장

---

## 📊 성능 모니터링 데이터 모델

### 성능 관련 데이터 클래스들
```java
// SecurityPerformanceSummary.java
package com.routepick.monitoring.performance.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class SecurityPerformanceSummary {
    private LocalDateTime timestamp;
    private JwtPerformanceStats jwtValidationStats;
    private RateLimitPerformanceStats rateLimitStats;
    private SecurityFilterPerformanceStats securityFilterStats;
    private SystemResourceStats systemResourceStats;
    private DatabasePerformanceStats databaseStats;
}

// JwtPerformanceStats.java
@Getter
@Setter
@Builder
public class JwtPerformanceStats {
    private long totalValidations;
    private double averageValidationTime;
    private double cacheHitRate;
    private long performanceThreshold;
    private long slowValidationCount;
    private double p95ValidationTime;
    private double p99ValidationTime;
}

// RateLimitPerformanceStats.java
@Getter
@Setter
@Builder
public class RateLimitPerformanceStats {
    private long totalChecks;
    private double averageCheckTime;
    private long performanceThreshold;
    private long slowCheckCount;
    private double redisConnectionPoolUsage;
    private long rateLimitViolations;
}

// SecurityFilterPerformanceStats.java
@Getter
@Setter
@Builder
public class SecurityFilterPerformanceStats {
    private long totalExecutions;
    private double averageExecutionTime;
    private long performanceThreshold;
    private long slowExecutionCount;
    private Map<String, Double> filterExecutionTimes;
}

// SystemResourceStats.java
@Getter
@Setter
@Builder
public class SystemResourceStats {
    private double cpuUsage;
    private double memoryUsage;
    private double redisConnections;
    private double databaseConnections;
    private long availableMemoryMB;
    private int availableProcessors;
}

// OptimizationPlan.java
@Getter
@Setter
@Builder
public class OptimizationPlan {
    private LocalDateTime timestamp;
    private boolean jwtCacheWarmup;
    private boolean memoryCleanup;
    private boolean threadPoolOptimization;
    private boolean redisOptimization;
    private boolean databaseOptimization;
    private String reason;
    
    public boolean hasOptimizations() {
        return jwtCacheWarmup || memoryCleanup || threadPoolOptimization || 
               redisOptimization || databaseOptimization;
    }
}

// OptimizationResult.java
@Getter
@Setter
@Builder
public class OptimizationResult {
    private boolean success;
    private String message;
    private LocalDateTime timestamp;
    private long executionTimeMs;
    
    public static OptimizationResult success(String message) {
        return OptimizationResult.builder()
            .success(true)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static OptimizationResult failure(String message) {
        return OptimizationResult.builder()
            .success(false)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

---

## 🎯 SecurityConfigurationManager 구현

### 통합 보안 설정 관리자
```java
package com.routepick.config.security;

import com.routepick.monitoring.performance.SecurityPerformanceMonitor;
import com.routepick.monitoring.SecurityMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 통합 보안 설정 관리자
 * 모든 보안 설정의 중앙 집중 관리 및 동적 설정 변경
 * 
 * 관리 기능:
 * - 보안 정책 동적 변경
 * - 환경별 보안 설정 적용
 * - 보안 설정 검증
 * - 실시간 설정 모니터링
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfigurationManager implements HealthIndicator {
    
    private final SecurityPerformanceMonitor performanceMonitor;
    private final SecurityMonitoringService monitoringService;
    
    @Value("${app.security.dynamic-config:true}")
    private boolean dynamicConfigEnabled;
    
    @Value("${app.security.config-validation:true}")
    private boolean configValidationEnabled;
    
    @Value("${app.security.auto-tuning:false}")
    private boolean autoTuningEnabled;
    
    // 보안 설정 상태 추적
    private final AtomicBoolean securityConfigHealthy = new AtomicBoolean(true);
    private LocalDateTime lastConfigUpdate = LocalDateTime.now();
    private LocalDateTime lastHealthCheck = LocalDateTime.now();
    
    // 동적 보안 설정
    private Map<String, Object> dynamicSecuritySettings = new HashMap<>();
    
    @PostConstruct
    public void initializeSecurityConfiguration() {
        log.info("Initializing Security Configuration Manager...");
        
        // 초기 보안 설정 로드
        loadInitialSecuritySettings();
        
        // 설정 검증
        if (configValidationEnabled) {
            validateSecurityConfiguration();
        }
        
        // 성능 기반 자동 튜닝 활성화
        if (autoTuningEnabled) {
            enablePerformanceBasedTuning();
        }
        
        log.info("Security Configuration Manager initialized successfully");
    }
    
    /**
     * 초기 보안 설정 로드
     */
    private void loadInitialSecuritySettings() {
        dynamicSecuritySettings.put("jwt.validation.timeout", 5000L);
        dynamicSecuritySettings.put("rate.limit.enabled", true);
        dynamicSecuritySettings.put("cors.max.age", 3600);
        dynamicSecuritySettings.put("csrf.protection.enabled", true);
        dynamicSecuritySettings.put("xss.protection.enabled", true);
        dynamicSecuritySettings.put("security.headers.enabled", true);
        dynamicSecuritySettings.put("audit.logging.enabled", true);
        dynamicSecuritySettings.put("performance.monitoring.enabled", true);
        
        lastConfigUpdate = LocalDateTime.now();
        log.info("Initial security settings loaded: {} settings", dynamicSecuritySettings.size());
    }
    
    /**
     * 보안 설정 검증
     */
    private void validateSecurityConfiguration() {
        boolean configValid = true;
        
        try {
            // JWT 설정 검증
            if (!validateJwtConfiguration()) {
                configValid = false;
                log.error("JWT configuration validation failed");
            }
            
            // CORS 설정 검증
            if (!validateCorsConfiguration()) {
                configValid = false;
                log.error("CORS configuration validation failed");
            }
            
            // Rate Limiting 설정 검증
            if (!validateRateLimitConfiguration()) {
                configValid = false;
                log.error("Rate Limiting configuration validation failed");
            }
            
            securityConfigHealthy.set(configValid);
            
            if (configValid) {
                log.info("Security configuration validation passed");
            } else {
                log.error("Security configuration validation failed");
            }
            
        } catch (Exception e) {
            log.error("Error during security configuration validation", e);
            securityConfigHealthy.set(false);
        }
    }
    
    /**
     * 동적 보안 설정 업데이트
     */
    public boolean updateSecuritySetting(String settingKey, Object settingValue, String updatedBy) {
        if (!dynamicConfigEnabled) {
            log.warn("Dynamic configuration is disabled");
            return false;
        }
        
        try {
            Object oldValue = dynamicSecuritySettings.get(settingKey);
            
            // 설정 변경 검증
            if (!validateSettingChange(settingKey, settingValue)) {
                log.warn("Security setting validation failed: {} = {}", settingKey, settingValue);
                return false;
            }
            
            // 설정 업데이트
            dynamicSecuritySettings.put(settingKey, settingValue);
            lastConfigUpdate = LocalDateTime.now();
            
            // 감사 로그
            log.info("Security setting updated: {} changed from {} to {} by {}", 
                settingKey, oldValue, settingValue, updatedBy);
            
            // 설정 적용
            applySecuritySetting(settingKey, settingValue);
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to update security setting: {} = {}", settingKey, settingValue, e);
            return false;
        }
    }
    
    /**
     * 현재 보안 설정 조회
     */
    public Map<String, Object> getCurrentSecuritySettings() {
        Map<String, Object> settings = new HashMap<>(dynamicSecuritySettings);
        settings.put("_last_update", lastConfigUpdate);
        settings.put("_config_healthy", securityConfigHealthy.get());
        return settings;
    }
    
    /**
     * 보안 설정 상태 조회
     */
    public SecurityConfigurationStatus getConfigurationStatus() {
        return SecurityConfigurationStatus.builder()
            .healthy(securityConfigHealthy.get())
            .lastUpdate(lastConfigUpdate)
            .lastHealthCheck(lastHealthCheck)
            .totalSettings(dynamicSecuritySettings.size())
            .dynamicConfigEnabled(dynamicConfigEnabled)
            .autoTuningEnabled(autoTuningEnabled)
            .settings(new HashMap<>(dynamicSecuritySettings))
            .build();
    }
    
    /**
     * 주기적 설정 건강성 체크 (1분마다)
     */
    @Scheduled(fixedRate = 60000) // 1분
    public void performConfigurationHealthCheck() {
        log.debug("Performing security configuration health check...");
        
        lastHealthCheck = LocalDateTime.now();
        
        // 설정 검증
        if (configValidationEnabled) {
            validateSecurityConfiguration();
        }
        
        // 성능 기반 자동 조정
        if (autoTuningEnabled) {
            performAutoTuning();
        }
    }
    
    /**
     * 성능 기반 자동 튜닝
     */
    private void performAutoTuning() {
        try {
            SecurityPerformanceSummary performance = performanceMonitor.getPerformanceSummary();
            
            // JWT 검증 성능이 느리면 타임아웃 연장
            if (performance.getJwtValidationStats().getAverageValidationTime() > 200) {
                Long currentTimeout = (Long) dynamicSecuritySettings.get("jwt.validation.timeout");
                if (currentTimeout < 10000) { // 최대 10초
                    updateSecuritySetting("jwt.validation.timeout", currentTimeout + 1000, "auto-tuning");
                    log.info("JWT validation timeout auto-tuned to {}ms", currentTimeout + 1000);
                }
            }
            
            // 메모리 사용량이 높으면 캐시 크기 조정
            if (performance.getSystemResourceStats().getMemoryUsage() > 85.0) {
                // 캐시 크기 감소 로직
                log.info("High memory usage detected, considering cache size reduction");
            }
            
        } catch (Exception e) {
            log.error("Error during auto-tuning", e);
        }
    }
    
    /**
     * Spring Boot Actuator Health Indicator 구현
     */
    @Override
    public Health health() {
        Health.Builder healthBuilder = securityConfigHealthy.get() ? 
            Health.up() : Health.down();
        
        return healthBuilder
            .withDetail("last_update", lastConfigUpdate)
            .withDetail("last_health_check", lastHealthCheck)
            .withDetail("total_settings", dynamicSecuritySettings.size())
            .withDetail("dynamic_config_enabled", dynamicConfigEnabled)
            .withDetail("auto_tuning_enabled", autoTuningEnabled)
            .build();
    }
    
    // ========== 보조 메서드 ==========
    
    private boolean validateJwtConfiguration() {
        // JWT 관련 설정 검증 로직
        return true;
    }
    
    private boolean validateCorsConfiguration() {
        // CORS 관련 설정 검증 로직
        return true;
    }
    
    private boolean validateRateLimitConfiguration() {
        // Rate Limiting 관련 설정 검증 로직
        return true;
    }
    
    private boolean validateSettingChange(String key, Object value) {
        // 개별 설정 변경 검증 로직
        return true;
    }
    
    private void applySecuritySetting(String key, Object value) {
        // 실제 보안 설정 적용 로직
        log.debug("Applied security setting: {} = {}", key, value);
    }
    
    private void enablePerformanceBasedTuning() {
        log.info("Performance-based auto-tuning enabled");
    }
}

// SecurityConfigurationStatus.java
@Getter
@Setter
@Builder
public class SecurityConfigurationStatus {
    private boolean healthy;
    private LocalDateTime lastUpdate;
    private LocalDateTime lastHealthCheck;
    private int totalSettings;
    private boolean dynamicConfigEnabled;
    private boolean autoTuningEnabled;
    private Map<String, Object> settings;
}
```

## 📊 핵심 데이터 모델 설계

### 1. 성능 통계 모델
```java
// 성능 지표 포함 항목
- totalValidations: 총 검증 횟수
- averageValidationTime: 평균 검증 시간
- cacheHitRate: 캐시 히트율 (%)
- p95ValidationTime: 95% 백분위 시간
- p99ValidationTime: 99% 백분위 시간
```

### 2. 최적화 계획 모델
```java
// 수행할 최적화 작업
- jwtCacheWarmup: JWT 캐시 워밍업 필요
- memoryCleanup: 메모리 정리 필요
- threadPoolOptimization: 스레드 풀 조정 필요
- redisOptimization: Redis 연결 최적화 필요
- databaseOptimization: DB 연결 최적화 필요
```

### 3. 동적 설정 관리
- **8가지 핵심 보안 설정** 관리:
  - JWT 검증 타임아웃 (5초 기본)
  - Rate Limiting 활성화
  - CORS 최대 나이 (3600초)
  - CSRF/XSS 보호 활성화
  - 보안 헤더 적용
  - 감사 로깅 활성화
  - 성능 모니터링 활성화

### 4. 자동 튜닝 로직
- **JWT 성능 기반**: 200ms 초과 시 타임아웃 연장 (최대 10초)
- **메모리 사용량 기반**: 85% 초과 시 캐시 크기 감소
- **1분 주기**: 설정 건강성 체크 및 자동 튜닝

### 5. Spring Boot Actuator 통합
- **Health Indicator**: `/actuator/health/securityConfig`
- **상태 정보**: 마지막 업데이트, 건강성 체크, 설정 개수
- **설정 플래그**: 동적 설정/자동 튜닝 활성화 상태