# step8-3e_response_security_final.md
# 민감정보 보호 완성

## 1. 고급 데이터 마스킹 서비스
```java
package com.routepick.backend.config.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataMaskingService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 마스킹 패턴 캐시
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
    
    // 마스킹 통계 키
    private static final String MASKING_STATS_KEY = "security:masking:stats";
    private static final String VIOLATION_TRACK_KEY = "security:masking:violations";
    
    /**
     * 이메일 마스킹 (ab***@example.com)
     */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        recordMaskingEvent("email");
        
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];
        
        if (localPart.length() <= 2) {
            return "***@" + domain;
        }
        
        return localPart.substring(0, 2) + "***@" + domain;
    }
    
    /**
     * 전화번호 마스킹 (010-****-1234)
     */
    public String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 8) {
            return phone;
        }
        
        recordMaskingEvent("phone");
        
        // 한국 휴대폰 패턴 처리
        Pattern phonePattern = getOrCreatePattern("phone", 
            "(01[016789])(-?)(\\d{3,4})(-?)(\\d{4})");
        
        return phonePattern.matcher(phone).replaceAll("$1$2****$4$5");
    }
    
    /**
     * JWT 토큰 마스킹 (앞 8자리만 표시)
     */
    public String maskToken(String token) {
        if (token == null || token.length() < 16) {
            return "***";
        }
        
        recordMaskingEvent("token");
        
        // Bearer 토큰 처리
        if (token.startsWith("Bearer ")) {
            String actualToken = token.substring(7);
            return "Bearer " + (actualToken.length() > 8 ? 
                actualToken.substring(0, 8) + "***" : "***");
        }
        
        return token.length() > 8 ? 
            token.substring(0, 8) + "***" : "***";
    }
    
    /**
     * 신용카드 마스킹 (****-****-****-1234)
     */
    public String maskCreditCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 12) {
            return cardNumber;
        }
        
        recordMaskingEvent("card");
        
        String cleaned = cardNumber.replaceAll("[^0-9]", "");
        if (cleaned.length() < 12) {
            return "****-****-****-****";
        }
        
        String lastFour = cleaned.substring(cleaned.length() - 4);
        return "****-****-****-" + lastFour;
    }
    
    /**
     * 주민등록번호 마스킹 (123456-1******)
     */
    public String maskResidentNumber(String residentNumber) {
        if (residentNumber == null || residentNumber.length() < 13) {
            return residentNumber;
        }
        
        recordMaskingEvent("resident");
        
        Pattern residentPattern = getOrCreatePattern("resident", 
            "(\\d{6})(-?)(\\d{1})(\\d{6})");
        
        return residentPattern.matcher(residentNumber).replaceAll("$1$2$3******");
    }
    
    /**
     * 은행 계좌번호 마스킹 (123-45-******)
     */
    public String maskBankAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 8) {
            return accountNumber;
        }
        
        recordMaskingEvent("account");
        
        // 계좌번호 앞 6자리만 표시
        String cleaned = accountNumber.replaceAll("[^0-9-]", "");
        if (cleaned.length() <= 6) {
            return "******";
        }
        
        return cleaned.substring(0, 6) + "******";
    }
    
    /**
     * IP 주소 마스킹 (192.168.***.** )
     */
    public String maskIpAddress(String ipAddress) {
        if (ipAddress == null || !ipAddress.contains(".")) {
            return ipAddress;
        }
        
        recordMaskingEvent("ip");
        
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return ipAddress;
        }
        
        return parts[0] + "." + parts[1] + ".***.**";
    }
    
    /**
     * 사용자 정의 마스킹 (중간 부분 마스킹)
     */
    public String maskCustom(String input, int prefixLength, int suffixLength) {
        if (input == null || input.length() <= (prefixLength + suffixLength)) {
            return "***";
        }
        
        recordMaskingEvent("custom");
        
        String prefix = input.substring(0, prefixLength);
        String suffix = input.substring(input.length() - suffixLength);
        
        return prefix + "***" + suffix;
    }
    
    /**
     * JSON 응답에서 민감 필드 자동 마스킹
     */
    public String maskSensitiveFields(String jsonResponse, String[] sensitiveFields) {
        if (jsonResponse == null || sensitiveFields == null) {
            return jsonResponse;
        }
        
        String masked = jsonResponse;
        
        for (String field : sensitiveFields) {
            Pattern fieldPattern = getOrCreatePattern("json_" + field,
                "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
            
            masked = fieldPattern.matcher(masked).replaceAll(match -> {
                String value = match.group(1);
                String maskedValue = determineMaskingType(field, value);
                return "\"" + field + "\":\"" + maskedValue + "\"";
            });
        }
        
        return masked;
    }
    
    /**
     * 필드명에 따른 마스킹 타입 결정
     */
    private String determineMaskingType(String fieldName, String value) {
        String lowerFieldName = fieldName.toLowerCase();
        
        if (lowerFieldName.contains("email")) {
            return maskEmail(value);
        } else if (lowerFieldName.contains("phone") || lowerFieldName.contains("mobile")) {
            return maskPhoneNumber(value);
        } else if (lowerFieldName.contains("token") || lowerFieldName.contains("jwt")) {
            return maskToken(value);
        } else if (lowerFieldName.contains("card") || lowerFieldName.contains("credit")) {
            return maskCreditCard(value);
        } else if (lowerFieldName.contains("account") || lowerFieldName.contains("bank")) {
            return maskBankAccount(value);
        } else if (lowerFieldName.contains("resident") || lowerFieldName.contains("ssn")) {
            return maskResidentNumber(value);
        } else if (lowerFieldName.contains("ip") || lowerFieldName.contains("address")) {
            return maskIpAddress(value);
        } else if (lowerFieldName.contains("password") || lowerFieldName.contains("secret")) {
            return "***";
        } else {
            // 기본 마스킹: 앞 2자리 + *** + 뒤 1자리
            return maskCustom(value, 2, 1);
        }
    }
    
    /**
     * 패턴 캐시 관리
     */
    private Pattern getOrCreatePattern(String key, String regex) {
        return patternCache.computeIfAbsent(key, k -> Pattern.compile(regex));
    }
    
    /**
     * 마스킹 이벤트 기록 (통계)
     */
    private void recordMaskingEvent(String type) {
        try {
            String dailyKey = MASKING_STATS_KEY + ":" + 
                java.time.LocalDate.now().toString();
            redisTemplate.opsForHash().increment(dailyKey, type, 1);
            redisTemplate.expire(dailyKey, Duration.ofDays(30));
        } catch (Exception e) {
            log.debug("마스킹 통계 기록 실패: {}", e.getMessage());
        }
    }
    
    /**
     * 마스킹 통계 조회
     */
    public Map<Object, Object> getMaskingStats(String date) {
        String key = MASKING_STATS_KEY + ":" + date;
        return redisTemplate.opsForHash().entries(key);
    }
    
    /**
     * 의심스러운 마스킹 패턴 감지
     */
    public void detectSuspiciousPattern(String clientIp, String maskingType) {
        try {
            String key = VIOLATION_TRACK_KEY + ":" + clientIp + ":" + maskingType;
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofMinutes(10));
            
            // 10분 내 같은 타입 마스킹이 100회 초과시 의심
            if (count != null && count > 100) {
                log.warn("의심스러운 마스킹 패턴 감지: IP={}, Type={}, Count={}", 
                    clientIp, maskingType, count);
            }
        } catch (Exception e) {
            log.debug("마스킹 패턴 감지 실패: {}", e.getMessage());
        }
    }
}
```

