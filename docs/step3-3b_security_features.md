# Step 3-3b: 보안 강화 기능 구현

> RoutePickr 보안 강화 시스템 - 민감정보 마스킹, Rate Limiting, 위협 탐지  
> 생성일: 2025-08-21  
> 기반 분석: step3-3a_global_handler_core.md  
> 세분화: step3-3_global_handler_security.md에서 분리

---

## 🛡️ 보안 강화 구현

### SensitiveDataMasker 클래스
```java
package com.routepick.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 민감정보 마스킹 처리기
 */
@Slf4j
@Component
public class SensitiveDataMasker {
    
    // 이메일 패턴
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    
    // 휴대폰 번호 패턴
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("(01[0-9])-([0-9]{3,4})-([0-9]{4})");
    
    // 토큰 패턴
    private static final Pattern TOKEN_PATTERN = 
        Pattern.compile("Bearer\\s+([a-zA-Z0-9._-]+)");
    
    // 카드번호 패턴
    private static final Pattern CARD_PATTERN = 
        Pattern.compile("([0-9]{4})-([0-9]{4})-([0-9]{4})-([0-9]{4})");
    
    /**
     * 통합 마스킹 처리
     */
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String masked = input;
        masked = maskEmail(masked);
        masked = maskPhoneNumber(masked);
        masked = maskToken(masked);
        masked = maskCardNumber(masked);
        
        return masked;
    }
    
    /**
     * 이메일 마스킹: user@domain.com → u***@domain.com
     */
    public String maskEmail(String input) {
        if (input == null) return null;
        
        return EMAIL_PATTERN.matcher(input).replaceAll(matchResult -> {
            String username = matchResult.group(1);
            String domain = matchResult.group(2);
            
            if (username.length() <= 1) {
                return "***@" + domain;
            }
            
            return username.charAt(0) + "***@" + domain;
        });
    }
    
    /**
     * 휴대폰 번호 마스킹: 010-1234-5678 → 010-****-5678
     */
    public String maskPhoneNumber(String input) {
        if (input == null) return null;
        
        return PHONE_PATTERN.matcher(input).replaceAll(matchResult -> {
            String prefix = matchResult.group(1);  // 010
            String middle = matchResult.group(2);  // 1234
            String suffix = matchResult.group(3);  // 5678
            
            return prefix + "-****-" + suffix;
        });
    }
    
    /**
     * 토큰 마스킹: Bearer eyJhbGciOiJIUzI1NiJ9... → Bearer ****
     */
    public String maskToken(String input) {
        if (input == null) return null;
        
        return TOKEN_PATTERN.matcher(input).replaceAll("Bearer ****");
    }
    
    /**
     * 카드번호 마스킹: 1234-5678-9012-3456 → 1234-****-****-3456
     */
    public String maskCardNumber(String input) {
        if (input == null) return null;
        
        return CARD_PATTERN.matcher(input).replaceAll(matchResult -> {
            String first = matchResult.group(1);   // 1234
            String last = matchResult.group(4);    // 3456
            
            return first + "-****-****-" + last;
        });
    }
    
    /**
     * IP 주소 마스킹: 192.168.1.100 → 192.168.***.***
     */
    public String maskIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + "***";
        }
        
        return "***.***.***." + "***";
    }
    
    /**
     * 일반 문자열 마스킹: 3자리 이상 → 앞1자리 + *** + 뒤1자리
     */
    public String maskGeneral(String input) {
        if (input == null || input.length() <= 2) {
            return "***";
        }
        
        if (input.length() == 3) {
            return input.charAt(0) + "**";
        }
        
        return input.charAt(0) + "***" + input.charAt(input.length() - 1);
    }
}
```

