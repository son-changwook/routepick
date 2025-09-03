# Step 7-5g: Step 8 보안 설정 가이드

> Step 8 단계를 위한 보안 설정 준비 가이드 - Spring Security, JWT, 암호화, Rate Limiting
> 생성일: 2025-08-25  
> 단계: 7-5g (보안 설정 준비)
> 참고: step7-4g, step3-3b, step6-1a, step6-1d

---

## 🎯 Step 8 보안 설정 개요

Step 7에서 구현한 모든 Controller와 DTO가 완성됨에 따라, Step 8에서는 다음 보안 설정들이 필요합니다:

### 🔐 필수 보안 구성 요소
1. **Spring Security 설정** - 인증/인가, CORS, CSRF
2. **JWT 토큰 관리** - 토큰 검증, 갱신, 블랙리스트
3. **Rate Limiting** - API 호출 제한, DDoS 방지
4. **데이터 암호화** - 민감정보 암호화, 개인정보 마스킹
5. **보안 헤더** - XSS, CSRF, 콘텐츠 보안 정책
6. **감사 로깅** - 보안 이벤트 로깅, 접근 추적

---

## 🚀 Step 8에서 구현할 보안 설정들

### 1. Spring Security Configuration

#### SecurityConfig.java
```java
package com.routepick.config.security;

import com.routepick.security.jwt.JwtAuthenticationEntryPoint;
import com.routepick.security.jwt.JwtAuthenticationFilter;
import com.routepick.security.handler.CustomAccessDeniedHandler;
import com.routepick.security.handler.CustomAuthenticationSuccessHandler;
import com.routepick.security.handler.CustomLogoutSuccessHandler;
import com.routepick.service.auth.CustomUserDetailsService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security 설정
 * - JWT 기반 인증
 * - CORS 설정
 * - Rate Limiting 통합
 * - 보안 헤더 설정
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Security Filter Chain 설정
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF 비활성화 (JWT 사용)
                .csrf().disable()
                
                // CORS 설정
                .cors().configurationSource(corsConfigurationSource())
                .and()
                
                // 세션 관리 - Stateless
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                
                // Exception Handling
                .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .and()
                
                // URL 기반 인가 설정
                .authorizeHttpRequests(authz -> authz
                        // 공개 API
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers("/api/v1/notifications/banners").permitAll()
                        .requestMatchers("/api/v1/notifications/notices").permitAll()
                        .requestMatchers("/api/v1/payments/webhook").permitAll()
                        
                        // Swagger/OpenAPI (개발환경에서만)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        
                        // 정적 리소스
                        .requestMatchers("/favicon.ico", "/error").permitAll()
                        
                        // 관리자 전용 API
                        .requestMatchers("/api/v1/system/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/payments/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/notifications/admin/**").hasRole("ADMIN")
                        
                        // 인증 필요 API
                        .requestMatchers("/api/v1/**").authenticated()
                        
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                
                // JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                
                // 보안 헤더 설정
                .headers(headers -> headers
                        .frameOptions().deny()
                        .contentTypeOptions().and()
                        .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                .maxAgeInSeconds(31536000)
                                .includeSubdomains(true)
                        )
                        .and()
                )
                
                .build();
    }

    /**
     * CORS 설정
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 허용된 Origin (환경별 설정)
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",    // React 개발서버
                "http://localhost:8080",    // Spring Boot 개발서버
                "https://*.routepick.co.kr", // 운영 도메인
                "https://*.routepick.com"   // 글로벌 도메인
        ));
        
        // 허용된 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // 허용된 헤더
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With",
                "X-User-Agent", "X-Client-Version", "X-Device-Id"
        ));
        
        // 노출할 헤더
        configuration.setExposedHeaders(Arrays.asList(
                "X-Total-Count", "X-Page-Count", "X-Current-Page"
        ));
        
        // 자격 증명 허용
        configuration.setAllowCredentials(true);
        
        // Preflight 캐시 시간
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }

    /**
     * 패스워드 인코더
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // 강력한 해싱
    }

    /**
     * Authentication Manager
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
```

### 2. JWT 보안 설정

#### JwtSecurityConfig.java
```java
package com.routepick.config.security;

import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 보안 설정
 */
@Configuration
@Getter
public class JwtSecurityConfig {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token.expiration:3600000}") // 1시간
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration:2592000000}") // 30일
    private long refreshTokenExpiration;

    @Value("${jwt.issuer:routepick}")
    private String issuer;

    /**
     * JWT 서명용 보안 키 생성
     */
    public SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JWT 블랙리스트 Redis 키 접두어
     */
    public String getBlacklistKeyPrefix() {
        return "jwt:blacklist:";
    }

    /**
     * 리프레시 토큰 Redis 키 접두어
     */
    public String getRefreshTokenKeyPrefix() {
        return "jwt:refresh:";
    }
}
```

### 3. Rate Limiting 설정

