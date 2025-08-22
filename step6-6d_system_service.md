# ⚡ Step 6-6d: SystemService 구현

> 시스템 상태 모니터링, 헬스체크 및 백업 관리 완전 구현  
> 생성일: 2025-08-22  
> 기반: 전체 시스템 통합 모니터링

---

## 🎯 설계 목표

- **시스템 모니터링**: 실시간 시스템 상태 추적 및 분석
- **헬스체크**: 데이터베이스, Redis, 외부 API 상태 체크
- **성능 감시**: CPU, 메모리, 디스크 사용량 모니터링
- **백업 관리**: 자동 백업 및 복구 시스템
- **장애 대응**: 이상 상황 자동 감지 및 알림
- **시스템 설정**: 동적 설정 관리 및 적용

---

## ✅ SystemService.java

```java
package com.routepick.service.system;

import com.routepick.dto.system.SystemStatusDto;
import com.routepick.dto.system.HealthCheckDto;
import com.routepick.dto.system.SystemMetricsDto;
import com.routepick.dto.system.BackupStatusDto;
import com.routepick.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 시스템 관리 서비스
 * - 시스템 상태 모니터링
 * - 헬스체크 및 장애 감지
 * - 백업 및 복구 관리
 * - 성능 지표 수집 및 분석
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService implements HealthIndicator {
    
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationService notificationService;
    private final CacheService cacheService;
    private final ApiLogService apiLogService;
    private final ExternalApiService externalApiService;
    
    @Value("${app.backup.directory:/backup}")
    private String backupDirectory;
    
    @Value("${app.monitoring.enabled:true}")
    private boolean monitoringEnabled;
    
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    
    // 임계치 설정
    private static final double CPU_THRESHOLD = 80.0; // 80%
    private static final double MEMORY_THRESHOLD = 85.0; // 85%
    private static final double DISK_THRESHOLD = 90.0; // 90%
    private static final long RESPONSE_TIME_THRESHOLD = 5000L; // 5초
    
    // ===== 헬스체크 =====
    
    /**
     * Spring Boot Actuator 헬스체크
     */
    @Override
    public Health health() {
        try {
            SystemStatusDto systemStatus = getSystemStatus();
            
            if (systemStatus.isHealthy()) {
                return Health.up()
                        .withDetail("status", "UP")
                        .withDetail("database", systemStatus.getDatabaseStatus())
                        .withDetail("redis", systemStatus.getRedisStatus())
                        .withDetail("timestamp", systemStatus.getCheckTime())
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "DOWN")
                        .withDetail("issues", systemStatus.getHealthIssues())
                        .withDetail("timestamp", systemStatus.getCheckTime())
                        .build();
            }
            
        } catch (Exception e) {
            log.error("헬스체크 실패", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("timestamp", LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * 전체 시스템 상태 조회
     */
    public SystemStatusDto getSystemStatus() {
        log.debug("시스템 상태 조회 시작");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 각 컴포넌트 상태 체크
            HealthCheckDto databaseHealth = checkDatabaseHealth();
            HealthCheckDto redisHealth = checkRedisHealth();
            HealthCheckDto externalApiHealth = checkExternalApiHealth();
            SystemMetricsDto metrics = collectSystemMetrics();
            
            long checkDuration = System.currentTimeMillis() - startTime;
            
            // 전체 상태 판단
            boolean isHealthy = databaseHealth.isHealthy() && 
                               redisHealth.isHealthy() && 
                               externalApiHealth.isHealthy() &&
                               metrics.isWithinThresholds();
            
            List<String> healthIssues = new ArrayList<>();
            if (!databaseHealth.isHealthy()) {
                healthIssues.add("Database: " + databaseHealth.getMessage());
            }
            if (!redisHealth.isHealthy()) {
                healthIssues.add("Redis: " + redisHealth.getMessage());
            }
            if (!externalApiHealth.isHealthy()) {
                healthIssues.add("External API: " + externalApiHealth.getMessage());
            }
            if (!metrics.isWithinThresholds()) {
                healthIssues.add("System Metrics: " + metrics.getIssueDescription());
            }
            
            SystemStatusDto status = SystemStatusDto.builder()
                    .isHealthy(isHealthy)
                    .databaseStatus(databaseHealth.getStatus())
                    .redisStatus(redisHealth.getStatus())
                    .externalApiStatus(externalApiHealth.getStatus())
                    .systemMetrics(metrics)
                    .healthIssues(healthIssues)
                    .checkTime(LocalDateTime.now())
                    .checkDurationMs(checkDuration)
                    .environment(activeProfile)
                    .build();
            
            log.debug("시스템 상태 조회 완료: {} ({}ms)", 
                    isHealthy ? "정상" : "이상", checkDuration);
            
            return status;
            
        } catch (Exception e) {
            log.error("시스템 상태 조회 실패", e);
            
            return SystemStatusDto.builder()
                    .isHealthy(false)
                    .healthIssues(List.of("System check failed: " + e.getMessage()))
                    .checkTime(LocalDateTime.now())
                    .checkDurationMs(System.currentTimeMillis() - startTime)
                    .environment(activeProfile)
                    .build();
        }
    }
    
    /**
     * 데이터베이스 헬스체크
     */
    private HealthCheckDto checkDatabaseHealth() {
        long startTime = System.currentTimeMillis();
        
        try (Connection connection = dataSource.getConnection()) {
            // 간단한 쿼리로 DB 연결 상태 확인
            boolean isValid = connection.isValid(5); // 5초 타임아웃
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (isValid && responseTime < RESPONSE_TIME_THRESHOLD) {
                return HealthCheckDto.builder()
                        .status("UP")
                        .isHealthy(true)
                        .responseTimeMs(responseTime)
                        .message("Database connection successful")
                        .checkedAt(LocalDateTime.now())
                        .build();
            } else {
                String message = !isValid ? "Database connection failed" : 
                               "Database response time too slow: " + responseTime + "ms";
                
                return HealthCheckDto.builder()
                        .status("DOWN")
                        .isHealthy(false)
                        .responseTimeMs(responseTime)
                        .message(message)
                        .checkedAt(LocalDateTime.now())
                        .build();
            }
            
        } catch (Exception e) {
            log.error("데이터베이스 헬스체크 실패", e);
            
            return HealthCheckDto.builder()
                    .status("DOWN")
                    .isHealthy(false)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .message("Database error: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Redis 헬스체크
     */
    private HealthCheckDto checkRedisHealth() {
        long startTime = System.currentTimeMillis();
        
        try {
            // Redis PING 명령어로 연결 상태 확인
            String testKey = "health:check:" + System.currentTimeMillis();
            String testValue = "test";
            
            redisTemplate.opsForValue().set(testKey, testValue);
            String result = (String) redisTemplate.opsForValue().get(testKey);
            redisTemplate.delete(testKey);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (testValue.equals(result) && responseTime < RESPONSE_TIME_THRESHOLD) {
                return HealthCheckDto.builder()
                        .status("UP")
                        .isHealthy(true)
                        .responseTimeMs(responseTime)
                        .message("Redis connection successful")
                        .checkedAt(LocalDateTime.now())
                        .build();
            } else {
                String message = !testValue.equals(result) ? "Redis data consistency failed" : 
                               "Redis response time too slow: " + responseTime + "ms";
                
                return HealthCheckDto.builder()
                        .status("DOWN")
                        .isHealthy(false)
                        .responseTimeMs(responseTime)
                        .message(message)
                        .checkedAt(LocalDateTime.now())
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Redis 헬스체크 실패", e);
            
            return HealthCheckDto.builder()
                    .status("DOWN")
                    .isHealthy(false)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .message("Redis error: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * 외부 API 헬스체크
     */
    private HealthCheckDto checkExternalApiHealth() {
        try {
            var apiHealthStatuses = externalApiService.getAllApiHealthStatus();
            
            long totalApis = apiHealthStatuses.size();
            long healthyApis = apiHealthStatuses.stream()
                    .mapToLong(status -> status.getIsHealthy() ? 1 : 0)
                    .sum();
            
            double healthyRatio = totalApis > 0 ? (double) healthyApis / totalApis : 1.0;
            
            if (healthyRatio >= 0.8) { // 80% 이상 정상이면 OK
                return HealthCheckDto.builder()
                        .status("UP")
                        .isHealthy(true)
                        .message(String.format("External APIs healthy: %d/%d", healthyApis, totalApis))
                        .checkedAt(LocalDateTime.now())
                        .build();
            } else {
                return HealthCheckDto.builder()
                        .status("DEGRADED")
                        .isHealthy(false)
                        .message(String.format("Some external APIs down: %d/%d healthy", healthyApis, totalApis))
                        .checkedAt(LocalDateTime.now())
                        .build();
            }
            
        } catch (Exception e) {
            log.error("외부 API 헬스체크 실패", e);
            
            return HealthCheckDto.builder()
                    .status("DOWN")
                    .isHealthy(false)
                    .message("External API check failed: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    // ===== 시스템 메트릭 수집 =====
    
    /**
     * 시스템 메트릭 수집
     */
    private SystemMetricsDto collectSystemMetrics() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            Runtime runtime = Runtime.getRuntime();
            
            // CPU 사용률 (Java 9+ 기능 사용 시)
            double cpuUsage = osBean.getProcessCpuLoad() * 100;
            if (cpuUsage < 0) cpuUsage = 0; // 값이 음수인 경우 0으로 설정
            
            // 메모리 사용률
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsage = (double) usedMemory / maxMemory * 100;
            
            // 디스크 사용률
            File disk = new File("/");
            long totalSpace = disk.getTotalSpace();
            long freeSpace = disk.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            double diskUsage = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;
            
            // JVM 메트릭
            int activeThreads = Thread.activeCount();
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();
            
            return SystemMetricsDto.builder()
                    .cpuUsagePercent(cpuUsage)
                    .memoryUsagePercent(memoryUsage)
                    .diskUsagePercent(diskUsage)
                    .usedMemoryMB(usedMemory / (1024 * 1024))
                    .maxMemoryMB(maxMemory / (1024 * 1024))
                    .freeMemoryMB(freeMemory / (1024 * 1024))
                    .totalSpaceGB(totalSpace / (1024 * 1024 * 1024))
                    .freeSpaceGB(freeSpace / (1024 * 1024 * 1024))
                    .usedSpaceGB(usedSpace / (1024 * 1024 * 1024))
                    .activeThreads(activeThreads)
                    .heapUsedMB(heapUsed / (1024 * 1024))
                    .heapMaxMB(heapMax / (1024 * 1024))
                    .collectedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("시스템 메트릭 수집 실패", e);
            
            return SystemMetricsDto.builder()
                    .cpuUsagePercent(0.0)
                    .memoryUsagePercent(0.0)
                    .diskUsagePercent(0.0)
                    .collectedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    // ===== 정기 모니터링 =====
    
    /**
     * 정기 시스템 모니터링 (5분마다)
     */
    @Scheduled(fixedRate = 300000) // 5분
    @Async
    public void performSystemMonitoring() {
        if (!monitoringEnabled) {
            return;
        }
        
        log.debug("정기 시스템 모니터링 시작");
        
        try {
            SystemStatusDto systemStatus = getSystemStatus();
            
            // 시스템 이상 상황 체크
            if (!systemStatus.isHealthy()) {
                notifySystemIssue(systemStatus);
            }
            
            // 임계치 초과 체크
            SystemMetricsDto metrics = systemStatus.getSystemMetrics();
            if (metrics != null) {
                checkPerformanceThresholds(metrics);
            }
            
            log.debug("정기 시스템 모니터링 완료: {}", 
                    systemStatus.isHealthy() ? "정상" : "이상");
            
        } catch (Exception e) {
            log.error("정기 시스템 모니터링 실패", e);
        }
    }
    
    /**
     * 성능 임계치 체크
     */
    private void checkPerformanceThresholds(SystemMetricsDto metrics) {
        try {
            // CPU 사용률 체크
            if (metrics.getCpuUsagePercent() > CPU_THRESHOLD) {
                notifyHighCpuUsage(metrics.getCpuUsagePercent());
            }
            
            // 메모리 사용률 체크
            if (metrics.getMemoryUsagePercent() > MEMORY_THRESHOLD) {
                notifyHighMemoryUsage(metrics.getMemoryUsagePercent());
            }
            
            // 디스크 사용률 체크
            if (metrics.getDiskUsagePercent() > DISK_THRESHOLD) {
                notifyHighDiskUsage(metrics.getDiskUsagePercent());
            }
            
        } catch (Exception e) {
            log.error("성능 임계치 체크 실패", e);
        }
    }
    
    // ===== 백업 관리 =====
    
    /**
     * 자동 백업 실행 (매일 새벽 3시)
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Async
    @Transactional
    public CompletableFuture<BackupStatusDto> performAutoBackup() {
        log.info("자동 백업 시작");
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = "auto_backup_" + timestamp;
        
        return performBackup(backupName, true);
    }
    
    /**
     * 수동 백업 실행
     */
    @Async
    @Transactional
    public CompletableFuture<BackupStatusDto> performManualBackup(String backupName) {
        log.info("수동 백업 시작: {}", backupName);
        
        return performBackup(backupName, false);
    }
    
    /**
     * 백업 실행
     */
    private CompletableFuture<BackupStatusDto> performBackup(String backupName, boolean isAutomatic) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 백업 디렉토리 생성
            File backupDir = new File(backupDirectory);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            String backupPath = backupDirectory + "/" + backupName + ".sql";
            
            // 데이터베이스 백업 (mysqldump 사용 예시)
            ProcessBuilder pb = new ProcessBuilder(
                "mysqldump",
                "--host=localhost",
                "--user=" + System.getProperty("DB_USER", "root"),
                "--password=" + System.getProperty("DB_PASSWORD", ""),
                "--single-transaction",
                "--routines",
                "--triggers",
                "routepick"
            );
            
            pb.redirectOutput(new File(backupPath));
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            long duration = System.currentTimeMillis() - startTime;
            File backupFile = new File(backupPath);
            
            if (exitCode == 0 && backupFile.exists()) {
                BackupStatusDto successStatus = BackupStatusDto.builder()
                        .backupName(backupName)
                        .backupPath(backupPath)
                        .isSuccessful(true)
                        .isAutomatic(isAutomatic)
                        .fileSizeMB(backupFile.length() / (1024 * 1024))
                        .durationMs(duration)
                        .createdAt(LocalDateTime.now())
                        .message("백업 성공")
                        .build();
                
                log.info("백업 완료: {} ({}MB, {}ms)", 
                        backupName, successStatus.getFileSizeMB(), duration);
                
                // 성공 알림
                notifyBackupSuccess(successStatus);
                
                // 오래된 백업 정리
                cleanOldBackups();
                
                return CompletableFuture.completedFuture(successStatus);
                
            } else {
                throw new RuntimeException("백업 실패: exit code " + exitCode);
            }
            
        } catch (Exception e) {
            log.error("백업 실패: " + backupName, e);
            
            BackupStatusDto failStatus = BackupStatusDto.builder()
                    .backupName(backupName)
                    .isSuccessful(false)
                    .isAutomatic(isAutomatic)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .createdAt(LocalDateTime.now())
                    .message("백업 실패: " + e.getMessage())
                    .build();
            
            // 실패 알림
            notifyBackupFailure(failStatus);
            
            return CompletableFuture.completedFuture(failStatus);
        }
    }
    
    /**
     * 오래된 백업 정리 (30일 이상)
     */
    private void cleanOldBackups() {
        try {
            File backupDir = new File(backupDirectory);
            File[] backupFiles = backupDir.listFiles(file -> 
                file.getName().endsWith(".sql") && 
                file.lastModified() < System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            );
            
            if (backupFiles != null) {
                int deletedCount = 0;
                for (File file : backupFiles) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
                
                if (deletedCount > 0) {
                    log.info("오래된 백업 파일 정리: {}개 삭제", deletedCount);
                }
            }
            
        } catch (Exception e) {
            log.error("백업 정리 실패", e);
        }
    }
    
    /**
     * 백업 목록 조회
     */
    public List<BackupStatusDto> getBackupList() {
        try {
            File backupDir = new File(backupDirectory);
            File[] backupFiles = backupDir.listFiles(file -> file.getName().endsWith(".sql"));
            
            if (backupFiles == null) {
                return Collections.emptyList();
            }
            
            return Arrays.stream(backupFiles)
                    .map(this::createBackupStatusFromFile)
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .collect(ArrayList::new, (list, item) -> list.add(item), ArrayList::addAll);
                    
        } catch (Exception e) {
            log.error("백업 목록 조회 실패", e);
            return Collections.emptyList();
        }
    }
    
    // ===== 알림 시스템 =====
    
    /**
     * 시스템 이상 알림
     */
    private void notifySystemIssue(SystemStatusDto status) {
        try {
            String message = String.format(
                "시스템 상태 이상 감지 (%s 환경)\n문제점: %s",
                status.getEnvironment(),
                String.join(", ", status.getHealthIssues())
            );
            
            notificationService.sendSystemAlert("SYSTEM_HEALTH_ISSUE", message, Map.of(
                "environment", status.getEnvironment(),
                "issues", status.getHealthIssues(),
                "checkTime", status.getCheckTime()
            ));
            
        } catch (Exception e) {
            log.error("시스템 이상 알림 발송 실패", e);
        }
    }
    
    /**
     * 높은 CPU 사용률 알림
     */
    private void notifyHighCpuUsage(double cpuUsage) {
        try {
            String message = String.format(
                "높은 CPU 사용률: %.1f%% (임계치: %.0f%%)",
                cpuUsage, CPU_THRESHOLD
            );
            
            notificationService.sendSystemAlert("HIGH_CPU_USAGE", message, Map.of(
                "cpuUsage", cpuUsage,
                "threshold", CPU_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("높은 CPU 사용률 알림 발송 실패", e);
        }
    }
    
    /**
     * 높은 메모리 사용률 알림
     */
    private void notifyHighMemoryUsage(double memoryUsage) {
        try {
            String message = String.format(
                "높은 메모리 사용률: %.1f%% (임계치: %.0f%%)",
                memoryUsage, MEMORY_THRESHOLD
            );
            
            notificationService.sendSystemAlert("HIGH_MEMORY_USAGE", message, Map.of(
                "memoryUsage", memoryUsage,
                "threshold", MEMORY_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("높은 메모리 사용률 알림 발송 실패", e);
        }
    }
    
    /**
     * 높은 디스크 사용률 알림
     */
    private void notifyHighDiskUsage(double diskUsage) {
        try {
            String message = String.format(
                "높은 디스크 사용률: %.1f%% (임계치: %.0f%%)",
                diskUsage, DISK_THRESHOLD
            );
            
            notificationService.sendSystemAlert("HIGH_DISK_USAGE", message, Map.of(
                "diskUsage", diskUsage,
                "threshold", DISK_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("높은 디스크 사용률 알림 발송 실패", e);
        }
    }
    
    /**
     * 백업 성공 알림
     */
    private void notifyBackupSuccess(BackupStatusDto backup) {
        try {
            String message = String.format(
                "데이터베이스 백업 완료: %s (%dMB)",
                backup.getBackupName(),
                backup.getFileSizeMB()
            );
            
            notificationService.sendSystemAlert("BACKUP_SUCCESS", message, Map.of(
                "backupName", backup.getBackupName(),
                "fileSizeMB", backup.getFileSizeMB(),
                "isAutomatic", backup.getIsAutomatic()
            ));
            
        } catch (Exception e) {
            log.error("백업 성공 알림 발송 실패", e);
        }
    }
    
    /**
     * 백업 실패 알림
     */
    private void notifyBackupFailure(BackupStatusDto backup) {
        try {
            String message = String.format(
                "데이터베이스 백업 실패: %s\n오류: %s",
                backup.getBackupName(),
                backup.getMessage()
            );
            
            notificationService.sendSystemAlert("BACKUP_FAILURE", message, Map.of(
                "backupName", backup.getBackupName(),
                "errorMessage", backup.getMessage(),
                "isAutomatic", backup.getIsAutomatic()
            ));
            
        } catch (Exception e) {
            log.error("백업 실패 알림 발송 실패", e);
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 파일에서 BackupStatusDto 생성
     */
    private BackupStatusDto createBackupStatusFromFile(File file) {
        try {
            String fileName = file.getName();
            boolean isAutomatic = fileName.startsWith("auto_backup_");
            
            return BackupStatusDto.builder()
                    .backupName(fileName.replace(".sql", ""))
                    .backupPath(file.getAbsolutePath())
                    .isSuccessful(true)
                    .isAutomatic(isAutomatic)
                    .fileSizeMB(file.length() / (1024 * 1024))
                    .createdAt(LocalDateTime.ofEpochSecond(
                        file.lastModified() / 1000, 0, 
                        java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now())
                    ))
                    .message("백업 파일 존재")
                    .build();
                    
        } catch (Exception e) {
            log.error("BackupStatusDto 생성 실패: " + file.getName(), e);
            
            return BackupStatusDto.builder()
                    .backupName(file.getName())
                    .isSuccessful(false)
                    .message("정보 조회 실패")
                    .build();
        }
    }
}
```

