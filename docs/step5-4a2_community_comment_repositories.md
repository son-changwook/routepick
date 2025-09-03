# Step5-4a2: Community Comment Repository (2/2)

> **커뮤니티 댓글 Repository**  
> 5단계 Repository 레이어 구현: Comment 계층형 구조 관리

---

## 📋 파일 분할 정보
- **원본 파일**: step5-4a_community_core_repositories.md (1,300줄)
- **분할 구성**: 2개 파일로 세분화
- **현재 파일**: step5-4a2_community_comment_repositories.md (2/2)
- **포함 Repository**: CommentRepository

---

## 🎯 설계 목표

- **계층형 댓글 시스템**: 무제한 깊이 대댓글 구조 및 성능 최적화
- **실시간 상호작용**: 좋아요, 베스트 댓글, 고정 댓글 관리
- **성능 중심 로딩**: 댓글 트리 구조 효율적 조회 및 페이징
- **관리 및 모더레이션**: 신고, 익명, 비밀 댓글 시스템

---

## 💬 CommentRepository - 댓글 Repository

```java
package com.routepick.domain.community.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.community.entity.Comment;
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
 * Comment Repository
 * - 💬 계층형 댓글 구조 최적화
 * - 실시간 상호작용 지원
 * - 성능 중심 댓글 트리 로딩
 * - 베스트 댓글 및 정렬 관리
 */
@Repository
public interface CommentRepository extends BaseRepository<Comment, Long> {
    
    // ===== 기본 댓글 조회 =====
    
    /**
     * 게시글의 최상위 댓글 조회 (최신순)
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.parent IS NULL " +
           "AND c.isDeleted = false " +
           "ORDER BY c.isPinned DESC, c.createdAt DESC")
    List<Comment> findByPostIdAndParentIdIsNullOrderByCreatedAtDesc(@Param("postId") Long postId);
    
    /**
     * 게시글의 최상위 댓글 조회 (좋아요순)
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.parent IS NULL " +
           "AND c.isDeleted = false " +
           "ORDER BY c.isPinned DESC, c.likeCount DESC, c.createdAt ASC")
    List<Comment> findByPostIdAndParentIdIsNullOrderByLikeCount(@Param("postId") Long postId);
    
    /**
     * 특정 댓글의 대댓글 조회 (시간순)
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.parent.commentId = :parentId " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt ASC")
    List<Comment> findByParentIdOrderByCreatedAtAsc(@Param("parentId") Long parentId);
    
    /**
     * 게시글의 모든 댓글 조회 (계층 구조 포함)
     */
    @EntityGraph(attributePaths = {"user", "children"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.isDeleted = false " +
           "ORDER BY " +
           "CASE WHEN c.parent IS NULL THEN c.commentId ELSE c.parent.commentId END, " +
           "c.parent.commentId ASC NULLS FIRST, " +
           "c.createdAt ASC")
    List<Comment> findAllCommentsByPostIdWithHierarchy(@Param("postId") Long postId);
    
    // ===== 페이징 지원 댓글 조회 =====
    
    /**
     * 게시글 댓글 페이징 조회
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.parent IS NULL " +
           "AND c.isDeleted = false " +
           "ORDER BY c.isPinned DESC, c.createdAt DESC")
    Page<Comment> findRootCommentsByPostId(@Param("postId") Long postId, Pageable pageable);
    
    /**
     * 대댓글 페이징 조회
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.parent.commentId = :parentId " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt ASC")
    Page<Comment> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);
    
    // ===== 사용자별 댓글 조회 =====
    
    /**
     * 사용자 작성 댓글 조회
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.user.userId = :userId " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    /**
     * 사용자 작성 댓글 페이징
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.user.userId = :userId " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    Page<Comment> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 사용자가 받은 댓글 조회 (내 게시글에 달린 댓글)
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.user.userId = :userId " +
           "AND c.user.userId != :userId " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findCommentsOnUserPosts(@Param("userId") Long userId);
    
    // ===== 베스트 댓글 및 정렬 =====
    
    /**
     * 게시글의 베스트 댓글 조회
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.isBestComment = true " +
           "AND c.isDeleted = false " +
           "ORDER BY c.likeCount DESC, c.createdAt ASC")
    List<Comment> findBestCommentsByPostId(@Param("postId") Long postId);
    
    /**
     * 고정 댓글 조회
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.isPinned = true " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findPinnedCommentsByPostId(@Param("postId") Long postId);
    
    /**
     * 작성자 댓글 조회
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.isAuthorComment = true " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findAuthorCommentsByPostId(@Param("postId") Long postId);
    
    /**
     * 인기 댓글 조회 (좋아요 많은 순)
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.likeCount >= :minLikes " +
           "AND c.isDeleted = false " +
           "ORDER BY c.likeCount DESC, c.createdAt ASC")
    List<Comment> findPopularCommentsByPostId(@Param("postId") Long postId, 
                                             @Param("minLikes") Integer minLikes);
    
    // ===== 댓글 검색 =====
    
    /**
     * 댓글 내용으로 검색
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.content LIKE %:keyword% " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findByContentContaining(@Param("keyword") String keyword);
    
    /**
     * 특정 게시글에서 댓글 검색
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.content LIKE %:keyword% " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findByPostIdAndContentContaining(@Param("postId") Long postId, 
                                                  @Param("keyword") String keyword);
    
    /**
     * 복합 조건 댓글 검색
     */
    @Query("SELECT c FROM Comment c " +
           "WHERE c.isDeleted = false " +
           "AND (:postId IS NULL OR c.post.postId = :postId) " +
           "AND (:userId IS NULL OR c.user.userId = :userId) " +
           "AND (:keyword IS NULL OR c.content LIKE %:keyword%) " +
           "AND (:startDate IS NULL OR c.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR c.createdAt <= :endDate) " +
           "AND (:minLikes IS NULL OR c.likeCount >= :minLikes) " +
           "ORDER BY c.createdAt DESC")
    Page<Comment> findByComplexConditions(@Param("postId") Long postId,
                                         @Param("userId") Long userId,
                                         @Param("keyword") String keyword,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate,
                                         @Param("minLikes") Integer minLikes,
                                         Pageable pageable);
    
    // ===== 댓글 통계 =====
    
    /**
     * 게시글별 댓글 수 조회
     */
    @Query("SELECT COUNT(c) FROM Comment c " +
           "WHERE c.post.postId = :postId AND c.isDeleted = false")
    long countCommentsByPostId(@Param("postId") Long postId);
    
    /**
     * 사용자별 댓글 수 조회
     */
    @Query("SELECT COUNT(c) FROM Comment c " +
           "WHERE c.user.userId = :userId AND c.isDeleted = false")
    long countCommentsByUserId(@Param("userId") Long userId);
    
    /**
     * 댓글의 대댓글 수 조회
     */
    @Query("SELECT COUNT(c) FROM Comment c " +
           "WHERE c.parent.commentId = :parentId AND c.isDeleted = false")
    long countRepliesByParentId(@Param("parentId") Long parentId);
    
    /**
     * 최근 댓글 통계 (일별)
     */
    @Query("SELECT DATE(c.createdAt) as commentDate, COUNT(c) as commentCount " +
           "FROM Comment c " +
           "WHERE c.isDeleted = false " +
           "AND c.createdAt >= :startDate " +
           "GROUP BY DATE(c.createdAt) " +
           "ORDER BY commentDate DESC")
    List<Object[]> getDailyCommentStatistics(@Param("startDate") LocalDateTime startDate);
    
    /**
     * 사용자별 댓글 통계
     */
    @Query("SELECT c.user.userId, c.user.nickName, " +
           "COUNT(c) as commentCount, " +
           "SUM(c.likeCount) as totalLikes, " +
           "AVG(c.likeCount) as avgLikes " +
           "FROM Comment c " +
           "WHERE c.isDeleted = false " +
           "GROUP BY c.user.userId, c.user.nickName " +
           "ORDER BY commentCount DESC")
    List<Object[]> getUserCommentStatistics();
    
    // ===== 최근 활동 =====
    
    /**
     * 최근 댓글 조회
     */
    @EntityGraph(attributePaths = {"user", "post", "post.category"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.isDeleted = false " +
           "AND c.createdAt >= :sinceDate " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findRecentComments(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    /**
     * 활발한 댓글 스레드 조회 (댓글이 많은 게시글)
     */
    @Query("SELECT c.post.postId, c.post.title, COUNT(c) as commentCount " +
           "FROM Comment c " +
           "WHERE c.isDeleted = false " +
           "AND c.createdAt >= :sinceDate " +
           "GROUP BY c.post.postId, c.post.title " +
           "ORDER BY commentCount DESC")
    List<Object[]> findActiveCommentThreads(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    // ===== 관리자 기능 =====
    
    /**
     * 신고된 댓글 조회
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.reportCount >= :minReportCount " +
           "AND c.isDeleted = false " +
           "ORDER BY c.reportCount DESC, c.createdAt DESC")
    List<Comment> findReportedComments(@Param("minReportCount") Integer minReportCount);
    
    /**
     * 비밀 댓글 조회
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.isPrivate = true " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findPrivateComments();
    
    /**
     * 익명 댓글 조회
     */
    @EntityGraph(attributePaths = {"post"})
    @Query("SELECT c FROM Comment c " +
           "WHERE c.isAnonymous = true " +
           "AND c.isDeleted = false " +
           "ORDER BY c.createdAt DESC")
    List<Comment> findAnonymousComments();
    
    // ===== 성능 최적화 =====
    
    /**
     * 댓글 좋아요 수 업데이트 (벌크 연산)
     */
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + 1 WHERE c.commentId = :commentId")
    void incrementLikeCount(@Param("commentId") Long commentId);
    
    /**
     * 댓글 좋아요 수 감소 (벌크 연산)
     */
    @Query("UPDATE Comment c SET c.likeCount = GREATEST(0, c.likeCount - 1) WHERE c.commentId = :commentId")
    void decrementLikeCount(@Param("commentId") Long commentId);
    
    /**
     * 댓글 답글 수 업데이트
     */
    @Query(value = "UPDATE comments c " +
                   "SET reply_count = (" +
                   "    SELECT COUNT(*) FROM comments c2 " +
                   "    WHERE c2.parent_id = c.comment_id AND c2.is_deleted = false" +
                   ") " +
                   "WHERE c.comment_id = :commentId", nativeQuery = true)
    void updateReplyCount(@Param("commentId") Long commentId);
    
    /**
     * 게시글 댓글 통계 일괄 업데이트
     */
    @Query(value = "UPDATE comments c " +
                   "SET like_count = (" +
                   "    SELECT COUNT(*) FROM comment_likes cl " +
                   "    WHERE cl.comment_id = c.comment_id AND cl.is_active = true" +
                   "), " +
                   "reply_count = (" +
                   "    SELECT COUNT(*) FROM comments c2 " +
                   "    WHERE c2.parent_id = c.comment_id AND c2.is_deleted = false" +
                   ") " +
                   "WHERE c.post_id = :postId", nativeQuery = true)
    void updateCommentStatisticsByPostId(@Param("postId") Long postId);
    
    /**
     * 특정 깊이 이상의 댓글 조회 (성능 제어)
     */
    @Query("SELECT c FROM Comment c " +
           "WHERE c.post.postId = :postId " +
           "AND c.isDeleted = false " +
           "AND (" +
           "    SELECT COUNT(p) FROM Comment p " +
           "    WHERE p.commentId = c.commentId " +
           "    OR (c.parent IS NOT NULL AND p.commentId = c.parent.commentId) " +
           "    OR (c.parent IS NOT NULL AND c.parent.parent IS NOT NULL AND p.commentId = c.parent.parent.commentId)" +
           ") <= :maxDepth " +
           "ORDER BY c.createdAt ASC")
    List<Comment> findCommentsByMaxDepth(@Param("postId") Long postId, @Param("maxDepth") Integer maxDepth);
    
    /**
     * 전체 댓글 수 조회
     */
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.isDeleted = false")
    long countAllComments();
    
    /**
     * 오늘 작성된 댓글 수
     */
    @Query("SELECT COUNT(c) FROM Comment c " +
           "WHERE c.isDeleted = false " +
           "AND DATE(c.createdAt) = CURRENT_DATE")
    long countTodayComments();
}
```

