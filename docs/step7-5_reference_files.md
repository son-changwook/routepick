# Step 7-5: 커뮤니티, 결제, 시스템 Controller + DTO 참고 파일 목록

## 📋 구현 목표
7-5단계에서 구현할 항목들:
1. **CommunityController** - 게시글, 댓글, 좋아요 관리 API
2. **PaymentController** - 결제, 환불, 웹훅 처리 API
3. **NotificationController** - 알림, 공지사항, 팝업 관리 API
4. **MessageController** - 개인 메시지, 루트 태깅 메시지 API
5. **SystemController** - 시스템 관리, 모니터링, 외부 API 관리
6. **Community DTOs** - 커뮤니티 관련 Request/Response DTO
7. **Payment DTOs** - 결제 관련 Request/Response DTO  
8. **System DTOs** - 시스템 관련 Request/Response DTO

---

## 👥 커뮤니티(Community) 시스템 참고 파일들

### 1. 설계 및 분석 문서
- **step1-1_schema_analysis.md** ✅
  - 커뮤니티 게시판 구조 분석
  - 계층형 댓글 시스템 설계
  - 사용자 상호작용 관계 매핑

- **step1-3a_architecture_social_recommendation.md** ✅
  - 소셜 기능 아키텍처
  - 커뮤니티 추천 알고리즘
  - 사용자 상호작용 최적화

### 2. 예외 처리
- **step3-2d_validation_system_exceptions.md** ✅
  - CommunityException, PostException, CommentException
  - ErrorCode 정의: COMMUNITY-001~099, POST-001~050
  - 커뮤니티 관련 모든 예외 처리 체계

### 3. 엔티티 설계
- **step4-4a1_community_core_entities.md** ✅
  - BoardCategory, Post 엔티티
  - 게시판 카테고리 관리
  - 게시글 상태 및 권한 관리

- **step4-4a2_community_interaction_entities.md** ✅
  - Comment, PostLike, PostBookmark, CommentLike
  - 계층형 댓글 구조 (3단계 depth)
  - 사용자 상호작용 기능

### 4. Repository 레이어
- **step5-4a1_community_board_repositories.md** ✅
  - BoardCategoryRepository, PostRepository
  - 게시판 관리 및 검색 최적화
  - 인기 게시글 알고리즘

- **step5-4a2_community_comment_repositories.md** ✅
  - CommentRepository
  - 계층형 댓글 조회 최적화
  - 댓글 트리 구조 관리

- **step5-4b_community_interaction_repositories.md** ✅
  - PostLikeRepository, PostBookmarkRepository, CommentLikeRepository
  - 좋아요/북마크 중복 방지
  - 상호작용 통계 쿼리

- **step5-4c1_post_image_repositories.md** ✅
  - PostImageRepository
  - 게시글 이미지 관리
  - CDN 연동 및 썸네일

- **step5-4c2_post_video_repositories.md** ✅
  - PostVideoRepository
  - 게시글 동영상 관리
  - 스트리밍 최적화

- **step5-4c3_post_route_tag_repositories.md** ✅
  - PostRouteTagRepository
  - 게시글-루트 태깅 연결
  - 루트 추천 알고리즘 연동

### 5. Service 레이어
- **step6-4a_post_service.md** ✅
  - PostService 완전 구현
  - 게시글 CRUD, XSS 방지, 미디어 처리
  - 인기도 기반 추천 알고리즘

- **step6-4b_comment_service.md** ✅
  - CommentService 구현
  - 계층형 댓글 관리 (3단계 depth)
  - 실시간 댓글 알림

- **step6-4c_interaction_service.md** ✅
  - InteractionService 구현
  - 좋아요/북마크 관리
  - Redis 기반 실시간 카운터

---

## 💳 결제(Payment) 시스템 참고 파일들

### 1. 설계 및 분석 문서
- **step1-3b_korean_business_jpa.md** ✅
  - 한국 PG사 연동 구조
  - 가상계좌, 카드결제, 간편결제 지원
  - 결제 보안 및 암호화

