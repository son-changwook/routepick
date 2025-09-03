# Step 8-3d: XSS 방지 및 입력 데이터 검증

> XSS 공격 방지, HTML 태그 제거/이스케이프 및 종합 입력 검증  
> 생성일: 2025-08-26  
> 기반 파일: step7-1f_xss_security.md, step3-3b_security_features.md

---

## 🎯 구현 목표

- **XSS 방지**: HTML 태그 제거 및 스크립트 차단
- **입력 검증 강화**: @Valid 확장 및 한국어 문자 검증
- **SQL Injection 방지**: 파라미터 바인딩 및 패턴 검증
- **NoSQL Injection 방지**: MongoDB/Redis 쿼리 보안
- **응답 보안**: JSON 응답 sanitization

---

## 🛡️ 1. XssProtectionFilter 구현

### XssProtectionFilter.java
```java
package com.routepick.filter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.security.xss.XssRequestWrapper;
import com.routepick.service.security.SecurityAuditLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * XSS 방어 필터
 * - 모든 입력값 정제
 * - HTML 태그 제거/이스케이프
 * - 스크립트 인젝션 방지
 * - 악성 패턴 탐지
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class XssProtectionFilter extends OncePerRequestFilter {
    
    private final ObjectMapper objectMapper;
    private final SecurityAuditLogger auditLogger;
    
    // XSS 공격 패턴
    private static final List<String> XSS_PATTERNS = Arrays.asList(
        "<script", "</script>", "javascript:", "onload=", "onerror=", 
        "onclick=", "onmouseover=", "onfocus=", "onblur=", "onchange=",
        "alert(", "confirm(", "prompt(", "eval(", "expression(",
        "vbscript:", "livescript:", "mocha:", "charset=", "src=javascript:",
        "href=javascript:", "lowsrc=javascript:", "background=javascript:"
    );
    
    // 허용된 HTML 태그 (안전한 태그들)
    private static final List<String> ALLOWED_TAGS = Arrays.asList(
        "b", "i", "u", "strong", "em", "br", "p", "div", "span"
    );
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String contentType = request.getContentType();
        String requestUri = request.getRequestURI();
        
        // XSS 필터 적용 대상 확인
        if (shouldApplyXssFilter(contentType, requestUri)) {
            // XSS 방어 래퍼 적용
            XssRequestWrapper xssRequest = new XssRequestWrapper(request, objectMapper);
            
            // 악성 패턴 사전 검사
            if (containsMaliciousPattern(request)) {
                log.warn("Malicious XSS pattern detected - URI: {}, IP: {}", 
                        requestUri, getClientIp(request));
                
                // 보안 이벤트 로깅
                auditLogger.logSecurityViolation("XSS_ATTACK", 
                    String.format("Malicious XSS pattern detected in request to %s", requestUri),
                    "HIGH", 
                    Map.of("requestUri", requestUri, "clientIp", getClientIp(request)));
                
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request content");
                return;
            }
            
            filterChain.doFilter(xssRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
    
    /**
     * XSS 필터 적용 여부 결정
     */
    private boolean shouldApplyXssFilter(String contentType, String requestUri) {
        // API 엔드포인트 제외 (JSON만 허용하므로 별도 처리)
        if (requestUri.startsWith("/api/")) {
            return contentType != null && contentType.contains("application/json");
        }
        
        // 웹 페이지 요청은 모두 적용
        return contentType != null && (
            contentType.contains("application/json") ||
            contentType.contains("application/x-www-form-urlencoded") ||
            contentType.contains("multipart/form-data") ||
            contentType.contains("text/")
        );
    }
    
    /**
     * 악성 패턴 사전 검사
     */
    private boolean containsMaliciousPattern(HttpServletRequest request) {
        try {
            // URL 파라미터 검사
            if (request.getQueryString() != null) {
                String queryString = request.getQueryString().toLowerCase();
                for (String pattern : XSS_PATTERNS) {
                    if (queryString.contains(pattern.toLowerCase())) {
                        return true;
                    }
                }
            }
            
            // 헤더 검사 (User-Agent, Referer 등)
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && containsXssPattern(userAgent)) {
                return true;
            }
            
            String referer = request.getHeader("Referer");
            if (referer != null && containsXssPattern(referer)) {
                return true;
            }
            
        } catch (Exception e) {
            log.error("Error checking malicious patterns", e);
        }
        
        return false;
    }
    
    /**
     * XSS 패턴 포함 여부 확인
     */
    private boolean containsXssPattern(String input) {
        if (input == null) return false;
        
        String lowerInput = input.toLowerCase();
        return XSS_PATTERNS.stream()
                .anyMatch(pattern -> lowerInput.contains(pattern.toLowerCase()));
    }
    
    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
```

