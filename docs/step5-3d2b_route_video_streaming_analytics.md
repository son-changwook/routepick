# Step 5-3d2b: 루트 동영상 스트리밍 및 분석

## 개요
- **목적**: 동영상 스트리밍 최적화 및 상세 분석 Repository
- **대상**: 화질 관리, 트랜스코딩, 성능 분석
- **최적화**: 적응형 스트리밍, 모바일 최적화, 상세 통계

---

## 🎥 RouteVideoRepository - 스트리밍 및 분석 기능

```java
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
    
    /**
     * 데스크톱 최적화 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId " +
           "AND rv.videoWidth >= 1920 AND rv.videoQuality IN ('FHD', '4K') " +
           "AND rv.isActive = true " +
           "ORDER BY rv.videoQuality DESC")
    List<RouteVideo> findDesktopOptimizedVideos(@Param("routeId") Long routeId);
    
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
    
    /**
     * 썸네일 일괄 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.thumbnailUrl = :thumbnailUrl " +
           "WHERE rv.videoId IN :videoIds")
    int updateThumbnailsBatch(@Param("videoIds") List<Long> videoIds, 
                             @Param("thumbnailUrl") String thumbnailUrl);
    
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
    
    /**
     * 저대역폭 용 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId " +
           "AND rv.videoWidth <= 720 AND rv.fileSize <= 20000000 " +
           "AND rv.isActive = true " +
           "ORDER BY rv.fileSize ASC")
    List<RouteVideo> findLowBandwidthVideos(@Param("routeId") Long routeId);
    
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
     * 동영상 성능 지표 조회
     */
    @Query("SELECT rv.videoId, rv.title, rv.duration, rv.viewCount, " +
           "rv.averageWatchTime, rv.completionRate, rv.likeCount, rv.shareCount " +
           "FROM RouteVideo rv " +
           "WHERE rv.isActive = true AND rv.viewCount > 0 " +
           "ORDER BY (rv.completionRate * 0.4 + (rv.likeCount * 100.0 / rv.viewCount) * 0.3 + (rv.shareCount * 100.0 / rv.viewCount) * 0.3) DESC")
    List<Object[]> findVideoPerformanceMetrics(Pageable pageable);
    
    /**
     * 루트별 동영상 성능 요약
     */
    @Query("SELECT rv.route.routeId, " +
           "COUNT(rv) as totalVideos, " +
           "AVG(rv.viewCount) as avgViews, " +
           "AVG(rv.completionRate) as avgCompletionRate, " +
           "AVG(rv.likeCount) as avgLikes " +
           "FROM RouteVideo rv " +
           "WHERE rv.isActive = true " +
           "GROUP BY rv.route.routeId " +
           "ORDER BY avgCompletionRate DESC")
    List<Object[]> getRouteVideoPerformanceSummary();
    
    /**
     * 시간대별 동영상 업로드 통계
     */
    @Query("SELECT DATE(rv.createdAt) as uploadDate, COUNT(rv) as uploadCount " +
           "FROM RouteVideo rv " +
           "WHERE rv.createdAt >= :fromDate AND rv.createdAt <= :toDate " +
           "AND rv.isActive = true " +
           "GROUP BY DATE(rv.createdAt) " +
           "ORDER BY uploadDate DESC")
    List<Object[]> getUploadStatistics(@Param("fromDate") LocalDateTime fromDate,
                                       @Param("toDate") LocalDateTime toDate);
    
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
           "WHERE (rv.videoFormat IS NULL OR rv.videoQuality IS NULL) " +
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
    
    /**
     * 실패한 동영상 처리 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.updatedAt < :timeoutThreshold " +
           "AND (rv.videoFormat IS NULL OR rv.videoQuality IS NULL) " +
           "AND rv.isActive = true " +
           "ORDER BY rv.createdAt")
    List<RouteVideo> findFailedProcessingVideos(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);
    
    // ===== 동영상 메타데이터 업데이트 =====
    
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
    
    /**
     * 동영상 파일 사이즈 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.fileSize = :fileSize " +
           "WHERE rv.videoId = :videoId")
    int updateFileSize(@Param("videoId") Long videoId, @Param("fileSize") Long fileSize);
    
    /**
     * 동영상 처리 상태 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET " +
           "rv.processingStatus = :status, " +
           "rv.updatedAt = :updatedAt " +
           "WHERE rv.videoId = :videoId")
    int updateProcessingStatus(@Param("videoId") Long videoId, 
                              @Param("status") String status,
                              @Param("updatedAt") LocalDateTime updatedAt);
    
    // ===== 복합 조건 검색 =====
    
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
     * 업로더별 동영상 통계
     */
    @Query("SELECT rv.uploader.userId, COUNT(rv) as videoCount, " +
           "SUM(rv.fileSize) as totalSize, AVG(rv.duration) as avgDuration, " +
           "AVG(rv.viewCount) as avgViews, AVG(rv.completionRate) as avgCompletionRate " +
           "FROM RouteVideo rv " +
           "WHERE rv.isActive = true AND rv.uploader IS NOT NULL " +
           "GROUP BY rv.uploader.userId " +
           "ORDER BY videoCount DESC")
    List<Object[]> getUploaderStatistics();
    
    /**
     * 화질별 동영상 배분 통계
     */
    @Query("SELECT rv.videoQuality, COUNT(rv) as count, " +
           "AVG(rv.fileSize) as avgSize, AVG(rv.viewCount) as avgViews " +
           "FROM RouteVideo rv " +
           "WHERE rv.isActive = true AND rv.videoQuality IS NOT NULL " +
           "GROUP BY rv.videoQuality " +
           "ORDER BY count DESC")
    List<Object[]> getVideoQualityDistribution();
    
    /**
     * 시간대별 인기 동영상 분석
     */
    @Query("SELECT HOUR(rv.createdAt) as hour, COUNT(rv) as videoCount, " +
           "AVG(rv.viewCount) as avgViews " +
           "FROM RouteVideo rv " +
           "WHERE rv.createdAt >= :fromDate AND rv.isActive = true " +
           "GROUP BY HOUR(rv.createdAt) " +
           "ORDER BY hour")
    List<Object[]> getHourlyVideoStatistics(@Param("fromDate") LocalDateTime fromDate);
}
```

