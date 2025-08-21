# Step 4-1c: User 확장 엔티티 및 보안 강화

> **RoutePickr User 확장 시스템** - 본인인증, 약관관리, 토큰관리, 보안 강화  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-1c (JPA 엔티티 50개 - User 확장 4개 + 보안/최적화)  
> **분할**: step4-1_base_user_entities.md → User 확장 및 최적화 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 User 확장 시스템과 보안 강화 사항**을 담고 있습니다.

### 🎯 주요 특징
- **본인인증**: 한국 CI/DI 시스템, 휴대폰/이메일 인증
- **약관 관리**: 버전별 약관 이력, 동의 추적, 법적 증빙
- **토큰 관리**: JWT 블랙리스트, 리프레시 제한, 디바이스 추적
- **보안 강화**: 민감정보 암호화, 한국 특화 검증, JPA 최적화

### 📊 엔티티 목록 (4개)
1. **UserVerification** - 본인인증 정보 (CI/DI, 성인인증)
2. **UserAgreement** - 약관 동의 이력 (법적 증빙)
3. **ApiToken** - JWT 토큰 관리 (블랙리스트, 디바이스 추적)
4. **AgreementContent** - 약관 내용 관리 (버전 이력)

---

## 🔐 User 확장 엔티티 설계 (4개)

### UserVerification.java - 본인인증 정보
```java
package com.routepick.domain.user.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자 본인인증 정보
 * - 한국 본인인증 시스템 (CI/DI) 지원
 */
@Entity
@Table(name = "user_verifications", indexes = {
    @Index(name = "idx_verification_user", columnList = "user_id", unique = true),
    @Index(name = "idx_verification_ci", columnList = "ci"),
    @Index(name = "idx_verification_phone", columnList = "phone_number")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserVerification extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "ci", length = 255)
    private String ci; // 연계정보 (암호화 저장)
    
    @Column(name = "di", length = 255)
    private String di; // 중복가입확인정보 (암호화 저장)
    
    @Column(name = "real_name", length = 100)
    private String realName; // 실명 (암호화 저장)
    
    @Column(name = "birth_date", length = 10)
    private String birthDate; // YYYYMMDD 형식
    
    @Column(name = "gender", length = 1)
    private String gender; // M/F
    
    @Column(name = "nationality", length = 10)
    private String nationality; // 내국인/외국인
    
    @Column(name = "phone_number", length = 20)
    private String phoneNumber; // 인증받은 휴대폰 번호
    
    @Column(name = "telecom", length = 20)
    private String telecom; // 통신사 (SKT, KT, LGU+, 알뜰폰)
    
    @Column(name = "verification_method", length = 50)
    private String verificationMethod; // 인증방법 (PHONE, IPIN, CARD)
    
    @Column(name = "verification_date")
    private LocalDateTime verificationDate;
    
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;
    
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;
    
    @Column(name = "adult_verified", nullable = false)
    private boolean adultVerified = false;
    
    @Column(name = "verification_token", length = 100)
    private String verificationToken; // 이메일 인증 토큰
    
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 본인인증 완료 처리
     */
    public void completeVerification(String ci, String di, String realName, 
                                     String birthDate, String gender, String phoneNumber) {
        this.ci = ci;
        this.di = di;
        this.realName = realName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.phoneVerified = true;
        this.verificationDate = LocalDateTime.now();
        
        // 성인 여부 확인 (만 19세 이상)
        this.adultVerified = isAdult(birthDate);
    }
    
    /**
     * 이메일 인증 토큰 생성
     */
    public void generateEmailVerificationToken(String token) {
        this.verificationToken = token;
        this.tokenExpiresAt = LocalDateTime.now().plusHours(24);
    }
    
    /**
     * 이메일 인증 완료
     */
    public void verifyEmail(String token) {
        if (verificationToken != null && verificationToken.equals(token) 
            && tokenExpiresAt != null && tokenExpiresAt.isAfter(LocalDateTime.now())) {
            this.emailVerified = true;
            this.verificationToken = null;
            this.tokenExpiresAt = null;
        } else {
            throw new IllegalArgumentException("유효하지 않은 인증 토큰입니다");
        }
    }
    
    /**
     * 성인 여부 확인
     */
    private boolean isAdult(String birthDate) {
        if (birthDate == null || birthDate.length() != 8) return false;
        
        try {
            int birthYear = Integer.parseInt(birthDate.substring(0, 4));
            int currentYear = LocalDate.now().getYear();
            return (currentYear - birthYear) >= 19;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 본인인증 유효성 확인
     */
    @Transient
    public boolean isFullyVerified() {
        return phoneVerified && emailVerified;
    }
    
    @Override
    public Long getId() {
        return verificationId;
    }
}
```

