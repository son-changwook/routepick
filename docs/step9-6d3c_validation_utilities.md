# E2E 테스트 검증 유틸리티

## 검증 유틸리티

```java
package com.routepick.e2e.utils;

import com.routepick.auth.dto.response.AuthResponseDto;
import com.routepick.user.dto.response.UserDto;
import com.routepick.route.dto.response.RouteDto;
import com.routepick.community.dto.response.PostResponseDto;

import org.springframework.stereotype.Component;
import static org.assertj.core.api.Assertions.*;

/**
 * E2E 테스트 검증 유틸리티
 */
@Component
public class AssertionHelper {
    
    /**
     * 회원가입 응답 검증
     */
    public void assertSignupSuccess(AuthResponseDto response, String expectedEmail) {
        assertThat(response).isNotNull();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo(expectedEmail);
        assertThat(response.getAccessToken()).isNotNull().isNotEmpty();
        assertThat(response.getRefreshToken()).isNotNull().isNotEmpty();
        assertThat(response.getUser().getUserId()).isNotNull().isPositive();
    }
    
    /**
     * 로그인 응답 검증
     */
    public void assertLoginSuccess(AuthResponseDto response, String expectedEmail) {
        assertThat(response).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo(expectedEmail);
        assertThat(response.getAccessToken()).isNotNull().isNotEmpty();
        
        // 토큰 형식 검증 (JWT)
        assertThat(response.getAccessToken().split("\\.")).hasSize(3);
    }
    
    /**
     * 사용자 프로필 검증
     */
    public void assertUserProfile(UserDto user, String expectedEmail, String expectedNickname) {
        assertThat(user).isNotNull();
        assertThat(user.getEmail()).isEqualTo(expectedEmail);
        assertThat(user.getNickName()).isEqualTo(expectedNickname);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUserId()).isNotNull().isPositive();
    }
    
    /**
     * 루트 정보 검증
     */
    public void assertRouteValid(RouteDto route) {
        assertThat(route).isNotNull();
        assertThat(route.getRouteId()).isNotNull().isPositive();
        assertThat(route.getRouteName()).isNotNull().isNotEmpty();
        assertThat(route.getDifficulty()).isNotNull().matches("V[0-9]+");
        assertThat(route.getColor()).isNotNull().isNotEmpty();
    }
    
    /**
     * 포스트 검증
     */
    public void assertPostValid(PostResponseDto post, String expectedTitle) {
        assertThat(post).isNotNull();
        assertThat(post.getPostId()).isNotNull().isPositive();
        assertThat(post.getTitle()).isEqualTo(expectedTitle);
        assertThat(post.getContent()).isNotNull().isNotEmpty();
        assertThat(post.getCreatedAt()).isNotNull();
        assertThat(post.getViewCount()).isNotNull().isZero();
        assertThat(post.getLikeCount()).isNotNull().isZero();
    }
    
    /**
     * API 응답 시간 검증
     */
    public void assertResponseTime(long responseTimeMs, long maxAllowedMs, String operation) {
        assertThat(responseTimeMs)
                .withFailMessage("%s 응답 시간이 너무 깁니다: %dms (최대 허용: %dms)", 
                        operation, responseTimeMs, maxAllowedMs)
                .isLessThanOrEqualTo(maxAllowedMs);
        
        if (responseTimeMs > maxAllowedMs * 0.8) {
            System.out.printf("⚠️ %s 응답 시간 경고: %dms (한계의 80%% 초과)%n", 
                    operation, responseTimeMs);
        }
    }
    
    /**
     * 동시성 테스트 결과 검증
     */
    public void assertConcurrencyTestResults(int expectedCount, int successCount, int errorCount) {
        double successRate = (double) successCount / expectedCount;
        
        assertThat(successCount + errorCount).isEqualTo(expectedCount);
        assertThat(successRate)
                .withFailMessage("동시성 테스트 성공률이 낮습니다: %.2f%% (최소 95%% 필요)", 
                        successRate * 100)
                .isGreaterThanOrEqualTo(0.95);
        
        System.out.printf("✅ 동시성 테스트 결과: 성공 %d, 실패 %d, 성공률 %.2f%%%n", 
                successCount, errorCount, successRate * 100);
    }
    
    /**
     * 성능 기준 검증
     */
    public void assertPerformanceCriteria(String operation, long responseTime, double throughput) {
        // 응답 시간 기준
        switch (operation.toLowerCase()) {
            case "auth":
                assertResponseTime(responseTime, 1000, "인증");
                break;
            case "search":
                assertResponseTime(responseTime, 500, "검색");
                break;
            case "recommendation":
                assertResponseTime(responseTime, 2000, "추천");
                break;
            case "crud":
                assertResponseTime(responseTime, 1000, "CRUD");
                break;
            default:
                assertResponseTime(responseTime, 3000, operation);
        }
        
        // 처리량 기준 (TPS)
        if (throughput > 0) {
            assertThat(throughput)
                    .withFailMessage("%s 처리량이 기준에 못 미칩니다: %.2f TPS", operation, throughput)
                    .isGreaterThanOrEqualTo(getMinThroughput(operation));
        }
    }
    
    private double getMinThroughput(String operation) {
        return switch (operation.toLowerCase()) {
            case "auth" -> 10.0;      // 최소 10 TPS
            case "search" -> 50.0;    // 최소 50 TPS
            case "crud" -> 100.0;     // 최소 100 TPS
            default -> 20.0;          // 기본 20 TPS
        };
    }
}
```

---

*분할된 파일: step9-6d3_e2e_helper_utils.md → step9-6d3c_validation_utilities.md*  
*내용: E2E 테스트 검증 유틸리티 (AssertionHelper)*  
*라인 수: 149줄*