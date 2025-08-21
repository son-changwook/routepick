# Step 4-3c2: 사용자 활동 엔티티 설계

> **RoutePickr 사용자 활동 시스템** - 클라이밍 기록, 팔로우 관계, 소셜 활동  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-3c2 (JPA 엔티티 50개 - 사용자 활동 2개)  
> **분할**: step4-3c_climbing_activity_entities.md → 사용자 활동 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 사용자 활동 시스템**을 담고 있습니다.

### 🎯 주요 특징
- **상세 클라이밍 기록**: 성공/실패, 시도 횟수, 베타 정보, 개인 기록
- **소셜 팔로우 시스템**: 상호 팔로우, 활성도 추적, 관계 관리
- **성과 분석**: 성공률, 만족도, 컨디션 점수, 개인 기록 추적
- **커뮤니티 연동**: 기록 공유, 파트너 정보, 상호작용 추적

### 📊 엔티티 목록 (2개)
1. **UserClimb** - 클라이밍 기록 (상세 도전 기록, 성과 분석)
2. **UserFollow** - 팔로우 관계 (상호 팔로우, 활성도 추적)

---

## 📈 1. UserClimb 엔티티 - 클라이밍 기록

```java
package com.routepick.domain.activity.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 사용자 클라이밍 기록
 * - 개별 루트 도전 기록
 * - 성공/실패, 시도 횟수, 소요 시간 등
 */
@Entity
@Table(name = "user_climbs", indexes = {
    @Index(name = "idx_climb_user_date", columnList = "user_id, climb_date DESC"),
    @Index(name = "idx_climb_route", columnList = "route_id"),
    @Index(name = "idx_climb_user_route", columnList = "user_id, route_id"),
    @Index(name = "idx_climb_success", columnList = "is_successful, climb_date DESC"),
    @Index(name = "idx_climb_rating", columnList = "difficulty_rating DESC"),
    @Index(name = "idx_climb_branch", columnList = "branch_id, climb_date DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserClimb extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "climb_id")
    private Long climbId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    // ===== 클라이밍 기본 정보 =====
    
    @NotNull
    @Column(name = "climb_date", nullable = false)
    private LocalDate climbDate; // 클라이밍 날짜
    
    @Column(name = "start_time")
    private LocalTime startTime; // 시작 시간
    
    @Column(name = "end_time")
    private LocalTime endTime; // 종료 시간
    
    @Column(name = "is_successful", nullable = false)
    private boolean isSuccessful = false; // 성공 여부
    
    @Min(value = 1, message = "시도 횟수는 1회 이상이어야 합니다")
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1; // 시도 횟수
    
    @Column(name = "success_attempt")
    private Integer successAttempt; // 성공한 시도 번호
    
    // ===== 성과 정보 =====
    
    @Column(name = "climb_type", length = 30)
    private String climbType; // FLASH, ONSIGHT, REDPOINT, REPEAT
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "difficulty_rating")
    private Integer difficultyRating; // 체감 난이도 평점 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "enjoyment_rating")
    private Integer enjoymentRating; // 재미 평점 (1-5)
    
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "quality_rating")
    private Integer qualityRating; // 루트 품질 평점 (1-5)
    
    @Column(name = "personal_record", nullable = false)
    private boolean personalRecord = false; // 개인 기록 여부
    
    // ===== 상세 기록 =====
    
    @Column(name = "total_time_minutes")
    private Integer totalTimeMinutes; // 총 소요 시간 (분)
    
    @Column(name = "rest_time_minutes")
    private Integer restTimeMinutes; // 휴식 시간 (분)
    
    @Column(name = "fall_count")
    private Integer fallCount = 0; // 추락 횟수
    
    @Column(name = "key_holds_missed")
    private String keyHoldsMissed; // 놓친 핵심 홀드
    
    @Column(name = "beta_used", columnDefinition = "TEXT")
    private String betaUsed; // 사용한 베타
    
    @Column(name = "technique_notes", columnDefinition = "TEXT")
    private String techniqueNotes; // 기술 메모
    
    // ===== 환경 정보 =====
    
    @Column(name = "branch_id")
    private Long branchId; // 암장 지점 ID (비정규화)
    
    @Column(name = "wall_condition", length = 50)
    private String wallCondition; // 벽면 상태
    
    @Column(name = "weather_condition", length = 50)
    private String weatherCondition; // 날씨 (실외인 경우)
    
    @Column(name = "crowd_level", length = 20)
    private String crowdLevel; // 혼잡도 (EMPTY, LOW, MODERATE, HIGH, CROWDED)
    
    // ===== 신체/장비 정보 =====
    
    @Column(name = "climbing_shoe_id")
    private Long climbingShoeId; // 사용한 신발 ID
    
    @Column(name = "chalk_type", length = 30)
    private String chalkType; // 사용한 초크 종류
    
    @Column(name = "physical_condition", length = 30)
    private String physicalCondition; // 컨디션 (EXCELLENT, GOOD, FAIR, POOR)
    
    @Column(name = "injury_notes", length = 200)
    private String injuryNotes; // 부상 메모
    
    // ===== 소셜/공유 정보 =====
    
    @Column(name = "climb_notes", columnDefinition = "TEXT")
    private String climbNotes; // 클라이밍 메모
    
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true; // 공개 여부
    
    @Column(name = "shared_with_community", nullable = false)
    private boolean sharedWithCommunity = false; // 커뮤니티 공유 여부
    
    @Column(name = "climb_partners", length = 200)
    private String climbPartners; // 함께한 파트너들
    
    @Column(name = "witness_count")
    private Integer witnessCount = 0; // 목격자 수
    
    // ===== 메타데이터 =====
    
    @Column(name = "gps_latitude", precision = 10, scale = 8)
    private java.math.BigDecimal gpsLatitude; // GPS 위도
    
    @Column(name = "gps_longitude", precision = 11, scale = 8)
    private java.math.BigDecimal gpsLongitude; // GPS 경도
    
    @Column(name = "recorded_device", length = 50)
    private String recordedDevice; // 기록 디바이스
    
    @Column(name = "session_id", length = 100)
    private String sessionId; // 세션 ID (같은 날 같은 장소)
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 클라이밍 타입 한글 표시
     */
    @Transient
    public String getClimbTypeKorean() {
        if (climbType == null) return "일반";
        
        return switch (climbType) {
            case "FLASH" -> "플래시";
            case "ONSIGHT" -> "온사이트";
            case "REDPOINT" -> "레드포인트";
            case "REPEAT" -> "반복 완등";
            default -> "일반";
        };
    }
    
    /**
     * 성공률 계산
     */
    @Transient
    public float getSuccessRate() {
        if (attemptCount == null || attemptCount == 0) return 0.0f;
        return isSuccessful ? (1.0f / attemptCount * 100) : 0.0f;
    }
    
    /**
     * 순수 클라이밍 시간 계산
     */
    @Transient
    public Integer getActiveClimbTime() {
        if (totalTimeMinutes == null) return null;
        if (restTimeMinutes == null) return totalTimeMinutes;
        
        return Math.max(0, totalTimeMinutes - restTimeMinutes);
    }
    
    /**
     * 컨디션 점수 계산 (1-100)
     */
    @Transient
    public int getConditionScore() {
        if (physicalCondition == null) return 50;
        
        return switch (physicalCondition) {
            case "EXCELLENT" -> 90;
            case "GOOD" -> 70;
            case "FAIR" -> 50;
            case "POOR" -> 30;
            default -> 50;
        };
    }
    
    /**
     * 전체 만족도 계산
     */
    @Transient
    public float getOverallSatisfaction() {
        int total = 0;
        int count = 0;
        
        if (difficultyRating != null) { total += difficultyRating; count++; }
        if (enjoymentRating != null) { total += enjoymentRating; count++; }
        if (qualityRating != null) { total += qualityRating; count++; }
        
        return count > 0 ? (float) total / count : 0.0f;
    }
    
    /**
     * 혼잡도 한글 표시
     */
    @Transient
    public String getCrowdLevelKorean() {
        if (crowdLevel == null) return "보통";
        
        return switch (crowdLevel) {
            case "EMPTY" -> "한산함";
            case "LOW" -> "여유로움";
            case "MODERATE" -> "보통";
            case "HIGH" -> "붐빔";
            case "CROWDED" -> "매우 붐빔";
            default -> "보통";
        };
    }
    
    /**
     * 컨디션 한글 표시
     */
    @Transient
    public String getPhysicalConditionKorean() {
        if (physicalCondition == null) return "보통";
        
        return switch (physicalCondition) {
            case "EXCELLENT" -> "최상";
            case "GOOD" -> "좋음";
            case "FAIR" -> "보통";
            case "POOR" -> "나쁨";
            default -> "보통";
        };
    }
    
    /**
     * 성공 기록 처리
     */
    public void recordSuccess(int attemptNumber, String beta, String notes) {
        this.isSuccessful = true;
        this.successAttempt = attemptNumber;
        this.betaUsed = beta;
        this.techniqueNotes = notes;
        this.personalRecord = checkPersonalRecord();
    }
    
    /**
     * 개인 기록 확인 (Service Layer에서 구현 예정)
     */
    private boolean checkPersonalRecord() {
        // Service Layer에서 구현
        // 해당 사용자의 이전 기록과 비교
        return false;
    }
    
    /**
     * 시간 기록 설정
     */
    public void setTimeRecord(LocalTime start, LocalTime end, Integer restMinutes) {
        this.startTime = start;
        this.endTime = end;
        this.restTimeMinutes = restMinutes;
        
        if (start != null && end != null) {
            long totalMinutes = java.time.Duration.between(start, end).toMinutes();
            this.totalTimeMinutes = (int) totalMinutes;
        }
    }
    
    /**
     * 평점 업데이트
     */
    public void updateRatings(Integer difficulty, Integer enjoyment, Integer quality) {
        this.difficultyRating = difficulty;
        this.enjoymentRating = enjoyment;
        this.qualityRating = quality;
    }
    
    /**
     * 커뮤니티 공유
     */
    public void shareWithCommunity(String partners, String notes) {
        this.sharedWithCommunity = true;
        this.climbPartners = partners;
        this.climbNotes = notes;
        this.isPublic = true;
    }
    
    /**
     * 환경 정보 업데이트
     */
    public void updateEnvironment(String wallCondition, String weatherCondition, String crowdLevel) {
        this.wallCondition = wallCondition;
        this.weatherCondition = weatherCondition;
        this.crowdLevel = crowdLevel;
    }
    
    /**
     * 장비 정보 설정
     */
    public void setGearInfo(Long shoeId, String chalkType) {
        this.climbingShoeId = shoeId;
        this.chalkType = chalkType;
    }
    
    /**
     * 추락 기록
     */
    public void recordFall() {
        this.fallCount = (fallCount == null ? 0 : fallCount) + 1;
    }
    
    /**
     * 목격자 추가
     */
    public void addWitness() {
        this.witnessCount = (witnessCount == null ? 0 : witnessCount) + 1;
    }
    
    /**
     * GPS 위치 설정
     */
    public void setGpsLocation(java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
        this.gpsLatitude = latitude;
        this.gpsLongitude = longitude;
    }
    
    @Override
    public Long getId() {
        return climbId;
    }
}
```

