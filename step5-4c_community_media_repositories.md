# Step 5-4c: 커뮤니티 미디어 Repository 생성

## 개요
- **목적**: 커뮤니티 게시글 미디어(이미지, 동영상) 및 루트 태깅 Repository 생성
- **대상**: PostImageRepository, PostVideoRepository, PostRouteTagRepository
- **최적화**: CDN 연동, 미디어 스트리밍, 태깅 연결 관리

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

## 2. PostVideoRepository (게시글 동영상 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.community;

import com.routepick.backend.domain.entity.community.PostVideo;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 동영상 Repository
 * - 스트리밍 최적화 및 품질 관리
 * - 썸네일 및 메타데이터 처리
 * - 대용량 동영상 배치 처리
 */
@Repository
public interface PostVideoRepository extends BaseRepository<PostVideo, Long> {
    
    // 기본 조회 메서드
    List<PostVideo> findByPostIdOrderByCreatedAtDesc(Long postId);
    
    List<PostVideo> findByPostIdOrderByDisplayOrder(Long postId);
    
    Optional<PostVideo> findByIdAndPostId(Long id, Long postId);
    
    // 배치 조회 최적화
    @Query("SELECT pv FROM PostVideo pv WHERE pv.postId IN :postIds ORDER BY pv.postId, pv.displayOrder")
    List<PostVideo> findByPostIdInOrderByPostIdAndDisplayOrder(@Param("postIds") List<Long> postIds);
    
    // 썸네일 전용 조회
    @Query("SELECT new com.routepick.backend.application.dto.projection.VideoThumbnailProjection(" +
           "pv.id, pv.postId, pv.videoUrl, pv.thumbnailUrl, pv.duration, pv.displayOrder) " +
           "FROM PostVideo pv WHERE pv.postId IN :postIds")
    List<VideoThumbnailProjection> findVideoThumbnails(@Param("postIds") List<Long> postIds);
    
    // 스트리밍 품질별 조회
    @Query("SELECT pv FROM PostVideo pv WHERE pv.postId = :postId AND pv.quality = :quality")
    List<PostVideo> findByPostIdAndQuality(@Param("postId") Long postId, @Param("quality") String quality);
    
    // 인코딩 상태별 조회
    List<PostVideo> findByEncodingStatus(String encodingStatus);
    
    @Query("SELECT COUNT(pv) FROM PostVideo pv WHERE pv.encodingStatus = 'PENDING'")
    long countPendingEncodingVideos();
    
    // 용량 및 품질 관리
    @Query("SELECT SUM(pv.fileSize) FROM PostVideo pv WHERE pv.postId = :postId")
    Long calculateTotalFileSizeByPostId(@Param("postId") Long postId);
    
    @Query("SELECT pv FROM PostVideo pv WHERE pv.fileSize > :sizeLimit ORDER BY pv.fileSize DESC")
    List<PostVideo> findLargeVideos(@Param("sizeLimit") Long sizeLimit);
    
    @Query("SELECT AVG(pv.duration) FROM PostVideo pv WHERE pv.postId = :postId AND pv.status = 'ACTIVE'")
    Double getAverageVideoDurationByPostId(@Param("postId") Long postId);
    
    // 재생 통계
    @Modifying
    @Query("UPDATE PostVideo pv SET pv.viewCount = pv.viewCount + 1, pv.lastViewedAt = CURRENT_TIMESTAMP " +
           "WHERE pv.id = :videoId")
    void incrementViewCount(@Param("videoId") Long videoId);
    
    @Query("SELECT pv FROM PostVideo pv WHERE pv.postId = :postId ORDER BY pv.viewCount DESC")
    List<PostVideo> findByPostIdOrderByViewCountDesc(@Param("postId") Long postId);
    
    // 순서 관리
    @Query("SELECT MAX(pv.displayOrder) FROM PostVideo pv WHERE pv.postId = :postId")
    Optional<Integer> findMaxDisplayOrderByPostId(@Param("postId") Long postId);
    
