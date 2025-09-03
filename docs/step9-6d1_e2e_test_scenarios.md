# E2E 테스트 시나리오 설계

## 개요
RoutePickr 클라이밍 루트 추천 플랫폼의 End-to-End 테스트 시나리오를 설계합니다. 실제 사용자의 여정을 시뮬레이션하여 전체 시스템의 통합성을 검증합니다.

## 핵심 사용자 여정 (User Journey)

### 1. 신규 사용자 가입 여정

```java
/**
 * 시나리오: 새로운 클라이머의 첫 번째 앱 사용 경험
 * 
 * 1. 회원가입 (이메일 인증 포함)
 * 2. 프로필 설정 (클라이밍 레벨, 선호 스타일)
 * 3. 주변 암장 검색
 * 4. 첫 번째 루트 추천 받기
 * 5. 루트 상세 정보 확인
 * 6. 클라이밍 기록 저장
 */
@Test
@DisplayName("[E2E] 신규 사용자 완전한 가입-이용 여정")
void newUserCompleteJourney() {
    // 1단계: 회원가입
    SignupRequestDto signupRequest = createNewUserSignupRequest();
    AuthResponseDto authResponse = authController.signup(signupRequest);
    assertThat(authResponse.getUser().getEmailVerified()).isFalse();
    
    // 2단계: 이메일 인증
    String verificationCode = getVerificationCodeFromEmail(signupRequest.getEmail());
    emailController.verifyEmail(signupRequest.getEmail(), verificationCode);
    
    // 3단계: 프로필 완성
    UserProfileUpdateRequestDto profileRequest = UserProfileUpdateRequestDto.builder()
            .nickName("신규클라이머")
            .climbingLevel("V2")
            .preferredStyles(Arrays.asList("BOULDERING", "SPORT"))
            .height(170)
            .weight(65)
            .build();
    
    userController.updateProfile(authResponse.getUser().getUserId(), profileRequest);
    
    // 4단계: 위치 기반 암장 검색
    GymSearchRequestDto gymSearchRequest = GymSearchRequestDto.builder()
            .latitude(37.5665)
            .longitude(126.9780)
            .radius(10.0)
            .build();
    
    List<GymDto> nearbyGyms = gymController.searchGyms(gymSearchRequest);
    assertThat(nearbyGyms).isNotEmpty();
    
    // 5단계: 첫 번째 암장 선택 및 루트 추천
    GymDto selectedGym = nearbyGyms.get(0);
    RecommendationRequestDto recommendationRequest = RecommendationRequestDto.builder()
            .userId(authResponse.getUser().getUserId())
            .gymId(selectedGym.getGymId())
            .difficulty("V2")
            .build();
    
    List<RouteRecommendationDto> recommendations = recommendationController
            .getPersonalizedRecommendations(recommendationRequest);
    assertThat(recommendations).hasSizeGreaterThan(0);
    
    // 6단계: 추천 루트 상세 정보 확인
    RouteRecommendationDto firstRecommendation = recommendations.get(0);
    RouteDetailDto routeDetail = routeController.getRouteDetail(
            firstRecommendation.getRouteId());
    assertThat(routeDetail).isNotNull();
    assertThat(routeDetail.getTags()).isNotEmpty();
    
    // 7단계: 클라이밍 시도 및 기록
    ClimbingRecordRequestDto climbingRecord = ClimbingRecordRequestDto.builder()
            .userId(authResponse.getUser().getUserId())
            .routeId(firstRecommendation.getRouteId())
            .isCompleted(true)
            .attempts(3)
            .climbingDate(LocalDateTime.now())
            .notes("첫 번째 V2 완등!")
            .build();
    
    ClimbingRecordDto savedRecord = climbingController.recordClimbing(climbingRecord);
    assertThat(savedRecord.getIsCompleted()).isTrue();
    
    // 8단계: 사용자 통계 확인
    UserStatisticsDto userStats = userController.getUserStatistics(
            authResponse.getUser().getUserId());
    assertThat(userStats.getTotalClimbs()).isEqualTo(1);
    assertThat(userStats.getCompletedRoutes()).isEqualTo(1);
}
```

### 2. 기존 사용자 일상 이용 여정

