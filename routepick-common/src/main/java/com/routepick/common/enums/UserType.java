package com.routepick.common.enums;

/**
 * 사용자 유형
 */
public enum UserType {
    ADMIN("관리자"),
    GYM_ADMIN("체육관 관리자"), 
    REGULAR("일반 사용자");

    private final String description;

    UserType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}