    @Modifying
    @Query("UPDATE PostVideo pv SET pv.displayOrder = pv.displayOrder + 1 " +
           "WHERE pv.postId = :postId AND pv.displayOrder >= :displayOrder")
    void incrementDisplayOrderFromPosition(@Param("postId") Long postId, 
                                         @Param("displayOrder") Integer displayOrder);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.routepick.backend.application.dto.community.PostVideoSearchCriteria;
import com.routepick.backend.application.dto.projection.VideoAnalyticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 동영상 커스텀 Repository
 */
public interface PostVideoRepositoryCustom {
    
    // 고급 검색
    Page<PostVideo> searchVideos(PostVideoSearchCriteria criteria, Pageable pageable);
    
    // 동영상 분석
    List<VideoAnalyticsProjection> getVideoAnalyticsByDateRange(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 인코딩 최적화
    List<PostVideo> findVideosForEncoding(String targetQuality, int limit);
    
    // 스트리밍 최적화
    List<PostVideo> findPopularVideosForCaching(int limit);
    
    // 용량 최적화
    List<PostVideo> findVideosForCompression(Long minFileSize, int limit);
    
    // 품질별 통계
    List<VideoAnalyticsProjection> getVideoQualityStatistics();
    
    // 배치 처리
    void batchUpdateVideoStatus(List<Long> videoIds, String status);
    
    void batchUpdateEncodingStatus(List<Long> videoIds, String encodingStatus);
}
```

### Custom Repository 구현
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.routepick.backend.domain.entity.community.PostVideo;
import com.routepick.backend.domain.entity.community.QPostVideo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글 동영상 커스텀 Repository 구현
 */
@Repository
@RequiredArgsConstructor
public class PostVideoRepositoryCustomImpl implements PostVideoRepositoryCustom {
    
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    
    private static final QPostVideo postVideo = QPostVideo.postVideo;
    
    @Override
    public Page<PostVideo> searchVideos(PostVideoSearchCriteria criteria, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        
        if (criteria.getPostId() != null) {
            builder.and(postVideo.postId.eq(criteria.getPostId()));
        }
        
        if (criteria.getQuality() != null) {
            builder.and(postVideo.quality.eq(criteria.getQuality()));
        }
        
        if (criteria.getEncodingStatus() != null) {
            builder.and(postVideo.encodingStatus.eq(criteria.getEncodingStatus()));
        }
        
        if (criteria.getMinDuration() != null) {
            builder.and(postVideo.duration.goe(criteria.getMinDuration()));
        }
        
        if (criteria.getMaxDuration() != null) {
            builder.and(postVideo.duration.loe(criteria.getMaxDuration()));
        }
        
        if (criteria.getMinFileSize() != null) {
            builder.and(postVideo.fileSize.goe(criteria.getMinFileSize()));
        }
        
        List<PostVideo> content = queryFactory
            .selectFrom(postVideo)
            .where(builder)
            .orderBy(postVideo.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        
        Long total = queryFactory
            .select(postVideo.count())
            .from(postVideo)
            .where(builder)
            .fetchOne();
        
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
    
    @Override
    public List<VideoAnalyticsProjection> getVideoAnalyticsByDateRange(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        return queryFactory
            .select(Projections.constructor(VideoAnalyticsProjection.class,
                postVideo.quality,
                postVideo.count(),
                postVideo.fileSize.sum(),
                postVideo.duration.sum(),
                postVideo.viewCount.sum(),
                postVideo.fileSize.avg()
            ))
            .from(postVideo)
            .where(postVideo.createdAt.between(startDate, endDate)
                .and(postVideo.status.eq("ACTIVE")))
            .groupBy(postVideo.quality)
            .orderBy(postVideo.viewCount.sum().desc())
            .fetch();
    }
    
    @Override
    public List<PostVideo> findVideosForEncoding(String targetQuality, int limit) {
        return queryFactory
            .selectFrom(postVideo)
            .where(postVideo.encodingStatus.eq("PENDING")
                .and(postVideo.targetQuality.eq(targetQuality))
                .and(postVideo.status.eq("ACTIVE")))
            .orderBy(postVideo.priority.desc(), postVideo.createdAt.asc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<PostVideo> findPopularVideosForCaching(int limit) {
        return queryFactory
            .selectFrom(postVideo)
            .where(postVideo.status.eq("ACTIVE")
                .and(postVideo.encodingStatus.eq("COMPLETED")))
            .orderBy(postVideo.viewCount.desc(), postVideo.createdAt.desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<PostVideo> findVideosForCompression(Long minFileSize, int limit) {
        return queryFactory
            .selectFrom(postVideo)
            .where(postVideo.fileSize.gt(minFileSize)
                .and(postVideo.compressionStatus.eq("PENDING"))
                .and(postVideo.status.eq("ACTIVE")))
            .orderBy(postVideo.fileSize.desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<VideoAnalyticsProjection> getVideoQualityStatistics() {
        return queryFactory
            .select(Projections.constructor(VideoAnalyticsProjection.class,
                postVideo.quality,
                postVideo.count(),
                postVideo.fileSize.sum(),
                postVideo.duration.sum(),
                postVideo.viewCount.sum(),
                postVideo.fileSize.avg()
            ))
            .from(postVideo)
            .where(postVideo.status.eq("ACTIVE"))
            .groupBy(postVideo.quality)
            .orderBy(postVideo.count().desc())
            .fetch();
    }
    
    @Override
    public void batchUpdateVideoStatus(List<Long> videoIds, String status) {
        queryFactory
            .update(postVideo)
            .set(postVideo.status, status)
            .set(postVideo.updatedAt, LocalDateTime.now())
            .where(postVideo.id.in(videoIds))
            .execute();
        
        entityManager.flush();
        entityManager.clear();
    }
    
    @Override
    public void batchUpdateEncodingStatus(List<Long> videoIds, String encodingStatus) {
        queryFactory
            .update(postVideo)
            .set(postVideo.encodingStatus, encodingStatus)
            .set(postVideo.updatedAt, LocalDateTime.now())
            .where(postVideo.id.in(videoIds))
            .execute();
        
        entityManager.flush();
        entityManager.clear();
    }
}
```

## 3. PostRouteTagRepository (게시글-루트 태그 연결 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.community;

import com.routepick.backend.domain.entity.community.PostRouteTag;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 게시글-루트 태그 연결 Repository
 * - 게시글과 클라이밍 루트 태깅 시스템
 * - 루트 추천 및 관련성 분석
 * - 태그 기반 콘텐츠 분류
 */
@Repository
public interface PostRouteTagRepository extends BaseRepository<PostRouteTag, Long> {
    
