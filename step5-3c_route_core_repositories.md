# Step 5-3b: 루트 핵심 Repository 생성

> 클라이밍 루트 핵심 3개 Repository 완전 설계 (난이도 최적화 특화)  
> 생성일: 2025-08-20  
> 기반: step5-3a_gym_core_repositories.md, step4-3b_route_entities.md

---

## 🎯 설계 목표

- **난이도별 검색 성능 최적화**: V등급/5.등급 체계 지원
- **세터별 루트 관리 효율화**: 세터 성과 분석 및 관리
- **인기도 기반 정렬**: 조회수, 스크랩수, 완등률 복합 기준
- **고품질 검색 알고리즘**: 복합 조건 최적화

---

## 🧗‍♀️ 1. RouteRepository - 루트 핵심 검색 Repository

```java
package com.routepick.domain.route.repository;

import com.routepick.common.enums.RouteStatus;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.route.entity.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Route Repository
 * - 🎯 핵심 루트 검색 최적화
 * - 난이도별, 세터별, 벽면별 복합 검색
 * - 인기도 알고리즘 기반 정렬
 * - N+1 문제 완전 해결
 */
@Repository
public interface RouteRepository extends BaseRepository<Route, Long> {
    
    // ===== 기본 조회 메서드 (EntityGraph 최적화) =====
    
    /**
     * 루트 상세 조회 (연관 엔티티 포함)
     */
    @EntityGraph(attributePaths = {"routeSetter", "wall", "wall.branch", "wall.branch.gym", "climbingLevel"})
    @Query("SELECT r FROM Route r WHERE r.routeId = :routeId")
    Optional<Route> findByIdWithDetails(@Param("routeId") Long routeId);
    
    /**
     * 활성 루트 조회 (연관 엔티티 포함)
     */
    @EntityGraph(attributePaths = {"routeSetter", "wall", "wall.branch"})
    @Query("SELECT r FROM Route r WHERE r.routeStatus = 'ACTIVE'")
    List<Route> findActiveRoutesWithDetails(Pageable pageable);
    
    /**
     * 지점별 활성 루트 조회
     */
    @Query("SELECT r FROM Route r " +
           "JOIN r.wall w " +
           "WHERE w.branch.branchId = :branchId AND r.routeStatus = :status " +
           "ORDER BY r.createdAt DESC")
    List<Route> findByBranchAndRouteStatus(@Param("branchId") Long branchId, 
                                          @Param("status") RouteStatus status);
    
    /**
     * 지점 + 난이도 + 상태별 검색 (핵심 검색)
     */
    @EntityGraph(attributePaths = {"routeSetter", "climbingLevel"})
    @Query("SELECT r FROM Route r " +
           "JOIN r.wall w " +
           "WHERE w.branch.branchId = :branchId " +
           "AND r.climbingLevel.levelId = :levelId " +
           "AND r.routeStatus = :status " +
           "ORDER BY r.createdAt DESC")
    List<Route> findByBranchAndLevelAndRouteStatus(@Param("branchId") Long branchId,
                                                   @Param("levelId") Long levelId,
                                                   @Param("status") RouteStatus status);
    
    /**
     * 벽면별 활성 루트 조회
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.wall.wallId = :wallId AND r.routeStatus = :status " +
           "ORDER BY r.setDate DESC, r.createdAt DESC")
    List<Route> findByWallIdAndRouteStatus(@Param("wallId") Long wallId, 
                                          @Param("status") RouteStatus status);
    
    // ===== 🎯 인기도 기반 검색 (복합 기준 알고리즘) =====
    
    /**
     * 인기 루트 조회 - 복합 점수 기반
     * 가중치: 조회수(30%) + 스크랩수(40%) + 완등률(30%)
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.routeStatus = 'ACTIVE' " +
           "ORDER BY (r.viewCount * 0.3 + r.scrapCount * 0.4 + (r.climbCount * 100.0 / NULLIF(r.attemptCount, 0)) * 0.3) DESC")
    List<Route> findPopularRoutes(Pageable pageable);
    
    /**
     * 지점별 인기 루트 조회
     */
    @Query("SELECT r FROM Route r " +
           "JOIN r.wall w " +
           "WHERE w.branch.branchId = :branchId AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY (r.viewCount * 0.3 + r.scrapCount * 0.4 + (r.climbCount * 100.0 / NULLIF(r.attemptCount, 0)) * 0.3) DESC")
    List<Route> findPopularRoutesByBranch(@Param("branchId") Long branchId, Pageable pageable);
    
    /**
     * 난이도별 인기 루트 조회
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.climbingLevel.levelId = :levelId AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY (r.viewCount * 0.2 + r.scrapCount * 0.5 + r.climbCount * 0.3) DESC")
    List<Route> findPopularRoutesByLevel(@Param("levelId") Long levelId, Pageable pageable);
    
    /**
     * 추천 루트 조회 (추천 알고리즘)
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.routeStatus = 'ACTIVE' AND r.isFeatured = true " +
           "ORDER BY r.averageRating DESC, r.climbCount DESC")
    List<Route> findFeaturedRoutes(Pageable pageable);
    
    // ===== 세터별 루트 관리 =====
    
    /**
     * 세터별 루트 조회
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.routeSetter.setterId = :setterId AND r.routeStatus = :status " +
           "ORDER BY r.setDate DESC")
    List<Route> findBySetterAndRouteStatus(@Param("setterId") Long setterId, 
                                          @Param("status") RouteStatus status);
    
    /**
     * 세터별 기간별 루트 조회
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.routeSetter.setterId = :setterId " +
           "AND r.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY r.createdAt DESC")
    List<Route> findBySetterAndCreatedAtBetween(@Param("setterId") Long setterId,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * 세터별 루트 수 통계
     */
    @Query("SELECT rs.setterId, rs.setterName, COUNT(r) as routeCount FROM Route r " +
           "JOIN r.routeSetter rs " +
           "WHERE r.routeStatus = 'ACTIVE' " +
           "GROUP BY rs.setterId, rs.setterName " +
           "ORDER BY routeCount DESC")
    List<Object[]> countRoutesBySetters();
    
    // ===== 난이도별 검색 =====
    
    /**
     * 난이도 범위 검색
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.climbingLevel.difficultyScore BETWEEN :minScore AND :maxScore " +
           "AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.climbingLevel.difficultyScore, r.createdAt DESC")
    List<Route> findByDifficultyBetween(@Param("minScore") BigDecimal minScore, 
                                       @Param("maxScore") BigDecimal maxScore);
    
    /**
     * V등급 기준 검색
     */
    @Query("SELECT r FROM Route r " +
           "JOIN r.climbingLevel cl " +
           "WHERE cl.vGrade = :vGrade AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.setDate DESC")
    List<Route> findByVGrade(@Param("vGrade") String vGrade);
    
    /**
     * 5.등급 기준 검색
     */
    @Query("SELECT r FROM Route r " +
           "JOIN r.climbingLevel cl " +
           "WHERE cl.frenchGrade = :frenchGrade AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.setDate DESC")
    List<Route> findByFrenchGrade(@Param("frenchGrade") String frenchGrade);
    
    /**
     * 유사 난이도 루트 추천
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.climbingLevel.difficultyScore BETWEEN :targetScore - 1.0 AND :targetScore + 1.0 " +
           "AND r.routeId != :excludeRouteId AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY ABS(r.climbingLevel.difficultyScore - :targetScore), r.averageRating DESC")
    List<Route> findSimilarDifficultyRoutes(@Param("targetScore") BigDecimal targetScore,
                                           @Param("excludeRouteId") Long excludeRouteId,
                                           Pageable pageable);
    
    /**
     * 사용자 레벨별 추천 루트
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.climbingLevel.difficultyScore BETWEEN :userScore - 2.0 AND :userScore + 1.0 " +
           "AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.averageRating DESC, r.climbCount DESC")
    List<Route> findRecommendedRoutesForLevel(@Param("userScore") BigDecimal userScore, 
                                             Pageable pageable);
    
    // ===== 고급 복합 조건 검색 =====
    
    /**
     * 복합 필터 검색 (동적 쿼리용)
     */
    @Query("SELECT r FROM Route r " +
           "JOIN r.wall w " +
           "JOIN r.routeSetter rs " +
           "WHERE (:branchId IS NULL OR w.branch.branchId = :branchId) " +
           "AND (:setterId IS NULL OR rs.setterId = :setterId) " +
           "AND (:minDifficulty IS NULL OR r.climbingLevel.difficultyScore >= :minDifficulty) " +
           "AND (:maxDifficulty IS NULL OR r.climbingLevel.difficultyScore <= :maxDifficulty) " +
           "AND (:routeType IS NULL OR r.routeType = :routeType) " +
           "AND (:startDate IS NULL OR r.setDate >= :startDate) " +
           "AND (:endDate IS NULL OR r.setDate <= :endDate) " +
           "AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.setDate DESC")
    Page<Route> findRoutesByMultipleFilters(@Param("branchId") Long branchId,
                                           @Param("setterId") Long setterId,
                                           @Param("minDifficulty") BigDecimal minDifficulty,
                                           @Param("maxDifficulty") BigDecimal maxDifficulty,
                                           @Param("routeType") String routeType,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate,
                                           Pageable pageable);
    
    /**
     * 홀드 색상별 루트 검색
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.holdColor = :holdColor AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.setDate DESC")
    List<Route> findByHoldColor(@Param("holdColor") String holdColor);
    
    /**
     * 루트 스타일별 검색
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.routeStyle = :routeStyle AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.averageRating DESC")
    List<Route> findByRouteStyle(@Param("routeStyle") String routeStyle);
    
    // ===== 통계 및 분석 =====
    
    /**
     * 지점별 루트 통계
     */
    @Query("SELECT w.branch.branchId, w.branch.branchName, " +
           "COUNT(r) as totalRoutes, " +
           "AVG(r.climbingLevel.difficultyScore) as avgDifficulty, " +
           "AVG(r.successRate) as avgSuccessRate " +
           "FROM Route r " +
           "JOIN r.wall w " +
           "WHERE r.routeStatus = 'ACTIVE' " +
           "GROUP BY w.branch.branchId, w.branch.branchName " +
           "ORDER BY totalRoutes DESC")
    List<Object[]> getRouteStatisticsByBranch();
    
    /**
     * 세터별 성과 통계
     */
    @Query("SELECT rs.setterId, rs.setterName, " +
           "COUNT(r) as routeCount, " +
           "AVG(r.averageRating) as avgRating, " +
           "AVG(r.successRate) as avgSuccessRate, " +
           "SUM(r.climbCount) as totalClimbs " +
           "FROM Route r " +
           "JOIN r.routeSetter rs " +
           "WHERE r.routeStatus = 'ACTIVE' " +
           "GROUP BY rs.setterId, rs.setterName " +
           "ORDER BY avgRating DESC")
    List<Object[]> getSetterPerformanceStats();
    
    /**
     * 난이도별 루트 분포
     */
    @Query("SELECT cl.vGrade, COUNT(r) as routeCount, AVG(r.successRate) as avgSuccessRate " +
           "FROM Route r " +
           "JOIN r.climbingLevel cl " +
           "WHERE r.routeStatus = 'ACTIVE' " +
           "GROUP BY cl.vGrade " +
           "ORDER BY cl.difficultyScore")
    List<Object[]> getDifficultyDistribution();
    
    /**
     * 월별 루트 설정 통계
     */
    @Query("SELECT YEAR(r.setDate), MONTH(r.setDate), COUNT(r) as routeCount " +
           "FROM Route r " +
           "WHERE r.setDate BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(r.setDate), MONTH(r.setDate) " +
           "ORDER BY YEAR(r.setDate), MONTH(r.setDate)")
    List<Object[]> getMonthlyRouteSetStats(@Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);
    
    // ===== 특별 기능 =====
    
    /**
     * 대회용 루트 조회
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.isCompetitionRoute = true AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.setDate DESC")
    List<Route> findCompetitionRoutes();
    
    /**
     * 만료 예정 루트 조회
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.expectedRemoveDate BETWEEN CURRENT_DATE AND :endDate " +
           "AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.expectedRemoveDate")
    List<Route> findRoutesNearExpiry(@Param("endDate") LocalDate endDate);
    
    /**
     * 최근 완등된 루트 조회
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.climbCount DESC, r.updatedAt DESC")
    List<Route> findRecentlyClimbedRoutes(Pageable pageable);
    
    /**
     * 도전적인 루트 조회 (낮은 완등률)
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.routeStatus = 'ACTIVE' AND r.successRate < :maxSuccessRate " +
           "AND r.attemptCount >= :minAttempts " +
           "ORDER BY r.successRate ASC, r.climbingLevel.difficultyScore DESC")
    List<Route> findChallengingRoutes(@Param("maxSuccessRate") Float maxSuccessRate,
                                     @Param("minAttempts") Integer minAttempts,
                                     Pageable pageable);
    
    // ===== 업데이트 메서드 =====
    
    /**
     * 조회수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Route r SET r.viewCount = COALESCE(r.viewCount, 0) + 1 WHERE r.routeId = :routeId")
    int increaseViewCount(@Param("routeId") Long routeId);
    
    /**
     * 완등 기록 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Route r SET " +
           "r.climbCount = COALESCE(r.climbCount, 0) + 1, " +
           "r.attemptCount = COALESCE(r.attemptCount, 0) + 1, " +
           "r.successRate = CASE WHEN COALESCE(r.attemptCount, 0) + 1 > 0 " +
           "  THEN ((COALESCE(r.climbCount, 0) + 1) * 100.0 / (COALESCE(r.attemptCount, 0) + 1)) " +
           "  ELSE 0.0 END " +
           "WHERE r.routeId = :routeId")
    int recordSuccessfulClimb(@Param("routeId") Long routeId);
    
    /**
     * 시도 기록 업데이트 (실패)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Route r SET " +
           "r.attemptCount = COALESCE(r.attemptCount, 0) + 1, " +
           "r.successRate = CASE WHEN COALESCE(r.attemptCount, 0) + 1 > 0 " +
           "  THEN (COALESCE(r.climbCount, 0) * 100.0 / (COALESCE(r.attemptCount, 0) + 1)) " +
           "  ELSE 0.0 END " +
           "WHERE r.routeId = :routeId")
    int recordFailedAttempt(@Param("routeId") Long routeId);
    
    /**
     * 평점 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Route r SET " +
           "r.averageRating = :newRating, " +
           "r.ratingCount = COALESCE(r.ratingCount, 0) + 1 " +
           "WHERE r.routeId = :routeId")
    int updateRating(@Param("routeId") Long routeId, @Param("newRating") Float newRating);
    
    /**
     * 루트 상태 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Route r SET r.routeStatus = :status WHERE r.routeId = :routeId")
    int updateRouteStatus(@Param("routeId") Long routeId, @Param("status") RouteStatus status);
    
    /**
     * 루트 만료 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Route r SET " +
           "r.routeStatus = 'EXPIRED', " +
           "r.actualRemoveDate = CURRENT_DATE " +
           "WHERE r.expectedRemoveDate <= CURRENT_DATE AND r.routeStatus = 'ACTIVE'")
    int expireOverdueRoutes();
    
    // ===== 검색 및 자동완성 =====
    
    /**
     * 루트명 검색
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.routeName LIKE %:keyword% AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.routeName")
    List<Route> findByRouteNameContaining(@Param("keyword") String keyword);
    
    /**
     * 루트명 자동완성
     */
    @Query("SELECT DISTINCT r.routeName FROM Route r " +
           "WHERE r.routeName LIKE %:keyword% AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.routeName")
    List<String> findRouteNameSuggestions(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 홀드 색상 자동완성
     */
    @Query("SELECT DISTINCT r.holdColor FROM Route r " +
           "WHERE r.holdColor IS NOT NULL AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.holdColor")
    List<String> findDistinctHoldColors();
    
    // ===== 성능 최적화된 조회 =====
    
    /**
     * Slice 기반 무한 스크롤 (성능 최적화)
     */
    @Query("SELECT r FROM Route r " +
           "WHERE r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.createdAt DESC")
    Slice<Route> findActiveRoutesSlice(Pageable pageable);
    
    /**
     * 지점별 루트 Slice 조회
     */
    @Query("SELECT r FROM Route r " +
           "JOIN r.wall w " +
           "WHERE w.branch.branchId = :branchId AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.setDate DESC")
    Slice<Route> findRoutesByBranchSlice(@Param("branchId") Long branchId, Pageable pageable);
}
```

