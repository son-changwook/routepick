# Step 5-3c: 루트 미디어 Repository 생성

> 루트 미디어 2개 Repository 완전 설계 (고성능 미디어 처리 특화)  
> 생성일: 2025-08-20  
> 기반: step5-3b_route_core_repositories.md, step4-3b_route_entities.md

---

## 🎯 설계 목표

- **이미지/동영상 조회 성능 최적화**: 썸네일 일괄 처리, 해상도별 최적화
- **CDN 연동 지원**: CloudFront, ImageKit 등 CDN 통합
- **모바일/데스크톱 최적화**: 디바이스별 최적화된 미디어 제공
- **스트리밍 최적화**: 동영상 트랜스코딩 및 적응형 스트리밍

---

## 🖼️ 1. RouteImageRepository - 루트 이미지 Repository

```java
package com.routepick.domain.route.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.route.entity.RouteImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RouteImage Repository
 * - 루트 이미지 전문 최적화
 * - 썸네일 일괄 처리 최적화
 * - CDN 연동 및 해상도별 최적화
 * - 모바일/데스크톱 자동 선택
 */
@Repository
public interface RouteImageRepository extends BaseRepository<RouteImage, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 루트별 이미지 정렬 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isActive = true " +
           "ORDER BY ri.displayOrder ASC")
    List<RouteImage> findByRouteIdOrderByDisplayOrder(@Param("routeId") Long routeId);
    
    /**
     * 루트별 이미지 정렬 조회 (페이징)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isActive = true " +
           "ORDER BY ri.displayOrder ASC")
    Page<RouteImage> findByRouteIdOrderByDisplayOrder(@Param("routeId") Long routeId, 
                                                     Pageable pageable);
    
    /**
     * 루트별 이미지 타입별 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.imageType = :imageType AND ri.isActive = true " +
           "ORDER BY ri.displayOrder ASC")
    List<RouteImage> findByRouteIdAndImageType(@Param("routeId") Long routeId, 
                                              @Param("imageType") String imageType);
    
    /**
     * 루트의 대표 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isMain = true AND ri.isActive = true " +
           "ORDER BY ri.displayOrder ASC")
    Optional<RouteImage> findMainImageByRouteId(@Param("routeId") Long routeId);
    
    // ===== 썸네일 일괄 처리 최적화 =====
    
    /**
     * 썸네일 일괄 조회 (여러 루트) - 성능 최적화
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId IN :routeIds " +
           "AND (ri.isMain = true OR ri.displayOrder = 1) " +
           "AND ri.isActive = true " +
           "ORDER BY ri.route.routeId, ri.displayOrder")
    List<RouteImage> findThumbnailsByRouteIds(@Param("routeIds") List<Long> routeIds);
    
    /**
     * 대표 이미지 일괄 조회 (메인 이미지만)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.isMain = true AND ri.isActive = true " +
           "ORDER BY ri.route.routeId")
    List<RouteImage> findAllMainImages();
    
    /**
     * 특정 타입의 첫 번째 이미지들 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.imageType = :imageType AND ri.displayOrder = 1 AND ri.isActive = true " +
           "ORDER BY ri.route.routeId")
    List<RouteImage> findFirstImagesByType(@Param("imageType") String imageType);
    
    /**
     * 루트별 썸네일 URL 조회 (URL만 반환)
     */
    @Query("SELECT ri.route.routeId, ri.imageUrl FROM RouteImage ri " +
           "WHERE ri.route.routeId IN :routeIds " +
           "AND (ri.isMain = true OR ri.displayOrder = 1) " +
           "AND ri.isActive = true")
    List<Object[]> findThumbnailUrlsByRouteIds(@Param("routeIds") List<Long> routeIds);
    
    // ===== 이미지 타입별 조회 =====
    
    /**
     * 이미지 타입별 조회 (전체 루트)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.imageType = :imageType AND ri.isActive = true " +
           "ORDER BY ri.route.routeId, ri.displayOrder")
    List<RouteImage> findByImageType(@Param("imageType") String imageType);
    
    /**
     * 이미지 타입별 통계
     */
    @Query("SELECT ri.imageType, COUNT(ri) as imageCount FROM RouteImage ri " +
           "WHERE ri.isActive = true " +
           "GROUP BY ri.imageType " +
           "ORDER BY imageCount DESC")
    List<Object[]> countByImageType();
    
    /**
     * 루트별 이미지 타입 통계
     */
    @Query("SELECT ri.imageType, COUNT(ri) as imageCount FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isActive = true " +
           "GROUP BY ri.imageType " +
           "ORDER BY imageCount DESC")
    List<Object[]> countByRouteIdAndImageType(@Param("routeId") Long routeId);
    
    /**
     * 타입별 최신 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.imageType = :imageType AND ri.createdAt >= :sinceDate AND ri.isActive = true " +
           "ORDER BY ri.createdAt DESC")
    List<RouteImage> findByImageTypeAndCreatedAtAfter(@Param("imageType") String imageType,
                                                     @Param("sinceDate") LocalDateTime sinceDate);
    
    // ===== 해상도 및 품질별 최적화 =====
    
    /**
     * 모바일 최적화 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId " +
           "AND ri.width <= 800 AND ri.fileSize <= 500000 " +
           "AND ri.isActive = true " +
           "ORDER BY ri.displayOrder")
    List<RouteImage> findOptimizedImagesForMobile(@Param("routeId") Long routeId);
    
    /**
     * 고해상도 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId " +
           "AND ri.width >= 1920 AND ri.height >= 1080 " +
           "AND ri.isActive = true " +
           "ORDER BY ri.displayOrder")
    List<RouteImage> findHighResolutionImages(@Param("routeId") Long routeId);
    
    /**
     * 품질별 이미지 필터링
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId " +
           "AND (:minWidth IS NULL OR ri.width >= :minWidth) " +
           "AND (:maxWidth IS NULL OR ri.width <= :maxWidth) " +
           "AND (:minHeight IS NULL OR ri.height >= :minHeight) " +
           "AND (:maxHeight IS NULL OR ri.height <= :maxHeight) " +
           "AND ri.isActive = true " +
           "ORDER BY ri.displayOrder")
    List<RouteImage> findImagesByQuality(@Param("routeId") Long routeId,
                                        @Param("minWidth") Integer minWidth,
                                        @Param("maxWidth") Integer maxWidth,
                                        @Param("minHeight") Integer minHeight,
                                        @Param("maxHeight") Integer maxHeight);
    
    /**
     * 압축된 이미지 조회 (파일 크기 기준)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId " +
           "AND ri.fileSize <= :maxFileSize " +
           "AND ri.isActive = true " +
           "ORDER BY ri.fileSize ASC")
    List<RouteImage> findCompressedImages(@Param("routeId") Long routeId, 
                                         @Param("maxFileSize") Long maxFileSize);
    
    // ===== 표시 순서 관리 =====
    
    /**
     * 루트별 최대 표시 순서 조회
     */
    @Query("SELECT COALESCE(MAX(ri.displayOrder), 0) FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isActive = true")
    Integer findMaxDisplayOrderByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 특정 순서 이후 이미지들 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.displayOrder > :displayOrder AND ri.isActive = true " +
           "ORDER BY ri.displayOrder")
    List<RouteImage> findByRouteIdAndDisplayOrderGreaterThan(@Param("routeId") Long routeId, 
                                                            @Param("displayOrder") Integer displayOrder);
    
    /**
     * 표시 순서 변경 (드래그앤드롭)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.displayOrder = :newOrder " +
           "WHERE ri.imageId = :imageId")
    int updateDisplayOrder(@Param("imageId") Long imageId, @Param("newOrder") Integer newOrder);
    
    /**
     * 표시 순서 뒤로 밀기 (삽입 시)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.displayOrder = ri.displayOrder + 1 " +
           "WHERE ri.route.routeId = :routeId AND ri.displayOrder >= :fromOrder AND ri.isActive = true")
    int shiftDisplayOrdersUp(@Param("routeId") Long routeId, @Param("fromOrder") Integer fromOrder);
    
    /**
     * 표시 순서 앞으로 당기기 (삭제 시)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.displayOrder = ri.displayOrder - 1 " +
           "WHERE ri.route.routeId = :routeId AND ri.displayOrder > :fromOrder AND ri.isActive = true")
    int shiftDisplayOrdersDown(@Param("routeId") Long routeId, @Param("fromOrder") Integer fromOrder);
    
    // ===== 대표 이미지 관리 =====
    
    /**
     * 기존 대표 이미지 해제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isMain = false " +
           "WHERE ri.route.routeId = :routeId")
    int clearMainImages(@Param("routeId") Long routeId);
    
    /**
     * 대표 이미지 설정
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isMain = true, ri.displayOrder = 1 " +
           "WHERE ri.imageId = :imageId")
    int setAsMainImage(@Param("imageId") Long imageId);
    
    // ===== 파일 크기 및 성능 관리 =====
    
    /**
     * 대용량 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.fileSize > :sizeLimit AND ri.isActive = true " +
           "ORDER BY ri.fileSize DESC")
    List<RouteImage> findLargeImages(@Param("sizeLimit") Long sizeLimit);
    
    /**
     * 파일 크기별 통계
     */
    @Query("SELECT " +
           "CASE " +
           "  WHEN ri.fileSize < 100000 THEN '100KB 미만' " +
           "  WHEN ri.fileSize < 500000 THEN '100KB-500KB' " +
           "  WHEN ri.fileSize < 1000000 THEN '500KB-1MB' " +
           "  WHEN ri.fileSize < 5000000 THEN '1MB-5MB' " +
           "  ELSE '5MB 이상' " +
           "END as sizeRange, " +
           "COUNT(ri) as imageCount " +
           "FROM RouteImage ri " +
           "WHERE ri.isActive = true AND ri.fileSize IS NOT NULL " +
           "GROUP BY " +
           "CASE " +
           "  WHEN ri.fileSize < 100000 THEN '100KB 미만' " +
           "  WHEN ri.fileSize < 500000 THEN '100KB-500KB' " +
           "  WHEN ri.fileSize < 1000000 THEN '500KB-1MB' " +
           "  WHEN ri.fileSize < 5000000 THEN '1MB-5MB' " +
           "  ELSE '5MB 이상' " +
           "END " +
           "ORDER BY imageCount DESC")
    List<Object[]> getFileSizeDistribution();
    
    /**
     * MIME 타입별 통계
     */
    @Query("SELECT ri.mimeType, COUNT(ri) as imageCount FROM RouteImage ri " +
           "WHERE ri.isActive = true AND ri.mimeType IS NOT NULL " +
           "GROUP BY ri.mimeType " +
           "ORDER BY imageCount DESC")
    List<Object[]> countByMimeType();
    
    // ===== 조회수 및 인기도 =====
    
    /**
     * 조회수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.viewCount = COALESCE(ri.viewCount, 0) + 1 " +
           "WHERE ri.imageId = :imageId")
    int increaseViewCount(@Param("imageId") Long imageId);
    
    /**
     * 인기 이미지 조회 (조회수 기준)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.isActive = true " +
           "ORDER BY ri.viewCount DESC")
    List<RouteImage> findPopularImages(Pageable pageable);
    
    /**
     * 루트별 인기 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isActive = true " +
           "ORDER BY ri.viewCount DESC")
    List<RouteImage> findPopularImagesByRoute(@Param("routeId") Long routeId, Pageable pageable);
    
    /**
     * 좋아요 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.likeCount = COALESCE(ri.likeCount, 0) + 1 " +
           "WHERE ri.imageId = :imageId")
    int increaseLikeCount(@Param("imageId") Long imageId);
    
    // ===== 이미지 상태 관리 =====
    
    /**
     * 이미지 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isActive = false, ri.isMain = false " +
           "WHERE ri.imageId = :imageId")
    int deactivateImage(@Param("imageId") Long imageId);
    
    /**
     * 루트의 모든 이미지 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isActive = false, ri.isMain = false " +
           "WHERE ri.route.routeId = :routeId")
    int deactivateAllImagesByRoute(@Param("routeId") Long routeId);
    
    /**
     * 특정 타입 이미지 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isActive = false " +
           "WHERE ri.route.routeId = :routeId AND ri.imageType = :imageType")
    int deactivateImagesByType(@Param("routeId") Long routeId, @Param("imageType") String imageType);
    
    /**
     * 타입별 이미지 일괄 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.imageType = :imageType")
    int deleteByRouteIdAndImageType(@Param("routeId") Long routeId, @Param("imageType") String imageType);
    
    // ===== 업로드 관리 =====
    
    /**
     * 업로더별 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.uploader.userId = :uploaderId AND ri.isActive = true " +
           "ORDER BY ri.createdAt DESC")
    List<RouteImage> findByUploaderId(@Param("uploaderId") Long uploaderId);
    
    /**
     * 업로드 IP별 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.uploadIp = :uploadIp AND ri.isActive = true " +
           "ORDER BY ri.createdAt DESC")
    List<RouteImage> findByUploadIp(@Param("uploadIp") String uploadIp);
    
    /**
     * 업로더별 이미지 통계
     */
    @Query("SELECT ri.uploader.userId, COUNT(ri) as imageCount, SUM(ri.fileSize) as totalSize " +
           "FROM RouteImage ri " +
           "WHERE ri.isActive = true AND ri.uploader IS NOT NULL " +
           "GROUP BY ri.uploader.userId " +
           "ORDER BY imageCount DESC")
    List<Object[]> getUploaderStatistics();
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 복합 조건 이미지 검색
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE (:routeId IS NULL OR ri.route.routeId = :routeId) " +
           "AND (:imageType IS NULL OR ri.imageType = :imageType) " +
           "AND (:isMain IS NULL OR ri.isMain = :isMain) " +
           "AND (:keyword IS NULL OR ri.title LIKE %:keyword% OR ri.description LIKE %:keyword%) " +
           "AND ri.isActive = true " +
           "ORDER BY ri.route.routeId, ri.displayOrder")
    Page<RouteImage> findByComplexConditions(@Param("routeId") Long routeId,
                                           @Param("imageType") String imageType,
                                           @Param("isMain") Boolean isMain,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);
    
    /**
     * 제목으로 이미지 검색
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.title LIKE %:keyword% AND ri.isActive = true " +
           "ORDER BY ri.viewCount DESC")
    List<RouteImage> findByTitleContaining(@Param("keyword") String keyword);
    
    // ===== 관리자용 통계 =====
    
    /**
     * 전체 이미지 통계 요약
     */
    @Query("SELECT " +
           "COUNT(ri) as totalImages, " +
           "COUNT(CASE WHEN ri.isActive = true THEN 1 END) as activeImages, " +
           "COUNT(CASE WHEN ri.isMain = true THEN 1 END) as mainImages, " +
           "AVG(ri.fileSize) as avgFileSize, " +
           "SUM(ri.viewCount) as totalViews " +
           "FROM RouteImage ri")
    List<Object[]> getImageStatisticsSummary();
    
    /**
     * 루트별 이미지 수 통계
     */
    @Query("SELECT ri.route.routeId, COUNT(ri) as imageCount " +
           "FROM RouteImage ri " +
           "WHERE ri.isActive = true " +
           "GROUP BY ri.route.routeId " +
           "ORDER BY imageCount DESC")
    List<Object[]> countImagesByRoute();
    
    /**
     * 최근 업로드된 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.isActive = true " +
           "ORDER BY ri.createdAt DESC")
    List<RouteImage> findRecentlyUploadedImages(Pageable pageable);
}
```

