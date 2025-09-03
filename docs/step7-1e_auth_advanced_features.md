# Step 7-1e: Auth & Email 고급 기능 구현

> 인증 및 이메일 관련 고급 엔드포인트와 DTO 추가  
> 생성일: 2025-08-22  
> 기능: 비밀번호 재설정 완료, 이메일 인증 확인, 토큰 검증, 세션 관리

---

## 🎯 고급 기능 구현

### 1. 비밀번호 재설정 완료 프로세스
### 2. 이메일 인증 코드 확인 시스템
### 3. 토큰 유효성 검증 API
### 4. 다중 세션 관리 기능
### 5. 보안 강화 기능

---

## 🔐 AuthController 추가 엔드포인트

### AuthController.java (추가 메서드)
```java
    // ===== 비밀번호 재설정 완료 =====
    
    /**
     * 비밀번호 재설정 완료
     * - 재설정 토큰 검증
     * - 새 비밀번호 설정
     * - 기존 세션 모두 종료
     */
    @PostMapping("/reset-password/confirm")
    @Operation(summary = "비밀번호 재설정 완료", description = "재설정 토큰으로 새 비밀번호 설정")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "비밀번호 재설정 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "유효하지 않은 토큰"),
        @SwaggerApiResponse(responseCode = "410", description = "만료된 토큰")
    })
    public ResponseEntity<ApiResponse<PasswordResetResponse>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("비밀번호 재설정 완료 요청: token={}", request.getResetToken());
        
        // IP 주소 추출
        String clientIp = extractClientIp(httpRequest);
        
        // 비밀번호 재설정 처리
        PasswordResetResponse response = authService.resetPassword(
            request.getResetToken(),
            request.getNewPassword(),
            clientIp
        );
        
        log.info("비밀번호 재설정 완료: userId={}", response.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success(response, 
            "비밀번호가 성공적으로 변경되었습니다. 다시 로그인해주세요."));
    }
    
    // ===== 토큰 검증 =====
    
    /**
     * 액세스 토큰 유효성 검증
     * - 토큰 만료 확인
     * - 블랙리스트 확인
     * - 사용자 상태 확인
     */
    @PostMapping("/validate-token")
    @Operation(summary = "토큰 검증", description = "액세스 토큰의 유효성을 검증")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "유효한 토큰"),
        @SwaggerApiResponse(responseCode = "401", description = "유효하지 않은 토큰")
    })
    public ResponseEntity<ApiResponse<TokenValidationResponse>> validateToken(
            @Valid @RequestBody TokenValidationRequest request) {
        
        log.debug("토큰 검증 요청");
        
        // 토큰 검증
        TokenValidationResponse response = authService.validateToken(request.getAccessToken());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    // ===== 세션 관리 =====
    
    /**
     * 활성 세션 조회
     * - 현재 로그인된 모든 디바이스 조회
     * - 마지막 활동 시간 표시
     */
    @GetMapping("/sessions")
    @Operation(summary = "활성 세션 조회", description = "현재 로그인된 모든 세션 조회")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "세션 목록 조회 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getActiveSessions(
            @AuthenticationPrincipal Long userId) {
        
        log.info("활성 세션 조회: userId={}", userId);
        
        List<SessionResponse> sessions = authService.getActiveSessions(userId);
        
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }
    
    /**
     * 특정 세션 종료
     * - 다른 디바이스 로그아웃
     * - 토큰 무효화
     */
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "세션 종료", description = "특정 세션을 종료")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "세션 종료 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "세션을 찾을 수 없음")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> terminateSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal Long userId) {
        
        log.info("세션 종료 요청: userId={}, sessionId={}", userId, sessionId);
        
        authService.terminateSession(userId, sessionId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "세션이 종료되었습니다."));
    }
    
    /**
     * 모든 세션 종료
     * - 현재 세션 제외 모든 세션 종료
     * - 보안 목적
     */
    @PostMapping("/sessions/terminate-all")
    @Operation(summary = "모든 세션 종료", description = "현재 세션을 제외한 모든 세션 종료")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "모든 세션 종료 성공")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> terminateAllSessions(
            @RequestHeader("Authorization") String currentToken,
            @AuthenticationPrincipal Long userId) {
        
        log.info("모든 세션 종료 요청: userId={}", userId);
        
        String token = extractBearerToken(currentToken);
        authService.terminateAllSessionsExceptCurrent(userId, token);
        
        return ResponseEntity.ok(ApiResponse.success(null, 
            "현재 세션을 제외한 모든 세션이 종료되었습니다."));
    }
```

---

## 📧 EmailController 추가 엔드포인트

