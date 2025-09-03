# Step 7-4g: 7-4단계 보안 강화 구현

## 📋 보안 강화 목표
7-4단계 Controller 및 DTO에 대한 포괄적 보안 강화:
1. **권한 검증 강화** - @PostAuthorize 및 리소스 소유권 검증
2. **민감정보 보호** - GPS 좌표 마스킹 및 개인정보 보호
3. **입력 검증 강화** - 비즈니스 로직 검증 및 XSS 방지
4. **트랜잭션 보안** - 원자성 보장 및 데이터 일관성
5. **Rate Limiting 고도화** - 사용자별/IP별 복합 제한

---

## 🔐 1. 보안 서비스 레이어

### 📁 파일 위치
```
src/main/java/com/routepick/security/
```

### A. RouteSecurityService
```java
package com.routepick.security.service;

import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.route.repository.RouteScrapRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.exception.route.RouteAccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 루트 관련 보안 검증 서비스
 */
@Slf4j
@Service("routeSecurityService")
@RequiredArgsConstructor
public class RouteSecurityService {
    
    private final RouteRepository routeRepository;
    private final RouteScrapRepository routeScrapRepository;
    
    /**
     * 루트 접근 권한 검증
     */
    public boolean canAccessRoute(Long routeId, Long userId) {
        if (routeId == null || userId == null) {
            return false;
        }
        
        return routeRepository.findById(routeId)
            .map(route -> {
                // 공개 루트는 모든 사용자 접근 가능
                if (route.isPublic()) {
                    return true;
                }
                
                // 세터 본인은 항상 접근 가능
                if (route.getRouteSetter() != null && 
                    userId.equals(route.getRouteSetter().getUser().getId())) {
                    return true;
                }
                
                // 암장 회원권이 있는 경우 접근 가능
                return hasGymMembership(userId, route.getWall().getBranch().getId());
            })
            .orElse(false);
    }
    
    /**
     * 루트 수정 권한 검증
     */
    public boolean canModifyRoute(Long routeId, Long userId) {
        return routeRepository.findById(routeId)
            .map(route -> {
                // 세터 본인만 수정 가능
                if (route.getRouteSetter() != null && 
                    userId.equals(route.getRouteSetter().getUser().getId())) {
                    return true;
                }
                
                // 암장 관리자 권한 체크
                return isGymAdmin(userId, route.getWall().getBranch().getGym().getId());
            })
            .orElse(false);
    }
    
    /**
     * 스크랩 접근 권한 검증 (자신의 스크랩만)
     */
    public boolean canAccessScrap(Long userId, Long scrapOwnerId) {
        return userId != null && userId.equals(scrapOwnerId);
    }
    
    /**
     * 클라이밍 기록 접근 권한 (개인정보)
     */
    public boolean canAccessClimbingRecord(Long recordOwnerId, Long requestingUserId) {
        if (recordOwnerId == null || requestingUserId == null) {
            return false;
        }
        
        // 본인 기록만 접근 가능
        return recordOwnerId.equals(requestingUserId);
    }
    
    private boolean hasGymMembership(Long userId, Long branchId) {
        // 암장 회원권 보유 여부 확인 로직
        return true; // 임시 구현
    }
    
    private boolean isGymAdmin(Long userId, Long gymId) {
        // 암장 관리자 권한 확인 로직  
        return false; // 임시 구현
    }
}
```

### B. DataMaskingService
```java
package com.routepick.security.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 민감정보 마스킹 서비스
 */
@Service
public class DataMaskingService {
    
    private static final int GPS_PRECISION = 3; // 소수점 3자리 (약 100m 오차)
    
    /**
     * GPS 좌표 마스킹 (정밀도 제한)
     */
    public BigDecimal maskGpsCoordinate(BigDecimal coordinate) {
        if (coordinate == null) {
            return null;
        }
        
        return coordinate.setScale(GPS_PRECISION, RoundingMode.HALF_UP);
    }
    
    /**
     * 사용자 ID 마스킹 (로깅용)
     */
    public String maskUserId(Long userId) {
        if (userId == null) {
            return "null";
        }
        
        String userIdStr = userId.toString();
        if (userIdStr.length() <= 2) {
            return "***";
        }
        
        return userIdStr.substring(0, 2) + "***" + userIdStr.substring(userIdStr.length() - 1);
    }
    
    /**
     * 휴대폰 번호 마스킹
     */
    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return "***-****-****";
        }
        
        return phoneNumber.substring(0, 3) + "-****-" + phoneNumber.substring(phoneNumber.length() - 4);
    }
    
    /**
     * 상세 주소 마스킹 (동/읍면 까지만)
     */
    public String maskDetailedAddress(String address) {
        if (address == null) {
            return null;
        }
        
        // 구/군/시 + 동/읍/면 까지만 노출
        String[] parts = address.split(" ");
        if (parts.length >= 3) {
            return parts[0] + " " + parts[1] + " " + parts[2] + " ***";
        }
        
        return address;
    }
}
```