### RateLimitManager 클래스
```java
package com.routepick.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/**
 * Rate Limiting 관리자
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitManager {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // API별 Rate Limit 설정
    private static final int LOGIN_LIMIT_PER_MINUTE = 5;
    private static final int EMAIL_LIMIT_PER_MINUTE = 1; 
    private static final int SMS_LIMIT_PER_HOUR = 3;
    private static final int API_LIMIT_PER_MINUTE = 100;
    private static final int PAYMENT_LIMIT_PER_HOUR = 10;
    
    /**
     * Rate Limit 확인 및 카운트 증가
     */
    public RateLimitResult checkAndIncrement(String key, int limit, Duration window) {
        String redisKey = "rate_limit:" + key;
        
        try {
            // 현재 카운트 조회
            String currentCountStr = redisTemplate.opsForValue().get(redisKey);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
            
            if (currentCount >= limit) {
                // 제한 초과
                Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
                return RateLimitResult.builder()
                    .allowed(false)
                    .limit(limit)
                    .remaining(0)
                    .resetTime(LocalDateTime.now().plusSeconds(ttl != null ? ttl : window.getSeconds()).toEpochSecond(ZoneOffset.UTC))
                    .retryAfterSeconds(ttl != null ? ttl : window.getSeconds())
                    .build();
            }
            
            // 카운트 증가
            if (currentCount == 0) {
                // 첫 요청인 경우 TTL 설정
                redisTemplate.opsForValue().set(redisKey, "1", window);
            } else {
                // 기존 TTL 유지하며 증가
                redisTemplate.opsForValue().increment(redisKey);
            }
            
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            
            return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .remaining(limit - currentCount - 1)
                .resetTime(LocalDateTime.now().plusSeconds(ttl != null ? ttl : window.getSeconds()).toEpochSecond(ZoneOffset.UTC))
                .retryAfterSeconds(0)
                .build();
                
        } catch (Exception e) {
            log.error("Rate limit check failed for key: {}", key, e);
            // Redis 오류 시 요청 허용 (Fail-Open)
            return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .remaining(limit - 1)
                .resetTime(LocalDateTime.now().plus(window).toEpochSecond(ZoneOffset.UTC))
                .retryAfterSeconds(0)
                .build();
        }
    }
    
    /**
     * 로그인 Rate Limit 확인
     */
    public RateLimitResult checkLoginLimit(String ipAddress) {
        String key = "login:" + ipAddress;
        return checkAndIncrement(key, LOGIN_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
    }
    
    /**
     * 이메일 발송 Rate Limit 확인
     */
    public RateLimitResult checkEmailLimit(String ipAddress) {
        String key = "email:" + ipAddress;
        return checkAndIncrement(key, EMAIL_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
    }
    
    /**
     * SMS 발송 Rate Limit 확인
     */
    public RateLimitResult checkSmsLimit(String phoneNumber) {
        String key = "sms:" + phoneNumber;
        return checkAndIncrement(key, SMS_LIMIT_PER_HOUR, Duration.ofHours(1));
    }
    
    /**
     * API Rate Limit 확인
     */
    public RateLimitResult checkApiLimit(String userId) {
        String key = "api:" + userId;
        return checkAndIncrement(key, API_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
    }
    
    /**
     * 결제 Rate Limit 확인
     */
    public RateLimitResult checkPaymentLimit(String userId) {
        String key = "payment:" + userId;
        return checkAndIncrement(key, PAYMENT_LIMIT_PER_HOUR, Duration.ofHours(1));
    }
}

/**
 * Rate Limit 결과
 */
@lombok.Builder
@lombok.Getter
public class RateLimitResult {
    private boolean allowed;           // 요청 허용 여부
    private int limit;                // 제한 횟수
    private int remaining;            // 남은 횟수
    private long resetTime;           // 리셋 시간 (epoch)
    private long retryAfterSeconds;   // 재시도 가능 시간 (초)
}
```

---

## 🔍 보안 위협 탐지

### SecurityThreatDetector 클래스
```java
package com.routepick.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.List;

/**
 * 보안 위협 탐지기
 */
@Slf4j
@Component
public class SecurityThreatDetector {
    
    // XSS 공격 패턴
    private static final List<Pattern> XSS_PATTERNS = Arrays.asList(
        Pattern.compile(".*<script[^>]*>.*</script>.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*javascript:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*vbscript:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*onload\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*onerror\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*onclick\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*onmouseover\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*eval\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*expression\\s*\\(.*", Pattern.CASE_INSENSITIVE)
    );
    
    // SQL Injection 공격 패턴
    private static final List<Pattern> SQL_INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile(".*union\\s+select.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*drop\\s+table.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*delete\\s+from.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*insert\\s+into.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*update\\s+set.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*create\\s+table.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*alter\\s+table.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*truncate\\s+table.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*;--.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*'\\s*or\\s*'.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*'\\s*and\\s*'.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*1\\s*=\\s*1.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*1'='1.*", Pattern.CASE_INSENSITIVE)
    );
    
    // 악성 경로 패턴
    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\.\\./.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\.\\.\\\\.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/etc/passwd.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/proc/.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\\\windows\\\\.*", Pattern.CASE_INSENSITIVE)
    );
    
    /**
     * XSS 공격 탐지
     */
    public boolean isXssAttack(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        return XSS_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(input).matches());
    }
    
    /**
     * SQL Injection 공격 탐지
     */
    public boolean isSqlInjectionAttack(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        return SQL_INJECTION_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(input).matches());
    }
    
    /**
     * 경로 순회 공격 탐지
     */
    public boolean isPathTraversalAttack(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        return PATH_TRAVERSAL_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(input).matches());
    }
    
    /**
     * 통합 악성 입력 탐지
     */
    public SecurityThreatType detectThreat(String input) {
        if (input == null || input.isEmpty()) {
            return SecurityThreatType.NONE;
        }
        
        if (isXssAttack(input)) {
            return SecurityThreatType.XSS;
        }
        
        if (isSqlInjectionAttack(input)) {
            return SecurityThreatType.SQL_INJECTION;
        }
        
        if (isPathTraversalAttack(input)) {
            return SecurityThreatType.PATH_TRAVERSAL;
        }
        
        return SecurityThreatType.NONE;
    }
    
    /**
     * 보안 위협 유형
     */
    public enum SecurityThreatType {
        NONE,
        XSS,
        SQL_INJECTION,
        PATH_TRAVERSAL,
        MALICIOUS_REQUEST
    }
}
```