---

## 🎥 2. RouteVideoRepository - 루트 동영상 Repository

```java
package com.routepick.domain.route.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.route.entity.RouteVideo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RouteVideo Repository
 * - 루트 동영상 전문 최적화
 * - 스트리밍 최적화 및 트랜스코딩 관리
 * - 동영상 분석 및 통계
 * - 모바일/데스크톱 최적화
 */
@Repository
public interface RouteVideoRepository extends BaseRepository<RouteVideo, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 루트별 최신 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findByRouteIdOrderByCreatedAtDesc(@Param("routeId") Long routeId);
    
    /**
     * 루트별 동영상 조회 (페이징)
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    Page<RouteVideo> findByRouteIdOrderByCreatedAtDesc(@Param("routeId") Long routeId, 
                                                      Pageable pageable);
    
    /**
     * 루트별 동영상 타입별 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId AND rv.videoType = :videoType AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findByRouteIdAndVideoType(@Param("routeId") Long routeId, 
                                              @Param("videoType") String videoType);
    
    /**
     * 루트의 성공 영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId AND rv.isSuccessVideo = true AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findSuccessVideosByRouteId(@Param("routeId") Long routeId);
    
    // ===== 재생 시간별 필터링 =====
    
    /**
     * 재생 시간별 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.duration BETWEEN :minDuration AND :maxDuration AND rv.isActive = true " +
           "ORDER BY rv.duration")
    List<RouteVideo> findByDurationBetween(@Param("minDuration") Integer minDuration, 
                                          @Param("maxDuration") Integer maxDuration);
    
    /**
     * 짧은 동영상 조회 (30초 이하)
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.duration <= 30 AND rv.isActive = true " +
           "ORDER BY rv.viewCount DESC")
    List<RouteVideo> findShortVideos(Pageable pageable);
    
    /**
     * 긴 동영상 조회 (5분 이상)
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.duration >= 300 AND rv.isActive = true " +
           "ORDER BY rv.viewCount DESC")
    List<RouteVideo> findLongVideos(Pageable pageable);
    
    // ===== 화질별 동영상 관리 =====
    
    /**
     * 화질별 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.videoQuality = :quality AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findByVideoQuality(@Param("quality") String quality);
    
    /**
     * HD 이상 고화질 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.videoQuality IN ('HD', 'FHD', '4K') AND rv.isActive = true " +
           "ORDER BY rv.videoQuality DESC, rv.createdAt DESC")
    List<RouteVideo> findHighQualityVideos();
    
    /**
     * 모바일 최적화 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.videoWidth <= 1280 AND rv.fileSize <= 50000000 " +
           "AND rv.isActive = true " +
           "ORDER BY rv.fileSize ASC")
    List<RouteVideo> findMobileOptimizedVideos(@Param("routeId") Long routeId);
    
    // ===== 썸네일 관리 =====
    
    /**
     * 동영상 목록용 썸네일 조회
     */
    @Query("SELECT rv.videoId, rv.thumbnailUrl FROM RouteVideo rv " +
           "WHERE rv.route.routeId IN :routeIds AND rv.isActive = true " +
           "AND rv.thumbnailUrl IS NOT NULL")
    List<Object[]> findThumbnailsForVideoList(@Param("routeIds") List<Long> routeIds);
    
    /**
     * 썸네일이 없는 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.thumbnailUrl IS NULL AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findVideosWithoutThumbnail();
    
    // ===== 스트리밍 최적화 =====
    
    /**
     * 스트리밍 최적화 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.videoFormat IN ('MP4', 'WEBM') " +
           "AND rv.frameRate >= 24 AND rv.isActive = true " +
           "ORDER BY rv.videoQuality DESC")
    List<RouteVideo> findStreamingOptimizedVideos(@Param("routeId") Long routeId);
    
    /**
     * 비트레이트별 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId " +
           "AND (:minBitrate IS NULL OR rv.fileSize * 8 / rv.duration >= :minBitrate) " +
           "AND (:maxBitrate IS NULL OR rv.fileSize * 8 / rv.duration <= :maxBitrate) " +
           "AND rv.isActive = true " +
           "ORDER BY rv.fileSize ASC")
    List<RouteVideo> findVideosByBitrate(@Param("routeId") Long routeId,
                                        @Param("minBitrate") Long minBitrate,
                                        @Param("maxBitrate") Long maxBitrate);
    
    /**
     * 적응형 스트리밍용 다중 화질 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId " +
           "AND rv.videoFormat = 'MP4' AND rv.isActive = true " +
           "ORDER BY rv.videoWidth DESC")
    List<RouteVideo> findAdaptiveStreamingVideos(@Param("routeId") Long routeId);
    
    // ===== 동영상 분석 및 통계 =====
    
    /**
     * 동영상 분석 데이터 조회
     */
    @Query("SELECT rv.videoId, rv.viewCount, rv.averageWatchTime, rv.completionRate, rv.likeCount " +
           "FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId AND rv.isActive = true " +
           "ORDER BY rv.viewCount DESC")
    List<Object[]> findVideoAnalytics(@Param("routeId") Long routeId);
    
    /**
     * 인기 동영상 조회 (조회수 기준)
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.isActive = true " +
           "ORDER BY rv.viewCount DESC")
    List<RouteVideo> findPopularVideos(Pageable pageable);
    
    /**
     * 완주율이 높은 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.completionRate >= :minCompletionRate AND rv.viewCount >= :minViews " +
           "AND rv.isActive = true " +
           "ORDER BY rv.completionRate DESC")
    List<RouteVideo> findHighCompletionVideos(@Param("minCompletionRate") Float minCompletionRate,
                                             @Param("minViews") Long minViews);
    
    /**
     * 동영상 타입별 통계
     */
    @Query("SELECT rv.videoType, COUNT(rv) as videoCount, AVG(rv.duration) as avgDuration " +
           "FROM RouteVideo rv " +
           "WHERE rv.isActive = true " +
           "GROUP BY rv.videoType " +
           "ORDER BY videoCount DESC")
    List<Object[]> countByVideoType();
    
    /**
     * 루트별 동영상 타입 통계
     */
    @Query("SELECT rv.videoType, COUNT(rv) as videoCount FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId AND rv.isActive = true " +
           "GROUP BY rv.videoType " +
           "ORDER BY videoCount DESC")
    List<Object[]> countByRouteIdAndVideoType(@Param("routeId") Long routeId);
    
    // ===== 업로드 상태 관리 =====
    
    /**
     * 업로드 상태별 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findVideosByUploadStatus(@Param("routeId") Long routeId);
    
    /**
     * 트랜스코딩 대기 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.videoFormat IS NULL OR rv.videoQuality IS NULL " +
           "AND rv.isActive = true " +
           "ORDER BY rv.createdAt")
    List<RouteVideo> findVideosAwaitingTranscoding();
    
    /**
     * 처리 완료된 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.videoFormat IS NOT NULL AND rv.videoQuality IS NOT NULL " +
           "AND rv.thumbnailUrl IS NOT NULL AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findProcessedVideos();
    
    // ===== 업데이트 메서드 =====
    
    /**
     * 조회수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.viewCount = COALESCE(rv.viewCount, 0) + 1 " +
           "WHERE rv.videoId = :videoId")
    int increaseViewCount(@Param("videoId") Long videoId);
    
    /**
     * 재생 기록 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET " +
           "rv.totalPlayTime = COALESCE(rv.totalPlayTime, 0) + :watchedSeconds, " +
           "rv.averageWatchTime = CASE WHEN COALESCE(rv.viewCount, 0) > 0 " +
           "  THEN (COALESCE(rv.totalPlayTime, 0) + :watchedSeconds) / rv.viewCount " +
           "  ELSE :watchedSeconds END, " +
           "rv.completionRate = CASE WHEN rv.duration > 0 " +
           "  THEN ((COALESCE(rv.totalPlayTime, 0) + :watchedSeconds) / rv.viewCount / rv.duration) * 100 " +
           "  ELSE 0.0 END " +
           "WHERE rv.videoId = :videoId")
    int recordPlayTime(@Param("videoId") Long videoId, @Param("watchedSeconds") Integer watchedSeconds);
    
    /**
     * 좋아요 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.likeCount = COALESCE(rv.likeCount, 0) + 1 " +
           "WHERE rv.videoId = :videoId")
    int increaseLikeCount(@Param("videoId") Long videoId);
    
    /**
     * 공유 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.shareCount = COALESCE(rv.shareCount, 0) + 1 " +
           "WHERE rv.videoId = :videoId")
    int increaseShareCount(@Param("videoId") Long videoId);
    
    /**
     * 동영상 메타데이터 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET " +
           "rv.videoFormat = :format, " +
           "rv.videoQuality = :quality, " +
           "rv.videoWidth = :width, " +
           "rv.videoHeight = :height, " +
           "rv.frameRate = :frameRate, " +
           "rv.thumbnailUrl = :thumbnailUrl " +
           "WHERE rv.videoId = :videoId")
    int updateVideoMetadata(@Param("videoId") Long videoId,
                           @Param("format") String format,
                           @Param("quality") String quality,
                           @Param("width") Integer width,
                           @Param("height") Integer height,
                           @Param("frameRate") Float frameRate,
                           @Param("thumbnailUrl") String thumbnailUrl);
    
    // ===== 동영상 상태 관리 =====
    
    /**
     * 동영상 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.isActive = false, rv.isFeatured = false " +
           "WHERE rv.videoId = :videoId")
    int deactivateVideo(@Param("videoId") Long videoId);
    
    /**
     * 루트의 모든 동영상 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.isActive = false, rv.isFeatured = false " +
           "WHERE rv.route.routeId = :routeId")
    int deactivateAllVideosByRoute(@Param("routeId") Long routeId);
    
    /**
     * 특집 동영상 설정
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.isFeatured = :featured " +
           "WHERE rv.videoId = :videoId")
    int setFeaturedStatus(@Param("videoId") Long videoId, @Param("featured") boolean featured);
    
    // ===== 업로더 관리 =====
    
    /**
     * 업로더별 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.uploader.userId = :uploaderId AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findByUploaderId(@Param("uploaderId") Long uploaderId);
    
    /**
     * 업로더별 동영상 통계
     */
    @Query("SELECT rv.uploader.userId, COUNT(rv) as videoCount, " +
           "SUM(rv.fileSize) as totalSize, AVG(rv.duration) as avgDuration " +
           "FROM RouteVideo rv " +
           "WHERE rv.isActive = true AND rv.uploader IS NOT NULL " +
           "GROUP BY rv.uploader.userId " +
           "ORDER BY videoCount DESC")
    List<Object[]> getUploaderStatistics();
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 복합 조건 동영상 검색
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE (:routeId IS NULL OR rv.route.routeId = :routeId) " +
           "AND (:videoType IS NULL OR rv.videoType = :videoType) " +
           "AND (:quality IS NULL OR rv.videoQuality = :quality) " +
           "AND (:minDuration IS NULL OR rv.duration >= :minDuration) " +
           "AND (:maxDuration IS NULL OR rv.duration <= :maxDuration) " +
           "AND (:isFeatured IS NULL OR rv.isFeatured = :isFeatured) " +
           "AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    Page<RouteVideo> findByComplexConditions(@Param("routeId") Long routeId,
                                           @Param("videoType") String videoType,
                                           @Param("quality") String quality,
                                           @Param("minDuration") Integer minDuration,
                                           @Param("maxDuration") Integer maxDuration,
                                           @Param("isFeatured") Boolean isFeatured,
                                           Pageable pageable);
    
    /**
     * 제목으로 동영상 검색
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.title LIKE %:keyword% AND rv.isActive = true " +
           "ORDER BY rv.viewCount DESC")
    List<RouteVideo> findByTitleContaining(@Param("keyword") String keyword);
    
    // ===== 관리자용 통계 =====
    
    /**
     * 전체 동영상 통계 요약
     */
    @Query("SELECT " +
           "COUNT(rv) as totalVideos, " +
           "COUNT(CASE WHEN rv.isActive = true THEN 1 END) as activeVideos, " +
           "COUNT(CASE WHEN rv.isFeatured = true THEN 1 END) as featuredVideos, " +
           "AVG(rv.fileSize) as avgFileSize, " +
           "AVG(rv.duration) as avgDuration, " +
           "SUM(rv.viewCount) as totalViews " +
           "FROM RouteVideo rv")
    List<Object[]> getVideoStatisticsSummary();
    
    /**
     * 루트별 동영상 수 통계
     */
    @Query("SELECT rv.route.routeId, COUNT(rv) as videoCount " +
           "FROM RouteVideo rv " +
           "WHERE rv.isActive = true " +
           "GROUP BY rv.route.routeId " +
           "ORDER BY videoCount DESC")
    List<Object[]> countVideosByRoute();
    
    /**
     * 최근 업로드된 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findRecentlyUploadedVideos(Pageable pageable);
    
    /**
     * 특집 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.isFeatured = true AND rv.isActive = true " +
           "ORDER BY rv.viewCount DESC")
    List<RouteVideo> findFeaturedVideos(Pageable pageable);
}
```

