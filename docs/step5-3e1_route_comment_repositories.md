# Step 5-3e1: 루트 댓글 Repository - RouteComment 계층형 시스템

> 루트 계층형 댓글 Repository 완전 설계 (소프트 삭제 & 익명 처리 특화)  
> 생성일: 2025-08-21  
> 기반: step5-3e_route_interaction_repositories.md 세분화  
> 포함 Repository: RouteCommentRepository

---

## 📋 파일 세분화 정보
- **원본 파일**: step5-3e_route_interaction_repositories.md (1,175줄)
- **세분화 사유**: 토큰 제한 대응 및 기능별 책임 분리
- **이 파일 포함**: RouteCommentRepository (계층형 댓글 시스템)
- **다른 파일**: step5-3e2_route_vote_scrap_repositories.md (RouteDifficultyVoteRepository, RouteScrapRepository)

---

## 🎯 설계 목표

- **계층형 댓글 구조 최적화**: 부모-자식 관계 효율적 관리
- **소프트 삭제 및 익명 처리**: 안전한 데이터 관리
- **댓글 타입별 관리**: 베타, 세터, 일반 댓글 구분
- **성능 최적화**: N+1 문제 해결, 복합 인덱스 활용

---

## 💬 RouteCommentRepository - 계층형 댓글 Repository

