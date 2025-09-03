# Step 6-3a2: TagService 구현 - 검색 및 분석 시스템

> 태그 검색, 자동완성, 통계 분석 서비스 - Full-Text 검색, 인기 태그, 분석  
> 생성일: 2025-08-21  
> 단계: 6-3a2 (Service 레이어 - 태그 시스템 확장)  
> 연관: step6-3a1_tag_crud_management.md

---

## 🎯 설계 목표

- **고성능 검색**: Full-Text Index 활용 키워드 검색
- **실시간 자동완성**: prefix 기반 태그 제안 (2자 이상)
- **통계 분석**: 사용 통계, 인기 태그, 분포 분석
- **사용자별 맞춤**: 사용자/루트 태깅 전용 자동완성
- **성능 최적화**: 검색 결과 캐싱 및 최적화

---

## 🔍 TagService - 검색 및 분석 확장

### TagService.java (Part 2 - 검색 및 분석)
```java
// 앞의 import 구문들은 step6-3a1과 동일

/**
 * 태그 검색 및 분석 확장 서비스
 * 
 * 확장 기능:
 * - 키워드 검색 및 페이징
 * - 자동완성 시스템 (prefix 기반)
 * - 태그 통계 및 분석
 * - 인기 태그 랭킹
 * - 고급 검색 캐싱
 */
public class TagService {
    // ... 기본 필드들은 step6-3a1과 동일 ...

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
     * 고급 태그 검색 (필터 포함)
     */
    @Cacheable(value = "tag-advanced-search", 
               key = "#keyword + '_' + #tagType + '_' + #isUserSelectable + '_' + #isRouteTaggable + '_' + #pageable.pageNumber")
    public Page<Tag> searchTagsAdvanced(String keyword, TagType tagType, 
                                       Boolean isUserSelectable, Boolean isRouteTaggable, 
                                       Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            keyword = "";
        } else {
            keyword = XssProtectionUtil.cleanInput(keyword);
        }
        
        return tagRepository.searchTagsAdvanced(keyword, tagType, isUserSelectable, 
                                               isRouteTaggable, pageable);
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
    @Cacheable(value = "user-tag-autocomplete", key = "#prefix + '_' + #tagType")
    public List<Tag> autocompleteUserSelectableTags(String prefix, TagType tagType) {
        if (prefix == null || prefix.length() < autocompleteMinLength) {
            return List.of();
        }
        
        prefix = XssProtectionUtil.cleanInput(prefix);
        
        if (tagType != null) {
            return tagRepository.findUserSelectableByPrefixAndType(
                prefix, tagType, maxAutoCompleteResults);
        } else {
            return tagRepository.findUserSelectableByPrefix(prefix, maxAutoCompleteResults);
        }
    }

    /**
     * 루트 태깅용 태그 자동완성
     */
    @Cacheable(value = "route-tag-autocomplete", key = "#prefix + '_' + #tagType")
    public List<Tag> autocompleteRouteTaggableTags(String prefix, TagType tagType) {
        if (prefix == null || prefix.length() < autocompleteMinLength) {
            return List.of();
        }
        
        prefix = XssProtectionUtil.cleanInput(prefix);
        
        if (tagType != null) {
            return tagRepository.findRouteTaggableByPrefixAndType(
                prefix, tagType, maxAutoCompleteResults);
        } else {
            return tagRepository.findRouteTaggableByPrefix(prefix, maxAutoCompleteResults);
        }
    }

    /**
     * 스마트 자동완성 (사용 빈도 기반 우선순위)
     */
    @Cacheable(value = "smart-autocomplete", key = "#prefix + '_' + #tagType")
    public List<Tag> smartAutocompleteTags(String prefix, TagType tagType) {
        if (prefix == null || prefix.length() < autocompleteMinLength) {
            return List.of();
        }
        
        prefix = XssProtectionUtil.cleanInput(prefix);
        
        // 사용 빈도순으로 정렬된 자동완성 결과
        if (tagType != null) {
            return tagRepository.findByTagNameStartingWithAndTagTypeOrderByUsageCountDesc(
                prefix, tagType, maxAutoCompleteResults);
        } else {
            return tagRepository.findByTagNameStartingWithOrderByUsageCountDesc(
                prefix, maxAutoCompleteResults);
        }
    }

    /**
     * 유사 태그 추천 (편집거리 기반)
     */
    @Cacheable(value = "similar-tags", key = "#tagName + '_' + #maxDistance")
    public List<Tag> findSimilarTags(String tagName, int maxDistance) {
        if (!StringUtils.hasText(tagName) || maxDistance <= 0) {
            return List.of();
        }
        
        tagName = XssProtectionUtil.cleanInput(tagName);
        
        // 편집거리(Levenshtein distance) 기반 유사 태그 검색
        return tagRepository.findSimilarTagsByEditDistance(tagName, maxDistance, 10);
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
        
        // 최근 생성된 태그 Top 5
        List<Tag> recentTags = tagRepository.findTop5ByOrderByCreatedAtDesc();
        
        // 사용되지 않는 태그 수
        long unusedTags = tagRepository.countByUsageCount(0);
        
        return TagStatisticsDto.builder()
            .totalTags(totalTags)
            .userSelectableTags(userSelectableTags)
            .routeTaggableTags(routeTaggableTags)
            .bidirectionalTags(bidirectionalTags)
            .unusedTags(unusedTags)
            .tagTypeDistribution(tagTypeDistribution)
            .popularTags(popularTags)
            .recentTags(recentTags)
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
     * TagType별 인기 태그 조회
     */
    @Cacheable(value = "popular-tags-by-type", key = "#tagType + '_' + #limit")
    public List<Tag> getPopularTagsByType(TagType tagType, int limit) {
        return tagRepository.findTopByTagTypeOrderByUsageCountDesc(tagType, limit);
    }

    /**
     * 사용되지 않는 태그 조회
     */
    @Cacheable(value = "unused-tags")
    public List<Tag> getUnusedTags() {
        return tagRepository.findByUsageCountOrderByCreatedAtAsc(0);
    }

    /**
     * 최근 생성된 태그 조회
     */
    @Cacheable(value = "recent-tags", key = "#limit")
    public List<Tag> getRecentTags(int limit) {
        return tagRepository.findTopByOrderByCreatedAtDesc(limit);
    }

    /**
     * 태그 사용 빈도 분석
     */
    @Cacheable(value = "tag-usage-analysis")
    public TagUsageAnalysisDto getTagUsageAnalysis() {
        // 사용 빈도별 구간 분석
        long veryPopular = tagRepository.countByUsageCountGreaterThanEqual(100);  // 100회 이상
        long popular = tagRepository.countByUsageCountBetween(50, 99);            // 50-99회
        long moderate = tagRepository.countByUsageCountBetween(10, 49);           // 10-49회
        long low = tagRepository.countByUsageCountBetween(1, 9);                  // 1-9회
        long unused = tagRepository.countByUsageCount(0);                         // 0회
        
        // 평균 사용 횟수
        Double averageUsage = tagRepository.getAverageUsageCount();
        
        // 최고 사용 태그
        Tag mostUsedTag = tagRepository.findTopByOrderByUsageCountDesc().get(0);
        
        return TagUsageAnalysisDto.builder()
            .veryPopularTags(veryPopular)
            .popularTags(popular)
            .moderateTags(moderate)
            .lowUsageTags(low)
            .unusedTags(unused)
            .averageUsage(averageUsage != null ? averageUsage : 0.0)
            .mostUsedTag(mostUsedTag)
            .build();
    }

    /**
     * 태그 성장 트렌드 분석 (월별)
     */
    @Cacheable(value = "tag-growth-trend", key = "#months")
    public List<TagGrowthDto> getTagGrowthTrend(int months) {
        return tagRepository.getMonthlyTagGrowth(months);
    }

    // ===== 태그 추천 시스템 =====

    /**
     * 사용자 기반 태그 추천
     */
    @Cacheable(value = "recommended-tags", key = "#userId + '_' + #limit")
    public List<Tag> getRecommendedTagsForUser(Long userId, int limit) {
        // 사용자가 선호하는 태그와 유사한 태그 추천
        List<Long> userPreferredTagIds = tagRepository.getUserPreferredTagIds(userId);
        
        if (userPreferredTagIds.isEmpty()) {
            // 신규 사용자의 경우 인기 태그 추천
            return getPopularTags(limit);
        }
        
        return tagRepository.findRecommendedTagsBasedOnUserPreference(
            userPreferredTagIds, userId, limit);
    }

    /**
     * 루트 기반 태그 추천
     */
    @Cacheable(value = "route-recommended-tags", key = "#routeId + '_' + #limit")
    public List<Tag> getRecommendedTagsForRoute(Long routeId, int limit) {
        // 유사한 루트에서 많이 사용되는 태그 추천
        return tagRepository.findRecommendedTagsForRoute(routeId, limit);
    }

    /**
     * 컨텍스트 기반 태그 추천
     */
    @Cacheable(value = "context-recommended-tags", 
               key = "#existingTagIds.hashCode() + '_' + #limit")
    public List<Tag> getRecommendedTagsFromContext(List<Long> existingTagIds, int limit) {
        if (existingTagIds.isEmpty()) {
            return getPopularTags(limit);
        }
        
        // 기존 태그들과 자주 함께 사용되는 태그 추천
        return tagRepository.findFrequentlyUsedWithTags(existingTagIds, limit);
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
        private final long unusedTags;
        private final Map<TagType, Long> tagTypeDistribution;
        private final List<Tag> popularTags;
        private final List<Tag> recentTags;
    }

    /**
     * 태그 사용 분석 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class TagUsageAnalysisDto {
        private final long veryPopularTags;     // 100회 이상
        private final long popularTags;         // 50-99회
        private final long moderateTags;        // 10-49회
        private final long lowUsageTags;        // 1-9회
        private final long unusedTags;          // 0회
        private final double averageUsage;
        private final Tag mostUsedTag;
    }

    /**
     * 태그 성장 트렌드 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class TagGrowthDto {
        private final int year;
        private final int month;
        private final long newTagsCount;
        private final long totalTagsCount;
        private final double growthRate;
    }

    /**
     * 자동완성 결과 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class AutocompleteResultDto {
        private final List<Tag> exactMatches;        // 정확히 일치하는 태그
        private final List<Tag> prefixMatches;       // prefix 일치하는 태그
        private final List<Tag> similarTags;         // 유사한 태그
        private final boolean hasMore;               // 더 많은 결과 존재 여부
    }

    /**
     * 검색 결과 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class SearchResultDto {
        private final List<Tag> tags;
        private final long totalCount;
        private final Map<TagType, Long> typeDistribution;
        private final List<String> suggestions;      // 검색어 추천
        private final boolean corrected;             // 검색어 교정 여부
        private final String correctedQuery;         // 교정된 검색어
    }
}
```

