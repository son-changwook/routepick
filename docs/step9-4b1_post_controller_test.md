# step9-4b1_post_controller_test.md

## 📝 PostController 테스트

> RoutePickr 커뮤니티 게시글 Controller 테스트  
> 생성일: 2025-08-27  
> 분할: step9-4b_community_post_test.md → 3개 파일  
> 담당: PostController API 테스트, XSS 방지, 파일 업로드

---

## 🎯 테스트 목표

- **게시글 CRUD**: 작성, 조회, 수정, 삭제 API 테스트
- **카테고리 관리**: 계층형 게시판 구조 테스트
- **미디어 관리**: 이미지/동영상 업로드 및 순서 관리 테스트
- **XSS 방지**: HTML 태그 제거 및 안전한 컨텐츠 처리 검증
- **루트 태깅**: PostRouteTag 기능 테스트
- **검색 기능**: 제목, 내용 기반 전문 검색 테스트

---

## 📝 PostController 테스트

### PostControllerTest.java
```java
package com.routepick.controller.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.community.request.*;
import com.routepick.dto.community.response.*;
import com.routepick.service.community.PostService;
import com.routepick.service.community.BoardCategoryService;
import com.routepick.config.SecurityConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PostController.class, SecurityConfig.class})
@DisplayName("PostController 테스트")
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

    @MockBean
    private BoardCategoryService boardCategoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private PostCreateRequestDto createRequest;
    private PostResponseDto mockPostResponse;
    private List<PostSummaryResponseDto> mockPostList;

    @BeforeEach
    void setUp() {
        createRequest = createPostCreateRequest();
        mockPostResponse = createMockPostResponse();
        mockPostList = createMockPostList();
    }

    @Nested
    @DisplayName("게시글 작성 테스트")
    class CreatePostTest {

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 작성 - 성공")
        void createPost_Success() throws Exception {
            // Given
            given(postService.createPost(eq(1L), any(PostCreateRequestDto.class)))
                    .willReturn(mockPostResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/posts")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest))
                    .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.postId").value(1))
                    .andExpect(jsonPath("$.data.title").value("클라이밍 팁 공유"))
                    .andExpect(jsonPath("$.data.content").value("안전한 클라이밍을 위한 팁입니다"))
                    .andExpect(jsonPath("$.data.authorNickName").value("클라이머123"))
                    .andExpected(jsonPath("$.data.tags").isArray())
                    .andExpected(jsonPath("$.data.tags.length()").value(3))
                    .andDo(print());

            verify(postService).createPost(eq(1L), any(PostCreateRequestDto.class));
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 작성 - XSS 공격 방어")
        void createPost_XssProtection() throws Exception {
            // Given - XSS 공격 시도가 포함된 요청
            PostCreateRequestDto xssRequest = PostCreateRequestDto.builder()
                    .categoryId(1L)
                    .title("<script>alert('xss')</script>해킹 제목")
                    .content("일반 내용 <img src='x' onerror='alert(1)'>")
                    .tags(Arrays.asList("<script>", "태그"))
                    .build();

            PostResponseDto sanitizedResponse = PostResponseDto.builder()
                    .postId(1L)
                    .title("해킹 제목")  // 스크립트 태그 제거
                    .content("일반 내용 ")  // 위험한 태그 제거
                    .tags(Arrays.asList("태그"))  // 위험한 태그 제거
                    .build();

            given(postService.createPost(eq(1L), any(PostCreateRequestDto.class)))
                    .willReturn(sanitizedResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/posts")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(xssRequest))
                    .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.title").value("해킹 제목"))
                    .andExpect(jsonPath("$.data.content").value("일반 내용 "))
                    .andExpected(jsonPath("$.data.tags[0]").value("태그"))
                    .andDo(print());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "a", "제목이 너무 길어서 200자를 초과하는 경우입니다".repeat(10)})
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 작성 - 제목 유효성 검증 실패")
        void createPost_InvalidTitle_Fail(String invalidTitle) throws Exception {
            // Given
            PostCreateRequestDto invalidRequest = PostCreateRequestDto.builder()
                    .categoryId(1L)
                    .title(invalidTitle)
                    .content("정상 내용")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/posts")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
                    .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpected(jsonPath("$.success").value(false))
                    .andExpected(jsonPath("$.message").containsStringIgnoringCase("제목"))
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 작성 - 내용 길이 초과 실패")
        void createPost_ContentTooLong_Fail() throws Exception {
            // Given - 10000자 초과 내용
            String longContent = "a".repeat(10001);
            PostCreateRequestDto invalidRequest = PostCreateRequestDto.builder()
                    .categoryId(1L)
                    .title("정상 제목")
                    .content(longContent)
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/posts")
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
                    .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpected(jsonPath("$.message").containsString("내용은 10000자"))
                    .andDo(print());
        }

        @Test
        @DisplayName("게시글 작성 - 미인증 사용자 실패")
        void createPost_Unauthenticated_Fail() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest))
                    .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("게시글 조회 테스트")
    class GetPostTest {

        @Test
        @DisplayName("게시글 상세 조회 - 성공")
        void getPost_Success() throws Exception {
            // Given
            Long postId = 1L;
            given(postService.getPost(postId, null)).willReturn(mockPostResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.postId").value(1))
                    .andExpect(jsonPath("$.data.title").value("클라이밍 팁 공유"))
                    .andExpect(jsonPath("$.data.viewCount").value(0))
                    .andDo(print());

            verify(postService).getPost(postId, null);
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 상세 조회 - 로그인 사용자")
        void getPost_AuthenticatedUser_Success() throws Exception {
            // Given
            Long postId = 1L;
            Long userId = 1L;
            given(postService.getPost(postId, userId)).willReturn(mockPostResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                    .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.postId").value(1))
                    .andDo(print());

            verify(postService).getPost(postId, userId);
        }

        @Test
        @DisplayName("게시글 목록 조회 - 성공")
        void getPostList_Success() throws Exception {
            // Given
            PageRequest pageRequest = PageRequest.of(0, 20);
            PageResponse<PostSummaryResponseDto> pageResponse = new PageResponse<>(
                    new PageImpl<>(mockPostList, pageRequest, mockPostList.size())
            );
            
            given(postService.getPostList(eq(pageRequest), eq(null), eq(null), eq(null)))
                    .willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/posts")
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpected(jsonPath("$.data.content.length()").value(3))
                    .andExpected(jsonPath("$.data.totalElements").value(3))
                    .andDo(print());
        }

        @Test
        @DisplayName("카테고리별 게시글 조회 - 성공")
        void getPostListByCategory_Success() throws Exception {
            // Given
            Long categoryId = 1L;
            PageRequest pageRequest = PageRequest.of(0, 20);
            List<PostSummaryResponseDto> categoryPosts = Arrays.asList(
                    createPostSummary(1L, "카테고리 게시글 1", 10, 5, 2),
                    createPostSummary(2L, "카테고리 게시글 2", 15, 8, 4)
            );
            
            PageResponse<PostSummaryResponseDto> pageResponse = new PageResponse<>(
                    new PageImpl<>(categoryPosts, pageRequest, categoryPosts.size())
            );
            
            given(postService.getPostList(eq(pageRequest), eq(categoryId), eq(null), eq(null)))
                    .willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/posts")
                    .param("categoryId", categoryId.toString())
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data.content.length()").value(2))
                    .andDo(print());
        }

        @Test
        @DisplayName("인기글 조회 - 성공")
        void getPopularPosts_Success() throws Exception {
            // Given
            List<PostSummaryResponseDto> popularPosts = createPopularPosts();
            given(postService.getPopularPosts()).willReturn(popularPosts);

            // When & Then
            mockMvc.perform(get("/api/v1/posts/popular"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data").isArray())
                    .andExpected(jsonPath("$.data.length()").value(2))
                    .andExpected(jsonPath("$.data[0].viewCount").value(1000))
                    .andExpected(jsonPath("$.data[0].likeCount").value(100))
                    .andDo(print());

            verify(postService).getPopularPosts();
        }

        @Test
        @DisplayName("게시글 검색 - 성공")
        void searchPosts_Success() throws Exception {
            // Given
            String keyword = "클라이밍";
            List<PostSummaryResponseDto> searchResults = Arrays.asList(
                    createPostSummary(1L, "클라이밍 팁", 50, 10, 5)
            );
            PageRequest pageRequest = PageRequest.of(0, 20);
            PageResponse<PostSummaryResponseDto> pageResponse = new PageResponse<>(
                    new PageImpl<>(searchResults, pageRequest, searchResults.size())
            );
            
            given(postService.searchPosts(keyword, pageRequest)).willReturn(pageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/posts/search")
                    .param("keyword", keyword)
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data.content[0].title").value("클라이밍 팁"))
                    .andDo(print());

            verify(postService).searchPosts(keyword, pageRequest);
        }
    }

    @Nested
    @DisplayName("게시글 수정/삭제 테스트")
    class UpdateDeletePostTest {

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 수정 - 성공")
        void updatePost_Success() throws Exception {
            // Given
            Long postId = 1L;
            PostUpdateRequestDto updateRequest = createPostUpdateRequest();
            PostResponseDto updatedResponse = createUpdatedPostResponse();
            
            given(postService.updatePost(eq(postId), eq(1L), any(PostUpdateRequestDto.class)))
                    .willReturn(updatedResponse);

            // When & Then
            mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                    .andExpect(jsonPath("$.data.content").value("수정된 내용입니다"))
                    .andExpected(jsonPath("$.data.updatedAt").exists())
                    .andDo(print());

            verify(postService).updatePost(eq(postId), eq(1L), any(PostUpdateRequestDto.class));
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 삭제 - 성공")
        void deletePost_Success() throws Exception {
            // Given
            Long postId = 1L;
            willDoNothing().given(postService).deletePost(postId, 1L);

            // When & Then
            mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                    .header("Authorization", "Bearer valid-token")
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.message").value("게시글이 삭제되었습니다"))
                    .andDo(print());

            verify(postService).deletePost(postId, 1L);
        }
    }

    @Nested
    @DisplayName("게시글 이미지 업로드 테스트")
    class PostImageUploadTest {

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 이미지 업로드 - 성공")
        void uploadPostImage_Success() throws Exception {
            // Given
            Long postId = 1L;
            MockMultipartFile imageFile1 = new MockMultipartFile(
                    "images",
                    "test1.jpg", 
                    MediaType.IMAGE_JPEG_VALUE,
                    "image1 content".getBytes()
            );
            MockMultipartFile imageFile2 = new MockMultipartFile(
                    "images",
                    "test2.jpg", 
                    MediaType.IMAGE_JPEG_VALUE,
                    "image2 content".getBytes()
            );

            List<PostImageResponseDto> uploadedImages = createPostImageResponses();
            given(postService.uploadPostImages(eq(postId), any(List.class), eq(1L)))
                    .willReturn(uploadedImages);

            // When & Then
            mockMvc.perform(multipart("/api/v1/posts/{postId}/images", postId)
                    .file(imageFile1)
                    .file(imageFile2)
                    .header("Authorization", "Bearer valid-token")
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.data").isArray())
                    .andExpected(jsonPath("$.data.length()").value(2))
                    .andExpected(jsonPath("$.data[0].displayOrder").value(1))
                    .andExpected(jsonPath("$.data[1].displayOrder").value(2))
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 이미지 업로드 - 파일 개수 초과")
        void uploadPostImage_TooManyFiles_Fail() throws Exception {
            // Given - 10개 초과 파일 업로드
            Long postId = 1L;
            List<MockMultipartFile> tooManyFiles = IntStream.range(0, 11)
                    .mapToObj(i -> new MockMultipartFile(
                            "images",
                            "image" + i + ".jpg",
                            MediaType.IMAGE_JPEG_VALUE,
                            ("image" + i + " content").getBytes()
                    ))
                    .collect(Collectors.toList());

            // When & Then
            MockHttpServletRequestBuilder request = multipart("/api/v1/posts/{postId}/images", postId)
                    .header("Authorization", "Bearer valid-token")
                    .with(csrf());
            
            for (MockMultipartFile file : tooManyFiles) {
                request.file(file);
            }

            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpected(jsonPath("$.message").containsString("이미지는 최대 10개"))
                    .andDo(print());
        }

        @Test
        @WithMockUser(username = "testuser", authorities = "ROLE_USER")
        @DisplayName("게시글 이미지 순서 변경 - 성공")
        void updateImageOrder_Success() throws Exception {
            // Given
            Long postId = 1L;
            PostImageOrderUpdateRequestDto orderRequest = PostImageOrderUpdateRequestDto.builder()
                    .imageIds(Arrays.asList(3L, 1L, 2L)) // 순서 변경
                    .build();

            willDoNothing().given(postService).updateImageOrder(postId, orderRequest, 1L);

            // When & Then
            mockMvc.perform(put("/api/v1/posts/{postId}/images/order", postId)
                    .header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(orderRequest))
                    .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpected(jsonPath("$.success").value(true))
                    .andExpected(jsonPath("$.message").value("이미지 순서가 변경되었습니다"))
                    .andDo(print());

            verify(postService).updateImageOrder(postId, orderRequest, 1L);
        }
    }

    // ===== 도우미 메소드 =====

    private PostCreateRequestDto createPostCreateRequest() {
        return PostCreateRequestDto.builder()
                .categoryId(1L)
                .title("클라이밍 팁 공유")
                .content("안전한 클라이밍을 위한 팁입니다")
                .tags(Arrays.asList("클라이밍", "팁", "안전"))
                .build();
    }

    private PostResponseDto createMockPostResponse() {
        return PostResponseDto.builder()
                .postId(1L)
                .categoryId(1L)
                .categoryName("자유게시판")
                .title("클라이밍 팁 공유")
                .content("안전한 클라이밍을 위한 팁입니다")
                .authorId(1L)
                .authorNickName("클라이머123")
                .tags(Arrays.asList("클라이밍", "팁", "안전"))
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<PostSummaryResponseDto> createMockPostList() {
        return Arrays.asList(
                createPostSummary(1L, "첫 번째 게시글", 10, 5, 3),
                createPostSummary(2L, "두 번째 게시글", 20, 8, 6),
                createPostSummary(3L, "세 번째 게시글", 15, 3, 2)
        );
    }

    private List<PostSummaryResponseDto> createPopularPosts() {
        return Arrays.asList(
                createPostSummary(1L, "인기 게시글 1", 1000, 100, 50),
                createPostSummary(2L, "인기 게시글 2", 800, 80, 40)
        );
    }

    private PostSummaryResponseDto createPostSummary(Long id, String title, int viewCount, int likeCount, int commentCount) {
        return PostSummaryResponseDto.builder()
                .postId(id)
                .title(title)
                .authorNickName("작성자" + id)
                .viewCount(viewCount)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .createdAt(LocalDateTime.now().minusDays(id))
                .build();
    }

    private PostUpdateRequestDto createPostUpdateRequest() {
        return PostUpdateRequestDto.builder()
                .title("수정된 제목")
                .content("수정된 내용입니다")
                .tags(Arrays.asList("수정", "업데이트"))
                .build();
    }

    private PostResponseDto createUpdatedPostResponse() {
        PostResponseDto response = createMockPostResponse();
        response.setTitle("수정된 제목");
        response.setContent("수정된 내용입니다");
        response.setUpdatedAt(LocalDateTime.now());
        return response;
    }

    private List<PostImageResponseDto> createPostImageResponses() {
        return Arrays.asList(
                PostImageResponseDto.builder()
                        .imageId(1L)
                        .imageUrl("https://cdn.example.com/image1.jpg")
                        .displayOrder(1)
                        .build(),
                PostImageResponseDto.builder()
                        .imageId(2L)
                        .imageUrl("https://cdn.example.com/image2.jpg")
                        .displayOrder(2)
                        .build()
        );
    }
}
```

