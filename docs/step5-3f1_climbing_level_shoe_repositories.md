# 🧗‍♀️ Step 5-3f1: 클라이밍 등급 & 신발 Repository 설계

> **RoutePickr 등급 & 신발 시스템** - 등급 변환, 신발 프로필 관리
> 
> **생성일**: 2025-08-20  
> **단계**: 5-3f1 (Repository 50개 - 등급 & 신발 3개)  
> **분할**: step5-3f_climbing_activity_repositories.md에서 세분화
> **연관**: step5-3f2_user_activity_repositories.md

---

## 📋 파일 개요

이 파일은 **RoutePickr 클라이밍 등급 & 신발 시스템의 3개 Repository**를 담고 있습니다.

### 🎯 주요 특징
- **등급 시스템 최적화**: V등급/YDS/프랑스 등급 통합 매핑 및 변환
- **신발 프로필 관리**: 사용자 신발 등록 및 프로필 노출 최적화
- **통계 및 분석**: 등급별/신발별 사용자 분포 및 선호도 분석

### 📊 Repository 목록 (3개)
1. **ClimbingLevelRepository** - 클라이밍 등급 Repository
2. **ClimbingShoeRepository** - 클라이밍 신발 Repository  
3. **UserClimbingShoeRepository** - 사용자 신발 Repository

---

## 🎯 1. ClimbingLevelRepository - 클라이밍 등급 Repository

