# E2E 테스트 설계체

## 개요
RoutePickr의 End-to-End 테스트 설계 코드입니다. TestContainers를 활용하여 실제 환경과 동일한 조건에서 전체 시스템을 테스트합니다.

## 기본 테스트 클래스

```java
package com.routepick.e2e;

import com.routepick.RoutePickApplication;
import com.routepick.auth.dto.request.LoginRequestDto;
import com.routepick.auth.dto.request.SignupRequestDto;
import com.routepick.auth.dto.response.AuthResponseDto;
import com.routepick.user.dto.response.UserDto;
import com.routepick.recommendation.dto.response.RouteRecommendationDto;
import com.routepick.gym.dto.response.GymDto;
import com.routepick.route.dto.response.RouteDto;
import com.routepick.climbing.dto.request.ClimbingRecordRequestDto;
import com.routepick.climbing.dto.response.ClimbingRecordDto;
import com.routepick.community.dto.request.PostCreateRequestDto;
import com.routepick.community.dto.response.PostResponseDto;
import com.routepick.payment.dto.request.PaymentRequestDto;
import com.routepick.payment.dto.response.PaymentResponseDto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * RoutePickr E2E 통합 테스트
 * 
 * TestContainers를 사용한 실제 환경 시뮬레이션:
 * - MySQL 8.0 데이터베이스
 * - Redis 7.0 캐시 서버  
 * - MailHog SMTP 서버
 * - 실제 HTTP 클라이언트 통신
 */
@SpringBootTest(
    classes = RoutePickApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@ActiveProfiles("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {

    @LocalServerPort
    private int port;
    
    private TestRestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private String baseUrl;
    
    // TestContainers 네트워크
    static Network network = Network.newNetwork();
    
    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withNetwork(network)
            .withNetworkAliases("mysql")
            .withDatabaseName("routepick_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/init-e2e-test.sql");
    
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7.0"))
            .withNetwork(network)
            .withNetworkAliases("redis")
            .withExposedPorts(6379);
    
    @Container
    static GenericContainer<?> mailhogContainer = new GenericContainer<>(
            DockerImageName.parse("mailhog/mailhog:latest"))
            .withNetwork(network)
            .withNetworkAliases("mailhog")
            .withExposedPorts(1025, 8025);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 데이터베이스 설정
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        
        // Redis 설정
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", redisContainer::getFirstMappedPort);
        
        // 메일 설정
        registry.add("spring.mail.host", mailhogContainer::getHost);
        registry.add("spring.mail.port", mailhogContainer::getMappedPort);
        
        // 테스트 환경 설정
        registry.add("app.test-mode", () -> "true");
        registry.add("logging.level.com.routepick", () -> "DEBUG");
    }
    
    @BeforeEach
    void setUp() {
        restTemplate = new TestRestTemplate();
        objectMapper = new ObjectMapper();
        baseUrl = "http://localhost:" + port;
        
        // 테스트 데이터 초기화
        initializeTestData();
    }
    
    @AfterEach
    void tearDown() {
        // 테스트 후 정리
        cleanupTestData();
    }
    
    @Test
    @DisplayName("[E2E] 신규 사용자 완전한 회원가입-이용 여정")
    void testCompleteNewUserJourney() {
        // ================================================================================================
        // 1. 회원가입
        // ================================================================================================
        
        SignupRequestDto signupRequest = SignupRequestDto.builder()
                .email("newuser@routepick.com")
                .password("SecurePass123!")
                .nickName("신규클라이머")
                .phone("010-1234-5678")
                .birthDate("1990-01-01")
                .gender("MALE")
                .agreeToTerms(true)
                .agreeToPrivacy(true)
                .agreeToMarketing(false)
                .build();
        
        ResponseEntity<AuthResponseDto> signupResponse = restTemplate.postForEntity(
                baseUrl + "/api/auth/signup", signupRequest, AuthResponseDto.class);
        
        assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signupResponse.getBody()).isNotNull();
        assertThat(signupResponse.getBody().getUser().getEmail()).isEqualTo("newuser@routepick.com");
        assertThat(signupResponse.getBody().getAccessToken()).isNotNull();
        
        String accessToken = signupResponse.getBody().getAccessToken();
        Long userId = signupResponse.getBody().getUser().getUserId();
        
        // ================================================================================================
        // 2. 이메일 인증
        // ================================================================================================
        
        // 이메일 인증 코드 발송 확인 (MailHog에서 확인)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String mailhogApiUrl = String.format("http://%s:%d/api/v2/messages", 
                    mailhogContainer.getHost(), mailhogContainer.getMappedPort(8025));
            
            ResponseEntity<String> mailResponse = restTemplate.getForEntity(
                    mailhogApiUrl, String.class);
            assertThat(mailResponse.getBody()).contains("newuser@routepick.com");
            assertThat(mailResponse.getBody()).contains("이메일 인증");
        });
        
        // 인증 코드 추출 및 인증 (테스트 환경에서는 고정값 사용)
        String verificationCode = "123456"; // 테스트용 고정 코드
        
        HttpHeaders headers = createAuthHeaders(accessToken);
        HttpEntity<String> verifyRequest = new HttpEntity<>(headers);
        
        ResponseEntity<String> verifyResponse = restTemplate.exchange(
                baseUrl + "/api/email/verify?email=newuser@routepick.com&code=" + verificationCode,
                HttpMethod.POST, verifyRequest, String.class);
        
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // ================================================================================================
        // 3. 프로필 설정
        // ================================================================================================
        
        String profileUpdateJson = """
                {
                    "nickName": "신규클라이머",
                    "height": 175,
                    "weight": 70,
                    "climbingExperience": "BEGINNER",
                    "preferredDifficulties": ["V1", "V2", "V3"],
                    "bio": "클라이밍을 시작한 새로운 클라이머입니다!"
                }
                """;
        
        HttpEntity<String> profileRequest = new HttpEntity<>(profileUpdateJson, headers);
        
        ResponseEntity<UserDto> profileResponse = restTemplate.exchange(
                baseUrl + "/api/users/" + userId + "/profile",
                HttpMethod.PUT, profileRequest, UserDto.class);
        
        assertThat(profileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profileResponse.getBody().getNickName()).isEqualTo("신규클라이머");
        
        // ================================================================================================
        // 4. 주변 암장 검색
        // ================================================================================================
        
        String gymSearchUrl = String.format("%s/api/gyms/search?latitude=37.5665&longitude=126.9780&radius=10",
                baseUrl);
        
        HttpEntity<String> gymSearchRequest = new HttpEntity<>(headers);
        ResponseEntity<GymDto[]> gymResponse = restTemplate.exchange(
                gymSearchUrl, HttpMethod.GET, gymSearchRequest, GymDto[].class);
        
        assertThat(gymResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(gymResponse.getBody()).isNotEmpty();
        
        GymDto selectedGym = gymResponse.getBody()[0];
        
        // ================================================================================================
        // 5. 루트 추천 받기
        // ================================================================================================
        
        String recommendationUrl = String.format("%s/api/recommendations/personal/%d?gymId=%d",
                baseUrl, userId, selectedGym.getGymId());
        
        ResponseEntity<RouteRecommendationDto[]> recommendationResponse = restTemplate.exchange(
                recommendationUrl, HttpMethod.GET, 
                new HttpEntity<>(headers), RouteRecommendationDto[].class);
        
        assertThat(recommendationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recommendationResponse.getBody()).hasSizeGreaterThan(0);
        
        RouteRecommendationDto recommendedRoute = recommendationResponse.getBody()[0];
        
        // ================================================================================================
        // 6. 루트 상세 정보 조회
        // ================================================================================================
        
        ResponseEntity<RouteDto> routeDetailResponse = restTemplate.exchange(
                baseUrl + "/api/routes/" + recommendedRoute.getRouteId(),
                HttpMethod.GET, new HttpEntity<>(headers), RouteDto.class);
        
        assertThat(routeDetailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(routeDetailResponse.getBody().getTags()).isNotEmpty();
        
        // ================================================================================================
        // 7. 클라이밍 시도 기록
        // ================================================================================================
        
        String climbingRecordJson = String.format("""
                {
                    "routeId": %d,
                    "isCompleted": true,
                    "attempts": 3,
                    "climbingDate": "%s",
                    "notes": "첫 번째 완등! 정말 재미있는 루트였습니다.",
                    "difficulty": "V2"
                }
                """, recommendedRoute.getRouteId(), LocalDateTime.now().toString());
        
        HttpEntity<String> climbingRequest = new HttpEntity<>(climbingRecordJson, headers);
        
        ResponseEntity<ClimbingRecordDto> climbingResponse = restTemplate.exchange(
                baseUrl + "/api/climbing/records",
                HttpMethod.POST, climbingRequest, ClimbingRecordDto.class);
        
        assertThat(climbingResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(climbingResponse.getBody().getIsCompleted()).isTrue();
        
        // ================================================================================================
        // 8. 커뮤니티 포스팅
        // ================================================================================================
        
        String postJson = String.format("""
                {
                    "title": "첫 완등 성공!",
                    "content": "오늘 첫 번째 V2 루트를 완등했습니다! 앱 추천이 정말 좋네요.",
                    "routeId": %d,
                    "isPublic": true
                }
                """, recommendedRoute.getRouteId());
        
        HttpEntity<String> postRequest = new HttpEntity<>(postJson, headers);
        
        ResponseEntity<PostResponseDto> postResponse = restTemplate.exchange(
                baseUrl + "/api/community/posts",
                HttpMethod.POST, postRequest, PostResponseDto.class);
        
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postResponse.getBody().getTitle()).isEqualTo("첫 완등 성공!");
        
        // ================================================================================================
        // 9. 사용자 통계 확인
        // ================================================================================================
        
        ResponseEntity<String> statsResponse = restTemplate.exchange(
                baseUrl + "/api/users/" + userId + "/statistics",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        
        assertThat(statsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statsResponse.getBody()).contains("\"totalClimbs\":1");
        assertThat(statsResponse.getBody()).contains("\"completedRoutes\":1");
        
        System.out.println("✅ 신규 사용자 완전한 여정 테스트 성공!");
    }
    
    @Test
    @DisplayName("[E2E] 프리미엄 구독 및 결제 전체 플로우")
    void testPremiumSubscriptionFlow() {
        // ================================================================================================
        // 1. 기본 사용자 생성 및 로그인
        // ================================================================================================
        
        AuthResponseDto auth = createAndLoginTestUser("premium@routepick.com", "프리미엄후보");
        String accessToken = auth.getAccessToken();
        Long userId = auth.getUser().getUserId();
        
        HttpHeaders headers = createAuthHeaders(accessToken);
        
        // ================================================================================================
        // 2. 무료 사용자 제한 확인
        // ================================================================================================
        
        ResponseEntity<RouteRecommendationDto[]> freeRecommendations = restTemplate.exchange(
                baseUrl + "/api/recommendations/daily/" + userId,
                HttpMethod.GET, new HttpEntity<>(headers), RouteRecommendationDto[].class);
        
        assertThat(freeRecommendations.getBody().length).isLessThanOrEqualTo(10);
        
        // ================================================================================================
        // 3. 구독 상품 정보 조회
        // ================================================================================================
        
        ResponseEntity<String> plansResponse = restTemplate.exchange(
                baseUrl + "/api/payment/subscription/plans",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        
        assertThat(plansResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(plansResponse.getBody()).contains("MONTHLY");
        
        // ================================================================================================
        // 4. 결제 처리 (테스트 환경에서는 Mock)
        // ================================================================================================
        
        String paymentRequestJson = """
                {
                    "planType": "MONTHLY",
                    "paymentMethod": "CARD",
                    "cardNumber": "4111111111111111",
                    "expiryMonth": "12",
                    "expiryYear": "2025",
                    "cvv": "123",
                    "autoRenewal": true
                }
                """;
        
        HttpEntity<String> paymentRequest = new HttpEntity<>(paymentRequestJson, headers);
        
        ResponseEntity<PaymentResponseDto> paymentResponse = restTemplate.exchange(
                baseUrl + "/api/payment/subscription/subscribe",
                HttpMethod.POST, paymentRequest, PaymentResponseDto.class);
        
        assertThat(paymentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentResponse.getBody().getStatus()).isEqualTo("COMPLETED");
        
        // ================================================================================================
        // 5. 프리미엄 권한 즉시 적용 확인
        // ================================================================================================
        
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ResponseEntity<UserDto> userResponse = restTemplate.exchange(
                    baseUrl + "/api/users/" + userId,
                    HttpMethod.GET, new HttpEntity<>(headers), UserDto.class);
            
            assertThat(userResponse.getBody().getSubscriptionType()).isEqualTo("PREMIUM");
        });
        
        // ================================================================================================
        // 6. 프리미엄 기능 사용 - 무제한 추천
        // ================================================================================================
        
        ResponseEntity<RouteRecommendationDto[]> premiumRecommendations = restTemplate.exchange(
                baseUrl + "/api/recommendations/premium/" + userId,
                HttpMethod.GET, new HttpEntity<>(headers), RouteRecommendationDto[].class);
        
        assertThat(premiumRecommendations.getBody().length).isGreaterThan(10);
        
        // ================================================================================================
        // 7. 고급 분석 리포트 조회
        // ================================================================================================
        
        ResponseEntity<String> analyticsResponse = restTemplate.exchange(
                baseUrl + "/api/users/" + userId + "/analytics/premium",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        
        assertThat(analyticsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(analyticsResponse.getBody()).contains("progressTrend");
        
        System.out.println("✅ 프리미엄 구독 전체 플로우 테스트 성공!");
    }
    
    @Test
    @DisplayName("[E2E] 동시 사용자 성능 테스트 (100명)")
    void testConcurrentUsers() throws Exception {
        int concurrentUsers = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrentUsers];
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < concurrentUsers; i++) {
            final int userIndex = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    simulateUserSession(userIndex);
                } catch (Exception e) {
                    System.err.printf("사용자 %d 세션 실패: %s%n", userIndex, e.getMessage());
                    throw new RuntimeException(e);
                }
            }, executor);
        }
        
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures);
        allOf.get(60, TimeUnit.SECONDS); // 최대 60초 대기
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.printf("✅ %d명 동시 사용자 테스트 완료: %dms 소요%n", concurrentUsers, duration);
        
        // 성능 기준 검증 (1분 이내 완료)
        assertThat(duration).isLessThan(60000);
        
        executor.shutdown();
    }
    
    @Test
    @DisplayName("[E2E] 데이터 일관성 및 트랜잭션 검증")
    void testDataConsistencyAndTransactions() {
        // ================================================================================================
        // 1. 두 사용자 생성
        // ================================================================================================
        
        AuthResponseDto user1 = createAndLoginTestUser("consistency1@test.com", "일관성테스트1");
        AuthResponseDto user2 = createAndLoginTestUser("consistency2@test.com", "일관성테스트2");
        
        // ================================================================================================
        // 2. 테스트 루트 생성
        // ================================================================================================
        
        RouteDto testRoute = createTestRoute();
        
        // ================================================================================================
        // 3. 동시에 같은 루트에 대한 작업 수행
        // ================================================================================================
        
        CompletableFuture<Void> user1Actions = CompletableFuture.runAsync(() -> {
            try {
                HttpHeaders headers = createAuthHeaders(user1.getAccessToken());
                
                // 루트 북마크
                restTemplate.exchange(
                        baseUrl + "/api/routes/" + testRoute.getRouteId() + "/bookmark",
                        HttpMethod.POST, new HttpEntity<>(headers), String.class);
                
                // 댓글 작성
                String commentJson = """
                        {
                            "content": "정말 좋은 루트네요!",
                            "rating": 5
                        }
                        """;
                HttpEntity<String> commentRequest = new HttpEntity<>(commentJson, headers);
                restTemplate.exchange(
                        baseUrl + "/api/routes/" + testRoute.getRouteId() + "/comments",
                        HttpMethod.POST, commentRequest, String.class);
                
            } catch (Exception e) {
                throw new RuntimeException("User1 작업 실패", e);
            }
        });
        
        CompletableFuture<Void> user2Actions = CompletableFuture.runAsync(() -> {
            try {
                HttpHeaders headers = createAuthHeaders(user2.getAccessToken());
                
                // 클라이밍 기록
                String recordJson = String.format("""
                        {
                            "routeId": %d,
                            "isCompleted": true,
                            "attempts": 2,
                            "climbingDate": "%s"
                        }
                        """, testRoute.getRouteId(), LocalDateTime.now().toString());
                
                HttpEntity<String> recordRequest = new HttpEntity<>(recordJson, headers);
                restTemplate.exchange(
                        baseUrl + "/api/climbing/records",
                        HttpMethod.POST, recordRequest, String.class);
                
                // 난이도 투표
                String voteJson = """
                        {
                            "suggestedDifficulty": "V3",
                            "reason": "원래 등급보다 쉬운 것 같습니다"
                        }
                        """;
                HttpEntity<String> voteRequest = new HttpEntity<>(voteJson, headers);
                restTemplate.exchange(
                        baseUrl + "/api/routes/" + testRoute.getRouteId() + "/vote",
                        HttpMethod.POST, voteRequest, String.class);
                
            } catch (Exception e) {
                throw new RuntimeException("User2 작업 실패", e);
            }
        });
        
        // 두 작업 완료 대기
        CompletableFuture.allOf(user1Actions, user2Actions).join();
        
        // ================================================================================================
        // 4. 데이터 일관성 검증
        // ================================================================================================
        
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ResponseEntity<RouteDto> routeResponse = restTemplate.getForEntity(
                    baseUrl + "/api/routes/" + testRoute.getRouteId(), RouteDto.class);
            
            RouteDto updatedRoute = routeResponse.getBody();
            
            // 각 통계가 정확하게 반영되었는지 확인
            assertThat(updatedRoute.getBookmarkCount()).isEqualTo(1);
            assertThat(updatedRoute.getCommentCount()).isEqualTo(1);  
            assertThat(updatedRoute.getCompletionCount()).isEqualTo(1);
            assertThat(updatedRoute.getVoteCount()).isEqualTo(1);
        });
        
        System.out.println("✅ 데이터 일관성 및 트랜잭션 테스트 성공!");
    }
    
    // ================================================================================================
    // Helper Methods
    // ================================================================================================
    
    private void initializeTestData() {
        // 테스트 데이터베이스 초기 데이터 설정
        // 기본 암장, 루트, 태그 등 생성
    }
    
    private void cleanupTestData() {
        // 테스트 후 데이터 정리
        // 테스트용 사용자, 포스트 등 삭제
    }
    
    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
    
    private AuthResponseDto createAndLoginTestUser(String email, String nickName) {
        // 테스트 사용자 생성 및 로그인 로직
        SignupRequestDto signupRequest = SignupRequestDto.builder()
                .email(email)
                .password("TestPass123!")
                .nickName(nickName)
                .phone("010-0000-0000")
                .birthDate("1990-01-01")
                .gender("MALE")
                .agreeToTerms(true)
                .agreeToPrivacy(true)
                .build();
        
        ResponseEntity<AuthResponseDto> response = restTemplate.postForEntity(
                baseUrl + "/api/auth/signup", signupRequest, AuthResponseDto.class);
        
        return response.getBody();
    }
    
    private RouteDto createTestRoute() {
        // 테스트용 루트 생성 로직
        // 실제 구현에서는 적절한 루트 생성 API 호출
        return RouteDto.builder()
                .routeId(1L)
                .routeName("테스트 루트")
                .difficulty("V3")
                .build();
    }
    
    private void simulateUserSession(int userIndex) {
        String email = String.format("concurrent_user_%d@test.com", userIndex);
        AuthResponseDto auth = createAndLoginTestUser(email, "동시테스트" + userIndex);
        
        HttpHeaders headers = createAuthHeaders(auth.getAccessToken());
        
        // 1. 루트 추천 조회
        restTemplate.exchange(
                baseUrl + "/api/recommendations/daily/" + auth.getUser().getUserId(),
                HttpMethod.GET, new HttpEntity<>(headers), RouteRecommendationDto[].class);
        
        // 2. 암장 검색
        restTemplate.exchange(
                baseUrl + "/api/gyms/search?latitude=37.5665&longitude=126.9780",
                HttpMethod.GET, new HttpEntity<>(headers), GymDto[].class);
        
        // 3. 클라이밍 기록 (간단한 예시)
        String recordJson = String.format("""
                {
                    "routeId": 1,
                    "isCompleted": %s,
                    "attempts": %d,
                    "climbingDate": "%s"
                }
                """, userIndex % 2 == 0, userIndex % 5 + 1, LocalDateTime.now().toString());
        
        HttpEntity<String> recordRequest = new HttpEntity<>(recordJson, headers);
        restTemplate.exchange(
                baseUrl + "/api/climbing/records",
                HttpMethod.POST, recordRequest, String.class);
    }
}
```

