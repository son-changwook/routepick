# Step 5-3a-2: 암장 부가 Repository 생성

> 벽면 및 이미지 관리 2개 Repository 완전 설계  
> 생성일: 2025-08-20  
> 기반: step5-3a_gym_core_repositories.md, step4-3a_gym_entities.md

---

## 🎯 설계 목표

- **벽면 통계 및 분석**: 높이, 각도, 타입별 분류 및 통계
- **이미지 관리 최적화**: 순서 관리, 썸네일 일괄 조회
- **루트 배치 최적화**: 벽면별 루트 수용량 관리
- **CDN 이미지 최적화**: 타입별 이미지 관리 및 성능 최적화

---

## 🧗‍♀️ 1. WallRepository - 벽면 관리 Repository

```java
package com.routepick.domain.gym.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.gym.entity.Wall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Wall Repository
 * - 벽면 관리 및 통계
 * - 벽면 특성 분석 (높이, 각도, 타입)
 * - 루트 배치 최적화 지원
 */
@Repository
public interface WallRepository extends BaseRepository<Wall, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 지점별 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.branch.branchId = :branchId AND w.isActive = true " +
           "ORDER BY w.wallName")
    List<Wall> findByBranchIdAndActive(@Param("branchId") Long branchId);
    
    /**
     * 지점별 벽면 조회 (페이징)
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.branch.branchId = :branchId AND w.isActive = true " +
           "ORDER BY w.wallName")
    Page<Wall> findByBranchIdAndActive(@Param("branchId") Long branchId, Pageable pageable);
    
    /**
     * 지점별 벽면 타입 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.branch.branchId = :branchId AND w.wallType = :wallType AND w.isActive = true " +
           "ORDER BY w.wallName")
    List<Wall> findByBranchIdAndWallType(@Param("branchId") Long branchId, 
                                        @Param("wallType") String wallType);
    
    /**
     * 벽면명으로 검색
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.wallName LIKE %:keyword% AND w.isActive = true " +
           "ORDER BY w.wallName")
    List<Wall> findByWallNameContaining(@Param("keyword") String keyword);
    
    // ===== 벽면 특성별 조회 =====
    
    /**
     * 높이 범위별 벽면 검색
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.height BETWEEN :minHeight AND :maxHeight AND w.isActive = true " +
           "ORDER BY w.height")
    List<Wall> findByHeightBetween(@Param("minHeight") Float minHeight, 
                                  @Param("maxHeight") Float maxHeight);
    
    /**
     * 각도 범위별 벽면 검색
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.wallAngle BETWEEN :minAngle AND :maxAngle AND w.isActive = true " +
           "ORDER BY w.wallAngle")
    List<Wall> findByAngleBetween(@Param("minAngle") Integer minAngle, 
                                 @Param("maxAngle") Integer maxAngle);
    
    /**
     * 벽면 타입별 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.wallType = :wallType AND w.isActive = true " +
           "ORDER BY w.height DESC")
    List<Wall> findByWallType(@Param("wallType") String wallType);
    
    /**
     * 홀드 브랜드별 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.holdBrand = :holdBrand AND w.isActive = true " +
           "ORDER BY w.branch.branchName, w.wallName")
    List<Wall> findByHoldBrand(@Param("holdBrand") String holdBrand);
    
    /**
     * 표면 재질별 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.surfaceMaterial = :material AND w.isActive = true " +
           "ORDER BY w.wallName")
    List<Wall> findBySurfaceMaterial(@Param("material") String material);
    
    // ===== 벽면 타입별 통계 =====
    
    /**
     * 지점별 벽면 타입 통계
     */
    @Query("SELECT w.wallType, COUNT(w) as wallCount FROM Wall w " +
           "WHERE w.branch.branchId = :branchId AND w.isActive = true " +
           "GROUP BY w.wallType " +
           "ORDER BY wallCount DESC")
    List<Object[]> countByBranchIdAndWallType(@Param("branchId") Long branchId);
    
    /**
     * 전체 벽면 타입 분포
     */
    @Query("SELECT w.wallType, COUNT(w) as wallCount, AVG(w.height) as avgHeight FROM Wall w " +
           "WHERE w.isActive = true " +
           "GROUP BY w.wallType " +
           "ORDER BY wallCount DESC")
    List<Object[]> getWallTypeDistribution();
    
    /**
     * 홀드 브랜드별 통계
     */
    @Query("SELECT w.holdBrand, COUNT(w) as wallCount FROM Wall w " +
           "WHERE w.isActive = true AND w.holdBrand IS NOT NULL " +
           "GROUP BY w.holdBrand " +
           "ORDER BY wallCount DESC")
    List<Object[]> countByHoldBrand();
    
    /**
     * 높이별 벽면 분포
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN w.height < 3 THEN '낮음(3m 미만)' " +
           "  WHEN w.height < 5 THEN '보통(3-5m)' " +
           "  WHEN w.height < 8 THEN '높음(5-8m)' " +
           "  ELSE '매우높음(8m 이상)' " +
           "END as heightRange, " +
           "COUNT(w) as wallCount " +
           "FROM Wall w " +
           "WHERE w.isActive = true AND w.height IS NOT NULL " +
           "GROUP BY " +
           "CASE " +
           "  WHEN w.height < 3 THEN '낮음(3m 미만)' " +
           "  WHEN w.height < 5 THEN '보통(3-5m)' " +
           "  WHEN w.height < 8 THEN '높음(5-8m)' " +
           "  ELSE '매우높음(8m 이상)' " +
           "END " +
           "ORDER BY wallCount DESC")
    List<Object[]> getHeightDistribution();
    
    /**
     * 각도별 벽면 분포 (클라이밍 타입)
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN w.wallAngle < 85 THEN '슬랩' " +
           "  WHEN w.wallAngle <= 95 THEN '수직벽' " +
           "  WHEN w.wallAngle <= 135 THEN '오버행' " +
           "  ELSE '루프' " +
           "END as climbingType, " +
           "COUNT(w) as wallCount, " +
           "AVG(w.routeCount) as avgRoutes " +
           "FROM Wall w " +
           "WHERE w.isActive = true AND w.wallAngle IS NOT NULL " +
           "GROUP BY " +
           "CASE " +
           "  WHEN w.wallAngle < 85 THEN '슬랩' " +
           "  WHEN w.wallAngle <= 95 THEN '수직벽' " +
           "  WHEN w.wallAngle <= 135 THEN '오버행' " +
           "  ELSE '루프' " +
           "END " +
           "ORDER BY wallCount DESC")
    List<Object[]> getAngleDistribution();
    
    // ===== 루트 관리 및 통계 =====
    
    /**
     * 루트 추가 가능한 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.branch.branchId = :branchId AND w.isActive = true " +
           "AND w.routeCount < w.maxRouteCapacity " +
           "ORDER BY (w.maxRouteCapacity - w.routeCount) DESC")
    List<Wall> findAvailableWallsForRoutes(@Param("branchId") Long branchId);
    
    /**
     * 루트 수용량 초과 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.routeCount >= w.maxRouteCapacity AND w.isActive = true " +
           "ORDER BY w.routeCount DESC")
    List<Wall> findOverCapacityWalls();
    
    /**
     * 인기 벽면 조회 (루트 수 기준)
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.isActive = true " +
           "ORDER BY w.routeCount DESC")
    List<Wall> findPopularWallsByRouteCount(Pageable pageable);
    
    /**
     * 벽면 이용률 통계
     */
    @Query("SELECT w.wallId, w.wallName, w.routeCount, w.maxRouteCapacity, " +
           "(w.routeCount * 100.0 / w.maxRouteCapacity) as utilizationRate " +
           "FROM Wall w " +
           "WHERE w.isActive = true AND w.maxRouteCapacity > 0 " +
           "ORDER BY utilizationRate DESC")
    List<Object[]> findWallUtilizationStats();
    
    /**
     * 지점별 벽면 이용률 요약
     */
    @Query("SELECT w.branch.branchId, w.branch.branchName, " +
           "COUNT(w) as totalWalls, " +
           "SUM(w.routeCount) as totalRoutes, " +
           "SUM(w.maxRouteCapacity) as totalCapacity, " +
           "(SUM(w.routeCount) * 100.0 / SUM(w.maxRouteCapacity)) as avgUtilization " +
           "FROM Wall w " +
           "WHERE w.isActive = true AND w.maxRouteCapacity > 0 " +
           "GROUP BY w.branch.branchId, w.branch.branchName " +
           "ORDER BY avgUtilization DESC")
    List<Object[]> getBranchWallUtilizationSummary();
    
    // ===== 리셋 관리 =====
    
    /**
     * 리셋 필요한 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.isActive = true " +
           "AND w.lastResetDate IS NOT NULL " +
           "AND w.resetCycleWeeks IS NOT NULL " +
           "AND DATE_ADD(w.lastResetDate, INTERVAL w.resetCycleWeeks WEEK) <= CURRENT_DATE " +
           "ORDER BY w.lastResetDate")
    List<Wall> findWallsNeedingReset();
    
    /**
     * 특정 지점에서 리셋 필요한 벽면
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.branch.branchId = :branchId AND w.isActive = true " +
           "AND w.lastResetDate IS NOT NULL " +
           "AND w.resetCycleWeeks IS NOT NULL " +
           "AND DATE_ADD(w.lastResetDate, INTERVAL w.resetCycleWeeks WEEK) <= CURRENT_DATE " +
           "ORDER BY w.lastResetDate")
    List<Wall> findBranchWallsNeedingReset(@Param("branchId") Long branchId);
    
    /**
     * 최근 리셋된 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.lastResetDate >= :sinceDate AND w.isActive = true " +
           "ORDER BY w.lastResetDate DESC")
    List<Wall> findRecentlyResetWalls(@Param("sinceDate") LocalDate sinceDate);
    
    // ===== 대회용 벽면 관리 =====
    
    /**
     * 대회용 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.isCompetitionWall = true AND w.isActive = true " +
           "ORDER BY w.branch.branchName, w.wallName")
    List<Wall> findCompetitionWalls();
    
    /**
     * 지점별 대회용 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.branch.branchId = :branchId AND w.isCompetitionWall = true AND w.isActive = true " +
           "ORDER BY w.wallName")
    List<Wall> findCompetitionWallsByBranch(@Param("branchId") Long branchId);
    
    // ===== 업데이트 메서드 =====
    
    /**
     * 루트 수 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Wall w SET w.routeCount = " +
           "(SELECT COUNT(r) FROM Route r WHERE r.wall = w AND r.routeStatus = 'ACTIVE') " +
           "WHERE w.wallId = :wallId")
    int updateRouteCount(@Param("wallId") Long wallId);
    
    /**
     * 모든 벽면의 루트 수 일괄 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Wall w SET w.routeCount = " +
           "(SELECT COUNT(r) FROM Route r WHERE r.wall = w AND r.routeStatus = 'ACTIVE')")
    int updateAllRouteCount();
    
    /**
     * 벽면 리셋 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Wall w SET " +
           "w.lastResetDate = CURRENT_DATE, " +
           "w.routeCount = 0 " +
           "WHERE w.wallId = :wallId")
    int resetWall(@Param("wallId") Long wallId);
    
    /**
     * 벽면 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Wall w SET w.isActive = false WHERE w.wallId = :wallId")
    int deactivateWall(@Param("wallId") Long wallId);
    
    /**
     * 벽면 재활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Wall w SET w.isActive = true WHERE w.wallId = :wallId")
    int reactivateWall(@Param("wallId") Long wallId);
    
    /**
     * 최대 루트 수용량 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Wall w SET w.maxRouteCapacity = :capacity WHERE w.wallId = :wallId")
    int updateMaxRouteCapacity(@Param("wallId") Long wallId, @Param("capacity") Integer capacity);
    
    // ===== 복합 조건 검색 =====
    
    /**
     * 복합 조건 벽면 검색
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE (:branchId IS NULL OR w.branch.branchId = :branchId) " +
           "AND (:wallType IS NULL OR w.wallType = :wallType) " +
           "AND (:minHeight IS NULL OR w.height >= :minHeight) " +
           "AND (:maxHeight IS NULL OR w.height <= :maxHeight) " +
           "AND (:minAngle IS NULL OR w.wallAngle >= :minAngle) " +
           "AND (:maxAngle IS NULL OR w.wallAngle <= :maxAngle) " +
           "AND (:holdBrand IS NULL OR w.holdBrand = :holdBrand) " +
           "AND w.isActive = true " +
           "ORDER BY w.branch.branchName, w.wallName")
    Page<Wall> findByComplexConditions(@Param("branchId") Long branchId,
                                      @Param("wallType") String wallType,
                                      @Param("minHeight") Float minHeight,
                                      @Param("maxHeight") Float maxHeight,
                                      @Param("minAngle") Integer minAngle,
                                      @Param("maxAngle") Integer maxAngle,
                                      @Param("holdBrand") String holdBrand,
                                      Pageable pageable);
    
    // ===== 관리자용 통계 =====
    
    /**
     * 전체 벽면 통계 요약
     */
    @Query("SELECT " +
           "COUNT(w) as totalWalls, " +
           "COUNT(CASE WHEN w.isActive = true THEN 1 END) as activeWalls, " +
           "AVG(w.height) as avgHeight, " +
           "AVG(w.routeCount) as avgRouteCount, " +
           "SUM(w.routeCount) as totalRoutes " +
           "FROM Wall w")
    List<Object[]> getWallStatisticsSummary();
    
    /**
     * 최근 생성된 벽면 조회
     */
    @Query("SELECT w FROM Wall w " +
           "ORDER BY w.createdAt DESC")
    List<Wall> findRecentlyCreatedWalls(Pageable pageable);
    
    /**
     * 미사용 벽면 조회 (루트가 없는 벽면)
     */
    @Query("SELECT w FROM Wall w " +
           "WHERE w.routeCount = 0 AND w.isActive = true " +
           "ORDER BY w.createdAt DESC")
    List<Wall> findUnusedWalls();
}
```

