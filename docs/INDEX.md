# 📚 RoutePickr 프로젝트 문서 인덱스

> **Claude Code 최적화 인덱스** - 346개 설계 문서의 효율적 참조를 위한 구조화된 인덱스

---

## 🔍 **Quick Access (자주 사용)**

### 📌 **핵심 참조 파일**
- **[README.md](../README.md)** - 프로젝트 전체 현황 및 진행률
- **[docs/step1-1_schema_analysis.md](step1-1_schema_analysis.md)** - 데이터베이스 스키마 (50개 테이블)
- **[docs/step1-2_tag_system_analysis.md](step1-2_tag_system_analysis.md)** - 통합 태그 시스템 (추천 알고리즘)
- **[docs/step3-1b_error_codes.md](step3-1b_error_codes.md)** - ErrorCode 체계 (177개 코드)

### 🎯 **개발 현황 Overview**
- ✅ **완료**: Phase 1-9 (분석→설계→구현→API→Security→Testing 모든 단계 완료!) 
- 📝 **Testing**: 91개 테스트 파일 설계 완성
- **진행률**: 100% (9/9 단계 완료)

---

## 📁 **Phase별 상세 인덱스**

### 🔬 **Phase 1-3: Foundation (20개 파일)**
**분석 및 기초 설계 단계**

#### Phase 1: 스키마 분석 (3개)
- `step1-1_schema_analysis.md` (13KB) - 기본 스키마 분석
- `step1-2_tag_system_analysis.md` (21KB) - 태그 시스템 심층 분석  
- `step1-3a_architecture_social_recommendation.md` (10KB) - 아키텍처 설계

#### Phase 2: 구조 설계 (5개)
- `step2-1_backend_structure.md` (28KB) - Backend 구조
- `step2-2a_frontend_app_structure.md` (13KB) - App 구조
- `step2-3a_infrastructure_aws_terraform.md` (10KB) - AWS 인프라

#### Phase 3: 예외 처리 (12개)
- `step3-1b_error_codes.md` (30KB) - **177개 ErrorCode 정의**
- `step3-3a1_global_exception_handler.md` (765줄) - **GlobalExceptionHandler 구현**
- `step3-3a2_error_response_integration.md` (241줄) - **ErrorResponse DTO & Spring Boot 통합**
- `step3-3b_security_features.md` (24KB) - 보안 강화 기능

---

### **Phase 4: JPA Entities (23개 파일)**
**총 50개 엔티티 완성**

#### 👤 User Domain (7개 엔티티)
- `step4-1a_base_common_entities.md` - BaseEntity, User
- `step4-1b1_user_entity_core.md` - User 핵심 엔티티 (세분화)
- `step4-1b2_userprofile_socialaccount.md` - UserProfile, SocialAccount (세분화)
- `step4-1c_user_extended_entities.md` - 확장 엔티티

#### 🏷 Tag Domain (4개 엔티티)  
- `step4-2a1_tag_core_entities.md` - Tag, UserPreferredTag (세분화)
- `step4-2a2_route_tagging_recommendation_entities.md` - RouteTag, Recommendation (세분화)

#### 🏢 Gym Domain (5개 엔티티)
- `step4-3a1_gym_basic_entities.md` - Gym, GymBranch
- `step4-3a2_gym_extended_entities.md` - GymMember, Wall, BranchImage

#### 🧗 Route Domain (7개 엔티티)
- `step4-3b1_route_core_entities.md` - Route, RouteSetter  
- `step4-3b2_route_interaction_entities.md` - RouteImage, RouteComment 등

#### 🏘 Community Domain (8개 엔티티)
- `step4-4a1_community_core_entities.md` - BoardCategory, Post
- `step4-4a2_community_interaction_entities.md` - Comment, PostLike 등

#### 💳 Payment + Notification (8개 엔티티)
- `step4-4b1_payment_entities.md` - PaymentRecord, PaymentDetail
- `step4-4b2a_personal_notification_entities.md` - Notification

#### ⚙ System Management (6개 엔티티)
- `step4-4c1_system_management_entities.md` - AgreementContent, ExternalApiConfig
- `step4-4c2_system_logging_entities.md` - ApiLog, WebhookLog

---

### 🗄 **Phase 5: Repositories (55개 파일)**
**총 51개 Repository 완성 - QueryDSL 최적화**

#### Repository 구성
- **User**: 7개 Repository (인증, 검증, 팔로우 포함)
- **Tag**: 4개 Repository (태그 매칭, 추천)
- **Gym**: 5개 Repository (공간 쿼리, 위치 기반)
- **Route**: 8개 Repository (미디어, 댓글, 투표, 스크랩)
- **Community**: 9개 Repository (게시판, 댓글, 상호작용)
- **Payment**: 4개 Repository (PCI DSS 보안)
- **System**: 기타 Repository

