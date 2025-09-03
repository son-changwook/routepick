# 로깅 시스템 구성

## 개요
RoutePickr 시스템의 포괄적인 로깅 및 감사 시스템을 구현합니다. 성능 모니터링, 보안 감사, 디버깅을 위한 체계적인 로깅 구조를 제공합니다.

## 로깅 구성

### LoggingConfig

```java
package com.routepick.common.logging.config;

import com.routepick.common.logging.aspect.AuditLoggingAspect;
import com.routepick.common.logging.aspect.PerformanceLoggingAspect;
import com.routepick.common.logging.aspect.SecurityLoggingAspect;
import com.routepick.common.logging.filter.RequestLoggingFilter;
import com.routepick.common.logging.service.AuditLogService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.sift.SiftingAppender;
import ch.qos.logback.classic.sift.MDCBasedDiscriminator;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.ConsoleAppender;

import org.slf4j.LoggerFactory;

/**
 * 통합 로깅 설정
 * 
 * 주요 기능:
 * - 요청/응답 로깅
 * - 성능 모니터링
 * - 보안 감사 로깅
 * - 에러 추적
 * - 사용자별 로그 분리
 */
@Configuration
@EnableAspectJAutoProxy
public class LoggingConfig {

    /**
     * 요청 로깅 필터
     */
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registrationBean = 
                new FilterRegistrationBean<>();
        
        registrationBean.setFilter(new RequestLoggingFilter());
        registrationBean.addUrlPatterns("/api/*");
        registrationBean.setName("requestLoggingFilter");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        
        return registrationBean;
    }
    
    /**
     * 성능 모니터링 AOP
     */
    @Bean
    public PerformanceLoggingAspect performanceLoggingAspect() {
        return new PerformanceLoggingAspect();
    }
    
    /**
     * 보안 감사 AOP
     */
    @Bean
    public SecurityLoggingAspect securityLoggingAspect() {
        return new SecurityLoggingAspect();
    }
    
    /**
     * 일반 감사 로깅 AOP
     */
    @Bean
    public AuditLoggingAspect auditLoggingAspect() {
        return new AuditLoggingAspect();
    }
    
    /**
     * 감사 로그 서비스
     */
    @Bean
    public AuditLogService auditLogService() {
        return new AuditLogService();
    }
    
    /**
     * 로그백 프로그래밍 설정
     */
    @Bean
    public LoggerContext configureLogback() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // 콘솔 로거 설정
        configureConsoleAppender(context);
        
        // 파일 로거 설정
        configureFileAppenders(context);
        
        // 사용자별 로그 분리 설정
        configureUserSpecificLogging(context);
        
        return context;
    }
    
    private void configureConsoleAppender(LoggerContext context) {
        ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent> consoleAppender = 
                new ConsoleAppender<>();
        
        consoleAppender.setContext(context);
        consoleAppender.setName("CONSOLE");
        
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(
                "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
        );
        encoder.start();
        
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();
        
        context.getLogger("ROOT").addAppender(consoleAppender);
    }
    
    private void configureFileAppenders(LoggerContext context) {
        // 애플리케이션 로그
        configureRollingFileAppender(context, "APP", "logs/application.log");
        
        // 에러 로그
        configureRollingFileAppender(context, "ERROR", "logs/error.log");
        
        // 보안 감사 로그
        configureRollingFileAppender(context, "SECURITY", "logs/security-audit.log");
        
        // 성능 로그
        configureRollingFileAppender(context, "PERFORMANCE", "logs/performance.log");
    }
    
    private void configureRollingFileAppender(LoggerContext context, String name, String fileName) {
        RollingFileAppender<ch.qos.logback.classic.spi.ILoggingEvent> fileAppender = 
                new RollingFileAppender<>();
        
        fileAppender.setContext(context);
        fileAppender.setName(name);
        fileAppender.setFile(fileName);
        
        TimeBasedRollingPolicy<ch.qos.logback.classic.spi.ILoggingEvent> rollingPolicy = 
                new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(fileName + ".%d{yyyy-MM-dd}.gz");
        rollingPolicy.setMaxHistory(30); // 30일 보관
        rollingPolicy.setTotalSizeCap(ch.qos.logback.core.util.FileSize.valueOf("5GB"));
        rollingPolicy.start();
        
        fileAppender.setRollingPolicy(rollingPolicy);
        
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(
                "%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId}] [%X{userId}] %-5level %logger{36} - %msg%n"
        );
        encoder.start();
        
        fileAppender.setEncoder(encoder);
        fileAppender.start();
    }
    
    private void configureUserSpecificLogging(LoggerContext context) {
        SiftingAppender siftingAppender = new SiftingAppender();
        siftingAppender.setContext(context);
        siftingAppender.setName("USER_SIFT");
        
        MDCBasedDiscriminator discriminator = new MDCBasedDiscriminator();
        discriminator.setKey("userId");
        discriminator.setDefaultValue("anonymous");
        discriminator.start();
        
        siftingAppender.setDiscriminator(discriminator);
        siftingAppender.start();
    }
}
```

