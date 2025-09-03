# Step 7-1i: Phase 2 - HIGH 보안 수정

> XSS, SQL Injection 방어 및 세션 보안 구현  
> 생성일: 2025-08-22  
> 우선순위: 🟠 HIGH (1일 내 적용)

---

## 🟠 1. XSS 방어 구현

### XssProtectionFilter.java (신규)
```java
package com.routepick.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.security.xss.XssRequestWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * XSS 방어 필터
 * - 모든 입력값 정제
 * - HTML 태그 제거
 * - 스크립트 인젝션 방지
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class XssProtectionFilter extends OncePerRequestFilter {
    
    private final ObjectMapper objectMapper;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // Content-Type 확인
        String contentType = request.getContentType();
        
        // JSON, Form 데이터에 대해 XSS 필터 적용
        if (contentType != null && (contentType.contains("application/json") || 
                                    contentType.contains("application/x-www-form-urlencoded") ||
                                    contentType.contains("multipart/form-data"))) {
            
            // XSS 방어 래퍼 적용
            XssRequestWrapper xssRequest = new XssRequestWrapper(request, objectMapper);
            
            // Security Headers 설정
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-XSS-Protection", "1; mode=block");
            response.setHeader("Content-Security-Policy", 
                "default-src 'self'; script-src 'self' 'unsafe-inline' https://www.google.com/recaptcha/; " +
                "style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:;");
            
            filterChain.doFilter(xssRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
```

### XssRequestWrapper.java
```java
package com.routepick.security.xss;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * XSS 방어를 위한 Request Wrapper
 * - 모든 파라미터와 바디 데이터 정제
 */
@Slf4j
public class XssRequestWrapper extends HttpServletRequestWrapper {
    
    private final ObjectMapper objectMapper;
    private byte[] rawData;
    
    // 안전한 HTML 태그 화이트리스트
    private static final Safelist SAFELIST = Safelist.none()
        .addTags("b", "i", "u", "strong", "em", "mark", "small", "del", "ins", "sub", "sup")
        .addAttributes("a", "href", "title")
        .addProtocols("a", "href", "http", "https");
    
    public XssRequestWrapper(HttpServletRequest request, ObjectMapper objectMapper) {
        super(request);
        this.objectMapper = objectMapper;
        
        try {
            // Request Body 읽기
            InputStream inputStream = request.getInputStream();
            this.rawData = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            log.error("Request body 읽기 실패", e);
            this.rawData = new byte[0];
        }
    }
    
    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return cleanXss(value);
    }
    
    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        
        String[] cleanValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleanValues[i] = cleanXss(values[i]);
        }
        return cleanValues;
    }
    
    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> parameterMap = super.getParameterMap();
        Map<String, String[]> cleanMap = new HashMap<>();
        
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = entry.getValue();
            String[] cleanValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleanValues[i] = cleanXss(values[i]);
            }
            cleanMap.put(entry.getKey(), cleanValues);
        }
        
        return cleanMap;
    }
    
    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return cleanXss(value);
    }
    
    @Override
    public ServletInputStream getInputStream() throws IOException {
        // JSON 데이터 정제
        if (rawData.length > 0) {
            String content = new String(rawData, "UTF-8");
            
            // JSON 파싱 및 정제
            if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
                try {
                    Map<String, Object> jsonData = objectMapper.readValue(content, Map.class);
                    Map<String, Object> cleanData = cleanJsonData(jsonData);
                    String cleanJson = objectMapper.writeValueAsString(cleanData);
                    byte[] cleanBytes = cleanJson.getBytes("UTF-8");
                    
                    return new ServletInputStream() {
                        private int index = 0;
                        
                        @Override
                        public boolean isFinished() {
                            return index >= cleanBytes.length;
                        }
                        
                        @Override
                        public boolean isReady() {
                            return true;
                        }
                        
                        @Override
                        public void setReadListener(ReadListener listener) {}
                        
                        @Override
                        public int read() throws IOException {
                            if (index >= cleanBytes.length) {
                                return -1;
                            }
                            return cleanBytes[index++] & 0xFF;
                        }
                    };
                } catch (Exception e) {
                    log.error("JSON 정제 실패", e);
                }
            }
        }
        
        return super.getInputStream();
    }
    
    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }
    
    /**
     * XSS 정제
     */
    private String cleanXss(String value) {
        if (value == null) {
            return null;
        }
        
        // Jsoup을 사용한 HTML 정제
        String cleaned = Jsoup.clean(value, SAFELIST);
        
        // 추가 위험 패턴 제거
        cleaned = cleaned.replaceAll("(?i)<script.*?>.*?</script.*?>", "")
                        .replaceAll("(?i)<.*?javascript:.*?>", "")
                        .replaceAll("(?i)<iframe.*?>.*?</iframe.*?>", "")
                        .replaceAll("(?i)<object.*?>.*?</object.*?>", "")
                        .replaceAll("(?i)<embed.*?>.*?</embed.*?>", "")
                        .replaceAll("(?i)on\\w+\\s*=", "")
                        .replaceAll("(?i)expression\\s*\\(", "")
                        .replaceAll("(?i)vbscript\\s*:", "")
                        .replaceAll("(?i)data\\s*:", "");
        
        return cleaned;
    }
    
    /**
     * JSON 데이터 재귀적 정제
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> cleanJsonData(Map<String, Object> data) {
        Map<String, Object> cleanData = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            
            if (value instanceof String) {
                cleanData.put(entry.getKey(), cleanXss((String) value));
            } else if (value instanceof Map) {
                cleanData.put(entry.getKey(), cleanJsonData((Map<String, Object>) value));
            } else if (value instanceof List) {
                cleanData.put(entry.getKey(), cleanList((List<?>) value));
            } else {
                cleanData.put(entry.getKey(), value);
            }
        }
        
        return cleanData;
    }
    
    /**
     * 리스트 데이터 정제
     */
    private List<?> cleanList(List<?> list) {
        return list.stream()
            .map(item -> {
                if (item instanceof String) {
                    return cleanXss((String) item);
                } else if (item instanceof Map) {
                    return cleanJsonData((Map<String, Object>) item);
                } else {
                    return item;
                }
            })
            .toList();
    }
}
```

