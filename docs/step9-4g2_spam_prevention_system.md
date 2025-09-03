# 스팸 방지 시스템 구현 및 테스트

## 개요
RoutePickr 플랫폼의 스팸 방지 시스템을 구현하고 테스트하는 종합적인 가이드입니다. 메시지, 게시글, 댓글, 사용자 등록 등 모든 영역에서 스팸을 탐지하고 차단하는 시스템을 다룹니다.

## 스팸 방지 시스템 아키텍처

### 1. 핵심 컴포넌트

```java
package com.routepick.security.spam;

import com.routepick.security.spam.dto.SpamDetectionResult;
import com.routepick.security.spam.dto.SpamAnalysisRequest;
import com.routepick.security.spam.enums.SpamType;
import com.routepick.security.spam.enums.ActionType;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 통합 스팸 방지 시스템
 * 
 * 주요 기능:
 * - 실시간 스팸 탐지
 * - 다단계 필터링 (키워드, 패턴, ML)
 * - 사용자 행동 분석
 * - 자동 차단 및 경고
 */
@Service
public class SpamPreventionService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private SpamDetectionEngine spamDetectionEngine;
    
    @Autowired
    private UserBehaviorAnalyzer userBehaviorAnalyzer;
    
    @Autowired
    private ContentFilterService contentFilterService;
    
    private static final String SPAM_COUNTER_KEY = "spam:counter:";
    private static final String BLOCKED_USER_KEY = "spam:blocked:";
    private static final int SPAM_THRESHOLD = 5; // 5회 이상 스팸으로 판정 시 차단
    
    /**
     * 통합 스팸 검사
     */
    public SpamDetectionResult analyzeContent(SpamAnalysisRequest request) {
        SpamDetectionResult result = new SpamDetectionResult();
        result.setUserId(request.getUserId());
        result.setContentType(request.getContentType());
        result.setAnalysisTime(LocalDateTime.now());
        
        // 1. 사용자 차단 상태 확인
        if (isUserBlocked(request.getUserId())) {
            result.setSpamScore(1.0);
            result.setSpamType(SpamType.BLOCKED_USER);
            result.setAction(ActionType.BLOCK);
            result.setReason("차단된 사용자");
            return result;
        }
        
        // 2. 빈도수 기반 검사
        double frequencyScore = analyzeFrequency(request);
        
        // 3. 내용 기반 검사
        double contentScore = analyzeContentSpam(request);
        
        // 4. 행동 패턴 검사
        double behaviorScore = analyzeBehaviorPattern(request);
        
        // 5. 최종 스팸 점수 계산 (가중 평균)
        double finalScore = (frequencyScore * 0.4) + (contentScore * 0.4) + (behaviorScore * 0.2);
        result.setSpamScore(finalScore);
        
        // 6. 액션 결정
        if (finalScore >= 0.8) {
            result.setSpamType(SpamType.HIGH_RISK);
            result.setAction(ActionType.BLOCK);
            incrementSpamCounter(request.getUserId());
        } else if (finalScore >= 0.6) {
            result.setSpamType(SpamType.MEDIUM_RISK);
            result.setAction(ActionType.WARN);
        } else if (finalScore >= 0.3) {
            result.setSpamType(SpamType.LOW_RISK);
            result.setAction(ActionType.MONITOR);
        } else {
            result.setSpamType(SpamType.SAFE);
            result.setAction(ActionType.ALLOW);
        }
        
        // 7. 스팸 카운터 관리
        if (result.getAction() == ActionType.BLOCK) {
            int spamCount = incrementSpamCounter(request.getUserId());
            if (spamCount >= SPAM_THRESHOLD) {
                blockUser(request.getUserId(), "반복적인 스팸 행위", 24); // 24시간 차단
            }
        }
        
        return result;
    }
    
    /**
     * 빈도수 기반 스팸 분석
     */
    private double analyzeFrequency(SpamAnalysisRequest request) {
        String key = "freq:" + request.getContentType() + ":" + request.getUserId();
        String hourKey = key + ":" + (System.currentTimeMillis() / 3600000); // 시간대별
        
        // 현재 시간대 요청 수 증가
        Long currentHourCount = redisTemplate.opsForValue().increment(hourKey);
        redisTemplate.expire(hourKey, 1, TimeUnit.HOURS);
        
        // 최근 24시간 요청 수 계산
        long totalRecentRequests = 0;
        for (int i = 0; i < 24; i++) {
            String recentKey = key + ":" + ((System.currentTimeMillis() / 3600000) - i);
            Long count = (Long) redisTemplate.opsForValue().get(recentKey);
            if (count != null) {
                totalRecentRequests += count;
            }
        }
        
        // 점수 계산 (시간당 빈도와 일간 총량을 고려)
        double hourlyScore = Math.min(currentHourCount / 10.0, 1.0); // 시간당 10개 초과 시 1.0
        double dailyScore = Math.min(totalRecentRequests / 100.0, 1.0); // 일간 100개 초과 시 1.0
        
        return Math.max(hourlyScore, dailyScore);
    }
    
    /**
     * 내용 기반 스팸 분석
     */
    private double analyzeContentSpam(SpamAnalysisRequest request) {
        String content = request.getContent();
        double score = 0.0;
        
        // 1. 금지 키워드 검사
        Set<String> bannedWords = contentFilterService.getBannedWords();
        for (String word : bannedWords) {
            if (content.toLowerCase().contains(word.toLowerCase())) {
                score += 0.3;
            }
        }
        
        // 2. URL 스팸 검사
        long urlCount = content.chars()
                .mapToObj(c -> (char) c)
                .map(String::valueOf)
                .reduce("", String::concat)
                .split("http[s]?://").length - 1;
        
        if (urlCount > 3) { // URL 3개 초과
            score += 0.4;
        } else if (urlCount > 1) {
            score += 0.2;
        }
        
        // 3. 반복 문자 검사
        if (hasExcessiveRepetition(content)) {
            score += 0.3;
        }
        
        // 4. 광고성 패턴 검사
        if (containsAdvertisingPattern(content)) {
            score += 0.5;
        }
        
        // 5. 특수문자 남용 검사
        long specialCharCount = content.chars()
                .filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))
                .count();
        
        if (specialCharCount > content.length() * 0.3) { // 특수문자 30% 초과
            score += 0.2;
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * 사용자 행동 패턴 분석
     */
    private double analyzeBehaviorPattern(SpamAnalysisRequest request) {
        Long userId = request.getUserId();
        double score = 0.0;
        
        // 1. 계정 생성 후 즉시 활동 (신규 계정 의심)
        if (userBehaviorAnalyzer.isNewAccountSuspicious(userId)) {
            score += 0.3;
        }
        
        // 2. 동일 내용 반복 게시
        if (userBehaviorAnalyzer.hasDuplicateContent(userId, request.getContent())) {
            score += 0.4;
        }
        
        // 3. 단시간 대량 활동
        if (userBehaviorAnalyzer.hasBurstActivity(userId)) {
            score += 0.3;
        }
        
        // 4. 다수 사용자에게 동일 메시지 발송
        if (userBehaviorAnalyzer.hasMassMessaging(userId)) {
            score += 0.5;
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * 사용자 차단
     */
    public void blockUser(Long userId, String reason, int hours) {
        String key = BLOCKED_USER_KEY + userId;
        Map<String, Object> blockInfo = Map.of(
                "reason", reason,
                "blockedAt", LocalDateTime.now().toString(),
                "expiresAt", LocalDateTime.now().plusHours(hours).toString()
        );
        
        redisTemplate.opsForHash().putAll(key, blockInfo);
        redisTemplate.expire(key, hours, TimeUnit.HOURS);
        
        // 로깅 및 알림
        logSpamAction(userId, ActionType.BLOCK, reason);
    }
    
    /**
     * 사용자 차단 상태 확인
     */
    public boolean isUserBlocked(Long userId) {
        String key = BLOCKED_USER_KEY + userId;
        return redisTemplate.hasKey(key);
    }
    
    /**
     * 스팸 카운터 증가
     */
    private int incrementSpamCounter(Long userId) {
        String key = SPAM_COUNTER_KEY + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 7, TimeUnit.DAYS); // 7일간 보관
        return count.intValue();
    }
    
    // Helper 메서드들
    private boolean hasExcessiveRepetition(String content) {
        // 연속 반복 문자 검사 (예: "aaaaaaa", "!!!!!!")
        return content.matches(".*(.)\\1{6,}.*");
    }
    
    private boolean containsAdvertisingPattern(String content) {
        String[] adPatterns = {
                "할인", "이벤트", "무료", "특가", "광고", "홍보",
                "방문하세요", "클릭하세요", "지금 신청",
                "discount", "free", "sale", "promo"
        };
        
        String lowerContent = content.toLowerCase();
        int adWordCount = 0;
        
        for (String pattern : adPatterns) {
            if (lowerContent.contains(pattern.toLowerCase())) {
                adWordCount++;
            }
        }
        
        return adWordCount >= 3; // 광고성 키워드 3개 이상
    }
    
    private void logSpamAction(Long userId, ActionType action, String reason) {
        // 실제 구현에서는 로그 시스템에 기록
        System.out.printf("[SPAM] User %d - Action: %s, Reason: %s%n", 
                userId, action, reason);
    }
}
```

