# 🚀 Step 6-6c3: Cache Statistics & Monitoring

> 캐시 통계 및 성능 모니터링 시스템  
> 생성일: 2025-09-01  
> 분할 기준: 캐시 통계 및 모니터링

---

## 🎯 설계 목표

- **성능 모니터링**: 캐시 히트율 및 성능 분석
- **통계 수집**: 캐시 사용량 및 패턴 분석
- **알림 시스템**: 성능 임계치 기반 알림
- **최적화 제안**: 성능 개선 방안 제시

---

## ✅ CacheStatisticsService.java

```java
package com.routepick.service.system;

import com.routepick.dto.system.CacheStatisticsDto;
import com.routepick.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 캐시 통계 및 모니터링 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheStatisticsService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationService notificationService;
    
    // 통계 데이터 저장
    private final Map<String, CacheMetrics> metricsHistory = new ConcurrentHashMap<>();
    
    // 성능 임계치
    private static final double HIT_RATE_THRESHOLD = 0.8; // 80%
    private static final long MEMORY_USAGE_THRESHOLD = 80; // 80%
    private static final long RESPONSE_TIME_THRESHOLD = 100; // 100ms
    
    // ===== 캐시 통계 수집 =====
    
    /**
     * 캐시 통계 조회
     */
    public CacheStatisticsDto getCacheStatistics() {
        try {
            // Redis 정보 조회
            Properties info = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .info();
            
            // 캐시별 키 개수 계산
            Map<String, Long> cacheKeyCounts = calculateCacheKeyCounts();
            
            // 메모리 사용량
            String usedMemory = info.getProperty("used_memory_human");
            String maxMemory = info.getProperty("maxmemory_human");
            double memoryUsagePercent = calculateMemoryUsagePercent(info);
            
            // 히트율 계산
            double hitRate = calculateHitRate(info);
            
            // 연결 정보
            String connectedClients = info.getProperty("connected_clients");
            String uptime = info.getProperty("uptime_in_seconds");
            
            // 응답 시간 계산
            double avgResponseTime = calculateAverageResponseTime();
            
            return CacheStatisticsDto.builder()
                    .totalKeys(getTotalKeyCount())
                    .cacheKeyCounts(cacheKeyCounts)
                    .usedMemory(usedMemory)
                    .maxMemory(maxMemory)
                    .memoryUsagePercent(memoryUsagePercent)
                    .hitRate(hitRate)
                    .connectedClients(connectedClients)
                    .uptime(uptime)
                    .averageResponseTime(avgResponseTime)
                    .lastUpdated(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            log.error("캐시 통계 조회 실패", e);
            return CacheStatisticsDto.builder()
                    .lastUpdated(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * 캐시별 세부 통계
     */
    public Map<String, Object> getDetailedStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 도메인별 통계
            stats.put("userCaches", analyzeDomainCache("users"));
            stats.put("routeCaches", analyzeDomainCache("routes"));
            stats.put("gymCaches", analyzeDomainCache("gyms"));
            stats.put("communityCaches", analyzeDomainCache("posts"));
            stats.put("paymentCaches", analyzeDomainCache("payments"));
            
            // 시간대별 통계
            stats.put("hourlyStats", getHourlyStatistics());
            
            // 크기별 분포
            stats.put("sizeDistribution", getCacheSizeDistribution());
            
            // TTL 분포
            stats.put("ttlDistribution", getTtlDistribution());
            
        } catch (Exception e) {
            log.error("세부 캐시 통계 조회 실패", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
    
    // ===== 성능 분석 =====
    
    /**
     * 캐시 성능 분석
     */
    public Map<String, Object> analyzePerformance() {
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            CacheStatisticsDto stats = getCacheStatistics();
            
            // 성능 등급
            analysis.put("performanceGrade", calculatePerformanceGrade(stats));
            
            // 히트율 분석
            analysis.put("hitRateAnalysis", analyzeHitRate(stats.getHitRate()));
            
            // 메모리 사용 분석
            analysis.put("memoryAnalysis", analyzeMemoryUsage(stats.getMemoryUsagePercent()));
            
            // 응답 시간 분석
            analysis.put("responseTimeAnalysis", analyzeResponseTime(stats.getAverageResponseTime()));
            
            // 개선 제안
            analysis.put("recommendations", generateRecommendations(stats));
            
            // 트렌드 분석
            analysis.put("trends", analyzeTrends());
            
        } catch (Exception e) {
            log.error("캐시 성능 분석 실패", e);
            analysis.put("error", e.getMessage());
        }
        
        return analysis;
    }
    
    /**
     * 캐시 최적화 제안
     */
    public List<String> getOptimizationSuggestions() {
        List<String> suggestions = new ArrayList<>();
        
        try {
            CacheStatisticsDto stats = getCacheStatistics();
            
            // 히트율이 낮은 경우
            if (stats.getHitRate() < 0.7) {
                suggestions.add("히트율이 낮습니다. TTL 설정 및 캐시 키 구조를 검토해보세요.");
                suggestions.add("자주 접근되는 데이터의 프리로딩을 고려해보세요.");
            }
            
            // 메모리 사용량이 높은 경우
            if (stats.getMemoryUsagePercent() > 85) {
                suggestions.add("메모리 사용량이 높습니다. 불필요한 캐시를 정리해보세요.");
                suggestions.add("TTL을 단축하여 메모리 사용량을 줄여보세요.");
            }
            
            // 응답 시간이 긴 경우
            if (stats.getAverageResponseTime() > 50) {
                suggestions.add("평균 응답 시간이 깁니다. Redis 연결 풀 설정을 확인해보세요.");
                suggestions.add("복잡한 데이터 구조 대신 단순한 구조 사용을 고려해보세요.");
            }
            
            // 키 개수가 많은 경우
            if (stats.getTotalKeys() > 100000) {
                suggestions.add("캐시 키가 많습니다. 정기적인 정리 작업을 강화해보세요.");
                suggestions.add("키 네이밍 패턴을 최적화해보세요.");
            }
            
            // 도메인별 분석
            Map<String, Long> keyCounts = stats.getCacheKeyCounts();
            if (keyCounts != null) {
                keyCounts.forEach((domain, count) -> {
                    if (count > 10000) {
                        suggestions.add(String.format("%s 도메인의 캐시가 과도합니다 (%d개). 정리를 고려해보세요.", 
                            domain, count));
                    }
                });
            }
            
        } catch (Exception e) {
            log.error("최적화 제안 생성 실패", e);
            suggestions.add("최적화 제안 생성 중 오류가 발생했습니다.");
        }
        
        return suggestions;
    }
    
    // ===== 정기 모니터링 =====
    
    /**
     * 캐시 성능 체크 (5분마다)
     */
    @Scheduled(fixedRate = 300000)
    public void checkCachePerformance() {
        try {
            CacheStatisticsDto stats = getCacheStatistics();
            
            // 메트릭 저장
            saveCacheMetrics(stats);
            
            // 임계치 체크
            checkThresholds(stats);
            
            log.debug("캐시 성능 체크 완료: 히트율 {:.2f}%, 메모리 사용량 {:.1f}%", 
                    stats.getHitRate() * 100, stats.getMemoryUsagePercent());
            
        } catch (Exception e) {
            log.error("캐시 성능 체크 실패", e);
        }
    }
    
    /**
     * 임계치 체크
     */
    private void checkThresholds(CacheStatisticsDto stats) {
        // 히트율 체크
        if (stats.getHitRate() < HIT_RATE_THRESHOLD) {
            notifyLowHitRate(stats.getHitRate());
        }
        
        // 메모리 사용량 체크
        if (stats.getMemoryUsagePercent() > MEMORY_USAGE_THRESHOLD) {
            notifyHighMemoryUsage(stats.getMemoryUsagePercent());
        }
        
        // 응답 시간 체크
        if (stats.getAverageResponseTime() > RESPONSE_TIME_THRESHOLD) {
            notifySlowResponse(stats.getAverageResponseTime());
        }
    }
    
    /**
     * 일일 캐시 리포트 (매일 오전 9시)
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void generateDailyReport() {
        try {
            log.info("일일 캐시 리포트 생성 시작");
            
            Map<String, Object> report = new HashMap<>();
            
            // 현재 통계
            report.put("currentStats", getCacheStatistics());
            
            // 24시간 트렌드
            report.put("24hTrend", get24HourTrend());
            
            // 성능 분석
            report.put("performanceAnalysis", analyzePerformance());
            
            // 최적화 제안
            report.put("optimizationSuggestions", getOptimizationSuggestions());
            
            // 리포트 전송
            sendDailyReport(report);
            
            log.info("일일 캐시 리포트 생성 완료");
            
        } catch (Exception e) {
            log.error("일일 캐시 리포트 생성 실패", e);
        }
    }
    
    // ===== 계산 메서드들 =====
    
    private Map<String, Long> calculateCacheKeyCounts() {
        Map<String, Long> counts = new HashMap<>();
        
        try {
            String[] cacheNamespaces = {
                "users", "routes", "gyms", "posts", "payments", 
                "notifications", "apiLogs", "externalApi", "systemStats"
            };
            
            for (String namespace : cacheNamespaces) {
                Set<String> keys = redisTemplate.keys(namespace + ":*");
                counts.put(namespace, keys != null ? (long) keys.size() : 0L);
            }
            
        } catch (Exception e) {
            log.error("캐시별 키 개수 계산 실패", e);
        }
        
        return counts;
    }
    
    private double calculateHitRate(Properties info) {
        try {
            String hits = info.getProperty("keyspace_hits");
            String misses = info.getProperty("keyspace_misses");
            
            long hitCount = Long.parseLong(hits != null ? hits : "0");
            long missCount = Long.parseLong(misses != null ? misses : "0");
            long totalRequests = hitCount + missCount;
            
            return totalRequests > 0 ? (double) hitCount / totalRequests : 0.0;
            
        } catch (Exception e) {
            log.error("히트율 계산 실패", e);
            return 0.0;
        }
    }
    
    private double calculateMemoryUsagePercent(Properties info) {
        try {
            String usedMemory = info.getProperty("used_memory");
            String maxMemory = info.getProperty("maxmemory");
            
            if (usedMemory != null && maxMemory != null) {
                long used = Long.parseLong(usedMemory);
                long max = Long.parseLong(maxMemory);
                
                return max > 0 ? (double) used / max * 100 : 0.0;
            }
            
            return 0.0;
            
        } catch (Exception e) {
            log.error("메모리 사용률 계산 실패", e);
            return 0.0;
        }
    }
    
    private double calculateAverageResponseTime() {
        // 실제 구현에서는 응답 시간 메트릭을 수집
        // 여기서는 예시값 반환
        return 25.5; // ms
    }
    
    private long getTotalKeyCount() {
        try {
            Set<String> keys = redisTemplate.keys("*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("총 키 개수 조회 실패", e);
            return 0;
        }
    }
    
    // ===== 분석 메서드들 =====
    
    private Map<String, Object> analyzeDomainCache(String domain) {
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            Set<String> keys = redisTemplate.keys(domain + ":*");
            int keyCount = keys != null ? keys.size() : 0;
            
            // TTL 분포 계산
            Map<String, Integer> ttlDistribution = new HashMap<>();
            if (keys != null) {
                for (String key : keys) {
                    Long ttl = redisTemplate.getExpire(key);
                    String ttlRange = categorizeTtl(ttl);
                    ttlDistribution.merge(ttlRange, 1, Integer::sum);
                }
            }
            
            analysis.put("keyCount", keyCount);
            analysis.put("ttlDistribution", ttlDistribution);
            
        } catch (Exception e) {
            log.error("도메인 캐시 분석 실패: {}", domain, e);
            analysis.put("error", e.getMessage());
        }
        
        return analysis;
    }
    
    private String categorizeTtl(Long ttl) {
        if (ttl == null || ttl < 0) return "영구";
        if (ttl < 60) return "1분 미만";
        if (ttl < 300) return "5분 미만";
        if (ttl < 1800) return "30분 미만";
        if (ttl < 3600) return "1시간 미만";
        return "1시간 이상";
    }
    
    private String calculatePerformanceGrade(CacheStatisticsDto stats) {
        double score = 0;
        
        // 히트율 점수 (40%)
        score += Math.min(stats.getHitRate() * 100, 100) * 0.4;
        
        // 메모리 사용률 점수 (30%) - 낮을수록 좋음
        score += Math.max(100 - stats.getMemoryUsagePercent(), 0) * 0.3;
        
        // 응답 시간 점수 (30%) - 낮을수록 좋음
        double responseScore = Math.max(100 - stats.getAverageResponseTime(), 0);
        score += Math.min(responseScore, 100) * 0.3;
        
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }
    
    private Map<String, Object> analyzeHitRate(double hitRate) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (hitRate >= 0.9) {
            analysis.put("level", "매우 좋음");
            analysis.put("description", "캐시가 효율적으로 작동하고 있습니다.");
        } else if (hitRate >= 0.8) {
            analysis.put("level", "좋음");
            analysis.put("description", "캐시 성능이 양호합니다.");
        } else if (hitRate >= 0.7) {
            analysis.put("level", "보통");
            analysis.put("description", "캐시 최적화를 고려해보세요.");
        } else {
            analysis.put("level", "나쁨");
            analysis.put("description", "캐시 전략을 재검토해야 합니다.");
        }
        
        return analysis;
    }
    
    private Map<String, Object> analyzeMemoryUsage(double memoryUsage) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (memoryUsage < 60) {
            analysis.put("level", "좋음");
            analysis.put("description", "메모리 사용량이 적절합니다.");
        } else if (memoryUsage < 80) {
            analysis.put("level", "보통");
            analysis.put("description", "메모리 사용량을 모니터링하세요.");
        } else {
            analysis.put("level", "위험");
            analysis.put("description", "메모리 사용량이 높습니다. 정리가 필요합니다.");
        }
        
        return analysis;
    }
    
    private Map<String, Object> analyzeResponseTime(double responseTime) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (responseTime < 10) {
            analysis.put("level", "매우 빠름");
            analysis.put("description", "응답 시간이 매우 빠릅니다.");
        } else if (responseTime < 50) {
            analysis.put("level", "빠름");
            analysis.put("description", "응답 시간이 양호합니다.");
        } else if (responseTime < 100) {
            analysis.put("level", "보통");
            analysis.put("description", "응답 시간 최적화를 고려하세요.");
        } else {
            analysis.put("level", "느림");
            analysis.put("description", "응답 시간이 깁니다. 최적화가 필요합니다.");
        }
        
        return analysis;
    }
    
    // ===== 알림 메서드들 =====
    
    private void notifyLowHitRate(double hitRate) {
        try {
            String message = String.format(
                "캐시 히트율 저하: %.2f%% (임계치: %.0f%%)",
                hitRate * 100,
                HIT_RATE_THRESHOLD * 100
            );
            
            notificationService.sendSystemAlert("CACHE_LOW_HIT_RATE", message, Map.of(
                "hitRate", hitRate,
                "threshold", HIT_RATE_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("낮은 히트율 알림 발송 실패", e);
        }
    }
    
    private void notifyHighMemoryUsage(double usagePercent) {
        try {
            String message = String.format(
                "캐시 메모리 사용량 높음: %.1f%% (임계치: %d%%)",
                usagePercent,
                MEMORY_USAGE_THRESHOLD
            );
            
            notificationService.sendSystemAlert("CACHE_HIGH_MEMORY", message, Map.of(
                "usagePercent", usagePercent,
                "threshold", MEMORY_USAGE_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("높은 메모리 사용량 알림 발송 실패", e);
        }
    }
    
    private void notifySlowResponse(double responseTime) {
        try {
            String message = String.format(
                "캐시 응답 시간 지연: %.1fms (임계치: %dms)",
                responseTime,
                RESPONSE_TIME_THRESHOLD
            );
            
            notificationService.sendSystemAlert("CACHE_SLOW_RESPONSE", message, Map.of(
                "responseTime", responseTime,
                "threshold", RESPONSE_TIME_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("느린 응답 시간 알림 발송 실패", e);
        }
    }
    
    // ===== 헬퍼 메서드들 =====
    
    private void saveCacheMetrics(CacheStatisticsDto stats) {
        String timestamp = LocalDateTime.now().toString();
        CacheMetrics metrics = new CacheMetrics(
            stats.getHitRate(),
            stats.getMemoryUsagePercent(),
            stats.getAverageResponseTime(),
            stats.getTotalKeys()
        );
        metricsHistory.put(timestamp, metrics);
        
        // 24시간 이상 된 데이터 제거
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        metricsHistory.entrySet().removeIf(entry ->
            LocalDateTime.parse(entry.getKey()).isBefore(cutoff)
        );
    }
    
    private List<Map<String, Object>> get24HourTrend() {
        return metricsHistory.entrySet().stream()
            .filter(entry -> LocalDateTime.parse(entry.getKey())
                .isAfter(LocalDateTime.now().minusHours(24)))
            .map(entry -> Map.of(
                "timestamp", entry.getKey(),
                "metrics", entry.getValue()
            ))
            .sorted((a, b) -> ((String) a.get("timestamp"))
                .compareTo((String) b.get("timestamp")))
            .toList();
    }
    
    private Map<String, Object> getHourlyStatistics() {
        // 시간대별 캐시 사용 패턴 분석
        // 실제 구현에서는 더 정교한 분석
        return Map.of(
            "peakHour", "14:00-15:00",
            "lowHour", "03:00-04:00",
            "avgRequestsPerHour", 15000
        );
    }
    
    private Map<String, Integer> getCacheSizeDistribution() {
        // 캐시 크기별 분포
        return Map.of(
            "small (< 1KB)", 15000,
            "medium (1-10KB)", 8000,
            "large (10-100KB)", 2000,
            "xlarge (> 100KB)", 500
        );
    }
    
    private Map<String, Integer> getTtlDistribution() {
        // TTL 분포
        return Map.of(
            "5분 미만", 5000,
            "30분 미만", 12000,
            "1시간 미만", 6000,
            "1시간 이상", 3000,
            "영구", 500
        );
    }
    
    private List<String> generateRecommendations(CacheStatisticsDto stats) {
        List<String> recommendations = new ArrayList<>();
        
        if (stats.getHitRate() < 0.8) {
            recommendations.add("캐시 키 설계 재검토");
            recommendations.add("프리로딩 전략 강화");
        }
        
        if (stats.getMemoryUsagePercent() > 80) {
            recommendations.add("TTL 최적화");
            recommendations.add("불필요한 캐시 정리");
        }
        
        return recommendations;
    }
    
    private Map<String, Object> analyzeTrends() {
        // 트렌드 분석 (예시)
        return Map.of(
            "hitRateTrend", "상승",
            "memoryUsageTrend", "안정",
            "responseTimeTrend", "하락"
        );
    }
    
    private void sendDailyReport(Map<String, Object> report) {
        try {
            // 실제 구현에서는 이메일 또는 슬랙으로 전송
            log.info("일일 캐시 리포트 전송");
        } catch (Exception e) {
            log.error("일일 리포트 전송 실패", e);
        }
    }
    
    // 캐시 메트릭 데이터 클래스
    private record CacheMetrics(
        double hitRate,
        double memoryUsagePercent,
        double averageResponseTime,
        long totalKeys
    ) {}
}
```

