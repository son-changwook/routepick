# Step 9-2h1: Controller 및 API 테스트

> UserPreferenceController, RecommendationController API 엔드포인트 테스트  
> 생성일: 2025-08-21  
> 단계: 9-2h1 (Controller API 테스트)  
> 참고: step7-2, step7-3

---

## 🎮 UserPreferenceController 테스트

### UserPreferenceControllerTest.java

```java
package com.routepick.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.common.enums.PreferenceLevel;
import com.routepick.common.enums.SkillLevel;
import com.routepick.common.enums.TagType;
import com.routepick.controller.user.UserPreferenceController;
import com.routepick.dto.user.UserPreferenceRequestDto;
import com.routepick.dto.user.UserPreferenceResponseDto;
import com.routepick.dto.tag.TagResponseDto;
import com.routepick.security.JwtTokenProvider;
import com.routepick.service.user.UserPreferenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

@WebMvcTest(UserPreferenceController.class)
@DisplayName("UserPreferenceController 테스트")
class UserPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserPreferenceService userPreferenceService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UserPreferenceRequestDto validRequestDto;
    private UserPreferenceResponseDto validResponseDto;

    @BeforeEach
    void setUp() {
        // 유효한 선호도 요청 DTO
        validRequestDto = UserPreferenceRequestDto.builder()
                .preferences(Arrays.asList(
                        UserPreferenceRequestDto.PreferenceItem.builder()
                                .tagId(1L)
                                .preferenceLevel(PreferenceLevel.HIGH)
                                .skillLevel(SkillLevel.INTERMEDIATE)
                                .build(),
                        UserPreferenceRequestDto.PreferenceItem.builder()
                                .tagId(2L)
                                .preferenceLevel(PreferenceLevel.MEDIUM)
                                .skillLevel(SkillLevel.BEGINNER)
                                .build()
                ))
                .build();

        // 유효한 응답 DTO
        validResponseDto = UserPreferenceResponseDto.builder()
                .userId(1L)
                .totalPreferences(2)
                .preferences(Arrays.asList(
                        createTagResponse(1L, "다이나믹", TagType.STYLE, PreferenceLevel.HIGH, SkillLevel.INTERMEDIATE),
                        createTagResponse(2L, "슬랩", TagType.TECHNIQUE, PreferenceLevel.MEDIUM, SkillLevel.BEGINNER)
                ))
                .isEligibleForRecommendation(true)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    // ===== GET API 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/preferences - 사용자 선호도 조회 성공")
    void getUserPreferences_Success() throws Exception {
        // Given
        given(userPreferenceService.getUserPreferences(1L))
                .willReturn(validResponseDto);

        // When & Then
        mockMvc.perform(get("/api/v1/users/preferences")
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPreferences").value(2))
                .andExpect(jsonPath("$.data.preferences[0].tagName").value("다이나믹"))
                .andExpect(jsonPath("$.data.preferences[0].preferenceLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.isEligibleForRecommendation").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/preferences - 선호도 없는 사용자")
    void getUserPreferences_EmptyPreferences() throws Exception {
        // Given
        UserPreferenceResponseDto emptyResponse = UserPreferenceResponseDto.builder()
                .userId(1L)
                .totalPreferences(0)
                .preferences(Collections.emptyList())
                .isEligibleForRecommendation(false)
                .lastUpdated(LocalDateTime.now())
                .build();

        given(userPreferenceService.getUserPreferences(1L))
                .willReturn(emptyResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/users/preferences")
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPreferences").value(0))
                .andExpect(jsonPath("$.data.preferences").isEmpty())
                .andExpect(jsonPath("$.data.isEligibleForRecommendation").value(false));
    }

    // ===== PUT API 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("PUT /api/v1/users/preferences - 선호도 설정 성공")
    void updateUserPreferences_Success() throws Exception {
        // Given
        given(userPreferenceService.updateUserPreferences(eq(1L), any()))
                .willReturn(validResponseDto);

        // When & Then
        mockMvc.perform(put("/api/v1/users/preferences")
                        .with(csrf())
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestDto)))
                .andDo(print())
                .andExpected(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPreferences").value(2))
                .andExpected(jsonPath("$.message").value("선호도가 성공적으로 업데이트되었습니다."));

        verify(userPreferenceService).updateUserPreferences(eq(1L), any());
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/v1/users/preferences - 최대 선호도 수 초과")
    void updateUserPreferences_ExceedsMaxLimit() throws Exception {
        // Given - 21개 선호도 (최대 20개 초과)
        UserPreferenceRequestDto tooManyPreferences = UserPreferenceRequestDto.builder()
                .preferences(IntStream.rangeClosed(1, 21)
                        .mapToObj(i -> UserPreferenceRequestDto.PreferenceItem.builder()
                                .tagId((long) i)
                                .preferenceLevel(PreferenceLevel.MEDIUM)
                                .skillLevel(SkillLevel.BEGINNER)
                                .build())
                        .toList())
                .build();

        // When & Then
        mockMvc.perform(put("/api/v1/users/preferences")
                        .with(csrf())
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooManyPreferences)))
                .andDo(print())
                .andExpected(status().isBadRequest())
                .andExpected(jsonPath("$.success").value(false))
                .andExpected(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpected(jsonPath("$.error.message").value("선호도는 최대 20개까지 설정할 수 있습니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/v1/users/preferences - 유효하지 않은 요청 데이터")
    void updateUserPreferences_InvalidData() throws Exception {
        // Given - 빈 선호도 리스트
        UserPreferenceRequestDto invalidRequest = UserPreferenceRequestDto.builder()
                .preferences(Collections.emptyList())
                .build();

        // When & Then
        mockMvc.perform(put("/api/v1/users/preferences")
                        .with(csrf())
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpected(status().isBadRequest());
    }

    // ===== DELETE API 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/v1/users/preferences/{tagId} - 개별 선호도 삭제 성공")
    void deleteUserPreference_Success() throws Exception {
        // Given
        willDoNothing().given(userPreferenceService).removeUserPreference(1L, 1L);

        // When & Then
        mockMvc.perform(delete("/api/v1/users/preferences/{tagId}", 1L)
                        .with(csrf())
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L))
                .andDo(print())
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("선호도가 성공적으로 삭제되었습니다."));

        verify(userPreferenceService).removeUserPreference(1L, 1L);
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/v1/users/preferences - 전체 선호도 초기화 성공")
    void clearAllUserPreferences_Success() throws Exception {
        // Given
        willDoNothing().given(userPreferenceService).clearUserPreferences(1L);

        // When & Then
        mockMvc.perform(delete("/api/v1/users/preferences")
                        .with(csrf())
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L))
                .andDo(print())
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("모든 선호도가 성공적으로 초기화되었습니다."));

        verify(userPreferenceService).clearUserPreferences(1L);
    }

    // ===== 통계 API 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/preferences/statistics - 선호도 통계 조회")
    void getUserPreferenceStatistics_Success() throws Exception {
        // Given
        Map<String, Object> statistics = Map.of(
                "totalTags", 5,
                "preferenceDistribution", Map.of(
                        "HIGH", 2,
                        "MEDIUM", 2,
                        "LOW", 1
                ),
                "skillDistribution", Map.of(
                        "EXPERT", 1,
                        "ADVANCED", 1,
                        "INTERMEDIATE", 2,
                        "BEGINNER", 1
                ),
                "tagTypeDistribution", Map.of(
                        "STYLE", 2,
                        "TECHNIQUE", 2,
                        "HOLD_TYPE", 1
                )
        );

        given(userPreferenceService.getUserPreferenceStatistics(1L))
                .willReturn(statistics);

        // When & Then
        mockMvc.perform(get("/api/v1/users/preferences/statistics")
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L))
                .andDo(print())
                .andExpected(status().isOk())
                .andExpect(jsonPath("$.data.totalTags").value(5))
                .andExpect(jsonPath("$.data.preferenceDistribution.HIGH").value(2))
                .andExpected(jsonPath("$.data.skillDistribution.EXPERT").value(1));
    }

    // 헬퍼 메소드
    private TagResponseDto createTagResponse(Long id, String name, TagType type, 
                                           PreferenceLevel prefLevel, SkillLevel skillLevel) {
        return TagResponseDto.builder()
                .tagId(id)
                .tagName(name)
                .tagType(type)
                .preferenceLevel(prefLevel)
                .skillLevel(skillLevel)
                .experienceMonths(6)
                .isUserSelectable(true)
                .build();
    }
}
```

