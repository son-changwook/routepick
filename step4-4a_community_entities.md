# Step 4-4a: 커뮤니티 엔티티 설계

> 게시판, 게시글, 미디어, 태깅, 좋아요, 북마크, 댓글 엔티티 완전 설계  
> 생성일: 2025-08-19  
> 기반: step4-3c_climbing_activity_entities.md, step4-1_base_user_entities.md

---

## 🎯 설계 목표

- **체계적 게시판**: 카테고리별 분류, 권한 관리, 표시 순서
- **풍부한 콘텐츠**: 텍스트, 이미지, 동영상, 루트 태깅 지원
- **활발한 소셜**: 좋아요, 북마크, 계층형 댓글, 알림
- **성능 최적화**: 게시글 목록, 중복 방지, 미디어 관리

---

## 📂 1. BoardCategory 엔티티 - 게시판 카테고리

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 게시판 카테고리
 * - 자유게시판, 질문게시판, 후기게시판 등
 * - 계층형 카테고리 지원
 * - 권한별 접근 제어
 */
@Entity
@Table(name = "board_categories", indexes = {
    @Index(name = "idx_category_order", columnList = "display_order, is_active"),
    @Index(name = "idx_category_parent", columnList = "parent_category_id"),
    @Index(name = "idx_category_type", columnList = "category_type"),
    @Index(name = "idx_category_name", columnList = "category_name"),
    @Index(name = "idx_category_level", columnList = "category_level")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoardCategory extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private BoardCategory parentCategory; // 상위 카테고리
    
    @NotNull
    @Size(min = 2, max = 50, message = "카테고리명은 2-50자 사이여야 합니다")
    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName; // 카테고리명
    
    @Column(name = "category_code", length = 30)
    private String categoryCode; // 카테고리 코드 (GENERAL, QNA, REVIEW, ROUTE_INFO)
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 카테고리 설명
    
    @Column(name = "category_type", length = 30)
    private String categoryType; // PUBLIC, PRIVATE, ADMIN_ONLY
    
    @NotNull
    @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다")
    @Max(value = 999, message = "표시 순서는 999 이하여야 합니다")
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1; // 표시 순서
    
    @Column(name = "category_level")
    private Integer categoryLevel = 1; // 카테고리 깊이 (1: 최상위)
    
    @Column(name = "icon_url", length = 200)
    private String iconUrl; // 카테고리 아이콘
    
    @Column(name = "banner_image_url", length = 500)
    private String bannerImageUrl; // 배너 이미지
    
    @Column(name = "color_code", length = 7)
    private String colorCode; // 테마 색상 (#FFFFFF)
    
    // ===== 권한 설정 =====
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true; // 공개 여부
    
    @Column(name = "read_permission", length = 20)
    private String readPermission = "ALL"; // ALL, USER, ADMIN
    
    @Column(name = "write_permission", length = 20)
    private String writePermission = "USER"; // ALL, USER, ADMIN
    
    @Column(name = "comment_permission", length = 20)
    private String commentPermission = "USER"; // ALL, USER, ADMIN
    
    @Column(name = "require_approval", nullable = false)
    private boolean requireApproval = false; // 승인 필요 여부
    
    // ===== 게시판 설정 =====
    
    @Column(name = "allow_anonymous", nullable = false)
    private boolean allowAnonymous = false; // 익명 게시 허용
    
    @Column(name = "allow_file_upload", nullable = false)
    private boolean allowFileUpload = true; // 파일 업로드 허용
    
    @Column(name = "max_file_size_mb")
    private Integer maxFileSizeMb = 10; // 최대 파일 크기 (MB)
    
    @Column(name = "allow_video_upload", nullable = false)
    private boolean allowVideoUpload = true; // 동영상 업로드 허용
    
    @Column(name = "allow_route_tagging", nullable = false)
    private boolean allowRouteTagging = true; // 루트 태깅 허용
    
    // ===== 통계 정보 =====
    
    @Column(name = "post_count")
    private Integer postCount = 0; // 게시글 수
    
    @Column(name = "today_post_count")
    private Integer todayPostCount = 0; // 오늘 게시글 수
    
    @Column(name = "total_view_count")
    private Long totalViewCount = 0L; // 총 조회 수
    
    @Column(name = "last_post_date")
    private java.time.LocalDateTime lastPostDate; // 최근 게시일
    
    @Column(name = "last_post_user_id")
    private Long lastPostUserId; // 최근 게시자 ID
    
    @Column(name = "last_post_title", length = 100)
    private String lastPostTitle; // 최근 게시글 제목
    
    // ===== SEO 정보 =====
    
    @Column(name = "meta_title", length = 100)
    private String metaTitle; // SEO 제목
    
    @Column(name = "meta_description", length = 200)
    private String metaDescription; // SEO 설명
    
    @Column(name = "meta_keywords", length = 200)
    private String metaKeywords; // SEO 키워드
    
    @Column(name = "slug", length = 100)
    private String slug; // URL 슬러그
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "parentCategory", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BoardCategory> subCategories = new ArrayList<>(); // 하위 카테고리
    
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<Post> posts = new ArrayList<>(); // 게시글들
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 최상위 카테고리인지 확인
     */
    @Transient
    public boolean isRootCategory() {
        return parentCategory == null;
    }
    
    /**
     * 카테고리 깊이 계산
     */
    @Transient
    public int calculateLevel() {
        if (parentCategory == null) return 1;
        return parentCategory.calculateLevel() + 1;
    }
    
    /**
     * 권한 확인
     */
    public boolean canRead(String userRole) {
        if (!isActive) return false;
        
        return switch (readPermission) {
            case "ALL" -> true;
            case "USER" -> !"GUEST".equals(userRole);
            case "ADMIN" -> "ADMIN".equals(userRole);
            default -> true;
        };
    }
    
    public boolean canWrite(String userRole) {
        if (!isActive || !isPublic) return false;
        
        return switch (writePermission) {
            case "ALL" -> true;
            case "USER" -> !"GUEST".equals(userRole);
            case "ADMIN" -> "ADMIN".equals(userRole);
            default -> !"GUEST".equals(userRole);
        };
    }
    
    public boolean canComment(String userRole) {
        if (!isActive) return false;
        
        return switch (commentPermission) {
            case "ALL" -> true;
            case "USER" -> !"GUEST".equals(userRole);
            case "ADMIN" -> "ADMIN".equals(userRole);
            default -> !"GUEST".equals(userRole);
        };
    }
    
    /**
     * 게시글 수 업데이트
     */
    public void updatePostCount() {
        this.postCount = posts.size();
        this.todayPostCount = (int) posts.stream()
            .filter(post -> post.getCreatedAt().toLocalDate()
                .equals(java.time.LocalDate.now()))
            .count();
    }
    
    /**
     * 최근 게시글 정보 업데이트
     */
    public void updateLastPost(Long userId, String title) {
        this.lastPostDate = java.time.LocalDateTime.now();
        this.lastPostUserId = userId;
        this.lastPostTitle = title;
    }
    
    /**
     * 카테고리 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        // 하위 카테고리도 비활성화
        subCategories.forEach(BoardCategory::deactivate);
    }
    
    /**
     * 전체 경로 조회 (부모 → 자식)
     */
    @Transient
    public String getFullPath() {
        if (parentCategory == null) {
            return categoryName;
        }
        return parentCategory.getFullPath() + " > " + categoryName;
    }
    
    @Override
    public Long getId() {
        return categoryId;
    }
}
```

---

## 📝 2. Post 엔티티 - 게시글

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.SoftDeleteEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 게시글
 * - 제목, 내용, 조회수, 좋아요 등 기본 정보
 * - 미디어 파일 및 루트 태깅 지원
 * - 소프트 삭제 적용
 */
@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_post_category_date", columnList = "category_id, created_at DESC"),
    @Index(name = "idx_post_user", columnList = "user_id"),
    @Index(name = "idx_post_status", columnList = "post_status"),
    @Index(name = "idx_post_popular", columnList = "like_count DESC, view_count DESC"),
    @Index(name = "idx_post_pinned", columnList = "is_pinned, created_at DESC"),
    @Index(name = "idx_post_featured", columnList = "is_featured, created_at DESC"),
    @Index(name = "idx_post_search", columnList = "title, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Post extends SoftDeleteEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private BoardCategory category;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    // ===== 게시글 기본 정보 =====
    
    @NotNull
    @Size(min = 2, max = 200, message = "제목은 2-200자 사이여야 합니다")
    @Column(name = "title", nullable = false, length = 200)
    private String title; // 게시글 제목
    
    @NotNull
    @Size(min = 10, message = "내용은 최소 10자 이상이어야 합니다")
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content; // 게시글 내용 (HTML/Markdown)
    
    @Column(name = "content_type", length = 20)
    private String contentType = "MARKDOWN"; // MARKDOWN, HTML, PLAIN
    
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary; // 요약 (자동 생성 또는 수동 입력)
    
    // ===== 게시글 상태 =====
    
    @Column(name = "post_status", length = 20)
    private String postStatus = "PUBLISHED"; // DRAFT, PUBLISHED, HIDDEN, PENDING
    
    @Column(name = "post_type", length = 20)
    private String postType = "NORMAL"; // NORMAL, NOTICE, EVENT, QNA, REVIEW
    
    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false; // 고정 게시글
    
    @Column(name = "is_featured", nullable = false)
    private boolean isFeatured = false; // 추천 게시글
    
    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous = false; // 익명 게시글
    
    @Column(name = "allow_comments", nullable = false)
    private boolean allowComments = true; // 댓글 허용
    
    @Column(name = "require_login_to_view", nullable = false)
    private boolean requireLoginToView = false; // 로그인 필요
    
    // ===== 통계 정보 =====
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 조회 수
    
    @Column(name = "like_count")
    private Integer likeCount = 0; // 좋아요 수
    
    @Column(name = "dislike_count")
    private Integer dislikeCount = 0; // 싫어요 수
    
    @Column(name = "comment_count")
    private Integer commentCount = 0; // 댓글 수
    
    @Column(name = "bookmark_count")
    private Integer bookmarkCount = 0; // 북마크 수
    
    @Column(name = "share_count")
    private Integer shareCount = 0; // 공유 수
    
    @Column(name = "report_count")
    private Integer reportCount = 0; // 신고 수
    
    // ===== SEO 및 메타 정보 =====
    
    @Column(name = "meta_title", length = 100)
    private String metaTitle; // SEO 제목
    
    @Column(name = "meta_description", length = 200)
    private String metaDescription; // SEO 설명
    
    @Column(name = "tags", length = 500)
    private String tags; // 해시태그 (쉼표 구분)
    
    @Column(name = "slug", length = 200)
    private String slug; // URL 슬러그
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl; // 썸네일 이미지
    
    // ===== 편집 및 버전 관리 =====
    
    @Column(name = "edit_count")
    private Integer editCount = 0; // 수정 횟수
    
    @Column(name = "last_edited_at")
    private LocalDateTime lastEditedAt; // 마지막 수정일
    
    @Column(name = "last_edited_by")
    private Long lastEditedBy; // 마지막 수정자 ID
    
    @Column(name = "edit_reason", length = 200)
    private String editReason; // 수정 사유
    
    // ===== 게시 일정 =====
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt; // 게시일 (예약 게시 지원)
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // 만료일
    
    // ===== 관리자 정보 =====
    
    @Column(name = "approved_by")
    private Long approvedBy; // 승인자 ID
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 승인일
    
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes; // 관리자 메모
    
    // ===== IP 및 디바이스 정보 =====
    
    @Column(name = "author_ip", length = 45)
    private String authorIp; // 작성자 IP (IPv6 지원)
    
    @Column(name = "user_agent", length = 500)
    private String userAgent; // User-Agent
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PostImage> postImages = new ArrayList<>();
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PostVideo> postVideos = new ArrayList<>();
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PostRouteTag> postRouteTags = new ArrayList<>();
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PostLike> postLikes = new ArrayList<>();
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PostBookmark> postBookmarks = new ArrayList<>();
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Comment> comments = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 게시글 상태 한글 표시
     */
    @Transient
    public String getPostStatusKorean() {
        if (postStatus == null) return "게시됨";
        
        return switch (postStatus) {
            case "DRAFT" -> "임시저장";
            case "PUBLISHED" -> "게시됨";
            case "HIDDEN" -> "숨김";
            case "PENDING" -> "승인대기";
            default -> "게시됨";
        };
    }
    
    /**
     * 게시글 타입 한글 표시
     */
    @Transient
    public String getPostTypeKorean() {
        if (postType == null) return "일반";
        
        return switch (postType) {
            case "NORMAL" -> "일반";
            case "NOTICE" -> "공지사항";
            case "EVENT" -> "이벤트";
            case "QNA" -> "질문답변";
            case "REVIEW" -> "후기";
            default -> "일반";
        };
    }
    
    /**
     * 해시태그 목록 반환
     */
    @Transient
    public List<String> getTagList() {
        if (tags == null || tags.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return java.util.Arrays.asList(tags.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
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
     * 댓글 수 업데이트
     */
    public void updateCommentCount() {
        this.commentCount = comments.size();
    }
    
    /**
     * 게시글 수정
     */
    public void updateContent(String title, String content, Long editorId, String reason) {
        this.title = title;
        this.content = content;
        this.editCount = (editCount == null ? 0 : editCount) + 1;
        this.lastEditedAt = LocalDateTime.now();
        this.lastEditedBy = editorId;
        this.editReason = reason;
    }
    
    /**
     * 게시글 고정/해제
     */
    public void pin() {
        this.isPinned = true;
    }
    
    public void unpin() {
        this.isPinned = false;
    }
    
    /**
     * 게시글 추천/해제
     */
    public void feature() {
        this.isFeatured = true;
    }
    
    public void unfeature() {
        this.isFeatured = false;
    }
    
    /**
     * 게시 승인
     */
    public void approve(Long approverId, String notes) {
        this.postStatus = "PUBLISHED";
        this.approvedBy = approverId;
        this.approvedAt = LocalDateTime.now();
        this.adminNotes = notes;
        
        if (publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 예약 게시
     */
    public void schedulePublication(LocalDateTime scheduledTime) {
        this.postStatus = "PENDING";
        this.publishedAt = scheduledTime;
    }
    
    /**
     * 게시글 숨김 처리
     */
    public void hide(String reason) {
        this.postStatus = "HIDDEN";
        this.adminNotes = reason;
    }
    
    /**
     * 신고 수 증가
     */
    public void increaseReportCount() {
        this.reportCount = (reportCount == null ? 0 : reportCount) + 1;
    }
    
    /**
     * 대표 이미지 URL 조회
     */
    @Transient
    public String getMainImageUrl() {
        if (thumbnailUrl != null) return thumbnailUrl;
        
        return postImages.stream()
                .filter(img -> img.getDisplayOrder() == 1)
                .findFirst()
                .map(PostImage::getImageUrl)
                .orElse(null);
    }
    
    @Override
    public Long getId() {
        return postId;
    }
}
```

---

## 🖼️ 3. PostImage 엔티티 - 게시글 이미지

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 게시글 이미지
 * - 게시글별 여러 이미지 업로드
 * - 표시 순서 및 캡션 관리
 */
@Entity
@Table(name = "post_images", indexes = {
    @Index(name = "idx_post_image_order", columnList = "post_id, display_order"),
    @Index(name = "idx_post_image_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PostImage extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    
    @NotNull
    @Size(min = 10, max = 500, message = "이미지 URL은 10-500자 사이여야 합니다")
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl; // 이미지 URL
    
    @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다")
    @Max(value = 100, message = "표시 순서는 100 이하여야 합니다")
    @Column(name = "display_order")
    private Integer displayOrder = 1; // 표시 순서
    
    @Size(max = 200, message = "이미지 캡션은 최대 200자입니다")
    @Column(name = "caption", length = 200)
    private String caption; // 이미지 캡션
    
    @Column(name = "alt_text", length = 200)
    private String altText; // 대체 텍스트 (접근성)
    
    @Column(name = "file_name", length = 200)
    private String fileName; // 원본 파일명
    
    @Column(name = "file_size")
    private Long fileSize; // 파일 크기 (bytes)
    
    @Column(name = "width")
    private Integer width; // 이미지 가로 크기
    
    @Column(name = "height")
    private Integer height; // 이미지 세로 크기
    
    @Column(name = "mime_type", length = 50)
    private String mimeType; // MIME 타입
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "is_thumbnail", nullable = false)
    private boolean isThumbnail = false; // 썸네일 여부
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 썸네일로 설정
     */
    public void setAsThumbnail() {
        this.isThumbnail = true;
        this.displayOrder = 1;
    }
    
    /**
     * 파일 크기 정보 (가독성)
     */
    @Transient
    public String getFileSizeInfo() {
        if (fileSize == null) return "알 수 없음";
        
        if (fileSize < 1024) return fileSize + "B";
        else if (fileSize < 1024 * 1024) return (fileSize / 1024) + "KB";
        else return (fileSize / (1024 * 1024)) + "MB";
    }
    
    @Override
    public Long getId() {
        return imageId;
    }
}
```

---

## 🎥 4. PostVideo 엔티티 - 게시글 동영상

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 게시글 동영상
 * - 게시글별 동영상 업로드
 * - 썸네일 및 재생 정보 관리
 */
@Entity
@Table(name = "post_videos", indexes = {
    @Index(name = "idx_post_video", columnList = "post_id"),
    @Index(name = "idx_post_video_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PostVideo extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_id")
    private Long videoId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    
    @NotNull
    @Size(min = 10, max = 500, message = "동영상 URL은 10-500자 사이여야 합니다")
    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl; // 동영상 URL
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl; // 썸네일 URL
    
    @Size(max = 200, message = "동영상 제목은 최대 200자입니다")
    @Column(name = "title", length = 200)
    private String title; // 동영상 제목
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 동영상 설명
    
    @Min(value = 1, message = "재생시간은 1초 이상이어야 합니다")
    @Max(value = 7200, message = "재생시간은 7200초 이하여야 합니다")
    @Column(name = "duration")
    private Integer duration; // 재생시간 (초)
    
    @Column(name = "file_name", length = 200)
    private String fileName; // 원본 파일명
    
    @Column(name = "file_size")
    private Long fileSize; // 파일 크기 (bytes)
    
    @Column(name = "video_format", length = 20)
    private String videoFormat; // MP4, WEBM, AVI
    
    @Column(name = "video_quality", length = 20)
    private String videoQuality; // HD, FHD, 4K
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "play_count")
    private Long playCount = 0L; // 재생 횟수
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 재생시간 포맷 (mm:ss)
     */
    @Transient
    public String getFormattedDuration() {
        if (duration == null) return "00:00";
        
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    /**
     * 재생 횟수 증가
     */
    public void increasePlayCount() {
        this.playCount = (playCount == null ? 0L : playCount) + 1;
    }
    
    @Override
    public Long getId() {
        return videoId;
    }
}
```

---

## 🏷️ 5. PostRouteTag 엔티티 - 게시글 루트 태깅

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.route.entity.Route;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 게시글-루트 태깅 연결
 * - 게시글에서 특정 루트를 언급/태깅
 * - 루트 관련 후기, 팁 공유
 */
@Entity
@Table(name = "post_route_tags", indexes = {
    @Index(name = "idx_post_route_tag", columnList = "post_id, route_id", unique = true),
    @Index(name = "idx_route_posts", columnList = "route_id"),
    @Index(name = "idx_post_tags", columnList = "post_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PostRouteTag extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long tagId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @Column(name = "tag_type", length = 30)
    private String tagType; // REVIEW, TIP, QUESTION, COMPLETION, ATTEMPT
    
    @Size(max = 500, message = "태그 설명은 최대 500자입니다")
    @Column(name = "tag_description", columnDefinition = "TEXT")
    private String tagDescription; // 태그 설명
    
    @Column(name = "is_spoiler", nullable = false)
    private boolean isSpoiler = false; // 스포일러 여부 (베타 공개)
    
    @Column(name = "difficulty_rating")
    private Integer difficultyRating; // 개인적 난이도 평가 (1-5)
    
    @Column(name = "quality_rating")
    private Integer qualityRating; // 루트 품질 평가 (1-5)
    
    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted = false; // 완등 여부
    
    @Column(name = "attempt_count")
    private Integer attemptCount; // 시도 횟수
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 태그 타입 한글 표시
     */
    @Transient
    public String getTagTypeKorean() {
        if (tagType == null) return "일반";
        
        return switch (tagType) {
            case "REVIEW" -> "후기";
            case "TIP" -> "팁";
            case "QUESTION" -> "질문";
            case "COMPLETION" -> "완등 인증";
            case "ATTEMPT" -> "시도 기록";
            default -> "일반";
        };
    }
    
    @Override
    public Long getId() {
        return tagId;
    }
}
```

---

## 👍 6. PostLike 엔티티 - 게시글 좋아요

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

## 📌 7. PostBookmark 엔티티 - 게시글 북마크

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

## 💬 8. Comment 엔티티 - 댓글 (계층형)

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

## ⚡ 9. 성능 최적화 전략

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

@Query("SELECT c FROM Comment c " +
       "LEFT JOIN FETCH c.user u " +
       "LEFT JOIN FETCH c.children " +
       "WHERE c.post.id = :postId " +
       "AND c.parent IS NULL " +
       "AND c.isDeleted = false " +
       "ORDER BY c.isPinned DESC, c.likeCount DESC, c.createdAt ASC")
List<Comment> findRootCommentsByPost(@Param("postId") Long postId);
```

---

## ✅ 설계 완료 체크리스트

### 커뮤니티 엔티티 (8개)
- [x] **BoardCategory** - 게시판 카테고리 (계층형 구조, 권한 관리)
- [x] **Post** - 게시글 (제목, 내용, 상태 관리, SEO 지원)
- [x] **PostImage** - 게시글 이미지 (순서 관리, 썸네일 지원)
- [x] **PostVideo** - 게시글 동영상 (썸네일, 재생 통계)
- [x] **PostRouteTag** - 루트 태깅 (후기, 팁, 완등 인증)
- [x] **PostLike** - 좋아요 (다양한 반응 타입, 중복 방지)
- [x] **PostBookmark** - 북마크 (폴더 분류, 개인 메모)
- [x] **Comment** - 댓글 (계층형 구조, 소프트 삭제)

### 핵심 기능
- [x] 계층형 카테고리 시스템 (부모-자식 관계)
- [x] 다양한 콘텐츠 타입 (텍스트, 이미지, 동영상)
- [x] 루트 태깅 시스템 (후기, 팁, 완등 인증)
- [x] 다양한 좋아요 반응 (좋아요, 사랑, 웃음 등)
- [x] 개인 북마크 폴더 시스템

### 성능 최적화
- [x] 게시글 목록 조회 복합 인덱스
- [x] 중복 방지 UNIQUE 인덱스
- [x] 계층형 댓글 최적화 인덱스
- [x] 미디어 파일 순서 정렬 인덱스

### 사용자 경험
- [x] 익명/비공개 댓글 지원
- [x] 게시글 예약 발행
- [x] 베스트 댓글/고정 댓글
- [x] 스포일러 태그 지원
- [x] SEO 메타데이터 관리

---

**다음 단계**: Step 4-4b 알림 및 시스템 엔티티 설계  
**완료일**: 2025-08-19  
**핵심 성과**: 8개 커뮤니티 엔티티 + 계층형 구조 + 소셜 기능 완성