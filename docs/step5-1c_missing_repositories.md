# Step 5-1c: 누락된 Repository 인터페이스 추가

> UserFollowRepository 및 기타 누락된 Repository 인터페이스 설계  
> 생성일: 2025-08-20  
> 배경: step6-1 Service 구현 중 발견된 누락된 Repository들 추가

---

## 🎯 설계 목표

- **UserFollowRepository**: 팔로우 관계 관리 (step4-3c에 UserFollow 엔티티 존재)
- **누락된 Repository 보완**: Service 레이어에서 필요한 모든 Repository 인터페이스 추가
- **엔티티-Repository 매핑 완성**: 모든 엔티티에 대응하는 Repository 제공
- **필드명 일치**: 엔티티의 실제 필드명과 Repository 메서드 매칭

---

## 👥 UserFollowRepository - 팔로우 관계 Repository

### UserFollowRepository.java
```java
package com.routepick.domain.user.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.user.entity.UserFollow;
import com.routepick.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UserFollow Repository
 * - 팔로우 관계 관리
 * - 상호 팔로우 확인
 * - 팔로워/팔로잉 목록 조회
 * - 팔로우 통계 및 관리
 * 
 * 참고: UserFollow 엔티티는 step4-3c에 정의됨
 * 필드명: followerUser, followingUser (User 타입)
 */
@Repository
public interface UserFollowRepository extends BaseRepository<UserFollow, Long> {
    
    // ===== 기본 팔로우 관계 조회 =====
    
    /**
     * 팔로우 관계 존재 확인
     * @param followerUser 팔로우 하는 사용자
     * @param followingUser 팔로우 받는 사용자
     */
    @Query("SELECT CASE WHEN COUNT(uf) > 0 THEN true ELSE false END FROM UserFollow uf " +
           "WHERE uf.followerUser = :followerUser AND uf.followingUser = :followingUser " +
           "AND uf.isActive = true")
    boolean existsByFollowerUserAndFollowingUser(@Param("followerUser") User followerUser, 
                                                @Param("followingUser") User followingUser);
    
    /**
     * 팔로우 관계 조회
     * @param followerUser 팔로우 하는 사용자
     * @param followingUser 팔로우 받는 사용자
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followerUser = :followerUser AND uf.followingUser = :followingUser " +
           "AND uf.isActive = true")
    Optional<UserFollow> findByFollowerUserAndFollowingUser(@Param("followerUser") User followerUser,
                                                           @Param("followingUser") User followingUser);
    
    // ===== 팔로워 목록 조회 =====
    
    /**
     * 특정 사용자의 팔로워 목록 조회
     * @param userId 팔로우 받는 사용자 ID
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "JOIN FETCH uf.followerUser fu " +
           "JOIN FETCH fu.userProfile " +
           "WHERE uf.followingUser.userId = :userId AND uf.isActive = true " +
           "ORDER BY uf.followDate DESC")
    Page<UserFollow> findFollowersByUserId(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 팔로워 사용자 목록만 조회 (User 엔티티)
     * @param userId 팔로우 받는 사용자 ID
     */
    @Query("SELECT uf.followerUser FROM UserFollow uf " +
           "WHERE uf.followingUser.userId = :userId AND uf.isActive = true " +
           "ORDER BY uf.followDate DESC")
    Page<User> findFollowerUsersByUserId(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 팔로잉 목록 조회 =====
    
    /**
     * 특정 사용자의 팔로잉 목록 조회
     * @param userId 팔로우 하는 사용자 ID
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "JOIN FETCH uf.followingUser fu " +
           "JOIN FETCH fu.userProfile " +
           "WHERE uf.followerUser.userId = :userId AND uf.isActive = true " +
           "ORDER BY uf.followDate DESC")
    Page<UserFollow> findFollowingsByUserId(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 팔로잉 사용자 목록만 조회 (User 엔티티)
     * @param userId 팔로우 하는 사용자 ID
     */
    @Query("SELECT uf.followingUser FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId AND uf.isActive = true " +
           "ORDER BY uf.followDate DESC")
    Page<User> findFollowingUsersByUserId(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 상호 팔로우 관리 =====
    
    /**
     * 상호 팔로우 확인
     */
    @Query("SELECT CASE WHEN COUNT(uf1) > 0 AND COUNT(uf2) > 0 THEN true ELSE false END " +
           "FROM UserFollow uf1, UserFollow uf2 " +
           "WHERE uf1.followerUser.userId = :userId1 AND uf1.followingUser.userId = :userId2 " +
           "AND uf2.followerUser.userId = :userId2 AND uf2.followingUser.userId = :userId1 " +
           "AND uf1.isActive = true AND uf2.isActive = true")
    boolean isMutualFollow(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
    /**
     * 상호 팔로우 상태 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserFollow uf SET uf.isMutual = :isMutual " +
           "WHERE (uf.followerUser.userId = :userId1 AND uf.followingUser.userId = :userId2) " +
           "OR (uf.followerUser.userId = :userId2 AND uf.followingUser.userId = :userId1)")
    int updateMutualFollowStatus(@Param("userId1") Long userId1, 
                                @Param("userId2") Long userId2, 
                                @Param("isMutual") boolean isMutual);
    
    // ===== 팔로우 통계 =====
    
    /**
     * 팔로워 수 조회
     */
    @Query("SELECT COUNT(uf) FROM UserFollow uf " +
           "WHERE uf.followingUser.userId = :userId AND uf.isActive = true")
    long countFollowersByUserId(@Param("userId") Long userId);
    
    /**
     * 팔로잉 수 조회
     */
    @Query("SELECT COUNT(uf) FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId AND uf.isActive = true")
    long countFollowingsByUserId(@Param("userId") Long userId);
    
    /**
     * 상호 팔로우 수 조회
     */
    @Query("SELECT COUNT(uf) FROM UserFollow uf " +
           "WHERE uf.followerUser.userId = :userId AND uf.isActive = true AND uf.isMutual = true")
    long countMutualFollowsByUserId(@Param("userId") Long userId);
    
    // ===== 팔로우 관리 =====
    
    /**
     * 팔로우 비활성화 (언팔로우)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserFollow uf SET " +
           "uf.isActive = false, " +
           "uf.unfollowDate = CURRENT_TIMESTAMP " +
           "WHERE uf.followerUser = :followerUser AND uf.followingUser = :followingUser " +
           "AND uf.isActive = true")
    int unfollowUser(@Param("followerUser") User followerUser, @Param("followingUser") User followingUser);
    
    /**
     * 팔로우 재활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserFollow uf SET " +
           "uf.isActive = true, " +
           "uf.unfollowDate = null, " +
           "uf.followDate = CURRENT_TIMESTAMP " +
           "WHERE uf.followerUser = :followerUser AND uf.followingUser = :followingUser")
    int refollowUser(@Param("followerUser") User followerUser, @Param("followingUser") User followingUser);
    
    // ===== 팔로우 소셜 기능 =====
    
    /**
     * 친한 친구 설정
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserFollow uf SET uf.closeFriend = :closeFriend " +
           "WHERE uf.followerUser.userId = :followerId AND uf.followingUser.userId = :followingId")
    int updateCloseFriend(@Param("followerId") Long followerId, 
                         @Param("followingId") Long followingId, 
                         @Param("closeFriend") boolean closeFriend);
    
    /**
     * 알림 설정 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserFollow uf SET uf.notificationEnabled = :enabled " +
           "WHERE uf.followerUser.userId = :followerId AND uf.followingUser.userId = :followingId")
    int updateNotificationSetting(@Param("followerId") Long followerId,
                                 @Param("followingId") Long followingId,
                                 @Param("enabled") boolean enabled);
    
    /**
     * 사용자 차단
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserFollow uf SET " +
           "uf.blocked = true, " +
           "uf.isActive = false, " +
           "uf.unfollowDate = CURRENT_TIMESTAMP " +
           "WHERE uf.followerUser.userId = :blockerId AND uf.followingUser.userId = :blockedId")
    int blockUser(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);
    
    /**
     * 음소거 설정
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserFollow uf SET uf.muted = :muted " +
           "WHERE uf.followerUser.userId = :followerId AND uf.followingUser.userId = :followingId")
    int muteUser(@Param("followerId") Long followerId, 
                @Param("followingId") Long followingId, 
                @Param("muted") boolean muted);
    
    // ===== 팔로우 추천 =====
    
    /**
     * 상호 친구 기반 추천 (친구의 친구)
     */
    @Query("SELECT DISTINCT uf2.followingUser FROM UserFollow uf1 " +
           "JOIN UserFollow uf2 ON uf1.followingUser = uf2.followerUser " +
           "WHERE uf1.followerUser.userId = :userId " +
           "AND uf2.followingUser.userId != :userId " +
           "AND uf1.isActive = true AND uf2.isActive = true " +
           "AND NOT EXISTS (SELECT 1 FROM UserFollow uf3 " +
           "                WHERE uf3.followerUser.userId = :userId " +
           "                AND uf3.followingUser = uf2.followingUser " +
           "                AND uf3.isActive = true) " +
           "ORDER BY uf2.followDate DESC")
    List<User> findMutualFriendRecommendations(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 인기 사용자 추천 (팔로워 수 기준)
     */
    @Query("SELECT uf.followingUser, COUNT(uf) as followerCount FROM UserFollow uf " +
           "WHERE uf.isActive = true " +
           "AND uf.followingUser.userStatus = 'ACTIVE' " +
           "AND NOT EXISTS (SELECT 1 FROM UserFollow uf2 " +
           "                WHERE uf2.followerUser.userId = :userId " +
           "                AND uf2.followingUser = uf.followingUser " +
           "                AND uf2.isActive = true) " +
           "AND uf.followingUser.userId != :userId " +
           "GROUP BY uf.followingUser " +
           "ORDER BY followerCount DESC")
    List<Object[]> findPopularUserRecommendations(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 팔로우 분석 =====
    
    /**
     * 최근 팔로우 활동 조회
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.followDate >= :since " +
           "ORDER BY uf.followDate DESC")
    List<UserFollow> findRecentFollowActivities(@Param("since") LocalDateTime since);
    
    /**
     * 팔로우 경로별 통계
     */
    @Query("SELECT uf.followSource, COUNT(uf) FROM UserFollow uf " +
           "WHERE uf.followDate >= :since " +
           "GROUP BY uf.followSource " +
           "ORDER BY COUNT(uf) DESC")
    List<Object[]> getFollowSourceStatistics(@Param("since") LocalDateTime since);
    
    /**
     * 상호작용이 활발한 팔로우 관계 조회
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.interactionCount > :minInteractions " +
           "AND uf.lastInteractionDate >= :since " +
           "ORDER BY uf.interactionCount DESC")
    List<UserFollow> findActiveFollowRelationships(@Param("minInteractions") Integer minInteractions,
                                                  @Param("since") LocalDateTime since);
    
    // ===== 정리 및 관리 =====
    
    /**
     * 비활성 팔로우 관계 정리 (6개월 이상 상호작용 없음)
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.isActive = true " +
           "AND (uf.lastInteractionDate IS NULL OR uf.lastInteractionDate < :cutoffDate) " +
           "AND uf.followDate < :cutoffDate")
    List<UserFollow> findInactiveFollowRelationships(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 차단된 팔로우 관계 조회
     */
    @Query("SELECT uf FROM UserFollow uf " +
           "WHERE uf.blocked = true " +
           "ORDER BY uf.unfollowDate DESC")
    List<UserFollow> findBlockedFollowRelationships();
    
    /**
     * 상호작용 카운트 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserFollow uf SET " +
           "uf.interactionCount = COALESCE(uf.interactionCount, 0) + 1, " +
           "uf.lastInteractionDate = CURRENT_TIMESTAMP " +
           "WHERE uf.followerUser.userId = :followerId AND uf.followingUser.userId = :followingId " +
           "AND uf.isActive = true")
    int incrementInteractionCount(@Param("followerId") Long followerId, @Param("followingId") Long followingId);
}
```

