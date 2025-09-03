# Step 6-4a2: Post Search & Media Management

**파일들**: 게시글 검색, 미디어 관리, 인기 게시글 시스템 구현

이 파일은 `step6-4a1_post_crud_core.md`와 연계된 게시글 검색 및 미디어 관리 시스템입니다.

## 🔍 게시글 검색 및 미디어 관리 서비스

```java
package com.routepick.service.community;

import com.routepick.common.enums.PostStatus;
import com.routepick.domain.community.entity.Post;
import com.routepick.domain.community.entity.PostImage;
import com.routepick.domain.community.entity.PostVideo;
import com.routepick.domain.community.repository.PostRepository;
import com.routepick.domain.community.repository.PostImageRepository;
import com.routepick.domain.community.repository.PostVideoRepository;
import com.routepick.exception.community.CommunityException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 게시글 검색 및 미디어 관리 서비스
 * 
 * 주요 기능:
 * 1. 게시글 검색 (제목, 내용, 태그)
 * 2. 인기 게시글 조회
 * 3. 카테고리별 필터링
 * 4. 이미지/비디오 업로드 관리
 * 5. 미디어 최적화 처리
 * 
 * @author RoutePickr Team
 * @since 2025.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostSearchMediaService {
    
    private final PostRepository postRepository;
    private final PostImageRepository imageRepository;
    private final PostVideoRepository videoRepository;
    private final MediaUploadService mediaUploadService;
    
    // 캐시 이름
    private static final String CACHE_POPULAR_POSTS = "popularPosts";
    private static final String CACHE_CATEGORY_POSTS = "categoryPosts";
    private static final String CACHE_SEARCH_RESULTS = "searchResults";
    
    // 설정값
    private static final int MAX_IMAGES_PER_POST = 10;
    private static final int MAX_VIDEOS_PER_POST = 3;
    private static final int SEARCH_MIN_LENGTH = 2;
    private static final int SEARCH_MAX_LENGTH = 50;
    
    // ===================== 게시글 검색 =====================
    
    /**
     * 키워드로 게시글 검색
     */
    @Cacheable(value = CACHE_SEARCH_RESULTS,
              key = "#keyword + '_' + #categoryId + '_' + #pageable.pageNumber")
    public Page<Post> searchPosts(String keyword, Long categoryId, Pageable pageable) {
        log.debug("Searching posts: keyword={}, categoryId={}", keyword, categoryId);
        
        validateSearchKeyword(keyword);
        String cleanKeyword = XssProtectionUtil.sanitizeUserInput(keyword);
        
        if (categoryId != null) {
            return postRepository.searchInCategory(cleanKeyword, categoryId, pageable);
        } else {
            return postRepository.searchPosts(cleanKeyword, pageable);
        }
    }
    
    /**
     * 고급 검색 (다중 조건)
     */
    public Page<Post> advancedSearch(PostSearchRequest searchRequest, Pageable pageable) {
        log.debug("Advanced search: {}", searchRequest);
        
        // 검색 조건 검증
        validateAdvancedSearchRequest(searchRequest);
        
        // 검색어 정제
        if (StringUtils.hasText(searchRequest.getKeyword())) {
            searchRequest.setKeyword(XssProtectionUtil.sanitizeUserInput(searchRequest.getKeyword()));
        }
        
        if (StringUtils.hasText(searchRequest.getAuthor())) {
            searchRequest.setAuthor(XssProtectionUtil.sanitizeUserInput(searchRequest.getAuthor()));
        }
        
        return postRepository.advancedSearch(
            searchRequest.getKeyword(),
            searchRequest.getCategoryId(),
            searchRequest.getAuthor(),
            searchRequest.getDateFrom(),
            searchRequest.getDateTo(),
            searchRequest.getMinViewCount(),
            searchRequest.getMinLikeCount(),
            searchRequest.getTags(),
            pageable
        );
    }
    
    /**
     * 태그로 게시글 검색
     */
    @Cacheable(value = CACHE_SEARCH_RESULTS, key = "'tag_' + #tag + '_' + #pageable.pageNumber")
    public Page<Post> searchByTag(String tag, Pageable pageable) {
        log.debug("Searching posts by tag: {}", tag);
        
        if (!StringUtils.hasText(tag)) {
            throw new CommunityException("태그를 입력해주세요");
        }
        
        String cleanTag = XssProtectionUtil.sanitizeUserInput(tag);
        return postRepository.findByTag(cleanTag, pageable);
    }
    
    /**
     * 인기 검색어 조회
     */
    @Cacheable(value = "popularKeywords", unless = "#result.isEmpty()")
    public List<String> getPopularKeywords(int limit) {
        log.debug("Getting popular keywords: limit={}", limit);
        
        // TODO: 실제 구현에서는 검색 로그를 분석하여 인기 검색어 추출
        return postRepository.getPopularSearchKeywords(limit);
    }
    
    /**
     * 관련 게시글 추천
     */
    @Cacheable(value = "relatedPosts", key = "#postId + '_' + #limit")
    public List<Post> getRelatedPosts(Long postId, int limit) {
        log.debug("Getting related posts: postId={}, limit={}", postId, limit);
        
        Post currentPost = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
        
        // 같은 카테고리의 최근 인기 게시글
        return postRepository.findRelatedPosts(
            currentPost.getCategory().getCategoryId(),
            postId,
            PageRequest.of(0, limit)
        );
    }
    
    // ===================== 인기 게시글 조회 =====================
    
    /**
     * 인기 게시글 조회 (기간별)
     */
    @Cacheable(value = CACHE_POPULAR_POSTS, key = "#period + '_' + #size")
    public List<Post> getPopularPosts(int period, int size) {
        log.debug("Getting popular posts: period={} days, size={}", period, size);
        
        LocalDateTime since = LocalDateTime.now().minusDays(period);
        return postRepository.findPopularPosts(since, PageRequest.of(0, size));
    }
    
    /**
     * 조회수 기준 인기 게시글
     */
    @Cacheable(value = CACHE_POPULAR_POSTS, key = "'views_' + #period + '_' + #size")
    public List<Post> getMostViewedPosts(int period, int size) {
        log.debug("Getting most viewed posts: period={} days, size={}", period, size);
        
        LocalDateTime since = LocalDateTime.now().minusDays(period);
        return postRepository.findMostViewedPosts(since, PageRequest.of(0, size));
    }
    
    /**
     * 좋아요 기준 인기 게시글
     */
    @Cacheable(value = CACHE_POPULAR_POSTS, key = "'likes_' + #period + '_' + #size")
    public List<Post> getMostLikedPosts(int period, int size) {
        log.debug("Getting most liked posts: period={} days, size={}", period, size);
        
        LocalDateTime since = LocalDateTime.now().minusDays(period);
        return postRepository.findMostLikedPosts(since, PageRequest.of(0, size));
    }
    
    /**
     * 댓글 많은 게시글 (핫한 토픽)
     */
    @Cacheable(value = CACHE_POPULAR_POSTS, key = "'comments_' + #period + '_' + #size")
    public List<Post> getMostCommentedPosts(int period, int size) {
        log.debug("Getting most commented posts: period={} days, size={}", period, size);
        
        LocalDateTime since = LocalDateTime.now().minusDays(period);
        return postRepository.findMostCommentedPosts(since, PageRequest.of(0, size));
    }
    
    // ===================== 카테고리별 조회 =====================
    
    /**
     * 카테고리별 게시글 목록 조회
     */
    @Cacheable(value = CACHE_CATEGORY_POSTS,
              key = "#categoryId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<Post> getPostsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Getting posts by category: categoryId={}", categoryId);
        
        return postRepository.findByCategoryIdAndStatus(
            categoryId, PostStatus.PUBLISHED, pageable
        );
    }
    
    /**
     * 카테고리별 인기 게시글
     */
    @Cacheable(value = CACHE_CATEGORY_POSTS, key = "'popular_' + #categoryId + '_' + #size")
    public List<Post> getPopularPostsByCategory(Long categoryId, int size) {
        log.debug("Getting popular posts by category: categoryId={}, size={}", categoryId, size);
        
        LocalDateTime since = LocalDateTime.now().minusDays(7); // 최근 7일
        return postRepository.findPopularPostsByCategory(categoryId, since, PageRequest.of(0, size));
    }
    
    /**
     * 공지사항 조회
     */
    public List<Post> getNotices(Long categoryId) {
        log.debug("Getting notices: categoryId={}", categoryId);
        
        if (categoryId != null) {
            return postRepository.findNoticesByCategory(categoryId);
        } else {
            return postRepository.findAllNotices();
        }
    }
    
    /**
     * 고정 게시글 조회
     */
    public List<Post> getPinnedPosts(Long categoryId) {
        log.debug("Getting pinned posts: categoryId={}", categoryId);
        
        if (categoryId != null) {
            return postRepository.findPinnedPostsByCategory(categoryId);
        } else {
            return postRepository.findAllPinnedPosts();
        }
    }
    
    // ===================== 미디어 관리 =====================
    
    /**
     * 게시글 이미지 업로드 및 처리
     */
    @Transactional
    public List<PostImage> uploadPostImages(Long postId, List<MultipartFile> images) {
        log.info("Uploading images for post: postId={}, count={}", postId, images.size());
        
        if (images.size() > MAX_IMAGES_PER_POST) {
            throw new CommunityException("이미지는 최대 " + MAX_IMAGES_PER_POST + "개까지 업로드 가능합니다");
        }
        
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
        
        List<PostImage> uploadedImages = new ArrayList<>();
        int displayOrder = getNextImageDisplayOrder(postId);
        
        for (MultipartFile file : images) {
            try {
                validateImageFile(file);
                
                // S3에 이미지 업로드
                String imageUrl = mediaUploadService.uploadImage(file, "posts");
                String thumbnailUrl = mediaUploadService.generateThumbnail(imageUrl, 300, 300);
                
                PostImage postImage = PostImage.builder()
                    .post(post)
                    .imageUrl(imageUrl)
                    .thumbnailUrl(thumbnailUrl)
                    .displayOrder(displayOrder++)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .fileName(file.getOriginalFilename())
                    .build();
                
                uploadedImages.add(imageRepository.save(postImage));
                
            } catch (Exception e) {
                log.error("Failed to upload image: file={}, error={}", 
                         file.getOriginalFilename(), e.getMessage());
                throw new CommunityException("이미지 업로드 실패: " + file.getOriginalFilename());
            }
        }
        
        log.info("Images uploaded successfully: postId={}, count={}", postId, uploadedImages.size());
        return uploadedImages;
    }
    
    /**
     * 게시글 비디오 업로드 및 처리
     */
    @Transactional
    public List<PostVideo> uploadPostVideos(Long postId, List<MultipartFile> videos) {
        log.info("Uploading videos for post: postId={}, count={}", postId, videos.size());
        
        if (videos.size() > MAX_VIDEOS_PER_POST) {
            throw new CommunityException("동영상은 최대 " + MAX_VIDEOS_PER_POST + "개까지 업로드 가능합니다");
        }
        
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
        
        List<PostVideo> uploadedVideos = new ArrayList<>();
        int displayOrder = getNextVideoDisplayOrder(postId);
        
        for (MultipartFile file : videos) {
            try {
                validateVideoFile(file);
                
                // S3에 비디오 업로드
                String videoUrl = mediaUploadService.uploadVideo(file, "posts");
                String thumbnailUrl = mediaUploadService.generateVideoThumbnail(videoUrl);
                
                PostVideo postVideo = PostVideo.builder()
                    .post(post)
                    .videoUrl(videoUrl)
                    .thumbnailUrl(thumbnailUrl)
                    .displayOrder(displayOrder++)
                    .duration(0) // TODO: 비디오 메타데이터에서 추출
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .fileName(file.getOriginalFilename())
                    .viewCount(0L)
                    .build();
                
                uploadedVideos.add(videoRepository.save(postVideo));
                
            } catch (Exception e) {
                log.error("Failed to upload video: file={}, error={}", 
                         file.getOriginalFilename(), e.getMessage());
                throw new CommunityException("동영상 업로드 실패: " + file.getOriginalFilename());
            }
        }
        
        log.info("Videos uploaded successfully: postId={}, count={}", postId, uploadedVideos.size());
        return uploadedVideos;
    }
    
    /**
     * 게시글 이미지 목록 조회
     */
    public List<PostImage> getPostImages(Long postId) {
        return imageRepository.findByPostIdOrderByDisplayOrder(postId);
    }
    
    /**
     * 게시글 비디오 목록 조회
     */
    public List<PostVideo> getPostVideos(Long postId) {
        return videoRepository.findByPostIdOrderByDisplayOrder(postId);
    }
    
    /**
     * 이미지 삭제
     */
    @Transactional
    public void deletePostImage(Long imageId, Long userId) {
        log.info("Deleting post image: imageId={}, userId={}", imageId, userId);
        
        PostImage image = imageRepository.findById(imageId)
            .orElseThrow(() -> new CommunityException("이미지를 찾을 수 없습니다: " + imageId));
        
        // 권한 확인
        if (!image.getPost().getUser().getUserId().equals(userId)) {
            throw new CommunityException("이미지 삭제 권한이 없습니다");
        }
        
        // S3에서 파일 삭제
        mediaUploadService.deleteFile(image.getImageUrl());
        if (StringUtils.hasText(image.getThumbnailUrl())) {
            mediaUploadService.deleteFile(image.getThumbnailUrl());
        }
        
        imageRepository.delete(image);
    }
    
    /**
     * 비디오 삭제
     */
    @Transactional
    public void deletePostVideo(Long videoId, Long userId) {
        log.info("Deleting post video: videoId={}, userId={}", videoId, userId);
        
        PostVideo video = videoRepository.findById(videoId)
            .orElseThrow(() -> new CommunityException("비디오를 찾을 수 없습니다: " + videoId));
        
        // 권한 확인
        if (!video.getPost().getUser().getUserId().equals(userId)) {
            throw new CommunityException("비디오 삭제 권한이 없습니다");
        }
        
        // S3에서 파일 삭제
        mediaUploadService.deleteFile(video.getVideoUrl());
        if (StringUtils.hasText(video.getThumbnailUrl())) {
            mediaUploadService.deleteFile(video.getThumbnailUrl());
        }
        
        videoRepository.delete(video);
    }
    
    // ===================== Helper 메서드 =====================
    
    private void validateSearchKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new CommunityException("검색어를 입력해주세요");
        }
        
        if (keyword.trim().length() < SEARCH_MIN_LENGTH) {
            throw new CommunityException("검색어는 최소 " + SEARCH_MIN_LENGTH + "자 이상이어야 합니다");
        }
        
        if (keyword.trim().length() > SEARCH_MAX_LENGTH) {
            throw new CommunityException("검색어는 최대 " + SEARCH_MAX_LENGTH + "자까지 입력 가능합니다");
        }
    }
    
    private void validateAdvancedSearchRequest(PostSearchRequest request) {
        if (StringUtils.hasText(request.getKeyword())) {
            validateSearchKeyword(request.getKeyword());
        }
        
        if (request.getDateFrom() != null && request.getDateTo() != null) {
            if (request.getDateFrom().isAfter(request.getDateTo())) {
                throw new CommunityException("시작 날짜가 종료 날짜보다 늦을 수 없습니다");
            }
        }
        
        if (request.getMinViewCount() != null && request.getMinViewCount() < 0) {
            throw new CommunityException("최소 조회수는 0 이상이어야 합니다");
        }
        
        if (request.getMinLikeCount() != null && request.getMinLikeCount() < 0) {
            throw new CommunityException("최소 좋아요 수는 0 이상이어야 합니다");
        }
    }
    
    private void validateImageFile(MultipartFile file) {
        // 파일 크기 검증 (10MB)
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new CommunityException("이미지 파일 크기는 10MB를 초과할 수 없습니다");
        }
        
        // 이미지 형식 검증
        String contentType = file.getContentType();
        if (!isValidImageType(contentType)) {
            throw new CommunityException("지원하지 않는 이미지 형식입니다: " + contentType);
        }
    }
    
    private void validateVideoFile(MultipartFile file) {
        // 파일 크기 검증 (100MB)
        long maxSize = 100 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new CommunityException("비디오 파일 크기는 100MB를 초과할 수 없습니다");
        }
        
        // 비디오 형식 검증
        String contentType = file.getContentType();
        if (!isValidVideoType(contentType)) {
            throw new CommunityException("지원하지 않는 비디오 형식입니다: " + contentType);
        }
    }
    
    private boolean isValidImageType(String contentType) {
        return contentType != null && (
            contentType.equals("image/jpeg") ||
            contentType.equals("image/jpg") ||
            contentType.equals("image/png") ||
            contentType.equals("image/gif") ||
            contentType.equals("image/webp")
        );
    }
    
    private boolean isValidVideoType(String contentType) {
        return contentType != null && (
            contentType.equals("video/mp4") ||
            contentType.equals("video/avi") ||
            contentType.equals("video/mov") ||
            contentType.equals("video/wmv")
        );
    }
    
    private int getNextImageDisplayOrder(Long postId) {
        return imageRepository.findMaxDisplayOrderByPostId(postId)
            .map(order -> order + 1)
            .orElse(0);
    }
    
    private int getNextVideoDisplayOrder(Long postId) {
        return videoRepository.findMaxDisplayOrderByPostId(postId)
            .map(order -> order + 1)
            .orElse(0);
    }
}
```

