package com.routepick.api.dto.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원가입 요청 DTO
 * 이메일, 비밀번호, 사용자명(실명), 닉네임, 전화번호, 이메일 인증 토큰, 약관 동의 여부를 입력받아 회원가입 요청을 처리합니다.
 * 보안을 위한 입력 검증이 강화되었습니다.
 */
@Schema(description = "회원가입 요청 정보")
@Getter
@Setter
public class SignupRequest {
    
   // 기본 회원 정보
   @Schema(description = "사용자 이메일 주소", example = "user@example.com")
   @NotBlank(message = "이메일은 필수입니다.")
   @Email(message = "올바른 이메일 형식이 아닙니다.")
   @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
   @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", 
            message = "올바른 이메일 형식이 아닙니다.")
   private String email;
   
   @Schema(description = "사용자 비밀번호 (영문, 숫자, 특수문자 포함)", example = "password123!")
   @NotBlank(message = "비밀번호는 필수입니다.")
   @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
   @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$", 
            message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.")
   private String password;
   
   @Schema(description = "사용자 실명 (영문, 숫자, 한글, 언더스코어, 하이픈만 사용 가능)", example = "홍길동")
   @NotBlank(message = "사용자명은 필수입니다.")
   @Size(min = 2, max = 20, message = "사용자명은 2자 이상 20자 이하여야 합니다.")
   @Pattern(regexp = "^[a-zA-Z0-9가-힣_-]+$", message = "사용자명은 영문, 숫자, 한글, 언더스코어, 하이픈만 사용 가능합니다.")
   private String userName;
   
   @Schema(description = "사용자 닉네임 (영문, 숫자, 한글, 언더스코어, 하이픈만 사용 가능)", example = "climber123")
   @NotBlank(message = "닉네임은 필수입니다.")
   @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
   @Pattern(regexp = "^[a-zA-Z0-9가-힣_-]+$", message = "닉네임은 영문, 숫자, 한글, 언더스코어, 하이픈만 사용 가능합니다.")
   private String nickName;
   
   @Schema(description = "전화번호 (010-1234-5678 형식)", example = "010-1234-5678")
   @NotBlank(message = "전화번호는 필수입니다.")
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
   
   // 이메일 인증 토큰 (필수)
   @Schema(description = "이메일 인증 후 발급받은 등록 토큰", example = "registration-token-12345")
   @NotBlank(message = "이메일 인증 토큰은 필수입니다.")
   private String registrationToken;
   
   // 약관 동의 (각 약관별 개별 동의)
   @Schema(description = "이용약관 동의 여부", example = "true")
   @NotNull(message = "이용약관 동의가 필요합니다.")
   private Boolean agreeTerms; // 이용약관 동의
   
   @Schema(description = "개인정보처리방침 동의 여부", example = "true")
   @NotNull(message = "개인정보처리방침 동의가 필요합니다.")
   private Boolean agreePrivacy; // 개인정보처리방침 동의
   
   @Schema(description = "마케팅 수신 동의 여부 (선택사항)", example = "false")
   private Boolean marketingAgreed; // 마케팅 수신 동의
   
   @Schema(description = "위치정보 수집 동의 여부 (선택사항)", example = "false")
   private Boolean locationAgreed; // 위치정보 수집 동의
   
   /**
    * 필수 약관 동의 여부 확인
    * @return 필수 약관에 모두 동의한 경우 true
    */
   public boolean isRequiredAgreementValid() {
       return Boolean.TRUE.equals(agreeTerms) && Boolean.TRUE.equals(agreePrivacy);
   }
   
   /**
    * 마케팅 수신 동의 여부 확인
    * @return 마케팅 수신에 동의한 경우 true
    */
   public boolean isMarketingAgreed() {
       return Boolean.TRUE.equals(marketingAgreed);
   }
   
   /**
    * 위치정보 수집 동의 여부 확인
    * @return 위치정보 수집에 동의한 경우 true
    */
   public boolean isLocationAgreed() {
       return Boolean.TRUE.equals(locationAgreed);
   }

   @Override
   public String toString() {
       return "SignupRequest{" +
               "email='" + email + '\'' +
               ", userName='" + userName + '\'' +
               ", nickName='" + nickName + '\'' +
               ", phone='" + phone + '\'' +
               ", birthDate='" + birthDate + '\'' +
               ", address='" + address + '\'' +
               ", detailAddress='" + detailAddress + '\'' +
               ", emergencyContact='" + emergencyContact + '\'' +
               ", registrationToken='" + registrationToken + '\'' +
               ", agreeTerms=" + agreeTerms +
               ", agreePrivacy=" + agreePrivacy +
               ", marketingAgreed=" + marketingAgreed +
               ", locationAgreed=" + locationAgreed +
               '}';
   }
}
