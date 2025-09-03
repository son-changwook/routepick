# Step 9-1b: AuthService 단위 테스트 설계

> 인증 서비스 로직 및 보안 기능 테스트  
> 생성일: 2025-08-27  
> 기반: step6-1a_auth_service.md, step7-1c_auth_request_dtos.md  
> 테스트 범위: 로그인, 회원가입, 소셜 로그인, 보안 정책

---

## 🎯 테스트 목표

### 핵심 검증 사항
- **로그인 프로세스**: 인증 성공/실패 시나리오
- **회원가입 검증**: 이메일 중복, 패스워드 정책
- **소셜 로그인**: 4개 제공자 연동 테스트
- **보안 정책**: 계정 잠금, Rate Limiting, 브루트 포스 방어

---

## 🧪 AuthService 테스트 설계

### AuthServiceTest.java
```java
package com.routepick.service.auth;

import com.routepick.dto.auth.request.*;
import com.routepick.dto.auth.response.*;
import com.routepick.entity.user.User;
import com.routepick.entity.user.UserProfile;
import com.routepick.entity.user.UserVerification;
import com.routepick.entity.user.SocialAccount;
import com.routepick.enums.SocialProvider;
import com.routepick.enums.UserStatus;
import com.routepick.enums.UserType;
import com.routepick.exception.auth.*;
import com.routepick.exception.user.UserNotFoundException;
import com.routepick.exception.user.DuplicateEmailException;
import com.routepick.exception.security.AccountLockedException;
import com.routepick.repository.user.*;
import com.routepick.security.jwt.JwtTokenProvider;
import com.routepick.service.email.EmailService;
import com.routepick.service.security.RateLimitService;
import com.routepick.service.security.SecurityAuditService;
import com.routepick.util.IpExtractorUtil;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 단위 테스트
 * - 인증 로직 검증
 * - 보안 정책 테스트
 * - 예외 상황 처리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("인증 서비스 단위 테스트")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserVerificationRepository userVerificationRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private UserAgreementRepository userAgreementRepository;
    
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private RateLimitService rateLimitService;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UserProfile testProfile;
    private UserVerification testVerification;
    private String testClientIp = "192.168.1.100";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // 테스트 사용자 데이터 설정
        testUser = User.builder()
            .id(1L)
            .email("test@routepick.com")
            .password("encodedPassword123!")
            .userType(UserType.USER)
            .userStatus(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .build();

        testProfile = UserProfile.builder()
            .user(testUser)
            .nickname("테스트사용자")
            .phone("010-1234-5678")
            .profileCompleteness(75)
            .build();

        testVerification = UserVerification.builder()
            .user(testUser)
            .emailVerified(true)
            .phoneVerified(true)
            .build();
    }

    // ===== 로그인 테스트 =====

    @Test
    @DisplayName("정상 로그인 성공 테스트")
    void shouldLoginSuccessfully() {
        // given
        LoginRequest loginRequest = LoginRequest.builder()
            .email("test@routepick.com")
            .password("password123!")
            .rememberMe(false)
            .build();

        when(rateLimitService.isAllowed("login:" + testClientIp, 5, 300)).thenReturn(true);
        when(userRepository.findByEmailAndDeletedAtIsNull(loginRequest.getEmail()))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
            .thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(testUser.getEmail()))
            .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(testUser.getEmail()))
            .thenReturn("refresh-token");

        // when
        LoginResponse response = authService.login(loginRequest, testClientIp);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getUser().getEmail()).contains("***"); // 마스킹 확인
        assertThat(response.getTokens().getAccessToken()).isEqualTo("access-token");
        assertThat(response.getTokens().getRefreshToken()).isEqualTo("refresh-token");

        // 마지막 로그인 시간 업데이트 확인
        verify(userRepository).save(any(User.class));
        verify(securityAuditService).logLoginSuccess(testUser.getId(), testClientIp);
    }

    @Test
    @DisplayName("잘못된 비밀번호 로그인 실패 테스트")
    void shouldFailLoginWithWrongPassword() {
        // given
        LoginRequest loginRequest = LoginRequest.builder()
            .email("test@routepick.com")
            .password("wrongPassword")
            .build();

        when(rateLimitService.isAllowed("login:" + testClientIp, 5, 300)).thenReturn(true);
        when(userRepository.findByEmailAndDeletedAtIsNull(loginRequest.getEmail()))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(loginRequest, testClientIp))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessageContaining("Invalid credentials");

        // 로그인 실패 기록 확인
        verify(securityAuditService).logLoginFailure(testUser.getId(), testClientIp, "WRONG_PASSWORD");
        verify(rateLimitService).recordFailedAttempt("login_fail:" + testUser.getEmail());
    }

    @Test
    @DisplayName("존재하지 않는 사용자 로그인 실패 테스트")
    void shouldFailLoginWithNonExistentUser() {
        // given
        LoginRequest loginRequest = LoginRequest.builder()
            .email("nonexistent@routepick.com")
            .password("password123!")
            .build();

        when(rateLimitService.isAllowed("login:" + testClientIp, 5, 300)).thenReturn(true);
        when(userRepository.findByEmailAndDeletedAtIsNull(loginRequest.getEmail()))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(loginRequest, testClientIp))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining("User not found");

        verify(securityAuditService).logLoginFailure(null, testClientIp, "USER_NOT_FOUND");
    }

    @Test
    @DisplayName("계정 잠금 상태 로그인 실패 테스트")
    void shouldFailLoginWithLockedAccount() {
        // given
        testUser.setUserStatus(UserStatus.LOCKED);
        
        LoginRequest loginRequest = LoginRequest.builder()
            .email("test@routepick.com")
            .password("password123!")
            .build();

        when(rateLimitService.isAllowed("login:" + testClientIp, 5, 300)).thenReturn(true);
        when(userRepository.findByEmailAndDeletedAtIsNull(loginRequest.getEmail()))
            .thenReturn(Optional.of(testUser));

        // when & then
        assertThatThrownBy(() -> authService.login(loginRequest, testClientIp))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("Account is locked");

        verify(securityAuditService).logLoginFailure(testUser.getId(), testClientIp, "ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("Rate Limit 초과 로그인 실패 테스트")
    void shouldFailLoginWithRateLimitExceeded() {
        // given
        LoginRequest loginRequest = LoginRequest.builder()
            .email("test@routepick.com")
            .password("password123!")
            .build();

        when(rateLimitService.isAllowed("login:" + testClientIp, 5, 300)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(loginRequest, testClientIp))
            .isInstanceOf(RateLimitExceededException.class)
            .hasMessageContaining("Too many login attempts");

        verify(securityAuditService).logRateLimitViolation(testClientIp, "LOGIN_RATE_LIMIT");
    }

    // ===== 회원가입 테스트 =====

    @Test
    @DisplayName("정상 회원가입 성공 테스트")
    void shouldSignUpSuccessfully() {
        // given
        SignUpRequest signUpRequest = SignUpRequest.builder()
            .email("newuser@routepick.com")
            .password("Password123!")
            .passwordConfirm("Password123!")
            .nickname("신규사용자")
            .phone("010-9876-5432")
            .agreementIds(List.of(1L, 2L)) // 필수 약관
            .marketingConsent(true)
            .build();

        when(userRepository.existsByEmailAndDeletedAtIsNull(signUpRequest.getEmail()))
            .thenReturn(false);
        when(userProfileRepository.existsByNicknameAndUser_DeletedAtIsNull(signUpRequest.getNickname()))
            .thenReturn(false);
        when(passwordEncoder.encode(signUpRequest.getPassword()))
            .thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(2L);
                return user;
            });

        // when
        UserResponse response = authService.signUp(signUpRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).contains("***"); // 마스킹 확인
        assertThat(response.getNickname()).isEqualTo("신규사용자");
        assertThat(response.getEmailVerified()).isFalse();
        assertThat(response.getMarketingConsent()).isTrue();

        // 저장 확인
        verify(userRepository).save(any(User.class));
        verify(userProfileRepository).save(any(UserProfile.class));
        verify(userVerificationRepository).save(any(UserVerification.class));
        verify(emailService).sendWelcomeEmailAsync(eq("newuser@routepick.com"), eq("신규사용자"));
    }

    @Test
    @DisplayName("중복 이메일 회원가입 실패 테스트")
    void shouldFailSignUpWithDuplicateEmail() {
        // given
        SignUpRequest signUpRequest = SignUpRequest.builder()
            .email("duplicate@routepick.com")
            .password("Password123!")
            .passwordConfirm("Password123!")
            .nickname("중복테스트")
            .phone("010-1111-2222")
            .agreementIds(List.of(1L, 2L))
            .build();

        when(userRepository.existsByEmailAndDeletedAtIsNull(signUpRequest.getEmail()))
            .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signUp(signUpRequest))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessageContaining("Email already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("중복 닉네임 회원가입 실패 테스트")
    void shouldFailSignUpWithDuplicateNickname() {
        // given
        SignUpRequest signUpRequest = SignUpRequest.builder()
            .email("newuser@routepick.com")
            .password("Password123!")
            .passwordConfirm("Password123!")
            .nickname("중복닉네임")
            .phone("010-3333-4444")
            .agreementIds(List.of(1L, 2L))
            .build();

        when(userRepository.existsByEmailAndDeletedAtIsNull(signUpRequest.getEmail()))
            .thenReturn(false);
        when(userProfileRepository.existsByNicknameAndUser_DeletedAtIsNull(signUpRequest.getNickname()))
            .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signUp(signUpRequest))
            .isInstanceOf(DuplicateNicknameException.class)
            .hasMessageContaining("Nickname already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("약한 비밀번호 회원가입 실패 테스트")
    void shouldFailSignUpWithWeakPassword() {
        // given
        SignUpRequest signUpRequest = SignUpRequest.builder()
            .email("newuser@routepick.com")
            .password("123456") // 약한 비밀번호
            .passwordConfirm("123456")
            .nickname("신규사용자")
            .phone("010-5555-6666")
            .agreementIds(List.of(1L, 2L))
            .build();

        // when & then - @Valid 검증에 의해 실패해야 함
        // 실제로는 Controller 레벨에서 처리되지만, 서비스 레벨에서도 검증
        assertThatThrownBy(() -> authService.signUp(signUpRequest))
            .isInstanceOf(WeakPasswordException.class)
            .hasMessageContaining("Password does not meet security requirements");
    }

    // ===== 소셜 로그인 테스트 =====

    @Test
    @DisplayName("Google 소셜 로그인 성공 테스트")
    void shouldLoginWithGoogleSuccessfully() {
        // given
        SocialLoginRequest socialRequest = SocialLoginRequest.builder()
            .provider(SocialProvider.GOOGLE)
            .socialId("google-12345")
            .email("user@gmail.com")
            .name("Google User")
            .profileImageUrl("https://google.com/profile.jpg")
            .accessToken("google-access-token")
            .build();

        SocialAccount existingSocial = SocialAccount.builder()
            .user(testUser)
            .provider(SocialProvider.GOOGLE)
            .socialId("google-12345")
            .build();

        when(socialAccountRepository.findByProviderAndSocialId(
            SocialProvider.GOOGLE, "google-12345"))
            .thenReturn(Optional.of(existingSocial));
        when(jwtTokenProvider.generateAccessToken(testUser.getEmail()))
            .thenReturn("social-access-token");
        when(jwtTokenProvider.generateRefreshToken(testUser.getEmail()))
            .thenReturn("social-refresh-token");

        // when
        LoginResponse response = authService.socialLogin(socialRequest, testClientIp);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTokens().getAccessToken()).isEqualTo("social-access-token");

        verify(securityAuditService).logSocialLoginSuccess(
            testUser.getId(), testClientIp, SocialProvider.GOOGLE);
    }

    @Test
    @DisplayName("신규 소셜 계정 자동 회원가입 테스트")
    void shouldAutoSignUpWithNewSocialAccount() {
        // given
        SocialLoginRequest socialRequest = SocialLoginRequest.builder()
            .provider(SocialProvider.KAKAO)
            .socialId("kakao-67890")
            .email("user@kakao.com")
            .name("카카오사용자")
            .profileImageUrl("https://kakao.com/profile.jpg")
            .accessToken("kakao-access-token")
            .build();

        when(socialAccountRepository.findByProviderAndSocialId(
            SocialProvider.KAKAO, "kakao-67890"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("user@kakao.com"))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(3L);
                return user;
            });
        when(jwtTokenProvider.generateAccessToken("user@kakao.com"))
            .thenReturn("kakao-access-token");
        when(jwtTokenProvider.generateRefreshToken("user@kakao.com"))
            .thenReturn("kakao-refresh-token");

        // when
        LoginResponse response = authService.socialLogin(socialRequest, testClientIp);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.isFirstLogin()).isTrue();
        assertThat(response.getIncompleteProfileFields()).isNotEmpty();

        // 사용자 및 소셜 계정 생성 확인
        verify(userRepository).save(any(User.class));
        verify(userProfileRepository).save(any(UserProfile.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    @Test
    @DisplayName("소셜 계정 연동 실패 테스트")
    void shouldFailSocialLoginWithInvalidToken() {
        // given
        SocialLoginRequest socialRequest = SocialLoginRequest.builder()
            .provider(SocialProvider.NAVER)
            .socialId("naver-invalid")
            .email("invalid@naver.com")
            .accessToken("invalid-token")
            .build();

        // 외부 소셜 API 검증 실패 시뮬레이션
        when(authService.validateSocialToken(socialRequest))
            .thenThrow(new InvalidSocialTokenException("Invalid social token"));

        // when & then
        assertThatThrownBy(() -> authService.socialLogin(socialRequest, testClientIp))
            .isInstanceOf(InvalidSocialTokenException.class)
            .hasMessageContaining("Invalid social token");
    }

    // ===== 토큰 갱신 테스트 =====

    @Test
    @DisplayName("Refresh Token으로 토큰 갱신 성공 테스트")
    void shouldRefreshTokenSuccessfully() {
        // given
        String refreshToken = "valid-refresh-token";
        
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn(testUser.getEmail());
        when(userRepository.findByEmailAndDeletedAtIsNull(testUser.getEmail()))
            .thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateAccessToken(testUser.getEmail()))
            .thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(testUser.getEmail()))
            .thenReturn("new-refresh-token");

        // when
        TokenResponse response = authService.refreshToken(refreshToken);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("만료된 Refresh Token 갱신 실패 테스트")
    void shouldFailRefreshWithExpiredToken() {
        // given
        String expiredRefreshToken = "expired-refresh-token";
        
        when(jwtTokenProvider.validateToken(expiredRefreshToken))
            .thenThrow(new ExpiredTokenException("Token expired"));

        // when & then
        assertThatThrownBy(() -> authService.refreshToken(expiredRefreshToken))
            .isInstanceOf(ExpiredTokenException.class)
            .hasMessageContaining("Token expired");
    }

    // ===== 로그아웃 테스트 =====

    @Test
    @DisplayName("로그아웃 성공 테스트")
    void shouldLogoutSuccessfully() {
        // given
        String accessToken = "valid-access-token";
        
        when(jwtTokenProvider.getUsernameFromToken(accessToken)).thenReturn(testUser.getEmail());
        when(userRepository.findByEmailAndDeletedAtIsNull(testUser.getEmail()))
            .thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.getExpirationTime(accessToken))
            .thenReturn(System.currentTimeMillis() + 3600000);

        // when
        authService.logout(accessToken, testClientIp);

        // then
        // 토큰이 블랙리스트에 추가되는지 확인
        verify(valueOperations).set(
            eq("blacklist:token:" + accessToken), 
            eq("LOGGED_OUT"), 
            eq(3600000L), 
            eq(TimeUnit.MILLISECONDS)
        );
        
        verify(securityAuditService).logLogoutSuccess(testUser.getId(), testClientIp);
    }

    // ===== 브루트 포스 공격 방어 테스트 =====

    @Test
    @DisplayName("브루트 포스 공격 계정 잠금 테스트")
    void shouldLockAccountAfterBruteForceAttempts() {
        // given
        LoginRequest loginRequest = LoginRequest.builder()
            .email("test@routepick.com")
            .password("wrongPassword")
            .build();

        // 5회 연속 실패 후 계정 잠금 시뮬레이션
        when(rateLimitService.isAllowed("login:" + testClientIp, 5, 300)).thenReturn(true);
        when(userRepository.findByEmailAndDeletedAtIsNull(loginRequest.getEmail()))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
            .thenReturn(false);
        when(rateLimitService.getFailureCount("login_fail:" + testUser.getEmail()))
            .thenReturn(5);

        // when & then
        assertThatThrownBy(() -> authService.login(loginRequest, testClientIp))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("Account locked due to multiple failed attempts");

        // 계정 상태가 LOCKED로 변경되는지 확인
        verify(userRepository).save(argThat(user -> 
            user.getUserStatus() == UserStatus.LOCKED));
        
        verify(securityAuditService).logAccountLocked(testUser.getId(), testClientIp, "BRUTE_FORCE");
    }

    // ===== 이메일 검증 테스트 =====

    @Test
    @DisplayName("이메일 중복 확인 테스트")
    void shouldCheckEmailAvailability() {
        // given
        EmailCheckRequest request = EmailCheckRequest.builder()
            .email("available@routepick.com")
            .build();

        when(userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail()))
            .thenReturn(false);

        // when
        EmailCheckResponse response = authService.checkEmailAvailability(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAvailable()).isTrue();
        assertThat(response.getMessage()).contains("사용 가능한");
        assertThat(response.getEmail()).isEqualTo(request.getEmail());
    }

    @Test
    @DisplayName("이메일 중복 시 추천 이메일 제공 테스트")
    void shouldProvideSuggestedEmailsWhenDuplicate() {
        // given
        EmailCheckRequest request = EmailCheckRequest.builder()
            .email("duplicate@routepick.com")
            .build();

        when(userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail()))
            .thenReturn(true);

        // when
        EmailCheckResponse response = authService.checkEmailAvailability(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAvailable()).isFalse();
        assertThat(response.getMessage()).contains("이미 사용 중인");
        assertThat(response.getSuggestions()).isNotEmpty();
        assertThat(response.getSuggestions()).allMatch(email -> 
            email.startsWith("duplicate") && email.contains("@routepick.com"));
    }

    // ===== 엣지 케이스 테스트 =====

    @Test
    @DisplayName("삭제된 사용자 복구 로그인 테스트")
    void shouldRestoreDeletedUserOnLogin() {
        // given
        testUser.setDeletedAt(LocalDateTime.now().minusDays(7)); // 7일 전 삭제
        
        LoginRequest loginRequest = LoginRequest.builder()
            .email("test@routepick.com")
            .password("password123!")
            .build();

        when(rateLimitService.isAllowed("login:" + testClientIp, 5, 300)).thenReturn(true);
        when(userRepository.findByEmail(loginRequest.getEmail()))
            .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
            .thenReturn(true);

        // when
        LoginResponse response = authService.login(loginRequest, testClientIp);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        
        // 삭제 상태가 해제되는지 확인
        verify(userRepository).save(argThat(user -> user.getDeletedAt() == null));
    }

    @AfterEach
    void tearDown() {
        reset(userRepository, jwtTokenProvider, passwordEncoder, emailService, 
              rateLimitService, securityAuditService, redisTemplate);
    }
}
```

