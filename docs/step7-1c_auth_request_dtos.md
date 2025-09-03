# Step 7-1c: Request DTOs 구현

> 인증 및 이메일 관련 Request DTO 완전 구현  
> 생성일: 2025-08-22  
> 검증: @Valid, @Pattern, 한국 특화 규칙

---

## 🎯 설계 원칙

- **입력 검증**: Jakarta Validation 완벽 적용
- **한국 특화**: 한글 닉네임, 한국 휴대폰 번호
- **보안 강화**: 패스워드 정책, XSS 방지
- **명확한 에러 메시지**: 사용자 친화적 메시지
- **Swagger 문서화**: @Schema 완벽 적용

---

## 📝 Request DTO 구현

### 1. SignUpRequest.java
```java
package com.routepick.dto.auth.request;

import com.routepick.validation.annotation.KoreanPhone;
import com.routepick.validation.annotation.SecurePassword;
import com.routepick.validation.annotation.UniqueEmail;
import com.routepick.validation.annotation.UniqueNickname;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

/**
 * 회원가입 요청 DTO
 * - 이메일, 비밀번호, 닉네임, 휴대폰, 약관 동의
 * - 한국 특화 검증 규칙 적용
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password") // 비밀번호 로그 제외
@Schema(description = "회원가입 요청 정보")
public class SignUpRequest {
    
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 254, message = "이메일은 254자를 초과할 수 없습니다.")
    @UniqueEmail // 커스텀 검증: 이메일 중복 확인
    @Schema(description = "사용자 이메일", example = "user@example.com", required = true)
    private String email;
    
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,20}$",
        message = "비밀번호는 대소문자, 숫자, 특수문자(@$!%*?&)를 각각 1개 이상 포함해야 합니다."
    )
    @SecurePassword // 커스텀 검증: 일반적인 패스워드 차단
    @Schema(description = "사용자 비밀번호 (8-20자, 대소문자+숫자+특수문자)", 
            example = "Password123!", required = true)
    private String password;
    
    @NotBlank(message = "비밀번호 확인은 필수 입력값입니다.")
    @Schema(description = "비밀번호 확인", example = "Password123!", required = true)
    private String passwordConfirm;
    
    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
    @Pattern(
        regexp = "^[가-힣a-zA-Z0-9]{2,10}$",
        message = "닉네임은 한글, 영문, 숫자만 사용 가능합니다. (특수문자 불가)"
    )
    @UniqueNickname // 커스텀 검증: 닉네임 중복 확인
    @Schema(description = "사용자 닉네임 (2-10자, 한글/영문/숫자)", 
            example = "클라이머123", required = true)
    private String nickname;
    
    @NotBlank(message = "휴대폰 번호는 필수 입력값입니다.")
    @Pattern(
        regexp = "^01[0-9]-\\d{3,4}-\\d{4}$",
        message = "휴대폰 번호는 010-0000-0000 형식이어야 합니다."
    )
    @KoreanPhone // 커스텀 검증: 한국 휴대폰 번호 검증
    @Schema(description = "휴대폰 번호 (한국 형식)", 
            example = "010-1234-5678", required = true)
    private String phone;
    
    @NotEmpty(message = "약관 동의는 필수입니다.")
    @Size(min = 1, message = "최소 1개 이상의 약관에 동의해야 합니다.")
    @Schema(description = "동의한 약관 ID 목록", 
            example = "[1, 2, 3]", required = true)
    private List<@NotNull @Positive Long> agreementIds;
    
    @Schema(description = "마케팅 수신 동의 여부", example = "true", defaultValue = "false")
    private Boolean marketingConsent = false;
    
    @Schema(description = "추천인 코드", example = "FRIEND2024")
    @Pattern(regexp = "^[A-Z0-9]{6,10}$", message = "추천인 코드는 6-10자의 대문자와 숫자만 가능합니다.")
    private String referralCode;
    
    /**
     * 비밀번호 일치 검증
     */
    @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
    @Schema(hidden = true)
    public boolean isPasswordMatching() {
        return password != null && password.equals(passwordConfirm);
    }
    
    /**
     * 필수 약관 포함 검증
     */
    @AssertTrue(message = "필수 약관에 모두 동의해야 합니다.")
    @Schema(hidden = true)
    public boolean hasRequiredAgreements() {
        // 필수 약관 ID: 1(서비스 이용약관), 2(개인정보 처리방침)
        return agreementIds != null && 
               agreementIds.contains(1L) && 
               agreementIds.contains(2L);
    }
}
```

