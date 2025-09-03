# Step 7-1a: AuthController 구현

> 인증 관련 RESTful API Controller 완전 구현  
> 생성일: 2025-08-22  
> 기반: step6-1a_auth_service.md, JWT 토큰 관리 및 소셜 로그인

---

## 🎯 설계 원칙

- **RESTful API**: HTTP 상태 코드 정확한 사용
- **보안 강화**: Rate Limiting, XSS 방지, 패스워드 정책
- **입력 검증**: @Valid 완벽 적용
- **API 버전 관리**: /api/v1 prefix
- **표준 응답**: ApiResponse 통일 구조

---

## 🔐 AuthController 구현

### AuthController.java
```java
package com.routepick.controller.auth;

import com.routepick.common.ApiResponse;
import com.routepick.common.enums.SocialProvider;
import com.routepick.dto.auth.request.*;
import com.routepick.dto.auth.response.*;
import com.routepick.service.auth.AuthService;
import com.routepick.service.email.EmailService;
import com.routepick.annotation.RateLimited;
import com.routepick.util.XssProtectionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인증 관리 Controller
 * - JWT 기반 인증
 * - 소셜 로그인 (Google, Kakao, Naver, Facebook)
 * - 이메일 인증
 * - Rate Limiting 적용
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
@Tag(name = "인증 관리", description = "회원가입, 로그인, 토큰 관리 API")
public class AuthController {
    
    private final AuthService authService;
    private final EmailService emailService;
    
    // ===== 회원가입 =====
    
    /**
     * 일반 회원가입
     * - 이메일 중복 확인
     * - 비밀번호 정책 검증
     * - 닉네임 중복 확인
     * - 약관 동의 처리
     * - 이메일 인증 발송
     */
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이메일 기반 회원가입 처리")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "201", description = "회원가입 성공",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 입력값"),
        @SwaggerApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복")
    })
    @RateLimited(requests = 3, period = 300) // 5분간 3회 제한
    public ResponseEntity<ApiResponse<UserResponse>> signUp(
            @Valid @RequestBody SignUpRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("회원가입 요청: email={}, nickname={}", request.getEmail(), request.getNickname());
        
        // XSS 방지를 위한 입력값 정제
        request.setNickname(XssProtectionUtil.clean(request.getNickname()));
        
        // IP 주소 추출
        String clientIp = extractClientIp(httpRequest);
        
        // 회원가입 처리
        UserResponse response = authService.signUp(
            request.getEmail(),
            request.getPassword(),
            request.getNickname(),
            request.getPhone(),
            request.getAgreementIds(),
            clientIp
        );
        
        // 이메일 인증 발송 (비동기)
        emailService.sendVerificationEmail(response.getId());
        
        log.info("회원가입 성공: userId={}", response.getId());
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요."));
    }
    
    // ===== 로그인 =====
    
    /**
     * 일반 로그인
     * - 이메일/비밀번호 검증
     * - 계정 상태 확인
     * - JWT 토큰 발급
     * - 로그인 이력 기록
     */
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "로그인 성공",
                content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        @SwaggerApiResponse(responseCode = "423", description = "계정 잠김")
    })
    @RateLimited(requests = 5, period = 300) // 5분간 5회 제한
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("로그인 시도: email={}", request.getEmail());
        
        // IP 주소 및 User-Agent 추출
        String clientIp = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        // 로그인 처리
        LoginResponse response = authService.login(
            request.getEmail(),
            request.getPassword(),
            clientIp,
            userAgent
        );
        
        log.info("로그인 성공: userId={}", response.getUser().getId());
        
        return ResponseEntity.ok(ApiResponse.success(response, "로그인이 완료되었습니다."));
    }
    
    // ===== 소셜 로그인 =====
    
    /**
     * 소셜 로그인
     * - 4개 제공자 지원 (Google, Kakao, Naver, Facebook)
     * - 신규 회원 자동 가입
     * - 기존 회원 연동
     * - JWT 토큰 발급
     */
    @PostMapping("/social-login")
    @Operation(summary = "소셜 로그인", description = "소셜 계정으로 로그인 또는 회원가입")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "소셜 로그인 성공",
                content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 소셜 정보"),
        @SwaggerApiResponse(responseCode = "401", description = "소셜 인증 실패")
    })
    public ResponseEntity<ApiResponse<LoginResponse>> socialLogin(
            @Valid @RequestBody SocialLoginRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("소셜 로그인 시도: provider={}, email={}", request.getProvider(), request.getEmail());
        
        // IP 주소 및 User-Agent 추출
        String clientIp = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        // 소셜 로그인 처리
        LoginResponse response = authService.socialLogin(
            request.getProvider(),
            request.getSocialId(),
            request.getEmail(),
            request.getName(),
            clientIp,
            userAgent
        );
        
        log.info("소셜 로그인 성공: userId={}, provider={}", 
                response.getUser().getId(), request.getProvider());
        
        return ResponseEntity.ok(ApiResponse.success(response, "소셜 로그인이 완료되었습니다."));
    }
    
    // ===== 토큰 관리 =====
    
    /**
     * 토큰 갱신
     * - Refresh Token 검증
     * - 새로운 Access Token 발급
     * - 토큰 로테이션 적용
     */
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 Access Token 재발급")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "토큰 갱신 성공",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @SwaggerApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            @RequestHeader("Refresh-Token") String refreshToken,
            HttpServletRequest httpRequest) {
        
        log.info("토큰 갱신 요청");
        
        // IP 주소 추출
        String clientIp = extractClientIp(httpRequest);
        
        // 토큰 갱신 처리
        TokenResponse response = authService.refreshToken(refreshToken, clientIp);
        
        log.info("토큰 갱신 성공");
        
        return ResponseEntity.ok(ApiResponse.success(response, "토큰이 갱신되었습니다."));
    }
    
    /**
     * 로그아웃
     * - 토큰 무효화 (블랙리스트 등록)
     * - Redis 세션 삭제
     * - 로그아웃 이력 기록
     */
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 세션 종료 및 토큰 무효화")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "로그아웃 성공"),
        @SwaggerApiResponse(responseCode = "401", description = "인증되지 않은 요청")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String accessToken,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {
        
        log.info("로그아웃 요청: userId={}", userId);
        
        // Bearer 토큰 추출
        String token = extractBearerToken(accessToken);
        
        // IP 주소 추출
        String clientIp = extractClientIp(httpRequest);
        
        // 로그아웃 처리
        authService.logout(userId, token, clientIp);
        
        log.info("로그아웃 성공: userId={}", userId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "로그아웃이 완료되었습니다."));
    }
    
    // ===== 이메일 확인 =====
    
    /**
     * 이메일 중복 확인
     * - 회원가입 전 이메일 사용 가능 여부 확인
     * - 실시간 검증
     */
    @PostMapping("/check-email")
    @Operation(summary = "이메일 중복 확인", description = "회원가입 전 이메일 사용 가능 여부 확인")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "확인 완료",
                content = @Content(schema = @Schema(implementation = EmailCheckResponse.class)))
    })
    @RateLimited(requests = 10, period = 60) // 1분간 10회 제한
    public ResponseEntity<ApiResponse<EmailCheckResponse>> checkEmail(
            @Valid @RequestBody EmailCheckRequest request) {
        
        log.info("이메일 중복 확인: email={}", request.getEmail());
        
        // 이메일 중복 확인
        boolean available = authService.isEmailAvailable(request.getEmail());
        
        EmailCheckResponse response = EmailCheckResponse.builder()
            .available(available)
            .message(available ? "사용 가능한 이메일입니다." : "이미 사용 중인 이메일입니다.")
            .build();
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    // ===== 비밀번호 재설정 =====
    
    /**
     * 비밀번호 재설정 요청
     * - 이메일로 재설정 링크 발송
     * - 임시 토큰 생성 (15분 유효)
     * - 재설정 이력 기록
     */
    @PostMapping("/reset-password")
    @Operation(summary = "비밀번호 재설정", description = "비밀번호 재설정 링크를 이메일로 발송")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "재설정 이메일 발송 완료"),
        @SwaggerApiResponse(responseCode = "404", description = "등록되지 않은 이메일")
    })
    @RateLimited(requests = 3, period = 600) // 10분간 3회 제한
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("비밀번호 재설정 요청: email={}", request.getEmail());
        
        // IP 주소 추출
        String clientIp = extractClientIp(httpRequest);
        
        // 비밀번호 재설정 처리
        authService.requestPasswordReset(request.getEmail(), clientIp);
        
        return ResponseEntity.ok(ApiResponse.success(null, 
            "비밀번호 재설정 링크가 이메일로 발송되었습니다."));
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 클라이언트 IP 주소 추출
     * - X-Forwarded-For 헤더 우선 확인
     * - Proxy 환경 대응
     */
    private String extractClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR",
            "X-Real-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 첫 번째 IP만 추출 (쉼표로 구분된 경우)
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Bearer 토큰 추출
     * - Authorization 헤더에서 Bearer 토큰 추출
     */
    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header format");
    }
}
```