---

## 🔧 2. XssRequestWrapper 구현

### XssRequestWrapper.java
```java
package com.routepick.security.xss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * XSS 방어를 위한 Request Wrapper
 * - 모든 파라미터와 바디 데이터 정제
 * - HTML 태그 제거/이스케이프
 * - JSON 데이터 재귀적 정제
 */
@Slf4j
public class XssRequestWrapper extends HttpServletRequestWrapper {
    
    private final ObjectMapper objectMapper;
    private byte[] rawData;
    private final Map<String, String[]> sanitizedParameters;
    
    // 안전한 HTML 태그 화이트리스트
    private static final Safelist ALLOWED_HTML = Safelist.none()
            .addTags("b", "i", "u", "strong", "em")
            .addAttributes("b", "class")
            .addAttributes("i", "class")
            .addAttributes("strong", "class")
            .addAttributes("em", "class");
    
    public XssRequestWrapper(HttpServletRequest request, ObjectMapper objectMapper) {
        super(request);
        this.objectMapper = objectMapper;
        this.sanitizedParameters = new HashMap<>();
        
        // 파라미터 사전 정제
        sanitizeParameters(request);
        
        // 요청 바디 사전 정제 (JSON 등)
        sanitizeRequestBody(request);
    }
    
    /**
     * 파라미터 정제
     */
    private void sanitizeParameters(HttpServletRequest request) {
        Map<String, String[]> originalParams = request.getParameterMap();
        
        for (Map.Entry<String, String[]> entry : originalParams.entrySet()) {
            String key = cleanXss(entry.getKey());
            String[] values = entry.getValue();
            String[] cleanValues = new String[values.length];
            
            for (int i = 0; i < values.length; i++) {
                cleanValues[i] = cleanXss(values[i]);
            }
            
            sanitizedParameters.put(key, cleanValues);
        }
    }
    
    /**
     * 요청 바디 정제
     */
    private void sanitizeRequestBody(HttpServletRequest request) {
        try {
            String contentType = request.getContentType();
            
            if (contentType != null && contentType.contains("application/json")) {
                // JSON 데이터 정제
                rawData = sanitizeJsonBody(request);
            } else {
                // 기타 데이터는 원본 유지
                rawData = IOUtils.toByteArray(request.getInputStream());
            }
            
        } catch (IOException e) {
            log.error("Failed to sanitize request body", e);
            rawData = new byte[0];
        }
    }
    
    /**
     * JSON 바디 정제
     */
    private byte[] sanitizeJsonBody(HttpServletRequest request) throws IOException {
        try (InputStream inputStream = request.getInputStream()) {
            String body = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            
            if (StringUtils.hasText(body)) {
                // JSON 파싱 및 재귀적 정제
                JsonNode rootNode = objectMapper.readTree(body);
                JsonNode sanitizedNode = sanitizeJsonNode(rootNode);
                
                // 정제된 JSON을 바이트 배열로 변환
                return objectMapper.writeValueAsBytes(sanitizedNode);
            }
        } catch (Exception e) {
            log.warn("Failed to sanitize JSON body: {}", e.getMessage());
        }
        
        return new byte[0];
    }
    
    /**
     * JSON 노드 재귀적 정제
     */
    private JsonNode sanitizeJsonNode(JsonNode node) {
        if (node.isTextual()) {
            // 텍스트 값 정제
            String cleanValue = cleanXss(node.asText());
            return objectMapper.valueToTree(cleanValue);
        } else if (node.isObject()) {
            // 객체 재귀 처리
            Map<String, Object> result = new HashMap<>();
            node.fields().forEachRemaining(entry -> {
                String cleanKey = cleanXss(entry.getKey());
                JsonNode cleanValue = sanitizeJsonNode(entry.getValue());
                result.put(cleanKey, cleanValue);
            });
            return objectMapper.valueToTree(result);
        } else if (node.isArray()) {
            // 배열 재귀 처리
            return objectMapper.valueToTree(
                node.elements()
                    .asSequence()
                    .map(this::sanitizeJsonNode)
                    .toList()
            );
        }
        
        return node; // 숫자, boolean 등은 그대로 반환
    }
    
    /**
     * XSS 패턴 정제
     */
    private String cleanXss(String value) {
        if (value == null) {
            return null;
        }
        
        // 1단계: HTML 태그 제거 (화이트리스트 기반)
        String cleaned = Jsoup.clean(value, ALLOWED_HTML);
        
        // 2단계: 스크립트 패턴 제거
        cleaned = removeScriptPatterns(cleaned);
        
        // 3단계: HTML 엔티티 이스케이프
        cleaned = escapeHtmlEntities(cleaned);
        
        // 4단계: SQL Injection 패턴 제거
        cleaned = removeSqlInjectionPatterns(cleaned);
        
        return cleaned;
    }
    
    /**
     * 스크립트 패턴 제거
     */
    private String removeScriptPatterns(String input) {
        if (input == null) return null;
        
        return input
                .replaceAll("(?i)<script[^>]*>.*?</script>", "")
                .replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "")
                .replaceAll("(?i)<object[^>]*>.*?</object>", "")
                .replaceAll("(?i)<embed[^>]*>.*?</embed>", "")
                .replaceAll("(?i)<link[^>]*>", "")
                .replaceAll("(?i)<meta[^>]*>", "")
                .replaceAll("(?i)javascript:", "")
                .replaceAll("(?i)vbscript:", "")
                .replaceAll("(?i)onload=", "")
                .replaceAll("(?i)onerror=", "")
                .replaceAll("(?i)onclick=", "")
                .replaceAll("(?i)onmouseover=", "")
                .replaceAll("(?i)expression\\(", "")
                .replaceAll("(?i)eval\\(", "")
                .replaceAll("(?i)alert\\(", "")
                .replaceAll("(?i)confirm\\(", "")
                .replaceAll("(?i)prompt\\(", "");
    }
    
    /**
     * HTML 엔티티 이스케이프
     */
    private String escapeHtmlEntities(String input) {
        if (input == null) return null;
        
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }
    
    /**
     * SQL Injection 패턴 제거
     */
    private String removeSqlInjectionPatterns(String input) {
        if (input == null) return null;
        
        return input
                .replaceAll("(?i)(union|select|insert|delete|update|drop|create|alter|exec|execute)", "")
                .replaceAll("(?i)(or|and)\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?", "")
                .replaceAll("(?i)['\"]\\s*(or|and)\\s*['\"]", "")
                .replaceAll("(?i)--|#|/\\*|\\*/", "")
                .replaceAll("(?i)\\bxp_\\w+", "")
                .replaceAll("(?i)\\bsp_\\w+", "");
    }
    
    @Override
    public String getParameter(String name) {
        String[] values = getParameterValues(name);
        return (values != null && values.length > 0) ? values[0] : null;
    }
    
    @Override
    public String[] getParameterValues(String name) {
        return sanitizedParameters.get(name);
    }
    
    @Override
    public Map<String, String[]> getParameterMap() {
        return sanitizedParameters;
    }
    
    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new CachedBodyServletInputStream(rawData);
    }
    
    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }
    
    /**
     * 캐시된 바디 입력 스트림
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final InputStream cachedBodyInputStream;
        
        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.cachedBodyInputStream = new ByteArrayInputStream(cachedBody);
        }
        
        @Override
        public boolean isFinished() {
            try {
                return cachedBodyInputStream.available() == 0;
            } catch (IOException e) {
                return false;
            }
        }
        
        @Override
        public boolean isReady() {
            return true;
        }
        
        @Override
        public void setReadListener(ReadListener readListener) {
            // 구현 불필요
        }
        
        @Override
        public int read() throws IOException {
            return cachedBodyInputStream.read();
        }
    }
}
```

