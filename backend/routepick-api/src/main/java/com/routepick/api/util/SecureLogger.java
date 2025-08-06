package com.routepick.api.util;

import lombok.extern.slf4j.Slf4j;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 보안 강화된 로깅 유틸리티
 * 민감한 정보를 마스킹하여 안전하게 로그를 기록합니다.
 */
@Slf4j
public class SecureLogger {
    
    // 민감한 정보 패턴 (대소문자 구분 없음)
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        "(password|token|secret|key|code|auth|credential|session)", 
        Pattern.CASE_INSENSITIVE
    );
    
    // 이메일 패턴
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    /**
     * 민감한 데이터를 마스킹
     * @param input 입력 문자열
     * @return 마스킹된 문자열
     */
    public static String maskSensitiveData(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "***";
        }
        
        String trimmed = input.trim();
        
        // 4자 이하인 경우 완전 마스킹
        if (trimmed.length() <= 4) {
            return "***";
        }
        
        // 6자 이하인 경우 첫 1자만 표시
        if (trimmed.length() <= 6) {
            return trimmed.substring(0, 1) + "***";
        }
        
        // 그 외의 경우 앞 2자, 뒤 2자만 표시
        return trimmed.substring(0, 2) + "***" + trimmed.substring(trimmed.length() - 2);
    }
    
    /**
     * 이메일 주소를 마스킹
     * @param email 이메일 주소
     * @return 마스킹된 이메일 주소
     */
    public static String maskEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return "***@***.***";
        }
        
        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        
        // 로컬 부분 마스킹
        if (localPart.length() <= 2) {
            return "***" + domainPart;
        } else if (localPart.length() <= 4) {
            return localPart.substring(0, 1) + "***" + domainPart;
        } else {
            return localPart.substring(0, 2) + "***" + localPart.substring(localPart.length() - 1) + domainPart;
        }
    }
    
    /**
     * IP 주소를 마스킹 (마지막 옥텟만 마스킹)
     * @param ip IP 주소
     * @return 마스킹된 IP 주소
     */
    public static String maskIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return "***.***.***.***";
        }
        
        // IPv4 패턴 확인
        if (ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            int lastDotIndex = ip.lastIndexOf('.');
            return ip.substring(0, lastDotIndex) + ".***";
        }
        
        // IPv6나 기타 형식인 경우 뒷부분 마스킹
        if (ip.length() > 8) {
            return ip.substring(0, ip.length() - 4) + "***";
        }
        
        return "***";
    }
    
    /**
     * 보안 이벤트 로깅 (민감한 정보 자동 마스킹)
     * @param event 이벤트 메시지
     * @param params 파라미터들
     */
    public static void logSecurityEvent(String event, Object... params) {
        Object[] maskedParams = Arrays.stream(params)
            .map(param -> {
                if (param instanceof String str) {
                    // 이메일인지 확인
                    if (EMAIL_PATTERN.matcher(str).matches()) {
                        return maskEmail(str);
                    }
                    // 민감한 정보 패턴인지 확인
                    if (SENSITIVE_PATTERN.matcher(str).find()) {
                        return maskSensitiveData(str);
                    }
                }
                return param;
            })
            .toArray();
            
        log.warn("SECURITY_EVENT: " + event, maskedParams);
    }
    
    /**
     * 보안 이벤트 로깅 (디버그 레벨)
     * @param event 이벤트 메시지
     * @param params 파라미터들
     */
    public static void logSecurityDebug(String event, Object... params) {
        Object[] maskedParams = Arrays.stream(params)
            .map(param -> {
                if (param instanceof String str) {
                    if (EMAIL_PATTERN.matcher(str).matches()) {
                        return maskEmail(str);
                    }
                    if (SENSITIVE_PATTERN.matcher(str).find()) {
                        return maskSensitiveData(str);
                    }
                }
                return param;
            })
            .toArray();
            
        log.debug("SECURITY_DEBUG: " + event, maskedParams);
    }
    
    /**
     * 일반 로깅에서 이메일 마스킹
     * @param message 로그 메시지
     * @param email 이메일 주소
     * @param otherParams 기타 파라미터들
     */
    public static void logWithMaskedEmail(String message, String email, Object... otherParams) {
        Object[] allParams = new Object[otherParams.length + 1];
        allParams[0] = maskEmail(email);
        System.arraycopy(otherParams, 0, allParams, 1, otherParams.length);
        
        log.info(message, allParams);
    }
    
    /**
     * 인증 관련 로깅 (자동으로 토큰, 코드 등을 마스킹)
     * @param message 로그 메시지
     * @param params 파라미터들
     */
    public static void logAuthEvent(String message, Object... params) {
        Object[] maskedParams = Arrays.stream(params)
            .map(param -> {
                if (param instanceof String str) {
                    if (EMAIL_PATTERN.matcher(str).matches()) {
                        return maskEmail(str);
                    }
                    if (SENSITIVE_PATTERN.matcher(str).find() || str.length() > 10) {
                        return maskSensitiveData(str);
                    }
                }
                return param;
            })
            .toArray();
            
        log.info("AUTH_EVENT: " + message, maskedParams);
    }
}