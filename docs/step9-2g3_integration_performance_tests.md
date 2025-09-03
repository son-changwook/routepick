# step9-2g3_integration_performance_tests.md

## 📋 통합 성능 & Repository 테스트 최종 구성

### 🎯 목표
- 전체 Repository 통합 성능 테스트
- 동시성 처리 안정성 검증
- 대용량 데이터 처리 성능 확인
- 테스트 설정 및 유틸리티 구성

### 🏗️ 테스트 구조

## 6. 통합 Repository 성능 테스트 🚀

### 6.1 전체 Repository 협력 테스트

```java
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Repository 통합 성능 테스트")
class RepositoryIntegrationPerformanceTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql");

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private RouteTagRepository routeTagRepository;

    @Autowired
    private UserPreferredTagRepository userPreferredTagRepository;

    @Autowired
    private UserRouteRecommendationRepository recommendationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Test
    @DisplayName("추천 시스템 전체 데이터 플로우 성능")
    void fullRecommendationSystemPerformance() {
        // Given - 대량 테스트 데이터 설정
        List<Tag> tags = createLargeTags(100);
        List<User> users = createLargeUsers(50);
        List<Route> routes = createLargeRoutes(200);
        
        // Step 1: 태그 생성 (100개)
        long tagStart = System.currentTimeMillis();
        tagRepository.saveAll(tags);
        long tagEnd = System.currentTimeMillis();
        
        // Step 2: 루트-태그 매핑 (1000개)
        List<RouteTag> routeTags = createRouteTagMappings(routes, tags);
        long routeTagStart = System.currentTimeMillis();
        routeTagRepository.saveAll(routeTags);
        long routeTagEnd = System.currentTimeMillis();
        
        // Step 3: 사용자 선호 태그 (500개)
        List<UserPreferredTag> preferences = createUserPreferences(users, tags);
        long prefStart = System.currentTimeMillis();
        userPreferredTagRepository.saveAll(preferences);
        long prefEnd = System.currentTimeMillis();
        
        // Step 4: 추천 계산 (10000개)
        List<UserRouteRecommendation> recommendations = createRecommendations(users, routes);
        long recStart = System.currentTimeMillis();
        recommendationRepository.saveAll(recommendations);
        long recEnd = System.currentTimeMillis();

        // Then - 성능 검증 (각 단계별 시간 제한)
        assertThat(tagEnd - tagStart).isLessThan(2000); // 2초
        assertThat(routeTagEnd - routeTagStart).isLessThan(5000); // 5초
        assertThat(prefEnd - prefStart).isLessThan(3000); // 3초
        assertThat(recEnd - recStart).isLessThan(15000); // 15초

        // 데이터 정합성 검증
        assertThat(tagRepository.count()).isEqualTo(100);
        assertThat(routeTagRepository.count()).isEqualTo(1000);
        assertThat(userPreferredTagRepository.count()).isEqualTo(500);
        assertThat(recommendationRepository.count()).isEqualTo(10000);
    }

    @Test
    @DisplayName("복합 쿼리 성능 최적화 검증")
    void complexQueryPerformanceTest() {
        // Given
        setupComplexQueryTestData();
        
        // When - 복합 조회 쿼리들
        long start1 = System.currentTimeMillis();
        List<Tag> popularTags = tagRepository.findTop20ByIsActiveTrueOrderByUsageCountDesc();
        long time1 = System.currentTimeMillis() - start1;

        long start2 = System.currentTimeMillis();
        List<UserRouteRecommendation> topRecs = recommendationRepository
                .findTop50ByUserOrderByRecommendationScoreDesc(userRepository.findAll().get(0));
        long time2 = System.currentTimeMillis() - start2;

        long start3 = System.currentTimeMillis();
        List<RouteTag> routeTagStats = routeTagRepository
                .findByTagInAndRelevanceScoreGreaterThan(popularTags.subList(0, 10), 0.8);
        long time3 = System.currentTimeMillis() - start3;

        // Then
        assertThat(time1).isLessThan(500); // 500ms
        assertThat(time2).isLessThan(1000); // 1초
        assertThat(time3).isLessThan(1500); // 1.5초
        
        assertThat(popularTags).isNotEmpty();
        assertThat(topRecs).isNotEmpty();
        assertThat(routeTagStats).isNotEmpty();
    }

    @Test
    @DisplayName("동시성 처리 안정성 검증")
    void concurrencyStabilityTest() throws InterruptedException {
        // Given
        setupConcurrencyTestData();
        CountDownLatch latch = new CountDownLatch(10);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // When - 10개 스레드가 동시에 데이터 조작
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    // 각 스레드가 독립적인 데이터 생성/조회
                    User user = createUniqueUser("concurrent_user_" + threadId);
                    List<UserPreferredTag> prefs = createUserPreferences(List.of(user), 
                            tagRepository.findByIsActiveTrueOrderByUsageCountDesc().subList(0, 5));
                    userPreferredTagRepository.saveAll(prefs);
                    
                    // 추천 데이터 생성
                    List<Route> someRoutes = routeRepository.findAll().stream()
                            .limit(10)
                            .collect(Collectors.toList());
                    List<UserRouteRecommendation> recs = createRecommendations(List.of(user), someRoutes);
                    recommendationRepository.saveAll(recs);
                    
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Then
        latch.await(30, TimeUnit.SECONDS);
        assertThat(exceptions).isEmpty(); // 예외 없이 완료
        
        // 데이터 일관성 검증
        long userCount = userRepository.count();
        long prefCount = userPreferredTagRepository.count();
        long recCount = recommendationRepository.count();
        
        assertThat(userCount).isGreaterThanOrEqualTo(10);
        assertThat(prefCount).isGreaterThanOrEqualTo(50);
        assertThat(recCount).isGreaterThanOrEqualTo(100);
    }

    // 헬퍼 메소드들
    private List<Tag> createLargeTags(int count) {
        List<Tag> tags = new ArrayList<>();
        TagType[] tagTypes = TagType.values();
        
        for (int i = 1; i <= count; i++) {
            tags.add(Tag.builder()
                    .tagType(tagTypes[i % tagTypes.length])
                    .tagName("대량태그_" + i)
                    .tagDescription("대량 생성 태그 " + i)
                    .isActive(true)
                    .usageCount((long) (Math.random() * 1000))
                    .build());
        }
        return tagRepository.saveAll(tags);
    }

    private List<User> createLargeUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            users.add(User.builder()
                    .email("perf_user" + i + "@test.com")
                    .nickname("성능테스터" + i)
                    .provider(SocialProvider.LOCAL)
                    .providerId("perf" + i)
                    .isActive(true)
                    .build());
        }
        return userRepository.saveAll(users);
    }

    private List<Route> createLargeRoutes(int count) {
        List<Route> routes = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            routes.add(Route.builder()
                    .routeName("성능테스트루트_" + i)
                    .difficulty("V" + (i % 15 + 1))
                    .routeType(i % 2 == 0 ? RouteType.BOULDER : RouteType.SPORT)
                    .isActive(true)
                    .build());
        }
        return routeRepository.saveAll(routes);
    }

    private List<RouteTag> createRouteTagMappings(List<Route> routes, List<Tag> tags) {
        List<RouteTag> mappings = new ArrayList<>();
        for (Route route : routes) {
            // 각 루트당 5개의 랜덤 태그
            Collections.shuffle(tags);
            for (int i = 0; i < 5 && i < tags.size(); i++) {
                mappings.add(RouteTag.builder()
                        .route(route)
                        .tag(tags.get(i))
                        .relevanceScore(0.5 + Math.random() * 0.5)
                        .createdBy(1L)
                        .build());
            }
        }
        return mappings;
    }

    private List<UserPreferredTag> createUserPreferences(List<User> users, List<Tag> tags) {
        List<UserPreferredTag> preferences = new ArrayList<>();
        for (User user : users) {
            // 각 사용자당 10개의 랜덤 선호 태그
            Collections.shuffle(tags);
            for (int i = 0; i < 10 && i < tags.size(); i++) {
                preferences.add(UserPreferredTag.builder()
                        .user(user)
                        .tag(tags.get(i))
                        .preferenceLevel(PreferenceLevel.values()[(int) (Math.random() * 3)])
                        .skillLevel(SkillLevel.values()[(int) (Math.random() * 4)])
                        .build());
            }
        }
        return preferences;
    }

    private List<UserRouteRecommendation> createRecommendations(List<User> users, List<Route> routes) {
        List<UserRouteRecommendation> recommendations = new ArrayList<>();
        for (User user : users) {
            for (Route route : routes) {
                double tagScore = Math.random();
                double levelScore = Math.random();
                double finalScore = tagScore * 0.7 + levelScore * 0.3;
                
                recommendations.add(UserRouteRecommendation.builder()
                        .user(user)
                        .route(route)
                        .tagMatchingScore(tagScore)
                        .levelMatchingScore(levelScore)
                        .recommendationScore(finalScore)
                        .calculatedAt(LocalDateTime.now())
                        .build());
            }
        }
        return recommendations;
    }

    private User createUniqueUser(String identifier) {
        User user = User.builder()
                .email(identifier + "@test.com")
                .nickname(identifier)
                .provider(SocialProvider.LOCAL)
                .providerId(identifier)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    private void setupComplexQueryTestData() {
        List<Tag> tags = createLargeTags(50);
        List<User> users = createLargeUsers(10);
        List<Route> routes = createLargeRoutes(100);
        
        routeTagRepository.saveAll(createRouteTagMappings(routes, tags));
        userPreferredTagRepository.saveAll(createUserPreferences(users, tags));
        recommendationRepository.saveAll(createRecommendations(users, routes.subList(0, 50)));
    }

    private void setupConcurrencyTestData() {
        createLargeTags(20);
        createLargeRoutes(50);
    }
}
```

