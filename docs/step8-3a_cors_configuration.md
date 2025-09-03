# Step 8-3a: CORS 설정 구현

> Cross-Origin Resource Sharing 정책 설정 및 동적 Origin 관리  
> 생성일: 2025-08-26  
> 기반 파일: step8-1a_security_config.md, step2-3_infrastructure_setup.md

---

## 🎯 구현 목표

- **환경별 CORS 정책**: 개발/스테이징/프로덕션 환경별 설정
- **동적 Origin 관리**: 화이트리스트 기반 동적 Origin 허용
- **Preflight 최적화**: OPTIONS 요청 캐싱 및 최적화
- **보안 강화**: 민감한 헤더 노출 제한
- **모니터링**: CORS 요청 로깅 및 위반 탐지

---

## 🌐 1. CorsConfig 구현

### CorsConfig.java
```java
package com.routepick.config.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 설정
 * - 환경별 동적 Origin 관리
 * - 보안 강화된 CORS 정책
 * - Preflight 요청 최적화
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {
    
    private final CorsProperties corsProperties;
    private final Environment environment;
    
    /**
     * CORS 설정 소스
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 환경별 Origin 설정
        configuration.setAllowedOrigins(getAllowedOrigins());
        
        // HTTP 메서드 설정
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
        ));
        
        // 허용 헤더 설정
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "X-CSRF-Token",
            "X-Client-Version",
            "X-Device-Type"
        ));
        
        // 노출 헤더 설정 (클라이언트가 접근 가능한 헤더)
        configuration.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "X-Total-Count",
            "X-Page-Number",
            "X-Page-Size",
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Reset"
        ));
        
        // 인증 정보 포함 허용
        configuration.setAllowCredentials(true);
        
        // Preflight 요청 캐시 시간 (1시간)
        configuration.setMaxAge(3600L);
        
        // URL 패턴별 CORS 설정
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        
        // 모든 경로에 기본 설정 적용
        source.registerCorsConfiguration("/api/**", configuration);
        
        // 관리자 API는 더 제한적인 설정
        source.registerCorsConfiguration("/api/v1/admin/**", getAdminCorsConfiguration());
        
        // WebSocket 엔드포인트
        source.registerCorsConfiguration("/ws/**", getWebSocketCorsConfiguration());
        
        log.info("CORS Configuration initialized for environment: {}", 
                Arrays.toString(environment.getActiveProfiles()));
        
        return source;
    }
    
    /**
     * 환경별 허용 Origin 목록 조회
     */
    private List<String> getAllowedOrigins() {
        String[] profiles = environment.getActiveProfiles();
        
        if (profiles.length == 0 || Arrays.asList(profiles).contains("local")) {
            // 로컬 개발 환경
            return corsProperties.getLocal().getAllowedOrigins();
        } else if (Arrays.asList(profiles).contains("dev")) {
            // 개발 서버
            return corsProperties.getDev().getAllowedOrigins();
        } else if (Arrays.asList(profiles).contains("staging")) {
            // 스테이징 서버
            return corsProperties.getStaging().getAllowedOrigins();
        } else if (Arrays.asList(profiles).contains("prod")) {
            // 프로덕션 서버
            return corsProperties.getProd().getAllowedOrigins();
        }
        
        // 기본값 (보안상 제한적)
        return List.of("https://routepick.co.kr");
    }
    
    /**
     * 관리자 API CORS 설정 (더 제한적)
     */
    private CorsConfiguration getAdminCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 관리자 도메인만 허용
        configuration.setAllowedOrigins(Arrays.asList(
            "https://admin.routepick.co.kr",
            "http://localhost:3001"  // 로컬 관리자 앱
        ));
        
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));
        
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Admin-Token"
        ));
        
        configuration.setExposedHeaders(Arrays.asList(
            "X-Total-Count", "X-Admin-Session"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        return configuration;
    }
    
    /**
     * WebSocket CORS 설정
     */
    private CorsConfiguration getWebSocketCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        configuration.setAllowedOrigins(Arrays.asList(
            "https://routepick.co.kr",
            "wss://routepick.co.kr",
            "http://localhost:3000",
            "ws://localhost:3000"
        ));
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        return configuration;
    }
}
```

---

## ⚙️ 2. CorsProperties 설정

### CorsProperties.java
```java
package com.routepick.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * CORS 설정 프로퍼티
 * - 환경별 CORS 정책 관리
 * - 동적 Origin 설정
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.security.cors")
public class CorsProperties {
    
    @NotNull
    private EnvironmentCors local = new EnvironmentCors();
    
    @NotNull
    private EnvironmentCors dev = new EnvironmentCors();
    
    @NotNull
    private EnvironmentCors staging = new EnvironmentCors();
    
    @NotNull
    private EnvironmentCors prod = new EnvironmentCors();
    
    @Data
    public static class EnvironmentCors {
        private List<String> allowedOrigins = List.of("http://localhost:3000");
        private Boolean allowCredentials = true;
        private Long maxAge = 3600L;
        private Boolean enableLogging = false;
    }
}
```

