# Step 7-4d: Gym & Route Request DTOs 구현

## 📋 구현 목표
암장 및 루트 관리 Request DTO 구현:
1. **한국 특화 검증** - GPS 좌표, 지역, 주소 검증
2. **V등급/YDS 시스템** - 난이도 범위 및 등급 시스템 지원
3. **비즈니스 로직 검증** - 도메인별 제약 조건 검증
4. **Bean Validation** - 표준 검증 어노테이션 활용
5. **한국 문화 반영** - 언어, 지역, 단위 체계 고려

---

## 🏢 Gym Request DTOs

### 📁 파일 위치
```
src/main/java/com/routepick/dto/gym/request/
```

### 1. NearbyGymSearchRequest
```java
package com.routepick.dto.gym.request;

import com.routepick.common.enums.BranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주변 암장 검색 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "주변 암장 검색 요청")
public class NearbyGymSearchRequest {
    
    @Schema(description = "위도 (한국 범위: 33.0~38.6)", example = "37.5665", required = true)
    @NotNull(message = "위도는 필수입니다")
    @DecimalMin(value = "33.0", message = "위도는 33.0 이상이어야 합니다 (한국 남단 기준)")
    @DecimalMax(value = "38.6", message = "위도는 38.6 이하여야 합니다 (한국 북단 기준)")
    @Digits(integer = 2, fraction = 6, message = "위도는 소수점 6자리까지 가능합니다")
    private BigDecimal latitude;
    
    @Schema(description = "경도 (한국 범위: 124.0~132.0)", example = "126.9780", required = true)
    @NotNull(message = "경도는 필수입니다")
    @DecimalMin(value = "124.0", message = "경도는 124.0 이상이어야 합니다 (한국 서단 기준)")
    @DecimalMax(value = "132.0", message = "경도는 132.0 이하여야 합니다 (한국 동단 기준)")
    @Digits(integer = 3, fraction = 6, message = "경도는 소수점 6자리까지 가능합니다")
    private BigDecimal longitude;
    
    @Schema(description = "검색 반경 (km)", example = "5", minimum = "1", maximum = "50")
    @NotNull(message = "검색 반경은 필수입니다")
    @Min(value = 1, message = "검색 반경은 최소 1km입니다")
    @Max(value = 50, message = "검색 반경은 최대 50km입니다")
    private Integer radius;
    
    @Schema(description = "지점 상태 필터", example = "ACTIVE")
    private BranchStatus branchStatus;
    
    @Schema(description = "최대 결과 수", example = "20", minimum = "1", maximum = "100")
    @Min(value = 1, message = "최소 1개 이상이어야 합니다")
    @Max(value = 100, message = "최대 100개까지 조회 가능합니다")
    private Integer limit = 20;
    
    /**
     * 한국 좌표 범위 검증
     */
    @AssertTrue(message = "한국 내 유효한 좌표여야 합니다")
    public boolean isValidKoreanCoordinates() {
        if (latitude == null || longitude == null) {
            return false;
        }
        
        // 더 정확한 한국 영토 검증 (주요 도시 기준)
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        
        // 제주도까지 포함한 한국 전 영토 검증
        return (lat >= 33.0 && lat <= 38.6) && (lng >= 124.0 && lng <= 132.0);
    }
}
```

### 2. GymSearchRequest
```java
package com.routepick.dto.gym.request;

import com.routepick.common.enums.BranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 암장 검색 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "암장 검색 요청")
public class GymSearchRequest {
    
    @Schema(description = "검색 키워드 (암장명, 지점명)", example = "더클라임")
    @Size(min = 1, max = 100, message = "검색 키워드는 1-100자여야 합니다")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s\\-_.()]*$", 
             message = "검색 키워드에 특수문자는 사용할 수 없습니다")
    private String keyword;
    
    @Schema(description = "지역 (시/도)", example = "서울특별시")
    @Pattern(regexp = "^(서울특별시|부산광역시|대구광역시|인천광역시|광주광역시|대전광역시|울산광역시|세종특별자치시|경기도|강원도|충청북도|충청남도|전라북도|전라남도|경상북도|경상남도|제주특별자치도)$",
             message = "올바른 한국 지역명을 입력해주세요")
    private String region;
    
    @Schema(description = "상세 주소", example = "강남구 역삼동")
    @Size(max = 200, message = "주소는 200자를 초과할 수 없습니다")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s\\-_.()]*$", 
             message = "주소에 허용되지 않는 문자가 포함되어 있습니다")
    private String address;
    
    @Schema(description = "편의시설 목록", 
            example = "[\"주차장\", \"샤워실\", \"락커\", \"매점\", \"렌탈\"]")
    @Size(max = 10, message = "편의시설은 최대 10개까지 선택 가능합니다")
    private List<@Size(min = 1, max = 20, message = "편의시설명은 1-20자여야 합니다") 
                 @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s\\-_.()]*$", 
                         message = "편의시설명에 특수문자는 사용할 수 없습니다") String> amenities;
    
    @Schema(description = "지점 상태", example = "ACTIVE")
    private BranchStatus branchStatus;
    
    /**
     * 검색 조건 존재 여부 검증
     */
    @AssertTrue(message = "최소 하나의 검색 조건을 입력해야 합니다")
    public boolean hasSearchCriteria() {
        return (keyword != null && !keyword.trim().isEmpty()) ||
               (region != null && !region.trim().isEmpty()) ||
               (address != null && !address.trim().isEmpty()) ||
               (amenities != null && !amenities.isEmpty());
    }
}
```