---

## 📊 DTO 클래스들

### SystemStatusDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 시스템 상태 DTO
 */
@Getter
@Builder
public class SystemStatusDto {
    private Boolean isHealthy;
    private String databaseStatus;
    private String redisStatus;
    private String externalApiStatus;
    private SystemMetricsDto systemMetrics;
    private List<String> healthIssues;
    private LocalDateTime checkTime;
    private Long checkDurationMs;
    private String environment;
    
    public String getOverallStatus() {
        if (isHealthy == null) return "UNKNOWN";
        return isHealthy ? "HEALTHY" : "UNHEALTHY";
    }
    
    public String getStatusColor() {
        if (isHealthy == null) return "gray";
        return isHealthy ? "green" : "red";
    }
    
    public int getIssueCount() {
        return healthIssues != null ? healthIssues.size() : 0;
    }
    
    public boolean hasIssues() {
        return getIssueCount() > 0;
    }
    
    public String getCheckDurationDisplay() {
        return checkDurationMs != null ? checkDurationMs + "ms" : "N/A";
    }
}
```

### HealthCheckDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 헬스체크 결과 DTO
 */
@Getter
@Builder
public class HealthCheckDto {
    private String status;
    private Boolean isHealthy;
    private Long responseTimeMs;
    private String message;
    private LocalDateTime checkedAt;
    
    public String getStatusColor() {
        if (!isHealthy) return "red";
        
        return switch (status) {
            case "UP" -> "green";
            case "DEGRADED" -> "yellow";
            case "DOWN" -> "red";
            default -> "gray";
        };
    }
    
    public String getResponseTimeDisplay() {
        if (responseTimeMs == null) return "N/A";
        
        if (responseTimeMs < 100) return "매우 빠름 (" + responseTimeMs + "ms)";
        if (responseTimeMs < 500) return "빠름 (" + responseTimeMs + "ms)";
        if (responseTimeMs < 1000) return "보통 (" + responseTimeMs + "ms)";
        if (responseTimeMs < 5000) return "느림 (" + responseTimeMs + "ms)";
        return "매우 느림 (" + responseTimeMs + "ms)";
    }
    
    public boolean isResponseTimeAcceptable() {
        return responseTimeMs != null && responseTimeMs < 5000; // 5초 미만
    }
}
```

### SystemMetricsDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 시스템 메트릭 DTO
 */
@Getter
@Builder
public class SystemMetricsDto {
    private Double cpuUsagePercent;
    private Double memoryUsagePercent;
    private Double diskUsagePercent;
    private Long usedMemoryMB;
    private Long maxMemoryMB;
    private Long freeMemoryMB;
    private Long totalSpaceGB;
    private Long freeSpaceGB;
    private Long usedSpaceGB;
    private Integer activeThreads;
    private Long heapUsedMB;
    private Long heapMaxMB;
    private LocalDateTime collectedAt;
    
    public boolean isWithinThresholds() {
        return cpuUsagePercent < 80.0 && 
               memoryUsagePercent < 85.0 && 
               diskUsagePercent < 90.0;
    }
    
    public String getIssueDescription() {
        if (isWithinThresholds()) return "모든 지표 정상";
        
        StringBuilder issues = new StringBuilder();
        if (cpuUsagePercent >= 80.0) {
            issues.append("CPU 사용률 높음(").append(String.format("%.1f%%", cpuUsagePercent)).append(") ");
        }
        if (memoryUsagePercent >= 85.0) {
            issues.append("메모리 사용률 높음(").append(String.format("%.1f%%", memoryUsagePercent)).append(") ");
        }
        if (diskUsagePercent >= 90.0) {
            issues.append("디스크 사용률 높음(").append(String.format("%.1f%%", diskUsagePercent)).append(") ");
        }
        
        return issues.toString().trim();
    }
    
