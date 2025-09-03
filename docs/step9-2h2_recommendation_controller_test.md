# 추천 컨트롤러 테스트

## 개요
RoutePickr의 핵심 기능인 루트 추천 시스템의 컨트롤러 계층을 테스트합니다. API 엔드포인트의 정확성, 성능, 보안성을 종합적으로 검증합니다.

## 테스트 클래스 구조

```java
package com.routepick.recommendation.controller;

import com.routepick.recommendation.controller.RecommendationController;
import com.routepick.recommendation.dto.request.RecommendationRequestDto;
import com.routepick.recommendation.dto.request.PreferenceUpdateRequestDto;
import com.routepick.recommendation.dto.response.RouteRecommendationDto;
import com.routepick.recommendation.dto.response.RecommendationAnalyticsDto;
import com.routepick.recommendation.service.RecommendationService;
import com.routepick.user.service.UserPreferenceService;
import com.routepick.common.security.JwtAuthenticationFilter;
import com.routepick.common.exception.BusinessException;
import com.routepick.common.exception.ErrorCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Arrays;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * 추천 컨트롤러 테스트
 * 
 * 테스트 범위:
 * - 개인화 추천 API
 * - 일일 추천 API
 * - 필터링 추천 API
 * - 추천 분석 API
 * - 사용자 선호도 관리 API
 * - 입력 검증 및 오류 처리
 */
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private RecommendationService recommendationService;
    
    @MockBean
    private UserPreferenceService userPreferenceService;
    
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    private Long testUserId;
    private Long testGymId;
    private RouteRecommendationDto sampleRecommendation;
    
    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testGymId = 1L;
        
        sampleRecommendation = RouteRecommendationDto.builder()
                .routeId(1L)
                .routeName("테스트 루트")
                .difficulty("V3")
                .gymName("테스트 암장")
                .score(0.85)
                .matchedTags(Arrays.asList("CRIMPING", "OVERHANG"))
                .estimatedSuccessRate(0.75)
                .description("개인화 추천 루트")
                .build();
    }
    
    @Nested
    @DisplayName("개인화 추천 API 테스트")
    class PersonalizedRecommendationTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 개인화 루트 추천 조회")
        void getPersonalizedRecommendations_Success() throws Exception {
            // given
            List<RouteRecommendationDto> recommendations = Arrays.asList(
                sampleRecommendation,
                RouteRecommendationDto.builder()
                    .routeId(2L)
                    .routeName("두 번째 추천 루트")
                    .difficulty("V2")
                    .gymName("테스트 암장")
                    .score(0.80)
                    .matchedTags(Arrays.asList("SLOPERS", "DYNAMIC"))
                    .estimatedSuccessRate(0.82)
                    .build()
            );
            
            given(recommendationService.getPersonalizedRecommendations(eq(testUserId), eq(10)))
                    .willReturn(recommendations);
            
            // when & then
            mockMvc.perform(get("/api/recommendations/personal/{userId}", testUserId)
                    .param("limit", "10")
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].routeId").value(1))
                    .andExpect(jsonPath("$[0].routeName").value("테스트 루트"))
                    .andExpect(jsonPath("$[0].difficulty").value("V3"))
                    .andExpect(jsonPath("$[0].score").value(0.85))
                    .andExpect(jsonPath("$[0].matchedTags").isArray())
                    .andExpect(jsonPath("$[0].matchedTags[0]").value("CRIMPING"))
                    .andExpect(jsonPath("$[0].estimatedSuccessRate").value(0.75))
                    .andExpect(jsonPath("$[1].routeId").value(2))
                    .andExpect(jsonPath("$[1].score").value(0.80));
            
            verify(recommendationService).getPersonalizedRecommendations(testUserId, 10);
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 암장별 개인화 추천")
        void getPersonalizedRecommendationsByGym_Success() throws Exception {
            // given
            given(recommendationService.getPersonalizedRecommendationsByGym(testUserId, testGymId, 5))
                    .willReturn(Arrays.asList(sampleRecommendation));
            
            // when & then
            mockMvc.perform(get("/api/recommendations/personal/{userId}/gym/{gymId}", testUserId, testGymId)
                    .param("limit", "5")
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].routeId").value(1))
                    .andExpect(jsonPath("$[0].gymName").value("테스트 암장"));
            
            verify(recommendationService).getPersonalizedRecommendationsByGym(testUserId, testGymId, 5);
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 존재하지 않는 사용자 추천 요청")
        void getPersonalizedRecommendations_UserNotFound() throws Exception {
            // given
            Long nonExistentUserId = 999L;
            given(recommendationService.getPersonalizedRecommendations(nonExistentUserId, 10))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));
            
            // when & then
            mockMvc.perform(get("/api/recommendations/personal/{userId}", nonExistentUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").exists());
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 잘못된 limit 파라미터")
        void getPersonalizedRecommendations_InvalidLimit() throws Exception {
            // when & then
            mockMvc.perform(get("/api/recommendations/personal/{userId}", testUserId)
                    .param("limit", "101") // 최대 100개 초과
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("추천 개수는 1~100 사이여야 합니다"));
        }
    }
    
    @Nested
    @DisplayName("일일 추천 API 테스트")
    class DailyRecommendationTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 일일 추천 루트 조회")
        void getDailyRecommendations_Success() throws Exception {
            // given
            List<RouteRecommendationDto> dailyRecommendations = Arrays.asList(
                sampleRecommendation,
                RouteRecommendationDto.builder()
                    .routeId(3L)
                    .routeName("오늘의 추천 루트")
                    .difficulty("V4")
                    .score(0.78)
                    .matchedTags(Arrays.asList("JUGS", "MANTLING"))
                    .build()
            );
            
            given(recommendationService.getDailyRecommendations(testUserId))
                    .willReturn(dailyRecommendations);
            
            // when & then
            mockMvc.perform(get("/api/recommendations/daily/{userId}", testUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].routeId").value(1))
                    .andExpect(jsonPath("$[1].routeId").value(3))
                    .andExpect(jsonPath("$[1].routeName").value("오늘의 추천 루트"));
            
            verify(recommendationService).getDailyRecommendations(testUserId);
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 빈 일일 추천 결과 처리")
        void getDailyRecommendations_EmptyResult() throws Exception {
            // given
            given(recommendationService.getDailyRecommendations(testUserId))
                    .willReturn(Arrays.asList());
            
            // when & then
            mockMvc.perform(get("/api/recommendations/daily/{userId}", testUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
    
    @Nested
    @DisplayName("필터링 추천 API 테스트")  
    class FilteredRecommendationTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 난이도 필터링 추천")
        void getFilteredRecommendations_ByDifficulty_Success() throws Exception {
            // given
            RecommendationRequestDto request = RecommendationRequestDto.builder()
                    .userId(testUserId)
                    .minDifficulty("V2")
                    .maxDifficulty("V4")
                    .gymId(testGymId)
                    .limit(15)
                    .build();
            
            given(recommendationService.getFilteredRecommendations(any(RecommendationRequestDto.class)))
                    .willReturn(Arrays.asList(sampleRecommendation));
            
            String requestBody = objectMapper.writeValueAsString(request);
            
            // when & then
            mockMvc.perform(post("/api/recommendations/filtered")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpected(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].difficulty").value("V3"));
            
            verify(recommendationService).getFilteredRecommendations(any(RecommendationRequestDto.class));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 태그 기반 필터링 추천")
        void getFilteredRecommendations_ByTags_Success() throws Exception {
            // given
            RecommendationRequestDto request = RecommendationRequestDto.builder()
                    .userId(testUserId)
                    .preferredTags(Arrays.asList("CRIMPING", "OVERHANG"))
                    .excludedTags(Arrays.asList("SLOPERS"))
                    .limit(10)
                    .build();
            
            given(recommendationService.getFilteredRecommendations(any(RecommendationRequestDto.class)))
                    .willReturn(Arrays.asList(sampleRecommendation));
            
            String requestBody = objectMapper.writeValueAsString(request);
            
            // when & then
            mockMvc.perform(post("/api/recommendations/filtered")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].matchedTags").isArray())
                    .andExpect(jsonPath("$[0].matchedTags[0]").value("CRIMPING"));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 잘못된 필터링 요청")
        void getFilteredRecommendations_InvalidRequest() throws Exception {
            // given - 잘못된 난이도 범위
            RecommendationRequestDto invalidRequest = RecommendationRequestDto.builder()
                    .userId(testUserId)
                    .minDifficulty("V5")
                    .maxDifficulty("V2") // min > max
                    .build();
            
            String requestBody = objectMapper.writeValueAsString(invalidRequest);
            
            // when & then
            mockMvc.perform(post("/api/recommendations/filtered")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("최소 난이도가 최대 난이도보다 높을 수 없습니다"));
        }
    }
    
    @Nested
    @DisplayName("추천 분석 API 테스트")
    class RecommendationAnalyticsTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 사용자 추천 분석 조회")
        void getRecommendationAnalytics_Success() throws Exception {
            // given
            RecommendationAnalyticsDto analytics = RecommendationAnalyticsDto.builder()
                    .totalRecommendations(150)
                    .completedRecommendations(45)
                    .successRate(0.30)
                    .preferredDifficulties(Arrays.asList("V2", "V3", "V4"))
                    .mostMatchedTags(Arrays.asList("CRIMPING", "OVERHANG", "DYNAMIC"))
                    .averageRecommendationScore(0.82)
                    .lastUpdated(LocalDateTime.now())
                    .build();
            
            given(recommendationService.getRecommendationAnalytics(testUserId))
                    .willReturn(analytics);
            
            // when & then
            mockMvc.perform(get("/api/recommendations/analytics/{userId}", testUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRecommendations").value(150))
                    .andExpect(jsonPath("$.completedRecommendations").value(45))
                    .andExpect(jsonPath("$.successRate").value(0.30))
                    .andExpect(jsonPath("$.preferredDifficulties").isArray())
                    .andExpect(jsonPath("$.preferredDifficulties.length()").value(3))
                    .andExpect(jsonPath("$.mostMatchedTags").isArray())
                    .andExpect(jsonPath("$.mostMatchedTags[0]").value("CRIMPING"))
                    .andExpect(jsonPath("$.averageRecommendationScore").value(0.82));
            
            verify(recommendationService).getRecommendationAnalytics(testUserId);
        }
        
        @Test
        @WithMockUser(username = "1", roles = "PREMIUM")
        @DisplayName("[성공] 프리미엄 사용자 고급 분석")
        void getPremiumAnalytics_Success() throws Exception {
            // given
            RecommendationAnalyticsDto premiumAnalytics = RecommendationAnalyticsDto.builder()
                    .totalRecommendations(300)
                    .weeklyProgress(Arrays.asList(10, 15, 12, 18, 20))
                    .difficultyProgression(Arrays.asList("V2", "V3", "V4", "V5"))
                    .personalizedInsights("최근 오버행 루트에서 좋은 성과를 보이고 있습니다.")
                    .build();
            
            given(recommendationService.getPremiumAnalytics(testUserId))
                    .willReturn(premiumAnalytics);
            
            // when & then
            mockMvc.perform(get("/api/recommendations/analytics/{userId}/premium", testUserId)
                    .with(jwt().authorities(() -> "ROLE_PREMIUM")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRecommendations").value(300))
                    .andExpect(jsonPath("$.weeklyProgress").isArray())
                    .andExpect(jsonPath("$.difficultyProgression").isArray())
                    .andExpect(jsonPath("$.personalizedInsights").value("최근 오버행 루트에서 좋은 성과를 보이고 있습니다."));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 일반 사용자의 프리미엄 분석 접근")
        void getPremiumAnalytics_AccessDenied() throws Exception {
            // when & then
            mockMvc.perform(get("/api/recommendations/analytics/{userId}/premium", testUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("ACCESS_DENIED"))
                    .andExpect(jsonPath("$.message").value("프리미엄 기능에 대한 접근 권한이 없습니다"));
        }
    }
    
    @Nested
    @DisplayName("사용자 선호도 관리 API 테스트")
    class UserPreferenceTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 사용자 선호도 업데이트")
        void updateUserPreferences_Success() throws Exception {
            // given
            PreferenceUpdateRequestDto request = PreferenceUpdateRequestDto.builder()
                    .userId(testUserId)
                    .preferredDifficulties(Arrays.asList("V3", "V4", "V5"))
                    .preferredTags(Arrays.asList("CRIMPING", "OVERHANG", "DYNAMIC"))
                    .excludedTags(Arrays.asList("SLOPERS"))
                    .preferredGymIds(Arrays.asList(1L, 2L, 3L))
                    .build();
            
            String requestBody = objectMapper.writeValueAsString(request);
            
            // when & then
            mockMvc.perform(put("/api/recommendations/preferences/{userId}", testUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("선호도가 성공적으로 업데이트되었습니다"));
            
            verify(userPreferenceService).updateUserPreferences(any(PreferenceUpdateRequestDto.class));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 사용자 선호도 조회")
        void getUserPreferences_Success() throws Exception {
            // given
            PreferenceUpdateRequestDto preferences = PreferenceUpdateRequestDto.builder()
                    .userId(testUserId)
                    .preferredDifficulties(Arrays.asList("V2", "V3"))
                    .preferredTags(Arrays.asList("CRIMPING", "JUGS"))
                    .excludedTags(Arrays.asList("SLOPERS"))
                    .build();
            
            given(userPreferenceService.getUserPreferences(testUserId))
                    .willReturn(preferences);
            
            // when & then
            mockMvc.perform(get("/api/recommendations/preferences/{userId}", testUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(testUserId))
                    .andExpect(jsonPath("$.preferredDifficulties").isArray())
                    .andExpect(jsonPath("$.preferredDifficulties.length()").value(2))
                    .andExpect(jsonPath("$.preferredTags").isArray())
                    .andExpect(jsonPath("$.preferredTags[0]").value("CRIMPING"))
                    .andExpect(jsonPath("$.excludedTags[0]").value("SLOPERS"));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 잘못된 선호도 업데이트")
        void updateUserPreferences_InvalidData() throws Exception {
            // given - 빈 선호도 리스트
            PreferenceUpdateRequestDto invalidRequest = PreferenceUpdateRequestDto.builder()
                    .userId(testUserId)
                    .preferredDifficulties(Arrays.asList()) // 빈 리스트
                    .preferredTags(Arrays.asList())
                    .build();
            
            String requestBody = objectMapper.writeValueAsString(invalidRequest);
            
            // when & then
            mockMvc.perform(put("/api/recommendations/preferences/{userId}", testUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("최소 하나의 선호 난이도 또는 태그를 선택해야 합니다"));
        }
    }
    
    @Nested
    @DisplayName("추천 피드백 API 테스트")
    class RecommendationFeedbackTest {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[성공] 추천 피드백 제출")
        void submitRecommendationFeedback_Success() throws Exception {
            // given
            String feedbackJson = """
                {
                    "routeId": 1,
                    "userId": 1,
                    "feedback": "HELPFUL",
                    "rating": 4,
                    "comment": "좋은 추천이었습니다!",
                    "completed": true
                }
                """;
            
            // when & then
            mockMvc.perform(post("/api/recommendations/feedback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(feedbackJson)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("피드백이 성공적으로 제출되었습니다"));
            
            verify(recommendationService).submitFeedback(any());
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] 잘못된 피드백 점수")
        void submitRecommendationFeedback_InvalidRating() throws Exception {
            // given - 유효 범위를 벗어난 점수
            String invalidFeedbackJson = """
                {
                    "routeId": 1,
                    "userId": 1,
                    "feedback": "HELPFUL",
                    "rating": 6,
                    "comment": "점수가 잘못됨"
                }
                """;
            
            // when & then
            mockMvc.perform(post("/api/recommendations/feedback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidFeedbackJson)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("평점은 1~5 사이의 값이어야 합니다"));
        }
    }
    
    @Nested
    @DisplayName("보안 및 권한 테스트")
    class SecurityTest {
        
        @Test
        @DisplayName("[실패] 인증되지 않은 사용자 접근")
        void getPersonalizedRecommendations_Unauthenticated() throws Exception {
            // when & then
            mockMvc.perform(get("/api/recommendations/personal/{userId}", testUserId))
                    .andExpect(status().isUnauthorized());
        }
        
        @Test
        @WithMockUser(username = "2", roles = "USER")
        @DisplayName("[실패] 다른 사용자의 추천 조회 시도")
        void getPersonalizedRecommendations_UnauthorizedUser() throws Exception {
            // given - 사용자 2가 사용자 1의 추천을 조회 시도
            doThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                    .when(recommendationService).getPersonalizedRecommendations(testUserId, 10);
            
            // when & then
            mockMvc.perform(get("/api/recommendations/personal/{userId}", testUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("[실패] Rate Limiting 초과")
        void getPersonalizedRecommendations_RateLimitExceeded() throws Exception {
            // given
            doThrow(new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED))
                    .when(recommendationService).getPersonalizedRecommendations(testUserId, 10);
            
            // when & then
            mockMvc.perform(get("/api/recommendations/personal/{userId}", testUserId)
                    .with(jwt().authorities(() -> "ROLE_USER")))
                    .andExpect(status().isTooManyRequests())
                    .andExpected(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
                    .andExpect(jsonPath("$.message").value("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."));
        }
    }
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    @DisplayName("[종합] 추천 시스템 전체 워크플로우")
    void recommendationSystem_CompleteWorkflow() throws Exception {
        // 1. 사용자 선호도 설정
        PreferenceUpdateRequestDto preferences = PreferenceUpdateRequestDto.builder()
                .userId(testUserId)
                .preferredDifficulties(Arrays.asList("V3", "V4"))
                .preferredTags(Arrays.asList("CRIMPING", "OVERHANG"))
                .build();
        
        String preferencesJson = objectMapper.writeValueAsString(preferences);
        
        mockMvc.perform(put("/api/recommendations/preferences/{userId}", testUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(preferencesJson)
                .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
        
        // 2. 개인화 추천 조회
        given(recommendationService.getPersonalizedRecommendations(testUserId, 10))
                .willReturn(Arrays.asList(sampleRecommendation));
        
        mockMvc.perform(get("/api/recommendations/personal/{userId}", testUserId)
                .param("limit", "10")
                .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        
        // 3. 추천 분석 조회
        RecommendationAnalyticsDto analytics = RecommendationAnalyticsDto.builder()
                .totalRecommendations(1)
                .successRate(0.0)
                .build();
        
        given(recommendationService.getRecommendationAnalytics(testUserId))
                .willReturn(analytics);
        
        mockMvc.perform(get("/api/recommendations/analytics/{userId}", testUserId)
                .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpected(jsonPath("$.totalRecommendations").value(1));
        
        // 모든 서비스 호출 검증
        verify(userPreferenceService).updateUserPreferences(any());
        verify(recommendationService).getPersonalizedRecommendations(testUserId, 10);
        verify(recommendationService).getRecommendationAnalytics(testUserId);
    }
}
```