---

## ⚡ 성능 최적화 전략

### 계층형 댓글 최적화
```sql
-- 댓글 계층 구조 조회 최적화
CREATE INDEX idx_comment_hierarchy_optimal 
ON comments(post_id, parent_id, is_deleted, is_pinned DESC, like_count DESC, created_at ASC);

-- 댓글 통계 최적화
CREATE INDEX idx_comment_stats_optimization 
ON comments(post_id, is_deleted, like_count DESC);

-- 사용자별 댓글 조회 최적화
CREATE INDEX idx_comment_user_date 
ON comments(user_id, is_deleted, created_at DESC);
```

### 베스트 댓글 관리 최적화
```sql
-- 베스트 댓글 조회 최적화
CREATE INDEX idx_comment_best_like 
ON comments(post_id, is_best_comment, like_count DESC, created_at ASC);

-- 고정 댓글 최적화
CREATE INDEX idx_comment_pinned 
ON comments(post_id, is_pinned, created_at DESC);

-- 작성자 댓글 최적화
CREATE INDEX idx_comment_author 
ON comments(post_id, is_author_comment, created_at DESC);
```

### 댓글 검색 최적화
```sql
-- 댓글 내용 검색 최적화
CREATE FULLTEXT INDEX idx_comment_content_search 
ON comments(content);

-- 복합 조건 검색 최적화
CREATE INDEX idx_comment_complex_search 
ON comments(is_deleted, user_id, created_at DESC, like_count DESC);
```