```java
package com.routepick.domain.climb.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.climb.entity.ClimbingLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * ClimbingLevel Repository
 * - 🎯 V등급/YDS/프랑스 등급 통합 관리
 * - 등급 변환 및 매핑 시스템
 * - 레벨 진행도 추적
 * - 난이도 기반 검색
 */
@Repository
public interface ClimbingLevelRepository extends BaseRepository<ClimbingLevel, Long> {
    
    // ===== 기본 등급 조회 =====
    
    /**
     * V등급으로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.vGrade = :vGrade AND cl.isActive = true")
    Optional<ClimbingLevel> findByVGrade(@Param("vGrade") String vGrade);
    
    /**
     * YDS 등급으로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.ydsGrade = :ydsGrade AND cl.isActive = true")
    Optional<ClimbingLevel> findByYdsGrade(@Param("ydsGrade") String ydsGrade);
    
    /**
     * 프랑스 등급으로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.frenchGrade = :frenchGrade AND cl.isActive = true")
    Optional<ClimbingLevel> findByFrenchGrade(@Param("frenchGrade") String frenchGrade);
    
    /**
     * 클라이밍 타입별 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.climbingType = :climbingType AND cl.isActive = true " +
           "ORDER BY cl.gradeOrder ASC")
    List<ClimbingLevel> findByClimbingTypeOrderByGradeOrder(@Param("climbingType") String climbingType);
    
    // ===== 난이도 기반 조회 =====
    
    /**
     * 난이도 점수 범위로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore BETWEEN :minScore AND :maxScore " +
           "AND cl.isActive = true " +
           "ORDER BY cl.difficultyScore ASC")
    List<ClimbingLevel> findByDifficultyScoreBetween(@Param("minScore") BigDecimal minScore, 
                                                    @Param("maxScore") BigDecimal maxScore);
    
    /**
     * 모든 등급을 난이도 순으로 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.isActive = true " +
           "ORDER BY cl.difficultyScore ASC")
    List<ClimbingLevel> findAllOrderByDifficultyScore();
    
    /**
     * 특정 난이도 이상의 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore >= :minScore AND cl.isActive = true " +
           "ORDER BY cl.difficultyScore ASC")
    List<ClimbingLevel> findByDifficultyScoreGreaterThanEqual(@Param("minScore") BigDecimal minScore);
    
    /**
     * 특정 난이도 이하의 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore <= :maxScore AND cl.isActive = true " +
           "ORDER BY cl.difficultyScore ASC")
    List<ClimbingLevel> findByDifficultyScoreLessThanEqual(@Param("maxScore") BigDecimal maxScore);
    
    // ===== 레벨 진행도 관리 =====
    
    /**
     * 다음 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.gradeOrder = :currentOrder + 1 " +
           "AND cl.climbingType = :climbingType AND cl.isActive = true")
    Optional<ClimbingLevel> getNextLevel(@Param("currentOrder") Integer currentOrder, 
                                        @Param("climbingType") String climbingType);
    
    /**
     * 이전 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.gradeOrder = :currentOrder - 1 " +
           "AND cl.climbingType = :climbingType AND cl.isActive = true")
    Optional<ClimbingLevel> getPreviousLevel(@Param("currentOrder") Integer currentOrder, 
                                            @Param("climbingType") String climbingType);
    
    /**
     * 레벨 진행 경로 조회 (현재 레벨부터 목표 레벨까지)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.gradeOrder BETWEEN :startOrder AND :endOrder " +
           "AND cl.climbingType = :climbingType AND cl.isActive = true " +
           "ORDER BY cl.gradeOrder ASC")
    List<ClimbingLevel> findLevelProgression(@Param("startOrder") Integer startOrder,
                                           @Param("endOrder") Integer endOrder,
                                           @Param("climbingType") String climbingType);
    
    /**
     * 사용자 현재 레벨에서 다음 N단계 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.gradeOrder > :currentOrder " +
           "AND cl.gradeOrder <= :currentOrder + :steps " +
           "AND cl.climbingType = :climbingType AND cl.isActive = true " +
           "ORDER BY cl.gradeOrder ASC")
    List<ClimbingLevel> findNextLevels(@Param("currentOrder") Integer currentOrder,
                                      @Param("steps") Integer steps,
                                      @Param("climbingType") String climbingType);
    
    // ===== 등급 변환 시스템 =====
    
    /**
     * V등급을 YDS로 변환
     */
    @Query("SELECT cl.ydsGrade FROM ClimbingLevel cl " +
           "WHERE cl.vGrade = :vGrade AND cl.ydsGrade IS NOT NULL AND cl.isActive = true")
    Optional<String> convertVGradeToYds(@Param("vGrade") String vGrade);
    
    /**
     * V등급을 프랑스 등급으로 변환
     */
    @Query("SELECT cl.frenchGrade FROM ClimbingLevel cl " +
           "WHERE cl.vGrade = :vGrade AND cl.frenchGrade IS NOT NULL AND cl.isActive = true")
    Optional<String> convertVGradeToFrench(@Param("vGrade") String vGrade);
    
    /**
     * YDS를 V등급으로 변환
     */
    @Query("SELECT cl.vGrade FROM ClimbingLevel cl " +
           "WHERE cl.ydsGrade = :ydsGrade AND cl.vGrade IS NOT NULL AND cl.isActive = true")
    Optional<String> convertYdsToVGrade(@Param("ydsGrade") String ydsGrade);
    
    /**
     * 난이도 점수로 등급 매핑
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE ABS(cl.difficultyScore - :score) = " +
           "(SELECT MIN(ABS(cl2.difficultyScore - :score)) FROM ClimbingLevel cl2 " +
           " WHERE cl2.climbingType = :climbingType AND cl2.isActive = true) " +
           "AND cl.climbingType = :climbingType AND cl.isActive = true")
    Optional<ClimbingLevel> findClosestLevelByScore(@Param("score") BigDecimal score,
                                                   @Param("climbingType") String climbingType);
    
    // ===== 초보자/숙련도별 조회 =====
    
    /**
     * 초보자 친화적 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.beginnerFriendly = true AND cl.isActive = true " +
           "ORDER BY cl.gradeOrder ASC")
    List<ClimbingLevel> findBeginnerFriendlyLevels();
    
    /**
     * 대회 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.isCompetitionGrade = true AND cl.isActive = true " +
           "ORDER BY cl.gradeOrder ASC")
    List<ClimbingLevel> findCompetitionGrades();
    
    /**
     * 난이도 수준별 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.difficultyScore >= :minScore AND cl.difficultyScore <= :maxScore " +
           "AND cl.isActive = true " +
           "ORDER BY cl.gradeOrder ASC")
    List<ClimbingLevel> findByDifficultyLevel(@Param("minScore") BigDecimal minScore,
                                             @Param("maxScore") BigDecimal maxScore);
    
    // ===== 통계 및 분석 =====
    
    /**
     * 등급별 루트 수 통계
     */
    @Query("SELECT cl.levelId, cl.vGrade, cl.ydsGrade, cl.routeCount " +
           "FROM ClimbingLevel cl " +
           "WHERE cl.isActive = true " +
           "ORDER BY cl.routeCount DESC")
    List<Object[]> getLevelRouteStatistics();
    
    /**
     * 등급별 사용자 분포
     */
    @Query("SELECT cl.levelId, cl.vGrade, cl.userCount " +
           "FROM ClimbingLevel cl " +
           "WHERE cl.isActive = true " +
           "ORDER BY cl.userCount DESC")
    List<Object[]> getLevelUserDistribution();
    
    /**
     * 평균 성공률 높은 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.averageSuccessRate >= :minSuccessRate AND cl.isActive = true " +
           "ORDER BY cl.averageSuccessRate DESC")
    List<ClimbingLevel> findHighSuccessRateLevels(@Param("minSuccessRate") Float minSuccessRate);
    
    /**
     * 인기 등급 조회 (시도 횟수 기준)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.totalAttempts > 0 AND cl.isActive = true " +
           "ORDER BY cl.totalAttempts DESC")
    List<ClimbingLevel> findPopularLevels(Pageable pageable);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 등급명으로 검색 (모든 등급 시스템)
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE (cl.vGrade LIKE %:keyword% " +
           "   OR cl.ydsGrade LIKE %:keyword% " +
           "   OR cl.frenchGrade LIKE %:keyword%) " +
           "AND cl.isActive = true " +
           "ORDER BY cl.gradeOrder ASC")
    List<ClimbingLevel> searchByGradeName(@Param("keyword") String keyword);
    
    /**
     * 복합 조건 등급 검색
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE (:climbingType IS NULL OR cl.climbingType = :climbingType) " +
           "AND (:minScore IS NULL OR cl.difficultyScore >= :minScore) " +
           "AND (:maxScore IS NULL OR cl.difficultyScore <= :maxScore) " +
           "AND (:beginnerFriendly IS NULL OR cl.beginnerFriendly = :beginnerFriendly) " +
           "AND cl.isActive = true " +
           "ORDER BY cl.gradeOrder ASC")
    Page<ClimbingLevel> findByComplexConditions(@Param("climbingType") String climbingType,
                                               @Param("minScore") BigDecimal minScore,
                                               @Param("maxScore") BigDecimal maxScore,
                                               @Param("beginnerFriendly") Boolean beginnerFriendly,
                                               Pageable pageable);
    
    // ===== 관리자용 메서드 =====
    
    /**
     * 비활성 등급 조회
     */
    @Query("SELECT cl FROM ClimbingLevel cl " +
           "WHERE cl.isActive = false " +
           "ORDER BY cl.gradeOrder ASC")
    List<ClimbingLevel> findInactiveLevels();
    
    /**
     * 등급 순서 중복 확인
     */
    @Query("SELECT COUNT(cl) > 1 FROM ClimbingLevel cl " +
           "WHERE cl.gradeOrder = :gradeOrder AND cl.climbingType = :climbingType")
    boolean hasGradeOrderConflict(@Param("gradeOrder") Integer gradeOrder, 
                                 @Param("climbingType") String climbingType);
    
    /**
     * 최대 등급 순서 조회
     */
    @Query("SELECT MAX(cl.gradeOrder) FROM ClimbingLevel cl " +
           "WHERE cl.climbingType = :climbingType AND cl.isActive = true")
    Optional<Integer> getMaxGradeOrder(@Param("climbingType") String climbingType);
    
    /**
     * 전체 등급 수 조회
     */
    @Query("SELECT COUNT(cl) FROM ClimbingLevel cl " +
           "WHERE cl.isActive = true")
    long countActiveLevels();
    
    /**
     * 타입별 등급 수 조회
     */
    @Query("SELECT cl.climbingType, COUNT(cl) as levelCount FROM ClimbingLevel cl " +
           "WHERE cl.isActive = true " +
           "GROUP BY cl.climbingType " +
           "ORDER BY levelCount DESC")
    List<Object[]> countLevelsByType();
}
```