---

## 👨‍🎨 2. RouteSetterRepository - 루트 세터 Repository

```java
package com.routepick.domain.route.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.route.entity.RouteSetter;
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
 * RouteSetter Repository
 * - 세터 관리 및 성과 분석
 * - 세터 레벨 및 전문성 관리
 * - 세터별 통계 및 인기도 분석
 */
@Repository
public interface RouteSetterRepository extends BaseRepository<RouteSetter, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 세터명으로 조회 (정확한 매칭)
     */
    @Query("SELECT rs FROM RouteSetter rs WHERE rs.setterName = :setterName AND rs.isActive = true")
    Optional<RouteSetter> findBySetterName(@Param("setterName") String setterName);
    
    /**
     * 세터명 부분 검색 (자동완성용)
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE (rs.setterName LIKE %:keyword% OR rs.nickname LIKE %:keyword% OR rs.englishName LIKE %:keyword%) " +
           "AND rs.isActive = true " +
           "ORDER BY rs.setterName")
    List<RouteSetter> findBySetterNameContaining(@Param("keyword") String keyword);
    
    /**
     * 세터명 자동완성 (이름만 반환)
     */
    @Query("SELECT DISTINCT rs.setterName FROM RouteSetter rs " +
           "WHERE rs.setterName LIKE %:keyword% AND rs.isActive = true " +
           "ORDER BY rs.setterName")
    List<String> findSetterNameSuggestions(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 활성 세터 모두 조회
     */
    @Query("SELECT rs FROM RouteSetter rs WHERE rs.isActive = true ORDER BY rs.setterName")
    List<RouteSetter> findAllActive();
    
    /**
     * 활성 세터 페이징 조회
     */
    @Query("SELECT rs FROM RouteSetter rs WHERE rs.isActive = true ORDER BY rs.averageRating DESC, rs.totalRoutesSet DESC")
    Page<RouteSetter> findAllActive(Pageable pageable);
    
    // ===== 세터 레벨별 조회 =====
    
    /**
     * 세터 레벨별 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.setterLevel = :level AND rs.isActive = true " +
           "ORDER BY rs.averageRating DESC")
    List<RouteSetter> findBySetterLevel(@Param("level") Integer level);
    
    /**
     * 세터 레벨 범위별 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.setterLevel BETWEEN :minLevel AND :maxLevel AND rs.isActive = true " +
           "ORDER BY rs.setterLevel DESC, rs.averageRating DESC")
    List<RouteSetter> findBySetterLevelBetween(@Param("minLevel") Integer minLevel, 
                                              @Param("maxLevel") Integer maxLevel);
    
    /**
     * 고급 세터 조회 (레벨 7 이상)
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.setterLevel >= 7 AND rs.isActive = true " +
           "ORDER BY rs.setterLevel DESC, rs.experienceYears DESC")
    List<RouteSetter> findAdvancedSetters();
    
    /**
     * 신규 세터 조회 (레벨 3 이하)
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.setterLevel <= 3 AND rs.isActive = true " +
           "ORDER BY rs.startSettingDate DESC")
    List<RouteSetter> findJuniorSetters();
    
    // ===== 인기 및 성과 기반 조회 =====
    
    /**
     * 인기 세터 조회 (복합 기준)
     * 기준: 루트 수(40%) + 평점(40%) + 완등률(20%)
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.isActive = true AND rs.totalRoutesSet > 0 " +
           "ORDER BY (rs.totalRoutesSet * 0.4 + rs.averageRating * 20 + " +
           "  (SELECT AVG(r.successRate) FROM Route r WHERE r.routeSetter = rs) * 0.2) DESC")
    List<RouteSetter> findPopularSetters(Pageable pageable);
    
    /**
     * 평점 기준 우수 세터
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.isActive = true AND rs.averageRating >= :minRating AND rs.ratingCount >= :minRatingCount " +
           "ORDER BY rs.averageRating DESC")
    List<RouteSetter> findTopRatedSetters(@Param("minRating") Float minRating,
                                         @Param("minRatingCount") Integer minRatingCount);
    
    /**
     * 활발한 세터 조회 (월간 활동 기준)
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.isActive = true AND rs.monthlyRoutesSet >= :minMonthlyRoutes " +
           "ORDER BY rs.monthlyRoutesSet DESC")
    List<RouteSetter> findActiveSetters(@Param("minMonthlyRoutes") Integer minMonthlyRoutes);
    
    // ===== 전문성 및 특기별 조회 =====
    
    /**
     * 특기 스타일별 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.specialtyStyle LIKE %:style% AND rs.isActive = true " +
           "ORDER BY rs.averageRating DESC")
    List<RouteSetter> findBySpecialtyStyle(@Param("style") String style);
    
    /**
     * 특기 난이도별 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.specialtyDifficulty = :difficulty AND rs.isActive = true " +
           "ORDER BY rs.totalRoutesSet DESC")
    List<RouteSetter> findBySpecialtyDifficulty(@Param("difficulty") String difficulty);
    
    /**
     * 경력별 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.experienceYears >= :minYears AND rs.isActive = true " +
           "ORDER BY rs.experienceYears DESC")
    List<RouteSetter> findByExperienceYears(@Param("minYears") Integer minYears);
    
    /**
     * 자격증 보유 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.certification IS NOT NULL AND rs.certification != '' AND rs.isActive = true " +
           "ORDER BY rs.setterLevel DESC")
    List<RouteSetter> findCertifiedSetters();
    
    // ===== 활동 지역별 조회 =====
    
    /**
     * 주 활동 암장별 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.mainGymName LIKE %:gymName% AND rs.isActive = true " +
           "ORDER BY rs.setterName")
    List<RouteSetter> findByMainGym(@Param("gymName") String gymName);
    
    /**
     * 프리랜서 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.isFreelancer = true AND rs.isActive = true " +
           "ORDER BY rs.averageRating DESC")
    List<RouteSetter> findFreelancerSetters();
    
    /**
     * 특정 지역 활동 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.availableLocations LIKE %:location% AND rs.isActive = true " +
           "ORDER BY rs.setterName")
    List<RouteSetter> findByAvailableLocation(@Param("location") String location);
    
    // ===== 세터별 통계 분석 =====
    
    /**
     * 세터별 루트 수 통계
     */
    @Query("SELECT rs.setterId, rs.setterName, rs.totalRoutesSet FROM RouteSetter rs " +
           "WHERE rs.isActive = true " +
           "ORDER BY rs.totalRoutesSet DESC")
    List<Object[]> countRoutesBySetterId();
    
    /**
     * 세터별 상세 통계
     */
    @Query("SELECT rs.setterId, rs.setterName, rs.totalRoutesSet, rs.averageRating, " +
           "AVG(r.successRate) as avgSuccessRate, " +
           "AVG(r.averageRating) as avgRouteRating, " +
           "COUNT(r) as activeRoutes " +
           "FROM RouteSetter rs " +
           "LEFT JOIN Route r ON r.routeSetter = rs AND r.routeStatus = 'ACTIVE' " +
           "WHERE rs.isActive = true " +
           "GROUP BY rs.setterId, rs.setterName, rs.totalRoutesSet, rs.averageRating " +
           "ORDER BY rs.averageRating DESC")
    List<Object[]> findSetterStatistics();
    
    /**
     * 세터 성과 지표 (상위 퍼센트)
     */
    @Query("SELECT rs.setterId, rs.setterName, " +
           "rs.totalRoutesSet, rs.averageRating, " +
           "PERCENT_RANK() OVER (ORDER BY rs.totalRoutesSet) as routeCountPercentile, " +
           "PERCENT_RANK() OVER (ORDER BY rs.averageRating) as ratingPercentile " +
           "FROM RouteSetter rs " +
           "WHERE rs.isActive = true AND rs.totalRoutesSet > 0 " +
           "ORDER BY rs.averageRating DESC")
    List<Object[]> findSetterPerformanceMetrics();
    
    /**
     * 세터별 특화 태그 분석
     */
    @Query("SELECT rs.setterId, rs.setterName, " +
           "rs.specialtyStyle, rs.specialtyDifficulty, " +
           "COUNT(r) as routeCount, " +
           "AVG(r.successRate) as avgSuccessRate " +
           "FROM RouteSetter rs " +
           "LEFT JOIN Route r ON r.routeSetter = rs AND r.routeStatus = 'ACTIVE' " +
           "WHERE rs.isActive = true " +
           "GROUP BY rs.setterId, rs.setterName, rs.specialtyStyle, rs.specialtyDifficulty " +
           "ORDER BY routeCount DESC")
    List<Object[]> findSetterSpecialtyTags();
    
    /**
     * 지점별 활동 세터 분석
     */
    @Query("SELECT gb.branchName, rs.setterName, COUNT(r) as routeCount " +
           "FROM Route r " +
           "JOIN r.routeSetter rs " +
           "JOIN r.wall w " +
           "JOIN w.branch gb " +
           "WHERE r.routeStatus = 'ACTIVE' AND rs.isActive = true " +
           "AND r.setDate BETWEEN :startDate AND :endDate " +
           "GROUP BY gb.branchName, rs.setterName " +
           "ORDER BY gb.branchName, routeCount DESC")
    List<Object[]> findSettersByBranchAndPeriod(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);
    
    // ===== 랭킹 시스템 =====
    
    /**
     * 세터 레벨별 분포 통계
     */
    @Query("SELECT rs.setterLevel, COUNT(rs) as setterCount FROM RouteSetter rs " +
           "WHERE rs.isActive = true " +
           "GROUP BY rs.setterLevel " +
           "ORDER BY rs.setterLevel")
    List<Object[]> getSetterLevelDistribution();
    
    /**
     * 경력별 세터 분포
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN rs.experienceYears < 1 THEN '1년 미만' " +
           "  WHEN rs.experienceYears < 3 THEN '1-3년' " +
           "  WHEN rs.experienceYears < 5 THEN '3-5년' " +
           "  WHEN rs.experienceYears < 10 THEN '5-10년' " +
           "  ELSE '10년 이상' " +
           "END as experienceRange, " +
           "COUNT(rs) as setterCount " +
           "FROM RouteSetter rs " +
           "WHERE rs.isActive = true AND rs.experienceYears IS NOT NULL " +
           "GROUP BY " +
           "CASE " +
           "  WHEN rs.experienceYears < 1 THEN '1년 미만' " +
           "  WHEN rs.experienceYears < 3 THEN '1-3년' " +
           "  WHEN rs.experienceYears < 5 THEN '3-5년' " +
           "  WHEN rs.experienceYears < 10 THEN '5-10년' " +
           "  ELSE '10년 이상' " +
           "END " +
           "ORDER BY MIN(rs.experienceYears)")
    List<Object[]> getSetterExperienceDistribution();
    
    // ===== 업데이트 메서드 =====
    
    /**
     * 세터 루트 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteSetter rs SET " +
           "rs.totalRoutesSet = COALESCE(rs.totalRoutesSet, 0) + 1, " +
           "rs.monthlyRoutesSet = COALESCE(rs.monthlyRoutesSet, 0) + 1 " +
           "WHERE rs.setterId = :setterId")
    int incrementRouteCount(@Param("setterId") Long setterId);
    
    /**
     * 월간 루트 수 리셋 (매월 실행)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteSetter rs SET rs.monthlyRoutesSet = 0 WHERE rs.isActive = true")
    int resetMonthlyRouteCounts();
    
    /**
     * 세터 평점 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteSetter rs SET " +
           "rs.averageRating = :newRating, " +
           "rs.ratingCount = COALESCE(rs.ratingCount, 0) + 1 " +
           "WHERE rs.setterId = :setterId")
    int updateRating(@Param("setterId") Long setterId, @Param("newRating") Float newRating);
    
    /**
     * 조회수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteSetter rs SET rs.viewCount = COALESCE(rs.viewCount, 0) + 1 WHERE rs.setterId = :setterId")
    int increaseViewCount(@Param("setterId") Long setterId);
    
    /**
     * 팔로워 수 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteSetter rs SET rs.followerCount = :followerCount WHERE rs.setterId = :setterId")
    int updateFollowerCount(@Param("setterId") Long setterId, @Param("followerCount") Integer followerCount);
    
    /**
     * 세터 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteSetter rs SET rs.isActive = false WHERE rs.setterId = :setterId")
    int deactivateSetter(@Param("setterId") Long setterId);
    
    /**
     * 세터 재활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteSetter rs SET rs.isActive = true WHERE rs.setterId = :setterId")
    int reactivateSetter(@Param("setterId") Long setterId);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 복합 조건 세터 검색
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE (:keyword IS NULL OR rs.setterName LIKE %:keyword% OR rs.nickname LIKE %:keyword%) " +
           "AND (:minLevel IS NULL OR rs.setterLevel >= :minLevel) " +
           "AND (:maxLevel IS NULL OR rs.setterLevel <= :maxLevel) " +
           "AND (:isFreelancer IS NULL OR rs.isFreelancer = :isFreelancer) " +
           "AND (:location IS NULL OR rs.availableLocations LIKE %:location%) " +
           "AND rs.isActive = true " +
           "ORDER BY rs.averageRating DESC, rs.totalRoutesSet DESC")
    Page<RouteSetter> findByComplexConditions(@Param("keyword") String keyword,
                                             @Param("minLevel") Integer minLevel,
                                             @Param("maxLevel") Integer maxLevel,
                                             @Param("isFreelancer") Boolean isFreelancer,
                                             @Param("location") String location,
                                             Pageable pageable);
    
    /**
     * 이메일로 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs WHERE rs.email = :email")
    Optional<RouteSetter> findByEmail(@Param("email") String email);
    
    /**
     * 최근 가입한 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE rs.isActive = true " +
           "ORDER BY rs.createdAt DESC")
    List<RouteSetter> findRecentlyJoinedSetters(Pageable pageable);
    
    /**
     * 소셜 미디어 연동 세터 조회
     */
    @Query("SELECT rs FROM RouteSetter rs " +
           "WHERE (rs.instagramUrl IS NOT NULL OR rs.youtubeUrl IS NOT NULL) " +
           "AND rs.isActive = true " +
           "ORDER BY rs.followerCount DESC")
    List<RouteSetter> findSettersWithSocialMedia();
}
```

