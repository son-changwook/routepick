# Step5-3f2: User Activity Repositories (2/2)

> **클라이밍 기록 및 팔로우 관계 Repository**  
> 5단계 Repository 레이어 구현: 사용자 활동 추적 시스템

---

## 📋 파일 분할 정보
- **원본 파일**: step5-3f_climbing_activity_repositories.md (1,560줄)
- **분할 구성**: 2개 파일로 세분화
- **현재 파일**: step5-3f2_user_activity_repositories.md (2/2)
- **포함 Repository**: UserClimbRepository, UserFollowRepository

---

## 🎯 4. UserClimbRepository - 클라이밍 기록 Repository

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
import java.util.List;
import java.util.Optional;

/**
 * UserClimb Repository
 * - 🧗‍♂️ 클라이밍 기록 최적화
 * - 성과 추적 및 분석
 * - 개인 기록 관리
 * - 진행도 시각화
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

---

## ✅ 설계 완료 체크리스트

### 활동 추적 Repository (2개)  
- [x] **UserClimbRepository** - 클라이밍 기록 최적화 및 성과 분석
- [x] **UserFollowRepository** - 팔로우 관계 최적화 및 소셜 네트워크

### 전문 기능 구현
- [x] 클라이밍 패턴 분석 (요일별, 컨디션별)
- [x] 개인 기록 추적 (플래시, 온사이트, PR)
- [x] 상호 팔로우 및 추천 알고리즘
- [x] 소셜 네트워크 분석 및 관계 관리

### 성능 최적화
- [x] 클라이밍 기록 조회 인덱스 최적화
- [x] 팔로우 관계 양방향 검색 최적화  
- [x] @EntityGraph N+1 문제 해결
- [x] 복합 조건 검색 최적화

### 통계 및 분석 기능
- [x] 사용자별 클라이밍 진행도 분석
- [x] 월별/레벨별 성과 통계
- [x] 팔로우 네트워크 분석
- [x] 개인 기록 및 성과 추적

### 소셜 기능
- [x] 팔로우/팔로워 관리
- [x] 상호 팔로우 및 친한 친구
- [x] 팔로우 추천 시스템
- [x] 차단/음소거 관리

---

**분할 완료**: step5-3f_climbing_activity_repositories.md → step5-3f1 + step5-3f2  
**완료일**: 2025-08-20  
**핵심 성과**: UserClimb(클라이밍 기록) + UserFollow(팔로우 관계) Repository 완성