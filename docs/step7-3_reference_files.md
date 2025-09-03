# Step 7-3: 태그 시스템 및 추천 Controller + DTO 참고 파일 목록

## 📋 구현 목표
7-3단계에서 구현할 항목들:
1. **TagController** - 태그 관리, 검색, 자동완성 API
2. **RecommendationController** - 개인화 추천, 루트 추천 API  
3. **Tag System DTOs** - 태그 관련 Request/Response DTO
4. **Recommendation DTOs** - 추천 관련 Request/Response DTO

---

## 🏷️ 태그 시스템 참고 파일들

### 1. 설계 및 분석 문서
- **step1-2_tag_system_analysis.md** ✅
  - 태그 시스템 전체 아키텍처 분석
  - 8가지 TagType 정의 (STYLE, MOVEMENT, TECHNIQUE, HOLD_TYPE, WALL_ANGLE, FEATURE, DIFFICULTY, OTHER)
  - 추천 알고리즘 기본 설계
  - 사용자-태그 관계 매핑

- **step1-3a_architecture_social_recommendation.md** ✅
  - 추천 시스템 아키텍처
  - 소셜 기능과 추천의 연계
  - 성능 최적화 전략

### 2. 예외 처리
- **step3-2c_tag_payment_exceptions.md** ✅
  - TagException, RecommendationException
  - ErrorCode 정의: TAG-001~099
  - 태그 관련 모든 예외 처리 체계

### 3. 엔티티 설계
- **step4-2a_tag_system_entities.md** ✅
  - Tag, UserPreferredTag, RouteTag, UserRouteRecommendation 엔티티
  - 관계 매핑 및 인덱스 전략
  - 추천 점수 계산 로직

### 4. Repository 레이어
- **step5-2a_tag_core_repositories.md** ✅
  - TagRepository, UserPreferredTagRepository
  - 태그 검색 최적화
  - 자동완성 쿼리

- **step5-2b_tag_route_repositories.md** ✅
  - RouteTagRepository, UserRouteRecommendationRepository
  - 추천 점수 계산 쿼리
  - 대량 데이터 처리 최적화

### 5. Service 레이어
- **step6-3a_tag_service.md** ✅
  - TagService 완전 구현
  - 태그 CRUD, 검색, 자동완성
  - 캐싱 전략 (4시간 TTL)

- **step6-3b_user_preference_service.md** ✅
  - UserPreferenceService 구현
  - 사용자 선호 태그 관리
  - 선호도 점수 계산

- **step6-3c_route_tagging_service.md** ✅
  - RouteTaggingService 구현
  - 루트-태그 매핑 관리
  - 태그 추천 및 자동 분류

- **step6-3d_recommendation_service.md** ✅
  - RecommendationService 구현
  - AI 기반 개인화 추천 (태그 70% + 레벨 30%)
  - 실시간 추천 업데이트
  - 배치 처리 최적화

---

## 🎯 추천 시스템 참고 파일들

### 1. 추천 알고리즘 설계
- **step1-2_tag_system_analysis.md** ✅
  - 기본 추천 알고리즘 (태그 매칭 + 난이도 매칭)
  - 사용자 활동 기반 학습
  - 실시간 업데이트 전략

- **step6-3d_recommendation_service.md** ✅
  - 구체적인 추천 점수 계산 로직
  - 개인화 추천 파이프라인
  - 성능 최적화 및 캐싱

---

## 🎨 Controller 패턴 참고 파일들

### 1. 기본 Controller 구조
- **step7-1a_auth_controller.md** ✅
  - RESTful API 설계 패턴
  - @PreAuthorize 보안 적용
  - @RateLimited 속도 제한
  - ApiResponse 표준 응답
  - Swagger 문서화

- **step7-2a_user_controller.md** ✅
  - CRUD 패턴 구현
  - 페이징 처리
  - 검색 기능 구현
  - 캐싱 전략

