# step5-2a1_tag_master_repository.md

> 태그 마스터 Repository - 태그 검색, 통계, 관리 최적화
> 생성일: 2025-08-27  
> 단계: 5-2a1 (태그 마스터 Repository)
> 참고: step5-2a, step4-2a, step6-3a

---

## 🏷️ TagRepository - 마스터 태그 Repository 완전체

### 핵심 설계 목표
- **고성능 태그 검색**: Full-Text Index 기반 실시간 자동완성
- **태그 통계 분석**: 사용 빈도, 인기도, 트렌드 분석 최적화  
- **마스터 데이터 품질**: 중복 방지, 일관성 유지, 정합성 보장
- **추천 시스템 지원**: AI 추천 알고리즘 데이터 공급 최적화

---

## 📊 TagRepository.java - 마스터 태그 Repository

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
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.isActive = true ORDER BY t.displayOrder, t.tagName")
    List<Tag> findByTagTypeOrderByDisplayOrder(@Param("tagType") TagType tagType);
    
    /**
     * 사용자 선택 가능한 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.isUserSelectable = true AND t.isActive = true ORDER BY t.displayOrder")
    List<Tag> findByTagTypeAndIsUserSelectable(@Param("tagType") TagType tagType);
    
    /**
     * 루트 태깅 가능한 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.isRouteTaggable = true AND t.isActive = true ORDER BY t.displayOrder")
    List<Tag> findByTagTypeAndIsRouteTaggable(@Param("tagType") TagType tagType);
    
    /**
     * 모든 활성 태그 조회 (플래그 무관)
     */
    @Query("SELECT t FROM Tag t WHERE t.isActive = true ORDER BY t.tagType, t.displayOrder")
    List<Tag> findAllActiveTags();
    
    // ===== 태그 검색 및 자동완성 =====
    
    /**
     * 태그명 부분 검색 (자동완성용)
     */
    @Query("SELECT t FROM Tag t WHERE t.tagName LIKE %:keyword% AND t.isUserSelectable = true AND t.isActive = true ORDER BY t.usageCount DESC, t.displayOrder")
    List<Tag> findByTagNameContaining(@Param("keyword") String keyword);
    
    /**
     * 태그명 부분 검색 (페이징)
     */
    @Query("SELECT t FROM Tag t WHERE t.tagName LIKE %:keyword% AND t.isActive = true ORDER BY t.usageCount DESC, t.displayOrder")
    Page<Tag> findByTagNameContaining(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 태그 타입별 키워드 검색
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.tagName LIKE %:keyword% AND t.isActive = true ORDER BY t.usageCount DESC")
    List<Tag> findByTagTypeAndTagNameContaining(@Param("tagType") TagType tagType, @Param("keyword") String keyword);
    
    /**
     * 설명 포함 키워드 검색 (Full-Text 검색)
     */
    @Query("SELECT t FROM Tag t WHERE (t.tagName LIKE %:keyword% OR t.tagDescription LIKE %:keyword%) AND t.isActive = true ORDER BY t.usageCount DESC")
    List<Tag> findByTagNameOrDescriptionContaining(@Param("keyword") String keyword);
    
    /**
     * 고급 키워드 검색 (정확도 기반 정렬)
     */
    @Query(value = "SELECT * FROM tags t WHERE " +
           "(MATCH(t.tag_name, t.tag_description) AGAINST(?1 IN BOOLEAN MODE) " +
           "OR t.tag_name LIKE CONCAT('%', ?1, '%')) " +
           "AND t.is_active = true " +
           "ORDER BY " +
           "CASE " +
           "  WHEN t.tag_name = ?1 THEN 1000 " +
           "  WHEN t.tag_name LIKE CONCAT(?1, '%') THEN 900 " +
           "  WHEN t.tag_name LIKE CONCAT('%', ?1, '%') THEN 800 " +
           "  ELSE MATCH(t.tag_name, t.tag_description) AGAINST(?1 IN BOOLEAN MODE) * 100 " +
           "END DESC, " +
           "t.usage_count DESC " +
           "LIMIT ?2", nativeQuery = true)
    List<Tag> findByAdvancedKeywordSearch(String keyword, int limit);
    
    // ===== 인기 태그 및 통계 =====
    
    /**
     * 인기 태그 조회 (사용 빈도 기준)
     */
    @Query("SELECT t FROM Tag t WHERE t.usageCount > 0 AND t.isActive = true ORDER BY t.usageCount DESC")
    List<Tag> findPopularTagsByUsage(Pageable pageable);
    
    /**
     * 태그 타입별 인기 태그
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.usageCount > 0 AND t.isActive = true ORDER BY t.usageCount DESC")
    List<Tag> findPopularTagsByTypeAndUsage(@Param("tagType") TagType tagType, Pageable pageable);
    
    /**
     * 최근 인기 상승 태그 (지난 30일)
     */
    @Query(value = "SELECT t.*, " +
           "(t.usage_count - COALESCE(th.previous_usage_count, 0)) as growth " +
           "FROM tags t " +
           "LEFT JOIN tag_usage_history th ON t.tag_id = th.tag_id " +
           "AND th.recorded_date = DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
           "WHERE t.is_active = true " +
           "AND (t.usage_count - COALESCE(th.previous_usage_count, 0)) > 0 " +
           "ORDER BY growth DESC, t.usage_count DESC " +
           "LIMIT ?1", nativeQuery = true)
    List<Tag> findTrendingTags(int limit);
    
    /**
     * 태그 타입별 개수 통계
     */
    @Query("SELECT t.tagType, COUNT(t) FROM Tag t WHERE t.isActive = true GROUP BY t.tagType ORDER BY t.tagType")
    List<Object[]> countByTagType();
    
    /**
     * 사용되지 않은 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE (t.usageCount = 0 OR t.usageCount IS NULL) AND t.isActive = true ORDER BY t.createdAt DESC")
    List<Tag> findUnusedTags();
    
    /**
     * 특정 사용 빈도 이상 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.usageCount >= :minUsage AND t.isActive = true ORDER BY t.usageCount DESC")
    List<Tag> findTagsWithMinUsage(@Param("minUsage") Integer minUsage);
    
    /**
     * 태그 사용 분포 통계
     */
    @Query(value = "SELECT " +
           "CASE " +
           "  WHEN t.usage_count = 0 THEN 'UNUSED' " +
           "  WHEN t.usage_count BETWEEN 1 AND 10 THEN 'LOW' " +
           "  WHEN t.usage_count BETWEEN 11 AND 100 THEN 'MEDIUM' " +
           "  WHEN t.usage_count BETWEEN 101 AND 1000 THEN 'HIGH' " +
           "  ELSE 'VERY_HIGH' " +
           "END as usage_category, " +
           "COUNT(*) as tag_count " +
           "FROM tags t " +
           "WHERE t.is_active = true " +
           "GROUP BY usage_category " +
           "ORDER BY tag_count DESC", nativeQuery = true)
    List<Object[]> getTagUsageDistribution();
    
    // ===== 태그 관리 =====
    
    /**
     * 사용 횟수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.usageCount = COALESCE(t.usageCount, 0) + 1, t.modifiedAt = CURRENT_TIMESTAMP WHERE t.tagId = :tagId")
    int incrementUsageCount(@Param("tagId") Long tagId);
    
    /**
     * 사용 횟수 일괄 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.usageCount = COALESCE(t.usageCount, 0) + 1, t.modifiedAt = CURRENT_TIMESTAMP WHERE t.tagId IN :tagIds")
    int incrementUsageCountBatch(@Param("tagIds") List<Long> tagIds);
    
    /**
     * 사용 횟수 특정 값으로 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.usageCount = COALESCE(t.usageCount, 0) + :increment, t.modifiedAt = CURRENT_TIMESTAMP WHERE t.tagId = :tagId")
    int incrementUsageCountByValue(@Param("tagId") Long tagId, @Param("increment") int increment);
    
    /**
     * 태그 활성화 상태 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.isUserSelectable = :userSelectable, t.isRouteTaggable = :routeTaggable, t.modifiedAt = CURRENT_TIMESTAMP WHERE t.tagId = :tagId")
    int updateTagFlags(@Param("tagId") Long tagId, 
                      @Param("userSelectable") boolean userSelectable, 
                      @Param("routeTaggable") boolean routeTaggable);
    
    /**
     * 표시 순서 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.displayOrder = :displayOrder, t.modifiedAt = CURRENT_TIMESTAMP WHERE t.tagId = :tagId")
    int updateDisplayOrder(@Param("tagId") Long tagId, @Param("displayOrder") Integer displayOrder);
    
    /**
     * 태그 비활성화 (soft delete)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.isActive = false, t.modifiedAt = CURRENT_TIMESTAMP WHERE t.tagId = :tagId")
    int deactivateTag(@Param("tagId") Long tagId);
    
    /**
     * 태그 일괄 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tag t SET t.isActive = false, t.modifiedAt = CURRENT_TIMESTAMP WHERE t.tagId IN :tagIds")
    int deactivateTagsBatch(@Param("tagIds") List<Long> tagIds);
    
    // ===== 고급 조회 =====
    
    /**
     * 모든 활성 태그 조회 (양방향)
     */
    @Query("SELECT t FROM Tag t WHERE t.isUserSelectable = true AND t.isRouteTaggable = true AND t.isActive = true ORDER BY t.tagType, t.displayOrder")
    List<Tag> findAllActiveBidirectionalTags();
    
    /**
     * 태그 타입별 활성 태그 조회
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType IN :tagTypes AND (t.isUserSelectable = true OR t.isRouteTaggable = true) AND t.isActive = true ORDER BY t.tagType, t.displayOrder")
    List<Tag> findActiveTagsByTypes(@Param("tagTypes") List<TagType> tagTypes);
    
    /**
     * 중복 태그명 확인
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Tag t WHERE t.tagName = :tagName AND t.isActive = true")
    boolean existsByTagName(@Param("tagName") String tagName);
    
    /**
     * 태그명 중복 확인 (대소문자 무관)
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Tag t WHERE LOWER(t.tagName) = LOWER(:tagName) AND t.isActive = true")
    boolean existsByTagNameIgnoreCase(@Param("tagName") String tagName);
    
    // ===== 추천 시스템 지원 =====
    
    /**
     * 추천 가능한 태그 조회 (사용자 선택 가능 + 최소 사용 빈도)
     */
    @Query("SELECT t FROM Tag t WHERE t.isUserSelectable = true AND t.usageCount >= :minUsage AND t.isActive = true ORDER BY t.usageCount DESC")
    List<Tag> findRecommendableTags(@Param("minUsage") Integer minUsage);
    
    /**
     * 특정 태그 타입의 추천 태그
     */
    @Query("SELECT t FROM Tag t WHERE t.tagType = :tagType AND t.isUserSelectable = true AND t.usageCount >= :minUsage AND t.isActive = true ORDER BY t.usageCount DESC")
    List<Tag> findRecommendableTagsByType(@Param("tagType") TagType tagType, @Param("minUsage") Integer minUsage);
    
    /**
     * 신규 사용자를 위한 기본 추천 태그
     */
    @Query("SELECT t FROM Tag t WHERE t.isUserSelectable = true AND t.usageCount >= :minUsage AND t.isActive = true " +
           "AND t.tagType IN ('STYLE', 'TECHNIQUE', 'HOLD_TYPE') " +
           "ORDER BY t.usageCount DESC")
    List<Tag> findNewUserRecommendedTags(@Param("minUsage") Integer minUsage, Pageable pageable);
    
    // ===== 성능 최적화 쿼리 =====
    
    /**
     * 태그 ID 목록으로 태그명만 조회 (성능 최적화)
     */
    @Query("SELECT t.tagId, t.tagName FROM Tag t WHERE t.tagId IN :tagIds AND t.isActive = true")
    List<Object[]> findTagNamesByIds(@Param("tagIds") List<Long> tagIds);
    
    /**
     * 특정 태그 타입의 활성 태그 ID 목록
     */
    @Query("SELECT t.tagId FROM Tag t WHERE t.tagType = :tagType AND t.isActive = true ORDER BY t.displayOrder")
    List<Long> findActiveTagIdsByType(@Param("tagType") TagType tagType);
    
    /**
     * 사용 빈도 Top N 태그 ID 목록
     */
    @Query("SELECT t.tagId FROM Tag t WHERE t.usageCount > 0 AND t.isActive = true ORDER BY t.usageCount DESC")
    List<Long> findTopUsageTagIds(Pageable pageable);
}
```

---

## 🔍 TagRepositoryCustom - Custom 인터페이스

```java
package com.routepick.domain.tag.repository;

