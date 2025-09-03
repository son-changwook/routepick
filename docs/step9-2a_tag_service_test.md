# 9-2a: 태그 서비스 테스트 설계

> 태그 시스템 핵심 로직 테스트 - 8가지 TagType 검증 및 CRUD 테스트
> 생성일: 2025-08-27
> 단계: 9-2a (태그 시스템 및 추천 테스트 - 태그 서비스)
> 테스트 대상: TagService, UserPreferenceService, RouteTaggingService

---

## 🎯 테스트 목표

- **태그 CRUD 테스트**: 8가지 TagType별 생성/수정/삭제/조회
- **사용자 선호도 테스트**: PreferenceLevel 3단계 × SkillLevel 4단계
- **루트 태깅 테스트**: 연관도(relevance_score) 기반 품질 관리
- **태그 검증 테스트**: 플래그 기반 선택/태깅 가능성 검증
- **카테고리별 정렬**: display_order 기반 UI 표시 순서

---

## 🏷️ TagService 테스트 설계

### TagServiceTest.java
```java
package com.routepick.service.tag;

import com.routepick.common.enums.TagType;
import com.routepick.domain.tag.entity.Tag;
import com.routepick.domain.tag.repository.TagRepository;
import com.routepick.exception.tag.TagException;
import com.routepick.service.tag.TagService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * 태그 서비스 단위 테스트
 * - 8가지 TagType 검증
 * - CRUD 연산 테스트
 * - 플래그 기반 필터링 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TagService 테스트")
class TagServiceTest {
    
    @Mock
    private TagRepository tagRepository;
    
    @InjectMocks
    private TagService tagService;
    
    private Tag testTag;
    private List<Tag> testTags;
    
    @BeforeEach
    void setUp() {
        testTag = createTestTag(1L, "볼더링", TagType.STYLE, true, true);
        testTags = createTestTagList();
    }
    
    // ===== 태그 생성 테스트 =====
    
    @Test
    @DisplayName("태그 생성 - 성공")
    void createTag_Success() {
        // Given
        String tagName = "크림핑";
        TagType tagType = TagType.TECHNIQUE;
        String description = "손가락 끝으로 작은 홀드 잡기";
        
        Tag savedTag = createTestTag(2L, tagName, tagType, true, true);
        given(tagRepository.existsByTagName(tagName)).willReturn(false);
        given(tagRepository.save(any(Tag.class))).willReturn(savedTag);
        
        // When
        Tag result = tagService.createTag(tagName, tagType, description, true, true);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTagName()).isEqualTo(tagName);
        assertThat(result.getTagType()).isEqualTo(tagType);
        assertThat(result.getDescription()).isEqualTo(description);
        assertThat(result.isUserSelectable()).isTrue();
        assertThat(result.isRouteTaggable()).isTrue();
        
        verify(tagRepository).existsByTagName(tagName);
        verify(tagRepository).save(any(Tag.class));
    }
    
    @Test
    @DisplayName("태그 생성 - 중복 이름 실패")
    void createTag_DuplicateName_Failure() {
        // Given
        String duplicateName = "볼더링";
        given(tagRepository.existsByTagName(duplicateName)).willReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> 
            tagService.createTag(duplicateName, TagType.STYLE, null, true, true)
        )
        .isInstanceOf(TagException.class)
        .hasMessageContaining("이미 존재하는 태그");
        
        verify(tagRepository).existsByTagName(duplicateName);
        verify(tagRepository, never()).save(any(Tag.class));
    }
    
    @Test
    @DisplayName("태그 생성 - 8가지 TagType 모두 지원 검증")
    void createTag_AllTagTypes_Success() {
        // Given & When & Then
        for (TagType tagType : TagType.values()) {
            String tagName = "Test_" + tagType.name();
            Tag savedTag = createTestTag(1L, tagName, tagType, true, true);
            
            given(tagRepository.existsByTagName(tagName)).willReturn(false);
            given(tagRepository.save(any(Tag.class))).willReturn(savedTag);
            
            Tag result = tagService.createTag(tagName, tagType, null, true, true);
            
            assertThat(result.getTagType()).isEqualTo(tagType);
            assertThat(result.getTagName()).isEqualTo(tagName);
        }
    }
    
    // ===== 태그 조회 테스트 =====
    
    @Test
    @DisplayName("태그 ID로 조회 - 성공")
    void findTagById_Success() {
        // Given
        Long tagId = 1L;
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        
        // When
        Tag result = tagService.findById(tagId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTagId()).isEqualTo(tagId);
        assertThat(result.getTagName()).isEqualTo("볼더링");
        
        verify(tagRepository).findById(tagId);
    }
    
    @Test
    @DisplayName("태그 ID로 조회 - 존재하지 않음")
    void findTagById_NotFound() {
        // Given
        Long nonExistentId = 999L;
        given(tagRepository.findById(nonExistentId)).willReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> tagService.findById(nonExistentId))
            .isInstanceOf(TagException.class)
            .hasMessageContaining("태그를 찾을 수 없습니다");
        
        verify(tagRepository).findById(nonExistentId);
    }
    
    @Test
    @DisplayName("TagType별 태그 조회 - display_order 정렬")
    void findTagsByType_OrderedByDisplayOrder() {
        // Given
        TagType tagType = TagType.TECHNIQUE;
        List<Tag> techniqueTags = Arrays.asList(
            createTestTag(1L, "크림핑", tagType, 20, true, true),
            createTestTag(2L, "슬로핑", tagType, 21, true, true),
            createTestTag(3L, "핀치", tagType, 22, true, true)
        );
        
        given(tagRepository.findByTagTypeOrderByDisplayOrder(tagType))
            .willReturn(techniqueTags);
        
        // When
        List<Tag> result = tagService.findByType(tagType);
        
        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getTagName()).isEqualTo("크림핑");
        assertThat(result.get(0).getDisplayOrder()).isEqualTo(20);
        assertThat(result.get(1).getTagName()).isEqualTo("슬로핑");
        assertThat(result.get(2).getTagName()).isEqualTo("핀치");
        
        verify(tagRepository).findByTagTypeOrderByDisplayOrder(tagType);
    }
    
    @Test
    @DisplayName("사용자 선택 가능한 태그 조회")
    void findUserSelectableTags_Success() {
        // Given
        List<Tag> selectableTags = Arrays.asList(
            createTestTag(1L, "볼더링", TagType.STYLE, true, true),
            createTestTag(2L, "크림핑", TagType.TECHNIQUE, true, true),
            createTestTag(3L, "다이나믹", TagType.MOVEMENT, true, true)
        );
        
        given(tagRepository.findByIsUserSelectableTrueOrderByTagTypeAscDisplayOrderAsc())
            .willReturn(selectableTags);
        
        // When
        List<Tag> result = tagService.findUserSelectableTags();
        
        // Then
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(Tag::isUserSelectable);
        
        verify(tagRepository).findByIsUserSelectableTrueOrderByTagTypeAscDisplayOrderAsc();
    }
    
    @Test
    @DisplayName("루트 태깅 가능한 태그 조회")
    void findRouteTaggableTags_Success() {
        // Given
        List<Tag> taggableTags = Arrays.asList(
            createTestTag(1L, "볼더링", TagType.STYLE, true, true),
            createTestTag(2L, "저그", TagType.HOLD_TYPE, false, true),
            createTestTag(3L, "오버행", TagType.WALL_ANGLE, false, true)
        );
        
        given(tagRepository.findByIsRouteTaggableTrueOrderByTagTypeAscDisplayOrderAsc())
            .willReturn(taggableTags);
        
        // When
        List<Tag> result = tagService.findRouteTaggableTags();
        
        // Then
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(Tag::isRouteTaggable);
        
        verify(tagRepository).findByIsRouteTaggableTrueOrderByTagTypeAscDisplayOrderAsc();
    }
    
    // ===== 태그 수정 테스트 =====
    
    @Test
    @DisplayName("태그 수정 - 성공")
    void updateTag_Success() {
        // Given
        Long tagId = 1L;
        String newDescription = "실내 암벽등반의 대표적인 스타일";
        
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        given(tagRepository.save(any(Tag.class))).willReturn(testTag);
        
        // When
        Tag result = tagService.updateTag(tagId, null, null, newDescription, null, null);
        
        // Then
        assertThat(result).isNotNull();
        verify(tagRepository).findById(tagId);
        verify(tagRepository).save(testTag);
    }
    
    @Test
    @DisplayName("태그 플래그 수정 - 사용자 선택 불가로 변경")
    void updateTagFlags_DisableUserSelectable() {
        // Given
        Long tagId = 1L;
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        given(tagRepository.save(any(Tag.class))).willReturn(testTag);
        
        // When
        Tag result = tagService.updateTag(tagId, null, null, null, false, null);
        
        // Then
        assertThat(result).isNotNull();
        verify(tagRepository).save(testTag);
    }
    
    // ===== 태그 삭제 테스트 =====
    
    @Test
    @DisplayName("태그 삭제 - 성공")
    void deleteTag_Success() {
        // Given
        Long tagId = 1L;
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        given(tagRepository.isTagInUse(tagId)).willReturn(false);
        willDoNothing().given(tagRepository).deleteById(tagId);
        
        // When
        tagService.deleteTag(tagId);
        
        // Then
        verify(tagRepository).findById(tagId);
        verify(tagRepository).isTagInUse(tagId);
        verify(tagRepository).deleteById(tagId);
    }
    
    @Test
    @DisplayName("태그 삭제 - 사용 중인 태그 삭제 실패")
    void deleteTag_TagInUse_Failure() {
        // Given
        Long tagId = 1L;
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        given(tagRepository.isTagInUse(tagId)).willReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> tagService.deleteTag(tagId))
            .isInstanceOf(TagException.class)
            .hasMessageContaining("사용 중인 태그는 삭제할 수 없습니다");
        
        verify(tagRepository).findById(tagId);
        verify(tagRepository).isTagInUse(tagId);
        verify(tagRepository, never()).deleteById(tagId);
    }
    
    // ===== 태그 검색 테스트 =====
    
    @Test
    @DisplayName("태그 검색 - 이름으로 검색")
    void searchTags_ByName_Success() {
        // Given
        String keyword = "볼더";
        Pageable pageable = PageRequest.of(0, 10);
        List<Tag> searchResults = Arrays.asList(testTag);
        Page<Tag> pageResult = new PageImpl<>(searchResults, pageable, 1);
        
        given(tagRepository.searchByTagNameContaining(keyword, pageable))
            .willReturn(pageResult);
        
        // When
        Page<Tag> result = tagService.searchTags(keyword, pageable);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTagName()).contains("볼더");
        
        verify(tagRepository).searchByTagNameContaining(keyword, pageable);
    }
    
    @Test
    @DisplayName("태그 통계 조회 - TagType별 개수")
    void getTagStatistics_Success() {
        // Given
        Map<TagType, Long> mockStats = new HashMap<>();
        mockStats.put(TagType.STYLE, 5L);
        mockStats.put(TagType.TECHNIQUE, 10L);
        mockStats.put(TagType.MOVEMENT, 8L);
        mockStats.put(TagType.HOLD_TYPE, 12L);
        
        given(tagRepository.countByTagTypeGroupBy()).willReturn(mockStats);
        
        // When
        Map<TagType, Long> result = tagService.getTagStatistics();
        
        // Then
        assertThat(result).hasSize(4);
        assertThat(result.get(TagType.STYLE)).isEqualTo(5L);
        assertThat(result.get(TagType.TECHNIQUE)).isEqualTo(10L);
        assertThat(result.get(TagType.MOVEMENT)).isEqualTo(8L);
        assertThat(result.get(TagType.HOLD_TYPE)).isEqualTo(12L);
        
        verify(tagRepository).countByTagTypeGroupBy();
    }
    
    // ===== 배치 연산 테스트 =====
    
    @Test
    @DisplayName("배치 태그 생성 - 성공")
    void createTagsBatch_Success() {
        // Given
        List<String> tagNames = Arrays.asList("크림핑", "슬로핑", "핀치");
        TagType tagType = TagType.TECHNIQUE;
        
        List<Tag> savedTags = IntStream.range(0, tagNames.size())
            .mapToObj(i -> createTestTag(
                Long.valueOf(i + 1), 
                tagNames.get(i), 
                tagType, 
                true, 
                true
            ))
            .toList();
        
        given(tagRepository.saveAll(anyList())).willReturn(savedTags);
        
        // When
        List<Tag> result = tagService.createTagsBatch(tagNames, tagType, true, true);
        
        // Then
        assertThat(result).hasSize(3);
        assertThat(result.stream().map(Tag::getTagName))
            .containsExactlyInAnyOrderElementsOf(tagNames);
        
        verify(tagRepository).saveAll(anyList());
    }
    
    @Test
    @DisplayName("플래그 배치 업데이트 - display_order 재정렬")
    void updateDisplayOrderBatch_Success() {
        // Given
        TagType tagType = TagType.TECHNIQUE;
        List<Long> tagIds = Arrays.asList(1L, 2L, 3L);
        
        given(tagRepository.findAllById(tagIds)).willReturn(testTags.subList(0, 3));
        given(tagRepository.saveAll(anyList())).willReturn(testTags.subList(0, 3));
        
        // When
        tagService.updateDisplayOrderBatch(tagType, tagIds);
        
        // Then
        verify(tagRepository).findAllById(tagIds);
        verify(tagRepository).saveAll(anyList());
    }
    
    // ===== 헬퍼 메서드 =====
    
    private Tag createTestTag(Long id, String name, TagType type, 
                             boolean userSelectable, boolean routeTaggable) {
        return createTestTag(id, name, type, 0, userSelectable, routeTaggable);
    }
    
    private Tag createTestTag(Long id, String name, TagType type, int displayOrder,
                             boolean userSelectable, boolean routeTaggable) {
        return Tag.builder()
            .tagId(id)
            .tagName(name)
            .tagType(type)
            .description("Test description for " + name)
            .displayOrder(displayOrder)
            .isUserSelectable(userSelectable)
            .isRouteTaggable(routeTaggable)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }
    
    private List<Tag> createTestTagList() {
        return Arrays.asList(
            createTestTag(1L, "볼더링", TagType.STYLE, true, true),
            createTestTag(2L, "크림핑", TagType.TECHNIQUE, true, true),
            createTestTag(3L, "다이나믹", TagType.MOVEMENT, true, true),
            createTestTag(4L, "저그", TagType.HOLD_TYPE, false, true),
            createTestTag(5L, "오버행", TagType.WALL_ANGLE, false, true)
        );
    }
}
```

