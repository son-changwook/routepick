# Step 7-5d: System Controller 구현

> 시스템 관리, 모니터링, 헬스체크 Controller - 관리자 전용, 실시간 시스템 상태 조회
> 생성일: 2025-08-25
> 단계: 7-5d (Controller 레이어 - 시스템 관리)
> 참고: step6-6d, step6-6a, step6-6b, step6-6c, step4-4c1, step4-4c2

---

## 🎯 설계 목표

- **시스템 상태**: 헬스체크, 성능 지표, 리소스 사용량
- **로그 관리**: API 로그, 시스템 로그, 에러 로그 조회
- **캐시 관리**: Redis 캐시 상태, 초기화, 워밍업
- **외부 API**: 연동 상태, API 키 관리, 상태 모니터링
- **백업 관리**: 자동 백업 실행, 백업 상태 조회

---

## ⚙️ SystemController 구현

### SystemController.java
```java
package com.routepick.controller.api.v1.system;

import com.routepick.common.annotation.RateLimited;
import com.routepick.common.dto.ApiResponse;
import com.routepick.service.system.SystemService;
import com.routepick.service.system.ApiLogService;
import com.routepick.service.system.CacheService;
import com.routepick.service.system.ExternalApiService;
import com.routepick.dto.system.request.*;
import com.routepick.dto.system.response.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 시스템 관리 Controller
 * - 시스템 상태 및 헬스체크
 * - API 로그 및 모니터링
 * - 캐시 관리
 * - 외부 API 관리
 * - 백업 및 복구 관리
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Validated
@Tag(name = "System", description = "시스템 관리 API (관리자 전용)")
@SecurityRequirement(name = "bearerAuth")
public class SystemController {

    private final SystemService systemService;
    private final ApiLogService apiLogService;
    private final CacheService cacheService;
    private final ExternalApiService externalApiService;

    /**
     * 시스템 헬스체크
     * GET /api/v1/system/health
     */
    @GetMapping("/health")
    @Operation(summary = "시스템 헬스체크", description = "전체 시스템의 상태를 확인합니다.")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR')")
    @RateLimited(requests = 60, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<HealthCheckResponse>> getSystemHealth() {
        
        try {
            log.info("시스템 헬스체크 요청");
            
            // 전체 시스템 상태 체크
            HealthCheckResponse response = systemService.performHealthCheck();
            
            log.info("시스템 헬스체크 완료: overall={}, dbStatus={}, redisStatus={}, apiStatus={}", 
                    response.getOverallStatus(),
                    response.getDatabaseStatus(),
                    response.getRedisStatus(), 
                    response.getExternalApiStatus());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("시스템 헬스체크 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 시스템 통계 조회
     * GET /api/v1/system/stats
     */
    @GetMapping("/stats")
    @Operation(summary = "시스템 통계", description = "시스템 성능 및 사용량 통계를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<SystemStatsResponse>> getSystemStats(
            @RequestParam(required = false, defaultValue = "1") int hours) {
        
        try {
            log.info("시스템 통계 조회: hours={}", hours);
            
            // 시스템 통계 조회
            SystemStatsResponse response = systemService.getSystemStats(hours);
            
            log.info("시스템 통계 조회 완료: cpuUsage={}%, memoryUsage={}%, diskUsage={}%", 
                    response.getCpuUsage(), response.getMemoryUsage(), response.getDiskUsage());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("시스템 통계 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * API 로그 조회
     * GET /api/v1/system/logs
     */
    @GetMapping("/logs")
    @Operation(summary = "API 로그 조회", description = "API 호출 로그를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 100, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Page<ApiLogResponse>>> getApiLogs(
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "false") boolean errorsOnly,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        
        try {
            log.info("API 로그 조회: endpoint={}, method={}, statusCode={}, errorsOnly={}", 
                    endpoint, method, statusCode, errorsOnly);
            
            // API 로그 검색 조건 구성
            LogSearchCriteria criteria = LogSearchCriteria.builder()
                    .endpoint(endpoint)
                    .method(method)
                    .statusCode(statusCode)
                    .startDate(startDate)
                    .endDate(endDate)
                    .errorsOnly(errorsOnly)
                    .build();
            
            // API 로그 조회
            Page<ApiLogResponse> response = apiLogService.getApiLogs(criteria, pageable);
            
            log.info("API 로그 조회 완료: totalElements={}, errorCount={}", 
                    response.getTotalElements(), 
                    response.getContent().stream()
                            .mapToLong(log -> log.getStatusCode() >= 400 ? 1 : 0).sum());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("API 로그 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 캐시 상태 조회
     * GET /api/v1/system/cache
     */
    @GetMapping("/cache")
    @Operation(summary = "캐시 상태 조회", description = "Redis 캐시 상태를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 60, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<CacheStatusResponse>> getCacheStatus() {
        
        try {
            log.info("캐시 상태 조회");
            
            // 캐시 상태 조회
            CacheStatusResponse response = cacheService.getCacheStatus();
            
            log.info("캐시 상태 조회 완료: redisStatus={}, totalKeys={}, hitRate={}%", 
                    response.getRedisStatus(), response.getTotalKeys(), response.getHitRate());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("캐시 상태 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 캐시 초기화
     * DELETE /api/v1/system/cache/clear
     */
    @DeleteMapping("/cache/clear")
    @Operation(summary = "캐시 초기화", description = "지정된 캐시를 초기화합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimits({
        @RateLimited(requests = 5, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 10, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP)
    })
    public ResponseEntity<ApiResponse<CacheClearResponse>> clearCache(
            @Valid @RequestBody CacheClearRequest request) {
        
        try {
            log.info("캐시 초기화 요청: cacheNames={}, pattern={}", 
                    request.getCacheNames(), request.getKeyPattern());
            
            // 캐시 초기화 수행
            CacheClearResponse response = cacheService.clearCache(request);
            
            log.info("캐시 초기화 완료: clearedCount={}, affectedCaches={}", 
                    response.getClearedCount(), response.getAffectedCaches().size());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("캐시 초기화 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 캐시 워밍업
     * POST /api/v1/system/cache/warmup
     */
    @PostMapping("/cache/warmup")
    @Operation(summary = "캐시 워밍업", description = "자주 사용되는 데이터를 미리 캐시에 로드합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 3, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<CacheWarmupResponse>> warmupCache(
            @Valid @RequestBody CacheWarmupRequest request) {
        
        try {
            log.info("캐시 워밍업 요청: cacheTypes={}", request.getCacheTypes());
            
            // 캐시 워밍업 수행
            CacheWarmupResponse response = cacheService.warmupCache(request);
            
            log.info("캐시 워밍업 완료: warmedCount={}, duration={}ms", 
                    response.getWarmedCount(), response.getDurationMs());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("캐시 워밍업 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 외부 API 상태 조회
     * GET /api/v1/system/external-apis
     */
    @GetMapping("/external-apis")
    @Operation(summary = "외부 API 상태", description = "연동된 외부 API들의 상태를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<List<ExternalApiStatusResponse>>> getExternalApiStatus() {
        
        try {
            log.info("외부 API 상태 조회");
            
            // 외부 API 상태 조회
            List<ExternalApiStatusResponse> response = externalApiService.getAllApiStatus();
            
            log.info("외부 API 상태 조회 완료: totalApis={}, activeCount={}, errorCount={}", 
                    response.size(),
                    response.stream().mapToLong(api -> "ACTIVE".equals(api.getStatus()) ? 1 : 0).sum(),
                    response.stream().mapToLong(api -> "ERROR".equals(api.getStatus()) ? 1 : 0).sum());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("외부 API 상태 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 외부 API 헬스체크
     * POST /api/v1/system/external-apis/{apiName}/health-check
     */
    @PostMapping("/external-apis/{apiName}/health-check")
    @Operation(summary = "외부 API 헬스체크", description = "특정 외부 API의 헬스체크를 수행합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 20, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<ExternalApiHealthResponse>> checkExternalApiHealth(
            @PathVariable @NotBlank String apiName) {
        
        try {
            log.info("외부 API 헬스체크: apiName={}", apiName);
            
            // 외부 API 헬스체크 수행
            ExternalApiHealthResponse response = externalApiService.performHealthCheck(apiName);
            
            log.info("외부 API 헬스체크 완료: apiName={}, status={}, responseTime={}ms", 
                    apiName, response.getStatus(), response.getResponseTimeMs());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("외부 API 헬스체크 실패: apiName={}, error={}", apiName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 시스템 백업 실행
     * POST /api/v1/system/backup
     */
    @PostMapping("/backup")
    @Operation(summary = "시스템 백업", description = "시스템 데이터 백업을 실행합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimits({
        @RateLimited(requests = 2, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 5, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP)
    })
    public ResponseEntity<ApiResponse<BackupResponse>> createBackup(
            @Valid @RequestBody BackupRequest request) {
        
        try {
            log.info("시스템 백업 요청: backupType={}, includeLogs={}", 
                    request.getBackupType(), request.isIncludeLogs());
            
            // 백업 실행
            BackupResponse response = systemService.createBackup(request);
            
            log.info("시스템 백업 시작: backupId={}, estimatedDuration={}분", 
                    response.getBackupId(), response.getEstimatedDurationMinutes());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("시스템 백업 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 백업 상태 조회
     * GET /api/v1/system/backup/status
     */
    @GetMapping("/backup/status")
    @Operation(summary = "백업 상태 조회", description = "진행 중이거나 완료된 백업의 상태를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 60, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<List<BackupStatusResponse>>> getBackupStatus() {
        
        try {
            log.info("백업 상태 조회");
            
            // 백업 상태 조회
            List<BackupStatusResponse> response = systemService.getBackupStatus();
            
            log.info("백업 상태 조회 완료: totalBackups={}, inProgress={}, completed={}", 
                    response.size(),
                    response.stream().mapToLong(b -> "IN_PROGRESS".equals(b.getStatus()) ? 1 : 0).sum(),
                    response.stream().mapToLong(b -> "COMPLETED".equals(b.getStatus()) ? 1 : 0).sum());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("백업 상태 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 시스템 설정 조회
     * GET /api/v1/system/config
     */
    @GetMapping("/config")
    @Operation(summary = "시스템 설정 조회", description = "동적 시스템 설정을 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<SystemConfigResponse>> getSystemConfig() {
        
        try {
            log.info("시스템 설정 조회");
            
            // 시스템 설정 조회
            SystemConfigResponse response = systemService.getSystemConfig();
            
            log.info("시스템 설정 조회 완료: activeProfile={}, maintenanceMode={}", 
                    response.getActiveProfile(), response.isMaintenanceMode());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("시스템 설정 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 시스템 설정 변경
     * PUT /api/v1/system/config
     */
    @PutMapping("/config")
    @Operation(summary = "시스템 설정 변경", description = "동적 시스템 설정을 변경합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 10, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<SystemConfigResponse>> updateSystemConfig(
            @Valid @RequestBody SystemConfigUpdateRequest request) {
        
        try {
            log.info("시스템 설정 변경: maintenanceMode={}, rateLimitEnabled={}", 
                    request.isMaintenanceMode(), request.isRateLimitEnabled());
            
            // 시스템 설정 변경
            SystemConfigResponse response = systemService.updateSystemConfig(request);
            
            log.info("시스템 설정 변경 완료: maintenanceMode={}, rateLimitEnabled={}", 
                    response.isMaintenanceMode(), response.isRateLimitEnabled());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("시스템 설정 변경 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 시스템 알림 발송
     * POST /api/v1/system/alerts
     */
    @PostMapping("/alerts")
    @Operation(summary = "시스템 알림 발송", description = "시스템 관련 알림을 관리자에게 발송합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 20, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<SystemAlertResponse>> sendSystemAlert(
            @Valid @RequestBody SystemAlertRequest request) {
        
        try {
            log.info("시스템 알림 발송: alertType={}, severity={}, targetAdmins={}", 
                    request.getAlertType(), request.getSeverity(), 
                    request.getTargetAdminIds() != null ? request.getTargetAdminIds().size() : "ALL");
            
            // 시스템 알림 발송
            SystemAlertResponse response = systemService.sendSystemAlert(request);
            
            log.info("시스템 알림 발송 완료: alertId={}, sentCount={}", 
                    response.getAlertId(), response.getSentCount());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("시스템 알림 발송 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 시스템 메트릭스 수집
     * GET /api/v1/system/metrics
     */
    @GetMapping("/metrics")
    @Operation(summary = "시스템 메트릭스", description = "상세한 시스템 메트릭스를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 100, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<SystemMetricsResponse>> getSystemMetrics(
            @RequestParam(required = false, defaultValue = "5") int minutes) {
        
        try {
            log.info("시스템 메트릭스 조회: minutes={}", minutes);
            
            // 시스템 메트릭스 수집
            SystemMetricsResponse response = systemService.getSystemMetrics(minutes);
            
            log.info("시스템 메트릭스 조회 완료: dataPoints={}, avgResponseTime={}ms", 
                    response.getDataPoints().size(), response.getAverageResponseTime());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("시스템 메트릭스 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }
}
```

