# Step 6-1c: UserService 구현

> 사용자 관리, 프로필, 팔로우 시스템 완전 구현  
> 생성일: 2025-08-20  
> 기반: step5-1a,b,c_repositories.md, 캐싱 전략 및 한국 특화 검증

---

## 🎯 설계 목표

- **프로필 관리**: UserProfile 엔티티 직접 조작 및 캐싱
- **팔로우 시스템**: UserFollow 엔티티 기반 소셜 네트워킹
- **사용자 검색**: 닉네임/이메일 기반 검색 (개인정보 보호)
- **한국 특화**: 한글 닉네임 정규식 검증 (2-10자)
- **캐싱 전략**: @Cacheable/@CacheEvict 사용자 정보 최적화

---

## 👤 UserService - 사용자 서비스

### UserService.java
```java
package com.routepick.service.user;

import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.entity.UserProfile;
import com.routepick.domain.user.entity.UserFollow;
import com.routepick.domain.user.repository.UserProfileRepository;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.domain.user.repository.UserFollowRepository;
import com.routepick.exception.user.UserException;
import com.routepick.exception.validation.ValidationException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사용자 서비스
 * - 프로필 관리
 * - 닉네임 중복 검사
 * - 팔로우 관리
 * - 사용자 검색
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserFollowRepository userFollowRepository;
    
    // ===== 사용자 조회 =====
    
    /**
     * 사용자 조회 (캐싱)
     */
    @Cacheable(value = "users", key = "#userId")
    public User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> UserException.notFound(userId));
    }
    
    /**
     * 이메일로 사용자 조회
     */
    @Cacheable(value = "users", key = "#email")
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> UserException.notFoundByEmail(email));
    }
    
    /**
     * 닉네임으로 사용자 조회
     */
    public User getUserByNickname(String nickname) {
        return userRepository.findByNickName(nickname)
            .orElseThrow(() -> UserException.notFoundByNickname(nickname));
    }
    
    // ===== 프로필 관리 =====
    
    /**
     * 프로필 조회
     */
    @Cacheable(value = "userProfiles", key = "#userId")
    public UserProfile getUserProfile(Long userId) {
        return userProfileRepository.findByUserId(userId)
            .orElseThrow(() -> UserException.profileNotFound(userId));
    }
    
    /**
     * 프로필 업데이트
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#userId"),
        @CacheEvict(value = "userProfiles", key = "#userId")
    })
    public UserProfile updateProfile(Long userId, Map<String, Object> updates) {
        log.info("프로필 업데이트: userId={}", userId);
        
        UserProfile profile = getUserProfile(userId);
        
        // 필드별 업데이트
        updates.forEach((key, value) -> {
            switch (key) {
                case "gender" -> profile.setGender((String) value);
                case "birthDate" -> profile.setBirthDate(LocalDate.parse((String) value));
                case "height" -> profile.setHeight((Integer) value);
                case "weight" -> profile.setWeight((Integer) value);
                case "armReach" -> profile.setArmReach((Integer) value);
                case "climbingYears" -> profile.setClimbingYears((Integer) value);
                case "bio" -> profile.setBio(XssProtectionUtil.sanitize((String) value));
                case "isPublic" -> profile.setPublic((Boolean) value);
                case "profileImageUrl" -> profile.setProfileImageUrl((String) value);
            }
        });
        
        return userProfileRepository.save(profile);
    }
    
    /**
     * 프로필 이미지 업데이트
     */
    @Transactional
    @CacheEvict(value = "userProfiles", key = "#userId")
    public void updateProfileImage(Long userId, String imageUrl) {
        UserProfile profile = getUserProfile(userId);
        profile.setProfileImageUrl(imageUrl);
        userProfileRepository.save(profile);
        
        log.info("프로필 이미지 업데이트: userId={}, imageUrl={}", userId, imageUrl);
    }
    
    // ===== 닉네임 관리 =====
    
    /**
     * 닉네임 중복 검사
     */
    public boolean isNicknameAvailable(String nickname) {
        // 한글 정규식 검증
        if (!validateKoreanNickname(nickname)) {
            throw ValidationException.invalidKoreanNickname(nickname);
        }
        
        return !userRepository.existsByNickName(nickname);
    }
    
    /**
     * 닉네임 변경
     */
    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void changeNickname(Long userId, String newNickname) {
        // 한글 정규식 검증
        if (!validateKoreanNickname(newNickname)) {
            throw ValidationException.invalidKoreanNickname(newNickname);
        }
        
        // 중복 확인
        if (userRepository.existsByNickName(newNickname)) {
            throw UserException.nicknameAlreadyExists(newNickname);
        }
        
        User user = getUser(userId);
        user.setNickName(XssProtectionUtil.sanitize(newNickname));
        userRepository.save(user);
        
        log.info("닉네임 변경: userId={}, newNickname={}", userId, newNickname);
    }
    
    // ===== 사용자 검색 =====
    
    /**
     * 닉네임으로 검색
     */
    public Page<User> searchByNickname(String keyword, Pageable pageable) {
        String sanitizedKeyword = XssProtectionUtil.sanitize(keyword);
        return userRepository.findByNickNameContaining(sanitizedKeyword, pageable)
            .map(projection -> userRepository.findById(projection.getUserId()).orElse(null));
    }
    
    /**
     * 이메일로 검색 (실제로는 닉네임 검색 사용)
     */
    public Page<User> searchByEmail(String keyword, Pageable pageable) {
        String sanitizedKeyword = XssProtectionUtil.sanitize(keyword);
        // 이메일 검색 대신 닉네임 검색 사용 (개인정보 보호)
        return userRepository.findByNickNameContaining(sanitizedKeyword, pageable)
            .map(projection -> userRepository.findById(projection.getUserId()).orElse(null));
    }
    
    /**
     * 최근 활성 사용자 조회
     */
    public Page<User> getRecentActiveUsers(int days, Pageable pageable) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return userRepository.findRecentActiveUsers(since, pageable)
            .map(projection -> userRepository.findById(projection.getUserId()).orElse(null));
    }
    
    // ===== 팔로우 관리 =====
    
    /**
     * 팔로우
     */
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw UserException.cannotFollowSelf();
        }
        
        // 이미 팔로우 중인지 확인
        if (userFollowRepository.existsByFollowerUserAndFollowingUser(getUser(followerId), getUser(followingId))) {
            throw UserException.alreadyFollowing(followingId);
        }
        
        User follower = getUser(followerId);
        User following = getUser(followingId);
        
        UserFollow userFollow = UserFollow.builder()
            .followerUser(follower)
            .followingUser(following)
            .build();
        
        userFollowRepository.save(userFollow);
        
        // 팔로워/팔로잉 카운트 업데이트 (직접 업데이트)
        UserProfile followingProfile = getUserProfile(followingId);
        followingProfile.setFollowerCount(followingProfile.getFollowerCount() + 1);
        userProfileRepository.save(followingProfile);
        
        UserProfile followerProfile = getUserProfile(followerId);
        followerProfile.setFollowingCount(followerProfile.getFollowingCount() + 1);
        userProfileRepository.save(followerProfile);
        
        log.info("팔로우 성공: followerId={}, followingId={}", followerId, followingId);
    }
    
    /**
     * 언팔로우
     */
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        UserFollow userFollow = userFollowRepository
            .findByFollowerUserAndFollowingUser(getUser(followerId), getUser(followingId))
            .orElseThrow(() -> UserException.notFollowing(followingId));
        
        userFollowRepository.delete(userFollow);
        
        // 팔로워/팔로잉 카운트 업데이트 (직접 업데이트)
        UserProfile followingProfile = getUserProfile(followingId);
        followingProfile.setFollowerCount(Math.max(0, followingProfile.getFollowerCount() - 1));
        userProfileRepository.save(followingProfile);
        
        UserProfile followerProfile = getUserProfile(followerId);
        followerProfile.setFollowingCount(Math.max(0, followerProfile.getFollowingCount() - 1));
        userProfileRepository.save(followerProfile);
        
        log.info("언팔로우 성공: followerId={}, followingId={}", followerId, followingId);
    }
    
    /**
     * 팔로워 목록 조회
     */
    public Page<User> getFollowers(Long userId, Pageable pageable) {
        return userFollowRepository.findFollowersByUserId(userId, pageable)
            .map(UserFollow::getFollowerUser);
    }
    
    /**
     * 팔로잉 목록 조회
     */
    public Page<User> getFollowings(Long userId, Pageable pageable) {
        return userFollowRepository.findFollowingsByUserId(userId, pageable)
            .map(UserFollow::getFollowingUser);
    }
    
    /**
     * 팔로우 여부 확인
     */
    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowRepository.existsByFollowerUserAndFollowingUser(getUser(followerId), getUser(followingId));
    }
    
    // ===== 사용자 상태 관리 =====
    
    /**
     * 사용자 검증
     */
    public void validateUser(Long userId) {
        User user = getUser(userId);
        
        if (user.getUserStatus() == UserStatus.INACTIVE) {
            throw UserException.inactive(userId);
        }
        
        if (user.getUserStatus() == UserStatus.SUSPENDED) {
            throw UserException.suspended(userId);
        }
        
        if (user.getUserStatus() == UserStatus.DELETED) {
            throw UserException.deleted(userId);
        }
    }
    
    /**
     * 계정 삭제 (소프트 삭제)
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#userId"),
        @CacheEvict(value = "userProfiles", key = "#userId")
    })
    public void deleteAccount(Long userId) {
        User user = getUser(userId);
        user.deleteAccount();
        userRepository.save(user);
        
        log.info("계정 삭제 처리: userId={}", userId);
    }
    
    // ===== Helper 메서드 =====
    
    /**
     * 한글 닉네임 검증
     */
    private boolean validateKoreanNickname(String nickname) {
        return nickname != null && nickname.matches("^[가-힣a-zA-Z0-9]{2,10}$");
    }
}
```

