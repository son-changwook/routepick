# Step 8-2b: IP 기반 접근 제한 구현

> IP 화이트리스트, 지역 차단, 의심스러운 IP 자동 탐지 및 차단  
> 생성일: 2025-08-26  
> 기반 파일: step6-6a_api_log_service.md, step3-3b_security_features.md

---

## 🎯 구현 목표

- **IP 화이트리스트**: 관리자 API 접근 IP 제한
- **GeoIP 차단**: 특정 국가/지역 접근 제한
- **자동 IP 차단**: 의심스러운 활동 패턴 탐지
- **브루트 포스 방어**: 로그인 실패 기반 IP 차단
- **실시간 모니터링**: IP 기반 보안 이벤트 추적

---

## 🛡️ 1. IpWhitelistFilter 구현

### IpWhitelistFilter.java
```java
package com.routepick.filter;

import com.routepick.service.security.IpAccessControlService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * IP 화이트리스트 필터
 * - 관리자 API IP 제한
 * - 국가별 접근 제한
 * - 의심스러운 IP 차단
 */
@Slf4j
@Component
@Order(2) // Rate Limiting 이후 실행
@RequiredArgsConstructor
public class IpWhitelistFilter extends OncePerRequestFilter {
    
    private final IpAccessControlService ipAccessControlService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String clientIp = extractClientIp(request);
        String requestUri = request.getRequestURI();
        
        try {
            // 1. 차단된 IP 검사
            if (ipAccessControlService.isBlocked(clientIp)) {
                log.warn("Blocked IP attempted access: {} -> {}", clientIp, requestUri);
                sendForbiddenResponse(response, "IP address is blocked");
                return;
            }
            
            // 2. 관리자 API 화이트리스트 검사
            if (requestUri.startsWith("/api/v1/admin/")) {
                if (!ipAccessControlService.isWhitelisted(clientIp)) {
                    log.warn("Non-whitelisted IP attempted admin access: {} -> {}", clientIp, requestUri);
                    sendForbiddenResponse(response, "Access denied from this IP address");
                    return;
                }
            }
            
            // 3. 지역 기반 접근 제한
            if (!ipAccessControlService.isCountryAllowed(clientIp)) {
                log.warn("Geo-blocked IP attempted access: {} -> {}", clientIp, requestUri);
                sendForbiddenResponse(response, "Access denied from this region");
                return;
            }
            
            // 4. 접근 로그 기록
            ipAccessControlService.logAccess(clientIp, requestUri, request.getMethod());
            
        } catch (Exception e) {
            log.error("Error in IP access control: ", e);
            // 보안 필터 오류 시에도 요청 계속 진행 (가용성 우선)
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * 클라이언트 IP 추출 (step6-6a 기반 강화)
     */
    private String extractClientIp(HttpServletRequest request) {
        String[] headers = {
            "CF-Connecting-IP",     // Cloudflare
            "X-Forwarded-For",      // 일반적인 프록시
            "X-Real-IP",            // Nginx
            "X-Cluster-Client-IP",  // 클러스터
            "Proxy-Client-IP",      // Apache
            "WL-Proxy-Client-IP",   // WebLogic
            "HTTP_CLIENT_IP",       // HTTP 클라이언트
            "HTTP_X_FORWARDED_FOR"  // HTTP X-Forwarded-For
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 여러 IP가 있는 경우 첫 번째 IP 사용
                String firstIp = ip.split(",")[0].trim();
                if (isValidIp(firstIp)) {
                    return firstIp;
                }
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * IP 주소 유효성 검사
     */
    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        
        // IPv4 기본 검증
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 403 Forbidden 응답 전송
     */
    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String jsonResponse = String.format(
            "{\"success\":false,\"errorCode\":\"FORBIDDEN\",\"message\":\"%s\"}", 
            message
        );
        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
```

---

## 🌐 2. IpAccessControlService 구현

