# step5-2a2_user_preference_repository.md

> 사용자 선호 태그 Repository - 개인화 추천, 유사 사용자 매칭 최적화
> 생성일: 2025-08-27  
> 단계: 5-2a2 (사용자 선호 태그 Repository)
> 참고: step5-2a1, step6-3b, step6-3d1

---

## 👤 UserPreferredTagRepository - 사용자 선호도 완전체

### 핵심 설계 목표
- **개인화 추천 최적화**: 사용자 선호도 기반 AI 추천 알고리즘 지원
- **유사 사용자 매칭**: 공통 선호도 기반 사용자 클러스터링
- **선호도 분석**: 실시간 선호도 변화 추적 및 패턴 분석
- **성능 최적화**: 대용량 선호도 데이터 고속 처리

---

## 📊 UserPreferredTagRepository.java - 사용자 선호 Repository

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

import java.time.LocalDateTime;
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
     * 사용자의 모든 선호 태그 조회 (선호도 순 정렬)
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC, t.displayOrder, t.tagName")
    List<UserPreferredTag> findByUserIdOrderByPreferenceLevel(@Param("userId") Long userId);
    
    /**
     * 사용자의 특정 선호도 태그 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.preferenceLevel IN :preferenceLevels AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC, t.usageCount DESC")
    List<UserPreferredTag> findByUserIdAndPreferenceLevelIn(@Param("userId") Long userId, 
                                                           @Param("preferenceLevels") List<PreferenceLevel> preferenceLevels);
    
    /**
     * 사용자의 특정 숙련도 태그 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.skillLevel = :skillLevel AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC, t.displayOrder")
    List<UserPreferredTag> findByUserIdAndSkillLevel(@Param("userId") Long userId, 
                                                    @Param("skillLevel") SkillLevel skillLevel);
    
    /**
     * 사용자-태그 조합 조회 (활성/비활성 모두)
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId = :tagId")
    Optional<UserPreferredTag> findByUserIdAndTagId(@Param("userId") Long userId, @Param("tagId") Long tagId);
    
    /**
     * 사용자의 활성 선호 태그만 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "ORDER BY CASE upt.preferenceLevel " +
           "  WHEN 'HIGH' THEN 3 " +
           "  WHEN 'MEDIUM' THEN 2 " +
           "  WHEN 'LOW' THEN 1 " +
           "  ELSE 0 END DESC, t.usageCount DESC")
    List<UserPreferredTag> findActivePreferencesByUserId(@Param("userId") Long userId);
    
    // ===== 중복 확인 및 존재 여부 =====
    
    /**
     * 중복 선호 태그 확인 (활성만)
     */
    @Query("SELECT CASE WHEN COUNT(upt) > 0 THEN true ELSE false END FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId = :tagId AND upt.isActive = true")
    boolean existsByUserIdAndTagId(@Param("userId") Long userId, @Param("tagId") Long tagId);
    
    /**
     * 사용자의 선호 태그 수 조회 (활성만)
     */
    @Query("SELECT COUNT(upt) FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true")
    long countByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 선호도별 태그 수 조회
     */
    @Query("SELECT upt.preferenceLevel, COUNT(upt) FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "GROUP BY upt.preferenceLevel " +
           "ORDER BY CASE upt.preferenceLevel " +
           "  WHEN 'HIGH' THEN 3 " +
           "  WHEN 'MEDIUM' THEN 2 " +
           "  WHEN 'LOW' THEN 1 END DESC")
    List<Object[]> countByUserIdAndPreferenceLevel(@Param("userId") Long userId);
    
    /**
     * 특정 선호도 이상 태그 수 조회
     */
    @Query("SELECT COUNT(upt) FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "AND upt.preferenceLevel IN ('HIGH', 'MEDIUM')")
    long countHighAndMediumPreferences(@Param("userId") Long userId);
    
    // ===== 태그 타입별 선호도 =====
    
    /**
     * 사용자의 태그 타입별 선호 태그
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND t.tagType = :tagType AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC, t.usageCount DESC")
    List<UserPreferredTag> findByUserIdAndTagType(@Param("userId") Long userId, @Param("tagType") TagType tagType);
    
    /**
     * 태그 타입별 높은 선호도 태그만 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.tag t " +
           "WHERE upt.user.userId = :userId AND t.tagType = :tagType " +
           "AND upt.preferenceLevel = 'HIGH' AND upt.isActive = true " +
           "ORDER BY t.usageCount DESC, t.displayOrder")
    List<UserPreferredTag> findHighPreferenceByUserIdAndTagType(@Param("userId") Long userId, 
                                                              @Param("tagType") TagType tagType);
    
    /**
     * 사용자의 태그 타입별 선호도 분포
     */
    @Query("SELECT t.tagType, upt.preferenceLevel, COUNT(upt) FROM UserPreferredTag upt " +
           "JOIN upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "GROUP BY t.tagType, upt.preferenceLevel " +
           "ORDER BY t.tagType, upt.preferenceLevel DESC")
    List<Object[]> getPreferenceDistributionByTagType(@Param("userId") Long userId);
    
    // ===== 선호도 업데이트 =====
    
    /**
     * 선호도 레벨 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserPreferredTag upt SET upt.preferenceLevel = :preferenceLevel, upt.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId = :tagId")
    int updatePreferenceLevel(@Param("userId") Long userId, 
                             @Param("tagId") Long tagId, 
                             @Param("preferenceLevel") PreferenceLevel preferenceLevel);
    
    /**
     * 숙련도 레벨 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserPreferredTag upt SET upt.skillLevel = :skillLevel, upt.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId = :tagId")
    int updateSkillLevel(@Param("userId") Long userId, 
                        @Param("tagId") Long tagId, 
                        @Param("skillLevel") SkillLevel skillLevel);
    
    /**
     * 선호 태그 비활성화 (soft delete)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserPreferredTag upt SET upt.isActive = false, upt.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId IN :tagIds")
    int deactivatePreferenceTags(@Param("userId") Long userId, @Param("tagIds") List<Long> tagIds);
    
    /**
     * 선호 태그 재활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserPreferredTag upt SET upt.isActive = true, upt.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId = :tagId")
    int reactivatePreferenceTag(@Param("userId") Long userId, @Param("tagId") Long tagId);
    
    /**
     * 선호 태그 일괄 삭제 (hard delete)
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.tag.tagId IN :tagIds")
    int deleteByUserIdAndTagIdIn(@Param("userId") Long userId, @Param("tagIds") List<Long> tagIds);
    
    /**
     * 오래된 비활성 선호 태그 정리
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserPreferredTag upt " +
           "WHERE upt.isActive = false AND upt.modifiedAt < :cutoffDate")
    int cleanupInactivePreferences(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ===== 유사 사용자 찾기 =====
    
    /**
     * 특정 태그를 선호하는 사용자들 조회
     */
    @Query("SELECT upt FROM UserPreferredTag upt " +
           "JOIN FETCH upt.user u " +
           "WHERE upt.tag.tagId = :tagId AND upt.preferenceLevel IN ('MEDIUM', 'HIGH') AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC, u.createdAt DESC")
    List<UserPreferredTag> findUsersByPreferredTag(@Param("tagId") Long tagId);
    
    /**
     * 유사한 선호도를 가진 사용자 찾기 (공통 태그 기반)
     */
    @Query("SELECT upt2.user.userId, COUNT(*) as commonTags, " +
           "AVG(CASE WHEN upt1.preferenceLevel = upt2.preferenceLevel THEN 2.0 " +
           "    WHEN ABS(CAST(upt1.preferenceLevel AS int) - CAST(upt2.preferenceLevel AS int)) = 1 THEN 1.0 " +
           "    ELSE 0.5 END) as similarity " +
           "FROM UserPreferredTag upt1 " +
           "JOIN UserPreferredTag upt2 ON upt1.tag.tagId = upt2.tag.tagId " +
           "WHERE upt1.user.userId = :userId AND upt2.user.userId != :userId " +
           "AND upt1.isActive = true AND upt2.isActive = true " +
           "GROUP BY upt2.user.userId " +
           "HAVING COUNT(*) >= :minCommonTags " +
           "ORDER BY similarity DESC, commonTags DESC")
    List<Object[]> findUsersByCommonPreferences(@Param("userId") Long userId, 
                                               @Param("minCommonTags") int minCommonTags);
    
    /**
     * 반대 성향 사용자 찾기 (다양성 추천용)
     */
    @Query("SELECT upt2.user.userId, COUNT(*) as differentTags " +
           "FROM UserPreferredTag upt1 " +
           "JOIN UserPreferredTag upt2 ON upt1.tag.tagId = upt2.tag.tagId " +
           "WHERE upt1.user.userId = :userId AND upt2.user.userId != :userId " +
           "AND upt1.isActive = true AND upt2.isActive = true " +
           "AND upt1.preferenceLevel != upt2.preferenceLevel " +
           "GROUP BY upt2.user.userId " +
           "HAVING COUNT(*) >= :minDifferentTags " +
           "ORDER BY differentTags DESC")
    List<Object[]> findUsersWithOppositePreferences(@Param("userId") Long userId, 
                                                   @Param("minDifferentTags") int minDifferentTags);
    
    // ===== 추천 태그 발견 =====
    
    /**
     * 사용자에게 추천할 수 있는 태그 찾기 (협업 필터링)
     */
    @Query("SELECT t.tagId, t.tagName, COUNT(*) as recommendCount, " +
           "AVG(CASE upt2.preferenceLevel WHEN 'HIGH' THEN 100 WHEN 'MEDIUM' THEN 70 WHEN 'LOW' THEN 30 END) as avgPreference " +
           "FROM UserPreferredTag upt1 " +
           "JOIN UserPreferredTag similarUpt ON upt1.tag.tagId = similarUpt.tag.tagId " +
           "JOIN UserPreferredTag upt2 ON similarUpt.user.userId = upt2.user.userId " +
           "JOIN Tag t ON upt2.tag.tagId = t.tagId " +
           "WHERE upt1.user.userId = :userId " +
           "AND similarUpt.user.userId != :userId " +
           "AND t.tagId NOT IN (SELECT myUpt.tag.tagId FROM UserPreferredTag myUpt WHERE myUpt.user.userId = :userId AND myUpt.isActive = true) " +
           "AND t.isUserSelectable = true AND t.isActive = true " +
           "AND upt1.isActive = true AND similarUpt.isActive = true AND upt2.isActive = true " +
           "GROUP BY t.tagId, t.tagName " +
           "HAVING recommendCount >= :minRecommendCount " +
           "ORDER BY avgPreference DESC, recommendCount DESC")
    List<Object[]> findRecommendedTagsForUser(@Param("userId") Long userId, 
                                             @Param("minRecommendCount") int minRecommendCount, 
                                             Pageable pageable);
    
    /**
     * 신규 사용자를 위한 인기 태그 추천
     */
    @Query("SELECT upt.tag.tagId, upt.tag.tagName, t.tagType, COUNT(*) as userCount, " +
           "AVG(CASE upt.preferenceLevel WHEN 'HIGH' THEN 100 WHEN 'MEDIUM' THEN 70 WHEN 'LOW' THEN 30 END) as avgPreference " +
           "FROM UserPreferredTag upt " +
           "JOIN upt.tag t " +
           "WHERE upt.isActive = true AND t.isUserSelectable = true AND t.isActive = true " +
           "GROUP BY upt.tag.tagId, upt.tag.tagName, t.tagType " +
           "HAVING userCount >= :minUsers " +
           "ORDER BY avgPreference DESC, userCount DESC")
    List<Object[]> findPopularTagsForNewUsers(@Param("minUsers") int minUsers, Pageable pageable);
    
    // ===== 통계 및 분석 =====
    
    /**
     * 가장 인기 있는 태그 조회 (선호 사용자 수 기준)
     */
    @Query("SELECT upt.tag.tagId, upt.tag.tagName, COUNT(DISTINCT upt.user.userId) as userCount, " +
           "AVG(CASE upt.preferenceLevel WHEN 'HIGH' THEN 100 WHEN 'MEDIUM' THEN 70 WHEN 'LOW' THEN 30 END) as avgPreference " +
           "FROM UserPreferredTag upt " +
           "WHERE upt.isActive = true " +
           "GROUP BY upt.tag.tagId, upt.tag.tagName " +
           "ORDER BY userCount DESC, avgPreference DESC")
    List<Object[]> findMostPopularTags(Pageable pageable);
    
    /**
     * 태그별 평균 선호도 및 사용자 분포 조회
     */
    @Query("SELECT upt.tag.tagId, upt.tag.tagName, t.tagType, " +
           "COUNT(*) as totalUsers, " +
           "SUM(CASE WHEN upt.preferenceLevel = 'HIGH' THEN 1 ELSE 0 END) as highUsers, " +
           "SUM(CASE WHEN upt.preferenceLevel = 'MEDIUM' THEN 1 ELSE 0 END) as mediumUsers, " +
           "SUM(CASE WHEN upt.preferenceLevel = 'LOW' THEN 1 ELSE 0 END) as lowUsers, " +
           "AVG(CASE upt.preferenceLevel WHEN 'HIGH' THEN 100 WHEN 'MEDIUM' THEN 70 WHEN 'LOW' THEN 30 END) as avgPreference " +
           "FROM UserPreferredTag upt " +
           "JOIN upt.tag t " +
           "WHERE upt.isActive = true " +
           "GROUP BY upt.tag.tagId, upt.tag.tagName, t.tagType " +
           "ORDER BY avgPreference DESC")
    List<Object[]> findTagsWithDetailedPreferenceStats();
    
    /**
     * 사용자의 선호도 프로필 분석
     */
    @Query("SELECT t.tagType, " +
           "COUNT(*) as tagCount, " +
           "SUM(CASE WHEN upt.preferenceLevel = 'HIGH' THEN 1 ELSE 0 END) as highCount, " +
           "SUM(CASE WHEN upt.preferenceLevel = 'MEDIUM' THEN 1 ELSE 0 END) as mediumCount, " +
           "SUM(CASE WHEN upt.preferenceLevel = 'LOW' THEN 1 ELSE 0 END) as lowCount, " +
           "AVG(CASE upt.preferenceLevel WHEN 'HIGH' THEN 100 WHEN 'MEDIUM' THEN 70 WHEN 'LOW' THEN 30 END) as avgPreference " +
           "FROM UserPreferredTag upt " +
           "JOIN upt.tag t " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "GROUP BY t.tagType " +
           "ORDER BY avgPreference DESC")
    List<Object[]> analyzeUserPreferenceProfile(@Param("userId") Long userId);
    
    /**
     * 시간대별 선호도 변화 추적
     */
    @Query("SELECT DATE(upt.modifiedAt) as date, " +
           "COUNT(*) as changeCount, " +
           "SUM(CASE WHEN upt.preferenceLevel = 'HIGH' THEN 1 ELSE 0 END) as highChanges " +
           "FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.modifiedAt >= :startDate " +
           "GROUP BY DATE(upt.modifiedAt) " +
           "ORDER BY date DESC")
    List<Object[]> trackPreferenceChanges(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
    
    // ===== 성능 최적화 쿼리 =====
    
    /**
     * 사용자 선호 태그 ID 목록만 조회 (성능 최적화)
     */
    @Query("SELECT upt.tag.tagId FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.isActive = true " +
           "ORDER BY upt.preferenceLevel DESC")
    List<Long> findPreferredTagIdsByUserId(@Param("userId") Long userId);
    
    /**
     * 높은 선호도 태그 ID 목록만 조회
     */
    @Query("SELECT upt.tag.tagId FROM UserPreferredTag upt " +
           "WHERE upt.user.userId = :userId AND upt.preferenceLevel IN ('HIGH', 'MEDIUM') AND upt.isActive = true")
    List<Long> findHighPreferenceTagIds(@Param("userId") Long userId);
    
    /**
     * 사용자 그룹의 공통 선호 태그 찾기
     */
    @Query("SELECT upt.tag.tagId, COUNT(DISTINCT upt.user.userId) as userCount " +
           "FROM UserPreferredTag upt " +
           "WHERE upt.user.userId IN :userIds AND upt.isActive = true " +
           "GROUP BY upt.tag.tagId " +
           "HAVING userCount >= :minUsers " +
           "ORDER BY userCount DESC")
    List<Object[]> findCommonPreferencesForUserGroup(@Param("userIds") List<Long> userIds, 
                                                    @Param("minUsers") int minUsers);
}
```

---

## 🔍 UserPreferredTagRepositoryCustom - Custom 인터페이스

```java
package com.routepick.domain.tag.repository;

import com.routepick.domain.tag.entity.UserPreferredTag;
import com.routepick.dto.tag.response.UserSimilarityResult;
import com.routepick.dto.tag.response.TagRecommendationResult;
import com.routepick.dto.tag.response.UserCluster;
import com.routepick.dto.tag.response.PreferenceChangeResult;

import java.util.List;

/**
 * UserPreferredTag Repository Custom 인터페이스
 * - QueryDSL 기반 복잡한 선호도 분석
 */
public interface UserPreferredTagRepositoryCustom {
    
    /**
     * 사용자 선호도 유사성 분석 (코사인 유사도 기반)
     */
    List<UserSimilarityResult> findSimilarUsers(Long userId, int maxResults);
    
    /**
     * 개인화된 태그 추천 (하이브리드 추천)
     */
    List<TagRecommendationResult> recommendTagsForUser(Long userId, int maxResults);
    
    /**
     * 사용자 클러스터링 (K-means 기반 선호도 클러스터링)
     */
    List<UserCluster> clusterUsersByPreferences(int clusterCount);
    
    /**
     * 선호도 변화 추적 및 트렌드 분석
     */
    List<PreferenceChangeResult> trackPreferenceChanges(Long userId, int days);
    
    /**
     * 태그 공출현 매트릭스 생성 (사용자 선호도 기반)
     */
    List<Object[]> generateTagCoOccurrenceMatrix();
    
    /**
     * 사용자 선호도 다이버시티 스코어 계산
     */
    Double calculatePreferenceDiversityScore(Long userId);
    
    /**
     * 선호도 기반 사용자 세그멘테이션
     */
    List<Object[]> segmentUsersByPreferences();
}
```

---

## ⚡ 성능 최적화 및 인덱스 전략

### 1. 복합 인덱스 설계
```sql
-- 사용자별 선호도 조회 최적화
CREATE INDEX idx_user_preferred_tags_user_active ON user_preferred_tags (user_id, is_active, preference_level DESC);

-- 태그별 선호 사용자 조회 최적화  
CREATE INDEX idx_user_preferred_tags_tag_preference ON user_preferred_tags (tag_id, preference_level, is_active, user_id);

-- 유사 사용자 매칭 최적화
CREATE INDEX idx_user_preferred_tags_similarity ON user_preferred_tags (tag_id, user_id, preference_level, is_active);

-- 선호도 분석 최적화
CREATE INDEX idx_user_preferred_tags_analysis ON user_preferred_tags (is_active, preference_level, modified_at DESC);

-- 태그 타입별 선호도 분석
CREATE INDEX idx_user_preferred_tags_type_analysis ON user_preferred_tags (user_id, is_active) 
INCLUDE (preference_level, created_at);
```

### 2. 캐시 전략
```java
// 사용자 선호 태그 캐싱 (1시간)
@Cacheable(value = "userPreferences", key = "#userId", unless = "#result.isEmpty()")
List<UserPreferredTag> findActivePreferencesByUserId(Long userId);

// 인기 태그 캐싱 (30분)
@Cacheable(value = "popularPreferenceTags", key = "'top_' + #pageable.pageSize")
List<Object[]> findMostPopularTags(Pageable pageable);

// 추천 태그 캐싱 (2시간)
@Cacheable(value = "recommendedTags", key = "#userId + '_' + #maxResults")
List<TagRecommendationResult> recommendTagsForUser(Long userId, int maxResults);

// 캐시 무효화
@CacheEvict(value = {"userPreferences", "recommendedTags"}, key = "#userId")
void invalidateUserPreferenceCache(Long userId);
```

### 3. 배치 처리 최적화
```java
// 선호도 통계 배치 갱신 (매일 새벽 3시)
@Scheduled(cron = "0 0 3 * * ?")
public void refreshPreferenceStatistics() {
    // 사용자별 선호도 프로필 갱신
    batchUpdateUserPreferenceProfiles();
    
    // 태그 인기도 점수 갱신  
    batchUpdateTagPopularityScores();
    
    // 유사 사용자 매칭 테이블 갱신
    batchUpdateUserSimilarityMatrix();
}
```

---

## 📊 성능 메트릭 및 모니터링

### 주요 성능 지표
- **사용자 선호 태그 조회**: < 50ms (캐시 적용 시)
- **유사 사용자 찾기**: < 200ms (인덱스 최적화)
- **태그 추천 계산**: < 500ms (하이브리드 추천)
- **선호도 업데이트**: < 10ms (단일 업데이트)

### 추천 시스템 성능
- **추천 정확도**: 85%+ (협업 필터링 + 컨텐츠 기반)
- **추천 다양성**: 0.7+ (다이버시티 스코어)
- **Cold Start 문제**: 인기 태그 기반 해결
- **실시간 반영**: 선호도 변경 후 5분 내 반영

---

*사용자 선호 Repository 완성일: 2025-08-27*  
*분할 원본: step5-2a_tag_core_repositories.md (285-585줄)*  
*주요 기능: 개인화 추천, 유사 사용자 매칭, 선호도 분석*  
*다음 단계: RouteTag 및 추천 Repository 구현*