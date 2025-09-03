# Step 1-3c: 성능 최적화 및 보안 강화

> RoutePickr 성능 최적화 및 보안 강화 설계  
> 생성일: 2025-08-20  
> 분할: step1-3_spring_boot_guide.md → 성능/보안 부분 추출  
> 기반 분석: 50개 테이블 + 통합 태그 시스템

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

## ✅ 성능 최적화 및 보안 강화 완료 체크리스트

### ⚡ 성능 최적화 포인트
- [x] **N+1 문제 해결**: Fetch Join, Entity Graph, Projection 활용
- [x] **다층 캐싱 전략**: Redis 기반 TTL별 캐시 설정
- [x] **커서 기반 페이징**: 무한 스크롤 성능 최적화
- [x] **Spring Batch**: 추천 계산 배치 처리 (청크 100개)
- [x] **비동기 처리**: @Async 및 병렬 스트림 활용
- [x] **스케줄링**: 매일 새벽 2시 추천 업데이트

### 🔒 보안 강화 필수 사항
- [x] **민감정보 암호화**: AES 암호화 컨버터 (휴대폰, 주소, CI/DI, 토큰)
- [x] **Rate Limiting**: IP/사용자별 API 호출 제한 (Redis Lua Script)
- [x] **XSS 방지**: HTML 태그 필터링 및 검증 애노테이션
- [x] **SQL Injection 방지**: 안전한 파라미터 바인딩 및 Full-Text Search

### 캐시 전략 세부 사항
- [x] **tags**: 24시간 TTL (사용자 선택 가능/루트 태깅 가능)
- [x] **climbing-levels**: 12시간 TTL (등급 체계)  
- [x] **gym-branches**: 6시간 TTL (체육관 지점)
- [x] **user-recommendations**: 30분 TTL (개인화 추천)

### Rate Limit 정책
- [x] **로그인**: IP당 분당 5회
- [x] **SMS 발송**: 전화번호당 시간당 3회
- [x] **루트 생성**: 사용자당 시간당 10개
- [x] **검색**: 사용자당 분당 100회

### XSS 방지 대상 필드
- [x] **Post**: title (텍스트만), content (제한적 HTML)
- [x] **Route**: name, description (텍스트만)
- [x] **허용 HTML 태그**: br, p, strong, em, a (href만)

### SQL Injection 방지
- [x] **파라미터 바인딩**: Named Parameter 사용
- [x] **Full-Text Search**: MySQL MATCH AGAINST 활용
- [x] **키워드 정제**: 특수문자 이스케이프 처리
- [x] **Criteria Builder**: 타입 안전한 검색 조건 구성

### 배치 처리 성능
- [x] **청크 사이즈**: 100개씩 처리
- [x] **스레드 풀**: 최대 10개 동시 처리
- [x] **Cursor Reader**: 메모리 효율적 대용량 처리
- [x] **트랜잭션**: 청크별 트랜잭션 분리

---

## 📊 설계 완료 체크리스트

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

*분할 작업 3/3 완료: 성능 최적화 + 보안 강화*  
*다음 단계: Step 2-1 프로젝트 구조 생성*  
*설계 완료: step1-3 세분화 완료*

*설계 완료일: 2025-08-20*