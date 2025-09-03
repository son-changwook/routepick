# Step 7-1d: Response DTOs 구현

> 인증 및 이메일 관련 Response DTO 완전 구현  
> 생성일: 2025-08-22  
> 보안: 민감정보 마스킹, 필요 정보만 노출

---

## 🎯 설계 원칙

- **최소 정보 노출**: 필요한 정보만 반환
- **민감정보 마스킹**: 이메일, 전화번호 부분 마스킹
- **일관된 구조**: ApiResponse 래퍼 사용
- **타임스탬프 포함**: 응답 시간 기록
- **Swagger 문서화**: @Schema 완벽 적용

---

## 📤 Response DTO 구현

### 1. UserResponse.java
```java
package com.routepick.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.routepick.common.enums.UserStatus;
import com.routepick.common.enums.UserType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자 정보 응답 DTO
 * - 회원가입, 로그인 후 반환되는 사용자 정보
 * - 민감정보 제외 또는 마스킹 처리
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "사용자 정보 응답")
public class UserResponse {
    
    @Schema(description = "사용자 ID", example = "1")
    private Long id;
    
    @Schema(description = "이메일 (마스킹 처리)", example = "us***@example.com")
    private String email;
    
    @Schema(description = "닉네임", example = "클라이머123")
    private String nickname;
    
    @Schema(description = "사용자 타입", example = "USER")
    private UserType userType;
    
    @Schema(description = "사용자 상태", example = "ACTIVE")
    private UserStatus userStatus;
    
    @Schema(description = "프로필 이미지 URL", 
            example = "https://cdn.routepickr.com/profile/1.jpg")
    private String profileImageUrl;
    
    @Schema(description = "이메일 인증 여부", example = "false")
    private Boolean emailVerified;
    
    @Schema(description = "휴대폰 인증 여부", example = "false")
    private Boolean phoneVerified;
    
    @Schema(description = "마케팅 수신 동의", example = "true")
    private Boolean marketingConsent;
    
    @Schema(description = "가입일시", example = "2024-01-15T10:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @Schema(description = "마지막 로그인 일시", example = "2024-01-20T15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastLoginAt;
    
    @Schema(description = "프로필 완성도 (%)", example = "75")
    private Integer profileCompleteness;
    
    @Schema(description = "팔로워 수", example = "42")
    private Integer followerCount;
    
    @Schema(description = "팔로잉 수", example = "28")
    private Integer followingCount;
    
    @Schema(description = "완등한 루트 수", example = "156")
    private Integer completedRouteCount;
    
    /**
     * 이메일 마스킹 처리
     * user@example.com -> us***@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        String[] parts = email.split("@");
        String localPart = parts[0];
        
        if (localPart.length() <= 2) {
            return "***@" + parts[1];
        }
        
        return localPart.substring(0, 2) + "***@" + parts[1];
    }
    
    /**
     * 전화번호 마스킹 처리
     * 010-1234-5678 -> 010-****-5678
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 13) {
            return phone;
        }
        
        return phone.substring(0, 4) + "****" + phone.substring(8);
    }
}
```

### 2. TokenResponse.java
```java
package com.routepick.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JWT 토큰 응답 DTO
 * - 로그인, 토큰 갱신 시 반환
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "JWT 토큰 응답")
public class TokenResponse {
    
    @Schema(description = "액세스 토큰", 
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;
    
    @Schema(description = "리프레시 토큰", 
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
    
    @Schema(description = "토큰 타입", example = "Bearer")
    private String tokenType = "Bearer";
    
    @Schema(description = "액세스 토큰 만료 시간 (초)", example = "3600")
    private Long expiresIn;
    
    @Schema(description = "리프레시 토큰 만료 시간 (초)", example = "2592000")
    private Long refreshExpiresIn;
    
    @Schema(description = "토큰 발급 시간", example = "2024-01-20T15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime issuedAt;
    
    @Schema(description = "스코프 (권한 범위)", example = "read write")
    private String scope;
    
    /**
     * 간편 생성 메서드
     */
    public static TokenResponse of(String accessToken, String refreshToken, 
                                   Long expiresIn, Long refreshExpiresIn) {
        return TokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(expiresIn)
            .refreshExpiresIn(refreshExpiresIn)
            .issuedAt(LocalDateTime.now())
            .scope("read write")
            .build();
    }
}
```

