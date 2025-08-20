# Step 5-3e: 클라이밍 및 활동 Repository 생성

> 클라이밍 등급, 신발 관리, 활동 추적 5개 Repository 완전 설계  
> 생성일: 2025-08-20  
> 기반: step5-3d_route_interaction_repositories.md, step4-3c_climbing_activity_entities.md

---

## 🎯 설계 목표

- **등급 시스템 최적화**: V등급/YDS/프랑스 등급 통합 매핑 및 변환
- **신발 프로필 관리**: 사용자 신발 등록 및 프로필 노출 최적화
- **클라이밍 기록 분석**: 상세 기록 추적 및 진행도 분석
- **소셜 네트워크**: 팔로우 관계 및 추천 시스템 최적화

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

## 📈 4. UserClimbRepository - 클라이밍 기록 Repository

```java
package com.routepick.domain.activity.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.activity.entity.UserClimb;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UserClimb Repository
 * - 🏔️ 클라이밍 기록 최적화
 * - 상세 기록 추적 및 진행도 분석
 * - 사용자별 통계 및 성과 분석
 * - 개인화된 클라이밍 패턴 분석
 */
@Repository
public interface UserClimbRepository extends BaseRepository<UserClimb, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자별 최신 클라이밍 기록
     */
    @EntityGraph(attributePaths = {"route", "route.climbingLevel"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "ORDER BY uc.climbDate DESC, uc.createdAt DESC")
    List<UserClimb> findByUserIdOrderByClimbDateDesc(@Param("userId") Long userId);
    
    /**
     * 사용자별 클라이밍 기록 (페이징)
     */
    @EntityGraph(attributePaths = {"route", "route.climbingLevel"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "ORDER BY uc.climbDate DESC")
    Page<UserClimb> findByUserIdOrderByClimbDateDesc(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 기간별 클라이밍 기록
     */
    @EntityGraph(attributePaths = {"route"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "AND uc.climbDate BETWEEN :startDate AND :endDate " +
           "ORDER BY uc.climbDate DESC")
    List<UserClimb> findByUserIdAndClimbDateBetween(@Param("userId") Long userId,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);
    
    /**
     * 루트별 도전 기록
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.route.routeId = :routeId " +
           "ORDER BY uc.climbDate DESC")
    List<UserClimb> findByRouteIdOrderByClimbDateDesc(@Param("routeId") Long routeId);
    
    // ===== 성공/실패 기반 조회 =====
    
    /**
     * 성공한 클라이밍 기록만 조회
     */
    @EntityGraph(attributePaths = {"route", "route.climbingLevel"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId AND uc.isSuccessful = true " +
           "ORDER BY uc.climbDate DESC")
    List<UserClimb> findSuccessfulClimbsByUser(@Param("userId") Long userId);
    
    /**
     * 성공률 기준 조회
     */
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "AND (CAST(uc.isSuccessful AS int) * 100.0 / uc.attemptCount) >= :minSuccessRate " +
           "ORDER BY uc.climbDate DESC")
    List<UserClimb> findByUserIdAndSuccessRateGreaterThan(@Param("userId") Long userId,
                                                         @Param("minSuccessRate") Float minSuccessRate);
    
    /**
     * 플래시/온사이트 기록 조회
     */
    @EntityGraph(attributePaths = {"route", "route.climbingLevel"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "AND uc.climbType IN ('FLASH', 'ONSIGHT') " +
           "AND uc.isSuccessful = true " +
           "ORDER BY uc.climbDate DESC")
    List<UserClimb> findFlashAndOnsightClimbs(@Param("userId") Long userId);
    
    // ===== 통계 계산 메서드 =====
    
    /**
     * 사용자 클라이밍 통계 계산
     */
    @Query("SELECT " +
           "COUNT(uc) as totalClimbs, " +
           "COUNT(CASE WHEN uc.isSuccessful = true THEN 1 END) as successfulClimbs, " +
           "AVG(uc.attemptCount) as avgAttempts, " +
           "AVG(CASE WHEN uc.difficultyRating IS NOT NULL THEN uc.difficultyRating END) as avgDifficultyRating, " +
           "COUNT(CASE WHEN uc.personalRecord = true THEN 1 END) as personalRecords " +
           "FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId")
    List<Object[]> calculateUserStatistics(@Param("userId") Long userId);
    
    /**
     * 월별 클라이밍 진행도
     */
    @Query("SELECT " +
           "EXTRACT(YEAR FROM uc.climbDate) as year, " +
           "EXTRACT(MONTH FROM uc.climbDate) as month, " +
           "COUNT(uc) as totalClimbs, " +
           "COUNT(CASE WHEN uc.isSuccessful = true THEN 1 END) as successfulClimbs, " +
           "AVG(CASE WHEN uc.route.climbingLevel.difficultyScore IS NOT NULL " +
           "    THEN uc.route.climbingLevel.difficultyScore END) as avgDifficulty " +
           "FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "AND uc.climbDate >= :startDate " +
           "GROUP BY EXTRACT(YEAR FROM uc.climbDate), EXTRACT(MONTH FROM uc.climbDate) " +
           "ORDER BY year DESC, month DESC")
    List<Object[]> calculateMonthlyProgress(@Param("userId") Long userId,
                                          @Param("startDate") LocalDate startDate);
    
    /**
     * 레벨별 성과 분석
     */
    @Query("SELECT " +
           "cl.vGrade, " +
           "COUNT(uc) as totalAttempts, " +
           "COUNT(CASE WHEN uc.isSuccessful = true THEN 1 END) as successfulClimbs, " +
           "AVG(uc.attemptCount) as avgAttempts " +
           "FROM UserClimb uc " +
           "JOIN uc.route.climbingLevel cl " +
           "WHERE uc.user.userId = :userId " +
           "GROUP BY cl.levelId, cl.vGrade " +
           "ORDER BY cl.difficultyScore ASC")
    List<Object[]> findUserLevelAnalysis(@Param("userId") Long userId);
    
    // ===== 개인 기록 및 성과 =====
    
    /**
     * 최근 성과 조회 (개인 기록, 플래시 등)
     */
    @EntityGraph(attributePaths = {"route", "route.climbingLevel"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "AND (uc.personalRecord = true OR uc.climbType IN ('FLASH', 'ONSIGHT')) " +
           "AND uc.climbDate >= :sinceDate " +
           "ORDER BY uc.climbDate DESC")
    List<UserClimb> findRecentAchievements(@Param("userId") Long userId,
                                          @Param("sinceDate") LocalDate sinceDate);
    
    /**
     * 개인 최고 기록 조회
     */
    @EntityGraph(attributePaths = {"route", "route.climbingLevel"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId AND uc.personalRecord = true " +
           "ORDER BY uc.route.climbingLevel.difficultyScore DESC, uc.climbDate DESC")
    List<UserClimb> findPersonalBests(@Param("userId") Long userId);
    
    /**
     * 가장 어려운 완등 기록
     */
    @EntityGraph(attributePaths = {"route", "route.climbingLevel"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId AND uc.isSuccessful = true " +
           "ORDER BY uc.route.climbingLevel.difficultyScore DESC")
    Optional<UserClimb> findHardestSuccessfulClimb(@Param("userId") Long userId);
    
    /**
     * 최소 시도로 완등한 기록
     */
    @EntityGraph(attributePaths = {"route", "route.climbingLevel"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId AND uc.isSuccessful = true " +
           "ORDER BY uc.attemptCount ASC, uc.route.climbingLevel.difficultyScore DESC")
    List<UserClimb> findMostEfficientClimbs(@Param("userId") Long userId);
    
    // ===== 클라이밍 패턴 분석 =====
    
    /**
     * 사용자 클라이밍 패턴 분석
     */
    @Query("SELECT " +
           "uc.physicalCondition, " +
           "COUNT(uc) as climbCount, " +
           "AVG(CASE WHEN uc.isSuccessful = true THEN 1.0 ELSE 0.0 END) as successRate " +
           "FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "AND uc.physicalCondition IS NOT NULL " +
           "GROUP BY uc.physicalCondition " +
           "ORDER BY successRate DESC")
    List<Object[]> findClimbingPatterns(@Param("userId") Long userId);
    
    /**
     * 요일별 클라이밍 패턴
     */
    @Query("SELECT " +
           "EXTRACT(DOW FROM uc.climbDate) as dayOfWeek, " +
           "COUNT(uc) as climbCount, " +
           "AVG(CASE WHEN uc.isSuccessful = true THEN 1.0 ELSE 0.0 END) as successRate " +
           "FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "GROUP BY EXTRACT(DOW FROM uc.climbDate) " +
           "ORDER BY dayOfWeek")
    List<Object[]> findWeeklyClimbingPatterns(@Param("userId") Long userId);
    
    /**
     * 실력 향상 추이 분석
     */
    @Query("SELECT " +
           "DATE_TRUNC('month', uc.climbDate) as month, " +
           "MAX(uc.route.climbingLevel.difficultyScore) as maxDifficulty, " +
           "AVG(CASE WHEN uc.isSuccessful = true " +
           "    THEN uc.route.climbingLevel.difficultyScore END) as avgSuccessfulDifficulty " +
           "FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "AND uc.climbDate >= :startDate " +
           "GROUP BY DATE_TRUNC('month', uc.climbDate) " +
           "ORDER BY month ASC")
    List<Object[]> findClimbingProgressByUser(@Param("userId") Long userId,
                                             @Param("startDate") LocalDate startDate);
    
    // ===== 루트 및 장소별 분석 =====
    
    /**
     * 사용자의 선호 암장 분석
     */
    @Query("SELECT " +
           "uc.branchId, " +
           "COUNT(uc) as visitCount, " +
           "AVG(CASE WHEN uc.isSuccessful = true THEN 1.0 ELSE 0.0 END) as successRate " +
           "FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId AND uc.branchId IS NOT NULL " +
           "GROUP BY uc.branchId " +
           "ORDER BY visitCount DESC")
    List<Object[]> findPreferredGyms(@Param("userId") Long userId);
    
    /**
     * 루트별 재도전 기록
     */
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId AND uc.route.routeId = :routeId " +
           "ORDER BY uc.climbDate ASC")
    List<UserClimb> findRetryHistory(@Param("userId") Long userId, @Param("routeId") Long routeId);
    
    /**
     * 사용자별 완등한 고유 루트 수
     */
    @Query("SELECT COUNT(DISTINCT uc.route.routeId) FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId AND uc.isSuccessful = true")
    long countUniqueSuccessfulRoutes(@Param("userId") Long userId);
    
    // ===== 소셜 및 공유 기능 =====
    
    /**
     * 공개된 클라이밍 기록 조회
     */
    @EntityGraph(attributePaths = {"route", "user"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.isPublic = true AND uc.sharedWithCommunity = true " +
           "ORDER BY uc.climbDate DESC")
    List<UserClimb> findPublicClimbs(Pageable pageable);
    
    /**
     * 팔로잉 사용자들의 최근 클라이밍 기록
     */
    @EntityGraph(attributePaths = {"route", "user"})
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId IN (" +
           "  SELECT uf.followingUser.userId FROM UserFollow uf " +
           "  WHERE uf.followerUser.userId = :userId AND uf.isActive = true" +
           ") " +
           "AND uc.isPublic = true " +
           "ORDER BY uc.climbDate DESC")
    List<UserClimb> findFollowingClimbs(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 복합 조건 클라이밍 기록 검색
     */
    @Query("SELECT uc FROM UserClimb uc " +
           "WHERE uc.user.userId = :userId " +
           "AND (:startDate IS NULL OR uc.climbDate >= :startDate) " +
           "AND (:endDate IS NULL OR uc.climbDate <= :endDate) " +
           "AND (:isSuccessful IS NULL OR uc.isSuccessful = :isSuccessful) " +
           "AND (:climbType IS NULL OR uc.climbType = :climbType) " +
           "AND (:minDifficulty IS NULL OR uc.route.climbingLevel.difficultyScore >= :minDifficulty) " +
           "ORDER BY uc.climbDate DESC")
    Page<UserClimb> findByComplexConditions(@Param("userId") Long userId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate,
                                           @Param("isSuccessful") Boolean isSuccessful,
                                           @Param("climbType") String climbType,
                                           @Param("minDifficulty") java.math.BigDecimal minDifficulty,
                                           Pageable pageable);
    
    // ===== 관리 및 통계 =====
    
    /**
     * 사용자별 클라이밍 기록 수
     */
    @Query("SELECT COUNT(uc) FROM UserClimb uc WHERE uc.user.userId = :userId")
    long countClimbsByUserId(@Param("userId") Long userId);
    
    /**
     * 루트별 도전 횟수
     */
    @Query("SELECT COUNT(uc) FROM UserClimb uc WHERE uc.route.routeId = :routeId")
    long countClimbsByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 최근 활발한 클라이머 조회
     */
    @Query("SELECT uc.user.userId, COUNT(uc) as climbCount FROM UserClimb uc " +
           "WHERE uc.climbDate >= :sinceDate " +
           "GROUP BY uc.user.userId " +
           "ORDER BY climbCount DESC")
    List<Object[]> findActiveClimbers(@Param("sinceDate") LocalDate sinceDate);
}
```