---

## 🎯 3. ClimbingLevelRepository - 클라이밍 레벨 Repository

```java
package com.routepick.domain.climb.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.climb.entity.ClimbingLevel;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * ClimbingLevel Repository
 * - V등급과 5.등급 매핑 시스템
 * - 난이도 변환 및 진행 시스템
 * - 레벨 통계 및 분포 분석
 */
@Repository
public interface ClimbingLevelRepository extends BaseRepository<ClimbingLevel, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * V등급으로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl WHERE cl.vGrade = :vGrade")
    Optional<ClimbingLevel> findByVGrade(@Param("vGrade") String vGrade);
    
    /**
     * 5.등급(프렌치)으로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl WHERE cl.frenchGrade = :frenchGrade")
    Optional<ClimbingLevel> findByFrenchGrade(@Param("frenchGrade") String frenchGrade);
    
    /**
     * YDS 등급으로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl WHERE cl.ydsGrade = :ydsGrade")
    Optional<ClimbingLevel> findByYdsGrade(@Param("ydsGrade") String ydsGrade);
    
    /**
     * 난이도 점수로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl WHERE cl.difficultyScore = :score")
    Optional<ClimbingLevel> findByDifficultyScore(@Param("score") BigDecimal score);
    
    /**
     * 난이도 점수 범위로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore BETWEEN :minScore AND :maxScore " +
           "ORDER BY cl.difficultyScore")
    List<ClimbingLevel> findByDifficultyScoreBetween(@Param("minScore") BigDecimal minScore, 
                                                    @Param("maxScore") BigDecimal maxScore);
    
    /**
     * 모든 레벨을 난이도 순으로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl ORDER BY cl.difficultyScore ASC")
    List<ClimbingLevel> findAllOrderByDifficultyScore();
    
    // ===== 등급 변환 시스템 =====
    
    /**
     * V등급을 5.등급으로 변환
     */
    @Query("SELECT cl.frenchGrade FROM ClimbingLevel cl WHERE cl.vGrade = :vGrade")
    Optional<String> convertVGradeToFrench(@Param("vGrade") String vGrade);
    
    /**
     * 5.등급을 V등급으로 변환
     */
    @Query("SELECT cl.vGrade FROM ClimbingLevel cl WHERE cl.frenchGrade = :frenchGrade")
    Optional<String> convertFrenchToVGrade(@Param("frenchGrade") String frenchGrade);
    
    /**
     * YDS를 V등급으로 변환
     */
    @Query("SELECT cl.vGrade FROM ClimbingLevel cl WHERE cl.ydsGrade = :ydsGrade")
    Optional<String> convertYdsToVGrade(@Param("ydsGrade") String ydsGrade);
    
    /**
     * 등급 문자열을 난이도 점수로 변환
     */
    @Query("SELECT cl.difficultyScore FROM ClimbingLevel cl " +
           "WHERE cl.vGrade = :grade OR cl.frenchGrade = :grade OR cl.ydsGrade = :grade")
    Optional<BigDecimal> convertGradeToScore(@Param("grade") String grade);
    
    /**
     * 난이도 점수를 주요 등급으로 변환
     */
    @Query("SELECT cl.vGrade, cl.frenchGrade, cl.ydsGrade FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore = :score")
    Optional<Object[]> convertScoreToGrades(@Param("score") BigDecimal score);
    
    // ===== 레벨 진행 시스템 =====
    
    /**
     * 다음 난이도 레벨 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore > :currentScore " +
           "ORDER BY cl.difficultyScore ASC")
    Optional<ClimbingLevel> getNextLevel(@Param("currentScore") BigDecimal currentScore);
    
    /**
     * 이전 난이도 레벨 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore < :currentScore " +
           "ORDER BY cl.difficultyScore DESC")
    Optional<ClimbingLevel> getPreviousLevel(@Param("currentScore") BigDecimal currentScore);
    
    /**
     * 사용자 레벨 진행 경로 (현재 레벨 기준 ±3)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore BETWEEN :currentScore - 3.0 AND :currentScore + 3.0 " +
           "ORDER BY cl.difficultyScore")
    List<ClimbingLevel> findLevelProgression(@Param("currentScore") BigDecimal currentScore);
    
    /**
     * 초급자 레벨 (V0-V3, 5.8-5.10)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore <= 3.0 " +
           "ORDER BY cl.difficultyScore")
    List<ClimbingLevel> findBeginnerLevels();
    
    /**
     * 중급자 레벨 (V4-V7, 5.11-5.12)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore BETWEEN 4.0 AND 7.0 " +
           "ORDER BY cl.difficultyScore")
    List<ClimbingLevel> findIntermediateLevels();
    
    /**
     * 고급자 레벨 (V8+, 5.13+)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore >= 8.0 " +
           "ORDER BY cl.difficultyScore")
    List<ClimbingLevel> findAdvancedLevels();
    
    // ===== 통계 및 분석 =====
    
    /**
     * 난이도 분포 통계 (루트 수 기준)
     */
    @Query("SELECT cl.vGrade, cl.frenchGrade, cl.difficultyScore, COUNT(r) as routeCount " +
           "FROM ClimbingLevel cl " +
           "LEFT JOIN Route r ON r.climbingLevel = cl AND r.routeStatus = 'ACTIVE' " +
           "GROUP BY cl.levelId, cl.vGrade, cl.frenchGrade, cl.difficultyScore " +
           "ORDER BY cl.difficultyScore")
    List<Object[]> calculateDifficultyDistribution();
    
    /**
     * 인기 난이도 Top N
     */
    @Query("SELECT cl.vGrade, cl.frenchGrade, COUNT(r) as routeCount " +
           "FROM ClimbingLevel cl " +
           "JOIN Route r ON r.climbingLevel = cl AND r.routeStatus = 'ACTIVE' " +
           "GROUP BY cl.levelId, cl.vGrade, cl.frenchGrade " +
           "ORDER BY routeCount DESC")
    List<Object[]> findPopularDifficulties();
    
    /**
     * 난이도별 평균 완등률
     */
    @Query("SELECT cl.vGrade, cl.difficultyScore, " +
           "AVG(r.successRate) as avgSuccessRate, " +
           "COUNT(r) as routeCount " +
           "FROM ClimbingLevel cl " +
           "LEFT JOIN Route r ON r.climbingLevel = cl AND r.routeStatus = 'ACTIVE' " +
           "GROUP BY cl.levelId, cl.vGrade, cl.difficultyScore " +
           "ORDER BY cl.difficultyScore")
    List<Object[]> calculateSuccessRateByDifficulty();
    
    /**
     * 난이도별 평균 시도 횟수
     */
    @Query("SELECT cl.vGrade, cl.difficultyScore, " +
           "AVG(r.attemptCount) as avgAttempts, " +
           "AVG(r.climbCount) as avgClimbs " +
           "FROM ClimbingLevel cl " +
           "LEFT JOIN Route r ON r.climbingLevel = cl AND r.routeStatus = 'ACTIVE' " +
           "WHERE r.attemptCount > 0 " +
           "GROUP BY cl.levelId, cl.vGrade, cl.difficultyScore " +
           "ORDER BY cl.difficultyScore")
    List<Object[]> calculateAttemptStatsByDifficulty();
    
    // ===== 등급 시스템별 조회 =====
    
    /**
     * V등급 시스템 모든 레벨
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.vGrade IS NOT NULL AND cl.vGrade != '' " +
           "ORDER BY cl.difficultyScore")
    List<ClimbingLevel> findAllVGrades();
    
    /**
     * 프렌치 등급 시스템 모든 레벨
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.frenchGrade IS NOT NULL AND cl.frenchGrade != '' " +
           "ORDER BY cl.difficultyScore")
    List<ClimbingLevel> findAllFrenchGrades();
    
    /**
     * YDS 등급 시스템 모든 레벨
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.ydsGrade IS NOT NULL AND cl.ydsGrade != '' " +
           "ORDER BY cl.difficultyScore")
    List<ClimbingLevel> findAllYdsGrades();
    
    // ===== 등급 검증 및 유틸리티 =====
    
    /**
     * 등급 존재 여부 확인
     */
    @Query("SELECT COUNT(cl) > 0 FROM ClimbingLevel cl " +
           "WHERE cl.vGrade = :vGrade OR cl.frenchGrade = :frenchGrade OR cl.ydsGrade = :ydsGrade")
    boolean existsByAnyGrade(@Param("vGrade") String vGrade, 
                           @Param("frenchGrade") String frenchGrade, 
                           @Param("ydsGrade") String ydsGrade);
    
    /**
     * 최소 난이도 점수
     */
    @Query("SELECT MIN(cl.difficultyScore) FROM ClimbingLevel cl")
    Optional<BigDecimal> findMinDifficultyScore();
    
    /**
     * 최대 난이도 점수
     */
    @Query("SELECT MAX(cl.difficultyScore) FROM ClimbingLevel cl")
    Optional<BigDecimal> findMaxDifficultyScore();
    
    /**
     * 난이도 점수 구간별 레벨 수
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN cl.difficultyScore < 3 THEN '초급 (V0-V2)' " +
           "  WHEN cl.difficultyScore < 6 THEN '중급 (V3-V5)' " +
           "  WHEN cl.difficultyScore < 10 THEN '고급 (V6-V9)' " +
           "  ELSE '전문 (V10+)' " +
           "END as levelRange, " +
           "COUNT(cl) as levelCount " +
           "FROM ClimbingLevel cl " +
           "GROUP BY " +
           "CASE " +
           "  WHEN cl.difficultyScore < 3 THEN '초급 (V0-V2)' " +
           "  WHEN cl.difficultyScore < 6 THEN '중급 (V3-V5)' " +
           "  WHEN cl.difficultyScore < 10 THEN '고급 (V6-V9)' " +
           "  ELSE '전문 (V10+)' " +
           "END " +
           "ORDER BY MIN(cl.difficultyScore)")
    List<Object[]> getLevelRangeDistribution();
    
    // ===== 추천 시스템 지원 =====
    
    /**
     * 사용자 맞춤 난이도 추천 (현재 레벨 ±1)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore BETWEEN :userScore - 1.0 AND :userScore + 1.0 " +
           "ORDER BY ABS(cl.difficultyScore - :userScore)")
    List<ClimbingLevel> findRecommendedLevelsForUser(@Param("userScore") BigDecimal userScore);
    
    /**
     * 도전적인 난이도 추천 (현재 레벨 +1~+2)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore BETWEEN :userScore + 1.0 AND :userScore + 2.0 " +
           "ORDER BY cl.difficultyScore")
    List<ClimbingLevel> findChallengingLevelsForUser(@Param("userScore") BigDecimal userScore);
    
    /**
     * 연습용 난이도 추천 (현재 레벨 -1~0)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore BETWEEN :userScore - 1.0 AND :userScore " +
           "ORDER BY cl.difficultyScore DESC")
    List<ClimbingLevel> findPracticeLevelsForUser(@Param("userScore") BigDecimal userScore);
}
```

