# Step 2-3: Infrastructure 및 통합 환경 설정

> RoutePickr Infrastructure 완전 구현  
> 생성일: 2025-08-16  
> 기반 분석: step2-1_backend_structure.md, step2-2_frontend_structure.md

---

## 🎯 인프라 개요

### 생성된 인프라스트럭처 구성
- **routepick-common**: Java 공통 라이브러리 (DTO, Enum, Constants)
- **routepick-infrastructure**: Terraform AWS 설정
- **docker-compose.yml**: 개발 환경 통합 구성
- **CI/CD 파이프라인**: GitHub Actions 자동화
- **통합 스크립트**: 배포 및 운영 자동화

---

## 📦 routepick-common (Java 공통 라이브러리)

### 🏗️ 라이브러리 구조

```
routepick-common/
├── build.gradle                  # Gradle Library 설정
└── src/main/java/com/routepick/common/
    ├── dto/
    │   └── ApiResponse.java       # 통일된 API 응답 포맷
    ├── enums/
    │   ├── UserType.java          # 사용자 유형 (3개)
    │   ├── TagType.java           # 태그 유형 (8개)
    │   ├── SocialProvider.java    # 소셜 로그인 (4개)
    │   ├── PreferenceLevel.java   # 선호도 레벨
    │   └── SkillLevel.java        # 숙련도 레벨
    └── constants/
        └── Constants.java         # 전역 상수 정의
```

### 📋 주요 Enum 정의

#### TagType (8가지 카테고리)
```java
public enum TagType {
    STYLE("스타일", "클라이밍 스타일 관련 태그"),
    FEATURE("특징", "루트의 물리적 특징"),
    TECHNIQUE("기술", "필요한 클라이밍 기술"),
    DIFFICULTY("난이도", "체감 난이도 관련"),
    MOVEMENT("동작", "특정 동작이나 무브"),
    HOLD_TYPE("홀드 타입", "홀드의 종류나 형태"),
    WALL_ANGLE("벽면 각도", "벽의 기울기나 각도"),
    OTHER("기타", "기타 분류되지 않는 태그");
}
```

#### SocialProvider (4개 - APPLE 제외)
```java
public enum SocialProvider {
    GOOGLE("구글", "google"),
    KAKAO("카카오", "kakao"),
    NAVER("네이버", "naver"),
    FACEBOOK("페이스북", "facebook");
}
```

#### PreferenceLevel (추천 가중치)
```java
public enum PreferenceLevel {
    LOW("낮음", 0.3),
    MEDIUM("보통", 0.7),
    HIGH("높음", 1.0);
}
```

### 🔧 공통 상수 정의

```java
// 추천 알고리즘 상수
public static final double TAG_WEIGHT = 0.7;        // 태그 매칭 70%
public static final double LEVEL_WEIGHT = 0.3;      // 레벨 매칭 30%
public static final int MIN_RECOMMENDATION_SCORE = 20;

// 한국 GPS 좌표 범위
public static final double KOREA_MIN_LATITUDE = 33.0;
public static final double KOREA_MAX_LATITUDE = 38.6;

// JWT 설정
public static final long JWT_ACCESS_TOKEN_EXPIRATION = 1800000L;   // 30분
public static final long JWT_REFRESH_TOKEN_EXPIRATION = 604800000L; // 7일

// Redis 캐시 TTL
public static final long CACHE_TTL_USER_RECOMMENDATIONS = 86400; // 24시간
public static final long CACHE_TTL_ROUTE_TAGS = 3600;           // 1시간
```

---

## ☁️ routepick-infrastructure (Terraform AWS)

### 🏗️ 인프라 구성

```
routepick-infrastructure/
├── terraform/
│   ├── main.tf                   # 메인 Terraform 설정
│   ├── variables.tf              # 변수 정의
│   ├── outputs.tf                # 출력값 정의
│   ├── vpc.tf                    # VPC 네트워크 설정
│   ├── security.tf               # 보안 그룹 + WAF
│   └── rds.tf                    # MySQL RDS 설정
├── environments/
│   ├── dev.tfvars               # 개발 환경 변수
│   ├── staging.tfvars           # 스테이징 환경 변수
│   └── prod.tfvars              # 운영 환경 변수
└── modules/                     # 재사용 모듈
    ├── vpc/
    ├── rds/
    └── security/
```