import com.routepick.common.enums.TagType;
import com.routepick.domain.tag.entity.Tag;
import com.routepick.dto.tag.response.TagUsageStatistics;

import java.util.List;

/**
 * Tag Repository Custom 인터페이스
 * - QueryDSL 기반 복잡한 태그 검색
 * - 고급 통계 및 분석 기능
 */
public interface TagRepositoryCustom {
    
    /**
     * 복합 조건 태그 검색
     */
    List<Tag> findTagsByComplexConditions(List<TagType> tagTypes, 
                                         String keyword, 
                                         Boolean userSelectable, 
                                         Boolean routeTaggable, 
                                         Integer minUsage,
                                         Integer maxResults);
    
    /**
     * 태그 사용 통계 분석
     */
    List<TagUsageStatistics> getTagUsageStatistics();
    
    /**
     * 유사 태그 찾기 (이름 기반 Levenshtein Distance)
     */
    List<Tag> findSimilarTags(String tagName, int maxResults);
    
    /**
     * 트렌딩 태그 조회 (최근 사용 빈도 증가)
     */
    List<Tag> findTrendingTags(int days, int maxResults);
    
    /**
     * 태그 공출현 분석 (함께 사용되는 태그)
     */
    List<Object[]> findCoOccurrentTags(Long tagId, int maxResults);
    