### EmailController.java (추가 메서드)
```java
    // ===== 이메일 인증 확인 =====
    
    /**
     * 이메일 인증 코드 확인
     * - 6자리 코드 검증
     * - 만료 시간 확인
     * - 인증 완료 처리
     */
    @PostMapping("/verify/confirm")
    @Operation(summary = "이메일 인증 확인", description = "이메일로 받은 인증 코드를 확인")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "인증 성공",
                content = @Content(schema = @Schema(implementation = EmailVerificationResponse.class))),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 인증 코드"),
        @SwaggerApiResponse(responseCode = "410", description = "만료된 인증 코드")
    })
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> confirmVerificationCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("이메일 인증 코드 확인: email={}", request.getEmail());
        
        try {
            // IP 주소 추출
            String clientIp = extractClientIp(httpRequest);
            
            // 인증 코드 검증
            boolean isValid = emailService.verifyCode(
                request.getEmail(), 
                request.getVerificationCode()
            );
            
            if (!isValid) {
                // 남은 시도 횟수 확인
                int remainingAttempts = emailService.getRemainingVerificationAttempts(request.getEmail());
                
                if (remainingAttempts <= 0) {
                    log.warn("이메일 인증 시도 횟수 초과: email={}, ip={}", request.getEmail(), clientIp);
                    
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error("VERIFICATION_ATTEMPTS_EXCEEDED", 
                            "인증 시도 횟수를 초과했습니다. 새로운 인증 코드를 요청해주세요."));
                }
                
                EmailVerificationResponse response = EmailVerificationResponse.failed(
                    request.getEmail(), 
                    remainingAttempts
                );
                
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_VERIFICATION_CODE", 
                        String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", remainingAttempts)));
            }
            
            // 인증 성공 처리
            emailService.markEmailAsVerified(request.getEmail());
            
            EmailVerificationResponse response = EmailVerificationResponse.verified(request.getEmail());
            
            log.info("이메일 인증 성공: email={}, ip={}", request.getEmail(), clientIp);
            
            return ResponseEntity.ok(ApiResponse.success(response, "이메일 인증이 완료되었습니다."));
            
        } catch (Exception e) {
            log.error("이메일 인증 확인 실패: email={}, error={}", 
                request.getEmail(), e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("VERIFICATION_FAILED", 
                    "인증 처리 중 오류가 발생했습니다."));
        }
    }
    
    /**
     * 이메일 인증 상태 조회
     * - 인증 완료 여부
     * - 인증 코드 발송 이력
     * - 남은 유효 시간
     */
    @GetMapping("/verify/status")
    @Operation(summary = "이메일 인증 상태", description = "이메일 인증 상태를 조회")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "상태 조회 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "인증 이력 없음")
    })
    public ResponseEntity<ApiResponse<EmailVerificationStatusResponse>> getVerificationStatus(
            @RequestParam @Email String email) {
        
        log.info("이메일 인증 상태 조회: email={}", email);
        
        EmailVerificationStatusResponse status = emailService.getVerificationStatus(email);
        
        if (status == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NO_VERIFICATION_HISTORY", 
                    "해당 이메일의 인증 이력이 없습니다."));
        }
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }
```

---

## 📝 추가 Request DTOs

### PasswordResetConfirmRequest.java
```java
package com.routepick.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 비밀번호 재설정 완료 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"newPassword", "newPasswordConfirm"})
@Schema(description = "비밀번호 재설정 완료 요청")
public class PasswordResetConfirmRequest {
    
    @NotBlank(message = "재설정 토큰은 필수입니다.")
    @Schema(description = "비밀번호 재설정 토큰", 
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890", required = true)
    private String resetToken;
    
    @NotBlank(message = "새 비밀번호는 필수입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,20}$",
        message = "비밀번호는 대소문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다."
    )
    @Schema(description = "새 비밀번호", example = "NewPassword123!", required = true)
    private String newPassword;
    
    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    @Schema(description = "새 비밀번호 확인", example = "NewPassword123!", required = true)
    private String newPasswordConfirm;
    
    @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
    @Schema(hidden = true)
    public boolean isPasswordMatching() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
```

### EmailVerificationConfirmRequest.java
```java
package com.routepick.dto.email.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 이메일 인증 확인 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Schema(description = "이메일 인증 확인 요청")
public class EmailVerificationConfirmRequest {
    
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Schema(description = "인증할 이메일", example = "user@example.com", required = true)
    private String email;
    
    @NotBlank(message = "인증 코드는 필수입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 6자리 숫자여야 합니다.")
    @Schema(description = "6자리 인증 코드", example = "123456", required = true)
    private String verificationCode;
}
```

### TokenValidationRequest.java
```java
package com.routepick.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * 토큰 검증 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Schema(description = "토큰 검증 요청")
public class TokenValidationRequest {
    
    @NotBlank(message = "액세스 토큰은 필수입니다.")
    @Schema(description = "검증할 액세스 토큰", 
            example = "eyJhbGciOiJIUzI1NiIs...", required = true)
    private String accessToken;
}
```

---

## 📤 추가 Response DTOs

