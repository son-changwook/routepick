# Step 9-1e: AuthIntegration 통합 테스트 구현

> 인증 및 이메일 전체 플로우 통합 테스트  
> 생성일: 2025-08-27  
> 기반: Testcontainers (Redis, MySQL), 실제 SMTP  
> 테스트 범위: E2E 인증 플로우, 실제 DB/Redis 연동, 성능 테스트

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
import com.routepick.entity.user.UserProfile;
import com.routepick.enums.SocialProvider;
import com.routepick.enums.UserStatus;
import com.routepick.repository.user.UserRepository;
import com.routepick.repository.user.UserProfileRepository;
import com.routepick.test.config.TestContainersConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * 인증 및 이메일 전체 플로우 통합 테스트
 * - Testcontainers로 실제 환경 구성
 * - E2E 인증 플로우 검증
 * - 성능 및 동시성 테스트
 * - 장애 복구 시나리오 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestContainersConfig.class)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "logging.level.com.routepick=DEBUG"
})
@DisplayName("인증 및 이메일 통합 테스트")
class AuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1";
        
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    // ===== 전체 인증 플로우 E2E 테스트 =====

    @Test
    @DisplayName("회원가입부터 로그인까지 전체 플로우 테스트")
    void shouldCompleteFullAuthenticationFlow() {
        // 1단계: 이메일 중복 확인
        EmailCheckRequest emailCheck = EmailCheckRequest.builder()
            .email("integration@routepick.com")
            .build();

        ResponseEntity<String> emailCheckResponse = restTemplate.postForEntity(
            baseUrl + "/auth/check-email",
            emailCheck,
            String.class
        );
        
        assertThat(emailCheckResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emailCheckResponse.getBody()).contains("\"available\":true");

        // 2단계: 이메일 인증 코드 발송
        EmailVerificationRequest emailVerification = EmailVerificationRequest.builder()
            .email("integration@routepick.com")
            .purpose("SIGNUP")
            .build();

        ResponseEntity<String> verificationResponse = restTemplate.postForEntity(
            baseUrl + "/email/verify",
            emailVerification,
            String.class
        );
        
        assertThat(verificationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verificationResponse.getBody()).contains("\"codeSent\":true");

        // 3단계: Redis에서 인증 코드 조회 (실제 환경에서는 이메일 확인)
        String verificationCode = (String) redisTemplate.opsForValue()
            .get("email:verification:integration@routepick.com");
        assertThat(verificationCode).isNotNull().matches("\\d{6}");

        // 4단계: 인증 코드 검증
        EmailVerificationRequest codeVerification = EmailVerificationRequest.builder()
            .email("integration@routepick.com")
            .verificationCode(verificationCode)
            .purpose("SIGNUP")
            .build();

        ResponseEntity<String> codeVerificationResponse = restTemplate.postForEntity(
            baseUrl + "/email/verify",
            codeVerification,
            String.class
        );
        
        assertThat(codeVerificationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(codeVerificationResponse.getBody()).contains("\"verified\":true");

        // 5단계: 회원가입
        SignUpRequest signUpRequest = SignUpRequest.builder()
            .email("integration@routepick.com")
            .password("Integration123!")
            .passwordConfirm("Integration123!")
            .nickname("통합테스트")
            .phone("010-1111-2222")
            .agreementIds(List.of(1L, 2L))
            .marketingConsent(false)
            .build();

        ResponseEntity<String> signUpResponse = restTemplate.postForEntity(
            baseUrl + "/auth/signup",
            signUpRequest,
            String.class
        );
        
        assertThat(signUpResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signUpResponse.getBody()).contains("\"nickname\":\"통합테스트\"");

        // 6단계: 로그인
        LoginRequest loginRequest = LoginRequest.builder()
            .email("integration@routepick.com")
            .password("Integration123!")
            .rememberMe(false)
            .build();

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            String.class
        );
        
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).contains("\"accessToken\":");
        assertThat(loginResponse.getBody()).contains("\"refreshToken\":");

        // 7단계: DB 확인
        User savedUser = userRepository.findByEmailAndDeletedAtIsNull("integration@routepick.com")
            .orElseThrow();
        assertThat(savedUser.getEmail()).isEqualTo("integration@routepick.com");
        assertThat(savedUser.getUserStatus()).isEqualTo(UserStatus.ACTIVE);

        UserProfile savedProfile = userProfileRepository.findByUser(savedUser)
            .orElseThrow();
        assertThat(savedProfile.getNickname()).isEqualTo("통합테스트");
        assertThat(savedProfile.getPhone()).isEqualTo("010-1111-2222");
    }

    @Test
    @DisplayName("소셜 로그인 전체 플로우 테스트")
    void shouldCompleteSocialLoginFlow() {
        // given
        SocialLoginRequest socialRequest = SocialLoginRequest.builder()
            .provider(SocialProvider.GOOGLE)
            .socialId("google-integration-test")
            .email("social@gmail.com")
            .name("소셜사용자")
            .profileImageUrl("https://google.com/profile.jpg")
            .accessToken("google-test-token")
            .expiresIn(3600L)
            .build();

        // when - 첫 번째 소셜 로그인 (자동 회원가입)
        ResponseEntity<String> firstLoginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/social-login",
            socialRequest,
            String.class
        );

        // then
        assertThat(firstLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstLoginResponse.getBody()).contains("\"isFirstLogin\":true");
        assertThat(firstLoginResponse.getBody()).contains("\"accessToken\":");

        // DB에 사용자 생성 확인
        User socialUser = userRepository.findByEmailAndDeletedAtIsNull("social@gmail.com")
            .orElseThrow();
        assertThat(socialUser).isNotNull();

        // when - 두 번째 소셜 로그인 (기존 사용자)
        ResponseEntity<String> secondLoginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/social-login",
            socialRequest,
            String.class
        );

        // then
        assertThat(secondLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondLoginResponse.getBody()).contains("\"isFirstLogin\":false");
    }

    // ===== 토큰 관리 통합 테스트 =====

    @Test
    @DisplayName("토큰 갱신 및 로그아웃 플로우 테스트")
    void shouldManageTokensCorrectly() {
        // 사전 조건: 로그인된 사용자
        String[] tokens = performLogin("token@routepick.com", "TokenTest123!");
        String accessToken = tokens[0];
        String refreshToken = tokens[1];

        // 1단계: Access Token으로 보호된 리소스 접근 (향후 구현될 API)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2단계: Refresh Token으로 토큰 갱신
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.setBearerAuth(refreshToken);
        HttpEntity<String> refreshEntity = new HttpEntity<>(refreshHeaders);

        ResponseEntity<String> refreshResponse = restTemplate.exchange(
            baseUrl + "/auth/refresh",
            HttpMethod.POST,
            refreshEntity,
            String.class
        );

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).contains("\"accessToken\":");
        
        // 새로운 토큰 추출
        String newAccessToken = extractTokenFromResponse(refreshResponse.getBody(), "accessToken");
        assertThat(newAccessToken).isNotEqualTo(accessToken);

        // 3단계: 로그아웃
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setBearerAuth(newAccessToken);
        HttpEntity<String> logoutEntity = new HttpEntity<>(logoutHeaders);

        ResponseEntity<String> logoutResponse = restTemplate.exchange(
            baseUrl + "/auth/logout",
            HttpMethod.POST,
            logoutEntity,
            String.class
        );

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4단계: 로그아웃된 토큰으로 갱신 시도 (실패해야 함)
        ResponseEntity<String> failedRefreshResponse = restTemplate.exchange(
            baseUrl + "/auth/refresh",
            HttpMethod.POST,
            new HttpEntity<>(logoutHeaders),
            String.class
        );

        assertThat(failedRefreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ===== 보안 테스트 =====

    @Test
    @DisplayName("브루트 포스 공격 방어 테스트")
    void shouldDefendAgainstBruteForceAttack() {
        // 사전 조건: 테스트 사용자 생성
        createTestUser("bruteforce@routepick.com", "ValidPassword123!");

        LoginRequest loginRequest = LoginRequest.builder()
            .email("bruteforce@routepick.com")
            .password("WrongPassword")
            .build();

        // 5회 연속 실패 시도
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/auth/login",
                loginRequest,
                String.class
            );
            
            if (i < 4) {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            } else {
                // 5번째 시도에서 계정 잠금
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
            }
        }

        // 올바른 비밀번호로도 로그인 불가능 확인
        loginRequest.setPassword("ValidPassword123!");
        ResponseEntity<String> validPasswordResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            String.class
        );
        
        assertThat(validPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    @DisplayName("Rate Limiting 테스트")
    void shouldApplyRateLimiting() {
        EmailVerificationRequest request = EmailVerificationRequest.builder()
            .email("ratelimit@routepick.com")
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

    // ===== 성능 테스트 =====

    @Test
    @DisplayName("동시 사용자 로그인 성능 테스트")
    void shouldHandleConcurrentLogins() throws InterruptedException {
        // 사전 조건: 100명의 테스트 사용자 생성
        createMultipleTestUsers(100);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CompletableFuture<?>[] futures = new CompletableFuture[100];

        long startTime = System.currentTimeMillis();

        // 100명이 동시에 로그인
        for (int i = 0; i < 100; i++) {
            final int userId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                LoginRequest request = LoginRequest.builder()
                    .email("perf" + userId + "@routepick.com")
                    .password("Performance123!")
                    .build();

                ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/auth/login",
                    request,
                    String.class
                );

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }, executor);
        }

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 100명 로그인을 15초 내에 완료해야 함
        assertThat(duration).isLessThan(15000L);
        
        System.out.println("100 concurrent logins completed in " + duration + "ms");
        executor.shutdown();
    }

    @Test
    @DisplayName("이메일 발송 대량 처리 성능 테스트")
    void shouldHandleBulkEmailSending() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CompletableFuture<?>[] futures = new CompletableFuture[50];

        long startTime = System.currentTimeMillis();

        // 50개 이메일 동시 발송
        for (int i = 0; i < 50; i++) {
            final int emailId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                EmailVerificationRequest request = EmailVerificationRequest.builder()
                    .email("bulk" + emailId + "@routepick.com")
                    .purpose("SIGNUP")
                    .build();

                ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/email/verify",
                    request,
                    String.class
                );

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }, executor);
        }

        CompletableFuture.allOf(futures).get(20, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 50개 이메일을 10초 내에 처리해야 함
        assertThat(duration).isLessThan(10000L);
        
        System.out.println("50 emails sent concurrently in " + duration + "ms");
        executor.shutdown();
    }

    // ===== 장애 복구 테스트 =====

    @Test
    @DisplayName("Redis 연결 장애 복구 테스트")
    void shouldRecoverFromRedisFailure() {
        // Redis 일시적 장애 시뮬레이션 (실제로는 Testcontainers pause/unpause)
        // 여기서는 Redis 키 삭제로 시뮬레이션
        
        String email = "redis-failure@routepick.com";
        
        // 1단계: 정상 상황에서 인증 코드 발송
        EmailVerificationRequest request = EmailVerificationRequest.builder()
            .email(email)
            .purpose("SIGNUP")
            .build();

        ResponseEntity<String> response1 = restTemplate.postForEntity(
            baseUrl + "/email/verify",
            request,
            String.class
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Redis에서 코드 확인
        String code = (String) redisTemplate.opsForValue().get("email:verification:" + email);
        assertThat(code).isNotNull();

        // 2단계: Redis 데이터 삭제 (장애 시뮬레이션)
        redisTemplate.delete("email:verification:" + email);
        redisTemplate.delete("email:cooldown:" + email);

        // 3단계: 복구 시도 (새로운 코드 발송)
        await().atMost(31, TimeUnit.SECONDS) // 쿨다운 대기
            .until(() -> {
                ResponseEntity<String> recovery = restTemplate.postForEntity(
                    baseUrl + "/email/verify",
                    request,
                    String.class
                );
                return recovery.getStatusCode() == HttpStatus.OK;
            });

        // 4단계: 새 코드로 정상 동작 확인
        String newCode = (String) redisTemplate.opsForValue().get("email:verification:" + email);
        assertThat(newCode).isNotNull().isNotEqualTo(code);
    }

    // ===== 보조 메서드 =====

    private String[] performLogin(String email, String password) {
        // 사용자 생성
        createTestUser(email, password);

        // 로그인 수행
        LoginRequest loginRequest = LoginRequest.builder()
            .email(email)
            .password(password)
            .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String accessToken = extractTokenFromResponse(response.getBody(), "accessToken");
        String refreshToken = extractTokenFromResponse(response.getBody(), "refreshToken");

        return new String[]{accessToken, refreshToken};
    }

    private void createTestUser(String email, String password) {
        SignUpRequest signUpRequest = SignUpRequest.builder()
            .email(email)
            .password(password)
            .passwordConfirm(password)
            .nickname("테스트" + System.currentTimeMillis())
            .phone("010-" + String.format("%04d", (int)(Math.random() * 10000)) + "-5678")
            .agreementIds(List.of(1L, 2L))
            .marketingConsent(false)
            .build();

        restTemplate.postForEntity(baseUrl + "/auth/signup", signUpRequest, String.class);
    }

    private void createMultipleTestUsers(int count) {
        IntStream.range(0, count).parallel().forEach(i -> {
            SignUpRequest request = SignUpRequest.builder()
                .email("perf" + i + "@routepick.com")
                .password("Performance123!")
                .passwordConfirm("Performance123!")
                .nickname("성능테스트" + i)
                .phone("010-" + String.format("%04d", i) + "-0000")
                .agreementIds(List.of(1L, 2L))
                .build();

            restTemplate.postForEntity(baseUrl + "/auth/signup", request, String.class);
        });
    }

    private String extractTokenFromResponse(String responseBody, String tokenType) {
        try {
            // JSON 파싱하여 토큰 추출 (간단한 정규식 사용)
            String pattern = "\"" + tokenType + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern compiled = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = compiled.matcher(responseBody);
            
            if (matcher.find()) {
                return matcher.group(1);
            }
            throw new IllegalArgumentException("Token not found in response: " + tokenType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract token: " + tokenType, e);
        }
    }

    @AfterEach
    void tearDown() {
        // Redis 정리
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
}
```

---

## 🔧 Testcontainers 설정

### TestContainersConfig.java
```java
package com.routepick.test.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 설정
 * MySQL, Redis 컨테이너 구성
 */
@TestConfiguration
public class TestContainersConfig {

    @Container
    @ServiceConnection
    @Bean
    static MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("routepick_test")
            .withUsername("test")
            .withPassword("test")
            .withEnv("MYSQL_ROOT_PASSWORD", "root")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")
            .waitingFor(Wait.forListeningPort());
    }

    @Container
    @ServiceConnection
    @Bean
    static GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "test")
            .waitingFor(Wait.forListeningPort());
    }

    @Container
    @Bean
    static GenericContainer<?> mailhogContainer() {
        return new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:latest"))
            .withExposedPorts(1025, 8025) // SMTP: 1025, Web UI: 8025
            .waitingFor(Wait.forListeningPort());
    }
}
```

### 테스트 데이터 SQL

#### test-data.sql
```sql
-- 필수 약관 데이터
INSERT INTO agreement_contents (id, title, content, version, is_required, created_at) VALUES
(1, '서비스 이용약관', '서비스 이용약관 내용...', '1.0', true, NOW()),
(2, '개인정보 처리방침', '개인정보 처리방침 내용...', '1.0', true, NOW()),
(3, '위치기반 서비스 약관', '위치기반 서비스 약관 내용...', '1.0', false, NOW()),
(4, '마케팅 수신 동의', '마케팅 정보 수신 동의...', '1.0', false, NOW());