## 7. 테스트 설정 및 유틸리티 ⚙️

### 7.1 테스트 설정 파일

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:tc:mysql:8.0:///routepick_test
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
    
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        
  test:
    database:
      replace: none
      
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    com.routepick.backend: DEBUG
```

### 7.2 테스트 유틸리티 클래스

```java
@TestComponent
public class RepositoryTestUtils {
    
    public static final double RECOMMENDATION_TAG_WEIGHT = 0.7;
    public static final double RECOMMENDATION_LEVEL_WEIGHT = 0.3;
    
    public double calculateExpectedRecommendationScore(double tagScore, double levelScore) {
        return tagScore * RECOMMENDATION_TAG_WEIGHT + levelScore * RECOMMENDATION_LEVEL_WEIGHT;
    }
    
    public void assertRecommendationScoreCalculation(UserRouteRecommendation recommendation) {
        double expected = calculateExpectedRecommendationScore(
                recommendation.getTagMatchingScore(),
                recommendation.getLevelMatchingScore()
        );
        assertThat(recommendation.getRecommendationScore())
                .isCloseTo(expected, within(0.001));
    }
    
    public List<Tag> createTagsForAllTypes() {
        return Arrays.stream(TagType.values())
                .map(type -> Tag.builder()
                        .tagType(type)
                        .tagName(type.name().toLowerCase() + "_test")
                        .tagDescription("Test tag for " + type)
                        .isActive(true)
                        .usageCount(0L)
                        .build())
                .collect(Collectors.toList());
    }
    
