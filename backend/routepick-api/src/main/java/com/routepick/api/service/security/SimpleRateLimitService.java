package com.routepick.api.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class SimpleRateLimitService {

    private final ConcurrentHashMap<String, RequestCounter> ipCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RequestCounter> emailCounters = new ConcurrentHashMap<>();
    private final RequestCounter globalCounter = new RequestCounter(10, 60); // 1분에 10회

    /**
     * IP 기반 Rate Limiting
     * @param ipAddress 클라이언트 IP 주소
     * @return 요청 허용 여부
     */
    public boolean tryConsumeByIp(String ipAddress) {
        RequestCounter counter = ipCounters.computeIfAbsent(ipAddress, 
            k -> new RequestCounter(3, 60)); // 1분에 3회
        
        boolean allowed = counter.tryConsume();
        if (!allowed) {
            log.warn("IP {} 에서 너무 많은 요청이 발생했습니다.", ipAddress);
        }
        return allowed;
    }

    /**
     * 이메일 기반 Rate Limiting
     * @param email 이메일 주소
     * @return 요청 허용 여부
     */
    public boolean tryConsumeByEmail(String email) {
        RequestCounter counter = emailCounters.computeIfAbsent(email, 
            k -> new RequestCounter(5, 3600)); // 1시간에 5회
        
        boolean allowed = counter.tryConsume();
        if (!allowed) {
            log.warn("이메일 {} 에서 너무 많은 요청이 발생했습니다.", email);
        }
        return allowed;
    }

    /**
     * 전역 Rate Limiting
     * @return 요청 허용 여부
     */
    public boolean tryConsumeGlobal() {
        boolean allowed = globalCounter.tryConsume();
        if (!allowed) {
            log.warn("전역 Rate Limit 초과");
        }
        return allowed;
    }

    /**
     * 요청 카운터 클래스
     */
    private static class RequestCounter {
        private final int maxRequests;
        private final int windowSeconds;
        private AtomicInteger count;
        private LocalDateTime windowStart;

        public RequestCounter(int maxRequests, int windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
            this.count = new AtomicInteger(0);
            this.windowStart = LocalDateTime.now();
        }

        public boolean tryConsume() {
            LocalDateTime now = LocalDateTime.now();
            
            // 윈도우가 지났으면 리셋
            if (now.isAfter(windowStart.plusSeconds(windowSeconds))) {
                count.set(0);
                windowStart = now;
            }
            
            // 카운트 증가 시도
            int current = count.get();
            if (current >= maxRequests) {
                return false;
            }
            
            return count.compareAndSet(current, current + 1);
        }
    }
} 