---

## 📋 Repository 완성도 체크

### 기존 Repository 검증 결과
| Repository | 상태 | 비고 |
|------------|------|------|
| UserRepository | ✅ 완성 | step5-1b에 구현됨 |
| UserProfileRepository | ✅ 완성 | step5-1b에 구현됨, `findByUser` 메서드 추가됨 |
| SocialAccountRepository | ✅ 완성 | step5-1b에 구현됨 |
| UserVerificationRepository | ✅ 완성 | step5-1b에 구현됨 |
| UserAgreementRepository | ✅ 완성 | step5-1b에 구현됨 |
| ApiTokenRepository | ✅ 완성 | step5-1b에 구현됨, 메서드명 수정됨 |
| AgreementContentRepository | ✅ 완성 | step5-1b에 구현됨, 메서드명 수정됨 |
| **UserFollowRepository** | ✅ **신규 추가** | **이 파일에서 구현** |

### 메서드명 매칭 검증
| Service에서 호출하는 메서드 | Repository 실제 메서드 | 상태 |
|--------------------------|---------------------|------|
| `existsByFollowerAndFollowing` | `existsByFollowerUserAndFollowingUser` | ✅ 수정됨 |
| `findByFollowerAndFollowing` | `findByFollowerUserAndFollowingUser` | ✅ 수정됨 |
| `findFollowersByUserId` | `findFollowersByUserId` | ✅ 일치 |
| `findFollowingsByUserId` | `findFollowingsByUserId` | ✅ 일치 |
| `revokeByToken` | `revokeToken` | ✅ 수정됨 |
| `revokeAllByUserId` | `revokeAllUserTokens` | ✅ 수정됨 |
| `findActiveByType` | `findActiveByAgreementType` | ✅ 수정됨 |