---

## 📋 System Request DTOs

### BackupRequest.java
```java
package com.routepick.dto.system.request;

import com.routepick.common.enums.BackupType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 백업 요청 DTO
 */
@Data
@Schema(description = "백업 요청")
public class BackupRequest {

    @NotNull(message = "백업 타입은 필수입니다")
    @Schema(description = "백업 타입", example = "FULL")
    private BackupType backupType;

    @Schema(description = "백업에 포함할 테이블 목록 (null이면 전체)")
    private List<String> includeTables;

    @Schema(description = "백업에서 제외할 테이블 목록")
    private List<String> excludeTables;

    @Schema(description = "로그 포함 여부", example = "false")
    private boolean includeLogs = false;

    @Schema(description = "압축 여부", example = "true")
    private boolean compress = true;

    @Schema(description = "백업 설명")
    private String description;

    @Schema(description = "외부 저장소 업로드 여부", example = "true")
    private boolean uploadToCloud = true;

    @Schema(description = "백업 보존 기간 (일)", example = "30")
    private int retentionDays = 30;
}
```

### CacheClearRequest.java
```java
package com.routepick.dto.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Size;
import java.util.List;

/**
 * 캐시 초기화 요청 DTO
 */
@Data
@Schema(description = "캐시 초기화 요청")
public class CacheClearRequest {

    @Schema(description = "초기화할 캐시 이름 목록")
    @Size(max = 20, message = "캐시 이름은 최대 20개까지 가능합니다")
    private List<String> cacheNames;

    @Schema(description = "키 패턴 (와일드카드 지원)", example = "user:*")
    private String keyPattern;

    @Schema(description = "모든 캐시 초기화 여부", example = "false")
    private boolean clearAll = false;

    @Schema(description = "확인 메시지", example = "CONFIRM_CLEAR")
    private String confirmationMessage;
}
```