#### 핵심 Repository 파일
- `step5-1a_common_repositories.md` - BaseRepository, TimeZone 유틸
- `step5-2a_tag_core_repositories.md` - **태그 매칭 알고리즘**
- `step5-3a_gym_core_repositories.md` - **한국 좌표 공간 쿼리**
- `step5-3c1_route_search_repositories.md` - **루트 검색 최적화**

---

### ⚙ **Phase 6: Services (77개 파일)**
**총 20개 Service 완성 - 비즈니스 로직 핵심**

#### 🔐 Authentication Services (4개)
- `step6-1a_auth_service.md` (22KB) - **JWT 인증, 소셜 로그인**
- `step6-1b_email_service.md` (17KB) - 비동기 이메일, Redis 인증
- `step6-1c_user_service.md` (16KB) - 사용자 관리, 프로필, 팔로우
- `step6-1d_verification_security.md` (21KB) - 본인인증, 보안 유틸

#### 🏢 Gym & Route Services (4개)
- `step6-2a_gym_service.md` (20KB) - **체육관 관리, 한국좌표 검증**
- `step6-2b_route_service.md` (26KB) - **루트 관리, V등급/YDS 변환**
- `step6-2c_route_media_service.md` (26KB) - 루트 미디어, 댓글시스템
- `step6-2d_climbing_record_service.md` (26KB) - 클라이밍 기록, 통계분석

#### 🏷 Tag & Recommendation Services (4개)
- `step6-3a_tag_service.md` - 태그 관리 (6가지 카테고리)
- `step6-3b_user_preference_service.md` - 사용자 선호도, 개인화
- `step6-3c_route_tagging_service.md` - **루트-태그 연관, 품질검증**
- `step6-3d_recommendation_service.md` - **AI 추천 (태그70% + 레벨30%)**

#### 🏘 Community Services (4개)  
- `step6-4a_post_service.md` - 게시글 CRUD, XSS방지
- `step6-4b_comment_service.md` - 계층형 댓글 (3단계 depth)
- `step6-4c_interaction_service.md` - 좋아요/북마크, Redis 최적화
- `step6-4d_message_service.md` - 개인메시지, 루트태깅

#### 💳 Payment & Notification Services (4개)
- `step6-5a_payment_service.md` - **한국PG 연동, SERIALIZABLE 트랜잭션**
- `step6-5b_payment_refund_service.md` - 자동환불, 부분환불
- `step6-5c_webhook_service.md` - 웹훅처리, 서명검증
- `step6-5d_notification_service.md` - **다채널 알림 (FCM/이메일/인앱)**

#### ⚙ System Services (4개) - **최근 세분화 완료**
- `step6-6d1_system_monitoring.md` (345줄) - **실시간 시스템 모니터링**
- `step6-6d2_health_check_service.md` (520줄) - **헬스체크 서비스**
- `step6-6d3_backup_management.md` (430줄) - **백업 관리 시스템**
- `step6-6d4_performance_metrics.md` (537줄) - **성능 메트릭 분석**

---

### 🎮 **Phase 7: Controllers & DTOs (35개 파일)**
**총 15개 Controller + 65개 DTO 완성**

#### API Controller 구성
- **step7-1**: Auth & Email Controllers (인증 시스템)
- **step7-2**: User & Profile Controllers (사용자 관리)  
- **step7-3**: Tag & Recommendation Controllers (태그/추천)
- **step7-4**: Gym, Route, Climbing Controllers (암장/루트) - **최근 세분화**
- **step7-5**: Community, Payment, System Controllers (커뮤니티/결제)

#### 세분화된 Response DTOs - **최근 완료**
- `step7-4e1_gym_response_dtos.md` (196줄) - **암장 Response DTOs**
- `step7-4e2_route_climbing_response_dtos.md` (522줄) - **루트 & 클라이밍 Response DTOs**

#### 보안 강화 완료
- `step7-5_security_audit_report.md` - **보안 감사 91.3/100점**
- XSS, SQL Injection, Rate Limiting, 민감정보 마스킹 완료

---

### 🔒 **Phase 8: Security (40개 파일)**
**🟡 현재 진행 중**

#### 보안 시스템 - **최근 세분화 완료**  
- `step8-2d1_security_audit_logger.md` (297줄) - **보안 이벤트 로깅**
- `step8-2d2_threat_detection_service.md` (323줄) - **위협 탐지 서비스**
- `step8-2d3_security_monitoring_config.md` (372줄) - **보안 모니터링 설정**

#### 완료된 보안 요소
- JWT 인증 시스템
- Rate Limiting (Redis + Lua)
- IP 접근 제어
- XSS/CSRF 방지
- 보안 헤더 설정

---

### 🧪 **Phase 9: Testing (91개 파일)**
**⏳ 대기 중 - 테스트 코드 구현**

#### 테스트 구성 (6개 Phase)
- **step9-1**: Auth & Email Service Tests
- **step9-2**: Tag & Recommendation Tests  
- **step9-3**: Gym & Route Tests
- **step9-4**: Community & Social Tests
- **step9-5**: Payment & Notification Tests
- **step9-6**: Integration & E2E Tests