---

## 🖼️ 2. BranchImageRepository - 지점 이미지 Repository

```java
package com.routepick.domain.gym.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.gym.entity.BranchImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * BranchImage Repository
 * - 지점 이미지 관리
 * - 표시 순서 관리
 * - 이미지 타입별 분류
 */
@Repository
public interface BranchImageRepository extends BaseRepository<BranchImage, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 지점별 이미지 정렬 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.branch.branchId = :branchId AND bi.isActive = true " +
           "ORDER BY bi.displayOrder ASC")
    List<BranchImage> findByBranchIdOrderByDisplayOrder(@Param("branchId") Long branchId);
    
    /**
     * 지점별 이미지 정렬 조회 (페이징)
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.branch.branchId = :branchId AND bi.isActive = true " +
           "ORDER BY bi.displayOrder ASC")
    Page<BranchImage> findByBranchIdOrderByDisplayOrder(@Param("branchId") Long branchId, 
                                                       Pageable pageable);
    
    /**
     * 지점별 이미지 타입별 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.branch.branchId = :branchId AND bi.imageType = :imageType AND bi.isActive = true " +
           "ORDER BY bi.displayOrder ASC")
    List<BranchImage> findByBranchIdAndImageType(@Param("branchId") Long branchId, 
                                                @Param("imageType") String imageType);
    
    /**
     * 지점의 대표 이미지 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.branch.branchId = :branchId AND bi.isMain = true AND bi.isActive = true " +
           "ORDER BY bi.displayOrder ASC")
    Optional<BranchImage> findMainImageByBranchId(@Param("branchId") Long branchId);
    
    // ===== 이미지 타입별 조회 =====
    
    /**
     * 이미지 타입별 조회 (전체 지점)
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.imageType = :imageType AND bi.isActive = true " +
           "ORDER BY bi.branch.branchName, bi.displayOrder")
    List<BranchImage> findByImageType(@Param("imageType") String imageType);
    
    /**
     * 이미지 타입별 통계
     */
    @Query("SELECT bi.imageType, COUNT(bi) as imageCount FROM BranchImage bi " +
           "WHERE bi.isActive = true " +
           "GROUP BY bi.imageType " +
           "ORDER BY imageCount DESC")
    List<Object[]> countByImageType();
    
    /**
     * 지점별 이미지 타입 통계
     */
    @Query("SELECT bi.imageType, COUNT(bi) as imageCount FROM BranchImage bi " +
           "WHERE bi.branch.branchId = :branchId AND bi.isActive = true " +
           "GROUP BY bi.imageType " +
           "ORDER BY imageCount DESC")
    List<Object[]> countByBranchIdAndImageType(@Param("branchId") Long branchId);
    
    // ===== 썸네일 및 최적화 =====
    
    /**
     * 썸네일 일괄 조회 (여러 지점)
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.branch.branchId IN :branchIds " +
           "AND (bi.isMain = true OR bi.displayOrder = 1) " +
           "AND bi.isActive = true " +
           "ORDER BY bi.branch.branchId, bi.displayOrder")
    List<BranchImage> findThumbnailsByBranchIds(@Param("branchIds") List<Long> branchIds);
    
    /**
     * 대표 이미지 일괄 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.isMain = true AND bi.isActive = true " +
           "ORDER BY bi.branch.branchName")
    List<BranchImage> findAllMainImages();
    
    /**
     * 특정 타입의 첫 번째 이미지들 조회 (지점별)
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.imageType = :imageType AND bi.displayOrder = 1 AND bi.isActive = true " +
           "ORDER BY bi.branch.branchName")
    List<BranchImage> findFirstImagesByType(@Param("imageType") String imageType);
    
    // ===== 표시 순서 관리 =====
    
    /**
     * 지점별 최대 표시 순서 조회
     */
    @Query("SELECT COALESCE(MAX(bi.displayOrder), 0) FROM BranchImage bi " +
           "WHERE bi.branch.branchId = :branchId AND bi.isActive = true")
    Integer findMaxDisplayOrderByBranchId(@Param("branchId") Long branchId);
    
    /**
     * 특정 순서 이후 이미지들 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.branch.branchId = :branchId AND bi.displayOrder > :displayOrder AND bi.isActive = true " +
           "ORDER BY bi.displayOrder")
    List<BranchImage> findByBranchIdAndDisplayOrderGreaterThan(@Param("branchId") Long branchId, 
                                                              @Param("displayOrder") Integer displayOrder);
    
    /**
     * 표시 순서 변경 (일괄 업데이트)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BranchImage bi SET bi.displayOrder = :newOrder " +
           "WHERE bi.imageId = :imageId")
    int updateDisplayOrder(@Param("imageId") Long imageId, @Param("newOrder") Integer newOrder);
    
    /**
     * 표시 순서 뒤로 밀기 (삽입 시)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BranchImage bi SET bi.displayOrder = bi.displayOrder + 1 " +
           "WHERE bi.branch.branchId = :branchId AND bi.displayOrder >= :fromOrder AND bi.isActive = true")
    int shiftDisplayOrdersUp(@Param("branchId") Long branchId, @Param("fromOrder") Integer fromOrder);
    
    /**
     * 표시 순서 앞으로 당기기 (삭제 시)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BranchImage bi SET bi.displayOrder = bi.displayOrder - 1 " +
           "WHERE bi.branch.branchId = :branchId AND bi.displayOrder > :fromOrder AND bi.isActive = true")
    int shiftDisplayOrdersDown(@Param("branchId") Long branchId, @Param("fromOrder") Integer fromOrder);
    
    // ===== 대표 이미지 관리 =====
    
    /**
     * 기존 대표 이미지 해제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BranchImage bi SET bi.isMain = false " +
           "WHERE bi.branch.branchId = :branchId")
    int clearMainImages(@Param("branchId") Long branchId);
    
    /**
     * 대표 이미지 설정
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BranchImage bi SET bi.isMain = true, bi.displayOrder = 1 " +
           "WHERE bi.imageId = :imageId")
    int setAsMainImage(@Param("imageId") Long imageId);
    
    // ===== 파일 크기 및 성능 관리 =====
    
    /**
     * 대용량 이미지 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.fileSize > :sizeLimit AND bi.isActive = true " +
           "ORDER BY bi.fileSize DESC")
    List<BranchImage> findLargeImages(@Param("sizeLimit") Long sizeLimit);
    
    /**
     * 파일 크기별 통계
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN bi.fileSize < 100000 THEN '100KB 미만' " +
           "  WHEN bi.fileSize < 500000 THEN '100KB-500KB' " +
           "  WHEN bi.fileSize < 1000000 THEN '500KB-1MB' " +
           "  ELSE '1MB 이상' " +
           "END as sizeRange, " +
           "COUNT(bi) as imageCount " +
           "FROM BranchImage bi " +
           "WHERE bi.isActive = true AND bi.fileSize IS NOT NULL " +
           "GROUP BY " +
           "CASE " +
           "  WHEN bi.fileSize < 100000 THEN '100KB 미만' " +
           "  WHEN bi.fileSize < 500000 THEN '100KB-500KB' " +
           "  WHEN bi.fileSize < 1000000 THEN '500KB-1MB' " +
           "  ELSE '1MB 이상' " +
           "END " +
           "ORDER BY imageCount DESC")
    List<Object[]> getFileSizeDistribution();
    
    /**
     * MIME 타입별 통계
     */
    @Query("SELECT bi.mimeType, COUNT(bi) as imageCount FROM BranchImage bi " +
           "WHERE bi.isActive = true AND bi.mimeType IS NOT NULL " +
           "GROUP BY bi.mimeType " +
           "ORDER BY imageCount DESC")
    List<Object[]> countByMimeType();
    
    // ===== 조회수 및 인기도 =====
    
    /**
     * 조회수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BranchImage bi SET bi.viewCount = COALESCE(bi.viewCount, 0) + 1 " +
           "WHERE bi.imageId = :imageId")
    int increaseViewCount(@Param("imageId") Long imageId);
    
    /**
     * 인기 이미지 조회 (조회수 기준)
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.isActive = true " +
           "ORDER BY bi.viewCount DESC")
    List<BranchImage> findPopularImages(Pageable pageable);
    
    /**
     * 지점별 인기 이미지 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.branch.branchId = :branchId AND bi.isActive = true " +
           "ORDER BY bi.viewCount DESC")
    List<BranchImage> findPopularImagesByBranch(@Param("branchId") Long branchId, Pageable pageable);
    
    // ===== 이미지 상태 관리 =====
    
    /**
     * 이미지 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BranchImage bi SET bi.isActive = false, bi.isMain = false " +
           "WHERE bi.imageId = :imageId")
    int deactivateImage(@Param("imageId") Long imageId);
    
    /**
     * 지점의 모든 이미지 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BranchImage bi SET bi.isActive = false, bi.isMain = false " +
           "WHERE bi.branch.branchId = :branchId")
    int deactivateAllImagesByBranch(@Param("branchId") Long branchId);
    
    /**
     * 특정 타입 이미지 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BranchImage bi SET bi.isActive = false " +
           "WHERE bi.branch.branchId = :branchId AND bi.imageType = :imageType")
    int deactivateImagesByType(@Param("branchId") Long branchId, @Param("imageType") String imageType);
    
    // ===== 업로드 관리 =====
    
    /**
     * 업로더별 이미지 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.uploaderId = :uploaderId AND bi.isActive = true " +
           "ORDER BY bi.createdAt DESC")
    List<BranchImage> findByUploaderId(@Param("uploaderId") Long uploaderId);
    
    /**
     * 업로드 IP별 이미지 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.uploadIp = :uploadIp AND bi.isActive = true " +
           "ORDER BY bi.createdAt DESC")
    List<BranchImage> findByUploadIp(@Param("uploadIp") String uploadIp);
    
    /**
     * 업로더별 이미지 통계
     */
    @Query("SELECT bi.uploaderId, COUNT(bi) as imageCount, SUM(bi.fileSize) as totalSize " +
           "FROM BranchImage bi " +
           "WHERE bi.isActive = true AND bi.uploaderId IS NOT NULL " +
           "GROUP BY bi.uploaderId " +
           "ORDER BY imageCount DESC")
    List<Object[]> getUploaderStatistics();
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 복합 조건 이미지 검색
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE (:branchId IS NULL OR bi.branch.branchId = :branchId) " +
           "AND (:imageType IS NULL OR bi.imageType = :imageType) " +
           "AND (:isMain IS NULL OR bi.isMain = :isMain) " +
           "AND (:keyword IS NULL OR bi.title LIKE %:keyword% OR bi.description LIKE %:keyword%) " +
           "AND bi.isActive = true " +
           "ORDER BY bi.branch.branchId, bi.displayOrder")
    Page<BranchImage> findByComplexConditions(@Param("branchId") Long branchId,
                                             @Param("imageType") String imageType,
                                             @Param("isMain") Boolean isMain,
                                             @Param("keyword") String keyword,
                                             Pageable pageable);
    
    /**
     * 제목으로 이미지 검색
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.title LIKE %:keyword% AND bi.isActive = true " +
           "ORDER BY bi.viewCount DESC")
    List<BranchImage> findByTitleContaining(@Param("keyword") String keyword);
    
    // ===== 관리자용 통계 =====
    
    /**
     * 전체 이미지 통계 요약
     */
    @Query("SELECT " +
           "COUNT(bi) as totalImages, " +
           "COUNT(CASE WHEN bi.isActive = true THEN 1 END) as activeImages, " +
           "COUNT(CASE WHEN bi.isMain = true THEN 1 END) as mainImages, " +
           "AVG(bi.fileSize) as avgFileSize, " +
           "SUM(bi.viewCount) as totalViews " +
           "FROM BranchImage bi")
    List<Object[]> getImageStatisticsSummary();
    
    /**
     * 지점별 이미지 수 통계
     */
    @Query("SELECT bi.branch.branchId, bi.branch.branchName, COUNT(bi) as imageCount " +
           "FROM BranchImage bi " +
           "WHERE bi.isActive = true " +
           "GROUP BY bi.branch.branchId, bi.branch.branchName " +
           "ORDER BY imageCount DESC")
    List<Object[]> countImagesByBranch();
    
    /**
     * 최근 업로드된 이미지 조회
     */
    @Query("SELECT bi FROM BranchImage bi " +
           "WHERE bi.isActive = true " +
           "ORDER BY bi.createdAt DESC")
    List<BranchImage> findRecentlyUploadedImages(Pageable pageable);
}
```

