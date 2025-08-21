# Step 5-4c1: 게시글 이미지 Repository 생성

## 개요
- **목적**: 게시글 이미지 관리 Repository 생성
- **대상**: PostImageRepository
- **최적화**: CDN 연동, 썸네일 처리, 대용량 이미지 배치 처리

## 1. PostImageRepository (게시글 이미지 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.community;

import com.routepick.backend.domain.entity.community.PostImage;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 이미지 Repository
 * - 이미지 순서 관리 및 최적화
 * - CDN 캐싱 및 썸네일 처리
 * - 대용량 이미지 배치 처리
 */
@Repository
public interface PostImageRepository extends BaseRepository<PostImage, Long> {
    
    // 기본 조회 메서드
    List<PostImage> findByPostIdOrderByDisplayOrder(Long postId);
    
    List<PostImage> findByPostIdOrderByCreatedAtDesc(Long postId);
    
    Optional<PostImage> findByIdAndPostId(Long id, Long postId);
    
    // 배치 조회 최적화
    @Query("SELECT pi FROM PostImage pi WHERE pi.postId IN :postIds ORDER BY pi.postId, pi.displayOrder")
    List<PostImage> findByPostIdInOrderByPostIdAndDisplayOrder(@Param("postIds") List<Long> postIds);
    
    // 썸네일 전용 조회
    @Query("SELECT new com.routepick.backend.application.dto.projection.ImageThumbnailProjection(" +
           "pi.id, pi.postId, pi.imageUrl, pi.thumbnailUrl, pi.displayOrder) " +
           "FROM PostImage pi WHERE pi.postId IN :postIds")
    List<ImageThumbnailProjection> findThumbnailsByPostIds(@Param("postIds") List<Long> postIds);
    
    // 순서 관리
    @Modifying
    @Query("UPDATE PostImage pi SET pi.displayOrder = pi.displayOrder + 1 " +
           "WHERE pi.postId = :postId AND pi.displayOrder >= :displayOrder")
    void incrementDisplayOrderFromPosition(@Param("postId") Long postId, 
                                         @Param("displayOrder") Integer displayOrder);
    
    @Query("SELECT MAX(pi.displayOrder) FROM PostImage pi WHERE pi.postId = :postId")
    Optional<Integer> findMaxDisplayOrderByPostId(@Param("postId") Long postId);
    
    // 용량 관리
    @Query("SELECT SUM(pi.fileSize) FROM PostImage pi WHERE pi.postId = :postId")
    Long calculateTotalFileSizeByPostId(@Param("postId") Long postId);
    
    @Query("SELECT pi FROM PostImage pi WHERE pi.fileSize > :sizeLimit")
    List<PostImage> findLargeImages(@Param("sizeLimit") Long sizeLimit);
    
    // 상태별 조회
    List<PostImage> findByPostIdAndStatus(Long postId, String status);
    
    @Query("SELECT COUNT(pi) FROM PostImage pi WHERE pi.postId = :postId AND pi.status = 'ACTIVE'")
    long countActiveImagesByPostId(@Param("postId") Long postId);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.routepick.backend.application.dto.community.PostImageSearchCriteria;
import com.routepick.backend.application.dto.projection.ImageStatisticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 이미지 커스텀 Repository
 */
public interface PostImageRepositoryCustom {
    
    // 고급 검색
    Page<PostImage> searchImages(PostImageSearchCriteria criteria, Pageable pageable);
    