### 2. LoginRequest.java
```java
package com.routepick.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 로그인 요청 DTO
 * - 이메일과 비밀번호로 인증
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password") // 비밀번호 로그 제외
@Schema(description = "로그인 요청 정보")
public class LoginRequest {
    
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 254, message = "이메일은 254자를 초과할 수 없습니다.")
    @Schema(description = "사용자 이메일", example = "user@example.com", required = true)
    private String email;
    
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    @Schema(description = "사용자 비밀번호", example = "Password123!", required = true)
    private String password;
    
    @Schema(description = "자동 로그인 여부", example = "false", defaultValue = "false")
    private Boolean rememberMe = false;
    
    @Schema(description = "디바이스 정보", example = "iPhone 14 Pro")
    @Size(max = 100, message = "디바이스 정보는 100자를 초과할 수 없습니다.")
    private String deviceInfo;
    
    @Schema(description = "앱 버전", example = "1.0.0")
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "앱 버전은 X.Y.Z 형식이어야 합니다.")
    private String appVersion;
}
```

### 3. SocialLoginRequest.java
```java
package com.routepick.dto.auth.request;

import com.routepick.common.enums.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 소셜 로그인 요청 DTO
 * - 4개 제공자 지원: Google, Kakao, Naver, Facebook
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Schema(description = "소셜 로그인 요청 정보")
public class SocialLoginRequest {
    
    @NotNull(message = "소셜 제공자는 필수입니다.")
    @Schema(description = "소셜 제공자", example = "GOOGLE", required = true,
            allowableValues = {"GOOGLE", "KAKAO", "NAVER", "FACEBOOK"})
    private SocialProvider provider;
    
    @NotBlank(message = "소셜 ID는 필수입니다.")
    @Size(max = 255, message = "소셜 ID는 255자를 초과할 수 없습니다.")
    @Schema(description = "소셜 제공자로부터 받은 고유 ID", 
            example = "1234567890", required = true)
    private String socialId;
    
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 254, message = "이메일은 254자를 초과할 수 없습니다.")
    @Schema(description = "소셜 계정 이메일", example = "user@gmail.com")
    private String email;
    
    @NotBlank(message = "이름은 필수입니다.")
    @Size(min = 1, max = 50, message = "이름은 1자 이상 50자 이하여야 합니다.")
    @Schema(description = "사용자 이름", example = "홍길동", required = true)
    private String name;
    
    @Schema(description = "프로필 이미지 URL", 
            example = "https://example.com/profile.jpg")
    @Pattern(regexp = "^https?://.*", message = "올바른 URL 형식이 아닙니다.")
    @Size(max = 500, message = "프로필 이미지 URL은 500자를 초과할 수 없습니다.")
    private String profileImageUrl;
    
    @Schema(description = "소셜 액세스 토큰", required = true)
    @NotBlank(message = "액세스 토큰은 필수입니다.")
    private String accessToken;
    
    @Schema(description = "소셜 리프레시 토큰")
    private String refreshToken;
    
    @Schema(description = "토큰 만료 시간 (초)", example = "3600")
    @Positive(message = "토큰 만료 시간은 양수여야 합니다.")
    private Long expiresIn;
}
```

### 4. EmailCheckRequest.java
```java
package com.routepick.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 이메일 중복 확인 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Schema(description = "이메일 중복 확인 요청")
public class EmailCheckRequest {
    
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 254, message = "이메일은 254자를 초과할 수 없습니다.")
    @Schema(description = "확인할 이메일 주소", 
            example = "user@example.com", required = true)
    private String email;
}
```

### 5. EmailVerificationRequest.java
```java
package com.routepick.dto.email.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 이메일 인증 요청 DTO
 * - 인증 코드 발송 및 검증
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Schema(description = "이메일 인증 요청")
public class EmailVerificationRequest {
    
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 254, message = "이메일은 254자를 초과할 수 없습니다.")
    @Schema(description = "인증할 이메일 주소", 
            example = "user@example.com", required = true)
    private String email;
    
    @Schema(description = "인증 코드 (6자리 숫자)", example = "123456")
    @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 6자리 숫자여야 합니다.")
    private String verificationCode;
    
    @Schema(description = "인증 목적", example = "SIGNUP", 
            allowableValues = {"SIGNUP", "PASSWORD_RESET", "EMAIL_CHANGE"})
    private String purpose = "SIGNUP";
}
```

