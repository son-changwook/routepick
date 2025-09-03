# Step5-1b1: User Core Repositories (1/2)

> **사용자 핵심 Repository**  
> 5단계 Repository 레이어 구현: User, UserProfile, SocialAccount 관리

---

## 📋 파일 분할 정보
- **원본 파일**: step5-1b_user_repositories.md (1,354줄)
- **분할 구성**: 2개 파일로 세분화
- **현재 파일**: step5-1b1_user_core_repositories.md (1/2)
- **포함 Repository**: UserRepository, UserProfileRepository, SocialAccountRepository

---

## 🎯 설계 목표

- **이메일 기반 로그인 최적화**: Spring Security UserDetailsService 지원
- **소셜 로그인 핵심**: 4개 Provider (Google, Kakao, Naver, Facebook) 지원
- **성능 최적화**: N+1 문제 해결, 인덱스 활용, @EntityGraph 설계
- **보안 강화**: SQL Injection 방지, JWT 토큰 관리, 민감정보 보호
- **한국 특화**: 휴대폰 인증, CI/DI 연계정보 관리, 약관 동의 처리

---

## 👤 User 핵심 Repository 설계 (3개)

### UserRepository.java - 사용자 기본 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.enums.UserStatus;
import com.routepick.common.enums.UserType;
import com.routepick.common.repository.SoftDeleteRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.projection.UserSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * User Repository
 * - 이메일 기반 로그인 최적화
 * - Spring Security UserDetailsService 지원
 * - 사용자 통계 및 관리 기능
 */
@Repository
public interface UserRepository extends SoftDeleteRepository<User, Long> {
    
    // ===== 기본 조회 메서드 (이메일 기반 로그인) =====
    
    /**
     * 이메일로 사용자 조회 (로그인용)
     */
    @EntityGraph(attributePaths = {"userProfile", "socialAccounts"})
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.userStatus != 'DELETED'")
    Optional<User> findByEmail(@Param("email") String email);
    
    /**
     * 이메일과 상태로 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.userStatus = :status")
    Optional<User> findByEmailAndUserStatus(@Param("email") String email, @Param("status") UserStatus status);
    
    /**
     * 활성 사용자만 이메일로 조회
     */
    @EntityGraph(attributePaths = {"userProfile", "userVerification"})
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.userStatus = 'ACTIVE'")
    Optional<User> findActiveByEmail(@Param("email") String email);
    
    // ===== 중복 확인 메서드 =====
    
    /**
     * 이메일 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email AND u.userStatus != 'DELETED'")
    boolean existsByEmail(@Param("email") String email);
    
    /**
     * 닉네임 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.nickName = :nickName AND u.userStatus != 'DELETED'")
    boolean existsByNickName(@Param("nickName") String nickName);
    
    /**
     * 휴대폰 번호 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.phone = :phone AND u.userStatus != 'DELETED'")
    boolean existsByPhone(@Param("phone") String phone);
    
    // ===== 닉네임 및 검색 메서드 =====
    
    /**
     * 닉네임으로 사용자 조회
     */
    @EntityGraph(attributePaths = {"userProfile"})
    @Query("SELECT u FROM User u WHERE u.nickName = :nickName AND u.userStatus = 'ACTIVE'")
    Optional<User> findByNickName(@Param("nickName") String nickName);
    
