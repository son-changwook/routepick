# 🏢 Step 4-3a2: 체육관 확장 엔티티 설계

> **RoutePickr 체육관 확장 관리** - 회원, 벽면, 이미지 관리
> 
> **생성일**: 2025-08-20  
> **단계**: 4-3a2 (JPA 엔티티 50개 - 체육관 확장 3개)  
> **분할**: step4-3a_gym_management_entities.md에서 세분화
> **연관**: step4-3a1_gym_basic_entities.md

---

## 📋 파일 개요

이 파일은 **RoutePickr 체육관 확장 관리의 3개 엔티티**를 담고 있습니다.

### 🎯 주요 특징
- **회원 관리**: User와 GymBranch 다대다 관계
- **벽면 정보**: 클라이밍 물리적 특성 관리
- **이미지 관리**: 지점별 다중 이미지 업로드

### 📊 엔티티 목록 (3개)
1. **GymMember** - 암장 회원 관리 (회원권, 만료일)
2. **Wall** - 벽면 정보 (각도, 높이, 홀드 시스템)
3. **BranchImage** - 지점 이미지 (타입별 분류, 순서)

---

## 👥 1. GymMember 엔티티 - 암장 회원 관리

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 암장 회원 정보
 * - User와 GymBranch 다대다 관계 매핑 테이블
 * - 회원권 정보 및 만료일 관리
 */
