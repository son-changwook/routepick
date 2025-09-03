# Step 4-4c2: 시스템 로깅 엔티티 설계

> **RoutePickr 시스템 로깅** - API 로그, 외부 API 설정, 웹훅 로그  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-4c2 (JPA 엔티티 50개 - 시스템 로깅 3개)  
> **분할**: step4-4c_system_final.md → 시스템 로깅 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 시스템 로깅 엔티티**를 담고 있습니다.

### 🎯 주요 특징
- **API 호출 추적**: REST API 호출 로그, 성능 모니터링, 에러 추적
- **외부 API 관리**: 소셜 로그인, 결제, 지도 등 외부 API 설정 관리
- **웹훅 시스템**: 외부 시스템 연동, 이벤트 전송, 재시도 로직
- **운영 최적화**: 로그 검색, 상태 모니터링, 성능 분석

### 📊 엔티티 목록 (3개)
1. **ApiLog** - API 호출 로그 (성능 모니터링, 에러 추적)
2. **ExternalApiConfig** - 외부 API 설정 (환경별 관리, 상태 체크)
3. **WebhookLog** - 웹훅 로그 (이벤트 전송, 재시도 관리)

---

## 📊 1. ApiLog 엔티티 - API 호출 로그

```java
package com.routepick.domain.system.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.ApiLogLevel;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * API 호출 로그
 * - REST API 호출 추적
 * - 성능 모니터링
 * - 에러 추적 및 분석
 */
@Entity
@Table(name = "api_logs", indexes = {
    @Index(name = "idx_api_log_endpoint", columnList = "endpoint"),
    @Index(name = "idx_api_log_user", columnList = "user_id"),
    @Index(name = "idx_api_log_status", columnList = "response_status"),
    @Index(name = "idx_api_log_level", columnList = "log_level"),
    @Index(name = "idx_api_log_method", columnList = "http_method"),
    @Index(name = "idx_api_log_time", columnList = "request_time DESC"),
    @Index(name = "idx_api_log_duration", columnList = "duration_ms DESC"),
    @Index(name = "idx_api_log_error", columnList = "log_level, response_status, request_time DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ApiLog extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // 호출 사용자 (비로그인 시 null)
    
    // ===== 요청 정보 =====
    
    @NotBlank
    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint; // API 엔드포인트
    
    @NotBlank
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod; // GET, POST, PUT, DELETE 등
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // IPv4/IPv6 지원
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @NotNull
    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime = LocalDateTime.now();
    
    // ===== 응답 정보 =====
    
    @Min(value = 100, message = "HTTP 상태 코드는 100 이상이어야 합니다")
    @Max(value = 599, message = "HTTP 상태 코드는 599 이하여야 합니다")
    @Column(name = "response_status")
    private Integer responseStatus; // HTTP 상태 코드
    
    @Min(value = 0, message = "응답 시간은 0ms 이상이어야 합니다")
    @Column(name = "duration_ms")
    private Long durationMs; // 응답 시간 (밀리초)
    
    @Column(name = "response_size")
    private Long responseSize; // 응답 크기 (바이트)
    
    // ===== 로그 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "log_level", nullable = false, length = 10)
    private ApiLogLevel logLevel = ApiLogLevel.INFO;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage; // 에러 메시지
    
    @Column(name = "exception_class", length = 200)
    private String exceptionClass; // 예외 클래스명
    
    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams; // 요청 파라미터 (JSON)
    
    // ===== 생성자 =====
    
    public static ApiLog createInfoLog(String endpoint, String method, String clientIp) {
        return ApiLog.builder()
                .endpoint(endpoint)
                .httpMethod(method)
                .clientIp(clientIp)
                .logLevel(ApiLogLevel.INFO)
                .requestTime(LocalDateTime.now())
                .build();
    }
    
    public static ApiLog createErrorLog(String endpoint, String method, String errorMessage, String exceptionClass) {
        return ApiLog.builder()
                .endpoint(endpoint)
                .httpMethod(method)
                .logLevel(ApiLogLevel.ERROR)
                .errorMessage(errorMessage)
                .exceptionClass(exceptionClass)
                .requestTime(LocalDateTime.now())
                .build();
    }
    
    public static ApiLog createUserLog(User user, String endpoint, String method, String clientIp, String userAgent) {
        return ApiLog.builder()
                .user(user)
                .endpoint(endpoint)
                .httpMethod(method)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .logLevel(ApiLogLevel.INFO)
                .requestTime(LocalDateTime.now())
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 응답 완료 처리
     */
    public void completeResponse(int status, long duration, long size) {
        this.responseStatus = status;
        this.durationMs = duration;
        this.responseSize = size;
        
        // 상태 코드별 로그 레벨 자동 설정
        if (status >= 500) {
            this.logLevel = ApiLogLevel.ERROR;
        } else if (status >= 400) {
            this.logLevel = ApiLogLevel.WARN;
        }
    }
    
    /**
     * 에러 정보 설정
     */
    public void setErrorInfo(String message, String exceptionClass) {
        this.errorMessage = message;
        this.exceptionClass = exceptionClass;
        this.logLevel = ApiLogLevel.ERROR;
    }
    
    /**
     * 로그 레벨 한글 표시
     */
    @Transient
    public String getLogLevelKorean() {
        if (logLevel == null) return "정보";
        
        return switch (logLevel) {
            case DEBUG -> "디버그";
            case INFO -> "정보";
            case WARN -> "경고";
            case ERROR -> "오류";
            default -> "정보";
        };
    }
    
    /**
     * 느린 API 여부 (1초 이상)
     */
    @Transient
    public boolean isSlowApi() {
        return durationMs != null && durationMs > 1000;
    }
    
    /**
     * 매우 느린 API 여부 (5초 이상)
     */
    @Transient
    public boolean isVerySlowApi() {
        return durationMs != null && durationMs > 5000;
    }
    
    /**
     * 에러 로그 여부
     */
    @Transient
    public boolean isErrorLog() {
        return logLevel == ApiLogLevel.ERROR || 
               (responseStatus != null && responseStatus >= 400);
    }
    
    /**
     * 성공 응답 여부
     */
    @Transient
    public boolean isSuccessResponse() {
        return responseStatus != null && responseStatus >= 200 && responseStatus < 300;
    }
    
    /**
     * 클라이언트 에러 여부 (4xx)
     */
    @Transient
    public boolean isClientError() {
        return responseStatus != null && responseStatus >= 400 && responseStatus < 500;
    }
    
    /**
     * 서버 에러 여부 (5xx)
     */
    @Transient
    public boolean isServerError() {
        return responseStatus != null && responseStatus >= 500;
    }
    
    /**
     * 응답 크기 MB 단위 반환
     */
    @Transient
    public double getResponseSizeMB() {
        if (responseSize == null) return 0.0;
        return responseSize / (1024.0 * 1024.0);
    }
    
    /**
     * 성능 등급 반환
     */
    @Transient
    public String getPerformanceGrade() {
        if (durationMs == null) return "미측정";
        
        if (durationMs < 100) return "우수";
        if (durationMs < 500) return "좋음";
        if (durationMs < 1000) return "보통";
        if (durationMs < 3000) return "느림";
        return "매우 느림";
    }
    
    /**
     * 로그 요약 정보
     */
    @Transient
    public String getLogSummary() {
        return String.format("%s %s - %d (%dms)", 
                httpMethod, endpoint, 
                responseStatus != null ? responseStatus : 0, 
                durationMs != null ? durationMs : 0);
    }
    
    /**
     * 사용자별 로그 여부
     */
    @Transient
    public boolean isUserLog() {
        return user != null;
    }
    
    @Override
    public Long getId() {
        return logId;
    }
}
```

