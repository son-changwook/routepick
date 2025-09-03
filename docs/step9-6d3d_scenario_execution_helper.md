# E2E 테스트 시나리오 실행 헬퍼

## 시나리오 실행 헬퍼

```java
package com.routepick.e2e.utils;

import com.routepick.auth.dto.response.AuthResponseDto;
import com.routepick.route.dto.response.RouteRecommendationDto;
import com.routepick.community.dto.response.PostResponseDto;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.LocalDateTime;

/**
 * E2E 테스트 시나리오 실행 헬퍼
 */
@Component
public class ScenarioExecutor {
    
    private final TestRestTemplate restTemplate;
    private final TestDataGenerator dataGenerator;
    private final AssertionHelper assertionHelper;
    
    public ScenarioExecutor(TestRestTemplate restTemplate, 
                           TestDataGenerator dataGenerator,
                           AssertionHelper assertionHelper) {
        this.restTemplate = restTemplate;
        this.dataGenerator = dataGenerator;
        this.assertionHelper = assertionHelper;
    }
    
    /**
     * 신규 사용자 온보딩 시나리오 실행
     */
    public AuthResponseDto executeNewUserOnboardingScenario(String baseUrl) {
        System.out.println("🚀 신규 사용자 온보딩 시나리오 시작");
        
        // 1. 회원가입
        var signupRequest = dataGenerator.createRandomSignupRequest();
        ResponseEntity<AuthResponseDto> signupResponse = restTemplate.postForEntity(
                baseUrl + "/api/auth/signup", signupRequest, AuthResponseDto.class);
        
        AuthResponseDto auth = signupResponse.getBody();
        assertionHelper.assertSignupSuccess(auth, signupRequest.getEmail());
        
        // 2. 이메일 인증 (시뮬레이션)
        HttpHeaders headers = createAuthHeaders(auth.getAccessToken());
        restTemplate.exchange(
                baseUrl + "/api/email/verify?email=" + signupRequest.getEmail() + "&code=123456",
                HttpMethod.POST, new HttpEntity<>(headers), String.class);
        
        // 3. 프로필 설정
        var profileRequest = dataGenerator.createProfileUpdateRequest();
        restTemplate.exchange(
                baseUrl + "/api/users/" + auth.getUser().getUserId() + "/profile",
                HttpMethod.PUT, new HttpEntity<>(profileRequest, headers), String.class);
        
        System.out.println("✅ 신규 사용자 온보딩 완료: " + signupRequest.getEmail());
        return auth;
    }
    
    /**
     * 클라이밍 세션 시나리오 실행
     */
    public void executeClimbingSessionScenario(String baseUrl, AuthResponseDto user, int routeCount) {
        System.out.printf("🧗 클라이밍 세션 시나리오 시작: %d개 루트\n", routeCount);
        
        HttpHeaders headers = createAuthHeaders(user.getAccessToken());
        
        // 추천 루트 받기
        ResponseEntity<RouteRecommendationDto[]> recommendationResponse = restTemplate.exchange(
                baseUrl + "/api/recommendations/daily/" + user.getUser().getUserId(),
                HttpMethod.GET, new HttpEntity<>(headers), RouteRecommendationDto[].class);
        
        RouteRecommendationDto[] recommendations = recommendationResponse.getBody();
        
        // 여러 루트 도전
        for (int i = 0; i < Math.min(routeCount, recommendations.length); i++) {
            var climbingRecord = dataGenerator.createClimbingRecordRequest(
                    user.getUser().getUserId(), recommendations[i].getRouteId());
            
            restTemplate.exchange(
                    baseUrl + "/api/climbing/records",
                    HttpMethod.POST, new HttpEntity<>(climbingRecord, headers), String.class);
        }
        
        System.out.println("✅ 클라이밍 세션 완료");
    }
    
    /**
     * 소셜 상호작용 시나리오 실행
     */
    public void executeSocialInteractionScenario(String baseUrl, AuthResponseDto user1, AuthResponseDto user2) {
        System.out.println("👥 소셜 상호작용 시나리오 시작");
        
        HttpHeaders headers1 = createAuthHeaders(user1.getAccessToken());
        HttpHeaders headers2 = createAuthHeaders(user2.getAccessToken());
        
        // User1이 포스트 작성
        var postRequest = dataGenerator.createPostCreateRequest(user1.getUser().getUserId());
        ResponseEntity<PostResponseDto> postResponse = restTemplate.exchange(
                baseUrl + "/api/community/posts",
                HttpMethod.POST, new HttpEntity<>(postRequest, headers1), PostResponseDto.class);
        
        PostResponseDto post = postResponse.getBody();
        
        // User2가 좋아요 및 댓글
        restTemplate.exchange(
                baseUrl + "/api/community/posts/" + post.getPostId() + "/like",
                HttpMethod.POST, new HttpEntity<>(headers2), String.class);
        
        var commentRequest = dataGenerator.createCommentCreateRequest(
                post.getPostId(), user2.getUser().getUserId());
        restTemplate.exchange(
                baseUrl + "/api/community/posts/" + post.getPostId() + "/comments",
                HttpMethod.POST, new HttpEntity<>(commentRequest, headers2), String.class);
        
        // User2가 User1 팔로우
        restTemplate.exchange(
                baseUrl + "/api/users/" + user1.getUser().getUserId() + "/follow",
                HttpMethod.POST, new HttpEntity<>(headers2), String.class);
        
        System.out.println("✅ 소셜 상호작용 완료");
    }
    
    /**
     * 동시 사용자 시뮬레이션
     */
    public ConcurrentTestResult executeConcurrentUserSimulation(String baseUrl, int userCount) {
        System.out.printf("⚡ 동시 사용자 시뮬레이션 시작: %d명\n", userCount);
        
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(userCount, 50)); // 최대 50개 스레드
        
        CompletableFuture<Boolean>[] futures = new CompletableFuture[userCount];
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < userCount; i++) {
            final int userIndex = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    executeTypicalUserSession(baseUrl, userIndex);
                    return true;
                } catch (Exception e) {
                    System.err.printf("사용자 %d 세션 실패: %s\n", userIndex, e.getMessage());
                    return false;
                }
            }, executor);
        }
        
        // 모든 작업 완료 대기
        int successCount = 0;
        int errorCount = 0;
        
        for (CompletableFuture<Boolean> future : futures) {
            try {
                if (future.join()) {
                    successCount++;
                } else {
                    errorCount++;
                }
            } catch (Exception e) {
                errorCount++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        executor.shutdown();
        
        ConcurrentTestResult result = new ConcurrentTestResult(
                userCount, successCount, errorCount, duration);
        
        assertionHelper.assertConcurrencyTestResults(userCount, successCount, errorCount);
        
        System.out.printf("✅ 동시 사용자 시뮬레이션 완료: %d명, %.2f%% 성공률, %dms 소요\n",
                userCount, result.getSuccessRate() * 100, duration);
        
        return result;
    }
    
    /**
     * 전형적인 사용자 세션 실행
     */
    private void executeTypicalUserSession(String baseUrl, int userIndex) {
        // 사용자 생성 및 로그인
        var signupRequest = dataGenerator.createSignupRequest(
                String.format("user%d@test.com", userIndex), "테스트사용자" + userIndex);
        
        ResponseEntity<AuthResponseDto> authResponse = restTemplate.postForEntity(
                baseUrl + "/api/auth/signup", signupRequest, AuthResponseDto.class);
        
        AuthResponseDto auth = authResponse.getBody();
        HttpHeaders headers = createAuthHeaders(auth.getAccessToken());
        
        // 추천 루트 조회
        restTemplate.exchange(
                baseUrl + "/api/recommendations/daily/" + auth.getUser().getUserId(),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        
        // 암장 검색
        restTemplate.exchange(
                baseUrl + "/api/gyms/search?latitude=37.5665&longitude=126.9780",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        
        // 클라이밍 기록 (랜덤)
        if (userIndex % 3 == 0) { // 1/3 확률로 클라이밍 기록
            var recordRequest = dataGenerator.createClimbingRecordRequest(
                    auth.getUser().getUserId(), 1L); // 테스트 루트 ID
            
            restTemplate.exchange(
                    baseUrl + "/api/climbing/records",
                    HttpMethod.POST, new HttpEntity<>(recordRequest, headers), String.class);
        }
    }
    
    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
    
    /**
     * 동시성 테스트 결과
     */
    public static class ConcurrentTestResult {
        private final int totalUsers;
        private final int successCount;
        private final int errorCount;
        private final long durationMs;
        
        public ConcurrentTestResult(int totalUsers, int successCount, int errorCount, long durationMs) {
            this.totalUsers = totalUsers;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.durationMs = durationMs;
        }
        
        public double getSuccessRate() {
            return (double) successCount / totalUsers;
        }
        
        public double getThroughput() {
            return (double) totalUsers / (durationMs / 1000.0);
        }
        
        // getters...
        public int getTotalUsers() { return totalUsers; }
        public int getSuccessCount() { return successCount; }
        public int getErrorCount() { return errorCount; }
        public long getDurationMs() { return durationMs; }
    }
}
```

---

*분할된 파일: step9-6d3_e2e_helper_utils.md → step9-6d3d_scenario_execution_helper.md*  
*내容: E2E 테스트 시나리오 실행 헬퍼 (ScenarioExecutor)*  
*라인 수: 262줄*