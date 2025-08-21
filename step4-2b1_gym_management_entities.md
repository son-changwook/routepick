# Step 4-2b1: 암장 관리 엔티티 설계

> **RoutePickr 암장 관리 시스템** - 암장 체인점, 지점, 회원, 벽면, 이미지 관리  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-2b1 (JPA 엔티티 50개 - 암장 관리 5개)  
> **분할**: step4-2b_gym_route_entities.md → 암장 관리 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 암장 관리 시스템**을 담고 있습니다.

### 🎯 주요 특징
- **계층형 구조**: Gym → GymBranch → Wall → Route
- **한국 특화**: GPS 좌표 범위, 주소 체계, 사업자등록번호
- **멤버십 관리**: 회원권 종류별 관리, 자동 갱신 시스템
- **미디어 관리**: AWS S3 연동 이미지 시스템

### 📊 엔티티 목록 (5개)
1. **Gym** - 암장 마스터 정보 (체인점 관리)
2. **GymBranch** - 암장 지점 정보 (GPS, 한국 주소체계)
3. **GymMember** - 암장 회원 관리 (회원권 종류별 관리)
4. **Wall** - 벽면 정보 (물리적 특성, 루트 용량)
5. **BranchImage** - 암장 이미지 (AWS S3, 타입별 분류)

---

## 🏢 1. Gym 엔티티 - 암장 마스터 정보

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * 암장 마스터 정보 엔티티
 * - 암장 체인점 관리
 * - 1:N 관계로 여러 지점 보유
 */
