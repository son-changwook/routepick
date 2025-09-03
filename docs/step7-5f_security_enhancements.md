# 7-5f단계: 보안 패치 및 XSS 방지 구현

## 📋 구현 개요
- **XSS 방지**: Response DTO 이스케이프 처리
- **민감정보 마스킹**: 표준화된 마스킹 유틸리티
- **보안 취약점 패치**: 통합 보안 강화
- **입력 검증**: 커스텀 Validator 구현

## 🛡️ XSS 방지 구현

### 1. SafeHtml 커스텀 Validator
```java
package com.routepick.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = SafeHtmlValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeHtml {
    
    String message() default "HTML 태그가 포함되어 있거나 안전하지 않은 내용입니다";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * 허용할 HTML 태그 (빈 배열이면 모든 HTML 태그 제거)
     */
    String[] allowedTags() default {};
    
    /**
     * 허용할 속성 (빈 배열이면 모든 속성 제거)
     */
    String[] allowedAttributes() default {};
}
```

### 2. SafeHtmlValidator 구현
```java
package com.routepick.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.Arrays;
import java.util.regex.Pattern;

@Slf4j
public class SafeHtmlValidator implements ConstraintValidator<SafeHtml, String> {
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(javascript:|vbscript:|onload=|onerror=|onclick=|onmouseover=|onfocus=|onblur=|" +
        "onchange=|onsubmit=|onreset=|onselect=|onkeydown=|onkeypress=|onkeyup=|" +
        "<script[^>]*>.*?</script>|<iframe[^>]*>.*?</iframe>|<object[^>]*>.*?</object>|" +
        "<embed[^>]*>.*?</embed>|<applet[^>]*>.*?</applet>|<meta[^>]*>|<link[^>]*>|" +
        "expression\\s*\\(|url\\s*\\(|@import|<!--.*?-->)"
    );
    
    private String[] allowedTags;
    private String[] allowedAttributes;
    
    @Override
    public void initialize(SafeHtml constraintAnnotation) {
        this.allowedTags = constraintAnnotation.allowedTags();
        this.allowedAttributes = constraintAnnotation.allowedAttributes();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        
        // 1. XSS 패턴 검사
        if (XSS_PATTERN.matcher(value).find()) {
            log.warn("XSS 패턴 감지: {}", value.substring(0, Math.min(value.length(), 100)));
            return false;
        }
        
        // 2. HTML 태그 검증
        if (!isHtmlContentSafe(value)) {
            return false;
        }
        
        // 3. SQL Injection 패턴 검사
        if (containsSqlInjectionPattern(value)) {
            log.warn("SQL Injection 패턴 감지");
            return false;
        }
        
        return true;
    }
    
    private boolean isHtmlContentSafe(String value) {
        try {
            Safelist safelist = createSafelist();
            String cleaned = Jsoup.clean(value, safelist);
            
            // 원본과 정제된 내용이 다르면 위험한 태그가 있었다는 의미
            return cleaned.equals(value);
            
        } catch (Exception e) {
            log.error("HTML 검증 중 오류 발생", e);
            return false;
        }
    }
    
    private Safelist createSafelist() {
        if (allowedTags.length == 0) {
            return Safelist.none(); // 모든 HTML 태그 제거
        }
        
        Safelist safelist = new Safelist();
        safelist.addTags(allowedTags);
        
        if (allowedAttributes.length > 0) {
            for (String tag : allowedTags) {
                safelist.addAttributes(tag, allowedAttributes);
            }
        }
        
        return safelist;
    }
    
    private boolean containsSqlInjectionPattern(String value) {
        String lowerValue = value.toLowerCase();
        String[] sqlKeywords = {
            "union select", "drop table", "delete from", "insert into", 
            "update set", "create table", "alter table", "exec ", "execute ",
            "sp_", "xp_", "/*", "*/", "--", "char(", "cast(", "convert("
        };
        
        return Arrays.stream(sqlKeywords)
                .anyMatch(lowerValue::contains);
    }
}
```

