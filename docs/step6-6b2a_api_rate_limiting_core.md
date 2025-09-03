# 🌐 Step 6-6b2a: API Rate Limiting 핵심 기능

> API 호출 제한 관리 및 사용량 추적 시스템  
> 생성일: 2025-09-01  
> 분할 기준: Rate Limiting 핵심 로직

---

## 🎯 설계 목표

- **Rate Limiting**: API 호출 제한 관리 및 사용량 추적
- **실시간 추적**: Redis 기반 분산 카운터
- **사용량 모니터링**: 실시간 API 사용량 추적
- **자동 알림**: 임계치 기반 알림 시스템

---

## ✅ ApiRateLimitingService.java (핵심 기능)

```java
package com.routepick.service.system;

import com.routepick.dto.system.ApiUsageDto;
import com.routepick.dto.system.RateLimitStatusDto;
import com.routepick.exception.system.ApiRateLimitException;
import com.routepick.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * API Rate Limiting 및 사용량 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiRateLimitingService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationService notificationService;
    
    // Rate Limiting 설정
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final String USAGE_KEY_PREFIX = "api_usage:";
    private static final double USAGE_WARNING_THRESHOLD = 0.8; // 80%
    private static final double USAGE_CRITICAL_THRESHOLD = 0.95; // 95%
    
    // ===== Rate Limiting 핵심 기능 =====
    
    /**
     * API 호출 허용 여부 체크
     */
    public boolean isRequestAllowed(Long configId, String identifier) {
        try {
            String key = buildRateLimitKey(configId, identifier);
            
            // 현재 카운트 조회
            Integer currentCount = (Integer) redisTemplate.opsForValue().get(key);
            
            if (currentCount == null) {
                // 첫 요청인 경우
                redisTemplate.opsForValue().set(key, 1, 1, TimeUnit.HOURS);
                recordApiUsage(configId, identifier, true);
                return true;
            }
            
            // Rate Limit 조회
            int maxRequests = getMaxRequestsPerHour(configId);
            
            if (currentCount >= maxRequests) {
                log.warn("Rate limit 초과: configId={}, identifier={}, count={}/{}", 
                        configId, identifier, currentCount, maxRequests);
                recordApiUsage(configId, identifier, false);
                return false;
            }
            
            // 카운트 증가
            redisTemplate.opsForValue().increment(key);
            recordApiUsage(configId, identifier, true);
            
            return true;
            
        } catch (Exception e) {
            log.error("Rate limit 체크 실패: configId={}, identifier={}", configId, identifier, e);
            // 오류 시 허용 (fail-open)
            return true;
        }
    }
    
    /**
     * 남은 허용 요청 수 조회
     */
    public int getRemainingRequests(Long configId, String identifier) {
        try {
            String key = buildRateLimitKey(configId, identifier);
            Integer currentCount = (Integer) redisTemplate.opsForValue().get(key);
            
            if (currentCount == null) {
                return getMaxRequestsPerHour(configId);
            }
            
            int maxRequests = getMaxRequestsPerHour(configId);
            return Math.max(0, maxRequests - currentCount);
            
        } catch (Exception e) {
            log.error("남은 요청 수 조회 실패: configId={}", configId, e);
            return 0;
        }
    }
    
    /**
     * Rate Limit 리셋 시간 조회
     */
    public LocalDateTime getRateLimitResetTime(Long configId, String identifier) {
        try {
            String key = buildRateLimitKey(configId, identifier);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            
            if (ttl != null && ttl > 0) {
                return LocalDateTime.now().plusSeconds(ttl);
            }
            
            return LocalDateTime.now().plusHours(1);
            
        } catch (Exception e) {
            log.error("Rate limit 리셋 시간 조회 실패: configId={}", configId, e);
            return LocalDateTime.now().plusHours(1);
        }
    }
    
    /**
     * Rate Limit 수동 리셋
     */
    public void resetRateLimit(Long configId, String identifier) {
        try {
            String key = buildRateLimitKey(configId, identifier);
            redisTemplate.delete(key);
            
            log.info("Rate limit 리셋: configId={}, identifier={}", configId, identifier);
            
        } catch (Exception e) {
            log.error("Rate limit 리셋 실패: configId={}, identifier={}", configId, identifier, e);
        }
    }
    
    // ===== 사용량 추적 =====
    
    /**
     * API 사용량 기록
     */
    private void recordApiUsage(Long configId, String identifier, boolean success) {
        try {
            String dailyKey = buildUsageKey(configId, "daily");
            String hourlyKey = buildUsageKey(configId, "hourly");
            
            // 일일 사용량 증가
            redisTemplate.opsForValue().increment(dailyKey);
            redisTemplate.expire(dailyKey, 24, TimeUnit.HOURS);
            
            // 시간당 사용량 증가
            redisTemplate.opsForValue().increment(hourlyKey);
            redisTemplate.expire(hourlyKey, 1, TimeUnit.HOURS);
            
            if (success) {
                redisTemplate.opsForValue().increment(dailyKey + ":success");
                redisTemplate.expire(dailyKey + ":success", 24, TimeUnit.HOURS);
            } else {
                redisTemplate.opsForValue().increment(dailyKey + ":failed");
                redisTemplate.expire(dailyKey + ":failed", 24, TimeUnit.HOURS);
            }
            
        } catch (Exception e) {
            log.error("API 사용량 기록 실패: configId={}", configId, e);
        }
    }
    
    /**
     * API 사용량 통계 조회
     */
    public ApiUsageDto getApiUsageStats(Long configId) {
        try {
            String dailyKey = buildUsageKey(configId, "daily");
            String hourlyKey = buildUsageKey(configId, "hourly");
            
            Integer dailyUsage = (Integer) redisTemplate.opsForValue().get(dailyKey);
            Integer hourlyUsage = (Integer) redisTemplate.opsForValue().get(hourlyKey);
            Integer dailySuccess = (Integer) redisTemplate.opsForValue().get(dailyKey + ":success");
            Integer dailyFailed = (Integer) redisTemplate.opsForValue().get(dailyKey + ":failed");
            
            int maxDailyRequests = getMaxRequestsPerDay(configId);
            double usagePercent = dailyUsage != null ? 
                    (double) dailyUsage / maxDailyRequests * 100 : 0.0;
            
            return ApiUsageDto.builder()
                    .configId(configId)
                    .dailyUsage(dailyUsage != null ? dailyUsage : 0)
                    .hourlyUsage(hourlyUsage != null ? hourlyUsage : 0)
                    .maxDailyRequests(maxDailyRequests)
                    .usagePercent(usagePercent)
                    .successCount(dailySuccess != null ? dailySuccess : 0)
                    .failedCount(dailyFailed != null ? dailyFailed : 0)
                    .lastUpdated(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            log.error("API 사용량 통계 조회 실패: configId={}", configId, e);
            return ApiUsageDto.builder()
                    .configId(configId)
                    .dailyUsage(0)
                    .hourlyUsage(0)
                    .lastUpdated(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * 전체 API 사용량 조회
     */
    public List<ApiUsageDto> getAllApiUsageStats() {
        List<ApiUsageDto> usageList = new ArrayList<>();
        
        try {
            // 활성화된 모든 API Config ID 조회 (실제 구현에서는 Repository에서 조회)
            List<Long> configIds = getActiveConfigIds();
            
            for (Long configId : configIds) {
                usageList.add(getApiUsageStats(configId));
            }
            
        } catch (Exception e) {
            log.error("전체 API 사용량 조회 실패", e);
        }
        
        return usageList;
    }
    
    // ===== 정기 모니터링 =====
    
    /**
     * 사용량 모니터링 (10분마다)
     */
    @Scheduled(fixedRate = 600000)
    public void monitorUsage() {
        try {
            List<ApiUsageDto> usageList = getAllApiUsageStats();
            
            for (ApiUsageDto usage : usageList) {
                double usagePercent = usage.getUsagePercent() / 100.0;
                
                if (usagePercent >= USAGE_CRITICAL_THRESHOLD) {
                    notifyHighUsage(usage, "CRITICAL");
                } else if (usagePercent >= USAGE_WARNING_THRESHOLD) {
                    notifyHighUsage(usage, "WARNING");
                }
            }
            
            log.debug("사용량 모니터링 완료: {}개 API", usageList.size());
            
        } catch (Exception e) {
            log.error("사용량 모니터링 실패", e);
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    private String buildRateLimitKey(Long configId, String identifier) {
        String hour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        return String.format("%s%d:%s:%s", RATE_LIMIT_KEY_PREFIX, configId, identifier, hour);
    }
    
    private String buildUsageKey(Long configId, String period) {
        String timeKey = switch (period) {
            case "daily" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            case "hourly" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
            default -> "total";
        };
        return String.format("%s%d:%s:%s", USAGE_KEY_PREFIX, configId, period, timeKey);
    }
    
    private int getMaxRequestsPerHour(Long configId) {
        // 실제 구현에서는 API Config에서 조회
        return 100; // 기본값
    }
    
    private int getMaxRequestsPerDay(Long configId) {
        // 실제 구현에서는 API Config에서 조회
        return 1000; // 기본값
    }
    
    private List<Long> getActiveConfigIds() {
        // 실제 구현에서는 Repository에서 조회
        return Arrays.asList(1L, 2L, 3L); // 예시
    }
    
    // ===== 알림 메서드 =====
    
    private void notifyHighUsage(ApiUsageDto usage, String level) {
        try {
            String message = String.format(
                "API 사용량 %s: ConfigId=%d, 사용률=%.1f%% (%d/%d)",
                level, usage.getConfigId(), usage.getUsagePercent(),
                usage.getDailyUsage(), usage.getMaxDailyRequests()
            );
            
            notificationService.sendSystemAlert("API_HIGH_USAGE", message, Map.of(
                "configId", usage.getConfigId(),
                "usagePercent", usage.getUsagePercent(),
                "level", level
            ));
            
        } catch (Exception e) {
            log.error("높은 사용량 알림 발송 실패: configId={}", usage.getConfigId(), e);
        }
    }
}
```

---

## 📈 핵심 기능 특징

### 1. **Rate Limiting 관리**
- **시간당 제한**: Redis 키를 시간별로 분리
- **분산 카운터**: Redis increment로 원자적 증가
- **자동 만료**: TTL로 자동 정리

### 2. **사용량 추적**
- **실시간 집계**: 시간/일별 사용량 분리 추적
- **성공/실패 분리**: 정확한 통계 제공
- **자동 정리**: TTL로 오래된 데이터 자동 삭제

### 3. **모니터링**
- **주기적 체크**: 10분마다 사용량 모니터링
- **임계치 알림**: WARNING(80%), CRITICAL(95%)
- **실시간 대시보드**: 사용량 현황 실시간 조회

### 4. **Fail-Safe 설계**
- **Fail-Open**: 오류 시 요청 허용
- **에러 핸들링**: 모든 Redis 오류 처리
- **복구 기능**: 수동 리셋 기능 제공

---

**📝 연관 파일**: 
- step6-6b2b_circuit_breaker_backoff.md (Circuit Breaker & 백오프)
- step6-6b1_external_api_core.md (API 핵심)
- step6-6b3_api_monitoring.md (모니터링)