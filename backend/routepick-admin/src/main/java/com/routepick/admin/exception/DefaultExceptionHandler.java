package com.routepick.admin.exception;

import jakarta.servlet.http.HttpServletRequest;
import com.routepick.admin.exception.customExceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 관리자 애플리케이션의 전역 예외 처리기.
 * 관리자 전용 보안 정책과 예외 처리를 포함합니다.
 */
@Slf4j
@ControllerAdvice
public class DefaultExceptionHandler {

        @Value("${security.auth.max-failed-attempts:3}") // 관리자는 더 엄격한 제한
        private int maxFailedAttempts;

        @Value("${security.auth.lock-duration-minutes:60}") // 관리자는 더 긴 잠금 시간
        private int lockDurationMinutes;

        @Value("${security.auth.cleanup-interval-minutes:30}") // 관리자는 더 자주 정리
        private int cleanupIntervalMinutes;

        // IP별 인증 실패 횟수를 추적하는 맵
        private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
        private final Map<String, Long> lockedIps = new ConcurrentHashMap<>();

        // 동시성 제어를 위한 락
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        private ApiError createApiError(HttpServletRequest request, Exception ex, HttpStatus status, String errorCode) {
                String message = ex.getMessage();
                String details = null;

                // 개발 환경에서만 상세 정보 제공
                if ("dev".equals(System.getProperty("spring.profiles.active"))) {
                        details = ex.toString();
                }

                // 보안 관련 예외는 일반화된 메시지 사용
                if (ex instanceof AccessDeniedException || ex instanceof InsufficientAuthenticationException) {
                        message = "관리자 접근이 거부되었습니다.";
                }

                ApiError apiError = new ApiError(
                                request.getRequestURI(),
                                message,
                                status.value(),
                                errorCode,
                                java.time.LocalDateTime.now(),
                                details);

                // 로깅
                if (status.is5xxServerError()) {
                        log.error("Admin application error occurred: ", ex);
                } else {
                        log.warn("Admin application warning: {}", ex.getMessage());
                }

                return apiError;
        }

        @ExceptionHandler(RequestNotAcceptableException.class)
        public ResponseEntity<ApiError> handleException(
                        RequestNotAcceptableException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.NOT_ACCEPTABLE)
                                .body(createApiError(request, ex, HttpStatus.NOT_ACCEPTABLE,
                                                "ADMIN_REQUEST_NOT_ACCEPTABLE"));
        }

        @ExceptionHandler(RequestValidationException.class)
        public ResponseEntity<ApiError> handleException(
                        RequestValidationException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(createApiError(request, ex, HttpStatus.BAD_REQUEST, "ADMIN_INVALID_REQUEST"));
        }

        @ExceptionHandler(DuplicateResourceException.class)
        public ResponseEntity<ApiError> handleException(
                        DuplicateResourceException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(createApiError(request, ex, HttpStatus.CONFLICT, "ADMIN_DUPLICATE_RESOURCE"));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiError> handleException(
                        AccessDeniedException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(createApiError(request, ex, HttpStatus.FORBIDDEN, "ADMIN_ACCESS_DENIED"));
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiError> handleException(
                        ResourceNotFoundException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(createApiError(request, ex, HttpStatus.NOT_FOUND, "ADMIN_RESOURCE_NOT_FOUND"));
        }

        @ExceptionHandler(InsufficientAuthenticationException.class)
        public ResponseEntity<ApiError> handleException(
                        InsufficientAuthenticationException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(createApiError(request, ex, HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED"));
        }

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ApiError> handleException(
                        BadCredentialsException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(createApiError(request, ex, HttpStatus.UNAUTHORIZED,
                                                "ADMIN_INVALID_CREDENTIALS"));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiError> handleException(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(createApiError(request, ex, HttpStatus.BAD_REQUEST, "ADMIN_VALIDATION_FAILED"));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiError> handleException(
                        HttpMessageNotReadableException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(createApiError(request, ex, HttpStatus.BAD_REQUEST,
                                                "ADMIN_INVALID_REQUEST_BODY"));
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiError> handleException(
                        DataIntegrityViolationException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(createApiError(request, ex, HttpStatus.CONFLICT,
                                                "ADMIN_DATA_INTEGRITY_VIOLATION"));
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ApiError> handleException(
                        HttpRequestMethodNotSupportedException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(createApiError(request, ex, HttpStatus.METHOD_NOT_ALLOWED,
                                                "ADMIN_METHOD_NOT_ALLOWED"));
        }

        @ExceptionHandler(NoHandlerFoundException.class)
        public ResponseEntity<ApiError> handleException(
                        NoHandlerFoundException ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(createApiError(request, ex, HttpStatus.NOT_FOUND, "ADMIN_ENDPOINT_NOT_FOUND"));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiError> handleException(
                        Exception ex,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(createApiError(request, ex, HttpStatus.INTERNAL_SERVER_ERROR,
                                                "ADMIN_INTERNAL_SERVER_ERROR"));
        }

        /**
         * 주기적으로 오래된 IP 잠금 및 실패 시도 기록을 정리합니다.
         */
        @Scheduled(fixedRateString = "${security.auth.cleanup-interval-minutes:30}000")
        public void cleanupOldEntries() {
                lock.writeLock().lock();
                try {
                        long currentTime = System.currentTimeMillis();
                        long lockDurationMillis = Duration.ofMinutes(lockDurationMinutes).toMillis();

                        // 잠긴 IP 정리
                        lockedIps.entrySet().removeIf(entry -> currentTime - entry.getValue() > lockDurationMillis);

                        // 실패 시도 기록 정리
                        failedAttempts.entrySet().removeIf(entry -> !lockedIps.containsKey(entry.getKey()));

                        log.debug("Cleaned up old admin authentication failure records. Remaining entries - Locked IPs: {}, Failed attempts: {}",
                                        lockedIps.size(), failedAttempts.size());
                } finally {
                        lock.writeLock().unlock();
                }
        }
}