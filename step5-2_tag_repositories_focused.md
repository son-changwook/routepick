# Step 5-2: 태그 시스템 Repository 집중 생성

> 태그 시스템 4개 Repository 완전 설계 (성능 최적화 즉시 반영)  
> 생성일: 2025-08-20  
> 기반: step5-1_base_user_repositories.md, step4-2_tag_business_entities.md

---

## 🎯 설계 목표

- **태그 검색 성능 최적화**: 자동완성, 실시간 검색, Full-Text Index 활용
- **추천 알고리즘 지원**: 고성능 쿼리, 복합 인덱스 최적화
- **태그 통계 및 분석**: 사용 빈도, 인기도, 트렌드 분석
- **사용자 선호도 기반 개인화**: 유사 사용자 매칭, 추천 태그 발견

---

## 🏷️ 1. TagRepository - 마스터 태그 Repository

```java
package com.routepick.domain.tag.repository;

import com.routepick.common.enums.TagType;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.tag.entity.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Tag Repository
 * - 마스터 태그 관리
 * - 태그 검색 및 자동완성 최적화
 * - 태그 통계 및 인기도 분석
 */
@Repository
public interface TagRepository extends BaseRepository<Tag, Long>, TagRepositoryCustom {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 태그명으로 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.tagName = :tagName")
    Optional<Tag> findByTagName(@Param("tagName") String tagName);
    
    /**
     * 태그 타입별 조회 (표시 순서 정렬)
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType ORDER BY t.displayOrder, t.tagName")
    List<Tag> findByTagTypeOrderByDisplayOrder(@Param("tagType") TagType tagType);
    
    /**
     * 사용자 선택 가능한 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.isUserSelectable = true ORDER BY t.displayOrder")
    List<Tag> findByTagTypeAndIsUserSelectable(@Param("tagType") TagType tagType);
    
    /**
     * 루트 태깅 가능한 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.isRouteTaggable = true ORDER BY t.displayOrder")
    List<Tag> findByTagTypeAndIsRouteTaggable(@Param("tagType") TagType tagType);
    
    // ===== 태그 검색 및 자동완성 =====
    
    /**
     * 태그명 부분 검색 (자동완성용)
     */
    @Query("SELECT t FROM Tag t WHERE t.tagName LIKE %:keyword% AND t.isUserSelectable = true ORDER BY t.displayOrder")
    List<Tag> findByTagNameContaining(@Param("keyword") String keyword);
    
    /**
     * 태그명 부분 검색 (페이징)
     */
    @Query("SELECT t FROM Tag t WHERE t.tagName LIKE %:keyword% ORDER BY t.usageCount DESC, t.displayOrder")
    Page<Tag> findByTagNameContaining(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 태그 타입별 키워드 검색
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.tagName LIKE %:keyword% ORDER BY t.usageCount DESC")
    List<Tag> findByTagTypeAndTagNameContaining(@Param("tagType") TagType tagType, @Param("keyword") String keyword);
    
    /**
     * 설명 포함 키워드 검색
     */
    @Query("SELECT t FROM Tag t WHERE (t.tagName LIKE %:keyword% OR t.description LIKE %:keyword%) ORDER BY t.usageCount DESC")
    List<Tag> findByTagNameOrDescriptionContaining(@Param("keyword") String keyword);
    
    // ===== 인기 태그 및 통계 =====
    
    /**
     * 인기 태그 조회 (사용 빈도 기준)
     */
    @Query("SELECT t FROM Tag t WHERE t.usageCount > 0 ORDER BY t.usageCount DESC")
    List<Tag> findPopularTagsByUsage(Pageable pageable);
    
    /**
     * 태그 타입별 인기 태그
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.usageCount > 0 ORDER BY t.usageCount DESC")
    List<Tag> findPopularTagsByTypeAndUsage(@Param("tagType") TagType tagType, Pageable pageable);
    
    /**
     * 태그 타입별 개수 통계
     */
    @Query("SELECT t.tagType, COUNT(t) FROM Tag t GROUP BY t.tagType ORDER BY t.tagType")
    List<Object[]> countByTagType();
    
    /**
     * 사용되지 않은 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.usageCount = 0 OR t.usageCount IS NULL ORDER BY t.createdAt DESC")
    List<Tag> findUnusedTags();
    
    /**
     * 특정 사용 빈도 이상 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.usageCount >= :minUsage ORDER BY t.usageCount DESC")
    List<Tag> findTagsWithMinUsage(@Param("minUsage") Integer minUsage);
    
    // ===== 태그 관리 =====
    
    /**
     * 사용 횟수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.usageCount = COALESCE(t.usageCount, 0) + 1 WHERE t.tagId = :tagId")
    int incrementUsageCount(@Param("tagId") Long tagId);
    
    /**
     * 사용 횟수 일괄 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.usageCount = COALESCE(t.usageCount, 0) + 1 WHERE t.tagId IN :tagIds")
    int incrementUsageCountBatch(@Param("tagIds") List<Long> tagIds);
    
    /**
     * 태그 활성화 상태 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.isUserSelectable = :userSelectable, t.isRouteTaggable = :routeTaggable WHERE t.tagId = :tagId")
    int updateTagFlags(@Param("tagId") Long tagId, 
                      @Param("userSelectable") boolean userSelectable, 
                      @Param("routeTaggable") boolean routeTaggable);
    
    /**
     * 표시 순서 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.displayOrder = :displayOrder WHERE t.tagId = :tagId")
    int updateDisplayOrder(@Param("tagId") Long tagId, @Param("displayOrder") Integer displayOrder);
    
    // ===== 고급 조회 =====
    
    /**
     * 모든 활성 태그 조회 (양방향)
     */
    @Query("SELECT t FROM Tag t WHERE t.isUserSelectable = true AND t.isRouteTaggable = true ORDER BY t.tagType, t.displayOrder")
    List<Tag> findAllActiveBidirectionalTags();
    
    /**
     * 태그 타입별 활성 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType IN :tagTypes AND (t.isUserSelectable = true OR t.isRouteTaggable = true) ORDER BY t.tagType, t.displayOrder")
    List<Tag> findActiveTagsByTypes(@Param("tagTypes") List<TagType> tagTypes);
    
    /**
     * 중복 태그명 확인
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Tag t WHERE t.tagName = :tagName")
    boolean existsByTagName(@Param("tagName") String tagName);
    
    /**
     * 태그 카테고리별 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.tagCategory = :category ORDER BY t.displayOrder")
    List<Tag> findByTagCategory(@Param("category") String category);
    
    // ===== 추천 시스템 지원 =====
    
    /**
     * 추천 가능한 태그 조회 (사용자 선택 가능 + 최소 사용 빈도)
     */
    @Query("SELECT t FROM Tag t WHERE t.isUserSelectable = true AND t.usageCount >= :minUsage ORDER BY t.usageCount DESC")
    List<Tag> findRecommendableTags(@Param("minUsage") Integer minUsage);
    
    /**
     * 특정 태그 타입의 추천 태그
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.isUserSelectable = true AND t.usageCount >= :minUsage ORDER BY t.usageCount DESC")
    List<Tag> findRecommendableTagsByType(@Param("tagType") TagType tagType, @Param("minUsage") Integer minUsage);
}
```

