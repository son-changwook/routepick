# Step 6-2d2: Climbing Statistics Analysis Service

**파일**: `routepick-backend/src/main/java/com/routepick/service/ClimbingStatisticsService.java`

이 파일은 `step6-2d1_climbing_record_core.md`와 연계된 클라이밍 통계 분석 및 성장 추적 서비스입니다.

## 📋 클라이밍 통계 분석 서비스 구현

```java
package com.routepick.service;

import com.routepick.entity.*;
import com.routepick.repository.*;
import com.routepick.dto.statistics.*;
import com.routepick.exception.*;
import com.routepick.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 클라이밍 통계 분석 서비스
 * 
 * 주요 기능:
 * 1. 개인별 성장 통계 분석
 * 2. 난이도별/태그별 완등 통계
 * 3. 월간/연간 성장 추이 분석
 * 4. 개인 랭킹 시스템
 * 5. 성취 배지 시스템
 * 6. 비교 분석 기능
 * 
 * @author RoutePickr Team
 * @since 2024.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClimbingStatisticsService {
    
    private final UserClimbRepository userClimbRepository;
    private final RouteRepository routeRepository;
    private final ClimbingLevelRepository climbingLevelRepository;
    private final UserRepository userRepository;
    private final RouteTagRepository routeTagRepository;
    private final TagRepository tagRepository;
    private final CacheService cacheService;
    private final DateUtil dateUtil;
    
    private static final String STATS_CACHE_PREFIX = "climbing:stats";
    private static final int STATS_CACHE_TTL = 3600; // 1시간
    private static final String RANKING_CACHE_PREFIX = "climbing:ranking";
    private static final int RANKING_CACHE_TTL = 1800; // 30분
    private static final String GROWTH_CACHE_PREFIX = "climbing:growth";
    private static final int GROWTH_CACHE_TTL = 7200; // 2시간

    // ===================== 개인 통계 분석 =====================
    
    /**
     * 사용자 기본 통계 조회
     */
    @Cacheable(value = "userBasicStats", key = "#userId", unless = "#result == null")
    public UserClimbingStatsResponse getUserBasicStats(Long userId) {
        log.info("사용자 기본 통계 조회 - 사용자 ID: {}", userId);
        
        validateUser(userId);
        
        try {
            // 기본 통계 데이터 수집
            ClimbingBasicStats basicStats = collectBasicStats(userId);
            
            // 현재 레벨 정보
            ClimbingLevel currentLevel = getCurrentLevel(userId);
            
            // 최고 난이도 완등
            UserClimb hardestClimb = getHardestCompletedClimb(userId);
            
            // 최근 30일 활동
            RecentActivityStats recentActivity = getRecentActivityStats(userId, 30);
            
            return UserClimbingStatsResponse.builder()
                .userId(userId)
                .totalClimbs(basicStats.getTotalClimbs())
                .completedRoutes(basicStats.getCompletedRoutes())
                .attemptedRoutes(basicStats.getAttemptedRoutes())
                .successRate(basicStats.getSuccessRate())
                .averageGrade(basicStats.getAverageGrade())
                .currentLevel(currentLevel != null ? currentLevel.getDisplayName() : null)
                .hardestGrade(hardestClimb != null ? hardestClimb.getRoute().getDifficulty() : null)
                .recentActivity(recentActivity)
                .lastUpdated(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("사용자 기본 통계 조회 실패 - 사용자 ID: {}", userId, e);
            throw new StatisticsException(ErrorCode.STATISTICS_CALCULATION_FAILED, 
                "기본 통계 계산에 실패했습니다.");
        }
    }
    
    /**
     * 사용자 상세 통계 조회
     */
    @Cacheable(value = "userDetailedStats", key = "#userId + '_' + #period", unless = "#result == null")
    public UserDetailedStatsResponse getUserDetailedStats(Long userId, String period) {
        log.info("사용자 상세 통계 조회 - 사용자 ID: {}, 기간: {}", userId, period);
        
        validateUser(userId);
        validatePeriod(period);
        
        try {
            LocalDateTime startDate = calculateStartDate(period);
            
            // 난이도별 통계
            Map<String, GradeStats> gradeStats = getGradeStatistics(userId, startDate);
            
            // 태그별 통계
            Map<String, TagStats> tagStats = getTagStatistics(userId, startDate);
            
            // 월별 성장 추이
            List<MonthlyProgress> monthlyProgress = getMonthlyProgress(userId, startDate);
            
            // 성취 분석
            AchievementAnalysis achievements = getAchievementAnalysis(userId, startDate);
            
            return UserDetailedStatsResponse.builder()
                .userId(userId)
                .period(period)
                .gradeStatistics(gradeStats)
                .tagStatistics(tagStats)
                .monthlyProgress(monthlyProgress)
                .achievements(achievements)
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("사용자 상세 통계 조회 실패 - 사용자 ID: {}, 기간: {}", userId, period, e);
            throw new StatisticsException(ErrorCode.STATISTICS_CALCULATION_FAILED, 
                "상세 통계 계산에 실패했습니다.");
        }
    }

    // ===================== 성장 분석 =====================
    
    /**
     * 사용자 성장 추이 분석
     */
    @Cacheable(value = "userGrowthTrend", key = "#userId + '_' + #months", unless = "#result == null")
    public GrowthTrendResponse getUserGrowthTrend(Long userId, int months) {
        log.info("사용자 성장 추이 분석 - 사용자 ID: {}, 개월 수: {}", userId, months);
        
        validateUser(userId);
        validateMonthsRange(months);
        
        try {
            LocalDateTime startDate = LocalDateTime.now().minusMonths(months);
            
            // 월별 완등 데이터
            List<MonthlyClimbData> monthlyData = getMonthlyClimbData(userId, startDate);
            
            // 난이도 성장 추이
            List<GradeProgressData> gradeProgress = getGradeProgressData(userId, startDate);
            
            // 성장률 계산
            GrowthRateAnalysis growthRate = calculateGrowthRate(monthlyData, gradeProgress);
            
            // 예측 모델
            FuturePrediction prediction = generateFuturePrediction(gradeProgress, growthRate);
            
            return GrowthTrendResponse.builder()
                .userId(userId)
                .analysisMonths(months)
                .monthlyData(monthlyData)
                .gradeProgress(gradeProgress)
                .growthRate(growthRate)
                .futurePrediction(prediction)
                .analysisDate(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("사용자 성장 추이 분석 실패 - 사용자 ID: {}, 개월 수: {}", userId, months, e);
            throw new StatisticsException(ErrorCode.STATISTICS_CALCULATION_FAILED, 
                "성장 추이 분석에 실패했습니다.");
        }
    }
    
    /**
     * 개인 기록 비교 분석
     */
    public PersonalComparisonResponse getPersonalComparison(Long userId, LocalDate startDate, LocalDate endDate) {
        log.info("개인 기록 비교 분석 - 사용자 ID: {}, 시작: {}, 종료: {}", userId, startDate, endDate);
        
        validateUser(userId);
        validateDateRange(startDate, endDate);
        
        try {
            // 이전 기간과 현재 기간 데이터 수집
            LocalDate previousStart = startDate.minusDays(endDate.toEpochDay() - startDate.toEpochDay());
            
            PeriodStats currentPeriod = getPeriodStats(userId, startDate, endDate);
            PeriodStats previousPeriod = getPeriodStats(userId, previousStart, startDate.minusDays(1));
            
            // 비교 분석
            ComparisonAnalysis comparison = comparePerformance(currentPeriod, previousPeriod);
            
            // 개선 포인트 제안
            List<ImprovementSuggestion> suggestions = generateImprovementSuggestions(comparison);
            
            return PersonalComparisonResponse.builder()
                .userId(userId)
                .currentPeriod(currentPeriod)
                .previousPeriod(previousPeriod)
                .comparison(comparison)
                .improvements(suggestions)
                .comparisonDate(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("개인 기록 비교 분석 실패 - 사용자 ID: {}", userId, e);
            throw new StatisticsException(ErrorCode.STATISTICS_CALCULATION_FAILED, 
                "비교 분석에 실패했습니다.");
        }
    }

    // ===================== 랭킹 시스템 =====================
    
    /**
     * 사용자 랭킹 조회
     */
    @Cacheable(value = "userRanking", key = "#userId + '_' + #category", unless = "#result == null")
    public UserRankingResponse getUserRanking(Long userId, String category) {
        log.info("사용자 랭킹 조회 - 사용자 ID: {}, 카테고리: {}", userId, category);
        
        validateUser(userId);
        validateRankingCategory(category);
        
        try {
            String cacheKey = String.format("%s:%s:%s", RANKING_CACHE_PREFIX, category, userId);
            
            return cacheService.getOrCompute(cacheKey, RANKING_CACHE_TTL, () -> {
                RankingCalculator calculator = getRankingCalculator(category);
                
                // 전체 랭킹에서 사용자 위치
                int userRank = calculator.calculateUserRank(userId);
                
                // 주변 랭킹 (상위 5명, 하위 5명)
                List<RankingEntry> nearbyRanking = calculator.getNearbyRanking(userId, 5);
                
                // 카테고리별 상위 랭커
                List<RankingEntry> topRankers = calculator.getTopRankers(10);
                
                // 랭킹 점수 상세
                RankingScoreDetail scoreDetail = calculator.getScoreDetail(userId);
                
                return UserRankingResponse.builder()
                    .userId(userId)
                    .category(category)
                    .currentRank(userRank)
                    .totalParticipants(calculator.getTotalParticipants())
                    .percentile(calculatePercentile(userRank, calculator.getTotalParticipants()))
                    .nearbyRanking(nearbyRanking)
                    .topRankers(topRankers)
                    .scoreDetail(scoreDetail)
                    .lastUpdated(LocalDateTime.now())
                    .build();
            });
            
        } catch (Exception e) {
            log.error("사용자 랭킹 조회 실패 - 사용자 ID: {}, 카테고리: {}", userId, category, e);
            throw new StatisticsException(ErrorCode.RANKING_CALCULATION_FAILED, 
                "랭킹 계산에 실패했습니다.");
        }
    }
    
    /**
     * 글로벌 랭킹 조회
     */
    @Cacheable(value = "globalRanking", key = "#category + '_' + #pageable.pageNumber", unless = "#result == null")
    public GlobalRankingResponse getGlobalRanking(String category, Pageable pageable) {
        log.info("글로벌 랭킹 조회 - 카테고리: {}, 페이지: {}", category, pageable.getPageNumber());
        
        validateRankingCategory(category);
        
        try {
            String cacheKey = String.format("%s:global:%s:%d", RANKING_CACHE_PREFIX, category, pageable.getPageNumber());
            
            return cacheService.getOrCompute(cacheKey, RANKING_CACHE_TTL, () -> {
                RankingCalculator calculator = getRankingCalculator(category);
                
                // 페이징된 랭킹 목록
                Page<RankingEntry> rankings = calculator.getGlobalRanking(pageable);
                
                // 랭킹 통계
                RankingStatistics statistics = calculator.getRankingStatistics();
                
                return GlobalRankingResponse.builder()
                    .category(category)
                    .rankings(rankings.getContent())
                    .currentPage(pageable.getPageNumber())
                    .totalPages(rankings.getTotalPages())
                    .totalElements(rankings.getTotalElements())
                    .statistics(statistics)
                    .lastUpdated(LocalDateTime.now())
                    .build();
            });
            
        } catch (Exception e) {
            log.error("글로벌 랭킹 조회 실패 - 카테고리: {}", category, e);
            throw new StatisticsException(ErrorCode.RANKING_CALCULATION_FAILED, 
                "글로벌 랭킹 조회에 실패했습니다.");
        }
    }

    // ===================== 성취 시스템 =====================
    
    /**
     * 사용자 배지/성취 조회
     */
    @Cacheable(value = "userAchievements", key = "#userId", unless = "#result == null")
    public UserAchievementsResponse getUserAchievements(Long userId) {
        log.info("사용자 성취 조회 - 사용자 ID: {}", userId);
        
        validateUser(userId);
        
        try {
            // 현재 획득한 배지
            List<Achievement> earnedAchievements = getEarnedAchievements(userId);
            
            // 진행 중인 성취
            List<Achievement> inProgressAchievements = getInProgressAchievements(userId);
            
            // 추천 성취 (다음 목표)
            List<Achievement> recommendedAchievements = getRecommendedAchievements(userId);
            
            // 성취 점수 계산
            int totalAchievementScore = calculateTotalAchievementScore(earnedAchievements);
            
            return UserAchievementsResponse.builder()
                .userId(userId)
                .earnedAchievements(earnedAchievements)
                .inProgressAchievements(inProgressAchievements)
                .recommendedAchievements(recommendedAchievements)
                .totalScore(totalAchievementScore)
                .completionRate(calculateCompletionRate(earnedAchievements))
                .lastUpdated(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("사용자 성취 조회 실패 - 사용자 ID: {}", userId, e);
            throw new StatisticsException(ErrorCode.ACHIEVEMENT_CALCULATION_FAILED, 
                "성취 조회에 실패했습니다.");
        }
    }

    // ===================== 비교 분석 =====================
    
    /**
     * 친구와 비교 분석
     */
    public FriendComparisonResponse compareFriends(Long userId, List<Long> friendIds, String period) {
        log.info("친구 비교 분석 - 사용자 ID: {}, 친구 수: {}, 기간: {}", userId, friendIds.size(), period);
        
        validateUser(userId);
        validateFriendIds(friendIds);
        validatePeriod(period);
        
        try {
            LocalDateTime startDate = calculateStartDate(period);
            
            // 본인 통계
            ComparisonUserStats userStats = getComparisonUserStats(userId, startDate);
            
            // 친구들 통계
            List<ComparisonUserStats> friendsStats = friendIds.stream()
                .map(friendId -> getComparisonUserStats(friendId, startDate))
                .collect(Collectors.toList());
            
            // 비교 분석
            ComparisonAnalysisResult analysisResult = analyzeComparison(userStats, friendsStats);
            
            // 그룹 통계
            GroupStatistics groupStats = calculateGroupStatistics(userStats, friendsStats);
            
            return FriendComparisonResponse.builder()
                .userId(userId)
                .period(period)
                .userStats(userStats)
                .friendsStats(friendsStats)
                .analysisResult(analysisResult)
                .groupStatistics(groupStats)
                .comparisonDate(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("친구 비교 분석 실패 - 사용자 ID: {}", userId, e);
            throw new StatisticsException(ErrorCode.COMPARISON_CALCULATION_FAILED, 
                "친구 비교 분석에 실패했습니다.");
        }
    }

    // ===================== 헬퍼 메소드 =====================
    
    private void validateUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ValidationException(ErrorCode.INVALID_USER_ID, "유효하지 않은 사용자 ID입니다.");
        }
        
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
    }
    
    private void validatePeriod(String period) {
        List<String> validPeriods = Arrays.asList("1M", "3M", "6M", "1Y", "ALL");
        if (!validPeriods.contains(period)) {
            throw new ValidationException(ErrorCode.INVALID_PERIOD, "유효하지 않은 기간입니다.");
        }
    }
    
    private void validateMonthsRange(int months) {
        if (months < 1 || months > 24) {
            throw new ValidationException(ErrorCode.INVALID_MONTHS_RANGE, "개월 수는 1~24 사이여야 합니다.");
        }
    }
    
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ValidationException(ErrorCode.INVALID_DATE_RANGE, "시작일과 종료일이 필요합니다.");
        }
        
        if (startDate.isAfter(endDate)) {
            throw new ValidationException(ErrorCode.INVALID_DATE_RANGE, "시작일이 종료일보다 늦을 수 없습니다.");
        }
        
        if (endDate.isAfter(LocalDate.now())) {
            throw new ValidationException(ErrorCode.INVALID_DATE_RANGE, "종료일이 현재 날짜보다 늦을 수 없습니다.");
        }
    }
    
    private void validateRankingCategory(String category) {
        List<String> validCategories = Arrays.asList("TOTAL", "MONTHLY", "DIFFICULTY", "COMPLETION_RATE");
        if (!validCategories.contains(category)) {
            throw new ValidationException(ErrorCode.INVALID_RANKING_CATEGORY, "유효하지 않은 랭킹 카테고리입니다.");
        }
    }
    
    private void validateFriendIds(List<Long> friendIds) {
        if (friendIds == null || friendIds.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_FRIEND_LIST, "친구 목록이 비어있습니다.");
        }
        
        if (friendIds.size() > 10) {
            throw new ValidationException(ErrorCode.TOO_MANY_FRIENDS, "비교할 수 있는 친구는 최대 10명입니다.");
        }
    }
    
    private LocalDateTime calculateStartDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        switch (period) {
            case "1M": return now.minusMonths(1);
            case "3M": return now.minusMonths(3);
            case "6M": return now.minusMonths(6);
            case "1Y": return now.minusYears(1);
            case "ALL": return LocalDateTime.of(2020, 1, 1, 0, 0); // 서비스 시작일
            default: return now.minusMonths(3); // 기본값
        }
    }
    
    private ClimbingBasicStats collectBasicStats(Long userId) {
        List<UserClimb> allClimbs = userClimbRepository.findByUserIdOrderByClimbedAtDesc(userId);
        
        long totalClimbs = allClimbs.size();
        long completedRoutes = allClimbs.stream()
            .filter(climb -> climb.getStatus() == ClimbStatus.COMPLETED)
            .count();
        long attemptedRoutes = allClimbs.stream()
            .map(climb -> climb.getRoute().getId())
            .collect(Collectors.toSet())
            .size();
        
        BigDecimal successRate = totalClimbs > 0 ? 
            BigDecimal.valueOf(completedRoutes).divide(BigDecimal.valueOf(totalClimbs), 2, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
        
        Double averageGrade = allClimbs.stream()
            .filter(climb -> climb.getStatus() == ClimbStatus.COMPLETED)
            .mapToDouble(climb -> climb.getRoute().getDifficulty())
            .average()
            .orElse(0.0);
        
        return ClimbingBasicStats.builder()
            .totalClimbs(totalClimbs)
            .completedRoutes(completedRoutes)
            .attemptedRoutes(attemptedRoutes)
            .successRate(successRate)
            .averageGrade(averageGrade)
            .build();
    }
    
    private ClimbingLevel getCurrentLevel(Long userId) {
        return userClimbRepository.findTopByUserIdAndStatusOrderByClimbedAtDesc(
            userId, ClimbStatus.COMPLETED)
            .map(climb -> climb.getRoute().getClimbingLevel())
            .orElse(null);
    }
    
    private UserClimb getHardestCompletedClimb(Long userId) {
        return userClimbRepository.findTopByUserIdAndStatusOrderByRouteDifficultyDesc(
            userId, ClimbStatus.COMPLETED).orElse(null);
    }
    
    private RecentActivityStats getRecentActivityStats(Long userId, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<UserClimb> recentClimbs = userClimbRepository.findByUserIdAndClimbedAtAfter(userId, startDate);
        
        long recentTotal = recentClimbs.size();
        long recentCompleted = recentClimbs.stream()
            .filter(climb -> climb.getStatus() == ClimbStatus.COMPLETED)
            .count();
        
        return RecentActivityStats.builder()
            .days(days)
            .totalClimbs(recentTotal)
            .completedClimbs(recentCompleted)
            .activeDays((int) recentClimbs.stream()
                .map(climb -> climb.getClimbedAt().toLocalDate())
                .distinct()
                .count())
            .build();
    }
    
    private BigDecimal calculatePercentile(int rank, int total) {
        if (total <= 0) return BigDecimal.ZERO;
        double percentile = ((double)(total - rank + 1) / total) * 100;
        return BigDecimal.valueOf(percentile).setScale(2, RoundingMode.HALF_UP);
    }
    
    private RankingCalculator getRankingCalculator(String category) {
        switch (category) {
            case "TOTAL": return new TotalClimbsRankingCalculator(userClimbRepository);
            case "MONTHLY": return new MonthlyRankingCalculator(userClimbRepository);
            case "DIFFICULTY": return new DifficultyRankingCalculator(userClimbRepository);
            case "COMPLETION_RATE": return new CompletionRateRankingCalculator(userClimbRepository);
            default: throw new ValidationException(ErrorCode.INVALID_RANKING_CATEGORY, "지원하지 않는 랭킹 카테고리입니다.");
        }
    }
}

/**
 * 클라이밍 기본 통계 DTO
 */
@Data
@Builder
class ClimbingBasicStats {
    private Long totalClimbs;
    private Long completedRoutes;
    private Long attemptedRoutes;
    private BigDecimal successRate;
    private Double averageGrade;
}

/**
 * 최근 활동 통계 DTO
 */
@Data
@Builder
class RecentActivityStats {
    private Integer days;
    private Long totalClimbs;
    private Long completedClimbs;
    private Integer activeDays;
}

/**
 * 랭킹 계산기 인터페이스
 */
interface RankingCalculator {
    int calculateUserRank(Long userId);
    List<RankingEntry> getNearbyRanking(Long userId, int range);
    List<RankingEntry> getTopRankers(int limit);
    RankingScoreDetail getScoreDetail(Long userId);
    int getTotalParticipants();
    Page<RankingEntry> getGlobalRanking(Pageable pageable);
    RankingStatistics getRankingStatistics();
}
```

