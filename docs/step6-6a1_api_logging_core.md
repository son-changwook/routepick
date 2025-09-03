# Step 6-6a1: ApiLogService 핵심 로깅

> API 호출 로그 관리 및 기본 조회 기능
> 생성일: 2025-08-22
> 단계: 6-6a1 (Service 레이어 - API 로깅 핵심)
> 기반: step4-4c2_system_logging_entities.md, step5-4f3_system_management_repositories.md

---

## 🎯 설계 목표

- **API 로깅**: 모든 REST API 호출 추적 및 로그 관리
- **비동기 처리**: 응답 성능에 영향 없는 백그라운드 로깅
- **에러 추적**: 에러 로그 생성 및 분류
- **기본 조회**: 최근 로그, 에러 로그, 느린 요청 조회

---

## ✅ ApiLogService.java

```java
package com.routepick.service.system;

import com.routepick.common.enums.ApiLogLevel;
import com.routepick.domain.system.entity.ApiLog;
import com.routepick.domain.system.repository.ApiLogRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.dto.system.ApiLogDto;
import com.routepick.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * API 로그 관리 서비스 - 핵심 로깅
 * - API 호출 로깅 및 기본 조회
 * - 비동기 로그 처리
 * - 에러 추적 및 분류
 * - 느린 요청 감지
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiLogService {
    
    private final ApiLogRepository apiLogRepository;
    private final NotificationService notificationService;
    
    // 성능 임계치 설정
    private static final long SLOW_REQUEST_THRESHOLD = 1000L; // 1초
    private static final long VERY_SLOW_REQUEST_THRESHOLD = 5000L; // 5초
    private static final double ERROR_RATE_THRESHOLD = 0.05; // 5%
    
    // ===== API 로그 생성 =====
    
    /**
     * API 호출 로그 생성 (비동기)
     */
    @Async
    @Transactional
    public CompletableFuture<Void> logApiCall(User user, HttpServletRequest request, 
                                            Integer responseStatus, Long duration, 
                                            Long responseSize, String errorMessage) {
        try {
            ApiLog apiLog = ApiLog.builder()
                    .user(user)
                    .endpoint(request.getRequestURI())
                    .httpMethod(request.getMethod())
                    .clientIp(getClientIp(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .responseStatus(responseStatus)
                    .durationMs(duration)
                    .responseSize(responseSize)
                    .requestTime(LocalDateTime.now())
                    .build();
            
            // 에러 정보 설정
            if (errorMessage != null) {
                apiLog.setErrorMessage(errorMessage);
                apiLog.setLogLevel(ApiLogLevel.ERROR);
            } else if (responseStatus >= 500) {
                apiLog.setLogLevel(ApiLogLevel.ERROR);
            } else if (responseStatus >= 400) {
                apiLog.setLogLevel(ApiLogLevel.WARN);
            }
            
            // 느린 요청 체크
            if (duration > VERY_SLOW_REQUEST_THRESHOLD) {
                apiLog.setLogLevel(ApiLogLevel.WARN);
                // 매우 느린 요청 알림
                notifySlowRequest(apiLog);
            }
            
            apiLogRepository.save(apiLog);
            
            log.debug("API 로그 저장 완료: {} {} - {}ms", 
                    request.getMethod(), request.getRequestURI(), duration);
            
        } catch (Exception e) {
            log.error("API 로그 저장 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 에러 로그 생성
     */
    @Async
    @Transactional
    public CompletableFuture<Void> logError(String endpoint, String method, 
                                          String errorMessage, String exceptionClass) {
        try {
            ApiLog errorLog = ApiLog.createErrorLog(endpoint, method, errorMessage, exceptionClass);
            apiLogRepository.save(errorLog);
            
            log.debug("에러 로그 저장 완료: {} {} - {}", method, endpoint, errorMessage);
            
        } catch (Exception e) {
            log.error("에러 로그 저장 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== API 로그 조회 =====
    
    /**
     * 최근 API 로그 조회 (캐시 적용)
     */
    @Cacheable(value = "apiLogs:recent", key = "#hours + ':' + #pageable.pageNumber")
    public List<ApiLogDto> getRecentLogs(int hours, Pageable pageable) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<ApiLog> logs = apiLogRepository.findRecentLogs(since, pageable);
        
        return logs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 에러 로그 조회
     */
    @Cacheable(value = "apiLogs:errors", key = "#hours + ':' + #pageable.pageNumber")
    public List<ApiLogDto> getErrorLogs(int hours, Pageable pageable) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<ApiLog> errorLogs = apiLogRepository.findErrorLogs(since, pageable);
        
        return errorLogs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 느린 요청 조회
     */
    @Cacheable(value = "apiLogs:slow", key = "#threshold + ':' + #hours")
    public List<ApiLogDto> getSlowRequests(long threshold, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<ApiLog> slowLogs = apiLogRepository.findSlowRequests(threshold, since);
        
        return slowLogs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 사용자별 API 호출 수 조회
     */
    @Cacheable(value = "apiLogs:userCount", key = "#userId + ':' + #hours")
    public long getUserApiCallCount(Long userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return apiLogRepository.countApiCallsByUser(userId, since);
    }
    
    // ===== 로그 관리 =====
    
    /**
     * 오래된 로그 정리 (스케줄링)
     */
    @Transactional
    public void cleanOldLogs(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        
        try {
            // 성공 로그는 더 짧은 기간 보관 (30일)
            LocalDateTime successCutoff = LocalDateTime.now().minusDays(30);
            int deletedSuccess = apiLogRepository.deleteOldSuccessLogs(successCutoff);
            
            // 에러 로그는 더 긴 기간 보관 (90일)
            int deletedTotal = apiLogRepository.deleteOldLogs(cutoffDate);
            
            log.info("오래된 API 로그 정리 완료: 성공 로그 {}개, 전체 {}개 삭제", 
                    deletedSuccess, deletedTotal);
            
        } catch (Exception e) {
            log.error("API 로그 정리 실패", e);
        }
    }
    
    // ===== 알림 처리 =====
    
    /**
     * 느린 요청 알림
     */
    private void notifySlowRequest(ApiLog apiLog) {
        try {
            String message = String.format(
                "느린 API 요청 탐지: %s %s (%dms)",
                apiLog.getHttpMethod(),
                apiLog.getEndpoint(),
                apiLog.getDurationMs()
            );
            
            // 관리자에게 알림 발송
            notificationService.sendSystemAlert("SLOW_API", message, Map.of(
                "endpoint", apiLog.getEndpoint(),
                "method", apiLog.getHttpMethod(),
                "duration", apiLog.getDurationMs(),
                "threshold", VERY_SLOW_REQUEST_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("느린 요청 알림 발송 실패", e);
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * ApiLog -> ApiLogDto 변환
     */
    private ApiLogDto convertToDto(ApiLog apiLog) {
        return ApiLogDto.builder()
                .logId(apiLog.getLogId())
                .userId(apiLog.getUser() != null ? apiLog.getUser().getUserId() : null)
                .endpoint(apiLog.getEndpoint())
                .httpMethod(apiLog.getHttpMethod())
                .clientIp(apiLog.getClientIp())
                .responseStatus(apiLog.getResponseStatus())
                .durationMs(apiLog.getDurationMs())
                .responseSize(apiLog.getResponseSize())
                .logLevel(apiLog.getLogLevel())
                .errorMessage(apiLog.getErrorMessage())
                .requestTime(apiLog.getRequestTime())
                .createdAt(apiLog.getCreatedAt())
                .build();
    }
}
```