---

## 🔍 고성능 검색 시스템

### 🔎 **1. 키워드 검색**
- **Full-Text 검색**: 태그명, 설명, 카테고리 전체 검색
- **고급 검색**: TagType, 플래그 기반 복합 필터
- **페이징 지원**: 대용량 검색 결과 효율적 처리
- **검색어 정제**: XSS 보호 및 특수문자 처리

### 🚀 **2. 실시간 자동완성**
- **Prefix 기반**: 2자 이상부터 자동완성 시작
- **스마트 순서**: 사용 빈도 기반 우선순위
- **맞춤형 필터**: 사용자용/루트용 별도 자동완성
- **성능 최적화**: 최대 20개 결과 제한

### 🎯 **3. 고급 자동완성**
- **유사 태그 추천**: 편집거리 기반 추천
- **컨텍스트 추천**: 기존 태그와 함께 사용되는 태그
- **사용자 맞춤**: 개인 선호도 기반 추천
- **루트 맞춤**: 유사 루트 기반 추천

---

## 📊 통계 및 분석 시스템

### 📈 **1. 기본 통계**
- **태그 분포**: 총 태그 수, 필터별 태그 수
- **사용 통계**: 사용/미사용 태그 분포
- **타입별 분포**: TagType별 태그 수
- **인기 랭킹**: 사용 횟수 기반 Top N

