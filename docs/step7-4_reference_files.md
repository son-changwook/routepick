# Step 7-4: 암장 및 루트 관리 Controller + DTO 참고 파일 목록

## 📋 구현 목표
7-4단계에서 구현할 항목들:
1. **GymController** - 암장 관리, 검색, 지점 관리 API
2. **RouteController** - 루트 관리, 검색, 난이도 투표 API
3. **RouteMediaController** - 루트 이미지/동영상 관리 API
4. **Gym System DTOs** - 암장 관련 Request/Response DTO
5. **Route System DTOs** - 루트 관련 Request/Response DTO
6. **Route Media DTOs** - 미디어 관련 Request/Response DTO

---

## 🏢 암장(Gym) 시스템 참고 파일들

### 1. 설계 및 분석 문서
- **step1-1_schema_analysis.md** ✅
  - 전체 데이터베이스 구조 분석
  - 암장-지점-루트 계층 구조
  - 한국 특화 좌표 시스템

- **step1-3b_korean_business_jpa.md** ✅
  - 한국 특화 비즈니스 로직
  - GPS 좌표 범위 검증 (33°~38°N, 125°~132°E)
  - 한국 주소 체계 및 우편번호

- **step1-3c_performance_security.md** ✅
  - 공간 쿼리 최적화
  - 인덱스 전략 (위치 검색)
  - 보안 강화 방안

### 2. 예외 처리
- **step3-2b_gym_route_exceptions.md** ✅
  - GymException, RouteException
  - ErrorCode 정의: GYM-001~050, ROUTE-001~099
  - 암장/루트 관련 모든 예외 처리 체계

### 3. 엔티티 설계
- **step4-2b1_gym_management_entities.md** ✅
  - Gym, GymBranch 엔티티 관계
  - 암장 운영 시간, 가격 정보
  - 지점별 특화 정보

- **step4-3a1_gym_basic_entities.md** ✅
  - Gym (체육관 기본 정보) 엔티티
  - 운영 시간, 연락처, 웹사이트 관리
  - 상태 관리 (ACTIVE, INACTIVE, CLOSED)

- **step4-3a2_gym_extended_entities.md** ✅
  - GymBranch (지점), Wall (벽면) 엔티티
  - GymMember (회원권), BranchImage (지점 이미지)
  - 공간 정보 및 미디어 관리

- **step4-2b2_route_management_entities.md** ✅
  - Route, RouteSetter 엔티티
  - V등급/YDS 난이도 시스템
  - 루트 상태 관리 및 세터 정보

- **step4-3b1_route_core_entities.md** ✅
  - Route (루트 핵심 정보) 엔티티
  - 난이도, 색상, 설치일 관리
  - 태그 시스템 연동

- **step4-3b2_route_interaction_entities.md** ✅
  - RouteComment, RouteDifficultyVote, RouteScrap
  - 루트 상호작용 기능
  - 사용자 피드백 시스템

### 4. Repository 레이어
- **step5-3a_gym_core_repositories.md** ✅
  - GymRepository, GymBranchRepository
  - 위치 기반 검색 최적화
  - 거리 계산 쿼리

- **step5-3b_gym_additional_repositories.md** ✅
  - GymMemberRepository, WallRepository, BranchImageRepository
  - 복합 검색 조건
  - 이미지 메타데이터 관리

- **step5-3c1_route_search_repositories.md** ✅
  - RouteRepository (검색 특화)
  - 난이도별, 태그별, 지점별 검색
  - 페이징 최적화

- **step5-3c2_route_management_repositories.md** ✅
  - RouteSetterRepository
  - 루트 관리 기능
  - 세터별 통계

- **step5-3d1_route_image_repositories.md** ✅
  - RouteImageRepository
  - 이미지 업로드, 썸네일 관리
  - CDN 연동

- **step5-3d2_route_video_repositories.md** ✅
  - RouteVideoRepository
  - 동영상 업로드, 스트리밍
  - 트랜스코딩 지원

- **step5-3e1_route_comment_repositories.md** ✅
  - RouteCommentRepository
  - 계층형 댓글 구조
  - 대댓글 관리

