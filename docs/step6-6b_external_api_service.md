# 🌐 Step 6-6b: ExternalApiService 구현

> 외부 API 설정 관리, 상태 모니터링 및 연동 테스트 완전 구현  
> 생성일: 2025-08-22  
> 기반: step4-4c2_system_logging_entities.md, step5-4f3_system_management_repositories.md

---

## 🎯 설계 목표

- **API 설정 관리**: 외부 API 키, 엔드포인트, 제한사항 통합 관리
- **환경별 분리**: DEV/STAGING/PROD 환경별 설정 격리
- **상태 모니터링**: API 헬스체크 및 가용성 실시간 추적
- **보안 강화**: API 키 암호화 저장 및 안전한 관리
- **Rate Limiting**: 호출 제한 관리 및 사용량 추적
- **연동 테스트**: 외부 API 연결 상태 자동 검증

---

## ✅ ExternalApiService.java

```java
package com.routepick.service.system;

import com.routepick.common.enums.ApiProviderType;
import com.routepick.domain.system.entity.ExternalApiConfig;
import com.routepick.domain.system.repository.ExternalApiConfigRepository;
import com.routepick.dto.system.ExternalApiConfigDto;
import com.routepick.dto.system.ApiHealthStatusDto;
import com.routepick.dto.system.ApiUsageDto;
import com.routepick.exception.system.SystemException;
import com.routepick.service.notification.NotificationService;
import com.routepick.util.EncryptionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 외부 API 관리 서비스
 * - 외부 API 설정 및 키 관리
 * - API 상태 모니터링 및 헬스체크
 * - Rate Limiting 및 사용량 추적
 * - 연동 테스트 및 장애 감지
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalApiService {
    
    private final ExternalApiConfigRepository externalApiConfigRepository;
    private final NotificationService notificationService;
    private final EncryptionUtil encryptionUtil;
    private final RestTemplate restTemplate;
    
    // 헬스체크 타임아웃 설정
    private static final int HEALTH_CHECK_TIMEOUT = 10000; // 10초
    private static final double USAGE_WARNING_THRESHOLD = 0.8; // 80%
    private static final double USAGE_CRITICAL_THRESHOLD = 0.95; // 95%
    
    // ===== API 설정 관리 =====
    
    /**
     * 외부 API 설정 생성
     */
    @Transactional
    @CacheEvict(value = "externalApi:configs", allEntries = true)
    public ExternalApiConfigDto createApiConfig(ExternalApiConfigDto configDto) {
        log.info("외부 API 설정 생성: {}", configDto.getProviderName());
        
        // API 키 암호화
        String encryptedApiKey = encryptionUtil.encrypt(configDto.getApiKey());
        String encryptedApiSecret = configDto.getApiSecret() != null ? 
                encryptionUtil.encrypt(configDto.getApiSecret()) : null;
        
        ExternalApiConfig config = ExternalApiConfig.builder()
                .providerType(configDto.getProviderType())
                .providerName(configDto.getProviderName())
                .environment(configDto.getEnvironment())
                .apiKey(encryptedApiKey)
                .apiSecret(encryptedApiSecret)
                .baseUrl(configDto.getBaseUrl())
                .callbackUrl(configDto.getCallbackUrl())
                .rateLimitPerHour(configDto.getRateLimitPerHour())
                .timeoutMs(configDto.getTimeoutMs())
                .retryCount(configDto.getRetryCount())
                .description(configDto.getDescription())
                .isActive(true)
                .build();
        
        ExternalApiConfig savedConfig = externalApiConfigRepository.save(config);
        
        // 생성 직후 헬스체크 수행
        performHealthCheck(savedConfig);
        
        log.info("외부 API 설정 생성 완료: {} (ID: {})", 
                savedConfig.getProviderName(), savedConfig.getConfigId());
        
        return convertToDto(savedConfig);
    }
    
    /**
     * API 설정 수정
     */
    @Transactional
    @CacheEvict(value = "externalApi:configs", allEntries = true)
    public ExternalApiConfigDto updateApiConfig(Long configId, ExternalApiConfigDto configDto) {
        log.info("외부 API 설정 수정: configId={}", configId);
        
        ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                .orElseThrow(() -> SystemException.externalApiConfigNotFound(configId));
        
        // 수정 가능한 필드만 업데이트
        if (configDto.getBaseUrl() != null) {
            config.setBaseUrl(configDto.getBaseUrl());
        }
        if (configDto.getCallbackUrl() != null) {
            config.setCallbackUrl(configDto.getCallbackUrl());
        }
        if (configDto.getRateLimitPerHour() != null) {
            config.setRateLimitPerHour(configDto.getRateLimitPerHour());
        }
        if (configDto.getTimeoutMs() != null) {
            config.setTimeoutMs(configDto.getTimeoutMs());
        }
        if (configDto.getRetryCount() != null) {
            config.setRetryCount(configDto.getRetryCount());
        }
        if (configDto.getDescription() != null) {
            config.setDescription(configDto.getDescription());
        }
        
        // API 키가 변경된 경우 재암호화
        if (configDto.getApiKey() != null && !configDto.getApiKey().isEmpty()) {
            config.setApiKey(encryptionUtil.encrypt(configDto.getApiKey()));
        }
        if (configDto.getApiSecret() != null && !configDto.getApiSecret().isEmpty()) {
            config.setApiSecret(encryptionUtil.encrypt(configDto.getApiSecret()));
        }
        
        ExternalApiConfig updatedConfig = externalApiConfigRepository.save(config);
        
        log.info("외부 API 설정 수정 완료: {}", updatedConfig.getProviderName());
        
        return convertToDto(updatedConfig);
    }
    
    /**
     * API 설정 활성화/비활성화
     */
    @Transactional
    @CacheEvict(value = "externalApi:configs", allEntries = true)
    public void toggleApiStatus(Long configId, boolean isActive) {
        log.info("외부 API 상태 변경: configId={}, active={}", configId, isActive);
        
        ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                .orElseThrow(() -> SystemException.externalApiConfigNotFound(configId));
        
        if (isActive) {
            config.activate();
        } else {
            config.deactivate();
        }
        
        externalApiConfigRepository.save(config);
        
        log.info("외부 API 상태 변경 완료: {} -> {}", 
                config.getProviderName(), isActive ? "활성화" : "비활성화");
    }
    
    // ===== API 설정 조회 =====
    
    /**
     * 활성 API 설정 조회 (캐시 적용)
     */
    @Cacheable(value = "externalApi:configs", key = "#environment")
    public List<ExternalApiConfigDto> getActiveApiConfigs(String environment) {
        List<ExternalApiConfig> configs = externalApiConfigRepository
                .findActiveByEnvironment(environment);
        
        return configs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 특정 API 설정 조회
     */
    @Cacheable(value = "externalApi:config", key = "#providerName + ':' + #environment")
    public Optional<ExternalApiConfigDto> getApiConfig(String providerName, String environment) {
        return externalApiConfigRepository.findByApiNameAndEnvironment(providerName, environment)
                .map(this::convertToDto);
    }
    
    /**
     * API 키 복호화 조회 (보안 메서드)
     */
    public String getDecryptedApiKey(String providerName, String environment) {
        ExternalApiConfig config = externalApiConfigRepository
                .findByApiNameAndEnvironment(providerName, environment)
                .orElseThrow(() -> SystemException.externalApiConfigNotFound(providerName));
        
        if (!config.getIsActive()) {
            throw SystemException.externalApiConfigInactive(providerName);
        }
        
        return encryptionUtil.decrypt(config.getApiKey());
    }
    
    /**
     * API 시크릿 복호화 조회 (보안 메서드)
     */
    public String getDecryptedApiSecret(String providerName, String environment) {
        ExternalApiConfig config = externalApiConfigRepository
                .findByApiNameAndEnvironment(providerName, environment)
                .orElseThrow(() -> SystemException.externalApiConfigNotFound(providerName));
        
        if (!config.getIsActive()) {
            throw SystemException.externalApiConfigInactive(providerName);
        }
        
        return config.getApiSecret() != null ? 
                encryptionUtil.decrypt(config.getApiSecret()) : null;
    }
    
    // ===== 상태 모니터링 =====
    
    /**
     * 정기 헬스체크 (매 30분)
     */
    @Scheduled(fixedRate = 1800000) // 30분
    @Async
    public void scheduledHealthCheck() {
        log.info("정기 외부 API 헬스체크 시작");
        
        List<ExternalApiConfig> configs = externalApiConfigRepository.findByIsActiveTrueOrderByApiName();
        
        for (ExternalApiConfig config : configs) {
            if (config.needsHealthCheck()) {
                performHealthCheck(config);
            }
        }
        
        log.info("정기 외부 API 헬스체크 완료: {}개 API 체크", configs.size());
    }
    
    /**
     * 개별 API 헬스체크
     */
    @Async
    public CompletableFuture<String> performHealthCheck(ExternalApiConfig config) {
        try {
            log.debug("헬스체크 시작: {}", config.getProviderName());
            
            String healthStatus = checkApiHealth(config);
            
            // 상태 업데이트
            externalApiConfigRepository.updateHealthStatus(
                    config.getConfigId(), 
                    LocalDateTime.now(), 
                    healthStatus
            );
            
            // 상태 변화 시 알림
            if (!"HEALTHY".equals(config.getHealthStatus()) && "HEALTHY".equals(healthStatus)) {
                notifyApiRecovered(config);
            } else if ("HEALTHY".equals(config.getHealthStatus()) && !"HEALTHY".equals(healthStatus)) {
                notifyApiFailure(config, healthStatus);
            }
            
            log.debug("헬스체크 완료: {} -> {}", config.getProviderName(), healthStatus);
            
            return CompletableFuture.completedFuture(healthStatus);
            
        } catch (Exception e) {
            log.error("헬스체크 실패: " + config.getProviderName(), e);
            
            externalApiConfigRepository.updateHealthStatus(
                    config.getConfigId(), 
                    LocalDateTime.now(), 
                    "UNHEALTHY"
            );
            
            return CompletableFuture.completedFuture("UNHEALTHY");
        }
    }
    
    /**
     * API 헬스체크 실행
     */
    private String checkApiHealth(ExternalApiConfig config) {
        try {
            // 헬스체크 URL 구성
            String healthUrl = buildHealthCheckUrl(config);
            
            // HTTP 요청 수행 (타임아웃 적용)
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 응답 상태 분석
            if (response.getStatusCode() == HttpStatus.OK) {
                if (responseTime > config.getTimeoutMs()) {
                    return "DEGRADED"; // 응답은 왔지만 느림
                }
                return "HEALTHY";
            } else if (response.getStatusCode().is5xxServerError()) {
                return "UNHEALTHY";
            } else {
                return "DEGRADED";
            }
            
        } catch (ResourceAccessException e) {
            log.warn("API 연결 실패: {} - {}", config.getProviderName(), e.getMessage());
            return "UNHEALTHY";
        } catch (Exception e) {
            log.error("헬스체크 에러: " + config.getProviderName(), e);
            return "UNKNOWN";
        }
    }
    
    /**
     * 헬스체크 URL 구성
     */
    private String buildHealthCheckUrl(ExternalApiConfig config) {
        String baseUrl = config.getBaseUrl();
        
        // 제공자별 헬스체크 엔드포인트
        return switch (config.getProviderType()) {
            case SOCIAL_LOGIN -> baseUrl + "/health";
            case PAYMENT -> baseUrl + "/v1/health";
            case MAP -> baseUrl + "/status";
            case NOTIFICATION -> baseUrl + "/ping";
            case EMAIL -> baseUrl + "/health";
            case SMS -> baseUrl + "/status";
            default -> baseUrl;
        };
    }
    
    /**
     * API 상태 조회
     */
    @Cacheable(value = "externalApi:health", key = "'all'")
    public List<ApiHealthStatusDto> getAllApiHealthStatus() {
        return externalApiConfigRepository.findApiHealthStatus()
                .stream()
                .map(projection -> ApiHealthStatusDto.builder()
                    .providerName(projection.getApiName())
                    .isActive(projection.getIsActive())
                    .healthStatus(projection.getHealthStatus())
                    .lastHealthCheck(projection.getLastHealthCheck())
                    .currentUsage(projection.getCurrentUsage())
                    .rateLimit(projection.getRateLimit())
                    .usagePercentage(projection.getUsagePercentage())
                    .isHealthy(projection.isHealthy())
                    .build())
                .collect(Collectors.toList());
    }
    
    // ===== Rate Limiting =====
    
    /**
     * API 사용량 증가
     */
    @Transactional
    public void incrementUsage(String providerName) {
        int updatedRows = externalApiConfigRepository.incrementUsageCounter(providerName);
        
        if (updatedRows > 0) {
            // 사용량 체크
            checkUsageThreshold(providerName);
        }
    }
    
    /**
     * 사용량 임계치 체크
     */
    private void checkUsageThreshold(String providerName) {
        try {
            ExternalApiConfig config = externalApiConfigRepository
                    .findByApiNameAndIsActiveTrue(providerName)
                    .orElse(null);
            
            if (config != null && config.getRateLimitPerHour() != null) {
                double usageRate = (double) config.getCurrentUsage() / config.getRateLimitPerHour();
                
                if (usageRate >= USAGE_CRITICAL_THRESHOLD) {
                    notifyUsageCritical(config, usageRate);
                } else if (usageRate >= USAGE_WARNING_THRESHOLD) {
                    notifyUsageWarning(config, usageRate);
                }
            }
            
        } catch (Exception e) {
            log.error("사용량 임계치 체크 실패: " + providerName, e);
        }
    }
    
    /**
     * 사용량 카운터 리셋 (매일 자정)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    @CacheEvict(value = "externalApi:usage", allEntries = true)
    public void resetUsageCounters() {
        log.info("외부 API 사용량 카운터 리셋 시작");
        
        int resetCount = externalApiConfigRepository.resetAllUsageCounters();
        
        log.info("외부 API 사용량 카운터 리셋 완료: {}개 API", resetCount);
    }
    
    /**
     * API 사용량 통계 조회
     */
    @Cacheable(value = "externalApi:usage", key = "'statistics'")
    public List<ApiUsageDto> getUsageStatistics() {
        List<Object[]> results = externalApiConfigRepository.getApiUsageStatistics();
        
        return results.stream()
                .map(row -> ApiUsageDto.builder()
                    .providerName((String) row[0])
                    .currentUsage((Long) row[1])
                    .rateLimit((Long) row[2])
                    .usagePercentage((Double) row[3])
                    .build())
                .collect(Collectors.toList());
    }
    
    // ===== 알림 시스템 =====
    
    /**
     * API 장애 알림
     */
    private void notifyApiFailure(ExternalApiConfig config, String status) {
        try {
            String message = String.format(
                "외부 API 장애 감지: %s (%s 환경) - 상태: %s",
                config.getProviderName(),
                config.getEnvironment(),
                status
            );
            
            notificationService.sendSystemAlert("API_FAILURE", message, Map.of(
                "providerName", config.getProviderName(),
                "environment", config.getEnvironment(),
                "healthStatus", status,
                "baseUrl", config.getBaseUrl()
            ));
            
        } catch (Exception e) {
            log.error("API 장애 알림 발송 실패", e);
        }
    }
    
    /**
     * API 복구 알림
     */
    private void notifyApiRecovered(ExternalApiConfig config) {
        try {
            String message = String.format(
                "외부 API 복구: %s (%s 환경)",
                config.getProviderName(),
                config.getEnvironment()
            );
            
            notificationService.sendSystemAlert("API_RECOVERED", message, Map.of(
                "providerName", config.getProviderName(),
                "environment", config.getEnvironment()
            ));
            
        } catch (Exception e) {
            log.error("API 복구 알림 발송 실패", e);
        }
    }
    
    /**
     * 사용량 경고 알림
     */
    private void notifyUsageWarning(ExternalApiConfig config, double usageRate) {
        try {
            String message = String.format(
                "외부 API 사용량 경고: %s - %.1f%% 사용 중",
                config.getProviderName(),
                usageRate * 100
            );
            
            notificationService.sendSystemAlert("API_USAGE_WARNING", message, Map.of(
                "providerName", config.getProviderName(),
                "usageRate", usageRate,
                "currentUsage", config.getCurrentUsage(),
                "rateLimit", config.getRateLimitPerHour()
            ));
            
        } catch (Exception e) {
            log.error("사용량 경고 알림 발송 실패", e);
        }
    }
    
    /**
     * 사용량 임계 알림
     */
    private void notifyUsageCritical(ExternalApiConfig config, double usageRate) {
        try {
            String message = String.format(
                "외부 API 사용량 임계: %s - %.1f%% 사용 중 (제한 임박)",
                config.getProviderName(),
                usageRate * 100
            );
            
            notificationService.sendSystemAlert("API_USAGE_CRITICAL", message, Map.of(
                "providerName", config.getProviderName(),
                "usageRate", usageRate,
                "currentUsage", config.getCurrentUsage(),
                "rateLimit", config.getRateLimitPerHour()
            ));
            
        } catch (Exception e) {
            log.error("사용량 임계 알림 발송 실패", e);
        }
    }
    
    // ===== 연동 테스트 =====
    
    /**
     * API 연동 테스트
     */
    public Map<String, Object> testApiConnection(Long configId) {
        log.info("API 연동 테스트 시작: configId={}", configId);
        
        ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                .orElseThrow(() -> SystemException.externalApiConfigNotFound(configId));
        
        long startTime = System.currentTimeMillis();
        String testResult = performConnectionTest(config);
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = Map.of(
            "configId", configId,
            "providerName", config.getProviderName(),
            "testResult", testResult,
            "duration", duration,
            "testedAt", LocalDateTime.now(),
            "success", "SUCCESS".equals(testResult)
        );
        
        log.info("API 연동 테스트 완료: {} -> {}", config.getProviderName(), testResult);
        
        return result;
    }
    
    /**
     * 연결 테스트 수행
     */
    private String performConnectionTest(ExternalApiConfig config) {
        try {
            // 기본 연결성 테스트
            String healthStatus = checkApiHealth(config);
            
            if ("HEALTHY".equals(healthStatus)) {
                return "SUCCESS";
            } else if ("DEGRADED".equals(healthStatus)) {
                return "SLOW_RESPONSE";
            } else {
                return "CONNECTION_FAILED";
            }
            
        } catch (Exception e) {
            log.error("연결 테스트 실패: " + config.getProviderName(), e);
            return "TEST_ERROR";
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * ExternalApiConfig -> ExternalApiConfigDto 변환
     */
    private ExternalApiConfigDto convertToDto(ExternalApiConfig config) {
        return ExternalApiConfigDto.builder()
                .configId(config.getConfigId())
                .providerType(config.getProviderType())
                .providerName(config.getProviderName())
                .environment(config.getEnvironment())
                .baseUrl(config.getBaseUrl())
                .callbackUrl(config.getCallbackUrl())
                .rateLimitPerHour(config.getRateLimitPerHour())
                .timeoutMs(config.getTimeoutMs())
                .retryCount(config.getRetryCount())
                .isActive(config.getIsActive())
                .healthStatus(config.getHealthStatus())
                .lastHealthCheck(config.getLastHealthCheck())
                .description(config.getDescription())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                // 보안상 API 키는 마스킹
                .apiKey("****")
                .apiSecret(config.getApiSecret() != null ? "****" : null)
                .build();
    }
}
```

