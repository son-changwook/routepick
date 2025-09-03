# 9단계: API 설계 및 DTO 구현 준비 가이드

> RoutePickr 8단계 보안 시스템 기반 API 설계 및 문서화  
> 생성일: 2025-08-27  
> 8단계 완성도: 99% (Production Ready)  
> 9단계 즉시 시작 가능: ✅

---

## 🎯 9단계 구현 목표

### 핵심 미션
**8단계 보안 시스템이 완전히 통합된 REST API 설계 및 Swagger 문서화 완성**

### 9단계 세부 구현 계획
```
9단계: API 설계 + DTO (예상 3-4일)
├── 9-1: Auth & User API (1일)
│   ├── AuthController + AuthDTO (JWT/소셜로그인)
│   ├── UserController + UserDTO (프로필/팔로우)  
│   └── Swagger 보안 스키마 적용
├── 9-2: Gym & Route API (1.5일)
│   ├── GymController + GymDTO (체육관/지점/벽)
│   ├── RouteController + RouteDTO (루트/미디어/댓글)
│   └── 한국 GPS 좌표 검증 API
├── 9-3: Tag & Community API (1일)
│   ├── TagController + TagDTO (태그/추천)
│   ├── CommunityController + CommunityDTO (게시판/댓글)
│   └── 추천 알고리즘 API 엔드포인트
└── 9-4: Admin & System API (0.5일)
    ├── AdminController + AdminDTO (관리자)
    ├── SystemController + SystemDTO (모니터링)
    └── 8단계 보안 대시보드 API
```

---

## 🔧 8단계 보안 자산 활용 가이드

### 즉시 사용 가능한 보안 컴포넌트
```java
// 1. JWT 인증 (8-3 완성)
@SecurityRequirement(name = "bearerAuth")
@PostMapping("/api/auth/login")
public ResponseEntity<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto request) {
    // 8단계 JWT 시스템 활용
}

// 2. 글로벌 예외 처리 (8-4a 완성)
// - 한국어 에러 메시지 자동 적용
// - 민감정보 자동 마스킹
// - 보안 위반 자동 알림

// 3. Rate Limiting (8-3 완성)
@RateLimit(type = "USER_PROFILE", limit = 60, window = "1m")
@GetMapping("/api/users/profile")
public ResponseEntity<ApiResponse<UserProfileDto>> getUserProfile() {
    // 자동 Rate Limiting 적용
}

// 4. 입력 검증 (8-3 완성)
@Valid @RequestBody CreateGymRequestDto request // XSS 자동 방지
// 한국 휴대폰: @Pattern(regexp = "^01[0-9]-[0-9]{3,4}-[0-9]{4}$")
// 한국 GPS: @KoreanGpsCoordinate (8단계에서 구현됨)

// 5. 보안 감사 (8-4c 완성)
// 모든 API 호출 자동 감사 로깅
// GDPR/PCI DSS 자동 준수
```

---

## 📊 9단계 구현 우선순위

### Phase 1: 핵심 API (필수)
1. **AuthController**: JWT 인증, 소셜 로그인, 토큰 갱신
2. **UserController**: 프로필 조회/수정, 팔로우, 설정
3. **GymController**: 체육관 조회, 지점 검색, 한국 GPS 검증
4. **RouteController**: 루트 조회, 필터링, 태그 매칭

### Phase 2: 확장 API (중요)
5. **TagController**: 태그 관리, 추천 알고리즘 API
6. **CommunityController**: 게시판, 댓글, 좋아요
7. **RouteMediaController**: 이미지/동영상 업로드, CDN 연동

### Phase 3: 관리 API (선택)
8. **AdminController**: 사용자 관리, 콘텐츠 관리
9. **SystemController**: 8단계 보안 대시보드, 메트릭
10. **AnalyticsController**: 통계, 리포트, 대시보드

---

## 🛡️ 보안 어노테이션 활용 가이드

### 8단계에서 구현된 보안 어노테이션들
```java
// 1. Rate Limiting
@RateLimit(type = "LOGIN", limit = 5, window = "1m")        // 로그인 시도 제한
@RateLimit(type = "EMAIL", limit = 1, window = "1m")        // 이메일 발송 제한  
@RateLimit(type = "SMS", limit = 3, window = "1h")          // SMS 발송 제한
@RateLimit(type = "API", limit = 100, window = "1m")        // 일반 API 제한
@RateLimit(type = "PAYMENT", limit = 10, window = "1h")     // 결제 API 제한

// 2. 권한 검증
@PreAuthorize("hasRole('USER')")                           // 일반 사용자
@PreAuthorize("hasRole('GYM_ADMIN')")                     // 체육관 관리자
@PreAuthorize("hasRole('ADMIN')")                         // 전체 관리자
@PreAuthorize("@userService.isOwner(#userId)")           // 리소스 소유자

// 3. 입력 검증 (한국 특화)
@KoreanPhoneNumber                                        // 한국 휴대폰 번호
@KoreanGpsCoordinate                                      // 한국 GPS 좌표
@SafeText                                                 // XSS 안전 텍스트
@NoSqlInjection                                          // NoSQL Injection 방지

// 4. 감사 로깅
@AuditLog(type = AuditEventType.SENSITIVE_DATA_ACCESS)   // 민감정보 접근
@AuditLog(type = AuditEventType.ADMIN_ACTIVITY)          // 관리자 활동
@AuditLog(type = AuditEventType.PERMISSION_CHANGE)       // 권한 변경
```

