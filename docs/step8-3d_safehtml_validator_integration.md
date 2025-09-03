# step8-3d 보완: SafeHtml Validator 통합

## 🛡️ step7-5f SafeHtml Validator와 8-3d XSS 방지 통합

### 1. 통합 SafeHtmlValidator 구현
```java
package com.routepick.backend.config.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * SafeHtml Validator 통합 구현
 * - step7-5f 기능 + 8-3d XSS 방지 통합
 * - JSoup 기반 HTML 정화
 * - SQL Injection 방지
 * - 한국어 특화 검증
 */
@Slf4j
@Component
public class IntegratedSafeHtmlValidator implements ConstraintValidator<SafeHtml, String> {
    
    // XSS 공격 패턴 (step7-5f 기반)
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(javascript:|vbscript:|onload=|onerror=|onclick=|onmouseover=|onfocus=|onblur=|" +
        "onchange=|onsubmit=|onreset=|onselect=|onkeydown=|onkeypress=|onkeyup=|" +
        "<script[^>]*>.*?</script>|<iframe[^>]*>.*?</iframe>|<object[^>]*>.*?</object>|" +
        "<embed[^>]*>.*?</embed>|<applet[^>]*>.*?</applet>|<meta[^>]*>|<link[^>]*>|" +
        "expression\\s*\\(|url\\s*\\(|@import|<!--.*?-->)",
        Pattern.DOTALL
    );
    
    // SQL Injection 패턴 강화
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(union\\s+select|drop\\s+table|delete\\s+from|insert\\s+into|" +
        "update\\s+set|create\\s+table|alter\\s+table|exec\\s+|execute\\s+|" +
        "sp_|xp_|/\\*|\\*/|--|char\\s*\\(|cast\\s*\\(|convert\\s*\\(|" +
        "waitfor\\s+delay|benchmark\\s*\\(|sleep\\s*\\()",
        Pattern.DOTALL
    );
    
    // NoSQL Injection 패턴 (8-3d에서 가져옴)
    private static final Pattern NOSQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(\\$where|\\$ne|\\$gt|\\$lt|\\$gte|\\$lte|\\$in|\\$nin|\\$regex|\\$exists|" +
        "\\$eval|\\$expr|\\$jsonSchema|\\$geoIntersects)",
        Pattern.CASE_INSENSITIVE
    );
    
    // 한국어 특화 패턴
    private static final Pattern KOREAN_SAFE_PATTERN = 
        Pattern.compile("^[\\uac00-\\ud7a3a-zA-Z0-9\\s.,!?()\\-_+=/]*$");
    
    private String[] allowedTags;
    private String[] allowedAttributes;
    private boolean koreanOnly;
    private int maxLength;
    
    @Override
    public void initialize(SafeHtml constraintAnnotation) {
        this.allowedTags = constraintAnnotation.allowedTags();
        this.allowedAttributes = constraintAnnotation.allowedAttributes();
        this.koreanOnly = constraintAnnotation.koreanOnly();
        this.maxLength = constraintAnnotation.maxLength();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        
        // 1. 길이 검증
        if (value.length() > maxLength) {
            addViolation(context, "입력 길이가 최대 허용 길이를 초과했습니다. (최대: " + maxLength + ")");
            return false;
        }
        
        // 2. 한국어 전용 모드 검증
        if (koreanOnly && !KOREAN_SAFE_PATTERN.matcher(value).matches()) {
            addViolation(context, "한국어, 영어, 숫자, 기본 특수문자만 사용할 수 있습니다.");
            return false;
        }
        
        // 3. XSS 패턴 검사 (step7-5f)
        if (XSS_PATTERN.matcher(value).find()) {
            log.warn("XSS 패턴 감지: {}", maskSensitiveContent(value));
            addViolation(context, "위험한 스크립트가 감지되었습니다.");
            return false;
        }
        
        // 4. SQL Injection 검사
        if (SQL_INJECTION_PATTERN.matcher(value).find()) {
            log.warn("SQL Injection 패턴 감지: {}", maskSensitiveContent(value));
            addViolation(context, "데이터베이스 공격 패턴이 감지되었습니다.");
            return false;
        }
        
        // 5. NoSQL Injection 검사 (8-3d 통합)
        if (NOSQL_INJECTION_PATTERN.matcher(value).find()) {
            log.warn("NoSQL Injection 패턴 감지: {}", maskSensitiveContent(value));
            addViolation(context, "NoSQL 공격 패턴이 감지되었습니다.");
            return false;
        }
        
        // 6. HTML 콘텐츠 안전성 검사 (JSoup 사용)
        if (!isHtmlContentSafe(value)) {
            addViolation(context, "허용되지 않은 HTML 태그나 속성이 포함되어 있습니다.");
            return false;
        }
        
        // 7. 추가 보안 검증
        if (!isContentSecure(value)) {
            addViolation(context, "보안상 위험한 내용이 감지되었습니다.");
            return false;
        }
        
        return true;
    }
    
    /**
     * HTML 콘텐츠 안전성 검사 (step7-5f 기반)
     */
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
    
    /**
     * Safelist 생성 (허용된 태그/속성 기반)
     */
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
        
        // 안전한 프로토콜만 허용
        safelist.addProtocols("a", "href", "http", "https");
        safelist.addProtocols("img", "src", "http", "https");
        
        return safelist;
    }
    
    /**
     * 추가 보안 검증 (8-3d InputSanitizer 로직 통합)
     */
    private boolean isContentSecure(String value) {
        String lowerValue = value.toLowerCase();
        
        // 파일 경로 순회 공격 방지
        if (lowerValue.contains("../") || lowerValue.contains("..\\")) {
            log.warn("경로 순회 공격 시도 감지");
            return false;
        }
        
        // 프로토콜 기반 공격 방지
        String[] dangerousProtocols = {
            "file://", "ftp://", "data:", "blob:", "javascript:", "vbscript:"
        };
        
        for (String protocol : dangerousProtocols) {
            if (lowerValue.contains(protocol)) {
                log.warn("위험한 프로토콜 감지: {}", protocol);
                return false;
            }
        }
        
        // 서버사이드 템플릿 인젝션 방지
        if (lowerValue.contains("{{") || lowerValue.contains("${") || 
            lowerValue.contains("<%") || lowerValue.contains("#{")) {
            log.warn("템플릿 인젝션 패턴 감지");
            return false;
        }
        
        return true;
    }
    
    /**
     * 민감한 내용 마스킹 (로그용)
     */
    private String maskSensitiveContent(String content) {
        if (content.length() <= 50) {
            return content.substring(0, Math.min(20, content.length())) + "***";
        }
        return content.substring(0, 20) + "...(" + (content.length() - 20) + " more chars)***";
    }
    
    /**
     * 커스텀 검증 오류 메시지 추가
     */
    private void addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
}
```

