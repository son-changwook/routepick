package com.routepick.api.exception.customExceptions;

@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class RequestValidationException extends RuntimeException {
    public RequestValidationException(String message) {
        super(message);
    }
}

// 3. RequestValidationException.java
// 요청 데이터가 유효하지 않을 때 발생시키는 예외.
// 예: 필수 필드 누락.