### 실시간 상호작용 최적화
```sql
-- 좋아요 수 업데이트 최적화
CREATE INDEX idx_comment_like_update 
ON comments(comment_id, like_count);

-- 답글 수 업데이트 최적화
CREATE INDEX idx_comment_reply_count 
ON comments(parent_id, is_deleted);
```

---

## ✅ 설계 완료 체크리스트

### Comment Repository 핵심 기능
- [x] **계층형 댓글 구조** - 무제한 깊이 대댓글 지원
- [x] **실시간 상호작용** - 좋아요, 답글, 베스트 댓글 관리
- [x] **성능 최적화** - 계층 구조 효율적 조회 및 페이징
- [x] **검색 및 필터링** - 내용 검색, 복합 조건 필터

### 베스트 댓글 시스템
- [x] 좋아요 기준 베스트 댓글 선정
- [x] 고정 댓글 (관리자/작성자 권한)
- [x] 작성자 댓글 구분 표시
- [x] 인기 댓글 임계값 설정

### 사용자 경험 향상
- [x] 댓글 정렬 옵션 (최신순/좋아요순)
- [x] 페이징 지원 (루트 댓글/대댓글 별도)
- [x] 사용자별 댓글 이력 관리
- [x] 실시간 통계 업데이트

### 모더레이션 기능
- [x] 신고된 댓글 관리
- [x] 비밀 댓글 (작성자-게시글 작성자만 보기)
- [x] 익명 댓글 지원
- [x] 댓글 깊이 제한 (성능 제어)

### 성능 최적화
- [x] @EntityGraph N+1 문제 해결
- [x] 계층형 구조 복합 인덱스
- [x] 벌크 연산 통계 업데이트
- [x] 댓글 트리 로딩 최적화

### 통계 및 분석
- [x] 일별/사용자별 댓글 통계
- [x] 활발한 댓글 스레드 분석
- [x] 댓글 활동 패턴 추적
- [x] 실시간 댓글 수 관리

---

**분할 완료**: step5-4a_community_core_repositories.md → step5-4a1 + step5-4a2  
**완료일**: 2025-08-20  
**핵심 성과**: Comment Repository 완성 (계층형 구조 + 실시간 상호작용)