## API 문서화 테스트

```java
@Nested
@DisplayName("API 문서화 테스트")
class ApiDocumentationTest {
    
    @Test
    @WithMockUser(username = "1", roles = "USER")
    @DisplayName("[문서화] 개인화 추천 API 문서")
    void documentPersonalizedRecommendationApi() throws Exception {
        // given
        given(recommendationService.getPersonalizedRecommendations(testUserId, 10))
                .willReturn(Arrays.asList(sampleRecommendation));
        
        // when & then
        mockMvc.perform(get("/api/recommendations/personal/{userId}", testUserId)
                .param("limit", "10")
                .with(jwt().authorities(() -> "ROLE_USER")))
                .andDo(document("get-personalized-recommendations",
                        pathParameters(
                                parameterWithName("userId").description("사용자 ID")
                        ),
                        requestParameters(
                                parameterWithName("limit").description("추천 개수 (기본값: 10, 최대: 100)")
                        ),
                        responseFields(
                                fieldWithPath("[].routeId").description("루트 ID"),
                                fieldWithPath("[].routeName").description("루트 이름"),
                                fieldWithPath("[].difficulty").description("난이도"),
                                fieldWithPath("[].gymName").description("암장 이름"),
                                fieldWithPath("[].score").description("추천 점수 (0.0-1.0)"),
                                fieldWithPath("[].matchedTags").description("매칭된 태그 목록"),
                                fieldWithPath("[].estimatedSuccessRate").description("예상 성공률")
                        )))
                .andExpect(status().isOk());
    }
}
```

## 실행 및 검증

### 실행 명령어
```bash
# 추천 컨트롤러 테스트 전체 실행
./gradlew test --tests="*RecommendationControllerTest"

# 특정 테스트 클래스만 실행
./gradlew test --tests="RecommendationControllerTest.PersonalizedRecommendationTest"

# API 문서 생성과 함께 실행
./gradlew test --tests="*RecommendationControllerTest" -Pspring.profiles.active=docs
```

### 검증 포인트
1. **API 정확성**: 모든 엔드포인트가 예상대로 동작
2. **입력 검증**: 잘못된 입력에 대한 적절한 오류 응답
3. **권한 검증**: 사용자 권한에 따른 접근 제어
4. **성능**: API 응답 시간 및 처리량
5. **보안**: 인증, 권한, Rate Limiting 등
6. **문서화**: API 스펙 자동 생성 및 유지

이 테스트는 추천 시스템의 핵심 API들이 모든 시나리오에서 안정적으로 동작함을 보장합니다.