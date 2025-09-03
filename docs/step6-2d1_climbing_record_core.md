# Step 6-2d1: ClimbingRecordService 기록 관리 핵심

> 클라이밍 기록 CRUD, 등급 변환, 신발 관리
> 생성일: 2025-08-21
> 단계: 6-2d1 (Service 레이어 - 클라이밍 기록 핵심)
> 참고: step4-3c1, step5-3f1, step5-3f2

---

## 🎯 설계 목표

- **기록 관리**: 클라이밍 시도/완등 기록 생성 및 관리
- **등급 변환**: V등급 ↔ YDS ↔ 프랑스 등급 상호 변환
- **신발 관리**: 개인 신발 컬렉션 및 성능 평가
- **팔로우 시스템**: 사용자 간 팔로우 관계 관리

---

## 🧗‍♂️ ClimbingRecordService - 기록 관리 핵심

### ClimbingRecordService.java
```java
package com.routepick.service.climb;

import com.routepick.common.enums.ClimbingResult;
import com.routepick.domain.climb.entity.ClimbingLevel;
import com.routepick.domain.climb.entity.ClimbingShoe;
import com.routepick.domain.climb.entity.UserClimbingShoe;
import com.routepick.domain.activity.entity.UserClimb;
import com.routepick.domain.activity.entity.UserFollow;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.climb.repository.ClimbingLevelRepository;
import com.routepick.domain.climb.repository.ClimbingShoeRepository;
import com.routepick.domain.climb.repository.UserClimbingShoeRepository;
import com.routepick.domain.activity.repository.UserClimbRepository;
import com.routepick.domain.activity.repository.UserFollowRepository;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.exception.route.RouteException;
import com.routepick.exception.user.UserException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 클라이밍 기록 관리 서비스 - 핵심 기능
 * 
 * 주요 기능:
 * - 클라이밍 기록 생성 및 관리
 * - V등급/YDS/프랑스 등급 변환
 * - 신발 프로필 관리
 * - 사용자 팔로우 시스템
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClimbingRecordService {

    private final UserClimbRepository userClimbRepository;
    private final UserFollowRepository userFollowRepository;
    private final ClimbingLevelRepository climbingLevelRepository;
    private final ClimbingShoeRepository climbingShoeRepository;
    private final UserClimbingShoeRepository userClimbingShoeRepository;
    private final RouteRepository routeRepository;
    
    @Value("${routepick.climbing.max-attempts-per-route:100}")
    private int maxAttemptsPerRoute;

    // ===== 클라이밍 기록 관리 =====

    /**
     * 클라이밍 기록 생성
     */
    @Transactional
    @CacheEvict(value = {"user-climb-stats", "user-progress"}, allEntries = true)
    public UserClimb recordClimbing(Long userId, Long routeId, ClimbingResult result,
                                  Integer attempts, String memo, Long shoeId) {
        
        // 루트 존재 검증
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 시도 횟수 검증
        if (attempts <= 0 || attempts > maxAttemptsPerRoute) {
            throw RouteException.invalidAttemptCount(attempts, maxAttemptsPerRoute);
        }
        
        // XSS 보호
        if (StringUtils.hasText(memo)) {
            memo = XssProtectionUtil.cleanInput(memo);
        }
        
        // 신발 검증 (선택사항)
        UserClimbingShoe userShoe = null;
        if (shoeId != null) {
            userShoe = userClimbingShoeRepository.findByIdAndUserIdAndDeletedFalse(shoeId, userId)
                .orElseThrow(() -> UserException.userShoeNotFound(shoeId, userId));
        }
        
        // 기존 기록 확인 (같은 날 같은 루트)
        LocalDate today = LocalDate.now();
        Optional<UserClimb> existingRecord = userClimbRepository
            .findByUserIdAndRouteIdAndClimbDateAndDeletedFalse(userId, routeId, today);
        
        UserClimb userClimb;
        if (existingRecord.isPresent()) {
            // 기존 기록 업데이트
            userClimb = existingRecord.get();
            userClimb.updateRecord(result, attempts, memo);
            if (userShoe != null) {
                userClimb.updateShoe(userShoe);
                updateShoeUsage(userShoe);
            }
            log.info("클라이밍 기록 업데이트 - userId: {}, routeId: {}, result: {}", 
                    userId, routeId, result);
        } else {
            // 새 기록 생성
            userClimb = UserClimb.builder()
                .userId(userId)
                .route(route)
                .result(result)
                .attempts(attempts)
                .memo(memo)
                .climbDate(today)
                .userClimbingShoe(userShoe)
                .build();
            userClimb = userClimbRepository.save(userClimb);
            
            if (userShoe != null) {
                updateShoeUsage(userShoe);
            }
            
            log.info("클라이밍 기록 생성 - userId: {}, routeId: {}, result: {}", 
                    userId, routeId, result);
        }
        
        // 루트 통계 업데이트
        updateRouteStatistics(route, result);
        
        return userClimb;
    }

    /**
     * 사용자 클라이밍 기록 목록
     */
    @Cacheable(value = "user-climb-records", 
               key = "#userId + '_' + #startDate + '_' + #endDate + '_' + #pageable.pageNumber")
    public Page<UserClimb> getUserClimbRecords(Long userId, LocalDate startDate, 
                                             LocalDate endDate, Pageable pageable) {
        
        if (startDate != null && endDate != null) {
            return userClimbRepository.findByUserIdAndClimbDateBetweenAndDeletedFalse(
                userId, startDate, endDate, pageable);
        } else {
            return userClimbRepository.findByUserIdAndDeletedFalseOrderByClimbDateDesc(
                userId, pageable);
        }
    }

    /**
     * 루트별 사용자 기록 조회
     */
    @Cacheable(value = "user-route-record", key = "#userId + '_' + #routeId")
    public Optional<UserClimb> getUserRouteRecord(Long userId, Long routeId) {
        return userClimbRepository.findLatestByUserIdAndRouteIdAndDeletedFalse(userId, routeId);
    }

    /**
     * 클라이밍 기록 삭제
     */
    @Transactional
    @CacheEvict(value = {"user-climb-records", "user-climb-stats"}, allEntries = true)
    public void deleteClimbRecord(Long recordId, Long userId) {
        UserClimb userClimb = userClimbRepository.findByIdAndUserIdAndDeletedFalse(recordId, userId)
            .orElseThrow(() -> UserException.climbRecordNotFound(recordId, userId));
        
        userClimb.markAsDeleted();
        
        log.info("클라이밍 기록 삭제 - recordId: {}, userId: {}", recordId, userId);
    }

    /**
     * 기록 수정
     */
    @Transactional
    @CacheEvict(value = {"user-climb-records", "user-climb-stats"}, allEntries = true)
    public UserClimb updateClimbRecord(Long recordId, Long userId, ClimbingResult result,
                                     Integer attempts, String memo) {
        UserClimb userClimb = userClimbRepository.findByIdAndUserIdAndDeletedFalse(recordId, userId)
            .orElseThrow(() -> UserException.climbRecordNotFound(recordId, userId));
        
        // 입력 검증
        if (attempts != null && (attempts <= 0 || attempts > maxAttemptsPerRoute)) {
            throw RouteException.invalidAttemptCount(attempts, maxAttemptsPerRoute);
        }
        
        // XSS 보호
        if (StringUtils.hasText(memo)) {
            memo = XssProtectionUtil.cleanInput(memo);
        }
        
        // 기록 업데이트
        userClimb.updateRecord(result, attempts, memo);
        
        log.info("클라이밍 기록 수정 - recordId: {}, userId: {}, result: {}", 
                recordId, userId, result);
        return userClimb;
    }

    // ===== 등급 변환 시스템 =====

    /**
     * V등급을 YDS 등급으로 변환
     */
    @Cacheable(value = "grade-conversion", key = "'v-to-yds_' + #vGrade")
    public String convertVGradeToYds(String vGrade) {
        return climbingLevelRepository.findYdsGradeByVGrade(vGrade)
            .orElse("Unknown");
    }

    /**
     * YDS 등급을 V등급으로 변환
     */
    @Cacheable(value = "grade-conversion", key = "'yds-to-v_' + #ydsGrade")
    public String convertYdsToVGrade(String ydsGrade) {
        return climbingLevelRepository.findVGradeByYdsGrade(ydsGrade)
            .orElse("Unknown");
    }

    /**
     * 프랑스 등급을 V등급으로 변환
     */
    @Cacheable(value = "grade-conversion", key = "'french-to-v_' + #frenchGrade")
    public String convertFrenchToVGrade(String frenchGrade) {
        return climbingLevelRepository.findVGradeByFrenchGrade(frenchGrade)
            .orElse("Unknown");
    }

    /**
     * V등급을 프랑스 등급으로 변환
     */
    @Cacheable(value = "grade-conversion", key = "'v-to-french_' + #vGrade")
    public String convertVGradeToFrench(String vGrade) {
        return climbingLevelRepository.findFrenchGradeByVGrade(vGrade)
            .orElse("Unknown");
    }

    /**
     * 등급별 난이도 점수 조회
     */
    @Cacheable(value = "grade-difficulty", key = "#levelId")
    public BigDecimal getGradeDifficultyScore(Long levelId) {
        return climbingLevelRepository.findById(levelId)
            .map(ClimbingLevel::getDifficultyScore)
            .orElse(BigDecimal.ZERO);
    }

    /**
     * 등급 목록 조회 (난이도순)
     */
    @Cacheable(value = "climbing-levels")
    public List<ClimbingLevel> getClimbingLevels() {
        return climbingLevelRepository.findAllOrderByDifficultyScore();
    }

    /**
     * 등급 범위 검색
     */
    @Cacheable(value = "grade-range", key = "#minDifficulty + '_' + #maxDifficulty")
    public List<ClimbingLevel> getLevelsByDifficultyRange(BigDecimal minDifficulty, 
                                                         BigDecimal maxDifficulty) {
        return climbingLevelRepository.findByDifficultyScoreBetween(minDifficulty, maxDifficulty);
    }

    // ===== 신발 관리 시스템 =====

    /**
     * 사용자 신발 등록
     */
    @Transactional
    @CacheEvict(value = "user-shoes", key = "#userId")
    public UserClimbingShoe registerUserShoe(Long userId, Long shoeId, String nickname,
                                            Integer personalRating, String review,
                                            LocalDate purchaseDate, BigDecimal purchasePrice) {
        
        // 신발 존재 검증
        ClimbingShoe shoe = climbingShoeRepository.findByIdAndDeletedFalse(shoeId)
            .orElseThrow(() -> UserException.shoeNotFound(shoeId));
        
        // XSS 보호
        if (StringUtils.hasText(nickname)) {
            nickname = XssProtectionUtil.cleanInput(nickname);
        }
        if (StringUtils.hasText(review)) {
            review = XssProtectionUtil.cleanInput(review);
        }
        
        // 개인 평점 검증 (1-10)
        if (personalRating != null && (personalRating < 1 || personalRating > 10)) {
            throw UserException.invalidShoeRating(personalRating);
        }
        
        // 중복 등록 검증
        if (userClimbingShoeRepository.existsByUserIdAndShoeIdAndDeletedFalse(userId, shoeId)) {
            throw UserException.shoeAlreadyRegistered(userId, shoeId);
        }
        
        UserClimbingShoe userShoe = UserClimbingShoe.builder()
            .userId(userId)
            .shoe(shoe)
            .nickname(nickname)
            .personalRating(personalRating)
            .review(review)
            .purchaseDate(purchaseDate)
            .purchasePrice(purchasePrice)
            .usageCount(0L)
            .build();
            
        UserClimbingShoe savedUserShoe = userClimbingShoeRepository.save(userShoe);
        
        log.info("사용자 신발 등록 - userId: {}, shoeId: {}, nickname: {}", 
                userId, shoeId, nickname);
        return savedUserShoe;
    }

    /**
     * 사용자 신발 목록
     */
    @Cacheable(value = "user-shoes", key = "#userId")
    public List<UserClimbingShoe> getUserShoes(Long userId) {
        return userClimbingShoeRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * 신발 정보 수정
     */
    @Transactional
    @CacheEvict(value = "user-shoes", key = "#userId")
    public UserClimbingShoe updateUserShoe(Long userShoeId, Long userId, String nickname,
                                         Integer personalRating, String review) {
        
        UserClimbingShoe userShoe = userClimbingShoeRepository
            .findByIdAndUserIdAndDeletedFalse(userShoeId, userId)
            .orElseThrow(() -> UserException.userShoeNotFound(userShoeId, userId));
        
        // XSS 보호 및 업데이트
        if (nickname != null) {
            userShoe.updateNickname(XssProtectionUtil.cleanInput(nickname));
        }
        
        if (personalRating != null) {
            if (personalRating < 1 || personalRating > 10) {
                throw UserException.invalidShoeRating(personalRating);
            }
            userShoe.updatePersonalRating(personalRating);
        }
        
        if (review != null) {
            userShoe.updateReview(XssProtectionUtil.cleanInput(review));
        }
        
        log.info("사용자 신발 정보 수정 - userShoeId: {}, userId: {}", userShoeId, userId);
        return userShoe;
    }

    /**
     * 신발 삭제
     */
    @Transactional
    @CacheEvict(value = "user-shoes", key = "#userId")
    public void deleteUserShoe(Long userShoeId, Long userId) {
        UserClimbingShoe userShoe = userClimbingShoeRepository
            .findByIdAndUserIdAndDeletedFalse(userShoeId, userId)
            .orElseThrow(() -> UserException.userShoeNotFound(userShoeId, userId));
        
        userShoe.markAsDeleted();
        
        log.info("사용자 신발 삭제 - userShoeId: {}, userId: {}", userShoeId, userId);
    }

    /**
     * 신발 추천 시스템
     */
    @Cacheable(value = "shoe-recommendations", key = "#userId")
    public List<ClimbingShoe> getRecommendedShoes(Long userId) {
        // 사용자의 완등 기록 기반 레벨 분석
        List<ClimbingLevel> userLevels = userClimbRepository.findCompletedLevelsByUserId(userId);
        
        if (userLevels.isEmpty()) {
            // 기록이 없는 경우 초보자용 신발 추천
            return climbingShoeRepository.findBeginnerShoes();
        }
        
        // 평균 난이도 계산
        double avgDifficulty = userLevels.stream()
            .mapToDouble(level -> level.getDifficultyScore().doubleValue())
            .average()
            .orElse(0.0);
        
        // 난이도에 맞는 신발 추천
        return climbingShoeRepository.findShoesByDifficultyRange(
            BigDecimal.valueOf(avgDifficulty - 10.0),
            BigDecimal.valueOf(avgDifficulty + 10.0));
    }

    // ===== 팔로우 시스템 =====

    /**
     * 사용자 팔로우/언팔로우 토글
     */
    @Transactional
    @CacheEvict(value = {"user-following", "user-followers"}, allEntries = true)
    public boolean toggleUserFollow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw UserException.cannotFollowSelf();
        }
        
        Optional<UserFollow> existingFollow = userFollowRepository
            .findByFollowerIdAndFolloweeIdAndDeletedFalse(followerId, followeeId);
        
        if (existingFollow.isPresent()) {
            // 언팔로우
            existingFollow.get().markAsDeleted();
            log.info("사용자 언팔로우 - followerId: {}, followeeId: {}", followerId, followeeId);
            return false;
        } else {
            // 팔로우
            UserFollow userFollow = UserFollow.builder()
                .followerId(followerId)
                .followeeId(followeeId)
                .build();
            userFollowRepository.save(userFollow);
            log.info("사용자 팔로우 - followerId: {}, followeeId: {}", followerId, followeeId);
            return true;
        }
    }

    /**
     * 팔로잉 목록
     */
    @Cacheable(value = "user-following", key = "#userId + '_' + #pageable.pageNumber")
    public Page<UserFollow> getUserFollowing(Long userId, Pageable pageable) {
        return userFollowRepository.findByFollowerIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 팔로워 목록
     */
    @Cacheable(value = "user-followers", key = "#userId + '_' + #pageable.pageNumber")
    public Page<UserFollow> getUserFollowers(Long userId, Pageable pageable) {
        return userFollowRepository.findByFolloweeIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 팔로우 상태 확인
     */
    public boolean isFollowing(Long followerId, Long followeeId) {
        return userFollowRepository.existsByFollowerIdAndFolloweeIdAndDeletedFalse(followerId, followeeId);
    }

    /**
     * 팔로우 통계
     */
    @Cacheable(value = "follow-stats", key = "#userId")
    public FollowStatsDto getFollowStats(Long userId) {
        long followingCount = userFollowRepository.countByFollowerIdAndDeletedFalse(userId);
        long followerCount = userFollowRepository.countByFolloweeIdAndDeletedFalse(userId);
        
        return FollowStatsDto.builder()
            .userId(userId)
            .followingCount(followingCount)
            .followerCount(followerCount)
            .build();
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 신발 사용 기록 업데이트
     */
    @Transactional
    protected void updateShoeUsage(UserClimbingShoe userShoe) {
        userShoe.incrementUsageCount();
        userClimbingShoeRepository.save(userShoe);
        log.debug("신발 사용 횟수 증가 - userShoeId: {}, count: {}", 
                 userShoe.getId(), userShoe.getUsageCount());
    }

    /**
     * 루트 통계 업데이트
     */
    @Transactional
    protected void updateRouteStatistics(Route route, ClimbingResult result) {
        route.incrementAttemptCount();
        
        if (result == ClimbingResult.COMPLETED) {
            route.incrementCompletionCount();
        }
        
        routeRepository.save(route);
        log.debug("루트 통계 업데이트 - routeId: {}, result: {}", route.getId(), result);
    }

    // ===== DTO 클래스 =====

    /**
     * 팔로우 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class FollowStatsDto {
        private final Long userId;
        private final Long followingCount;
        private final Long followerCount;
        
        public String getFollowRatio() {
            if (followingCount == 0) return "0:1";
            if (followerCount == 0) return "1:0";
            
            double ratio = (double) followerCount / followingCount;
            return String.format("%.1f:1", ratio);
        }
        
        public String getPopularityLevel() {
            if (followerCount >= 1000) return "인플루언서";
            if (followerCount >= 100) return "인기 사용자";
            if (followerCount >= 10) return "활발한 사용자";
            return "일반 사용자";
        }
    }
}
```