---

## ✅ 3. InputSanitizer 구현

### InputSanitizer.java
```java
package com.routepick.security.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 입력 데이터 정제 및 검증
 * - @Valid 검증 강화
 * - 한국어 문자 검증
 * - 특수 문자 필터링
 */
@Slf4j
@Component
public class InputSanitizer {
    
    // 한국어 문자 패턴
    private static final Pattern KOREAN_PATTERN = Pattern.compile("[가-힣ㄱ-ㅎㅏ-ㅣ]+");
    
    // 영문 + 숫자 패턴
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    
    // 이메일 패턴
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    // 한국 휴대폰 번호 패턴
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$"
    );
    
    // SQL Injection 위험 패턴
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(union|select|insert|delete|update|drop|create|alter|exec|execute|script|javascript)"
    );
    
    // NoSQL Injection 위험 패턴
    private static final Pattern NOSQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(\\$where|\\$ne|\\$gt|\\$lt|\\$regex|\\$in|\\$nin|mapreduce|group)"
    );
    
    /**
     * 한국어 닉네임 검증 및 정제
     */
    public String sanitizeKoreanNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return null;
        }
        
        // 길이 검증 (2-10자)
        if (nickname.length() < 2 || nickname.length() > 10) {
            throw new IllegalArgumentException("닉네임은 2-10자 사이여야 합니다.");
        }
        
        // 한국어, 영문, 숫자만 허용
        if (!nickname.matches("^[가-힣a-zA-Z0-9]+$")) {
            throw new IllegalArgumentException("닉네임은 한글, 영문, 숫자만 사용 가능합니다.");
        }
        
        // 금지어 검증
        if (containsBannedWords(nickname)) {
            throw new IllegalArgumentException("사용할 수 없는 단어가 포함되어 있습니다.");
        }
        
        return nickname.trim();
    }
    
    /**
     * 이메일 검증 및 정제
     */
    public String sanitizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        
        email = email.trim().toLowerCase();
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
        
        // SQL Injection 패턴 검사
        if (SQL_INJECTION_PATTERN.matcher(email).find()) {
            throw new IllegalArgumentException("유효하지 않은 이메일 형식입니다.");
        }
        
        return email;
    }
    
    /**
     * 휴대폰 번호 검증 및 정제
     */
    public String sanitizePhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return null;
        }
        
        // 하이픈 제거 후 검증
        String cleanPhone = phoneNumber.replaceAll("-", "");
        
        if (!PHONE_PATTERN.matcher(cleanPhone).matches()) {
            throw new IllegalArgumentException("올바른 휴대폰 번호 형식이 아닙니다.");
        }
        
        // 표준 형식으로 변환 (010-1234-5678)
        if (cleanPhone.length() == 11) {
            return cleanPhone.substring(0, 3) + "-" + 
                   cleanPhone.substring(3, 7) + "-" + 
                   cleanPhone.substring(7);
        }
        
        return phoneNumber;
    }
    
    /**
     * 일반 텍스트 검증 및 정제
     */
    public String sanitizeText(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        
        // 길이 검증
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(
                String.format("텍스트는 %d자를 초과할 수 없습니다.", maxLength));
        }
        
        // SQL/NoSQL Injection 패턴 검사
        if (SQL_INJECTION_PATTERN.matcher(text).find()) {
            throw new IllegalArgumentException("유효하지 않은 문자가 포함되어 있습니다.");
        }
        
        if (NOSQL_INJECTION_PATTERN.matcher(text).find()) {
            throw new IllegalArgumentException("유효하지 않은 문자가 포함되어 있습니다.");
        }
        
        return text.trim();
    }
    
    /**
     * 패스워드 정제 및 검증
     */
    public String sanitizePassword(String password) {
        if (!StringUtils.hasText(password)) {
            return null;
        }
        
        // 길이 검증 (8-50자)
        if (password.length() < 8 || password.length() > 50) {
            throw new IllegalArgumentException("패스워드는 8-50자 사이여야 합니다.");
        }
        
        // 복잡도 검증
        if (!isValidPasswordComplexity(password)) {
            throw new IllegalArgumentException(
                "패스워드는 영문 대소문자, 숫자, 특수문자를 포함해야 합니다.");
        }
        
        return password; // 패스워드는 원본 유지
    }
    
    /**
     * 검색 쿼리 정제
     */
    public String sanitizeSearchQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        
        // 길이 제한 (100자)
        if (query.length() > 100) {
            query = query.substring(0, 100);
        }
        
        // 위험한 패턴 제거
        query = SQL_INJECTION_PATTERN.matcher(query).replaceAll("");
        query = NOSQL_INJECTION_PATTERN.matcher(query).replaceAll("");
        
        // 특수 문자 일부 제거
        query = query.replaceAll("[<>\"'%;()&+]", "");
        
        return query.trim();
    }
    
    /**
     * URL 검증 및 정제
     */
    public String sanitizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        
        // 길이 제한
        if (url.length() > 2048) {
            throw new IllegalArgumentException("URL이 너무 깁니다.");
        }
        
        // 허용된 프로토콜만 허용
        if (!url.matches("^https?://.*")) {
            throw new IllegalArgumentException("HTTP 또는 HTTPS URL만 허용됩니다.");
        }
        
        // 위험한 패턴 검사
        if (url.toLowerCase().contains("javascript:") || 
            url.toLowerCase().contains("data:") ||
            url.toLowerCase().contains("vbscript:")) {
            throw new IllegalArgumentException("유효하지 않은 URL입니다.");
        }
        
        return url;
    }
    
    /**
     * 금지어 포함 여부 확인
     */
    private boolean containsBannedWords(String text) {
        String[] bannedWords = {
            "관리자", "admin", "운영자", "시스템", "테스트", "test"
        };
        
        String lowerText = text.toLowerCase();
        for (String banned : bannedWords) {
            if (lowerText.contains(banned.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 패스워드 복잡도 검증
     */
    private boolean isValidPasswordComplexity(String password) {
        // 최소 3가지 종류의 문자 포함
        int complexity = 0;
        
        if (password.matches(".*[a-z].*")) complexity++; // 소문자
        if (password.matches(".*[A-Z].*")) complexity++; // 대문자
        if (password.matches(".*[0-9].*")) complexity++; // 숫자
        if (password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) complexity++; // 특수문자
        
        return complexity >= 3;
    }
}
```

