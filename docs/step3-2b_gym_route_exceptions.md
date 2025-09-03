# Step 3-2b: 체육관 및 루트 예외 클래스

> GymException, RouteException 도메인별 예외 클래스 구현  
> 생성일: 2025-08-20  
> 분할: step3-2_domain_exceptions.md → 체육관/루트 도메인 추출  
> 기반 분석: step3-1_exception_base.md

---

## 🎯 체육관 및 루트 예외 클래스 개요

### 구현 원칙
- **BaseException 상속**: 공통 기능 활용 (로깅, 마스킹, 추적)
- **도메인 특화**: 각 도메인 비즈니스 로직에 특화된 생성자 및 메서드
- **팩토리 메서드**: 자주 사용되는 예외의 간편 생성
- **컨텍스트 정보**: 도메인별 추가 정보 포함
- **보안 강화**: 민감정보 보호 및 적절한 로깅 레벨

### 2개 도메인 예외 클래스
```
GymException         # 체육관 관리 (지점, GPS, 영업시간)
RouteException       # 루트 관리 (난이도, 미디어, 접근권한)
```

---

## 🏢 GymException (체육관 관련)

### 클래스 구조
```java
package com.routepick.exception.gym;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 체육관 관련 예외 클래스
 * 
 * 주요 기능:
 * - 체육관/지점/벽면 관리 예외
 * - 한국 GPS 좌표 범위 검증
 * - 영업시간 관리 예외
 * - 용량 관리 예외
 * - 접근 권한 예외
 */
@Getter
public class GymException extends BaseException {
    
    private final Long gymId;           // 관련 체육관 ID
    private final Long branchId;       // 관련 지점 ID
    private final Long wallId;         // 관련 벽면 ID
    private final Double latitude;     // GPS 위도
    private final Double longitude;    // GPS 경도
    
    // 기본 생성자
    public GymException(ErrorCode errorCode) {
        super(errorCode);
        this.gymId = null;
        this.branchId = null;
        this.wallId = null;
        this.latitude = null;
        this.longitude = null;
    }
    
    // ID 포함 생성자
    public GymException(ErrorCode errorCode, Long gymId, Long branchId, Long wallId) {
        super(errorCode);
        this.gymId = gymId;
        this.branchId = branchId;
        this.wallId = wallId;
        this.latitude = null;
        this.longitude = null;
    }
    
    // GPS 좌표 포함 생성자
    public GymException(ErrorCode errorCode, Double latitude, Double longitude) {
        super(errorCode);
        this.gymId = null;
        this.branchId = null;
        this.wallId = null;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    // 파라미터화된 메시지 생성자
    public GymException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
        this.gymId = null;
        this.branchId = null;
        this.wallId = null;
        this.latitude = null;
        this.longitude = null;
    }
    
    // 원인 예외 포함 생성자
    public GymException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
        this.gymId = null;
        this.branchId = null;
        this.wallId = null;
        this.latitude = null;
        this.longitude = null;
    }
    
    // ========== 팩토리 메서드 (체육관 관리) ==========
    
    /**
     * 체육관을 찾을 수 없음
     */
    public static GymException gymNotFound(Long gymId) {
        return new GymException(ErrorCode.GYM_NOT_FOUND, gymId, null, null);
    }
    
    /**
     * 체육관 지점을 찾을 수 없음
     */
    public static GymException branchNotFound(Long branchId) {
        return new GymException(ErrorCode.GYM_BRANCH_NOT_FOUND, null, branchId, null);
    }
    
    /**
     * 클라이밍 벽을 찾을 수 없음
     */
    public static GymException wallNotFound(Long wallId) {
        return new GymException(ErrorCode.WALL_NOT_FOUND, null, null, wallId);
    }
    
    /**
     * 이미 등록된 체육관
     */
    public static GymException gymAlreadyExists(Double latitude, Double longitude) {
        return new GymException(ErrorCode.GYM_ALREADY_EXISTS, latitude, longitude);
    }
    
    /**
     * 체육관 수용 인원 초과
     */
    public static GymException capacityExceeded(Long branchId, int currentCapacity, int maxCapacity) {
        return new GymException(ErrorCode.GYM_CAPACITY_EXCEEDED, branchId, currentCapacity, maxCapacity);
    }
    
    // ========== GPS 좌표 관련 팩토리 메서드 (한국 특화) ==========
    
    /**
     * 유효하지 않은 GPS 좌표
     */
    public static GymException invalidGpsCoordinates(Double latitude, Double longitude) {
        return new GymException(ErrorCode.INVALID_GPS_COORDINATES, latitude, longitude);
    }
    
    /**
     * 한국 GPS 좌표 범위 검증
     */
    public static void validateKoreanCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new GymException(ErrorCode.REQUIRED_FIELD_MISSING, "latitude, longitude");
        }
        
        // 한국 본토 좌표 범위
        if (latitude < 33.0 || latitude > 38.6 || longitude < 124.0 || longitude > 132.0) {
            throw invalidGpsCoordinates(latitude, longitude);
        }
    }
    
    // ========== 영업시간 관련 팩토리 메서드 ==========
    
    /**
     * 현재 운영시간이 아님
     */
    public static GymException gymClosed(Long branchId) {
        return new GymException(ErrorCode.GYM_CLOSED, null, branchId, null);
    }
    
    /**
     * 유효하지 않은 영업시간 형식
     */
    public static GymException invalidBusinessHours(String businessHoursJson) {
        return new GymException(ErrorCode.INVALID_BUSINESS_HOURS, businessHoursJson);
    }
    
    // ========== 편의 메서드 ==========
    
    /**
     * 두 GPS 좌표 간의 거리 계산 (하버사인 공식)
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구 반지름 (km)
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c; // 거리 (km)
    }
    
    /**
     * 서울 중심부 좌표인지 확인
     */
    public static boolean isSeoulCenterArea(double latitude, double longitude) {
        // 서울 중심부 대략적 범위 (강남, 강북, 마포, 용산 지역)
        return latitude >= 37.4 && latitude <= 37.7 && longitude >= 126.8 && longitude <= 127.2;
    }
}
```

