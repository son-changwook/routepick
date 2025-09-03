# Step 6-6a2: API 성능 모니터링 시스템

> API 성능 분석, 통계 생성 및 실시간 모니터링
> 생성일: 2025-08-22
> 단계: 6-6a2 (Service 레이어 - API 성능 모니터링)
> 기반: step4-4c2_system_logging_entities.md, step5-4f3_system_management_repositories.md

---

## 🎯 설계 목표

- **성능 모니터링**: 응답 시간, 처리량, 에러율 실시간 분석
- **에러 분석**: 에러 패턴 분석 및 문제 엔드포인트 탐지
- **사용량 분석**: 사용자별, 엔드포인트별 API 사용 패턴 분석
- **알림 시스템**: 임계치 초과 시 자동 알림 발송
- **최적화**: 느린 API 탐지 및 개선 제안

---

## ✅ ApiPerformanceMonitoringService.java

```java
package com.routepick.service.system;

import com.routepick.domain.system.repository.ApiLogRepository;
import com.routepick.dto.system.ApiPerformanceDto;
import com.routepick.dto.system.ApiUsageStatisticsDto;
import com.routepick.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * API 성능 모니터링 서비스
 * - 성능 분석 및 최적화 제안
 * - 에러 패턴 분석 및 알림
 * - 사용량 통계 및 트렌드 분석
 * - 시스템 헬스 체크 및 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiPerformanceMonitoringService {
    
    private final ApiLogRepository apiLogRepository;
    private final NotificationService notificationService;
    
    // 성능 임계치 설정
    private static final long SLOW_REQUEST_THRESHOLD = 1000L; // 1초
    private static final double ERROR_RATE_THRESHOLD = 0.05; // 5%
    
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
                        "maxTime", row[2],
                        "callCount", row[3]
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
                    "errorCount", row[2],
                    "avgResponseTime", row[3],
                    "errorRate", calculateErrorRate((Long) row[2], (Long) row[4])
                ))
                .collect(Collectors.toList());
    }
    
    /**
     * 성능 개선 제안 생성
     */
    @Cacheable(value = "apiStats:recommendations", key = "#hours")
    public List<Map<String, Object>> getPerformanceRecommendations(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<Map<String, Object>> recommendations = new java.util.ArrayList<>();
        
        // 느린 엔드포인트 탐지
        List<Object[]> slowEndpoints = apiLogRepository.findSlowEndpoints(since, 
                PageRequest.of(0, 10));
        
        for (Object[] row : slowEndpoints) {
            String endpoint = (String) row[0];
            Double avgTime = (Double) row[1];
            Long maxTime = (Long) row[2];
            Long callCount = (Long) row[3];
            
            if (avgTime > SLOW_REQUEST_THRESHOLD) {
                recommendations.add(Map.of(
                    "type", "SLOW_ENDPOINT",
                    "priority", avgTime > 3000 ? "HIGH" : "MEDIUM",
                    "endpoint", endpoint,
                    "issue", "평균 응답시간이 " + avgTime + "ms로 느립니다",
                    "suggestion", "쿼리 최적화, 인덱스 추가, 캐싱 적용을 검토하세요",
                    "avgTime", avgTime,
                    "maxTime", maxTime,
                    "callCount", callCount
                ));
            }
        }
        
        // 에러율 높은 엔드포인트 탐지
        List<Object[]> errorEndpoints = apiLogRepository.findHighErrorRateEndpoints(since, 0.1); // 10%
        
        for (Object[] row : errorEndpoints) {
            String endpoint = (String) row[0];
            Long errorCount = (Long) row[1];
            Long totalCount = (Long) row[2];
            double errorRate = (double) errorCount / totalCount;
            
            recommendations.add(Map.of(
                "type", "HIGH_ERROR_RATE",
                "priority", errorRate > 0.2 ? "CRITICAL" : "HIGH",
                "endpoint", endpoint,
                "issue", String.format("에러율이 %.1f%%로 높습니다", errorRate * 100),
                "suggestion", "에러 원인 분석, 입력 검증 강화, 예외 처리 개선이 필요합니다",
                "errorRate", errorRate,
                "errorCount", errorCount,
                "totalCount", totalCount
            ));
        }
        
        return recommendations;
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
                    "uniqueEndpoints", row[2],
                    "avgResponseTime", row[3],
                    "errorCount", row[4]
                ))
                .collect(Collectors.toList());
    }
    
    /**
     * API 사용 트렌드 분석
     */
    @Cacheable(value = "apiStats:trends", key = "#days")
    public Map<String, Object> getApiUsageTrends(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        // 일별 사용량 트렌드
        List<Object[]> dailyStats = apiLogRepository.findDailyApiUsageStats(startDate);
        
        // 시간대별 사용량 패턴
        List<Object[]> hourlyPattern = apiLogRepository.findHourlyUsagePattern(startDate);
        
        // 인기 엔드포인트 변화
        List<Object[]> endpointTrends = apiLogRepository.findEndpointUsageTrends(startDate);
        
        return Map.of(
            "dailyUsage", dailyStats.stream()
                .collect(Collectors.toMap(
                    row -> row[0], // date
                    row -> Map.of(
                        "totalCalls", row[1],
                        "uniqueUsers", row[2],
                        "avgResponseTime", row[3]
                    )
                )),
            "hourlyPattern", hourlyPattern.stream()
                .collect(Collectors.toMap(
                    row -> row[0], // hour
                    row -> row[1]  // call count
                )),
            "endpointTrends", endpointTrends.stream()
                .collect(Collectors.toMap(
                    row -> row[0], // endpoint
                    row -> Map.of(
                        "currentRank", row[1],
                        "previousRank", row[2],
                        "change", row[3]
                    )
                ))
        );
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
                
                // 성능 저하 체크
                checkPerformanceDegradation(since);
                
                // 비정상적인 트래픽 패턴 체크
                checkAbnormalTrafficPattern(since);
            }
            
            log.debug("시스템 헬스 체크 완료");
            
        } catch (Exception e) {
            log.error("시스템 헬스 체크 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 성능 저하 체크
     */
    private void checkPerformanceDegradation(LocalDateTime since) {
        List<Object[]> currentStats = apiLogRepository.findSlowEndpoints(since, 
                PageRequest.of(0, 5));
        
        LocalDateTime previousHour = since.minusHours(1);
        List<Object[]> previousStats = apiLogRepository.findSlowEndpoints(previousHour, 
                PageRequest.of(0, 5));
        
        // 응답 시간 비교
        for (Object[] current : currentStats) {
            String endpoint = (String) current[0];
            Double currentAvg = (Double) current[1];
            
            previousStats.stream()
                .filter(prev -> endpoint.equals(prev[0]))
                .findFirst()
                .ifPresent(prev -> {
                    Double previousAvg = (Double) prev[1];
                    double degradation = (currentAvg - previousAvg) / previousAvg;
                    
                    // 20% 이상 성능 저하 시 알림
                    if (degradation > 0.2) {
                        notifyPerformanceDegradation(endpoint, previousAvg, currentAvg, degradation);
                    }
                });
        }
    }
    
    /**
     * 비정상적인 트래픽 패턴 체크
     */
    private void checkAbnormalTrafficPattern(LocalDateTime since) {
        Long currentHourCalls = apiLogRepository.countApiCallsSince(since);
        Long previousHourCalls = apiLogRepository.countApiCallsSince(since.minusHours(1));
        
        if (previousHourCalls > 0) {
            double changeRate = (double) (currentHourCalls - previousHourCalls) / previousHourCalls;
            
            // 200% 이상 증가 시 급증 알림
            if (changeRate > 2.0) {
                notifyTrafficSpike(currentHourCalls, previousHourCalls, changeRate);
            }
            // 80% 이상 감소 시 급감 알림
            else if (changeRate < -0.8) {
                notifyTrafficDrop(currentHourCalls, previousHourCalls, changeRate);
            }
        }
    }
    
    // ===== 알림 메서드들 =====
    
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
    
    /**
     * 성능 저하 알림
     */
    private void notifyPerformanceDegradation(String endpoint, Double previousAvg, 
                                            Double currentAvg, double degradation) {
        try {
            String message = String.format(
                "API 성능 저하 탐지: %s (%.0fms → %.0fms, %.1f%% 증가)",
                endpoint, previousAvg, currentAvg, degradation * 100
            );
            
            notificationService.sendSystemAlert("PERFORMANCE_DEGRADATION", message, Map.of(
                "endpoint", endpoint,
                "previousAvg", previousAvg,
                "currentAvg", currentAvg,
                "degradation", degradation
            ));
            
        } catch (Exception e) {
            log.error("성능 저하 알림 발송 실패", e);
        }
    }
    
    /**
     * 트래픽 급증 알림
     */
    private void notifyTrafficSpike(Long currentCalls, Long previousCalls, double changeRate) {
        try {
            String message = String.format(
                "API 트래픽 급증: %d → %d (%.0f%% 증가)",
                previousCalls, currentCalls, changeRate * 100
            );
            
            notificationService.sendSystemAlert("TRAFFIC_SPIKE", message, Map.of(
                "currentCalls", currentCalls,
                "previousCalls", previousCalls,
                "changeRate", changeRate
            ));
            
        } catch (Exception e) {
            log.error("트래픽 급증 알림 발송 실패", e);
        }
    }
    
    /**
     * 트래픽 급감 알림
     */
    private void notifyTrafficDrop(Long currentCalls, Long previousCalls, double changeRate) {
        try {
            String message = String.format(
                "API 트래픽 급감: %d → %d (%.0f%% 감소)",
                previousCalls, currentCalls, Math.abs(changeRate) * 100
            );
            
            notificationService.sendSystemAlert("TRAFFIC_DROP", message, Map.of(
                "currentCalls", currentCalls,
                "previousCalls", previousCalls,
                "changeRate", changeRate
            ));
            
        } catch (Exception e) {
            log.error("트래픽 급감 알림 발송 실패", e);
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 에러율 계산
     */
    private double calculateErrorRate(Long errorCount, Long totalCount) {
        return totalCount > 0 ? (double) errorCount / totalCount : 0.0;
    }
}
```