### 요청 로깅 필터

```java
package com.routepick.common.logging.filter;

import com.routepick.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP 요청/응답 로깅 필터
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 로깅하지 않을 경로들
    private static final String[] EXCLUDED_PATHS = {
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/favicon.ico"
    };
    
    // 민감한 헤더들 (로깅 제외)
    private static final String[] SENSITIVE_HEADERS = {
            "authorization", 
            "cookie", 
            "x-api-key",
            "x-auth-token"
    };
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, 
            HttpServletResponse response, 
            FilterChain filterChain) throws ServletException, IOException {
        
        // 제외 경로 확인
        if (shouldSkipLogging(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 추적 ID 생성 및 MDC 설정
        String traceId = generateTraceId();
        setupMDC(request, traceId);
        
        // 요청/응답 래퍼 생성
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 요청 로깅
            logRequest(requestWrapper, traceId);
            
            // 요청 처리
            filterChain.doFilter(requestWrapper, responseWrapper);
            
            // 응답 로깅
            logResponse(responseWrapper, traceId, System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            // 예외 로깅
            logException(e, traceId);
            throw e;
        } finally {
            // 응답 본문 복사
            responseWrapper.copyBodyToResponse();
            
            // MDC 정리
            clearMDC();
        }
    }
    
    private boolean shouldSkipLogging(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String excludedPath : EXCLUDED_PATHS) {
            if (path.startsWith(excludedPath)) {
                return true;
            }
        }
        return false;
    }
    
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private void setupMDC(HttpServletRequest request, String traceId) {
        MDC.put("traceId", traceId);
        MDC.put("requestMethod", request.getMethod());
        MDC.put("requestURI", request.getRequestURI());
        MDC.put("clientIP", getClientIP(request));
        
        // 사용자 정보 (인증된 경우)
        String userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            MDC.put("userId", userId);
        }
    }
    
    private void logRequest(ContentCachingRequestWrapper request, String traceId) {
        try {
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("traceId", traceId);
            requestData.put("method", request.getMethod());
            requestData.put("uri", request.getRequestURI());
            requestData.put("queryString", request.getQueryString());
            requestData.put("clientIP", getClientIP(request));
            requestData.put("userAgent", request.getHeader("User-Agent"));
            requestData.put("timestamp", System.currentTimeMillis());
            
            // 헤더 정보 (민감한 정보 제외)
            Map<String, String> headers = new HashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (!isSensitiveHeader(headerName)) {
                    headers.put(headerName, request.getHeader(headerName));
                }
            }
            requestData.put("headers", headers);
            
            // 요청 본문 (POST, PUT, PATCH인 경우)
            if (shouldLogRequestBody(request)) {
                String body = getRequestBody(request);
                if (body != null && !body.isEmpty()) {
                    requestData.put("body", maskSensitiveData(body));
                }
            }
            
            auditLog.info("REQUEST: {}", objectMapper.writeValueAsString(requestData));
            
        } catch (Exception e) {
            log.warn("요청 로깅 중 오류 발생", e);
        }
    }
    
    private void logResponse(ContentCachingResponseWrapper response, String traceId, long duration) {
        try {
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("traceId", traceId);
            responseData.put("status", response.getStatus());
            responseData.put("duration", duration + "ms");
            responseData.put("timestamp", System.currentTimeMillis());
            
            // 응답 본문 (에러인 경우에만)
            if (response.getStatus() >= 400) {
                String body = getResponseBody(response);
                if (body != null && !body.isEmpty()) {
                    responseData.put("errorBody", body);
                }
            }
            
            // 성능 경고 (응답 시간이 3초 초과인 경우)
            if (duration > 3000) {
                responseData.put("performanceWarning", "SLOW_RESPONSE");
                log.warn("느린 응답 감지 - TraceId: {}, Duration: {}ms", traceId, duration);
            }
            
            auditLog.info("RESPONSE: {}", objectMapper.writeValueAsString(responseData));
            
        } catch (Exception e) {
            log.warn("응답 로깅 중 오류 발생", e);
        }
    }
    
    private void logException(Exception exception, String traceId) {
        try {
            Map<String, Object> exceptionData = new HashMap<>();
            exceptionData.put("traceId", traceId);
            exceptionData.put("exception", exception.getClass().getSimpleName());
            exceptionData.put("message", exception.getMessage());
            exceptionData.put("timestamp", System.currentTimeMillis());
            
            auditLog.error("EXCEPTION: {}", objectMapper.writeValueAsString(exceptionData));
            
        } catch (Exception e) {
            log.warn("예외 로깅 중 오류 발생", e);
        }
    }
    
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean isSensitiveHeader(String headerName) {
        for (String sensitiveHeader : SENSITIVE_HEADERS) {
            if (sensitiveHeader.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean shouldLogRequestBody(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }
    
    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, request.getCharacterEncoding() != null ? 
                    java.nio.charset.Charset.forName(request.getCharacterEncoding()) :
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }
    
    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, response.getCharacterEncoding() != null ?
                    java.nio.charset.Charset.forName(response.getCharacterEncoding()) :
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }
    
    private String maskSensitiveData(String data) {
        if (data == null) return null;
        
        // 비밀번호 마스킹
        data = data.replaceAll("(\"password\"\\s*:\\s*\")[^\"]*\"", "$1****\"");
        
        // 카드번호 마스킹
        data = data.replaceAll("(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})", 
                              "$1-****-****-$4");
        
        // 이메일 부분 마스킹
        data = data.replaceAll("([a-zA-Z0-9._%+-]{1,3})[a-zA-Z0-9._%+-]*@", "$1****@");
        
        return data;
    }
    
    private void clearMDC() {
        MDC.clear();
    }
}
```