    /**
     * 사용자별 태그 사용 패턴 분석
     */
    List<Object[]> analyzeUserTagUsagePatterns(Long userId);
    
    /**
     * 태그 클러스터링 (유사성 기반)
     */
    List<Object[]> clusterTagsBySimilarity(int clusterCount);
    
    /**
     * 계절별 태그 트렌드 분석
     */
    List<Object[]> analyzeSeasonalTagTrends();
}
```

---

## ⚡ 성능 최적화 전략

### 1. 인덱스 최적화
```sql
-- 태그 검색 성능 최적화
CREATE INDEX idx_tags_search_basic ON tags (is_active, tag_name, usage_count DESC);
CREATE INDEX idx_tags_type_selectable ON tags (tag_type, is_user_selectable, is_active, display_order);
CREATE INDEX idx_tags_usage_analysis ON tags (usage_count DESC, is_active, tag_type);

-- Full-Text 검색 인덱스
ALTER TABLE tags ADD FULLTEXT(tag_name, tag_description);

-- 복합 검색 최적화
CREATE INDEX idx_tags_complex_search ON tags (tag_type, is_user_selectable, is_route_taggable, usage_count DESC);
```

### 2. 쿼리 캐싱 전략
```java
// 인기 태그 캐싱 (1시간)
@Cacheable(value = "popularTags", key = "#tagType + '_' + #pageable.pageNumber", 
           unless = "#result.isEmpty()")
