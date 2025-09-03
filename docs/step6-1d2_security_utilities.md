# Step 6-1d2: Security Utilities & JWT System

**파일들**: JWT 토큰 제공자, XSS 방지, 보안 검증 유틸리티 구현

이 파일은 `step6-1d1_verification_core.md`와 연계된 보안 유틸리티 시스템입니다.

## 🔒 JWT 토큰 제공자 구현

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
 * 
 * 주요 기능:
 * 1. Access Token 생성 (30분)
 * 2. Refresh Token 생성 (7일)
 * 3. 토큰 유효성 검증
 * 4. 토큰 정보 추출
 * 5. 토큰 타입 구분
 * 
 * @author RoutePickr Team
 * @since 2025.08
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
    
    /**
     * 토큰 만료까지 남은 시간 (초)
     */
    public long getTimeUntilExpiration(String token) {
        Date expiration = getExpirationFromToken(token);
        long now = System.currentTimeMillis();
        
        return Math.max(0, (expiration.getTime() - now) / 1000);
    }
    
    /**
     * 토큰 발급 시간 조회
     */
    public Date getIssuedAtFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
        
        return claims.getIssuedAt();
    }
    
    /**
     * 토큰이 곧 만료되는지 확인 (10분 이내)
     */
    public boolean isTokenExpiringSoon(String token) {
        long timeUntilExpiration = getTimeUntilExpiration(token);
        return timeUntilExpiration <= 600; // 10분 = 600초
    }
    
    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }
    
    public long getRefreshTokenValiditySeconds() {
        return refreshTokenValiditySeconds;
    }
}
```

## 🛡️ XSS 방지 유틸리티

```java
package com.routepick.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import java.util.regex.Pattern;

/**
 * XSS 방지 유틸리티
 * 
 * 주요 기능:
 * 1. HTML 태그 제거 및 안전한 텍스트 반환
 * 2. 스크립트 태그 제거
 * 3. 사용자 입력 검증 (닉네임, 이름 등)
 * 4. URL 검증 및 안전한 URL 반환
 * 5. 한국어 특화 입력 검증
 * 
 * @author RoutePickr Team
 * @since 2025.08
 */
public class XssProtectionUtil {
    
    private static final Safelist RICH_TEXT_SAFELIST = Safelist.relaxed()
        .addTags("h1", "h2", "h3", "h4", "h5", "h6")
        .addAttributes("a", "href", "target")
        .addProtocols("a", "href", "http", "https");
    
    private static final Safelist BASIC_SAFELIST = Safelist.basic();
    private static final Safelist NO_HTML_SAFELIST = Safelist.none();
    
