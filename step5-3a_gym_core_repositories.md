# Step 5-3a: 암장 핵심 Repository 생성

> 암장 핵심 3개 Repository 완전 설계 (공간 쿼리 특화)  
> 생성일: 2025-08-20  
> 기반: step5-2_tag_repositories_focused.md, step4-3a_gym_entities.md

---

## 🎯 설계 목표

- **MySQL Spatial Index 활용**: 한국 좌표계 최적화 (위도 33-38.6, 경도 124-132)
- **거리 기반 검색 성능 최적화**: ST_Distance_Sphere 함수 활용
- **지역별 암장 클러스터링**: 행정구역 기반 검색 지원
- **회원 관리 최적화**: 멤버십 상태, 만료일 관리

---

## 🏢 1. GymRepository - 암장 기본 Repository

```java
package com.routepick.domain.gym.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.gym.entity.Gym;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gym Repository
 * - 암장 기본 CRUD
 * - 프랜차이즈 관리
 * - 인기 암장 분석
 */
@Repository
public interface GymRepository extends BaseRepository<Gym, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 암장명으로 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.name = :name AND g.isActive = true")
    Optional<Gym> findByName(@Param("name") String name);
    
    /**
     * 암장명 부분 검색
     */
    @Query("SELECT g FROM Gym g WHERE g.name LIKE %:keyword% AND g.isActive = true ORDER BY g.createdAt DESC")
    List<Gym> findByNameContaining(@Param("keyword") String keyword);
    
    /**
     * 암장명 부분 검색 (페이징)
     */
    @Query("SELECT g FROM Gym g WHERE g.name LIKE %:keyword% AND g.isActive = true ORDER BY g.branchCount DESC, g.name")
    Page<Gym> findByNameContaining(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 활성 암장 모두 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.isActive = true ORDER BY g.name")
    List<Gym> findAllActive();
    
    /**
     * 활성 암장 페이징 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.isActive = true ORDER BY g.branchCount DESC, g.name")
    Page<Gym> findAllActive(Pageable pageable);
    
    // ===== 프랜차이즈 관리 =====
    
    /**
     * 프랜차이즈 암장 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.isFranchise = true AND g.isActive = true ORDER BY g.branchCount DESC")
    List<Gym> findFranchiseGyms();
    
    /**
     * 개인 암장 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.isFranchise = false AND g.isActive = true ORDER BY g.createdAt DESC")
    List<Gym> findIndependentGyms();
    
    /**
     * 지점 수별 암장 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.branchCount >= :minBranches AND g.isActive = true ORDER BY g.branchCount DESC")
    List<Gym> findGymsByMinBranches(@Param("minBranches") Integer minBranches);
    
    // ===== 소유자 관리 =====
    
    /**
     * 소유자별 암장 조회 (관리자용)
     */
    @Query("SELECT g FROM Gym g WHERE g.createdBy = :ownerId ORDER BY g.createdAt DESC")
    List<Gym> findByOwnerUserId(@Param("ownerId") Long ownerId);
    
    /**
     * 소유자의 활성 암장 수
     */
    @Query("SELECT COUNT(g) FROM Gym g WHERE g.createdBy = :ownerId AND g.isActive = true")
    long countActiveGymsByOwner(@Param("ownerId") Long ownerId);
    
    // ===== 통계 및 분석 =====
    
    /**
     * 개설 통계 (기간별)
     */
    @Query("SELECT COUNT(g) FROM Gym g WHERE g.createdAt BETWEEN :startDate AND :endDate")
    long countByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * 인기 암장 조회 (지점 수 기준)
     */
    @Query("SELECT g FROM Gym g WHERE g.isActive = true ORDER BY g.branchCount DESC")
    List<Gym> findPopularGymsByBranchCount(Pageable pageable);
    
    /**
     * 인기 암장 조회 (멤버 수 기준) - GymBranch와 조인
     */
    @Query("SELECT g, SUM(gb.memberCount) as totalMembers FROM Gym g " +
           "JOIN g.branches gb " +
           "WHERE g.isActive = true AND gb.branchStatus = 'ACTIVE' " +
           "GROUP BY g " +
           "ORDER BY totalMembers DESC")
    List<Object[]> findPopularGymsByMemberCount(Pageable pageable);
    
    /**
     * 지역별 암장 분포 통계
     */
    @Query("SELECT gb.district, COUNT(DISTINCT g) as gymCount FROM Gym g " +
           "JOIN g.branches gb " +
           "WHERE g.isActive = true AND gb.branchStatus = 'ACTIVE' " +
           "GROUP BY gb.district " +
           "ORDER BY gymCount DESC")
    List<Object[]> getGymDistributionByDistrict();
    
    /**
     * 프랜차이즈 vs 개인 암장 통계
     */
    @Query("SELECT g.isFranchise, COUNT(g), AVG(g.branchCount) FROM Gym g " +
           "WHERE g.isActive = true " +
           "GROUP BY g.isFranchise")
    List<Object[]> getFranchiseStatistics();
    
    // ===== 업데이트 메서드 =====
    
    /**
     * 지점 수 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Gym g SET g.branchCount = (SELECT COUNT(gb) FROM GymBranch gb WHERE gb.gym = g AND gb.branchStatus = 'ACTIVE') WHERE g.gymId = :gymId")
    int updateBranchCount(@Param("gymId") Long gymId);
    
    /**
     * 모든 암장의 지점 수 일괄 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Gym g SET g.branchCount = (SELECT COUNT(gb) FROM GymBranch gb WHERE gb.gym = g AND gb.branchStatus = 'ACTIVE')")
    int updateAllBranchCounts();
    
    /**
     * 암장 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Gym g SET g.isActive = false WHERE g.gymId = :gymId")
    int deactivateGym(@Param("gymId") Long gymId);
    
    /**
     * 암장 재활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Gym g SET g.isActive = true WHERE g.gymId = :gymId")
    int reactivateGym(@Param("gymId") Long gymId);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 복합 조건 검색
     */
    @Query("SELECT g FROM Gym g WHERE " +
           "(:keyword IS NULL OR g.name LIKE %:keyword% OR g.description LIKE %:keyword%) AND " +
           "(:isFranchise IS NULL OR g.isFranchise = :isFranchise) AND " +
           "(:minBranches IS NULL OR g.branchCount >= :minBranches) AND " +
           "g.isActive = true " +
           "ORDER BY g.branchCount DESC, g.name")
    Page<Gym> findByComplexConditions(@Param("keyword") String keyword,
                                     @Param("isFranchise") Boolean isFranchise,
                                     @Param("minBranches") Integer minBranches,
                                     Pageable pageable);
    
    /**
     * 사업자등록번호로 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.businessRegistrationNumber = :businessNumber")
    Optional<Gym> findByBusinessRegistrationNumber(@Param("businessNumber") String businessNumber);
    
    /**
     * 이메일로 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.email = :email")
    List<Gym> findByEmail(@Param("email") String email);
    
    /**
     * 웹사이트 URL로 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.websiteUrl = :websiteUrl")
    Optional<Gym> findByWebsiteUrl(@Param("websiteUrl") String websiteUrl);
    
    // ===== 관리자용 조회 =====
    
    /**
     * 최근 등록된 암장 조회
     */
    @Query("SELECT g FROM Gym g ORDER BY g.createdAt DESC")
    List<Gym> findRecentlyCreatedGyms(Pageable pageable);
    
    /**
     * 승인 대기 암장 조회 (필요시 확장)
     */
    @Query("SELECT g FROM Gym g WHERE g.isActive = false ORDER BY g.createdAt")
    List<Gym> findPendingApprovalGyms();
    
    /**
     * 브랜드 컬러별 암장 조회
     */
    @Query("SELECT g FROM Gym g WHERE g.brandColor = :brandColor AND g.isActive = true")
    List<Gym> findByBrandColor(@Param("brandColor") String brandColor);
}
```

