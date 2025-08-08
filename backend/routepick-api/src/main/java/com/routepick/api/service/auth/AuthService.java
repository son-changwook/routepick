package com.routepick.api.service.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.routepick.api.dto.auth.SignupRequest;
import com.routepick.api.dto.auth.SignupResponse;
import com.routepick.api.dto.auth.LoginRequest;
import com.routepick.api.dto.auth.LoginResponse;
import com.routepick.api.dto.auth.TokenRefreshRequest;
import com.routepick.api.dto.auth.TokenRefreshResponse;
import com.routepick.api.dto.auth.NickNameCheckRequest;
import com.routepick.api.dto.auth.NickNameCheckResponse;
import com.routepick.api.mapper.UserMapper;
import com.routepick.api.mapper.UserDetailsMapper;
import com.routepick.api.mapper.ApiTokenMapper;
import com.routepick.api.service.email.RedisSignupSessionService;
import com.routepick.api.service.validation.ValidationService;
import com.routepick.common.exception.AuthenticationException;
import com.routepick.common.exception.FileException;
import com.routepick.common.exception.ServiceException;
import com.routepick.api.util.InputSanitizer;
import com.routepick.common.domain.user.User;
import com.routepick.common.domain.user.UserDetails;
import com.routepick.common.domain.token.ApiToken;
import com.routepick.common.enums.UserStatus;
import com.routepick.common.enums.UserType;
import com.routepick.common.exception.customExceptions.EmailDuplicateException;
import com.routepick.common.exception.customExceptions.InvalidPasswordFormatException;
import com.routepick.common.exception.customExceptions.UserNotFoundException;
import com.routepick.common.exception.customExceptions.RequestValidationException;
import com.routepick.common.exception.customExceptions.DuplicateResourceException;
import com.routepick.api.service.file.FileService;
import com.routepick.api.service.auth.TokenBlacklistService;
import com.routepick.api.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserDetailsMapper userDetailsMapper;
    private final ApiTokenMapper apiTokenMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;
    // private final SignupSessionService signupSessionService; // Redis로 대체됨
    private final RedisSignupSessionService redisSignupSessionService;
    private final ValidationService validationService;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 닉네임 중복 검사
     * @param request 닉네임 중복 검사 요청 정보
     * @return 닉네임 중복 검사 응답 정보
     */
    public NickNameCheckResponse checkNickNameAvailability(NickNameCheckRequest request) {
        // 입력값 정제
        String sanitizedNickName = InputSanitizer.sanitizeInput(request.getNickName());
        
        // 닉네임 중복 확인
        boolean isDuplicate = userDetailsMapper.existsByNickName(sanitizedNickName);
        
        if (isDuplicate) {
            log.info("닉네임 중복 확인 - 중복됨: {}", sanitizedNickName);
            return NickNameCheckResponse.unavailable(sanitizedNickName);
        } else {
            log.info("닉네임 중복 확인 - 사용 가능: {}", sanitizedNickName);
            return NickNameCheckResponse.available(sanitizedNickName);
        }
    }

    /**
     * 회원가입 처리
     * @param request 회원가입 요청 정보
     * @param profileImage 프로필 이미지 파일 (선택사항)
     * @return 회원가입 응답 정보
     */
    @Transactional
    public SignupResponse signup(SignupRequest request, MultipartFile profileImage) {

        // 1. 전문적인 검증 서비스를 통한 종합 검증
        validationService.validateSignupRequest(request);

        // 2. 입력 데이터 정제 (검증 통과 후)
        String sanitizedEmail = InputSanitizer.sanitizeEmail(request.getEmail());
        String sanitizedUserName = InputSanitizer.sanitizeInput(request.getUserName());
        String sanitizedNickName = InputSanitizer.sanitizeInput(request.getNickName());
        String sanitizedPhone = InputSanitizer.sanitizeInput(request.getPhone());
        String sanitizedAddress = InputSanitizer.sanitizeInput(request.getAddress());
        String sanitizedDetailAddress = InputSanitizer.sanitizeInput(request.getDetailAddress());
        String sanitizedEmergencyContact = InputSanitizer.sanitizeInput(request.getEmergencyContact());

        // 3. 이메일 인증 토큰 검증 (JWT 기반)
        if (!validateRegistrationToken(request.getRegistrationToken(), sanitizedEmail)) {
            throw new RequestValidationException("유효하지 않은 이메일 인증 토큰입니다. 이메일 인증을 다시 진행해주세요.");
        }

        // 4. 이메일 중복 확인
        if (userMapper.existsByEmail(sanitizedEmail)) {
            throw new EmailDuplicateException("이미 존재하는 이메일입니다.");
        }
        
        // 5. 닉네임 중복 확인
        if (userDetailsMapper.existsByNickName(sanitizedNickName)) {
            throw new DuplicateResourceException("이미 사용 중인 닉네임입니다.");
        }
    
        // 6. 비밀번호 해싱
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        
        // 7. 프로필 이미지 업로드 (있는 경우)
        String profileImageUrl = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                profileImageUrl = fileService.uploadProfileImage(profileImage);
            } catch (Exception e) {
                throw FileException.fileUploadFailed(e.getMessage(), e);
            }
        }
        
        // 8. User 객체 생성 (정제된 데이터 사용)
        User user = User.builder()
                .email(sanitizedEmail)
                .passwordHash(hashedPassword)
                .userName(sanitizedUserName)
                .phone(sanitizedPhone)
                .profileImageUrl(profileImageUrl)
                .birthDate(request.getBirthDate() != null ? LocalDate.parse(request.getBirthDate()) : null)
                .address(sanitizedAddress)
                .detailAddress(sanitizedDetailAddress)
                .emergencyContact(sanitizedEmergencyContact)
                .userType(UserType.NORMAL)
                .userStatus(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // 9. 사용자 저장
        userMapper.insertUser(user);
        
        // 10. UserDetails 객체 생성 및 저장 (닉네임 포함)
        UserDetails userDetails = UserDetails.builder()
                .userId(user.getUserId())
                .nickName(sanitizedNickName)
                .followingCount(0)
                .followerCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        userDetailsMapper.insertUserDetails(userDetails);
        
        // 11. 약관 동의 저장
        saveUserAgreements(user.getUserId(), request);
        
        // 12. 등록 토큰 사용 처리 (Redis 세션 삭제)
        redisSignupSessionService.consumeRegistrationToken(request.getRegistrationToken());
        
        log.info("새 사용자 등록 완료: email={}, userName={}, nickName={}", 
                sanitizedEmail, sanitizedUserName, sanitizedNickName);
        
        // 13. SignupResponse 생성 및 반환
        return SignupResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .nickName(userDetails.getNickName())
                .profileImageUrl(user.getProfileImageUrl())
                .message("회원가입이 성공적으로 완료되었습니다.")
                .build();
    }

    /**
     * JWT 기반 회원가입 토큰 검증
     * @param token 회원가입 토큰
     * @param email 이메일
     * @return 검증 성공 여부
     */
    private boolean validateRegistrationToken(String token, String email) {
        try {
            // JWT 토큰 검증
            if (!jwtService.validateToken(token)) {
                log.warn("유효하지 않은 JWT 회원가입 토큰: {}", token.substring(0, 10) + "***");
                return false;
            }
            
            // 토큰 타입 확인
            String tokenType = jwtService.getTokenTypeFromToken(token);
            if (!"REGISTRATION".equals(tokenType)) {
                log.warn("잘못된 토큰 타입: {}", tokenType);
                return false;
            }
            
            // 이메일 일치 확인
            String tokenEmail = jwtService.getEmailFromToken(token);
            if (!email.equals(tokenEmail)) {
                log.warn("토큰 이메일 불일치: tokenEmail={}, requestEmail={}", tokenEmail, email);
                return false;
            }
            
            // 토큰 만료 확인
            if (jwtService.isTokenExpired(token)) {
                log.warn("만료된 회원가입 토큰: {}", token.substring(0, 10) + "***");
                return false;
            }
            
            log.info("JWT 회원가입 토큰 검증 성공: email={}", email);
            return true;
            
        } catch (Exception e) {
            log.error("JWT 회원가입 토큰 검증 실패: token={}, email={}, error={}", 
                token.substring(0, 10) + "***", email, e.getMessage(), e);
            return false;
        }
    }


    /**
     * 사용자 약관 동의 저장
     * @param userId 사용자 ID
     * @param request 회원가입 요청 정보
     */
    private void saveUserAgreements(Long userId, SignupRequest request) {
        // 향후 구현 예정: UserAgreementMapper를 주입받아서 실제 DB 저장 로직 구현
        // 현재는 로그만 출력하지만, 향후 실제 DB 저장 로직으로 교체 예정
        
        log.info("사용자 {} 약관 동의 저장:", userId);
        log.info("- 이용약관: {}", request.isRequiredAgreementValid() ? "동의" : "미동의");
        log.info("- 개인정보처리방침: {}", request.isRequiredAgreementValid() ? "동의" : "미동의");
        log.info("- 마케팅 수신: {}", request.isMarketingAgreed() ? "동의" : "미동의");
        log.info("- 위치정보 수집: {}", request.isLocationAgreed() ? "동의" : "미동의");
        
        // 향후 구현 예정:
        // 1. UserAgreementMapper 주입
        // 2. 각 약관별 동의 정보를 DB에 저장
        // 3. 트랜잭션 처리
        // 4. 예외 처리
    }


    
    /**
     * 로그인 처리
     * @param request 로그인 요청 정보
     * @return 로그인 응답 정보
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. 입력값 검증
        validationService.validateEmail(request.getEmail());
        
        // 2. 사용자 조회
        User user = userMapper.findByEmail(request.getEmail())
            .orElseThrow(() -> new UserNotFoundException("존재하지 않는 사용자입니다."));
        
        // 3. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordFormatException("비밀번호가 일치하지 않습니다.");
        }
        
        // 4. 계정 상태 확인
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            switch (user.getUserStatus()) {
                case INACTIVE:
                    throw AuthenticationException.accountDisabled();
                case SUSPENDED:
                    throw AuthenticationException.accountSuspended();
                case DELETED:
                    throw AuthenticationException.accountDeleted();
                default:
                    throw AuthenticationException.accountDisabled();
            }
        }
        
        // 5. 사용자 상세 정보 조회 (닉네임 포함)
        var userDetailsOpt = userDetailsMapper.findByUserId(user.getUserId());
        String nickName = userDetailsOpt.map(com.routepick.common.domain.user.UserDetails::getNickName).orElse(null);
        
        // 6. CustomUserDetails 생성
        CustomUserDetails customUserDetails = new CustomUserDetails(
            user.getUserId(),
            user.getEmail(),
            user.getUserName(), // 사용자 실명
            nickName, // 사용자 닉네임
            user.getProfileImageUrl(),
            user.getPassword(),
            user.isEnabled(),
            user.isAccountNonExpired(),
            user.isCredentialsNonExpired(),
            user.isAccountNonLocked(),
            user.getAuthorities()
        );
        
        // 7. 기존 토큰 만료 처리
        apiTokenMapper.revokeAllTokensByUserId(user.getUserId());
        
        // 8. 새 토큰 생성 (CustomUserDetails 사용하여 닉네임 포함)
        String accessToken = jwtService.generateAccessToken(customUserDetails);
        String refreshToken = jwtService.generateRefreshToken(customUserDetails);
        
        // 9. 토큰 저장
        saveToken(user.getUserId(), accessToken, ApiToken.TokenType.ACCESS, 3600L); // 1시간
        saveToken(user.getUserId(), refreshToken, ApiToken.TokenType.REFRESH, 2592000L); // 30일
        
        // 10. 마지막 로그인 시간 업데이트
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateUser(user);
        
        // 11. 응답 생성
        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .userInfo(LoginResponse.UserInfo.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .userName(user.getUserName()) // 사용자 실명
                .nickName(nickName) // 사용자 닉네임
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
            throw AuthenticationException.invalidRefreshToken();
        }
        
        // 2. 토큰 타입 확인
        String tokenType = jwtService.getTokenTypeFromToken(request.getRefreshToken());
        if (!"REFRESH".equals(tokenType)) {
            throw AuthenticationException.tokenTypeMismatch();
        }
        
        // 3. 토큰 만료 확인
        if (jwtService.isTokenExpired(request.getRefreshToken())) {
            throw AuthenticationException.expiredToken();
        }
        
        // 4. 사용자 조회
        Long userId = jwtService.getUserIdFromToken(request.getRefreshToken());
        User user = userMapper.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("존재하지 않는 사용자입니다."));
        
        // 5. DB에서 토큰 확인
        ApiToken dbToken = apiTokenMapper.findByToken(request.getRefreshToken())
            .orElseThrow(() -> AuthenticationException.invalidToken());
        
        if (dbToken.getIsRevoked()) {
            throw AuthenticationException.expiredToken();
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
    
    /**
     * 로그아웃 처리
     * @param token 무효화할 액세스 토큰
     */
    @Transactional
    public void logout(String token) {
        log.info("로그아웃 처리 시작");
        
        try {
            // 1. 토큰 유효성 검증
            if (!jwtService.validateToken(token)) {
                log.warn("유효하지 않은 토큰으로 로그아웃 시도: {}", token.substring(0, 10) + "***");
                throw AuthenticationException.invalidToken();
            }
            
            // 2. 토큰 타입 확인 (액세스 토큰만 허용)
            String tokenType = jwtService.getTokenTypeFromToken(token);
            if (!"ACCESS".equals(tokenType)) {
                log.warn("잘못된 토큰 타입으로 로그아웃 시도: {}", tokenType);
                throw AuthenticationException.tokenTypeMismatch();
            }
            
            // 3. 사용자 ID 추출
            Long userId = jwtService.getUserIdFromToken(token);
            
            // 4. DB에서 토큰 확인
            ApiToken dbToken = apiTokenMapper.findByToken(token)
                .orElseThrow(() -> AuthenticationException.invalidToken());
            
            // 5. 토큰이 이미 무효화되었는지 확인
            if (dbToken.getIsRevoked()) {
                log.warn("이미 무효화된 토큰으로 로그아웃 시도: {}", dbToken.getTokenId());
                throw AuthenticationException.expiredToken();
            }
            
            // 6. 토큰 무효화 (DB에서 revoke 처리)
            apiTokenMapper.revokeToken(dbToken.getTokenId());
            
            // 7. 토큰을 블랙리스트에 추가 (Redis)
            tokenBlacklistService.addToBlacklist(token, 1); // 1시간 동안 블랙리스트 유지
            
            // 8. 해당 사용자의 모든 토큰 무효화 (선택사항)
            // apiTokenMapper.revokeAllUserTokens(userId);
            
            log.info("로그아웃 처리 완료: userId={}, tokenId={}", userId, dbToken.getTokenId());
            
        } catch (Exception e) {
            log.error("로그아웃 처리 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        }
    }
    
} 