### TagRepositoryCustom.java - Custom Repository 인터페이스
```java
package com.routepick.domain.tag.repository;

import com.routepick.common.enums.TagType;
import com.routepick.domain.tag.entity.Tag;

import java.util.List;

/**
 * Tag Repository Custom 인터페이스
 * - QueryDSL 기반 복잡한 태그 검색
 */
public interface TagRepositoryCustom {
    
    /**
     * 복합 조건 태그 검색
     */
    List<Tag> findTagsByComplexConditions(List<TagType> tagTypes, String keyword, 
                                         Boolean userSelectable, Boolean routeTaggable, 
                                         Integer minUsage);
    
    /**
     * 태그 사용 통계 분석
     */
    List<TagUsageStatistics> getTagUsageStatistics();
    
    /**
     * 유사 태그 찾기 (이름 기반)
     */
    List<Tag> findSimilarTags(String tagName, int maxResults);
    
    /**
     * 트렌딩 태그 조회 (최근 사용 빈도 증가)
     */
    List<Tag> findTrendingTags(int days, int maxResults);
}
```

---

## 👤 2. UserPreferredTagRepository - 사용자 선호 태그 Repository

```java
package com.routepick.domain.tag.repository;

import com.routepick.common.enums.PreferenceLevel;
import com.routepick.common.enums.SkillLevel;
import com.routepick.common.enums.TagType;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.tag.entity.UserPreferredTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * UserPreferredTag Repository
 * - 사용자 선호도 관리 최적화
 * - 개인화 추천 지원
 * - 유사 사용자 매칭
 */
@Repository
public interface UserPreferredTagRepository extends BaseRepository<UserPreferredTag, Long>, UserPreferredTagRepositoryCustom {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자의 모든 선호 태그 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC, t.displayOrder")
    List<UserPreferredTag> findByUserIdOrderByPreferenceLevel(@Param("userId") Long userId);
    
    /**
     * 사용자의 특정 선호도 태그 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.preferenceLevel IN :preferenceLevels AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC")
    List<UserPreferredTag> findByUserIdAndPreferenceLevelIn(@Param("userId") Long userId, 
                                                           @Param("preferenceLevels") List<PreferenceLevel> preferenceLevels);
    
    /**
     * 사용자의 특정 숙련도 태그 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.skillLevel = :skillLevel AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC")
    List<UserPreferredTag> findByUserIdAndSkillLevel(@Param("userId") Long userId, 
                                                    @Param("skillLevel") SkillLevel skillLevel);
    
    /**
     * 사용자-태그 조합 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId = :tagId")
    Optional<UserPreferredTag> findByUserIdAndTagId(@Param("userId") Long userId, @Param("tagId") Long tagId);
    
    // ===== 중복 확인 및 존재 여부 =====
    
    /**
     * 중복 선호 태그 확인
     */
    @Query("SELECT CASE WHEN COUNT(upt) > 0 THEN true ELSE false END FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId = :tagId AND upt.isActive = true")
    boolean existsByUserIdAndTagId(@Param("userId") Long userId, @Param("tagId") Long tagId);
    
    /**
     * 사용자의 선호 태그 수 조회
     */
    @Query("SELECT COUNT(upt) FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true")
    long countByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 선호도별 태그 수 조회
     */
    @Query("SELECT upt.preferenceLevel, COUNT(upt) FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "GROUP BY upt.preferenceLevel")
    List<Object[]> countByUserIdAndPreferenceLevel(@Param("userId") Long userId);
    
    // ===== 태그 타입별 선호도 =====
    
    /**
     * 사용자의 태그 타입별 선호 태그
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND t.tagType = :tagType AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC")
    List<UserPreferredTag> findByUserIdAndTagType(@Param("userId") Long userId, @Param("tagType") TagType tagType);
    
    /**
     * 태그 타입별 높은 선호도 태그만 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND t.tagType = :tagType " +
           "AND upt.preferenceLevel = 'HIGH' AND upt.isActive = true " +
           "ORDER BY t.displayOrder")
    List<UserPreferredTag> findHighPreferenceByUserIdAndTagType(@Param("userId") Long userId, 
                                                              @Param("tagType") TagType tagType);
    
    // ===== 선호도 업데이트 =====
    
    /**
     * 선호도 레벨 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserPreferredTag upt SET upt.preferenceLevel = :preferenceLevel " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId = :tagId")
    int updatePreferenceLevel(@Param("userId") Long userId, 
                             @Param("tagId") Long tagId, 
                             @Param("preferenceLevel") PreferenceLevel preferenceLevel);
    
    /**
     * 숙련도 레벨 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserPreferredTag upt SET upt.skillLevel = :skillLevel " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId = :tagId")
    int updateSkillLevel(@Param("userId") Long userId, 
                        @Param("tagId") Long tagId, 
                        @Param("skillLevel") SkillLevel skillLevel);
    
    /**
     * 선호 태그 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserPreferredTag upt SET upt.isActive = false " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId IN :tagIds")
    int deactivatePreferenceTags(@Param("userId") Long userId, @Param("tagIds") List<Long> tagIds);
    
    /**
     * 선호 태그 일괄 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId IN :tagIds")
    int deleteByUserIdAndTagIdIn(@Param("userId") Long userId, @Param("tagIds") List<Long> tagIds);
    
    // ===== 유사 사용자 찾기 =====
    
    /**
     * 특정 태그를 선호하는 사용자들 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.user u " +
           "WHERE upt.tag.tagId = :tagId AND upt.preferenceLevel IN ('MEDIUM', 'HIGH') AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC")
    List<UserPreferredTag> findUsersByPreferredTag(@Param("tagId") Long tagId);
    
    /**
     * 유사한 선호도를 가진 사용자 찾기
     */
    @Query("SELECT upt2.user.userId, COUNT(*) as commonTags FROM UserPreferredTag upt1 " +
           "JOIN UserPreferredTag upt2 ON upt1.tag.tagId = upt2.tag.tagId " +
           "WHERE upt1.user.userId = :userId AND upt2.user.userId != :userId " +
           "AND upt1.isActive = true AND upt2.isActive = true " +
           "AND upt1.preferenceLevel = upt2.preferenceLevel " +
           "GROUP BY upt2.user.userId " +
           "HAVING COUNT(*) >= :minCommonTags " +
           "ORDER BY commonTags DESC")
    List<Object[]> findUsersByCommonPreferences(@Param("userId") Long userId, 
                                               @Param("minCommonTags") int minCommonTags);
    
    // ===== 추천 태그 발견 =====
    
    /**
     * 사용자에게 추천할 수 있는 태그 찾기
     */
    @Query("SELECT t.tagId, COUNT(*) as recommendCount FROM UserPreferredTag upt1 " +
           "JOIN UserPreferredTag upt2 ON upt1.user.userId != upt2.user.userId " +
           "JOIN Tag t ON upt2.tag.tagId = t.tagId " +
           "WHERE upt1.user.userId = :userId " +
           "AND upt1.tag.tagId IN (SELECT upt3.tag.tagId FROM UserPreferredTag upt3 WHERE upt3.user.userId = upt2.user.userId) " +
           "AND t.tagId NOT IN (SELECT upt4.tag.tagId FROM UserPreferredTag upt4 WHERE upt4.user.userId = :userId AND upt4.isActive = true) " +
           "AND t.isUserSelectable = true " +
           "AND upt2.isActive = true " +
           "GROUP BY t.tagId " +
           "ORDER BY recommendCount DESC")
    List<Object[]> findRecommendedTagsForUser(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 통계 및 분석 =====
    
    /**
     * 가장 인기 있는 태그 조회
     */
    @Query("SELECT upt.tag.tagId, upt.tag.tagName, COUNT(*) as userCount FROM UserPreferredTag upt " +
           "WHERE upt.isActive = true " +
           "GROUP BY upt.tag.tagId, upt.tag.tagName " +
           "ORDER BY userCount DESC")
    List<Object[]> findMostPopularTags(Pageable pageable);
    
    /**
     * 태그별 평균 선호도 조회
     */
    @Query("SELECT upt.tag.tagId, upt.tag.tagName, " +
           "AVG(CASE upt.preferenceLevel WHEN 'HIGH' THEN 100 WHEN 'MEDIUM' THEN 70 WHEN 'LOW' THEN 30 END) as avgPreference " +
           "FROM UserPreferredTag upt " +
           "WHERE upt.isActive = true " +
           "GROUP BY upt.tag.tagId, upt.tag.tagName " +
           "ORDER BY avgPreference DESC")
    List<Object[]> findTagsWithAveragePreference();
    
    /**
     * 사용자의 선호도 프로필 분석
     */
    @Query("SELECT t.tagType, " +
           "COUNT(*) as tagCount, " +
           "AVG(CASE upt.preferenceLevel WHEN 'HIGH' THEN 100 WHEN 'MEDIUM' THEN 70 WHEN 'LOW' THEN 30 END) as avgPreference " +
           "FROM UserPreferredTag upt " +
           "JOIN upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "GROUP BY t.tagType " +
           "ORDER BY avgPreference DESC")
    List<Object[]> analyzeUserPreferenceProfile(@Param("userId") Long userId);
}
```

