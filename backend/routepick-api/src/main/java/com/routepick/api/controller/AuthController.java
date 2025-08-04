package com.routepick.api.controller;

import com.routepick.api.dto.auth.SignupRequest;
import com.routepick.api.dto.auth.SignupResponse;
import com.routepick.api.dto.auth.LoginRequest;
import com.routepick.api.dto.auth.LoginResponse;
import com.routepick.api.dto.auth.TokenRefreshRequest;
import com.routepick.api.dto.auth.TokenRefreshResponse;
import com.routepick.api.dto.email.EmailCheckRequest;
import com.routepick.api.dto.email.EmailCheckResponse;
import com.routepick.api.dto.email.EmailVerificationRequest;
import com.routepick.api.dto.email.EmailVerificationResponse;
import com.routepick.api.dto.email.VerifyCodeRequest;
import com.routepick.api.dto.email.VerifyCodeResponse;
import com.routepick.api.service.auth.AuthService;
import com.routepick.api.service.email.EmailVerificationService;
import com.routepick.api.service.security.SimpleRateLimitService;
import com.routepick.api.util.InputSanitizer;
import com.routepick.common.domain.user.User;
import com.routepick.common.exception.customExceptions.UserNotFoundException;
import com.routepick.common.exception.customExceptions.InvalidPasswordFormatException;
import com.routepick.common.exception.customExceptions.RequestValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

