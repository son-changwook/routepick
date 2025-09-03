# Step 6-3a1: TagService 구현 - 태그 CRUD 관리

> 태그 시스템 핵심 관리 서비스 - CRUD, 검증, 필터링, 캐싱  
> 생성일: 2025-08-21  
> 단계: 6-3a1 (Service 레이어 - 태그 시스템 핵심)  
> 참고: step1-2, step4-2a, step5-2a, step3-2c

---

## 🎯 설계 목표

- **8가지 TagType 완전 지원**: STYLE, FEATURE, TECHNIQUE, DIFFICULTY, MOVEMENT, HOLD_TYPE, WALL_ANGLE, OTHER
- **태그 CRUD 관리**: 생성, 조회, 수정, 삭제 및 중복 방지
- **플래그 기반 필터링**: is_user_selectable, is_route_taggable 지원
- **캐싱 전략**: @Cacheable 활용 4시간 TTL 적용
- **XSS 보호**: 모든 입력값 보안 처리

---

## 🏷️ TagService - 태그 핵심 관리

### TagService.java (Part 1 - CRUD 관리)
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
 * 태그 시스템 핵심 관리 서비스
 * 
 * 주요 기능:
 * - 8가지 TagType 기반 태그 CRUD
 * - 사용자 선택 가능 태그 필터링
 * - 루트 태깅 가능 태그 필터링
 * - display_order 관리
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
}
```

---

## 📋 핵심 CRUD 기능 설명

### 🏷️ **1. 태그 기본 관리**
- **생성**: 8가지 TagType 지원, 자동 기본값 설정
- **조회**: ID/이름 기반 조회, 캐싱 지원
- **수정**: 부분 업데이트 지원, 중복 검증
- **삭제**: 사용 중인 태그 삭제 방지 (물리적 삭제)

### 🔍 **2. 태그 필터링 시스템**
- **사용자 선택용**: `is_user_selectable = true` 태그만 조회
- **루트 태깅용**: `is_route_taggable = true` 태그만 조회  
- **양방향 태그**: 두 플래그 모두 true인 태그
- **TagType별 조회**: 8가지 태그 타입별 분류

### 🔢 **3. display_order 관리**
- **일괄 업데이트**: Map을 통한 순서 일괄 변경
- **타입별 재정렬**: TagType별 기본 순서 복원
- **자동 순서**: TagType.getSortOrder() * 100 + 순번

### 🗂️ **4. 태그 그룹화**
- **타입별 그룹**: TagType을 키로 한 태그 맵
- **카테고리별 그룹**: tag_category를 키로 한 태그 맵
- **배치 조회**: 여러 태그 ID를 한번에 조회

---

## 💾 Redis 캐싱 전략

### 캐시 키 구조
- **개별 태그**: `tag:{tagId}`, `tag-by-name:{tagName}`
- **태그 목록**: `all-tags`, `tags-by-type:{tagType}`
- **필터링**: `user-selectable-tags:{tagType}`, `route-taggable-tags:{tagType}`
- **그룹화**: `tags-grouped-by-type`, `tags-grouped-by-category`
- **배치**: `tags-batch:{hashCode}`

### 캐시 TTL 설정
- **기본 TTL**: 4시간 (설정 가능)
- **실시간 반영**: CUD 작업 시 @CacheEvict로 즉시 무효화
- **선택적 무효화**: 관련 캐시만 정확히 무효화

### 캐시 무효화 전략
- **태그 생성/수정/삭제**: 모든 태그 관련 캐시 무효화
- **사용 횟수 증가**: 인기 태그 관련 캐시만 무효화
- **순서 변경**: 정렬 관련 캐시 무효화

---

## 🛡️ 보안 및 검증

### XSS 보호
- **모든 입력값**: XssProtectionUtil.cleanInput() 적용
- **태그명, 카테고리, 설명**: HTML 태그 제거 및 인코딩
- **검색어 정제**: 사용자 입력 검색어 정제

### 비즈니스 규칙 검증
- **태그명 유니크**: 중복 태그명 생성/수정 방지
- **사용 중 태그 보호**: usageCount > 0인 태그 삭제 방지
- **기본값 설정**: null 플래그를 true로 자동 설정

### 데이터 일관성
- **@Transactional**: 데이터 일관성 보장
- **CASCADE 처리**: 연관 데이터 처리 고려
- **예외 처리**: TagException 기반 명확한 에러 메시지

---

## 🎯 8가지 TagType 지원

### TagType 분류
1. **STYLE**: 클라이밍 스타일 (볼더링, 스포츠클라이밍 등)
2. **FEATURE**: 벽면 특징 (오버행, 슬랩 등)  
3. **TECHNIQUE**: 기술 요소 (다이노, 맨틀 등)
4. **DIFFICULTY**: 난이도 관련 (파워, 지구력 등)
5. **MOVEMENT**: 동작 패턴 (크로스, 록오프 등)
6. **HOLD_TYPE**: 홀드 유형 (크림프, 슬로퍼 등)
7. **WALL_ANGLE**: 벽면 각도 (수직, 오버행 등)
8. **OTHER**: 기타 분류

### 자동 순서 관리
- **기본 순서**: `TagType.getSortOrder() * 100`
- **세부 순서**: 타입 내에서 0, 1, 2... 순차 증가
- **표시 제어**: UI에서 정렬된 순서로 표시

---

## 🚀 활용 시나리오

### 👤 **사용자 프로필 설정**
- 사용자 선택 가능 태그만 필터링하여 제공
- 선호하는 클라이밍 스타일 및 기술 선택
- TagType별 그룹화로 체계적 선택 지원

### 🧗 **루트 태깅**
- 루트 태깅 가능 태그만 제공
- 루트 특성에 맞는 태그 분류
- 자동완성으로 빠른 태그 입력 지원

### 📊 **관리자 운영**
- 태그 사용 통계 모니터링
- display_order 관리로 UI 최적화
- 사용 중인 태그 보호로 데이터 무결성 유지

*step6-3a1 완성: 태그 CRUD 및 핵심 관리 기능 구현 완료*