### UserPreferredTagRepositoryCustom.java - Custom Repository 인터페이스
```java
package com.routepick.domain.tag.repository;

import com.routepick.domain.tag.entity.UserPreferredTag;
import com.routepick.domain.user.entity.User;

import java.util.List;

/**
 * UserPreferredTag Repository Custom 인터페이스
 * - QueryDSL 기반 복잡한 선호도 분석
 */
public interface UserPreferredTagRepositoryCustom {
    
    /**
     * 사용자 선호도 유사성 분석
     */
    List<UserSimilarityResult> findSimilarUsers(Long userId, int maxResults);
    
    /**
     * 개인화된 태그 추천
     */
    List<TagRecommendationResult> recommendTagsForUser(Long userId, int maxResults);
    
    /**
     * 사용자 클러스터링 (선호도 기반)
     */
    List<UserCluster> clusterUsersByPreferences(int clusterCount);
    
    /**
     * 선호도 변화 추적
     */
    List<PreferenceChangeResult> trackPreferenceChanges(Long userId, int days);
}
```

---

## 🧗‍♂️ 3. RouteTagRepository - 루트 태그 연관 Repository

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

## 🎯 4. UserRouteRecommendationRepository - 추천 시스템 Repository

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

## ⚡ 5. 성능 최적화 전략

