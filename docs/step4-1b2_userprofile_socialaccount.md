# Step 4-1b2: UserProfile 및 SocialAccount 엔티티

> **RoutePickr User 확장 시스템** - UserProfile, SocialAccount 엔티티  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-1b2 (User 확장 엔티티)  
> **분할**: step4-1b_user_core_entities.md → UserProfile, SocialAccount 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 User 확장 시스템**을 담고 있습니다.

### 🎯 주요 특징
- **UserProfile**: 클라이밍 특화 프로필, JSON 선호도 저장
- **SocialAccount**: 4개 Provider 소셜 로그인 (구글, 카카오, 네이버, 페이스북)
- **클라이밍 특화**: 신체 정보, 레벨, 통계 관리

---

## 👤 UserProfile.java - 사용자 상세 프로필

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

---

## 🔗 SocialAccount.java - 소셜 로그인 계정

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

## 📈 UserProfile 주요 특징

### 1. **클라이밍 특화 정보**
- **신체 정보**: 키, 몸무게, 팔 리치 (cm 단위)
- **클라이밍 레벨**: ClimbingLevel 엔티티와 연결
- **홈 체육관**: GymBranch와 ManyToOne 관계
- **경력**: 클라이밍 시작 연수 추적

### 2. **스마트 계산 기능**
- **나이 자동 계산**: 생년월일 기반
- **BMI 자동 계산**: 키/몸무게 기반
- **프로필 완성도**: 10개 필드 기반 퍼센트 계산
- **완등 통계**: 총/월간 완등 수 자동 집계

### 3. **JSON Preferences**
- **유연한 저장**: Map<String, Object> 타입
- **MySQL JSON**: @JdbcTypeCode(SqlTypes.JSON)
- **개인화 설정**: 사용자별 맞춤 설정 저장
- **확장성**: 새로운 설정 추가 용이

### 4. **소셜 기능**
- **팔로워/팔로잉**: 카운트 관리
- **공개 설정**: 프로필 공개/비공개 선택
- **자기소개**: TEXT 타입 bio 필드

---

## 🔗 SocialAccount 주요 특징

### 1. **4개 Provider 지원**
- **Google**: 글로벌 표준 OAuth2
- **Kakao**: 한국 1위 메신저
- **Naver**: 한국 1위 포털
- **Facebook**: 글로벌 소셜 네트워크

### 2. **토큰 관리**
- **Access Token**: API 호출용 토큰
- **Refresh Token**: 토큰 갱신용
- **만료 시간**: 자동 만료 체크
- **보안**: @JsonIgnore로 토큰 숨김

### 3. **다중 계정 지원**
- **Primary 계정**: 주 소셜 계정 설정
- **여러 Provider**: 동시 연동 가능
- **복합 유니크**: provider + social_id

### 4. **사용자 정보 동기화**
- **소셜 이메일**: Provider별 이메일
- **소셜 이름**: Provider별 이름
- **프로필 이미지**: 소셜 프로필 이미지 URL
- **로그인 추적**: 마지막 로그인 시간

---

## ✅ 설계 완료 체크리스트

### UserProfile 엔티티
- [x] **클라이밍 특화**: 레벨, 홈 체육관, 경력, 신체정보
- [x] **자동 계산**: 나이, BMI, 프로필 완성도
- [x] **JSON Preferences**: 유연한 사용자 설정 저장
- [x] **소셜 기능**: 팔로워/팔로잉, 공개/비공개

### SocialAccount 엔티티
- [x] **4개 Provider**: Google, Kakao, Naver, Facebook
- [x] **토큰 관리**: Access/Refresh Token, 만료 체크
- [x] **다중 계정**: Primary 계정 설정, 복합 유니크
- [x] **정보 동기화**: 소셜 이메일, 이름, 프로필 이미지

### JPA 최적화
- [x] **인덱스 전략**: user_id 유니크, level_id, branch_id
- [x] **LAZY 로딩**: 성능 최적화를 위한 지연 로딩
- [x] **JSON 컬럼**: MySQL JSON 타입 활용
- [x] **비즈니스 메서드**: 도메인 로직 캡슐화

---

**📝 연관 파일**: 
- step4-1b1_user_entity_core.md (User 엔티티 핵심)
- step4-1c_user_extended_entities.md (User 확장 엔티티)

---

**다음 단계**: User 확장 엔티티 (UserVerification, UserAgreement 등)  
**완료일**: 2025-08-20  
**핵심 성과**: UserProfile + SocialAccount + 클라이밍 특화 완성