---

## ⚡ 4. 성능 최적화 강화

### 복합 인덱스 생성
```sql
-- 루트 검색 최적화 (핵심 검색 패턴)
CREATE INDEX idx_route_search_optimal 
ON routes(wall_id, route_status, difficulty_score, created_at DESC);

-- 인기도 알고리즘 최적화
CREATE INDEX idx_route_popularity_complex 
ON routes(route_status, view_count DESC, scrap_count DESC, climb_count DESC);

-- 세터별 성과 분석
CREATE INDEX idx_route_setter_performance 
ON routes(route_setter_id, route_status, average_rating DESC, success_rate DESC);

-- 난이도별 분석
CREATE INDEX idx_route_difficulty_analysis 
ON routes(level_id, route_status, success_rate, attempt_count);

-- 지점별 루트 관리
CREATE INDEX idx_route_branch_management 
ON routes(wall_id, route_status, set_date DESC, expected_remove_date);
```

### N+1 문제 완전 해결
```java
// Repository 메서드에서 EntityGraph 활용 예시
@EntityGraph(attributePaths = {
    "routeSetter", 
    "wall", 
    "wall.branch", 
    "wall.branch.gym", 
    "climbingLevel"
})
@Query("SELECT r FROM Route r WHERE r.routeStatus = 'ACTIVE'")
List<Route> findActiveRoutesWithAllDetails();
```

