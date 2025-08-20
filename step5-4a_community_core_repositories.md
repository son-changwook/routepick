# Step 5-4a: 커뮤니티 핵심 Repository 생성

> 게시판 카테고리, 게시글, 댓글 3개 핵심 Repository 완전 설계  
> 생성일: 2025-08-20  
> 기반: step5-3e_climbing_activity_repositories.md, step4-4a_community_entities.md

---

## 🎯 설계 목표

- **카테고리 관리**: 계층형 카테고리 구조 및 권한 기반 접근 제어
- **게시글 최적화**: 성능 중심 게시글 목록 조회 및 검색
- **댓글 시스템**: 계층형 댓글 구조 및 실시간 상호작용

---

## 📂 1. BoardCategoryRepository - 게시판 카테고리 Repository

```java
package com.routepick.domain.community.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.community.entity.BoardCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * BoardCategory Repository
 * - 📂 계층형 카테고리 구조 관리
 * - 권한 기반 접근 제어
 * - 게시판 통계 및 최적화
 * - 카테고리별 성능 관리
 */
@Repository
public interface BoardCategoryRepository extends BaseRepository<BoardCategory, Long> {
    
    // ===== 기본 카테고리 조회 =====
    
    /**
     * 활성 카테고리 전체 조회 (표시 순서별)
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true " +
           "ORDER BY bc.displayOrder ASC, bc.categoryName ASC")
    List<BoardCategory> findAllOrderByDisplayOrder();
    
    /**
     * 최상위 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.parentCategory IS NULL AND bc.isActive = true " +
           "ORDER BY bc.displayOrder ASC")
    List<BoardCategory> findRootCategories();
    
    /**
     * 특정 부모의 하위 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.parentCategory.categoryId = :parentId AND bc.isActive = true " +
           "ORDER BY bc.displayOrder ASC")
    List<BoardCategory> findByParentCategoryId(@Param("parentId") Long parentId);
    
    /**
     * 카테고리 코드로 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.categoryCode = :categoryCode AND bc.isActive = true")
    Optional<BoardCategory> findByCategoryCode(@Param("categoryCode") String categoryCode);
    
    /**
     * 슬러그로 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.slug = :slug AND bc.isActive = true")
    Optional<BoardCategory> findBySlug(@Param("slug") String slug);
    
    // ===== 계층형 구조 관리 =====
    
    /**
     * 특정 카테고리의 모든 하위 카테고리 조회 (재귀)
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.parentCategory.categoryId = :parentId " +
           "OR bc.categoryId IN (" +
           "  SELECT bc2.categoryId FROM BoardCategory bc2 " +
           "  WHERE bc2.parentCategory.categoryId = :parentId" +
           ") " +
           "ORDER BY bc.categoryLevel ASC, bc.displayOrder ASC")
    List<BoardCategory> findAllSubCategories(@Param("parentId") Long parentId);
    
    /**
     * 특정 깊이의 카테고리들 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.categoryLevel = :level AND bc.isActive = true " +
           "ORDER BY bc.displayOrder ASC")
    List<BoardCategory> findByCategoryLevel(@Param("level") Integer level);
    
    /**
     * 최대 깊이 조회
     */
    @Query("SELECT MAX(bc.categoryLevel) FROM BoardCategory bc " +
           "WHERE bc.isActive = true")
    Integer getMaxCategoryLevel();
    
    /**
     * 카테고리 경로 조회 (부모 → 자식)
     */
    @Query("WITH RECURSIVE category_path AS (" +
           "  SELECT bc.categoryId, bc.categoryName, bc.parentCategory, 1 as depth " +
           "  FROM BoardCategory bc " +
           "  WHERE bc.categoryId = :categoryId " +
           "  UNION ALL " +
           "  SELECT p.categoryId, p.categoryName, p.parentCategory, cp.depth + 1 " +
           "  FROM BoardCategory p " +
           "  INNER JOIN category_path cp ON p.categoryId = cp.parentCategory " +
           ") SELECT * FROM category_path ORDER BY depth DESC")
    List<Object[]> getCategoryPath(@Param("categoryId") Long categoryId);
    
    // ===== 권한 및 접근 제어 =====
    
    /**
     * 공개 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true AND bc.isPublic = true " +
           "ORDER BY bc.displayOrder ASC")
    List<BoardCategory> findPublicCategories();
    
    /**
     * 권한별 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true " +
           "AND (bc.readPermission = 'ALL' " +
           "     OR (bc.readPermission = 'USER' AND :userRole != 'GUEST') " +
           "     OR (bc.readPermission = 'ADMIN' AND :userRole = 'ADMIN')) " +
           "ORDER BY bc.displayOrder ASC")
    List<BoardCategory> findByUserPermission(@Param("userRole") String userRole);
    
    /**
     * 쓰기 가능한 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true AND bc.isPublic = true " +
           "AND (bc.writePermission = 'ALL' " +
           "     OR (bc.writePermission = 'USER' AND :userRole != 'GUEST') " +
           "     OR (bc.writePermission = 'ADMIN' AND :userRole = 'ADMIN')) " +
           "ORDER BY bc.displayOrder ASC")
    List<BoardCategory> findWritableCategories(@Param("userRole") String userRole);
    
    /**
     * 파일 업로드 가능한 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true AND bc.allowFileUpload = true " +
           "ORDER BY bc.displayOrder ASC")
    List<BoardCategory> findFileUploadEnabledCategories();
    
    // ===== 카테고리 통계 =====
    
    /**
     * 카테고리별 게시글 수 조회
     */
    @Query("SELECT bc.categoryId, bc.categoryName, bc.postCount " +
           "FROM BoardCategory bc " +
           "WHERE bc.isActive = true " +
           "ORDER BY bc.postCount DESC")
    List<Object[]> countPostsByCategory();
    
    /**
     * 인기 카테고리 조회 (게시글 수 기준)
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true AND bc.postCount > 0 " +
           "ORDER BY bc.postCount DESC, bc.totalViewCount DESC")
    List<BoardCategory> findPopularCategories(Pageable pageable);
    
    /**
     * 최근 활동 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true " +
           "AND bc.lastPostDate >= :sinceDate " +
           "ORDER BY bc.lastPostDate DESC")
    List<BoardCategory> findRecentActiveCategories(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * 오늘 게시글이 있는 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true AND bc.todayPostCount > 0 " +
           "ORDER BY bc.todayPostCount DESC")
    List<BoardCategory> findTodayActiveCategories();
    
    // ===== 카테고리 검색 =====
    
    /**
     * 카테고리명으로 검색
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true " +
           "AND (bc.categoryName LIKE %:keyword% " +
           "     OR bc.description LIKE %:keyword%) " +
           "ORDER BY bc.displayOrder ASC")
    List<BoardCategory> searchByName(@Param("keyword") String keyword);
    
    /**
     * 카테고리 타입별 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.categoryType = :categoryType AND bc.isActive = true " +
           "ORDER BY bc.displayOrder ASC")
    List<BoardCategory> findByCategoryType(@Param("categoryType") String categoryType);
    
    /**
     * 복합 조건 카테고리 검색
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true " +
           "AND (:categoryType IS NULL OR bc.categoryType = :categoryType) " +
           "AND (:isPublic IS NULL OR bc.isPublic = :isPublic) " +
           "AND (:parentId IS NULL OR bc.parentCategory.categoryId = :parentId) " +
           "AND (:minPostCount IS NULL OR bc.postCount >= :minPostCount) " +
           "ORDER BY bc.displayOrder ASC")
    Page<BoardCategory> findByComplexConditions(@Param("categoryType") String categoryType,
                                               @Param("isPublic") Boolean isPublic,
                                               @Param("parentId") Long parentId,
                                               @Param("minPostCount") Integer minPostCount,
                                               Pageable pageable);
    
    // ===== 관리자 기능 =====
    
    /**
     * 표시 순서 중복 확인
     */
    @Query("SELECT COUNT(bc) > 1 FROM BoardCategory bc " +
           "WHERE bc.displayOrder = :displayOrder " +
           "AND (:parentId IS NULL AND bc.parentCategory IS NULL " +
           "     OR bc.parentCategory.categoryId = :parentId)")
    boolean hasDisplayOrderConflict(@Param("displayOrder") Integer displayOrder,
                                   @Param("parentId") Long parentId);
    
    /**
     * 다음 표시 순서 조회
     */
    @Query("SELECT COALESCE(MAX(bc.displayOrder), 0) + 1 FROM BoardCategory bc " +
           "WHERE (:parentId IS NULL AND bc.parentCategory IS NULL " +
           "       OR bc.parentCategory.categoryId = :parentId)")
    Integer getNextDisplayOrder(@Param("parentId") Long parentId);
    
    /**
     * 승인 대기 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.requireApproval = true AND bc.isActive = false " +
           "ORDER BY bc.createdAt ASC")
    List<BoardCategory> findPendingApprovalCategories();
    
    /**
     * 비활성 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = false " +
           "ORDER BY bc.categoryName ASC")
    List<BoardCategory> findInactiveCategories();
    
    /**
     * 빈 카테고리 조회 (게시글이 없는 카테고리)
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true AND bc.postCount = 0 " +
           "ORDER BY bc.createdAt ASC")
    List<BoardCategory> findEmptyCategories();
    
    // ===== 통계 분석 =====
    
    /**
     * 카테고리 타입별 통계
     */
    @Query("SELECT bc.categoryType, COUNT(bc) as categoryCount, SUM(bc.postCount) as totalPosts " +
           "FROM BoardCategory bc " +
           "WHERE bc.isActive = true " +
           "GROUP BY bc.categoryType " +
           "ORDER BY totalPosts DESC")
    List<Object[]> getCategoryTypeStatistics();
    
    /**
     * 깊이별 카테고리 분포
     */
    @Query("SELECT bc.categoryLevel, COUNT(bc) as categoryCount " +
           "FROM BoardCategory bc " +
           "WHERE bc.isActive = true " +
           "GROUP BY bc.categoryLevel " +
           "ORDER BY bc.categoryLevel ASC")
    List<Object[]> getCategoryLevelDistribution();
    
    /**
     * 전체 카테고리 통계 요약
     */
    @Query("SELECT " +
           "COUNT(bc) as totalCategories, " +
           "COUNT(CASE WHEN bc.parentCategory IS NULL THEN 1 END) as rootCategories, " +
           "SUM(bc.postCount) as totalPosts, " +
           "AVG(bc.postCount) as avgPostsPerCategory " +
           "FROM BoardCategory bc " +
           "WHERE bc.isActive = true")
    List<Object[]> getCategoryStatisticsSummary();
    
    /**
     * 최근 업데이트된 카테고리 조회
     */
    @Query("SELECT bc FROM BoardCategory bc " +
           "WHERE bc.isActive = true " +
           "AND bc.lastPostDate IS NOT NULL " +
           "ORDER BY bc.lastPostDate DESC")
    List<BoardCategory> findRecentlyUpdatedCategories(Pageable pageable);
    
    // ===== 성능 최적화 메서드 =====
    
    /**
     * 카테고리 통계 일괄 업데이트
     */
    @Query(value = "UPDATE board_categories bc " +
                   "SET post_count = (" +
                   "    SELECT COUNT(*) FROM posts p " +
                   "    WHERE p.category_id = bc.category_id " +
                   "    AND p.post_status = 'PUBLISHED'" +
                   "), " +
                   "today_post_count = (" +
                   "    SELECT COUNT(*) FROM posts p " +
                   "    WHERE p.category_id = bc.category_id " +
                   "    AND p.post_status = 'PUBLISHED' " +
                   "    AND DATE(p.created_at) = CURDATE()" +
                   ") " +
                   "WHERE bc.is_active = true", nativeQuery = true)
    void updateAllCategoryStatistics();
    
    /**
     * 특정 카테고리 통계 업데이트
     */
    @Query(value = "UPDATE board_categories " +
                   "SET post_count = (" +
                   "    SELECT COUNT(*) FROM posts " +
                   "    WHERE category_id = :categoryId " +
                   "    AND post_status = 'PUBLISHED'" +
                   "), " +
                   "today_post_count = (" +
                   "    SELECT COUNT(*) FROM posts " +
                   "    WHERE category_id = :categoryId " +
                   "    AND post_status = 'PUBLISHED' " +
                   "    AND DATE(created_at) = CURDATE()" +
                   ") " +
                   "WHERE category_id = :categoryId", nativeQuery = true)
    void updateCategoryStatistics(@Param("categoryId") Long categoryId);
    
    /**
     * 전체 활성 카테고리 수 조회
     */
    @Query("SELECT COUNT(bc) FROM BoardCategory bc WHERE bc.isActive = true")
    long countActiveCategories();
    
    /**
     * 부모 카테고리별 하위 카테고리 수 조회
     */
    @Query("SELECT bc.parentCategory.categoryId, COUNT(bc) as subCategoryCount " +
           "FROM BoardCategory bc " +
           "WHERE bc.parentCategory IS NOT NULL AND bc.isActive = true " +
           "GROUP BY bc.parentCategory.categoryId " +
           "ORDER BY subCategoryCount DESC")
    List<Object[]> countSubCategoriesByParent();
}
```