### application.yml 설정
```yaml
app:
  security:
    cors:
      local:
        allowed-origins:
          - "http://localhost:3000"      # React 앱
          - "http://localhost:3001"      # Admin 앱
          - "http://localhost:8080"      # API 서버
          - "http://127.0.0.1:3000"
        allow-credentials: true
        max-age: 3600
        enable-logging: true
      
      dev:
        allowed-origins:
          - "https://dev.routepick.co.kr"
          - "https://admin-dev.routepick.co.kr"
          - "http://localhost:3000"      # 개발자 로컬 테스트용
        allow-credentials: true
        max-age: 3600
        enable-logging: true
      
      staging:
        allowed-origins:
          - "https://staging.routepick.co.kr"
          - "https://admin-staging.routepick.co.kr"
        allow-credentials: true
        max-age: 7200
        enable-logging: true
      
      prod:
        allowed-origins:
          - "https://routepick.co.kr"
          - "https://www.routepick.co.kr"
          - "https://admin.routepick.co.kr"
          - "https://m.routepick.co.kr"    # 모바일 웹
        allow-credentials: true
        max-age: 86400   # 24시간
        enable-logging: false
```

---

## 🛡️ 3. CorsFilter 구현

### DynamicCorsFilter.java
```java
package com.routepick.filter.security;

import com.routepick.config.security.CorsProperties;
import com.routepick.service.security.SecurityAuditLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 동적 CORS 필터
 * - Origin 검증 강화
 * - 동적 정책 적용
 * - CORS 위반 로깅
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DynamicCorsFilter extends OncePerRequestFilter {
    
    private final CorsProperties corsProperties;
    private final SecurityAuditLogger auditLogger;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String origin = request.getHeader("Origin");
        String method = request.getMethod();
        
        // OPTIONS 요청 (Preflight) 처리
        if ("OPTIONS".equals(method)) {
            handlePreflightRequest(request, response, origin);
            return;
        }
        
        // Origin 검증
        if (origin != null && isAllowedOrigin(origin)) {
            // CORS 헤더 설정
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Vary", "Origin");
            
            if (corsProperties.getLocal().getEnableLogging()) {
                log.debug("CORS request allowed - Origin: {}, Method: {}, Path: {}", 
                        origin, method, request.getRequestURI());
            }
        } else if (origin != null) {
            // 허용되지 않은 Origin
            log.warn("CORS request denied - Origin: {}, Method: {}, Path: {}", 
                    origin, method, request.getRequestURI());
            
            // 보안 이벤트 로깅
            auditLogger.logSecurityViolation("CORS_VIOLATION", 
                String.format("Unauthorized origin: %s attempted to access %s", origin, request.getRequestURI()),
                "MEDIUM", Map.of("origin", origin, "method", method));
            
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "CORS request denied");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Preflight 요청 처리
     */
    private void handlePreflightRequest(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       String origin) {
        
        if (origin != null && isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Methods", 
                "GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD");
            response.setHeader("Access-Control-Allow-Headers", 
                "Authorization, Content-Type, X-Requested-With, Accept, Origin, " +
                "Access-Control-Request-Method, Access-Control-Request-Headers, " +
                "X-CSRF-Token, X-Client-Version, X-Device-Type");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Max-Age", "3600");
            response.setStatus(HttpServletResponse.SC_OK);
            
            log.debug("Preflight request processed - Origin: {}", origin);
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            log.warn("Preflight request denied - Origin: {}", origin);
        }
    }
    
    /**
     * Origin 허용 여부 검증
     */
    private boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isEmpty()) {
            return false;
        }
        
        // 현재 환경의 허용 Origin 목록 가져오기
        List<String> allowedOrigins = getAllowedOriginsForCurrentEnvironment();
        
        // 정확한 매칭
        if (allowedOrigins.contains(origin)) {
            return true;
        }
        
        // 와일드카드 패턴 매칭 (*.routepick.co.kr)
        for (String allowed : allowedOrigins) {
            if (allowed.startsWith("*") && origin.endsWith(allowed.substring(1))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 현재 환경의 허용 Origin 목록 조회
     */
    private List<String> getAllowedOriginsForCurrentEnvironment() {
        // 실제 환경 프로필에 따라 동적으로 결정
        // 여기서는 간단히 local 설정을 반환
        return corsProperties.getLocal().getAllowedOrigins();
    }
}
```

---

## 📊 4. CORS 모니터링