---

## 🎯 UserFollow 엔티티 필드명 매핑

### 실제 엔티티 필드 (step4-3c 기준)
```java
// UserFollow 엔티티의 실제 필드명
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "follower_user_id", nullable = false)
private User followerUser; // 팔로우 하는 사용자

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "following_user_id", nullable = false)  
private User followingUser; // 팔로우 받는 사용자
```

### Repository 메서드 매핑
- **Service 기대**: `follower`, `following` 
- **실제 엔티티**: `followerUser`, `followingUser`
- **해결책**: Repository 메서드명을 실제 필드명에 맞춰 구현

---

## ✅ 완성된 Repository 인터페이스 목록

### 공통 Repository (step5-1a)
- [x] BaseRepository
- [x] SoftDeleteRepository  
- [x] BaseQueryDslRepository
- [x] QueryDslConfig

### User 도메인 Repository (step5-1b + step5-1c)
- [x] UserRepository
- [x] UserProfileRepository (+ `findByUser` 메서드 추가)
- [x] SocialAccountRepository
- [x] UserVerificationRepository
- [x] UserAgreementRepository  
- [x] ApiTokenRepository (+ 메서드명 수정)
- [x] AgreementContentRepository (+ 메서드명 수정)
- [x] **UserFollowRepository** (신규 추가)

### Projection 인터페이스 (step5-1a)
- [x] UserSummaryProjection
- [x] GymBranchLocationProjection
- [x] RouteBasicProjection
- [x] TagStatisticsProjection

---

## 📝 다음 단계

1. **step6-1 Service 검증**: UserFollowRepository 추가 후 Service 코드 재검증
2. **step5-2 계획**: Gym, Route, Tag 도메인 Repository 설계
3. **통합 테스트**: Repository-Service 연동 테스트
4. **성능 최적화**: 인덱스 및 쿼리 성능 검증

---

*추가 완료: UserFollowRepository 및 누락된 Repository 메서드들*  
*Repository 레이어 User 도메인 100% 완성*