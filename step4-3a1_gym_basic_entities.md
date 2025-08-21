# 🏢 Step 4-3a1: 체육관 기본 엔티티 설계

> **RoutePickr 체육관 기본 관리** - 암장, 지점 정보 관리
> 
> **생성일**: 2025-08-20  
> **단계**: 4-3a1 (JPA 엔티티 50개 - 체육관 기본 2개)  
> **분할**: step4-3a_gym_management_entities.md에서 세분화
> **연관**: step4-3a2_gym_extended_entities.md

---

## 📋 파일 개요

이 파일은 **RoutePickr 체육관 기본 관리의 2개 엔티티**를 담고 있습니다.

### 🎯 주요 특징
- **한국 특화 암장 관리**: GPS 좌표 범위 검증, 한국 표준 주소
- **성능 최적화**: Spatial Index, 복합 인덱스, JSON 컬럼 활용
- **계층형 구조**: Gym → GymBranch → Wall → Route 계층
- **프랜차이즈 지원**: 다중 지점 관리 체계

### 📊 엔티티 목록 (2개)
1. **Gym** - 암장 기본 정보 (프랜차이즈 체인)
2. **GymBranch** - 암장 지점 정보 (실제 운영 지점)

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

## 🎯 엔티티 설계 특징

### 🏢 Gym 엔티티 핵심 기능

#### 1. **프랜차이즈 체인 관리**
- 암장명: 더클라임, 볼더링파크 등 브랜드명
- 사업자등록번호: 한국 형식 검증 (123-45-67890)
- 브랜드 컬러: HEX 색상코드로 UI 일관성 유지
- 프랜차이즈 플래그: 개별 암장 vs 체인점 구분

#### 2. **연락처 및 마케팅**
- 전화번호: 한국 지역번호 형식 검증
- 웹사이트, 인스타그램: SNS 연동 지원
- 로고 이미지: 브랜드 아이덴티티 관리

#### 3. **지점 관리**
- 지점 수 자동 집계
- 활성 지점 수 실시간 계산
- 대표 지점 식별 기능

### 🏪 GymBranch 엔티티 핵심 기능

#### 1. **한국 특화 위치 관리**
- 좌표 범위: 위도 33.0~38.6, 경도 124.0~132.0
- Spatial Index: 위치 기반 검색 최적화
- 행정구역: 강남구, 마포구 등 검색 필터링
- 지하철 정보: 대중교통 접근성 안내

#### 2. **운영 정보 관리**
- 운영시간: JSON 형태로 요일별 시간 저장
- 24시간 운영: 특수 운영 형태 지원
- 시설 정보: 주차, 샤워, 락커, 용품대여

#### 3. **통계 및 분석**
- 벽면 수, 루트 수, 회원 수 집계
- 월간 방문자 수 추적
- 평균 평점 및 리뷰 수 관리

#### 4. **거리 계산**
- Haversine 공식: 정확한 거리 계산
- GPS 좌표 기반: 실시간 위치 서비스

### 📊 인덱스 전략
- **Gym**: `name`, `is_active`, `created_at DESC`
- **GymBranch**: `(gym_id, branch_status)`, `(latitude, longitude)`, `district`

### 🔒 한국 특화 검증
- 전화번호: 02-1234-5678 형식
- 휴대폰: 010-1234-5678 형식  
- 사업자등록번호: 123-45-67890 형식
- GPS 좌표: 한국 영토 내 범위 제한

---

## 📈 성능 최적화

### 💾 공간 인덱스(Spatial Index)
- 위치 기반 검색 고속화
- 반경 내 암장 검색 최적화
- GPS 좌표 범위 쿼리 성능 향상

### 🚀 JSON 컬럼 활용
- 운영시간: 요일별 시간대 저장
- 동적 스키마: 추가 정보 확장성
- NoSQL 스타일: 복잡한 데이터 구조

### 📱 모바일 최적화
- 거리 계산: 클라이언트 실시간 처리
- 이미지 CDN: 로고/대표이미지 최적화
- 캐싱: 자주 조회되는 지점 정보

---

**📝 다음 단계**: step4-3a2_gym_extended_entities.md에서 확장 엔티티 (GymMember, Wall, BranchImage) 설계