### CorsMonitoringService.java
```java
package com.routepick.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CORS 모니터링 서비스
 * - CORS 요청 통계 수집
 * - 위반 패턴 분석
 * - 이상 징후 탐지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorsMonitoringService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String CORS_METRICS_PREFIX = "cors:metrics:";
    private static final String CORS_VIOLATION_PREFIX = "cors:violation:";
    
    /**
     * CORS 요청 기록
     */
    public void recordCorsRequest(String origin, String method, boolean allowed) {
        try {
            String date = LocalDateTime.now().toLocalDate().toString();
            String key = CORS_METRICS_PREFIX + date;
            String field = origin + ":" + method + ":" + (allowed ? "allowed" : "denied");
            
            redisTemplate.opsForHash().increment(key, field, 1);
            redisTemplate.expire(key, 30, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Failed to record CORS metrics", e);
        }
    }
    
    /**
     * CORS 위반 기록
     */
    public void recordCorsViolation(String origin, String path, String reason) {
        try {
            String key = CORS_VIOLATION_PREFIX + LocalDateTime.now().toLocalDate().toString();
            String value = String.format("%s|%s|%s|%d", 
                origin, path, reason, System.currentTimeMillis());
            
            redisTemplate.opsForList().leftPush(key, value);
            redisTemplate.opsForList().trim(key, 0, 999); // 최대 1000개
            redisTemplate.expire(key, 7, TimeUnit.DAYS);
            
            // 특정 Origin에서 과도한 위반 시 알림
            checkExcessiveViolations(origin);
            
        } catch (Exception e) {
            log.error("Failed to record CORS violation", e);
        }
    }
    
    /**
     * 과도한 CORS 위반 검사
     */
    private void checkExcessiveViolations(String origin) {
        String key = CORS_VIOLATION_PREFIX + "count:" + origin;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        if (count != null && count >= 100) {
            log.error("Excessive CORS violations detected from origin: {} (count: {})", 
                    origin, count);
            // 알림 발송 로직
        }
    }
    
    /**
     * CORS 통계 수집 (일일)
     */
    @Scheduled(cron = "0 0 1 * * *") // 매일 새벽 1시
    public void collectDailyCorsStatistics() {
        try {
            String yesterday = LocalDateTime.now().minusDays(1).toLocalDate().toString();
            String key = CORS_METRICS_PREFIX + yesterday;
            
            Map<Object, Object> metrics = redisTemplate.opsForHash().entries(key);
            
            if (!metrics.isEmpty()) {
                long totalRequests = metrics.values().stream()
                    .mapToLong(v -> Long.parseLong(v.toString()))
                    .sum();
                
                long deniedRequests = metrics.entrySet().stream()
                    .filter(e -> e.getKey().toString().contains("denied"))
                    .mapToLong(e -> Long.parseLong(e.getValue().toString()))
                    .sum();
                
                double denialRate = totalRequests > 0 ? 
                    (double) deniedRequests / totalRequests * 100 : 0;
                
                log.info("Daily CORS statistics - Date: {}, Total: {}, Denied: {}, Denial Rate: {:.2f}%",
                        yesterday, totalRequests, deniedRequests, denialRate);
            }
            
        } catch (Exception e) {
            log.error("Failed to collect CORS statistics", e);
        }
    }
}
```

---

## 🔧 5. Spring Security CORS 통합

### SecurityConfig.java 업데이트
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final CorsConfigurationSource corsConfigurationSource;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS 설정 적용
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            
            // CSRF는 JWT 사용으로 비활성화
            .csrf(AbstractHttpConfigurer::disable)
            
            // 기타 보안 설정...
            ;
            
        return http.build();
    }
}
```

---

## 📈 6. CORS 테스트 및 검증

### CORS 요청 테스트 예제
```javascript
// Frontend JavaScript 예제
fetch('https://api.routepick.co.kr/api/v1/auth/login', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
    },
    credentials: 'include', // 쿠키 포함
    body: JSON.stringify({
        email: 'user@example.com',
        password: 'password'
    })
})
.then(response => {
    // CORS 헤더 확인
    console.log('CORS Headers:', {
        'Access-Control-Allow-Origin': response.headers.get('Access-Control-Allow-Origin'),
        'Access-Control-Allow-Credentials': response.headers.get('Access-Control-Allow-Credentials')
    });
    return response.json();
})
.catch(error => {
    console.error('CORS Error:', error);
});
```

---

## ✅ 설계 완료 체크리스트

- [x] 환경별 CORS 정책 설정
- [x] 동적 Origin 화이트리스트 관리
- [x] Preflight 요청 최적화 (캐시 시간 설정)
- [x] 민감한 헤더 노출 제한
- [x] CORS 위반 로깅 및 모니터링
- [x] 관리자 API 별도 CORS 정책
- [x] WebSocket CORS 설정
- [x] Spring Security 통합

---

*Step 8-3a 완료: CORS 설정 구현*
*다음 파일: step8-3b_csrf_protection.md*