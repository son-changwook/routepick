# step5-3d1a_route_image_core_repository.md

> 루트 이미지 핵심 Repository - 기본 조회, 썸네일 처리, 해상도 최적화
> 생성일: 2025-08-27  
> 단계: 5-3d1a (루트 이미지 핵심)
> 참고: step5-3d1, step4-3b2, step6-2c

---

## 🖼️ RouteImageRepository 핵심 기능

### 설계 목표
- **썸네일 최적화**: 대표 이미지 및 썸네일 고속 조회
- **해상도별 처리**: 모바일/데스크톱 디바이스 자동 최적화
- **표시 순서 관리**: 드래그앤드롭 순서 변경 지원
- **성능 최적화**: 배치 조회 및 인덱스 최적화

---

## 🖼️ RouteImageRepository.java - 핵심 Repository

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
public interface RouteImageRepository extends BaseRepository<RouteImage, Long>, RouteImageRepositoryCustom {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 루트별 이미지 정렬 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isActive = true " +
           "ORDER BY ri.displayOrder ASC, ri.createdAt DESC")
    List<RouteImage> findByRouteIdOrderByDisplayOrder(@Param("routeId") Long routeId);
    
    /**
     * 루트별 이미지 정렬 조회 (페이징)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isActive = true " +
           "ORDER BY ri.displayOrder ASC, ri.createdAt DESC")
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
     * 루트의 대표 이미지 조회 (첫 번째 메인 이미지)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isMain = true AND ri.isActive = true " +
           "ORDER BY ri.displayOrder ASC " +
           "LIMIT 1")
    Optional<RouteImage> findMainImageByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 루트의 모든 대표 이미지 조회 (복수 가능)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isMain = true AND ri.isActive = true " +
           "ORDER BY ri.displayOrder ASC")
    List<RouteImage> findAllMainImagesByRouteId(@Param("routeId") Long routeId);
    
    /**
     * 루트별 활성 이미지 수 조회
     */
    @Query("SELECT COUNT(ri) FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isActive = true")
    Long countActiveImagesByRouteId(@Param("routeId") Long routeId);
    
    // ===== 썸네일 일괄 처리 최적화 =====
    
    /**
     * 썸네일 일괄 조회 (여러 루트) - 성능 최적화
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId IN :routeIds " +
           "AND (ri.isMain = true OR ri.displayOrder = 1) " +
           "AND ri.isActive = true " +
           "ORDER BY ri.route.routeId, ri.displayOrder ASC")
    List<RouteImage> findThumbnailsByRouteIds(@Param("routeIds") List<Long> routeIds);
    
    /**
     * 대표 이미지 일괄 조회 (메인 이미지만)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.isMain = true AND ri.isActive = true " +
           "ORDER BY ri.route.routeId, ri.displayOrder ASC")
    List<RouteImage> findAllMainImages();
    
    /**
     * 특정 타입의 첫 번째 이미지들 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.imageType = :imageType AND ri.displayOrder = 1 AND ri.isActive = true " +
           "ORDER BY ri.route.routeId")
    List<RouteImage> findFirstImagesByType(@Param("imageType") String imageType);
    
    /**
     * 루트별 썸네일 URL 조회 (URL만 반환 - 성능 최적화)
     */
    @Query("SELECT ri.route.routeId, ri.imageUrl, ri.thumbnailUrl FROM RouteImage ri " +
           "WHERE ri.route.routeId IN :routeIds " +
           "AND (ri.isMain = true OR ri.displayOrder = 1) " +
           "AND ri.isActive = true " +
           "ORDER BY ri.route.routeId, ri.displayOrder ASC")
    List<Object[]> findThumbnailUrlsByRouteIds(@Param("routeIds") List<Long> routeIds);
    
    /**
     * 배치 썸네일 조회 (성능 특화)
     */
    @Query(value = "SELECT DISTINCT ri.route_id, ri.image_url, ri.thumbnail_url, ri.width, ri.height " +
                   "FROM route_images ri " +
                   "WHERE ri.route_id IN (:routeIds) " +
                   "AND ri.is_active = true " +
                   "AND (ri.is_main = true OR ri.display_order = 1) " +
                   "ORDER BY ri.route_id, ri.display_order", nativeQuery = true)
    List<Object[]> findThumbnailDataByRouteIds(@Param("routeIds") List<Long> routeIds);
    
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
    @Query("SELECT ri.imageType, COUNT(ri) as imageCount, AVG(ri.fileSize) as avgSize FROM RouteImage ri " +
           "WHERE ri.isActive = true AND ri.imageType IS NOT NULL " +
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
     * 태블릿 최적화 이미지 조회
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId " +
           "AND ri.width <= 1024 AND ri.width > 800 AND ri.fileSize <= 1000000 " +
           "AND ri.isActive = true " +
           "ORDER BY ri.displayOrder")
    List<RouteImage> findOptimizedImagesForTablet(@Param("routeId") Long routeId);
    
    /**
     * 고해상도 이미지 조회 (데스크톱)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId " +
           "AND ri.width >= 1920 AND ri.height >= 1080 " +
           "AND ri.isActive = true " +
           "ORDER BY ri.displayOrder")
    List<RouteImage> findHighResolutionImages(@Param("routeId") Long routeId);
    
    /**
     * 품질별 이미지 필터링 (유연한 조건)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId " +
           "AND (:minWidth IS NULL OR ri.width >= :minWidth) " +
           "AND (:maxWidth IS NULL OR ri.width <= :maxWidth) " +
           "AND (:minHeight IS NULL OR ri.height >= :minHeight) " +
           "AND (:maxHeight IS NULL OR ri.height <= :maxHeight) " +
           "AND (:maxFileSize IS NULL OR ri.fileSize <= :maxFileSize) " +
           "AND ri.isActive = true " +
           "ORDER BY ri.displayOrder")
    List<RouteImage> findImagesByQuality(@Param("routeId") Long routeId,
                                        @Param("minWidth") Integer minWidth,
                                        @Param("maxWidth") Integer maxWidth,
                                        @Param("minHeight") Integer minHeight,
                                        @Param("maxHeight") Integer maxHeight,
                                        @Param("maxFileSize") Long maxFileSize);
    
    /**
     * 압축된 이미지 조회 (파일 크기 기준)
     */
    @Query("SELECT ri FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId " +
           "AND ri.fileSize <= :maxFileSize " +
           "AND ri.isActive = true " +
           "ORDER BY ri.fileSize ASC, ri.displayOrder ASC")
    List<RouteImage> findCompressedImages(@Param("routeId") Long routeId, 
                                         @Param("maxFileSize") Long maxFileSize);
    
    /**
     * 적응형 이미지 조회 (디바이스별 최적화)
     */
    @Query(value = "SELECT ri.* FROM route_images ri " +
                   "WHERE ri.route_id = :routeId AND ri.is_active = true " +
                   "AND CASE " +
                   "  WHEN :deviceType = 'mobile' THEN ri.width <= 800 AND ri.file_size <= 500000 " +
                   "  WHEN :deviceType = 'tablet' THEN ri.width <= 1024 AND ri.file_size <= 1000000 " +
                   "  WHEN :deviceType = 'desktop' THEN ri.width >= 1200 " +
                   "  ELSE true " +
                   "END " +
                   "ORDER BY ri.display_order", nativeQuery = true)
    List<RouteImage> findAdaptiveImages(@Param("routeId") Long routeId, @Param("deviceType") String deviceType);
    
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
    @Query("UPDATE RouteImage ri SET ri.displayOrder = :newOrder, ri.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.imageId = :imageId")
    int updateDisplayOrder(@Param("imageId") Long imageId, @Param("newOrder") Integer newOrder);
    
    /**
     * 표시 순서 뒤로 밀기 (삽입 시)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.displayOrder = ri.displayOrder + 1, ri.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.route.routeId = :routeId AND ri.displayOrder >= :fromOrder AND ri.isActive = true")
    int shiftDisplayOrdersUp(@Param("routeId") Long routeId, @Param("fromOrder") Integer fromOrder);
    
    /**
     * 표시 순서 앞으로 당기기 (삭제 시)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.displayOrder = ri.displayOrder - 1, ri.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.route.routeId = :routeId AND ri.displayOrder > :fromOrder AND ri.isActive = true")
    int shiftDisplayOrdersDown(@Param("routeId") Long routeId, @Param("fromOrder") Integer fromOrder);
    
    /**
     * 배치 순서 재정렬 (중간에 빈 순서 제거)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE route_images ri " +
                   "JOIN (" +
                   "  SELECT image_id, ROW_NUMBER() OVER (ORDER BY display_order, created_at) as new_order " +
                   "  FROM route_images " +
                   "  WHERE route_id = :routeId AND is_active = true" +
                   ") AS ordered ON ri.image_id = ordered.image_id " +
                   "SET ri.display_order = ordered.new_order, ri.modified_at = NOW()", nativeQuery = true)
    int reorderDisplayOrders(@Param("routeId") Long routeId);
    
    // ===== 대표 이미지 관리 =====
    
    /**
     * 기존 대표 이미지 해제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isMain = false, ri.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.route.routeId = :routeId")
    int clearMainImages(@Param("routeId") Long routeId);
    
    /**
     * 대표 이미지 설정 (순서도 함께 조정)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteImage ri SET ri.isMain = true, ri.displayOrder = 1, ri.modifiedAt = CURRENT_TIMESTAMP " +
           "WHERE ri.imageId = :imageId")
    int setAsMainImage(@Param("imageId") Long imageId);
    
    /**
     * 자동 대표 이미지 설정 (대표 이미지가 없을 때 첫 번째를 대표로)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE route_images ri " +
                   "SET ri.is_main = true, ri.modified_at = NOW() " +
                   "WHERE ri.image_id = (" +
                   "  SELECT sub.image_id FROM (" +
                   "    SELECT image_id FROM route_images " +
                   "    WHERE route_id = :routeId AND is_active = true " +
                   "    ORDER BY display_order ASC, created_at ASC " +
                   "    LIMIT 1" +
                   "  ) AS sub" +
                   ") " +
                   "AND NOT EXISTS (" +
                   "  SELECT 1 FROM route_images " +
                   "  WHERE route_id = :routeId AND is_main = true AND is_active = true" +
                   ")", nativeQuery = true)
    int setAutoMainImage(@Param("routeId") Long routeId);
    
    /**
     * 대표 이미지 존재 확인
     */
    @Query("SELECT CASE WHEN COUNT(ri) > 0 THEN true ELSE false END FROM RouteImage ri " +
           "WHERE ri.route.routeId = :routeId AND ri.isMain = true AND ri.isActive = true")
    boolean hasMainImage(@Param("routeId") Long routeId);
}
```

---

## 📊 인덱스 최적화 전략

### 1. 핵심 성능 인덱스
```sql
-- 루트별 이미지 조회 최적화
CREATE INDEX idx_route_images_route_display 
ON route_images(route_id, is_active, display_order, created_at);