    /**
     * 한글 닉네임 부분 검색
     */
    @Query("SELECT u FROM User u WHERE u.nickName LIKE %:keyword% AND u.userStatus = 'ACTIVE' ORDER BY u.nickName")
    List<UserSummaryProjection> findByNickNameContaining(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 사용자명 부분 검색
     */
    @Query("SELECT u FROM User u WHERE u.userName LIKE %:keyword% AND u.userStatus = 'ACTIVE' ORDER BY u.userName")
    List<UserSummaryProjection> findByUserNameContaining(@Param("keyword") String keyword, Pageable pageable);
    
    // ===== 사용자 타입별 조회 =====
    
    /**
     * 사용자 타입별 조회
     */
    @Query("SELECT u FROM User u WHERE u.userType = :userType AND u.userStatus = 'ACTIVE' ORDER BY u.createdAt DESC")
    Page<UserSummaryProjection> findByUserType(@Param("userType") UserType userType, Pageable pageable);
    
    /**
     * 관리자 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.userType IN ('ADMIN', 'GYM_ADMIN') AND u.userStatus = 'ACTIVE' ORDER BY u.lastLoginAt DESC")
    List<UserSummaryProjection> findAllAdmins();
    
    // ===== 로그인 관련 메서드 =====
    
    /**
     * 로그인 성공 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.lastLoginAt = CURRENT_TIMESTAMP, " +
           "u.loginCount = COALESCE(u.loginCount, 0) + 1, " +
           "u.failedLoginCount = 0, " +
           "u.lastFailedLoginAt = null " +
           "WHERE u.userId = :userId")
    int updateLoginSuccess(@Param("userId") Long userId);
    
    /**
     * 로그인 실패 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.failedLoginCount = COALESCE(u.failedLoginCount, 0) + 1, " +
           "u.lastFailedLoginAt = CURRENT_TIMESTAMP " +
           "WHERE u.userId = :userId")
    int updateLoginFailure(@Param("userId") Long userId);
    
    /**
     * 계정 잠금 처리 (로그인 실패 5회 이상)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.userStatus = 'SUSPENDED' " +
           "WHERE u.userId = :userId AND u.failedLoginCount >= 5")
    int lockAccount(@Param("userId") Long userId);
    
    // ===== 통계 및 관리 메서드 =====
    
    /**
     * 기간별 가입자 수 조회
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate AND u.userStatus != 'DELETED'")
    long countByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * 상태별 사용자 수 조회
     */
    @Query("SELECT u.userStatus, COUNT(u) FROM User u WHERE u.userStatus != 'DELETED' GROUP BY u.userStatus")
    List<Object[]> countByUserStatus();
    
    /**
     * 최근 활성 사용자 조회 (30일 이내 로그인)
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt >= :since AND u.userStatus = 'ACTIVE' ORDER BY u.lastLoginAt DESC")
    Page<UserSummaryProjection> findRecentActiveUsers(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * 미인증 사용자 조회 (이메일 미인증)
     */
    @Query("SELECT u FROM User u JOIN u.userVerification uv " +
           "WHERE uv.emailVerified = false AND u.createdAt >= :since AND u.userStatus = 'ACTIVE'")
    List<User> findUnverifiedUsers(@Param("since") LocalDateTime since);
    
    /**
     * 장기 미접속 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt < :cutoffDate AND u.userStatus = 'ACTIVE' ORDER BY u.lastLoginAt")
    Page<UserSummaryProjection> findInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);
    
    // ===== 비밀번호 관리 =====
    
    /**
     * 비밀번호 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.passwordHash = :passwordHash, u.passwordChangedAt = CURRENT_TIMESTAMP " +
           "WHERE u.userId = :userId")
    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
    
    /**
     * 비밀번호 만료 예정 사용자 조회 (90일 기준)
     */
    @Query("SELECT u FROM User u WHERE u.passwordChangedAt < :expiryDate AND u.userStatus = 'ACTIVE'")
    List<User> findUsersWithExpiringPasswords(@Param("expiryDate") LocalDateTime expiryDate);
}
```

### UserProfileRepository.java - 사용자 프로필 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.user.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * UserProfile Repository
 * - 사용자 프로필 관리
 * - 완등 통계 업데이트
 * - 팔로워/팔로잉 통계 관리
 */
@Repository
public interface UserProfileRepository extends BaseRepository<UserProfile, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자 ID로 프로필 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "LEFT JOIN FETCH up.climbingLevel " +
           "LEFT JOIN FETCH up.homeBranch " +
           "WHERE up.user.userId = :userId")
    Optional<UserProfile> findByUserId(@Param("userId") Long userId);
    
    /**
     * User 엔티티로 프로필 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "LEFT JOIN FETCH up.climbingLevel " +
           "LEFT JOIN FETCH up.homeBranch " +
           "WHERE up.user = :user")
    Optional<UserProfile> findByUser(@Param("user") com.routepick.domain.user.entity.User user);
    
    /**
     * 공개 프로필만 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findAllPublicProfiles(Pageable pageable);
    
    // ===== 프로필 이미지 관리 =====
    
    /**
     * 프로필 이미지 URL 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.profileImageUrl = :imageUrl " +
           "WHERE up.user.userId = :userId")
    int updateProfileImage(@Param("userId") Long userId, @Param("imageUrl") String imageUrl);
    
    /**
     * 프로필 이미지 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.profileImageUrl = null " +
           "WHERE up.user.userId = :userId")
    int deleteProfileImage(@Param("userId") Long userId);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 클라이밍 레벨별 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.climbingLevel.levelId = :levelId AND up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findByClimbingLevel(@Param("levelId") Long levelId, Pageable pageable);
    
    /**
     * 홈 지점별 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.homeBranch.branchId = :branchId AND up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findByHomeBranch(@Param("branchId") Long branchId, Pageable pageable);
    
    /**
     * 클라이밍 경력별 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.climbingYears >= :minYears AND up.climbingYears <= :maxYears " +
           "AND up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findByClimbingYearsRange(@Param("minYears") Integer minYears, 
                                              @Param("maxYears") Integer maxYears, 
                                              Pageable pageable);
    
    /**
     * 성별별 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.gender = :gender AND up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findByGender(@Param("gender") String gender, Pageable pageable);
    
    // ===== 완등 통계 관리 =====
    
    /**
     * 완등 카운트 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET " +
           "up.totalClimbCount = COALESCE(up.totalClimbCount, 0) + 1, " +
           "up.monthlyClimbCount = COALESCE(up.monthlyClimbCount, 0) + 1 " +
           "WHERE up.user.userId = :userId")
    int incrementClimbCount(@Param("userId") Long userId);
    
    /**
     * 월간 완등 카운트 리셋
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.monthlyClimbCount = 0")
    int resetMonthlyClimbCount();
    
    /**
     * 특정 사용자의 월간 완등 카운트 리셋
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.monthlyClimbCount = 0 WHERE up.user.userId = :userId")
    int resetUserMonthlyClimbCount(@Param("userId") Long userId);
    
    // ===== 통계 조회 =====
    
    /**
     * 인기 사용자 조회 (팔로워 수 기준)
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.isPublic = true AND u.userStatus = 'ACTIVE' " +
           "ORDER BY up.followerCount DESC, up.totalClimbCount DESC")
    Page<UserProfile> findPopularUsers(Pageable pageable);
    
    /**
     * 활발한 클라이머 조회 (월간 완등 수 기준)
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.isPublic = true AND u.userStatus = 'ACTIVE' AND up.monthlyClimbCount > 0 " +
           "ORDER BY up.monthlyClimbCount DESC")
    Page<UserProfile> findActiveClimbers(Pageable pageable);
    
    /**
     * 프로필 완성도 높은 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.isPublic = true AND u.userStatus = 'ACTIVE' " +
           "AND up.profileImageUrl IS NOT NULL " +
           "AND up.bio IS NOT NULL AND up.bio != '' " +
           "AND up.climbingLevel IS NOT NULL " +
           "AND up.homeBranch IS NOT NULL")
    Page<UserProfile> findCompleteProfiles(Pageable pageable);
}
```

### SocialAccountRepository.java - 소셜 계정 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.enums.SocialProvider;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.user.entity.SocialAccount;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SocialAccount Repository
 * - 소셜 로그인 핵심 Repository
 * - 4개 Provider 지원 (Google, Kakao, Naver, Facebook)
 * - 토큰 관리 및 중복 확인
 */
@Repository
public interface SocialAccountRepository extends BaseRepository<SocialAccount, Long> {
    