---

## 📊 DTO 클래스들

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

## 🔧 캐시 설정 확장

```yaml
# application.yml에 추가
spring:
  cache:
    redis:
      cache-configurations:
        apiStats:performance:
          ttl: 15m
        apiStats:usage:
          ttl: 30m
        apiStats:trends:
          ttl: 1h
        apiStats:recommendations:
          ttl: 20m
```

---

## 📈 주요 특징

### 1. **실시간 모니터링**
- 성능 임계치 기반 자동 알림
- 에러율 모니터링 및 이상 탐지
- 트래픽 패턴 분석 및 급등/급감 감지

### 2. **지능형 분석**
- 성능 개선 제안 자동 생성
- 문제 엔드포인트 탐지
- 사용 트렌드 분석

### 3. **예측적 알림**
- 성능 저하 조기 감지
- 비정상 트래픽 패턴 알림
- 임계치 기반 사전 알림

### 4. **효율적 캐싱**
- 분석 유형별 차등 TTL 적용
- 복잡한 통계 쿼리 결과 캐싱
- 실시간성과 성능 균형

---

**📝 연계 파일**: step6-6a1_api_logging_core.md와 함께 사용  
**완료일**: 2025-08-22  
**핵심 성과**: API 성능 모니터링 + 실시간 알림 + 개선 제안 완성