### 📋 **2. 사용 빈도 분석**
- **구간별 분석**: 매우 인기/인기/보통/낮음/미사용
- **평균 사용도**: 전체 태그 평균 사용 횟수
- **최고 인기 태그**: 가장 많이 사용된 태그
- **미사용 태그**: 정리 대상 태그 식별

### 📅 **3. 성장 트렌드**
- **월별 성장**: 신규 태그 생성 추이
- **성장률 계산**: 전월 대비 성장률
- **최근 태그**: 최근 생성된 태그 목록
- **트렌드 분석**: 태그 생성 패턴 분석

---

## 🎯 태그 추천 시스템

### 🤖 **1. 사용자 기반 추천**
- **선호도 분석**: 사용자가 선택한 태그 기반
- **유사 태그**: 선호 태그와 관련된 태그 추천  
- **신규 사용자**: 인기 태그 기반 초기 추천
- **개인화**: 사용자별 맞춤 추천

### 🧗 **2. 루트 기반 추천**
- **유사 루트 분석**: 비슷한 특성의 루트 태그
- **함께 사용**: 자주 함께 태깅되는 태그
- **난이도별**: 레벨별 추천 태그
- **벽면별**: 벽면 특성별 추천 태그

### 🔗 **3. 컨텍스트 추천**
- **연관 태그**: 기존 태그와 연관된 태그
- **태그 조합**: 효과적인 태그 조합 제안
- **완성도**: 누락된 필수 태그 제안
- **품질 향상**: 태그 품질 개선 제안

