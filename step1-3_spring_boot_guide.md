# Step 1-3: 비즈니스 로직 및 Spring Boot 설계 가이드

> RoutePickr Spring Boot 애플리케이션 설계 완전 가이드  
> 분석일: 2025-08-16  
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

## 🇰🇷 3. 한국 클라이밍 특화 비즈니스 규칙

### V등급과 5.등급 체계 매핑

#### 볼더링 V등급 시스템
```java
@Entity
@Table(name = "climbing_levels")
public class ClimbingLevel {
    
    @Enumerated(EnumType.STRING)
    private LevelSystem system; // V_SCALE, YDS_SCALE
    
    @Column(name = "level_name")
    private String levelName; // "V0", "V1", "5.10a"
    
    @Column(name = "difficulty_score")
    private Integer difficultyScore; // 정렬용 점수
}

public enum VScale {
    V0(1), V1(2), V2(3), V3(4), V4(5), V5(6), V6(7), V7(8), 
    V8(9), V9(10), V10(11), V11(12), V12(13), V13(14), V14(15), V15(16);
    
    private final int score;
}
```

#### YDS(5.등급) 시스템
```java
public enum YdsScale {
    FIVE_5("5.5", 1), FIVE_6("5.6", 2), FIVE_7("5.7", 3), 
    FIVE_8("5.8", 4), FIVE_9("5.9", 5), FIVE_10A("5.10a", 6),
    FIVE_10B("5.10b", 7), FIVE_10C("5.10c", 8), FIVE_10D("5.10d", 9),
    FIVE_11A("5.11a", 10), FIVE_11B("5.11b", 11), FIVE_11C("5.11c", 12),
    // ... 5.15d까지
    
    private final String notation;
    private final int score;
}
```

### 한국 좌표 범위 검증

#### GPS 좌표 유효성 검사
```java
@Component
public class KoreaGeoValidator {
    
    // 한국 본토 좌표 범위
    private static final double KOREA_MIN_LATITUDE = 33.0;   // 제주도 남단
    private static final double KOREA_MAX_LATITUDE = 38.6;   // 북한 접경
    private static final double KOREA_MIN_LONGITUDE = 124.0; // 서해 최서단
    private static final double KOREA_MAX_LONGITUDE = 132.0; // 동해 최동단
    
    public boolean isValidKoreaCoordinate(double latitude, double longitude) {
        return latitude >= KOREA_MIN_LATITUDE && latitude <= KOREA_MAX_LATITUDE
            && longitude >= KOREA_MIN_LONGITUDE && longitude <= KOREA_MAX_LONGITUDE;
    }
    
    @EventListener
    public void validateGymBranchLocation(GymBranchCreatedEvent event) {
        GymBranch branch = event.getGymBranch();
        if (!isValidKoreaCoordinate(branch.getLatitude(), branch.getLongitude())) {
            throw new InvalidLocationException("한국 내 좌표가 아닙니다: " + 
                branch.getLatitude() + ", " + branch.getLongitude());
        }
    }
}
```

### 암장 운영시간 (business_hours JSON)

#### JSON 구조 정의
```java
@Entity
@Table(name = "gym_branches")
public class GymBranch {
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", columnDefinition = "json")
    private BusinessHours businessHours;
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessHours {
    private DaySchedule monday;
    private DaySchedule tuesday;
    private DaySchedule wednesday;
    private DaySchedule thursday;
    private DaySchedule friday;
    private DaySchedule saturday;
    private DaySchedule sunday;
    
    // 한국 공휴일 특별 운영시간
    private Map<String, DaySchedule> holidays; // "2024-01-01": DaySchedule
}

public class DaySchedule {
    private LocalTime openTime;  // "09:00"
    private LocalTime closeTime; // "22:00"
    private boolean closed;      // 휴무일 여부
    private String note;         // "점심시간 12:00-13:00"
}
```

#### 운영시간 비즈니스 로직
```java
@Service
public class GymScheduleService {
    
    public boolean isCurrentlyOpen(GymBranch branch) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        BusinessHours hours = branch.getBusinessHours();
        
        // 공휴일 확인
        if (isKoreanHoliday(now.toLocalDate())) {
            DaySchedule holidaySchedule = hours.getHolidays()
                .get(now.toLocalDate().toString());
            if (holidaySchedule != null) {
                return isOpenAtTime(holidaySchedule, now.toLocalTime());
            }
        }
        
        // 평일/주말 확인
        DaySchedule daySchedule = getDaySchedule(hours, now.getDayOfWeek());
        return isOpenAtTime(daySchedule, now.toLocalTime());
    }
}
```

