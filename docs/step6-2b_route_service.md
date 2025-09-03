# Step 6-2b: RouteService 구현

> 클라이밍 루트 관리 서비스 - V등급/YDS 변환, 난이도 투표, 스크랩 시스템  
> 생성일: 2025-08-21  
> 단계: 6-2b (Service 레이어 - 루트 도메인)  
> 참고: step4-2b2, step4-3b1, step5-3c1, step5-3c2, step5-3e2

---

## 🎯 설계 목표

- **등급 시스템**: V등급/YDS 등급 변환 및 관리
- **검색 최적화**: 난이도, 세터, 인기도 기반 복합 검색
- **난이도 투표**: 사용자 참여형 난이도 보정 시스템
- **스크랩 관리**: 개인화된 루트 북마크 및 목표 관리
- **성능 최적화**: Redis 캐싱 및 인기도 알고리즘

---

## 🧗‍♀️ RouteService - 클라이밍 루트 관리 서비스

### RouteService.java
```java
package com.routepick.service.route;

import com.routepick.common.enums.GradeSystem;
import com.routepick.common.enums.RouteStatus;
import com.routepick.domain.climb.entity.ClimbingLevel;
import com.routepick.domain.gym.entity.Wall;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.entity.RouteDifficultyVote;
import com.routepick.domain.route.entity.RouteScrap;
import com.routepick.domain.route.entity.RouteSetter;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.route.repository.RouteDifficultyVoteRepository;
import com.routepick.domain.route.repository.RouteScrapRepository;
import com.routepick.domain.route.repository.RouteSetterRepository;
import com.routepick.domain.climb.repository.ClimbingLevelRepository;
import com.routepick.domain.gym.repository.WallRepository;
import com.routepick.exception.route.RouteException;
import com.routepick.exception.gym.GymException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 클라이밍 루트 관리 서비스
 * 
 * 주요 기능:
 * - 루트 CRUD 및 상태 관리
 * - V등급/YDS 등급 변환 시스템
 * - 난이도 투표 및 보정 시스템
 * - 루트 스크랩 및 개인화 관리
 * - 인기도 기반 추천 알고리즘
 * - 세터별 루트 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteSetterRepository routeSetterRepository;
    private final ClimbingLevelRepository climbingLevelRepository;
    private final WallRepository wallRepository;
    private final RouteDifficultyVoteRepository routeDifficultyVoteRepository;
    private final RouteScrapRepository routeScrapRepository;
    
    @Value("${routepick.route.popularity-weight.views:0.3}")
    private double viewsWeight;
    
    @Value("${routepick.route.popularity-weight.scraps:0.4}")
    private double scrapsWeight;
    
    @Value("${routepick.route.popularity-weight.completion-rate:0.3}")
    private double completionRateWeight;

    // ===== 루트 기본 관리 =====

    /**
     * 루트 생성
     */
    @Transactional
    @CacheEvict(value = {"routes", "popular-routes"}, allEntries = true)
    public Route createRoute(Long wallId, Long setterId, Long levelId, String routeName,
                           String description, String holdColor, String tapeColor,
                           LocalDate setDate, LocalDate removeDate) {
        
        // 관련 엔티티 검증
        Wall wall = wallRepository.findByIdAndDeletedFalse(wallId)
            .orElseThrow(() -> GymException.wallNotFound(wallId));
            
        RouteSetter setter = routeSetterRepository.findByIdAndDeletedFalse(setterId)
            .orElseThrow(() -> RouteException.setterNotFound(setterId));
            
        ClimbingLevel level = climbingLevelRepository.findById(levelId)
            .orElseThrow(() -> RouteException.levelNotFound(levelId));
        
        // XSS 보호
        routeName = XssProtectionUtil.cleanInput(routeName);
        description = XssProtectionUtil.cleanInput(description);
        holdColor = XssProtectionUtil.cleanInput(holdColor);
        tapeColor = XssProtectionUtil.cleanInput(tapeColor);
        
        // 동일 벽면 내 루트명 중복 검증
        if (routeRepository.existsByWallIdAndRouteNameAndDeletedFalse(wallId, routeName)) {
            throw RouteException.routeAlreadyExists(wallId, routeName);
        }
        
        // 제거일 검증
        if (removeDate != null && removeDate.isBefore(setDate)) {
            throw RouteException.invalidRemoveDate(setDate, removeDate);
        }
        
        Route route = Route.builder()
            .wall(wall)
            .setter(setter)
            .level(level)
            .routeName(routeName)
            .description(description)
            .holdColor(holdColor)
            .tapeColor(tapeColor)
            .setDate(setDate)
            .removeDate(removeDate)
            .status(RouteStatus.ACTIVE)
            .viewCount(0L)
            .scrapCount(0L)
            .completionCount(0L)
            .attemptCount(0L)
            .averageVoteScore(BigDecimal.ZERO)
            .popularityScore(BigDecimal.ZERO)
            .build();
            
        Route savedRoute = routeRepository.save(route);
        
        log.info("루트 생성 완료 - routeId: {}, name: {}, level: {}", 
                savedRoute.getId(), savedRoute.getRouteName(), level.getGradeName());
        return savedRoute;
    }

    /**
     * 루트 상세 조회 (조회수 증가)
     */
    @Transactional
    public Route getRouteById(Long routeId) {
        Route route = routeRepository.findByIdWithDetailsAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
            
        // 조회수 증가
        route.incrementViewCount();
        
        // 인기도 점수 재계산
        updatePopularityScore(route);
        
        return route;
    }

    /**
     * 루트 정보 수정
     */
    @Transactional
    @CacheEvict(value = {"route", "routes"}, key = "#routeId")
    public Route updateRoute(Long routeId, String routeName, String description,
                           String holdColor, String tapeColor, LocalDate removeDate) {
        
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // XSS 보호 및 업데이트
        if (StringUtils.hasText(routeName)) {
            routeName = XssProtectionUtil.cleanInput(routeName);
            
            // 동일 벽면 내 루트명 중복 검증
            if (!route.getRouteName().equals(routeName) &&
                routeRepository.existsByWallIdAndRouteNameAndDeletedFalse(
                    route.getWall().getId(), routeName)) {
                throw RouteException.routeAlreadyExists(route.getWall().getId(), routeName);
            }
            route.updateRouteName(routeName);
        }
        
        if (StringUtils.hasText(description)) {
            route.updateDescription(XssProtectionUtil.cleanInput(description));
        }
        
        if (StringUtils.hasText(holdColor)) {
            route.updateHoldColor(XssProtectionUtil.cleanInput(holdColor));
        }
        
        if (StringUtils.hasText(tapeColor)) {
            route.updateTapeColor(XssProtectionUtil.cleanInput(tapeColor));
        }
        
        if (removeDate != null) {
            if (removeDate.isBefore(route.getSetDate())) {
                throw RouteException.invalidRemoveDate(route.getSetDate(), removeDate);
            }
            route.updateRemoveDate(removeDate);
        }
        
        log.info("루트 정보 수정 완료 - routeId: {}", routeId);
        return route;
    }

    /**
     * 루트 상태 변경
     */
    @Transactional
    @CacheEvict(value = {"route", "routes"}, key = "#routeId")
    public void updateRouteStatus(Long routeId, RouteStatus status) {
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
            
        route.updateStatus(status);
        
        log.info("루트 상태 변경 - routeId: {}, status: {}", routeId, status);
    }

    /**
     * 루트 소프트 삭제
     */
    @Transactional
    @CacheEvict(value = {"route", "routes", "popular-routes"}, allEntries = true)
    public void deleteRoute(Long routeId) {
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
            
        route.markAsDeleted();
        
        log.info("루트 삭제 완료 - routeId: {}", routeId);
    }

    // ===== 루트 검색 및 필터링 =====

    /**
     * 벽면별 루트 목록 조회
     */
    @Cacheable(value = "wall-routes", key = "#wallId + '_' + #pageable.pageNumber")
    public Page<Route> getRoutesByWall(Long wallId, Pageable pageable) {
        // 벽면 존재 검증
        wallRepository.findByIdAndDeletedFalse(wallId)
            .orElseThrow(() -> GymException.wallNotFound(wallId));
            
        return routeRepository.findByWallIdAndDeletedFalseOrderBySetDateDesc(wallId, pageable);
    }

    /**
     * 난이도별 루트 검색
     */
    @Cacheable(value = "routes-by-level", 
               key = "#levelId + '_' + #pageable.pageNumber")
    public Page<Route> getRoutesByLevel(Long levelId, Pageable pageable) {
        // 레벨 존재 검증
        climbingLevelRepository.findById(levelId)
            .orElseThrow(() -> RouteException.levelNotFound(levelId));
            
        return routeRepository.findByLevelIdAndDeletedFalseOrderByPopularityScoreDesc(
            levelId, pageable);
    }

    /**
     * 세터별 루트 목록
     */
    @Cacheable(value = "setter-routes", 
               key = "#setterId + '_' + #pageable.pageNumber")
    public Page<Route> getRoutesBySetter(Long setterId, Pageable pageable) {
        // 세터 존재 검증
        routeSetterRepository.findByIdAndDeletedFalse(setterId)
            .orElseThrow(() -> RouteException.setterNotFound(setterId));
            
        return routeRepository.findBySetterIdAndDeletedFalseOrderBySetDateDesc(
            setterId, pageable);
    }

    /**
     * 인기 루트 목록 (캐싱)
     */
    @Cacheable(value = "popular-routes", key = "#pageable.pageNumber")
    public Page<Route> getPopularRoutes(Pageable pageable) {
        return routeRepository.findActiveRoutesOrderByPopularityDesc(pageable);
    }

    /**
     * 최신 루트 목록
     */
    @Cacheable(value = "recent-routes", key = "#pageable.pageNumber")
    public Page<Route> getRecentRoutes(Pageable pageable) {
        return routeRepository.findByDeletedFalseAndStatusOrderBySetDateDesc(
            RouteStatus.ACTIVE, pageable);
    }

    /**
     * 복합 조건 루트 검색
     */
    public Page<Route> searchRoutes(Long branchId, List<Long> levelIds, 
                                  List<Long> setterIds, String keyword,
                                  LocalDate startDate, LocalDate endDate,
                                  Pageable pageable) {
        
        // XSS 보호
        if (StringUtils.hasText(keyword)) {
            keyword = XssProtectionUtil.cleanInput(keyword);
        }
        
        return routeRepository.findRoutesByComplexConditions(
            branchId, levelIds, setterIds, keyword, startDate, endDate, pageable);
    }

    // ===== 난이도 투표 시스템 =====

    /**
     * 난이도 투표 등록/수정
     */
    @Transactional
    @CacheEvict(value = {"route", "routes"}, key = "#routeId")
    public RouteDifficultyVote voteRouteDifficulty(Long routeId, Long userId, 
                                                 Integer voteScore, String comment) {
        
        // 루트 존재 검증
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 투표 점수 검증 (1-10)
        if (voteScore < 1 || voteScore > 10) {
            throw RouteException.invalidVoteScore(voteScore);
        }
        
        // XSS 보호
        if (StringUtils.hasText(comment)) {
            comment = XssProtectionUtil.cleanInput(comment);
        }
        
        // 기존 투표 확인
        Optional<RouteDifficultyVote> existingVote = 
            routeDifficultyVoteRepository.findByRouteIdAndUserIdAndDeletedFalse(routeId, userId);
        
        RouteDifficultyVote vote;
        if (existingVote.isPresent()) {
            // 기존 투표 수정
            vote = existingVote.get();
            vote.updateVote(voteScore, comment);
            log.info("난이도 투표 수정 - routeId: {}, userId: {}, score: {}", 
                    routeId, userId, voteScore);
        } else {
            // 새 투표 등록
            vote = RouteDifficultyVote.builder()
                .route(route)
                .userId(userId)
                .voteScore(voteScore)
                .comment(comment)
                .build();
            vote = routeDifficultyVoteRepository.save(vote);
            log.info("난이도 투표 등록 - routeId: {}, userId: {}, score: {}", 
                    routeId, userId, voteScore);
        }
        
        // 루트 평균 투표 점수 업데이트
        updateRouteAverageVoteScore(routeId);
        
        return vote;
    }

    /**
     * 루트 평균 투표 점수 업데이트
     */
    @Transactional
    protected void updateRouteAverageVoteScore(Long routeId) {
        BigDecimal averageScore = routeDifficultyVoteRepository
            .calculateWeightedAverageScore(routeId);
            
        if (averageScore != null) {
            routeRepository.updateAverageVoteScore(routeId, averageScore);
            log.debug("루트 평균 투표 점수 업데이트 - routeId: {}, average: {}", 
                     routeId, averageScore);
        }
    }

    /**
     * 루트 투표 통계 조회
     */
    @Cacheable(value = "route-vote-stats", key = "#routeId")
    public RouteVoteStatsDto getRouteVoteStats(Long routeId) {
        Map<String, Object> stats = routeDifficultyVoteRepository
            .getRouteVoteStatistics(routeId);
            
        return RouteVoteStatsDto.builder()
            .routeId(routeId)
            .totalVotes(((Number) stats.get("totalVotes")).longValue())
            .averageScore((BigDecimal) stats.get("averageScore"))
            .scoreDistribution((Map<Integer, Long>) stats.get("scoreDistribution"))
            .build();
    }

    // ===== 루트 스크랩 시스템 =====

    /**
     * 루트 스크랩 추가/제거 토글
     */
    @Transactional
    @CacheEvict(value = {"user-scraps", "route"}, allEntries = true)
    public boolean toggleRouteScrap(Long routeId, Long userId, String memo, 
                                  LocalDate targetDate, String folder) {
        
        // 루트 존재 검증
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 기존 스크랩 확인
        Optional<RouteScrap> existingScrap = 
            routeScrapRepository.findByRouteIdAndUserIdAndDeletedFalse(routeId, userId);
        
        if (existingScrap.isPresent()) {
            // 스크랩 제거
            RouteScrap scrap = existingScrap.get();
            scrap.markAsDeleted();
            
            // 루트 스크랩 수 감소
            route.decrementScrapCount();
            
            log.info("루트 스크랩 제거 - routeId: {}, userId: {}", routeId, userId);
            return false;
        } else {
            // XSS 보호
            if (StringUtils.hasText(memo)) {
                memo = XssProtectionUtil.cleanInput(memo);
            }
            if (StringUtils.hasText(folder)) {
                folder = XssProtectionUtil.cleanInput(folder);
            }
            
            // 스크랩 추가
            RouteScrap scrap = RouteScrap.builder()
                .route(route)
                .userId(userId)
                .memo(memo)
                .targetDate(targetDate)
                .folder(folder)
                .build();
            routeScrapRepository.save(scrap);
            
            // 루트 스크랩 수 증가
            route.incrementScrapCount();
            
            log.info("루트 스크랩 추가 - routeId: {}, userId: {}", routeId, userId);
            return true;
        }
    }

    /**
     * 사용자의 스크랩 루트 목록
     */
    @Cacheable(value = "user-scraps", key = "#userId + '_' + #folder + '_' + #pageable.pageNumber")
    public Page<RouteScrap> getUserScrapRoutes(Long userId, String folder, Pageable pageable) {
        if (StringUtils.hasText(folder)) {
            return routeScrapRepository.findByUserIdAndFolderAndDeletedFalseOrderByCreatedAtDesc(
                userId, folder, pageable);
        } else {
            return routeScrapRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(
                userId, pageable);
        }
    }

    /**
     * 스크랩 정보 수정
     */
    @Transactional
    @CacheEvict(value = "user-scraps", allEntries = true)
    public RouteScrap updateScrapInfo(Long scrapId, String memo, 
                                    LocalDate targetDate, String folder) {
        
        RouteScrap scrap = routeScrapRepository.findByIdAndDeletedFalse(scrapId)
            .orElseThrow(() -> RouteException.scrapNotFound(scrapId));
        
        // XSS 보호 및 업데이트
        if (memo != null) {
            scrap.updateMemo(XssProtectionUtil.cleanInput(memo));
        }
        
        if (targetDate != null) {
            scrap.updateTargetDate(targetDate);
        }
        
        if (folder != null) {
            scrap.updateFolder(XssProtectionUtil.cleanInput(folder));
        }
        
        log.info("스크랩 정보 수정 - scrapId: {}", scrapId);
        return scrap;
    }

    // ===== 인기도 및 통계 관리 =====

    /**
     * 루트 인기도 점수 업데이트
     */
    @Transactional
    protected void updatePopularityScore(Route route) {
        // 완등률 계산 (시도 대비 완등)
        double completionRate = route.getAttemptCount() > 0 ? 
            (double) route.getCompletionCount() / route.getAttemptCount() : 0.0;
        
        // 정규화된 점수 계산 (0-100)
        double normalizedViews = Math.min(route.getViewCount() / 1000.0, 100.0);
        double normalizedScraps = Math.min(route.getScrapCount() / 100.0, 100.0);
        double normalizedCompletionRate = completionRate * 100.0;
        
        // 가중 평균으로 인기도 점수 계산
        double popularityScore = (normalizedViews * viewsWeight) +
                                (normalizedScraps * scrapsWeight) +
                                (normalizedCompletionRate * completionRateWeight);
        
        route.updatePopularityScore(BigDecimal.valueOf(popularityScore)
            .setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 루트 완등 기록
     */
    @Transactional
    @CacheEvict(value = {"route", "popular-routes"}, allEntries = true)
    public void recordRouteCompletion(Long routeId, Long userId, boolean completed) {
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 시도 횟수 증가
        route.incrementAttemptCount();
        
        // 완등 시 완등 횟수 증가
        if (completed) {
            route.incrementCompletionCount();
        }
        
        // 인기도 점수 재계산
        updatePopularityScore(route);
        
        log.info("루트 완등 기록 - routeId: {}, userId: {}, completed: {}", 
                routeId, userId, completed);
    }

    /**
     * 루트 통계 조회
     */
    @Cacheable(value = "route-stats", key = "#routeId")
    public RouteStatsDto getRouteStats(Long routeId) {
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 완등률 계산
        double completionRate = route.getAttemptCount() > 0 ? 
            (double) route.getCompletionCount() / route.getAttemptCount() * 100.0 : 0.0;
        
        return RouteStatsDto.builder()
            .routeId(routeId)
            .routeName(route.getRouteName())
            .viewCount(route.getViewCount())
            .scrapCount(route.getScrapCount())
            .completionCount(route.getCompletionCount())
            .attemptCount(route.getAttemptCount())
            .completionRate(BigDecimal.valueOf(completionRate).setScale(1, RoundingMode.HALF_UP))
            .averageVoteScore(route.getAverageVoteScore())
            .popularityScore(route.getPopularityScore())
            .build();
    }

    // ===== V등급/YDS 등급 변환 =====

    /**
     * V등급을 YDS 등급으로 변환
     */
    public String convertVGradeToYds(String vGrade) {
        // V등급 → YDS 변환 로직
        Map<String, String> vToYdsMap = Map.of(
            "V0", "5.10a", "V1", "5.10b", "V2", "5.10c", "V3", "5.10d",
            "V4", "5.11a", "V5", "5.11b", "V6", "5.11c", "V7", "5.11d",
            "V8", "5.12a", "V9", "5.12b", "V10", "5.12c", "V11", "5.12d",
            "V12", "5.13a", "V13", "5.13b", "V14", "5.13c", "V15", "5.13d"
        );
        
        return vToYdsMap.getOrDefault(vGrade, "Unknown");
    }

    /**
     * YDS 등급을 V등급으로 변환
     */
    public String convertYdsToVGrade(String ydsGrade) {
        // YDS → V등급 변환 로직
        Map<String, String> ydsToVMap = Map.of(
            "5.10a", "V0", "5.10b", "V1", "5.10c", "V2", "5.10d", "V3",
            "5.11a", "V4", "5.11b", "V5", "5.11c", "V6", "5.11d", "V7",
            "5.12a", "V8", "5.12b", "V9", "5.12c", "V10", "5.12d", "V11",
            "5.13a", "V12", "5.13b", "V13", "5.13c", "V14", "5.13d", "V15"
        );
        
        return ydsToVMap.getOrDefault(ydsGrade, "Unknown");
    }

    // ===== DTO 클래스 =====

    /**
     * 루트 투표 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class RouteVoteStatsDto {
        private final Long routeId;
        private final Long totalVotes;
        private final BigDecimal averageScore;
        private final Map<Integer, Long> scoreDistribution;
    }

    /**
     * 루트 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class RouteStatsDto {
        private final Long routeId;
        private final String routeName;
        private final Long viewCount;
        private final Long scrapCount;
        private final Long completionCount;
        private final Long attemptCount;
        private final BigDecimal completionRate;
        private final BigDecimal averageVoteScore;
        private final BigDecimal popularityScore;
    }
}
```