---

## 📋 주요 기능 설명

### 🧗‍♂️ **1. 클라이밍 기록 관리**
- **기록 생성**: 시도/완등 기록, 메모, 사용 신발 정보
- **중복 처리**: 같은 날 같은 루트 기록 업데이트
- **기록 수정**: 기존 기록 수정 및 삭제
- **검증 시스템**: 최대 시도 횟수, 입력값 검증

### 🔄 **2. 등급 변환 시스템**
- **V등급 ↔ YDS**: 볼더링 등급 상호 변환
- **프랑스 등급**: 프랑스 등급 ↔ V등급 변환
- **난이도 점수**: 등급별 수치화된 난이도 점수
- **등급 범위**: 난이도 범위 기반 등급 검색

### 👟 **3. 신발 관리 시스템**
- **신발 등록**: 개인 신발 컬렉션 관리
- **사용 추적**: 신발별 사용 횟수 자동 추적
- **개인 평가**: 신발 평점 및 리뷰 시스템
- **추천 시스템**: 레벨별 맞춤 신발 추천

### 👥 **4. 팔로우 시스템**
- **팔로우/언팔로우**: 토글 방식 팔로우 관리
- **팔로잉/팔로워**: 양방향 관계 관리 (페이징 지원)
- **자가 팔로우 방지**: 본인 팔로우 차단
- **팔로우 통계**: 팔로잉/팔로워 수 및 비율 분석

