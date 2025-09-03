# Step 6-3b: UserPreferenceService 구현

> 사용자 선호 태그 관리 서비스 - PreferenceLevel, SkillLevel, 추천 재계산 트리거  
> 생성일: 2025-08-21  
> 단계: 6-3b (Service 레이어 - 사용자 선호도)  
> 참고: step4-2a, step5-2a, step6-1a

---

## 🎯 설계 목표

- **사용자 선호 태그 관리**: UserPreferredTag 엔티티 CRUD
- **PreferenceLevel 관리**: LOW, MEDIUM, HIGH 3단계 선호도
- **SkillLevel 관리**: BEGINNER, INTERMEDIATE, ADVANCED, EXPERT 4단계 숙련도
- **추천 재계산 트리거**: 선호도 변경 시 이벤트 발행
- **사용자별 태그 통계**: 선호도 분석 및 시각화 데이터 제공

---

## 👤 UserPreferenceService - 사용자 선호도 관리 서비스

### UserPreferenceService.java
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
import com.routepick.event.UserPreferenceChangedEvent;
import com.routepick.exception.tag.TagException;
import com.routepick.exception.user.UserException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 사용자 선호 태그 관리 서비스
 * 
 * 주요 기능:
 * - 사용자 선호 태그 설정/조회
 * - PreferenceLevel 관리 (LOW, MEDIUM, HIGH)
 * - SkillLevel 관리 (BEGINNER~EXPERT)
 * - 선호도 업데이트 시 추천 재계산 트리거
 * - 사용자별 태그 통계 제공
 * - ApplicationEventPublisher 이벤트 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPreferenceService {

    private final UserPreferredTagRepository userPreferredTagRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    @Value("${routepick.preference.max-tags-per-user:20}")
    private int maxTagsPerUser;
    
    @Value("${routepick.preference.min-tags-for-recommendation:3}")
    private int minTagsForRecommendation;
    
    @Value("${routepick.preference.skill-upgrade-months:6}")
    private int skillUpgradeMonths;

    // ===== 사용자 선호 태그 설정 =====

    /**
     * 사용자 선호 태그 추가
     */
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-tag-statistics"}, key = "#userId")
    public UserPreferredTag addUserPreference(Long userId, Long tagId, 
                                             PreferenceLevel preferenceLevel,
                                             SkillLevel skillLevel,
                                             Integer experienceMonths) {
        
        // 사용자 존재 검증
        User user = userRepository.findById(userId)
            .orElseThrow(() -> UserException.userNotFound(userId));
        
        // 태그 존재 및 선택 가능 검증
        Tag tag = tagRepository.findById(tagId)
            .orElseThrow(() -> TagException.tagNotFound(tagId));
            
        if (!tag.isUserSelectable()) {
            throw TagException.tagNotSelectableByUser(tagId);
        }
        
        // 기존 선호 태그 확인
        Optional<UserPreferredTag> existing = userPreferredTagRepository
            .findByUserIdAndTagId(userId, tagId);
            
        if (existing.isPresent()) {
            // 기존 태그 업데이트
            UserPreferredTag userTag = existing.get();
            if (!userTag.isActive()) {
                userTag.setActive(true); // 재활성화
            }
            userTag.setPreferenceLevel(preferenceLevel != null ? preferenceLevel : userTag.getPreferenceLevel());
            userTag.setSkillLevel(skillLevel != null ? skillLevel : userTag.getSkillLevel());
            if (experienceMonths != null) {
                userTag.setExperienceMonths(experienceMonths);
            }
            
            // 추천 재계산 이벤트 발행
            publishPreferenceChangedEvent(userId, tagId, "UPDATE");
            
            log.info("사용자 선호 태그 업데이트 - userId: {}, tagId: {}, preference: {}", 
                    userId, tagId, preferenceLevel);
            return userTag;
        }
        
        // 최대 태그 수 제한 확인
        long currentTagCount = userPreferredTagRepository.countActiveByUserId(userId);
        if (currentTagCount >= maxTagsPerUser) {
            throw TagException.maxUserTagsExceeded(userId, maxTagsPerUser);
        }
        
        // 새 선호 태그 생성
        UserPreferredTag userTag = UserPreferredTag.builder()
            .user(user)
            .tag(tag)
            .preferenceLevel(preferenceLevel != null ? preferenceLevel : PreferenceLevel.MEDIUM)
            .skillLevel(skillLevel != null ? skillLevel : SkillLevel.BEGINNER)
            .experienceMonths(experienceMonths != null ? experienceMonths : 0)
            .isActive(true)
            .build();
            
        UserPreferredTag savedUserTag = userPreferredTagRepository.save(userTag);
        
        // 태그 사용 횟수 증가
        tag.incrementUsageCount();
        
        // 추천 재계산 이벤트 발행
        publishPreferenceChangedEvent(userId, tagId, "ADD");
        
        log.info("사용자 선호 태그 추가 - userId: {}, tagId: {}, preference: {}", 
                userId, tagId, preferenceLevel);
        return savedUserTag;
    }

    /**
     * 사용자 선호 태그 일괄 설정
     */
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-tag-statistics"}, key = "#userId")
    public List<UserPreferredTag> setUserPreferences(Long userId, 
                                                    Map<Long, PreferenceLevel> tagPreferences) {
        
        // 사용자 존재 검증
        User user = userRepository.findById(userId)
            .orElseThrow(() -> UserException.userNotFound(userId));
        
        // 최대 태그 수 제한
        if (tagPreferences.size() > maxTagsPerUser) {
            throw TagException.maxUserTagsExceeded(userId, maxTagsPerUser);
        }
        
        // 기존 활성 태그 비활성화
        userPreferredTagRepository.deactivateAllUserTags(userId);
        
        // 새 태그 설정
        List<UserPreferredTag> userTags = new ArrayList<>();
        for (Map.Entry<Long, PreferenceLevel> entry : tagPreferences.entrySet()) {
            Long tagId = entry.getKey();
            PreferenceLevel preferenceLevel = entry.getValue();
            
            Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> TagException.tagNotFound(tagId));
                
            if (!tag.isUserSelectable()) {
                continue; // 선택 불가능한 태그는 스킵
            }
            
            // 기존 태그 확인 및 재활성화 또는 새로 생성
            Optional<UserPreferredTag> existing = userPreferredTagRepository
                .findByUserIdAndTagId(userId, tagId);
                
            UserPreferredTag userTag;
            if (existing.isPresent()) {
                userTag = existing.get();
                userTag.setActive(true);
                userTag.setPreferenceLevel(preferenceLevel);
            } else {
                userTag = UserPreferredTag.builder()
                    .user(user)
                    .tag(tag)
                    .preferenceLevel(preferenceLevel)
                    .skillLevel(SkillLevel.BEGINNER)
                    .experienceMonths(0)
                    .isActive(true)
                    .build();
                userTag = userPreferredTagRepository.save(userTag);
            }
            
            userTags.add(userTag);
            tag.incrementUsageCount();
        }
        
        // 추천 재계산 이벤트 발행
        publishPreferenceChangedEvent(userId, null, "BULK_UPDATE");
        
        log.info("사용자 선호 태그 일괄 설정 완료 - userId: {}, 태그 수: {}", 
                userId, userTags.size());
        return userTags;
    }

    // ===== 사용자 선호 태그 조회 =====

    /**
     * 사용자의 모든 활성 선호 태그 조회
     */
    @Cacheable(value = "user-preferences", key = "#userId")
    public List<UserPreferredTag> getUserPreferences(Long userId) {
        return userPreferredTagRepository.findByUserIdOrderByPreferenceLevel(userId);
    }

    /**
     * 사용자의 특정 선호도 레벨 태그 조회
     */
    @Cacheable(value = "user-preferences-by-level", key = "#userId + '_' + #preferenceLevels")
    public List<UserPreferredTag> getUserPreferencesByLevel(Long userId, 
                                                           List<PreferenceLevel> preferenceLevels) {
        return userPreferredTagRepository.findByUserIdAndPreferenceLevels(userId, preferenceLevels);
    }

    /**
     * 사용자의 특정 TagType 선호 태그 조회
     */
    @Cacheable(value = "user-preferences-by-type", key = "#userId + '_' + #tagType")
    public List<UserPreferredTag> getUserPreferencesByTagType(Long userId, TagType tagType) {
        return userPreferredTagRepository.findByUserIdAndTagType(userId, tagType);
    }

    /**
     * 사용자의 고급 스킬 태그 조회 (ADVANCED, EXPERT)
     */
    @Cacheable(value = "user-advanced-skills", key = "#userId")
    public List<UserPreferredTag> getUserAdvancedSkills(Long userId) {
        return userPreferredTagRepository.findByUserIdAndSkillLevels(
            userId, List.of(SkillLevel.ADVANCED, SkillLevel.EXPERT));
    }

    // ===== PreferenceLevel 관리 =====

    /**
     * 선호도 레벨 업데이트
     */
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-preferences-by-level"}, key = "#userId")
    public UserPreferredTag updatePreferenceLevel(Long userId, Long tagId, 
                                                 PreferenceLevel newLevel) {
        
        UserPreferredTag userTag = userPreferredTagRepository
            .findByUserIdAndTagId(userId, tagId)
            .orElseThrow(() -> TagException.userTagNotFound(userId, tagId));
            
        PreferenceLevel oldLevel = userTag.getPreferenceLevel();
        userTag.setPreferenceLevel(newLevel);
        
        // 추천 재계산 이벤트 발행 (레벨 변경 시)
        if (oldLevel != newLevel) {
            publishPreferenceChangedEvent(userId, tagId, "PREFERENCE_LEVEL_CHANGE");
        }
        
        log.info("선호도 레벨 변경 - userId: {}, tagId: {}, {} -> {}", 
                userId, tagId, oldLevel, newLevel);
        return userTag;
    }

    /**
     * 선호도 레벨 업그레이드 (LOW → MEDIUM → HIGH)
     */
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-preferences-by-level"}, key = "#userId")
    public UserPreferredTag upgradePreferenceLevel(Long userId, Long tagId) {
        
        UserPreferredTag userTag = userPreferredTagRepository
            .findByUserIdAndTagId(userId, tagId)
            .orElseThrow(() -> TagException.userTagNotFound(userId, tagId));
            
        PreferenceLevel oldLevel = userTag.getPreferenceLevel();
        userTag.upgradePreference();
        
        if (oldLevel != userTag.getPreferenceLevel()) {
            publishPreferenceChangedEvent(userId, tagId, "PREFERENCE_UPGRADE");
        }
        
        log.info("선호도 레벨 업그레이드 - userId: {}, tagId: {}, {} -> {}", 
                userId, tagId, oldLevel, userTag.getPreferenceLevel());
        return userTag;
    }

    // ===== SkillLevel 관리 =====

    /**
     * 스킬 레벨 업데이트
     */
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-advanced-skills"}, key = "#userId")
    public UserPreferredTag updateSkillLevel(Long userId, Long tagId, 
                                            SkillLevel newLevel,
                                            Integer experienceMonths) {
        
        UserPreferredTag userTag = userPreferredTagRepository
            .findByUserIdAndTagId(userId, tagId)
            .orElseThrow(() -> TagException.userTagNotFound(userId, tagId));
            
        SkillLevel oldLevel = userTag.getSkillLevel();
        userTag.setSkillLevel(newLevel);
        
        if (experienceMonths != null) {
            userTag.setExperienceMonths(experienceMonths);
        }
        
        // 스킬 레벨 변경 이벤트
        if (oldLevel != newLevel) {
            publishPreferenceChangedEvent(userId, tagId, "SKILL_LEVEL_CHANGE");
        }
        
        log.info("스킬 레벨 변경 - userId: {}, tagId: {}, {} -> {}", 
                userId, tagId, oldLevel, newLevel);
        return userTag;
    }

    /**
     * 스킬 레벨 향상 (BEGINNER → INTERMEDIATE → ADVANCED → EXPERT)
     */
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-advanced-skills"}, key = "#userId")
    public UserPreferredTag improveSkillLevel(Long userId, Long tagId) {
        
        UserPreferredTag userTag = userPreferredTagRepository
            .findByUserIdAndTagId(userId, tagId)
            .orElseThrow(() -> TagException.userTagNotFound(userId, tagId));
            
        SkillLevel oldLevel = userTag.getSkillLevel();
        userTag.improveSkill(); // 경험 개월 수도 함께 증가
        
        if (oldLevel != userTag.getSkillLevel()) {
            publishPreferenceChangedEvent(userId, tagId, "SKILL_IMPROVEMENT");
        }
        
        log.info("스킬 레벨 향상 - userId: {}, tagId: {}, {} -> {}", 
                userId, tagId, oldLevel, userTag.getSkillLevel());
        return userTag;
    }

    /**
     * 경험 기간 기반 자동 스킬 업그레이드
     */
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-advanced-skills"}, key = "#userId")
    public List<UserPreferredTag> autoUpgradeSkillsByExperience(Long userId) {
        
        List<UserPreferredTag> userTags = getUserPreferences(userId);
        List<UserPreferredTag> upgradedTags = new ArrayList<>();
        
        for (UserPreferredTag userTag : userTags) {
            Integer experienceMonths = userTag.getExperienceMonths();
            if (experienceMonths == null) continue;
            
            SkillLevel expectedLevel = calculateExpectedSkillLevel(experienceMonths);
            if (userTag.getSkillLevel().getLevel() < expectedLevel.getLevel()) {
                userTag.setSkillLevel(expectedLevel);
                upgradedTags.add(userTag);
            }
        }
        
        if (!upgradedTags.isEmpty()) {
            publishPreferenceChangedEvent(userId, null, "AUTO_SKILL_UPGRADE");
            log.info("자동 스킬 업그레이드 완료 - userId: {}, 업그레이드 수: {}", 
                    userId, upgradedTags.size());
        }
        
        return upgradedTags;
    }

    // ===== 사용자별 태그 통계 =====

    /**
     * 사용자 태그 통계 조회
     */
    @Cacheable(value = "user-tag-statistics", key = "#userId")
    public UserTagStatisticsDto getUserTagStatistics(Long userId) {
        
        List<UserPreferredTag> userTags = getUserPreferences(userId);
        
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
        
        List<UserPreferredTag> userTags = getUserPreferences(userId);
        if (userTags.isEmpty()) {
            return List.of();
        }
        
        Set<Long> userTagIds = userTags.stream()
            .map(ut -> ut.getTag().getTagId())
            .collect(Collectors.toSet());
        
        // Repository에서 유사 사용자 조회
        return userPreferredTagRepository.findSimilarUsers(userId, userTagIds, limit);
    }

    // ===== 선호 태그 삭제/비활성화 =====

    /**
     * 사용자 선호 태그 삭제 (비활성화)
     */
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-tag-statistics"}, key = "#userId")
    public void removeUserPreference(Long userId, Long tagId) {
        
        UserPreferredTag userTag = userPreferredTagRepository
            .findByUserIdAndTagId(userId, tagId)
            .orElseThrow(() -> TagException.userTagNotFound(userId, tagId));
            
        userTag.deactivate();
        
        // 추천 재계산 이벤트 발행
        publishPreferenceChangedEvent(userId, tagId, "REMOVE");
        
        log.info("사용자 선호 태그 제거 - userId: {}, tagId: {}", userId, tagId);
    }

    /**
     * 사용자의 모든 선호 태그 초기화
     */
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-tag-statistics"}, key = "#userId")
    public void clearUserPreferences(Long userId) {
        
        userPreferredTagRepository.deactivateAllUserTags(userId);
        
        // 추천 재계산 이벤트 발행
        publishPreferenceChangedEvent(userId, null, "CLEAR_ALL");
        
        log.info("사용자 선호 태그 전체 초기화 - userId: {}", userId);
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 추천 재계산 이벤트 발행
     */
    private void publishPreferenceChangedEvent(Long userId, Long tagId, String changeType) {
        UserPreferenceChangedEvent event = UserPreferenceChangedEvent.builder()
            .userId(userId)
            .tagId(tagId)
            .changeType(changeType)
            .changedAt(LocalDateTime.now())
            .build();
            
        eventPublisher.publishEvent(event);
        
        log.debug("선호도 변경 이벤트 발행 - userId: {}, tagId: {}, type: {}", 
                 userId, tagId, changeType);
    }

    /**
     * 경험 개월 수 기반 예상 스킬 레벨 계산
     */
    private SkillLevel calculateExpectedSkillLevel(Integer experienceMonths) {
        if (experienceMonths == null || experienceMonths < skillUpgradeMonths) {
            return SkillLevel.BEGINNER;
        } else if (experienceMonths < skillUpgradeMonths * 2) {
            return SkillLevel.INTERMEDIATE;
        } else if (experienceMonths < skillUpgradeMonths * 3) {
            return SkillLevel.ADVANCED;
        } else {
            return SkillLevel.EXPERT;
        }
    }

    /**
     * 사용자가 추천 받을 수 있는 상태인지 확인
     */
    public boolean isEligibleForRecommendation(Long userId) {
        long activeTagCount = userPreferredTagRepository.countActiveByUserId(userId);
        return activeTagCount >= minTagsForRecommendation;
    }

    // ===== DTO 클래스 =====

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
}