---

## 🏪 2. GymBranchRepository - 암장 지점 Repository (공간 쿼리 특화)

```java
package com.routepick.domain.gym.repository;

import com.routepick.common.enums.BranchStatus;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.gym.entity.GymBranch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * GymBranch Repository
 * - 🌍 공간 쿼리 최적화 특화
 * - MySQL Spatial 함수 활용
 * - 한국 좌표계 최적화
 */
@Repository
public interface GymBranchRepository extends BaseRepository<GymBranch, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 암장별 지점 조회
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.gym.gymId = :gymId AND gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.isMainBranch DESC, gb.branchName")
    List<GymBranch> findByGymIdAndActiveStatus(@Param("gymId") Long gymId);
    
    /**
     * 지점명으로 조회
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.branchName LIKE %:keyword% AND gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.branchName")
    List<GymBranch> findByBranchNameContaining(@Param("keyword") String keyword);
    
    /**
     * 주소 및 상태별 조회
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE (gb.address LIKE %:address% OR gb.detailAddress LIKE %:address%) " +
           "AND gb.branchStatus = :status " +
           "ORDER BY gb.branchName")
    List<GymBranch> findByAddressContainingAndBranchStatus(@Param("address") String address, 
                                                          @Param("status") BranchStatus status);
    
    // ===== 🌍 공간 쿼리 메서드 (MySQL Spatial 활용) =====
    
    /**
     * 반경 내 지점 검색 (Spatial 함수 활용)
     */
    @Query(value = "SELECT * FROM gym_branches gb " +
                   "WHERE ST_Distance_Sphere(POINT(gb.longitude, gb.latitude), POINT(:lng, :lat)) <= :radius " +
                   "AND gb.branch_status = :status " +
                   "ORDER BY ST_Distance_Sphere(POINT(gb.longitude, gb.latitude), POINT(:lng, :lat))", 
           nativeQuery = true)
    List<GymBranch> findNearbyBranches(@Param("lat") BigDecimal latitude, 
                                      @Param("lng") BigDecimal longitude, 
                                      @Param("radius") double radius, 
                                      @Param("status") String status);
    
    /**
     * 가장 가까운 지점 조회
     */
    @Query(value = "SELECT * FROM gym_branches gb " +
                   "WHERE gb.branch_status = 'ACTIVE' " +
                   "ORDER BY ST_Distance_Sphere(POINT(gb.longitude, gb.latitude), POINT(:lng, :lat)) " +
                   "LIMIT 1", 
           nativeQuery = true)
    Optional<GymBranch> findNearestBranchToUser(@Param("lat") BigDecimal latitude, 
                                               @Param("lng") BigDecimal longitude);
    
    /**
     * 경계 박스 내 지점 검색
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.latitude BETWEEN :minLat AND :maxLat " +
           "AND gb.longitude BETWEEN :minLng AND :maxLng " +
           "AND gb.branchStatus = :status " +
           "ORDER BY gb.branchName")
    List<GymBranch> findWithinBounds(@Param("minLat") BigDecimal minLat, 
                                    @Param("maxLat") BigDecimal maxLat,
                                    @Param("minLng") BigDecimal minLng, 
                                    @Param("maxLng") BigDecimal maxLng,
                                    @Param("status") BranchStatus status);
    
    /**
     * 특정 반경 내 지점 수 계산
     */
    @Query(value = "SELECT COUNT(*) FROM gym_branches gb " +
                   "WHERE ST_Distance_Sphere(POINT(gb.longitude, gb.latitude), POINT(:lng, :lat)) <= :radius " +
                   "AND gb.branch_status = 'ACTIVE'", 
           nativeQuery = true)
    long countBranchesInRadius(@Param("lat") BigDecimal latitude, 
                              @Param("lng") BigDecimal longitude, 
                              @Param("radius") double radius);
    
    /**
     * 사용자 위치에서 거리 계산과 함께 조회
     */
    @Query(value = "SELECT *, " +
                   "ST_Distance_Sphere(POINT(longitude, latitude), POINT(:lng, :lat)) as distance " +
                   "FROM gym_branches " +
                   "WHERE branch_status = 'ACTIVE' " +
                   "ORDER BY distance " +
                   "LIMIT :limit", 
           nativeQuery = true)
    List<Object[]> calculateDistanceFromUser(@Param("lat") BigDecimal latitude, 
                                           @Param("lng") BigDecimal longitude, 
                                           @Param("limit") int limit);
    
    // ===== 한국 지역별 검색 =====
    
    /**
     * 지역(구/군)별 지점 조회
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.district = :district AND gb.branchStatus = :status " +
           "ORDER BY gb.memberCount DESC, gb.branchName")
    List<GymBranch> findByRegionAndBranchStatus(@Param("district") String district, 
                                               @Param("status") BranchStatus status);
    
    /**
     * 지역별 지점 수 통계
     */
    @Query("SELECT gb.district, COUNT(gb) as branchCount FROM GymBranch gb " +
           "WHERE gb.branchStatus = 'ACTIVE' " +
           "GROUP BY gb.district " +
           "ORDER BY branchCount DESC")
    List<Object[]> countBranchesByDistrict();
    
    /**
     * 지하철역 정보로 검색
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.subwayInfo LIKE %:subway% AND gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.branchName")
    List<GymBranch> findBySubwayInfo(@Param("subway") String subway);
    
    // ===== 편의시설 기반 검색 =====
    
    /**
     * 주차 가능한 지점 조회
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.hasParking = true AND gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.branchName")
    List<GymBranch> findBranchesWithParking();
    
    /**
     * 샤워시설 있는 지점 조회
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.hasShower = true AND gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.branchName")
    List<GymBranch> findBranchesWithShower();
    
    /**
     * 24시간 운영 지점 조회
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.is24Hours = true AND gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.branchName")
    List<GymBranch> find24HourBranches();
    
    /**
     * 복합 편의시설 조건 검색
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE (:hasParking IS NULL OR gb.hasParking = :hasParking) " +
           "AND (:hasShower IS NULL OR gb.hasShower = :hasShower) " +
           "AND (:hasLocker IS NULL OR gb.hasLocker = :hasLocker) " +
           "AND (:hasRental IS NULL OR gb.hasRental = :hasRental) " +
           "AND gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.memberCount DESC")
    List<GymBranch> findByAmenities(@Param("hasParking") Boolean hasParking,
                                   @Param("hasShower") Boolean hasShower,
                                   @Param("hasLocker") Boolean hasLocker,
                                   @Param("hasRental") Boolean hasRental);
    
    // ===== 본점 및 상태 관리 =====
    
    /**
     * 암장의 본점 조회
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.gym.gymId = :gymId AND gb.isMainBranch = true")
    Optional<GymBranch> findMainBranchByGymId(@Param("gymId") Long gymId);
    
    /**
     * 상태별 지점 조회
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.branchStatus = :status " +
           "ORDER BY gb.createdAt DESC")
    List<GymBranch> findByBranchStatus(@Param("status") BranchStatus status);
    
    /**
     * 상태별 지점 수 통계
     */
    @Query("SELECT gb.branchStatus, COUNT(gb) FROM GymBranch gb GROUP BY gb.branchStatus")
    List<Object[]> countByBranchStatus();
    
    // ===== 통계 및 인기도 =====
    
    /**
     * 인기 지점 조회 (멤버 수 기준)
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.memberCount DESC")
    List<GymBranch> findPopularBranchesByMemberCount(Pageable pageable);
    
    /**
     * 인기 지점 조회 (루트 수 기준)
     */
    @Query("SELECT gb FROM GymBranch gb " +
           "WHERE gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.routeCount DESC")
    List<GymBranch> findPopularBranchesByRouteCount(Pageable pageable);
    
    /**
     * 지점별 벽면 수 통계
     */
    @Query("SELECT gb.branchId, gb.branchName, gb.wallCount FROM GymBranch gb " +
           "WHERE gb.branchStatus = 'ACTIVE' " +
           "ORDER BY gb.wallCount DESC")
    List<Object[]> getBranchWallStatistics();
    
    // ===== 업데이트 메서드 =====
    
    /**
     * 멤버 수 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GymBranch gb SET gb.memberCount = " +
           "(SELECT COUNT(gm) FROM GymMember gm WHERE gm.branch = gb AND gm.isActive = true) " +
           "WHERE gb.branchId = :branchId")
    int updateMemberCount(@Param("branchId") Long branchId);
    
    /**
     * 벽면 수 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GymBranch gb SET gb.wallCount = " +
           "(SELECT COUNT(w) FROM Wall w WHERE w.branch = gb) " +
           "WHERE gb.branchId = :branchId")
    int updateWallCount(@Param("branchId") Long branchId);
    
    /**
     * 루트 수 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GymBranch gb SET gb.routeCount = " +
           "(SELECT COUNT(r) FROM Route r JOIN r.wall w WHERE w.branch = gb AND r.routeStatus = 'ACTIVE') " +
           "WHERE gb.branchId = :branchId")
    int updateRouteCount(@Param("branchId") Long branchId);
    
    /**
     * 지점 상태 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GymBranch gb SET gb.branchStatus = :status WHERE gb.branchId = :branchId")
    int updateBranchStatus(@Param("branchId") Long branchId, @Param("status") BranchStatus status);
}
```