### Custom Repository 인터페이스
```java
// RouteRepositoryCustom - QueryDSL 기반 동적 검색
public interface RouteRepositoryCustom {
    Page<Route> findRoutesByDynamicFilter(RouteSearchFilter filter, Pageable pageable);
    List<Route> findSimilarRoutes(Long routeId, int limit);
    List<RouteStatistics> getRouteAnalytics(Long branchId, LocalDate startDate, LocalDate endDate);
}

// RouteSetterRepositoryCustom - 세터 성과 분석 전문
public interface RouteSetterRepositoryCustom {
    List<SetterPerformanceDto> analyzeSetterPerformance(LocalDate startDate, LocalDate endDate);
    List<SetterRankingDto> getSetterRankings(String rankingType);
    SetterStatisticsDto getDetailedSetterStats(Long setterId);
}
```

---

## ✅ 설계 완료 체크리스트

### 루트 핵심 Repository (3개)
- [x] **RouteRepository** - 핵심 루트 검색, 인기도 알고리즘, 복합 조건 검색
- [x] **RouteSetterRepository** - 세터 관리, 성과 분석, 레벨 시스템
- [x] **ClimbingLevelRepository** - V등급/5.등급 매핑, 변환 시스템, 진행 경로

### 핵심 기능 구현
- [x] 난이도별 검색 성능 최적화 (V등급, 5.등급 체계)
- [x] 인기도 복합 알고리즘 (조회수 30% + 스크랩수 40% + 완등률 30%)
- [x] 세터별 루트 관리 및 성과 분석
- [x] 등급 변환 시스템 (V ↔ 5. ↔ YDS)

### 성능 최적화
- [x] @EntityGraph로 N+1 문제 완전 해결
- [x] 복합 인덱스 전략 (wall_id + difficulty + route_status)
- [x] Slice 기반 무한 스크롤 지원
- [x] 네이티브 쿼리 최적화 (복잡한 통계 계산)

### 고급 검색 기능
- [x] 복합 조건 동적 검색 (지점+세터+난이도+기간+상태)
- [x] 유사 난이도 루트 추천 알고리즘
- [x] 사용자 레벨별 맞춤 추천
- [x] 홀드 색상, 루트 스타일별 검색

### Custom Repository 설계
- [x] RouteRepositoryCustom 인터페이스 설계
- [x] RouteSetterRepositoryCustom 인터페이스 설계
- [x] QueryDSL 기반 동적 검색 지원
- [x] 성과 분석 전문 메서드

---

**다음 단계**: Step 5-3c Route 미디어 및 상호작용 Repository 설계  
**완료일**: 2025-08-20  
**핵심 성과**: 루트 핵심 3개 Repository + 인기도 알고리즘 + 등급 변환 시스템 완료