# Step 7-1i: Custom Validators 구현체

> @UniqueEmail, @UniqueNickname 등 커스텀 검증 Validator 실제 구현  
> 생성일: 2025-08-25  
> 목표: Repository 연동 실시간 중복 검사 및 고성능 검증 시스템

---

## 🎯 구현 목표

### 1. 실시간 중복 검사 (Repository 연동)
### 2. 캐싱으로 성능 최적화
### 3. 한국 특화 검증 (휴대폰, 주민번호)
### 4. 보안 강화 패스워드 검증
### 5. 비동기 검증 지원

---

## 🔍 UniqueEmail Validator 구현

### UniqueEmailValidator.java
```java
package com.routepick.validation.validator;

import com.routepick.repository.user.UserRepository;
import com.routepick.validation.annotation.UniqueEmail;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 이메일 중복 검증 Validator
 * - 실시간 DB 조회
 * - Redis 캐싱으로 성능 최적화
 * - 소프트 삭제 계정 고려
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {
    
    private final UserRepository userRepository;
    private final EmailValidationCacheService cacheService;
    
    @Override
    public void initialize(UniqueEmail constraintAnnotation) {
        // 초기화 로직 (필요시)
    }
    
    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        // null이나 빈 값은 @NotBlank에서 처리
        if (!StringUtils.hasText(email)) {
            return true;
        }
        
        // 이메일 형식 재검증
        if (!isValidEmailFormat(email)) {
            addCustomMessage(context, "올바른 이메일 형식이 아닙니다.");
            return false;
        }
        
        try {
            // 캐시에서 먼저 확인
            Boolean cachedResult = cacheService.getCachedEmailValidation(email);
            if (cachedResult != null) {
                log.debug("Email validation cache hit: email={}, available={}", 
                         maskEmail(email), cachedResult);
                return cachedResult;
            }
            
            // DB에서 실시간 확인
            boolean isAvailable = checkEmailAvailability(email);
            
            // 결과 캐싱 (5분)
            cacheService.cacheEmailValidation(email, isAvailable);
            
            if (!isAvailable) {
                addCustomMessage(context, "이미 사용 중인 이메일입니다.");
                log.info("Email already exists: email={}", maskEmail(email));
            }
            
            return isAvailable;
            
        } catch (Exception e) {
            log.error("Email validation failed: email={}, error={}", 
                     maskEmail(email), e.getMessage(), e);
            
            // 오류 시 통과 (다른 검증에서 처리)
            return true;
        }
    }
    
    /**
     * 이메일 사용 가능 여부 확인
     * - 활성 계정과 소프트 삭제 계정 모두 고려
     */
    private boolean checkEmailAvailability(String email) {
        // 활성 계정 확인
        boolean activeExists = userRepository.existsByEmailAndDeletedAtIsNull(email.toLowerCase());
        
        if (activeExists) {
            return false;
        }
        
        // 소프트 삭제된 계정 확인 (30일 이내 재가입 불가)
        boolean recentlyDeleted = userRepository.existsByEmailAndDeletedAtAfter(
            email.toLowerCase(), 
            java.time.LocalDateTime.now().minusDays(30)
        );
        
        return !recentlyDeleted;
    }
    
    /**
     * 이메일 형식 검증
     */
    private boolean isValidEmailFormat(String email) {
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }
    
    /**
     * 커스텀 에러 메시지 추가
     */
    private void addCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
    
    /**
     * 이메일 마스킹 (로깅용)
     */
    private String maskEmail(String email) {
        if (!email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0];
        return local.substring(0, Math.min(2, local.length())) + "***@" + parts[1];
    }
}
```

---

## 🏷️ UniqueNickname Validator 구현

