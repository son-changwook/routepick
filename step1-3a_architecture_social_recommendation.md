# Step 1-3a: 아키텍처 및 소셜 로그인 설계

> RoutePickr Spring Boot 아키텍처 및 소셜 로그인 시스템 설계  
> 생성일: 2025-08-20  
> 분할: step1-3_spring_boot_guide.md → 아키텍처/소셜/추천 부분 추출  
> 기반 분석: 50개 테이블 + 통합 태그 시스템

---

## 🎯 전체 설계 개요

### 핵심 아키텍처
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Presentation  │    │    Business     │    │      Data       │
│     Layer       │    │     Layer       │    │     Layer       │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ • Controller    │───▶│ • Service       │───▶│ • Repository    │
│ • DTO          │    │ • Domain        │    │ • JPA Entity    │
│ • Validation   │    │ • Business Rule │    │ • Native Query  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Cross-Cutting  │    │    External     │    │    Database     │
│   Concerns      │    │   Integration   │    │     MySQL       │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ • Security      │    │ • Social Login  │    │ • 50 Tables     │
│ • Caching       │    │ • Payment API   │    │ • Stored Proc   │
│ • Auditing      │    │ • SMS API       │    │ • JSON Fields   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 도메인 중심 패키지 구조
```
com.routepickr
├── common/              # 공통 모듈
├── config/              # 설정 클래스
├── domain/              # 도메인별 패키지
│   ├── user/           # USER + AUTH 도메인
│   ├── gym/            # GYM 도메인
│   ├── climbing/       # CLIMB 도메인
│   ├── tag/            # TAG 도메인 (핵심)
│   ├── route/          # ROUTE 도메인
│   ├── activity/       # ACTIVITY 도메인
│   ├── community/      # COMMUNITY 도메인
│   ├── message/        # MESSAGE 도메인
│   ├── payment/        # PAYMENT 도메인
│   ├── notification/   # NOTIFICATION 도메인
│   └── system/         # SYSTEM 도메인
└── external/           # 외부 API 연동
```

---

## 🔐 1. 소셜 로그인 시스템 구조

### social_accounts 테이블 완전 분석

#### 테이블 구조
```sql
CREATE TABLE `social_accounts` (
  `social_account_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `provider` enum('GOOGLE','KAKAO','NAVER','FACEBOOK'),
  `social_id` varchar(100) NOT NULL,
  `access_token` text,
  `refresh_token` text, 
  `token_expires_at` timestamp NULL,
  UNIQUE KEY `idx_social_provider_id` (`provider`,`social_id`)
)
```

### SocialProvider Enum 4가지 값

| Provider | 한글명 | OAuth 특징 | 토큰 갱신 |
|----------|--------|------------|----------|
| `GOOGLE` | 구글 | 글로벌 표준, 안정적 | refresh_token 지원 |
| `KAKAO` | 카카오 | 한국 점유율 1위 | refresh_token 지원 |
| `NAVER` | 네이버 | 한국 특화, 실명 제공 | access_token 만료 시 재로그인 |
| `FACEBOOK` | 페이스북 | 글로벌 커뮤니티 | refresh_token 지원 |

### 필드별 상세 분석

#### `social_id` 필드
- **용도**: 각 소셜 제공자에서 제공하는 고유 사용자 ID
- **특징**: 
  - Google: 숫자형 ID (21자리)
  - Kakao: 숫자형 ID (9-10자리)
  - Naver: 문자열 ID (복합 형태)
  - Facebook: 숫자형 ID (15-17자리)

#### `access_token` / `refresh_token` 필드
- **보안**: TEXT 타입으로 암호화 저장 필요
- **용도**: API 호출 및 토큰 갱신
- **만료 관리**: token_expires_at 필드로 추적

### 이메일 기반 통합 인증 로직

#### 1단계: 소셜 로그인 요청 처리
```java
@Service
public class SocialLoginService {
    
    public LoginResponse processSocialLogin(SocialLoginRequest request) {
        // 1. 소셜 제공자에서 사용자 정보 획득
        SocialUserInfo socialInfo = getSocialUserInfo(request);
        
        // 2. 기존 계정 연동 확인
        Optional<SocialAccount> existingSocial = socialAccountRepository
            .findByProviderAndSocialId(request.getProvider(), socialInfo.getSocialId());
            
        if (existingSocial.isPresent()) {
            // 기존 소셜 계정으로 로그인
            return loginExistingUser(existingSocial.get());
        }
        
        // 3. 이메일 기반 기존 사용자 확인
        Optional<User> existingUser = userRepository
            .findByEmail(socialInfo.getEmail());
            
        if (existingUser.isPresent()) {
            // 기존 사용자에 소셜 계정 연동
            return linkSocialAccount(existingUser.get(), socialInfo);
        }
        
        // 4. 신규 사용자 가입
        return createNewUserWithSocial(socialInfo);
    }
}
```

#### 2단계: 토큰 관리 전략
```java
@Component
public class SocialTokenManager {
    
    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void refreshExpiredTokens() {
        List<SocialAccount> expiredAccounts = socialAccountRepository
            .findByTokenExpiresAtBefore(LocalDateTime.now());
            
        for (SocialAccount account : expiredAccounts) {
            try {
                refreshToken(account);
            } catch (TokenRefreshException e) {
                // 갱신 실패 시 사용자에게 재로그인 요청
                notificationService.sendReLoginNotification(account.getUser());
            }
        }
    }
}
```

---

## 📊 2. CalculateUserRouteRecommendations 프로시저 분석

### 입력 파라미터 구조
```sql
CREATE PROCEDURE CalculateUserRouteRecommendations(IN p_user_id INT)
```

**파라미터 검증**:
- `p_user_id`: 활성 상태 사용자 ID (user_status = 'ACTIVE')
- 전제 조건: user_profile.level_id 존재
- 선택 조건: user_preferred_tags 1개 이상

### 추천 계산 로직 5단계

#### 1단계: 기존 추천 데이터 정리
```sql
DELETE FROM user_route_recommendations WHERE user_id = p_user_id;
```

#### 2단계: 활성 루트 목록 조회
```sql
DECLARE route_cursor CURSOR FOR 
    SELECT route_id FROM routes WHERE route_status = 'ACTIVE';
