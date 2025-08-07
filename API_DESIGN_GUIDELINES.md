# RoutePick API 설계 가이드라인

## 1. API 명명 규칙

### 1.1 RESTful API 설계 원칙
```
GET    /api/{resource}           # 리소스 목록 조회
GET    /api/{resource}/{id}      # 특정 리소스 조회
POST   /api/{resource}           # 리소스 생성
PUT    /api/{resource}/{id}      # 리소스 전체 수정
PATCH  /api/{resource}/{id}      # 리소스 부분 수정
DELETE /api/{resource}/{id}      # 리소스 삭제
```

### 1.2 클라이밍 관련 리소스 설계
```
# 클라이밍장 관련
GET    /api/climbing-gyms                    # 클라이밍장 목록 조회
GET    /api/climbing-gyms/{id}               # 클라이밍장 상세 정보
GET    /api/climbing-gyms/{id}/routes        # 클라이밍장의 문제 목록
GET    /api/climbing-gyms/{id}/facilities    # 클라이밍장 시설 정보

# 문제(Route) 관련
GET    /api/routes                           # 문제 목록 조회 (필터링/검색)
GET    /api/routes/{id}                      # 문제 상세 정보
POST   /api/routes                           # 문제 등록 (관리자만)
PUT    /api/routes/{id}                      # 문제 정보 수정 (관리자만)
DELETE /api/routes/{id}                      # 문제 삭제 (관리자만)

# 사용자 활동 관련
GET    /api/user/activities                  # 사용자 활동 내역
POST   /api/user/activities                  # 활동 기록 등록
GET    /api/user/favorites                   # 즐겨찾기 목록
POST   /api/user/favorites/routes/{id}       # 문제 즐겨찾기 추가
DELETE /api/user/favorites/routes/{id}       # 문제 즐겨찾기 제거

# 검색 및 필터링
GET    /api/search/routes                    # 문제 검색
GET    /api/search/climbing-gyms             # 클라이밍장 검색
GET    /api/filters/difficulty-levels        # 난이도 필터 옵션
GET    /api/filters/route-types              # 문제 유형 필터 옵션
```

## 2. 응답 구조 표준화

### 2.1 성공 응답 구조
```json
{
  "success": true,
  "code": 200,
  "message": "성공 메시지",
  "data": {
    // 실제 데이터
  },
  "timestamp": "2024-01-01T12:00:00",
  "pagination": {
    "page": 1,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

### 2.2 에러 응답 구조
```json
{
  "success": false,
  "code": 400,
  "message": "에러 메시지",
  "error": {
    "code": 400,
    "message": "에러 메시지",
    "details": "상세 에러 정보",
    "field": "에러 필드명"
  },
  "timestamp": "2024-01-01T12:00:00"
}
```

## 3. 클라이밍 관련 데이터 모델

### 3.1 클라이밍장 (ClimbingGym)
```json
{
  "id": 1,
  "name": "클라이밍장명",
  "address": "주소",
  "phone": "전화번호",
  "website": "웹사이트",
  "operatingHours": "운영시간",
  "facilities": ["샤워실", "주차장", "카페"],
  "location": {
    "latitude": 37.5665,
    "longitude": 126.9780
  },
  "images": ["이미지URL1", "이미지URL2"],
  "rating": 4.5,
  "reviewCount": 123
}
```

### 3.2 문제 (Route)
```json
{
  "id": 1,
  "climbingGymId": 1,
  "name": "문제명",
  "difficultyLevel": "5.10a",
  "routeType": "BOULDERING",
  "color": "#FF0000",
  "description": "문제 설명",
  "imageUrl": "이미지URL",
  "setter": "세터명",
  "setDate": "2024-01-01",
  "status": "ACTIVE",
  "tags": ["오버행", "크림프"],
  "rating": 4.2,
  "attemptCount": 45,
  "successCount": 12
}
```

### 3.3 사용자 활동 (UserActivity)
```json
{
  "id": 1,
  "userId": 123,
  "routeId": 1,
  "activityType": "ATTEMPT",
  "status": "SUCCESS",
  "attemptCount": 3,
  "notes": "활동 메모",
  "timestamp": "2024-01-01T12:00:00",
  "imageUrl": "활동 이미지"
}
```

## 4. 인증 및 권한 관리

### 4.1 사용자 역할
- `USER`: 일반 사용자
- `ADMIN`: 관리자
- `SETTER`: 문제 세터

### 4.2 권한별 API 접근
```java
// 일반 사용자 접근 가능
@PreAuthorize("hasRole('USER')")
public ResponseEntity<ApiResponse<RouteDTO>> getRoute(@PathVariable Long id)

// 관리자만 접근 가능
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<RouteDTO>> createRoute(@RequestBody RouteCreateRequest request)

