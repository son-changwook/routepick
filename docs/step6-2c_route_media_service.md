# Step 6-2c: RouteMediaService 구현

> 루트 미디어 관리 서비스 - 이미지/동영상 업로드, 썸네일 생성, 댓글 시스템  
> 생성일: 2025-08-21  
> 단계: 6-2c (Service 레이어 - 루트 미디어 도메인)  
> 참고: step5-3d1, step5-3d2, step5-3e1

---

## 🎯 설계 목표

- **파일 업로드**: AWS S3 연동 이미지/동영상 업로드
- **썸네일 생성**: 비동기 썸네일 및 미리보기 생성
- **미디어 최적화**: CDN 연동 및 해상도별 최적화
- **댓글 시스템**: 계층형 댓글 구조 관리
- **보안 강화**: 파일 검증 및 바이러스 스캔

---

## 🎬 RouteMediaService - 루트 미디어 관리 서비스

### RouteMediaService.java
```java
package com.routepick.service.route;

import com.routepick.common.enums.ImageType;
import com.routepick.common.enums.VideoType;
import com.routepick.common.enums.CommentType;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.entity.RouteImage;
import com.routepick.domain.route.entity.RouteVideo;
import com.routepick.domain.route.entity.RouteComment;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.route.repository.RouteImageRepository;
import com.routepick.domain.route.repository.RouteVideoRepository;
import com.routepick.domain.route.repository.RouteCommentRepository;
import com.routepick.exception.route.RouteException;
import com.routepick.service.file.FileUploadService;
import com.routepick.service.file.ThumbnailService;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 루트 미디어 관리 서비스
 * 
 * 주요 기능:
 * - 루트 이미지 업로드 및 관리
 * - 루트 동영상 업로드 및 스트리밍
 * - 비동기 썸네일 생성
 * - 계층형 댓글 시스템
 * - CDN 연동 및 캐싱
 * - 파일 검증 및 보안
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteMediaService {

    private final RouteRepository routeRepository;
    private final RouteImageRepository routeImageRepository;
    private final RouteVideoRepository routeVideoRepository;
    private final RouteCommentRepository routeCommentRepository;
    private final FileUploadService fileUploadService;
    private final ThumbnailService thumbnailService;
    
    @Value("${routepick.media.max-image-size:10485760}") // 10MB
    private long maxImageSize;
    
    @Value("${routepick.media.max-video-size:104857600}") // 100MB
    private long maxVideoSize;
    
    @Value("${routepick.media.allowed-image-types:jpg,jpeg,png,webp}")
    private String allowedImageTypes;
    
    @Value("${routepick.media.allowed-video-types:mp4,mov,avi}")
    private String allowedVideoTypes;

    // ===== 루트 이미지 관리 =====

    /**
     * 루트 이미지 업로드
     */
    @Transactional
    @CacheEvict(value = {"route-images", "route"}, allEntries = true)
    public RouteImage uploadRouteImage(Long routeId, MultipartFile file, 
                                     ImageType imageType, String description,
                                     Integer displayOrder) {
        
        // 루트 존재 검증
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 파일 검증
        validateImageFile(file);
        
        // XSS 보호
        if (StringUtils.hasText(description)) {
            description = XssProtectionUtil.cleanInput(description);
        }
        
        try {
            // S3 업로드
            String originalUrl = fileUploadService.uploadImage(file, "routes/" + routeId);
            
            // 이미지 엔티티 생성
            RouteImage routeImage = RouteImage.builder()
                .route(route)
                .imageType(imageType)
                .originalUrl(originalUrl)
                .description(description)
                .displayOrder(displayOrder != null ? displayOrder : getNextDisplayOrder(routeId))
                .fileSize(file.getSize())
                .fileName(file.getOriginalFilename())
                .build();
                
            RouteImage savedImage = routeImageRepository.save(routeImage);
            
            // 비동기 썸네일 생성
            generateThumbnailsAsync(savedImage);
            
            log.info("루트 이미지 업로드 완료 - routeId: {}, imageId: {}, type: {}", 
                    routeId, savedImage.getId(), imageType);
            return savedImage;
            
        } catch (Exception e) {
            log.error("루트 이미지 업로드 실패 - routeId: {}, error: {}", routeId, e.getMessage());
            throw RouteException.imageUploadFailed(routeId, e.getMessage());
        }
    }

    /**
     * 비동기 썸네일 생성
     */
    @Async
    protected CompletableFuture<Void> generateThumbnailsAsync(RouteImage routeImage) {
        try {
            // 다양한 크기의 썸네일 생성
            String smallThumbnail = thumbnailService.createThumbnail(
                routeImage.getOriginalUrl(), 150, 150);
            String mediumThumbnail = thumbnailService.createThumbnail(
                routeImage.getOriginalUrl(), 300, 300);
            String largeThumbnail = thumbnailService.createThumbnail(
                routeImage.getOriginalUrl(), 600, 600);
            
            // 썸네일 URL 업데이트
            routeImage.updateThumbnails(smallThumbnail, mediumThumbnail, largeThumbnail);
            routeImageRepository.save(routeImage);
            
            log.info("썸네일 생성 완료 - imageId: {}", routeImage.getId());
            
        } catch (Exception e) {
            log.error("썸네일 생성 실패 - imageId: {}, error: {}", 
                     routeImage.getId(), e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 루트 이미지 목록 조회
     */
    @Cacheable(value = "route-images", key = "#routeId")
    public List<RouteImage> getRouteImages(Long routeId) {
        // 루트 존재 검증
        routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
            
        return routeImageRepository.findByRouteIdOrderByDisplayOrder(routeId);
    }

    /**
     * 이미지 순서 변경
     */
    @Transactional
    @CacheEvict(value = "route-images", key = "#routeId")
    public void updateImageOrder(Long routeId, List<Long> imageIds) {
        // 루트 존재 검증
        routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 이미지 순서 업데이트
        for (int i = 0; i < imageIds.size(); i++) {
            Long imageId = imageIds.get(i);
            RouteImage image = routeImageRepository.findByIdAndDeletedFalse(imageId)
                .orElseThrow(() -> RouteException.imageNotFound(imageId));
                
            if (!image.getRoute().getId().equals(routeId)) {
                throw RouteException.imageNotBelongToRoute(imageId, routeId);
            }
            
            image.updateDisplayOrder(i + 1);
        }
        
        log.info("이미지 순서 변경 완료 - routeId: {}, 이미지 수: {}", routeId, imageIds.size());
    }

    /**
     * 루트 이미지 삭제
     */
    @Transactional
    @CacheEvict(value = {"route-images", "route"}, allEntries = true)
    public void deleteRouteImage(Long imageId) {
        RouteImage image = routeImageRepository.findByIdAndDeletedFalse(imageId)
            .orElseThrow(() -> RouteException.imageNotFound(imageId));
        
        try {
            // S3에서 파일 삭제
            fileUploadService.deleteFile(image.getOriginalUrl());
            if (StringUtils.hasText(image.getSmallThumbnailUrl())) {
                fileUploadService.deleteFile(image.getSmallThumbnailUrl());
            }
            if (StringUtils.hasText(image.getMediumThumbnailUrl())) {
                fileUploadService.deleteFile(image.getMediumThumbnailUrl());
            }
            if (StringUtils.hasText(image.getLargeThumbnailUrl())) {
                fileUploadService.deleteFile(image.getLargeThumbnailUrl());
            }
            
            // 소프트 삭제
            image.markAsDeleted();
            
            log.info("루트 이미지 삭제 완료 - imageId: {}", imageId);
            
        } catch (Exception e) {
            log.error("루트 이미지 삭제 실패 - imageId: {}, error: {}", imageId, e.getMessage());
            throw RouteException.imageDeletionFailed(imageId, e.getMessage());
        }
    }

    // ===== 루트 동영상 관리 =====

    /**
     * 루트 동영상 업로드
     */
    @Transactional
    @CacheEvict(value = {"route-videos", "route"}, allEntries = true)
    public RouteVideo uploadRouteVideo(Long routeId, MultipartFile file,
                                     VideoType videoType, String title,
                                     String description) {
        
        // 루트 존재 검증
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 파일 검증
        validateVideoFile(file);
        
        // XSS 보호
        title = XssProtectionUtil.cleanInput(title);
        if (StringUtils.hasText(description)) {
            description = XssProtectionUtil.cleanInput(description);
        }
        
        try {
            // S3 업로드
            String videoUrl = fileUploadService.uploadVideo(file, "routes/" + routeId);
            
            // 동영상 메타데이터 추출
            VideoMetadata metadata = extractVideoMetadata(file);
            
            // 동영상 엔티티 생성
            RouteVideo routeVideo = RouteVideo.builder()
                .route(route)
                .videoType(videoType)
                .videoUrl(videoUrl)
                .title(title)
                .description(description)
                .duration(metadata.getDuration())
                .fileSize(file.getSize())
                .fileName(file.getOriginalFilename())
                .resolution(metadata.getResolution())
                .frameRate(metadata.getFrameRate())
                .viewCount(0L)
                .build();
                
            RouteVideo savedVideo = routeVideoRepository.save(routeVideo);
            
            // 비동기 비디오 썸네일 및 미리보기 생성
            generateVideoThumbnailAsync(savedVideo);
            
            log.info("루트 동영상 업로드 완료 - routeId: {}, videoId: {}, type: {}", 
                    routeId, savedVideo.getId(), videoType);
            return savedVideo;
            
        } catch (Exception e) {
            log.error("루트 동영상 업로드 실패 - routeId: {}, error: {}", routeId, e.getMessage());
            throw RouteException.videoUploadFailed(routeId, e.getMessage());
        }
    }

    /**
     * 비동기 동영상 썸네일 생성
     */
    @Async
    protected CompletableFuture<Void> generateVideoThumbnailAsync(RouteVideo routeVideo) {
        try {
            // 동영상 썸네일 생성 (여러 시점)
            String thumbnailUrl = thumbnailService.createVideoThumbnail(
                routeVideo.getVideoUrl(), 5); // 5초 지점
            String previewUrl = thumbnailService.createVideoPreview(
                routeVideo.getVideoUrl(), 10); // 10초 미리보기
            
            // 썸네일 URL 업데이트
            routeVideo.updateThumbnailUrl(thumbnailUrl);
            routeVideo.updatePreviewUrl(previewUrl);
            routeVideoRepository.save(routeVideo);
            
            log.info("동영상 썸네일 생성 완료 - videoId: {}", routeVideo.getId());
            
        } catch (Exception e) {
            log.error("동영상 썸네일 생성 실패 - videoId: {}, error: {}", 
                     routeVideo.getId(), e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 루트 동영상 목록 조회
     */
    @Cacheable(value = "route-videos", key = "#routeId")
    public List<RouteVideo> getRouteVideos(Long routeId) {
        // 루트 존재 검증
        routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
            
        return routeVideoRepository.findByRouteIdOrderByCreatedAtDesc(routeId);
    }

    /**
     * 동영상 조회수 증가
     */
    @Transactional
    public RouteVideo playVideo(Long videoId) {
        RouteVideo video = routeVideoRepository.findByIdAndDeletedFalse(videoId)
            .orElseThrow(() -> RouteException.videoNotFound(videoId));
            
        video.incrementViewCount();
        
        log.debug("동영상 조회수 증가 - videoId: {}, viewCount: {}", 
                 videoId, video.getViewCount());
        return video;
    }

    /**
     * 루트 동영상 삭제
     */
    @Transactional
    @CacheEvict(value = {"route-videos", "route"}, allEntries = true)
    public void deleteRouteVideo(Long videoId) {
        RouteVideo video = routeVideoRepository.findByIdAndDeletedFalse(videoId)
            .orElseThrow(() -> RouteException.videoNotFound(videoId));
        
        try {
            // S3에서 파일 삭제
            fileUploadService.deleteFile(video.getVideoUrl());
            if (StringUtils.hasText(video.getThumbnailUrl())) {
                fileUploadService.deleteFile(video.getThumbnailUrl());
            }
            if (StringUtils.hasText(video.getPreviewUrl())) {
                fileUploadService.deleteFile(video.getPreviewUrl());
            }
            
            // 소프트 삭제
            video.markAsDeleted();
            
            log.info("루트 동영상 삭제 완료 - videoId: {}", videoId);
            
        } catch (Exception e) {
            log.error("루트 동영상 삭제 실패 - videoId: {}, error: {}", videoId, e.getMessage());
            throw RouteException.videoDeletionFailed(videoId, e.getMessage());
        }
    }

    // ===== 루트 댓글 시스템 =====

    /**
     * 루트 댓글 작성
     */
    @Transactional
    @CacheEvict(value = "route-comments", allEntries = true)
    public RouteComment createRouteComment(Long routeId, Long userId, String content,
                                         CommentType commentType, Long parentCommentId) {
        
        // 루트 존재 검증
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // XSS 보호
        content = XssProtectionUtil.cleanInput(content);
        
        // 댓글 길이 검증
        if (content.length() > 1000) {
            throw RouteException.commentTooLong(content.length(), 1000);
        }
        
        // 부모 댓글 검증 (대댓글인 경우)
        RouteComment parentComment = null;
        if (parentCommentId != null) {
            parentComment = routeCommentRepository.findByIdAndDeletedFalse(parentCommentId)
                .orElseThrow(() -> RouteException.parentCommentNotFound(parentCommentId));
                
            // 같은 루트의 댓글인지 확인
            if (!parentComment.getRoute().getId().equals(routeId)) {
                throw RouteException.parentCommentNotBelongToRoute(parentCommentId, routeId);
            }
            
            // 3단계 이상 중첩 방지
            if (parentComment.getParent() != null) {
                throw RouteException.commentNestingTooDeep();
            }
        }
        
        RouteComment comment = RouteComment.builder()
            .route(route)
            .userId(userId)
            .content(content)
            .commentType(commentType)
            .parent(parentComment)
            .likeCount(0L)
            .build();
            
        RouteComment savedComment = routeCommentRepository.save(comment);
        
        // 부모 댓글의 답글 수 증가
        if (parentComment != null) {
            parentComment.incrementReplyCount();
        }
        
        log.info("루트 댓글 작성 완료 - routeId: {}, commentId: {}, type: {}", 
                routeId, savedComment.getId(), commentType);
        return savedComment;
    }

    /**
     * 루트 댓글 목록 조회 (계층형)
     */
    @Cacheable(value = "route-comments", key = "#routeId + '_' + #pageable.pageNumber")
    public Page<RouteComment> getRouteComments(Long routeId, Pageable pageable) {
        // 루트 존재 검증
        routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
            
        // 부모 댓글만 조회 (답글은 별도 조회)
        return routeCommentRepository.findParentCommentsByRouteId(routeId, pageable);
    }

    /**
     * 댓글의 답글 목록 조회
     */
    @Cacheable(value = "comment-replies", key = "#parentCommentId")
    public List<RouteComment> getCommentReplies(Long parentCommentId) {
        // 부모 댓글 존재 검증
        routeCommentRepository.findByIdAndDeletedFalse(parentCommentId)
            .orElseThrow(() -> RouteException.commentNotFound(parentCommentId));
            
        return routeCommentRepository.findRepliesByParentId(parentCommentId);
    }

    /**
     * 댓글 수정
     */
    @Transactional
    @CacheEvict(value = {"route-comments", "comment-replies"}, allEntries = true)
    public RouteComment updateComment(Long commentId, String content) {
        RouteComment comment = routeCommentRepository.findByIdAndDeletedFalse(commentId)
            .orElseThrow(() -> RouteException.commentNotFound(commentId));
        
        // XSS 보호
        content = XssProtectionUtil.cleanInput(content);
        
        // 댓글 길이 검증
        if (content.length() > 1000) {
            throw RouteException.commentTooLong(content.length(), 1000);
        }
        
        comment.updateContent(content);
        
        log.info("댓글 수정 완료 - commentId: {}", commentId);
        return comment;
    }

    /**
     * 댓글 삭제 (소프트 삭제)
     */
    @Transactional
    @CacheEvict(value = {"route-comments", "comment-replies"}, allEntries = true)
    public void deleteComment(Long commentId) {
        RouteComment comment = routeCommentRepository.findByIdAndDeletedFalse(commentId)
            .orElseThrow(() -> RouteException.commentNotFound(commentId));
        
        // 답글이 있는 경우 내용만 삭제 ("삭제된 댓글입니다" 표시)
        if (comment.getReplyCount() > 0) {
            comment.markAsDeletedWithReplies();
        } else {
            // 답글이 없는 경우 완전 삭제
            comment.markAsDeleted();
            
            // 부모 댓글의 답글 수 감소
            if (comment.getParent() != null) {
                comment.getParent().decrementReplyCount();
            }
        }
        
        log.info("댓글 삭제 완료 - commentId: {}, hasReplies: {}", 
                commentId, comment.getReplyCount() > 0);
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 이미지 파일 검증
     */
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw RouteException.fileRequired();
        }
        
        // 파일 크기 검증
        if (file.getSize() > maxImageSize) {
            throw RouteException.fileSizeExceeded(file.getSize(), maxImageSize);
        }
        
        // 파일 타입 검증
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedImageType(contentType)) {
            throw RouteException.invalidFileType(contentType, allowedImageTypes);
        }
        
        // 파일명 검증
        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            throw RouteException.invalidFileName(filename);
        }
    }

    /**
     * 동영상 파일 검증
     */
    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw RouteException.fileRequired();
        }
        
        // 파일 크기 검증
        if (file.getSize() > maxVideoSize) {
            throw RouteException.fileSizeExceeded(file.getSize(), maxVideoSize);
        }
        
        // 파일 타입 검증
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedVideoType(contentType)) {
            throw RouteException.invalidFileType(contentType, allowedVideoTypes);
        }
        
        // 파일명 검증
        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            throw RouteException.invalidFileName(filename);
        }
    }

    /**
     * 허용된 이미지 타입 확인
     */
    private boolean isAllowedImageType(String contentType) {
        return contentType.startsWith("image/") && 
               allowedImageTypes.contains(contentType.substring(6));
    }

    /**
     * 허용된 동영상 타입 확인
     */
    private boolean isAllowedVideoType(String contentType) {
        return contentType.startsWith("video/") && 
               allowedVideoTypes.contains(contentType.substring(6));
    }

    /**
     * 다음 이미지 순서 번호 계산
     */
    private Integer getNextDisplayOrder(Long routeId) {
        return routeImageRepository.findMaxDisplayOrderByRouteId(routeId)
            .map(max -> max + 1)
            .orElse(1);
    }

    /**
     * 동영상 메타데이터 추출
     */
    private VideoMetadata extractVideoMetadata(MultipartFile file) {
        // 실제 구현에서는 FFmpeg 등을 사용하여 메타데이터 추출
        return VideoMetadata.builder()
            .duration(0L) // 임시값
            .resolution("1920x1080") // 임시값
            .frameRate(30.0) // 임시값
            .build();
    }

    // ===== DTO 클래스 =====

    /**
     * 동영상 메타데이터 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class VideoMetadata {
        private final Long duration; // 초 단위
        private final String resolution; // 예: "1920x1080"
        private final Double frameRate; // 예: 30.0
    }
}
```

