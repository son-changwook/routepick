# ⚡ RoutePickr Quick Reference

> **Claude Code 빠른 참조용** - 핵심 파일만 모음

---

## 🔥 **Most Used Files**

### 📋 **프로젝트 현황**
- **[INDEX.md](INDEX.md)** - 📚 전체 파일 인덱스 (353개 설계 파일)
- **[README.md](../README.md)** - 📊 전체 프로젝트 현황
- **[docs/README.md](README.md)** - 📁 문서 구조 가이드

### 🗄 **데이터 구조**  
- **[step1-1_schema_analysis.md](step1-1_schema_analysis.md)** - 🗃 DB 스키마 (50개 테이블)
- **[step1-2_tag_system_analysis.md](step1-2_tag_system_analysis.md)** - 🏷 태그 시스템 (추천 알고리즘)

### ⚠ **예외 처리**
- **[step3-1b_error_codes.md](step3-1b_error_codes.md)** - 🚨 ErrorCode (177개)
- **[step3-3a1_global_exception_handler.md](step3-3a1_global_exception_handler.md)** - 🛡 GlobalExceptionHandler 구현
- **[step3-3a2_error_response_integration.md](step3-3a2_error_response_integration.md)** - 📋 ErrorResponse DTO & Spring Boot 통합

---

## 🎯 **도메인별 핵심 파일**

### 👤 **User & Auth**
```bash
# 엔티티
step4-1b1_user_entity_core.md          # User 엔티티
step4-1b2_userprofile_socialaccount.md # UserProfile, 소셜 로그인

# 서비스  
step6-1a_auth_service.md               # JWT 인증 (22KB)
step6-1c_user_service.md               # 사용자 관리 (16KB)

# API
step7-1a_auth_controller.md            # 인증 API (7개 엔드포인트)
step7-2a_user_controller.md            # 사용자 API
```

### 🏢 **Gym & Route**
```bash
# 엔티티
step4-3a1_gym_basic_entities.md        # Gym, GymBranch
step4-3b1_route_core_entities.md       # Route, RouteSetter

# 서비스 (최근 세분화)
step6-2a_gym_service.md               # 체육관 관리 (20KB)
step6-2b_route_service.md             # 루트 관리 (26KB)

# API (최근 세분화)  
step7-4e1_gym_response_dtos.md        # 암장 Response DTOs
step7-4e2_route_climbing_response_dtos.md # 루트 & 클라이밍 DTOs
```

### 🏷 **Tag & Recommendation**
```bash
# 엔티티
step4-2a1_tag_core_entities.md        # Tag, UserPreferredTag  
step4-2a2_route_tagging_recommendation_entities.md # AI 추천

# 서비스
step6-3c_route_tagging_service.md     # 루트-태그 연관
step6-3d_recommendation_service.md    # AI 추천 (태그70% + 레벨30%)
```

### 🔒 **Security (최근 세분화)**
```bash
# 보안 서비스 
step8-2d1_security_audit_logger.md    # 보안 이벤트 로깅 (297줄)
step8-2d2_threat_detection_service.md # 위협 탐지 (323줄)  
step8-2d3_security_monitoring_config.md # 모니터링 설정 (372줄)

# JWT & 인증
step8-1c_jwt_token_provider.md        # JWT 토큰 관리
```

### ⚙ **System Services (최근 세분화)**
```bash
# 시스템 모니터링
step6-6d1_system_monitoring.md        # 실시간 모니터링 (345줄)
step6-6d2_health_check_service.md     # 헬스체크 (520줄)
step6-6d3_backup_management.md        # 백업 관리 (430줄)  
step6-6d4_performance_metrics.md      # 성능 메트릭 (537줄)
```

---

## 📊 **통계 & 상태**

### 📈 **개발 진행률**
- ✅ **완료**: Phase 1-9 (Testing 설계까지 모든 단계 완료!)
- 📝 **상태**: 91개 Testing 파일 설계 완성
- **전체**: 100% (9/9 단계 완료)

### 📁 **파일 현황**
- **설계 파일**: 353개 (step*.md)
- **전체 문서**: 357개 (docs/), 359개 (프로젝트 전체)
- **최적화**: 100% (353개 설계 파일 ≤ 1000줄) - 완료
- **대용량**: 0개 (모든 파일 최적화 완료)
- **세분화 성과**: 3개 → 9개 (평균 354줄/파일)

