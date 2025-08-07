package com.routepick.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 닉네임 중복 검사 요청 DTO
 * 회원가입 전 닉네임 중복 여부를 확인하기 위한 요청 정보를 담습니다.
 */
@Schema(description = "닉네임 중복 검사 요청 정보")
@Getter
@Setter
public class NickNameCheckRequest {
    
    @Schema(description = "확인할 닉네임", example = "climber123")
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣_-]+$", 
             message = "닉네임은 영문, 숫자, 한글, 언더스코어, 하이픈만 사용 가능합니다.")
    private String nickName;
    
    @Override
    public String toString() {
        return "NickNameCheckRequest{" +
                "nickName='" + nickName + '\'' +
                '}';
    }
}