### 추가 요청 DTO - PasswordResetRequest.java
```java
package com.routepick.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 비밀번호 재설정 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Schema(description = "비밀번호 재설정 요청")
public class PasswordResetRequest {
    
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 254, message = "이메일은 254자를 초과할 수 없습니다.")
    @Schema(description = "비밀번호를 재설정할 계정의 이메일", 
            example = "user@example.com", required = true)
    private String email;
}
```

---

## 🛡️ 커스텀 검증 어노테이션

### 1. @UniqueEmail
```java
package com.routepick.validation.annotation;

import com.routepick.validation.validator.UniqueEmailValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 이메일 중복 검증 어노테이션
 */
@Documented
@Constraint(validatedBy = UniqueEmailValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueEmail {
    String message() default "이미 사용 중인 이메일입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

### 2. @UniqueNickname
```java
package com.routepick.validation.annotation;

import com.routepick.validation.validator.UniqueNicknameValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 닉네임 중복 검증 어노테이션
 */
@Documented
@Constraint(validatedBy = UniqueNicknameValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueNickname {
    String message() default "이미 사용 중인 닉네임입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

### 3. @KoreanPhone
```java
package com.routepick.validation.annotation;

import com.routepick.validation.validator.KoreanPhoneValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 한국 휴대폰 번호 검증 어노테이션
 */
@Documented
@Constraint(validatedBy = KoreanPhoneValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface KoreanPhone {
    String message() default "올바른 한국 휴대폰 번호가 아닙니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

### 4. @SecurePassword
```java
package com.routepick.validation.annotation;

import com.routepick.validation.validator.SecurePasswordValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 보안 패스워드 검증 어노테이션
 * - 일반적인 패스워드 차단
 * - 개인정보 포함 검증
 */
@Documented
@Constraint(validatedBy = SecurePasswordValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurePassword {
    String message() default "보안에 취약한 비밀번호입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

---

## 🔧 검증 유틸리티

### ValidationMessages.java
```java
package com.routepick.validation;

/**
 * 검증 메시지 상수
 */
public class ValidationMessages {
    
    // 이메일
    public static final String EMAIL_REQUIRED = "이메일은 필수 입력값입니다.";
    public static final String EMAIL_INVALID = "올바른 이메일 형식이 아닙니다.";
    public static final String EMAIL_DUPLICATE = "이미 사용 중인 이메일입니다.";
    
    // 비밀번호
    public static final String PASSWORD_REQUIRED = "비밀번호는 필수 입력값입니다.";
    public static final String PASSWORD_LENGTH = "비밀번호는 8자 이상 20자 이하여야 합니다.";
    public static final String PASSWORD_PATTERN = "비밀번호는 대소문자, 숫자, 특수문자를 포함해야 합니다.";
    public static final String PASSWORD_MISMATCH = "비밀번호가 일치하지 않습니다.";
    public static final String PASSWORD_WEAK = "보안에 취약한 비밀번호입니다.";
    
    // 닉네임
    public static final String NICKNAME_REQUIRED = "닉네임은 필수 입력값입니다.";
    public static final String NICKNAME_LENGTH = "닉네임은 2자 이상 10자 이하여야 합니다.";
    public static final String NICKNAME_PATTERN = "닉네임은 한글, 영문, 숫자만 사용 가능합니다.";
    public static final String NICKNAME_DUPLICATE = "이미 사용 중인 닉네임입니다.";
    
    // 휴대폰
    public static final String PHONE_REQUIRED = "휴대폰 번호는 필수 입력값입니다.";
    public static final String PHONE_PATTERN = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.";
    public static final String PHONE_INVALID = "올바른 한국 휴대폰 번호가 아닙니다.";
    
    // 약관
    public static final String AGREEMENT_REQUIRED = "약관 동의는 필수입니다.";
    public static final String AGREEMENT_MANDATORY = "필수 약관에 모두 동의해야 합니다.";
}
```

---

*Step 7-1c 완료: Request DTOs 구현 (5개 + 추가 1개)*