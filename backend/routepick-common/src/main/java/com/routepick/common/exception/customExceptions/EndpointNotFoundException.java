package com.routepick.common.exception.customExceptions;

import com.routepick.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 요청한 엔드포인트를 찾을 수 없을 때 발생하는 예외
 */
public class EndpointNotFoundException extends BaseException {
    
    public EndpointNotFoundException(String path) {
        super(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", 
              "요청한 API를 찾을 수 없습니다: " + path);
    }
    
    public EndpointNotFoundException(String method, String path) {
        super(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", 
              "요청한 API를 찾을 수 없습니다: " + method + " " + path);
    }
}