---

## ⚡ 3. 미디어 파일 관리 최적화

### CDN 통합 및 URL 생성
```java
/**
 * CDN 최적화 URL 생성 서비스
 */
@Service
public class MediaUrlService {
    
    /**
     * 이미지 CDN URL 생성
     */
    public String generateImageUrl(String originalUrl, String size, String format) {
        // CloudFront, ImageKit 등 CDN 변환 로직
        return originalUrl
            .replace(".jpg", "_" + size + "." + format)
            .replace("storage.com", "cdn.routepick.com");
    }
    
    /**
     * 동영상 스트리밍 URL 생성
     */
    public String generateStreamingUrl(String videoId, String quality) {
        return "https://stream.routepick.com/" + videoId + "/" + quality + ".m3u8";
    }
}
```

### 이미지 리사이징 최적화
```sql
-- 이미지 크기별 최적화 인덱스
CREATE INDEX idx_route_image_size_optimization 
ON route_images(route_id, width, height, file_size);

-- 동영상 품질별 인덱스
CREATE INDEX idx_route_video_quality_optimization 
ON route_videos(route_id, video_quality, video_width, video_height);

-- 미디어 타입별 성능 인덱스
CREATE INDEX idx_route_image_type_display 
ON route_images(route_id, image_type, display_order, is_active);

-- 동영상 스트리밍 최적화 인덱스
CREATE INDEX idx_route_video_streaming 
ON route_videos(route_id, video_format, frame_rate, is_active);
```

