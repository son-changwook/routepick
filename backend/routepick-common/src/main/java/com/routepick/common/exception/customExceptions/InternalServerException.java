package com.routepick.common.exception.customExceptions;

import com.routepick.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 내부 서버 오류가 발생했을 때 사용하는 예외
 */
public class InternalServerException extends BaseException {
    
    public InternalServerException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", 
              "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }
    
    public InternalServerException(String details) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", 
              "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", details);
    }
    
    public InternalServerException(String details, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", 
              "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", details, cause);
    }
}
