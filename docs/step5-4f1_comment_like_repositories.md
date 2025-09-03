# Step 5-4f1: 댓글 좋아요 Repository 생성

## 개요
- **목적**: 댓글 좋아요 시스템 Repository 생성
- **대상**: CommentLikeRepository
- **최적화**: 중복 방지, 인기 댓글 추적, 사용자 활동 분석

## 1. CommentLikeRepository (댓글 좋아요 최적화)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.community;

import com.routepick.backend.domain.entity.community.CommentLike;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 댓글 좋아요 Repository
 * - 댓글 좋아요/취소 중복 방지
 * - 인기 댓글 추적 및 분석
 * - 사용자별 좋아요 이력 관리
 */
@Repository
public interface CommentLikeRepository extends BaseRepository<CommentLike, Long> {
    
    // ===== 기본 조회 및 존재 확인 =====
    
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);
    
    Optional<CommentLike> findByCommentIdAndUserId(Long commentId, Long userId);
    
    List<CommentLike> findByUserIdOrderByLikeDateDesc(Long userId);
    
    List<CommentLike> findByCommentIdOrderByLikeDateDesc(Long commentId);
    
    // ===== 카운트 조회 =====
    
    long countByCommentId(Long commentId);
    
    long countByUserId(Long userId);
    
    @Query("SELECT COUNT(cl) FROM CommentLike cl " +
           "WHERE cl.commentId = :commentId AND cl.likeDate >= :since")
    long countRecentLikesByCommentId(@Param("commentId") Long commentId, 
                                    @Param("since") LocalDateTime since);
    
    // ===== 좋아요 취소 =====
    
    @Transactional
    @Modifying
    @Query("DELETE FROM CommentLike cl WHERE cl.commentId = :commentId AND cl.userId = :userId")
    int deleteByCommentIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);
    
    // ===== 인기 댓글 분석 =====
    
    @Query("SELECT cl.commentId, COUNT(cl) as likeCount " +
           "FROM CommentLike cl " +
           "WHERE cl.likeDate >= :since " +
           "GROUP BY cl.commentId " +
           "HAVING likeCount >= :minLikes " +
           "ORDER BY likeCount DESC")
    List<Object[]> findMostLikedComments(@Param("since") LocalDateTime since, 
                                        @Param("minLikes") Long minLikes);
    
    @Query("SELECT cl.commentId, COUNT(cl) as likeCount " +
           "FROM CommentLike cl " +
           "JOIN Comment c ON cl.commentId = c.id " +
           "WHERE c.postId = :postId " +
           "GROUP BY cl.commentId " +
           "ORDER BY likeCount DESC")
    List<Object[]> findMostLikedCommentsByPost(@Param("postId") Long postId);
    
    // ===== 사용자 활동 분석 =====
    
    @Query("SELECT cl FROM CommentLike cl " +
           "JOIN Comment c ON cl.commentId = c.id " +
           "WHERE c.authorId = :authorId " +
           "ORDER BY cl.likeDate DESC")
    List<CommentLike> findLikesForUserComments(@Param("authorId") Long authorId);
    
    @Query("SELECT DATE(cl.likeDate), COUNT(cl) " +
           "FROM CommentLike cl " +
           "WHERE cl.userId = :userId " +
           "AND cl.likeDate >= :startDate " +
           "GROUP BY DATE(cl.likeDate) " +
           "ORDER BY DATE(cl.likeDate) DESC")
    List<Object[]> getUserLikeActivity(@Param("userId") Long userId, 
                                      @Param("startDate") LocalDateTime startDate);
    
    // ===== 댓글 좋아요 통계 =====
    
    @Query("SELECT AVG(likeCount) FROM (" +
           "SELECT COUNT(cl) as likeCount FROM CommentLike cl " +
           "GROUP BY cl.commentId) AS avgLikes")
    Double getAverageLikesPerComment();
    
    @Query("SELECT cl.commentId " +
           "FROM CommentLike cl " +
           "WHERE cl.likeDate BETWEEN :startDate AND :endDate " +
           "GROUP BY cl.commentId " +
           "ORDER BY COUNT(cl) DESC")
    List<Long> findTrendingCommentIds(@Param("startDate") LocalDateTime startDate, 
                                     @Param("endDate") LocalDateTime endDate, 
                                     Pageable pageable);
    
    // ===== 배치 처리 =====
    
    @Query("SELECT cl.commentId, COUNT(cl) as likeCount " +
           "FROM CommentLike cl " +
           "GROUP BY cl.commentId " +
           "HAVING likeCount > :threshold")
    List<Object[]> findCommentsWithHighLikes(@Param("threshold") Long threshold);
    
    @Transactional
    @Modifying
    @Query("DELETE FROM CommentLike cl " +
           "WHERE cl.likeDate < :cutoffDate")
    int deleteOldLikes(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.routepick.backend.application.dto.community.CommentLikeSearchCriteria;
import com.routepick.backend.application.dto.projection.CommentLikeAnalyticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 댓글 좋아요 커스텀 Repository
 */
public interface CommentLikeRepositoryCustom {
    
    // 고급 검색
    Page<CommentLike> searchCommentLikes(CommentLikeSearchCriteria criteria, Pageable pageable);
    
    // 인기 댓글 분석
    List<CommentLikeAnalyticsProjection> getCommentLikeAnalytics(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 사용자 좋아요 패턴 분석
    List<CommentLikeAnalyticsProjection> getUserLikePatterns(Long userId, int days);
    
    // 댓글 인기도 트렌드
    List<Long> findTrendingCommentsByTimeframe(int hours, int limit);
    
    // 좋아요 급증 댓글 탐지
    List<Long> findSurgingComments(int hours, double growthThreshold);
    
    // 배치 처리
    void batchDeleteOldLikes(LocalDateTime cutoffDate, int batchSize);
    
    void batchUpdateLikeStatistics();
}
```

### Custom Repository 구현
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.routepick.backend.domain.entity.community.CommentLike;
import com.routepick.backend.domain.entity.community.QCommentLike;
import com.routepick.backend.domain.entity.community.QComment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 댓글 좋아요 커스텀 Repository 구현
 */
@Repository
@RequiredArgsConstructor
public class CommentLikeRepositoryCustomImpl implements CommentLikeRepositoryCustom {
    
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    
    private static final QCommentLike commentLike = QCommentLike.commentLike;
    private static final QComment comment = QComment.comment;
    
    @Override
    public Page<CommentLike> searchCommentLikes(CommentLikeSearchCriteria criteria, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        
        if (criteria.getUserId() != null) {
            builder.and(commentLike.userId.eq(criteria.getUserId()));
        }
        
        if (criteria.getCommentId() != null) {
            builder.and(commentLike.commentId.eq(criteria.getCommentId()));
        }
        
        if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
            builder.and(commentLike.likeDate.between(criteria.getStartDate(), criteria.getEndDate()));
        }
        
        if (criteria.getPostId() != null) {
            builder.and(commentLike.commentId.in(
                queryFactory.select(comment.id)
                           .from(comment)
                           .where(comment.postId.eq(criteria.getPostId()))
            ));
        }
        
        List<CommentLike> content = queryFactory
            .selectFrom(commentLike)
            .where(builder)
            .orderBy(commentLike.likeDate.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        
        Long total = queryFactory
            .select(commentLike.count())
            .from(commentLike)
            .where(builder)
            .fetchOne();
        
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
    
    @Override
    public List<CommentLikeAnalyticsProjection> getCommentLikeAnalytics(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        return queryFactory
            .select(Projections.constructor(CommentLikeAnalyticsProjection.class,
                commentLike.commentId,
                commentLike.count(),
                commentLike.likeDate.min(),
                commentLike.likeDate.max(),
                commentLike.userId.countDistinct()
            ))
            .from(commentLike)
            .where(commentLike.likeDate.between(startDate, endDate))
            .groupBy(commentLike.commentId)
            .orderBy(commentLike.count().desc())
            .fetch();
    }
    
    @Override
    public List<CommentLikeAnalyticsProjection> getUserLikePatterns(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        return queryFactory
            .select(Projections.constructor(CommentLikeAnalyticsProjection.class,
                commentLike.commentId,
                commentLike.count(),
                commentLike.likeDate.min(),
                commentLike.likeDate.max(),
                commentLike.userId.countDistinct()
            ))
            .from(commentLike)
            .join(comment).on(commentLike.commentId.eq(comment.id))
            .where(commentLike.userId.eq(userId)
                .and(commentLike.likeDate.goe(since)))
            .groupBy(commentLike.commentId, comment.postId)
            .orderBy(commentLike.likeDate.max().desc())
            .fetch();
    }
    
    @Override
    public List<Long> findTrendingCommentsByTimeframe(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        return queryFactory
            .select(commentLike.commentId)
            .from(commentLike)
            .where(commentLike.likeDate.goe(since))
            .groupBy(commentLike.commentId)
            .orderBy(commentLike.count().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<Long> findSurgingComments(int hours, double growthThreshold) {
        LocalDateTime recentTime = LocalDateTime.now().minusHours(hours);
        LocalDateTime previousTime = LocalDateTime.now().minusHours(hours * 2);
        
        // 최근 시간대 좋아요 수
        var recentLikes = queryFactory
            .select(commentLike.commentId, commentLike.count())
            .from(commentLike)
            .where(commentLike.likeDate.goe(recentTime))
            .groupBy(commentLike.commentId)
            .fetch();
        
        // 이전 시간대 좋아요 수
        var previousLikes = queryFactory
            .select(commentLike.commentId, commentLike.count())
            .from(commentLike)
            .where(commentLike.likeDate.between(previousTime, recentTime))
            .groupBy(commentLike.commentId)
            .fetch();
        
        // Java에서 증가율 계산 (간단한 구현)
        return recentLikes.stream()
            .filter(recent -> {
                Long commentId = (Long) recent.get(0);
                Long recentCount = (Long) recent.get(1);
                
                Long previousCount = previousLikes.stream()
                    .filter(prev -> prev.get(0).equals(commentId))
                    .map(prev -> (Long) prev.get(1))
                    .findFirst()
                    .orElse(0L);
                
                if (previousCount == 0) return recentCount > 0;
                
                double growthRate = (double) (recentCount - previousCount) / previousCount;
                return growthRate >= growthThreshold;
            })
            .map(recent -> (Long) recent.get(0))
            .limit(10)
            .toList();
    }
    
    @Override
    public void batchDeleteOldLikes(LocalDateTime cutoffDate, int batchSize) {
        int deletedCount;
        do {
            deletedCount = queryFactory
                .delete(commentLike)
                .where(commentLike.likeDate.lt(cutoffDate))
                .limit(batchSize)
                .execute();
            
            entityManager.flush();
            entityManager.clear();
        } while (deletedCount > 0);
    }
    
    @Override
    public void batchUpdateLikeStatistics() {
        // 댓글별 좋아요 수 통계 업데이트
        queryFactory
            .update(comment)
            .set(comment.likeCount, 
                queryFactory.select(commentLike.count())
                           .from(commentLike)
                           .where(commentLike.commentId.eq(comment.id)))
            .execute();
        
        entityManager.flush();
        entityManager.clear();
    }
}
```

## Projection 인터페이스

### CommentLikeAnalyticsProjection
```java
package com.routepick.backend.application.dto.projection;

import java.time.LocalDateTime;

/**
 * 댓글 좋아요 분석 Projection
 */
public class CommentLikeAnalyticsProjection {
    private Long commentId;
    private Long likeCount;
    private LocalDateTime firstLike;
    private LocalDateTime lastLike;
    private Long uniqueUsers;
    
    public CommentLikeAnalyticsProjection(Long commentId, Long likeCount, 
                                        LocalDateTime firstLike, LocalDateTime lastLike, Long uniqueUsers) {
        this.commentId = commentId;
        this.likeCount = likeCount;
        this.firstLike = firstLike;
        this.lastLike = lastLike;
        this.uniqueUsers = uniqueUsers;
    }
    
    // Getters
    public Long getCommentId() { return commentId; }
    public Long getLikeCount() { return likeCount; }
    public LocalDateTime getFirstLike() { return firstLike; }
    public LocalDateTime getLastLike() { return lastLike; }
    public Long getUniqueUsers() { return uniqueUsers; }
    
    // 계산된 필드
    public Double getLikeVelocity() {
        if (firstLike != null && lastLike != null && !firstLike.equals(lastLike)) {
            long hours = java.time.Duration.between(firstLike, lastLike).toHours();
            return hours > 0 ? (double) likeCount / hours : likeCount.doubleValue();
        }
        return 0.0;
    }
    
    public Double getEngagementRate() {
        return uniqueUsers > 0 ? (double) likeCount / uniqueUsers : 0.0;
    }
}
```

## 📈 성능 최적화 포인트

### 1. 인덱스 최적화
```sql
-- 댓글 좋아요 최적화
CREATE UNIQUE INDEX idx_comment_like_unique ON comment_likes(comment_id, user_id);
CREATE INDEX idx_comment_like_user_date ON comment_likes(user_id, like_date DESC);
CREATE INDEX idx_comment_like_comment_date ON comment_likes(comment_id, like_date DESC);
CREATE INDEX idx_comment_like_date_analysis ON comment_likes(like_date DESC);
```

### 2. 캐싱 전략
- **Redis 캐싱**: 댓글별 좋아요 수, 인기 댓글 목록
- **애플리케이션 캐싱**: 트렌딩 댓글 ID, 사용자별 좋아요 통계
- **실시간 업데이트**: WebSocket으로 좋아요 수 동기화

### 3. 배치 처리 최적화
- **통계 업데이트**: 주기적으로 댓글별 좋아요 수 동기화
- **오래된 데이터 정리**: 일정 기간 후 좋아요 이력 삭제
- **급증 탐지**: 시간대별 좋아요 증가율 모니터링

## 🎯 주요 기능
- ✅ **중복 방지**: 사용자-댓글 유니크 제약 조건
- ✅ **인기 댓글 추적**: 좋아요 수 기반 랭킹
- ✅ **사용자 활동 분석**: 좋아요 패턴 및 선호도 분석
- ✅ **트렌드 분석**: 시간대별 인기 댓글 탐지
- ✅ **급증 탐지**: 급격한 좋아요 증가 댓글 감지
- ✅ **배치 처리**: 통계 업데이트 및 데이터 정리

## 💡 비즈니스 로직 활용
- **댓글 랭킹**: 좋아요 수 기반 베스트 댓글 선정
- **사용자 분석**: 좋아요 패턴으로 사용자 관심사 파악
- **콘텐츠 품질**: 좋아요 비율로 댓글 품질 평가
- **실시간 피드**: 급증하는 댓글을 실시간 피드에 노출

---
*Step 5-4f1 완료: 댓글 좋아요 Repository 생성 완료*  
*다음: step5-4f2 메시지 시스템 Repository 대기 중*