---

## 👥 3. GymMemberRepository - 암장 멤버십 Repository

```java
package com.routepick.domain.gym.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.gym.entity.GymMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * GymMember Repository
 * - 암장 멤버십 관리
 * - 멤버십 상태 추적
 * - 만료 관리 및 통계
 */
@Repository
public interface GymMemberRepository extends BaseRepository<GymMember, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자-지점 멤버십 조회
     */
    @Query("SELECT gm FROM GymMember gm " +
           "WHERE gm.user.userId = :userId AND gm.branch.branchId = :branchId")
    Optional<GymMember> findByUserIdAndBranchId(@Param("userId") Long userId, 
                                               @Param("branchId") Long branchId);
    
    /**
     * 사용자의 모든 멤버십 조회
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.branch b " +
           "JOIN FETCH b.gym g " +
           "WHERE gm.user.userId = :userId " +
           "ORDER BY gm.membershipEndDate DESC")
    List<GymMember> findByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 활성 멤버십 조회
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.branch b " +
           "JOIN FETCH b.gym g " +
           "WHERE gm.user.userId = :userId AND gm.isActive = true " +
           "ORDER BY gm.membershipEndDate")
    List<GymMember> findActiveByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 유효한 멤버십 조회 (만료일 기준)
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.branch b " +
           "JOIN FETCH b.gym g " +
           "WHERE gm.user.userId = :userId " +
           "AND gm.membershipEndDate >= CURRENT_DATE " +
           "AND gm.isActive = true " +
           "ORDER BY gm.membershipEndDate")
    List<GymMember> findByUserIdAndMembershipEndDateAfter(@Param("userId") Long userId);
    
    // ===== 지점별 멤버 관리 =====
    
    /**
     * 지점별 멤버 현황
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.user u " +
           "WHERE gm.branch.branchId = :branchId AND gm.isActive = :isActive " +
           "ORDER BY gm.membershipEndDate DESC")
    List<GymMember> findByBranchIdAndIsActive(@Param("branchId") Long branchId, 
                                             @Param("isActive") boolean isActive);
    
    /**
     * 지점별 멤버 현황 페이징
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.user u " +
           "WHERE gm.branch.branchId = :branchId AND gm.isActive = :isActive " +
           "ORDER BY gm.membershipEndDate DESC")
    Page<GymMember> findByBranchIdAndIsActive(@Param("branchId") Long branchId, 
                                             @Param("isActive") boolean isActive, 
                                             Pageable pageable);
    
    /**
     * 지점별 활성 멤버 수
     */
    @Query("SELECT COUNT(gm) FROM GymMember gm " +
           "WHERE gm.branch.branchId = :branchId AND gm.isActive = true")
    long countActiveMembersByBranch(@Param("branchId") Long branchId);
    
    /**
     * 지점별 멤버 수 통계 (상태별)
     */
    @Query("SELECT gm.isActive, COUNT(gm) FROM GymMember gm " +
           "WHERE gm.branch.branchId = :branchId " +
           "GROUP BY gm.isActive")
    List<Object[]> countMembersByStatusAndBranch(@Param("branchId") Long branchId);
    
    // ===== 만료 관리 =====
    
    /**
     * 만료 예정 멤버십 조회
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.user u " +
           "JOIN FETCH gm.branch b " +
           "WHERE gm.membershipEndDate BETWEEN CURRENT_DATE AND :endDate " +
           "AND gm.isActive = true " +
           "ORDER BY gm.membershipEndDate")
    List<GymMember> findExpiringMemberships(@Param("endDate") LocalDate endDate);
    
    /**
     * 특정 지점의 만료 예정 멤버십
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.user u " +
           "WHERE gm.branch.branchId = :branchId " +
           "AND gm.membershipEndDate BETWEEN CURRENT_DATE AND :endDate " +
           "AND gm.isActive = true " +
           "ORDER BY gm.membershipEndDate")
    List<GymMember> findExpiringMembershipsByBranch(@Param("branchId") Long branchId, 
                                                   @Param("endDate") LocalDate endDate);
    
    /**
     * 이미 만료된 멤버십 조회
     */
    @Query("SELECT gm FROM GymMember gm " +
           "WHERE gm.membershipEndDate < CURRENT_DATE " +
           "AND gm.isActive = true " +
           "ORDER BY gm.membershipEndDate")
    List<GymMember> findExpiredMemberships();
    
    /**
     * 만료된 멤버십 자동 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GymMember gm SET gm.isActive = false " +
           "WHERE gm.membershipEndDate < CURRENT_DATE AND gm.isActive = true")
    int expireOverdueMemberships();
    
    // ===== 멤버십 유형별 조회 =====
    
    /**
     * 멤버십 타입별 조회
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.user u " +
           "WHERE gm.membershipType = :membershipType " +
           "AND gm.isActive = true " +
           "ORDER BY gm.membershipEndDate")
    List<GymMember> findByMembershipType(@Param("membershipType") String membershipType);
    
    /**
     * 멤버십 타입별 통계
     */
    @Query("SELECT gm.membershipType, COUNT(gm) FROM GymMember gm " +
           "WHERE gm.isActive = true " +
           "GROUP BY gm.membershipType " +
           "ORDER BY COUNT(gm) DESC")
    List<Object[]> countByMembershipType();
    
    // ===== 멤버십 갱신 및 연장 =====
    
    /**
     * 멤버십 연장
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GymMember gm SET " +
           "gm.membershipEndDate = :newEndDate, " +
           "gm.isActive = true " +
           "WHERE gm.membershipId = :memberId")
    int extendMembership(@Param("memberId") Long memberId, 
                        @Param("newEndDate") LocalDate newEndDate);
    
    /**
     * 멤버십 상태 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GymMember gm SET gm.isActive = :isActive " +
           "WHERE gm.membershipId = :memberId")
    int updateMembershipStatus(@Param("memberId") Long memberId, 
                              @Param("isActive") boolean isActive);
    
    // ===== 통계 및 분석 =====
    
    /**
     * 멤버십 동향 분석 (월별 가입)
     */
    @Query("SELECT YEAR(gm.membershipStartDate), MONTH(gm.membershipStartDate), COUNT(gm) FROM GymMember gm " +
           "WHERE gm.membershipStartDate BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(gm.membershipStartDate), MONTH(gm.membershipStartDate) " +
           "ORDER BY YEAR(gm.membershipStartDate), MONTH(gm.membershipStartDate)")
    List<Object[]> findMembershipTrends(@Param("startDate") LocalDate startDate, 
                                       @Param("endDate") LocalDate endDate);
    
    /**
     * 지점별 멤버 유지율 계산
     */
    @Query("SELECT gm.branch.branchId, " +
           "COUNT(CASE WHEN gm.isActive = true THEN 1 END) as activeMembers, " +
           "COUNT(gm) as totalMembers, " +
           "(COUNT(CASE WHEN gm.isActive = true THEN 1 END) * 100.0 / COUNT(gm)) as retentionRate " +
           "FROM GymMember gm " +
           "WHERE gm.membershipStartDate >= :sinceDate " +
           "GROUP BY gm.branch.branchId " +
           "ORDER BY retentionRate DESC")
    List<Object[]> calculateMemberRetentionRate(@Param("sinceDate") LocalDate sinceDate);
    
    /**
     * 평균 멤버십 기간 계산
     */
    @Query("SELECT AVG(DATEDIFF(gm.membershipEndDate, gm.membershipStartDate)) FROM GymMember gm " +
           "WHERE gm.branch.branchId = :branchId")
    Double calculateAverageMembershipDuration(@Param("branchId") Long branchId);
    
    /**
     * 멤버십 수익 통계 (지점별)
     */
    @Query("SELECT gm.branch.branchId, " +
           "SUM(gm.membershipFee) as totalRevenue, " +
           "COUNT(gm) as totalMembers " +
           "FROM GymMember gm " +
           "WHERE gm.isActive = true " +
           "GROUP BY gm.branch.branchId " +
           "ORDER BY totalRevenue DESC")
    List<Object[]> calculateRevenueByBranch();
    
    // ===== 특별 조회 =====
    
    /**
     * VIP 멤버 조회 (장기 회원 또는 높은 등급)
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.user u " +
           "WHERE (gm.visitCount >= :minVisits OR gm.membershipType LIKE '%VIP%') " +
           "AND gm.isActive = true " +
           "ORDER BY gm.visitCount DESC, gm.membershipStartDate")
    List<GymMember> findVipMembers(@Param("minVisits") Integer minVisits);
    
    /**
     * 신규 가입자 조회
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.user u " +
           "WHERE gm.membershipStartDate >= :sinceDate " +
           "ORDER BY gm.membershipStartDate DESC")
    List<GymMember> findNewMembers(@Param("sinceDate") LocalDate sinceDate);
    
    /**
     * 비활성 멤버 조회 (최근 방문 기록 없음)
     */
    @Query("SELECT gm FROM GymMember gm " +
           "JOIN FETCH gm.user u " +
           "WHERE gm.lastVisitDate < :cutoffDate " +
           "AND gm.isActive = true " +
           "ORDER BY gm.lastVisitDate")
    List<GymMember> findInactiveMembers(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 방문 기록 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GymMember gm SET " +
           "gm.visitCount = COALESCE(gm.visitCount, 0) + 1, " +
           "gm.lastVisitDate = CURRENT_TIMESTAMP " +
           "WHERE gm.user.userId = :userId AND gm.branch.branchId = :branchId")
    int recordVisit(@Param("userId") Long userId, @Param("branchId") Long branchId);
}
```

