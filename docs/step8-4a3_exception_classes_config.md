# 예외 클래스 및 설정 구현

## 개요
RoutePickr 시스템의 커스텀 예외 클래스들과 예외 처리 관련 설정을 구현합니다.

## 기본 예외 클래스

### BaseException

```java
package com.routepick.common.exception;

import com.routepick.common.exception.enums.ErrorCode;
import lombok.Getter;
import java.util.Map;

/**
 * 모든 커스텀 예외의 기본 클래스
 */
@Getter
public abstract class BaseException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Map<String, Object> details;
    
    protected BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }
    
    protected BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }
    
    protected BaseException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
    
    protected BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }
    
    protected BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }
}
```

## 도메인별 예외 클래스

### UserException

```java
package com.routepick.common.exception;

import com.routepick.common.exception.enums.ErrorCode;
import lombok.Getter;
import java.util.Map;

/**
 * 사용자 관련 예외
 */
@Getter
public class UserException extends BaseException {
    
    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public UserException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public UserException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode, errorCode.getMessage(), details);
    }
    
    // 자주 사용되는 예외 생성 메서드
    public static UserException notFound(Long userId) {
        return new UserException(
            ErrorCode.USER_NOT_FOUND,
            String.format("사용자를 찾을 수 없습니다. ID: %d", userId)
        );
    }
    
    public static UserException duplicateEmail(String email) {
        return new UserException(
            ErrorCode.DUPLICATE_EMAIL,
            String.format("이미 사용 중인 이메일입니다: %s", email)
        );
    }
    
    public static UserException invalidStatus(String status) {
        return new UserException(
            ErrorCode.INVALID_USER_STATUS,
            String.format("유효하지 않은 사용자 상태입니다: %s", status)
        );
    }
}
```

### AuthException

```java
package com.routepick.common.exception;

import com.routepick.common.exception.enums.ErrorCode;
import lombok.Getter;

/**
 * 인증/인가 관련 예외
 */
@Getter
public class AuthException extends BaseException {
    
    private final String attemptedUsername;
    private final String ipAddress;
    
    public AuthException(ErrorCode errorCode) {
        super(errorCode);
        this.attemptedUsername = null;
        this.ipAddress = null;
    }
    
    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
        this.attemptedUsername = null;
        this.ipAddress = null;
    }
    
    public AuthException(ErrorCode errorCode, String attemptedUsername, String ipAddress) {
        super(errorCode);
        this.attemptedUsername = attemptedUsername;
        this.ipAddress = ipAddress;
    }
    
    // 인증 실패 관련 예외
    public static AuthException invalidCredentials(String username, String ip) {
        return new AuthException(ErrorCode.INVALID_CREDENTIALS, username, ip);
    }
    
    public static AuthException tokenExpired() {
        return new AuthException(ErrorCode.TOKEN_EXPIRED, "토큰이 만료되었습니다");
    }
    
    public static AuthException invalidToken() {
        return new AuthException(ErrorCode.INVALID_TOKEN, "유효하지 않은 토큰입니다");
    }
    
    public static AuthException accessDenied() {
        return new AuthException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다");
    }
}
```

### PaymentException

```java
package com.routepick.common.exception;

import com.routepick.common.exception.enums.ErrorCode;
import lombok.Getter;
import java.math.BigDecimal;

/**
 * 결제 관련 예외
 */
@Getter
public class PaymentException extends BaseException {
    
    private final String paymentId;
    private final BigDecimal amount;
    private final String paymentMethod;
    
    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
        this.paymentId = null;
        this.amount = null;
        this.paymentMethod = null;
    }
    
    public PaymentException(ErrorCode errorCode, String paymentId, BigDecimal amount) {
        super(errorCode);
        this.paymentId = paymentId;
        this.amount = amount;
        this.paymentMethod = null;
    }
    
    public PaymentException(ErrorCode errorCode, String message, String paymentId, 
                           BigDecimal amount, String paymentMethod) {
        super(errorCode, message);
        this.paymentId = paymentId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
    }
    
    // 결제 실패 예외 생성
    public static PaymentException paymentFailed(String paymentId, BigDecimal amount, String reason) {
        return new PaymentException(
            ErrorCode.PAYMENT_FAILED,
            String.format("결제 실패 - ID: %s, 금액: %s, 사유: %s", paymentId, amount, reason),
            paymentId,
            amount,
            null
        );
    }
    
    public static PaymentException insufficientBalance(BigDecimal required, BigDecimal available) {
        return new PaymentException(
            ErrorCode.INSUFFICIENT_BALANCE,
            String.format("잔액 부족 - 필요: %s, 가용: %s", required, available),
            null,
            required,
            null
        );
    }
}
```

### DataException

