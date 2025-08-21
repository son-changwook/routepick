# Step 5-3c2: 루트 관리 Repository - RouteSetter & ClimbingLevel

> 루트 세터 관리 & 클라이밍 레벨 시스템 Repository 완전 설계  
> 생성일: 2025-08-21  
> 기반: step5-3c_route_core_repositories.md 세분화  
> 포함 Repository: RouteSetterRepository, ClimbingLevelRepository

---

## 📋 파일 세분화 정보
- **원본 파일**: step5-3c_route_core_repositories.md (1,244줄)
- **세분화 사유**: 토큰 제한 대응 및 기능별 책임 분리
- **이 파일 포함**: RouteSetterRepository, ClimbingLevelRepository (세터 관리 + 레벨 시스템)
- **다른 파일**: step5-3c1_route_search_repositories.md (RouteRepository)

---

## 🎯 설계 목표

- **세터별 루트 관리 효율화**: 세터 성과 분석 및 관리
- **등급 변환 시스템**: V등급 ↔ 5.등급 ↔ YDS 완벽 매핑
- **레벨 진행 시스템**: 사용자 맞춤 난이도 추천
- **성과 분석 최적화**: 세터 랭킹, 통계, 특화 분석

---

## 👨‍🎨 1. RouteSetterRepository - 루트 세터 Repository

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

## 🎯 2. ClimbingLevelRepository - 클라이밍 레벨 Repository

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

## ⚡ 성능 최적화 전략

### Custom Repository 인터페이스
```java
// RouteSetterRepositoryCustom - 세터 성과 분석 전문
public interface RouteSetterRepositoryCustom {
    List<SetterPerformanceDto> analyzeSetterPerformance(LocalDate startDate, LocalDate endDate);
    List<SetterRankingDto> getSetterRankings(String rankingType);
    SetterStatisticsDto getDetailedSetterStats(Long setterId);
}

// ClimbingLevelRepositoryCustom - 난이도 분석 전문
public interface ClimbingLevelRepositoryCustom {
    List<DifficultyAnalysisDto> analyzeDifficultyTrends(LocalDate startDate, LocalDate endDate);
    List<LevelProgressionDto> calculateLevelProgression(Long userId);
    DifficultyRecommendationDto getPersonalizedDifficultyRecommendation(Long userId);
}
```

### 인덱스 최적화
```sql
-- 세터 성과 분석 최적화
CREATE INDEX idx_route_setter_performance 
ON routes(route_setter_id, route_status, average_rating DESC, success_rate DESC);

-- 난이도 분석 최적화  
CREATE INDEX idx_climbing_level_analysis
ON climbing_levels(difficulty_score, v_grade, french_grade);

-- 세터 검색 최적화
CREATE INDEX idx_setter_search_optimal
ON route_setters(is_active, setter_level, average_rating DESC, total_routes_set DESC);
```

### 캐싱 전략
```java
@Cacheable(value = "topSetters", key = "#limit")
List<RouteSetter> findTopRatedSetters(int limit);

@Cacheable(value = "difficultyDistribution")
List<Object[]> calculateDifficultyDistribution();

@CacheEvict(value = "setterStats", allEntries = true)
void refreshSetterStatistics();
```

---

## ✅ 설계 완료 체크리스트

### 루트 관리 Repository (2개)
- [x] **RouteSetterRepository** - 세터 관리, 성과 분석, 레벨 시스템
- [x] **ClimbingLevelRepository** - V등급/5.등급 매핑, 변환 시스템, 진행 경로

### 핵심 기능 구현
- [x] 세터별 루트 관리 및 성과 분석
- [x] 등급 변환 시스템 (V ↔ 5. ↔ YDS)
- [x] 레벨 진행 경로 추천 시스템
- [x] 세터 랭킹 및 통계 분석

### 고급 기능
- [x] 세터 성과 지표 (상위 퍼센트 랭킹)
- [x] 난이도별 통계 및 분포 분석
- [x] 사용자 맞춤 난이도 추천
- [x] 세터 전문성 및 특기 분석

### 성능 최적화
- [x] 복합 조건 세터 검색 최적화
- [x] 등급 변환 시스템 캐싱
- [x] 세터 통계 분석 인덱스
- [x] Custom Repository 인터페이스 설계

---

**관련 파일**: step5-3c1_route_search_repositories.md (RouteRepository)  
**완료일**: 2025-08-21  
**핵심 성과**: 루트 관리 2개 Repository 완성 (세터 성과 분석 + 등급 변환 시스템)