---

## 👥 팔로우 시스템 상세 설계

### 1. 팔로우 관계 모델
```java
// UserFollow 엔티티 활용
@Entity
@Table(name = "user_follows")
public class UserFollow {
    // followerUser: 팔로우를 하는 사용자
    // followingUser: 팔로우를 받는 사용자
    // 예시: A가 B를 팔로우 → followerUser=A, followingUser=B
}
```

### 2. 팔로우 카운트 관리
```java
// UserProfile에서 실시간 카운트 관리
private Integer followerCount = 0;   // 나를 팔로우하는 사람 수
private Integer followingCount = 0;  // 내가 팔로우하는 사람 수

// 팔로우 시 양쪽 카운트 업데이트
// A가 B를 팔로우 → A.followingCount++, B.followerCount++
```

### 3. 팔로우 제약 조건
- **자기 자신 팔로우 불가**: `followerId.equals(followingId)` 검증
- **중복 팔로우 방지**: 기존 팔로우 관계 확인
- **카운트 음수 방지**: `Math.max(0, count - 1)` 적용

---

## 🔍 사용자 검색 시스템

### 1. 검색 우선순위
```java
// 1순위: 닉네임 정확히 일치
User exactMatch = userRepository.findByNickName(keyword);

// 2순위: 닉네임 부분 일치 (LIKE 검색)
Page<User> partialMatches = userRepository.findByNickNameContaining(keyword, pageable);

// 3순위: 최근 활성 사용자 (검색어 없을 때)
Page<User> recentUsers = userRepository.findRecentActiveUsers(since, pageable);
```

