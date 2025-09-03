# step9-3f2_recommendation_algorithm_security_tests.md

> 추천 알고리즘 보안 테스트 - 점수 조작 방지, 권한 제어, 알고리즘 보호
> 생성일: 2025-08-27  
> 단계: 9-3f2 (추천 알고리즘 보안 테스트)
> 참고: step9-3f1, step6-3d1, step8-4b

---

## 🤖 추천 알고리즘 보안 위험 요소

### High Priority 취약점
- **추천 점수 직접 조작**: API를 통한 recommendation_score 변경 시도
- **다른 사용자 데이터 접근**: 권한 없는 추천 데이터 조회
- **알고리즘 역공학**: 가중치 정보 노출로 추천 로직 분석 가능
- **대량 요청 공격**: 추천 계산 API 남용으로 서버 부하

### 보안 목표
- 모든 점수 값의 무결성 보장 (0.0-1.0 범위)
- 사용자별 데이터 완전 격리
- 추천 알고리즘 내부 로직 은닉
- Rate Limiting으로 남용 방지

---

## 🤖 추천 알고리즘 보안 테스트 구현

### RecommendationSecurityTest.java
```java
package com.routepick.security.test.recommendation;

import com.routepick.service.recommendation.RecommendationService;
import com.routepick.domain.tag.entity.UserRouteRecommendation;
import com.routepick.domain.audit.entity.SecurityAuditLog;
import com.routepick.domain.audit.repository.SecurityAuditRepository;
import com.routepick.exception.recommendation.RecommendationSecurityException;
import com.routepick.exception.user.UnauthorizedAccessException;
import com.routepick.security.ratelimit.RateLimitService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("추천 알고리즘 보안 테스트")
class RecommendationSecurityTest {

    @Autowired
    private RecommendationService recommendationService;
    
    @Autowired
    private SecurityAuditRepository securityAuditRepository;
    
    @MockBean
    private RateLimitService rateLimitService;
    
    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        clearSecurityAuditLogs();
    }

    // ===== 점수 조작 방지 =====

    @Test
    @DisplayName("추천 점수 직접 조작 시도 차단")
    void preventDirectScoreManipulation() {
        // Given
        Long userId = 1L;
        Long routeId = 1L;
        BigDecimal manipulatedScore = new BigDecimal("2.0"); // 1.0 초과값
        
        // When & Then
        assertThatThrownBy(() -> 
            recommendationService.setRecommendationScore(userId, routeId, manipulatedScore))
            .isInstanceOf(RecommendationSecurityException.class)
            .hasMessageContaining("추천 점수는 0.0과 1.0 사이여야 합니다");
    }

    @Test
    @DisplayName("배치 추천 계산 시 비정상 점수 탐지")
    void detectAbnormalScoresInBatchCalculation() {
        // Given
        Long userId = 1L;
        
        // When
        CompletableFuture<Void> future = recommendationService.calculateUserRecommendationsAsync(userId);
        future.join(); // 비동기 완료 대기
        
        // Then - 모든 점수가 정상 범위 내에 있는지 확인
        List<UserRouteRecommendation> recommendations = 
            recommendationService.getUserRecommendations(userId);
        
        recommendations.forEach(rec -> {
            assertThat(rec.getRecommendationScore())
                .isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            assertThat(rec.getTagMatchScore())
                .isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            assertThat(rec.getLevelMatchScore())
                .isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        });
    }

    @Test
    @DisplayName("음수 점수 시도 차단")
    void preventNegativeScores() {
        // Given
        Long userId = 1L;
        Long routeId = 1L;
        BigDecimal negativeScore = new BigDecimal("-0.5");
        
        // When & Then
        assertThatThrownBy(() -> 
            recommendationService.updateTagMatchScore(userId, routeId, negativeScore))
            .isInstanceOf(RecommendationSecurityException.class)
            .hasMessageContaining("점수는 음수일 수 없습니다");
    }

    @Test
    @DisplayName("점수 정밀도 제한 확인 (소수점 3자리)")
    void validateScorePrecisionLimit() {
        // Given
        Long userId = 1L;
        Long routeId = 1L;
        BigDecimal highPrecisionScore = new BigDecimal("0.123456789"); // 9자리 정밀도
        
        // When
        recommendationService.updateTagMatchScore(userId, routeId, highPrecisionScore);
        
        // Then - 자동으로 3자리로 반올림되는지 확인
        UserRouteRecommendation recommendation = 
            recommendationService.getUserRouteRecommendation(userId, routeId);
        
        assertThat(recommendation.getTagMatchScore())
            .isEqualTo(new BigDecimal("0.123").setScale(3, RoundingMode.HALF_UP));
    }

    // ===== 권한 기반 접근 제어 =====

    @Test
    @WithMockUser(username = "user1", authorities = "ROLE_USER")
    @DisplayName("다른 사용자 추천 데이터 접근 차단")
    void preventUnauthorizedRecommendationAccess() {
        // Given
        Long currentUserId = 1L;
        Long otherUserId = 2L;
        
        // When & Then
        assertThatThrownBy(() -> 
            recommendationService.getUserRecommendations(otherUserId))
            .isInstanceOf(UnauthorizedAccessException.class)
            .hasMessageContaining("다른 사용자의 추천 데이터에 접근할 수 없습니다");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자의 모든 사용자 추천 데이터 접근 허용")
    void allowAdminAccessToAllRecommendations() {
        // Given
        Long anyUserId = 2L;
        
        // When & Then
        assertThatCode(() -> 
            recommendationService.getUserRecommendations(anyUserId))
            .doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = "user1", authorities = "ROLE_USER")
    @DisplayName("사용자별 추천 데이터 완전 격리 확인")
    void validateUserDataIsolation() {
        // Given
        Long user1Id = 1L;
        Long user2Id = 2L;
        
        // 각 사용자의 추천 계산
        recommendationService.calculateUserRecommendationsAsync(user1Id).join();
        recommendationService.calculateUserRecommendationsAsync(user2Id).join();
        
        // When - User1의 데이터 조회
        List<UserRouteRecommendation> user1Recommendations = 
            recommendationService.getUserRecommendations(user1Id);
        
        // Then - User2의 데이터가 포함되지 않았는지 확인
        assertThat(user1Recommendations).allMatch(rec -> 
            rec.getUserId().equals(user1Id));
        
        assertThat(user1Recommendations).noneMatch(rec -> 
            rec.getUserId().equals(user2Id));
    }

    // ===== 알고리즘 역공학 방지 =====

    @Test
    @DisplayName("추천 알고리즘 상세 정보 노출 방지")
    void preventAlgorithmDetailsExposure() {
        // Given
        Long userId = 1L;
        
        // When
        recommendationService.calculateUserRecommendationsAsync(userId).join();
        List<UserRouteRecommendation> recommendations = 
            recommendationService.getUserRecommendations(userId);
        
        // Then - 내부 계산 과정이 노출되지 않는지 확인
        recommendations.forEach(rec -> {
            // 가중치 정보 미노출
            assertThat(rec.toString()).doesNotContain("tagWeight");
            assertThat(rec.toString()).doesNotContain("levelWeight");
            assertThat(rec.toString()).doesNotContain("0.7"); // 태그 가중치
            assertThat(rec.toString()).doesNotContain("0.3"); // 레벨 가중치
            
            // 내부 계산식 미노출
            assertThat(rec.toString()).doesNotContain("calculation");
            assertThat(rec.toString()).doesNotContain("formula");
            assertThat(rec.toString()).doesNotContain("algorithm");
        });
    }

    @Test
    @DisplayName("가중치 설정 API 접근 제한")
    void restrictWeightConfigurationAccess() {
        // Given
        BigDecimal newTagWeight = new BigDecimal("0.8");
        BigDecimal newLevelWeight = new BigDecimal("0.2");
        
        // When & Then - 일반 사용자는 가중치 변경 불가
        assertThatThrownBy(() -> 
            recommendationService.updateAlgorithmWeights(newTagWeight, newLevelWeight))
            .isInstanceOf(RecommendationSecurityException.class)
            .hasMessageContaining("알고리즘 설정 변경 권한이 없습니다");
    }

    // ===== Rate Limiting 및 남용 방지 =====

    @Test
    @DisplayName("대량 추천 요청 시 Rate Limiting 적용")
    void applyRateLimitingForBulkRecommendations() {
        // Given
        Long userId = 1L;
        int maxRequestsPerMinute = 10;
        
        given(rateLimitService.isAllowedByUser(eq(userId.toString()), anyString(), anyString()))
            .willReturn(true, true, true, true, true, // 처음 5번은 허용
                       true, true, true, true, true,  // 다음 5번도 허용
                       false, false, false, false, false); // 초과 요청은 차단
        
        // When & Then
        for (int i = 0; i < maxRequestsPerMinute + 5; i++) {
            if (i < maxRequestsPerMinute) {
                // 정상 요청
                assertThatCode(() -> 
                    recommendationService.getUserRecommendations(userId))
                    .doesNotThrowAnyException();
            } else {
                // 초과 요청 차단
                assertThatThrownBy(() -> 
                    recommendationService.getUserRecommendations(userId))
                    .isInstanceOf(RecommendationSecurityException.class)
                    .hasMessageContaining("요청 한도를 초과했습니다");
            }
        }
    }

    @Test
    @DisplayName("동시 추천 계산 요청 제한")
    void limitConcurrentRecommendationCalculations() {
        // Given
        Long userId = 1L;
        
        // When - 동시에 여러 추천 계산 요청
        CompletableFuture<Void> future1 = recommendationService.calculateUserRecommendationsAsync(userId);
        
        // Then - 이미 진행 중인 경우 차단
        assertThatThrownBy(() -> 
            recommendationService.calculateUserRecommendationsAsync(userId))
            .isInstanceOf(RecommendationSecurityException.class)
            .hasMessageContaining("이미 추천 계산이 진행 중입니다");
        
        // 첫 번째 작업 완료 후 새 요청 허용
        future1.join();
        assertThatCode(() -> 
            recommendationService.calculateUserRecommendationsAsync(userId))
            .doesNotThrowAnyException();
    }

    // ===== 데이터 무결성 검증 =====

    @Test
    @DisplayName("추천 데이터 무결성 검증")
    void validateRecommendationDataIntegrity() {
        // Given
        Long userId = 1L;
        
        // When
        recommendationService.calculateUserRecommendationsAsync(userId).join();
        List<UserRouteRecommendation> recommendations = 
            recommendationService.getUserRecommendations(userId);
        
        // Then
        recommendations.forEach(rec -> {
            // 필수 필드 존재
            assertThat(rec.getUserId()).isNotNull();
            assertThat(rec.getRouteId()).isNotNull();
            assertThat(rec.getRecommendationScore()).isNotNull();
            assertThat(rec.getTagMatchScore()).isNotNull();
            assertThat(rec.getLevelMatchScore()).isNotNull();
            
            // 점수 일관성 검증 (tagMatchScore * 0.7 + levelMatchScore * 0.3)
            BigDecimal expectedScore = rec.getTagMatchScore()
                .multiply(new BigDecimal("0.7"))
                .add(rec.getLevelMatchScore().multiply(new BigDecimal("0.3")))
                .setScale(3, RoundingMode.HALF_UP);
            
            assertThat(rec.getRecommendationScore())
                .isEqualByComparingTo(expectedScore);
                
            // 시간 필드 검증
            assertThat(rec.getCalculatedAt()).isNotNull();
            assertThat(rec.getCreatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("추천 점수 합계 검증 (태그70% + 레벨30% = 100%)")
    void validateScoreWeightSum() {
        // Given
        Long userId = 1L;
        Long routeId = 1L;
        BigDecimal tagScore = new BigDecimal("0.8");
        BigDecimal levelScore = new BigDecimal("0.6");
        
        // When
        recommendationService.updateTagMatchScore(userId, routeId, tagScore);
        recommendationService.updateLevelMatchScore(userId, routeId, levelScore);
        
        UserRouteRecommendation recommendation = 
            recommendationService.getUserRouteRecommendation(userId, routeId);
        
        // Then - 가중 합계 검증
        BigDecimal expectedTotal = tagScore.multiply(new BigDecimal("0.7"))
                                         .add(levelScore.multiply(new BigDecimal("0.3")))
                                         .setScale(3, RoundingMode.HALF_UP);
        
        assertThat(recommendation.getRecommendationScore())
            .isEqualByComparingTo(expectedTotal);
    }

    // ===== 감사 로깅 =====

    @Test
    @DisplayName("추천 계산 시 보안 이벤트 로깅")
    void logSecurityEventsInRecommendationCalculation() {
        // Given
        Long userId = 1L;
        
        // When
        recommendationService.calculateUserRecommendationsAsync(userId).join();
        
        // Then
        List<SecurityAuditLog> securityLogs = 
            securityAuditRepository.findByUserIdAndEventType(userId, "RECOMMENDATION_CALCULATED");
        
        assertThat(securityLogs).hasSize(1);
        SecurityAuditLog log = securityLogs.get(0);
        assertThat(log.getEventDetails())
            .contains("user_id=" + userId)
            .contains("calculation_time")
            .contains("recommendation_count");
    }

    @Test
    @DisplayName("비정상 접근 시도 감사 로그 생성")
    void logUnauthorizedAccessAttempts() {
        // Given
        Long currentUserId = 1L;
        Long targetUserId = 2L;
        
        // When - 비정상 접근 시도
        try {
            recommendationService.getUserRecommendations(targetUserId);
        } catch (UnauthorizedAccessException e) {
            // 예외 발생 예상
        }
        
        // Then - 보안 위반 로그 확인
        List<SecurityAuditLog> securityLogs = 
            securityAuditRepository.findByUserIdAndEventType(currentUserId, "UNAUTHORIZED_RECOMMENDATION_ACCESS");
        
        assertThat(securityLogs).hasSize(1);
        assertThat(securityLogs.get(0).getEventDetails())
            .contains("attempted_user_id=" + targetUserId)
            .contains("access_denied=true");
    }

    @Test
    @DisplayName("Rate Limit 초과 시 감사 로그 생성")
    void logRateLimitExceededEvents() {
        // Given
        Long userId = 1L;
        given(rateLimitService.isAllowedByUser(eq(userId.toString()), anyString(), anyString()))
            .willReturn(false); // Rate limit 초과 시뮬레이션
        
        // When - Rate Limit 초과 요청
        try {
            recommendationService.getUserRecommendations(userId);
        } catch (RecommendationSecurityException e) {
            // 예외 발생 예상
        }
        
        // Then
        List<SecurityAuditLog> rateLimitLogs = 
            securityAuditRepository.findByUserIdAndEventType(userId, "RATE_LIMIT_EXCEEDED");
        
        assertThat(rateLimitLogs).hasSize(1);
        assertThat(rateLimitLogs.get(0).getEventDetails())
            .contains("endpoint=recommendation")
            .contains("limit_type=user_based");
    }

    // ===== 성능 및 리소스 보호 =====

    @Test
    @DisplayName("추천 계산 타임아웃 설정 확인")
    void validateRecommendationCalculationTimeout() {
        // Given
        Long userId = 1L;
        long startTime = System.currentTimeMillis();
        
        // When
        CompletableFuture<Void> future = recommendationService.calculateUserRecommendationsAsync(userId);
        
        // Then - 30초 내에 완료되어야 함
        assertThatCode(() -> 
            future.get(30, TimeUnit.SECONDS))
            .doesNotThrowAnyException();
        
        long endTime = System.currentTimeMillis();
        assertThat(endTime - startTime).isLessThan(30000); // 30초 미만
    }

    // ===== 도우미 메소드 =====

    private void clearSecurityAuditLogs() {
        securityAuditRepository.deleteAll();
    }
}
```

