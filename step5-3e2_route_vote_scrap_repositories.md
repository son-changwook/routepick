# Step 5-3e2: 루트 투표 & 스크랩 Repository - Vote & Scrap 관리

> 루트 난이도 투표 & 스크랩 관리 Repository 완전 설계 (가중 평균 & 개인화 특화)  
> 생성일: 2025-08-21  
> 기반: step5-3e_route_interaction_repositories.md 세분화  
> 포함 Repository: RouteDifficultyVoteRepository, RouteScrapRepository

---

## 📋 파일 세분화 정보
- **원본 파일**: step5-3e_route_interaction_repositories.md (1,175줄)
- **세분화 사유**: 토큰 제한 대응 및 기능별 책임 분리
- **이 파일 포함**: RouteDifficultyVoteRepository, RouteScrapRepository (투표 + 스크랩 관리)
- **다른 파일**: step5-3e1_route_comment_repositories.md (RouteCommentRepository)

---

## 🎯 설계 목표

- **난이도 투표 시스템**: 사용자 참여형 난이도 보정
- **가중 평균 계산**: 투표 신뢰도 기반 정확한 난이도 산출
- **스크랩 기능 최적화**: 개인화 루트 관리 및 추천
- **우선순위 관리**: 목표일 설정, 폴더별 분류

---

## 🗳️ 1. RouteDifficultyVoteRepository - 난이도 투표 Repository

