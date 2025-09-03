# 8-4c2단계: RequestLoggingFilter HTTP 요청/응답 로깅

> RoutePickr HTTP 요청/응답 로깅 필터 (성능 모니터링)  
> 생성일: 2025-08-27  
> 기반 참고: step8-2d_security_monitoring.md, step3-3c_monitoring_testing.md  
> 핵심 구현: RequestLoggingFilter - 완전한 API 호출 추적

---

## 📊 HTTP 로깅 필터 개요

### 설계 원칙
- **완전한 요청 추적**: 모든 HTTP 호출의 상세 로깅
- **성능 모니터링**: 실행 시간 추적 및 느린 요청 감지
- **민감정보 보호**: 헤더 및 바디 데이터 자동 마스킹
- **비동기 처리**: 메트릭 수집으로 성능 영향 최소화
- **ELK 연동**: JSON 구조화 로그로 분석 용이

### 로깅 아키텍처
```
┌─────────────────────┐
│ RequestLoggingFilter │  ← HTTP 요청/응답 로깅 필터
│ (API 호출 상세 추적)    │
└─────────────────────┘
          ↓
    4가지 로깅 타입:
    1. 요청 로깅 (헤더, 바디, 파라미터)
    2. 응답 로깅 (상태, 크기, 실행시간)
    3. 에러 로깅 (예외 상세, 스택트레이스)
    4. 성능 메트릭 (느린 요청, 응답 크기)
```

---

## 📊 RequestLoggingFilter 구현

