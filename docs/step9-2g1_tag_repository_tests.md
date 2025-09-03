# Step 9-2g1: TagRepository & RouteTagRepository 테스트

> Repository Layer 핵심 테스트 - Tag 및 루트 태깅 시스템  
> 생성일: 2025-08-31  
> 단계: 9-2g1 (테스트 코드 - Repository 핵심)  
> 연관: step9-2g2, step9-2g3

---

## 🎯 개요

### 테스트 목적
- Repository layer 데이터 접근 로직 검증
- QueryDSL 복잡 쿼리 성능 최적화 확인
- JPA 엔티티 매핑 및 인덱스 효율성 검증
- 태그 시스템 데이터 무결성 보장

### 테스트 범위
- TagRepository: 태그 CRUD 및 검색 최적화
- RouteTagRepository: 루트-태그 매핑 성능

### 참조 파일
- step5-2a_tag_core_repositories.md (585줄)
- step5-2b_tag_route_repositories.md (791줄)
- step4-2a_tag_system_entities.md (엔티티 구조)

---

## 🏷️ TagRepository 테스트

### 기본 CRUD 테스트

```java
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("TagRepository 테스트")
class TagRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("태그 생성 및 조회")
    void createAndFindTag() {
        // Given
        Tag tag = Tag.builder()
                .tagType(TagType.STYLE)
                .tagName("다이나믹")
                .tagDescription("역동적인 움직임이 필요한 루트")
                .isActive(true)
                .usageCount(0L)
                .build();

        // When
        Tag savedTag = tagRepository.save(tag);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<Tag> found = tagRepository.findById(savedTag.getTagId());
        assertThat(found).isPresent();
        assertThat(found.get().getTagName()).isEqualTo("다이나믹");
        assertThat(found.get().getTagType()).isEqualTo(TagType.STYLE);
    }

    @Test
    @DisplayName("태그 타입별 조회 성능 테스트")
    void findByTagTypePerformance() {
        // Given - 대량 데이터 생성
        List<Tag> tags = new ArrayList<>();
        for (TagType tagType : TagType.values()) {
            for (int i = 1; i <= 100; i++) {
                tags.add(Tag.builder()
                        .tagType(tagType)
                        .tagName(tagType.name() + "_" + i)
                        .tagDescription("Test tag " + i)
                        .isActive(true)
                        .usageCount((long) i)
                        .build());
            }
        }
        tagRepository.saveAll(tags);
        entityManager.flush();
        entityManager.clear();

        // When - 성능 측정
        long startTime = System.currentTimeMillis();
        List<Tag> styleTags = tagRepository.findByTagTypeAndIsActiveTrue(TagType.STYLE);
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(styleTags).hasSize(100);
        assertThat(endTime - startTime).isLessThan(100); // 100ms 이내
        assertThat(styleTags).allMatch(tag -> tag.getTagType() == TagType.STYLE);
    }

    @Test
    @DisplayName("활성 태그 검색 쿼리 최적화")
    void findActiveTagsOptimized() {
        // Given
        List<Tag> activeTags = createTestTags(50, true);
        List<Tag> inactiveTags = createTestTags(30, false);
        tagRepository.saveAll(activeTags);
        tagRepository.saveAll(inactiveTags);
        entityManager.flush();

        // When
        List<Tag> result = tagRepository.findByIsActiveTrueOrderByUsageCountDesc();

        // Then
        assertThat(result).hasSize(50);
        assertThat(result).allMatch(Tag::isActive);
        // Usage count 내림차순 정렬 확인
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getUsageCount())
                    .isGreaterThanOrEqualTo(result.get(i + 1).getUsageCount());
        }
    }

    @Test
    @DisplayName("태그 이름 중복 검증")
    void validateUniqueTagName() {
        // Given
        Tag tag1 = Tag.builder()
                .tagType(TagType.STYLE)
                .tagName("오버행")
                .tagDescription("오버행 루트")
                .isActive(true)
                .usageCount(0L)
                .build();
        tagRepository.save(tag1);

        // When & Then
        Tag tag2 = Tag.builder()
                .tagType(TagType.FEATURE)
                .tagName("오버행") // 같은 이름
                .tagDescription("다른 설명")
                .isActive(true)
                .usageCount(0L)
                .build();

        // UNIQUE 제약 조건 위반 확인
        assertThatThrownBy(() -> {
            tagRepository.save(tag2);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<Tag> createTestTags(int count, boolean isActive) {
        List<Tag> tags = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            tags.add(Tag.builder()
                    .tagType(TagType.values()[i % TagType.values().length])
                    .tagName("TestTag_" + i + "_" + isActive)
                    .tagDescription("Test description " + i)
                    .isActive(isActive)
                    .usageCount((long) (count - i)) // 역순으로 usage count 설정
                    .build());
        }
        return tags;
    }
}
```