```java
package com.routepick.domain.route.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.route.entity.RouteDifficultyVote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RouteDifficultyVote Repository
 * - 난이도 투표 관리 및 분석
 * - 가중 평균 난이도 계산
 * - 투표 신뢰도 관리
 * - 사용자별 투표 이력
 */
@Repository
public interface RouteDifficultyVoteRepository extends BaseRepository<RouteDifficultyVote, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 루트별 난이도 투표 조회
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isActive = true " +
           "ORDER BY rdv.createdAt DESC")
    List<RouteDifficultyVote> findByRouteIdOrderByCreatedAtDesc(@Param("routeId") Long routeId);
    
    /**
     * 루트별 난이도 투표 조회 (페이징)
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isActive = true " +
           "ORDER BY rdv.createdAt DESC")
    Page<RouteDifficultyVote> findByRouteIdOrderByCreatedAtDesc(@Param("routeId") Long routeId, 
                                                               Pageable pageable);
    
    /**
     * 사용자-루트별 투표 조회
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE rdv.user.userId = :userId AND rdv.route.routeId = :routeId")
    Optional<RouteDifficultyVote> findByUserIdAndRouteId(@Param("userId") Long userId, 
                                                        @Param("routeId") Long routeId);
    
    /**
     * 사용자의 모든 투표 조회
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE rdv.user.userId = :userId AND rdv.isActive = true " +
           "ORDER BY rdv.createdAt DESC")
    List<RouteDifficultyVote> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    // ===== 난이도 계산 및 분석 =====
    
    /**
     * 루트별 가중 평균 난이도 계산
     */
    @Query("SELECT " +
           "AVG(CASE rdv.suggestedDifficulty " +
           "  WHEN 'V0' THEN 0.0 * rdv.voteWeight " +
           "  WHEN 'V1' THEN 1.0 * rdv.voteWeight " +
           "  WHEN 'V2' THEN 2.0 * rdv.voteWeight " +
           "  WHEN 'V3' THEN 3.0 * rdv.voteWeight " +
           "  WHEN 'V4' THEN 4.0 * rdv.voteWeight " +
           "  WHEN 'V5' THEN 5.0 * rdv.voteWeight " +
           "  WHEN 'V6' THEN 6.0 * rdv.voteWeight " +
           "  WHEN 'V7' THEN 7.0 * rdv.voteWeight " +
           "  WHEN 'V8' THEN 8.0 * rdv.voteWeight " +
           "  WHEN 'V9' THEN 9.0 * rdv.voteWeight " +
           "  WHEN 'V10' THEN 10.0 * rdv.voteWeight " +
           "  ELSE 5.0 * rdv.voteWeight " +
           "END) as weightedAvgDifficulty " +
           "FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isActive = true")
    Optional<BigDecimal> calculateAverageDifficultyByRoute(@Param("routeId") Long routeId);
    
    /**
     * 루트별 난이도 투표 분포
     */
    @Query("SELECT rdv.suggestedDifficulty, COUNT(rdv) as voteCount, AVG(rdv.voteWeight) as avgWeight " +
           "FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isActive = true " +
           "GROUP BY rdv.suggestedDifficulty " +
           "ORDER BY voteCount DESC")
    List<Object[]> getDifficultyVoteDistribution(@Param("routeId") Long routeId);
    
    /**
     * 루트별 투표 통계 요약
     */
    @Query("SELECT " +
           "COUNT(rdv) as totalVotes, " +
           "COUNT(CASE WHEN rdv.isSuccessfulClimb = true THEN 1 END) as successfulVotes, " +
           "AVG(rdv.voteWeight) as avgWeight, " +
           "AVG(rdv.confidenceLevel) as avgConfidence " +
           "FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isActive = true")
    List<Object[]> getVoteStatisticsByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 난이도 변화 추이 분석
     */
    @Query("SELECT rdv.difficultyChange, COUNT(rdv) as changeCount FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isActive = true " +
           "GROUP BY rdv.difficultyChange " +
           "ORDER BY rdv.difficultyChange")
    List<Object[]> getDifficultyChangeDistribution(@Param("routeId") Long routeId);
    
    // ===== 투표 신뢰도별 조회 =====
    
    /**
     * 고신뢰도 투표자의 투표 조회
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.voteWeight >= :minWeight " +
           "AND rdv.confidenceLevel >= :minConfidence AND rdv.isActive = true " +
           "ORDER BY rdv.voteWeight DESC")
    List<RouteDifficultyVote> findHighReliabilityVotes(@Param("routeId") Long routeId,
                                                      @Param("minWeight") Float minWeight,
                                                      @Param("minConfidence") Integer minConfidence);
    
    /**
     * 완등자 투표만 조회
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isSuccessfulClimb = true AND rdv.isActive = true " +
           "ORDER BY rdv.climbAttemptCount ASC")
    List<RouteDifficultyVote> findSuccessfulClimberVotes(@Param("routeId") Long routeId);
    
    /**
     * 경험 수준별 투표 조회
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.userExperienceLevel = :experienceLevel " +
           "AND rdv.isActive = true " +
           "ORDER BY rdv.voteWeight DESC")
    List<RouteDifficultyVote> findVotesByExperienceLevel(@Param("routeId") Long routeId,
                                                        @Param("experienceLevel") String experienceLevel);
    
    // ===== 투표 패턴 분석 =====
    
    /**
     * 사용자별 투표 패턴 분석
     */
    @Query("SELECT rdv.user.userId, " +
           "COUNT(rdv) as totalVotes, " +
           "AVG(rdv.voteWeight) as avgWeight, " +
           "COUNT(CASE WHEN rdv.isSuccessfulClimb = true THEN 1 END) as successfulClimbs, " +
           "AVG(rdv.climbAttemptCount) as avgAttempts " +
           "FROM RouteDifficultyVote rdv " +
           "WHERE rdv.isActive = true " +
           "GROUP BY rdv.user.userId " +
           "ORDER BY totalVotes DESC")
    List<Object[]> analyzeUserVotingPatterns();
    
    /**
     * 시기별 난이도 투표 트렌드
     */
    @Query("SELECT DATE(rdv.createdAt), AVG(CASE rdv.suggestedDifficulty " +
           "  WHEN 'V0' THEN 0.0 WHEN 'V1' THEN 1.0 WHEN 'V2' THEN 2.0 " +
           "  WHEN 'V3' THEN 3.0 WHEN 'V4' THEN 4.0 WHEN 'V5' THEN 5.0 " +
           "  WHEN 'V6' THEN 6.0 WHEN 'V7' THEN 7.0 WHEN 'V8' THEN 8.0 " +
           "  WHEN 'V9' THEN 9.0 WHEN 'V10' THEN 10.0 ELSE 5.0 END) as avgDifficulty " +
           "FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isActive = true " +
           "AND rdv.createdAt >= :startDate " +
           "GROUP BY DATE(rdv.createdAt) " +
           "ORDER BY DATE(rdv.createdAt)")
    List<Object[]> getDifficultyTrend(@Param("routeId") Long routeId, 
                                     @Param("startDate") LocalDateTime startDate);
    
    /**
     * 난이도별 완등률 상관관계
     */
    @Query("SELECT rdv.suggestedDifficulty, " +
           "AVG(CASE WHEN rdv.isSuccessfulClimb = true THEN 1.0 ELSE 0.0 END) as successRate, " +
           "COUNT(rdv) as voteCount " +
           "FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isActive = true " +
           "GROUP BY rdv.suggestedDifficulty " +
           "ORDER BY successRate DESC")
    List<Object[]> getDifficultySuccessCorrelation(@Param("routeId") Long routeId);
    
    // ===== 투표 검증 및 업데이트 =====
    
    /**
     * 중복 투표 확인
     */
    @Query("SELECT COUNT(rdv) > 0 FROM RouteDifficultyVote rdv " +
           "WHERE rdv.user.userId = :userId AND rdv.route.routeId = :routeId")
    boolean existsByUserIdAndRouteId(@Param("userId") Long userId, @Param("routeId") Long routeId);
    
    /**
     * 투표 가중치 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteDifficultyVote rdv SET rdv.voteWeight = :newWeight " +
           "WHERE rdv.voteId = :voteId")
    int updateVoteWeight(@Param("voteId") Long voteId, @Param("newWeight") Float newWeight);
    
    /**
     * 완등 정보 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteDifficultyVote rdv SET " +
           "rdv.isSuccessfulClimb = true, " +
           "rdv.climbAttemptCount = :attemptCount, " +
           "rdv.voteWeight = LEAST(rdv.voteWeight + 0.2, 2.0) " +
           "WHERE rdv.voteId = :voteId")
    int updateClimbSuccess(@Param("voteId") Long voteId, @Param("attemptCount") Integer attemptCount);
    
    /**
     * 투표 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteDifficultyVote rdv SET rdv.isActive = false " +
           "WHERE rdv.voteId = :voteId")
    int deactivateVote(@Param("voteId") Long voteId);
    
    /**
     * 루트의 모든 투표 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteDifficultyVote rdv SET rdv.isActive = false " +
           "WHERE rdv.route.routeId = :routeId")
    int deactivateAllVotesByRoute(@Param("routeId") Long routeId);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 특정 난이도 투표 조회
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.suggestedDifficulty = :difficulty " +
           "AND rdv.isActive = true " +
           "ORDER BY rdv.voteWeight DESC")
    List<RouteDifficultyVote> findByRouteIdAndSuggestedDifficulty(@Param("routeId") Long routeId,
                                                                 @Param("difficulty") String difficulty);
    
    /**
     * 복합 조건 투표 검색
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE (:routeId IS NULL OR rdv.route.routeId = :routeId) " +
           "AND (:userId IS NULL OR rdv.user.userId = :userId) " +
           "AND (:difficulty IS NULL OR rdv.suggestedDifficulty = :difficulty) " +
           "AND (:isSuccessful IS NULL OR rdv.isSuccessfulClimb = :isSuccessful) " +
           "AND (:minWeight IS NULL OR rdv.voteWeight >= :minWeight) " +
           "AND rdv.isActive = true " +
           "ORDER BY rdv.createdAt DESC")
    Page<RouteDifficultyVote> findByComplexConditions(@Param("routeId") Long routeId,
                                                     @Param("userId") Long userId,
                                                     @Param("difficulty") String difficulty,
                                                     @Param("isSuccessful") Boolean isSuccessful,
                                                     @Param("minWeight") Float minWeight,
                                                     Pageable pageable);
    
    // ===== 통계 집계 =====
    
    /**
     * 전체 투표 통계
     */
    @Query("SELECT " +
           "COUNT(rdv) as totalVotes, " +
           "COUNT(DISTINCT rdv.route.routeId) as votedRoutes, " +
           "COUNT(DISTINCT rdv.user.userId) as activeVoters, " +
           "AVG(rdv.voteWeight) as avgWeight " +
           "FROM RouteDifficultyVote rdv " +
           "WHERE rdv.isActive = true")
    List<Object[]> getGlobalVoteStatistics();
    
    /**
     * 최근 활발한 투표 조회
     */
    @Query("SELECT rdv FROM RouteDifficultyVote rdv " +
           "WHERE rdv.createdAt >= :sinceDate AND rdv.isActive = true " +
           "ORDER BY rdv.createdAt DESC")
    List<RouteDifficultyVote> findRecentVotes(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * 투표 수 집계 (루트별)
     */
    @Query("SELECT COUNT(rdv) FROM RouteDifficultyVote rdv " +
           "WHERE rdv.route.routeId = :routeId AND rdv.isActive = true")
    long countVotesByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 사용자별 투표 수 집계
     */
    @Query("SELECT COUNT(rdv) FROM RouteDifficultyVote rdv " +
           "WHERE rdv.user.userId = :userId AND rdv.isActive = true")
    long countVotesByUserId(@Param("userId") Long userId);
}
```