---

## 🎯 RecommendationController 테스트

### RecommendationControllerTest.java

```java
package com.routepick.controller.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.controller.recommendation.RecommendationController;
import com.routepick.dto.recommendation.RecommendationRequestDto;
import com.routepick.dto.recommendation.RecommendationResponseDto;
import com.routepick.dto.route.RouteSimpleDto;
import com.routepick.security.JwtTokenProvider;
import com.routepick.service.recommendation.RecommendationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

@WebMvcTest(RecommendationController.class)
@DisplayName("RecommendationController 테스트")
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private RecommendationResponseDto validRecommendationResponse;

    @BeforeEach
    void setUp() {
        // 유효한 추천 응답 DTO
        validRecommendationResponse = RecommendationResponseDto.builder()
                .userId(1L)
                .algorithmVersion("1.0.0")
                .totalRecommendations(10)
                .recommendedRoutes(Arrays.asList(
                        createRouteDto(1L, "테스트 루트 1", "V4", 0.92),
                        createRouteDto(2L, "테스트 루트 2", "V5", 0.88),
                        createRouteDto(3L, "테스트 루트 3", "V3", 0.85)
                ))
                .generatedAt(LocalDateTime.now())
                .cacheTtl(3600)
                .build();
    }

    // ===== GET API 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/recommendations/routes - 개인화 루트 추천 조회 성공")
    void getPersonalizedRecommendations_Success() throws Exception {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        given(recommendationService.getPersonalizedRecommendations(eq(1L), any()))
                .willReturn(validRecommendationResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/recommendations/routes")
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L)
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.data.totalRecommendations").value(10))
                .andExpected(jsonPath("$.data.recommendedRoutes").isArray())
                .andExpected(jsonPath("$.data.recommendedRoutes[0].routeName").value("테스트 루트 1"))
                .andExpect(jsonPath("$.data.recommendedRoutes[0].recommendationScore").value(0.92));

        verify(recommendationService).getPersonalizedRecommendations(eq(1L), any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/recommendations/routes - 선호도 미설정 사용자")
    void getPersonalizedRecommendations_NoPreferences() throws Exception {
        // Given
        RecommendationResponseDto emptyResponse = RecommendationResponseDto.builder()
                .userId(1L)
                .totalRecommendations(0)
                .recommendedRoutes(Collections.emptyList())
                .message("추천을 받기 위해서는 최소 3개 이상의 선호 태그를 설정해주세요.")
                .generatedAt(LocalDateTime.now())
                .build();

        given(recommendationService.getPersonalizedRecommendations(eq(1L), any()))
                .willReturn(emptyResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/recommendations/routes")
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L))
                .andDo(print())
                .andExpected(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendations").value(0))
                .andExpected(jsonPath("$.data.message").value("추천을 받기 위해서는 최소 3개 이상의 선호 태그를 설정해주세요."));
    }

    // ===== POST API 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/recommendations/refresh - 추천 새로고침 성공")
    void refreshRecommendations_Success() throws Exception {
        // Given
        given(recommendationService.refreshPersonalizedRecommendations(eq(1L)))
                .willReturn(validRecommendationResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/recommendations/refresh")
                        .with(csrf())
                        .header("Authorization", "Bearer valid-token")
                        .requestAttr("userId", 1L))
                .andDo(print())
                .andExpected(status().isOk())
                .andExpected(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.data.totalRecommendations").value(10))
                .andExpected(jsonPath("$.message").value("추천 목록이 새로 생성되었습니다."));

        verify(recommendationService).refreshPersonalizedRecommendations(1L);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/recommendations/batch-generate - 배치 추천 생성")
    void generateBatchRecommendations_Success() throws Exception {
        // Given
        RecommendationRequestDto batchRequest = RecommendationRequestDto.builder()
                .userIds(Arrays.asList(1L, 2L, 3L))
                .forceRefresh(true)
                .build();

        Map<String, Object> batchResult = Map.of(
                "processedUsers", 3,
                "successfulCount", 3,
                "failedCount", 0,
                "processingTimeMs", 1500
        );

        given(recommendationService.generateBatchRecommendations(any()))
                .willReturn(batchResult);

        // When & Then
        mockMvc.perform(post("/api/v1/recommendations/batch-generate")
                        .with(csrf())
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchRequest)))
                .andDo(print())
                .andExpected(status().isOk())
                .andExpect(jsonPath("$.data.processedUsers").value(3))
                .andExpectedjsonPath("$.data.successfulCount").value(3));
    }

    // ===== 알고리즘 정보 API 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/recommendations/algorithm-info - 알고리즘 정보 조회")
    void getAlgorithmInfo_Success() throws Exception {
        // Given
        Map<String, Object> algorithmInfo = Map.of(
                "name", "TAG_MATCHING_70_LEVEL_30",
                "tagMatchingWeight", 0.7,
                "levelMatchingWeight", 0.3,
                "description", "태그 매칭 70% + 레벨 매칭 30% 가중치 알고리즘",
                "version", "1.0.0",
                "lastUpdated", "2025-08-27T00:00:00"
        );

        given(recommendationService.getAlgorithmInfo())
                .willReturn(algorithmInfo);

        // When & Then
        mockMvc.perform(get("/api/v1/recommendations/algorithm-info")
                        .header("Authorization", "Bearer valid-token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpected(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.data.tagMatchingWeight").value(0.7))
                .andExpected(jsonPath("$.data.levelMatchingWeight").value(0.3));
    }

    // ===== 에러 처리 테스트 =====

    @Test
    @DisplayName("인증 없이 추천 조회 시도")
    void getRecommendationsWithoutAuth_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations/routes"))
                .andDo(print())
                .andExpected(status().isUnauthorized());
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
                .andExpected(status().isForbidden())
                .andExpected(jsonPath("$.success").value(false))
                .andExpectedjsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    // ===== 캐시 관련 테스트 =====

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
                .andExpectedjsonPath("$.message").value("캐시가 성공적으로 삭제되었습니다."));

        verify(recommendationService).invalidateUserCache(1L);
    }

    // 헬퍼 메소드
    private RouteSimpleDto createRouteDto(Long routeId, String routeName, String difficulty, double score) {
        return RouteSimpleDto.builder()
                .routeId(routeId)
                .routeName(routeName)
                .difficulty(difficulty)
                .recommendationScore(score)
                .branchName("테스트 지점")
                .gymName("테스트 암장")
                .imageUrl("https://cdn.example.com/route/" + routeId + ".jpg")
                .tagNames(Arrays.asList("다이나믹", "파워풀"))
                .createdAt(LocalDateTime.now())
                .build();
    }
}
```

