# Step 5-4c3: 게시글-루트 태그 Repository 생성

## 개요
- **목적**: 게시글-루트 태그 연결 관리 Repository 생성
- **대상**: PostRouteTagRepository
- **최적화**: 태그 기반 추천, 관련성 분석, 트렌드 분석

## 1. PostRouteTagRepository (게시글-루트 태그 연결 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.community;

import com.routepick.backend.domain.entity.community.PostRouteTag;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 게시글-루트 태그 연결 Repository
 * - 게시글과 클라이밍 루트 태깅 시스템
 * - 루트 추천 및 관련성 분석
 * - 태그 기반 콘텐츠 분류
 */
@Repository
public interface PostRouteTagRepository extends BaseRepository<PostRouteTag, Long> {
    
    // 기본 연관 관계 조회
    List<PostRouteTag> findByPostIdOrderByCreatedAt(Long postId);
    
    List<PostRouteTag> findByRouteIdOrderByCreatedAt(Long routeId);
    
    Optional<PostRouteTag> findByPostIdAndRouteId(Long postId, Long routeId);
    
    // 카운트 조회
    long countByPostId(Long postId);
    
    long countByRouteId(Long routeId);
    
    @Query("SELECT COUNT(prt) FROM PostRouteTag prt WHERE prt.postId = :postId AND prt.status = 'ACTIVE'")
    long countActiveTagsByPostId(@Param("postId") Long postId);
    
    // 배치 조회 최적화
    @Query("SELECT prt FROM PostRouteTag prt WHERE prt.postId IN :postIds ORDER BY prt.postId, prt.createdAt")
    List<PostRouteTag> findByPostIdInOrderByPostIdAndCreatedAt(@Param("postIds") List<Long> postIds);
    
    @Query("SELECT prt FROM PostRouteTag prt WHERE prt.routeId IN :routeIds ORDER BY prt.routeId, prt.createdAt DESC")
    List<PostRouteTag> findByRouteIdInOrderByRouteIdAndCreatedAtDesc(@Param("routeIds") List<Long> routeIds);
    
    // 관련성 분석
    @Query("SELECT prt.routeId, COUNT(prt) as tagCount FROM PostRouteTag prt " +
           "WHERE prt.postId IN :postIds AND prt.status = 'ACTIVE' " +
           "GROUP BY prt.routeId ORDER BY tagCount DESC")
    List<Object[]> findPopularRoutesByPostIds(@Param("postIds") List<Long> postIds);
    
    @Query("SELECT prt.postId, COUNT(prt) as tagCount FROM PostRouteTag prt " +
           "WHERE prt.routeId IN :routeIds AND prt.status = 'ACTIVE' " +
           "GROUP BY prt.postId ORDER BY tagCount DESC")
    List<Object[]> findRelatedPostsByRouteIds(@Param("routeIds") List<Long> routeIds);
    
    // 중복 체크 및 유니크 제약
    boolean existsByPostIdAndRouteId(Long postId, Long routeId);
    
    @Query("SELECT CASE WHEN COUNT(prt) > 0 THEN true ELSE false END FROM PostRouteTag prt " +
           "WHERE prt.postId = :postId AND prt.routeId = :routeId AND prt.status = 'ACTIVE'")
    boolean existsActiveTagByPostIdAndRouteId(@Param("postId") Long postId, @Param("routeId") Long routeId);
    
    // 태그 관리
    @Modifying
    @Query("UPDATE PostRouteTag prt SET prt.status = 'INACTIVE' " +
           "WHERE prt.postId = :postId AND prt.routeId = :routeId")
    void deactivateTag(@Param("postId") Long postId, @Param("routeId") Long routeId);
    
    @Modifying
    @Query("UPDATE PostRouteTag prt SET prt.status = 'ACTIVE' " +
           "WHERE prt.postId = :postId AND prt.routeId = :routeId")
    void activateTag(@Param("postId") Long postId, @Param("routeId") Long routeId);
    
