package com.routepick.api.dto.user;

import com.routepick.common.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 정보 응답 DTO
 * users 테이블의 기본 회원 정보를 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 정보 응답")
public class UserInfoResponse {
    
    @Schema(description = "사용자 ID", example = "1")
    private Long userId;
    
    @Schema(description = "이메일", example = "user@example.com")
    private String email;
    
    @Schema(description = "사용자 실명", example = "홍길동")
    private String userName;
    
    @Schema(description = "닉네임", example = "climber123")
    private String nickName;
    
    @Schema(description = "전화번호", example = "010-1234-5678")
    private String phone;
    
    @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
    private String profileImageUrl;
    
    @Schema(description = "생년월일", example = "1990-01-01")
    private LocalDate birthDate;
    
    @Schema(description = "주소", example = "서울시 강남구")
    private String address;
    
    @Schema(description = "상세주소", example = "테헤란로 123")
    private String detailAddress;
    
    @Schema(description = "비상연락처", example = "010-9876-5432")
    private String emergencyContact;
} 