# Step 5-2b: 태그 루트 연관 Repository - RouteTag & Recommendation

> 태그 시스템 루트 연관 2개 Repository 설계 (루트-태그 관계 & 추천 시스템)  
> 생성일: 2025-08-21  
> 기반: step5-2_tag_repositories_focused.md 세분화  
> 포함 Repository: RouteTagRepository, UserRouteRecommendationRepository

---

## 📋 파일 세분화 정보
- **원본 파일**: step5-2_tag_repositories_focused.md (1,286줄)
- **세분화 사유**: 토큰 제한 대응 및 기능별 책임 분리
- **이 파일 포함**: RouteTagRepository, UserRouteRecommendationRepository
- **다른 파일**: step5-2a_tag_core_repositories.md (TagRepository, UserPreferredTagRepository)

---

## 🎯 설계 목표

- **루트-태그 연관성 관리**: 정확한 태깅, 연관성 점수 최적화
- **추천 시스템 지원**: AI 기반 점수 계산, 실시간 추천 갱신
- **품질 관리**: 태그 품질 분석, 중복 방지, 합의도 측정
- **성능 최적화**: 복합 인덱스, 배치 처리, 캐싱 전략

---

## 🧗‍♂️ 1. RouteTagRepository - 루트 태그 연관 Repository

