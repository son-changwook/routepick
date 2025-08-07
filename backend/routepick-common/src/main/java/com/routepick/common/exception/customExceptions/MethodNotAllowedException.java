package com.routepick.common.exception.customExceptions;

import com.routepick.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 지원하지 않는 HTTP 메서드 요청 시 발생하는 예외
 */
public class MethodNotAllowedException extends BaseException {
    
    public MethodNotAllowedException(String method) {
        super(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", 
              "지원하지 않는 HTTP 메서드입니다: " + method);
    }
    
    public MethodNotAllowedException(String method, String supportedMethods) {
        super(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", 
              "지원하지 않는 HTTP 메서드입니다: " + method + 
              " (지원 메서드: " + supportedMethods + ")");
    }
}