---

## 🔒 보안 및 성능 최적화

### 1. Rate Limiting 어노테이션
```java
package com.routepick.annotation;

import java.lang.annotation.*;

/**
 * Rate Limiting 어노테이션
 * - API 호출 횟수 제한
 * - DDoS 공격 방어
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {
    /**
     * 허용되는 요청 횟수
     */
    int requests() default 10;
    
    /**
     * 시간 윈도우 (초 단위)
     */
    int period() default 60;
    
    /**
     * Rate limit 키 생성 전략
     */
    KeyStrategy keyStrategy() default KeyStrategy.IP;
    
    enum KeyStrategy {
        IP,           // IP 주소 기반
        USER,         // 사용자 ID 기반
        IP_AND_USER   // IP + 사용자 조합
    }
}
```

### 2. XSS 방지 유틸리티
```java
package com.routepick.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * XSS 방지 유틸리티
 * - HTML 태그 제거
 * - 스크립트 인젝션 방지
 */
public class XssProtectionUtil {
    
    private static final Safelist SAFELIST = Safelist.none();
    
    /**
     * HTML 태그 및 스크립트 제거
     */
    public static String clean(String input) {
        if (input == null) {
            return null;
        }
        return Jsoup.clean(input, SAFELIST);
    }
    
    /**
     * 닉네임용 정제
     * - 한글, 영문, 숫자, 언더스코어만 허용
     */
    public static String cleanNickname(String nickname) {
        if (nickname == null) {
            return null;
        }
        // HTML 태그 제거 후 특수문자 검증
        String cleaned = clean(nickname);
        return cleaned.replaceAll("[^가-힣a-zA-Z0-9_]", "");
    }
}
```

