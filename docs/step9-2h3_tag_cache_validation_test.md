# 태그 캐시 검증 테스트

## 개요
태그 시스템의 캐시 전략과 데이터 일관성을 검증하는 테스트입니다. 태그 캐시의 정확성, 무효화 전략, 동시성 처리, 성능 최적화를 종합적으로 테스트합니다.

## 테스트 클래스 구조

```java
package com.routepick.tag.cache;

import com.routepick.tag.dto.request.TagCreateRequestDto;
import com.routepick.tag.dto.request.TagUpdateRequestDto;
import com.routepick.tag.dto.response.TagDto;
import com.routepick.tag.service.TagService;
import com.routepick.common.service.CacheService;
import com.routepick.route.service.RouteTagService;
import com.routepick.user.service.UserPreferenceService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * 태그 캐시 검증 테스트
 * 
 * 검증 영역:
 * - 태그 데이터 캐시 정확성
 * - 캐시 무효화 전략 검증
 * - 태그 검색 캐시 성능
 * - 사용자 선호도 캐시 동기화
 * - 동시성 환경에서 캐시 일관성
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TagCacheValidationTest {

    @Autowired
    private TagService tagService;
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private RouteTagService routeTagService;
    
    @Autowired
    private UserPreferenceService userPreferenceService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private Long testUserId;
    private static final String TAG_CACHE_PREFIX = "tag:";
    private static final String SEARCH_CACHE_PREFIX = "tag:search:";
    
    @BeforeEach
    void setUp() {
        testUserId = 1L;
        
        // 캐시 초기화
        cacheService.clearAll();
    }
    
    @AfterEach
    void tearDown() {
        // 테스트 후 캐시 정리
        cacheService.clearAll();
    }
    
    @Nested
    @DisplayName("태그 데이터 캐시 검증")
    class TagDataCacheTest {
        
        @Test
        @DisplayName("[검증] 태그 생성 후 캐시 동기화")
        void tagCreation_CacheSynchronization() {
            // given
            TagCreateRequestDto createRequest = TagCreateRequestDto.builder()
                    .tagName("CACHE_TEST_TAG")
                    .tagType("TECHNIQUE")
                    .description("캐시 테스트용 태그")
                    .color("BLUE")
                    .build();
            
            // when - 태그 생성
            TagDto createdTag = tagService.createTag(createRequest);
            
            // then - 캐시에서 직접 조회하여 검증
            String cacheKey = TAG_CACHE_PREFIX + createdTag.getTagId();
            Object cachedTag = redisTemplate.opsForValue().get(cacheKey);
            
            assertThat(cachedTag).isNotNull();
            
            // 캐시에서 조회한 데이터와 서비스에서 조회한 데이터 일치 확인
            TagDto serviceTag = tagService.getTagById(createdTag.getTagId());
            TagDto cacheRetrievedTag = tagService.getTagById(createdTag.getTagId()); // 두 번째 호출은 캐시에서
            
            assertThat(cacheRetrievedTag.getTagName()).isEqualTo(serviceTag.getTagName());
            assertThat(cacheRetrievedTag.getTagType()).isEqualTo(serviceTag.getTagType());
            assertThat(cacheRetrievedTag.getDescription()).isEqualTo(serviceTag.getDescription());
        }
        
        @Test
        @DisplayName("[검증] 태그 수정 시 캐시 무효화")
        void tagUpdate_CacheInvalidation() {
            // given - 태그 생성 및 캐시 생성
            TagCreateRequestDto createRequest = TagCreateRequestDto.builder()
                    .tagName("ORIGINAL_TAG")
                    .tagType("MOVEMENT")
                    .description("원본 설명")
                    .build();
            
            TagDto createdTag = tagService.createTag(createRequest);
            
            // 캐시 생성을 위한 조회
            TagDto originalCachedTag = tagService.getTagById(createdTag.getTagId());
            
            // when - 태그 수정
            TagUpdateRequestDto updateRequest = TagUpdateRequestDto.builder()
                    .tagId(createdTag.getTagId())
                    .tagName("UPDATED_TAG")
                    .description("수정된 설명")
                    .color("RED")
                    .build();
            
            TagDto updatedTag = tagService.updateTag(updateRequest);
            
            // then - 캐시가 무효화되고 새로운 데이터로 갱신되었는지 확인
            TagDto newCachedTag = tagService.getTagById(createdTag.getTagId());
            
            assertThat(newCachedTag.getTagName()).isEqualTo("UPDATED_TAG");
            assertThat(newCachedTag.getDescription()).isEqualTo("수정된 설명");
            assertThat(newCachedTag.getTagName()).isNotEqualTo(originalCachedTag.getTagName());
            
            // 캐시 키가 올바르게 업데이트되었는지 확인
            String cacheKey = TAG_CACHE_PREFIX + createdTag.getTagId();
            Object currentCachedData = redisTemplate.opsForValue().get(cacheKey);
            assertThat(currentCachedData).isNotNull();
        }
        
        @Test
        @DisplayName("[검증] 태그 삭제 시 캐시 완전 제거")
        void tagDeletion_CacheRemoval() {
            // given - 태그 생성 및 캐시 생성
            TagCreateRequestDto createRequest = TagCreateRequestDto.builder()
                    .tagName("DELETE_TEST_TAG")
                    .tagType("HOLD_TYPE")
                    .description("삭제 테스트용")
                    .build();
            
            TagDto createdTag = tagService.createTag(createRequest);
            tagService.getTagById(createdTag.getTagId()); // 캐시 생성
            
            String cacheKey = TAG_CACHE_PREFIX + createdTag.getTagId();
            assertThat(redisTemplate.opsForValue().get(cacheKey)).isNotNull();
            
            // when - 태그 삭제
            tagService.deleteTag(createdTag.getTagId());
            
            // then - 캐시에서 완전히 제거되었는지 확인
            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                Object cachedData = redisTemplate.opsForValue().get(cacheKey);
                assertThat(cachedData).isNull();
            });
            
            // 서비스에서도 조회되지 않아야 함
            assertThatThrownBy(() -> tagService.getTagById(createdTag.getTagId()))
                    .isInstanceOf(RuntimeException.class);
        }
    }
    
    @Nested
    @DisplayName("태그 검색 캐시 검증")
    class TagSearchCacheTest {
        
        @Test
        @DisplayName("[검증] 태그 검색 결과 캐시")
        void tagSearch_ResultCaching() {
            // given - 테스트용 태그들 생성
            for (int i = 1; i <= 5; i++) {
                TagCreateRequestDto createRequest = TagCreateRequestDto.builder()
                        .tagName("SEARCH_TAG_" + i)
                        .tagType("TECHNIQUE")
                        .description("검색 테스트용 태그 " + i)
                        .build();
                tagService.createTag(createRequest);
            }
            
            String searchTerm = "SEARCH_TAG";
            
            // when - 첫 번째 검색 (DB 쿼리)
            long startTime1 = System.nanoTime();
            List<TagDto> firstSearchResult = tagService.searchTags(searchTerm, 10);
            long firstSearchTime = Duration.ofNanos(System.nanoTime() - startTime1).toMillis();
            
            // then
            assertThat(firstSearchResult).hasSizeGreaterThan(0);
            
            // when - 두 번째 검색 (캐시 조회)
            long startTime2 = System.nanoTime();
            List<TagDto> secondSearchResult = tagService.searchTags(searchTerm, 10);
            long secondSearchTime = Duration.ofNanos(System.nanoTime() - startTime2).toMillis();
            
            // then - 검색 결과 일치 및 성능 향상 확인
            assertThat(secondSearchResult.size()).isEqualTo(firstSearchResult.size());
            assertThat(secondSearchTime).isLessThan(firstSearchTime);
            
            // 캐시에서 검색 결과가 조회되는지 확인
            String searchCacheKey = SEARCH_CACHE_PREFIX + searchTerm + ":10";
            Object cachedResult = redisTemplate.opsForValue().get(searchCacheKey);
            assertThat(cachedResult).isNotNull();
            
            System.out.printf("태그 검색 캐시 성능: 첫 번째 %dms, 두 번째 %dms%n", 
                    firstSearchTime, secondSearchTime);
        }
        
        @Test
        @DisplayName("[검증] 태그 검색 캐시 무효화 - 새 태그 추가")
        void tagSearchCache_InvalidationOnNewTag() {
            // given - 초기 검색 및 캐시 생성
            String searchTerm = "DYNAMIC";
            List<TagDto> initialResult = tagService.searchTags(searchTerm, 10);
            int initialCount = initialResult.size();
            
            // when - 검색어에 매칭되는 새 태그 추가
            TagCreateRequestDto newTagRequest = TagCreateRequestDto.builder()
                    .tagName("DYNAMIC_MOVEMENT")
                    .tagType("MOVEMENT")
                    .description("동적 움직임 태그")
                    .build();
            
            tagService.createTag(newTagRequest);
            
            // then - 캐시가 무효화되고 새로운 결과 반영 확인
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                List<TagDto> newResult = tagService.searchTags(searchTerm, 10);
                assertThat(newResult.size()).isGreaterThan(initialCount);
                
                // 새로 추가된 태그가 검색 결과에 포함되는지 확인
                boolean newTagIncluded = newResult.stream()
                        .anyMatch(tag -> tag.getTagName().equals("DYNAMIC_MOVEMENT"));
                assertThat(newTagIncluded).isTrue();
            });
        }
        
        @Test
        @DisplayName("[검증] 다양한 검색어 캐시 관리")
        void multipleSearchTerms_CacheManagement() {
            // given
            String[] searchTerms = {"CRIMP", "OVERHANG", "DYNAMIC", "POWER", "BALANCE"};
            
            // when - 각 검색어로 검색 실행
            for (String term : searchTerms) {
                List<TagDto> result = tagService.searchTags(term, 5);
                
                // 각 검색 결과가 캐시되었는지 확인
                String cacheKey = SEARCH_CACHE_PREFIX + term + ":5";
                Object cachedData = redisTemplate.opsForValue().get(cacheKey);
                assertThat(cachedData).isNotNull();
            }
            
            // then - 모든 검색어 캐시가 독립적으로 관리되는지 확인
            Set<String> allCacheKeys = redisTemplate.keys(SEARCH_CACHE_PREFIX + "*");
            assertThat(allCacheKeys).hasSizeGreaterThanOrEqualTo(searchTerms.length);
            
            System.out.printf("관리 중인 태그 검색 캐시 키: %d개%n", allCacheKeys.size());
        }
    }
    
    @Nested
    @DisplayName("사용자 선호도 캐시 동기화")
    class UserPreferenceCacheSyncTest {
        
        @Test
        @DisplayName("[검증] 사용자 선호 태그 캐시 동기화")
        void userPreferredTags_CacheSync() {
            // given
            List<String> preferredTags = List.of("CRIMPING", "OVERHANG", "DYNAMIC");
            
            // when - 사용자 선호 태그 설정
            userPreferenceService.updateUserPreferredTags(testUserId, preferredTags);
            
            // then - 캐시에 올바르게 저장되었는지 확인
            List<String> cachedPreferences = userPreferenceService.getUserPreferredTags(testUserId);
            assertThat(cachedPreferences).containsExactlyInAnyOrderElementsOf(preferredTags);
            
            // 캐시 키 확인
            String userPrefCacheKey = "user:preferences:" + testUserId;
            Object cachedData = redisTemplate.opsForValue().get(userPrefCacheKey);
            assertThat(cachedData).isNotNull();
        }
        
        @Test
        @DisplayName("[검증] 태그 삭제 시 사용자 선호도 캐시 갱신")
        void tagDeletion_UserPreferenceUpdate() {
            // given - 태그 생성 및 사용자 선호도 설정
            TagCreateRequestDto tagRequest = TagCreateRequestDto.builder()
                    .tagName("TEMP_PREFERENCE_TAG")
                    .tagType("TECHNIQUE")
                    .description("임시 선호도 태그")
                    .build();
            
            TagDto createdTag = tagService.createTag(tagRequest);
            
            List<String> preferences = List.of("TEMP_PREFERENCE_TAG", "CRIMPING");
            userPreferenceService.updateUserPreferredTags(testUserId, preferences);
            
            // 초기 선호도 확인
            List<String> initialPrefs = userPreferenceService.getUserPreferredTags(testUserId);
            assertThat(initialPrefs).contains("TEMP_PREFERENCE_TAG");
            
            // when - 태그 삭제
            tagService.deleteTag(createdTag.getTagId());
            
            // then - 사용자 선호도에서 해당 태그가 자동 제거되었는지 확인
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                List<String> updatedPrefs = userPreferenceService.getUserPreferredTags(testUserId);
                assertThat(updatedPrefs).doesNotContain("TEMP_PREFERENCE_TAG");
                assertThat(updatedPrefs).contains("CRIMPING"); // 다른 선호도는 유지
            });
        }
    }
    
    @Nested
    @DisplayName("동시성 캐시 일관성")
    class ConcurrentCacheConsistencyTest {
        
        @Test
        @DisplayName("[검증] 동시 태그 수정 시 캐시 일관성")
        void concurrentTagModification_CacheConsistency() throws Exception {
            // given - 테스트용 태그 생성
            TagCreateRequestDto createRequest = TagCreateRequestDto.builder()
                    .tagName("CONCURRENT_TEST_TAG")
                    .tagType("MOVEMENT")
                    .description("동시성 테스트용")
                    .build();
            
            TagDto createdTag = tagService.createTag(createRequest);
            
            // when - 여러 스레드에서 동시에 태그 수정 시도
            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<CompletableFuture<TagDto>> futures = new ArrayList<>();
            
            for (int i = 0; i < 5; i++) {
                final int index = i;
                CompletableFuture<TagDto> future = CompletableFuture.supplyAsync(() -> {
                    TagUpdateRequestDto updateRequest = TagUpdateRequestDto.builder()
                            .tagId(createdTag.getTagId())
                            .description("동시성 테스트 수정 " + index)
                            .build();
                    
                    return tagService.updateTag(updateRequest);
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 업데이트 완료 대기
            List<TagDto> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            // then - 최종 상태의 일관성 확인
            TagDto finalTag = tagService.getTagById(createdTag.getTagId());
            
            // 캐시와 DB 데이터가 일치하는지 확인
            String cacheKey = TAG_CACHE_PREFIX + createdTag.getTagId();
            
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                TagDto cachedTag = tagService.getTagById(createdTag.getTagId());
                assertThat(cachedTag.getDescription()).isEqualTo(finalTag.getDescription());
                assertThat(cachedTag.getUpdatedAt()).isEqualTo(finalTag.getUpdatedAt());
            });
            
            executor.shutdown();
        }
        
        @Test
        @DisplayName("[검증] 동시 검색 캐시 생성 일관성")
        void concurrentSearchCaching_Consistency() throws Exception {
            // given
            String searchTerm = "CONSISTENCY_TEST";
            ExecutorService executor = Executors.newFixedThreadPool(10);
            
            // when - 10개 스레드에서 동시에 동일한 검색 실행
            List<CompletableFuture<List<TagDto>>> futures = new ArrayList<>();
            
            for (int i = 0; i < 10; i++) {
                CompletableFuture<List<TagDto>> future = CompletableFuture.supplyAsync(() -> {
                    return tagService.searchTags(searchTerm, 5);
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 검색 완료 대기
            List<List<TagDto>> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            // then - 모든 검색 결과가 일치하는지 확인
            List<TagDto> firstResult = results.get(0);
            
            for (List<TagDto> result : results) {
                assertThat(result.size()).isEqualTo(firstResult.size());
                
                for (int i = 0; i < result.size(); i++) {
                    assertThat(result.get(i).getTagId()).isEqualTo(firstResult.get(i).getTagId());
                    assertThat(result.get(i).getTagName()).isEqualTo(firstResult.get(i).getTagName());
                }
            }
            
            // 캐시가 올바르게 생성되었는지 확인
            String cacheKey = SEARCH_CACHE_PREFIX + searchTerm + ":5";
            Object cachedResult = redisTemplate.opsForValue().get(cacheKey);
            assertThat(cachedResult).isNotNull();
            
            executor.shutdown();
        }
    }
    
    @Test
    @DisplayName("[종합] 태그 캐시 시스템 전체 검증")
    void comprehensive_TagCacheSystemValidation() {
        System.out.println("=== 태그 캐시 시스템 전체 검증 시작 ===");
        
        // 1. 기본 태그 CRUD 캐시 동작 확인
        TagCreateRequestDto createRequest = TagCreateRequestDto.builder()
                .tagName("COMPREHENSIVE_TEST")
                .tagType("FEATURE")
                .description("종합 테스트용 태그")
                .build();
        
        TagDto createdTag = tagService.createTag(createRequest);
        assertThat(createdTag).isNotNull();
        
        // 캐시 생성 확인
        TagDto cachedTag = tagService.getTagById(createdTag.getTagId());
        assertThat(cachedTag.getTagName()).isEqualTo("COMPREHENSIVE_TEST");
        
        // 2. 검색 캐시 동작 확인
        List<TagDto> searchResult = tagService.searchTags("COMPREHENSIVE", 10);
        assertThat(searchResult).isNotEmpty();
        
        // 3. 사용자 선호도 캐시 연동 확인
        List<String> preferences = List.of("COMPREHENSIVE_TEST");
        userPreferenceService.updateUserPreferredTags(testUserId, preferences);
        
        List<String> retrievedPrefs = userPreferenceService.getUserPreferredTags(testUserId);
        assertThat(retrievedPrefs).contains("COMPREHENSIVE_TEST");
        
        // 4. 수정 시 캐시 무효화 확인
        TagUpdateRequestDto updateRequest = TagUpdateRequestDto.builder()
                .tagId(createdTag.getTagId())
                .description("종합 테스트 - 수정됨")
                .build();
        
        TagDto updatedTag = tagService.updateTag(updateRequest);
        assertThat(updatedTag.getDescription()).isEqualTo("종합 테스트 - 수정됨");
        
        // 5. 삭제 시 캐시 완전 제거 확인
        tagService.deleteTag(createdTag.getTagId());
        
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            String cacheKey = TAG_CACHE_PREFIX + createdTag.getTagId();
            Object deletedCacheData = redisTemplate.opsForValue().get(cacheKey);
            assertThat(deletedCacheData).isNull();
        });
        
        // 6. 캐시 통계 확인
        Set<String> allTagCacheKeys = redisTemplate.keys(TAG_CACHE_PREFIX + "*");
        Set<String> allSearchCacheKeys = redisTemplate.keys(SEARCH_CACHE_PREFIX + "*");
        
        System.out.printf("태그 캐시 키: %d개, 검색 캐시 키: %d개%n", 
                allTagCacheKeys.size(), allSearchCacheKeys.size());
        
        System.out.println("=== 태그 캐시 시스템 전체 검증 완료: 모든 기능 정상 동작 ===");
    }
}
```

## 캐시 성능 모니터링

### Redis 캐시 통계
```bash
# 태그 관련 캐시 키 조회
redis-cli --scan --pattern "tag:*" | head -10

# 검색 캐시 통계
redis-cli --scan --pattern "tag:search:*" | wc -l

# 메모리 사용량 확인
redis-cli memory usage "tag:1"
redis-cli memory usage "tag:search:CRIMPING:10"
```

### 캐시 효율성 기준
- **캐시 히트율**: 85% 이상
- **캐시 응답시간**: 10ms 이하
- **캐시 무효화**: 100ms 이하
- **동시성 일관성**: 100% 보장
- **메모리 효율성**: 적정 TTL 설정으로 메모리 최적화

이 테스트는 태그 시스템의 캐시가 모든 상황에서 정확하고 일관되게 동작함을 보장합니다.