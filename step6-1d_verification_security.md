# Step 6-1d: UserVerificationService & 보안 유틸리티 구현

> 사용자 인증 관리, 약관 동의, JWT 및 XSS 보안 유틸리티 완전 구현  
> 생성일: 2025-08-20  
> 기반: step5-1a,b,c_repositories.md, 한국 특화 본인인증 및 보안 강화

---

## 🎯 설계 목표

- **인증 관리**: 이메일/휴대폰 본인인증 처리
- **약관 동의**: AgreementType별 동의 상태 추적
- **계정 활성화**: 인증 완료 후 자동 활성화 로직
- **보안 강화**: JWT 토큰 관리, XSS 방지 유틸리티
- **한국 특화**: CI/DI 기반 본인인증, 성인 인증

---

## ✅ UserVerificationService - 사용자 인증 서비스

### UserVerificationService.java
```java
package com.routepick.service.user;

import com.routepick.common.enums.AgreementType;
import com.routepick.domain.system.entity.AgreementContent;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.entity.UserAgreement;
import com.routepick.domain.user.entity.UserVerification;
import com.routepick.domain.user.repository.AgreementContentRepository;
import com.routepick.domain.user.repository.UserAgreementRepository;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.domain.user.repository.UserVerificationRepository;
import com.routepick.exception.user.UserException;
import com.routepick.exception.validation.ValidationException;
import com.routepick.service.auth.AuthService;
import com.routepick.service.email.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 사용자 인증 서비스
 * - 이메일 인증 처리
 * - 약관 동의 관리
 * - 계정 활성화
 * - 인증 상태 추적
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserVerificationService {
    
    private final UserRepository userRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final AgreementContentRepository agreementContentRepository;
    private final EmailService emailService;
    private final AuthService authService;
    
    // ===== 이메일 인증 =====
    
    /**
     * 이메일 인증 코드 확인
     */
    @Transactional
    public void verifyEmail(String email, String verificationCode) {
        log.info("이메일 인증 시도: email={}", email);
        
        // 인증 코드 검증
        if (!emailService.verifyCode(email, verificationCode)) {
            throw ValidationException.invalidVerificationCode();
        }
        
        // 사용자 조회
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> UserException.notFoundByEmail(email));
        
        // 인증 정보 업데이트
        UserVerification verification = userVerificationRepository
            .findByUserId(user.getUserId())
            .orElseGet(() -> UserVerification.builder()
                .user(user)
                .build());
        
        verification.setEmailVerified(true);
        verification.setVerificationDate(LocalDateTime.now());
        userVerificationRepository.save(verification);
        
        // 계정 활성화 (이메일 인증 후)
        if (user.getUserStatus() == UserStatus.INACTIVE) {
            authService.activateAccount(user.getUserId());
        }
        
        // 환영 이메일 발송
        emailService.sendWelcomeEmail(user);
        
        log.info("이메일 인증 완료: userId={}, email={}", user.getUserId(), email);
    }
    
    /**
     * 이메일 인증 재발송
     */
    @Transactional
    public void resendVerificationEmail(String email) {
        log.info("이메일 인증 재발송 요청: email={}", email);
        
        // 재발송 가능 여부 확인
        if (!emailService.canResendVerification(email)) {
            throw ValidationException.verificationCooldown();
        }
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> UserException.notFoundByEmail(email));
        
        // 이미 인증된 경우
        UserVerification verification = userVerificationRepository
            .findByUserId(user.getUserId())
            .orElse(null);
        
        if (verification != null && verification.isEmailVerified()) {
            throw ValidationException.alreadyVerified();
        }
        
        // 인증 메일 재발송
        emailService.sendVerificationEmail(user);
        
        log.info("이메일 인증 재발송 완료: email={}", email);
    }
    
    // ===== 휴대폰 인증 =====
    
    /**
     * 휴대폰 본인인증 처리
     */
    @Transactional
    public void verifyPhone(Long userId, String ci, String di, String realName, 
                           String birthDate, String gender, String phoneNumber) {
        log.info("휴대폰 본인인증 처리: userId={}", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> UserException.notFound(userId));
        
        // CI/DI 중복 확인
        userVerificationRepository.findByCi(ci).ifPresent(existing -> {
            if (!existing.getUser().getUserId().equals(userId)) {
                throw UserException.duplicateVerification();
            }
        });
        
        // 인증 정보 저장
        UserVerification verification = userVerificationRepository
            .findByUserId(userId)
            .orElseGet(() -> UserVerification.builder()
                .user(user)
                .build());
        
        verification.completeVerification(ci, di, realName, birthDate, gender, phoneNumber);
        userVerificationRepository.save(verification);
        
        // 사용자 휴대폰 번호 업데이트
        if (user.getPhone() == null) {
            user.setPhone(phoneNumber);
            userRepository.save(user);
        }
        
        log.info("휴대폰 본인인증 완료: userId={}, phoneNumber={}", userId, phoneNumber);
    }
    
    // ===== 약관 동의 관리 =====
    
    /**
     * 약관 동의 처리
     */
    @Transactional
    public void agreeToTerms(Long userId, Map<AgreementType, Boolean> agreements, 
                            HttpServletRequest request) {
        log.info("약관 동의 처리: userId={}, agreements={}", userId, agreements);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> UserException.notFound(userId));
        
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        
        agreements.forEach((type, agreed) -> {
            // 현재 활성 약관 조회
            AgreementContent content = agreementContentRepository
                .findActiveByAgreementType(type)
                .orElseThrow(() -> ValidationException.agreementNotFound(type));
            
            // 기존 동의 이력 조회
            UserAgreement agreement = userAgreementRepository
                .findLatestByUserIdAndAgreementType(user.getUserId(), type)
                .orElseGet(() -> UserAgreement.builder()
                    .user(user)
                    .agreementContent(content)
                    .agreementType(type)
                    .build());
            
            if (agreed) {
                agreement.agree(ipAddress, userAgent);
            } else {
                // 필수 약관은 거부 불가
                if (type.isRequired()) {
                    throw ValidationException.requiredAgreementRejected(type);
                }
                agreement.disagree();
            }
            
            userAgreementRepository.save(agreement);
        });
        
        log.info("약관 동의 처리 완료: userId={}", userId);
    }
    
    /**
     * 사용자 약관 동의 상태 조회
     */
    public Map<AgreementType, Boolean> getUserAgreements(Long userId) {
        List<UserAgreement> agreements = userAgreementRepository.findByUserId(userId);
        
        return agreements.stream()
            .collect(Collectors.toMap(
                UserAgreement::getAgreementType,
                UserAgreement::isAgreed,
                (existing, replacement) -> replacement // 최신 동의 상태 유지
            ));
    }
    
    /**
     * 특정 약관 동의 여부 확인
     */
    public boolean hasAgreedTo(Long userId, AgreementType agreementType) {
        return userAgreementRepository
            .findLatestByUserIdAndAgreementType(userId, agreementType)
            .map(UserAgreement::isAgreed)
            .orElse(false);
    }
    
    /**
     * 필수 약관 모두 동의했는지 확인
     */
    public boolean hasAllRequiredAgreements(Long userId) {
        return userAgreementRepository.hasAllRequiredAgreements(userId);
    }
    
    // ===== 계정 활성화 =====
    
    /**
     * 계정 활성화 체크
     */
    public boolean canActivateAccount(Long userId) {
        // 이메일 인증 확인
        UserVerification verification = userVerificationRepository
            .findByUserId(userId)
            .orElse(null);
        
        if (verification == null || !verification.isEmailVerified()) {
            return false;
        }
        
        // 필수 약관 동의 확인
        return hasAllRequiredAgreements(userId);
    }
    
    /**
     * 계정 활성화
     */
    @Transactional
    public void activateAccount(Long userId) {
        if (!canActivateAccount(userId)) {
            throw ValidationException.cannotActivateAccount();
        }
        
        authService.activateAccount(userId);
        
        log.info("계정 활성화 완료: userId={}", userId);
    }
    
    // ===== 인증 상태 추적 =====
    
    /**
     * 사용자 인증 상태 조회
     */
    public UserVerification getUserVerification(Long userId) {
        return userVerificationRepository
            .findByUserId(userId)
            .orElse(null);
    }
    
    /**
     * 인증 완료 여부 확인
     */
    public boolean isFullyVerified(Long userId) {
        UserVerification verification = getUserVerification(userId);
        return verification != null && verification.isFullyVerified();
    }
    
    /**
     * 성인 인증 여부 확인
     */
    public boolean isAdultVerified(Long userId) {
        UserVerification verification = getUserVerification(userId);
        return verification != null && verification.isAdultVerified();
    }
    
    // ===== Helper 메서드 =====
    
    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
```

