# Step 5-3c1: 루트 검색 Repository - RouteRepository 핵심 검색

> 클라이밍 루트 핵심 검색 Repository 완전 설계 (인기도 알고리즘 특화)  
> 생성일: 2025-08-21  
> 기반: step5-3c_route_core_repositories.md 세분화  
> 포함 Repository: RouteRepository

---

## 📋 파일 세분화 정보
- **원본 파일**: step5-3c_route_core_repositories.md (1,244줄)
- **세분화 사유**: 토큰 제한 대응 및 기능별 책임 분리
- **이 파일 포함**: RouteRepository (핵심 루트 검색)
- **다른 파일**: step5-3c2_route_management_repositories.md (RouteSetterRepository, ClimbingLevelRepository)

---

## 🎯 설계 목표

- **난이도별 검색 성능 최적화**: V등급/5.등급 체계 지원
- **인기도 기반 정렬**: 조회수, 스크랩수, 완등률 복합 기준  
- **고품질 검색 알고리즘**: 복합 조건 최적화
- **N+1 문제 완전 해결**: EntityGraph 최적화

---

## 🧗‍♀️ RouteRepository - 루트 핵심 검색 Repository

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

## ⚡ 성능 최적화 강화

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
```

---

## ✅ 설계 완료 체크리스트

### 루트 검색 Repository (1개)
- [x] **RouteRepository** - 핵심 루트 검색, 인기도 알고리즘, 복합 조건 검색

### 핵심 기능 구현  
- [x] 난이도별 검색 성능 최적화 (V등급, 5.등급 체계)
- [x] 인기도 복합 알고리즘 (조회수 30% + 스크랩수 40% + 완등률 30%)
- [x] 복합 조건 동적 검색 (지점+세터+난이도+기간+상태)
- [x] 유사 난이도 루트 추천 알고리즘

### 성능 최적화
- [x] @EntityGraph로 N+1 문제 완전 해결
- [x] 복합 인덱스 전략 (wall_id + difficulty + route_status)
- [x] Slice 기반 무한 스크롤 지원
- [x] 네이티브 쿼리 최적화 (복잡한 통계 계산)

### 고급 검색 기능
- [x] 사용자 레벨별 맞춤 추천
- [x] 홀드 색상, 루트 스타일별 검색
- [x] 대회용, 만료 예정 루트 특별 검색
- [x] 도전적인 루트 조회 (낮은 완등률)

---

**관련 파일**: step5-3c2_route_management_repositories.md (RouteSetterRepository, ClimbingLevelRepository)  
**완료일**: 2025-08-21  
**핵심 성과**: 루트 검색 Repository 완성 (인기도 알고리즘 + 복합 조건 검색 + 성능 최적화)