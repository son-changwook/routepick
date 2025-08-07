# RoutePick

크로스 플랫폼 웹/앱 서비스 프로젝트

## 프로젝트 구조

```
routepick/
├── backend/           # Spring Boot 백엔드
├── frontend/         # React 프론트엔드
├── shared/           # 공유 모듈
└── build/            # 빌드 결과물
```

## 기술 스택

### 백엔드

- Spring Boot (Gradle)
- JDK 17
- Java + MyBatis
- MySQL

### 프론트엔드

- JavaScript + TypeScript
- Node.js
- API Gateway
- WebSocket

### 인프라

- AWS
- Docker
- GitHub

### 개발 환경 세팅

- Docker 설치
- VS Code Docker 확장 설치 (추천)

## 개발 환경 설정

### 필수 요구사항

- JDK 17
- Node.js
- MySQL
- Docker
- Gradle

### 보안 설정

#### 비활성화된 보안 컴포넌트

- `MyBatisSecurityConfig.java`: 순환 참조 문제로 인해 비활성화됨
  - 위치: `backend/routepick-api/src/main/java/com/routepick/api/config/MyBatisSecurityConfig.java`
  - 활성화 방법: 주석 처리된 `@Configuration` 해제 후 순환 참조 문제 해결
  - 현재는 기본 MyBatis 보안 + ValidationService로 충분한 보안 제공

## 개발 가이드라인

### ⚠️ userName과 nickName 관련 주의사항

#### 1. Spring Security 컨벤션
- `getUsername()`: 반드시 String 타입, 사용자 식별자 (userId)
- Spring Security의 UserDetails 인터페이스 규칙
- JWT 토큰의 sub 필드와 일치해야 함

#### 2. 실제 사용자 정보
- `getUserName()`: 사용자 실명 (홍길동)
- `getNickName()`: 사용자 닉네임 (climber123) - UI 표시 우선 사용

#### 3. 혼동 금지
- `getUsername()` ≠ `getUserName()` ≠ `getNickName()`
- `getUsername()` = userId (String)
- `getUserName()` = 사용자 실명
- `getNickName()` = 사용자 닉네임 (UI 표시 우선)

#### 4. JWT 토큰 구조
```json
{
  "sub": "123",           // userId (String)
  "userName": "홍길동",      // 사용자 실명
  "nickName": "climber123", // 사용자 닉네임
  "email": "user@example.com"
}
```

#### 5. 사용 예시
```java
@AuthenticationPrincipal CustomUserDetails userDetails
Long userId = userDetails.getUserId();           // 사용자 식별자
String userName = userDetails.getUserName();     // 사용자 실명
String nickName = userDetails.getNickName();     // 사용자 닉네임 (UI 표시 우선)
```

#### 6. DTO 응답 구조
```json
{
  "success": true,
  "code": 200,
  "message": "성공",
  "data": {
    "userName": "climber123",  // 실제 사용자 이름
    "email": "user@example.com"
  }
}
```

### API 구현 가이드라인

#### 1. API 명명 규칙
```
GET    /api/{resource}           # 리소스 목록 조회
GET    /api/{resource}/{id}      # 특정 리소스 조회
POST   /api/{resource}           # 리소스 생성
PUT    /api/{resource}/{id}      # 리소스 전체 수정
PATCH  /api/{resource}/{id}      # 리소스 부분 수정
DELETE /api/{resource}/{id}      # 리소스 삭제
```

#### 2. 응답 구조 표준화
```json
{
  "success": true,
  "code": 200,
  "message": "성공 메시지",
  "data": {
    // 실제 데이터
  },
  "timestamp": "2024-01-01T12:00:00",
  "error": {
    "code": 400,
    "message": "에러 메시지",
    "details": "상세 에러 정보",
    "field": "에러 필드명"
  }
}
```

#### 3. 향후 확장 시 주의사항
1. **새로운 빈 추가 시**: `routepickXXXBean` 형식 사용
2. **자동 설정 추가 시**: `BeanConflictPreventionConfig`에 추가
3. **의존성 주입 시**: `@Qualifier` 임시 방편 대신 근본적 해결

### 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

### 프론트엔드 실행

```bash
cd frontend
npm install
npm start
```

## 라이선스

MIT License
