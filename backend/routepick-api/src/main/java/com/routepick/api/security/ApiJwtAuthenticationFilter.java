package com.routepick.api.security;

import com.routepick.api.service.auth.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 토큰을 검증하고 인증을 처리하는 필터.
 * 모든 요청에 대해 JWT 토큰의 유효성을 검사하고, 유효한 경우 SecurityContext에 인증 정보를 설정합니다.
 * JWT 토큰의 sub 필드에서 userId를 추출하여 사용자 정보를 로드합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiJwtAuthenticationFilter extends OncePerRequestFilter {

    private final com.routepick.api.service.auth.JwtService jwtService;
    private final ApiUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
                // 블랙리스트 검증 추가
                if (tokenBlacklistService.isBlacklisted(jwt)) {
                    log.warn("블랙리스트에 있는 토큰으로 접근 시도: {}", jwt.substring(0, 10) + "***");
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // JWT 토큰의 sub 필드에서 userId 추출
                String userId = jwtService.getClaimsFromToken(jwt).getSubject();
                String displayName = jwtService.getUserNameFromToken(jwt);
                log.debug("JWT 토큰에서 추출한 userId: {}, displayName: {}", userId, displayName);
                
                // UserDetails 객체 생성 (userId로 사용자 정보 로드)
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("User '{}' (displayName: '{}') authenticated successfully", userId, displayName);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}