```java
package com.routepick.common.exception;

import com.routepick.common.exception.enums.ErrorCode;
import lombok.Getter;

/**
 * 데이터 관련 예외
 */
@Getter
public class DataException extends BaseException {
    
    private final String entityType;
    private final Object entityId;
    
    public DataException(ErrorCode errorCode) {
        super(errorCode);
        this.entityType = null;
        this.entityId = null;
    }
    
    public DataException(ErrorCode errorCode, String entityType, Object entityId) {
        super(errorCode, 
              String.format("%s를 찾을 수 없습니다. ID: %s", entityType, entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }
    
    // 데이터 관련 예외
    public static DataException notFound(String entityType, Object id) {
        return new DataException(ErrorCode.DATA_NOT_FOUND, entityType, id);
    }
    
    public static DataException duplicateEntry(String entityType, String field) {
        return new DataException(
            ErrorCode.DUPLICATE_DATA,
            String.format("%s의 %s가 이미 존재합니다", entityType, field)
        );
    }
    
    public static DataException invalidData(String message) {
        return new DataException(ErrorCode.INVALID_DATA, message);
    }
}
```

### ValidationException

```java
package com.routepick.common.exception;

import com.routepick.common.exception.enums.ErrorCode;
import lombok.Getter;
import java.util.Map;

/**
 * 검증 관련 예외
 */
@Getter
public class ValidationException extends BaseException {
    
    private final Map<String, String> fieldErrors;
    
    public ValidationException(ErrorCode errorCode, Map<String, String> fieldErrors) {
        super(errorCode);
        this.fieldErrors = fieldErrors;
    }
    
    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.fieldErrors = fieldErrors;
    }
    
    // 검증 예외 생성
    public static ValidationException invalidField(String field, String message) {
        return new ValidationException(
            ErrorCode.VALIDATION_ERROR,
            Map.of(field, message)
        );
    }
    
    public static ValidationException multipleErrors(Map<String, String> errors) {
        return new ValidationException(ErrorCode.VALIDATION_ERROR, errors);
    }
}
```

### SystemException

```java
package com.routepick.common.exception;

import com.routepick.common.exception.enums.ErrorCode;
import lombok.Getter;

/**
 * 시스템 관련 예외
 */
@Getter
public class SystemException extends BaseException {
    
    private final String component;
    private final String operation;
    
    public SystemException(ErrorCode errorCode) {
        super(errorCode);
        this.component = null;
        this.operation = null;
    }
    
    public SystemException(ErrorCode errorCode, String component, String operation) {
        super(errorCode, 
              String.format("시스템 오류 - 컴포넌트: %s, 작업: %s", component, operation));
        this.component = component;
        this.operation = operation;
    }
    
    // 시스템 예외 생성
    public static SystemException serviceUnavailable(String service) {
        return new SystemException(
            ErrorCode.SERVICE_UNAVAILABLE,
            service,
            "SERVICE_CALL"
        );
    }
    
    public static SystemException externalApiError(String api, String message) {
        return new SystemException(
            ErrorCode.EXTERNAL_API_ERROR,
            String.format("외부 API 오류 - %s: %s", api, message)
        );
    }
    
    public static SystemException databaseError(String operation) {
        return new SystemException(
            ErrorCode.DATABASE_ERROR,
            "DATABASE",
            operation
        );
    }
}
```

## 예외 처리 설정

### ExceptionHandlerConfig

```java
package com.routepick.common.exception.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 예외 처리 설정
 */
@Configuration
public class ExceptionHandlerConfig implements WebMvcConfigurer {
    
    /**
     * 예외 처리 우선순위 설정
     */
    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class SecurityExceptionHandler {
        // 보안 관련 예외를 최우선으로 처리
    }
    
    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public static class ValidationExceptionHandler {
        // 검증 예외 처리
    }
    
    @RestControllerAdvice
    @Order(Ordered.LOWEST_PRECEDENCE)
    public static class GeneralExceptionHandler {
        // 일반 예외 처리 (GlobalExceptionHandler)
    }
}
```

### ExceptionMonitoringConfig

```java
package com.routepick.common.exception.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;

/**
 * 예외 모니터링 설정
 */
@Configuration
public class ExceptionMonitoringConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> exceptionMetrics() {
        return registry -> {
            // 예외 발생 카운터
            Counter.builder("application.exceptions")
                    .description("Total number of exceptions")
                    .register(registry);
            
            // 예외 타입별 카운터
            Counter.builder("application.exceptions.by.type")
                    .description("Exceptions by type")
                    .register(registry);
            
            // 심각도별 카운터
            Counter.builder("application.exceptions.by.severity")
                    .description("Exceptions by severity")
                    .register(registry);
        };
    }
    
    @Bean
    public ExceptionMetricsAspect exceptionMetricsAspect(MeterRegistry meterRegistry) {
        return new ExceptionMetricsAspect(meterRegistry);
    }
}
```

### ExceptionLoggingConfig

