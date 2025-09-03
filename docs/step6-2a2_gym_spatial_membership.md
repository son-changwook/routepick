# Step 6-2a2: GymService 구현 - 공간쿼리 및 회원관리

> 체육관 공간쿼리 및 회원관리 서비스 - 위치검색, 회원권, 통계  
> 생성일: 2025-08-21  
> 단계: 6-2a2 (Service 레이어 - 체육관 도메인 확장)  
> 연관: step6-2a1_gym_management_core.md

---

## 🎯 설계 목표

- **공간 쿼리**: MySQL ST_Distance_Sphere 함수 활용 주변 검색
- **회원 관리**: 체육관 회원권 등록, 만료, 상태 관리
- **통계 시스템**: 체육관 운영 통계 및 분석
- **성능 최적화**: 위치 기반 캐싱 및 배치 처리

---

## 🗺️ GymService - 공간쿼리 및 회원관리 확장

### GymService.java (Part 2 - 확장 기능)
```java
// 앞의 import 구문들은 step6-2a1과 동일

/**
 * 체육관 공간쿼리 및 회원관리 확장 서비스
 * 
 * 확장 기능:
 * - 주변 체육관 검색 (공간 쿼리)
 * - 체육관 회원 관리
 * - 통계 및 분석
 * - 고급 캐싱 전략
 */
public class GymService {
    // ... 기본 필드들은 step6-2a1과 동일 ...

    // ===== 공간 쿼리 (위치 기반 검색) =====

    /**
     * 주변 체육관 지점 검색 (공간 쿼리)
     */
    @Cacheable(value = "nearby-branches", 
               key = "#latitude + '_' + #longitude + '_' + #radiusKm + '_' + #pageable.pageNumber")
    public Page<GymBranch> findNearbyBranches(BigDecimal latitude, BigDecimal longitude,
                                            Double radiusKm, Pageable pageable) {
        
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
     * 정확한 거리 계산 포함 주변 검색
     */
    @Cacheable(value = "nearby-branches-detailed", 
               key = "#latitude + '_' + #longitude + '_' + #radiusKm")
    public List<NearbyGymDto> findNearbyBranchesWithDistance(BigDecimal latitude, BigDecimal longitude,
                                                           Double radiusKm, int maxResults) {
        
        validateKoreaCoordinates(latitude, longitude);
        
        if (radiusKm == null) {
            radiusKm = defaultSearchRadiusKm;
        }
        
        if (radiusKm <= 0 || radiusKm > maxSearchRadiusKm) {
            throw GymException.invalidSearchRadius(radiusKm, maxSearchRadiusKm);
        }
        
        // 커스텀 Repository 메서드로 거리 포함 검색
        return gymBranchRepository.findNearbyBranchesWithDistance(latitude, longitude, radiusKm, maxResults);
    }

    /**
     * 지역별 체육관 수 통계
     */
    @Cacheable(value = "regional-gym-stats", key = "#region")
    public RegionalGymStatsDto getRegionalGymStats(String region) {
        if (!StringUtils.hasText(region)) {
            throw GymException.invalidRegion(region);
        }
        
        region = XssProtectionUtil.cleanInput(region);
        
        long totalGymCount = gymRepository.countByAddressContainingAndDeletedFalse(region);
        long activeBranchCount = gymBranchRepository.countByAddressContainingAndStatusAndDeletedFalse(
            region, GymStatus.ACTIVE);
        long totalMemberCount = gymMemberRepository.countByRegionAndStatusAndDeletedFalse(
            region, MembershipStatus.ACTIVE);
        
        return RegionalGymStatsDto.builder()
            .region(region)
            .totalGymCount(totalGymCount)
            .activeBranchCount(activeBranchCount)
            .totalMemberCount(totalMemberCount)
            .build();
    }

    // ===== 체육관 회원 관리 =====

    /**
     * 체육관 회원 등록
     */
    @Transactional
    public GymMember registerGymMember(Long userId, Long gymId, LocalDateTime startDate,
                                     LocalDateTime endDate, String membershipType) {
        
        // 체육관 존재 검증
        Gym gym = getGymById(gymId);
        
        // 기존 활성 회원권 검증
        Optional<GymMember> existingMembership = gymMemberRepository
            .findByUserIdAndGymIdAndStatusAndDeletedFalse(userId, gymId, MembershipStatus.ACTIVE);
            
        if (existingMembership.isPresent()) {
            throw GymException.membershipAlreadyActive(userId, gymId);
        }
        
        // 회원권 기간 검증
        if (startDate.isAfter(endDate)) {
            throw GymException.invalidMembershipPeriod(startDate, endDate);
        }
        
        GymMember gymMember = GymMember.builder()
            .userId(userId)
            .gym(gym)
            .startDate(startDate)
            .endDate(endDate)
            .membershipType(membershipType)
            .status(MembershipStatus.ACTIVE)
            .build();
            
        GymMember savedMember = gymMemberRepository.save(gymMember);
        
        log.info("체육관 회원 등록 완료 - userId: {}, gymId: {}, membershipId: {}", 
                userId, gymId, savedMember.getId());
        return savedMember;
    }

    /**
     * 사용자의 체육관 회원권 조회
     */
    @Cacheable(value = "user-memberships", key = "#userId")
    public List<GymMember> getUserMemberships(Long userId) {
        return gymMemberRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * 활성 회원권만 조회
     */
    @Cacheable(value = "user-active-memberships", key = "#userId")
    public List<GymMember> getUserActiveMemberships(Long userId) {
        return gymMemberRepository.findByUserIdAndStatusAndDeletedFalse(userId, MembershipStatus.ACTIVE);
    }

    /**
     * 체육관 회원권 만료 처리
     */
    @Transactional
    @CacheEvict(value = {"user-memberships", "user-active-memberships"}, key = "#gymMember.userId")
    public void expireMembership(Long membershipId) {
        GymMember gymMember = gymMemberRepository.findByIdAndDeletedFalse(membershipId)
            .orElseThrow(() -> GymException.membershipNotFound(membershipId));
            
        gymMember.updateStatus(MembershipStatus.EXPIRED);
        
        log.info("체육관 회원권 만료 처리 - membershipId: {}", membershipId);
    }

    /**
     * 회원권 연장
     */
    @Transactional
    @CacheEvict(value = {"user-memberships", "user-active-memberships"}, key = "#gymMember.userId")
    public GymMember extendMembership(Long membershipId, LocalDateTime newEndDate) {
        GymMember gymMember = gymMemberRepository.findByIdAndDeletedFalse(membershipId)
            .orElseThrow(() -> GymException.membershipNotFound(membershipId));
        
        // 연장 기간 검증
        if (newEndDate.isBefore(gymMember.getEndDate())) {
            throw GymException.invalidExtensionPeriod(gymMember.getEndDate(), newEndDate);
        }
        
        gymMember.extendMembership(newEndDate);
        
        log.info("체육관 회원권 연장 완료 - membershipId: {}, newEndDate: {}", 
                membershipId, newEndDate);
        return gymMember;
    }

    /**
     * 만료 예정 회원권 조회 (배치 작업용)
     */
    public List<GymMember> getExpiringMemberships(int daysBeforeExpiry) {
        LocalDateTime expiryThreshold = LocalDateTime.now().plusDays(daysBeforeExpiry);
        return gymMemberRepository.findMembershipsExpiringBefore(expiryThreshold, MembershipStatus.ACTIVE);
    }

    /**
     * 체육관별 회원 수 조회
     */
    @Cacheable(value = "gym-member-count", key = "#gymId")
    public long getGymMemberCount(Long gymId) {
        return gymMemberRepository.countByGymIdAndStatusAndDeletedFalse(gymId, MembershipStatus.ACTIVE);
    }

    // ===== 통계 및 분석 =====

    /**
     * 체육관 통계 조회
     */
    @Cacheable(value = "gym-stats", key = "#gymId")
    public GymStatsDto getGymStats(Long gymId) {
        Gym gym = getGymById(gymId);
        
        long branchCount = gymBranchRepository.countByGymIdAndDeletedFalse(gymId);
        long activeMemberCount = gymMemberRepository.countByGymIdAndStatusAndDeletedFalse(
            gymId, MembershipStatus.ACTIVE);
        long totalWallCount = wallRepository.countByGymIdAndDeletedFalse(gymId);
        
        return GymStatsDto.builder()
            .gymId(gymId)
            .gymName(gym.getName())
            .branchCount(branchCount)
            .activeMemberCount(activeMemberCount)
            .totalWallCount(totalWallCount)
            .build();
    }

    /**
     * 체육관 종합 분석 리포트
     */
    @Cacheable(value = "gym-analysis-report", key = "#gymId + '_' + #reportDate.toLocalDate()")
    public GymAnalysisReportDto getGymAnalysisReport(Long gymId, LocalDateTime reportDate) {
        Gym gym = getGymById(gymId);
        
        // 기본 통계
        GymStatsDto basicStats = getGymStats(gymId);
        
        // 회원권 유형별 통계
        Map<String, Long> membershipTypeStats = gymMemberRepository
            .findMembershipTypeStatsForGym(gymId, MembershipStatus.ACTIVE);
        
        // 월별 신규 회원 통계 (최근 12개월)
        LocalDateTime oneYearAgo = reportDate.minusMonths(12);
        List<MonthlyMemberStatsDto> monthlyStats = gymMemberRepository
            .findMonthlyMemberStats(gymId, oneYearAgo, reportDate);
        
        // 지점별 회원 분포
        Map<Long, Long> branchMemberDistribution = gymMemberRepository
            .findMemberDistributionByBranch(gymId, MembershipStatus.ACTIVE);
        
        return GymAnalysisReportDto.builder()
            .gymId(gymId)
            .gymName(gym.getName())
            .reportDate(reportDate)
            .basicStats(basicStats)
            .membershipTypeStats(membershipTypeStats)
            .monthlyMemberStats(monthlyStats)
            .branchMemberDistribution(branchMemberDistribution)
            .totalRevenue(calculateTotalRevenue(gymId, oneYearAgo, reportDate))
            .build();
    }

    /**
     * 체육관 수익 계산 (추정)
     */
    private BigDecimal calculateTotalRevenue(Long gymId, LocalDateTime startDate, LocalDateTime endDate) {
        // 실제 결제 시스템과 연동하여 정확한 수익 계산
        // 여기서는 회원권 기반 추정 계산
        List<GymMember> membersInPeriod = gymMemberRepository
            .findMembersRegisteredBetween(gymId, startDate, endDate);
        
        // 회원권 타입별 평균 가격 (설정 파일에서 가져오거나 별도 테이블 관리)
        Map<String, BigDecimal> membershipPrices = getMembershipPrices();
        
        return membersInPeriod.stream()
            .map(member -> membershipPrices.getOrDefault(member.getMembershipType(), BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 회원권 가격 정보 조회 (설정 기반)
     */
    private Map<String, BigDecimal> getMembershipPrices() {
        // 실제 구현에서는 설정 파일이나 별도 테이블에서 관리
        Map<String, BigDecimal> prices = new HashMap<>();
        prices.put("MONTHLY", new BigDecimal("100000"));
        prices.put("QUARTERLY", new BigDecimal("270000"));
        prices.put("YEARLY", new BigDecimal("1000000"));
        prices.put("DAY_PASS", new BigDecimal("15000"));
        return prices;
    }

    // ===== DTO 클래스 =====

    /**
     * 주변 체육관 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class NearbyGymDto {
        private final Long branchId;
        private final Long gymId;
        private final String gymName;
        private final String branchName;
        private final String address;
        private final BigDecimal latitude;
        private final BigDecimal longitude;
        private final Double distanceKm;
        private final GymStatus status;
        private final LocalTime openTime;
        private final LocalTime closeTime;
        private final boolean currentlyOpen;
    }

    /**
     * 지역별 체육관 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class RegionalGymStatsDto {
        private final String region;
        private final long totalGymCount;
        private final long activeBranchCount;
        private final long totalMemberCount;
    }

    /**
     * 체육관 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class GymStatsDto {
        private final Long gymId;
        private final String gymName;
        private final long branchCount;
        private final long activeMemberCount;
        private final long totalWallCount;
    }

    /**
     * 체육관 분석 리포트 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class GymAnalysisReportDto {
        private final Long gymId;
        private final String gymName;
        private final LocalDateTime reportDate;
        private final GymStatsDto basicStats;
        private final Map<String, Long> membershipTypeStats;
        private final List<MonthlyMemberStatsDto> monthlyMemberStats;
        private final Map<Long, Long> branchMemberDistribution;
        private final BigDecimal totalRevenue;
    }

    /**
     * 월별 회원 통계 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class MonthlyMemberStatsDto {
        private final int year;
        private final int month;
        private final long newMemberCount;
        private final long expiredMemberCount;
        private final long netGrowth;
    }
}
```