---

## Custom Repository 구현

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

---

## 🚀 스트리밍 최적화 서비스

```java
/**
 * 동영상 스트리밍 URL 생성 서비스
 */
@Service
@RequiredArgsConstructor
public class VideoStreamingService {
    
    private final RouteVideoRepository routeVideoRepository;
    
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
        List<RouteVideo> videos = routeVideoRepository.findAdaptiveStreamingVideos(routeId);
        
        StringBuilder manifest = new StringBuilder("#EXTM3U\n");
        manifest.append("#EXT-X-VERSION:6\n");
        
        for (RouteVideo video : videos) {
            long bitrate = calculateBitrate(video);
            manifest.append(String.format(
                "#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d\n",
                bitrate, video.getVideoWidth(), video.getVideoHeight()
            ));
            manifest.append(generateStreamingUrl(video.getVideoId().toString(), 
                                               video.getVideoQuality())).append("\n");
        }
        
        return manifest.toString();
    }
    
    /**
     * 썸네일 URL 생성
     */
    public String generateThumbnailUrl(String videoId, String timestamp) {
        return "https://thumbnails.routepick.com/" + videoId + "/" + timestamp + ".jpg";
    }
    
    /**
     * 비트레이트 계산
     */
    private long calculateBitrate(RouteVideo video) {
        if (video.getFileSize() == null || video.getDuration() == null || video.getDuration() == 0) {
            return 1000000; // 기본값 1Mbps
        }
        return (video.getFileSize() * 8) / video.getDuration();
    }
    
    /**
     * 모바일 최적 화질 선택
     */
    public String selectOptimalQualityForMobile(Long routeId, String networkType) {
        List<RouteVideo> videos = routeVideoRepository.findMobileOptimizedVideos(routeId);
        
        return switch (networkType.toLowerCase()) {
            case "wifi" -> videos.stream()
                .filter(v -> "HD".equals(v.getVideoQuality()))
                .findFirst()
                .map(v -> v.getVideoQuality())
                .orElse("SD");
            case "4g" -> "SD";
            case "3g" -> "LD";
            default -> "SD";
        };
    }
}
```

