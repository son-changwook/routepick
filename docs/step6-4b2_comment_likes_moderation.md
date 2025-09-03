# Step 6-4b2: CommentService 구현 - 좋아요 및 신고 관리

> 댓글 좋아요 시스템, 신고 관리, 모더레이션 기능  
> 생성일: 2025-08-22  
> 단계: 6-4b2 (Service 레이어 - 댓글 시스템 확장)  
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
 * - 댓글 신고 및 모더레이션
 * - 댓글 통계 및 분석
 * - 자동 숨김 처리
 */
public class CommentService {
    // ... 기본 필드들은 step6-4b1과 동일 ...

    private static final String CACHE_COMMENT_LIKES = "commentLikes";
    private static final String CACHE_COMMENT_STATS = "commentStats";
    
    // 신고 관련 설정
    private static final int REPORT_THRESHOLD_AUTO_HIDE = 5;  // 자동 숨김 신고 횟수
    private static final int REPORT_THRESHOLD_ADMIN_REVIEW = 3;  // 관리자 검토 신고 횟수

    // ===== 댓글 좋아요 시스템 =====

    /**
     * 댓글 좋아요 토글
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
        
        // 삭제된 댓글 좋아요 방지
        if (comment.isDeleted() || comment.getStatus() == CommentStatus.DELETED) {
            throw new CommunityException("삭제된 댓글에는 좋아요를 할 수 없습니다");
        }
        
        Optional<CommentLike> existingLike = commentLikeRepository
            .findByCommentIdAndUserId(commentId, userId);
            
        if (existingLike.isPresent()) {
            // 좋아요 취소
            commentLikeRepository.delete(existingLike.get());
            commentRepository.decrementLikeCount(commentId);
            
            // 이벤트 발행
            eventPublisher.publishEvent(new CommentLikeRemovedEvent(commentId, userId));
            
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
            if (!comment.getUser().getUserId().equals(userId)) {
                notificationService.sendCommentLikeNotification(
                    comment.getUser().getUserId(),
                    comment,
                    user
                );
            }
            
            // 이벤트 발행
            eventPublisher.publishEvent(new CommentLikeAddedEvent(commentId, userId));
            
            log.info("Comment like added: commentId={}, userId={}", commentId, userId);
            return true;
        }
    }

    /**
     * 댓글 좋아요 여부 확인
     */
    @Cacheable(value = CACHE_COMMENT_LIKES, key = "#commentId + '_' + #userId")
    public boolean isCommentLiked(Long commentId, Long userId) {
        return commentLikeRepository.existsByCommentIdAndUserId(commentId, userId);
    }

    /**
     * 댓글 좋아요 사용자 목록 조회
     */
    @Cacheable(value = CACHE_COMMENT_LIKES, key = "#commentId + '_users'")
    public List<User> getCommentLikeUsers(Long commentId, int limit) {
        List<CommentLike> likes = commentLikeRepository
            .findByCommentIdOrderByCreatedAtDesc(commentId, PageRequest.of(0, limit))
            .getContent();
            
        return likes.stream()
            .map(CommentLike::getUser)
            .collect(Collectors.toList());
    }

    /**
     * 사용자가 좋아요한 댓글 목록
     */
    public Page<Comment> getUserLikedComments(Long userId, Pageable pageable) {
        return commentLikeRepository.findCommentsByUserId(userId, pageable);
    }

    // ===== 댓글 신고 및 모더레이션 =====