---

## 🧗‍♂️ RouteException (루트 관련)

### 클래스 구조
```java
package com.routepick.exception.route;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 루트 관련 예외 클래스
 * 
 * 주요 기능:
 * - 루트 등록/조회/관리 예외
 * - V등급/5.등급 체계 검증
 * - 루트 미디어 (이미지/영상) 예외
 * - 루트 접근 권한 예외
 * - 파일 업로드 예외
 */
@Getter
public class RouteException extends BaseException {
    
    private final Long routeId;         // 관련 루트 ID
    private final Long branchId;       // 관련 지점 ID
    private final Long setterId;       // 관련 세터 ID
    private final String levelName;    // 관련 난이도명 (V0, 5.10a 등)
    private final String fileName;     // 관련 파일명
    private final Long fileSize;       // 파일 크기 (bytes)
    
    // 기본 생성자
    public RouteException(ErrorCode errorCode) {
        super(errorCode);
        this.routeId = null;
        this.branchId = null;
        this.setterId = null;
        this.levelName = null;
        this.fileName = null;
        this.fileSize = null;
    }
    
    // 루트 ID 포함 생성자
    public RouteException(ErrorCode errorCode, Long routeId) {
        super(errorCode);
        this.routeId = routeId;
        this.branchId = null;
        this.setterId = null;
        this.levelName = null;
        this.fileName = null;
        this.fileSize = null;
    }
    
    // 파일 정보 포함 생성자
    public RouteException(ErrorCode errorCode, String fileName, Long fileSize) {
        super(errorCode);
        this.routeId = null;
        this.branchId = null;
        this.setterId = null;
        this.levelName = null;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }
    
    // 파라미터화된 메시지 생성자
    public RouteException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
        this.routeId = null;
        this.branchId = null;
        this.setterId = null;
        this.levelName = null;
        this.fileName = null;
        this.fileSize = null;
    }
    
    // 원인 예외 포함 생성자
    public RouteException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
        this.routeId = null;
        this.branchId = null;
        this.setterId = null;
        this.levelName = null;
        this.fileName = null;
        this.fileSize = null;
    }
    
    // ========== 팩토리 메서드 (루트 관리) ==========
    
    /**
     * 루트를 찾을 수 없음
     */
    public static RouteException routeNotFound(Long routeId) {
        return new RouteException(ErrorCode.ROUTE_NOT_FOUND, routeId);
    }
    
    /**
     * 이미 동일한 루트가 존재
     */
    public static RouteException routeAlreadyExists(Long branchId, String levelName) {
        RouteException exception = new RouteException(ErrorCode.ROUTE_ALREADY_EXISTS);
        exception.branchId = branchId;
        exception.levelName = levelName;
        return exception;
    }
    
    /**
     * 루트 세터를 찾을 수 없음
     */
    public static RouteException setterNotFound(Long setterId) {
        RouteException exception = new RouteException(ErrorCode.ROUTE_SETTER_NOT_FOUND);
        exception.setterId = setterId;
        return exception;
    }
    
    /**
     * 클라이밍 난이도를 찾을 수 없음
     */
    public static RouteException levelNotFound(String levelName) {
        RouteException exception = new RouteException(ErrorCode.CLIMBING_LEVEL_NOT_FOUND);
        exception.levelName = levelName;
        return exception;
    }
    
    /**
     * 비활성화된 루트
     */
    public static RouteException routeInactive(Long routeId) {
        return new RouteException(ErrorCode.ROUTE_INACTIVE, routeId);
    }
    
    /**
     * 루트 접근 권한 거부
     */
    public static RouteException accessDenied(Long routeId, Long userId) {
        return new RouteException(ErrorCode.ROUTE_ACCESS_DENIED, routeId, userId);
    }
    
    // ========== 미디어 관련 팩토리 메서드 ==========
    
    /**
     * 루트 이미지를 찾을 수 없음
     */
    public static RouteException imageNotFound(Long routeId, String fileName) {
        return new RouteException(ErrorCode.ROUTE_IMAGE_NOT_FOUND, fileName, null);
    }
    
    /**
     * 루트 영상을 찾을 수 없음
     */
    public static RouteException videoNotFound(Long routeId, String fileName) {
        return new RouteException(ErrorCode.ROUTE_VIDEO_NOT_FOUND, fileName, null);
    }
    
    /**
     * 미디어 업로드 실패
     */
    public static RouteException mediaUploadFailed(String fileName, Throwable cause) {
        RouteException exception = new RouteException(ErrorCode.MEDIA_UPLOAD_FAILED, cause);
        exception.fileName = fileName;
        return exception;
    }
    
    /**
     * 지원하지 않는 파일 형식
     */
    public static RouteException invalidFileFormat(String fileName) {
        return new RouteException(ErrorCode.INVALID_FILE_FORMAT, fileName, null);
    }
    
    /**
     * 파일 크기 초과
     */
    public static RouteException fileSizeExceeded(String fileName, Long fileSize, Long maxSize) {
        return new RouteException(ErrorCode.FILE_SIZE_EXCEEDED, fileName, fileSize);
    }
    
    // ========== V등급/5.등급 체계 검증 메서드 ==========
    
    /**
     * V등급 (볼더링) 유효성 검증
     */
    public static boolean isValidVGrade(String grade) {
        if (grade == null) return false;
        
        // V0부터 V17까지
        return grade.matches("^V([0-9]|1[0-7])$");
    }
    
    /**
     * YDS 5.등급 (리드/탑로프) 유효성 검증
     */
    public static boolean isValidYdsGrade(String grade) {
        if (grade == null) return false;
        
        // 5.5부터 5.15d까지
        return grade.matches("^5\\.(([5-9])|((1[0-5])[a-d]?))$");
    }
    
    /**
     * 난이도 등급 형식 검증
     */
    public static void validateClimbingLevel(String levelName) {
        if (levelName == null || levelName.trim().isEmpty()) {
            throw new RouteException(ErrorCode.REQUIRED_FIELD_MISSING, "levelName");
        }
        
        if (!isValidVGrade(levelName) && !isValidYdsGrade(levelName)) {
            throw levelNotFound(levelName);
        }
    }
    
    /**
     * 파일 형식 검증 (이미지)
     */
    public static void validateImageFormat(String fileName) {
        if (fileName == null) {
            throw new RouteException(ErrorCode.REQUIRED_FIELD_MISSING, "fileName");
        }
        
        String extension = getFileExtension(fileName).toLowerCase();
        if (!extension.matches("^(jpg|jpeg|png|gif|webp)$")) {
            throw invalidFileFormat(fileName);
        }
    }
    
    /**
     * 파일 형식 검증 (영상)
     */
    public static void validateVideoFormat(String fileName) {
        if (fileName == null) {
            throw new RouteException(ErrorCode.REQUIRED_FIELD_MISSING, "fileName");
        }
        
        String extension = getFileExtension(fileName).toLowerCase();
        if (!extension.matches("^(mp4|avi|mov|wmv|flv|webm)$")) {
            throw invalidFileFormat(fileName);
        }
    }
    
    /**
     * 파일 크기 검증
     */
    public static void validateFileSize(String fileName, Long fileSize, Long maxSizeBytes) {
        if (fileSize == null || fileSize <= 0) {
            throw new RouteException(ErrorCode.REQUIRED_FIELD_MISSING, "fileSize");
        }
        
        if (fileSize > maxSizeBytes) {
            throw fileSizeExceeded(fileName, fileSize, maxSizeBytes);
        }
    }
    
    // ========== 편의 메서드 ==========
    
    /**
     * 파일 확장자 추출
     */
    private static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }
    
    /**
     * V등급을 숫자로 변환 (정렬용)
     */
    public static int vGradeToNumber(String vGrade) {
        if (!isValidVGrade(vGrade)) return -1;
        return Integer.parseInt(vGrade.substring(1));
    }
    
    /**
     * YDS 등급을 숫자로 변환 (정렬용)
     */
    public static double ydsGradeToNumber(String ydsGrade) {
        if (!isValidYdsGrade(ydsGrade)) return -1.0;
        
        // 5.10a → 10.1, 5.11d → 11.4 형식으로 변환
        String[] parts = ydsGrade.substring(2).split("(?=[a-d])");
        double base = Double.parseDouble(parts[0]);
        
        if (parts.length > 1) {
            char subGrade = parts[1].charAt(0);
            base += (subGrade - 'a' + 1) * 0.1;
        }
        
        return base;
    }
}
```