---

## 📝 2. PostRepository - 게시글 Repository

```java
package com.routepick.domain.community.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.community.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Post Repository
 * - 📝 게시글 핵심 최적화
 * - 성능 중심 목록 조회
 * - 검색 및 필터링 특화
 * - 인기/추천 게시글 관리
 */
@Repository
public interface PostRepository extends BaseRepository<Post, Long> {
    
    // ===== 기본 게시글 조회 =====
    
    /**
     * 카테고리별 게시글 조회 (최신순)
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.category.categoryId = :categoryId " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.isPinned DESC, p.createdAt DESC")
    List<Post> findByCategoryIdOrderByCreatedAtDesc(@Param("categoryId") Long categoryId);
    
    /**
     * 카테고리별 게시글 조회 (페이징)
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.category.categoryId = :categoryId " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.isPinned DESC, p.createdAt DESC")
    Page<Post> findByCategoryIdOrderByCreatedAtDesc(@Param("categoryId") Long categoryId, Pageable pageable);
    
    /**
     * 사용자별 게시글 조회
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.user.userId = :userId " +
           "AND p.isDeleted = false " +
           "ORDER BY p.createdAt DESC")
    List<Post> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    /**
     * 게시글 상태별 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = :postStatus " +
           "AND p.isDeleted = false " +
           "ORDER BY p.createdAt DESC")
    List<Post> findByPostStatus(@Param("postStatus") String postStatus);
    
    // ===== 인기 게시글 조회 =====
    
    /**
     * 인기 게시글 조회 (조회수 + 좋아요 기준)
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND p.createdAt >= :sinceDate " +
           "ORDER BY (p.viewCount * 0.3 + p.likeCount * 0.5 + p.commentCount * 0.2) DESC")
    List<Post> findPopularPosts(@Param("sinceDate") LocalDateTime sinceDate, Pageable pageable);
    
    /**
     * 주간 인기 게시글
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND p.createdAt >= :weekAgo " +
           "ORDER BY p.viewCount DESC, p.likeCount DESC")
    List<Post> findWeeklyPopularPosts(@Param("weekAgo") LocalDateTime weekAgo, Pageable pageable);
    
    /**
     * 추천 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.isFeatured = true " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.createdAt DESC")
    List<Post> findFeaturedPosts();
    
    /**
     * 고정 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.isPinned = true " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.createdAt DESC")
    List<Post> findPinnedPosts();
    
    // ===== 게시글 검색 =====
    
    /**
     * 제목 + 내용 검색
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND (p.title LIKE %:keyword% OR p.content LIKE %:keyword%) " +
           "ORDER BY p.createdAt DESC")
    List<Post> findByTitleContainingOrContentContaining(@Param("keyword") String keyword);
    
    /**
     * 제목으로만 검색
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND p.title LIKE %:keyword% " +
           "ORDER BY p.createdAt DESC")
    List<Post> findByTitleContaining(@Param("keyword") String keyword);
    
    /**
     * 작성자명으로 검색
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND p.user.nickName LIKE %:authorName% " +
           "ORDER BY p.createdAt DESC")
    List<Post> findByAuthorName(@Param("authorName") String authorName);
    
    /**
     * 태그로 검색
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND p.tags LIKE %:tag% " +
           "ORDER BY p.createdAt DESC")
    List<Post> findByTag(@Param("tag") String tag);
    
    /**
     * 복합 조건 검색
     */
    @Query("SELECT p FROM Post p " +
           "WHERE p.isDeleted = false " +
           "AND (:categoryId IS NULL OR p.category.categoryId = :categoryId) " +
           "AND (:postStatus IS NULL OR p.postStatus = :postStatus) " +
           "AND (:postType IS NULL OR p.postType = :postType) " +
           "AND (:userId IS NULL OR p.user.userId = :userId) " +
           "AND (:keyword IS NULL OR p.title LIKE %:keyword% OR p.content LIKE %:keyword%) " +
           "AND (:startDate IS NULL OR p.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR p.createdAt <= :endDate) " +
           "ORDER BY p.isPinned DESC, p.createdAt DESC")
    Page<Post> findByComplexConditions(@Param("categoryId") Long categoryId,
                                      @Param("postStatus") String postStatus,
                                      @Param("postType") String postType,
                                      @Param("userId") Long userId,
                                      @Param("keyword") String keyword,
                                      @Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate,
                                      Pageable pageable);
    
    // ===== 게시글 타입별 조회 =====
    
    /**
     * 공지사항 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postType = 'NOTICE' " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.isPinned DESC, p.createdAt DESC")
    List<Post> findNotices();
    
    /**
     * 이벤트 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postType = 'EVENT' " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND (p.expiresAt IS NULL OR p.expiresAt > :now) " +
           "ORDER BY p.createdAt DESC")
    List<Post> findActiveEvents(@Param("now") LocalDateTime now);
    
    /**
     * 질문답변 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postType = 'QNA' " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.createdAt DESC")
    List<Post> findQnaPosts();
    
    /**
     * 후기 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postType = 'REVIEW' " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.likeCount DESC, p.createdAt DESC")
    List<Post> findReviewPosts();
    
    // ===== 통계 및 분석 =====
    
    /**
     * 일별 게시글 수 통계
     */
    @Query("SELECT DATE(p.createdAt) as postDate, COUNT(p) as postCount " +
           "FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND p.createdAt >= :startDate " +
           "GROUP BY DATE(p.createdAt) " +
           "ORDER BY postDate DESC")
    List<Object[]> getDailyPostStatistics(@Param("startDate") LocalDateTime startDate);
    
    /**
     * 카테고리별 게시글 수 통계
     */
    @Query("SELECT p.category.categoryName, COUNT(p) as postCount " +
           "FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "GROUP BY p.category.categoryId, p.category.categoryName " +
           "ORDER BY postCount DESC")
    List<Object[]> getPostCountByCategory();
    
    /**
     * 사용자별 게시글 통계
     */
    @Query("SELECT p.user.userId, p.user.nickName, COUNT(p) as postCount, SUM(p.viewCount) as totalViews " +
           "FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "GROUP BY p.user.userId, p.user.nickName " +
           "ORDER BY postCount DESC")
    List<Object[]> getUserPostStatistics();
    
    /**
     * 최다 조회 게시글 TOP 10
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.viewCount DESC")
    List<Post> findMostViewedPosts(Pageable pageable);
    
    /**
     * 최다 좋아요 게시글 TOP 10
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.likeCount DESC")
    List<Post> findMostLikedPosts(Pageable pageable);
    
    // ===== 관련 게시글 추천 =====
    
    /**
     * 유사한 게시글 조회 (카테고리 + 태그 기반)
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postId != :postId " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND (p.category.categoryId = :categoryId " +
           "     OR p.tags LIKE %:tags%) " +
           "ORDER BY p.viewCount DESC, p.createdAt DESC")
    List<Post> findSimilarPosts(@Param("postId") Long postId,
                               @Param("categoryId") Long categoryId,
                               @Param("tags") String tags,
                               Pageable pageable);
    
    /**
     * 같은 작성자의 다른 게시글
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.user.userId = :userId " +
           "AND p.postId != :excludePostId " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.createdAt DESC")
    List<Post> findOtherPostsByUser(@Param("userId") Long userId,
                                   @Param("excludePostId") Long excludePostId,
                                   Pageable pageable);
    
    // ===== 슬러그 및 URL 관리 =====
    
    /**
     * 슬러그로 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.slug = :slug " +
           "AND p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false")
    Optional<Post> findBySlug(@Param("slug") String slug);
    
    /**
     * 슬러그 중복 확인
     */
    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.slug = :slug")
    boolean existsBySlug(@Param("slug") String slug);
    
    // ===== 관리자 기능 =====
    
    /**
     * 승인 대기 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PENDING' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.createdAt ASC")
    List<Post> findPendingPosts();
    
    /**
     * 신고된 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.reportCount >= :minReportCount " +
           "AND p.isDeleted = false " +
           "ORDER BY p.reportCount DESC, p.createdAt DESC")
    List<Post> findReportedPosts(@Param("minReportCount") Integer minReportCount);
    
    /**
     * 숨겨진 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'HIDDEN' " +
           "AND p.isDeleted = false " +
           "ORDER BY p.createdAt DESC")
    List<Post> findHiddenPosts();
    
    /**
     * 예약 발행 게시글 조회
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p " +
           "WHERE p.postStatus = 'PENDING' " +
           "AND p.publishedAt IS NOT NULL " +
           "AND p.publishedAt <= :now " +
           "AND p.isDeleted = false")
    List<Post> findScheduledPostsToPublish(@Param("now") LocalDateTime now);
    
    // ===== 성능 최적화 =====
    
    /**
     * 조회수 업데이트 (벌크 연산)
     */
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.postId = :postId")
    void incrementViewCount(@Param("postId") Long postId);
    
    /**
     * 게시글 통계 일괄 업데이트
     */
    @Query(value = "UPDATE posts p " +
                   "SET like_count = (" +
                   "    SELECT COUNT(*) FROM post_likes pl " +
                   "    WHERE pl.post_id = p.post_id AND pl.is_active = true AND pl.like_type = 'LIKE'" +
                   "), " +
                   "comment_count = (" +
                   "    SELECT COUNT(*) FROM comments c " +
                   "    WHERE c.post_id = p.post_id AND c.is_deleted = false" +
                   "), " +
                   "bookmark_count = (" +
                   "    SELECT COUNT(*) FROM post_bookmarks pb " +
                   "    WHERE pb.post_id = p.post_id" +
                   ")", nativeQuery = true)
    void updateAllPostStatistics();
    
    /**
     * 전체 게시글 수 조회
     */
    @Query("SELECT COUNT(p) FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' AND p.isDeleted = false")
    long countPublishedPosts();
    
    /**
     * 오늘 작성된 게시글 수
     */
    @Query("SELECT COUNT(p) FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND DATE(p.createdAt) = :today")
    long countTodayPosts(@Param("today") LocalDate today);
    
    /**
     * 기간별 게시글 수
     */
    @Query("SELECT COUNT(p) FROM Post p " +
           "WHERE p.postStatus = 'PUBLISHED' " +
           "AND p.isDeleted = false " +
           "AND p.createdAt BETWEEN :startDate AND :endDate")
    long countPostsBetweenDates(@Param("startDate") LocalDateTime startDate,
                               @Param("endDate") LocalDateTime endDate);
}
```