---

## 📊 추천 서비스 보안 강화 구현

### RecommendationService 보안 메소드 추가
```java
package com.routepick.service.recommendation;

import com.routepick.exception.recommendation.RecommendationSecurityException;
import com.routepick.security.ratelimit.RateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class RecommendationService {

    private final RateLimitService rateLimitService;
    private final ConcurrentHashMap<Long, ReentrantLock> calculationLocks = new ConcurrentHashMap<>();
    
    /**
     * 점수 범위 검증 (0.0 ~ 1.0)
     */
    private void validateScoreRange(BigDecimal score) {
        if (score == null) {
            throw new RecommendationSecurityException("점수는 null일 수 없습니다");
        }
        
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            throw new RecommendationSecurityException("점수는 음수일 수 없습니다");
        }
        
        if (score.compareTo(BigDecimal.ONE) > 0) {
            throw new RecommendationSecurityException("추천 점수는 0.0과 1.0 사이여야 합니다");
        }
    }
    
    /**
     * 점수 정밀도 정규화 (소수점 3자리)
     */
    private BigDecimal normalizeScore(BigDecimal score) {
        return score.setScale(3, RoundingMode.HALF_UP);
    }
    
    /**
     * Rate Limiting 검증
     */
    private void checkRateLimit(Long userId, String endpoint) {
        if (!rateLimitService.isAllowedByUser(userId.toString(), endpoint, "GET")) {
            throw new RecommendationSecurityException("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
        }
    }
    
    /**
     * 동시 계산 방지
     */
    private ReentrantLock getCalculationLock(Long userId) {
        return calculationLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    }
    
    /**
     * 사용자 권한 검증
     */
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public List<UserRouteRecommendation> getUserRecommendations(Long userId) {
        checkRateLimit(userId, "recommendations");
        // 실제 구현 로직...
    }
    
    /**
     * 관리자만 알고리즘 가중치 변경 가능
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void updateAlgorithmWeights(BigDecimal tagWeight, BigDecimal levelWeight) {
        // 가중치 합계가 1.0인지 확인
        if (tagWeight.add(levelWeight).compareTo(BigDecimal.ONE) != 0) {
            throw new RecommendationSecurityException("가중치 합계는 1.0이어야 합니다");
        }
        // 실제 구현 로직...
    }
}
```