```java
package com.routepick.domain.tag.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.tag.entity.RouteTag;
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
 * RouteTag Repository
 * - 루트-태그 연관도 관리 최적화
 * - 연관성 점수 기반 정렬
 * - 태그 품질 관리
 */
@Repository
public interface RouteTagRepository extends BaseRepository<RouteTag, Long>, RouteTagRepositoryCustom {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 루트의 모든 태그 조회 (연관성 점수 순)
     */
    @Query("SELECT rt FROM RouteTag rt " +
           "JOIN FETCH rt.tag t " +
           "WHERE rt.route.routeId = :routeId " +
           "ORDER BY rt.relevanceScore DESC")
    List<RouteTag> findByRouteIdOrderByRelevanceScoreDesc(@Param("routeId") Long routeId);
    
    /**
     * 특정 태그의 모든 루트 조회 (연관성 점수 순)
     */
    @Query("SELECT rt FROM RouteTag rt " +
           "JOIN FETCH rt.route r " +
           "WHERE rt.tag.tagId = :tagId " +
           "ORDER BY rt.relevanceScore DESC")
    List<RouteTag> findByTagIdOrderByRelevanceScoreDesc(@Param("tagId") Long tagId);
    
    /**
     * 루트-태그 조합 조회
     */
    @Query("SELECT rt FROM RouteTag rt " +
           "WHERE rt.route.routeId = :routeId AND rt.tag.tagId = :tagId")
    Optional<RouteTag> findByRouteIdAndTagId(@Param("routeId") Long routeId, @Param("tagId") Long tagId);
    
    // ===== 연관성 점수 기반 조회 =====
    
    /**
     * 높은 연관성 태그 조회 (점수 기준)
     */
    @Query("SELECT rt FROM RouteTag rt " +
           "JOIN FETCH rt.tag t " +
           "WHERE rt.tag.tagId = :tagId AND rt.relevanceScore >= :minScore " +
           "ORDER BY rt.relevanceScore DESC")
    List<RouteTag> findByTagIdAndRelevanceScoreGreaterThan(@Param("tagId") Long tagId, 
                                                         @Param("minScore") BigDecimal minScore);
    
    /**
     * 루트의 높은 연관성 태그만 조회
     */
    @Query("SELECT rt FROM RouteTag rt " +
           "JOIN FETCH rt.tag t " +
           "WHERE rt.route.routeId = :routeId AND rt.relevanceScore >= :minScore " +
           "ORDER BY rt.relevanceScore DESC")
    List<RouteTag> findHighRelevanceTagsByRoute(@Param("routeId") Long routeId, 
                                              @Param("minScore") BigDecimal minScore);
    
    /**
     * 특정 점수 범위의 루트 태그 조회
     */
    @Query("SELECT rt FROM RouteTag rt " +
           "WHERE rt.relevanceScore BETWEEN :minScore AND :maxScore " +
           "ORDER BY rt.relevanceScore DESC")
    List<RouteTag> findByRelevanceScoreBetween(@Param("minScore") BigDecimal minScore, 
                                             @Param("maxScore") BigDecimal maxScore);
    
    // ===== 태그 사용 통계 =====
    
    /**
     * 태그별 사용 횟수 조회
     */
    @Query("SELECT rt.tag.tagId, rt.tag.tagName, COUNT(*) as usageCount " +
           "FROM RouteTag rt " +
           "GROUP BY rt.tag.tagId, rt.tag.tagName " +
           "ORDER BY usageCount DESC")
    List<Object[]> countUsageByTag();
    
    /**
     * 특정 태그의 사용 횟수
     */
    @Query("SELECT COUNT(rt) FROM RouteTag rt WHERE rt.tag.tagId = :tagId")
    long countByTagId(@Param("tagId") Long tagId);
    
    /**
     * 루트별 태그 수 조회
     */
    @Query("SELECT rt.route.routeId, COUNT(*) as tagCount FROM RouteTag rt " +
           "GROUP BY rt.route.routeId " +
           "ORDER BY tagCount DESC")
    List<Object[]> countTagsByRoute();
    
    /**
     * 특정 루트의 태그 수
     */
    @Query("SELECT COUNT(rt) FROM RouteTag rt WHERE rt.route.routeId = :routeId")
    long countByRouteId(@Param("routeId") Long routeId);
    
    // ===== 사용자별 태깅 관리 =====
    
    /**
     * 특정 사용자가 태깅한 루트 조회
     */
    @Query("SELECT rt FROM RouteTag rt " +
           "JOIN FETCH rt.route r " +
           "JOIN FETCH rt.tag t " +
           "WHERE rt.createdByUser.userId = :userId " +
           "ORDER BY rt.createdAt DESC")
    List<RouteTag> findByCreatedByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자가 특정 루트에 태깅한 내용 조회
     */
    @Query("SELECT rt FROM RouteTag rt " +
           "JOIN FETCH rt.tag t " +
           "WHERE rt.route.routeId = :routeId AND rt.createdByUser.userId = :userId " +
           "ORDER BY rt.relevanceScore DESC")
    List<RouteTag> findByRouteIdAndCreatedByUserId(@Param("routeId") Long routeId, 
                                                  @Param("userId") Long userId);
    
    /**
     * 사용자별 태깅 통계
     */
    @Query("SELECT rt.createdByUser.userId, COUNT(*) as tagCount, AVG(rt.relevanceScore) as avgScore " +
           "FROM RouteTag rt " +
           "WHERE rt.createdByUser IS NOT NULL " +
           "GROUP BY rt.createdByUser.userId " +
           "ORDER BY tagCount DESC")
    List<Object[]> getTaggingStatisticsByUser();
    
    // ===== 연관성 점수 업데이트 =====
    
    /**
     * 연관성 점수 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteTag rt SET rt.relevanceScore = :relevanceScore " +
           "WHERE rt.route.routeId = :routeId AND rt.tag.tagId = :tagId")
    int updateRelevanceScore(@Param("routeId") Long routeId, 
                           @Param("tagId") Long tagId, 
                           @Param("relevanceScore") BigDecimal relevanceScore);
    
    /**
     * 연관성 점수 일괄 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteTag rt SET rt.relevanceScore = :relevanceScore " +
           "WHERE rt.routeTagId IN :routeTagIds")
    int updateRelevanceScoreBatch(@Param("routeTagIds") List<Long> routeTagIds, 
                                 @Param("relevanceScore") BigDecimal relevanceScore);
    
    /**
     * 루트의 모든 태그 연관성 점수 조정
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteTag rt SET rt.relevanceScore = rt.relevanceScore * :factor " +
           "WHERE rt.route.routeId = :routeId")
    int adjustRelevanceScoresByFactor(@Param("routeId") Long routeId, 
                                    @Param("factor") BigDecimal factor);
    
    // ===== 품질 관리 =====
    
    /**
     * 낮은 연관성 태그 조회 (품질 관리용)
     */
    @Query("SELECT rt FROM RouteTag rt " +
           "JOIN FETCH rt.route r " +
           "JOIN FETCH rt.tag t " +
           "WHERE rt.relevanceScore < :threshold " +
           "ORDER BY rt.relevanceScore")
    List<RouteTag> findLowQualityTags(@Param("threshold") BigDecimal threshold);
    
    /**
     * 중복 태그 조회
     */
    @Query("SELECT rt.route.routeId, rt.tag.tagId, COUNT(*) as duplicateCount " +
           "FROM RouteTag rt " +
           "GROUP BY rt.route.routeId, rt.tag.tagId " +
           "HAVING COUNT(*) > 1")
    List<Object[]> findDuplicateTags();
    
    /**
     * 시스템 생성 vs 사용자 생성 태그 비율
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN rt.createdByUser IS NULL THEN 1 END) as systemGenerated, " +
           "COUNT(CASE WHEN rt.createdByUser IS NOT NULL THEN 1 END) as userGenerated " +
           "FROM RouteTag rt")
    List<Object[]> getTagSourceStatistics();
    
    // ===== 고급 분석 쿼리 =====
    
    /**
     * 태그별 평균 연관성 점수 계산
     */
    @Query("SELECT rt.tag.tagId, rt.tag.tagName, AVG(rt.relevanceScore) as avgRelevance, COUNT(*) as usageCount " +
           "FROM RouteTag rt " +
           "GROUP BY rt.tag.tagId, rt.tag.tagName " +
           "ORDER BY avgRelevance DESC")
    List<Object[]> calculateAverageRelevanceByTag();
    
    /**
     * 루트의 태그 합의도 조회 (여러 사용자가 동일 태그 부여)
     */
    @Query("SELECT rt.route.routeId, rt.tag.tagId, COUNT(DISTINCT rt.createdByUser.userId) as userConsensus " +
           "FROM RouteTag rt " +
           "WHERE rt.createdByUser IS NOT NULL " +
           "GROUP BY rt.route.routeId, rt.tag.tagId " +
           "HAVING COUNT(DISTINCT rt.createdByUser.userId) >= :minUsers " +
           "ORDER BY userConsensus DESC")
    List<Object[]> findRoutesWithHighTagConsensus(@Param("minUsers") int minUsers);
    
    /**
     * 태그 연관성 매트릭스 (함께 사용되는 태그들)
     */
    @Query("SELECT rt1.tag.tagId as tag1, rt2.tag.tagId as tag2, COUNT(*) as coOccurrence " +
           "FROM RouteTag rt1 " +
           "JOIN RouteTag rt2 ON rt1.route.routeId = rt2.route.routeId " +
           "WHERE rt1.tag.tagId < rt2.tag.tagId " +
           "GROUP BY rt1.tag.tagId, rt2.tag.tagId " +
           "HAVING COUNT(*) >= :minCoOccurrence " +
           "ORDER BY coOccurrence DESC")
    List<Object[]> findTagCoOccurrenceMatrix(@Param("minCoOccurrence") int minCoOccurrence);
    
    // ===== 삭제 작업 =====
    
    /**
     * 루트의 모든 태그 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RouteTag rt WHERE rt.route.routeId = :routeId")
    int deleteByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 특정 태그의 모든 연관 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RouteTag rt WHERE rt.tag.tagId = :tagId")
    int deleteByTagId(@Param("tagId") Long tagId);
    
    /**
     * 낮은 연관성 태그 일괄 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RouteTag rt WHERE rt.relevanceScore < :threshold")
    int deleteLowRelevanceTags(@Param("threshold") BigDecimal threshold);
}
```