---

## 📊 ApiLogDto.java

```java
package com.routepick.dto.system;

import com.routepick.common.enums.ApiLogLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * API 로그 DTO
 */
@Getter
@Builder
public class ApiLogDto {
    private Long logId;
    private Long userId;
    private String endpoint;
    private String httpMethod;
    private String clientIp;
    private Integer responseStatus;
    private Long durationMs;
    private Long responseSize;
    private ApiLogLevel logLevel;
    private String errorMessage;
    private LocalDateTime requestTime;
    private LocalDateTime createdAt;
    
    // 계산된 필드들
    public String getPerformanceGrade() {
        if (durationMs == null) return "미측정";
        
        if (durationMs < 100) return "우수";
        if (durationMs < 500) return "좋음";
        if (durationMs < 1000) return "보통";
        if (durationMs < 3000) return "느림";
        return "매우 느림";
    }
    
    public boolean isSlowRequest() {
        return durationMs != null && durationMs > 1000;
    }
    
    public boolean isErrorLog() {
        return responseStatus != null && responseStatus >= 400;
    }
    
    public String getResponseSizeMB() {
        if (responseSize == null) return "0.0 MB";
        return String.format("%.2f MB", responseSize / (1024.0 * 1024.0));
    }
}
```

---

## 🔧 캐시 설정

### ApiLogConfig.java
```java
package com.routepick.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * API 로그 캐시 설정
 */
@Configuration
@EnableCaching
@EnableAsync
public class ApiLogConfig {
    
    /**
     * API 로그용 캐시 설정
     */
    @Bean
    public RedisCacheManager apiLogCacheManager(RedisConnectionFactory connectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // API 로그 캐시 (5분)
        cacheConfigurations.put("apiLogs:recent", 
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)));
        
        cacheConfigurations.put("apiLogs:errors", 
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(3)));
        
        cacheConfigurations.put("apiLogs:slow", 
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)));
        
        cacheConfigurations.put("apiLogs:userCount", 
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15)));
        
        return RedisCacheManager.builder(connectionFactory)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
```

---

## 📈 핵심 특징

### 1. **비동기 로깅**
- `@Async` 어노테이션으로 로그 저장 비동기 처리
- API 응답 속도에 영향 없는 백그라운드 로깅
- `CompletableFuture` 반환으로 논블로킹 처리

### 2. **지능형 분류**
- 응답 코드별 자동 로그 레벨 설정
- 성능 기준 기반 느린 요청 감지
- 에러 메시지 포함 상세 로깅

### 3. **효율적 조회**
- Redis 캐시 기반 빠른 조회
- 페이징 처리로 대용량 데이터 관리
- 시간 범위별 필터링 지원

### 4. **자동 정리**
- 로그 타입별 차등 보관 정책
- 스케줄링 기반 자동 정리
- 스토리지 최적화

---

## 🛡️ 보안 및 안정성

### 1. **민감정보 보호**
- 클라이언트 IP 정확한 추출
- User-Agent 정보 기록
- 에러 메시지 안전 저장

### 2. **장애 대응**
- 로그 저장 실패 시 시스템 영향 최소화
- 예외 처리로 서비스 안정성 확보
- 디버그 로그 레벨 활용

### 3. **성능 최적화**
- 비동기 처리로 메인 플로우 영향 없음
- 캐시 활용 빠른 조회 성능
- 인덱스 최적화 쿼리

---

**📝 다음 단계**: step6-6a2_api_performance_monitoring.md  
**완료일**: 2025-08-22  
**핵심 성과**: API 로깅 핵심 기능 + 비동기 처리 + 기본 조회 완성