package com.routepick.common.exception.customExceptions;

import com.routepick.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 잘못된 JSON 형식일 때 발생하는 예외
 */
public class InvalidJsonException extends BaseException {
    
    public InvalidJsonException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_JSON", 
              "올바른 JSON 형식으로 요청해주세요.");
    }
    
    public InvalidJsonException(String details) {
        super(HttpStatus.BAD_REQUEST, "INVALID_JSON", 
              "올바른 JSON 형식으로 요청해주세요.", details);
    }
}
