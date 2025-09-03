# step9-4e2_message_social_login_security_tests.md

## 📋 메시지 스팸 방지 & 소셜 로그인 보안 테스트

### 🎯 목표
- 메시지 스팸 공격 방지 시스템 검증
- 소셜 로그인 토큰 보안 검증
- 대량 메시지 발송 남용 방어
- 이상 행위 패턴 탐지 및 대응

### 💬 테스트 범위
- **메시지 남용**: 발송 제한, 스팸 필터링, 내용 검증
- **토큰 보안**: 소셜 로그인 토큰 검증, 재사용 방지
- **패턴 분석**: 대량 발송, 신뢰도 점수, 응답률 분석
- **이상 탐지**: 위치/디바이스 변화, 다중 제공자 로그인

---

## 💬 메시지 스팸 방지 테스트

### MessageSpamPreventionTest.java
```java
package com.routepick.security.test.message;

import com.routepick.service.community.MessageService;
import com.routepick.util.SpamFilterUtil;
import com.routepick.util.RateLimitUtil;
import com.routepick.exception.message.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("메시지 스팸 방지 테스트")
class MessageSpamPreventionTest {

    private MessageSpamSecurityService messageSpamSecurityService;
    private RateLimitUtil rateLimitUtil;
    private SpamFilterUtil spamFilterUtil;

    @BeforeEach
    void setUp() {
        rateLimitUtil = mock(RateLimitUtil.class);
        spamFilterUtil = mock(SpamFilterUtil.class);
        messageSpamSecurityService = new MessageSpamSecurityService(rateLimitUtil, spamFilterUtil);
    }

    @Nested
    @DisplayName("메시지 발송 제한 테스트")
    class MessageRateLimitTest {

        @Test
        @DisplayName("1분 내 메시지 발송 제한 - 5회 초과 차단")
        void messageRateLimit_PerMinute() {
            // Given
            Long userId = 1L;
            String rateLimitKey = "message_send:" + userId;
            
            given(rateLimitUtil.isAllowed(rateLimitKey, 5, 60)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                messageSpamSecurityService.checkMessageSendLimit(userId))
                .isInstanceOf(MessageRateLimitException.class)
                .hasMessageContaining("1분 내 메시지 발송 한도를 초과했습니다");
        }

        @Test
        @DisplayName("1시간 내 메시지 발송 제한 - 50회 초과 차단")
        void messageRateLimit_PerHour() {
            // Given
            Long userId = 1L;
            String hourlyKey = "message_hourly:" + userId;
            
            given(rateLimitUtil.isAllowed("message_send:" + userId, 5, 60)).willReturn(true);
            given(rateLimitUtil.isAllowed(hourlyKey, 50, 3600)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                messageSpamSecurityService.checkMessageSendLimit(userId))
                .isInstanceOf(MessageRateLimitException.class)
                .hasMessageContaining("1시간 내 메시지 발송 한도를 초과했습니다");
        }

        @Test
        @DisplayName("동일한 수신자에게 메시지 제한 - 10분 내 3회 초과")
        void messageRateLimit_SameReceiver() {
            // Given
            Long senderId = 1L;
            Long receiverId = 2L;
            String sameReceiverKey = "message_same:" + senderId + ":" + receiverId;
            
            given(rateLimitUtil.isAllowed(anyString(), anyInt(), anyInt())).willReturn(true);
            given(rateLimitUtil.isAllowed(sameReceiverKey, 3, 600)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> 
                messageSpamSecurityService.checkSameReceiverLimit(senderId, receiverId))
                .isInstanceOf(MessageRateLimitException.class)
                .hasMessageContaining("동일한 수신자에게 너무 많은 메시지를 보냈습니다");
        }
    }

    @Nested
    @DisplayName("스팸 메시지 내용 필터링 테스트")
    class SpamContentFilterTest {

        @ParameterizedTest
        @ValueSource(strings = {
            "무료 체험! 지금 신청하세요!!!",
            "돈벌기 좋은 기회입니다. 연락주세요",
            "대출 가능합니다. 신용불량자도 OK",
            "성인용품 할인 판매",
            "도박 사이트 추천"
        })
        @DisplayName("스팸 키워드 탐지 - 차단")
        void detectSpamKeywords(String spamContent) {
            // Given
            given(spamFilterUtil.containsSpamKeywords(spamContent)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> 
                messageSpamSecurityService.validateMessageContent(spamContent))
                .isInstanceOf(SpamMessageException.class)
                .hasMessageContaining("스팸으로 의심되는 내용이 포함되어 있습니다");
        }

        @Test
        @DisplayName("동일 내용 반복 발송 탐지")
        void detectRepeatedContent() {
            // Given
            Long senderId = 1L;
            String content = "안녕하세요. 클라이밍 모임에 참여하실래요?";
            String contentHash = "hash_" + content.hashCode();
            
            given(spamFilterUtil.isRepeatedContent(senderId, contentHash, 10)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> 
                messageSpamSecurityService.checkRepeatedContent(senderId, content))
                .isInstanceOf(SpamMessageException.class)
                .hasMessageContaining("동일한 내용을 반복적으로 발송하고 있습니다");
        }

        @Test
        @DisplayName("URL 링크 개수 제한 - 3개 초과 차단")
        void limitUrlLinksInMessage() {
            // Given
            String contentWithManyUrls = "여기 확인해보세요 http://site1.com, http://site2.com, http://site3.com, http://site4.com";
            
            given(spamFilterUtil.countUrls(contentWithManyUrls)).willReturn(4);

            // When & Then
            assertThatThrownBy(() -> 
                messageSpamSecurityService.validateUrlCount(contentWithManyUrls))
                .isInstanceOf(SpamMessageException.class)
                .hasMessageContaining("메시지에 포함할 수 있는 링크는 최대 3개입니다");
        }

        @Test
        @DisplayName("연락처 정보 다량 포함 탐지")
        void detectMultipleContactInfo() {
            // Given
            String contentWithContacts = "연락주세요! 010-1234-5678, kakao: user123, telegram: @user456";
            
            given(spamFilterUtil.countContactInfo(contentWithContacts)).willReturn(3);

            // When & Then
            assertThatThrownBy(() -> 
                messageSpamSecurityService.validateContactInfoCount(contentWithContacts))
                .isInstanceOf(SpamMessageException.class)
                .hasMessageContaining("과도한 연락처 정보가 포함되어 있습니다");
        }
    }

    @Nested
    @DisplayName("메시지 스팸 패턴 분석 테스트")
    class SpamPatternAnalysisTest {

        @Test
        @DisplayName("대량 발송 패턴 탐지")
        void detectBulkSendingPattern() {
            // Given
            Long senderId = 1L;
            int messagesLastHour = 45; // 1시간 내 45개 메시지
            int uniqueRecipientsLastHour = 40; // 40명의 서로 다른 수신자
            
            given(spamFilterUtil.getMessagesCountLastHour(senderId)).willReturn(messagesLastHour);
            given(spamFilterUtil.getUniqueRecipientsLastHour(senderId)).willReturn(uniqueRecipientsLastHour);

            // When
            boolean isBulkSending = messageSpamSecurityService.detectBulkSendingPattern(senderId);

            // Then
            assertThat(isBulkSending).isTrue(); // 높은 메시지/수신자 비율
        }

        @Test
        @DisplayName("신규 가입자 타겟팅 패턴 탐지")
        void detectNewUserTargetingPattern() {
            // Given
            Long senderId = 1L;
            List<Long> recentRecipients = Arrays.asList(100L, 101L, 102L, 103L, 104L);
            
            given(spamFilterUtil.getRecentRecipients(senderId, 20)).willReturn(recentRecipients);
            given(spamFilterUtil.areNewUsers(recentRecipients, 7)).willReturn(true);

            // When
            boolean isTargetingNewUsers = messageSpamSecurityService.detectNewUserTargeting(senderId);

            // Then
            assertThat(isTargetingNewUsers).isTrue();
        }

        @Test
        @DisplayName("스팸 신고 기반 발신자 신뢰도 점수")
        void calculateSenderTrustScore() {
            // Given
            Long senderId = 1L;
            SpamReports reports = SpamReports.builder()
                    .totalReports(8) // 총 8번 신고
                    .recentReports(3) // 최근 7일 내 3번 신고
                    .validReports(6) // 검증된 신고 6건
                    .totalMessagesSent(1000) // 총 1000개 메시지 발송
                    .build();
            
            given(spamFilterUtil.getSpamReports(senderId)).willReturn(reports);

            // When
            double trustScore = messageSpamSecurityService.calculateSenderTrustScore(senderId);

            // Then
            assertThat(trustScore).isLessThan(0.5); // 낮은 신뢰도 (0.0 ~ 1.0)
        }

        @Test
        @DisplayName("메시지 응답률 기반 스팸 판정")
        void detectSpamByResponseRate() {
            // Given
            Long senderId = 1L;
            MessageStats stats = MessageStats.builder()
                    .sentMessages(100) // 100개 발송
                    .receivedReplies(2) // 2개 응답
                    .readMessages(20) // 20개 읽음
                    .deletedMessages(70) // 70개 삭제됨
                    .reportedMessages(5) // 5개 신고됨
                    .build();
            
            given(spamFilterUtil.getMessageStats(senderId)).willReturn(stats);

            // When
            boolean isSpamBehavior = messageSpamSecurityService.detectSpamByEngagement(senderId);

            // Then
            assertThat(isSpamBehavior).isTrue(); // 낮은 응답률 + 높은 삭제율 + 신고
        }
    }

    @Nested
    @DisplayName("메시지 스팸 제재 시스템 테스트")
    class MessageSanctionTest {

        @Test
        @DisplayName("메시지 발송 일시 정지 - 1시간")
        void temporaryMessageSendSuspension() {
            // Given
            Long senderId = 1L;
            int spamScore = 75; // 70-80점
            
            // When
            messageSpamSecurityService.applyMessageSanction(senderId, spamScore);

            // Then
            verify(spamFilterUtil).suspendMessageSend(senderId, 1); // 1시간 정지
        }

        @Test
        @DisplayName("메시지 기능 24시간 제한")
        void dailyMessageRestriction() {
            // Given
            Long senderId = 1L;
            int spamScore = 85; // 80-90점
            
            // When
            messageSpamSecurityService.applyMessageSanction(senderId, spamScore);

            // Then
            verify(spamFilterUtil).suspendMessageSend(senderId, 24); // 24시간 정지
            verify(spamFilterUtil).sendWarningNotification(senderId, "MESSAGE_SPAM_WARNING");
        }

        @Test
        @DisplayName("발송된 스팸 메시지 일괄 삭제")
        void cleanupSpamMessages() {
            // Given
            Long spamSenderId = 1L;
            List<Long> spamMessageIds = Arrays.asList(100L, 101L, 102L, 103L);
            
            given(spamFilterUtil.getSpamMessageIds(spamSenderId, 24)).willReturn(spamMessageIds);

            // When
            messageSpamSecurityService.cleanupSpamMessages(spamSenderId);

            // Then
            verify(spamFilterUtil).bulkDeleteMessages(spamMessageIds);
            verify(spamFilterUtil).notifyAffectedRecipients(spamMessageIds);
        }
    }
}
```

