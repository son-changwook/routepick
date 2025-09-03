# Step 4-4a2: 커뮤니티 상호작용 엔티티 설계

> **RoutePickr 커뮤니티 상호작용 시스템** - 좋아요, 북마크, 댓글, 댓글 좋아요  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-4a2 (JPA 엔티티 50개 - 커뮤니티 상호작용 4개)  
> **분할**: step4-4a_community_entities.md → 커뮤니티 상호작용 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 커뮤니티 상호작용 시스템**을 담고 있습니다.

### 🎯 주요 특징
- **다양한 반응**: 좋아요, 사랑, 웃음, 화남 등 다양한 감정 표현
- **개인 북마크**: 폴더별 분류, 개인 메모, 우선순위 관리
- **계층형 댓글**: 무제한 깊이, 베스트 댓글, 고정 댓글
- **상호작용 최적화**: 중복 방지, 통계 집계, 성능 인덱스

### 📊 엔티티 목록 (4개)
1. **PostLike** - 게시글 좋아요 (다양한 반응 타입)
2. **PostBookmark** - 게시글 북마크 (폴더 분류)
3. **Comment** - 댓글 (계층형 구조)
4. **CommentLike** - 댓글 좋아요 (중복 방지)

---

## 👍 1. PostLike 엔티티 - 게시글 좋아요

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * 게시글 좋아요
 * - 사용자별 게시글 좋아요/싫어요
 * - 중복 방지 및 취소 지원
 */
