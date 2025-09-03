# Step 1-1: 기본 스키마 구조 분석

> RoutePickr Database Schema 완전 분석 결과  
> 분석일: 2025-08-16  
> 총 테이블 수: 50개

---

## 📊 1. 전체 테이블 목록 (50개)

### 순서별 테이블 목록
1. `users` 2. `climbing_levels` 3. `gyms` 4. `gym_branches` 5. `walls`
6. `route_setters` 7. `routes` 8. `tags` 9. `route_tags` 10. `user_profile`
11. `user_preferred_tags` 12. `user_route_recommendations` 13. `user_climbs` 14. `climbing_shoes` 15. `user_climbing_shoes`
16. `board_categories` 17. `posts` 18. `comments` 19. `route_images` 20. `route_videos`
21. `route_comments` 22. `route_difficulty_votes` 23. `route_scraps` 24. `branch_images` 25. `gym_members`
26. `user_follows` 27. `post_images` 28. `post_videos` 29. `post_likes` 30. `post_bookmarks`
31. `post_route_tags` 32. `comment_likes` 33. `messages` 34. `message_route_tags` 35. `notifications`
36. `notices` 37. `banners` 38. `app_popups` 39. `api_tokens` 40. `api_logs`
41. `social_accounts` 42. `user_verifications` 43. `agreement_contents` 44. `user_agreements` 45. `external_api_configs`
46. `payment_records` 47. `payment_details` 48. `payment_items` 49. `payment_refunds` 50. `webhook_logs`

---

## 🏗️ 2. 도메인별 테이블 분류

### 👤 USER 도메인 (5개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `users` | 사용자 기본 정보 | email, password_hash, user_type |
| `user_profile` | 사용자 상세 프로필 | gender, height, level_id, branch_id |
| `user_verifications` | 본인인증 정보 | ci, di, phone_verified |
| `user_agreements` | 약관 동의 이력 | agreement_type, is_agreed |
| `social_accounts` | 소셜 로그인 연동 | provider, social_id, access_token |

### 🔐 AUTH 도메인 (2개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `api_tokens` | JWT 토큰 관리 | token, token_type, expires_at |
| `api_logs` | API 호출 로그 | endpoint, method, status_code |

### 🏢 GYM 도메인 (5개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `gyms` | 체육관 정보 | name, gym_admin_id |
| `gym_branches` | 지점 정보 | branch_name, address, latitude, longitude |
| `gym_members` | 직원 관리 | user_id, branch_id, role |
| `branch_images` | 지점 사진 | image_url, display_order |
| `walls` | 클라이밍 벽 | wall_name, set_date, wall_status |

### 🧗‍♀️ CLIMB 도메인 (3개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `climbing_levels` | 난이도 체계 | level_name (V0, V1, 5.10a 등) |
| `climbing_shoes` | 신발 정보 | brand, model |
| `user_climbing_shoes` | 사용자 보유 신발 | user_id, shoe_id |

### 🏷️ TAG 도메인 (4개) - **핵심**
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `tags` | 마스터 태그 | tag_name, tag_type, tag_category |
| `user_preferred_tags` | 사용자 선호도 | preference_level, skill_level |
| `route_tags` | 루트 특성 | relevance_score |
| `user_route_recommendations` | 추천 결과 캐시 | recommendation_score |

### 🧗‍♂️ ROUTE 도메인 (7개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `routes` | 루트 기본 정보 | name, level_id, color, angle |
| `route_setters` | 루트 세터 | name, setter_type, bio |
| `route_images` | 루트 사진 | image_url, is_main |
| `route_videos` | 루트 영상 | video_url, thumbnail_url |
| `route_comments` | 루트 댓글 | content, parent_id |
| `route_difficulty_votes` | 체감 난이도 투표 | difficulty_level |
| `route_scraps` | 즐겨찾기 | scrap_status |

### 📊 ACTIVITY 도메인 (2개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `user_climbs` | 완등 기록 | climb_date, notes |
| `user_follows` | 팔로우 관계 | follower_id, following_id |

### 📱 COMMUNITY 도메인 (9개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `board_categories` | 게시판 분류 | category_name |
| `posts` | 게시글 | title, content, view_count |
| `post_images` | 게시글 사진 | image_url, display_order |
| `post_videos` | 게시글 영상 | video_url, processing_status |
| `post_route_tags` | 게시글-루트 연결 | post_id, route_id |
| `post_likes` | 게시글 좋아요 | user_id, post_id |
| `post_bookmarks` | 게시글 북마크 | user_id, post_id |
| `comments` | 댓글 | content, parent_id, like_count |
| `comment_likes` | 댓글 좋아요 | user_id, comment_id |

### 💬 MESSAGE 도메인 (2개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `messages` | 개인 메시지 | sender_id, receiver_id, is_read |
| `message_route_tags` | 메시지 내 루트 공유 | message_id, route_id |

