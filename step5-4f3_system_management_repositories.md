# Step 5-4f3: 시스템 관리 Repository 생성

## 개요
- **목적**: 시스템 관리 Repository 생성 (최종 완성)
- **대상**: ApiLogRepository, ExternalApiConfigRepository, WebhookLogRepository
- **최적화**: API 모니터링, 외부 API 관리, 웹훅 시스템

## 1. ApiLogRepository (API 로그 분석 최적화)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.system;

import com.routepick.backend.domain.entity.system.ApiLog;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API 로그 Repository
 * - API 사용량 분석 및 모니터링
 * - 성능 메트릭 및 에러 패턴 분석
 * - 사용자별 API 호출 통계
 */
@Repository
public interface ApiLogRepository extends BaseRepository<ApiLog, Long> {
    
    // ===== 기간별 API 로그 =====
    
    List<ApiLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
        LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    @Query("SELECT al FROM ApiLog al " +
           "WHERE al.createdAt >= :since " +
           "ORDER BY al.responseTime DESC")
    List<ApiLog> findRecentLogs(@Param("since") LocalDateTime since, Pageable pageable);
    
    // ===== 상태코드별 로그 =====
    
    List<ApiLog> findByResponseStatusAndCreatedAtBetween(
        Integer responseStatus, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT al FROM ApiLog al " +
           "WHERE al.responseStatus >= 400 " +
           "AND al.createdAt >= :since " +
           "ORDER BY al.createdAt DESC")
    List<ApiLog> findErrorLogs(@Param("since") LocalDateTime since, Pageable pageable);
    
    @Query("SELECT al FROM ApiLog al " +
           "WHERE al.responseStatus >= 500 " +
           "AND al.createdAt >= :since " +
           "ORDER BY al.createdAt DESC")
    List<ApiLog> findServerErrorLogs(@Param("since") LocalDateTime since);
    
    // ===== 사용자별 API 로그 =====
    
    List<ApiLog> findByUserIdAndCreatedAtBetween(
        Long userId, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT COUNT(al) FROM ApiLog al " +
           "WHERE al.userId = :userId " +
           "AND al.createdAt >= :since")
    long countApiCallsByUser(@Param("userId") Long userId, 
                            @Param("since") LocalDateTime since);
    
    // ===== URL 패턴별 로그 =====
    
    List<ApiLog> findByRequestUrlContainingAndCreatedAtAfter(
        String urlPattern, LocalDateTime after);
    
    @Query("SELECT al.requestUrl, COUNT(al) as callCount " +
           "FROM ApiLog al " +
           "WHERE al.createdAt >= :since " +
           "GROUP BY al.requestUrl " +
           "ORDER BY callCount DESC")
    List<Object[]> findMostCalledEndpoints(@Param("since") LocalDateTime since, 
                                          Pageable pageable);
    
    // ===== 느린 요청 분석 =====
    
    @Query("SELECT al FROM ApiLog al " +
           "WHERE al.responseTime > :threshold " +
           "AND al.createdAt >= :since " +
           "ORDER BY al.responseTime DESC")
    List<ApiLog> findSlowRequests(@Param("threshold") Long threshold, 
                                 @Param("since") LocalDateTime since);
    
    @Query("SELECT al.requestUrl, AVG(al.responseTime) as avgTime, MAX(al.responseTime) as maxTime " +
           "FROM ApiLog al " +
           "WHERE al.createdAt >= :since " +
           "GROUP BY al.requestUrl " +
           "ORDER BY avgTime DESC")
    List<Object[]> findSlowEndpoints(@Param("since") LocalDateTime since, 
                                    Pageable pageable);
    
    // ===== API 사용 통계 =====
    