@Entity
@Table(name = "post_likes", indexes = {
    @Index(name = "idx_like_post_user", columnList = "post_id, user_id", unique = true),
    @Index(name = "idx_like_user", columnList = "user_id"),
    @Index(name = "idx_like_post", columnList = "post_id"),
    @Index(name = "idx_like_type", columnList = "like_type"),
    @Index(name = "idx_like_date", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PostLike extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long likeId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "like_type", length = 20)
    private String likeType = "LIKE"; // LIKE, DISLIKE, LOVE, LAUGH, ANGRY
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태 (좋아요 취소 지원)
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // 클라이언트 IP
    
    @Column(name = "user_agent", length = 500)
    private String userAgent; // User Agent
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 좋아요 타입 한글 표시
     */
    @Transient
    public String getLikeTypeKorean() {
        if (likeType == null) return "좋아요";
        
        return switch (likeType) {
            case "LIKE" -> "좋아요";
            case "DISLIKE" -> "싫어요";
            case "LOVE" -> "사랑해요";
            case "LAUGH" -> "웃겨요";
            case "ANGRY" -> "화나요";
            default -> "좋아요";
        };
    }
    
    /**
     * 좋아요 이모지
     */
    @Transient
    public String getLikeEmoji() {
        if (likeType == null) return "👍";
        
        return switch (likeType) {
            case "LIKE" -> "👍";
            case "DISLIKE" -> "👎";
            case "LOVE" -> "❤️";
            case "LAUGH" -> "😂";
            case "ANGRY" -> "😡";
            default -> "👍";
        };
    }
    
    /**
     * 좋아요 취소
     */
    public void cancel() {
        this.isActive = false;
    }
    
    /**
     * 좋아요 복원
     */
    public void restore() {
        this.isActive = true;
    }
    
    /**
     * 좋아요 타입 변경
     */
    public void changeLikeType(String newType) {
        this.likeType = newType;
        this.isActive = true;
    }
    
    @Override
    public Long getId() {
        return likeId;
    }
}
```

---

## 📌 2. PostBookmark 엔티티 - 게시글 북마크

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 게시글 북마크
 * - 사용자별 게시글 저장
 * - 폴더별 분류 및 개인 메모
 */
@Entity
@Table(name = "post_bookmarks", indexes = {
    @Index(name = "idx_bookmark_user_post", columnList = "user_id, post_id", unique = true),
    @Index(name = "idx_bookmark_user", columnList = "user_id"),
    @Index(name = "idx_bookmark_post", columnList = "post_id"),
    @Index(name = "idx_bookmark_folder", columnList = "user_id, folder_name"),
    @Index(name = "idx_bookmark_date", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PostBookmark extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bookmark_id")
    private Long bookmarkId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Size(max = 50, message = "폴더명은 최대 50자입니다")
    @Column(name = "folder_name", length = 50)
    private String folderName = "기본 폴더"; // 북마크 폴더명
    
    @Size(max = 500, message = "개인 메모는 최대 500자입니다")
    @Column(name = "personal_memo", columnDefinition = "TEXT")
    private String personalMemo; // 개인 메모
    
    @Column(name = "personal_tags", length = 200)
    private String personalTags; // 개인 태그 (쉼표 구분)
    
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false; // 공개 북마크 여부
    
    @Column(name = "priority_level")
    private Integer priorityLevel = 3; // 우선순위 (1: 높음, 5: 낮음)
    
    @Column(name = "read_later", nullable = false)
    private boolean readLater = false; // 나중에 읽기
    
    @Column(name = "is_favorite", nullable = false)
    private boolean isFavorite = false; // 즐겨찾기
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 개인 태그 목록 반환
     */
    @Transient
    public java.util.List<String> getPersonalTagList() {
        if (personalTags == null || personalTags.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return java.util.Arrays.asList(personalTags.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 우선순위 한글 표시
     */
    @Transient
    public String getPriorityLevelKorean() {
        if (priorityLevel == null) return "보통";
        
        return switch (priorityLevel) {
            case 1 -> "매우 높음";
            case 2 -> "높음";
            case 3 -> "보통";
            case 4 -> "낮음";
            case 5 -> "매우 낮음";
            default -> "보통";
        };
    }
    
    /**
     * 폴더 이동
     */
    public void moveToFolder(String newFolderName) {
        this.folderName = newFolderName;
    }
    
    /**
     * 메모 업데이트
     */
    public void updateMemo(String memo) {
        this.personalMemo = memo;
    }
    
    /**
     * 태그 업데이트
     */
    public void updateTags(java.util.List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            this.personalTags = null;
            return;
        }
        this.personalTags = String.join(",", tags);
    }
    
    /**
     * 즐겨찾기 토글
     */
    public void toggleFavorite() {
        this.isFavorite = !isFavorite;
    }
    
    @Override
    public Long getId() {
        return bookmarkId;
    }
}
```

---

## 💬 3. Comment 엔티티 - 댓글 (계층형)

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.SoftDeleteEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 댓글 (계층형 구조)
 * - 게시글 댓글 및 대댓글
 * - 부모-자식 관계로 무제한 깊이 지원
 * - 소프트 삭제 적용
 */
@Entity
@Table(name = "comments", indexes = {
    @Index(name = "idx_comment_post_parent", columnList = "post_id, parent_id, created_at DESC"),
    @Index(name = "idx_comment_user", columnList = "user_id"),
    @Index(name = "idx_comment_parent", columnList = "parent_id"),
    @Index(name = "idx_comment_active", columnList = "is_deleted, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Comment extends SoftDeleteEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent; // 부모 댓글
    
    @NotNull
    @Size(min = 1, max = 1000, message = "댓글은 1-1000자 사이여야 합니다")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content; // 댓글 내용
    
    @Column(name = "content_type", length = 20)
    private String contentType = "PLAIN"; // PLAIN, MARKDOWN, HTML
    
    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous = false; // 익명 댓글
    
    @Column(name = "is_private", nullable = false)
    private boolean isPrivate = false; // 비밀 댓글 (작성자와 게시글 작성자만 볼 수 있음)
    
    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false; // 고정 댓글
    
    @Column(name = "is_author_comment", nullable = false)
    private boolean isAuthorComment = false; // 게시글 작성자 댓글
    
    @Column(name = "is_best_comment", nullable = false)
    private boolean isBestComment = false; // 베스트 댓글
    
    // ===== 통계 정보 =====
    
    @Column(name = "like_count")
    private Integer likeCount = 0; // 좋아요 수
    
    @Column(name = "dislike_count")
    private Integer dislikeCount = 0; // 싫어요 수
    
    @Column(name = "reply_count")
    private Integer replyCount = 0; // 답글 수
    
    @Column(name = "report_count")
    private Integer reportCount = 0; // 신고 수
    
    // ===== 편집 정보 =====
    
    @Column(name = "edit_count")
    private Integer editCount = 0; // 수정 횟수
    
    @Column(name = "last_edited_at")
    private java.time.LocalDateTime lastEditedAt; // 마지막 수정일
    
    @Column(name = "edit_reason", length = 200)
    private String editReason; // 수정 사유
    
    // ===== IP 및 디바이스 정보 =====
    
    @Column(name = "author_ip", length = 45)
    private String authorIp; // 작성자 IP
    
    @Column(name = "user_agent", length = 500)
    private String userAgent; // User-Agent
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Comment> children = new ArrayList<>(); // 자식 댓글들
    
    @OneToMany(mappedBy = "comment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CommentLike> commentLikes = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 최상위 댓글인지 확인
     */
    @Transient
    public boolean isRootComment() {
        return parent == null;
    }
    
    /**
     * 댓글 깊이 계산
     */
    @Transient
    public int getDepth() {
        if (parent == null) return 0;
        return parent.getDepth() + 1;
    }
    
    /**
     * 표시용 작성자명
     */
    @Transient
    public String getDisplayAuthorName() {
        if (isAnonymous) return "익명";
        return user.getNickName();
    }
    
    /**
     * 좋아요 수 증가/감소
     */
    public void increaseLikeCount() {
        this.likeCount = (likeCount == null ? 0 : likeCount) + 1;
    }
    
    public void decreaseLikeCount() {
        this.likeCount = Math.max(0, (likeCount == null ? 0 : likeCount) - 1);
    }
    
    /**
     * 답글 수 업데이트
     */
    public void updateReplyCount() {
        this.replyCount = children.size();
        
        // 부모 댓글의 답글 수도 업데이트
        if (parent != null) {
            parent.updateReplyCount();
        }
    }
    
    /**
     * 댓글 수정
     */
    public void updateContent(String newContent, String reason) {
        this.content = newContent;
        this.editCount = (editCount == null ? 0 : editCount) + 1;
        this.lastEditedAt = java.time.LocalDateTime.now();
        this.editReason = reason;
    }
    
    /**
     * 댓글 고정/해제
     */
    public void pin() {
        this.isPinned = true;
    }
    
    public void unpin() {
        this.isPinned = false;
    }
    
    /**
     * 베스트 댓글 설정/해제
     */
    public void setBestComment() {
        this.isBestComment = true;
    }
    
    public void unsetBestComment() {
        this.isBestComment = false;
    }
    
    /**
     * 작성자 댓글 표시
     */
    public void markAsAuthorComment() {
        this.isAuthorComment = true;
    }
    
    /**
     * 신고 수 증가
     */
    public void increaseReportCount() {
        this.reportCount = (reportCount == null ? 0 : reportCount) + 1;
    }
    
    /**
     * 모든 하위 댓글 조회 (재귀)
     */
    @Transient
    public List<Comment> getAllDescendants() {
        List<Comment> descendants = new ArrayList<>();
        for (Comment child : children) {
            descendants.add(child);
            descendants.addAll(child.getAllDescendants());
        }
        return descendants;
    }
    
    @Override
    public Long getId() {
        return commentId;
    }
}
```

---

## ⚡ 4. CommentLike 엔티티 - 댓글 좋아요

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * 댓글 좋아요
 * - 댓글별 좋아요/싫어요
 * - 중복 방지 및 취소 지원
 */
@Entity
@Table(name = "comment_likes", indexes = {
    @Index(name = "idx_comment_like_user", columnList = "comment_id, user_id", unique = true),
    @Index(name = "idx_comment_like_comment", columnList = "comment_id"),
    @Index(name = "idx_comment_like_user_only", columnList = "user_id"),
    @Index(name = "idx_comment_like_type", columnList = "like_type"),
    @Index(name = "idx_comment_like_date", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CommentLike extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_like_id")
    private Long commentLikeId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "like_type", length = 20)
    private String likeType = "LIKE"; // LIKE, DISLIKE
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태 (좋아요 취소 지원)
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // 클라이언트 IP
    
    @Column(name = "user_agent", length = 500)
    private String userAgent; // User Agent
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 좋아요 타입 한글 표시
     */
    @Transient
    public String getLikeTypeKorean() {
        if (likeType == null) return "좋아요";
        
        return switch (likeType) {
            case "LIKE" -> "좋아요";
            case "DISLIKE" -> "싫어요";
            default -> "좋아요";
        };
    }
    
    /**
     * 좋아요 이모지
     */
    @Transient
    public String getLikeEmoji() {
        if (likeType == null) return "👍";
        
        return switch (likeType) {
            case "LIKE" -> "👍";
            case "DISLIKE" -> "👎";
            default -> "👍";
        };
    }
    
    /**
     * 좋아요 취소
     */
    public void cancel() {
        this.isActive = false;
    }
    
    /**
     * 좋아요 복원
     */
    public void restore() {
        this.isActive = true;
    }
    
    /**
     * 좋아요 타입 변경
     */
    public void changeLikeType(String newType) {
        this.likeType = newType;
        this.isActive = true;
    }
    
    /**
     * 좋아요 여부 확인
     */
    @Transient
    public boolean isLike() {
        return "LIKE".equals(likeType) && isActive;
    }
    
    /**
     * 싫어요 여부 확인
     */
    @Transient
    public boolean isDislike() {
        return "DISLIKE".equals(likeType) && isActive;
    }
    
    @Override
    public Long getId() {
        return commentLikeId;
    }
}
```

---

## ⚡ 5. 성능 최적화 전략

### 복합 인덱스 DDL 추가
```sql
-- 게시글 목록 조회 최적화
CREATE INDEX idx_post_category_status_date 
ON posts(category_id, post_status, is_pinned DESC, created_at DESC);

-- 인기 게시글 조회
CREATE INDEX idx_post_popularity_complex 
ON posts(post_status, like_count DESC, view_count DESC, comment_count DESC);

-- 사용자별 북마크 정렬
CREATE INDEX idx_bookmark_user_folder_date 
ON post_bookmarks(user_id, folder_name, created_at DESC);

-- 댓글 계층 구조 최적화
CREATE INDEX idx_comment_thread_optimization 
ON comments(post_id, parent_id, is_deleted, is_pinned DESC, like_count DESC);

-- 중복 방지 및 성능 최적화
CREATE INDEX idx_like_post_type_date 
ON post_likes(post_id, like_type, is_active, created_at DESC);

-- 댓글 좋아요 통계 최적화
CREATE INDEX idx_comment_like_stats 
ON comment_likes(comment_id, like_type, is_active);
```

### N+1 문제 해결 쿼리 예시
```java
// Repository에서 Fetch Join 활용
@Query("SELECT p FROM Post p " +
       "LEFT JOIN FETCH p.user u " +
       "LEFT JOIN FETCH p.category c " +
       "LEFT JOIN FETCH p.postImages pi " +
       "WHERE p.postStatus = 'PUBLISHED' " +
       "AND p.category.isActive = true " +
       "ORDER BY p.isPinned DESC, p.createdAt DESC")
List<Post> findActivePostsWithDetails();

// 댓글 계층 구조 조회 최적화
@Query("SELECT c FROM Comment c " +
       "LEFT JOIN FETCH c.user u " +
       "LEFT JOIN FETCH c.children " +
       "WHERE c.post.id = :postId " +
       "AND c.parent IS NULL " +
       "AND c.isDeleted = false " +
       "ORDER BY c.isPinned DESC, c.likeCount DESC, c.createdAt ASC")
List<Comment> findRootCommentsByPost(@Param("postId") Long postId);

// 좋아요 통계 조회
@Query("SELECT pl.likeType, COUNT(pl) " +
       "FROM PostLike pl " +
       "WHERE pl.post.id = :postId AND pl.isActive = true " +
       "GROUP BY pl.likeType")
List<Object[]> getLikeStatistics(@Param("postId") Long postId);

// 사용자별 북마크 폴더 조회
@Query("SELECT pb.folderName, COUNT(pb) " +
       "FROM PostBookmark pb " +
       "WHERE pb.user.id = :userId " +
       "GROUP BY pb.folderName " +
       "ORDER BY COUNT(pb) DESC")
List<Object[]> getUserBookmarkFolders(@Param("userId") Long userId);
```

### 통계 정보 캐시 전략
```java
// Redis 캐시를 활용한 통계 정보 관리
@Cacheable(value = "postStats", key = "#postId")
public PostStatistics getPostStatistics(Long postId) {
    return PostStatistics.builder()
        .postId(postId)
        .totalLikes(likeRepository.countByPostIdAndIsActive(postId, true))
        .totalComments(commentRepository.countByPostIdAndIsDeleted(postId, false))
        .totalBookmarks(bookmarkRepository.countByPostId(postId))
        .build();
}

// 배치 작업으로 통계 정보 업데이트
@Scheduled(fixedRate = 300000) // 5분마다
public void updatePostStatistics() {
    List<Long> activePostIds = postRepository.findActivePostIds();
    for (Long postId : activePostIds) {
        updatePostStats(postId);
    }
}

// 댓글 좋아요 통계 업데이트
@Transactional
public void updateCommentLikeStats(Long commentId) {
    Comment comment = commentRepository.findById(commentId).orElseThrow();
    
    int likeCount = commentLikeRepository.countByCommentIdAndLikeTypeAndIsActive(
        commentId, "LIKE", true);
    int dislikeCount = commentLikeRepository.countByCommentIdAndLikeTypeAndIsActive(
        commentId, "DISLIKE", true);
        
    comment.setLikeCount(likeCount);
    comment.setDislikeCount(dislikeCount);
    
    commentRepository.save(comment);
}
```

---

## ✅ 설계 완료 체크리스트

### 커뮤니티 상호작용 엔티티 (4개)
- [x] **PostLike** - 게시글 좋아요 (다양한 반응 타입, 중복 방지, 취소 지원)
- [x] **PostBookmark** - 게시글 북마크 (폴더 분류, 개인 메모, 우선순위)
- [x] **Comment** - 댓글 (계층형 구조, 베스트/고정 댓글, 소프트 삭제)
- [x] **CommentLike** - 댓글 좋아요 (좋아요/싫어요, 중복 방지)

### 다양한 반응 시스템
- [x] 5가지 좋아요 타입 (좋아요, 사랑, 웃음, 화남, 싫어요)
- [x] 이모지 자동 매핑 (👍, ❤️, 😂, 😡, 👎)
- [x] 좋아요 취소 및 타입 변경 지원
- [x] 중복 방지 UNIQUE 인덱스

### 개인 북마크 시스템
- [x] 폴더별 북마크 분류 관리
- [x] 개인 메모 및 태그 시스템
- [x] 우선순위 설정 (1-5단계)
- [x] 즐겨찾기 및 나중에 읽기 기능

### 계층형 댓글 시스템
- [x] 부모-자식 관계로 무제한 깊이 지원
- [x] 베스트 댓글 및 고정 댓글 기능
- [x] 익명/비밀 댓글 지원
- [x] 작성자 댓글 구분 표시

### 성능 최적화
- [x] 좋아요/북마크 중복 방지 UNIQUE 인덱스
- [x] 댓글 계층 구조 조회 최적화
- [x] 통계 정보 캐시 전략
- [x] N+1 문제 해결 Fetch Join

### 사용자 경험 향상
- [x] 반응 타입별 이모지 표시
- [x] 우선순위별 북마크 정렬
- [x] 댓글 깊이 시각화 지원
- [x] 실시간 통계 업데이트

---

**다음 단계**: step4-3c_climbing_activity_entities.md (클라이밍 활동 엔티티 세분화)  
**완료일**: 2025-08-20  
**핵심 성과**: 4개 커뮤니티 상호작용 엔티티 + 다양한 반응 시스템 + 계층형 댓글 + 성능 최적화 완성