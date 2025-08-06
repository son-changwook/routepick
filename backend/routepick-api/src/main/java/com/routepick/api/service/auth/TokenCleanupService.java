package com.routepick.api.service.auth;

import com.routepick.api.mapper.ApiTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 토큰 정리 서비스
 * 만료된 토큰과 무효화된 토큰을 정기적으로 정리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {
    private final ApiTokenMapper apiTokenMapper;
    
    @Value("${token.cleanup.enabled:true}")
    private boolean cleanupEnabled;
    
    @Value("${token.cleanup.initial-delay-seconds:60}")
    private int initialDelaySeconds;
    
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private LocalDateTime startupTime;

    /**
     * 애플리케이션 시작 완료 후 초기화
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        startupTime = LocalDateTime.now();
        log.info("TokenCleanupService 초기화 완료 - 시작 시간: {}", startupTime);
        isInitialized.set(true);
    }

    /**
     * 매일 새벽 2시에 만료된 토큰 정리
     * 서버 시작 후 초기 지연 시간이 지난 후에만 실행
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        if (!isServiceReady()) {
            log.debug("TokenCleanupService가 아직 준비되지 않았습니다. 스킵합니다.");
            return;
        }
        
        try {
            log.info("만료된 토큰 정리 시작");
            int deletedCount = apiTokenMapper.deleteExpiredTokens();
            log.info("만료된 토큰 정리 완료: {}개 삭제", deletedCount);
        } catch (Exception e) {
            log.error("만료된 토큰 정리 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 매시간 만료된 토큰 확인
     * 서버 시작 후 초기 지연 시간이 지난 후에만 실행
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void checkExpiredTokens() {
        if (!isServiceReady()) {
            log.debug("TokenCleanupService가 아직 준비되지 않았습니다. 스킵합니다.");
            return;
        }
        
        try {
            log.debug("만료된 토큰 상태 확인");
            int deletedCount = apiTokenMapper.deleteExpiredTokens();
            if (deletedCount > 0) {
                log.info("만료된 토큰 발견 및 정리: {}개", deletedCount);
            }
        } catch (Exception e) {
            log.error("만료된 토큰 확인 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 서비스가 실행 준비가 되었는지 확인
     */
    private boolean isServiceReady() {
        if (!cleanupEnabled) {
            log.debug("토큰 정리 기능이 비활성화되어 있습니다.");
            return false;
        }
        
        if (!isInitialized.get()) {
            log.debug("TokenCleanupService가 아직 초기화되지 않았습니다.");
            return false;
        }
        
        if (startupTime == null) {
            log.debug("시작 시간이 설정되지 않았습니다.");
            return false;
        }
        
        // 초기 지연 시간이 지났는지 확인
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime readyTime = startupTime.plusSeconds(initialDelaySeconds);
        
        if (now.isBefore(readyTime)) {
            log.debug("초기 지연 시간이 아직 지나지 않았습니다. 남은 시간: {}초", 
                java.time.Duration.between(now, readyTime).getSeconds());
            return false;
        }
        
        return true;
    }
} 