### 2. 예외 처리
- **step3-2c_tag_payment_exceptions.md** ✅
  - PaymentException, RefundException, WebhookException
  - ErrorCode 정의: PAYMENT-001~099, REFUND-001~050
  - 결제 관련 모든 예외 처리 체계

### 3. 엔티티 설계
- **step4-4b1_payment_entities.md** ✅
  - PaymentRecord, PaymentDetail, PaymentItem, PaymentRefund
  - 결제 기록 및 상세 정보 관리
  - 환불 처리 워크플로우

### 4. Repository 레이어
- **step5-4d_payment_repositories.md** ✅
  - PaymentRecordRepository, PaymentDetailRepository
  - PaymentItemRepository, PaymentRefundRepository
  - 결제 통계 및 정산 쿼리

### 5. Service 레이어
- **step6-5a_payment_service.md** ✅
  - PaymentService 구현
  - 한국 PG 연동 (토스, 카카오페이, 네이버페이)
  - SERIALIZABLE 트랜잭션 처리

- **step6-5b_payment_refund_service.md** ✅
  - PaymentRefundService 구현
  - 자동환불, 부분환불 지원
  - 승인 워크플로우 관리

- **step6-5c_webhook_service.md** ✅
  - WebhookService 구현
  - 웹훅 처리 및 서명 검증
  - 지수 백오프 재시도 로직

---

## 🔔 알림(Notification) 시스템 참고 파일들

### 1. 엔티티 설계
- **step4-4b2a_personal_notification_entities.md** ✅
  - Notification 엔티티
  - 개인 알림 관리
  - 알림 타입 및 상태 관리

- **step4-4b2b1_notice_banner_entities.md** ✅
  - Notice, Banner 엔티티
  - 공지사항 및 배너 관리
  - 노출 조건 및 스케줄링

- **step4-4b2b2_app_popup_entities.md** ✅
  - AppPopup 엔티티
  - 앱 팝업 관리
  - 타겟팅 및 A/B 테스트

### 2. Repository 레이어
- **step5-4e_notification_repositories.md** ✅
  - NotificationRepository, NoticeRepository
  - BannerRepository, AppPopupRepository
  - 알림 발송 최적화 쿼리

### 3. Service 레이어
- **step6-5d_notification_service.md** ✅
  - NotificationService 구현
  - 다채널 알림 (FCM, 이메일, 인앱)
  - 개인화 알림 및 배치 발송

---

## 💬 메시지(Message) 시스템 참고 파일들

### 1. Repository 레이어
- **step5-4f2_message_system_repositories.md** ✅
  - MessageRepository, MessageRouteTagRepository
  - 개인 메시지 관리
  - 루트 태깅 메시지 시스템

### 2. Service 레이어
- **step6-4d_message_service.md** ✅
  - MessageService 구현
  - 개인 메시지 관리
  - 루트 태깅 메시지, 대량 발송

---

## ⚙️ 시스템(System) 관리 참고 파일들

### 1. 설계 및 분석 문서
- **step1-3c_performance_security.md** ✅
  - 시스템 성능 모니터링
  - 보안 강화 방안
  - 로깅 및 감사 전략

### 2. 예외 처리
- **step3-2d_validation_system_exceptions.md** ✅
  - SystemException, ApiException, ValidationException
  - ErrorCode 정의: SYSTEM-001~099, API-001~050
  - 시스템 관련 예외 처리

### 3. 엔티티 설계
- **step4-4c1_system_management_entities.md** ✅
  - AgreementContent, ExternalApiConfig
  - 약관 관리 및 외부 API 설정
  - 시스템 설정 관리

- **step4-4c2_system_logging_entities.md** ✅
  - WebhookLog 엔티티
  - 시스템 로깅 및 감사
  - API 호출 추적

### 4. Repository 레이어
- **step5-4f3_system_management_repositories.md** ✅
  - AgreementContentRepository, ExternalApiConfigRepository
  - WebhookLogRepository
  - 시스템 통계 및 모니터링 쿼리

### 5. Service 레이어
- **step6-6a_api_log_service.md** ✅
  - ApiLogService 구현
  - API 로그 관리, 성능 모니터링
  - 에러 분석 및 통계