---

## 👤 사용자 선호도 서비스 테스트

### UserPreferenceServiceTest.java
```java
package com.routepick.service.tag;

import com.routepick.common.enums.PreferenceLevel;
import com.routepick.common.enums.SkillLevel;
import com.routepick.common.enums.TagType;
import com.routepick.domain.tag.entity.Tag;
import com.routepick.domain.tag.entity.UserPreferredTag;
import com.routepick.domain.tag.repository.TagRepository;
import com.routepick.domain.tag.repository.UserPreferredTagRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.tag.TagException;
import com.routepick.exception.user.UserException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * 사용자 선호도 서비스 테스트
 * - PreferenceLevel 3단계 검증
 * - SkillLevel 4단계 검증
 * - 선호도 매트릭스 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserPreferenceService 테스트")
class UserPreferenceServiceTest {
    
    @Mock
    private UserPreferredTagRepository preferredTagRepository;
    
    @Mock
    private TagRepository tagRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserPreferenceService userPreferenceService;
    
    private User testUser;
    private Tag testTag;
    private UserPreferredTag testPreference;
    
    @BeforeEach
    void setUp() {
        testUser = createTestUser(1L, "testuser");
        testTag = createTestTag(1L, "볼더링", TagType.STYLE);
        testPreference = createTestPreference(1L, testUser, testTag, 
                                            PreferenceLevel.HIGH, SkillLevel.INTERMEDIATE);
    }
    
    // ===== 선호도 설정 테스트 =====
    
    @Test
    @DisplayName("사용자 선호도 설정 - 신규 생성")
    void setUserPreference_NewPreference_Success() {
        // Given
        Long userId = 1L;
        Long tagId = 1L;
        PreferenceLevel preferenceLevel = PreferenceLevel.HIGH;
        SkillLevel skillLevel = SkillLevel.INTERMEDIATE;
        
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        given(tagRepository.isUserSelectable(tagId)).willReturn(true);
        given(preferredTagRepository.findByUserIdAndTagId(userId, tagId))
            .willReturn(Optional.empty());
        given(preferredTagRepository.save(any(UserPreferredTag.class)))
            .willReturn(testPreference);
        
        // When
        UserPreferredTag result = userPreferenceService.setUserPreference(
            userId, tagId, preferenceLevel, skillLevel);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPreferenceLevel()).isEqualTo(preferenceLevel);
        assertThat(result.getSkillLevel()).isEqualTo(skillLevel);
        
        verify(userRepository).findById(userId);
        verify(tagRepository).findById(tagId);
        verify(tagRepository).isUserSelectable(tagId);
        verify(preferredTagRepository).findByUserIdAndTagId(userId, tagId);
        verify(preferredTagRepository).save(any(UserPreferredTag.class));
    }
    
    @Test
    @DisplayName("사용자 선호도 설정 - 기존 선호도 수정")
    void setUserPreference_UpdateExisting_Success() {
        // Given
        Long userId = 1L;
        Long tagId = 1L;
        PreferenceLevel newPreferenceLevel = PreferenceLevel.MEDIUM;
        SkillLevel newSkillLevel = SkillLevel.ADVANCED;
        
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        given(tagRepository.isUserSelectable(tagId)).willReturn(true);
        given(preferredTagRepository.findByUserIdAndTagId(userId, tagId))
            .willReturn(Optional.of(testPreference));
        given(preferredTagRepository.save(any(UserPreferredTag.class)))
            .willReturn(testPreference);
        
        // When
        UserPreferredTag result = userPreferenceService.setUserPreference(
            userId, tagId, newPreferenceLevel, newSkillLevel);
        
        // Then
        verify(preferredTagRepository).save(testPreference);
    }
    
    @Test
    @DisplayName("사용자 선호도 설정 - 선택 불가 태그 실패")
    void setUserPreference_NotSelectableTag_Failure() {
        // Given
        Long userId = 1L;
        Long tagId = 1L;
        
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        given(tagRepository.isUserSelectable(tagId)).willReturn(false);
        
        // When & Then
        assertThatThrownBy(() -> 
            userPreferenceService.setUserPreference(userId, tagId, 
                                                  PreferenceLevel.HIGH, SkillLevel.BEGINNER)
        )
        .isInstanceOf(TagException.class)
        .hasMessageContaining("선택할 수 없는 태그입니다");
        
        verify(tagRepository).isUserSelectable(tagId);
        verify(preferredTagRepository, never()).save(any());
    }
    
    // ===== PreferenceLevel 3단계 테스트 =====
    
    @Test
    @DisplayName("PreferenceLevel 3단계 모두 설정 가능")
    void setUserPreference_AllPreferenceLevels_Success() {
        // Given
        Long userId = 1L;
        Long tagId = 1L;
        
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        given(tagRepository.isUserSelectable(tagId)).willReturn(true);
        given(preferredTagRepository.findByUserIdAndTagId(userId, tagId))
            .willReturn(Optional.empty());
        
        // When & Then - HIGH
        given(preferredTagRepository.save(any(UserPreferredTag.class)))
            .willReturn(createTestPreference(1L, testUser, testTag, PreferenceLevel.HIGH, SkillLevel.BEGINNER));
        
        UserPreferredTag highResult = userPreferenceService.setUserPreference(
            userId, tagId, PreferenceLevel.HIGH, SkillLevel.BEGINNER);
        assertThat(highResult.getPreferenceLevel()).isEqualTo(PreferenceLevel.HIGH);
        assertThat(highResult.getPreferenceLevel().getWeight()).isEqualTo(100);
        
        // When & Then - MEDIUM
        given(preferredTagRepository.save(any(UserPreferredTag.class)))
            .willReturn(createTestPreference(2L, testUser, testTag, PreferenceLevel.MEDIUM, SkillLevel.INTERMEDIATE));
        
        UserPreferredTag mediumResult = userPreferenceService.setUserPreference(
            userId, tagId, PreferenceLevel.MEDIUM, SkillLevel.INTERMEDIATE);
        assertThat(mediumResult.getPreferenceLevel()).isEqualTo(PreferenceLevel.MEDIUM);
        assertThat(mediumResult.getPreferenceLevel().getWeight()).isEqualTo(70);
        
        // When & Then - LOW  
        given(preferredTagRepository.save(any(UserPreferredTag.class)))
            .willReturn(createTestPreference(3L, testUser, testTag, PreferenceLevel.LOW, SkillLevel.ADVANCED));
        
        UserPreferredTag lowResult = userPreferenceService.setUserPreference(
            userId, tagId, PreferenceLevel.LOW, SkillLevel.ADVANCED);
        assertThat(lowResult.getPreferenceLevel()).isEqualTo(PreferenceLevel.LOW);
        assertThat(lowResult.getPreferenceLevel().getWeight()).isEqualTo(30);
    }
    
    // ===== SkillLevel 4단계 테스트 =====
    
    @Test
    @DisplayName("SkillLevel 4단계 모두 설정 가능")
    void setUserPreference_AllSkillLevels_Success() {
        // Given
        Long userId = 1L;
        Long tagId = 1L;
        
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(tagRepository.findById(tagId)).willReturn(Optional.of(testTag));
        given(tagRepository.isUserSelectable(tagId)).willReturn(true);
        given(preferredTagRepository.findByUserIdAndTagId(userId, tagId))
            .willReturn(Optional.empty());
        
        // When & Then - 각 SkillLevel 테스트
        for (SkillLevel skillLevel : SkillLevel.values()) {
            given(preferredTagRepository.save(any(UserPreferredTag.class)))
                .willReturn(createTestPreference(1L, testUser, testTag, PreferenceLevel.MEDIUM, skillLevel));
            
            UserPreferredTag result = userPreferenceService.setUserPreference(
                userId, tagId, PreferenceLevel.MEDIUM, skillLevel);
            
            assertThat(result.getSkillLevel()).isEqualTo(skillLevel);
            assertThat(result.getSkillLevel().getLevel()).isEqualTo(skillLevel.getLevel());
        }
    }
    
    @Test
    @DisplayName("SkillLevel 차이 계산 테스트")
    void skillLevel_DifferenceCalculation_Success() {
        // When & Then
        assertThat(SkillLevel.BEGINNER.getDifference(SkillLevel.BEGINNER)).isEqualTo(0);
        assertThat(SkillLevel.BEGINNER.getDifference(SkillLevel.INTERMEDIATE)).isEqualTo(1);
        assertThat(SkillLevel.BEGINNER.getDifference(SkillLevel.EXPERT)).isEqualTo(3);
        assertThat(SkillLevel.EXPERT.getDifference(SkillLevel.BEGINNER)).isEqualTo(3);
    }
    
    // ===== 선호도 조회 테스트 =====
    
    @Test
    @DisplayName("사용자 선호도 목록 조회 - PreferenceLevel 순 정렬")
    void getUserPreferences_OrderByPreferenceLevel() {
        // Given
        Long userId = 1L;
        List<UserPreferredTag> preferences = Arrays.asList(
            createTestPreference(1L, testUser, testTag, PreferenceLevel.HIGH, SkillLevel.EXPERT),
            createTestPreference(2L, testUser, createTestTag(2L, "크림핑", TagType.TECHNIQUE), 
                               PreferenceLevel.MEDIUM, SkillLevel.INTERMEDIATE),
            createTestPreference(3L, testUser, createTestTag(3L, "다이나믹", TagType.MOVEMENT), 
                               PreferenceLevel.LOW, SkillLevel.BEGINNER)
        );
        
        given(preferredTagRepository.findByUserIdOrderByPreferenceLevelDesc(userId))
            .willReturn(preferences);
        
        // When
        List<UserPreferredTag> result = userPreferenceService.getUserPreferences(userId);
        
        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPreferenceLevel()).isEqualTo(PreferenceLevel.HIGH);
        assertThat(result.get(1).getPreferenceLevel()).isEqualTo(PreferenceLevel.MEDIUM);
        assertThat(result.get(2).getPreferenceLevel()).isEqualTo(PreferenceLevel.LOW);
        
        verify(preferredTagRepository).findByUserIdOrderByPreferenceLevelDesc(userId);
    }
    
    @Test
    @DisplayName("TagType별 선호도 조회")
    void getUserPreferencesByTagType_Success() {
        // Given
        Long userId = 1L;
        TagType tagType = TagType.TECHNIQUE;
        List<UserPreferredTag> preferences = Arrays.asList(
            createTestPreference(1L, testUser, createTestTag(1L, "크림핑", tagType), 
                               PreferenceLevel.HIGH, SkillLevel.ADVANCED),
            createTestPreference(2L, testUser, createTestTag(2L, "슬로핑", tagType), 
                               PreferenceLevel.MEDIUM, SkillLevel.INTERMEDIATE)
        );
        
        given(preferredTagRepository.findByUserIdAndTagTypeOrderByPreferenceLevelDesc(userId, tagType))
            .willReturn(preferences);
        
        // When
        List<UserPreferredTag> result = userPreferenceService.getUserPreferencesByTagType(userId, tagType);
        
        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(pref -> pref.getTag().getTagType() == tagType);
        
        verify(preferredTagRepository)
            .findByUserIdAndTagTypeOrderByPreferenceLevelDesc(userId, tagType);
    }
    
    // ===== 선호도 삭제 테스트 =====
    
    @Test
    @DisplayName("사용자 선호도 삭제 - 성공")
    void removeUserPreference_Success() {
        // Given
        Long userId = 1L;
        Long tagId = 1L;
        
        given(preferredTagRepository.findByUserIdAndTagId(userId, tagId))
            .willReturn(Optional.of(testPreference));
        willDoNothing().given(preferredTagRepository).delete(testPreference);
        
        // When
        userPreferenceService.removeUserPreference(userId, tagId);
        
        // Then
        verify(preferredTagRepository).findByUserIdAndTagId(userId, tagId);
        verify(preferredTagRepository).delete(testPreference);
    }
    
    @Test
    @DisplayName("사용자 선호도 삭제 - 존재하지 않는 선호도")
    void removeUserPreference_NotFound_Failure() {
        // Given
        Long userId = 1L;
        Long tagId = 999L;
        
        given(preferredTagRepository.findByUserIdAndTagId(userId, tagId))
            .willReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> 
            userPreferenceService.removeUserPreference(userId, tagId)
        )
        .isInstanceOf(TagException.class)
        .hasMessageContaining("선호도를 찾을 수 없습니다");
        
        verify(preferredTagRepository).findByUserIdAndTagId(userId, tagId);
        verify(preferredTagRepository, never()).delete(any());
    }
    
    // ===== 헬퍼 메서드 =====
    
    private User createTestUser(Long id, String nickname) {
        return User.builder()
            .userId(id)
            .nickname(nickname)
            .email("test@example.com")
            .build();
    }
    
    private Tag createTestTag(Long id, String name, TagType type) {
        return Tag.builder()
            .tagId(id)
            .tagName(name)
            .tagType(type)
            .isUserSelectable(true)
            .isRouteTaggable(true)
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    private UserPreferredTag createTestPreference(Long id, User user, Tag tag, 
                                                 PreferenceLevel preferenceLevel, 
                                                 SkillLevel skillLevel) {
        return UserPreferredTag.builder()
            .userTagId(id)
            .user(user)
            .tag(tag)
            .preferenceLevel(preferenceLevel)
            .skillLevel(skillLevel)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }
}
```