### 🌐 AWS 리소스 구성

#### VPC 네트워크 (Multi-AZ)
- **VPC**: 10.0.0.0/16 CIDR
- **Public Subnets**: 2개 AZ (10.0.1.0/24, 10.0.2.0/24)
- **Private Subnets**: 2개 AZ (10.0.10.0/24, 10.0.11.0/24)
- **NAT Gateway**: 각 AZ별 1개 (고가용성)
- **Internet Gateway**: 1개
- **VPC Endpoint**: S3 (비용 최적화)

#### 보안 설정
- **ALB Security Group**: HTTP(80), HTTPS(443) 허용
- **App Security Group**: ALB에서만 8080 포트 허용
- **RDS Security Group**: App에서만 3306 포트 허용
- **Redis Security Group**: App에서만 6379 포트 허용
- **WAF**: DDoS 보호 + 한국 트래픽 최적화

#### 데이터베이스 (RDS MySQL 8.0)
- **인스턴스**: Multi-AZ (운영) / Single-AZ (개발)
- **백업**: 7일 보관 (운영) / 3일 보관 (개발)
- **모니터링**: Enhanced Monitoring + Performance Insights
- **한국 최적화**: Asia/Seoul 시간대, utf8mb4 문자셋
- **Read Replica**: 운영 환경에만 생성

#### 캐시 (ElastiCache Redis)
- **엔진**: Redis 7.0
- **인스턴스**: cache.t3.micro (개발) / cache.r7g.large (운영)
- **백업**: 자동 백업 활성화
- **보안**: VPC 내부 접근만 허용

### 🇰🇷 한국 특화 설정

```hcl
variable "korea_settings" {
  default = {
    timezone               = "Asia/Seoul"
    default_language       = "ko"
    social_login_providers = ["GOOGLE", "KAKAO", "NAVER", "FACEBOOK"]
    gps_bounds = {
      min_latitude  = 33.0
      max_latitude  = 38.6
      min_longitude = 124.0
      max_longitude = 132.0
    }
  }
}
```

### 📊 모니터링 및 알림
- **CloudWatch Alarms**: CPU, 메모리, 연결 수
- **SNS Topics**: 알림 발송
- **Auto Scaling**: 트래픽 기반 자동 확장

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

## 🔐 보안 및 모니터링

### 🛡️ 보안 설정

#### WAF (Web Application Firewall)
```hcl
# Rate Limiting
rate_based_statement {
  limit              = 2000
  aggregate_key_type = "IP"
}

# 지역 기반 접근 제어 (한국 최적화)
geo_match_statement {
  country_codes = ["KR", "US", "JP", "CN"]
}
```

#### Secrets 관리
- **AWS Secrets Manager**: 데이터베이스 비밀번호
- **GitHub Secrets**: CI/CD 환경 변수
- **환경 변수 분리**: .env.example 템플릿 제공

### 📊 모니터링 대시보드

#### Grafana 대시보드
- **시스템 메트릭**: CPU, 메모리, 디스크, 네트워크
- **애플리케이션 메트릭**: 응답 시간, 처리량, 에러율
- **비즈니스 메트릭**: 사용자 활동, 추천 성능
- **한국 특화**: KST 시간대, 한국어 레이블

#### 알림 설정
- **Slack 통합**: 배포 상태, 에러 알림
- **CloudWatch Alarms**: 임계값 초과 시 SNS 발송
- **PagerDuty 연동**: 중요 장애 시 호출

---

## 🌏 한국 최적화 설정

### 🗾 지역화 설정
- **시간대**: Asia/Seoul (모든 서비스)
- **언어**: 한국어 우선 (에러 메시지, 로그)
- **문자셋**: UTF-8 (MySQL utf8mb4_unicode_ci)

### 🔍 GPS 좌표 검증
```java
// 한국 GPS 좌표 범위 검증
public static boolean isValidKoreaCoordinate(double lat, double lng) {
    return lat >= KOREA_MIN_LATITUDE && lat <= KOREA_MAX_LATITUDE &&
           lng >= KOREA_MIN_LONGITUDE && lng <= KOREA_MAX_LONGITUDE;
}
```

