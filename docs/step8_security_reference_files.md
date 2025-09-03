# 8단계: Security 설정 구현 참고 파일 목록

## 📋 구현 목표
- **JWT 보안 설정**: Spring Security 6.x 기반 토큰 인증
- **인증/인가**: Role 기반 접근 제어 (RBAC)
- **Rate Limiting**: Redis 기반 분산 제한
- **CORS/CSRF**: 크로스 도메인 보안 설정
- **세분화 전략**: 토큰 제한 대응, 단계별 구현

## 🎯 8단계 구현 범위

### 8-1: JWT & Security Configuration (4개 파일)
1. **SecurityConfig** - Spring Security 6.x 메인 설정
2. **JwtAuthenticationFilter** - JWT 토큰 검증 필터
3. **JwtTokenProvider** - 토큰 생성/검증 유틸리티
4. **JwtProperties** - JWT 설정값 관리

### 8-2: Authentication & Authorization (4개 파일)
1. **CustomUserDetailsService** - 사용자 인증 정보 로드
2. **OAuth2SuccessHandler** - 소셜 로그인 성공 처리
3. **AccessDeniedHandler** - 권한 없음 처리
4. **AuthenticationEntryPoint** - 인증 실패 처리

### 8-3: Rate Limiting & Protection (4개 파일)
1. **RateLimitingFilter** - Rate Limiting 필터
2. **RateLimitingService** - Redis 기반 제한 서비스
3. **DDoSProtectionFilter** - DDoS 공격 방어
4. **IpBlockingService** - IP 차단 관리

### 8-4: CORS & CSRF Configuration (3개 파일)
1. **CorsConfig** - CORS 정책 설정
2. **CsrfConfig** - CSRF 토큰 관리
3. **SecurityHeaderConfig** - 보안 헤더 설정

### 8-5: Security Utilities & Monitoring (3개 파일)
1. **SecurityAuditService** - 보안 이벤트 로깅
2. **TokenBlacklistService** - 토큰 무효화 관리
3. **SecurityMetricsService** - 보안 메트릭 수집

## 📁 참고해야 할 기존 파일들

### Service 레이어 (step6)
```
✅ step6-1a_auth_service.md
   - JWT 토큰 생성/검증 로직
   - RefreshToken 관리
   - 소셜 로그인 통합

✅ step6-1d_verification_security.md
   - 본인인증 로직
   - 보안 유틸리티
   - 암호화/복호화

✅ step6-6c_cache_service.md
   - Redis 캐시 관리
   - TTL 설정
   - 분산 락 구현
```

### Controller 레이어 (step7)
```
✅ step7-1a_auth_controller.md
   - 인증 엔드포인트
   - @PreAuthorize 사용 예시
   - SecurityContext 활용

✅ step7-1f_xss_security.md
   - XSS 방지 구현
   - Custom Validator

✅ step7-1g_rate_limiting.md
   - @RateLimited 어노테이션
   - Rate Limiting 전략

✅ step7-5f_security_enhancements.md
   - 보안 필터 체인
   - 민감정보 마스킹
   - 보안 로깅
```

### Entity & Repository (step4-5)
```
✅ step4-1b_user_core_entities.md
   - User, UserRole 엔티티
   - 권한 관계 매핑

✅ step5-1b2_user_verification_repositories.md
   - 사용자 인증 Repository
   - 토큰 저장소
```

## 🔧 구현 순서

### Phase 1: Core Security (8-1)
```java
// 1. SecurityConfig.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    // Spring Security 6.x 설정
    // SecurityFilterChain Bean
    // PasswordEncoder Bean
}

// 2. JwtAuthenticationFilter.java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // JWT 토큰 검증
    // SecurityContext 설정
}

// 3. JwtTokenProvider.java
@Component
public class JwtTokenProvider {
    // 토큰 생성 (Access/Refresh)
    // 토큰 검증
    // Claims 추출
}
```

### Phase 2: Authentication (8-2)
```java
// 1. CustomUserDetailsService.java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    // 사용자 정보 로드
    // 권한 매핑
}

// 2. OAuth2SuccessHandler.java
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    // 소셜 로그인 성공 처리
    // JWT 토큰 발급
}
```