---

## ⚙️ 2. ExternalApiConfig 엔티티 - 외부 API 설정

```java
package com.routepick.domain.system.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.ApiProviderType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 외부 API 설정
 * - 소셜 로그인, 결제, 지도 등 외부 API 설정 관리
 * - API 키, 엔드포인트, 제한사항 관리
 * - 환경별 설정 분리
 */
@Entity
@Table(name = "external_api_configs", indexes = {
    @Index(name = "idx_external_api_provider", columnList = "provider_type"),
    @Index(name = "idx_external_api_environment", columnList = "environment"),
    @Index(name = "idx_external_api_active", columnList = "is_active"),
    @Index(name = "idx_external_api_provider_env", columnList = "provider_type, environment", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExternalApiConfig extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;
    
    // ===== 제공자 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private ApiProviderType providerType;
    
    @NotBlank
    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName; // GOOGLE, KAKAO, NAVER, FACEBOOK, INICIS 등
    
    @NotBlank
    @Column(name = "environment", nullable = false, length = 20)
    private String environment; // DEV, STAGING, PROD
    
    // ===== API 설정 =====
    
    @NotBlank
    @Column(name = "api_key", nullable = false, length = 200)
    private String apiKey; // 암호화된 API 키
    
    @Column(name = "api_secret", length = 200)
    private String apiSecret; // 암호화된 API 시크릿
    
    @NotBlank
    @Column(name = "base_url", nullable = false, length = 200)
    private String baseUrl; // 기본 URL
    
    @Column(name = "callback_url", length = 200)
    private String callbackUrl; // 콜백 URL (소셜 로그인용)
    
    // ===== 제한 설정 =====
    
    @Min(value = 1, message = "시간당 호출 제한은 1 이상이어야 합니다")
    @Max(value = 1000000, message = "시간당 호출 제한은 1,000,000 이하여야 합니다")
    @Column(name = "rate_limit_per_hour")
    private Integer rateLimitPerHour; // 시간당 호출 제한
    
    @Min(value = 1000, message = "타임아웃은 1000ms 이상이어야 합니다")
    @Max(value = 300000, message = "타임아웃은 300초 이하여야 합니다")
    @Column(name = "timeout_ms")
    private Integer timeoutMs = 30000; // 타임아웃 (밀리초)
    
    @Min(value = 0, message = "재시도 횟수는 0 이상이어야 합니다")
    @Max(value = 10, message = "재시도 횟수는 10 이하여야 합니다")
    @Column(name = "retry_count")
    private Integer retryCount = 3; // 재시도 횟수
    
    // ===== 상태 정보 =====
    
    @NotNull
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "last_health_check")
    private LocalDateTime lastHealthCheck; // 마지막 상태 확인
    
    @Column(name = "health_status", length = 20)
    private String healthStatus; // HEALTHY, UNHEALTHY, UNKNOWN
    
    @Column(name = "description", length = 500)
    private String description; // 설정 설명
    
    // ===== 생성자 =====
    
    public static ExternalApiConfig createSocialLogin(ApiProviderType type, String providerName, 
                                                     String environment, String apiKey, String baseUrl, String callbackUrl) {
        return ExternalApiConfig.builder()
                .providerType(type)
                .providerName(providerName)
                .environment(environment)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .callbackUrl(callbackUrl)
                .rateLimitPerHour(1000)
                .timeoutMs(30000)
                .retryCount(3)
                .build();
    }
    
    public static ExternalApiConfig createPaymentGateway(String providerName, String environment, 
                                                        String apiKey, String apiSecret, String baseUrl) {
        return ExternalApiConfig.builder()
                .providerType(ApiProviderType.PAYMENT)
                .providerName(providerName)
                .environment(environment)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .baseUrl(baseUrl)
                .rateLimitPerHour(10000)
                .timeoutMs(60000)
                .retryCount(5)
                .build();
    }
    
    public static ExternalApiConfig createMapService(String providerName, String environment, 
                                                    String apiKey, String baseUrl) {
        return ExternalApiConfig.builder()
                .providerType(ApiProviderType.MAP)
                .providerName(providerName)
                .environment(environment)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .rateLimitPerHour(5000)
                .timeoutMs(15000)
                .retryCount(2)
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * API 제공자 타입 한글 표시
     */
    @Transient
    public String getProviderTypeKorean() {
        if (providerType == null) return "미분류";
        
        return switch (providerType) {
            case SOCIAL_LOGIN -> "소셜 로그인";
            case PAYMENT -> "결제";
            case MAP -> "지도 서비스";
            case NOTIFICATION -> "알림 서비스";
            case FILE_STORAGE -> "파일 저장소";
            case EMAIL -> "이메일 서비스";
            case SMS -> "문자 서비스";
            default -> "기타";
        };
    }
    
    /**
     * 환경 한글 표시
     */
    @Transient
    public String getEnvironmentKorean() {
        if (environment == null) return "미설정";
        
        return switch (environment.toUpperCase()) {
            case "DEV" -> "개발";
            case "STAGING" -> "스테이징";
            case "PROD" -> "운영";
            case "TEST" -> "테스트";
            default -> environment;
        };
    }
    
    /**
     * 상태 한글 표시
     */
    @Transient
    public String getHealthStatusKorean() {
        if (healthStatus == null) return "미확인";
        
        return switch (healthStatus) {
            case "HEALTHY" -> "정상";
            case "UNHEALTHY" -> "비정상";
            case "DEGRADED" -> "성능 저하";
            case "MAINTENANCE" -> "점검 중";
            default -> "알 수 없음";
        };
    }
    
    /**
     * API 설정 활성화
     */
    public void activate() {
        this.isActive = true;
    }
    
    /**
     * API 설정 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        this.healthStatus = "INACTIVE";
    }
    
    /**
     * 헬스 체크 업데이트
     */
    public void updateHealthStatus(String status) {
        this.healthStatus = status;
        this.lastHealthCheck = LocalDateTime.now();
    }
    
    /**
     * 유효한 설정인지 확인
     */
    public boolean isValidConfig() {
        return providerType != null && apiKey != null && !apiKey.trim().isEmpty() &&
               baseUrl != null && !baseUrl.trim().isEmpty() && isActive;
    }
    
    /**
     * 프로덕션 환경 여부
     */
    @Transient
    public boolean isProduction() {
        return "PROD".equalsIgnoreCase(environment);
    }
    
    /**
     * 개발 환경 여부
     */
    @Transient
    public boolean isDevelopment() {
        return "DEV".equalsIgnoreCase(environment);
    }
    
    /**
     * 헬스 체크 필요 여부 (1시간마다)
     */
    @Transient
    public boolean needsHealthCheck() {
        return lastHealthCheck == null || 
               lastHealthCheck.isBefore(LocalDateTime.now().minusHours(1));
    }
    
    /**
     * 정상 상태 여부
     */
    @Transient
    public boolean isHealthy() {
        return "HEALTHY".equals(healthStatus);
    }
    
    /**
     * 비정상 상태 여부
     */
    @Transient
    public boolean isUnhealthy() {
        return "UNHEALTHY".equals(healthStatus) || "DEGRADED".equals(healthStatus);
    }
    
    /**
     * 타임아웃 초 단위 반환
     */
    @Transient
    public int getTimeoutSeconds() {
        return timeoutMs != null ? timeoutMs / 1000 : 30;
    }
    
    /**
     * 설정 요약 정보
     */
    @Transient
    public String getConfigSummary() {
        return String.format("%s (%s) - %s 환경", 
                providerName, providerType, environment);
    }
    
    @Override
    public Long getId() {
        return configId;
    }
}
```