// ===== 이벤트 클래스 =====

/**
 * 사용자 선호도 변경 이벤트
 */
@lombok.Builder
@lombok.Getter
class UserPreferenceChangedEvent {
    private final Long userId;
    private final Long tagId;
    private final String changeType;
    private final LocalDateTime changedAt;
}
```

---

## 📋 주요 기능 설명

### 👤 **1. 선호 태그 설정**
- **개별 추가**: 태그별 선호도/스킬 레벨 설정
- **일괄 설정**: 여러 태그 한번에 설정
- **최대 제한**: 사용자당 최대 20개 태그
- **재활성화**: 비활성화된 태그 재사용

### 📊 **2. PreferenceLevel 관리**
- **3단계 레벨**: LOW(25%), MEDIUM(50%), HIGH(75%)
- **레벨 업그레이드**: 단계적 상승 지원
- **가중치 계산**: 추천 알고리즘용 가중치
- **레벨별 조회**: 특정 레벨 태그 필터링

### 🎯 **3. SkillLevel 관리**
- **4단계 레벨**: BEGINNER → INTERMEDIATE → ADVANCED → EXPERT
- **자동 업그레이드**: 경험 기간 기반 자동 상승
- **경험 개월 수**: 스킬 레벨과 연동
- **고급 스킬 조회**: ADVANCED 이상 태그 필터

### 📢 **4. 이벤트 발행**
- **추천 재계산 트리거**: ApplicationEventPublisher 활용
- **변경 타입**: ADD, UPDATE, REMOVE, BULK_UPDATE 등
- **비동기 처리**: 추천 시스템 자동 갱신
- **이벤트 정보**: userId, tagId, changeType, timestamp

### 📈 **5. 통계 및 분석**
- **선호도 분포**: PreferenceLevel별 태그 수
- **스킬 분포**: SkillLevel별 태그 수
- **TagType 분포**: 태그 타입별 분포
- **유사 사용자**: 공통 태그 기반 유사도

---

## 💾 **Redis 캐싱 전략**

### 캐시 키 구조
- **사용자 선호도**: `user-preferences:{userId}`
- **레벨별 선호도**: `user-preferences-by-level:{userId}_{levels}`
- **타입별 선호도**: `user-preferences-by-type:{userId}_{tagType}`
- **고급 스킬**: `user-advanced-skills:{userId}`
- **통계 정보**: `user-tag-statistics:{userId}`

### 캐시 무효화
- **선호도 변경**: 관련 사용자 캐시 무효화
- **이벤트 발행**: 추천 캐시 자동 갱신

---

## 🚀 **다음 단계**

**Phase 3 완료 후 진행할 작업:**
- **step6-3c_route_tagging_service.md**: 루트 태깅 Service
- **step6-3d_recommendation_service.md**: 추천 시스템 Service

*step6-3b 완성: UserPreferenceService 완전 구현 완료*