---

## 💬 3. CommentRepository - 댓글 Repository

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

## ⚡ 4. 성능 최적화 전략

### 게시글 목록 조회 최적화
```sql
-- 카테고리별 게시글 목록 조회 최적화
CREATE INDEX idx_post_category_status_pin_date 
ON posts(category_id, post_status, is_pinned DESC, created_at DESC);

-- 인기 게시글 조회 최적화
CREATE INDEX idx_post_popularity_score 
ON posts(post_status, 
         (view_count * 0.3 + like_count * 0.5 + comment_count * 0.2) DESC);

-- 검색 최적화 (Full-Text Index)
CREATE FULLTEXT INDEX idx_post_fulltext_search 
ON posts(title, content);
```

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

### 카테고리 관리 최적화
```sql
-- 계층형 카테고리 조회 최적화
CREATE INDEX idx_category_hierarchy 
ON board_categories(parent_category_id, category_level, display_order);

-- 카테고리 권한 및 상태 최적화
CREATE INDEX idx_category_permission_status 
ON board_categories(is_active, is_public, read_permission, write_permission);
```

---

## ✅ 설계 완료 체크리스트

### 커뮤니티 핵심 Repository (3개)
- [x] **BoardCategoryRepository** - 계층형 카테고리 구조 및 권한 관리
- [x] **PostRepository** - 게시글 핵심 최적화 및 성능 중심 조회
- [x] **CommentRepository** - 계층형 댓글 구조 및 실시간 상호작용