    // 추천 시스템 지원
    @Query("SELECT prt.routeId FROM PostRouteTag prt " +
           "WHERE prt.postId IN (SELECT p.id FROM Post p WHERE p.authorId = :userId) " +
           "AND prt.status = 'ACTIVE' " +
           "GROUP BY prt.routeId ORDER BY COUNT(prt) DESC")
    List<Long> findFrequentRouteIdsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT prt FROM PostRouteTag prt " +
           "JOIN Post p ON prt.postId = p.id " +
           "WHERE p.authorId IN :userIds AND prt.status = 'ACTIVE' " +
           "ORDER BY prt.createdAt DESC")
    List<PostRouteTag> findRecentTagsByUserIds(@Param("userIds") List<Long> userIds);
    
    // 통계 조회
    @Query("SELECT DATE(prt.createdAt) as tagDate, COUNT(prt) as dailyCount " +
           "FROM PostRouteTag prt WHERE prt.createdAt >= :startDate AND prt.status = 'ACTIVE' " +
           "GROUP BY DATE(prt.createdAt) ORDER BY tagDate DESC")
    List<Object[]> getDailyTaggingStatistics(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT prt.routeId, COUNT(DISTINCT prt.postId) as postCount " +
           "FROM PostRouteTag prt WHERE prt.status = 'ACTIVE' " +
           "GROUP BY prt.routeId HAVING postCount >= :minPostCount " +
           "ORDER BY postCount DESC")
    List<Object[]> findPopularRoutesWithMinPosts(@Param("minPostCount") Long minPostCount);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.routepick.backend.application.dto.community.PostRouteTagSearchCriteria;
import com.routepick.backend.application.dto.projection.RouteTagAnalyticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글-루트 태그 커스텀 Repository
 */
public interface PostRouteTagRepositoryCustom {
    
    // 고급 검색
    Page<PostRouteTag> searchRouteTags(PostRouteTagSearchCriteria criteria, Pageable pageable);
    
    // 추천 시스템 지원
    List<Long> findRecommendedRouteIds(Long userId, int limit);
    
    List<Long> findSimilarPostIds(Long postId, int limit);
    