// 세터 또는 관리자 접근 가능
@PreAuthorize("hasAnyRole('SETTER', 'ADMIN')")
public ResponseEntity<ApiResponse<RouteDTO>> updateRoute(@PathVariable Long id, @RequestBody RouteUpdateRequest request)
```

## 5. 검색 및 필터링

### 5.1 문제 검색 API
```
GET /api/search/routes?q=오버행&difficulty=5.10a&type=BOULDERING&gymId=1&page=1&size=20
```

### 5.2 필터링 옵션
- `difficulty`: 난이도 (5.8, 5.9, 5.10a, 5.10b, ...)
- `type`: 문제 유형 (BOULDERING, LEAD, TOP_ROPE)
- `color`: 홀드 색상
- `gymId`: 클라이밍장 ID
- `status`: 문제 상태 (ACTIVE, INACTIVE)
- `setter`: 세터명

## 6. 실시간 기능 (WebSocket)

### 6.1 WebSocket 엔드포인트
```
/ws/notifications    # 실시간 알림
/ws/activities       # 실시간 활동 업데이트
```

### 6.2 메시지 형식
```json
{
  "type": "ACTIVITY_UPDATE",
  "data": {
    "userId": 123,
    "routeId": 1,
    "activityType": "SUCCESS",
    "timestamp": "2024-01-01T12:00:00"
  }
}
```

## 7. 파일 업로드

### 7.1 파일 업로드 API
```
POST /api/files/routes/{routeId}/images    # 문제 이미지 업로드
POST /api/files/activities/{activityId}    # 활동 이미지 업로드
POST /api/files/gyms/{gymId}/images       # 클라이밍장 이미지 업로드
```

### 7.2 지원 파일 형식
- 이미지: JPG, PNG, GIF, WebP
- 최대 파일 크기: 10MB
- 이미지 리사이징: 자동 썸네일 생성

## 8. 성능 최적화

### 8.1 캐싱 전략
```java
@Cacheable("routes")
public List<RouteDTO> getRoutes(RouteSearchCriteria criteria)

@Cacheable("climbing-gyms")
public ClimbingGymDTO getClimbingGym(Long id)
```

### 8.2 페이지네이션
```java
@GetMapping("/routes")
public ResponseEntity<ApiResponse<Page<RouteDTO>>> getRoutes(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    RouteSearchCriteria criteria)
```

## 9. 보안 고려사항

### 9.1 입력 검증
```java
@Valid @RequestBody RouteCreateRequest request
```

### 9.2 Rate Limiting
```java
@RateLimit(value = 100, timeUnit = TimeUnit.MINUTES)
public ResponseEntity<ApiResponse<RouteDTO>> createRoute()
```

### 9.3 SQL Injection 방지
- MyBatis 파라미터 바인딩 사용
- 동적 쿼리 최소화

## 10. 에러 코드 표준화

### 10.1 HTTP 상태 코드
- `200`: 성공
- `201`: 생성 성공
- `400`: 잘못된 요청
- `401`: 인증 실패
- `403`: 권한 없음
- `404`: 리소스 없음
- `409`: 충돌 (중복 등)
- `429`: 요청 한도 초과
- `500`: 서버 오류

### 10.2 비즈니스 에러 코드
```java
public enum ErrorCode {
    ROUTE_NOT_FOUND("ROUTE_001", "문제를 찾을 수 없습니다."),
    GYM_NOT_FOUND("GYM_001", "클라이밍장을 찾을 수 없습니다."),
    INVALID_DIFFICULTY("ROUTE_002", "잘못된 난이도입니다."),
    DUPLICATE_FAVORITE("USER_001", "이미 즐겨찾기에 추가된 문제입니다.")
}
```

## 11. 테스트 가이드라인

### 11.1 단위 테스트
```java
@Test
void shouldCreateRoute_WhenValidRequest() {
    // Given
    RouteCreateRequest request = createValidRequest();
    
    // When
    ResponseEntity<ApiResponse<RouteDTO>> response = routeController.createRoute(request);
    
    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getData().getName()).isEqualTo(request.getName());
}
```

### 11.2 통합 테스트
```java
@Test
void shouldReturnRoutes_WhenSearchingByDifficulty() {
    // Given
    String difficulty = "5.10a";
    
    // When
    ResponseEntity<ApiResponse<Page<RouteDTO>>> response = 
        routeController.searchRoutes(difficulty, 0, 20);
    
    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getData().getContent())
        .allMatch(route -> route.getDifficultyLevel().equals(difficulty));
}
```

## 12. 문서화

### 12.1 Swagger 설정
```java
@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("RoutePick API")
                .version("1.0.0")
                .description("클라이밍 문제 검색 및 관리 API"))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"));
    }
}
```

### 12.2 API 문서화 예시
```java
@Operation(
    summary = "문제 목록 조회",
    description = "필터링 조건에 따라 문제 목록을 조회합니다."
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "401", description = "인증 실패")
})
@GetMapping("/routes")
public ResponseEntity<ApiResponse<Page<RouteDTO>>> getRoutes(
    @Parameter(description = "검색 키워드") @RequestParam(required = false) String q,
    @Parameter(description = "난이도") @RequestParam(required = false) String difficulty,
    @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") int page,
    @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
    // 구현
}
```

이 가이드라인을 따라 일관된 API를 구현하면 확장 가능하고 유지보수가 용이한 클라이밍 서비스를 구축할 수 있습니다.