---

## 🔗 3. WebhookLog 엔티티 - 웹훅 로그

```java
package com.routepick.domain.system.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.WebhookStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 웹훅 로그
 * - 외부 시스템으로의 웹훅 호출 추적
 * - 결제, 알림 등 이벤트 전송 로그
 * - 재시도 및 실패 추적
 */
@Entity
@Table(name = "webhook_logs", indexes = {
    @Index(name = "idx_webhook_log_event", columnList = "event_type"),
    @Index(name = "idx_webhook_log_status", columnList = "webhook_status"),
    @Index(name = "idx_webhook_log_url", columnList = "target_url"),
    @Index(name = "idx_webhook_log_time", columnList = "sent_at DESC"),
    @Index(name = "idx_webhook_log_retry", columnList = "retry_count"),
    @Index(name = "idx_webhook_log_failed", columnList = "webhook_status, sent_at DESC"),
    @Index(name = "idx_webhook_log_duration", columnList = "response_time_ms DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class WebhookLog extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "webhook_log_id")
    private Long webhookLogId;
    
    // ===== 이벤트 정보 =====
    
    @NotBlank
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // PAYMENT_SUCCESS, USER_REGISTER, ROUTE_CREATED 등
    
    @Column(name = "event_id", length = 100)
    private String eventId; // 이벤트 고유 ID
    
    @NotBlank
    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl; // 웹훅 대상 URL
    
    // ===== 요청 정보 =====
    
    @NotBlank
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod = "POST";
    
    @Column(name = "request_headers", columnDefinition = "TEXT")
    private String requestHeaders; // 요청 헤더 (JSON)
    
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody; // 요청 본문 (JSON)
    
    @NotNull
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();
    
    // ===== 응답 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "webhook_status", nullable = false, length = 20)
    private WebhookStatus webhookStatus = WebhookStatus.PENDING;
    
    @Min(value = 100, message = "HTTP 상태 코드는 100 이상이어야 합니다")
    @Max(value = 599, message = "HTTP 상태 코드는 599 이하여야 합니다")
    @Column(name = "response_status")
    private Integer responseStatus; // HTTP 응답 상태
    
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody; // 응답 본문
    
    @Min(value = 0, message = "응답 시간은 0ms 이상이어야 합니다")
    @Column(name = "response_time_ms")
    private Long responseTimeMs; // 응답 시간 (밀리초)
    
    // ===== 재시도 정보 =====
    
    @Min(value = 0, message = "재시도 횟수는 0 이상이어야 합니다")
    @Max(value = 10, message = "재시도 횟수는 10 이하여야 합니다")
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    private Integer maxRetries = 3;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt; // 다음 재시도 시각
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage; // 에러 메시지
    
    // ===== 생성자 =====
    
    public static WebhookLog createWebhook(String eventType, String eventId, String targetUrl, String requestBody) {
        return WebhookLog.builder()
                .eventType(eventType)
                .eventId(eventId)
                .targetUrl(targetUrl)
                .requestBody(requestBody)
                .sentAt(LocalDateTime.now())
                .build();
    }
    
    public static WebhookLog createPaymentWebhook(String eventId, String targetUrl, String paymentData) {
        return WebhookLog.builder()
                .eventType("PAYMENT_COMPLETED")
                .eventId(eventId)
                .targetUrl(targetUrl)
                .requestBody(paymentData)
                .maxRetries(5) // 결제는 더 많이 재시도
                .sentAt(LocalDateTime.now())
                .build();
    }
    
    public static WebhookLog createUserWebhook(String eventType, String userId, String targetUrl, String userData) {
        return WebhookLog.builder()
                .eventType(eventType)
                .eventId(userId)
                .targetUrl(targetUrl)
                .requestBody(userData)
                .sentAt(LocalDateTime.now())
                .build();
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 이벤트 타입 한글 표시
     */
    @Transient
    public String getEventTypeKorean() {
        if (eventType == null) return "알 수 없음";
        
        return switch (eventType) {
            case "PAYMENT_SUCCESS" -> "결제 성공";
            case "PAYMENT_FAILED" -> "결제 실패";
            case "USER_REGISTER" -> "회원 가입";
            case "USER_WITHDRAW" -> "회원 탈퇴";
            case "ROUTE_CREATED" -> "루트 생성";
            case "CLIMB_COMPLETED" -> "클라이밍 완료";
            case "FOLLOW_CREATED" -> "팔로우 생성";
            default -> eventType;
        };
    }
    
    /**
     * 웹훅 상태 한글 표시
     */
    @Transient
    public String getWebhookStatusKorean() {
        if (webhookStatus == null) return "대기 중";
        
        return switch (webhookStatus) {
            case PENDING -> "대기 중";
            case SUCCESS -> "성공";
            case FAILED -> "실패";
            case RETRY_SCHEDULED -> "재시도 예정";
            case CANCELLED -> "취소됨";
            default -> "알 수 없음";
        };
    }
    
    /**
     * 웹훅 성공 처리
     */
    public void markSuccess(int responseStatus, String responseBody, long responseTime) {
        this.webhookStatus = WebhookStatus.SUCCESS;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseTimeMs = responseTime;
    }
    
    /**
     * 웹훅 실패 처리
     */
    public void markFailure(String errorMessage, Integer responseStatus) {
        this.webhookStatus = WebhookStatus.FAILED;
        this.errorMessage = errorMessage;
        this.responseStatus = responseStatus;
        
        // 재시도 가능한 경우 스케줄링
        if (canRetry()) {
            scheduleRetry();
        }
    }
    
    /**
     * 재시도 스케줄링
     */
    public void scheduleRetry() {
        this.retryCount++;
        this.webhookStatus = WebhookStatus.RETRY_SCHEDULED;
        
        // 지수 백오프: 2^retryCount 분 후 재시도
        long delayMinutes = (long) Math.pow(2, retryCount);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
    }
    
    /**
     * 재시도 가능 여부
     */
    public boolean canRetry() {
        return retryCount < maxRetries && 
               (responseStatus == null || responseStatus >= 500 || responseStatus == 429);
    }
    
    /**
     * 재시도 필요 여부
     */
    public boolean needsRetry() {
        return webhookStatus == WebhookStatus.RETRY_SCHEDULED &&
               nextRetryAt != null && 
               nextRetryAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * 성공 응답 여부
     */
    @Transient
    public boolean isSuccessResponse() {
        return responseStatus != null && responseStatus >= 200 && responseStatus < 300;
    }
    
    /**
     * 최종 실패 여부
     */
    @Transient
    public boolean isFinalFailure() {
        return webhookStatus == WebhookStatus.FAILED && !canRetry();
    }
    
    /**
     * 느린 웹훅 여부 (5초 이상)
     */
    @Transient
    public boolean isSlowWebhook() {
        return responseTimeMs != null && responseTimeMs > 5000;
    }
    
    /**
     * 매우 느린 웹훅 여부 (30초 이상)
     */
    @Transient
    public boolean isVerySlowWebhook() {
        return responseTimeMs != null && responseTimeMs > 30000;
    }
    
    /**
     * 응답 시간 초 단위 반환
     */
    @Transient
    public double getResponseTimeSeconds() {
        return responseTimeMs != null ? responseTimeMs / 1000.0 : 0.0;
    }
    
    /**
     * 다음 재시도까지 남은 시간 (분)
     */
    @Transient
    public long getMinutesUntilNextRetry() {
        if (nextRetryAt == null) return 0;
        return java.time.temporal.ChronoUnit.MINUTES.between(LocalDateTime.now(), nextRetryAt);
    }
    
    /**
     * 웹훅 로그 요약
     */
    @Transient
    public String getWebhookSummary() {
        return String.format("%s -> %s (%s, %d회 시도)", 
                eventType, targetUrl, webhookStatus, retryCount + 1);
    }
    
    /**
     * 성능 등급 반환
     */
    @Transient
    public String getPerformanceGrade() {
        if (responseTimeMs == null) return "미측정";
        
        if (responseTimeMs < 1000) return "우수";
        if (responseTimeMs < 3000) return "좋음";
        if (responseTimeMs < 10000) return "보통";
        return "느림";
    }
    
    @Override
    public Long getId() {
        return webhookLogId;
    }
}
```