---

## 📊 4. 민감정보 마스킹 필터

### DataMaskingFilter.java
```java
package com.routepick.filter.security;

import com.routepick.security.SensitiveDataMasker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 응답 데이터 마스킹 필터
 * - 민감정보 자동 마스킹
 * - 로그 및 응답에서 개인정보 보호
 */
@Slf4j
@Component
@Order(100) // 낮은 우선순위 (응답 처리 마지막)
@RequiredArgsConstructor
public class DataMaskingFilter extends OncePerRequestFilter {
    
    private final SensitiveDataMasker dataMasker;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestUri = request.getRequestURI();
        
        // 민감정보를 포함할 가능성이 있는 API만 마스킹 적용
        if (shouldApplyMasking(requestUri)) {
            ResponseWrapper responseWrapper = new ResponseWrapper(response);
            
            filterChain.doFilter(request, responseWrapper);
            
            // 응답 데이터 마스킹
            String originalContent = responseWrapper.getContent();
            String maskedContent = dataMasker.mask(originalContent);
            
            // 마스킹된 내용을 실제 응답에 작성
            response.getOutputStream().write(maskedContent.getBytes(StandardCharsets.UTF_8));
            
            // 로깅 (마스킹된 데이터로)
            if (log.isDebugEnabled()) {
                log.debug("Response masked for {}: {} -> {}", 
                        requestUri, 
                        truncateForLog(originalContent),
                        truncateForLog(maskedContent));
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
    
    /**
     * 마스킹 적용 대상 판단
     */
    private boolean shouldApplyMasking(String requestUri) {
        return requestUri.startsWith("/api/v1/users/") ||
               requestUri.startsWith("/api/v1/admin/users") ||
               requestUri.contains("/profile") ||
               requestUri.contains("/personal");
    }
    
    /**
     * 로그용 문자열 자르기
     */
    private String truncateForLog(String content) {
        if (content == null) return "null";
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }
    
    /**
     * 응답 래퍼 클래스
     */
    private static class ResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private PrintWriter writer;
        
        public ResponseWrapper(HttpServletResponse response) {
            super(response);
        }
        
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }
                
                @Override
                public void setWriteListener(WriteListener writeListener) {
                    // 구현 불필요
                }
                
                @Override
                public void write(int b) throws IOException {
                    outputStream.write(b);
                }
            };
        }
        
        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            }
            return writer;
        }
        
        public String getContent() {
            if (writer != null) {
                writer.flush();
            }
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
```

