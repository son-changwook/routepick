package com.routepick.api.service.auth;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.routepick.api.dto.auth.SignupRequest;
import com.routepick.api.dto.auth.LoginRequest;
import com.routepick.api.dto.auth.LoginResponse;
import com.routepick.api.dto.auth.TokenRefreshRequest;
import com.routepick.api.dto.auth.TokenRefreshResponse;
import com.routepick.api.mapper.UserMapper;
import com.routepick.api.mapper.ApiTokenMapper;
import com.routepick.api.service.email.SignupSessionService;
import com.routepick.common.domain.user.User;
import com.routepick.common.domain.token.ApiToken;
import com.routepick.common.enums.UserStatus;
import com.routepick.common.enums.UserType;
import com.routepick.common.exception.customExceptions.EmailDuplicateException;
import com.routepick.common.exception.customExceptions.InvalidPasswordFormatException;
import com.routepick.common.exception.customExceptions.UserNotFoundException;
import com.routepick.common.exception.customExceptions.RequestValidationException;
import com.routepick.api.service.file.FileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final ApiTokenMapper apiTokenMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;
    private final SignupSessionService signupSessionService;

    /**
     * 회원가입 처리
     * @param request 회원가입 요청 정보
     * @param profileImage 프로필 이미지 파일 (선택사항)
     * @return 생성된 사용자 정보
     */
    @Transactional
    public User signup(SignupRequest request, MultipartFile profileImage) {

        // 1. 이메일 인증 토큰 검증
        if (!signupSessionService.validateRegistrationToken(request.getRegistrationToken(), request.getEmail())) {
            throw new RequestValidationException("유효하지 않은 이메일 인증 토큰입니다. 이메일 인증을 다시 진행해주세요.");
        }

        // 2. 이메일 중복 확인
        if (userMapper.existsByEmail(request.getEmail())) {
            throw new EmailDuplicateException("이미 존재하는 이메일입니다.");
        }

        // 3. 비밀번호 유효성 검사
        if (!isValidPassword(request.getPassword())) {
            throw new InvalidPasswordFormatException("비밀번호는 8자 이상이어야 합니다.");
        }
        
        // 4. 약관 동의 검증
        if (!request.isRequiredAgreementValid()) {
            throw new IllegalArgumentException("필수 약관에 동의해야 합니다.");
        }
    
        // 5. 비밀번호 해싱
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        
        // 6. 프로필 이미지 업로드 (있는 경우)
        String profileImageUrl = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                profileImageUrl = fileService.uploadProfileImage(profileImage);
            } catch (Exception e) {
                throw new IllegalArgumentException("프로필 이미지 업로드에 실패했습니다: " + e.getMessage());
            }
        }
        
        // 7. User 객체 생성
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(hashedPassword)
                .userName(request.getUserName())
                .phone(request.getPhone())
                .profileImageUrl(profileImageUrl)
                .userType(UserType.NORMAL)
                .userStatus(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // 8. 사용자 저장
        userMapper.insertUser(user);
        
        // 9. 약관 동의 저장
        saveUserAgreements(user.getUserId(), request);
        
        // 10. 등록 토큰 사용 처리 (세션 삭제)
        signupSessionService.consumeRegistrationToken(request.getRegistrationToken());
        
        log.info("새 사용자 등록 완료: {}", user.getEmail());
        
        return user;
    }

    /**
     * 사용자 약관 동의 저장
     * @param userId 사용자 ID
     * @param request 회원가입 요청 정보
     */
    private void saveUserAgreements(Long userId, SignupRequest request) {
        // TODO: UserAgreementMapper를 주입받아서 실제 DB 저장 로직 구현
        // 현재는 로그만 출력
        
        log.info("사용자 {} 약관 동의 저장:", userId);
        log.info("- 이용약관: {}", request.isRequiredAgreementValid() ? "동의" : "미동의");
        log.info("- 개인정보처리방침: {}", request.isRequiredAgreementValid() ? "동의" : "미동의");
        log.info("- 마케팅 수신: {}", request.isMarketingAgreed() ? "동의" : "미동의");
        log.info("- 위치정보 수집: {}", request.isLocationAgreed() ? "동의" : "미동의");
    }

    /**
     * 비밀번호 유효성 검사
     * @param password 검사할 비밀번호
     * @return 유효한 비밀번호인 경우 true, 그렇지 않으면 false
     */
    private boolean isValidPassword(String password){   
        if(password.length() < 8){
            return false;
        }
        if(!password.matches(".*[A-Z].*")){
            return false;
        }
        if(!password.matches(".*[a-z].*")){
            return false;
        }
        if(!password.matches(".*[0-9].*")){
            return false;
        }
        return true;
    }
    
    /**
     * 로그인 처리
     * @param request 로그인 요청 정보
     * @return 로그인 응답 정보
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. 사용자 조회
        User user = userMapper.findByEmail(request.getEmail())
            .orElseThrow(() -> new UserNotFoundException("존재하지 않는 사용자입니다."));
        
        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordFormatException("비밀번호가 일치하지 않습니다.");
        }
        
        // 3. 계정 상태 확인
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("비활성화된 계정입니다.");
        }
        
        // 4. 기존 토큰 만료 처리
        apiTokenMapper.revokeAllTokensByUserId(user.getUserId());
        
        // 5. 새 토큰 생성
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        
        // 6. 토큰 저장
        saveToken(user.getUserId(), accessToken, ApiToken.TokenType.ACCESS, 3600L); // 1시간
        saveToken(user.getUserId(), refreshToken, ApiToken.TokenType.REFRESH, 2592000L); // 30일
        
        // 7. 마지막 로그인 시간 업데이트
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateUser(user);
        
        // 8. 응답 생성
        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(3600L)
            .userInfo(LoginResponse.UserInfo.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .userName(user.getUsername())
                .profileImageUrl(user.getProfileImageUrl())
                .build())
            .build();
    }
    

    
    /**
     * 토큰 저장
     */
    private void saveToken(Long userId, String token, ApiToken.TokenType tokenType, Long expiresInSeconds) {
        ApiToken apiToken = ApiToken.builder()
            .userId(userId)
            .token(token)
            .tokenType(tokenType)
            .expiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds))
            .isRevoked(false)
            .build();
        
        apiTokenMapper.insertToken(apiToken);
    }
    
    /**
     * 토큰 갱신
     * @param request 토큰 갱신 요청
     * @return 토큰 갱신 응답
     */
    @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        // 1. 리프레시 토큰 검증
        if (!jwtService.validateToken(request.getRefreshToken())) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.");
        }
        
        // 2. 토큰 타입 확인
        String tokenType = jwtService.getTokenTypeFromToken(request.getRefreshToken());
        if (!"REFRESH".equals(tokenType)) {
            throw new IllegalArgumentException("리프레시 토큰이 아닙니다.");
        }
        
        // 3. 토큰 만료 확인
        if (jwtService.isTokenExpired(request.getRefreshToken())) {
            throw new IllegalArgumentException("만료된 리프레시 토큰입니다.");
        }
        
        // 4. 사용자 조회
        Long userId = jwtService.getUserIdFromToken(request.getRefreshToken());
        User user = userMapper.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("존재하지 않는 사용자입니다."));
        
        // 5. DB에서 토큰 확인
        ApiToken dbToken = apiTokenMapper.findByToken(request.getRefreshToken())
            .orElseThrow(() -> new IllegalArgumentException("DB에 존재하지 않는 토큰입니다."));
        
        if (dbToken.getIsRevoked()) {
            throw new IllegalArgumentException("이미 만료된 토큰입니다.");
        }
        
        // 6. 새 토큰 생성
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        
        // 7. 기존 토큰 만료 처리
        apiTokenMapper.revokeToken(dbToken.getTokenId());
        
        // 8. 새 토큰 저장
        saveToken(user.getUserId(), newAccessToken, ApiToken.TokenType.ACCESS, 3600L);
        saveToken(user.getUserId(), newRefreshToken, ApiToken.TokenType.REFRESH, 2592000L);
        
        // 9. 응답 생성
        return TokenRefreshResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .tokenType("Bearer")
            .expiresIn(3600L)
            .build();
    }
    
} 