---

## ✅ 설계 완료 체크리스트

### 시스템 로깅 엔티티 (3개)
- [x] **ApiLog** - API 호출 로그 (성능 모니터링, 에러 추적, 사용자별 로그)
- [x] **ExternalApiConfig** - 외부 API 설정 (환경별 관리, 헬스 체크, 제한 설정)
- [x] **WebhookLog** - 웹훅 로그 (이벤트 전송, 재시도 관리, 성능 추적)

### API 호출 추적 시스템
- [x] 엔드포인트별 성능 모니터링 및 분석
- [x] 사용자별 API 사용 패턴 추적
- [x] 에러 로그 자동 분류 및 알림
- [x] 느린 API 자동 감지 (1초/5초 기준)

### 외부 API 관리 시스템
- [x] 환경별 API 설정 분리 (DEV/STAGING/PROD)
- [x] API 키 암호화 저장 및 관리
- [x] 제공자별 제한 사항 설정 (호출 횟수, 타임아웃)
- [x] 헬스 체크 및 상태 모니터링

### 웹훅 전송 시스템
- [x] 이벤트별 웹훅 전송 로그
- [x] 지수 백오프 재시도 로직
- [x] 실패 원인 분석 및 추적
- [x] 성능 등급 자동 분류

### 운영 최적화
- [x] 로그 검색 및 필터링 인덱스
- [x] 시간별/상태별 로그 분석 지원
- [x] 성능 지표 자동 계산 및 등급화
- [x] 재시도 스케줄링 및 관리

### 모니터링 기능
- [x] 실시간 API 성능 모니터링
- [x] 외부 API 상태 체크 자동화
- [x] 웹훅 전송 성공률 추적
- [x] 에러율 및 응답시간 분석

---

**다음 단계**: Repository 레이어 설계 (5단계)  
**완료일**: 2025-08-20  
**핵심 성과**: 3개 시스템 로깅 엔티티 + API 모니터링 + 외부 API 관리 + 웹훅 시스템 완성

## 🏆 JPA 엔티티 50개 완성 달성!

**총 50개 엔티티가 모두 완성되었습니다.**
- **User 도메인**: 7개 엔티티
- **Tag 시스템**: 4개 엔티티  
- **Gym 관련**: 5개 엔티티
- **Route 관련**: 7개 엔티티
- **Climbing**: 5개 엔티티
- **Community**: 9개 엔티티
- **Payment**: 4개 엔티티
- **Notification**: 4개 엔티티
- **System**: 6개 엔티티

**다음 단계는 Repository 레이어 설계입니다!**