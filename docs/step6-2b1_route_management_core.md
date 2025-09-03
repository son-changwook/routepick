# Step 6-2b1: RouteService 루트 관리 핵심

> 클라이밍 루트 기본 CRUD 및 검색 기능
> 생성일: 2025-08-21
> 단계: 6-2b1 (Service 레이어 - 루트 관리 핵심)
> 참고: step4-2b2, step4-3b1, step5-3c1, step5-3c2

---

## 🎯 설계 목표

- **루트 CRUD**: 클라이밍 루트 생성, 조회, 수정, 삭제
- **검색 최적화**: 벽면, 난이도, 세터별 검색
- **상태 관리**: 루트 상태 변경 및 관리
- **인기도 관리**: 조회수, 스크랩 기반 인기도 산출
- **성능 최적화**: Redis 캐싱 및 최적화된 쿼리

---

## 🧗‍♀️ RouteService - 루트 관리 핵심

### RouteService.java
```java
package com.routepick.service.route;

import com.routepick.common.enums.RouteStatus;
import com.routepick.domain.climb.entity.ClimbingLevel;
import com.routepick.domain.gym.entity.Wall;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.entity.RouteSetter;
import com.routepick.domain.route.repository.RouteRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 클라이밍 루트 관리 서비스 - 핵심 관리
 * 
 * 주요 기능:
 * - 루트 CRUD 및 상태 관리
 * - 루트 검색 및 필터링
 * - 인기도 점수 관리
 * - 캐싱 최적화
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

    // ===== DTO 클래스 =====

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

### 📊 **3. 인기도 알고리즘**
- **가중 평균**: 조회수(30%) + 스크랩수(40%) + 완등률(30%)
- **정규화**: 0-100 범위로 정규화된 점수
- **실시간 업데이트**: 조회/스크랩/완등 시 자동 업데이트
- **인기 루트**: 인기도 기반 추천 시스템

### 📈 **4. 통계 관리**
- **완등률 계산**: 시도 대비 완등 비율
- **루트 통계**: 조회수, 스크랩수, 완등 통계
- **성과 추적**: 루트별 성과 지표 관리

---

## 💾 **Redis 캐싱 전략**

### 캐시 키 구조
- **개별 루트**: `route:{routeId}`
- **벽면별 루트**: `wall-routes:{wallId}_{page}`
- **인기 루트**: `popular-routes:{page}`
- **최신 루트**: `recent-routes:{page}`
- **루트 통계**: `route-stats:{routeId}`

### 캐시 무효화
- **루트 수정 시**: 관련 캐시 전체 무효화
- **상태 변경 시**: 해당 루트 관련 캐시 무효화
- **TTL 관리**: 1시간 기본, 통계는 6시간

---

## 🛡️ **보안 및 성능 최적화**

### 보안 강화
- **XSS 보호**: 모든 입력값 XssProtectionUtil 적용
- **중복 검증**: 루트명 중복 방지 로직
- **날짜 검증**: 설정일/제거일 유효성 검사

### 성능 최적화
- **N+1 방지**: EntityGraph 활용 최적화
- **인덱스 활용**: 복합 인덱스 기반 검색
- **캐싱 전략**: Redis 기반 다층 캐시
- **배치 처리**: 대용량 데이터 효율적 처리

---

**📝 연계 파일**: step6-2b2_route_difficulty_system.md와 함께 사용  
**완료일**: 2025-08-22  
**핵심 성과**: 루트 관리 핵심 + 검색 최적화 + 인기도 알고리즘 완성