---

## 🧗‍♀️ Route Request DTOs

### 3. RouteSearchRequest
```java
package com.routepick.dto.route.request;

import com.routepick.common.enums.GradeSystem;
import com.routepick.common.enums.RouteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 루트 검색 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "루트 검색 요청")
public class RouteSearchRequest {
    
    @Schema(description = "지점 ID", example = "1")
    @Positive(message = "지점 ID는 양수여야 합니다")
    private Long branchId;
    
    @Schema(description = "벽면 ID", example = "1")
    @Positive(message = "벽면 ID는 양수여야 합니다")
    private Long wallId;
    
    @Schema(description = "최소 난이도 (V0=1, V1=2, ..., V16=17)", example = "5", minimum = "1", maximum = "20")
    @Min(value = 1, message = "난이도는 최소 1이어야 합니다")
    @Max(value = 20, message = "난이도는 최대 20이어야 합니다")
    private Integer minDifficulty;
    
    @Schema(description = "최대 난이도 (V0=1, V1=2, ..., V16=17)", example = "10", minimum = "1", maximum = "20")
    @Min(value = 1, message = "난이도는 최소 1이어야 합니다")
    @Max(value = 20, message = "난이도는 최대 20이어야 합니다")
    private Integer maxDifficulty;
    
    @Schema(description = "등급 시스템", example = "V_GRADE")
    private GradeSystem gradeSystem;
    
    @Schema(description = "루트 상태", example = "ACTIVE")
    private RouteStatus routeStatus;
    
    @Schema(description = "태그 ID 목록", example = "[1, 2, 3]")
    @Size(max = 10, message = "태그는 최대 10개까지 선택 가능합니다")
    private List<@Positive(message = "태그 ID는 양수여야 합니다") Long> tags;
    
    @Schema(description = "색상 (헥스 코드)", example = "#FF5733")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "올바른 헥스 색상 코드를 입력해주세요 (예: #FF5733)")
    private String color;
    
    @Schema(description = "세터 ID", example = "1")
    @Positive(message = "세터 ID는 양수여야 합니다")
    private Long setterId;
    
    /**
     * 난이도 범위 검증
     */
    @AssertTrue(message = "최소 난이도는 최대 난이도보다 작거나 같아야 합니다")
    public boolean isValidDifficultyRange() {
        if (minDifficulty == null || maxDifficulty == null) {
            return true; // null인 경우는 다른 검증에서 처리
        }
        return minDifficulty <= maxDifficulty;
    }
}
```

### 4. RouteTagRequest
```java
package com.routepick.dto.route.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 루트 태깅 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "루트 태깅 요청")
public class RouteTagRequest {
    
    @Schema(description = "태그 ID", example = "1", required = true)
    @NotNull(message = "태그 ID는 필수입니다")
    @Positive(message = "태그 ID는 양수여야 합니다")
    private Long tagId;
    
    @Schema(description = "관련도 점수 (0.0 ~ 1.0)", example = "0.8", required = true)
    @NotNull(message = "관련도 점수는 필수입니다")
    @DecimalMin(value = "0.0", inclusive = true, message = "관련도 점수는 0.0 이상이어야 합니다")
    @DecimalMax(value = "1.0", inclusive = true, message = "관련도 점수는 1.0 이하여야 합니다")
    @Digits(integer = 1, fraction = 2, message = "관련도 점수는 소수점 2자리까지 가능합니다")
    private BigDecimal relevanceScore;
    
    @Schema(description = "태깅 이유 (선택사항)", example = "이 루트에 딱 맞는 홀드 타입이에요")
    @Size(max = 200, message = "태깅 이유는 200자를 초과할 수 없습니다")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s.,!?\\-()]*$", 
             message = "태깅 이유에 허용되지 않는 문자가 포함되어 있습니다")
    private String reason;
}
```

