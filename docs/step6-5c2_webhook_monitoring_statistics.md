# Step 6-5c2: Webhook Monitoring & Statistics

**파일들**: 웹훅 통계, 모니터링, 로그 관리 시스템 구현

이 파일은 `step6-5c1_webhook_processing_core.md`와 연계된 웹훅 모니터링 및 통계 시스템입니다.

## 📊 웹훅 통계 및 모니터링 서비스

```java
package com.routepick.service.webhook;

import com.routepick.common.enums.WebhookStatus;
import com.routepick.common.enums.WebhookType;
import com.routepick.common.enums.ApiProvider;
import com.routepick.domain.system.entity.WebhookLog;
import com.routepick.domain.system.repository.WebhookLogRepository;
import com.routepick.dto.webhook.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 웹훅 모니터링 및 통계 서비스
 * 
 * 주요 기능:
 * 1. 웹훅 통계 수집 및 분석
 * 2. 실시간 모니터링 대시보드
 * 3. 로그 관리 및 정리
 * 4. 알림 및 경고 시스템
 * 5. 성능 분석 리포트
 * 
 * @author RoutePickr Team
 * @since 2025.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookStatisticsService {
    
    private final WebhookLogRepository webhookLogRepository;
    private final NotificationService notificationService;
    
    // 캐시 이름
    private static final String CACHE_WEBHOOK_STATS = "webhookStats";
    private static final String CACHE_WEBHOOK_SUMMARY = "webhookSummary";
    
    // ===================== 통계 조회 =====================
    
    /**
     * 웹훅 통계 조회
     */
    @Cacheable(value = CACHE_WEBHOOK_STATS,
              key = "#startDate + '_' + #endDate + '_' + #provider")
    public WebhookStatistics getWebhookStatistics(LocalDateTime startDate, LocalDateTime endDate,
                                                 ApiProvider provider) {
        log.debug("Getting webhook statistics: startDate={}, endDate={}, provider={}", 
                 startDate, endDate, provider);
        
        Long totalCount = webhookLogRepository.countByDateRange(startDate, endDate, provider);
        Long successCount = webhookLogRepository.countByDateRangeAndStatus(
            startDate, endDate, WebhookStatus.PROCESSED, provider
        );
        Long failedCount = webhookLogRepository.countByDateRangeAndStatus(
            startDate, endDate, WebhookStatus.FAILED, provider
        );
        Long retryingCount = webhookLogRepository.countByDateRangeAndStatus(
            startDate, endDate, WebhookStatus.RETRYING, provider
        );
        Long abandonedCount = webhookLogRepository.countByDateRangeAndStatus(
            startDate, endDate, WebhookStatus.ABANDONED, provider
        );
        
        // 타입별 통계
        List<WebhookTypeStatistics> typeStats = webhookLogRepository
            .getWebhookTypeStatistics(startDate, endDate, provider);
        
        // 시간별 통계 (24시간)
        List<WebhookHourlyStatistics> hourlyStats = getHourlyStatistics(startDate, endDate, provider);
        
        // 성공률 계산
        BigDecimal successRate = totalCount > 0 ?
            BigDecimal.valueOf(successCount).divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;
        
        // 평균 처리 시간
        Double avgProcessingTime = webhookLogRepository.getAverageProcessingTime(startDate, endDate, provider);
        
        return WebhookStatistics.builder()
            .startDate(startDate)
            .endDate(endDate)
            .provider(provider)
            .totalCount(totalCount)
            .successCount(successCount)
            .failedCount(failedCount)
            .retryingCount(retryingCount)
            .abandonedCount(abandonedCount)
            .successRate(successRate)
            .averageProcessingTimeSeconds(avgProcessingTime != null ? avgProcessingTime : 0.0)
            .typeStatistics(typeStats)
            .hourlyStatistics(hourlyStats)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 웹훅 요약 통계
     */
    @Cacheable(value = CACHE_WEBHOOK_SUMMARY, unless = "#result == null")
    public WebhookSummary getWebhookSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24Hours = now.minusHours(24);
        LocalDateTime last7Days = now.minusDays(7);
        LocalDateTime last30Days = now.minusDays(30);
        
        // 24시간 통계
        WebhookPeriodSummary last24HoursSummary = getPeriodSummary(last24Hours, now);
        
        // 7일 통계
        WebhookPeriodSummary last7DaysSummary = getPeriodSummary(last7Days, now);
        
        // 30일 통계
        WebhookPeriodSummary last30DaysSummary = getPeriodSummary(last30Days, now);
        
        // 최근 실패한 웹훅들
        List<WebhookLog> recentFailures = webhookLogRepository
            .findRecentFailedWebhooks(last24Hours, 10);
        
        return WebhookSummary.builder()
            .last24Hours(last24HoursSummary)
            .last7Days(last7DaysSummary)
            .last30Days(last30DaysSummary)
            .recentFailures(recentFailures.stream()
                .map(this::convertToFailureInfo)
                .collect(Collectors.toList()))
            .generatedAt(now)
            .build();
    }
    
    /**
     * 웹훅 성능 분석
     */
    public WebhookPerformanceAnalysis getPerformanceAnalysis(LocalDateTime startDate, LocalDateTime endDate,
                                                           ApiProvider provider) {
        log.info("Generating webhook performance analysis: startDate={}, endDate={}, provider={}", 
                startDate, endDate, provider);
        
        // 처리 시간 분포
        List<WebhookProcessingTimeDistribution> processingTimeDistribution = 
            webhookLogRepository.getProcessingTimeDistribution(startDate, endDate, provider);
        
        // 재시도 분석
        List<WebhookRetryAnalysis> retryAnalysis = 
            webhookLogRepository.getRetryAnalysis(startDate, endDate, provider);
        
        // 오류 분석
        List<WebhookErrorAnalysis> errorAnalysis = 
            webhookLogRepository.getErrorAnalysis(startDate, endDate, provider);
        
        // 처리량 추이
        List<WebhookThroughputTrend> throughputTrend = 
            getThroughputTrend(startDate, endDate, provider);
        
        return WebhookPerformanceAnalysis.builder()
            .startDate(startDate)
            .endDate(endDate)
            .provider(provider)
            .processingTimeDistribution(processingTimeDistribution)
            .retryAnalysis(retryAnalysis)
            .errorAnalysis(errorAnalysis)
            .throughputTrend(throughputTrend)
            .analyzedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 웹훅 로그 조회 (페이징)
     */
    public Page<WebhookLogResponse> getWebhookLogs(WebhookLogSearchRequest request, Pageable pageable) {
        log.debug("Searching webhook logs: {}", request);
        
        Page<WebhookLog> logs = webhookLogRepository.searchWebhookLogs(
            request.getStartDate(),
            request.getEndDate(),
            request.getProvider(),
            request.getWebhookType(),
            request.getStatus(),
            request.getWebhookId(),
            pageable
        );
        
        return logs.map(this::convertToWebhookLogResponse);
    }
    
    // ===================== 모니터링 및 알림 =====================
    
    /**
     * 웹훅 건강 상태 체크
     */
    @Scheduled(fixedDelay = 300000) // 5분마다
    public void checkWebhookHealth() {
        log.debug("Checking webhook health");
        
        LocalDateTime last30Minutes = LocalDateTime.now().minusMinutes(30);
        
        // 각 제공자별 건강 상태 체크
        for (ApiProvider provider : ApiProvider.values()) {
            WebhookHealthStatus health = checkProviderHealth(provider, last30Minutes);
            
            if (health.getStatus() == HealthStatus.UNHEALTHY) {
                sendHealthAlert(provider, health);
            }
        }
    }
    
    /**
     * 특정 제공자의 건강 상태 체크
     */
    private WebhookHealthStatus checkProviderHealth(ApiProvider provider, LocalDateTime since) {
        Long totalCount = webhookLogRepository.countByDateRange(since, LocalDateTime.now(), provider);
        Long successCount = webhookLogRepository.countByDateRangeAndStatus(
            since, LocalDateTime.now(), WebhookStatus.PROCESSED, provider
        );
        Long failedCount = webhookLogRepository.countByDateRangeAndStatus(
            since, LocalDateTime.now(), WebhookStatus.FAILED, provider
        );
        
        HealthStatus status = HealthStatus.HEALTHY;
        List<String> issues = new ArrayList<>();
        
        // 성공률이 95% 미만이면 경고
        if (totalCount > 0) {
            double successRate = (double) successCount / totalCount;
            if (successRate < 0.95) {
                status = HealthStatus.WARNING;
                issues.add(String.format("성공률 낮음: %.2f%%", successRate * 100));
            }
            
            // 성공률이 80% 미만이면 불건전
            if (successRate < 0.80) {
                status = HealthStatus.UNHEALTHY;
            }
        }
        
        // 실패 건수가 너무 많으면 경고
        if (failedCount > 10) {
            status = HealthStatus.WARNING;
            issues.add(String.format("높은 실패 건수: %d", failedCount));
        }
        
        if (failedCount > 50) {
            status = HealthStatus.UNHEALTHY;
        }
        
        return WebhookHealthStatus.builder()
            .provider(provider)
            .status(status)
            .totalCount(totalCount)
            .successCount(successCount)
            .failedCount(failedCount)
            .issues(issues)
            .checkedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 건강 상태 알림 발송
     */
    private void sendHealthAlert(ApiProvider provider, WebhookHealthStatus health) {
        log.warn("Webhook health alert for provider {}: status={}, issues={}", 
                provider, health.getStatus(), health.getIssues());
        
        String message = String.format(
            "[웹훅 건강상태 알림]\n제공자: %s\n상태: %s\n문제점: %s\n확인시간: %s",
            provider,
            health.getStatus(),
            String.join(", ", health.getIssues()),
            health.getCheckedAt()
        );
        
        // 관리자에게 알림 발송
        notificationService.sendAdminAlert("웹훅 건강상태 알림", message);
    }
    
    /**
     * 실시간 웹훅 모니터링 데이터
     */
    public WebhookRealtimeMonitoring getRealtimeMonitoring() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last5Minutes = now.minusMinutes(5);
        
        // 최근 5분간 처리량
        Long recentThroughput = webhookLogRepository.countByDateRange(last5Minutes, now, null);
        
        // 현재 처리 중인 웹훅 수 (메모리에서)
        int processingCount = getProcessingWebhookCount();
        
        // 최근 평균 처리 시간
        Double avgProcessingTime = webhookLogRepository.getAverageProcessingTime(last5Minutes, now, null);
        
        // 제공자별 현황
        List<WebhookProviderStatus> providerStatuses = Arrays.stream(ApiProvider.values())
            .map(provider -> getProviderRealtimeStatus(provider, last5Minutes, now))
            .collect(Collectors.toList());
        
        return WebhookRealtimeMonitoring.builder()
            .currentThroughput(recentThroughput)
            .processingCount(processingCount)
            .averageProcessingTimeSeconds(avgProcessingTime != null ? avgProcessingTime : 0.0)
            .providerStatuses(providerStatuses)
            .lastUpdated(now)
            .build();
    }
    
    // ===================== 로그 관리 =====================
    
    /**
     * 실패한 웹훅 정리 (스케줄링)
     */
    @Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시
    @Transactional
    public void cleanupFailedWebhooks() {
        log.info("Cleaning up failed webhooks");
        
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        
        // 7일 이상 된 실패/포기 웹훅 삭제
        int deletedCount = webhookLogRepository.deleteOldFailedWebhooks(cutoff);
        
        log.info("Cleaned up {} failed webhooks", deletedCount);
    }
    
    /**
     * 성공한 웹훅 로그 정리 (보관 기간: 30일)
     */
    @Scheduled(cron = "0 30 2 * * ?") // 매일 새벽 2시 30분
    @Transactional
    public void cleanupSuccessfulWebhooks() {
        log.info("Cleaning up old successful webhooks");
        
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        
        // 30일 이상 된 성공 웹훅 삭제
        int deletedCount = webhookLogRepository.deleteOldSuccessfulWebhooks(cutoff);
        
        log.info("Cleaned up {} successful webhooks", deletedCount);
    }
    
    /**
     * 웹훅 로그 아카이브
     */
    @Scheduled(cron = "0 0 3 1 * ?") // 매월 1일 새벽 3시
    @Transactional
    public void archiveOldWebhooks() {
        log.info("Archiving old webhook logs");
        
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(3);
        
        // 3개월 이상 된 로그를 아카이브 테이블로 이동
        int archivedCount = webhookLogRepository.archiveOldWebhooks(cutoff);
        
        log.info("Archived {} old webhooks", archivedCount);
    }
    
    // ===================== Helper 메서드 =====================
    
    private WebhookPeriodSummary getPeriodSummary(LocalDateTime startDate, LocalDateTime endDate) {
        Long totalCount = webhookLogRepository.countByDateRange(startDate, endDate, null);
        Long successCount = webhookLogRepository.countByDateRangeAndStatus(
            startDate, endDate, WebhookStatus.PROCESSED, null
        );
        Long failedCount = webhookLogRepository.countByDateRangeAndStatus(
            startDate, endDate, WebhookStatus.FAILED, null
        );
        
        BigDecimal successRate = totalCount > 0 ?
            BigDecimal.valueOf(successCount).divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;
        
        return WebhookPeriodSummary.builder()
            .totalCount(totalCount)
            .successCount(successCount)
            .failedCount(failedCount)
            .successRate(successRate)
            .build();
    }
    
    private List<WebhookHourlyStatistics> getHourlyStatistics(LocalDateTime startDate, LocalDateTime endDate, 
                                                            ApiProvider provider) {
        // 시간대별 통계 수집
        List<WebhookHourlyStatistics> hourlyStats = new ArrayList<>();
        
        LocalDateTime current = startDate.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime end = endDate.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        
        while (current.isBefore(end)) {
            LocalDateTime nextHour = current.plusHours(1);
            
            Long totalCount = webhookLogRepository.countByDateRange(current, nextHour, provider);
            Long successCount = webhookLogRepository.countByDateRangeAndStatus(
                current, nextHour, WebhookStatus.PROCESSED, provider
            );
            Long failedCount = webhookLogRepository.countByDateRangeAndStatus(
                current, nextHour, WebhookStatus.FAILED, provider
            );
            
            hourlyStats.add(WebhookHourlyStatistics.builder()
                .hour(current)
                .totalCount(totalCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .build());
            
            current = nextHour;
        }
        
        return hourlyStats;
    }
    
    private List<WebhookThroughputTrend> getThroughputTrend(LocalDateTime startDate, LocalDateTime endDate,
                                                          ApiProvider provider) {
        // 처리량 추이 분석 (1시간 단위)
        return getHourlyStatistics(startDate, endDate, provider).stream()
            .map(hourly -> WebhookThroughputTrend.builder()
                .timestamp(hourly.getHour())
                .throughput(hourly.getTotalCount())
                .successThroughput(hourly.getSuccessCount())
                .failedThroughput(hourly.getFailedCount())
                .build())
            .collect(Collectors.toList());
    }
    
    private WebhookFailureInfo convertToFailureInfo(WebhookLog log) {
        return WebhookFailureInfo.builder()
            .webhookId(log.getWebhookId())
            .webhookType(log.getWebhookType())
            .provider(log.getProvider())
            .failedAt(log.getCreatedAt())
            .retryCount(log.getRetryCount())
            .errorMessage(log.getErrorMessage())
            .status(log.getStatus())
            .build();
    }
    
    private WebhookLogResponse convertToWebhookLogResponse(WebhookLog log) {
        return WebhookLogResponse.builder()
            .logId(log.getLogId())
            .webhookId(log.getWebhookId())
            .webhookType(log.getWebhookType())
            .provider(log.getProvider())
            .status(log.getStatus())
            .retryCount(log.getRetryCount())
            .processingTimeSeconds(calculateProcessingTime(log))
            .errorMessage(log.getErrorMessage())
            .createdAt(log.getCreatedAt())
            .processedAt(log.getProcessedAt())
            .lastRetryAt(log.getLastRetryAt())
            .build();
    }
    
    private Double calculateProcessingTime(WebhookLog log) {
        if (log.getProcessedAt() != null) {
            return (double) ChronoUnit.MILLIS.between(log.getCreatedAt(), log.getProcessedAt()) / 1000.0;
        }
        return null;
    }
    
    private WebhookProviderStatus getProviderRealtimeStatus(ApiProvider provider, 
                                                           LocalDateTime startDate, LocalDateTime endDate) {
        Long totalCount = webhookLogRepository.countByDateRange(startDate, endDate, provider);
        Long successCount = webhookLogRepository.countByDateRangeAndStatus(
            startDate, endDate, WebhookStatus.PROCESSED, provider
        );
        Long failedCount = webhookLogRepository.countByDateRangeAndStatus(
            startDate, endDate, WebhookStatus.FAILED, provider
        );
        
        return WebhookProviderStatus.builder()
            .provider(provider)
            .totalCount(totalCount)
            .successCount(successCount)
            .failedCount(failedCount)
            .build();
    }
    
    private int getProcessingWebhookCount() {
        // WebhookService에서 현재 처리 중인 웹훅 수를 가져옴
        // 실제 구현에서는 적절한 방법으로 처리 중인 웹훅 수를 조회
        return 0; // 플레이스홀더
    }
}
```

