# step8-3d_xss_input_validation.md
# XSS 방지 및 데이터 검증

## 1. XssProtectionFilter.java
```java
package com.routepick.backend.config.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.backend.common.exception.custom.SecurityException;
import com.routepick.backend.common.exception.enums.ErrorCode;
import com.routepick.backend.config.security.xss.XssRequestWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class XssProtectionFilter implements Filter {
    
    private final ObjectMapper objectMapper;
    
    // 악성 패턴 리스트
    private static final List<Pattern> MALICIOUS_PATTERNS = Arrays.asList(
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onload\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onerror\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onclick\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onmouseover\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("alert\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("confirm\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("prompt\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<form[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\.\\.\\\/", Pattern.CASE_INSENSITIVE), // Path traversal
        Pattern.compile("file:\\/\\/", Pattern.CASE_INSENSITIVE),
        Pattern.compile("data:", Pattern.CASE_INSENSITIVE)
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // XSS 공격 선제검지 (빠른 차단)
        if (containsMaliciousPatterns(httpRequest)) {
            log.warn("XSS 공격 시도 감지: IP={}, URI={}, UserAgent={}",
                httpRequest.getRemoteAddr(),
                httpRequest.getRequestURI(),
                httpRequest.getHeader("User-Agent")
            );
            
            sendXssErrorResponse(httpResponse);
            return;
        }
        
        // XSS 방지 래퍼 적용
        XssRequestWrapper xssRequestWrapper = new XssRequestWrapper(httpRequest);
        
        try {
            chain.doFilter(xssRequestWrapper, response);
        } catch (Exception e) {
            log.error("XSS 필터 처리 중 오류 발생", e);
            throw new SecurityException(ErrorCode.XSS_PROTECTION_ERROR);
        }
    }
    
    private boolean containsMaliciousPatterns(HttpServletRequest request) {
        // 요청 파라미터 검사
        if (request.getParameterMap() != null) {
            for (String[] values : request.getParameterMap().values()) {
                for (String value : values) {
                    if (value != null && isMalicious(value)) {
                        return true;
                    }
                }
            }
        }
        
        // 헤더 검사
        if (request.getHeaderNames() != null) {
            while (request.getHeaderNames().hasMoreElements()) {
                String headerName = request.getHeaderNames().nextElement();
                String headerValue = request.getHeader(headerName);
                if (headerValue != null && isMalicious(headerValue)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean isMalicious(String input) {
        for (Pattern pattern : MALICIOUS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
    
    private void sendXssErrorResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        
        String jsonResponse = "{\"code\":\"XSS_PROTECTION_ERROR\",\"message\":\"비정상적인 요청이 감지되었습니다.\"}";        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
```

