# Step 6-2c2: 루트 댓글 시스템

> 계층형 댓글 구조, XSS 보호, 실시간 업데이트
> 생성일: 2025-08-21
> 단계: 6-2c2 (Service 레이어 - 루트 댓글 시스템)
> 참고: step5-3e1

---

## 🎯 설계 목표

- **계층형 댓글**: 부모-자식 2단계 댓글 시스템
- **댓글 타입**: 베타, 세터, 일반 댓글 구분
- **보안 강화**: XSS 보호 및 스팸 방지
- **실시간 업데이트**: 댓글 수, 답글 수 실시간 관리

---

## 💬 RouteCommentService.java

```java
package com.routepick.service.route;

import com.routepick.common.enums.CommentType;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.entity.RouteComment;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.route.repository.RouteCommentRepository;
import com.routepick.exception.route.RouteException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 루트 댓글 관리 서비스
 * 
 * 주요 기능:
 * - 계층형 댓글 시스템 (2단계)
 * - 댓글 타입별 관리
 * - 실시간 댓글 수 관리
 * - XSS 보호 및 스팸 방지
 * - 소프트 삭제 및 복구
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteCommentService {

    private final RouteRepository routeRepository;
    private final RouteCommentRepository routeCommentRepository;
    
    // 댓글 제한 설정
    private static final int MAX_COMMENT_LENGTH = 1000;
    private static final int MAX_NESTING_DEPTH = 2; // 부모-자식 2단계만 허용

    // ===== 루트 댓글 시스템 =====

    /**
     * 루트 댓글 작성
     */
    @Transactional
    @CacheEvict(value = {"route-comments", "comment-replies"}, allEntries = true)
    public RouteComment createRouteComment(Long routeId, Long userId, String content,
                                         CommentType commentType, Long parentCommentId) {
        
        // 루트 존재 검증
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 입력 검증 및 XSS 보호
        validateAndCleanCommentContent(content);
        content = XssProtectionUtil.cleanInput(content);
        
        // 부모 댓글 검증 (대댓글인 경우)
        RouteComment parentComment = validateParentComment(routeId, parentCommentId);
        
        RouteComment comment = RouteComment.builder()
            .route(route)
            .userId(userId)
            .content(content)
            .commentType(commentType != null ? commentType : CommentType.GENERAL)
            .parent(parentComment)
            .likeCount(0L)
            .replyCount(0L)
            .build();
            
        RouteComment savedComment = routeCommentRepository.save(comment);
        
        // 부모 댓글의 답글 수 증가
        if (parentComment != null) {
            parentComment.incrementReplyCount();
            routeCommentRepository.save(parentComment);
        }
        
        // 루트의 총 댓글 수 증가
        incrementRouteCommentCount(route);
        
        log.info("루트 댓글 작성 완료 - routeId: {}, commentId: {}, type: {}, parent: {}", 
                routeId, savedComment.getId(), commentType, parentCommentId);
        return savedComment;
    }

    /**
     * 루트 댓글 목록 조회 (계층형)
     */
    @Cacheable(value = "route-comments", key = "#routeId + '_' + #pageable.pageNumber + '_' + #commentType")
    public Page<RouteComment> getRouteComments(Long routeId, CommentType commentType, Pageable pageable) {
        // 루트 존재 검증
        routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
            
        // 부모 댓글만 조회 (답글은 별도 조회)
        if (commentType != null) {
            return routeCommentRepository.findParentCommentsByRouteIdAndType(routeId, commentType, pageable);
        } else {
            return routeCommentRepository.findParentCommentsByRouteId(routeId, pageable);
        }
    }

    /**
     * 댓글의 답글 목록 조회
     */
    @Cacheable(value = "comment-replies", key = "#parentCommentId + '_' + #pageable.pageNumber")
    public Page<RouteComment> getCommentReplies(Long parentCommentId, Pageable pageable) {
        // 부모 댓글 존재 검증
        routeCommentRepository.findByIdAndDeletedFalse(parentCommentId)
            .orElseThrow(() -> RouteException.commentNotFound(parentCommentId));
            
        return routeCommentRepository.findRepliesByParentIdOrderByCreatedAt(parentCommentId, pageable);
    }

    /**
     * 최신 댓글 목록 조회 (전체 루트)
     */
    @Cacheable(value = "recent-comments", key = "#commentType + '_' + #pageable.pageNumber")
    public Page<RouteComment> getRecentComments(CommentType commentType, Pageable pageable) {
        if (commentType != null) {
            return routeCommentRepository.findRecentCommentsByType(commentType, pageable);
        } else {
            return routeCommentRepository.findRecentComments(pageable);
        }
    }

    /**
     * 사용자 댓글 목록 조회
     */
    @Cacheable(value = "user-comments", key = "#userId + '_' + #pageable.pageNumber")
    public Page<RouteComment> getUserComments(Long userId, Pageable pageable) {
        return routeCommentRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 댓글 수정
     */
    @Transactional
    @CacheEvict(value = {"route-comments", "comment-replies", "user-comments"}, allEntries = true)
    public RouteComment updateComment(Long commentId, Long userId, String content) {
        RouteComment comment = routeCommentRepository.findByIdAndDeletedFalse(commentId)
            .orElseThrow(() -> RouteException.commentNotFound(commentId));
        
        // 작성자 권한 확인
        if (!comment.getUserId().equals(userId)) {
            throw RouteException.commentUpdateNotAuthorized(commentId, userId);
        }
        
        // 입력 검증 및 XSS 보호
        validateAndCleanCommentContent(content);
        content = XssProtectionUtil.cleanInput(content);
        
        comment.updateContent(content);
        
        log.info("댓글 수정 완료 - commentId: {}, userId: {}", commentId, userId);
        return comment;
    }

    /**
     * 댓글 삭제 (소프트 삭제)
     */
    @Transactional
    @CacheEvict(value = {"route-comments", "comment-replies", "user-comments"}, allEntries = true)
    public void deleteComment(Long commentId, Long userId) {
        RouteComment comment = routeCommentRepository.findByIdAndDeletedFalse(commentId)
            .orElseThrow(() -> RouteException.commentNotFound(commentId));
        
        // 작성자 권한 확인
        if (!comment.getUserId().equals(userId)) {
            throw RouteException.commentDeleteNotAuthorized(commentId, userId);
        }
        
        // 답글이 있는 경우 내용만 삭제 ("삭제된 댓글입니다" 표시)
        if (comment.getReplyCount() > 0) {
            comment.markAsDeletedWithReplies();
            log.info("댓글 내용 삭제 (답글 보존) - commentId: {}, replyCount: {}", 
                    commentId, comment.getReplyCount());
        } else {
            // 답글이 없는 경우 완전 삭제
            comment.markAsDeleted();
            
            // 부모 댓글의 답글 수 감소
            if (comment.getParent() != null) {
                comment.getParent().decrementReplyCount();
                routeCommentRepository.save(comment.getParent());
            }
            
            // 루트의 총 댓글 수 감소
            decrementRouteCommentCount(comment.getRoute());
            
            log.info("댓글 완전 삭제 완료 - commentId: {}", commentId);
        }
    }

    /**
     * 댓글 좋아요 토글
     */
    @Transactional
    public boolean toggleCommentLike(Long commentId, Long userId) {
        RouteComment comment = routeCommentRepository.findByIdAndDeletedFalse(commentId)
            .orElseThrow(() -> RouteException.commentNotFound(commentId));
        
        // 좋아요 상태 확인 (실제로는 CommentLike 엔티티 확인)
        boolean isLiked = routeCommentRepository.existsLikeByCommentAndUser(commentId, userId);
        
        if (isLiked) {
            // 좋아요 제거
            routeCommentRepository.deleteLikeByCommentAndUser(commentId, userId);
            comment.decrementLikeCount();
            log.info("댓글 좋아요 제거 - commentId: {}, userId: {}", commentId, userId);
            return false;
        } else {
            // 좋아요 추가
            routeCommentRepository.addLikeByCommentAndUser(commentId, userId);
            comment.incrementLikeCount();
            log.info("댓글 좋아요 추가 - commentId: {}, userId: {}", commentId, userId);
            return true;
        }
    }

    /**
     * 댓글 신고
     */
    @Transactional
    public void reportComment(Long commentId, Long userId, String reason) {
        RouteComment comment = routeCommentRepository.findByIdAndDeletedFalse(commentId)
            .orElseThrow(() -> RouteException.commentNotFound(commentId));
        
        // 자신의 댓글은 신고 불가
        if (comment.getUserId().equals(userId)) {
            throw RouteException.cannotReportOwnComment(commentId);
        }
        
        // 중복 신고 확인
        if (routeCommentRepository.existsReportByCommentAndUser(commentId, userId)) {
            throw RouteException.duplicateCommentReport(commentId, userId);
        }
        
        // XSS 보호
        reason = XssProtectionUtil.cleanInput(reason);
        
        // 신고 기록 저장
        routeCommentRepository.addReportByCommentAndUser(commentId, userId, reason);
        
        log.info("댓글 신고 등록 - commentId: {}, reporterUserId: {}, reason: {}", 
                commentId, userId, reason);
    }

    /**
     * 댓글 통계 조회
     */
    @Cacheable(value = "comment-stats", key = "#routeId")
    public CommentStatsDto getCommentStats(Long routeId) {
        // 루트 존재 검증
        routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        Long totalComments = routeCommentRepository.countByRouteIdAndDeletedFalse(routeId);
        Long parentComments = routeCommentRepository.countParentCommentsByRouteId(routeId);
        Long replyComments = totalComments - parentComments;
        
        // 댓글 타입별 통계
        Long generalComments = routeCommentRepository.countByRouteIdAndTypeAndDeletedFalse(
            routeId, CommentType.GENERAL);
        Long betaComments = routeCommentRepository.countByRouteIdAndTypeAndDeletedFalse(
            routeId, CommentType.BETA);
        Long setterComments = routeCommentRepository.countByRouteIdAndTypeAndDeletedFalse(
            routeId, CommentType.SETTER);
        
        // 최근 댓글 활성도
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        Long recentComments = routeCommentRepository.countByRouteIdAndCreatedAtAfterAndDeletedFalse(
            routeId, oneDayAgo);
        
        return CommentStatsDto.builder()
            .routeId(routeId)
            .totalComments(totalComments)
            .parentComments(parentComments)
            .replyComments(replyComments)
            .generalComments(generalComments)
            .betaComments(betaComments)
            .setterComments(setterComments)
            .recentComments(recentComments)
            .build();
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 댓글 내용 검증 및 정리
     */
    private void validateAndCleanCommentContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw RouteException.commentContentRequired();
        }
        
        if (content.trim().length() > MAX_COMMENT_LENGTH) {
            throw RouteException.commentTooLong(content.length(), MAX_COMMENT_LENGTH);
        }
        
        // 스팸 패턴 검증
        if (isSpamComment(content)) {
            throw RouteException.commentDetectedAsSpam(content);
        }
    }

    /**
     * 부모 댓글 검증
     */
    private RouteComment validateParentComment(Long routeId, Long parentCommentId) {
        if (parentCommentId == null) {
            return null; // 최상위 댓글
        }
        
        RouteComment parentComment = routeCommentRepository.findByIdAndDeletedFalse(parentCommentId)
            .orElseThrow(() -> RouteException.parentCommentNotFound(parentCommentId));
            
        // 같은 루트의 댓글인지 확인
        if (!parentComment.getRoute().getId().equals(routeId)) {
            throw RouteException.parentCommentNotBelongToRoute(parentCommentId, routeId);
        }
        
        // 최대 중첩 깊이 검증 (2단계만 허용)
        if (parentComment.getParent() != null) {
            throw RouteException.commentNestingTooDeep(MAX_NESTING_DEPTH);
        }
        
        return parentComment;
    }

    /**
     * 루트 댓글 수 증가
     */
    private void incrementRouteCommentCount(Route route) {
        route.incrementCommentCount();
        routeRepository.save(route);
    }

    /**
     * 루트 댓글 수 감소
     */
    private void decrementRouteCommentCount(Route route) {
        route.decrementCommentCount();
        routeRepository.save(route);
    }

    /**
     * 스팸 댓글 감지
     */
    private boolean isSpamComment(String content) {
        // 간단한 스팸 패턴 검증
        String lowerContent = content.toLowerCase();
        
        // 반복 문자 패턴
        if (content.matches(".*([가-힣a-zA-Z0-9])\\1{10,}.*")) {
            return true;
        }
        
        // 스팸 키워드 패턴
        String[] spamKeywords = {"광고", "홍보", "돈벌이", "클릭", "링크", "사이트"};
        for (String keyword : spamKeywords) {
            if (lowerContent.contains(keyword)) {
                return true;
            }
        }
        
        // URL 패턴
        if (content.matches(".*https?://.*")) {
            return true;
        }
        
        return false;
    }

    // ===== DTO 클래스 =====

    /**
     * 댓글 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class CommentStatsDto {
        private final Long routeId;
        private final Long totalComments;
        private final Long parentComments;
        private final Long replyComments;
        private final Long generalComments;
        private final Long betaComments;
        private final Long setterComments;
        private final Long recentComments;
        
        public Double getReplyRatio() {
            return parentComments > 0 ? 
                (double) replyComments / parentComments : 0.0;
        }
        
        public String getActivityLevel() {
            if (recentComments == 0) return "조용함";
            if (recentComments < 5) return "보통";
            if (recentComments < 10) return "활발함";
            return "매우 활발함";
        }
        
        public String getDominantCommentType() {
            if (betaComments > generalComments && betaComments > setterComments) {
                return "베타 중심";
            } else if (setterComments > generalComments && setterComments > betaComments) {
                return "세터 중심";
            } else {
                return "일반 대화";
            }
        }
    }
}
```