### RouteTagRepositoryCustom.java - Custom Repository 인터페이스
```java
package com.routepick.domain.tag.repository;

import com.routepick.domain.tag.entity.RouteTag;

import java.math.BigDecimal;
import java.util.List;

/**
 * RouteTag Repository Custom 인터페이스
 * - QueryDSL 기반 복잡한 연관도 분석
 */
public interface RouteTagRepositoryCustom {
    
    /**
     * 복합 조건 루트 태그 검색
     */
    List<RouteTag> findRouteTagsByComplexConditions(List<Long> routeIds, List<Long> tagIds, 
                                                   BigDecimal minRelevance, Long createdByUserId);
    
    /**
     * 태그 품질 분석
     */
    List<TagQualityResult> analyzeTagQuality();
    
    /**
     * 루트 태그 추천 (기존 태그 기반)
     */
    List<TagRecommendationResult> recommendTagsForRoute(Long routeId, int maxResults);
    
    /**
     * 연관성 점수 분포 분석
     */
    List<RelevanceDistributionResult> analyzeRelevanceDistribution();
}
```

---

## 🎯 2. UserRouteRecommendationRepository - 추천 시스템 Repository

```java
package com.routepick.domain.tag.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.tag.entity.UserRouteRecommendation;
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
 * UserRouteRecommendation Repository
 * - AI 추천 시스템 지원 최적화
 * - 추천 점수 기반 정렬
 * - 추천 결과 캐싱 및 갱신
 */
@Repository
public interface UserRouteRecommendationRepository extends BaseRepository<UserRouteRecommendation, Long>, RecommendationRepositoryCustom {
    
    // ===== 기본 추천 조회 =====
    
    /**
     * 사용자 추천 루트 Top N 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.isActive = true " +
           "ORDER BY urr.recommendationScore DESC")
    List<UserRouteRecommendation> findTop10ByUserIdAndIsActiveTrueOrderByRecommendationScoreDesc(@Param("userId") Long userId, 
                                                                                                Pageable pageable);
    
    /**
     * 사용자의 모든 활성 추천 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.isActive = true " +
           "ORDER BY urr.recommendationScore DESC")
    List<UserRouteRecommendation> findActiveRecommendationsByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 활성 추천 페이징 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.isActive = true " +
           "ORDER BY urr.recommendationScore DESC")
    Page<UserRouteRecommendation> findActiveRecommendationsByUserId(@Param("userId") Long userId, 
                                                                  Pageable pageable);
    
    /**
     * 특정 사용자-루트 추천 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "WHERE urr.user.userId = :userId AND urr.route.routeId = :routeId")
    Optional<UserRouteRecommendation> findByUserIdAndRouteId(@Param("userId") Long userId, 
                                                           @Param("routeId") Long routeId);
    
    // ===== 점수 기반 조회 =====
    
    /**
     * 높은 추천 점수 조회 (점수 기준)
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.recommendationScore >= :minScore AND urr.isActive = true " +
           "ORDER BY urr.recommendationScore DESC")
    List<UserRouteRecommendation> findByUserIdAndRecommendationScoreGreaterThan(@Param("userId") Long userId, 
                                                                               @Param("minScore") BigDecimal minScore);
    
    /**
     * 태그 매칭 점수 기준 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.tagMatchScore >= :minTagScore AND urr.isActive = true " +
           "ORDER BY urr.tagMatchScore DESC")
    List<UserRouteRecommendation> findByUserIdAndTagMatchScoreGreaterThan(@Param("userId") Long userId, 
                                                                         @Param("minTagScore") BigDecimal minTagScore);
    
    /**
     * 레벨 매칭 점수 기준 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.levelMatchScore >= :minLevelScore AND urr.isActive = true " +
           "ORDER BY urr.levelMatchScore DESC")
    List<UserRouteRecommendation> findByUserIdAndLevelMatchScoreGreaterThan(@Param("userId") Long userId, 
                                                                           @Param("minLevelScore") BigDecimal minLevelScore);
    
    // ===== 추천 타입별 조회 =====
    
    /**
     * 태그 중심 추천 조회 (태그 점수 > 레벨 점수)
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.tagMatchScore > urr.levelMatchScore AND urr.isActive = true " +
           "ORDER BY urr.tagMatchScore DESC")
    List<UserRouteRecommendation> findTagDrivenRecommendations(@Param("userId") Long userId);
    
    /**
     * 레벨 중심 추천 조회 (레벨 점수 > 태그 점수)
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.levelMatchScore > urr.tagMatchScore AND urr.isActive = true " +
           "ORDER BY urr.levelMatchScore DESC")
    List<UserRouteRecommendation> findLevelDrivenRecommendations(@Param("userId") Long userId);
    
    // ===== 시간 기반 조회 =====
    
    /**
     * 최근 계산된 추천 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.calculatedAt >= :since AND urr.isActive = true " +
           "ORDER BY urr.calculatedAt DESC")
    List<UserRouteRecommendation> findRecentRecommendations(@Param("userId") Long userId, 
                                                           @Param("since") LocalDateTime since);
    
    /**
     * 오래된 추천 조회 (갱신 대상)
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "WHERE urr.calculatedAt < :cutoffDate AND urr.isActive = true " +
           "ORDER BY urr.calculatedAt")
    List<UserRouteRecommendation> findOutdatedRecommendations(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ===== 추천 상호작용 추적 =====
    
    /**
     * 조회된 추천 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.viewCount > 0 AND urr.isActive = true " +
           "ORDER BY urr.lastViewedAt DESC")
    List<UserRouteRecommendation> findViewedRecommendations(@Param("userId") Long userId);
    
    /**
     * 클릭된 추천 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.clicked = true AND urr.isActive = true " +
           "ORDER BY urr.clickedAt DESC")
    List<UserRouteRecommendation> findClickedRecommendations(@Param("userId") Long userId);
    
    /**
     * 미조회 추천 조회
     */
    @Query("SELECT urr FROM UserRouteRecommendation urr " +
           "JOIN FETCH urr.route r " +
           "WHERE urr.user.userId = :userId AND urr.viewCount = 0 AND urr.isActive = true " +
           "ORDER BY urr.recommendationScore DESC")
    List<UserRouteRecommendation> findUnviewedRecommendations(@Param("userId") Long userId);
    
    // ===== 추천 상태 업데이트 =====
    
    /**
     * 추천 조회 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserRouteRecommendation urr SET " +
           "urr.viewCount = COALESCE(urr.viewCount, 0) + 1, " +
           "urr.lastViewedAt = CURRENT_TIMESTAMP " +
           "WHERE urr.user.userId = :userId AND urr.route.routeId = :routeId")
    int markAsViewed(@Param("userId") Long userId, @Param("routeId") Long routeId);
    
    /**
     * 추천 클릭 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserRouteRecommendation urr SET " +
           "urr.clicked = true, " +
           "urr.clickedAt = CURRENT_TIMESTAMP, " +
           "urr.viewCount = COALESCE(urr.viewCount, 0) + 1, " +
           "urr.lastViewedAt = CURRENT_TIMESTAMP " +
           "WHERE urr.user.userId = :userId AND urr.route.routeId = :routeId")
    int markAsClicked(@Param("userId") Long userId, @Param("routeId") Long routeId);
    
    /**
     * 추천 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserRouteRecommendation urr SET urr.isActive = false " +
           "WHERE urr.user.userId = :userId AND urr.route.routeId IN :routeIds")
    int deactivateRecommendations(@Param("userId") Long userId, @Param("routeIds") List<Long> routeIds);
    
    // ===== 추천 점수 갱신 =====
    
    /**
     * 추천 점수 재계산
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserRouteRecommendation urr SET " +
           "urr.recommendationScore = (urr.tagMatchScore * 0.7 + urr.levelMatchScore * 0.3), " +
           "urr.calculatedAt = CURRENT_TIMESTAMP " +
           "WHERE urr.user.userId = :userId")
    int recalculateRecommendationScores(@Param("userId") Long userId);
    
    /**
     * 태그 매칭 점수 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserRouteRecommendation urr SET " +
           "urr.tagMatchScore = :tagMatchScore, " +
           "urr.recommendationScore = (:tagMatchScore * 0.7 + urr.levelMatchScore * 0.3), " +
           "urr.calculatedAt = CURRENT_TIMESTAMP " +
           "WHERE urr.user.userId = :userId AND urr.route.routeId = :routeId")
    int updateTagMatchScore(@Param("userId") Long userId, 
                           @Param("routeId") Long routeId, 
                           @Param("tagMatchScore") BigDecimal tagMatchScore);
    
    /**
     * 레벨 매칭 점수 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserRouteRecommendation urr SET " +
           "urr.levelMatchScore = :levelMatchScore, " +
           "urr.recommendationScore = (urr.tagMatchScore * 0.7 + :levelMatchScore * 0.3), " +
           "urr.calculatedAt = CURRENT_TIMESTAMP " +
           "WHERE urr.user.userId = :userId AND urr.route.routeId = :routeId")
    int updateLevelMatchScore(@Param("userId") Long userId, 
                             @Param("routeId") Long routeId, 
                             @Param("levelMatchScore") BigDecimal levelMatchScore);
    
    // ===== 배치 작업 =====
    
    /**
     * 사용자 추천 전체 갱신 (기존 비활성화 후 새로 생성)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserRouteRecommendation urr SET urr.isActive = false " +
           "WHERE urr.user.userId = :userId")
    int deactivateAllUserRecommendations(@Param("userId") Long userId);
    
    /**
     * 오래된 추천 정리
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserRouteRecommendation urr " +
           "WHERE urr.user.userId = :userId AND urr.calculatedAt < :cutoffDate")
    int deleteOutdatedRecommendations(@Param("userId") Long userId, 
                                     @Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 비활성 추천 정리
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserRouteRecommendation urr " +
           "WHERE urr.isActive = false AND urr.calculatedAt < :cutoffDate")
    int deleteInactiveRecommendations(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ===== 통계 및 분석 =====
    
    /**
     * 사용자별 추천 통계
     */
    @Query("SELECT urr.user.userId, " +
           "COUNT(*) as totalRecommendations, " +
           "AVG(urr.recommendationScore) as avgScore, " +
           "SUM(CASE WHEN urr.clicked = true THEN 1 ELSE 0 END) as clickCount, " +
           "SUM(urr.viewCount) as totalViews " +
           "FROM UserRouteRecommendation urr " +
           "WHERE urr.isActive = true " +
           "GROUP BY urr.user.userId " +
           "ORDER BY avgScore DESC")
    List<Object[]> getRecommendationStatisticsByUser();
    
    /**
     * 루트별 추천 통계
     */
    @Query("SELECT urr.route.routeId, " +
           "COUNT(*) as recommendationCount, " +
           "AVG(urr.recommendationScore) as avgScore, " +
           "SUM(CASE WHEN urr.clicked = true THEN 1 ELSE 0 END) as clickCount " +
           "FROM UserRouteRecommendation urr " +
           "WHERE urr.isActive = true " +
           "GROUP BY urr.route.routeId " +
           "ORDER BY recommendationCount DESC")
    List<Object[]> getRecommendationStatisticsByRoute();
    
    /**
     * 추천 성과 분석 (클릭률)
     */
    @Query("SELECT " +
           "COUNT(*) as totalRecommendations, " +
           "SUM(CASE WHEN urr.clicked = true THEN 1 ELSE 0 END) as totalClicks, " +
           "SUM(urr.viewCount) as totalViews, " +
           "AVG(urr.recommendationScore) as avgScore " +
           "FROM UserRouteRecommendation urr " +
           "WHERE urr.isActive = true")
    List<Object[]> getOverallRecommendationPerformance();
    
    /**
     * 추천 알고리즘 효과 분석
     */
    @Query("SELECT " +
           "AVG(urr.tagMatchScore) as avgTagScore, " +
           "AVG(urr.levelMatchScore) as avgLevelScore, " +
           "COUNT(CASE WHEN urr.tagMatchScore > urr.levelMatchScore THEN 1 END) as tagDriven, " +
           "COUNT(CASE WHEN urr.levelMatchScore > urr.tagMatchScore THEN 1 END) as levelDriven " +
           "FROM UserRouteRecommendation urr " +
           "WHERE urr.isActive = true")
    List<Object[]> analyzeRecommendationAlgorithmEffectiveness();
    
    // ===== 유사 사용자 찾기 =====
    
    /**
     * 유사한 추천을 받은 사용자들 찾기
     */
    @Query("SELECT urr2.user.userId, COUNT(*) as commonRecommendations " +
           "FROM UserRouteRecommendation urr1 " +
           "JOIN UserRouteRecommendation urr2 ON urr1.route.routeId = urr2.route.routeId " +
           "WHERE urr1.user.userId = :userId AND urr2.user.userId != :userId " +
           "AND urr1.isActive = true AND urr2.isActive = true " +
           "AND urr1.recommendationScore >= :minScore AND urr2.recommendationScore >= :minScore " +
           "GROUP BY urr2.user.userId " +
           "HAVING COUNT(*) >= :minCommonRecommendations " +
           "ORDER BY commonRecommendations DESC")
    List<Object[]> findUsersWithSimilarRecommendations(@Param("userId") Long userId, 
                                                      @Param("minScore") BigDecimal minScore, 
                                                      @Param("minCommonRecommendations") int minCommonRecommendations);
}
```