---

## 📊 DTO 클래스들

### ExternalApiConfigDto.java
```java
package com.routepick.dto.system;

import com.routepick.common.enums.ApiProviderType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 외부 API 설정 DTO
 */
@Getter
@Builder
public class ExternalApiConfigDto {
    private Long configId;
    private ApiProviderType providerType;
    private String providerName;
    private String environment;
    private String apiKey;
    private String apiSecret;
    private String baseUrl;
    private String callbackUrl;
    private Integer rateLimitPerHour;
    private Integer timeoutMs;
    private Integer retryCount;
    private Boolean isActive;
    private String healthStatus;
    private LocalDateTime lastHealthCheck;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 계산된 필드들
    public String getProviderTypeKorean() {
        if (providerType == null) return "미분류";
        
        return switch (providerType) {
            case SOCIAL_LOGIN -> "소셜 로그인";
            case PAYMENT -> "결제";
            case MAP -> "지도 서비스";
            case NOTIFICATION -> "알림 서비스";
            case FILE_STORAGE -> "파일 저장소";
            case EMAIL -> "이메일 서비스";
            case SMS -> "문자 서비스";
            default -> "기타";
        };
    }
    
    public String getEnvironmentKorean() {
        if (environment == null) return "미설정";
        
        return switch (environment.toUpperCase()) {
            case "DEV" -> "개발";
            case "STAGING" -> "스테이징";
            case "PROD" -> "운영";
            case "TEST" -> "테스트";
            default -> environment;
        };
    }
    
    public String getHealthStatusKorean() {
        if (healthStatus == null) return "미확인";
        
        return switch (healthStatus) {
            case "HEALTHY" -> "정상";
            case "UNHEALTHY" -> "비정상";
            case "DEGRADED" -> "성능 저하";
            case "MAINTENANCE" -> "점검 중";
            default -> "알 수 없음";
        };
    }
    
    public boolean isHealthy() {
        return "HEALTHY".equals(healthStatus);
    }
    
    public boolean needsHealthCheck() {
        return lastHealthCheck == null || 
               lastHealthCheck.isBefore(LocalDateTime.now().minusHours(1));
    }
    
    public int getTimeoutSeconds() {
        return timeoutMs != null ? timeoutMs / 1000 : 30;
    }
}
```

### ApiHealthStatusDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * API 헬스 상태 DTO
 */
@Getter
@Builder
public class ApiHealthStatusDto {
    private String providerName;
    private Boolean isActive;
    private String healthStatus;
    private LocalDateTime lastHealthCheck;
    private Long currentUsage;
    private Long rateLimit;
    private Double usagePercentage;
    private Boolean isHealthy;
    
    public String getStatusColor() {
        if (!isActive) return "gray";
        
        return switch (healthStatus) {
            case "HEALTHY" -> "green";
            case "DEGRADED" -> "yellow";
            case "UNHEALTHY" -> "red";
            case "MAINTENANCE" -> "blue";
            default -> "gray";
        };
    }
    
    public String getUsageLevel() {
        if (usagePercentage == null) return "알 수 없음";
        
        if (usagePercentage >= 95) return "위험";
        if (usagePercentage >= 80) return "경고";
        if (usagePercentage >= 60) return "주의";
        return "정상";
    }
    
    public boolean isUsageCritical() {
        return usagePercentage != null && usagePercentage >= 95;
    }
    
    public boolean isUsageWarning() {
        return usagePercentage != null && usagePercentage >= 80;
    }
}
```

### ApiUsageDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

/**
 * API 사용량 DTO
 */
@Getter
@Builder
public class ApiUsageDto {
    private String providerName;
    private Long currentUsage;
    private Long rateLimit;
    private Double usagePercentage;
    
    public String getUsageDisplay() {
        return String.format("%d / %d (%s)",
                currentUsage != null ? currentUsage : 0,
                rateLimit != null ? rateLimit : 0,
                getUsagePercentageDisplay());
    }
    
    public String getUsagePercentageDisplay() {
        return usagePercentage != null ? 
                String.format("%.1f%%", usagePercentage) : "0.0%";
    }
    
    public String getUsageStatus() {
        if (usagePercentage == null) return "알 수 없음";
        
        if (usagePercentage >= 95) return "위험";
        if (usagePercentage >= 80) return "경고";
        if (usagePercentage >= 60) return "주의";
        return "정상";
    }
    
    public Long getRemainingQuota() {
        if (rateLimit == null || currentUsage == null) return 0L;
        return Math.max(0, rateLimit - currentUsage);
    }
}
```