---

## 👟 2. ClimbingShoeRepository - 클라이밍 신발 Repository

```java
package com.routepick.domain.climb.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.climb.entity.ClimbingShoe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ClimbingShoe Repository
 * - 👟 신발 프로필 노출용 간단 관리
 * - 브랜드/모델별 검색
 * - 인기 신발 조회
 * - 기본 신발 정보 관리
 */
@Repository
public interface ClimbingShoeRepository extends BaseRepository<ClimbingShoe, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 브랜드와 모델로 신발 검색
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.brand = :brand AND cs.model = :model AND cs.isActive = true")
    Optional<ClimbingShoe> findByBrandAndModel(@Param("brand") String brand, 
                                              @Param("model") String model);
    
    /**
     * 브랜드별 신발 목록 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.brand = :brand AND cs.isActive = true " +
           "ORDER BY cs.model ASC")
    List<ClimbingShoe> findByBrand(@Param("brand") String brand);
    
    /**
     * 전체 신발 목록 (브랜드/모델순 정렬)
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true " +
           "ORDER BY cs.brand ASC, cs.model ASC")
    List<ClimbingShoe> findAllOrderByBrandAscModelAsc();
    
    /**
     * 활성 브랜드 목록 조회
     */
    @Query("SELECT DISTINCT cs.brand FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true " +
           "ORDER BY cs.brand ASC")
    List<String> findDistinctBrands();
    
    // ===== 인기 신발 조회 =====
    
    /**
     * 인기 신발 모델 TOP 10 (사용자 수 기준)
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true AND cs.userCount > 0 " +
           "ORDER BY cs.userCount DESC")
    List<ClimbingShoe> findPopularShoeModels(Pageable pageable);
    
    /**
     * 평점 높은 신발 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true AND cs.averageRating >= :minRating " +
           "AND cs.reviewCount >= :minReviews " +
           "ORDER BY cs.averageRating DESC, cs.reviewCount DESC")
    List<ClimbingShoe> findHighRatedShoes(@Param("minRating") Float minRating,
                                         @Param("minReviews") Integer minReviews);
    
    /**
     * 신발 타입별 인기 신발
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.shoeType = :shoeType AND cs.isActive = true " +
           "ORDER BY cs.popularityScore DESC")
    List<ClimbingShoe> findPopularShoesByType(@Param("shoeType") String shoeType);
    
    /**
     * 최근 출시 신발 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true AND cs.modelYear >= :year " +
           "ORDER BY cs.modelYear DESC, cs.createdAt DESC")
    List<ClimbingShoe> findRecentShoes(@Param("year") Integer year);
    
    // ===== 카테고리별 조회 =====
    
    /**
     * 신발 타입별 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.shoeType = :shoeType AND cs.isActive = true " +
           "ORDER BY cs.brand ASC, cs.model ASC")
    List<ClimbingShoe> findByShoeType(@Param("shoeType") String shoeType);
    
    /**
     * 가격대별 신발 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.priceRange = :priceRange AND cs.isActive = true " +
           "ORDER BY cs.averageRating DESC")
    List<ClimbingShoe> findByPriceRange(@Param("priceRange") String priceRange);
    
    /**
     * 스킬 레벨별 신발 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.targetSkillLevel = :skillLevel AND cs.isActive = true " +
           "ORDER BY cs.popularityScore DESC")
    List<ClimbingShoe> findByTargetSkillLevel(@Param("skillLevel") String skillLevel);
    
    /**
     * 성별별 신발 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE (cs.genderType = :genderType OR cs.genderType = 'UNISEX') " +
           "AND cs.isActive = true " +
           "ORDER BY cs.brand ASC, cs.model ASC")
    List<ClimbingShoe> findByGenderType(@Param("genderType") String genderType);
    
    // ===== 검색 기능 =====
    
    /**
     * 신발명으로 검색 (브랜드 + 모델)
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE (cs.brand LIKE %:keyword% OR cs.model LIKE %:keyword%) " +
           "AND cs.isActive = true " +
           "ORDER BY cs.popularityScore DESC")
    List<ClimbingShoe> searchByName(@Param("keyword") String keyword);
    
    /**
     * 복합 조건 신발 검색
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE (:brand IS NULL OR cs.brand = :brand) " +
           "AND (:shoeType IS NULL OR cs.shoeType = :shoeType) " +
           "AND (:priceRange IS NULL OR cs.priceRange = :priceRange) " +
           "AND (:skillLevel IS NULL OR cs.targetSkillLevel = :skillLevel) " +
           "AND cs.isActive = true " +
           "ORDER BY cs.popularityScore DESC")
    Page<ClimbingShoe> findByComplexConditions(@Param("brand") String brand,
                                              @Param("shoeType") String shoeType,
                                              @Param("priceRange") String priceRange,
                                              @Param("skillLevel") String skillLevel,
                                              Pageable pageable);
    
    // ===== 통계 정보 =====
    
    /**
     * 브랜드별 신발 수 통계
     */
    @Query("SELECT cs.brand, COUNT(cs) as shoeCount FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true " +
           "GROUP BY cs.brand " +
           "ORDER BY shoeCount DESC")
    List<Object[]> countShoesByBrand();
    
    /**
     * 타입별 신발 수 통계
     */
    @Query("SELECT cs.shoeType, COUNT(cs) as shoeCount FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true " +
           "GROUP BY cs.shoeType " +
           "ORDER BY shoeCount DESC")
    List<Object[]> countShoesByType();
    
    /**
     * 평점 분포 통계
     */
    @Query("SELECT " +
           "FLOOR(cs.averageRating) as ratingFloor, " +
           "COUNT(cs) as shoeCount " +
           "FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true AND cs.averageRating > 0 " +
           "GROUP BY FLOOR(cs.averageRating) " +
           "ORDER BY ratingFloor DESC")
    List<Object[]> getRatingDistribution();
    
    /**
     * 전체 신발 통계 요약
     */
    @Query("SELECT " +
           "COUNT(cs) as totalShoes, " +
           "COUNT(DISTINCT cs.brand) as totalBrands, " +
           "AVG(cs.averageRating) as avgRating, " +
           "SUM(cs.userCount) as totalUsers " +
           "FROM ClimbingShoe cs " +
           "WHERE cs.isActive = true")
    List<Object[]> getShoeStatisticsSummary();
    
    // ===== 관리자용 메서드 =====
    
    /**
     * 단종된 신발 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.isDiscontinued = true " +
           "ORDER BY cs.brand ASC, cs.model ASC")
    List<ClimbingShoe> findDiscontinuedShoes();
    
    /**
     * 비활성 신발 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.isActive = false " +
           "ORDER BY cs.brand ASC, cs.model ASC")
    List<ClimbingShoe> findInactiveShoes();
    
    /**
     * 리뷰가 없는 신발 조회
     */
    @Query("SELECT cs FROM ClimbingShoe cs " +
           "WHERE cs.reviewCount = 0 AND cs.isActive = true " +
           "ORDER BY cs.createdAt ASC")
    List<ClimbingShoe> findShoesWithoutReviews();
    
    /**
     * 브랜드와 모델 중복 확인
     */
    @Query("SELECT COUNT(cs) > 1 FROM ClimbingShoe cs " +
           "WHERE cs.brand = :brand AND cs.model = :model")
    boolean hasBrandModelConflict(@Param("brand") String brand, @Param("model") String model);
    
    /**
     * 전체 신발 수 조회
     */
    @Query("SELECT COUNT(cs) FROM ClimbingShoe cs WHERE cs.isActive = true")
    long countActiveShoes();
}
```