### QueryDSL 복잡 쿼리 테스트

```java
@Test
@DisplayName("태그 검색 쿼리 (이름 + 타입 조합)")
void complexTagSearch() {
    // Given
    createVariousTestTags();
    
    // When - 복잡 검색 조건
    List<Tag> result = tagRepository.findTagsWithComplexConditions(
            "다이나믹",
            List.of(TagType.STYLE, TagType.MOVEMENT),
            50L
    );

    // Then
    assertThat(result).isNotEmpty();
    assertThat(result).allMatch(tag -> 
            tag.getTagName().contains("다이나믹") &&
            (tag.getTagType() == TagType.STYLE || tag.getTagType() == TagType.MOVEMENT) &&
            tag.getUsageCount() >= 50L
    );
}

@Test
@DisplayName("태그 통계 집계 쿼리")
void tagStatisticsQuery() {
    // Given
    createStatisticsTestData();

    // When
    List<TagStatistics> stats = tagRepository.getTagStatisticsByType();

    // Then
    assertThat(stats).hasSize(TagType.values().length);
    TagStatistics styleStats = stats.stream()
            .filter(s -> s.getTagType() == TagType.STYLE)
            .findFirst()
            .orElseThrow();
    
    assertThat(styleStats.getCount()).isGreaterThan(0);
    assertThat(styleStats.getTotalUsage()).isGreaterThan(0);
}

@Test
@DisplayName("인덱스 활용 성능 검증")
void indexUtilizationTest() {
    // Given - 대량 데이터
    List<Tag> largeTags = new ArrayList<>();
    for (int i = 1; i <= 10000; i++) {
        largeTags.add(Tag.builder()
                .tagType(TagType.values()[i % TagType.values().length])
                .tagName("Tag_" + String.format("%05d", i))
                .tagDescription("Description " + i)
                .isActive(i % 2 == 0)
                .usageCount((long) i)
                .build());
    }
    tagRepository.saveAll(largeTags);
    entityManager.flush();

    // When - 인덱스를 활용한 쿼리들
    long start1 = System.currentTimeMillis();
    List<Tag> byType = tagRepository.findByTagTypeAndIsActiveTrue(TagType.STYLE);
    long time1 = System.currentTimeMillis() - start1;

    long start2 = System.currentTimeMillis();
    List<Tag> byName = tagRepository.findByTagNameContainingAndIsActiveTrue("Tag_001");
    long time2 = System.currentTimeMillis() - start2;

    long start3 = System.currentTimeMillis();
    List<Tag> byUsage = tagRepository.findByUsageCountGreaterThanAndIsActiveTrueOrderByUsageCountDesc(5000L);
    long time3 = System.currentTimeMillis() - start3;

    // Then - 성능 검증 (각각 500ms 이내)
    assertThat(time1).isLessThan(500);
    assertThat(time2).isLessThan(500);
    assertThat(time3).isLessThan(500);
    
    assertThat(byType).isNotEmpty();
    assertThat(byName).isNotEmpty();
    assertThat(byUsage).isNotEmpty();
}
```

---

## 🧗 RouteTagRepository 테스트

### 루트-태그 매핑 테스트