### 성능 로깅 Aspect

```java
package com.routepick.common.logging.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 성능 모니터링 AOP
 */
@Aspect
@Component
public class PerformanceLoggingAspect {
    
    private static final Logger performanceLog = LoggerFactory.getLogger("PERFORMANCE");
    
    /**
     * Service 클래스의 public 메서드 성능 모니터링
     */
    @Around("execution(public * com.routepick.*.service.*.*(..))")
    public Object logServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodPerformance(joinPoint, "SERVICE");
    }
    
    /**
     * Repository 클래스의 public 메서드 성능 모니터링
     */
    @Around("execution(public * com.routepick.*.repository.*.*(..))")
    public Object logRepositoryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodPerformance(joinPoint, "REPOSITORY");
    }
    
    /**
     * Controller 클래스의 public 메서드 성능 모니터링
     */
    @Around("execution(public * com.routepick.*.controller.*.*(..))")
    public Object logControllerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodPerformance(joinPoint, "CONTROLLER");
    }
    
    private Object logMethodPerformance(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        long startTime = System.currentTimeMillis();
        String traceId = MDC.get("traceId");
        
        try {
            // 메서드 시작 로깅
            performanceLog.debug("[{}] {}#{} started - TraceId: {}, Args: {}", 
                    layer, className, methodName, traceId, 
                    args.length > 0 ? Arrays.toString(args) : "none");
            
            // 메서드 실행
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 성능 로깅
            if (executionTime > getThreshold(layer)) {
                performanceLog.warn("[{}] {}#{} SLOW - Duration: {}ms, TraceId: {}", 
                        layer, className, methodName, executionTime, traceId);
            } else {
                performanceLog.debug("[{}] {}#{} completed - Duration: {}ms, TraceId: {}", 
                        layer, className, methodName, executionTime, traceId);
            }
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            performanceLog.error("[{}] {}#{} FAILED - Duration: {}ms, TraceId: {}, Error: {}", 
                    layer, className, methodName, executionTime, traceId, e.getMessage());
            throw e;
        }
    }
    
    private long getThreshold(String layer) {
        return switch (layer) {
            case "CONTROLLER" -> 2000; // 2초
            case "SERVICE" -> 1000;    // 1초
            case "REPOSITORY" -> 500;  // 0.5초
            default -> 1000;
        };
    }
}
```

### 보안 감사 로깅 Aspect