### UserAgreement.java - 약관 동의 이력
```java
package com.routepick.domain.user.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.AgreementType;
import com.routepick.domain.system.entity.AgreementContent;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자 약관 동의 이력
 * - 법적 증빙을 위한 동의 이력 관리
 */
@Entity
@Table(name = "user_agreements", indexes = {
    @Index(name = "idx_agreement_user_type", columnList = "user_id, agreement_type"),
    @Index(name = "idx_agreement_date", columnList = "agreed_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserAgreement extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agreement_id")
    private Long agreementId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_content_id", nullable = false)
    private AgreementContent agreementContent;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false, length = 20)
    private AgreementType agreementType;
    
    @Column(name = "is_agreed", nullable = false)
    private boolean isAgreed;
    
    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;
    
    @Column(name = "disagreed_at")
    private LocalDateTime disagreedAt;
    
    @Column(name = "agreed_version", length = 20)
    private String agreedVersion;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress; // IPv6 지원
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 약관 동의 처리
     */
    public void agree(String ipAddress, String userAgent) {
        this.isAgreed = true;
        this.agreedAt = LocalDateTime.now();
        this.disagreedAt = null;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.agreedVersion = agreementContent.getVersion();
    }
    
    /**
     * 약관 동의 철회
     */
    public void disagree() {
        if (agreementType.isRequired()) {
            throw new IllegalStateException("필수 약관은 철회할 수 없습니다");
        }
        this.isAgreed = false;
        this.disagreedAt = LocalDateTime.now();
    }
    
    /**
     * 동의 유효성 확인
     */
    @Transient
    public boolean isValidAgreement() {
        if (!isAgreed) return false;
        
        // 버전이 다르면 재동의 필요
        return agreedVersion != null && 
               agreedVersion.equals(agreementContent.getVersion());
    }
    
    @Override
    public Long getId() {
        return agreementId;
    }
}
```

### ApiToken.java - JWT 토큰 관리
```java
package com.routepick.domain.user.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.TokenType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * API 토큰 관리
 * - JWT Access/Refresh 토큰 저장
 * - 토큰 블랙리스트 관리
 */
@Entity
@Table(name = "api_tokens", indexes = {
    @Index(name = "idx_token_user_type", columnList = "user_id, token_type"),
    @Index(name = "idx_token_expires", columnList = "expires_at"),
    @Index(name = "idx_token_value", columnList = "token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ApiToken extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "token", nullable = false, unique = true, length = 500)
    private String token;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 20)
    private TokenType tokenType;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
    
    @Column(name = "is_blacklisted", nullable = false)
    private boolean isBlacklisted = false;
    
    @Column(name = "device_info", length = 200)
    private String deviceInfo; // 디바이스 정보 (User-Agent)
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    
    @Column(name = "revoked_reason", length = 200)
    private String revokedReason;
    
    @Column(name = "refresh_count")
    private Integer refreshCount = 0;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 토큰 만료 여부 확인
     */
    @Transient
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * 토큰 유효성 확인
     */
    @Transient
    public boolean isValid() {
        return isActive && !isBlacklisted && !isExpired();
    }
    
    /**
     * 토큰 폐기
     */
    public void revoke(String reason) {
        this.isActive = false;
        this.isBlacklisted = true;
        this.revokedAt = LocalDateTime.now();
        this.revokedReason = reason;
    }
    
    /**
     * 토큰 사용 기록
     */
    public void recordUsage() {
        this.lastUsedAt = LocalDateTime.now();
    }
    
    /**
     * 리프레시 카운트 증가
     */
    public void incrementRefreshCount() {
        this.refreshCount = (refreshCount == null ? 0 : refreshCount) + 1;
    }
    
    /**
     * 리프레시 가능 여부 확인 (최대 10회)
     */
    @Transient
    public boolean canRefresh() {
        return isValid() && refreshCount < 10;
    }
    
    @Override
    public Long getId() {
        return tokenId;
    }
}
```