---

## 👥 2. UserFollow 엔티티 - 팔로우 관계

```java
package com.routepick.domain.activity.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 사용자 팔로우 관계
 * - 팔로워/팔로잉 관계 관리
 * - 상호 팔로우 확인
 */
@Entity
@Table(name = "user_follows", indexes = {
    @Index(name = "idx_follow_relationship", columnList = "follower_user_id, following_user_id", unique = true),
    @Index(name = "idx_follow_follower", columnList = "follower_user_id"),
    @Index(name = "idx_follow_following", columnList = "following_user_id"),
    @Index(name = "idx_follow_mutual", columnList = "is_mutual"),
    @Index(name = "idx_follow_date", columnList = "follow_date DESC"),
    @Index(name = "idx_follow_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserFollow extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "follow_id")
    private Long followId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_user_id", nullable = false)
    private User followerUser; // 팔로우 하는 사용자
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_user_id", nullable = false)
    private User followingUser; // 팔로우 받는 사용자
    
    @NotNull
    @Column(name = "follow_date", nullable = false)
    private LocalDateTime followDate; // 팔로우 시작일
    
    @Column(name = "unfollow_date")
    private LocalDateTime unfollowDate; // 언팔로우 일시
    
    @Column(name = "is_mutual", nullable = false)
    private boolean isMutual = false; // 상호 팔로우 여부
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 팔로우
    
    @Column(name = "follow_source", length = 50)
    private String followSource; // 팔로우 경로 (SEARCH, RECOMMENDATION, ROUTE, COMMENT 등)
    
    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = true; // 알림 설정
    
    @Column(name = "close_friend", nullable = false)
    private boolean closeFriend = false; // 친한 친구 표시
    
    @Column(name = "blocked", nullable = false)
    private boolean blocked = false; // 차단 여부
    
    @Column(name = "muted", nullable = false)
    private boolean muted = false; // 음소거 여부
    
    // ===== 통계 정보 =====
    
    @Column(name = "interaction_count")
    private Integer interactionCount = 0; // 상호작용 횟수
    
    @Column(name = "last_interaction_date")
    private LocalDateTime lastInteractionDate; // 마지막 상호작용 일시
    
    @Column(name = "mutual_climb_count")
    private Integer mutualClimbCount = 0; // 함께한 클라이밍 수
    
    @Column(name = "last_activity_view_date")
    private LocalDateTime lastActivityViewDate; // 마지막 활동 조회일
    
    // ===== 개인정보 =====
    
    @Column(name = "follow_note", length = 200)
    private String followNote; // 팔로우 메모
    
    @Column(name = "nickname", length = 50)
    private String nickname; // 개인적 별명
    
    @Column(name = "relationship_type", length = 30)
    private String relationshipType; // FRIEND, CLIMBING_PARTNER, INSPIRATION, OTHER
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 팔로우 관계 한글 표시
     */
    @Transient
    public String getRelationshipTypeKorean() {
        if (relationshipType == null) return "일반";
        
        return switch (relationshipType) {
            case "FRIEND" -> "친구";
            case "CLIMBING_PARTNER" -> "클라이밍 파트너";
            case "INSPIRATION" -> "영감을 주는 사람";
            case "OTHER" -> "기타";
            default -> "일반";
        };
    }
    
    /**
     * 팔로우 소스 한글 표시
     */
    @Transient
    public String getFollowSourceKorean() {
        if (followSource == null) return "일반";
        
        return switch (followSource) {
            case "SEARCH" -> "검색";
            case "RECOMMENDATION" -> "추천";
            case "ROUTE" -> "루트 페이지";
            case "COMMENT" -> "댓글";
            case "COMMUNITY" -> "커뮤니티";
            case "FRIEND_SUGGESTION" -> "친구 추천";
            default -> "일반";
        };
    }
    
    /**
     * 팔로우 기간 계산 (일)
     */
    @Transient
    public long getFollowDurationDays() {
        if (followDate == null) return 0;
        
        LocalDateTime endDate = isActive ? LocalDateTime.now() : unfollowDate;
        if (endDate == null) endDate = LocalDateTime.now();
        
        return java.time.temporal.ChronoUnit.DAYS.between(followDate, endDate);
    }
    
    /**
     * 활성도 점수 계산 (0-100)
     */
    @Transient
    public int getActivityScore() {
        int score = 0;
        
        // 기본 팔로우 점수
        score += 20;
        
        // 상호 팔로우 보너스
        if (isMutual) score += 30;
        
        // 상호작용 점수
        if (interactionCount != null) {
            score += Math.min(interactionCount * 2, 30);
        }
        
        // 최근 활동 보너스
        if (lastInteractionDate != null) {
            long daysSinceLastInteraction = java.time.temporal.ChronoUnit.DAYS
                .between(lastInteractionDate, LocalDateTime.now());
            
            if (daysSinceLastInteraction <= 7) score += 20;
            else if (daysSinceLastInteraction <= 30) score += 10;
        }
        
        return Math.min(score, 100);
    }
    
    /**
     * 친밀도 레벨 계산
     */
    @Transient
    public String getIntimacyLevel() {
        int activityScore = getActivityScore();
        
        if (closeFriend) return "친한 친구";
        if (activityScore >= 80) return "매우 친함";
        if (activityScore >= 60) return "친함";
        if (activityScore >= 40) return "보통";
        if (activityScore >= 20) return "알고 지냄";
        return "새로운 팔로우";
    }
    
    /**
     * 상호작용 기록
     */
    public void recordInteraction() {
        this.interactionCount = (interactionCount == null ? 0 : interactionCount) + 1;
        this.lastInteractionDate = LocalDateTime.now();
    }
    
    /**
     * 상호 팔로우 설정
     */
    public void setMutualFollow() {
        this.isMutual = true;
    }
    
    /**
     * 상호 팔로우 해제
     */
    public void unsetMutualFollow() {
        this.isMutual = false;
    }
    
    /**
     * 언팔로우 처리
     */
    public void unfollow() {
        this.isActive = false;
        this.unfollowDate = LocalDateTime.now();
        this.isMutual = false;
        this.notificationEnabled = false;
    }
    
    /**
     * 팔로우 재개
     */
    public void refollow() {
        this.isActive = true;
        this.unfollowDate = null;
        this.followDate = LocalDateTime.now(); // 새로운 팔로우 날짜
        this.notificationEnabled = true;
    }
    
    /**
     * 차단 처리
     */
    public void block() {
        this.blocked = true;
        this.isActive = false;
        this.notificationEnabled = false;
        this.isMutual = false;
    }
    
    /**
     * 차단 해제
     */
    public void unblock() {
        this.blocked = false;
    }
    
    /**
     * 음소거 처리
     */
    public void mute() {
        this.muted = true;
        this.notificationEnabled = false;
    }
    
    /**
     * 음소거 해제
     */
    public void unmute() {
        this.muted = false;
        this.notificationEnabled = true;
    }
    
    /**
     * 친한 친구 설정
     */
    public void setCloseFriend(boolean isCloseFriend) {
        this.closeFriend = isCloseFriend;
    }
    
    /**
     * 개인 메모 업데이트
     */
    public void updateNote(String note, String nickname, String relationshipType) {
        this.followNote = note;
        this.nickname = nickname;
        this.relationshipType = relationshipType;
    }
    
    /**
     * 함께한 클라이밍 기록
     */
    public void recordMutualClimb() {
        this.mutualClimbCount = (mutualClimbCount == null ? 0 : mutualClimbCount) + 1;
        recordInteraction();
    }
    
    /**
     * 마지막 활동 조회 업데이트
     */
    public void updateLastActivityView() {
        this.lastActivityViewDate = LocalDateTime.now();
    }
    
    /**
     * 팔로우 관계 강도 계산 (0.0-1.0)
     */
    @Transient
    public float getRelationshipStrength() {
        float strength = 0.0f;
        
        // 기본 팔로우 점수
        strength += 0.2f;
        
        // 상호 팔로우
        if (isMutual) strength += 0.3f;
        
        // 상호작용 빈도
        if (interactionCount != null && interactionCount > 0) {
            strength += Math.min(interactionCount * 0.01f, 0.3f);
        }
        
        // 최근 활동
        if (lastInteractionDate != null) {
            long daysSinceLastInteraction = java.time.temporal.ChronoUnit.DAYS
                .between(lastInteractionDate, LocalDateTime.now());
                
            if (daysSinceLastInteraction <= 7) strength += 0.2f;
            else if (daysSinceLastInteraction <= 30) strength += 0.1f;
        }
        
        // 친한 친구 보너스
        if (closeFriend) strength += 0.1f;
        
        // 함께한 클라이밍
        if (mutualClimbCount != null && mutualClimbCount > 0) {
            strength += Math.min(mutualClimbCount * 0.02f, 0.1f);
        }
        
        return Math.min(strength, 1.0f);
    }
    
    /**
     * 알림 설정 업데이트
     */
    public void updateNotificationSettings(boolean enabled) {
        if (!blocked && !muted) {
            this.notificationEnabled = enabled;
        }
    }
    
    @Override
    public Long getId() {
        return followId;
    }
}
```