### 2. 사용자 행동 분석기

```java
package com.routepick.security.spam;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 사용자 행동 패턴 분석기
 */
@Component
public class UserBehaviorAnalyzer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private UserService userService;
    
    /**
     * 신규 계정 의심 행동 검사
     */
    public boolean isNewAccountSuspicious(Long userId) {
        // 계정 생성일 확인
        LocalDateTime createdAt = userService.getUserCreatedDate(userId);
        Duration accountAge = Duration.between(createdAt, LocalDateTime.now());
        
        if (accountAge.toDays() > 7) {
            return false; // 7일 이상된 계정은 신규 계정 아님
        }
        
        // 신규 계정의 활동량 확인
        String activityKey = "activity:" + userId;
        Long activityCount = (Long) redisTemplate.opsForValue().get(activityKey);
        
        if (activityCount == null) {
            activityCount = 0L;
        }
        
        // 신규 계정이 하루에 50개 이상 활동 시 의심
        return accountAge.toHours() < 24 && activityCount > 50;
    }
    
    /**
     * 중복 내용 게시 검사
     */
    public boolean hasDuplicateContent(Long userId, String content) {
        String contentHash = String.valueOf(content.hashCode());
        String key = "content:" + userId + ":" + contentHash;
        
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        return count > 3; // 동일 내용을 1시간 내 3회 이상 게시
    }
    
    /**
     * 폭발적 활동 패턴 검사
     */
    public boolean hasBurstActivity(Long userId) {
        String key = "burst:" + userId;
        long currentMinute = System.currentTimeMillis() / 60000;
        String minuteKey = key + ":" + currentMinute;
        
        Long currentMinuteCount = redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, 1, TimeUnit.MINUTES);
        
        // 1분에 10개 이상 활동 시 폭발적 활동으로 판단
        return currentMinuteCount > 10;
    }
    
    /**
     * 대량 메시지 발송 검사
     */
    public boolean hasMassMessaging(Long userId) {
        String key = "mass_msg:" + userId;
        Long recipientCount = (Long) redisTemplate.opsForValue().get(key);
        
        if (recipientCount == null) {
            return false;
        }
        
        // 1시간 내 50명 이상에게 메시지 발송 시 대량 발송으로 판단
        return recipientCount > 50;
    }
    
    /**
     * 메시지 수신자 추가 (대량 발송 추적용)
     */
    public void addMessageRecipient(Long senderId, Long receiverId) {
        String key = "mass_msg:" + senderId;
        redisTemplate.opsForSet().add(key, receiverId);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
    }
}
```