---

## 👥 5. UserFollowRepository - 팔로우 관계 Repository

```java
package com.routepick.domain.activity.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.activity.entity.UserFollow;
import com.routepick.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UserFollow Repository
 * - 👥 팔로우 관계 최적화
 * - 소셜 네트워크 분석
 * - 팔로우 추천 시스템
 * - 상호작용 기반 관계 관리
 */
@Repository
public interface UserFollowRepository extends BaseRepository<UserFollow, Long> {
    
    // ===== 기본 팔로우 관계 조회 =====
    
    /**
     * 팔로잉 목록 조회 (내가 팔로우하는 사람들)
     */
    @EntityGraph(attributePaths = {"followingUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId AND uf.isActive = true " +
           "ORDER BY uf.followDate DESC")
    List<UserFollow> findByFollowerUserIdOrderByFollowDateDesc(@Param("userId") Long userId);
    
    /**
     * 팔로워 목록 조회 (나를 팔로우하는 사람들)
     */
    @EntityGraph(attributePaths = {"followerUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followingUser.userId = :userId AND uf.isActive = true " +
           "ORDER BY uf.followDate DESC")
    List<UserFollow> findByFollowingUserIdOrderByFollowDateDesc(@Param("userId") Long userId);
    
    /**
     * 팔로우 관계 확인
     */
    @Query("SELECT COUNT(uf) > 0 FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :followerId " +
           "AND uf.followingUser.userId = :followingId " +
           "AND uf.isActive = true")
    boolean existsByFollowerUserIdAndFollowingUserId(@Param("followerId") Long followerId,
                                                    @Param("followingId") Long followingId);
    
    /**
     * 특정 팔로우 관계 조회
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :followerId " +
           "AND uf.followingUser.userId = :followingId")
    Optional<UserFollow> findByFollowerUserIdAndFollowingUserId(@Param("followerId") Long followerId,
                                                               @Param("followingId") Long followingId);
    
    // ===== 상호 팔로우 관리 =====
    
    /**
     * 상호 팔로우 목록 조회
     */
    @EntityGraph(attributePaths = {"followingUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId " +
           "AND uf.isActive = true AND uf.isMutual = true " +
           "ORDER BY uf.lastInteractionDate DESC")
    List<UserFollow> findMutualFollows(@Param("userId") Long userId);
    
    /**
     * 상호 팔로우 여부 확인
     */
    @Query("SELECT uf1.isMutual FROM UserFollow uf1 " +
           "WHERE uf1.followerUser.userId = :user1Id " +
           "AND uf1.followingUser.userId = :user2Id " +
           "AND EXISTS (" +
           "  SELECT 1 FROM UserFollow uf2 " +
           "  WHERE uf2.followerUser.userId = :user2Id " +
           "  AND uf2.followingUser.userId = :user1Id " +
           "  AND uf2.isActive = true" +
           ") AND uf1.isActive = true")
    Optional<Boolean> checkMutualFollow(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);
    
    /**
     * 친한 친구 목록 조회
     */
    @EntityGraph(attributePaths = {"followingUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId " +
           "AND uf.isActive = true AND uf.closeFriend = true " +
           "ORDER BY uf.lastInteractionDate DESC")
    List<UserFollow> findCloseFriends(@Param("userId") Long userId);
    
    // ===== 팔로우 수 통계 =====
    
    /**
     * 팔로잉 수 조회
     */
    @Query("SELECT COUNT(uf) FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId AND uf.isActive = true")
    long countByFollowerUserId(@Param("userId") Long userId);
    
    /**
     * 팔로워 수 조회
     */
    @Query("SELECT COUNT(uf) FROM UserFollow uf " +
           "WHERE uf.followingUser.userId = :userId AND uf.isActive = true")
    long countByFollowingUserId(@Param("userId") Long userId);
    
    /**
     * 상호 팔로우 수 조회
     */
    @Query("SELECT COUNT(uf) FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId " +
           "AND uf.isActive = true AND uf.isMutual = true")
    long countMutualFollows(@Param("userId") Long userId);
    
    // ===== 팔로우 추천 시스템 =====
    
    /**
     * 팔로우 추천 (친구의 친구 기반)
     */
    @EntityGraph(attributePaths = {"followingUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId IN (" +
           "  SELECT uf2.followingUser.userId FROM UserFollow uf2 " +
           "  WHERE uf2.followerUser.userId = :userId AND uf2.isActive = true" +
           ") " +
           "AND uf.followingUser.userId != :userId " +
           "AND uf.isActive = true " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM UserFollow uf3 " +
           "  WHERE uf3.followerUser.userId = :userId " +
           "  AND uf3.followingUser.userId = uf.followingUser.userId" +
           ") " +
           "GROUP BY uf.followingUser.userId, uf.followingUser " +
           "ORDER BY COUNT(uf) DESC")
    List<UserFollow> findRecommendedFollows(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 유사한 사용자 팔로잉 (공통 관심사 기반)
     */
    @Query("SELECT DISTINCT uf2.followingUser FROM UserFollow uf1 " +
           "JOIN UserFollow uf2 ON uf1.followingUser.userId = uf2.followerUser.userId " +
           "WHERE uf1.followerUser.userId = :userId " +
           "AND uf2.followingUser.userId != :userId " +
           "AND uf1.isActive = true AND uf2.isActive = true " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM UserFollow uf3 " +
           "  WHERE uf3.followerUser.userId = :userId " +
           "  AND uf3.followingUser.userId = uf2.followingUser.userId" +
           ") " +
           "ORDER BY uf2.followingUser.nickName")
    List<User> findFollowingSimilarUsers(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 영향력 있는 사용자 조회 (팔로워 수 기준)
     */
    @Query("SELECT uf.followingUser, COUNT(uf) as followerCount FROM UserFollow uf " +
           "WHERE uf.isActive = true " +
           "AND uf.followingUser.userId != :userId " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM UserFollow uf2 " +
           "  WHERE uf2.followerUser.userId = :userId " +
           "  AND uf2.followingUser.userId = uf.followingUser.userId" +
           ") " +
           "GROUP BY uf.followingUser " +
           "ORDER BY followerCount DESC")
    List<Object[]> findInfluentialUsers(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 활동 기반 분석 =====
    
    /**
     * 활성 팔로워 조회 (최근 상호작용 기준)
     */
    @EntityGraph(attributePaths = {"followerUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followingUser.userId = :userId " +
           "AND uf.isActive = true " +
           "AND uf.lastInteractionDate >= :sinceDate " +
           "ORDER BY uf.lastInteractionDate DESC")
    List<UserFollow> findActiveFollowers(@Param("userId") Long userId,
                                        @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * 팔로우 네트워크 통계
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN uf.followerUser.userId = :userId THEN 1 END) as followingCount, " +
           "COUNT(CASE WHEN uf.followingUser.userId = :userId THEN 1 END) as followerCount, " +
           "COUNT(CASE WHEN uf.followerUser.userId = :userId AND uf.isMutual = true THEN 1 END) as mutualCount, " +
           "AVG(uf.interactionCount) as avgInteractions " +
           "FROM UserFollow uf " +
           "WHERE (uf.followerUser.userId = :userId OR uf.followingUser.userId = :userId) " +
           "AND uf.isActive = true")
    List<Object[]> calculateFollowNetworkStats(@Param("userId") Long userId);
    
    /**
     * 팔로우 동향 분석 (시간별)
     */
    @Query("SELECT " +
           "DATE_TRUNC('month', uf.followDate) as month, " +
           "COUNT(uf) as newFollows " +
           "FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId " +
           "AND uf.followDate >= :startDate " +
           "GROUP BY DATE_TRUNC('month', uf.followDate) " +
           "ORDER BY month DESC")
    List<Object[]> findFollowTrends(@Param("userId") Long userId,
                                   @Param("startDate") LocalDateTime startDate);
    
    // ===== 관계 유형별 조회 =====
    
    /**
     * 관계 유형별 팔로우 조회
     */
    @EntityGraph(attributePaths = {"followingUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId " +
           "AND uf.relationshipType = :relationshipType " +
           "AND uf.isActive = true " +
           "ORDER BY uf.lastInteractionDate DESC")
    List<UserFollow> findByRelationshipType(@Param("userId") Long userId,
                                           @Param("relationshipType") String relationshipType);
    
    /**
     * 클라이밍 파트너 조회
     */
    @EntityGraph(attributePaths = {"followingUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId " +
           "AND uf.relationshipType = 'CLIMBING_PARTNER' " +
           "AND uf.isActive = true " +
           "ORDER BY uf.mutualClimbCount DESC")
    List<UserFollow> findClimbingPartners(@Param("userId") Long userId);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 사용자명으로 팔로잉 검색
     */
    @EntityGraph(attributePaths = {"followingUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId " +
           "AND (uf.followingUser.nickName LIKE %:keyword% " +
           "     OR uf.nickname LIKE %:keyword%) " +
           "AND uf.isActive = true " +
           "ORDER BY uf.lastInteractionDate DESC")
    List<UserFollow> searchFollowing(@Param("userId") Long userId, 
                                    @Param("keyword") String keyword);
    
    /**
     * 복합 조건 팔로우 검색
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId " +
           "AND (:relationshipType IS NULL OR uf.relationshipType = :relationshipType) " +
           "AND (:isMutual IS NULL OR uf.isMutual = :isMutual) " +
           "AND (:isCloseFriend IS NULL OR uf.closeFriend = :isCloseFriend) " +
           "AND uf.isActive = true " +
           "ORDER BY uf.lastInteractionDate DESC")
    Page<UserFollow> findByComplexConditions(@Param("userId") Long userId,
                                            @Param("relationshipType") String relationshipType,
                                            @Param("isMutual") Boolean isMutual,
                                            @Param("isCloseFriend") Boolean isCloseFriend,
                                            Pageable pageable);
    
    // ===== 관리 메서드 =====
    
    /**
     * 차단된 사용자 조회
     */
    @EntityGraph(attributePaths = {"followingUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId AND uf.blocked = true " +
           "ORDER BY uf.unfollowDate DESC")
    List<UserFollow> findBlockedUsers(@Param("userId") Long userId);
    
    /**
     * 음소거된 사용자 조회
     */
    @EntityGraph(attributePaths = {"followingUser"})
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId AND uf.muted = true " +
           "AND uf.isActive = true " +
           "ORDER BY uf.followDate DESC")
    List<UserFollow> findMutedUsers(@Param("userId") Long userId);
    
    /**
     * 비활성 팔로우 관계 조회
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId AND uf.isActive = false " +
           "ORDER BY uf.unfollowDate DESC")
    List<UserFollow> findInactiveFollows(@Param("userId") Long userId);
    
    /**
     * 전체 팔로우 통계
     */
    @Query("SELECT " +
           "COUNT(DISTINCT uf.followerUser.userId) as totalUsers, " +
           "COUNT(uf) as totalFollows, " +
           "COUNT(CASE WHEN uf.isMutual = true THEN 1 END) as mutualFollows, " +
           "AVG(uf.interactionCount) as avgInteractions " +
           "FROM UserFollow uf " +
           "WHERE uf.isActive = true")
    List<Object[]> getGlobalFollowStatistics();
}
```

