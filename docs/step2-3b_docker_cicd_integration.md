# Step 2-3b: Docker 개발환경 및 CI/CD 통합

> RoutePickr Docker & CI/CD 완전 구현  
> 생성일: 2025-08-16  
> 기반 분석: step2-1_backend_structure.md, step2-2_frontend_structure.md

---

## 🐳 Docker 개발 환경

### 🏗️ docker-compose.yml 구성

#### 전체 서비스 (12개)
1. **mysql**: MySQL 8.0 데이터베이스
2. **redis**: Redis 7.0 캐시
3. **backend**: Spring Boot API 서버
4. **admin-web**: React 관리자 웹
5. **metro**: React Native Metro 번들러
6. **nginx**: API Gateway + Static Server
7. **minio**: S3 호환 저장소 (개발용)
8. **elasticsearch**: 로그 검색
9. **kibana**: 로그 시각화
10. **mailhog**: 이메일 테스트
11. **prometheus**: 메트릭 수집
12. **grafana**: 대시보드

#### 네트워크 구성
```yaml
networks:
  routepick-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

#### 볼륨 관리
```yaml
volumes:
  mysql_data:         # MySQL 데이터 영구 저장
  redis_data:         # Redis 데이터 영구 저장
  minio_data:         # MinIO 파일 저장
  elasticsearch_data: # 로그 데이터
  prometheus_data:    # 메트릭 데이터
  grafana_data:       # 대시보드 설정
```

### 🔧 Nginx 설정

#### 도메인 라우팅
- **api.routepick.local**: Backend API
- **admin.routepick.local**: 관리자 웹
- **monitor.routepick.local**: 모니터링 도구들

#### Rate Limiting (한국 사용자 최적화)
```nginx
limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;
```

#### CORS 설정
```nginx
add_header 'Access-Control-Allow-Origin' '*' always;
add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
```

---

## 🚀 통합 스크립트

### 🔧 개발 환경 스크립트

#### scripts/development/start-all.sh
```bash
#!/bin/bash
# RoutePickr 개발 환경 전체 시작 스크립트

# 1. Docker 상태 확인
# 2. 기존 컨테이너 정리 (선택사항)
# 3. 인프라 서비스 먼저 시작 (MySQL, Redis)
# 4. Backend 애플리케이션 시작
# 5. Frontend 애플리케이션들 시작
# 6. 나머지 지원 서비스들 시작
# 7. 전체 상태 확인 및 URL 정보 출력
```

**주요 기능**:
- 색상 코딩된 로그 출력
- 서비스별 Health Check
- 순차적 시작으로 의존성 관리
- hosts 파일 설정 안내
- 실시간 로그 모니터링 안내

#### 제공 URL 정보
- 📱 React Native Metro: http://localhost:8081
- 💻 관리자 웹: http://localhost:3000
- 🔧 Backend API: http://localhost:8080/api/v1
- 📚 API 문서: http://localhost:8080/swagger-ui/index.html
- 📊 Grafana: http://localhost:3001
- 📈 Prometheus: http://localhost:9090
- 🔍 Kibana: http://localhost:5601

### 🚀 배포 스크립트

#### scripts/deployment/deploy-all.sh
```bash
#!/bin/bash
# RoutePickr 전체 배포 스크립트