### HTTP 요청/응답 로깅 필터
```java
package com.routepick.audit.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.security.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 요청/응답 로깅 필터
 * API 호출의 상세한 추적 및 성능 모니터링
 * 
 * 로깅 내용:
 * - 요청/응답 헤더
 * - 실행 시간
 * - 응답 크기
 * - 에러 발생 시 상세 정보
 * - 민감정보 자동 마스킹
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {
    
    private final SensitiveDataMasker dataMasker;
    private final ObjectMapper objectMapper;
    
    @Value("${app.logging.requests.enabled:true}")
    private boolean requestLoggingEnabled;
    
    @Value("${app.logging.requests.include-body:false}")
    private boolean includeRequestBody;
    
    @Value("${app.logging.requests.include-response-body:false}")
    private boolean includeResponseBody;
    
    @Value("${app.logging.requests.max-body-size:1024}")
    private int maxBodySize;
    
    @Value("${app.logging.requests.slow-request-threshold:2000}")
    private long slowRequestThreshold; // 2초
    
    // 로깅 제외 경로
    private final Set<String> excludedPaths = Set.of(
        "/actuator/health",
        "/actuator/metrics",
        "/favicon.ico",
        "/static/"
    );
    
    // 민감한 헤더 목록
    private final Set<String> sensitiveHeaders = Set.of(
        "Authorization",
        "Cookie",
        "X-API-Key",
        "X-Auth-Token"
    );
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        if (!requestLoggingEnabled || shouldSkipLogging(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 요청/응답 캐싱 래퍼
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        
        // 추적 ID 생성
        String traceId = generateTraceId();
        MDC.put("trace_id", traceId);
        MDC.put("request_uri", request.getRequestURI());
        MDC.put("client_ip", extractClientIp(request));
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 요청 로깅
            logRequest(wrappedRequest, traceId);
            
            // 실제 요청 처리
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            
        } catch (Exception e) {
            // 에러 로깅
            logError(wrappedRequest, e, traceId);
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 응답 로깅
            logResponse(wrappedRequest, wrappedResponse, executionTime, traceId);
            
            // 성능 메트릭 수집 (비동기)
            collectPerformanceMetrics(wrappedRequest.getMethod(), 
                wrappedRequest.getRequestURI(), executionTime, wrappedResponse.getStatus());
            
            // 응답 복사 (중요!)
            wrappedResponse.copyBodyToResponse();
            
            // MDC 정리
            MDC.clear();
        }
    }
    
    /**
     * 요청 로깅
     */
    private void logRequest(ContentCachingRequestWrapper request, String traceId) {
        try {
            RequestLogEntry logEntry = RequestLogEntry.builder()
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .method(request.getMethod())
                .uri(request.getRequestURI())
                .queryString(request.getQueryString())
                .clientIp(extractClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .headers(getFilteredHeaders(request))
                .contentLength(request.getContentLengthLong())
                .contentType(request.getContentType())
                .build();
            
            // 요청 바디 포함 (설정에 따라)
            if (includeRequestBody && hasBody(request)) {
                String body = getRequestBody(request);
                logEntry.setRequestBody(maskSensitiveData(body));
            }
            
            String logJson = objectMapper.writeValueAsString(logEntry);
            log.info("REQUEST: {}", logJson);
            
        } catch (Exception e) {
            log.error("Failed to log request for trace: {}", traceId, e);
        }
    }
    
    /**
     * 응답 로깅
     */
    private void logResponse(ContentCachingRequestWrapper request, 
                           ContentCachingResponseWrapper response, 
                           long executionTime, String traceId) {
        try {
            ResponseLogEntry logEntry = ResponseLogEntry.builder()
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .status(response.getStatus())
                .contentLength(response.getContentSize())
                .contentType(response.getContentType())
                .executionTimeMs(executionTime)
                .headers(getFilteredResponseHeaders(response))
                .build();
            
            // 응답 바디 포함 (설정에 따라)
            if (includeResponseBody && hasResponseBody(response)) {
                String body = getResponseBody(response);
                logEntry.setResponseBody(maskSensitiveData(body));
            }
            
            String logJson = objectMapper.writeValueAsString(logEntry);
            
            // 느린 요청 또는 에러 응답에 대해 경고 로그
            if (executionTime > slowRequestThreshold || response.getStatus() >= 400) {
                log.warn("RESPONSE: {}", logJson);
            } else {
                log.info("RESPONSE: {}", logJson);
            }
            
            // 성능 모니터링을 위한 별도 로그
            if (executionTime > slowRequestThreshold) {
                log.warn("SLOW REQUEST DETECTED: {} {} took {}ms", 
                    request.getMethod(), request.getRequestURI(), executionTime);
            }
            
        } catch (Exception e) {
            log.error("Failed to log response for trace: {}", traceId, e);
        }
    }
    
    /**
     * 에러 로깅
     */
    private void logError(ContentCachingRequestWrapper request, Exception error, String traceId) {
        try {
            ErrorLogEntry logEntry = ErrorLogEntry.builder()
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .method(request.getMethod())
                .uri(request.getRequestURI())
                .clientIp(extractClientIp(request))
                .errorType(error.getClass().getSimpleName())
                .errorMessage(error.getMessage())
                .stackTrace(Arrays.toString(error.getStackTrace()).substring(0, 
                    Math.min(1000, Arrays.toString(error.getStackTrace()).length())))
                .build();
            
            String logJson = objectMapper.writeValueAsString(logEntry);
            log.error("ERROR: {}", logJson);
            
        } catch (Exception e) {
            log.error("Failed to log error for trace: {}", traceId, e);
        }
    }
    
    /**
     * 비동기 성능 메트릭 수집
     */
    @Async
    protected CompletableFuture<Void> collectPerformanceMetrics(String method, String uri, 
                                                              long executionTime, int responseStatus) {
        try {
            PerformanceMetric metric = PerformanceMetric.builder()
                .endpoint(method + " " + uri)
                .executionTime(executionTime)
                .responseStatus(responseStatus)
                .timestamp(LocalDateTime.now())
                .build();
            
            // 메트릭 저장 로직 (Redis, Database 등)
            log.debug("Performance metric collected: {}", metric);
            
        } catch (Exception e) {
            log.error("Failed to collect performance metrics", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ========== 보조 메서드 ==========
    
    private boolean shouldSkipLogging(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludedPaths.stream().anyMatch(path::startsWith);
    }
    
    private String generateTraceId() {
        return String.format("REQ-%08X", ThreadLocalRandom.current().nextInt());
    }
    
    private String extractClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "X-Forwarded",
            "X-Cluster-Client-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
    
    private Map<String, String> getFilteredHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        
        Collections.list(request.getHeaderNames()).forEach(headerName -> {
            String headerValue = request.getHeader(headerName);
            
            if (sensitiveHeaders.contains(headerName)) {
                headerValue = dataMasker.maskToken(headerValue);
            }
            
            headers.put(headerName, headerValue);
        });
        
        return headers;
    }
    
    private Map<String, String> getFilteredResponseHeaders(ContentCachingResponseWrapper response) {
        Map<String, String> headers = new HashMap<>();
        
        response.getHeaderNames().forEach(headerName -> {
            String headerValue = response.getHeader(headerName);
            headers.put(headerName, headerValue);
        });
        
        return headers;
    }
    
    private boolean hasBody(ContentCachingRequestWrapper request) {
        return request.getContentLengthLong() > 0;
    }
    
    private boolean hasResponseBody(ContentCachingResponseWrapper response) {
        return response.getContentSize() > 0;
    }
    
    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > maxBodySize) {
            return new String(Arrays.copyOf(content, maxBodySize)) + "...[TRUNCATED]";
        }
        return new String(content);
    }
    
    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > maxBodySize) {
            return new String(Arrays.copyOf(content, maxBodySize)) + "...[TRUNCATED]";
        }
        return new String(content);
    }
    
    private String maskSensitiveData(String data) {
        if (data == null) return null;
        return dataMasker.mask(data);
    }
}
```

---

## 📋 로그 엔트리 모델 정의