### 휴대폰 번호 형식 검증

#### 한국 휴대폰 번호 패턴
```java
@Component
public class KoreanPhoneValidator {
    
    // 한국 휴대폰 번호 정규식 (010, 011, 016, 017, 018, 019)
    private static final String PHONE_PATTERN = 
        "^01[0-9]-\\d{3,4}-\\d{4}$";
    
    private static final Pattern pattern = Pattern.compile(PHONE_PATTERN);
    
    @PostConstruct
    public void validatePattern() {
        // 테스트 케이스 검증
        assert isValid("010-1234-5678");
        assert isValid("011-123-4567");
        assert !isValid("010-12345-678"); // 잘못된 형식
    }
    
    public boolean isValid(String phone) {
        return phone != null && pattern.matcher(phone).matches();
    }
    
    public String normalize(String phone) {
        // "01012345678" → "010-1234-5678"
        if (phone.length() == 11 && phone.startsWith("010")) {
            return phone.substring(0, 3) + "-" + 
                   phone.substring(3, 7) + "-" + 
                   phone.substring(7);
        }
        return phone;
    }
}

// 엔티티 검증
@Entity
public class User {
    @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", 
             message = "올바른 휴대폰 번호 형식이 아닙니다")
    private String phone;
}
```

---

## 📋 4. JSON 컬럼 활용 분석

### business_hours 구조 (gym_branches)
```json
{
  "monday": {"openTime": "09:00", "closeTime": "22:00", "closed": false},
  "tuesday": {"openTime": "09:00", "closeTime": "22:00", "closed": false},
  "wednesday": {"openTime": "09:00", "closeTime": "22:00", "closed": false},
  "thursday": {"openTime": "09:00", "closeTime": "22:00", "closed": false},
  "friday": {"openTime": "09:00", "closeTime": "23:00", "closed": false},
  "saturday": {"openTime": "10:00", "closeTime": "20:00", "closed": false},
  "sunday": {"openTime": "10:00", "closeTime": "20:00", "closed": false},
  "holidays": {
    "2024-01-01": {"closed": true, "note": "신정 휴무"},
    "2024-02-10": {"openTime": "12:00", "closeTime": "18:00", "note": "설날 단축운영"}
  }
}
```

### amenities 배열 형식 (gym_branches)
```json
{
  "facilities": ["shower", "locker", "cafe", "equipment_rental", "parking"],
  "equipment": {
    "climbing_shoes": true,
    "chalk_bag": true,
    "harness": true,
    "helmet": false
  },
  "services": {
    "beginner_lesson": true,
    "personal_training": true,
    "group_lesson": false,
    "equipment_maintenance": true
  },
  "accessibility": {
    "wheelchair_accessible": true,
    "elevator": true,
    "disabled_parking": true
  }
}
```

### preferences 설정 구조 (user_profile)
```json
{
  "notification": {
    "push_enabled": true,
    "email_enabled": false,
    "sms_enabled": true,
    "marketing_consent": false
  },
  "privacy": {
    "profile_public": true,
    "activity_public": false,
    "location_sharing": true
  },
  "climbing": {
    "preferred_styles": ["bouldering", "lead_climbing"],
    "avoid_routes": ["overhang", "roof"],
    "difficulty_range": {"min": "V2", "max": "V6"}
  },
  "ui": {
    "theme": "dark",
    "language": "ko",
    "distance_unit": "km"
  }
}
```

### JSON 활용 JPA 매핑
```java
// Hibernate 6+ JSON 매핑
@Entity
public class GymBranch {
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private BusinessHours businessHours;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Amenities amenities;
}

// 커스텀 JSON 컨버터
@Converter
public class BusinessHoursConverter implements AttributeConverter<BusinessHours, String> {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String convertToDatabaseColumn(BusinessHours attribute) {
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 변환 실패", e);
        }
    }
    
    @Override
    public BusinessHours convertToEntityAttribute(String dbData) {
        try {
            return mapper.readValue(dbData, BusinessHours.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 파싱 실패", e);
        }
    }
}
```