---

## 📋 주요 기능 설명

### 🧗‍♀️ **1. 루트 기본 관리**
- **생성/수정/삭제**: 루트 정보 CRUD 관리
- **상태 관리**: RouteStatus 기반 상태 변경
- **중복 검증**: 동일 벽면 내 루트명 중복 방지
- **조회수 관리**: 상세 조회 시 자동 조회수 증가

### 🔍 **2. 검색 및 필터링**
- **벽면별 검색**: 특정 벽면의 루트 목록
- **난이도별 검색**: 클라이밍 레벨 기반 필터링
- **세터별 검색**: 루트 세터 기반 검색
- **복합 조건 검색**: 다중 조건 조합 검색
- **인기/최신 순**: 정렬 옵션 제공

### 🗳️ **3. 난이도 투표 시스템**
- **투표 등록/수정**: 1-10점 난이도 투표
- **가중 평균**: 투표 신뢰도 기반 평균 계산
- **투표 통계**: 점수 분포 및 통계 제공
- **중복 방지**: 사용자당 루트별 1회 투표

### 📌 **4. 스크랩 시스템**
- **토글 방식**: 스크랩 추가/제거 토글
- **개인화 관리**: 메모, 목표일, 폴더 분류
- **스크랩 목록**: 사용자별 스크랩 루트 관리
- **스크랩 수 관리**: 실시간 스크랩 수 업데이트

