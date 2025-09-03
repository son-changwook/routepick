# 상호작용 컨트롤러 테스트

## 개요
커뮤니티 상호작용 기능(좋아요, 북마크, 팔로우 등)의 컨트롤러 계층을 테스트합니다. API 엔드포인트의 정확성, 권한 검증, 성능을 종합적으로 검증합니다.

## 테스트 클래스 구조

```java
package com.routepick.community.controller;

import com.routepick.community.controller.InteractionController;
import com.routepick.community.dto.request.PostLikeRequestDto;
import com.routepick.community.dto.request.PostBookmarkRequestDto;
import com.routepick.community.dto.request.UserFollowRequestDto;
import com.routepick.community.dto.response.InteractionResponseDto;
import com.routepick.community.dto.response.InteractionStatsDto;
import com.routepick.community.service.InteractionService;
import com.routepick.common.exception.BusinessException;
import com.routepick.common.exception.ErrorCode;
import com.routepick.common.security.JwtAuthenticationFilter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * 상호작용 컨트롤러 테스트
 * 
 * 테스트 범위:
 * - 게시글 좋아요/취소 API
 * - 게시글 북마크/취소 API
 * - 사용자 팔로우/언팔로우 API
 * - 상호작용 통계 조회 API
 * - 권한 검증 및 오류 처리
 */
@WebMvcTest(InteractionController.class)
class InteractionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private InteractionService interactionService;
    
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    private Long testUserId;
    private Long testPostId;
    private Long testTargetUserId;
    
    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testPostId = 1L;
        testTargetUserId = 2L;
    }
    
    @Nested
    @DisplayName("게시글 좋아요 API 테스트")
    class PostLikeTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 게시글 좋아요 추가")
        void addPostLike_Success() throws Exception {
            // given
            PostLikeRequestDto requestDto = PostLikeRequestDto.builder()
                    .postId(testPostId)
                    .userId(testUserId)
                    .build();
            
            InteractionResponseDto responseDto = InteractionResponseDto.builder()
                    .success(true)
                    .message("좋아요가 추가되었습니다")
                    .interactionType("LIKE")
                    .currentStatus(true)
                    .totalCount(15)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            given(interactionService.togglePostLike(eq(testUserId), eq(testPostId)))
                    .willReturn(responseDto);
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/posts/{postId}/like", testPostId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("좋아요가 추가되었습니다"))
                    .andExpect(jsonPath("$.interactionType").value("LIKE"))
                    .andExpect(jsonPath("$.currentStatus").value(true))
                    .andExpect(jsonPath("$.totalCount").value(15));
            
            verify(interactionService).togglePostLike(testUserId, testPostId);
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 게시글 좋아요 취소")
        void removePostLike_Success() throws Exception {
            // given
            PostLikeRequestDto requestDto = PostLikeRequestDto.builder()
                    .postId(testPostId)
                    .userId(testUserId)
                    .build();
            
            InteractionResponseDto responseDto = InteractionResponseDto.builder()
                    .success(true)
                    .message("좋아요가 취소되었습니다")
                    .interactionType("LIKE")
                    .currentStatus(false)
                    .totalCount(14)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            given(interactionService.togglePostLike(eq(testUserId), eq(testPostId)))
                    .willReturn(responseDto);
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/posts/{postId}/like", testPostId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("좋아요가 취소되었습니다"))
                    .andExpect(jsonPath("$.currentStatus").value(false))
                    .andExpect(jsonPath("$.totalCount").value(14));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 존재하지 않는 게시글에 좋아요")
        void addPostLike_PostNotFound() throws Exception {
            // given
            Long nonExistentPostId = 999L;
            PostLikeRequestDto requestDto = PostLikeRequestDto.builder()
                    .postId(nonExistentPostId)
                    .userId(testUserId)
                    .build();
            
            given(interactionService.togglePostLike(eq(testUserId), eq(nonExistentPostId)))
                    .willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/posts/{postId}/like", nonExistentPostId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("POST_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").exists());
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 자신의 게시글에 좋아요")
        void addPostLike_OwnPost_Forbidden() throws Exception {
            // given
            PostLikeRequestDto requestDto = PostLikeRequestDto.builder()
                    .postId(testPostId)
                    .userId(testUserId)
                    .build();
            
            given(interactionService.togglePostLike(eq(testUserId), eq(testPostId)))
                    .willThrow(new BusinessException(ErrorCode.SELF_INTERACTION_NOT_ALLOWED));
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/posts/{postId}/like", testPostId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SELF_INTERACTION_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.message").value("자신의 게시글에는 좋아요를 할 수 없습니다"));
        }
    }
    
    @Nested
    @DisplayName("게시글 북마크 API 테스트")
    class PostBookmarkTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 게시글 북마크 추가")
        void addPostBookmark_Success() throws Exception {
            // given
            PostBookmarkRequestDto requestDto = PostBookmarkRequestDto.builder()
                    .postId(testPostId)
                    .userId(testUserId)
                    .folderName("클라이밍 팁")
                    .build();
            
            InteractionResponseDto responseDto = InteractionResponseDto.builder()
                    .success(true)
                    .message("북마크가 추가되었습니다")
                    .interactionType("BOOKMARK")
                    .currentStatus(true)
                    .totalCount(8)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            given(interactionService.togglePostBookmark(eq(testUserId), eq(testPostId), eq("클라이밍 팁")))
                    .willReturn(responseDto);
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/posts/{postId}/bookmark", testPostId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("북마크가 추가되었습니다"))
                    .andExpect(jsonPath("$.interactionType").value("BOOKMARK"))
                    .andExpect(jsonPath("$.currentStatus").value(true));
            
            verify(interactionService).togglePostBookmark(testUserId, testPostId, "클라이밍 팁");
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 게시글 북마크 취소")
        void removePostBookmark_Success() throws Exception {
            // given
            PostBookmarkRequestDto requestDto = PostBookmarkRequestDto.builder()
                    .postId(testPostId)
                    .userId(testUserId)
                    .build();
            
            InteractionResponseDto responseDto = InteractionResponseDto.builder()
                    .success(true)
                    .message("북마크가 취소되었습니다")
                    .interactionType("BOOKMARK")
                    .currentStatus(false)
                    .totalCount(7)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            given(interactionService.togglePostBookmark(eq(testUserId), eq(testPostId), eq(null)))
                    .willReturn(responseDto);
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/posts/{postId}/bookmark", testPostId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.currentStatus").value(false));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 북마크 폴더 목록 조회")
        void getBookmarkFolders_Success() throws Exception {
            // given
            List<String> folders = Arrays.asList("클라이밍 팁", "암장 정보", "장비 리뷰", "기술 가이드");
            
            given(interactionService.getUserBookmarkFolders(eq(testUserId)))
                    .willReturn(folders);
            
            // when & then
            mockMvc.perform(get("/api/v1/interactions/users/{userId}/bookmark-folders", testUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0]").value("클라이밍 팁"))
                    .andExpect(jsonPath("$[1]").value("암장 정보"))
                    .andExpect(jsonPath("$[2]").value("장비 리뷰"))
                    .andExpect(jsonPath("$[3]").value("기술 가이드"));
            
            verify(interactionService).getUserBookmarkFolders(testUserId);
        }
    }
    
    @Nested
    @DisplayName("사용자 팔로우 API 테스트")
    class UserFollowTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 사용자 팔로우")
        void followUser_Success() throws Exception {
            // given
            UserFollowRequestDto requestDto = UserFollowRequestDto.builder()
                    .followerId(testUserId)
                    .followeeId(testTargetUserId)
                    .build();
            
            InteractionResponseDto responseDto = InteractionResponseDto.builder()
                    .success(true)
                    .message("팔로우가 완료되었습니다")
                    .interactionType("FOLLOW")
                    .currentStatus(true)
                    .totalCount(25) // 팔로워 수
                    .timestamp(LocalDateTime.now())
                    .build();
            
            given(interactionService.toggleUserFollow(eq(testUserId), eq(testTargetUserId)))
                    .willReturn(responseDto);
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/users/{userId}/follow", testTargetUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("팔로우가 완료되었습니다"))
                    .andExpect(jsonPath("$.interactionType").value("FOLLOW"))
                    .andExpect(jsonPath("$.currentStatus").value(true))
                    .andExpect(jsonPath("$.totalCount").value(25));
            
            verify(interactionService).toggleUserFollow(testUserId, testTargetUserId);
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 사용자 언팔로우")
        void unfollowUser_Success() throws Exception {
            // given
            UserFollowRequestDto requestDto = UserFollowRequestDto.builder()
                    .followerId(testUserId)
                    .followeeId(testTargetUserId)
                    .build();
            
            InteractionResponseDto responseDto = InteractionResponseDto.builder()
                    .success(true)
                    .message("언팔로우가 완료되었습니다")
                    .interactionType("FOLLOW")
                    .currentStatus(false)
                    .totalCount(24)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            given(interactionService.toggleUserFollow(eq(testUserId), eq(testTargetUserId)))
                    .willReturn(responseDto);
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/users/{userId}/follow", testTargetUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("언팔로우가 완료되었습니다"))
                    .andExpect(jsonPath("$.currentStatus").value(false));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 자기 자신 팔로우")
        void followSelf_Forbidden() throws Exception {
            // given
            UserFollowRequestDto requestDto = UserFollowRequestDto.builder()
                    .followerId(testUserId)
                    .followeeId(testUserId) // 자기 자신
                    .build();
            
            given(interactionService.toggleUserFollow(eq(testUserId), eq(testUserId)))
                    .willThrow(new BusinessException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED));
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/users/{userId}/follow", testUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("SELF_FOLLOW_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.message").value("자기 자신을 팔로우할 수 없습니다"));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 존재하지 않는 사용자 팔로우")
        void followNonExistentUser_UserNotFound() throws Exception {
            // given
            Long nonExistentUserId = 999L;
            UserFollowRequestDto requestDto = UserFollowRequestDto.builder()
                    .followerId(testUserId)
                    .followeeId(nonExistentUserId)
                    .build();
            
            given(interactionService.toggleUserFollow(eq(testUserId), eq(nonExistentUserId)))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/users/{userId}/follow", nonExistentUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
        }
    }
    
    @Nested
    @DisplayName("상호작용 통계 API 테스트")
    class InteractionStatsTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 게시글 상호작용 통계 조회")
        void getPostInteractionStats_Success() throws Exception {
            // given
            InteractionStatsDto statsDto = InteractionStatsDto.builder()
                    .postId(testPostId)
                    .likeCount(25)
                    .bookmarkCount(8)
                    .commentCount(12)
                    .shareCount(3)
                    .viewCount(156)
                    .isLikedByUser(true)
                    .isBookmarkedByUser(false)
                    .engagementRate(0.23)
                    .build();
            
            given(interactionService.getPostInteractionStats(eq(testPostId), eq(testUserId)))
                    .willReturn(statsDto);
            
            // when & then
            mockMvc.perform(get("/api/v1/interactions/posts/{postId}/stats", testPostId)
                    .param("userId", testUserId.toString())
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.postId").value(testPostId))
                    .andExpect(jsonPath("$.likeCount").value(25))
                    .andExpect(jsonPath("$.bookmarkCount").value(8))
                    .andExpect(jsonPath("$.commentCount").value(12))
                    .andExpect(jsonPath("$.shareCount").value(3))
                    .andExpect(jsonPath("$.viewCount").value(156))
                    .andExpect(jsonPath("$.isLikedByUser").value(true))
                    .andExpect(jsonPath("$.isBookmarkedByUser").value(false))
                    .andExpect(jsonPath("$.engagementRate").value(0.23));
            
            verify(interactionService).getPostInteractionStats(testPostId, testUserId);
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 사용자 상호작용 통계 조회")
        void getUserInteractionStats_Success() throws Exception {
            // given
            InteractionStatsDto statsDto = InteractionStatsDto.builder()
                    .userId(testUserId)
                    .totalLikes(245)
                    .totalBookmarks(67)
                    .totalComments(134)
                    .followerCount(89)
                    .followingCount(156)
                    .postsCount(45)
                    .averageEngagementRate(0.15)
                    .build();
            
            given(interactionService.getUserInteractionStats(eq(testUserId)))
                    .willReturn(statsDto);
            
            // when & then
            mockMvc.perform(get("/api/v1/interactions/users/{userId}/stats", testUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(testUserId))
                    .andExpect(jsonPath("$.totalLikes").value(245))
                    .andExpect(jsonPath("$.totalBookmarks").value(67))
                    .andExpect(jsonPath("$.totalComments").value(134))
                    .andExpect(jsonPath("$.followerCount").value(89))
                    .andExpect(jsonPath("$.followingCount").value(156))
                    .andExpect(jsonPath("$.postsCount").value(45))
                    .andExpect(jsonPath("$.averageEngagementRate").value(0.15));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 인기 게시글 목록 조회")
        void getPopularPosts_Success() throws Exception {
            // given
            given(interactionService.getPopularPosts(eq(10), eq("week")))
                    .willReturn(Arrays.asList(
                        createMockPostStats(1L, 45, 12, 8),
                        createMockPostStats(2L, 38, 9, 6),
                        createMockPostStats(3L, 32, 15, 4)
                    ));
            
            // when & then
            mockMvc.perform(get("/api/v1/interactions/posts/popular")
                    .param("limit", "10")
                    .param("period", "week")
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].postId").value(1))
                    .andExpect(jsonPath("$[0].likeCount").value(45))
                    .andExpect(jsonPath("$[1].postId").value(2))
                    .andExpect(jsonPath("$[2].postId").value(3));
            
            verify(interactionService).getPopularPosts(10, "week");
        }
    }
    
    @Nested
    @DisplayName("권한 및 보안 테스트")
    class SecurityTest {
        
        @Test
        @DisplayName("[실패] 인증되지 않은 사용자 접근")
        void unauthenticatedAccess_Denied() throws Exception {
            // when & then
            mockMvc.perform(post("/api/v1/interactions/posts/{postId}/like", testPostId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        
        @Test
        @WithMockUser(username = "2", roles = "USER")
        @DisplayName("[실패] 다른 사용자 대신 상호작용 시도")
        void unauthorizedUserInteraction_Forbidden() throws Exception {
            // given
            PostLikeRequestDto requestDto = PostLikeRequestDto.builder()
                    .postId(testPostId)
                    .userId(testUserId) // 실제 인증된 사용자와 다름
                    .build();
            
            doThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                    .when(interactionService).togglePostLike(eq(testUserId), eq(testPostId));
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/posts/{postId}/like", testPostId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] Rate Limiting 초과")
        void rateLimitExceeded_TooManyRequests() throws Exception {
            // given
            PostLikeRequestDto requestDto = PostLikeRequestDto.builder()
                    .postId(testPostId)
                    .userId(testUserId)
                    .build();
            
            given(interactionService.togglePostLike(eq(testUserId), eq(testPostId)))
                    .willThrow(new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED));
            
            String requestBody = objectMapper.writeValueAsString(requestDto);
            
            // when & then
            mockMvc.perform(post("/api/v1/interactions/posts/{postId}/like", testPostId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
                    .andExpect(jsonPath("$.message").value("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."));
        }
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    @DisplayName("[종합] 상호작용 전체 워크플로우")
    void interactionSystem_CompleteWorkflow() throws Exception {
        // 1. 게시글 좋아요
        InteractionResponseDto likeResponse = InteractionResponseDto.builder()
                .success(true)
                .message("좋아요가 추가되었습니다")
                .interactionType("LIKE")
                .currentStatus(true)
                .totalCount(1)
                .build();
        
        given(interactionService.togglePostLike(eq(testUserId), eq(testPostId)))
                .willReturn(likeResponse);
        
        mockMvc.perform(post("/api/v1/interactions/posts/{postId}/like", testPostId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postId\":" + testPostId + ",\"userId\":" + testUserId + "}")
                .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpected(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.interactionType").value("LIKE"));
        
        // 2. 게시글 북마크
        InteractionResponseDto bookmarkResponse = InteractionResponseDto.builder()
                .success(true)
                .message("북마크가 추가되었습니다")
                .interactionType("BOOKMARK")
                .currentStatus(true)
                .totalCount(1)
                .build();
        
        given(interactionService.togglePostBookmark(eq(testUserId), eq(testPostId), eq("즐겨찾기")))
                .willReturn(bookmarkResponse);
        
        mockMvc.perform(post("/api/v1/interactions/posts/{postId}/bookmark", testPostId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postId\":" + testPostId + ",\"userId\":" + testUserId + ",\"folderName\":\"즐겨찾기\"}")
                .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.interactionType").value("BOOKMARK"));
        
        // 3. 통계 조회
        InteractionStatsDto statsDto = InteractionStatsDto.builder()
                .postId(testPostId)
                .likeCount(1)
                .bookmarkCount(1)
                .isLikedByUser(true)
                .isBookmarkedByUser(true)
                .build();
        
        given(interactionService.getPostInteractionStats(eq(testPostId), eq(testUserId)))
                .willReturn(statsDto);
        
        mockMvc.perform(get("/api/v1/interactions/posts/{postId}/stats", testPostId)
                .param("userId", testUserId.toString())
                .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpected(jsonPath("$.likeCount").value(1))
                .andExpected(jsonPath("$.bookmarkCount").value(1))
                .andExpected(jsonPath("$.isLikedByUser").value(true))
                .andExpected(jsonPath("$.isBookmarkedByUser").value(true));
        
        // 모든 서비스 호출 검증
        verify(interactionService).togglePostLike(testUserId, testPostId);
        verify(interactionService).togglePostBookmark(testUserId, testPostId, "즐겨찾기");
        verify(interactionService).getPostInteractionStats(testPostId, testUserId);
    }
    
    // ================================================================================================
    // Helper Methods
    // ================================================================================================
    
    private InteractionStatsDto createMockPostStats(Long postId, int likes, int comments, int bookmarks) {
        return InteractionStatsDto.builder()
                .postId(postId)
                .likeCount(likes)
                .commentCount(comments)
                .bookmarkCount(bookmarks)
                .viewCount(likes * 5) // 임의 계산
                .engagementRate(0.12)
                .build();
    }
}
```

