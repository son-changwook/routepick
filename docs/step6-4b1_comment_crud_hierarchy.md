# Step 6-4b1: CommentService - CRUD 및 계층 구조

> 댓글 CRUD 관리 및 계층형 댓글 구조 구현
> 생성일: 2025-08-22
> 단계: 6-4b1 (Service 레이어 - 댓글 CRUD)
> 참고: step4-4a2, step5-4a2, step5-4f1

---

## 🎯 설계 목표

- **댓글 CRUD**: 생성, 조회, 수정, 삭제 관리
- **계층형 구조**: parent_id 활용한 대댓글 시스템
- **깊이 제한**: 최대 3단계까지 대댓글 허용
- **댓글 개수**: 게시글별 댓글 수 실시간 업데이트
- **XSS 방지**: 악성 스크립트 차단

---

## 💬 CommentService 핵심 구현

### CommentService.java (CRUD 및 계층 구조)
```java
package com.routepick.service.community;

import com.routepick.common.enums.CommentStatus;
import com.routepick.domain.community.entity.Comment;
import com.routepick.domain.community.entity.CommentLike;
import com.routepick.domain.community.entity.Post;
import com.routepick.domain.community.repository.CommentRepository;
import com.routepick.domain.community.repository.CommentLikeRepository;
import com.routepick.domain.community.repository.PostRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.community.CommunityException;
import com.routepick.exception.user.UserException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 댓글 관리 서비스 - CRUD 및 계층 구조
 * - 댓글 CRUD 관리
 * - 계층형 댓글 구조
 * - 대댓글 깊이 제한
 * - 댓글 개수 카운팅
 * - XSS 방지 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {
    
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    
    // 캐시 이름
    private static final String CACHE_COMMENT = "comment";
    private static final String CACHE_POST_COMMENTS = "postComments";
    private static final String CACHE_COMMENT_LIKES = "commentLikes";
    
    // 설정값
    private static final int MAX_COMMENT_LENGTH = 1000;
    private static final int MAX_COMMENT_DEPTH = 3; // 최대 댓글 깊이
    private static final int MAX_REPLIES_PER_COMMENT = 100; // 댓글당 최대 대댓글 수
    
    /**
     * 댓글 생성
     * @param postId 게시글 ID
     * @param userId 작성자 ID
     * @param content 댓글 내용
     * @param parentId 부모 댓글 ID (대댓글인 경우)
     * @return 생성된 댓글
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_POST_COMMENTS, key = "#postId"),
        @CacheEvict(value = CACHE_COMMENT, allEntries = true)
    })
    public Comment createComment(Long postId, Long userId, String content, Long parentId) {
        log.info("Creating comment: postId={}, userId={}, parentId={}", postId, userId, parentId);
        
        // 게시글 확인
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new CommunityException("게시글을 찾을 수 없습니다: " + postId));
            
        // 사용자 확인
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException("사용자를 찾을 수 없습니다: " + userId));
            
        // 내용 검증 및 XSS 방지
        validateCommentContent(content);
        String cleanContent = XssProtectionUtil.cleanXss(content);
        
        // 댓글 깊이 확인
        int depth = 0;
        Comment parentComment = null;
        
        if (parentId != null) {
            parentComment = commentRepository.findById(parentId)
                .orElseThrow(() -> new CommunityException("부모 댓글을 찾을 수 없습니다: " + parentId));
                
            // 같은 게시글의 댓글인지 확인
            if (!parentComment.getPost().getPostId().equals(postId)) {
                throw new CommunityException("잘못된 부모 댓글입니다");
            }
            
            // 깊이 계산 및 제한 확인
            depth = calculateCommentDepth(parentComment) + 1;
            if (depth >= MAX_COMMENT_DEPTH) {
                throw new CommunityException("대댓글은 최대 " + MAX_COMMENT_DEPTH + "단계까지만 가능합니다");
            }
            
            // 대댓글 수 제한 확인
            Long replyCount = commentRepository.countByParentId(parentId);
            if (replyCount >= MAX_REPLIES_PER_COMMENT) {
                throw new CommunityException("대댓글은 최대 " + MAX_REPLIES_PER_COMMENT + "개까지만 가능합니다");
            }
        }
        
        // 댓글 생성
        Comment comment = Comment.builder()
            .post(post)
            .user(user)
            .content(cleanContent)
            .parentComment(parentComment)
            .depth(depth)
            .status(CommentStatus.ACTIVE)
            .likeCount(0L)
            .replyCount(0L)
            .isDeleted(false)
            .build();
            
        Comment savedComment = commentRepository.save(comment);
        
        // 게시글 댓글 수 증가
        postRepository.incrementCommentCount(postId);
        
        // 부모 댓글의 대댓글 수 증가
        if (parentComment != null) {
            commentRepository.incrementReplyCount(parentId);
            
            // 부모 댓글 작성자에게 알림
            if (!parentComment.getUser().getUserId().equals(userId)) {
                notificationService.sendReplyNotification(
                    parentComment.getUser().getUserId(),
                    savedComment
                );
            }
        }
        
        // 게시글 작성자에게 알림 (본인 댓글 제외)
        if (!post.getUser().getUserId().equals(userId)) {
            notificationService.sendCommentNotification(
                post.getUser().getUserId(),
                savedComment
            );
        }
        
        // 이벤트 발행
        eventPublisher.publishEvent(new CommentCreatedEvent(savedComment));
        
        log.info("Comment created successfully: commentId={}", savedComment.getCommentId());
        return savedComment;
    }
    
    /**
     * 댓글 수정
     * @param commentId 댓글 ID
     * @param userId 수정자 ID
     * @param content 새로운 내용
     * @return 수정된 댓글
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_COMMENT, key = "#commentId"),
        @CacheEvict(value = CACHE_POST_COMMENTS, allEntries = true)
    })
    public Comment updateComment(Long commentId, Long userId, String content) {
        log.info("Updating comment: commentId={}, userId={}", commentId, userId);
        
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommunityException("댓글을 찾을 수 없습니다: " + commentId));
            
        // 권한 확인
        if (!comment.getUser().getUserId().equals(userId)) {
            throw new CommunityException("댓글 수정 권한이 없습니다");
        }
        
        // 삭제된 댓글 확인
        if (comment.isDeleted() || comment.getStatus() == CommentStatus.DELETED) {
            throw new CommunityException("삭제된 댓글은 수정할 수 없습니다");
        }
        
        // 내용 검증 및 XSS 방지
        validateCommentContent(content);
        String cleanContent = XssProtectionUtil.cleanXss(content);
        
        comment.setContent(cleanContent);
        comment.setUpdatedAt(LocalDateTime.now());
        comment.setIsEdited(true);
        
        Comment updatedComment = commentRepository.save(comment);
        
        // 이벤트 발행
        eventPublisher.publishEvent(new CommentUpdatedEvent(updatedComment));
        
        log.info("Comment updated successfully: commentId={}", commentId);
        return updatedComment;
    }
    
    /**
     * 댓글 삭제
     * @param commentId 댓글 ID
     * @param userId 삭제자 ID
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_COMMENT, key = "#commentId"),
        @CacheEvict(value = CACHE_POST_COMMENTS, allEntries = true)
    })
    public void deleteComment(Long commentId, Long userId) {
        log.info("Deleting comment: commentId={}, userId={}", commentId, userId);
        
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommunityException("댓글을 찾을 수 없습니다: " + commentId));
            
        // 권한 확인
        if (!comment.getUser().getUserId().equals(userId)) {
            throw new CommunityException("댓글 삭제 권한이 없습니다");
        }
        
        // 대댓글이 있는 경우 소프트 삭제
        if (comment.getReplyCount() > 0) {
            comment.setContent("[삭제된 댓글입니다]");
            comment.setIsDeleted(true);
            comment.setStatus(CommentStatus.DELETED);
            comment.setDeletedAt(LocalDateTime.now());
            commentRepository.save(comment);
        } else {
            // 대댓글이 없으면 완전 삭제
            commentRepository.delete(comment);
            
            // 부모 댓글의 대댓글 수 감소
            if (comment.getParentComment() != null) {
                commentRepository.decrementReplyCount(comment.getParentComment().getCommentId());
            }
        }
        
        // 게시글 댓글 수 감소
        postRepository.decrementCommentCount(comment.getPost().getPostId());
        
        // 이벤트 발행
        eventPublisher.publishEvent(new CommentDeletedEvent(commentId));
        
        log.info("Comment deleted successfully: commentId={}", commentId);
    }
    
    /**
     * 게시글의 댓글 목록 조회 (계층형)
     * @param postId 게시글 ID
     * @param pageable 페이징
     * @return 댓글 페이지
     */
    @Cacheable(value = CACHE_POST_COMMENTS, 
              key = "#postId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<Comment> getPostComments(Long postId, Pageable pageable) {
        log.debug("Getting post comments: postId={}", postId);
        
        // 최상위 댓글만 조회 (depth = 0)
        Page<Comment> topLevelComments = commentRepository.findTopLevelCommentsByPostId(
            postId, pageable
        );
        
        // 각 댓글의 대댓글 로드 (N+1 문제 방지를 위해 배치 조회)
        List<Long> commentIds = topLevelComments.getContent().stream()
            .map(Comment::getCommentId)
            .collect(Collectors.toList());
            
        if (!commentIds.isEmpty()) {
            List<Comment> replies = commentRepository.findRepliesByParentIds(commentIds);
            Map<Long, List<Comment>> replyMap = replies.stream()
                .collect(Collectors.groupingBy(c -> c.getParentComment().getCommentId()));
                
            // 대댓글 매핑
            topLevelComments.forEach(comment -> {
                List<Comment> commentReplies = replyMap.getOrDefault(
                    comment.getCommentId(), Collections.emptyList()
                );
                comment.setReplies(commentReplies);
            });
        }
        
        return topLevelComments;
    }
    
    /**
     * 사용자의 댓글 목록 조회
     * @param userId 사용자 ID
     * @param pageable 페이징
     * @return 댓글 페이지
     */
    public Page<Comment> getUserComments(Long userId, Pageable pageable) {
        log.debug("Getting user comments: userId={}", userId);
        
        return commentRepository.findByUserIdAndStatus(
            userId, CommentStatus.ACTIVE, pageable
        );
    }
    
    /**
     * 댓글 깊이 계산
     * @param comment 댓글
     * @return 깊이
     */
    private int calculateCommentDepth(Comment comment) {
        int depth = 0;
        Comment current = comment;
        
        while (current.getParentComment() != null) {
            depth++;
            current = current.getParentComment();
            
            // 무한 루프 방지
            if (depth >= MAX_COMMENT_DEPTH) {
                break;
            }
        }
        
        return depth;
    }
    
    /**
     * 댓글 내용 검증
     * @param content 내용
     */
    private void validateCommentContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new CommunityException("댓글 내용을 입력해주세요");
        }
        
        if (content.length() > MAX_COMMENT_LENGTH) {
            throw new CommunityException("댓글은 " + MAX_COMMENT_LENGTH + "자를 초과할 수 없습니다");
        }
    }
    
    // 이벤트 클래스들
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CommentCreatedEvent {
        private final Comment comment;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CommentUpdatedEvent {
        private final Comment comment;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CommentDeletedEvent {
        private final Long commentId;
    }
}
```

