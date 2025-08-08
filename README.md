# RoutePick 프로젝트

## 📋 프로젝트 개요

RoutePick은 클라이밍 관련 서비스로, 사용자가 원하는 클라이밍 문제의 정보를 빠르고 간편하게 찾을 수 있는 크로스 플랫폼 웹/앱 서비스입니다.

## 🏗️ 아키텍처

### **백엔드 (Backend)**
- **프레임워크**: Spring Boot 3.2.3 (Gradle)
- **언어**: Java 17
- **ORM**: MyBatis
- **데이터베이스**: MySQL 8.0
- **캐시**: Redis 7.2
- **인증**: JWT (JSON Web Token)
- **보안**: Spring Security

### **프론트엔드 (Frontend)**
- **웹**: JavaScript + TypeScript
- **앱**: React Native
- **상태관리**: Redux
- **통신**: WebSocket + REST API

### **인프라 (Infrastructure)**
- **클라우드**: AWS
  - **서버**: EC2
  - **저장소**: S3
  - **도메인**: Route 53
  - **데이터베이스**: RDS (MySQL)
  - **SSL**: ACM
  - **이메일**: SES
- **푸시 알림**: Firebase Cloud Messaging
- **컨테이너**: Docker + Docker Compose

## 📁 프로젝트 구조

```
routepick/
├── backend/
│   ├── routepick-api/          # API 서버 (사용자용)
│   ├── routepick-admin/        # Admin 서버 (관리자용)
│   └── routepick-common/       # 공통 모듈
├── frontend/
│   ├── routepick-web/          # 웹 프론트엔드
│   └── routepick-app/          # 모바일 앱
├── docker-compose.yml          # 개발 환경 설정
├── .env                        # 환경변수 (보안)
└── SECURITY_SETUP.md          # 보안 설정 가이드
```

## 🚀 개발 환경 설정

### **1. 필수 요구사항**
- Java 17
- Docker & Docker Compose
- Node.js 18+
- MySQL Workbench (선택사항)

### **2. 환경 설정**
```bash
# 1. 프로젝트 클론
git clone <repository-url>
cd routepick

# 2. 환경변수 설정
cp env.example .env
# .env 파일 편집 (JWT_SECRET, DB_PASSWORD 등)

# 3. 개발 환경 시작
docker-compose up -d mysql redis

# 4. API 서버 실행
./gradlew :routepick-api:bootRun
```

### **3. 접속 정보**
- **API 서버**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Admin 서버**: http://localhost:8081 (예정)
- **웹 프론트엔드**: http://localhost:3000 (예정)

## 🔐 보안 설정

### **중요: 환경변수 설정**
프로덕션 환경 배포 전 반드시 다음 환경변수를 설정하세요:

```bash
# JWT 설정 (32자 이상, 영문/숫자/특수문자 포함)
JWT_SECRET=your-super-secure-jwt-secret-key-here-minimum-32-characters

# 데이터베이스 설정
DB_PASSWORD=your-secure-database-password

# Redis 설정
SPRING_REDIS_PASSWORD=your-secure-redis-password

# 이메일 설정
MAIL_PASSWORD=your-app-password
```

### **보안 키 생성**
```bash
# JWT Secret 생성
openssl rand -base64 32

# 데이터베이스 비밀번호 생성
openssl rand -base64 16

# Redis 비밀번호 생성
openssl rand -base64 16
```

## 🏛️ 아키텍처 설계

### **모듈별 독립적인 설정**
- **API 모듈**: `ApiRedisConfig` (Redis DB 0)
- **Admin 모듈**: `AdminRedisConfig` (Redis DB 1)
- **조건부 활성화**: `@ConditionalOnProperty`
- **빈 이름 규칙**: `{모듈명}XXX` (apiRedisTemplate, adminRedisTemplate)

### **Spring Security 사용자 정보 구분**
⚠️ **중요**: `userName`과 `Usaername`의 혼동을 주의하세요!

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

## 📚 API 설계 가이드

### **RESTful API 원칙**
- **리소스 중심**: `/climbing-gyms`, `/routes`, `/users`
- **HTTP 메서드**: GET, POST, PUT, DELETE
- **상태 코드**: 200, 201, 400, 401, 404, 500
- **표준 응답**: `ApiResponse<T>` 형식

