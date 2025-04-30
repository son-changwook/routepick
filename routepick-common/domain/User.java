package com.scw.routepick.domain;

import lombok.Data;
import java.time.LocalDateTime;
import com.scw.routepick.common.enums.UserType;
import com.scw.routepick.common.enums.UserStatus;

@Data
public class User {

    private Long userId; // 유저 아이디
    private String email; // 이메일
    private String passwordHash; // 비밀번호 해시
    private String userName; // 유저 이름
    private String phone; // 전화번호
    private String profileImageUrl; // 프로필 이미지 URL
    private UserType userType; // 유저 타입
    private LocalDateTime createdAt; // 생성 일시
    private LocalDateTime updatedAt; // 수정 일시
    private LocalDateTime lastLoginAt; // 마지막 로그인 일시
    private UserStatus userStatus; // 유저 상태

}