### IpAccessControlService.java
```java
package com.routepick.service.security;

import com.routepick.service.system.ApiLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * IP 접근 제어 서비스
 * - IP 화이트리스트/블랙리스트 관리
 * - 지역 기반 접근 제한
 * - 의심스러운 IP 자동 탐지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpAccessControlService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApiLogService apiLogService;
    private final GeoIpService geoIpService;
    
    // Redis 키 패턴
    private static final String BLOCKED_IP_PREFIX = "security:blocked_ip:";
    private static final String WHITELIST_PREFIX = "security:whitelist:";
    private static final String SUSPICIOUS_IP_PREFIX = "security:suspicious:";
    private static final String ACCESS_LOG_PREFIX = "security:access_log:";
    
    // 한국 IP 대역 (간략화)
    private static final Set<String> KOREAN_IP_RANGES = Set.of(
        "1.201.0.0/16", "1.208.0.0/12", "1.224.0.0/11",
        "14.0.0.0/8", "27.0.0.0/8", "39.7.0.0/16",
        "49.161.0.0/16", "58.120.0.0/13", "59.5.0.0/16",
        "61.250.0.0/16", "112.175.0.0/16", "115.68.0.0/14",
        "117.111.0.0/16", "118.220.0.0/14", "121.78.0.0/15",
        "175.192.0.0/10", "180.64.0.0/11", "203.236.0.0/15"
    );
    
    /**
     * IP 차단 여부 확인
     */
    @Cacheable(value = "ipBlacklist", key = "#clientIp")
    public boolean isBlocked(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }
        
        // 영구 차단 IP 확인
        String permanentKey = BLOCKED_IP_PREFIX + "permanent:" + clientIp;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(permanentKey))) {
            return true;
        }
        
        // 임시 차단 IP 확인
        String temporaryKey = BLOCKED_IP_PREFIX + "temporary:" + clientIp;
        return Boolean.TRUE.equals(redisTemplate.hasKey(temporaryKey));
    }
    
    /**
     * IP 화이트리스트 여부 확인
     */
    @Cacheable(value = "ipWhitelist", key = "#clientIp")
    public boolean isWhitelisted(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }
        
        // 로컬 IP는 항상 허용
        if (isLocalIp(clientIp)) {
            return true;
        }
        
        // 화이트리스트 확인
        String whitelistKey = WHITELIST_PREFIX + clientIp;
        return Boolean.TRUE.equals(redisTemplate.hasKey(whitelistKey));
    }
    
    /**
     * 국가 기반 접근 허용 여부
     */
    @Cacheable(value = "geoAccess", key = "#clientIp")
    public boolean isCountryAllowed(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return true;
        }
        
        // 로컬 IP는 항상 허용
        if (isLocalIp(clientIp)) {
            return true;
        }
        
        try {
            // GeoIP를 통한 국가 확인
            String countryCode = geoIpService.getCountryCode(clientIp);
            
            // 허용 국가 목록 (한국, 미국, 일본, 캐나다, 호주, 영국)
            Set<String> allowedCountries = Set.of("KR", "US", "JP", "CA", "AU", "GB");
            
            return countryCode != null && allowedCountries.contains(countryCode);
            
        } catch (Exception e) {
            log.warn("GeoIP lookup failed for IP: {}, allowing by default", clientIp);
            // GeoIP 서비스 오류 시 기본 허용 (가용성 우선)
            return true;
        }
    }
    
    /**
     * IP 임시 차단
     */
    public void blockIpTemporarily(String clientIp, Duration duration, String reason) {
        String key = BLOCKED_IP_PREFIX + "temporary:" + clientIp;
        redisTemplate.opsForValue().set(key, reason, duration.getSeconds(), TimeUnit.SECONDS);
        
        log.warn("IP temporarily blocked: {} for {} - {}", clientIp, duration, reason);
        
        // 보안 이벤트 로그
        logSecurityEvent(clientIp, "IP_BLOCKED_TEMPORARY", reason);
    }
    
    /**
     * IP 영구 차단
     */
    public void blockIpPermanently(String clientIp, String reason) {
        String key = BLOCKED_IP_PREFIX + "permanent:" + clientIp;
        redisTemplate.opsForValue().set(key, reason);
        
        log.error("IP permanently blocked: {} - {}", clientIp, reason);
        
        // 보안 이벤트 로그
        logSecurityEvent(clientIp, "IP_BLOCKED_PERMANENT", reason);
    }
    
    /**
     * IP 화이트리스트 추가
     */
    public void addToWhitelist(String clientIp, String description) {
        String key = WHITELIST_PREFIX + clientIp;
        redisTemplate.opsForValue().set(key, description);
        
        log.info("IP added to whitelist: {} - {}", clientIp, description);
    }
    
    /**
     * 의심스러운 IP 활동 기록
     */
    public void recordSuspiciousActivity(String clientIp, String activity, String details) {
        String key = SUSPICIOUS_IP_PREFIX + clientIp;
        String value = String.format("%s:%s:%d", activity, details, System.currentTimeMillis());
        
        // 최근 24시간 의심 활동 기록
        redisTemplate.opsForList().leftPush(key, value);
        redisTemplate.opsForList().trim(key, 0, 99); // 최대 100개까지
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
        
        log.warn("Suspicious activity recorded: {} - {} ({})", clientIp, activity, details);
        
        // 일정 횟수 이상 의심 활동 시 자동 차단
        checkAutoBlock(clientIp);
    }
    
    /**
     * 접근 로그 기록
     */
    @Async
    public void logAccess(String clientIp, String uri, String method) {
        try {
            String key = ACCESS_LOG_PREFIX + clientIp + ":" + 
                        LocalDateTime.now().toLocalDate().toString();
            
            String logEntry = String.format("%s:%s:%d", method, uri, System.currentTimeMillis());
            
            redisTemplate.opsForList().leftPush(key, logEntry);
            redisTemplate.opsForList().trim(key, 0, 999); // 최대 1000개까지
            redisTemplate.expire(key, 7, TimeUnit.DAYS); // 7일 보관
            
        } catch (Exception e) {
            log.warn("Failed to log access for IP: {}", clientIp, e);
        }
    }
    
    /**
     * 자동 차단 검사
     */
    private void checkAutoBlock(String clientIp) {
        String key = SUSPICIOUS_IP_PREFIX + clientIp;
        Long suspiciousCount = redisTemplate.opsForList().size(key);
        
        if (suspiciousCount != null && suspiciousCount >= 10) {
            // 24시간 내 10회 이상 의심 활동 시 1시간 차단
            blockIpTemporarily(clientIp, Duration.ofHours(1), 
                "Automatic block due to suspicious activities: " + suspiciousCount);
        } else if (suspiciousCount != null && suspiciousCount >= 20) {
            // 24시간 내 20회 이상 의심 활동 시 24시간 차단
            blockIpTemporarily(clientIp, Duration.ofHours(24), 
                "Extended block due to repeated suspicious activities: " + suspiciousCount);
        }
    }
    
    /**
     * 로컬 IP 여부 확인
     */
    private boolean isLocalIp(String ip) {
        if (ip == null) return false;
        
        return ip.equals("127.0.0.1") || 
               ip.equals("::1") || 
               ip.equals("0:0:0:0:0:0:0:1") ||
               ip.startsWith("192.168.") ||
               ip.startsWith("10.") ||
               (ip.startsWith("172.") && isPrivateClassB(ip));
    }
    
    /**
     * 클래스 B 사설 IP 확인
     */
    private boolean isPrivateClassB(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            }
        } catch (NumberFormatException e) {
            // 무시
        }
        return false;
    }
    
    /**
     * 보안 이벤트 로그
     */
    private void logSecurityEvent(String clientIp, String eventType, String details) {
        try {
            // step6-6a ApiLogService 활용
            apiLogService.logError("SECURITY_EVENT", "IP_ACCESS_CONTROL", 
                String.format("IP: %s, Event: %s, Details: %s", clientIp, eventType, details), 
                "IpAccessControlService");
        } catch (Exception e) {
            log.error("Failed to log security event", e);
        }
    }
}
```

