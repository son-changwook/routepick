# Step 9-1a: JwtTokenProvider 실제 설계

## 📋 구현 목표
- **JWT 토큰 생성**: Access Token, Refresh Token 생성 로직
- **토큰 검증**: 서명 검증, 만료 시간, 페이로드 검증  
- **보안 강화**: 토큰 변조 방지, 블랙리스트 연동
- **성능 최적화**: 토큰 생성/검증 성능 개선

## 🔐 JwtTokenProvider 구현

### JwtTokenProvider.java
```java
package com.routepick.backend.security.jwt;

import com.routepick.backend.exception.security.InvalidTokenException;
import com.routepick.backend.exception.security.ExpiredTokenException;
import com.routepick.backend.security.service.TokenBlacklistService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT 토큰 생성 및 검증 서비스
 * - Access Token / Refresh Token 관리
 * - 토큰 보안 검증 및 블랙리스트 연동
 * - 성능 최적화된 토큰 처리
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {
    
    private final TokenBlacklistService tokenBlacklistService;
    
    @Value("${app.security.jwt.secret}")
    private String jwtSecret;
    
    @Value("${app.security.jwt.expiration-ms:3600000}") // 1시간
    private long jwtExpirationMs;
    
    @Value("${app.security.jwt.refresh-expiration-ms:86400000}") // 24시간
    private long refreshExpirationMs;
    
    @Value("${app.security.jwt.issuer:routepick}")
    private String issuer;
    
    private SecretKey secretKey;
    
    @PostConstruct
    public void init() {
        // JWT 서명용 SecretKey 생성
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT 토큰 프로바이더 초기화 완료 - 만료시간: {}분", jwtExpirationMs / 60000);
    }
    
    /**
     * Access Token 생성
     */
    public String generateAccessToken(String username) {
        return generateAccessToken(username, List.of("ROLE_USER"));
    }
    
    public String generateAccessToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        
        return Jwts.builder()
                .setSubject(username)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .setId(UUID.randomUUID().toString()) // JTI (JWT ID)
                .claim("roles", roles)
                .claim("type", "ACCESS")
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * Authentication 객체로부터 Access Token 생성
     */
    public String generateAccessToken(Authentication authentication) {
        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        
        return generateAccessToken(username, roles);
    }
    
    /**
     * Refresh Token 생성
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationMs);
        
        return Jwts.builder()
                .setSubject(username)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .setId(UUID.randomUUID().toString())
                .claim("type", "REFRESH")
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 토큰 검증
     */
    public boolean validateToken(String token) {
        try {
            // 1. 블랙리스트 확인
            if (tokenBlacklistService.isTokenBlacklisted(token)) {
                log.warn("블랙리스트된 토큰 사용 시도: {}...", token.substring(0, 20));
                return false;
            }
            
            // 2. JWT 서명 및 구조 검증
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseClaimsJws(token);
            
            return true;
            
        } catch (SecurityException e) {
            log.error("JWT 서명이 올바르지 않습니다: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("JWT 토큰이 올바르지 않습니다: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.debug("JWT 토큰이 만료되었습니다: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("지원하지 않는 JWT 토큰입니다: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 비어있습니다: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 토큰에서 사용자명 추출
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("토큰에서 사용자명 추출 실패: {}", e.getMessage());
            throw new InvalidTokenException("토큰에서 사용자명을 추출할 수 없습니다");
        }
    }
    
    /**
     * 토큰에서 역할 목록 추출
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        try {
            Claims claims = extractClaims(token);
            List<String> roles = claims.get("roles", List.class);
            return roles != null ? roles : List.of("ROLE_USER");
        } catch (Exception e) {
            log.error("토큰에서 역할 추출 실패: {}", e.getMessage());
            return List.of("ROLE_USER");
        }
    }
    
    /**
     * 토큰 타입 확인 (ACCESS/REFRESH)
     */
    public String getTokenType(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.get("type", String.class);
        } catch (Exception e) {
            log.error("토큰 타입 확인 실패: {}", e.getMessage());
            return "UNKNOWN";
        }
    }
    
    /**
     * 토큰 만료 시간 확인
     */
    public Date getExpirationDateFromToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration();
        } catch (Exception e) {
            log.error("토큰 만료 시간 확인 실패: {}", e.getMessage());
            throw new InvalidTokenException("토큰 만료 시간을 확인할 수 없습니다");
        }
    }
    
    /**
     * 토큰 JWT ID 추출
     */
    public String getTokenId(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getId();
        } catch (Exception e) {
            log.error("토큰 ID 추출 실패: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 토큰이 특정 시간 내에 만료되는지 확인
     */
    public boolean isTokenExpiringWithin(String token, Duration duration) {
        try {
            Date expirationDate = getExpirationDateFromToken(token);
            Date now = new Date();
            long timeUntilExpiration = expirationDate.getTime() - now.getTime();
            
            return timeUntilExpiration <= duration.toMillis();
        } catch (Exception e) {
            return true; // 확인할 수 없으면 만료된 것으로 처리
        }
    }
    
    /**
     * Refresh Token 유효성 검증
     */
    public boolean validateRefreshToken(String refreshToken) {
        if (!validateToken(refreshToken)) {
            return false;
        }
        
        String tokenType = getTokenType(refreshToken);
        if (!"REFRESH".equals(tokenType)) {
            log.warn("Refresh Token이 아닌 토큰으로 갱신 시도: {}", tokenType);
            return false;
        }
        
        return true;
    }
    
    /**
     * Access Token을 Refresh Token으로 갱신
     */
    public String refreshAccessToken(String refreshToken) {
        if (!validateRefreshToken(refreshToken)) {
            throw new InvalidTokenException("유효하지 않은 Refresh Token입니다");
        }
        
        try {
            String username = getUsernameFromToken(refreshToken);
            List<String> roles = getRolesFromToken(refreshToken);
            
            // 새로운 Access Token 생성
            return generateAccessToken(username, roles);
            
        } catch (Exception e) {
            log.error("Access Token 갱신 실패: {}", e.getMessage());
            throw new InvalidTokenException("토큰 갱신에 실패했습니다");
        }
    }
    
    /**
     * 토큰 블랙리스트 추가
     */
    public void addToBlacklist(String token, Long userId) {
        try {
            Date expirationDate = getExpirationDateFromToken(token);
            Duration ttl = Duration.between(
                    LocalDateTime.now(),
                    expirationDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            );
            
            if (ttl.isPositive()) {
                tokenBlacklistService.addToBlacklist(token, userId, ttl);
                log.info("토큰을 블랙리스트에 추가: userId={}, ttl={}분", userId, ttl.toMinutes());
            }
        } catch (Exception e) {
            log.error("토큰 블랙리스트 추가 실패: {}", e.getMessage());
        }
    }
    
    /**
     * Claims 추출 (내부 메서드)
     */
    private Claims extractClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.debug("만료된 토큰에서 Claims 추출 시도");
            throw new ExpiredTokenException("토큰이 만료되었습니다");
        } catch (Exception e) {
            log.error("Claims 추출 실패: {}", e.getMessage());
            throw new InvalidTokenException("토큰 분석에 실패했습니다");
        }
    }
    
    /**
     * 토큰 정보 요약 조회 (디버깅용)
     */
    public TokenInfo getTokenInfo(String token) {
        try {
            Claims claims = extractClaims(token);
            
            return TokenInfo.builder()
                    .subject(claims.getSubject())
                    .issuer(claims.getIssuer())
                    .issuedAt(claims.getIssuedAt())
                    .expiration(claims.getExpiration())
                    .tokenId(claims.getId())
                    .tokenType(claims.get("type", String.class))
                    .roles(claims.get("roles", List.class))
                    .isValid(validateToken(token))
                    .isExpired(claims.getExpiration().before(new Date()))
                    .build();
                    
        } catch (Exception e) {
            return TokenInfo.builder()
                    .isValid(false)
                    .error(e.getMessage())
                    .build();
        }
    }
    
    /**
     * 토큰 정보 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class TokenInfo {
        private String subject;
        private String issuer;
        private Date issuedAt;
        private Date expiration;
        private String tokenId;
        private String tokenType;
        private List<String> roles;
        private boolean isValid;
        private boolean isExpired;
        private String error;
    }
}
```

