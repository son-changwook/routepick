# Step 7-5a: 커뮤니티 Controllers 구현

## 📋 구현 목표
커뮤니티 관리를 위한 RESTful API Controllers 구현:
1. **PostController** - 게시글 CRUD, 검색, 상호작용 관리
2. **CommentController** - 댓글 관리, 계층형 구조 지원
3. **게시글 미디어 관리** - 이미지/동영상 업로드, CDN 연동
4. **실시간 상호작용** - 좋아요, 북마크, 조회수 관리
5. **보안 강화** - XSS 방지, 권한 검증, Rate Limiting

---

## 📝 1. PostController 구현

### 📁 파일 위치
```
src/main/java/com/routepick/controller/community/PostController.java
```

### 📝 구현 코드
```java
package com.routepick.controller.community;

import com.routepick.annotation.RateLimited;
import com.routepick.annotation.RateLimits;
import com.routepick.annotation.SecureTransaction;
import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.community.request.PostCreateRequest;
import com.routepick.dto.community.request.PostSearchRequest;
import com.routepick.dto.community.request.PostUpdateRequest;
import com.routepick.dto.community.response.PostResponse;
import com.routepick.security.service.DataMaskingService;
import com.routepick.service.community.PostService;
import com.routepick.service.community.InteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 게시글 관리 Controller
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Tag(name = "Post Management", description = "게시글 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class PostController {

    private final PostService postService;
    private final InteractionService interactionService;
    private final DataMaskingService dataMaskingService;

    /**
     * 게시글 목록 조회
     */
    @GetMapping
    @Operation(summary = "게시글 목록 조회", 
               description = "카테고리별, 인기도별 게시글 목록을 조회합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 검색 조건"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 200, period = 60, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getPosts(
            @Parameter(description = "카테고리 ID")
            @RequestParam(required = false) Long categoryId,
            
            @Parameter(description = "검색 키워드")
            @RequestParam(required = false) String keyword,
            
            @Parameter(description = "정렬 기준")
            @RequestParam(defaultValue = "LATEST") PostSearchRequest.SortBy sortBy,
            
            @Parameter(description = "페이징 정보")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        log.debug("Getting posts: categoryId={}, keyword={}, sortBy={}", categoryId, keyword, sortBy);
        
        PostSearchRequest searchRequest = PostSearchRequest.builder()
            .categoryId(categoryId)
            .keyword(keyword)
            .sortBy(sortBy)
            .build();
        
        Page<PostResponse> posts = postService.searchPosts(searchRequest, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
            PageResponse.of(posts),
            String.format("게시글 %d개를 조회했습니다", posts.getTotalElements())));
    }

    /**
     * 게시글 상세 조회
     */
    @GetMapping("/{postId}")
    @Operation(summary = "게시글 상세 조회", 
               description = "특정 게시글의 상세 정보를 조회하고 조회수를 증가시킵니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostAuthorize("@postSecurityService.canViewPost(returnObject.body.data, authentication.principal.userId)")
    @RateLimited(requests = 300, period = 60, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    public ResponseEntity<ApiResponse<PostResponse>> getPost(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Getting post details: postId={}, userId={}", 
                 postId, dataMaskingService.maskUserId(userId));
        
        PostResponse post = postService.getPostWithIncrementView(postId, userId);
        
        return ResponseEntity.ok(ApiResponse.success(
            post, 
            "게시글을 조회했습니다"));
    }

    /**
     * 게시글 작성
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "게시글 작성", 
               description = "새로운 게시글을 작성합니다. 이미지/동영상 업로드 지원")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "201", description = "작성 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력 데이터"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "413", description = "파일 크기 초과"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimits({
        @RateLimited(requests = 30, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 20, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP) // 스팸 방지
    })
    @SecureTransaction(personalData = true, auditLevel = "INFO")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @ModelAttribute PostCreateRequest request,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Creating post: userId={}, categoryId={}, title={}", 
                dataMaskingService.maskUserId(userId), request.getCategoryId(), 
                dataMaskingService.maskText(request.getTitle()));
        
        PostResponse createdPost = postService.createPost(userId, request);
        
        return ResponseEntity.status(201).body(ApiResponse.success(
            createdPost, 
            "게시글이 작성되었습니다"));
    }

    /**
     * 게시글 수정
     */
    @PutMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "게시글 수정", 
               description = "기존 게시글을 수정합니다. 이미지 추가/삭제 지원")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "수정 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력 데이터"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "403", description = "권한 없음"),
        @SwaggerApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("@postSecurityService.canModifyPost(#postId, authentication.principal.userId)")
    @RateLimited(requests = 50, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    @SecureTransaction(personalData = true, auditLevel = "INFO")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @Valid @ModelAttribute PostUpdateRequest request,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Updating post: postId={}, userId={}", postId, dataMaskingService.maskUserId(userId));
        
        PostResponse updatedPost = postService.updatePost(postId, userId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            updatedPost, 
            "게시글이 수정되었습니다"));
    }

    /**
     * 게시글 삭제
     */
    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제", 
               description = "게시글을 삭제합니다. 연관된 미디어 파일도 함께 삭제됩니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "삭제 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "403", description = "권한 없음"),
        @SwaggerApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("@postSecurityService.canModifyPost(#postId, authentication.principal.userId)")
    @RateLimited(requests = 20, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    @SecureTransaction(personalData = true, auditLevel = "WARN")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @AuthenticationPrincipal Long userId) {
        
        log.warn("Deleting post: postId={}, userId={}", postId, dataMaskingService.maskUserId(userId));
        
        postService.deletePost(postId, userId);
        
        return ResponseEntity.ok(ApiResponse.success(
            null, 
            "게시글이 삭제되었습니다"));
    }

    /**
     * 게시글 좋아요
     */
    @PostMapping("/{postId}/like")
    @Operation(summary = "게시글 좋아요", 
               description = "게시글에 좋아요를 추가하거나 취소합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "좋아요 처리 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    @SecureTransaction(personalData = true)
    public ResponseEntity<ApiResponse<PostLikeResponse>> togglePostLike(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Toggling post like: postId={}, userId={}", 
                 postId, dataMaskingService.maskUserId(userId));
        
        PostLikeResponse likeResult = interactionService.togglePostLike(userId, postId);
        
        return ResponseEntity.ok(ApiResponse.success(
            likeResult, 
            likeResult.isLiked() ? "게시글에 좋아요를 눌렀습니다" : "게시글 좋아요를 취소했습니다"));
    }

    /**
     * 게시글 북마크
     */
    @PostMapping("/{postId}/bookmark")
    @Operation(summary = "게시글 북마크", 
               description = "게시글을 북마크에 추가하거나 제거합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "북마크 처리 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 50, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    @SecureTransaction(personalData = true)
    public ResponseEntity<ApiResponse<PostBookmarkResponse>> togglePostBookmark(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Toggling post bookmark: postId={}, userId={}", 
                 postId, dataMaskingService.maskUserId(userId));
        
        PostBookmarkResponse bookmarkResult = interactionService.togglePostBookmark(userId, postId);
        
        return ResponseEntity.ok(ApiResponse.success(
            bookmarkResult, 
            bookmarkResult.isBookmarked() ? "게시글을 북마크했습니다" : "북마크를 해제했습니다"));
    }

    // ========== 내부 Response DTO ==========

    /**
     * 게시글 좋아요 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "게시글 좋아요 응답")
    public static class PostLikeResponse {
        @Schema(description = "좋아요 여부", example = "true")
        private Boolean isLiked;
        
        @Schema(description = "총 좋아요 수", example = "42")
        private Integer totalLikes;
        
        @Schema(description = "처리 시간")
        private java.time.LocalDateTime processedAt;
    }

    /**
     * 게시글 북마크 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "게시글 북마크 응답")
    public static class PostBookmarkResponse {
        @Schema(description = "북마크 여부", example = "true")
        private Boolean isBookmarked;
        
        @Schema(description = "총 북마크 수", example = "28")
        private Integer totalBookmarks;
        
        @Schema(description = "처리 시간")
        private java.time.LocalDateTime processedAt;
    }
}
```

