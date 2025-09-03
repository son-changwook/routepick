# step9-4e1_follow_privacy_security_tests.md

## 📋 팔로우 스팸 방지 & 프라이버시 보안 테스트

### 🎯 목표
- 팔로우 스팸 공격 방지 시스템 검증
- 프라이버시 설정 우회 방지 테스트
- 사용자 정보 보호 메커니즘 검증
- 소셜 기능 남용 방어 테스트

### 🚫 테스트 범위
- **팔로우 스팸**: Rate Limiting, 일일 팔로우 제한
- **프라이버시**: 공개/비공개 설정 강제 적용
- **정보 보호**: 민감 정보 마스킹, 접근 제어
- **패턴 분석**: 봇 활동 탐지, 스팸 점수 계산

---

## 🎯 보안 테스트 목표

### 발견된 취약점 분석
- **Critical**: 팔로우 스팸 공격 가능성
- **High**: 프라이버시 설정 우회 시도
- **High**: 대량 메시지 발송 남용
- **Medium**: 소셜 로그인 토큰 탈취 위험
- **Medium**: 사용자 정보 노출 가능성

### 보안 테스트 범위
- **팔로우 스팸**: Rate Limiting, 일일 팔로우 제한
- **프라이버시**: 공개/비공개 설정 강제 적용
- **메시지 남용**: 발송 제한, 스팸 필터링
- **토큰 보안**: 소셜 로그인 토큰 검증
- **정보 보호**: 민감 정보 마스킹, 접근 제어

---

## 🚫 팔로우 스팸 방지 테스트

