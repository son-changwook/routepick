# 🌐 Step 6-6b1: External API Core Management

> 외부 API 설정 관리 및 보안 핵심 기능  
> 생성일: 2025-09-01  
> 분할 기준: API 설정 관리 핵심 로직

---

## 🎯 설계 목표

- **API 설정 관리**: 외부 API 키, 엔드포인트, 제한사항 통합 관리
- **환경별 분리**: DEV/STAGING/PROD 환경별 설정 격리
- **보안 강화**: API 키 암호화 저장 및 안전한 관리
- **연동 테스트**: 외부 API 연결 상태 자동 검증

---

## ✅ ExternalApiService.java (핵심 관리)

```java
package com.routepick.service.system;

import com.routepick.common.enums.ApiProviderType;
import com.routepick.domain.system.entity.ExternalApiConfig;
import com.routepick.domain.system.repository.ExternalApiConfigRepository;
import com.routepick.dto.system.ExternalApiConfigDto;
import com.routepick.dto.system.ApiHealthStatusDto;
import com.routepick.exception.system.SystemException;
import com.routepick.util.EncryptionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 외부 API 핵심 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalApiService {
    
    private final ExternalApiConfigRepository externalApiConfigRepository;
    private final EncryptionUtil encryptionUtil;
    private final RestTemplate restTemplate;
    
    // 헬스체크 설정
    private static final int HEALTH_CHECK_TIMEOUT = 10000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    // ===== API 설정 관리 =====
    
    /**
     * 외부 API 설정 생성
     */
    @Transactional
    @CacheEvict(value = "externalApi:configs", allEntries = true)
    public ExternalApiConfigDto createApiConfig(ExternalApiConfigDto configDto) {
        log.info("외부 API 설정 생성: {}", configDto.getProviderName());
        
        try {
            // 중복 검증
            validateDuplicateConfig(configDto);
            
            // API 키 암호화
            String encryptedApiKey = encryptionUtil.encrypt(configDto.getApiKey());
            String encryptedApiSecret = configDto.getApiSecret() != null ? 
                    encryptionUtil.encrypt(configDto.getApiSecret()) : null;
            
            // 엔티티 생성
            ExternalApiConfig config = ExternalApiConfig.builder()
                    .providerType(configDto.getProviderType())
                    .providerName(configDto.getProviderName())
                    .environment(configDto.getEnvironment())
                    .apiKey(encryptedApiKey)
                    .apiSecret(encryptedApiSecret)
                    .baseUrl(configDto.getBaseUrl())
                    .maxRequestsPerDay(configDto.getMaxRequestsPerDay())
                    .timeoutSeconds(configDto.getTimeoutSeconds())
                    .isActive(true)
                    .build();
            
            ExternalApiConfig savedConfig = externalApiConfigRepository.save(config);
            
            log.info("외부 API 설정 생성 완료: ID={}", savedConfig.getId());
            
            return mapToDto(savedConfig);
            
        } catch (Exception e) {
            log.error("외부 API 설정 생성 실패: {}", configDto.getProviderName(), e);
            throw new SystemException("외부 API 설정 생성에 실패했습니다.", e);
        }
    }
    
    /**
     * 외부 API 설정 수정
     */
    @Transactional
    @CacheEvict(value = "externalApi:configs", allEntries = true)
    public ExternalApiConfigDto updateApiConfig(Long configId, ExternalApiConfigDto configDto) {
        log.info("외부 API 설정 수정: ID={}", configId);
        
        try {
            ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                    .orElseThrow(() -> new SystemException("API 설정을 찾을 수 없습니다: " + configId));
            
            // 변경 가능한 필드만 업데이트
            if (configDto.getBaseUrl() != null) {
                config.updateBaseUrl(configDto.getBaseUrl());
            }
            
            if (configDto.getMaxRequestsPerDay() != null) {
                config.updateMaxRequestsPerDay(configDto.getMaxRequestsPerDay());
            }
            
            if (configDto.getTimeoutSeconds() != null) {
                config.updateTimeoutSeconds(configDto.getTimeoutSeconds());
            }
            
            // API 키 변경 시 재암호화
            if (configDto.getApiKey() != null && !configDto.getApiKey().isEmpty()) {
                String encryptedApiKey = encryptionUtil.encrypt(configDto.getApiKey());
                config.updateApiKey(encryptedApiKey);
            }
            
            if (configDto.getApiSecret() != null) {
                String encryptedApiSecret = !configDto.getApiSecret().isEmpty() ? 
                        encryptionUtil.encrypt(configDto.getApiSecret()) : null;
                config.updateApiSecret(encryptedApiSecret);
            }
            
            ExternalApiConfig updatedConfig = externalApiConfigRepository.save(config);
            
            log.info("외부 API 설정 수정 완료: ID={}", configId);
            
            return mapToDto(updatedConfig);
            
        } catch (Exception e) {
            log.error("외부 API 설정 수정 실패: ID={}", configId, e);
            throw new SystemException("외부 API 설정 수정에 실패했습니다.", e);
        }
    }
    
    /**
     * 외부 API 설정 활성화/비활성화
     */
    @Transactional
    @CacheEvict(value = "externalApi:configs", allEntries = true)
    public void toggleApiConfig(Long configId, boolean isActive) {
        log.info("외부 API 설정 상태 변경: ID={}, active={}", configId, isActive);
        
        try {
            ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                    .orElseThrow(() -> new SystemException("API 설정을 찾을 수 없습니다: " + configId));
            
            config.updateActiveStatus(isActive);
            externalApiConfigRepository.save(config);
            
            log.info("외부 API 설정 상태 변경 완료: ID={}", configId);
            
        } catch (Exception e) {
            log.error("외부 API 설정 상태 변경 실패: ID={}", configId, e);
            throw new SystemException("외부 API 설정 상태 변경에 실패했습니다.", e);
        }
    }
    
    /**
     * 외부 API 설정 삭제
     */
    @Transactional
    @CacheEvict(value = "externalApi:configs", allEntries = true)
    public void deleteApiConfig(Long configId) {
        log.info("외부 API 설정 삭제: ID={}", configId);
        
        try {
            ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                    .orElseThrow(() -> new SystemException("API 설정을 찾을 수 없습니다: " + configId));
            
            // 소프트 삭제
            config.markAsDeleted();
            externalApiConfigRepository.save(config);
            
            log.info("외부 API 설정 삭제 완료: ID={}", configId);
            
        } catch (Exception e) {
            log.error("외부 API 설정 삭제 실패: ID={}", configId, e);
            throw new SystemException("외부 API 설정 삭제에 실패했습니다.", e);
        }
    }
    
    // ===== API 설정 조회 =====
    
    /**
     * 환경별 활성 API 설정 조회 (캐시됨)
     */
    @Cacheable(value = "externalApi:configs", key = "#environment")
    public List<ExternalApiConfigDto> getActiveApiConfigs(String environment) {
        log.debug("환경별 활성 API 설정 조회: {}", environment);
        
        try {
            List<ExternalApiConfig> configs = externalApiConfigRepository
                    .findByEnvironmentAndIsActiveTrue(environment);
            
            return configs.stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("환경별 활성 API 설정 조회 실패: {}", environment, e);
            throw new SystemException("API 설정 조회에 실패했습니다.", e);
        }
    }
    
    /**
     * Provider 타입별 API 설정 조회
     */
    @Cacheable(value = "externalApi:provider", key = "#providerType + ':' + #environment")
    public Optional<ExternalApiConfigDto> getApiConfigByProvider(
            ApiProviderType providerType, String environment) {
        
        log.debug("Provider별 API 설정 조회: {}:{}", providerType, environment);
        
        try {
            Optional<ExternalApiConfig> config = externalApiConfigRepository
                    .findByProviderTypeAndEnvironmentAndIsActiveTrue(providerType, environment);
            
            return config.map(this::mapToDto);
            
        } catch (Exception e) {
            log.error("Provider별 API 설정 조회 실패: {}:{}", providerType, environment, e);
            return Optional.empty();
        }
    }
    
    /**
     * API 설정 상세 조회
     */
    public ExternalApiConfigDto getApiConfigById(Long configId) {
        log.debug("API 설정 상세 조회: ID={}", configId);
        
        try {
            ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                    .orElseThrow(() -> new SystemException("API 설정을 찾을 수 없습니다: " + configId));
            
            return mapToDto(config);
            
        } catch (Exception e) {
            log.error("API 설정 상세 조회 실패: ID={}", configId, e);
            throw new SystemException("API 설정 조회에 실패했습니다.", e);
        }
    }
    
    /**
     * 전체 API 설정 목록 조회
     */
    public List<ExternalApiConfigDto> getAllApiConfigs() {
        log.debug("전체 API 설정 목록 조회");
        
        try {
            List<ExternalApiConfig> configs = externalApiConfigRepository.findAll();
            
            return configs.stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("전체 API 설정 목록 조회 실패", e);
            throw new SystemException("API 설정 목록 조회에 실패했습니다.", e);
        }
    }
    
    // ===== API 연결 테스트 =====
    
    /**
     * API 연결 테스트
     */
    public ApiHealthStatusDto testApiConnection(Long configId) {
        log.info("API 연결 테스트: ID={}", configId);
        
        try {
            ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                    .orElseThrow(() -> new SystemException("API 설정을 찾을 수 없습니다: " + configId));
            
            return performHealthCheck(config);
            
        } catch (Exception e) {
            log.error("API 연결 테스트 실패: ID={}", configId, e);
            return ApiHealthStatusDto.builder()
                    .configId(configId)
                    .isHealthy(false)
                    .responseTime(0L)
                    .statusCode(0)
                    .errorMessage(e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * API 헬스체크 수행
     */
    private ApiHealthStatusDto performHealthCheck(ExternalApiConfig config) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 헬스체크 엔드포인트 구성
            String healthCheckUrl = buildHealthCheckUrl(config);
            
            // HTTP 요청 수행
            ResponseEntity<String> response = restTemplate.getForEntity(healthCheckUrl, String.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            boolean isHealthy = response.getStatusCode() == HttpStatus.OK;
            
            return ApiHealthStatusDto.builder()
                    .configId(config.getId())
                    .providerName(config.getProviderName())
                    .isHealthy(isHealthy)
                    .responseTime(responseTime)
                    .statusCode(response.getStatusCode().value())
                    .checkedAt(LocalDateTime.now())
                    .build();
            
        } catch (ResourceAccessException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ApiHealthStatusDto.builder()
                    .configId(config.getId())
                    .providerName(config.getProviderName())
                    .isHealthy(false)
                    .responseTime(responseTime)
                    .statusCode(0)
                    .errorMessage("연결 시간 초과: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ApiHealthStatusDto.builder()
                    .configId(config.getId())
                    .providerName(config.getProviderName())
                    .isHealthy(false)
                    .responseTime(responseTime)
                    .statusCode(0)
                    .errorMessage("헬스체크 실패: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    // ===== 보안 관리 =====
    
    /**
     * 암호화된 API 키 복호화 (내부 사용)
     */
    public String getDecryptedApiKey(Long configId) {
        try {
            ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                    .orElseThrow(() -> new SystemException("API 설정을 찾을 수 없습니다: " + configId));
            
            return encryptionUtil.decrypt(config.getApiKey());
            
        } catch (Exception e) {
            log.error("API 키 복호화 실패: ID={}", configId, e);
            throw new SystemException("API 키 복호화에 실패했습니다.", e);
        }
    }
    
    /**
     * 암호화된 API Secret 복호화 (내부 사용)
     */
    public String getDecryptedApiSecret(Long configId) {
        try {
            ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                    .orElseThrow(() -> new SystemException("API 설정을 찾을 수 없습니다: " + configId));
            
            return config.getApiSecret() != null ? 
                    encryptionUtil.decrypt(config.getApiSecret()) : null;
            
        } catch (Exception e) {
            log.error("API Secret 복호화 실패: ID={}", configId, e);
            throw new SystemException("API Secret 복호화에 실패했습니다.", e);
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 중복 설정 검증
     */
    private void validateDuplicateConfig(ExternalApiConfigDto configDto) {
        boolean exists = externalApiConfigRepository.existsByProviderTypeAndEnvironment(
                configDto.getProviderType(), configDto.getEnvironment());
        
        if (exists) {
            throw new SystemException(String.format(
                "동일한 Provider와 환경의 설정이 이미 존재합니다: %s:%s",
                configDto.getProviderType(), configDto.getEnvironment()));
        }
    }
    
    /**
     * 헬스체크 URL 구성
     */
    private String buildHealthCheckUrl(ExternalApiConfig config) {
        String baseUrl = config.getBaseUrl();
        
        // Provider별 헬스체크 엔드포인트
        return switch (config.getProviderType()) {
            case GOOGLE_MAPS -> baseUrl + "/maps/api/geocode/json?address=test&key=" + 
                              getDecryptedApiKey(config.getId());
            case KAKAO_MAP -> baseUrl + "/v2/local/search/address.json?query=test";
            case NAVER_MAP -> baseUrl + "/map-geocode/v2/geocode?query=test";
            case PAYMENT_TOSS -> baseUrl + "/v1/payments";
            case PAYMENT_KAKAOPAY -> baseUrl + "/v1/payment/ready";
            case PAYMENT_NAVERPAY -> baseUrl + "/v1/payments";
            case FCM -> "https://fcm.googleapis.com/v1/projects/test/messages:send";
            case EMAIL_SENDGRID -> "https://api.sendgrid.com/v3/mail/send";
            case SMS_ALIGO -> "https://apis.aligo.in/send/";
            default -> baseUrl + "/health";
        };
    }
    
    /**
     * Entity를 DTO로 변환
     */
    private ExternalApiConfigDto mapToDto(ExternalApiConfig config) {
        return ExternalApiConfigDto.builder()
                .id(config.getId())
                .providerType(config.getProviderType())
                .providerName(config.getProviderName())
                .environment(config.getEnvironment())
                .apiKey("***") // 보안상 마스킹
                .apiSecret(config.getApiSecret() != null ? "***" : null)
                .baseUrl(config.getBaseUrl())
                .maxRequestsPerDay(config.getMaxRequestsPerDay())
                .timeoutSeconds(config.getTimeoutSeconds())
                .isActive(config.getIsActive())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
    
    /**
     * API 설정 유효성 검증
     */
    public boolean validateApiConfig(Long configId) {
        try {
            ExternalApiConfig config = externalApiConfigRepository.findById(configId)
                    .orElse(null);
            
            if (config == null || !config.getIsActive()) {
                return false;
            }
            
            // 기본 검증
            if (config.getBaseUrl() == null || config.getBaseUrl().isEmpty()) {
                return false;
            }
            
            if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
                return false;
            }
            
            // Provider별 추가 검증
            return switch (config.getProviderType()) {
                case PAYMENT_TOSS, PAYMENT_KAKAOPAY, PAYMENT_NAVERPAY -> 
                    config.getApiSecret() != null && !config.getApiSecret().isEmpty();
                default -> true;
            };
            
        } catch (Exception e) {
            log.error("API 설정 유효성 검증 실패: ID={}", configId, e);
            return false;
        }
    }
}
```

---

## 📈 주요 특징

### 1. **보안 강화**
- API 키/Secret 암호화 저장
- 민감 정보 마스킹 처리
- 복호화는 내부 사용만

### 2. **설정 관리**
- CRUD 완전 지원
- 환경별 설정 분리
- 소프트 삭제 처리

### 3. **연결 테스트**
- Provider별 헬스체크 엔드포인트
- 응답 시간 측정
- 상세한 오류 정보

### 4. **캐시 최적화**
- 환경별 설정 캐시
- Provider별 캐시
- 변경 시 캐시 무효화

---

**📝 연관 파일**: 
- step6-6b2_api_rate_limiting.md (Rate Limiting)
- step6-6b3_api_monitoring.md (모니터링)