### SystemConfigUpdateRequest.java
```java
package com.routepick.dto.system.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.Map;

/**
 * 시스템 설정 변경 요청 DTO
 */
@Data
@Schema(description = "시스템 설정 변경 요청")
public class SystemConfigUpdateRequest {

    @Schema(description = "점검 모드 활성화", example = "false")
    private boolean maintenanceMode = false;

    @Schema(description = "점검 모드 메시지")
    private String maintenanceMessage;

    @Schema(description = "Rate Limiting 활성화", example = "true")
    private boolean rateLimitEnabled = true;

    @Schema(description = "로그 레벨", example = "INFO")
    private String logLevel;

    @Schema(description = "캐시 활성화", example = "true")
    private boolean cacheEnabled = true;

    @Schema(description = "메모리 임계치 (%)", example = "85")
    @Min(50) @Max(95)
    private int memoryThreshold = 85;

    @Schema(description = "CPU 임계치 (%)", example = "80")
    @Min(50) @Max(95)
    private int cpuThreshold = 80;

    @Schema(description = "API 타임아웃 (초)", example = "30")
    @Min(1) @Max(300)
    private int apiTimeoutSeconds = 30;

    @Schema(description = "동적 설정 값")
    private Map<String, String> dynamicSettings;

    @Schema(description = "설정 변경 사유")
    private String changeReason;
}
```