    public String getCpuLevel() {
        if (cpuUsagePercent == null) return "알 수 없음";
        
        if (cpuUsagePercent >= 90) return "위험";
        if (cpuUsagePercent >= 80) return "경고";
        if (cpuUsagePercent >= 60) return "주의";
        return "정상";
    }
    
    public String getMemoryLevel() {
        if (memoryUsagePercent == null) return "알 수 없음";
        
        if (memoryUsagePercent >= 95) return "위험";
        if (memoryUsagePercent >= 85) return "경고";
        if (memoryUsagePercent >= 70) return "주의";
        return "정상";
    }
    
    public String getDiskLevel() {
        if (diskUsagePercent == null) return "알 수 없음";
        
        if (diskUsagePercent >= 95) return "위험";
        if (diskUsagePercent >= 90) return "경고";
        if (diskUsagePercent >= 80) return "주의";
        return "정상";
    }
    
    public Double getHeapUsagePercent() {
        if (heapUsedMB == null || heapMaxMB == null || heapMaxMB == 0) {
            return 0.0;
        }
        return (double) heapUsedMB / heapMaxMB * 100;
    }
}
```

### BackupStatusDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 백업 상태 DTO
 */
@Getter
@Builder
public class BackupStatusDto {
    private String backupName;
    private String backupPath;
    private Boolean isSuccessful;
    private Boolean isAutomatic;
    private Long fileSizeMB;
    private Long durationMs;
    private LocalDateTime createdAt;
    private String message;
    
    public String getBackupType() {
        return isAutomatic ? "자동 백업" : "수동 백업";
    }
    
    public String getStatusDisplay() {
        if (isSuccessful == null) return "알 수 없음";
        return isSuccessful ? "성공" : "실패";
    }
    
    public String getStatusColor() {
        if (isSuccessful == null) return "gray";
        return isSuccessful ? "green" : "red";
    }
    
    public String getFileSizeDisplay() {
        if (fileSizeMB == null) return "N/A";
        
        if (fileSizeMB >= 1024) {
            return String.format("%.2f GB", fileSizeMB / 1024.0);
        } else {
            return fileSizeMB + " MB";
        }
    }
    
    public String getDurationDisplay() {
        if (durationMs == null) return "N/A";
        
        long seconds = durationMs / 1000;
        if (seconds >= 60) {
            return String.format("%d분 %d초", seconds / 60, seconds % 60);
        } else {
            return seconds + "초";
        }
    }
    
    public boolean isOld() {
        return createdAt != null && 
               createdAt.isBefore(LocalDateTime.now().minusDays(30));
    }
}
```

---

## 📈 주요 특징

### 1. **통합 헬스체크**
- 데이터베이스, Redis, 외부 API 상태 통합 모니터링
- Spring Boot Actuator 연동
- 응답 시간 기반 성능 평가

### 2. **실시간 모니터링**
- JVM 메트릭 수집 (CPU, 메모리, 스레드)
- 시스템 리소스 모니터링
- 임계치 기반 자동 알림

### 3. **자동 백업 시스템**
- 정기 자동 백업 (매일 새벽 3시)
- 수동 백업 지원
- 오래된 백업 자동 정리

### 4. **장애 대응**
- 실시간 이상 상황 감지
- 다단계 알림 시스템
- 상세한 문제 분석 리포트

---

**📝 다음 단계**: step6-6e_service_layer_validation.md  
**완료일**: 2025-08-22  
**핵심 성과**: 시스템 모니터링 + 헬스체크 + 백업 관리 + 장애 대응 완성