## 2. XssRequestWrapper.java
```java
package com.routepick.backend.config.security.xss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.*;

@Slf4j
public class XssRequestWrapper extends HttpServletRequestWrapper {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private byte[] cachedBody;
    
    public XssRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = cacheRequestBody(request);
    }
    
    private byte[] cacheRequestBody(HttpServletRequest request) throws IOException {
        if (request.getInputStream() == null) {
            return new byte[0];
        }
        
        try (InputStream inputStream = request.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }
    
    @Override
    public ServletInputStream getInputStream() throws IOException {
        // JSON 데이터 정화 처리
        String sanitizedBody = sanitizeJsonBody(new String(cachedBody));
        byte[] sanitizedBytes = sanitizedBody.getBytes();
        
        return new ServletInputStream() {
            private ByteArrayInputStream inputStream = new ByteArrayInputStream(sanitizedBytes);
            
            @Override
            public boolean isFinished() {
                return inputStream.available() == 0;
            }
            
            @Override
            public boolean isReady() {
                return true;
            }
            
            @Override
            public void setReadListener(ReadListener listener) {
                // 사용하지 않음
            }
            
            @Override
            public int read() throws IOException {
                return inputStream.read();
            }
        };
    }
    
    @Override
    public BufferedReader getReader() throws IOException {
        String sanitizedBody = sanitizeJsonBody(new String(cachedBody));
        return new BufferedReader(new StringReader(sanitizedBody));
    }
    
    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return sanitizeHtml(value);
    }
    
    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        
        String[] sanitizedValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            sanitizedValues[i] = sanitizeHtml(values[i]);
        }
        return sanitizedValues;
    }
    
    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> originalMap = super.getParameterMap();
        Map<String, String[]> sanitizedMap = new HashMap<>();
        
        for (Map.Entry<String, String[]> entry : originalMap.entrySet()) {
            String[] sanitizedValues = new String[entry.getValue().length];
            for (int i = 0; i < entry.getValue().length; i++) {
                sanitizedValues[i] = sanitizeHtml(entry.getValue()[i]);
            }
            sanitizedMap.put(entry.getKey(), sanitizedValues);
        }
        
        return sanitizedMap;
    }
    
    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        // 중요 헤더만 정화
        if ("User-Agent".equalsIgnoreCase(name) || "Referer".equalsIgnoreCase(name)) {
            return sanitizeHtml(value);
        }
        return value;
    }
    
    private String sanitizeJsonBody(String body) {
        if (!StringUtils.hasText(body)) {
            return body;
        }
        
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode sanitizedNode = sanitizeJsonNode(jsonNode);
            return objectMapper.writeValueAsString(sanitizedNode);
        } catch (JsonProcessingException e) {
            // JSON이 아닌 경우 일반 텍스트로 정화
            return sanitizeHtml(body);
        }
    }
    
    private JsonNode sanitizeJsonNode(JsonNode node) {
        if (node.isTextual()) {
            return new TextNode(sanitizeHtml(node.asText()));
        } else if (node.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            node.fieldNames().forEachRemaining(fieldName -> {
                objectNode.set(fieldName, sanitizeJsonNode(node.get(fieldName)));
            });
            return objectNode;
        } else if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                arrayNode.add(sanitizeJsonNode(item));
            }
            return arrayNode;
        }
        return node;
    }
    
    private String sanitizeHtml(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        // JSoup을 사용한 HTML 정화
        Safelist safelist = Safelist.none()
                .addTags("b", "i", "u", "strong", "em")
                .addAttributes("span", "class")
                .addProtocols("a", "href", "http", "https");
        
        String cleaned = Jsoup.clean(input, safelist);
        
        // 추가적인 정화 처리
        cleaned = cleaned.replaceAll("(?i)javascript:", "");
        cleaned = cleaned.replaceAll("(?i)vbscript:", "");
        cleaned = cleaned.replaceAll("(?i)data:", "");
        cleaned = cleaned.replaceAll("(?i)on\\w+\\s*=", "");
        
        return cleaned;
    }
}
```

