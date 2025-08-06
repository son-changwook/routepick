package com.routepick.api.util;

import lombok.extern.slf4j.Slf4j;
import java.util.regex.Pattern;

/**
 * 입력 데이터 정제 유틸리티 클래스
 * XSS 공격 방지를 위한 HTML 태그 필터링 및 특수문자 이스케이프 처리
 */
@Slf4j
public class InputSanitizer {
    
    // XSS 공격 패턴
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "<script[^>]*>.*?</script>|javascript:|vbscript:|onload=|onerror=|onclick=|onmouseover=|onfocus=|onblur=", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL
    );
    
    // SQL Injection 패턴
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('|;|--|/\\*|\\*/|xp_|sp_|exec|execute|insert|select|delete|update|drop|create|alter|declare|cast|convert|union|waitfor|delay|shutdown)",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * HTML 태그 제거
     * @param input 입력 문자열
     * @return HTML 태그가 제거된 문자열
     */
    public static String removeHtmlTags(String input) {
        if (input == null) return null;
        return input.replaceAll("<[^>]*>", "");
    }
    
    /**
     * HTML 특수문자 이스케이프
     * @param input 입력 문자열
     * @return HTML 이스케이프된 문자열
     */
    public static String escapeHtml(String input) {
        if (input == null) return null;
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;")
                   .replace("/", "&#x2F;");
    }
    
    /**
     * XSS 공격 패턴 검사
     * @param input 입력 문자열
     * @return XSS 공격 패턴이 포함되어 있으면 true
     */
    public static boolean containsXssPattern(String input) {
        if (input == null) return false;
        return XSS_PATTERN.matcher(input).find();
    }
    
    /**
     * SQL Injection 패턴 검사
     * @param input 입력 문자열
     * @return SQL Injection 패턴이 포함되어 있으면 true
     */
    public static boolean containsSqlInjectionPattern(String input) {
        if (input == null) return false;
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }
    
    /**
     * 입력 데이터 정제 (HTML 태그 제거 + XSS 패턴 검사)
     * @param input 입력 문자열
     * @return 정제된 문자열
     * @throws SecurityException XSS 공격 패턴이 감지된 경우
     */
    public static String sanitizeInput(String input) {
        if (input == null) return null;
        
        // HTML 태그 제거
        String sanitized = removeHtmlTags(input);
        
        // XSS 공격 패턴 검사
        if (containsXssPattern(sanitized)) {
            log.warn("XSS 공격 패턴이 감지되었습니다. 입력 길이: {}", input != null ? input.length() : 0);
            throw new SecurityException("XSS 공격이 감지되었습니다.");
        }
        
        // SQL Injection 패턴 검사
        if (containsSqlInjectionPattern(sanitized)) {
            log.warn("SQL Injection 패턴이 감지되었습니다. 입력 길이: {}", input != null ? input.length() : 0);
            throw new SecurityException("SQL Injection 공격이 감지되었습니다.");
        }
        
        return sanitized.trim();
    }
    
    /**
     * 이메일 주소 정제
     * @param email 이메일 주소
     * @return 정제된 이메일 주소
     */
    public static String sanitizeEmail(String email) {
        if (email == null) return null;
        
        String sanitized = email.trim().toLowerCase();
        
        // 기본적인 이메일 형식 검증
        if (!sanitized.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new SecurityException("올바르지 않은 이메일 형식입니다.");
        }
        
        return sanitized;
    }
    
    /**
     * 사용자명 정제
     * @param userName 사용자명
     * @return 정제된 사용자명
     */
    public static String sanitizeUserName(String userName) {
        if (userName == null) return null;
        
        String sanitized = sanitizeInput(userName);
        
        // 사용자명 형식 검증
        if (!sanitized.matches("^[a-zA-Z0-9가-힣_-]{2,20}$")) {
            throw new SecurityException("사용자명은 2-20자의 영문, 숫자, 한글, 언더스코어, 하이픈만 사용 가능합니다.");
        }
        
        return sanitized;
    }
} 