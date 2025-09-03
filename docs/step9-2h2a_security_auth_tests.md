# step9-2h2a_security_auth_tests.md

> **RoutePickr - 클라이밍 루트 추천 플랫폼**  
> Step 9-2h2a: 보안/인증/권한 테스트 설계

## 📋 이 문서의 내용

이 문서는 **step9-2h_missing_critical_tests.md**에서 분할된 보안 테스트 부분으로, 다음 테스트들을 포함합니다:

### 🔐 보안 관련 테스트
- RecommendationController 보안 테스트 (인증, 권한, 데이터 격리)
- UserPreferenceController 보안 테스트

### 🏷️ 태그 플래그 검증 테스트
- `is_user_selectable` 플래그 검증
- `is_route_taggable` 플래그 검증  
- `display_order` 정렬 검증
- 플래그 조합 유효성 검사

---

## 🔐 보안 테스트 설계

### RecommendationController 보안 테스트

```java
package com.routepick.controller.recommendation;

import com.routepick.dto.recommendation.RecommendationResponseDto;
import com.routepick.service.recommendation.RecommendationService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecommendationController.class)
@DisplayName("RecommendationController 보안 테스트")
class RecommendationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @Test
    @DisplayName("비인증 사용자 추천 조회 차단")
    @WithAnonymousUser
    void unauthorizedUserShouldBeBlocked() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/recommendations")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isUnauthorized());
        
        // 서비스 호출되지 않음 확인
        verifyNoInteractions(recommendationService);
    }

    @Test
    @DisplayName("인증된 사용자 자신의 추천만 조회 가능")
    @WithMockUser(username = "user1")
    void authenticatedUserCanAccessOwnRecommendations() throws Exception {
        // Given
        Long userId = 1L;
        List<RecommendationResponseDto> recommendations = Arrays.asList(
                createRecommendation(1L, userId, "테스트 루트 1"),
                createRecommendation(2L, userId, "테스트 루트 2")
        );
        
        Page<RecommendationResponseDto> page = new PageImpl<>(recommendations);
        
        given(recommendationService.getUserRecommendations(eq(userId), any()))
                .willReturn(page);

        // When & Then
        mockMvc.perform(get("/api/v1/recommendations")
                .param("page", "0")
                .param("size", "10")
                .header("X-User-ID", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));
        
        verify(recommendationService).getUserRecommendations(eq(userId), any());
    }

    @Test
    @DisplayName("다른 사용자의 추천 조회 차단")
    @WithMockUser(username = "user1")
    void userCannotAccessOtherUsersRecommendations() throws Exception {
        // Given
        Long requestUserId = 1L;
        Long targetUserId = 2L; // 다른 사용자
        
        // When & Then
        mockMvc.perform(get("/api/v1/recommendations")
                .param("page", "0")
                .param("size", "10")
                .header("X-User-ID", requestUserId.toString())
                .param("targetUserId", targetUserId.toString()))
                .andExpect(status().isForbidden());
        
        verifyNoInteractions(recommendationService);
    }

    @Test
    @DisplayName("추천 피드백 권한 검증")
    @WithMockUser(username = "user1")
    void feedbackRequiresProperAuthorization() throws Exception {
        // Given
        Long userId = 1L;
        Long routeId = 100L;
        
        // When & Then - 정상 케이스
        mockMvc.perform(post("/api/v1/recommendations/feedback")
                .param("routeId", routeId.toString())
                .param("liked", "true")
                .header("X-User-ID", userId.toString()))
                .andExpect(status().isOk());
        
        verify(recommendationService).processRecommendationFeedback(userId, routeId, true);
    }

    private RecommendationResponseDto createRecommendation(Long routeId, Long userId, String routeName) {
        return RecommendationResponseDto.builder()
                .routeId(routeId)
                .userId(userId)
                .routeName(routeName)
                .recommendationScore(0.85)
                .tagMatchScore(0.9)
                .levelMatchScore(0.7)
                .build();
    }
}
```

### UserPreferenceController 보안 테스트