---

## ⚡ 3. 성능 최적화 전략

### 복합 인덱스 생성
```sql
-- 벽면 검색 최적화
CREATE INDEX idx_wall_branch_type_active ON walls (branch_id, wall_type, is_active);
CREATE INDEX idx_wall_height_angle ON walls (height, wall_angle);
CREATE INDEX idx_wall_route_capacity ON walls (route_count, max_route_capacity);

-- 이미지 관리 최적화
CREATE INDEX idx_branch_image_display ON branch_images (branch_id, display_order, is_active);
CREATE INDEX idx_branch_image_type_main ON branch_images (branch_id, image_type, is_main);
CREATE INDEX idx_branch_image_size ON branch_images (file_size DESC);
```

### 벽면 통계 캐싱
```java
@Cacheable(value = "wallStats", key = "#branchId")
public List<WallStatistics> getWallStatistics(Long branchId) {
    return wallRepository.countByBranchIdAndWallType(branchId);
}

@CacheEvict(value = "wallStats", key = "#branchId")
public void refreshWallStatistics(Long branchId) {
    // 벽면 통계 캐시 무효화
}
```

### 이미지 CDN 최적화
```java
/**
 * 이미지 URL 변환 (CDN, 썸네일)
 */
public String optimizeImageUrl(String originalUrl, String size) {
    // CloudFront, ImageKit 등 CDN 변환 로직
    return originalUrl.replace(".jpg", "_" + size + ".jpg");
}
```

