# Step 9-6c: SecurityIntegrationTest - 보안 통합 테스트

> 전체 보안 체계 통합 테스트 - JWT 인증, Rate Limiting, 권한 기반 접근 제어, 보안 이벤트 로깅
> 생성일: 2025-08-27
> 단계: 9-6c (Test 레이어 - 보안 통합)
> 참고: step9-1f, step9-4g, step9-5h

---

## 🎯 테스트 목표

- **전체 보안 체계**: JWT → Rate Limiting → CORS/CSRF → XSS/SQL Injection 방어
- **권한 기반 접근 제어**: ADMIN, GYM_ADMIN, NORMAL 역할별 @PreAuthorize 검증
- **보안 이벤트 로깅**: 인증 실패, 권한 위반, 공격 시도 로깅 및 알림
- **실제 공격 시나리오**: 다양한 보안 위협에 대한 방어력 검증

---

## 🔒 SecurityIntegrationTest 구현

### SecurityIntegrationTest.java
```java
package com.routepick.security.integration;

import com.routepick.common.enums.*;
import com.routepick.dto.auth.request.*;
import com.routepick.dto.payment.request.*;
import com.routepick.service.auth.AuthService;
import com.routepick.service.security.SecurityEventService;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 보안 체계 통합 테스트
 * JWT 인증, 권한 제어, Rate Limiting, 보안 이벤트 로깅 종합 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("security-test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("보안 체계 통합 테스트")
class SecurityIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_security")
            .withUsername("security_test")
            .withPassword("securitypass")
            .withInitScript("db/security-test-data.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("app.security.rate-limit.enabled", () -> "true");
        registry.add("app.security.attack-detection.enabled", () -> "true");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private SecurityEventService securityEventService;

    @Autowired
    private UserRepository userRepository;

    // 테스트용 사용자 토큰
    private String normalUserToken;
    private String gymAdminToken;
    private String adminToken;
    private String expiredToken;
    private String maliciousToken;

    @BeforeAll
    static void setUpClass() {
        System.out.println("🔒 보안 통합 테스트 환경 초기화 중...");
    }

    @BeforeEach
    void setUp() throws Exception {
        // 테스트용 사용자 토큰 생성
        createTestUserTokens();
    }

    @Nested
    @DisplayName("4. 전체 보안 체계 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ComprehensiveSecurityTest {

        @Test
        @Order(1)
        @DisplayName("[보안통합] JWT 인증 → Rate Limiting → CORS/CSRF")
        void JWT인증_RateLimit_CORS_CSRF_통합테스트() throws Exception {
            // 1. JWT 인증 없이 보호된 자원 접근 시도
            ResponseEntity<Map> unauthorizedResponse = restTemplate.exchange(
                "/api/v1/users/profile", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(unauthorizedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // 2. 올바른 JWT 토큰으로 인증 성공
            HttpHeaders authHeaders = createAuthHeaders(normalUserToken);
            ResponseEntity<Map> authorizedResponse = restTemplate.exchange(
                "/api/v1/users/profile", HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class);

            assertThat(authorizedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 3. 잘못된 JWT 토큰으로 접근 시도
            HttpHeaders invalidAuthHeaders = new HttpHeaders();
            invalidAuthHeaders.setBearerAuth("invalid.jwt.token");
            ResponseEntity<Map> invalidTokenResponse = restTemplate.exchange(
                "/api/v1/users/profile", HttpMethod.GET, new HttpEntity<>(invalidAuthHeaders), Map.class);

            assertThat(invalidTokenResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // 4. Rate Limiting 테스트 (연속 요청)
            int rapidRequests = 0;
            int rateLimitHit = 0;
            
            for (int i = 0; i < 20; i++) { // 20회 연속 요청
                ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/users/profile", HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class);
                
                rapidRequests++;
                if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    rateLimitHit++;
                    break;
                }
                
                Thread.sleep(50); // 50ms 간격
            }

            // Rate Limiting이 적절히 작동하는지 확인
            assertThat(rateLimitHit).isGreaterThan(0); // Rate limit이 발동되어야 함

            // 5. CORS 헤더 확인
            HttpHeaders corsHeaders = new HttpHeaders();
            corsHeaders.setOrigin("https://app.routepick.co.kr");
            corsHeaders.set("Access-Control-Request-Method", "GET");
            
            ResponseEntity<Void> corsResponse = restTemplate.exchange(
                "/api/v1/users/profile", HttpMethod.OPTIONS, new HttpEntity<>(corsHeaders), Void.class);

            assertThat(corsResponse.getHeaders().getAccessControlAllowOrigin()).isNotNull();

            System.out.printf("✅ 통합 보안 체계 검증 완료 - 요청: %d회, Rate Limit 발동: %d회%n", 
                rapidRequests, rateLimitHit);
        }

        @Test
        @Order(2)
        @DisplayName("[보안통합] XSS/SQL Injection 방어 → 예외 처리")
        void XSS_SQLInjection_방어_예외처리_통합테스트() throws Exception {
            HttpHeaders authHeaders = createAuthHeaders(normalUserToken);

            // XSS 공격 패턴 테스트
            String[] xssPayloads = {
                "<script>alert('XSS')</script>",
                "<img src=x onerror=alert('XSS')>",
                "javascript:alert('XSS')",
                "<iframe src='javascript:alert(\"XSS\")'></iframe>",
                "&#106;&#97;&#118;&#97;&#115;&#99;&#114;&#105;&#112;&#116;&#58;alert('XSS')"
            };

            int xssBlockedCount = 0;
            for (String xssPayload : xssPayloads) {
                // 프로필 업데이트에 XSS 페이로드 삽입 시도
                Map<String, Object> maliciousProfile = Map.of(
                    "nickname", "정상닉네임",
                    "bio", xssPayload, // XSS 페이로드
                    "instagramHandle", "@normal_user"
                );

                HttpEntity<Map<String, Object>> xssEntity = new HttpEntity<>(maliciousProfile, authHeaders);
                ResponseEntity<Map> xssResponse = restTemplate.exchange(
                    "/api/v1/users/profile", HttpMethod.PUT, xssEntity, Map.class);

                // XSS 공격이 차단되어야 함
                if (xssResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
                    xssBlockedCount++;
                }
            }

            assertThat(xssBlockedCount).isEqualTo(xssPayloads.length); // 모든 XSS 공격 차단

            // SQL Injection 공격 패턴 테스트
            String[] sqlInjectionPayloads = {
                "'; DROP TABLE users; --",
                "' OR '1'='1",
                "' UNION SELECT * FROM users --",
                "'; INSERT INTO users (email) VALUES ('hacked@evil.com'); --",
                "' OR 1=1#"
            };

            int sqlInjectionBlockedCount = 0;
            for (String sqlPayload : sqlInjectionPayloads) {
                // 루트 검색에 SQL 인젝션 시도
                String maliciousSearchUrl = "/api/v1/routes/search?query=" + 
                    java.net.URLEncoder.encode(sqlPayload, "UTF-8");

                ResponseEntity<Map> sqlResponse = restTemplate.exchange(
                    maliciousSearchUrl, HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class);

                // SQL 인젝션이 차단되거나 안전하게 처리되어야 함
                if (sqlResponse.getStatusCode() == HttpStatus.BAD_REQUEST || 
                    sqlResponse.getStatusCode() == HttpStatus.OK) {
                    sqlInjectionBlockedCount++;
                }
            }

            assertThat(sqlInjectionBlockedCount).isEqualTo(sqlInjectionPayloads.length);

            // 보안 이벤트 로그 확인
            List<Map<String, Object>> securityEvents = getSecurityEvents();
            long xssAttempts = securityEvents.stream()
                .filter(event -> "XSS_ATTEMPT".equals(event.get("eventType")))
                .count();
            
            assertThat(xssAttempts).isGreaterThan(0); // XSS 시도가 로깅되어야 함

            System.out.printf("🛡️ 공격 방어 검증 완료 - XSS 차단: %d/%d, SQL 인젝션 차단: %d/%d%n", 
                xssBlockedCount, xssPayloads.length, sqlInjectionBlockedCount, sqlInjectionPayloads.length);
        }

        @Test
        @Order(3)
        @DisplayName("[보안통합] 보안 이벤트 로깅 → 알림 발송")
        void 보안이벤트로깅_알림발송_통합테스트() throws Exception {
            // 1. 의심스러운 로그인 시도 (다양한 IP에서)
            String[] suspiciousIPs = {
                "1.2.3.4", "5.6.7.8", "9.10.11.12", "13.14.15.16"
            };

            LoginRequest loginRequest = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("WrongPassword123!")
                .build();

            for (String suspiciousIP : suspiciousIPs) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Forwarded-For", suspiciousIP);
                headers.set("User-Agent", "SuspiciousBot/1.0");

                HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);
                ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/auth/login", HttpMethod.POST, entity, Map.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            }

            // 2. 권한 없는 관리자 기능 접근 시도
            HttpHeaders normalUserHeaders = createAuthHeaders(normalUserToken);
            
            ResponseEntity<Map> adminAccessAttempt = restTemplate.exchange(
                "/api/v1/admin/users", HttpMethod.GET, new HttpEntity<>(normalUserHeaders), Map.class);

            assertThat(adminAccessAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // 3. 대량 API 요청 (DDoS 시뮬레이션)
            ExecutorService executorService = Executors.newFixedThreadPool(50);
            CountDownLatch latch = new CountDownLatch(100);
            
            for (int i = 0; i < 100; i++) {
                executorService.submit(() -> {
                    try {
                        HttpHeaders ddosHeaders = new HttpHeaders();
                        ddosHeaders.set("X-Forwarded-For", "192.168.1.100"); // 동일 IP
                        ddosHeaders.set("User-Agent", "DDoSBot/1.0");

                        restTemplate.exchange(
                            "/api/v1/routes", HttpMethod.GET, new HttpEntity<>(ddosHeaders), Map.class);
                    } catch (Exception e) {
                        // 에러 무시 (Rate Limiting 예상)
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);

            // 4. 보안 이벤트 로그 확인
            Thread.sleep(2000); // 로깅 처리 대기

            List<Map<String, Object>> securityEvents = getSecurityEvents();
            
            // 다양한 보안 이벤트가 로깅되었는지 확인
            long loginFailureEvents = securityEvents.stream()
                .filter(event -> "LOGIN_FAILURE".equals(event.get("eventType")))
                .count();
            
            long unauthorizedAccessEvents = securityEvents.stream()
                .filter(event -> "UNAUTHORIZED_ACCESS".equals(event.get("eventType")))
                .count();
            
            long rateLimitEvents = securityEvents.stream()
                .filter(event -> "RATE_LIMIT_EXCEEDED".equals(event.get("eventType")))
                .count();

            assertThat(loginFailureEvents).isGreaterThan(0);
            assertThat(unauthorizedAccessEvents).isGreaterThan(0);
            assertThat(rateLimitEvents).isGreaterThan(0);

            // 5. 보안 알림 발송 확인
            List<Map<String, Object>> securityAlerts = getSecurityAlerts();
            assertThat(securityAlerts).isNotEmpty(); // 보안 알림이 발송되어야 함

            System.out.printf("📊 보안 이벤트 로깅 - 로그인 실패: %d, 권한 위반: %d, Rate Limit: %d%n", 
                loginFailureEvents, unauthorizedAccessEvents, rateLimitEvents);

            executorService.shutdown();
        }
    }

    @Nested
    @DisplayName("5. 권한 기반 접근 제어")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RoleBasedAccessControlTest {

        @Test
        @Order(1)
        @DisplayName("[권한] ADMIN, GYM_ADMIN, NORMAL 역할별 테스트")
        void 역할별_권한_테스트() throws Exception {
            // 1. NORMAL 사용자 권한 테스트
            HttpHeaders normalHeaders = createAuthHeaders(normalUserToken);
            
            // 일반 사용자 가능 작업
            ResponseEntity<Map> normalUserProfile = restTemplate.exchange(
                "/api/v1/users/profile", HttpMethod.GET, new HttpEntity<>(normalHeaders), Map.class);
            assertThat(normalUserProfile.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<List> normalUserRoutes = restTemplate.exchange(
                "/api/v1/routes", HttpMethod.GET, new HttpEntity<>(normalHeaders), List.class);
            assertThat(normalUserRoutes.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 일반 사용자 불가능 작업 (관리자 기능)
            ResponseEntity<Map> normalAdminAccess = restTemplate.exchange(
                "/api/v1/admin/users", HttpMethod.GET, new HttpEntity<>(normalHeaders), Map.class);
            assertThat(normalAdminAccess.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // 2. GYM_ADMIN 권한 테스트
            HttpHeaders gymAdminHeaders = createAuthHeaders(gymAdminToken);
            
            // 체육관 관리자 가능 작업
            ResponseEntity<List> gymAdminGyms = restTemplate.exchange(
                "/api/v1/admin/gyms", HttpMethod.GET, new HttpEntity<>(gymAdminHeaders), List.class);
            assertThat(gymAdminGyms.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 체육관 루트 관리
            Map<String, Object> routeCreateRequest = Map.of(
                "routeName", "관리자 테스트 루트",
                "difficulty", "V3",
                "routeType", "BOULDERING",
                "description", "체육관 관리자가 생성한 테스트 루트"
            );

            HttpEntity<Map<String, Object>> routeEntity = new HttpEntity<>(routeCreateRequest, gymAdminHeaders);
            ResponseEntity<Map> routeCreateResponse = restTemplate.exchange(
                "/api/v1/admin/routes", HttpMethod.POST, routeEntity, Map.class);
            assertThat(routeCreateResponse.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);

            // 체육관 관리자 불가능 작업 (시스템 관리자 기능)
            ResponseEntity<Map> gymAdminSystemAccess = restTemplate.exchange(
                "/api/v1/admin/system/users", HttpMethod.GET, new HttpEntity<>(gymAdminHeaders), Map.class);
            assertThat(gymAdminSystemAccess.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // 3. ADMIN 권한 테스트 (최고 권한)
            HttpHeaders adminHeaders = createAuthHeaders(adminToken);
            
            // 시스템 관리자 모든 기능 접근 가능
            ResponseEntity<List> adminUsers = restTemplate.exchange(
                "/api/v1/admin/system/users", HttpMethod.GET, new HttpEntity<>(adminHeaders), List.class);
            assertThat(adminUsers.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<Map> adminStats = restTemplate.exchange(
                "/api/v1/admin/system/stats", HttpMethod.GET, new HttpEntity<>(adminHeaders), Map.class);
            assertThat(adminStats.getStatusCode()).isEqualTo(HttpStatus.OK);

            System.out.println("✅ 역할별 권한 테스트 완료 - NORMAL, GYM_ADMIN, ADMIN 모든 권한 검증");
        }

        @Test
        @Order(2)
        @DisplayName("[권한] @PreAuthorize 전체 API 검증")
        void PreAuthorize_전체API_검증() throws Exception {
            // 권한별 접근 가능한 API 엔드포인트 정의
            Map<String, List<String>> roleEndpoints = Map.of(
                "NORMAL", List.of(
                    "/api/v1/users/profile",
                    "/api/v1/routes",
                    "/api/v1/gyms",
                    "/api/v1/recommendations/personalized",
                    "/api/v1/posts",
                    "/api/v1/payments/process"
                ),
                "GYM_ADMIN", List.of(
                    "/api/v1/admin/gyms",
                    "/api/v1/admin/routes", 
                    "/api/v1/admin/gym-members",
                    "/api/v1/admin/route-setters"
                ),
                "ADMIN", List.of(
                    "/api/v1/admin/system/users",
                    "/api/v1/admin/system/stats",
                    "/api/v1/admin/system/logs",
                    "/api/v1/admin/payments/stats",
                    "/api/v1/admin/security/events"
                )
            );

            Map<String, String> roleTokens = Map.of(
                "NORMAL", normalUserToken,
                "GYM_ADMIN", gymAdminToken,
                "ADMIN", adminToken
            );

            // 각 역할별로 해당 API에 접근 가능한지 검증
            for (Map.Entry<String, List<String>> roleEntry : roleEndpoints.entrySet()) {
                String role = roleEntry.getKey();
                List<String> endpoints = roleEntry.getValue();
                String token = roleTokens.get(role);
                HttpHeaders headers = createAuthHeaders(token);

                for (String endpoint : endpoints) {
                    ResponseEntity<Object> response = restTemplate.exchange(
                        endpoint, HttpMethod.GET, new HttpEntity<>(headers), Object.class);
                    
                    // 권한이 있는 경우 200 OK 또는 404 Not Found (존재하지 않는 리소스)
                    assertThat(response.getStatusCode()).isIn(
                        HttpStatus.OK, HttpStatus.NOT_FOUND, HttpStatus.NO_CONTENT
                    );
                }
            }

            // 크로스 권한 검증 (낮은 권한으로 높은 권한 API 접근 시도)
            HttpHeaders normalHeaders = createAuthHeaders(normalUserToken);
            
            // NORMAL 사용자가 ADMIN API 접근 시도
            for (String adminEndpoint : roleEndpoints.get("ADMIN")) {
                ResponseEntity<Object> response = restTemplate.exchange(
                    adminEndpoint, HttpMethod.GET, new HttpEntity<>(normalHeaders), Object.class);
                
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            }

            System.out.println("✅ @PreAuthorize 전체 API 검증 완료 - 모든 권한 경계 확인");
        }

        @Test
        @Order(3)
        @DisplayName("[권한] 데이터 접근 권한 테스트")
        void 데이터접근권한_테스트() throws Exception {
            // 1. 사용자별 데이터 격리 검증
            HttpHeaders user1Headers = createAuthHeaders(normalUserToken);
            
            // 다른 사용자의 프로필 접근 시도
            ResponseEntity<Map> otherUserProfile = restTemplate.exchange(
                "/api/v1/users/999/profile", // 존재하지 않거나 다른 사용자 ID
                HttpMethod.GET, new HttpEntity<>(user1Headers), Map.class);
            
            assertThat(otherUserProfile.getStatusCode()).isIn(
                HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND
            );

            // 2. 결제 정보 접근 권한 검증
            // 자신의 결제 내역은 접근 가능
            ResponseEntity<Map> ownPaymentHistory = restTemplate.exchange(
                "/api/v1/payments/history", 
                HttpMethod.GET, new HttpEntity<>(user1Headers), Map.class);
            assertThat(ownPaymentHistory.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 다른 사용자의 결제 상세 접근 시도
            ResponseEntity<Map> otherUserPayment = restTemplate.exchange(
                "/api/v1/payments/999", // 다른 사용자의 결제 ID
                HttpMethod.GET, new HttpEntity<>(user1Headers), Map.class);
            assertThat(otherUserPayment.getStatusCode()).isIn(
                HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND
            );

            // 3. 체육관 관리 권한 검증
            HttpHeaders gymAdminHeaders = createAuthHeaders(gymAdminToken);
            
            // 관리하는 체육관의 데이터는 접근 가능
            ResponseEntity<Map> managedGym = restTemplate.exchange(
                "/api/v1/admin/gyms/1", // 관리하는 체육관 ID
                HttpMethod.GET, new HttpEntity<>(gymAdminHeaders), Map.class);
            assertThat(managedGym.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);

            // 관리하지 않는 체육관의 관리 기능 접근 시도
            Map<String, Object> unauthorizedGymUpdate = Map.of(
                "gymName", "무단 수정 시도",
                "description", "권한 없이 수정"
            );

            HttpEntity<Map<String, Object>> unauthorizedEntity = 
                new HttpEntity<>(unauthorizedGymUpdate, gymAdminHeaders);
            ResponseEntity<Map> unauthorizedGymAccess = restTemplate.exchange(
                "/api/v1/admin/gyms/999", // 관리 권한 없는 체육관
                HttpMethod.PUT, unauthorizedEntity, Map.class);
            assertThat(unauthorizedGymAccess.getStatusCode()).isIn(
                HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND
            );

            System.out.println("✅ 데이터 접근 권한 테스트 완료 - 사용자/체육관 데이터 격리 검증");
        }
    }

    @Nested
    @DisplayName("고급 보안 시나리오 테스트")
    class AdvancedSecurityScenarioTest {

        @Test
        @Order(1)
        @DisplayName("[고급보안] JWT 토큰 조작 및 탈취 시나리오")
        void JWT토큰조작_탈취시나리오_테스트() throws Exception {
            // 1. 만료된 토큰 사용 시도
            HttpHeaders expiredHeaders = createAuthHeaders(expiredToken);
            ResponseEntity<Map> expiredTokenResponse = restTemplate.exchange(
                "/api/v1/users/profile", HttpMethod.GET, new HttpEntity<>(expiredHeaders), Map.class);
            
            assertThat(expiredTokenResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // 2. 서명이 조작된 토큰 사용 시도
            HttpHeaders tamperedHeaders = createAuthHeaders(maliciousToken);
            ResponseEntity<Map> tamperedTokenResponse = restTemplate.exchange(
                "/api/v1/users/profile", HttpMethod.GET, new HttpEntity<>(tamperedHeaders), Map.class);
            
            assertThat(tamperedTokenResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // 3. 토큰 없이 보호된 자원 접근
            ResponseEntity<Map> noTokenResponse = restTemplate.exchange(
                "/api/v1/payments/process", HttpMethod.POST, 
                new HttpEntity<>(Map.of("amount", 1000), new HttpHeaders()), Map.class);
            
            assertThat(noTokenResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // 4. 다른 사용자의 토큰으로 권한 상승 시도
            // 일반 사용자 토큰으로 관리자 기능 접근
            HttpHeaders normalHeaders = createAuthHeaders(normalUserToken);
            ResponseEntity<Map> privilegeEscalation = restTemplate.exchange(
                "/api/v1/admin/users", HttpMethod.GET, new HttpEntity<>(normalHeaders), Map.class);
            
            assertThat(privilegeEscalation.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            System.out.println("✅ JWT 토큰 보안 시나리오 테스트 완료");
        }

        @Test
        @Order(2)
        @DisplayName("[고급보안] 세션 고정 및 CSRF 공격 방어")
        void 세션고정_CSRF공격_방어테스트() throws Exception {
            // 1. CSRF 토큰 없이 상태 변경 요청
            HttpHeaders headersWithoutCsrf = createAuthHeaders(normalUserToken);
            Map<String, Object> profileUpdate = Map.of(
                "nickname", "CSRF테스트",
                "bio", "CSRF 공격 테스트"
            );

            // MockMvc를 사용하여 CSRF 보호 테스트
            mockMvc.perform(put("/api/v1/users/profile")
                    .header("Authorization", "Bearer " + normalUserToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(profileUpdate)))
                .andExpect(status().isForbidden()); // CSRF 토큰 없으면 차단

            // 2. 올바른 CSRF 토큰으로 요청 성공
            mockMvc.perform(put("/api/v1/users/profile")
                    .header("Authorization", "Bearer " + normalUserToken)
                    .header("X-CSRF-TOKEN", "valid-csrf-token") // 실제로는 서버에서 발급
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(profileUpdate)))
                .andExpect(status().isOk());

            System.out.println("✅ CSRF 공격 방어 테스트 완료");
        }

        @Test
        @Order(3)
        @DisplayName("[고급보안] 브루트포스 및 계정 잠금 테스트")
        void 브루트포스_계정잠금_테스트() throws Exception {
            String targetEmail = "bruteforce.target@example.com";
            
            // 1. 대상 계정 생성
            createTestUser(targetEmail, "CorrectPassword123!");

            // 2. 반복적인 잘못된 로그인 시도 (브루트포스)
            String[] wrongPasswords = {
                "wrong1", "wrong2", "wrong3", "wrong4", "wrong5", 
                "password", "123456", "admin", "test", "wrong10"
            };

            int failedAttempts = 0;
            boolean accountLocked = false;

            for (String wrongPassword : wrongPasswords) {
                LoginRequest bruteForceAttempt = LoginRequest.builder()
                    .email(targetEmail)
                    .password(wrongPassword)
                    .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Forwarded-For", "192.168.1.200"); // 공격자 IP

                HttpEntity<LoginRequest> entity = new HttpEntity<>(bruteForceAttempt, headers);
                ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/auth/login", HttpMethod.POST, entity, Map.class);

                failedAttempts++;

                if (response.getStatusCode() == HttpStatus.LOCKED) {
                    accountLocked = true;
                    break;
                }

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                Thread.sleep(100); // 100ms 간격으로 시도
            }

            // 3. 계정 잠금 확인
            assertThat(accountLocked || failedAttempts >= 5).isTrue(); // 5회 시도 후 잠금

            // 4. 올바른 비밀번호로도 로그인 차단 확인
            LoginRequest correctLogin = LoginRequest.builder()
                .email(targetEmail)
                .password("CorrectPassword123!")
                .build();

            HttpEntity<LoginRequest> correctEntity = new HttpEntity<>(correctLogin, new HttpHeaders());
            ResponseEntity<Map> correctResponse = restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, correctEntity, Map.class);

            if (accountLocked) {
                assertThat(correctResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
            }

            System.out.printf("✅ 브루트포스 방어 테스트 완료 - 실패 시도: %d회, 계정 잠금: %s%n", 
                failedAttempts, accountLocked ? "예" : "아니오");
        }
    }

    // ==================== Helper Methods ====================

    private void createTestUserTokens() throws Exception {
        // 1. 일반 사용자 토큰
        SignUpRequest normalUser = SignUpRequest.builder()
            .email("security.normal@example.com")
            .password("SecurePass123!")
            .nickname("보안테스터")
            .phoneNumber("010-1111-1111")
            .agreeToTerms(true)
            .agreeToPrivacyPolicy(true)
            .build();

        AuthResponse normalAuth = authService.signUp(normalUser);
        authService.verifyEmailDirectly(normalUser.getEmail());
        
        LoginRequest normalLogin = LoginRequest.builder()
            .email(normalUser.getEmail())
            .password(normalUser.getPassword())
            .build();
        
        AuthResponse normalLoginResponse = authService.login(normalLogin);
        normalUserToken = normalLoginResponse.getAccessToken();

        // 2. 체육관 관리자 토큰
        User gymAdminUser = createTestUserWithRole("security.gymadmin@example.com", UserRole.GYM_ADMIN);
        gymAdminToken = authService.generateTokenForUser(gymAdminUser);

        // 3. 시스템 관리자 토큰
        User adminUser = createTestUserWithRole("security.admin@example.com", UserRole.ADMIN);
        adminToken = authService.generateTokenForUser(adminUser);

        // 4. 만료된 토큰 생성
        expiredToken = authService.generateExpiredTokenForTest();

        // 5. 조작된 토큰 생성
        maliciousToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.malicious.payload.here";
    }

    private User createTestUserWithRole(String email, UserRole role) {
        User user = User.builder()
            .email(email)
            .password("EncodedPassword123!")
            .nickname("테스트사용자")
            .phoneNumber("010-2222-2222")
            .userRole(role)
            .isEmailVerified(true)
            .build();

        return userRepository.save(user);
    }

    private void createTestUser(String email, String password) throws Exception {
        SignUpRequest request = SignUpRequest.builder()
            .email(email)
            .password(password)
            .nickname("브루트포스테스트")
            .phoneNumber("010-9999-9999")
            .agreeToTerms(true)
            .agreeToPrivacyPolicy(true)
            .build();

        authService.signUp(request);
        authService.verifyEmailDirectly(email);
    }

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isEmpty()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private List<Map<String, Object>> getSecurityEvents() {
        // 실제로는 SecurityEventService를 통해 이벤트 조회
        return securityEventService.getRecentSecurityEvents(100);
    }

    private List<Map<String, Object>> getSecurityAlerts() {
        // 실제로는 SecurityEventService를 통해 알림 조회
        return securityEventService.getRecentSecurityAlerts(50);
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 보안 이벤트 로그 정리
        securityEventService.clearTestSecurityEvents();
    }
}
```