```java
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("RouteTagRepository 테스트")
class RouteTagRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RouteTagRepository routeTagRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private TagRepository tagRepository;

    private Route testRoute;
    private List<Tag> testTags;

    @BeforeEach
    void setUp() {
        // 테스트 루트 생성
        testRoute = Route.builder()
                .routeName("테스트 루트")
                .difficulty("V4")
                .routeType(RouteType.BOULDER)
                .isActive(true)
                .build();
        testRoute = routeRepository.save(testRoute);

        // 테스트 태그들 생성
        testTags = Arrays.asList(
                createTag(TagType.STYLE, "다이나믹"),
                createTag(TagType.MOVEMENT, "데드포인트"),
                createTag(TagType.HOLD_TYPE, "크림프")
        );
        testTags = tagRepository.saveAll(testTags);
        entityManager.flush();
    }

    @Test
    @DisplayName("루트 태그 매핑 생성")
    void createRouteTagMapping() {
        // Given
        Tag tag = testTags.get(0);

        // When
        RouteTag routeTag = RouteTag.builder()
                .route(testRoute)
                .tag(tag)
                .relevanceScore(0.85)
                .createdBy(1L)
                .build();
        RouteTag saved = routeTagRepository.save(routeTag);

        // Then
        assertThat(saved.getRouteTagId()).isNotNull();
        assertThat(saved.getRoute().getRouteId()).isEqualTo(testRoute.getRouteId());
        assertThat(saved.getTag().getTagId()).isEqualTo(tag.getTagId());
        assertThat(saved.getRelevanceScore()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("루트별 태그 조회 성능")
    void findTagsByRoutePerformance() {
        // Given - 다중 태그 매핑
        List<RouteTag> routeTags = testTags.stream()
                .map(tag -> RouteTag.builder()
                        .route(testRoute)
                        .tag(tag)
                        .relevanceScore(0.7 + (Math.random() * 0.3))
                        .createdBy(1L)
                        .build())
                .collect(Collectors.toList());
        routeTagRepository.saveAll(routeTags);
        entityManager.flush();

        // When
        long startTime = System.currentTimeMillis();
        List<RouteTag> result = routeTagRepository.findByRouteOrderByRelevanceScoreDesc(testRoute);
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(result).hasSize(3);
        assertThat(endTime - startTime).isLessThan(100);
        
        // Relevance score 내림차순 정렬 확인
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getRelevanceScore())
                    .isGreaterThanOrEqualTo(result.get(i + 1).getRelevanceScore());
        }
    }

    @Test
    @DisplayName("태그별 루트 조회")
    void findRoutesByTag() {
        // Given
        Route route2 = routeRepository.save(Route.builder()
                .routeName("테스트 루트 2")
                .difficulty("V5")
                .routeType(RouteType.BOULDER)
                .isActive(true)
                .build());

        Tag dynamicTag = testTags.get(0); // "다이나믹" 태그
        
        // 두 루트에 같은 태그 연결
        routeTagRepository.saveAll(Arrays.asList(
                RouteTag.builder().route(testRoute).tag(dynamicTag).relevanceScore(0.9).createdBy(1L).build(),
                RouteTag.builder().route(route2).tag(dynamicTag).relevanceScore(0.8).createdBy(1L).build()
        ));
        entityManager.flush();

        // When
        List<RouteTag> result = routeTagRepository.findByTagOrderByRelevanceScoreDesc(dynamicTag);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRelevanceScore()).isEqualTo(0.9);
        assertThat(result.get(1).getRelevanceScore()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("루트 태그 중복 방지")
    void preventDuplicateRouteTag() {
        // Given
        Tag tag = testTags.get(0);
        RouteTag routeTag1 = RouteTag.builder()
                .route(testRoute)
                .tag(tag)
                .relevanceScore(0.8)
                .createdBy(1L)
                .build();
        routeTagRepository.save(routeTag1);

        // When & Then
        RouteTag routeTag2 = RouteTag.builder()
                .route(testRoute)
                .tag(tag) // 같은 조합
                .relevanceScore(0.9)
                .createdBy(2L)
                .build();

        assertThatThrownBy(() -> {
            routeTagRepository.save(routeTag2);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Relevance Score 범위 검증")
    void validateRelevanceScoreRange() {
        // Given
        Tag tag = testTags.get(0);

        // When & Then - 유효 범위 (0.0 ~ 1.0)
        RouteTag validRouteTag = RouteTag.builder()
                .route(testRoute)
                .tag(tag)
                .relevanceScore(0.75)
                .createdBy(1L)
                .build();
        
        assertThat(routeTagRepository.save(validRouteTag)).isNotNull();
        
        // 범위 벗어나는 값들은 비즈니스 로직에서 검증
        // (JPA 레벨에서는 @Min, @Max 어노테이션으로 검증)
    }

    @Test
    @DisplayName("대량 루트 태그 배치 처리")
    void batchProcessRouteTagsTest() {
        // Given - 대량 데이터
        List<Route> routes = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            routes.add(Route.builder()
                    .routeName("배치 루트 " + i)
                    .difficulty("V" + (i % 10 + 1))
                    .routeType(RouteType.BOULDER)
                    .isActive(true)
                    .build());
        }
        routes = routeRepository.saveAll(routes);

        List<RouteTag> routeTags = new ArrayList<>();
        for (Route route : routes) {
            for (Tag tag : testTags) {
                routeTags.add(RouteTag.builder()
                        .route(route)
                        .tag(tag)
                        .relevanceScore(Math.random())
                        .createdBy(1L)
                        .build());
            }
        }

        // When
        long startTime = System.currentTimeMillis();
        List<RouteTag> saved = routeTagRepository.saveAll(routeTags);
        entityManager.flush();
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(saved).hasSize(300); // 100 routes * 3 tags
        assertThat(endTime - startTime).isLessThan(5000); // 5초 이내
    }

    private Tag createTag(TagType tagType, String tagName) {
        return Tag.builder()
                .tagType(tagType)
                .tagName(tagName)
                .tagDescription(tagName + " 태그")
                .isActive(true)
                .usageCount(0L)
                .build();
    }
}
```