## 📋 검색 요청 DTO 클래스

```java
/**
 * 게시글 고급 검색 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostSearchRequest {
    
    // 검색어
    private String keyword;
    
    // 카테고리 ID
    private Long categoryId;
    
    // 작성자 (닉네임 또는 이름)
    private String author;
    
    // 작성일 범위
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    
    // 최소 조회수
    private Long minViewCount;
    
    // 최소 좋아요 수
    private Long minLikeCount;
    
    // 태그 목록
    private List<String> tags;
    
    // 미디어 포함 여부
    private Boolean hasImages;
    private Boolean hasVideos;
    
    // 정렬 기준
    private SearchSortType sortType = SearchSortType.RELEVANCE;
}

/**
 * 검색 정렬 타입
 */
public enum SearchSortType {
    RELEVANCE,      // 연관성
    RECENT,         // 최신순
    POPULAR,        // 인기순
    VIEW_COUNT,     // 조회수순
    LIKE_COUNT      // 좋아요순
}

/**
 * 미디어 업로드 응답
 */
@Data
@Builder
public class MediaUploadResponse {
    private Long mediaId;
    private String mediaUrl;
    private String thumbnailUrl;
    private Long fileSize;
    private String fileName;
    private String mimeType;
    private Integer displayOrder;
    private LocalDateTime uploadedAt;
}
```

