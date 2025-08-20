# Step 5-4b: 커뮤니티 상호작용 Repository 생성

> 게시글 좋아요, 북마크 2개 상호작용 Repository 완전 설계  
> 생성일: 2025-08-20  
> 기반: step5-4a_community_core_repositories.md

---

## 🎯 설계 목표

- **좋아요 최적화**: 중복 방지 및 빠른 좋아요 여부 확인
- **북마크 관리**: 사용자별 폴더 관리 및 개인화 기능
- **참여도 측정**: 사용자 참여도 및 콘텐츠 인기도 분석

---

## 💖 1. PostLikeRepository - 게시글 좋아요 Repository

```java
package com.routepick.domain.community.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.community.entity.PostLike;
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
 * PostLike Repository
 * - 💖 게시글 좋아요 최적화
 * - 중복 방지 및 빠른 조회
 * - 사용자 참여도 측정
 * - 다양한 반응 타입 지원
 */
@Repository
public interface PostLikeRepository extends BaseRepository<PostLike, Long> {
    
    // ===== 기본 좋아요 조회 =====
    
    /**
     * 게시글과 사용자로 좋아요 여부 확인
     */
    @Query("SELECT COUNT(pl) > 0 FROM PostLike pl " +
           "WHERE pl.post.postId = :postId " +
           "AND pl.user.userId = :userId " +
           "AND pl.isActive = true")
    boolean existsByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);
    
    /**
     * 게시글과 사용자의 좋아요 정보 조회
     */
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.post.postId = :postId " +
           "AND pl.user.userId = :userId")
    Optional<PostLike> findByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);
    
    /**
     * 게시글의 활성 좋아요 조회
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.post.postId = :postId " +
           "AND pl.isActive = true " +
           "ORDER BY pl.createdAt DESC")
    List<PostLike> findByPostIdAndIsActiveTrue(@Param("postId") Long postId);
    
    /**
     * 사용자가 좋아요한 게시글 목록
     */
    @EntityGraph(attributePaths = {"post", "post.category", "post.user"})
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.user.userId = :userId " +
           "AND pl.isActive = true " +
           "ORDER BY pl.createdAt DESC")
    List<PostLike> findByUserIdAndIsActiveTrue(@Param("userId") Long userId);
    
    // ===== 좋아요 수 통계 =====
    
    /**
     * 게시글별 좋아요 수 조회
     */
    @Query("SELECT COUNT(pl) FROM PostLike pl " +
           "WHERE pl.post.postId = :postId AND pl.isActive = true")
    long countByPostId(@Param("postId") Long postId);
    
    /**
     * 게시글별 좋아요 타입 통계
     */
    @Query("SELECT pl.likeType, COUNT(pl) as likeCount FROM PostLike pl " +
           "WHERE pl.post.postId = :postId AND pl.isActive = true " +
           "GROUP BY pl.likeType " +
           "ORDER BY likeCount DESC")
    List<Object[]> countByPostIdGroupByLikeType(@Param("postId") Long postId);
    
    /**
     * 사용자별 좋아요한 게시글 수
     */
    @Query("SELECT COUNT(pl) FROM PostLike pl " +
           "WHERE pl.user.userId = :userId AND pl.isActive = true")
    long countByUserId(@Param("userId") Long userId);
    
    /**
     * 게시글 작성자가 받은 총 좋아요 수
     */
    @Query("SELECT COUNT(pl) FROM PostLike pl " +
           "WHERE pl.post.user.userId = :authorId AND pl.isActive = true")
    long countByPostAuthorId(@Param("authorId") Long authorId);
    
    // ===== 좋아요 타입별 조회 =====
    
    /**
     * 특정 타입의 좋아요 조회
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.post.postId = :postId " +
           "AND pl.likeType = :likeType " +
           "AND pl.isActive = true " +
           "ORDER BY pl.createdAt DESC")
    List<PostLike> findByPostIdAndLikeType(@Param("postId") Long postId, 
                                          @Param("likeType") String likeType);
    
    /**
     * 게시글의 LIKE 타입만 조회
     */
    @Query("SELECT COUNT(pl) FROM PostLike pl " +
           "WHERE pl.post.postId = :postId " +
           "AND pl.likeType = 'LIKE' " +
           "AND pl.isActive = true")
    long countLikesByPostId(@Param("postId") Long postId);
    
    /**
     * 게시글의 DISLIKE 타입만 조회
     */
    @Query("SELECT COUNT(pl) FROM PostLike pl " +
           "WHERE pl.post.postId = :postId " +
           "AND pl.likeType = 'DISLIKE' " +
           "AND pl.isActive = true")
    long countDislikesByPostId(@Param("postId") Long postId);
    
    /**
     * 사용자별 좋아요 타입 통계
     */
    @Query("SELECT pl.likeType, COUNT(pl) as likeCount FROM PostLike pl " +
           "WHERE pl.user.userId = :userId AND pl.isActive = true " +
           "GROUP BY pl.likeType " +
           "ORDER BY likeCount DESC")
    List<Object[]> getUserLikeTypeStatistics(@Param("userId") Long userId);
    
    // ===== 페이징 지원 조회 =====
    
    /**
     * 게시글 좋아요 목록 페이징
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.post.postId = :postId " +
           "AND pl.isActive = true " +
           "ORDER BY pl.createdAt DESC")
    Page<PostLike> findByPostIdAndIsActiveTrue(@Param("postId") Long postId, Pageable pageable);
    
    /**
     * 사용자 좋아요 목록 페이징
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.user.userId = :userId " +
           "AND pl.isActive = true " +
           "ORDER BY pl.createdAt DESC")
    Page<PostLike> findByUserIdAndIsActiveTrue(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 시간 기반 통계 =====
    
    /**
     * 기간별 좋아요 통계
     */
    @Query("SELECT DATE(pl.createdAt) as likeDate, COUNT(pl) as likeCount " +
           "FROM PostLike pl " +
           "WHERE pl.isActive = true " +
           "AND pl.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(pl.createdAt) " +
           "ORDER BY likeDate DESC")
    List<Object[]> getLikeStatisticsByDateRange(@Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * 최근 좋아요 활동 조회
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.isActive = true " +
           "AND pl.createdAt >= :sinceDate " +
           "ORDER BY pl.createdAt DESC")
    List<PostLike> findRecentLikes(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    /**
     * 오늘의 좋아요 수
     */
    @Query("SELECT COUNT(pl) FROM PostLike pl " +
           "WHERE pl.isActive = true " +
           "AND DATE(pl.createdAt) = CURRENT_DATE")
    long countTodayLikes();
    
    /**
     * 게시글별 최근 좋아요 조회
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.post.postId = :postId " +
           "AND pl.isActive = true " +
           "AND pl.createdAt >= :sinceDate " +
           "ORDER BY pl.createdAt DESC")
    List<PostLike> findRecentLikesByPostId(@Param("postId") Long postId,
                                          @Param("sinceDate") LocalDateTime sinceDate);
    
    // ===== 인기 콘텐츠 분석 =====
    
    /**
     * 가장 많은 좋아요를 받은 게시글 TOP N
     */
    @Query("SELECT pl.post.postId, pl.post.title, COUNT(pl) as likeCount " +
           "FROM PostLike pl " +
           "WHERE pl.isActive = true " +
           "AND pl.createdAt >= :sinceDate " +
           "GROUP BY pl.post.postId, pl.post.title " +
           "ORDER BY likeCount DESC")
    List<Object[]> findMostLikedPosts(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    /**
     * 카테고리별 좋아요 통계
     */
    @Query("SELECT pl.post.category.categoryName, COUNT(pl) as likeCount " +
           "FROM PostLike pl " +
           "WHERE pl.isActive = true " +
           "AND pl.createdAt >= :sinceDate " +
           "GROUP BY pl.post.category.categoryId, pl.post.category.categoryName " +
           "ORDER BY likeCount DESC")
    List<Object[]> getLikeStatisticsByCategory(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * 활발한 사용자 TOP N (좋아요를 많이 누른 사용자)
     */
    @Query("SELECT pl.user.userId, pl.user.nickName, COUNT(pl) as likeCount " +
           "FROM PostLike pl " +
           "WHERE pl.isActive = true " +
           "AND pl.createdAt >= :sinceDate " +
           "GROUP BY pl.user.userId, pl.user.nickName " +
           "ORDER BY likeCount DESC")
    List<Object[]> findMostActiveLikers(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    /**
     * 좋아요를 많이 받은 작성자 TOP N
     */
    @Query("SELECT pl.post.user.userId, pl.post.user.nickName, COUNT(pl) as receivedLikes " +
           "FROM PostLike pl " +
           "WHERE pl.isActive = true " +
           "AND pl.createdAt >= :sinceDate " +
           "GROUP BY pl.post.user.userId, pl.post.user.nickName " +
           "ORDER BY receivedLikes DESC")
    List<Object[]> findMostLikedAuthors(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    // ===== 사용자 관계 분석 =====
    
    /**
     * 특정 사용자와 자주 상호작용하는 사용자 조회
     */
    @Query("SELECT pl.user.userId, pl.user.nickName, COUNT(pl) as interactionCount " +
           "FROM PostLike pl " +
           "WHERE pl.post.user.userId = :authorId " +
           "AND pl.user.userId != :authorId " +
           "AND pl.isActive = true " +
           "GROUP BY pl.user.userId, pl.user.nickName " +
           "ORDER BY interactionCount DESC")
    List<Object[]> findFrequentInteractionUsers(@Param("authorId") Long authorId, Pageable pageable);
    
    /**
     * 사용자가 좋아요한 게시글의 작성자들
     */
    @Query("SELECT pl.post.user.userId, pl.post.user.nickName, COUNT(pl) as likeCount " +
           "FROM PostLike pl " +
           "WHERE pl.user.userId = :userId " +
           "AND pl.isActive = true " +
           "GROUP BY pl.post.user.userId, pl.post.user.nickName " +
           "ORDER BY likeCount DESC")
    List<Object[]> findLikedAuthors(@Param("userId") Long userId);
    
    // ===== 관리자 기능 =====
    
    /**
     * 비활성 좋아요 조회 (취소된 좋아요)
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.isActive = false " +
           "ORDER BY pl.updatedAt DESC")
    List<PostLike> findInactiveLikes(Pageable pageable);
    
    /**
     * 동일 IP에서의 좋아요 조회 (어뷰징 탐지)
     */
    @Query("SELECT pl.clientIp, COUNT(pl) as likeCount FROM PostLike pl " +
           "WHERE pl.clientIp IS NOT NULL " +
           "AND pl.isActive = true " +
           "AND pl.createdAt >= :sinceDate " +
           "GROUP BY pl.clientIp " +
           "HAVING COUNT(pl) > :threshold " +
           "ORDER BY likeCount DESC")
    List<Object[]> findSuspiciousIpLikes(@Param("sinceDate") LocalDateTime sinceDate,
                                        @Param("threshold") Integer threshold);
    
    /**
     * 짧은 시간 내 많은 좋아요 조회 (어뷰징 탐지)
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT pl FROM PostLike pl " +
           "WHERE pl.user.userId = :userId " +
           "AND pl.createdAt >= :sinceDate " +
           "AND pl.isActive = true " +
           "ORDER BY pl.createdAt DESC")
    List<PostLike> findRapidLikesByUser(@Param("userId") Long userId,
                                       @Param("sinceDate") LocalDateTime sinceDate);
    
    // ===== 성능 최적화 =====
    
    /**
     * 게시글 좋아요 수 증가 (벌크 연산)
     */
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.postId = :postId")
    void incrementPostLikeCount(@Param("postId") Long postId);
    
    /**
     * 게시글 좋아요 수 감소 (벌크 연산)
     */
    @Query("UPDATE Post p SET p.likeCount = GREATEST(0, p.likeCount - 1) WHERE p.postId = :postId")
    void decrementPostLikeCount(@Param("postId") Long postId);
    
    /**
     * 좋아요 활성화
     */
    @Query("UPDATE PostLike pl SET pl.isActive = true WHERE pl.likeId = :likeId")
    void activateLike(@Param("likeId") Long likeId);
    
    /**
     * 좋아요 비활성화
     */
    @Query("UPDATE PostLike pl SET pl.isActive = false WHERE pl.likeId = :likeId")
    void deactivateLike(@Param("likeId") Long likeId);
    
    /**
     * 좋아요 타입 변경
     */
    @Query("UPDATE PostLike pl SET pl.likeType = :newType WHERE pl.likeId = :likeId")
    void updateLikeType(@Param("likeId") Long likeId, @Param("newType") String newType);
    
    /**
     * 전체 좋아요 통계 요약
     */
    @Query("SELECT " +
           "COUNT(pl) as totalLikes, " +
           "COUNT(DISTINCT pl.user.userId) as uniqueUsers, " +
           "COUNT(DISTINCT pl.post.postId) as uniquePosts, " +
           "AVG(pl.post.likeCount) as avgLikesPerPost " +
           "FROM PostLike pl " +
           "WHERE pl.isActive = true")
    List<Object[]> getLikeStatisticsSummary();
    
    /**
     * 게시글별 좋아요 통계 일괄 업데이트
     */
    @Query(value = "UPDATE posts p " +
                   "SET like_count = (" +
                   "    SELECT COUNT(*) FROM post_likes pl " +
                   "    WHERE pl.post_id = p.post_id " +
                   "    AND pl.is_active = true " +
                   "    AND pl.like_type = 'LIKE'" +
                   "), " +
                   "dislike_count = (" +
                   "    SELECT COUNT(*) FROM post_likes pl " +
                   "    WHERE pl.post_id = p.post_id " +
                   "    AND pl.is_active = true " +
                   "    AND pl.like_type = 'DISLIKE'" +
                   ")", nativeQuery = true)
    void updateAllPostLikeStatistics();
    
    /**
     * 특정 게시글 좋아요 통계 업데이트
     */
    @Query(value = "UPDATE posts " +
                   "SET like_count = (" +
                   "    SELECT COUNT(*) FROM post_likes " +
                   "    WHERE post_id = :postId " +
                   "    AND is_active = true " +
                   "    AND like_type = 'LIKE'" +
                   "), " +
                   "dislike_count = (" +
                   "    SELECT COUNT(*) FROM post_likes " +
                   "    WHERE post_id = :postId " +
                   "    AND is_active = true " +
                   "    AND like_type = 'DISLIKE'" +
                   ") " +
                   "WHERE post_id = :postId", nativeQuery = true)
    void updatePostLikeStatistics(@Param("postId") Long postId);
    
    /**
     * 전체 활성 좋아요 수 조회
     */
    @Query("SELECT COUNT(pl) FROM PostLike pl WHERE pl.isActive = true")
    long countActiveLikes();
}
```