---

## ✅ 설계 완료 체크리스트

### 암장 부가 Repository (2개)
- [x] WallRepository - 벽면 관리, 높이/각도 분석, 루트 배치 최적화
- [x] BranchImageRepository - 이미지 관리, 순서 관리, 썸네일 최적화

### 벽면 관리 특화 기능
- [x] 높이/각도 범위별 검색
- [x] 벽면 타입별 분류 (슬랩, 수직벽, 오버행, 루프)
- [x] 루트 수용량 관리 및 이용률 분석
- [x] 리셋 주기 관리 자동화

### 이미지 관리 최적화
- [x] 표시 순서 관리 (삽입/삭제 시 자동 정렬)
- [x] 이미지 타입별 분류 (MAIN, INTERIOR, WALL, FACILITY, EXTERIOR)
- [x] 썸네일 일괄 조회 최적화
- [x] 파일 크기별 통계 및 최적화

### 성능 최적화
- [x] 복합 인덱스 (branch_id + wall_type + is_active)
- [x] 이미지 캐싱 전략
- [x] CDN 통합 지원
- [x] 조회수 추적 및 인기도 분석

---

**다음 단계**: Step 5-3c Route 도메인 Repository 설계  
**완료일**: 2025-08-20  
**핵심 성과**: 벽면 관리 + 이미지 최적화 2개 Repository 완료