## 특수 시나리오 테스트

### 오류 상황 처리 테스트

```java
@Test
@DisplayName("[E2E] 다양한 오류 상황 처리")
void testErrorScenarios() {
    AuthResponseDto user = createAndLoginTestUser("error@test.com", "오류테스트");
    HttpHeaders headers = createAuthHeaders(user.getAccessToken());
    
    // ================================================================================================
    // 1. 네트워크 타임아웃 시뮬레이션
    // ================================================================================================
    
    // 매우 긴 요청으로 타임아웃 유발
    String longContentJson = """
            {
                "title": "타임아웃 테스트",
                "content": "%s"
            }
            """.formatted("x".repeat(1000000)); // 1MB 콘텐츠
    
    HttpEntity<String> longRequest = new HttpEntity<>(longContentJson, headers);
    
    assertThatThrownBy(() -> {
        restTemplate.exchange(
                baseUrl + "/api/community/posts",
                HttpMethod.POST, longRequest, String.class);
    }).isInstanceOf(Exception.class);
    
    // ================================================================================================
    // 2. 잘못된 인증 토큰
    // ================================================================================================
    
    HttpHeaders invalidHeaders = new HttpHeaders();
    invalidHeaders.setBearerAuth("invalid_token_123");
    
    ResponseEntity<String> unauthorizedResponse = restTemplate.exchange(
            baseUrl + "/api/users/" + user.getUser().getUserId(),
            HttpMethod.GET, new HttpEntity<>(invalidHeaders), String.class);
    
    assertThat(unauthorizedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    
    // ================================================================================================
    // 3. 존재하지 않는 리소스 접근
    // ================================================================================================
    
    ResponseEntity<String> notFoundResponse = restTemplate.exchange(
            baseUrl + "/api/routes/999999",
            HttpMethod.GET, new HttpEntity<>(headers), String.class);
    
    assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    
    // ================================================================================================
    // 4. 잘못된 요청 데이터
    // ================================================================================================
    
    String invalidJson = """
            {
                "email": "invalid-email",
                "password": "",
                "nickName": null
            }
            """;
    
    HttpEntity<String> invalidRequest = new HttpEntity<>(invalidJson, new HttpHeaders());
    
    ResponseEntity<String> badRequestResponse = restTemplate.exchange(
            baseUrl + "/api/auth/signup",
            HttpMethod.POST, invalidRequest, String.class);
    
    assertThat(badRequestResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badRequestResponse.getBody()).contains("validation");
    
    System.out.println("✅ 오류 상황 처리 테스트 성공!");
}
```