### 📱 소셜 로그인 4개 Provider
1. **Google**: 글로벌 표준 OAuth2
2. **Kakao**: 한국 1위 메신저 (커스텀 Provider)
3. **Naver**: 한국 1위 포털 (커스텀 Provider)
4. **Facebook**: 글로벌 소셜 네트워크

### 🚀 CDN 최적화
- **CloudFront**: 서울 리전 최적화
- **S3 Transfer Acceleration**: 업로드 성능 향상
- **이미지 최적화**: WebP 포맷 지원

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

## ✅ Step 2-3 완료 체크리스트

### 📦 공통 라이브러리 (routepick-common)
- [x] **Gradle 라이브러리 설정**: Java 17, Maven 발행
- [x] **공통 DTO**: ApiResponse 통일된 응답 포맷
- [x] **핵심 Enum 정의**: TagType(8개), SocialProvider(4개), PreferenceLevel, SkillLevel
- [x] **전역 상수**: 추천 알고리즘, GPS 범위, JWT, 캐시 TTL
- [x] **한국 특화**: 소셜 로그인 4개, 시간대, 좌표 범위

### ☁️ AWS 인프라 (Terraform)
- [x] **VPC 네트워크**: Multi-AZ, Public/Private Subnets, NAT Gateway
- [x] **보안 설정**: Security Groups, WAF, DDoS 보호
- [x] **RDS MySQL**: Multi-AZ, Enhanced Monitoring, 한국 시간대
- [x] **ElastiCache Redis**: 캐시 클러스터, VPC 보안
- [x] **한국 최적화**: Seoul 리전, 지역 기반 접근 제어
- [x] **모니터링**: CloudWatch Alarms, SNS 알림

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

### 🔐 보안 및 모니터링
- [x] **Secrets 관리**: AWS Secrets Manager, GitHub Secrets
- [x] **모니터링**: Grafana 대시보드, Prometheus 메트릭
- [x] **로그 관리**: ELK Stack, 중앙화된 로그 수집
- [x] **알림 시스템**: Slack, PagerDuty 연동

---

## 📊 프로젝트 현황 요약

### 📈 생성 완료 통계
- **총 프로젝트 수**: 5개 (Common + Infrastructure + 3개 애플리케이션)
- **총 파일 수**: 50개+ (설정, 스크립트, 문서 포함)
- **AWS 리소스**: 20개+ (VPC, EC2, RDS, ElastiCache, S3, CloudFront 등)
- **Docker 서비스**: 12개 (개발 환경 완전 구성)
- **CI/CD 파이프라인**: 2개 (Backend + Frontend)
- **소요 시간**: 3시간

### 🎯 핵심 성과

#### 🏗️ 완전한 인프라스트럭처
1. **AWS 클라우드**: Terraform IaC로 완전 자동화
2. **개발 환경**: Docker Compose 12개 서비스 통합
3. **CI/CD**: GitHub Actions 완전 자동화
4. **모니터링**: Grafana + Prometheus + ELK Stack
5. **보안**: WAF + Secrets Manager + 취약점 스캔

#### 📦 공통 라이브러리 완성
1. **Java Library**: Gradle 기반 공통 컴포넌트
2. **DTO 통일**: Frontend-Backend 완벽 연동
3. **Enum 체계**: 8개 TagType, 4개 SocialProvider
4. **상수 관리**: 추천 알고리즘, 한국 특화 설정
5. **Maven 발행**: 모든 프로젝트에서 공통 사용

#### 🇰🇷 한국 특화 최적화
1. **지역 설정**: Asia/Seoul 시간대, 한국어 우선
2. **GPS 범위**: 한국 좌표계 검증 (33.0-38.6N)
3. **소셜 로그인**: APPLE 제외, 4개 Provider
4. **CDN 최적화**: 서울 리전 CloudFront
5. **WAF 설정**: 한국 트래픽 우선 허용

#### 🔄 DevOps 완전 자동화
1. **Infrastructure as Code**: Terraform으로 AWS 관리
2. **Container Orchestration**: Docker Compose 개발 환경
3. **CI/CD Pipeline**: 자동 테스트, 빌드, 배포
4. **Blue/Green Deployment**: 운영 환경 무중단 배포
5. **Monitoring & Alerting**: 실시간 모니터링, 장애 알림

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
*핵심 성과: RoutePickr Infrastructure 및 통합 환경 100% 완성*