---

## 🔒 보안 유틸리티 클래스

### JwtTokenProvider.java
```java
package com.routepick.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 토큰 제공자
 */
@Slf4j
@Component
public class JwtTokenProvider {
    
    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;
    
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-validity-seconds:1800}") long accessTokenValiditySeconds,
            @Value("${app.jwt.refresh-token-validity-seconds:604800}") long refreshTokenValiditySeconds) {
        
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }
    
    /**
     * Access Token 생성 (30분)
     */
    public String createAccessToken(Long userId, String email) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValiditySeconds * 1000);
        
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("email", email)
            .claim("type", "ACCESS")
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }
    
    /**
     * Refresh Token 생성 (7일)
     */
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshTokenValiditySeconds * 1000);
        
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("type", "REFRESH")
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }
    
    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 토큰에서 사용자 ID 추출
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
        
        return Long.parseLong(claims.getSubject());
    }
    
    /**
     * 토큰에서 이메일 추출
     */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
        
        return claims.get("email", String.class);
    }
    
    /**
     * 토큰 타입 확인
     */
    public String getTokenType(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
        
        return claims.get("type", String.class);
    }
    
    /**
     * 토큰 만료 시간 조회
     */
    public Date getExpirationFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
        
        return claims.getExpiration();
    }
    
    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }
    
    public long getRefreshTokenValiditySeconds() {
        return refreshTokenValiditySeconds;
    }
}
```

