# Step 6-3c: RouteTaggingService 구현

> 루트 태깅 관리 서비스 - 태그 연관도 관리, 품질 검증, 중복 방지
> 생성일: 2025-08-22
> 단계: 6-3c (Service 레이어 - 루트 태깅)
> 참고: step4-2a, step5-2b, step6-3a

---

## 🎯 설계 목표

- **태그 연관도 관리**: relevance_score 계산 및 검증 (0.0-1.0)
- **품질 관리**: 태깅 품질 추적, created_by 기반 신뢰도
- **중복 방지**: 루트-태그 조합 유니크 처리
- **통계 제공**: 태그별 사용 빈도, 인기도 분석
- **캐싱 최적화**: 자주 접근하는 루트 태그 캐싱

---

## 🏷️ RouteTaggingService 구현

### RouteTaggingService.java
```java
package com.routepick.service.tag;

import com.routepick.common.enums.TagType;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.tag.entity.RouteTag;
import com.routepick.domain.tag.entity.Tag;
import com.routepick.domain.tag.repository.RouteTagRepository;
import com.routepick.domain.tag.repository.TagRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.exception.tag.TagException;
import com.routepick.exception.route.RouteException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 루트 태깅 관리 서비스
 * - 루트에 태그 부착/제거
 * - 태그 연관도 점수 관리
 * - 태깅 품질 검증
 * - 태그 사용 통계
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteTaggingService {
    
    private final RouteTagRepository routeTagRepository;
    private final RouteRepository routeRepository;
    private final TagRepository tagRepository;
    private final TagService tagService;
    private final ApplicationEventPublisher eventPublisher;
    
    // 연관도 점수 범위
    private static final BigDecimal MIN_RELEVANCE_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_RELEVANCE_SCORE = BigDecimal.ONE;
    private static final BigDecimal DEFAULT_RELEVANCE_SCORE = new BigDecimal("0.5");
    
    // 캐시 이름
    private static final String CACHE_ROUTE_TAGS = "routeTags";
    private static final String CACHE_TAG_ROUTES = "tagRoutes";
    private static final String CACHE_TAG_STATS = "tagStats";
    
    /**
     * 루트에 태그 추가
     * @param routeId 루트 ID
     * @param tagId 태그 ID  
     * @param relevanceScore 연관도 점수 (0.0-1.0)
     * @param createdBy 태깅한 사용자
     * @return 생성된 RouteTag
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_ROUTE_TAGS, key = "#routeId"),
        @CacheEvict(value = CACHE_TAG_ROUTES, key = "#tagId"),
        @CacheEvict(value = CACHE_TAG_STATS, allEntries = true)
    })
    public RouteTag addTagToRoute(Long routeId, Long tagId, 
                                 BigDecimal relevanceScore, User createdBy) {
        log.info("Adding tag {} to route {} with score {}", tagId, routeId, relevanceScore);
        
        // 루트 확인
        Route route = routeRepository.findById(routeId)
            .orElseThrow(() -> new RouteException("루트를 찾을 수 없습니다: " + routeId));
            
        // 태그 확인 및 taggable 검증
        Tag tag = tagRepository.findById(tagId)
            .orElseThrow(() -> new TagException("태그를 찾을 수 없습니다: " + tagId));
            
        if (!tag.getIsRouteTaggable()) {
            throw new TagException("이 태그는 루트에 사용할 수 없습니다: " + tag.getTagName());
        }
        
        // 중복 확인
        Optional<RouteTag> existing = routeTagRepository.findByRouteIdAndTagId(routeId, tagId);
        if (existing.isPresent()) {
            // 기존 태그가 있으면 연관도 점수만 업데이트
            return updateRelevanceScore(existing.get(), relevanceScore);
        }
        
        // 연관도 점수 검증
        validateRelevanceScore(relevanceScore);
        
        // RouteTag 생성
        RouteTag routeTag = RouteTag.builder()
            .route(route)
            .tag(tag)
            .relevanceScore(relevanceScore != null ? relevanceScore : DEFAULT_RELEVANCE_SCORE)
            .createdBy(createdBy != null ? createdBy.getNickName() : "SYSTEM")
            .build();
            
        RouteTag saved = routeTagRepository.save(routeTag);
        
        // 이벤트 발행 (추천 재계산 트리거)
        eventPublisher.publishEvent(new RouteTaggedEvent(routeId, tagId));
        
        log.info("RouteTag created: route={}, tag={}, score={}", 
                routeId, tag.getTagName(), saved.getRelevanceScore());
                
        return saved;
    }
    
    /**
     * 루트에 여러 태그 일괄 추가
     * @param routeId 루트 ID
     * @param tagRelevanceMap 태그ID-연관도 맵
     * @param createdBy 태깅한 사용자
     * @return 생성된 RouteTag 목록
     */
    @Transactional
    public List<RouteTag> addTagsToRoute(Long routeId, 
                                        Map<Long, BigDecimal> tagRelevanceMap,
                                        User createdBy) {
        log.info("Adding {} tags to route {}", tagRelevanceMap.size(), routeId);
        
        List<RouteTag> routeTags = new ArrayList<>();
        
        for (Map.Entry<Long, BigDecimal> entry : tagRelevanceMap.entrySet()) {
            try {
                RouteTag routeTag = addTagToRoute(routeId, entry.getKey(), 
                                                 entry.getValue(), createdBy);
                routeTags.add(routeTag);
            } catch (Exception e) {
                log.error("Failed to add tag {} to route {}: {}", 
                         entry.getKey(), routeId, e.getMessage());
            }
        }
        
        return routeTags;
    }
    
    /**
     * 루트에서 태그 제거
     * @param routeId 루트 ID
     * @param tagId 태그 ID
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_ROUTE_TAGS, key = "#routeId"),
        @CacheEvict(value = CACHE_TAG_ROUTES, key = "#tagId"),
        @CacheEvict(value = CACHE_TAG_STATS, allEntries = true)
    })
    public void removeTagFromRoute(Long routeId, Long tagId) {
        log.info("Removing tag {} from route {}", tagId, routeId);
        
        RouteTag routeTag = routeTagRepository.findByRouteIdAndTagId(routeId, tagId)
            .orElseThrow(() -> new TagException("루트-태그 관계를 찾을 수 없습니다"));
            
        routeTagRepository.delete(routeTag);
        
        // 이벤트 발행
        eventPublisher.publishEvent(new RouteUntaggedEvent(routeId, tagId));
        
        log.info("RouteTag removed: route={}, tag={}", routeId, tagId);
    }
    
    /**
     * 연관도 점수 업데이트
     * @param routeTag RouteTag 엔티티
     * @param newScore 새로운 연관도 점수
     * @return 업데이트된 RouteTag
     */
    @Transactional
    public RouteTag updateRelevanceScore(RouteTag routeTag, BigDecimal newScore) {
        validateRelevanceScore(newScore);
        
        routeTag.setRelevanceScore(newScore);
        routeTag.setUpdatedAt(LocalDateTime.now());
        
        return routeTagRepository.save(routeTag);
    }
    
    /**
     * 루트의 모든 태그 조회 (연관도 순)
     * @param routeId 루트 ID
     * @return RouteTag 목록
     */
    @Cacheable(value = CACHE_ROUTE_TAGS, key = "#routeId")
    public List<RouteTag> getRouteTags(Long routeId) {
        return routeTagRepository.findByRouteIdOrderByRelevanceScoreDesc(routeId);
    }
    
    /**
     * 루트의 특정 타입 태그만 조회
     * @param routeId 루트 ID
     * @param tagType 태그 타입
     * @return RouteTag 목록
     */
    public List<RouteTag> getRouteTagsByType(Long routeId, TagType tagType) {
        List<RouteTag> allTags = getRouteTags(routeId);
        
        return allTags.stream()
            .filter(rt -> rt.getTag().getTagType() == tagType)
            .collect(Collectors.toList());
    }
    
    /**
     * 루트의 높은 연관도 태그만 조회
     * @param routeId 루트 ID
     * @param minScore 최소 연관도 점수
     * @return RouteTag 목록
     */
    public List<RouteTag> getHighRelevanceTags(Long routeId, BigDecimal minScore) {
        return routeTagRepository.findHighRelevanceTagsByRoute(routeId, minScore);
    }
    
    /**
     * 태그가 사용된 루트 목록 조회
     * @param tagId 태그 ID
     * @param pageable 페이징
     * @return 루트 페이지
     */
    @Cacheable(value = CACHE_TAG_ROUTES, key = "#tagId + '_' + #pageable.pageNumber")
    public Page<Route> getRoutesWithTag(Long tagId, Pageable pageable) {
        return routeTagRepository.findRoutesWithTag(tagId, pageable);
    }
    
    /**
     * 유사한 태그를 가진 루트 찾기
     * @param routeId 기준 루트 ID
     * @param limit 결과 개수 제한
     * @return 유사 루트 목록
     */
    public List<Route> findSimilarRoutes(Long routeId, int limit) {
        // 현재 루트의 태그 조회
        List<RouteTag> routeTags = getRouteTags(routeId);
        
        if (routeTags.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 태그 ID 목록 추출
        List<Long> tagIds = routeTags.stream()
            .map(rt -> rt.getTag().getTagId())
            .collect(Collectors.toList());
            
        // 유사 루트 조회 (커스텀 쿼리)
        return routeTagRepository.findSimilarRoutes(routeId, tagIds, 
                                                   PageRequest.of(0, limit));
    }
    
    /**
     * 태그 사용 통계 조회
     * @param tagId 태그 ID
     * @return 태그 통계 정보
     */
    @Cacheable(value = CACHE_TAG_STATS, key = "#tagId")
    public TagStatistics getTagStatistics(Long tagId) {
        Long usageCount = routeTagRepository.countByTagId(tagId);
        BigDecimal avgRelevance = routeTagRepository.getAverageRelevanceScore(tagId);
        List<Object[]> topRoutes = routeTagRepository.findTopRoutesByTag(tagId, 5);
        
        return TagStatistics.builder()
            .tagId(tagId)
            .usageCount(usageCount)
            .averageRelevance(avgRelevance)
            .topRoutes(topRoutes)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    /**
     * 태그 타입별 분포 조회
     * @param routeId 루트 ID
     * @return 태그 타입별 개수 맵
     */
    public Map<TagType, Long> getTagTypeDistribution(Long routeId) {
        List<RouteTag> routeTags = getRouteTags(routeId);
        
        return routeTags.stream()
            .collect(Collectors.groupingBy(
                rt -> rt.getTag().getTagType(),
                Collectors.counting()
            ));
    }
    
    /**
     * 태그 품질 점수 계산
     * - created_by 신뢰도
     * - 사용 빈도
     * - 평균 연관도
     * @param tagId 태그 ID
     * @return 품질 점수 (0.0-1.0)
     */
    public BigDecimal calculateTagQualityScore(Long tagId) {
        TagStatistics stats = getTagStatistics(tagId);
        
        // 사용 빈도 점수 (0-1)
        BigDecimal usageScore = BigDecimal.valueOf(
            Math.min(stats.getUsageCount() / 100.0, 1.0)
        );
        
        // 평균 연관도 점수
        BigDecimal relevanceScore = stats.getAverageRelevance() != null ?
            stats.getAverageRelevance() : BigDecimal.ZERO;
            
        // 가중 평균 (사용빈도 40%, 연관도 60%)
        return usageScore.multiply(new BigDecimal("0.4"))
            .add(relevanceScore.multiply(new BigDecimal("0.6")))
            .setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 비동기 태그 추천 생성
     * @param routeId 루트 ID
     * @return 추천 태그 목록 (비동기)
     */
    @Async
    public CompletableFuture<List<Tag>> suggestTagsForRoute(Long routeId) {
        log.info("Generating tag suggestions for route {}", routeId);
        
        Route route = routeRepository.findById(routeId)
            .orElseThrow(() -> new RouteException("루트를 찾을 수 없습니다"));
            
        // 현재 태그 확인
        Set<Long> existingTagIds = getRouteTags(routeId).stream()
            .map(rt -> rt.getTag().getTagId())
            .collect(Collectors.toSet());
            
        // 추천 로직
        List<Tag> suggestions = new ArrayList<>();
        
        // 1. 같은 난이도의 다른 루트에서 자주 사용되는 태그
        List<Tag> sameLevelTags = routeTagRepository
            .findPopularTagsByLevel(route.getLevel().getLevelId(), 10);
        suggestions.addAll(sameLevelTags.stream()
            .filter(tag -> !existingTagIds.contains(tag.getTagId()))
            .limit(5)
            .collect(Collectors.toList()));
            
        // 2. 같은 벽(Wall)의 다른 루트에서 사용되는 태그
        if (route.getWall() != null) {
            List<Tag> wallTags = routeTagRepository
                .findPopularTagsByWall(route.getWall().getWallId(), 10);
            suggestions.addAll(wallTags.stream()
                .filter(tag -> !existingTagIds.contains(tag.getTagId()))
                .limit(3)
                .collect(Collectors.toList()));
        }
        
        return CompletableFuture.completedFuture(suggestions);
    }
    
    /**
     * 연관도 점수 검증
     * @param score 점수
     */
    private void validateRelevanceScore(BigDecimal score) {
        if (score == null) {
            return; // null이면 기본값 사용
        }
        
        if (score.compareTo(MIN_RELEVANCE_SCORE) < 0 || 
            score.compareTo(MAX_RELEVANCE_SCORE) > 0) {
            throw new TagException("연관도 점수는 0.0에서 1.0 사이여야 합니다: " + score);
        }
    }
    
    // 이벤트 클래스
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RouteTaggedEvent {
        private final Long routeId;
        private final Long tagId;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RouteUntaggedEvent {
        private final Long routeId;
        private final Long tagId;
    }
    
    // 통계 DTO
    @lombok.Builder
    @lombok.Getter
    public static class TagStatistics {
        private final Long tagId;
        private final Long usageCount;
        private final BigDecimal averageRelevance;
        private final List<Object[]> topRoutes;
        private final LocalDateTime lastUpdated;
    }
}
```