## 📋 통계 응답 DTO 클래스

```java
package com.routepick.dto.statistics;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 사용자 클라이밍 통계 응답
 */
@Data
@Builder
public class UserClimbingStatsResponse {
    private Long userId;
    private Long totalClimbs;
    private Long completedRoutes;
    private Long attemptedRoutes;
    private BigDecimal successRate;
    private Double averageGrade;
    private String currentLevel;
    private Double hardestGrade;
    private RecentActivityStats recentActivity;
    private LocalDateTime lastUpdated;
}

/**
 * 사용자 상세 통계 응답
 */
@Data
@Builder
public class UserDetailedStatsResponse {
    private Long userId;
    private String period;
    private Map<String, GradeStats> gradeStatistics;
    private Map<String, TagStats> tagStatistics;
    private List<MonthlyProgress> monthlyProgress;
    private AchievementAnalysis achievements;
    private LocalDateTime generatedAt;
}

/**
 * 성장 추이 응답
 */
@Data
@Builder
public class GrowthTrendResponse {
    private Long userId;
    private Integer analysisMonths;
    private List<MonthlyClimbData> monthlyData;
    private List<GradeProgressData> gradeProgress;
    private GrowthRateAnalysis growthRate;
    private FuturePrediction futurePrediction;
    private LocalDateTime analysisDate;
}

/**
 * 개인 비교 분석 응답
 */
@Data
@Builder
public class PersonalComparisonResponse {
    private Long userId;
    private PeriodStats currentPeriod;
    private PeriodStats previousPeriod;
    private ComparisonAnalysis comparison;
    private List<ImprovementSuggestion> improvements;
    private LocalDateTime comparisonDate;
}

/**
 * 사용자 랭킹 응답
 */
@Data
@Builder
public class UserRankingResponse {
    private Long userId;
    private String category;
    private Integer currentRank;
    private Integer totalParticipants;
    private BigDecimal percentile;
    private List<RankingEntry> nearbyRanking;
    private List<RankingEntry> topRankers;
    private RankingScoreDetail scoreDetail;
    private LocalDateTime lastUpdated;
}

/**
 * 글로벌 랭킹 응답
 */
@Data
@Builder
public class GlobalRankingResponse {
    private String category;
    private List<RankingEntry> rankings;
    private Integer currentPage;
    private Integer totalPages;
    private Long totalElements;
    private RankingStatistics statistics;
    private LocalDateTime lastUpdated;
}

/**
 * 사용자 성취 응답
 */
@Data
@Builder
public class UserAchievementsResponse {
    private Long userId;
    private List<Achievement> earnedAchievements;
    private List<Achievement> inProgressAchievements;
    private List<Achievement> recommendedAchievements;
    private Integer totalScore;
    private BigDecimal completionRate;
    private LocalDateTime lastUpdated;
}

/**
 * 친구 비교 응답
 */
@Data
@Builder
public class FriendComparisonResponse {
    private Long userId;
    private String period;
    private ComparisonUserStats userStats;
    private List<ComparisonUserStats> friendsStats;
    private ComparisonAnalysisResult analysisResult;
    private GroupStatistics groupStatistics;
    private LocalDateTime comparisonDate;
}
```

