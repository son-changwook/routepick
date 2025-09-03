# 8-1c: JWT Token Provider

## 📋 구현 목표
- **토큰 생성**: Access Token, Refresh Token 생성
- **토큰 검증**: 서명, 만료시간, Claims 검증
- **Claims 추출**: 사용자 정보, 권한 추출
- **토큰 갱신**: Refresh Token으로 Access Token 재발급

## 🔐 JwtTokenProvider 구현

### JwtTokenProvider.java
```java
package com.routepick.backend.security.jwt;

import com.routepick.backend.security.dto.TokenInfo;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-seconds:1800}") long accessTokenValidity,
            @Value("${jwt.refresh-token-validity-seconds:604800}") long refreshTokenValidity,
            @Value("${jwt.issuer:routepick}") String issuer) {
        
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityMs = accessTokenValidity * 1000;
        this.refreshTokenValidityMs = refreshTokenValidity * 1000;
        this.issuer = issuer;
    }

    /**
     * Access Token 생성
     */
    public String generateAccessToken(Authentication authentication) {
        String username = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        return generateAccessToken(username, authorities);
    }

    public String generateAccessToken(String username) {
        return generateAccessToken(username, Collections.emptyList());
    }

    public String generateAccessToken(String username, 
                                     Collection<? extends GrantedAuthority> authorities) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidityMs);
        
        String authoritiesString = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));
        
        return Jwts.builder()
            .setSubject(username)
            .setIssuer(issuer)
            .setIssuedAt(now)
            .setExpiration(validity)
            .claim("type", "ACCESS")
            .claim("authorities", authoritiesString)
            .signWith(secretKey, SignatureAlgorithm.HS512)
            .compact();
    }

    /**
     * Refresh Token 생성
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshTokenValidityMs);
        
        return Jwts.builder()
            .setSubject(username)
            .setIssuer(issuer)
            .setIssuedAt(now)
            .setExpiration(validity)
            .claim("type", "REFRESH")
            .signWith(secretKey, SignatureAlgorithm.HS512)
            .compact();
    }

    /**
     * Token Pair 생성 (Access + Refresh)
     */
    public TokenInfo generateTokenPair(Authentication authentication) {
        String username = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        String accessToken = generateAccessToken(username, authorities);
        String refreshToken = generateRefreshToken(username);
        
        return TokenInfo.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .accessTokenExpiresIn(accessTokenValidityMs / 1000)
            .refreshTokenExpiresIn(refreshTokenValidityMs / 1000)
            .tokenType("Bearer")
            .build();
    }

    /**
     * 토큰 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
            return true;
            
        } catch (SecurityException | MalformedJwtException e) {
            log.error("잘못된 JWT 서명입니다: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("만료된 JWT 토큰입니다: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 토큰입니다: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 잘못되었습니다: {}", e.getMessage());
        }
        
        return false;
    }

    /**
     * 토큰에서 사용자명 추출
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    /**
     * 토큰에서 권한 정보 추출
     */
    public Collection<? extends GrantedAuthority> getAuthorities(String token) {
        Claims claims = getClaims(token);
        String authoritiesString = claims.get("authorities", String.class);
        
        if (authoritiesString == null || authoritiesString.isEmpty()) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(authoritiesString.split(","))
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }

    /**
     * 토큰 타입 확인 (ACCESS or REFRESH)
     */
    public String getTokenType(String token) {
        Claims claims = getClaims(token);
        return claims.get("type", String.class);
    }

    /**
     * 토큰 만료 시간 추출
     */
    public long getExpirationTime(String token) {
        Claims claims = getClaims(token);
        return claims.getExpiration().getTime();
    }

    /**
     * 토큰 남은 유효 시간 (초)
     */
    public long getRemainingTime(String token) {
        long expirationTime = getExpirationTime(token);
        long currentTime = System.currentTimeMillis();
        return Math.max(0, (expirationTime - currentTime) / 1000);
    }

    /**
     * Refresh Token으로 Access Token 재발급
     */
    public String refreshAccessToken(String refreshToken) {
        if (!validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다");
        }
        
        String tokenType = getTokenType(refreshToken);
        if (!"REFRESH".equals(tokenType)) {
            throw new IllegalArgumentException("Refresh Token이 아닙니다");
        }
        
        String username = getUsernameFromToken(refreshToken);
        return generateAccessToken(username);
    }

    /**
     * Claims 추출
     */
    private Claims getClaims(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
                
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    /**
     * 토큰 정보 추출 (디버깅용)
     */
    public Map<String, Object> getTokenInfo(String token) {
        Claims claims = getClaims(token);
        
        Map<String, Object> info = new HashMap<>();
        info.put("subject", claims.getSubject());
        info.put("issuer", claims.getIssuer());
        info.put("type", claims.get("type"));
        info.put("authorities", claims.get("authorities"));
        info.put("issuedAt", new Date(claims.getIssuedAt().getTime()));
        info.put("expiration", new Date(claims.getExpiration().getTime()));
        info.put("remainingTime", getRemainingTime(token) + " seconds");
        
        return info;
    }
}
```

