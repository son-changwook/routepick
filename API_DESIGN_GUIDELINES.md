# RoutePick API 설계 가이드라인

## 📋 개요

RoutePick API는 클라이밍 관련 서비스를 위한 RESTful API입니다. 이 문서는 API 설계, 구현, 보안, 성능 최적화에 대한 가이드라인을 제공합니다.

## 🏗️ 아키텍처 원칙

### **모듈별 독립적인 설계**
- **API 모듈**: 일반 사용자용 API (`routepick-api`)
- **Admin 모듈**: 관리자용 API (`routepick-admin`)
- **공통 모듈**: 공유 도메인 및 유틸리티 (`routepick-common`)

### **빈 관리 원칙**
```java
// ✅ 올바른 방식: 모듈별 독립적인 설정
@Configuration
@ConditionalOnProperty(name = "spring.application.name", havingValue = "routepick-api")
public class ApiRedisConfig {
    @Bean("apiRedisTemplate")
    public RedisTemplate<String, Object> apiRedisTemplate() { ... }
}

// ❌ 피해야 할 방식: 임시 해결책
@Qualifier("routepickStringRedisTemplate") // 임시 방편
```

## 📚 RESTful API 설계

### **리소스 중심 설계**

#### **1. 클라이밍장 (Climbing Gyms)**
```
GET    /api/climbing-gyms              # 클라이밍장 목록
GET    /api/climbing-gyms/{id}         # 특정 클라이밍장 정보
POST   /api/climbing-gyms              # 클라이밍장 등록 (관리자)
PUT    /api/climbing-gyms/{id}         # 클라이밍장 정보 수정 (관리자)
DELETE /api/climbing-gyms/{id}         # 클라이밍장 삭제 (관리자)
```

#### **2. 클라이밍 루트 (Routes)**
```
GET    /api/routes                     # 루트 목록 (필터링 가능)
GET    /api/routes/{id}                # 특정 루트 정보
POST   /api/routes                     # 루트 등록 (관리자)
PUT    /api/routes/{id}                # 루트 정보 수정 (관리자)
DELETE /api/routes/{id}                # 루트 삭제 (관리자)
```

#### **3. 사용자 활동 (User Activities)**
```
GET    /api/users/{userId}/activities  # 사용자 활동 기록
POST   /api/users/{userId}/activities  # 활동 기록 추가
PUT    /api/activities/{id}            # 활동 기록 수정
DELETE /api/activities/{id}            # 활동 기록 삭제
```

#### **4. 인증 및 사용자 관리**
```
POST   /api/auth/signup               # 회원가입
POST   /api/auth/login                # 로그인
POST   /api/auth/logout               # 로그아웃
POST   /api/auth/refresh              # 토큰 갱신
GET    /api/auth/me                   # 현재 사용자 정보
PUT    /api/users/profile             # 프로필 수정
```

### **표준 응답 형식**

