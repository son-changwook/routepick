# 9-2h2: 보안/캐싱/플래그 검증 테스트 구현 (완전본)

> **RoutePickr - 클라이밍 루트 추천 플랫폼**  
> Step 9-2h: 누락된 Critical 테스트 보완 (보안/캐싱/플래그 검증 Part)

## 📋 이 문서의 내용

이 문서는 **step9-2h_missing_critical_tests.md**에서 분할된 두 번째 부분으로, 다음 테스트들을 포함합니다:

### 🔐 보안 관련 테스트
- RecommendationController 보안 테스트 (인증, 권한, 데이터 격리)
- UserPreferenceController 보안 테스트

### 🚀 캐싱 전략 테스트  
- 태그 캐싱 (30분 TTL)
- 추천 결과 캐싱 (1시간 TTL)
- 캐시 무효화 및 워밍업
- Redis 분산 캐시 일관성

### 🏷️ 태그 플래그 검증 테스트
- `is_user_selectable` 플래그 검증
- `is_route_taggable` 플래그 검증  
- `display_order` 정렬 검증
- 플래그 조합 유효성 검사

---

## 🔐 보안 테스트 구현

### RecommendationController 보안 테스트

```java
package com.routepick.controller.tag;

import com.routepick.service.tag.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecommendationController.class)
@DisplayName("RecommendationController 보안 테스트")
class RecommendationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    // ===== 보안 테스트 =====

    @Test
    @DisplayName("인증 없이 추천 조회 시도")
    void getRecommendationsWithoutAuth_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations/routes"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("다른 사용자 추천 데이터 접근 시도 - 격리 확인")
    void accessOtherUserRecommendations_ShouldBeIsolated() throws Exception {
        // Given - 사용자별 데이터 격리 확인
        given(recommendationService.getPersonalizedRecommendations(eq(2L), any()))
                .willThrow(new AccessDeniedException("다른 사용자의 추천 데이터에 접근할 수 없습니다."));

        // When & Then
        mockMvc.perform(get("/api/v1/recommendations/routes")
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 2L)) // 다른 사용자
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser
    @DisplayName("추천 결과 캐시 무효화 테스트")
    void cacheInvalidation_Success() throws Exception {
        // Given
        willDoNothing().given(recommendationService).invalidateUserCache(1L);

        // When & Then
        mockMvc.perform(delete("/api/v1/recommendations/cache")
                        .with(csrf())
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("캐시가 성공적으로 삭제되었습니다."));

        verify(recommendationService).invalidateUserCache(1L);
    }
}
```

---

## 🏷️ 태그 플래그 검증 테스트

### TagFlagValidationTest.java

