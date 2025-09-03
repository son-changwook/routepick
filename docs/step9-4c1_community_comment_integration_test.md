# 커뮤니티 댓글 통합 테스트

## 개요
커뮤니티 댓글 시스템의 통합 테스트를 구현합니다. 실제 데이터베이스와 연동하여 댓글의 생성, 수정, 삭제, 계층 구조 관리 등의 기능을 검증합니다.

## 테스트 클래스 구조

```java
package com.routepick.community.integration;

import com.routepick.community.dto.request.CommentCreateRequestDto;
import com.routepick.community.dto.request.CommentUpdateRequestDto;
import com.routepick.community.dto.response.CommentResponseDto;
import com.routepick.community.entity.Post;
import com.routepick.community.entity.Comment;
import com.routepick.community.repository.PostRepository;
import com.routepick.community.repository.CommentRepository;
import com.routepick.community.service.CommentService;
import com.routepick.user.entity.User;
import com.routepick.user.repository.UserRepository;
import com.routepick.common.exception.BusinessException;
import com.routepick.common.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 커뮤니티 댓글 통합 테스트
 * 
 * 테스트 범위:
 * - 댓글 CRUD 전체 플로우
 * - 계층형 댓글 구조 검증
 * - 데이터베이스 트랜잭션 검증
 * - 비즈니스 로직 통합 검증
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class CommentIntegrationTest {

    @Autowired
    private CommentService commentService;
    
    @Autowired
    private CommentRepository commentRepository;
    
    @Autowired
    private PostRepository postRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    private User testUser;
    private Post testPost;
    
    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .nickName("테스터")
                .phone("010-1234-5678")
                .birthDate("1990-01-01")
                .gender("MALE")
                .agreeToTerms(true)
                .agreeToPrivacy(true)
                .agreeToMarketing(false)
                .build();
        testUser = userRepository.save(testUser);
        
        // 테스트 게시글 생성
        testPost = Post.builder()
                .user(testUser)
                .title("테스트 게시글")
                .content("테스트용 게시글 내용입니다.")
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .isPublic(true)
                .build();
        testPost = postRepository.save(testPost);
    }
    
    @Nested
    @DisplayName("댓글 생성 통합 테스트")
    class CreateCommentIntegrationTest {
        
        @Test
        @DisplayName("[성공] 최상위 댓글 생성 - 전체 플로우")
        void createTopLevelComment_Success() {
            // given
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("이것은 최상위 댓글입니다.")
                    .build();
            
            // when
            CommentResponseDto result = commentService.createComment(testUser.getUserId(), requestDto);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getCommentId()).isNotNull();
            assertThat(result.getContent()).isEqualTo("이것은 최상위 댓글입니다.");
            assertThat(result.getUserId()).isEqualTo(testUser.getUserId());
            assertThat(result.getParentId()).isNull();
            assertThat(result.getDepth()).isEqualTo(0);
            assertThat(result.getLikeCount()).isEqualTo(0);
            assertThat(result.getChildComments()).isEmpty();
            
            // 데이터베이스 검증
            Comment savedComment = commentRepository.findById(result.getCommentId()).orElse(null);
            assertThat(savedComment).isNotNull();
            assertThat(savedComment.getContent()).isEqualTo("이것은 최상위 댓글입니다.");
            assertThat(savedComment.getUser().getUserId()).isEqualTo(testUser.getUserId());
            assertThat(savedComment.getPost().getPostId()).isEqualTo(testPost.getPostId());
            
            // 게시글 댓글 수 증가 검증
            Post updatedPost = postRepository.findById(testPost.getPostId()).orElse(null);
            assertThat(updatedPost).isNotNull();
            assertThat(updatedPost.getCommentCount()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("[성공] 대댓글 생성 - 계층 구조")
        void createReplyComment_Success() {
            // given - 부모 댓글 먼저 생성
            CommentCreateRequestDto parentRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("부모 댓글입니다.")
                    .build();
            CommentResponseDto parentComment = commentService.createComment(testUser.getUserId(), parentRequestDto);
            
            // 대댓글 생성
            CommentCreateRequestDto replyRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .parentId(parentComment.getCommentId())
                    .content("이것은 대댓글입니다.")
                    .build();
            
            // when
            CommentResponseDto result = commentService.createComment(testUser.getUserId(), replyRequestDto);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getParentId()).isEqualTo(parentComment.getCommentId());
            assertThat(result.getDepth()).isEqualTo(1);
            assertThat(result.getContent()).isEqualTo("이것은 대댓글입니다.");
            
            // 계층 구조 검증
            Comment savedReply = commentRepository.findById(result.getCommentId()).orElse(null);
            assertThat(savedReply).isNotNull();
            assertThat(savedReply.getParent().getCommentId()).isEqualTo(parentComment.getCommentId());
            assertThat(savedReply.getDepth()).isEqualTo(1);
            
            // 게시글 댓글 수 검증
            Post updatedPost = postRepository.findById(testPost.getPostId()).orElse(null);
            assertThat(updatedPost.getCommentCount()).isEqualTo(2); // 부모 + 자식
        }
        
        @Test
        @DisplayName("[실패] 존재하지 않는 게시글에 댓글 생성")
        void createComment_PostNotFound_ThrowsException() {
            // given
            Long nonExistentPostId = 999L;
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(nonExistentPostId)
                    .content("존재하지 않는 게시글에 댓글")
                    .build();
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUser.getUserId(), requestDto))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.POST_NOT_FOUND.getMessage());
        }
        
        @Test
        @DisplayName("[실패] 3단계 이상 깊이의 댓글 생성 차단")
        void createComment_ExceedMaxDepth_ThrowsException() {
            // given - 2단계까지 댓글 생성
            CommentCreateRequestDto level1RequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("1단계 댓글")
                    .build();
            CommentResponseDto level1Comment = commentService.createComment(testUser.getUserId(), level1RequestDto);
            
            CommentCreateRequestDto level2RequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .parentId(level1Comment.getCommentId())
                    .content("2단계 댓글")
                    .build();
            CommentResponseDto level2Comment = commentService.createComment(testUser.getUserId(), level2RequestDto);
            
            // 3단계 댓글 생성 시도
            CommentCreateRequestDto level3RequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .parentId(level2Comment.getCommentId())
                    .content("3단계 댓글 - 실패해야 함")
                    .build();
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUser.getUserId(), level3RequestDto))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMMENT_MAX_DEPTH_EXCEEDED.getMessage());
        }
    }
    
    @Nested
    @DisplayName("댓글 조회 통합 테스트")
    class GetCommentsIntegrationTest {
        
        @Test
        @DisplayName("[성공] 게시글의 모든 댓글 계층 구조로 조회")
        void getCommentsByPost_WithHierarchy_Success() {
            // given - 계층 구조 댓글 생성
            CommentCreateRequestDto parentRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("부모 댓글 1")
                    .build();
            CommentResponseDto parent1 = commentService.createComment(testUser.getUserId(), parentRequestDto);
            
            CommentCreateRequestDto replyRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .parentId(parent1.getCommentId())
                    .content("부모 댓글 1의 대댓글")
                    .build();
            commentService.createComment(testUser.getUserId(), replyRequestDto);
            
            CommentCreateRequestDto parent2RequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("부모 댓글 2")
                    .build();
            commentService.createComment(testUser.getUserId(), parent2RequestDto);
            
            // when
            List<CommentResponseDto> result = commentService.getCommentsByPost(testPost.getPostId());
            
            // then
            assertThat(result).hasSize(2); // 최상위 댓글만 2개
            
            CommentResponseDto firstParent = result.get(0);
            assertThat(firstParent.getContent()).isEqualTo("부모 댓글 1");
            assertThat(firstParent.getChildComments()).hasSize(1);
            assertThat(firstParent.getChildComments().get(0).getContent()).isEqualTo("부모 댓글 1의 대댓글");
            
            CommentResponseDto secondParent = result.get(1);
            assertThat(secondParent.getContent()).isEqualTo("부모 댓글 2");
            assertThat(secondParent.getChildComments()).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("댓글 수정 통합 테스트")
    class UpdateCommentIntegrationTest {
        
        @Test
        @DisplayName("[성공] 댓글 내용 수정")
        void updateComment_Success() {
            // given - 댓글 생성
            CommentCreateRequestDto createRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("원본 댓글 내용")
                    .build();
            CommentResponseDto createdComment = commentService.createComment(testUser.getUserId(), createRequestDto);
            
            // 수정 요청
            CommentUpdateRequestDto updateRequestDto = CommentUpdateRequestDto.builder()
                    .commentId(createdComment.getCommentId())
                    .content("수정된 댓글 내용")
                    .build();
            
            // when
            CommentResponseDto result = commentService.updateComment(testUser.getUserId(), updateRequestDto);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEqualTo("수정된 댓글 내용");
            assertThat(result.getUpdatedAt()).isAfter(result.getCreatedAt());
            
            // 데이터베이스 검증
            Comment updatedComment = commentRepository.findById(createdComment.getCommentId()).orElse(null);
            assertThat(updatedComment).isNotNull();
            assertThat(updatedComment.getContent()).isEqualTo("수정된 댓글 내용");
            assertThat(updatedComment.getUpdatedAt()).isAfter(updatedComment.getCreatedAt());
        }
        
        @Test
        @DisplayName("[실패] 다른 사용자의 댓글 수정 시도")
        void updateComment_UnauthorizedUser_ThrowsException() {
            // given - 댓글 생성
            CommentCreateRequestDto createRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("원본 댓글 내용")
                    .build();
            CommentResponseDto createdComment = commentService.createComment(testUser.getUserId(), createRequestDto);
            
            // 다른 사용자 생성
            User otherUser = User.builder()
                    .email("other@example.com")
                    .password("password")
                    .nickName("다른사용자")
                    .phone("010-9999-9999")
                    .birthDate("1985-01-01")
                    .gender("FEMALE")
                    .agreeToTerms(true)
                    .agreeToPrivacy(true)
                    .build();
            otherUser = userRepository.save(otherUser);
            
            // 다른 사용자로 수정 시도
            CommentUpdateRequestDto updateRequestDto = CommentUpdateRequestDto.builder()
                    .commentId(createdComment.getCommentId())
                    .content("악의적 수정")
                    .build();
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.updateComment(otherUser.getUserId(), updateRequestDto))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMMENT_UPDATE_FORBIDDEN.getMessage());
        }
    }
    
    @Nested
    @DisplayName("댓글 삭제 통합 테스트")
    class DeleteCommentIntegrationTest {
        
        @Test
        @DisplayName("[성공] 자식이 없는 댓글 완전 삭제")
        void deleteComment_NoChildren_Success() {
            // given
            CommentCreateRequestDto createRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("삭제할 댓글")
                    .build();
            CommentResponseDto createdComment = commentService.createComment(testUser.getUserId(), createRequestDto);
            
            // when
            commentService.deleteComment(testUser.getUserId(), createdComment.getCommentId());
            
            // then - 물리적 삭제 확인
            boolean exists = commentRepository.existsById(createdComment.getCommentId());
            assertThat(exists).isFalse();
            
            // 게시글 댓글 수 감소 확인
            Post updatedPost = postRepository.findById(testPost.getPostId()).orElse(null);
            assertThat(updatedPost.getCommentCount()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("[성공] 자식이 있는 댓글 논리적 삭제")
        void deleteComment_WithChildren_LogicalDelete() {
            // given - 부모-자식 댓글 생성
            CommentCreateRequestDto parentRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("부모 댓글")
                    .build();
            CommentResponseDto parentComment = commentService.createComment(testUser.getUserId(), parentRequestDto);
            
            CommentCreateRequestDto childRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .parentId(parentComment.getCommentId())
                    .content("자식 댓글")
                    .build();
            commentService.createComment(testUser.getUserId(), childRequestDto);
            
            // when - 부모 댓글 삭제
            commentService.deleteComment(testUser.getUserId(), parentComment.getCommentId());
            
            // then - 논리적 삭제 확인 (댓글은 존재하지만 내용이 변경됨)
            Comment deletedComment = commentRepository.findById(parentComment.getCommentId()).orElse(null);
            assertThat(deletedComment).isNotNull();
            assertThat(deletedComment.getContent()).isEqualTo("삭제된 댓글입니다.");
            assertThat(deletedComment.getDeletedAt()).isNotNull();
            
            // 게시글 댓글 수는 유지 (논리적 삭제)
            Post updatedPost = postRepository.findById(testPost.getPostId()).orElse(null);
            assertThat(updatedPost.getCommentCount()).isEqualTo(2);
        }
    }
    
    @Nested
    @DisplayName("댓글 좋아요 통합 테스트")
    class CommentLikeIntegrationTest {
        
        @Test
        @DisplayName("[성공] 댓글 좋아요 토글 - 추가")
        void toggleCommentLike_Add_Success() {
            // given
            CommentCreateRequestDto createRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("좋아요 테스트 댓글")
                    .build();
            CommentResponseDto createdComment = commentService.createComment(testUser.getUserId(), createRequestDto);
            
            // when
            boolean result = commentService.toggleCommentLike(testUser.getUserId(), createdComment.getCommentId());
            
            // then
            assertThat(result).isTrue(); // 좋아요 추가됨
            
            // 댓글 좋아요 수 증가 확인
            Comment updatedComment = commentRepository.findById(createdComment.getCommentId()).orElse(null);
            assertThat(updatedComment).isNotNull();
            assertThat(updatedComment.getLikeCount()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("[성공] 댓글 좋아요 토글 - 취소")
        void toggleCommentLike_Remove_Success() {
            // given - 좋아요 먼저 추가
            CommentCreateRequestDto createRequestDto = CommentCreateRequestDto.builder()
                    .postId(testPost.getPostId())
                    .content("좋아요 취소 테스트 댓글")
                    .build();
            CommentResponseDto createdComment = commentService.createComment(testUser.getUserId(), createRequestDto);
            commentService.toggleCommentLike(testUser.getUserId(), createdComment.getCommentId());
            
            // when - 좋아요 취소
            boolean result = commentService.toggleCommentLike(testUser.getUserId(), createdComment.getCommentId());
            
            // then
            assertThat(result).isFalse(); // 좋아요 취소됨
            
            // 댓글 좋아요 수 감소 확인
            Comment updatedComment = commentRepository.findById(createdComment.getCommentId()).orElse(null);
            assertThat(updatedComment).isNotNull();
            assertThat(updatedComment.getLikeCount()).isEqualTo(0);
        }
    }
    
    @Test
    @DisplayName("[통합] 댓글 시스템 전체 플로우")
    void commentSystem_CompleteFlow_Success() {
        // given
        int initialCommentCount = testPost.getCommentCount();
        
        // 1. 최상위 댓글 생성
        CommentCreateRequestDto parentRequestDto = CommentCreateRequestDto.builder()
                .postId(testPost.getPostId())
                .content("통합 테스트 최상위 댓글")
                .build();
        CommentResponseDto parentComment = commentService.createComment(testUser.getUserId(), parentRequestDto);
        
        // 2. 대댓글 생성
        CommentCreateRequestDto replyRequestDto = CommentCreateRequestDto.builder()
                .postId(testPost.getPostId())
                .parentId(parentComment.getCommentId())
                .content("통합 테스트 대댓글")
                .build();
        CommentResponseDto replyComment = commentService.createComment(testUser.getUserId(), replyRequestDto);
        
        // 3. 댓글 좋아요
        commentService.toggleCommentLike(testUser.getUserId(), parentComment.getCommentId());
        commentService.toggleCommentLike(testUser.getUserId(), replyComment.getCommentId());
        
        // 4. 댓글 수정
        CommentUpdateRequestDto updateRequestDto = CommentUpdateRequestDto.builder()
                .commentId(parentComment.getCommentId())
                .content("수정된 통합 테스트 댓글")
                .build();
        commentService.updateComment(testUser.getUserId(), updateRequestDto);
        
        // 5. 전체 댓글 조회
        List<CommentResponseDto> comments = commentService.getCommentsByPost(testPost.getPostId());
        
        // then - 전체 플로우 검증
        assertThat(comments).hasSize(1); // 최상위 댓글 1개
        
        CommentResponseDto retrievedParent = comments.get(0);
        assertThat(retrievedParent.getContent()).isEqualTo("수정된 통합 테스트 댓글");
        assertThat(retrievedParent.getLikeCount()).isEqualTo(1);
        assertThat(retrievedParent.getChildComments()).hasSize(1);
        
        CommentResponseDto retrievedReply = retrievedParent.getChildComments().get(0);
        assertThat(retrievedReply.getContent()).isEqualTo("통합 테스트 대댓글");
        assertThat(retrievedReply.getLikeCount()).isEqualTo(1);
        
        // 게시글 댓글 수 확인
        Post finalPost = postRepository.findById(testPost.getPostId()).orElse(null);
        assertThat(finalPost.getCommentCount()).isEqualTo(initialCommentCount + 2);
    }
}
```

## 테스트 실행 및 검증

### 실행 명령어
```bash
# 전체 통합 테스트 실행
./gradlew test --tests="*CommentIntegrationTest"

# 특정 테스트 클래스만 실행
./gradlew test --tests="CommentIntegrationTest.CreateCommentIntegrationTest"

# 프로파일 지정하여 실행
./gradlew test --tests="*CommentIntegrationTest" -Dspring.profiles.active=test
```

### 검증 포인트
1. **데이터 무결성**: 댓글 생성/수정/삭제 시 데이터베이스 상태 일관성
2. **계층 구조**: 부모-자식 댓글 관계 및 깊이 제한
3. **비즈니스 로직**: 권한 검사, 댓글 수 관리, 논리적/물리적 삭제
4. **트랜잭션**: 복합 작업의 원자성 보장
5. **성능**: 계층 구조 조회 시 N+1 문제 방지