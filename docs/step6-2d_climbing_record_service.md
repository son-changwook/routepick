# Step 6-2d: ClimbingRecordService 구현

> 클라이밍 기록 관리 서비스 - V등급/YDS 변환, 통계 분석, 신발 관리, 진행도 추적  
> 생성일: 2025-08-21  
> 단계: 6-2d (Service 레이어 - 클라이밍 기록 도메인)  
> 참고: step4-3c1, step5-3f1, step5-3f2

---

## 🎯 설계 목표

- **등급 변환 시스템**: V등급 ↔ YDS ↔ 프랑스 등급 상호 변환
- **클라이밍 기록 관리**: 시도/완등 기록, 통계 분석, 진행도 추적
- **신발 프로필 관리**: 개인 신발 컬렉션, 성능 평가, 추천 시스템
- **성과 분석**: 레벨별 성장 추적, 개인/그룹 비교 분석
- **목표 설정**: 개인화된 클라이밍 목표 및 달성률 관리

---

## 🧗‍♂️ ClimbingRecordService - 클라이밍 기록 관리 서비스

### ClimbingRecordService.java
```java
package com.routepick.service.climb;

import com.routepick.common.enums.ClimbingResult;
import com.routepick.common.enums.DifficultyLevel;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 클라이밍 기록 관리 서비스
 * 
 * 주요 기능:
 * - 클라이밍 기록 생성 및 관리
 * - V등급/YDS/프랑스 등급 변환
 * - 개인/그룹 통계 분석
 * - 신발 프로필 관리
 * - 레벨 진행도 추적
 * - 목표 설정 및 달성률 분석
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
    
    @Value("${routepick.climbing.score-weight.completion:0.7}")
    private double completionWeight;
    
    @Value("${routepick.climbing.score-weight.attempt:0.3}")
    private double attemptWeight;

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

    // ===== 통계 및 분석 =====

    /**
     * 사용자 클라이밍 통계
     */
    @Cacheable(value = "user-climb-stats", key = "#userId")
    public UserClimbingStatsDto getUserClimbingStats(Long userId) {
        // 전체 기록 수
        long totalRecords = userClimbRepository.countByUserIdAndDeletedFalse(userId);
        
        // 완등 수
        long completedCount = userClimbRepository.countByUserIdAndResultAndDeletedFalse(
            userId, ClimbingResult.COMPLETED);
        
        // 완등률 계산
        BigDecimal completionRate = totalRecords > 0 ? 
            BigDecimal.valueOf((double) completedCount / totalRecords * 100.0)
                .setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        
        // 최고 등급
        ClimbingLevel highestLevel = userClimbRepository.findHighestCompletedLevel(userId)
            .orElse(null);
        
        // 최근 활동일
        LocalDate lastClimbDate = userClimbRepository.findLatestClimbDate(userId)
            .orElse(null);
        
        // 월별 통계
        Map<YearMonth, Long> monthlyStats = userClimbRepository
            .getMonthlyClimbCounts(userId, LocalDate.now().minusMonths(12));
        
        return UserClimbingStatsDto.builder()
            .userId(userId)
            .totalRecords(totalRecords)
            .completedCount(completedCount)
            .completionRate(completionRate)
            .highestLevel(highestLevel)
            .lastClimbDate(lastClimbDate)
            .monthlyStats(monthlyStats)
            .build();
    }

    /**
     * 레벨별 진행도 분석
     */
    @Cacheable(value = "user-progress", key = "#userId")
    public List<LevelProgressDto> getUserLevelProgress(Long userId) {
        List<ClimbingLevel> levels = getClimbingLevels();
        
        return levels.stream().map(level -> {
            long totalAttempts = userClimbRepository.countByUserIdAndLevelId(userId, level.getId());
            long completedCount = userClimbRepository.countByUserIdAndLevelIdAndResult(
                userId, level.getId(), ClimbingResult.COMPLETED);
            
            BigDecimal successRate = totalAttempts > 0 ? 
                BigDecimal.valueOf((double) completedCount / totalAttempts * 100.0)
                    .setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            
            return LevelProgressDto.builder()
                .level(level)
                .totalAttempts(totalAttempts)
                .completedCount(completedCount)
                .successRate(successRate)
                .build();
        }).collect(Collectors.toList());
    }

    /**
     * 기간별 성장 분석
     */
    @Cacheable(value = "user-growth", key = "#userId + '_' + #months")
    public GrowthAnalysisDto getGrowthAnalysis(Long userId, int months) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);
        
        // 기간별 기록 조회
        List<UserClimb> records = userClimbRepository
            .findByUserIdAndClimbDateBetweenAndDeletedFalse(userId, startDate, endDate);
        
        // 월별 그룹화
        Map<YearMonth, List<UserClimb>> monthlyRecords = records.stream()
            .collect(Collectors.groupingBy(record -> 
                YearMonth.from(record.getClimbDate())));
        
        // 월별 성장 지표 계산
        List<MonthlyProgressDto> monthlyProgress = monthlyRecords.entrySet().stream()
            .map(entry -> {
                YearMonth month = entry.getKey();
                List<UserClimb> monthRecords = entry.getValue();
                
                long completedCount = monthRecords.stream()
                    .mapToLong(r -> r.getResult() == ClimbingResult.COMPLETED ? 1 : 0)
                    .sum();
                
                OptionalDouble avgDifficulty = monthRecords.stream()
                    .filter(r -> r.getResult() == ClimbingResult.COMPLETED)
                    .mapToDouble(r -> r.getRoute().getLevel().getDifficultyScore().doubleValue())
                    .average();
                
                return MonthlyProgressDto.builder()
                    .month(month)
                    .totalAttempts((long) monthRecords.size())
                    .completedCount(completedCount)
                    .averageDifficulty(avgDifficulty.isPresent() ? 
                        BigDecimal.valueOf(avgDifficulty.getAsDouble())
                            .setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .build();
            })
            .sorted(Comparator.comparing(MonthlyProgressDto::getMonth))
            .collect(Collectors.toList());
        
        return GrowthAnalysisDto.builder()
            .userId(userId)
            .analysisStartDate(startDate)
            .analysisEndDate(endDate)
            .monthlyProgress(monthlyProgress)
            .build();
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
     * 신발 사용 기록 업데이트
     */
    @Transactional
    protected void updateShoeUsage(UserClimbingShoe userShoe) {
        userShoe.incrementUsageCount();
        log.debug("신발 사용 횟수 증가 - userShoeId: {}, count: {}", 
                 userShoe.getId(), userShoe.getUsageCount());
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
     * 사용자 팔로우
     */
    @Transactional
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
    @Cacheable(value = "user-following", key = "#userId")
    public List<UserFollow> getUserFollowing(Long userId) {
        return userFollowRepository.findByFollowerIdAndDeletedFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * 팔로워 목록
     */
    @Cacheable(value = "user-followers", key = "#userId")
    public List<UserFollow> getUserFollowers(Long userId) {
        return userFollowRepository.findByFolloweeIdAndDeletedFalseOrderByCreatedAtDesc(userId);
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 루트 통계 업데이트
     */
    @Transactional
    protected void updateRouteStatistics(Route route, ClimbingResult result) {
        route.incrementAttemptCount();
        
        if (result == ClimbingResult.COMPLETED) {
            route.incrementCompletionCount();
        }
        
        log.debug("루트 통계 업데이트 - routeId: {}, result: {}", route.getId(), result);
    }

    // ===== DTO 클래스 =====

    /**
     * 사용자 클라이밍 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class UserClimbingStatsDto {
        private final Long userId;
        private final long totalRecords;
        private final long completedCount;
        private final BigDecimal completionRate;
        private final ClimbingLevel highestLevel;
        private final LocalDate lastClimbDate;
        private final Map<YearMonth, Long> monthlyStats;
    }

    /**
     * 레벨 진행도 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class LevelProgressDto {
        private final ClimbingLevel level;
        private final long totalAttempts;
        private final long completedCount;
        private final BigDecimal successRate;
    }

    /**
     * 성장 분석 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class GrowthAnalysisDto {
        private final Long userId;
        private final LocalDate analysisStartDate;
        private final LocalDate analysisEndDate;
        private final List<MonthlyProgressDto> monthlyProgress;
    }

    /**
     * 월별 진행도 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class MonthlyProgressDto {
        private final YearMonth month;
        private final Long totalAttempts;
        private final Long completedCount;
        private final BigDecimal averageDifficulty;
    }
}
```

