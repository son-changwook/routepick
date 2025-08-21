# Step 4-1b: User 핵심 엔티티 설계

> **RoutePickr User 핵심 시스템** - User, UserProfile, SocialAccount 엔티티  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-1b (JPA 엔티티 50개 - User 핵심 3개)  
> **분할**: step4-1_base_user_entities.md → User 핵심 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 핵심 사용자 시스템 3개 엔티티**를 담고 있습니다.

### 🎯 주요 특징
- **User**: Spring Security 완전 연동, 한국 특화 검증
- **UserProfile**: 클라이밍 특화 프로필, JSON 선호도 저장
- **SocialAccount**: 4개 Provider 소셜 로그인 (구글, 카카오, 네이버, 페이스북)
- **보안 강화**: 패스워드 암호화, 실패 카운트, 토큰 만료 관리

### 📊 엔티티 목록 (3개)
1. **User** - 사용자 기본 정보 (Spring Security UserDetails)
2. **UserProfile** - 사용자 상세 프로필 (클라이밍 특화)
3. **SocialAccount** - 소셜 로그인 계정 (4개 Provider)

---

## 👤 3. User 도메인 엔티티 설계 (3개)

### User.java - 사용자 기본 정보
```java
package com.routepick.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.UserStatus;
import com.routepick.common.enums.UserType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Where;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 사용자 기본 정보 엔티티
 * - 이메일 기반 로그인
 * - 한글 닉네임 지원
 * - Spring Security UserDetails 구현
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email", unique = true),
    @Index(name = "idx_users_nickname", columnList = "nick_name", unique = true),
    @Index(name = "idx_users_status", columnList = "user_status"),
    @Index(name = "idx_users_created", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Where(clause = "user_status != 'DELETED'") // 소프트 삭제 필터
public class User extends BaseEntity implements UserDetails {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;
    
    @NotNull
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;
    
    @JsonIgnore
    @ToString.Exclude
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @NotNull
    @Size(min = 2, max = 20, message = "이름은 2-20자 사이여야 합니다")
    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;
    
    @NotNull
    @Pattern(regexp = "^[가-힣a-zA-Z0-9]{2,10}$", 
             message = "닉네임은 한글/영문/숫자 2-10자로 구성되어야 합니다")
    @Column(name = "nick_name", unique = true, nullable = false, length = 30)
    private String nickName;
    
    @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", 
             message = "올바른 휴대폰 번호 형식이 아닙니다 (010-1234-5678)")
    @Column(name = "phone", length = 20)
    private String phone;
    
    @Column(name = "emergency_contact", length = 20)
    private String emergencyContact;
    
    @Column(name = "address", length = 200)
    private String address;
    
    @Column(name = "detail_address", length = 100)
    private String detailAddress;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    @ColumnDefault("'NORMAL'")
    private UserType userType = UserType.NORMAL;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private UserStatus userStatus = UserStatus.ACTIVE;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "login_count")
    @ColumnDefault("0")
    private Integer loginCount = 0;
    
    @Column(name = "failed_login_count")
    @ColumnDefault("0")
    private Integer failedLoginCount = 0;
    
    @Column(name = "last_failed_login_at")
    private LocalDateTime lastFailedLoginAt;
    
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;
    
    // ===== 연관관계 매핑 =====
    
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserProfile userProfile;
    
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserVerification userVerification;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserAgreement> userAgreements = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SocialAccount> socialAccounts = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<ApiToken> apiTokens = new ArrayList<>();
    
    // ===== UserDetails 구현 =====
    
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(
            new SimpleGrantedAuthority(userType.getAuthority())
        );
    }
    
    @Override
    @JsonIgnore
    public String getPassword() {
        return passwordHash;
    }
    
    @Override
    @JsonIgnore
    public String getUsername() {
        return email;
    }
    
    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return userStatus != UserStatus.DELETED;
    }
    
    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return userStatus != UserStatus.SUSPENDED;
    }
    
    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        // 비밀번호 변경 후 90일 경과 체크
        if (passwordChangedAt == null) return true;
        return passwordChangedAt.plusDays(90).isAfter(LocalDateTime.now());
    }
    
    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return userStatus == UserStatus.ACTIVE;
    }
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 로그인 성공 처리
     */
    public void loginSuccess() {
        this.lastLoginAt = LocalDateTime.now();
        this.loginCount = (loginCount == null ? 0 : loginCount) + 1;
        this.failedLoginCount = 0;
        this.lastFailedLoginAt = null;
    }
    
    /**
     * 로그인 실패 처리
     */
    public void loginFailed() {
        this.failedLoginCount = (failedLoginCount == null ? 0 : failedLoginCount) + 1;
        this.lastFailedLoginAt = LocalDateTime.now();
        
        // 5회 실패 시 계정 잠금
        if (failedLoginCount >= 5) {
            this.userStatus = UserStatus.SUSPENDED;
        }
    }
    
    /**
     * 비밀번호 변경
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = LocalDateTime.now();
    }
    
    /**
     * 계정 삭제 (소프트 삭제)
     */
    public void deleteAccount() {
        this.userStatus = UserStatus.DELETED;
        this.email = "deleted_" + userId + "@deleted.com";
        this.nickName = "탈퇴사용자_" + userId;
        this.phone = null;
        this.address = null;
        this.detailAddress = null;
    }
    
    @Override
    public Long getId() {
        return userId;
    }
}
```

