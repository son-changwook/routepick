package com.routepick.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.common.dto.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * API 인증 진입점
 * 인증되지 않은 요청에 대한 처리를 담당합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        log.warn("Unauthorized access attempt - URI: {}, IP: {}, Exception: {}",
                request.getRequestURI(),
                getClientIp(request),
                authException.getClass().getSimpleName());

        response.setStatus(org.springframework.http.HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse apiError = ApiResponse.error(
                org.springframework.http.HttpStatus.UNAUTHORIZED.value(),
                "로그인이 필요합니다.");

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