### **설계 완료**
- **엔티티**: 50개 (JPA + QueryDSL)
- **Repository**: 51개 (공간쿼리, 성능최적화)
- **Service**: 20개 (비즈니스 로직)  
- **Controller**: 15개 (REST API)
- **DTO**: 65개 (Request 32 + Response 33)
- **Security**: 56개 (완전한 보안 시스템) 
- **Testing**: 91개 (전체 테스트 설계 완료)

---

## 🚨 **주의사항**

### 🎉 **대용량 파일 세분화 100% 완료** ✅
```bash
# ✨ 모든 1000라인+ 대용량 파일 세분화 완료! ✨
# 전체 357개 파일이 모두 관리 가능한 크기로 최적화 완료

# step9-6d2 세분화 완료 (3개 파일)
step9-6d2a_failure_recovery_system.md         # 359줄 - FailureRecoveryService
step9-6d2b_failure_recovery_test_scenarios.md # 726줄 - E2E 실패 복구 테스트
step9-6d2c_recovery_metrics_monitoring.md     # 25줄  - 복구 메트릭

# step9-6d3 세분화 완료 (4개 파일)  
step9-6d3a_test_data_generator.md             # 487줄 - 테스트 데이터 생성기
step9-6d3b_test_environment_manager.md        # 170줄 - 테스트 환경 관리
step9-6d3c_validation_utilities.md            # 149줄 - 검증 유틸리티
step9-6d3d_scenario_execution_helper.md       # 262줄 - 시나리오 실행

# step3-3a 세분화 완료 (2개 파일)
step3-3a1_global_exception_handler.md         # 767줄 - GlobalExceptionHandler
step3-3a2_error_response_integration.md       # 245줄 - ErrorResponse DTO

최적화 성과: 3,189줄 → 9개 파일 (평균 354줄/파일)
```

### 🔄 **최근 변경 파일**
```bash
# 2025-09-03 최종 세분화 완료
step9-6d2a~c_*.md                      # E2E Failure Recovery 3개 파일
step9-6d3a~d_*.md                      # E2E Helper Utils 4개 파일
step3-3a1~2_*.md                       # Exception Handler 2개 파일

# 삭제된 원본 대용량 파일
step9-6d2_e2e_failure_recovery_test.md # 원본 (1,111줄)
step9-6d3_e2e_helper_utils.md          # 원본 (1,071줄)
step3-3a_global_handler_core.md        # 원본 (1,007줄)

세분화 완료: 전체 353개 설계 파일 100% 최적화
```

---

## 🎯 **Claude Code 사용 팁**

### 📝 **파일 참조 방법**
```bash
# 특정 도메인 찾기
"step4*user*"     # User 관련 엔티티
"step6*auth*"     # 인증 관련 서비스  
"step7*response*" # Response DTO

# 최근 세분화된 파일
"step6-6d[1-4]_*" # System 서비스 4개
"step7-4e[1-2]_*" # Response DTOs 2개  
"step8-2d[1-3]_*" # Security 3개
```

### 🔍 **검색 키워드**
- **`#core`** - 핵심 파일
- **`#entity`** - JPA 엔티티  
- **`#service`** - 비즈니스 로직
- **`#security`** - 보안 관련
- **`#recent`** - 최근 수정/생성

---

**⚡ 빠른 시작**: `INDEX.md` → 원하는 Phase → 구체적 파일  
**설계 완료**: 9/9 단계 모든 구현 설계 완성  
**세분화 완료**: 353개 설계 파일 100% Claude Code 최적화 달성

### **추가 도구 파일**
- **[GITHUB_ACTIONS_TROUBLESHOOTING.md](../GITHUB_ACTIONS_TROUBLESHOOTING.md)** - CI/CD 트러블슈팅 가이드
- **[step5-9_comprehensive_review_report.md](step5-9_comprehensive_review_report.md)** - 품질 검토 보고서

---

*Updated: 2025-09-03 | RoutePickr 100% 설계 완료! | Claude Code 최적화 100% 달성!*