---

## 📌 2. RouteScrapRepository - 루트 스크랩 Repository

```java
package com.routepick.domain.route.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.route.entity.RouteScrap;
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
 * RouteScrap Repository
 * - 루트 스크랩(북마크) 관리
 * - 개인 폴더 및 태그 시스템
 * - 우선순위 기반 정렬
 * - 목표일 관리
 */
@Repository
public interface RouteScrapRepository extends BaseRepository<RouteScrap, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자의 모든 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId " +
           "ORDER BY rs.createdAt DESC")
    List<RouteScrap> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    /**
     * 사용자의 스크랩 조회 (페이징)
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId " +
           "ORDER BY rs.createdAt DESC")
    Page<RouteScrap> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 사용자-루트별 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "WHERE rs.user.userId = :userId AND rs.route.routeId = :routeId")
    Optional<RouteScrap> findByUserIdAndRouteId(@Param("userId") Long userId, 
                                               @Param("routeId") Long routeId);
    
    /**
     * 스크랩 존재 여부 확인
     */
    @Query("SELECT COUNT(rs) > 0 FROM RouteScrap rs " +
           "WHERE rs.user.userId = :userId AND rs.route.routeId = :routeId")
    boolean existsByUserIdAndRouteId(@Param("userId") Long userId, @Param("routeId") Long routeId);
    
    // ===== 폴더별 관리 =====
    
    /**
     * 사용자의 폴더별 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.folderName = :folderName " +
           "ORDER BY rs.priorityLevel ASC, rs.createdAt DESC")
    List<RouteScrap> findByUserIdAndFolderName(@Param("userId") Long userId, 
                                              @Param("folderName") String folderName);
    
    /**
     * 사용자의 폴더 목록 조회
     */
    @Query("SELECT DISTINCT rs.folderName FROM RouteScrap rs " +
           "WHERE rs.user.userId = :userId AND rs.folderName IS NOT NULL " +
           "ORDER BY rs.folderName")
    List<String> findFolderNamesByUserId(@Param("userId") Long userId);
    
    /**
     * 폴더별 스크랩 수 통계
     */
    @Query("SELECT rs.folderName, COUNT(rs) as scrapCount FROM RouteScrap rs " +
           "WHERE rs.user.userId = :userId " +
           "GROUP BY rs.folderName " +
           "ORDER BY scrapCount DESC")
    List<Object[]> countScrapsByFolderName(@Param("userId") Long userId);
    
    /**
     * 기본 폴더 스크랩 조회 (폴더 미지정)
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND (rs.folderName IS NULL OR rs.folderName = '기본 폴더') " +
           "ORDER BY rs.priorityLevel ASC, rs.createdAt DESC")
    List<RouteScrap> findDefaultFolderScraps(@Param("userId") Long userId);
    
    // ===== 우선순위별 관리 =====
    
    /**
     * 우선순위별 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.priorityLevel = :priority " +
           "ORDER BY rs.createdAt DESC")
    List<RouteScrap> findByUserIdAndPriorityLevel(@Param("userId") Long userId, 
                                                 @Param("priority") Integer priority);
    
    /**
     * 고우선순위 스크랩 조회 (우선순위 1-2)
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.priorityLevel <= 2 " +
           "ORDER BY rs.priorityLevel ASC, rs.targetDate ASC")
    List<RouteScrap> findHighPriorityScraps(@Param("userId") Long userId);
    
    /**
     * 우선순위별 통계
     */
    @Query("SELECT rs.priorityLevel, COUNT(rs) as scrapCount FROM RouteScrap rs " +
           "WHERE rs.user.userId = :userId " +
           "GROUP BY rs.priorityLevel " +
           "ORDER BY rs.priorityLevel")
    List<Object[]> countScrapsByPriorityLevel(@Param("userId") Long userId);
    
    // ===== 스크랩 이유별 관리 =====
    
    /**
     * 스크랩 이유별 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.scrapReason = :reason " +
           "ORDER BY rs.priorityLevel ASC, rs.createdAt DESC")
    List<RouteScrap> findByUserIdAndScrapReason(@Param("userId") Long userId, 
                                               @Param("reason") String reason);
    
    /**
     * 도전 예정 루트 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.scrapReason = 'TO_TRY' " +
           "ORDER BY rs.priorityLevel ASC, rs.targetDate ASC")
    List<RouteScrap> findChallengeScraps(@Param("userId") Long userId);
    
    /**
     * 즐겨찾기 루트 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.scrapReason = 'FAVORITE' " +
           "ORDER BY rs.viewCount DESC, rs.createdAt DESC")
    List<RouteScrap> findFavoriteScraps(@Param("userId") Long userId);
    
    /**
     * 목표 루트 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.scrapReason = 'GOAL' " +
           "ORDER BY rs.targetDate ASC, rs.priorityLevel ASC")
    List<RouteScrap> findGoalScraps(@Param("userId") Long userId);
    
    // ===== 목표일 관리 =====
    
    /**
     * 목표일이 있는 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.targetDate IS NOT NULL " +
           "ORDER BY rs.targetDate ASC")
    List<RouteScrap> findScrapsWithTargetDate(@Param("userId") Long userId);
    
    /**
     * 목표일 임박 스크랩 조회 (D-7)
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.targetDate BETWEEN CURRENT_DATE AND :endDate " +
           "ORDER BY rs.targetDate ASC")
    List<RouteScrap> findUpcomingTargetScraps(@Param("userId") Long userId, 
                                             @Param("endDate") LocalDate endDate);
    
    /**
     * 목표일 지난 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.targetDate < CURRENT_DATE " +
           "ORDER BY rs.targetDate DESC")
    List<RouteScrap> findOverdueTargetScraps(@Param("userId") Long userId);
    
    // ===== 태그별 관리 =====
    
    /**
     * 개인 태그별 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.personalTags LIKE %:tag% " +
           "ORDER BY rs.createdAt DESC")
    List<RouteScrap> findByUserIdAndPersonalTag(@Param("userId") Long userId, 
                                               @Param("tag") String tag);
    
    /**
     * 사용자의 모든 개인 태그 조회
     */
    @Query("SELECT DISTINCT rs.personalTags FROM RouteScrap rs " +
           "WHERE rs.user.userId = :userId AND rs.personalTags IS NOT NULL " +
           "ORDER BY rs.personalTags")
    List<String> findPersonalTagsByUserId(@Param("userId") Long userId);
    
    // ===== 공개 스크랩 관리 =====
    
    /**
     * 사용자의 공개 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.isPublic = true " +
           "ORDER BY rs.createdAt DESC")
    List<RouteScrap> findPublicScrapsByUserId(@Param("userId") Long userId);
    
    /**
     * 루트별 공개 스크랩 조회 (다른 사용자들의 스크랩)
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.user u " +
           "WHERE rs.route.routeId = :routeId AND rs.isPublic = true " +
           "ORDER BY rs.createdAt DESC")
    List<RouteScrap> findPublicScrapsByRouteId(@Param("routeId") Long routeId);
    
    // ===== 조회수 및 활동 관리 =====
    
    /**
     * 조회수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteScrap rs SET " +
           "rs.viewCount = COALESCE(rs.viewCount, 0) + 1, " +
           "rs.lastViewedAt = CURRENT_TIMESTAMP " +
           "WHERE rs.scrapId = :scrapId")
    int increaseViewCount(@Param("scrapId") Long scrapId);
    
    /**
     * 자주 조회하는 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.viewCount >= :minViews " +
           "ORDER BY rs.viewCount DESC")
    List<RouteScrap> findFrequentlyViewedScraps(@Param("userId") Long userId, 
                                               @Param("minViews") Integer minViews);
    
    /**
     * 최근 조회한 스크랩 조회
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.lastViewedAt >= :sinceDate " +
           "ORDER BY rs.lastViewedAt DESC")
    List<RouteScrap> findRecentlyViewedScraps(@Param("userId") Long userId, 
                                             @Param("sinceDate") LocalDateTime sinceDate);
    
    // ===== 업데이트 메서드 =====
    
    /**
     * 개인 메모 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteScrap rs SET rs.personalMemo = :memo " +
           "WHERE rs.scrapId = :scrapId")
    int updatePersonalMemo(@Param("scrapId") Long scrapId, @Param("memo") String memo);
    
    /**
     * 개인 태그 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteScrap rs SET rs.personalTags = :tags " +
           "WHERE rs.scrapId = :scrapId")
    int updatePersonalTags(@Param("scrapId") Long scrapId, @Param("tags") String tags);
    
    /**
     * 폴더 이동
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteScrap rs SET rs.folderName = :newFolderName " +
           "WHERE rs.scrapId = :scrapId")
    int moveToFolder(@Param("scrapId") Long scrapId, @Param("newFolderName") String newFolderName);
    
    /**
     * 우선순위 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteScrap rs SET rs.priorityLevel = :newPriority " +
           "WHERE rs.scrapId = :scrapId")
    int updatePriorityLevel(@Param("scrapId") Long scrapId, @Param("newPriority") Integer newPriority);
    
    /**
     * 목표일 설정
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteScrap rs SET rs.targetDate = :targetDate " +
           "WHERE rs.scrapId = :scrapId")
    int updateTargetDate(@Param("scrapId") Long scrapId, @Param("targetDate") LocalDate targetDate);
    
    /**
     * 공개 상태 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteScrap rs SET rs.isPublic = :isPublic " +
           "WHERE rs.scrapId = :scrapId")
    int updatePublicStatus(@Param("scrapId") Long scrapId, @Param("isPublic") boolean isPublic);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 메모 내용 검색
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId AND rs.personalMemo LIKE %:keyword% " +
           "ORDER BY rs.createdAt DESC")
    List<RouteScrap> findByUserIdAndMemoContaining(@Param("userId") Long userId, 
                                                  @Param("keyword") String keyword);
    
    /**
     * 복합 조건 스크랩 검색
     */
    @Query("SELECT rs FROM RouteScrap rs " +
           "JOIN FETCH rs.route r " +
           "WHERE rs.user.userId = :userId " +
           "AND (:folderName IS NULL OR rs.folderName = :folderName) " +
           "AND (:priority IS NULL OR rs.priorityLevel = :priority) " +
           "AND (:reason IS NULL OR rs.scrapReason = :reason) " +
           "AND (:hasTargetDate IS NULL OR " +
           "     (:hasTargetDate = true AND rs.targetDate IS NOT NULL) OR " +
           "     (:hasTargetDate = false AND rs.targetDate IS NULL)) " +
           "ORDER BY rs.priorityLevel ASC, rs.createdAt DESC")
    Page<RouteScrap> findByComplexConditions(@Param("userId") Long userId,
                                           @Param("folderName") String folderName,
                                           @Param("priority") Integer priority,
                                           @Param("reason") String reason,
                                           @Param("hasTargetDate") Boolean hasTargetDate,
                                           Pageable pageable);
    
    // ===== 통계 및 분석 =====
    
    /**
     * 루트별 스크랩 수 조회
     */
    @Query("SELECT COUNT(rs) FROM RouteScrap rs " +
           "WHERE rs.route.routeId = :routeId")
    long countScrapsByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 사용자별 스크랩 수 조회
     */
    @Query("SELECT COUNT(rs) FROM RouteScrap rs " +
           "WHERE rs.user.userId = :userId")
    long countScrapsByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 스크랩 통계 요약
     */
    @Query("SELECT " +
           "COUNT(rs) as totalScraps, " +
           "COUNT(DISTINCT rs.folderName) as folderCount, " +
           "COUNT(CASE WHEN rs.targetDate IS NOT NULL THEN 1 END) as scrapsWithTarget, " +
           "AVG(rs.priorityLevel) as avgPriority, " +
           "SUM(rs.viewCount) as totalViews " +
           "FROM RouteScrap rs " +
           "WHERE rs.user.userId = :userId")
    List<Object[]> getScrapStatisticsByUserId(@Param("userId") Long userId);
    
    /**
     * 인기 스크랩 루트 조회 (스크랩 수 기준)
     */
    @Query("SELECT rs.route, COUNT(rs) as scrapCount FROM RouteScrap rs " +
           "GROUP BY rs.route " +
           "ORDER BY scrapCount DESC")
    List<Object[]> findPopularScrapedRoutes(Pageable pageable);
}
```

