package com.routepick.api.util;

import com.routepick.api.service.security.RedisRateLimitService;
import com.routepick.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Rate Limit 체크 로직을 중앙화하는 헬퍼 클래스
 * 반복되는 Rate Limit 체크 코드를 제거하고 일관성을 보장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitHelper {
    
    private final RedisRateLimitService redisRateLimitService;
    
    /**
     * IP 기반 Rate Limit 체크
     * @param request HTTP 요청
     * @throws BusinessException Rate Limit 초과 시
     */
    public void checkIpRateLimit(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        if (!redisRateLimitService.tryConsumeByIp(clientIp)) {
            SecureLogger.logSecurityEvent("IP rate limit exceeded: {}", clientIp);
            throw BusinessException.rateLimitExceeded();
        }
    }
    
    /**
     * 이메일 기반 Rate Limit 체크
     * @param email 사용자 이메일
     * @throws BusinessException Rate Limit 초과 시
     */
    public void checkEmailRateLimit(String email) {
        if (!redisRateLimitService.tryConsumeByEmail(email)) {
            SecureLogger.logSecurityEvent("Email rate limit exceeded: {}", email);
            throw BusinessException.rateLimitExceeded();
        }
    }
    
    /**
     * 엔드포인트 기반 Rate Limit 체크
     * @param request HTTP 요청
     * @param identifier 식별자 (보통 IP 또는 사용자 ID)
     * @throws BusinessException Rate Limit 초과 시
     */
    public void checkEndpointRateLimit(HttpServletRequest request, String identifier) {
        String endpoint = request.getRequestURI();
        if (!redisRateLimitService.tryConsumeByEndpoint(endpoint, identifier)) {
            SecureLogger.logSecurityEvent("Endpoint rate limit exceeded: {} for {}", endpoint, identifier);
            throw BusinessException.rateLimitExceeded();
        }
    }
    
    /**
     * 전역 Rate Limit 체크
     * @throws BusinessException Rate Limit 초과 시
     */
    public void checkGlobalRateLimit() {
        if (!redisRateLimitService.tryConsumeGlobal()) {
            SecureLogger.logSecurityEvent("Global rate limit exceeded");
            throw BusinessException.rateLimitExceeded();
        }
    }
    
    /**
     * 복합 Rate Limit 체크 (IP + 이메일)
     * @param request HTTP 요청
     * @param email 사용자 이메일
     * @throws BusinessException Rate Limit 초과 시
     */
    public void checkCombinedRateLimit(HttpServletRequest request, String email) {
        checkIpRateLimit(request);
        checkEmailRateLimit(email);
    }
    
    /**
     * 인증 관련 Rate Limit 체크 (IP + 이메일 + 엔드포인트)
     * @param request HTTP 요청
     * @param email 사용자 이메일
     * @throws BusinessException Rate Limit 초과 시
     */
    public void checkAuthRateLimit(HttpServletRequest request, String email) {
        String clientIp = getClientIpAddress(request);
        checkIpRateLimit(request);
        checkEmailRateLimit(email);
        checkEndpointRateLimit(request, clientIp);
    }
    
    /**
     * 클라이언트 IP 주소 추출
     * 프록시 환경을 고려한 IP 주소 추출
     */
    public String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        String xForwardedProto = request.getHeader("X-Forwarded-Proto");
        if (xForwardedProto != null) {
            String proxyClientIp = request.getHeader("Proxy-Client-IP");
            if (proxyClientIp != null && !proxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(proxyClientIp)) {
                return proxyClientIp;
            }
            
            String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
            if (wlProxyClientIp != null && !wlProxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(wlProxyClientIp)) {
                return wlProxyClientIp;
            }
        }
        
        return request.getRemoteAddr();
    }
}