package com.routepick.api.exception.customExceptions;

@ResponseStatus(code = HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}

// 1. DuplicateResourceException.java
// 중복된 리소스가 존재할 때 발생시키는 예외.
// 예: 동일한 이메일로 회원가입 시도.
