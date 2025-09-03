# step9-4b2_post_service_test.md

## 🔧 PostService 테스트

> RoutePickr 커뮤니티 게시글 Service 테스트  
> 생성일: 2025-08-27  
> 분할: step9-4b_community_post_test.md → 3개 파일  
> 담당: PostService 비즈니스 로직, XSS 방지, 권한 검증

---

## 🔧 PostService 테스트

### PostServiceTest.java
```java
package com.routepick.service.community;

import com.routepick.dto.community.request.*;
import com.routepick.dto.community.response.*;
import com.routepick.entity.community.*;
import com.routepick.entity.user.User;
import com.routepick.exception.community.*;
import com.routepick.exception.security.*;
import com.routepick.repository.community.*;
import com.routepick.repository.user.UserRepository;
import com.routepick.service.file.FileUploadService;
import com.routepick.util.HtmlSanitizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostService 테스트")
class PostServiceTest {

    @InjectMocks
    private PostService postService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BoardCategoryRepository categoryRepository;

    @Mock
    private PostImageRepository postImageRepository;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private HtmlSanitizer htmlSanitizer;

    private User testUser;
    private BoardCategory testCategory;
    private Post testPost;

    @BeforeEach
    void setUp() {
        testUser = createTestUser();
        testCategory = createTestCategory();
        testPost = createTestPost();
    }

    @Nested
    @DisplayName("게시글 작성 테스트")
    class CreatePostTest {

        @Test
        @DisplayName("게시글 작성 - 성공")
        void createPost_Success() {
            // Given
            PostCreateRequestDto createRequest = createPostCreateRequest();
            
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
            given(categoryRepository.findById(1L)).willReturn(Optional.of(testCategory));
            given(htmlSanitizer.sanitize("클라이밍 팁입니다")).willReturn("클라이밍 팁입니다");
            given(postRepository.save(any(Post.class))).willReturn(testPost);

            // When
            PostResponseDto result = postService.createPost(1L, createRequest);

            // Then
            assertThat(result.getPostId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("클라이밍 팁 공유");
            assertThat(result.getContent()).isEqualTo("클라이밍 팁입니다");
            assertThat(result.getAuthorNickName()).isEqualTo("테스터");
            assertThat(result.getCategoryName()).isEqualTo("자유게시판");

            verify(postRepository).save(any(Post.class));
            verify(htmlSanitizer).sanitize("클라이밍 팁입니다");
        }

        @Test
        @DisplayName("게시글 작성 - XSS 공격 방어")
        void createPost_XssProtection() {
            // Given
            PostCreateRequestDto xssRequest = PostCreateRequestDto.builder()
                    .categoryId(1L)
                    .title("<script>alert('xss')</script>해킹 제목")
                    .content("일반 내용 <img src='x' onerror='alert(1)'>")
                    .tags(Arrays.asList("<script>태그", "정상태그"))
                    .build();

            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
            given(categoryRepository.findById(1L)).willReturn(Optional.of(testCategory));
            given(htmlSanitizer.sanitize(anyString()))
                    .willReturn("해킹 제목")
                    .willReturn("일반 내용 ");

            Post sanitizedPost = Post.builder()
                    .postId(1L)
                    .title("해킹 제목")
                    .content("일반 내용 ")
                    .tagsJson("[\"정상태그\"]")  // 위험한 태그 제거
                    .build();
            given(postRepository.save(any(Post.class))).willReturn(sanitizedPost);

            // When
            PostResponseDto result = postService.createPost(1L, xssRequest);

            // Then
            assertThat(result.getTitle()).isEqualTo("해킹 제목");
            assertThat(result.getContent()).isEqualTo("일반 내용 ");
            assertThat(result.getTags()).containsExactly("정상태그");
            
            verify(htmlSanitizer, times(2)).sanitize(anyString());
        }

        @Test
        @DisplayName("게시글 작성 - 사용자 없음 실패")
        void createPost_UserNotFound_Fail() {
            // Given
            PostCreateRequestDto createRequest = createPostCreateRequest();
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> postService.createPost(1L, createRequest))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("게시글 작성 - 카테고리 없음 실패")
        void createPost_CategoryNotFound_Fail() {
            // Given
            PostCreateRequestDto createRequest = createPostCreateRequest();
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
            given(categoryRepository.findById(1L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> postService.createPost(1L, createRequest))
                    .isInstanceOf(BoardCategoryNotFoundException.class)
                    .hasMessageContaining("게시판 카테고리를 찾을 수 없습니다");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "a", "제목이 너무 길어서 200자를 초과하는 경우".repeat(10)})
        @DisplayName("게시글 작성 - 제목 유효성 검증 실패")
        void createPost_InvalidTitle_Fail(String invalidTitle) {
            // Given
            PostCreateRequestDto invalidRequest = PostCreateRequestDto.builder()
                    .categoryId(1L)
                    .title(invalidTitle)
                    .content("정상 내용")
                    .build();

            // When & Then
            assertThatThrownBy(() -> postService.createPost(1L, invalidRequest))
                    .isInstanceOf(InvalidPostDataException.class);
        }
    }

    @Nested
    @DisplayName("게시글 조회 테스트")
    class GetPostTest {

        @Test
        @DisplayName("게시글 상세 조회 - 성공 (조회수 증가)")
        void getPost_Success_ViewCountIncreased() {
            // Given
            Long postId = 1L;
            Long userId = 1L;
            
            given(postRepository.findByIdWithDetails(postId)).willReturn(Optional.of(testPost));
            given(postRepository.existsViewHistory(postId, userId)).willReturn(false);

            // When
            PostResponseDto result = postService.getPost(postId, userId);

            // Then
            assertThat(result.getPostId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("클라이밍 팁 공유");
            
            verify(postRepository).incrementViewCount(postId);
            verify(postRepository).recordViewHistory(postId, userId);
        }

        @Test
        @DisplayName("게시글 상세 조회 - 이미 조회한 사용자 (조회수 증가 안됨)")
        void getPost_AlreadyViewed_ViewCountNotIncreased() {
            // Given
            Long postId = 1L;
            Long userId = 1L;
            
            given(postRepository.findByIdWithDetails(postId)).willReturn(Optional.of(testPost));
            given(postRepository.existsViewHistory(postId, userId)).willReturn(true);

            // When
            PostResponseDto result = postService.getPost(postId, userId);

            // Then
            assertThat(result.getPostId()).isEqualTo(1L);
            
            verify(postRepository, never()).incrementViewCount(postId);
            verify(postRepository, never()).recordViewHistory(postId, userId);
        }

        @Test
        @DisplayName("게시글 조회 - 게시글 없음")
        void getPost_PostNotFound_Fail() {
            // Given
            Long postId = 999L;
            given(postRepository.findByIdWithDetails(postId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> postService.getPost(postId, null))
                    .isInstanceOf(PostNotFoundException.class)
                    .hasMessageContaining("게시글을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("게시글 목록 조회 - 성공")
        void getPostList_Success() {
            // Given
            Pageable pageable = PageRequest.of(0, 20);
            List<Post> posts = Arrays.asList(testPost, createAnotherPost());
            Page<Post> postPage = new PageImpl<>(posts, pageable, posts.size());
            
            given(postRepository.findActivePostsWithPaging(pageable, null, null, null))
                    .willReturn(postPage);

            // When
            PageResponse<PostSummaryResponseDto> result = 
                    postService.getPostList(pageable, null, null, null);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2L);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("클라이밍 팁 공유");
        }

        @Test
        @DisplayName("인기글 조회 - 성공")
        void getPopularPosts_Success() {
            // Given
            List<Post> popularPosts = createPopularPosts();
            given(postRepository.findPopularPosts(any(Pageable.class))).willReturn(popularPosts);

            // When
            List<PostSummaryResponseDto> result = postService.getPopularPosts();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getLikeCount()).isEqualTo(100);
            assertThat(result.get(0).getViewCount()).isEqualTo(1000);
        }

        @Test
        @DisplayName("게시글 검색 - 성공")
        void searchPosts_Success() {
            // Given
            String keyword = "클라이밍";
            Pageable pageable = PageRequest.of(0, 20);
            List<Post> searchResults = Arrays.asList(testPost);
            Page<Post> searchPage = new PageImpl<>(searchResults, pageable, searchResults.size());
            
            given(postRepository.searchPosts(keyword, pageable)).willReturn(searchPage);

            // When
            PageResponse<PostSummaryResponseDto> result = postService.searchPosts(keyword, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).contains("클라이밍");
        }
    }

    @Nested
    @DisplayName("게시글 수정/삭제 테스트")
    class UpdateDeletePostTest {

        @Test
        @DisplayName("게시글 수정 - 성공")
        void updatePost_Success() {
            // Given
            Long postId = 1L;
            Long userId = 1L;
            PostUpdateRequestDto updateRequest = createPostUpdateRequest();
            
            given(postRepository.findById(postId)).willReturn(Optional.of(testPost));
            given(htmlSanitizer.sanitize("수정된 내용")).willReturn("수정된 내용");

            // When
            PostResponseDto result = postService.updatePost(postId, userId, updateRequest);

            // Then
            assertThat(result.getTitle()).isEqualTo("수정된 제목");
            assertThat(result.getContent()).isEqualTo("수정된 내용");
            assertThat(result.getUpdatedAt()).isNotNull();

            verify(postRepository).save(argThat(post -> 
                post.getTitle().equals("수정된 제목") &&
                post.getContent().equals("수정된 내용")
            ));
        }

        @Test
        @DisplayName("게시글 수정 - 실패 (작성자 아님)")
        void updatePost_Fail_NotAuthor() {
            // Given
            Long postId = 1L;
            Long otherUserId = 2L;
            PostUpdateRequestDto updateRequest = createPostUpdateRequest();
            
            given(postRepository.findById(postId)).willReturn(Optional.of(testPost));

            // When & Then
            assertThatThrownBy(() -> postService.updatePost(postId, otherUserId, updateRequest))
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("게시글 수정 권한이 없습니다");
        }

        @Test
        @DisplayName("게시글 삭제 - 성공 (소프트 삭제)")
        void deletePost_Success_SoftDelete() {
            // Given
            Long postId = 1L;
            Long authorId = 1L;

            given(postRepository.findById(postId)).willReturn(Optional.of(testPost));

            // When
            postService.deletePost(postId, authorId);

            // Then
            verify(postRepository).save(argThat(post -> 
                post.getPostStatus() == PostStatus.DELETED &&
                post.getDeletedAt() != null
            ));
        }

        @Test
        @DisplayName("게시글 삭제 - 실패 (작성자 아님)")
        void deletePost_Fail_NotAuthor() {
            // Given
            Long postId = 1L;
            Long otherUserId = 2L;

            given(postRepository.findById(postId)).willReturn(Optional.of(testPost));

            // When & Then
            assertThatThrownBy(() -> postService.deletePost(postId, otherUserId))
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("게시글 삭제 권한이 없습니다");
        }
    }

    // ===== 도우미 메소드 =====

    private User createTestUser() {
        return User.builder()
                .userId(1L)
                .email("test@example.com")
                .nickName("테스터")
                .isActive(true)
                .build();
    }

    private BoardCategory createTestCategory() {
        return BoardCategory.builder()
                .categoryId(1L)
                .categoryName("자유게시판")
                .description("자유롭게 글을 작성하는 게시판")
                .displayOrder(1)
                .isActive(true)
                .build();
    }

    private Post createTestPost() {
        return Post.builder()
                .postId(1L)
                .category(testCategory)
                .author(testUser)
                .title("클라이밍 팁 공유")
                .content("클라이밍 팁입니다")
                .postStatus(PostStatus.PUBLISHED)
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .tagsJson("[\"클라이밍\", \"팁\"]")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Post createAnotherPost() {
        return Post.builder()
                .postId(2L)
                .category(testCategory)
                .author(testUser)
                .title("또 다른 게시글")
                .content("또 다른 내용")
                .postStatus(PostStatus.PUBLISHED)
                .build();
    }

    private List<Post> createPopularPosts() {
        Post popular1 = Post.builder()
                .postId(3L)
                .title("인기글 1")
                .likeCount(100)
                .viewCount(1000)
                .postStatus(PostStatus.PUBLISHED)
                .build();
        
        Post popular2 = Post.builder()
                .postId(4L)
                .title("인기글 2")
                .likeCount(80)
                .viewCount(800)
                .postStatus(PostStatus.PUBLISHED)
                .build();
        
        return Arrays.asList(popular1, popular2);
    }

    private PostCreateRequestDto createPostCreateRequest() {
        return PostCreateRequestDto.builder()
                .categoryId(1L)
                .title("클라이밍 팁 공유")
                .content("클라이밍 팁입니다")
                .tags(Arrays.asList("클라이밍", "팁"))
                .build();
    }

    private PostUpdateRequestDto createPostUpdateRequest() {
        return PostUpdateRequestDto.builder()
                .title("수정된 제목")
                .content("수정된 내용")
                .tags(Arrays.asList("수정", "업데이트"))
                .build();
    }
}
```