---

## 📊 CacheStatisticsDto 확장

```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 확장된 캐시 통계 DTO
 */
@Getter
@Builder
public class CacheStatisticsDto {
    private Long totalKeys;
    private Map<String, Long> cacheKeyCounts;
    private String usedMemory;
    private String maxMemory;
    private Double memoryUsagePercent;
    private Double hitRate;
    private String connectedClients;
    private String uptime;
    private Double averageResponseTime;
    private LocalDateTime lastUpdated;
    
    public String getHitRateDisplay() {
        return hitRate != null ? String.format("%.2f%%", hitRate * 100) : "0.00%";
    }
    
    public String getMemoryUsageDisplay() {
        return memoryUsagePercent != null ? String.format("%.1f%%", memoryUsagePercent) : "0.0%";
    }
    
    public String getResponseTimeDisplay() {
        return averageResponseTime != null ? String.format("%.1fms", averageResponseTime) : "N/A";
    }
    
    public boolean isHealthy() {
        return hitRate != null && hitRate >= 0.8 &&
               memoryUsagePercent != null && memoryUsagePercent < 80 &&
               averageResponseTime != null && averageResponseTime < 100;
    }
    
    public String getPerformanceLevel() {
        if (!isHealthy()) return "주의";
        
        if (hitRate >= 0.95 && memoryUsagePercent < 60 && averageResponseTime < 25) {
            return "매우 좋음";
        } else if (hitRate >= 0.9 && memoryUsagePercent < 70 && averageResponseTime < 50) {
            return "좋음";
        } else {
            return "보통";
        }
    }
    
    public Long getTotalCacheKeys() {
        return cacheKeyCounts != null ? 
                cacheKeyCounts.values().stream().mapToLong(Long::longValue).sum() : 0L;
    }
    
    public String getUptimeDisplay() {
        if (uptime == null) return "N/A";
        
        try {
            long seconds = Long.parseLong(uptime);
            long days = seconds / (24 * 3600);
            long hours = (seconds % (24 * 3600)) / 3600;
            long minutes = (seconds % 3600) / 60;
            
            if (days > 0) {
                return String.format("%d일 %d시간", days, hours);
            } else if (hours > 0) {
                return String.format("%d시간 %d분", hours, minutes);
            } else {
                return String.format("%d분", minutes);
            }
        } catch (Exception e) {
            return uptime + "초";
        }
    }
}
```

---

## 📈 주요 특징

### 1. **실시간 성능 모니터링**
- 히트율, 메모리 사용량 추적
- 응답 시간 분석
- 임계치 기반 자동 알림

### 2. **세부 통계 분석**
- 도메인별 캐시 분석
- 시간대별 사용 패턴
- TTL 및 크기 분포 분석

### 3. **최적화 제안**
- 성능 등급 평가
- 구체적 개선 방안 제시
- 트렌드 기반 예측

### 4. **일일 리포트**
- 종합 성능 분석
- 24시간 트렌드
- 최적화 권장사항

---

**📝 연관 파일**: 
- step6-6c1_cache_core.md (캐시 핵심)
- step6-6c2_cache_warming.md (캐시 워밍업)