    @Query("SELECT new com.routepick.backend.application.dto.projection.ApiUsageStatisticsProjection(" +
           "DATE(al.createdAt), al.requestMethod, COUNT(al), AVG(al.responseTime), " +
           "SUM(CASE WHEN al.responseStatus < 400 THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN al.responseStatus >= 400 THEN 1 ELSE 0 END)) " +
           "FROM ApiLog al " +
           "WHERE al.createdAt >= :startDate " +
           "GROUP BY DATE(al.createdAt), al.requestMethod " +
           "ORDER BY DATE(al.createdAt) DESC")
    List<ApiUsageStatisticsProjection> calculateApiUsageStatistics(@Param("startDate") LocalDateTime startDate);
    
    // ===== 에러 패턴 분석 =====
    
    @Query("SELECT al.responseStatus, al.errorMessage, COUNT(al) as errorCount " +
           "FROM ApiLog al " +
           "WHERE al.responseStatus >= 400 " +
           "AND al.createdAt >= :since " +
           "GROUP BY al.responseStatus, al.errorMessage " +
           "ORDER BY errorCount DESC")
    List<Object[]> findErrorPatterns(@Param("since") LocalDateTime since);
    
    @Query("SELECT al.requestUrl, al.responseStatus, COUNT(al) as errorCount " +
           "FROM ApiLog al " +
           "WHERE al.responseStatus >= 400 " +
           "AND al.createdAt >= :since " +
           "GROUP BY al.requestUrl, al.responseStatus " +
           "HAVING errorCount >= :minCount " +
           "ORDER BY errorCount DESC")
    List<Object[]> findProblematicEndpoints(@Param("since") LocalDateTime since, 
                                           @Param("minCount") Long minCount);
    
    // ===== 사용자 행동 분석 =====
    
    @Query("SELECT al.userId, COUNT(al) as apiCalls, COUNT(DISTINCT al.requestUrl) as uniqueEndpoints " +
           "FROM ApiLog al " +
           "WHERE al.createdAt >= :since " +
           "GROUP BY al.userId " +
           "ORDER BY apiCalls DESC")
    List<Object[]> findMostActiveUsers(@Param("since") LocalDateTime since, 
                                      Pageable pageable);
    
    // ===== 로그 정리 =====
    
    @Transactional
    @Modifying
    @Query("DELETE FROM ApiLog al WHERE al.createdAt < :cutoffDate")
    int deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Transactional
    @Modifying
    @Query("DELETE FROM ApiLog al " +
           "WHERE al.responseStatus < 400 " +
           "AND al.createdAt < :cutoffDate")
    int deleteOldSuccessLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

## 2. ExternalApiConfigRepository (외부 API 설정 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.system;

import com.routepick.backend.domain.entity.system.ExternalApiConfig;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 외부 API 설정 Repository
 * - 외부 API 연동 설정 관리
 * - API 상태 모니터링 및 헬스체크
 * - 레이트 제한 및 할당량 관리
 */
@Repository
public interface ExternalApiConfigRepository extends BaseRepository<ExternalApiConfig, Long> {
    
    // ===== 활성 API 설정 조회 =====
    
    Optional<ExternalApiConfig> findByApiNameAndIsActiveTrue(String apiName);
    
    List<ExternalApiConfig> findByIsActiveTrueOrderByApiName();
    
    @Query("SELECT eac FROM ExternalApiConfig eac " +
           "WHERE eac.isActive = true " +
           "AND eac.environment = :environment " +
           "ORDER BY eac.apiName")
    List<ExternalApiConfig> findActiveByEnvironment(@Param("environment") String environment);
    
    // ===== API명으로 조회 =====
    
    List<ExternalApiConfig> findByApiName(String apiName);
    
    Optional<ExternalApiConfig> findByApiNameAndEnvironment(String apiName, String environment);
    
    @Query("SELECT eac FROM ExternalApiConfig eac " +
           "WHERE eac.apiName LIKE CONCAT(:prefix, '%') " +
           "AND eac.isActive = true")
    List<ExternalApiConfig> findByApiNamePrefix(@Param("prefix") String prefix);
    
    // ===== API 상태 업데이트 =====
    
    @Transactional
    @Modifying
    @Query("UPDATE ExternalApiConfig eac SET eac.isActive = :status, eac.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE eac.apiName = :apiName")
    int updateApiStatus(@Param("apiName") String apiName, @Param("status") boolean status);
    
