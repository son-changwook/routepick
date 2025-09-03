# step6-4a1a_post_crud_core.md

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
 * 보안 특징:
 * - XSS 공격 방지를 위한 HTML 태그 필터링
 * - 슬러그 중복 방지 시스템
 * - 권한 기반 접근 제어
 * - Redis 캐싱을 통한 성능 최적화
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BoardCategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    private static final String CACHE_POST = "post";
    private static final String CACHE_POST_LIST = "post_list";
    private static final String CACHE_POST_CATEGORY = "post_category";
    private static final int VIEW_COUNT_CACHE_SECONDS = 1800; // 30분
    
    // ===================== 게시글 생성 =====================
    
    /**
     * 게시글 생성
     * 
     * @param userId 작성자 ID
     * @param categoryId 카테고리 ID
     * @param title 제목
     * @param content 내용
     * @param tags 태그 목록
     * @param isPublic 공개 여부
     * @return 생성된 게시글
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_POST_LIST, allEntries = true),
        @CacheEvict(value = CACHE_POST_CATEGORY, key = "#categoryId")
    })
    public Post createPost(Long userId, Long categoryId, String title, String content, 
                          String tags, boolean isPublic) {
        log.info("Creating post: userId={}, categoryId={}, title={}, isPublic={}", 
                userId, categoryId, title, isPublic);
        
        // 사용자 조회 및 검증
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException("사용자를 찾을 수 없습니다: " + userId));
            
        // 카테고리 조회 및 검증
        BoardCategory category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new CommunityException("카테고리를 찾을 수 없습니다: " + categoryId));
        
        // XSS 보호 처리
        String safeTitle = XssProtectionUtil.cleanXssContent(title);
        String safeContent = XssProtectionUtil.cleanXssContent(content);
        String safeTags = tags != null ? XssProtectionUtil.cleanXssContent(tags) : null;
        
        // 제목 길이 제한 (100자)
        if (safeTitle.length() > 100) {
            throw new CommunityException("제목은 100자를 초과할 수 없습니다");
        }
        
        // 내용 길이 제한 (10,000자)
        if (safeContent.length() > 10000) {
            throw new CommunityException("내용은 10,000자를 초과할 수 없습니다");
        }
        
        // 슬러그 생성 (중복 방지)
        String slug = generateUniqueSlug(safeTitle);
        
        // 게시글 생성
        Post post = Post.builder()
            .user(user)
            .category(category)
            .title(safeTitle)
            .content(safeContent)
            .slug(slug)
            .tags(safeTags)
            .status(isPublic ? PostStatus.PUBLISHED : PostStatus.DRAFT)
            .viewCount(0L)
            .likeCount(0L)
            .commentCount(0L)
            .build();
        
        Post savedPost = postRepository.save(post);
        
        // 게시글 생성 이벤트 발행
        // eventPublisher.publishEvent(new PostCreatedEvent(savedPost));
        
        log.info("Post created successfully: postId={}, slug={}", 
                savedPost.getPostId(), savedPost.getSlug());
        
        return savedPost;
    }
    
    /**
     * 고유한 슬러그 생성
     */
    private String generateUniqueSlug(String title) {
        String baseSlug = SlugGenerator.generateSlug(title);
        String uniqueSlug = baseSlug;
        int counter = 1;
        
        // 슬러그 중복 검사 및 처리
        while (postRepository.existsBySlug(uniqueSlug)) {
            uniqueSlug = baseSlug + "-" + counter;
            counter++;
            
            // 무한 루프 방지 (최대 1000번 시도)
            if (counter > 1000) {
                uniqueSlug = baseSlug + "-" + System.currentTimeMillis();
                break;
            }
        }
        
        return uniqueSlug;
    }
    
    // ===================== 게시글 수정 =====================
    
    /**
     * 게시글 수정
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_POST, key = "#postId"),
        @CacheEvict(value = CACHE_POST_LIST, allEntries = true),
        @CacheEvict(value = CACHE_POST_CATEGORY, allEntries = true)
    })
    public Post updatePost(Long postId, Long userId, String title, String content, 
                          String tags, PostStatus status) {
        log.info("Updating post: postId={}, userId={}, title={}, status={}", 
                postId, userId, title, status);
        
        // 게시글 조회 및 권한 검증
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
            
        // 작성자 권한 검증
        validatePostOwnership(post, userId);
        
        // XSS 보호 처리
        if (StringUtils.hasText(title)) {
            String safeTitle = XssProtectionUtil.cleanXssContent(title);
            if (safeTitle.length() > 100) {
                throw new CommunityException("제목은 100자를 초과할 수 없습니다");
            }
            post.setTitle(safeTitle);
            
            // 제목 변경시 슬러그도 업데이트
            String newSlug = generateUniqueSlug(safeTitle);
            post.setSlug(newSlug);
        }
        
        if (StringUtils.hasText(content)) {
            String safeContent = XssProtectionUtil.cleanXssContent(content);
            if (safeContent.length() > 10000) {
                throw new CommunityException("내용은 10,000자를 초과할 수 없습니다");
            }
            post.setContent(safeContent);
        }
        
        if (tags != null) {
            String safeTags = XssProtectionUtil.cleanXssContent(tags);
            post.setTags(safeTags);
        }
        
        if (status != null) {
            post.setStatus(status);
        }
        
        post.setUpdatedAt(LocalDateTime.now());
        
        Post updatedPost = postRepository.save(post);
        
        // 게시글 수정 이벤트 발행
        // eventPublisher.publishEvent(new PostUpdatedEvent(updatedPost));
        
        log.info("Post updated successfully: postId={}", updatedPost.getPostId());
        
        return updatedPost;
    }
    
    // ===================== 게시글 삭제 =====================
    
    /**
     * 게시글 삭제 (소프트 삭제)
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_POST, key = "#postId"),
        @CacheEvict(value = CACHE_POST_LIST, allEntries = true),
        @CacheEvict(value = CACHE_POST_CATEGORY, allEntries = true)
    })
    public void deletePost(Long postId, Long userId) {
        log.info("Deleting post: postId={}, userId={}", postId, userId);
        
        // 게시글 조회 및 권한 검증
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
            
        // 작성자 권한 검증
        validatePostOwnership(post, userId);
        
        // 소프트 삭제 처리
        post.setStatus(PostStatus.DELETED);
        post.setUpdatedAt(LocalDateTime.now());
        
        postRepository.save(post);
        
        // 게시글 삭제 이벤트 발행
        // eventPublisher.publishEvent(new PostDeletedEvent(post));
        
        log.info("Post deleted successfully: postId={}", postId);
    }
    
    /**
     * 게시글 완전 삭제 (물리적 삭제 - 관리자용)
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_POST, key = "#postId"),
        @CacheEvict(value = CACHE_POST_LIST, allEntries = true),
        @CacheEvict(value = CACHE_POST_CATEGORY, allEntries = true)
    })
    public void permanentDeletePost(Long postId, Long adminId) {
        log.warn("Permanently deleting post: postId={}, adminId={}", postId, adminId);
        
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
        
        // 관련 데이터 정리 (댓글, 좋아요, 북마크 등)
        // TODO: 연관된 댓글, 좋아요, 북마크 데이터도 함께 삭제 처리
        
        postRepository.delete(post);
        
        log.warn("Post permanently deleted: postId={}", postId);
    }
    
    // ===================== 게시글 조회 =====================
    
    /**
     * 게시글 상세 조회
     */
    @Cacheable(value = CACHE_POST, key = "#postId")
    public Post getPost(Long postId, Long userId) {
        log.debug("Getting post: postId={}, userId={}", postId, userId);
        
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
        
        // 삭제된 게시글은 조회 불가
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
     * 게시글 기본 정보 조회 (캐시 없음)
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