### 보안 테스트

```java
@Test
@DisplayName("[E2E] 보안 공격 시나리오 차단")
void testSecurityAttackPrevention() {
    AuthResponseDto user = createAndLoginTestUser("security@test.com", "보안테스트");
    HttpHeaders headers = createAuthHeaders(user.getAccessToken());
    
    // ================================================================================================
    // 1. XSS 공격 시도
    // ================================================================================================
    
    String xssPayload = """
            {
                "title": "정상 제목",
                "content": "<script>alert('XSS')</script>악성 스크립트 포함"
            }
            """;
    
    HttpEntity<String> xssRequest = new HttpEntity<>(xssPayload, headers);
    
    ResponseEntity<String> xssResponse = restTemplate.exchange(
            baseUrl + "/api/community/posts",
            HttpMethod.POST, xssRequest, String.class);
    
    // XSS가 차단되거나 무력화되었는지 확인
    if (xssResponse.getStatusCode().is2xxSuccessful()) {
        // 생성된 포스트에서 스크립트 태그가 제거되었는지 확인
        assertThat(xssResponse.getBody()).doesNotContain("<script>");
        assertThat(xssResponse.getBody()).doesNotContain("alert(");
    } else {
        // 또는 요청 자체가 차단되었는지 확인
        assertThat(xssResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    
    // ================================================================================================
    // 2. SQL 인젝션 시도
    // ================================================================================================
    
    String sqlInjectionPayload = "'; DROP TABLE users; --";
    String searchUrl = baseUrl + "/api/gyms/search?name=" + sqlInjectionPayload;
    
    ResponseEntity<String> sqlResponse = restTemplate.exchange(
            searchUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    
    // SQL 인젝션이 차단되었는지 확인 (정상 응답이거나 에러)
    assertThat(sqlResponse.getStatusCode().value()).isLessThan(500);
    
    // ================================================================================================
    // 3. 권한 우회 시도
    // ================================================================================================
    
    // 다른 사용자의 프로필 수정 시도
    Long otherUserId = 999999L;
    String profileUpdateJson = """
            {
                "nickName": "해킹된계정",
                "bio": "권한을 우회하여 수정"
            }
            """;
    
    HttpEntity<String> privilegeRequest = new HttpEntity<>(profileUpdateJson, headers);
    
    ResponseEntity<String> privilegeResponse = restTemplate.exchange(
            baseUrl + "/api/users/" + otherUserId + "/profile",
            HttpMethod.PUT, privilegeRequest, String.class);
    
    // 권한 없음 또는 찾을 수 없음 오류 확인
    assertThat(privilegeResponse.getStatusCode()).isIn(
            HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED);
    
    // ================================================================================================
    // 4. 과도한 요청 (Rate Limiting)
    // ================================================================================================
    
    int rapidRequests = 50;
    int blockedRequests = 0;
    
    for (int i = 0; i < rapidRequests; i++) {
        ResponseEntity<String> rapidResponse = restTemplate.exchange(
                baseUrl + "/api/recommendations/daily/" + user.getUser().getUserId(),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        
        if (rapidResponse.getStatusCode().equals(HttpStatus.TOO_MANY_REQUESTS)) {
            blockedRequests++;
        }
    }
    
    // Rate limiting이 작동했는지 확인 (일부 요청이 차단되어야 함)
    assertThat(blockedRequests).isGreaterThan(0);
    
    System.out.println("✅ 보안 공격 차단 테스트 성공!");
    System.out.printf("Rate limiting: %d/%d 요청 차단됨%n", blockedRequests, rapidRequests);
}
```