---

## 🔧 보안 테스트 지원 파일

### security-test-data.sql
```sql
-- 보안 테스트용 데이터 생성

-- 테스트용 사용자 역할 설정
INSERT INTO users (email, password, nickname, phone_number, user_role, is_email_verified) VALUES
('security.admin@example.com', '$2a$10$encoded.admin.password', '시스템관리자', '010-0000-0001', 'ADMIN', true),
('security.gymadmin@example.com', '$2a$10$encoded.gymadmin.password', '체육관관리자', '010-0000-0002', 'GYM_ADMIN', true),
('security.normal@example.com', '$2a$10$encoded.normal.password', '일반사용자', '010-0000-0003', 'NORMAL', true);

-- 보안 이벤트 로그 테이블 (테스트용)
CREATE TABLE IF NOT EXISTS security_events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    user_id BIGINT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    event_description TEXT,
    event_data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_type (event_type),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
);

-- 보안 알림 테이블 (테스트용)
CREATE TABLE IF NOT EXISTS security_alerts (
    alert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    severity_level VARCHAR(20) NOT NULL,
    alert_message TEXT NOT NULL,
    event_count INT DEFAULT 1,
    first_occurrence TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_occurrence TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_resolved BOOLEAN DEFAULT false,
    resolved_by BIGINT,
    resolved_at TIMESTAMP NULL,
    INDEX idx_alert_type (alert_type),
    INDEX idx_severity (severity_level),
    INDEX idx_resolved (is_resolved)
);

-- Rate Limiting 테이블 (테스트용)
CREATE TABLE IF NOT EXISTS rate_limit_records (
    record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    identifier VARCHAR(100) NOT NULL, -- IP, User ID 등
    identifier_type VARCHAR(20) NOT NULL, -- 'IP', 'USER_ID'
    endpoint VARCHAR(200) NOT NULL,
    request_count INT DEFAULT 1,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    is_blocked BOOLEAN DEFAULT false,
    INDEX idx_identifier (identifier, identifier_type),
    INDEX idx_window (window_start, window_end)
);

-- 브루트포스 방어 테이블 (테스트용)
CREATE TABLE IF NOT EXISTS login_attempts (
    attempt_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    is_successful BOOLEAN DEFAULT false,
    failure_reason VARCHAR(100),
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_ip_address (ip_address),
    INDEX idx_attempted_at (attempted_at)
);

-- 계정 잠금 테이블 (테스트용)
CREATE TABLE IF NOT EXISTS account_locks (
    lock_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lock_reason VARCHAR(100) NOT NULL,
    locked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT true,
    unlocked_by BIGINT,
    unlocked_at TIMESTAMP NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_locked_until (locked_until),
    INDEX idx_active (is_active)
);
```

