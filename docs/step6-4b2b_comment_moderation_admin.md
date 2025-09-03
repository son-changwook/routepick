# step6-4b2b_comment_moderation_admin.md

## 🚨 관리자 대시보드 및 모더레이션 시스템

### 관리자 댓글 현황 대시보드

```java
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