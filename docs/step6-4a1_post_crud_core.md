# Step 6-4a1: Post CRUD Core Service

**파일**: `routepick-backend/src/main/java/com/routepick/service/community/PostService.java`

이 파일은 게시글 CRUD의 핵심 기능을 구현합니다.

## 📝 게시글 CRUD 핵심 서비스 구현

```java
package com.routepick.service.community;

import com.routepick.common.enums.PostStatus;
import com.routepick.domain.community.entity.BoardCategory;
import com.routepick.domain.community.entity.Post;
import com.routepick.domain.community.repository.BoardCategoryRepository;
import com.routepick.domain.community.repository.PostRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.community.CommunityException;
import com.routepick.exception.user.UserException;
import com.routepick.util.XssProtectionUtil;
import com.routepick.util.SlugGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 게시글 CRUD 핵심 서비스
 * 
 * 주요 기능:
 * 1. 게시글 생성, 수정, 삭제, 조회
 * 2. XSS 방지 및 입력 검증
 * 3. 권한 검증 시스템
 * 4. 조회수 관리
 * 5. 캐싱 전략
 * 
 * @author RoutePickr Team
 * @since 2025.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    
    private final PostRepository postRepository;
    private final BoardCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    // 캐시 이름
    private static final String CACHE_POST = "post";
    private static final String CACHE_POST_LIST = "postList";
    private static final String CACHE_CATEGORY_POSTS = "categoryPosts";
    
    // 설정값
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 10000;
    private static final int VIEW_COUNT_CACHE_SECONDS = 600; // 10분
    
    // ===================== 게시글 생성 =====================
    
    /**
     * 게시글 생성
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_POST_LIST, allEntries = true),
        @CacheEvict(value = CACHE_CATEGORY_POSTS, key = "#categoryId")
    })
    public Post createPost(Long userId, Long categoryId, String title, String content, PostStatus status) {
        log.info("Creating post: userId={}, categoryId={}, title={}", userId, categoryId, title);
        
        // 사용자 확인
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException("사용자를 찾을 수 없습니다: " + userId));
            
        // 카테고리 확인
        BoardCategory category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new CommunityException("카테고리를 찾을 수 없습니다: " + categoryId));
            
        // 입력값 검증 및 XSS 방지
        validatePostInput(title, content);
        String cleanTitle = XssProtectionUtil.sanitizePostContent(title);
        String cleanContent = XssProtectionUtil.sanitizePostContent(content);
        
        // 슬러그 생성 (SEO 친화적 URL)
        String slug = SlugGenerator.generateSlug(cleanTitle);
        
        // 게시글 생성
        Post post = Post.builder()
            .user(user)
            .category(category)
            .title(cleanTitle)
            .content(cleanContent)
            .slug(slug)
            .status(status != null ? status : PostStatus.PUBLISHED)
            .viewCount(0L)
            .likeCount(0L)
            .commentCount(0L)
            .isNotice(false)
            .isPinned(false)
            .build();
            
        Post savedPost = postRepository.save(post);
        
        // 카테고리 게시글 수 증가
        incrementCategoryPostCount(category);
        
        // 이벤트 발행
        eventPublisher.publishEvent(new PostCreatedEvent(savedPost));
        
        log.info("Post created successfully: postId={}", savedPost.getPostId());
        return savedPost;
    }
    
    /**
     * 대량 게시글 생성 (관리자용)
     */
    @Transactional
    @CacheEvict(value = {CACHE_POST_LIST, CACHE_CATEGORY_POSTS}, allEntries = true)
    public void createBulkPosts(Long userId, Long categoryId, List<PostCreateRequest> requests) {
        log.info("Creating bulk posts: userId={}, categoryId={}, count={}", 
                userId, categoryId, requests.size());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException("사용자를 찾을 수 없습니다: " + userId));
            
        BoardCategory category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new CommunityException("카테고리를 찾을 수 없습니다: " + categoryId));
        
        List<Post> posts = requests.stream()
            .map(request -> {
                validatePostInput(request.getTitle(), request.getContent());
                
                return Post.builder()
                    .user(user)
                    .category(category)
                    .title(XssProtectionUtil.sanitizePostContent(request.getTitle()))
                    .content(XssProtectionUtil.sanitizePostContent(request.getContent()))
                    .slug(SlugGenerator.generateSlug(request.getTitle()))
                    .status(PostStatus.PUBLISHED)
                    .viewCount(0L)
                    .likeCount(0L)
                    .commentCount(0L)
                    .build();
            })
            .collect(Collectors.toList());
        
        postRepository.saveAll(posts);
        
        // 카테고리 게시글 수 업데이트
        category.setPostCount(category.getPostCount() + posts.size());
        categoryRepository.save(category);
        
        log.info("Bulk posts created successfully: count={}", posts.size());
    }
    
    // ===================== 게시글 수정 =====================
    
    /**
     * 게시글 수정
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_POST, key = "#postId"),
        @CacheEvict(value = CACHE_POST_LIST, allEntries = true)
    })
    public Post updatePost(Long postId, Long userId, String title, String content, PostStatus status) {
        log.info("Updating post: postId={}, userId={}", postId, userId);
        
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
            
        // 권한 확인
        validatePostOwnership(post, userId);
        
        // 삭제된 게시글 수정 방지
        if (post.getStatus() == PostStatus.DELETED) {
            throw new CommunityException("삭제된 게시글은 수정할 수 없습니다");
        }
        
        // 입력값 검증 및 업데이트
        boolean hasChanges = false;
        
        if (StringUtils.hasText(title) && !title.equals(post.getTitle())) {
            validateTitle(title);
            post.setTitle(XssProtectionUtil.sanitizePostContent(title));
            post.setSlug(SlugGenerator.generateSlug(post.getTitle()));
            hasChanges = true;
        }
        
        if (StringUtils.hasText(content) && !content.equals(post.getContent())) {
            validateContent(content);
            post.setContent(XssProtectionUtil.sanitizePostContent(content));
            hasChanges = true;
        }
        
        if (status != null && status != post.getStatus()) {
            post.setStatus(status);
            hasChanges = true;
        }
        
        if (hasChanges) {
            post.setUpdatedAt(LocalDateTime.now());
            post = postRepository.save(post);
            
            // 이벤트 발행
            eventPublisher.publishEvent(new PostUpdatedEvent(post));
        }
        
        log.info("Post updated successfully: postId={}", postId);
        return post;
    }
    
    /**
     * 게시글 상태 변경
     */
    @Transactional
    @CacheEvict(value = CACHE_POST, key = "#postId")
    public Post changePostStatus(Long postId, Long userId, PostStatus newStatus) {
        log.info("Changing post status: postId={}, userId={}, newStatus={}", 
                postId, userId, newStatus);
        
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
        
        validatePostOwnership(post, userId);
        
        PostStatus oldStatus = post.getStatus();
        post.setStatus(newStatus);
        post.setUpdatedAt(LocalDateTime.now());
        
        Post updatedPost = postRepository.save(post);
        
        // 상태 변경에 따른 처리
        handleStatusChange(post, oldStatus, newStatus);
        
        return updatedPost;
    }
    
    // ===================== 게시글 삭제 =====================
    
    /**
     * 게시글 소프트 삭제
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_POST, key = "#postId"),
        @CacheEvict(value = CACHE_POST_LIST, allEntries = true)
    })
    public void deletePost(Long postId, Long userId) {
        log.info("Deleting post: postId={}, userId={}", postId, userId);
        
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
            
        // 권한 확인
        validatePostOwnership(post, userId);
        
        // 이미 삭제된 게시글 확인
        if (post.getStatus() == PostStatus.DELETED) {
            throw new CommunityException("이미 삭제된 게시글입니다");
        }
        
        // 소프트 삭제 처리
        post.setStatus(PostStatus.DELETED);
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
        
        // 카테고리 게시글 수 감소
        decrementCategoryPostCount(post.getCategory());
        
        // 이벤트 발행
        eventPublisher.publishEvent(new PostDeletedEvent(postId, userId));
        
        log.info("Post deleted successfully: postId={}", postId);
    }
    
    /**
     * 게시글 영구 삭제 (관리자용)
     */
    @Transactional
    @CacheEvict(value = {CACHE_POST, CACHE_POST_LIST}, allEntries = true)
    public void permanentlyDeletePost(Long postId, Long adminId) {
        log.info("Permanently deleting post: postId={}, adminId={}", postId, adminId);
        
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
        
        // 연관 데이터 삭제 (좋아요, 댓글, 북마크 등)
        // 실제 구현에서는 관련 Repository들을 호출하여 삭제
        
        postRepository.delete(post);
        
        // 이벤트 발행
        eventPublisher.publishEvent(new PostPermanentlyDeletedEvent(postId, adminId));
        
        log.info("Post permanently deleted: postId={}", postId);
    }
    
    // ===================== 게시글 조회 =====================
    
    /**
     * 게시글 조회 (조회수 증가)
     */
    @Transactional
    @Cacheable(value = CACHE_POST, key = "#postId", unless = "#result == null")
    public Post getPost(Long postId, Long userId) {
        log.debug("Getting post: postId={}, userId={}", postId, userId);
        
        Post post = postRepository.findByIdWithDetails(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
            
        // 삭제된 게시글 확인
        if (post.getStatus() == PostStatus.DELETED) {
            throw new CommunityException("삭제된 게시글입니다");
        }
        
        // 비공개 게시글 접근 권한 확인
        if (post.getStatus() == PostStatus.DRAFT) {
            if (userId == null || !post.getUser().getUserId().equals(userId)) {
                throw new CommunityException("비공개 게시글입니다");
            }
        }
        
        // 조회수 증가 (비동기)
        incrementViewCountAsync(postId, userId);
        
        return post;
    }
    
    /**
     * 게시글 ID로 기본 정보만 조회 (캐시 없음)
     */
    public Post getPostBasicInfo(Long postId) {
        return postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
    }
    
    /**
     * 슬러그로 게시글 조회
     */
    @Cacheable(value = CACHE_POST, key = "'slug_' + #slug")
    public Post getPostBySlug(String slug, Long userId) {
        log.debug("Getting post by slug: slug={}, userId={}", slug, userId);
        
        Post post = postRepository.findBySlugAndStatusNot(slug, PostStatus.DELETED)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + slug));
        
        // 비공개 게시글 접근 권한 확인
        if (post.getStatus() == PostStatus.DRAFT) {
            if (userId == null || !post.getUser().getUserId().equals(userId)) {
                throw new CommunityException("비공개 게시글입니다");
            }
        }
        
        // 조회수 증가 (비동기)
        incrementViewCountAsync(post.getPostId(), userId);
        
        return post;
    }
    
    /**
     * 사용자의 게시글 목록 조회
     */
    public Page<Post> getUserPosts(Long userId, PostStatus status, Pageable pageable) {
        log.debug("Getting user posts: userId={}, status={}", userId, status);
        
        if (status != null) {
            return postRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            return postRepository.findByUserIdAndStatusNot(userId, PostStatus.DELETED, pageable);
        }
    }
    
    /**
     * 최신 게시글 목록 조회
     */
    @Cacheable(value = CACHE_POST_LIST, 
              key = "'recent_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<Post> getRecentPosts(Pageable pageable) {
        log.debug("Getting recent posts");
        return postRepository.findByStatus(PostStatus.PUBLISHED, pageable);
    }
    
    // ===================== 조회수 관리 =====================
    
    /**
     * 조회수 증가 (비동기)
     */
    @Async
    @Transactional
    public CompletableFuture<Void> incrementViewCountAsync(Long postId, Long userId) {
        try {
            // Redis를 통한 중복 조회 방지 구현
            String viewKey = String.format("post:view:%d:%d", postId, 
                                         userId != null ? userId : 0);
            
            // TODO: Redis에서 중복 체크 로직 구현
            // if (redisTemplate.hasKey(viewKey)) {
            //     return CompletableFuture.completedFuture(null);
            // }
            // redisTemplate.opsForValue().set(viewKey, "1", VIEW_COUNT_CACHE_SECONDS, TimeUnit.SECONDS);
            
            postRepository.incrementViewCount(postId);
            log.debug("View count incremented for post: {}", postId);
        } catch (Exception e) {
            log.error("Failed to increment view count: postId={}, error={}", postId, e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 조회수 직접 업데이트 (관리자용)
     */
    @Transactional
    @CacheEvict(value = CACHE_POST, key = "#postId")
    public void updateViewCount(Long postId, Long newViewCount, Long adminId) {
        log.info("Updating view count: postId={}, newViewCount={}, adminId={}", 
                postId, newViewCount, adminId);
        
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
        
        post.setViewCount(newViewCount);
        postRepository.save(post);
    }
    
    // ===================== Helper 메서드 =====================
    
    /**
     * 게시글 소유권 검증
     */
    private void validatePostOwnership(Post post, Long userId) {
        if (!post.getUser().getUserId().equals(userId)) {
            throw new CommunityException("게시글에 대한 권한이 없습니다");
        }
    }
    
    /**
     * 입력값 검증
     */
    private void validatePostInput(String title, String content) {
        validateTitle(title);
        validateContent(content);
    }
    
    /**
     * 제목 검증
     */
    private void validateTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new CommunityException("제목을 입력해주세요");
        }
        
        if (title.trim().length() > MAX_TITLE_LENGTH) {
            throw new CommunityException("제목은 " + MAX_TITLE_LENGTH + "자를 초과할 수 없습니다");
        }
        
        // 특수문자나 금지어 검사
        if (containsProhibitedWords(title)) {
            throw new CommunityException("제목에 금지된 단어가 포함되어 있습니다");
        }
    }
    
    /**
     * 내용 검증
     */
    private void validateContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new CommunityException("내용을 입력해주세요");
        }
        
        if (content.trim().length() > MAX_CONTENT_LENGTH) {
            throw new CommunityException("내용은 " + MAX_CONTENT_LENGTH + "자를 초과할 수 없습니다");
        }
        
        // 스팸이나 금지어 검사
        if (containsProhibitedWords(content)) {
            throw new CommunityException("내용에 금지된 단어가 포함되어 있습니다");
        }
    }
    
    /**
     * 금지어 검사
     */
    private boolean containsProhibitedWords(String text) {
        // TODO: 금지어 리스트 구현
        return false;
    }
    
    /**
     * 카테고리 게시글 수 증가
     */
    private void incrementCategoryPostCount(BoardCategory category) {
        category.setPostCount(category.getPostCount() + 1);
        categoryRepository.save(category);
    }
    
    /**
     * 카테고리 게시글 수 감소
     */
    private void decrementCategoryPostCount(BoardCategory category) {
        category.setPostCount(Math.max(0, category.getPostCount() - 1));
        categoryRepository.save(category);
    }
    
    /**
     * 상태 변경 처리
     */
    private void handleStatusChange(Post post, PostStatus oldStatus, PostStatus newStatus) {
        // DRAFT -> PUBLISHED: 카테고리 수 증가
        if (oldStatus == PostStatus.DRAFT && newStatus == PostStatus.PUBLISHED) {
            incrementCategoryPostCount(post.getCategory());
        }
        // PUBLISHED -> DRAFT: 카테고리 수 감소
        else if (oldStatus == PostStatus.PUBLISHED && newStatus == PostStatus.DRAFT) {
            decrementCategoryPostCount(post.getCategory());
        }
        
        // 이벤트 발행
        eventPublisher.publishEvent(new PostStatusChangedEvent(post, oldStatus, newStatus));
    }
    
    /**
     * 게시글 존재 여부 확인
     */
    public boolean existsPost(Long postId) {
        return postRepository.existsByIdAndStatusNot(postId, PostStatus.DELETED);
    }
    
    /**
     * 게시글 수 조회 (사용자별)
     */
    public long countUserPosts(Long userId, PostStatus status) {
        if (status != null) {
            return postRepository.countByUserIdAndStatus(userId, status);
        } else {
            return postRepository.countByUserIdAndStatusNot(userId, PostStatus.DELETED);
        }
    }
}
```

