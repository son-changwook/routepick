# Step 6-2b2: 루트 난이도 시스템

> V등급/YDS 변환, 난이도 투표 및 스크랩 시스템
> 생성일: 2025-08-21
> 단계: 6-2b2 (Service 레이어 - 루트 난이도 시스템)
> 참고: step4-2b2, step4-3b1, step5-3e2

---

## 🎯 설계 목표

- **등급 시스템**: V등급/YDS 등급 변환 및 관리
- **난이도 투표**: 사용자 참여형 난이도 보정 시스템
- **스크랩 관리**: 개인화된 루트 북마크 및 목표 관리
- **통계 분석**: 투표 통계 및 분포 분석

---

## 📊 RouteDifficultyService.java

```java
package com.routepick.service.route;

import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.entity.RouteDifficultyVote;
import com.routepick.domain.route.entity.RouteScrap;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.route.repository.RouteDifficultyVoteRepository;
import com.routepick.domain.route.repository.RouteScrapRepository;
import com.routepick.exception.route.RouteException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 루트 난이도 관리 서비스
 * 
 * 주요 기능:
 * - 난이도 투표 및 보정 시스템
 * - 루트 스크랩 및 개인화 관리
 * - V등급/YDS 등급 변환 시스템
 * - 투표 통계 및 분석
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteDifficultyService {

    private final RouteRepository routeRepository;
    private final RouteDifficultyVoteRepository routeDifficultyVoteRepository;
    private final RouteScrapRepository routeScrapRepository;

    // ===== 난이도 투표 시스템 =====

    /**
     * 난이도 투표 등록/수정
     */
    @Transactional
    @CacheEvict(value = {"route", "routes"}, key = "#routeId")
    public RouteDifficultyVote voteRouteDifficulty(Long routeId, Long userId, 
                                                 Integer voteScore, String comment) {
        
        // 루트 존재 검증
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 투표 점수 검증 (1-10)
        if (voteScore < 1 || voteScore > 10) {
            throw RouteException.invalidVoteScore(voteScore);
        }
        
        // XSS 보호
        if (StringUtils.hasText(comment)) {
            comment = XssProtectionUtil.cleanInput(comment);
        }
        
        // 기존 투표 확인
        Optional<RouteDifficultyVote> existingVote = 
            routeDifficultyVoteRepository.findByRouteIdAndUserIdAndDeletedFalse(routeId, userId);
        
        RouteDifficultyVote vote;
        if (existingVote.isPresent()) {
            // 기존 투표 수정
            vote = existingVote.get();
            vote.updateVote(voteScore, comment);
            log.info("난이도 투표 수정 - routeId: {}, userId: {}, score: {}", 
                    routeId, userId, voteScore);
        } else {
            // 새 투표 등록
            vote = RouteDifficultyVote.builder()
                .route(route)
                .userId(userId)
                .voteScore(voteScore)
                .comment(comment)
                .build();
            vote = routeDifficultyVoteRepository.save(vote);
            log.info("난이도 투표 등록 - routeId: {}, userId: {}, score: {}", 
                    routeId, userId, voteScore);
        }
        
        // 루트 평균 투표 점수 업데이트
        updateRouteAverageVoteScore(routeId);
        
        return vote;
    }

    /**
     * 루트 평균 투표 점수 업데이트
     */
    @Transactional
    protected void updateRouteAverageVoteScore(Long routeId) {
        BigDecimal averageScore = routeDifficultyVoteRepository
            .calculateWeightedAverageScore(routeId);
            
        if (averageScore != null) {
            routeRepository.updateAverageVoteScore(routeId, averageScore);
            log.debug("루트 평균 투표 점수 업데이트 - routeId: {}, average: {}", 
                     routeId, averageScore);
        }
    }

    /**
     * 난이도 투표 삭제
     */
    @Transactional
    @CacheEvict(value = {"route", "route-vote-stats"}, key = "#routeId")
    public void deleteVote(Long routeId, Long userId) {
        RouteDifficultyVote vote = routeDifficultyVoteRepository
            .findByRouteIdAndUserIdAndDeletedFalse(routeId, userId)
            .orElseThrow(() -> RouteException.voteNotFound(routeId, userId));
        
        vote.markAsDeleted();
        
        // 루트 평균 투표 점수 업데이트
        updateRouteAverageVoteScore(routeId);
        
        log.info("난이도 투표 삭제 - routeId: {}, userId: {}", routeId, userId);
    }

    /**
     * 사용자의 루트 투표 조회
     */
    public Optional<RouteDifficultyVote> getUserVote(Long routeId, Long userId) {
        return routeDifficultyVoteRepository.findByRouteIdAndUserIdAndDeletedFalse(routeId, userId);
    }

    /**
     * 루트 투표 통계 조회
     */
    @Cacheable(value = "route-vote-stats", key = "#routeId")
    public RouteVoteStatsDto getRouteVoteStats(Long routeId) {
        Map<String, Object> stats = routeDifficultyVoteRepository
            .getRouteVoteStatistics(routeId);
            
        return RouteVoteStatsDto.builder()
            .routeId(routeId)
            .totalVotes(((Number) stats.get("totalVotes")).longValue())
            .averageScore((BigDecimal) stats.get("averageScore"))
            .scoreDistribution((Map<Integer, Long>) stats.get("scoreDistribution"))
            .medianScore(((Number) stats.get("medianScore")).intValue())
            .standardDeviation((BigDecimal) stats.get("standardDeviation"))
            .build();
    }

    /**
     * 루트 투표 목록 조회
     */
    @Cacheable(value = "route-votes", key = "#routeId + '_' + #pageable.pageNumber")
    public Page<RouteDifficultyVote> getRouteVotes(Long routeId, Pageable pageable) {
        return routeDifficultyVoteRepository.findByRouteIdAndDeletedFalseOrderByCreatedAtDesc(
            routeId, pageable);
    }

    // ===== 루트 스크랩 시스템 =====

    /**
     * 루트 스크랩 추가/제거 토글
     */
    @Transactional
    @CacheEvict(value = {"user-scraps", "route"}, allEntries = true)
    public boolean toggleRouteScrap(Long routeId, Long userId, String memo, 
                                  LocalDate targetDate, String folder) {
        
        // 루트 존재 검증
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
            .orElseThrow(() -> RouteException.routeNotFound(routeId));
        
        // 기존 스크랩 확인
        Optional<RouteScrap> existingScrap = 
            routeScrapRepository.findByRouteIdAndUserIdAndDeletedFalse(routeId, userId);
        
        if (existingScrap.isPresent()) {
            // 스크랩 제거
            RouteScrap scrap = existingScrap.get();
            scrap.markAsDeleted();
            
            // 루트 스크랩 수 감소
            route.decrementScrapCount();
            
            log.info("루트 스크랩 제거 - routeId: {}, userId: {}", routeId, userId);
            return false;
        } else {
            // XSS 보호
            if (StringUtils.hasText(memo)) {
                memo = XssProtectionUtil.cleanInput(memo);
            }
            if (StringUtils.hasText(folder)) {
                folder = XssProtectionUtil.cleanInput(folder);
            }
            
            // 스크랩 추가
            RouteScrap scrap = RouteScrap.builder()
                .route(route)
                .userId(userId)
                .memo(memo)
                .targetDate(targetDate)
                .folder(folder)
                .build();
            routeScrapRepository.save(scrap);
            
            // 루트 스크랩 수 증가
            route.incrementScrapCount();
            
            log.info("루트 스크랩 추가 - routeId: {}, userId: {}", routeId, userId);
            return true;
        }
    }

    /**
     * 사용자의 스크랩 루트 목록
     */
    @Cacheable(value = "user-scraps", key = "#userId + '_' + #folder + '_' + #pageable.pageNumber")
    public Page<RouteScrap> getUserScrapRoutes(Long userId, String folder, Pageable pageable) {
        if (StringUtils.hasText(folder)) {
            return routeScrapRepository.findByUserIdAndFolderAndDeletedFalseOrderByCreatedAtDesc(
                userId, folder, pageable);
        } else {
            return routeScrapRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(
                userId, pageable);
        }
    }

    /**
     * 사용자의 스크랩 폴더 목록
     */
    @Cacheable(value = "user-scrap-folders", key = "#userId")
    public List<String> getUserScrapFolders(Long userId) {
        return routeScrapRepository.findDistinctFoldersByUserId(userId);
    }

    /**
     * 스크랩 정보 수정
     */
    @Transactional
    @CacheEvict(value = "user-scraps", allEntries = true)
    public RouteScrap updateScrapInfo(Long scrapId, String memo, 
                                    LocalDate targetDate, String folder) {
        
        RouteScrap scrap = routeScrapRepository.findByIdAndDeletedFalse(scrapId)
            .orElseThrow(() -> RouteException.scrapNotFound(scrapId));
        
        // XSS 보호 및 업데이트
        if (memo != null) {
            scrap.updateMemo(XssProtectionUtil.cleanInput(memo));
        }
        
        if (targetDate != null) {
            scrap.updateTargetDate(targetDate);
        }
        
        if (folder != null) {
            scrap.updateFolder(XssProtectionUtil.cleanInput(folder));
        }
        
        log.info("스크랩 정보 수정 - scrapId: {}", scrapId);
        return scrap;
    }

    /**
     * 스크랩 상태 확인
     */
    public boolean isRouteScrappedByUser(Long routeId, Long userId) {
        return routeScrapRepository.existsByRouteIdAndUserIdAndDeletedFalse(routeId, userId);
    }

    /**
     * 목표일 기준 스크랩 루트 조회
     */
    @Cacheable(value = "user-target-scraps", key = "#userId + '_' + #targetDate")
    public List<RouteScrap> getUserScrapsByTargetDate(Long userId, LocalDate targetDate) {
        return routeScrapRepository.findByUserIdAndTargetDateAndDeletedFalse(userId, targetDate);
    }

    // ===== V등급/YDS 등급 변환 =====

    /**
     * V등급을 YDS 등급으로 변환
     */
    public String convertVGradeToYds(String vGrade) {
        // V등급 → YDS 변환 로직
        Map<String, String> vToYdsMap = Map.of(
            "V0", "5.10a", "V1", "5.10b", "V2", "5.10c", "V3", "5.10d",
            "V4", "5.11a", "V5", "5.11b", "V6", "5.11c", "V7", "5.11d",
            "V8", "5.12a", "V9", "5.12b", "V10", "5.12c", "V11", "5.12d",
            "V12", "5.13a", "V13", "5.13b", "V14", "5.13c", "V15", "5.13d",
            "V16", "5.14a", "V17", "5.14b"
        );
        
        return vToYdsMap.getOrDefault(vGrade.toUpperCase(), "Unknown");
    }

    /**
     * YDS 등급을 V등급으로 변환
     */
    public String convertYdsToVGrade(String ydsGrade) {
        // YDS → V등급 변환 로직
        Map<String, String> ydsToVMap = Map.of(
            "5.10a", "V0", "5.10b", "V1", "5.10c", "V2", "5.10d", "V3",
            "5.11a", "V4", "5.11b", "V5", "5.11c", "V6", "5.11d", "V7",
            "5.12a", "V8", "5.12b", "V9", "5.12c", "V10", "5.12d", "V11",
            "5.13a", "V12", "5.13b", "V13", "5.13c", "V14", "5.13d", "V15",
            "5.14a", "V16", "5.14b", "V17"
        );
        
        return ydsToVMap.getOrDefault(ydsGrade.toLowerCase(), "Unknown");
    }

    /**
     * 등급 범위 조회 (V등급 기준)
     */
    public GradeRangeDto getGradeRange(String minVGrade, String maxVGrade) {
        int minLevel = extractVGradeLevel(minVGrade);
        int maxLevel = extractVGradeLevel(maxVGrade);
        
        if (minLevel > maxLevel) {
            throw RouteException.invalidGradeRange(minVGrade, maxVGrade);
        }
        
        return GradeRangeDto.builder()
            .minVGrade(minVGrade)
            .maxVGrade(maxVGrade)
            .minYdsGrade(convertVGradeToYds(minVGrade))
            .maxYdsGrade(convertVGradeToYds(maxVGrade))
            .levelRange(maxLevel - minLevel + 1)
            .difficulty(calculateDifficultyLevel(minLevel, maxLevel))
            .build();
    }

    /**
     * V등급 레벨 추출 (V0 = 0, V1 = 1, ...)
     */
    private int extractVGradeLevel(String vGrade) {
        try {
            return Integer.parseInt(vGrade.substring(1));
        } catch (Exception e) {
            throw RouteException.invalidGradeFormat(vGrade);
        }
    }

    /**
     * 난이도 레벨 계산
     */
    private String calculateDifficultyLevel(int minLevel, int maxLevel) {
        double avgLevel = (minLevel + maxLevel) / 2.0;
        
        if (avgLevel <= 3) return "초급";
        if (avgLevel <= 7) return "중급";
        if (avgLevel <= 12) return "고급";
        return "전문가";
    }

    // ===== DTO 클래스 =====

    /**
     * 루트 투표 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class RouteVoteStatsDto {
        private final Long routeId;
        private final Long totalVotes;
        private final BigDecimal averageScore;
        private final Map<Integer, Long> scoreDistribution;
        private final Integer medianScore;
        private final BigDecimal standardDeviation;
        
        public String getDifficultyConsensus() {
            if (standardDeviation.compareTo(BigDecimal.valueOf(1.5)) <= 0) {
                return "높은 합의";
            } else if (standardDeviation.compareTo(BigDecimal.valueOf(2.5)) <= 0) {
                return "보통 합의";
            } else {
                return "낮은 합의";
            }
        }
        
        public String getReliabilityLevel() {
            if (totalVotes >= 10 && standardDeviation.compareTo(BigDecimal.valueOf(1.5)) <= 0) {
                return "매우 높음";
            } else if (totalVotes >= 5 && standardDeviation.compareTo(BigDecimal.valueOf(2.0)) <= 0) {
                return "높음";
            } else if (totalVotes >= 3) {
                return "보통";
            } else {
                return "낮음";
            }
        }
    }

    /**
     * 등급 범위 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class GradeRangeDto {
        private final String minVGrade;
        private final String maxVGrade;
        private final String minYdsGrade;
        private final String maxYdsGrade;
        private final Integer levelRange;
        private final String difficulty;
    }
}
```