```java
package com.routepick.common.logging.aspect;

import com.routepick.common.security.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 보안 감사 로깅 AOP
 */
@Aspect
@Component
public class SecurityLoggingAspect {
    
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");
    
    /**
     * 인증 관련 메서드 호출 전 로깅
     */
    @Before("execution(* com.routepick.auth.service.AuthService.*(..))")
    public void logAuthAttempt(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "AUTH_ATTEMPT");
        logData.put("method", methodName);
        logData.put("timestamp", LocalDateTime.now());
        logData.put("clientIP", MDC.get("clientIP"));
        
        if (args.length > 0 && args[0] instanceof String) {
            logData.put("username", args[0]);
        }
        
        securityLog.info("Security Event: {}", logData);
    }
    
    /**
     * 인증 성공 시 로깅
     */
    @AfterReturning(value = "execution(* com.routepick.auth.service.AuthService.login(..))", 
                   returning = "result")
    public void logAuthSuccess(JoinPoint joinPoint, Object result) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "AUTH_SUCCESS");
        logData.put("userId", SecurityUtils.getCurrentUserId());
        logData.put("username", SecurityUtils.getCurrentUsername());
        logData.put("timestamp", LocalDateTime.now());
        logData.put("clientIP", MDC.get("clientIP"));
        logData.put("userAgent", MDC.get("userAgent"));
        
        securityLog.info("Security Event: {}", logData);
    }
    
    /**
     * 인증 실패 시 로깅
     */
    @AfterThrowing(value = "execution(* com.routepick.auth.service.AuthService.*(..))", 
                  throwing = "ex")
    public void logAuthFailure(JoinPoint joinPoint, Exception ex) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "AUTH_FAILURE");
        logData.put("method", methodName);
        logData.put("error", ex.getClass().getSimpleName());
        logData.put("message", ex.getMessage());
        logData.put("timestamp", LocalDateTime.now());
        logData.put("clientIP", MDC.get("clientIP"));
        
        if (args.length > 0 && args[0] instanceof String) {
            logData.put("attemptedUsername", args[0]);
        }
        
        securityLog.warn("Security Event: {}", logData);
    }
    
    /**
     * 결제 관련 보안 로깅
     */
    @Before("execution(* com.routepick.payment.service.*.*(..))")
    public void logPaymentActivity(JoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "PAYMENT_ACTIVITY");
        logData.put("service", className);
        logData.put("method", methodName);
        logData.put("userId", SecurityUtils.getCurrentUserId());
        logData.put("timestamp", LocalDateTime.now());
        logData.put("sessionId", MDC.get("sessionId"));
        
        securityLog.info("Payment Security Event: {}", logData);
    }
    
    /**
     * 관리자 권한 필요 작업 로깅
     */
    @Before("@annotation(org.springframework.security.access.prepost.PreAuthorize) && " +
            "args(*, org.springframework.security.access.prepost.PreAuthorize(\"hasRole('ADMIN')\"))")
    public void logAdminActivity(JoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "ADMIN_ACTIVITY");
        logData.put("service", className);
        logData.put("method", methodName);
        logData.put("adminId", SecurityUtils.getCurrentUserId());
        logData.put("timestamp", LocalDateTime.now());
        logData.put("clientIP", MDC.get("clientIP"));
        
        securityLog.warn("Admin Security Event: {}", logData);
    }
}
```

### 감사 로그 서비스

```java
package com.routepick.common.logging.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 감사 로그 서비스
 */
@Service
public class AuditLogService {
    
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 사용자 활동 로깅
     */
    public void logUserActivity(String userId, String activity, Map<String, Object> details) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("type", "USER_ACTIVITY");
            logEntry.put("userId", userId);
            logEntry.put("activity", activity);
            logEntry.put("timestamp", LocalDateTime.now());
            logEntry.put("details", details);
            
            auditLogger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            auditLogger.error("감사 로그 작성 실패", e);
        }
    }
    
    /**
     * 데이터 변경 로깅
     */
    public void logDataChange(String entityType, Object entityId, String operation, 
                             Object oldValue, Object newValue) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("type", "DATA_CHANGE");
            logEntry.put("entityType", entityType);
            logEntry.put("entityId", entityId);
            logEntry.put("operation", operation);
            logEntry.put("oldValue", oldValue);
            logEntry.put("newValue", newValue);
            logEntry.put("timestamp", LocalDateTime.now());
            
            auditLogger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            auditLogger.error("데이터 변경 로그 작성 실패", e);
        }
    }
    
    /**
     * 시스템 이벤트 로깅
     */
    public void logSystemEvent(String event, String description, Map<String, Object> metadata) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("type", "SYSTEM_EVENT");
            logEntry.put("event", event);
            logEntry.put("description", description);
            logEntry.put("metadata", metadata);
            logEntry.put("timestamp", LocalDateTime.now());
            
            auditLogger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            auditLogger.error("시스템 이벤트 로그 작성 실패", e);
        }
    }
}
```