```java
@Test
@DisplayName("[E2E] 기존 사용자 일상적 앱 사용 패턴")
void existingUserDailyUsage() {
    // 0. 기존 사용자 로그인
    LoginRequestDto loginRequest = createExistingUserLoginRequest();
    AuthResponseDto authResponse = authController.login(loginRequest);
    Long userId = authResponse.getUser().getUserId();
    
    // 1. 오늘의 추천 루트 확인
    List<RouteRecommendationDto> dailyRecommendations = 
            recommendationController.getDailyRecommendations(userId);
    assertThat(dailyRecommendations).hasSizeGreaterThanOrEqualTo(5);
    
    // 2. 친구 활동 피드 확인
    List<ActivityFeedDto> friendActivities = 
            communityController.getFriendActivities(userId);
    assertThat(friendActivities).isNotEmpty();
    
    // 3. 새로운 루트에 대한 댓글 작성
    Long targetRouteId = dailyRecommendations.get(0).getRouteId();
    CommentCreateRequestDto commentRequest = CommentCreateRequestDto.builder()
            .routeId(targetRouteId)
            .content("이 루트 정말 좋네요! 홀딩이 재미있어요 👍")
            .build();
    
    CommentResponseDto comment = routeController.addRouteComment(commentRequest);
    assertThat(comment.getContent()).contains("정말 좋네요");
    
    // 4. 루트 즐겨찾기 추가
    routeController.toggleBookmark(userId, targetRouteId);
    List<RouteDto> bookmarkedRoutes = userController.getBookmarkedRoutes(userId);
    assertThat(bookmarkedRoutes).anyMatch(route -> 
            route.getRouteId().equals(targetRouteId));
    
    // 5. 클라이밍 세션 기록 (여러 루트)
    for (int i = 0; i < 3; i++) {
        ClimbingRecordRequestDto sessionRecord = ClimbingRecordRequestDto.builder()
                .userId(userId)
                .routeId(dailyRecommendations.get(i).getRouteId())
                .isCompleted(i < 2) // 2개 완등, 1개 미완등
                .attempts(i + 2)
                .climbingDate(LocalDateTime.now())
                .build();
        
        climbingController.recordClimbing(sessionRecord);
    }
    
    // 6. 세션 후 통계 확인 및 성과 공유
    UserStatisticsDto updatedStats = userController.getUserStatistics(userId);
    
    PostCreateRequestDto sharePost = PostCreateRequestDto.builder()
            .userId(userId)
            .title("오늘 세션 결과")
            .content(String.format("오늘 3개 루트 중 2개 완등! 총 완등 수: %d개", 
                    updatedStats.getCompletedRoutes()))
            .isPublic(true)
            .build();
    
    communityController.createPost(sharePost);
}
```

### 3. 소셜 기능 활용 여정

