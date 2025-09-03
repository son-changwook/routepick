# Step 6-3b2: UserPreferenceService - 통계 및 분석

> 사용자 선호도 통계, 유사 사용자 분석, 시각화 데이터 제공  
> 생성일: 2025-08-21  
> 단계: 6-3b2 (Service 레이어 - 사용자 선호도 분석)  
> 참고: step4-2a, step5-2a, step6-3b1

---

## 🎯 설계 목표

- **선호도 통계**: PreferenceLevel/SkillLevel별 분포 분석
- **유사 사용자**: 공통 태그 기반 유사도 계산
- **TagType 분석**: 태그 타입별 선호도 분포
- **시각화 데이터**: 대시보드용 차트 데이터 제공
- **추천 적격성**: 추천 시스템 사용 가능 여부 판단

---

## 📊 UserPreferenceAnalyticsService - 선호도 분석 서비스

### UserPreferenceAnalyticsService.java (분석 전용)
```java
package com.routepick.service.tag;

import com.routepick.common.enums.PreferenceLevel;
import com.routepick.common.enums.SkillLevel;
import com.routepick.common.enums.TagType;
import com.routepick.domain.tag.entity.UserPreferredTag;
import com.routepick.domain.tag.repository.UserPreferredTagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 사용자 선호도 통계 및 분석 서비스
 * 
 * 주요 기능:
 * - 사용자별 태그 통계 제공
 * - 선호도/스킬 분포 분석
 * - 유사 사용자 찾기
 * - TagType별 선호도 분포
 * - 시각화용 차트 데이터 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPreferenceAnalyticsService {

    private final UserPreferredTagRepository userPreferredTagRepository;
    private final UserPreferenceService userPreferenceService;
    
    @Value("${routepick.preference.min-tags-for-recommendation:3}")
    private int minTagsForRecommendation;

    // ===== 사용자별 태그 통계 =====

    /**
     * 사용자 태그 통계 조회
     */
    @Cacheable(value = "user-tag-statistics", key = "#userId")
    public UserTagStatisticsDto getUserTagStatistics(Long userId) {
        
        List<UserPreferredTag> userTags = userPreferenceService.getUserPreferences(userId);
        
        // 선호도별 분포
        Map<PreferenceLevel, Long> preferenceDistribution = userTags.stream()
            .collect(Collectors.groupingBy(
                UserPreferredTag::getPreferenceLevel,
                Collectors.counting()
            ));
        
        // 스킬별 분포
        Map<SkillLevel, Long> skillDistribution = userTags.stream()
            .collect(Collectors.groupingBy(
                UserPreferredTag::getSkillLevel,
                Collectors.counting()
            ));
        
        // TagType별 분포
        Map<TagType, Long> tagTypeDistribution = userTags.stream()
            .collect(Collectors.groupingBy(
                ut -> ut.getTag().getTagType(),
                Collectors.counting()
            ));
        
        // 평균 경험 개월 수
        double avgExperienceMonths = userTags.stream()
            .mapToInt(ut -> ut.getExperienceMonths() != null ? ut.getExperienceMonths() : 0)
            .average()
            .orElse(0.0);
        
        // 추천 가중치 합계
        double totalRecommendationWeight = userTags.stream()
            .mapToDouble(UserPreferredTag::getRecommendationWeight)
            .sum();
        
        return UserTagStatisticsDto.builder()
            .userId(userId)
            .totalTags(userTags.size())
            .preferenceDistribution(preferenceDistribution)
            .skillDistribution(skillDistribution)
            .tagTypeDistribution(tagTypeDistribution)
            .avgExperienceMonths(avgExperienceMonths)
            .totalRecommendationWeight(totalRecommendationWeight)
            .isEligibleForRecommendation(userTags.size() >= minTagsForRecommendation)
            .build();
    }

    /**
     * 유사 선호도 사용자 찾기
     */
    @Cacheable(value = "similar-users", key = "#userId + '_' + #limit")
    public List<SimilarUserDto> findSimilarUsers(Long userId, int limit) {
        
        List<UserPreferredTag> userTags = userPreferenceService.getUserPreferences(userId);
        if (userTags.isEmpty()) {
            return List.of();
        }
        
        Set<Long> userTagIds = userTags.stream()
            .map(ut -> ut.getTag().getTagId())
            .collect(Collectors.toSet());
        
        // Repository에서 유사 사용자 조회
        return userPreferredTagRepository.findSimilarUsers(userId, userTagIds, limit);
    }

    /**
     * 사용자 선호도 프로필 요약
     */
    @Cacheable(value = "user-preference-profile", key = "#userId")
    public UserPreferenceProfileDto getUserPreferenceProfile(Long userId) {
        
        List<UserPreferredTag> userTags = userPreferenceService.getUserPreferences(userId);
        
        // 최고 선호도 태그들 (HIGH level)
        List<String> topPreferences = userTags.stream()
            .filter(ut -> ut.getPreferenceLevel() == PreferenceLevel.HIGH)
            .map(ut -> ut.getTag().getTagName())
            .collect(Collectors.toList());
        
        // 고급 스킬 태그들 (ADVANCED, EXPERT)
        List<String> advancedSkills = userTags.stream()
            .filter(ut -> ut.getSkillLevel() == SkillLevel.ADVANCED || 
                         ut.getSkillLevel() == SkillLevel.EXPERT)
            .map(ut -> ut.getTag().getTagName())
            .collect(Collectors.toList());
        
        // 주요 TagType들
        Set<TagType> primaryTagTypes = userTags.stream()
            .filter(ut -> ut.getPreferenceLevel().getWeight() >= 0.5) // MEDIUM 이상
            .map(ut -> ut.getTag().getTagType())
            .collect(Collectors.toSet());
        
        // 전체 경험 레벨 (평균)
        SkillLevel overallSkillLevel = calculateOverallSkillLevel(userTags);
        
        return UserPreferenceProfileDto.builder()
            .userId(userId)
            .topPreferences(topPreferences)
            .advancedSkills(advancedSkills)
            .primaryTagTypes(primaryTagTypes)
            .overallSkillLevel(overallSkillLevel)
            .totalExperienceMonths(calculateTotalExperience(userTags))
            .preferenceScore(calculatePreferenceScore(userTags))
            .build();
    }

    // ===== 선호도 분포 분석 =====

    /**
     * 선호도 레벨별 상세 분포
     */
    @Cacheable(value = "preference-level-distribution", key = "#userId")
    public PreferenceLevelDistributionDto getPreferenceLevelDistribution(Long userId) {
        
        List<UserPreferredTag> userTags = userPreferenceService.getUserPreferences(userId);
        
        Map<PreferenceLevel, List<TagSummaryDto>> levelGroups = userTags.stream()
            .collect(Collectors.groupingBy(
                UserPreferredTag::getPreferenceLevel,
                Collectors.mapping(
                    this::convertToTagSummary,
                    Collectors.toList()
                )
            ));
        
        return PreferenceLevelDistributionDto.builder()
            .userId(userId)
            .highPreferences(levelGroups.getOrDefault(PreferenceLevel.HIGH, List.of()))
            .mediumPreferences(levelGroups.getOrDefault(PreferenceLevel.MEDIUM, List.of()))
            .lowPreferences(levelGroups.getOrDefault(PreferenceLevel.LOW, List.of()))
            .totalTags(userTags.size())
            .build();
    }

    /**
     * 스킬 레벨별 상세 분포
     */
    @Cacheable(value = "skill-level-distribution", key = "#userId")
    public SkillLevelDistributionDto getSkillLevelDistribution(Long userId) {
        
        List<UserPreferredTag> userTags = userPreferenceService.getUserPreferences(userId);
        
        Map<SkillLevel, List<TagSummaryDto>> skillGroups = userTags.stream()
            .collect(Collectors.groupingBy(
                UserPreferredTag::getSkillLevel,
                Collectors.mapping(
                    this::convertToTagSummary,
                    Collectors.toList()
                )
            ));
        
        return SkillLevelDistributionDto.builder()
            .userId(userId)
            .expertTags(skillGroups.getOrDefault(SkillLevel.EXPERT, List.of()))
            .advancedTags(skillGroups.getOrDefault(SkillLevel.ADVANCED, List.of()))
            .intermediateTags(skillGroups.getOrDefault(SkillLevel.INTERMEDIATE, List.of()))
            .beginnerTags(skillGroups.getOrDefault(SkillLevel.BEGINNER, List.of()))
            .totalTags(userTags.size())
            .build();
    }

    /**
     * TagType별 선호도 분포 (차트용)
     */
    @Cacheable(value = "tagtype-distribution-chart", key = "#userId")
    public List<TagTypeChartDataDto> getTagTypeDistributionChart(Long userId) {
        
        List<UserPreferredTag> userTags = userPreferenceService.getUserPreferences(userId);
        
        return userTags.stream()
            .collect(Collectors.groupingBy(
                ut -> ut.getTag().getTagType(),
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    tags -> TagTypeChartDataDto.builder()
                        .tagType(tags.get(0).getTag().getTagType())
                        .count(tags.size())
                        .avgPreferenceLevel(calculateAvgPreferenceLevel(tags))
                        .avgSkillLevel(calculateAvgSkillLevel(tags))
                        .totalWeight(tags.stream()
                            .mapToDouble(UserPreferredTag::getRecommendationWeight)
                            .sum())
                        .build()
                )
            ))
            .values()
            .stream()
            .sorted(Comparator.comparing(TagTypeChartDataDto::getTotalWeight).reversed())
            .collect(Collectors.toList());
    }

    // ===== 추천 시스템 지원 =====

    /**
     * 추천용 가중치 맵 생성
     */
    @Cacheable(value = "recommendation-weights", key = "#userId")
    public Map<Long, Double> getRecommendationWeights(Long userId) {
        
        List<UserPreferredTag> userTags = userPreferenceService.getUserPreferences(userId);
        
        return userTags.stream()
            .collect(Collectors.toMap(
                ut -> ut.getTag().getTagId(),
                UserPreferredTag::getRecommendationWeight,
                (existing, replacement) -> existing
            ));
    }

    /**
     * 선호도 기반 태그 우선순위 리스트
     */
    @Cacheable(value = "preference-priority-tags", key = "#userId")
    public List<PreferredTagPriorityDto> getPreferenceBasedPriority(Long userId) {
        
        List<UserPreferredTag> userTags = userPreferenceService.getUserPreferences(userId);
        
        return userTags.stream()
            .map(ut -> PreferredTagPriorityDto.builder()
                .tagId(ut.getTag().getTagId())
                .tagName(ut.getTag().getTagName())
                .tagType(ut.getTag().getTagType())
                .preferenceLevel(ut.getPreferenceLevel())
                .skillLevel(ut.getSkillLevel())
                .weight(ut.getRecommendationWeight())
                .priority(calculatePriority(ut))
                .build())
            .sorted(Comparator.comparing(PreferredTagPriorityDto::getPriority).reversed())
            .collect(Collectors.toList());
    }

    // ===== 유틸리티 메서드 =====

    /**
     * UserPreferredTag를 TagSummaryDto로 변환
     */
    private TagSummaryDto convertToTagSummary(UserPreferredTag userTag) {
        return TagSummaryDto.builder()
            .tagId(userTag.getTag().getTagId())
            .tagName(userTag.getTag().getTagName())
            .tagType(userTag.getTag().getTagType())
            .preferenceLevel(userTag.getPreferenceLevel())
            .skillLevel(userTag.getSkillLevel())
            .experienceMonths(userTag.getExperienceMonths())
            .weight(userTag.getRecommendationWeight())
            .build();
    }

    /**
     * 전체 스킬 레벨 계산
     */
    private SkillLevel calculateOverallSkillLevel(List<UserPreferredTag> userTags) {
        if (userTags.isEmpty()) return SkillLevel.BEGINNER;
        
        double avgLevel = userTags.stream()
            .mapToDouble(ut -> ut.getSkillLevel().getLevel())
            .average()
            .orElse(1.0);
        
        if (avgLevel >= 3.5) return SkillLevel.EXPERT;
        if (avgLevel >= 2.5) return SkillLevel.ADVANCED;
        if (avgLevel >= 1.5) return SkillLevel.INTERMEDIATE;
        return SkillLevel.BEGINNER;
    }

    /**
     * 총 경험 개월 수 계산
     */
    private int calculateTotalExperience(List<UserPreferredTag> userTags) {
        return userTags.stream()
            .mapToInt(ut -> ut.getExperienceMonths() != null ? ut.getExperienceMonths() : 0)
            .sum();
    }

    /**
     * 전체 선호도 점수 계산
     */
    private double calculatePreferenceScore(List<UserPreferredTag> userTags) {
        return userTags.stream()
            .mapToDouble(UserPreferredTag::getRecommendationWeight)
            .sum();
    }

    /**
     * 평균 선호도 레벨 계산
     */
    private double calculateAvgPreferenceLevel(List<UserPreferredTag> tags) {
        return tags.stream()
            .mapToDouble(ut -> ut.getPreferenceLevel().getWeight())
            .average()
            .orElse(0.0);
    }

    /**
     * 평균 스킬 레벨 계산
     */
    private double calculateAvgSkillLevel(List<UserPreferredTag> tags) {
        return tags.stream()
            .mapToDouble(ut -> ut.getSkillLevel().getLevel())
            .average()
            .orElse(0.0);
    }

    /**
     * 태그 우선순위 계산
     */
    private double calculatePriority(UserPreferredTag userTag) {
        double preferenceWeight = userTag.getPreferenceLevel().getWeight();
        double skillWeight = userTag.getSkillLevel().getLevel() / 4.0; // 정규화
        double experienceWeight = Math.min((userTag.getExperienceMonths() != null ? 
                                          userTag.getExperienceMonths() : 0) / 12.0, 1.0);
        
        return (preferenceWeight * 0.5) + (skillWeight * 0.3) + (experienceWeight * 0.2);
    }

    // ===== DTO 클래스들 =====

    /**
     * 사용자 태그 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class UserTagStatisticsDto {
        private final Long userId;
        private final int totalTags;
        private final Map<PreferenceLevel, Long> preferenceDistribution;
        private final Map<SkillLevel, Long> skillDistribution;
        private final Map<TagType, Long> tagTypeDistribution;
        private final double avgExperienceMonths;
        private final double totalRecommendationWeight;
        private final boolean isEligibleForRecommendation;
    }

    /**
     * 유사 사용자 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class SimilarUserDto {
        private final Long userId;
        private final String nickname;
        private final int commonTagCount;
        private final double similarityScore;
    }

    /**
     * 사용자 선호도 프로필 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class UserPreferenceProfileDto {
        private final Long userId;
        private final List<String> topPreferences;
        private final List<String> advancedSkills;
        private final Set<TagType> primaryTagTypes;
        private final SkillLevel overallSkillLevel;
        private final int totalExperienceMonths;
        private final double preferenceScore;
    }

    /**
     * 태그 요약 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class TagSummaryDto {
        private final Long tagId;
        private final String tagName;
        private final TagType tagType;
        private final PreferenceLevel preferenceLevel;
        private final SkillLevel skillLevel;
        private final Integer experienceMonths;
        private final double weight;
    }

    /**
     * 선호도 레벨 분포 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class PreferenceLevelDistributionDto {
        private final Long userId;
        private final List<TagSummaryDto> highPreferences;
        private final List<TagSummaryDto> mediumPreferences;
        private final List<TagSummaryDto> lowPreferences;
        private final int totalTags;
    }

    /**
     * 스킬 레벨 분포 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class SkillLevelDistributionDto {
        private final Long userId;
        private final List<TagSummaryDto> expertTags;
        private final List<TagSummaryDto> advancedTags;
        private final List<TagSummaryDto> intermediateTags;
        private final List<TagSummaryDto> beginnerTags;
        private final int totalTags;
    }

    /**
     * TagType 차트 데이터 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class TagTypeChartDataDto {
        private final TagType tagType;
        private final int count;
        private final double avgPreferenceLevel;
        private final double avgSkillLevel;
        private final double totalWeight;
    }

    /**
     * 선호 태그 우선순위 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class PreferredTagPriorityDto {
        private final Long tagId;
        private final String tagName;
        private final TagType tagType;
        private final PreferenceLevel preferenceLevel;
        private final SkillLevel skillLevel;
        private final double weight;
        private final double priority;
    }
}
```