## 🔄 캐시 전략 및 성능 최적화

```java
/**
 * 통계 캐시 관리
 */
@Component
public class StatisticsCacheManager {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 통계 캐시 무효화
     */
    public void invalidateUserStats(Long userId) {
        String pattern = String.format("*%s*", userId);
        Set<String> keysToDelete = redisTemplate.keys(pattern);
        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
    }
    
    /**
     * 랭킹 캐시 전체 갱신
     */
    @Scheduled(cron = "0 */30 * * * *") // 30분마다
    public void refreshRankingCache() {
        String pattern = RANKING_CACHE_PREFIX + "*";
        Set<String> keysToDelete = redisTemplate.keys(pattern);
        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
    }
}
```

## 📊 연동 참고사항

### step6-2d1_climbing_record_core.md 연동점
1. **ClimbingRecordService**: 기록 생성/수정 시 통계 캐시 무효화
2. **기록 검증**: 통계 계산 시 검증된 기록만 사용
3. **등급 변환**: 통계 분석 시 일관된 등급 체계 적용
4. **신발 정보**: 신발별 성능 분석 포함

### 주요 의존성
- **UserClimbRepository**: 기록 데이터 조회
- **RouteRepository**: 루트 정보 조회  
- **ClimbingLevelRepository**: 레벨 정보 조회
- **CacheService**: 통계 캐싱 최적화
- **DateUtil**: 날짜 계산 유틸리티

### 확장 계획
1. **머신러닝 예측**: 성장 패턴 분석 고도화
2. **실시간 통계**: WebSocket 기반 실시간 업데이트
3. **소셜 통계**: 그룹별/커뮤니티별 통계 분석
4. **개인화 인사이트**: AI 기반 개선 제안 시스템

---
**연관 파일**: `step6-2d1_climbing_record_core.md`
**구현 우선순위**: HIGH (통계 분석 핵심 기능)
**예상 개발 기간**: 5-7일