---

## 💬 2. CommentController 구현

### 📁 파일 위치
```
src/main/java/com/routepick/controller/community/CommentController.java
```

### 📝 구현 코드
```java
package com.routepick.controller.community;

import com.routepick.annotation.RateLimited;
import com.routepick.annotation.RateLimits;
import com.routepick.annotation.SecureTransaction;
import com.routepick.common.ApiResponse;
import com.routepick.common.PageResponse;
import com.routepick.dto.community.request.CommentCreateRequest;
import com.routepick.dto.community.response.CommentResponse;
import com.routepick.security.service.DataMaskingService;
import com.routepick.service.community.CommentService;
import com.routepick.service.community.InteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 댓글 관리 Controller
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comment Management", description = "댓글 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class CommentController {

    private final CommentService commentService;
    private final InteractionService interactionService;
    private final DataMaskingService dataMaskingService;

    /**
     * 댓글 목록 조회 (계층형)
     */
    @GetMapping
    @Operation(summary = "댓글 목록 조회", 
               description = "게시글의 댓글 목록을 계층형 구조로 조회합니다 (3단계 depth)")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        @SwaggerApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @RateLimited(requests = 200, period = 60, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> getComments(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @Parameter(description = "페이징 정보")
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        
        log.debug("Getting comments for post: postId={}", postId);
        
        Page<CommentResponse> comments = commentService.getCommentsHierarchy(postId, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
            PageResponse.of(comments),
            String.format("댓글 %d개를 조회했습니다", comments.getTotalElements())));
    }

    /**
     * 댓글 작성
     */
    @PostMapping
    @Operation(summary = "댓글 작성", 
               description = "게시글에 댓글을 작성합니다. 대댓글 작성 지원 (최대 3단계)")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "201", description = "댓글 작성 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력 데이터"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "422", description = "댓글 깊이 초과"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimits({
        @RateLimited(requests = 60, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP) // 스팸 방지
    })
    @SecureTransaction(personalData = true, auditLevel = "INFO")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @Valid @RequestBody CommentCreateRequest request,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Creating comment: postId={}, userId={}, parentId={}", 
                postId, dataMaskingService.maskUserId(userId), request.getParentId());
        
        // 댓글 깊이 검증
        if (request.getParentId() != null) {
            int depth = commentService.calculateCommentDepth(request.getParentId());
            if (depth >= 3) {
                throw new IllegalArgumentException("댓글은 최대 3단계까지만 가능합니다");
            }
        }
        
        CommentResponse createdComment = commentService.createComment(userId, postId, request);
        
        return ResponseEntity.status(201).body(ApiResponse.success(
            createdComment, 
            "댓글이 작성되었습니다"));
    }

    /**
     * 댓글 수정
     */
    @PutMapping("/{commentId}")
    @Operation(summary = "댓글 수정", 
               description = "작성한 댓글을 수정합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "댓글 수정 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력 데이터"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "403", description = "권한 없음"),
        @SwaggerApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("@commentSecurityService.canModifyComment(#commentId, authentication.principal.userId)")
    @RateLimited(requests = 30, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    @SecureTransaction(personalData = true, auditLevel = "INFO")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @Parameter(description = "댓글 ID", required = true)
            @PathVariable Long commentId,
            
            @Valid @RequestBody CommentCreateRequest request,
            
            @AuthenticationPrincipal Long userId) {
        
        log.info("Updating comment: commentId={}, userId={}", commentId, dataMaskingService.maskUserId(userId));
        
        CommentResponse updatedComment = commentService.updateComment(commentId, userId, request);
        
        return ResponseEntity.ok(ApiResponse.success(
            updatedComment, 
            "댓글이 수정되었습니다"));
    }

    /**
     * 댓글 삭제
     */
    @DeleteMapping("/{commentId}")
    @Operation(summary = "댓글 삭제", 
               description = "댓글을 삭제합니다. 대댓글이 있는 경우 '삭제된 댓글' 메시지로 표시")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "댓글 삭제 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "403", description = "권한 없음"),
        @SwaggerApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("@commentSecurityService.canModifyComment(#commentId, authentication.principal.userId)")
    @RateLimited(requests = 20, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    @SecureTransaction(personalData = true, auditLevel = "WARN")
    public ResponseEntity<ApiResponse<CommentDeletionResponse>> deleteComment(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @Parameter(description = "댓글 ID", required = true)
            @PathVariable Long commentId,
            
            @AuthenticationPrincipal Long userId) {
        
        log.warn("Deleting comment: commentId={}, userId={}", commentId, dataMaskingService.maskUserId(userId));
        
        CommentDeletionResponse deletionResult = commentService.deleteComment(commentId, userId);
        
        return ResponseEntity.ok(ApiResponse.success(
            deletionResult, 
            deletionResult.isPermanentlyDeleted() ? "댓글이 삭제되었습니다" : "댓글이 숨김 처리되었습니다"));
    }

    /**
     * 댓글 좋아요
     */
    @PostMapping("/{commentId}/like")
    @Operation(summary = "댓글 좋아요", 
               description = "댓글에 좋아요를 추가하거나 취소합니다")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "좋아요 처리 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 필요"),
        @SwaggerApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
        @SwaggerApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 60, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    @SecureTransaction(personalData = true)
    public ResponseEntity<ApiResponse<CommentLikeResponse>> toggleCommentLike(
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable Long postId,
            
            @Parameter(description = "댓글 ID", required = true)
            @PathVariable Long commentId,
            
            @AuthenticationPrincipal Long userId) {
        
        log.debug("Toggling comment like: commentId={}, userId={}", 
                 commentId, dataMaskingService.maskUserId(userId));
        
        CommentLikeResponse likeResult = interactionService.toggleCommentLike(userId, commentId);
        
        return ResponseEntity.ok(ApiResponse.success(
            likeResult, 
            likeResult.isLiked() ? "댓글에 좋아요를 눌렀습니다" : "댓글 좋아요를 취소했습니다"));
    }

    // ========== 내부 Response DTO ==========

    /**
     * 댓글 삭제 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "댓글 삭제 응답")
    public static class CommentDeletionResponse {
        @Schema(description = "완전 삭제 여부 (대댓글 없음)", example = "true")
        private Boolean isPermanentlyDeleted;
        
        @Schema(description = "남은 대댓글 수", example = "3")
        private Integer remainingRepliesCount;
        
        @Schema(description = "삭제 시간")
        private java.time.LocalDateTime deletedAt;
    }

    /**
     * 댓글 좋아요 응답 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Schema(description = "댓글 좋아요 응답")
    public static class CommentLikeResponse {
        @Schema(description = "좋아요 여부", example = "true")
        private Boolean isLiked;
        
        @Schema(description = "총 좋아요 수", example = "15")
        private Integer totalLikes;
        
        @Schema(description = "처리 시간")
        private java.time.LocalDateTime processedAt;
    }
}
```