### 5. DifficultyVoteRequest
```java
package com.routepick.dto.route.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 난이도 투표 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "난이도 투표 요청")
public class DifficultyVoteRequest {
    
    @Schema(description = "제안하는 난이도 (V0=1, V1=2, ..., V16=17)", 
            example = "8", minimum = "1", maximum = "20", required = true)
    @NotNull(message = "제안 난이도는 필수입니다")
    @Min(value = 1, message = "난이도는 최소 1이어야 합니다 (V0 등급)")
    @Max(value = 20, message = "난이도는 최대 20이어야 합니다 (V16+ 등급)")
    private Integer suggestedDifficulty;
    
    @Schema(description = "투표 이유", example = "생각보다 홀드가 작아서 어려웠습니다")
    @Size(max = 200, message = "투표 이유는 200자를 초과할 수 없습니다")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s.,!?\\-()]*$", 
             message = "투표 이유에 허용되지 않는 문자가 포함되어 있습니다")
    private String voteReason;
    
    @Schema(description = "신뢰도 (1-5, 자신의 실력 평가)", example = "4", minimum = "1", maximum = "5")
    @Min(value = 1, message = "신뢰도는 최소 1이어야 합니다")
    @Max(value = 5, message = "신뢰도는 최대 5여야 합니다")
    private Integer confidenceLevel = 3; // 기본값 3 (보통)
}
```

---

## 🧗‍♂️ Climbing Request DTOs

### 6. ClimbingRecordRequest
```java
package com.routepick.dto.climbing.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 클라이밍 기록 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "클라이밍 기록 요청")
public class ClimbingRecordRequest {
    
    @Schema(description = "루트 ID", example = "1", required = true)
    @NotNull(message = "루트 ID는 필수입니다")
    @Positive(message = "루트 ID는 양수여야 합니다")
    private Long routeId;
    
    @Schema(description = "클라이밍 날짜", example = "2025-08-25", required = true)
    @NotNull(message = "클라이밍 날짜는 필수입니다")
    @PastOrPresent(message = "클라이밍 날짜는 오늘 또는 과거 날짜여야 합니다")
    private LocalDate climbDate;
    
    @Schema(description = "성공률 (0.0 ~ 1.0, 0.0=실패, 1.0=완전성공)", 
            example = "0.8", required = true)
    @NotNull(message = "성공률은 필수입니다")
    @DecimalMin(value = "0.0", inclusive = true, message = "성공률은 0.0 이상이어야 합니다")
    @DecimalMax(value = "1.0", inclusive = true, message = "성공률은 1.0 이하여야 합니다")
    @Digits(integer = 1, fraction = 2, message = "성공률은 소수점 2자리까지 가능합니다")
    private BigDecimal successRate;
    
    @Schema(description = "체감 난이도 평가 (1-5)", example = "4", minimum = "1", maximum = "5", required = true)
    @NotNull(message = "난이도 평가는 필수입니다")
    @Min(value = 1, message = "난이도 평가는 최소 1이어야 합니다")
    @Max(value = 5, message = "난이도 평가는 최대 5여야 합니다")
    private Integer difficultyRating;
    
    @Schema(description = "시도 횟수", example = "5", minimum = "1")
    @Positive(message = "시도 횟수는 양수여야 합니다")
    private Integer attemptCount = 1; // 기본값 1회
    
    @Schema(description = "운동 시간 (분)", example = "30", minimum = "1", maximum = "600")
    @Min(value = 1, message = "운동 시간은 최소 1분이어야 합니다")
    @Max(value = 600, message = "운동 시간은 최대 600분(10시간)이어야 합니다")
    private Integer durationMinutes;
    
    @Schema(description = "메모", example = "왼쪽 홀드가 미끄러웠음. 다음에는 더 조심해야겠다.")
    @Size(max = 500, message = "메모는 500자를 초과할 수 없습니다")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s.,!?\\-()]*$", 
             message = "메모에 허용되지 않는 문자가 포함되어 있습니다")
    private String memo;
}
```