```java
package com.routepick.controller.tag;

import com.routepick.dto.tag.request.UserPreferenceUpdateRequest;
import com.routepick.service.tag.UserPreferenceService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserPreferenceController.class)
@DisplayName("UserPreferenceController 보안 테스트")
class UserPreferenceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserPreferenceService userPreferenceService;

    @Test
    @DisplayName("비인증 사용자 선호도 설정 차단")
    @WithAnonymousUser
    void unauthorizedUserCannotSetPreferences() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/users/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "tagId": 1,
                        "preferenceLevel": "HIGH"
                    }
                    """))
                .andExpect(status().isUnauthorized());
        
        verifyNoInteractions(userPreferenceService);
    }

    @Test
    @DisplayName("인증된 사용자만 자신의 선호도 수정 가능")
    @WithMockUser(username = "user1")
    void authenticatedUserCanUpdateOwnPreferences() throws Exception {
        // Given
        Long userId = 1L;
        Long tagId = 10L;
        
        UserPreferenceUpdateRequest request = UserPreferenceUpdateRequest.builder()
                .tagId(tagId)
                .preferenceLevel("HIGH")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/users/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-ID", userId.toString())
                .content("""
                    {
                        "tagId": 10,
                        "preferenceLevel": "HIGH"
                    }
                    """))
                .andExpect(status().isOk());
        
        verify(userPreferenceService).updateUserPreference(eq(userId), eq(tagId), eq("HIGH"));
    }

    @Test
    @DisplayName("사용자 간 데이터 격리 확인")
    @WithMockUser(username = "user1")
    void userDataIsolation() throws Exception {
        // Given
        Long user1Id = 1L;
        Long user2Id = 2L;
        
        // User1으로 접근 시 User2의 데이터에 접근하지 못함
        mockMvc.perform(get("/api/v1/users/{userId}/preferences", user2Id)
                .header("X-User-ID", user1Id.toString()))
                .andExpect(status().isForbidden());
        
        // 자신의 데이터는 정상 접근
        mockMvc.perform(get("/api/v1/users/{userId}/preferences", user1Id)
                .header("X-User-ID", user1Id.toString()))
                .andExpect(status().isOk());
        
        verify(userPreferenceService).getUserPreferences(user1Id);
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("태그 플래그 검증 테스트")
class TagFlagValidationTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    @Test
    @DisplayName("is_user_selectable 플래그 검증 - 사용자 선택 가능한 태그만 조회")
    void userSelectableTagsOnly() {
        // Given
        List<Tag> allTags = Arrays.asList(
                createTagWithFlags(1L, "다이나믹", TagType.STYLE, true, true, 1), // 선택 가능
                createTagWithFlags(2L, "시스템태그", TagType.STYLE, false, true, 2), // 선택 불가능
                createTagWithFlags(3L, "파워풀", TagType.STYLE, true, true, 3) // 선택 가능
        );
        
        given(tagRepository.findByIsActiveTrueAndIsUserSelectableTrueOrderByDisplayOrderAsc())
                .willReturn(Arrays.asList(allTags.get(0), allTags.get(2)));

        // When
        List<Tag> userSelectableTags = tagService.getUserSelectableTags();

        // Then
        assertThat(userSelectableTags).hasSize(2);
        assertThat(userSelectableTags).allMatch(Tag::getIsUserSelectable);
        assertThat(userSelectableTags).extracting(Tag::getTagName)
                .containsExactly("다이나믹", "파워풀");
    }

    @Test
    @DisplayName("is_route_taggable 플래그 검증 - 루트 태깅 가능한 태그만 조회")
    void routeTaggableTagsOnly() {
        // Given
        List<Tag> allTags = Arrays.asList(
                createTagWithFlags(1L, "크림프", TagType.HOLD_TYPE, true, true, 1), // 태깅 가능
                createTagWithFlags(2L, "히든태그", TagType.HOLD_TYPE, true, false, 2), // 태깅 불가능
                createTagWithFlags(3L, "슬로퍼", TagType.HOLD_TYPE, true, true, 3) // 태깅 가능
        );
        
        given(tagRepository.findByIsActiveTrueAndIsRouteTaggableTrueOrderByDisplayOrderAsc())
                .willReturn(Arrays.asList(allTags.get(0), allTags.get(2)));

        // When
        List<Tag> routeTaggableTags = tagService.getRouteTaggableTags();

        // Then
        assertThat(routeTaggableTags).hasSize(2);
        assertThat(routeTaggableTags).allMatch(Tag::getIsRouteTaggable);
        assertThat(routeTaggableTags).extracting(Tag::getTagName)
                .containsExactly("크림프", "슬로퍼");
    }

    @Test
    @DisplayName("display_order 정렬 검증 - 올바른 순서로 정렬")
    void displayOrderSorting() {
        // Given
        List<Tag> unsortedTags = Arrays.asList(
                createTagWithFlags(3L, "세번째", TagType.STYLE, true, true, 30),
                createTagWithFlags(1L, "첫번째", TagType.STYLE, true, true, 10),
                createTagWithFlags(2L, "두번째", TagType.STYLE, true, true, 20)
        );
        
        List<Tag> sortedTags = Arrays.asList(
                unsortedTags.get(1), // display_order: 10
                unsortedTags.get(2), // display_order: 20
                unsortedTags.get(0)  // display_order: 30
        );
        
        given(tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
                .willReturn(sortedTags);

        // When
        List<Tag> result = tagService.getAllActiveTagsSortedByDisplayOrder();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Tag::getDisplayOrder)
                .containsExactly(10, 20, 30);
        assertThat(result).extracting(Tag::getTagName)
                .containsExactly("첫번째", "두번째", "세번째");
    }

    @Test
    @DisplayName("플래그 조합 유효성 검사 - 잘못된 플래그 조합 감지")
    void flagCombinationValidation() {
        // Given - 비활성 태그인데 사용자 선택 가능한 잘못된 조합
        Tag invalidTag = createTagWithFlags(1L, "잘못된태그", TagType.STYLE, true, true, 1);
        invalidTag.setIsActive(false); // 비활성화
        
        // When & Then
        assertThatThrownBy(() -> tagService.validateTagFlags(invalidTag))
                .isInstanceOf(TagException.class)
                .hasMessageContaining("비활성 태그는 사용자 선택이 불가능합니다");
    }

    @Test
    @DisplayName("중복 display_order 검증")
    void duplicateDisplayOrderValidation() {
        // Given
        Tag tag1 = createTagWithFlags(1L, "태그1", TagType.STYLE, true, true, 10);
        Tag tag2 = createTagWithFlags(2L, "태그2", TagType.STYLE, true, true, 10); // 중복 순서
        
        given(tagRepository.findByDisplayOrder(10))
                .willReturn(Arrays.asList(tag1, tag2));

        // When & Then
        assertThatThrownBy(() -> tagService.validateUniqueDisplayOrder(10))
                .isInstanceOf(TagException.class)
                .hasMessageContaining("중복된 display_order입니다");
    }

    private Tag createTagWithFlags(Long id, String name, TagType type, 
                                  Boolean userSelectable, Boolean routeTaggable, Integer displayOrder) {
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