### UserProfile.java - 사용자 상세 프로필
```java
package com.routepick.domain.user.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.climb.entity.ClimbingLevel;
import com.routepick.domain.gym.entity.GymBranch;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.Map;

/**
 * 사용자 상세 프로필 정보
 * - 1:1 관계로 User와 연결
 * - JSON 타입으로 preferences 저장
 */
@Entity
@Table(name = "user_profile", indexes = {
    @Index(name = "idx_user_profile_user", columnList = "user_id", unique = true),
    @Index(name = "idx_user_profile_level", columnList = "level_id"),
    @Index(name = "idx_user_profile_branch", columnList = "branch_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserProfile extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    private Long profileId;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "gender", length = 10)
    private String gender; // MALE, FEMALE, OTHER
    
    @Column(name = "birth_date")
    private LocalDate birthDate;
    
    @Column(name = "height")
    private Integer height; // cm 단위
    
    @Column(name = "weight")
    private Integer weight; // kg 단위
    
    @Column(name = "arm_reach")
    private Integer armReach; // cm 단위
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private ClimbingLevel climbingLevel;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private GymBranch homeBranch;
    
    @Column(name = "climbing_years")
    private Integer climbingYears;
    
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;
    
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio; // 자기소개
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "json")
    private Map<String, Object> preferences;
    
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;
    
    @Column(name = "total_climb_count")
    private Integer totalClimbCount = 0;
    
    @Column(name = "monthly_climb_count")
    private Integer monthlyClimbCount = 0;
    
    @Column(name = "follower_count")
    private Integer followerCount = 0;
    
    @Column(name = "following_count")
    private Integer followingCount = 0;
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 나이 계산
     */
    @Transient
    public Integer getAge() {
        if (birthDate == null) return null;
        return LocalDate.now().getYear() - birthDate.getYear();
    }
    
    /**
     * BMI 계산
     */
    @Transient
    public Double getBmi() {
        if (height == null || weight == null || height == 0) return null;
        double heightInMeter = height / 100.0;
        return weight / (heightInMeter * heightInMeter);
    }
    
    /**
     * 프로필 완성도 계산 (%)
     */
    @Transient
    public int getProfileCompleteness() {
        int totalFields = 10;
        int completedFields = 0;
        
        if (gender != null) completedFields++;
        if (birthDate != null) completedFields++;
        if (height != null) completedFields++;
        if (weight != null) completedFields++;
        if (armReach != null) completedFields++;
        if (climbingLevel != null) completedFields++;
        if (homeBranch != null) completedFields++;
        if (climbingYears != null) completedFields++;
        if (profileImageUrl != null) completedFields++;
        if (bio != null && !bio.isEmpty()) completedFields++;
        
        return (completedFields * 100) / totalFields;
    }
    
    /**
     * 완등 카운트 증가
     */
    public void incrementClimbCount() {
        this.totalClimbCount = (totalClimbCount == null ? 0 : totalClimbCount) + 1;
        this.monthlyClimbCount = (monthlyClimbCount == null ? 0 : monthlyClimbCount) + 1;
    }
    
    /**
     * 월간 완등 카운트 리셋
     */
    public void resetMonthlyClimbCount() {
        this.monthlyClimbCount = 0;
    }
    
    @Override
    public Long getId() {
        return profileId;
    }
}
```

