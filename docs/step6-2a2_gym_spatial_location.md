# Step 6-2a2: GymService - 공간 위치 및 지점 관리

> 한국 좌표 검증, 공간 쿼리, 지점 관리 기능
> 생성일: 2025-08-21
> 단계: 6-2a2 (Service 레이어 - 체육관 공간)
> 참고: step4-1a, step3-2b, step6-1a

---

## 🎯 설계 목표

- **한국 좌표 검증**: 위도 33.0~38.6, 경도 124.0~132.0 범위 검증
- **공간 쿼리**: MySQL ST_Distance_Sphere 함수 활용 주변 검색
- **지점 관리**: 체육관 다중 지점 관리
- **운영 시간**: 지점별 운영 시간 관리
- **범위 검증**: 최대 50km 반경 제한

---

## 🗺️ GymService 공간 위치 관리

### GymService.java (공간 및 지점 부분)
```java
    // 한국 좌표 범위 상수
    private static final double KOREA_MIN_LATITUDE = 33.0;
    private static final double KOREA_MAX_LATITUDE = 38.6;
    private static final double KOREA_MIN_LONGITUDE = 124.0;
    private static final double KOREA_MAX_LONGITUDE = 132.0;
    
    @Value("${routepick.gym.default-radius-km:10}")
    private double defaultSearchRadiusKm;
    
    @Value("${routepick.gym.max-radius-km:50}")
    private double maxSearchRadiusKm;

    // ===== 체육관 지점 관리 =====

    /**
     * 체육관 지점 생성
     * @param gymId 체육관 ID
     * @param branchName 지점명
     * @param address 주소
     * @param latitude 위도
     * @param longitude 경도
     * @param openTime 개점 시간
     * @param closeTime 마감 시간
     * @return 생성된 지점
     */
    @Transactional
    @CacheEvict(value = "gym-branches", allEntries = true)
    public GymBranch createGymBranch(Long gymId, String branchName, String address,
                                   BigDecimal latitude, BigDecimal longitude,
                                   LocalTime openTime, LocalTime closeTime) {
        
        log.info("체육관 지점 생성 시작 - gymId: {}, branchName: {}", gymId, branchName);
        
        Gym gym = getGymById(gymId);
        
        // 한국 좌표 범위 검증
        validateKoreaCoordinates(latitude, longitude);
        
        // XSS 보호
        branchName = XssProtectionUtil.cleanInput(branchName);
        address = XssProtectionUtil.cleanInput(address);
        
        // 입력값 검증
        if (!StringUtils.hasText(branchName)) {
            throw GymException.branchNameRequired();
        }
        
        if (!StringUtils.hasText(address)) {
            throw GymException.addressRequired();
        }
        
        // 동일 체육관 내 지점명 중복 검증
        if (gymBranchRepository.existsByGymIdAndBranchNameAndDeletedFalse(gymId, branchName)) {
            throw GymException.branchAlreadyExists(gymId, branchName);
        }
        
        // 운영 시간 검증
        if (openTime != null && closeTime != null && !openTime.isBefore(closeTime)) {
            throw GymException.invalidOperatingHours(openTime, closeTime);
        }
        
        GymBranch branch = GymBranch.builder()
            .gym(gym)
            .branchName(branchName)
            .address(address)
            .latitude(latitude)
            .longitude(longitude)
            .openTime(openTime != null ? openTime : LocalTime.of(6, 0))  // 기본 6:00
            .closeTime(closeTime != null ? closeTime : LocalTime.of(23, 0))  // 기본 23:00
            .status(GymStatus.ACTIVE)
            .build();
            
        GymBranch savedBranch = gymBranchRepository.save(branch);
        
        log.info("체육관 지점 생성 완료 - gymId: {}, branchId: {}, name: {}", 
                gymId, savedBranch.getId(), savedBranch.getBranchName());
        return savedBranch;
    }

    /**
     * 주변 체육관 지점 검색 (공간 쿼리)
     * @param latitude 사용자 위도
     * @param longitude 사용자 경도
     * @param radiusKm 검색 반경 (km)
     * @param pageable 페이징
     * @return 주변 지점 목록
     */
    @Cacheable(value = "nearby-branches", 
               key = "#latitude + '_' + #longitude + '_' + #radiusKm + '_' + #pageable.pageNumber")
    public Page<GymBranch> findNearbyBranches(BigDecimal latitude, BigDecimal longitude,
                                            Double radiusKm, Pageable pageable) {
        
        log.debug("주변 체육관 검색 - lat: {}, lng: {}, radius: {}km", latitude, longitude, radiusKm);
        
        // 한국 좌표 범위 검증
        validateKoreaCoordinates(latitude, longitude);
        
        // 검색 반경 검증
        if (radiusKm == null) {
            radiusKm = defaultSearchRadiusKm;
        }
        
        if (radiusKm <= 0 || radiusKm > maxSearchRadiusKm) {
            throw GymException.invalidSearchRadius(radiusKm, maxSearchRadiusKm);
        }
        
        // MySQL ST_Distance_Sphere 함수 활용 공간 쿼리
        return gymBranchRepository.findNearbyBranches(latitude, longitude, radiusKm, pageable);
    }
    
    /**
     * 주변 체육관 거리 계산
     * @param fromLat 시작점 위도
     * @param fromLng 시작점 경도
     * @param toLat 도착점 위도
     * @param toLng 도착점 경도
     * @return 거리 (km)
     */
    public double calculateDistance(BigDecimal fromLat, BigDecimal fromLng,
                                   BigDecimal toLat, BigDecimal toLng) {
        
        // 한국 좌표 범위 검증
        validateKoreaCoordinates(fromLat, fromLng);
        validateKoreaCoordinates(toLat, toLng);
        
        // Haversine 공식으로 거리 계산
        double earthRadiusKm = 6371.0;
        
        double dLat = Math.toRadians(toLat.doubleValue() - fromLat.doubleValue());
        double dLng = Math.toRadians(toLng.doubleValue() - fromLng.doubleValue());
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(fromLat.doubleValue())) * 
                   Math.cos(Math.toRadians(toLat.doubleValue())) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
                   
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return earthRadiusKm * c;
    }
    
    /**
     * 지역별 체육관 검색
     * @param sido 시/도
     * @param sigungu 시/군/구
     * @param pageable 페이징
     * @return 지역별 체육관 목록
     */
    @Cacheable(value = "gyms-by-region", key = "#sido + '_' + #sigungu + '_' + #pageable.pageNumber")
    public Page<GymBranch> findGymsByRegion(String sido, String sigungu, Pageable pageable) {
        log.debug("지역별 체육관 검색 - sido: {}, sigungu: {}", sido, sigungu);
        
        if (!StringUtils.hasText(sido)) {
            throw GymException.regionRequired();
        }
        
        sido = XssProtectionUtil.cleanInput(sido);
        sigungu = StringUtils.hasText(sigungu) ? XssProtectionUtil.cleanInput(sigungu) : null;
        
        return gymBranchRepository.findByRegion(sido, sigungu, pageable);
    }

    /**
     * 체육관 지점 정보 수정
     * @param branchId 지점 ID
     * @param branchName 지점명
     * @param address 주소
     * @param latitude 위도
     * @param longitude 경도
     * @param openTime 개점 시간
     * @param closeTime 마감 시간
     * @return 수정된 지점
     */
    @Transactional
    @CacheEvict(value = {"gym-branches", "nearby-branches", "gyms-by-region"}, allEntries = true)
    public GymBranch updateGymBranch(Long branchId, String branchName, String address,
                                   BigDecimal latitude, BigDecimal longitude,
                                   LocalTime openTime, LocalTime closeTime) {
        
        log.info("체육관 지점 정보 수정 시작 - branchId: {}", branchId);
        
        GymBranch branch = gymBranchRepository.findByIdAndDeletedFalse(branchId)
            .orElseThrow(() -> GymException.branchNotFound(branchId));
        
        // 좌표 변경 시 한국 범위 검증
        if (latitude != null && longitude != null) {
            validateKoreaCoordinates(latitude, longitude);
            branch.updateCoordinates(latitude, longitude);
        }
        
        // XSS 보호 및 업데이트
        if (StringUtils.hasText(branchName)) {
            branchName = XssProtectionUtil.cleanInput(branchName);
            
            // 동일 체육관 내 지점명 중복 검증
            if (!branch.getBranchName().equals(branchName) &&
                gymBranchRepository.existsByGymIdAndBranchNameAndDeletedFalse(
                    branch.getGym().getId(), branchName)) {
                throw GymException.branchAlreadyExists(branch.getGym().getId(), branchName);
            }
            branch.updateBranchName(branchName);
        }
        
        if (StringUtils.hasText(address)) {
            branch.updateAddress(XssProtectionUtil.cleanInput(address));
        }
        
        if (openTime != null && closeTime != null) {
            if (!openTime.isBefore(closeTime)) {
                throw GymException.invalidOperatingHours(openTime, closeTime);
            }
            branch.updateOperatingHours(openTime, closeTime);
        }
        
        log.info("체육관 지점 정보 수정 완료 - branchId: {}", branchId);
        return branch;
    }
    
    /**
     * 체육관 지점 삭제
     * @param branchId 지점 ID
     */
    @Transactional
    @CacheEvict(value = {"gym-branches", "nearby-branches", "gyms-by-region"}, allEntries = true)
    public void deleteGymBranch(Long branchId) {
        log.info("체육관 지점 삭제 시작 - branchId: {}", branchId);
        
        GymBranch branch = gymBranchRepository.findByIdAndDeletedFalse(branchId)
            .orElseThrow(() -> GymException.branchNotFound(branchId));
            
        branch.markAsDeleted();
        
        // 관련 벨면들도 소프트 삭제
        List<Wall> walls = wallRepository.findByBranchIdAndDeletedFalseOrderByWallName(branchId);
        walls.forEach(Wall::markAsDeleted);
        
        log.info("체육관 지점 삭제 완료 - branchId: {}, 관련 벨면 수: {}", branchId, walls.size());
    }
    
    /**
     * 체육관의 모든 지점 조회
     * @param gymId 체육관 ID
     * @return 지점 목록
     */
    @Cacheable(value = "gym-branches", key = "#gymId")
    public List<GymBranch> getGymBranches(Long gymId) {
        log.debug("체육관 지점 목록 조회 - gymId: {}", gymId);
        
        // 체육관 존재 검증
        getGymById(gymId);
        
        return gymBranchRepository.findByGymIdAndDeletedFalseOrderByBranchName(gymId);
    }
    
    /**
     * 지점 상세 조회
     * @param branchId 지점 ID
     * @return 지점 정보
     */
    @Cacheable(value = "branch", key = "#branchId")
    public GymBranch getBranchById(Long branchId) {
        return gymBranchRepository.findByIdAndDeletedFalse(branchId)
            .orElseThrow(() -> GymException.branchNotFound(branchId));
    }
    
    // ===== 벨면 관리 =====

    /**
     * 벨면 생성
     * @param branchId 지점 ID
     * @param wallName 벨면명
     * @param wallType 벨면 타입
     * @param wallHeight 벨면 높이
     * @param wallAngle 벨면 각도
     * @return 생성된 벨면
     */
    @Transactional
    @CacheEvict(value = "branch-walls", key = "#branchId")
    public Wall createWall(Long branchId, String wallName, String wallType, 
                          Integer wallHeight, String wallAngle) {
        
        log.info("벨면 생성 시작 - branchId: {}, wallName: {}", branchId, wallName);
        
        GymBranch branch = getBranchById(branchId);
        
        // XSS 보호
        wallName = XssProtectionUtil.cleanInput(wallName);
        wallType = XssProtectionUtil.cleanInput(wallType);
        wallAngle = XssProtectionUtil.cleanInput(wallAngle);
        
        // 입력값 검증
        if (!StringUtils.hasText(wallName)) {
            throw GymException.wallNameRequired();
        }
        
        // 동일 지점 내 벨면명 중복 검증
        if (wallRepository.existsByBranchIdAndWallNameAndDeletedFalse(branchId, wallName)) {
            throw GymException.wallAlreadyExists(branchId, wallName);
        }
        
        // 벨면 높이 검증
        if (wallHeight != null && (wallHeight <= 0 || wallHeight > 100)) {
            throw GymException.invalidWallHeight(wallHeight);
        }
        
        Wall wall = Wall.builder()
            .branch(branch)
            .wallName(wallName)
            .wallType(wallType != null ? wallType : "VERTICAL")  // 기본값
            .wallHeight(wallHeight != null ? wallHeight : 5)     // 기본 5m
            .wallAngle(wallAngle != null ? wallAngle : "90°")      // 기본 수직
            .build();
            
        Wall savedWall = wallRepository.save(wall);
        
        log.info("벨면 생성 완료 - branchId: {}, wallId: {}, name: {}", 
                branchId, savedWall.getId(), savedWall.getWallName());
        return savedWall;
    }

    /**
     * 지점의 벨면 목록 조회
     * @param branchId 지점 ID
     * @return 벨면 목록
     */
    @Cacheable(value = "branch-walls", key = "#branchId")
    public List<Wall> getBranchWalls(Long branchId) {
        log.debug("지점 벨면 목록 조회 - branchId: {}", branchId);
        
        // 지점 존재 검증
        getBranchById(branchId);
            
        return wallRepository.findByBranchIdAndDeletedFalseOrderByWallName(branchId);
    }
    
    // ===== 유틸리티 메서드 =====

    /**
     * 한국 좌표 범위 검증
     * @param latitude 위도
     * @param longitude 경도
     */
    private void validateKoreaCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw GymException.coordinatesRequired();
        }
        
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        
        if (lat < KOREA_MIN_LATITUDE || lat > KOREA_MAX_LATITUDE ||
            lng < KOREA_MIN_LONGITUDE || lng > KOREA_MAX_LONGITUDE) {
            throw GymException.invalidKoreaCoordinates(lat, lng);
        }
    }

    /**
     * 체육관 운영 시간 확인
     * @param branchId 지점 ID
     * @return 운영 중 여부
     */
    public boolean isGymOpen(Long branchId) {
        GymBranch branch = getBranchById(branchId);
            
        LocalTime now = LocalTime.now();
        LocalTime openTime = branch.getOpenTime();
        LocalTime closeTime = branch.getCloseTime();
        
        // 24시간 운영 처리
        if (openTime.equals(closeTime)) {
            return true;
        }
        
        // 일반적인 운영 시간
        if (openTime.isBefore(closeTime)) {
            return !now.isBefore(openTime) && !now.isAfter(closeTime);
        } else {
            // 자정 넘는 운영 (예: 22:00 - 06:00)
            return !now.isBefore(openTime) || !now.isAfter(closeTime);
        }
    }
    
    /**
     * 지점별 운영 상태 확인
     * @param branchId 지점 ID
     * @return 운영 상태 정보
     */
    public BranchOperationStatus getBranchOperationStatus(Long branchId) {
        GymBranch branch = getBranchById(branchId);
        
        boolean isOpen = isGymOpen(branchId);
        LocalTime now = LocalTime.now();
        
        return BranchOperationStatus.builder()
            .branchId(branchId)
            .branchName(branch.getBranchName())
            .isOpen(isOpen)
            .currentTime(now)
            .openTime(branch.getOpenTime())
            .closeTime(branch.getCloseTime())
            .build();
    }
    
    // ===== DTO 클래스 =====

    /**
     * 지점 운영 상태 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class BranchOperationStatus {
        private final Long branchId;
        private final String branchName;
        private final boolean isOpen;
        private final LocalTime currentTime;
        private final LocalTime openTime;
        private final LocalTime closeTime;
    }
}
```

