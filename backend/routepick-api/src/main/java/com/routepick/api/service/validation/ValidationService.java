package com.routepick.api.service.validation;

import com.routepick.api.dto.auth.SignupRequest;
import com.routepick.common.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.routepick.api.security.SqlInjectionProtection;
import com.routepick.common.exception.SecurityException;

import java.util.regex.Pattern;

/**
 * 전문적인 검증 서비스
 * 비즈니스 로직과 검증 로직을 분리하여 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {
    
    private final SqlInjectionProtection sqlInjectionProtection;

    // 보안 위험 패턴들 (정규식 수정)
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('|--|;|\\||\\*|exec\\s+s|exec\\s+x)"
    );
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        ".*(<script[^>]*>.*?</script>|javascript:|on\\w+\\s*=|<iframe|<embed|<object|eval\\()",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern DANGEROUS_CHARS_PATTERN = Pattern.compile(
        "[<>\"'%;()&+\\\\]"
    );
    
    // 길이 제한
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MAX_PHONE_LENGTH = 20;
    private static final int MAX_ADDRESS_LENGTH = 255;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 100;
    
    // 이메일 패턴 (RFC 5322 기반 간소화)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    // 사용자명 패턴 (영문, 한글만 허용)
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z가-힣]+$"
    );
    
    // 닉네임 패턴 (영문, 숫자, 언더스코어, 마침표, 골뱅이, 하이픈만 허용 - 인스타그램 스타일)
    private static final Pattern NICKNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._@-]+$"
    );
    
    // 전화번호 패턴 (한국 휴대폰 번호)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^01[0-9]-?\\d{3,4}-?\\d{4}$"
    );
    
    // 비밀번호 강도 패턴 (영문, 숫자, 특수문자 포함)
    private static final Pattern PASSWORD_STRENGTH_PATTERN = Pattern.compile(
        "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$"
    );

    /**
     * 회원가입 요청 전체 검증
     * @param request 회원가입 요청
     */
    public void validateSignupRequest(SignupRequest request) {
        validateEmail(request.getEmail());
        validateUserName(request.getUserName());
        validateNickName(request.getNickName());  // 닉네임 검증 추가
        validatePassword(request.getPassword());
        validatePhone(request.getPhone());
        
        if (request.getAddress() != null) {
            validateAddress(request.getAddress());
        }
        
        if (request.getDetailAddress() != null) {
            validateAddress(request.getDetailAddress());
        }
        
        if (request.getEmergencyContact() != null) {
            validatePhone(request.getEmergencyContact());
        }
        
        validateRequiredAgreements(request);
    }

    /**
     * 이메일 검증
     * @param email 검증할 이메일
     */
    public void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw ValidationException.required("이메일");
        }
        
        String trimmedEmail = email.trim();
        
        // 길이 검증
        if (trimmedEmail.length() > MAX_EMAIL_LENGTH) {
            throw ValidationException.invalidLength("이메일", 1, MAX_EMAIL_LENGTH);
        }
        
        // 형식 검증
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            throw ValidationException.invalidEmailFormat();
        }
        
        // SQL Injection 방지 검증
        sqlInjectionProtection.validateAndSanitize(trimmedEmail, "email");
    }

    /**
     * 사용자명 검증
     * @param userName 검증할 사용자명
     */
    public void validateUserName(String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            throw ValidationException.required("사용자명");
        }
        
        String trimmedUserName = userName.trim();
        
        // 길이 검증
        if (trimmedUserName.length() < 2 || trimmedUserName.length() > MAX_USERNAME_LENGTH) {
            throw ValidationException.invalidLength("사용자명", 2, MAX_USERNAME_LENGTH);
        }
        
        // 형식 검증
        if (!USERNAME_PATTERN.matcher(trimmedUserName).matches()) {
            throw ValidationException.invalidFormat("사용자명", "영문, 한글만 사용 가능");
        }
        
        // SQL Injection 방지 검증
        sqlInjectionProtection.validateAndSanitize(trimmedUserName, "username");
    }

    /**
     * 비밀번호 검증
     * @param password 검증할 비밀번호
     */
    public void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw ValidationException.required("비밀번호");
        }
        
        // 길이 검증
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw ValidationException.invalidLength("비밀번호", MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH);
        }
        
        // 강도 검증
        if (!PASSWORD_STRENGTH_PATTERN.matcher(password).matches()) {
            throw ValidationException.weakPassword();
        }
        
        // 일반적인 약한 비밀번호 패턴 검증
        validateWeakPasswordPatterns(password);
    }

    /**
     * 전화번호 검증
     * @param phone 검증할 전화번호
     */
    public void validatePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw ValidationException.required("전화번호");
        }
        
        String trimmedPhone = phone.trim();
        
        // 길이 검증
        if (trimmedPhone.length() > MAX_PHONE_LENGTH) {
            throw ValidationException.invalidLength("전화번호", 1, MAX_PHONE_LENGTH);
        }
        
        // 형식 검증
        if (!PHONE_PATTERN.matcher(trimmedPhone).matches()) {
            throw ValidationException.invalidFormat("전화번호", "01X-XXXX-XXXX 형식");
        }
        
        // 보안 검증
        validateSecurityThreats(trimmedPhone, "전화번호");
    }

    /**
     * 주소 검증
     * @param address 검증할 주소
     */
    public void validateAddress(String address) {
        if (address != null && !address.trim().isEmpty()) {
            String trimmedAddress = address.trim();
            
            // 길이 검증
            if (trimmedAddress.length() > MAX_ADDRESS_LENGTH) {
                throw ValidationException.invalidLength("주소", 1, MAX_ADDRESS_LENGTH);
            }
            
            // 보안 검증
            validateSecurityThreats(trimmedAddress, "주소");
        }
    }

    /**
     * 필수 약관 동의 검증
     * @param request 회원가입 요청
     */
    public void validateRequiredAgreements(SignupRequest request) {
        if (!Boolean.TRUE.equals(request.getAgreeTerms())) {
            throw new ValidationException("agreeTerms", "이용약관 동의는 필수입니다.");
        }
        
        if (!Boolean.TRUE.equals(request.getAgreePrivacy())) {
            throw new ValidationException("agreePrivacy", "개인정보처리방침 동의는 필수입니다.");
        }
    }

    /**
     * 보안 위협 검증 (XSS 등)
     * SQL Injection은 SqlInjectionProtection에서 처리
     * @param input 검증할 입력값
     * @param fieldName 필드명
     */
    private void validateSecurityThreats(String input, String fieldName) {
        // XSS 검증
        if (XSS_PATTERN.matcher(input).find()) {
            log.warn("XSS 시도 감지: field={}, length={}", fieldName, input.length());
            throw SecurityException.xssDetected();
        }
        
        // 위험한 특수문자 검증 (이메일과 사용자명에서는 일부 허용)
        if (!fieldName.equals("이메일") && DANGEROUS_CHARS_PATTERN.matcher(input).find()) {
            log.warn("위험한 특수문자 감지: field={}, length={}", fieldName, input.length());
            throw SecurityException.invalidInputFormat(fieldName);
        }
    }

    /**
     * 약한 비밀번호 패턴 검증
     * @param password 검증할 비밀번호
     */
    private void validateWeakPasswordPatterns(String password) {
        // 연속된 문자 검증 (예: 123456, abcdef)
        if (hasConsecutiveChars(password, 4)) {
            throw new ValidationException("password", "연속된 문자나 숫자는 4개 이상 사용할 수 없습니다.");
        }
        
        // 반복 문자 검증 (예: aaaa, 1111)
        if (hasRepeatingChars(password, 4)) {
            throw new ValidationException("password", "같은 문자나 숫자는 4개 이상 반복할 수 없습니다.");
        }
        
        // 일반적인 약한 비밀번호 검증
        String lowerPassword = password.toLowerCase();
        String[] commonPasswords = {
            "password", "12345678", "qwerty123", "admin123", 
            "welcome123", "password123", "test1234"
        };
        
        for (String commonPassword : commonPasswords) {
            if (lowerPassword.contains(commonPassword)) {
                throw new ValidationException("password", "일반적으로 사용되는 약한 비밀번호입니다.");
            }
        }
    }

    /**
     * 연속된 문자 검증
     * @param input 검증할 문자열
     * @param maxLength 최대 연속 길이
     * @return 연속된 문자가 최대 길이를 초과하면 true
     */
    private boolean hasConsecutiveChars(String input, int maxLength) {
        int consecutiveCount = 1;
        for (int i = 1; i < input.length(); i++) {
            if (Math.abs(input.charAt(i) - input.charAt(i - 1)) == 1) {
                consecutiveCount++;
                if (consecutiveCount >= maxLength) {
                    return true;
                }
            } else {
                consecutiveCount = 1;
            }
        }
        return false;
    }

    /**
     * 반복 문자 검증
     * @param input 검증할 문자열
     * @param maxLength 최대 반복 길이
     * @return 반복 문자가 최대 길이를 초과하면 true
     */
    private boolean hasRepeatingChars(String input, int maxLength) {
        int repeatCount = 1;
        for (int i = 1; i < input.length(); i++) {
            if (input.charAt(i) == input.charAt(i - 1)) {
                repeatCount++;
                if (repeatCount >= maxLength) {
                    return true;
                }
            } else {
                repeatCount = 1;
            }
        }
        return false;
    }

    /**
     * 닉네임 검증
     * @param nickName 검증할 닉네임
     */
    public void validateNickName(String nickName) {
        if (nickName == null || nickName.trim().isEmpty()) {
            throw ValidationException.required("닉네임");
        }

        String trimmedNickName = nickName.trim();

        // 길이 검증
        if (trimmedNickName.length() < 2 || trimmedNickName.length() > MAX_USERNAME_LENGTH) {
            throw ValidationException.invalidLength("닉네임", 2, MAX_USERNAME_LENGTH);
        }

        // 형식 검증 (특수문자는 하이픈과 언더스코어만 허용)
        if (!NICKNAME_PATTERN.matcher(trimmedNickName).matches()) {
            throw ValidationException.invalidFormat("닉네임", "영문, 숫자, 언더스코어(_), 마침표(.), 골뱅이(@), 하이픈(-)만 사용 가능");
        }

        // 보안 위협 검증
        validateSecurityThreats(trimmedNickName, "닉네임");

        // SQL Injection 방지 검증
        sqlInjectionProtection.validateAndSanitize(trimmedNickName, "nickname");
    }

    /**
     * 단순 길이만 검증하는 헬퍼 메서드
     * @param input 입력값
     * @param fieldName 필드명
     * @param maxLength 최대 길이
     */
    public void validateLength(String input, String fieldName, int maxLength) {
        if (input != null && input.length() > maxLength) {
            throw ValidationException.invalidLength(fieldName, 0, maxLength);
        }
    }

    /**
     * null이 아닌 문자열의 기본 보안 검증
     * @param input 입력값
     * @param fieldName 필드명
     */
    public void validateBasicSecurity(String input, String fieldName) {
        if (input != null && !input.trim().isEmpty()) {
            validateSecurityThreats(input.trim(), fieldName);
        }
    }
}