---

## 🗺️ 공간 쿼리 및 위치 기반 검색

### 📍 **1. 주변 검색 시스템**
- **공간 쿼리**: MySQL ST_Distance_Sphere 함수 활용
- **반경 제한**: 기본 10km, 최대 50km 검색 범위
- **거리 계산**: 정확한 거리 포함 결과 제공
- **좌표 검증**: 한국 영토 내 좌표만 허용

### 🎯 **2. 고급 검색 기능**
- **상세 정보**: 거리, 운영시간, 현재 운영상태 포함
- **지역별 통계**: 특정 지역 체육관 현황 분석
- **캐싱 최적화**: 위치별 검색 결과 캐싱

---

## 👥 회원 관리 시스템

### 🎫 **1. 회원권 관리**
- **등록**: 체육관별 회원권 등록 및 검증
- **중복 방지**: 동일 체육관 활성 회원권 중복 방지
- **기간 관리**: 시작일/종료일 기반 유효성 검증
- **상태 관리**: ACTIVE, EXPIRED, SUSPENDED 상태

### 📅 **2. 회원권 라이프사이클**
- **연장**: 기존 회원권 기간 연장
- **만료**: 자동/수동 만료 처리
- **알림**: 만료 예정 회원권 배치 조회
- **통계**: 회원권 타입별, 기간별 분석

