package com.routepick.common.exception.customExceptions;

import com.routepick.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * 접근이 거부되었을 때 발생하는 예외
 */
public class AccessDeniedException extends BaseException {
    
    public AccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", 
              "접근 권한이 없습니다.");
    }
    
    public AccessDeniedException(String resource) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", 
              "접근 권한이 없습니다: " + resource);
    }
    
    public AccessDeniedException(String resource, String reason) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", 
              "접근 권한이 없습니다: " + resource + " (" + reason + ")");
    }
}