## 로그백 설정 파일

### logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 변수 정의 -->
    <property name="LOG_PATH" value="logs"/>
    <property name="LOG_PATTERN" 
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId}] [%X{userId}] [%thread] %-5level %logger{36} - %msg%n"/>
    
    <!-- 콘솔 출력 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>
    
    <!-- 애플리케이션 로그 파일 -->
    <appender name="APP_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/application.log</file>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/application.%d{yyyy-MM-dd}.%i.gz</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy 
                class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>100MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
            <maxHistory>30</maxHistory>
            <totalSizeCap>5GB</totalSizeCap>
        </rollingPolicy>
    </appender>
    
    <!-- 에러 로그 파일 -->
    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/error.log</file>
        <filter class="ch.qos.logback.classic.filter.LevelFilter">
            <level>ERROR</level>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/error.%d{yyyy-MM-dd}.%i.gz</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy 
                class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>50MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
            <maxHistory>90</maxHistory>
        </rollingPolicy>
    </appender>
    
    <!-- 보안 감사 로그 -->
    <appender name="SECURITY_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/security-audit.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId}] - %msg%n</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/security-audit.%d{yyyy-MM-dd}.gz</fileNamePattern>
            <maxHistory>365</maxHistory> <!-- 보안 로그는 1년간 보관 -->
        </rollingPolicy>
    </appender>
    
    <!-- 성능 로그 -->
    <appender name="PERFORMANCE_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/performance.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId}] - %msg%n</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/performance.%d{yyyy-MM-dd}.gz</fileNamePattern>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
    </appender>
    
    <!-- 감사 로그 -->
    <appender name="AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/audit.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} - %msg%n</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/audit.%d{yyyy-MM-dd}.gz</fileNamePattern>
            <maxHistory>365</maxHistory> <!-- 감사 로그는 1년간 보관 -->
        </rollingPolicy>
    </appender>
    
    <!-- 로거 설정 -->
    <logger name="com.routepick" level="INFO" additivity="false">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="APP_FILE"/>
        <appender-ref ref="ERROR_FILE"/>
    </logger>
    
    <logger name="SECURITY" level="INFO" additivity="false">
        <appender-ref ref="SECURITY_FILE"/>
    </logger>
    
    <logger name="PERFORMANCE" level="DEBUG" additivity="false">
        <appender-ref ref="PERFORMANCE_FILE"/>
    </logger>
    
    <logger name="AUDIT" level="INFO" additivity="false">
        <appender-ref ref="AUDIT_FILE"/>
    </logger>
    
    <!-- 외부 라이브러리 로그 레벨 조정 -->
    <logger name="org.springframework" level="WARN"/>
    <logger name="org.hibernate" level="WARN"/>
    <logger name="com.zaxxer.hikari" level="WARN"/>
    
    <!-- 루트 로거 -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="APP_FILE"/>
    </root>
    
    <!-- 프로파일별 설정 -->
    <springProfile name="dev">
        <logger name="com.routepick" level="DEBUG"/>
        <logger name="org.springframework.web" level="DEBUG"/>
    </springProfile>
    
    <springProfile name="prod">
        <logger name="com.routepick" level="INFO"/>
        <root level="WARN">
            <appender-ref ref="APP_FILE"/>
            <appender-ref ref="ERROR_FILE"/>
        </root>
    </springProfile>
</configuration>
```

## 설정 파일

### application.yml

```yaml
# 로깅 관련 설정
logging:
  config: classpath:logback-spring.xml
  level:
    com.routepick: INFO
    org.springframework.security: WARN
    org.hibernate.SQL: WARN
    org.hibernate.type.descriptor.sql.BasicBinder: WARN

# 애플리케이션별 로깅 설정
app:
  logging:
    # 요청/응답 로깅 활성화
    request-response-logging: true
    
    # 성능 모니터링
    performance-monitoring: true
    performance-threshold:
      controller: 2000ms
      service: 1000ms
      repository: 500ms
    
    # 보안 감사 로깅
    security-audit: true
    
    # 민감정보 마스킹
    mask-sensitive-data: true
    
    # 로그 압축 및 보관
    archive:
      enabled: true
      max-history: 30
      total-size-cap: 5GB
```

이 로깅 시스템은 RoutePickr 애플리케이션의 완전한 관찰 가능성(Observability)을 제공하며, 문제 해결, 성능 최적화, 보안 감사를 위한 포괄적인 로깅을 지원합니다.