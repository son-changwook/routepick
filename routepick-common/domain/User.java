package com.scw.routepick.common.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long userId;
    private String email;
    private String passwordHash;
    private String userName;
    private String phone;
    private String profileImageUrl;
    private UserType userType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private UserStatus userStatus;

    public enum UserType {
        NORMAL, ADMIN, GYM_ADMIN
    }

    public enum UserStatus {
        ACTIVE, INACTIVE, SUSPENDED, DELETED
    }
}