## 실행 및 리포팅

### 테스트 실행 스크립트

```bash
#!/bin/bash
# run-e2e-tests.sh

echo "🚀 RoutePickr E2E 테스트 시작"

# Docker 환경 정리 및 재시작
echo "🧹 기존 컨테이너 정리..."
docker-compose -f docker-compose.e2e.yml down -v
docker system prune -f

# 새 환경 구성
echo "🐳 Docker 환경 구성..."
docker-compose -f docker-compose.e2e.yml up -d --build

# 서비스 준비 대기
echo "⏳ 서비스 준비 대기..."
./wait-for-services.sh

# E2E 테스트 실행
echo "🧪 E2E 테스트 실행..."
./gradlew clean test --tests="*EndToEndTest*" \
    -Dspring.profiles.active=e2e \
    --continue \
    --parallel \
    2>&1 | tee e2e-test-output.log

TEST_RESULT=$?

# 결과 리포트 생성
echo "📊 테스트 결과 분석..."
./generate-e2e-report.sh

# 정리
echo "🧹 테스트 환경 정리..."
docker-compose -f docker-compose.e2e.yml down

if [ $TEST_RESULT -eq 0 ]; then
    echo "✅ E2E 테스트 모두 통과!"
    echo "📄 상세 리포트: build/reports/tests/test/index.html"
else
    echo "❌ 일부 E2E 테스트 실패"
    echo "🔍 로그 확인: e2e-test-output.log"
    exit 1
fi
```

### 성능 메트릭 수집

```java
@Component
public class E2EMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    public E2EMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    public void recordTestExecution(String testName, long duration, boolean success) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("e2e.test.duration")
                .tag("test", testName)
                .tag("success", String.valueOf(success))
                .register(meterRegistry));
        
        Counter.builder("e2e.test.count")
                .tag("test", testName)
                .tag("result", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }
    
    public void recordApiCall(String endpoint, long responseTime, int statusCode) {
        Timer.builder("e2e.api.response.time")
                .tag("endpoint", endpoint)
                .tag("status", String.valueOf(statusCode))
                .register(meterRegistry)
                .record(responseTime, TimeUnit.MILLISECONDS);
    }
}
```

이 E2E 테스트 설계체는 실제 운영 환경과 최대한 유사한 조건에서 전체 시스템을 검증하여 배포 전 품질을 보장합니다.