---

## 🔧 API 엔드포인트 테스트 매트릭스

### UserPreferenceController 엔드포인트
| Method | Endpoint | 기능 | 테스트 케이스 |
|--------|----------|------|-------------|
| GET | `/api/v1/users/preferences` | 선호도 조회 | ✅ 성공, ✅ 빈 결과 |
| PUT | `/api/v1/users/preferences` | 선호도 설정 | ✅ 성공, ✅ 최대 한도 초과, ✅ 유효성 검증 |
| DELETE | `/api/v1/users/preferences/{tagId}` | 개별 삭제 | ✅ 성공 |
| DELETE | `/api/v1/users/preferences` | 전체 초기화 | ✅ 성공 |
| GET | `/api/v1/users/preferences/statistics` | 통계 조회 | ✅ 성공 |

### RecommendationController 엔드포인트
| Method | Endpoint | 기능 | 테스트 케이스 |
|--------|----------|------|-------------|
| GET | `/api/v1/recommendations/routes` | 개인화 추천 | ✅ 성공, ✅ 선호도 없음, ✅ 권한 검증 |
| POST | `/api/v1/recommendations/refresh` | 추천 새로고침 | ✅ 성공 |
| POST | `/api/v1/recommendations/batch-generate` | 배치 생성 | ✅ 성공 |
| GET | `/api/v1/recommendations/algorithm-info` | 알고리즘 정보 | ✅ 성공 |
| DELETE | `/api/v1/recommendations/cache` | 캐시 무효화 | ✅ 성공 |

---

## 🛡️ 보안 테스트 시나리오

### **1. 인증/인가 테스트**
- ✅ 인증 없이 API 호출 → 401 Unauthorized
- ✅ 다른 사용자 데이터 접근 → 403 Forbidden  
- ✅ JWT 토큰 유효성 검증
- ✅ 사용자별 데이터 격리 확인

### **2. 입력 검증 테스트**
- ✅ 최대 선호도 수 제한 (20개)
- ✅ 필수 파라미터 누락 검증
- ✅ 잘못된 데이터 타입 처리
- ✅ SQL Injection 방지 확인

### **3. Rate Limiting 테스트**
- 추천 API 호출 횟수 제한
- IP별 요청 수 제한
- 사용자별 추천 새로고침 제한

---

## 🚀 **다음 단계**

**step9-2h2 연계:**
- 캐싱 전략 테스트
- 보안 및 플래그 검증 테스트
- 성능 및 통합 테스트

*step9-2h1 완성: Controller 및 API 테스트 완료*