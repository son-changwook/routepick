# Step 6-1d1: User Verification Core Service

**파일**: `routepick-backend/src/main/java/com/routepick/service/user/UserVerificationService.java`

이 파일은 사용자 인증 관리, 약관 동의, 계정 활성화의 핵심 기능을 구현합니다.

## 📋 사용자 인증 서비스 구현

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
 * 
 * 주요 기능:
 * 1. 이메일 인증 처리
 * 2. 휴대폰 본인인증 처리
 * 3. 약관 동의 관리
 * 4. 계정 활성화 로직
 * 5. 인증 상태 추적
 * 
 * @author RoutePickr Team
 * @since 2025.08
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
    
    // ===================== 이메일 인증 =====================
    
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
    
    // ===================== 휴대폰 본인인증 =====================
    
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
    
    /**
     * 휴대폰 인증 상태 확인
     */
    public boolean isPhoneVerified(Long userId) {
        UserVerification verification = getUserVerification(userId);
        return verification != null && verification.isPhoneVerified();
    }
    
    /**
     * 성인 인증 여부 확인
     */
    public boolean isAdultVerified(Long userId) {
        UserVerification verification = getUserVerification(userId);
        return verification != null && verification.isAdultVerified();
    }
    
    // ===================== 약관 동의 관리 =====================
    
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
    
    /**
     * 현재 활성 약관 목록 조회
     */
    public List<AgreementContent> getActiveAgreements() {
        return agreementContentRepository.findAllActive();
    }
    
    // ===================== 계정 활성화 =====================
    
    /**
     * 계정 활성화 가능 여부 체크
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
    
    /**
     * 계정 비활성화
     */
    @Transactional
    public void deactivateAccount(Long userId, String reason) {
        log.info("계정 비활성화 시도: userId={}, reason={}", userId, reason);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> UserException.notFound(userId));
        
        authService.deactivateAccount(userId, reason);
        
        log.info("계정 비활성화 완료: userId={}", userId);
    }
    
    // ===================== 인증 상태 추적 =====================
    
    /**
     * 사용자 인증 상태 조회
     */
    public UserVerification getUserVerification(Long userId) {
        return userVerificationRepository
            .findByUserId(userId)
            .orElse(null);
    }
    
    /**
     * 인증 완료 여부 확인 (이메일 + 휴대폰 + 필수약관)
     */
    public boolean isFullyVerified(Long userId) {
        UserVerification verification = getUserVerification(userId);
        boolean emailVerified = verification != null && verification.isEmailVerified();
        boolean phoneVerified = verification != null && verification.isPhoneVerified();
        boolean agreementsCompleted = hasAllRequiredAgreements(userId);
        
        return emailVerified && phoneVerified && agreementsCompleted;
    }
    
    /**
     * 기본 인증 완료 여부 (이메일 + 필수약관)
     */
    public boolean isBasicVerified(Long userId) {
        UserVerification verification = getUserVerification(userId);
        boolean emailVerified = verification != null && verification.isEmailVerified();
        boolean agreementsCompleted = hasAllRequiredAgreements(userId);
        
        return emailVerified && agreementsCompleted;
    }
    
    /**
     * 인증 진행률 계산 (0-100%)
     */
    public int getVerificationProgress(Long userId) {
        int totalSteps = 3; // 이메일, 휴대폰, 약관
        int completedSteps = 0;
        
        UserVerification verification = getUserVerification(userId);
        
        // 이메일 인증
        if (verification != null && verification.isEmailVerified()) {
            completedSteps++;
        }
        
        // 휴대폰 인증
        if (verification != null && verification.isPhoneVerified()) {
            completedSteps++;
        }
        
        // 필수 약관 동의
        if (hasAllRequiredAgreements(userId)) {
            completedSteps++;
        }
        
        return (completedSteps * 100) / totalSteps;
    }
    
    // ===================== Helper 메서드 =====================
    
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
    
    /**
     * 인증 정보 유효성 검증
     */
    private void validateVerificationData(String ci, String di, String realName, 
                                        String birthDate, String gender, String phoneNumber) {
        if (ci == null || ci.length() != 88) {
            throw ValidationException.invalidCi();
        }
        
        if (di == null || di.length() != 64) {
            throw ValidationException.invalidDi();
        }
        
        if (realName == null || realName.trim().isEmpty()) {
            throw ValidationException.invalidRealName();
        }
        
        if (birthDate == null || !birthDate.matches("\\d{8}")) {
            throw ValidationException.invalidBirthDate();
        }
        
        if (gender == null || (!gender.equals("M") && !gender.equals("F"))) {
            throw ValidationException.invalidGender();
        }
        
        if (phoneNumber == null || !phoneNumber.matches("^01[0-9]-\\d{4}-\\d{4}$")) {
            throw ValidationException.invalidPhoneNumber();
        }
    }
}
```

## 📋 약관 동의 시스템 상세 설계

### AgreementType Enum 정의

```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 약관 동의 유형
 */
@Getter
@RequiredArgsConstructor
public enum AgreementType {
    TERMS(true, "이용약관"),           // 필수
    PRIVACY(true, "개인정보처리방침"),   // 필수
    MARKETING(false, "마케팅 정보 수신"), // 선택
    LOCATION(false, "위치정보 이용");   // 선택
    
    private final boolean required;
    private final String displayName;
}
```

### 약관 동의 프로세스 구현

```java
/**
 * 약관 동의 처리 워크플로우
 */
@Component
public class AgreementWorkflow {
    