---

## 🌍 3. GeoIP 서비스 구현

### GeoIpService.java
```java
package com.routepick.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * GeoIP 조회 서비스
 * - IP 기반 국가/지역 정보 조회
 * - 외부 API 연동 (ip-api.com 등)
 */
@Slf4j
@Service
public class GeoIpService {
    
    private final RestTemplate restTemplate;
    
    public GeoIpService() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * IP의 국가 코드 조회 (캐시 적용)
     */
    @Cacheable(value = "geoIpCache", key = "#clientIp")
    public String getCountryCode(String clientIp) {
        try {
            // ip-api.com 무료 API 사용 (상용 환경에서는 유료 서비스 권장)
            String url = String.format("http://ip-api.com/json/%s?fields=countryCode", clientIp);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("countryCode")) {
                String countryCode = (String) response.get("countryCode");
                log.debug("GeoIP lookup successful: {} -> {}", clientIp, countryCode);
                return countryCode;
            }
            
        } catch (Exception e) {
            log.warn("GeoIP lookup failed for IP: {}", clientIp, e);
        }
        
        return null;
    }
    
    /**
     * IP의 상세 위치 정보 조회
     */
    @Cacheable(value = "geoIpDetailCache", key = "#clientIp")
    public GeoIpInfo getGeoInfo(String clientIp) {
        try {
            String url = String.format("http://ip-api.com/json/%s", clientIp);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && "success".equals(response.get("status"))) {
                return GeoIpInfo.builder()
                    .ip(clientIp)
                    .country((String) response.get("country"))
                    .countryCode((String) response.get("countryCode"))
                    .region((String) response.get("regionName"))
                    .city((String) response.get("city"))
                    .timezone((String) response.get("timezone"))
                    .isp((String) response.get("isp"))
                    .build();
            }
            
        } catch (Exception e) {
            log.warn("GeoIP detailed lookup failed for IP: {}", clientIp, e);
        }
        
        return null;
    }
    
    /**
     * 한국 IP 여부 확인 (빠른 검사)
     */
    public boolean isKoreanIp(String clientIp) {
        try {
            // 간단한 한국 대역 검사
            if (clientIp.startsWith("1.201.") || 
                clientIp.startsWith("1.208.") ||
                clientIp.startsWith("14.") ||
                clientIp.startsWith("27.") ||
                clientIp.startsWith("175.") ||
                clientIp.startsWith("180.")) {
                return true;
            }
            
            // GeoIP 서비스 조회
            String countryCode = getCountryCode(clientIp);
            return "KR".equals(countryCode);
            
        } catch (Exception e) {
            log.warn("Korean IP check failed for: {}", clientIp, e);
            return false;
        }
    }
}
```