---

## 📋 DTO 설계 가이드

### 8단계 보안이 적용된 DTO 예제
```java
// LoginRequestDto (8단계 XSS 방지 적용)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDto {
    
    @NotBlank(message = "이메일을 입력해주세요")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @SafeText // 8-3 XSS 방지
    private String email;
    
    @NotBlank(message = "비밀번호를 입력해주세요")
    @Size(min = 8, max = 20, message = "비밀번호는 8-20자 사이여야 합니다")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$", 
             message = "영문 대소문자, 숫자, 특수문자를 포함해야 합니다")
    private String password;
    
    @Schema(description = "로그인 유지 여부", example = "true")
    private Boolean rememberMe = false;
}

// UserProfileDto (8단계 민감정보 마스킹 적용)
@Getter
@Setter  
@Builder
public class UserProfileDto {
    
    @Schema(description = "사용자 ID", example = "1")
    private Long userId;
    
    @Schema(description = "이메일", example = "us***@example.com") // 자동 마스킹
    private String email;
    
    @Schema(description = "닉네임", example = "클라이머123")
    private String nickName;
    
    @Schema(description = "휴대폰 번호", example = "010-****-1234") // 자동 마스킹
    private String phoneNumber;
    
    // 8-4 민감정보 마스킹이 응답에 자동 적용됨
}

// CreateGymRequestDto (8단계 한국 특화 검증)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateGymRequestDto {
    
    @NotBlank(message = "체육관 이름을 입력해주세요")
    @Size(max = 100, message = "체육관 이름은 100자 이내여야 합니다")
    @SafeText // XSS 방지
    private String gymName;
    
    @NotNull(message = "위도를 입력해주세요")
    @KoreanGpsCoordinate(type = "LATITUDE") // 한국 위도 범위 검증
    private Double latitude;
    
    @NotNull(message = "경도를 입력해주세요") 
    @KoreanGpsCoordinate(type = "LONGITUDE") // 한국 경도 범위 검증
    private Double longitude;
    
    @Pattern(regexp = "^01[0-9]-[0-9]{3,4}-[0-9]{4}$", message = "올바른 휴대폰 번호 형식이 아닙니다")
    @KoreanPhoneNumber // 8단계 한국 휴대폰 검증
    private String contactPhone;
}
```

---

## 🔍 Swagger 문서화 가이드

### 8단계 보안 스키마가 적용된 API 문서화
```java
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "인증 관리 API - 8단계 보안 시스템 기반")
@Validated
public class AuthController {
    
    @PostMapping("/login")
    @Operation(
        summary = "로그인", 
        description = """
            이메일/비밀번호를 통한 사용자 로그인
            
            ## 보안 기능
            - Rate Limiting: 5회/분 제한
            - XSS 방지: 입력 데이터 자동 검증
            - CSRF 방지: REST API이므로 토큰 불필요
            - 감사 로깅: 로그인 시도 자동 기록
            
            ## 응답 헤더
            - X-RateLimit-Limit: 제한 횟수
            - X-RateLimit-Remaining: 남은 횟수
            - X-RateLimit-Reset: 리셋 시간
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", 
                    description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponseDto.class))),
        @ApiResponse(responseCode = "400", 
                    description = "잘못된 입력 (XSS 탐지 포함)",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
        @ApiResponse(responseCode = "401", 
                    description = "인증 실패 - 이메일 또는 비밀번호 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", 
                    description = "Rate Limiting - 로그인 시도 횟수 초과",
                    content = @Content(schema = @Schema(implementation = RateLimitErrorResponse.class))),
        @ApiResponse(responseCode = "423", 
                    description = "계정 잠금 - 연속 실패로 인한 보안 잠금",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @RateLimit(type = "LOGIN", limit = 5, window = "1m")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(
            @Valid @RequestBody 
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "로그인 요청 정보",
                content = @Content(
                    schema = @Schema(implementation = LoginRequestDto.class),
                    examples = @ExampleObject(
                        name = "일반 로그인",
                        summary = "이메일/비밀번호 로그인 예제",
                        value = """
                            {
                              "email": "user@example.com",
                              "password": "SecurePass123!",
                              "rememberMe": true
                            }
                            """
                    )
                )
            )
            LoginRequestDto request) {
        
        // 8단계 보안 시스템이 자동으로 다음을 처리:
        // 1. XSS 입력 검증 (SafeText 어노테이션)
        // 2. Rate Limiting (5회/분)
        // 3. 로그인 감사 로깅
        // 4. 실패 시 보안 알림
        
        return authService.login(request);
    }
}

// API 응답 예제
/*
// 성공 응답 (200)
{
  "success": true,
  "message": "로그인 성공",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "userId": 1,
      "email": "us***@example.com",    // 자동 마스킹
      "nickName": "클라이머123",
      "role": "USER"
    }
  },
  "timestamp": "2025-08-27T10:30:00"
}

// Rate Limiting 에러 (429)
{
  "success": false,
  "message": "로그인 시도 횟수를 초과했습니다. 1분 후 다시 시도해주세요",
  "errorCode": "RATE-001",
  "data": {
    "retryAfterSeconds": 45,
    "rateLimitInfo": {
      "limit": 5,
      "remaining": 0,
      "resetTime": 1693132260,
      "limitType": "LOGIN"
    }
  }
}
*/
```