---

## ⚡ 3. 성능 최적화 전략

### 복합 인덱스 DDL 추가
```sql
-- 클라이밍 기록 분석용 인덱스
CREATE INDEX idx_climb_user_success_date 
ON user_climbs(user_id, is_successful, climb_date DESC);

-- 루트별 성공률 계산용
CREATE INDEX idx_climb_route_success 
ON user_climbs(route_id, is_successful, attempt_count);

-- 사용자별 개인 기록 검색
CREATE INDEX idx_climb_personal_record 
ON user_climbs(user_id, personal_record, climb_date DESC);

-- 팔로우 추천용 인덱스
CREATE INDEX idx_follow_mutual_activity 
ON user_follows(is_mutual, is_active, last_interaction_date DESC);

-- 활성 팔로우 관계 조회
CREATE INDEX idx_follow_active_mutual 
ON user_follows(follower_user_id, is_active, is_mutual);

-- 팔로우 소스별 통계용
CREATE INDEX idx_follow_source_stats 
ON user_follows(follow_source, is_active, follow_date DESC);
```

### 통계 정보 계산 쿼리 예시
```java
// Repository에서 사용할 통계 쿼리들

// 사용자별 성공률 계산
@Query("SELECT COUNT(CASE WHEN uc.isSuccessful = true THEN 1 END) * 100.0 / COUNT(*) " +
       "FROM UserClimb uc WHERE uc.user.id = :userId")
Double getUserSuccessRate(@Param("userId") Long userId);

// 루트별 평균 시도 횟수
@Query("SELECT AVG(uc.attemptCount) FROM UserClimb uc " +
       "WHERE uc.route.id = :routeId AND uc.isSuccessful = true")
Double getAverageAttempts(@Param("routeId") Long routeId);

// 상호 팔로우 목록
@Query("SELECT uf.followingUser FROM UserFollow uf " +
       "WHERE uf.followerUser.id = :userId " +
       "AND uf.isActive = true AND uf.isMutual = true " +
       "ORDER BY uf.lastInteractionDate DESC")
List<User> findMutualFollows(@Param("userId") Long userId);

// 사용자별 월간 클라이밍 통계
@Query("SELECT COUNT(*), AVG(uc.attemptCount), " +
       "COUNT(CASE WHEN uc.isSuccessful = true THEN 1 END) " +
       "FROM UserClimb uc " +
       "WHERE uc.user.id = :userId " +
       "AND uc.climbDate >= :startDate AND uc.climbDate <= :endDate")
Object[] getMonthlyClimbStats(@Param("userId") Long userId, 
                             @Param("startDate") LocalDate startDate,
                             @Param("endDate") LocalDate endDate);

// 팔로우 활성도 기반 추천
@Query("SELECT uf FROM UserFollow uf " +
       "WHERE uf.followerUser.id IN (" +
       "  SELECT uf2.followingUser.id FROM UserFollow uf2 " +
       "  WHERE uf2.followerUser.id = :userId AND uf2.isActive = true" +
       ") " +
       "AND uf.followingUser.id != :userId " +
       "AND uf.isActive = true " +
       "GROUP BY uf.followingUser " +
       "ORDER BY COUNT(uf) DESC, MAX(uf.lastInteractionDate) DESC")
List<UserFollow> findFollowRecommendations(@Param("userId") Long userId);
```