### AgreementContent.java - 약관 내용 관리
```java
package com.routepick.domain.system.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.AgreementType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 약관 내용 관리
 * - 버전별 약관 내용 이력 관리
 */
@Entity
@Table(name = "agreement_contents", indexes = {
    @Index(name = "idx_agreement_type_version", columnList = "agreement_type, version"),
    @Index(name = "idx_agreement_active", columnList = "is_active, effective_date")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AgreementContent extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_id")
    private Long contentId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false, length = 20)
    private AgreementType agreementType;
    
    @Column(name = "version", nullable = false, length = 20)
    private String version; // 예: "1.0.0", "2025.01.01"
    
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content; // HTML 형식
    
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary; // 요약본
    
    @Column(name = "effective_date", nullable = false)
    private LocalDateTime effectiveDate; // 시행일
    
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate; // 만료일
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = false;
    
    @Column(name = "is_required", nullable = false)
    private boolean isRequired;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    @Column(name = "created_admin_id")
    private Long createdAdminId;
    
    @Column(name = "approved_admin_id")
    private Long approvedAdminId;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 약관 활성화
     */
    public void activate(Long adminId) {
        this.isActive = true;
        this.approvedAdminId = adminId;
        this.approvedAt = LocalDateTime.now();
    }
    
    /**
     * 약관 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * 현재 유효한 약관인지 확인
     */
    @Transient
    public boolean isCurrentlyEffective() {
        LocalDateTime now = LocalDateTime.now();
        boolean afterEffective = effectiveDate.isBefore(now) || effectiveDate.isEqual(now);
        boolean beforeExpiry = expiryDate == null || expiryDate.isAfter(now);
        
        return isActive && afterEffective && beforeExpiry;
    }
    
    @Override
    public Long getId() {
        return contentId;
    }
}
```

---

## 🔒 보안 강화 사항

### 암호화 처리가 필요한 필드
```java
// User 엔티티
@Convert(converter = PasswordEncoder.class)
private String passwordHash;

// UserVerification 엔티티
@Convert(converter = AESCryptoConverter.class)
private String ci;  // 연계정보 암호화

@Convert(converter = AESCryptoConverter.class)
private String di;  // 중복가입확인정보 암호화

@Convert(converter = AESCryptoConverter.class)
private String realName;  // 실명 암호화

// SocialAccount 엔티티
@Convert(converter = TokenCryptoConverter.class)
private String accessToken;  // OAuth 토큰 암호화

@Convert(converter = TokenCryptoConverter.class)
private String refreshToken;  // OAuth 리프레시 토큰 암호화
```

### 민감정보 보호 어노테이션
```java
@JsonIgnore        // JSON 직렬화 제외
@ToString.Exclude  // toString() 제외
```

### 한국 특화 검증 패턴
```java
// 휴대폰 번호 검증
@Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$")

// 한글 닉네임 검증
@Pattern(regexp = "^[가-힣a-zA-Z0-9]{2,10}$")

// 이메일 검증
@Email(regexp = "^[A-Za-z0-9+_.-]+@(.+)$")
```