---

## ⚡ 6. 성능 최적화 전략

### 클라이밍 기록 최적화
```sql
-- 사용자별 클라이밍 기록 조회 최적화
CREATE INDEX idx_climb_user_date_success 
ON user_climbs(user_id, climb_date DESC, is_successful);

-- 루트별 도전 기록 최적화
CREATE INDEX idx_climb_route_date 
ON user_climbs(route_id, climb_date DESC);

-- 성과 분석용 인덱스
CREATE INDEX idx_climb_personal_record 
ON user_climbs(user_id, personal_record, climb_date DESC);
```

### 팔로우 관계 최적화
```sql
-- 양방향 팔로우 관계 검색 최적화
CREATE INDEX idx_follow_bidirectional 
ON user_follows(follower_user_id, following_user_id, is_active);

-- 상호 팔로우 조회 최적화
CREATE INDEX idx_follow_mutual_interaction 
ON user_follows(is_mutual, is_active, last_interaction_date DESC);

-- 팔로우 추천용 인덱스
CREATE INDEX idx_follow_recommendation 
ON user_follows(following_user_id, is_active, follow_date DESC);
```

### 신발 프로필 최적화
```sql
-- 사용자 신발 프로필 조회 최적화
CREATE INDEX idx_user_shoe_profile 
ON user_climbing_shoes(user_id, is_currently_using, created_at DESC);

-- 신발별 사용자 통계 최적화
CREATE INDEX idx_shoe_user_stats 
ON user_climbing_shoes(shoe_id, review_rating DESC);
```