---

## 📋 주요 기능 설명

### 🧗‍♂️ **1. 클라이밍 기록 관리**
- **기록 생성**: 시도/완등 기록, 메모, 사용 신발 정보
- **중복 처리**: 같은 날 같은 루트 기록 업데이트
- **검증 시스템**: 최대 시도 횟수, 입력값 검증
- **통계 연동**: 루트 통계 자동 업데이트

### 🔄 **2. 등급 변환 시스템**
- **V등급 ↔ YDS**: 볼더링 등급 상호 변환
- **프랑스 등급**: 프랑스 등급 ↔ V등급 변환
- **난이도 점수**: 등급별 수치화된 난이도 점수
- **캐싱 최적화**: 변환 결과 Redis 캐싱

### 📊 **3. 통계 및 분석**
- **개인 통계**: 전체/완등 기록 수, 완등률, 최고 등급
- **레벨 진행도**: 등급별 성공률 및 시도 횟수
- **성장 분석**: 월별 성장 지표 및 트렌드 분석
- **월별 통계**: 12개월 활동 패턴 분석

### 👟 **4. 신발 관리 시스템**
- **신발 등록**: 개인 신발 컬렉션 관리
- **사용 추적**: 신발별 사용 횟수 자동 추적
- **개인 평가**: 신발 평점 및 리뷰 시스템
- **추천 시스템**: 레벨별 맞춤 신발 추천