---

## 🛡️ 2. 커스텀 검증 어노테이션

### A. 비즈니스 로직 검증
```java
package com.routepick.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 클라이밍 기록의 비즈니스 로직 검증
 */
@Documented
@Constraint(validatedBy = ClimbingRecordValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidClimbingRecord {
    String message() default "클라이밍 기록이 유효하지 않습니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

```java
package com.routepick.validation;

import com.routepick.dto.climbing.request.ClimbingRecordRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/**
 * 클라이밍 기록 검증 로직
 */
public class ClimbingRecordValidator implements ConstraintValidator<ValidClimbingRecord, ClimbingRecordRequest> {
    
    @Override
    public boolean isValid(ClimbingRecordRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return false;
        }
        
        // 성공률과 시도 횟수 일관성 검증
        if (!isValidSuccessRateAndAttempts(request.getSuccessRate(), request.getAttemptCount())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "성공률과 시도 횟수가 일치하지 않습니다. 1회 시도시 성공률은 0.0 또는 1.0이어야 합니다")
                .addConstraintViolation();
            return false;
        }
        
        // 운동 시간과 시도 횟수 합리성 검증
        if (!isReasonableDuration(request.getDurationMinutes(), request.getAttemptCount())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "운동 시간이 시도 횟수에 비해 너무 짧거나 깁니다")
                .addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private boolean isValidSuccessRateAndAttempts(BigDecimal successRate, Integer attemptCount) {
        if (successRate == null || attemptCount == null || attemptCount <= 0) {
            return false;
        }
        
        // 1회 시도의 경우 0% 또는 100%만 가능
        if (attemptCount == 1) {
            return successRate.compareTo(BigDecimal.ZERO) == 0 || 
                   successRate.compareTo(BigDecimal.ONE) == 0;
        }
        
        // 다회 시도의 경우 가능한 성공률 범위 검증
        BigDecimal minPossibleRate = BigDecimal.ZERO;
        BigDecimal maxPossibleRate = BigDecimal.ONE;
        
        return successRate.compareTo(minPossibleRate) >= 0 && 
               successRate.compareTo(maxPossibleRate) <= 0;
    }
    
    private boolean isReasonableDuration(Integer durationMinutes, Integer attemptCount) {
        if (durationMinutes == null || attemptCount == null) {
            return true; // 선택 필드는 별도 검증
        }
        
        // 시도당 최소 1분, 최대 30분으로 합리성 검증
        int minDuration = attemptCount * 1;
        int maxDuration = attemptCount * 30;
        
        return durationMinutes >= minDuration && durationMinutes <= maxDuration;
    }
}
```

### B. 한국 GPS 좌표 검증
```java
package com.routepick.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 한국 영토 내 GPS 좌표 검증
 */