#### **성공 응답**
```json
{
  "success": true,
  "code": 200,
  "message": "성공적으로 처리되었습니다.",
  "data": {
    "userId": 123,
    "userName": "홍길동",
    "nickName": "climber123",
    "email": "user@example.com"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### **에러 응답**
```json
{
  "success": false,
  "code": 400,
  "message": "잘못된 요청입니다.",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "이메일 형식이 올바르지 않습니다.",
    "field": "email",
    "details": "올바른 이메일 형식을 입력해주세요."
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

## 🔐 보안 설계

### **인증 및 인가**

#### **JWT 토큰 구조**
```json
{
  "sub": "123",                    // userId (String)
  "userName": "홍길동",              // 사용자 실명
  "nickName": "climber123",        // 사용자 닉네임
  "email": "user@example.com",
  "roles": ["USER"],
  "iat": 1640995200,               // 발급 시간
  "exp": 1640998800                // 만료 시간
}
```

#### **토큰 관리**
- **액세스 토큰**: 1시간 (API 호출용)
- **리프레시 토큰**: 30일 (토큰 갱신용)
- **블랙리스트**: Redis 기반 무효화 관리

### **Spring Security 사용자 정보 구분**

⚠️ **중요**: `userName`과 `nickName`의 혼동을 주의하세요!

```java
// Spring Security 컨벤션
public String getUsername() {
    return String.valueOf(userId); // userId를 문자열로 반환
}

// 실제 사용자 정보
public String getUserName() {
    return userName; // 사용자 실명
}

public String getNickName() {
    return nickName; // 사용자 닉네임
}
```

**구분**:
- `getUsername()`: Spring Security용 (userId)
- `getUserName()`: 실제 사용자 이름
- `getNickName()`: 사용자 닉네임

### **보안 고려사항**

#### **1. 입력 검증**
```java
@Valid @RequestBody SignupRequest request
```

#### **2. SQL Injection 방지**
- MyBatis 파라미터 바인딩 사용
- PreparedStatement 활용
- 입력값 검증 및 이스케이프

#### **3. XSS 방지**
- 입력값 필터링
- 출력값 인코딩
- CSP (Content Security Policy) 적용

#### **4. Rate Limiting**
```java
// IP 기반: 1분에 100회
// 이메일 기반: 1시간에 10회
// 사용자 기반: 1분에 200회
```

## 📊 데이터 모델

### **핵심 엔티티**

#### **1. 사용자 (User)**
```java
User {
    userId: Long,           // 사용자 식별자
    userName: String,       // 실명
    nickName: String,       // 닉네임
    email: String,          // 이메일
    password: String,       // 암호화된 비밀번호
    status: UserStatus,     // 계정 상태
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime
}
```

#### **2. 클라이밍장 (ClimbingGym)**
```java
ClimbingGym {
    gymId: Long,            // 클라이밍장 식별자
    name: String,           // 클라이밍장명
    location: String,       // 위치
    address: String,        // 주소
    phone: String,          // 연락처
    description: String,    // 설명
    status: GymStatus,      // 상태
    routes: List<Route>     // 루트 목록
}
```

#### **3. 클라이밍 루트 (Route)**
```java
Route {
    routeId: Long,          // 루트 식별자
    name: String,           // 루트명
    difficulty: String,     // 난이도
    grade: String,          // 등급
    gymId: Long,            // 클라이밍장 ID
    description: String,    // 설명
    status: RouteStatus,    // 상태
    userActivities: List<UserActivity>
}
```

#### **4. 사용자 활동 (UserActivity)**
```java
UserActivity {
    activityId: Long,       // 활동 식별자
    userId: Long,           // 사용자 ID
    routeId: Long,          // 루트 ID
    status: ActivityStatus, // 완료 상태
    attemptCount: Integer,  // 시도 횟수
    completedAt: LocalDateTime,
    notes: String           // 메모
}
```

## 🔍 검색 및 필터링

### **검색 API**
```
GET /api/routes?search=keyword&difficulty=5.10&gymId=1
GET /api/climbing-gyms?location=서울&status=ACTIVE
GET /api/users/{userId}/activities?status=COMPLETED&dateFrom=2024-01-01
```

### **페이지네이션**
```json
{
  "success": true,
  "data": {
    "content": [...],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "totalElements": 150,
      "totalPages": 8
    }
  }
}
```

## 📡 WebSocket 엔드포인트

### **실시간 알림**
```
WS /ws/notifications/{userId}  # 사용자별 알림
WS /ws/admin/notifications     # 관리자 알림
```

### **메시지 형식**
```json
{
  "type": "NOTIFICATION",
  "data": {
    "title": "새로운 루트가 등록되었습니다.",
    "message": "클라이밍장 A에 새로운 루트가 추가되었습니다.",
    "timestamp": "2024-01-01T12:00:00Z"
  }
}
```

## 📁 파일 업로드

### **프로필 이미지**
```
POST /api/users/profile/image
Content-Type: multipart/form-data

파일 크기: 최대 5MB
지원 형식: JPG, PNG, GIF
```

### **클라이밍장 이미지**
```
POST /api/climbing-gyms/{id}/images
Content-Type: multipart/form-data

파일 크기: 최대 10MB
지원 형식: JPG, PNG, GIF
최대 개수: 10개
```

## ⚡ 성능 최적화

### **캐싱 전략**
- **Redis 캐싱**: 자주 조회되는 데이터
- **로컬 캐싱**: 설정 정보, 상수
- **CDN**: 정적 파일 (이미지, CSS, JS)

### **데이터베이스 최적화**
- **인덱싱**: 자주 조회되는 컬럼
- **쿼리 최적화**: N+1 문제 방지
- **커넥션 풀**: HikariCP 사용

### **API 응답 최적화**
- **압축**: Gzip 사용
- **지연 로딩**: 필요한 데이터만 조회
- **배치 처리**: 대량 데이터 처리

## 🧪 테스트 가이드라인

### **단위 테스트**
```java
@Test
void shouldCreateUserSuccessfully() {
    // Given
    SignupRequest request = new SignupRequest("test@example.com", "password");
    
    // When
    SignupResponse response = authService.signup(request);
    
    // Then
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getUserId()).isNotNull();
}
```

### **통합 테스트**
```java
@Test
void shouldReturnUserProfile() {
    // Given
    String token = generateTestToken();
    
    // When
    ApiResponse<UserProfile> response = userController.getProfile(token);
    
    // Then
    assertThat(response.getCode()).isEqualTo(200);
    assertThat(response.getData().getUserName()).isEqualTo("테스트");
}
```

### **API 테스트**
```bash
# Swagger UI 사용
http://localhost:8080/swagger-ui.html

# curl 예시
curl -X GET "http://localhost:8080/api/users/me" \
  -H "Authorization: Bearer {token}"
```

## 📝 에러 코드 표준화

### **HTTP 상태 코드**
- **200**: 성공
- **201**: 생성 성공
- **400**: 잘못된 요청
- **401**: 인증 실패
- **403**: 권한 없음
- **404**: 리소스 없음
- **500**: 서버 오류

### **비즈니스 에러 코드**
```java
public enum ErrorCode {
    // 인증 관련
    INVALID_CREDENTIALS("AUTH001", "잘못된 인증 정보"),
    TOKEN_EXPIRED("AUTH002", "토큰이 만료되었습니다"),
    
    // 사용자 관련
    USER_NOT_FOUND("USER001", "사용자를 찾을 수 없습니다"),
    EMAIL_ALREADY_EXISTS("USER002", "이미 존재하는 이메일입니다"),
    
    // 클라이밍장 관련
    GYM_NOT_FOUND("GYM001", "클라이밍장을 찾을 수 없습니다"),
    ROUTE_NOT_FOUND("ROUTE001", "루트를 찾을 수 없습니다")
}
```

## 🔧 개발 환경 설정

### **로컬 개발**
```bash
# 1. 데이터베이스 시작
docker-compose up -d mysql redis

# 2. API 서버 실행
./gradlew :routepick-api:bootRun

# 3. Swagger UI 접속
# http://localhost:8080/swagger-ui.html
```

### **환경변수 설정**
```bash
# .env 파일 생성
cp env.example .env

# 필수 환경변수 설정
JWT_SECRET=your-super-secure-jwt-secret-key
DB_PASSWORD=your-secure-database-password
SPRING_REDIS_PASSWORD=your-secure-redis-password
```

## 📊 모니터링 및 로깅

### **로깅 레벨**
```yaml
logging:
  level:
    com.routepick.api: DEBUG
    org.springframework.security: DEBUG
    org.mybatis: DEBUG
```

### **성능 모니터링**
- **응답 시간**: 평균, 95th percentile
- **에러율**: 4xx, 5xx 에러 비율
- **처리량**: 초당 요청 수 (RPS)

## 🚀 배포 체크리스트

### **배포 전 확인사항**
- [ ] 환경변수 설정 완료
- [ ] 보안 키 강화
- [ ] 데이터베이스 마이그레이션
- [ ] API 테스트 완료
- [ ] 성능 테스트 완료
- [ ] 보안 테스트 완료

### **배포 후 확인사항**
- [ ] 서비스 정상 동작
- [ ] 로그 모니터링
- [ ] 알림 설정
- [ ] 백업 정책 수립

---

**⚠️ 중요**: 이 가이드라인은 RoutePick 프로젝트의 API 설계 및 구현을 위한 표준입니다. 
모든 개발자는 이 가이드라인을 준수하여 일관성 있는 API를 구현해야 합니다.