---

## 👟📝 3. UserClimbingShoeRepository - 사용자 신발 Repository

```java
package com.routepick.domain.climb.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.climb.entity.UserClimbingShoe;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * UserClimbingShoe Repository
 * - 👟📝 사용자 신발 프로필 관리
 * - 신발 등록 및 노출용 조회
 * - 사용자별 신발 목록
 * - 신발별 사용자 통계
 */
@Repository
public interface UserClimbingShoeRepository extends BaseRepository<UserClimbingShoe, Long> {
    
    // ===== 사용자별 신발 조회 =====
    
    /**
     * 사용자가 등록한 신발 목록 (최신순)
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.climbingShoe cs " +
           "WHERE ucs.user.userId = :userId " +
           "ORDER BY ucs.createdAt DESC")
    List<UserClimbingShoe> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    /**
     * 사용자의 현재 사용 중인 신발 조회
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.climbingShoe cs " +
           "WHERE ucs.user.userId = :userId AND ucs.isCurrentlyUsing = true " +
           "ORDER BY ucs.lastUseDate DESC")
    List<UserClimbingShoe> findByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 특정 신발 조회
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "WHERE ucs.user.userId = :userId AND ucs.climbingShoe.shoeId = :shoeId")
    Optional<UserClimbingShoe> findByUserIdAndShoeId(@Param("userId") Long userId, 
                                                    @Param("shoeId") Long shoeId);
    
    /**
     * 사용자 신발 등록 여부 확인
     */
    @Query("SELECT COUNT(ucs) > 0 FROM UserClimbingShoe ucs " +
           "WHERE ucs.user.userId = :userId AND ucs.climbingShoe.shoeId = :shoeId")
    boolean existsByUserIdAndShoeId(@Param("userId") Long userId, @Param("shoeId") Long shoeId);
    
    // ===== 신발별 사용자 조회 =====
    
    /**
     * 해당 신발을 사용하는 사용자 수
     */
    @Query("SELECT COUNT(DISTINCT ucs.user.userId) FROM UserClimbingShoe ucs " +
           "WHERE ucs.climbingShoe.shoeId = :shoeId")
    long countByShoeId(@Param("shoeId") Long shoeId);
    
    /**
     * 같은 신발을 사용하는 다른 사용자들
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.user u " +
           "WHERE ucs.climbingShoe.shoeId = :shoeId " +
           "AND ucs.user.userId != :excludeUserId " +
           "AND ucs.isCurrentlyUsing = true " +
           "ORDER BY ucs.reviewRating DESC")
    List<UserClimbingShoe> findUsersWithSameShoe(@Param("shoeId") Long shoeId, 
                                                 @Param("excludeUserId") Long excludeUserId);
    
    /**
     * 신발별 현재 사용자 목록
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.user u " +
           "WHERE ucs.climbingShoe.shoeId = :shoeId AND ucs.isCurrentlyUsing = true " +
           "ORDER BY ucs.createdAt DESC")
    List<UserClimbingShoe> findCurrentUsersByShoeId(@Param("shoeId") Long shoeId);
    
    // ===== 사이즈별 조회 =====
    
    /**
     * 사용자의 특정 사이즈 신발 조회
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.climbingShoe cs " +
           "WHERE ucs.user.userId = :userId AND ucs.shoeSize = :shoeSize " +
           "ORDER BY ucs.createdAt DESC")
    List<UserClimbingShoe> findByUserIdAndShoeSize(@Param("userId") Long userId, 
                                                  @Param("shoeSize") Integer shoeSize);
    
    /**
     * 신발별 사이즈 분포
     */
    @Query("SELECT ucs.shoeSize, COUNT(ucs) as userCount FROM UserClimbingShoe ucs " +
           "WHERE ucs.climbingShoe.shoeId = :shoeId " +
           "GROUP BY ucs.shoeSize " +
           "ORDER BY userCount DESC")
    List<Object[]> getSizeDistributionByShoe(@Param("shoeId") Long shoeId);
    
    /**
     * 인기 사이즈 조회 (전체)
     */
    @Query("SELECT ucs.shoeSize, COUNT(ucs) as userCount FROM UserClimbingShoe ucs " +
           "GROUP BY ucs.shoeSize " +
           "ORDER BY userCount DESC")
    List<Object[]> getPopularSizes();
    
    // ===== 평점 및 리뷰 관련 =====
    
    /**
     * 신발별 평균 평점 계산
     */
    @Query("SELECT AVG(ucs.reviewRating) FROM UserClimbingShoe ucs " +
           "WHERE ucs.climbingShoe.shoeId = :shoeId AND ucs.reviewRating IS NOT NULL")
    Optional<Double> calculateAverageRatingByShoe(@Param("shoeId") Long shoeId);
    
    /**
     * 고평점 사용자 신발 조회 (평점 4점 이상)
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.climbingShoe cs " +
           "WHERE ucs.user.userId = :userId AND ucs.reviewRating >= 4 " +
           "ORDER BY ucs.reviewRating DESC")
    List<UserClimbingShoe> findHighRatedShoesByUser(@Param("userId") Long userId);
    
    /**
     * 신발별 좋은 리뷰 조회
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.user u " +
           "WHERE ucs.climbingShoe.shoeId = :shoeId " +
           "AND ucs.reviewRating >= :minRating " +
           "AND ucs.reviewText IS NOT NULL " +
           "ORDER BY ucs.reviewRating DESC")
    List<UserClimbingShoe> findGoodReviewsByShoe(@Param("shoeId") Long shoeId, 
                                                 @Param("minRating") Integer minRating);
    
    // ===== 추천 시스템 =====
    
    /**
     * 사용자별 신발 추천 (비슷한 취향의 사용자 기반)
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.climbingShoe cs " +
           "WHERE ucs.user.userId IN (" +
           "  SELECT ucs2.user.userId FROM UserClimbingShoe ucs2 " +
           "  WHERE ucs2.climbingShoe.shoeId IN (" +
           "    SELECT ucs3.climbingShoe.shoeId FROM UserClimbingShoe ucs3 " +
           "    WHERE ucs3.user.userId = :userId AND ucs3.reviewRating >= 4" +
           "  ) AND ucs2.user.userId != :userId AND ucs2.reviewRating >= 4" +
           ") " +
           "AND ucs.climbingShoe.shoeId NOT IN (" +
           "  SELECT ucs4.climbingShoe.shoeId FROM UserClimbingShoe ucs4 " +
           "  WHERE ucs4.user.userId = :userId" +
           ") " +
           "ORDER BY ucs.reviewRating DESC")
    List<UserClimbingShoe> findShoeRecommendationsForUser(@Param("userId") Long userId);
    
    /**
     * 유사한 신발 사용자 조회
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.user u " +
           "WHERE ucs.climbingShoe.shoeType = (" +
           "  SELECT cs.shoeType FROM ClimbingShoe cs " +
           "  JOIN UserClimbingShoe ucs2 ON cs.shoeId = ucs2.climbingShoe.shoeId " +
           "  WHERE ucs2.user.userId = :userId AND ucs2.isCurrentlyUsing = true " +
           "  ORDER BY ucs2.createdAt DESC LIMIT 1" +
           ") " +
           "AND ucs.user.userId != :userId " +
           "AND ucs.isCurrentlyUsing = true " +
           "ORDER BY ucs.reviewRating DESC")
    List<UserClimbingShoe> findUsersWithSimilarShoes(@Param("userId") Long userId);
    
    // ===== 통계 조회 =====
    
    /**
     * 사용자 신발 통계 요약
     */
    @Query("SELECT " +
           "COUNT(ucs) as totalShoes, " +
           "COUNT(CASE WHEN ucs.isCurrentlyUsing = true THEN 1 END) as activeShoes, " +
           "AVG(ucs.reviewRating) as avgRating, " +
           "COUNT(CASE WHEN ucs.reviewRating >= 4 THEN 1 END) as highRatedShoes " +
           "FROM UserClimbingShoe ucs " +
           "WHERE ucs.user.userId = :userId")
    List<Object[]> getUserShoeStatistics(@Param("userId") Long userId);
    
    /**
     * 브랜드별 사용자 선호도
     */
    @Query("SELECT cs.brand, COUNT(ucs) as userCount, AVG(ucs.reviewRating) as avgRating " +
           "FROM UserClimbingShoe ucs " +
           "JOIN ucs.climbingShoe cs " +
           "WHERE ucs.user.userId = :userId " +
           "GROUP BY cs.brand " +
           "ORDER BY avgRating DESC, userCount DESC")
    List<Object[]> getUserBrandPreferences(@Param("userId") Long userId);
    
    /**
     * 전체 신발 사용 통계
     */
    @Query("SELECT " +
           "COUNT(DISTINCT ucs.user.userId) as totalUsers, " +
           "COUNT(DISTINCT ucs.climbingShoe.shoeId) as uniqueShoes, " +
           "AVG(ucs.reviewRating) as avgRating " +
           "FROM UserClimbingShoe ucs")
    List<Object[]> getGlobalShoeUsageStatistics();
    
    // ===== 관리 메서드 =====
    
    /**
     * 사용자별 신발 수 조회
     */
    @Query("SELECT COUNT(ucs) FROM UserClimbingShoe ucs " +
           "WHERE ucs.user.userId = :userId")
    long countShoesByUserId(@Param("userId") Long userId);
    
    /**
     * 활성 신발 수 조회 (사용자별)
     */
    @Query("SELECT COUNT(ucs) FROM UserClimbingShoe ucs " +
           "WHERE ucs.user.userId = :userId AND ucs.isCurrentlyUsing = true")
    long countActiveShoesByUserId(@Param("userId") Long userId);
    
    /**
     * 리뷰가 있는 신발 조회
     */
    @Query("SELECT ucs FROM UserClimbingShoe ucs " +
           "JOIN FETCH ucs.climbingShoe cs " +
           "WHERE ucs.user.userId = :userId " +
           "AND ucs.reviewText IS NOT NULL " +
           "ORDER BY ucs.reviewRating DESC")
    List<UserClimbingShoe> findReviewedShoesByUser(@Param("userId") Long userId);
}
```