- **step6-6b_external_api_service.md** ✅
  - ExternalApiService 구현
  - 외부 API 관리, 상태 모니터링
  - API 키 암호화 및 순환

- **step6-6c_cache_service.md** ✅
  - CacheService 구현
  - Redis 캐시 관리
  - TTL 최적화, 스마트 워밍업

- **step6-6d_system_service.md** ✅
  - SystemService 구현
  - 시스템 모니터링, 헬스체크
  - 백업 관리 및 복구

---

## 🎨 Controller 패턴 참고 파일들

### 1. 기본 Controller 구조
- **step7-1a_auth_controller.md** ✅
  - RESTful API 설계 패턴
  - @PreAuthorize 보안 적용
  - @RateLimited 속도 제한
  - ApiResponse 표준 응답

- **step7-2a_user_controller.md** ✅
  - CRUD 패턴 구현
  - 페이징 처리 최적화
  - 검색 기능 구현

- **step7-3a_tag_controller.md** ✅
  - 복잡한 검색 API
  - 자동완성 기능
  - 관리자 권한 분리

- **step7-4a_gym_controller.md** ✅
  - 위치 기반 검색
  - GPS 좌표 마스킹
  - 보안 강화 패턴

### 2. DTO 설계 패턴
- **step7-1c_auth_request_dtos.md** ✅
  - Request DTO 검증 패턴
  - Bean Validation 활용
  - 한국 특화 검증

- **step7-1d_auth_response_dtos.md** ✅
  - Response DTO 설계
  - 민감정보 제외 정책
  - 중첩 구조 활용

- **step7-3d_tag_request_dtos.md** ✅
  - 배치 처리 Request
  - 복잡한 비즈니스 검증
  - 가중치 합계 검증

- **step7-4d_request_dtos.md** ✅
  - 한국 GPS 좌표 검증
  - V등급 시스템 지원
  - 클라이밍 기록 검증

### 3. 보안 및 성능 패턴
- **step7-4g_security_enhancements.md** ✅
  - 권한 검증 서비스
  - 데이터 마스킹 서비스
  - XSS 방지 필터

- **step7-4h_missing_service_integrations.md** ✅
  - Service 통합 패턴
  - 비동기 처리 최적화
  - 알림 서비스 연동

---

## 📝 7-5단계 구현 계획

### CommunityController 구현 예정
1. **POST /api/v1/community/posts** - 게시글 작성
2. **GET /api/v1/community/posts** - 게시글 목록 조회
3. **GET /api/v1/community/posts/{id}** - 게시글 상세 조회
4. **PUT /api/v1/community/posts/{id}** - 게시글 수정
5. **DELETE /api/v1/community/posts/{id}** - 게시글 삭제
6. **POST /api/v1/community/posts/{id}/comments** - 댓글 작성
7. **GET /api/v1/community/posts/{id}/comments** - 댓글 조회
8. **POST /api/v1/community/posts/{id}/like** - 게시글 좋아요
9. **POST /api/v1/community/posts/{id}/bookmark** - 게시글 북마크
10. **GET /api/v1/community/categories** - 카테고리 목록

### PaymentController 구현 예정
1. **POST /api/v1/payments** - 결제 요청
2. **GET /api/v1/payments/{id}** - 결제 상세 조회
3. **POST /api/v1/payments/{id}/cancel** - 결제 취소
4. **POST /api/v1/payments/refund** - 환불 요청
5. **GET /api/v1/payments/history** - 결제 내역 조회
6. **POST /api/v1/payments/webhook** - 웹훅 처리
7. **GET /api/v1/payments/methods** - 결제 수단 조회

### NotificationController 구현 예정
1. **GET /api/v1/notifications** - 알림 목록 조회
2. **PUT /api/v1/notifications/{id}/read** - 알림 읽음 처리
3. **DELETE /api/v1/notifications/{id}** - 알림 삭제
4. **PUT /api/v1/notifications/read-all** - 모든 알림 읽음
5. **GET /api/v1/notifications/settings** - 알림 설정 조회
6. **PUT /api/v1/notifications/settings** - 알림 설정 변경
7. **GET /api/v1/notices** - 공지사항 조회