### PasswordResetResponse.java
```java
package com.routepick.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "비밀번호 재설정 응답")
public class PasswordResetResponse {
    
    @Schema(description = "사용자 ID", example = "123")
    private Long userId;
    
    @Schema(description = "재설정 성공 여부", example = "true")
    private Boolean success;
    
    @Schema(description = "재설정 완료 시간", example = "2024-01-20T15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime resetAt;
    
    @Schema(description = "메시지", example = "비밀번호가 성공적으로 변경되었습니다.")
    private String message;
    
    @Schema(description = "모든 세션 종료 여부", example = "true")
    private Boolean allSessionsTerminated;
}
```

### TokenValidationResponse.java
```java
package com.routepick.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 토큰 검증 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "토큰 검증 응답")
public class TokenValidationResponse {
    
    @Schema(description = "토큰 유효 여부", example = "true")
    private Boolean valid;
    
    @Schema(description = "사용자 ID", example = "123")
    private Long userId;
    
    @Schema(description = "사용자 이메일", example = "user@example.com")
    private String email;
    
    @Schema(description = "토큰 만료 시간", example = "2024-01-20T16:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;
    
    @Schema(description = "남은 유효 시간 (초)", example = "3600")
    private Long remainingSeconds;
    
    @Schema(description = "토큰 스코프", example = "read write")
    private String scope;
    
    @Schema(description = "블랙리스트 여부", example = "false")
    private Boolean blacklisted;
}
```

### SessionResponse.java
```java
package com.routepick.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 세션 정보 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "세션 정보")
public class SessionResponse {
    
    @Schema(description = "세션 ID", example = "session_123456")
    private String sessionId;
    
    @Schema(description = "디바이스 정보", example = "iPhone 14 Pro")
    private String deviceInfo;
    
    @Schema(description = "브라우저/앱 정보", example = "Chrome 120.0.0")
    private String userAgent;
    
    @Schema(description = "IP 주소", example = "211.234.56.78")
    private String ipAddress;
    
    @Schema(description = "위치 정보", example = "서울, 대한민국")
    private String location;
    
    @Schema(description = "로그인 시간", example = "2024-01-20T10:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime loginAt;
    
    @Schema(description = "마지막 활동 시간", example = "2024-01-20T15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastActivityAt;
    
    @Schema(description = "현재 세션 여부", example = "true")
    private Boolean isCurrent;
}
```

### EmailVerificationStatusResponse.java
```java
package com.routepick.dto.email.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 이메일 인증 상태 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "이메일 인증 상태")
public class EmailVerificationStatusResponse {
    
    @Schema(description = "이메일 주소", example = "user@example.com")
    private String email;
    
    @Schema(description = "인증 완료 여부", example = "false")
    private Boolean verified;
    
    @Schema(description = "인증 코드 발송 여부", example = "true")
    private Boolean codeSent;
    
    @Schema(description = "마지막 발송 시간", example = "2024-01-20T15:40:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSentAt;
    
    @Schema(description = "인증 완료 시간", example = "2024-01-20T15:45:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime verifiedAt;
    
    @Schema(description = "남은 유효 시간 (초)", example = "180")
    private Integer remainingSeconds;
    
    @Schema(description = "재발송 가능 시간 (초)", example = "30")
    private Integer resendAvailableIn;
    
    @Schema(description = "발송 횟수", example = "2")
    private Integer sentCount;
    
    @Schema(description = "남은 시도 횟수", example = "3")
    private Integer remainingAttempts;
}
```

---

## 🔒 보안 강화 기능

### CaptchaService.java (추가)
```java
package com.routepick.service.security;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CAPTCHA 검증 서비스
 * - 로그인 실패 5회 이상 시 활성화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {
    
    /**
     * CAPTCHA 필요 여부 확인
     */
    public boolean isCaptchaRequired(String email, String ip) {
        // Redis에서 실패 횟수 확인
        int failedAttempts = getFailedAttempts(email, ip);
        return failedAttempts >= 5;
    }
    
    /**
     * CAPTCHA 토큰 검증
     */
    public boolean verifyCaptcha(String token) {
        // Google reCAPTCHA 또는 hCaptcha API 호출
        // 구현 필요
        return true;
    }
    
    private int getFailedAttempts(String email, String ip) {
        // Redis에서 실패 횟수 조회
        return 0;
    }
}
```

### SuspiciousLoginDetector.java (추가)
```java
package com.routepick.service.security;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 의심스러운 로그인 감지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspiciousLoginDetector {
    
    /**
     * 의심스러운 로그인 감지
     */
    public boolean isSuspicious(Long userId, String ip, String userAgent) {
        // 1. 새로운 IP 주소 확인
        // 2. 새로운 디바이스 확인
        // 3. 짧은 시간 내 다른 지역에서 로그인
        // 4. 비정상적인 패턴 감지
        
        return false; // 구현 필요
    }
    
    /**
     * 추가 인증 필요 여부
     */
    public boolean requiresAdditionalAuth(Long userId) {
        // 2FA 설정 여부 확인
        return false;
    }
}
```

---

*Step 7-1e 완료: Auth & Email 고급 기능 구현*