```java
@Test
@DisplayName("[E2E] 커뮤니티 및 소셜 기능 완전 활용")
void socialFeaturesCompleteUsage() {
    // 1. 두 명의 사용자 준비
    AuthResponseDto user1 = createAndLoginUser("climber1@example.com", "클라이머1");
    AuthResponseDto user2 = createAndLoginUser("climber2@example.com", "클라이머2");
    
    // 2. User1이 흥미로운 루트 발견 및 포스팅
    RouteDto interestingRoute = findInterestingRoute();
    
    PostCreateRequestDto routePost = PostCreateRequestDto.builder()
            .userId(user1.getUser().getUserId())
            .title("숨겨진 보석 루트 발견!")
            .content("오늘 발견한 정말 재미있는 V4 루트입니다. 크림핑과 다이나믹한 무브가 조화롭게 어우러져요.")
            .routeId(interestingRoute.getRouteId())
            .isPublic(true)
            .build();
    
    PostResponseDto createdPost = communityController.createPost(routePost);
    
    // 3. 포스트에 이미지 첨부
    String imagePath = uploadTestClimbingImage();
    communityController.addPostImage(createdPost.getPostId(), imagePath);
    
    // 4. User2가 포스트 발견 및 좋아요
    communityController.togglePostLike(user2.getUser().getUserId(), createdPost.getPostId());
    
    // 5. User2가 댓글 작성
    CommentCreateRequestDto comment = CommentCreateRequestDto.builder()
            .postId(createdPost.getPostId())
            .userId(user2.getUser().getUserId())
            .content("우와! 저도 이 루트 도전해보고 싶어요. 어느 암장에 있나요?")
            .build();
    
    CommentResponseDto createdComment = communityController.createComment(comment);
    
    // 6. User1이 댓글에 답글
    CommentCreateRequestDto reply = CommentCreateRequestDto.builder()
            .postId(createdPost.getPostId())
            .parentId(createdComment.getCommentId())
            .userId(user1.getUser().getUserId())
            .content("클라임존 강남점에 있어요! 함께 가실래요?")
            .build();
    
    communityController.createComment(reply);
    
    // 7. User2가 User1 팔로우
    userController.followUser(user2.getUser().getUserId(), user1.getUser().getUserId());
    
    // 8. User2가 해당 루트 도전 및 기록
    ClimbingRecordRequestDto challengeRecord = ClimbingRecordRequestDto.builder()
            .userId(user2.getUser().getUserId())
            .routeId(interestingRoute.getRouteId())
            .isCompleted(false)
            .attempts(5)
            .climbingDate(LocalDateTime.now())
            .notes("아직 완등하지 못했지만 재미있는 루트네요!")
            .build();
    
    climbingController.recordClimbing(challengeRecord);
    
    // 9. User2가 후기 포스팅
    PostCreateRequestDto followUpPost = PostCreateRequestDto.builder()
            .userId(user2.getUser().getUserId())
            .title("클라이머1님 추천 루트 도전!")
            .content("아직 완등하지는 못했지만 정말 좋은 루트 추천해주셔서 감사해요! 다음에 꼭 완등하겠습니다.")
            .parentPostId(createdPost.getPostId())
            .isPublic(true)
            .build();
    
    communityController.createPost(followUpPost);
    
    // 10. 알림 시스템 검증 - User1이 관련 알림들을 받았는지 확인
    List<NotificationDto> user1Notifications = 
            notificationController.getUserNotifications(user1.getUser().getUserId());
    
    assertThat(user1Notifications).anyMatch(notification -> 
            notification.getType() == NotificationType.POST_COMMENT &&
            notification.getContent().contains("댓글을 작성했습니다"));
    
    assertThat(user1Notifications).anyMatch(notification -> 
            notification.getType() == NotificationType.NEW_FOLLOWER &&
            notification.getContent().contains("팔로우하기 시작했습니다"));
}
```

### 4. 결제 및 프리미엄 기능 여정

```java
@Test
@DisplayName("[E2E] 프리미엄 구독 및 결제 완전한 플로우")
void premiumSubscriptionFullFlow() {
    // 1. 무료 사용자 로그인
    AuthResponseDto user = createAndLoginUser("premium_candidate@example.com", "프리미엄후보");
    Long userId = user.getUser().getUserId();
    
    // 2. 무료 사용자 제한 확인 (일일 추천 제한)
    List<RouteRecommendationDto> freeRecommendations = 
            recommendationController.getDailyRecommendations(userId);
    assertThat(freeRecommendations).hasSizeLessThanOrEqualTo(10); // 무료 사용자 제한
    
    // 3. 프리미엄 기능 소개 페이지 조회
    PremiumFeatureDto premiumFeatures = 
            paymentController.getPremiumFeatures();
    assertThat(premiumFeatures.getFeatures()).contains("무제한 루트 추천");
    
    // 4. 프리미엄 구독 상품 선택
    SubscriptionPlanDto monthlyPlan = paymentController.getSubscriptionPlans()
            .stream()
            .filter(plan -> plan.getPlanType() == PlanType.MONTHLY)
            .findFirst()
            .orElseThrow();
    
    // 5. 결제 정보 입력 및 구독 시작
    PaymentRequestDto paymentRequest = PaymentRequestDto.builder()
            .userId(userId)
            .planId(monthlyPlan.getPlanId())
            .paymentMethod("CARD")
            .autoRenewal(true)
            .build();
    
    PaymentResponseDto paymentResult = paymentController.processSubscription(paymentRequest);
    assertThat(paymentResult.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    
    // 6. 프리미엄 권한 즉시 적용 확인
    UserDto updatedUser = userController.getUserProfile(userId);
    assertThat(updatedUser.getSubscriptionType()).isEqualTo(SubscriptionType.PREMIUM);
    
    // 7. 프리미엄 기능 사용 - 무제한 추천
    List<RouteRecommendationDto> premiumRecommendations = 
            recommendationController.getPremiumRecommendations(userId);
    assertThat(premiumRecommendations).hasSizeGreaterThan(10);
    
    // 8. 프리미엄 전용 고급 필터링 사용
    AdvancedFilterDto advancedFilter = AdvancedFilterDto.builder()
            .difficultyRange(Arrays.asList("V3", "V4", "V5"))
            .preferredTags(Arrays.asList("CRIMPING", "OVERHANG"))
            .excludeCompletedRoutes(true)
            .similarityThreshold(0.8)
            .build();
    
    List<RouteRecommendationDto> filteredRecommendations = 
            recommendationController.getAdvancedRecommendations(userId, advancedFilter);
    assertThat(filteredRecommendations).allMatch(rec -> 
            rec.getDifficulty().matches("V[3-5]"));
    
    // 9. 프리미엄 분석 리포트 조회
    ClimbingAnalyticsDto analytics = 
            userController.getPremiumAnalytics(userId);
    assertThat(analytics.getProgressTrend()).isNotNull();
    assertThat(analytics.getWeaknessAnalysis()).isNotNull();
    
    // 10. 구독 관리 - 다음 결제일 확인
    SubscriptionDto subscription = 
            paymentController.getCurrentSubscription(userId);
    assertThat(subscription.getNextBillingDate()).isAfter(LocalDate.now());
    
    // 11. 프리미엄 혜택 알림 수신 확인
    List<NotificationDto> premiumNotifications = 
            notificationController.getPremiumNotifications(userId);
    assertThat(premiumNotifications).anyMatch(notification -> 
            notification.getContent().contains("프리미엄 구독이 활성화되었습니다"));
}
```

