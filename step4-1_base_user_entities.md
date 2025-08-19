# Step 4-1: 기본 엔티티 및 User 도메인 설계

> BaseEntity, 공통 Enum, User 도메인 엔티티 완전 설계  
> 생성일: 2025-08-19  
> 기반: step1-1_schema_analysis.md, step1-3_spring_boot_guide.md, step2-1_backend_structure.md

---

## 🎯 설계 목표

- **BaseEntity 추상 클래스**: JPA Auditing 기반 공통 필드 관리
- **공통 Enum 클래스**: 전체 도메인에서 사용할 상태값 표준화
- **User 도메인 엔티티**: 7개 핵심 엔티티 설계 (보안 강화)
- **한국 특화 검증**: 휴대폰, 한글 닉네임, 본인인증 시스템

---

## 📋 1. BaseEntity 추상 클래스 설계

### BaseEntity.java - 모든 엔티티의 기본 클래스
```java
package com.routepick.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 모든 엔티티의 기본 클래스
 * - Auditing 필드 자동 관리
 * - equals, hashCode, toString 기본 구현
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity implements Serializable {
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;
    
    @LastModifiedBy
    @Column(name = "last_modified_by")
    private Long lastModifiedBy;
    
    @Version
    @Column(name = "version")
    private Long version; // 낙관적 락을 위한 버전 관리
    
    /**
     * 엔티티 ID를 반환하는 추상 메서드
     * 하위 클래스에서 반드시 구현해야 함
     */
    public abstract Long getId();
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        BaseEntity that = (BaseEntity) o;
        
        // ID가 null이 아닌 경우에만 비교
        if (getId() == null || that.getId() == null) {
            return false;
        }
        
        return Objects.equals(getId(), that.getId());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getClass().getName(), getId());
    }
    
    @Override
    public String toString() {
        return String.format("%s[id=%d, createdAt=%s, updatedAt=%s]",
            getClass().getSimpleName(),
            getId(),
            createdAt,
            updatedAt
        );
    }
    
    /**
     * 엔티티가 새로운 엔티티인지 확인
     */
    @Transient
    public boolean isNew() {
        return getId() == null;
    }
    
    /**
     * 생성 시간 기준 비교
     */
    public int compareByCreatedAt(BaseEntity other) {
        if (this.createdAt == null || other.createdAt == null) {
            return 0;
        }
        return this.createdAt.compareTo(other.createdAt);
    }
}
```

### SoftDeleteEntity.java - 소프트 삭제 지원 엔티티
```java
package com.routepick.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 소프트 삭제를 지원하는 엔티티 베이스 클래스
 * - 실제 데이터를 삭제하지 않고 삭제 플래그로 관리
 */
@MappedSuperclass
@Getter
public abstract class SoftDeleteEntity extends BaseEntity {
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;
    
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
    
    /**
     * 소프트 삭제 처리
     */
    public void delete(Long deletedBy) {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
    
    /**
     * 삭제 취소 (복구)
     */
    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
    }
    
    /**
     * 삭제 여부 확인
     */
    public boolean isDeleted() {
        return isDeleted || deletedAt != null;
    }
}
```

---

## 🔧 2. 공통 Enum 클래스 설계

### UserType.java - 사용자 유형
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 유형 정의
 */
@Getter
@RequiredArgsConstructor
public enum UserType {
    NORMAL("일반 사용자", "ROLE_USER"),
    ADMIN("시스템 관리자", "ROLE_ADMIN"),
    GYM_ADMIN("체육관 관리자", "ROLE_GYM_ADMIN");
    
    private final String description;
    private final String role;
    
    /**
     * 관리자 권한 여부 확인
     */
    public boolean isAdmin() {
        return this == ADMIN || this == GYM_ADMIN;
    }
    
