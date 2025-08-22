# 🔍 Step 6-6a: ApiLogService 구현

> API 호출 로그 관리, 성능 모니터링 및 에러 분석 완전 구현  
> 생성일: 2025-08-22  
> 기반: step4-4c2_system_logging_entities.md, step5-4f3_system_management_repositories.md

---

## 🎯 설계 목표

- **API 로깅**: 모든 REST API 호출 추적 및 로그 관리
- **성능 모니터링**: 응답 시간, 처리량, 에러율 실시간 분석
- **에러 분석**: 에러 패턴 분석 및 문제 엔드포인트 탐지
- **사용량 분석**: 사용자별, 엔드포인트별 API 사용 패턴 분석
- **알림 시스템**: 임계치 초과 시 자동 알림 발송
- **최적화**: 느린 API 탐지 및 개선 제안

---

## ✅ ApiLogService.java

```java
package com.routepick.service.system;

import com.routepick.common.enums.ApiLogLevel;
import com.routepick.domain.system.entity.ApiLog;
import com.routepick.domain.system.repository.ApiLogRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.dto.system.ApiLogDto;
import com.routepick.dto.system.ApiPerformanceDto;
import com.routepick.dto.system.ApiUsageStatisticsDto;
import com.routepick.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * API 로그 관리 서비스
 * - API 호출 로깅 및 모니터링
 * - 성능 분석 및 최적화 제안
 * - 에러 패턴 분석 및 알림
 * - 사용량 통계 및 트렌드 분석
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
    
    // ===== 성능 분석 =====
    
    /**
     * API 성능 통계 조회
     */
    @Cacheable(value = "apiStats:performance", key = "#hours")
    public ApiPerformanceDto getApiPerformanceStats(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        // 전체 통계 계산
        List<Object[]> endpointStats = apiLogRepository.findSlowEndpoints(since, 
                PageRequest.of(0, 100));
        
        List<Object[]> errorPatterns = apiLogRepository.findErrorPatterns(since);
        
        Map<String, Object> performanceData = endpointStats.stream()
                .collect(Collectors.toMap(
                    row -> (String) row[0], // endpoint
                    row -> Map.of(
                        "avgTime", row[1],
                        "maxTime", row[2]
                    )
                ));
        
        Map<String, Long> errorCounts = errorPatterns.stream()
                .collect(Collectors.toMap(
                    row -> row[0] + ":" + row[1], // status:message
                    row -> (Long) row[2] // count
                ));
        
        return ApiPerformanceDto.builder()
                .analysisHours(hours)
                .endpointPerformance(performanceData)
                .errorPatterns(errorCounts)
                .slowRequestThreshold(SLOW_REQUEST_THRESHOLD)
                .analyzedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 가장 많이 호출된 엔드포인트 조회
     */
    @Cacheable(value = "apiStats:popular", key = "#hours + ':' + #limit")
    public Map<String, Long> getMostCalledEndpoints(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Pageable pageable = PageRequest.of(0, limit);
        
        List<Object[]> results = apiLogRepository.findMostCalledEndpoints(since, pageable);
        
        return results.stream()
                .collect(Collectors.toMap(
                    row -> (String) row[0], // endpoint
                    row -> (Long) row[1]    // count
                ));
    }
    
    /**
     * 문제 엔드포인트 탐지
     */
    @Cacheable(value = "apiStats:problematic", key = "#hours + ':' + #minErrorCount")
    public List<Map<String, Object>> getProblematicEndpoints(int hours, long minErrorCount) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<Object[]> results = apiLogRepository.findProblematicEndpoints(since, minErrorCount);
        
        return results.stream()
                .map(row -> Map.of(
                    "endpoint", row[0],
                    "statusCode", row[1],
                    "errorCount", row[2]
                ))
                .collect(Collectors.toList());
    }
    
    // ===== 사용량 분석 =====
    
    /**
     * API 사용량 통계
     */
    @Cacheable(value = "apiStats:usage", key = "#days")
    public List<ApiUsageStatisticsDto> getUsageStatistics(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        return apiLogRepository.calculateApiUsageStatistics(startDate)
                .stream()
                .map(projection -> ApiUsageStatisticsDto.builder()
                    .date(projection.getDate())
                    .method(projection.getMethod())
                    .totalCalls(projection.getTotalCalls())
                    .averageResponseTime(projection.getAverageResponseTime())
                    .successCount(projection.getSuccessCount())
                    .errorCount(projection.getErrorCount())
                    .successRate(projection.getSuccessRate())
                    .build())
                .collect(Collectors.toList());
    }
    
    /**
     * 가장 활발한 사용자 조회
     */
    @Cacheable(value = "apiStats:activeUsers", key = "#hours + ':' + #limit")
    public List<Map<String, Object>> getMostActiveUsers(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Pageable pageable = PageRequest.of(0, limit);
        
        List<Object[]> results = apiLogRepository.findMostActiveUsers(since, pageable);
        
        return results.stream()
                .map(row -> Map.of(
                    "userId", row[0],
                    "apiCalls", row[1],
                    "uniqueEndpoints", row[2]
                ))
                .collect(Collectors.toList());
    }
    
    // ===== 알림 및 모니터링 =====
    
    /**
     * 시스템 상태 체크 (에러율 모니터링)
     */
    @Async
    public CompletableFuture<Void> checkSystemHealth() {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(1);
            
            // 최근 1시간 통계 계산
            List<Object[]> stats = apiLogRepository.calculateApiUsageStatistics(since)
                    .stream()
                    .map(projection -> new Object[]{
                        projection.getTotalCalls(),
                        projection.getErrorCount(),
                        projection.getSuccessRate()
                    })
                    .collect(Collectors.toList());
            
            if (!stats.isEmpty()) {
                double totalCalls = stats.stream()
                        .mapToLong(row -> (Long) row[0])
                        .sum();
                
                double totalErrors = stats.stream()
                        .mapToLong(row -> (Long) row[1])
                        .sum();
                
                double errorRate = totalCalls > 0 ? totalErrors / totalCalls : 0;
                
                // 에러율 임계치 초과 시 알림
                if (errorRate > ERROR_RATE_THRESHOLD) {
                    notifyHighErrorRate(errorRate, totalCalls, totalErrors);
                }
            }
            
            log.debug("시스템 헬스 체크 완료");
            
        } catch (Exception e) {
            log.error("시스템 헬스 체크 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
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
    
    /**
     * 높은 에러율 알림
     */
    private void notifyHighErrorRate(double errorRate, double totalCalls, double totalErrors) {
        try {
            String message = String.format(
                "높은 에러율 탐지: %.2f%% (전체: %.0f, 에러: %.0f)",
                errorRate * 100,
                totalCalls,
                totalErrors
            );
            
            // 관리자에게 알림 발송
            notificationService.sendSystemAlert("HIGH_ERROR_RATE", message, Map.of(
                "errorRate", errorRate,
                "totalCalls", totalCalls,
                "totalErrors", totalErrors,
                "threshold", ERROR_RATE_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("높은 에러율 알림 발송 실패", e);
        }
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

## 📊 DTO 클래스들

### ApiLogDto.java
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

### ApiPerformanceDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * API 성능 통계 DTO
 */
@Getter
@Builder
public class ApiPerformanceDto {
    private int analysisHours;
    private Map<String, Object> endpointPerformance;
    private Map<String, Long> errorPatterns;
    private long slowRequestThreshold;
    private LocalDateTime analyzedAt;
    
    public int getSlowEndpointCount() {
        return endpointPerformance.size();
    }
    
    public int getErrorPatternCount() {
        return errorPatterns.size();
    }
    
    public long getTotalErrorCount() {
        return errorPatterns.values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }
}
```

### ApiUsageStatisticsDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * API 사용 통계 DTO
 */
@Getter
@Builder
public class ApiUsageStatisticsDto {
    private LocalDate date;
    private String method;
    private Long totalCalls;
    private Double averageResponseTime;
    private Long successCount;
    private Long errorCount;
    private Double successRate;
    
    public String getMethodDisplay() {
        return method != null ? method.toUpperCase() : "UNKNOWN";
    }
    
    public String getSuccessRateDisplay() {
        return String.format("%.2f%%", successRate);
    }
    
    public boolean isHealthy() {
        return successRate >= 95.0; // 95% 이상이면 건강한 상태
    }
    
    public String getPerformanceLevel() {
        if (averageResponseTime == null) return "미측정";
        
        if (averageResponseTime < 200) return "매우 빠름";
        if (averageResponseTime < 500) return "빠름";
        if (averageResponseTime < 1000) return "보통";
        if (averageResponseTime < 2000) return "느림";
        return "매우 느림";
    }
}
```

---

## 🔧 설정 클래스

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
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * API 로그 설정
 */
@Configuration
@EnableCaching
@EnableAsync
@EnableScheduling
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
        
        // API 통계 캐시 (15분)
        cacheConfigurations.put("apiStats:performance", 
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15)));
        
        cacheConfigurations.put("apiStats:usage", 
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)));
        
        cacheConfigurations.put("apiStats:popular", 
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)));
        
        return RedisCacheManager.builder(connectionFactory)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
```

---

## 📈 성능 최적화 특징

### 1. **비동기 로깅**
- `@Async` 어노테이션으로 로그 저장 비동기 처리
- API 응답 속도에 영향 없는 백그라운드 로깅
- `CompletableFuture` 반환으로 논블로킹 처리

### 2. **지능형 캐싱**
- Redis 기반 다층 캐시 전략
- 조회 빈도별 차등 TTL 설정
- 실시간 데이터와 통계 데이터 분리

### 3. **실시간 모니터링**
- 성능 임계치 기반 자동 알림
- 에러율 모니터링 및 이상 탐지
- 느린 요청 실시간 감지

### 4. **최적화된 쿼리**
- Repository 인덱스 활용한 고성능 조회
- Projection 사용으로 필요한 데이터만 조회
- 페이징 처리로 대용량 데이터 효율 관리

---

## 🛡️ 보안 및 안정성

### 1. **민감정보 보호**
- 클라이언트 IP 기록으로 추적 가능
- User-Agent 정보로 클라이언트 식별
- 에러 메시지 민감정보 필터링

### 2. **데이터 관리**
- 로그 보관 정책 (성공 30일, 에러 90일)
- 자동 정리 스케줄링
- 스토리지 최적화

### 3. **장애 대응**
- 로그 저장 실패 시 시스템 영향 최소화
- 예외 처리로 서비스 안정성 확보
- 백업 로깅 메커니즘

---

**📝 다음 단계**: step6-6b_external_api_service.md  
**완료일**: 2025-08-22  
**핵심 성과**: API 로그 관리 + 성능 모니터링 + 에러 분석 + 실시간 알림 완성