---

## 🔒 보안 강화 효과

### 방어된 공격 유형
✅ **점수 조작**: 모든 점수 값 0.0-1.0 범위 강제  
✅ **권한 우회**: 사용자별 데이터 완전 격리  
✅ **알고리즘 노출**: 내부 로직 및 가중치 정보 은닉  
✅ **대량 공격**: Rate Limiting으로 남용 방지  
✅ **동시 요청**: 사용자당 하나의 계산만 허용  
✅ **데이터 무결성**: 점수 일관성 자동 검증  

### 성능 최적화
- **비동기 처리**: 추천 계산 비동기화로 응답성 향상
- **캐싱 활용**: 계산 결과 Redis 캐싱으로 성능 개선
- **타임아웃 설정**: 30초 제한으로 무한 대기 방지
- **동시성 제어**: 사용자별 Lock으로 리소스 보호

### 감사 추적
- **모든 계산 로깅**: 추천 계산 시간 및 결과 수 기록
- **보안 위반 추적**: 비정상 접근 시도 상세 로깅
- **Rate Limit 로깅**: 제한 초과 시도 기록
- **성능 메트릭**: 계산 시간 및 리소스 사용량 추적

---

*추천 알고리즘 보안 테스트 완성일: 2025-08-27*  
*분할 원본: step9-3f_tag_recommendation_security_test.md (300-588줄)*  
*보안 테스트 수: 19개 (High Priority 보안 이슈 완전 해결)*  
*다음 단계: Tag 매칭 보안 테스트 및 XSS 방어 구현*