## 📋 웹훅 통계 DTO 클래스

```java
/**
 * 웹훅 통계 응답
 */
@Data
@Builder
public class WebhookStatistics {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ApiProvider provider;
    private Long totalCount;
    private Long successCount;
    private Long failedCount;
    private Long retryingCount;
    private Long abandonedCount;
    private BigDecimal successRate;
    private Double averageProcessingTimeSeconds;
    private List<WebhookTypeStatistics> typeStatistics;
    private List<WebhookHourlyStatistics> hourlyStatistics;
    private LocalDateTime generatedAt;
}

/**
 * 웹훅 요약 통계
 */
@Data
@Builder
public class WebhookSummary {
    private WebhookPeriodSummary last24Hours;
    private WebhookPeriodSummary last7Days;
    private WebhookPeriodSummary last30Days;
    private List<WebhookFailureInfo> recentFailures;
    private LocalDateTime generatedAt;
}

/**
 * 웹훅 성능 분석
 */
@Data
@Builder
public class WebhookPerformanceAnalysis {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ApiProvider provider;
    private List<WebhookProcessingTimeDistribution> processingTimeDistribution;
    private List<WebhookRetryAnalysis> retryAnalysis;
    private List<WebhookErrorAnalysis> errorAnalysis;
    private List<WebhookThroughputTrend> throughputTrend;
    private LocalDateTime analyzedAt;
}

/**
 * 웹훅 실시간 모니터링
 */
@Data
@Builder
public class WebhookRealtimeMonitoring {
    private Long currentThroughput;
    private Integer processingCount;
    private Double averageProcessingTimeSeconds;
    private List<WebhookProviderStatus> providerStatuses;
    private LocalDateTime lastUpdated;
}

/**
 * 웹훅 건강 상태
 */
@Data
@Builder
public class WebhookHealthStatus {
    private ApiProvider provider;
    private HealthStatus status;
    private Long totalCount;
    private Long successCount;
    private Long failedCount;
    private List<String> issues;
    private LocalDateTime checkedAt;
}

/**
 * 건강 상태 열거형
 */
public enum HealthStatus {
    HEALTHY,    // 정상
    WARNING,    // 경고
    UNHEALTHY   // 불건전
}

/**
 * 웹훅 로그 검색 요청
 */
@Data
public class WebhookLogSearchRequest {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ApiProvider provider;
    private WebhookType webhookType;
    private WebhookStatus status;
    private String webhookId;
}

/**
 * 웹훅 로그 응답
 */
@Data
@Builder
public class WebhookLogResponse {
    private Long logId;
    private String webhookId;
    private WebhookType webhookType;
    private ApiProvider provider;
    private WebhookStatus status;
    private Integer retryCount;
    private Double processingTimeSeconds;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private LocalDateTime lastRetryAt;
}
```