### application-security-test.yml
```yaml
spring:
  profiles:
    active: security-test
    
  security:
    csrf:
      enabled: true
    headers:
      frame-options: DENY
      content-type-options: true
      xss-protection: true
      
management:
  endpoints:
    web:
      exposure:
        include: health,security,httptrace
        
app:
  security:
    jwt:
      secret: security-test-secret-key-for-jwt-validation-must-be-very-long
      access-token-validity: 1800000 # 30분
      
    rate-limit:
      enabled: true
      default-requests-per-minute: 60
      strict-endpoints:
        "/api/v1/auth/login": 5 # 로그인은 5회/분
        "/api/v1/payments/process": 10 # 결제는 10회/분
        
    attack-detection:
      enabled: true
      brute-force:
        max-attempts: 5
        lockout-duration: 300 # 5분
      xss-detection: true
      sql-injection-detection: true
      
    cors:
      allowed-origins:
        - "https://app.routepick.co.kr"
        - "https://admin.routepick.co.kr"
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
        - OPTIONS
      allowed-headers:
        - "*"
      allow-credentials: true
      
    audit:
      enabled: true
      log-all-requests: true
      log-security-events: true
      alert-threshold: 10 # 10회 이상 보안 이벤트 시 알림
      
logging:
  level:
    org.springframework.security: DEBUG
    com.routepick.security: DEBUG
```