---

## 📤 System Response DTOs

### HealthCheckResponse.java
```java
package com.routepick.dto.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 헬스체크 응답 DTO
 */
@Data
@Schema(description = "시스템 헬스체크 결과")
public class HealthCheckResponse {

    @Schema(description = "전체 상태", example = "UP")
    private String overallStatus;

    @Schema(description = "데이터베이스 상태", example = "UP")
    private String databaseStatus;

    @Schema(description = "Redis 상태", example = "UP")
    private String redisStatus;

    @Schema(description = "외부 API 상태", example = "UP")
    private String externalApiStatus;

    @Schema(description = "디스크 상태", example = "UP")
    private String diskStatus;

    @Schema(description = "메모리 상태", example = "UP")
    private String memoryStatus;

    @Schema(description = "상세 헬스체크 결과")
    private Map<String, ComponentHealth> componentHealths;

    @Schema(description = "마지막 체크 시간")
    private LocalDateTime lastCheckedAt;

    @Schema(description = "체크 소요 시간 (ms)")
    private long checkDurationMs;

    @Schema(description = "시스템 가동 시간 (분)")
    private long uptimeMinutes;

    /**
     * 컴포넌트별 상태
     */
    @Data
    @Schema(description = "컴포넌트 상태")
    public static class ComponentHealth {
        private String status;
        private String message;
        private long responseTimeMs;
        private Map<String, Object> details;
    }
}
```

