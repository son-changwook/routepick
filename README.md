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
├── 🗄️ database/              # MySQL 스키마 (50 테이블)
├── 🐳 docker/                # Docker 개발 환경
└── 🚀 scripts/               # 배포 및 운영 스크립트
```

## 🛠️ 기술 스택

### Backend
- **Spring Boot 3.2** (Java 17)
- **MySQL 8.0** + **Redis 7.0**
- **QueryDSL** + **JPA Auditing**
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

### 🎯 설계 파일 기반 구현 워크플로우

**RoutePickr는 체계적인 설계 파일을 기반으로 한 단계별 구현 방식을 채택합니다.**

#### 📁 설계 파일 구조 (357개 파일)
```
설계 단계별 파일 구성:
├── step4-*: Entity 설계 (50개)
├── step5-*: Repository 설계 (51개)
├── step6-*: Service 설계 (20개, 77개 세분화 파일)
├── step7-*: Controller & DTO 설계 (15개 + 65개, 35개 파일)
├── step8-*: Security & 최종 검증 (40개 파일)
└── step9-*: Testing (91개 파일)
```

#### 🚀 Claude Code 활용 구현 방법

**1단계: API별 세분화 구현 요청**
```bash
# 예시 1: 회원가입 API 구현
claude code "회원가입 API를 구현해줘. 다음 설계 파일들을 참고해서:
- step7-1a_auth_controller.md (Controller 설계)
- step7-1c_auth_request_dtos.md (Request DTO)
- step7-1d_auth_response_dtos.md (Response DTO) 
- step7-1f_xss_security.md (보안 구현)
- step6-1a_auth_service.md (Service 로직)"

# 예시 2: 체육관 검색 API 구현  
claude code "주변 체육관 검색 API를 구현해줘. 관련 설계 파일:
- step7-4a_gym_controller.md
- step6-2a1_gym_management_core.md
- step6-2a2_gym_spatial_membership.md (공간쿼리 로직)"
```

**2단계: 자동 품질 보장**
- ✅ **보안**: XSS, CSRF, Rate Limiting 자동 적용
- ✅ **검증**: Bean Validation, 한국 특화 검증 포함
- ✅ **예외처리**: 177개 ErrorCode 체계 반영
- ✅ **캐싱**: Redis 캐싱 전략 자동 적용
- ✅ **한국 특화**: 좌표, 휴대폰, 한글, PG사 연동

**3단계: 일관성 있는 코드 생성**
모든 API가 동일한 설계 원칙을 따르며, 설계 문서에 명시된 보안/성능 최적화가 자동으로 반영됩니다.

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

### 💡 중요: 설계 기반 구현 가이드
- [📋 프로젝트 진행 상황](CLAUDE.md) - **설계 파일 기반 구현 워크플로우 가이드**
- [🚨 GitHub Actions 트러블슈팅 가이드](docs/GITHUB_ACTIONS_TROUBLESHOOTING.md)

### 🔍 파일 관리 최적화 완료 (357개 파일)

#### 📊 **파일 최적화 현황** (2025-09-03 완료)
- **총 파일 수**: 357개
- **최적화율**: 100% (357개 파일이 1000라인 이하)
- **대용량 파일**: 0개 (모든 파일 최적화 완료)
- **최근 세분화**: 9개 파일 (3개 대용량 → 9개 최적화)

#### 🎯 **Claude Code 최적화 인덱스**
프로젝트 파일 탐색을 위한 체계적 인덱스를 제공합니다:
- **[INDEX.md](INDEX.md)** - 357개 파일의 완전한 인덱스
- **[QUICKREF.md](QUICKREF.md)** - 핵심 파일 빠른 참조 가이드

#### 📚 **Claude Code 최적화 인덱스 시스템**
개발 효율성 극대화를 위한 체계적 인덱스를 구축했습니다:
- **[INDEX.md](INDEX.md)** - 357개 파일의 완전한 인덱스
- **[QUICKREF.md](QUICKREF.md)** - 핵심 파일 빠른 참조 가이드
- **Phase별 조직화** - 단계별 파일 구조 명확화
- **도메인별 그루핑** - 기능별 파일 클러스터링
- **검색 패턴 제공** - Claude Code에서 효율적인 파일 검색 지원

#### 🔄 **최근 세분화 완료**
대용량 파일을 기능별로 세분화하여 개발 효율성을 극대화:
```bash
# System Services (4개로 세분화)
step6-6d1_system_monitoring.md       # 실시간 모니터링 (345줄)
step6-6d2_health_check_service.md    # 헬스체크 (520줄)
step6-6d3_backup_management.md       # 백업 관리 (430줄)  
step6-6d4_performance_metrics.md     # 성능 메트릭 (537줄)

# Response DTOs (2개로 세분화)
step7-4e1_gym_response_dtos.md       # 암장 DTOs (196줄)
step7-4e2_route_climbing_response_dtos.md # 루트 & 클라이밍 DTOs (522줄)

