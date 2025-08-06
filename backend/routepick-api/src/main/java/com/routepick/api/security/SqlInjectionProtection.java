package com.routepick.api.security;

import com.routepick.common.exception.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * SQL Injection 방지를 위한 보안 컴포넌트
 * 
 * 다층 방어 전략:
 * 1. 입력 정규화 (Normalization)
 * 2. 패턴 기반 검증
 * 3. 위험 문자 필터링
 * 4. 컨텍스트별 검증
 */
@Slf4j
@Component
public class SqlInjectionProtection {
    
    // SQL 키워드 패턴 (대소문자 구분 없음)
    private static final Pattern SQL_KEYWORDS = Pattern.compile(
        "(?i)(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|EXECUTE|UNION|OR|AND)\\b)",
        Pattern.CASE_INSENSITIVE
    );
    
    // SQL 주석 패턴
    private static final Pattern SQL_COMMENTS = Pattern.compile(
        "(?i)(--|/\\*|\\*/|#)",
        Pattern.CASE_INSENSITIVE
    );
    
    // SQL 연산자 패턴
    private static final Pattern SQL_OPERATORS = Pattern.compile(
        "(?i)(\\b(UNION|INTERSECT|EXCEPT)\\b\\s+\\b(ALL|DISTINCT)?\\b)",
        Pattern.CASE_INSENSITIVE
    );
    
    // 위험한 함수 패턴
    private static final Pattern DANGEROUS_FUNCTIONS = Pattern.compile(
        "(?i)(\\b(LOAD_FILE|INTO\\s+OUTFILE|INTO\\s+DUMPFILE|BENCHMARK|SLEEP|UPDATEXML|EXTRACTVALUE)\\b)",
        Pattern.CASE_INSENSITIVE
    );
    
    // 인코딩 우회 패턴
    private static final Pattern ENCODING_BYPASS = Pattern.compile(
        "(?i)(%27|%22|%3B|%2D%2D|%23|%2F%2A|%2A%2F)",
        Pattern.CASE_INSENSITIVE
    );
    
    // 위험한 문자 시퀀스
    private static final Pattern DANGEROUS_SEQUENCES = Pattern.compile(
        "(?i)(\\b(OR|AND)\\b\\s*[0-9]+\\s*[=<>]|\\b(OR|AND)\\b\\s*['\"].*['\"])",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * SQL Injection 방지를 위한 다층 검증
     * 
     * @param input 검증할 입력값
     * @param context 검증 컨텍스트 (예: "email", "username", "search")
     * @return 검증된 안전한 문자열
     * @throws SecurityException SQL Injection 패턴이 감지된 경우
     */
    public String validateAndSanitize(String input, String context) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }
        
        // 1단계: 입력 정규화
        String normalized = normalizeInput(input);
        
        // 2단계: 패턴 기반 검증
        validatePatterns(normalized, context);
        
        // 3단계: 컨텍스트별 추가 검증
        validateByContext(normalized, context);
        