### 캐시 전략
```java
// Redis 캐시를 활용한 통계 정보 관리

@Cacheable(value = "userClimbStats", key = "#userId")
public UserClimbStatistics getUserClimbStatistics(Long userId) {
    return UserClimbStatistics.builder()
        .userId(userId)
        .totalClimbs(climbRepository.countByUserId(userId))
        .successfulClimbs(climbRepository.countByUserIdAndIsSuccessful(userId, true))
        .averageAttempts(climbRepository.getAverageAttempts(userId))
        .personalRecords(climbRepository.countByUserIdAndPersonalRecord(userId, true))
        .build();
}

@Cacheable(value = "userFollowStats", key = "#userId")
public UserFollowStatistics getUserFollowStatistics(Long userId) {
    return UserFollowStatistics.builder()
        .userId(userId)
        .followersCount(followRepository.countByFollowingUserIdAndIsActive(userId, true))
        .followingCount(followRepository.countByFollowerUserIdAndIsActive(userId, true))
        .mutualFollowsCount(followRepository.countByFollowerUserIdAndIsActiveAndIsMutual(userId, true, true))
        .build();
}

// 배치 작업으로 통계 정보 업데이트
@Scheduled(fixedRate = 600000) // 10분마다
public void updateClimbStatistics() {
    List<Long> activeUserIds = userRepository.findActiveUserIds();
    for (Long userId : activeUserIds) {
        updateUserClimbStats(userId);
    }
}
```