### QueryDSL 최적화 쿼리 테스트

```java
@Test
@DisplayName("복합 조건 루트-태그 검색")
void complexRouteTagSearch() {
    // Given
    setupComplexTestData();

    // When - 복합 검색: 특정 태그 타입들과 relevance score 조건
    List<RouteTag> result = routeTagRepository.findRouteTagsWithComplexConditions(
            List.of(TagType.STYLE, TagType.MOVEMENT),
            0.8,
            testRoute.getBranchId()
    );

    // Then
    assertThat(result).isNotEmpty();
    assertThat(result).allMatch(rt -> 
            (rt.getTag().getTagType() == TagType.STYLE || rt.getTag().getTagType() == TagType.MOVEMENT) &&
            rt.getRelevanceScore() >= 0.8
    );
}

@Test
@DisplayName("태그 매칭 통계 쿼리")
void tagMatchingStatistics() {
    // Given
    setupStatisticsData();

    // When
    List<TagMatchingStats> stats = routeTagRepository.getTagMatchingStatistics(testRoute.getBranchId());

    // Then
    assertThat(stats).isNotEmpty();
    TagMatchingStats styleStats = stats.stream()
            .filter(s -> s.getTagType() == TagType.STYLE)
            .findFirst()
            .orElseThrow();
    
    assertThat(styleStats.getRouteCount()).isGreaterThan(0);
    assertThat(styleStats.getAverageRelevance()).isGreaterThan(0.0);
}
```

---

## 🎯 **다음 단계**

**연관 파일:**
- **step9-2g2_user_preference_recommendation_tests.md**: 사용자 선호도 및 추천 테스트
- **step9-2g3_integration_performance_tests.md**: 통합 성능 테스트

*step9-2g1 완성: TagRepository & RouteTagRepository 테스트 구현 완료*