    // 한국어 + 영문 + 숫자 패턴
    private static final Pattern KOREAN_NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9\\s]{1,20}$");
    private static final Pattern KOREAN_NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_\\-]{2,15}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[0-9]-\\d{4}-\\d{4}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?.*$");
    
    /**
     * HTML 태그 제거 및 안전한 텍스트 반환 (리치 텍스트용)
     */
    public static String sanitizeRichText(String input) {
        if (input == null) {
            return null;
        }
        
        return Jsoup.clean(input, RICH_TEXT_SAFELIST);
    }
    
    /**
     * 기본 HTML 태그만 허용
     */
    public static String sanitizeBasicHtml(String input) {
        if (input == null) {
            return null;
        }
        
        return Jsoup.clean(input, BASIC_SAFELIST);
    }
    
    /**
     * 모든 HTML 태그 제거
     */
    public static String stripHtml(String input) {
        if (input == null) {
            return null;
        }
        
        return Jsoup.clean(input, NO_HTML_SAFELIST);
    }
    
    /**
     * 스크립트 태그만 제거
     */
    public static String removeScripts(String input) {
        if (input == null) {
            return null;
        }
        
        return Jsoup.clean(input, BASIC_SAFELIST);
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
        
        // 특수문자 제거 (한글, 영문, 숫자, 일부 특수문자만 허용)
        return cleaned.replaceAll("[^가-힣a-zA-Z0-9\\s_\\-]", "");
    }
    
    /**
     * 한국어 이름 검증 (실명)
     */
    public static String sanitizeKoreanName(String input) {
        if (input == null) {
            return null;
        }
        
        String cleaned = stripHtml(input).trim();
        
        if (!KOREAN_NAME_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("유효하지 않은 이름 형식입니다.");
        }
        
        return cleaned;
    }
    
    /**
     * 닉네임 검증
     */
    public static String sanitizeNickname(String input) {
        if (input == null) {
            return null;
        }
        
        String cleaned = stripHtml(input).trim();
        
        if (!KOREAN_NICKNAME_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("닉네임은 2-15자의 한글, 영문, 숫자, _, -만 사용 가능합니다.");
        }
        
        return cleaned;
    }
    
    /**
     * 이메일 검증
     */
    public static String sanitizeEmail(String input) {
        if (input == null) {
            return null;
        }
        
        String cleaned = stripHtml(input).trim().toLowerCase();
        
        if (!EMAIL_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("유효하지 않은 이메일 형식입니다.");
        }
        
        return cleaned;
    }
    
    /**
     * 휴대폰 번호 검증
     */
    public static String sanitizePhoneNumber(String input) {
        if (input == null) {
            return null;
        }
        
        String cleaned = stripHtml(input).trim().replaceAll("[^0-9-]", "");
        
        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("유효하지 않은 휴대폰 번호 형식입니다. (예: 010-1234-5678)");
        }
        
        return cleaned;
    }
    
    /**
     * URL 검증 및 안전한 URL 반환
     */
    public static String sanitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        
        String cleaned = stripHtml(url).trim();
        
        // 기본적인 URL 패턴 검증
        if (!URL_PATTERN.matcher(cleaned).matches()) {
            return null;
        }
        
        return cleaned;
    }
    
    /**
     * 게시글 내용 검증 (리치 텍스트)
     */
    public static String sanitizePostContent(String content) {
        if (content == null) {
            return null;
        }
        
        // 리치 텍스트 허용하되 위험한 태그 제거
        String cleaned = sanitizeRichText(content);
        
        // 길이 제한 (예: 10,000자)
        if (cleaned.length() > 10000) {
            throw new IllegalArgumentException("게시글 내용이 너무 깁니다. (최대 10,000자)");
        }
        
        return cleaned;
    }
    
    /**
     * 댓글 내용 검증
     */
    public static String sanitizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        
        // 댓글은 기본 HTML만 허용
        String cleaned = sanitizeBasicHtml(comment);
        
        // 길이 제한 (예: 500자)
        if (cleaned.length() > 500) {
            throw new IllegalArgumentException("댓글이 너무 깁니다. (최대 500자)");
        }
        
        return cleaned;
    }
}
```

## 🔐 암호화 유틸리티

```java
package com.routepick.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 암호화 유틸리티
 * 
 * 주요 기능:
 * 1. CI/DI 암호화 (AES-256)
 * 2. 민감정보 해시화 (SHA-256)
 * 3. 솔트 생성
 * 4. 안전한 랜덤 문자열 생성
 * 
 * @author RoutePickr Team
 * @since 2025.08
 */
@Component
public class EncryptionUtil {
    
    private final SecretKey aesKey;
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    public EncryptionUtil(@Value("${app.encryption.secret}") String secret) {
        this.aesKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), AES_ALGORITHM);
    }
    
    /**
     * CI (개인식별정보) 암호화
     */
    public String encryptCi(String ci) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] encrypted = cipher.doFinal(ci.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("CI 암호화 실패", e);
        }
    }
    
    /**
     * CI (개인식별정보) 복호화
     */
    public String decryptCi(String encryptedCi) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            byte[] decoded = Base64.getDecoder().decode(encryptedCi);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("CI 복호화 실패", e);
        }
    }
    
    /**
     * DI (중복가입확인정보) 해시화 (단방향)
     */
    public String hashDi(String di) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(di.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("DI 해시화 실패", e);
        }
    }
    
    /**
     * 솔트 생성
     */
    public String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * 솔트와 함께 해시화
     */
    public String hashWithSalt(String data, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("솔트 해시화 실패", e);
        }
    }
    
    /**
     * 안전한 랜덤 문자열 생성
     */
    public String generateSecureRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    /**
     * 숫자만 포함하는 랜덤 문자열 생성 (인증 코드용)
     */
    public String generateNumericCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        
        return sb.toString();
    }
}
```

## 🛡️ 입력 검증 유틸리티

```java
package com.routepick.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 입력 검증 유틸리티
 * 
 * 주요 기능:
 * 1. 한국 특화 검증 (휴대폰, 주민번호 등)
 * 2. 이메일 검증
 * 3. 비밀번호 강도 검증
 * 4. 날짜 형식 검증
 * 
 * @author RoutePickr Team
 * @since 2025.08
 */
public class ValidationUtil {
    
    // 한국 휴대폰 번호 패턴
    private static final Pattern KOREAN_MOBILE_PATTERN = 
        Pattern.compile("^01[0-9]-\\d{4}-\\d{4}$");
    