# Security Monitoring (3개로 세분화)
step8-2d1_security_audit_logger.md   # 보안 감사 로거 (297줄)
step8-2d2_threat_detection_service.md # 위협 탐지 (323줄)
step8-2d3_security_monitoring_config.md # 모니터링 설정 (372줄)
step7-4e2_route_climbing_response_dtos.md # 루트/클라이밍 DTOs (522줄)

# Security Monitoring (3개로 세분화)
step8-2d1_security_audit_logger.md   # 보안 감사 로거 (297줄)
step8-2d2_threat_detection_service.md # 위협 탐지 (323줄)
step8-2d3_security_monitoring_config.md # 모니터링 설정 (372줄)
```

#### 🚨 **파일 인코딩 표준**
- **UTF-8 인코딩**: 모든 파일 UTF-8 표준 준수
- **중복 제거**: 5개 중복/불필요 파일 정리
- **일관성 보장**: 파일명 규칙 표준화 완료

### 📋 설계 문서 활용법
**모든 설계 파일은 Claude Code로 실제 구현할 때 참조용으로 사용됩니다:**
- **단계별 구현**: Entity → Repository → Service → Controller → Security → Testing 순서
- **API별 구현**: 관련 설계 파일들을 조합하여 완전한 API 구현
- **품질 보장**: 설계에 명시된 보안/성능 최적화 자동 적용
- **인덱스 활용**: INDEX.md와 QUICKREF.md로 효율적인 파일 탐색

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
- [🌐 GlobalExceptionHandler 구현](step3-3a1_global_exception_handler.md)
- [📋 ErrorResponse DTO & Spring Boot 통합](step3-3a2_error_response_integration.md)
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

### 6단계: Service 레이어 구현 ✨ (총 20개 Service 완성)
#### 6-1: 인증 및 사용자 관리 Service (4개)
- [🔐 JWT 인증 및 소셜 로그인 Service](step6-1a_auth_service.md)
- [📧 비동기 이메일 발송 및 Redis 인증 코드 Service](step6-1b_email_service.md)
- [👤 사용자 관리, 프로필, 팔로우 Service](step6-1c_user_service.md)
- [✅ 본인인증, 약관동의 및 보안 유틸리티](step6-1d_verification_security.md)

#### 6-2: 체육관 및 루트 관리 Service (4개)
- [🏢 체육관 관리 Service (한국좌표 검증, 공간쿼리)](step6-2a_gym_service.md)
- [🧗 루트 관리 Service (V등급/YDS 변환, 난이도 투표)](step6-2b_route_service.md)
- [🎬 루트 미디어 Service (이미지/동영상, 썸네일, 댓글시스템)](step6-2c_route_media_service.md)
- [📊 클라이밍 기록 Service (통계분석, 신발관리, 성장추적)](step6-2d_climbing_record_service.md)

#### 6-3: 태그 및 추천 시스템 Service (4개)
- [🏷️ TagService (태그 관리, 6가지 카테고리)](step6-3a_tag_service.md)
- [🎯 UserPreferenceService (사용자 선호도, 개인화)](step6-3b_user_preference_service.md)
- [🔗 RouteTaggingService (루트-태그 연관, 품질검증)](step6-3c_route_tagging_service.md)
- [🤖 RecommendationService (AI 추천, 태그70%+레벨30%)](step6-3d_recommendation_service.md)

#### 6-4: 커뮤니티 시스템 Service (4개)
- [📝 PostService (게시글 CRUD, XSS방지, 미디어처리)](step6-4a_post_service.md)
- [💬 CommentService (계층형 댓글, 3단계 depth)](step6-4b_comment_service.md)
- [👍 InteractionService (좋아요/북마크, Redis 최적화)](step6-4c_interaction_service.md)
- [💌 MessageService (개인메시지, 루트태깅, 대량발송)](step6-4d_message_service.md)

#### 6-5: 결제 및 알림 Service (4개)
- [💳 PaymentService (한국PG 연동, SERIALIZABLE 트랜잭션)](step6-5a_payment_service.md)
- [💰 PaymentRefundService (자동환불, 부분환불, 승인워크플로우)](step6-5b_payment_refund_service.md)
- [🔗 WebhookService (웹훅처리, 서명검증, 지수백오프)](step6-5c_webhook_service.md)
- [🔔 NotificationService (다채널 알림, FCM/이메일/인앱)](step6-5d_notification_service.md)

#### 6-6: 시스템 관리 Service (4개) - **최근 완료**
- [📊 ApiLogService (API로그, 성능모니터링, 에러분석)](step6-6a_api_log_service.md)
- [🔗 ExternalApiService (외부API관리, 상태모니터링, 암호화)](step6-6b_external_api_service.md)
- [🗄️ CacheService (Redis캐시, TTL최적화, 스마트워밍업)](step6-6c_cache_service.md)

#### 6-6: 시스템 관리 Service (4개) - **최근 세분화 완료**
- [📊 SystemMonitoring (실시간 모니터링)](step6-6d1_system_monitoring.md)
- [🔍 HealthCheckService (헬스체크)](step6-6d2_health_check_service.md)
- [💾 BackupManagement (백업 관리)](step6-6d3_backup_management.md)
- [📈 PerformanceMetrics (성능 메트릭)](step6-6d4_performance_metrics.md)

### 7단계: Controller & DTO 구현 ✨ (15개 Controller + 65개 DTO 완성)

#### 보안 감사 완료
- [🛡️ Security Audit Report](step7-5_security_audit_report.md) - **91.3/100점 통과**
- XSS/SQL Injection 방지, Rate Limiting, 민감정보 마스킹 완료

#### 7-1: Authentication & Email Controllers
- [🔐 Auth Controller (JWT, OAuth2, 세션관리)](step7-1a_auth_controller.md)
- [📧 Email Controller (인증메일, 템플릿)](step7-1b_email_controller.md)

#### 7-2: User Management Controllers  
- [👤 User Controller (프로필, 검색, 팔로우)](step7-2a_user_controller.md)
- [👥 Follow Controller (팔로우 관리)](step7-2b_follow_controller.md)

#### 7-3: Tag & Recommendation Controllers
- [🏷️ Tag Controller (태그 관리, 검색)](step7-3a_tag_controller.md)
- [🎯 Recommendation Controller (AI 추천)](step7-3c_recommendation_controller.md)

#### 7-4: Gym & Route Controllers - **최근 세분화 완료**
- [🏢 Gym Controller (체육관 관리)](step7-4a_gym_controller.md)
- [🧗 Route Controller (루트 CRUD)](step7-4b_route_controller.md)
- [📊 Climbing Controller (기록관리)](step7-4c_climbing_controller.md)
- [🏢 Gym Response DTOs (암장 응답)](step7-4e1_gym_response_dtos.md)
- [🧗 Route & Climbing Response DTOs](step7-4e2_route_climbing_response_dtos.md)

#### 7-5: Community & System Controllers
- [💬 Community Controllers (게시글, 댓글)](step7-5a_community_controllers.md)
- [💳 Payment Controller (결제, 환불)](step7-5b_payment_controller.md)
- [🔔 Notification Controller (알림, FCM)](step7-5c_notification_controller.md)
- [⚙️ System Controller (모니터링)](step7-5d_system_controller.md)

#### 보안 강화 및 최종 검증
- [🛡️ Security Enhancements (XSS, SQL Injection 방지)](step7-5f_security_enhancements.md)
- [✅ Security Audit Report (보안 감사 통과)](step7-5_security_audit_report.md)

### 8단계: Security 설정 🔒 (40개 파일, 부분 완료)

#### JWT & Authentication 설정
- [🔐 JWT Token Provider](step8-1c_jwt_token_provider.md)
- [🔑 JWT Authentication Filter](step8-1b_jwt_authentication_filter.md)
- [⚙️ Security Configuration](step8-1a_security_config.md)

#### Security Monitoring 시스템 - **최근 세분화 완료**
- [📊 Security Audit Logger (보안 감사 로거, 297줄)](step8-2d1_security_audit_logger.md)
- [🚨 Threat Detection Service (위협 탐지, 323줄)](step8-2d2_threat_detection_service.md) 
- [⚡ Security Monitoring Config (모니터링 설정, 372줄)](step8-2d3_security_monitoring_config.md)

#### Security 강화 기능
- [🚫 Rate Limiting Implementation](step8-2a_rate_limiting_implementation.md)
- [🌐 IP Access Control](step8-2b_ip_access_control.md)
- [🛡️ CORS & CSRF Protection](step8-3a_cors_configuration.md)
- [🔒 XSS Input Validation](step8-3d_xss_input_validation.md)

### 9단계: Testing 🧪 (91개 파일, 준비 완룄)

#### Testing 구성 (6개 Phase)
- **step9-1**: Auth & Email Service Tests (15개 파일)
- **step9-2**: Tag & Recommendation Tests (17개 파일)
- **step9-3**: Gym & Route Tests (12개 파일)
- **step9-4**: Community & Social Tests (15개 파일)
- **step9-5**: Payment & Notification Tests (17개 파일)
- **step9-6**: Integration & E2E Tests (15개 파일)

---

<div align="center">

**🧗‍♀️ RoutePickr로 더 나은 클라이밍 경험을 시작하세요! 🧗‍♂️**

**📁 357개 파일 최적화 완료** - 100% 파일이 1000라인 이하

📊 **프로젝트 진행률: 100%** (9/9 단계 완료!) 🎉

- ✅ **완료**: 50개 Entity + 51개 Repository + 20개 Service + 15개 Controller + 65개 DTO + 56개 Security + 91개 Testing
- 🎉 **설계 완성**: 9단계 Testing까지 모든 설계 100% 완료!
- 🚀 **다음**: 설계 기반 실제 코드 구현 시작

Made with ❤️ by RoutePickr Team

</div>