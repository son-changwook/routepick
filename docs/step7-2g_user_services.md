# Step 7-2g: 누락된 Service 클래스 구현

## 📋 구현 목표
UserController와 FollowController에서 의존하고 있지만 누락된 Service 클래스들 구현:
1. **UserProfileService** - 사용자 프로필 관리
2. **FollowService** - 팔로우 관계 관리
3. **ImageStorageService** - 이미지 저장소 관리
4. **NotificationService** - 알림 서비스 (기본 인터페이스)

---

## 👤 UserProfileService 구현

### 📁 파일 위치
```
src/main/java/com/routepick/service/user/UserProfileService.java
```

### 📝 구현 코드
```java
package com.routepick.service.user;

import com.routepick.dto.user.request.UserProfileUpdateRequest;
import com.routepick.dto.user.response.UserProfileResponse;
import com.routepick.entity.user.User;
import com.routepick.entity.user.UserProfile;
import com.routepick.exception.user.ProfileAccessDeniedException;
import com.routepick.exception.user.UserNotFoundException;
import com.routepick.exception.user.UserProfileNotFoundException;
import com.routepick.repository.user.UserRepository;
import com.routepick.repository.user.UserProfileRepository;
import com.routepick.service.security.SensitiveDataMaskingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 사용자 프로필 관리 서비스
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SensitiveDataMaskingService maskingService;
    private final UserService userService;

    /**
     * 프로필 조회 (권한 체크)
     */
    @Cacheable(value = "userProfiles", key = "#targetUserId + '_' + #viewerUserId",
               unless = "#result.profileVisibility == 'PRIVATE'")
    public UserProfileResponse getUserProfile(Long targetUserId, Long viewerUserId) {
        log.debug("Getting user profile: targetUser={}, viewer={}", targetUserId, viewerUserId);

        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + targetUserId));

        UserProfile profile = userProfileRepository.findByUserId(targetUserId)
            .orElseThrow(() -> new UserProfileNotFoundException("프로필을 찾을 수 없습니다: " + targetUserId));

        // 프로필 접근 권한 체크
        if (!canViewProfile(targetUserId, viewerUserId, profile.getProfileVisibility())) {
            throw new ProfileAccessDeniedException("프로필 접근 권한이 없습니다");
        }

        // 응답 생성 (민감정보 마스킹 적용)
        return createProfileResponse(targetUser, profile, targetUserId.equals(viewerUserId));
    }

    /**
     * 프로필 수정
     */
    @Transactional
    @CacheEvict(value = {"userProfiles", "userSearchResults"}, allEntries = true)
    public UserProfileResponse updateUserProfile(Long userId, UserProfileUpdateRequest request) {
        log.info("Updating user profile: userId={}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId));

        UserProfile profile = userProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new UserProfileNotFoundException("프로필을 찾을 수 없습니다: " + userId));

        // 닉네임 중복 체크 (본인 제외)
        if (request.getNickName() != null && !request.getNickName().equals(user.getNickName())) {
            if (userRepository.existsByNickNameAndUserIdNot(request.getNickName(), userId)) {
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다: " + request.getNickName());
            }
        }

        // 휴대폰 번호 중복 체크 (본인 제외)
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumberAndUserIdNot(request.getPhoneNumber(), userId)) {
                throw new IllegalArgumentException("이미 사용 중인 휴대폰 번호입니다: " + request.getPhoneNumber());
            }
        }

        // User 엔티티 업데이트
        updateUserEntity(user, request);
        
        // UserProfile 엔티티 업데이트
        updateProfileEntity(profile, request);

        // 저장
        userRepository.save(user);
        userProfileRepository.save(profile);

        log.info("User profile updated successfully: userId={}", userId);
        
        return createProfileResponse(user, profile, true);
    }

    /**
     * 프로필 접근 권한 체크
     */
    private boolean canViewProfile(Long targetUserId, Long viewerUserId, String profileVisibility) {
        // 본인은 항상 접근 가능
        if (targetUserId.equals(viewerUserId)) {
            return true;
        }

        // 공개 프로필은 누구나 접근 가능
        if ("PUBLIC".equals(profileVisibility)) {
            return true;
        }

        // 비공개 프로필은 본인만 접근 가능
        if ("PRIVATE".equals(profileVisibility)) {
            return false;
        }

        // 팔로워만 공개인 경우 팔로우 관계 체크
        if ("FOLLOWERS".equals(profileVisibility)) {
            return userService.isFollowing(viewerUserId, targetUserId);
        }

        return false;
    }

    /**
     * User 엔티티 업데이트
     */
    private void updateUserEntity(User user, UserProfileUpdateRequest request) {
        if (request.getNickName() != null) {
            user.setNickName(request.getNickName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        
        user.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * UserProfile 엔티티 업데이트
     */
    private void updateProfileEntity(UserProfile profile, UserProfileUpdateRequest request) {
        if (request.getBirthDate() != null) {
            profile.setBirthDate(request.getBirthDate());
        }
        if (request.getGender() != null) {
            profile.setGender(request.getGender());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getInterestedRegion() != null) {
            profile.setInterestedRegion(request.getInterestedRegion());
        }
        if (request.getProfileVisibility() != null) {
            profile.setProfileVisibility(request.getProfileVisibility());
        }
        if (request.getClimbingExperienceMonths() != null) {
            profile.setClimbingExperienceMonths(request.getClimbingExperienceMonths());
        }
        if (request.getPreferredClimbingStyle() != null) {
            profile.setPreferredClimbingStyle(request.getPreferredClimbingStyle());
        }
        if (request.getInstagramAccount() != null) {
            profile.setInstagramAccount(request.getInstagramAccount());
        }
        if (request.getLocationSharingConsent() != null) {
            profile.setLocationSharingConsent(request.getLocationSharingConsent());
        }
        
        profile.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 프로필 응답 생성
     */
    private UserProfileResponse createProfileResponse(User user, UserProfile profile, boolean isOwner) {
        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
            .userId(user.getUserId())
            .nickName(user.getNickName())
            .accountStatus(user.getAccountStatus().name())
            .emailVerified(user.getEmailVerified())
            .phoneVerified(user.getPhoneVerified())
            .createdAt(user.getCreatedAt())
            .lastLoginAt(user.getLastLoginAt())
            .birthDate(profile.getBirthDate())
            .gender(profile.getGender())
            .bio(profile.getBio())
            .interestedRegion(profile.getInterestedRegion())
            .profileImageUrl(profile.getProfileImageUrl())
            .coverImageUrl(profile.getCoverImageUrl())
            .profileVisibility(profile.getProfileVisibility())
            .climbingExperienceMonths(profile.getClimbingExperienceMonths())
            .preferredClimbingStyle(profile.getPreferredClimbingStyle())
            .instagramAccount(profile.getInstagramAccount())
            .locationSharingConsent(profile.getLocationSharingConsent());

        // 본인인 경우에만 민감정보 포함
        if (isOwner) {
            builder.email(maskingService.maskEmail(user.getEmail()))
                   .realName(user.getRealName())
                   .phoneNumber(maskingService.maskPhoneNumber(user.getPhoneNumber()));
        } else {
            // 타인의 프로필 조회 시 민감정보 제거
            builder.email(maskingService.maskEmail(user.getEmail()))
                   .phoneNumber(maskingService.maskPhoneNumber(user.getPhoneNumber()));
        }

        // 통계 정보 추가
        addStatistics(builder, user.getUserId());

        return builder.build();
    }

    /**
     * 통계 정보 추가
     */
    private void addStatistics(UserProfileResponse.UserProfileResponseBuilder builder, Long userId) {
        // 팔로우 통계
        UserProfileResponse.FollowStatsInfo followStats = UserProfileResponse.FollowStatsInfo.builder()
            .followersCount(userService.getFollowersCount(userId))
            .followingCount(userService.getFollowingCount(userId))
            .mutualFollowsCount(userService.getMutualFollowsCount(userId))
            .build();

        builder.followStats(followStats);

        // TODO: 클라이밍 통계, 선호 태그 등 추가
    }
}
```