## ⚙️ JWT 설정

### JwtProperties.java
```java
package com.routepick.backend.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 설정 프로퍼티
 */
@Component
@ConfigurationProperties(prefix = "app.security.jwt")
@Getter
@Setter
public class JwtProperties {
    
    /**
     * JWT 서명 키 (256비트 이상)
     */
    private String secret;
    
    /**
     * Access Token 만료시간 (밀리초)
     */
    private long expirationMs = 3600000; // 1시간
    
    /**
     * Refresh Token 만료시간 (밀리초)
     */
    private long refreshExpirationMs = 86400000; // 24시간
    
    /**
     * JWT 발급자
     */
    private String issuer = "routepick";
    
    /**
     * 토큰 갱신 임계시간 (밀리초)
     */
    private long refreshThresholdMs = 600000; // 10분
}
```

## 🔧 Custom Exception

### InvalidTokenException.java
```java
package com.routepick.backend.exception.security;

import com.routepick.backend.exception.BaseException;
import com.routepick.backend.exception.ErrorCode;

/**
 * JWT 토큰 유효성 검증 실패 예외
 */
public class InvalidTokenException extends BaseException {
    
    public InvalidTokenException(String message) {
        super(ErrorCode.INVALID_JWT_TOKEN, message);
    }
    
    public InvalidTokenException(String message, Throwable cause) {
        super(ErrorCode.INVALID_JWT_TOKEN, message, cause);
    }
}
```