### TokenInfo.java (DTO)
```java
package com.routepick.backend.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo {
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("refresh_token")
    private String refreshToken;
    
    @JsonProperty("token_type")
    private String tokenType;
    
    @JsonProperty("expires_in")
    private Long accessTokenExpiresIn;  // seconds
    
    @JsonProperty("refresh_expires_in")
    private Long refreshTokenExpiresIn; // seconds
}
```

## 🔑 JWT 토큰 구조

### Access Token Claims
```json
{
  "sub": "user@example.com",
  "iss": "routepick",
  "iat": 1703001600,
  "exp": 1703003400,
  "type": "ACCESS",
  "authorities": "ROLE_USER,ROLE_GYM_MEMBER"
}
```

### Refresh Token Claims
```json
{
  "sub": "user@example.com",
  "iss": "routepick",
  "iat": 1703001600,
  "exp": 1703606400,
  "type": "REFRESH"
}
```

## 🔒 보안 설정

### 1. Secret Key 관리
```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-base64-encoded}
  access-token-validity-seconds: 1800  # 30분
  refresh-token-validity-seconds: 604800  # 7일
  issuer: routepick
```

### 2. 토큰 수명
- **Access Token**: 30분 (짧은 수명)
- **Refresh Token**: 7일 (긴 수명)
- **자동 갱신**: 만료 5분 전

### 3. 서명 알고리즘
- **HS512**: HMAC with SHA-512
- **Key Size**: 256 bits 이상
- **Base64 인코딩**: 안전한 전송

## 🔄 토큰 갱신 플로우

### 1. 일반 갱신
```java
// Refresh Token으로 새 Access Token 발급
String newAccessToken = tokenProvider.refreshAccessToken(refreshToken);
```

### 2. 자동 갱신
```java
// 만료 5분 전 자동 갱신
if (getRemainingTime(token) < 300) {
    String newToken = generateAccessToken(username);
    response.setHeader("X-New-Token", newToken);
}
```

### 3. 완전 재인증
```java
// Refresh Token도 만료된 경우
// 사용자는 다시 로그인 필요
```

## ⚡ 성능 최적화

### 1. SecretKey 캐싱
```java
// 생성자에서 한 번만 생성
this.secretKey = Keys.hmacShaKeyFor(keyBytes);
```

### 2. Claims 재사용
```java
// ExpiredJwtException에서도 Claims 추출
catch (ExpiredJwtException e) {
    return e.getClaims();
}
```

### 3. 권한 문자열 캐싱
```java
// 권한을 콤마로 구분된 문자열로 저장
.claim("authorities", authoritiesString)
```

## ✅ 체크리스트

### 설계 완료
- [x] Access Token 생성
- [x] Refresh Token 생성
- [x] 토큰 검증
- [x] Claims 추출
- [x] 토큰 갱신
- [x] 권한 정보 처리
- [x] 만료 시간 관리
- [x] TokenInfo DTO

### 다음 구현
- [ ] JwtProperties
- [ ] TokenBlacklistService
- [ ] SecurityAuditService

---
*JwtTokenProvider 설계 완료 - 다음: JwtProperties 설계*