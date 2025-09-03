# 9-3d1: GymController 주변 검색 테스트

> 암장 주변 검색 API 테스트 - GPS 좌표 기반 검색, 한국 좌표 검증  
> 생성일: 2025-08-27  
> 단계: 9-3d1 (암장 주변 검색 테스트)  
> 테스트 대상: 주변 암장 검색, 좌표 검증, 반경 설정

---

## 🎯 테스트 목표

### 주변 암장 검색 API 검증
- **GPS 좌표 기반**: 위도/경도 기반 근거리 검색
- **한국 좌표 검증**: 파라미터 유효성 검증 (위도 33.0~38.6, 경도 124.0~132.0)
- **반경 설정**: 1km~50km 범위 검색
- **주요 도시**: 8개 주요 도시별 검색 테스트

---

## 🏢 GymControllerTest - 주변 검색 테스트

```java
package com.routepick.controller.gym;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.common.enums.BranchStatus;
import com.routepick.dto.gym.request.NearbyGymSearchRequest;
import com.routepick.dto.gym.response.GymBranchResponse;
import com.routepick.service.gym.GymService;
import com.routepick.security.JwtTokenProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

@WebMvcTest(GymController.class)
@DisplayName("GymController 주변 검색 테스트")
class GymControllerNearbySearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GymService gymService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private List<GymBranchResponse> testBranches;
    private GymBranchResponse seoulBranch;
    private GymBranchResponse busanBranch;

    @BeforeEach
    void setUp() {
        // 서울 지점
        seoulBranch = GymBranchResponse.builder()
                .branchId(1L)
                .gymName("클라임존")
                .branchName("강남점")
                .address("서울특별시 강남구 테헤란로 123")
                .latitude(new BigDecimal("37.4979"))
                .longitude(new BigDecimal("127.0276"))
                .phoneNumber("02-1234-5678")
                .branchStatus(BranchStatus.ACTIVE)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(23, 0))
                .distanceKm(2.5)
                .wallCount(5)
                .routeCount(45)
                .build();

        // 부산 지점
        busanBranch = GymBranchResponse.builder()
                .branchId(2L)
                .gymName("부산클라이밍")
                .branchName("해운대점")
                .address("부산광역시 해운대구 센텀중앙로 123")
                .latitude(new BigDecimal("35.1696"))
                .longitude(new BigDecimal("129.1306"))
                .phoneNumber("051-1234-5678")
                .branchStatus(BranchStatus.ACTIVE)
                .openingTime(LocalTime.of(6, 0))
                .closingTime(LocalTime.of(23, 0))
                .distanceKm(1.8)
                .wallCount(8)
                .routeCount(72)
                .build();

        testBranches = Arrays.asList(seoulBranch, busanBranch);
    }

    // ===== 주변 암장 검색 API 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("주변 암장 검색 - 성공 (서울 강남)")
    void getNearbyGyms_Success_Gangnam() throws Exception {
        // Given - 강남역 좌표
        BigDecimal latitude = new BigDecimal("37.4979");
        BigDecimal longitude = new BigDecimal("127.0276");
        Integer radius = 5;
        
        given(gymService.findNearbyGyms(any(NearbyGymSearchRequest.class)))
                .willReturn(Arrays.asList(seoulBranch));

        // When & Then
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", latitude.toString())
                        .param("longitude", longitude.toString())
                        .param("radius", radius.toString())
                        .param("limit", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].branchName").value("강남점"))
                .andExpect(jsonPath("$.data[0].distanceKm").value(2.5))
                .andExpect(jsonPath("$.message").value("반경 5km 내 1개의 암장을 찾았습니다"));

        verify(gymService).findNearbyGyms(any(NearbyGymSearchRequest.class));
    }

    @ParameterizedTest
    @CsvSource({
        "37.5665, 126.9780, 서울역",
        "35.1796, 129.0756, 부산역", 
        "35.8714, 128.6014, 대구역",
        "37.4563, 126.7052, 인천역",
        "35.1595, 126.8526, 광주역",
        "36.3504, 127.3845, 대전역",
        "35.5384, 129.3114, 울산역",
        "33.4996, 126.5312, 제주역"
    })
    @WithMockUser
    @DisplayName("주요 도시별 주변 암장 검색")
    void getNearbyGyms_MajorCities(BigDecimal latitude, BigDecimal longitude, String cityName) throws Exception {
        // Given
        given(gymService.findNearbyGyms(any(NearbyGymSearchRequest.class)))
                .willReturn(testBranches);

        // When & Then
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", latitude.toString())
                        .param("longitude", longitude.toString())
                        .param("radius", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(gymService).findNearbyGyms(any(NearbyGymSearchRequest.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 25, 50})
    @WithMockUser
    @DisplayName("반경별 주변 암장 검색 테스트")
    void getNearbyGyms_VariousRadius(int radius) throws Exception {
        // Given
        given(gymService.findNearbyGyms(any(NearbyGymSearchRequest.class)))
                .willReturn(testBranches);

        // When & Then
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780") 
                        .param("radius", String.valueOf(radius)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("반경 " + radius + "km 내 2개의 암장을 찾았습니다"));
    }

    // ===== 한국 좌표 검증 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("좌표 검증 - 위도 범위 미만 (32.9)")
    void getNearbyGyms_InvalidLatitude_TooLow() throws Exception {
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", "32.9") // 33.0 미만
                        .param("longitude", "126.9780")
                        .param("radius", "5"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("위도는 33.0 이상이어야 합니다"));
    }

    @Test
    @WithMockUser
    @DisplayName("좌표 검증 - 위도 범위 초과 (38.7)")
    void getNearbyGyms_InvalidLatitude_TooHigh() throws Exception {
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", "38.7") // 38.6 초과
                        .param("longitude", "126.9780")
                        .param("radius", "5"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("위도는 38.6 이하여야 합니다"));
    }

    @Test
    @WithMockUser
    @DisplayName("좌표 검증 - 경도 범위 미만 (123.9)")
    void getNearbyGyms_InvalidLongitude_TooLow() throws Exception {
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", "37.5665")
                        .param("longitude", "123.9") // 124.0 미만
                        .param("radius", "5"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("경도는 124.0 이상이어야 합니다"));
    }

    @Test
    @WithMockUser
    @DisplayName("좌표 검증 - 경도 범위 초과 (132.1)")
    void getNearbyGyms_InvalidLongitude_TooHigh() throws Exception {
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", "37.5665")
                        .param("longitude", "132.1") // 132.0 초과
                        .param("radius", "5"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("경도는 132.0 이하여야 합니다"));
    }

    @ParameterizedTest
    @CsvSource({
        "0, 반경은 최소 1km입니다",
        "51, 반경은 최대 50km입니다", 
        "-5, 반경은 최소 1km입니다"
    })
    @WithMockUser
    @DisplayName("반경 파라미터 검증")
    void getNearbyGyms_InvalidRadius(int radius, String expectedMessage) throws Exception {
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("radius", String.valueOf(radius)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(expectedMessage));
    }

    // ===== 에러 처리 테스트 =====

    @Test
    @WithMockUser
    @DisplayName("필수 파라미터 누락 - latitude")
    void missingRequiredParameter_Latitude() throws Exception {
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("longitude", "126.9780")
                        .param("radius", "5"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("잘못된 데이터 타입 - 문자열을 숫자로")
    void invalidDataType_StringToNumber() throws Exception {
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", "not_a_number")
                        .param("longitude", "126.9780")
                        .param("radius", "5"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("과도한 limit 파라미터 제한")
    void excessiveLimit_Prevention() throws Exception {
        mockMvc.perform(get("/api/v1/gyms/nearby")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("radius", "5")
                        .param("limit", "1000")) // 과도한 limit
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
```

