package com.routepick.api.service.email;

import com.routepick.api.dto.email.EmailCheckRequest;
import com.routepick.api.dto.email.EmailCheckResponse;
import com.routepick.api.dto.email.EmailVerificationRequest;
import com.routepick.api.dto.email.EmailVerificationResponse;
import com.routepick.api.dto.email.VerifyCodeRequest;
import com.routepick.api.dto.email.VerifyCodeResponse;
import com.routepick.api.mapper.UserMapper;
import com.routepick.api.service.auth.JwtService;
import com.routepick.common.domain.session.SignupSession;
import com.routepick.common.exception.customExceptions.DuplicateResourceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 이메일 인증 서비스
 * 회원가입 전 이메일 인증을 처리합니다.
 */
@Slf4j
@Service
public class EmailVerificationService {

    private final UserMapper userMapper;
    private final EmailService emailService;
    private final SignupSessionService sessionService;
    private final JwtService jwtService;

    public EmailVerificationService(UserMapper userMapper, EmailService emailService, SignupSessionService sessionService, JwtService jwtService){
        this.userMapper = userMapper;
        this.emailService = emailService;
        this.sessionService = sessionService;
        this.jwtService = jwtService;
    }

/**
 * 이메일 중복검사
 * @param email 이메일
 * @return 중복검사 결과
 */
public EmailCheckResponse checkEmailAvailability(String email) {
    boolean emailExists = userMapper.existsByEmail(email);
    return EmailCheckResponse.builder()
    .available(!emailExists)
    .message(emailExists ? "이미 사용중인 이메일입니다." : "사용 가능한 이메일입니다.")
    .verificationRequired(!emailExists)
    .build();
}

/**
 * 인증 코드 발송
 * @param email 이메일
 * @return 발송 결과
 */
public EmailVerificationResponse sendVerificationCode(String email) {
    log.info("인증 코드 발송 요청: {}", email);
    
    try {
        // 1. 이메일 중복 재확인
        if (userMapper.existsByEmail(email)) {
            log.warn("이미 사용 중인 이메일로 인증 코드 발송 시도: {}", email);
            return EmailVerificationResponse.builder()
                .message("이미 사용 중인 이메일입니다.")
                .build();
        }
        
        // 2. 인증 코드 생성
        String verificationCode = emailService.generateVerificationCode();
        log.debug("생성된 인증 코드: {}", verificationCode);
        
        // 3. 세션 생성
        String sessionToken = sessionService.createSession(email);
        log.debug("생성된 세션 토큰: {}", sessionToken);
        
        // 4. 세션에 인증 코드 저장
        sessionService.setVerificationCode(sessionToken, verificationCode);
        
        // 5. 이메일 발송
        emailService.sendVerificationEmail(email, verificationCode);
        log.info("인증 코드 이메일 발송 완료: {}", email);
        
        // 6. 응답 생성
        return EmailVerificationResponse.builder()
            .message("인증 코드가 발송되었습니다.")
            .verificationCode(verificationCode) // 개발용, 실제로는 제거
            .sessionToken(sessionToken)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
            
    } catch (Exception e) {
        log.error("인증 코드 발송 실패: {}", e.getMessage(), e);
        return EmailVerificationResponse.builder()
            .message("인증 코드 발송에 실패했습니다. 잠시 후 다시 시도해주세요.")
            .build();
    }
}

/**
 * 인증 코드 검증
 * @param email 이메일
 * @param verificationCode 인증 코드
 * @param sessionToken 세션 토큰
 * @return 검증 결과
 */
public VerifyCodeResponse verifyCode(String email, String verificationCode, String sessionToken) {
    log.info("인증 코드 검증 요청: email={}, sessionToken={}", email, sessionToken);
    
    try {
        // 1. 세션 확인
        var sessionOpt = sessionService.getSession(sessionToken);
        if (sessionOpt.isEmpty()) {
            log.warn("유효하지 않은 세션: sessionToken={}", sessionToken);
            return VerifyCodeResponse.builder()
                .message("유효하지 않은 세션입니다.")
                .build();
        }
        
        SignupSession session = sessionOpt.get();
        
        // 2. 이메일 일치 확인
        if (!email.equals(session.getEmail())) {
            log.warn("이메일 불일치: sessionEmail={}, requestEmail={}", session.getEmail(), email);
            return VerifyCodeResponse.builder()
                .message("이메일이 일치하지 않습니다.")
                .build();
        }
        
        // 3. 인증 코드 확인
        if (!verificationCode.equals(session.getVerificationCode())) {
            log.warn("인증 코드 불일치: sessionCode={}, requestCode={}", session.getVerificationCode(), verificationCode);
            return VerifyCodeResponse.builder()
                .message("인증 코드가 일치하지 않습니다.")
                .build();
        }
        
        // 4. 세션 인증 완료 처리
        sessionService.markSessionVerified(sessionToken);
        
        // 5. 회원가입 토큰 생성 및 세션에 저장
        String registrationToken = generateRegistrationToken(email);
        sessionService.setRegistrationToken(sessionToken, registrationToken);
        
        // 6. 응답 생성
        return VerifyCodeResponse.builder()
            .message("이메일 인증이 완료되었습니다.")
            .verifiedEmail(email)
            .registrationToken(registrationToken)
            .tokenExpiresAt(LocalDateTime.now().plusMinutes(10)) // 10분 후 만료
            .build();
            
    } catch (Exception e) {
        log.error("인증 코드 검증 실패: {}", e.getMessage(), e);
        return VerifyCodeResponse.builder()
            .message("인증 코드 검증에 실패했습니다.")
            .build();
    }
}

/**
 * 회원가입 토큰 생성 (JWT 기반 보안 토큰)
 * @param email 이메일
 * @return JWT 기반 회원가입 토큰
 */
private String generateRegistrationToken(String email) {
    try {
        // JWT 토큰에 포함할 정보
        Map<String, Object> claims = Map.of(
            "email", email,
            "tokenType", "REGISTRATION",
            "purpose", "signup"
        );
        
        // JWT 토큰 생성 (10분 만료)
        return jwtService.generateToken(claims, 600L); // 10분 = 600초
        
    } catch (Exception e) {
        log.error("JWT 회원가입 토큰 생성 실패: email={}, error={}", email, e.getMessage(), e);
        // JWT 생성 실패 시 기존 방식으로 폴백
        return "REG_" + email.hashCode() + "_" + System.currentTimeMillis();
    }
}
}