---

## 🗺️ 공간 위치 관리 기능

### 1. 한국 좌표 검증
- **위도 범위**: 33.0 ~ 38.6 (제주도 ~ 함경도)
- **경도 범위**: 124.0 ~ 132.0 (서해 ~ 동해)
- **입력 검증**: null 값 및 범위 외 좌표 차단
- **예외 처리**: 상세한 오류 메시지 제공

### 2. 공간 쿼리 시스템
- **MySQL ST_Distance_Sphere**: 지구 곡률 고려 거리 계산
- **반경 제한**: 기본 10km, 최대 50km
- **Haversine 공식**: 직선 거리 계산 지원
- **성능 최적화**: Spatial Index 활용

### 3. 지점 관리 시스템
- **다중 지점**: 하나의 체육관이 여러 지점 운영
- **지점별 관리**: 개별 지점 정보 및 상태
- **운영 시간**: 지점별 개점/마감 시간
- **주소 관리**: 상세 주소 및 지역 정보

### 4. 벨면 관리
- **벨면 속성**: 이름, 타입, 높이, 각도
- **지점별 관리**: 각 지점마다 벨면 관리
- **중복 방지**: 동일 지점 내 벨면명 중복 방지
- **사양 검증**: 높이 1-100m 범위 제한

---

## 🔍 검색 및 조회 기능