## 📈 웹훅 대시보드 설정

```yaml
# 웹훅 모니터링 설정
app:
  webhook:
    monitoring:
      health-check:
        enabled: true
        interval: 300s  # 5분마다
        success-rate-threshold: 0.95  # 95% 미만시 경고
        failure-threshold: 10  # 10회 이상 실패시 경고
        
      statistics:
        cache-ttl: 300s  # 5분 캐시
        real-time-interval: 30s  # 30초마다 갱신
        
      cleanup:
        failed-webhooks:
          enabled: true
          retention-days: 7
          schedule: "0 0 2 * * ?"
          
        successful-webhooks:
          enabled: true
          retention-days: 30
          schedule: "0 30 2 * * ?"
          
        archive:
          enabled: true
          retention-months: 3
          schedule: "0 0 3 1 * ?"
          
      alerts:
        admin-notification: true
        slack-webhook: ${SLACK_WEBHOOK_URL:}
        email-notification: true
```

## 🔄 모니터링 대시보드 API

```java
/**
 * 웹훅 모니터링 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/webhooks/monitoring")
@RequiredArgsConstructor
public class WebhookMonitoringController {
    
    private final WebhookStatisticsService statisticsService;
    
    @GetMapping("/summary")
    public ResponseEntity<WebhookSummary> getSummary() {
        return ResponseEntity.ok(statisticsService.getWebhookSummary());
    }
    
    @GetMapping("/statistics")
    public ResponseEntity<WebhookStatistics> getStatistics(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            @RequestParam(required = false) ApiProvider provider) {
        return ResponseEntity.ok(statisticsService.getWebhookStatistics(startDate, endDate, provider));
    }
    
    @GetMapping("/realtime")
    public ResponseEntity<WebhookRealtimeMonitoring> getRealtimeMonitoring() {
        return ResponseEntity.ok(statisticsService.getRealtimeMonitoring());
    }
    
    @GetMapping("/performance")
    public ResponseEntity<WebhookPerformanceAnalysis> getPerformanceAnalysis(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            @RequestParam(required = false) ApiProvider provider) {
        return ResponseEntity.ok(statisticsService.getPerformanceAnalysis(startDate, endDate, provider));
    }
    
    @GetMapping("/logs")
    public ResponseEntity<Page<WebhookLogResponse>> getLogs(
            WebhookLogSearchRequest request, Pageable pageable) {
        return ResponseEntity.ok(statisticsService.getWebhookLogs(request, pageable));
    }
}
```

## 📊 연동 참고사항

### step6-5c1_webhook_processing_core.md 연동점
1. **통계 수집**: 웹훅 처리 결과를 실시간으로 통계화
2. **모니터링**: 웹훅 처리 상태 실시간 추적
3. **알림**: 웹훅 실패 및 건강상태 알림
4. **로그 관리**: 웹훅 로그의 생명주기 관리

### 성능 최적화
1. **캐싱**: 통계 데이터 캐싱으로 응답 속도 향상
2. **배치 처리**: 대량 로그 정리 작업 최적화
3. **인덱싱**: 통계 쿼리 성능 향상을 위한 인덱스 전략
4. **아카이빙**: 오래된 데이터의 별도 보관으로 성능 유지

### 확장성 고려사항
1. **샤딩**: 대량 웹훅 로그 처리를 위한 DB 샤딩
2. **파티셔닝**: 시간 기반 테이블 파티셔닝
3. **스트리밍**: 실시간 통계를 위한 스트리밍 처리
4. **분산 처리**: 통계 계산의 분산 처리

---
**연관 파일**: `step6-5c1_webhook_processing_core.md`
**구현 우선순위**: MEDIUM (모니터링 시스템)
**예상 개발 기간**: 2-3일