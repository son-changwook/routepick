package com.routepick.api.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.api.util.SecureLogger;
import com.routepick.common.domain.session.SignupSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.routepick.common.exception.ServiceException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis 기반 회원가입 세션 관리 서비스
 * 보안 강화된 세션 관리와 자동 만료 기능을 제공합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSignupSessionService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // Redis 키 패턴
    private static final String SESSION_KEY_PREFIX = "signup_session:";
    private static final String TOKEN_KEY_PREFIX = "registration_token:";
    private static final String EMAIL_SESSION_PREFIX = "email_session:";
    
    // 기본 만료 시간
    private static final Duration SESSION_EXPIRE_TIME = Duration.ofMinutes(10); // 10분
    private static final Duration TOKEN_EXPIRE_TIME = Duration.ofMinutes(30);   // 30분
    
    /**
     * 세션 생성
     * @param email 이메일
     * @return 세션 ID (UUID)
     */
    public String createSession(String email) {
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(SESSION_EXPIRE_TIME);
        
        SignupSession session = SignupSession.builder()
            .sessionId(sessionId)
            .email(email)
            .verificationCode("") // EmailVerificationService에서 설정
            .createdAt(now)
            .expiresAt(expiresAt)
            .isVerified(false)
            .build();
        
        try {
            // Redis에 세션 저장 (자동 만료 설정)
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.opsForValue().set(sessionKey, session, SESSION_EXPIRE_TIME);
            
            // 이메일별 세션 맵핑 저장 (중복 방지용)
            String emailKey = EMAIL_SESSION_PREFIX + SecureLogger.maskEmail(email);
            redisTemplate.opsForValue().set(emailKey, sessionId, SESSION_EXPIRE_TIME);
            
            SecureLogger.logWithMaskedEmail("세션 생성 완료: sessionId={}, email={}", 
                email, sessionId.substring(0, 8) + "***");
            
            return sessionId;
            
        } catch (Exception e) {
            log.error("세션 생성 실패: sessionId={}, error={}", sessionId, e.getMessage(), e);
            throw ServiceException.sessionCreationFailed(e);
        }
    }
    
    /**
     * 세션 조회
     * @param sessionId 세션 ID
     * @return 세션 정보 (Optional)
     */
    public Optional<SignupSession> getSession(String sessionId) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            Object sessionObj = redisTemplate.opsForValue().get(sessionKey);
            
            if (sessionObj == null) {
                log.debug("세션을 찾을 수 없음: sessionId={}", sessionId.substring(0, 8) + "***");
                return Optional.empty();
            }
            
            SignupSession session;
            if (sessionObj instanceof SignupSession) {
                session = (SignupSession) sessionObj;
            } else {
                // JSON 문자열인 경우 역직렬화
                String sessionJson = sessionObj.toString();
                session = objectMapper.readValue(sessionJson, SignupSession.class);
            }
            
            // 만료 체크 (Redis TTL과 별도로 애플리케이션 레벨에서도 체크)
            if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
                log.debug("만료된 세션: sessionId={}", sessionId.substring(0, 8) + "***");
                deleteSession(sessionId);
                return Optional.empty();
            }
            
            return Optional.of(session);
            
        } catch (Exception e) {
            log.error("세션 조회 실패: sessionId={}, error={}", 
                sessionId.substring(0, 8) + "***", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * 세션 인증 완료 처리
     * @param sessionId 세션 ID
     */
    public void markSessionVerified(String sessionId) {
        try {
            Optional<SignupSession> sessionOpt = getSession(sessionId);
            if (sessionOpt.isPresent()) {
                SignupSession session = sessionOpt.get();
                session.setVerified(true);
                
                // 업데이트된 세션 저장
                String sessionKey = SESSION_KEY_PREFIX + sessionId;
                redisTemplate.opsForValue().set(sessionKey, session, SESSION_EXPIRE_TIME);
                
                log.info("세션 인증 완료: sessionId={}", sessionId.substring(0, 8) + "***");
            }
        } catch (Exception e) {
            log.error("세션 인증 처리 실패: sessionId={}, error={}", 
                sessionId.substring(0, 8) + "***", e.getMessage(), e);
        }
    }
    
    /**
     * 세션에 인증 코드 설정
     * @param sessionId 세션 ID
     * @param verificationCode 인증 코드
     */
    public void setVerificationCode(String sessionId, String verificationCode) {
        try {
            Optional<SignupSession> sessionOpt = getSession(sessionId);
            if (sessionOpt.isPresent()) {
                SignupSession session = sessionOpt.get();
                session.setVerificationCode(verificationCode);
                
                // 업데이트된 세션 저장
                String sessionKey = SESSION_KEY_PREFIX + sessionId;
                redisTemplate.opsForValue().set(sessionKey, session, SESSION_EXPIRE_TIME);
                
                log.debug("인증 코드 설정: sessionId={}, code=***", sessionId.substring(0, 8) + "***");
            }
        } catch (Exception e) {
            log.error("인증 코드 설정 실패: sessionId={}, error={}", 
                sessionId.substring(0, 8) + "***", e.getMessage(), e);
        }
    }
    
    /**
     * 세션에 등록 토큰 설정
     * @param sessionId 세션 ID
     * @param registrationToken 등록 토큰
     */
    public void setRegistrationToken(String sessionId, String registrationToken) {
        try {
            Optional<SignupSession> sessionOpt = getSession(sessionId);
            if (sessionOpt.isPresent()) {
                SignupSession session = sessionOpt.get();
                session.setRegistrationToken(registrationToken);
                
                // 세션 업데이트
                String sessionKey = SESSION_KEY_PREFIX + sessionId;
                redisTemplate.opsForValue().set(sessionKey, session, SESSION_EXPIRE_TIME);
                
                // 등록 토큰으로 세션 ID 조회가 가능하도록 별도 저장
                String tokenKey = TOKEN_KEY_PREFIX + registrationToken;
                redisTemplate.opsForValue().set(tokenKey, sessionId, TOKEN_EXPIRE_TIME);
                
                log.debug("등록 토큰 설정: sessionId={}, token=***", sessionId.substring(0, 8) + "***");
            }
        } catch (Exception e) {
            log.error("등록 토큰 설정 실패: sessionId={}, error={}", 
                sessionId.substring(0, 8) + "***", e.getMessage(), e);
        }
    }
    
    /**
     * 등록 토큰 검증
     * @param registrationToken 등록 토큰
     * @param email 이메일
     * @return 검증 성공 여부
     */
    public boolean validateRegistrationToken(String registrationToken, String email) {
        SecureLogger.logAuthEvent("등록 토큰 검증 요청: token={}, email={}", 
            registrationToken, email);
        
        try {
            // 등록 토큰으로 세션 ID 찾기
            String tokenKey = TOKEN_KEY_PREFIX + registrationToken;
            Object sessionIdObj = redisTemplate.opsForValue().get(tokenKey);
            
            if (sessionIdObj == null) {
                log.warn("유효하지 않은 등록 토큰: token=***");
                return false;
            }
            
            String sessionId = sessionIdObj.toString();
            
            // 세션 ID로 세션 찾기
            Optional<SignupSession> sessionOpt = getSession(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("등록 토큰에 해당하는 세션을 찾을 수 없음: token=***, sessionId={}", 
                    sessionId.substring(0, 8) + "***");
                return false;
            }
            
            SignupSession session = sessionOpt.get();
            
            // 이메일 일치 확인
            if (!email.equals(session.getEmail())) {
                SecureLogger.logSecurityEvent("이메일 불일치 - sessionEmail={}, requestEmail={}", 
                    session.getEmail(), email);
                return false;
            }
            
            // 인증 완료 확인
            if (!session.isVerified()) {
                log.warn("인증되지 않은 세션: token=***");
                return false;
            }
            
            // 만료 확인
            if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
                log.warn("만료된 등록 토큰: token=***");
                return false;
            }
            
            SecureLogger.logAuthEvent("등록 토큰 검증 성공: token={}, email={}", 
                registrationToken, email);
            return true;
            
        } catch (Exception e) {
            log.error("등록 토큰 검증 실패: token=***, error={}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 등록 토큰 사용 후 세션 삭제
     * @param registrationToken 등록 토큰
     */
    public void consumeRegistrationToken(String registrationToken) {
        log.info("등록 토큰 사용: token=***");
        
        try {
            // 등록 토큰으로 세션 ID 찾기
            String tokenKey = TOKEN_KEY_PREFIX + registrationToken;
            Object sessionIdObj = redisTemplate.opsForValue().get(tokenKey);
            
            if (sessionIdObj != null) {
                String sessionId = sessionIdObj.toString();
                
                // 세션 삭제
                deleteSession(sessionId);
                
                // 등록 토큰도 삭제
                redisTemplate.delete(tokenKey);
                
                log.debug("등록 토큰 및 세션 삭제 완료: token=***, sessionId={}", 
                    sessionId.substring(0, 8) + "***");
            }
        } catch (Exception e) {
            log.error("등록 토큰 소비 실패: token=***, error={}", e.getMessage(), e);
        }
    }
    
    /**
     * 세션 삭제
     * @param sessionId 세션 ID
     */
    public void deleteSession(String sessionId) {
        try {
            // 세션 정보 먼저 조회 (이메일 세션 키 삭제를 위해)
            Optional<SignupSession> sessionOpt = getSession(sessionId);
            
            // 세션 키 삭제
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.delete(sessionKey);
            
            // 이메일 세션 키 삭제
            if (sessionOpt.isPresent()) {
                String emailKey = EMAIL_SESSION_PREFIX + SecureLogger.maskEmail(sessionOpt.get().getEmail());
                redisTemplate.delete(emailKey);
            }
            
            log.debug("세션 삭제 완료: sessionId={}", sessionId.substring(0, 8) + "***");
            
        } catch (Exception e) {
            log.error("세션 삭제 실패: sessionId={}, error={}", 
                sessionId.substring(0, 8) + "***", e.getMessage(), e);
        }
    }
    
    /**
     * 이메일로 기존 세션 확인
     * @param email 이메일
     * @return 기존 세션 ID (Optional)
     */
    public Optional<String> findSessionByEmail(String email) {
        try {
            String emailKey = EMAIL_SESSION_PREFIX + SecureLogger.maskEmail(email);
            Object sessionIdObj = redisTemplate.opsForValue().get(emailKey);
            
            if (sessionIdObj != null) {
                return Optional.of(sessionIdObj.toString());
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("이메일로 세션 조회 실패: email={}, error={}", 
                SecureLogger.maskEmail(email), e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * 세션 통계 조회 (관리자용)
     * @return 활성 세션 수
     */
    public long getActiveSessionCount() {
        try {
            return redisTemplate.keys(SESSION_KEY_PREFIX + "*").size();
        } catch (Exception e) {
            log.error("세션 통계 조회 실패", e);
            return 0;
        }
    }
    
    /**
     * 만료된 세션 정리 (스케줄링 작업용)
     * Redis TTL로 자동 삭제되지만, 추가적인 정리 작업
     */
    public void cleanupExpiredSessions() {
        try {
            // Redis TTL이 만료된 키들은 자동으로 삭제되므로
            // 여기서는 로그만 남김
            long activeCount = getActiveSessionCount();
            log.debug("세션 정리 작업 완료 - 활성 세션 수: {}", activeCount);
            
        } catch (Exception e) {
            log.error("세션 정리 작업 실패", e);
        }
    }
}