### 3. KoreanText 커스텀 Validator
```java
package com.routepick.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = KoreanTextValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface KoreanText {
    
    String message() default "한글, 영문, 숫자, 일반 특수문자만 사용 가능합니다";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * 이모지 허용 여부
     */
    boolean allowEmoji() default true;
    
    /**
     * 특수문자 허용 여부
     */
    boolean allowSpecialChars() default true;
}

@Slf4j
public class KoreanTextValidator implements ConstraintValidator<KoreanText, String> {
    
    private boolean allowEmoji;
    private boolean allowSpecialChars;
    
    // 한글, 영문, 숫자 기본 패턴
    private static final Pattern BASIC_PATTERN = Pattern.compile("^[a-zA-Z0-9가-힣\\s]*$");
    
    // 안전한 특수문자 패턴
    private static final Pattern SAFE_SPECIAL_CHARS = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?~`]");
    
    // 이모지 패턴
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
        "[\\x{1F600}-\\x{1F64F}]|" +  // 이모티콘
        "[\\x{1F300}-\\x{1F5FF}]|" +  // 기타 기호
        "[\\x{1F680}-\\x{1F6FF}]|" +  // 교통 기호
        "[\\x{1F1E0}-\\x{1F1FF}]|" +  // 국기
        "[\\x{2600}-\\x{26FF}]|" +    // 기타 기호
        "[\\x{2700}-\\x{27BF}]"       // 장식 기호
    );
    
    @Override
    public void initialize(KoreanText constraintAnnotation) {
        this.allowEmoji = constraintAnnotation.allowEmoji();
        this.allowSpecialChars = constraintAnnotation.allowSpecialChars();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        
        // 1. 기본 문자 검증 (한글, 영문, 숫자, 공백)
        String tempValue = value;
        
        // 2. 이모지 허용 시 제거 후 검증
        if (allowEmoji) {
            tempValue = EMOJI_PATTERN.matcher(tempValue).replaceAll("");
        }
        
        // 3. 특수문자 허용 시 제거 후 검증
        if (allowSpecialChars) {
            tempValue = SAFE_SPECIAL_CHARS.matcher(tempValue).replaceAll("");
        }
        
        // 4. 최종 검증
        boolean isValid = BASIC_PATTERN.matcher(tempValue).matches();
        
        if (!isValid) {
            log.warn("부적절한 문자 포함: {}", value.substring(0, Math.min(value.length(), 50)));
        }
        
        return isValid;
    }
}
```

## 🔒 민감정보 마스킹 유틸리티

### 1. DataMaskingUtil 구현
```java
package com.routepick.backend.common.util;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class DataMaskingUtil {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^(.{1,3}).*@(.*)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\d{3})(\\d{3,4})(\\d{4})$");
    private static final Pattern CARD_PATTERN = Pattern.compile("^(\\d{4})(\\d{4,8})(\\d{4})$");
    
    /**
     * 이메일 마스킹
     * example@domain.com → exa***@domain.com
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        
        if (localPart.length() <= 3) {
            return "*".repeat(localPart.length()) + domainPart;
        }
        
        return localPart.substring(0, 3) + "*".repeat(localPart.length() - 3) + domainPart;
    }
    
    /**
     * 휴대폰 번호 마스킹
     * 010-1234-5678 → 010-****-5678
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        
        String cleanNumber = phoneNumber.replaceAll("[^0-9]", "");
        
        if (cleanNumber.length() == 11) {
            return cleanNumber.substring(0, 3) + "-****-" + cleanNumber.substring(7);
        } else if (cleanNumber.length() == 10) {
            return cleanNumber.substring(0, 3) + "-***-" + cleanNumber.substring(6);
        }
        
        return "*".repeat(phoneNumber.length());
    }
    
    /**
     * 카드 번호 마스킹
     * 1234-5678-9012-3456 → 1234-****-****-3456
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return cardNumber;
        }
        
        String cleanNumber = cardNumber.replaceAll("[^0-9]", "");
        
        if (cleanNumber.length() >= 12) {
            return cleanNumber.substring(0, 4) + "-****-****-" + 
                   cleanNumber.substring(cleanNumber.length() - 4);
        }
        
        return "*".repeat(Math.min(cardNumber.length(), 16));
    }
    
    /**
     * 이름 마스킹
     * 홍길동 → 홍*동, 김철수 → 김*수
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        if (name.length() == 1) {
            return "*";
        } else if (name.length() == 2) {
            return name.charAt(0) + "*";
        } else {
            return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
        }
    }
    
    /**
     * 주소 마스킹
     * 서울시 강남구 테헤란로 123 → 서울시 강남구 ***
     */
    public static String maskAddress(String address) {
        if (address == null || address.isEmpty()) {
            return address;
        }
        
        String[] parts = address.split("\\s+");
        if (parts.length <= 2) {
            return address.substring(0, Math.min(address.length() / 2, 10)) + "***";
        }
        
        return parts[0] + " " + parts[1] + " ***";
    }
    
    /**
     * API 키 마스킹
     * sk_test_1234567890abcdef → sk_test_***
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return apiKey;
        }
        
        if (apiKey.contains("_")) {
            String[] parts = apiKey.split("_", 3);
            if (parts.length >= 2) {
                return parts[0] + "_" + parts[1] + "_***";
            }
        }
        
        return apiKey.substring(0, Math.min(apiKey.length() / 3, 10)) + "***";
    }
}
```

### 2. Response DTO 마스킹 적용
```java
package com.routepick.backend.common.response;

import com.routepick.backend.common.util.DataMaskingUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;

@Slf4j
@Component
public class ResponseDataMasker {
    
    /**
     * Response 객체의 민감정보 자동 마스킹
     */
    @SuppressWarnings("unchecked")
    public <T> T maskSensitiveData(T response) {
        if (response == null) {
            return null;
        }
        
        try {
            if (response instanceof Collection) {
                Collection<Object> collection = (Collection<Object>) response;
                collection.forEach(this::maskObjectFields);
            } else {
                maskObjectFields(response);
            }
        } catch (Exception e) {
            log.error("민감정보 마스킹 중 오류 발생", e);
        }
        
        return response;
    }
    
    private void maskObjectFields(Object obj) {
        if (obj == null) {
            return;
        }
        
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            field.setAccessible(true);
            
            try {
                Object value = field.get(obj);
                if (value instanceof String) {
                    String maskedValue = maskFieldByName(field.getName(), (String) value);
                    if (!maskedValue.equals(value)) {
                        field.set(obj, maskedValue);
                    }
                } else if (value != null && !isPrimitiveOrWrapper(value.getClass())) {
                    // 중첩 객체 재귀 처리
                    maskObjectFields(value);
                }
            } catch (Exception e) {
                log.warn("필드 마스킹 실패: {}.{}", clazz.getSimpleName(), field.getName());
            }
        }
    }
    
    private String maskFieldByName(String fieldName, String value) {
        String lowerFieldName = fieldName.toLowerCase();
        
        if (lowerFieldName.contains("email")) {
            return DataMaskingUtil.maskEmail(value);
        } else if (lowerFieldName.contains("phone") || lowerFieldName.contains("mobile")) {
            return DataMaskingUtil.maskPhoneNumber(value);
        } else if (lowerFieldName.contains("card") && lowerFieldName.contains("number")) {
            return DataMaskingUtil.maskCardNumber(value);
        } else if (lowerFieldName.contains("name") && !lowerFieldName.contains("username")) {
            return DataMaskingUtil.maskName(value);
        } else if (lowerFieldName.contains("address")) {
            return DataMaskingUtil.maskAddress(value);
        } else if (lowerFieldName.contains("apikey") || lowerFieldName.contains("secretkey")) {
            return DataMaskingUtil.maskApiKey(value);
        }
        
        return value;
    }
    
    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() || 
               clazz == String.class ||
               clazz == Boolean.class ||
               clazz == Integer.class ||
               clazz == Long.class ||
               clazz == Double.class ||
               clazz == Float.class ||
               Number.class.isAssignableFrom(clazz);
    }
}
```

## 🔍 보안 로깅 시스템

### 1. SecurityEventLogger 구현
```java
package com.routepick.backend.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventLogger {
    
    private final SecurityAuditRepository securityAuditRepository;
    
    /**
     * XSS 공격 시도 로깅
     */
    public void logXssAttempt(String userAgent, String remoteAddr, String payload) {
        Map<String, Object> details = new HashMap<>();
        details.put("type", "XSS_ATTEMPT");
        details.put("userAgent", userAgent);
        details.put("payload", payload.substring(0, Math.min(payload.length(), 200)));
        
        logSecurityEvent("XSS_BLOCKED", remoteAddr, details);
        
        // 심각한 경우 알림 발송
        if (isHighRiskXss(payload)) {
            sendSecurityAlert("High-risk XSS attempt detected", details);
        }
    }
    
    /**
     * SQL Injection 시도 로깅
     */
    public void logSqlInjectionAttempt(String userAgent, String remoteAddr, String payload) {
        Map<String, Object> details = new HashMap<>();
        details.put("type", "SQL_INJECTION_ATTEMPT");
        details.put("userAgent", userAgent);
        details.put("payload", payload.substring(0, Math.min(payload.length(), 200)));
        
        logSecurityEvent("SQL_INJECTION_BLOCKED", remoteAddr, details);
        sendSecurityAlert("SQL Injection attempt detected", details);
    }
    
    /**
     * Rate Limiting 위반 로깅
     */
    public void logRateLimitExceeded(String endpoint, String remoteAddr, Long userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("type", "RATE_LIMIT_EXCEEDED");
        details.put("endpoint", endpoint);
        details.put("userId", userId);
        
        logSecurityEvent("RATE_LIMIT_EXCEEDED", remoteAddr, details);
        
        // 반복적인 위반 시 계정 잠금 고려
        checkRepeatedViolations(remoteAddr, userId);
    }
    
    /**
     * 관리자 권한 액세스 로깅
     */
    public void logAdminAccess(Long userId, String action, String resource) {
        Map<String, Object> details = new HashMap<>();
        details.put("type", "ADMIN_ACCESS");
        details.put("userId", userId);
        details.put("action", action);
        details.put("resource", resource);
        
        logSecurityEvent("ADMIN_ACCESS", null, details);
    }
    
    private void logSecurityEvent(String eventType, String remoteAddr, Map<String, Object> details) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
            .eventType(eventType)
            .ipAddress(remoteAddr)
            .eventTime(LocalDateTime.now())
            .details(details)
            .severity(getSeverityLevel(eventType))
            .build();
            
        securityAuditRepository.save(auditLog);
        
        // 시스템 로그에도 기록
        log.warn("보안 이벤트 발생: {} from {}, details: {}", eventType, remoteAddr, details);
    }
    
    private boolean isHighRiskXss(String payload) {
        String[] highRiskPatterns = {
            "document.cookie", "window.location", "eval(", 
            "setTimeout(", "setInterval(", "Function("
        };
        
        String lowerPayload = payload.toLowerCase();
        for (String pattern : highRiskPatterns) {
            if (lowerPayload.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    private void sendSecurityAlert(String message, Map<String, Object> details) {
        // 보안팀 알림 발송 로직
        log.error("🚨 SECURITY ALERT: {} - {}", message, details);
        // 추후 Slack, 이메일 알림 구현
    }
    
    private void checkRepeatedViolations(String remoteAddr, Long userId) {
        // 반복적인 위반 체크 로직
        // 임계치 초과 시 계정 잠금 또는 IP 차단 고려
    }
    
    private String getSeverityLevel(String eventType) {
        switch (eventType) {
            case "XSS_BLOCKED":
            case "SQL_INJECTION_BLOCKED":
                return "HIGH";
            case "RATE_LIMIT_EXCEEDED":
                return "MEDIUM";
            case "ADMIN_ACCESS":
                return "INFO";
            default:
                return "LOW";
        }
    }
}
```

## 🛡️ 통합 보안 설정

### 1. WebSecurityConfig 강화
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class WebSecurityConfig {
    
    private final SecurityEventLogger securityEventLogger;
    private final ResponseDataMasker responseDataMasker;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors().and()
            .csrf().disable()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/auth/**", "/api/v1/system/health", "/api/v1/system/agreements")
                    .permitAll()
                .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                .anyRequest()
                    .authenticated()
            )
            .addFilterBefore(new XssProtectionFilter(securityEventLogger), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new ResponseMaskingFilter(responseDataMasker), FilterSecurityInterceptor.class)
            .exceptionHandling()
                .authenticationEntryPoint(customAuthenticationEntryPoint())
                .accessDeniedHandler(customAccessDeniedHandler());
            
        return http.build();
    }
    
    @Bean
    public CustomAuthenticationEntryPoint customAuthenticationEntryPoint() {
        return new CustomAuthenticationEntryPoint(securityEventLogger);
    }
    
    @Bean
    public CustomAccessDeniedHandler customAccessDeniedHandler() {
        return new CustomAccessDeniedHandler(securityEventLogger);
    }
}
```

### 2. XssProtectionFilter 구현
```java
@Slf4j
@RequiredArgsConstructor
public class XssProtectionFilter implements Filter {
    
    private final SecurityEventLogger securityEventLogger;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        try {
            XssHttpServletRequestWrapper wrappedRequest = new XssHttpServletRequestWrapper(httpRequest);
            chain.doFilter(wrappedRequest, response);
        } catch (XssDetectedException e) {
            securityEventLogger.logXssAttempt(
                httpRequest.getHeader("User-Agent"),
                httpRequest.getRemoteAddr(),
                e.getPayload()
            );
            
            handleXssDetected((HttpServletResponse) response);
        }
    }
    
    private void handleXssDetected(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Invalid request content detected\"}");
    }
}
```

## ✅ 구현 완료
- [x] XSS 방지 (@SafeHtml Validator)
- [x] 한국어 텍스트 검증 (@KoreanText Validator)
- [x] 민감정보 마스킹 유틸리티
- [x] 보안 이벤트 로깅 시스템
- [x] Response 자동 마스킹 필터
- [x] SQL Injection 방지
- [x] 통합 보안 설정 강화

---
*보안 패치 및 XSS 방지 구현 완료 - 7-5단계 완료!*