    // ===== 소셜 로그인 핵심 메서드 =====
    
    /**
     * Provider와 소셜 ID로 계정 조회 (로그인 핵심)
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "JOIN FETCH sa.user u " +
           "WHERE sa.provider = :provider AND sa.socialId = :socialId AND u.userStatus = 'ACTIVE'")
    Optional<SocialAccount> findByProviderAndSocialId(@Param("provider") SocialProvider provider, 
                                                      @Param("socialId") String socialId);
    
    /**
     * Provider와 소셜 ID 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(sa) > 0 THEN true ELSE false END FROM SocialAccount sa " +
           "JOIN sa.user u " +
           "WHERE sa.provider = :provider AND sa.socialId = :socialId AND u.userStatus != 'DELETED'")
    boolean existsByProviderAndSocialId(@Param("provider") SocialProvider provider, 
                                       @Param("socialId") String socialId);
    
    // ===== 사용자별 소셜 계정 관리 =====
    
    /**
     * 사용자의 모든 소셜 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.user.userId = :userId " +
           "ORDER BY sa.isPrimary DESC, sa.lastLoginAt DESC")
    List<SocialAccount> findByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 특정 Provider 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.user.userId = :userId AND sa.provider = :provider")
    Optional<SocialAccount> findByUserAndProvider(@Param("userId") Long userId, 
                                                 @Param("provider") SocialProvider provider);
    
    /**
     * 사용자의 Primary 소셜 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.user.userId = :userId AND sa.isPrimary = true")
    Optional<SocialAccount> findPrimaryByUserId(@Param("userId") Long userId);
    
    // ===== Primary 계정 관리 =====
    
    /**
     * 사용자의 모든 소셜 계정 Primary 해제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SocialAccount sa SET sa.isPrimary = false WHERE sa.user.userId = :userId")
    int clearAllPrimaryByUserId(@Param("userId") Long userId);
    
    /**
     * 특정 소셜 계정을 Primary로 설정
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SocialAccount sa SET sa.isPrimary = true WHERE sa.socialAccountId = :socialAccountId")
    int setPrimaryAccount(@Param("socialAccountId") Long socialAccountId);
    
    // ===== 토큰 관리 =====
    
    /**
     * 토큰 정보 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SocialAccount sa SET " +
           "sa.accessToken = :accessToken, " +
           "sa.refreshToken = :refreshToken, " +
           "sa.tokenExpiresAt = :expiresAt " +
           "WHERE sa.socialAccountId = :socialAccountId")
    int updateTokens(@Param("socialAccountId") Long socialAccountId,
                    @Param("accessToken") String accessToken,
                    @Param("refreshToken") String refreshToken,
                    @Param("expiresAt") LocalDateTime expiresAt);
    
    /**
     * 마지막 로그인 시간 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SocialAccount sa SET sa.lastLoginAt = CURRENT_TIMESTAMP " +
           "WHERE sa.socialAccountId = :socialAccountId")
    int updateLastLoginAt(@Param("socialAccountId") Long socialAccountId);
    
    /**
     * 만료된 토큰을 가진 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.tokenExpiresAt < CURRENT_TIMESTAMP")
    List<SocialAccount> findExpiredTokenAccounts();
    
    // ===== 통계 및 관리 =====
    
    /**
     * Provider별 사용자 수 통계
     */
    @Query("SELECT sa.provider, COUNT(DISTINCT sa.user.userId) FROM SocialAccount sa " +
           "JOIN sa.user u " +
           "WHERE u.userStatus = 'ACTIVE' " +
           "GROUP BY sa.provider")
    List<Object[]> countUsersByProvider();
    