-- 썸네일 일괄 조회 최적화
CREATE INDEX idx_route_images_thumbnail 
ON route_images(route_id, is_main, display_order, is_active) 
INCLUDE (image_url, thumbnail_url, width, height);

-- 해상도별 조회 최적화
CREATE INDEX idx_route_images_resolution 
ON route_images(route_id, width, height, file_size, is_active);

-- 이미지 타입별 조회 최적화
CREATE INDEX idx_route_images_type_display 
ON route_images(image_type, is_active, display_order);

-- 대표 이미지 조회 최적화
CREATE INDEX idx_route_images_main 
ON route_images(is_main, is_active, route_id, display_order);

-- 파일 크기 최적화 인덱스
CREATE INDEX idx_route_images_filesize 
ON route_images(file_size, is_active, route_id);
```

### 2. 복합 조건 인덱스
```sql
-- 디바이스별 최적화 조회
CREATE INDEX idx_route_images_device_optimization 
ON route_images(route_id, is_active, width, file_size, display_order);

-- 이미지 품질 필터링
CREATE INDEX idx_route_images_quality_filter 
ON route_images(route_id, is_active) 
INCLUDE (width, height, file_size, display_order);
```

---

## 🚀 성능 최적화 기법

### 배치 조회 최적화
- **IN 조건 활용**: 여러 루트의 썸네일을 한 번에 조회
- **서브쿼리 최적화**: EXISTS vs IN 성능 비교 적용
- **LIMIT 활용**: 불필요한 데이터 로딩 방지
- **Native Query**: 복잡한 조건의 성능 최적화

### 메모리 효율성
- **필드 선택 조회**: URL만 필요한 경우 Object[] 활용
- **Lazy Loading**: 큰 이미지 데이터의 지연 로딩
- **Projection**: DTO 기반 필요 필드만 조회
- **페이징**: 대량 이미지 데이터의 분할 처리

### 캐시 최적화
- **썸네일 캐싱**: 자주 조회되는 썸네일 Redis 캐싱
- **메타데이터 캐싱**: 이미지 크기/타입 정보 캐싱
- **CDN 연동**: 이미지 파일 자체의 CDN 캐싱
- **Conditional 캐싱**: 조건부 캐싱으로 메모리 절약

---

*루트 이미지 핵심 Repository 완성일: 2025-08-27*  
*분할 원본: step5-3d1_route_image_repositories.md (300줄)*  
*주요 기능: 썸네일 최적화, 해상도별 처리, 표시 순서 관리*  
*다음 단계: 관리 및 통계 Repository 구현*