---

## 📌 2. PostBookmarkRepository - 게시글 북마크 Repository

```java
package com.routepick.domain.community.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.community.entity.PostBookmark;
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
 * PostBookmark Repository
 * - 📌 게시글 북마크 최적화
 * - 사용자별 폴더 관리
 * - 개인화 기능 지원
 * - 북마크 통계 및 분석
 */
@Repository
public interface PostBookmarkRepository extends BaseRepository<PostBookmark, Long> {
    
    // ===== 기본 북마크 조회 =====
    
    /**
     * 사용자별 북마크 목록 (최신순)
     */
    @EntityGraph(attributePaths = {"post", "post.category", "post.user"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    /**
     * 사용자별 북마크 목록 페이징
     */
    @EntityGraph(attributePaths = {"post", "post.category", "post.user"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "ORDER BY pb.createdAt DESC")
    Page<PostBookmark> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 북마크 여부 확인
     */
    @Query("SELECT COUNT(pb) > 0 FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId AND pb.post.postId = :postId")
    boolean existsByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
    
    /**
     * 특정 사용자와 게시글의 북마크 조회
     */
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId AND pb.post.postId = :postId")
    Optional<PostBookmark> findByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
    
    /**
     * 게시글별 북마크 목록
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.post.postId = :postId " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findByPostIdOrderByCreatedAtDesc(@Param("postId") Long postId);
    
    // ===== 폴더별 북마크 관리 =====
    
    /**
     * 사용자의 특정 폴더 북마크 조회
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId AND pb.folderName = :folderName " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findByUserIdAndFolderName(@Param("userId") Long userId, 
                                                 @Param("folderName") String folderName);
    
    /**
     * 사용자의 폴더별 북마크 페이징
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId AND pb.folderName = :folderName " +
           "ORDER BY pb.createdAt DESC")
    Page<PostBookmark> findByUserIdAndFolderName(@Param("userId") Long userId,
                                                 @Param("folderName") String folderName,
                                                 Pageable pageable);
    
    /**
     * 사용자의 모든 폴더 목록 조회
     */
    @Query("SELECT DISTINCT pb.folderName FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "ORDER BY pb.folderName ASC")
    List<String> findDistinctFolderNamesByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 폴더별 북마크 수 통계
     */
    @Query("SELECT pb.folderName, COUNT(pb) as bookmarkCount FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "GROUP BY pb.folderName " +
           "ORDER BY bookmarkCount DESC")
    List<Object[]> countBookmarksByFolder(@Param("userId") Long userId);
    
    /**
     * 빈 폴더 조회 (북마크가 없는 폴더)
     */
    @Query("SELECT DISTINCT pb.folderName FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "AND pb.folderName NOT IN (" +
           "    SELECT pb2.folderName FROM PostBookmark pb2 " +
           "    WHERE pb2.user.userId = :userId" +
           ")")
    List<String> findEmptyFolders(@Param("userId") Long userId);
    
    // ===== 우선순위 및 즐겨찾기 =====
    
    /**
     * 즐겨찾기 북마크 조회
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId AND pb.isFavorite = true " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findFavoriteBookmarks(@Param("userId") Long userId);
    
    /**
     * 우선순위별 북마크 조회
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "ORDER BY pb.priorityLevel ASC, pb.createdAt DESC")
    List<PostBookmark> findByUserIdOrderByPriority(@Param("userId") Long userId);
    
    /**
     * 나중에 읽기 북마크 조회
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId AND pb.readLater = true " +
           "ORDER BY pb.priorityLevel ASC, pb.createdAt DESC")
    List<PostBookmark> findReadLaterBookmarks(@Param("userId") Long userId);
    
    /**
     * 높은 우선순위 북마크 조회
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "AND pb.priorityLevel <= :maxPriority " +
           "ORDER BY pb.priorityLevel ASC, pb.createdAt DESC")
    List<PostBookmark> findHighPriorityBookmarks(@Param("userId") Long userId,
                                                @Param("maxPriority") Integer maxPriority);
    
    // ===== 북마크 검색 =====
    
    /**
     * 개인 메모로 북마크 검색
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "AND pb.personalMemo LIKE %:keyword% " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findByUserIdAndPersonalMemoContaining(@Param("userId") Long userId,
                                                            @Param("keyword") String keyword);
    
    /**
     * 개인 태그로 북마크 검색
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "AND pb.personalTags LIKE %:tag% " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findByUserIdAndPersonalTagsContaining(@Param("userId") Long userId,
                                                            @Param("tag") String tag);
    
    /**
     * 게시글 제목으로 북마크 검색
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "AND pb.post.title LIKE %:keyword% " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findByUserIdAndPostTitleContaining(@Param("userId") Long userId,
                                                         @Param("keyword") String keyword);
    
    /**
     * 복합 조건 북마크 검색
     */
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "AND (:folderName IS NULL OR pb.folderName = :folderName) " +
           "AND (:isFavorite IS NULL OR pb.isFavorite = :isFavorite) " +
           "AND (:readLater IS NULL OR pb.readLater = :readLater) " +
           "AND (:maxPriority IS NULL OR pb.priorityLevel <= :maxPriority) " +
           "AND (:keyword IS NULL " +
           "     OR pb.post.title LIKE %:keyword% " +
           "     OR pb.personalMemo LIKE %:keyword% " +
           "     OR pb.personalTags LIKE %:keyword%) " +
           "ORDER BY pb.priorityLevel ASC, pb.createdAt DESC")
    Page<PostBookmark> findByComplexConditions(@Param("userId") Long userId,
                                              @Param("folderName") String folderName,
                                              @Param("isFavorite") Boolean isFavorite,
                                              @Param("readLater") Boolean readLater,
                                              @Param("maxPriority") Integer maxPriority,
                                              @Param("keyword") String keyword,
                                              Pageable pageable);
    
    // ===== 북마크 통계 =====
    
    /**
     * 사용자별 북마크 수 조회
     */
    @Query("SELECT COUNT(pb) FROM PostBookmark pb WHERE pb.user.userId = :userId")
    long countByUserId(@Param("userId") Long userId);
    
    /**
     * 게시글별 북마크 수 조회
     */
    @Query("SELECT COUNT(pb) FROM PostBookmark pb WHERE pb.post.postId = :postId")
    long countByPostId(@Param("postId") Long postId);
    
    /**
     * 게시글 작성자별 받은 북마크 수
     */
    @Query("SELECT COUNT(pb) FROM PostBookmark pb WHERE pb.post.user.userId = :authorId")
    long countByPostAuthorId(@Param("authorId") Long authorId);
    
    /**
     * 기간별 북마크 통계
     */
    @Query("SELECT DATE(pb.createdAt) as bookmarkDate, COUNT(pb) as bookmarkCount " +
           "FROM PostBookmark pb " +
           "WHERE pb.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(pb.createdAt) " +
           "ORDER BY bookmarkDate DESC")
    List<Object[]> getBookmarkStatisticsByDateRange(@Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);
    
    /**
     * 가장 많이 북마크된 게시글 TOP N
     */
    @Query("SELECT pb.post.postId, pb.post.title, COUNT(pb) as bookmarkCount " +
           "FROM PostBookmark pb " +
           "WHERE pb.createdAt >= :sinceDate " +
           "GROUP BY pb.post.postId, pb.post.title " +
           "ORDER BY bookmarkCount DESC")
    List<Object[]> findMostBookmarkedPosts(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    /**
     * 카테고리별 북마크 통계
     */
    @Query("SELECT pb.post.category.categoryName, COUNT(pb) as bookmarkCount " +
           "FROM PostBookmark pb " +
           "WHERE pb.createdAt >= :sinceDate " +
           "GROUP BY pb.post.category.categoryId, pb.post.category.categoryName " +
           "ORDER BY bookmarkCount DESC")
    List<Object[]> getBookmarkStatisticsByCategory(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * 사용자별 북마크 활동 통계
     */
    @Query("SELECT pb.user.userId, pb.user.nickName, " +
           "COUNT(pb) as bookmarkCount, " +
           "COUNT(DISTINCT pb.folderName) as folderCount, " +
           "COUNT(CASE WHEN pb.isFavorite = true THEN 1 END) as favoriteCount " +
           "FROM PostBookmark pb " +
           "WHERE pb.createdAt >= :sinceDate " +
           "GROUP BY pb.user.userId, pb.user.nickName " +
           "ORDER BY bookmarkCount DESC")
    List<Object[]> getUserBookmarkStatistics(@Param("sinceDate") LocalDateTime sinceDate);
    
    // ===== 공개 북마크 및 추천 =====
    
    /**
     * 공개 북마크 조회
     */
    @EntityGraph(attributePaths = {"user", "post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.isPublic = true " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findPublicBookmarks(Pageable pageable);
    
    /**
     * 인기 공개 북마크 조회
     */
    @EntityGraph(attributePaths = {"user", "post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.isPublic = true " +
           "AND pb.post.likeCount >= :minLikes " +
           "ORDER BY pb.post.likeCount DESC, pb.createdAt DESC")
    List<PostBookmark> findPopularPublicBookmarks(@Param("minLikes") Integer minLikes, Pageable pageable);
    
    /**
     * 북마크 추천 (유사한 취향의 사용자 기반)
     */
    @Query("SELECT pb.post.postId, pb.post.title, COUNT(pb) as bookmarkCount " +
           "FROM PostBookmark pb " +
           "WHERE pb.user.userId IN (" +
           "  SELECT pb2.user.userId FROM PostBookmark pb2 " +
           "  WHERE pb2.post.postId IN (" +
           "    SELECT pb3.post.postId FROM PostBookmark pb3 " +
           "    WHERE pb3.user.userId = :userId" +
           "  ) AND pb2.user.userId != :userId" +
           ") " +
           "AND pb.post.postId NOT IN (" +
           "  SELECT pb4.post.postId FROM PostBookmark pb4 " +
           "  WHERE pb4.user.userId = :userId" +
           ") " +
           "GROUP BY pb.post.postId, pb.post.title " +
           "ORDER BY bookmarkCount DESC")
    List<Object[]> findRecommendedBookmarks(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 최근 활동 =====
    
    /**
     * 최근 북마크 활동 조회
     */
    @EntityGraph(attributePaths = {"user", "post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.createdAt >= :sinceDate " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findRecentBookmarks(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    /**
     * 사용자의 최근 북마크 조회
     */
    @EntityGraph(attributePaths = {"post", "post.category"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.user.userId = :userId " +
           "AND pb.createdAt >= :sinceDate " +
           "ORDER BY pb.createdAt DESC")
    List<PostBookmark> findRecentBookmarksByUser(@Param("userId") Long userId,
                                                @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * 오늘의 북마크 수
     */
    @Query("SELECT COUNT(pb) FROM PostBookmark pb " +
           "WHERE DATE(pb.createdAt) = CURRENT_DATE")
    long countTodayBookmarks();
    
    // ===== 관리 기능 =====
    
    /**
     * 중복 북마크 조회 (같은 사용자, 같은 게시글)
     */
    @Query("SELECT pb.user.userId, pb.post.postId, COUNT(pb) as duplicateCount FROM PostBookmark pb " +
           "GROUP BY pb.user.userId, pb.post.postId " +
           "HAVING COUNT(pb) > 1")
    List<Object[]> findDuplicateBookmarks();
    
    /**
     * 삭제된 게시글의 북마크 조회
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.post.isDeleted = true")
    List<PostBookmark> findBookmarksOfDeletedPosts();
    
    /**
     * 오래된 북마크 조회 (N개월 이상)
     */
    @EntityGraph(attributePaths = {"user", "post"})
    @Query("SELECT pb FROM PostBookmark pb " +
           "WHERE pb.createdAt < :cutoffDate " +
           "ORDER BY pb.createdAt ASC")
    List<PostBookmark> findOldBookmarks(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ===== 성능 최적화 =====
    
    /**
     * 게시글 북마크 수 증가 (벌크 연산)
     */
    @Query("UPDATE Post p SET p.bookmarkCount = p.bookmarkCount + 1 WHERE p.postId = :postId")
    void incrementPostBookmarkCount(@Param("postId") Long postId);
    
    /**
     * 게시글 북마크 수 감소 (벌크 연산)
     */
    @Query("UPDATE Post p SET p.bookmarkCount = GREATEST(0, p.bookmarkCount - 1) WHERE p.postId = :postId")
    void decrementPostBookmarkCount(@Param("postId") Long postId);
    
    /**
     * 게시글별 북마크 통계 일괄 업데이트
     */
    @Query(value = "UPDATE posts p " +
                   "SET bookmark_count = (" +
                   "    SELECT COUNT(*) FROM post_bookmarks pb " +
                   "    WHERE pb.post_id = p.post_id" +
                   ")", nativeQuery = true)
    void updateAllPostBookmarkStatistics();
    
    /**
     * 특정 게시글 북마크 통계 업데이트
     */
    @Query(value = "UPDATE posts " +
                   "SET bookmark_count = (" +
                   "    SELECT COUNT(*) FROM post_bookmarks " +
                   "    WHERE post_id = :postId" +
                   ") " +
                   "WHERE post_id = :postId", nativeQuery = true)
    void updatePostBookmarkStatistics(@Param("postId") Long postId);
    
    /**
     * 전체 북마크 통계 요약
     */
    @Query("SELECT " +
           "COUNT(pb) as totalBookmarks, " +
           "COUNT(DISTINCT pb.user.userId) as uniqueUsers, " +
           "COUNT(DISTINCT pb.post.postId) as uniquePosts, " +
           "COUNT(DISTINCT pb.folderName) as totalFolders, " +
           "AVG(pb.post.bookmarkCount) as avgBookmarksPerPost " +
           "FROM PostBookmark pb")
    List<Object[]> getBookmarkStatisticsSummary();
    
    /**
     * 전체 북마크 수 조회
     */
    @Query("SELECT COUNT(pb) FROM PostBookmark pb")
    long countAllBookmarks();
    
    /**
     * 폴더별 북마크 이동 (벌크 연산)
     */
    @Query("UPDATE PostBookmark pb SET pb.folderName = :newFolderName " +
           "WHERE pb.user.userId = :userId AND pb.folderName = :oldFolderName")
    void moveFolderBookmarks(@Param("userId") Long userId,
                            @Param("oldFolderName") String oldFolderName,
                            @Param("newFolderName") String newFolderName);
}
```