### 5. 오류 상황 처리 여정

```java
@Test
@DisplayName("[E2E] 다양한 오류 상황에서의 사용자 경험")
void errorHandlingUserExperience() {
    AuthResponseDto user = createAndLoginUser("error_test@example.com", "에러테스터");
    Long userId = user.getUser().getUserId();
    
    // 1. 네트워크 연결 불안정 상황 시뮬레이션
    simulateNetworkInstability();
    
    // 오프라인 모드에서 캐시된 데이터 사용
    List<RouteRecommendationDto> cachedRecommendations = 
            recommendationController.getCachedRecommendations(userId);
    assertThat(cachedRecommendations).isNotEmpty();
    
    // 2. 서버 부하 상황에서 우아한 성능 저하
    simulateHighServerLoad();
    
    long startTime = System.currentTimeMillis();
    List<RouteDto> routes = routeController.getPopularRoutes();
    long responseTime = System.currentTimeMillis() - startTime;
    
    // 응답은 느려질 수 있지만 실패하지는 않아야 함
    assertThat(routes).isNotEmpty();
    assertThat(responseTime).isLessThan(10000); // 10초 이내
    
    // 3. 잘못된 데이터 입력에 대한 친화적 오류 메시지
    SignupRequestDto invalidSignup = SignupRequestDto.builder()
            .email("invalid-email")
            .password("weak")
            .nickName("")
            .build();
    
    try {
        authController.signup(invalidSignup);
        fail("유효하지 않은 가입 정보로 가입이 성공해서는 안됩니다");
    } catch (ValidationException e) {
        assertThat(e.getErrors()).isNotEmpty();
        assertThat(e.getErrors()).anyMatch(error -> 
                error.getField().equals("email") && 
                error.getMessage().contains("올바른 이메일 형식"));
    }
    
    // 4. 존재하지 않는 리소스 접근
    try {
        routeController.getRouteDetail(99999L);
        fail("존재하지 않는 루트 조회가 성공해서는 안됩니다");
    } catch (ResourceNotFoundException e) {
        assertThat(e.getMessage()).contains("루트를 찾을 수 없습니다");
    }
    
    // 5. 권한 없는 작업 시도
    Long otherUserId = 99999L;
    try {
        userController.updateProfile(otherUserId, new UserProfileUpdateRequestDto());
        fail("권한 없는 프로필 수정이 성공해서는 안됩니다");
    } catch (UnauthorizedException e) {
        assertThat(e.getMessage()).contains("권한이 없습니다");
    }
    
    // 6. 복구 및 정상 동작 확인
    restoreNormalConditions();
    
    // 정상적인 추천 서비스 재개
    List<RouteRecommendationDto> normalRecommendations = 
            recommendationController.getDailyRecommendations(userId);
    assertThat(normalRecommendations).hasSizeGreaterThanOrEqualTo(5);
}
```

## 성능 중심 E2E 시나리오

### 6. 대용량 트래픽 처리