### MessageController 구현 예정
1. **POST /api/v1/messages** - 메시지 발송
2. **GET /api/v1/messages** - 메시지 목록 조회
3. **GET /api/v1/messages/{id}** - 메시지 상세 조회
4. **PUT /api/v1/messages/{id}/read** - 메시지 읽음 처리
5. **DELETE /api/v1/messages/{id}** - 메시지 삭제
6. **POST /api/v1/messages/route-tag** - 루트 태깅 메시지

### SystemController 구현 예정
1. **GET /api/v1/system/health** - 시스템 상태 확인
2. **GET /api/v1/system/stats** - 시스템 통계 조회
3. **GET /api/v1/system/logs** - 시스템 로그 조회
4. **PUT /api/v1/system/cache/clear** - 캐시 초기화
5. **GET /api/v1/system/external-apis** - 외부 API 상태
6. **POST /api/v1/system/backup** - 백업 실행
7. **GET /api/v1/system/agreements** - 약관 관리

### DTO 설계 예정

**Community DTOs**:
- PostCreateRequest, PostUpdateRequest, CommentCreateRequest
- PostResponse, PostListResponse, CommentResponse, CategoryResponse

**Payment DTOs**:
- PaymentCreateRequest, RefundRequest, WebhookRequest
- PaymentResponse, PaymentHistoryResponse, RefundResponse

**Notification DTOs**:
- NotificationResponse, NotificationSettingsRequest
- NoticeResponse, BannerResponse

**Message DTOs**:
- MessageCreateRequest, RouteTagMessageRequest  
- MessageResponse, MessageListResponse

**System DTOs**:
- SystemStatsResponse, HealthCheckResponse, LogQueryRequest
- ExternalApiStatusResponse, BackupResponse

---

## ⚙️ 기술적 고려사항

### 1. 성능 최적화
- **Redis 캐싱** - 인기 게시글, 알림 설정 캐싱
- **배치 처리** - 대량 알림 발송, 통계 처리
- **페이징 최적화** - 커뮤니티 무한 스크롤
- **CDN 연동** - 미디어 파일 최적화

### 2. 보안 요구사항
- **결제 보안** - PCI DSS 준수, 카드 정보 암호화
- **개인정보 보호** - 메시지 암호화, 알림 설정 보안
- **XSS 방지** - 게시글/댓글 내용 정제
- **Rate Limiting** - API 남용 방지

### 3. API 설계 원칙
- **RESTful 설계** - 자원 중심 URL 구조
- **비동기 처리** - 알림 발송, 미디어 처리
- **웹훅 처리** - 결제 콜백, 외부 시스템 연동
- **실시간 기능** - WebSocket 연동 고려

### 4. 한국 특화 기능
- **PG사 연동** - 토스페이먼츠, 카카오페이, 네이버페이
- **가상계좌** - 은행별 가상계좌 발급
- **휴대폰 결제** - 통신사 소액결제
- **현금영수증** - 자동 발급 시스템

---

## 📊 구현 우선순위

### HIGH 우선순위
1. **CommunityController** - 핵심 소셜 기능
2. **PaymentController** - 수익화 필수 기능
3. **Community & Payment DTOs** - Request/Response 정의

### MEDIUM 우선순위
1. **NotificationController** - 사용자 경험 개선
2. **MessageController** - 개인 소통 기능
3. **Notification & Message DTOs** - 알림/메시지 DTO

### LOW 우선순위
1. **SystemController** - 관리자 전용 기능
2. **System DTOs** - 시스템 관리 DTO
3. **고급 분석 기능** - 통계 및 모니터링

---

*참고 파일 분석 완료일: 2025-08-25*  
*총 참고 파일: 32개 (설계 4개 + 예외처리 2개 + 엔티티 8개 + Repository 10개 + Service 8개 + Controller패턴 10개)*  
*다음 단계: CommunityController 구현 시작*