### RecommendationRepositoryCustom.java - Custom Repository 인터페이스
```java
package com.routepick.domain.tag.repository;

import com.routepick.domain.tag.entity.UserRouteRecommendation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recommendation Repository Custom 인터페이스
 * - QueryDSL 기반 복잡한 추천 알고리즘 지원
 */
public interface RecommendationRepositoryCustom {
    
    /**
     * 개인화된 추천 점수 계산
     */
    List<RecommendationScoreResult> calculatePersonalizedRecommendations(Long userId);
    
    /**
     * 협업 필터링 추천
     */
    List<UserRouteRecommendation> findCollaborativeFilteringRecommendations(Long userId, int maxResults);
    
    /**
     * 하이브리드 추천 (콘텐츠 기반 + 협업 필터링)
     */
    List<UserRouteRecommendation> findHybridRecommendations(Long userId, int maxResults);
    
    /**
     * 추천 다양성 최적화
     */
    List<UserRouteRecommendation> findDiversifiedRecommendations(Long userId, int maxResults);
    
    /**
     * 실시간 추천 갱신
     */
    void refreshRecommendationsForUser(Long userId);
    
    /**
     * 배치 추천 업데이트
     */
    void batchUpdateRecommendations(List<Long> userIds);
}
```

---

## ⚡ 성능 최적화 전략