---

## 📋 5. 커스텀 검증 어노테이션

### @SafeText 어노테이션
```java
package com.routepick.validation.annotation;

import com.routepick.validation.validator.SafeTextValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 안전한 텍스트 검증 어노테이션
 * - XSS 방지
 * - SQL Injection 방지
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {
    String message() default "유효하지 않은 문자가 포함되어 있습니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    int maxLength() default 255;
    boolean allowHtml() default false;
    boolean allowKorean() default true;
}
```

### SafeTextValidator.java
```java
package com.routepick.validation.validator;

import com.routepick.security.validation.InputSanitizer;
import com.routepick.validation.annotation.SafeText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 안전한 텍스트 검증기
 */
@Component
@RequiredArgsConstructor
public class SafeTextValidator implements ConstraintValidator<SafeText, String> {
    
    private final InputSanitizer inputSanitizer;
    private SafeText annotation;
    
    @Override
    public void initialize(SafeText constraintAnnotation) {
        this.annotation = constraintAnnotation;
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null 값은 @NotNull에서 처리
        }
        
        try {
            inputSanitizer.sanitizeText(value, annotation.maxLength());
            return true;
        } catch (IllegalArgumentException e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(e.getMessage())
                   .addConstraintViolation();
            return false;
        }
    }
}
```

---

## ✅ 구현 완료 체크리스트

- [x] XssProtectionFilter 구현 (악성 패턴 사전 탐지)
- [x] XssRequestWrapper 구현 (HTML 태그 제거/이스케이프)
- [x] InputSanitizer 구현 (한국어 지원 + 종합 검증)
- [x] SQL/NoSQL Injection 방지
- [x] DataMaskingFilter 구현 (응답 민감정보 마스킹)
- [x] @SafeText 커스텀 검증 어노테이션
- [x] 한국어 특화 검증 (닉네임, 휴대폰번호)
- [x] JSON 데이터 재귀적 정제

---

*Step 8-3d 완료: XSS 방지 및 입력 데이터 검증*
*다음 단계: step8-3e_response_security_final.md (민감정보 보호 완성)*