package com.routepick.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequest {
    
   // 기본 회원 정보
   @NotBlank(message = "이메일은 필수입니다.")
   @Email(message = "올바른 이메일 형식이 아닙니다.")
   @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
   private String email;
   
   @NotBlank(message = "비밀번호는 필수입니다.")
   @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
   @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$", 
            message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.")
   private String password;
   
   @NotBlank(message = "사용자명은 필수입니다.")
   @Size(min = 2, max = 20, message = "사용자명은 2자 이상 20자 이하여야 합니다.")
   @Pattern(regexp = "^[a-zA-Z0-9가-힣_-]+$", message = "사용자명은 영문, 숫자, 한글, 언더스코어, 하이픈만 사용 가능합니다.")
   private String userName;
   
   @NotBlank(message = "전화번호는 필수입니다.")
   @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")   
   private String phone;
   
   // 약관 동의 (각 약관별 개별 동의)
   @NotNull(message = "이용약관 동의가 필요합니다.")
   private Boolean agreeTerms; // 이용약관 동의
   
   @NotNull(message = "개인정보처리방침 동의가 필요합니다.")
   private Boolean agreePrivacy; // 개인정보처리방침 동의
   
   private Boolean agreeMarketing; // 마케팅 수신 동의 (선택)
   
   private Boolean agreeLocation; // 위치정보 수집 동의 (선택)
   
   /**
    * 필수 약관 동의 여부 확인
    */
   public boolean isRequiredAgreementValid() {
       return agreeTerms != null && agreeTerms && 
              agreePrivacy != null && agreePrivacy;
   }

   /**
    * 마케팅 수신 동의 여부 확인
    */
   public boolean isMarketingAgreed() {
       return agreeMarketing != null && agreeMarketing;
   }

   /**
    * 위치정보 수집 동의 여부 확인
    */
   public boolean isLocationAgreed() {
       return agreeLocation != null && agreeLocation;
   }

   @Override
   public String toString() {
       return "SignupRequest{" +
               "email='" + email + '\'' +
               ", userName='" + userName + '\'' +
               ", phone='" + phone + '\'' +
               ", agreeTerms=" + agreeTerms +
               ", agreePrivacy=" + agreePrivacy +
               ", agreeMarketing=" + agreeMarketing +
               ", agreeLocation=" + agreeLocation +
               '}';
   }

}