## 3. InputSanitizer.java
```java
package com.routepick.backend.config.security.input;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
@Slf4j
public class InputSanitizer {
    
    // 한국어 닉네임 패턴 (한글, 영어, 숫자, 특수문자 제한)
    private static final Pattern KOREAN_NICKNAME_PATTERN = 
        Pattern.compile("^[\\uac00-\\ud7a3a-zA-Z0-9._-]{2,20}$");
    
    // 한국 휴대폰 번호 패턴
    private static final Pattern KOREAN_PHONE_PATTERN = 
        Pattern.compile("^01[016789]-?\\d{3,4}-?\\d{4}$");
    
    // 한국어 텍스트 패턴 (한글 포함 일반 텍스트)
    private static final Pattern KOREAN_TEXT_PATTERN = 
        Pattern.compile("^[\\uac00-\\ud7a3a-zA-Z0-9\\s.,!?()-]{1,500}$");
    
    // SQL Injection 방지 패턴
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute|script|declare|sp_)\\s",
        Pattern.CASE_INSENSITIVE
    );
    
    // NoSQL Injection 방지 패턴 (MongoDB)
    private static final Pattern NOSQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(\\$where|\\$ne|\\$gt|\\$lt|\\$gte|\\$lte|\\$in|\\$nin|\\$regex|\\$exists)",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 한국어 닉네임 검증
     */
    public boolean isValidKoreanNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return false;
        }
        
        // 기본 패턴 검증
        if (!KOREAN_NICKNAME_PATTERN.matcher(nickname).matches()) {
            return false;
        }
        
        // 예약어 차단
        String[] reservedWords = {"admin", "administrator", "root", "system", "null", "undefined"};
        String lowerNickname = nickname.toLowerCase();
        for (String word : reservedWords) {
            if (lowerNickname.contains(word)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 한국 휴대폰 번호 검증
     */
    public boolean isValidKoreanPhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return false;
        }
        
        // 하이픈 제거 후 검증
        String normalized = phoneNumber.replaceAll("-", "");
        return KOREAN_PHONE_PATTERN.matcher(phoneNumber).matches() &&
               normalized.length() >= 10 && normalized.length() <= 11;
    }
    
    /**
     * 한국어 텍스트 검증
     */
    public boolean isValidKoreanText(String text) {
        if (!StringUtils.hasText(text)) {
            return true; // 빈 텍스트 허용
        }
        
        return KOREAN_TEXT_PATTERN.matcher(text).matches();
    }
    
    /**
     * HTML 정화 처리
     */
    public String sanitizeHtml(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        return Jsoup.clean(input, Safelist.basic());
    }
    
    /**
     * 스크립트 태그 완전 제거
     */
    public String removeAllScripts(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        return Jsoup.clean(input, Safelist.none());
    }
    
    /**
     * SQL Injection 방지 검증
     */
    public boolean containsSqlInjection(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }
        
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }
    
    /**
     * NoSQL Injection 방지 검증
     */
    public boolean containsNoSqlInjection(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }
        
        return NOSQL_INJECTION_PATTERN.matcher(input).find();
    }
    
    /**
     * 포괄적인 입력 검증
     */
    public boolean isSafeInput(String input) {
        if (!StringUtils.hasText(input)) {
            return true;
        }
        
        // SQL/NoSQL Injection 차단
        if (containsSqlInjection(input) || containsNoSqlInjection(input)) {
            log.warn("Injection 공격 시도 감지: {}", input.substring(0, Math.min(50, input.length())));
            return false;
        }
        
        // XSS 기본 차단
        String cleaned = Jsoup.clean(input, Safelist.none());
        if (!input.equals(cleaned)) {
            log.warn("XSS 공격 시도 감지: {}", input.substring(0, Math.min(50, input.length())));
            return false;
        }
        
        return true;
    }
    
    /**
     * 다중 입력 정화 처리
     */
    public String sanitizeMultiInput(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String result = input;
        
        // 1. HTML 태그 제거
        result = Jsoup.clean(result, Safelist.basic());
        
        // 2. JavaScript 이벤트 제거
        result = result.replaceAll("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']", "");
        
        // 3. URL 스키마 제한
        result = result.replaceAll("(?i)javascript:", "");
        result = result.replaceAll("(?i)vbscript:", "");
        result = result.replaceAll("(?i)data:", "");
        
        // 4. 특수 문자 이스케이프
        result = result.replace("<", "&lt;");
        result = result.replace(">", "&gt;");
        result = result.replace("\"", "&quot;");
        result = result.replace("'", "&#39;");
        
        return result;
    }
}
```

## 4. @SafeText 커스텀 검증 애노테이션
```java
package com.routepick.backend.config.validation.annotation;

import com.routepick.backend.config.validation.validator.SafeTextValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeTextValidator.class)
@Documented
public @interface SafeText {
    
    String message() default "비정상적인 문자가 포함되어 있습니다";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * HTML 태그 허용 여부
     */
    boolean allowHtml() default false;
    
    /**
     * 최대 길이
     */
    int maxLength() default 500;
    
    /**
     * 한국어 전용 모드
     */
    boolean koreanOnly() default false;
}
```