### Phase 3: Rate Limiting (8-3)
```java
// 1. RateLimitingFilter.java
@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    // Redis 기반 요청 카운팅
    // 제한 초과 처리
}

// 2. RateLimitingService.java
@Service
public class RateLimitingService {
    // Lua 스크립트 실행
    // 분산 환경 대응
}
```

### Phase 4: CORS/CSRF (8-4)
```java
// 1. CorsConfig.java
@Configuration
public class CorsConfig {
    // 허용 도메인 설정
    // 허용 메소드/헤더 설정
}

// 2. SecurityHeaderConfig.java
@Component
public class SecurityHeaderConfig {
    // X-Frame-Options
    // X-Content-Type-Options
    // Strict-Transport-Security
}
```

### Phase 5: Monitoring (8-5)
```java
// 1. SecurityAuditService.java
@Service
public class SecurityAuditService {
    // 보안 이벤트 로깅
    // 실패 시도 추적
}

// 2. TokenBlacklistService.java
@Service
public class TokenBlacklistService {
    // 로그아웃 토큰 관리
    // 만료 토큰 정리
}
```

## 🛡️ 보안 요구사항

### JWT 보안
- **토큰 수명**: Access Token 30분, Refresh Token 7일
- **토큰 저장**: HttpOnly Cookie + Secure Flag
- **토큰 갱신**: Sliding Window 방식
- **토큰 무효화**: Redis Blacklist

### Rate Limiting
- **전역 제한**: 1000req/min per IP
- **인증 API**: 5req/min per IP
- **일반 API**: 100req/min per User
- **파일 업로드**: 10req/min per User

### CORS 설정
```yaml
allowed-origins:
  - https://routepick.com
  - https://admin.routepick.com
  - http://localhost:3000 # 개발용
allowed-methods:
  - GET, POST, PUT, DELETE, OPTIONS
allowed-headers:
  - Authorization, Content-Type, X-Requested-With
```

### Security Headers
```
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000
Content-Security-Policy: default-src 'self'
```

## 📊 구현 우선순위

### Critical (필수)
1. **SecurityConfig** - Spring Security 설정
2. **JwtAuthenticationFilter** - JWT 인증
3. **JwtTokenProvider** - 토큰 관리
4. **CustomUserDetailsService** - 사용자 인증

### High (권장)
1. **RateLimitingFilter** - Rate Limiting
2. **CorsConfig** - CORS 설정
3. **OAuth2SuccessHandler** - 소셜 로그인
4. **SecurityAuditService** - 보안 로깅

### Medium (선택)
1. **DDoSProtectionFilter** - DDoS 방어
2. **TokenBlacklistService** - 토큰 블랙리스트
3. **SecurityMetricsService** - 메트릭 수집

## 🎯 예상 결과물

### 8단계 완료 시
- **18개 Security 컴포넌트** 구현
- **JWT 기반 인증 시스템** 완성
- **Rate Limiting 시스템** 구축
- **CORS/CSRF 보안** 설정
- **보안 모니터링 체계** 확립

### 테스트 가능 항목
- JWT 토큰 발급/검증
- Role 기반 접근 제어
- Rate Limiting 동작
- CORS 정책 적용
- 보안 헤더 검증

## ✅ 체크리스트

### Phase 1 (8-1)
- [ ] SecurityConfig 구현
- [ ] JwtAuthenticationFilter 구현
- [ ] JwtTokenProvider 구현
- [ ] JwtProperties 설정

### Phase 2 (8-2)
- [ ] CustomUserDetailsService 구현
- [ ] OAuth2SuccessHandler 구현
- [ ] AccessDeniedHandler 구현
- [ ] AuthenticationEntryPoint 구현

### Phase 3 (8-3)
- [ ] RateLimitingFilter 구현
- [ ] RateLimitingService 구현
- [ ] DDoSProtectionFilter 구현
- [ ] IpBlockingService 구현

### Phase 4 (8-4)
- [ ] CorsConfig 구현
- [ ] CsrfConfig 구현
- [ ] SecurityHeaderConfig 구현

### Phase 5 (8-5)
- [ ] SecurityAuditService 구현
- [ ] TokenBlacklistService 구현
- [ ] SecurityMetricsService 구현

---
*8단계 Security 설정 구현 준비 완료*
*예상 작업 기간: 5개 세션 (Phase별 1세션)*
*토큰 제한 대응: 파일당 평균 200줄 이하*