```java
package com.routepick.domain.route.repository;

import com.routepick.common.repository.SoftDeleteRepository;
import com.routepick.domain.route.entity.RouteComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RouteComment Repository
 * - 🌳 계층형 댓글 최적화
 * - 소프트 삭제 지원
 * - 익명 댓글 처리
 * - 댓글 유형별 관리
 */
@Repository
public interface RouteCommentRepository extends SoftDeleteRepository<RouteComment, Long> {
    
    // ===== 🌳 계층형 댓글 조회 =====
    
    /**
     * 루트별 최상위 댓글 조회 (계층형 구조의 루트)
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.parent IS NULL AND rc.isDeleted = false " +
           "ORDER BY rc.isPinned DESC, rc.createdAt DESC")
    List<RouteComment> findByRouteIdAndParentIdIsNullOrderByCreatedAtDesc(@Param("routeId") Long routeId);
    
    /**
     * 루트별 최상위 댓글 조회 (페이징)
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.parent IS NULL AND rc.isDeleted = false " +
           "ORDER BY rc.isPinned DESC, rc.createdAt DESC")
    Page<RouteComment> findByRouteIdAndParentIdIsNullOrderByCreatedAtDesc(@Param("routeId") Long routeId, 
                                                                         Pageable pageable);
    
    /**
     * 특정 댓글의 대댓글들 조회
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.parent.commentId = :parentId AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt ASC")
    List<RouteComment> findByParentIdOrderByCreatedAtAsc(@Param("parentId") Long parentId);
    
    /**
     * 전체 댓글 트리 구조 조회 (루트 + 대댓글)
     */
    @EntityGraph(attributePaths = {"user", "children", "children.user"})
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.isDeleted = false " +
           "ORDER BY COALESCE(rc.parent.commentId, rc.commentId), rc.createdAt")
    List<RouteComment> findRouteCommentsTree(@Param("routeId") Long routeId);
    
    /**
     * 댓글 깊이별 조회
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.isDeleted = false " +
           "AND (:maxDepth = 0 OR rc.parent IS NULL OR " +
           "     (SELECT COUNT(p) FROM RouteComment p WHERE p.commentId IN " +
           "      (SELECT DISTINCT c.parent.commentId FROM RouteComment c " +
           "       WHERE c.commentId = rc.commentId AND c.parent IS NOT NULL)) <= :maxDepth) " +
           "ORDER BY rc.createdAt DESC")
    List<RouteComment> findByRouteIdWithMaxDepth(@Param("routeId") Long routeId, 
                                                @Param("maxDepth") Integer maxDepth);
    
    // ===== 댓글 타입별 조회 =====
    
    /**
     * 댓글 타입별 조회
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.commentType = :commentType AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    List<RouteComment> findByRouteIdAndCommentType(@Param("routeId") Long routeId, 
                                                  @Param("commentType") String commentType);
    
    /**
     * 베타 댓글 조회 (스포일러 포함)
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.commentType = 'BETA' AND rc.isDeleted = false " +
           "ORDER BY rc.likeCount DESC, rc.createdAt DESC")
    List<RouteComment> findBetaCommentsByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 세터 댓글 조회 (루트 세터의 댓글)
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.isAuthorComment = true AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    List<RouteComment> findSetterCommentsByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 고정 댓글 조회
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.isPinned = true AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    List<RouteComment> findPinnedCommentsByRouteId(@Param("routeId") Long routeId);
    
    // ===== 사용자별 댓글 조회 =====
    
    /**
     * 사용자의 모든 댓글 조회
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.user.userId = :userId AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    List<RouteComment> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    /**
     * 사용자의 댓글 조회 (페이징)
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.user.userId = :userId AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    Page<RouteComment> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 사용자의 루트별 댓글 조회
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.user.userId = :userId AND rc.route.routeId = :routeId AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    List<RouteComment> findByUserIdAndRouteId(@Param("userId") Long userId, @Param("routeId") Long routeId);
    
    // ===== 인기 댓글 및 통계 =====
    
    /**
     * 인기 댓글 조회 (좋아요 수 기준)
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.likeCount >= :minLikes AND rc.isDeleted = false " +
           "ORDER BY rc.likeCount DESC")
    List<RouteComment> findPopularCommentsByRouteId(@Param("routeId") Long routeId, 
                                                   @Param("minLikes") Integer minLikes);
    
    /**
     * 최근 활발한 댓글 (최근 대댓글이 달린 댓글)
     */
    @Query("SELECT DISTINCT rc FROM RouteComment rc " +
           "LEFT JOIN rc.children child " +
           "WHERE rc.route.routeId = :routeId AND rc.parent IS NULL AND rc.isDeleted = false " +
           "AND (child.createdAt >= :sinceDate OR rc.createdAt >= :sinceDate) " +
           "ORDER BY COALESCE(MAX(child.createdAt), rc.createdAt) DESC")
    List<RouteComment> findActiveCommentsByRouteId(@Param("routeId") Long routeId, 
                                                  @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * 댓글 통계 (루트별)
     */
    @Query("SELECT " +
           "COUNT(rc) as totalComments, " +
           "COUNT(CASE WHEN rc.parent IS NULL THEN 1 END) as rootComments, " +
           "COUNT(CASE WHEN rc.parent IS NOT NULL THEN 1 END) as replyComments, " +
           "AVG(rc.likeCount) as avgLikes " +
           "FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.isDeleted = false")
    List<Object[]> getCommentStatsByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 댓글 타입별 통계
     */
    @Query("SELECT rc.commentType, COUNT(rc) as commentCount FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.isDeleted = false " +
           "GROUP BY rc.commentType " +
           "ORDER BY commentCount DESC")
    List<Object[]> countByRouteIdAndCommentType(@Param("routeId") Long routeId);
    
    // ===== 익명 댓글 관리 =====
    
    /**
     * 익명 댓글 조회
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.isAnonymous = true AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    List<RouteComment> findAnonymousCommentsByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 비익명 댓글 조회
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.isAnonymous = false AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    List<RouteComment> findNamedCommentsByRouteId(@Param("routeId") Long routeId);
    
    // ===== 업데이트 메서드 =====
    
    /**
     * 좋아요 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteComment rc SET rc.likeCount = COALESCE(rc.likeCount, 0) + 1 " +
           "WHERE rc.commentId = :commentId")
    int increaseLikeCount(@Param("commentId") Long commentId);
    
    /**
     * 답글 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteComment rc SET rc.replyCount = COALESCE(rc.replyCount, 0) + 1 " +
           "WHERE rc.commentId = :parentId")
    int increaseReplyCount(@Param("parentId") Long parentId);
    
    /**
     * 답글 수 감소
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteComment rc SET rc.replyCount = GREATEST(COALESCE(rc.replyCount, 0) - 1, 0) " +
           "WHERE rc.commentId = :parentId")
    int decreaseReplyCount(@Param("parentId") Long parentId);
    
    /**
     * 신고 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteComment rc SET rc.reportCount = COALESCE(rc.reportCount, 0) + 1 " +
           "WHERE rc.commentId = :commentId")
    int increaseReportCount(@Param("commentId") Long commentId);
    
    /**
     * 댓글 고정/해제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteComment rc SET rc.isPinned = :isPinned " +
           "WHERE rc.commentId = :commentId")
    int updatePinnedStatus(@Param("commentId") Long commentId, @Param("isPinned") boolean isPinned);
    
    /**
     * 세터 댓글 표시
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteComment rc SET rc.isAuthorComment = true " +
           "WHERE rc.commentId = :commentId")
    int markAsAuthorComment(@Param("commentId") Long commentId);
    
    // ===== 소프트 삭제 관리 =====
    
    /**
     * 댓글 소프트 삭제 (계층 구조 유지)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteComment rc SET " +
           "rc.isDeleted = true, " +
           "rc.content = '삭제된 댓글입니다.', " +
           "rc.deletedAt = CURRENT_TIMESTAMP " +
           "WHERE rc.commentId = :commentId")
    int softDeleteComment(@Param("commentId") Long commentId);
    
    /**
     * 대댓글 포함 전체 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteComment rc SET " +
           "rc.isDeleted = true, " +
           "rc.content = '삭제된 댓글입니다.', " +
           "rc.deletedAt = CURRENT_TIMESTAMP " +
           "WHERE rc.commentId = :commentId OR rc.parent.commentId = :commentId")
    int softDeleteCommentWithReplies(@Param("commentId") Long commentId);
    
    /**
     * 루트의 모든 댓글 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteComment rc SET " +
           "rc.isDeleted = true, " +
           "rc.deletedAt = CURRENT_TIMESTAMP " +
           "WHERE rc.route.routeId = :routeId")
    int softDeleteAllCommentsByRoute(@Param("routeId") Long routeId);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 댓글 내용 검색
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.content LIKE %:keyword% AND rc.isDeleted = false " +
           "ORDER BY rc.likeCount DESC, rc.createdAt DESC")
    List<RouteComment> findByRouteIdAndContentContaining(@Param("routeId") Long routeId, 
                                                        @Param("keyword") String keyword);
    
    /**
     * 복합 조건 댓글 검색
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE (:routeId IS NULL OR rc.route.routeId = :routeId) " +
           "AND (:userId IS NULL OR rc.user.userId = :userId) " +
           "AND (:commentType IS NULL OR rc.commentType = :commentType) " +
           "AND (:isAnonymous IS NULL OR rc.isAnonymous = :isAnonymous) " +
           "AND (:isPinned IS NULL OR rc.isPinned = :isPinned) " +
           "AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    Page<RouteComment> findByComplexConditions(@Param("routeId") Long routeId,
                                             @Param("userId") Long userId,
                                             @Param("commentType") String commentType,
                                             @Param("isAnonymous") Boolean isAnonymous,
                                             @Param("isPinned") Boolean isPinned,
                                             Pageable pageable);
    
    // ===== 관리자용 조회 =====
    
    /**
     * 신고 많은 댓글 조회
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.reportCount >= :minReports AND rc.isDeleted = false " +
           "ORDER BY rc.reportCount DESC")
    List<RouteComment> findHighlyReportedComments(@Param("minReports") Integer minReports);
    
    /**
     * 최근 댓글 조회 (전체)
     */
    @Query("SELECT rc FROM RouteComment rc " +
           "WHERE rc.createdAt >= :sinceDate AND rc.isDeleted = false " +
           "ORDER BY rc.createdAt DESC")
    List<RouteComment> findRecentComments(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * 활발한 사용자 댓글 조회
     */
    @Query("SELECT rc.user.userId, COUNT(rc) as commentCount FROM RouteComment rc " +
           "WHERE rc.createdAt >= :sinceDate AND rc.isDeleted = false " +
           "GROUP BY rc.user.userId " +
           "ORDER BY commentCount DESC")
    List<Object[]> findActiveCommenters(@Param("sinceDate") LocalDateTime sinceDate);
    
    // ===== 댓글 수 업데이트 =====
    
    /**
     * 루트의 댓글 수 업데이트 (Route 엔티티의 commentCount 업데이트용)
     */
    @Query("SELECT COUNT(rc) FROM RouteComment rc " +
           "WHERE rc.route.routeId = :routeId AND rc.isDeleted = false")
    long countActiveCommentsByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 사용자별 댓글 수 조회
     */
    @Query("SELECT COUNT(rc) FROM RouteComment rc " +
           "WHERE rc.user.userId = :userId AND rc.isDeleted = false")
    long countCommentsByUserId(@Param("userId") Long userId);
    
    /**
     * 대댓글 수 조회 (특정 댓글의)
     */
    @Query("SELECT COUNT(rc) FROM RouteComment rc " +
           "WHERE rc.parent.commentId = :parentId AND rc.isDeleted = false")
    long countRepliesByParentId(@Param("parentId") Long parentId);
}
```