## 스팸 방지 테스트

### 1. 스팸 탐지 테스트

```java
package com.routepick.security.spam;

import com.routepick.security.spam.dto.SpamAnalysisRequest;
import com.routepick.security.spam.dto.SpamDetectionResult;
import com.routepick.security.spam.enums.SpamType;
import com.routepick.security.spam.enums.ActionType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * 스팸 방지 시스템 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SpamPreventionSystemTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private SpamPreventionService spamPreventionService;
    
    @Autowired
    private UserBehaviorAnalyzer userBehaviorAnalyzer;
    
    private Long testUserId1 = 1L;
    private Long testUserId2 = 2L;
    
    @BeforeEach
    void setUp() {
        // 테스트 전 Redis 데이터 정리
        // redisTemplate.getConnectionFactory().getConnection().flushAll();
    }
    
    @Nested
    @DisplayName("내용 기반 스팸 탐지")
    class ContentBasedSpamDetectionTest {
        
        @Test
        @DisplayName("[탐지] 금지 키워드 포함 내용 차단")
        void detectBannedWords_BlockContent() {
            // given - 금지 키워드가 포함된 내용
            SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                    .userId(testUserId1)
                    .contentType("POST")
                    .content("이 사이트에서 돈을 벌 수 있는 방법을 알려드립니다! 지금 클릭하세요!")
                    .build();
            
            // when
            SpamDetectionResult result = spamPreventionService.analyzeContent(request);
            
            // then
            assertThat(result.getSpamScore()).isGreaterThan(0.5);
            assertThat(result.getAction()).isIn(ActionType.WARN, ActionType.BLOCK);
            assertThat(result.getSpamType()).isNotEqualTo(SpamType.SAFE);
        }
        
        @Test
        @DisplayName("[탐지] 과도한 URL 포함 내용 차단")
        void detectExcessiveUrls_BlockContent() {
            // given - 다수의 URL이 포함된 내용
            SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                    .userId(testUserId1)
                    .contentType("POST")
                    .content("여기를 방문하세요: http://site1.com http://site2.com http://site3.com http://site4.com")
                    .build();
            
            // when
            SpamDetectionResult result = spamPreventionService.analyzeContent(request);
            
            // then
            assertThat(result.getSpamScore()).isGreaterThan(0.4);
            assertThat(result.getAction()).isIn(ActionType.WARN, ActionType.BLOCK);
        }
        
        @Test
        @DisplayName("[탐지] 반복 문자 과다 사용 탐지")
        void detectExcessiveRepetition() {
            // given - 반복 문자가 과도하게 사용된 내용
            SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                    .userId(testUserId1)
                    .contentType("MESSAGE")
                    .content("와우우우우우우우우우!!! 정말 대박입니다!!!!!!!!!")
                    .build();
            
            // when
            SpamDetectionResult result = spamPreventionService.analyzeContent(request);
            
            // then
            assertThat(result.getSpamScore()).isGreaterThan(0.3);
            assertThat(result.getAction()).isIn(ActionType.MONITOR, ActionType.WARN);
        }
        
        @Test
        @DisplayName("[안전] 정상 내용 통과")
        void allowNormalContent() {
            // given - 정상적인 내용
            SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                    .userId(testUserId1)
                    .contentType("POST")
                    .content("오늘 암장에서 V4 루트를 완등했습니다. 정말 기뻐요!")
                    .build();
            
            // when
            SpamDetectionResult result = spamPreventionService.analyzeContent(request);
            
            // then
            assertThat(result.getSpamScore()).isLessThan(0.3);
            assertThat(result.getAction()).isEqualTo(ActionType.ALLOW);
            assertThat(result.getSpamType()).isEqualTo(SpamType.SAFE);
        }
    }
    
    @Nested
    @DisplayName("빈도 기반 스팸 탐지")
    class FrequencyBasedSpamDetectionTest {
        
        @Test
        @DisplayName("[탐지] 단시간 대량 게시 차단")
        void detectRapidPosting_BlockUser() {
            // given & when - 짧은 시간에 대량 게시 시뮬레이션
            for (int i = 0; i < 15; i++) {
                SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                        .userId(testUserId1)
                        .contentType("POST")
                        .content("테스트 게시글 #" + i)
                        .build();
                
                SpamDetectionResult result = spamPreventionService.analyzeContent(request);
                
                // 10번째 이후부터는 스팸으로 탐지되어야 함
                if (i >= 10) {
                    assertThat(result.getSpamScore()).isGreaterThan(0.6);
                    assertThat(result.getAction()).isIn(ActionType.WARN, ActionType.BLOCK);
                }
            }
        }
        
        @Test
        @DisplayName("[탐지] 시간당 빈도 제한 검증")
        void validateHourlyFrequencyLimit() {
            // given - 시간당 빈도 테스트
            String contentType = "MESSAGE";
            
            // when - 시간당 제한을 초과하는 요청
            for (int i = 0; i < 12; i++) {
                SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                        .userId(testUserId2)
                        .contentType(contentType)
                        .content("메시지 #" + i)
                        .build();
                
                SpamDetectionResult result = spamPreventionService.analyzeContent(request);
                
                // 10번 초과 시 빈도 점수 상승
                if (i > 10) {
                    assertThat(result.getSpamScore()).isGreaterThan(0.5);
                }
            }
        }
    }
    
    @Nested
    @DisplayName("사용자 행동 패턴 분석")
    class UserBehaviorAnalysisTest {
        
        @Test
        @DisplayName("[탐지] 신규 계정 의심 행동 탐지")
        void detectSuspiciousNewAccount() {
            // given - 신규 계정 의심 행동 시뮬레이션
            Long suspiciousUserId = 999L; // 신규 사용자
            
            // 신규 계정의 급작스런 대량 활동
            for (int i = 0; i < 60; i++) {
                SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                        .userId(suspiciousUserId)
                        .contentType("POST")
                        .content("신규 사용자 게시글 #" + i)
                        .build();
                
                SpamDetectionResult result = spamPreventionService.analyzeContent(request);
                
                // 일정 수준 이상의 활동에서 의심 점수 상승
                if (i > 50) {
                    assertThat(result.getSpamScore()).isGreaterThan(0.4);
                }
            }
        }
        
        @Test
        @DisplayName("[탐지] 중복 내용 반복 게시 탐지")
        void detectDuplicateContentPosting() {
            // given - 동일 내용 반복 게시
            String duplicateContent = "이것은 중복 테스트 내용입니다.";
            
            // when - 동일 내용을 여러 번 게시
            SpamDetectionResult lastResult = null;
            for (int i = 0; i < 5; i++) {
                SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                        .userId(testUserId1)
                        .contentType("POST")
                        .content(duplicateContent)
                        .build();
                
                lastResult = spamPreventionService.analyzeContent(request);
            }
            
            // then - 마지막 요청에서는 중복으로 인한 높은 스팸 점수
            assertThat(lastResult.getSpamScore()).isGreaterThan(0.6);
            assertThat(lastResult.getAction()).isIn(ActionType.WARN, ActionType.BLOCK);
        }
        
        @Test
        @DisplayName("[탐지] 폭발적 활동 패턴 탐지")
        void detectBurstActivityPattern() {
            // given & when - 1분 내 대량 활동
            for (int i = 0; i < 15; i++) {
                SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                        .userId(testUserId2)
                        .contentType("COMMENT")
                        .content("빠른 댓글 #" + i)
                        .build();
                
                SpamDetectionResult result = spamPreventionService.analyzeContent(request);
                
                // 10개 초과 시 폭발적 활동으로 탐지
                if (i >= 10) {
                    assertThat(result.getSpamScore()).isGreaterThan(0.4);
                }
            }
        }
        
        @Test
        @DisplayName("[탐지] 대량 메시지 발송 탐지")
        void detectMassMessaging() {
            // given - 대량 메시지 발송 시뮬레이션
            Long senderId = testUserId1;
            
            // 다수의 수신자에게 메시지 발송 기록
            for (int i = 1; i <= 60; i++) {
                userBehaviorAnalyzer.addMessageRecipient(senderId, (long) i);
            }
            
            // when - 메시지 내용 분석
            SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                    .userId(senderId)
                    .contentType("MESSAGE")
                    .content("안녕하세요! 이 메시지를 확인해주세요.")
                    .build();
            
            SpamDetectionResult result = spamPreventionService.analyzeContent(request);
            
            // then - 대량 발송으로 인한 높은 스팸 점수
            assertThat(result.getSpamScore()).isGreaterThan(0.5);
            assertThat(result.getAction()).isIn(ActionType.WARN, ActionType.BLOCK);
        }
    }
    
    @Nested
    @DisplayName("사용자 차단 시스템")
    class UserBlockingSystemTest {
        
        @Test
        @DisplayName("[차단] 반복적 스팸 행위로 사용자 자동 차단")
        void autoBlockUser_RepeatedSpam() {
            // given - 스팸 임계치 초과를 위한 반복 스팸 행위
            Long spammerId = 888L;
            
            // when - 5회 이상 스팸으로 탐지되는 행위 반복
            for (int i = 0; i < 7; i++) {
                SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                        .userId(spammerId)
                        .contentType("POST")
                        .content("스팸 테스트 광고글입니다. 지금 클릭하세요! http://spam.com")
                        .build();
                
                spamPreventionService.analyzeContent(request);
            }
            
            // then - 사용자가 자동으로 차단되었는지 확인
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                boolean isBlocked = spamPreventionService.isUserBlocked(spammerId);
                assertThat(isBlocked).isTrue();
            });
        }
        
        @Test
        @DisplayName("[차단] 차단된 사용자의 모든 활동 즉시 차단")
        void blockedUser_AllActivitiesBlocked() {
            // given - 사용자를 먼저 차단
            Long blockedUserId = 777L;
            spamPreventionService.blockUser(blockedUserId, "테스트 차단", 1);
            
            // when - 차단된 사용자의 활동 시도
            SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                    .userId(blockedUserId)
                    .contentType("POST")
                    .content("정상적인 내용입니다.")
                    .build();
            
            SpamDetectionResult result = spamPreventionService.analyzeContent(request);
            
            // then - 내용이 정상적이어도 즉시 차단
            assertThat(result.getSpamScore()).isEqualTo(1.0);
            assertThat(result.getSpamType()).isEqualTo(SpamType.BLOCKED_USER);
            assertThat(result.getAction()).isEqualTo(ActionType.BLOCK);
        }
        
        @Test
        @DisplayName("[해제] 차단 시간 경과 후 자동 해제")
        void autoUnblockUser_AfterTimeout() {
            // given - 짧은 시간 차단
            Long tempBlockedUserId = 666L;
            spamPreventionService.blockUser(tempBlockedUserId, "임시 차단 테스트", 0); // 즉시 만료
            
            // when & then - 시간 경과 후 차단 해제 확인
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                boolean isStillBlocked = spamPreventionService.isUserBlocked(tempBlockedUserId);
                assertThat(isStillBlocked).isFalse();
            });
            
            // 차단 해제 후 정상 활동 가능 확인
            SpamAnalysisRequest request = SpamAnalysisRequest.builder()
                    .userId(tempBlockedUserId)
                    .contentType("POST")
                    .content("차단 해제 후 정상 게시글")
                    .build();
            
            SpamDetectionResult result = spamPreventionService.analyzeContent(request);
            assertThat(result.getAction()).isEqualTo(ActionType.ALLOW);
        }
    }
    
    @Test
    @DisplayName("[종합] 스팸 방지 시스템 전체 시나리오")
    void comprehensive_SpamPreventionScenario() {
        System.out.println("=== 스팸 방지 시스템 종합 테스트 시작 ===");
        
        // 1. 정상 사용자 활동
        System.out.println("👤 1. 정상 사용자 활동 테스트");
        
        SpamAnalysisRequest normalRequest = SpamAnalysisRequest.builder()
                .userId(testUserId1)
                .contentType("POST")
                .content("오늘 새로운 클라이밍 루트에 도전했습니다. 정말 재미있었어요!")
                .build();
        
        SpamDetectionResult normalResult = spamPreventionService.analyzeContent(normalRequest);
        assertThat(normalResult.getAction()).isEqualTo(ActionType.ALLOW);
        assertThat(normalResult.getSpamType()).isEqualTo(SpamType.SAFE);
        System.out.println("✅ 정상 활동 허용");
        
        // 2. 경미한 스팸 활동 (경고 수준)
        System.out.println("⚠️ 2. 경미한 스팸 활동 테스트");
        
        SpamAnalysisRequest minorSpamRequest = SpamAnalysisRequest.builder()
                .userId(testUserId1)
                .contentType("POST")
                .content("이 링크를 확인해보세요! http://example.com 정말 좋은 정보입니다!")
                .build();
        
        SpamDetectionResult minorResult = spamPreventionService.analyzeContent(minorSpamRequest);
        assertThat(minorResult.getAction()).isIn(ActionType.MONITOR, ActionType.WARN);
        System.out.println("✅ 경미한 스팸 탐지 및 경고");
        
        // 3. 심각한 스팸 활동 (차단 수준)
        System.out.println("🚫 3. 심각한 스팸 활동 테스트");
        
        SpamAnalysisRequest severeSpamRequest = SpamAnalysisRequest.builder()
                .userId(testUserId2)
                .contentType("POST")
                .content("돈벌이 기회! 클릭하세요! http://spam1.com http://spam2.com http://spam3.com 할인 이벤트 무료!")
                .build();
        
        SpamDetectionResult severeResult = spamPreventionService.analyzeContent(severeSpamRequest);
        assertThat(severeResult.getAction()).isEqualTo(ActionType.BLOCK);
        assertThat(severeResult.getSpamType()).isEqualTo(SpamType.HIGH_RISK);
        System.out.println("✅ 심각한 스팸 탐지 및 차단");
        
        // 4. 반복 스팸으로 인한 사용자 자동 차단
        System.out.println("🔒 4. 반복 스팸 사용자 자동 차단 테스트");
        
        Long repeatSpammerId = 555L;
        for (int i = 0; i < 6; i++) {
            SpamAnalysisRequest repeatSpam = SpamAnalysisRequest.builder()
                    .userId(repeatSpammerId)
                    .contentType("MESSAGE")
                    .content("반복 스팸 메시지 #" + i + " 광고 클릭하세요!")
                    .build();
            spamPreventionService.analyzeContent(repeatSpam);
        }
        
        // 사용자 차단 확인
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            boolean isBlocked = spamPreventionService.isUserBlocked(repeatSpammerId);
            assertThat(isBlocked).isTrue();
        });
        System.out.println("✅ 반복 스팸 사용자 자동 차단 완료");
        
        // 5. 차단된 사용자의 모든 활동 차단
        System.out.println("🛡️ 5. 차단 사용자 활동 차단 테스트");
        
        SpamAnalysisRequest blockedUserRequest = SpamAnalysisRequest.builder()
                .userId(repeatSpammerId)
                .contentType("POST")
                .content("이제 정상적인 글을 써보겠습니다.")
                .build();
        
        SpamDetectionResult blockedResult = spamPreventionService.analyzeContent(blockedUserRequest);
        assertThat(blockedResult.getAction()).isEqualTo(ActionType.BLOCK);
        assertThat(blockedResult.getSpamType()).isEqualTo(SpamType.BLOCKED_USER);
        System.out.println("✅ 차단된 사용자 모든 활동 차단 확인");
        
        System.out.println("\n=== 📊 스팸 방지 시스템 테스트 결과 ===");
        System.out.println("✅ 정상 활동 허용: 정상 동작");
        System.out.println("⚠️ 경미한 스팸 탐지: 정상 동작");
        System.out.println("🚫 심각한 스팸 차단: 정상 동작");
        System.out.println("🔒 반복 스팸 자동 차단: 정상 동작");
        System.out.println("🛡️ 차단 사용자 보호: 정상 동작");
        System.out.println("\n=== 🎉 스팸 방지 시스템 모든 기능 정상 동작 확인 ===");
    }
}
```

## 스팸 방지 정책 및 기준

### 1. 탐지 기준
- **HIGH_RISK (0.8+)**: 즉시 차단
- **MEDIUM_RISK (0.6-0.8)**: 경고 및 모니터링 
- **LOW_RISK (0.3-0.6)**: 모니터링 강화
- **SAFE (0.3 미만)**: 정상 허용

### 2. 자동 차단 조건
- 5회 이상 스팸으로 탐지된 사용자
- 시간당 10개 이상 급작스런 게시
- 동일 내용 1시간 내 3회 이상 반복
- 50명 이상에게 대량 메시지 발송

### 3. 차단 기간 정책
- **1차 위반**: 24시간 차단
- **2차 위반**: 7일 차단  
- **3차 위반**: 30일 차단
- **4차 위반**: 영구 차단

### 4. 모니터링 및 알림
- 실시간 스팸 탐지 로그
- 관리자 대시보드 알림
- 사용자 신고 시스템 연동
- 주기적 스팸 패턴 분석

이 스팸 방지 시스템은 다층적 보안으로 플랫폼을 보호하며, 정상 사용자의 활동을 방해하지 않으면서 효과적으로 스팸을 차단합니다.