- **step7-2b_follow_controller.md** ✅
  - 관계 관리 패턴
  - 통계 API 구현
  - 비동기 알림 처리

### 2. DTO 설계 패턴
- **step7-1c_auth_request_dtos.md** ✅
  - Request DTO 검증 패턴
  - Bean Validation 활용
  - 한국 특화 검증

- **step7-1d_auth_response_dtos.md** ✅
  - Response DTO 설계
  - 민감정보 제외
  - 중첩 구조 활용

- **step7-2c_user_request_dtos.md** ✅
  - 복잡한 검색 조건 DTO
  - 페이징 파라미터
  - 조건부 검증

- **step7-2d_user_response_dtos.md** ✅
  - 통계 정보 포함 Response
  - 조건부 데이터 노출
  - 중첩된 정보 구조

### 3. 보안 및 성능 패턴
- **step7-1f_xss_security.md** ✅
  - XSS 방지 필터 적용
  - 입력 데이터 정제

- **step7-1g_rate_limiting.md** ✅
  - @RateLimited 구현
  - API별 속도 제한

- **step7-2h_conditional_masking.md** ✅
  - 조건부 데이터 마스킹
  - @MaskingRule 활용

---

## 📝 7-3단계 구현 계획

### TagController 구현 예정
1. **GET /api/v1/tags** - 태그 목록 조회 (타입별, 검색)
2. **GET /api/v1/tags/{id}** - 태그 상세 조회
3. **GET /api/v1/tags/autocomplete** - 태그 자동완성
4. **POST /api/v1/tags** - 태그 생성 (관리자)
5. **PUT /api/v1/tags/{id}** - 태그 수정 (관리자)
6. **DELETE /api/v1/tags/{id}** - 태그 삭제 (관리자)
7. **GET /api/v1/tags/statistics** - 태그 사용 통계

### RecommendationController 구현 예정
1. **GET /api/v1/recommendations/routes** - 개인화 루트 추천
2. **GET /api/v1/recommendations/tags** - 사용자 추천 태그
3. **POST /api/v1/recommendations/feedback** - 추천 피드백
4. **GET /api/v1/recommendations/trending** - 인기 루트/태그
5. **PUT /api/v1/recommendations/preferences** - 선호도 업데이트

### DTO 설계 예정
- **TagRequestDTOs**: TagSearchRequest, TagCreateRequest, TagUpdateRequest
- **TagResponseDTOs**: TagResponse, TagListResponse, TagStatsResponse
- **RecommendationRequestDTOs**: RecommendationRequest, PreferenceUpdateRequest
- **RecommendationResponseDTOs**: RouteRecommendationResponse, TagRecommendationResponse

---

## ⚙️ 기술적 고려사항

### 1. 성능 최적화
- Redis 캐싱 (1-4시간 TTL)
- 검색 인덱스 최적화
- 페이징 처리 필수
- 배치 처리 고려

### 2. 보안 요구사항
- @PreAuthorize 권한 체크
- @RateLimited 속도 제한
- XSS 방지 필터
- 입력 검증 강화

### 3. API 설계 원칙
- RESTful 설계
- 표준 HTTP 상태 코드
- 일관된 응답 형식
- 상세한 Swagger 문서

---

## 📊 구현 우선순위

### HIGH 우선순위
1. **TagController** - 태그 시스템 핵심 기능
2. **Tag DTOs** - Request/Response 정의
3. **기본 추천 API** - 루트 추천 핵심 기능

### MEDIUM 우선순위  
1. **RecommendationController** - 고급 추천 기능
2. **Recommendation DTOs** - 추천 관련 DTO
3. **통계 및 분석 API**

### LOW 우선순위
1. **관리자 전용 API**
2. **고급 분석 기능**
3. **A/B 테스트 지원**

---

*참고 파일 분석 완료일: 2025-08-25*  
*총 참고 파일: 16개*  
*다음 단계: TagController 구현 시작*