### 1. 공간 기반 검색
- **주변 검색**: 사용자 위치 기반 반경 검색
- **거리 계산**: 정확한 직선 거리 계산
- **성능 최적화**: Redis 캐싱 및 Spatial Index
- **페이징**: 대용량 검색 결과 처리

### 2. 지역별 검색
- **행정구역**: 시/도, 시/군/구 기반 검색
- **계층 검색**: 대분류 -> 소분류 차례 검색
- **주소 파싱**: 주소 데이터 기반 분류
- **캐싱 전략**: 지역별 검색 결과 캐싱

### 3. 운영 상태 관리
- **실시간 확인**: 현재 시간 기반 운영 상태
- **24시간 대응**: 자정 넘는 운영시간 처리
- **상태 정보**: 상세한 운영 상태 DTO 제공
- **예외 처리**: 잘못된 운영시간 방지

---

## ✅ 완료 사항
- ✅ 한국 좌표 범위 검증 (33.0-38.6, 124.0-132.0)
- ✅ MySQL ST_Distance_Sphere 공간 쿼리
- ✅ 주변 체육관 검색 시스템
- ✅ 체육관 지점 관리
- ✅ 지역별 체육관 검색
- ✅ 거리 계산 (Haversine 공식)
- ✅ 운영 시간 관리 시스템
- ✅ 벨면 관리 시스템
- ✅ Redis 캐싱 최적화
- ✅ 예외 처리 및 로깅

---

*GymService 공간 위치 및 지점 관리 기능 구현 완료*