#### 대용량 파일 주의 ⚠
#### step9-6d2 세분화 (3개 파일)
- `step9-6d2a_failure_recovery_system.md` (359줄) - **FailureRecoveryService 시스템**
- `step9-6d2b_failure_recovery_test_scenarios.md` (726줄) - **E2E 실패 복구 테스트 시나리오**  
- `step9-6d2c_recovery_metrics_monitoring.md` (25줄) - **복구 메트릭 및 모니터링**

#### step9-6d3 세분화 (4개 파일)
- `step9-6d3a_test_data_generator.md` (487줄) - **한국어 테스트 데이터 생성기**
- `step9-6d3b_test_environment_manager.md` (170줄) - **테스트 환경 관리 유틸리티**
- `step9-6d3c_validation_utilities.md` (149줄) - **E2E 테스트 검증 유틸리티**
- `step9-6d3d_scenario_execution_helper.md` (262줄) - **시나리오 실행 헬퍼**

---

## **아키텍처 Quick Reference**

### 📊 **데이터베이스 (50개 테이블)**
- **USER 도메인**: 5개 (users, user_profile, user_verifications 등)
- **GYM 도메인**: 5개 (gyms, gym_branches, walls 등)
- **ROUTE 도메인**: 7개 (routes, route_images, route_comments 등)
- **TAG 도메인**: 4개 (tags, user_preferred_tags, route_tags 등)
- **COMMUNITY 도메인**: 9개 (posts, comments, post_likes 등)
- **PAYMENT 도메인**: 4개 (payment_records, payment_details 등)

### 🎯 **핵심 기능**
- **AI 기반 루트 추천**: 태그 매칭(70%) + 레벨 매칭(30%)
- **통합 태그 시스템**: 6가지 카테고리 (STYLE, MOVEMENT, TECHNIQUE 등)
- **한국 특화**: GPS 좌표, 한글 지원, 휴대폰 인증, 가상계좌 결제
- **소셜 로그인**: 4개 제공자 (GOOGLE, KAKAO, NAVER, FACEBOOK)

### **기술 스택**
- **Backend**: Spring Boot 3.2+, MySQL 8.0, Redis 7.x
- **보안**: JWT, OAuth2, Rate Limiting, XSS/CSRF 방지
- **성능**: QueryDSL, Redis 캐싱, CDN 연동
- **배포**: AWS (RDS, ElastiCache, S3, CloudFront)

---

## 📝 **최근 업데이트**

### ✅ **2025-09-03 완료 작업**
1. **대용량 파일 세분화 완료 (3개 → 9개)**
   - step9-6d2_e2e_failure_recovery_test.md (1,111줄) → 3개 파일
   - step9-6d3_e2e_helper_utils.md (1,071줄) → 4개 파일
   - step3-3a_global_handler_core.md (1,007줄) → 2개 파일

2. **파일 최적화 100% 달성**
   - 전체 357개 파일 모두 1000라인 이하
   - 평균 354줄/파일로 최적화 완료

### 🎯 **세분화 성과**
- **총 처리**: 3개 대용량 파일 (3,189줄) → 9개 최적화 파일 (평균 354줄)
- **최적화율**: 100% (357/357개 파일 1000라인 이하 달성)
- **Claude Code 최적화**: 완전 달성

---

**프로젝트 현황**: 9/9 단계 완료 (100%)  
**총 설계 파일**: 353개 (step*.md)  
**전체 문서**: 357개 (docs/), 359개 (프로젝트 전체)  
**최적화율**: 100% (353/353개 설계 파일 1000라인 이하)  
**다음 단계**: 실제 코드 구현 (모든 설계 완료 상태)  
**추가 파일**: INDEX.md, QUICKREF.md, docs/README.md, GITHUB_ACTIONS_TROUBLESHOOTING.md, step5-9_comprehensive_review_report.md

---

*Last Updated: 2025-09-03*  
*Generated for Claude Code Optimization*

---

## **추가 도구 파일**

### 📋 **문서 관리**
- **[INDEX.md](INDEX.md)** - 전체 353개 설계 파일의 체계적 인덱스 (이 파일)
- **[QUICKREF.md](QUICKREF.md)** - 핵심 파일 빠른 참조 가이드
- **[docs/README.md](docs/README.md)** - docs 폴더 구조 설명

### 🚀 **CI/CD 및 배포**
- **[GITHUB_ACTIONS_TROUBLESHOOTING.md](GITHUB_ACTIONS_TROUBLESHOOTING.md)** - GitHub Actions CI/CD 트러블슈팅 가이드

### 📊 **품질 검토**
- **[step5-9_comprehensive_review_report.md](step5-9_comprehensive_review_report.md)** - Phase 5-9 종합 품질 검토 보고서