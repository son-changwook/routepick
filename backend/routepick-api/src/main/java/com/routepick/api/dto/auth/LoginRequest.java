package com.routepick.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 로그인 요청 DTO
 * 이메일과 비밀번호를 입력받아 로그인 요청을 처리합니다.
 */
@Schema(description = "로그인 요청 정보")
@Getter
@Setter
public class LoginRequest {
    
    @Schema(description = "사용자 이메일 주소", example = "user@example.com")
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", 
             message = "올바른 이메일 형식이 아닙니다.")
    private String email;
    
    @Schema(description = "사용자 비밀번호", example = "password123!")
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 1, max = 100, message = "비밀번호는 1자 이상 100자 이하여야 합니다.")
    private String password;
    
    @Override
    public String toString() {
        return "LoginRequest{" +
                "email='" + email + '\'' +
                ", password='[PROTECTED]'" +
                '}';
    }
}