### UniqueNicknameValidator.java
```java
package com.routepick.validation.validator;

import com.routepick.repository.user.UserRepository;
import com.routepick.validation.annotation.UniqueNickname;
import com.routepick.service.profanity.ProfanityFilterService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 닉네임 중복 검증 Validator
 * - 실시간 DB 조회
 * - 금지어 필터링
 * - 한글/영문/숫자 검증
 * - 예약어 차단
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UniqueNicknameValidator implements ConstraintValidator<UniqueNickname, String> {
    
    private final UserRepository userRepository;
    private final ProfanityFilterService profanityFilterService;
    private final NicknameValidationCacheService cacheService;
    
    // 예약어 목록
    private static final Set<String> RESERVED_WORDS = Set.of(
        "admin", "administrator", "root", "system", "support", "help",
        "관리자", "운영자", "시스템", "고객센터", "문의", "공지",
        "routepick", "클라이머", "클라이밍", "암장"
    );
    
    // 한글/영문/숫자만 허용하는 패턴
    private static final Pattern VALID_NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9]{2,10}$");
    
    @Override
    public boolean isValid(String nickname, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(nickname)) {
            return true; // @NotBlank에서 처리
        }
        
        try {
            // 캐시 확인
            Boolean cachedResult = cacheService.getCachedNicknameValidation(nickname);
            if (cachedResult != null) {
                log.debug("Nickname validation cache hit: nickname={}, available={}", 
                         nickname, cachedResult);
                return cachedResult;
            }
            
            // 다단계 검증
            ValidationResult result = validateNickname(nickname);
            
            // 결과 캐싱
            cacheService.cacheNicknameValidation(nickname, result.isValid());
            
            if (!result.isValid()) {
                addCustomMessage(context, result.getMessage());
                log.info("Nickname validation failed: nickname={}, reason={}", 
                        nickname, result.getMessage());
            }
            
            return result.isValid();
            
        } catch (Exception e) {
            log.error("Nickname validation error: nickname={}, error={}", 
                     nickname, e.getMessage(), e);
            return true; // 오류 시 통과
        }
    }
    
    /**
     * 닉네임 다단계 검증
     */
    private ValidationResult validateNickname(String nickname) {
        // 1. 형식 검증
        if (!VALID_NICKNAME_PATTERN.matcher(nickname).matches()) {
            return ValidationResult.invalid("닉네임은 한글, 영문, 숫자만 사용 가능합니다 (2-10자).");
        }
        
        // 2. 예약어 검증
        if (isReservedWord(nickname)) {
            return ValidationResult.invalid("사용할 수 없는 닉네임입니다.");
        }
        
        // 3. 금지어 검증
        if (profanityFilterService.containsProfanity(nickname)) {
            return ValidationResult.invalid("부적절한 언어가 포함된 닉네임입니다.");
        }
        
        // 4. 중복 검증
        if (userRepository.existsByNicknameAndDeletedAtIsNull(nickname)) {
            return ValidationResult.invalid("이미 사용 중인 닉네임입니다.");
        }
        
        // 5. 유사 닉네임 검증 (선택적)
        if (hasSimilarNickname(nickname)) {
            return ValidationResult.invalid("유사한 닉네임이 이미 존재합니다.");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * 예약어 확인
     */
    private boolean isReservedWord(String nickname) {
        return RESERVED_WORDS.contains(nickname.toLowerCase());
    }
    
    /**
     * 유사 닉네임 확인
     * - 특수문자 제거 후 비교
     * - 숫자 변환 후 비교
     */
    private boolean hasSimilarNickname(String nickname) {
        String normalized = normalizeNickname(nickname);
        
        // 정규화된 닉네임으로 유사성 검사
        return userRepository.existsByNormalizedNickname(normalized);
    }
    
    /**
     * 닉네임 정규화
     */
    private String normalizeNickname(String nickname) {
        return nickname.toLowerCase()
                      .replaceAll("[0-9]", "") // 숫자 제거
                      .replaceAll("[^가-힣a-z]", ""); // 한글, 영문만 남기기
    }
    
    /**
     * 커스텀 메시지 추가
     */
    private void addCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
    
    /**
     * 검증 결과 클래스
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
        
        boolean isValid() { return valid; }
        String getMessage() { return message; }
    }
}
```

---

## 📱 KoreanPhone Validator 구현