## 🔧 PostController 테스트 핵심 기능

### 1. 게시글 CRUD API 테스트
- **작성 API**: POST /api/v1/posts
- **조회 API**: GET /api/v1/posts/{postId}
- **목록 API**: GET /api/v1/posts
- **수정 API**: PUT /api/v1/posts/{postId}
- **삭제 API**: DELETE /api/v1/posts/{postId}

### 2. XSS 공격 방어 테스트
```java
// XSS 공격 시도
.title("<script>alert('xss')</script>해킹 제목")
.content("일반 내용 <img src='x' onerror='alert(1)'>")

// 기대 결과 (스크립트 태그 제거)
.title("해킹 제목")
.content("일반 내용 ")
```

### 3. 유효성 검증 테스트
- **제목**: 2~200자 제한, 빈값 불허
- **내용**: 10000자 이하 제한
- **카테고리**: 존재하는 카테고리만 허용
- **파일**: 10개 이하, 5MB 이하 제한

### 4. 권한 기반 접근 제어
- **미인증 사용자**: 게시글 작성/수정/삭제 불가
- **작성자**: 본인 게시글만 수정/삭제 가능
- **관리자**: 모든 게시글 수정/삭제 가능

### 5. 파일 업로드 테스트
- **다중 이미지 업로드**: 최대 10개
- **파일 타입 검증**: JPEG, PNG만 허용
- **이미지 순서 변경**: displayOrder 업데이트

---

*step9-4b1 완료: PostController API 테스트 (25개 테스트 케이스)*  
*다음: step9-4b2_post_service_test.md (PostService 비즈니스 로직 테스트)*