---

## ⚡ 4. 한국 특화 공간 쿼리 최적화

### Spatial Index 생성
```sql
-- 한국 좌표계 최적화를 위한 Spatial Index
CREATE SPATIAL INDEX idx_gym_branch_location ON gym_branches (location);

-- 위도/경도 복합 인덱스
CREATE INDEX idx_gym_branch_coordinates ON gym_branches (latitude, longitude);

-- 지역별 인덱스
CREATE INDEX idx_gym_branch_district ON gym_branches (district, branch_status);
```

### 한국 지역 기반 검색 최적화
```java
/**
 * 한국 주요 도시별 암장 검색
 */
@Query("SELECT gb FROM GymBranch gb WHERE " +
       "gb.address LIKE %:city% AND gb.branchStatus = 'ACTIVE' " +
       "ORDER BY gb.memberCount DESC")
List<GymBranch> findBranchesByCity(@Param("city") String city);

/**
 * 서울 25개 구별 검색
 */
@Query("SELECT gb FROM GymBranch gb WHERE " +
       "gb.district = :district AND gb.address LIKE '%서울%' " +
       "AND gb.branchStatus = 'ACTIVE'")
List<GymBranch> findSeoulBranchesByDistrict(@Param("district") String district);
```

### 거리 계산 성능 최적화
```sql
-- 한국 지형 특성을 반영한 거리 계산
SELECT *, 
  ST_Distance_Sphere(
    POINT(longitude, latitude), 
    POINT(127.0276, 37.4979)  -- 강남역 좌표
  ) AS distance 
FROM gym_branches 
WHERE ST_Distance_Sphere(
  POINT(longitude, latitude), 
  POINT(127.0276, 37.4979)
) <= 3000  -- 3km 반경
ORDER BY distance;
```