### 7. ClimbingShoeRequest
```java
package com.routepick.dto.climbing.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 클라이밍 신발 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "클라이밍 신발 요청")
public class ClimbingShoeRequest {
    
    @Schema(description = "신발 ID", example = "1", required = true)
    @NotNull(message = "신발 ID는 필수입니다")
    @Positive(message = "신발 ID는 양수여야 합니다")
    private Long shoeId;
    
    @Schema(description = "신발 사이즈 (mm)", example = "250", minimum = "200", maximum = "320", required = true)
    @NotNull(message = "신발 사이즈는 필수입니다")
    @DecimalMin(value = "200", message = "신발 사이즈는 최소 200mm입니다")
    @DecimalMax(value = "320", message = "신발 사이즈는 최대 320mm입니다")
    private Integer shoeSize;
    
    @Schema(description = "리뷰 평점 (1-5)", example = "4", minimum = "1", maximum = "5", required = true)
    @NotNull(message = "리뷰 평점은 필수입니다")
    @Min(value = 1, message = "평점은 최소 1점이어야 합니다")
    @Max(value = 5, message = "평점은 최대 5점이어야 합니다")
    private Integer reviewRating;
    
    @Schema(description = "구매 날짜", example = "2025-01-15")
    @PastOrPresent(message = "구매 날짜는 오늘 또는 과거 날짜여야 합니다")
    private LocalDate purchaseDate;
    
    @Schema(description = "사용 기간 (개월)", example = "6", minimum = "0", maximum = "120")
    @Min(value = 0, message = "사용 기간은 0개월 이상이어야 합니다")
    @Max(value = 120, message = "사용 기간은 120개월(10년) 이하여야 합니다")
    private Integer usagePeriodMonths = 0;
    
    @Schema(description = "리뷰 내용", example = "그립감이 좋고 편안합니다. 초보자에게 추천!")
    @Size(max = 1000, message = "리뷰는 1000자를 초과할 수 없습니다")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s.,!?\\-()]*$", 
             message = "리뷰에 허용되지 않는 문자가 포함되어 있습니다")
    private String review;
    
    @Schema(description = "추천 여부", example = "true")
    private Boolean recommended = true; // 기본값 추천
}
```

### 8. ClimbingStatsRequest
```java
package com.routepick.dto.climbing.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 클라이밍 통계 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "클라이밍 통계 요청")
public class ClimbingStatsRequest {
    
    @Schema(description = "시작 날짜", example = "2025-01-01")
    private LocalDate startDate;
    
    @Schema(description = "종료 날짜", example = "2025-08-25")
    private LocalDate endDate;
    
    @Schema(description = "실패한 시도도 포함", example = "true")
    private Boolean includeFailedAttempts = true; // 기본값 포함
    
    @Schema(description = "지점별 통계 포함", example = "false")
    private Boolean includeGymStats = false;
    
    @Schema(description = "월별 상세 통계 포함", example = "false")
    private Boolean includeMonthlyDetails = false;
    
    /**
     * 날짜 범위 검증
     */
    @AssertTrue(message = "시작 날짜는 종료 날짜보다 이전이어야 합니다")
    public boolean isValidDateRange() {
        if (startDate == null || endDate == null) {
            return true; // null인 경우는 기본 범위 적용
        }
        return !startDate.isAfter(endDate);
    }
    
    /**
     * 최대 조회 기간 검증 (5년)
     */
    @AssertTrue(message = "조회 기간은 최대 5년까지 가능합니다")
    public boolean isValidPeriodLength() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return startDate.plusYears(5).isAfter(endDate) || startDate.plusYears(5).isEqual(endDate);
    }
}
```

---

## 📋 구현 완료 사항
✅ **8개 Request DTO** - Gym(2개) + Route(3개) + Climbing(3개)  
✅ **한국 특화 검증** - GPS 좌표, 지역명, 주소 체계  
✅ **V등급 시스템** - 난이도 1~20 (V0~V16+) 완벽 지원  
✅ **Bean Validation** - 표준 검증 어노테이션 완벽 활용  
✅ **비즈니스 로직 검증** - @AssertTrue 커스텀 검증  
✅ **보안 강화** - XSS 방지 패턴 정규식 적용  
✅ **완전한 문서화** - Swagger Schema 완벽 적용  

## 🎯 주요 특징
- **한국 좌표 검증** - 위도 33.0~38.6°, 경도 124.0~132.0° 정확한 범위
- **지역명 검증** - 17개 시/도 정확한 한국어 검증
- **V등급 완벽 지원** - V0(1) ~ V16+(20) 국제 표준 난이도
- **관련도 점수** - 0.0~1.0 소수점 2자리 정밀도
- **날짜 범위 검증** - 과거/현재 제한, 최대 5년 조회 기간
- **한국어 패턴** - 한글, 영문, 숫자, 기본 문장부호만 허용
- **신발 사이즈** - 200~320mm 한국 표준 사이즈 범위

## ⚙️ 검증 전략
- **필수 필드** - @NotNull로 필수 값 보장
- **범위 검증** - @Min, @Max로 비즈니스 규칙 적용
- **패턴 검증** - @Pattern으로 한국 특화 형식 검증
- **커스텀 검증** - @AssertTrue로 복합 조건 검증
- **보안 검증** - XSS 방지 정규식 패턴 적용