---

## 📋 주요 기능 설명

### 🗳️ **1. 난이도 투표 시스템**
- **투표 등록/수정**: 1-10점 난이도 투표
- **가중 평균**: 투표 신뢰도 기반 평균 계산
- **투표 통계**: 점수 분포 및 통계 제공
- **중복 방지**: 사용자당 루트별 1회 투표
- **신뢰도 분석**: 표준편차 기반 합의 수준 측정

### 📌 **2. 스크랩 시스템**
- **토글 방식**: 스크랩 추가/제거 토글
- **개인화 관리**: 메모, 목표일, 폴더 분류
- **스크랩 목록**: 사용자별 스크랩 루트 관리
- **폴더 관리**: 사용자 정의 폴더 분류
- **목표 관리**: 목표일 기반 루트 관리

### 🔄 **3. 등급 변환 시스템**
- **V등급 ↔ YDS**: 볼더링 등급 상호 변환
- **표준 매핑**: 국제 표준 등급 매핑 테이블
- **등급 범위**: 난이도 범위 분석
- **난이도 레벨**: 초급/중급/고급/전문가 분류

### 📊 **4. 통계 분석**
- **투표 분포**: 점수별 투표 분포 분석
- **합의 수준**: 표준편차 기반 난이도 합의도
- **신뢰도 평가**: 투표 수와 분산 기반 신뢰도
- **중앙값**: 평균과 함께 중앙값 제공