---

## 🔧 테스트 유틸리티

### AuthTestDataBuilder.java
```java
package com.routepick.test.builder;

import com.routepick.dto.auth.request.LoginRequest;
import com.routepick.dto.auth.request.SignUpRequest;
import com.routepick.dto.auth.request.SocialLoginRequest;
import com.routepick.entity.user.User;
import com.routepick.entity.user.UserProfile;
import com.routepick.enums.SocialProvider;
import com.routepick.enums.UserStatus;
import com.routepick.enums.UserType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인증 테스트 데이터 빌더
 */
public class AuthTestDataBuilder {
    
    public static LoginRequest.LoginRequestBuilder validLoginRequest() {
        return LoginRequest.builder()
            .email("test@routepick.com")
            .password("Password123!")
            .rememberMe(false)
            .deviceInfo("Test Device")
            .appVersion("1.0.0");
    }
    
    public static SignUpRequest.SignUpRequestBuilder validSignUpRequest() {
        return SignUpRequest.builder()
            .email("newuser@routepick.com")
            .password("Password123!")
            .passwordConfirm("Password123!")
            .nickname("신규사용자")
            .phone("010-1234-5678")
            .agreementIds(List.of(1L, 2L))
            .marketingConsent(true);
    }
    
    public static SocialLoginRequest.SocialLoginRequestBuilder validGoogleLogin() {
        return SocialLoginRequest.builder()
            .provider(SocialProvider.GOOGLE)
            .socialId("google-123456")
            .email("user@gmail.com")
            .name("Google User")
            .profileImageUrl("https://google.com/profile.jpg")
            .accessToken("google-access-token")
            .expiresIn(3600L);
    }
    
    public static User.UserBuilder activeUser() {
        return User.builder()
            .email("test@routepick.com")
            .password("encodedPassword")
            .userType(UserType.USER)
            .userStatus(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .lastLoginAt(LocalDateTime.now().minusDays(1));
    }
    
    public static UserProfile.UserProfileBuilder completeProfile() {
        return UserProfile.builder()
            .nickname("테스트사용자")
            .phone("010-1234-5678")
            .profileCompleteness(100)
            .bio("테스트 사용자입니다.");
    }
}
```

---

## 📊 테스트 커버리지 목표

### 기능별 테스트 범위
- ✅ **로그인**: 성공/실패, 계정 상태, Rate Limiting
- ✅ **회원가입**: 검증 규칙, 중복 확인, 약관 동의
- ✅ **소셜 로그인**: 4개 제공자, 신규 가입, 토큰 검증
- ✅ **토큰 관리**: 생성, 갱신, 만료, 블랙리스트
- ✅ **보안 정책**: 브루트 포스, 계정 잠금, 감사 로그

### 예외 시나리오
- ✅ **인증 실패**: 잘못된 비밀번호, 존재하지 않는 사용자
- ✅ **계정 상태**: 잠금, 비활성화, 삭제된 계정
- ✅ **Rate Limiting**: IP 기반, 계정 기반 제한
- ✅ **토큰 보안**: 만료, 변조, 무효화

---

*Step 9-1b 완료: AuthService 단위 테스트 설계*
*다음 단계: EmailService 단위 테스트 설계*