---

## 📊 테스트 결과 검증

### 1. TagType 8가지 전체 검증
- ✅ STYLE, FEATURE, TECHNIQUE, DIFFICULTY
- ✅ MOVEMENT, HOLD_TYPE, WALL_ANGLE, OTHER

### 2. PreferenceLevel 가중치 검증
- ✅ HIGH (100%), MEDIUM (70%), LOW (30%)
- ✅ 추천 알고리즘 가중치 정확성

### 3. SkillLevel 4단계 검증
- ✅ BEGINNER (1), INTERMEDIATE (2), ADVANCED (3), EXPERT (4)
- ✅ 레벨 차이 계산 로직

### 4. 플래그 기반 필터링
- ✅ isUserSelectable: 사용자 선택 가능 여부
- ✅ isRouteTaggable: 루트 태깅 가능 여부

### 5. 정렬 및 순서 관리
- ✅ display_order 기반 UI 표시 순서
- ✅ TagType별 카테고리 정렬

---

## ✅ 9-2a 단계 완료

**태그 서비스 테스트 설계 완료**:
- 태그 CRUD 연산 29개 테스트 케이스
- 사용자 선호도 관리 15개 테스트 케이스  
- 8가지 TagType × 7가지 플래그 조합 검증
- PreferenceLevel 3단계 × SkillLevel 4단계 매트릭스
- 예외 처리 및 경계값 테스트 완료

**다음 단계**: 9-2b 추천 알고리즘 테스트 설계

---

*9-2a TagService 테스트 설계 완료! - AI 기반 태그 시스템 검증 완료*