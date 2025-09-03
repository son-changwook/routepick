# step9-1e1_auth_integration_core.md

> 인증 및 이메일 핵심 통합 테스트  
> 생성일: 2025-08-27  
> 기반: Testcontainers (Redis, MySQL), 실제 SMTP  
> 테스트 범위: E2E 인증 플로우, 실제 DB/Redis 연동

---

## 🎯 테스트 목표

### 핵심 검증 사항
- **전체 플로우**: 회원가입 → 이메일 인증 → 로그인 → 토큰 갱신
- **실제 환경**: Testcontainers로 Redis, MySQL 구동
- **성능 검증**: 동시 사용자, 응답 시간, 처리량
- **장애 복구**: DB 연결 끊김, Redis 장애 시나리오

---

## 🧪 AuthIntegration 통합 테스트

### AuthIntegrationTest.java
```java
package com.routepick.integration.auth;

import com.routepick.dto.auth.request.*;
import com.routepick.dto.auth.response.*;
import com.routepick.dto.email.request.EmailVerificationRequest;
import com.routepick.dto.email.response.EmailVerificationResponse;
import com.routepick.entity.user.User;
import com.routepick.repository.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * 인증 시스템 통합 테스트
 * - 실제 Redis, MySQL 컨테이너 사용
 * - 전체 인증 플로우 E2E 테스트
 * - 성능 및 안정성 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "logging.level.com.routepick=DEBUG"
})
class AuthIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + restTemplate.getRootUri().getPort() + "/api/v1";
        
        // 테스트 데이터 정리
        userRepository.deleteAll();
    }

    // ===== 전체 플로우 테스트 =====

    @Test
    @DisplayName("회원가입부터 로그인까지 전체 플로우 테스트")
    void shouldCompleteFullAuthenticationFlow() throws Exception {
        String email = "integration@routepick.com";
        String password = "Integration123!";

        // 1. 이메일 인증 코드 요청
        EmailVerificationRequest emailRequest = EmailVerificationRequest.builder()
            .email(email)
            .purpose("SIGNUP")
            .build();

        ResponseEntity<String> emailResponse = restTemplate.postForEntity(
            baseUrl + "/email/verify",
            emailRequest,
            String.class
        );
        
        assertThat(emailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Redis에서 인증 코드 조회 (테스트용)
        String verificationCode = getVerificationCodeFromRedis(email);
        assertThat(verificationCode).isNotNull();

        // 2. 회원가입
        SignupRequest signupRequest = SignupRequest.builder()
            .email(email)
            .password(password)
            .nickname("IntegrationUser")
            .realName("통합테스트")
            .phoneNumber("01012345678")
            .emailVerificationCode(verificationCode)
            .agreeToTerms(true)
            .agreeToPrivacy(true)
            .agreeToMarketing(false)
            .build();

        ResponseEntity<String> signupResponse = restTemplate.postForEntity(
            baseUrl + "/auth/signup",
            signupRequest,
            String.class
        );

        assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 3. 로그인
        LoginRequest loginRequest = LoginRequest.builder()
            .email(email)
            .password(password)
            .build();

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            String.class
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // JWT 토큰 검증
        String responseBody = loginResponse.getBody();
        assertThat(responseBody).contains("accessToken");
        assertThat(responseBody).contains("refreshToken");

        // 4. 토큰으로 사용자 정보 조회
        String accessToken = extractAccessToken(responseBody);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> profileResponse = restTemplate.exchange(
            baseUrl + "/users/profile",
            HttpMethod.GET,
            request,
            String.class
        );

        assertThat(profileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profileResponse.getBody()).contains(email);

        // 5. 토큰 갱신
        String refreshToken = extractRefreshToken(responseBody);
        TokenRefreshRequest refreshRequest = TokenRefreshRequest.builder()
            .refreshToken(refreshToken)
            .build();

        ResponseEntity<String> refreshResponse = restTemplate.postForEntity(
            baseUrl + "/auth/refresh",
            refreshRequest,
            String.class
        );

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).contains("accessToken");
    }

    @Test
    @DisplayName("소셜 로그인 통합 테스트")
    void shouldHandleSocialLoginFlow() {
        // Google OAuth 시뮬레이션
        SocialLoginRequest request = SocialLoginRequest.builder()
            .provider("GOOGLE")
            .idToken("mock_google_id_token")
            .email("google@routepick.com")
            .name("Google User")
            .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/auth/social/google",
            request,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("accessToken");
        
        // DB에 사용자 생성 확인
        User user = userRepository.findByEmail("google@routepick.com").orElse(null);
        assertThat(user).isNotNull();
        assertThat(user.getProvider()).isEqualTo("GOOGLE");
    }

    // ===== 보안 테스트 =====

    @Test
    @DisplayName("브루트 포스 공격 방어 테스트")
    void shouldPreventBruteForceAttacks() {
        String email = "bruteforce@routepick.com";
        createTestUser(email, "Correct123!");

        LoginRequest request = LoginRequest.builder()
            .email(email)
            .password("WrongPassword")
            .build();

        // 5회 연속 실패 시도
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/auth/login",
                request,
                String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 6번째 시도는 차단되어야 함
        ResponseEntity<String> blockedResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            request,
            String.class
        );
        
        assertThat(blockedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("JWT 토큰 변조 방어 테스트")
    void shouldPreventTokenTampering() {
        String email = "token@routepick.com";
        createTestUser(email, "Token123!");

        // 정상 로그인
        LoginRequest loginRequest = LoginRequest.builder()
            .email(email)
            .password("Token123!")
            .build();

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            String.class
        );

        String accessToken = extractAccessToken(loginResponse.getBody());
        
        // 토큰 변조
        String tamperedToken = accessToken.substring(0, accessToken.length() - 5) + "HACKED";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tamperedToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/users/profile",
            HttpMethod.GET,
            request,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ===== Rate Limiting 테스트 =====

    @Test
    @DisplayName("이메일 인증 Rate Limiting 테스트")
    void shouldRateLimitEmailVerification() {
        String email = "ratelimit@routepick.com";
        
        EmailVerificationRequest request = EmailVerificationRequest.builder()
            .email(email)
            .purpose("SIGNUP")
            .build();

        // 첫 번째 요청 성공
        ResponseEntity<String> firstResponse = restTemplate.postForEntity(
            baseUrl + "/email/verify",
            request,
            String.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 즉시 두 번째 요청 (쿨다운 위반)
        ResponseEntity<String> secondResponse = restTemplate.postForEntity(
            baseUrl + "/email/verify",
            request,
            String.class
        );
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(secondResponse.getHeaders().getFirst("Retry-After")).isNotNull();
    }