### 복합 인덱스 최적화
```sql
-- 추천 시스템 핵심 인덱스
CREATE INDEX idx_user_recommendation_score ON user_route_recommendations (user_id, recommendation_score DESC, is_active);

-- 태그 매칭 최적화
CREATE INDEX idx_route_tag_relevance ON route_tags (route_id, relevance_score DESC);

-- 연관성 분석 최적화
CREATE INDEX idx_tag_relevance_analysis ON route_tags (tag_id, relevance_score DESC, created_by_user_id);
```

### 쿼리 캐싱 전략
```java
@Cacheable(value = "userRecommendations", key = "#userId")
List<UserRouteRecommendation> findTopRecommendations(Long userId);

@Cacheable(value = "routeTags", key = "#routeId")
List<RouteTag> findHighRelevanceTagsByRoute(Long routeId);

@CacheEvict(value = "recommendationStats", allEntries = true)
void refreshRecommendationStatistics();
```

### 배치 처리 지원
```java
@Async
@Transactional
public void batchUpdateRecommendations(List<Long> userIds) {
    // 비동기 배치 처리
    userIds.parallelStream()
          .forEach(this::refreshUserRecommendations);
}

@Scheduled(fixedRate = 3600000) // 1시간마다
public void cleanupOutdatedRecommendations() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
    userRouteRecommendationRepository.deleteInactiveRecommendations(cutoff);
}
```

---

## ✅ 설계 완료 체크리스트

### 태그 루트 연관 Repository (2개)
- [x] RouteTagRepository - 루트-태그 연관도 관리, 품질 분석
- [x] UserRouteRecommendationRepository - AI 추천 시스템 지원

### Custom Repository 인터페이스
- [x] RouteTagRepositoryCustom - 연관도 계산 및 분석
- [x] RecommendationRepositoryCustom - 추천 알고리즘 최적화

### 핵심 기능
- [x] 연관성 점수 기반 태그 관리
- [x] AI 추천 시스템 완전 지원
- [x] 추천 상호작용 추적
- [x] 품질 관리 및 분석

### 성능 최적화
- [x] 복합 인덱스 추천 시스템 최적화
- [x] 쿼리 캐싱 전략 설계
- [x] 배치 처리 지원 구조
- [x] 실시간 추천 갱신 지원

---

**관련 파일**: step5-2a_tag_core_repositories.md (TagRepository, UserPreferredTagRepository)  
**완료일**: 2025-08-21  
**핵심 성과**: 태그 루트 연관 2개 Repository 완성 (루트-태그 관계 + AI 추천 시스템)