    // 이메일 패턴
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    
    // 비밀번호 패턴 (8-20자, 영문+숫자+특수문자 조합)
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,20}$");
    
    // 한국어 이름 패턴
    private static final Pattern KOREAN_NAME_PATTERN = 
        Pattern.compile("^[가-힣]{2,5}$");
    
    // 닉네임 패턴
    private static final Pattern NICKNAME_PATTERN = 
        Pattern.compile("^[가-힣a-zA-Z0-9_\\-]{2,15}$");
    
    /**
     * 휴대폰 번호 검증
     */
    public static boolean isValidKoreanMobile(String mobile) {
        return mobile != null && KOREAN_MOBILE_PATTERN.matcher(mobile).matches();
    }
    
    /**
     * 이메일 검증
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * 비밀번호 강도 검증
     */
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }
    
    /**
     * 한국어 이름 검증
     */
    public static boolean isValidKoreanName(String name) {
        return name != null && KOREAN_NAME_PATTERN.matcher(name).matches();
    }
    
    /**
     * 닉네임 검증
     */
    public static boolean isValidNickname(String nickname) {
        return nickname != null && NICKNAME_PATTERN.matcher(nickname).matches();
    }
    
    /**
     * 생년월일 검증 (YYYYMMDD)
     */
    public static boolean isValidBirthDate(String birthDate) {
        if (birthDate == null || birthDate.length() != 8) {
            return false;
        }
        
        try {
            LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
    
    /**
     * 성별 검증 (M/F)
     */
    public static boolean isValidGender(String gender) {
        return "M".equals(gender) || "F".equals(gender);
    }
    
    /**
     * CI 형식 검증 (88자리 문자열)
     */
    public static boolean isValidCi(String ci) {
        return ci != null && ci.length() == 88 && ci.matches("^[A-Za-z0-9+/=]+$");
    }
    
    /**
     * DI 형식 검증 (64자리 문자열)
     */
    public static boolean isValidDi(String di) {
        return di != null && di.length() == 64 && di.matches("^[A-Za-z0-9+/=]+$");
    }
    
    /**
     * URL 검증
     */
    public static boolean isValidUrl(String url) {
        if (url == null) {
            return false;
        }
        
        return url.matches("^https?://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?.*$");
    }
    
    /**
     * 한국 좌표 범위 검증
     */
    public static boolean isValidKoreanCoordinate(double latitude, double longitude) {
        // 한반도 대략적 좌표 범위
        boolean validLatitude = latitude >= 33.0 && latitude <= 38.6;
        boolean validLongitude = longitude >= 124.6 && longitude <= 131.9;
        
        return validLatitude && validLongitude;
    }
    
    /**
     * 문자열 길이 검증
     */
    public static boolean isValidLength(String str, int minLength, int maxLength) {
        if (str == null) {
            return false;
        }
        
        int length = str.length();
        return length >= minLength && length <= maxLength;
    }
    
    /**
     * 숫자 범위 검증
     */
    public static boolean isValidRange(int value, int min, int max) {
        return value >= min && value <= max;
    }
}
```

## 🔄 보안 설정 Configuration

```java
package com.routepick.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 보안 설정
 */
@Configuration
public class SecurityUtilConfig {
    
    /**
     * 비밀번호 암호화
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // strength 12
    }
}
```

## 📊 연동 참고사항

### step6-1d1_verification_core.md 연동점
1. **JWT 토큰**: 이메일/휴대폰 인증 완료 후 토큰 발급
2. **XSS 방지**: 약관 동의 시 사용자 입력 검증
3. **암호화**: CI/DI 정보 암호화 저장
4. **입력 검증**: 휴대폰 번호, 실명 등 유효성 검사

### 보안 강화 포인트
1. **JWT 보안**: 토큰 탈취 방지, 만료 시간 관리
2. **XSS 방지**: 모든 사용자 입력 검증 및 필터링
3. **암호화**: 민감정보 AES-256 암호화
4. **검증**: 한국 특화 입력값 검증 로직

### 성능 최적화
1. **토큰 캐싱**: Redis를 통한 토큰 상태 관리
2. **검증 캐싱**: 자주 사용되는 검증 결과 캐싱
3. **비동기 처리**: 암호화/복호화 작업 비동기 처리

---
**연관 파일**: `step6-1d1_verification_core.md`
**구현 우선순위**: HIGH (보안 핵심 기능)
**예상 개발 기간**: 2-3일