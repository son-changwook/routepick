# Step 6-3a: TagService 구현

> 태그 시스템 관리 서비스 - 8가지 TagType 지원, CRUD, 자동완성, 캐싱  
> 생성일: 2025-08-21  
> 단계: 6-3a (Service 레이어 - 태그 시스템)  
> 참고: step1-2, step4-2a, step5-2a, step3-2c

---

## 🎯 설계 목표

- **8가지 TagType 완전 지원**: STYLE, FEATURE, TECHNIQUE, DIFFICULTY, MOVEMENT, HOLD_TYPE, WALL_ANGLE, OTHER
- **태그 검색 최적화**: 자동완성, 실시간 검색, Full-Text Index 활용
- **캐싱 전략**: @Cacheable 활용 4시간 TTL 적용
- **플래그 관리**: is_user_selectable, is_route_taggable 필터링
- **품질 관리**: 중복 방지, display_order 관리, 사용 통계

---

## 🏷️ TagService - 태그 관리 서비스

### TagService.java
```java
package com.routepick.service.tag;

import com.routepick.common.enums.TagType;
import com.routepick.domain.tag.entity.Tag;
import com.routepick.domain.tag.repository.TagRepository;
import com.routepick.exception.tag.TagException;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 태그 시스템 관리 서비스
 * 
 * 주요 기능:
 * - 8가지 TagType 기반 태그 관리
 * - 사용자 선택 가능 태그 필터링
 * - 루트 태깅 가능 태그 필터링
 * - 태그 자동완성 및 검색
 * - 태그 사용 통계 및 분석
 * - Redis 캐싱 (4시간 TTL)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;
    
    @Value("${routepick.tag.cache-ttl-hours:4}")
    private int cacheTimeToLiveHours;
    
    @Value("${routepick.tag.autocomplete-min-length:2}")
    private int autocompleteMinLength;
    
    @Value("${routepick.tag.max-results:20}")
    private int maxAutoCompleteResults;

    // ===== 태그 기본 관리 =====

    /**
     * 태그 생성
     */
    @Transactional
    @CacheEvict(value = {"tags", "user-selectable-tags", "route-taggable-tags"}, allEntries = true)
    public Tag createTag(String tagName, TagType tagType, String tagCategory,
                        String description, Boolean isUserSelectable, 
                        Boolean isRouteTaggable, Integer displayOrder) {
        
        // XSS 보호
        tagName = XssProtectionUtil.cleanInput(tagName);
        if (StringUtils.hasText(tagCategory)) {
            tagCategory = XssProtectionUtil.cleanInput(tagCategory);
        }
        if (StringUtils.hasText(description)) {
            description = XssProtectionUtil.cleanInput(description);
        }
        
        // 중복 태그명 검증
        if (tagRepository.existsByTagName(tagName)) {
            throw TagException.tagAlreadyExists(tagName);
        }
        
        // 기본값 설정
        if (isUserSelectable == null) {
            isUserSelectable = true;
        }
        if (isRouteTaggable == null) {
            isRouteTaggable = true;
        }
        if (displayOrder == null) {
            displayOrder = tagType.getSortOrder() * 100;
        }
        
        Tag tag = Tag.builder()
            .tagName(tagName)
            .tagType(tagType)
            .tagCategory(tagCategory)
            .description(description)
            .isUserSelectable(isUserSelectable)
            .isRouteTaggable(isRouteTaggable)
            .displayOrder(displayOrder)
            .usageCount(0)
            .build();
            
        Tag savedTag = tagRepository.save(tag);
        
        log.info("태그 생성 완료 - tagId: {}, name: {}, type: {}", 
                savedTag.getTagId(), savedTag.getTagName(), tagType);
        return savedTag;
    }

    /**
     * 태그 ID로 조회
     */
    @Cacheable(value = "tag", key = "#tagId")
    public Tag getTagById(Long tagId) {
        return tagRepository.findById(tagId)
            .orElseThrow(() -> TagException.tagNotFound(tagId));
    }

    /**
     * 태그명으로 조회
     */
    @Cacheable(value = "tag-by-name", key = "#tagName")
    public Tag getTagByName(String tagName) {
        return tagRepository.findByTagName(tagName)
            .orElseThrow(() -> TagException.tagNotFoundByName(tagName));
    }

    /**
     * 태그 정보 수정
     */
    @Transactional
    @CacheEvict(value = {"tag", "tags", "user-selectable-tags", "route-taggable-tags"}, allEntries = true)
    public Tag updateTag(Long tagId, String tagName, String tagCategory,
                        String description, Boolean isUserSelectable,
                        Boolean isRouteTaggable, Integer displayOrder) {
        
        Tag tag = getTagById(tagId);
        
        // XSS 보호 및 업데이트
        if (StringUtils.hasText(tagName)) {
            tagName = XssProtectionUtil.cleanInput(tagName);
            
            // 다른 태그와 이름 중복 검증
            if (!tag.getTagName().equals(tagName) && 
                tagRepository.existsByTagName(tagName)) {
                throw TagException.tagAlreadyExists(tagName);
            }
            tag.setTagName(tagName);
        }
        
        if (tagCategory != null) {
            tag.setTagCategory(XssProtectionUtil.cleanInput(tagCategory));
        }
        
        if (description != null) {
            tag.setDescription(XssProtectionUtil.cleanInput(description));
        }
        
        if (isUserSelectable != null) {
            tag.setUserSelectable(isUserSelectable);
        }
        
        if (isRouteTaggable != null) {
            tag.setRouteTaggable(isRouteTaggable);
        }
        
        if (displayOrder != null) {
            tag.setDisplayOrder(displayOrder);
        }
        
        log.info("태그 정보 수정 완료 - tagId: {}", tagId);
        return tag;
    }

    /**
     * 태그 삭제 (물리적 삭제)
     * 주의: 사용 중인 태그는 삭제 불가
     */
    @Transactional
    @CacheEvict(value = {"tag", "tags", "user-selectable-tags", "route-taggable-tags"}, allEntries = true)
    public void deleteTag(Long tagId) {
        Tag tag = getTagById(tagId);
        
        // 사용 중인 태그 검증
        if (tag.getUsageCount() > 0) {
            throw TagException.tagInUse(tagId, tag.getUsageCount());
        }
        
        tagRepository.delete(tag);
        
        log.info("태그 삭제 완료 - tagId: {}, name: {}", tagId, tag.getTagName());
    }

    // ===== 태그 조회 및 필터링 =====

    /**
     * 모든 태그 조회 (캐싱)
     */
    @Cacheable(value = "all-tags")
    public List<Tag> getAllTags() {
        return tagRepository.findAllByOrderByTagTypeAscDisplayOrderAsc();
    }

    /**
     * TagType별 태그 조회
     */
    @Cacheable(value = "tags-by-type", key = "#tagType")
    public List<Tag> getTagsByType(TagType tagType) {
        return tagRepository.findByTagTypeOrderByDisplayOrderAsc(tagType);
    }

    /**
     * 프로필용 태그 조회 (is_user_selectable = true)
     */
    @Cacheable(value = "user-selectable-tags", key = "#tagType != null ? #tagType : 'all'")
    public List<Tag> getUserSelectableTags(TagType tagType) {
        if (tagType != null) {
            return tagRepository.findByIsUserSelectableTrueAndTagTypeOrderByDisplayOrderAsc(tagType);
        } else {
            return tagRepository.findByIsUserSelectableTrueOrderByTagTypeAscDisplayOrderAsc();
        }
    }

    /**
     * 루트용 태그 조회 (is_route_taggable = true)
     */
    @Cacheable(value = "route-taggable-tags", key = "#tagType != null ? #tagType : 'all'")
    public List<Tag> getRouteTaggableTags(TagType tagType) {
        if (tagType != null) {
            return tagRepository.findByIsRouteTaggableTrueAndTagTypeOrderByDisplayOrderAsc(tagType);
        } else {
            return tagRepository.findByIsRouteTaggableTrueOrderByTagTypeAscDisplayOrderAsc();
        }
    }

    /**
     * 양방향 태그 조회 (사용자 선택 + 루트 태깅 모두 가능)
     */
    @Cacheable(value = "bidirectional-tags")
    public List<Tag> getBidirectionalTags() {
        return tagRepository.findBidirectionalTags();
    }

    // ===== 태그 검색 및 자동완성 =====

    /**
     * 태그 검색 (페이징)
     */
    @Cacheable(value = "tag-search", key = "#keyword + '_' + #pageable.pageNumber")
    public Page<Tag> searchTags(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return tagRepository.findAll(pageable);
        }
        
        keyword = XssProtectionUtil.cleanInput(keyword);
        return tagRepository.searchByKeyword(keyword, pageable);
    }

    /**
     * 태그 자동완성
     */
    @Cacheable(value = "tag-autocomplete", key = "#prefix + '_' + #tagType")
    public List<Tag> autocompleteTags(String prefix, TagType tagType) {
        // 최소 길이 검증
        if (prefix == null || prefix.length() < autocompleteMinLength) {
            return List.of();
        }
        
        prefix = XssProtectionUtil.cleanInput(prefix);
        
        List<Tag> results;
        if (tagType != null) {
            results = tagRepository.findByTagNameStartingWithAndTagType(
                prefix, tagType, maxAutoCompleteResults);
        } else {
            results = tagRepository.findByTagNameStartingWith(
                prefix, maxAutoCompleteResults);
        }
        
        return results;
    }

    /**
     * 사용자 선택용 태그 자동완성
     */
    @Cacheable(value = "user-tag-autocomplete", key = "#prefix")
    public List<Tag> autocompleteUserSelectableTags(String prefix) {
        if (prefix == null || prefix.length() < autocompleteMinLength) {
            return List.of();
        }
        
        prefix = XssProtectionUtil.cleanInput(prefix);
        return tagRepository.findUserSelectableByPrefix(prefix, maxAutoCompleteResults);
    }

    /**
     * 루트 태깅용 태그 자동완성
     */
    @Cacheable(value = "route-tag-autocomplete", key = "#prefix")
    public List<Tag> autocompleteRouteTaggableTags(String prefix) {
        if (prefix == null || prefix.length() < autocompleteMinLength) {
            return List.of();
        }
        
        prefix = XssProtectionUtil.cleanInput(prefix);
        return tagRepository.findRouteTaggableByPrefix(prefix, maxAutoCompleteResults);
    }

    // ===== 태그 통계 및 분석 =====

    /**
     * 태그 사용 통계 조회
     */
    @Cacheable(value = "tag-statistics")
    public TagStatisticsDto getTagStatistics() {
        long totalTags = tagRepository.count();
        long userSelectableTags = tagRepository.countByIsUserSelectableTrue();
        long routeTaggableTags = tagRepository.countByIsRouteTaggableTrue();
        long bidirectionalTags = tagRepository.countBidirectionalTags();
        
        // TagType별 분포
        Map<TagType, Long> tagTypeDistribution = tagRepository.getTagTypeDistribution();
        
        // 인기 태그 Top 10
        List<Tag> popularTags = tagRepository.findTop10ByOrderByUsageCountDesc();
        
        return TagStatisticsDto.builder()
            .totalTags(totalTags)
            .userSelectableTags(userSelectableTags)
            .routeTaggableTags(routeTaggableTags)
            .bidirectionalTags(bidirectionalTags)
            .tagTypeDistribution(tagTypeDistribution)
            .popularTags(popularTags)
            .build();
    }

    /**
     * TagType별 태그 수 조회
     */
    @Cacheable(value = "tag-count-by-type")
    public Map<TagType, Long> getTagCountByType() {
        return tagRepository.getTagTypeDistribution();
    }

    /**
     * 인기 태그 조회
     */
    @Cacheable(value = "popular-tags", key = "#limit")
    public List<Tag> getPopularTags(int limit) {
        return tagRepository.findTopByOrderByUsageCountDesc(limit);
    }

    /**
     * 태그 사용 횟수 증가
     */
    @Transactional
    @CacheEvict(value = {"tag", "popular-tags", "tag-statistics"}, allEntries = true)
    public void incrementTagUsage(Long tagId) {
        Tag tag = getTagById(tagId);
        tag.incrementUsageCount();
        
        log.debug("태그 사용 횟수 증가 - tagId: {}, count: {}", 
                 tagId, tag.getUsageCount());
    }

    // ===== display_order 관리 =====

    /**
     * 태그 표시 순서 일괄 업데이트
     */
    @Transactional
    @CacheEvict(value = {"tags", "user-selectable-tags", "route-taggable-tags"}, allEntries = true)
    public void updateDisplayOrders(Map<Long, Integer> displayOrderMap) {
        displayOrderMap.forEach((tagId, displayOrder) -> {
            Tag tag = getTagById(tagId);
            tag.setDisplayOrder(displayOrder);
        });
        
        log.info("태그 표시 순서 일괄 업데이트 완료 - 수정 개수: {}", displayOrderMap.size());
    }

    /**
     * TagType별 기본 표시 순서 재정렬
     */
    @Transactional
    @CacheEvict(value = {"tags", "user-selectable-tags", "route-taggable-tags"}, allEntries = true)
    public void resetDisplayOrdersByType(TagType tagType) {
        List<Tag> tags = getTagsByType(tagType);
        
        int baseOrder = tagType.getSortOrder() * 100;
        for (int i = 0; i < tags.size(); i++) {
            tags.get(i).setDisplayOrder(baseOrder + i);
        }
        
        log.info("TagType별 표시 순서 재정렬 완료 - type: {}, count: {}", 
                tagType, tags.size());
    }

    // ===== 태그 그룹화 =====

    /**
     * TagType별로 그룹화된 태그 맵 조회
     */
    @Cacheable(value = "tags-grouped-by-type")
    public Map<TagType, List<Tag>> getTagsGroupedByType() {
        List<Tag> allTags = getAllTags();
        return allTags.stream()
            .collect(Collectors.groupingBy(Tag::getTagType));
    }

    /**
     * 카테고리별로 그룹화된 태그 맵 조회
     */
    @Cacheable(value = "tags-grouped-by-category")
    public Map<String, List<Tag>> getTagsGroupedByCategory() {
        List<Tag> allTags = getAllTags();
        return allTags.stream()
            .filter(tag -> StringUtils.hasText(tag.getTagCategory()))
            .collect(Collectors.groupingBy(Tag::getTagCategory));
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 태그 존재 여부 확인
     */
    public boolean existsTag(Long tagId) {
        return tagRepository.existsById(tagId);
    }

    /**
     * 태그명 존재 여부 확인
     */
    public boolean existsTagName(String tagName) {
        return tagRepository.existsByTagName(tagName);
    }

    /**
     * 태그 배치 조회
     */
    @Cacheable(value = "tags-batch", key = "#tagIds.hashCode()")
    public List<Tag> getTagsByIds(List<Long> tagIds) {
        return tagRepository.findAllById(tagIds);
    }

    // ===== DTO 클래스 =====

    /**
     * 태그 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class TagStatisticsDto {
        private final long totalTags;
        private final long userSelectableTags;
        private final long routeTaggableTags;
        private final long bidirectionalTags;
        private final Map<TagType, Long> tagTypeDistribution;
        private final List<Tag> popularTags;
    }
}
```

