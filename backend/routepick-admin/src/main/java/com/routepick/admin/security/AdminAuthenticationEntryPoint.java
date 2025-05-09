package com.routepick.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.admin.exception.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 관리자 인증 실패 시 처리하는 진입점.
 * 인증되지 않은 요청에 대한 응답을 생성합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        log.warn("Unauthorized admin access attempt - URI: {}, IP: {}, Exception: {}",
                request.getRequestURI(),
                getClientIp(request),
                authException.getClass().getSimpleName());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError apiError = new ApiError(
                request.getRequestURI(),
                "관리자 인증이 필요합니다.",
                HttpStatus.UNAUTHORIZED.value(),
                "ADMIN_UNAUTHORIZED",
                LocalDateTime.now());

        response.getWriter().write(objectMapper.writeValueAsString(apiError));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}