    public void verifyQueryExecutionTime(Runnable query, long maxTimeMs) {
        long startTime = System.currentTimeMillis();
        query.run();
        long executionTime = System.currentTimeMillis() - startTime;
        assertThat(executionTime).isLessThan(maxTimeMs);
    }
}
```

## 8. 테스트 실행 및 검증 📊

### 8.1 테스트 커버리지 목표
- Repository 메소드 커버리지: **95% 이상**
- QueryDSL 쿼리 검증: **100%**
- 성능 임계값 준수: **100%**
- 동시성 안정성: **100%**

### 8.2 성능 기준 ⏱️
- **단순 CRUD**: 100ms 이내
- **복합 쿼리**: 1초 이내
- **대량 배치**: 10초 이내 (1만 건 기준)
- **동시성 테스트**: 30초 이내 완료

## 9. 총 테스트 케이스 수 📈

### 9.1 Repository별 테스트 수
- **TagRepository**: 7개 테스트 케이스 ✅
- **RouteTagRepository**: 8개 테스트 케이스 ✅
- **UserPreferredTagRepository**: 8개 테스트 케이스 ✅
- **UserRouteRecommendationRepository**: 9개 테스트 케이스 ✅
- **통합 성능 테스트**: 3개 테스트 케이스 ✅

### 9.2 총계
**총 35개 Repository 테스트 케이스** 🎯

### 9.3 성능 검증 항목
- **데이터 플로우 성능**: 4단계 처리 (태그→매핑→선호도→추천)
- **복합 쿼리 최적화**: 3가지 복합 쿼리 성능 검증
- **동시성 안정성**: 10개 스레드 동시 처리
- **대량 데이터**: 100개 태그, 50명 사용자, 200개 루트, 10000개 추천

### 9.4 핵심 검증 요소
- **추천 알고리즘**: 태그 매칭 70% + 레벨 매칭 30% 공식 검증
- **데이터 무결성**: 외래키 제약조건 및 중복 방지
- **성능 최적화**: 인덱스 활용 및 쿼리 최적화
- **확장성**: 대용량 데이터 처리 및 동시성 지원

## 🎯 최종 요약

이 문서는 RoutePickr 플랫폼의 태그 시스템 및 추천 알고리즘 Repository 테스트를 완전히 다룹니다:

1. **TagRepository & RouteTagRepository**: 기본 태그 관리 및 루트-태그 매핑
2. **UserPreferredTagRepository & UserRouteRecommendationRepository**: 사용자 선호도 및 추천 시스템
3. **통합 성능 테스트**: 전체 시스템 협력 및 대용량 처리 검증

총 35개의 포괄적인 테스트 케이스로 Repository 레이어의 완전한 검증을 제공합니다.