### XssProtectionValidator.java (커스텀 검증)
```java
package com.routepick.validation.validator;

import com.routepick.validation.annotation.XssProtection;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * XSS 보호 검증기
 */
public class XssProtectionValidator implements ConstraintValidator<XssProtection, String> {
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        
        // HTML 태그 검사
        String cleaned = Jsoup.clean(value, Safelist.none());
        
        // 정제 전후 비교
        if (!value.equals(cleaned)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("HTML 태그나 스크립트는 사용할 수 없습니다")
                   .addConstraintViolation();
            return false;
        }
        
        // 위험한 패턴 검사
        String[] dangerousPatterns = {
            "javascript:", "vbscript:", "onload=", "onclick=", "onerror=",
            "<script", "</script", "<iframe", "<object", "<embed",
            "expression(", "import(", "document.", "window.", "eval("
        };
        
        String lowerValue = value.toLowerCase();
        for (String pattern : dangerousPatterns) {
            if (lowerValue.contains(pattern)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("보안상 위험한 내용이 포함되어 있습니다")
                       .addConstraintViolation();
                return false;
            }
        }
        
        return true;
    }
}
```

---

## 🟠 2. SQL Injection 방어 구현

### SqlInjectionProtectionValidator.java
```java
package com.routepick.validation.validator;

import com.routepick.validation.annotation.SqlInjectionProtection;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * SQL Injection 방어 검증기
 */
@Slf4j
public class SqlInjectionProtectionValidator implements ConstraintValidator<SqlInjectionProtection, String> {
    
    // SQL Injection 패턴
    private static final Pattern[] SQL_PATTERNS = {
        Pattern.compile(".*([';]+|(--)+).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(ALTER|CREATE|DELETE|DROP|EXEC(UTE)?|INSERT( INTO)?|MERGE|SELECT|UPDATE|UNION( ALL)?).*", 
                       Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(\\bOR\\b.{1,10}[\\=\\<\\>\\(]).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bWHERE\\b.*\\b1\\s*=\\s*1.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bAND\\b.*\\b1\\s*=\\s*1.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bSLEEP\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bBENCHMARK\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bWAITFOR\\s+DELAY.*", Pattern.CASE_INSENSITIVE)
    };
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        
        // SQL Injection 패턴 검사
        for (Pattern pattern : SQL_PATTERNS) {
            if (pattern.matcher(value).matches()) {
                log.warn("SQL Injection 패턴 감지: value={}", value);
                
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("입력값에 허용되지 않는 문자가 포함되어 있습니다")
                       .addConstraintViolation();
                return false;
            }
        }
        
        // 특수문자 제한 (닉네임 등에 적용)
        if (containsDangerousCharacters(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("특수문자는 사용할 수 없습니다")
                   .addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    /**
     * 위험한 특수문자 검사
     */
    private boolean containsDangerousCharacters(String value) {
        // 허용되는 특수문자: _ - @ .
        String allowedSpecialChars = "_\\-@\\.";
        String pattern = "[^a-zA-Z0-9가-힣" + allowedSpecialChars + "]";
        
        return Pattern.compile(pattern).matcher(value).find();
    }
}
```

