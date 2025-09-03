# Step 6-4b2: CommentService - 좋아요 및 신고 기능

> 댓글 좋아요, 신고 관리 및 추가 기능들
> 생성일: 2025-08-22
> 단계: 6-4b2 (Service 레이어 - 댓글 좋아요/신고)
> 참고: step4-4a2, step5-4a2, step5-4f1

---

## 🎯 설계 목표

- **댓글 좋아요**: CommentLike 엔티티 관리
- **신고 관리**: 부적절한 댓글 신고 및 처리
- **인기 댓글**: 좋아요 기반 인기 댓글 조회
- **중복 방지**: 사용자별 좋아요 중복 방지
- **알림 연동**: 좋아요/신고 시 알림 발송

---

## 💡 CommentService 좋아요 및 신고 기능

### CommentService.java (좋아요/신고 부분)
```java
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
     * 댓글 좋아요 수 조회
     * @param commentId 댓글 ID
     * @return 좋아요 수
     */
    @Cacheable(value = CACHE_COMMENT_LIKES, key = "'count_' + #commentId")
    public Long getCommentLikeCount(Long commentId) {
        return commentLikeRepository.countByCommentId(commentId);
    }
    
    /**
     * 댓글에 좋아요 누른 사용자 목록
     * @param commentId 댓글 ID
     * @param pageable 페이징
     * @return 사용자 목록
     */
    public Page<User> getCommentLikeUsers(Long commentId, Pageable pageable) {
        log.debug("Getting comment like users: commentId={}", commentId);
        
        return commentLikeRepository.findUsersByCommentId(commentId, pageable);
    }
    
    /**
     * 사용자가 좋아요 누른 댓글 목록
     * @param userId 사용자 ID
     * @param pageable 페이징
     * @return 댓글 목록
     */
    public Page<Comment> getUserLikedComments(Long userId, Pageable pageable) {
        log.debug("Getting user liked comments: userId={}", userId);
        
        return commentLikeRepository.findCommentsByUserId(userId, pageable);
    }
    
    /**
     * 댓글 대량 좋아요 토글 (관리자용)
     * @param commentIds 댓글 ID 목록
     * @param userId 사용자 ID
     * @param isLike 좋아요 여부
     */
    @Transactional
    @Async
    public CompletableFuture<Void> batchToggleCommentLikes(
            List<Long> commentIds, Long userId, boolean isLike) {
        log.info("Batch toggling comment likes: commentIds={}, userId={}, isLike={}", 
                commentIds.size(), userId, isLike);
        
        for (Long commentId : commentIds) {
            try {
                toggleCommentLike(commentId, userId);
            } catch (Exception e) {
                log.warn("Failed to toggle like for comment {}: {}", commentId, e.getMessage());
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 댓글 통계 조회
     * @param postId 게시글 ID
     * @return 댓글 통계
     */
    public CommentStatistics getCommentStatistics(Long postId) {
        log.debug("Getting comment statistics: postId={}", postId);
        
        Long totalComments = commentRepository.countByPostId(postId);
        Long totalLikes = commentLikeRepository.countByPostId(postId);
        Long activeComments = commentRepository.countByPostIdAndStatus(
            postId, CommentStatus.ACTIVE
        );
        
        return CommentStatistics.builder()
            .postId(postId)
            .totalComments(totalComments)
            .activeComments(activeComments)
            .totalLikes(totalLikes)
            .averageLikesPerComment(totalComments > 0 ? (double) totalLikes / totalComments : 0.0)
            .build();
    }
    
    /**
     * 사용자별 댓글 활동 통계
     * @param userId 사용자 ID
     * @return 활동 통계
     */
    public UserCommentActivity getUserCommentActivity(Long userId) {
        log.debug("Getting user comment activity: userId={}", userId);
        
        Long totalComments = commentRepository.countByUserId(userId);
        Long totalLikes = commentLikeRepository.countLikesReceivedByUserId(userId);
        Long totalGivenLikes = commentLikeRepository.countByUserId(userId);
        
        return UserCommentActivity.builder()
            .userId(userId)
            .totalComments(totalComments)
            .totalLikesReceived(totalLikes)
            .totalLikesGiven(totalGivenLikes)
            .averageLikesPerComment(totalComments > 0 ? (double) totalLikes / totalComments : 0.0)
            .build();
    }
    
    /**
     * 댓글 신고 내역 조회 (관리자용)
     * @param pageable 페이징
     * @return 신고된 댓글 목록
     */
    public Page<Comment> getReportedComments(Pageable pageable) {
        log.debug("Getting reported comments");
        
        return commentRepository.findByReportCountGreaterThan(0, pageable);
    }
    
    /**
     * 댓글 상태 변경 (관리자용)
     * @param commentId 댓글 ID
     * @param status 새로운 상태
     * @param adminUserId 관리자 ID
     */
    @Transactional
    public void changeCommentStatus(Long commentId, CommentStatus status, Long adminUserId) {
        log.info("Changing comment status: commentId={}, status={}, adminUserId={}", 
                commentId, status, adminUserId);
        
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommunityException("댓글을 찾을 수 없습니다: " + commentId));
        
        CommentStatus oldStatus = comment.getStatus();
        comment.setStatus(status);
        
        if (status == CommentStatus.DELETED) {
            comment.setDeletedAt(LocalDateTime.now());
        }
        
        commentRepository.save(comment);
        
        // 상태 변경 이벤트 발행
        eventPublisher.publishEvent(new CommentStatusChangedEvent(
            commentId, oldStatus, status, adminUserId
        ));
        
        log.info("Comment status changed: commentId={}, {} -> {}", 
                commentId, oldStatus, status);
    }
    
    /**
     * 댓글 핀/언핀 (게시글 작성자용)
     * @param commentId 댓글 ID
     * @param userId 요청자 ID (게시글 작성자여야 함)
     * @param isPinned 핀 여부
     */
    @Transactional
    public void toggleCommentPin(Long commentId, Long userId, boolean isPinned) {
        log.info("Toggling comment pin: commentId={}, userId={}, isPinned={}", 
                commentId, userId, isPinned);
        
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommunityException("댓글을 찾을 수 없습니다: " + commentId));
        
        // 게시글 작성자 권한 확인
        if (!comment.getPost().getUser().getUserId().equals(userId)) {
            throw new CommunityException("댓글 핀은 게시글 작성자만 설정할 수 있습니다");
        }
        
        comment.setIsPinned(isPinned);
        comment.setPinnedAt(isPinned ? LocalDateTime.now() : null);
        
        commentRepository.save(comment);
        
        log.info("Comment pin toggled: commentId={}, isPinned={}", commentId, isPinned);
    }
}
```