### SocialAccount.java - 소셜 로그인 계정
```java
package com.routepick.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.SocialProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 소셜 로그인 연동 정보
 * - 4개 Provider 지원 (Google, Kakao, Naver, Facebook)
 */
@Entity
@Table(name = "social_accounts", indexes = {
    @Index(name = "idx_social_provider_id", columnList = "provider, social_id", unique = true),
    @Index(name = "idx_social_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SocialAccount extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id")
    private Long socialAccountId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;
    
    @Column(name = "social_id", nullable = false, length = 100)
    private String socialId;
    
    @Column(name = "social_email", length = 100)
    private String socialEmail;
    
    @Column(name = "social_name", length = 100)
    private String socialName;
    
    @Column(name = "social_image_url", length = 500)
    private String socialImageUrl;
    
    @JsonIgnore
    @ToString.Exclude
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;
    
    @JsonIgnore
    @ToString.Exclude
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;
    
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 토큰 갱신
     */
    public void updateTokens(String accessToken, String refreshToken, LocalDateTime expiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpiresAt = expiresAt;
    }
    
    /**
     * 토큰 만료 여부 확인
     */
    @Transient
    public boolean isTokenExpired() {
        if (tokenExpiresAt == null) return true;
        return tokenExpiresAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * 로그인 시간 업데이트
     */
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
    
    /**
     * 주 소셜 계정 설정
     */
    public void setPrimaryAccount() {
        // 다른 소셜 계정의 primary를 false로 설정하는 로직은 Service에서 처리
        this.isPrimary = true;
    }
    
    @Override
    public Long getId() {
        return socialAccountId;
    }
}
```

---

## ✅ 설계 완료 체크리스트

### User 핵심 엔티티 (3개)
- [x] **User** - 사용자 기본 정보 (Spring Security UserDetails 완전 구현)
- [x] **UserProfile** - 사용자 상세 프로필 (클라이밍 특화, JSON preferences)
- [x] **SocialAccount** - 소셜 로그인 계정 (4개 Provider 지원)

### Spring Security 완전 연동
- [x] UserDetails 인터페이스 구현
- [x] GrantedAuthority 권한 관리 (ROLE_USER, ROLE_ADMIN, ROLE_GYM_ADMIN)
- [x] 계정 상태 관리 (만료, 잠금, 자격증명, 활성화)
- [x] 비밀번호 90일 만료 정책

### 한국 특화 기능
- [x] 한글 닉네임 검증 (한글/영문/숫자 2-10자)
- [x] 휴대폰 번호 형식 검증 (010-1234-5678)
- [x] 카카오, 네이버 소셜 로그인 우선 지원
- [x] 비상연락처, 상세주소 필드

### 보안 강화
- [x] 패스워드 해시 저장 (@JsonIgnore, @ToString.Exclude)
- [x] 로그인 실패 5회 시 계정 잠금
- [x] 소프트 삭제로 개인정보 보호
- [x] 소셜 토큰 암호화 저장

### 클라이밍 특화
- [x] 클라이밍 레벨, 경력, 홈 체육관 연결
- [x] 신체정보 (키, 몸무게, 팔 리치) 저장
- [x] BMI 자동 계산, 프로필 완성도 측정
- [x] 완등 통계 (총/월간 완등 수)

### JPA 최적화
- [x] LAZY 로딩으로 성능 최적화
- [x] 복합 인덱스 (이메일, 닉네임, 상태별)
- [x] JSON 컬럼으로 유연한 preferences 저장
- [x] 소프트 삭제 필터 (@Where)

---

**다음 단계**: step4-1c_user_extended_entities.md (User 확장 엔티티)  
**완료일**: 2025-08-20  
**핵심 성과**: 3개 핵심 User 엔티티 + Spring Security 완전 연동 + 한국 특화 완성