    // 기본 연관 관계 조회
    List<PostRouteTag> findByPostIdOrderByCreatedAt(Long postId);
    
    List<PostRouteTag> findByRouteIdOrderByCreatedAt(Long routeId);
    
    Optional<PostRouteTag> findByPostIdAndRouteId(Long postId, Long routeId);
    
    // 카운트 조회
    long countByPostId(Long postId);
    
    long countByRouteId(Long routeId);
    
    @Query("SELECT COUNT(prt) FROM PostRouteTag prt WHERE prt.postId = :postId AND prt.status = 'ACTIVE'")
    long countActiveTagsByPostId(@Param("postId") Long postId);
    
    // 배치 조회 최적화
    @Query("SELECT prt FROM PostRouteTag prt WHERE prt.postId IN :postIds ORDER BY prt.postId, prt.createdAt")
    List<PostRouteTag> findByPostIdInOrderByPostIdAndCreatedAt(@Param("postIds") List<Long> postIds);
    
    @Query("SELECT prt FROM PostRouteTag prt WHERE prt.routeId IN :routeIds ORDER BY prt.routeId, prt.createdAt DESC")
    List<PostRouteTag> findByRouteIdInOrderByRouteIdAndCreatedAtDesc(@Param("routeIds") List<Long> routeIds);
    