### ExpiredTokenException.java
```java
package com.routepick.backend.exception.security;

import com.routepick.backend.exception.BaseException;
import com.routepick.backend.exception.ErrorCode;

/**
 * JWT 토큰 만료 예외
 */
public class ExpiredTokenException extends BaseException {
    
    public ExpiredTokenException(String message) {
        super(ErrorCode.EXPIRED_JWT_TOKEN, message);
    }
    
    public ExpiredTokenException(String message, Throwable cause) {
        super(ErrorCode.EXPIRED_JWT_TOKEN, message, cause);
    }
}
```

## 🧪 테스트 코드

### JwtTokenProviderTest.java
```java
package com.routepick.backend.security.jwt;

import com.routepick.backend.exception.security.InvalidTokenException;
import com.routepick.backend.exception.security.ExpiredTokenException;
import com.routepick.backend.security.service.TokenBlacklistService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT 토큰 프로바이더 테스트")
class JwtTokenProviderTest {
    
    @Mock
    private TokenBlacklistService blacklistService;
    
    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;
    
    private static final String TEST_SECRET = "test-jwt-secret-key-for-routepick-must-be-at-least-256-bits-long-for-security";
    private static final String TEST_USERNAME = "testuser@routepick.com";
    private static final List<String> TEST_ROLES = List.of("ROLE_USER");
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpirationMs", 86400000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "issuer", "routepick-test");
        
        jwtTokenProvider.init();
    }
    
    @Test
    @DisplayName("Access Token 정상 생성")
    void shouldGenerateValidAccessToken() {
        // when
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME, TEST_ROLES);
        
        // then
        assertThat(token).isNotNull().isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // Header.Payload.Signature
        
        String username = jwtTokenProvider.getUsernameFromToken(token);
        assertThat(username).isEqualTo(TEST_USERNAME);
        
        List<String> roles = jwtTokenProvider.getRolesFromToken(token);
        assertThat(roles).isEqualTo(TEST_ROLES);
        
        String tokenType = jwtTokenProvider.getTokenType(token);
        assertThat(tokenType).isEqualTo("ACCESS");
    }
    
    @Test
    @DisplayName("Refresh Token 정상 생성")
    void shouldGenerateValidRefreshToken() {
        // when
        String refreshToken = jwtTokenProvider.generateRefreshToken(TEST_USERNAME);
        
        // then
        assertThat(refreshToken).isNotNull().isNotEmpty();
        
        String tokenType = jwtTokenProvider.getTokenType(refreshToken);
        assertThat(tokenType).isEqualTo("REFRESH");
        
        boolean isValid = jwtTokenProvider.validateRefreshToken(refreshToken);
        assertThat(isValid).isTrue();
    }
    
    @Test
    @DisplayName("토큰 검증 성공")
    void shouldValidateTokenSuccessfully() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME, TEST_ROLES);
        when(blacklistService.isTokenBlacklisted(anyString())).thenReturn(false);
        
        // when
        boolean isValid = jwtTokenProvider.validateToken(token);
        
        // then
        assertThat(isValid).isTrue();
    }
    
    @Test
    @DisplayName("블랙리스트된 토큰 검증 실패")
    void shouldFailValidationForBlacklistedToken() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME, TEST_ROLES);
        when(blacklistService.isTokenBlacklisted(token)).thenReturn(true);
        
        // when
        boolean isValid = jwtTokenProvider.validateToken(token);
        
        // then
        assertThat(isValid).isFalse();
    }
    
    @Test
    @DisplayName("변조된 토큰 검증 실패")
    void shouldFailValidationForTamperedToken() {
        // given
        String validToken = jwtTokenProvider.generateAccessToken(TEST_USERNAME, TEST_ROLES);
        String tamperedToken = validToken + "tampered";
        when(blacklistService.isTokenBlacklisted(anyString())).thenReturn(false);
        
        // when
        boolean isValid = jwtTokenProvider.validateToken(tamperedToken);
        
        // then
        assertThat(isValid).isFalse();
    }
    
    @Test
    @DisplayName("Access Token 갱신 성공")
    void shouldRefreshAccessTokenSuccessfully() {
        // given
        String refreshToken = jwtTokenProvider.generateRefreshToken(TEST_USERNAME);
        when(blacklistService.isTokenBlacklisted(anyString())).thenReturn(false);
        
        // when
        String newAccessToken = jwtTokenProvider.refreshAccessToken(refreshToken);
        
        // then
        assertThat(newAccessToken).isNotNull().isNotEmpty();
        
        String username = jwtTokenProvider.getUsernameFromToken(newAccessToken);
        assertThat(username).isEqualTo(TEST_USERNAME);
        
        String tokenType = jwtTokenProvider.getTokenType(newAccessToken);
        assertThat(tokenType).isEqualTo("ACCESS");
    }
    
    @Test
    @DisplayName("만료 임계시간 확인")
    void shouldCheckTokenExpirationThreshold() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME, TEST_ROLES);
        
        // when
        boolean isExpiringWithinOneHour = jwtTokenProvider.isTokenExpiringWithin(token, Duration.ofHours(1));
        boolean isExpiringWithinOneDay = jwtTokenProvider.isTokenExpiringWithin(token, Duration.ofDays(1));
        
        // then
        assertThat(isExpiringWithinOneHour).isTrue();
        assertThat(isExpiringWithinOneDay).isTrue();
    }
    
    @Test
    @DisplayName("토큰 정보 조회")
    void shouldGetTokenInfo() {
        // given
        String token = jwtTokenProvider.generateAccessToken(TEST_USERNAME, TEST_ROLES);
        
        // when
        JwtTokenProvider.TokenInfo tokenInfo = jwtTokenProvider.getTokenInfo(token);
        
        // then
        assertThat(tokenInfo).isNotNull();
        assertThat(tokenInfo.getSubject()).isEqualTo(TEST_USERNAME);
        assertThat(tokenInfo.getIssuer()).isEqualTo("routepick-test");
        assertThat(tokenInfo.getTokenType()).isEqualTo("ACCESS");
        assertThat(tokenInfo.getRoles()).isEqualTo(TEST_ROLES);
        assertThat(tokenInfo.isValid()).isTrue();
        assertThat(tokenInfo.isExpired()).isFalse();
    }
}
```

## 📋 application.yml 설정

```yaml
app:
  security:
    jwt:
      secret: ${JWT_SECRET:routepick-jwt-secret-key-must-be-at-least-256-bits-long-for-production-security}
      expiration-ms: 3600000      # 1시간 (60분 * 60초 * 1000ms)
      refresh-expiration-ms: 86400000  # 24시간
      issuer: routepick
      refresh-threshold-ms: 600000  # 10분
```

---

**다음 단계**: step9-1b_auth_service_test.md (AuthService 테스트 설계)  
**연관 시스템**: step8-5a (TokenBlacklistService) 완전 통합  
**성능 목표**: 토큰 생성 1ms 이내, 검증 0.5ms 이내  

*생성일: 2025-09-02*  
*RoutePickr 9-1a: JWT 토큰 시스템 완성*