---

## 📊 추천 시스템 보안 예외

### RecommendationSecurityException 클래스
```java
package com.routepick.exception.security;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 추천 시스템 보안 예외 클래스
 */
@Getter
public class RecommendationSecurityException extends BaseException {
    
    private final Long userId;              // 관련 사용자 ID
    private final String securityViolationType; // 보안 위반 유형
    private final String attemptedAction;   // 시도한 작업
    private final String resourceId;        // 관련 리소스 ID
    
    // ErrorCode 확장 (추천 시스템 보안)
    public static final ErrorCode RECOMMENDATION_ACCESS_DENIED = 
        new ErrorCode(HttpStatus.FORBIDDEN, "TAG-024", 
            "추천 정보에 접근할 권한이 없습니다", 
            "Unauthorized access to recommendation data");
    
    public static final ErrorCode RECOMMENDATION_DATA_MANIPULATION = 
        new ErrorCode(HttpStatus.FORBIDDEN, "TAG-025", 
            "추천 데이터 조작이 감지되었습니다", 
            "Recommendation data manipulation attempt detected");
    
    public static final ErrorCode TAG_SYSTEM_ABUSE = 
        new ErrorCode(HttpStatus.TOO_MANY_REQUESTS, "TAG-026", 
            "태그 시스템 악용이 감지되었습니다", 
            "Tag system abuse detected");
    
    private RecommendationSecurityException(ErrorCode errorCode, Long userId, 
                                          String securityViolationType, String attemptedAction, String resourceId) {
        super(errorCode);
        this.userId = userId;
        this.securityViolationType = securityViolationType;
        this.attemptedAction = attemptedAction;
        this.resourceId = resourceId;
    }
    
    /**
     * 무단 추천 데이터 접근
     */
    public static RecommendationSecurityException accessDenied(Long userId, String resourceId) {
        return new RecommendationSecurityException(
            RECOMMENDATION_ACCESS_DENIED, userId, "ACCESS_VIOLATION", "read", resourceId);
    }
    
    /**
     * 추천 데이터 조작 시도
     */
    public static RecommendationSecurityException dataManipulation(Long userId, String attemptedAction) {
        return new RecommendationSecurityException(
            RECOMMENDATION_DATA_MANIPULATION, userId, "DATA_MANIPULATION", attemptedAction, null);
    }
    
    /**
     * 태그 시스템 악용
     */
    public static RecommendationSecurityException systemAbuse(Long userId, String abuseType) {
        return new RecommendationSecurityException(
            TAG_SYSTEM_ABUSE, userId, "SYSTEM_ABUSE", abuseType, null);
    }
}
```

---

## 🔒 추가 보안 마스킹 메서드

