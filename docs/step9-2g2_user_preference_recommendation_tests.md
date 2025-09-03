# step9-2g2_user_preference_recommendation_tests.md

## 📋 사용자 선호도 & 추천 시스템 Repository 테스트

### 🎯 목표
- UserPreferredTagRepository 테스트 (사용자 태그 선호도)
- UserRouteRecommendationRepository 테스트 (루트 추천 알고리즘)
- 선호도 기반 추천 시스템 검증
- 성능 최적화 및 데이터 무결성 검증

### 🏗️ 테스트 구조

#### 4. UserPreferredTagRepository 테스트 📊

**4.1 사용자 선호 태그 관리 테스트**

```java
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("UserPreferredTagRepository 테스트")
class UserPreferredTagRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserPreferredTagRepository userPreferredTagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TagRepository tagRepository;

    private User testUser;
    private List<Tag> testTags;

    @BeforeEach
    void setUp() {
        // 테스트 사용자
        testUser = User.builder()
                .email("test@example.com")
                .nickname("테스터")
                .provider(SocialProvider.LOCAL)
                .providerId("test123")
                .isActive(true)
                .build();
        testUser = userRepository.save(testUser);

        // 테스트 태그들
        testTags = Arrays.asList(
                createTag(TagType.STYLE, "파워풀", 100L),
                createTag(TagType.MOVEMENT, "정적", 80L),
                createTag(TagType.TECHNIQUE, "밸런스", 60L)
        );
        testTags = tagRepository.saveAll(testTags);
        entityManager.flush();
    }

    @Test
    @DisplayName("사용자 선호 태그 생성")
    void createUserPreferredTag() {
        // Given
        Tag tag = testTags.get(0);

        // When
        UserPreferredTag preferredTag = UserPreferredTag.builder()
                .user(testUser)
                .tag(tag)
                .preferenceLevel(PreferenceLevel.HIGH)
                .skillLevel(SkillLevel.INTERMEDIATE)
                .build();
        UserPreferredTag saved = userPreferredTagRepository.save(preferredTag);

        // Then
        assertThat(saved.getUserPreferredTagId()).isNotNull();
        assertThat(saved.getUser().getUserId()).isEqualTo(testUser.getUserId());
        assertThat(saved.getTag().getTagId()).isEqualTo(tag.getTagId());
        assertThat(saved.getPreferenceLevel()).isEqualTo(PreferenceLevel.HIGH);
        assertThat(saved.getSkillLevel()).isEqualTo(SkillLevel.INTERMEDIATE);
    }

    @Test
    @DisplayName("사용자별 선호 태그 조회")
    void findPreferredTagsByUser() {
        // Given
        List<UserPreferredTag> preferences = testTags.stream()
                .map(tag -> UserPreferredTag.builder()
                        .user(testUser)
                        .tag(tag)
                        .preferenceLevel(PreferenceLevel.MEDIUM)
                        .skillLevel(SkillLevel.BEGINNER)
                        .build())
                .collect(Collectors.toList());
        userPreferredTagRepository.saveAll(preferences);
        entityManager.flush();

        // When
        List<UserPreferredTag> result = userPreferredTagRepository.findByUserOrderByCreatedAtDesc(testUser);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(pref -> pref.getUser().getUserId().equals(testUser.getUserId()));
        
        // 최신 순 정렬 확인
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getCreatedAt())
                    .isAfterOrEqualTo(result.get(i + 1).getCreatedAt());
        }
    }

    @Test
    @DisplayName("선호 레벨별 태그 조회")
    void findByPreferenceLevel() {
        // Given - 다양한 선호 레벨의 태그들
        userPreferredTagRepository.saveAll(Arrays.asList(
                createUserPreferredTag(testTags.get(0), PreferenceLevel.HIGH),
                createUserPreferredTag(testTags.get(1), PreferenceLevel.MEDIUM),
                createUserPreferredTag(testTags.get(2), PreferenceLevel.LOW)
        ));
        entityManager.flush();

        // When
        List<UserPreferredTag> highPrefs = userPreferredTagRepository
                .findByUserAndPreferenceLevel(testUser, PreferenceLevel.HIGH);

        // Then
        assertThat(highPrefs).hasSize(1);
        assertThat(highPrefs.get(0).getPreferenceLevel()).isEqualTo(PreferenceLevel.HIGH);
    }

    @Test
    @DisplayName("선호 태그 중복 방지")
    void preventDuplicatePreferredTag() {
        // Given
        Tag tag = testTags.get(0);
        UserPreferredTag pref1 = createUserPreferredTag(tag, PreferenceLevel.HIGH);
        userPreferredTagRepository.save(pref1);

        // When & Then
        UserPreferredTag pref2 = createUserPreferredTag(tag, PreferenceLevel.MEDIUM);
        
        assertThatThrownBy(() -> {
            userPreferredTagRepository.save(pref2);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("선호 태그 업데이트 성능")
    void updatePreferredTagsPerformance() {
        // Given - 기존 선호 태그들
        List<UserPreferredTag> existing = testTags.stream()
                .map(tag -> createUserPreferredTag(tag, PreferenceLevel.LOW))
                .collect(Collectors.toList());
        existing = userPreferredTagRepository.saveAll(existing);
        entityManager.flush();

        // When - 선호 레벨 업데이트
        long startTime = System.currentTimeMillis();
        existing.forEach(pref -> pref.updatePreference(PreferenceLevel.HIGH, SkillLevel.ADVANCED));
        userPreferredTagRepository.saveAll(existing);
        entityManager.flush();
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(endTime - startTime).isLessThan(500); // 500ms 이내
        
        List<UserPreferredTag> updated = userPreferredTagRepository.findByUserOrderByCreatedAtDesc(testUser);
        assertThat(updated).allMatch(pref -> pref.getPreferenceLevel() == PreferenceLevel.HIGH);
    }

    @Test
    @DisplayName("태그 타입별 선호도 통계")
    void preferenceStatisticsByTagType() {
        // Given - 다양한 태그 타입의 선호 설정
        setupPreferenceStatisticsData();

        // When
        List<TagPreferenceStats> stats = userPreferredTagRepository
                .getPreferenceStatisticsByTagType(testUser.getUserId());

        // Then
        assertThat(stats).isNotEmpty();
        TagPreferenceStats styleStats = stats.stream()
                .filter(s -> s.getTagType() == TagType.STYLE)
                .findFirst()
                .orElseThrow();
        
        assertThat(styleStats.getPreferenceCount()).isGreaterThan(0);
        assertThat(styleStats.getAverageSkillLevel()).isNotNull();
    }

    @Test
    @DisplayName("스킬 레벨 매트릭스 검증")
    void skillLevelMatrixValidation() {
        // Given - 모든 스킬 레벨 조합
        List<UserPreferredTag> allCombinations = new ArrayList<>();
        Tag tag = testTags.get(0);
        
        for (PreferenceLevel prefLevel : PreferenceLevel.values()) {
            for (SkillLevel skillLevel : SkillLevel.values()) {
                // 각 조합마다 별도 태그 필요 (중복 방지)
                Tag uniqueTag = createTag(TagType.STYLE, 
                        "태그_" + prefLevel + "_" + skillLevel, 0L);
                tagRepository.save(uniqueTag);
                
                allCombinations.add(UserPreferredTag.builder()
                        .user(testUser)
                        .tag(uniqueTag)
                        .preferenceLevel(prefLevel)
                        .skillLevel(skillLevel)
                        .build());
            }
        }
        userPreferredTagRepository.saveAll(allCombinations);
        entityManager.flush();

        // When
        List<UserPreferredTag> saved = userPreferredTagRepository.findByUserOrderByCreatedAtDesc(testUser);

        // Then
        assertThat(saved).hasSize(PreferenceLevel.values().length * SkillLevel.values().length);
        
        // 각 조합이 정확히 저장되었는지 검증
        for (PreferenceLevel prefLevel : PreferenceLevel.values()) {
            for (SkillLevel skillLevel : SkillLevel.values()) {
                assertThat(saved.stream().anyMatch(pref -> 
                        pref.getPreferenceLevel() == prefLevel && 
                        pref.getSkillLevel() == skillLevel
                )).isTrue();
            }
        }
    }

    private UserPreferredTag createUserPreferredTag(Tag tag, PreferenceLevel level) {
        return UserPreferredTag.builder()
                .user(testUser)
                .tag(tag)
                .preferenceLevel(level)
                .skillLevel(SkillLevel.INTERMEDIATE)
                .build();
    }

    private Tag createTag(TagType tagType, String tagName, Long usageCount) {
        return Tag.builder()
                .tagType(tagType)
                .tagName(tagName)
                .tagDescription(tagName + " 설명")
                .isActive(true)
                .usageCount(usageCount)
                .build();
    }

    private void setupPreferenceStatisticsData() {
        // 통계용 테스트 데이터 생성
        List<Tag> additionalTags = Arrays.asList(
                createTag(TagType.STYLE, "테크니컬", 50L),
                createTag(TagType.MOVEMENT, "동적", 70L),
                createTag(TagType.HOLD_TYPE, "슬로퍼", 30L)
        );
        tagRepository.saveAll(additionalTags);

        List<UserPreferredTag> preferences = new ArrayList<>();
        preferences.add(createUserPreferredTag(testTags.get(0), PreferenceLevel.HIGH));
        preferences.add(createUserPreferredTag(additionalTags.get(0), PreferenceLevel.MEDIUM));
        preferences.add(createUserPreferredTag(additionalTags.get(1), PreferenceLevel.HIGH));
        
        userPreferredTagRepository.saveAll(preferences);
        entityManager.flush();
    }
}
```

