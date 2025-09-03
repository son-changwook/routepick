# Step 4-1a: 기본 공통 엔티티 및 Enum 설계

> **RoutePickr 공통 기반 클래스** - BaseEntity, SoftDelete, 공통 Enum 9개  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-1a (JPA 엔티티 50개 - 기본 공통 클래스)  
> **분할**: step4-1_base_user_entities.md → 기본 공통 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 모든 엔티티 기반이 되는 공통 클래스들**을 담고 있습니다.

### 🎯 설계 목표
- **BaseEntity 추상 클래스**: JPA Auditing 기반 공통 필드 관리
- **공통 Enum 클래스**: 전체 도메인에서 사용할 상태값 표준화
- **SoftDelete 지원**: 데이터 삭제 시 물리적 삭제 대신 논리적 삭제
- **한국 특화**: 소셜 로그인 4개 Provider, 결제 상태, 알림 시스템

### 📊 구성 요소
1. **BaseEntity** - 모든 엔티티의 기본 클래스
2. **SoftDeleteEntity** - 소프트 삭제 지원 엔티티
3. **공통 Enum 9개** - 도메인 전체 상태값 표준화

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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

import java.util.Arrays;

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

## ✅ 설계 완료 체크리스트

### BaseEntity 설계 (2개)
- [x] **BaseEntity** - JPA Auditing, equals/hashCode, 버전 관리
- [x] **SoftDeleteEntity** - 논리적 삭제, 복구 기능

### 공통 Enum 클래스 (9개)
- [x] **UserType** - 사용자 유형 (일반/관리자/체육관 관리자)
- [x] **UserStatus** - 사용자 상태 (활성/비활성/정지/삭제)
- [x] **BranchStatus** - 체육관 지점 상태 (운영중/중단/폐업/오픈예정)
- [x] **RouteStatus** - 루트 상태 (활성/만료/제거)
- [x] **PaymentStatus** - 결제 상태 (대기/완료/실패/취소/환불)
- [x] **AgreementType** - 약관 유형 (이용약관/개인정보/마케팅/위치정보)
- [x] **TokenType** - 토큰 유형 (액세스/리프레시/비밀번호재설정/이메일인증)
- [x] **NotificationType** - 알림 유형 (시스템/댓글/좋아요/팔로우/완등/루트/결제)
- [x] **SocialProvider** - 소셜 로그인 (구글/카카오/네이버/페이스북)

### 한국 특화 기능
- [x] 카카오, 네이버 소셜 로그인 지원
- [x] 한국 결제 상태 관리 (PG사 연동 준비)
- [x] FCM 푸시 알림 토픽 설정
- [x] 개인정보보호법 준수 약관 유형

### 기술적 특징
- [x] JPA Auditing 자동 생성/수정 추적
- [x] 낙관적 락 지원 (@Version)
- [x] 소프트 삭제로 데이터 보호
- [x] Spring Security 권한 시스템 연동

---

**다음 단계**: step4-1b_user_core_entities.md (User 핵심 엔티티)  
**완료일**: 2025-08-20  
**핵심 성과**: 11개 기본 공통 클래스 + 한국 특화 Enum + JPA 최적화 완성