## 🔧 미디어 업로드 서비스 인터페이스

```java
/**
 * 미디어 업로드 서비스 인터페이스
 */
public interface MediaUploadService {
    
    /**
     * 이미지 업로드
     */
    String uploadImage(MultipartFile file, String directory);
    
    /**
     * 비디오 업로드
     */
    String uploadVideo(MultipartFile file, String directory);
    
    /**
     * 썸네일 생성
     */
    String generateThumbnail(String imageUrl, int width, int height);
    
    /**
     * 비디오 썸네일 생성
     */
    String generateVideoThumbnail(String videoUrl);
    
    /**
     * 파일 삭제
     */
    void deleteFile(String fileUrl);
    
    /**
     * 파일 메타데이터 추출
     */
    MediaMetadata extractMetadata(MultipartFile file);
}
```

## 📊 성능 최적화 전략

```yaml
# 검색 및 미디어 최적화 설정
app:
  community:
    search:
      cache-ttl: 300s  # 5분 캐시
      max-results: 1000  # 최대 검색 결과
      highlight-enabled: true  # 검색어 하이라이트
      
    media:
      image:
        max-size: 10MB
        max-count: 10
        allowed-types: [jpg, jpeg, png, gif, webp]
        thumbnail-sizes: [150x150, 300x300, 600x600]
        
      video:
        max-size: 100MB
        max-count: 3
        allowed-types: [mp4, avi, mov, wmv]
        thumbnail-size: 480x270
        
      cdn:
        enabled: true
        base-url: ${CDN_BASE_URL:}
        cache-control: max-age=31536000  # 1년
```

## 📊 연동 참고사항

### step6-4a1_post_crud_core.md 연동점
1. **기본 CRUD**: 게시글 생성 시 미디어 자동 처리
2. **권한 검증**: 미디어 삭제 시 게시글 소유권 확인
3. **캐시 무효화**: 게시글 수정 시 관련 검색 캐시 갱신
4. **이벤트 연동**: 미디어 업로드/삭제 이벤트 처리

### 성능 최적화
1. **검색 인덱싱**: Elasticsearch 연동 고려
2. **이미지 최적화**: WebP 변환, 다양한 크기 썸네일
3. **CDN 활용**: 미디어 파일 빠른 로딩
4. **캐싱**: 인기 검색어, 관련 게시글 캐싱

### 확장성 고려사항
1. **검색 엔진**: Elasticsearch/OpenSearch 도입
2. **이미지 처리**: AWS Lambda 기반 비동기 처리
3. **스토리지**: S3 Intelligent Tiering 적용
4. **분석**: 검색 패턴 분석 및 개선

---
**연관 파일**: `step6-4a1_post_crud_core.md`
**구현 우선순위**: MEDIUM (부가 기능)
**예상 개발 기간**: 3-4일