---

## 📊 주변 검색 테스트 특징

### 1. **GPS 좌표 기반 검색**
- **강남역 좌표**: 37.4979, 127.0276
- **거리 계산**: distanceKm 반환값 검증
- **반경 설정**: 1km~50km 다양한 반경 테스트

### 2. **한국 전역 좌표 검증**
- **위도 범위**: 33.0~38.6 (한국 영토 기준)
- **경도 범위**: 124.0~132.0 (한국 영토 기준)
- **8개 주요 도시**: 서울, 부산, 대구, 인천, 광주, 대전, 울산, 제주

### 3. **파라미터 검증**
- **반경 제한**: 1km~50km 범위 검증
- **limit 제한**: 과도한 요청 방지
- **필수 파라미터**: latitude, longitude 누락 체크

### 4. **응답 검증**
- **ApiResponse 표준**: success, data, message 구조
- **브랜치 정보**: 암장명, 지점명, 주소, 거리
- **통계 정보**: 벽면 수, 루트 수

---

## ✅ 주변 검색 테스트 체크리스트

### 핵심 기능 테스트 (11개)
- [x] **성공 케이스**: 강남역 기준 주변 암장 검색
- [x] **8개 주요 도시**: 서울, 부산, 대구, 인천, 광주, 대전, 울산, 제주
- [x] **5가지 반경**: 1km, 5km, 10km, 25km, 50km

### 좌표 검증 테스트 (4개)
- [x] **위도 범위**: 33.0 미만, 38.6 초과 차단
- [x] **경도 범위**: 124.0 미만, 132.0 초과 차단

### 파라미터 검증 테스트 (3개)
- [x] **반경 검증**: 0km, 51km, -5km 차단
- [x] **필수 파라미터**: latitude 누락 체크
- [x] **데이터 타입**: 문자열을 숫자로 변환 시도 차단

### 보안 테스트 (1개)
- [x] **과도한 limit**: 1000개 초과 요청 차단

---

**📝 연관 파일**: 
- step9-3d2_gym_detail_search_security_tests.md (상세조회, 검색, 보안 테스트)

---

**다음 단계**: 암장 상세 조회 및 검색 API 테스트  
**완료일**: 2025-08-27  
**핵심 성과**: GPS 기반 주변 검색 + 한국 좌표 검증 완성