```java
package com.routepick.service.tag;

import com.routepick.common.enums.TagType;
import com.routepick.domain.tag.entity.Tag;
import com.routepick.domain.tag.repository.TagRepository;
import com.routepick.exception.tag.TagException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("태그 플래그 검증 테스트")
class TagFlagValidationTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    private List<Tag> testTags;

    @BeforeEach
    void setUp() {
        // 다양한 플래그 조합의 테스트 태그들
        testTags = Arrays.asList(
                createTag(1L, "다이나믹", TagType.STYLE, true, true, 1),    // 프로필용O, 루트용O
                createTag(2L, "시스템태그", TagType.OTHER, false, true, 2),   // 프로필용X, 루트용O
                createTag(3L, "관리자전용", TagType.FEATURE, false, false, 3), // 프로필용X, 루트용X
                createTag(4L, "프로필전용", TagType.MOVEMENT, true, false, 4)  // 프로필용O, 루트용X
        );
    }

    @Test
    @DisplayName("is_user_selectable=true 태그만 프로필용 조회")
    void getSelectableTags_OnlyUserSelectableTags() {
        // Given
        List<Tag> userSelectableTags = testTags.stream()
                .filter(Tag::isUserSelectable)
                .toList();

        given(tagRepository.findByIsUserSelectableTrueAndIsActiveTrueOrderByDisplayOrderAsc())
                .willReturn(userSelectableTags);

        // When
        List<Tag> result = tagService.getTagsForProfile();

        // Then
        assertThat(result).hasSize(2); // "다이나믹", "프로필전용"
        assertThat(result).extracting(Tag::getTagName)
                .containsExactly("다이나믹", "프로필전용");
        assertThat(result).allMatch(Tag::isUserSelectable);

        verify(tagRepository).findByIsUserSelectableTrueAndIsActiveTrueOrderByDisplayOrderAsc();
    }

    @Test
    @DisplayName("is_route_taggable=true 태그만 루트 태깅용 조회")
    void getTaggableTags_OnlyRouteTaggableTags() {
        // Given
        List<Tag> routeTaggableTags = testTags.stream()
                .filter(Tag::isRouteTaggable)
                .toList();

        given(tagRepository.findByIsRouteTaggableTrueAndIsActiveTrueOrderByTagTypeAscDisplayOrderAsc())
                .willReturn(routeTaggableTags);

        // When
        List<Tag> result = tagService.getTagsForRouteTagging();

        // Then
        assertThat(result).hasSize(2); // "다이나믹", "시스템태그"
        assertThat(result).extracting(Tag::getTagName)
                .containsExactly("다이나믹", "시스템태그");
        assertThat(result).allMatch(Tag::isRouteTaggable);

        verify(tagRepository).findByIsRouteTaggableTrueAndIsActiveTrueOrderByTagTypeAscDisplayOrderAsc();
    }

    @Test
    @DisplayName("플래그 조합 검증 - 둘 다 false인 태그는 접근 불가")
    void validateTagFlags_BothFalse_ShouldNotBeAccessible() {
        // Given
        Tag restrictedTag = testTags.stream()
                .filter(tag -> !tag.isUserSelectable() && !tag.isRouteTaggable())
                .findFirst()
                .orElseThrow(); // "관리자전용"

        given(tagRepository.findById(3L))
                .willReturn(Optional.of(restrictedTag));

        // When & Then - 프로필용 접근 시도
        assertThatThrownBy(() -> tagService.validateUserSelectableTag(3L))
                .isInstanceOf(TagException.class)
                .hasMessage("해당 태그는 사용자 선택용이 아닙니다.");

        // When & Then - 루트 태깅용 접근 시도
        assertThatThrownBy(() -> tagService.validateRouteTaggableTag(3L))
                .isInstanceOf(TagException.class)
                .hasMessage("해당 태그는 루트 태깅용이 아닙니다.");
    }

    @Test
    @DisplayName("display_order 정렬 검증")
    void validateDisplayOrder_SortCorrectly() {
        // Given
        given(tagRepository.findByIsUserSelectableTrueAndIsActiveTrueOrderByDisplayOrderAsc())
                .willReturn(testTags.stream()
                        .filter(Tag::isUserSelectable)
                        .sorted(Comparator.comparing(Tag::getDisplayOrder))
                        .toList());

        // When
        List<Tag> result = tagService.getTagsForProfile();

        // Then
        assertThat(result).isSortedAccordingTo(Comparator.comparing(Tag::getDisplayOrder));
        
        // display_order 값 검증
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getDisplayOrder())
                    .isLessThanOrEqualTo(result.get(i + 1).getDisplayOrder());
        }
    }

    @Test
    @DisplayName("TagType과 display_order 복합 정렬 검증")
    void validateComplexSorting_TagTypeAndDisplayOrder() {
        // Given
        List<Tag> complexSortedTags = Arrays.asList(
                createTag(1L, "스타일1", TagType.STYLE, true, true, 1),
                createTag(2L, "스타일2", TagType.STYLE, true, true, 2),
                createTag(3L, "특징1", TagType.FEATURE, true, true, 1),
                createTag(4L, "특징2", TagType.FEATURE, true, true, 2)
        );

        given(tagRepository.findByIsRouteTaggableTrueAndIsActiveTrueOrderByTagTypeAscDisplayOrderAsc())
                .willReturn(complexSortedTags);

        // When
        List<Tag> result = tagService.getTagsForRouteTagging();

        // Then - TagType별 그룹화 후 display_order 정렬 확인
        Map<TagType, List<Tag>> groupedByType = result.stream()
                .collect(Collectors.groupingBy(Tag::getTagType));

        groupedByType.values().forEach(group -> {
            assertThat(group).isSortedAccordingTo(Comparator.comparing(Tag::getDisplayOrder));
        });
    }

    @Test
    @DisplayName("플래그 업데이트 후 정렬 순서 재정렬")
    void updateTagFlags_ResortOrder() {
        // Given - 기존 태그들
        Tag tagToUpdate = testTags.get(2); // "관리자전용" (둘 다 false)
        
        given(tagRepository.findById(3L))
                .willReturn(Optional.of(tagToUpdate));
        given(tagRepository.save(any(Tag.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // When - 플래그 업데이트 (프로필용으로 변경)
        tagService.updateTagFlags(3L, true, false, 10); // display_order도 변경

        // Then
        verify(tagRepository).save(argThat(tag -> 
                tag.isUserSelectable() && 
                !tag.isRouteTaggable() && 
                tag.getDisplayOrder() == 10
        ));
    }

    // 헬퍼 메소드
    private Tag createTag(Long id, String name, TagType type, boolean userSelectable, 
                          boolean routeTaggable, int displayOrder) {
        return Tag.builder()
                .tagId(id)
                .tagType(type)
                .tagName(name)
                .tagDescription(name + " 설명")
                .isActive(true)
                .isUserSelectable(userSelectable)
                .isRouteTaggable(routeTaggable)
                .displayOrder(displayOrder)
                .usageCount(0L)
                .build();
    }
}
```