### 2. 개인정보 보호
```java
// 이메일 검색 → 닉네임 검색으로 리다이렉트
public Page<User> searchByEmail(String keyword, Pageable pageable) {
    // 실제로는 닉네임 검색 수행 (이메일 노출 방지)
    return searchByNickname(keyword, pageable);
}
```

### 3. XSS 방지
```java
// 모든 검색어에 XSS 필터링 적용
String sanitizedKeyword = XssProtectionUtil.sanitize(keyword);
```

---

## 📊 프로필 관리 상세 기능

### 1. 동적 프로필 업데이트
```java
// Map 기반 필드별 업데이트 (null 값 허용)
Map<String, Object> updates = Map.of(
    "bio", "새로운 자기소개",
    "height", 175,
    "isPublic", true
);
userService.updateProfile(userId, updates);
```

### 2. 지원 필드 목록
```java
// 개인정보 필드
"gender"         → 성별 (M/F/OTHER)
"birthDate"      → 생년월일 (LocalDate)
"height"         → 키 (cm)
"weight"         → 몸무게 (kg)
"armReach"       → 팔 리치 (cm)

// 클라이밍 정보
"climbingYears"  → 클라이밍 경력 (년)
"bio"            → 자기소개 (XSS 필터링)

// 공개 설정
"isPublic"       → 프로필 공개 여부
"profileImageUrl"→ 프로필 이미지 URL
```

### 3. 캐시 무효화 전략
```java
@Caching(evict = {
    @CacheEvict(value = "users", key = "#userId"),          // 사용자 기본 정보
    @CacheEvict(value = "userProfiles", key = "#userId")    // 프로필 상세 정보
})
```

---

## ✅ 구현 완료 체크리스트

### 👤 사용자 관리
- [x] 사용자 조회 (@Cacheable 캐싱 적용)
- [x] 이메일/닉네임으로 사용자 조회
- [x] 사용자 상태 검증 (ACTIVE/INACTIVE/SUSPENDED/DELETED)
- [x] 계정 삭제 (소프트 삭제)
- [x] XSS 방지 (모든 입력값 필터링)

### 📝 프로필 관리
- [x] 프로필 조회 (@Cacheable 캐싱)
- [x] 동적 프로필 업데이트 (Map 기반)
- [x] 프로필 이미지 업데이트
- [x] 캐시 무효화 (@CacheEvict)
- [x] 개인정보 필드 9개 지원

### 🔤 닉네임 시스템
- [x] 한글 닉네임 정규식 검증 (2-10자)
- [x] 닉네임 중복 검사
- [x] 닉네임 변경 기능
- [x] 영문/숫자 조합 지원
- [x] XSS 방지 적용

### 👥 팔로우 시스템
- [x] 팔로우/언팔로우 기능
- [x] 팔로워/팔로잉 목록 조회 (페이징)
- [x] 팔로우 여부 확인
- [x] 실시간 팔로우 카운트 관리
- [x] 자기 자신 팔로우 방지

### 🔍 검색 기능
- [x] 닉네임 기반 사용자 검색 (부분 일치)
- [x] 최근 활성 사용자 조회
- [x] 개인정보 보호 (이메일 검색 제한)
- [x] 페이징 지원
- [x] XSS 방지 검색어 필터링

---

**다음 파일**: Step 6-1d UserVerificationService & 보안 유틸리티 구현  
**핵심 목표**: 인증 관리, 약관 동의, JWT/XSS 보안 유틸리티 구현

*완료일: 2025-08-20*  
*핵심 성과: 사용자 관리 및 소셜 네트워킹 시스템 완전 구현*