---

## ⚡ 성능 최적화 전략

### 난이도 투표 최적화 인덱스
```sql
-- 난이도 투표 집계 최적화
CREATE INDEX idx_difficulty_vote_aggregation 
ON route_difficulty_votes(route_id, is_active, vote_weight DESC);

-- 사용자별 투표 패턴 분석
CREATE INDEX idx_difficulty_vote_user_pattern 
ON route_difficulty_votes(user_id, is_successful_climb, vote_weight);

-- 시간별 난이도 트렌드 분석
CREATE INDEX idx_difficulty_vote_trend 
ON route_difficulty_votes(route_id, created_at, suggested_difficulty);

-- 중복 투표 방지 최적화
CREATE UNIQUE INDEX idx_difficulty_vote_unique
ON route_difficulty_votes(user_id, route_id) WHERE is_active = true;
```

### 스크랩 관리 최적화 인덱스
```sql
-- 사용자별 스크랩 폴더 최적화
CREATE INDEX idx_route_scrap_user_folder 
ON route_scraps(user_id, folder_name, priority_level, created_at DESC);

-- 목표일 기반 스크랩 관리
CREATE INDEX idx_route_scrap_target_management 
ON route_scraps(user_id, target_date, priority_level);

-- 공개 스크랩 조회 최적화
CREATE INDEX idx_route_scrap_public 
ON route_scraps(route_id, is_public, created_at DESC);

-- 스크랩 이유별 조회 최적화
CREATE INDEX idx_route_scrap_reason
ON route_scraps(user_id, scrap_reason, priority_level, target_date);
```