### **인증/인가**
- **JWT 토큰**: 액세스 토큰 (1시간) + 리프레시 토큰 (30일)
- **역할 기반**: USER, ADMIN, MODERATOR
- **토큰 블랙리스트**: Redis 기반 무효화 관리

### **데이터 모델**
```java
// 사용자 정보
User {
    userId: Long,
    userName: String,    // 실명
    nickName: String,    // 닉네임
    email: String,
    status: UserStatus
}

// 클라이밍장 정보
ClimbingGym {
    gymId: Long,
    name: String,
    location: String,
    routes: List<Route>
}

// 클라이밍 루트 정보
Route {
    routeId: Long,
    name: String,
    difficulty: String,
    gymId: Long,
    userActivities: List<UserActivity>
}
```

## 🔧 개발 가이드

### **새로운 모듈 추가 시**
1. **모듈별 설정 클래스 생성**:
   ```java
   @Configuration
   @ConditionalOnProperty(name = "spring.application.name", havingValue = "routepick-mobile")
   public class MobileRedisConfig { ... }
   ```

2. **빈 이름 규칙 준수**:
   - `{모듈명}RedisTemplate`
   - `{모듈명}StringRedisTemplate`
   - `{모듈명}RedisConnectionFactory`

3. **Redis 데이터베이스 분리**:
   - API: DB 0
   - Admin: DB 1
   - Mobile: DB 2 (예정)

### **API 구현 시 주의사항**
1. **일관된 응답 형식**:
   ```java
   ApiResponse<UserDTO> response = ApiResponse.success(userData);
   ```

2. **적절한 예외 처리**:
   ```java
   throw new ResourceNotFoundException("User not found");
   ```

3. **입력 검증**:
   ```java
   @Valid @RequestBody SignupRequest request
   ```

4. **보안 고려사항**:
   - SQL Injection 방지
   - XSS 방지
   - Rate Limiting 적용

## 🚀 배포 가이드

### **개발 환경**
```bash
# 1. 환경변수 설정
cp env.example .env
# .env 파일 편집

# 2. 데이터베이스 시작
docker-compose up -d mysql redis

# 3. API 서버 실행
./gradlew :routepick-api:bootRun

# 4. Swagger UI 접속
# http://localhost:8080/swagger-ui.html
```

### **프로덕션 환경**
1. **환경변수 필수 설정**
2. **보안 키 강화**
3. **SSL/TLS 적용**
4. **모니터링 설정**
5. **백업 정책 수립**

## 📝 체크리스트

### **개발 시작 전**
- [ ] Java 17 설치
- [ ] Docker & Docker Compose 설치
- [ ] 환경변수 파일(.env) 생성
- [ ] 보안 키 설정 (JWT_SECRET, DB_PASSWORD 등)
- [ ] 데이터베이스 연결 확인

### **코드 작성 시**
- [ ] 모듈별 독립적인 설정 사용
- [ ] 명확한 빈 이름 규칙 준수
- [ ] Spring Security 사용자 정보 구분
- [ ] 일관된 API 응답 형식
- [ ] 적절한 예외 처리

### **배포 전**
- [ ] 보안 설정 검증
- [ ] 환경변수 확인
- [ ] 데이터베이스 백업
- [ ] 성능 테스트
- [ ] 보안 테스트

## 🆘 문제 해결

### **일반적인 오류**
1. **포트 충돌**: `lsof -ti:8080 | xargs kill -9`
2. **데이터베이스 연결 실패**: Docker 컨테이너 상태 확인
3. **Redis 연결 실패**: Redis 비밀번호 설정 확인
4. **빈 충돌**: 모듈별 설정 클래스 사용

### **로그 확인**
```bash
# 애플리케이션 로그
tail -f logs/application.log

# Docker 로그
docker-compose logs -f routepick-api
```

## 📞 지원

개발 관련 문의사항이 있으시면 개발팀에 연락하세요.

---

**⚠️ 중요**: 이 프로젝트는 보안과 관련된 민감한 정보를 포함하고 있습니다. 
프로덕션 환경에서는 반드시 강력한 비밀번호와 보안 키를 사용하세요.