### SecureQueryBuilder.java (안전한 쿼리 빌더)
```java
package com.routepick.security.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 안전한 쿼리 빌더
 * - 파라미터 바인딩 강제
 * - 동적 쿼리 검증
 */
@Slf4j
@Component
public class SecureQueryBuilder {
    
    private static final Pattern SAFE_COLUMN_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Pattern SAFE_TABLE_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    
    /**
     * 안전한 ORDER BY 절 생성
     */
    public String buildOrderByClause(String sortColumn, String sortDirection) {
        // 컬럼명 검증
        if (!isValidColumnName(sortColumn)) {
            log.warn("유효하지 않은 정렬 컬럼: {}", sortColumn);
            return "ORDER BY id ASC"; // 기본값
        }
        
        // 정렬 방향 검증
        String direction = "ASC".equalsIgnoreCase(sortDirection) || "DESC".equalsIgnoreCase(sortDirection) 
                          ? sortDirection.toUpperCase() : "ASC";
        
        return String.format("ORDER BY %s %s", sortColumn, direction);
    }
    
    /**
     * 안전한 WHERE IN 절 생성
     */
    public String buildWhereInClause(String columnName, List<?> values, MapSqlParameterSource params) {
        if (!isValidColumnName(columnName)) {
            throw new IllegalArgumentException("유효하지 않은 컬럼명: " + columnName);
        }
        
        if (values == null || values.isEmpty()) {
            return "1=0"; // 항상 false
        }
        
        // 파라미터 바인딩
        String paramName = columnName + "List";
        params.addValue(paramName, values);
        
        return String.format("%s IN (:%s)", columnName, paramName);
    }
    
    /**
     * 컬럼명 검증
     */
    private boolean isValidColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return false;
        }
        
        // 화이트리스트 방식
        List<String> allowedColumns = List.of(
            "id", "email", "nickname", "created_at", "updated_at",
            "status", "level", "score", "rating", "view_count"
        );
        
        return allowedColumns.contains(columnName.toLowerCase()) && 
               SAFE_COLUMN_PATTERN.matcher(columnName).matches();
    }
    
    /**
     * 테이블명 검증
     */
    public boolean isValidTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        
        // 화이트리스트 방식
        List<String> allowedTables = List.of(
            "users", "user_profiles", "routes", "gyms", "posts", "comments"
        );
        
        return allowedTables.contains(tableName.toLowerCase()) && 
               SAFE_TABLE_PATTERN.matcher(tableName).matches();
    }
}
```

---

## 🟠 3. 세션 보안 구현