---

## ✅ 체육관/루트 예외 완료 체크리스트

### 🏢 GymException 구현
- [x] 체육관/지점/벽면 관리 예외
- [x] 한국 GPS 좌표 범위 검증 (33-38.6N, 124-132E)
- [x] 영업시간 관리 예외
- [x] 수용 인원 관리 예외
- [x] 하버사인 공식 거리 계산
- [x] 서울 중심부 영역 확인

### 🧗‍♂️ RouteException 구현
- [x] 루트 CRUD 관련 예외
- [x] V등급 (V0-V17) 검증
- [x] YDS 5.등급 (5.5-5.15d) 검증
- [x] 이미지/영상 파일 형식 검증
- [x] 파일 크기 제한 검증
- [x] 미디어 업로드 예외 처리
- [x] 등급 변환 유틸리티 메서드

### 한국 특화 기능
- [x] 한국 GPS 좌표 범위 검증
- [x] 서울 중심부 영역 판별
- [x] 국내 클라이밍 등급 체계 지원
- [x] 한국 체육관 영업시간 패턴

### 보안 강화 사항
- [x] GPS 좌표 범위 검증으로 무효 데이터 차단
- [x] 파일 업로드 시 확장자 및 크기 검증
- [x] 루트 접근 권한 예외 처리
- [x] 민감정보 마스킹 (좌표, 파일명)

---

*분할 작업 2/4 완료: GymException + RouteException*  
*다음 파일: step3-2c_tag_payment_exceptions.md*