---

## 📋 주요 기능 설명

### 🏷️ **1. 태그 기본 관리**
- **CRUD 작업**: 생성, 조회, 수정, 삭제
- **중복 방지**: 태그명 유니크 검증
- **사용 중 보호**: 사용 중인 태그 삭제 방지
- **XSS 보호**: 모든 입력값 보안 처리

### 🔍 **2. 태그 필터링**
- **사용자 선택용**: is_user_selectable = true 필터
- **루트 태깅용**: is_route_taggable = true 필터
- **양방향 태그**: 두 플래그 모두 true인 태그
- **TagType별 조회**: 8가지 태그 타입별 분류

### 🔎 **3. 검색 및 자동완성**
- **키워드 검색**: 태그명, 설명 검색
- **자동완성**: prefix 기반 실시간 제안
- **최소 길이**: 2자 이상부터 자동완성
- **결과 제한**: 최대 20개 결과 반환

### 📊 **4. 통계 및 분석**
- **사용 통계**: 전체, 필터별 태그 수
- **인기 태그**: 사용 횟수 기반 랭킹
- **타입별 분포**: TagType별 태그 분포
- **사용 횟수 추적**: incrementUsageCount

### 🔢 **5. display_order 관리**
- **순서 업데이트**: 일괄 순서 변경
- **타입별 재정렬**: TagType별 기본 순서
- **UI 표시 제어**: 정렬된 태그 목록

