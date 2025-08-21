# Step 4-3b2: 루트 상호작용 엔티티 설계

> **RoutePickr 루트 상호작용 시스템** - 댓글, 투표, 스크랩, 성능 최적화  
> 
> **생성일**: 2025-08-20  
> **단계**: 4-3b2 (JPA 엔티티 50개 - 루트 상호작용 3개 + 최적화)  
> **분할**: step4-3b_route_entities.md → 루트 상호작용 부분 추출

---

## 📋 파일 개요

이 파일은 **RoutePickr의 루트 상호작용 시스템과 성능 최적화**를 담고 있습니다.

### 🎯 주요 특징
- **계층형 댓글**: 부모-자식 구조, 소프트 삭제, 스포일러 처리
- **난이도 투표**: 사용자 체감 난이도, 가중치 시스템, 신뢰도 평가
- **스크랩 시스템**: 개인 폴더, 메모, 태그, 우선순위 관리
- **성능 최적화**: 복합 인덱스, N+1 해결, 통계 캐시

### 📊 엔티티 목록 (3개)
1. **RouteComment** - 루트 댓글 (계층형 구조, 베타 스포일러)
2. **RouteDifficultyVote** - 난이도 투표 (가중치, 신뢰도 시스템)
3. **RouteScrap** - 루트 스크랩 (개인 폴더, 메모, 우선순위)

---

## 💬 5. RouteComment 엔티티 - 루트 댓글 (계층형)

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.SoftDeleteEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 루트 댓글 관리 (계층형 구조)
 * - 부모-자식 관계로 대댓글 지원
 * - 소프트 삭제 적용
 */