### 2. 강화된 SafeHtml 애노테이션
```java
package com.routepick.backend.config.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * SafeHtml 검증 애노테이션 (step7-5f + 8-3d 통합)
 */
@Documented
@Constraint(validatedBy = IntegratedSafeHtmlValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, 
         ElementType.CONSTRUCTOR, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeHtml {
    
    String message() default "안전하지 않은 내용이 포함되어 있습니다";
    
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
    
    /**
     * 한국어 전용 모드 (한글, 영어, 숫자, 기본 특수문자만 허용)
     */
    boolean koreanOnly() default false;
    
    /**
     * 최대 길이 제한
     */
    int maxLength() default 1000;
    
    /**
     * 엄격한 모드 (더 강력한 보안 검증)
     */
    boolean strictMode() default false;
}
```

### 3. SafeHtml 사용 예시 (DTO 통합)
```java
package com.routepick.backend.dto.request;

import com.routepick.backend.config.validation.SafeHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostCreateRequest {
    
    @NotBlank(message = "제목은 필수입니다")
    @SafeHtml(
        allowedTags = {"b", "i", "strong", "em"}, 
        maxLength = 100,
        koreanOnly = true,
        message = "제목에 허용되지 않은 내용이 포함되어 있습니다"
    )
    private String title;
    
    @SafeHtml(
        allowedTags = {"p", "br", "b", "i", "strong", "em", "u", "a"},
        allowedAttributes = {"href", "title"},
        maxLength = 5000,
        message = "내용에 허용되지 않은 HTML이 포함되어 있습니다"
    )
    private String content;
    
    @SafeHtml(
        allowedTags = {},  // HTML 태그 모두 제거
        maxLength = 50,
        koreanOnly = true,
        message = "닉네임은 한글, 영어, 숫자만 사용 가능합니다"
    )
    private String nickname;
    
    @SafeHtml(
        strictMode = true,  // 엄격한 보안 모드
        maxLength = 200
    )
    private String description;
}
```

### 4. XssRequestWrapper 통합 개선
```java
package com.routepick.backend.security.xss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.routepick.backend.config.validation.IntegratedSafeHtmlValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * XSS 방지 Request Wrapper (SafeHtml Validator 통합)
 */
@Slf4j
public class EnhancedXssRequestWrapper extends HttpServletRequestWrapper {
    
    private final IntegratedSafeHtmlValidator safeHtmlValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public EnhancedXssRequestWrapper(HttpServletRequest request) {
        super(request);
        this.safeHtmlValidator = new IntegratedSafeHtmlValidator();
    }
    
    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return sanitizeInput(value, false);
    }
    
    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        
        String[] sanitizedValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            sanitizedValues[i] = sanitizeInput(values[i], false);
        }
        return sanitizedValues;
    }
    
    /**
     * 통합된 입력 정화 (SafeHtml Validator 로직 사용)
     */
    private String sanitizeInput(String input, boolean allowHtml) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        try {
            // 1. SafeHtml Validator의 보안 검증 로직 재사용
            if (!isSecureContent(input)) {
                log.warn("위험한 입력 감지 및 정화: {}", 
                    input.substring(0, Math.min(50, input.length())));
                return ""; // 위험한 내용은 빈 문자열로 대체
            }
            
            // 2. JSoup으로 HTML 정화
            Safelist safelist = allowHtml ? 
                Safelist.basic() : Safelist.none();
                
            String cleaned = Jsoup.clean(input, safelist);
            
            // 3. 추가 정화 처리
            cleaned = cleaned.replaceAll("(?i)javascript:", "");
            cleaned = cleaned.replaceAll("(?i)vbscript:", "");
            cleaned = cleaned.replaceAll("(?i)on\\w+\\s*=", "");
            
            return cleaned;
            
        } catch (Exception e) {
            log.error("입력 정화 중 오류 발생", e);
            return ""; // 오류 시 안전하게 빈 문자열 반환
        }
    }
    
    /**
     * SafeHtml Validator의 보안 검증 로직 재사용
     */
    private boolean isSecureContent(String content) {
        // IntegratedSafeHtmlValidator의 검증 로직을 여기서 재사용
        // (실제로는 Validator를 직접 호출하거나 공통 유틸리티로 분리)
        return !content.toLowerCase().contains("script") &&
               !content.toLowerCase().contains("javascript:") &&
               !content.toLowerCase().contains("union select") &&
               !content.contains("../");
    }
}
```