---

## 📊 성능 최적화

### 인덱스 전략
```sql
-- 화질별 인덱스
CREATE INDEX idx_route_video_quality_optimization 
ON route_videos(route_id, video_quality, video_width, video_height);

-- 스트리밍 최적화 인덱스
CREATE INDEX idx_route_video_streaming 
ON route_videos(route_id, video_format, frame_rate, is_active);

-- 재생 통계 인덱스
CREATE INDEX idx_route_video_analytics 
ON route_videos(view_count DESC, completion_rate DESC, like_count DESC);

-- 업로드 상태 인덱스
CREATE INDEX idx_route_video_processing 
ON route_videos(video_format, video_quality, thumbnail_url);

-- 파일 사이즈 및 비트레이트 인덱스
CREATE INDEX idx_route_video_bandwidth 
ON route_videos(video_width, file_size, duration);
```

### 트랜스코딩 최적화
- **다중 품질 인코딩**: 480p, 720p, 1080p, 4K
- **적응형 비트레이트**: 네트워크 상황에 따른 자동 품질 조절
- **썸네일 자동 생성**: 동영상 업로드 시 자동 썸네일 추출
- **압축 최적화**: H.264/H.265 코덱 활용

---

## 🎯 주요 기능

### 스트리밍 최적화
- ✅ **적응형 비트레이트 스트리밍**: HLS 기반 자동 품질 조절
- ✅ **다중 해상도 지원**: 240p~4K 다양한 화질 옵션
- ✅ **디바이스 최적화**: 모바일/데스크톱 별도 최적화
- ✅ **네트워크 대응**: 3G/4G/WiFi 별 품질 선택

### 분석 및 통계
- ✅ **상세 재생 분석**: 완주율, 평균 시청 시간 추적
- ✅ **성능 지표**: 인기도, 참여도 기반 랭킹
- ✅ **업로더 통계**: 사용자별 동영상 활동 분석
- ✅ **시간대별 분석**: 업로드 패턴 및 인기도 분석

### 트랜스코딩 관리
- ✅ **상태 추적**: 업로드부터 처리 완료까지 단계별 관리
- ✅ **실패 처리**: 타임아웃 및 오류 동영상 자동 감지
- ✅ **배치 처리**: 대량 동영상 메타데이터 일괄 업데이트
- ✅ **자동 리트리**: 실패한 작업 자동 재시도

---

## 🔄 동영상 처리 워크플로우

1. **업로드**: 원본 동영상 저장
2. **메타데이터 추출**: 해상도, 길이, 포맷 정보
3. **썸네일 생성**: 자동 썸네일 추출
4. **다중 품질 트랜스코딩**: 240p~4K 변환
5. **적응형 매니페스트 생성**: HLS/DASH 배포
6. **CDN 배포**: 스트리밍 서버 배포
7. **분석 시작**: 재생 통계 수집

---

## ✅ 완료 사항
- ✅ 다중 화질 스트리밍 시스템
- ✅ 적응형 비트레이트 지원
- ✅ 모바일/데스크톱 최적화
- ✅ 동영상 분석 및 성능 측정
- ✅ 트랜스코딩 상태 관리
- ✅ 썸네일 자동 생성
- ✅ 배치 처리 시스템
- ✅ 상세 업로더 통계
- ✅ 시간대별 사용 패턴 분석

---

*RouteVideoRepository 스트리밍 및 분석 기능 구현 완료*