### XssProtectionUtil.java
```java
package com.routepick.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * XSS 방지 유틸리티
 */
public class XssProtectionUtil {
    
    private static final Safelist SAFELIST = Safelist.relaxed()
        .addTags("h1", "h2", "h3", "h4", "h5", "h6")
        .addAttributes("a", "href", "target")
        .addProtocols("a", "href", "http", "https");
    
    /**
     * HTML 태그 제거 및 안전한 텍스트 반환
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        
        return Jsoup.clean(input, SAFELIST);
    }
    
    /**
     * 모든 HTML 태그 제거
     */
    public static String stripHtml(String input) {
        if (input == null) {
            return null;
        }
        
        return Jsoup.clean(input, Safelist.none());
    }
    
    /**
     * 스크립트 태그만 제거
     */
    public static String removeScripts(String input) {
        if (input == null) {
            return null;
        }
        
        return Jsoup.clean(input, Safelist.basic());
    }
    
    /**
     * 사용자 입력 검증 (닉네임, 이름 등)
     */
    public static String sanitizeUserInput(String input) {
        if (input == null) {
            return null;
        }
        
        // HTML 태그 완전 제거
        String cleaned = stripHtml(input);
        
        // 특수문자 제거 (한글, 영문, 숫자만 허용)
        return cleaned.replaceAll("[^가-힣a-zA-Z0-9\\s]", "");
    }
    
    /**
     * URL 검증 및 안전한 URL 반환
     */
    public static String sanitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        
        // 기본적인 URL 패턴 검증
        if (!url.matches("^https?://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?.*$")) {
            return null;
        }
        
        return stripHtml(url);
    }
}
```

---

## 📋 약관 동의 시스템 상세 설계

### 1. AgreementType Enum
```java
public enum AgreementType {
    TERMS(true, "이용약관"),           // 필수
    PRIVACY(true, "개인정보처리방침"),   // 필수
    MARKETING(false, "마케팅 정보 수신"), // 선택
    LOCATION(false, "위치정보 이용");   // 선택
    
    private final boolean required;
    private final String displayName;
}
```