### 💳 PAYMENT 도메인 (4개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `payment_records` | 결제 기록 | amount, payment_status, transaction_id |
| `payment_details` | 결제 상세 | card_name, vbank_name |
| `payment_items` | 결제 항목 | item_name, item_amount |
| `payment_refunds` | 환불 처리 | refund_amount, refund_status |

### 🔔 NOTIFICATION 도메인 (4개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `notifications` | 푸시 알림 | type, title, is_read |
| `notices` | 공지사항 | notice_type, title, content |
| `banners` | 메인 배너 | image_url, display_order |
| `app_popups` | 이벤트 팝업 | start_date, end_date |

### ⚙️ SYSTEM 도메인 (3개)
| 테이블 | 역할 | 주요 필드 |
|--------|------|-----------|
| `agreement_contents` | 약관 내용 | agreement_type, version, content |
| `external_api_configs` | 외부 API 설정 | api_name, api_key |
| `webhook_logs` | 웹훅 로그 | provider, event_type, payload |

---

## 🔗 3. 테이블 간 관계 분석

### Primary Key & Foreign Key 매핑

#### 🏆 중심 허브 테이블: `users`
```sql
users (user_id) 관련 FK:
├── user_profile.user_id (1:1)
├── user_verifications.user_id (1:1)
├── gyms.gym_admin_id (1:N)
├── route_setters.created_by (1:N)
├── user_preferred_tags.user_id (1:N)
├── user_route_recommendations.user_id (1:N)
├── user_climbs.user_id (1:N)
├── user_follows.follower_id, following_id (N:M)
├── posts.user_id (1:N)
├── comments.user_id (1:N)
├── messages.sender_id, receiver_id (1:N)
├── notifications.user_id (1:N)
├── api_tokens.user_id (1:N)
└── payment_records.user_id (1:N)
```

#### 🏢 체육관 계층구조
```sql
gyms (gym_id)
└── gym_branches (branch_id)
    ├── walls (wall_id)
    │   └── routes (route_id)
    ├── branch_images
    └── gym_members
```

#### 🧗‍♂️ 루트 중심 관계
```sql
routes (route_id) 관련 FK:
├── route_tags.route_id (1:N)
├── route_images.route_id (1:N)
├── route_videos.route_id (1:N)
├── route_comments.route_id (1:N)
├── route_difficulty_votes.route_id (1:N)
├── route_scraps.route_id (1:N)
├── user_climbs.route_id (1:N)
├── user_route_recommendations.route_id (1:N)
├── post_route_tags.route_id (1:N)
└── message_route_tags.route_id (1:N)
```

### 관계 타입별 분류

#### 1:1 관계
- `users` ↔ `user_profile`
- `users` ↔ `user_verifications`

#### 1:N 관계 (주요)
- `gyms` → `gym_branches` → `walls` → `routes` (계층구조)
- `users` → `user_climbs`, `notifications`, `messages`
- `routes` → `route_images`, `route_videos`, `route_comments`
- `posts` → `post_images`, `post_videos`, `comments`
- `payment_records` → `payment_details`, `payment_items`

#### N:M 관계 (연결 테이블)
| 관계 | 연결 테이블 | 목적 |
|------|-------------|------|
| users ↔ tags | `user_preferred_tags` | 사용자 선호 태그 |
| routes ↔ tags | `route_tags` | 루트 특성 태그 |
| users ↔ routes | `route_scraps`, `user_climbs` | 즐겨찾기, 완등기록 |
| users ↔ users | `user_follows` | 팔로우 관계 |
| posts ↔ routes | `post_route_tags` | 게시글-루트 연결 |
| users ↔ posts | `post_likes`, `post_bookmarks` | 좋아요, 북마크 |

---

## 🌳 4. 계층형 구조 테이블

### Self-Referencing 테이블 (parent_id 필드)

#### `comments` 테이블
```sql
comments.parent_id → comments.comment_id
- 용도: 댓글에 대한 대댓글 구현
- 삭제 정책: ON DELETE SET NULL
- 최대 깊이: 제한 없음 (UI에서 2-3단계로 제한 권장)
```

#### `route_comments` 테이블
```sql
route_comments.parent_id → route_comments.comment_id  
- 용도: 루트 댓글에 대한 대댓글 구현
- 삭제 정책: ON DELETE CASCADE
- 최대 깊이: 제한 없음
```

### 지리적 계층구조
```sql
gyms → gym_branches → walls → routes
- 체육관 → 지점 → 벽 → 루트의 4단계 계층
- 각 단계별 CASCADE 삭제 정책
```

---

## 🇰🇷 5. 한국 특화 데이터 필드