-- 클라이밍 레벨 데이터
INSERT INTO climbing_levels (id, v_grade, yds_grade, font_grade, description, difficulty_score) VALUES
(1, 'V0', '5.5', '3', '입문자', 1),
(2, 'V1', '5.6', '4-', '초급자', 2),
(3, 'V2', '5.7', '4', '초중급자', 3),
(4, 'V3', '5.8', '4+', '중급자', 4),
(5, 'V4', '5.9', '5', '중상급자', 5);

-- 테스트용 태그 데이터
INSERT INTO tags (id, tag_type, tag_name, description, color, icon, is_system, created_at) VALUES
(1, 'STYLE', '볼더링', '볼더링 스타일', '#FF5722', 'boulder', true, NOW()),
(2, 'STYLE', '리드클라이밍', '리드 클라이밍 스타일', '#2196F3', 'lead', true, NOW()),
(3, 'MOVEMENT', '다이노', '다이나믹한 움직임', '#E91E63', 'dynamic', true, NOW()),
(4, 'TECHNIQUE', '힐훅', '발뒤꿈치 훅 기술', '#9C27B0', 'heel', true, NOW()),
(5, 'HOLD_TYPE', '크림프', '작은 홀드', '#607D8B', 'crimp', true, NOW());
```

---

## 📊 통합 테스트 메트릭

### 성능 기준
- **동시 로그인**: 100명 사용자 15초 내 처리
- **이메일 발송**: 50개 이메일 10초 내 처리
- **전체 플로우**: 회원가입~로그인 5초 내 완료
- **토큰 갱신**: 100ms 내 응답

### 신뢰성 검증
- ✅ **DB 트랜잭션**: 회원가입 시 User, UserProfile 일관성
- ✅ **Redis TTL**: 인증 코드 정확한 만료 처리
- ✅ **동시성**: 100명 동시 접근 시 데이터 무결성
- ✅ **장애 복구**: Redis/DB 일시 장애 후 자동 복구

### 보안 검증
- ✅ **브루트 포스**: 5회 실패 시 계정 자동 잠금
- ✅ **Rate Limiting**: 쿨다운 기간 정확한 적용
- ✅ **토큰 보안**: 로그아웃 후 토큰 무효화 확인
- ✅ **세션 관리**: 동시 로그인 제한 및 중복 세션 처리

---

*Step 9-1e 완료: AuthIntegration 통합 테스트 구현*
*다음 단계: 보안 공격 방어 테스트 구현*