### Custom Repository 인터페이스
```java
// RouteImageRepositoryCustom - 이미지 배치 처리 및 최적화
public interface RouteImageRepositoryCustom {
    List<RouteImage> findOptimizedImagesForDevice(Long routeId, String deviceType);
    void batchUpdateDisplayOrders(List<ImageOrderDto> orders);
    List<RouteImage> findImagesForBatchProcessing(ImageProcessingCriteria criteria);
    Map<String, Object> getImageAnalytics(Long routeId);
}

// RouteVideoRepositoryCustom - 동영상 스트리밍 및 분석
public interface RouteVideoRepositoryCustom {
    List<RouteVideo> findAdaptiveStreamingQualities(Long routeId);
    VideoAnalyticsDto getDetailedVideoAnalytics(Long videoId);
    List<RouteVideo> findVideosForTranscoding();
    void batchUpdateVideoMetadata(List<VideoMetadataDto> metadata);
}
```

---

## ✅ 설계 완료 체크리스트

### 루트 미디어 Repository (2개)
- [x] **RouteImageRepository** - 이미지 최적화, 썸네일 일괄 처리, 해상도별 관리
- [x] **RouteVideoRepository** - 동영상 스트리밍, 트랜스코딩, 분석 통계

### 미디어 최적화 기능
- [x] 썸네일 일괄 조회 (성능 최적화)
- [x] 해상도별 자동 선택 (모바일/데스크톱)
- [x] 이미지 드래그앤드롭 순서 변경
- [x] 동영상 적응형 스트리밍 지원

### 고성능 미디어 처리
- [x] 파일 크기별 압축 이미지 조회
- [x] 화질별 동영상 필터링 (HD, FHD, 4K)
- [x] 비트레이트 기반 최적화
- [x] 스트리밍 완주율 분석

### CDN 및 성능 최적화
- [x] CDN URL 생성 지원
- [x] 복합 인덱스 (route_id + type + display_order)
- [x] 배치 처리 메서드
- [x] 캐시 무효화 지원

### Custom Repository 설계
- [x] RouteImageRepositoryCustom 인터페이스
- [x] RouteVideoRepositoryCustom 인터페이스
- [x] 배치 처리 및 분석 전용 메서드
- [x] 디바이스별 최적화 로직

---

**다음 단계**: Step 5-3d Route 상호작용 Repository (댓글, 투표, 스크랩)  
**완료일**: 2025-08-20  
**핵심 성과**: 미디어 2개 Repository + CDN 최적화 + 스트리밍 지원 완료