---

## 📊 통계 및 DTO 클래스

### CommentStatistics.java
```java
package com.routepick.dto.community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentStatistics {
    private Long postId;
    private Long totalComments;
    private Long activeComments;
    private Long totalLikes;
    private Double averageLikesPerComment;
}
```

### UserCommentActivity.java
```java
package com.routepick.dto.community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCommentActivity {
    private Long userId;
    private Long totalComments;
    private Long totalLikesReceived;
    private Long totalLikesGiven;
    private Double averageLikesPerComment;
}
```

### CommentStatusChangedEvent.java
```java
package com.routepick.event;

import com.routepick.common.enums.CommentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommentStatusChangedEvent {
    private final Long commentId;
    private final CommentStatus oldStatus;
    private final CommentStatus newStatus;
    private final Long adminUserId;
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
      like:
        enable-notifications: true
        batch-size: 100  # 대량 처리 배치 크기
      moderation:
        auto-hide-threshold: 5  # 자동 숨김 신고 수
        admin-review-required: true  # 관리자 검토 필요
```

---

## 📈 주요 기능 요약

### 1. 댓글 좋아요 시스템
- **토글 방식**: 클릭으로 좋아요/취소
- **중복 방지**: 사용자당 1회 제한
- **자기 좋아요 방지**: 본인 댓글 제외
- **실시간 카운트**: 즉시 반영
- **알림 연동**: 작성자에게 알림
- **캐싱 최적화**: Redis 캐시 적용

### 2. 댓글 신고 시스템
- **신고 기능**: 부적절한 댓글 신고
- **자동 숨김**: 임계값(5회) 초과시
- **관리자 알림**: 신고 내역 전달
- **상태 관리**: ACTIVE, HIDDEN, DELETED
- **신고 카운트**: 누적 신고 횟수 관리

### 3. 인기 댓글 및 통계
- **인기 댓글**: 좋아요 수 기준 정렬
- **댓글 통계**: 게시글별 댓글 현황
- **사용자 활동**: 개인별 댓글 통계
- **관리자 도구**: 신고된 댓글 관리

### 4. 추가 기능
- **댓글 핀**: 게시글 작성자가 댓글 고정
- **대량 처리**: 비동기 배치 작업
- **상태 변경**: 관리자 댓글 상태 관리
- **사용자별 좋아요 내역**: 개인 활동 추적

---

## ✅ 완료 사항
- ✅ 댓글 좋아요 토글 시스템
- ✅ 댓글 신고 및 자동 숨김
- ✅ 인기 댓글 조회
- ✅ 댓글 통계 및 분석
- ✅ 사용자 활동 통계
- ✅ 관리자 댓글 관리 도구
- ✅ 댓글 핀 기능
- ✅ 대량 처리 및 비동기 작업
- ✅ 이벤트 기반 알림 연동

---

*CommentService 좋아요 및 신고 기능 구현 완료*