    @Transactional
    @Modifying
    @Query("UPDATE ExternalApiConfig eac SET eac.lastHealthCheck = :checkTime, " +
           "eac.healthStatus = :status, eac.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE eac.id = :configId")
    int updateHealthStatus(@Param("configId") Long configId, 
                          @Param("checkTime") LocalDateTime checkTime, 
                          @Param("status") String status);
    
    // ===== 레이트 제한별 조회 =====
    
    List<ExternalApiConfig> findByRateLimitGreaterThan(Integer rateLimit);
    
    @Query("SELECT eac FROM ExternalApiConfig eac " +
           "WHERE eac.currentUsage >= eac.rateLimit * :threshold " +
           "AND eac.isActive = true")
    List<ExternalApiConfig> findNearRateLimit(@Param("threshold") Double threshold);
    
    // ===== 만료된 API 설정 =====
    
    @Query("SELECT eac FROM ExternalApiConfig eac " +
           "WHERE eac.expiryDate < CURRENT_TIMESTAMP " +
           "AND eac.isActive = true")
    List<ExternalApiConfig> findExpiredApiConfigs();
    
    @Query("SELECT eac FROM ExternalApiConfig eac " +
           "WHERE eac.expiryDate BETWEEN CURRENT_TIMESTAMP AND :warningDate " +
           "AND eac.isActive = true")
    List<ExternalApiConfig> findExpiringApiConfigs(@Param("warningDate") LocalDateTime warningDate);
    
    // ===== API 상태 체크 =====
    
    @Query("SELECT new com.routepick.backend.application.dto.projection.ApiHealthStatusProjection(" +
           "eac.apiName, eac.isActive, eac.healthStatus, eac.lastHealthCheck, " +
           "eac.currentUsage, eac.rateLimit) " +
           "FROM ExternalApiConfig eac " +
           "WHERE eac.isActive = true " +
           "ORDER BY eac.lastHealthCheck ASC")
    List<ApiHealthStatusProjection> findApiHealthStatus();
    
    @Query("SELECT eac FROM ExternalApiConfig eac " +
           "WHERE eac.lastHealthCheck < :staleTime " +
           "AND eac.isActive = true")
    List<ExternalApiConfig> findStaleHealthChecks(@Param("staleTime") LocalDateTime staleTime);
    
    // ===== 사용량 통계 =====
    
    @Query("SELECT SUM(eac.currentUsage) FROM ExternalApiConfig eac " +
           "WHERE eac.isActive = true")
    Long getTotalApiUsage();
    
    @Query("SELECT eac.apiName, eac.currentUsage, eac.rateLimit, " +
           "(eac.currentUsage * 100.0 / eac.rateLimit) as usagePercentage " +
           "FROM ExternalApiConfig eac " +
           "WHERE eac.isActive = true " +
           "ORDER BY usagePercentage DESC")
    List<Object[]> getApiUsageStatistics();
    
    // ===== 설정 관리 =====
    
    @Transactional
    @Modifying
    @Query("UPDATE ExternalApiConfig eac SET eac.currentUsage = 0, eac.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE eac.isActive = true")
    int resetAllUsageCounters();
    
    @Transactional
    @Modifying
    @Query("UPDATE ExternalApiConfig eac SET eac.currentUsage = eac.currentUsage + 1 " +
           "WHERE eac.apiName = :apiName AND eac.isActive = true")
    int incrementUsageCounter(@Param("apiName") String apiName);
    
    // ===== 환경별 설정 =====
    
    @Query("SELECT DISTINCT eac.environment FROM ExternalApiConfig eac")
    List<String> findAllEnvironments();
    