@Slf4j
@Tag(name = "인증", description = "인증 관련 API (회원가입, 로그인, 토큰 갱신, 이메일 중복 확인, 인증 코드 발송, 인증 코드 검증)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final SimpleRateLimitService rateLimitService;

    @Operation(
        summary = "회원가입",
        description = "이메일 인증 토큰이 필요한 회원가입 API입니다." + 
        "프로필 이미지는 선택사항이며, 필수 약관에 동의해야 합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "회원가입 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SignupResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "성공 예시",
                        value = """
                        {
                          "userId": 123,
                          "email": "user@example.com",
                          "displayName": "climber123",
                          "profileImageUrl": "https://example.com/profile.jpg",
                          "message": "회원가입이 성공적으로 완료되었습니다."
                        }
                        """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 검증 실패 (이메일 형식 불일치, 비밀번호 형식 불일치, 필수 약관 동의 여부 불일치)"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "이미 존재하는 이메일"
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate Limit 초과 (이메일 발송 또는 회원가입 요청 너무 많음)" 
        )
    })
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
        @Parameter(
            description = "회원가입 정보 (JSON 형식)",
            required = true
        )
        @RequestPart("userData") @Valid SignupRequest request,
        @Parameter(
            description = "프로필 이미지 (선택사항)",
            required = false
        )
        @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
        HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        
        // Rate Limiting 체크
        if (!rateLimitService.tryConsumeByIp(clientIp)) {
            throw new RuntimeException("너무 많은 요청입니다. 잠시 후 다시 시도해주세요.");
        }
        
        if (!rateLimitService.tryConsumeByEmail(request.getEmail())) {
            throw new RuntimeException("해당 이메일로 너무 많은 요청이 발생했습니다.");
        }
        
        if (!rateLimitService.tryConsumeGlobal()) {
            throw new RuntimeException("서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.");
        }
        
        // 입력 데이터 정제
        String sanitizedEmail = InputSanitizer.sanitizeEmail(request.getEmail());
        log.info("회원가입 요청: {}", sanitizedEmail);
        
        try {
            SignupResponse response = authService.signup(request, profileImage);
            
            log.info("회원가입 성공: {}", response.getEmail());
            return ResponseEntity.ok(response);
            
        } catch (RequestValidationException e) {
            log.warn("회원가입 실패 - 요청 검증 실패: {}, error={}", sanitizedEmail, e.getMessage());
            throw e;
            
        } catch (Exception e) {
            log.error("회원가입 실패: {}", e.getMessage(), e);
            throw new RuntimeException("회원가입에 실패했습니다: " + e.getMessage());
        }
    }
    

    
    @Operation(
        summary = "로그인",
        description = "이메일과 비밀번호로 로그인하여 JWT 액세스 토큰과 리프레시 토큰을 발급받습니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoginResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "이메일 또는 비밀번호가 일치하지 않습니다."
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 형식 오류"
        )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        
        // 입력 데이터 정제
        String sanitizedEmail = InputSanitizer.sanitizeEmail(request.getEmail());
        log.info("로그인 요청: {}", sanitizedEmail);
        
        try {
            LoginResponse response = authService.login(request);
            log.info("로그인 성공: {}", sanitizedEmail);
            return ResponseEntity.ok(response);
            
        } catch (UserNotFoundException e) {
            log.warn("로그인 실패 - 사용자를 찾을 수 없음: {}", sanitizedEmail);
            throw e;
            
        } catch (Exception e) {
            log.error("로그인 실패: {}", e.getMessage(), e);
            throw new RuntimeException("로그인에 실패했습니다: " + e.getMessage());
        }
    }

    @Operation(
        summary = "토큰 갱신",
        description = "리프레시 토큰을 사용하여 새로운 액세스 토큰을 발급받습니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "토큰 갱신 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TokenRefreshResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "유효하지 않은 리프레시 토큰입니다."
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 형식 오류"
        )
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refreshToken(@RequestBody @Valid TokenRefreshRequest request) {
        
        log.info("토큰 갱신 요청");
        
        try {
            TokenRefreshResponse response = authService.refreshToken(request);
            log.info("토큰 갱신 성공");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("토큰 갱신 실패: {}", e.getMessage(), e);
            throw new RuntimeException("토큰 갱신에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Operation(
        summary = "이메일 중복 확인",
        description = "회원가입 전 이메일 중복 여부를 확인합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "중복 확인 완료",
            content = @Content(
                mediaType = "application/json"
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 형식 오류"
        )
    })
    @PostMapping("/email/check")
    public ResponseEntity<EmailCheckResponse> checkEmailAvailability(@RequestBody @Valid EmailCheckRequest request) {
        
        // 입력 데이터 정제
        String sanitizedEmail = InputSanitizer.sanitizeEmail(request.getEmail());
        log.info("이메일 중복 확인 요청: {}", sanitizedEmail);
        
        EmailCheckResponse response = emailVerificationService.checkEmailAvailability(sanitizedEmail);
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "이메일 인증 코드 발송",
        description = "회원가입을 위한 이메일 인증 코드를 발송합니다. 5분 후 만료됩니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "인증 코드 발송 성공",
            content = @Content(
                mediaType = "application/json"
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 형식 오류"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "이미 사용 중인 이메일입니다."
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate Limit 초과 (너무 많은 요청)"
        )
    })
    @PostMapping("/email/verification")
    public ResponseEntity<EmailVerificationResponse> sendVerificationCode(
            @RequestBody @Valid EmailVerificationRequest request,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        
        // Rate Limiting 체크
        if (!rateLimitService.tryConsumeByIp(clientIp)) {
            throw new RuntimeException("너무 많은 요청입니다. 잠시 후 다시 시도해주세요.");
        }
        
        if (!rateLimitService.tryConsumeByEmail(request.getEmail())) {
            throw new RuntimeException("해당 이메일로 너무 많은 요청이 발생했습니다.");
        }
        
        // 입력 데이터 정제
        String sanitizedEmail = InputSanitizer.sanitizeEmail(request.getEmail());
        log.info("이메일 인증 코드 발송 요청: {}", sanitizedEmail);
        
        EmailVerificationResponse response = emailVerificationService.sendVerificationCode(sanitizedEmail);
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "이메일 인증 코드 검증",
        description = "발송된 이메일 인증 코드를 검증하고 회원가입 토큰을 발급합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "인증 코드 검증 성공",
            content = @Content(
                mediaType = "application/json"
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 형식 오류"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "유효하지 않은 인증 코드 또는 만료된 세션"
        )
    })
    @PostMapping("/email/verify")
    public ResponseEntity<VerifyCodeResponse> verifyCode(@RequestBody @Valid VerifyCodeRequest request) {
        
        log.info("이메일 인증 코드 검증 요청");
        
        VerifyCodeResponse response = emailVerificationService.verifyCode(
            request.getEmail(), 
            request.getVerificationCode(), 
            request.getSessionToken()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0];
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
} 