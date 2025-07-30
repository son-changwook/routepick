package com.routepick.api.exception.customExceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_ACCEPTABLE)
public class RequestNotAcceptableException extends RuntimeException {
    public RequestNotAcceptableException(String message) {
        super(message);
    }
}

// 2. RequestNotAcceptableException.java
// 요청이 허용되지 않는 상태일 때 발생시키는 예외.
// 예: 허용되지 않는 메서드 호출.