---

## 💾 고급 캐싱 전략

### 검색 캐싱
- **키워드 검색**: `tag-search:{keyword}_{page}`
- **고급 검색**: `tag-advanced-search:{hash}`
- **자동완성**: `tag-autocomplete:{prefix}_{type}`
- **추천 시스템**: `recommended-tags:{userId}_{limit}`

### 통계 캐싱
- **기본 통계**: `tag-statistics` (6시간)
- **사용 분석**: `tag-usage-analysis` (12시간)
- **성장 트렌드**: `tag-growth-trend:{months}` (24시간)
- **인기 태그**: `popular-tags:{limit}` (2시간)

### 캐시 최적화
- **선택적 무효화**: 관련 캐시만 정확히 무효화
- **배경 갱신**: 주기적 백그라운드 갱신
- **계층적 캐싱**: L1(메모리) + L2(Redis) 구조

---

## 🛡️ 성능 및 보안

### 성능 최적화
- **인덱스 활용**: Full-Text Index, Prefix Index
- **결과 제한**: 자동완성 20개, 검색 페이징
- **쿼리 최적화**: 복잡한 통계 쿼리 최적화
- **캐시 적중률**: 90% 이상 캐시 적중률 목표

### 보안 강화
- **입력 검증**: 모든 검색어 XSS 보호
- **SQL Injection**: PreparedStatement 사용
- **권한 제어**: 사용자별 접근 권한 관리
- **Rate Limiting**: 자동완성 요청 제한

---

## 🚀 활용 시나리오

### 🔍 **검색 최적화**
- 사용자가 원하는 태그를 빠르게 찾기
- 오타나 유사 검색어 자동 교정
- 검색 이력 기반 개인화

### 📱 **모바일 UX 최적화**
- 터치 친화적 자동완성
- 최소 키입력으로 최대 효과
- 네트워크 최적화된 결과 전송

### 📊 **데이터 드리븐 운영**
- 태그 사용 패턴 분석
- 미사용 태그 정리
- 인기 태그 기반 UI 최적화

*step6-3a2 완성: 태그 검색 및 분석 시스템 구현 완료*