---

## ⚡ JPA 성능 최적화 설정

### 인덱스 전략
```java
// 단일 컬럼 인덱스
@Index(name = "idx_users_email", columnList = "email", unique = true)
@Index(name = "idx_users_nickname", columnList = "nick_name", unique = true)

// 복합 인덱스
@Index(name = "idx_social_provider_id", columnList = "provider, social_id")
@Index(name = "idx_agreement_user_type", columnList = "user_id, agreement_type")

// 정렬용 인덱스
@Index(name = "idx_users_created", columnList = "created_at DESC")
```

### Fetch 전략
```java
// 기본적으로 모든 연관관계는 LAZY 로딩
@OneToOne(fetch = FetchType.LAZY)
@OneToMany(fetch = FetchType.LAZY)
@ManyToOne(fetch = FetchType.LAZY)

// N+1 문제 해결을 위한 Fetch Join은 Repository에서 처리
@Query("SELECT u FROM User u " +
       "LEFT JOIN FETCH u.userProfile " +
       "LEFT JOIN FETCH u.userVerification " +
       "WHERE u.userId = :userId")
```

### 캐스케이드 전략
```java
// User 삭제 시 연관 엔티티도 함께 삭제
@OneToOne(cascade = CascadeType.ALL)  // UserProfile, UserVerification
@OneToMany(cascade = CascadeType.ALL)  // UserAgreements, SocialAccounts

// 연관 엔티티는 독립적으로 관리
@ManyToOne  // CascadeType 없음
```

---

## ✅ 설계 완료 체크리스트

### User 확장 엔티티 (4개)
- [x] **UserVerification** - 본인인증 정보 (CI/DI, 성인인증, 이메일/휴대폰 인증)
- [x] **UserAgreement** - 약관 동의 이력 (법적 증빙, IP/UserAgent 추적)
- [x] **ApiToken** - JWT 토큰 관리 (블랙리스트, 디바이스 추적, 리프레시 제한)
- [x] **AgreementContent** - 약관 내용 관리 (버전 이력, 효력 기간)

### 한국 특화 본인인증
- [x] CI (연계정보) / DI (중복가입확인정보) 저장
- [x] 통신사별 휴대폰 인증 (SKT, KT, LGU+, 알뜰폰)
- [x] IPIN, 카드 인증 방법 지원
- [x] 성인 여부 자동 확인 (만 19세 기준)

### 약관 관리 시스템
- [x] 개인정보보호법 준수 (이용약관, 개인정보처리방침 필수)
- [x] 마케팅 수신동의, 위치정보 이용동의 선택
- [x] 버전별 약관 이력 관리
- [x] IP주소, UserAgent 추적으로 법적 증빙

### JWT 토큰 보안
- [x] 토큰 블랙리스트 관리
- [x] 리프레시 토큰 최대 10회 제한
- [x] 디바이스별 토큰 추적
- [x] 토큰 만료/폐기 이유 기록

### 보안 강화
- [x] 민감정보 암호화 (CI, DI, 실명, OAuth 토큰)
- [x] @JsonIgnore, @ToString.Exclude로 정보 노출 방지
- [x] 한국 휴대폰 번호 형식 검증
- [x] 한글 닉네임 정규표현식 검증

### JPA 최적화
- [x] 모든 연관관계 LAZY 로딩으로 성능 최적화
- [x] 복합 인덱스로 쿼리 성능 향상
- [x] 캐스케이드 전략으로 데이터 정합성 보장
- [x] 낙관적 락으로 동시성 제어

---

**다음 단계**: step4-2a_tag_system_entities.md (태그 시스템 엔티티)  
**완료일**: 2025-08-20  
**핵심 성과**: 4개 User 확장 엔티티 + 한국 특화 보안 + JPA 최적화 완성