### SystemStatsResponse.java
```java
package com.routepick.dto.system.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 시스템 통계 응답 DTO
 */
@Data
@Schema(description = "시스템 통계")
public class SystemStatsResponse {

    @Schema(description = "CPU 사용률 (%)", example = "45.2")
    private double cpuUsage;

    @Schema(description = "메모리 사용률 (%)", example = "67.8")
    private double memoryUsage;

    @Schema(description = "디스크 사용률 (%)", example = "52.1")
    private double diskUsage;

    @Schema(description = "활성 스레드 수", example = "125")
    private int activeThreads;

    @Schema(description = "활성 커넥션 수", example = "45")
    private int activeConnections;

    @Schema(description = "평균 응답 시간 (ms)", example = "150")
    private double averageResponseTime;

    @Schema(description = "요청 처리량 (분당)", example = "1250")
    private long requestsPerMinute;

    @Schema(description = "에러율 (%)", example = "0.5")
    private double errorRate;

    @Schema(description = "GC 실행 횟수 (최근 1시간)", example = "12")
    private long gcCount;

    @Schema(description = "GC 소요 시간 (ms)", example = "250")
    private long gcTimeMs;

    @Schema(description = "JVM 힙 메모리 (MB)")
    private MemoryInfo heapMemory;

    @Schema(description = "JVM 비힙 메모리 (MB)")
    private MemoryInfo nonHeapMemory;

    @Schema(description = "시간대별 통계")
    private List<HourlyStats> hourlyStats;

    @Schema(description = "통계 수집 시간")
    private LocalDateTime collectedAt;

    /**
     * 메모리 정보
     */
    @Data
    @Schema(description = "메모리 정보")
    public static class MemoryInfo {
        private long used;
        private long max;
        private long committed;
        private double usagePercent;
    }

    /**
     * 시간별 통계
     */
    @Data
    @Schema(description = "시간별 통계")
    public static class HourlyStats {
        private LocalDateTime hour;
        private double avgCpuUsage;
        private double avgMemoryUsage;
        private long totalRequests;
        private double avgResponseTime;
        private long errorCount;
    }
}
```

