package com.routepick.api.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 닉네임 중복 검사 응답 DTO
 * 닉네임 중복 여부와 사용 가능 여부를 반환합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "닉네임 중복 검사 응답 정보")
public class NickNameCheckResponse {
    
    @Schema(description = "확인한 닉네임", example = "climber123")
    private String nickName;
    
    @Schema(description = "사용 가능 여부", example = "true")
    private boolean available;
    
    @Schema(description = "응답 메시지", example = "사용 가능한 닉네임입니다.")
    private String message;
    
    /**
     * 사용 가능한 닉네임인 경우의 응답 생성
     */
    public static NickNameCheckResponse available(String nickName) {
        return NickNameCheckResponse.builder()
                .nickName(nickName)
                .available(true)
                .message("사용 가능한 닉네임입니다.")
                .build();
    }
    
    /**
     * 이미 사용 중인 닉네임인 경우의 응답 생성
     */
    public static NickNameCheckResponse unavailable(String nickName) {
        return NickNameCheckResponse.builder()
                .nickName(nickName)
                .available(false)
                .message("이미 사용 중인 닉네임입니다.")
                .build();
    }
}