    // 관련성 분석
    @Query("SELECT prt.routeId, COUNT(prt) as tagCount FROM PostRouteTag prt " +
           "WHERE prt.postId IN :postIds AND prt.status = 'ACTIVE' " +
           "GROUP BY prt.routeId ORDER BY tagCount DESC")
    List<Object[]> findPopularRoutesByPostIds(@Param("postIds") List<Long> postIds);
    
    @Query("SELECT prt.postId, COUNT(prt) as tagCount FROM PostRouteTag prt " +
           "WHERE prt.routeId IN :routeIds AND prt.status = 'ACTIVE' " +
           "GROUP BY prt.postId ORDER BY tagCount DESC")
    List<Object[]> findRelatedPostsByRouteIds(@Param("routeIds") List<Long> routeIds);
    
    // 중복 체크 및 유니크 제약
    boolean existsByPostIdAndRouteId(Long postId, Long routeId);
    
    @Query("SELECT CASE WHEN COUNT(prt) > 0 THEN true ELSE false END FROM PostRouteTag prt " +
           "WHERE prt.postId = :postId AND prt.routeId = :routeId AND prt.status = 'ACTIVE'")
    boolean existsActiveTagByPostIdAndRouteId(@Param("postId") Long postId, @Param("routeId") Long routeId);
    
    // 태그 관리
    @Modifying
    @Query("UPDATE PostRouteTag prt SET prt.status = 'INACTIVE' " +
           "WHERE prt.postId = :postId AND prt.routeId = :routeId")
    void deactivateTag(@Param("postId") Long postId, @Param("routeId") Long routeId);
    
    @Modifying
    @Query("UPDATE PostRouteTag prt SET prt.status = 'ACTIVE' " +
           "WHERE prt.postId = :postId AND prt.routeId = :routeId")
    void activateTag(@Param("postId") Long postId, @Param("routeId") Long routeId);
    
    // 추천 시스템 지원
    @Query("SELECT prt.routeId FROM PostRouteTag prt " +
           "WHERE prt.postId IN (SELECT p.id FROM Post p WHERE p.authorId = :userId) " +
           "AND prt.status = 'ACTIVE' " +
           "GROUP BY prt.routeId ORDER BY COUNT(prt) DESC")
    List<Long> findFrequentRouteIdsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT prt FROM PostRouteTag prt " +
           "JOIN Post p ON prt.postId = p.id " +
           "WHERE p.authorId IN :userIds AND prt.status = 'ACTIVE' " +
           "ORDER BY prt.createdAt DESC")
    List<PostRouteTag> findRecentTagsByUserIds(@Param("userIds") List<Long> userIds);
    
    // 통계 조회
    @Query("SELECT DATE(prt.createdAt) as tagDate, COUNT(prt) as dailyCount " +
           "FROM PostRouteTag prt WHERE prt.createdAt >= :startDate AND prt.status = 'ACTIVE' " +
           "GROUP BY DATE(prt.createdAt) ORDER BY tagDate DESC")
    List<Object[]> getDailyTaggingStatistics(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT prt.routeId, COUNT(DISTINCT prt.postId) as postCount " +
           "FROM PostRouteTag prt WHERE prt.status = 'ACTIVE' " +
           "GROUP BY prt.routeId HAVING postCount >= :minPostCount " +
           "ORDER BY postCount DESC")
    List<Object[]> findPopularRoutesWithMinPosts(@Param("minPostCount") Long minPostCount);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.routepick.backend.application.dto.community.PostRouteTagSearchCriteria;
import com.routepick.backend.application.dto.projection.RouteTagAnalyticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글-루트 태그 커스텀 Repository
 */
public interface PostRouteTagRepositoryCustom {
    
