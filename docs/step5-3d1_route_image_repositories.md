# Step 5-3d1: 루트 이미지 Repository 생성

## 개요
- **목적**: 루트 이미지 전문 Repository 생성
- **대상**: RouteImageRepository
- **최적화**: 썸네일 처리, CDN 연동, 해상도별 최적화

## 🖼️ RouteImageRepository - 루트 이미지 Repository

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

## Custom Repository Interface

```java
package com.routepick.domain.route.repository.custom;

import com.routepick.domain.route.entity.RouteImage;
import com.routepick.application.dto.route.ImageOrderDto;
import com.routepick.application.dto.route.ImageProcessingCriteria;

import java.util.List;
import java.util.Map;

/**
 * 루트 이미지 커스텀 Repository
 */
public interface RouteImageRepositoryCustom {
    
    /**
     * 디바이스별 최적화 이미지 조회
     */
    List<RouteImage> findOptimizedImagesForDevice(Long routeId, String deviceType);
    
    /**
     * 배치 순서 업데이트
     */
    void batchUpdateDisplayOrders(List<ImageOrderDto> orders);
    
    /**
     * 배치 처리용 이미지 조회
     */
    List<RouteImage> findImagesForBatchProcessing(ImageProcessingCriteria criteria);
    
    /**
     * 이미지 분석 데이터
     */
    Map<String, Object> getImageAnalytics(Long routeId);
    
    /**
     * CDN 최적화 대상 이미지 조회
     */
    List<RouteImage> findImagesForCdnOptimization(int limit);
    
    /**
     * 썸네일 재생성 대상 이미지 조회
     */
    List<RouteImage> findImagesNeedingThumbnailRegeneration();
}
```

## 📈 성능 최적화 포인트

### 1. 인덱스 최적화
```sql
-- 이미지 크기별 최적화 인덱스
CREATE INDEX idx_route_image_size_optimization 
ON route_images(route_id, width, height, file_size);

-- 미디어 타입별 성능 인덱스
CREATE INDEX idx_route_image_type_display 
ON route_images(route_id, image_type, display_order, is_active);

-- 썸네일 조회 최적화
CREATE INDEX idx_route_image_thumbnail 
ON route_images(route_id, is_main, display_order);

-- 조회수 및 인기도 인덱스
CREATE INDEX idx_route_image_popularity 
ON route_images(view_count DESC, like_count DESC);
```

### 2. CDN 통합 및 URL 생성
```java
/**
 * CDN 최적화 URL 생성 서비스
 */
@Service
public class ImageUrlService {
    
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
     * 디바이스별 최적화 URL
     */
    public String generateOptimizedUrl(String originalUrl, String deviceType) {
        String size = "mobile".equals(deviceType) ? "800x600" : "1920x1080";
        String format = "webp"; // WebP 포맷 우선 사용
        return generateImageUrl(originalUrl, size, format);
    }
}
```

### 3. 이미지 리사이징 최적화
- **WebP 포맷**: 압축률 향상 (30-35% 감소)
- **Progressive JPEG**: 점진적 로딩
- **Responsive Images**: srcset 속성 활용
- **Lazy Loading**: 지연 로딩으로 성능 향상

## 🎯 주요 기능
- ✅ **썸네일 일괄 처리**: 여러 루트 썸네일 동시 조회
- ✅ **해상도별 최적화**: 모바일/데스크톱 자동 선택
- ✅ **표시 순서 관리**: 드래그앤드롭 순서 변경
- ✅ **대표 이미지 관리**: 메인 이미지 설정/해제
- ✅ **파일 크기 최적화**: 압축 이미지 필터링
- ✅ **타입별 분류**: 이미지 타입별 통계 및 관리
- ✅ **업로더 추적**: 업로드 사용자 및 IP 관리
- ✅ **인기도 분석**: 조회수, 좋아요 기반 랭킹

## 💡 비즈니스 로직 활용
- **CDN 연동**: 이미지 URL 자동 변환
- **모바일 최적화**: 디바이스별 이미지 제공
- **배치 처리**: 대용량 이미지 일괄 처리
- **통계 분석**: 이미지 사용 패턴 분석

---
*Step 5-3d1 완료: 루트 이미지 Repository 생성 완료*  
*다음: step5-3d2 루트 동영상 Repository 대기 중*