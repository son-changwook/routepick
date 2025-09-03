# Step 4-1b1: User 엔티티 핵심 설계

> **RoutePickr User 핵심 시스템** - User 엔티티와 Spring Security 연동  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-1b1 (User 엔티티 핵심)  
> **분할**: step4-1b_user_core_entities.md → User 엔티티 핵심 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 핵심 User 엔티티**를 담고 있습니다.

### 🎯 주요 특징
- **User**: Spring Security 완전 연동, 한국 특화 검증
- **보안 강화**: 패스워드 암호화, 실패 카운트, 토큰 만료 관리
- **UserDetails 구현**: 계정 상태 관리 및 권한 제어

---

## 👤 User.java - 사용자 기본 정보

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

---

## 📈 User 엔티티 주요 특징

### 1. **Spring Security 완전 연동**
- **UserDetails 구현**: Spring Security와 완벽 연동
- **권한 관리**: UserType 기반 GrantedAuthority
- **계정 상태**: 만료, 잠금, 자격증명, 활성화 체크
- **90일 비밀번호 정책**: 자동 만료 체크

### 2. **한국 특화 기능**
- **한글 닉네임**: 한글/영문/숫자 2-10자 검증
- **휴대폰 번호**: 010-1234-5678 형식 검증
- **주소 체계**: 기본주소 + 상세주소 분리
- **비상연락처**: 클라이밍 안전 고려

### 3. **보안 강화**
- **패스워드 해시**: @JsonIgnore, @ToString.Exclude
- **로그인 실패**: 5회 실패 시 계정 자동 잠금
- **소프트 삭제**: 개인정보 마스킹 후 보관
- **로그인 추적**: 성공/실패 시간 및 횟수 기록

### 4. **JPA 최적화**
- **인덱스 전략**: email, nickname 유니크 인덱스
- **LAZY 로딩**: 성능 최적화를 위한 지연 로딩
- **소프트 삭제 필터**: @Where 어노테이션 사용
- **복합 관계**: OneToOne, OneToMany 관계 정의

---

**📝 연관 파일**: 
- step4-1b2_userprofile_socialaccount.md (UserProfile, SocialAccount)
- step4-1c_user_extended_entities.md (User 확장 엔티티)

---

**다음 단계**: UserProfile과 SocialAccount 엔티티 설계  
**완료일**: 2025-08-20  
**핵심 성과**: User 엔티티 + Spring Security 완전 연동 + 한국 특화 완성