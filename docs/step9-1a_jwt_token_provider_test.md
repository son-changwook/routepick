# Step 9-1a: JwtTokenProvider 테스트 설계

> JWT 토큰 생성/검증 보안 테스트  
> 생성일: 2025-08-27  
> 기반: step6-1a_auth_service.md, step8-1b_jwt_authentication_filter.md  
> 테스트 범위: Access/Refresh 토큰, 만료 처리, 변조 탐지, 블랙리스트

---

## 🎯 테스트 목표

### 핵심 검증 사항
- **토큰 생성 보안**: Access/Refresh 토큰 올바른 생성
- **토큰 검증**: 서명 검증, 만료 시간, 페이로드 검증
- **보안 공격 방어**: 토큰 변조, 만료, 블랙리스트 처리
- **성능 검증**: 토큰 생성/검증 성능 측정

---

## 🧪 JwtTokenProviderTest 구현

### JwtTokenProviderTest.java
```java
package com.routepick.security.jwt;

import com.routepick.exception.security.InvalidTokenException;
import com.routepick.exception.security.ExpiredTokenException;
import com.routepick.security.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtTokenProvider 보안 테스트
 * - 토큰 생성/검증 로직 검증
 * - 보안 공격 시나리오 테스트
 * - 성능 측정 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT 토큰 프로바이더 보안 테스트")
class JwtTokenProviderTest {

    @Mock
    private TokenBlacklistService blacklistService;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET_KEY = "test-jwt-secret-key-for-routepick-authentication-system-must-be-at-least-256-bits-long";
    private static final String TEST_USERNAME = "testuser@routepick.com";
    private static final List<String> TEST_ROLES = List.of("ROLE_USER");

    @BeforeEach
    void setUp() {
        // 테스트용 JWT 설정 주입
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L); // 1시간
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpirationMs", 86400000L); // 24시간
        ReflectionTestUtils.setField(jwtTokenProvider, "issuer", "routepick-test");
        
        // 초기화 메서드 호출 (PostConstruct 시뮬레이션)
        jwtTokenProvider.init();
    }

    // ===== 토큰 생성 테스트 =====

    @Test
    @DisplayName("Access Token 정상 생성 테스트")
    void shouldGenerateValidAccessToken() {
        // given
        String username = TEST_USERNAME;

        // when
        String token = jwtTokenProvider.generateAccessToken(username);

        // then
        assertThat(token).isNotNull().isNotEmpty();
        
        // JWT 구조 확인 (Header.Payload.Signature)
        String[] tokenParts = token.split("\\.");
        assertThat(tokenParts).hasSize(3);
        
        // 토큰에서 사용자명 추출 확인
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);
        assertThat(extractedUsername).isEqualTo(username);
        
        // 토큰 검증 성공 확인
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("Refresh Token 정상 생성 테스트")
    void shouldGenerateValidRefreshToken() {
        // given
        String username = TEST_USERNAME;

        // when
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        // then
        assertThat(refreshToken).isNotNull().isNotEmpty();
        
        // Refresh 토큰 검증
        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
        
        // 사용자명 추출 확인
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(refreshToken);
        assertThat(extractedUsername).isEqualTo(username);
        
        // Refresh 토큰 만료 시간 확인 (24시간)
        Date expirationDate = jwtTokenProvider.getExpirationDateFromToken(refreshToken);
        LocalDateTime expiration = expirationDate.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        
        assertThat(expiration).isAfter(LocalDateTime.now().plusHours(20));
    }

    @Test
    @DisplayName("역할 정보 포함 토큰 생성 테스트")
    void shouldGenerateTokenWithRoles() {
        // given
        String username = TEST_USERNAME;
        List<String> roles = TEST_ROLES;

        // when
        String token = jwtTokenProvider.generateTokenWithRoles(username, roles);

        // then
        assertThat(token).isNotNull();
        
        // 토큰에서 역할 정보 추출
        List<String> extractedRoles = jwtTokenProvider.getRolesFromToken(token);
        assertThat(extractedRoles).containsExactlyElementsOf(roles);
    }

    // ===== 토큰 검증 테스트 =====

    @Test
    @DisplayName("유효한 토큰 검증 성공 테스트")
    void shouldValidateValidToken() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME);
        when(blacklistService.isBlacklisted(token)).thenReturn(false);

        // when
        boolean isValid = jwtTokenProvider.validateToken(token);

        // then
        assertThat(isValid).isTrue();
        verify(blacklistService).isBlacklisted(token);
    }

    @Test
    @DisplayName("만료된 토큰 검증 실패 테스트")
    void shouldFailValidationForExpiredToken() {
        // given - 만료 시간을 과거로 설정하여 만료된 토큰 생성
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", -1000L); // -1초
        jwtTokenProvider.init();
        
        String expiredToken = jwtTokenProvider.generateAccessToken(TEST_USERNAME);
        
        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
            .isInstanceOf(ExpiredTokenException.class)
            .hasMessageContaining("Token expired");
    }

    @Test
    @DisplayName("변조된 토큰 검증 실패 테스트")
    void shouldFailValidationForTamperedToken() {
        // given
        String validToken = jwtTokenProvider.generateAccessToken(TEST_USERNAME);
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "HACKED";

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Invalid token signature");
    }

    @Test
    @DisplayName("블랙리스트 토큰 검증 실패 테스트")
    void shouldFailValidationForBlacklistedToken() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME);
        when(blacklistService.isBlacklisted(token)).thenReturn(true);

        // when
        boolean isValid = jwtTokenProvider.validateToken(token);

        // then
        assertThat(isValid).isFalse();
        verify(blacklistService).isBlacklisted(token);
    }

    @Test
    @DisplayName("잘못된 서명 키로 생성된 토큰 검증 실패 테스트")
    void shouldFailValidationForWrongSignature() {
        // given - 다른 키로 토큰 생성
        SecretKey wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-different-from-actual".getBytes(StandardCharsets.UTF_8));
        String tokenWithWrongKey = Jwts.builder()
            .setSubject(TEST_USERNAME)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(wrongKey)
            .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tokenWithWrongKey))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Invalid token signature");
    }

    // ===== 토큰 정보 추출 테스트 =====

    @Test
    @DisplayName("토큰에서 사용자명 추출 테스트")
    void shouldExtractUsernameFromToken() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME);

        // when
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        // then
        assertThat(extractedUsername).isEqualTo(TEST_USERNAME);
    }

    @Test
    @DisplayName("토큰에서 만료 시간 추출 테스트")
    void shouldExtractExpirationTimeFromToken() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME);
        long currentTime = System.currentTimeMillis();

        // when
        long expirationTime = jwtTokenProvider.getExpirationTime(token);

        // then
        // 만료 시간이 현재 시간보다 미래여야 함
        assertThat(expirationTime).isGreaterThan(currentTime);
        
        // 만료 시간이 약 1시간(3600초) 후여야 함 (±10초 허용)
        long expectedExpiration = currentTime + 3600000; // 1시간
        assertThat(Math.abs(expirationTime - expectedExpiration)).isLessThan(10000); // 10초 오차 허용
    }

    @Test
    @DisplayName("토큰에서 발급 시간 추출 테스트")
    void shouldExtractIssuedTimeFromToken() {
        // given
        long beforeGeneration = System.currentTimeMillis();
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME);
        long afterGeneration = System.currentTimeMillis();

        // when
        Date issuedAt = jwtTokenProvider.getIssuedAtFromToken(token);

        // then
        assertThat(issuedAt).isNotNull();
        assertThat(issuedAt.getTime()).isBetween(beforeGeneration, afterGeneration);
    }

    // ===== 토큰 갱신 테스트 =====

    @Test
    @DisplayName("Refresh Token으로 Access Token 갱신 테스트")
    void shouldRefreshAccessTokenWithValidRefreshToken() {
        // given
        String refreshToken = jwtTokenProvider.generateRefreshToken(TEST_USERNAME);
        when(blacklistService.isBlacklisted(refreshToken)).thenReturn(false);

        // when
        String newAccessToken = jwtTokenProvider.refreshAccessToken(refreshToken);

        // then
        assertThat(newAccessToken).isNotNull().isNotEmpty();
        assertThat(jwtTokenProvider.validateToken(newAccessToken)).isTrue();
        
        // 새로운 토큰의 사용자명이 일치해야 함
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(newAccessToken);
        assertThat(extractedUsername).isEqualTo(TEST_USERNAME);
    }

    @Test
    @DisplayName("만료된 Refresh Token으로 갱신 실패 테스트")
    void shouldFailRefreshWithExpiredRefreshToken() {
        // given - 만료된 Refresh Token 생성
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpirationMs", -1000L);
        jwtTokenProvider.init();
        
        String expiredRefreshToken = jwtTokenProvider.generateRefreshToken(TEST_USERNAME);

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.refreshAccessToken(expiredRefreshToken))
            .isInstanceOf(ExpiredTokenException.class);
    }

    // ===== 보안 공격 시나리오 테스트 =====

    @Test
    @DisplayName("None 알고리즘 공격 방어 테스트")
    void shouldPreventNoneAlgorithmAttack() {
        // given - None 알고리즘을 사용한 악의적인 토큰 생성 시도
        String noneAlgorithmToken = Jwts.builder()
            .setSubject(TEST_USERNAME)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .setHeaderParam("alg", "none")
            .compact() + ".";

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(noneAlgorithmToken))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("페이로드 수정 공격 방어 테스트")
    void shouldPreventPayloadTamperingAttack() {
        // given
        String originalToken = jwtTokenProvider.generateAccessToken(TEST_USERNAME);
        
        // 페이로드 부분 수정 (사용자명 변경 시도)
        String[] parts = originalToken.split("\\.");
        String tamperedPayload = parts[1].replace("testuser", "adminuser");
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("토큰 재사용 공격 방어 테스트")
    void shouldPreventTokenReuseAttack() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME);
        
        // 토큰을 블랙리스트에 추가 (로그아웃 시뮬레이션)
        when(blacklistService.isBlacklisted(token)).thenReturn(true);

        // when
        boolean isValid = jwtTokenProvider.validateToken(token);

        // then
        assertThat(isValid).isFalse();
        verify(blacklistService).isBlacklisted(token);
    }

    // ===== 성능 테스트 =====

    @Test
    @DisplayName("토큰 생성 성능 테스트")
    void shouldMeetTokenGenerationPerformanceRequirement() {
        // given
        int tokenCount = 1000;
        long startTime = System.currentTimeMillis();

        // when - 1000개 토큰 생성
        for (int i = 0; i < tokenCount; i++) {
            jwtTokenProvider.generateAccessToken(TEST_USERNAME + i);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // then - 1000개 토큰을 1초 내에 생성해야 함
        assertThat(duration).isLessThan(1000L);
        
        double tokensPerSecond = (tokenCount * 1000.0) / duration;
        System.out.println(String.format("Token generation performance: %.2f tokens/second", tokensPerSecond));
        
        // 최소 1000 tokens/second 성능 요구사항
        assertThat(tokensPerSecond).isGreaterThan(1000);
    }

    @Test
    @DisplayName("토큰 검증 성능 테스트")
    void shouldMeetTokenValidationPerformanceRequirement() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME);
        when(blacklistService.isBlacklisted(anyString())).thenReturn(false);
        
        int validationCount = 1000;
        long startTime = System.currentTimeMillis();

        // when - 1000번 토큰 검증
        for (int i = 0; i < validationCount; i++) {
            jwtTokenProvider.validateToken(token);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // then - 1000번 검증을 0.5초 내에 완료해야 함
        assertThat(duration).isLessThan(500L);
        
        double validationsPerSecond = (validationCount * 1000.0) / duration;
        System.out.println(String.format("Token validation performance: %.2f validations/second", validationsPerSecond));
        
        // 최소 2000 validations/second 성능 요구사항
        assertThat(validationsPerSecond).isGreaterThan(2000);
    }

    // ===== 엣지 케이스 테스트 =====

    @Test
    @DisplayName("빈 토큰 처리 테스트")
    void shouldHandleEmptyToken() {
        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(""))
            .isInstanceOf(InvalidTokenException.class);
            
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(null))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("잘못된 형식 토큰 처리 테스트")
    void shouldHandleMalformedToken() {
        // given
        String malformedToken = "not.a.valid.jwt.token";

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(malformedToken))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Malformed token");
    }

    @Test
    @DisplayName("특수 문자 포함 사용자명 토큰 생성 테스트")
    void shouldHandleSpecialCharactersInUsername() {
        // given
        String specialUsername = "user+test@route-pick.co.kr";

        // when
        String token = jwtTokenProvider.generateAccessToken(specialUsername);

        // then
        assertThat(token).isNotNull();
        
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);
        assertThat(extractedUsername).isEqualTo(specialUsername);
        
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("매우 긴 사용자명 처리 테스트")
    void shouldHandleLongUsername() {
        // given
        String longUsername = "very.long.username.that.exceeds.normal.limits@".repeat(10) + "example.com";

        // when
        String token = jwtTokenProvider.generateAccessToken(longUsername);

        // then
        assertThat(token).isNotNull();
        
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);
        assertThat(extractedUsername).isEqualTo(longUsername);
    }

    // ===== 테스트 후 정리 =====

    @AfterEach
    void tearDown() {
        // Mock 객체 초기화
        reset(blacklistService);
    }
}
```