    /**
     * 특정 Provider의 활성 사용자 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "JOIN FETCH sa.user u " +
           "WHERE sa.provider = :provider AND u.userStatus = 'ACTIVE' " +
           "ORDER BY sa.lastLoginAt DESC")
    List<SocialAccount> findActiveAccountsByProvider(@Param("provider") SocialProvider provider);
    
    /**
     * 최근 소셜 로그인 사용자 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "JOIN FETCH sa.user u " +
           "WHERE sa.lastLoginAt >= :since AND u.userStatus = 'ACTIVE' " +
           "ORDER BY sa.lastLoginAt DESC")
    List<SocialAccount> findRecentSocialLogins(@Param("since") LocalDateTime since);
}
```

---

## ⚡ 성능 최적화 전략

### 사용자 인증 최적화
```sql
-- 이메일 기반 로그인 최적화
CREATE UNIQUE INDEX idx_user_email_status 
ON users(email, user_status);

-- 닉네임 검색 최적화
CREATE INDEX idx_user_nickname_status 
ON users(nick_name, user_status);

-- 로그인 통계 최적화
CREATE INDEX idx_user_last_login_status 
ON users(last_login_at DESC, user_status);
```

### 프로필 검색 최적화
```sql
-- 프로필 완성도 검색 최적화
CREATE INDEX idx_profile_complete_public 
ON user_profiles(is_public, profile_image_url, bio, climbing_level_id, home_branch_id);

-- 클라이밍 통계 최적화
CREATE INDEX idx_profile_climb_stats 
ON user_profiles(monthly_climb_count DESC, total_climb_count DESC);
```

### 소셜 로그인 최적화
```sql
-- 소셜 로그인 조회 최적화
CREATE UNIQUE INDEX idx_social_provider_id 
ON social_accounts(provider, provider_id, is_connected);

-- 사용자별 소셜 계정 조회 최적화
CREATE INDEX idx_social_user_provider 
ON social_accounts(user_id, provider, is_connected);
```

---

## ✅ 설계 완료 체크리스트

### User 핵심 Repository (3개)
- [x] **UserRepository** - 이메일 기반 로그인 및 사용자 관리
- [x] **UserProfileRepository** - 프로필 관리 및 완등 통계
- [x] **SocialAccountRepository** - 소셜 로그인 (Google, Kakao, Naver, Facebook)

### 핵심 기능 구현
- [x] Spring Security UserDetailsService 지원
- [x] 소셜 로그인 4개 Provider 완전 지원
- [x] 이메일/닉네임/휴대폰 중복 확인
- [x] 로그인 실패 및 계정 잠금 처리

### 성능 최적화
- [x] @EntityGraph N+1 문제 해결
- [x] 이메일 기반 로그인 인덱스 최적화
- [x] 프로필 검색 및 완성도 조회 최적화
- [x] 소셜 계정 Provider별 조회 최적화

### 보안 강화
- [x] 비밀번호 해시 관리
- [x] 소셜 토큰 만료 관리
- [x] 계정 연결/해제 보안 처리
- [x] 중복 소셜 계정 탐지

---

**분할 진행**: step5-1b_user_repositories.md → step5-1b1 (1/2)  
**완료일**: 2025-08-20  
**핵심 성과**: User 핵심 3개 Repository 완성 (인증/프로필/소셜)