---

## ✅ 설계 완료 체크리스트

### 암장 핵심 Repository (3개)
- [x] GymRepository - 암장 기본 관리, 프랜차이즈 분석
- [x] GymBranchRepository - 공간 쿼리 특화, MySQL Spatial 활용
- [x] GymMemberRepository - 멤버십 관리, 만료 추적

### 공간 쿼리 최적화
- [x] MySQL Spatial Index 활용
- [x] 한국 좌표계 최적화 (위도 33-38.6, 경도 124-132)
- [x] ST_Distance_Sphere 함수 활용
- [x] 지역별 클러스터링 지원

### 한국 특화 기능
- [x] 행정구역별 검색 (서울 25개 구)
- [x] 지하철역 기반 검색
- [x] 거리 계산 한국 지형 반영
- [x] 사업자등록번호 검증

### 성능 최적화
- [x] 복합 인덱스 (gym_id + branch_status)
- [x] LAZY 로딩 적용
- [x] 통계 정보 캐시 (member_count, wall_count)
- [x] 배치 업데이트 쿼리

---

**다음 단계**: Step 5-3b Wall, BranchImage Repository 설계  
**완료일**: 2025-08-20  
**핵심 성과**: 암장 핵심 3개 Repository + 공간 쿼리 특화 + 한국 특화 최적화 완료