        // 4단계: 최종 정제
        return finalSanitization(normalized);
    }
    
    /**
     * 입력 정규화
     * - 공백 정규화
     * - 인코딩 정규화
     * - 특수문자 정규화
     */
    private String normalizeInput(String input) {
        // 공백 정규화
        String normalized = input.trim().replaceAll("\\s+", " ");
        
        // URL 디코딩 (위험한 인코딩 우회 방지)
        normalized = decodeUrlEncoding(normalized);
        
        // 유니코드 정규화
        normalized = java.text.Normalizer.normalize(normalized, java.text.Normalizer.Form.NFC);
        
        return normalized;
    }
    
    /**
     * URL 인코딩 디코딩 (위험한 인코딩 우회 방지)
     */
    private String decodeUrlEncoding(String input) {
        try {
            // 위험한 인코딩 패턴 검사
            if (ENCODING_BYPASS.matcher(input).find()) {
                log.warn("위험한 URL 인코딩 패턴 감지: {}", input);
                throw SecurityException.sqlInjectionDetected();
            }
            
            // 안전한 디코딩만 허용
            return java.net.URLDecoder.decode(input, "UTF-8");
        } catch (Exception e) {
            log.warn("URL 디코딩 실패: {}", input);
            throw SecurityException.sqlInjectionDetected();
        }
    }
    
    /**
     * 패턴 기반 검증
     */
    private void validatePatterns(String input, String context) {
        // SQL 키워드 검사
        if (SQL_KEYWORDS.matcher(input).find()) {
            log.warn("SQL 키워드 감지: context={}, input={}", context, maskSensitiveData(input));
            throw SecurityException.sqlInjectionDetected();
        }
        
        // SQL 주석 검사
        if (SQL_COMMENTS.matcher(input).find()) {
            log.warn("SQL 주석 패턴 감지: context={}, input={}", context, maskSensitiveData(input));
            throw SecurityException.sqlInjectionDetected();
        }
        
        // SQL 연산자 검사
        if (SQL_OPERATORS.matcher(input).find()) {
            log.warn("SQL 연산자 감지: context={}, input={}", context, maskSensitiveData(input));
            throw SecurityException.sqlInjectionDetected();
        }
        
        // 위험한 함수 검사
        if (DANGEROUS_FUNCTIONS.matcher(input).find()) {
            log.warn("위험한 SQL 함수 감지: context={}, input={}", context, maskSensitiveData(input));
            throw SecurityException.sqlInjectionDetected();
        }
        
        // 위험한 시퀀스 검사
        if (DANGEROUS_SEQUENCES.matcher(input).find()) {
            log.warn("위험한 SQL 시퀀스 감지: context={}, input={}", context, maskSensitiveData(input));
            throw SecurityException.sqlInjectionDetected();
        }
    }
    
    /**
     * 컨텍스트별 추가 검증
     */
    private void validateByContext(String input, String context) {
        switch (context.toLowerCase()) {
            case "email":
                validateEmailContext(input);
                break;
            case "username":
                validateUsernameContext(input);
                break;
            case "search":
                validateSearchContext(input);
                break;
            case "comment":
                validateCommentContext(input);
                break;
            default:
                validateGenericContext(input);
                break;
        }
    }
    
    /**
     * 이메일 컨텍스트 검증
     */
    private void validateEmailContext(String input) {
        // 이메일 형식 검증
        if (!input.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            log.warn("잘못된 이메일 형식: {}", maskSensitiveData(input));
            throw SecurityException.invalidEmailFormat();
        }
        
        // 이메일 길이 제한
        if (input.length() > 254) {
            log.warn("이메일 길이 초과: {}", input.length());
            throw SecurityException.invalidEmailFormat();
        }
    }
    
    /**
     * 사용자명 컨텍스트 검증
     */
    private void validateUsernameContext(String input) {
        // 사용자명 형식 검증 (영문, 숫자, 한글, 언더스코어, 하이픈만 허용)
        if (!input.matches("^[a-zA-Z0-9가-힣_-]{2,20}$")) {
            log.warn("잘못된 사용자명 형식: {}", maskSensitiveData(input));
            throw SecurityException.invalidInputFormat("사용자명");
        }
    }
    
    /**
     * 검색 컨텍스트 검증
     */
    private void validateSearchContext(String input) {
        // 검색어 길이 제한
        if (input.length() > 100) {
            log.warn("검색어 길이 초과: {}", input.length());
            throw SecurityException.invalidInputFormat("검색어");
        }
        
        // 검색어에 허용되지 않는 문자 검사
        if (input.matches(".*[<>\"'&].*")) {
            log.warn("검색어에 위험한 문자 포함: {}", maskSensitiveData(input));
            throw SecurityException.invalidInputFormat("검색어");
        }
    }
    
    /**
     * 댓글 컨텍스트 검증
     */
    private void validateCommentContext(String input) {
        // 댓글 길이 제한
        if (input.length() > 1000) {
            log.warn("댓글 길이 초과: {}", input.length());
            throw SecurityException.invalidInputFormat("댓글");
        }
        
        // 스크립트 태그 검사
        if (input.toLowerCase().contains("<script") || input.toLowerCase().contains("javascript:")) {
            log.warn("댓글에 스크립트 태그 포함: {}", maskSensitiveData(input));
            throw SecurityException.xssDetected();
        }
    }
    
    /**
     * 일반 컨텍스트 검증
     */
    private void validateGenericContext(String input) {
        // 기본 길이 제한
        if (input.length() > 500) {
            log.warn("입력 길이 초과: {}", input.length());
            throw SecurityException.invalidInputFormat("입력값");
        }
        
        // 위험한 문자 검사
        if (input.matches(".*[<>\"'&].*")) {
            log.warn("위험한 문자 포함: {}", maskSensitiveData(input));
            throw SecurityException.invalidInputFormat("입력값");
        }
    }
    
    /**
     * 최종 정제
     */
    private String finalSanitization(String input) {
        // HTML 엔티티 이스케이프
        String sanitized = input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
        
        return sanitized;
    }
    
    /**
     * 민감한 데이터 마스킹
     */
    private String maskSensitiveData(String input) {
        if (input == null || input.length() <= 10) {
            return "***";
        }
        return input.substring(0, 3) + "***" + input.substring(input.length() - 3);
    }
    
    /**
     * 배치 검증 (여러 입력값을 한번에 검증)
     */
    public void validateBatch(String... inputs) {
        for (String input : inputs) {
            if (input != null) {
                validateAndSanitize(input, "generic");
            }
        }
    }
} 