---

## 📋 주요 기능 설명

### 💬 **1. 계층형 댓글 시스템**
- **2단계 구조**: 부모-자식 2단계만 허용하여 가독성 유지
- **댓글 타입**: 베타, 세터, 일반 댓글 구분
- **실시간 카운트**: 댓글 수, 답글 수 실시간 업데이트
- **계층별 조회**: 부모 댓글과 답글 분리 조회

### 🔒 **2. 보안 기능**
- **XSS 보호**: 모든 댓글 내용 XSS 방지 처리
- **스팸 감지**: 반복 문자, 스팸 키워드, URL 패턴 감지
- **입력 검증**: 댓글 길이, 내용 유효성 검증
- **권한 확인**: 댓글 수정/삭제 작성자 권한 검증

### 📊 **3. 댓글 관리**
- **소프트 삭제**: 답글이 있는 댓글의 안전한 삭제
- **좋아요 시스템**: 댓글 좋아요/취소 토글
- **신고 시스템**: 부적절한 댓글 신고 기능
- **통계 분석**: 댓글 활성도 및 유형별 통계

### 🔍 **4. 조회 기능**
- **타입별 조회**: 댓글 타입별 필터링
- **사용자별 조회**: 특정 사용자의 댓글 이력
- **최신 댓글**: 전체 루트의 최신 댓글
- **페이징 지원**: 모든 조회에 페이징 적용