---

## 🎯 Repository 설계 특징

### 🎯 ClimbingLevelRepository 핵심 기능

#### 1. **다중 등급 시스템 지원**
- V등급, YDS, 프랑스 등급 통합 관리
- 등급 간 자동 변환 시스템
- 난이도 점수 기반 매핑

#### 2. **레벨 진행 시스템**
- 다음/이전 등급 조회
- N단계 앞선 등급 조회
- 레벨 진행 경로 추적

#### 3. **통계 및 분석**
- 등급별 루트 수 통계
- 사용자 분포 분석
- 성공률 기반 추천

### 👟 ClimbingShoeRepository 핵심 기능

#### 1. **신발 카탈로그 관리**
- 브랜드/모델별 체계적 분류
- 신발 타입별 카테고리화
- 가격대/스킬레벨별 필터링

#### 2. **인기도 및 평점 시스템**
- 사용자 수 기반 인기도 계산
- 평점 및 리뷰 수 종합 평가
- 최신 출시 모델 추적

#### 3. **검색 및 추천**
- 복합 조건 검색 지원
- 타겟 사용자별 추천
- 성별/스킬레벨 맞춤 추천

### 👟📝 UserClimbingShoeRepository 핵심 기능

#### 1. **개인 신발 프로필**
- 사용자별 신발 목록 관리
- 현재 사용 중인 신발 추적
- 신발 사이즈 및 리뷰 관리

#### 2. **소셜 기능**
- 같은 신발 사용자 찾기
- 신발별 사용자 커뮤니티
- 유사 취향 사용자 매칭

#### 3. **추천 알고리즘**
- 협업 필터링 기반 추천
- 사용자 취향 분석
- 브랜드 선호도 학습

---

## 📈 성능 최적화

### 💾 인덱스 전략
- 등급 조회: `(climbing_type, grade_order)`, `difficulty_score`
- 신발 검색: `(brand, model)`, `(shoe_type, popularity_score)`
- 사용자 신발: `(user_id, shoe_id)`, `(shoe_id, is_currently_using)`

### 🚀 캐싱 최적화
- 등급 변환표: 메모리 캐싱
- 인기 신발 리스트: Redis 캐싱 (1시간)
- 사용자 신발 프로필: 세션 캐싱

### 📊 통계 최적화
- 배치 집계: 일일 통계 업데이트
- 실시간 카운터: Redis Increment 사용
- 분석 쿼리: Read Replica 활용

---

**📝 다음 단계**: step5-3f2_user_activity_repositories.md에서 사용자 활동 추적 Repository 설계