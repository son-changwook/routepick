package com.routepick.api.exception.customExceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// 4. ResourceNotFoundException.java
// 요청된 리소스가 존재하지 않을 때 발생시키는 예외.
// 예: 존재하지 않는 사용자 ID로 조회 시도.
// 예: 존재하지 않는 게시글 조회 등.