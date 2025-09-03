# GlobalExceptionHandler ErrorResponse 통합

## 📋 ErrorResponse DTO 클래스

### 기본 ErrorResponse
```java
package com.routepick.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 표준 에러 응답 DTO
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    private String errorCode;           // 에러 코드 (AUTH-001, USER-001 등)
    private String userMessage;        // 한국어 사용자 메시지
    private String developerMessage;   // 영문 개발자 메시지
    private LocalDateTime timestamp;   // 에러 발생 시각
    private String path;               // 요청 경로
    private String traceId;            // 추적 ID
    private List<String> supportedMethods; // 지원되는 HTTP 메서드 (해당 시)
    
    // 보안 관련 추가 정보 (필요 시)
    private String securityLevel;      // 보안 수준 (HIGH, MEDIUM, LOW)
    private String ipAddress;          // 클라이언트 IP (마스킹됨)
    private String userAgent;          // User Agent (필요 시)
}
```

### ValidationErrorResponse
```java
package com.routepick.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 검증 에러 응답 DTO
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationErrorResponse {
    
    private String errorCode;           // 에러 코드
    private String userMessage;        // 한국어 사용자 메시지  
    private String developerMessage;   // 영문 개발자 메시지
    private LocalDateTime timestamp;   // 에러 발생 시각
    private String path;               // 요청 경로
    private List<FieldErrorDetail> fieldErrors; // 필드별 에러 상세
    private int totalErrors;           // 총 에러 개수
}

/**
 * 필드 에러 상세 정보
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldErrorDetail {
    
    private String field;              // 필드명
    private String rejectedValue;      // 입력된 값 (마스킹됨)
    private String message;            // 에러 메시지
    private String code;               // 에러 코드
    private String expectedFormat;     // 기대되는 형식
    private List<String> suggestions;  // 수정 제안사항
}
```

### RateLimitErrorResponse
```java
package com.routepick.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Rate Limiting 에러 응답 DTO
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateLimitErrorResponse {
    
    private String errorCode;           // 에러 코드
    private String userMessage;        // 한국어 사용자 메시지
    private String developerMessage;   // 영문 개발자 메시지
    private LocalDateTime timestamp;   // 에러 발생 시각
    private String path;               // 요청 경로
    private RateLimitInfo rateLimitInfo; // Rate Limit 상세 정보
    private long retryAfterSeconds;    // 재시도 가능 시간 (초)
}

/**
 * Rate Limit 상세 정보
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateLimitInfo {
    
    private int limit;                 // 제한 횟수
    private int remaining;             // 남은 횟수
    private long resetTime;            // 리셋 시간 (epoch)
    private long retryAfterSeconds;    // 재시도 가능 시간 (초)
    private String limitType;          // 제한 타입 (IP, USER, API)
    private String rateLimitKey;       // Rate Limit 키 (마스킹됨)
}
```

---

## ⚙️ Spring Boot 통합 설정

### application.yml 설정
```yaml
# 예외 처리 관련 설정
app:
  security:
    mask-sensitive-data: ${MASK_SENSITIVE_DATA:true}
    detailed-error-info: ${DETAILED_ERROR_INFO:false}
    rate-limit:
      enabled: true
      default-limit: 100
      default-window: PT1M  # 1분
  monitoring:
    security-alert-threshold: ${SECURITY_ALERT_THRESHOLD:5}
    system-error-threshold: ${SYSTEM_ERROR_THRESHOLD:10}
    slack:
      webhook-url: ${SLACK_WEBHOOK_URL:}
      security-channel: ${SLACK_SECURITY_CHANNEL:#security-alerts}
      system-channel: ${SLACK_SYSTEM_CHANNEL:#system-alerts}

# 환경별 로깅 설정
logging:
  level:
    com.routepick.exception: INFO
    com.routepick.security: WARN
    com.routepick.monitoring: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%logger{36}] - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%logger{36}] [%X{traceId}] - %msg%n"

---
# 개발 환경
spring:
  profiles: local
app:
  security:
    detailed-error-info: true  # 개발 환경에서는 상세 에러 정보 표시
logging:
  level:
    com.routepick.exception: DEBUG
    com.routepick.security: DEBUG

---
# 스테이징 환경  
spring:
  profiles: staging
app:
  security:
    detailed-error-info: false
logging:
  level:
    com.routepick.exception: INFO
    com.routepick.security: WARN

---
# 운영 환경
spring:
  profiles: production
app:
  security:
    mask-sensitive-data: true
    detailed-error-info: false
  monitoring:
    security-alert-threshold: 3  # 운영에서는 더 엄격하게
    system-error-threshold: 5
logging:
  level:
    com.routepick.exception: WARN
    com.routepick.security: ERROR
  file:
    name: logs/routepick-errors.log
    max-size: 100MB
    max-history: 30
```

---

## ✅ Step 3-3a 완료 체크리스트

### 🔧 GlobalExceptionHandler 구현
- [x] **@ControllerAdvice**: Spring Boot 전역 예외 처리 구현
- [x] **8개 도메인 예외**: 각 커스텀 예외별 @ExceptionHandler 구현
- [x] **HTTP 상태 매핑**: ErrorCode별 적절한 HTTP 상태 코드 자동 매핑
- [x] **ApiResponse 연동**: 표준 응답 형식 완전 적용
- [x] **Spring 표준 예외**: @Valid, @Validated, AccessDenied 등 처리

### 📋 예외 응답 구조
- [x] **ErrorResponse DTO**: 기본 에러 응답 구조 설계
- [x] **ValidationErrorResponse**: 필드별 검증 에러 정보 포함
- [x] **RateLimitErrorResponse**: Rate Limiting 상세 정보 포함
- [x] **FieldErrorDetail**: 필드별 상세 에러 정보
- [x] **타임스탬프/경로**: 모든 응답에 요청 정보 포함

### ⚙️ Spring Boot 통합
- [x] **application.yml**: 환경별 에러 설정 (local/staging/production)
- [x] **로깅 레벨**: 환경별 차등 로깅 레벨 설정
- [x] **보안 설정**: 민감정보 마스킹, 상세 에러 정보 환경별 제어
- [x] **Rate Limit 설정**: Redis 기반 분산 Rate Limiting
- [x] **모니터링 설정**: Slack 웹훅, 알림 임계값 설정

---

**다음 단계**: step3-3b_security_features.md (보안 강화 기능)  
**관련 파일**: step3-3c_monitoring_testing.md (모니터링 및 테스트)

*분할된 파일: step3-3a_global_handler_core.md → step3-3a2_error_response_integration.md*  
*내용: ErrorResponse DTO & Spring Boot 통합*  
*라인 수: 241줄*