    // 태그 분석
    List<RouteTagAnalyticsProjection> getRouteTagAnalytics(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 트렌드 분석
    List<Long> findTrendingRouteIds(int days, int limit);
    
    List<Long> findTrendingPostIds(int days, int limit);
    
    // 관련성 점수 계산
    List<RouteTagAnalyticsProjection> calculateRouteRelatedness(List<Long> routeIds);
    
    // 사용자 관심사 분석
    List<Long> findUserInterestedRouteIds(Long userId, int limit);
    
    // 배치 처리
    void batchCreateTags(List<PostRouteTag> tags);
    
    void batchUpdateTagStatus(List<Long> tagIds, String status);
    
    // 정리 작업
    void cleanupInactiveTags(LocalDateTime beforeDate);
    
    void removeDuplicateTags();
}
```

### Custom Repository 구현
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.routepick.backend.domain.entity.community.PostRouteTag;
import com.routepick.backend.domain.entity.community.QPostRouteTag;
import com.routepick.backend.domain.entity.community.QPost;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글-루트 태그 커스텀 Repository 구현
 */
@Repository
@RequiredArgsConstructor
public class PostRouteTagRepositoryCustomImpl implements PostRouteTagRepositoryCustom {
    
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    
    private static final QPostRouteTag postRouteTag = QPostRouteTag.postRouteTag;
    private static final QPost post = QPost.post;
    
    @Override
    public Page<PostRouteTag> searchRouteTags(PostRouteTagSearchCriteria criteria, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        
        if (criteria.getPostId() != null) {
            builder.and(postRouteTag.postId.eq(criteria.getPostId()));
        }
        
        if (criteria.getRouteId() != null) {
            builder.and(postRouteTag.routeId.eq(criteria.getRouteId()));
        }
        
        if (criteria.getStatus() != null) {
            builder.and(postRouteTag.status.eq(criteria.getStatus()));
        }
        
        if (criteria.getAuthorId() != null) {
            builder.and(postRouteTag.postId.in(
                queryFactory.select(post.id)
                           .from(post)
                           .where(post.authorId.eq(criteria.getAuthorId()))
            ));
        }
        
        if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
            builder.and(postRouteTag.createdAt.between(criteria.getStartDate(), criteria.getEndDate()));
        }
        
        List<PostRouteTag> content = queryFactory
            .selectFrom(postRouteTag)
            .where(builder)
            .orderBy(postRouteTag.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        
        Long total = queryFactory
            .select(postRouteTag.count())
            .from(postRouteTag)
            .where(builder)
            .fetchOne();
        
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
    
    @Override
    public List<Long> findRecommendedRouteIds(Long userId, int limit) {
        // 사용자가 자주 태그한 루트와 유사한 루트 찾기
        return queryFactory
            .select(postRouteTag.routeId)
            .from(postRouteTag)
            .join(post).on(postRouteTag.postId.eq(post.id))
            .where(post.authorId.ne(userId) // 본인 게시글 제외
                .and(postRouteTag.routeId.in(
                    queryFactory.select(postRouteTag.routeId)
                               .from(postRouteTag)
                               .join(post).on(postRouteTag.postId.eq(post.id))
                               .where(post.authorId.eq(userId))
                               .groupBy(postRouteTag.routeId)
                               .having(postRouteTag.count().goe(2L))
                ))
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<Long> findSimilarPostIds(Long postId, int limit) {
        // 같은 루트를 태그한 다른 게시글들
        List<Long> routeIds = queryFactory
            .select(postRouteTag.routeId)
            .from(postRouteTag)
            .where(postRouteTag.postId.eq(postId)
                .and(postRouteTag.status.eq("ACTIVE")))
            .fetch();
        
        if (routeIds.isEmpty()) {
            return List.of();
        }
        
        return queryFactory
            .select(postRouteTag.postId)
            .from(postRouteTag)
            .where(postRouteTag.routeId.in(routeIds)
                .and(postRouteTag.postId.ne(postId))
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.postId)
            .orderBy(postRouteTag.count().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<RouteTagAnalyticsProjection> getRouteTagAnalytics(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        return queryFactory
            .select(Projections.constructor(RouteTagAnalyticsProjection.class,
                postRouteTag.routeId,
                postRouteTag.count(),
                postRouteTag.postId.countDistinct(),
                post.authorId.countDistinct()
            ))
            .from(postRouteTag)
            .join(post).on(postRouteTag.postId.eq(post.id))
            .where(postRouteTag.createdAt.between(startDate, endDate)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc())
            .fetch();
    }
    
    @Override
    public List<Long> findTrendingRouteIds(int days, int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        return queryFactory
            .select(postRouteTag.routeId)
            .from(postRouteTag)
            .where(postRouteTag.createdAt.goe(startDate)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<Long> findTrendingPostIds(int days, int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        return queryFactory
            .select(postRouteTag.postId)
            .from(postRouteTag)
            .where(postRouteTag.createdAt.goe(startDate)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.postId)
            .orderBy(postRouteTag.count().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<RouteTagAnalyticsProjection> calculateRouteRelatedness(List<Long> routeIds) {
        return queryFactory
            .select(Projections.constructor(RouteTagAnalyticsProjection.class,
                postRouteTag.routeId,
                postRouteTag.count(),
                postRouteTag.postId.countDistinct(),
                post.authorId.countDistinct()
            ))
            .from(postRouteTag)
            .join(post).on(postRouteTag.postId.eq(post.id))
            .where(postRouteTag.routeId.in(routeIds)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc())
            .fetch();
    }
    
    @Override
    public List<Long> findUserInterestedRouteIds(Long userId, int limit) {
        return queryFactory
            .select(postRouteTag.routeId)
            .from(postRouteTag)
            .join(post).on(postRouteTag.postId.eq(post.id))
            .where(post.authorId.eq(userId)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc(), postRouteTag.createdAt.max().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public void batchCreateTags(List<PostRouteTag> tags) {
        int batchSize = 50;
        for (int i = 0; i < tags.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, tags.size());
            List<PostRouteTag> batch = tags.subList(i, endIndex);
            
            for (PostRouteTag tag : batch) {
                entityManager.persist(tag);
            }
            entityManager.flush();
            entityManager.clear();
        }
    }
    
    @Override
    public void batchUpdateTagStatus(List<Long> tagIds, String status) {
        queryFactory
            .update(postRouteTag)
            .set(postRouteTag.status, status)
            .set(postRouteTag.updatedAt, LocalDateTime.now())
            .where(postRouteTag.id.in(tagIds))
            .execute();
        
        entityManager.flush();
        entityManager.clear();
    }
    
    @Override
    public void cleanupInactiveTags(LocalDateTime beforeDate) {
        queryFactory
            .delete(postRouteTag)
            .where(postRouteTag.status.eq("INACTIVE")
                .and(postRouteTag.updatedAt.lt(beforeDate)))
            .execute();
        
        entityManager.flush();
    }
    
    @Override
    public void removeDuplicateTags() {
        // 중복 태그 제거 - 가장 최근 것만 유지
        List<PostRouteTag> duplicates = queryFactory
            .selectFrom(postRouteTag)
            .where(postRouteTag.id.notIn(
                queryFactory.select(postRouteTag.id.max())
                           .from(postRouteTag)
                           .groupBy(postRouteTag.postId, postRouteTag.routeId)
            ))
            .fetch();
        
        for (PostRouteTag duplicate : duplicates) {
            entityManager.remove(duplicate);
        }
        entityManager.flush();
    }
}
```

## Projection 인터페이스

### RouteTagAnalyticsProjection
```java
package com.routepick.backend.application.dto.projection;

/**
 * 루트 태그 분석 Projection
 */
public class RouteTagAnalyticsProjection {
    private Long routeId;
    private Long tagCount;
    private Long postCount;
    private Long uniqueUserCount;
    
    public RouteTagAnalyticsProjection(Long routeId, Long tagCount, 
                                     Long postCount, Long uniqueUserCount) {
        this.routeId = routeId;
        this.tagCount = tagCount;
        this.postCount = postCount;
        this.uniqueUserCount = uniqueUserCount;
    }
    
    // Getters
    public Long getRouteId() { return routeId; }
    public Long getTagCount() { return tagCount; }
    public Long getPostCount() { return postCount; }
    public Long getUniqueUserCount() { return uniqueUserCount; }
    
    // 관련성 점수 계산 (태그 수 + 게시글 수 + 고유 사용자 수)
    public Double getRelatednessScore() {
        return (tagCount * 0.4) + (postCount * 0.4) + (uniqueUserCount * 0.2);
    }
}
```

## 📈 성능 최적화 포인트

### 1. 인덱스 최적화
```sql
-- 게시글-루트 태그
CREATE UNIQUE INDEX idx_post_route_tags_unique ON post_route_tags(post_id, route_id, status);
CREATE INDEX idx_post_route_tags_route_created ON post_route_tags(route_id, created_at DESC);
CREATE INDEX idx_post_route_tags_trending ON post_route_tags(created_at DESC, status);
CREATE INDEX idx_post_route_tags_post_status ON post_route_tags(post_id, status);
CREATE INDEX idx_post_route_tags_route_status ON post_route_tags(route_id, status);
```

### 2. 캐싱 전략
- **Redis 캐싱**: 인기 루트 ID, 트렌딩 태그 데이터
- **애플리케이션 캐싱**: 자주 조회되는 태그 관계, 추천 결과
- **분산 캐싱**: 사용자별 추천 루트 목록

### 3. 배치 처리 최적화
- **태그 정리**: 주기적 중복 제거 및 비활성 태그 삭제
- **통계 갱신**: 일별/주별 태깅 통계 사전 계산
- **추천 알고리즘**: 배치로 사용자별 추천 점수 갱신

## 🎯 주요 기능
- ✅ **태그 관계 관리**: 게시글-루트 간 N:M 관계
- ✅ **추천 시스템**: 사용자 관심사 기반 루트 추천
- ✅ **트렌드 분석**: 시간대별 인기 루트/게시글 분석
- ✅ **관련성 계산**: 루트 간 유사도 및 관련성 점수
- ✅ **중복 관리**: 중복 태그 자동 감지 및 정리
- ✅ **배치 처리**: 대량 태그 생성/업데이트 최적화

## 🔍 추천 알고리즘 로직

### 1. 사용자 기반 추천
```java
// 사용자가 자주 태그한 루트와 유사한 루트 찾기
List<Long> recommendedRouteIds = findRecommendedRouteIds(userId, limit);
```

### 2. 콘텐츠 기반 추천
```java
// 특정 게시글과 유사한 다른 게시글 찾기
List<Long> similarPostIds = findSimilarPostIds(postId, limit);
```

### 3. 트렌드 기반 추천
```java
// 최근 N일간 인기 루트 찾기
List<Long> trendingRouteIds = findTrendingRouteIds(7, limit);
```

### 4. 관련성 점수 계산
- **태그 수**: 40% 가중치
- **게시글 수**: 40% 가중치  
- **고유 사용자 수**: 20% 가중치

---
*Step 5-4c3 완료: 게시글-루트 태그 Repository 생성 완료*  
*다음: step5-4f 시스템 최종 Repository 세분화 대기 중*