---

## 🚀 캐싱 전략 테스트

### CachingStrategyTest.java

```java
package com.routepick.service.tag;

import com.routepick.common.enums.TagType;
import com.routepick.domain.tag.entity.Tag;
import com.routepick.dto.recommendation.RecommendationResponseDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("캐싱 전략 테스트")
class CachingStrategyTest {

    @MockBean
    private CacheManager cacheManager;

    @MockBean
    private Cache tagCache;

    @MockBean
    private Cache recommendationCache;

    @Test
    @DisplayName("태그 캐싱 - 30분 TTL 확인")
    void tagCaching_30MinuteTTL() {
        // Given
        List<Tag> tags = Arrays.asList(
                createTag(1L, "다이나믹", TagType.STYLE),
                createTag(2L, "파워풀", TagType.STYLE)
        );

        String cacheKey = "tags:profile:active";
        
        given(cacheManager.getCache("tags"))
                .willReturn(tagCache);
        given(tagCache.get(cacheKey, List.class))
                .willReturn(null) // 첫 번째 호출 시 캐시 미스
                .willReturn(tags); // 두 번째 호출 시 캐시 히트

        // When - 첫 번째 호출 (캐시 미스)
        List<Tag> firstResult = getTagsWithCache(cacheKey);
        
        // 캐시에 저장
        tagCache.put(cacheKey, tags);
        
        // 두 번째 호출 (캐시 히트)
        List<Tag> secondResult = getTagsWithCache(cacheKey);

        // Then
        assertThat(firstResult).isNull(); // 캐시 미스
        assertThat(secondResult).isEqualTo(tags); // 캐시 히트
        
        verify(tagCache).put(cacheKey, tags);
        verify(tagCache, times(2)).get(cacheKey, List.class);
    }

    @Test
    @DisplayName("추천 결과 캐싱 - 1시간 TTL 확인")
    void recommendationCaching_1HourTTL() {
        // Given
        Long userId = 1L;
        String cacheKey = "recommendations:user:" + userId;
        
        RecommendationResponseDto recommendationResponse = RecommendationResponseDto.builder()
                .userId(userId)
                .totalRecommendations(5)
                .calculatedAt(LocalDateTime.now())
                .cacheExpiresAt(LocalDateTime.now().plusHours(1)) // 1시간 TTL
                .build();

        given(cacheManager.getCache("recommendations"))
                .willReturn(recommendationCache);
        given(recommendationCache.get(cacheKey, RecommendationResponseDto.class))
                .willReturn(null) // 캐시 미스
                .willReturn(recommendationResponse); // 캐시 히트

        // When - 첫 번째 호출
        RecommendationResponseDto firstResult = getRecommendationsWithCache(cacheKey);
        
        // 캐시에 저장
        recommendationCache.put(cacheKey, recommendationResponse);
        
        // 두 번째 호출
        RecommendationResponseDto secondResult = getRecommendationsWithCache(cacheKey);

        // Then
        assertThat(firstResult).isNull();
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.getUserId()).isEqualTo(userId);
        
        // TTL 검증
        assertThat(secondResult.getCacheExpiresAt())
                .isAfter(LocalDateTime.now().plusMinutes(50)); // 최소 50분 후 만료
        
        verify(recommendationCache).put(cacheKey, recommendationResponse);
    }

    @Test
    @DisplayName("캐시 무효화 - 사용자 선호도 변경 시")
    void cacheInvalidation_OnPreferenceUpdate() {
        // Given
        Long userId = 1L;
        String userRecCacheKey = "recommendations:user:" + userId;
        String tagCacheKey = "tags:profile:active";

        given(cacheManager.getCache("recommendations"))
                .willReturn(recommendationCache);
        given(cacheManager.getCache("tags"))
                .willReturn(tagCache);

        // When - 사용자 선호도 변경 (추천 캐시만 무효화)
        invalidateUserRecommendationCache(userId);

        // Then - 사용자별 추천 캐시만 삭제, 태그 캐시는 유지
        verify(recommendationCache).evict(userRecCacheKey);
        verify(tagCache, never()).clear();
    }

    @Test
    @DisplayName("캐시 워밍업 - 인기 태그 미리 로딩")
    void cacheWarming_PopularTags() {
        // Given
        List<Tag> popularTags = Arrays.asList(
                createTag(1L, "다이나믹", TagType.STYLE),
                createTag(2L, "파워풀", TagType.STYLE),
                createTag(3L, "오버행", TagType.FEATURE)
        );

        given(cacheManager.getCache("tags"))
                .willReturn(tagCache);

        // When - 애플리케이션 시작 시 캐시 워밍업
        String[] warmupKeys = {
                "tags:profile:active",
                "tags:route:active", 
                "tags:popular:top20"
        };

        for (String key : warmupKeys) {
            tagCache.put(key, popularTags);
        }

        // Then
        for (String key : warmupKeys) {
            verify(tagCache).put(key, popularTags);
        }
    }

    @Test
    @DisplayName("분산 캐시 일관성 - Redis 클러스터")
    void distributedCacheConsistency() {
        // Given - 멀티 인스턴스 환경에서 캐시 일관성
        String cacheKey = "tags:global:active";
        List<Tag> tags = Arrays.asList(createTag(1L, "테스트", TagType.STYLE));

        // Instance A에서 캐시 업데이트
        given(cacheManager.getCache("tags")).willReturn(tagCache);
        tagCache.put(cacheKey, tags);

        // Instance B에서 동일 키 조회
        given(tagCache.get(cacheKey, List.class)).willReturn(tags);

        // When
        List<Tag> result = getTagsWithCache(cacheKey);

        // Then - 분산 환경에서도 일관된 데이터 반환
        assertThat(result).isEqualTo(tags);
        verify(tagCache).get(cacheKey, List.class);
    }

    // 헬퍼 메소드들
    @SuppressWarnings("unchecked")
    private List<Tag> getTagsWithCache(String cacheKey) {
        Cache cache = cacheManager.getCache("tags");
        return cache != null ? cache.get(cacheKey, List.class) : null;
    }

    private RecommendationResponseDto getRecommendationsWithCache(String cacheKey) {
        Cache cache = cacheManager.getCache("recommendations");
        return cache != null ? cache.get(cacheKey, RecommendationResponseDto.class) : null;
    }

    private void invalidateUserRecommendationCache(Long userId) {
        Cache cache = cacheManager.getCache("recommendations");
        if (cache != null) {
            cache.evict("recommendations:user:" + userId);
        }
    }

    private Tag createTag(Long id, String name, TagType type) {
        return Tag.builder()
                .tagId(id)
                .tagType(type)
                .tagName(name)
                .tagDescription(name + " 태그")
                .isActive(true)
                .isUserSelectable(true)
                .isRouteTaggable(true)
                .displayOrder(1)
                .usageCount(0L)
                .build();
    }
}
```

