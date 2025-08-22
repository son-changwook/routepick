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
- **QueryDSL** + **JPA Auditing** (51개 Repository 완성)
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

# 애플리케이션 실행
./gradlew bootRun

# 테스트 실행
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
- [📋 프로젝트 진행 상황](CLAUDE.md) - **6단계 Service 레이어 완료 (8개 Service)**
- [🚨 GitHub Actions 트러블슈팅 가이드](docs/GITHUB_ACTIONS_TROUBLESHOOTING.md)

### 1단계: 분석 문서
- [📊 데이터베이스 스키마 분석](step1-1_schema_analysis.md)
- [🏷️ 태그 시스템 심층 분석](step1-2_tag_system_analysis.md)
- [🏗️ 아키텍처 및 소셜 로그인](step1-3a_architecture_social_recommendation.md)
- [🇰🇷 한국 특화 비즈니스 규칙](step1-3b_korean_business_jpa.md)
- [⚡ 성능 최적화 및 보안 강화](step1-3c_performance_security.md)

### 2단계: 구조 설계 문서
- [🏛️ Backend 프로젝트 구조](step2-1_backend_structure.md)
- [📱 Frontend 구조 설계](step2-2_frontend_structure.md)
- [☁️ 인프라 설정](step2-3_infrastructure_setup.md)

### 3단계: 예외 처리 체계 (177개 ErrorCode)
- [🔧 BaseException 설계 및 보안 원칙](step3-1a_base_exception_design.md)
- [📋 ErrorCode Enum 체계 (177개)](step3-1b_error_codes.md)
- [📊 예외 통계 및 모니터링](step3-1c_statistics_monitoring.md)
- [🔐 인증/사용자 예외](step3-2a_auth_user_exceptions.md)
- [🏢 체육관/루트 예외](step3-2b_gym_route_exceptions.md)
- [🏷️ 태그/결제 예외](step3-2c_tag_payment_exceptions.md)
- [🛡️ 검증/시스템 예외](step3-2d_validation_system_exceptions.md)
- [🌐 전역 예외 처리 핵심](step3-3a_global_handler_core.md)
- [🔒 보안 강화 기능](step3-3b_security_features.md)
- [📈 모니터링 및 테스트](step3-3c_monitoring_testing.md)

### 4단계: JPA 엔티티 설계 (50개)
- [🏗️ Base 공통 엔티티 및 Enum](step4-1a_base_common_entities.md)
- [👤 User 핵심 엔티티](step4-1b_user_core_entities.md)  
- [🔐 User 확장 엔티티 및 보안 강화](step4-1c_user_extended_entities.md)
- [🏷️ 통합 태그 시스템 엔티티](step4-2a_tag_system_entities.md)
- [🏢 암장 관리 엔티티](step4-2b1_gym_management_entities.md)
- [🧗 루트 관리 엔티티](step4-2b2_route_management_entities.md)
- [🧗‍♀️ 클라이밍 최적화 엔티티](step4-2c_climbing_optimization_entities.md)
- [🏢 Gym 관련 엔티티](step4-3a_gym_management_entities.md)
- [🧗 Route 핵심 엔티티](step4-3b1_route_core_entities.md)
- [⭐ Route 상호작용 엔티티](step4-3b2_route_interaction_entities.md)
- [🎯 Climbing 시스템 엔티티](step4-3c1_climbing_system_entities.md)
- [📈 User 활동 엔티티](step4-3c2_user_activity_entities.md)
- [💬 Community 핵심 엔티티](step4-4a1_community_core_entities.md)
- [👥 Community 상호작용 엔티티](step4-4a2_community_interaction_entities.md)
- [💳 Payment 결제 시스템 엔티티](step4-4b1_payment_entities.md)
- [🔔 Notification 알림 시스템 엔티티](step4-4b2_notification_entities.md)
- [🔧 System 관리 엔티티](step4-4c1_system_management_entities.md)
- [📊 System 로깅 엔티티](step4-4c2_system_logging_entities.md)

### 5단계: Repository 레이어 설계 ✨
- [📋 Common Repository & QueryDSL](step5-1a_common_repositories.md)
- [👤 User Core Repository](step5-1b1_user_core_repositories.md)
- [🔐 User Verification Repository](step5-1b2_user_verification_repositories.md)  
- [👥 UserFollow & Missing Repository](step5-1c_missing_repositories.md)
- [🏷️ Tag System Repository](step5-2_tag_repositories_focused.md)
- [🏢 Gym Core Repository](step5-3a_gym_core_repositories.md)
- [🏗️ Gym Additional Repository](step5-3b_gym_additional_repositories.md)
- [🧗 Route Core Repository](step5-3c1_route_core_repositories.md)
- [🔧 Route QueryDSL Repository](step5-3c2_route_querydsl_repositories.md)
- [📸 Route Image Repository](step5-3d1_route_image_repositories.md)
- [🎬 Route Video Repository](step5-3d2_route_video_repositories.md)
- [⭐ Route Comment Repository](step5-3e1_route_comment_repositories.md)
- [🗳️ Route Vote & Scrap Repository](step5-3e2_route_vote_scrap_repositories.md)
- [🧗‍♂️ Climbing Level & Shoe Repository](step5-3f1_climbing_level_shoe_repositories.md)
- [⚡ User Activity Repository](step5-3f2_user_activity_repositories.md)
- [💬 Community Core Repository](step5-4a1_community_core_repositories.md)
- [📱 Community Category Repository](step5-4a2_community_category_repositories.md)
- [👍 Community Interaction Repository](step5-4b_community_interaction_repositories.md)
- [📸 Community Post Media Repository](step5-4c1_community_post_media_repositories.md)
- [💬 Community Comment Repository](step5-4c2_community_comment_repositories.md)
- [💳 Payment Repository](step5-4d_payment_repositories.md)
- [🔔 Notification Repository](step5-4e_notification_repositories.md)
- [📧 Message System Repository](step5-4f1_message_system_repositories.md)
- [🔧 System Management Repository](step5-4f2_system_management_repositories.md)

### 6단계: Service 레이어 구현 ✨ (Auth & User & Gym & Route 관리)
#### 6-1: 인증 및 사용자 관리 Service
- [🔐 JWT 인증 및 소셜 로그인 Service](step6-1a_auth_service.md)
- [📧 비동기 이메일 발송 및 Redis 인증 코드 Service](step6-1b_email_service.md)
- [👤 사용자 관리, 프로필, 팔로우 Service](step6-1c_user_service.md)
- [✅ 본인인증, 약관동의 및 보안 유틸리티](step6-1d_verification_security.md)

#### 6-2: 체육관 및 루트 관리 Service
- [🏢 체육관 관리 Service (한국좌표 검증, 공간쿼리)](step6-2a_gym_service.md)
- [🧗 루트 관리 Service (V등급/YDS 변환, 난이도 투표)](step6-2b_route_service.md)
- [🎬 루트 미디어 Service (이미지/동영상, 썸네일, 댓글시스템)](step6-2c_route_media_service.md)
- [📊 클라이밍 기록 Service (통계분석, 신발관리, 성장추적)](step6-2d_climbing_record_service.md)

---

<div align="center">

**🧗‍♀️ RoutePickr로 더 나은 클라이밍 경험을 시작하세요! 🧗‍♂️**

**✅ 6단계 Service 레이어 완료** - Auth & User & Gym & Route 관리 8개 Service 완성 (89% 달성)

Made with ❤️ by RoutePickr Team

</div>