---

## 🔧 테스트 설정 클래스

### JwtTestConfiguration.java
```java
package com.routepick.config.test;

import com.routepick.security.jwt.JwtTokenProvider;
import com.routepick.security.service.TokenBlacklistService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * JWT 테스트 설정
 */
@TestConfiguration
@TestPropertySource(properties = {
    "jwt.secret=test-jwt-secret-key-for-routepick-authentication-system-must-be-at-least-256-bits-long",
    "jwt.expiration=3600000",
    "jwt.refresh-expiration=86400000",
    "jwt.issuer=routepick-test"
})
public class JwtTestConfiguration {

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @Bean
    @Primary
    public JwtTokenProvider testJwtTokenProvider() {
        return new JwtTokenProvider(tokenBlacklistService);
    }
}
```

---

## 📊 테스트 메트릭

### 성능 기준
- **토큰 생성**: 최소 1,000 tokens/second
- **토큰 검증**: 최소 2,000 validations/second
- **메모리 사용량**: 토큰당 1KB 미만

### 보안 검증 항목
- ✅ **서명 검증**: HMAC-SHA256 서명 변조 탐지
- ✅ **만료 시간**: 정확한 TTL 처리
- ✅ **블랙리스트**: 무효화된 토큰 차단
- ✅ **알고리즘 고정**: None 알고리즘 공격 방어
- ✅ **페이로드 보호**: 토큰 내용 변조 탐지

---

*Step 9-1a 완료: JwtTokenProvider 보안 테스트 설계*
*다음 단계: AuthService 단위 테스트 설계*