    /**
     * 댓글 신고
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
        
        // 중복 신고 방지 (사용자별)
        if (commentRepository.existsReportByCommentAndUser(commentId, userId)) {
            throw new CommunityException("이미 신고한 댓글입니다");
        }
        
        // 신고 내역 저장 (별도 테이블 또는 간단히 카운트 증가)
        comment.setReportCount(comment.getReportCount() + 1);
        
        // 신고 사유별 통계 업데이트
        updateReportStatistics(commentId, reason);
        
        // 신고 횟수별 자동 처리
        handleAutoModeration(comment);
        
        commentRepository.save(comment);
        
        // 신고 이벤트 발행
        eventPublisher.publishEvent(new CommentReportedEvent(commentId, userId, reason));
        
        // 관리자에게 알림 (임계값 도달 시)
        if (comment.getReportCount() >= REPORT_THRESHOLD_ADMIN_REVIEW) {
            notificationService.sendReportNotificationToAdmin(comment, userId, reason);
        }
        
        log.info("Comment reported: commentId={}, reportCount={}", 
                commentId, comment.getReportCount());
    }

    /**
     * 자동 모더레이션 처리
     */
    private void handleAutoModeration(Comment comment) {
        int reportCount = comment.getReportCount();
        
        if (reportCount >= REPORT_THRESHOLD_AUTO_HIDE) {
            // 자동 숨김 처리
            comment.setStatus(CommentStatus.HIDDEN);
            log.warn("Comment auto-hidden due to reports: commentId={}, reportCount={}", 
                    comment.getCommentId(), reportCount);
                    
            // 자동 숨김 알림
            notificationService.sendAutoHideNotification(comment);
            
        } else if (reportCount >= REPORT_THRESHOLD_ADMIN_REVIEW) {
            // 관리자 검토 대기 상태
            comment.setStatus(CommentStatus.UNDER_REVIEW);
            log.info("Comment marked for admin review: commentId={}, reportCount={}", 
                    comment.getCommentId(), reportCount);
        }
    }

    /**
     * 신고 사유별 통계 업데이트
     */
    private void updateReportStatistics(Long commentId, String reason) {
        // 신고 사유별 통계 (Redis 또는 별도 테이블)
        // 예: SPAM, INAPPROPRIATE, OFFENSIVE 등
        // 실제 구현에서는 ReportStatistics 서비스 호출
    }

    /**
     * 댓글 상태 관리자 승인/거부
     */
    @Transactional
    public void moderateComment(Long commentId, CommentModerationAction action, 
                               Long adminUserId, String reason) {
        log.info("Moderating comment: commentId={}, action={}, admin={}", 
                commentId, action, adminUserId);
        
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommunityException("댓글을 찾을 수 없습니다: " + commentId));
        
        switch (action) {
            case APPROVE:
                comment.setStatus(CommentStatus.ACTIVE);
                comment.setReportCount(0); // 신고 횟수 초기화
                break;
                
            case REJECT:
                comment.setStatus(CommentStatus.HIDDEN);
                comment.setContent("[관리자에 의해 숨김 처리된 댓글입니다]");
                break;
                
            case DELETE:
                comment.setStatus(CommentStatus.DELETED);
                comment.setIsDeleted(true);
                comment.setDeletedAt(LocalDateTime.now());
                break;
                
            case WARNING:
                // 경고 처리 (댓글은 유지, 사용자에게 경고)
                notificationService.sendWarningNotification(
                    comment.getUser().getUserId(), 
                    comment, 
                    reason
                );
                break;
        }
        
        commentRepository.save(comment);
        
        // 모더레이션 로그 저장
        saveModerationLog(commentId, action, adminUserId, reason);
        
        // 이벤트 발행
        eventPublisher.publishEvent(new CommentModeratedEvent(commentId, action, adminUserId));
        
