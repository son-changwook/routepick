package com.routepick.api.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 회원가입 응답 DTO
 * 회원가입 성공 시 클라이언트에게 반환할 정보만 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponse {
    
    /**
     * 사용자 ID
     */
    private Long userId;
    
    /**
     * 이메일 주소
     */
    private String email;
    
    /**
     * 표시용 닉네임
     */
    private String displayName;
    
    /**
     * 프로필 이미지 URL (선택사항)
     */
    private String profileImageUrl;
    
    /**
     * 회원가입 완료 메시지
     */
    private String message;
} 