---

## 💾 **캐싱 전략**

### 캐시 키 구조
- **기록 목록**: `user-climb-records:{userId}_{startDate}_{endDate}_{page}`
- **루트 기록**: `user-route-record:{userId}_{routeId}`
- **신발 목록**: `user-shoes:{userId}`
- **등급 변환**: `grade-conversion:{type}_{grade}`
- **팔로우 목록**: `user-following:{userId}_{page}`

### 캐시 무효화
- **기록 생성/수정/삭제**: 관련 사용자 캐시 무효화
- **신발 등록/수정**: 사용자 신발 캐시 무효화
- **팔로우 변경**: 팔로우 관련 캐시 무효화

---

## 🎯 **등급 변환 매핑**

### V등급 ↔ YDS ↔ 프랑스 변환
| V등급 | YDS 등급 | 프랑스 등급 | 난이도 점수 |
|--------|----------|-------------|-------------|
| V0     | 5.10a    | 6a          | 10.0        |
| V1     | 5.10b    | 6a+         | 15.0        |
| V2     | 5.10c    | 6b          | 20.0        |
| V3     | 5.10d    | 6b+         | 25.0        |
| V4     | 5.11a    | 6c          | 30.0        |
| V5     | 5.11b    | 6c+         | 35.0        |
| V6     | 5.11c    | 7a          | 40.0        |
| V7     | 5.11d    | 7a+         | 45.0        |

---

## 🔒 **보안 및 검증**

### 입력 검증
- **시도 횟수**: 1~100회 범위 검증
- **개인 평점**: 1~10점 범위 검증
- **XSS 보호**: 모든 텍스트 입력 XssProtectionUtil 적용

### 권한 관리
- **기록 수정**: 작성자만 수정/삭제 가능
- **신발 관리**: 소유자만 수정/삭제 가능
- **자가 팔로우**: 본인 팔로우 방지

---

**📝 연계 파일**: step6-2d2_climbing_statistics_analysis.md와 함께 사용  
**완료일**: 2025-08-22  
**핵심 성과**: 클라이밍 기록 관리 + 등급 변환 + 신발 관리 완성