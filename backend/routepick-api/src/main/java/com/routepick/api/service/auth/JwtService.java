package com.routepick.api.service.auth;

import com.routepick.api.config.JwtConfig;
import com.routepick.api.security.CustomUserDetails;
import com.routepick.common.domain.user.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;
    
    /**
     * 액세스 토큰 생성 (User 객체 사용 - 기존 호환성)
     */
    public String generateAccessToken(User user) {
        return generateToken(user, jwtConfig.getExpiration(), "ACCESS");
    }
    
    /**
     * 리프레시 토큰 생성 (User 객체 사용 - 기존 호환성)
     */
    public String generateRefreshToken(User user) {
        return generateToken(user, jwtConfig.getRefreshExpiration(), "REFRESH");
    }
    
    /**
     * 액세스 토큰 생성 (CustomUserDetails 사용 - 닉네임 포함)
     */
    public String generateAccessToken(CustomUserDetails userDetails) {
        return generateToken(userDetails, jwtConfig.getExpiration(), "ACCESS");
    }
    
    /**
     * 리프레시 토큰 생성 (CustomUserDetails 사용 - 닉네임 포함)
     */
    public String generateRefreshToken(CustomUserDetails userDetails) {
        return generateToken(userDetails, jwtConfig.getRefreshExpiration(), "REFRESH");
    }
    
    /**
     * 커스텀 claims로 토큰 생성 (회원가입 토큰 등에 사용)
     * @param claims 토큰에 포함할 정보
     * @param expirationSeconds 만료 시간 (초)
     * @return JWT 토큰
     */
    public String generateToken(Map<String, Object> claims, long expirationSeconds) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (expirationSeconds * 1000));
        
        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 토큰 생성 공통 메서드 (User 객체 사용)
     * JWT 표준에 맞게 구조화:
     * - sub: 사용자 식별자 (userId)
     * - iss: 발급자
     * - iat: 발급 시간
     * - exp: 만료 시간
     * - claims: 추가 정보 (email, userName, profileImageUrl, userType, tokenType)
     */
    private String generateToken(User user, long expiration, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (expiration * 1000));
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail()); // 이메일 정보
        claims.put("userName", user.getUserName()); // 사용자 실명
        claims.put("profileImageUrl", user.getProfileImageUrl()); // 마이페이지용
        claims.put("userType", user.getUserType().name());
        claims.put("tokenType", tokenType);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(user.getUserId())) // userId를 sub로 사용 (JWT 표준)
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 토큰 생성 공통 메서드 (CustomUserDetails 사용 - 닉네임 포함)
     * JWT 표준에 맞게 구조화:
     * - sub: 사용자 식별자 (userId)
     * - iss: 발급자
     * - iat: 발급 시간
     * - exp: 만료 시간
     * - claims: 추가 정보 (email, userName, nickName, profileImageUrl, userType, tokenType)
     */
    private String generateToken(CustomUserDetails userDetails, long expiration, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (expiration * 1000));
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", userDetails.getEmail()); // 이메일 정보
        claims.put("userName", userDetails.getUserName()); // 사용자 실명
        claims.put("nickName", userDetails.getNickName()); // 사용자 닉네임
        claims.put("profileImageUrl", userDetails.getProfileImageUrl()); // 마이페이지용
        claims.put("userType", "NORMAL"); // 일반 사용자
        claims.put("tokenType", tokenType);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userDetails.getUserId())) // userId를 sub로 사용 (JWT 표준)
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 토큰 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 토큰에서 사용자 ID 추출 (sub 필드)
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return Long.parseLong(claims.getSubject()); // sub 필드에서 userId 추출
    }
    
    /**
     * 토큰에서 이메일 추출 (claims에서 추출)
     */
    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("email", String.class); // claims에서 email 추출
    }
    
    /**
     * 토큰에서 사용자 실명 추출
     */
    public String getUserNameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userName", String.class);
    }
    
    /**
     * 토큰에서 사용자 닉네임 추출
     */
    public String getNickNameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("nickName", String.class);
    }
    
    /**
     * 토큰에서 프로필 이미지 URL 추출
     */
    public String getProfileImageUrlFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("profileImageUrl", String.class);
    }
    
    /**
     * 토큰에서 토큰 타입 추출
     */
    public String getTokenTypeFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("tokenType", String.class);
    }
    
    /**
     * 토큰에서 사용자 타입 추출
     */
    public String getUserTypeFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("userType", String.class);
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
     * 서명 키 생성
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecretKey().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * 토큰 만료 시간 확인
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
} 