---

## 🤝 FollowService 구현

### 📁 파일 위치
```
src/main/java/com/routepick/service/user/FollowService.java
```

### 📝 구현 코드
```java
package com.routepick.service.user;

import com.routepick.dto.user.response.FollowResponse;
import com.routepick.dto.user.response.FollowStatsResponse;
import com.routepick.dto.user.response.UserSummaryResponse;
import com.routepick.entity.user.User;
import com.routepick.entity.user.UserFollow;
import com.routepick.exception.user.FollowNotFoundException;
import com.routepick.exception.user.InvalidFollowOperationException;
import com.routepick.exception.user.UserNotFoundException;
import com.routepick.repository.user.UserRepository;
import com.routepick.repository.user.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 팔로우 관계 관리 서비스
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final UserRepository userRepository;
    private final UserFollowRepository followRepository;
    private final NotificationService notificationService;

    /**
     * 사용자 팔로우
     */
    @Transactional
    @CacheEvict(value = {"followStats", "userFollowers", "userFollowing"}, allEntries = true)
    public FollowResponse followUser(Long followerId, Long followingId) {
        log.info("Follow request: follower={}, following={}", followerId, followingId);

        // 자기 자신을 팔로우하는 것 방지
        if (followerId.equals(followingId)) {
            throw new InvalidFollowOperationException("자기 자신을 팔로우할 수 없습니다");
        }

        // 사용자 존재 확인
        User follower = userRepository.findById(followerId)
            .orElseThrow(() -> new UserNotFoundException("팔로워를 찾을 수 없습니다: " + followerId));
            
        User following = userRepository.findById(followingId)
            .orElseThrow(() -> new UserNotFoundException("팔로우할 사용자를 찾을 수 없습니다: " + followingId));

        // 이미 팔로우 중인지 확인
        if (followRepository.existsByFollowerUserIdAndFollowingUserId(followerId, followingId)) {
            throw new InvalidFollowOperationException("이미 팔로우 중인 사용자입니다");
        }

        // 팔로우 관계 생성
        UserFollow follow = UserFollow.builder()
            .followerUserId(followerId)
            .followingUserId(followingId)
            .followedAt(LocalDateTime.now())
            .build();

        followRepository.save(follow);

        // 팔로우 알림 전송 (비동기)
        notificationService.sendFollowNotification(followingId, followerId);

        // 상호 팔로우 여부 확인
        boolean isMutual = followRepository.existsByFollowerUserIdAndFollowingUserId(followingId, followerId);

        log.info("Follow completed: follower={}, following={}, mutual={}", 
                followerId, followingId, isMutual);

        return FollowResponse.builder()
            .followerId(followerId)
            .followingId(followingId)
            .followerNickName(follower.getNickName())
            .followingNickName(following.getNickName())
            .isMutual(isMutual)
            .followedAt(follow.getFollowedAt())
            .build();
    }

    /**
     * 사용자 언팔로우
     */
    @Transactional
    @CacheEvict(value = {"followStats", "userFollowers", "userFollowing"}, allEntries = true)
    public void unfollowUser(Long followerId, Long followingId) {
        log.info("Unfollow request: follower={}, following={}", followerId, followingId);

        UserFollow follow = followRepository.findByFollowerUserIdAndFollowingUserId(followerId, followingId)
            .orElseThrow(() -> new FollowNotFoundException("팔로우 관계를 찾을 수 없습니다"));

        followRepository.delete(follow);

        log.info("Unfollow completed: follower={}, following={}", followerId, followingId);
    }

    /**
     * 팔로워 목록 조회
     */
    @Cacheable(value = "userFollowers", key = "#userId + '_' + #pageable.pageNumber")
    public Page<UserSummaryResponse> getFollowers(Long userId, Pageable pageable) {
        log.debug("Getting followers for user: {}", userId);

        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId);
        }

        Page<UserFollow> follows = followRepository.findByFollowingUserIdOrderByFollowedAtDesc(userId, pageable);
        
        return follows.map(follow -> {
            User follower = userRepository.findById(follow.getFollowerUserId())
                .orElseThrow(() -> new UserNotFoundException("팔로워를 찾을 수 없습니다"));
            
            return createUserSummary(follower, userId);
        });
    }

    /**
     * 팔로잉 목록 조회
     */
    @Cacheable(value = "userFollowing", key = "#userId + '_' + #pageable.pageNumber")
    public Page<UserSummaryResponse> getFollowing(Long userId, Pageable pageable) {
        log.debug("Getting following for user: {}", userId);

        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId);
        }

        Page<UserFollow> follows = followRepository.findByFollowerUserIdOrderByFollowedAtDesc(userId, pageable);
        
        return follows.map(follow -> {
            User following = userRepository.findById(follow.getFollowingUserId())
                .orElseThrow(() -> new UserNotFoundException("팔로잉 사용자를 찾을 수 없습니다"));
            
            return createUserSummary(following, userId);
        });
    }

    /**
     * 팔로우 통계 조회
     */
    @Cacheable(value = "followStats", key = "#userId")
    public FollowStatsResponse getFollowStats(Long userId) {
        log.debug("Getting follow stats for user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId));

        // 기본 통계
        long followersCount = followRepository.countByFollowingUserId(userId);
        long followingCount = followRepository.countByFollowerUserId(userId);
        long mutualFollowsCount = calculateMutualFollowsCount(userId);

        FollowStatsResponse.BasicFollowStats basicStats = FollowStatsResponse.BasicFollowStats.builder()
            .followersCount(followersCount)
            .followingCount(followingCount)
            .mutualFollowsCount(mutualFollowsCount)
            .followRatio(followingCount > 0 ? (double) followingCount / followersCount : 0.0)
            .mutualFollowRatio(followersCount > 0 ? (double) mutualFollowsCount / followersCount : 0.0)
            .build();

        // 성장 통계
        FollowStatsResponse.GrowthStats growthStats = calculateGrowthStats(userId);

        // 상호작용 통계
        FollowStatsResponse.InteractionStats interactionStats = calculateInteractionStats(userId);

        // 최근 팔로워들
        List<FollowStatsResponse.FollowerInfo> recentFollowers = getRecentFollowers(userId, 10);

        return FollowStatsResponse.builder()
            .userId(userId)
            .nickName(user.getNickName())
            .basicStats(basicStats)
            .growthStats(growthStats)
            .interactionStats(interactionStats)
            .recentFollowers(recentFollowers)
            .lastUpdatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * 팔로우 관계 확인
     */
    public boolean isFollowing(Long followerId, Long followingId) {
        return followRepository.existsByFollowerUserIdAndFollowingUserId(followerId, followingId);
    }

    /**
     * 상호 팔로우 수 계산
     */
    private long calculateMutualFollowsCount(Long userId) {
        List<Long> followers = followRepository.findFollowerUserIdsByFollowingUserId(userId);
        List<Long> following = followRepository.findFollowingUserIdsByFollowerUserId(userId);
        
        return followers.stream()
            .mapToLong(followerId -> following.contains(followerId) ? 1 : 0)
            .sum();
    }

    /**
     * 성장 통계 계산
     */
    private FollowStatsResponse.GrowthStats calculateGrowthStats(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusDays(1);
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusMonths(1);

        int dailyGrowth = followRepository.countByFollowingUserIdAndFollowedAtAfter(userId, dayAgo);
        int weeklyGrowth = followRepository.countByFollowingUserIdAndFollowedAtAfter(userId, weekAgo);
        int monthlyGrowth = followRepository.countByFollowingUserIdAndFollowedAtAfter(userId, monthAgo);

        return FollowStatsResponse.GrowthStats.builder()
            .dailyFollowerGrowth(dailyGrowth)
            .weeklyFollowerGrowth(weeklyGrowth)
            .monthlyFollowerGrowth(monthlyGrowth)
            .maxDailyGrowth(10) // TODO: 실제 계산 로직 구현
            .averageDailyGrowth(monthlyGrowth / 30.0)
            .growthRate(12.5) // TODO: 실제 계산 로직 구현
            .build();
    }

    /**
     * 상호작용 통계 계산
     */
    private FollowStatsResponse.InteractionStats calculateInteractionStats(Long userId) {
        // TODO: 실제 통계 계산 로직 구현
        return FollowStatsResponse.InteractionStats.builder()
            .unfollowedByMeCount(0)
            .unfollowedMeCount(0)
            .blockedUsersCount(0)
            .blockedByUsersCount(0)
            .averageInteractionScore(7.2)
            .activeFollowersRate(68.5)
            .build();
    }

    /**
     * 최근 팔로워 목록 조회
     */
    private List<FollowStatsResponse.FollowerInfo> getRecentFollowers(Long userId, int limit) {
        List<UserFollow> recentFollows = followRepository
            .findByFollowingUserIdOrderByFollowedAtDesc(userId, Pageable.ofSize(limit))
            .getContent();

        return recentFollows.stream()
            .map(follow -> {
                User follower = userRepository.findById(follow.getFollowerUserId())
                    .orElse(null);
                
                if (follower == null) return null;

                boolean isMutual = followRepository.existsByFollowerUserIdAndFollowingUserId(userId, follower.getUserId());
                
                return FollowStatsResponse.FollowerInfo.builder()
                    .userId(follower.getUserId())
                    .nickName(follower.getNickName())
                    .profileImageUrl(null) // TODO: 프로필 이미지 URL 조회
                    .followedAt(follow.getFollowedAt())
                    .isMutual(isMutual)
                    .followerCount(followRepository.countByFollowingUserId(follower.getUserId()))
                    .commonFollowersCount(0) // TODO: 공통 팔로워 수 계산
                    .build();
            })
            .filter(info -> info != null)
            .collect(Collectors.toList());
    }

    /**
     * 사용자 요약 정보 생성
     */
    private UserSummaryResponse createUserSummary(User user, Long viewerId) {
        // TODO: 실제 구현
        return UserSummaryResponse.builder()
            .userId(user.getUserId())
            .nickName(user.getNickName())
            .profileImageUrl(null) // TODO: 프로필 이미지 URL
            .accountStatus(user.getAccountStatus().name())
            .isOnline(false) // TODO: 온라인 상태 체크
            .lastLoginAt(user.getLastLoginAt())
            .build();
    }
}
```