---

## 📊 통계 및 분석 시스템

### 📈 **1. 기본 통계**
- **체육관 현황**: 지점 수, 회원 수, 벽면 수
- **회원 분석**: 회원권 타입별 분포
- **지점 분석**: 지점별 회원 분포

### 📋 **2. 종합 분석 리포트**
- **월별 트렌드**: 신규/만료 회원 추이
- **수익 분석**: 회원권 기반 수익 추정
- **운영 지표**: 체육관 운영 효율성 분석
- **성장 분석**: 순증 회원 수 추적

---

## 🛡️ 성능 및 보안 최적화

### 💾 **캐싱 전략**
- **위치 기반**: `nearby-branches` - 위치별 검색 결과
- **사용자 기반**: `user-memberships` - 사용자 회원권 정보
- **통계 기반**: `gym-stats` - 체육관 통계 정보
- **분석 리포트**: `gym-analysis-report` - 일별 분석 캐싱

### ⚡ **성능 최적화**
- **공간 인덱스**: MySQL Spatial Index 활용
- **배치 처리**: 만료 예정 회원권 배치 조회
- **페이징**: 대용량 검색 결과 페이징 처리
- **쿼리 최적화**: 복잡한 통계 쿼리 최적화

---

## 🚀 활용 시나리오

### 🔍 **위치 기반 서비스**
- 사용자 현재 위치 기준 주변 체육관 추천
- 지역별 체육관 밀도 분석
- 접근성 기반 체육관 선택 지원

### 👥 **회원 관리 자동화**
- 만료 예정 회원권 자동 알림
- 회원권 갱신 추천 시스템
- 체육관별 회원 현황 모니터링

### 📊 **데이터 드리븐 운영**
- 체육관 운영 효율성 분석
- 수익 최적화 인사이트 제공
- 확장 전략 수립 지원

*step6-2a2 완성: 체육관 공간쿼리 및 회원관리 설계 완료*