### GPS 좌표 시스템
```sql
gym_branches.latitude    DECIMAL(10,8)  -- 위도 (한국 좌표계 지원)
gym_branches.longitude   DECIMAL(11,8)  -- 경도
-- 복합 인덱스: idx_gym_branches_location (latitude, longitude)
-- 용도: 근처 체육관 검색, 거리 계산
```

### 한글 텍스트 필드 (UTF8MB4)
| 테이블 | 필드 | 용도 |
|--------|------|------|
| `users` | user_name, nick_name | 실명, 닉네임 |
| `users` | address, detail_address | 주소, 상세주소 |
| `gym_branches` | branch_name, address | 지점명, 지점주소 |
| `routes` | name, description | 루트명, 설명 |
| `tags` | tag_name | 태그명 (볼더링, 크림핑 등) |
| `route_setters` | name, bio | 세터명, 소개 |
| `posts` | title, content | 게시글 제목, 내용 |

### 휴대폰 번호 체계
```sql
users.phone              VARCHAR(20)  -- 010-1234-5678 형식
users.emergency_contact  VARCHAR(20)  -- 비상연락처  
gym_branches.contact_phone VARCHAR(20) -- 지점 연락처
user_verifications.phone_verified TINYINT(1) -- 휴대폰 인증 여부
```

### 한국 본인인증 시스템
```sql
user_verifications.ci    VARCHAR(255)  -- 연계정보 (IPIN/휴대폰)
user_verifications.di    VARCHAR(255)  -- 중복가입확인정보
user_verifications.real_name VARCHAR(100) -- 실명
user_verifications.verification_method VARCHAR(50) -- 인증방법
```

### 한국 결제 시스템
```sql
-- 가상계좌 (한국 특화)
payment_details.vbank_name     VARCHAR(50)   -- 은행명
payment_details.vbank_number   VARCHAR(30)   -- 계좌번호  
payment_details.vbank_holder   VARCHAR(100)  -- 예금주
payment_details.vbank_date     DATETIME      -- 입금 마감일

-- 카드 결제
payment_details.card_name      VARCHAR(50)   -- 카드사명
payment_details.card_number    VARCHAR(30)   -- 카드번호 (마스킹)
payment_details.card_quota     INT           -- 할부 개월수 (한국 특화)
```

### 시간대 처리
```sql
-- 모든 TIMESTAMP 필드 
created_at, updated_at, last_login_at 등
- 기본값: CURRENT_TIMESTAMP
- 업데이트: ON UPDATE CURRENT_TIMESTAMP  
- 시간대: UTC 저장 → 애플리케이션에서 KST 변환

-- JSON 영업시간 (요일별)
gym_branches.business_hours JSON
-- 예시: {"monday": "09:00-22:00", "sunday": "10:00-20:00"}
```

---

## 📈 6. 기본 ERD 구조

### 핵심 엔티티 관계도
```
        [users] 1:1 [user_profile]
           |
           | 1:N
           ↓
        [gyms] 1:N [gym_branches] 1:N [walls] 1:N [routes]
           |                                      |
           | 1:N                                  | 1:N
           ↓                                      ↓
    [route_setters]                         [route_tags]
                                                 |
                                                 | N:1
                                                 ↓
    [user_preferred_tags] N:M [tags] ←──────────┘
           |
           | N:1
           ↓
        [users] ←──── [user_route_recommendations] ──→ [routes]
```

### 주요 인덱스 전략
```sql
-- 성능 최적화 인덱스
users: email (UNIQUE), nick_name (UNIQUE)
routes: (branch_id, level_id) -- 복합 인덱스
gym_branches: (latitude, longitude) -- 위치 검색
user_route_recommendations: (user_id, recommendation_score DESC)
tags: tag_type, tag_category, is_user_selectable
api_logs: request_time, status_code
payment_records: payment_status, payment_date
```

### CASCADE 정책 요약
- **ON DELETE CASCADE**: 연관 데이터 완전 삭제 (route_tags, user_climbs 등)
- **ON DELETE SET NULL**: 참조만 해제 (comments.parent_id, api_logs.user_id 등)
- **ON DELETE RESTRICT**: 삭제 방지 (기본 FK 제약조건)

---

## ✅ 분석 완료 체크리스트

- [x] 전체 50개 테이블 목록 추출 완료
- [x] 12개 도메인별 분류 완료  
- [x] Primary Key, Foreign Key 관계 매핑 완료
- [x] 1:1, 1:N, N:M 관계 식별 완료
- [x] 계층형 구조 (parent_id) 분석 완료
- [x] 한국 특화 데이터 필드 식별 완료
- [x] 기본 ERD 구조 정리 완료

---

**다음 단계**: Step 1-2 통합 태그 시스템 심층 분석  
**분석 파일**: `step1-2_tag_system_analysis.md`