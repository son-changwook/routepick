# Step 6-6d1: SystemMonitoring Service 구현

## 📋 구현 목표
- **실시간 시스템 모니터링**: CPU, 메모리, 디스크 사용량 추적
- **성능 지표 수집**: JVM 메트릭 및 시스템 리소스 분석
- **임계치 관리**: 리소스 사용량 임계치 모니터링 및 알림
- **정기 모니터링**: 5분 간격 자동 시스템 상태 체크

## 🔍 SystemMonitoringService 구현

### SystemMonitoringService.java
```java
package com.routepick.backend.service.system;

import com.routepick.backend.dto.system.SystemMetricsDto;
import com.routepick.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 시스템 모니터링 서비스
 * - 실시간 시스템 리소스 모니터링
 * - 성능 지표 수집 및 분석
 * - 임계치 기반 자동 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMonitoringService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationService notificationService;
    
    @Value("${app.monitoring.enabled:true}")
    private boolean monitoringEnabled;
    
    @Value("${app.monitoring.alert.enabled:true}")
    private boolean alertEnabled;
    
    // 임계치 설정
    private static final double CPU_THRESHOLD = 80.0; // 80%
    private static final double MEMORY_THRESHOLD = 85.0; // 85%
    private static final double DISK_THRESHOLD = 90.0; // 90%
    private static final int ALERT_COOLDOWN_MINUTES = 30; // 30분 쿨다운
    
    // Redis Keys
    private static final String METRICS_KEY = "system:metrics:current";
    private static final String HISTORY_KEY_PREFIX = "system:metrics:history:";
    private static final String ALERT_KEY_PREFIX = "system:alert:last:";
    
    /**
     * 현재 시스템 메트릭 수집
     */
    public SystemMetricsDto collectSystemMetrics() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            Runtime runtime = Runtime.getRuntime();
            
            // CPU 사용률 계산
            double cpuUsage = calculateCpuUsage(osBean);
            
            // 메모리 사용률 계산
            SystemMemoryInfo memoryInfo = calculateMemoryUsage(runtime);
            
            // 디스크 사용률 계산
            SystemDiskInfo diskInfo = calculateDiskUsage();
            
            // JVM 메트릭 수집
            JvmMetrics jvmMetrics = collectJvmMetrics(memoryBean);
            
            SystemMetricsDto metrics = SystemMetricsDto.builder()
                    .cpuUsagePercent(cpuUsage)
                    .memoryUsagePercent(memoryInfo.usagePercent)
                    .diskUsagePercent(diskInfo.usagePercent)
                    .usedMemoryMB(memoryInfo.usedMB)
                    .maxMemoryMB(memoryInfo.maxMB)
                    .freeMemoryMB(memoryInfo.freeMB)
                    .totalSpaceGB(diskInfo.totalGB)
                    .freeSpaceGB(diskInfo.freeGB)
                    .usedSpaceGB(diskInfo.usedGB)
                    .activeThreads(jvmMetrics.activeThreads)
                    .heapUsedMB(jvmMetrics.heapUsedMB)
                    .heapMaxMB(jvmMetrics.heapMaxMB)
                    .collectedAt(LocalDateTime.now())
                    .build();
            
            // Redis에 현재 메트릭 저장
            storeCurrentMetrics(metrics);
            
            // 임계치 확인 및 알림
            checkThresholdsAndAlert(metrics);
            
            return metrics;
            
        } catch (Exception e) {
            log.error("시스템 메트릭 수집 실패", e);
            return createErrorMetrics();
        }
    }
    
    /**
     * CPU 사용률 계산
     */
    private double calculateCpuUsage(OperatingSystemMXBean osBean) {
        try {
            double cpuUsage = osBean.getProcessCpuLoad() * 100;
            
            // 음수 값 처리 (측정 불가능한 경우)
            if (cpuUsage < 0) {
                // 시스템 전체 CPU 사용률로 대체
                cpuUsage = osBean.getSystemCpuLoad() * 100;
            }
            
            return Math.max(0, Math.min(100, cpuUsage));
            
        } catch (Exception e) {
            log.warn("CPU 사용률 계산 실패: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * 메모리 사용률 계산
     */
    private SystemMemoryInfo calculateMemoryUsage(Runtime runtime) {
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double memoryUsage = maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;
        
        return new SystemMemoryInfo(
                memoryUsage,
                usedMemory / (1024 * 1024),  // MB
                maxMemory / (1024 * 1024),   // MB
                freeMemory / (1024 * 1024)   // MB
        );
    }
    
    /**
     * 디스크 사용률 계산
     */
    private SystemDiskInfo calculateDiskUsage() {
        try {
            File disk = new File("/");
            long totalSpace = disk.getTotalSpace();
            long freeSpace = disk.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            
            double diskUsage = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;
            
            return new SystemDiskInfo(
                    diskUsage,
                    totalSpace / (1024L * 1024L * 1024L),  // GB
                    freeSpace / (1024L * 1024L * 1024L),   // GB
                    usedSpace / (1024L * 1024L * 1024L)    // GB
            );
            
        } catch (Exception e) {
            log.warn("디스크 사용률 계산 실패: {}", e.getMessage());
            return new SystemDiskInfo(0.0, 0L, 0L, 0L);
        }
    }
    
    /**
     * JVM 메트릭 수집
     */
    private JvmMetrics collectJvmMetrics(MemoryMXBean memoryBean) {
        int activeThreads = Thread.activeCount();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024); // MB
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);   // MB
        
        return new JvmMetrics(activeThreads, heapUsed, heapMax);
    }
    
    /**
     * 임계치 확인 및 알림
     */
    private void checkThresholdsAndAlert(SystemMetricsDto metrics) {
        if (!alertEnabled) {
            return;
        }
        
        // CPU 임계치 확인
        if (metrics.getCpuUsagePercent() > CPU_THRESHOLD) {
            sendThresholdAlert("CPU", metrics.getCpuUsagePercent(), CPU_THRESHOLD);
        }
        
        // 메모리 임계치 확인
        if (metrics.getMemoryUsagePercent() > MEMORY_THRESHOLD) {
            sendThresholdAlert("Memory", metrics.getMemoryUsagePercent(), MEMORY_THRESHOLD);
        }
        
        // 디스크 임계치 확인
        if (metrics.getDiskUsagePercent() > DISK_THRESHOLD) {
            sendThresholdAlert("Disk", metrics.getDiskUsagePercent(), DISK_THRESHOLD);
        }
    }
    
    /**
     * 임계치 초과 알림 발송
     */
    @Async
    protected void sendThresholdAlert(String resourceType, double currentUsage, double threshold) {
        String alertKey = ALERT_KEY_PREFIX + resourceType.toLowerCase();
        
        try {
            // 쿨다운 확인
            String lastAlertTime = (String) redisTemplate.opsForValue().get(alertKey);
            if (lastAlertTime != null) {
                LocalDateTime lastAlert = LocalDateTime.parse(lastAlertTime);
                if (lastAlert.plusMinutes(ALERT_COOLDOWN_MINUTES).isAfter(LocalDateTime.now())) {
                    return; // 쿨다운 중
                }
            }
            
            String message = String.format(
                    "🚨 %s 사용률 임계치 초과\\n현재: %.1f%%, 임계치: %.1f%%\\n시스템을 점검해주세요.",
                    resourceType, currentUsage, threshold);
            
            // 관리자에게 알림 발송
            CompletableFuture.runAsync(() -> {
                try {
                    notificationService.sendSystemAlert("시스템 리소스 경고", message);
                    log.warn("시스템 리소스 경고 발송: {} {}%", resourceType, currentUsage);
                } catch (Exception e) {
                    log.error("시스템 알림 발송 실패", e);
                }
            });
            
            // 마지막 알림 시간 저장
            redisTemplate.opsForValue().set(alertKey, LocalDateTime.now().toString());
            redisTemplate.expire(alertKey, java.time.Duration.ofHours(1));
            
        } catch (Exception e) {
            log.error("임계치 알림 처리 실패", e);
        }
    }
    
    /**
     * 현재 메트릭을 Redis에 저장
     */
    private void storeCurrentMetrics(SystemMetricsDto metrics) {
        try {
            // 현재 메트릭 저장
            redisTemplate.opsForValue().set(METRICS_KEY, metrics);
            
            // 히스토리 저장 (1시간 보관)
            String historyKey = HISTORY_KEY_PREFIX + System.currentTimeMillis();
            redisTemplate.opsForValue().set(historyKey, metrics);
            redisTemplate.expire(historyKey, java.time.Duration.ofHours(1));
            
        } catch (Exception e) {
            log.error("메트릭 저장 실패", e);
        }
    }
    
    /**
     * 정기 시스템 모니터링 (5분마다)
     */
    @Scheduled(fixedRate = 300000) // 5분
    @Async
    public void performSystemMonitoring() {
        if (!monitoringEnabled) {
            return;
        }
        
        try {
            log.debug("정기 시스템 모니터링 시작");
            
            SystemMetricsDto metrics = collectSystemMetrics();
            
            // 시스템 상태 로깅
            log.info("시스템 상태 - CPU: {:.1f}%, Memory: {:.1f}%, Disk: {:.1f}%",
                    metrics.getCpuUsagePercent(),
                    metrics.getMemoryUsagePercent(),
                    metrics.getDiskUsagePercent());
            
            // 성능 통계 업데이트
            updatePerformanceStatistics(metrics);
            
        } catch (Exception e) {
            log.error("정기 시스템 모니터링 실패", e);
        }
    }
    
    /**
     * 성능 통계 업데이트
     */
    private void updatePerformanceStatistics(SystemMetricsDto metrics) {
        try {
            String today = LocalDateTime.now().toLocalDate().toString();
            String statsKey = "system:stats:" + today;
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("maxCpu", Math.max(
                    getStoredValue(statsKey, "maxCpu", 0.0),
                    metrics.getCpuUsagePercent()));
            stats.put("maxMemory", Math.max(
                    getStoredValue(statsKey, "maxMemory", 0.0),
                    metrics.getMemoryUsagePercent()));
            stats.put("maxDisk", Math.max(
                    getStoredValue(statsKey, "maxDisk", 0.0),
                    metrics.getDiskUsagePercent()));
            stats.put("lastUpdate", LocalDateTime.now().toString());
            
            redisTemplate.opsForHash().putAll(statsKey, stats);
            redisTemplate.expire(statsKey, java.time.Duration.ofDays(7)); // 7일 보관
            
        } catch (Exception e) {
            log.error("성능 통계 업데이트 실패", e);
        }
    }
    
    /**
     * Redis에서 저장된 값 조회
     */
    private double getStoredValue(String key, String field, double defaultValue) {
        try {
            Object value = redisTemplate.opsForHash().get(key, field);
            return value != null ? Double.parseDouble(value.toString()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * 에러 메트릭 생성
     */
    private SystemMetricsDto createErrorMetrics() {
        return SystemMetricsDto.builder()
                .cpuUsagePercent(0.0)
                .memoryUsagePercent(0.0)
                .diskUsagePercent(0.0)
                .usedMemoryMB(0L)
                .maxMemoryMB(0L)
                .freeMemoryMB(0L)
                .totalSpaceGB(0L)
                .freeSpaceGB(0L)
                .usedSpaceGB(0L)
                .activeThreads(0)
                .heapUsedMB(0L)
                .heapMaxMB(0L)
                .collectedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 현재 메트릭 조회
     */
    public SystemMetricsDto getCurrentMetrics() {
        try {
            SystemMetricsDto metrics = (SystemMetricsDto) redisTemplate.opsForValue().get(METRICS_KEY);
            return metrics != null ? metrics : collectSystemMetrics();
        } catch (Exception e) {
            log.error("현재 메트릭 조회 실패", e);
            return collectSystemMetrics();
        }
    }
    
    // ===== 내부 데이터 클래스 =====
    
    private record SystemMemoryInfo(double usagePercent, long usedMB, long maxMB, long freeMB) {}
    private record SystemDiskInfo(double usagePercent, long totalGB, long freeGB, long usedGB) {}
    private record JvmMetrics(int activeThreads, long heapUsedMB, long heapMaxMB) {}
}
```

---

**다음 파일**: step6-6d2_health_check_service.md (헬스체크 서비스)  
**연관 시스템**: NotificationService (알림 발송), Redis (메트릭 저장)  
**성능 목표**: 메트릭 수집 100ms 이내, 임계치 알림 1초 이내

*생성일: 2025-09-02*  
*RoutePickr 6-6d1: 실시간 시스템 모니터링 완성*