### KoreanPhoneValidator.java
```java
package com.routepick.validation.validator;

import com.routepick.validation.annotation.KoreanPhone;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 한국 휴대폰 번호 검증 Validator
 * - 한국 통신사별 번호 체계 검증
 * - 가상번호 및 특수번호 차단
 * - 국제번호 형식 지원
 */
@Slf4j
@Component
public class KoreanPhoneValidator implements ConstraintValidator<KoreanPhone, String> {
    
    // 한국 휴대폰 번호 패턴 (하이픈 포함)
    private static final Pattern PHONE_WITH_HYPHEN = Pattern.compile("^01[016789]-\\d{3,4}-\\d{4}$");
    
    // 한국 휴대폰 번호 패턴 (하이픈 없음)
    private static final Pattern PHONE_WITHOUT_HYPHEN = Pattern.compile("^01[016789]\\d{7,8}$");
    
    // 국제번호 형식 (+82)
    private static final Pattern INTERNATIONAL_FORMAT = Pattern.compile("^\\+82-?1[016789]-?\\d{3,4}-?\\d{4}$");
    
    // 유효한 통신사 접두번호
    private static final Set<String> VALID_CARRIERS = Set.of(
        "010", // SKT, KT, LG U+
        "011", // SKT (구)
        "016", // KT (구)  
        "017", // SKT (구)
        "018", // KT (구)
        "019"  // LG U+ (구)
    );
    
    // 차단할 특수번호
    private static final Set<String> BLOCKED_NUMBERS = Set.of(
        "01000000000",
        "01011111111", 
        "01012345678",
        "01087654321"
    );
    
    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(phone)) {
            return true; // @NotBlank에서 처리
        }
        
        try {
            // 공백 제거
            phone = phone.replaceAll("\\s", "");
            
            // 다양한 형식 검증
            ValidationResult result = validatePhoneNumber(phone);
            
            if (!result.isValid()) {
                addCustomMessage(context, result.getMessage());
                log.debug("Phone validation failed: phone={}, reason={}", 
                         maskPhone(phone), result.getMessage());
            }
            
            return result.isValid();
            
        } catch (Exception e) {
            log.error("Phone validation error: phone={}, error={}", 
                     maskPhone(phone), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 휴대폰 번호 다단계 검증
     */
    private ValidationResult validatePhoneNumber(String phone) {
        // 1. 기본 형식 검증
        if (!isValidFormat(phone)) {
            return ValidationResult.invalid("올바른 휴대폰 번호 형식이 아닙니다 (010-0000-0000).");
        }
        
        // 2. 통신사 검증
        if (!isValidCarrier(phone)) {
            return ValidationResult.invalid("지원하지 않는 통신사 번호입니다.");
        }
        
        // 3. 특수번호 차단
        if (isBlockedNumber(phone)) {
            return ValidationResult.invalid("사용할 수 없는 번호입니다.");
        }
        
        // 4. 연속번호 체크
        if (hasConsecutiveDigits(phone)) {
            return ValidationResult.invalid("연속된 숫자로만 구성된 번호는 사용할 수 없습니다.");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * 기본 형식 검증
     */
    private boolean isValidFormat(String phone) {
        return PHONE_WITH_HYPHEN.matcher(phone).matches() ||
               PHONE_WITHOUT_HYPHEN.matcher(phone).matches() ||
               INTERNATIONAL_FORMAT.matcher(phone).matches();
    }
    
    /**
     * 통신사 검증
     */
    private boolean isValidCarrier(String phone) {
        // 하이픈 제거하고 숫자만 추출
        String numbers = phone.replaceAll("[^0-9]", "");
        
        // 국제번호인 경우 +82 제거
        if (phone.startsWith("+82")) {
            numbers = "0" + numbers.substring(2);
        }
        
        if (numbers.length() < 3) {
            return false;
        }
        
        String carrier = numbers.substring(0, 3);
        return VALID_CARRIERS.contains(carrier);
    }
    
    /**
     * 특수번호 차단
     */
    private boolean isBlockedNumber(String phone) {
        String numbers = phone.replaceAll("[^0-9]", "");
        
        // 국제번호 정규화
        if (phone.startsWith("+82")) {
            numbers = "0" + numbers.substring(2);
        }
        
        return BLOCKED_NUMBERS.contains(numbers);
    }
    
    /**
     * 연속번호 체크
     */
    private boolean hasConsecutiveDigits(String phone) {
        String numbers = phone.replaceAll("[^0-9]", "");
        
        // 연속된 같은 숫자 4개 이상
        for (int i = 0; i <= numbers.length() - 4; i++) {
            char digit = numbers.charAt(i);
            boolean consecutive = true;
            for (int j = 1; j < 4; j++) {
                if (i + j >= numbers.length() || numbers.charAt(i + j) != digit) {
                    consecutive = false;
                    break;
                }
            }
            if (consecutive) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 전화번호 마스킹
     */
    private String maskPhone(String phone) {
        if (phone.length() < 8) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
    
    /**
     * 커스텀 메시지 추가
     */
    private void addCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
    
    /**
     * 검증 결과 클래스
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
        
        boolean isValid() { return valid; }
        String getMessage() { return message; }
    }
}
```

---

## 🔐 SecurePassword Validator 구현