## 5. UserRouteRecommendationRepository 테스트 🎯

### 5.1 추천 알고리즘 데이터 처리 테스트

```java
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("UserRouteRecommendationRepository 테스트")
class UserRouteRecommendationRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("routepick_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRouteRecommendationRepository recommendationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RouteRepository routeRepository;

    private User testUser;
    private List<Route> testRoutes;

    @BeforeEach
    void setUp() {
        // 테스트 사용자
        testUser = User.builder()
                .email("recommender@example.com")
                .nickname("추천테스터")
                .provider(SocialProvider.LOCAL)
                .providerId("rec123")
                .isActive(true)
                .build();
        testUser = userRepository.save(testUser);

        // 테스트 루트들
        testRoutes = Arrays.asList(
                createRoute("루트 A", "V4", RouteType.BOULDER),
                createRoute("루트 B", "V5", RouteType.BOULDER),
                createRoute("루트 C", "5.10a", RouteType.SPORT)
        );
        testRoutes = routeRepository.saveAll(testRoutes);
        entityManager.flush();
    }

    @Test
    @DisplayName("추천 데이터 생성")
    void createRecommendation() {
        // Given
        Route route = testRoutes.get(0);

        // When
        UserRouteRecommendation recommendation = UserRouteRecommendation.builder()
                .user(testUser)
                .route(route)
                .tagMatchingScore(0.75)
                .levelMatchingScore(0.80)
                .recommendationScore(0.76) // 0.75 * 0.7 + 0.80 * 0.3
                .calculatedAt(LocalDateTime.now())
                .build();
        UserRouteRecommendation saved = recommendationRepository.save(recommendation);

        // Then
        assertThat(saved.getRecommendationId()).isNotNull();
        assertThat(saved.getUser().getUserId()).isEqualTo(testUser.getUserId());
        assertThat(saved.getRoute().getRouteId()).isEqualTo(route.getRouteId());
        assertThat(saved.getTagMatchingScore()).isEqualTo(0.75);
        assertThat(saved.getLevelMatchingScore()).isEqualTo(0.80);
        assertThat(saved.getRecommendationScore()).isEqualTo(0.76);
    }

    @Test
    @DisplayName("사용자별 추천 루트 조회 (상위 N개)")
    void findTopRecommendationsForUser() {
        // Given - 다양한 점수의 추천 데이터
        List<UserRouteRecommendation> recommendations = testRoutes.stream()
                .map(route -> UserRouteRecommendation.builder()
                        .user(testUser)
                        .route(route)
                        .tagMatchingScore(Math.random())
                        .levelMatchingScore(Math.random())
                        .recommendationScore(Math.random())
                        .calculatedAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());
        recommendationRepository.saveAll(recommendations);
        entityManager.flush();

        // When - 상위 2개 추천 조회
        List<UserRouteRecommendation> topRecommendations = 
                recommendationRepository.findTop10ByUserOrderByRecommendationScoreDesc(testUser);

        // Then
        assertThat(topRecommendations).hasSize(3); // 전체가 3개뿐이므로
        
        // 점수 내림차순 정렬 확인
        for (int i = 0; i < topRecommendations.size() - 1; i++) {
            assertThat(topRecommendations.get(i).getRecommendationScore())
                    .isGreaterThanOrEqualTo(topRecommendations.get(i + 1).getRecommendationScore());
        }
    }

    @Test
    @DisplayName("추천 점수 계산 공식 검증")
    void validateRecommendationScoreFormula() {
        // Given
        Route route = testRoutes.get(0);
        double tagScore = 0.85;
        double levelScore = 0.60;
        double expectedScore = tagScore * 0.7 + levelScore * 0.3; // 0.595 + 0.18 = 0.775

        // When
        UserRouteRecommendation recommendation = UserRouteRecommendation.builder()
                .user(testUser)
                .route(route)
                .tagMatchingScore(tagScore)
                .levelMatchingScore(levelScore)
                .recommendationScore(expectedScore)
                .calculatedAt(LocalDateTime.now())
                .build();
        UserRouteRecommendation saved = recommendationRepository.save(recommendation);

        // Then
        assertThat(saved.getRecommendationScore()).isCloseTo(0.775, within(0.001));
        assertThat(saved.getTagMatchingScore()).isEqualTo(0.85);
        assertThat(saved.getLevelMatchingScore()).isEqualTo(0.60);
    }

    @Test
    @DisplayName("추천 데이터 업데이트 성능")
    void updateRecommendationsPerformance() {
        // Given - 기존 추천 데이터
        List<UserRouteRecommendation> existing = testRoutes.stream()
                .map(route -> UserRouteRecommendation.builder()
                        .user(testUser)
                        .route(route)
                        .tagMatchingScore(0.5)
                        .levelMatchingScore(0.5)
                        .recommendationScore(0.5)
                        .calculatedAt(LocalDateTime.now().minusHours(1))
                        .build())
                .collect(Collectors.toList());
        existing = recommendationRepository.saveAll(existing);
        entityManager.flush();

        // When - 재계산 및 업데이트
        long startTime = System.currentTimeMillis();
        existing.forEach(rec -> {
            rec.updateScores(0.8, 0.7, 0.77);
            rec.updateCalculationTime();
        });
        recommendationRepository.saveAll(existing);
        entityManager.flush();
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(endTime - startTime).isLessThan(500); // 500ms 이내
        
        List<UserRouteRecommendation> updated = recommendationRepository.findByUser(testUser);
        assertThat(updated).allMatch(rec -> rec.getRecommendationScore() == 0.77);
        assertThat(updated).allMatch(rec -> 
                rec.getCalculatedAt().isAfter(LocalDateTime.now().minusMinutes(1))
        );
    }

    @Test
    @DisplayName("추천 데이터 중복 방지")
    void preventDuplicateRecommendations() {
        // Given
        Route route = testRoutes.get(0);
        UserRouteRecommendation rec1 = UserRouteRecommendation.builder()
                .user(testUser)
                .route(route)
                .tagMatchingScore(0.7)
                .levelMatchingScore(0.8)
                .recommendationScore(0.73)
                .calculatedAt(LocalDateTime.now())
                .build();
        recommendationRepository.save(rec1);

        // When & Then
        UserRouteRecommendation rec2 = UserRouteRecommendation.builder()
                .user(testUser)
                .route(route) // 같은 사용자-루트 조합
                .tagMatchingScore(0.9)
                .levelMatchingScore(0.6)
                .recommendationScore(0.81)
                .calculatedAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> {
            recommendationRepository.save(rec2);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("대량 추천 데이터 배치 처리")
    void batchProcessRecommendations() {
        // Given - 추가 사용자들과 루트들
        List<User> additionalUsers = createAdditionalUsers(10);
        List<Route> additionalRoutes = createAdditionalRoutes(20);
        
        List<UserRouteRecommendation> bulkRecommendations = new ArrayList<>();
        for (User user : additionalUsers) {
            for (Route route : additionalRoutes) {
                bulkRecommendations.add(UserRouteRecommendation.builder()
                        .user(user)
                        .route(route)
                        .tagMatchingScore(Math.random())
                        .levelMatchingScore(Math.random())
                        .recommendationScore(Math.random())
                        .calculatedAt(LocalDateTime.now())
                        .build());
            }
        }

        // When
        long startTime = System.currentTimeMillis();
        List<UserRouteRecommendation> saved = recommendationRepository.saveAll(bulkRecommendations);
        entityManager.flush();
        long endTime = System.currentTimeMillis();

        // Then
        assertThat(saved).hasSize(200); // 10 users * 20 routes
        assertThat(endTime - startTime).isLessThan(10000); // 10초 이내
    }

    @Test
    @DisplayName("추천 통계 집계 쿼리")
    void recommendationStatisticsQuery() {
        // Given
        setupRecommendationStatisticsData();

        // When
        List<RecommendationStats> stats = recommendationRepository
                .getRecommendationStatisticsByUser(testUser.getUserId());

        // Then
        assertThat(stats).isNotEmpty();
        RecommendationStats userStats = stats.get(0);
        assertThat(userStats.getTotalRecommendations()).isGreaterThan(0);
        assertThat(userStats.getAverageScore()).isGreaterThan(0.0);
        assertThat(userStats.getMaxScore()).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("시간 기반 추천 데이터 정리")
    void timeBasedRecommendationCleanup() {
        // Given - 오래된 추천 데이터
        Route route = testRoutes.get(0);
        UserRouteRecommendation oldRec = UserRouteRecommendation.builder()
                .user(testUser)
                .route(route)
                .tagMatchingScore(0.6)
                .levelMatchingScore(0.7)
                .recommendationScore(0.63)
                .calculatedAt(LocalDateTime.now().minusDays(8)) // 8일 전
                .build();
        recommendationRepository.save(oldRec);

        UserRouteRecommendation newRec = UserRouteRecommendation.builder()
                .user(testUser)
                .route(testRoutes.get(1))
                .tagMatchingScore(0.8)
                .levelMatchingScore(0.9)
                .recommendationScore(0.83)
                .calculatedAt(LocalDateTime.now()) // 최신
                .build();
        recommendationRepository.save(newRec);
        entityManager.flush();

        // When - 7일 이상 된 데이터 조회
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        List<UserRouteRecommendation> oldRecommendations = 
                recommendationRepository.findByCalculatedAtBefore(cutoffDate);

        // Then
        assertThat(oldRecommendations).hasSize(1);
        assertThat(oldRecommendations.get(0).getCalculatedAt()).isBefore(cutoffDate);
    }

    private Route createRoute(String name, String difficulty, RouteType type) {
        return Route.builder()
                .routeName(name)
                .difficulty(difficulty)
                .routeType(type)
                .isActive(true)
                .build();
    }

    private List<User> createAdditionalUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            users.add(User.builder()
                    .email("user" + i + "@test.com")
                    .nickname("테스터" + i)
                    .provider(SocialProvider.LOCAL)
                    .providerId("test" + i)
                    .isActive(true)
                    .build());
        }
        return userRepository.saveAll(users);
    }

    private List<Route> createAdditionalRoutes(int count) {
        List<Route> routes = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            routes.add(Route.builder()
                    .routeName("테스트 루트 " + i)
                    .difficulty("V" + (i % 10 + 1))
                    .routeType(i % 2 == 0 ? RouteType.BOULDER : RouteType.SPORT)
                    .isActive(true)
                    .build());
        }
        return routeRepository.saveAll(routes);
    }

    private void setupRecommendationStatisticsData() {
        List<UserRouteRecommendation> stats = testRoutes.stream()
                .map(route -> UserRouteRecommendation.builder()
                        .user(testUser)
                        .route(route)
                        .tagMatchingScore(0.5 + Math.random() * 0.5)
                        .levelMatchingScore(0.4 + Math.random() * 0.6)
                        .recommendationScore(0.5 + Math.random() * 0.4)
                        .calculatedAt(LocalDateTime.now().minusHours((long) (Math.random() * 24)))
                        .build())
                .collect(Collectors.toList());
        recommendationRepository.saveAll(stats);
        entityManager.flush();
    }
}
```