@CacheEvict(value = "popularTags", allEntries = true, condition = "#result.size() > 0")
List<Tag> findPopularTagsByType(TagType tagType, Pageable pageable);

// 자동완성 캐싱 (30분)
@Cacheable(value = "tagAutocomplete", key = "#keyword", 
           condition = "#keyword.length() >= 2")
List<Tag> findByTagNameContaining(String keyword);

// 태그 통계 캐싱 (6시간)
@Cacheable(value = "tagStatistics", key = "'usage_distribution'")
List<Object[]> getTagUsageDistribution();
```

### 3. 배치 처리 최적화
```java
// 사용 빈도 배치 업데이트 (매일 새벽 2시)
@Scheduled(cron = "0 0 2 * * ?")
@Modifying
@Query(value = "UPDATE tags t SET t.usage_count = (" +
               "  SELECT COUNT(*) FROM route_tags rt WHERE rt.tag_id = t.tag_id" +
               ") + (" +
               "  SELECT COUNT(*) FROM user_preferred_tags upt WHERE upt.tag_id = t.tag_id AND upt.is_active = true" +
               ")", nativeQuery = true)
int batchUpdateUsageCounts();
```

---

## 📊 성능 메트릭 및 모니터링

### 주요 성능 지표
- **태그 검색 응답 시간**: < 100ms (자동완성), < 500ms (고급 검색)
- **인기 태그 조회**: < 50ms (캐싱 적용)
- **통계 분석**: < 2초 (복잡한 집계 쿼리)
- **사용 빈도 업데이트**: 배치 처리로 실시간 성능 영향 최소화

### 캐시 전략
- **인기 태그**: 1시간 캐싱, 태그 추가/수정 시 즉시 무효화
- **자동완성**: 30분 캐싱, 2글자 이상 검색어만 캐싱
- **통계 데이터**: 6시간 캐싱, 매일 새벽 자동 갱신

---

*태그 마스터 Repository 완성일: 2025-08-27*  
*분할 원본: step5-2a_tag_core_repositories.md (300줄)*  
*주요 기능: 고성능 검색, 통계 분석, 추천 시스템 지원*  
*다음 단계: UserPreferredTag Repository 구현*