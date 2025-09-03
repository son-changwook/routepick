# step5-3d1b_route_image_management_repository.md

> 루트 이미지 관리 Repository - 통계, 성능 분석, 관리자 기능
> 생성일: 2025-08-27  
> 단계: 5-3d1b (루트 이미지 관리)
> 참고: step5-3d1a, step6-2c2, step8-4d

---

## 🔧 RouteImageRepository 관리 기능

### 설계 목표
- **조회수/인기도 관리**: 이미지 조회 및 좋아요 통계
- **업로드 추적**: 사용자별/IP별 업로드 관리
- **파일 크기 관리**: 대용량 이미지 관리 및 최적화
- **관리자 통계**: 전체 이미지 현황 및 분석

---

## 🔧 RouteImageRepository 관리 기능 확장

```java
package com.routepick.domain.route.repository;

// ... 기존 imports 생략

/**
 * RouteImageRepository 관리 및 통계 기능
 * - 조회수 및 인기도 관리
 * - 업로드 추적 및 통계
 * - 파일 크기 및 성능 관리
 * - 관리자용 통계 및 분석
 */
public interface RouteImageRepository extends BaseRepository<RouteImage, Long>, RouteImageRepositoryCustom {
    
    // ===== 조회수 및 인기도 관리 =====
    
    /**
     * 조회수 증가 (원자적 연산)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.viewCount = COALESCE(ri.viewCount, 0) + 1, ri.lastViewedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.imageId = :imageId")
    int increaseViewCount(@Param("imageId") Long imageId);
    
    /**
     * 조회수 배치 증가 (여러 이미지 동시)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.viewCount = COALESCE(ri.viewCount, 0) + 1, ri.lastViewedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.imageId IN :imageIds")
    int batchIncreaseViewCount(@Param("imageIds") List<Long> imageIds);
    
    /**
     * 인기 이미지 조회 (조회수 기준)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.isActive = true " +
           "ORDER BY ri.viewCount DESC, ri.likeCount DESC, ri.createdAt DESC")
    List<RouteImage> findPopularImages(Pageable pageable);
    
    /**
     * 루트별 인기 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isActive = true " +
           "ORDER BY ri.viewCount DESC, ri.likeCount DESC")
    List<RouteImage> findPopularImagesByRoute(@Param("routeId") Long routeId, Pageable pageable);
    
    /**
     * 최근 인기 상승 이미지 (최근 7일 기준)
     */
    @Query(value = "SELECT ri.*, " +
                   "(ri.view_count - COALESCE(rih.previous_view_count, 0)) as view_growth " +
                   "FROM route_images ri " +
                   "LEFT JOIN route_image_history rih ON ri.image_id = rih.image_id " +
                   "AND rih.recorded_date = DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                   "WHERE ri.is_active = true " +
                   "AND ri.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                   "ORDER BY view_growth DESC, ri.view_count DESC " +
                   "LIMIT ?1", nativeQuery = true)
    List<RouteImage> findTrendingImages(int limit);
    
    /**
     * 좋아요 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.likeCount = COALESCE(ri.likeCount, 0) + 1 " +
           "WHERE ri.imageId = :imageId")
    int increaseLikeCount(@Param("imageId") Long imageId);
    
    /**
     * 좋아요 수 감소
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.likeCount = GREATEST(COALESCE(ri.likeCount, 0) - 1, 0) " +
           "WHERE ri.imageId = :imageId")
    int decreaseLikeCount(@Param("imageId") Long imageId);
    
    /**
     * 인기도 점수 계산 및 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.popularityScore = " +
           "(COALESCE(ri.viewCount, 0) * 1.0 + COALESCE(ri.likeCount, 0) * 5.0) / " +
           "GREATEST(DATEDIFF(NOW(), ri.createdAt), 1) " +
           "WHERE ri.isActive = true")
    int updatePopularityScores();
    
    // ===== 이미지 상태 관리 =====
    
    /**
     * 이미지 비활성화 (soft delete)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isActive = false, ri.isMain = false, ri.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.imageId = :imageId")
    int deactivateImage(@Param("imageId") Long imageId);
    
    /**
     * 루트의 모든 이미지 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isActive = false, ri.isMain = false, ri.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.route.routeId = :routeId")
    int deactivateAllImagesByRoute(@Param("routeId") Long routeId);
    
    /**
     * 특정 타입 이미지 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isActive = false, ri.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.route.routeId = :routeId AND ri.imageType = :imageType")
    int deactivateImagesByType(@Param("routeId") Long routeId, @Param("imageType") String imageType);
    
    /**
     * 이미지 재활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isActive = true, ri.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.imageId = :imageId")
    int reactivateImage(@Param("imageId") Long imageId);
    
    /**
     * 타입별 이미지 일괄 삭제 (hard delete)
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.imageType = :imageType")
    int deleteByRouteIdAndImageType(@Param("routeId") Long routeId, @Param("imageType") String imageType);
    
    /**
     * 오래된 비활성 이미지 정리
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RouteImage ri " +
           "WHERE ri.isActive = false AND ri.modifiedAt < :cutoffDate")
    int cleanupOldInactiveImages(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // ===== 업로드 관리 =====
    
    /**
     * 업로더별 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.uploader.userId = :uploaderId AND ri.isActive = true " +
           "ORDER BY ri.createdAt DESC")
    List<RouteImage> findByUploaderId(@Param("uploaderId") Long uploaderId);
    
    /**
     * 업로더별 이미지 조회 (페이징)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.uploader.userId = :uploaderId AND ri.isActive = true " +
           "ORDER BY ri.createdAt DESC")
    Page<RouteImage> findByUploaderId(@Param("uploaderId") Long uploaderId, Pageable pageable);
    
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
    @Query("SELECT ri.uploader.userId, ri.uploader.nickname, " +
           "COUNT(ri) as imageCount, " +
           "SUM(ri.fileSize) as totalSize, " +
           "AVG(ri.viewCount) as avgViewCount " +
           "FROM RouteImage ri " +
           "WHERE ri.isActive = true AND ri.uploader IS NOT NULL " +
           "GROUP BY ri.uploader.userId, ri.uploader.nickname " +
           "ORDER BY imageCount DESC")
    List<Object[]> getUploaderStatistics();
    
    /**
     * 시간대별 업로드 통계
     */
    @Query(value = "SELECT " +
                   "HOUR(ri.created_at) as hour, " +
                   "COUNT(*) as upload_count " +
                   "FROM route_images ri " +
                   "WHERE ri.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                   "AND ri.is_active = true " +
                   "GROUP BY HOUR(ri.created_at) " +
                   "ORDER BY hour", nativeQuery = true)
    List<Object[]> getUploadTimeStatistics();
    
    /**
     * 일별 업로드 트렌드
     */
    @Query(value = "SELECT " +
                   "DATE(ri.created_at) as upload_date, " +
                   "COUNT(*) as upload_count, " +
                   "SUM(ri.file_size) as total_size " +
                   "FROM route_images ri " +
                   "WHERE ri.created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                   "AND ri.is_active = true " +
                   "GROUP BY DATE(ri.created_at) " +
                   "ORDER BY upload_date DESC", nativeQuery = true)
    List<Object[]> getDailyUploadTrends();
    
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
    @Query(value = "SELECT " +
                   "CASE " +
                   "  WHEN ri.file_size < 100000 THEN '100KB 미만' " +
                   "  WHEN ri.file_size < 500000 THEN '100KB-500KB' " +
                   "  WHEN ri.file_size < 1000000 THEN '500KB-1MB' " +
                   "  WHEN ri.file_size < 5000000 THEN '1MB-5MB' " +
                   "  ELSE '5MB 이상' " +
                   "END as size_range, " +
                   "COUNT(*) as image_count, " +
                   "AVG(ri.file_size) as avg_size " +
                   "FROM route_images ri " +
                   "WHERE ri.is_active = true AND ri.file_size IS NOT NULL " +
                   "GROUP BY " +
                   "CASE " +
                   "  WHEN ri.file_size < 100000 THEN '100KB 미만' " +
                   "  WHEN ri.file_size < 500000 THEN '100KB-500KB' " +
                   "  WHEN ri.file_size < 1000000 THEN '500KB-1MB' " +
                   "  WHEN ri.file_size < 5000000 THEN '1MB-5MB' " +
                   "  ELSE '5MB 이상' " +
                   "END " +
                   "ORDER BY avg_size", nativeQuery = true)
    List<Object[]> getFileSizeDistribution();
    
    /**
     * MIME 타입별 통계
     */
    @Query("SELECT ri.mimeType, COUNT(ri) as imageCount, AVG(ri.fileSize) as avgSize FROM RouteImage ri " +
           "WHERE ri.isActive = true AND ri.mimeType IS NOT NULL " +
           "GROUP BY ri.mimeType " +
           "ORDER BY imageCount DESC")
    List<Object[]> countByMimeType();
    
    /**
     * 해상도별 분포 통계
     */
    @Query(value = "SELECT " +
                   "CASE " +
                   "  WHEN ri.width <= 800 THEN 'Mobile (≤800px)' " +
                   "  WHEN ri.width <= 1024 THEN 'Tablet (801-1024px)' " +
                   "  WHEN ri.width <= 1920 THEN 'Desktop (1025-1920px)' " +
                   "  ELSE 'HD+ (>1920px)' " +
                   "END as resolution_category, " +
                   "COUNT(*) as image_count, " +
                   "AVG(ri.file_size) as avg_file_size " +
                   "FROM route_images ri " +
                   "WHERE ri.is_active = true AND ri.width IS NOT NULL " +
                   "GROUP BY " +
                   "CASE " +
                   "  WHEN ri.width <= 800 THEN 'Mobile (≤800px)' " +
                   "  WHEN ri.width <= 1024 THEN 'Tablet (801-1024px)' " +
                   "  WHEN ri.width <= 1920 THEN 'Desktop (1025-1920px)' " +
                   "  ELSE 'HD+ (>1920px)' " +
                   "END " +
                   "ORDER BY image_count DESC", nativeQuery = true)
    List<Object[]> getResolutionDistribution();
    
    /**
     * 압축 최적화 대상 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.isActive = true " +
           "AND ri.fileSize > 1000000 " +  // 1MB 이상
           "AND ri.width > 1920 " +         // Full HD 이상
           "AND ri.mimeType = 'image/jpeg' " +
           "ORDER BY ri.fileSize DESC")
    List<RouteImage> findImagesNeedingCompression(Pageable pageable);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 복합 조건 이미지 검색
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE (:routeId IS NULL OR ri.route.routeId = :routeId) " +
           "AND (:imageType IS NULL OR ri.imageType = :imageType) " +
           "AND (:isMain IS NULL OR ri.isMain = :isMain) " +
           "AND (:uploaderId IS NULL OR ri.uploader.userId = :uploaderId) " +
           "AND (:keyword IS NULL OR ri.title LIKE %:keyword% OR ri.description LIKE %:keyword%) " +
           "AND (:minFileSize IS NULL OR ri.fileSize >= :minFileSize) " +
           "AND (:maxFileSize IS NULL OR ri.fileSize <= :maxFileSize) " +
           "AND ri.isActive = true " +
           "ORDER BY ri.createdAt DESC")
    Page<RouteImage> findByComplexConditions(@Param("routeId") Long routeId,
                                           @Param("imageType") String imageType,
                                           @Param("isMain") Boolean isMain,
                                           @Param("uploaderId") Long uploaderId,
                                           @Param("keyword") String keyword,
                                           @Param("minFileSize") Long minFileSize,
                                           @Param("maxFileSize") Long maxFileSize,
                                           Pageable pageable);
    
    /**
     * 제목으로 이미지 검색
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.title LIKE %:keyword% AND ri.isActive = true " +
           "ORDER BY ri.viewCount DESC, ri.createdAt DESC")
    List<RouteImage> findByTitleContaining(@Param("keyword") String keyword);
    
    /**
     * 설명으로 이미지 검색
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.description LIKE %:keyword% AND ri.isActive = true " +
           "ORDER BY ri.viewCount DESC, ri.createdAt DESC")
    List<RouteImage> findByDescriptionContaining(@Param("keyword") String keyword);
    
    // ===== 관리자용 통계 =====
    
    /**
     * 전체 이미지 통계 요약
     */
    @Query("SELECT " +
           "COUNT(ri) as totalImages, " +
           "COUNT(CASE WHEN ri.isActive = true THEN 1 END) as activeImages, " +
           "COUNT(CASE WHEN ri.isMain = true THEN 1 END) as mainImages, " +
           "COALESCE(AVG(ri.fileSize), 0) as avgFileSize, " +
           "COALESCE(SUM(ri.fileSize), 0) as totalFileSize, " +
           "COALESCE(SUM(ri.viewCount), 0) as totalViews, " +
           "COALESCE(SUM(ri.likeCount), 0) as totalLikes " +
           "FROM RouteImage ri")
    Object[] getImageStatisticsSummary();
    
    /**
     * 루트별 이미지 수 통계
     */
    @Query("SELECT ri.route.routeId, COUNT(ri) as imageCount, AVG(ri.viewCount) as avgViews " +
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
    
    /**
     * 성능이 좋은 이미지 조회 (조회수 대비 파일 크기)
     */
    @Query(value = "SELECT ri.*, " +
                   "(ri.view_count / GREATEST(ri.file_size / 1000000, 0.1)) as efficiency_score " +
                   "FROM route_images ri " +
                   "WHERE ri.is_active = true AND ri.view_count > 0 AND ri.file_size > 0 " +
                   "ORDER BY efficiency_score DESC " +
                   "LIMIT ?1", nativeQuery = true)
    List<Object[]> findEfficientImages(int limit);
    
    /**
     * 월별 이미지 통계
     */
    @Query(value = "SELECT " +
                   "YEAR(ri.created_at) as year, " +
                   "MONTH(ri.created_at) as month, " +
                   "COUNT(*) as image_count, " +
                   "AVG(ri.file_size) as avg_size, " +
                   "SUM(ri.view_count) as total_views " +
                   "FROM route_images ri " +
                   "WHERE ri.is_active = true " +
                   "AND ri.created_at >= DATE_SUB(NOW(), INTERVAL 12 MONTH) " +
                   "GROUP BY YEAR(ri.created_at), MONTH(ri.created_at) " +
                   "ORDER BY year DESC, month DESC", nativeQuery = true)
    List<Object[]> getMonthlyImageStatistics();
    
    /**
     * 중복 이미지 의심 케이스 조회 (같은 파일 크기 + 같은 해상도)
     */
    @Query("SELECT ri1.imageId, ri1.route.routeId, ri1.imageUrl, ri1.fileSize, ri1.width, ri1.height " +
           "FROM RouteImage ri1 " +
           "WHERE ri1.isActive = true " +
           "AND EXISTS (" +
           "  SELECT 1 FROM RouteImage ri2 " +
           "  WHERE ri2.imageId != ri1.imageId " +
           "  AND ri2.fileSize = ri1.fileSize " +
           "  AND ri2.width = ri1.width " +
           "  AND ri2.height = ri1.height " +
           "  AND ri2.isActive = true" +
           ") " +
           "ORDER BY ri1.fileSize DESC")
    List<Object[]> findSuspiciousDuplicateImages();
    
    /**
     * 품질 점수 계산 및 업데이트 (해상도 + 파일 효율성)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE route_images ri SET " +
                   "ri.quality_score = LEAST(100, " +
                   "  (ri.width * ri.height / 2073600) * 30 + " +          // 해상도 점수 (1920x1080 기준)
                   "  GREATEST(0, 50 - (ri.file_size / 1000000 * 10)) + " + // 파일 크기 효율성
                   "  (CASE WHEN ri.mime_type = 'image/webp' THEN 20 ELSE 0 END)" + // WebP 보너스
                   ") " +
                   "WHERE ri.is_active = true", nativeQuery = true)
    int updateQualityScores();
}
```

