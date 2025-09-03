# Step 5-3d2: 루트 동영상 Repository 생성

## 개요
- **목적**: 루트 동영상 전문 Repository 생성
- **대상**: RouteVideoRepository
- **최적화**: 스트리밍 최적화, 트랜스코딩 관리, 동영상 분석

## 🎥 RouteVideoRepository - 루트 동영상 Repository

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
           "WHERE rv.route.routeId = :routeId " +
           "AND rv.videoWidth <= 1280 AND rv.fileSize <= 50000000 " +
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
           "WHERE rv.route.routeId = :routeId " +
           "AND rv.videoFormat IN ('MP4', 'WEBM') " +
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

## Custom Repository Interface

```java
package com.routepick.domain.route.repository.custom;

import com.routepick.domain.route.entity.RouteVideo;
import com.routepick.application.dto.route.VideoMetadataDto;
import com.routepick.application.dto.route.VideoAnalyticsDto;

import java.util.List;

/**
 * 루트 동영상 커스텀 Repository
 */
public interface RouteVideoRepositoryCustom {
    
    /**
     * 적응형 스트리밍 품질 조회
     */
    List<RouteVideo> findAdaptiveStreamingQualities(Long routeId);
    
    /**
     * 상세 동영상 분석 데이터
     */
    VideoAnalyticsDto getDetailedVideoAnalytics(Long videoId);
    
    /**
     * 트랜스코딩 대상 동영상 조회
     */
    List<RouteVideo> findVideosForTranscoding();
    
    /**
     * 동영상 메타데이터 배치 업데이트
     */
    void batchUpdateVideoMetadata(List<VideoMetadataDto> metadata);
    
    /**
     * 스트리밍 성능 분석
     */
    List<VideoAnalyticsDto> getStreamingPerformanceAnalytics();
    
    /**
     * 동영상 품질별 통계
     */
    List<VideoAnalyticsDto> getVideoQualityStatistics();
}
```

## 📈 성능 최적화 포인트

### 1. 인덱스 최적화
```sql
-- 동영상 품질별 인덱스
CREATE INDEX idx_route_video_quality_optimization 
ON route_videos(route_id, video_quality, video_width, video_height);

-- 동영상 스트리밍 최적화 인덱스
CREATE INDEX idx_route_video_streaming 
ON route_videos(route_id, video_format, frame_rate, is_active);

-- 재생 통계 인덱스
CREATE INDEX idx_route_video_analytics 
ON route_videos(view_count DESC, completion_rate DESC, like_count DESC);

-- 업로드 상태 인덱스
CREATE INDEX idx_route_video_processing 
ON route_videos(video_format, video_quality, thumbnail_url);
```

### 2. 스트리밍 최적화
```java
/**
 * 동영상 스트리밍 URL 생성 서비스
 */
@Service
public class VideoStreamingService {
    
    /**
     * 동영상 스트리밍 URL 생성
     */
    public String generateStreamingUrl(String videoId, String quality) {
        return "https://stream.routepick.com/" + videoId + "/" + quality + ".m3u8";
    }
    
    /**
     * 적응형 스트리밍 매니페스트 생성
     */
    public String generateAdaptiveStreamingManifest(Long routeId) {
        // HLS 적응형 스트리밍 매니페스트 생성
        return "https://stream.routepick.com/routes/" + routeId + "/adaptive.m3u8";
    }
    
    /**
     * 썸네일 URL 생성
     */
    public String generateThumbnailUrl(String videoId, String timestamp) {
        return "https://thumbnails.routepick.com/" + videoId + "/" + timestamp + ".jpg";
    }
}
```

### 3. 트랜스코딩 최적화
- **다중 품질 인코딩**: 480p, 720p, 1080p, 4K
- **적응형 비트레이트**: 네트워크 상황에 따른 자동 품질 조절
- **썸네일 자동 생성**: 동영상 업로드 시 자동 썸네일 추출
- **압축 최적화**: H.264/H.265 코덱 활용

## 🎯 주요 기능
- ✅ **스트리밍 최적화**: 적응형 비트레이트 스트리밍
- ✅ **화질별 관리**: 다중 품질 동영상 지원
- ✅ **재생 분석**: 완주율, 평균 시청 시간 추적
- ✅ **트랜스코딩 관리**: 동영상 처리 상태 추적
- ✅ **썸네일 관리**: 자동 썸네일 생성 및 관리
- ✅ **모바일 최적화**: 디바이스별 동영상 제공
- ✅ **업로더 추적**: 업로드 사용자 통계 관리

## 📊 동영상 분석 기능
- **조회수 추적**: 실시간 조회수 업데이트
- **재생 기록**: 시청 시간 및 완주율 분석
- **인기도 분석**: 좋아요, 공유 수 기반 랭킹
- **품질별 통계**: 화질별 사용 패턴 분석

## 🔄 동영상 처리 워크플로우
1. **업로드**: 원본 동영상 저장
2. **메타데이터 추출**: 해상도, 길이, 포맷 정보
3. **썸네일 생성**: 자동 썸네일 추출
4. **트랜스코딩**: 다중 품질 변환
5. **CDN 배포**: 스트리밍 서버 배포
6. **분석 시작**: 재생 통계 수집

---
*Step 5-3d2 완료: 루트 동영상 Repository 생성 완료*  
*다음: 남은 step5 파일 세분화 계속 진행*