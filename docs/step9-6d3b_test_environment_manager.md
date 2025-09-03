# E2E 테스트 환경 관리 유틸리티

## 테스트 환경 관리 유틸리티

```java
package com.routepick.e2e.utils;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.TimeUnit;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

/**
 * E2E 테스트 환경 관리 유틸리티
 */
@Component
public class TestEnvironmentManager {
    
    private final TestRestTemplate restTemplate;
    
    public TestEnvironmentManager(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * 서비스들이 모두 준비될 때까지 대기
     */
    public void waitForServicesReady(String baseUrl, Duration timeout) {
        System.out.println("🔍 서비스 준비 상태 확인 중...");
        
        // 애플리케이션 헬스체크
        await().atMost(timeout.toMillis(), TimeUnit.MILLISECONDS)
               .pollInterval(2, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   try {
                       ResponseEntity<String> response = restTemplate.getForEntity(
                               baseUrl + "/actuator/health", String.class);
                       assert response.getStatusCode() == HttpStatus.OK;
                       assert response.getBody().contains("UP");
                       System.out.println("✅ 애플리케이션 서비스 준비 완료");
                   } catch (Exception e) {
                       System.out.println("⏳ 애플리케이션 서비스 준비 중... (" + e.getMessage() + ")");
                       throw new AssertionError("애플리케이션이 아직 준비되지 않음");
                   }
               });
        
        // 데이터베이스 연결 확인
        await().atMost(30, TimeUnit.SECONDS)
               .pollInterval(2, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   try {
                       ResponseEntity<String> response = restTemplate.getForEntity(
                               baseUrl + "/actuator/health/db", String.class);
                       assert response.getStatusCode() == HttpStatus.OK;
                       System.out.println("✅ 데이터베이스 연결 확인");
                   } catch (Exception e) {
                       System.out.println("⏳ 데이터베이스 연결 확인 중...");
                       throw new AssertionError("데이터베이스 연결 실패");
                   }
               });
        
        // Redis 연결 확인
        await().atMost(30, TimeUnit.SECONDS)
               .pollInterval(2, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   try {
                       ResponseEntity<String> response = restTemplate.getForEntity(
                               baseUrl + "/actuator/health/redis", String.class);
                       assert response.getStatusCode() == HttpStatus.OK;
                       System.out.println("✅ Redis 연결 확인");
                   } catch (Exception e) {
                       System.out.println("⏳ Redis 연결 확인 중...");
                       throw new AssertionError("Redis 연결 실패");
                   }
               });
        
        System.out.println("🎉 모든 서비스 준비 완료!");
    }
    
    /**
     * 컨테이너 상태 확인
     */
    public void verifyContainerHealth(GenericContainer<?> container, String serviceName) {
        if (!container.isRunning()) {
            throw new RuntimeException(serviceName + " 컨테이너가 실행되지 않음");
        }
        
        // 컨테이너 로그 확인
        String logs = container.getLogs();
        if (logs.contains("ERROR") || logs.contains("FATAL")) {
            System.err.println("⚠️ " + serviceName + " 컨테이너에서 오류 발견:");
            System.err.println(logs);
        } else {
            System.out.println("✅ " + serviceName + " 컨테이너 정상 동작");
        }
    }
    
    /**
     * 테스트 데이터베이스 초기화
     */
    public void initializeTestDatabase(String baseUrl) {
        System.out.println("🗄️ 테스트 데이터베이스 초기화 중...");
        
        try {
            // 테스트 데이터 초기화 엔드포인트 호출
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/api/test/init-data", null, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ 테스트 데이터 초기화 완료");
            } else {
                System.out.println("⚠️ 테스트 데이터 초기화 실패: " + response.getBody());
            }
        } catch (Exception e) {
            System.out.println("⚠️ 테스트 데이터 초기화 중 오류: " + e.getMessage());
        }
    }
    
    /**
     * 테스트 후 정리
     */
    public void cleanupTestData(String baseUrl) {
        System.out.println("🧹 테스트 데이터 정리 중...");
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/api/test/cleanup", null, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ 테스트 데이터 정리 완료");
            }
        } catch (Exception e) {
            System.out.println("⚠️ 테스트 데이터 정리 중 오류: " + e.getMessage());
        }
    }
    
    /**
     * 성능 모니터링 시작
     */
    public void startPerformanceMonitoring(String baseUrl) {
        try {
            restTemplate.postForEntity(baseUrl + "/actuator/metrics/start", null, String.class);
            System.out.println("📊 성능 모니터링 시작");
        } catch (Exception e) {
            System.out.println("⚠️ 성능 모니터링 시작 실패: " + e.getMessage());
        }
    }
    
    /**
     * 성능 리포트 생성
     */
    public String generatePerformanceReport(String baseUrl) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    baseUrl + "/actuator/metrics/report", String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("📈 성능 리포트 생성 완료");
                return response.getBody();
            }
        } catch (Exception e) {
            System.out.println("⚠️ 성능 리포트 생성 실패: " + e.getMessage());
        }
        return "성능 리포트 생성 실패";
    }
}
```

---

*분할된 파일: step9-6d3_e2e_helper_utils.md → step9-6d3b_test_environment_manager.md*  
*내용: E2E 테스트 환경 관리 유틸리티 (TestEnvironmentManager)*  
*라인 수: 170줄*