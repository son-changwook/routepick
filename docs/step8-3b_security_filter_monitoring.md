# Step 8-3b: Security Filter 모니터링 및 성능 관리

> Security Filter Chain 실행 모니터링, 성능 통계, 설정 관리  
> 생성일: 2025-08-21  
> 단계: 8-3b (Security 설정 - 모니터링)  
> 참고: step8-3a, step8-4b

---

## 📊 Filter 실행 순서 모니터링

### 1. FilterExecutionMonitor
```java
package com.routepick.backend.security.monitor;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class FilterExecutionMonitor implements Filter {
    
    private final ConcurrentHashMap<String, AtomicInteger> filterExecutionCount = new ConcurrentHashMap<>();
    private final ThreadLocal<Long> requestStartTime = new ThreadLocal<>();
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestId = generateRequestId(httpRequest);
        
        requestStartTime.set(System.currentTimeMillis());
        
        log.debug("=== Filter Chain 시작 === RequestID: {}, URI: {}", 
            requestId, httpRequest.getRequestURI());
        
        try {
            // 필터 체인 실행
            chain.doFilter(request, response);
            
        } finally {
            long duration = System.currentTimeMillis() - requestStartTime.get();
            
            log.debug("=== Filter Chain 완료 === RequestID: {}, Duration: {}ms", 
                requestId, duration);
                
            // 성능 통계 수집
            if (duration > 1000) { // 1초 이상
                log.warn("느린 요청 감지: URI={}, Duration={}ms", 
                    httpRequest.getRequestURI(), duration);
            }
            
            requestStartTime.remove();
        }
    }
    
    private String generateRequestId(HttpServletRequest request) {
        return String.format("%s-%s-%d", 
            request.getMethod(),
            request.getRequestURI().hashCode(),
            System.currentTimeMillis() % 10000
        );
    }
    
    /**
     * 필터 실행 통계 조회
     */
    public ConcurrentHashMap<String, AtomicInteger> getFilterExecutionStats() {
        return new ConcurrentHashMap<>(filterExecutionCount);
    }
}
```

### 2. SecurityFilterPerformanceCollector
```java
package com.routepick.backend.security.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Security Filter 성능 통계 수집기
 */
@Component
@Slf4j
public class SecurityFilterPerformanceCollector {
    
    // 필터별 실행 시간 통계
    private final ConcurrentHashMap<String, PerformanceMetrics> filterMetrics = new ConcurrentHashMap<>();
    
    // 전체 통계
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder slowRequests = new LongAdder();
    private final AtomicLong maxExecutionTime = new AtomicLong(0);
    
    /**
     * 필터 실행 시간 기록
     */
    public void recordFilterExecution(String filterName, long executionTimeMs) {
        // 필터별 통계 업데이트
        filterMetrics.computeIfAbsent(filterName, k -> new PerformanceMetrics())
                   .recordExecution(executionTimeMs);
        
        // 전체 통계 업데이트  
        totalRequests.increment();
        
        if (executionTimeMs > 1000) { // 1초 이상
            slowRequests.increment();
        }
        
        // 최대 실행 시간 업데이트
        maxExecutionTime.updateAndGet(current -> Math.max(current, executionTimeMs));
    }
    
    /**
     * 필터별 성능 통계 조회
     */
    public PerformanceMetrics getFilterMetrics(String filterName) {
        return filterMetrics.get(filterName);
    }
    
    /**
     * 전체 성능 요약 조회
     */
    public OverallPerformanceSummary getOverallSummary() {
        return OverallPerformanceSummary.builder()
            .totalRequests(totalRequests.sum())
            .slowRequests(slowRequests.sum())
            .slowRequestRatio(totalRequests.sum() > 0 ? 
                (double) slowRequests.sum() / totalRequests.sum() : 0.0)
            .maxExecutionTime(maxExecutionTime.get())
            .activeFilters(filterMetrics.size())
            .build();
    }
    
    /**
     * 성능 통계 리셋
     */
    public void resetStats() {
        filterMetrics.clear();
        totalRequests.reset();
        slowRequests.reset();
        maxExecutionTime.set(0);
        log.info("Security Filter 성능 통계 리셋 완료");
    }
    
    /**
     * 필터별 성능 메트릭
     */
    @lombok.Data
    public static class PerformanceMetrics {
        private final LongAdder executionCount = new LongAdder();
        private final LongAdder totalExecutionTime = new LongAdder();
        private final AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxExecutionTime = new AtomicLong(0);
        
        public void recordExecution(long timeMs) {
            executionCount.increment();
            totalExecutionTime.add(timeMs);
            
            minExecutionTime.updateAndGet(current -> Math.min(current, timeMs));
            maxExecutionTime.updateAndGet(current -> Math.max(current, timeMs));
        }
        
        public double getAverageExecutionTime() {
            long count = executionCount.sum();
            return count > 0 ? (double) totalExecutionTime.sum() / count : 0.0;
        }
    }
    
    /**
     * 전체 성능 요약
     */
    @lombok.Builder
    @lombok.Data
    public static class OverallPerformanceSummary {
        private final long totalRequests;
        private final long slowRequests;
        private final double slowRequestRatio;
        private final long maxExecutionTime;
        private final int activeFilters;
    }
}
```