---

## 📊 주요 분석 기능

### **1. 사용자 태그 통계**
- **선호도 분포**: HIGH, MEDIUM, LOW별 태그 수
- **스킬 분포**: BEGINNER~EXPERT별 태그 수  
- **TagType 분포**: 6가지 태그 타입별 분포
- **경험 통계**: 평균 경험 개월 수, 총 가중치

### **2. 유사 사용자 분석**
- **공통 태그 매칭**: 같은 선호 태그 보유 사용자
- **유사도 점수**: 가중치 기반 유사성 계산
- **추천 알고리즘**: 협업 필터링 지원

### **3. 시각화 데이터**
- **차트 데이터**: 대시보드용 분포 차트
- **우선순위**: 선호도/스킬/경험 통합 점수
- **프로필 요약**: 사용자 특성 한눈에 보기

### **4. 추천 시스템 지원**
- **가중치 맵**: 태그별 추천 가중치
- **적격성 판단**: 최소 태그 수 확인
- **우선순위 리스트**: 개인화 추천용 순서

---

## 💾 **Redis 캐싱 전략**

### 분석 데이터 캐싱
- **통계 데이터**: `user-tag-statistics:{userId}`
- **유사 사용자**: `similar-users:{userId}_{limit}`
- **선호도 프로필**: `user-preference-profile:{userId}`
- **차트 데이터**: `tagtype-distribution-chart:{userId}`

### 추천 시스템 캐싱
- **가중치 맵**: `recommendation-weights:{userId}`
- **우선순위**: `preference-priority-tags:{userId}`

---

## 🚀 **활용 방안**

**대시보드 활용:**
- 사용자 선호도 시각화
- 개인화 추천 근거 제시
- 커뮤니티 매칭 지원

**추천 시스템 연계:**
- step6-3d RecommendationService 지원
- 가중치 기반 루트 추천
- 유사 사용자 기반 협업 필터링

*step6-3b2 완성: UserPreferenceService 통계 분석 완료*