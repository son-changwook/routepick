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