---

## ⚡ 3. 성능 최적화 전략

### 좋아요 시스템 최적화
```sql
-- 좋아요 중복 방지 및 빠른 조회 최적화
CREATE UNIQUE INDEX idx_postlike_unique_constraint 
ON post_likes(post_id, user_id);

-- 좋아요 타입별 조회 최적화
CREATE INDEX idx_postlike_type_active_date 
ON post_likes(post_id, like_type, is_active, created_at DESC);

-- 사용자별 좋아요 활동 최적화
CREATE INDEX idx_postlike_user_active_date 
ON post_likes(user_id, is_active, created_at DESC);

-- 인기 게시글 분석 최적화
CREATE INDEX idx_postlike_stats_optimization 
ON post_likes(is_active, created_at DESC, post_id);
```

### 북마크 시스템 최적화
```sql
-- 북마크 중복 방지 및 빠른 조회 최적화
CREATE UNIQUE INDEX idx_bookmark_unique_constraint 
ON post_bookmarks(user_id, post_id);

-- 폴더별 북마크 조회 최적화
CREATE INDEX idx_bookmark_user_folder_date 
ON post_bookmarks(user_id, folder_name, created_at DESC);

-- 우선순위별 북마크 최적화
CREATE INDEX idx_bookmark_user_priority_date 
ON post_bookmarks(user_id, priority_level ASC, created_at DESC);

-- 북마크 검색 최적화
CREATE INDEX idx_bookmark_search_optimization 
ON post_bookmarks(user_id, is_favorite, read_later, priority_level);

-- 공개 북마크 조회 최적화
CREATE INDEX idx_bookmark_public_date 
ON post_bookmarks(is_public, created_at DESC);
```