---

## 📊 계층형 댓글 구조

### 1. 댓글 깊이 관리
- **최대 깊이**: 3단계 제한
- **깊이 계산**: 부모-자식 관계 추적
- **무한 루프 방지**: 순환 참조 차단
- **depth 필드**: 댓글 레벨 저장

### 2. 대댓글 제한
- **개수 제한**: 댓글당 최대 100개
- **계층적 조회**: N+1 문제 방지
- **배치 로딩**: 효율적 대댓글 조회
- **매핑 구조**: Map 기반 관계 설정

### 3. 댓글 삭제 전략
- **소프트 삭제**: 대댓글 존재시
- **하드 삭제**: 대댓글 없을 때
- **참조 정리**: 관련 카운트 감소
- **데이터 일관성**: 트랜잭션 보장

---

## ✅ CRUD 완료 사항
- ✅ 댓글 생성 (XSS 방지, 깊이 검증)
- ✅ 댓글 수정 (권한 확인, 수정 표시)
- ✅ 댓글 삭제 (소프트/하드 삭제)
- ✅ 댓글 조회 (계층형 구조)
- ✅ 대댓글 깊이 제한 (최대 3단계)
- ✅ 댓글 개수 카운팅
- ✅ 알림 시스템 연동
- ✅ Redis 캐싱 적용

---

*CommentService CRUD 및 계층 구조 구현 완료*