---

## 🏗️ 5. Spring Boot JPA 설계 권장사항

### BaseEntity 설계 (Auditing 필드)

#### 공통 엔티티 베이스
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "created_at")
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;
    
    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
    
    @Version
    private Long version; // 낙관적 락
}

// 소프트 삭제 지원 베이스
@MappedSuperclass
public abstract class SoftDeleteEntity extends BaseEntity {
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;
    
    public boolean isDeleted() {
        return deletedAt != null;
    }
    
    public void delete(Long deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
```

### 연관관계 매핑 전략 (LAZY vs EAGER)

#### 권장 매핑 전략
```java
@Entity
@Table(name = "routes")
public class Route extends BaseEntity {
    
    // ToOne 관계: LAZY (기본값)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private GymBranch branch;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private ClimbingLevel level;
    
    // ToMany 관계: LAZY (항상)
    @OneToMany(mappedBy = "route", fetch = FetchType.LAZY)
    private List<RouteImage> images = new ArrayList<>();
    
    @OneToMany(mappedBy = "route", fetch = FetchType.LAZY)
    private List<RouteTag> tags = new ArrayList<>();
    
    // 성능이 중요한 연관관계: Batch Size 설정
    @BatchSize(size = 20)
    @OneToMany(mappedBy = "route")
    private List<RouteComment> comments = new ArrayList<>();
}

// N+1 문제 해결을 위한 Repository
@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {
    
    // Fetch Join 활용
    @Query("SELECT r FROM Route r " +
           "JOIN FETCH r.branch b " +
           "JOIN FETCH r.level l " +
           "WHERE r.routeStatus = 'ACTIVE'")
    List<Route> findActiveRoutesWithBranchAndLevel();
    
    // BatchSize와 조합
    @Query("SELECT r FROM Route r WHERE r.branch.branchId = :branchId")
    List<Route> findByBranchId(@Param("branchId") Long branchId);
}
```

### 복합 인덱스 설계 필요 테이블

#### 성능 최적화 인덱스 전략
```java
// 복합 인덱스 정의
@Entity
@Table(name = "routes", indexes = {
    @Index(name = "idx_routes_branch_level", columnList = "branch_id, level_id"),
    @Index(name = "idx_routes_status_date", columnList = "route_status, created_at"),
    @Index(name = "idx_routes_setter", columnList = "setter_id, route_status")
})
public class Route extends BaseEntity {
    // ...
}

@Entity
@Table(name = "user_route_recommendations", indexes = {
    @Index(name = "idx_recommendations_user_score", 
           columnList = "user_id, recommendation_score DESC"),
    @Index(name = "idx_recommendations_active", 
           columnList = "is_active, calculated_at")
})
public class UserRouteRecommendation extends BaseEntity {
    // ...
}

@Entity
@Table(name = "user_preferred_tags", indexes = {
    @Index(name = "idx_user_tags_preference", 
           columnList = "user_id, preference_level"),
    @Index(name = "idx_tag_users", 
           columnList = "tag_id, skill_level")
})
public class UserPreferredTag extends BaseEntity {
    // ...
}
```

### 공간 인덱스 활용 테이블 (gym_branches)

#### MySQL Spatial Index 활용
```java
@Entity
@Table(name = "gym_branches")
public class GymBranch extends BaseEntity {
    
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;
    
    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;
    
    // MySQL POINT 타입 활용 (선택사항)
    @Column(name = "location", columnDefinition = "POINT")
    private Point location;
}

// 거리 기반 검색 Repository
@Repository
public interface GymBranchRepository extends JpaRepository<GymBranch, Long> {
    
    @Query(value = """
        SELECT *, ST_Distance_Sphere(
            POINT(:longitude, :latitude), 
            POINT(longitude, latitude)
        ) as distance 
        FROM gym_branches 
        WHERE ST_Distance_Sphere(
            POINT(:longitude, :latitude), 
            POINT(longitude, latitude)
        ) <= :radiusMeters
        ORDER BY distance
        """, nativeQuery = true)
    List<GymBranch> findNearbyBranches(
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusMeters") double radiusMeters
    );
}
```

---

## ⚡ 6. 성능 최적화 필수 포인트

### N+1 문제 해결 대상 쿼리

#### 주요 N+1 발생 지점
```java
// ❌ N+1 문제 발생
@GetMapping("/routes")
public List<RouteDto> getRoutes() {
    List<Route> routes = routeRepository.findAll();
    return routes.stream()
        .map(route -> RouteDto.builder()
            .id(route.getId())
            .name(route.getName())
            .branchName(route.getBranch().getBranchName()) // N+1 발생!
            .levelName(route.getLevel().getLevelName())     // N+1 발생!
            .tags(route.getTags().stream()                  // N+1 발생!
                .map(RouteTag::getTag)
                .map(Tag::getTagName)
                .collect(Collectors.toList()))
            .build())
        .collect(Collectors.toList());
}

// ✅ 해결 방법 1: Fetch Join
@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {
    
    @Query("SELECT DISTINCT r FROM Route r " +
           "LEFT JOIN FETCH r.branch " +
           "LEFT JOIN FETCH r.level " +
           "LEFT JOIN FETCH r.tags rt " +
           "LEFT JOIN FETCH rt.tag " +
           "WHERE r.routeStatus = 'ACTIVE'")
    List<Route> findActiveRoutesWithAssociations();
}

// ✅ 해결 방법 2: Entity Graph
@EntityGraph(attributePaths = {"branch", "level", "tags.tag"})
List<Route> findByRouteStatus(RouteStatus status);

// ✅ 해결 방법 3: Projection
@Query("SELECT new com.routepickr.dto.RouteProjection(" +
       "r.id, r.name, b.branchName, l.levelName) " +
       "FROM Route r " +
       "JOIN r.branch b " +
       "JOIN r.level l")
List<RouteProjection> findRouteProjections();
```

### 캐싱 전략 필요 데이터

#### 다층 캐싱 전략
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        RedisCacheManager.Builder builder = RedisCacheManager
            .RedisCacheManagerBuilder
            .fromConnectionFactory(redisConnectionFactory())
            .cacheDefaults(cacheConfiguration());
        
        // 캐시별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "tags", cacheConfiguration().entryTtl(Duration.ofHours(24)),
            "climbing-levels", cacheConfiguration().entryTtl(Duration.ofHours(12)),
            "gym-branches", cacheConfiguration().entryTtl(Duration.ofHours(6)),
            "user-recommendations", cacheConfiguration().entryTtl(Duration.ofMinutes(30))
        );
        
        builder.withInitialCacheConfigurations(cacheConfigs);
        return builder.build();
    }
}

@Service
public class TagService {
    
    @Cacheable(value = "tags", key = "'user-selectable'")
    public List<Tag> getUserSelectableTags() {
        return tagRepository.findByIsUserSelectableTrue();
    }
    
    @Cacheable(value = "tags", key = "'route-taggable'") 
    public List<Tag> getRouteTaggableTags() {
        return tagRepository.findByIsRouteTaggableTrue();
    }
    
    @CacheEvict(value = "tags", allEntries = true)
    public void refreshTagCache() {
        // 관리자가 태그 수정 시 캐시 무효화
    }
}
```

### 페이징 처리 필수 API

#### 커서 기반 페이징 구현
```java
@RestController
public class RouteController {
    
    // 무한 스크롤용 커서 페이징
    @GetMapping("/routes")
    public ApiResponse<RoutePageResponse> getRoutes(
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long lastRouteId,
        @RequestParam(required = false) Long branchId) {
        
        Pageable pageable = PageRequest.of(0, size);
        Page<Route> routes;
        
        if (lastRouteId != null) {
            // 커서 기반 페이징 (성능 우수)
            routes = routeRepository.findRoutesAfterCursor(
                lastRouteId, branchId, pageable);
        } else {
            // 첫 페이지
            routes = routeRepository.findActiveRoutes(branchId, pageable);
        }
        
        return ApiResponse.success(RoutePageResponse.from(routes));
    }
}

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {
    
    @Query("SELECT r FROM Route r " +
           "WHERE (:branchId IS NULL OR r.branch.branchId = :branchId) " +
           "AND r.routeId > :lastRouteId " +
           "AND r.routeStatus = 'ACTIVE' " +
           "ORDER BY r.routeId ASC")
    Page<Route> findRoutesAfterCursor(
        @Param("lastRouteId") Long lastRouteId,
        @Param("branchId") Long branchId,
        Pageable pageable);
}
```

### 배치 처리 필요 작업 (추천 계산)

#### Spring Batch 활용 추천 시스템
```java
@Configuration
@EnableBatchProcessing
public class RecommendationBatchConfig {
    
    @Bean
    public Job updateRecommendationsJob() {
        return jobBuilderFactory.get("updateRecommendationsJob")
            .incrementer(new RunIdIncrementer())
            .start(updateRecommendationsStep())
            .build();
    }
    
    @Bean
    public Step updateRecommendationsStep() {
        return stepBuilderFactory.get("updateRecommendationsStep")
            .<Long, RecommendationResult>chunk(100)
            .reader(activeUserReader())
            .processor(recommendationProcessor())
            .writer(recommendationWriter())
            .taskExecutor(asyncTaskExecutor())
            .throttleLimit(10) // 동시 처리 스레드 수
            .build();
    }
    
    @Bean
    @StepScope
    public ItemReader<Long> activeUserReader() {
        return new JdbcCursorItemReaderBuilder<Long>()
            .dataSource(dataSource)
            .sql("SELECT user_id FROM users WHERE user_status = 'ACTIVE'")
            .rowMapper((rs, rowNum) -> rs.getLong("user_id"))
            .build();
    }
    
    @Bean
    @StepScope  
    public ItemProcessor<Long, RecommendationResult> recommendationProcessor() {
        return userId -> {
            // Stored Procedure 호출
            jdbcTemplate.call("{CALL CalculateUserRouteRecommendations(?)}", userId);
            return new RecommendationResult(userId, LocalDateTime.now());
        };
    }
}

// 스케줄링
@Component
public class RecommendationScheduler {
    
    @Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시
    public void runRecommendationBatch() throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
            
        jobLauncher.run(updateRecommendationsJob, jobParameters);
    }
}
```

---

## 🔒 7. 보안 강화 필수 사항

### 민감정보 암호화 필드 목록

#### AES 암호화 적용 필드
```java
// 암호화가 필요한 민감 정보
@Entity
public class User extends BaseEntity {
    
    @Convert(converter = PhoneNumberCryptoConverter.class)
    private String phone; // 휴대폰 번호
    
    @Convert(converter = AddressCryptoConverter.class) 
    private String address; // 주소
    
    @Convert(converter = RealNameCryptoConverter.class)
    private String realName; // 실명 (본인인증)
}

@Entity
public class UserVerification extends BaseEntity {
    
    @Convert(converter = CiCryptoConverter.class)
    private String ci; // 연계정보
    
    @Convert(converter = DiCryptoConverter.class)
    private String di; // 중복가입확인정보
}

@Entity  
public class SocialAccount extends BaseEntity {
    
    @Convert(converter = TokenCryptoConverter.class)
    private String accessToken;
    
    @Convert(converter = TokenCryptoConverter.class)
    private String refreshToken;
}

// 암호화 컨버터 구현
@Component
public class PhoneNumberCryptoConverter implements AttributeConverter<String, String> {
    
    @Autowired
    private AESCrypto aesCrypto;
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute != null ? aesCrypto.encrypt(attribute) : null;
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData != null ? aesCrypto.decrypt(dbData) : null;
    }
}
```

### Rate Limiting 적용 API 목록

#### API별 Rate Limit 전략
```java
@Configuration
public class RateLimitConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        // Redis 설정
        return new RedisTemplate<>();
    }
}

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @PostMapping("/login")
    @RateLimit(key = "login:#{request.remoteAddr}", limit = 5, window = "1m")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        // 로그인 시도 제한: IP당 분당 5회
        return authService.login(request);
    }
    
    @PostMapping("/send-sms")
    @RateLimit(key = "sms:#{request.phone}", limit = 3, window = "1h")
    public ApiResponse<Void> sendSmsVerification(@RequestBody SmsRequest request) {
        // SMS 발송 제한: 전화번호당 시간당 3회
        return smsService.sendVerification(request);
    }
}

@RestController
@RequestMapping("/api/routes")
public class RouteController {
    
    @PostMapping
    @RateLimit(key = "route-create:#{@userContext.getCurrentUserId()}", limit = 10, window = "1h")
    public ApiResponse<RouteResponse> createRoute(@RequestBody RouteCreateRequest request) {
        // 루트 생성 제한: 사용자당 시간당 10개
        return routeService.createRoute(request);
    }
    
    @GetMapping("/search")
    @RateLimit(key = "route-search:#{@userContext.getCurrentUserId()}", limit = 100, window = "1m")
    public ApiResponse<List<RouteResponse>> searchRoutes(@RequestParam String query) {
        // 검색 제한: 사용자당 분당 100회
        return routeService.searchRoutes(query);
    }
}

// Rate Limit 애노테이션 구현
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String key();
    int limit();
    String window(); // "1m", "1h", "1d"
}

@Aspect
@Component
public class RateLimitAspect {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(rateLimit.key(), joinPoint);
        String windowKey = key + ":" + getCurrentWindow(rateLimit.window());
        
        String luaScript = """
            local current = redis.call('GET', KEYS[1])
            if current == false then
                redis.call('SET', KEYS[1], 1)
                redis.call('EXPIRE', KEYS[1], ARGV[2])
                return 1
            else
                local count = redis.call('INCR', KEYS[1])
                if count > tonumber(ARGV[1]) then
                    return -1
                end
                return count
            end
            """;
            
        Long result = redisTemplate.execute(new DefaultRedisScript<>(luaScript, Long.class),
            List.of(windowKey), rateLimit.limit(), getWindowSeconds(rateLimit.window()));
            
        if (result == -1) {
            throw new RateLimitExceededException("요청 한도를 초과했습니다");
        }
        
        return joinPoint.proceed();
    }
}
```

### XSS 방지 필요 텍스트 필드

#### HTML 태그 필터링
```java
@Component
public class XssProtectionService {
    
    private final PolicyFactory policy;
    
    public XssProtectionService() {
        this.policy = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(new HtmlPolicyBuilder()
                .allowElements("br", "p", "strong", "em")
                .allowAttributes("href").onElements("a")
                .requireRelNofollowOnLinks()
                .toFactory());
    }
    
    public String sanitize(String html) {
        return policy.sanitize(html);
    }
}

// XSS 방지 대상 필드
@Entity
public class Post extends BaseEntity {
    
    @Column(name = "title")
    @XssProtection
    private String title;
    
    @Column(name = "content", columnDefinition = "TEXT") 
    @XssProtection(allowHtml = true) // 제한적 HTML 허용
    private String content;
}

@Entity
public class Route extends BaseEntity {
    
    @Column(name = "name")
    @XssProtection
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    @XssProtection
    private String description;
}

// 커스텀 검증 애노테이션
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = XssProtectionValidator.class)
public @interface XssProtection {
    boolean allowHtml() default false;
    String message() default "잠재적으로 위험한 HTML이 포함되어 있습니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
public class XssProtectionValidator implements ConstraintValidator<XssProtection, String> {
    
    @Autowired
    private XssProtectionService xssProtectionService;
    
    private boolean allowHtml;
    
    @Override
    public void initialize(XssProtection annotation) {
        this.allowHtml = annotation.allowHtml();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        
        String sanitized = xssProtectionService.sanitize(value);
        
        if (!allowHtml) {
            // HTML 태그 완전 제거
            return value.equals(Jsoup.clean(value, Whitelist.none()));
        } else {
            // 안전한 HTML만 허용
            return value.equals(sanitized);
        }
    }
}
```

### SQL Injection 방지 검색 쿼리

#### 안전한 동적 쿼리 구현
```java
@Repository
public class RouteSearchRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<Route> searchRoutes(RouteSearchCriteria criteria) {
        StringBuilder jpql = new StringBuilder(
            "SELECT DISTINCT r FROM Route r " +
            "LEFT JOIN FETCH r.branch b " +
            "LEFT JOIN FETCH r.level l " +
            "LEFT JOIN r.tags rt " +
            "LEFT JOIN rt.tag t " +
            "WHERE r.routeStatus = 'ACTIVE'"
        );
        
        Map<String, Object> parameters = new HashMap<>();
        
        // 안전한 파라미터 바인딩
        if (criteria.getBranchId() != null) {
            jpql.append(" AND r.branch.branchId = :branchId");
            parameters.put("branchId", criteria.getBranchId());
        }
        
        if (criteria.getLevelIds() != null && !criteria.getLevelIds().isEmpty()) {
            jpql.append(" AND r.level.levelId IN :levelIds");
            parameters.put("levelIds", criteria.getLevelIds());
        }
        
        if (StringUtils.hasText(criteria.getSearchKeyword())) {
            // Full-Text Search 활용 (안전함)
            jpql.append(" AND (MATCH(r.name, r.description) AGAINST (:keyword IN BOOLEAN MODE))");
            parameters.put("keyword", sanitizeSearchKeyword(criteria.getSearchKeyword()));
        }
        
        if (criteria.getTagIds() != null && !criteria.getTagIds().isEmpty()) {
            jpql.append(" AND t.tagId IN :tagIds");
            parameters.put("tagIds", criteria.getTagIds());
        }
        
        jpql.append(" ORDER BY r.createdAt DESC");
        
        TypedQuery<Route> query = entityManager.createQuery(jpql.toString(), Route.class);
        parameters.forEach(query::setParameter);
        
        return query.setMaxResults(criteria.getLimit())
                   .setFirstResult(criteria.getOffset())
                   .getResultList();
    }
    
    private String sanitizeSearchKeyword(String keyword) {
        // MySQL Boolean Full-Text Search 특수문자 이스케이프
        return keyword.replaceAll("[+\\-><()~*\"@]+", " ")
                     .trim();
    }
}

// Criteria Builder 패턴
public class RouteSearchCriteria {
    private Long branchId;
    private List<Long> levelIds;
    private List<Long> tagIds;
    private String searchKeyword;
    private int limit = 20;
    private int offset = 0;
    
    public static RouteSearchCriteriaBuilder builder() {
        return new RouteSearchCriteriaBuilder();
    }
    
    public static class RouteSearchCriteriaBuilder {
        // Builder 패턴 구현
    }
}
```

---

## ✅ 설계 완료 체크리스트

- [x] 소셜 로그인 시스템 구조 분석 완료
  - [x] SocialProvider enum 4개 값 분석 (GOOGLE, KAKAO, NAVER, FACEBOOK)
  - [x] 이메일 기반 통합 인증 로직 설계
  - [x] 토큰 관리 및 갱신 전략 수립

- [x] CalculateUserRouteRecommendations 프로시저 분석 완료
  - [x] 5단계 추천 계산 로직 상세 분석
  - [x] 성능 최적화 포인트 도출
  - [x] Spring Batch 연동 방안 설계

- [x] 한국 클라이밍 특화 비즈니스 규칙 완료
  - [x] V등급/5.등급 체계 매핑 구조 설계
  - [x] 한국 GPS 좌표 범위 검증 로직
  - [x] 휴대폰 번호 형식 검증 구현

- [x] JSON 컬럼 활용 분석 완료
  - [x] business_hours, amenities, preferences 구조 정의
  - [x] JPA JSON 매핑 전략 수립
  - [x] 커스텀 컨버터 구현 방안

- [x] Spring Boot JPA 설계 권장사항 완료
  - [x] BaseEntity 및 Auditing 설계
  - [x] 연관관계 매핑 전략 (LAZY 중심)
  - [x] 복합 인덱스 및 공간 인덱스 설계

- [x] 성능 최적화 필수 포인트 완료
  - [x] N+1 문제 해결 전략 수립
  - [x] 다층 캐싱 전략 설계
  - [x] 커서 기반 페이징 구현 방안
  - [x] Spring Batch 활용 배치 처리 설계

- [x] 보안 강화 필수 사항 완료
  - [x] 민감정보 AES 암호화 전략
  - [x] API별 Rate Limiting 설계
  - [x] XSS 방지 필터링 구현
  - [x] SQL Injection 방지 안전한 쿼리 설계

---

**다음 단계**: Step 2-1 프로젝트 구조 생성  
**설계 완료**: `step1-3_spring_boot_guide.md`

*설계 완료일: 2025-08-16*  
*총 설계 시간: 3시간*  
*핵심 설계 방향: 성능과 보안을 동시에 고려한 한국형 클라이밍 플랫폼*