### 통계 집계 최적화
```sql
-- 게시글 통계 업데이트 최적화
CREATE INDEX idx_post_stats_update 
ON post_likes(post_id, is_active, like_type);

CREATE INDEX idx_post_bookmark_stats 
ON post_bookmarks(post_id);

-- 사용자 활동 분석 최적화
CREATE INDEX idx_user_activity_analysis 
ON post_likes(user_id, created_at DESC, is_active);

CREATE INDEX idx_user_bookmark_activity 
ON post_bookmarks(user_id, created_at DESC);
```

---

## ✅ 설계 완료 체크리스트

### 커뮤니티 상호작용 Repository (2개)
- [x] **PostLikeRepository** - 게시글 좋아요 최적화 및 중복 방지
- [x] **PostBookmarkRepository** - 북마크 폴더 관리 및 개인화 기능

### 핵심 기능 구현
- [x] 좋아요/싫어요 중복 방지 및 빠른 여부 확인
- [x] 다양한 반응 타입 지원 (LIKE, DISLIKE, LOVE, LAUGH, ANGRY)
- [x] 폴더별 북마크 관리 및 개인 메모 시스템
- [x] 우선순위 및 즐겨찾기 북마크 지원

### 성능 최적화
- [x] 고유 인덱스를 통한 중복 방지
- [x] 복합 인덱스를 통한 빠른 조회
- [x] 벌크 연산을 통한 통계 업데이트
- [x] @EntityGraph N+1 문제 해결

### 사용자 참여도 분석
- [x] 좋아요/북마크 통계 및 동향 분석
- [x] 인기 콘텐츠 및 활발한 사용자 식별
- [x] 사용자 관계 분석 (상호작용 패턴)
- [x] 콘텐츠 추천 시스템 지원

### 개인화 기능
- [x] 북마크 폴더 시스템 (기본 폴더, 사용자 정의 폴더)
- [x] 개인 메모 및 태그 시스템
- [x] 우선순위 레벨 관리 (1-5단계)
- [x] 나중에 읽기 및 즐겨찾기 기능

### 관리자 기능
- [x] 어뷰징 탐지 (동일 IP, 단시간 대량 좋아요)
- [x] 중복 데이터 관리
- [x] 통계 일괄 업데이트
- [x] 비활성 데이터 관리

---

**다음 단계**: Step 5-4c 커뮤니티 미디어 Repository 설계 또는 다른 도메인으로 진행  
**완료일**: 2025-08-20  
**핵심 성과**: 2개 상호작용 Repository + 중복 방지 + 개인화 시스템 완료