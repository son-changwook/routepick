# Step 7-1b: EmailController 구현

> 이메일 인증 관련 RESTful API Controller 완전 구현  
> 생성일: 2025-08-22  
> 기반: step6-1b_email_service.md, Redis 기반 인증 코드 관리

---

## 🎯 설계 원칙

- **비동기 처리**: @Async 이메일 발송
- **Redis 캐싱**: 인증 코드 TTL 관리
- **Rate Limiting**: 재발송 제한
- **보안 강화**: 6자리 랜덤 코드, 만료 시간
- **에러 처리**: 명확한 에러 메시지

---

## 📧 EmailController 구현

### EmailController.java
```java
package com.routepick.controller.email;

import com.routepick.common.ApiResponse;
import com.routepick.dto.email.request.EmailVerificationRequest;
import com.routepick.dto.email.response.EmailVerificationResponse;
import com.routepick.service.email.EmailService;
import com.routepick.annotation.RateLimited;
import com.routepick.exception.email.EmailException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 이메일 관리 Controller
 * - 이메일 인증 코드 발송
 * - 인증 코드 검증
 * - Redis 기반 코드 관리
 * - Rate Limiting 적용
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/email")
@RequiredArgsConstructor
@Validated
@Tag(name = "이메일 관리", description = "이메일 인증 및 알림 API")
public class EmailController {
    
    private final EmailService emailService;
    
    // ===== 이메일 인증 =====
    
    /**
     * 이메일 인증 코드 발송
     * - 6자리 랜덤 코드 생성
     * - Redis 저장 (TTL 5분)
     * - 재발송 쿨다운 30초
     * - 비동기 발송 처리
     */
    @PostMapping("/verify")
    @Operation(summary = "이메일 인증 코드 발송", description = "회원가입 또는 이메일 변경 시 인증 코드 발송")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "인증 코드 발송 성공",
                content = @Content(schema = @Schema(implementation = EmailVerificationResponse.class))),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 이메일 주소"),
        @SwaggerApiResponse(responseCode = "409", description = "이미 인증된 이메일"),
        @SwaggerApiResponse(responseCode = "429", description = "재발송 쿨다운 중")
    })
    @RateLimited(requests = 5, period = 300, keyStrategy = RateLimited.KeyStrategy.IP) // 5분간 5회 제한
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> sendVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("이메일 인증 코드 발송 요청: email={}", request.getEmail());
        
        try {
            // IP 주소 추출 (보안 로깅용)
            String clientIp = extractClientIp(httpRequest);
            
            // 재발송 쿨다운 확인
            if (!emailService.checkCooldown(request.getEmail())) {
                log.warn("이메일 재발송 쿨다운 중: email={}, ip={}", request.getEmail(), clientIp);
                
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("EMAIL_COOLDOWN", 
                        "인증 코드 재발송은 30초 후에 가능합니다."));
            }
            
            // 이미 인증된 이메일인지 확인
            if (emailService.isEmailVerified(request.getEmail())) {
                log.info("이미 인증된 이메일: email={}", request.getEmail());
                
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("EMAIL_ALREADY_VERIFIED", 
                        "이미 인증이 완료된 이메일입니다."));
            }
            
            // 인증 코드 생성 및 발송 (비동기)
            String verificationCode = emailService.generateVerificationCode();
            CompletableFuture<Boolean> sendResult = emailService.sendVerificationEmailAsync(
                request.getEmail(), 
                verificationCode
            );
            
            // Redis에 인증 코드 저장 (TTL 5분)
            emailService.saveVerificationCode(request.getEmail(), verificationCode);
            
            // 재발송 쿨다운 설정 (30초)
            emailService.setCooldown(request.getEmail());
            
            // 응답 생성
            EmailVerificationResponse response = EmailVerificationResponse.builder()
                .email(request.getEmail())
                .codeSent(true)
                .expiresIn(300) // 5분 (초 단위)
                .message("인증 코드가 이메일로 발송되었습니다. 5분 이내에 입력해주세요.")
                .sentAt(LocalDateTime.now())
                .build();
            
            log.info("이메일 인증 코드 발송 완료: email={}, ip={}", request.getEmail(), clientIp);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("이메일 인증 코드 발송 실패: email={}, error={}", 
                request.getEmail(), e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("EMAIL_SEND_FAILED", 
                    "인증 코드 발송 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
        }
    }
    
    /**
     * 이메일 인증 코드 재발송
     * - 기존 코드 무효화
     * - 새로운 코드 생성
     * - 재발송 횟수 제한
     */
    @PostMapping("/resend-verification")
    @Operation(summary = "인증 코드 재발송", description = "이메일 인증 코드를 다시 발송")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "재발송 성공",
                content = @Content(schema = @Schema(implementation = EmailVerificationResponse.class))),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 요청"),
        @SwaggerApiResponse(responseCode = "404", description = "발송 이력 없음"),
        @SwaggerApiResponse(responseCode = "429", description = "재발송 한도 초과")
    })
    @RateLimited(requests = 3, period = 600, keyStrategy = RateLimited.KeyStrategy.IP) // 10분간 3회 제한
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> resendVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("이메일 인증 코드 재발송 요청: email={}", request.getEmail());
        
        try {
            // IP 주소 추출
            String clientIp = extractClientIp(httpRequest);
            
            // 재발송 쿨다운 확인 (더 엄격한 기준 적용)
            if (!emailService.checkCooldown(request.getEmail())) {
                int remainingSeconds = emailService.getRemainingCooldownSeconds(request.getEmail());
                
                log.warn("이메일 재발송 쿨다운 중: email={}, remainingSeconds={}, ip={}", 
                    request.getEmail(), remainingSeconds, clientIp);
                
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("EMAIL_COOLDOWN", 
                        String.format("인증 코드 재발송은 %d초 후에 가능합니다.", remainingSeconds)));
            }
            
            // 발송 이력 확인
            if (!emailService.hasVerificationHistory(request.getEmail())) {
                log.warn("인증 코드 발송 이력 없음: email={}", request.getEmail());
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NO_VERIFICATION_HISTORY", 
                        "인증 코드 발송 이력이 없습니다. 먼저 인증 코드를 요청해주세요."));
            }
            
            // 재발송 횟수 확인
            int resendCount = emailService.getResendCount(request.getEmail());
            if (resendCount >= 5) { // 최대 5회 재발송 허용
                log.warn("이메일 재발송 한도 초과: email={}, count={}, ip={}", 
                    request.getEmail(), resendCount, clientIp);
                
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("RESEND_LIMIT_EXCEEDED", 
                        "인증 코드 재발송 한도를 초과했습니다. 고객센터에 문의해주세요."));
            }
            
            // 기존 코드 무효화
            emailService.invalidateVerificationCode(request.getEmail());
            
            // 새로운 인증 코드 생성 및 발송
            String newVerificationCode = emailService.generateVerificationCode();
            CompletableFuture<Boolean> sendResult = emailService.sendVerificationEmailAsync(
                request.getEmail(), 
                newVerificationCode
            );
            
            // Redis에 새 인증 코드 저장
            emailService.saveVerificationCode(request.getEmail(), newVerificationCode);
            
            // 재발송 횟수 증가
            emailService.incrementResendCount(request.getEmail());
            
            // 재발송 쿨다운 설정 (점진적 증가: 30초 * (재발송 횟수 + 1))
            int cooldownSeconds = 30 * (resendCount + 1);
            emailService.setCooldownWithDuration(request.getEmail(), cooldownSeconds);
            
            // 응답 생성
            EmailVerificationResponse response = EmailVerificationResponse.builder()
                .email(request.getEmail())
                .codeSent(true)
                .expiresIn(300) // 5분
                .message(String.format("인증 코드가 재발송되었습니다. (재발송 %d/5회)", resendCount + 1))
                .sentAt(LocalDateTime.now())
                .resendCount(resendCount + 1)
                .nextResendAvailableIn(cooldownSeconds)
                .build();
            
            log.info("이메일 인증 코드 재발송 완료: email={}, count={}, ip={}", 
                request.getEmail(), resendCount + 1, clientIp);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("이메일 인증 코드 재발송 실패: email={}, error={}", 
                request.getEmail(), e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("EMAIL_RESEND_FAILED", 
                    "인증 코드 재발송 중 오류가 발생했습니다."));
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 클라이언트 IP 주소 추출
     * - 프록시 환경 대응
     * - 보안 로깅용
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
    
    // ===== 내부 API (관리자용) =====
    
    /**
     * 이메일 인증 상태 확인 (내부용)
     * - 관리자 전용
     * - 디버깅 및 지원 목적
     */
    @GetMapping("/internal/verification-status")
    @Operation(summary = "[내부] 이메일 인증 상태 확인", description = "관리자 전용 API")
    @ApiResponses({
        @SwaggerApiResponse(responseCode = "200", description = "상태 조회 성공"),
        @SwaggerApiResponse(responseCode = "403", description = "권한 없음")
    })
    public ResponseEntity<ApiResponse<EmailVerificationStatusResponse>> getVerificationStatus(
            @RequestParam String email,
            @RequestHeader("X-Admin-Key") String adminKey) {
        
        // 관리자 키 검증
        if (!emailService.validateAdminKey(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("INVALID_ADMIN_KEY", "유효하지 않은 관리자 키입니다."));
        }
        
        EmailVerificationStatusResponse status = emailService.getVerificationStatus(email);
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
```