---

## ⚡ 성능 최적화 전략

### 계층형 댓글 최적화 인덱스
```sql
-- 계층형 댓글 최적화 인덱스
CREATE INDEX idx_route_comment_hierarchy 
ON route_comments(route_id, parent_id, is_deleted, created_at DESC);

-- 댓글 타입별 검색 최적화
CREATE INDEX idx_route_comment_type_analysis 
ON route_comments(route_id, comment_type, is_pinned, like_count DESC);

-- 사용자 댓글 활동 추적
CREATE INDEX idx_route_comment_user_activity 
ON route_comments(user_id, created_at DESC, is_deleted);

-- 익명 댓글 처리 최적화
CREATE INDEX idx_route_comment_anonymous
ON route_comments(route_id, is_anonymous, is_deleted, created_at DESC);

-- 고정 댓글 우선 조회
CREATE INDEX idx_route_comment_pinned
ON route_comments(route_id, is_pinned DESC, is_deleted, created_at DESC);
```

### N+1 문제 해결 전략
```java
// EntityGraph를 활용한 효율적 조회
@EntityGraph(attributePaths = {"user", "children", "children.user"})
@Query("SELECT rc FROM RouteComment rc WHERE rc.route.routeId = :routeId")
List<RouteComment> findRouteCommentsTreeOptimized(@Param("routeId") Long routeId);

// Batch Size 설정으로 지연 로딩 최적화
@BatchSize(size = 20)
private Set<RouteComment> children;
```

