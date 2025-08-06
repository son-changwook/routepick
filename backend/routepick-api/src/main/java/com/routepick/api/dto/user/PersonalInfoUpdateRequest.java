package com.routepick.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 개인정보 수정 요청 DTO
 * 사용자의 개인정보를 수정할 때 사용됩니다.
 * 이메일은 수정 불가능하며, 비밀번호는 별도 API를 통해 수정합니다.
 */
@Getter
@Setter
@Schema(description = "개인정보 수정 요청")
public class PersonalInfoUpdateRequest {

    @Schema(description = "사용자명 (영문, 숫자, 한글, 언더스코어, 하이픈만 사용 가능)", example = "climber123")
    @Size(min = 2, max = 20, message = "사용자명은 2자 이상 20자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣_-]+$", message = "사용자명은 영문, 숫자, 한글, 언더스코어, 하이픈만 사용 가능합니다.")
    private String userName;

    @Schema(description = "전화번호 (010-1234-5678 형식)", example = "010-1234-5678")
    @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")   
    private String phone;

    @Schema(description = "생년월일 (YYYY-MM-DD 형식, 선택사항)", example = "1990-01-01")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "올바른 생년월일 형식이 아닙니다. (YYYY-MM-DD)")
    private String birthDate;

    @Schema(description = "주소 (선택사항)", example = "서울시 강남구")
    @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
    private String address;

    @Schema(description = "상세주소 (선택사항)", example = "테헤란로 123")
    @Size(max = 255, message = "상세주소는 255자 이하여야 합니다.")
    private String detailAddress;

    @Schema(description = "비상연락처 (선택사항)", example = "010-9876-5432")
    @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "올바른 비상연락처 형식이 아닙니다.")
    private String emergencyContact;
} 