### Full-Text Index 활용
```sql
-- 태그 검색을 위한 Full-Text Index
ALTER TABLE tags ADD FULLTEXT(tag_name, description);

-- 태그 검색 쿼리 최적화
SELECT * FROM tags 
WHERE MATCH(tag_name, description) AGAINST('볼더링 크림핑' IN BOOLEAN MODE)
ORDER BY usage_count DESC;
```

### 복합 인덱스 최적화
```sql
-- 추천 시스템 핵심 인덱스
CREATE INDEX idx_user_recommendation_score ON user_route_recommendations (user_id, recommendation_score DESC, is_active);

-- 태그 매칭 최적화
CREATE INDEX idx_route_tag_relevance ON route_tags (route_id, relevance_score DESC);

-- 선호도 분석 최적화
CREATE INDEX idx_user_preference_analysis ON user_preferred_tags (user_id, preference_level, tag_id);
```

### 쿼리 캐싱 전략
```java
@Cacheable(value = "popularTags", key = "#tagType")
List<Tag> findPopularTagsByType(TagType tagType);

@Cacheable(value = "userRecommendations", key = "#userId")
List<UserRouteRecommendation> findTopRecommendations(Long userId);

@CacheEvict(value = "tagStatistics", allEntries = true)
void refreshTagStatistics();
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
```