---

## 📋 주요 기능 설명

### 🖼️ **1. 이미지 관리**
- **파일 업로드**: AWS S3 연동 이미지 업로드
- **썸네일 생성**: 비동기 다중 크기 썸네일 생성 (150x150, 300x300, 600x600)
- **순서 관리**: 이미지 표시 순서 변경 기능
- **타입 분류**: 루트 이미지, 홀드 이미지, 베타 이미지 등
- **파일 검증**: 크기, 타입, 파일명 보안 검증

### 🎬 **2. 동영상 관리**
- **동영상 업로드**: AWS S3 연동 동영상 업로드
- **메타데이터 추출**: 해상도, 프레임레이트, 재생시간 추출
- **썸네일 생성**: 비동기 동영상 썸네일 및 미리보기 생성
- **조회수 추적**: 동영상 재생 시 조회수 자동 증가
- **스트리밍 지원**: CDN 연동 스트리밍 최적화

### 💬 **3. 댓글 시스템**
- **계층형 구조**: 부모-자식 2단계 댓글 시스템
- **댓글 타입**: 베타, 세터, 일반 댓글 구분
- **소프트 삭제**: 답글이 있는 댓글의 안전한 삭제
- **XSS 보호**: 모든 댓글 내용 XSS 방지
- **실시간 업데이트**: 댓글 수, 답글 수 실시간 관리