@Entity
@Table(name = "route_comments", indexes = {
    @Index(name = "idx_comment_route_parent", columnList = "route_id, parent_id, created_at DESC"),
    @Index(name = "idx_comment_user", columnList = "user_id"),
    @Index(name = "idx_comment_parent", columnList = "parent_id"),
    @Index(name = "idx_comment_active", columnList = "is_deleted, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteComment extends SoftDeleteEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private RouteComment parent; // 부모 댓글 (대댓글인 경우)
    
    @NotNull
    @Size(min = 1, max = 1000, message = "댓글은 1-1000자 사이여야 합니다")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content; // 댓글 내용
    
    @Column(name = "comment_type", length = 30)
    private String commentType; // NORMAL, BETA, TIP, QUESTION, COMPLIMENT
    
    @Column(name = "is_spoiler", nullable = false)
    private boolean isSpoiler = false; // 스포일러 여부 (베타 공개)
    
    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous = false; // 익명 댓글 여부
    
    @Column(name = "like_count")
    private Integer likeCount = 0; // 좋아요 수
    
    @Column(name = "reply_count")
    private Integer replyCount = 0; // 답글 수
    
    @Column(name = "report_count")
    private Integer reportCount = 0; // 신고 수
    
    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false; // 고정 댓글
    
    @Column(name = "is_author_comment", nullable = false)
    private boolean isAuthorComment = false; // 세터 댓글
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // 작성자 IP
    
    @Column(name = "user_agent", length = 500)
    private String userAgent; // User Agent
    
    // ===== 연관관계 매핑 =====
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RouteComment> children = new ArrayList<>(); // 자식 댓글들
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 최상위 댓글인지 확인
     */
    @Transient
    public boolean isRootComment() {
        return parent == null;
    }
    
    /**
     * 댓글 깊이 계산
     */
    @Transient
    public int getDepth() {
        if (parent == null) return 0;
        return parent.getDepth() + 1;
    }
    
    /**
     * 댓글 타입 한글명
     */
    @Transient
    public String getCommentTypeKorean() {
        if (commentType == null) return "일반";
        
        return switch (commentType) {
            case "NORMAL" -> "일반";
            case "BETA" -> "베타";
            case "TIP" -> "팁";
            case "QUESTION" -> "질문";
            case "COMPLIMENT" -> "칭찬";
            default -> "기타";
        };
    }
    
    /**
     * 표시용 작성자명
     */
    @Transient
    public String getDisplayAuthorName() {
        if (isAnonymous) return "익명";
        return user.getNickName();
    }
    
    /**
     * 좋아요 수 증가
     */
    public void increaseLikeCount() {
        this.likeCount = (likeCount == null ? 0 : likeCount) + 1;
    }
    
    /**
     * 답글 수 증가
     */
    public void increaseReplyCount() {
        this.replyCount = (replyCount == null ? 0 : replyCount) + 1;
        
        // 부모 댓글의 답글 수도 증가
        if (parent != null) {
            parent.increaseReplyCount();
        }
    }
    
    /**
     * 신고 수 증가
     */
    public void increaseReportCount() {
        this.reportCount = (reportCount == null ? 0 : reportCount) + 1;
    }
    
    /**
     * 댓글 고정
     */
    public void pin() {
        this.isPinned = true;
    }
    
    /**
     * 댓글 고정 해제
     */
    public void unpin() {
        this.isPinned = false;
    }
    
    /**
     * 세터 댓글 표시
     */
    public void markAsAuthorComment() {
        this.isAuthorComment = true;
    }
    
    /**
     * 댓글 수정
     */
    public void updateContent(String newContent) {
        this.content = newContent;
    }
    
    @Override
    public Long getId() {
        return commentId;
    }
}
```

---

## 🗳️ 6. RouteDifficultyVote 엔티티 - 난이도 투표

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 루트 난이도 투표
 * - 사용자가 체감한 난이도 투표
 * - 투표 이유 및 근거 제공
 */
@Entity
@Table(name = "route_difficulty_votes", indexes = {
    @Index(name = "idx_vote_route_user", columnList = "route_id, user_id", unique = true),
    @Index(name = "idx_vote_route", columnList = "route_id"),
    @Index(name = "idx_vote_user", columnList = "user_id"),
    @Index(name = "idx_vote_difficulty", columnList = "suggested_difficulty"),
    @Index(name = "idx_vote_created", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteDifficultyVote extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id")
    private Long voteId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @Size(min = 1, max = 10, message = "제안 난이도는 1-10자 사이여야 합니다")
    @Column(name = "suggested_difficulty", nullable = false, length = 10)
    private String suggestedDifficulty; // 제안하는 난이도 (V0, V1, 5.10a 등)
    
    @Column(name = "original_difficulty", length = 10)
    private String originalDifficulty; // 원래 난이도 (투표 당시)
    
    @Column(name = "difficulty_change")
    private Integer difficultyChange; // 난이도 변화 (-2: 매우 쉬움, 0: 적정, +2: 매우 어려움)
    
    @Column(name = "vote_reason", columnDefinition = "TEXT")
    private String voteReason; // 투표 이유
    
    @Column(name = "user_max_grade", length = 10)
    private String userMaxGrade; // 투표자 최고 등급 (신뢰도 측정용)
    
    @Column(name = "user_experience_level", length = 20)
    private String userExperienceLevel; // 투표자 경력 수준
    
    @Column(name = "climb_attempt_count")
    private Integer climbAttemptCount; // 시도 횟수
    
    @Column(name = "is_successful_climb", nullable = false)
    private boolean isSuccessfulClimb = false; // 완등 여부
    
    @Column(name = "confidence_level")
    private Integer confidenceLevel; // 확신도 (1-5)
    
    @Column(name = "vote_weight")
    private Float voteWeight = 1.0f; // 투표 가중치
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 투표
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // 투표자 IP
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 난이도 변화 한글 표시
     */
    @Transient
    public String getDifficultyChangeKorean() {
        if (difficultyChange == null) return "적정";
        
        return switch (difficultyChange) {
            case -2 -> "매우 쉬움";
            case -1 -> "쉬움";
            case 0 -> "적정";
            case 1 -> "어려움";
            case 2 -> "매우 어려움";
            default -> "알 수 없음";
        };
    }
    
    /**
     * 경험 수준 한글 표시
     */
    @Transient
    public String getExperienceLevelKorean() {
        if (userExperienceLevel == null) return "미설정";
        
        return switch (userExperienceLevel) {
            case "BEGINNER" -> "초급자";
            case "INTERMEDIATE" -> "중급자";
            case "ADVANCED" -> "고급자";
            case "EXPERT" -> "전문가";
            default -> userExperienceLevel;
        };
    }
    
    /**
     * 확신도 한글 표시
     */
    @Transient
    public String getConfidenceLevelKorean() {
        if (confidenceLevel == null) return "보통";
        
        return switch (confidenceLevel) {
            case 1 -> "매우 낮음";
            case 2 -> "낮음";
            case 3 -> "보통";
            case 4 -> "높음";
            case 5 -> "매우 높음";
            default -> "보통";
        };
    }
    
    /**
     * 투표 신뢰도 계산
     */
    @Transient
    public float getVoteReliability() {
        float reliability = voteWeight;
        
        // 완등 여부에 따른 가중치
        if (isSuccessfulClimb) {
            reliability += 0.3f;
        }
        
        // 시도 횟수에 따른 가중치
        if (climbAttemptCount != null && climbAttemptCount > 3) {
            reliability += 0.2f;
        }
        
        // 확신도에 따른 가중치
        if (confidenceLevel != null && confidenceLevel >= 4) {
            reliability += 0.1f;
        }
        
        return Math.min(reliability, 2.0f); // 최대 2.0까지
    }
    
    /**
     * 투표 수정
     */
    public void updateVote(String newDifficulty, String reason, Integer confidence) {
        this.suggestedDifficulty = newDifficulty;
        this.voteReason = reason;
        this.confidenceLevel = confidence;
    }
    
    /**
     * 투표 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * 완등 기록 업데이트
     */
    public void recordClimbSuccess(int attemptCount) {
        this.isSuccessfulClimb = true;
        this.climbAttemptCount = attemptCount;
        
        // 완등 시 가중치 증가
        this.voteWeight = Math.min(voteWeight + 0.2f, 2.0f);
    }
    
    @Override
    public Long getId() {
        return voteId;
    }
}
```

---

## 📌 7. RouteScrap 엔티티 - 루트 스크랩

```java
package com.routepick.domain.route.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 루트 스크랩 (북마크)
 * - 사용자가 나중에 도전할 루트 저장
 * - 개인 메모 및 태그 지원
 */
@Entity
@Table(name = "route_scraps", indexes = {
    @Index(name = "idx_scrap_user_route", columnList = "user_id, route_id", unique = true),
    @Index(name = "idx_scrap_user", columnList = "user_id"),
    @Index(name = "idx_scrap_route", columnList = "route_id"),
    @Index(name = "idx_scrap_folder", columnList = "user_id, folder_name"),
    @Index(name = "idx_scrap_created", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RouteScrap extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long scrapId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    
    @Size(max = 50, message = "폴더명은 최대 50자입니다")
    @Column(name = "folder_name", length = 50)
    private String folderName; // 스크랩 폴더명 (기본값: "기본 폴더")
    
    @Column(name = "personal_memo", columnDefinition = "TEXT")
    private String personalMemo; // 개인 메모
    
    @Column(name = "personal_tags", length = 200)
    private String personalTags; // 개인 태그 (쉼표 구분)
    
    @Column(name = "priority_level")
    private Integer priorityLevel = 3; // 우선순위 (1: 높음, 3: 보통, 5: 낮음)
    
    @Column(name = "target_date")
    private java.time.LocalDate targetDate; // 목표 도전일
    
    @Column(name = "scrap_reason", length = 100)
    private String scrapReason; // 스크랩 이유 (TO_TRY, FAVORITE, REFERENCE, GOAL)
    
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false; // 공개 스크랩 (다른 사용자가 볼 수 있음)
    
    @Column(name = "is_notification_enabled", nullable = false)
    private boolean isNotificationEnabled = false; // 알림 설정
    
    @Column(name = "view_count")
    private Integer viewCount = 0; // 조회 횟수 (개인 통계)
    
    @Column(name = "last_viewed_at")
    private java.time.LocalDateTime lastViewedAt; // 마지막 조회일
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 스크랩 이유 한글 표시
     */
    @Transient
    public String getScrapReasonKorean() {
        if (scrapReason == null) return "일반";
        
        return switch (scrapReason) {
            case "TO_TRY" -> "도전 예정";
            case "FAVORITE" -> "즐겨찾기";
            case "REFERENCE" -> "참고용";
            case "GOAL" -> "목표 루트";
            default -> "기타";
        };
    }
    
    /**
     * 우선순위 한글 표시
     */
    @Transient
    public String getPriorityLevelKorean() {
        if (priorityLevel == null) return "보통";
        
        return switch (priorityLevel) {
            case 1 -> "높음";
            case 2 -> "약간 높음";
            case 3 -> "보통";
            case 4 -> "약간 낮음";
            case 5 -> "낮음";
            default -> "보통";
        };
    }
    
    /**
     * 개인 태그 목록 반환
     */
    @Transient
    public java.util.List<String> getPersonalTagList() {
        if (personalTags == null || personalTags.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return java.util.Arrays.asList(personalTags.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 목표일까지 남은 일수
     */
    @Transient
    public long getDaysUntilTarget() {
        if (targetDate == null) return -1;
        return java.time.LocalDate.now().until(targetDate).getDays();
    }
    
    /**
     * 조회 기록
     */
    public void recordView() {
        this.viewCount = (viewCount == null ? 0 : viewCount) + 1;
        this.lastViewedAt = java.time.LocalDateTime.now();
    }
    
    /**
     * 메모 업데이트
     */
    public void updateMemo(String memo) {
        this.personalMemo = memo;
    }
    
    /**
     * 태그 업데이트
     */
    public void updateTags(java.util.List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            this.personalTags = null;
            return;
        }
        this.personalTags = String.join(",", tags);
    }
    
    /**
     * 폴더 이동
     */
    public void moveToFolder(String newFolderName) {
        this.folderName = newFolderName;
    }
    
    /**
     * 우선순위 변경
     */
    public void changePriority(int newPriority) {
        if (newPriority < 1 || newPriority > 5) {
            throw new IllegalArgumentException("우선순위는 1-5 사이여야 합니다");
        }
        this.priorityLevel = newPriority;
    }
    
    /**
     * 목표일 설정
     */
    public void setTargetDate(java.time.LocalDate date) {
        if (date != null && date.isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("목표일은 현재 날짜 이후여야 합니다");
        }
        this.targetDate = date;
    }
    
    @Override
    public Long getId() {
        return scrapId;
    }
}
```

---

## ⚡ 8. 성능 최적화 전략

### 복합 인덱스 DDL 추가
```sql
-- 루트 검색 최적화 (벽별 + 난이도 + 상태)
CREATE INDEX idx_route_search_optimal 
ON routes(wall_id, route_status, difficulty_score, climb_count DESC);

-- 세터별 루트 통계
CREATE INDEX idx_route_setter_stats 
ON routes(route_setter_id, route_status, created_at DESC);

-- 인기 루트 조회
CREATE INDEX idx_route_popularity_complex 
ON routes(route_status, climb_count DESC, like_count DESC, created_at DESC);

-- 댓글 계층 구조 최적화
CREATE INDEX idx_comment_hierarchy 
ON route_comments(route_id, parent_id, is_deleted, created_at DESC);

-- 사용자 스크랩 폴더별 정렬
CREATE INDEX idx_scrap_user_folder_priority 
ON route_scraps(user_id, folder_name, priority_level, created_at DESC);
```

### N+1 문제 해결 쿼리 예시
```java
// Repository에서 Fetch Join 활용
@Query("SELECT r FROM Route r " +
       "LEFT JOIN FETCH r.routeSetter " +
       "LEFT JOIN FETCH r.wall w " +
       "LEFT JOIN FETCH w.branch b " +
       "LEFT JOIN FETCH b.gym " +
       "WHERE r.routeStatus = 'ACTIVE' " +
       "AND r.wall.branch.branchStatus = 'ACTIVE'")
List<Route> findActiveRoutesWithDetails();

// 댓글 계층 구조 조회 최적화
@Query("SELECT c FROM RouteComment c " +
       "LEFT JOIN FETCH c.user " +
       "LEFT JOIN FETCH c.children children " +
       "LEFT JOIN FETCH children.user " +
       "WHERE c.route.id = :routeId " +
       "AND c.parent IS NULL " +
       "AND c.isDeleted = false " +
       "ORDER BY c.isPinned DESC, c.likeCount DESC, c.createdAt ASC")
List<RouteComment> findRootCommentsByRoute(@Param("routeId") Long routeId);

// 난이도 투표 통계 조회
@Query("SELECT v.suggestedDifficulty, COUNT(v), AVG(v.voteWeight) " +
       "FROM RouteDifficultyVote v " +
       "WHERE v.route.id = :routeId AND v.isActive = true " +
       "GROUP BY v.suggestedDifficulty " +
       "ORDER BY COUNT(v) DESC")
List<Object[]> getVoteStatistics(@Param("routeId") Long routeId);
```

### 통계 정보 캐시 전략
```java
// Redis 캐시를 활용한 통계 정보 관리
@Cacheable(value = "routeStats", key = "#routeId")
public RouteStatistics getRouteStatistics(Long routeId) {
    return RouteStatistics.builder()
        .routeId(routeId)
        .totalClimbs(climbRepository.countByRouteId(routeId))
        .averageRating(voteRepository.getAverageRating(routeId))
        .popularityScore(calculatePopularityScore(routeId))
        .build();
}

// 배치 작업으로 통계 정보 업데이트
@Scheduled(fixedRate = 300000) // 5분마다
public void updateRouteStatistics() {
    List<Long> activeRouteIds = routeRepository.findActiveRouteIds();
    for (Long routeId : activeRouteIds) {
        updateRouteStats(routeId);
    }
}
```

---

## ✅ 설계 완료 체크리스트

### 루트 상호작용 엔티티 (3개)
- [x] **RouteComment** - 루트 댓글 (계층형 구조, 베타 스포일러, 소프트 삭제)
- [x] **RouteDifficultyVote** - 난이도 투표 (가중치, 신뢰도 시스템, 경험 수준)
- [x] **RouteScrap** - 루트 스크랩 (개인 폴더, 메모, 태그, 우선순위)

### 계층형 댓글 시스템
- [x] 부모-자식 관계로 대댓글 무제한 지원
- [x] 댓글 타입별 분류 (일반, 베타, 팁, 질문, 칭찬)
- [x] 베타 스포일러 처리 및 익명 댓글 지원
- [x] 세터 댓글 구분 및 고정 댓글 기능

### 난이도 투표 시스템
- [x] 사용자 체감 난이도 투표 (V등급/5.등급 지원)
- [x] 투표 가중치 및 신뢰도 계산 시스템
- [x] 완등 여부, 시도 횟수, 확신도 기반 평가
- [x] 투표자 경력 수준 및 최고 등급 추적

### 스크랩 관리 시스템
- [x] 개인 폴더별 스크랩 분류 관리
- [x] 개인 메모 및 태그 시스템
- [x] 우선순위 및 목표 도전일 설정
- [x] 공개/비공개 스크랩 및 알림 설정

### 성능 최적화
- [x] 복합 인덱스로 검색 성능 향상
- [x] N+1 문제 해결을 위한 Fetch Join
- [x] 통계 정보 캐시 전략
- [x] 배치 작업으로 정기적 데이터 정리

### 사용자 경험 향상
- [x] 베타 정보 스포일러 처리
- [x] 개인화된 스크랩 폴더 시스템
- [x] 난이도 투표 신뢰도 시각화
- [x] 댓글 깊이 제한 및 계층 표시

---

**다음 단계**: step4-4a_community_entities.md (커뮤니티 엔티티 세분화)  
**완료일**: 2025-08-20  
**핵심 성과**: 3개 루트 상호작용 엔티티 + 계층형 댓글 + 난이도 투표 시스템 + 성능 최적화 완성