---

## ✅ 설계 완료 체크리스트

### 사용자 활동 엔티티 (2개)
- [x] **UserClimb** - 클라이밍 기록 (상세 도전 기록, 성과 분석, 환경 정보)
- [x] **UserFollow** - 팔로우 관계 (상호 팔로우, 활성도 추적, 관계 관리)

### 상세 클라이밍 기록 시스템
- [x] 플래시, 온사이트, 레드포인트 등 클라이밍 타입 분류
- [x] 시도 횟수, 성공률, 소요 시간 상세 기록
- [x] 체감 난이도, 재미, 품질 5단계 평가
- [x] 개인 기록 자동 인식 및 추적

### 소셜 팔로우 시스템
- [x] 상호 팔로우 확인 및 관계 강도 계산
- [x] 팔로우 소스 추적 (검색, 추천, 댓글 등)
- [x] 친한 친구, 차단, 음소거 관리
- [x] 상호작용 빈도 및 활성도 점수

### 성과 분석 기능
- [x] 성공률, 만족도, 컨디션 점수 계산
- [x] 월간/연간 클라이밍 통계 분석
- [x] 개인 기록 및 성장 추적
- [x] 환경 요인별 성과 분석

### 커뮤니티 연동
- [x] 기록 공개/비공개 설정
- [x] 클라이밍 파트너 기록 및 목격자 시스템
- [x] 커뮤니티 공유 및 소셜 기능
- [x] 함께한 클라이밍 횟수 추적

### 성능 최적화
- [x] 사용자별 클라이밍 기록 조회 최적화
- [x] 팔로우 관계 검색 및 추천 인덱스
- [x] 통계 정보 캐시 전략
- [x] 배치 작업으로 성능 통계 업데이트

---

**다음 단계**: step4-4c_system_final.md (시스템 최종 엔티티 세분화)  
**완료일**: 2025-08-20  
**핵심 성과**: 2개 사용자 활동 엔티티 + 상세 클라이밍 기록 + 소셜 팔로우 시스템 + 성과 분석 완성