### 📊 **5. 인기도 알고리즘**
- **가중 평균**: 조회수(30%) + 스크랩수(40%) + 완등률(30%)
- **정규화**: 0-100 범위로 정규화된 점수
- **실시간 업데이트**: 조회/스크랩/완등 시 자동 업데이트
- **인기 루트**: 인기도 기반 추천 시스템

### 🔄 **6. 등급 변환 시스템**
- **V등급 ↔ YDS**: 볼더링 등급 상호 변환
- **표준 매핑**: 국제 표준 등급 매핑 테이블
- **확장 가능**: 추후 다른 등급 시스템 추가 가능

---

## 💾 **Redis 캐싱 전략**

### 캐시 키 구조
- **개별 루트**: `route:{routeId}`
- **벽면별 루트**: `wall-routes:{wallId}_{page}`
- **인기 루트**: `popular-routes:{page}`
- **사용자 스크랩**: `user-scraps:{userId}_{folder}_{page}`
- **루트 통계**: `route-stats:{routeId}`

### 캐시 무효화
- **루트 수정 시**: 관련 캐시 전체 무효화
- **투표/스크랩 시**: 해당 루트 관련 캐시 무효화
- **TTL 관리**: 1시간 기본, 통계는 6시간

---

## 🛡️ **보안 및 성능 최적화**

### 보안 강화
- **XSS 보호**: 모든 입력값 XssProtectionUtil 적용
- **입력 검증**: 투표 점수, 날짜 범위 등 검증
- **권한 검증**: 루트 수정/삭제 권한 확인

### 성능 최적화
- **N+1 방지**: EntityGraph 활용 최적화
- **인덱스 활용**: 복합 인덱스 기반 검색
- **배치 처리**: 대용량 데이터 효율적 처리
- **통계 캐싱**: 자주 조회되는 통계 정보 캐싱

---

## 🚀 **다음 단계**

**Phase 3 완료 후 진행할 작업:**
- **step6-2c_route_media_service.md**: 루트 미디어 관리 서비스
- **step6-2d_climbing_record_service.md**: 클라이밍 기록 서비스

*step6-2b 완성: 루트 도메인 완전 설계 완료*