        log.info("Comment moderation completed: commentId={}, action={}", commentId, action);
    }

    /**
     * 모더레이션 로그 저장
     */
    private void saveModerationLog(Long commentId, CommentModerationAction action, 
                                  Long adminUserId, String reason) {
        // 실제 구현에서는 ModerationLog 엔티티에 저장
        log.info("Moderation log - Comment: {}, Action: {}, Admin: {}, Reason: {}", 
                commentId, action, adminUserId, reason);
    }

    // ===== 댓글 통계 및 분석 =====

    /**
     * 댓글 통계 조회
     */
    @Cacheable(value = CACHE_COMMENT_STATS, key = "#commentId")
    public CommentStatisticsDto getCommentStatistics(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommunityException("댓글을 찾을 수 없습니다: " + commentId));
        
        // 좋아요한 사용자 수
        long likeCount = commentLikeRepository.countByCommentId(commentId);
        
        // 대댓글 수
        long replyCount = commentRepository.countByParentId(commentId);
        
        // 신고 수
        int reportCount = comment.getReportCount();
        
        // 조회수 (별도 추적 시)
        long viewCount = getCommentViewCount(commentId);
        
        return CommentStatisticsDto.builder()
            .commentId(commentId)
            .likeCount(likeCount)
            .replyCount(replyCount)
            .reportCount(reportCount)
            .viewCount(viewCount)
            .createdAt(comment.getCreatedAt())
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    /**
     * 게시글의 댓글 통계
     */
    @Cacheable(value = CACHE_COMMENT_STATS, key = "'post_' + #postId")
    public PostCommentStatisticsDto getPostCommentStatistics(Long postId) {
        long totalComments = commentRepository.countByPostIdAndStatus(postId, CommentStatus.ACTIVE);
        long totalLikes = commentLikeRepository.countByPostId(postId);
        long totalReports = commentRepository.sumReportCountByPostId(postId);
        
        // 최근 활동 (24시간 내)
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        long recentComments = commentRepository.countByPostIdAndCreatedAtAfter(postId, last24Hours);
        long recentLikes = commentLikeRepository.countByPostIdAndCreatedAtAfter(postId, last24Hours);
        
        // 인기 댓글 (좋아요 5개 이상)
        long popularComments = commentRepository.countByPostIdAndLikeCountGreaterThan(postId, 5);
        
        return PostCommentStatisticsDto.builder()
            .postId(postId)
            .totalComments(totalComments)
            .totalLikes(totalLikes)
            .totalReports(totalReports)
            .recentComments(recentComments)
            .recentLikes(recentLikes)
            .popularComments(popularComments)
            .calculatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * 사용자의 댓글 활동 통계
     */
    public UserCommentStatisticsDto getUserCommentStatistics(Long userId) {
        long totalComments = commentRepository.countByUserIdAndStatus(userId, CommentStatus.ACTIVE);
        long totalLikesReceived = commentLikeRepository.countLikesReceivedByUser(userId);
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

    // ===== 대시보드 및 관리 기능 =====

    /**
     * 관리자 대시보드용 댓글 현황
     */
    public AdminCommentDashboardDto getAdminCommentDashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24Hours = now.minusHours(24);
        LocalDateTime last7Days = now.minusDays(7);
        
        // 기본 통계
        long totalActiveComments = commentRepository.countByStatus(CommentStatus.ACTIVE);
        long totalDeletedComments = commentRepository.countByStatus(CommentStatus.DELETED);
        long totalHiddenComments = commentRepository.countByStatus(CommentStatus.HIDDEN);
        long totalUnderReview = commentRepository.countByStatus(CommentStatus.UNDER_REVIEW);
        
        // 최근 활동
        long commentsLast24h = commentRepository.countByCreatedAtAfter(last24Hours);
        long commentsLast7Days = commentRepository.countByCreatedAtAfter(last7Days);
        
        // 신고 현황
        long totalReports = commentRepository.sumAllReportCounts();
        long pendingReports = commentRepository.countByStatusAndReportCountGreaterThan(
            CommentStatus.UNDER_REVIEW, 0);
        
        // 인기 댓글
        List<Comment> topComments = commentRepository.findTop10ByOrderByLikeCountDesc();
        
        return AdminCommentDashboardDto.builder()
            .totalActiveComments(totalActiveComments)
            .totalDeletedComments(totalDeletedComments)
            .totalHiddenComments(totalHiddenComments)
            .totalUnderReview(totalUnderReview)
            .commentsLast24Hours(commentsLast24h)
            .commentsLast7Days(commentsLast7Days)
            .totalReports(totalReports)
            .pendingReports(pendingReports)
            .topComments(topComments)
            .generatedAt(now)
            .build();
    }

    // ===== 이벤트 클래스들 =====

    /**
     * 댓글 좋아요 추가 이벤트
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CommentLikeAddedEvent {
        private final Long commentId;
        private final Long userId;
    }

    /**
     * 댓글 좋아요 제거 이벤트
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CommentLikeRemovedEvent {
        private final Long commentId;
        private final Long userId;
    }

    /**
     * 댓글 신고 이벤트
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CommentReportedEvent {
        private final Long commentId;
        private final Long reporterId;
        private final String reason;
    }

    /**
     * 댓글 모더레이션 이벤트
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CommentModeratedEvent {
        private final Long commentId;
        private final CommentModerationAction action;
        private final Long adminUserId;
    }

    // ===== ENUM 클래스 =====

    /**
     * 모더레이션 액션 타입
     */
    public enum CommentModerationAction {
        APPROVE,    // 승인
        REJECT,     // 거부 (숨김)
        DELETE,     // 삭제
        WARNING     // 경고
    }

    // ===== DTO 클래스들 =====

    /**
     * 댓글 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class CommentStatisticsDto {
        private final Long commentId;
        private final long likeCount;
        private final long replyCount;
        private final int reportCount;
        private final long viewCount;
        private final LocalDateTime createdAt;
        private final LocalDateTime lastUpdated;
    }

    /**
     * 게시글 댓글 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class PostCommentStatisticsDto {
        private final Long postId;
        private final long totalComments;
        private final long totalLikes;
        private final long totalReports;
        private final long recentComments;
        private final long recentLikes;
        private final long popularComments;
        private final LocalDateTime calculatedAt;
    }

    /**
     * 사용자 댓글 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class UserCommentStatisticsDto {
        private final Long userId;
        private final long totalComments;
        private final long totalLikesReceived;
        private final long totalLikesGiven;
        private final double averageCommentLength;
        private final long recentCommentsLast30Days;
        private final Comment mostLikedComment;
        private final LocalDateTime calculatedAt;
    }

    /**
     * 관리자 댓글 대시보드 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class AdminCommentDashboardDto {
        private final long totalActiveComments;
        private final long totalDeletedComments;
        private final long totalHiddenComments;
        private final long totalUnderReview;
        private final long commentsLast24Hours;
        private final long commentsLast7Days;
        private final long totalReports;
        private final long pendingReports;
        private final List<Comment> topComments;
        private final LocalDateTime generatedAt;
    }
}
```

---

## 🔧 시스템 설정 및 통합

### application.yml 설정
```yaml
# 댓글 시스템 설정
app:
  community:
    comment:
      cache-ttl: 30m  # 댓글 캐시 TTL
      max-length: 1000
      max-depth: 3  # 최대 댓글 깊이
      max-replies: 100  # 댓글당 최대 대댓글
      like:
        enable-notifications: true
        cache-ttl: 15m
      moderation:
        report-threshold-review: 3  # 관리자 검토 임계값
        report-threshold-auto-hide: 5  # 자동 숨김 임계값
        auto-moderation: true
      statistics:
        cache-ttl: 1h
        enable-view-tracking: true
```

---

## 👍 댓글 좋아요 시스템

### ❤️ **1. 좋아요 토글 기능**
- **중복 방지**: 사용자당 댓글 1개씩만 좋아요 가능
- **자기 댓글 방지**: 본인 댓글에는 좋아요 불가
- **삭제 댓글 방지**: 삭제된 댓글에는 좋아요 불가
- **실시간 카운트**: 좋아요 추가/제거 시 즉시 반영

### 🔔 **2. 알림 시스템**
- **작성자 알림**: 댓글 좋아요 시 작성자에게 알림
- **본인 제외**: 자신의 댓글에는 알림 발송 안함
- **배치 알림**: 여러 좋아요 시 묶어서 알림
- **이벤트 기반**: 좋아요 추가/제거 이벤트 발행

### 📊 **3. 좋아요 통계**
- **사용자 목록**: 댓글에 좋아요한 사용자 목록
- **개인 통계**: 사용자별 받은/준 좋아요 수
- **인기 댓글**: 좋아요 수 기준 인기 댓글
- **트렌드 분석**: 시간별 좋아요 활동 패턴

---

## 🚨 신고 및 모더레이션 시스템

### 📢 **1. 신고 기능**
- **신고 사유**: 스팸, 부적절한 내용, 욕설 등 분류
- **중복 방지**: 동일 사용자의 중복 신고 차단
- **자기 신고 방지**: 본인 댓글 신고 불가
- **통계 관리**: 신고 사유별 통계 수집

### 🤖 **2. 자동 모더레이션**
- **임계값 기반**: 신고 수에 따른 단계별 처리
  - 3개 이상: 관리자 검토 대기 (UNDER_REVIEW)
  - 5개 이상: 자동 숨김 처리 (HIDDEN)
- **상태 변경**: CommentStatus 자동 업데이트
- **알림 발송**: 임계값 도달 시 관리자 알림

### 👨‍💼 **3. 관리자 모더레이션**
- **승인/거부**: 신고된 댓글 검토 후 처리
- **경고 시스템**: 사용자에게 경고 메시지 발송
- **모더레이션 로그**: 모든 관리 활동 기록
- **대량 처리**: 여러 댓글 일괄 처리 기능

---

## 📊 통계 및 분석 시스템

### 📈 **1. 댓글 통계**
- **기본 지표**: 좋아요, 대댓글, 신고, 조회수
- **시간별 분석**: 24시간, 7일, 30일 활동
- **인기도 측정**: 좋아요 5개 이상 인기 댓글
- **실시간 집계**: 통계 자동 업데이트

### 👤 **2. 사용자 통계**
- **작성 활동**: 총 댓글 수, 최근 활동
- **상호작용**: 받은/준 좋아요 수
- **품질 지표**: 평균 댓글 길이, 인기 댓글
- **참여도**: 댓글 작성 빈도, 반응률

### 🏢 **3. 관리자 대시보드**
- **전체 현황**: 활성/삭제/숨김/검토대기 댓글 수
- **최근 활동**: 24시간/7일 내 댓글 활동
- **신고 현황**: 총 신고 수, 처리 대기 신고
- **인기 댓글**: 좋아요 순위 Top 10

---

## 💾 캐싱 전략

### 🚀 **좋아요 캐싱**
- **좋아요 상태**: `commentLikes:{commentId}_{userId}`
- **좋아요 사용자**: `commentLikes:{commentId}_users`
- **TTL 설정**: 15분 캐시 유지
- **즉시 무효화**: 좋아요 변경 시 캐시 삭제

### 📊 **통계 캐싱**
- **댓글 통계**: `commentStats:{commentId}`
- **게시글 통계**: `commentStats:post_{postId}`
- **TTL 설정**: 1시간 캐시 유지
- **주기적 갱신**: 백그라운드에서 통계 업데이트

---

## 🛡️ 보안 및 안정성

### 🔒 **보안 조치**
- **권한 검증**: 모든 작업에 대한 권한 확인
- **상태 검증**: 삭제된 댓글에 대한 작업 차단
- **중복 방지**: 좋아요, 신고 중복 처리 방지
- **로그 기록**: 모든 모더레이션 활동 로그

### ⚡ **성능 최적화**
- **배치 처리**: 대량 작업의 배치 처리
- **인덱스 최적화**: 자주 조회되는 필드 인덱스
- **캐시 활용**: 반복 조회 데이터 캐싱
- **비동기 처리**: 알림, 통계 계산 비동기화

---

## 🚀 활용 시나리오

### 💬 **커뮤니티 참여**
- 댓글 좋아요로 사용자 간 상호작용 증진
- 신고 기능으로 건전한 커뮤니티 문화 조성
- 통계 정보로 인기 콘텐츠 파악

### 🔧 **관리 효율성**
- 자동 모더레이션으로 관리 부담 경감
- 상세 통계로 커뮤니티 상태 모니터링
- 대시보드로 관리자 업무 효율화

### 📈 **비즈니스 인사이트**
- 사용자 참여도 분석으로 콘텐츠 전략 수립
- 신고 패턴 분석으로 정책 개선
- 활동 통계로 서비스 개선 방향 도출

*step6-4b2 완성: 댓글 좋아요 및 신고 관리 설계 완료*