### BackupResponse.java
```java
package com.routepick.dto.system.response;

import com.routepick.common.enums.BackupStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 백업 응답 DTO
 */
@Data
@Schema(description = "백업 응답")
public class BackupResponse {

    @Schema(description = "백업 ID", example = "backup_20250825_001")
    private String backupId;

    @Schema(description = "백업 상태", example = "STARTED")
    private BackupStatus status;

    @Schema(description = "백업 시작 시간")
    private LocalDateTime startedAt;

    @Schema(description = "예상 소요 시간 (분)", example = "15")
    private int estimatedDurationMinutes;

    @Schema(description = "백업 파일 경로")
    private String backupFilePath;

    @Schema(description = "백업 크기 (MB)")
    private Long backupSizeMB;

    @Schema(description = "진행률 (%)", example = "0")
    private int progressPercent;

    @Schema(description = "진행 상태 메시지")
    private String progressMessage;

    @Schema(description = "백업 설명")
    private String description;
}
```

---

## 🔍 시스템 모니터링 기능

### 실시간 메트릭 수집
```java
/**
 * 실시간 시스템 메트릭 수집 서비스
 */
@Component
public class SystemMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    
    @Scheduled(fixedRate = 5000) // 5초마다 수집
    public void collectMetrics() {
        // CPU 사용률
        double cpuUsage = osBean.getProcessCpuLoad() * 100;
        meterRegistry.gauge("system.cpu.usage", cpuUsage);
        
        // 메모리 사용률
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsage = (double) heapUsage.getUsed() / heapUsage.getMax() * 100;
        meterRegistry.gauge("system.memory.usage", memoryUsage);
        
        // 디스크 사용률
        File disk = new File("/");
        double diskUsage = (double) (disk.getTotalSpace() - disk.getFreeSpace()) 
                          / disk.getTotalSpace() * 100;
        meterRegistry.gauge("system.disk.usage", diskUsage);
    }
}
```

### 자동 알림 시스템
```java
/**
 * 시스템 임계치 모니터링
 */
@Component
public class SystemAlertManager {
    
    @EventListener
    public void handleHighCpuUsage(HighCpuUsageEvent event) {
        if (event.getCpuUsage() > 90.0) {
            sendCriticalAlert("CPU 사용률이 90%를 초과했습니다: " + event.getCpuUsage() + "%");
        }
    }
    
    @EventListener
    public void handleHighMemoryUsage(HighMemoryUsageEvent event) {
        if (event.getMemoryUsage() > 85.0) {
            sendWarningAlert("메모리 사용률이 85%를 초과했습니다: " + event.getMemoryUsage() + "%");
        }
    }
    
    private void sendCriticalAlert(String message) {
        // Slack, 이메일, SMS 등으로 즉시 알림
        notificationService.sendSystemAlert(AlertLevel.CRITICAL, message);
    }
}
```

---

*SystemController 설계 완료일: 2025-08-25*
*구현 항목: 헬스체크, 통계 조회, 로그 관리, 캐시 관리, 백업 시스템*
*보안 기능: 관리자 권한 검증, 작업 감사 로깅, Rate Limiting*
*다음 단계: Request/Response DTOs 완성*