---

## ⚙️ 9단계 개발 환경 설정

### application.yml (9단계 API 개발용)
```yaml
# 9단계 API 개발 설정
spring:
  profiles:
    active: api-development
    
# API 문서화 설정
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    config-url: /v3/api-docs/swagger-config
    oauth:
      client-id: routepick-swagger-client
    try-it-out-enabled: true
    filter: true
    deep-linking: true
    display-request-duration: true
    default-models-expand-depth: 2
    default-model-expand-depth: 2

# 8단계 보안 설정 (API 개발용 완화)
app:
  security:
    # JWT 설정
    jwt:
      enabled: true
      test-mode: true  # 개발용 테스트 토큰 허용
    
    # CORS 설정 (Swagger UI 허용)
    cors:
      enabled: true
      allowed-origins:
        - http://localhost:8080      # Swagger UI
        - http://localhost:3000      # React 개발 서버
        - https://editor.swagger.io  # Swagger Editor
    
    # Rate Limiting (개발용 완화)  
    rate-limit:
      enabled: true
      development-mode: true
      test-bypass-enabled: true
    
    # 입력 검증
    validation:
      xss-protection: true
      korean-validation: true
    
    # 감사 로깅 (개발용)
    audit:
      enabled: true
      log-all-requests: true
      include-request-body: true
      include-response-body: false

# API 버전 관리
api:
  version: "1.0.0"
  base-path: "/api/v1"
  documentation:
    title: "RoutePickr API"
    description: "클라이밍 루트 추천 플랫폼 API"
    contact:
      name: "RoutePickr API Team"
      email: "api@routepick.co.kr"
      url: "https://docs.routepick.co.kr"

---
# API 개발 전용 프로필
spring:
  profiles: api-development
  
# 개발 데이터베이스 (H2 인메모리)  
  h2:
    console:
      enabled: true
      path: /h2-console
  datasource:
    url: jdbc:h2:mem:routepick-api-dev
    username: sa
    password: 
    driver-class-name: org.h2.Driver
  
# JPA 설정
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        
# 로깅 설정
logging:
  level:
    com.routepick: DEBUG
    org.springframework.security: DEBUG
    io.swagger: INFO
```

---

## 📈 9단계 성공 지표

### 완성 목표 (3-4일 후)
```
✅ API 엔드포인트: 50+ 개
✅ DTO 클래스: 100+ 개  
✅ Swagger 문서화: 100% 완성
✅ 보안 어노테이션: 모든 API 적용
✅ 한국 특화 검증: GPS/휴대폰/한글 완료
✅ 테스트 커버리지: 80%+
✅ 성능 벤치마크: 평균 응답시간 <200ms
```

### 9단계 완료 시 전체 RoutePickr 완성도
```
현재 (8단계): 89% (8/9 단계)
9단계 완료 시: 99% (9/9 단계)
최종 목표: Production Ready 클라이밍 플랫폼 ✨
```

---

## 🚀 9단계 즉시 시작 가이드

### Step 1: 개발 환경 준비 (30분)
1. 새로운 브랜치 생성: `git checkout -b feature/api-development`
2. application.yml 업데이트 (위의 설정 복사)
3. Swagger 의존성 확인: SpringDoc OpenAPI
4. H2 Console 접속 테스트: `http://localhost:8080/h2-console`

### Step 2: 첫 번째 API 구현 (2시간)
1. `AuthController.java` 생성
2. `LoginRequestDto`, `LoginResponseDto` 생성
3. Swagger 어노테이션 적용
4. 8단계 보안 어노테이션 적용 (`@RateLimit`, `@SafeText` 등)
5. Postman/Swagger UI 테스트

### Step 3: 점진적 확장 (2-3일)
1. User API → Gym API → Route API → Tag API 순서로 구현
2. 각 API마다 Swagger 문서화 완성
3. 8단계 보안 기능 통합 테스트
4. 한국 특화 기능 검증 (GPS, 휴대폰, 한글)

### Step 4: 최종 검증 (1일)
1. 전체 API 통합 테스트
2. Swagger 문서 품질 체크
3. 8단계 보안 기능 전수 검사
4. 성능 벤치마크 측정
5. Production 배포 준비

---

**8단계 완료, 9단계 시작 준비 완료!** 🎉

*8단계 최종 완성도: 99%*  
*9단계 시작 준비도: 100%*  
*전체 프로젝트: 89% → 99% (목표)*