### SessionSecurityService.java
```java
package com.routepick.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 세션 보안 서비스
 * - 세션 하이재킹 감지
 * - 동시 로그인 제한
 * - 비정상 접근 패턴 감지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSecurityService {
    
    private static final String SESSION_PREFIX = "session:";
    private static final String ACTIVE_SESSIONS_PREFIX = "active:sessions:";
    private static final String SESSION_HISTORY_PREFIX = "session:history:";
    
    @Value("${security.session.max-concurrent:5}")
    private int maxConcurrentSessions;
    
    @Value("${security.session.hijack-threshold:3}")
    private int hijackThreshold;
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 세션 생성 및 등록
     */
    public String createSession(Long userId, String deviceId, String ipAddress, String userAgent) {
        String sessionId = UUID.randomUUID().toString();
        
        // 세션 정보 저장
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("user_id", userId);
        sessionData.put("device_id", deviceId);
        sessionData.put("ip_address", ipAddress);
        sessionData.put("user_agent", userAgent);
        sessionData.put("created_at", LocalDateTime.now().toString());
        sessionData.put("last_activity", LocalDateTime.now().toString());
        sessionData.put("ip_changes", 0);
        
        String sessionKey = SESSION_PREFIX + sessionId;
        redisTemplate.opsForHash().putAll(sessionKey, sessionData);
        redisTemplate.expire(sessionKey, 24, TimeUnit.HOURS);
        
        // 활성 세션 목록에 추가
        addToActiveSessions(userId, sessionId);
        
        // 동시 세션 제한 확인
        enforceSessionLimit(userId);
        
        log.info("세션 생성: userId={}, sessionId={}, ip={}", userId, sessionId, ipAddress);
        
        return sessionId;
    }
    
    /**
     * 세션 검증 및 하이재킹 감지
     */
    public boolean validateSession(String sessionId, String currentIp, String currentUserAgent) {
        String sessionKey = SESSION_PREFIX + sessionId;
        Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);
        
        if (sessionData.isEmpty()) {
            log.warn("존재하지 않는 세션: sessionId={}", sessionId);
            return false;
        }
        
        String originalIp = (String) sessionData.get("ip_address");
        String originalUserAgent = (String) sessionData.get("user_agent");
        Integer ipChanges = (Integer) sessionData.get("ip_changes");
        
        // User-Agent 변경 감지 (하이재킹 가능성)
        if (!currentUserAgent.equals(originalUserAgent)) {
            log.error("User-Agent 변경 감지 (세션 하이재킹 의심): sessionId={}, original={}, current={}", 
                     sessionId, originalUserAgent, currentUserAgent);
            invalidateSession(sessionId);
            return false;
        }
        
        // IP 변경 감지
        if (!currentIp.equals(originalIp)) {
            ipChanges++;
            log.warn("IP 변경 감지: sessionId={}, changes={}, original={}, current={}", 
                    sessionId, ipChanges, originalIp, currentIp);
            
            // IP 변경 횟수가 임계치 초과 (하이재킹 가능성)
            if (ipChanges >= hijackThreshold) {
                log.error("과도한 IP 변경 (세션 하이재킹 의심): sessionId={}, changes={}", sessionId, ipChanges);
                invalidateSession(sessionId);
                recordSuspiciousActivity((Long) sessionData.get("user_id"), "SESSION_HIJACK", currentIp);
                return false;
            }
            
            // IP 변경 횟수 업데이트
            redisTemplate.opsForHash().put(sessionKey, "ip_changes", ipChanges);
            redisTemplate.opsForHash().put(sessionKey, "ip_address", currentIp);
        }
        
        // 마지막 활동 시간 업데이트
        redisTemplate.opsForHash().put(sessionKey, "last_activity", LocalDateTime.now().toString());
        redisTemplate.expire(sessionKey, 24, TimeUnit.HOURS);
        
        return true;
    }
    
    /**
     * 비정상 접근 패턴 감지
     */
    public boolean detectAbnormalAccess(Long userId, String ipAddress, LocalDateTime loginTime) {
        String historyKey = SESSION_HISTORY_PREFIX + userId;
        
        // 최근 로그인 이력 조회
        List<Map<String, Object>> recentLogins = getRecentLoginHistory(userId, 5);
        
        for (Map<String, Object> login : recentLogins) {
            String prevIp = (String) login.get("ip_address");
            LocalDateTime prevTime = LocalDateTime.parse((String) login.get("login_time"));
            
            // 짧은 시간 내 다른 지역에서 로그인 (불가능한 이동)
            if (!prevIp.equals(ipAddress)) {
                long minutesDiff = ChronoUnit.MINUTES.between(prevTime, loginTime);
                
                // 5분 이내 다른 IP에서 로그인 시도
                if (minutesDiff < 5) {
                    log.error("불가능한 위치 이동 감지: userId={}, prevIp={}, currentIp={}, minutes={}", 
                             userId, prevIp, ipAddress, minutesDiff);
                    return true;
                }
                
                // IP 지역 확인 (다른 국가인 경우)
                if (isDifferentCountry(prevIp, ipAddress) && minutesDiff < 120) {
                    log.error("국가간 빠른 이동 감지: userId={}, prevIp={}, currentIp={}, minutes={}", 
                             userId, prevIp, ipAddress, minutesDiff);
                    return true;
                }
            }
        }
        
        // 로그인 이력 저장
        saveLoginHistory(userId, ipAddress, loginTime);
        
        return false;
    }
    
    /**
     * 동시 세션 제한
     */
    private void enforceSessionLimit(Long userId) {
        String activeKey = ACTIVE_SESSIONS_PREFIX + userId;
        Set<Object> activeSessions = redisTemplate.opsForSet().members(activeKey);
        
        if (activeSessions != null && activeSessions.size() > maxConcurrentSessions) {
            // 가장 오래된 세션 종료
            List<SessionInfo> sessions = new ArrayList<>();
            for (Object sessionId : activeSessions) {
                String sessionKey = SESSION_PREFIX + sessionId;
                Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);
                if (!sessionData.isEmpty()) {
                    sessions.add(new SessionInfo(
                        (String) sessionId,
                        LocalDateTime.parse((String) sessionData.get("created_at"))
                    ));
                }
            }
            
            // 생성 시간 기준 정렬
            sessions.sort(Comparator.comparing(SessionInfo::createdAt));
            
            // 초과된 세션 종료
            int sessionsToRemove = sessions.size() - maxConcurrentSessions;
            for (int i = 0; i < sessionsToRemove; i++) {
                invalidateSession(sessions.get(i).sessionId());
                log.info("동시 세션 제한으로 세션 종료: userId={}, sessionId={}", 
                        userId, sessions.get(i).sessionId());
            }
        }
    }
    
    /**
     * 세션 무효화
     */
    public void invalidateSession(String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;
        Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);
        
        if (!sessionData.isEmpty()) {
            Long userId = (Long) sessionData.get("user_id");
            
            // 세션 데이터 삭제
            redisTemplate.delete(sessionKey);
            
            // 활성 세션 목록에서 제거
            String activeKey = ACTIVE_SESSIONS_PREFIX + userId;
            redisTemplate.opsForSet().remove(activeKey, sessionId);
            
            log.info("세션 무효화: sessionId={}, userId={}", sessionId, userId);
        }
    }
    
    /**
     * 활성 세션 목록에 추가
     */
    private void addToActiveSessions(Long userId, String sessionId) {
        String activeKey = ACTIVE_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().add(activeKey, sessionId);
        redisTemplate.expire(activeKey, 30, TimeUnit.DAYS);
    }
    
    /**
     * 로그인 이력 저장
     */
    private void saveLoginHistory(Long userId, String ipAddress, LocalDateTime loginTime) {
        String historyKey = SESSION_HISTORY_PREFIX + userId;
        
        Map<String, Object> loginData = new HashMap<>();
        loginData.put("ip_address", ipAddress);
        loginData.put("login_time", loginTime.toString());
        
        redisTemplate.opsForList().leftPush(historyKey, loginData);
        redisTemplate.opsForList().trim(historyKey, 0, 99); // 최근 100개만 유지
        redisTemplate.expire(historyKey, 30, TimeUnit.DAYS);
    }
    
    /**
     * 최근 로그인 이력 조회
     */
    private List<Map<String, Object>> getRecentLoginHistory(Long userId, int count) {
        String historyKey = SESSION_HISTORY_PREFIX + userId;
        List<Object> history = redisTemplate.opsForList().range(historyKey, 0, count - 1);
        
        if (history == null) {
            return Collections.emptyList();
        }
        
        return history.stream()
            .map(obj -> (Map<String, Object>) obj)
            .toList();
    }
    
    /**
     * IP 국가 비교 (간단한 구현)
     */
    private boolean isDifferentCountry(String ip1, String ip2) {
        // 실제로는 IP 지역 정보 API 사용 필요
        // 여기서는 IP 대역으로 간단히 판단
        String[] parts1 = ip1.split("\\.");
        String[] parts2 = ip2.split("\\.");
        
        // 첫 번째 옥텟이 다르면 다른 국가로 가정
        return !parts1[0].equals(parts2[0]);
    }
    
    /**
     * 의심스러운 활동 기록
     */
    private void recordSuspiciousActivity(Long userId, String activityType, String details) {
        // AuditService 호출
        log.error("의심스러운 활동: userId={}, type={}, details={}", userId, activityType, details);
    }
    
    /**
     * 세션 정보 내부 클래스
     */
    private record SessionInfo(String sessionId, LocalDateTime createdAt) {}
}
```

---

*Step 7-1i 완료: Phase 2 HIGH 보안 수정 구현*