    /**
     * Spring Security 권한 문자열 반환
     */
    public String getAuthority() {
        return this.role;
    }
}
```

### UserStatus.java - 사용자 상태
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 계정 상태
 */
@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("활성", true),
    INACTIVE("비활성", false),
    SUSPENDED("정지", false),
    DELETED("삭제", false);
    
    private final String description;
    private final boolean canLogin;
    
    /**
     * 로그인 가능 상태 확인
     */
    public boolean isLoginable() {
        return this == ACTIVE;
    }
    
    /**
     * 복구 가능 상태 확인
     */
    public boolean isRecoverable() {
        return this != DELETED;
    }
}
```

### BranchStatus.java - 체육관 지점 상태
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 체육관 지점 운영 상태
 */
@Getter
@RequiredArgsConstructor
public enum BranchStatus {
    ACTIVE("운영중", true),
    INACTIVE("임시중단", false),
    CLOSED("폐업", false),
    PENDING("오픈예정", false);
    
    private final String description;
    private final boolean isOperating;
    
    /**
     * 루트 등록 가능 여부
     */
    public boolean canAddRoute() {
        return this == ACTIVE;
    }
}
```

### RouteStatus.java - 루트 상태
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 클라이밍 루트 상태
 */
@Getter
@RequiredArgsConstructor
public enum RouteStatus {
    ACTIVE("활성", true, true),
    EXPIRED("만료", false, true),
    REMOVED("제거", false, false);
    
    private final String description;
    private final boolean isClimbable;
    private final boolean isVisible;
    
    /**
     * 추천 대상 여부
     */
    public boolean isRecommendable() {
        return this == ACTIVE;
    }
}
```

### PaymentStatus.java - 결제 상태
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 결제 처리 상태
 */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    PENDING("대기중", false, true),
    COMPLETED("완료", true, false),
    FAILED("실패", false, false),
    CANCELLED("취소", false, false),
    REFUNDED("환불", false, false);
    
    private final String description;
    private final boolean isSuccess;
    private final boolean canCancel;
    
    /**
     * 환불 가능 여부
     */
    public boolean isRefundable() {
        return this == COMPLETED;
    }
}
```

### AgreementType.java - 약관 유형
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 동의 약관 유형
 */
@Getter
@RequiredArgsConstructor
public enum AgreementType {
    TERMS("이용약관", true, 1),
    PRIVACY("개인정보처리방침", true, 2),
    MARKETING("마케팅 수신동의", false, 3),
    LOCATION("위치정보 이용동의", false, 4);
    
    private final String title;
    private final boolean isRequired;
    private final int displayOrder;
    
    /**
     * 필수 약관 목록 조회
     */
    public static List<AgreementType> getRequiredTypes() {
        return Arrays.stream(values())
            .filter(AgreementType::isRequired)
            .collect(Collectors.toList());
    }
}
```

### TokenType.java - 토큰 유형
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

/**
 * JWT 토큰 유형 및 유효기간
 */
@Getter
@RequiredArgsConstructor
public enum TokenType {
    ACCESS("액세스 토큰", Duration.ofMinutes(30)),
    REFRESH("리프레시 토큰", Duration.ofDays(7)),
    RESET_PASSWORD("비밀번호 재설정", Duration.ofHours(1)),
    EMAIL_VERIFICATION("이메일 인증", Duration.ofHours(24));
    
    private final String description;
    private final Duration validity;
    
    /**
     * 만료 시간(밀리초) 반환
     */
    public long getExpirationMillis() {
        return validity.toMillis();
    }
}
```

### NotificationType.java - 알림 유형
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 푸시 알림 유형
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {
    SYSTEM("시스템", "system", true),
    COMMENT("댓글", "comment", true),
    LIKE("좋아요", "like", true),
    FOLLOW("팔로우", "follow", true),
    CLIMB("완등", "climb", true),
    ROUTE_UPDATE("루트 업데이트", "route", true),
    PAYMENT("결제", "payment", false);
    
    private final String title;
    private final String topic;
    private final boolean isPushEnabled;
    
    /**
     * FCM 토픽명 생성
     */
    public String getFcmTopic() {
        return "notification_" + topic;
    }
}
```