---

## 🔧 설정 클래스

### ExternalApiConfig.java
```java
package com.routepick.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 외부 API 설정
 */
@Configuration
public class ExternalApiConfig {
    
    /**
     * 헬스체크용 RestTemplate
     */
    @Bean
    public RestTemplate healthCheckRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10초 연결 타임아웃
        factory.setReadTimeout(10000);    // 10초 읽기 타임아웃
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // 에러 핸들러 설정
        restTemplate.setErrorHandler(new RestTemplateErrorHandler());
        
        return restTemplate;
    }
}
```

---

## 🛡️ 보안 강화

### EncryptionUtil.java
```java
package com.routepick.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 암호화 유틸리티
 */
@Slf4j
@Component
public class EncryptionUtil {
    
    @Value("${app.encryption.secret-key}")
    private String secretKey;
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    /**
     * 문자열 암호화
     */
    public String encrypt(String plainText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
            
        } catch (Exception e) {
            log.error("암호화 실패", e);
            throw new RuntimeException("암호화 실패", e);
        }
    }
    
    /**
     * 문자열 복호화
     */
    public String decrypt(String encryptedText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            
            return new String(decryptedBytes);
            
        } catch (Exception e) {
            log.error("복호화 실패", e);
            throw new RuntimeException("복호화 실패", e);
        }
    }
}
```

---

## 📈 주요 특징

### 1. **보안 강화**
- API 키/시크릿 AES 암호화 저장
- 복호화는 필요시에만 수행
- DTO 응답 시 API 키 마스킹

### 2. **지능형 모니터링**
- 30분마다 자동 헬스체크
- 상태 변화 시 실시간 알림
- 사용량 임계치 기반 알림

### 3. **환경별 관리**
- DEV/STAGING/PROD 환경 분리
- 환경별 독립적인 설정 관리
- 환경별 모니터링 및 알림

### 4. **Rate Limiting**
- 시간당 호출 제한 관리
- 실시간 사용량 추적
- 자동 카운터 리셋 (일별)

---

**📝 다음 단계**: step6-6c_cache_service.md  
**완료일**: 2025-08-22  
**핵심 성과**: 외부 API 관리 + 상태 모니터링 + 보안 강화 + Rate Limiting 완성