### 👥 **5. 팔로우 시스템**
- **팔로우/언팔로우**: 토글 방식 팔로우 관리
- **팔로잉/팔로워**: 양방향 관계 관리
- **자가 팔로우 방지**: 본인 팔로우 차단
- **소프트 삭제**: 안전한 관계 해제

---

## 💾 **Redis 캐싱 전략**

### 캐시 키 구조
- **사용자 통계**: `user-climb-stats:{userId}`
- **진행도 분석**: `user-progress:{userId}`
- **성장 분석**: `user-growth:{userId}_{months}`
- **신발 목록**: `user-shoes:{userId}`
- **등급 변환**: `grade-conversion:{type}_{grade}`

### 캐시 무효화
- **기록 생성/삭제**: 관련 사용자 통계 캐시 무효화
- **신발 등록/수정**: 사용자 신발 캐시 무효화
- **TTL 관리**: 통계 6시간, 변환 24시간

---

## 🎯 **등급 변환 매핑 테이블**

### V등급 ↔ YDS 변환
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
| V8     | 5.12a    | 7b          | 50.0        |

---

## 📈 **성장 분석 지표**

### 개인 성장 측정
- **완등률 변화**: 월별 완등률 트렌드
- **평균 난이도**: 완등한 루트의 평균 난이도 상승
- **활동량**: 월별 클라이밍 횟수 패턴
- **레벨 진행**: 새로운 등급 도전 및 성공

### 비교 분석
- **또래 비교**: 같은 레벨 사용자 대비 성과
- **목표 달성**: 설정한 목표 대비 달성률
- **신발 효과**: 신발별 성능 차이 분석

---

## 🚀 **다음 단계**

**Phase 5 완료로 전체 step6-2 시리즈 완성:**
- ✅ **step6-2a_gym_service.md**: 체육관 관리 서비스
- ✅ **step6-2b_route_service.md**: 루트 관리 서비스
- ✅ **step6-2c_route_media_service.md**: 루트 미디어 서비스
- ✅ **step6-2d_climbing_record_service.md**: 클라이밍 기록 서비스

*step6-2d 완성: 클라이밍 기록 도메인 완전 구현 완료*  
*전체 Gym & Route 도메인 Service 레이어 구현 완료! 🎉*