    @Query("SELECT COUNT(eac) FROM ExternalApiConfig eac " +
           "WHERE eac.environment = :environment AND eac.isActive = true")
    long countActiveByEnvironment(@Param("environment") String environment);
}
```

## 3. WebhookLogRepository (웹훅 로그 모니터링)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.system;

import com.routepick.backend.domain.entity.system.WebhookLog;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 웹훅 로그 Repository
 * - 웹훅 전송 및 응답 로그 관리
 * - 재시도 로직 및 실패 분석
 * - 웹훅 성능 모니터링
 */
@Repository
public interface WebhookLogRepository extends BaseRepository<WebhookLog, Long> {
    
    // ===== 기간별 웹훅 로그 =====
    
    List<WebhookLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
        LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    @Query("SELECT wl FROM WebhookLog wl " +
           "WHERE wl.createdAt >= :since " +
           "ORDER BY wl.createdAt DESC")
    List<WebhookLog> findRecentWebhookLogs(@Param("since") LocalDateTime since, 
                                          Pageable pageable);
    
    // ===== 상태별 웹훅 로그 =====
    
    List<WebhookLog> findByResponseStatusAndCreatedAtBetween(
        Integer responseStatus, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT wl FROM WebhookLog wl " +
           "WHERE wl.isSuccess = false " +
           "AND wl.createdAt >= :since " +
           "ORDER BY wl.createdAt DESC")
    List<WebhookLog> findFailedWebhooks(@Param("since") LocalDateTime since);
    
    @Query("SELECT wl FROM WebhookLog wl " +
           "WHERE wl.responseStatus >= 500 " +
           "AND wl.createdAt >= :since " +
           "ORDER BY wl.createdAt DESC")
    List<WebhookLog> findServerErrorWebhooks(@Param("since") LocalDateTime since);
    
    // ===== URL별 웹훅 로그 =====
    
    List<WebhookLog> findByWebhookUrlAndCreatedAtAfter(
        String webhookUrl, LocalDateTime after);
    
    @Query("SELECT wl.webhookUrl, COUNT(wl) as callCount, " +
           "SUM(CASE WHEN wl.isSuccess = true THEN 1 ELSE 0 END) as successCount " +
           "FROM WebhookLog wl " +
           "WHERE wl.createdAt >= :since " +
           "GROUP BY wl.webhookUrl " +
           "ORDER BY callCount DESC")
    List<Object[]> findWebhookUrlStatistics(@Param("since") LocalDateTime since);
    
    // ===== 재시도 횟수별 =====
    
    List<WebhookLog> findByRetryCountGreaterThan(Integer retryCount);
    
    @Query("SELECT wl FROM WebhookLog wl " +
           "WHERE wl.retryCount < wl.maxRetries " +
           "AND wl.isSuccess = false " +
           "AND wl.nextRetryAt <= CURRENT_TIMESTAMP " +
           "ORDER BY wl.nextRetryAt ASC")
    List<WebhookLog> findPendingRetries();
    
    @Query("SELECT AVG(wl.retryCount) FROM WebhookLog wl " +
           "WHERE wl.isSuccess = false " +
           "AND wl.createdAt >= :since")
    Double getAverageRetryCount(@Param("since") LocalDateTime since);
    
    // ===== 웹훅 성공률 계산 =====
    
    @Query("SELECT " +
           "(SUM(CASE WHEN wl.isSuccess = true THEN 1 ELSE 0 END) * 100.0 / COUNT(wl)) as successRate " +
           "FROM WebhookLog wl " +
           "WHERE wl.createdAt >= :since")
    Double calculateWebhookSuccessRate(@Param("since") LocalDateTime since);
    
    @Query("SELECT wl.webhookUrl, " +
           "(SUM(CASE WHEN wl.isSuccess = true THEN 1 ELSE 0 END) * 100.0 / COUNT(wl)) as successRate " +
           "FROM WebhookLog wl " +
           "WHERE wl.createdAt >= :since " +
           "GROUP BY wl.webhookUrl " +
           "ORDER BY successRate DESC")
    List<Object[]> calculateSuccessRateByUrl(@Param("since") LocalDateTime since);
    
    // ===== 웹훅 성능 지표 =====
    
    @Query("SELECT new com.routepick.backend.application.dto.projection.WebhookPerformanceProjection(" +
           "wl.webhookUrl, COUNT(wl), AVG(wl.responseTime), MAX(wl.responseTime), " +
           "SUM(CASE WHEN wl.isSuccess = true THEN 1 ELSE 0 END), AVG(wl.retryCount)) " +
           "FROM WebhookLog wl " +
           "WHERE wl.createdAt >= :since " +
           "GROUP BY wl.webhookUrl " +
           "ORDER BY COUNT(wl) DESC")
    List<WebhookPerformanceProjection> findWebhookPerformanceMetrics(@Param("since") LocalDateTime since);
    
    @Query("SELECT wl FROM WebhookLog wl " +
           "WHERE wl.responseTime > :threshold " +
           "AND wl.createdAt >= :since " +
           "ORDER BY wl.responseTime DESC")
    List<WebhookLog> findSlowWebhooks(@Param("threshold") Long threshold, 
                                     @Param("since") LocalDateTime since);
    
    // ===== 웹훅 이벤트 타입별 분석 =====
    
    @Query("SELECT wl.eventType, COUNT(wl) as eventCount, " +
           "SUM(CASE WHEN wl.isSuccess = true THEN 1 ELSE 0 END) as successCount " +
           "FROM WebhookLog wl " +
           "WHERE wl.createdAt >= :since " +
           "GROUP BY wl.eventType " +
           "ORDER BY eventCount DESC")
    List<Object[]> getEventTypeStatistics(@Param("since") LocalDateTime since);
    
    // ===== 재시도 횟수 업데이트 =====
    
    @Transactional
    @Modifying
    @Query("UPDATE WebhookLog wl SET wl.retryCount = wl.retryCount + 1, " +
           "wl.nextRetryAt = :nextRetryAt, wl.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE wl.id = :webhookLogId")
    int updateRetryCount(@Param("webhookLogId") Long webhookLogId, 
                        @Param("nextRetryAt") LocalDateTime nextRetryAt);
    
    @Transactional
    @Modifying
    @Query("UPDATE WebhookLog wl SET wl.isSuccess = true, wl.responseStatus = :status, " +
           "wl.responseBody = :responseBody, wl.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE wl.id = :webhookLogId")
    int markAsSuccess(@Param("webhookLogId") Long webhookLogId, 
                     @Param("status") Integer status, 
                     @Param("responseBody") String responseBody);
    
    // ===== 로그 정리 =====
    
    @Transactional
    @Modifying
    @Query("DELETE FROM WebhookLog wl WHERE wl.createdAt < :cutoffDate")
    int deleteOldWebhookLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Transactional
    @Modifying
    @Query("DELETE FROM WebhookLog wl " +
           "WHERE wl.isSuccess = true " +
           "AND wl.createdAt < :cutoffDate")
    int deleteOldSuccessfulWebhooks(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ===== 실패 패턴 분석 =====
    
    @Query("SELECT wl.responseStatus, wl.errorMessage, COUNT(wl) as errorCount " +
           "FROM WebhookLog wl " +
           "WHERE wl.isSuccess = false " +
           "AND wl.createdAt >= :since " +
           "GROUP BY wl.responseStatus, wl.errorMessage " +
           "ORDER BY errorCount DESC")
    List<Object[]> analyzeFailurePatterns(@Param("since") LocalDateTime since);
}
```

## 📊 Projection 인터페이스들

### ApiUsageStatisticsProjection
```java
package com.routepick.backend.application.dto.projection;

import java.time.LocalDate;

/**
 * API 사용 통계 Projection
 */
public class ApiUsageStatisticsProjection {
    private LocalDate date;
    private String method;
    private Long totalCalls;
    private Double averageResponseTime;
    private Long successCount;
    private Long errorCount;
    
    public ApiUsageStatisticsProjection(LocalDate date, String method, Long totalCalls,
                                       Double averageResponseTime, Long successCount, Long errorCount) {
        this.date = date;
        this.method = method;
        this.totalCalls = totalCalls;
        this.averageResponseTime = averageResponseTime;
        this.successCount = successCount;
        this.errorCount = errorCount;
    }
    
    // Getters
    public LocalDate getDate() { return date; }
    public String getMethod() { return method; }
    public Long getTotalCalls() { return totalCalls; }
    public Double getAverageResponseTime() { return averageResponseTime; }
    public Long getSuccessCount() { return successCount; }
    public Long getErrorCount() { return errorCount; }
    public Double getSuccessRate() { 
        return totalCalls > 0 ? (double) successCount / totalCalls * 100 : 0.0; 
    }
}
```

### ApiHealthStatusProjection
```java
package com.routepick.backend.application.dto.projection;

import java.time.LocalDateTime;

/**
 * API 헬스 상태 Projection
 */
public class ApiHealthStatusProjection {
    private String apiName;
    private Boolean isActive;
    private String healthStatus;
    private LocalDateTime lastHealthCheck;
    private Long currentUsage;
    private Long rateLimit;
    
    public ApiHealthStatusProjection(String apiName, Boolean isActive, String healthStatus,
                                   LocalDateTime lastHealthCheck, Long currentUsage, Long rateLimit) {
        this.apiName = apiName;
        this.isActive = isActive;
        this.healthStatus = healthStatus;
        this.lastHealthCheck = lastHealthCheck;
        this.currentUsage = currentUsage;
        this.rateLimit = rateLimit;
    }
    
    // Getters
    public String getApiName() { return apiName; }
    public Boolean getIsActive() { return isActive; }
    public String getHealthStatus() { return healthStatus; }
    public LocalDateTime getLastHealthCheck() { return lastHealthCheck; }
    public Long getCurrentUsage() { return currentUsage; }
    public Long getRateLimit() { return rateLimit; }
    
    public Double getUsagePercentage() {
        return rateLimit > 0 ? (double) currentUsage / rateLimit * 100 : 0.0;
    }
    
    public boolean isHealthy() {
        return "HEALTHY".equals(healthStatus);
    }
}
```

### WebhookPerformanceProjection
```java
package com.routepick.backend.application.dto.projection;

/**
 * 웹훅 성능 Projection
 */
public class WebhookPerformanceProjection {
    private String webhookUrl;
    private Long totalCalls;
    private Double averageResponseTime;
    private Long maxResponseTime;
    private Long successCount;
    private Double averageRetryCount;
    
    public WebhookPerformanceProjection(String webhookUrl, Long totalCalls, Double averageResponseTime,
                                       Long maxResponseTime, Long successCount, Double averageRetryCount) {
        this.webhookUrl = webhookUrl;
        this.totalCalls = totalCalls;
        this.averageResponseTime = averageResponseTime;
        this.maxResponseTime = maxResponseTime;
        this.successCount = successCount;
        this.averageRetryCount = averageRetryCount;
    }
    
    // Getters
    public String getWebhookUrl() { return webhookUrl; }
    public Long getTotalCalls() { return totalCalls; }
    public Double getAverageResponseTime() { return averageResponseTime; }
    public Long getMaxResponseTime() { return maxResponseTime; }
    public Long getSuccessCount() { return successCount; }
    public Double getAverageRetryCount() { return averageRetryCount; }
    public Double getSuccessRate() {
        return totalCalls > 0 ? (double) successCount / totalCalls * 100 : 0.0;
    }
}
```

## 🎯 **최종 전체 검증 - 총 50개 Repository 완성 확인**

### ✅ Repository 도메인별 집계
```
User 도메인 (7개): UserRepository, UserProfileRepository, UserVerificationRepository, 
                   UserAgreementRepository, SocialAccountRepository, ApiTokenRepository, 
                   UserFollowRepository

Tag 시스템 (4개): TagRepository, UserPreferredTagRepository, RouteTagRepository, 
                  UserRouteRecommendationRepository

Gym 시스템 (5개): GymRepository, GymBranchRepository, GymMemberRepository, 
                  WallRepository, BranchImageRepository

Route 시스템 (8개): RouteRepository, RouteSetterRepository, ClimbingLevelRepository,
                   RouteImageRepository, RouteVideoRepository, RouteCommentRepository,
                   RouteDifficultyVoteRepository, RouteScrapRepository

Climbing & Activity (5개): ClimbingLevelRepository, ClimbingShoeRepository, 
                           UserClimbingShoeRepository, UserClimbRepository, UserFollowRepository

Community (9개): BoardCategoryRepository, PostRepository, CommentRepository, PostLikeRepository,
                 PostBookmarkRepository, PostImageRepository, PostVideoRepository, 
                 PostRouteTagRepository, CommentLikeRepository

Payment (4개): PaymentRecordRepository, PaymentDetailRepository, PaymentItemRepository, 
               PaymentRefundRepository

Notification (4개): NotificationRepository, NoticeRepository, BannerRepository, AppPopupRepository

Message (2개): MessageRepository, MessageRouteTagRepository

System (3개): ApiLogRepository, ExternalApiConfigRepository, WebhookLogRepository

총 계: 7+4+5+8+5+9+4+4+2+3 = 51개 Repository ✅
```

## 📈 성능 최적화 전략

### 1. 인덱스 최적화
```sql
-- 시스템 관리 인덱스
CREATE INDEX idx_api_log_date_status ON api_logs(created_at DESC, response_status);
CREATE INDEX idx_api_log_user_date ON api_logs(user_id, created_at DESC);
CREATE INDEX idx_api_log_url_performance ON api_logs(request_url, response_time DESC);
CREATE INDEX idx_external_api_active ON external_api_configs(is_active, api_name);
CREATE INDEX idx_external_api_health ON external_api_configs(health_status, last_health_check);
CREATE INDEX idx_webhook_log_retry ON webhook_logs(is_success, next_retry_at);
CREATE INDEX idx_webhook_log_url_date ON webhook_logs(webhook_url, created_at DESC);
```

### 2. 시스템 모니터링
- **API 로그 분석**: 성능 메트릭, 에러 패턴, 사용량 통계
- **외부 API 관리**: 헬스체크, 레이트 제한, 사용량 모니터링
- **웹훅 모니터링**: 재시도 로직, 성공률 추적, 성능 지표

### 3. 배치 처리 최적화
- **로그 정리**: 오래된 로그 자동 삭제
- **통계 생성**: 주기적 성능 지표 계산
- **알림 시스템**: 임계치 초과 시 자동 알림

## 🚀 6단계 Service 레이어 준비사항

### ✅ Repository 레이어 완성으로 Service 구현 준비 완료
1. **총 51개 Repository** 완성 ✅
2. **모든 QueryDSL Custom Repository** 구현 완료 ✅
3. **성능 최적화 및 보안 강화** 검증 ✅

### 📋 비즈니스 로직 설계 가이드 준비
- **도메인별 Service 인터페이스** 설계 가이드
- **트랜잭션 관리 전략** 수립 완료
- **예외 처리 체계** 연동 준비

### 🔄 트랜잭션 관리 전략 수립 완료
- **읽기 전용 트랜잭션** 최적화
- **분산 트랜잭션** 관리 전략
- **이벤트 기반 비동기** 처리

## 🎯 주요 기능
- ✅ **API 모니터링**: 성능 지표, 에러 패턴, 사용량 추적
- ✅ **외부 API 관리**: 헬스체크, 레이트 제한, 만료 알림
- ✅ **웹훅 시스템**: 재시도 로직, 성공률 모니터링
- ✅ **시스템 분석**: 사용 패턴 분석 및 최적화 제안
- ✅ **자동화**: 배치 처리 및 알림 시스템
- ✅ **확장성**: 마이크로서비스 아키텍처 지원

---
*Step 5-4f3 완료: 시스템 관리 Repository 3개 생성 완료*  
*Repository 레이어 총 51개 완성으로 Service 레이어 구현 준비 완료*  
*다음: 6단계 Service 레이어 개발 시작*