### SecurePasswordValidator.java  
```java
package com.routepick.validation.validator;

import com.routepick.validation.annotation.SecurePassword;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 보안 패스워드 검증 Validator
 * - 일반적인 패스워드 차단
 * - 개인정보 포함 검증
 * - 패스워드 강도 분석
 * - 키보드 패턴 차단
 */
@Slf4j
@Component
public class SecurePasswordValidator implements ConstraintValidator<SecurePassword, String> {
    
    // 일반적인 약한 패스워드 목록
    private static final Set<String> WEAK_PASSWORDS = loadWeakPasswords();
    
    // 키보드 패턴
    private static final Set<String> KEYBOARD_PATTERNS = Set.of(
        "qwerty", "asdf", "zxcv", "1234", "abcd",
        "qwertyui", "asdfgh", "zxcvbn", "12345678"
    );
    
    // 반복 패턴 (aaa, 111 등)
    private static final Pattern REPEATED_CHARS = Pattern.compile("(.)\\1{2,}");
    
    // 순차 패턴 (abc, 123 등)  
    private static final Pattern SEQUENTIAL_PATTERN = Pattern.compile("(abc|bcd|cde|def|efg|fgh|ghi|hij|ijk|jkl|klm|lmn|mno|nop|opq|pqr|qrs|rst|stu|tuv|uvw|vwx|wxy|xyz|012|123|234|345|456|567|678|789)");
    
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(password)) {
            return true; // @NotBlank에서 처리
        }
        
        try {
            ValidationResult result = validatePassword(password);
            
            if (!result.isValid()) {
                addCustomMessage(context, result.getMessage());
                log.info("Password validation failed: reason={}", result.getMessage());
            }
            
            return result.isValid();
            
        } catch (Exception e) {
            log.error("Password validation error: error={}", e.getMessage(), e);
            return true; // 오류 시 통과
        }
    }
    
    /**
     * 패스워드 보안 검증
     */
    private ValidationResult validatePassword(String password) {
        String lowerPassword = password.toLowerCase();
        
        // 1. 일반적인 약한 패스워드 검증
        if (WEAK_PASSWORDS.contains(lowerPassword)) {
            return ValidationResult.invalid("너무 일반적인 비밀번호입니다.");
        }
        
        // 2. 키보드 패턴 검증
        for (String pattern : KEYBOARD_PATTERNS) {
            if (lowerPassword.contains(pattern)) {
                return ValidationResult.invalid("키보드 패턴이 포함된 비밀번호는 사용할 수 없습니다.");
            }
        }
        
        // 3. 반복 문자 검증
        if (REPEATED_CHARS.matcher(password).find()) {
            return ValidationResult.invalid("동일한 문자가 연속으로 3개 이상 포함될 수 없습니다.");
        }
        
        // 4. 순차 패턴 검증
        if (SEQUENTIAL_PATTERN.matcher(lowerPassword).find()) {
            return ValidationResult.invalid("연속된 문자나 숫자 패턴은 사용할 수 없습니다.");
        }
        
        // 5. 패스워드 강도 검증
        int strength = calculatePasswordStrength(password);
        if (strength < 60) { // 60점 이하는 약한 패스워드
            return ValidationResult.invalid("패스워드가 너무 약합니다. 더 복잡한 패스워드를 사용해주세요.");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * 패스워드 강도 계산 (0-100점)
     */
    private int calculatePasswordStrength(String password) {
        int score = 0;
        
        // 길이 점수 (최대 25점)
        if (password.length() >= 8) score += 10;
        if (password.length() >= 12) score += 10;
        if (password.length() >= 16) score += 5;
        
        // 문자 종류 점수 (최대 40점)
        if (password.matches(".*[a-z].*")) score += 10; // 소문자
        if (password.matches(".*[A-Z].*")) score += 10; // 대문자
        if (password.matches(".*[0-9].*")) score += 10; // 숫자
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score += 10; // 특수문자
        
        // 복잡성 점수 (최대 35점)
        Set<Character> uniqueChars = new HashSet<>();
        for (char c : password.toCharArray()) {
            uniqueChars.add(c);
        }
        
        // 고유 문자 비율
        double uniqueRatio = (double) uniqueChars.size() / password.length();
        score += (int) (uniqueRatio * 20);
        
        // 문자/숫자 조합
        if (password.matches(".*[a-zA-Z].*") && password.matches(".*[0-9].*")) {
            score += 15;
        }
        
        return Math.min(100, score);
    }
    
    /**
     * 약한 패스워드 목록 로드
     */
    private static Set<String> loadWeakPasswords() {
        Set<String> weakPasswords = new HashSet<>();
        
        try {
            InputStream is = SecurePasswordValidator.class
                .getResourceAsStream("/security/weak-passwords.txt");
            
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim().toLowerCase();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            weakPasswords.add(line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load weak passwords list", e);
        }
        
        // 기본 약한 패스워드 추가
        weakPasswords.addAll(Set.of(
            "password", "123456", "password123", "admin", "root",
            "qwerty", "abc123", "passw0rd", "welcome", "login",
            "비밀번호", "패스워드", "암호"
        ));
        
        return weakPasswords;
    }
    
    /**
     * 커스텀 메시지 추가
     */
    private void addCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
    
    /**
     * 검증 결과 클래스
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
        
        boolean isValid() { return valid; }
        String getMessage() { return message; }
    }
}
```

