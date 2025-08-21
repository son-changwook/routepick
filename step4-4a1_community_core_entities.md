# Step 4-4a1: 커뮤니티 핵심 엔티티 설계

> **RoutePickr 커뮤니티 핵심 시스템** - 게시판, 게시글, 미디어, 루트 태깅  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-4a1 (JPA 엔티티 50개 - 커뮤니티 핵심 5개)  
> **분할**: step4-4a_community_entities.md → 커뮤니티 핵심 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 커뮤니티 핵심 시스템**을 담고 있습니다.

### 🎯 주요 특징
- **계층형 게시판**: 카테고리별 분류, 권한 관리, 표시 순서
- **풍부한 게시글**: 텍스트, 이미지, 동영상, 루트 태깅 지원
- **미디어 관리**: 순서 정렬, 캡션, 재생 통계
- **루트 연동**: 후기, 팁, 완등 인증, 스포일러 처리

### 📊 엔티티 목록 (5개)
1. **BoardCategory** - 게시판 카테고리 (계층형 구조)
2. **Post** - 게시글 (예약발행, SEO 최적화)
3. **PostImage** - 게시글 이미지 (순서, 캡션)
4. **PostVideo** - 게시글 동영상 (썸네일, 재생통계)
5. **PostRouteTag** - 루트 태깅 (후기, 팁, 완등인증)

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
    
    @NotNull
    @Size(min = 1, max = 50, message = "카테고리 이름은 1-50자 사이여야 합니다")
    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName; // 카테고리 이름
    
    @Size(max = 500, message = "카테고리 설명은 최대 500자입니다")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 카테고리 설명
    
    @Column(name = "category_type", length = 30)
    private String categoryType; // FREE, QUESTION, REVIEW, NOTICE, EVENT
    
    @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다")
    @Max(value = 100, message = "표시 순서는 100 이하여야 합니다")
    @Column(name = "display_order")
    private Integer displayOrder = 1; // 표시 순서
    
    @Column(name = "category_level", nullable = false)
    private Integer categoryLevel = 1; // 카테고리 깊이 (1: 최상위)
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "post_count")
    private Long postCount = 0L; // 게시글 수
    
    @Column(name = "icon_name", length = 50)
    private String iconName; // 아이콘 이름
    
    @Column(name = "background_color", length = 7)
    private String backgroundColor; // 배경색 (#FFFFFF)
    
    @Column(name = "required_auth_level", length = 20)
    private String requiredAuthLevel; // 필요 권한 (GUEST, USER, VERIFIED, ADMIN)
    
    @Column(name = "allow_anonymous", nullable = false)
    private boolean allowAnonymous = false; // 익명 글 허용
    
    @Column(name = "allow_attachments", nullable = false)
    private boolean allowAttachments = true; // 첨부파일 허용
    
    @Column(name = "max_attachment_size")
    private Long maxAttachmentSize = 10485760L; // 최대 첨부 파일 크기 (10MB)
    
    // ===== 계층형 관계 =====
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private BoardCategory parentCategory; // 부모 카테고리
    
    @OneToMany(mappedBy = "parentCategory", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BoardCategory> childCategories = new ArrayList<>(); // 하위 카테고리
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 게시글 수 증가
     */
    public void increasePostCount() {
        this.postCount++;
        
        // 부모 카테고리도 게시글 수 증가
        if (parentCategory != null) {
            parentCategory.increasePostCount();
        }
    }
    
    /**
     * 게시글 수 감소
     */
    public void decreasePostCount() {
        if (this.postCount > 0) {
            this.postCount--;
            
            // 부모 카테고리도 게시글 수 감소
            if (parentCategory != null) {
                parentCategory.decreasePostCount();
            }
        }
    }
    
    /**
     * 최상위 카테고리인지 확인
     */
    @Transient
    public boolean isRootCategory() {
        return parentCategory == null;
    }
    
    /**
     * 하위 카테고리 존재 여부
     */
    @Transient
    public boolean hasChildren() {
        return childCategories != null && !childCategories.isEmpty();
    }
    
    /**
     * 카테고리 타입 한글 표시
     */
    @Transient
    public String getCategoryTypeKorean() {
        if (categoryType == null) return "일반";
        
        return switch (categoryType) {
            case "FREE" -> "자유게시판";
            case "QUESTION" -> "질문게시판";
            case "REVIEW" -> "후기게시판";
            case "NOTICE" -> "공지사항";
            case "EVENT" -> "이벤트";
            default -> "일반";
        };
    }
    
    /**
     * 권한 수준 한글 표시
     */
    @Transient
    public String getRequiredAuthLevelKorean() {
        if (requiredAuthLevel == null) return "모든 사용자";
        
        return switch (requiredAuthLevel) {
            case "GUEST" -> "모든 사용자";
            case "USER" -> "회원";
            case "VERIFIED" -> "인증회원";
            case "ADMIN" -> "관리자";
            default -> "일반 회원";
        };
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
 * - 다양한 게시판별 게시글 관리
 * - 예약 발행 지원
 * - 소프트 삭제 적용
 */
@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_post_category_status", columnList = "category_id, post_status, published_at DESC"),
    @Index(name = "idx_post_author", columnList = "author_id"),
    @Index(name = "idx_post_published", columnList = "published_at DESC"),
    @Index(name = "idx_post_views", columnList = "view_count DESC"),
    @Index(name = "idx_post_likes", columnList = "like_count DESC")
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
    @JoinColumn(name = "author_id", nullable = false)
    private User author;
    
    @NotNull
    @Size(min = 1, max = 200, message = "제목은 1-200자 사이여야 합니다")
    @Column(name = "title", nullable = false, length = 200)
    private String title; // 게시글 제목
    
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content; // 게시글 내용 (HTML)
    
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary; // 요약 (검색용)
    
    @Column(name = "post_status", length = 20)
    private String postStatus = "PUBLISHED"; // DRAFT, PUBLISHED, PENDING, HIDDEN
    
    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false; // 고정 게시글
    
    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous = false; // 익명 게시글
    
    @Column(name = "allow_comments", nullable = false)
    private boolean allowComments = true; // 댓글 허용
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 조회 수
    
    @Column(name = "like_count")
    private Integer likeCount = 0; // 좋아요 수
    
    @Column(name = "comment_count")
    private Integer commentCount = 0; // 댓글 수
    
    @Column(name = "bookmark_count")
    private Integer bookmarkCount = 0; // 북마크 수
    
    @Column(name = "report_count")
    private Integer reportCount = 0; // 신고 수
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt; // 발행일
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl; // 썸네일 이미지
    
    @Column(name = "tags", length = 500)
    private String tags; // 해시태그 (쉼표 구분)
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // 작성자 IP
    
    @Column(name = "user_agent", length = 500)
    private String userAgent; // User Agent
    
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes; // 관리자 메모
    
    // ===== SEO 최적화 필드 =====
    
    @Column(name = "seo_title", length = 200)
    private String seoTitle; // SEO 제목
    
    @Column(name = "seo_description", length = 300)
    private String seoDescription; // SEO 설명
    
    @Column(name = "seo_keywords", length = 200)
    private String seoKeywords; // SEO 키워드
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PostImage> postImages = new ArrayList<>(); // 게시글 이미지
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PostVideo> postVideos = new ArrayList<>(); // 게시글 동영상
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PostRouteTag> postRouteTags = new ArrayList<>(); // 루트 태깅
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 조회 수 증가
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
        if (likeCount != null && likeCount > 0) {
            this.likeCount--;
        }
    }
    
    /**
     * 댓글 수 증가/감소
     */
    public void increaseCommentCount() {
        this.commentCount = (commentCount == null ? 0 : commentCount) + 1;
    }
    
    public void decreaseCommentCount() {
        if (commentCount != null && commentCount > 0) {
            this.commentCount--;
        }
    }
    
    /**
     * 북마크 수 증가/감소
     */
    public void increaseBookmarkCount() {
        this.bookmarkCount = (bookmarkCount == null ? 0 : bookmarkCount) + 1;
    }
    
    public void decreaseBookmarkCount() {
        if (bookmarkCount != null && bookmarkCount > 0) {
            this.bookmarkCount--;
        }
    }
    
    /**
     * 게시글 발행
     */
    public void publish() {
        this.postStatus = "PUBLISHED";
        this.publishedAt = LocalDateTime.now();
    }
    
    /**
     * 게시글 예약 발행
     */
    public void schedulePublish(LocalDateTime scheduledTime) {
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
    private Integer width; // 이미지 너비 (px)
    
    @Column(name = "height")
    private Integer height; // 이미지 높이 (px)
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 파일 사이즈 MB 단위 반환
     */
    @Transient
    public double getFileSizeMB() {
        if (fileSize == null) return 0;
        return fileSize / (1024.0 * 1024.0);
    }
    
    /**
     * 이미지 비율 반환
     */
    @Transient
    public double getAspectRatio() {
        if (width == null || height == null || height == 0) return 1.0;
        return (double) width / height;
    }
    
    /**
     * 썸네일 URL 생성 (예: _thumb 접미사)
     */
    @Transient
    public String getThumbnailUrl() {
        if (imageUrl == null) return null;
        
        int lastDot = imageUrl.lastIndexOf('.');
        if (lastDot == -1) return imageUrl;
        
        return imageUrl.substring(0, lastDot) + "_thumb" + imageUrl.substring(lastDot);
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
    
    @Size(max = 500, message = "동영상 설명은 최대 500자입니다")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 동영상 설명
    
    @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다")
    @Max(value = 100, message = "표시 순서는 100 이하여야 합니다")
    @Column(name = "display_order")
    private Integer displayOrder = 1; // 표시 순서
    
    @Column(name = "duration_seconds")
    private Integer durationSeconds; // 재생 시간 (초)
    
    @Column(name = "file_size")
    private Long fileSize; // 파일 크기 (bytes)
    
    @Column(name = "width")
    private Integer width; // 동영상 너비 (px)
    
    @Column(name = "height")
    private Integer height; // 동영상 높이 (px)
    
    @Column(name = "video_format", length = 20)
    private String videoFormat; // MP4, AVI, MOV 등
    
    @Column(name = "encoding", length = 50)
    private String encoding; // H.264, H.265 등
    
    @Column(name = "bitrate")
    private Integer bitrate; // 비트레이트 (kbps)
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "play_count")
    private Long playCount = 0L; // 재생 횟수
    
    @Column(name = "auto_play", nullable = false)
    private boolean autoPlay = false; // 자동 재생
    
    @Column(name = "muted", nullable = false)
    private boolean muted = true; // 음소거
    
    @Column(name = "show_controls", nullable = false)
    private boolean showControls = true; // 컨트롤 표시
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 재생 횟수 증가
     */
    public void increasePlayCount() {
        this.playCount = (playCount == null ? 0L : playCount) + 1;
    }
    
    /**
     * 재생 시간을 MM:SS 형식으로 반환
     */
    @Transient
    public String getFormattedDuration() {
        if (durationSeconds == null || durationSeconds <= 0) return "00:00";
        
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    /**
     * 파일 사이즈 MB 단위 반환
     */
    @Transient
    public double getFileSizeMB() {
        if (fileSize == null) return 0;
        return fileSize / (1024.0 * 1024.0);
    }
    
    /**
     * 동영상 비율 반환
     */
    @Transient
    public double getAspectRatio() {
        if (width == null || height == null || height == 0) return 16.0 / 9.0; // 기본 16:9
        return (double) width / height;
    }
    
    /**
     * 비트레이트 품질 등급 반환
     */
    @Transient
    public String getQualityLevel() {
        if (bitrate == null) return "알 수 없음";
        
        if (bitrate >= 8000) return "매우 높음";
        if (bitrate >= 5000) return "높음";
        if (bitrate >= 2500) return "보통";
        if (bitrate >= 1000) return "낮음";
        return "매우 낮음";
    }
    
    @Override
    public Long getId() {
        return videoId;
    }
}
```

---

## 🏷️ 5. PostRouteTag 엔티티 - 루트 태깅

```java
package com.routepick.domain.community.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 게시글 루트 태깅
 * - 게시글에 루트 연결
 * - 후기, 팁, 완등 인증 등
 */
@Entity
@Table(name = "post_route_tags", indexes = {
    @Index(name = "idx_post_route_tag", columnList = "post_id, route_id", unique = true),
    @Index(name = "idx_post_route", columnList = "route_id"),
    @Index(name = "idx_route_tag_type", columnList = "tag_type"),
    @Index(name = "idx_route_tag_user", columnList = "user_id")
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 태그 생성자
    
    @Column(name = "tag_type", length = 30)
    private String tagType; // REVIEW, TIP, QUESTION, COMPLETION, ATTEMPT
    
    @Size(max = 500, message = "태그 설명은 최대 500자입니다")
    @Column(name = "tag_description", columnDefinition = "TEXT")
    private String tagDescription; // 태그 설명
    
    @Column(name = "is_spoiler", nullable = false)
    private boolean isSpoiler = false; // 스포일러 여부 (베타 공개)
    
    @Min(value = 1, message = "난이도 평가는 1 이상이어야 합니다")
    @Max(value = 5, message = "난이도 평가는 5 이하여야 합니다")
    @Column(name = "difficulty_rating")
    private Integer difficultyRating; // 개인적 난이도 평가 (1-5)
    
    @Min(value = 1, message = "품질 평가는 1 이상이어야 합니다")
    @Max(value = 5, message = "품질 평가는 5 이하여야 합니다")
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
    
    /**
     * 난이도 평가 한글 표시
     */
    @Transient
    public String getDifficultyRatingKorean() {
        if (difficultyRating == null) return "평가 없음";
        
        return switch (difficultyRating) {
            case 1 -> "매우 쉬움";
            case 2 -> "쉬움";
            case 3 -> "보통";
            case 4 -> "어려움";
            case 5 -> "매우 어려움";
            default -> "평가 없음";
        };
    }
    
    /**
     * 품질 평가 한글 표시
     */
    @Transient
    public String getQualityRatingKorean() {
        if (qualityRating == null) return "평가 없음";
        
        return switch (qualityRating) {
            case 1 -> "매우 나쁨";
            case 2 -> "나쁨";
            case 3 -> "보통";
            case 4 -> "좋음";
            case 5 -> "매우 좋음";
            default -> "평가 없음";
        };
    }
    
    /**
     * 평점을 별점으로 표시
     */
    @Transient
    public String getRatingStars(Integer rating) {
        if (rating == null) return "";
        
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < rating; i++) {
            stars.append("⭐");
        }
        return stars.toString();
    }
    
    /**
     * 완등 인증 여부 체크
     */
    @Transient
    public boolean isCompletionCertified() {
        return "COMPLETION".equals(tagType) && isCompleted;
    }
    
    @Override
    public Long getId() {
        return tagId;
    }
}
```

---

## ✅ 설계 완료 체크리스트

### 커뮤니티 핵심 엔티티 (5개)
- [x] **BoardCategory** - 게시판 카테고리 (계층형 구조, 권한 관리, 아이콘/색상)
- [x] **Post** - 게시글 (예약 발행, SEO 최적화, 소프트 삭제)
- [x] **PostImage** - 게시글 이미지 (순서 정렬, 캡션, 썸네일 생성)
- [x] **PostVideo** - 게시글 동영상 (썸네일, 재생 통계, 품질 관리)
- [x] **PostRouteTag** - 루트 태깅 (후기, 팁, 완등 인증, 평점)

### 계층형 게시판 시스템
- [x] 부모-자식 관계로 카테고리 계층 구조
- [x] 권한별 접근 제어 (GUEST, USER, VERIFIED, ADMIN)
- [x] 카테고리별 설정 (익명글, 첨부파일, 크기 제한)
- [x] 게시글 수 자동 집계 (부모 카테고리까지 반영)

### 풍부한 콘텐츠 시스템
- [x] 텍스트, 이미지, 동영상 통합 지원
- [x] 예약 발행 및 상태 관리
- [x] SEO 최적화 (제목, 설명, 키워드)
- [x] 썸네일 자동 생성 및 관리

### 루트 연동 시스템
- [x] 루트별 후기, 팁, 질문 태깅
- [x] 완등 인증 및 시도 기록
- [x] 개인적 난이도/품질 평가 (1-5점)
- [x] 베타 스포일러 처리

### 성능 최적화
- [x] 게시글 목록 조회 복합 인덱스
- [x] 미디어 파일 순서 정렬 인덱스
- [x] 카테고리별 검색 인덱스
- [x] LAZY 로딩으로 성능 최적화

---

**다음 단계**: step4-4a2_community_interaction_entities.md (커뮤니티 상호작용 엔티티)  
**완료일**: 2025-08-20  
**핵심 성과**: 5개 커뮤니티 핵심 엔티티 + 계층형 게시판 + 루트 연동 + SEO 최적화 완성