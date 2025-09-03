# step6-4b2a_comment_likes_system.md

> 댓글 좋아요 시스템, 사용자 통계, 관리 기능  
> 생성일: 2025-08-22  
> 단계: 6-4b2a (Service 레이어 - 댓글 좋아요 및 통계)  
> 연관: step6-4b1_comment_crud_hierarchy.md

---

## 🎯 설계 목표

- **댓글 좋아요**: CommentLike 엔티티 기반 좋아요 토글 시스템
- **신고 관리**: 부적절한 댓글 신고 및 자동 숨김 처리
- **모더레이션**: 관리자 알림, 상태 관리, 임계값 기반 자동 처리
- **통계 및 분석**: 댓글 활동 통계, 사용자 참여도 분석
- **캐싱 최적화**: 좋아요 상태 및 통계 정보 캐싱

---

## 👍 CommentService - 좋아요 및 신고 관리 확장

### CommentService.java (Part 2 - 좋아요 및 모더레이션)
```java
// 앞의 import 구문들은 step6-4b1과 동일

/**
 * 댓글 좋아요 및 신고 관리 확장 서비스
 * 
 * 확장 기능:
 * - 댓글 좋아요 토글 시스템
 * - 부적절한 댓글 신고 관리
 * - 모더레이션 및 자동 처리
 * - 사용자별 댓글 통계
 * - 관리자 대시보드 지원
 */
public class CommentService {

    // ... 이전 메서드들은 step6-4b1과 동일

    // ===== 댓글 좋아요 시스템 =====

    /**
     * 댓글 좋아요 토글 (추가/제거)
     * 
     * @param commentId 댓글 ID
     * @param userId 사용자 ID
     * @return 좋아요 상태 결과
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "comment", key = "#commentId"),
        @CacheEvict(value = "comment_likes", key = "#commentId + '_' + #userId")
    })
    public CommentLikeResultDto toggleCommentLike(Long commentId, Long userId) {
        log.info("Toggling comment like: commentId={}, userId={}", commentId, userId);

        // 댓글 존재 및 접근 권한 확인
        Comment comment = getCommentEntity(commentId);
        validateCommentAccess(comment, userId);

        // 기존 좋아요 확인
        Optional<CommentLike> existingLike = commentLikeRepository.findByCommentIdAndUserId(commentId, userId);

        if (existingLike.isPresent()) {
            // 좋아요 제거
            commentLikeRepository.delete(existingLike.get());
            comment.decrementLikeCount();
            commentRepository.save(comment);

            // 이벤트 발행
            eventPublisher.publishEvent(new CommentLikeRemovedEvent(commentId, userId));

            log.info("Comment like removed: commentId={}, userId={}", commentId, userId);
            return CommentLikeResultDto.builder()
                .commentId(commentId)
                .userId(userId)
                .liked(false)
                .totalLikes(comment.getLikeCount())
                .actionPerformed(true)
                .message("좋아요가 취소되었습니다")
                .build();
        } else {
            // 좋아요 추가
            CommentLike newLike = CommentLike.builder()
                .comment(comment)
                .userId(userId)
                .build();
            
            commentLikeRepository.save(newLike);
            comment.incrementLikeCount();
            commentRepository.save(comment);

            // 이벤트 발행
            eventPublisher.publishEvent(new CommentLikeAddedEvent(commentId, userId));

            log.info("Comment like added: commentId={}, userId={}", commentId, userId);
            return CommentLikeResultDto.builder()
                .commentId(commentId)
                .userId(userId)
                .liked(true)
                .totalLikes(comment.getLikeCount())
                .actionPerformed(true)
                .message("좋아요가 추가되었습니다")
                .build();
        }
    }

    /**
     * 댓글 좋아요 상태 확인
     * 
     * @param commentId 댓글 ID
     * @param userId 사용자 ID
     * @return 좋아요 여부
     */
    @Cacheable(value = "comment_likes", key = "#commentId + '_' + #userId")
    public boolean isCommentLiked(Long commentId, Long userId) {
        if (userId == null) {
            return false;
        }
        return commentLikeRepository.existsByCommentIdAndUserId(commentId, userId);
    }

    /**
     * 댓글 좋아요 목록 조회 (관리자용)
     * 
     * @param commentId 댓글 ID
     * @param pageable 페이징 정보
     * @return 좋아요한 사용자 목록
     */
    public Page<CommentLikeDetailDto> getCommentLikes(Long commentId, Pageable pageable) {
        log.debug("Getting comment likes: commentId={}", commentId);
        
        Page<CommentLike> likes = commentLikeRepository.findByCommentIdOrderByCreatedAtDesc(commentId, pageable);
        
        return likes.map(like -> CommentLikeDetailDto.builder()
            .commentId(commentId)
            .userId(like.getUserId())
            .likedAt(like.getCreatedAt())
            .build());
    }

    // ===== 댓글 신고 시스템 =====

    /**
     * 댓글 신고
     * 
     * @param commentId 신고할 댓글 ID
     * @param reporterId 신고자 ID
     * @param reason 신고 사유
     * @return 신고 처리 결과
     */
    @Transactional
    public CommentReportResultDto reportComment(Long commentId, Long reporterId, CommentReportReason reason) {
        log.info("Reporting comment: commentId={}, reporterId={}, reason={}", commentId, reporterId, reason);

        Comment comment = getCommentEntity(commentId);
        
        // 중복 신고 방지
        if (commentRepository.hasUserReportedComment(commentId, reporterId)) {
            return CommentReportResultDto.builder()
                .success(false)
                .message("이미 신고한 댓글입니다")
                .build();
        }

        // 본인 댓글 신고 방지
        if (comment.getUserId().equals(reporterId)) {
            return CommentReportResultDto.builder()
                .success(false)
                .message("본인이 작성한 댓글은 신고할 수 없습니다")
                .build();
        }

        // 신고 카운트 증가
        comment.incrementReportCount();
        commentRepository.save(comment);

        // 신고 임계값 확인 (10회 이상 신고시 자동 숨김)
        if (comment.getReportCount() >= REPORT_THRESHOLD) {
            comment.setStatus(CommentStatus.UNDER_REVIEW);
            comment.setModerationReason("다수 신고로 인한 자동 숨김 처리");
            commentRepository.save(comment);

            // 관리자에게 알림 발송
            sendModerationAlert(comment);
        }

        // 신고 이벤트 발행
        eventPublisher.publishEvent(new CommentReportedEvent(commentId, reporterId, reason));

        log.info("Comment reported successfully: commentId={}, reportCount={}", 
                commentId, comment.getReportCount());

        return CommentReportResultDto.builder()
            .success(true)
            .message("신고가 접수되었습니다. 검토 후 적절한 조치를 취하겠습니다")
            .currentReportCount(comment.getReportCount())
            .build();
    }

    // ===== 사용자 댓글 통계 =====

    /**
     * 사용자별 댓글 통계
     * 
     * @param userId 사용자 ID
     * @return 댓글 활동 통계
     */
    @Cacheable(value = "user_comment_stats", key = "#userId")
    public UserCommentStatisticsDto getUserCommentStatistics(Long userId) {
        log.debug("Getting user comment statistics: userId={}", userId);

        // 기본 통계
        long totalComments = commentRepository.countByUserIdAndStatus(userId, CommentStatus.ACTIVE);
        long totalLikesReceived = commentLikeRepository.countByCommentUserId(userId);
        long totalLikesGiven = commentLikeRepository.countByUserId(userId);
        
        // 평균 댓글 길이
        Double avgCommentLength = commentRepository.getAverageCommentLengthByUser(userId);
        
        // 최근 30일 활동
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        long recentComments = commentRepository.countByUserIdAndCreatedAtAfter(userId, last30Days);
        
        // 가장 인기있었던 댓글
        Comment mostLikedComment = commentRepository.findMostLikedCommentByUser(userId);
        
        return UserCommentStatisticsDto.builder()
            .userId(userId)
            .totalComments(totalComments)
            .totalLikesReceived(totalLikesReceived)
            .totalLikesGiven(totalLikesGiven)
            .averageCommentLength(avgCommentLength != null ? avgCommentLength : 0.0)
            .recentCommentsLast30Days(recentComments)
            .mostLikedComment(mostLikedComment)
            .calculatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * 댓글 조회수 추적
     */
    private long getCommentViewCount(Long commentId) {
        // 실제 구현에서는 Redis나 별도 테이블에서 조회수 관리
        return 0L; // 플레이스홀더
    }