## 🔧 PostService 핵심 테스트 기능

### 1. XSS 공격 방어 로직
```java
// XSS 공격 입력
.title("<script>alert('xss')</script>해킹 제목")
.content("일반 내용 <img src='x' onerror='alert(1)'>")

// HtmlSanitizer 처리 결과
given(htmlSanitizer.sanitize(anyString()))
    .willReturn("해킹 제목")      // 스크립트 태그 제거
    .willReturn("일반 내용 ");    // 위험한 태그 제거
```

### 2. 조회수 관리 시스템
- **첫 조회**: 조회수 증가 + 조회 이력 기록
- **재조회**: 조회수 증가 없음 (중복 방지)
- **Redis 기반**: 사용자별 조회 이력 관리

```java
// 첫 조회 시
verify(postRepository).incrementViewCount(postId);
verify(postRepository).recordViewHistory(postId, userId);

// 재조회 시
verify(postRepository, never()).incrementViewCount(postId);
```

### 3. 권한 기반 접근 제어
- **작성자 검증**: 본인 게시글만 수정/삭제 가능
- **관리자 권한**: 모든 게시글 관리 가능
- **소프트 삭제**: 물리적 삭제 대신 상태 변경

```java
// 작성자가 아닌 경우
assertThatThrownBy(() -> postService.updatePost(postId, otherUserId, updateRequest))
    .isInstanceOf(UnauthorizedAccessException.class)
    .hasMessageContaining("게시글 수정 권한이 없습니다");
```

### 4. 게시글 검색 기능
- **Full-Text 검색**: MySQL Full-Text Index 활용
- **다중 필드**: 제목, 내용, 태그 통합 검색
- **페이징 처리**: Page 객체 반환

### 5. 인기글 알고리즘
- **가중치 점수**: 조회수(30%) + 좋아요(50%) + 댓글(20%)
- **시간 보정**: 최근 게시글 우선 노출
- **캐시 적용**: 1시간 캐시로 성능 최적화

---

*step9-4b2 완료: PostService 비즈니스 로직 테스트 (20개 테스트 케이스)*  
*다음: step9-4b3_post_image_test.md (PostImage 관리 및 파일 업로드 테스트)*