# 사용법: ./deploy-all.sh [환경] [옵션]
# 환경: dev, staging, prod
# 옵션: --infra-only, --app-only, --plan, --force
```

**주요 기능**:
- 환경별 배포 (dev/staging/prod)
- Terraform 인프라 배포
- Docker 이미지 빌드 및 ECR 푸시
- ECS 서비스 업데이트
- 배포 후 Health Check
- Blue/Green 배포 (운영 환경)

---

## 🔄 CI/CD 파이프라인

### 📱 Backend CI/CD (.github/workflows/ci-backend.yml)

#### 파이프라인 단계
1. **Code Quality**: Checkstyle, SpotBugs, 의존성 검사
2. **Test**: 단위/통합 테스트 (MySQL + Redis)
3. **Security Scan**: OWASP 취약점, Trivy 스캔
4. **Build Image**: Docker 이미지 빌드 및 ECR 푸시
5. **Deploy Dev**: 개발 환경 자동 배포 (develop 브랜치)
6. **Deploy Prod**: 운영 환경 수동 배포 (main 브랜치)
7. **Performance Test**: K6 부하 테스트

#### 환경별 배포 전략
- **개발**: Push 시 자동 배포
- **운영**: 수동 승인 + Blue/Green 배포

### 💻 Frontend CI/CD (.github/workflows/ci-frontend.yml)

#### React Native App
- TypeScript 타입 검사
- ESLint 코드 스타일 검사
- Jest 단위 테스트
- Android APK 빌드 (개발)
- Google Play Store 배포 (운영)

#### React Admin Web
- TypeScript 타입 검사
- ESLint 코드 스타일 검사
- Jest 단위 테스트
- Cypress E2E 테스트
- Docker 이미지 빌드 및 배포
- Lighthouse 성능 감사

#### 보안 검사
- npm audit (의존성 취약점)
- Snyk 보안 스캔
- 컨테이너 이미지 스캔

---

## 📁 프로젝트 루트 구조

### 🏗️ 최종 디렉토리 구조

```
RoutePickr/
├── 📱 routepick-app/              # React Native 모바일 앱
│   ├── src/screens/               # 7개 도메인 화면
│   ├── src/components/            # 재사용 컴포넌트
│   ├── src/services/              # API + 인증 서비스
│   ├── src/store/                 # Redux Toolkit
│   ├── package.json               # React Native 의존성
│   └── tsconfig.json              # TypeScript 설정
├── 💻 routepick-admin/            # React 관리자 웹
│   ├── src/pages/                 # 7개 도메인 페이지
│   ├── src/components/            # UI 컴포넌트
│   ├── src/hooks/                 # 커스텀 훅
│   ├── src/store/                 # Zustand
│   ├── package.json               # React + Vite 의존성
│   └── vite.config.ts             # Vite 빌드 설정
├── 🖥️ routepick-backend/          # Spring Boot API 서버
│   ├── src/main/java/.../domain/  # 12개 도메인
│   ├── src/main/java/.../config/  # 설정 클래스
│   ├── src/main/resources/        # 환경별 설정
│   └── build.gradle               # Gradle 빌드 설정
├── 📦 routepick-common/           # Java 공통 라이브러리
│   ├── src/main/java/.../dto/     # 공통 DTO
│   ├── src/main/java/.../enums/   # 공통 Enum (8개 TagType 등)
│   ├── src/main/java/.../constants/ # 상수 정의
│   └── build.gradle               # Library 빌드 설정
├── ☁️ routepick-infrastructure/    # Terraform AWS 인프라
│   ├── terraform/                 # AWS 리소스 정의
│   │   ├── main.tf                # 메인 설정
│   │   ├── vpc.tf                 # VPC 네트워크
│   │   ├── security.tf            # 보안 그룹 + WAF
│   │   ├── rds.tf                 # MySQL RDS
│   │   └── outputs.tf             # 출력값
│   └── environments/              # 환경별 변수
├── 🗄️ database/                  # 데이터베이스
│   └── routepick.sql              # 스키마 정의 (50 테이블)
├── 🐳 docker/                    # Docker 설정
│   ├── nginx/                     # Nginx 설정
│   │   ├── nginx.conf             # 메인 설정
│   │   └── default.conf           # 가상 호스트
│   ├── mysql/                     # MySQL 설정
│   └── monitoring/                # 모니터링 설정
├── 🚀 scripts/                   # 운영 스크립트
│   ├── development/               # 개발 환경 스크립트
│   │   └── start-all.sh           # 전체 시작 스크립트
│   ├── deployment/                # 배포 스크립트
│   │   └── deploy-all.sh          # 전체 배포 스크립트
│   └── ci-cd/                     # CI/CD 스크립트
├── 📋 .github/workflows/          # GitHub Actions
│   ├── ci-backend.yml             # Backend CI/CD
│   └── ci-frontend.yml            # Frontend CI/CD
├── 📄 docker-compose.yml          # 개발 환경 통합 구성
├── 🔧 .env.example                # 환경 변수 템플릿
├── 📝 .gitignore                  # Git 제외 파일
└── 📖 README.md                   # 프로젝트 문서
```

---

## ✅ Docker & CI/CD 완료 체크리스트

### 🐳 개발 환경 (Docker Compose)
- [x] **12개 서비스**: MySQL, Redis, Backend, Frontend, 모니터링
- [x] **네트워크 설정**: 전용 브리지 네트워크, 서브넷 분리
- [x] **볼륨 관리**: 영구 데이터 저장, 개발 환경 최적화
- [x] **Nginx 설정**: API Gateway, 도메인 라우팅, Rate Limiting
- [x] **Health Check**: 서비스별 헬스 체크 설정

### 🚀 통합 스크립트
- [x] **start-all.sh**: 개발 환경 전체 시작, 순차적 실행, 상태 확인
- [x] **deploy-all.sh**: 환경별 배포, Terraform + Docker, Blue/Green
- [x] **색상 로그**: 직관적인 상태 출력
- [x] **에러 처리**: 실패 시 롤백, 타임아웃 설정

### 🔄 CI/CD 파이프라인
- [x] **Backend Pipeline**: 코드 품질, 테스트, 보안 스캔, 배포
- [x] **Frontend Pipeline**: TypeScript, ESLint, Jest, Cypress, 배포
- [x] **환경별 배포**: dev(자동), prod(수동 승인)
- [x] **보안 검사**: OWASP, Trivy, npm audit, Snyk
- [x] **성능 테스트**: K6 부하 테스트, Lighthouse 감사

### 📁 프로젝트 구조
- [x] **루트 파일**: .env.example, .gitignore, README.md, docker-compose.yml
- [x] **디렉토리 구조**: 8개 주요 디렉토리, 계층적 구성
- [x] **문서화**: 포괄적인 README, 사용법 안내
- [x] **한국어 지원**: 한국어 주석, 설명, 에러 메시지

---

## 📊 Docker & CI/CD 현황 요약

### 📈 생성 완료 통계
- **Docker 서비스**: 12개 (개발 환경 완전 구성)
- **CI/CD 파이프라인**: 2개 (Backend + Frontend)
- **배포 스크립트**: 2개 (개발환경 + 운영배포)
- **총 파일 수**: 30개+ (설정, 스크립트, 문서 포함)

### 🎯 핵심 성과

#### 🐳 완전한 개발 환경
1. **Docker Compose**: 12개 서비스 통합
2. **네트워크 분리**: 172.20.0.0/16 전용 네트워크
3. **데이터 영속성**: 6개 볼륨으로 데이터 보존
4. **Health Check**: 서비스별 상태 모니터링
5. **로컬 도메인**: 3개 도메인으로 서비스 분리

#### 🔄 완전 자동화 DevOps
1. **Infrastructure as Code**: Terraform으로 AWS 관리
2. **Container Orchestration**: Docker Compose 개발 환경
3. **CI/CD Pipeline**: 자동 테스트, 빌드, 배포
4. **Blue/Green Deployment**: 운영 환경 무중단 배포
5. **Monitoring & Alerting**: 실시간 모니터링, 장애 알림

#### 📱 모바일 앱 최적화
1. **React Native**: Metro 번들러 통합
2. **Hot Reload**: 실시간 개발 환경
3. **APK 빌드**: 자동화된 Android 빌드
4. **Play Store**: 운영 배포 자동화
5. **TypeScript**: 타입 안전성 보장

---

## 🛠️ 다음 개발 단계

### Step 3: 핵심 기능 구현 (예상 8-10시간)
1. **Backend Entity 생성**: 50개 테이블 → JPA Entity 매핑
2. **Repository 계층**: JPA + QueryDSL 복합 쿼리
3. **Service 계층**: 비즈니스 로직 + 추천 알고리즘
4. **Controller 계층**: REST API + Swagger 문서화
5. **Frontend 컴포넌트**: 핵심 화면 및 기능 구현

### 우선 구현 기능
1. **사용자 인증**: 소셜 로그인 4개 Provider
2. **태그 관리**: 8가지 태그 타입 시스템
3. **추천 엔진**: 태그 70% + 레벨 30% 알고리즘
4. **지도 검색**: GPS 기반 주변 암장 찾기
5. **루트 관리**: 이미지/영상 업로드, 태깅

---

**다음 단계**: Backend Entity 및 Repository 구현  
**예상 소요 시간**: 4-5시간  
**핵심 목표**: 50개 테이블 JPA 매핑 + 추천 시스템 핵심 로직

*완료일: 2025-08-16*  
*핵심 성과: RoutePickr Docker & CI/CD 100% 완성*