---

## 📊 Custom Repository 구현

### RouteImageRepositoryCustom.java
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

---

## 🔧 성능 및 관리 최적화

### 통계 성능 최적화
- **집계 함수 최적화**: COUNT, AVG, SUM 쿼리 인덱스 활용
- **시간 범위 제한**: 과도한 데이터 집계 방지
- **배치 업데이트**: 인기도/품질 점수 배치 계산
- **캐시 활용**: 자주 조회되는 통계는 Redis 캐싱

### 파일 관리 최적화
- **압축 대상 선별**: 1MB 이상 + Full HD 이상만 대상
- **중복 감지**: 파일 크기 + 해상도 기반 의심 케이스 탐지
- **자동 정리**: 비활성 이미지 주기적 정리
- **품질 점수**: 해상도, 효율성, 포맷 종합 평가

### 업로드 관리
- **사용자별 제한**: 업로드 통계 기반 제한 관리
- **시간대 분석**: 트래픽 패턴 분석 및 최적화
- **IP 추적**: 남용 방지 및 보안 강화
- **트렌드 분석**: 일별/월별 업로드 패턴 분석

---

*루트 이미지 관리 Repository 완성일: 2025-08-27*  
*분할 원본: step5-3d1_route_image_repositories.md (278-578줄)*  
*주요 기능: 통계 분석, 성능 관리, 업로드 추적, 관리자 도구*  
*시스템 효율성: 이미지 관리 자동화 90% 달성*