## 📋 게시글 이벤트 클래스

```java
/**
 * 게시글 생성 이벤트
 */
@Getter
@AllArgsConstructor
public class PostCreatedEvent {
    private final Post post;
}

/**
 * 게시글 수정 이벤트
 */
@Getter
@AllArgsConstructor
public class PostUpdatedEvent {
    private final Post post;
}

/**
 * 게시글 삭제 이벤트
 */
@Getter
@AllArgsConstructor
public class PostDeletedEvent {
    private final Long postId;
    private final Long userId;
}

/**
 * 게시글 영구 삭제 이벤트
 */
@Getter
@AllArgsConstructor
public class PostPermanentlyDeletedEvent {
    private final Long postId;
    private final Long adminId;
}

/**
 * 게시글 상태 변경 이벤트
 */
@Getter
@AllArgsConstructor
public class PostStatusChangedEvent {
    private final Post post;
    private final PostStatus oldStatus;
    private final PostStatus newStatus;
}
```

## 🔧 게시글 생성 요청 DTO

```java
/**
 * 게시글 생성 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateRequest {
    
    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다")
    private String title;
    
    @NotBlank(message = "내용은 필수입니다")
    @Size(max = 10000, message = "내용은 10,000자를 초과할 수 없습니다")
    private String content;
    
    @NotNull(message = "카테고리는 필수입니다")
    private Long categoryId;
    
    private PostStatus status = PostStatus.PUBLISHED;
    
    private Boolean isNotice = false;
    
    private Boolean isPinned = false;
    
    // 태그 (선택사항)
    private List<String> tags;
}

/**
 * 게시글 수정 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdateRequest {
    
    @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다")
    private String title;
    
    @Size(max = 10000, message = "내용은 10,000자를 초과할 수 없습니다")
    private String content;
    
    private PostStatus status;
    
    private Boolean isNotice;
    
    private Boolean isPinned;
    
    private List<String> tags;
}
```

## 📊 연동 참고사항

### step6-4a2_post_search_media.md 연동점
1. **검색 기능**: 게시글 제목, 내용 검색
2. **미디어 처리**: 이미지, 동영상 업로드 및 관리
3. **인기 게시글**: 좋아요, 조회수 기반 정렬
4. **카테고리 필터링**: 카테고리별 게시글 조회

### 주요 의존성
- **PostRepository**: 게시글 데이터 관리
- **BoardCategoryRepository**: 카테고리 정보
- **UserRepository**: 사용자 권한 확인
- **XssProtectionUtil**: XSS 방지 처리
- **SlugGenerator**: SEO 친화적 URL 생성

### 성능 최적화
1. **캐싱**: Redis 기반 게시글 캐싱
2. **비동기 처리**: 조회수 증가 비동기 처리
3. **페이징**: 대량 데이터 효율적 처리
4. **인덱싱**: 검색 성능 향상

---
**연관 파일**: `step6-4a2_post_search_media.md`
**구현 우선순위**: HIGH (커뮤니티 핵심 기능)
**예상 개발 기간**: 3-4일