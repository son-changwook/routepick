package com.scw.routepick.enums;

/**
 * 토큰의 유형을 나타내는 열거형
 */
public enum TokenType {
    ACCESS, // 액세스 토큰
    REFRESH, // 리프레시 토큰
    RESET_PASSWORD, // 비밀번호 재설정 토큰
    EMAIL_VERIFICATION // 이메일 인증 토큰
}
