package com.routepick.common.exception.customExceptions;

import com.routepick.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 파라미터 타입이 맞지 않을 때 발생하는 예외
 */
public class TypeMismatchException extends BaseException {
    
    public TypeMismatchException(String parameterName, String expectedType, String actualValue) {
        super(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", 
              "파라미터 타입이 맞지 않습니다: " + parameterName + 
              " (예상: " + expectedType + ", 실제: " + actualValue + ")");
    }
    
    public TypeMismatchException(String parameterName, String expectedType) {
        super(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", 
              "파라미터 타입이 맞지 않습니다: " + parameterName + " (예상: " + expectedType + ")");
    }
}