### SocialProvider.java - 소셜 로그인 제공자
```java
package com.routepick.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 제공자 (4개 지원)
 */
@Getter
@RequiredArgsConstructor
public enum SocialProvider {
    GOOGLE("구글", "google", true),
    KAKAO("카카오", "kakao", true),
    NAVER("네이버", "naver", true),
    FACEBOOK("페이스북", "facebook", true);
    
    private final String displayName;
    private final String registrationId;
    private final boolean isEnabled;
    
    /**
     * Spring Security registrationId로 Provider 찾기
     */
    public static SocialProvider fromRegistrationId(String registrationId) {
        return Arrays.stream(values())
            .filter(provider -> provider.registrationId.equals(registrationId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + registrationId));
    }
    
    /**
     * 한국 주요 Provider 여부
     */
    public boolean isKoreanProvider() {
        return this == KAKAO || this == NAVER;
    }
}
```

---

## 👤 3. User 도메인 엔티티 설계 (7개)

### User.java - 사용자 기본 정보
```java
package com.routepick.domain.user.entity;

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

### UserVerification.java - 본인인증 정보
```java
package com.routepick.domain.user.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

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

## 🔒 4. 보안 강화 사항

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

## ⚡ 5. JPA 성능 최적화 설정

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

### BaseEntity 설계
- [x] JPA Auditing 필드 (createdAt, updatedAt, createdBy, lastModifiedBy)
- [x] 낙관적 락을 위한 @Version 필드
- [x] equals, hashCode, toString 기본 구현
- [x] SoftDeleteEntity 추가 설계

### 공통 Enum 클래스 (9개)
- [x] UserType - 사용자 유형 (NORMAL, ADMIN, GYM_ADMIN)
- [x] UserStatus - 사용자 상태 (ACTIVE, INACTIVE, SUSPENDED, DELETED)
- [x] BranchStatus - 지점 상태 (ACTIVE, INACTIVE, CLOSED, PENDING)
- [x] RouteStatus - 루트 상태 (ACTIVE, EXPIRED, REMOVED)
- [x] PaymentStatus - 결제 상태 (PENDING, COMPLETED, FAILED, CANCELLED, REFUNDED)
- [x] AgreementType - 약관 유형 (TERMS, PRIVACY, MARKETING, LOCATION)
- [x] TokenType - 토큰 유형 (ACCESS, REFRESH, RESET_PASSWORD, EMAIL_VERIFICATION)
- [x] NotificationType - 알림 유형 (7가지)
- [x] SocialProvider - 소셜 제공자 (GOOGLE, KAKAO, NAVER, FACEBOOK)

### User 도메인 엔티티 (7개)
- [x] User - 사용자 기본 정보 (UserDetails 구현)
- [x] UserProfile - 사용자 상세 프로필 (JSON preferences)
- [x] SocialAccount - 소셜 로그인 (4개 Provider)
- [x] UserVerification - 본인인증 (CI/DI)
- [x] UserAgreement - 약관 동의 이력
- [x] ApiToken - JWT 토큰 관리
- [x] AgreementContent - 약관 내용 버전 관리

### 보안 강화
- [x] 패스워드 암호화 (@JsonIgnore, @ToString.Exclude)
- [x] 민감정보 암호화 (CI, DI, 실명, 토큰)
- [x] 한국 특화 검증 (휴대폰, 한글 닉네임)
- [x] 이메일 형식 검증

### JPA 최적화
- [x] 모든 연관관계 LAZY 로딩
- [x] 적절한 인덱스 설계 (단일/복합)
- [x] 캐스케이드 전략 적용
- [x] 낙관적 락 버전 관리

---

**다음 단계**: Step 4-2 Gym, Route, Tag 도메인 엔티티 설계  
**완료일**: 2025-08-19  
**핵심 성과**: BaseEntity + 9개 Enum + 7개 User 엔티티 완전 설계