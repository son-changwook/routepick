package com.routepick.api.service.auth;

import com.routepick.api.config.JwtConfig;
import com.routepick.api.security.CustomUserDetails;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT 토큰 생성 및 검증 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    /**
     * 액세스 토큰 생성
     */
    public String generateAccessToken(CustomUserDetails user) {
        return generateToken(user, jwtConfig.getExpiration(), "ACCESS");
    }

    /**
     * 리프레시 토큰 생성
     */
    public String generateRefreshToken(CustomUserDetails user) {
        return generateToken(user, jwtConfig.getRefreshExpiration(), "REFRESH");
    }

    /**
     * 액세스 토큰 생성 (UserDetails)
     */
    public String generateAccessToken(UserDetails userDetails) {
        return generateToken(userDetails, jwtConfig.getExpiration(), "ACCESS");
    }

    /**
     * 리프레시 토큰 생성 (UserDetails)
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return generateToken(userDetails, jwtConfig.getRefreshExpiration(), "REFRESH");
    }

    /**
     * 토큰 생성 공통 메서드
     */
    private String generateToken(CustomUserDetails user, long expiration, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("email", user.getEmail());
        claims.put("userName", user.getUserName());
        claims.put("nickName", user.getNickName());
        claims.put("profileImageUrl", user.getProfileImageUrl());
        claims.put("type", tokenType);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(user.getUserId())) // userId를 sub 필드에 저장
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 토큰 생성 공통 메서드 (UserDetails)
     */
    private String generateToken(UserDetails userDetails, long expiration, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", tokenType);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername()) // userId를 sub 필드에 저장
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * 토큰에서 특정 클레임 추출
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 토큰에서 모든 클레임 추출
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    /**
     * 토큰 만료 여부 확인
     */
    public Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * 토큰에서 만료일 추출
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * 토큰 검증 (CustomUserDetails)
     */
    public boolean validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                log.warn("토큰이 null이거나 비어있습니다.");
                return false;
            }
            
            // 토큰 형식 검증
            if (!token.matches("^[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*$")) {
                log.warn("잘못된 JWT 토큰 형식입니다.");
                return false;
            }
            
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(30) // 30초 클록 스큐 허용
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            // 추가 검증
            if (claims.getExpiration() == null) {
                log.warn("토큰에 만료 시간이 없습니다.");
                return false;
            }
            
            if (claims.getIssuedAt() == null) {
                log.warn("토큰에 발급 시간이 없습니다.");
                return false;
            }
            
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JWT 만료 시간 반환
     */
    public long getJwtExpirationInMs() {
        return jwtConfig.getExpiration();
    }

    /**
     * JWT 리프레시 만료 시간 반환
     */
    public long getRefreshExpirationInMs() {
        return jwtConfig.getRefreshExpiration();
    }

    /**
     * 토큰 생성 (사용자명과 권한 기반)
     */
    public String generateToken(String username, org.springframework.security.core.GrantedAuthority authorities, Date now, Date expiryDate) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", authorities);
        claims.put("type", "USER");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 리프레시 토큰 생성
     */
    public String generateRefreshToken(String username, Date now, Date expiryDate) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "REFRESH");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 토큰 갱신
     */
    public String refreshToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getExpiration());

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 토큰에서 Claims 추출
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 토큰에서 사용자 실명 추출
     */
    public String getUserNameFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("userName", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("토큰에서 사용자 실명 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 사용자 ID 추출 (Long)
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 이메일 추출
     */
    public String getEmailFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("email", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("토큰에서 이메일 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 토큰 타입 추출
     */
    public String getTokenTypeFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("type", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("토큰에서 토큰 타입 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Claims와 만료 시간으로 토큰 생성
     */
    public String generateToken(Map<String, Object> claims, long expirationInSeconds) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (expirationInSeconds * 1000));

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 서명 키 생성 (보안 강화)
     */
    private SecretKey getSigningKey() {
        try {
            byte[] keyBytes = jwtConfig.getSecretKey().getBytes(StandardCharsets.UTF_8);
            
            // 키 길이 검증 (최소 256비트 = 32바이트)
            if (keyBytes.length < 32) {
                log.error("JWT 시크릿 키가 너무 짧습니다. 최소 32바이트가 필요합니다.");
                throw new SecurityException("JWT 시크릿 키가 너무 짧습니다.");
            }
            
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.error("JWT 서명 키 생성 실패: {}", e.getMessage(), e);
            throw new SecurityException("JWT 서명 키 생성에 실패했습니다.", e);
        }
    }
} 