### 3. LoginResponse.java
```java
package com.routepick.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 로그인 응답 DTO
 * - 사용자 정보와 토큰 정보를 함께 반환
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "로그인 응답")
public class LoginResponse {
    
    @Schema(description = "사용자 정보")
    private UserResponse user;
    
    @Schema(description = "JWT 토큰 정보")
    private TokenResponse tokens;
    
    @Schema(description = "로그인 성공 여부", example = "true")
    private Boolean success = true;
    
    @Schema(description = "로그인 메시지", example = "로그인이 완료되었습니다.")
    private String message;
    
    @Schema(description = "로그인 시간", example = "2024-01-20T15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime loginAt;
    
    @Schema(description = "첫 로그인 여부", example = "false")
    private Boolean isFirstLogin;
    
    @Schema(description = "비밀번호 변경 필요 여부", example = "false")
    private Boolean passwordChangeRequired;
    
    @Schema(description = "추가 인증 필요 여부 (2FA)", example = "false")
    private Boolean additionalAuthRequired;
    
    @Schema(description = "미완료 프로필 항목", example = "[\"profileImage\", \"bio\"]")
    private List<String> incompleteProfileFields;
    
    @Schema(description = "신규 알림 개수", example = "5")
    private Integer unreadNotificationCount;
    
    @Schema(description = "공지사항 팝업 표시 여부", example = "true")
    private Boolean showNoticePopup;
    
    @Schema(description = "공지사항 ID (팝업용)", example = "3")
    private Long noticeId;
    
    /**
     * 성공 응답 생성
     */
    public static LoginResponse success(UserResponse user, TokenResponse tokens) {
        return LoginResponse.builder()
            .user(user)
            .tokens(tokens)
            .success(true)
            .message("로그인이 완료되었습니다.")
            .loginAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 첫 로그인 응답 생성
     */
    public static LoginResponse firstLogin(UserResponse user, TokenResponse tokens) {
        return LoginResponse.builder()
            .user(user)
            .tokens(tokens)
            .success(true)
            .message("환영합니다! 프로필을 완성해주세요.")
            .loginAt(LocalDateTime.now())
            .isFirstLogin(true)
            .incompleteProfileFields(List.of("profileImage", "bio", "climbingLevel"))
            .build();
    }
}
```

### 4. EmailCheckResponse.java
```java
package com.routepick.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이메일 중복 확인 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "이메일 중복 확인 응답")
public class EmailCheckResponse {
    
    @Schema(description = "이메일 사용 가능 여부", example = "true")
    private Boolean available;
    
    @Schema(description = "응답 메시지", example = "사용 가능한 이메일입니다.")
    private String message;
    
    @Schema(description = "확인한 이메일", example = "user@example.com")
    private String email;
    
    @Schema(description = "확인 시간", example = "2024-01-20T15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime checkedAt;
    
    @Schema(description = "추천 이메일 목록 (사용 불가 시)", 
            example = "[\"user1@example.com\", \"user2@example.com\"]")
    private List<String> suggestions;
    
    /**
     * 사용 가능 응답 생성
     */
    public static EmailCheckResponse available(String email) {
        return EmailCheckResponse.builder()
            .available(true)
            .message("사용 가능한 이메일입니다.")
            .email(email)
            .checkedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 사용 불가 응답 생성
     */
    public static EmailCheckResponse unavailable(String email, List<String> suggestions) {
        return EmailCheckResponse.builder()
            .available(false)
            .message("이미 사용 중인 이메일입니다.")
            .email(email)
            .checkedAt(LocalDateTime.now())
            .suggestions(suggestions)
            .build();
    }
}
```

### 5. EmailVerificationResponse.java
```java
package com.routepick.dto.email.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 이메일 인증 응답 DTO
 * - 인증 코드 발송 및 검증 결과
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "이메일 인증 응답")
public class EmailVerificationResponse {
    
    @Schema(description = "인증 대상 이메일", example = "user@example.com")
    private String email;
    
    @Schema(description = "인증 코드 발송 여부", example = "true")
    private Boolean codeSent;
    
    @Schema(description = "인증 완료 여부", example = "false")
    private Boolean verified;
    
    @Schema(description = "응답 메시지", 
            example = "인증 코드가 이메일로 발송되었습니다.")
    private String message;
    
    @Schema(description = "인증 코드 만료 시간 (초)", example = "300")
    private Integer expiresIn;
    
    @Schema(description = "발송 시간", example = "2024-01-20T15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sentAt;
    
    @Schema(description = "인증 완료 시간", example = "2024-01-20T15:48:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime verifiedAt;
    
    @Schema(description = "재발송 횟수", example = "1")
    private Integer resendCount;
    
    @Schema(description = "다음 재발송 가능 시간 (초)", example = "30")
    private Integer nextResendAvailableIn;
    
    @Schema(description = "남은 재발송 가능 횟수", example = "4")
    private Integer remainingResendCount;
    
    @Schema(description = "인증 시도 횟수", example = "1")
    private Integer attemptCount;
    
    @Schema(description = "남은 인증 시도 횟수", example = "4")
    private Integer remainingAttemptCount;
    
    /**
     * 발송 성공 응답 생성
     */
    public static EmailVerificationResponse sent(String email) {
        return EmailVerificationResponse.builder()
            .email(email)
            .codeSent(true)
            .verified(false)
            .message("인증 코드가 이메일로 발송되었습니다. 5분 이내에 입력해주세요.")
            .expiresIn(300)
            .sentAt(LocalDateTime.now())
            .resendCount(0)
            .nextResendAvailableIn(30)
            .remainingResendCount(5)
            .build();
    }
    
    /**
     * 인증 성공 응답 생성
     */
    public static EmailVerificationResponse verified(String email) {
        return EmailVerificationResponse.builder()
            .email(email)
            .codeSent(false)
            .verified(true)
            .message("이메일 인증이 완료되었습니다.")
            .verifiedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 인증 실패 응답 생성
     */
    public static EmailVerificationResponse failed(String email, int remainingAttempts) {
        return EmailVerificationResponse.builder()
            .email(email)
            .codeSent(false)
            .verified(false)
            .message("인증 코드가 일치하지 않습니다.")
            .remainingAttemptCount(remainingAttempts)
            .build();
    }
}
```