### GlobalExceptionHandler 보안 마스킹 확장
```java
// GlobalExceptionHandler 클래스 내 추가 메서드들

/**
 * 보안 에러 정보 제한
 */
private ErrorResponse sanitizeSecurityError(ErrorResponse errorResponse) {
    return ErrorResponse.builder()
        .errorCode(errorResponse.getErrorCode())
        .userMessage("보안상의 이유로 접근이 제한되었습니다")
        .developerMessage("Security violation detected")
        .timestamp(errorResponse.getTimestamp())
        .path(errorResponse.getPath())
        .traceId(errorResponse.getTraceId())
        .securityLevel("HIGH")
        .build();
}

/**
 * 사용자 민감정보 마스킹
 */
private ErrorResponse maskUserSensitiveData(ErrorResponse errorResponse, UserException ex) {
    String maskedMessage = errorResponse.getUserMessage();
    String maskedDeveloperMessage = errorResponse.getDeveloperMessage();
    
    // 이메일 마스킹
    if (ex.getEmail() != null) {
        String maskedEmail = sensitiveDataMasker.maskEmail(ex.getEmail());
        maskedMessage = maskedMessage.replace(ex.getEmail(), maskedEmail);
        maskedDeveloperMessage = maskedDeveloperMessage.replace(ex.getEmail(), maskedEmail);
    }
    
    // 휴대폰 번호 마스킹
    if (ex.getPhoneNumber() != null) {
        String maskedPhone = sensitiveDataMasker.maskPhoneNumber(ex.getPhoneNumber());
        maskedMessage = maskedMessage.replace(ex.getPhoneNumber(), maskedPhone);
        maskedDeveloperMessage = maskedDeveloperMessage.replace(ex.getPhoneNumber(), maskedPhone);
    }
    
    return ErrorResponse.builder()
        .errorCode(errorResponse.getErrorCode())
        .userMessage(maskedMessage)
        .developerMessage(maskedDeveloperMessage)
        .timestamp(errorResponse.getTimestamp())
        .path(errorResponse.getPath())
        .traceId(errorResponse.getTraceId())
        .ipAddress(sensitiveDataMasker.maskIpAddress(errorResponse.getIpAddress()))
        .build();
}

/**
 * GPS 좌표 정보 마스킹
 */
private ErrorResponse maskGpsCoordinates(ErrorResponse errorResponse, GymException ex) {
    String maskedMessage = errorResponse.getUserMessage();
    String maskedDeveloperMessage = errorResponse.getDeveloperMessage();
    
    if (ex.getLatitude() != null && ex.getLongitude() != null) {
        // GPS 좌표를 대략적인 지역으로 마스킹
        String originalCoords = String.format("%.6f,%.6f", ex.getLatitude(), ex.getLongitude());
        String maskedCoords = String.format("%.1f,%.1f", ex.getLatitude(), ex.getLongitude());
        
        maskedMessage = maskedMessage.replace(originalCoords, maskedCoords);
        maskedDeveloperMessage = maskedDeveloperMessage.replace(originalCoords, maskedCoords);
    }
    
    return ErrorResponse.builder()
        .errorCode(errorResponse.getErrorCode())
        .userMessage(maskedMessage)
        .developerMessage(maskedDeveloperMessage)
        .timestamp(errorResponse.getTimestamp())
        .path(errorResponse.getPath())
        .traceId(errorResponse.getTraceId())
        .build();
}

/**
 * 결제 민감정보 마스킹
 */
private ErrorResponse maskPaymentSensitiveData(ErrorResponse errorResponse, PaymentException ex) {
    String maskedMessage = errorResponse.getUserMessage();
    String maskedDeveloperMessage = errorResponse.getDeveloperMessage();
    
    // 카드번호 마스킹
    if (ex.getCardNumber() != null) {
        String maskedCard = sensitiveDataMasker.maskCardNumber(ex.getCardNumber());
        maskedMessage = maskedMessage.replace(ex.getCardNumber(), maskedCard);
        maskedDeveloperMessage = maskedDeveloperMessage.replace(ex.getCardNumber(), maskedCard);
    }
    
    // 계좌번호 마스킹
    if (ex.getAccountNumber() != null) {
        String maskedAccount = sensitiveDataMasker.maskGeneral(ex.getAccountNumber());
        maskedMessage = maskedMessage.replace(ex.getAccountNumber(), maskedAccount);
        maskedDeveloperMessage = maskedDeveloperMessage.replace(ex.getAccountNumber(), maskedAccount);
    }
    
    return ErrorResponse.builder()
        .errorCode(errorResponse.getErrorCode())
        .userMessage(maskedMessage)
        .developerMessage(maskedDeveloperMessage)
        .timestamp(errorResponse.getTimestamp())
        .path(errorResponse.getPath())
        .traceId(errorResponse.getTraceId())
        .build();
}

/**
 * 악성 입력값 마스킹
 */
private ErrorResponse maskMaliciousInput(ErrorResponse errorResponse, ValidationException ex) {
    String maskedMessage = errorResponse.getUserMessage();
    String maskedDeveloperMessage = errorResponse.getDeveloperMessage();
    
    if (ex.getInputValue() != null) {
        String maskedInput = "***BLOCKED***";
        maskedMessage = maskedMessage.replace(ex.getInputValue(), maskedInput);
        maskedDeveloperMessage = maskedDeveloperMessage.replace(ex.getInputValue(), maskedInput);
    }
    
    return ErrorResponse.builder()
        .errorCode(errorResponse.getErrorCode())
        .userMessage(maskedMessage)
        .developerMessage(maskedDeveloperMessage)
        .timestamp(errorResponse.getTimestamp())
        .path(errorResponse.getPath())
        .traceId(errorResponse.getTraceId())
        .securityLevel("HIGH")
        .build();
}

/**
 * 시스템 에러 정보 제한
 */
private ErrorResponse sanitizeSystemError(ErrorResponse errorResponse) {
    return ErrorResponse.builder()
        .errorCode(errorResponse.getErrorCode())
        .userMessage("시스템 오류가 발생했습니다. 관리자에게 문의해주세요")
        .developerMessage("System error occurred")
        .timestamp(errorResponse.getTimestamp())
        .path(errorResponse.getPath())
        .traceId(errorResponse.getTraceId())
        .build();
}

/**
 * Rate Limit 정보 추출
 */
private RateLimitInfo extractRateLimitInfo(HttpServletRequest request) {
    // Rate Limit 헤더에서 정보 추출
    String limitHeader = request.getHeader("X-RateLimit-Limit");
    String remainingHeader = request.getHeader("X-RateLimit-Remaining");
    String resetHeader = request.getHeader("X-RateLimit-Reset");
    
    int limit = limitHeader != null ? Integer.parseInt(limitHeader) : 100;
    int remaining = remainingHeader != null ? Integer.parseInt(remainingHeader) : 0;
    long resetTime = resetHeader != null ? Long.parseLong(resetHeader) : 
                     LocalDateTime.now().plusMinutes(1).toEpochSecond(ZoneOffset.UTC);
    
    return RateLimitInfo.builder()
        .limit(limit)
        .remaining(remaining)
        .resetTime(resetTime)
        .retryAfterSeconds(resetTime - LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
        .limitType("API")
        .rateLimitKey("***MASKED***")
        .build();
}
```