@Entity
@Table(name = "gyms", indexes = {
    @Index(name = "idx_gyms_name", columnList = "gym_name"),
    @Index(name = "idx_gyms_business", columnList = "business_registration_number", unique = true),
    @Index(name = "idx_gyms_status", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Gym extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gym_id")
    private Long gymId;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "gym_name", nullable = false, length = 100)
    private String gymName;
    
    @Size(max = 12)
    @Column(name = "business_registration_number", unique = true, length = 12)
    private String businessRegistrationNumber; // 한국 사업자등록번호
    
    @Size(max = 50)
    @Column(name = "ceo_name", length = 50)
    private String ceoName;
    
    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;
    
    @Size(max = 500)
    @Column(name = "website_url", length = 500)
    private String websiteUrl;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @NotBlank
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    // 연관관계
    @OneToMany(mappedBy = "gym", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GymBranch> branches = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 지점 추가
     */
    public void addBranch(GymBranch branch) {
        branches.add(branch);
        branch.setGym(this);
    }
    
    /**
     * 활성 지점 수 조회
     */
    @Transient
    public int getActiveBranchCount() {
        return (int) branches.stream()
            .filter(GymBranch::isActive)
            .count();
    }
    
    /**
     * 암장 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        // 모든 지점도 비활성화
        branches.forEach(GymBranch::deactivate);
    }
    
    @Override
    public Long getId() {
        return gymId;
    }
}
```

---

## 🏢 2. GymBranch 엔티티 - 암장 지점 정보

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 암장 지점 정보 엔티티
 * - 한국 특화: 좌표계, 주소 체계
 * - Spatial Index 준비
 */
@Entity
@Table(name = "gym_branches", indexes = {
    @Index(name = "idx_branches_location", columnList = "latitude, longitude"),
    @Index(name = "idx_branches_address", columnList = "address"),
    @Index(name = "idx_branches_active", columnList = "is_active"),
    @Index(name = "idx_branches_gym", columnList = "gym_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GymBranch extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long branchId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "branch_name", nullable = false, length = 100)
    private String branchName;
    
    @NotBlank
    @Size(max = 200)
    @Column(name = "address", nullable = false, length = 200)
    private String address;
    
    @Size(max = 200)
    @Column(name = "detailed_address", length = 200)
    private String detailedAddress;
    
    // 한국 좌표계 (WGS84) - Spatial Index 적용 예정
    @NotNull
    @DecimalMin(value = "33.0", message = "위도는 33.0 이상이어야 합니다 (한국 최남단)")
    @DecimalMax(value = "38.5", message = "위도는 38.5 이하여야 합니다 (한국 최북단)")
    @Column(name = "latitude", precision = 10, scale = 8, nullable = false)
    private BigDecimal latitude;
    
    @NotNull
    @DecimalMin(value = "125.0", message = "경도는 125.0 이상이어야 합니다 (한국 최서단)")
    @DecimalMax(value = "132.0", message = "경도는 132.0 이하여야 합니다 (한국 최동단)")
    @Column(name = "longitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal longitude;
    
    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @Column(name = "operating_hours", length = 100)
    private String operatingHours;
    
    @Column(name = "day_pass_price")
    private Integer dayPassPrice; // 일일 이용료
    
    @Column(name = "monthly_pass_price")
    private Integer monthlyPassPrice; // 월 이용료
    
    @Column(name = "shoe_rental_price")
    private Integer shoeRentalPrice; // 신발 대여비
    
    @NotBlank
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "parking_available")
    @ColumnDefault("false")
    private boolean parkingAvailable = false;
    
    @Column(name = "shower_available")
    @ColumnDefault("false")
    private boolean showerAvailable = false;
    
    @Column(name = "wifi_available")
    @ColumnDefault("false")
    private boolean wifiAvailable = false;
    
    // 연관관계
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Wall> walls = new ArrayList<>();
    
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BranchImage> images = new ArrayList<>();
    
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GymMember> members = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 거리 계산 (Haversine 공식) - km 단위
     */
    @Transient
    public double calculateDistance(BigDecimal targetLat, BigDecimal targetLon) {
        double lat1 = latitude.doubleValue();
        double lon1 = longitude.doubleValue();
        double lat2 = targetLat.doubleValue();
        double lon2 = targetLon.doubleValue();
        
        final int R = 6371; // 지구 반지름 (km)
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * 활성 벽면 수 조회
     */
    @Transient
    public int getActiveWallCount() {
        return (int) walls.stream()
            .filter(Wall::isActive)
            .count();
    }
    
    /**
     * 지점 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        // 모든 벽면도 비활성화
        walls.forEach(Wall::deactivate);
    }
    
    /**
     * 전체 주소 반환
     */
    @Transient
    public String getFullAddress() {
        if (detailedAddress != null && !detailedAddress.trim().isEmpty()) {
            return address + " " + detailedAddress;
        }
        return address;
    }
    
    @Override
    public Long getId() {
        return branchId;
    }
}
```

---

## 👤 3. GymMember 엔티티 - 암장 회원 관리

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.MembershipType;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;

/**
 * 암장 회원 관리 엔티티
 * - User ↔ GymBranch 다대다 관계
 * - 회원권 종류별 관리
 */
@Entity
@Table(name = "gym_members", indexes = {
    @Index(name = "idx_gym_members_user", columnList = "user_id"),
    @Index(name = "idx_gym_members_branch", columnList = "branch_id"),
    @Index(name = "idx_gym_members_active", columnList = "is_active"),
    @Index(name = "idx_gym_members_expiry", columnList = "membership_end_date"),
    @Index(name = "uk_user_branch", columnList = "user_id, branch_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GymMember extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "membership_type", nullable = false, length = 20)
    private MembershipType membershipType;
    
    @NotNull
    @Column(name = "membership_start_date", nullable = false)
    private LocalDate membershipStartDate;
    
    @NotNull
    @Column(name = "membership_end_date", nullable = false)
    private LocalDate membershipEndDate;
    
    @Column(name = "payment_amount")
    private Integer paymentAmount;
    
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "auto_renewal")
    @ColumnDefault("false")
    private boolean autoRenewal = false;
    
    @Column(name = "membership_number", length = 50)
    private String membershipNumber; // 암장별 회원번호
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes; // 특이사항
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 회원권 만료 여부 확인
     */
    @Transient
    public boolean isExpired() {
        return LocalDate.now().isAfter(membershipEndDate);
    }
    
    /**
     * 잔여 일수 계산
     */
    @Transient
    public long getRemainingDays() {
        LocalDate now = LocalDate.now();
        if (now.isAfter(membershipEndDate)) return 0;
        return now.until(membershipEndDate).getDays();
    }
    
    /**
     * 회원권 연장
     */
    public void extendMembership(int months) {
        this.membershipEndDate = membershipEndDate.plusMonths(months);
        this.isActive = true;
    }
    
    /**
     * 회원권 해지
     */
    public void cancel() {
        this.isActive = false;
        this.autoRenewal = false;
    }
    
    /**
     * 자동 갱신 활성화
     */
    public void enableAutoRenewal() {
        this.autoRenewal = true;
    }
    
    @Override
    public Long getId() {
        return memberId;
    }
}
```

---

## 🧱 4. Wall 엔티티 - 벽면 정보

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.WallType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * 벽면 정보 엔티티
 * - 암장의 개별 벽면 관리
 * - 경사각, 높이 등 물리적 특성
 */
@Entity
@Table(name = "walls", indexes = {
    @Index(name = "idx_walls_branch", columnList = "branch_id"),
    @Index(name = "idx_walls_type", columnList = "wall_type"),
    @Index(name = "idx_walls_angle", columnList = "wall_angle"),
    @Index(name = "idx_walls_active", columnList = "is_active")
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
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;
    
    @NotBlank
    @Column(name = "wall_name", nullable = false, length = 50)
    private String wallName;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "wall_type", nullable = false, length = 20)
    private WallType wallType;
    
    @Min(value = -30, message = "벽 각도는 -30도 이상이어야 합니다")
    @Max(value = 180, message = "벽 각도는 180도 이하여야 합니다")
    @Column(name = "wall_angle")
    private Integer wallAngle; // 벽면 경사각 (도 단위)
    
    @Column(name = "wall_height")
    private Double wallHeight; // 벽 높이 (미터)
    
    @Column(name = "wall_width")
    private Double wallWidth; // 벽 너비 (미터)
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive = true;
    
    @Column(name = "route_capacity")
    private Integer routeCapacity; // 동시 설치 가능 루트 수
    
    @Column(name = "color", length = 7)
    private String color; // 벽면 색상 (HEX)
    
    // 연관관계
    @OneToMany(mappedBy = "wall", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Route> routes = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 활성 루트 수 조회
     */
    @Transient
    public int getActiveRouteCount() {
        return (int) routes.stream()
            .filter(Route::isActive)
            .count();
    }
    
    /**
     * 루트 용량 여유분 확인
     */
    @Transient
    public int getAvailableCapacity() {
        if (routeCapacity == null) return Integer.MAX_VALUE;
        return Math.max(0, routeCapacity - getActiveRouteCount());
    }
    
    /**
     * 벽면 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        // 모든 루트도 비활성화
        routes.forEach(Route::deactivate);
    }
    
    /**
     * 벽면 각도 분류 반환
     */
    @Transient
    public String getAngleCategory() {
        if (wallAngle == null) return "UNKNOWN";
        
        if (wallAngle <= -10) return "OVERHANG_SEVERE";
        if (wallAngle <= 0) return "OVERHANG";
        if (wallAngle <= 15) return "SLAB";
        if (wallAngle <= 30) return "VERTICAL";
        if (wallAngle <= 45) return "STEEP";
        return "ROOF";
    }
    
    /**
     * 벽면 면적 계산
     */
    @Transient
    public Double getWallArea() {
        if (wallHeight == null || wallWidth == null) return null;
        return wallHeight * wallWidth;
    }
    
    @Override
    public Long getId() {
        return wallId;
    }
}
```

---

## 📸 5. BranchImage 엔티티 - 암장 이미지

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.ImageType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * 암장 지점 이미지 엔티티
 * - AWS S3 연동
 * - 이미지 타입별 분류
 */
@Entity
@Table(name = "branch_images", indexes = {
    @Index(name = "idx_branch_images_branch", columnList = "branch_id"),
    @Index(name = "idx_branch_images_type", columnList = "image_type"),
    @Index(name = "idx_branch_images_order", columnList = "branch_id, display_order")
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
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private GymBranch branch;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 20)
    private ImageType imageType;
    
    @NotBlank
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;
    
    @Column(name = "original_filename", length = 255)
    private String originalFilename;
    
    @Column(name = "file_size")
    private Long fileSize; // 바이트 단위
    
    @Column(name = "image_width")
    private Integer imageWidth; // 픽셀
    
    @Column(name = "image_height")
    private Integer imageHeight; // 픽셀
    
    @Column(name = "display_order")
    @ColumnDefault("0")
    private Integer displayOrder = 0;
    
    @Column(name = "alt_text", length = 200)
    private String altText; // 접근성을 위한 대체 텍스트
    
    @Column(name = "caption", length = 500)
    private String caption; // 이미지 설명
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 파일 크기를 사람이 읽기 쉬운 형태로 변환
     */
    @Transient
    public String getFormattedFileSize() {
        if (fileSize == null) return "Unknown";
        
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }
    
    /**
     * 이미지 비율 계산
     */
    @Transient
    public Double getAspectRatio() {
        if (imageWidth == null || imageHeight == null || imageHeight == 0) return null;
        return (double) imageWidth / imageHeight;
    }
    
    /**
     * 썸네일 URL 반환 (없으면 원본 반환)
     */
    @Transient
    public String getDisplayUrl() {
        return thumbnailUrl != null ? thumbnailUrl : imageUrl;
    }
    
    @Override
    public Long getId() {
        return imageId;
    }
}
```

---

## ✅ 설계 완료 체크리스트

### 암장 관리 엔티티 (5개)
- [x] **Gym** - 암장 마스터 정보 (체인점 관리, 사업자등록번호, 지점 관리)
- [x] **GymBranch** - 암장 지점 정보 (GPS 좌표, 한국 주소체계, Haversine 거리계산)
- [x] **GymMember** - 암장 회원 관리 (회원권 종류, 만료일 관리, 자동 갱신)
- [x] **Wall** - 벽면 정보 (물리적 특성, 루트 용량, 각도별 분류)
- [x] **BranchImage** - 암장 이미지 (AWS S3, 타입별 분류, 썸네일 지원)

### 한국 특화 기능
- [x] 한국 GPS 좌표 범위 검증 (위도: 33.0-38.5, 경도: 125.0-132.0)
- [x] 사업자등록번호 관리 (12자리 UNIQUE 제약)
- [x] Haversine 공식 기반 정확한 거리 계산
- [x] 한국 주소 체계 지원 (기본주소 + 상세주소)

### 비즈니스 로직 특징
- [x] 계층형 비활성화 (Gym → GymBranch → Wall)
- [x] 회원권 만료일 자동 계산 및 연장 관리
- [x] 벽면 루트 용량 관리 및 여유분 계산
- [x] 이미지 파일 크기 자동 포맷팅 및 비율 계산

### AWS S3 연동
- [x] 원본 이미지 URL 및 썸네일 URL 분리 관리
- [x] 파일 메타데이터 (크기, 해상도, 원본파일명) 저장
- [x] 접근성을 위한 alt_text 지원
- [x] 표시 순서 관리 (display_order)

### 성능 최적화
- [x] GPS 좌표 복합 인덱스로 위치 검색 최적화
- [x] 사업자등록번호 UNIQUE 인덱스로 중복 방지
- [x] 회원권 만료일 인덱스로 만료 예정 회원 조회 최적화
- [x] 이미지 타입별 인덱스로 갤러리 기능 최적화

---

**다음 단계**: step4-2b2_route_management_entities.md (루트 관리 엔티티)  
**완료일**: 2025-08-20  
**핵심 성과**: 5개 암장 관리 엔티티 + 한국 특화 기능 + AWS S3 연동 + 계층형 관리 완성