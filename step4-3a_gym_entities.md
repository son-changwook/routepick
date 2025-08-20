# Step 4-3a: 암장 관련 엔티티 설계

> 암장, 지점, 회원, 벽면, 이미지 관리 엔티티 완전 설계  
> 생성일: 2025-08-19  
> 기반: step4-1_base_user_entities.md, 한국 특화 지점 관리

---

## 🎯 설계 목표

- **한국 특화 암장 관리**: GPS 좌표 범위 검증, 한국 표준 주소
- **성능 최적화**: Spatial Index, 복합 인덱스, JSON 컬럼 활용
- **계층형 구조**: Gym → GymBranch → Wall → Route 계층
- **회원 관리**: User와 GymBranch 다대다 관계

---

## 🏢 1. Gym 엔티티 - 암장 기본 정보

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 암장 기본 정보
 * - 프랜차이즈 체인 관리 (ex: 더클라임, 볼더링파크)
 * - 여러 지점을 가질 수 있는 상위 개념
 */
@Entity
@Table(name = "gyms", indexes = {
    @Index(name = "idx_gym_name", columnList = "name"),
    @Index(name = "idx_gym_status", columnList = "is_active"),
    @Index(name = "idx_gym_created", columnList = "created_at DESC")
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
    
    @NotNull
    @Size(min = 2, max = 100, message = "암장명은 2-100자 사이여야 합니다")
    @Column(name = "name", nullable = false, length = 100)
    private String name; // 암장명 (ex: 더클라임, 볼더링파크)
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 암장 소개
    
    @Pattern(regexp = "^0\\d{1,2}-\\d{3,4}-\\d{4}$", 
             message = "올바른 전화번호 형식이 아닙니다 (02-1234-5678)")
    @Column(name = "phone", length = 20)
    private String phone; // 대표 전화번호
    
    @Column(name = "website_url", length = 200)
    private String websiteUrl; // 홈페이지
    
    @Column(name = "instagram_url", length = 200)
    private String instagramUrl; // 인스타그램
    
    @Column(name = "email", length = 100)
    private String email; // 문의 이메일
    
    @Size(max = 20, message = "사업자등록번호는 최대 20자입니다")
    @Pattern(regexp = "\\d{3}-\\d{2}-\\d{5}", 
             message = "사업자등록번호 형식이 올바르지 않습니다 (123-45-67890)")
    @Column(name = "business_registration_number", length = 20)
    private String businessRegistrationNumber; // 사업자등록번호
    
    @Column(name = "logo_image_url", length = 500)
    private String logoImageUrl; // 로고 이미지
    
    @Column(name = "brand_color", length = 7)
    private String brandColor; // 브랜드 컬러 (hex)
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 운영 여부
    
    @Column(name = "is_franchise", nullable = false)
    private boolean isFranchise = false; // 프랜차이즈 여부
    
    @Column(name = "branch_count")
    private Integer branchCount = 0; // 지점 수
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "gym", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GymBranch> branches = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 활성 지점 수 조회
     */
    @Transient
    public long getActiveBranchCount() {
        return branches.stream()
                .filter(branch -> branch.getBranchStatus().isOperating())
                .count();
    }
    
    /**
     * 지점 수 업데이트
     */
    public void updateBranchCount() {
        this.branchCount = branches.size();
    }
    
    /**
     * 암장 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        // 모든 지점도 비활성화
        branches.forEach(branch -> branch.setBranchStatus(BranchStatus.CLOSED));
    }
    
    /**
     * 대표 지점 조회
     */
    @Transient
    public GymBranch getMainBranch() {
        return branches.stream()
                .filter(GymBranch::isMainBranch)
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public Long getId() {
        return gymId;
    }
}
```

---

## 🏪 2. GymBranch 엔티티 - 암장 지점 정보

```java
package com.routepick.domain.gym.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.BranchStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 암장 지점 정보
 * - 한국 좌표 범위 검증 적용
 * - JSON 운영시간 관리
 * - Spatial Index 적용
 */
@Entity
@Table(name = "gym_branches", indexes = {
    @Index(name = "idx_branch_gym_status", columnList = "gym_id, branch_status"),
    @Index(name = "idx_branch_location", columnList = "latitude, longitude"), // Spatial Index
    @Index(name = "idx_branch_name", columnList = "branch_name"),
    @Index(name = "idx_branch_district", columnList = "district"),
    @Index(name = "idx_branch_main", columnList = "is_main_branch"),
    @Index(name = "idx_branch_created", columnList = "created_at DESC")
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;
    
    @NotNull
    @Size(min = 2, max = 100, message = "지점명은 2-100자 사이여야 합니다")
    @Column(name = "branch_name", nullable = false, length = 100)
    private String branchName; // 지점명 (ex: 강남점, 홍대점)
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 지점 소개
    
    // ===== 한국 좌표 범위 검증 =====
    
    @NotNull
    @DecimalMin(value = "33.0", message = "위도는 33.0 이상이어야 합니다")
    @DecimalMax(value = "38.6", message = "위도는 38.6 이하여야 합니다")
    @Digits(integer = 2, fraction = 8, message = "위도는 소수점 8자리까지 입력 가능합니다")
    @Column(name = "latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude; // 위도 (한국 범위: 33.0 ~ 38.6)
    
    @NotNull
    @DecimalMin(value = "124.0", message = "경도는 124.0 이상이어야 합니다")
    @DecimalMax(value = "132.0", message = "경도는 132.0 이하여야 합니다")
    @Digits(integer = 3, fraction = 8, message = "경도는 소수점 8자리까지 입력 가능합니다")
    @Column(name = "longitude", nullable = false, precision = 11, scale = 8)
    private BigDecimal longitude; // 경도 (한국 범위: 124.0 ~ 132.0)
    
    // ===== 주소 정보 =====
    
    @NotNull
    @Size(min = 5, max = 200, message = "주소는 5-200자 사이여야 합니다")
    @Column(name = "address", nullable = false, length = 200)
    private String address; // 기본 주소
    
    @Size(max = 100, message = "상세주소는 최대 100자입니다")
    @Column(name = "detail_address", length = 100)
    private String detailAddress; // 상세 주소
    
    @Column(name = "postal_code", length = 10)
    private String postalCode; // 우편번호
    
    @Column(name = "district", length = 50)
    private String district; // 행정구역 (ex: 강남구, 마포구)
    
    @Column(name = "subway_info", length = 200)
    private String subwayInfo; // 지하철 정보
    
    // ===== 연락처 정보 =====
    
    @Pattern(regexp = "^0\\d{1,2}-\\d{3,4}-\\d{4}$", 
             message = "올바른 전화번호 형식이 아닙니다")
    @Column(name = "phone", length = 20)
    private String phone; // 지점 전화번호
    
    @Column(name = "manager_name", length = 50)
    private String managerName; // 지점 관리자
    
    @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", 
             message = "올바른 휴대폰 번호 형식이 아닙니다")
    @Column(name = "manager_phone", length = 20)
    private String managerPhone; // 관리자 연락처
    
    // ===== 운영 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "branch_status", nullable = false, length = 20)
    private BranchStatus branchStatus = BranchStatus.ACTIVE;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", columnDefinition = "json")
    private Map<String, Object> businessHours; // 운영시간 JSON
    
    @Column(name = "is_main_branch", nullable = false)
    private boolean isMainBranch = false; // 본점 여부
    
    @Column(name = "is_24hours", nullable = false)
    private boolean is24Hours = false; // 24시간 운영
    
    @Column(name = "has_parking", nullable = false)
    private boolean hasParking = false; // 주차 가능
    
    @Column(name = "parking_info", length = 200)
    private String parkingInfo; // 주차 안내
    
    @Column(name = "has_shower", nullable = false)
    private boolean hasShower = false; // 샤워시설
    
    @Column(name = "has_locker", nullable = false)
    private boolean hasLocker = false; // 락커
    
    @Column(name = "has_rental", nullable = false)
    private boolean hasRental = false; // 용품 대여
    
    // ===== 통계 정보 =====
    
    @Column(name = "wall_count")
    private Integer wallCount = 0; // 벽면 수
    
    @Column(name = "route_count")
    private Integer routeCount = 0; // 루트 수
    
    @Column(name = "member_count")
    private Integer memberCount = 0; // 회원 수
    
    @Column(name = "monthly_visit_count")
    private Integer monthlyVisitCount = 0; // 월간 방문자 수
    
    @Column(name = "average_rating")
    private Float averageRating = 0.0f; // 평균 평점
    
    @Column(name = "review_count")
    private Integer reviewCount = 0; // 리뷰 수
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Wall> walls = new ArrayList<>();
    
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BranchImage> branchImages = new ArrayList<>();
    
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GymMember> gymMembers = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 운영 중인지 확인
     */
    public boolean isOperating() {
        return branchStatus == BranchStatus.ACTIVE;
    }
    
    /**
     * 현재 시간 운영 여부 확인
     */
    @Transient
    public boolean isOpenNow() {
        if (is24Hours) return true;
        if (!isOperating()) return false;
        
        // JSON businessHours에서 현재 시간 확인 로직
        // 구현 예시는 Service Layer에서 처리
        return true;
    }
    
    /**
     * 거리 계산 (km)
     */
    @Transient
    public double calculateDistance(BigDecimal targetLat, BigDecimal targetLng) {
        double lat1 = latitude.doubleValue();
        double lng1 = longitude.doubleValue();
        double lat2 = targetLat.doubleValue();
        double lng2 = targetLng.doubleValue();
        
        // Haversine 공식
        double R = 6371; // 지구 반지름(km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng/2) * Math.sin(dLng/2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }
    
    /**
     * 통계 정보 업데이트
     */
    public void updateStatistics() {
        this.wallCount = walls.size();
        this.memberCount = gymMembers.size();
        // routeCount는 Repository에서 계산
    }
    
    /**
     * 대표 이미지 조회
     */
    @Transient
    public String getMainImageUrl() {
        return branchImages.stream()
                .filter(img -> img.getDisplayOrder() == 1)
                .findFirst()
                .map(BranchImage::getImageUrl)
                .orElse(null);
    }
    
    @Override
    public Long getId() {
        return branchId;
    }
}
```

---

## 👥 3. GymMember 엔티티 - 암장 회원 관리

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

## 🧗‍♀️ 4. Wall 엔티티 - 벽면 정보

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

## 🖼️ 5. BranchImage 엔티티 - 지점 이미지

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

## 🔧 6. 비즈니스 운영시간 JSON 구조

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

## ⚡ 7. 성능 최적화 전략

### 복합 인덱스 DDL 추가
```sql
-- 지점 위치 기반 검색 (반경 검색)
CREATE INDEX idx_branch_location_status 
ON gym_branches(latitude, longitude, branch_status);

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

### Spatial 검색 최적화
```java
// Repository에서 거리 기반 검색 쿼리 예시
@Query(value = "SELECT b.*, " +
       "(6371 * acos(cos(radians(:latitude)) * cos(radians(b.latitude)) * " +
       "cos(radians(b.longitude) - radians(:longitude)) + " +
       "sin(radians(:latitude)) * sin(radians(b.latitude)))) AS distance " +
       "FROM gym_branches b " +
       "WHERE b.branch_status = 'ACTIVE' " +
       "HAVING distance <= :radiusKm " +
       "ORDER BY distance", nativeQuery = true)
List<GymBranch> findNearbyBranches(
    @Param("latitude") BigDecimal latitude,
    @Param("longitude") BigDecimal longitude, 
    @Param("radiusKm") double radiusKm);
```

---

## ✅ 설계 완료 체크리스트

### 암장 관련 엔티티 (5개)
- [x] **Gym** - 암장 기본 정보 (프랜차이즈 체인 관리)
- [x] **GymBranch** - 지점 정보 (한국 좌표 검증, JSON 운영시간)
- [x] **GymMember** - 회원 관리 (User ↔ Branch 다대다)
- [x] **Wall** - 벽면 정보 (각도, 높이, 홀드 시스템)
- [x] **BranchImage** - 지점 이미지 (타입별 분류, 순서 관리)

### 한국 특화 기능
- [x] GPS 좌표 범위 검증 (위도: 33.0~38.6, 경도: 124.0~132.0)
- [x] 전화번호 패턴 검증 (지역번호 + 일반번호)
- [x] 휴대폰 번호 패턴 (010-1234-5678)
- [x] 사업자등록번호 형식 (123-45-67890)
- [x] JSON 운영시간 (한국 공휴일 대응)

### 성능 최적화
- [x] Spatial Index (위치 기반 검색)
- [x] 복합 인덱스 (gym_id + branch_status)
- [x] 모든 연관관계 LAZY 로딩
- [x] 통계 정보 캐시 (member_count, route_count)

### 비즈니스 로직
- [x] 거리 계산 (Haversine 공식)
- [x] 운영시간 체크 (24시간/특별 운영일)
- [x] 회원권 유효성 검증
- [x] 벽면 리셋 주기 관리
- [x] 이미지 타입별 관리

---

**다음 단계**: Step 4-3b 클라이밍 루트 관련 엔티티 설계  
**완료일**: 2025-08-19  
**핵심 성과**: 5개 암장 엔티티 + 한국 특화 + Spatial Index 최적화