- **step5-3e2_route_vote_scrap_repositories.md** ✅
  - RouteDifficultyVoteRepository, RouteScrapRepository
  - 난이도 투표 시스템
  - 북마크 기능

### 5. Service 레이어
- **step6-2a_gym_service.md** ✅
  - GymService 완전 구현
  - 암장 CRUD, 위치 검색, 지점 관리
  - 한국 좌표 검증 로직

- **step6-2b_route_service.md** ✅
  - RouteService 구현
  - 루트 CRUD, V등급/YDS 변환
  - 난이도 투표 및 통계

- **step6-2c_route_media_service.md** ✅
  - RouteMediaService 구현
  - 이미지/동영상 업로드, 썸네일 생성
  - 댓글 시스템

- **step6-2d_climbing_record_service.md** ✅
  - ClimbingRecordService 구현
  - 개인 기록 관리, 통계 분석
  - 신발 관리 시스템

---

## 🧗 루트(Route) 시스템 참고 파일들

### 1. 태그 연동 시스템
- **step6-3c_route_tagging_service.md** ✅
  - RouteTaggingService 구현
  - 루트-태그 매핑 관리
  - AI 기반 태그 추천

- **step5-2b_tag_route_repositories.md** ✅
  - RouteTagRepository, UserRouteRecommendationRepository
  - 태그 기반 추천 시스템
  - 사용자별 추천 점수

### 2. 미디어 관리 시스템
- **step5-3d1_route_image_repositories.md** ✅
  - RouteImageRepository 상세 구현
  - 이미지 메타데이터, 썸네일
  - S3/CloudFront 연동

- **step5-3d2_route_video_repositories.md** ✅
  - RouteVideoRepository 상세 구현
  - 동영상 스트리밍, 트랜스코딩
  - HLS/DASH 지원

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
  - 페이징 처리 최적화
  - 검색 기능 구현
  - 캐싱 전략

- **step7-2b_follow_controller.md** ✅
  - 관계 관리 패턴
  - 통계 API 구현
  - 비동기 알림 처리

- **step7-3a_tag_controller.md** ✅
  - 복잡한 검색 API
  - 자동완성 기능
  - 관리자 권한 분리

### 2. DTO 설계 패턴
- **step7-1c_auth_request_dtos.md** ✅
  - Request DTO 검증 패턴
  - Bean Validation 활용
  - 한국 특화 검증

- **step7-1d_auth_response_dtos.md** ✅
  - Response DTO 설계
  - 민감정보 제외 정책
  - 중첩 구조 활용

- **step7-2c_user_request_dtos.md** ✅
  - 복잡한 검색 조건 DTO
  - 페이징 파라미터
  - 조건부 검증 로직

- **step7-2d_user_response_dtos.md** ✅
  - 통계 정보 포함 Response
  - 조건부 데이터 노출
  - 중첩된 정보 구조

- **step7-3d_tag_request_dtos.md** ✅
  - 배치 처리 Request
  - 복잡한 비즈니스 검증
  - 가중치 합계 검증

- **step7-3e_tag_response_dtos.md** ✅
  - 다층 구조 Response
  - 메타데이터 포함
  - 성능 지표 제공

### 3. 보안 및 성능 패턴
- **step7-1f_xss_security.md** ✅
  - XSS 방지 필터 적용
  - 입력 데이터 정제

- **step7-1g_rate_limiting.md** ✅
  - @RateLimited 구현
  - API별 차별화된 속도 제한

- **step7-2h_conditional_masking.md** ✅
  - 조건부 데이터 마스킹
  - @MaskingRule 활용

---

## 📝 7-4단계 구현 계획

### GymController 구현 예정
1. **GET /api/v1/gyms** - 암장 목록 조회 (위치, 검색)
2. **GET /api/v1/gyms/{id}** - 암장 상세 조회
3. **GET /api/v1/gyms/{id}/branches** - 지점 목록 조회
4. **GET /api/v1/gyms/nearby** - 근처 암장 검색
5. **POST /api/v1/gyms** - 암장 등록 (관리자)
6. **PUT /api/v1/gyms/{id}** - 암장 정보 수정 (관리자)
7. **DELETE /api/v1/gyms/{id}** - 암장 삭제 (관리자)
8. **GET /api/v1/gyms/{id}/statistics** - 암장 통계 조회