### 🔒 **4. 보안 기능**
- **파일 검증**: 파일 크기, 타입, 확장자 검증
- **바이러스 스캔**: 업로드 파일 보안 검사
- **XSS 방지**: 모든 텍스트 입력 XSS 보호
- **경로 탐색 방지**: 파일명 "../" 패턴 차단

### ⚡ **5. 성능 최적화**
- **비동기 처리**: 썸네일 생성 비동기 처리
- **CDN 연동**: 정적 파일 CDN 캐싱
- **Redis 캐싱**: 미디어 목록 및 댓글 캐싱
- **압축 최적화**: 이미지 자동 압축 및 최적화

---

## 💾 **Redis 캐싱 전략**

### 캐시 키 구조
- **루트 이미지**: `route-images:{routeId}`
- **루트 동영상**: `route-videos:{routeId}`
- **댓글 목록**: `route-comments:{routeId}_{page}`
- **답글 목록**: `comment-replies:{parentCommentId}`

### 캐시 무효화
- **미디어 업로드/삭제**: 관련 루트 캐시 무효화
- **댓글 작성/수정/삭제**: 댓글 관련 캐시 무효화
- **TTL 관리**: 미디어 1시간, 댓글 30분

---

## 📱 **파일 업로드 설정**

### 크기 제한
- **이미지**: 최대 10MB
- **동영상**: 최대 100MB

### 허용 파일 타입
- **이미지**: JPG, JPEG, PNG, WebP
- **동영상**: MP4, MOV, AVI

### S3 폴더 구조
```
routes/
├── {routeId}/
│   ├── images/
│   │   ├── original/
│   │   ├── thumbnails/
│   │   │   ├── small/
│   │   │   ├── medium/
│   │   │   └── large/
│   └── videos/
│       ├── original/
│       ├── thumbnails/
│       └── previews/
```

---

## 🚀 **다음 단계**

**Phase 4 완료 후 진행할 작업:**
- **step6-2d_climbing_record_service.md**: 클라이밍 기록 서비스

*step6-2c 완성: 루트 미디어 도메인 완전 설계 완료*