### GeoIpInfo.java
```java
package com.routepick.service.security;

import lombok.Builder;
import lombok.Getter;

/**
 * GeoIP 정보 DTO
 */
@Getter
@Builder
public class GeoIpInfo {
    private String ip;
    private String country;
    private String countryCode;
    private String region;
    private String city;
    private String timezone;
    private String isp;
    
    public boolean isKorea() {
        return "KR".equals(countryCode);
    }
    
    public boolean isAllowedCountry() {
        if (countryCode == null) return false;
        
        // 허용 국가 목록
        return countryCode.equals("KR") ||  // 한국
               countryCode.equals("US") ||  // 미국
               countryCode.equals("JP") ||  // 일본
               countryCode.equals("CA") ||  // 캐나다
               countryCode.equals("AU") ||  // 호주
               countryCode.equals("GB");    // 영국
    }
}
```

---

## 🚨 4. SecurityEventListener 구현

### SecurityEventListener.java
```java
package com.routepick.listener;

import com.routepick.service.security.IpAccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 보안 이벤트 리스너
 * - 로그인 실패 이벤트 처리
 * - 브루트 포스 공격 탐지
 * - 자동 계정/IP 잠금
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventListener {
    
    private final IpAccessControlService ipAccessControlService;
    
    // IP별 로그인 실패 카운터
    private final ConcurrentHashMap<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
    
    // 브루트 포스 임계치
    private static final int BRUTE_FORCE_THRESHOLD = 5;
    private static final int SEVERE_BRUTE_FORCE_THRESHOLD = 10;
    
    /**
     * 로그인 실패 이벤트 처리
     */
    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String clientIp = extractClientIp(event);
        String username = event.getAuthentication().getName();
        
        if (clientIp != null) {
            // IP별 실패 횟수 증가
            AtomicInteger counter = failureCounters.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
            int failureCount = counter.incrementAndGet();
            
            log.warn("Authentication failure: username={}, ip={}, failureCount={}", 
                    username, clientIp, failureCount);
            
            // 의심스러운 활동 기록
            ipAccessControlService.recordSuspiciousActivity(clientIp, "LOGIN_FAILURE", 
                String.format("Username: %s, Attempt: %d", username, failureCount));
            
            // 브루트 포스 공격 탐지 및 차단
            if (failureCount >= SEVERE_BRUTE_FORCE_THRESHOLD) {
                // 10회 이상 실패 시 24시간 차단
                ipAccessControlService.blockIpTemporarily(clientIp, Duration.ofHours(24), 
                    String.format("Severe brute force attack detected: %d failures", failureCount));
                
            } else if (failureCount >= BRUTE_FORCE_THRESHOLD) {
                // 5회 이상 실패 시 1시간 차단
                ipAccessControlService.blockIpTemporarily(clientIp, Duration.ofHours(1), 
                    String.format("Brute force attack detected: %d failures", failureCount));
            }
        }
    }
    
    /**
     * 로그인 성공 이벤트 처리
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String clientIp = extractClientIp(event);
        String username = event.getAuthentication().getName();
        
        if (clientIp != null) {
            // 성공 시 실패 카운터 리셋
            failureCounters.remove(clientIp);
            
            log.info("Authentication success: username={}, ip={}", username, clientIp);
        }
    }
    
    /**
     * 클라이언트 IP 추출
     */
    private String extractClientIp(AbstractAuthenticationFailureEvent event) {
        Object details = event.getAuthentication().getDetails();
        if (details instanceof WebAuthenticationDetails) {
            return ((WebAuthenticationDetails) details).getRemoteAddress();
        }
        return null;
    }
    
    /**
     * 클라이언트 IP 추출 (성공 이벤트)
     */
    private String extractClientIp(AuthenticationSuccessEvent event) {
        Object details = event.getAuthentication().getDetails();
        if (details instanceof WebAuthenticationDetails) {
            return ((WebAuthenticationDetails) details).getRemoteAddress();
        }
        return null;
    }
}
```