```java
@Test
@DisplayName("[E2E] 동시 사용자 1000명 시뮬레이션")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
void highConcurrencyUserSimulation() {
    int concurrentUsers = 1000;
    ExecutorService executor = Executors.newFixedThreadPool(100);
    CountDownLatch latch = new CountDownLatch(concurrentUsers);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);
    
    // 1000명의 사용자가 동시에 시스템 이용
    for (int i = 0; i < concurrentUsers; i++) {
        final int userIndex = i;
        executor.submit(() -> {
            try {
                // 각 사용자의 전형적인 세션
                simulateTypicalUserSession(userIndex);
                successCount.incrementAndGet();
            } catch (Exception e) {
                errorCount.incrementAndGet();
                System.err.printf("사용자 %d 세션 실패: %s%n", userIndex, e.getMessage());
            } finally {
                latch.countDown();
            }
        });
    }
    
    // 모든 세션 완료 대기
    latch.await(50, TimeUnit.SECONDS);
    executor.shutdown();
    
    // 성공률 95% 이상 보장
    double successRate = (double) successCount.get() / concurrentUsers;
    assertThat(successRate).isGreaterThanOrEqualTo(0.95);
    
    System.out.printf("동시성 테스트 결과: 성공 %d명, 실패 %d명, 성공률 %.2f%%\n", 
            successCount.get(), errorCount.get(), successRate * 100);
}

private void simulateTypicalUserSession(int userIndex) {
    // 로그인 또는 회원가입
    String email = String.format("concurrent_user_%d@example.com", userIndex);
    AuthResponseDto auth = loginOrSignup(email, "password123");
    
    // 루트 추천 받기
    List<RouteRecommendationDto> recommendations = 
            recommendationController.getDailyRecommendations(auth.getUser().getUserId());
    assertThat(recommendations).isNotEmpty();
    
    // 임의 루트 선택 및 상세 조회
    RouteRecommendationDto selectedRoute = recommendations.get(
            userIndex % recommendations.size());
    RouteDetailDto routeDetail = routeController.getRouteDetail(
            selectedRoute.getRouteId());
    assertThat(routeDetail).isNotNull();
    
    // 클라이밍 기록 (50% 확률로 완등)
    boolean completed = userIndex % 2 == 0;
    ClimbingRecordRequestDto record = ClimbingRecordRequestDto.builder()
            .userId(auth.getUser().getUserId())
            .routeId(selectedRoute.getRouteId())
            .isCompleted(completed)
            .attempts(ThreadLocalRandom.current().nextInt(1, 6))
            .climbingDate(LocalDateTime.now())
            .build();
    
    climbingController.recordClimbing(record);
}
```

### 7. 데이터 일관성 검증

```java
@Test
@DisplayName("[E2E] 복합 트랜잭션 데이터 일관성 보장")
void complexTransactionDataConsistency() {
    // 1. 다중 사용자 환경에서 동일한 루트에 대한 동시 작업
    AuthResponseDto user1 = createAndLoginUser("consistency1@test.com", "일관성테스트1");
    AuthResponseDto user2 = createAndLoginUser("consistency2@test.com", "일관성테스트2");
    
    RouteDto testRoute = createTestRoute();
    
    // 2. 동시에 루트 관련 작업 수행
    CompletableFuture<Void> user1Actions = CompletableFuture.runAsync(() -> {
        // User1: 루트 북마크 + 댓글 + 난이도 투표
        routeController.toggleBookmark(user1.getUser().getUserId(), testRoute.getRouteId());
        
        CommentCreateRequestDto comment = CommentCreateRequestDto.builder()
                .routeId(testRoute.getRouteId())
                .content("정말 좋은 루트네요!")
                .build();
        routeController.addRouteComment(comment);
        
        DifficultyVoteRequestDto vote = DifficultyVoteRequestDto.builder()
                .routeId(testRoute.getRouteId())
                .userId(user1.getUser().getUserId())
                .suggestedDifficulty("V4")
                .build();
        routeController.voteDifficulty(vote);
    });
    
    CompletableFuture<Void> user2Actions = CompletableFuture.runAsync(() -> {
        // User2: 루트 완등 기록 + 이미지 업로드 + 난이도 투표
        ClimbingRecordRequestDto record = ClimbingRecordRequestDto.builder()
                .userId(user2.getUser().getUserId())
                .routeId(testRoute.getRouteId())
                .isCompleted(true)
                .attempts(3)
                .climbingDate(LocalDateTime.now())
                .build();
        climbingController.recordClimbing(record);
        
        String imagePath = uploadTestImage();
        routeController.addRouteImage(testRoute.getRouteId(), imagePath);
        
        DifficultyVoteRequestDto vote = DifficultyVoteRequestDto.builder()
                .routeId(testRoute.getRouteId())
                .userId(user2.getUser().getUserId())
                .suggestedDifficulty("V3")
                .build();
        routeController.voteDifficulty(vote);
    });
    
    // 두 작업 동시 실행 및 완료 대기
    CompletableFuture.allOf(user1Actions, user2Actions).join();
    
    // 3. 데이터 일관성 검증
    RouteDetailDto finalRouteState = routeController.getRouteDetail(testRoute.getRouteId());
    
    // 북마크 수 정확성
    assertThat(finalRouteState.getBookmarkCount()).isEqualTo(1);
    
    // 댓글 수 정확성  
    assertThat(finalRouteState.getCommentCount()).isEqualTo(1);
    
    // 완등 수 정확성
    assertThat(finalRouteState.getCompletionCount()).isEqualTo(1);
    
    // 이미지 수 정확성
    assertThat(finalRouteState.getImages()).hasSize(1);
    
    // 난이도 투표 결과 정확성 (두 명의 투표 반영)
    assertThat(finalRouteState.getDifficultyVotes()).hasSize(2);
    
    // 4. 사용자별 통계 일관성 검증
    UserStatisticsDto user1Stats = userController.getUserStatistics(user1.getUser().getUserId());
    UserStatisticsDto user2Stats = userController.getUserStatistics(user2.getUser().getUserId());
    
    assertThat(user1Stats.getBookmarkedRoutes()).isEqualTo(1);
    assertThat(user1Stats.getTotalComments()).isEqualTo(1);
    
    assertThat(user2Stats.getCompletedRoutes()).isEqualTo(1);
    assertThat(user2Stats.getTotalClimbs()).isEqualTo(1);
}
```

