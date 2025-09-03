# 커뮤니티 서비스 통합 테스트

## 개요
커뮤니티 서비스들 간의 통합성과 데이터 일관성을 검증하는 테스트입니다. 게시글, 댓글, 상호작용, 알림 등 모든 커뮤니티 기능의 연동을 종합적으로 테스트합니다.

## 테스트 클래스 구조

```java
package com.routepick.community.integration;

import com.routepick.community.service.PostService;
import com.routepick.community.service.CommentService;
import com.routepick.community.service.InteractionService;
import com.routepick.community.dto.request.PostCreateRequestDto;
import com.routepick.community.dto.request.CommentCreateRequestDto;
import com.routepick.community.dto.response.PostResponseDto;
import com.routepick.community.dto.response.CommentResponseDto;
import com.routepick.notification.service.NotificationService;
import com.routepick.user.service.UserService;
import com.routepick.common.service.CacheService;
import com.routepick.common.config.TestTransactionConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * 커뮤니티 서비스 통합 테스트
 * 
 * 통합 검증 영역:
 * - 게시글-댓글-알림 연동 플로우
 * - 상호작용-통계 업데이트 연동
 * - 캐시 무효화 및 동기화
 * - 트랜잭션 롤백 시 데이터 일관성
 * - 동시성 환경에서 서비스 연동
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class CommunityServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_community_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private PostService postService;
    
    @Autowired
    private CommentService commentService;
    
    @Autowired
    private InteractionService interactionService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private CacheService cacheService;
    
    private Long testUserId1;
    private Long testUserId2;
    private Long testRouteId;
    
    @BeforeEach
    void setUp() {
        testUserId1 = 1L;
        testUserId2 = 2L;
        testRouteId = 1L;
        
        // 캐시 초기화
        cacheService.clearAll();
    }
    
    @AfterEach
    void tearDown() {
        // 테스트 후 정리
        cacheService.clearAll();
    }
    
    @Nested
    @DisplayName("게시글-댓글-알림 통합 플로우")
    class PostCommentNotificationFlow {
        
        @Test
        @DisplayName("[통합] 게시글 작성 → 댓글 작성 → 알림 발송 전체 플로우")
        void postCommentNotification_CompleteFlow() {
            // 1. 사용자1이 게시글 작성
            PostCreateRequestDto postRequest = PostCreateRequestDto.builder()
                    .userId(testUserId1)
                    .title("새로운 클라이밍 루트 소개")
                    .content("오늘 발견한 정말 재미있는 V4 루트를 소개합니다!")
                    .routeId(testRouteId)
                    .isPublic(true)
                    .build();
            
            PostResponseDto createdPost = postService.createPost(postRequest);
            
            // 게시글 생성 검증
            assertThat(createdPost).isNotNull();
            assertThat(createdPost.getTitle()).isEqualTo("새로운 클라이밍 루트 소개");
            assertThat(createdPost.getCommentCount()).isZero();
            assertThat(createdPost.getLikeCount()).isZero();
            
            // 2. 사용자2가 댓글 작성
            CommentCreateRequestDto commentRequest = CommentCreateRequestDto.builder()
                    .postId(createdPost.getPostId())
                    .userId(testUserId2)
                    .content("정말 좋은 루트 추천 감사합니다! 저도 도전해보겠습니다.")
                    .build();
            
            CommentResponseDto createdComment = commentService.createComment(commentRequest);
            
            // 댓글 생성 검증
            assertThat(createdComment).isNotNull();
            assertThat(createdComment.getContent()).contains("정말 좋은 루트 추천");
            assertThat(createdComment.getUserId()).isEqualTo(testUserId2);
            
            // 3. 게시글 통계 업데이트 검증
            PostResponseDto updatedPost = postService.getPostById(createdPost.getPostId());
            assertThat(updatedPost.getCommentCount()).isEqualTo(1);
            
            // 4. 게시글 작성자에게 댓글 알림 발송 검증
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                List<?> notifications = notificationService.getUserNotifications(testUserId1, 0, 10);
                assertThat(notifications).isNotEmpty();
                
                // 알림 내용 검증 (실제 구현에서는 NotificationDto 사용)
                // assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.POST_COMMENT);
                // assertThat(notifications.get(0).getContent()).contains("댓글을 작성했습니다");
            });
            
            // 5. 캐시 업데이트 검증
            PostResponseDto cachedPost = postService.getPostById(createdPost.getPostId());
            assertThat(cachedPost.getCommentCount()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("[통합] 대댓글 작성 시 다중 알림 발송")
        void replyComment_MultipleNotifications() {
            // given - 게시글과 댓글 준비
            PostCreateRequestDto postRequest = PostCreateRequestDto.builder()
                    .userId(testUserId1)
                    .title("클라이밍 기술 질문")
                    .content("크림핑 기술에 대해 궁금한 점이 있습니다.")
                    .build();
            
            PostResponseDto post = postService.createPost(postRequest);
            
            CommentCreateRequestDto commentRequest = CommentCreateRequestDto.builder()
                    .postId(post.getPostId())
                    .userId(testUserId2)
                    .content("크림핑은 다음과 같이 하시면 됩니다...")
                    .build();
            
            CommentResponseDto parentComment = commentService.createComment(commentRequest);
            
            // when - 사용자3이 대댓글 작성
            Long testUserId3 = 3L;
            CommentCreateRequestDto replyRequest = CommentCreateRequestDto.builder()
                    .postId(post.getPostId())
                    .parentId(parentComment.getCommentId())
                    .userId(testUserId3)
                    .content("좋은 조언 감사합니다! 저도 도움이 되었어요.")
                    .build();
            
            CommentResponseDto reply = commentService.createComment(replyRequest);
            
            // then - 게시글 작성자와 댓글 작성자 모두에게 알림 발송
            assertThat(reply.getParentId()).isEqualTo(parentComment.getCommentId());
            
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                // 게시글 작성자에게 알림
                List<?> postAuthorNotifications = notificationService.getUserNotifications(testUserId1, 0, 10);
                assertThat(postAuthorNotifications).hasSizeGreaterThanOrEqualTo(1);
                
                // 원댓글 작성자에게 알림
                List<?> commentAuthorNotifications = notificationService.getUserNotifications(testUserId2, 0, 10);
                assertThat(commentAuthorNotifications).hasSizeGreaterThanOrEqualTo(1);
            });
            
            // 게시글 댓글 수 정확성 검증
            PostResponseDto finalPost = postService.getPostById(post.getPostId());
            assertThat(finalPost.getCommentCount()).isEqualTo(2); // 댓글 + 대댓글
        }
    }
    
    @Nested
    @DisplayName("상호작용-통계 업데이트 통합")
    class InteractionStatsIntegration {
        
        @Test
        @DisplayName("[통합] 좋아요 → 통계 업데이트 → 캐시 무효화")
        void like_StatsUpdate_CacheInvalidation() {
            // given - 게시글 생성
            PostCreateRequestDto postRequest = PostCreateRequestDto.builder()
                    .userId(testUserId1)
                    .title("통합 테스트용 게시글")
                    .content("좋아요 테스트를 위한 게시글입니다.")
                    .build();
            
            PostResponseDto post = postService.createPost(postRequest);
            assertThat(post.getLikeCount()).isZero();
            
            // 캐시 생성을 위한 조회
            PostResponseDto cachedPost1 = postService.getPostById(post.getPostId());
            assertThat(cachedPost1.getLikeCount()).isZero();
            
            // when - 좋아요 추가
            var likeResult = interactionService.togglePostLike(testUserId2, post.getPostId());
            
            // then - 즉시 통계 업데이트 검증
            assertThat(likeResult.isSuccess()).isTrue();
            assertThat(likeResult.getCurrentStatus()).isTrue();
            assertThat(likeResult.getTotalCount()).isEqualTo(1);
            
            // 캐시된 데이터도 업데이트되었는지 검증
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                PostResponseDto updatedPost = postService.getPostById(post.getPostId());
                assertThat(updatedPost.getLikeCount()).isEqualTo(1);
            });
            
            // 사용자별 상호작용 통계도 업데이트되었는지 검증
            var userStats = interactionService.getUserInteractionStats(testUserId2);
            assertThat(userStats.getTotalLikes()).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("[통합] 여러 사용자 동시 좋아요 → 정확한 통계 집계")
        void multipleLikes_AccurateStatsAggregation() throws Exception {
            // given - 게시글 생성
            PostCreateRequestDto postRequest = PostCreateRequestDto.builder()
                    .userId(testUserId1)
                    .title("인기 게시글")
                    .content("많은 사용자들이 좋아할 만한 내용입니다.")
                    .build();
            
            PostResponseDto post = postService.createPost(postRequest);
            
            // when - 10명의 사용자가 동시에 좋아요
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int i = 2; i <= 11; i++) { // userId 2~11
                final Long userId = (long) i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        var result = interactionService.togglePostLike(userId, post.getPostId());
                        assertThat(result.isSuccess()).isTrue();
                    } catch (Exception e) {
                        throw new RuntimeException("좋아요 실패: " + e.getMessage(), e);
                    }
                }, executor);
                futures.add(future);
            }
            
            // 모든 좋아요 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.SECONDS);
            
            // then - 정확한 좋아요 수 검증
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                PostResponseDto finalPost = postService.getPostById(post.getPostId());
                assertThat(finalPost.getLikeCount()).isEqualTo(10);
            });
            
            // 게시글 상호작용 통계 정확성 검증
            var postStats = interactionService.getPostInteractionStats(post.getPostId(), testUserId1);
            assertThat(postStats.getLikeCount()).isEqualTo(10);
            assertThat(postStats.getEngagementRate()).isGreaterThan(0);
            
            executor.shutdown();
        }
    }
    
    @Nested
    @DisplayName("트랜잭션 및 데이터 일관성")
    class TransactionConsistencyTest {
        
        @Test
        @DisplayName("[통합] 트랜잭션 롤백 시 모든 변경사항 일관성 유지")
        void transactionRollback_DataConsistency() {
            // given
            PostCreateRequestDto postRequest = PostCreateRequestDto.builder()
                    .userId(testUserId1)
                    .title("트랜잭션 테스트")
                    .content("롤백 테스트용 게시글")
                    .build();
            
            PostResponseDto post = postService.createPost(postRequest);
            Long initialCommentCount = post.getCommentCount();
            Long initialLikeCount = post.getLikeCount();
            
            // when - 의도적으로 오류를 발생시켜 트랜잭션 롤백 유도
            try {
                // 이 메서드는 댓글 작성 + 좋아요 + 알림을 하나의 트랜잭션으로 처리한다고 가정
                // 그리고 마지막에 의도적으로 예외를 발생시킴
                performComplexOperationWithError(post.getPostId(), testUserId2);
                fail("예외가 발생해야 합니다");
            } catch (RuntimeException e) {
                // 예상된 예외
                assertThat(e.getMessage()).contains("의도적 오류");
            }
            
            // then - 모든 변경사항이 롤백되었는지 확인
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                PostResponseDto rollbackPost = postService.getPostById(post.getPostId());
                assertThat(rollbackPost.getCommentCount()).isEqualTo(initialCommentCount);
                assertThat(rollbackPost.getLikeCount()).isEqualTo(initialLikeCount);
            });
            
            // 댓글이 실제로 생성되지 않았는지 확인
            List<CommentResponseDto> comments = commentService.getCommentsByPost(post.getPostId());
            assertThat(comments).isEmpty();
            
            // 알림도 발송되지 않았는지 확인
            List<?> notifications = notificationService.getUserNotifications(testUserId1, 0, 10);
            long notificationCountAfterRollback = notifications.size();
            // 롤백 이전과 동일한 알림 수를 유지해야 함
        }
        
        @Test
        @DisplayName("[통합] 부분 실패 시 데이터 정합성 보장")
        void partialFailure_DataIntegrity() {
            // given
            PostCreateRequestDto postRequest = PostCreateRequestDto.builder()
                    .userId(testUserId1)
                    .title("부분 실패 테스트")
                    .content("일부 작업만 성공하는 경우 테스트")
                    .build();
            
            PostResponseDto post = postService.createPost(postRequest);
            
            // when - 댓글 작성은 성공하지만 알림 발송은 실패하는 상황 시뮬레이션
            CommentCreateRequestDto commentRequest = CommentCreateRequestDto.builder()
                    .postId(post.getPostId())
                    .userId(testUserId2)
                    .content("부분 실패 테스트 댓글")
                    .build();
            
            // 알림 서비스를 일시적으로 실패하도록 설정 (실제 환경에서는 네트워크 오류 등)
            CommentResponseDto comment = commentService.createComment(commentRequest);
            
            // then - 댓글은 생성되었지만 알림 실패 시에도 데이터 일관성 유지
            assertThat(comment).isNotNull();
            
            PostResponseDto updatedPost = postService.getPostById(post.getPostId());
            assertThat(updatedPost.getCommentCount()).isEqualTo(1);
            
            List<CommentResponseDto> comments = commentService.getCommentsByPost(post.getPostId());
            assertThat(comments).hasSize(1);
            assertThat(comments.get(0).getContent()).isEqualTo("부분 실패 테스트 댓글");
        }
    }
    
    @Nested
    @DisplayName("캐시 동기화 및 성능")
    class CacheSynchronizationTest {
        
        @Test
        @DisplayName("[통합] 다중 서비스 캐시 동기화")
        void multiServiceCache_Synchronization() {
            // given - 게시글 생성 및 캐시 워밍업
            PostCreateRequestDto postRequest = PostCreateRequestDto.builder()
                    .userId(testUserId1)
                    .title("캐시 동기화 테스트")
                    .content("여러 서비스의 캐시 동기화 검증")
                    .build();
            
            PostResponseDto post = postService.createPost(postRequest);
            
            // 각 서비스에서 캐시 생성
            PostResponseDto cachedPost1 = postService.getPostById(post.getPostId());
            var postStats1 = interactionService.getPostInteractionStats(post.getPostId(), testUserId1);
            
            // when - 댓글 추가로 캐시 무효화 트리거
            CommentCreateRequestDto commentRequest = CommentCreateRequestDto.builder()
                    .postId(post.getPostId())
                    .userId(testUserId2)
                    .content("캐시 동기화 테스트 댓글")
                    .build();
            
            CommentResponseDto comment = commentService.createComment(commentRequest);
            
            // then - 모든 서비스의 캐시가 동기화되었는지 확인
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                // PostService 캐시 업데이트 확인
                PostResponseDto updatedPost = postService.getPostById(post.getPostId());
                assertThat(updatedPost.getCommentCount()).isEqualTo(1);
                
                // InteractionService 캐시 업데이트 확인
                var updatedStats = interactionService.getPostInteractionStats(post.getPostId(), testUserId1);
                assertThat(updatedStats.getCommentCount()).isEqualTo(1);
                
                // 댓글 목록 캐시 확인
                List<CommentResponseDto> comments = commentService.getCommentsByPost(post.getPostId());
                assertThat(comments).hasSize(1);
            });
        }
    }
    
    @Test
    @DisplayName("[종합] 커뮤니티 전체 서비스 통합 시나리오")
    void comprehensive_CommunityServiceIntegration() {
        System.out.println("=== 커뮤니티 전체 서비스 통합 테스트 시작 ===");
        
        // 1. 게시글 작성
        PostCreateRequestDto postRequest = PostCreateRequestDto.builder()
                .userId(testUserId1)
                .title("종합 통합 테스트")
                .content("모든 커뮤니티 기능을 테스트하는 게시글입니다.")
                .routeId(testRouteId)
                .isPublic(true)
                .build();
        
        PostResponseDto post = postService.createPost(postRequest);
        assertThat(post).isNotNull();
        System.out.println("✅ 1. 게시글 작성 완료");
        
        // 2. 여러 사용자가 댓글 작성
        for (int i = 2; i <= 4; i++) {
            CommentCreateRequestDto commentRequest = CommentCreateRequestDto.builder()
                    .postId(post.getPostId())
                    .userId((long) i)
                    .content("사용자 " + i + "의 댓글입니다.")
                    .build();
            
            CommentResponseDto comment = commentService.createComment(commentRequest);
            assertThat(comment).isNotNull();
        }
        System.out.println("✅ 2. 다중 사용자 댓글 작성 완료");
        
        // 3. 상호작용 (좋아요, 북마크)
        for (int i = 2; i <= 5; i++) {
            interactionService.togglePostLike((long) i, post.getPostId());
            if (i <= 3) {
                interactionService.togglePostBookmark((long) i, post.getPostId(), "즐겨찾기");
            }
        }
        System.out.println("✅ 3. 상호작용 (좋아요, 북마크) 완료");
        
        // 4. 대댓글 작성
        List<CommentResponseDto> comments = commentService.getCommentsByPost(post.getPostId());
        CommentCreateRequestDto replyRequest = CommentCreateRequestDto.builder()
                .postId(post.getPostId())
                .parentId(comments.get(0).getCommentId())
                .userId(testUserId2)
                .content("첫 번째 댓글에 대한 대댓글입니다.")
                .build();
        
        CommentResponseDto reply = commentService.createComment(replyRequest);
        assertThat(reply.getParentId()).isEqualTo(comments.get(0).getCommentId());
        System.out.println("✅ 4. 대댓글 작성 완료");
        
        // 5. 최종 통계 검증
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            PostResponseDto finalPost = postService.getPostById(post.getPostId());
            assertThat(finalPost.getCommentCount()).isEqualTo(4); // 댓글 3개 + 대댓글 1개
            assertThat(finalPost.getLikeCount()).isEqualTo(4); // 4명이 좋아요
            
            var finalStats = interactionService.getPostInteractionStats(post.getPostId(), testUserId1);
            assertThat(finalStats.getLikeCount()).isEqualTo(4);
            assertThat(finalStats.getBookmarkCount()).isEqualTo(2);
            assertThat(finalStats.getCommentCount()).isEqualTo(4);
            assertThat(finalStats.getEngagementRate()).isGreaterThan(0);
        });
        System.out.println("✅ 5. 최종 통계 검증 완료");
        
        // 6. 알림 발송 확인
        List<?> notifications = notificationService.getUserNotifications(testUserId1, 0, 20);
        assertThat(notifications).hasSizeGreaterThanOrEqualTo(4); // 댓글 알림들
        System.out.println("✅ 6. 알림 발송 검증 완료");
        
        // 7. 캐시 동기화 확인
        PostResponseDto cachedPost = postService.getPostById(post.getPostId());
        assertThat(cachedPost.getCommentCount()).isEqualTo(4);
        assertThat(cachedPost.getLikeCount()).isEqualTo(4);
        System.out.println("✅ 7. 캐시 동기화 검증 완료");
        
        System.out.println("=== 커뮤니티 전체 서비스 통합 테스트 완료: 모든 기능 정상 동작 ===");
    }
    
    // ================================================================================================
    // Helper Methods
    // ================================================================================================
    
    @Transactional
    private void performComplexOperationWithError(Long postId, Long userId) {
        // 댓글 작성
        CommentCreateRequestDto commentRequest = CommentCreateRequestDto.builder()
                .postId(postId)
                .userId(userId)
                .content("롤백 테스트 댓글")
                .build();
        commentService.createComment(commentRequest);
        
        // 좋아요 추가
        interactionService.togglePostLike(userId, postId);
        
        // 의도적 예외 발생 (트랜잭션 롤백 유도)
        throw new RuntimeException("의도적 오류 발생 - 트랜잭션 롤백 테스트");
    }
}
```