### FollowSpamPreventionTest.java
```java
package com.routepick.security.test.social;

import com.routepick.service.user.FollowService;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.social.*;
import com.routepick.util.RateLimitUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우 스팸 방지 테스트")
class FollowSpamPreventionTest {

    @Mock
    private FollowService followService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RateLimitUtil rateLimitUtil;

    @InjectMocks
    private FollowSecurityService followSecurityService;

    private User spamUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        spamUser = createTestUser(1L, "스팸사용자");
        targetUser = createTestUser(2L, "타겟사용자");
    }

    @Nested
    @DisplayName("팔로우 Rate Limiting 테스트")
    class FollowRateLimitTest {

        @Test
        @DisplayName("1분 내 팔로우 시도 제한 - 10회 초과 시 차단")
        void followRateLimit_PerMinute_Block() {
            // Given
            Long userId = 1L;
            String rateLimitKey = "follow:" + userId;
            
            given(rateLimitUtil.isAllowed(rateLimitKey, 10, 60)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                followSecurityService.checkFollowRateLimit(userId))
                .isInstanceOf(FollowRateLimitExceededException.class)
                .hasMessageContaining("1분 내 팔로우 시도 횟수를 초과했습니다");
        }

        @Test
        @DisplayName("1시간 내 팔로우 시도 제한 - 100회 초과 시 차단")
        void followRateLimit_PerHour_Block() {
            // Given
            Long userId = 1L;
            String hourlyKey = "follow_hourly:" + userId;
            
            given(rateLimitUtil.isAllowed("follow:" + userId, 10, 60)).willReturn(true);
            given(rateLimitUtil.isAllowed(hourlyKey, 100, 3600)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                followSecurityService.checkFollowRateLimit(userId))
                .isInstanceOf(FollowRateLimitExceededException.class)
                .hasMessageContaining("1시간 내 팔로우 시도 횟수를 초과했습니다");
        }

        @Test
        @DisplayName("24시간 내 팔로우 시도 제한 - 500회 초과 시 계정 제재")
        void followRateLimit_PerDay_AccountSuspension() {
            // Given
            Long userId = 1L;
            String dailyKey = "follow_daily:" + userId;
            
            given(rateLimitUtil.isAllowed("follow:" + userId, 10, 60)).willReturn(true);
            given(rateLimitUtil.isAllowed("follow_hourly:" + userId, 100, 3600)).willReturn(true);
            given(rateLimitUtil.isAllowed(dailyKey, 500, 86400)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                followSecurityService.checkFollowRateLimit(userId))
                .isInstanceOf(AccountSuspendedException.class)
                .hasMessageContaining("과도한 팔로우 시도로 계정이 일시 정지되었습니다");

            verify(userRepository).suspendUser(userId, "FOLLOW_SPAM", LocalDateTime.now().plusDays(1));
        }

        @Test
        @DisplayName("정상적인 팔로우 시도 - 통과")
        void followRateLimit_Normal_Pass() {
            // Given
            Long userId = 1L;
            
            given(rateLimitUtil.isAllowed(anyString(), anyInt(), anyInt())).willReturn(true);

            // When & Then
            assertThatCode(() -> followSecurityService.checkFollowRateLimit(userId))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("팔로우 패턴 분석 테스트")
    class FollowPatternAnalysisTest {

        @Test
        @DisplayName("순차적 사용자 ID 팔로우 패턴 탐지")
        void detectSequentialFollowPattern() {
            // Given - 순차적 ID 팔로우 (1, 2, 3, 4, 5...)
            Long userId = 1L;
            List<Long> recentFollowTargets = IntStream.range(100, 110)
                    .mapToObj(i -> (long) i)
                    .collect(Collectors.toList());
            
            given(followService.getRecentFollowTargets(userId, 10)).willReturn(recentFollowTargets);

            // When
            boolean isSequentialPattern = followSecurityService.detectSequentialPattern(userId);

            // Then
            assertThat(isSequentialPattern).isTrue();
        }

        @Test
        @DisplayName("신규 계정 대량 팔로우 패턴 탐지")
        void detectNewAccountTargetingPattern() {
            // Given - 최근 가입한 사용자들만 팔로우
            Long userId = 1L;
            List<Long> recentFollowTargets = Arrays.asList(200L, 201L, 202L, 203L, 204L);
            
            given(followService.getRecentFollowTargets(userId, 20)).willReturn(recentFollowTargets);
            given(userRepository.areAllNewAccounts(recentFollowTargets, 7)).willReturn(true);

            // When
            boolean isNewAccountTargeting = followSecurityService.detectNewAccountTargeting(userId);

            // Then
            assertThat(isNewAccountTargeting).isTrue();
        }

        @Test
        @DisplayName("봇 계정 의심 활동 패턴 탐지")
        void detectBotLikeActivity() {
            // Given
            Long userId = 1L;
            UserActivityPattern pattern = UserActivityPattern.builder()
                    .followsPerHour(50) // 시간당 50명 팔로우
                    .unfollowsPerHour(45) // 시간당 45명 언팔로우
                    .likesPerMinute(10) // 분당 10개 좋아요
                    .commentsPerHour(2) // 시간당 2개 댓글 (낮음)
                    .averageSessionDuration(300) // 5분 세션 (짧음)
                    .build();
            
            given(followService.getUserActivityPattern(userId, 24)).willReturn(pattern);

            // When
            boolean isBotLike = followSecurityService.detectBotLikeActivity(userId);

            // Then
            assertThat(isBotLike).isTrue(); // 높은 팔로우/좋아요 + 낮은 댓글/세션
        }

        @Test
        @DisplayName("스팸 패턴 종합 점수 계산")
        void calculateSpamScore() {
            // Given
            Long userId = 1L;
            SpamIndicators indicators = SpamIndicators.builder()
                    .sequentialFollowPattern(true) // +30점
                    .newAccountTargeting(true) // +25점
                    .highFollowUnfollowRatio(true) // +20점
                    .botLikeActivity(true) // +35점
                    .repeatFollowUnfollowSameUser(false) // +0점
                    .build();

            // When
            int spamScore = followSecurityService.calculateSpamScore(indicators);

            // Then
            assertThat(spamScore).isEqualTo(110); // 임계값 80점 초과
            assertThat(spamScore).isGreaterThan(80); // 스팸으로 판정
        }
    }

    @Nested
    @DisplayName("팔로우 제재 시스템 테스트")
    class FollowSanctionTest {

        @Test
        @DisplayName("1차 경고 - 팔로우 기능 1시간 제한")
        void firstWarning_FollowRestriction() {
            // Given
            Long userId = 1L;
            int spamScore = 85; // 80-90점
            
            // When
            followSecurityService.applySanction(userId, spamScore);

            // Then
            verify(userRepository).restrictFollow(userId, LocalDateTime.now().plusHours(1));
            verify(followService).sendWarningNotification(userId, "FOLLOW_SPAM_WARNING_1");
        }

        @Test
        @DisplayName("2차 경고 - 팔로우 기능 24시간 제한")
        void secondWarning_DayRestriction() {
            // Given
            Long userId = 1L;
            int spamScore = 95; // 90-100점
            
            given(userRepository.getWarningCount(userId, "FOLLOW_SPAM")).willReturn(1);

            // When
            followSecurityService.applySanction(userId, spamScore);

            // Then
            verify(userRepository).restrictFollow(userId, LocalDateTime.now().plusDays(1));
            verify(followService).sendWarningNotification(userId, "FOLLOW_SPAM_WARNING_2");
        }

        @Test
        @DisplayName("3차 제재 - 계정 7일 정지")
        void thirdSanction_AccountSuspension() {
            // Given
            Long userId = 1L;
            int spamScore = 110; // 100점 초과
            
            given(userRepository.getWarningCount(userId, "FOLLOW_SPAM")).willReturn(2);

            // When
            followSecurityService.applySanction(userId, spamScore);

            // Then
            verify(userRepository).suspendUser(userId, "FOLLOW_SPAM_FINAL", LocalDateTime.now().plusDays(7));
            verify(followService).sendSuspensionNotification(userId, 7);
        }

        @Test
        @DisplayName("스팸 계정 팔로워 정리 - 의심 팔로우 관계 해제")
        void cleanupSpamFollows() {
            // Given
            Long spamUserId = 1L;
            List<Long> suspiciousFollows = Arrays.asList(100L, 101L, 102L, 103L, 104L);
            
            given(followService.getSuspiciousFollows(spamUserId)).willReturn(suspiciousFollows);

            // When
            followSecurityService.cleanupSpamFollows(spamUserId);

            // Then
            verify(followService).bulkUnfollow(spamUserId, suspiciousFollows);
            verify(followService).notifyAffectedUsers(suspiciousFollows, "SPAM_FOLLOW_REMOVED");
        }
    }

    // ===== 도우미 메소드 =====

    private User createTestUser(Long userId, String nickName) {
        return User.builder()
                .userId(userId)
                .nickName(nickName)
                .email(nickName + "@example.com")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
```