---

## ✅ 설계 완료 체크리스트

### 클라이밍 관련 Repository (3개)
- [x] **ClimbingLevelRepository** - V등급/YDS/프랑스 등급 통합 매핑 시스템
- [x] **ClimbingShoeRepository** - 신발 프로필 노출용 간단 관리
- [x] **UserClimbingShoeRepository** - 사용자 신발 등록 및 프로필 관리

### 활동 추적 Repository (2개)  
- [x] **UserClimbRepository** - 클라이밍 기록 최적화 및 성과 분석
- [x] **UserFollowRepository** - 팔로우 관계 최적화 및 소셜 네트워크

### 전문 기능 구현
- [x] 등급 변환 시스템 (V등급 ↔ YDS ↔ 프랑스)
- [x] 레벨 진행도 추적 및 분석
- [x] 클라이밍 패턴 분석 (요일별, 컨디션별)
- [x] 상호 팔로우 및 추천 알고리즘

### 성능 최적화
- [x] 클라이밍 기록 조회 인덱스 최적화
- [x] 팔로우 관계 양방향 검색 최적화  
- [x] 신발 프로필 조회 최적화
- [x] @EntityGraph N+1 문제 해결

### 통계 및 분석 기능
- [x] 사용자별 클라이밍 진행도 분석
- [x] 월별/레벨별 성과 통계
- [x] 팔로우 네트워크 분석
- [x] 개인 기록 및 성과 추적

---

**다음 단계**: Step 5-4 Community 도메인 Repository 설계  
**완료일**: 2025-08-20  
**핵심 성과**: 5개 클라이밍/활동 Repository + 등급 변환 시스템 + 성과 분석 완료