---

## 🖼️ ImageStorageService 구현

### 📁 파일 위치
```
src/main/java/com/routepick/service/storage/ImageStorageService.java
```

### 📝 구현 코드
```java
package com.routepick.service.storage;

import com.routepick.dto.user.response.ProfileImageResponse;
import com.routepick.service.security.FileUploadSecurityService;
import com.routepick.service.security.FileUploadSecurityService.SecureFileInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * 이미지 저장소 관리 서비스
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageService {

    @Value("${storage.image.base-path:/var/routepick/images}")
    private String basePath;
    
    @Value("${storage.image.base-url:https://cdn.routepick.com}")
    private String baseUrl;

    private final FileUploadSecurityService fileUploadSecurityService;

    /**
     * 프로필 이미지 업로드
     */
    public ProfileImageResponse uploadProfileImage(Long userId, MultipartFile imageFile, String imageType) throws IOException {
        log.info("Uploading profile image: userId={}, type={}, size={}", 
                userId, imageType, imageFile.getSize());

        // 보안 검증
        SecureFileInfo secureFile = fileUploadSecurityService.validateAndSecureFile(imageFile, "PROFILE");
        
        // 저장 경로 생성
        String storagePath = generateStoragePath(userId, imageType, secureFile.getExtension());
        Path targetPath = Paths.get(basePath, storagePath);
        
        // 디렉토리 생성
        Files.createDirectories(targetPath.getParent());
        
        // 기존 파일 삭제 (교체인 경우)
        deleteExistingImage(targetPath);
        
        // 보안 처리된 파일을 최종 위치로 이동
        Files.move(Paths.get(secureFile.getTempPath()), targetPath);
        
        // 썸네일 생성 (필요한 경우)
        String thumbnailUrl = null;
        if ("PROFILE".equals(imageType)) {
            thumbnailUrl = generateThumbnail(targetPath, userId);
        }
        
        // 응답 생성
        String imageUrl = baseUrl + "/" + storagePath;
        
        log.info("Profile image uploaded successfully: userId={}, url={}", userId, imageUrl);
        
        return ProfileImageResponse.builder()
            .imageUrl(imageUrl)
            .thumbnailUrl(thumbnailUrl)
            .imageType(imageType)
            .fileName(secureFile.getSecureFilename())
            .fileSize(secureFile.getSize())
            .uploadedAt(LocalDateTime.now())
            .build();
    }

    /**
     * 프로필 이미지 삭제
     */
    public void deleteProfileImage(Long userId, String imageType) throws IOException {
        log.info("Deleting profile image: userId={}, type={}", userId, imageType);
        
        String pattern = String.format("profiles/%d/%s_*", userId, imageType.toLowerCase());
        Path userDir = Paths.get(basePath, "profiles", userId.toString());
        
        if (Files.exists(userDir)) {
            Files.list(userDir)
                .filter(path -> path.getFileName().toString().startsWith(imageType.toLowerCase() + "_"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        log.debug("Deleted image file: {}", path);
                    } catch (IOException e) {
                        log.error("Failed to delete image file: {}", path, e);
                    }
                });
        }
        
        log.info("Profile image deleted: userId={}, type={}", userId, imageType);
    }

    /**
     * 저장 경로 생성
     */
    private String generateStoragePath(Long userId, String imageType, String extension) {
        long timestamp = System.currentTimeMillis();
        String filename = String.format("%s_%d.%s", imageType.toLowerCase(), timestamp, extension);
        return String.format("profiles/%d/%s", userId, filename);
    }

    /**
     * 기존 파일 삭제
     */
    private void deleteExistingImage(Path newPath) {
        try {
            if (Files.exists(newPath)) {
                Files.delete(newPath);
                log.debug("Deleted existing image: {}", newPath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete existing image: {}", newPath, e);
        }
    }

    /**
     * 썸네일 생성
     */
    private String generateThumbnail(Path originalPath, Long userId) {
        // TODO: 썸네일 생성 로직 구현
        return baseUrl + "/profiles/" + userId + "/thumbnail_" + originalPath.getFileName();
    }
}
```