```java
package com.routepick.backend.config.validation.validator;

import com.routepick.backend.config.security.input.InputSanitizer;
import com.routepick.backend.config.validation.annotation.SafeText;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SafeTextValidator implements ConstraintValidator<SafeText, String> {
    
    private final InputSanitizer inputSanitizer;
    
    private boolean allowHtml;
    private int maxLength;
    private boolean koreanOnly;
    
    private static final Pattern KOREAN_ONLY_PATTERN = 
        Pattern.compile("^[\\uac00-\\ud7a3\\s]*$");
    
    @Override
    public void initialize(SafeText constraintAnnotation) {
        this.allowHtml = constraintAnnotation.allowHtml();
        this.maxLength = constraintAnnotation.maxLength();
        this.koreanOnly = constraintAnnotation.koreanOnly();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(value)) {
            return true; // @NotBlank로 별도 처리
        }
        
        // 길이 검증
        if (value.length() > maxLength) {
            addCustomMessage(context, "최대 " + maxLength + "자까지 입력 가능합니다.");
            return false;
        }
        
        // 한국어 전용 검증
        if (koreanOnly && !KOREAN_ONLY_PATTERN.matcher(value).matches()) {
            addCustomMessage(context, "한글만 입력 가능합니다.");
            return false;
        }
        
        // HTML 허용 여부 검증
        if (!allowHtml) {
            String sanitized = inputSanitizer.removeAllScripts(value);
            if (!value.equals(sanitized)) {
                addCustomMessage(context, "HTML 태그는 사용할 수 없습니다.");
                return false;
            }
        }
        
        // 기본 보안 검증
        if (!inputSanitizer.isSafeInput(value)) {
            addCustomMessage(context, "비정상적인 문자가 감지되었습니다.");
            return false;
        }
        
        return true;
    }
    
    private void addCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}
```

