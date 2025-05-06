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

## 개발 환경 설정

### 필수 요구사항
- JDK 17
- Node.js
- MySQL
- Docker
- Gradle

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