    // 이미지 통계
    List<ImageStatisticsProjection> getImageStatisticsByDateRange(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // CDN 최적화
    List<PostImage> findImagesForCdnOptimization(int limit);
    
    // 중복 이미지 탐지
    List<PostImage> findDuplicateImagesByHash(String imageHash);
    
    // 대용량 배치 처리
    void batchUpdateImageStatus(List<Long> imageIds, String status);
    
    // 압축 대상 이미지
    List<PostImage> findImagesNeedingCompression(Long minFileSize, int limit);
}
```

### Custom Repository 구현
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.routepick.backend.domain.entity.community.PostImage;
import com.routepick.backend.domain.entity.community.QPostImage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 이미지 커스텀 Repository 구현
 */
@Repository
@RequiredArgsConstructor
public class PostImageRepositoryCustomImpl implements PostImageRepositoryCustom {
    
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    
    private static final QPostImage postImage = QPostImage.postImage;
    
    @Override
    public Page<PostImage> searchImages(PostImageSearchCriteria criteria, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        
        if (criteria.getPostId() != null) {
            builder.and(postImage.postId.eq(criteria.getPostId()));
        }
        
        if (criteria.getStatus() != null) {
            builder.and(postImage.status.eq(criteria.getStatus()));
        }
        
        if (criteria.getMinFileSize() != null) {
            builder.and(postImage.fileSize.goe(criteria.getMinFileSize()));
        }
        
        if (criteria.getMaxFileSize() != null) {
            builder.and(postImage.fileSize.loe(criteria.getMaxFileSize()));
        }
        
        if (criteria.getImageType() != null) {
            builder.and(postImage.imageType.eq(criteria.getImageType()));
        }
        
        List<PostImage> content = queryFactory
            .selectFrom(postImage)
            .where(builder)
            .orderBy(postImage.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        
        Long total = queryFactory
            .select(postImage.count())
            .from(postImage)
            .where(builder)
            .fetchOne();
        
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
    
    @Override
    public List<ImageStatisticsProjection> getImageStatisticsByDateRange(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        return queryFactory
            .select(Projections.constructor(ImageStatisticsProjection.class,
                postImage.createdAt.yearMonth(),
                postImage.count(),
                postImage.fileSize.sum(),
                postImage.fileSize.avg()
            ))
            .from(postImage)
            .where(postImage.createdAt.between(startDate, endDate)
                .and(postImage.status.eq("ACTIVE")))
            .groupBy(postImage.createdAt.yearMonth())
            .orderBy(postImage.createdAt.yearMonth().desc())
            .fetch();
    }
    
    @Override
    public List<PostImage> findImagesForCdnOptimization(int limit) {
        return queryFactory
            .selectFrom(postImage)
            .where(postImage.cdnUrl.isNull()
                .and(postImage.status.eq("ACTIVE")))
            .orderBy(postImage.createdAt.asc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<PostImage> findDuplicateImagesByHash(String imageHash) {
        return queryFactory
            .selectFrom(postImage)
            .where(postImage.imageHash.eq(imageHash)
                .and(postImage.status.eq("ACTIVE")))
            .fetch();
    }
    
    @Override
    public void batchUpdateImageStatus(List<Long> imageIds, String status) {
        queryFactory
            .update(postImage)
            .set(postImage.status, status)
            .set(postImage.updatedAt, LocalDateTime.now())
            .where(postImage.id.in(imageIds))
            .execute();
        
        entityManager.flush();
        entityManager.clear();
    }
    
    @Override
    public List<PostImage> findImagesNeedingCompression(Long minFileSize, int limit) {
        return queryFactory
            .selectFrom(postImage)
            .where(postImage.fileSize.gt(minFileSize)
                .and(postImage.compressionStatus.eq("PENDING"))
                .and(postImage.status.eq("ACTIVE")))
            .orderBy(postImage.fileSize.desc())
            .limit(limit)
            .fetch();
    }
}
```

## Projection 인터페이스

### ImageThumbnailProjection
```java
package com.routepick.backend.application.dto.projection;

/**
 * 이미지 썸네일 최적화 Projection
 */
public class ImageThumbnailProjection {
    private Long id;
    private Long postId;
    private String imageUrl;
    private String thumbnailUrl;
    private Integer displayOrder;
    
    public ImageThumbnailProjection(Long id, Long postId, String imageUrl, 
                                  String thumbnailUrl, Integer displayOrder) {
        this.id = id;
        this.postId = postId;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.displayOrder = displayOrder;
    }
    
    // Getters
    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public String getImageUrl() { return imageUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Integer getDisplayOrder() { return displayOrder; }
}
```

### ImageStatisticsProjection
```java
package com.routepick.backend.application.dto.projection;

import java.time.YearMonth;

/**
 * 이미지 통계 Projection
 */
public class ImageStatisticsProjection {
    private YearMonth month;
    private Long imageCount;
    private Long totalSize;
    private Double averageSize;
    
    public ImageStatisticsProjection(YearMonth month, Long imageCount, 
                                   Long totalSize, Double averageSize) {
        this.month = month;
        this.imageCount = imageCount;
        this.totalSize = totalSize;
        this.averageSize = averageSize;
    }
    
    // Getters
    public YearMonth getMonth() { return month; }
    public Long getImageCount() { return imageCount; }
    public Long getTotalSize() { return totalSize; }
    public Double getAverageSize() { return averageSize; }
}
```

## 📈 성능 최적화 포인트

### 1. 인덱스 최적화
```sql
-- 게시글 이미지
CREATE INDEX idx_post_images_post_display ON post_images(post_id, display_order);
CREATE INDEX idx_post_images_size_status ON post_images(file_size, status);
CREATE INDEX idx_post_images_hash ON post_images(image_hash);
CREATE INDEX idx_post_images_cdn ON post_images(cdn_url);
CREATE INDEX idx_post_images_compression ON post_images(compression_status, file_size DESC);
```

### 2. 캐싱 전략
- **Redis 캐싱**: 인기 이미지 URL, 썸네일 데이터
- **CDN 캐싱**: 이미지 파일, 썸네일, 압축된 이미지
- **애플리케이션 캐싱**: 자주 조회되는 이미지 메타데이터

### 3. 배치 처리 최적화
- **이미지 압축**: 비동기 배치 처리 (큰 파일 우선)
- **썸네일 생성**: 백그라운드 작업으로 처리
- **CDN 업로드**: 일괄 처리로 네트워크 최적화

## 🎯 주요 기능
- ✅ **이미지 순서 관리**: displayOrder 기반 정렬
- ✅ **썸네일 최적화**: 별도 Projection으로 성능 향상
- ✅ **CDN 연동**: 캐싱 및 전송 최적화
- ✅ **용량 관리**: 파일 크기 모니터링 및 압축
- ✅ **배치 처리**: 대용량 이미지 일괄 처리
- ✅ **중복 탐지**: 해시 기반 중복 이미지 감지

---
*Step 5-4c1 완료: 게시글 이미지 Repository 생성 완료*  
*다음: step5-4c2 게시글 동영상 Repository 대기 중*