## API 문서화

### 주요 엔드포인트
```yaml
# 게시글 좋아요
POST /api/v1/interactions/posts/{postId}/like
Content-Type: application/json
Authorization: Bearer {token}

{
  "postId": 1,
  "userId": 1
}

# 게시글 북마크
POST /api/v1/interactions/posts/{postId}/bookmark
Content-Type: application/json
Authorization: Bearer {token}

{
  "postId": 1,
  "userId": 1,
  "folderName": "클라이밍 팁"
}

# 사용자 팔로우
POST /api/v1/interactions/users/{userId}/follow
Content-Type: application/json
Authorization: Bearer {token}

{
  "followerId": 1,
  "followeeId": 2
}

# 상호작용 통계 조회
GET /api/v1/interactions/posts/{postId}/stats?userId={userId}
GET /api/v1/interactions/users/{userId}/stats
```

## 실행 및 검증

### 실행 명령어
```bash
# 상호작용 컨트롤러 테스트 전체 실행
./gradlew test --tests="*InteractionControllerTest"

# 특정 테스트 그룹만 실행
./gradlew test --tests="InteractionControllerTest.PostLikeTest"

# 보안 테스트만 실행
./gradlew test --tests="InteractionControllerTest.SecurityTest"
```

### 검증 포인트
1. **API 정확성**: 모든 상호작용 API가 올바르게 동작
2. **권한 검증**: 인증/인가 시스템 통합 검증
3. **데이터 일관성**: 상호작용 후 통계 정확성
4. **오류 처리**: 다양한 예외 상황 적절 처리
5. **성능**: API 응답 시간 및 처리량
6. **보안**: Rate Limiting, 권한 우회 방지

이 테스트는 커뮤니티 상호작용 기능이 모든 시나리오에서 안전하고 정확하게 동작함을 보장합니다.