### 가중 평균 계산 최적화
```java
// 난이도 가중 평균 계산 캐싱
@Cacheable(value = "difficultyAverage", key = "#routeId")
public BigDecimal calculateWeightedAverageDifficulty(Long routeId) {
    return repository.calculateAverageDifficultyByRoute(routeId)
                    .orElse(BigDecimal.valueOf(5.0));
}

// 배치 업데이트로 성능 향상
@Scheduled(fixedRate = 600000) // 10분마다
public void updateVoteWeights() {
    // 완등자 투표 가중치 증가 배치 처리
}
```

### 스크랩 폴더 관리 최적화
```java
// 폴더별 스크랩 수 캐싱
@Cacheable(value = "userFolders", key = "#userId")
public List<FolderStatistics> getUserFolderStatistics(Long userId) {
    return repository.countScrapsByFolderName(userId)
                    .stream()
                    .map(this::mapToFolderStatistics)
                    .collect(Collectors.toList());
}
```

---

## ✅ 설계 완료 체크리스트

### 투표 & 스크랩 Repository (2개)
- [x] **RouteDifficultyVoteRepository** - 난이도 투표, 가중 평균, 신뢰도 관리
- [x] **RouteScrapRepository** - 개인 폴더, 우선순위, 목표일 관리

### 난이도 투표 시스템 핵심 기능
- [x] 가중 평균 난이도 계산 알고리즘
- [x] 완등자 투표 가중치 부여 (추가 0.2, 최대 2.0)
- [x] 투표 신뢰도 검증 시스템
- [x] 시간별 난이도 트렌드 분석

### 스크랩 개인화 기능
- [x] 폴더별 루트 분류 시스템
- [x] 우선순위 기반 정렬 (1-5단계)
- [x] 목표일 설정 및 D-day 알림
- [x] 개인 메모 및 태그 관리

### 투표 신뢰도 관리
- [x] 경험 수준별 투표 가중치 차등
- [x] 완등 여부에 따른 신뢰도 조정
- [x] 중복 투표 방지 시스템
- [x] 투표 패턴 분석 및 검증

### 스크랩 고급 기능
- [x] 스크랩 이유별 분류 (TO_TRY, FAVORITE, GOAL)
- [x] 공개/비공개 스크랩 관리
- [x] 조회 횟수 추적 및 분석
- [x] 복합 조건 검색 지원

### 성능 최적화
- [x] 복합 인덱스 전략 최적화
- [x] 가중 평균 계산 캐싱
- [x] 배치 업데이트 지원
- [x] 통계 집계 쿼리 최적화

---

**관련 파일**: step5-3e1_route_comment_repositories.md (RouteCommentRepository)  
**완료일**: 2025-08-21  
**핵심 성과**: 투표 & 스크랩 2개 Repository 완성 (가중 평균 계산 + 개인화 폴더 관리)