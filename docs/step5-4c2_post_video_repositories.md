# Step 5-4c2: 게시글 동영상 Repository 생성

## 개요
- **목적**: 게시글 동영상 관리 Repository 생성
- **대상**: PostVideoRepository
- **최적화**: 스트리밍 최적화, 품질 관리, 인코딩 처리

## 1. PostVideoRepository (게시글 동영상 관리)

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

## Projection 인터페이스

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

### VideoAnalyticsProjection
```java
package com.routepick.backend.application.dto.projection;

/**
 * 동영상 분석 Projection
 */
public class VideoAnalyticsProjection {
    private String quality;
    private Long videoCount;
    private Long totalFileSize;
    private Long totalDuration;
    private Long totalViewCount;
    private Double averageFileSize;
    
    public VideoAnalyticsProjection(String quality, Long videoCount, Long totalFileSize, 
                                  Long totalDuration, Long totalViewCount, Double averageFileSize) {
        this.quality = quality;
        this.videoCount = videoCount;
        this.totalFileSize = totalFileSize;
        this.totalDuration = totalDuration;
        this.totalViewCount = totalViewCount;
        this.averageFileSize = averageFileSize;
    }
    
    // Getters
    public String getQuality() { return quality; }
    public Long getVideoCount() { return videoCount; }
    public Long getTotalFileSize() { return totalFileSize; }
    public Long getTotalDuration() { return totalDuration; }
    public Long getTotalViewCount() { return totalViewCount; }
    public Double getAverageFileSize() { return averageFileSize; }
}
```

## 📈 성능 최적화 포인트

### 1. 인덱스 최적화
```sql
-- 게시글 동영상  
CREATE INDEX idx_post_videos_post_display ON post_videos(post_id, display_order);
CREATE INDEX idx_post_videos_encoding ON post_videos(encoding_status, priority DESC);
CREATE INDEX idx_post_videos_views ON post_videos(view_count DESC, created_at DESC);
CREATE INDEX idx_post_videos_quality ON post_videos(quality, status);
CREATE INDEX idx_post_videos_compression ON post_videos(compression_status, file_size DESC);
```

### 2. 캐싱 전략
- **Redis 캐싱**: 인기 동영상 URL, 메타데이터
- **CDN 캐싱**: 동영상 파일, 다양한 품질의 스트림
- **애플리케이션 캐싱**: 자주 조회되는 동영상 정보

### 3. 배치 처리 최적화
- **동영상 인코딩**: 품질별 우선순위 큐 시스템
- **썸네일 생성**: 동영상 업로드 시 자동 생성
- **압축 처리**: 큰 파일 우선으로 백그라운드 처리

### 4. 스트리밍 최적화
- **적응형 스트리밍**: 다양한 품질 제공
- **CDN 전송**: 글로벌 캐싱으로 속도 향상
- **대역폭 최적화**: 사용자 환경에 따른 품질 조절

## 🎯 주요 기능
- ✅ **동영상 품질 관리**: 다양한 해상도/품질 지원
- ✅ **인코딩 처리**: 비동기 인코딩 및 상태 관리
- ✅ **스트리밍 최적화**: 적응형 비트레이트 스트리밍
- ✅ **재생 통계**: 조회수 및 분석 데이터
- ✅ **배치 처리**: 대용량 동영상 일괄 처리
- ✅ **용량 관리**: 파일 크기 모니터링 및 압축

## 🎬 동영상 처리 워크플로우
1. **업로드**: 원본 동영상 저장
2. **썸네일 생성**: 자동 썸네일 추출
3. **인코딩 대기열**: 품질별 우선순위 설정
4. **다중 품질 인코딩**: 480p, 720p, 1080p
5. **CDN 배포**: 글로벌 캐싱 네트워크 배포
6. **스트리밍 제공**: 사용자 환경에 최적화된 품질 제공

---
*Step 5-4c2 완료: 게시글 동영상 Repository 생성 완료*  
*다음: step5-4c3 게시글-루트 태그 Repository 대기 중*