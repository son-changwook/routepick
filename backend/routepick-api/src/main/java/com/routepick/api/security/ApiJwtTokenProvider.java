package com.routepick.api.security;

import com.routepick.api.service.auth.JwtService;
import com.routepick.api.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT 토큰 제공자
 * JWT 토큰의 생성, 검증, 갱신을 담당합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiJwtTokenProvider {

    private final JwtService jwtService;

    /**
     * JWT 토큰 생성
     */
    public String generateToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtService.getJwtExpirationInMs());

        return jwtService.generateToken(userDetails.getUsername(), 
            userDetails.getAuthorities().iterator().next(), now, expiryDate);
    }

    /**
     * 리프레시 토큰 생성
     * JWT 표준에 맞게 sub 필드에 userId를 저장합니다.
     */
    public String generateRefreshToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtService.getRefreshExpirationInMs());

        return jwtService.generateRefreshToken(userDetails.getUsername(), now, expiryDate);
    }

    /**
     * JWT 토큰에서 사용자 ID 추출 (sub 필드)
     */
    public String getUsernameFromJWT(String token) {
        return jwtService.getUsernameFromToken(token);
    }

    /**
     * JWT 토큰 검증
     */
    public boolean validateToken(String authToken) {
        try {
            return jwtService.validateToken(authToken);
        } catch (JwtException ex) {
            log.error("Error validating token", ex);
            return false;
        }
    }

    /**
     * 토큰 갱신
     */
    public String refreshToken(String token) {
        try {
            return jwtService.refreshToken(token);
        } catch (Exception ex) {
            log.error("Error refreshing token", ex);
            return null;
        }
    }
}