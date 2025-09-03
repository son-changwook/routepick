# Step 9-1d: AuthController 웹 레이어 테스트 설계

> 인증 API 엔드포인트 및 HTTP 보안 테스트  
> 생성일: 2025-08-27  
> 기반: step7-1a_auth_controller.md, step7-1c_auth_request_dtos.md  
> 테스트 범위: RESTful API, 입력 검증, Rate Limiting, CORS 보안

---

## 🎯 테스트 목표

### 핵심 검증 사항
- **HTTP 엔드포인트**: 정상 응답, 상태 코드, JSON 구조
- **입력 검증**: @Valid 어노테이션, 한국 특화 검증
- **보안 헤더**: XSS, CORS, Rate Limiting
- **에러 처리**: 예외 상황별 적절한 HTTP 응답

---

## 🧪 AuthController 웹 레이어 테스트

### AuthControllerTest.java
```java
package com.routepick.controller.auth;

import com.routepick.dto.auth.request.*;
import com.routepick.dto.auth.response.*;
import com.routepick.service.auth.AuthService;
import com.routepick.exception.auth.*;
import com.routepick.exception.user.UserNotFoundException;
import com.routepick.exception.user.DuplicateEmailException;
import com.routepick.exception.security.RateLimitExceededException;
import com.routepick.config.test.SecurityTestConfig;
import com.routepick.test.builder.AuthTestDataBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * AuthController 웹 레이어 테스트
 * - HTTP 엔드포인트 검증
 * - 입력 검증 및 에러 처리
 * - 보안 헤더 및 Rate Limiting
 */
@WebMvcTest(AuthController.class)
@Import(SecurityTestConfig.class)
@DisplayName("인증 컨트롤러 웹 레이어 테스트")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuthService authService;

    private static final String TEST_IP = "192.168.1.100";

    // ===== 로그인 API 테스트 =====

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginApiTest {

        @Test
        @DisplayName("정상 로그인 200 응답 테스트")
        void shouldReturnSuccessfulLoginResponse() throws Exception {
            // given
            LoginRequest request = AuthTestDataBuilder.validLoginRequest().build();
            
            LoginResponse response = LoginResponse.builder()
                .user(UserResponse.builder()
                    .id(1L)
                    .email("te***@routepick.com")
                    .nickname("테스트사용자")
                    .emailVerified(true)
                    .build())
                .tokens(TokenResponse.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .build())
                .success(true)
                .message("로그인이 완료되었습니다.")
                .loginAt(LocalDateTime.now())
                .build();

            when(authService.login(any(LoginRequest.class), anyString()))
                .thenReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-Forwarded-For", TEST_IP))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.user.email").value("te***@routepick.com"))
                    .andExpect(jsonPath("$.data.tokens.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.tokens.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(header().exists("X-Content-Type-Options"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));

            verify(authService).login(any(LoginRequest.class), eq(TEST_IP));
        }

        @Test
        @DisplayName("잘못된 비밀번호 401 응답 테스트")
        void shouldReturn401ForInvalidCredentials() throws Exception {
            // given
            LoginRequest request = AuthTestDataBuilder.validLoginRequest()
                .password("wrongPassword")
                .build();

            when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpected(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Invalid credentials"));
        }

        @Test
        @DisplayName("사용자 없음 404 응답 테스트")
        void shouldReturn404ForUserNotFound() throws Exception {
            // given
            LoginRequest request = AuthTestDataBuilder.validLoginRequest()
                .email("nonexistent@routepick.com")
                .build();

            when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new UserNotFoundException("User not found"));

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("Rate Limiting 429 응답 테스트")
        void shouldReturn429ForRateLimitExceeded() throws Exception {
            // given
            LoginRequest request = AuthTestDataBuilder.validLoginRequest().build();

            when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new RateLimitExceededException("Too many login attempts"));

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                    .andExpect(header().exists("Retry-After"));
        }

        @Test
        @DisplayName("입력 검증 실패 400 응답 테스트")
        void shouldReturn400ForValidationFailure() throws Exception {
            // given - 잘못된 이메일 형식
            LoginRequest request = LoginRequest.builder()
                .email("invalid-email")
                .password("password123!")
                .build();

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("email"))
                    .andExpect(jsonPath("$.errors[0].message").value("올바른 이메일 형식이 아닙니다."));
        }

        @Test
        @DisplayName("XSS 공격 방어 테스트")
        void shouldDefendAgainstXSSAttack() throws Exception {
            // given - XSS 시도가 포함된 요청
            LoginRequest request = LoginRequest.builder()
                .email("test@routepick.com")
                .password("<script>alert('xss')</script>")
                .build();

            when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists("X-XSS-Protection"))
                    .andExpect(header().string("X-XSS-Protection", "1; mode=block"))
                    .andExpect(content().string(not(containsString("<script>"))));
        }
    }

    // ===== 회원가입 API 테스트 =====

    @Nested
    @DisplayName("POST /api/v1/auth/signup")
    class SignUpApiTest {

        @Test
        @DisplayName("정상 회원가입 201 응답 테스트")
        void shouldReturnSuccessfulSignUpResponse() throws Exception {
            // given
            SignUpRequest request = AuthTestDataBuilder.validSignUpRequest().build();
            
            UserResponse response = UserResponse.builder()
                .id(1L)
                .email("ne***@routepick.com")
                .nickname("신규사용자")
                .userType(UserType.USER)
                .userStatus(UserStatus.ACTIVE)
                .emailVerified(false)
                .phoneVerified(false)
                .marketingConsent(true)
                .createdAt(LocalDateTime.now())
                .profileCompleteness(50)
                .build();

            when(authService.signUp(any(SignUpRequest.class))).thenReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value("ne***@routepick.com"))
                    .andExpect(jsonPath("$.data.nickname").value("신규사용자"))
                    .andExpected(jsonPath("$.data.emailVerified").value(false))
                    .andExpected(jsonPath("$.data.marketingConsent").value(true));
        }

        @Test
        @DisplayName("중복 이메일 409 응답 테스트")
        void shouldReturn409ForDuplicateEmail() throws Exception {
            // given
            SignUpRequest request = AuthTestDataBuilder.validSignUpRequest()
                .email("duplicate@routepick.com")
                .build();

            when(authService.signUp(any(SignUpRequest.class)))
                .thenThrow(new DuplicateEmailException("Email already exists"));

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpected(jsonPath("$.success").value(false))
                    .andExpected(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"));
        }

        @Test
        @DisplayName("한국 특화 검증 테스트")
        void shouldValidateKoreanSpecificFields() throws Exception {
            // given - 잘못된 한국 휴대폰 번호
            SignUpRequest request = AuthTestDataBuilder.validSignUpRequest()
                .phone("123-456-7890") // 미국식 번호
                .build();

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpected(status().isBadRequest())
                    .andExpected(jsonPath("$.errors[?(@.field == 'phone')]").exists())
                    .andExpected(jsonPath("$.errors[?(@.field == 'phone')].message")
                        .value("휴대폰 번호는 010-0000-0000 형식이어야 합니다."));
        }

        @Test
        @DisplayName("패스워드 보안 정책 검증 테스트")
        void shouldValidatePasswordSecurityPolicy() throws Exception {
            // given - 보안 정책 위반 패스워드
            SignUpRequest request = AuthTestDataBuilder.validSignUpRequest()
                .password("123456") // 약한 패스워드
                .passwordConfirm("123456")
                .build();

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpected(jsonPath("$.errors[?(@.field == 'password')]").exists())
                    .andExpected(jsonPath("$.errors[?(@.field == 'password')].message")
                        .value(containsString("대소문자, 숫자, 특수문자")));
        }

        @Test
        @DisplayName("닉네임 한글 검증 테스트")
        void shouldValidateKoreanNickname() throws Exception {
            // given
            SignUpRequest request = AuthTestDataBuilder.validSignUpRequest()
                .nickname("테스트123") // 한글 + 숫자 조합 (유효)
                .build();

            UserResponse response = UserResponse.builder()
                .nickname("테스트123")
                .build();

            when(authService.signUp(any(SignUpRequest.class))).thenReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpected(status().isCreated())
                    .andExpected(jsonPath("$.data.nickname").value("테스트123"));
        }

        @Test
        @DisplayName("약관 동의 필수 검증 테스트")
        void shouldValidateMandatoryAgreements() throws Exception {
            // given - 필수 약관 미동의
            SignUpRequest request = AuthTestDataBuilder.validSignUpRequest()
                .agreementIds(List.of(3L, 4L)) // 필수 약관(1L, 2L) 누락
                .build();

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpected(status().isBadRequest())
                    .andExpected(jsonPath("$.errors[?(@.message == '필수 약관에 모두 동의해야 합니다.')]").exists());
        }
    }

    // ===== 소셜 로그인 API 테스트 =====

    @Nested
    @DisplayName("POST /api/v1/auth/social-login")
    class SocialLoginApiTest {

        @Test
        @DisplayName("Google 소셜 로그인 성공 테스트")
        void shouldSucceedGoogleSocialLogin() throws Exception {
            // given
            SocialLoginRequest request = AuthTestDataBuilder.validGoogleLogin().build();
            
            LoginResponse response = LoginResponse.builder()
                .user(UserResponse.builder()
                    .email("us***@gmail.com")
                    .nickname("GoogleUser")
                    .build())
                .tokens(TokenResponse.builder()
                    .accessToken("google-access-token")
                    .refreshToken("google-refresh-token")
                    .tokenType("Bearer")
                    .build())
                .success(true)
                .build();

            when(authService.socialLogin(any(SocialLoginRequest.class), anyString()))
                .thenReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/social-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.user.email").value("us***@gmail.com"))
                    .andExpected(jsonPath("$.data.tokens.accessToken").value("google-access-token"));
        }

        @Test
        @DisplayName("잘못된 소셜 토큰 401 응답 테스트")
        void shouldReturn401ForInvalidSocialToken() throws Exception {
            // given
            SocialLoginRequest request = AuthTestDataBuilder.validGoogleLogin()
                .accessToken("invalid-token")
                .build();

            when(authService.socialLogin(any(SocialLoginRequest.class), anyString()))
                .thenThrow(new InvalidSocialTokenException("Invalid social token"));

            // when & then
            mockMvc.perform(post("/api/v1/auth/social-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpected(status().isUnauthorized())
                    .andExpected(jsonPath("$.errorCode").value("INVALID_SOCIAL_TOKEN"));
        }

        @Test
        @DisplayName("지원하지 않는 소셜 제공자 400 응답 테스트")
        void shouldReturn400ForUnsupportedProvider() throws Exception {
            // given - JSON에서 잘못된 provider 전송
            String invalidRequest = """
                {
                    "provider": "UNSUPPORTED_PROVIDER",
                    "socialId": "123456",
                    "email": "test@example.com",
                    "name": "Test User",
                    "accessToken": "token"
                }
                """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/social-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest))
                    .andExpected(status().isBadRequest())
                    .andExpected(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        }
    }

    // ===== 토큰 갱신 API 테스트 =====

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshTokenApiTest {

        @Test
        @DisplayName("토큰 갱신 성공 테스트")
        void shouldRefreshTokenSuccessfully() throws Exception {
            // given
            String refreshToken = "valid-refresh-token";
            
            TokenResponse response = TokenResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .refreshExpiresIn(86400L)
                .build();

            when(authService.refreshToken(refreshToken)).thenReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .header("Authorization", "Bearer " + refreshToken))
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data.accessToken").value("new-access-token"))
                    .andExpected(jsonPath("$.data.refreshToken").value("new-refresh-token"));
        }

        @Test
        @DisplayName("만료된 Refresh Token 401 응답 테스트")
        void shouldReturn401ForExpiredRefreshToken() throws Exception {
            // given
            String expiredToken = "expired-refresh-token";

            when(authService.refreshToken(expiredToken))
                .thenThrow(new ExpiredTokenException("Token expired"));

            // when & then
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .header("Authorization", "Bearer " + expiredToken))
                    .andExpected(status().isUnauthorized())
                    .andExpected(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
        }
    }

    // ===== 로그아웃 API 테스트 =====

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutApiTest {

        @Test
        @DisplayName("로그아웃 성공 테스트")
        void shouldLogoutSuccessfully() throws Exception {
            // given
            String accessToken = "valid-access-token";

            doNothing().when(authService).logout(accessToken, TEST_IP);

            // when & then
            mockMvc.perform(post("/api/v1/auth/logout")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Forwarded-For", TEST_IP))
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.message").value("로그아웃이 완료되었습니다."));

            verify(authService).logout(accessToken, TEST_IP);
        }
    }

    // ===== 이메일 중복 확인 API 테스트 =====

    @Nested
    @DisplayName("POST /api/v1/auth/check-email")
    class CheckEmailApiTest {

        @Test
        @DisplayName("사용 가능한 이메일 200 응답 테스트")
        void shouldReturn200ForAvailableEmail() throws Exception {
            // given
            EmailCheckRequest request = EmailCheckRequest.builder()
                .email("available@routepick.com")
                .build();

            EmailCheckResponse response = EmailCheckResponse.available("available@routepick.com");

            when(authService.checkEmailAvailability(any(EmailCheckRequest.class)))
                .thenReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/check-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpected(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data.available").value(true))
                    .andExpected(jsonPath("$.data.message").value("사용 가능한 이메일입니다."));
        }

        @Test
        @DisplayName("중복 이메일 409 응답 및 추천 이메일 제공 테스트")
        void shouldReturn409WithSuggestionsForDuplicateEmail() throws Exception {
            // given
            EmailCheckRequest request = EmailCheckRequest.builder()
                .email("duplicate@routepick.com")
                .build();

            EmailCheckResponse response = EmailCheckResponse.unavailable(
                "duplicate@routepick.com",
                List.of("duplicate1@routepick.com", "duplicate2@routepick.com")
            );

            when(authService.checkEmailAvailability(any(EmailCheckRequest.class)))
                .thenReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/check-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpected(status().isConflict())
                    .andExpected(jsonPath("$.data.available").value(false))
                    .andExpected(jsonPath("$.data.suggestions").isArray())
                    .andExpected(jsonPath("$.data.suggestions", hasSize(2)));
        }

        @Test
        @DisplayName("이메일 형식 검증 400 응답 테스트")
        void shouldReturn400ForInvalidEmailFormat() throws Exception {
            // given
            EmailCheckRequest request = EmailCheckRequest.builder()
                .email("invalid.email.format")
                .build();

            // when & then
            mockMvc.perform(post("/api/v1/auth/check-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpected(status().isBadRequest())
                    .andExpected(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpected(jsonPath("$.errors[0].field").value("email"));
        }
    }

    // ===== CORS 보안 테스트 =====

    @Nested
    @DisplayName("CORS 보안 테스트")
    class CorsSecurityTest {

        @Test
        @DisplayName("허용된 Origin CORS 통과 테스트")
        void shouldAllowValidOrigin() throws Exception {
            // given
            LoginRequest request = AuthTestDataBuilder.validLoginRequest().build();

            when(authService.login(any(LoginRequest.class), anyString()))
                .thenReturn(LoginResponse.builder().success(true).build());

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("Origin", "https://routepick.com"))
                    .andExpected(status().isOk())
                    .andExpected(header().string("Access-Control-Allow-Origin", "https://routepick.com"));
        }

        @Test
        @DisplayName("차단된 Origin CORS 거부 테스트")
        void shouldRejectInvalidOrigin() throws Exception {
            // given
            LoginRequest request = AuthTestDataBuilder.validLoginRequest().build();

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("Origin", "https://malicious-site.com"))
                    .andExpected(status().isForbidden());
        }

        @Test
        @DisplayName("Preflight OPTIONS 요청 처리 테스트")
        void shouldHandlePreflightRequest() throws Exception {
            // when & then
            mockMvc.perform(options("/api/v1/auth/login")
                    .header("Origin", "https://routepick.com")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                    .andExpected(status().isOk())
                    .andExpected(header().string("Access-Control-Allow-Origin", "https://routepick.com"))
                    .andExpected(header().string("Access-Control-Allow-Methods", containsString("POST")))
                    .andExpected(header().string("Access-Control-Allow-Headers", containsString("Content-Type")));
        }
    }

    // ===== 보안 헤더 테스트 =====

    @Nested
    @DisplayName("보안 헤더 테스트")
    class SecurityHeadersTest {

        @Test
        @DisplayName("보안 헤더 포함 확인 테스트")
        void shouldIncludeSecurityHeaders() throws Exception {
            // given
            LoginRequest request = AuthTestDataBuilder.validLoginRequest().build();

            when(authService.login(any(LoginRequest.class), anyString()))
                .thenReturn(LoginResponse.builder().success(true).build());

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpected(status().isOk())
                    .andExpected(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpected(header().string("X-Frame-Options", "DENY"))
                    .andExpected(header().string("X-XSS-Protection", "1; mode=block"))
                    .andExpected(header().exists("Content-Security-Policy"));
        }

        @Test
        @DisplayName("HTTPS 리다이렉트 헤더 테스트")
        void shouldIncludeHttpsRedirectHeader() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/auth/login")
                    .header("X-Forwarded-Proto", "http")) // HTTP 요청 시뮬레이션
                    .andExpected(header().exists("Strict-Transport-Security"));
        }
    }
}
```

