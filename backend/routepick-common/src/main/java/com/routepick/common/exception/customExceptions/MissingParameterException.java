package com.routepick.common.exception.customExceptions;

import com.routepick.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 필수 파라미터가 누락되었을 때 발생하는 예외
 */
public class MissingParameterException extends BaseException {
    
    public MissingParameterException(String parameterName) {
        super(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", 
              "필수 파라미터가 누락되었습니다: " + parameterName);
    }
    
    public MissingParameterException(String parameterName, String parameterType) {
        super(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", 
              "필수 파라미터가 누락되었습니다: " + parameterName + " (" + parameterType + ")");
    }
}
