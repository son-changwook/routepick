# Step 5-2a: 태그 핵심 Repository - Tag & UserPreferredTag

> 태그 시스템 핵심 2개 Repository 설계 (마스터 태그 & 사용자 선호도)  
> 생성일: 2025-08-21  
> 기반: step5-2_tag_repositories_focused.md 세분화  
> 포함 Repository: TagRepository, UserPreferredTagRepository

---

## 📋 파일 세분화 정보
- **원본 파일**: step5-2_tag_repositories_focused.md (1,286줄)
- **세분화 사유**: 토큰 제한 대응 및 기능별 책임 분리
- **이 파일 포함**: TagRepository, UserPreferredTagRepository
- **다른 파일**: step5-2b_tag_route_repositories.md (RouteTagRepository, UserRouteRecommendationRepository)

---

## 🎯 설계 목표

- **태그 검색 성능 최적화**: 자동완성, 실시간 검색, Full-Text Index 활용
- **사용자 선호도 관리**: 개인화 추천 기반, 유사 사용자 매칭
- **태그 통계 및 분석**: 사용 빈도, 인기도, 트렌드 분석
- **마스터 데이터 품질**: 태그 중복 방지, 일관성 유지

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

## ⚡ 성능 최적화 전략

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
-- 태그 타입별 검색 최적화
CREATE INDEX idx_tag_type_usage ON tags (tag_type, usage_count DESC, is_user_selectable);

-- 선호도 분석 최적화
CREATE INDEX idx_user_preference_analysis ON user_preferred_tags (user_id, preference_level, tag_id);

-- 유사 사용자 매칭 최적화
CREATE INDEX idx_tag_preference_match ON user_preferred_tags (tag_id, preference_level, is_active);
```

### 쿼리 캐싱 전략
```java
@Cacheable(value = "popularTags", key = "#tagType")
List<Tag> findPopularTagsByType(TagType tagType);

@Cacheable(value = "userPreferences", key = "#userId")
List<UserPreferredTag> findActivePreferencesByUserId(Long userId);

@CacheEvict(value = "tagStatistics", allEntries = true)
void refreshTagStatistics();
```

---

## ✅ 설계 완료 체크리스트

### 태그 핵심 Repository (2개)
- [x] TagRepository - 마스터 태그 관리, 검색 최적화
- [x] UserPreferredTagRepository - 선호도 관리, 유사 사용자 매칭

### Custom Repository 인터페이스
- [x] TagRepositoryCustom - 복합 조건 태그 검색
- [x] UserPreferredTagRepositoryCustom - 선호도 분석

### 핵심 기능
- [x] 태그 자동완성 지원
- [x] 사용자 선호도 분석
- [x] 유사 사용자 매칭 알고리즘
- [x] 태그 통계 및 트렌드 분석

### 성능 최적화
- [x] Full-Text Index 태그 검색 최적화
- [x] 복합 인덱스 설계
- [x] 쿼리 캐싱 전략

---

**다음 파일**: step5-2b_tag_route_repositories.md (RouteTagRepository, UserRouteRecommendationRepository)  
**완료일**: 2025-08-21  
**핵심 성과**: 태그 핵심 2개 Repository 완성 (마스터 태그 + 사용자 선호도)