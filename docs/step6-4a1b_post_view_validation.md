# step6-4a1b_post_view_validation.md

## 📝 조회수 관리 및 검증 시스템

```java
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