## 5. 민감정보 보호 필터
```java
package com.routepick.backend.config.security.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataMaskingFilter implements Filter {
    
    private final ObjectMapper objectMapper;
    
    // 마스킹 패턴
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("(01[016789])-(\\d{3,4})-(\\d{4})");
    private static final Pattern TOKEN_PATTERN = 
        Pattern.compile("Bearer\\s+([A-Za-z0-9+/=]{20,})");
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("\"password\"\\s*:\\s*\"([^\"]+)\"");
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        // 응답 래퍼로 감싸서 데이터 마스킹
        DataMaskingResponseWrapper responseWrapper = 
            new DataMaskingResponseWrapper((HttpServletResponse) response);
        
        try {
            chain.doFilter(request, responseWrapper);
            
            // 응답 데이터 마스킹 수행
            String originalContent = responseWrapper.getContent();
            String maskedContent = maskSensitiveData(originalContent);
            
            // 마스킹된 데이터로 응답
            response.getOutputStream().write(maskedContent.getBytes());
            
        } catch (Exception e) {
            log.error("데이터 마스킹 처리 중 오류 발생", e);
            chain.doFilter(request, response);
        }
    }
    
    private String maskSensitiveData(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        String masked = content;
        
        try {
            // JSON 응답인 경우 구조적 마스킹
            if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
                masked = maskJsonResponse(content);
            } else {
                // 일반 텍스트 마스킹
                masked = maskPlainTextResponse(content);
            }
        } catch (Exception e) {
            log.warn("마스킹 처리 중 오류, 원본 데이터 반환: {}", e.getMessage());
            return content;
        }
        
        return masked;
    }
    
    private String maskJsonResponse(String jsonContent) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonContent);
        JsonNode maskedNode = maskJsonNode(rootNode);
        return objectMapper.writeValueAsString(maskedNode);
    }
    
    private JsonNode maskJsonNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode fieldValue = objectNode.get(fieldName);
                
                // 민감 필드 마스킹
                if (isSensitiveField(fieldName) && fieldValue.isTextual()) {
                    String maskedValue = maskFieldValue(fieldName, fieldValue.asText());
                    objectNode.put(fieldName, maskedValue);
                } else if (fieldValue.isObject() || fieldValue.isArray()) {
                    objectNode.set(fieldName, maskJsonNode(fieldValue));
                }
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) node)
                    .set(i, maskJsonNode(node.get(i)));
            }
        }
        
        return node;
    }
    
    private boolean isSensitiveField(String fieldName) {
        String lowerFieldName = fieldName.toLowerCase();
        return lowerFieldName.contains("email") || 
               lowerFieldName.contains("phone") || 
               lowerFieldName.contains("token") || 
               lowerFieldName.contains("password") ||
               lowerFieldName.contains("secret") ||
               lowerFieldName.contains("key");
    }
    
    private String maskFieldValue(String fieldName, String value) {
        String lowerFieldName = fieldName.toLowerCase();
        
        if (lowerFieldName.contains("email")) {
            return maskEmail(value);
        } else if (lowerFieldName.contains("phone")) {
            return maskPhone(value);
        } else if (lowerFieldName.contains("token") || lowerFieldName.contains("key")) {
            return maskToken(value);
        } else if (lowerFieldName.contains("password")) {
            return "***"; // 패스워드는 완전 마스킹
        } else {
            return value.length() > 3 ? 
                value.substring(0, 2) + "***" + value.substring(value.length() - 1) : 
                "***";
        }
    }
    
    private String maskPlainTextResponse(String content) {
        String masked = content;
        
        // 이메일 마스킹
        masked = EMAIL_PATTERN.matcher(masked).replaceAll(match -> {
            String localPart = match.group(1);
            String domain = match.group(2);
            String maskedLocal = localPart.length() > 2 ? 
                localPart.substring(0, 2) + "***" : "***";
            return maskedLocal + "@" + domain;
        });
        
        // 전화번호 마스킹
        masked = PHONE_PATTERN.matcher(masked).replaceAll("$1-****-$3");
        
        // 토큰 마스킹
        masked = TOKEN_PATTERN.matcher(masked).replaceAll(match -> {
            String token = match.group(1);
            return "Bearer " + (token.length() > 8 ? 
                token.substring(0, 8) + "***" : "***");
        });
        
        // 패스워드 마스킹
        masked = PASSWORD_PATTERN.matcher(masked).replaceAll("\"password\":\"***\"");
        
        return masked;
    }
    
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];
        
        String maskedLocal = localPart.length() > 2 ? 
            localPart.substring(0, 2) + "***" : "***";
        
        return maskedLocal + "@" + domain;
    }
    
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) {
            return phone;
        }
        
        // 010-1234-5678 -> 010-****-5678
        return phone.replaceAll("(01[016789])-(\\d{3,4})-(\\d{4})", "$1-****-$3");
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        
        return token.substring(0, 8) + "***";
    }
}
```

## 6. SecurityConfig 통합 설정
```java
package com.routepick.backend.config.security;

import com.routepick.backend.config.security.filter.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final XssProtectionFilter xssProtectionFilter;
    private final DataMaskingFilter dataMaskingFilter;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // XSS 방지 필터 추가
            .addFilterBefore(xssProtectionFilter, UsernamePasswordAuthenticationFilter.class)
            
            // 기본 보안 설정
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            );
        
        return http.build();
    }
    
    @Bean
    public FilterRegistrationBean<DataMaskingFilter> dataMaskingFilterRegistration() {
        FilterRegistrationBean<DataMaskingFilter> registration = 
            new FilterRegistrationBean<>(dataMaskingFilter);
        
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.setName("DataMaskingFilter");
        
        return registration;
    }
}
```

## 7. application.yml 설정 추가
```yaml
# XSS 및 입력 검증 설정
security:
  xss:
    enabled: true
    patterns:
      - "<script*"
      - "javascript:"
      - "vbscript:"
      - "on*="
    
  input-validation:
    korean:
      nickname-max-length: 20
      phone-validation: true
      text-max-length: 500
    
    sanitize:
      html-tags: true
      script-removal: true
      sql-injection: true
      nosql-injection: true
  
  data-masking:
    enabled: true
    patterns:
      email: "**@domain"
      phone: "***-****-***"
      token: "8chars***"
    fields:
      - email
      - phone
      - password
      - token
      - secret
      - key
```