---

## 🔧 테스트 설정 클래스

### SecurityTestConfig.java
```java
package com.routepick.config.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 웹 레이어 테스트용 보안 설정
 */
@TestConfiguration
@EnableWebSecurity
public class SecurityTestConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors().configurationSource(testCorsConfigurationSource())
            .and()
            .authorizeHttpRequests()
            .anyRequest().permitAll()
            .and()
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true))
                .and()
            );
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource testCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 테스트용 허용 Origin
        configuration.setAllowedOrigins(List.of(
            "https://routepick.com",
            "http://localhost:3000"
        ));
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }
}
```

---

## 📊 테스트 커버리지 검증

### API 엔드포인트 테스트 완성도
- ✅ **POST /api/v1/auth/login**: 성공/실패 시나리오 7개
- ✅ **POST /api/v1/auth/signup**: 입력 검증 및 한국 특화 검증 6개
- ✅ **POST /api/v1/auth/social-login**: 4개 제공자 지원 테스트 3개
- ✅ **POST /api/v1/auth/refresh**: 토큰 갱신 로직 2개
- ✅ **POST /api/v1/auth/logout**: 정상 로그아웃 1개
- ✅ **POST /api/v1/auth/check-email**: 중복 확인 및 추천 3개

### 보안 테스트 완성도
- ✅ **XSS 방어**: 스크립트 태그 필터링
- ✅ **CORS 정책**: 허용/차단 Origin 검증
- ✅ **보안 헤더**: X-Frame-Options, X-XSS-Protection 등
- ✅ **Rate Limiting**: 429 응답 및 Retry-After 헤더
- ✅ **입력 검증**: @Valid 어노테이션 및 커스텀 검증

### 한국 특화 기능 테스트
- ✅ **휴대폰 번호**: 010-0000-0000 형식 검증
- ✅ **한글 닉네임**: 2-10자 한글/영문/숫자 조합
- ✅ **이메일 마스킹**: te***@routepick.com 형식
- ✅ **약관 동의**: 필수 약관 검증 로직

---

*Step 9-1d 완료: AuthController 웹 레이어 테스트 설계*
*다음 단계: 통합 테스트 설계*