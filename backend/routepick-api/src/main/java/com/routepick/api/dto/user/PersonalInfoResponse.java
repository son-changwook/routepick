package com.routepick.api.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 개인정보 조회 응답 DTO
 * 사용자의 개인정보를 조회할 때 사용됩니다.
 * 이메일은 읽기 전용이며, 비밀번호는 보안상 제외됩니다.
 */
@Getter
@Builder
@Schema(description = "개인정보 조회 응답")
public class PersonalInfoResponse {

    @Schema(description = "이메일 (읽기 전용)", example = "user@example.com")
    private String email;

    @Schema(description = "사용자명", example = "climber123")
    private String userName;

    @Schema(description = "전화번호", example = "010-1234-5678")
    private String phone;

    @Schema(description = "프로필 이미지 URL", example = "/api/files/profiles/user123.jpg")
    private String profileImageUrl;

    @Schema(description = "생년월일", example = "1990-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @Schema(description = "주소", example = "서울시 강남구")
    private String address;

    @Schema(description = "상세주소", example = "123-45")
    private String detailAddress;

    @Schema(description = "비상연락처", example = "010-9876-5432")
    private String emergencyContact;
} 