---

## 🔐 소셜 로그인 보안 테스트

### SocialLoginSecurityTest.java
```java
package com.routepick.security.test.auth;

import com.routepick.service.auth.SocialLoginService;
import com.routepick.common.enums.SocialProvider;
import com.routepick.exception.auth.SocialLoginSecurityException;
import com.routepick.util.TokenValidationUtil;
import com.routepick.util.SocialApiSecurityUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@DisplayName("소셜 로그인 보안 테스트")
class SocialLoginSecurityTest {

    private SocialLoginSecurityService socialLoginSecurityService;
    private TokenValidationUtil tokenValidationUtil;
    private SocialApiSecurityUtil socialApiSecurityUtil;

    @BeforeEach
    void setUp() {
        tokenValidationUtil = mock(TokenValidationUtil.class);
        socialApiSecurityUtil = mock(SocialApiSecurityUtil.class);
        socialLoginSecurityService = new SocialLoginSecurityService(
                tokenValidationUtil, socialApiSecurityUtil);
    }

    @Nested
    @DisplayName("소셜 토큰 검증 테스트")
    class SocialTokenValidationTest {

        @ParameterizedTest
        @EnumSource(SocialProvider.class)
        @DisplayName("제공자별 토큰 형식 검증")
        void validateTokenFormat(SocialProvider provider) {
            // Given
            String validToken = generateValidTokenForProvider(provider);
            String invalidToken = "invalid_token_format";
            
            given(tokenValidationUtil.isValidTokenFormat(provider, validToken)).willReturn(true);
            given(tokenValidationUtil.isValidTokenFormat(provider, invalidToken)).willReturn(false);

            // When & Then - 유효한 토큰
            assertThatCode(() -> 
                socialLoginSecurityService.validateTokenFormat(provider, validToken))
                .doesNotThrowAnyException();

            // When & Then - 무효한 토큰
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateTokenFormat(provider, invalidToken))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("토큰 형식이 올바르지 않습니다");
        }

        @Test
        @DisplayName("토큰 만료 시간 검증")
        void validateTokenExpiration() {
            // Given
            String expiredToken = "expired_access_token";
            
            given(tokenValidationUtil.isTokenExpired(expiredToken)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateTokenExpiration(expiredToken))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("토큰이 만료되었습니다");
        }

        @Test
        @DisplayName("토큰 스코프 권한 검증")
        void validateTokenScope() {
            // Given
            String token = "valid_access_token";
            List<String> requiredScopes = Arrays.asList("profile", "email");
            List<String> tokenScopes = Arrays.asList("profile"); // email 스코프 누락
            
            given(tokenValidationUtil.getTokenScopes(token)).willReturn(tokenScopes);

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateTokenScopes(token, requiredScopes))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("필요한 권한이 부족합니다");
        }

        @Test
        @DisplayName("토큰 재사용 공격 탐지")
        void detectTokenReplayAttack() {
            // Given
            String token = "reused_token";
            String clientIp = "192.168.1.100";
            
            given(socialApiSecurityUtil.isTokenAlreadyUsed(token)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateTokenReuse(token, clientIp))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("이미 사용된 토큰입니다");
        }
    }

    @Nested
    @DisplayName("소셜 API 응답 보안 검증 테스트")
    class SocialApiSecurityTest {

        @Test
        @DisplayName("소셜 API 응답 무결성 검증")
        void validateApiResponseIntegrity() {
            // Given
            String apiResponse = "{\"id\":\"12345\",\"email\":\"user@example.com\"}";
            String expectedSignature = "valid_signature";
            String actualSignature = "tampered_signature";
            
            given(socialApiSecurityUtil.calculateSignature(apiResponse)).willReturn(actualSignature);

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateResponseIntegrity(apiResponse, expectedSignature))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("API 응답이 변조되었습니다");
        }

        @Test
        @DisplayName("소셜 제공자 IP 화이트리스트 검증")
        void validateProviderIpWhitelist() {
            // Given
            SocialProvider provider = SocialProvider.GOOGLE;
            String suspiciousIp = "192.168.1.1"; // 구글이 아닌 IP
            String validIp = "142.250.191.14"; // 구글 IP 범위
            
            given(socialApiSecurityUtil.isValidProviderIp(provider, suspiciousIp)).willReturn(false);
            given(socialApiSecurityUtil.isValidProviderIp(provider, validIp)).willReturn(true);

            // When & Then - 의심스러운 IP
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateProviderIp(provider, suspiciousIp))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("신뢰할 수 없는 IP에서의 요청입니다");

            // 유효한 IP는 통과
            assertThatCode(() -> 
                socialLoginSecurityService.validateProviderIp(provider, validIp))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("소셜 사용자 정보 일관성 검증")
        void validateUserInfoConsistency() {
            // Given
            String previousUserInfo = "{\"id\":\"12345\",\"email\":\"user@example.com\",\"name\":\"John Doe\"}";
            String currentUserInfo = "{\"id\":\"12345\",\"email\":\"hacker@evil.com\",\"name\":\"John Doe\"}";
            
            given(socialApiSecurityUtil.hasSignificantChanges(previousUserInfo, currentUserInfo))
                    .willReturn(true);

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateUserInfoConsistency(
                        "12345", previousUserInfo, currentUserInfo))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("사용자 정보에 의심스러운 변경이 감지되었습니다");
        }
    }

    @Nested
    @DisplayName("소셜 계정 연결 보안 테스트")
    class SocialAccountLinkingSecurityTest {

        @Test
        @DisplayName("계정 연결 시도 횟수 제한")
        void limitAccountLinkingAttempts() {
            // Given
            Long userId = 1L;
            String rateLimitKey = "social_link:" + userId;
            
            given(socialApiSecurityUtil.isRateLimited(rateLimitKey, 5, 3600)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.checkLinkingRateLimit(userId))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("계정 연결 시도 횟수를 초과했습니다");
        }

        @Test
        @DisplayName("이미 다른 계정과 연결된 소셜 계정 탐지")
        void detectAlreadyLinkedSocialAccount() {
            // Given
            SocialProvider provider = SocialProvider.KAKAO;
            String socialId = "kakao_123456";
            Long existingUserId = 2L;
            Long requestUserId = 1L;
            
            given(socialApiSecurityUtil.findUserBySocialId(provider, socialId))
                    .willReturn(Optional.of(existingUserId));

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateSocialAccountAvailability(
                        provider, socialId, requestUserId))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("이미 다른 계정과 연결된 소셜 계정입니다");
        }

        @Test
        @DisplayName("동시 계정 연결 시도 탐지")
        void detectConcurrentLinkingAttempts() {
            // Given
            Long userId = 1L;
            SocialProvider provider = SocialProvider.NAVER;
            
            given(socialApiSecurityUtil.hasActiveLinkingProcess(userId, provider)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateNoConcurrentLinking(userId, provider))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("이미 진행 중인 계정 연결 프로세스가 있습니다");
        }
    }

    @Nested
    @DisplayName("소셜 로그인 이상 행위 탐지 테스트")
    class AbnormalBehaviorDetectionTest {

        @Test
        @DisplayName("비정상적인 로그인 패턴 탐지 - 짧은 시간 내 다중 제공자")
        void detectAbnormalLoginPattern() {
            // Given
            Long userId = 1L;
            List<SocialLoginAttempt> recentAttempts = Arrays.asList(
                    new SocialLoginAttempt(SocialProvider.GOOGLE, LocalDateTime.now().minusMinutes(1)),
                    new SocialLoginAttempt(SocialProvider.KAKAO, LocalDateTime.now().minusMinutes(2)),
                    new SocialLoginAttempt(SocialProvider.NAVER, LocalDateTime.now().minusMinutes(3)),
                    new SocialLoginAttempt(SocialProvider.FACEBOOK, LocalDateTime.now().minusMinutes(4))
            );
            
            given(socialApiSecurityUtil.getRecentLoginAttempts(userId, 5)).willReturn(recentAttempts);

            // When
            boolean isAbnormal = socialLoginSecurityService.detectAbnormalLoginPattern(userId);

            // Then
            assertThat(isAbnormal).isTrue(); // 5분 내 4개 제공자 로그인 시도
        }

        @Test
        @DisplayName("지리적 위치 이상 탐지 - IP 기반 위치 급변")
        void detectGeographicalAnomalies() {
            // Given
            Long userId = 1L;
            String previousIp = "121.134.83.123"; // 서울
            String currentIp = "8.8.8.8"; // 미국
            
            given(socialApiSecurityUtil.getLastLoginIp(userId)).willReturn(previousIp);
            given(socialApiSecurityUtil.calculateDistance(previousIp, currentIp)).willReturn(10000.0); // 10,000km

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateGeographicalConsistency(userId, currentIp))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("비정상적인 위치에서의 로그인 시도입니다");
        }

        @Test
        @DisplayName("디바이스 핑거프린트 변조 탐지")
        void detectDeviceFingerprintTampering() {
            // Given
            Long userId = 1L;
            String previousFingerprint = "chrome_119_windows_10_1920x1080";
            String currentFingerprint = "firefox_120_linux_1366x768";
            
            given(socialApiSecurityUtil.getLastDeviceFingerprint(userId)).willReturn(previousFingerprint);
            given(socialApiSecurityUtil.calculateFingerprintDifference(previousFingerprint, currentFingerprint))
                    .willReturn(0.8); // 80% 차이

            // When & Then
            assertThatThrownBy(() -> 
                socialLoginSecurityService.validateDeviceConsistency(userId, currentFingerprint))
                .isInstanceOf(SocialLoginSecurityException.class)
                .hasMessageContaining("인식되지 않은 디바이스에서의 접근입니다");
        }
    }

    // ===== 도우미 메소드 =====

    private String generateValidTokenForProvider(SocialProvider provider) {
        switch (provider) {
            case GOOGLE:
                return "ya29.a0ARrdaM-valid_google_token";
            case KAKAO:
                return "AAAANw-valid_kakao_token";
            case NAVER:
                return "AAAANRkvalid_naver_token";
            case FACEBOOK:
                return "EAABwzLixnjYBAvalid_facebook_token";
            default:
                return "valid_token";
        }
    }
}
```

