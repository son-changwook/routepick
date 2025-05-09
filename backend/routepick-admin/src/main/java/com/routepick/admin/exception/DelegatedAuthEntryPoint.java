package com.routepick.admin.exception;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 관리자 애플리케이션의 인증 예외를 전역 예외 처리기로 위임하는 컴포넌트.
 * 관리자 전용 보안 정책과 예외 처리를 포함합니다.
 */
@Slf4j
@Component("delegatedAuthEntryPoint")
public class DelegatedAuthEntryPoint implements AuthenticationEntryPoint {

    private final @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver;

    @Value("${security.auth.max-failed-attempts:3}")
    private int maxFailedAttempts;

    @Value("${security.auth.lock-duration-minutes:60}")
    private int lockDurationMinutes;

    @Value("${security.auth.cleanup-interval-minutes:30}")
    private int cleanupIntervalMinutes;

    // IP별 인증 실패 횟수를 추적하는 맵
    private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedIps = new ConcurrentHashMap<>();

    // 동시성 제어를 위한 락
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public DelegatedAuthEntryPoint(HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        String clientIp = getClientIp(request);

        // IP 잠금 확인
        if (isIpLocked(clientIp)) {
            handleLockedIp(request, response, clientIp);
            return;
        }

        // 보안 이벤트 로깅
        logSecurityEvent(request, authException);

        // 안전한 예외 메시지 생성
        String safeMessage = getSafeExceptionMessage(authException);

        // 인증 실패 횟수 증가
        incrementFailedAttempts(clientIp);

        // 예외 처리 위임
        handlerExceptionResolver.resolveException(request, response, null, authException);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isIpLocked(String clientIp) {
        lock.readLock().lock();
        try {
            Long lockTime = lockedIps.get(clientIp);
            if (lockTime != null) {
                if (System.currentTimeMillis() - lockTime > Duration.ofMinutes(lockDurationMinutes).toMillis()) {
                    // 잠금 시간이 지났으면 잠금 해제
                    lock.readLock().unlock();
                    lock.writeLock().lock();
                    try {
                        lockedIps.remove(clientIp);
                        failedAttempts.remove(clientIp);
                        return false;
                    } finally {
                        lock.writeLock().unlock();
                        lock.readLock().lock();
                    }
                }
                return true;
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void handleLockedIp(HttpServletRequest request, HttpServletResponse response, String clientIp)
            throws IOException {
        long remainingTime = (Duration.ofMinutes(lockDurationMinutes).toMillis() -
                (System.currentTimeMillis() - lockedIps.get(clientIp))) / 1000 / 60;
        log.warn("Blocked admin access attempt from locked IP: {} for {} more minutes", clientIp, remainingTime);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.getWriter().write("관리자 접근이 일시적으로 차단되었습니다. " + remainingTime + "분 후에 다시 시도해주세요.");
    }

    private void incrementFailedAttempts(String clientIp) {
        lock.readLock().lock();
        try {
            AtomicInteger attempts = failedAttempts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
            int currentAttempts = attempts.incrementAndGet();

            if (currentAttempts >= maxFailedAttempts) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    lockedIps.put(clientIp, System.currentTimeMillis());
                    log.warn("Admin IP {} has been locked due to too many failed attempts", clientIp);
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private String getSafeExceptionMessage(AuthenticationException ex) {
        if (ex instanceof BadCredentialsException) {
            return "관리자 인증에 실패했습니다.";
        } else if (ex instanceof InsufficientAuthenticationException) {
            return "관리자 인증이 필요합니다.";
        }
        return "관리자 인증에 실패했습니다.";
    }

    private void logSecurityEvent(HttpServletRequest request, AuthenticationException ex) {
        String clientIp = getClientIp(request);
        log.warn("Admin authentication failed - URI: {}, IP: {}, Exception: {}, User-Agent: {}, Headers: {}",
                request.getRequestURI(),
                clientIp,
                ex.getClass().getSimpleName(),
                request.getHeader("User-Agent"),
                request.getHeaderNames());
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

            log.debug(
                    "Cleaned up old admin authentication failure records. Remaining entries - Locked IPs: {}, Failed attempts: {}",
                    lockedIps.size(), failedAttempts.size());
        } finally {
            lock.writeLock().unlock();
        }
    }
}
