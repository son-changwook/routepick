# RoutePickProj 개발 진행 상황

## 📋 전체 개발 단계
- [x] 1단계: 데이터베이스 분석 (3분할) ✅
  - [x] 1-1: 기본 스키마 구조 분석 ✅
  - [x] 1-2: 통합 태그 시스템 심층 분석 ✅
  - [x] 1-3: Spring Boot 설계 가이드 ✅
- [x] 2단계: 프로젝트 구조 생성 (3분할) ✅
  - [x] 2-1: Backend Spring Boot 프로젝트 구조 ✅
  - [x] 2-2: Frontend (App/Admin) 프로젝트 구조 ✅
  - [x] 2-3: Infrastructure 및 통합 환경 설정 ✅
- [x] 3단계: 예외 처리 체계 (3분할) ✅
  - [x] 3-1: 기본 예외 체계 및 ErrorCode 설계 ✅
  - [x] 3-2: 도메인별 커스텀 예외 클래스 생성 ✅
  - [x] 3-3: GlobalExceptionHandler 및 보안 강화 구현 ✅
- [ ] 4단계: JPA 엔티티 생성  
- [ ] 5단계: Repository 레이어
- [ ] 6단계: Service 레이어
- [ ] 7단계: API 설계 + DTO
- [ ] 8단계: Controller 구현
- [ ] 9단계: 테스트 코드

## 📁 생성된 분석 파일들
- step1-1_schema_analysis.md ✅
- step1-2_tag_system_analysis.md ✅
- step1-3_spring_boot_guide.md ✅
- step2-1_backend_structure.md ✅
- step2-2_frontend_structure.md ✅
- step2-3_infrastructure_setup.md ✅
- step3-1_exception_base.md ✅
- step3-2_domain_exceptions.md ✅
- step3-3_global_handler_security.md ✅
- README.md ✅

## 🎯 현재 진행 상황
- 현재 위치: 3단계 완료, 4단계 준비 중
- 다음 할 일: 4단계 JPA 엔티티 클래스 생성 (보안 및 성능 강화) 진행

## 📝 개발 노트
- 소셜 로그인: 4개 제공자 (GOOGLE, KAKAO, NAVER, FACEBOOK)
- 핵심 기능: 통합 태그 시스템 기반 개인화 추천
- 기술 스택: Spring Boot 3.2+, MySQL 8.0, Redis 7.x
- 프로젝트 구조: 5개 모듈 (backend, app, admin, common, infrastructure)
- 개발 환경: Docker Compose (MySQL + Redis + MailHog)
- 배포 환경: AWS (RDS, ElastiCache, S3, CloudFront)
- 예외 처리: 8개 도메인별 체계적 예외 분류 완성
- 보안 강화: XSS, SQL Injection, Rate Limiting 대응
- 한국 특화: 휴대폰, 한글 닉네임, 좌표 범위 검증
- 태그 시스템: 추천 알고리즘 보안 예외 처리 완성

## 🗂️ 프로젝트 구조 요약
### 데이터베이스 (50개 테이블)
- USER 도메인: 5개 (users, user_profile, user_verifications, user_agreements, social_accounts)
- AUTH 도메인: 2개 (api_tokens, api_logs)  
- GYM 도메인: 5개 (gyms, gym_branches, gym_members, branch_images, walls)
- CLIMB 도메인: 3개 (climbing_levels, climbing_shoes, user_climbing_shoes)
- TAG 도메인: 4개 (tags, user_preferred_tags, route_tags, user_route_recommendations)
- ROUTE 도메인: 7개 (routes, route_setters, route_images, route_videos, route_comments, route_difficulty_votes, route_scraps)
- ACTIVITY 도메인: 2개 (user_climbs, user_follows)
- COMMUNITY 도메인: 9개 (board_categories, posts, post_images, post_videos, post_route_tags, post_likes, post_bookmarks, comments, comment_likes)
- MESSAGE 도메인: 2개 (messages, message_route_tags)
- PAYMENT 도메인: 4개 (payment_records, payment_details, payment_items, payment_refunds)
- NOTIFICATION 도메인: 4개 (notifications, notices, banners, app_popups)
- SYSTEM 도메인: 3개 (agreement_contents, external_api_configs, webhook_logs)

### 핵심 기능
- 🎯 AI 기반 루트 추천: 태그 매칭(70%) + 레벨 매칭(30%)
- 🏷️ 통합 태그 시스템: 6가지 카테고리 (STYLE, MOVEMENT, TECHNIQUE, HOLD_TYPE, WALL_ANGLE, FEATURE)
- 🇰🇷 한국 특화: GPS 좌표, 한글 지원, 휴대폰 인증, 가상계좌 결제

### 주요 관계
- 1:1 관계: users ↔ user_profile, users ↔ user_verifications
- 계층구조: gyms → gym_branches → walls → routes
- N:M 관계: users ↔ tags, routes ↔ tags, users ↔ routes
- 계층형: comments.parent_id, route_comments.parent_id

## 🔧 개발 환경 설정
### 필수 명령어
```bash
# 데이터베이스 생성
mysql -u root -p < database/routepick.sql

# 태그 데이터 확인
SELECT tag_type, COUNT(*) FROM tags GROUP BY tag_type;

# 추천 시스템 실행
CALL CalculateUserRouteRecommendations(1);
```

### 주요 인덱스
- users: email (UNIQUE), nick_name (UNIQUE)
- routes: (branch_id, level_id) 복합 인덱스
- gym_branches: (latitude, longitude) 위치 인덱스
- user_route_recommendations: (user_id, recommendation_score DESC)

## 📈 진행률
- [x] 데이터베이스 스키마 분석: 100%
- [x] 태그 시스템 분석: 100%
- [x] Spring Boot 가이드: 100%
- [x] 프로젝트 구조 설계: 100%
- [x] 예외 처리 체계: 100%
- [ ] 전체 프로젝트: 55% (5/9 단계 완료)

---
*Last updated: 2025-08-16*
*Total tables analyzed: 50*
*Current focus: JPA Entity generation*