### RouteController 구현 예정
1. **GET /api/v1/routes** - 루트 목록 조회 (난이도, 태그별)
2. **GET /api/v1/routes/{id}** - 루트 상세 조회
3. **POST /api/v1/routes** - 루트 등록 (세터/관리자)
4. **PUT /api/v1/routes/{id}** - 루트 수정 (세터/관리자)
5. **DELETE /api/v1/routes/{id}** - 루트 삭제 (세터/관리자)
6. **GET /api/v1/routes/search** - 루트 검색 (복합 조건)
7. **POST /api/v1/routes/{id}/vote** - 난이도 투표
8. **POST /api/v1/routes/{id}/scrap** - 루트 스크랩
9. **GET /api/v1/routes/{id}/comments** - 루트 댓글 목록
10. **POST /api/v1/routes/{id}/comments** - 루트 댓글 작성

### RouteMediaController 구현 예정
1. **POST /api/v1/routes/{id}/images** - 이미지 업로드
2. **GET /api/v1/routes/{id}/images** - 이미지 목록 조회
3. **DELETE /api/v1/routes/{id}/images/{imageId}** - 이미지 삭제
4. **POST /api/v1/routes/{id}/videos** - 동영상 업로드
5. **GET /api/v1/routes/{id}/videos** - 동영상 목록 조회
6. **DELETE /api/v1/routes/{id}/videos/{videoId}** - 동영상 삭제

### DTO 설계 예정
**Gym System DTOs**:
- GymSearchRequest, GymCreateRequest, GymUpdateRequest
- GymResponse, GymListResponse, GymDetailResponse, GymStatsResponse

**Route System DTOs**:
- RouteSearchRequest, RouteCreateRequest, RouteUpdateRequest
- RouteResponse, RouteListResponse, RouteDetailResponse, RouteStatsResponse

**Route Media DTOs**:
- RouteImageUploadRequest, RouteVideoUploadRequest
- RouteImageResponse, RouteVideoResponse, MediaStatsResponse

---

## ⚙️ 기술적 고려사항

### 1. 성능 최적화
- **공간 쿼리 최적화** - PostGIS 활용 고려
- **이미지/동영상 처리** - 비동기 업로드, CDN 연동
- **검색 인덱스** - 복합 인덱스 전략
- **캐싱 전략** - Redis 1-4시간 TTL

### 2. 보안 요구사항
- **위치 정보 보호** - 정확한 좌표 마스킹
- **미디어 업로드 보안** - 파일 타입 검증, 바이러스 스캔
- **관리자 권한** - @PreAuthorize 세분화
- **Rate Limiting** - 미디어 업로드 특별 제한

### 3. API 설계 원칙
- **RESTful 설계** - 자원 중심 URL
- **한국 특화** - 주소, 좌표, 통화 단위
- **다국어 지원** - 암장명, 루트명 다국어
- **표준 응답** - ApiResponse 일관성

### 4. 특수 기능 구현
- **V등급/YDS 변환** - 난이도 시스템 호환
- **실시간 알림** - 새 루트 등록시 SSE
- **AI 추천** - 태그 기반 루트 추천
- **통계 대시보드** - 암장/루트 이용 현황

---

## 📊 구현 우선순위

### HIGH 우선순위
1. **GymController** - 암장 검색, 조회 핵심 기능
2. **RouteController** - 루트 CRUD, 검색 핵심 기능
3. **Gym & Route DTOs** - Request/Response 정의

### MEDIUM 우선순위
1. **RouteMediaController** - 이미지/동영상 업로드
2. **Route Media DTOs** - 미디어 관련 DTO
3. **통계 및 분석 API** - 대시보드 지원

### LOW 우선순위
1. **관리자 전용 API** - 고급 관리 기능
2. **실시간 알림** - SSE/WebSocket 연동
3. **AI 기반 추천** - 머신러닝 모델 연동

---

*참고 파일 분석 완료일: 2025-08-25*  
*총 참고 파일: 25개 (설계 4개 + 예외처리 1개 + 엔티티 7개 + Repository 8개 + Service 4개 + Controller패턴 8개)*  
*다음 단계: GymController 구현 시작*