    // 고급 검색
    Page<PostRouteTag> searchRouteTags(PostRouteTagSearchCriteria criteria, Pageable pageable);
    
    // 추천 시스템 지원
    List<Long> findRecommendedRouteIds(Long userId, int limit);
    
    List<Long> findSimilarPostIds(Long postId, int limit);
    
    // 태그 분석
    List<RouteTagAnalyticsProjection> getRouteTagAnalytics(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 트렌드 분석
    List<Long> findTrendingRouteIds(int days, int limit);
    
    List<Long> findTrendingPostIds(int days, int limit);
    
    // 관련성 점수 계산
    List<RouteTagAnalyticsProjection> calculateRouteRelatedness(List<Long> routeIds);
    
    // 사용자 관심사 분석
    List<Long> findUserInterestedRouteIds(Long userId, int limit);
    
    // 배치 처리
    void batchCreateTags(List<PostRouteTag> tags);
    
    void batchUpdateTagStatus(List<Long> tagIds, String status);
    
    // 정리 작업
    void cleanupInactiveTags(LocalDateTime beforeDate);
    
    void removeDuplicateTags();
}
```

### Custom Repository 구현
```java
package com.routepick.backend.infrastructure.persistence.repository.community.custom;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.routepick.backend.domain.entity.community.PostRouteTag;
import com.routepick.backend.domain.entity.community.QPostRouteTag;
import com.routepick.backend.domain.entity.community.QPost;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 게시글-루트 태그 커스텀 Repository 구현
 */
@Repository
@RequiredArgsConstructor
public class PostRouteTagRepositoryCustomImpl implements PostRouteTagRepositoryCustom {
    
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    
    private static final QPostRouteTag postRouteTag = QPostRouteTag.postRouteTag;
    private static final QPost post = QPost.post;
    
    @Override
    public Page<PostRouteTag> searchRouteTags(PostRouteTagSearchCriteria criteria, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        
        if (criteria.getPostId() != null) {
            builder.and(postRouteTag.postId.eq(criteria.getPostId()));
        }
        
        if (criteria.getRouteId() != null) {
            builder.and(postRouteTag.routeId.eq(criteria.getRouteId()));
        }
        
        if (criteria.getStatus() != null) {
            builder.and(postRouteTag.status.eq(criteria.getStatus()));
        }
        
        if (criteria.getAuthorId() != null) {
            builder.and(postRouteTag.postId.in(
                queryFactory.select(post.id)
                           .from(post)
                           .where(post.authorId.eq(criteria.getAuthorId()))
            ));
        }
        
        if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
            builder.and(postRouteTag.createdAt.between(criteria.getStartDate(), criteria.getEndDate()));
        }
        