---

## ✅ 설계 완료 체크리스트

### 태그 시스템 Repository (4개)
- [x] TagRepository - 마스터 태그 관리, 검색 최적화
- [x] UserPreferredTagRepository - 선호도 관리, 유사 사용자 매칭
- [x] RouteTagRepository - 연관도 관리, 품질 분석
- [x] UserRouteRecommendationRepository - 추천 시스템 지원

### Custom Repository 인터페이스
- [x] TagRepositoryCustom - 복합 조건 태그 검색
- [x] UserPreferredTagRepositoryCustom - 선호도 분석
- [x] RouteTagRepositoryCustom - 연관도 계산
- [x] RecommendationRepositoryCustom - 추천 알고리즘 최적화

### 성능 최적화 즉시 반영
- [x] Full-Text Index 태그 검색 최적화
- [x] 복합 인덱스 추천 시스템 최적화
- [x] 쿼리 캐싱 전략 설계
- [x] 배치 처리 지원 구조

### 고급 기능
- [x] 태그 자동완성 지원
- [x] 유사 사용자 매칭 알고리즘
- [x] 추천 다양성 최적화
- [x] 실시간 추천 갱신 지원

---

**다음 단계**: Step 5-3 Gym, Route 도메인 Repository 설계  
**완료일**: 2025-08-20  
**핵심 성과**: 태그 시스템 4개 Repository + 성능 최적화 + 추천 알고리즘 지원 완료