### 소프트 삭제 성능 최적화
```sql
-- 소프트 삭제 상태 별도 인덱스
CREATE INDEX idx_route_comment_soft_delete
ON route_comments(is_deleted, deleted_at);

-- 활성 댓글만 조회 최적화
CREATE INDEX idx_route_comment_active_only
ON route_comments(route_id, is_deleted) WHERE is_deleted = false;
```

### 통계 쿼리 최적화
```java
// 통계 집계를 위한 전용 메서드
@Query(value = "SELECT comment_type, COUNT(*) as count FROM route_comments " +
               "WHERE route_id = :routeId AND is_deleted = false " +
               "GROUP BY comment_type", nativeQuery = true)
List<Object[]> getCommentTypeStatsNative(@Param("routeId") Long routeId);
```

---

## ✅ 설계 완료 체크리스트

### 계층형 댓글 Repository (1개)
- [x] **RouteCommentRepository** - 계층형 댓글, 소프트 삭제, 익명 처리

### 계층형 댓글 시스템 핵심 기능
- [x] 부모-자식 관계 최적화 조회
- [x] 댓글 깊이별 제한 및 관리  
- [x] 베타/세터 댓글 특별 처리
- [x] 소프트 삭제로 구조 유지

### 댓글 타입별 관리
- [x] 일반 댓글, 베타 댓글, 세터 댓글 구분
- [x] 고정 댓글 우선 노출
- [x] 익명/실명 댓글 분리 관리
- [x] 댓글 타입별 통계 제공

### 소프트 삭제 & 안전성
- [x] 계층 구조 유지하는 소프트 삭제
- [x] 대댓글 포함 일괄 삭제
- [x] 삭제된 댓글 표시 처리
- [x] 신고 시스템 연동

### 성능 최적화
- [x] @EntityGraph N+1 문제 해결
- [x] 복합 인덱스 전략 (route_id + parent_id + is_deleted)
- [x] 소프트 삭제 성능 최적화
- [x] 통계 집계 쿼리 최적화

---

**관련 파일**: step5-3e2_route_vote_scrap_repositories.md (RouteDifficultyVoteRepository, RouteScrapRepository)  
**완료일**: 2025-08-21  
**핵심 성과**: 계층형 댓글 Repository 완성 (소프트 삭제 + 익명 처리 + 타입별 관리)