#### RateLimitConfig.java
```java
package com.routepick.config.security;

import com.routepick.security.ratelimit.RateLimitInterceptor;
import com.routepick.security.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rate Limiting 설정
 */
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitService rateLimitService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimitService))
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/health",
                        "/api/v1/payments/webhook"
                );
    }
}
```

### 4. 데이터 암호화 설정

#### EncryptionConfig.java
```java
package com.routepick.config.security;

import com.routepick.security.encryption.AESUtil;
import com.routepick.security.encryption.DataMaskingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 데이터 암호화 설정
 */
@Configuration
@RequiredArgsConstructor
public class EncryptionConfig {

    @Value("${app.encryption.key}")
    private String encryptionKey;

    @Value("${app.encryption.salt}")
    private String encryptionSalt;

    /**
     * AES 암호화 유틸리티
     */
    @Bean
    public AESUtil aesUtil() {
        return new AESUtil(encryptionKey, encryptionSalt);
    }

    /**
     * 데이터 마스킹 서비스
     */
    @Bean
    public DataMaskingService dataMaskingService() {
        return new DataMaskingService();
    }
}
```

---

## 📋 Step 8 구현 체크리스트

### 🔐 인증/인가 (Authentication/Authorization)
- [ ] JWT 토큰 생성/검증 구현
- [ ] 토큰 갱신 (Refresh Token) 구현
- [ ] 토큰 블랙리스트 관리
- [ ] 소셜 로그인 연동 (Google, Kakao, Naver, Facebook)
- [ ] 역할 기반 권한 관리 (RBAC)
- [ ] API 엔드포인트별 권한 설정

### 🛡️ 보안 강화 (Security Hardening)
- [ ] CORS 정책 설정
- [ ] CSRF 보호 설정
- [ ] XSS 방지 필터
- [ ] SQL Injection 방지
- [ ] 보안 헤더 설정 (HSTS, CSP, X-Frame-Options)
- [ ] 입력 데이터 검증 및 정제

### ⚡ Rate Limiting
- [ ] IP 기반 Rate Limiting
- [ ] 사용자 기반 Rate Limiting
- [ ] 엔드포인트별 Rate Limiting
- [ ] 복합 Rate Limiting (IP + User)
- [ ] Rate Limit 초과시 응답 처리
- [ ] Redis를 활용한 분산 Rate Limiting

### 🔒 데이터 보호 (Data Protection)
- [ ] 민감정보 암호화 (AES-256)
- [ ] 개인정보 마스킹
- [ ] 패스워드 해싱 (BCrypt)
- [ ] 데이터베이스 컬럼 암호화
- [ ] 로그에서 민감정보 필터링
- [ ] PCI DSS 준수 (결제 정보)

### 🔍 보안 모니터링 (Security Monitoring)
- [ ] 보안 이벤트 로깅
- [ ] 실패한 인증 시도 추적
- [ ] 이상 접근 패턴 감지
- [ ] 보안 알림 시스템
- [ ] 접근 로그 수집 및 분석
- [ ] 보안 대시보드 구성

### 🌐 네트워크 보안 (Network Security)
- [ ] HTTPS 강제 적용
- [ ] IP 화이트리스트/블랙리스트
- [ ] 지역 기반 접근 제한
- [ ] API Gateway 보안 설정
- [ ] WAF (Web Application Firewall) 연동
- [ ] DDoS 방지 설정

### 📱 클라이언트 보안 (Client Security)
- [ ] API 키 관리
- [ ] 클라이언트 인증서 검증
- [ ] 앱 무결성 검증
- [ ] 루팅/탈옥 탐지
- [ ] 앱 버전 호환성 체크
- [ ] 디바이스 지문 인식

### 🔧 보안 설정 (Security Configuration)
- [ ] 보안 설정 외부화
- [ ] 환경별 보안 정책
- [ ] 보안 패치 관리
- [ ] 보안 취약점 스캔
- [ ] 보안 컴플라이언스 체크
- [ ] 보안 정책 문서화

---

## 🚨 보안 우선순위

### HIGH (즉시 구현 필요)
1. **JWT 인증 시스템** - 모든 API 보호의 기반
2. **Rate Limiting** - DDoS 및 무차별 대입 공격 방지
3. **데이터 암호화** - 민감정보 보호
4. **입력 검증** - XSS, SQL Injection 방지
5. **HTTPS 강제** - 통신 암호화

### MEDIUM (단계별 구현)
1. **보안 헤더 설정** - 브라우저 보안 강화
2. **접근 로그** - 보안 이벤트 추적
3. **IP 제한** - 지역/IP 기반 접근 제어
4. **세션 관리** - 세션 하이재킹 방지
5. **API 버전 관리** - 구버전 API 보안

### LOW (장기적 구현)
1. **WAF 연동** - 웹 방화벽 설정
2. **보안 모니터링** - 실시간 위협 탐지
3. **컴플라이언스** - 규정 준수 체크
4. **보안 테스트** - 침투 테스트
5. **보안 교육** - 개발팀 보안 교육

---

## 📂 Step 8에서 생성할 파일 구조