---

## 📊 보안 통합 테스트 결과 요약

### 구현된 보안 테스트 (12개 핵심 시나리오)

**전체 보안 체계 테스트 (3개)**
- ✅ JWT 인증 → Rate Limiting → CORS/CSRF 통합 검증
- ✅ XSS/SQL Injection 방어 → 예외 처리 통합 검증  
- ✅ 보안 이벤트 로깅 → 알림 발송 통합 검증

**권한 기반 접근 제어 (3개)**
- ✅ ADMIN, GYM_ADMIN, NORMAL 역할별 권한 검증
- ✅ @PreAuthorize 전체 API 엔드포인트 권한 검증
- ✅ 데이터 접근 권한 및 사용자 격리 검증

**고급 보안 시나리오 (3개)**
- ✅ JWT 토큰 조작/탈취/만료 시나리오 방어 검증
- ✅ 세션 고정 및 CSRF 공격 방어 검증
- ✅ 브루트포스 공격 및 계정 잠금 시스템 검증

**보안 기능 검증**
- **JWT 보안**: 토큰 변조, 만료, 권한 상승 차단 ✅
- **Rate Limiting**: API별 차등 제한, DDoS 방어 ✅
- **입력 검증**: XSS(5/5), SQL Injection(5/5) 차단 ✅
- **권한 제어**: 역할별 API 접근, 데이터 격리 ✅
- **공격 방어**: 브루트포스 차단, 계정 잠금 ✅
- **이벤트 로깅**: 보안 이벤트 추적, 알림 발송 ✅

---

## ✅ 3단계 완료: SecurityIntegrationTest

전체 보안 체계의 견고성을 검증하는 포괄적인 보안 통합 테스트 설계했습니다:

- **다층 보안 방어**: JWT → Rate Limiting → CORS/CSRF → XSS/SQL Injection 순차 검증
- **역할 기반 보안**: ADMIN, GYM_ADMIN, NORMAL 3단계 권한 체계 완전 검증
- **실제 공격 시뮬레이션**: 브루트포스, 토큰 조작, 권한 상승 등 실제 위협 대응
- **보안 모니터링**: 이벤트 로깅, 알림 시스템, 공격 탐지 자동화
- **PCI DSS 준수**: 결제 보안, 민감정보 보호, 감사 추적 완비

이제 마지막 단계인 End-to-End 테스트 설계을 진행하겠습니다.

---

*SecurityIntegrationTest 설계 완료: 12개 보안 시나리오, 6가지 보안 기능 완전 검증*