        List<PostRouteTag> content = queryFactory
            .selectFrom(postRouteTag)
            .where(builder)
            .orderBy(postRouteTag.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        
        Long total = queryFactory
            .select(postRouteTag.count())
            .from(postRouteTag)
            .where(builder)
            .fetchOne();
        
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
    
    @Override
    public List<Long> findRecommendedRouteIds(Long userId, int limit) {
        // 사용자가 자주 태그한 루트와 유사한 루트 찾기
        return queryFactory
            .select(postRouteTag.routeId)
            .from(postRouteTag)
            .join(post).on(postRouteTag.postId.eq(post.id))
            .where(post.authorId.ne(userId) // 본인 게시글 제외
                .and(postRouteTag.routeId.in(
                    queryFactory.select(postRouteTag.routeId)
                               .from(postRouteTag)
                               .join(post).on(postRouteTag.postId.eq(post.id))
                               .where(post.authorId.eq(userId))
                               .groupBy(postRouteTag.routeId)
                               .having(postRouteTag.count().goe(2L))
                ))
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<Long> findSimilarPostIds(Long postId, int limit) {
        // 같은 루트를 태그한 다른 게시글들
        List<Long> routeIds = queryFactory
            .select(postRouteTag.routeId)
            .from(postRouteTag)
            .where(postRouteTag.postId.eq(postId)
                .and(postRouteTag.status.eq("ACTIVE")))
            .fetch();
        
        if (routeIds.isEmpty()) {
            return List.of();
        }
        
        return queryFactory
            .select(postRouteTag.postId)
            .from(postRouteTag)
            .where(postRouteTag.routeId.in(routeIds)
                .and(postRouteTag.postId.ne(postId))
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.postId)
            .orderBy(postRouteTag.count().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<RouteTagAnalyticsProjection> getRouteTagAnalytics(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        return queryFactory
            .select(Projections.constructor(RouteTagAnalyticsProjection.class,
                postRouteTag.routeId,
                postRouteTag.count(),
                postRouteTag.postId.countDistinct(),
                post.authorId.countDistinct()
            ))
            .from(postRouteTag)
            .join(post).on(postRouteTag.postId.eq(post.id))
            .where(postRouteTag.createdAt.between(startDate, endDate)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc())
            .fetch();
    }
    
    @Override
    public List<Long> findTrendingRouteIds(int days, int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        return queryFactory
            .select(postRouteTag.routeId)
            .from(postRouteTag)
            .where(postRouteTag.createdAt.goe(startDate)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<Long> findTrendingPostIds(int days, int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        return queryFactory
            .select(postRouteTag.postId)
            .from(postRouteTag)
            .where(postRouteTag.createdAt.goe(startDate)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.postId)
            .orderBy(postRouteTag.count().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<RouteTagAnalyticsProjection> calculateRouteRelatedness(List<Long> routeIds) {
        return queryFactory
            .select(Projections.constructor(RouteTagAnalyticsProjection.class,
                postRouteTag.routeId,
                postRouteTag.count(),
                postRouteTag.postId.countDistinct(),
                post.authorId.countDistinct()
            ))
            .from(postRouteTag)
            .join(post).on(postRouteTag.postId.eq(post.id))
            .where(postRouteTag.routeId.in(routeIds)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc())
            .fetch();
    }
    
    @Override
    public List<Long> findUserInterestedRouteIds(Long userId, int limit) {
        return queryFactory
            .select(postRouteTag.routeId)
            .from(postRouteTag)
            .join(post).on(postRouteTag.postId.eq(post.id))
            .where(post.authorId.eq(userId)
                .and(postRouteTag.status.eq("ACTIVE")))
            .groupBy(postRouteTag.routeId)
            .orderBy(postRouteTag.count().desc(), postRouteTag.createdAt.max().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public void batchCreateTags(List<PostRouteTag> tags) {
        int batchSize = 50;
        for (int i = 0; i < tags.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, tags.size());
            List<PostRouteTag> batch = tags.subList(i, endIndex);
            
            for (PostRouteTag tag : batch) {
                entityManager.persist(tag);
            }
            entityManager.flush();
            entityManager.clear();
        }
    }
    
    @Override
    public void batchUpdateTagStatus(List<Long> tagIds, String status) {
        queryFactory
            .update(postRouteTag)
            .set(postRouteTag.status, status)
            .set(postRouteTag.updatedAt, LocalDateTime.now())
            .where(postRouteTag.id.in(tagIds))
            .execute();
        
        entityManager.flush();
        entityManager.clear();
    }
    
    @Override
    public void cleanupInactiveTags(LocalDateTime beforeDate) {
        queryFactory
            .delete(postRouteTag)
            .where(postRouteTag.status.eq("INACTIVE")
                .and(postRouteTag.updatedAt.lt(beforeDate)))
            .execute();
        
        entityManager.flush();
    }
    
    @Override
    public void removeDuplicateTags() {
        // 중복 태그 제거 - 가장 최근 것만 유지
        List<PostRouteTag> duplicates = queryFactory
            .selectFrom(postRouteTag)
            .where(postRouteTag.id.notIn(
                queryFactory.select(postRouteTag.id.max())
                           .from(postRouteTag)
                           .groupBy(postRouteTag.postId, postRouteTag.routeId)
            ))
            .fetch();
        
        for (PostRouteTag duplicate : duplicates) {
            entityManager.remove(duplicate);
        }
        entityManager.flush();
    }
}
```

## Projection 인터페이스들

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

### VideoThumbnailProjection
```java
package com.routepick.backend.application.dto.projection;

/**
 * 동영상 썸네일 최적화 Projection
 */
public class VideoThumbnailProjection {
    private Long id;
    private Long postId;
    private String videoUrl;
    private String thumbnailUrl;
    private Integer duration;
    private Integer displayOrder;
    
    public VideoThumbnailProjection(Long id, Long postId, String videoUrl, 
                                  String thumbnailUrl, Integer duration, Integer displayOrder) {
        this.id = id;
        this.postId = postId;
        this.videoUrl = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.displayOrder = displayOrder;
    }
    
    // Getters
    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public String getVideoUrl() { return videoUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Integer getDuration() { return duration; }
    public Integer getDisplayOrder() { return displayOrder; }
}
```

### RouteTagAnalyticsProjection
```java
package com.routepick.backend.application.dto.projection;

/**
 * 루트 태그 분석 Projection
 */
public class RouteTagAnalyticsProjection {
    private Long routeId;
    private Long tagCount;
    private Long postCount;
    private Long uniqueUserCount;
    
    public RouteTagAnalyticsProjection(Long routeId, Long tagCount, 
                                     Long postCount, Long uniqueUserCount) {
        this.routeId = routeId;
        this.tagCount = tagCount;
        this.postCount = postCount;
        this.uniqueUserCount = uniqueUserCount;
    }
    
    // Getters
    public Long getRouteId() { return routeId; }
    public Long getTagCount() { return tagCount; }
    public Long getPostCount() { return postCount; }
    public Long getUniqueUserCount() { return uniqueUserCount; }
}
```

## 📈 성능 최적화 포인트

### 1. 인덱스 최적화
```sql
-- 게시글 이미지
CREATE INDEX idx_post_images_post_display ON post_images(post_id, display_order);
CREATE INDEX idx_post_images_size_status ON post_images(file_size, status);

-- 게시글 동영상  
CREATE INDEX idx_post_videos_post_display ON post_videos(post_id, display_order);
CREATE INDEX idx_post_videos_encoding ON post_videos(encoding_status, priority DESC);
CREATE INDEX idx_post_videos_views ON post_videos(view_count DESC, created_at DESC);

-- 게시글-루트 태그
CREATE UNIQUE INDEX idx_post_route_tags_unique ON post_route_tags(post_id, route_id, status);
CREATE INDEX idx_post_route_tags_route_created ON post_route_tags(route_id, created_at DESC);
CREATE INDEX idx_post_route_tags_trending ON post_route_tags(created_at DESC, status);
```

### 2. 캐싱 전략
- **Redis 캐싱**: 인기 미디어 URL, 썸네일 데이터
- **CDN 캐싱**: 이미지/동영상 파일, 변환된 미디어
- **애플리케이션 캐싱**: 자주 조회되는 태그 관계

### 3. 배치 처리 최적화
- **이미지 압축**: 비동기 배치 처리
- **동영상 인코딩**: 품질별 우선순위 큐
- **태그 정리**: 주기적 중복 제거 및 정리

## 🎯 다음 단계 예고
- **5-5단계**: Message & Payment Repository (메시지, 결제)
- **5-6단계**: Notification & System Repository (알림, 시스템)
- **Repository 레이어 완료** 후 **Service 레이어** 진행

---
*Step 5-4c 완료: 커뮤니티 미디어 Repository 3개 생성 완료*  
*다음: 5-5단계 Message & Payment Repository 대기 중*