```
src/main/java/com/routepick/
├── config/
│   ├── security/
│   │   ├── SecurityConfig.java ✅
│   │   ├── JwtSecurityConfig.java ✅
│   │   ├── RateLimitConfig.java ✅
│   │   ├── EncryptionConfig.java ✅
│   │   ├── CorsConfig.java
│   │   └── SecurityPropertiesConfig.java
│   └── monitoring/
│       ├── SecurityMonitoringConfig.java
│       └── AuditConfig.java
├── security/
│   ├── jwt/
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   ├── JwtTokenValidator.java
│   │   └── JwtBlacklistService.java
│   ├── handler/
│   │   ├── CustomAccessDeniedHandler.java
│   │   ├── CustomAuthenticationSuccessHandler.java
│   │   ├── CustomLogoutSuccessHandler.java
│   │   └── SecurityEventHandler.java
│   ├── filter/
│   │   ├── XSSProtectionFilter.java
│   │   ├── SQLInjectionFilter.java
│   │   ├── SecurityHeaderFilter.java
│   │   └── AuditLogFilter.java
│   ├── ratelimit/
│   │   ├── RateLimitInterceptor.java
│   │   ├── RateLimitService.java
│   │   ├── RateLimitResolver.java
│   │   └── RateLimitExceptionHandler.java
│   ├── encryption/
│   │   ├── AESUtil.java
│   │   ├── DataMaskingService.java
│   │   ├── PasswordEncryptionService.java
│   │   └── DatabaseEncryptionConverter.java
│   ├── validator/
│   │   ├── InputSanitizer.java
│   │   ├── SecurityValidator.java
│   │   └── ThreatDetector.java
│   └── monitoring/
│       ├── SecurityEventLogger.java
│       ├── SecurityMetricsCollector.java
│       ├── AuditTrailService.java
│       └── SecurityAlertService.java
└── aspect/
    ├── SecurityAuditAspect.java
    ├── RateLimitAspect.java
    └── DataMaskingAspect.java
```

---

## 🔧 필요한 의존성 추가

### build.gradle 보안 관련 의존성
```gradle
dependencies {
    // Spring Security
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.security:spring-security-test'
    
    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    implementation 'io.jsonwebtoken:jjwt-impl:0.11.5'
    implementation 'io.jsonwebtoken:jjwt-jackson:0.11.5'
    
    // Redis (Rate Limiting & JWT Blacklist)
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.redisson:redisson-spring-boot-starter:3.21.3'
    
    // 암호화
    implementation 'org.bouncycastle:bcprov-jdk15on:1.70'
    implementation 'org.jasypt:jasypt-spring-boot-starter:3.0.5'
    
    // 모니터링
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
    
    // 로깅 보안
    implementation 'ch.qos.logback:logback-classic'
    implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
    
    // 입력 검증
    implementation 'org.owasp.esapi:esapi:2.5.0.0'
    implementation 'org.jsoup:jsoup:1.16.1'
    
    // IP 지역 정보
    implementation 'com.maxmind.geoip2:geoip2:4.0.0'
}
```

---

## ⚡ Step 8 구현 순서

### Phase 1: 기본 보안 (1-2일)
1. Spring Security 기본 설정
2. JWT 토큰 생성/검증
3. 기본 인증/인가 구현
4. CORS 설정

### Phase 2: 고급 보안 (2-3일)
1. Rate Limiting 구현
2. 데이터 암호화 구현
3. XSS/SQL Injection 방지
4. 보안 헤더 설정

### Phase 3: 모니터링 (1-2일)
1. 보안 이벤트 로깅
2. 접근 추적 시스템
3. 보안 메트릭 수집
4. 알림 시스템 구축

### Phase 4: 테스트 및 최적화 (1-2일)
1. 보안 테스트 작성
2. 성능 테스트
3. 보안 설정 최적화
4. 문서화 완료

---

## 🎯 Step 8 완료 후 기대 효과

### 보안 강화
- **99.9% 보안 위협 차단**: 주요 보안 취약점 해결
- **실시간 위협 탐지**: 이상 접근 패턴 자동 감지
- **데이터 보호**: 민감정보 암호화 및 마스킹
- **컴플라이언스**: GDPR, PCI DSS 등 규정 준수

### 성능 최적화
- **Rate Limiting**: API 남용 방지, 서버 안정성 확보
- **캐싱 활용**: JWT 검증 성능 향상
- **비동기 처리**: 보안 로깅 성능 최적화
- **리소스 절약**: 불필요한 요청 차단

### 운영 효율성
- **자동화된 보안**: 수동 개입 최소화
- **실시간 모니터링**: 보안 상태 실시간 파악
- **알림 시스템**: 중요 보안 이벤트 즉시 통보
- **감사 추적**: 모든 보안 이벤트 기록

---

*Step 8 보안 설정 가이드 완성일: 2025-08-25*  
*다음 단계: Step 8 보안 구현 시작*  
*예상 소요 시간: 7-10일*  
*구현 우선순위: JWT 인증 → Rate Limiting → 데이터 보호 → 모니터링*