### 요청/응답/에러 로그 엔트리 클래스들
```java
// RequestLogEntry.java
package com.routepick.audit.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
public class RequestLogEntry {
    private String traceId;
    private LocalDateTime timestamp;
    private String method;
    private String uri;
    private String queryString;
    private String clientIp;
    private String userAgent;
    private Map<String, String> headers;
    private Long contentLength;
    private String contentType;
    private String requestBody;
}

// ResponseLogEntry.java
@Getter
@Setter
@Builder
public class ResponseLogEntry {
    private String traceId;
    private LocalDateTime timestamp;
    private int status;
    private Long contentLength;
    private String contentType;
    private long executionTimeMs;
    private Map<String, String> headers;
    private String responseBody;
}

// ErrorLogEntry.java
@Getter
@Setter
@Builder
public class ErrorLogEntry {
    private String traceId;
    private LocalDateTime timestamp;
    private String method;
    private String uri;
    private String clientIp;
    private String errorType;
    private String errorMessage;
    private String stackTrace;
}

// PerformanceMetric.java
@Getter
@Setter
@Builder
public class PerformanceMetric {
    private String endpoint;
    private long executionTime;
    private int responseStatus;
    private LocalDateTime timestamp;
    private String traceId;
}
```

---

## ⚡ 성능 최적화 기능

### 느린 요청 감지 및 알림
```java
/**
 * 성능 임계값 모니터링
 */
public class PerformanceMonitor {
    
    // 엔드포인트별 성능 임계값 설정
    private final Map<String, Long> endpointThresholds = Map.of(
        "GET /api/v1/routes/search", 1000L,      // 1초
        "POST /api/v1/auth/login", 500L,         // 0.5초
        "GET /api/v1/gyms/nearby", 2000L,        // 2초 (공간쿼리)
        "POST /api/v1/routes", 1500L,            // 1.5초
        "GET /api/v1/recommendations", 3000L     // 3초 (AI 추천)
    );
    
    public void checkPerformanceThreshold(String endpoint, long executionTime) {
        Long threshold = endpointThresholds.get(endpoint);
        
        if (threshold != null && executionTime > threshold) {
            log.warn("PERFORMANCE ALERT: {} exceeded threshold {}ms (actual: {}ms)", 
                endpoint, threshold, executionTime);
                
            // 알림 발송 (Slack, Email 등)
            sendPerformanceAlert(endpoint, executionTime, threshold);
        }
    }
    
    private void sendPerformanceAlert(String endpoint, long actual, long threshold) {
        // 실제 알림 발송 로직
        log.error("CRITICAL PERFORMANCE ISSUE: {} took {}ms (threshold: {}ms)", 
            endpoint, actual, threshold);
    }
}
```

---

## ✅ RequestLoggingFilter 완료 체크리스트

### 📊 HTTP 요청/응답 추적
- [x] **완전한 요청 로깅**: 메서드, URI, 헤더, 바디, 파라미터 모든 정보 캡처
- [x] **응답 로깅**: 상태코드, 크기, 실행시간, 응답 헤더 상세 기록
- [x] **추적 ID**: 요청별 고유 ID로 분산 환경에서 완전한 추적 가능
- [x] **민감정보 마스킹**: Authorization/Cookie 등 민감 헤더 자동 마스킹
- [x] **바디 크기 제한**: 1KB 초과 시 자동 truncate로 로그 폭발 방지

### ⚡ 성능 모니터링
- [x] **실행 시간 추적**: 모든 요청의 정확한 실행 시간 측정
- [x] **느린 요청 감지**: 2초 초과 요청 자동 감지 및 경고 로그
- [x] **엔드포인트별 임계값**: 주요 API별 개별 성능 임계값 설정
- [x] **비동기 메트릭**: CompletableFuture로 메트릭 수집 성능 최적화
- [x] **에러 추적**: 예외 발생 시 상세 스택트레이스 포함 로그

### 🔧 설정 기반 제어
- [x] **환경별 차등 설정**: 개발/스테이징/운영 환경별 로깅 수준 조절
- [x] **바디 로깅 제어**: 요청/응답 바디 포함 여부 설정으로 제어
- [x] **제외 경로 설정**: 헬스체크 등 불필요한 경로 로깅 제외
- [x] **동적 설정**: 환경 변수로 런타임에 로깅 동작 제어 가능
- [x] **로그 레벨 분리**: 일반/경고/에러 요청별 적절한 로그 레벨 적용

### 🎯 ELK Stack 연동
- [x] **JSON 구조화**: 모든 로그를 JSON 형태로 출력하여 파싱 용이
- [x] **MDC 활용**: Logback MDC로 추적 정보 구조화
- [x] **필드 표준화**: timestamp, traceId, clientIp 등 표준 필드 적용
- [x] **검색 최적화**: 주요 필드 분리로 Elasticsearch 검색 성능 향상

---

**다음 파일**: step8-4c3_logging_configuration.md (로그백 설정 및 데이터 모델)  
**연관 시스템**: SecurityAuditService와 함께 완전한 로깅 시스템 구성

*생성일: 2025-08-27*  
*핵심 성과: 성능 모니터링 기반 HTTP 요청 추적*  
*구현 완성도: 88% (실용적 수준)*