```java
package com.routepick.common.exception.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 예외 로깅 설정
 */
@Configuration
public class ExceptionLoggingConfig {
    
    @Bean
    @ConditionalOnProperty(name = "app.exception.detailed-logging", havingValue = "true")
    public ExceptionLoggingCustomizer detailedExceptionLogging() {
        return new ExceptionLoggingCustomizer();
    }
    
    public static class ExceptionLoggingCustomizer {
        
        public ExceptionLoggingCustomizer() {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // 예외 처리 관련 로거 설정
            Logger exceptionLogger = loggerContext.getLogger("com.routepick.common.exception");
            exceptionLogger.setLevel(Level.DEBUG);
            
            // 보안 예외 로거 (별도 파일로 저장)
            Logger securityLogger = loggerContext.getLogger("com.routepick.security.exception");
            securityLogger.setLevel(Level.WARN);
            securityLogger.setAdditive(false); // 상위 로거로 전파하지 않음
        }
    }
}
```

## 예외 처리 유틸리티

### ExceptionUtils

```java
package com.routepick.common.exception.util;

import org.springframework.stereotype.Component;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

/**
 * 예외 처리 유틸리티
 */
@Component
public class ExceptionUtils {
    
    /**
     * 스택 트레이스를 문자열로 변환
     */
    public static String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * 근본 원인 예외 찾기
     */
    public static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
    
    /**
     * 예외 체인에서 특정 타입 찾기
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable cause = throwable;
        while (cause != null) {
            if (type.isInstance(cause)) {
                return (T) cause;
            }
            cause = cause.getCause();
        }
        return null;
    }
    
    /**
     * 민감한 정보 마스킹
     */
    public static String maskSensitiveInfo(String message) {
        if (message == null) {
            return null;
        }
        
        // 이메일 마스킹
        message = message.replaceAll(
            "([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})",
            "$1****@$2"
        );
        
        // 전화번호 마스킹
        message = message.replaceAll(
            "(\\d{3})[- ]?(\\d{3,4})[- ]?(\\d{4})",
            "$1-****-$3"
        );
        
        // 카드번호 마스킹
        message = message.replaceAll(
            "(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})",
            "$1-****-****-$4"
        );
        
        return message;
    }
    
    /**
     * 예외 메시지 안전하게 가져오기
     */
    public static String getSafeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            message = throwable.getClass().getSimpleName();
        }
        return maskSensitiveInfo(message);
    }
}
```

## 설정 파일

### application.yml

```yaml
# 예외 처리 설정
app:
  exception:
    # 상세 로깅 활성화
    detailed-logging: ${EXCEPTION_DETAILED_LOGGING:false}
    
    # 스택 트레이스 포함 여부
    include-stacktrace: ${INCLUDE_STACKTRACE:false}
    
    # 민감정보 마스킹
    mask-sensitive-data: true
    
    # 에러 응답 상세 레벨
    response-detail-level: ${ERROR_DETAIL_LEVEL:MINIMAL}
    
    # 알림 설정
    notification:
      enabled: true
      critical-threshold: 10  # 10회 이상 발생 시 알림
      email: admin@routepick.com
      slack-webhook: ${SLACK_WEBHOOK_URL:}
    
    # 모니터링
    monitoring:
      enabled: true
      metrics-prefix: routepick.exceptions
      
# 로깅 설정
logging:
  level:
    com.routepick.common.exception: INFO
    com.routepick.security.exception: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
  file:
    name: logs/exceptions.log
    max-size: 10MB
    max-history: 30
```

## 테스트 코드

```java
package com.routepick.common.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

class ExceptionClassesTest {
    
    @Test
    @DisplayName("UserException 생성 테스트")
    void createUserException() {
        // given
        Long userId = 1L;
        
        // when
        UserException exception = UserException.notFound(userId);
        
        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(exception.getMessage()).contains("1");
    }
    
    @Test
    @DisplayName("AuthException 생성 테스트")
    void createAuthException() {
        // when
        AuthException exception = AuthException.tokenExpired();
        
        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
        assertThat(exception.getMessage()).contains("만료");
    }
    
    @Test
    @DisplayName("PaymentException 생성 테스트")
    void createPaymentException() {
        // given
        String paymentId = "PAY-123";
        BigDecimal amount = new BigDecimal("10000");
        
        // when
        PaymentException exception = PaymentException.paymentFailed(
            paymentId, amount, "카드 한도 초과"
        );
        
        // then
        assertThat(exception.getPaymentId()).isEqualTo(paymentId);
        assertThat(exception.getAmount()).isEqualTo(amount);
        assertThat(exception.getMessage()).contains("카드 한도 초과");
    }
    
    @Test
    @DisplayName("민감정보 마스킹 테스트")
    void maskSensitiveInfo() {
        // given
        String message = "이메일: test@example.com, 전화: 010-1234-5678";
        
        // when
        String masked = ExceptionUtils.maskSensitiveInfo(message);
        
        // then
        assertThat(masked).contains("test****@example.com");
        assertThat(masked).contains("010-****-5678");
    }
}
```

이 구현은 RoutePickr 시스템의 체계적인 예외 처리를 위한 완전한 구조를 제공합니다.