### 3. 응답 캐시 설정
```java
@Configuration
public class CacheConfig {
    
    @Bean
    public FilterRegistrationBean<CacheControlFilter> cacheControlFilter() {
        FilterRegistrationBean<CacheControlFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CacheControlFilter());
        registrationBean.addUrlPatterns("/api/v1/auth/check-email");
        return registrationBean;
    }
    
    public static class CacheControlFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            // 이메일 중복 확인은 캐시하지 않음
            httpResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setDateHeader("Expires", 0);
            chain.doFilter(request, response);
        }
    }
}
```

---

## 📋 API 명세

### 1. 회원가입
- **URL**: `POST /api/v1/auth/signup`
- **Rate Limit**: 5분간 3회
- **요청**: SignUpRequest
- **응답**: 201 Created, UserResponse

### 2. 로그인
- **URL**: `POST /api/v1/auth/login`
- **Rate Limit**: 5분간 5회
- **요청**: LoginRequest
- **응답**: 200 OK, LoginResponse

### 3. 소셜 로그인
- **URL**: `POST /api/v1/auth/social-login`
- **요청**: SocialLoginRequest
- **응답**: 200 OK, LoginResponse

### 4. 토큰 갱신
- **URL**: `POST /api/v1/auth/refresh`
- **헤더**: Refresh-Token
- **응답**: 200 OK, TokenResponse

### 5. 로그아웃
- **URL**: `POST /api/v1/auth/logout`
- **인증**: Required
- **응답**: 200 OK

### 6. 이메일 중복 확인
- **URL**: `POST /api/v1/auth/check-email`
- **Rate Limit**: 1분간 10회
- **요청**: EmailCheckRequest
- **응답**: 200 OK, EmailCheckResponse

### 7. 비밀번호 재설정
- **URL**: `POST /api/v1/auth/reset-password`
- **Rate Limit**: 10분간 3회
- **요청**: PasswordResetRequest
- **응답**: 200 OK

---

*Step 7-1a 완료: AuthController 구현 (7개 엔드포인트)*