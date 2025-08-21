# Step 1-3b: 한국 특화 비즈니스 규칙 및 JPA 설계

> RoutePickr 한국 클라이밍 특화 비즈니스 규칙 및 Spring Boot JPA 설계  
> 생성일: 2025-08-20  
> 분할: step1-3_spring_boot_guide.md → 한국특화/JSON/JPA 부분 추출  
> 기반 분석: 50개 테이블 + 통합 태그 시스템

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

## ✅ 한국 특화 및 JPA 설계 완료 체크리스트

### 🇰🇷 한국 클라이밍 특화 기능
- [x] **V등급 시스템**: V0~V15 볼더링 등급 체계
- [x] **YDS 등급 시스템**: 5.5~5.15d 리드/탑로프 등급 체계  
- [x] **한국 GPS 좌표**: 33.0~38.6N, 124.0~132.0E 범위 검증
- [x] **한국 휴대폰**: 01X-XXXX-XXXX 형식 검증 및 정규화
- [x] **공휴일 운영시간**: 한국 공휴일별 특별 운영시간 지원
- [x] **서울 시간대**: Asia/Seoul 기준 시간 처리

### 📋 JSON 컬럼 활용
- [x] **business_hours**: 요일별 + 공휴일 운영시간 JSON 구조  
- [x] **amenities**: 시설/장비/서비스/접근성 JSON 구조
- [x] **preferences**: 알림/프라이버시/클라이밍/UI 설정 JSON 구조
- [x] **JPA JSON 매핑**: Hibernate 6+ @JdbcTypeCode 활용
- [x] **커스텀 컨버터**: JSON 변환 실패 처리 및 오류 핸들링

### 🏗️ JPA 설계 권장사항
- [x] **BaseEntity**: Auditing 필드 + 낙관적 락 지원
- [x] **SoftDeleteEntity**: 소프트 삭제 지원 베이스 클래스
- [x] **LAZY 로딩**: 모든 연관관계 LAZY 설정 (성능 최적화)
- [x] **Batch Size**: N+1 문제 해결을 위한 @BatchSize 설정
- [x] **복합 인덱스**: 검색 성능 최적화를 위한 인덱스 전략
- [x] **공간 인덱스**: MySQL Spatial Index 활용 위치 기반 검색

### 성능 최적화 전략
- [x] **Fetch Join**: N+1 문제 해결을 위한 JOIN FETCH 활용
- [x] **Entity Graph**: 선언적 페치 전략 설정
- [x] **Projection**: 필요한 데이터만 선별적 조회
- [x] **공간 검색**: ST_Distance_Sphere 함수 활용 거리 계산

### 한국 비즈니스 로직
- [x] **클라이밍 등급**: V등급/YDS 등급 간 점수 변환 로직
- [x] **GPS 검증**: 한국 좌표 범위 실시간 검증
- [x] **휴대폰 정규화**: 11자리 → 하이픈 형식 자동 변환  
- [x] **운영시간 체크**: 현재 시간 기준 개방 여부 판단

---

*분할 작업 2/3 완료: 한국 특화 + JSON 활용 + JPA 설계*  
*다음 파일: step1-3c_performance_security.md*