## 📊 테스트 커버리지 요약

### 메시지 스팸 방지 테스트:
- ✅ Rate Limiting (1분 5회, 1시간 50회, 동일 수신자 제한)
- ✅ 스팸 콘텐츠 필터링 (키워드, 반복 내용, URL/연락처 제한)
- ✅ 패턴 분석 (대량 발송, 신규 사용자 타겟팅, 신뢰도 점수)
- ✅ 응답률 기반 스팸 판정 (낮은 응답률, 높은 삭제율)
- ✅ 제재 시스템 (1시간 → 24시간 정지, 스팸 메시지 삭제)

### 소셜 로그인 보안 테스트:
- ✅ 토큰 검증 (형식, 만료시간, 스코프, 재사용 방지)
- ✅ API 응답 보안 (무결성, IP 화이트리스트, 사용자 정보 일관성)
- ✅ 계정 연결 보안 (시도 횟수 제한, 중복 연결 방지, 동시 연결 탐지)
- ✅ 이상 행위 탐지 (다중 제공자 로그인, 지리적 이상, 디바이스 변조)

### 핵심 보안 기능:
- **메시지 보안**: 다층 Rate Limiting + 콘텐츠 필터링 + 패턴 분석
- **토큰 보안**: 4개 소셜 제공자별 토큰 형식 검증 + 재사용 방지
- **이상 탐지**: 위치/디바이스 변화 감지 + 로그인 패턴 분석
- **제재 시스템**: 단계별 제재 (정지 → 경고 → 삭제)