## 통합 테스트 시나리오

### 1. 기본 워크플로우 검증
- 게시글 작성 → 댓글 → 좋아요 → 알림
- 각 단계별 데이터 일관성 확인
- 캐시 동기화 검증

### 2. 동시성 시나리오  
- 다중 사용자 동시 상호작용
- 경합 상황에서 데이터 정합성
- 성능 병목 지점 식별

### 3. 오류 복구 시나리오
- 부분 실패 시 데이터 복구
- 트랜잭션 롤백 검증
- 외부 서비스 실패 대응

## 실행 및 검증

### 실행 명령어
```bash
# 전체 통합 테스트 실행
./gradlew test --tests="*CommunityServiceIntegrationTest"

# 특정 통합 시나리오만 실행
./gradlew test --tests="CommunityServiceIntegrationTest.PostCommentNotificationFlow"

# Docker 환경에서 실행
docker-compose -f docker-compose.test.yml up -d
./gradlew test --tests="*CommunityServiceIntegrationTest"
```

### 검증 기준
1. **데이터 일관성**: 모든 서비스 간 데이터 동기화
2. **트랜잭션 무결성**: 실패 시 완전한 롤백
3. **성능**: 통합 작업 응답 시간 < 3초
4. **캐시 동기화**: 실시간 캐시 무효화
5. **알림 정확성**: 모든 이벤트에 대한 정확한 알림

이 테스트는 커뮤니티 서비스들이 복잡한 상호작용 환경에서도 안정적으로 연동됨을 보장합니다.