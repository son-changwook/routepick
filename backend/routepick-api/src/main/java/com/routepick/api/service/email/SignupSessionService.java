package com.routepick.api.service.email;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.routepick.common.domain.session.SignupSession;
import com.routepick.api.util.SecureLogger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SignupSessionService {
    
    // 메모리 기반 세션 저장소 (실제로는 Redis나 DB 사용 권장)
    private final ConcurrentHashMap<String, SignupSession> sessions = new ConcurrentHashMap<>();
    
    // 등록 토큰 저장소 (메모리 기반)
    private final ConcurrentHashMap<String, String> registrationTokens = new ConcurrentHashMap<>();
    
    /**
     * 세션 생성
     * @param email 이메일
     * @return 세션 ID (UUID)
     */
    public String createSession(String email) {
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(5); // 5분 후 만료
        
        SignupSession session = SignupSession.builder()
            .sessionId(sessionId)
            .email(email)
            .verificationCode("") // EmailVerificationService에서 설정
            .createdAt(now)
            .expiresAt(expiresAt)
            .isVerified(false)
            .build();
        
        sessions.put(sessionId, session);
        SecureLogger.logWithMaskedEmail("세션 생성 완료: sessionId={}, email={}", 
            email, sessionId.substring(0, 8) + "***");
        
        return sessionId;
    }
    
    /**
     * 세션 조회
     * @param sessionId 세션 ID
     * @return 세션 정보 (Optional)
     */
    public Optional<SignupSession> getSession(String sessionId) {
        SignupSession session = sessions.get(sessionId);
        
        if (session == null) {
            log.warn("세션을 찾을 수 없음: sessionId={}", sessionId);
            return Optional.empty();
        }
        
        // 만료된 세션인지 확인
        if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
            log.warn("만료된 세션: sessionId={}", sessionId);
            sessions.remove(sessionId);
            return Optional.empty();
        }
        
        return Optional.of(session);
    }
    
    /**
     * 세션 인증 완료 처리
     * @param sessionId 세션 ID
     */
    public void markSessionVerified(String sessionId) {
        SignupSession session = sessions.get(sessionId);
        if (session != null) {
            session.setVerified(true);
            log.info("세션 인증 완료: sessionId={}", sessionId);
        }
    }
    
    /**
     * 세션에 인증 코드 설정
     * @param sessionId 세션 ID
     * @param verificationCode 인증 코드
     */
    public void setVerificationCode(String sessionId, String verificationCode) {
        SignupSession session = sessions.get(sessionId);
        if (session != null) {
            session.setVerificationCode(verificationCode);
            log.debug("인증 코드 설정: sessionId={}, code=***", sessionId);
        }
    }
    
    /**
     * 세션에 등록 토큰 설정
     * @param sessionId 세션 ID
     * @param registrationToken 등록 토큰
     */
    public void setRegistrationToken(String sessionId, String registrationToken) {
        SignupSession session = sessions.get(sessionId);
        if (session != null) {
            session.setRegistrationToken(registrationToken);
            // 등록 토큰을 별도 맵에도 저장
            registrationTokens.put(registrationToken, sessionId);
            log.debug("등록 토큰 설정: sessionId={}, token=***", sessionId);
        }
    }
    
    /**
     * 만료된 세션 정리 (스케줄링)
     */
    @Scheduled(fixedRate = 300000) // 5분마다
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        AtomicInteger expiredCount = new AtomicInteger(0);
        
        sessions.entrySet().removeIf(entry -> {
            boolean expired = now.isAfter(entry.getValue().getExpiresAt());
            if (expired) {
                expiredCount.incrementAndGet();
                log.debug("만료된 세션 제거: sessionId={}", entry.getKey());
            }
            return expired;
        });
        
        int count = expiredCount.get();
        if (count > 0) {
            log.info("만료된 세션 {}개 정리 완료", count);
        }
    }

    /**
     * 등록 토큰 검증
     * @param registrationToken 등록 토큰
     * @param email 이메일
     * @return 검증 성공 여부
     */
    public boolean validateRegistrationToken(String registrationToken, String email) {
        log.info("등록 토큰 검증 요청: token={}, email={}", registrationToken, email);
        
        try {
            // 등록 토큰으로 세션 ID 찾기
            String sessionId = registrationTokens.get(registrationToken);
            if (sessionId == null) {
                log.warn("유효하지 않은 등록 토큰: token={}", registrationToken);
                return false;
            }
            
            // 세션 ID로 세션 찾기
            var sessionOpt = getSession(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("등록 토큰에 해당하는 세션을 찾을 수 없음: token={}, sessionId={}", registrationToken, sessionId);
                return false;
            }
            
            SignupSession session = sessionOpt.get();
            
            // 이메일 일치 확인
            if (!email.equals(session.getEmail())) {
                log.warn("이메일 불일치: sessionEmail={}, requestEmail={}", session.getEmail(), email);
                return false;
            }
            
            // 인증 완료 확인
            if (!session.isVerified()) {
                log.warn("인증되지 않은 세션: token={}", registrationToken);
                return false;
            }
            
            // 만료 확인
            if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
                log.warn("만료된 등록 토큰: token={}", registrationToken);
                return false;
            }
            
            log.info("등록 토큰 검증 성공: token={}, email={}", registrationToken, email);
            return true;
            
        } catch (Exception e) {
            log.error("등록 토큰 검증 실패: token={}, error={}", registrationToken, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 등록 토큰 사용 후 세션 삭제
     * @param registrationToken 등록 토큰
     */
    public void consumeRegistrationToken(String registrationToken) {
        log.info("등록 토큰 사용: token={}", registrationToken);
        
        // 등록 토큰으로 세션 ID 찾기
        String sessionId = registrationTokens.get(registrationToken);
        if (sessionId != null) {
            // 세션 삭제
            sessions.remove(sessionId);
            // 등록 토큰도 삭제
            registrationTokens.remove(registrationToken);
            log.debug("등록 토큰 및 세션 삭제 완료: token={}, sessionId={}", registrationToken, sessionId);
        }
    }
}
