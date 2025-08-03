package com.routepick.api.controller;

import com.routepick.api.dto.auth.SignupRequest;
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

/**
 * 인증 관련 컨트롤러
 * 회원가입, 로그인, 토큰 갱신, 이메일 중복 확인, 인증 코드 발송, 인증 코드 검증을 처리합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final SimpleRateLimitService rateLimitService;

    /**
     * 회원가입 API
     * @param request 회원가입 정보
     * @param profileImage 프로필 이미지 파일 (선택사항)
     * @return 회원가입 결과
     */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestPart("userData") @Valid SignupRequest request,
                                   @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
                                   HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        
        // Rate Limiting 체크
        if (!rateLimitService.tryConsumeByIp(clientIp)) {
            return ResponseEntity.status(429).body("너무 많은 요청입니다. 잠시 후 다시 시도해주세요.");
        }
        
        if (!rateLimitService.tryConsumeByEmail(request.getEmail())) {
            return ResponseEntity.status(429).body("해당 이메일로 너무 많은 요청이 발생했습니다.");
        }
        
        if (!rateLimitService.tryConsumeGlobal()) {
            return ResponseEntity.status(429).body("서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.");
        }
        
        log.info("회원가입 요청: {}", request.getEmail());
        
        try {
            User user = authService.signup(request, profileImage);
            
            log.info("회원가입 성공: {}", user.getEmail());
            return ResponseEntity.ok(user);
            
        } catch (RequestValidationException e) {
            log.warn("회원가입 실패 - 요청 검증 실패: {}, error={}", request.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body("회원가입에 실패했습니다: " + e.getMessage());
            
        } catch (Exception e) {
            log.error("회원가입 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("회원가입에 실패했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 로그인 API
     * @param request 로그인 정보
     * @return 로그인 결과
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request) {
        
        log.info("로그인 요청: {}", request.getEmail());
        
        try {
            LoginResponse response = authService.login(request);
            
            log.info("로그인 성공: {}", request.getEmail());
            return ResponseEntity.ok(response);
            
        } catch (UserNotFoundException e) {
            log.warn("로그인 실패 - 사용자 없음: {}", request.getEmail());
            return ResponseEntity.status(401).body("이메일 또는 비밀번호가 일치하지 않습니다.");
            
        } catch (InvalidPasswordFormatException e) {
            log.warn("로그인 실패 - 비밀번호 불일치: {}", request.getEmail());
            return ResponseEntity.status(401).body("이메일 또는 비밀번호가 일치하지 않습니다.");
            
        } catch (Exception e) {
            log.error("로그인 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("로그인에 실패했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 토큰 갱신 API
     * @param request 토큰 갱신 요청
     * @return 토큰 갱신 결과
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody @Valid TokenRefreshRequest request) {
        
        log.info("토큰 갱신 요청");
        
        try {
            TokenRefreshResponse response = authService.refreshToken(request);
            
            log.info("토큰 갱신 성공");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("토큰 갱신 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body("토큰 갱신에 실패했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 이메일 중복 확인 API
     * @param request 이메일 중복 확인 요청
     * @return 중복 확인 결과
     */
    @PostMapping("/email/check")
    public ResponseEntity<EmailCheckResponse> checkEmailAvailability(@RequestBody @Valid EmailCheckRequest request) {
        log.info("이메일 중복 확인 요청: {}", request.getEmail());
        
        EmailCheckResponse response = emailVerificationService.checkEmailAvailability(request.getEmail());
        
        log.info("이메일 중복 확인 완료: email={}, available={}", request.getEmail(), response.isAvailable());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 인증 코드 발송 API
     * @param request 인증 코드 발송 요청
     * @param httpRequest HTTP 요청
     * @return 발송 결과
     */
    @PostMapping("/email/verification")
    public ResponseEntity<EmailVerificationResponse> sendVerificationCode(
            @RequestBody @Valid EmailVerificationRequest request,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        
        // Rate Limiting 체크
        if (!rateLimitService.tryConsumeByIp(clientIp)) {
            return ResponseEntity.status(429).body(EmailVerificationResponse.builder()
                .message("너무 많은 요청입니다. 잠시 후 다시 시도해주세요.")
                .build());
        }
        
        if (!rateLimitService.tryConsumeByEmail(request.getEmail())) {
            return ResponseEntity.status(429).body(EmailVerificationResponse.builder()
                .message("해당 이메일로 너무 많은 요청이 발생했습니다.")
                .build());
        }
        
        log.info("인증 코드 발송 요청: {}", request.getEmail());
        
        EmailVerificationResponse response = emailVerificationService.sendVerificationCode(request.getEmail());
        
        log.info("인증 코드 발송 완료: email={}", request.getEmail());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 인증 코드 검증 API
     * @param request 인증 코드 검증 요청
     * @return 검증 결과
     */
    @PostMapping("/email/verify")
    public ResponseEntity<VerifyCodeResponse> verifyCode(@RequestBody @Valid VerifyCodeRequest request) {
        log.info("인증 코드 검증 요청: email={}", request.getEmail());
        
        VerifyCodeResponse response = emailVerificationService.verifyCode(
            request.getEmail(), 
            request.getVerificationCode(), 
            request.getSessionToken()
        );
        
        log.info("인증 코드 검증 완료: email={}, success={}", 
            request.getEmail(), 
            response.getRegistrationToken() != null);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
} 