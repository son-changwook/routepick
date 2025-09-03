# Step 6-4b: CommentService 구현

> 댓글 관리 서비스 - 계층형 댓글, 댓글 좋아요, 신고 관리
> 생성일: 2025-08-22
> 단계: 6-4b (Service 레이어 - 댓글 관리)
> 참고: step4-4a2, step5-4a2, step5-4f1

---

## 🎯 설계 목표

- **댓글 CRUD**: 생성, 조회, 수정, 삭제 관리
- **계층형 구조**: parent_id 활용한 대댓글 시스템
- **댓글 좋아요**: CommentLike 엔티티 관리
- **깊이 제한**: 최대 3단계까지 대댓글 허용
- **댓글 개수**: 게시글별 댓글 수 실시간 업데이트
- **신고 관리**: 부적절한 댓글 신고 및 처리

---

## 💬 CommentService 구현

### CommentService.java
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
 * 댓글 관리 서비스
 * - 댓글 CRUD 관리
 * - 계층형 댓글 구조
 * - 댓글 좋아요 관리
 * - 댓글 신고 및 관리
 * - 대댓글 깊이 제한
 * - 댓글 개수 카운팅
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
     * 댓글 좋아요 토글
     * @param commentId 댓글 ID
     * @param userId 사용자 ID
     * @return 좋아요 여부
     */
    @Transactional
    @CacheEvict(value = CACHE_COMMENT_LIKES, key = "#commentId")
    public boolean toggleCommentLike(Long commentId, Long userId) {
        log.info("Toggling comment like: commentId={}, userId={}", commentId, userId);
        
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommunityException("댓글을 찾을 수 없습니다: " + commentId));
            
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException("사용자를 찾을 수 없습니다: " + userId));
            
        // 자기 댓글 좋아요 방지
        if (comment.getUser().getUserId().equals(userId)) {
            throw new CommunityException("자신의 댓글에는 좋아요를 할 수 없습니다");
        }
        
        Optional<CommentLike> existingLike = commentLikeRepository
            .findByCommentIdAndUserId(commentId, userId);
            
        if (existingLike.isPresent()) {
            // 좋아요 취소
            commentLikeRepository.delete(existingLike.get());
            commentRepository.decrementLikeCount(commentId);
            
            log.info("Comment like removed: commentId={}, userId={}", commentId, userId);
            return false;
        } else {
            // 좋아요 추가
            CommentLike like = CommentLike.builder()
                .comment(comment)
                .user(user)
                .build();
                
            commentLikeRepository.save(like);
            commentRepository.incrementLikeCount(commentId);
            
            // 댓글 작성자에게 알림
            notificationService.sendCommentLikeNotification(
                comment.getUser().getUserId(),
                comment,
                user
            );
            
            log.info("Comment like added: commentId={}, userId={}", commentId, userId);
            return true;
        }
    }
    
    /**
     * 댓글 좋아요 여부 확인
     * @param commentId 댓글 ID
     * @param userId 사용자 ID
     * @return 좋아요 여부
     */
    @Cacheable(value = CACHE_COMMENT_LIKES, key = "#commentId + '_' + #userId")
    public boolean isCommentLiked(Long commentId, Long userId) {
        return commentLikeRepository.existsByCommentIdAndUserId(commentId, userId);
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
     * 댓글 신고
     * @param commentId 댓글 ID
     * @param userId 신고자 ID
     * @param reason 신고 사유
     */
    @Transactional
    public void reportComment(Long commentId, Long userId, String reason) {
        log.info("Reporting comment: commentId={}, userId={}, reason={}", 
                commentId, userId, reason);
        
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommunityException("댓글을 찾을 수 없습니다: " + commentId));
            
        // 자기 댓글 신고 방지
        if (comment.getUser().getUserId().equals(userId)) {
            throw new CommunityException("자신의 댓글은 신고할 수 없습니다");
        }
        
        // 신고 처리 (별도 신고 테이블에 저장하거나 관리자에게 알림)
        comment.setReportCount(comment.getReportCount() + 1);
        
        // 신고 횟수가 임계값을 넘으면 자동 숨김 처리
        if (comment.getReportCount() >= 5) {
            comment.setStatus(CommentStatus.HIDDEN);
        }
        
        commentRepository.save(comment);
        
        // 관리자에게 알림
        notificationService.sendReportNotificationToAdmin(comment, userId, reason);
        
        log.info("Comment reported: commentId={}", commentId);
    }
    
    /**
     * 인기 댓글 조회
     * @param postId 게시글 ID
     * @param size 조회 개수
     * @return 인기 댓글 목록
     */
    public List<Comment> getPopularComments(Long postId, int size) {
        log.debug("Getting popular comments: postId={}, size={}", postId, size);
        
        PageRequest pageable = PageRequest.of(0, size, 
            Sort.by(Sort.Direction.DESC, "likeCount"));
            
        return commentRepository.findByPostIdAndStatus(
            postId, CommentStatus.ACTIVE, pageable
        ).getContent();
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

## 🔧 설정 및 통합

### application.yml 추가 설정
```yaml
# 댓글 시스템 설정
app:
  community:
    comment:
      cache-ttl: 30m  # 댓글 캐시 TTL
      max-length: 1000
      max-depth: 3  # 최대 댓글 깊이
      max-replies: 100  # 댓글당 최대 대댓글
      report-threshold: 5  # 자동 숨김 신고 횟수
```

---

## 📊 주요 기능 요약

### 1. 댓글 CRUD
- **생성**: XSS 방지, 깊이 제한 확인
- **수정**: 권한 검증, 수정 표시
- **삭제**: 소프트/하드 삭제 구분
- **조회**: 계층형 구조 로드

### 2. 계층형 댓글
- **깊이 제한**: 최대 3단계
- **대댓글 수 제한**: 댓글당 100개
- **깊이별 들여쓰기**: UI 표현 지원
- **부모-자식 관계**: 효율적 조회

### 3. 댓글 좋아요
- **중복 방지**: 사용자당 1회
- **자기 좋아요 방지**: 본인 댓글 제외
- **실시간 카운트**: 즉시 반영
- **알림 발송**: 작성자에게 알림

### 4. 신고 및 관리
- **신고 기능**: 부적절한 댓글 신고
- **자동 숨김**: 임계값 초과시
- **관리자 알림**: 신고 내역 전달
- **통계 관리**: 신고 횟수 추적

---

## ✅ 완료 사항
- ✅ 댓글 CRUD 관리
- ✅ 계층형 댓글 구조 (parent_id 활용)
- ✅ 댓글 좋아요 관리 (CommentLike)
- ✅ 댓글 신고 및 관리 기능
- ✅ 대댓글 깊이 제한 (최대 3단계)
- ✅ 댓글 개수 카운팅
- ✅ XSS 방지 처리
- ✅ Redis 캐싱 적용
- ✅ 알림 시스템 연동

---

*CommentService 구현 완료: 계층형 댓글 관리 시스템*