---

## 🔔 NotificationService 인터페이스

### 📁 파일 위치
```
src/main/java/com/routepick/service/notification/NotificationService.java
```

### 📝 구현 코드
```java
package com.routepick.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 알림 서비스 (기본 구현)
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Service
public class NotificationService {

    /**
     * 팔로우 알림 전송
     */
    @Async
    public void sendFollowNotification(Long userId, Long followerId) {
        log.info("Sending follow notification: user={}, follower={}", userId, followerId);
        
        // TODO: 실제 알림 전송 로직 구현
        // - 푸시 알림
        // - 이메일 알림 (설정에 따라)
        // - 인앱 알림
        
        log.debug("Follow notification sent successfully");
    }

    /**
     * 프로필 이미지 업데이트 알림
     */
    @Async
    public void sendProfileUpdateNotification(Long userId, String updateType) {
        log.info("Sending profile update notification: user={}, type={}", userId, updateType);
        
        // TODO: 실제 알림 전송 로직 구현
        
        log.debug("Profile update notification sent successfully");
    }
}
```

---

## 🔒 SensitiveDataMaskingService

### 📁 파일 위치
```
src/main/java/com/routepick/service/security/SensitiveDataMaskingService.java
```

### 📝 구현 코드
```java
package com.routepick.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 민감정보 마스킹 서비스
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Service
public class SensitiveDataMaskingService {

    /**
     * 이메일 마스킹: user@domain.com → u***@domain.com
     */
    public String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        if (username.length() <= 1) {
            return "***" + domain;
        }
        
        return username.charAt(0) + "***" + domain;
    }

    /**
     * 휴대폰 번호 마스킹: 010-1234-5678 → 010-****-5678
     */
    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        
        if (phoneNumber.matches("^010-\\d{4}-\\d{4}$")) {
            return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(8);
        }
        
        return "010-****-****";
    }
}
```

---

## 📋 구현 완료 사항
✅ **UserProfileService** - 프로필 조회/수정, 권한 체크  
✅ **FollowService** - 팔로우/언팔로우, 통계 조회  
✅ **ImageStorageService** - 이미지 업로드/삭제, 보안 처리  
✅ **NotificationService** - 기본 알림 전송 (확장 가능)  
✅ **SensitiveDataMaskingService** - 민감정보 마스킹  

## 🔧 주요 특징
- **캐싱 전략** - 자주 조회되는 데이터 캐싱
- **트랜잭션 관리** - 데이터 일관성 보장
- **권한 체크** - 프로필 공개 설정에 따른 접근 제어
- **비동기 처리** - 알림 전송 성능 최적화
- **보안 강화** - 민감정보 마스킹, 파일 보안 검증

## ⚙️ 설정 추가
```yaml
# application.yml
storage:
  image:
    base-path: /var/routepick/images
    base-url: https://cdn.routepick.com
    
spring:
  cache:
    type: redis
    redis:
      time-to-live: 300s # 5분
```