### EmailVerificationStatusResponse.java (추가 응답 DTO)
```java
package com.routepick.dto.email.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 이메일 인증 상태 응답 DTO (관리자용)
 */
@Getter
@Setter
@Builder
public class EmailVerificationStatusResponse {
    
    private String email;
    private boolean isVerified;
    private boolean hasActiveCode;
    private Integer remainingSeconds;
    private Integer resendCount;
    private LocalDateTime lastSentAt;
    private LocalDateTime verifiedAt;
}
```

---

## 🔒 보안 강화 기능

### 1. 인증 코드 생성 로직
```java
/**
 * 보안 강화된 인증 코드 생성
 * - SecureRandom 사용
 * - 6자리 숫자
 * - 예측 불가능한 패턴
 */
public String generateVerificationCode() {
    SecureRandom random = new SecureRandom();
    int code = 100000 + random.nextInt(900000); // 100000 ~ 999999
    return String.valueOf(code);
}
```

### 2. Redis 키 관리 전략
```java
/**
 * Redis 키 네이밍 규칙
 * - 명확한 네임스페이스
 * - TTL 자동 관리
 */
public class RedisKeyGenerator {
    
    // 인증 코드 키
    public static String verificationKey(String email) {
        return String.format("email:verification:%s", email);
    }
    
    // 쿨다운 키
    public static String cooldownKey(String email) {
        return String.format("email:cooldown:%s", email);
    }
    
    // 재발송 횟수 키
    public static String resendCountKey(String email) {
        return String.format("email:resend:count:%s", email);
    }
}
```

### 3. 비동기 이메일 발송 설정
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("EmailAsync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

---

## 📊 성능 최적화

### 1. Redis 캐싱 전략
- **인증 코드**: TTL 5분
- **쿨다운**: TTL 30초 (점진적 증가)
- **재발송 횟수**: TTL 1시간

### 2. 비동기 처리
- 이메일 발송은 비동기로 처리
- 응답 시간 최소화
- 실패 시 재시도 로직

### 3. Rate Limiting
- IP 기반 제한
- 인증 코드 발송: 5분간 5회
- 재발송: 10분간 3회

---

## 📋 API 명세

### 1. 이메일 인증 코드 발송
- **URL**: `POST /api/v1/email/verify`
- **Rate Limit**: 5분간 5회
- **요청**: EmailVerificationRequest
- **응답**: 200 OK, EmailVerificationResponse

### 2. 인증 코드 재발송
- **URL**: `POST /api/v1/email/resend-verification`
- **Rate Limit**: 10분간 3회
- **요청**: EmailVerificationRequest
- **응답**: 200 OK, EmailVerificationResponse

---

*Step 7-1b 완료: EmailController 구현 (2개 엔드포인트 + 1개 내부 API)*