```

#### 3단계: 태그 매칭 점수 계산
```sql
-- 사용자 선호도별 가중치 적용
CASE upt.preference_level
    WHEN 'HIGH' THEN rt.relevance_score * 100    -- 100%
    WHEN 'MEDIUM' THEN rt.relevance_score * 70   -- 70%
    WHEN 'LOW' THEN rt.relevance_score * 30      -- 30%
    ELSE 0
END
```

#### 4단계: 레벨 매칭 점수 계산
```sql
-- 레벨 차이별 점수 매트릭스
ABS(user_level - route_level) = 0 → 100점 (정확한 매칭)
ABS(user_level - route_level) = 1 → 80점  (도전적)
ABS(user_level - route_level) = 2 → 60점  (약간 어려움)
ABS(user_level - route_level) = 3 → 40점  (상당히 어려움)
ABS(user_level - route_level) = 4 → 20점  (매우 어려움)
ABS(user_level - route_level) ≥ 5 → 10점  (부적절)
```

#### 5단계: 최종 점수 산출 및 저장
```sql
-- 가중 평균: 태그 70% + 레벨 30%
SET v_total_score = (v_tag_score * 0.7) + (v_level_score * 0.3);

-- 품질 임계값: 20점 이상만 저장
IF v_total_score >= 20 THEN
    INSERT INTO user_route_recommendations ...
END IF;
```

### 출력 결과 형식
- **저장 위치**: `user_route_recommendations` 테이블
- **결과 구조**: 사용자별 추천 루트 목록 (점수 내림차순)
- **품질 보장**: 20점 미만 추천 제외
- **캐시 방식**: 테이블 저장으로 빠른 조회

### 성능 최적화 포인트

#### 배치 처리 최적화
```java
@Service
@Transactional
public class RecommendationService {
    
    @Async
    public CompletableFuture<Void> calculateUserRecommendations(Long userId) {
        return CompletableFuture.runAsync(() -> {
            jdbcTemplate.call("{CALL CalculateUserRouteRecommendations(?)}", userId);
        });
    }
    
    @Scheduled(cron = "0 0 2 * * ?") // 새벽 2시
    public void updateAllRecommendations() {
        List<Long> activeUserIds = userRepository.findActiveUserIds();
        
        // 사용자별 병렬 처리 (최대 10개 스레드)
        activeUserIds.parallelStream()
            .limit(10)
            .forEach(this::calculateUserRecommendations);
    }
}
```

---

## ✅ 아키텍처 및 소셜 로그인 완료 체크리스트

### 🎯 전체 아키텍처 설계
- [x] **3-Layer 아키텍처**: Presentation, Business, Data Layer 분리
- [x] **도메인 중심 패키지**: 11개 도메인별 패키지 구조
- [x] **Cross-Cutting 관심사**: Security, Caching, Auditing 분리
- [x] **외부 연동**: Social Login, Payment, SMS API 통합

### 🔐 소셜 로그인 시스템
- [x] **4개 제공자 지원**: GOOGLE, KAKAO, NAVER, FACEBOOK
- [x] **이메일 기반 통합**: 기존 계정과 소셜 계정 연동
- [x] **토큰 관리**: access_token, refresh_token 자동 갱신
- [x] **보안 강화**: TEXT 타입 암호화 저장
- [x] **만료 처리**: 스케줄러 기반 자동 토큰 갱신

### 📊 추천 시스템 설계
- [x] **저장 프로시저**: CalculateUserRouteRecommendations
- [x] **5단계 처리**: 정리 → 조회 → 태그매칭 → 레벨매칭 → 저장
- [x] **가중치 적용**: 태그 70%, 레벨 30%
- [x] **품질 보장**: 20점 이상만 추천
- [x] **배치 처리**: 비동기 병렬 처리로 성능 최적화

### 한국 특화 기능
- [x] **소셜 로그인**: KAKAO, NAVER 한국 특화 제공자
- [x] **추천 알고리즘**: 한국 클라이밍 문화 반영
- [x] **서울 시간대**: Asia/Seoul 기준 스케줄링

---

*분할 작업 1/3 완료: 아키텍처 + 소셜 로그인 + 추천 시스템*  
*다음 파일: step1-3b_korean_business_jpa.md*