### 2. 약관 동의 프로세스
```java
// 1. 현재 활성 약관 조회
AgreementContent content = agreementContentRepository
    .findActiveByAgreementType(type);

// 2. 사용자 동의 이력 생성/업데이트
UserAgreement agreement = UserAgreement.builder()
    .user(user)
    .agreementContent(content)
    .agreementType(type)
    .build();

// 3. 동의 처리 (IP, UserAgent 기록)
agreement.agree(ipAddress, userAgent);
```

### 3. 필수 약관 검증
```java
// 모든 필수 약관에 동의했는지 확인
public boolean hasAllRequiredAgreements(Long userId) {
    return Arrays.stream(AgreementType.values())
        .filter(AgreementType::isRequired)
        .allMatch(type -> hasAgreedTo(userId, type));
}
```

---

## 🔐 한국 특화 본인인증 시스템

### 1. CI/DI 기반 인증
```java
// CI (Connecting Information): 개인식별정보
// DI (Duplication Information): 중복가입확인정보
public void verifyPhone(Long userId, String ci, String di, ...) {
    // CI 중복 확인 (한 사람이 여러 계정 생성 방지)
    userVerificationRepository.findByCi(ci).ifPresent(existing -> {
        if (!existing.getUser().getUserId().equals(userId)) {
            throw UserException.duplicateVerification();
        }
    });
}
```

### 2. 성인 인증 로직
```java
// 생년월일 기반 성인 여부 확인
public boolean isAdultVerified(Long userId) {
    UserVerification verification = getUserVerification(userId);
    return verification != null && verification.isAdultVerified();
}

// UserVerification 엔티티 내 isAdultVerified() 메서드
public boolean isAdultVerified() {
    if (birthDate == null) return false;
    
    LocalDate today = LocalDate.now();
    LocalDate birth = LocalDate.parse(birthDate);
    
    return Period.between(birth, today).getYears() >= 19;
}
```

### 3. 인증 완료 체크
```java
// 이메일 + 휴대폰 + 필수약관 모두 완료
public boolean isFullyVerified(Long userId) {
    UserVerification verification = getUserVerification(userId);
    boolean emailVerified = verification != null && verification.isEmailVerified();
    boolean phoneVerified = verification != null && verification.isPhoneVerified();
    boolean agreementsCompleted = hasAllRequiredAgreements(userId);
    
    return emailVerified && phoneVerified && agreementsCompleted;
}
```

---

## ✅ 구현 완료 체크리스트

### ✅ 인증 관리
- [x] 이메일 인증 처리 (EmailService 연동)
- [x] 이메일 인증 재발송 (쿨타임 검증)
- [x] 휴대폰 본인인증 (CI/DI 기반)
- [x] 중복 인증 방지 (CI 기반 검증)
- [x] 성인 인증 (생년월일 기반)

### 📋 약관 동의
- [x] 4가지 약관 타입 지원 (TERMS, PRIVACY, MARKETING, LOCATION)
- [x] 필수/선택 약관 구분
- [x] 동의 이력 추적 (IP, UserAgent 기록)
- [x] 최신 약관 버전 관리
- [x] 필수 약관 완료 검증

### 🔓 계정 활성화
- [x] 이메일 인증 후 자동 활성화
- [x] 계정 활성화 조건 검증
- [x] 인증 상태 추적
- [x] 완전 인증 여부 확인
- [x] AuthService 연동

### 🔒 보안 유틸리티
- [x] JWT 토큰 생성/검증 (ACCESS/REFRESH)
- [x] 토큰 정보 추출 (userId, email, type)
- [x] XSS 방지 (HTML 태그 제거)
- [x] 사용자 입력 검증 (한글/영문/숫자만)
- [x] URL 검증 및 안전화

---

**세분화 완료**: Step 6-1 모든 파일 생성 완료  
**총 4개 파일**: AuthService, EmailService, UserService, VerificationService & 보안 유틸리티  
**핵심 성과**: 인증 및 사용자 관리 Service 레이어 완전 구현

*완료일: 2025-08-20*  
*핵심 성과: 한국 특화 본인인증 시스템 및 보안 강화 완전 구현*