@Entity
@Table(name = "gym_members", indexes = {
    @Index(name = "idx_member_user_branch", columnList = "user_id, branch_id", unique = true),
    @Index(name = "idx_member_user", columnList = "user_id"),
    @Index(name = "idx_member_branch", columnList = "branch_id"),
    @Index(name = "idx_member_status", columnList = "is_active"),
    @Index(name = "idx_member_expiry", columnList = "membership_end_date"),
    @Index(name = "idx_member_joined", columnList = "membership_start_date DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GymMember extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "membership_id")
    private Long membershipId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;
    
    @NotNull
    @Column(name = "membership_start_date", nullable = false)
    private LocalDate membershipStartDate; // 회원권 시작일
    
    @Future(message = "회원권 종료일은 미래 날짜여야 합니다")
    @Column(name = "membership_end_date")
    private LocalDate membershipEndDate; // 회원권 종료일
    
    @Column(name = "membership_type", length = 50)
    private String membershipType; // 회원권 종류 (월권, 연권, 기간권 등)
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 회원 여부
    
    @Column(name = "is_lifetime", nullable = false)
    private boolean isLifetime = false; // 평생 회원 여부
    
    @Column(name = "locker_number")
    private String lockerNumber; // 락커 번호
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes; // 특이사항
    
    @Column(name = "visit_count")
    private Integer visitCount = 0; // 방문 횟수
    
    @Column(name = "last_visit_date")
    private LocalDateTime lastVisitDate; // 마지막 방문일
    
    @Column(name = "membership_fee")
    private Integer membershipFee; // 회원권 가격
    
    @Column(name = "discount_rate")
    private Float discountRate = 0.0f; // 할인율
    
    @Column(name = "referrer_user_id")
    private Long referrerUserId; // 추천인
    
    @Column(name = "registration_channel", length = 50)
    private String registrationChannel; // 가입 경로 (앱, 현장 등)
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 회원권 유효성 확인
     */
    @Transient
    public boolean isValidMembership() {
        if (!isActive) return false;
        if (isLifetime) return true;
        if (membershipEndDate == null) return false;
        
        return membershipEndDate.isAfter(LocalDate.now()) || 
               membershipEndDate.isEqual(LocalDate.now());
    }
    
    /**
     * 회원권 만료까지 남은 일수
     */
    @Transient
    public long getDaysUntilExpiry() {
        if (isLifetime) return Long.MAX_VALUE;
        if (membershipEndDate == null) return 0;
        
        return LocalDate.now().until(membershipEndDate).getDays();
    }
    
    /**
     * 방문 처리
     */
    public void recordVisit() {
        this.visitCount = (visitCount == null ? 0 : visitCount) + 1;
        this.lastVisitDate = LocalDateTime.now();
    }
    
    /**
     * 회원권 연장
     */
    public void extendMembership(LocalDate newEndDate) {
        if (newEndDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("연장일은 현재 날짜보다 이후여야 합니다");
        }
        this.membershipEndDate = newEndDate;
    }
    
    /**
     * 회원권 정지
     */
    public void suspendMembership(String reason) {
        this.isActive = false;
        this.notes = (notes == null ? "" : notes + "\n") + 
                    "정지: " + LocalDate.now() + " - " + reason;
    }
    
    /**
     * 회원권 복원
     */
    public void restoreMembership() {
        this.isActive = true;
    }
    
    /**
     * 평생회원 전환
     */
    public void convertToLifetimeMember() {
        this.isLifetime = true;
        this.membershipEndDate = null;
        this.isActive = true;
    }
    
    @Override
    public Long getId() {
        return membershipId;
    }
}
```

---

## 🧗‍♀️ 2. Wall 엔티티 - 벽면 정보

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 벽면 정보
 * - 클라이밍 루트가 설정되는 물리적 벽면
 * - 각도, 높이, 홀드 타입 등 벽면 특성 관리
 */
@Entity
@Table(name = "walls", indexes = {
    @Index(name = "idx_wall_branch", columnList = "branch_id, wall_type"),
    @Index(name = "idx_wall_name", columnList = "wall_name"),
    @Index(name = "idx_wall_angle", columnList = "wall_angle"),
    @Index(name = "idx_wall_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Wall extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wall_id")
    private Long wallId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;
    
    @NotNull
    @Size(min = 1, max = 50, message = "벽면명은 1-50자 사이여야 합니다")
    @Column(name = "wall_name", nullable = false, length = 50)
    private String wallName; // 벽면명 (A벽, B벽, 오버행벽 등)
    
    @Column(name = "wall_type", length = 30)
    private String wallType; // 벽면 타입 (SLAB, VERTICAL, OVERHANG, ROOF)
    
    @Min(value = -30, message = "벽면 각도는 -30도 이상이어야 합니다")
    @Max(value = 180, message = "벽면 각도는 180도 이하여야 합니다")
    @Column(name = "wall_angle")
    private Integer wallAngle; // 벽면 각도 (슬랩: -30~0, 수직: 90, 오버행: 90~180)
    
    @Min(value = 2, message = "벽면 높이는 2m 이상이어야 합니다")
    @Max(value = 20, message = "벽면 높이는 20m 이하여야 합니다")
    @Column(name = "height")
    private Float height; // 벽면 높이 (미터)
    
    @Min(value = 1, message = "벽면 너비는 1m 이상이어야 합니다")
    @Max(value = 50, message = "벽면 너비는 50m 이하여야 합니다")
    @Column(name = "width")
    private Float width; // 벽면 너비 (미터)
    
    @Column(name = "surface_material", length = 50)
    private String surfaceMaterial; // 벽면 재질 (합판, 콘크리트, 인공암벽 등)
    
    @Column(name = "hold_brand", length = 50)
    private String holdBrand; // 홀드 브랜드 (Atomik, So iLL, Kilter 등)
    
    @Column(name = "color_system", length = 100)
    private String colorSystem; // 색상 시스템 (테이프/홀드 색상 구분법)
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 사용 가능 여부
    
    @Column(name = "is_competition_wall", nullable = false)
    private boolean isCompetitionWall = false; // 대회용 벽면
    
    @Column(name = "route_count")
    private Integer routeCount = 0; // 현재 설정된 루트 수
    
    @Column(name = "max_route_capacity")
    private Integer maxRouteCapacity = 10; // 최대 루트 수용량
    
    @Column(name = "last_reset_date")
    private java.time.LocalDate lastResetDate; // 마지막 루트 리셋일
    
    @Column(name = "reset_cycle_weeks")
    private Integer resetCycleWeeks = 4; // 루트 리셋 주기(주)
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes; // 특이사항
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "wall", fetch = FetchType.LAZY)
    private List<Route> routes = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 벽면 유형 분류
     */
    @Transient
    public String getWallTypeKorean() {
        if (wallAngle == null) return "미설정";
        
        if (wallAngle < 0) return "슬랩";
        else if (wallAngle < 85) return "슬랩";
        else if (wallAngle <= 95) return "수직벽";
        else if (wallAngle <= 135) return "오버행";
        else return "루프";
    }
    
    /**
     * 난이도 분포 조회
     */
    @Transient
    public String getDifficultyDistribution() {
        // Service Layer에서 구현
        return "V0-V2: 3개, V3-V5: 4개, V6+: 3개";
    }
    
    /**
     * 루트 추가 가능 여부
     */
    @Transient
    public boolean canAddRoute() {
        return isActive && routeCount < maxRouteCapacity;
    }
    
    /**
     * 리셋 필요 여부 확인
     */
    @Transient
    public boolean needsReset() {
        if (lastResetDate == null || resetCycleWeeks == null) return false;
        
        java.time.LocalDate nextResetDate = lastResetDate.plusWeeks(resetCycleWeeks);
        return java.time.LocalDate.now().isAfter(nextResetDate);
    }
    
    /**
     * 루트 수 업데이트
     */
    public void updateRouteCount(int count) {
        this.routeCount = Math.max(0, count);
    }
    
    /**
     * 루트 리셋 처리
     */
    public void resetWall() {
        this.lastResetDate = java.time.LocalDate.now();
        this.routeCount = 0;
    }
    
    /**
     * 벽면 비활성화
     */
    public void deactivate(String reason) {
        this.isActive = false;
        this.notes = (notes == null ? "" : notes + "\n") + 
                    "비활성화: " + java.time.LocalDate.now() + " - " + reason;
    }
    
    @Override
    public Long getId() {
        return wallId;
    }
}
```

---

## 🖼️ 3. BranchImage 엔티티 - 지점 이미지

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 지점 이미지 관리
 * - 지점별 여러 이미지 업로드
 * - 표시 순서 관리
 * - 이미지 타입 분류
 */
@Entity
@Table(name = "branch_images", indexes = {
    @Index(name = "idx_image_branch_order", columnList = "branch_id, display_order"),
    @Index(name = "idx_image_branch_type", columnList = "branch_id, image_type"),
    @Index(name = "idx_image_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BranchImage extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;
    
    @NotNull
    @Size(min = 10, max = 500, message = "이미지 URL은 10-500자 사이여야 합니다")
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl; // 이미지 URL
    
    @Column(name = "image_type", length = 30)
    private String imageType; // MAIN, INTERIOR, WALL, FACILITY, EXTERIOR
    
    @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다")
    @Max(value = 100, message = "표시 순서는 100 이하여야 합니다")
    @Column(name = "display_order")
    private Integer displayOrder = 1; // 표시 순서
    
    @Size(max = 200, message = "이미지 제목은 최대 200자입니다")
    @Column(name = "title", length = 200)
    private String title; // 이미지 제목
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 이미지 설명
    
    @Column(name = "alt_text", length = 200)
    private String altText; // 대체 텍스트 (SEO/접근성)
    
    @Column(name = "file_name", length = 200)
    private String fileName; // 원본 파일명
    
    @Column(name = "file_size")
    private Long fileSize; // 파일 크기 (bytes)
    
    @Column(name = "width")
    private Integer width; // 이미지 가로 크기
    
    @Column(name = "height")
    private Integer height; // 이미지 세로 크기
    
    @Column(name = "mime_type", length = 50)
    private String mimeType; // MIME 타입 (image/jpeg, image/png 등)
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "is_main", nullable = false)
    private boolean isMain = false; // 대표 이미지 여부
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 조회수
    
    @Column(name = "upload_ip", length = 45)
    private String uploadIp; // 업로드 IP (IPv6 지원)
    
    @Column(name = "uploader_id")
    private Long uploaderId; // 업로더 ID
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 이미지 타입 한글명 반환
     */
    @Transient
    public String getImageTypeKorean() {
        if (imageType == null) return "기본";
        
        return switch (imageType) {
            case "MAIN" -> "대표 이미지";
            case "INTERIOR" -> "내부 전경";
            case "WALL" -> "클라이밍 벽면";
            case "FACILITY" -> "편의시설";
            case "EXTERIOR" -> "외부 전경";
            default -> "기타";
        };
    }
    
    /**
     * 이미지 크기 정보 (가독성)
     */
    @Transient
    public String getImageSizeInfo() {
        if (width == null || height == null) return "알 수 없음";
        return width + "x" + height;
    }
    
    /**
     * 파일 크기 정보 (가독성)
     */
    @Transient
    public String getFileSizeInfo() {
        if (fileSize == null) return "알 수 없음";
        
        if (fileSize < 1024) return fileSize + "B";
        else if (fileSize < 1024 * 1024) return (fileSize / 1024) + "KB";
        else return (fileSize / (1024 * 1024)) + "MB";
    }
    
    /**
     * 대표 이미지로 설정
     */
    public void setAsMain() {
        this.isMain = true;
        this.displayOrder = 1;
    }
    
    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
    }
    
    /**
     * 이미지 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        this.isMain = false; // 대표 이미지도 해제
    }
    
    /**
     * 표시 순서 변경
     */
    public void changeDisplayOrder(Integer newOrder) {
        if (newOrder == null || newOrder < 1) {
            throw new IllegalArgumentException("표시 순서는 1 이상이어야 합니다");
        }
        this.displayOrder = newOrder;
    }
    
    /**
     * 썸네일 URL 생성 (예시)
     */
    @Transient
    public String getThumbnailUrl() {
        if (imageUrl == null) return null;
        
        // CDN 썸네일 변환 로직 (예: CloudFront, ImageKit 등)
        // 실제 구현은 Service Layer에서 처리
        String extension = imageUrl.substring(imageUrl.lastIndexOf('.'));
        String nameWithoutExt = imageUrl.substring(0, imageUrl.lastIndexOf('.'));
        return nameWithoutExt + "_thumb" + extension;
    }
    
    @Override
    public Long getId() {
        return imageId;
    }
}
```

---

## 🎯 엔티티 설계 특징

### 👥 GymMember 엔티티 핵심 기능

#### 1. **회원권 관리**
- 시작일/종료일: 회원권 유효 기간 관리
- 회원권 종류: 월권, 연권, 기간권, 평생권
- 유효성 검증: 만료일 기반 자동 검증
- 연장/정지/복원: 회원권 상태 변경

#### 2. **방문 추적**
- 방문 횟수: 암장 이용 패턴 분석
- 마지막 방문일: 휴면 회원 식별
- 방문 기록: 실시간 체크인 처리

#### 3. **부가 서비스**
- 락커 번호: 개인 락커 배정
- 추천인: 회원 추천 시스템
- 할인율: 개별 할인 혜택
- 가입 경로: 마케팅 분석

### 🧗‍♀️ Wall 엔티티 핵심 기능

#### 1. **물리적 특성**
- 벽면 각도: -30° ~ 180° (슬랩 → 루프)
- 높이/너비: 물리적 공간 정보
- 재질: 합판, 콘크리트, 인공암벽
- 홀드 브랜드: Atomik, So iLL, Kilter

#### 2. **루트 관리**
- 루트 수용량: 최대 설정 가능 루트 수
- 현재 루트 수: 실시간 루트 카운트
- 리셋 주기: 4주 기본 설정
- 대회용 벽면: 특별 관리 대상

#### 3. **벽면 분류**
- 자동 분류: 각도 기반 벽면 타입 판별
- 색상 시스템: 테이프/홀드 색상 구분
- 난이도 분포: V-Scale 기준 통계

### 🖼️ BranchImage 엔티티 핵심 기능

#### 1. **이미지 분류**
- 타입별 관리: 대표, 내부, 벽면, 시설, 외부
- 표시 순서: 1~100 순서 관리
- 대표 이미지: 메인 이미지 자동 설정

#### 2. **메타데이터 관리**
- 파일 정보: 크기, 해상도, MIME 타입
- SEO 최적화: ALT 텍스트, 제목, 설명
- 업로드 추적: IP, 업로더 ID 기록

#### 3. **성능 최적화**
- 썸네일 생성: CDN 연동 자동 변환
- 조회수 추적: 이미지별 조회 패턴
- 파일 크기 표시: B, KB, MB 자동 변환

### 📊 인덱스 전략
- **GymMember**: `(user_id, branch_id)` UNIQUE, `membership_end_date`, `is_active`
- **Wall**: `(branch_id, wall_type)`, `wall_angle`, `is_active`
- **BranchImage**: `(branch_id, display_order)`, `(branch_id, image_type)`

### 🔒 보안 고려사항
- 회원 정보 격리: branch_id 기반 접근 제어
- 이미지 업로드: IP 추적, 용량 제한
- 개인정보: 락커 번호, 할인율 암호화 필요

---

## 🔧 비즈니스 운영시간 JSON 구조

### BusinessHours JSON 스키마
```json
{
  "regular": {
    "monday": {
      "open": "06:00",
      "close": "23:00",
      "closed": false
    },
    "tuesday": {
      "open": "06:00", 
      "close": "23:00",
      "closed": false
    },
    "wednesday": {
      "open": "06:00",
      "close": "23:00", 
      "closed": false
    },
    "thursday": {
      "open": "06:00",
      "close": "23:00",
      "closed": false
    },
    "friday": {
      "open": "06:00",
      "close": "24:00",
      "closed": false
    },
    "saturday": {
      "open": "08:00",
      "close": "22:00",
      "closed": false
    },
    "sunday": {
      "open": "08:00",
      "close": "22:00",
      "closed": false
    }
  },
  "special": {
    "2025-01-01": {
      "closed": true,
      "reason": "신정"
    },
    "2025-12-25": {
      "closed": true,
      "reason": "크리스마스"
    }
  },
  "break_time": {
    "enabled": true,
    "start": "12:00",
    "end": "13:00"
  }
}
```

---

## 📈 성능 최적화

### 💾 복합 인덱스 DDL 추가
```sql
-- 지점별 회원 만료일 검색
CREATE INDEX idx_member_branch_expiry 
ON gym_members(branch_id, membership_end_date, is_active);

-- 벽면별 루트 통계
CREATE INDEX idx_wall_routes_stats 
ON walls(branch_id, is_active, route_count);

-- 이미지 메인/순서 정렬
CREATE INDEX idx_image_main_display 
ON branch_images(branch_id, is_main DESC, display_order ASC, is_active);
```

### 🚀 캐싱 전략
- 회원권 유효성: Redis 캐싱 (1시간)
- 벽면 리셋 일정: 메모리 캐싱
- 대표 이미지: CDN 캐싱

### 📱 모바일 최적화
- 이미지 최적화: WebP 변환, 압축
- 썸네일: 다양한 해상도 자동 생성
- 회원 체크인: QR 코드 연동

---

## ✅ 설계 완료 체크리스트

### 체육관 확장 엔티티 (3개)
- [x] **GymMember** - 회원 관리 (User ↔ Branch 다대다)
- [x] **Wall** - 벽면 정보 (각도, 높이, 홀드 시스템)
- [x] **BranchImage** - 지점 이미지 (타입별 분류, 순서 관리)

### 비즈니스 로직
- [x] 회원권 유효성 검증 (만료일, 평생권)
- [x] 벽면 타입 자동 분류 (각도 기반)
- [x] 루트 리셋 주기 관리
- [x] 이미지 타입별 분류 및 순서

### 성능 최적화
- [x] 회원-지점 복합 인덱스 (UNIQUE)
- [x] 벽면-타입 복합 인덱스
- [x] 이미지-순서 복합 인덱스
- [x] 조회수 추적 최적화

### 한국 특화 기능
- [x] 회원권 시스템 (월권, 연권, 평생권)
- [x] 락커 번호 관리
- [x] 공휴일 운영시간 JSON
- [x] 한글 벽면 분류 (슬랩, 수직, 오버행, 루프)

---

**📝 완료**: 체육관 관리 엔티티 세분화 작업 완료 (5개 → 2개 파일로 분할)