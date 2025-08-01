package com.routepick.api.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 인증 코드 검증 요청 DTO
 */
@Getter
@Setter
public class VerifyCodeRequest {
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "인증 코드는 필수입니다.")
    @Size(min = 6, max = 6, message = "인증코드는 6자리여야 합니다.")
    private String verificationCode;

    @NotBlank(message = "세션 토큰은 필수입니다.")
    private String sessionToken;
}
