package com.routepick.admin.exception.customExceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_ACCEPTABLE)
public class RequestNotAcceptableException extends RuntimeException {
    public RequestNotAcceptableException(String message) {
        super(message);
    }
}