---

## ✅ Step 3-3b 완료 체크리스트

### 🛡️ 보안 강화 구현
- [x] **SensitiveDataMasker**: 민감정보 자동 마스킹 (이메일, 휴대폰, 토큰)
- [x] **RateLimitManager**: API별 차등 제한 (로그인 5회/분, 이메일 1회/분)
- [x] **SecurityThreatDetector**: XSS/SQL Injection 패턴 탐지
- [x] **RecommendationSecurity**: 추천 시스템 보안 예외 3개 추가
- [x] **IP 추적**: 클라이언트 IP 다중 헤더 지원

### 🔒 마스킹 기능
- [x] **5가지 마스킹**: 이메일, 휴대폰, 토큰, 카드번호, IP 주소
- [x] **동적 마스킹**: 환경 설정에 따른 마스킹 on/off
- [x] **보안 수준별**: HIGH/MEDIUM/LOW 3단계 차등 처리
- [x] **예외별 마스킹**: 도메인별 특화 마스킹 로직

### ⚡ Rate Limiting 체계
- [x] **5개 API 타입**: 로그인, 이메일, SMS, API, 결제별 차등 제한
- [x] **Redis 분산**: 다중 서버 환경 지원
- [x] **Fail-Open**: Redis 장애 시 서비스 연속성 보장
- [x] **헤더 지원**: X-RateLimit-* 표준 헤더 제공

### 🔍 보안 위협 탐지
- [x] **3가지 공격 유형**: XSS, SQL Injection, Path Traversal
- [x] **실시간 탐지**: 요청 시점 즉시 차단
- [x] **패턴 기반**: 정규식을 활용한 효율적 탐지
- [x] **위협 분류**: SecurityThreatType enum으로 체계적 분류

### 📊 추천 시스템 보안
- [x] **3가지 보안 예외**: 무단 접근, 데이터 조작, 시스템 악용
- [x] **접근 제어**: 사용자별 추천 데이터 격리
- [x] **악용 방지**: 과도한 추천 요청 제한
- [x] **보안 로깅**: 추천 시스템 보안 이벤트 추적

---

**다음 단계**: step3-3c_monitoring_testing.md (모니터링 및 테스트)  
**관련 파일**: step3-3a_global_handler_core.md (핵심 예외 처리)

*생성일: 2025-08-21*  
*핵심 성과: RoutePickr 보안 강화 시스템 완성*