### 핵심 기능 구현
- [x] 계층형 카테고리 시스템 (부모-자식 관계, 권한 기반 접근)
- [x] 게시글 성능 최적화 (인기도 알고리즘, 복합 인덱스)
- [x] 계층형 댓글 시스템 (무제한 깊이, 베스트 댓글)
- [x] 검색 및 필터링 (Full-Text 검색, 복합 조건)

### 성능 최적화
- [x] 게시글 목록 조회 복합 인덱스
- [x] 계층형 댓글 트리 로딩 최적화
- [x] @EntityGraph N+1 문제 해결
- [x] 벌크 연산을 통한 통계 업데이트

### 사용자 경험
- [x] 카테고리별 권한 관리 (읽기/쓰기/댓글 권한)
- [x] 인기 게시글 및 추천 시스템
- [x] 베스트 댓글 및 고정 댓글 지원
- [x] 실시간 통계 및 활동 추적

### 관리자 기능
- [x] 게시글 승인 및 숨김 처리
- [x] 신고된 댓글 관리
- [x] 카테고리 통계 일괄 업데이트
- [x] 예약 발행 게시글 지원

---

**다음 단계**: Step 5-4b 커뮤니티 부가 Repository 설계 (좋아요, 북마크, 미디어)  
**완료일**: 2025-08-20  
**핵심 성과**: 3개 커뮤니티 핵심 Repository + 계층형 구조 + 성능 최적화 완료