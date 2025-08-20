# 🧗‍♀️ RoutePickr

> 한국 클라이밍 커뮤니티를 위한 **태그 기반 루트 추천 플랫폼**

[![Backend CI/CD](https://github.com/routepickr/routepickr/workflows/Backend%20CI/CD/badge.svg)](https://github.com/routepickr/routepickr/actions)
[![Frontend CI/CD](https://github.com/routepickr/routepickr/workflows/Frontend%20CI/CD/badge.svg)](https://github.com/routepickr/routepickr/actions)
[![codecov](https://codecov.io/gh/routepickr/routepickr/branch/main/graph/badge.svg)](https://codecov.io/gh/routepickr/routepickr)

## 📋 프로젝트 개요

RoutePickr는 한국의 클라이밍 체육관과 사용자를 연결하는 플랫폼으로, **AI 기반 태그 추천 시스템**을 통해 개인화된 클라이밍 루트를 제공합니다.

### 🎯 핵심 기능

- **🏷️ 태그 기반 추천**: 8가지 태그 체계로 정밀한 루트 매칭
- **📱 React Native 앱**: iOS/Android 크로스 플랫폼 지원
- **💻 관리자 웹**: React 기반 체육관 관리 시스템
- **🗺️ 지도 검색**: GPS 기반 주변 체육관 찾기
- **🔐 소셜 로그인**: 4개 Provider (Google, Kakao, Naver, Facebook)
- **⚡ 실시간 알림**: Firebase FCM 푸시 알림

## 🏗️ 아키텍처

```
RoutePickr/
├── 📱 routepick-app/          # React Native 모바일 앱
├── 💻 routepick-admin/        # React 관리자 웹
├── 🖥️ routepick-backend/      # Spring Boot API 서버
├── 📦 routepick-common/       # 공통 라이브러리 (Java)
├── ☁️ routepick-infrastructure/ # Terraform AWS 인프라
├── 🗄️ database/              # MySQL 스키마 (50 테이블, 50 엔티티)
├── 🐳 docker/                # Docker 개발 환경
└── 🚀 scripts/               # 배포 및 운영 스크립트
```

## 🛠️ 기술 스택

### Backend
- **Spring Boot 3.2** (Java 17)
- **MySQL 8.0** + **Redis 7.0**
- **QueryDSL** + **JPA Auditing** (50개 Repository 완성)
- **JWT** + **OAuth2** + **Spring Security**
- **AWS S3** + **Firebase FCM**

### Frontend
- **React Native 0.73.4** (TypeScript)
- **React 18** + **Vite** (관리자 웹)
- **Redux Toolkit** (앱) + **Zustand** (웹)
- **React Navigation 6** + **Ant Design 5**

### Infrastructure
- **AWS**: VPC, EC2, RDS, ElastiCache, S3, CloudFront
- **Terraform**: Infrastructure as Code
- **Docker**: 개발 환경 컨테이너화
- **GitHub Actions**: CI/CD 파이프라인

## 🚀 빠른 시작

### 1. 저장소 클론
```bash
git clone https://github.com/routepickr/routepickr.git
cd routepickr
```

### 2. 환경 설정
```bash
# 환경 변수 파일 생성
cp .env.example .env

# 환경 변수 수정 (DB, Redis, JWT Secret 등)
vi .env
```

### 3. 개발 환경 시작
```bash
# 전체 개발 환경 실행 (Docker Compose)
./scripts/development/start-all.sh

# 개별 서비스 시작
docker-compose up -d mysql redis  # 인프라만
docker-compose up -d backend      # 백엔드만
docker-compose up -d admin-web    # 관리자 웹만
```

### 4. 서비스 접속
- 📱 **React Native Metro**: http://localhost:8081
- 💻 **관리자 웹**: http://localhost:3000
- 🔧 **Backend API**: http://localhost:8080/api/v1
- 📚 **API 문서**: http://localhost:8080/swagger-ui/index.html
- 📊 **Grafana**: http://localhost:3001 (admin/routepick2024!)

## 📊 데이터베이스 구조

### 🏷️ 핵심 태그 시스템 (4개 테이블)
```sql
tags                    # 마스터 태그 (8가지 TagType)
user_preferred_tags     # 사용자 선호 태그
route_tags              # 루트 태그 (relevance_score)
user_route_recommendations # 추천 결과 캐시
```

### 📈 추천 알고리즘
- **태그 매칭**: 70% 가중치
- **레벨 매칭**: 30% 가중치
- **최소 점수**: 20점 이상
- **캐싱**: Redis 24시간 TTL

## 🔧 개발 가이드

### Backend 개발
```bash
cd routepick-backend

# 공통 라이브러리 빌드
cd ../routepick-common && ./gradlew publishToMavenLocal

# 애플리케이션 실행 (50개 Repository 로드)
./gradlew bootRun

# 테스트 실행 (Repository 통합 테스트 포함)
./gradlew test integrationTest
```

### Frontend 개발
```bash
# React Native 앱
cd routepick-app
npm install
npm start

# React 관리자 웹
cd routepick-admin
npm install
npm run dev
```

### 인프라 배포
```bash
# 개발 환경 배포
./scripts/deployment/deploy-all.sh dev

# 운영 환경 배포
./scripts/deployment/deploy-all.sh prod
```

## 📖 문서

### 개발 문서
- [📋 프로젝트 진행 상황](CLAUDE.md) - **5단계 완료 (Repository 레이어)**
- [🚨 GitHub Actions 트러블슈팅 가이드](docs/GITHUB_ACTIONS_TROUBLESHOOTING.md)

### 1단계: 분석 문서
- [📊 데이터베이스 스키마 분석](step1-1_schema_analysis.md)
- [🏷️ 태그 시스템 심층 분석](step1-2_tag_system_analysis.md)
- [🏗️ Spring Boot 설계 가이드](step1-3_spring_boot_guide.md)

### 2단계: 구조 설계 문서
- [🏛️ Backend 프로젝트 구조](step2-1_backend_structure.md)
- [📱 Frontend 구조 설계](step2-2_frontend_structure.md)
- [☁️ 인프라 설정](step2-3_infrastructure_setup.md)

### 3단계: 예외 처리 체계
- [⚡ 예외 처리 체계 기본 설계](step3-1_exception_base.md)
- [🚨 도메인별 커스텀 예외 클래스](step3-2_domain_exceptions.md)
- [🔒 GlobalExceptionHandler 및 보안 강화](step3-3_global_handler_security.md)

### 4단계: JPA 엔티티 설계 (50개)
- [👤 Base & User 엔티티](step4-1_base_user_entities.md)
- [🏷️ Tag 비즈니스 엔티티](step4-2_tag_business_entities.md)
- [🏢 Gym 관련 엔티티](step4-3a_gym_entities.md)
- [🧗 Route 관련 엔티티](step4-3b_route_entities.md)
- [⚡ Climbing & Activity 엔티티](step4-3c_climbing_activity_entities.md)
- [💬 Community 엔티티](step4-4a_community_entities.md)
- [💳 Payment & Notification 엔티티](step4-4b_payment_notification.md)
- [🔧 System Final 엔티티](step4-4c_system_final.md)

### 5단계: Repository 레이어 설계 (50개) ✨
- [👤 Base & User Repository](step5-1_base_user_repositories.md)
- [🏷️ Tag System Repository](step5-2_tag_repositories_focused.md)
- [🏢 Gym Core Repository](step5-3a_gym_core_repositories.md)
- [🏗️ Gym Additional Repository](step5-3b_gym_additional_repositories.md)
- [🧗 Route Core Repository](step5-3c_route_core_repositories.md)
- [📸 Route Media Repository](step5-3d_route_media_repositories.md)
- [⭐ Route Interaction Repository](step5-3e_route_interaction_repositories.md)
- [⚡ Climbing & Activity Repository](step5-3f_climbing_activity_repositories.md)
- [💬 Community Core Repository](step5-4a_community_core_repositories.md)
- [👍 Community Interaction Repository](step5-4b_community_interaction_repositories.md)
- [📱 Community Media Repository](step5-4c_community_media_repositories.md)
- [💳 Payment Repository](step5-4d_payment_repositories.md)
- [🔔 Notification Repository](step5-4e_notification_repositories.md)
- [🔧 System Final Repository](step5-4f_system_final_repositories.md)

---

<div align="center">

**🧗‍♀️ RoutePickr로 더 나은 클라이밍 경험을 시작하세요! 🧗‍♂️**

**✅ 5단계 완료** - Repository 레이어 50개 완성 (77% 달성)

Made with ❤️ by RoutePickr Team

</div>