## 2. 로깅 보안 강화 필터
```java
package com.routepick.backend.config.security.filter;

import com.routepick.backend.config.security.service.DataMaskingService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoggingSecurityFilter implements Filter {
    
    private final DataMaskingService dataMaskingService;
    
    // 로깅에서 제외할 민감한 파라미터들
    private static final List<String> SENSITIVE_PARAMS = Arrays.asList(
        "password", "pwd", "pass", "secret", "token", "key", 
        "email", "phone", "mobile", "card", "account", "ssn"
    );
    
    // 로깅할 헤더들 (민감하지 않은 것들만)
    private static final List<String> LOGGABLE_HEADERS = Arrays.asList(
        "user-agent", "accept", "accept-language", "content-type", "referer"
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        long startTime = System.currentTimeMillis();
        
        // 요청 로깅 (민감 정보 마스킹)
        logSecureRequest(httpRequest);
        
        try {
            chain.doFilter(request, response);
        } finally {
            // 응답 로깅
            long duration = System.currentTimeMillis() - startTime;
            logSecureResponse(httpRequest, httpResponse, duration);
        }
    }
    
    private void logSecureRequest(HttpServletRequest request) {
        try {
            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append("REQUEST: ");
            logBuilder.append(request.getMethod()).append(" ");
            logBuilder.append(request.getRequestURI());
            
            // 쿼리 파라미터 로깅 (민감 정보 마스킹)
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                String maskedQuery = maskSensitiveParameters(queryString);
                logBuilder.append("?").append(maskedQuery);
            }
            
            // 클라이언트 IP
            String clientIp = getClientIpAddress(request);
            logBuilder.append(" | IP: ").append(dataMaskingService.maskIpAddress(clientIp));
            
            // 안전한 헤더들만 로깅
            for (String headerName : LOGGABLE_HEADERS) {
                String headerValue = request.getHeader(headerName);
                if (headerValue != null) {
                    logBuilder.append(" | ").append(headerName).append(": ");
                    
                    // User-Agent는 일부만 로깅
                    if ("user-agent".equalsIgnoreCase(headerName)) {
                        logBuilder.append(truncateUserAgent(headerValue));
                    } else {
                        logBuilder.append(headerValue);
                    }\n                }\n            }\n            \n            log.info(logBuilder.toString());\n            \n        } catch (Exception e) {\n            log.debug(\"요청 로깅 중 오류 발생: {}\", e.getMessage());\n        }\n    }\n    \n    private void logSecureResponse(HttpServletRequest request, \n                                 HttpServletResponse response, long duration) {\n        try {\n            StringBuilder logBuilder = new StringBuilder();\n            logBuilder.append(\"RESPONSE: \");\n            logBuilder.append(request.getMethod()).append(\" \");\n            logBuilder.append(request.getRequestURI());\n            logBuilder.append(\" | Status: \").append(response.getStatus());\n            logBuilder.append(\" | Duration: \").append(duration).append(\"ms\");\n            \n            // Content-Type 로깅\n            String contentType = response.getContentType();\n            if (contentType != null) {\n                logBuilder.append(\" | Content-Type: \").append(contentType);\n            }\n            \n            log.info(logBuilder.toString());\n            \n        } catch (Exception e) {\n            log.debug(\"응답 로깅 중 오류 발생: {}\", e.getMessage());\n        }\n    }\n    \n    private String maskSensitiveParameters(String queryString) {\n        String masked = queryString;\n        \n        for (String sensitiveParam : SENSITIVE_PARAMS) {\n            // 파라미터명=값 패턴 마스킹\n            String pattern = \"(?i)(\" + sensitiveParam + \"=)[^&]*\";\n            masked = masked.replaceAll(pattern, \"$1***\");\n        }\n        \n        return masked;\n    }\n    \n    private String getClientIpAddress(HttpServletRequest request) {\n        String[] headerNames = {\n            \"X-Forwarded-For\",\n            \"X-Real-IP\",\n            \"Proxy-Client-IP\",\n            \"WL-Proxy-Client-IP\",\n            \"HTTP_CLIENT_IP\",\n            \"HTTP_X_FORWARDED_FOR\"\n        };\n        \n        for (String headerName : headerNames) {\n            String ip = request.getHeader(headerName);\n            if (ip != null && !ip.isEmpty() && !\"unknown\".equalsIgnoreCase(ip)) {\n                // 첫 번째 IP 주소 반환 (프록시 체인인 경우)\n                return ip.split(\",\")[0].trim();\n            }\n        }\n        \n        return request.getRemoteAddr();\n    }\n    \n    private String truncateUserAgent(String userAgent) {\n        if (userAgent == null || userAgent.length() <= 100) {\n            return userAgent;\n        }\n        \n        return userAgent.substring(0, 100) + \"...\";\n    }\n}\n```\n\n## 3. 민감정보 검출 서비스\n```java\npackage com.routepick.backend.config.security.service;\n\nimport lombok.RequiredArgsConstructor;\nimport lombok.extern.slf4j.Slf4j;\nimport org.springframework.data.redis.core.RedisTemplate;\nimport org.springframework.stereotype.Service;\n\nimport java.time.Duration;\nimport java.util.HashMap;\nimport java.util.Map;\nimport java.util.regex.Pattern;\n\n@Service\n@RequiredArgsConstructor\n@Slf4j\npublic class SensitiveDataDetectionService {\n    \n    private final RedisTemplate<String, Object> redisTemplate;\n    \n    // 민감정보 패턴들\n    private static final Map<String, Pattern> SENSITIVE_PATTERNS = new HashMap<>();\n    \n    static {\n        // 한국 휴대폰 번호\n        SENSITIVE_PATTERNS.put(\"PHONE_KR\", \n            Pattern.compile(\"01[016789]-?\\\\d{3,4}-?\\\\d{4}\"));\n        \n        // 이메일 주소\n        SENSITIVE_PATTERNS.put(\"EMAIL\", \n            Pattern.compile(\"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}\"));\n        \n        // 주민등록번호\n        SENSITIVE_PATTERNS.put(\"RESIDENT_NUMBER\", \n            Pattern.compile(\"\\\\d{6}-[1-4]\\\\d{6}\"));\n        \n        // 신용카드 번호 (Luhn 알고리즘 고려)\n        SENSITIVE_PATTERNS.put(\"CREDIT_CARD\", \n            Pattern.compile(\"(?:\\\\d{4}[-\\\\s]?){3}\\\\d{4}\"));\n        \n        // JWT 토큰 패턴\n        SENSITIVE_PATTERNS.put(\"JWT_TOKEN\", \n            Pattern.compile(\"eyJ[a-zA-Z0-9+/=]+\\\\.[a-zA-Z0-9+/=]+\\\\.[a-zA-Z0-9+/=]+\"));\n        \n        // AWS Access Key\n        SENSITIVE_PATTERNS.put(\"AWS_ACCESS_KEY\", \n            Pattern.compile(\"AKIA[0-9A-Z]{16}\"));\n        \n        // 한국 사업자등록번호\n        SENSITIVE_PATTERNS.put(\"BUSINESS_NUMBER\", \n            Pattern.compile(\"\\\\d{3}-\\\\d{2}-\\\\d{5}\"));\n        \n        // IP 주소\n        SENSITIVE_PATTERNS.put(\"IP_ADDRESS\", \n            Pattern.compile(\"\\\\b(?:\\\\d{1,3}\\\\.){3}\\\\d{1,3}\\\\b\"));\n    }\n    \n    private static final String DETECTION_STATS_KEY = \"security:detection:stats\";\n    private static final String VIOLATION_ALERT_KEY = \"security:detection:alerts\";\n    \n    /**\n     * 텍스트에서 민감정보 검출\n     */\n    public Map<String, Integer> detectSensitiveData(String text, String source) {\n        if (text == null || text.isEmpty()) {\n            return new HashMap<>();\n        }\n        \n        Map<String, Integer> detectionResults = new HashMap<>();\n        \n        for (Map.Entry<String, Pattern> entry : SENSITIVE_PATTERNS.entrySet()) {\n            String patternName = entry.getKey();\n            Pattern pattern = entry.getValue();\n            \n            long matchCount = pattern.matcher(text).results().count();\n            if (matchCount > 0) {\n                detectionResults.put(patternName, (int) matchCount);\n                \n                // 검출 통계 기록\n                recordDetection(patternName, source, (int) matchCount);\n                \n                // 경고 레벨 확인\n                checkAlertThreshold(patternName, source, (int) matchCount);\n            }\n        }\n        \n        return detectionResults;\n    }\n    \n    /**\n     * 특정 패턴의 민감정보만 검출\n     */\n    public boolean containsSensitivePattern(String text, String patternType) {\n        Pattern pattern = SENSITIVE_PATTERNS.get(patternType);\n        if (pattern == null) {\n            return false;\n        }\n        \n        return pattern.matcher(text).find();\n    }\n    \n    /**\n     * 민감정보 자동 마스킹\n     */\n    public String autoMaskSensitiveData(String text) {\n        if (text == null || text.isEmpty()) {\n            return text;\n        }\n        \n        String maskedText = text;\n        \n        // 각 패턴별로 마스킹 적용\n        for (Map.Entry<String, Pattern> entry : SENSITIVE_PATTERNS.entrySet()) {\n            String patternType = entry.getKey();\n            Pattern pattern = entry.getValue();\n            \n            maskedText = pattern.matcher(maskedText).replaceAll(match -> {\n                return getMaskedValue(patternType, match.group());\n            });\n        }\n        \n        return maskedText;\n    }\n    \n    /**\n     * 패턴 타입별 마스킹 값 생성\n     */\n    private String getMaskedValue(String patternType, String originalValue) {\n        switch (patternType) {\n            case \"PHONE_KR\":\n                return \"010-****-****\";\n            case \"EMAIL\":\n                return \"***@***.***\";\n            case \"RESIDENT_NUMBER\":\n                return \"******-*******\";\n            case \"CREDIT_CARD\":\n                return \"****-****-****-****\";\n            case \"JWT_TOKEN\":\n                return \"[JWT_TOKEN_MASKED]\";\n            case \"AWS_ACCESS_KEY\":\n                return \"AKIA****************\";\n            case \"BUSINESS_NUMBER\":\n                return \"***-**-*****\";\n            case \"IP_ADDRESS\":\n                return \"***.***.***.***\";\n            default:\n                return \"[SENSITIVE_DATA_MASKED]\";\n        }\n    }\n    \n    /**\n     * 민감정보 검출 결과를 JSON으로 변환\n     */\n    public String getSensitiveDataReport(String text, String source) {\n        Map<String, Integer> detections = detectSensitiveData(text, source);\n        \n        if (detections.isEmpty()) {\n            return \"{\\\"status\\\":\\\"clean\\\",\\\"detections\\\":{}\\\";}\";\n        }\n        \n        StringBuilder report = new StringBuilder();\n        report.append(\"{\\\"status\\\":\\\"sensitive_data_found\\\",\\\"detections\\\":{{\");\n        \n        boolean first = true;\n        for (Map.Entry<String, Integer> entry : detections.entrySet()) {\n            if (!first) {\n                report.append(\",\");\n            }\n            report.append(\"\\\"\").append(entry.getKey()).append(\"\\\":\").append(entry.getValue());\n            first = false;\n        }\n        \n        report.append(\"}}\");\n        \n        return report.toString();\n    }\n    \n    /**\n     * 검출 통계 기록\n     */\n    private void recordDetection(String patternType, String source, int count) {\n        try {\n            String dailyKey = DETECTION_STATS_KEY + \":\" + \n                java.time.LocalDate.now().toString() + \":\" + patternType;\n            \n            redisTemplate.opsForHash().increment(dailyKey, source, count);\n            redisTemplate.expire(dailyKey, Duration.ofDays(30));\n            \n        } catch (Exception e) {\n            log.debug(\"검출 통계 기록 실패: {}\", e.getMessage());\n        }\n    }\n    \n    /**\n     * 경고 임계값 확인\n     */\n    private void checkAlertThreshold(String patternType, String source, int count) {\n        // 높은 빈도의 민감정보 검출시 경고\n        if (count > 10) {\n            try {\n                String alertKey = VIOLATION_ALERT_KEY + \":\" + patternType + \":\" + source;\n                String alertMessage = String.format(\n                    \"High frequency sensitive data detection: %s in %s (count: %d)\",\n                    patternType, source, count\n                );\n                \n                redisTemplate.opsForValue().set(alertKey, alertMessage, Duration.ofHours(1));\n                \n                log.warn(\"민감정보 대량 검출: {}\", alertMessage);\n                \n            } catch (Exception e) {\n                log.debug(\"경고 기록 실패: {}\", e.getMessage());\n            }\n        }\n    }\n    \n    /**\n     * 일일 검출 통계 조회\n     */\n    public Map<String, Map<Object, Object>> getDailyDetectionStats(String date) {\n        Map<String, Map<Object, Object>> stats = new HashMap<>();\n        \n        for (String patternType : SENSITIVE_PATTERNS.keySet()) {\n            String key = DETECTION_STATS_KEY + \":\" + date + \":\" + patternType;\n            Map<Object, Object> patternStats = redisTemplate.opsForHash().entries(key);\n            \n            if (!patternStats.isEmpty()) {\n                stats.put(patternType, patternStats);\n            }\n        }\n        \n        return stats;\n    }\n    \n    /**\n     * 민감정보 패턴 추가 (동적)\n     */\n    public void addCustomPattern(String name, String regex) {\n        try {\n            Pattern pattern = Pattern.compile(regex);\n            SENSITIVE_PATTERNS.put(name, pattern);\n            log.info(\"사용자 정의 민감정보 패턴 추가: {}\", name);\n        } catch (Exception e) {\n            log.error(\"잘못된 정규식 패턴: {}\", regex, e);\n        }\n    }\n    \n    /**\n     * 등록된 모든 패턴 목록 반환\n     */\n    public Map<String, String> getAllPatterns() {\n        Map<String, String> patterns = new HashMap<>();\n        for (Map.Entry<String, Pattern> entry : SENSITIVE_PATTERNS.entrySet()) {\n            patterns.put(entry.getKey(), entry.getValue().pattern());\n        }\n        return patterns;\n    }\n}\n```\n\n## 4. 통합 보안 모니터링 서비스\n```java\npackage com.routepick.backend.config.security.service;\n\nimport lombok.RequiredArgsConstructor;\nimport lombok.extern.slf4j.Slf4j;\nimport org.springframework.data.redis.core.RedisTemplate;\nimport org.springframework.scheduling.annotation.Scheduled;\nimport org.springframework.stereotype.Service;\n\nimport java.time.Duration;\nimport java.time.LocalDateTime;\nimport java.time.format.DateTimeFormatter;\nimport java.util.HashMap;\nimport java.util.Map;\nimport java.util.Set;\n\n@Service\n@RequiredArgsConstructor\n@Slf4j\npublic class SecurityMonitoringService {\n    \n    private final RedisTemplate<String, Object> redisTemplate;\n    private final DataMaskingService dataMaskingService;\n    private final SensitiveDataDetectionService detectionService;\n    \n    private static final String MONITORING_KEY = \"security:monitoring\";\n    private static final String ALERT_KEY = \"security:alerts\";\n    \n    /**\n     * 종합 보안 상태 체크\n     */\n    public Map<String, Object> getSecurityStatus() {\n        Map<String, Object> status = new HashMap<>();\n        \n        // 현재 시간\n        status.put(\"timestamp\", LocalDateTime.now().format(\n            DateTimeFormatter.ofPattern(\"yyyy-MM-dd HH:mm:ss\")\n        ));\n        \n        // 마스킹 통계\n        String today = java.time.LocalDate.now().toString();\n        Map<Object, Object> maskingStats = dataMaskingService.getMaskingStats(today);\n        status.put(\"masking_stats\", maskingStats);\n        \n        // 민감정보 검출 통계\n        Map<String, Map<Object, Object>> detectionStats = \n            detectionService.getDailyDetectionStats(today);\n        status.put(\"detection_stats\", detectionStats);\n        \n        // 활성 경고 수\n        Set<String> activeAlerts = redisTemplate.keys(ALERT_KEY + \":*\");\n        status.put(\"active_alerts\", activeAlerts != null ? activeAlerts.size() : 0);\n        \n        // 시스템 상태\n        status.put(\"system_status\", \"operational\");\n        status.put(\"security_level\", determineSecurityLevel(maskingStats, detectionStats));\n        \n        return status;\n    }\n    \n    /**\n     * 보안 경고 생성\n     */\n    public void createSecurityAlert(String type, String message, String severity) {\n        try {\n            String timestamp = LocalDateTime.now().format(\n                DateTimeFormatter.ofPattern(\"yyyy-MM-dd HH:mm:ss\")\n            );\n            \n            Map<String, String> alert = new HashMap<>();\n            alert.put(\"type\", type);\n            alert.put(\"message\", message);\n            alert.put(\"severity\", severity);\n            alert.put(\"timestamp\", timestamp);\n            \n            String alertKey = ALERT_KEY + \":\" + type + \":\" + System.currentTimeMillis();\n            redisTemplate.opsForHash().putAll(alertKey, alert);\n            \n            // 심각한 경고는 24시간, 일반 경고는 1시간 보관\n            Duration ttl = \"critical\".equals(severity) ? Duration.ofHours(24) : Duration.ofHours(1);\n            redisTemplate.expire(alertKey, ttl);\n            \n            log.warn(\"보안 경고 생성: [{}] {} - {}\", severity.toUpperCase(), type, message);\n            \n        } catch (Exception e) {\n            log.error(\"보안 경고 생성 실패\", e);\n        }\n    }\n    \n    /**\n     * 주기적 보안 상태 체크 (5분마다)\n     */\n    @Scheduled(fixedRate = 300000)\n    public void periodicSecurityCheck() {\n        try {\n            Map<String, Object> status = getSecurityStatus();\n            String securityLevel = (String) status.get(\"security_level\");\n            \n            // 보안 레벨에 따른 조치\n            switch (securityLevel) {\n                case \"high_risk\":\n                    createSecurityAlert(\n                        \"SECURITY_LEVEL\", \n                        \"높은 위험 수준의 보안 활동이 감지되었습니다.\", \n                        \"critical\"\n                    );\n                    break;\n                case \"medium_risk\":\n                    createSecurityAlert(\n                        \"SECURITY_LEVEL\", \n                        \"중간 위험 수준의 보안 활동이 감지되었습니다.\", \n                        \"warning\"\n                    );\n                    break;\n                default:\n                    log.debug(\"보안 상태 정상: {}\", securityLevel);\n            }\n            \n            // 모니터링 데이터 저장\n            String monitoringKey = MONITORING_KEY + \":\" + \n                LocalDateTime.now().format(DateTimeFormatter.ofPattern(\"yyyy-MM-dd-HH-mm\"));\n            redisTemplate.opsForValue().set(monitoringKey, status, Duration.ofDays(7));\n            \n        } catch (Exception e) {\n            log.error(\"주기적 보안 체크 실패\", e);\n        }\n    }\n    \n    /**\n     * 보안 수준 결정\n     */\n    private String determineSecurityLevel(Map<Object, Object> maskingStats, \n                                        Map<String, Map<Object, Object>> detectionStats) {\n        \n        int totalMasking = maskingStats.values().stream()\n                .mapToInt(v -> Integer.parseInt(v.toString()))\n                .sum();\n        \n        int totalDetections = detectionStats.values().stream()\n                .mapToInt(statMap -> statMap.values().stream()\n                        .mapToInt(v -> Integer.parseInt(v.toString()))\n                        .sum())\n                .sum();\n        \n        // 위험 수준 판단 기준\n        if (totalDetections > 1000 || totalMasking > 5000) {\n            return \"high_risk\";\n        } else if (totalDetections > 100 || totalMasking > 500) {\n            return \"medium_risk\";\n        } else {\n            return \"low_risk\";\n        }\n    }\n    \n    /**\n     * 보안 통계 리포트 생성\n     */\n    public Map<String, Object> generateSecurityReport(String startDate, String endDate) {\n        Map<String, Object> report = new HashMap<>();\n        \n        try {\n            // 기간 내 마스킹 통계 집계\n            Map<String, Integer> maskingTotals = new HashMap<>();\n            \n            // 기간 내 검출 통계 집계\n            Map<String, Integer> detectionTotals = new HashMap<>();\n            \n            // 일자별 순회하여 데이터 수집\n            java.time.LocalDate start = java.time.LocalDate.parse(startDate);\n            java.time.LocalDate end = java.time.LocalDate.parse(endDate);\n            \n            for (java.time.LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {\n                String dateStr = date.toString();\n                \n                // 마스킹 통계\n                Map<Object, Object> dailyMasking = dataMaskingService.getMaskingStats(dateStr);\n                for (Map.Entry<Object, Object> entry : dailyMasking.entrySet()) {\n                    String key = entry.getKey().toString();\n                    int value = Integer.parseInt(entry.getValue().toString());\n                    maskingTotals.merge(key, value, Integer::sum);\n                }\n                \n                // 검출 통계\n                Map<String, Map<Object, Object>> dailyDetection = \n                    detectionService.getDailyDetectionStats(dateStr);\n                for (Map.Entry<String, Map<Object, Object>> typeEntry : dailyDetection.entrySet()) {\n                    String type = typeEntry.getKey();\n                    int typeTotal = typeEntry.getValue().values().stream()\n                            .mapToInt(v -> Integer.parseInt(v.toString()))\n                            .sum();\n                    detectionTotals.merge(type, typeTotal, Integer::sum);\n                }\n            }\n            \n            report.put(\"period\", startDate + \" ~ \" + endDate);\n            report.put(\"masking_summary\", maskingTotals);\n            report.put(\"detection_summary\", detectionTotals);\n            \n            // 총계\n            int totalMasking = maskingTotals.values().stream().mapToInt(Integer::intValue).sum();\n            int totalDetection = detectionTotals.values().stream().mapToInt(Integer::intValue).sum();\n            \n            report.put(\"total_masking_events\", totalMasking);\n            report.put(\"total_detection_events\", totalDetection);\n            \n            // 권장사항\n            report.put(\"recommendations\", generateRecommendations(totalMasking, totalDetection));\n            \n        } catch (Exception e) {\n            log.error(\"보안 리포트 생성 실패\", e);\n            report.put(\"error\", \"리포트 생성 중 오류가 발생했습니다.\");\n        }\n        \n        return report;\n    }\n    \n    /**\n     * 보안 권장사항 생성\n     */\n    private Map<String, String> generateRecommendations(int totalMasking, int totalDetection) {\n        Map<String, String> recommendations = new HashMap<>();\n        \n        if (totalDetection > 5000) {\n            recommendations.put(\"high_detection\", \n                \"민감정보 검출 빈도가 높습니다. 입력 검증을 강화하세요.\");\n        }\n        \n        if (totalMasking > 10000) {\n            recommendations.put(\"high_masking\", \n                \"마스킹 처리가 빈번합니다. 민감정보 처리 정책을 검토하세요.\");\n        }\n        \n        if (totalDetection < 100 && totalMasking < 100) {\n            recommendations.put(\"low_activity\", \n                \"보안 활동이 적습니다. 모니터링 범위를 확인하세요.\");\n        }\n        \n        return recommendations;\n    }\n}\n```\n\n## 5. 최종 통합 설정\n```yaml\n# application.yml - 민감정보 보호 완성 설정\nsecurity:\n  data-protection:\n    enabled: true\n    \n    masking:\n      enabled: true\n      patterns:\n        email: \"**@domain\"\n        phone: \"***-****-***\"\n        token: \"8chars***\"\n        card: \"****-****-****-****\"\n        resident: \"******-*******\"\n      \n      thresholds:\n        suspicious-count: 100\n        alert-count: 1000\n    \n    detection:\n      enabled: true\n      patterns:\n        phone-kr: true\n        email: true\n        resident-number: true\n        credit-card: true\n        jwt-token: true\n        aws-key: true\n        business-number: true\n        ip-address: true\n      \n      alerts:\n        high-frequency: 10\n        critical-threshold: 100\n    \n    logging:\n      secure-logging: true\n      mask-parameters: true\n      exclude-headers:\n        - \"authorization\"\n        - \"x-auth-token\"\n        - \"cookie\"\n      \n      include-headers:\n        - \"user-agent\"\n        - \"accept\"\n        - \"content-type\"\n    \n    monitoring:\n      enabled: true\n      check-interval: 300000  # 5분\n      report-retention: 30    # 30일\n      alert-retention: 24     # 24시간\n      \n      risk-levels:\n        low:\n          masking-threshold: 500\n          detection-threshold: 100\n        medium:\n          masking-threshold: 5000\n          detection-threshold: 1000\n        high:\n          masking-threshold: 10000\n          detection-threshold: 5000\n```\n\n## 6. API 엔드포인트 (관리자용)\n```java\npackage com.routepick.backend.controller.admin;\n\nimport com.routepick.backend.config.security.service.DataMaskingService;\nimport com.routepick.backend.config.security.service.SecurityMonitoringService;\nimport com.routepick.backend.config.security.service.SensitiveDataDetectionService;\nimport lombok.RequiredArgsConstructor;\nimport org.springframework.http.ResponseEntity;\nimport org.springframework.web.bind.annotation.*;\n\nimport java.util.Map;\n\n@RestController\n@RequestMapping(\"/admin/security\")\n@RequiredArgsConstructor\npublic class SecurityAdminController {\n    \n    private final SecurityMonitoringService monitoringService;\n    private final DataMaskingService maskingService;\n    private final SensitiveDataDetectionService detectionService;\n    \n    /**\n     * 전체 보안 상태 조회\n     */\n    @GetMapping(\"/status\")\n    public ResponseEntity<Map<String, Object>> getSecurityStatus() {\n        Map<String, Object> status = monitoringService.getSecurityStatus();\n        return ResponseEntity.ok(status);\n    }\n    \n    /**\n     * 마스킹 통계 조회\n     */\n    @GetMapping(\"/masking/stats/{date}\")\n    public ResponseEntity<Map<Object, Object>> getMaskingStats(@PathVariable String date) {\n        Map<Object, Object> stats = maskingService.getMaskingStats(date);\n        return ResponseEntity.ok(stats);\n    }\n    \n    /**\n     * 민감정보 검출 통계 조회\n     */\n    @GetMapping(\"/detection/stats/{date}\")\n    public ResponseEntity<Map<String, Map<Object, Object>>> getDetectionStats(@PathVariable String date) {\n        Map<String, Map<Object, Object>> stats = detectionService.getDailyDetectionStats(date);\n        return ResponseEntity.ok(stats);\n    }\n    \n    /**\n     * 보안 리포트 생성\n     */\n    @GetMapping(\"/report\")\n    public ResponseEntity<Map<String, Object>> generateSecurityReport(\n            @RequestParam String startDate,\n            @RequestParam String endDate) {\n        Map<String, Object> report = monitoringService.generateSecurityReport(startDate, endDate);\n        return ResponseEntity.ok(report);\n    }\n    \n    /**\n     * 사용자 정의 민감정보 패턴 추가\n     */\n    @PostMapping(\"/detection/pattern\")\n    public ResponseEntity<String> addCustomPattern(\n            @RequestParam String name,\n            @RequestParam String regex) {\n        detectionService.addCustomPattern(name, regex);\n        return ResponseEntity.ok(\"패턴이 성공적으로 추가되었습니다: \" + name);\n    }\n    \n    /**\n     * 등록된 모든 패턴 목록 조회\n     */\n    @GetMapping(\"/detection/patterns\")\n    public ResponseEntity<Map<String, String>> getAllPatterns() {\n        Map<String, String> patterns = detectionService.getAllPatterns();\n        return ResponseEntity.ok(patterns);\n    }\n    \n    /**\n     * 보안 경고 생성 (테스트용)\n     */\n    @PostMapping(\"/alert\")\n    public ResponseEntity<String> createAlert(\n            @RequestParam String type,\n            @RequestParam String message,\n            @RequestParam String severity) {\n        monitoringService.createSecurityAlert(type, message, severity);\n        return ResponseEntity.ok(\"경고가 생성되었습니다.\");\n    }\n}\n```"