---

## 📋 구현 완료 사항
✅ **PostController** - 게시글 관리 완전한 REST API (7개 엔드포인트)  
✅ **CommentController** - 댓글 관리 완전한 REST API (5개 엔드포인트)  
✅ **계층형 댓글 시스템** - 3단계 depth 지원, 대댓글 관리  
✅ **실시간 상호작용** - 좋아요, 북마크, 조회수 실시간 업데이트  
✅ **미디어 업로드** - 멀티파트 폼 데이터 지원, CDN 연동  
✅ **보안 강화** - XSS 방지, 권한 검증, Rate Limiting  
✅ **완전한 문서화** - Swagger 어노테이션 완벽 적용  

## 🎯 주요 특징
- **계층형 댓글**: 3단계 depth 지원, 대댓글 관리
- **스팸 방지**: 복합 Rate Limiting (사용자별 + IP별)
- **미디어 관리**: 이미지/동영상 업로드, 썸네일 자동 생성
- **실시간 카운터**: Redis 기반 좋아요/조회수 실시간 업데이트
- **XSS 보안**: 모든 입력 내용 자동 정제
- **소프트 삭제**: 대댓글 있는 댓글은 숨김 처리

## ⚙️ Rate Limiting 전략
- **게시글 조회**: 200-300회/분 (IP별)
- **게시글 작성**: 30회/분 + 20회/5분 (스팸 방지)
- **댓글 작성**: 60회/분 + 30회/5분 (스팸 방지)
- **좋아요/북마크**: 100회/분 (사용자별)
- **수정/삭제**: 20-50회/분 (신중한 작업)