---

## 📦 캐싱 서비스

### EmailValidationCacheService.java
```java
package com.routepick.service.validation.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 이메일 검증 캐시 서비스
 */
@Service
@RequiredArgsConstructor
public class EmailValidationCacheService {
    
    private static final String EMAIL_VALIDATION_PREFIX = "validation:email:";
    private static final long CACHE_TTL_MINUTES = 5;
    
    private final RedisTemplate<String, Boolean> redisTemplate;
    
    public Boolean getCachedEmailValidation(String email) {
        String key = EMAIL_VALIDATION_PREFIX + email.toLowerCase();
        return redisTemplate.opsForValue().get(key);
    }
    
    public void cacheEmailValidation(String email, boolean isAvailable) {
        String key = EMAIL_VALIDATION_PREFIX + email.toLowerCase();
        redisTemplate.opsForValue().set(key, isAvailable, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }
    
    public void invalidateEmailValidation(String email) {
        String key = EMAIL_VALIDATION_PREFIX + email.toLowerCase();
        redisTemplate.delete(key);
    }
}
```

### NicknameValidationCacheService.java
```java
package com.routepick.service.validation.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 닉네임 검증 캐시 서비스
 */
@Service
@RequiredArgsConstructor
public class NicknameValidationCacheService {
    
    private static final String NICKNAME_VALIDATION_PREFIX = "validation:nickname:";
    private static final long CACHE_TTL_MINUTES = 3;
    
    private final RedisTemplate<String, Boolean> redisTemplate;
    
    public Boolean getCachedNicknameValidation(String nickname) {
        String key = NICKNAME_VALIDATION_PREFIX + nickname.toLowerCase();
        return redisTemplate.opsForValue().get(key);
    }
    
    public void cacheNicknameValidation(String nickname, boolean isAvailable) {
        String key = NICKNAME_VALIDATION_PREFIX + nickname.toLowerCase();
        redisTemplate.opsForValue().set(key, isAvailable, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }
    
    public void invalidateNicknameValidation(String nickname) {
        String key = NICKNAME_VALIDATION_PREFIX + nickname.toLowerCase();
        redisTemplate.delete(key);
    }
}
```

---

## 🚫 욕설 필터 서비스

### ProfanityFilterService.java
```java
package com.routepick.service.profanity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 욕설/금지어 필터 서비스
 */
@Slf4j
@Service
public class ProfanityFilterService {
    
    private static final Set<String> PROFANITY_WORDS = loadProfanityWords();
    
    /**
     * 금지어 포함 여부 확인
     */
    public boolean containsProfanity(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        String lowerText = text.toLowerCase();
        
        return PROFANITY_WORDS.stream()
                              .anyMatch(lowerText::contains);
    }
    
    /**
     * 금지어 필터링 (대체)
     */
    public String filterProfanity(String text) {
        if (text == null) {
            return null;
        }
        
        String filtered = text;
        for (String profanity : PROFANITY_WORDS) {
            filtered = filtered.replaceAll("(?i)" + profanity, "***");
        }
        
        return filtered;
    }
    
    /**
     * 금지어 목록 로드
     */
    private static Set<String> loadProfanityWords() {
        Set<String> words = new HashSet<>();
        
        try {
            InputStream is = ProfanityFilterService.class
                .getResourceAsStream("/security/profanity-words.txt");
            
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim().toLowerCase();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            words.add(line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load profanity words list", e);
        }
        
        return words;
    }
}
```

---

## 📁 리소스 파일

### weak-passwords.txt
```
# 약한 패스워드 목록
password
123456
password123
admin
root
qwerty
abc123
passw0rd
welcome
login
guest
user
test
demo
비밀번호
패스워드
암호
1q2w3e4r
qwer1234
asdf1234
```

### profanity-words.txt
```
# 금지어 목록 (실제 환경에서는 더 포괄적으로 관리)
# 욕설, 비속어, 차별적 언어 등
admin
administrator
관리자
운영자
시스템
```

---

*Step 7-1i 완료: Custom Validators 구현체 (실시간 중복 검사 + 캐싱 + 보안)*