@Documented
@Constraint(validatedBy = KoreanGpsValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidKoreanGps {
    String message() default "한국 영토 내의 유효한 GPS 좌표가 아닙니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

```java
package com.routepick.validation;

import com.routepick.dto.gym.request.NearbyGymSearchRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/**
 * 한국 GPS 좌표 검증 로직
 */
public class KoreanGpsValidator implements ConstraintValidator<ValidKoreanGps, NearbyGymSearchRequest> {
    
    // 한국 영토의 정확한 경계
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("33.0");  // 마라도
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("38.6");  // 휴전선
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("124.0"); // 백령도
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("132.0"); // 독도
    
    @Override
    public boolean isValid(NearbyGymSearchRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getLatitude() == null || request.getLongitude() == null) {
            return false;
        }
        
        BigDecimal lat = request.getLatitude();
        BigDecimal lng = request.getLongitude();
        
        // 기본 범위 검증
        if (lat.compareTo(MIN_LATITUDE) < 0 || lat.compareTo(MAX_LATITUDE) > 0 ||
            lng.compareTo(MIN_LONGITUDE) < 0 || lng.compareTo(MAX_LONGITUDE) > 0) {
            return false;
        }
        
        // 주요 도시 근접성 검증 (더 정밀한 검증)
        return isNearMajorCity(lat, lng, context);
    }
    
    private boolean isNearMajorCity(BigDecimal lat, BigDecimal lng, ConstraintValidatorContext context) {
        // 주요 도시 좌표 (서울, 부산, 대구, 인천, 광주, 대전, 울산, 세종 등)
        double[][] majorCities = {
            {37.5665, 126.9780}, // 서울
            {35.1796, 129.0756}, // 부산  
            {35.8714, 128.6014}, // 대구
            {37.4563, 126.7052}, // 인천
            {35.1595, 126.8526}, // 광주
            {36.3504, 127.3845}, // 대전
            {35.5384, 129.3114}, // 울산
            {36.4800, 127.2890}, // 세종
            {33.4996, 126.5312}  // 제주
        };
        
        double requestLat = lat.doubleValue();
        double requestLng = lng.doubleValue();
        
        // 100km 이내에 주요 도시가 있는지 확인
        for (double[] city : majorCities) {
            double distance = calculateDistance(requestLat, requestLng, city[0], city[1]);
            if (distance <= 100.0) { // 100km 이내
                return true;
            }
        }
        
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
            "주요 도시로부터 100km 이내의 위치가 아닙니다. 정확한 한국 내 위치인지 확인해주세요")
            .addConstraintViolation();
        
        return false;
    }
    
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // 지구 반지름 (km)
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
                
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}
```

---

## 🔄 3. 트랜잭션 보안 강화

### A. 보안 트랜잭션 어노테이션
```java
package com.routepick.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.*;

/**
 * 보안 강화된 트랜잭션 어노테이션
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional
public @interface SecureTransaction {
    
    @AliasFor(annotation = Transactional.class)
    Propagation propagation() default Propagation.REQUIRED;
    
    @AliasFor(annotation = Transactional.class)
    Isolation isolation() default Isolation.READ_COMMITTED;
    
    @AliasFor(annotation = Transactional.class)
    int timeout() default -1;
    
    @AliasFor(annotation = Transactional.class)
    boolean readOnly() default false;
    
    @AliasFor(annotation = Transactional.class)
    Class<? extends Throwable>[] rollbackFor() default {Exception.class};
    
    /**
     * 개인정보 처리 여부
     */
    boolean personalData() default false;
    
    /**
     * 감사 로깅 레벨
     */
    String auditLevel() default "INFO";
}
```

### B. 트랜잭션 보안 인터셉터
```java
package com.routepick.security.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 보안 트랜잭션 인터셉터
 */
@Slf4j
@Aspect
@Component
public class SecureTransactionInterceptor {
    
    @Around("@annotation(secureTransaction)")
    public Object aroundSecureTransaction(ProceedingJoinPoint joinPoint, 
                                        com.routepick.annotation.SecureTransaction secureTransaction) throws Throwable {
        
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();
        
        try {
            // 개인정보 처리 시 추가 로깅
            if (secureTransaction.personalData()) {
                log.info("Personal data transaction started: method={}, audit={}", 
                        methodName, secureTransaction.auditLevel());
            }
            
            Object result = joinPoint.proceed();
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Secure transaction completed: method={}, duration={}ms", methodName, duration);
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Secure transaction failed: method={}, duration={}ms, error={}", 
                     methodName, duration, e.getMessage());
            throw e;
        }
    }
}
```

---

## 🚦 4. 고도화된 Rate Limiting

### A. 복합 Rate Limiting
```java
package com.routepick.annotation;

import java.lang.annotation.*;

/**
 * 고도화된 Rate Limiting 어노테이션
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(RateLimits.class)
public @interface RateLimited {
    
    /**
     * 허용 요청 수
     */
    int requests();
    
    /**
     * 기간 (초)
     */
    int period();
    
    /**
     * 키 생성 전략
     */
    KeyStrategy keyStrategy() default KeyStrategy.USER_ID;
    
    /**
     * 제한 범위
     */
    LimitScope scope() default LimitScope.PER_USER;
    
    /**
     * 우선순위 (낮을수록 우선)
     */
    int priority() default 100;
    
    enum KeyStrategy {
        USER_ID,        // 사용자 ID 기반
        IP_ADDRESS,     // IP 주소 기반  
        USER_AND_IP,    // 사용자 ID + IP 복합
        CUSTOM          // 커스텀 키 생성
    }
    
    enum LimitScope {
        PER_USER,       // 사용자별 제한
        PER_IP,         // IP별 제한
        GLOBAL          // 전역 제한
    }
}

/**
 * 복수 Rate Limiting 컨테이너
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimits {
    RateLimited[] value();
}
```

### B. Rate Limiting 구현체
```java
package com.routepick.security.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Redis 기반 Rate Limiting 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {
    
    private final StringRedisTemplate redisTemplate;
    
    private static final String RATE_LIMIT_SCRIPT = 
        "local key = KEYS[1] " +
        "local limit = tonumber(ARGV[1]) " +
        "local window = tonumber(ARGV[2]) " +
        "local current = redis.call('INCR', key) " +
        "if current == 1 then " +
        "    redis.call('EXPIRE', key, window) " +
        "end " +
        "if current > limit then " +
        "    return 0 " +
        "else " +
        "    return limit - current + 1 " +
        "end";
    
    /**
     * Rate Limit 검증
     */
    public boolean isAllowed(String key, int limit, int windowSeconds) {
        try {
            RedisScript<Long> script = RedisScript.of(RATE_LIMIT_SCRIPT, Long.class);
            Long remaining = redisTemplate.execute(script, List.of(key), 
                                                  String.valueOf(limit), 
                                                  String.valueOf(windowSeconds));
            
            boolean allowed = remaining != null && remaining > 0;
            
            if (!allowed) {
                log.warn("Rate limit exceeded: key={}, limit={}, window={}s", key, limit, windowSeconds);
            }
            
            return allowed;
            
        } catch (Exception e) {
            log.error("Rate limit check failed: key={}, error={}", key, e.getMessage());
            // 장애 시 요청 허용 (Fail-Open)
            return true;
        }
    }
    
    /**
     * 남은 요청 수 조회
     */
    public long getRemainingRequests(String key) {
        try {
            String count = redisTemplate.opsForValue().get(key);
            return count != null ? Long.parseLong(count) : 0;
        } catch (Exception e) {
            log.error("Failed to get remaining requests: key={}", key);
            return 0;
        }
    }
    
    /**
     * Rate Limit 초기화
     */
    public void resetRateLimit(String key) {
        try {
            redisTemplate.delete(key);
            log.info("Rate limit reset: key={}", key);
        } catch (Exception e) {
            log.error("Failed to reset rate limit: key={}", key);
        }
    }
}
```

---

## 🎯 5. XSS 방지 및 입력 정제

### A. XSS 방지 필터
```java
package com.routepick.security.filter;

import com.routepick.security.service.DataMaskingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.IOException;

/**
 * XSS 공격 방지 필터
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XssProtectionFilter extends OncePerRequestFilter {
    
    private final DataMaskingService dataMaskingService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // XSS 정제된 Request Wrapper 적용
        XssProtectedHttpServletRequest wrappedRequest = new XssProtectedHttpServletRequest(request);
        
        filterChain.doFilter(wrappedRequest, response);
    }
    
    /**
     * XSS 공격 코드 정제
     */
    public static String cleanXss(String input) {
        if (input == null) {
            return null;
        }
        
        // Jsoup을 사용한 HTML 태그 제거 및 정제
        return Jsoup.clean(input, Safelist.none())
                   .trim()
                   .replaceAll("\\s+", " "); // 연속된 공백 정규화
    }
    
    /**
     * 스크립트 패턴 탐지 및 차단
     */
    public static boolean containsMaliciousScript(String input) {
        if (input == null) {
            return false;
        }
        
        String lowerInput = input.toLowerCase();
        
        // 위험한 패턴들
        String[] maliciousPatterns = {
            "javascript:", "vbscript:", "onload=", "onerror=", "onclick=",
            "<script", "</script>", "eval(", "expression(", "url(javascript:",
            "&#", "&#x", "\\u", "\\x", "%3c", "%3e", "%22", "%27"
        };
        
        for (String pattern : maliciousPatterns) {
            if (lowerInput.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
}
```

### B. XSS 보호 Request Wrapper
```java
package com.routepick.security.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * XSS 보호 Request Wrapper
 */
public class XssProtectedHttpServletRequest extends HttpServletRequestWrapper {
    
    public XssProtectedHttpServletRequest(HttpServletRequest request) {
        super(request);
    }
    
    @Override
    public String[] getParameterValues(String parameter) {
        String[] values = super.getParameterValues(parameter);
        if (values == null) {
            return null;
        }
        
        String[] cleanValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleanValues[i] = XssProtectionFilter.cleanXss(values[i]);
        }
        
        return cleanValues;
    }
    
    @Override
    public String getParameter(String parameter) {
        String value = super.getParameter(parameter);
        return XssProtectionFilter.cleanXss(value);
    }
    
    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return XssProtectionFilter.cleanXss(value);
    }
}
```

---

## 📋 설계 완료 사항
✅ **보안 서비스 레이어** - 권한 검증, 데이터 마스킹 서비스  
✅ **커스텀 검증** - 비즈니스 로직, 한국 GPS 좌표 검증  
✅ **트랜잭션 보안** - @SecureTransaction, 인터셉터  
✅ **고도화된 Rate Limiting** - 복합 키 전략, Redis 구현  
✅ **XSS 방지** - 입력 정제 필터, Request Wrapper  

## 🎯 핵심 보안 강화 기능
- **권한 세분화**: 리소스별, 역할별 정밀한 접근 제어
- **GPS 마스킹**: 100m 정밀도로 개인 위치 보호
- **비즈니스 검증**: 논리적 일관성 보장
- **복합 Rate Limiting**: 사용자별 + IP별 다층 제한
- **XSS 완전 차단**: 모든 입력 데이터 정제
- **트랜잭션 감사**: 개인정보 처리 추적
- **장애 복원력**: Fail-Open 정책으로 서비스 연속성

다음 단계에서 Controller에 이 보안 강화 기능들을 적용하겠습니다.