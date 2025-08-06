package com.routepick.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 입력 검증 관련 예외
 * 주로 422 Unprocessable Entity 상황에서 사용
 */
public class ValidationException extends BaseException {
    
    /**
     * 검증 실패한 필드명
     */
    private final String fieldName;
    
    /**
     * 입력된 값
     */
    private final Object rejectedValue;
    
    public ValidationException(String fieldName, Object rejectedValue, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
        this.fieldName = fieldName;
        this.rejectedValue = rejectedValue;
    }
    
    public ValidationException(String fieldName, String message) {
        this(fieldName, null, message);
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public Object getRejectedValue() {
        return rejectedValue;
    }
    
    // 자주 사용되는 검증 예외들을 static 메서드로 제공
    public static ValidationException required(String fieldName) {
        return new ValidationException(fieldName, String.format("%s는 필수 입력 항목입니다.", fieldName));
    }
    
    public static ValidationException invalidFormat(String fieldName, String expectedFormat) {
        return new ValidationException(fieldName, String.format("%s의 형식이 올바르지 않습니다. 예상 형식: %s", fieldName, expectedFormat));
    }
    
    public static ValidationException invalidLength(String fieldName, int minLength, int maxLength) {
        return new ValidationException(fieldName, String.format("%s는 %d자 이상 %d자 이하로 입력해주세요.", fieldName, minLength, maxLength));
    }
    
    public static ValidationException duplicateValue(String fieldName, Object value) {
        return new ValidationException(fieldName, value, String.format("이미 사용 중인 %s입니다.", fieldName));
    }
    
    public static ValidationException invalidEmailFormat() {
        return new ValidationException("email", "올바른 이메일 형식이 아닙니다.");
    }
    
    public static ValidationException weakPassword() {
        return new ValidationException("password", "비밀번호는 8자 이상이며, 영문, 숫자, 특수문자를 포함해야 합니다.");
    }
}