---

## 💾 **캐싱 전략**

### 캐시 키 구조
- **투표 통계**: `route-vote-stats:{routeId}`
- **루트 투표 목록**: `route-votes:{routeId}_{page}`
- **사용자 스크랩**: `user-scraps:{userId}_{folder}_{page}`
- **스크랩 폴더**: `user-scrap-folders:{userId}`
- **목표 스크랩**: `user-target-scraps:{userId}_{date}`

### 캐시 무효화
- **투표 시**: 관련 루트 통계 캐시 무효화
- **스크랩 시**: 사용자 스크랩 관련 캐시 무효화
- **TTL 관리**: 통계 6시간, 스크랩 목록 1시간

---

## 🛡️ **보안 및 검증**

### 보안 강화
- **XSS 보호**: 모든 텍스트 입력 XssProtectionUtil 적용
- **투표 검증**: 1-10 범위 투표 점수 검증
- **등급 검증**: 올바른 등급 형식 검증

### 데이터 무결성
- **중복 방지**: 사용자별 루트당 1회 투표/스크랩
- **소프트 삭제**: 데이터 복구 가능한 삭제 방식
- **트랜잭션**: 데이터 일관성 보장

---

**📝 연계 파일**: step6-2b1_route_management_core.md와 함께 사용  
**완료일**: 2025-08-22  
**핵심 성과**: 난이도 투표 시스템 + 스크랩 관리 + 등급 변환 완성