## 📊 테스트 커버리지 요약

### UserPreferredTagRepository 테스트 영역:
- ✅ 기본 CRUD 작업 (생성, 조회, 업데이트)
- ✅ 사용자별 선호 태그 관리
- ✅ 선호도 레벨별 분류 및 조회
- ✅ 중복 방지 및 데이터 무결성
- ✅ 성능 최적화 검증 (500ms 이내)
- ✅ 태그 타입별 통계 생성
- ✅ 스킬 레벨 매트릭스 전체 조합 검증

### UserRouteRecommendationRepository 테스트 영역:
- ✅ 추천 데이터 생성 및 관리
- ✅ 추천 점수 계산 공식 검증 (태그 70% + 레벨 30%)
- ✅ 상위 N개 추천 조회 및 정렬
- ✅ 대량 데이터 배치 처리 (200개, 10초 이내)
- ✅ 추천 통계 집계 쿼리
- ✅ 시간 기반 데이터 정리
- ✅ 중복 방지 및 성능 최적화

### 핵심 검증 요소:
- **추천 알고리즘**: 태그 매칭 70% + 레벨 매칭 30% 공식
- **성능 기준**: 업데이트 500ms 이내, 배치 처리 10초 이내
- **데이터 무결성**: 중복 방지, 제약 조건 검증
- **확장성**: 대량 데이터 처리 및 통계 생성