## 테스트 실행 환경 설정

### Docker Compose 테스트 환경

```yaml
# docker-compose.e2e.yml
version: '3.8'
services:
  mysql-test:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: test_password
      MYSQL_DATABASE: routepick_test
    ports:
      - "3307:3306"
    volumes:
      - ./sql/init-test-data.sql:/docker-entrypoint-initdb.d/init-test-data.sql

  redis-test:
    image: redis:7.0
    ports:
      - "6380:6379"

  mailhog:
    image: mailhog/mailhog:latest
    ports:
      - "8026:8025"  # Web UI
      - "1026:1025"  # SMTP

  app:
    build: .
    ports:
      - "8081:8080"
    environment:
      SPRING_PROFILES_ACTIVE: e2e-test
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql-test:3306/routepick_test
      SPRING_REDIS_HOST: redis-test
      MAIL_HOST: mailhog
    depends_on:
      - mysql-test
      - redis-test
      - mailhog
```

### 실행 스크립트

```bash
#!/bin/bash
# run-e2e-tests.sh

echo "=== RoutePickr E2E 테스트 실행 ==="

# Docker 환경 구성
echo "1. Docker 환경 설정 중..."
docker-compose -f docker-compose.e2e.yml up -d

# 서비스 준비 대기
echo "2. 서비스 준비 대기 중..."
sleep 30

# E2E 테스트 실행
echo "3. E2E 테스트 실행 중..."
./gradlew test --tests="*E2ETest*" -Dspring.profiles.active=e2e-test

# 결과 확인
TEST_RESULT=$?

# 정리
echo "4. 테스트 환경 정리 중..."
docker-compose -f docker-compose.e2e.yml down

if [ $TEST_RESULT -eq 0 ]; then
    echo "✅ E2E 테스트 성공!"
else
    echo "❌ E2E 테스트 실패!"
    exit 1
fi
```

## 실행 및 모니터링

```bash
# E2E 테스트 실행
./run-e2e-tests.sh

# 특정 시나리오만 실행
./gradlew test --tests="*E2ETest.newUserCompleteJourney"

# 성능 중심 테스트만 실행  
./gradlew test --tests="*E2ETest.highConcurrencyUserSimulation"

# 상세 로그와 함께 실행
./gradlew test --tests="*E2ETest*" --info
```

### 성공 기준
- 모든 사용자 여정 시나리오 통과
- 95% 이상 성공률 (동시성 테스트)
- 응답 시간 SLA 준수
- 데이터 일관성 100% 보장
- 오류 상황에서 우아한 처리