---

## 🔒 프라이버시 보안 테스트

### PrivacySecurityTest.java
```java
package com.routepick.security.test.privacy;

import com.routepick.service.user.UserService;
import com.routepick.service.user.FollowService;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.entity.UserProfile;
import com.routepick.dto.user.response.UserProfileResponseDto;
import com.routepick.exception.user.PrivacyViolationException;
import com.routepick.util.PrivacyMaskingUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("프라이버시 보안 테스트")
class PrivacySecurityTest {

    @Mock
    private UserService userService;

    @Mock
    private FollowService followService;

    @Mock
    private PrivacyMaskingUtil privacyMaskingUtil;

    @InjectMocks
    private PrivacySecurityService privacySecurityService;

    private User privateUser;
    private User publicUser;
    private User viewerUser;

    @BeforeEach
    void setUp() {
        privateUser = createPrivateUser();
        publicUser = createPublicUser();
        viewerUser = createViewerUser();
    }

    @Nested
    @DisplayName("프로필 공개/비공개 설정 테스트")
    class ProfilePrivacyTest {

        @Test
        @DisplayName("비공개 프로필 접근 차단 - 팔로우하지 않은 사용자")
        void blockPrivateProfile_NotFollowing() {
            // Given
            Long privateUserId = 1L;
            Long viewerId = 3L;
            
            given(userService.isProfilePublic(privateUserId)).willReturn(false);
            given(followService.isFollowing(viewerId, privateUserId)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                privacySecurityService.validateProfileAccess(privateUserId, viewerId))
                .isInstanceOf(PrivacyViolationException.class)
                .hasMessageContaining("비공개 프로필입니다");
        }

        @Test
        @DisplayName("비공개 프로필 접근 허용 - 팔로우한 사용자")
        void allowPrivateProfile_Following() {
            // Given
            Long privateUserId = 1L;
            Long viewerId = 3L;
            
            given(userService.isProfilePublic(privateUserId)).willReturn(false);
            given(followService.isFollowing(viewerId, privateUserId)).willReturn(true);

            // When & Then
            assertThatCode(() -> 
                privacySecurityService.validateProfileAccess(privateUserId, viewerId))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("공개 프로필 접근 허용 - 모든 사용자")
        void allowPublicProfile_AnyUser() {
            // Given
            Long publicUserId = 2L;
            Long viewerId = 3L;
            
            given(userService.isProfilePublic(publicUserId)).willReturn(true);

            // When & Then
            assertThatCode(() -> 
                privacySecurityService.validateProfileAccess(publicUserId, viewerId))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("민감 정보 마스킹 - 비팔로워에게")
        void maskSensitiveInfo_NonFollower() {
            // Given
            Long userId = 1L;
            Long viewerId = 3L;
            UserProfileResponseDto profile = createProfileWithSensitiveInfo();
            
            given(followService.isFollowing(viewerId, userId)).willReturn(false);
            given(privacyMaskingUtil.maskPhoneNumber("010-1234-5678")).willReturn("010-****-****");
            given(privacyMaskingUtil.maskEmail("user@example.com")).willReturn("u***@***.com");

            // When
            UserProfileResponseDto maskedProfile = 
                privacySecurityService.applyPrivacyMasking(profile, userId, viewerId);

            // Then
            assertThat(maskedProfile.getPhoneNumber()).isEqualTo("010-****-****");
            assertThat(maskedProfile.getEmail()).isEqualTo("u***@***.com");
            assertThat(maskedProfile.getBio()).isNotNull(); // 공개 정보는 유지
        }

        @Test
        @DisplayName("민감 정보 노출 - 팔로워에게")
        void exposeSensitiveInfo_Follower() {
            // Given
            Long userId = 1L;
            Long viewerId = 3L;
            UserProfileResponseDto profile = createProfileWithSensitiveInfo();
            
            given(followService.isFollowing(viewerId, userId)).willReturn(true);

            // When
            UserProfileResponseDto result = 
                privacySecurityService.applyPrivacyMasking(profile, userId, viewerId);

            // Then
            assertThat(result.getPhoneNumber()).isEqualTo("010-1234-5678"); // 원본 유지
            assertThat(result.getEmail()).isEqualTo("user@example.com"); // 원본 유지
        }
    }

    @Nested
    @DisplayName("활동 내역 프라이버시 테스트")
    class ActivityPrivacyTest {

        @Test
        @DisplayName("비공개 사용자 활동 내역 차단")
        void blockPrivateUserActivity() {
            // Given
            Long privateUserId = 1L;
            Long viewerId = 3L;
            
            given(userService.isProfilePublic(privateUserId)).willReturn(false);
            given(followService.isFollowing(viewerId, privateUserId)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                privacySecurityService.validateActivityAccess(privateUserId, viewerId))
                .isInstanceOf(PrivacyViolationException.class)
                .hasMessageContaining("활동 내역을 볼 수 없습니다");
        }

        @Test
        @DisplayName("팔로우 목록 프라이버시 설정 적용")
        void applyFollowListPrivacy() {
            // Given
            Long userId = 1L;
            Long viewerId = 3L;
            
            given(userService.getFollowListPrivacySetting(userId)).willReturn("FOLLOWERS_ONLY");
            given(followService.isFollowing(viewerId, userId)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                privacySecurityService.validateFollowListAccess(userId, viewerId))
                .isInstanceOf(PrivacyViolationException.class)
                .hasMessageContaining("팔로우 목록이 비공개입니다");
        }

        @Test
        @DisplayName("북마크 목록 완전 비공개")
        void blockBookmarkListAccess() {
            // Given
            Long userId = 1L;
            Long viewerId = 3L;

            // When & Then - 북마크는 본인만 조회 가능
            assertThatThrownBy(() -> 
                privacySecurityService.validateBookmarkAccess(userId, viewerId))
                .isInstanceOf(PrivacyViolationException.class)
                .hasMessageContaining("본인만 조회할 수 있습니다");
        }

        @Test
        @DisplayName("메시지 발송 권한 검증 - 비팔로워 차단")
        void blockMessageFromNonFollower() {
            // Given
            Long senderId = 3L;
            Long receiverId = 1L;
            
            given(userService.getMessagePrivacySetting(receiverId)).willReturn("FOLLOWERS_ONLY");
            given(followService.isFollowing(senderId, receiverId)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                privacySecurityService.validateMessageSendPermission(senderId, receiverId))
                .isInstanceOf(PrivacyViolationException.class)
                .hasMessageContaining("팔로워만 메시지를 보낼 수 있습니다");
        }
    }

    @Nested
    @DisplayName("검색 및 추천 프라이버시 테스트")
    class SearchPrivacyTest {

        @Test
        @DisplayName("검색 결과에서 비공개 사용자 제외")
        void excludePrivateUsersFromSearch() {
            // Given
            String searchKeyword = "클라이머";
            Long searcherId = 3L;
            List<Long> allResults = Arrays.asList(1L, 2L, 3L, 4L); // 비공개/공개 사용자 혼합
            List<Long> publicUserIds = Arrays.asList(2L, 4L); // 공개 사용자만
            
            given(userService.filterPublicUsers(allResults)).willReturn(publicUserIds);

            // When
            List<UserSearchResultDto> results = 
                privacySecurityService.filterSearchResults(allResults, searcherId);

            // Then
            assertThat(results).hasSize(2);
            assertThat(results).extracting(UserSearchResultDto::getUserId)
                    .containsExactlyInAnyOrder(2L, 4L);
        }

        @Test
        @DisplayName("추천 사용자 목록에서 프라이버시 고려")
        void respectPrivacyInRecommendations() {
            // Given
            Long userId = 3L;
            List<Long> recommendations = Arrays.asList(1L, 2L, 5L, 6L);
            
            given(userService.filterPublicUsers(recommendations)).willReturn(Arrays.asList(2L, 6L));
            given(followService.filterNonBlockedUsers(userId, Arrays.asList(2L, 6L)))
                    .willReturn(Arrays.asList(2L, 6L));

            // When
            List<UserRecommendationDto> result = 
                privacySecurityService.getPrivacyRespectingRecommendations(userId);

            // Then
            assertThat(result).hasSize(2);
            verify(userService).filterPublicUsers(recommendations);
            verify(followService).filterNonBlockedUsers(userId, Arrays.asList(2L, 6L));
        }

        @Test
        @DisplayName("위치 기반 검색에서 위치 정보 비공개 사용자 제외")
        void excludeLocationPrivateUsers() {
            // Given
            Double latitude = 37.5665;
            Double longitude = 126.9780;
            Double radius = 5.0; // 5km
            Long searcherId = 3L;
            
            given(userService.hasLocationPrivacy(anyLong())).willReturn(true, false, true, false);

            // When
            List<Long> nearbyUsers = privacySecurityService.findNearbyUsersWithPrivacy(
                    latitude, longitude, radius, searcherId);

            // Then
            verify(userService, atLeast(1)).hasLocationPrivacy(anyLong());
            assertThat(nearbyUsers).allSatisfy(userId -> 
                assertThat(userService.hasLocationPrivacy(userId)).isFalse());
        }
    }

    @Nested
    @DisplayName("블록 및 신고 시스템 테스트")
    class BlockReportTest {

        @Test
        @DisplayName("차단된 사용자 접근 완전 차단")
        void blockAccessFromBlockedUser() {
            // Given
            Long blockedUserId = 3L;
            Long targetUserId = 1L;
            
            given(followService.isBlocked(targetUserId, blockedUserId)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> 
                privacySecurityService.validateNotBlocked(targetUserId, blockedUserId))
                .isInstanceOf(BlockedException.class)
                .hasMessageContaining("차단된 사용자입니다");
        }

        @Test
        @DisplayName("신고당한 사용자 가시성 제한")
        void limitVisibilityOfReportedUser() {
            // Given
            Long reportedUserId = 5L;
            Long viewerId = 3L;
            int reportCount = 10; // 신고 10회
            
            given(userService.getReportCount(reportedUserId)).willReturn(reportCount);

            // When
            boolean isVisible = privacySecurityService.isUserVisibleTo(reportedUserId, viewerId);

            // Then
            assertThat(isVisible).isFalse(); // 신고 5회 이상 시 일반 사용자에게 숨김
        }

        @Test
        @DisplayName("차단 목록 프라이버시 - 본인만 조회 가능")
        void blockListPrivacy() {
            // Given
            Long userId = 1L;
            Long requesterId = 3L;

            // When & Then
            assertThatThrownBy(() -> 
                privacySecurityService.validateBlockListAccess(userId, requesterId))
                .isInstanceOf(PrivacyViolationException.class)
                .hasMessageContaining("차단 목록은 본인만 조회할 수 있습니다");
        }
    }

    // ===== 도우미 메소드 =====

    private User createPrivateUser() {
        return User.builder()
                .userId(1L)
                .nickName("비공개사용자")
                .email("private@example.com")
                .userProfile(UserProfile.builder()
                        .isPublicProfile(false)
                        .build())
                .build();
    }

    private User createPublicUser() {
        return User.builder()
                .userId(2L)
                .nickName("공개사용자")
                .email("public@example.com")
                .userProfile(UserProfile.builder()
                        .isPublicProfile(true)
                        .build())
                .build();
    }

    private User createViewerUser() {
        return User.builder()
                .userId(3L)
                .nickName("조회자")
                .email("viewer@example.com")
                .build();
    }

    private UserProfileResponseDto createProfileWithSensitiveInfo() {
        return UserProfileResponseDto.builder()
                .userId(1L)
                .nickName("사용자")
                .email("user@example.com")
                .phoneNumber("010-1234-5678")
                .bio("클라이밍을 좋아합니다")
                .isPublicProfile(false)
                .build();
    }
}
```

## 📊 테스트 커버리지 요약

### 팔로우 스팸 방지 테스트:
- ✅ Rate Limiting (1분/1시간/24시간 제한)
- ✅ 패턴 분석 (순차적 팔로우, 신규 계정 타겟팅, 봇 활동 탐지)
- ✅ 스팸 점수 계산 및 제재 시스템
- ✅ 의심 팔로우 관계 정리

### 프라이버시 보안 테스트:
- ✅ 공개/비공개 프로필 접근 제어
- ✅ 민감 정보 마스킹 (전화번호, 이메일)
- ✅ 활동 내역 프라이버시 (팔로우 목록, 북마크, 메시지)
- ✅ 검색 및 추천에서 프라이버시 고려
- ✅ 블록/신고 시스템 통합 보안

### 핵심 보안 기능:
- **Rate Limiting**: 분/시간/일 단위 다층 제한
- **패턴 분석**: 봇 탐지 및 스팸 점수 계산
- **단계별 제재**: 경고 → 기능 제한 → 계정 정지
- **프라이버시 보호**: 접근 제어 및 정보 마스킹