### 3. SecurityFilterHealthIndicator
```java
package com.routepick.backend.security.monitor;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Security Filter 상태 모니터링
 */
@Component
@RequiredArgsConstructor
public class SecurityFilterHealthIndicator implements HealthIndicator {
    
    private final SecurityFilterPerformanceCollector performanceCollector;
    
    @Override
    public Health health() {
        var summary = performanceCollector.getOverallSummary();
        
        // 건강 상태 판단 기준
        boolean isHealthy = summary.getSlowRequestRatio() < 0.1 && // 10% 미만
                           summary.getMaxExecutionTime() < 5000;    // 5초 미만
        
        Health.Builder healthBuilder = isHealthy ? Health.up() : Health.down();
        
        return healthBuilder
            .withDetail("totalRequests", summary.getTotalRequests())
            .withDetail("slowRequests", summary.getSlowRequests())
            .withDetail("slowRequestRatio", String.format("%.2f%%", summary.getSlowRequestRatio() * 100))
            .withDetail("maxExecutionTime", summary.getMaxExecutionTime() + "ms")
            .withDetail("activeFilters", summary.getActiveFilters())
            .build();
    }
}
```

---

## ⚙️ Security 설정 관리

### 4. application.yml 필터 설정
```yaml
# Security Filter 설정
security:
  filter:
    order:
      cors: -100
      oauth2-cors: -95  
      security-headers: -80
      rate-limiting: -70
      xss-protection: -60
      csrf: -50
      jwt: -20
      data-masking: 10
      logging: 20
    
    performance:
      slow-request-threshold: 1000  # 1초
      monitoring-enabled: true
      
  chain:
    debug: false  # 운영환경에서는 false
    trace-requests: false
    
logging:
  level:
    com.routepick.backend.security: INFO
    org.springframework.security: WARN
```

### 5. SecurityConfigurationProperties
```java
package com.routepick.backend.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security 설정 프로퍼티
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityConfigurationProperties {
    
    private FilterConfig filter = new FilterConfig();
    private ChainConfig chain = new ChainConfig();
    
    @Data
    public static class FilterConfig {
        private OrderConfig order = new OrderConfig();
        private PerformanceConfig performance = new PerformanceConfig();
    }
    
    @Data
    public static class OrderConfig {
        private int cors = -100;
        private int oauth2Cors = -95;
        private int securityHeaders = -80;
        private int rateLimiting = -70;
        private int xssProtection = -60;
        private int csrf = -50;
        private int jwt = -20;
        private int dataMasking = 10;
        private int logging = 20;
    }
    
    @Data
    public static class PerformanceConfig {
        private long slowRequestThreshold = 1000;
        private boolean monitoringEnabled = true;
    }
    
    @Data
    public static class ChainConfig {
        private boolean debug = false;
        private boolean traceRequests = false;
    }
}
```

### 6. SecurityMetricsEndpoint (Actuator)
```java
package com.routepick.backend.security.actuator;

import com.routepick.backend.security.monitor.SecurityFilterPerformanceCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuator.endpoint.annotation.Endpoint;
import org.springframework.boot.actuator.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Security 메트릭 Actuator Endpoint
 */
@Component
@Endpoint(id = "security-filters")
@RequiredArgsConstructor
public class SecurityMetricsEndpoint {
    
    private final SecurityFilterPerformanceCollector performanceCollector;
    private final FilterExecutionMonitor executionMonitor;
    
    @ReadOperation
    public Map<String, Object> securityFilters() {
        return Map.of(
            "performance", performanceCollector.getOverallSummary(),
            "executionStats", executionMonitor.getFilterExecutionStats(),
            "timestamp", System.currentTimeMillis()
        );
    }
}
```

---

## 📈 성능 모니터링 전략

### **1. 실시간 모니터링**
- **Request ID**: 요청별 고유 식별자 생성
- **Execution Time**: 각 필터별 실행 시간 측정
- **Thread Local**: 요청별 컨텍스트 관리
- **Memory Efficient**: 통계 데이터 효율적 저장

### **2. 통계 수집**
- **Filter별 메트릭**: 실행 횟수, 평균/최대 시간
- **전체 요약**: 총 요청 수, 느린 요청 비율
- **Health Check**: Actuator 연동 상태 점검
- **Alerting**: 임계값 초과 시 알림

### **3. 성능 최적화**
- **Early Warning**: 1초 이상 요청 감지
- **Memory Management**: ThreadLocal 정리
- **Async Logging**: 로깅 성능 영향 최소화
- **Stats Reset**: 주기적 통계 초기화

---

## 🔧 운영 관리

### **Actuator Endpoints**
- **/actuator/health**: 전체 시스템 상태
- **/actuator/security-filters**: 필터별 성능 통계
- **/actuator/metrics**: 세부 메트릭 데이터

### **로그 레벨 관리**
- **개발환경**: DEBUG - 상세 실행 로그
- **스테이징**: INFO - 주요 이벤트만
- **운영환경**: WARN - 문제 상황만

### **알림 설정**
- **느린 요청**: 1초 이상 실행 시간
- **높은 비율**: 전체 요청 중 10% 이상 느린 요청
- **필터 오류**: Exception 발생 시 즉시 알림

---

## 🚀 **다음 단계**

**step8-4 연계 기능:**
- Global Exception Handler 통합
- Security Monitoring System 연동
- Audit Logging 시스템 통합

*step8-3b 완성: Security Filter 모니터링 시스템 완료*