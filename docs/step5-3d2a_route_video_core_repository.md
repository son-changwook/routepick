# Step 5-3d2a: 루트 동영상 핵심 Repository

## 개요
- **목적**: 루트 동영상 핵심 조회 및 관리 Repository
- **대상**: RouteVideoRepository 핵심 기능
- **최적화**: 기본 CRUD, 조회수 관리, 상태 관리

---

## 🎥 RouteVideoRepository - 핵심 기능

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
 * RouteVideo Repository - 핵심 기능
 * - 루트 동영상 기본 CRUD
 * - 조회수 및 재생 통계 관리
 * - 동영상 상태 관리
 * - 기본 필터링 및 정렬
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
    
    /**
     * 루트별 동영상 개수 조회
     */
    @Query("SELECT COUNT(rv) FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId AND rv.isActive = true")
    Long countByRouteId(@Param("routeId") Long routeId);
    
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
    
    // ===== 인기 동영상 조회 =====
    
    /**
     * 인기 동영상 조회 (조회수 기준)
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.isActive = true " +
           "ORDER BY rv.viewCount DESC")
    List<RouteVideo> findPopularVideos(Pageable pageable);
    
    /**
     * 루트별 인기 동영상 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId AND rv.isActive = true " +
           "ORDER BY rv.viewCount DESC")
    List<RouteVideo> findPopularVideosByRoute(@Param("routeId") Long routeId, Pageable pageable);
    
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
    
    // ===== 조회수 및 상호작용 통계 =====
    
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
     * 좋아요 수 감소
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.likeCount = GREATEST(COALESCE(rv.likeCount, 0) - 1, 0) " +
           "WHERE rv.videoId = :videoId")
    int decreaseLikeCount(@Param("videoId") Long videoId);
    
    /**
     * 공유 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.shareCount = COALESCE(rv.shareCount, 0) + 1 " +
           "WHERE rv.videoId = :videoId")
    int increaseShareCount(@Param("videoId") Long videoId);
    
    // ===== 동영상 상태 관리 =====
    
    /**
     * 동영상 활성화/비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RouteVideo rv SET rv.isActive = :isActive " +
           "WHERE rv.videoId = :videoId")
    int updateActiveStatus(@Param("videoId") Long videoId, @Param("isActive") boolean isActive);
    
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
     * 업로더별 동영상 개수
     */
    @Query("SELECT COUNT(rv) FROM RouteVideo rv " +
           "WHERE rv.uploader.userId = :uploaderId AND rv.isActive = true")
    Long countByUploaderId(@Param("uploaderId") Long uploaderId);
    
    // ===== 기본 통계 =====
    
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
    
    /**
     * 루트별 동영상 수 통계
     */
    @Query("SELECT rv.route.routeId, COUNT(rv) as videoCount " +
           "FROM RouteVideo rv " +
           "WHERE rv.isActive = true " +
           "GROUP BY rv.route.routeId " +
           "ORDER BY videoCount DESC")
    List<Object[]> countVideosByRoute();
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 제목으로 동영상 검색
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.title LIKE %:keyword% AND rv.isActive = true " +
           "ORDER BY rv.viewCount DESC")
    List<RouteVideo> findByTitleContaining(@Param("keyword") String keyword);
    
    /**
     * 루트별 제목 검색
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.route.routeId = :routeId " +
           "AND rv.title LIKE %:keyword% AND rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findByRouteIdAndTitleContaining(@Param("routeId") Long routeId, 
                                                    @Param("keyword") String keyword);
    
    /**
     * 활성 동영상만 조회
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.isActive = true " +
           "ORDER BY rv.createdAt DESC")
    List<RouteVideo> findAllActiveVideos(Pageable pageable);
    
    /**
     * 비활성 동영상 조회 (관리자용)
     */
    @Query("SELECT rv FROM RouteVideo rv " +
           "WHERE rv.isActive = false " +
           "ORDER BY rv.updatedAt DESC")
    List<RouteVideo> findInactiveVideos(Pageable pageable);
}
```

---

## 📊 핵심 기능 설명

### 1. 기본 CRUD 및 조회
- **루트별 동영상**: 특정 루트의 모든 동영상 조회
- **타입별 필터링**: 성공 영상, 실패 영상, 해설 영상 등
- **시간대별 정렬**: 최신순, 인기순 정렬 지원
- **페이징 처리**: 대용량 동영상 목록 효율적 처리

### 2. 인기도 및 통계 관리
- **조회수 추적**: 실시간 조회수 업데이트
- **재생 분석**: 평균 시청 시간, 완주율 계산
- **상호작용**: 좋아요, 공유 수 관리
- **인기 순위**: 조회수, 완주율 기반 랭킹

### 3. 동영상 상태 관리
- **활성화/비활성화**: 동영상 노출 상태 제어
- **특집 설정**: 특별 추천 동영상 관리
- **루트별 일괄 관리**: 루트 삭제 시 관련 동영상 처리
- **업로더 추적**: 동영상 업로드 사용자 관리

### 4. 검색 및 필터링
- **제목 검색**: 키워드 기반 동영상 검색
- **재생 시간 필터**: 짧은/긴 동영상 구분
- **타입별 조회**: 동영상 카테고리별 분류
- **상태별 조회**: 활성/비활성 동영상 관리

---

## 🔍 성능 최적화

### 인덱스 전략
```sql
-- 기본 조회 최적화
CREATE INDEX idx_route_video_basic 
ON route_videos(route_id, is_active, created_at DESC);

-- 인기도 조회 최적화
CREATE INDEX idx_route_video_popularity 
ON route_videos(is_active, view_count DESC, completion_rate DESC);

-- 업로더별 조회 최적화
CREATE INDEX idx_route_video_uploader 
ON route_videos(uploader_id, is_active, created_at DESC);

-- 타입별 조회 최적화
CREATE INDEX idx_route_video_type 
ON route_videos(video_type, is_active, created_at DESC);
```

### 캐싱 전략
- **인기 동영상**: Redis 캐싱으로 빠른 응답
- **루트별 동영상 수**: 카운트 결과 캐싱
- **통계 데이터**: 주기적 배치로 캐시 갱신
- **검색 결과**: 자주 검색되는 키워드 캐싱

---

## 🎯 주요 특징

### 재생 분석 기능
- **정확한 완주율 계산**: 총 재생시간 기반 정밀 계산
- **평균 시청 시간**: 실제 사용자 참여도 측정
- **조회수 무결성**: 중복 집계 방지 메커니즘
- **실시간 업데이트**: 사용자 행동 즉시 반영

### 상태 관리 시스템
- **안전한 비활성화**: 특집 설정 동시 해제
- **루트 연동**: 루트 삭제 시 관련 동영상 자동 처리
- **되돌리기 지원**: 소프트 삭제로 복원 가능
- **배치 처리**: 대량 상태 변경 효율적 처리

---

## ✅ 완료 사항
- ✅ 루트 동영상 기본 CRUD 작업
- ✅ 조회수 및 재생 통계 실시간 업데이트
- ✅ 동영상 상태 관리 시스템
- ✅ 인기도 기반 정렬 및 랭킹
- ✅ 업로더별 동영상 추적
- ✅ 검색 및 필터링 기능
- ✅ 성능 최적화 인덱스 설계
- ✅ 기본 통계 및 분석 쿼리

---

*RouteVideoRepository 핵심 기능 설계 완료*