    /**
     * 1. 현재 활성 약관 조회
     */
    public AgreementContent getActiveAgreement(AgreementType type) {
        return agreementContentRepository
            .findActiveByAgreementType(type)
            .orElseThrow(() -> ValidationException.agreementNotFound(type));
    }
    
    /**
     * 2. 사용자 동의 이력 생성/업데이트
     */
    public UserAgreement createOrUpdateAgreement(User user, AgreementContent content, 
                                               AgreementType type) {
        return userAgreementRepository
            .findLatestByUserIdAndAgreementType(user.getUserId(), type)
            .orElseGet(() -> UserAgreement.builder()
                .user(user)
                .agreementContent(content)
                .agreementType(type)
                .build());
    }
    
    /**
     * 3. 동의 처리 (IP, UserAgent 기록)
     */
    public void processAgreement(UserAgreement agreement, boolean agreed, 
                               String ipAddress, String userAgent) {
        if (agreed) {
            agreement.agree(ipAddress, userAgent);
        } else {
            if (agreement.getAgreementType().isRequired()) {
                throw ValidationException.requiredAgreementRejected(agreement.getAgreementType());
            }
            agreement.disagree();
        }
    }
}
```

### 한국 특화 본인인증 시스템

```java
/**
 * CI/DI 기반 본인인증 처리
 */
@Component
public class KoreanVerificationProcessor {
    
    /**
     * CI (Connecting Information): 개인식별정보
     * DI (Duplication Information): 중복가입확인정보
     */
    public void processKoreanVerification(Long userId, String ci, String di, 
                                        String realName, String birthDate, 
                                        String gender, String phoneNumber) {
        
        // 1. CI 중복 확인 (한 사람이 여러 계정 생성 방지)
        validateCiDuplication(userId, ci);
        
        // 2. 인증 데이터 유효성 검증
        validateVerificationData(ci, di, realName, birthDate, gender, phoneNumber);
        
        // 3. 성인 여부 확인
        boolean isAdult = calculateIsAdult(birthDate);
        
        // 4. 인증 정보 저장
        saveVerificationData(userId, ci, di, realName, birthDate, gender, phoneNumber, isAdult);
    }
    
    /**
     * 성인 인증 로직 (만 19세 이상)
     */
    private boolean calculateIsAdult(String birthDate) {
        LocalDate today = LocalDate.now();
        LocalDate birth = LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        return Period.between(birth, today).getYears() >= 19;
    }
    
    private void validateCiDuplication(Long userId, String ci) {
        userVerificationRepository.findByCi(ci).ifPresent(existing -> {
            if (!existing.getUser().getUserId().equals(userId)) {
                throw UserException.duplicateVerification();
            }
        });
    }
}
```

## 🔄 계정 활성화 로직

```java
/**
 * 계정 활성화 조건 체크
 */
@Component
public class AccountActivationChecker {
    
    /**
     * 기본 활성화 조건 (이메일 + 필수약관)
     */
    public boolean canActivateBasic(Long userId) {
        UserVerification verification = getUserVerification(userId);
        boolean emailVerified = verification != null && verification.isEmailVerified();
        boolean requiredAgreements = hasAllRequiredAgreements(userId);
        
        return emailVerified && requiredAgreements;
    }
    
    /**
     * 완전 활성화 조건 (이메일 + 휴대폰 + 필수약관)
     */
    public boolean canActivateFull(Long userId) {
        UserVerification verification = getUserVerification(userId);
        boolean emailVerified = verification != null && verification.isEmailVerified();
        boolean phoneVerified = verification != null && verification.isPhoneVerified();
        boolean requiredAgreements = hasAllRequiredAgreements(userId);
        
        return emailVerified && phoneVerified && requiredAgreements;
    }
    
    /**
     * 활성화 단계별 진행률
     */
    public VerificationProgress getVerificationProgress(Long userId) {
        UserVerification verification = getUserVerification(userId);
        
        return VerificationProgress.builder()
            .emailVerified(verification != null && verification.isEmailVerified())
            .phoneVerified(verification != null && verification.isPhoneVerified())
            .agreementsCompleted(hasAllRequiredAgreements(userId))
            .adultVerified(verification != null && verification.isAdultVerified())
            .completionRate(calculateCompletionRate(userId))
            .build();
    }
}
```

## 📊 연동 참고사항

### step6-1d2_security_utilities.md 연동점
1. **JWT 토큰**: 인증 완료 후 토큰 생성/검증
2. **XSS 방지**: 사용자 입력 데이터 안전화
3. **보안 검증**: 휴대폰 번호, 실명 등 입력값 검증
4. **암호화**: CI/DI 정보 암호화 저장

### 주요 의존성
- **UserRepository**: 사용자 정보 조회/수정
- **UserVerificationRepository**: 인증 정보 관리
- **UserAgreementRepository**: 약관 동의 이력
- **AgreementContentRepository**: 활성 약관 조회
- **EmailService**: 이메일 인증 처리
- **AuthService**: 계정 활성화/비활성화

### 보안 고려사항
1. **CI/DI 암호화**: 개인식별정보 암호화 저장
2. **IP 추적**: 약관 동의 시 IP 주소 기록
3. **중복 방지**: CI 기반 중복 가입 차단
4. **데이터 검증**: 모든 입력값 유효성 검사

---
**연관 파일**: `step6-1d2_security_utilities.md`
**구현 우선순위**: HIGH (핵심 인증 시스템)
**예상 개발 기간**: 3-4일