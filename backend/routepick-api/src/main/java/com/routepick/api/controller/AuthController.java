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
import com.routepick.api.util.InputSanitizer;
import com.routepick.api.util.RateLimitHelper;
import com.routepick.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 인증 관련 컨트롤러
 * 표준화된 ApiResponse 구조와 글로벌 예외 처리를 적용했습니다.
 */
@Slf4j
@Tag(name = "인증", description = "인증 관련 API (회원가입, 로그인, 토큰 갱신, 이메일 중복 확인, 인증 코드 발송, 인증 코드 검증)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final RateLimitHelper rateLimitHelper;

    @Operation(
        summary = "회원가입",
        description = "새로운 사용자 계정을 생성합니다. 이메일 인증이 완료된 후 가능합니다. 프로필 이미지는 선택사항입니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "회원가입 성공",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (입력값 오류, Rate Limit 초과 등)",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 존재하는 이메일",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestPart("userData") @Valid SignupRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
            HttpServletRequest httpRequest) {
        
        log.info("회원가입 요청 시작");
        
        // Rate Limit 체크 (IP + 이메일 + 엔드포인트)
        rateLimitHelper.checkAuthRateLimit(httpRequest, request.getEmail());
        
        // 입력값 정제
        String sanitizedEmail = InputSanitizer.sanitizeEmail(request.getEmail());
        String sanitizedUsername = InputSanitizer.sanitizeInput(request.getUserName());
        
        // Request 객체 업데이트
        request.setEmail(sanitizedEmail);
        request.setUserName(sanitizedUsername);
        
        // 회원가입 처리 (프로필 이미지 포함)
        SignupResponse response = authService.signup(request, profileImage);
        
        log.info("회원가입 완료: userId={}", response.getUserId());
        return ResponseEntity.ok(ApiResponse.success("회원가입이 완료되었습니다.", response));
    }

    @Operation(
        summary = "로그인",
        description = "이메일과 비밀번호로 로그인하여 액세스 토큰과 리프레시 토큰을 발급받습니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (입력값 오류, Rate Limit 초과 등)",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 (잘못된 이메일 또는 비밀번호)",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("로그인 요청 시작");
        
        // Rate Limit 체크
        rateLimitHelper.checkAuthRateLimit(httpRequest, request.getEmail());
        
        // 입력값 정제
        String sanitizedEmail = InputSanitizer.sanitizeEmail(request.getEmail());
        request.setEmail(sanitizedEmail);
        
        // 로그인 처리
        LoginResponse response = authService.login(request);
        
        log.info("로그인 완료: userId={}", response.getUserInfo().getUserId());
        return ResponseEntity.ok(ApiResponse.success("로그인이 완료되었습니다.", response));
    }

    @Operation(
        summary = "토큰 갱신",
        description = "리프레시 토큰을 사용하여 새로운 액세스 토큰을 발급받습니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "토큰 갱신 성공",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 또는 유효하지 않은 리프레시 토큰",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "만료되거나 유효하지 않은 리프레시 토큰",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(
            @Valid @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("토큰 갱신 요청 시작");
        
        // Rate Limit 체크 (IP 기반)
        rateLimitHelper.checkIpRateLimit(httpRequest);
        
        // 토큰 갱신 처리
        TokenRefreshResponse response = authService.refreshToken(request);
        
        log.info("토큰 갱신 완료");
        return ResponseEntity.ok(ApiResponse.success("토큰이 갱신되었습니다.", response));
    }

    @Operation(
        summary = "이메일 중복 확인",
        description = "회원가입 전 이메일 주소의 중복 여부를 확인합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "이메일 중복 확인 완료",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (잘못된 이메일 형식, Rate Limit 초과 등)",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 사용 중인 이메일",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/check-email")
    public ResponseEntity<ApiResponse<EmailCheckResponse>> checkEmail(
            @Valid @RequestBody EmailCheckRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("이메일 중복 확인 요청 시작");
        
        // Rate Limit 체크
        rateLimitHelper.checkCombinedRateLimit(httpRequest, request.getEmail());
        
        // 입력값 정제
        String sanitizedEmail = InputSanitizer.sanitizeEmail(request.getEmail());
        
        // 이메일 중복 확인
        EmailCheckResponse response = emailVerificationService.checkEmailAvailability(sanitizedEmail);
        
        String message = response.isAvailable() ? 
            "사용 가능한 이메일입니다." : "이미 사용 중인 이메일입니다.";
        
        log.info("이메일 중복 확인 완료: email={}, available={}", 
            sanitizedEmail, response.isAvailable());
        
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @Operation(
        summary = "이메일 인증 코드 발송",
        description = "회원가입을 위한 이메일 인증 코드를 발송합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "인증 코드 발송 완료",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (잘못된 이메일 형식, Rate Limit 초과 등)",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 사용 중인 이메일",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/send-verification-code")
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> sendVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("이메일 인증 코드 발송 요청 시작");
        
        // Rate Limit 체크 (더 엄격한 제한)
        rateLimitHelper.checkEmailRateLimit(request.getEmail());
        rateLimitHelper.checkEndpointRateLimit(httpRequest, 
            rateLimitHelper.getClientIpAddress(httpRequest));
        
        // 입력값 정제
        String sanitizedEmail = InputSanitizer.sanitizeEmail(request.getEmail());
        
        // 인증 코드 발송
        EmailVerificationResponse response = emailVerificationService
            .sendVerificationCode(sanitizedEmail);
        
        log.info("이메일 인증 코드 발송 완료: sessionToken={}", response.getSessionToken() != null ? response.getSessionToken() : "N/A");
        return ResponseEntity.ok(ApiResponse.success("인증 코드가 발송되었습니다.", response));
    }

    @Operation(
        summary = "이메일 인증 코드 검증",
        description = "발송된 인증 코드를 검증하여 회원가입을 위한 등록 토큰을 발급받습니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "인증 코드 검증 완료",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (잘못된 인증 코드, 만료된 세션 등)",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "인증 세션을 찾을 수 없음",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponse<VerifyCodeResponse>> verifyCode(
            @Valid @RequestBody VerifyCodeRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("인증 코드 검증 요청 시작");
        
        // Rate Limit 체크 (IP 기반)
        rateLimitHelper.checkIpRateLimit(httpRequest);
        
        // 인증 코드 검증
        VerifyCodeResponse response = emailVerificationService
            .verifyCode(request.getEmail(), request.getVerificationCode(), request.getSessionToken());
        
        log.info("인증 코드 검증 완료: registrationToken={}", 
            response.getRegistrationToken().substring(0, 10) + "***");
        
        return ResponseEntity.ok(ApiResponse.success("인증이 완료되었습니다.", response));
    }

    @Operation(
        summary = "로그아웃",
        description = "현재 액세스 토큰을 무효화합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "로그아웃 완료",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "유효하지 않은 토큰",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(description = "Authorization 헤더의 Bearer 토큰")
            @RequestHeader("Authorization") String authorizationHeader,
            HttpServletRequest httpRequest) {
        
        log.info("로그아웃 요청 시작");
        
        // Rate Limit 체크
        rateLimitHelper.checkIpRateLimit(httpRequest);
        
        // Bearer 토큰 추출
        String token = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring(7);
        }
        
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Authorization 헤더가 필요합니다."));
        }
        
        // 로그아웃 처리 (토큰 무효화)
        authService.logout(token);
        
        log.info("로그아웃 완료");
        return ResponseEntity.ok(ApiResponse.success("로그아웃이 완료되었습니다."));
    }
}