---

## 🏗️ 공통 응답 구조

### ApiResponse.java
```java
package com.routepick.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 통일된 API 응답 구조
 * @param <T> 응답 데이터 타입
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "API 응답 래퍼")
public class ApiResponse<T> {
    
    @Schema(description = "성공 여부", example = "true")
    private Boolean success;
    
    @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.")
    private String message;
    
    @Schema(description = "응답 데이터")
    private T data;
    
    @Schema(description = "에러 코드 (실패 시)", example = "USER_NOT_FOUND")
    private String errorCode;
    
    @Schema(description = "에러 상세 (실패 시)")
    private List<ErrorDetail> errors;
    
    @Schema(description = "응답 시간", example = "2024-01-20T15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    @Schema(description = "요청 추적 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String traceId;
    
    /**
     * 성공 응답 생성
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 메시지와 함께 성공 응답 생성
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 에러 응답 생성
     */
    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .errorCode(errorCode)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 상세 에러와 함께 에러 응답 생성
     */
    public static <T> ApiResponse<T> error(String errorCode, String message, List<ErrorDetail> errors) {
        return ApiResponse.<T>builder()
            .success(false)
            .errorCode(errorCode)
            .message(message)
            .errors(errors)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 에러 상세 정보
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorDetail {
        
        @Schema(description = "에러 필드", example = "email")
        private String field;
        
        @Schema(description = "에러 값", example = "invalid-email")
        private String value;
        
        @Schema(description = "에러 메시지", example = "올바른 이메일 형식이 아닙니다.")
        private String message;
    }
}
```

### PageResponse.java
```java
package com.routepick.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이징 응답 DTO
 * @param <T> 콘텐츠 타입
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "페이징 응답")
public class PageResponse<T> {
    
    @Schema(description = "콘텐츠 목록")
    private List<T> content;
    
    @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
    private Integer page;
    
    @Schema(description = "페이지 크기", example = "20")
    private Integer size;
    
    @Schema(description = "전체 요소 개수", example = "100")
    private Long totalElements;
    
    @Schema(description = "전체 페이지 수", example = "5")
    private Integer totalPages;
    
    @Schema(description = "첫 페이지 여부", example = "true")
    private Boolean first;
    
    @Schema(description = "마지막 페이지 여부", example = "false")
    private Boolean last;
    
    @Schema(description = "빈 페이지 여부", example = "false")
    private Boolean empty;
    
    /**
     * Spring Page 객체로부터 생성
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .empty(page.isEmpty())
            .build();
    }
}
```

---

## 🔒 보안 처리

### ResponseMaskingUtil.java
```java
package com.routepick.util;

/**
 * 응답 데이터 마스킹 유틸리티
 * - 민감정보 보호
 */
public class ResponseMaskingUtil {
    
    /**
     * 이메일 마스킹
     * user@example.com -> us***@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        String[] parts = email.split("@");
        String localPart = parts[0];
        
        if (localPart.length() <= 2) {
            return "***@" + parts[1];
        }
        
        return localPart.substring(0, 2) + "***@" + parts[1];
    }
    
    /**
     * 전화번호 마스킹
     * 010-1234-5678 -> 010-****-5678
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 13) {
            return phone;
        }
        
        return phone.substring(0, 4) + "****" + phone.substring(8);
    }
    
    /**
     * 이름 마스킹
     * 홍길동 -> 홍*동
     */
    public static String maskName(String name) {
        if (name == null || name.length() < 2) {
            return name;
        }
        
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        
        return name.charAt(0) + "*" + name.substring(name.length() - 1);
    }
    
    /**
     * 주민등록번호 마스킹
     * 901231-1234567 -> 901231-1******
     */
    public static String maskRrn(String rrn) {
        if (rrn == null || rrn.length() < 14) {
            return rrn;
        }
        
        return rrn.substring(0, 8) + "******";
    }
}
```

---

*Step 7-1d 완료: Response DTOs 구현 (5개 + 공통 응답 구조)*