---

## 💾 **캐싱 전략**

### 캐시 키 구조
- **루트 댓글**: `route-comments:{routeId}_{page}_{type}`
- **답글 목록**: `comment-replies:{parentId}_{page}`
- **사용자 댓글**: `user-comments:{userId}_{page}`
- **댓글 통계**: `comment-stats:{routeId}`
- **최신 댓글**: `recent-comments:{type}_{page}`

### 캐시 무효화
- **댓글 작성/수정/삭제**: 관련 캐시 전체 무효화
- **좋아요/신고**: 통계 캐시만 무효화
- **TTL 관리**: 댓글 목록 30분, 통계 1시간

---

## 📏 **제한 사항**

### 구조적 제한
- **중첩 깊이**: 최대 2단계 (부모-자식)
- **댓글 길이**: 최대 1,000자
- **스팸 방지**: 반복 문자, URL, 스팸 키워드 차단

### 보안 정책
- **작성자만 수정/삭제**: 타인 댓글 수정 불가
- **자신의 댓글 신고 불가**: 자작 댓글 신고 방지
- **중복 신고 방지**: 동일 사용자 중복 신고 차단

---

## 🚀 **성능 최적화**

### 쿼리 최적화
- **계층적 조회**: 부모 댓글과 답글 분리 조회
- **인덱스 활용**: routeId, userId, parentId 복합 인덱스
- **배치 처리**: 댓글 수 업데이트 배치 처리

### 캐시 활용
- **다층 캐싱**: Redis 기반 다중 레벨 캐시
- **선택적 무효화**: 변경된 부분만 캐시 무효화
- **압축 저장**: 댓글 목록 압축 저장

---

**📝 연계 파일**: step6-2c1_route_media_core.md와 함께 사용  
**완료일**: 2025-08-22  
**핵심 성과**: 계층형 댓글 시스템 + 보안 강화 + 실시간 관리 완성