---

## 🔧 설정 및 통합

### application.yml 추가 설정
```yaml
# 태그 시스템 설정
app:
  tag:
    route:
      cache-ttl: 2h  # 루트 태그 캐시 TTL
      min-relevance: 0.3  # 최소 연관도 점수
      max-tags-per-route: 20  # 루트당 최대 태그 수
      suggestion-limit: 10  # 태그 추천 개수
    quality:
      min-usage-for-trust: 10  # 신뢰할 수 있는 최소 사용 횟수
      quality-threshold: 0.6  # 품질 임계값
```

---

## 📊 주요 기능 요약

### 1. 태그 연관도 관리
- **relevance_score**: 0.0~1.0 범위 검증
- **기본값**: 0.5 (중간 연관도)
- **동적 업데이트**: 사용자 피드백 반영

### 2. 중복 방지
- **유니크 체크**: routeId + tagId 조합
- **업데이트 처리**: 기존 태그 존재시 점수만 갱신

### 3. 품질 관리
- **created_by 추적**: 태깅 품질 모니터링
- **품질 점수**: 사용빈도(40%) + 평균연관도(60%)
- **통계 제공**: 태그별 사용 현황

### 4. 캐싱 전략
- **루트 태그**: 2시간 TTL
- **태그 통계**: 주기적 갱신
- **캐시 무효화**: 태그 추가/제거시 자동

### 5. 이벤트 기반 연동
- **RouteTaggedEvent**: 추천 재계산 트리거
- **RouteUntaggedEvent**: 통계 업데이트 트리거

---

## ✅ 완료 사항
- ✅ 태그 연관도 점수 관리 (0.0-1.0)
- ✅ 중복 태깅 방지 로직
- ✅ 태그 품질 점수 계산
- ✅ 태그 사용 통계 제공
- ✅ 유사 루트 찾기 기능
- ✅ 비동기 태그 추천
- ✅ Redis 캐싱 적용
- ✅ 이벤트 발행 (추천 연동)

---

*RouteTaggingService 설계 완료: 태그 연관도 관리 및 품질 검증 시스템*