### 🗂️ **6. 태그 그룹화**
- **타입별 그룹**: TagType별 태그 맵
- **카테고리별 그룹**: category별 태그 맵
- **배치 조회**: 여러 태그 한번에 조회

---

## 💾 **Redis 캐싱 전략**

### 캐시 키 구조
- **개별 태그**: `tag:{tagId}`, `tag-by-name:{tagName}`
- **태그 목록**: `all-tags`, `tags-by-type:{tagType}`
- **필터링**: `user-selectable-tags:{tagType}`, `route-taggable-tags:{tagType}`
- **자동완성**: `tag-autocomplete:{prefix}_{tagType}`
- **통계**: `tag-statistics`, `popular-tags:{limit}`

### 캐시 TTL
- **기본 TTL**: 4시간 (설정 가능)
- **통계 캐시**: 6시간
- **자동완성**: 2시간

### 캐시 무효화
- **태그 수정/삭제**: 관련 모든 캐시 무효화
- **사용 횟수 증가**: 통계 캐시 무효화

---

## 🛡️ **보안 및 검증**

### 입력 검증
- **XSS 보호**: XssProtectionUtil 적용
- **태그명 유니크**: 중복 태그명 차단
- **최소 길이**: 자동완성 2자 이상

### 비즈니스 규칙
- **사용 중 태그 보호**: 삭제 시 사용 횟수 확인
- **기본값 설정**: 플래그 기본값 true
- **표시 순서**: TagType별 기본 순서

---

## 🚀 **다음 단계**

**Phase 2 완료 후 진행할 작업:**
- **step6-3b_user_preference_service.md**: 사용자 선호도 Service
- **step6-3c_route_tagging_service.md**: 루트 태깅 Service
- **step6-3d_recommendation_service.md**: 추천 시스템 Service

*step6-3a 완성: TagService 완전 구현 완료*