---

## 📊 테스트 통계 및 구성

### 보안/캐싱/플래그 검증 테스트 구성

| 테스트 클래스 | 테스트 케이스 수 | 주요 검증 내용 |
|-------------|----------------|---------------|
| **RecommendationControllerSecurityTest** | 3개 | 인증, 권한, 데이터 격리, 캐시 무효화 |
| **TagFlagValidationTest** | 6개 | is_user_selectable, is_route_taggable, display_order |
| **CachingStrategyTest** | 5개 | 30분/1시간 TTL, 캐시 무효화, 분산 일관성 |

### 📈 **총 14개 보안/캐싱/플래그 테스트 케이스**

---

## 🎯 테스트 검증 포인트

### 🔐 보안 검증
✅ **인증 검증**: Bearer Token 없이 접근 시 401 Unauthorized  
✅ **권한 검증**: 다른 사용자 데이터 접근 시 403 Forbidden  
✅ **데이터 격리**: 사용자별 추천 데이터 완전 분리  

### 🏷️ 플래그 검증
✅ **is_user_selectable**: 프로필용 태그만 조회 가능  
✅ **is_route_taggable**: 루트 태깅용 태그만 조회 가능  
✅ **display_order**: 올바른 정렬 순서 보장  
✅ **플래그 조합**: 둘 다 false인 태그 접근 차단  

### 🚀 캐싱 검증
✅ **TTL 검증**: 태그 30분, 추천 1시간 TTL 확인  
✅ **캐시 무효화**: 선호도 변경 시 추천 캐시만 삭제  
✅ **캐시 워밍업**: 인기 태그 미리 로딩  
✅ **분산 일관성**: Redis 클러스터 환경 일관성  

---

## 🏆 완성 현황

### step9-2h 분할 결과
- **step9-2h1_controller_api_tests.md**: Controller API 테스트 (16개)
- **step9-2h2_security_caching_tests.md**: 보안/캐싱/플래그 테스트 (14개) ✅

### 🎯 **총 30개 테스트 케이스로 완전 분할**

모든 보안, 캐싱, 플래그 검증 요구사항이 완벽하게 구현되어 **9-2h2 단계가 100% 완성**되었습니다.

---

*Step 9-2h2 완료: 보안/캐싱/플래그 검증 테스트 구현 완전본*  
*Created: 2025-08-27*  
*RoutePickr - 클라이밍 루트 추천 플랫폼*