---

## ⚙️ 5. IP 접근 제한 설정

### IpAccessConfig.java
```java
package com.routepick.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;

/**
 * IP 접근 제한 설정
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.security.ip-access")
public class IpAccessProperties {
    
    /**
     * IP 접근 제한 활성화
     */
    private boolean enabled = true;
    
    /**
     * 관리자 API 화이트리스트
     */
    @NotEmpty
    private List<String> adminWhitelist = List.of(
        "127.0.0.1",
        "::1",
        "192.168.1.0/24"
    );
    
    /**
     * 허용 국가 코드
     */
    private Set<String> allowedCountries = Set.of("KR", "US", "JP", "CA", "AU", "GB");
    
    /**
     * 차단 국가 코드
     */
    private Set<String> blockedCountries = Set.of("CN", "RU");
    
    /**
     * GeoIP 서비스 활성화
     */
    private boolean geoIpEnabled = true;
    
    /**
     * 자동 차단 임계치
     */
    private int autoBlockThreshold = 10;
    
    /**
     * 브루트 포스 임계치
     */
    private int bruteForceThreshold = 5;
}
```

### application.yml 추가 설정
```yaml
app:
  security:
    ip-access:
      enabled: true
      admin-whitelist:
        - "127.0.0.1"
        - "::1"
        - "192.168.1.0/24"
        - "10.0.0.0/8"
      allowed-countries:
        - "KR"  # 한국
        - "US"  # 미국
        - "JP"  # 일본
        - "CA"  # 캐나다
        - "AU"  # 호주
        - "GB"  # 영국
      blocked-countries:
        - "CN"  # 중국
        - "RU"  # 러시아
        - "KP"  # 북한
      geo-ip-enabled: true
      auto-block-threshold: 10
      brute-force-threshold: 5

spring:
  redis:
    security:
      database: 3  # IP 접근 제어 전용 DB
      timeout: 3000ms
```

---

## 📊 6. IP 접근 제한 모니터링

### IpAccessController.java (관리자용)
```java
package com.routepick.controller.admin;

import com.routepick.common.ApiResponse;
import com.routepick.service.security.IpAccessControlService;
import com.routepick.service.security.GeoIpService;
import com.routepick.service.security.GeoIpInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * IP 접근 제한 관리 API (관리자용)
 */
@RestController
@RequestMapping("/api/v1/admin/security/ip")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class IpAccessController {
    
    private final IpAccessControlService ipAccessControlService;
    private final GeoIpService geoIpService;
    
    /**
     * IP 차단 상태 조회
     */
    @GetMapping("/{ip}/status")
    public ResponseEntity<ApiResponse<Object>> checkIpStatus(@PathVariable String ip) {
        boolean isBlocked = ipAccessControlService.isBlocked(ip);
        boolean isWhitelisted = ipAccessControlService.isWhitelisted(ip);
        boolean isCountryAllowed = ipAccessControlService.isCountryAllowed(ip);
        GeoIpInfo geoInfo = geoIpService.getGeoInfo(ip);
        
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "ip", ip,
            "blocked", isBlocked,
            "whitelisted", isWhitelisted,
            "countryAllowed", isCountryAllowed,
            "geoInfo", geoInfo
        )));
    }
    
    /**
     * IP 임시 차단
     */
    @PostMapping("/{ip}/block")
    public ResponseEntity<ApiResponse<Void>> blockIp(@PathVariable String ip,
                                                   @RequestParam int hours,
                                                   @RequestParam String reason) {
        ipAccessControlService.blockIpTemporarily(ip, Duration.ofHours(hours), reason);
        return ResponseEntity.ok(ApiResponse.success());
    }
    
    /**
     * IP 화이트리스트 추가
     */
    @PostMapping("/{ip}/whitelist")
    public ResponseEntity<ApiResponse<Void>> addToWhitelist(@PathVariable String ip,
                                                          @RequestParam String description) {
        ipAccessControlService.addToWhitelist(ip, description);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
```

---

*Step 8-2b 완료: IP 기반 접근 제한 구현 (화이트리스트 + GeoIP + 자동차단)*
*다음 파일: step8-2c_method_security_config.md*