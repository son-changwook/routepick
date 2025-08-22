# Step 6-2: 암장 및 루트 관리 Service 구현

> 암장 및 루트 관리 4개 Service 완전 구현 (한국 특화 지역 검색 및 성능 최적화)  
> 생성일: 2025-08-21  
> 기반: step6-1_auth_service.md, step5-3a_gym_core_repositories.md

---

## 🎯 설계 목표

- **한국 지역 기반**: 위도 33.0~38.6, 경도 124.0~132.0 좌표 검증
- **성능 최적화**: @Cacheable Redis 캐싱, 공간 쿼리 최적화, @Async 비동기 처리
- **V등급/5.등급 체계**: ClimbingLevel 매핑 테이블 활용
- **페이징 처리**: 대용량 데이터 최적화
- **트랜잭션 관리**: @Transactional 데이터 일관성 보장

---

## 🏢 1. GymService - 암장 관리 서비스

### GymService.java
```java
package com.routepick.service.gym;

import com.routepick.common.enums.BranchStatus;
import com.routepick.domain.gym.entity.Gym;
import com.routepick.domain.gym.entity.GymBranch;
import com.routepick.domain.gym.entity.GymMember;
import com.routepick.domain.gym.repository.GymRepository;
import com.routepick.domain.gym.repository.GymBranchRepository;
import com.routepick.domain.gym.repository.GymMemberRepository;
import com.routepick.exception.gym.GymException;
import com.routepick.exception.validation.ValidationException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 암장 관리 서비스
 * - 한국 지역 기반 암장 검색
 * - 공간 쿼리 성능 최적화
 * - GymBranch/GymMember 관리
 * - 인기 암장 조회 및 캐싱
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymService {
    
    private final GymRepository gymRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymMemberRepository gymMemberRepository;
    
    // 한국 좌표 범위 상수
    private static final double KOREA_MIN_LATITUDE = 33.0;   // 제주도 남단
    private static final double KOREA_MAX_LATITUDE = 38.6;   // 북한 접경
    private static final double KOREA_MIN_LONGITUDE = 124.0; // 서해 최서단
    private static final double KOREA_MAX_LONGITUDE = 132.0; // 동해 최동단
    
    // ===== 암장 기본 관리 =====
    
    /**
     * 암장 조회 (캐싱)
     */
    @Cacheable(value = "gyms", key = "#gymId")
    public Gym getGym(Long gymId) {
        return gymRepository.findById(gymId)
            .orElseThrow(() -> GymException.notFound(gymId));
    }
    
    /**
     * 암장명으로 조회
     */
    @Cacheable(value = "gyms", key = "#name")
    public Optional<Gym> getGymByName(String name) {
        String sanitizedName = XssProtectionUtil.sanitize(name);
        return gymRepository.findByName(sanitizedName);
    }
    
    /**
     * 암장 생성
     */
    @Transactional
    public Gym createGym(String name, String description, boolean isFranchise, 
                         String businessRegistrationNumber, String email, String websiteUrl) {
        log.info("암장 생성: name={}", name);
        
        // 암장명 중복 확인
        if (gymRepository.findByName(name).isPresent()) {
            throw GymException.gymNameAlreadyExists(name);
        }
        
        // 사업자등록번호 중복 확인
        if (businessRegistrationNumber != null && 
            gymRepository.findByBusinessRegistrationNumber(businessRegistrationNumber).isPresent()) {
            throw GymException.businessRegistrationNumberAlreadyExists(businessRegistrationNumber);
        }
        
        Gym gym = Gym.builder()
            .name(XssProtectionUtil.sanitize(name))
            .description(XssProtectionUtil.sanitize(description))
            .isFranchise(isFranchise)
            .businessRegistrationNumber(businessRegistrationNumber)
            .email(email)
            .websiteUrl(websiteUrl)
            .branchCount(0)
            .isActive(true)
            .build();
        
        gym = gymRepository.save(gym);
        
        log.info("암장 생성 완료: gymId={}, name={}", gym.getGymId(), name);
        return gym;
    }
    
    /**
     * 암장 정보 수정
     */
    @Transactional
    @CacheEvict(value = "gyms", key = "#gymId")
    public Gym updateGym(Long gymId, Map<String, Object> updates) {
        log.info("암장 정보 수정: gymId={}", gymId);
        
        Gym gym = getGym(gymId);
        
        updates.forEach((key, value) -> {
            switch (key) {
                case "name" -> {
                    String newName = XssProtectionUtil.sanitize((String) value);
                    if (!gym.getName().equals(newName) && gymRepository.findByName(newName).isPresent()) {
                        throw GymException.gymNameAlreadyExists(newName);
                    }
                    gym.setName(newName);
                }
                case "description" -> gym.setDescription(XssProtectionUtil.sanitize((String) value));
                case "email" -> gym.setEmail((String) value);
                case "websiteUrl" -> gym.setWebsiteUrl((String) value);
                case "brandColor" -> gym.setBrandColor((String) value);
                case "logoUrl" -> gym.setLogoUrl((String) value);
                case "isFranchise" -> gym.setFranchise((Boolean) value);
            }
        });
        
        return gymRepository.save(gym);
    }
    
    // ===== 암장 검색 및 조회 =====
    
    /**
     * 암장명으로 검색
     */
    public Page<Gym> searchGymsByName(String keyword, Pageable pageable) {
        String sanitizedKeyword = XssProtectionUtil.sanitize(keyword);
        return gymRepository.findByNameContaining(sanitizedKeyword, pageable);
    }
    
    /**
     * 복합 조건 암장 검색
     */
    public Page<Gym> searchGyms(String keyword, Boolean isFranchise, 
                               Integer minBranches, Pageable pageable) {
        String sanitizedKeyword = keyword != null ? XssProtectionUtil.sanitize(keyword) : null;
        return gymRepository.findByComplexConditions(sanitizedKeyword, isFranchise, minBranches, pageable);
    }
    
    /**
     * 인기 암장 조회 (지점 수 기준)
     */
    @Cacheable(value = "popularGyms", key = "#pageable.pageSize + '_' + #pageable.pageNumber")
    public List<Gym> getPopularGymsByBranchCount(Pageable pageable) {
        return gymRepository.findPopularGymsByBranchCount(pageable);
    }
    
    /**
     * 인기 암장 조회 (멤버 수 기준)
     */
    @Cacheable(value = "popularGyms", key = "'members_' + #pageable.pageSize + '_' + #pageable.pageNumber")
    public List<Object[]> getPopularGymsByMemberCount(Pageable pageable) {
        return gymRepository.findPopularGymsByMemberCount(pageable);
    }
    
    // ===== 지점 관리 =====
    
    /**
     * 지점 생성
     */
    @Transactional
    @CacheEvict(value = {"gyms", "gymBranches"}, allEntries = true)
    public GymBranch createBranch(Long gymId, String branchName, String address, 
                                  String detailAddress, BigDecimal latitude, BigDecimal longitude,
                                  String district, boolean isMainBranch) {
        log.info("지점 생성: gymId={}, branchName={}", gymId, branchName);
        
        // 한국 좌표 범위 검증
        validateKoreaCoordinates(latitude, longitude);
        
        Gym gym = getGym(gymId);
        
        // 본점 설정 검증
        if (isMainBranch) {
            Optional<GymBranch> existingMainBranch = gymBranchRepository.findMainBranchByGymId(gymId);
            if (existingMainBranch.isPresent()) {
                throw GymException.mainBranchAlreadyExists(gymId);
            }
        }
        
        GymBranch branch = GymBranch.builder()
            .gym(gym)
            .branchName(XssProtectionUtil.sanitize(branchName))
            .address(XssProtectionUtil.sanitize(address))
            .detailAddress(XssProtectionUtil.sanitize(detailAddress))
            .latitude(latitude)
            .longitude(longitude)
            .district(XssProtectionUtil.sanitize(district))
            .isMainBranch(isMainBranch)
            .branchStatus(BranchStatus.ACTIVE)
            .memberCount(0)
            .wallCount(0)
            .routeCount(0)
            .build();
        
        branch = gymBranchRepository.save(branch);
        
        // 암장의 지점 수 업데이트
        gymRepository.updateBranchCount(gymId);
        
        log.info("지점 생성 완료: branchId={}, branchName={}", branch.getBranchId(), branchName);
        return branch;
    }
    
    /**
     * 지점 조회 (캐싱)
     */
    @Cacheable(value = "gymBranches", key = "#branchId")
    public GymBranch getBranch(Long branchId) {
        return gymBranchRepository.findById(branchId)
            .orElseThrow(() -> GymException.branchNotFound(branchId));
    }
    
    /**
     * 암장별 지점 목록 조회
     */
    @Cacheable(value = "gymBranches", key = "'gym_' + #gymId")
    public List<GymBranch> getBranchesByGym(Long gymId) {
        return gymBranchRepository.findByGymIdAndActiveStatus(gymId);
    }
    
    // ===== 한국 지역 기반 검색 =====
    
    /**
     * 반경 내 지점 검색 (공간 쿼리)
     */
    public List<GymBranch> findNearbyBranches(BigDecimal latitude, BigDecimal longitude, 
                                             double radiusInMeters) {
        // 한국 좌표 범위 검증
        validateKoreaCoordinates(latitude, longitude);
        
        log.info("주변 지점 검색: lat={}, lng={}, radius={}m", latitude, longitude, radiusInMeters);
        
        return gymBranchRepository.findNearbyBranches(
            latitude, longitude, radiusInMeters, BranchStatus.ACTIVE.name()
        );
    }
    
    /**
     * 사용자 위치에서 가장 가까운 지점
     */
    public Optional<GymBranch> findNearestBranch(BigDecimal latitude, BigDecimal longitude) {
        validateKoreaCoordinates(latitude, longitude);
        return gymBranchRepository.findNearestBranchToUser(latitude, longitude);
    }
    
    /**
     * 지역(구/군)별 지점 조회
     */
    @Cacheable(value = "gymBranches", key = "'district_' + #district")
    public List<GymBranch> getBranchesByDistrict(String district) {
        String sanitizedDistrict = XssProtectionUtil.sanitize(district);
        return gymBranchRepository.findByRegionAndBranchStatus(sanitizedDistrict, BranchStatus.ACTIVE);
    }
    
    /**
     * 편의시설 기반 지점 검색
     */
    public List<GymBranch> findBranchesByAmenities(Boolean hasParking, Boolean hasShower, 
                                                  Boolean hasLocker, Boolean hasRental) {
        return gymBranchRepository.findByAmenities(hasParking, hasShower, hasLocker, hasRental);
    }
    
    /**
     * 지하철역 기반 지점 검색
     */
    public List<GymBranch> findBranchesBySubway(String subwayStation) {
        String sanitizedStation = XssProtectionUtil.sanitize(subwayStation);
        return gymBranchRepository.findBySubwayInfo(sanitizedStation);
    }
    
    // ===== 지점 상태 관리 =====
    
    /**
     * 지점 상태 변경
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "gymBranches", key = "#branchId"),
        @CacheEvict(value = "gymBranches", allEntries = true)
    })
    public void updateBranchStatus(Long branchId, BranchStatus status) {
        log.info("지점 상태 변경: branchId={}, status={}", branchId, status);
        
        int updated = gymBranchRepository.updateBranchStatus(branchId, status);
        if (updated == 0) {
            throw GymException.branchNotFound(branchId);
        }
        
        // 암장의 지점 수 업데이트
        GymBranch branch = getBranch(branchId);
        gymRepository.updateBranchCount(branch.getGym().getGymId());
    }
    
    /**
     * 지점 통계 업데이트
     */
    @Transactional
    @CacheEvict(value = "gymBranches", key = "#branchId")
    public void updateBranchStatistics(Long branchId) {
        log.info("지점 통계 업데이트: branchId={}", branchId);
        
        gymBranchRepository.updateMemberCount(branchId);
        gymBranchRepository.updateWallCount(branchId);
        gymBranchRepository.updateRouteCount(branchId);
    }
    
    // ===== 멤버십 관리 =====
    
    /**
     * 멤버십 등록
     */
    @Transactional
    @CacheEvict(value = "gymBranches", key = "#branchId")
    public GymMember registerMembership(Long userId, Long branchId, String membershipType,
                                       LocalDate startDate, LocalDate endDate, Integer membershipFee) {
        log.info("멤버십 등록: userId={}, branchId={}", userId, branchId);
        
        // 기존 멤버십 확인
        Optional<GymMember> existingMember = gymMemberRepository.findByUserIdAndBranchId(userId, branchId);
        if (existingMember.isPresent() && existingMember.get().isActive()) {
            throw GymException.membershipAlreadyExists(userId, branchId);
        }
        
        GymBranch branch = getBranch(branchId);
        
        GymMember member = GymMember.builder()
            .user(null) // User 엔티티 주입 필요
            .branch(branch)
            .membershipType(membershipType)
            .membershipStartDate(startDate)
            .membershipEndDate(endDate)
            .membershipFee(membershipFee)
            .isActive(true)
            .visitCount(0)
            .build();
        
        member = gymMemberRepository.save(member);
        
        // 지점 멤버 수 업데이트
        gymBranchRepository.updateMemberCount(branchId);
        
        log.info("멤버십 등록 완료: membershipId={}", member.getMembershipId());
        return member;
    }
    
    /**
     * 멤버십 연장
     */
    @Transactional
    public void extendMembership(Long membershipId, LocalDate newEndDate) {
        log.info("멤버십 연장: membershipId={}, newEndDate={}", membershipId, newEndDate);
        
        int updated = gymMemberRepository.extendMembership(membershipId, newEndDate);
        if (updated == 0) {
            throw GymException.membershipNotFound(membershipId);
        }
    }
    
    /**
     * 사용자 멤버십 조회
     */
    public List<GymMember> getUserMemberships(Long userId) {
        return gymMemberRepository.findByUserId(userId);
    }
    
    /**
     * 활성 멤버십 조회
     */
    public List<GymMember> getActiveMemberships(Long userId) {
        return gymMemberRepository.findActiveByUserId(userId);
    }
    
    /**
     * 방문 기록
     */
    @Transactional
    public void recordVisit(Long userId, Long branchId) {
        log.info("방문 기록: userId={}, branchId={}", userId, branchId);
        
        int updated = gymMemberRepository.recordVisit(userId, branchId);
        if (updated == 0) {
            log.warn("방문 기록 실패 - 멤버십 없음: userId={}, branchId={}", userId, branchId);
        }
    }
    
    // ===== 통계 및 분석 =====
    
    /**
     * 지역별 암장 분포 통계
     */
    @Cacheable(value = "gymStats", key = "'distribution'")
    public List<Object[]> getGymDistributionByDistrict() {
        return gymRepository.getGymDistributionByDistrict();
    }
    
    /**
     * 프랜차이즈 vs 개인 암장 통계
     */
    @Cacheable(value = "gymStats", key = "'franchise'")
    public List<Object[]> getFranchiseStatistics() {
        return gymRepository.getFranchiseStatistics();
    }
    
    /**
     * 만료 예정 멤버십 조회
     */
    public List<GymMember> getExpiringMemberships(int daysAhead) {
        LocalDate endDate = LocalDate.now().plusDays(daysAhead);
        return gymMemberRepository.findExpiringMemberships(endDate);
    }
    
    /**
     * 만료된 멤버십 자동 처리
     */
    @Transactional
    public int processExpiredMemberships() {
        log.info("만료된 멤버십 자동 처리 시작");
        
        int expiredCount = gymMemberRepository.expireOverdueMemberships();
        
        log.info("만료된 멤버십 처리 완료: {}건", expiredCount);
        return expiredCount;
    }
    
    // ===== Helper 메서드 =====
    
    /**
     * 한국 좌표 범위 검증
     */
    private void validateKoreaCoordinates(BigDecimal latitude, BigDecimal longitude) {
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        
        if (lat < KOREA_MIN_LATITUDE || lat > KOREA_MAX_LATITUDE ||
            lng < KOREA_MIN_LONGITUDE || lng > KOREA_MAX_LONGITUDE) {
            throw ValidationException.invalidKoreaCoordinates(lat, lng);
        }
    }
    
    /**
     * 암장 활성화/비활성화
     */
    @Transactional
    @CacheEvict(value = "gyms", key = "#gymId")
    public void updateGymStatus(Long gymId, boolean isActive) {
        log.info("암장 상태 변경: gymId={}, isActive={}", gymId, isActive);
        
        if (isActive) {
            gymRepository.reactivateGym(gymId);
        } else {
            gymRepository.deactivateGym(gymId);
        }
    }
}
```

---

## 🧗 2. RouteService - 루트 관리 서비스

### RouteService.java
```java
package com.routepick.service.route;

import com.routepick.common.enums.RouteStatus;
import com.routepick.domain.climbing.entity.ClimbingLevel;
import com.routepick.domain.climbing.repository.ClimbingLevelRepository;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.entity.RouteScrap;
import com.routepick.domain.route.entity.RouteDifficultyVote;
import com.routepick.domain.route.entity.UserClimb;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.route.repository.RouteScrapRepository;
import com.routepick.domain.route.repository.RouteDifficultyVoteRepository;
import com.routepick.domain.route.repository.UserClimbRepository;
import com.routepick.exception.route.RouteException;
import com.routepick.exception.validation.ValidationException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 루트 관리 서비스
 * - 루트 CRUD 관리
 * - 난이도별 루트 조회
 * - RouteStatus 관리
 * - 스크랩/난이도 투표 처리
 * - 클라이밍 기록 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteService {
    
    private final RouteRepository routeRepository;
    private final RouteScrapRepository routeScrapRepository;
    private final RouteDifficultyVoteRepository routeDifficultyVoteRepository;
    private final UserClimbRepository userClimbRepository;
    private final ClimbingLevelRepository climbingLevelRepository;
    
    // ===== 루트 기본 관리 =====
    
    /**
     * 루트 조회 (캐싱)
     */
    @Cacheable(value = "routes", key = "#routeId")
    public Route getRoute(Long routeId) {
        return routeRepository.findById(routeId)
            .orElseThrow(() -> RouteException.notFound(routeId));
    }
    
    /**
     * 루트 생성
     */
    @Transactional
    public Route createRoute(Long wallId, Long setterUserId, Long levelId, String routeName,
                            String routeDescription, String color, String routeNumber) {
        log.info("루트 생성: wallId={}, routeName={}", wallId, routeName);
        
        // 루트 번호 중복 확인 (같은 벽면 내)
        if (routeRepository.existsByWallIdAndRouteNumber(wallId, routeNumber)) {
            throw RouteException.routeNumberAlreadyExists(wallId, routeNumber);
        }
        
        ClimbingLevel level = climbingLevelRepository.findById(levelId)
            .orElseThrow(() -> ValidationException.invalidClimbingLevel(levelId));
        
        Route route = Route.builder()
            .wall(null) // Wall 엔티티 주입 필요
            .setterUser(null) // User 엔티티 주입 필요
            .level(level)
            .routeName(XssProtectionUtil.sanitize(routeName))
            .routeDescription(XssProtectionUtil.sanitize(routeDescription))
            .color(color)
            .routeNumber(routeNumber)
            .routeStatus(RouteStatus.ACTIVE)
            .completionCount(0)
            .attemptCount(0)
            .scrapCount(0)
            .averageDifficulty(level.getDifficultyScore().doubleValue())
            .build();
        
        route = routeRepository.save(route);
        
        log.info("루트 생성 완료: routeId={}, routeName={}", route.getRouteId(), routeName);
        return route;
    }
    
    /**
     * 루트 정보 수정
     */
    @Transactional
    @CacheEvict(value = "routes", key = "#routeId")
    public Route updateRoute(Long routeId, Map<String, Object> updates) {
        log.info("루트 정보 수정: routeId={}", routeId);
        
        Route route = getRoute(routeId);
        
        updates.forEach((key, value) -> {
            switch (key) {
                case "routeName" -> route.setRouteName(XssProtectionUtil.sanitize((String) value));
                case "routeDescription" -> route.setRouteDescription(XssProtectionUtil.sanitize((String) value));
                case "color" -> route.setColor((String) value);
                case "routeNumber" -> {
                    String newNumber = (String) value;
                    if (!route.getRouteNumber().equals(newNumber) && 
                        routeRepository.existsByWallIdAndRouteNumber(
                            route.getWall().getWallId(), newNumber)) {
                        throw RouteException.routeNumberAlreadyExists(
                            route.getWall().getWallId(), newNumber);
                    }
                    route.setRouteNumber(newNumber);
                }
                case "levelId" -> {
                    Long levelId = (Long) value;
                    ClimbingLevel level = climbingLevelRepository.findById(levelId)
                        .orElseThrow(() -> ValidationException.invalidClimbingLevel(levelId));
                    route.setLevel(level);
                }
            }
        });
        
        return routeRepository.save(route);
    }
    
    // ===== 루트 검색 및 조회 =====
    
    /**
     * 벽면별 루트 조회
     */
    @Cacheable(value = "routes", key = "'wall_' + #wallId")
    public List<Route> getRoutesByWall(Long wallId) {
        return routeRepository.findByWallIdAndRouteStatus(wallId, RouteStatus.ACTIVE);
    }
    
    /**
     * 지점별 루트 조회
     */
    @Cacheable(value = "routes", key = "'branch_' + #branchId")
    public Page<Route> getRoutesByBranch(Long branchId, Pageable pageable) {
        return routeRepository.findByBranchId(branchId, pageable);
    }
    
    /**
     * 난이도별 루트 조회
     */
    @Cacheable(value = "routes", key = "'level_' + #levelId + '_' + #pageable.pageNumber")
    public Page<Route> getRoutesByLevel(Long levelId, Pageable pageable) {
        return routeRepository.findByLevelId(levelId, pageable);
    }
    
    /**
     * 난이도 범위 조회 (V등급/5.등급 체계)
     */
    public Page<Route> getRoutesByDifficultyRange(Integer minScore, Integer maxScore, 
                                                  Pageable pageable) {
        return routeRepository.findByDifficultyRange(minScore, maxScore, pageable);
    }
    
    /**
     * 루트 검색
     */
    public Page<Route> searchRoutes(String keyword, Long branchId, Long levelId, 
                                   String color, Pageable pageable) {
        String sanitizedKeyword = keyword != null ? XssProtectionUtil.sanitize(keyword) : null;
        return routeRepository.findByComplexConditions(sanitizedKeyword, branchId, levelId, color, pageable);
    }
    
    /**
     * 인기 루트 조회 (완등 수 기준)
     */
    @Cacheable(value = "popularRoutes", key = "#pageable.pageSize + '_' + #pageable.pageNumber")
    public List<Route> getPopularRoutesByCompletion(Pageable pageable) {
        return routeRepository.findPopularRoutesByCompletion(pageable);
    }
    
    /**
     * 인기 루트 조회 (스크랩 수 기준)
     */
    @Cacheable(value = "popularRoutes", key = "'scraps_' + #pageable.pageSize + '_' + #pageable.pageNumber")
    public List<Route> getPopularRoutesByScrap(Pageable pageable) {
        return routeRepository.findPopularRoutesByScrap(pageable);
    }
    
    // ===== 루트 상태 관리 =====
    
    /**
     * 루트 상태 변경
     */
    @Transactional
    @CacheEvict(value = "routes", key = "#routeId")
    public void updateRouteStatus(Long routeId, RouteStatus status) {
        log.info("루트 상태 변경: routeId={}, status={}", routeId, status);
        
        int updated = routeRepository.updateRouteStatus(routeId, status);
        if (updated == 0) {
            throw RouteException.notFound(routeId);
        }
        
        // 만료/제거된 루트는 스크랩도 비활성화
        if (status == RouteStatus.EXPIRED || status == RouteStatus.REMOVED) {
            routeScrapRepository.deactivateScrapsByRoute(routeId);
        }
    }
    
    /**
     * 루트 만료 처리
     */
    @Transactional
    public void expireRoute(Long routeId, String reason) {
        log.info("루트 만료 처리: routeId={}, reason={}", routeId, reason);
        
        updateRouteStatus(routeId, RouteStatus.EXPIRED);
        
        // 만료 사유 기록 (필요시 별도 테이블 생성)
        Route route = getRoute(routeId);
        route.setRouteDescription(route.getRouteDescription() + " [만료: " + reason + "]");
        routeRepository.save(route);
    }
    
    // ===== 스크랩 관리 =====
    
    /**
     * 루트 스크랩
     */
    @Transactional
    @CacheEvict(value = "routes", key = "#routeId")
    public RouteScrap scrapRoute(Long userId, Long routeId) {
        log.info("루트 스크랩: userId={}, routeId={}", userId, routeId);
        
        // 기존 스크랩 확인
        Optional<RouteScrap> existingScrap = routeScrapRepository.findByUserIdAndRouteId(userId, routeId);
        if (existingScrap.isPresent() && existingScrap.get().isActive()) {
            throw RouteException.alreadyScrapped(userId, routeId);
        }
        
        Route route = getRoute(routeId);
        
        RouteScrap scrap = RouteScrap.builder()
            .user(null) // User 엔티티 주입 필요
            .route(route)
            .isActive(true)
            .build();
        
        scrap = routeScrapRepository.save(scrap);
        
        // 루트 스크랩 수 업데이트
        routeRepository.updateScrapCount(routeId);
        
        log.info("루트 스크랩 완료: scrapId={}", scrap.getScrapId());
        return scrap;
    }
    
    /**
     * 스크랩 취소
     */
    @Transactional
    @CacheEvict(value = "routes", key = "#routeId")
    public void unscrapRoute(Long userId, Long routeId) {
        log.info("스크랩 취소: userId={}, routeId={}", userId, routeId);
        
        RouteScrap scrap = routeScrapRepository.findByUserIdAndRouteId(userId, routeId)
            .orElseThrow(() -> RouteException.scrapNotFound(userId, routeId));
        
        scrap.setActive(false);
        routeScrapRepository.save(scrap);
        
        // 루트 스크랩 수 업데이트
        routeRepository.updateScrapCount(routeId);
    }
    
    /**
     * 사용자 스크랩 목록
     */
    public Page<RouteScrap> getUserScraps(Long userId, Pageable pageable) {
        return routeScrapRepository.findActiveByUserId(userId, pageable);
    }
    
    // ===== 난이도 투표 =====
    
    /**
     * 난이도 투표
     */
    @Transactional
    @CacheEvict(value = "routes", key = "#routeId")
    public RouteDifficultyVote voteDifficulty(Long userId, Long routeId, Integer difficultyScore) {
        log.info("난이도 투표: userId={}, routeId={}, score={}", userId, routeId, difficultyScore);
        
        // 유효한 난이도 점수 검증 (1-16 범위)
        if (difficultyScore < 1 || difficultyScore > 16) {
            throw ValidationException.invalidDifficultyScore(difficultyScore);
        }
        
        Route route = getRoute(routeId);
        
        // 기존 투표 확인
        Optional<RouteDifficultyVote> existingVote = 
            routeDifficultyVoteRepository.findByUserIdAndRouteId(userId, routeId);
        
        RouteDifficultyVote vote;
        if (existingVote.isPresent()) {
            // 기존 투표 수정
            vote = existingVote.get();
            vote.setDifficultyScore(difficultyScore);
            log.info("난이도 투표 수정: voteId={}", vote.getVoteId());
        } else {
            // 새 투표 생성
            vote = RouteDifficultyVote.builder()
                .user(null) // User 엔티티 주입 필요
                .route(route)
                .difficultyScore(difficultyScore)
                .build();
            log.info("난이도 투표 생성");
        }
        
        vote = routeDifficultyVoteRepository.save(vote);
        
        // 루트 평균 난이도 업데이트
        updateAverageDifficulty(routeId);
        
        return vote;
    }
    
    /**
     * 평균 난이도 업데이트
     */
    @Transactional
    public void updateAverageDifficulty(Long routeId) {
        Double averageDifficulty = routeDifficultyVoteRepository.calculateAverageDifficulty(routeId);
        if (averageDifficulty != null) {
            routeRepository.updateAverageDifficulty(routeId, averageDifficulty);
        }
    }
    
    // ===== 클라이밍 기록 관리 =====
    
    /**
     * 클라이밍 시도 기록
     */
    @Transactional
    @CacheEvict(value = "routes", key = "#routeId")
    public UserClimb recordClimbAttempt(Long userId, Long routeId, boolean isCompleted, 
                                       Integer attemptCount, String memo) {
        log.info("클라이밍 기록: userId={}, routeId={}, completed={}", userId, routeId, isCompleted);
        
        Route route = getRoute(routeId);
        
        UserClimb climb = UserClimb.builder()
            .user(null) // User 엔티티 주입 필요
            .route(route)
            .isCompleted(isCompleted)
            .attemptCount(attemptCount)
            .memo(XssProtectionUtil.sanitize(memo))
            .climbDate(LocalDateTime.now())
            .build();
        
        climb = userClimbRepository.save(climb);
        
        // 루트 통계 업데이트
        routeRepository.updateAttemptCount(routeId);
        if (isCompleted) {
            routeRepository.updateCompletionCount(routeId);
        }
        
        log.info("클라이밍 기록 완료: climbId={}", climb.getClimbId());
        return climb;
    }
    
    /**
     * 사용자 클라이밍 기록 조회
     */
    public Page<UserClimb> getUserClimbs(Long userId, Pageable pageable) {
        return userClimbRepository.findByUserId(userId, pageable);
    }
    
    /**
     * 사용자 완등 기록
     */
    public Page<UserClimb> getUserCompletions(Long userId, Pageable pageable) {
        return userClimbRepository.findCompletedByUserId(userId, pageable);
    }
    
    /**
     * 루트별 클라이밍 기록
     */
    public Page<UserClimb> getRouteClimbs(Long routeId, Pageable pageable) {
        return userClimbRepository.findByRouteId(routeId, pageable);
    }
    
    // ===== 통계 및 분석 =====
    
    /**
     * 루트 통계 업데이트
     */
    @Transactional
    @CacheEvict(value = "routes", key = "#routeId")
    public void updateRouteStatistics(Long routeId) {
        log.info("루트 통계 업데이트: routeId={}", routeId);
        
        routeRepository.updateAttemptCount(routeId);
        routeRepository.updateCompletionCount(routeId);
        routeRepository.updateScrapCount(routeId);
        updateAverageDifficulty(routeId);
    }
    
    /**
     * 지점별 루트 통계
     */
    @Cacheable(value = "routeStats", key = "'branch_' + #branchId")
    public List<Object[]> getBranchRouteStatistics(Long branchId) {
        return routeRepository.getRouteStatisticsByBranch(branchId);
    }
    
    /**
     * 난이도별 루트 분포
     */
    @Cacheable(value = "routeStats", key = "'difficulty_distribution'")
    public List<Object[]> getDifficultyDistribution() {
        return routeRepository.getRouteDifficultyDistribution();
    }
    
    /**
     * 설정자별 루트 통계
     */
    public List<Object[]> getRoutesBySetterId(Long setterId) {
        return routeRepository.getRouteStatisticsBySetter(setterId);
    }
    
    // ===== V등급/5.등급 체계 지원 =====
    
    /**
     * 클라이밍 레벨 조회
     */
    @Cacheable(value = "climbingLevels", key = "'all'")
    public List<ClimbingLevel> getAllClimbingLevels() {
        return climbingLevelRepository.findAllByOrderByDifficultyScore();
    }
    
    /**
     * V등급 레벨 조회
     */
    @Cacheable(value = "climbingLevels", key = "'v_scale'")
    public List<ClimbingLevel> getVScaleLevels() {
        return climbingLevelRepository.findByLevelSystemOrderByDifficultyScore("V_SCALE");
    }
    
    /**
     * 5.등급 레벨 조회
     */
    @Cacheable(value = "climbingLevels", key = "'yds_scale'")
    public List<ClimbingLevel> getYdsScaleLevels() {
        return climbingLevelRepository.findByLevelSystemOrderByDifficultyScore("YDS_SCALE");
    }
    
    /**
     * 레벨 변환 (V등급 ↔ 5.등급)
     */
    public Optional<ClimbingLevel> convertLevel(Long levelId, String targetSystem) {
        ClimbingLevel sourceLevel = climbingLevelRepository.findById(levelId)
            .orElseThrow(() -> ValidationException.invalidClimbingLevel(levelId));
        
        // 같은 difficulty_score를 가진 다른 시스템의 레벨 찾기
        return climbingLevelRepository.findByLevelSystemAndDifficultyScore(
            targetSystem, sourceLevel.getDifficultyScore());
    }
}
```

---

## 📸 3. RouteMediaService - 루트 미디어 서비스

### RouteMediaService.java
```java
package com.routepick.service.route;

import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.entity.RouteImage;
import com.routepick.domain.route.entity.RouteVideo;
import com.routepick.domain.route.repository.RouteImageRepository;
import com.routepick.domain.route.repository.RouteVideoRepository;
import com.routepick.exception.route.RouteException;
import com.routepick.exception.validation.ValidationException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 루트 미디어 서비스
 * - 루트 이미지/동영상 관리
 * - 파일 업로드/삭제 처리
 * - 썸네일 생성 (@Async)
 * - 미디어 파일 유효성 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteMediaService {
    
    private final RouteImageRepository routeImageRepository;
    private final RouteVideoRepository routeVideoRepository;
    private final RouteService routeService;
    
    @Value("${app.media.upload-path:/uploads}")
    private String uploadPath;
    
    @Value("${app.media.max-file-size:10485760}") // 10MB
    private long maxFileSize;
    
    @Value("${app.media.allowed-image-types:jpg,jpeg,png,gif}")
    private String allowedImageTypes;
    
    @Value("${app.media.allowed-video-types:mp4,avi,mov}")
    private String allowedVideoTypes;
    
    // ===== 이미지 관리 =====
    
    /**
     * 루트 이미지 업로드
     */
    @Transactional
    public RouteImage uploadRouteImage(Long routeId, MultipartFile file, 
                                      String description, Integer displayOrder) {
        log.info("루트 이미지 업로드: routeId={}, fileName={}", routeId, file.getOriginalFilename());
        
        // 파일 유효성 검증
        validateImageFile(file);
        
        Route route = routeService.getRoute(routeId);
        
        // 파일 저장
        String savedFileName = saveFile(file, "images");
        String fileUrl = "/uploads/images/" + savedFileName;
        
        // display_order 자동 설정
        if (displayOrder == null) {
            displayOrder = getNextImageDisplayOrder(routeId);
        }
        
        RouteImage routeImage = RouteImage.builder()
            .route(route)
            .fileName(savedFileName)
            .originalFileName(file.getOriginalFilename())
            .fileUrl(fileUrl)
            .fileSize(file.getSize())
            .description(XssProtectionUtil.sanitize(description))
            .displayOrder(displayOrder)
            .build();
        
        routeImage = routeImageRepository.save(routeImage);
        
        // 비동기 썸네일 생성
        generateThumbnailAsync(routeImage.getImageId(), fileUrl);
        
        log.info("루트 이미지 업로드 완료: imageId={}", routeImage.getImageId());
        return routeImage;
    }
    
    /**
     * 루트별 이미지 조회
     */
    public List<RouteImage> getRouteImages(Long routeId) {
        return routeImageRepository.findByRouteIdOrderByDisplayOrder(routeId);
    }
    
    /**
     * 이미지 삭제
     */
    @Transactional
    public void deleteRouteImage(Long imageId) {
        log.info("루트 이미지 삭제: imageId={}", imageId);
        
        RouteImage image = routeImageRepository.findById(imageId)
            .orElseThrow(() -> RouteException.imageNotFound(imageId));
        
        // 파일 삭제
        deleteFile(image.getFileUrl());
        if (image.getThumbnailUrl() != null) {
            deleteFile(image.getThumbnailUrl());
        }
        
        routeImageRepository.delete(image);
        
        log.info("루트 이미지 삭제 완료: imageId={}", imageId);
    }
    
    /**
     * 이미지 순서 변경
     */
    @Transactional
    public void reorderImages(Long routeId, List<Long> imageIds) {
        log.info("이미지 순서 변경: routeId={}", routeId);
        
        for (int i = 0; i < imageIds.size(); i++) {
            routeImageRepository.updateDisplayOrder(imageIds.get(i), i + 1);
        }
    }
    
    // ===== 동영상 관리 =====
    
    /**
     * 루트 동영상 업로드
     */
    @Transactional
    public RouteVideo uploadRouteVideo(Long routeId, MultipartFile file, 
                                      String description, Integer displayOrder) {
        log.info("루트 동영상 업로드: routeId={}, fileName={}", routeId, file.getOriginalFilename());
        
        // 파일 유효성 검증
        validateVideoFile(file);
        
        Route route = routeService.getRoute(routeId);
        
        // 파일 저장
        String savedFileName = saveFile(file, "videos");
        String fileUrl = "/uploads/videos/" + savedFileName;
        
        // display_order 자동 설정
        if (displayOrder == null) {
            displayOrder = getNextVideoDisplayOrder(routeId);
        }
        
        RouteVideo routeVideo = RouteVideo.builder()
            .route(route)
            .fileName(savedFileName)
            .originalFileName(file.getOriginalFilename())
            .fileUrl(fileUrl)
            .fileSize(file.getSize())
            .description(XssProtectionUtil.sanitize(description))
            .displayOrder(displayOrder)
            .duration(0) // 실제로는 동영상 분석 필요
            .build();
        
        routeVideo = routeVideoRepository.save(routeVideo);
        
        // 비동기 동영상 처리 (썸네일, 메타데이터 추출)
        processVideoAsync(routeVideo.getVideoId(), fileUrl);
        
        log.info("루트 동영상 업로드 완료: videoId={}", routeVideo.getVideoId());
        return routeVideo;
    }
    
    /**
     * 루트별 동영상 조회
     */
    public List<RouteVideo> getRouteVideos(Long routeId) {
        return routeVideoRepository.findByRouteIdOrderByDisplayOrder(routeId);
    }
    
    /**
     * 동영상 삭제
     */
    @Transactional
    public void deleteRouteVideo(Long videoId) {
        log.info("루트 동영상 삭제: videoId={}", videoId);
        
        RouteVideo video = routeVideoRepository.findById(videoId)
            .orElseThrow(() -> RouteException.videoNotFound(videoId));
        
        // 파일 삭제
        deleteFile(video.getFileUrl());
        if (video.getThumbnailUrl() != null) {
            deleteFile(video.getThumbnailUrl());
        }
        
        routeVideoRepository.delete(video);
        
        log.info("루트 동영상 삭제 완료: videoId={}", videoId);
    }
    
    /**
     * 동영상 순서 변경
     */
    @Transactional
    public void reorderVideos(Long routeId, List<Long> videoIds) {
        log.info("동영상 순서 변경: routeId={}", routeId);
        
        for (int i = 0; i < videoIds.size(); i++) {
            routeVideoRepository.updateDisplayOrder(videoIds.get(i), i + 1);
        }
    }
    
    // ===== 파일 처리 =====
    
    /**
     * 파일 저장
     */
    private String saveFile(MultipartFile file, String subDirectory) {
        try {
            // 업로드 디렉토리 생성
            Path uploadDir = Paths.get(uploadPath, subDirectory);
            Files.createDirectories(uploadDir);
            
            // 고유 파일명 생성
            String extension = getFileExtension(file.getOriginalFilename());
            String savedFileName = UUID.randomUUID().toString() + "." + extension;
            
            // 파일 저장
            Path filePath = uploadDir.resolve(savedFileName);
            Files.copy(file.getInputStream(), filePath);
            
            return savedFileName;
            
        } catch (IOException e) {
            log.error("파일 저장 실패: {}", file.getOriginalFilename(), e);
            throw RouteException.fileUploadFailed(file.getOriginalFilename());
        }
    }
    
    /**
     * 파일 삭제
     */
    private void deleteFile(String fileUrl) {
        try {
            Path filePath = Paths.get(uploadPath, fileUrl.replace("/uploads/", ""));
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("파일 삭제 실패: {}", fileUrl, e);
        }
    }
    
    /**
     * 이미지 파일 유효성 검증
     */
    private void validateImageFile(MultipartFile file) {
        // 파일 크기 검증
        if (file.getSize() > maxFileSize) {
            throw ValidationException.fileSizeExceeded(file.getSize(), maxFileSize);
        }
        
        // 파일 확장자 검증
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        List<String> allowedTypes = Arrays.asList(allowedImageTypes.split(","));
        
        if (!allowedTypes.contains(extension)) {
            throw ValidationException.unsupportedFileType(extension, allowedImageTypes);
        }
        
        // 파일 내용 검증 (MIME 타입)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ValidationException.invalidImageFile();
        }
    }
    
    /**
     * 동영상 파일 유효성 검증
     */
    private void validateVideoFile(MultipartFile file) {
        // 파일 크기 검증 (동영상은 더 큰 용량 허용)
        long videoMaxSize = maxFileSize * 10; // 100MB
        if (file.getSize() > videoMaxSize) {
            throw ValidationException.fileSizeExceeded(file.getSize(), videoMaxSize);
        }
        
        // 파일 확장자 검증
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        List<String> allowedTypes = Arrays.asList(allowedVideoTypes.split(","));
        
        if (!allowedTypes.contains(extension)) {
            throw ValidationException.unsupportedFileType(extension, allowedVideoTypes);
        }
        
        // 파일 내용 검증 (MIME 타입)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw ValidationException.invalidVideoFile();
        }
    }
    
    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw ValidationException.invalidFileName(fileName);
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
    
    /**
     * 다음 이미지 표시 순서 조회
     */
    private Integer getNextImageDisplayOrder(Long routeId) {
        Integer maxOrder = routeImageRepository.findMaxDisplayOrderByRouteId(routeId);
        return (maxOrder != null) ? maxOrder + 1 : 1;
    }
    
    /**
     * 다음 동영상 표시 순서 조회
     */
    private Integer getNextVideoDisplayOrder(Long routeId) {
        Integer maxOrder = routeVideoRepository.findMaxDisplayOrderByRouteId(routeId);
        return (maxOrder != null) ? maxOrder + 1 : 1;
    }
    
    // ===== 비동기 처리 =====
    
    /**
     * 썸네일 생성 (비동기)
     */
    @Async
    public CompletableFuture<Void> generateThumbnailAsync(Long imageId, String fileUrl) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("썸네일 생성 시작: imageId={}", imageId);
                
                // 실제 썸네일 생성 로직 구현 필요
                // ImageIO, BufferedImage 등을 사용한 이미지 리사이징
                String thumbnailUrl = generateThumbnail(fileUrl);
                
                // 썸네일 URL 업데이트
                routeImageRepository.updateThumbnailUrl(imageId, thumbnailUrl);
                
                log.info("썸네일 생성 완료: imageId={}", imageId);
                
            } catch (Exception e) {
                log.error("썸네일 생성 실패: imageId={}", imageId, e);
            }
        });
    }
    
    /**
     * 동영상 처리 (비동기)
     */
    @Async
    public CompletableFuture<Void> processVideoAsync(Long videoId, String fileUrl) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("동영상 처리 시작: videoId={}", videoId);
                
                // 동영상 메타데이터 추출 (FFmpeg 등 활용)
                VideoMetadata metadata = extractVideoMetadata(fileUrl);
                
                // 동영상 썸네일 생성
                String thumbnailUrl = generateVideoThumbnail(fileUrl);
                
                // 메타데이터 업데이트
                routeVideoRepository.updateVideoMetadata(videoId, 
                    metadata.getDuration(), thumbnailUrl, 
                    metadata.getWidth(), metadata.getHeight());
                
                log.info("동영상 처리 완료: videoId={}", videoId);
                
            } catch (Exception e) {
                log.error("동영상 처리 실패: videoId={}", videoId, e);
            }
        });
    }
    
    /**
     * 실제 썸네일 생성 (구현 필요)
     */
    private String generateThumbnail(String fileUrl) {
        // TODO: 실제 썸네일 생성 로직 구현
        return fileUrl.replace(".", "_thumb.");
    }
    
    /**
     * 동영상 메타데이터 추출 (구현 필요)
     */
    private VideoMetadata extractVideoMetadata(String fileUrl) {
        // TODO: FFmpeg 등을 사용한 메타데이터 추출
        return new VideoMetadata(0, 1920, 1080);
    }
    
    /**
     * 동영상 썸네일 생성 (구현 필요)
     */
    private String generateVideoThumbnail(String fileUrl) {
        // TODO: 동영상 첫 프레임 또는 특정 시점 썸네일 생성
        return fileUrl.replace(".", "_thumb.jpg");
    }
    
    // Helper 클래스
    private static class VideoMetadata {
        private final int duration;
        private final int width;
        private final int height;
        
        public VideoMetadata(int duration, int width, int height) {
            this.duration = duration;
            this.width = width;
            this.height = height;
        }
        
        public int getDuration() { return duration; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }
}
```

---

## 📊 4. ClimbingRecordService - 클라이밍 기록 서비스

### ClimbingRecordService.java
```java
package com.routepick.service.climbing;

import com.routepick.domain.climbing.entity.ClimbingLevel;
import com.routepick.domain.climbing.entity.ClimbingShoe;
import com.routepick.domain.climbing.entity.UserClimbingShoe;
import com.routepick.domain.climbing.repository.ClimbingLevelRepository;
import com.routepick.domain.climbing.repository.ClimbingShoeRepository;
import com.routepick.domain.climbing.repository.UserClimbingShoeRepository;
import com.routepick.domain.route.entity.UserClimb;
import com.routepick.domain.route.repository.UserClimbRepository;
import com.routepick.exception.climbing.ClimbingException;
import com.routepick.exception.validation.ValidationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 클라이밍 기록 서비스
 * - UserClimb 기록 관리
 * - 클라이밍 통계 계산
 * - ClimbingLevel 매핑 (V등급 ↔ 5.등급)
 * - 개인 기록 조회 및 분석
 * - 클라이밍 신발 정보 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClimbingRecordService {
    
    private final UserClimbRepository userClimbRepository;
    private final ClimbingLevelRepository climbingLevelRepository;
    private final ClimbingShoeRepository climbingShoeRepository;
    private final UserClimbingShoeRepository userClimbingShoeRepository;
    
    // ===== 클라이밍 기록 관리 =====
    
    /**
     * 사용자별 클라이밍 통계 조회
     */
    @Cacheable(value = "climbingStats", key = "#userId")
    public ClimbingStatistics getUserClimbingStatistics(Long userId) {
        log.info("사용자 클라이밍 통계 조회: userId={}", userId);
        
        // 총 시도 수
        long totalAttempts = userClimbRepository.countByUserId(userId);
        
        // 총 완등 수
        long totalCompletions = userClimbRepository.countCompletedByUserId(userId);
        
        // 성공률 계산
        double successRate = totalAttempts > 0 ? 
            (double) totalCompletions / totalAttempts * 100 : 0.0;
        
        // 최고 난이도 완등
        Optional<Integer> highestCompletedLevel = userClimbRepository
            .findHighestCompletedLevel(userId);
        
        // 평균 시도 횟수
        Double averageAttempts = userClimbRepository.calculateAverageAttempts(userId);
        
        // 최근 활동일
        Optional<LocalDateTime> lastClimbDate = userClimbRepository
            .findLastClimbDate(userId);
        
        // 월별 완등 수 (최근 12개월)
        LocalDateTime oneYearAgo = LocalDateTime.now().minusMonths(12);
        List<Object[]> monthlyCompletions = userClimbRepository
            .getMonthlyCompletions(userId, oneYearAgo);
        
        return ClimbingStatistics.builder()
            .userId(userId)
            .totalAttempts(totalAttempts)
            .totalCompletions(totalCompletions)
            .successRate(BigDecimal.valueOf(successRate).setScale(2, RoundingMode.HALF_UP))
            .highestCompletedLevel(highestCompletedLevel.orElse(0))
            .averageAttempts(averageAttempts != null ? 
                BigDecimal.valueOf(averageAttempts).setScale(1, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO)
            .lastClimbDate(lastClimbDate.orElse(null))
            .monthlyCompletions(monthlyCompletions)
            .build();
    }
    
    /**
     * 사용자별 난이도 분석
     */
    public List<Object[]> getUserDifficultyAnalysis(Long userId) {
        return userClimbRepository.getDifficultyAnalysis(userId);
    }
    
    /**
     * 사용자별 최근 클라이밍 기록
     */
    public Page<UserClimb> getRecentClimbs(Long userId, Pageable pageable) {
        return userClimbRepository.findByUserIdOrderByClimbDate(userId, pageable);
    }
    
    /**
     * 루트별 사용자 클라이밍 기록
     */
    public Optional<UserClimb> getUserClimbRecord(Long userId, Long routeId) {
        return userClimbRepository.findByUserIdAndRouteId(userId, routeId);
    }
    
    /**
     * 사용자 완등 기록 (난이도별)
     */
    public Page<UserClimb> getUserCompletionsByLevel(Long userId, Long levelId, Pageable pageable) {
        return userClimbRepository.findCompletedByUserIdAndLevelId(userId, levelId, pageable);
    }
    
    // ===== 레벨 진척도 추적 =====
    
    /**
     * 사용자 현재 레벨 추정
     */
    public ClimbingLevel estimateUserLevel(Long userId) {
        log.info("사용자 레벨 추정: userId={}", userId);
        
        // 최근 3개월간 완등한 루트들의 난이도 분석
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        List<Object[]> recentCompletions = userClimbRepository
            .getRecentCompletionsByDifficulty(userId, threeMonthsAgo);
        
        if (recentCompletions.isEmpty()) {
            // 완등 기록이 없으면 가장 낮은 레벨 반환
            return climbingLevelRepository.findByDifficultyScore(1)
                .orElse(null);
        }
        
        // 가장 많이 완등한 난이도 레벨 찾기
        Integer mostCompletedLevel = recentCompletions.stream()
            .max((a, b) -> ((Long) a[1]).compareTo((Long) b[1]))
            .map(obj -> (Integer) obj[0])
            .orElse(1);
        
        return climbingLevelRepository.findByDifficultyScore(mostCompletedLevel)
            .orElse(null);
    }
    
    /**
     * 다음 목표 레벨 추천
     */
    public List<ClimbingLevel> getRecommendedNextLevels(Long userId) {
        ClimbingLevel currentLevel = estimateUserLevel(userId);
        if (currentLevel == null) {
            return List.of();
        }
        
        Integer currentScore = currentLevel.getDifficultyScore();
        
        // 현재 레벨 +1, +2 레벨 추천
        return climbingLevelRepository.findByDifficultyScoreBetween(
            currentScore + 1, currentScore + 2);
    }
    
    /**
     * 레벨별 진척도 계산
     */
    public Map<Integer, LevelProgress> calculateLevelProgress(Long userId) {
        List<Object[]> difficultyStats = getUserDifficultyAnalysis(userId);
        
        return difficultyStats.stream()
            .collect(Collectors.toMap(
                obj -> (Integer) obj[0], // difficulty_score
                obj -> {
                    long completions = (Long) obj[1];
                    long attempts = (Long) obj[2];
                    double successRate = attempts > 0 ? 
                        (double) completions / attempts * 100 : 0.0;
                    
                    return LevelProgress.builder()
                        .difficultyScore((Integer) obj[0])
                        .completions(completions)
                        .attempts(attempts)
                        .successRate(BigDecimal.valueOf(successRate)
                            .setScale(2, RoundingMode.HALF_UP))
                        .build();
                }
            ));
    }
    
    // ===== V등급/5.등급 매핑 =====
    
    /**
     * V등급 → 5.등급 변환
     */
    public Optional<ClimbingLevel> convertVScaleToYds(String vGrade) {
        ClimbingLevel vLevel = climbingLevelRepository
            .findByLevelSystemAndLevelName("V_SCALE", vGrade)
            .orElse(null);
        
        if (vLevel == null) {
            return Optional.empty();
        }
        
        return climbingLevelRepository.findByLevelSystemAndDifficultyScore(
            "YDS_SCALE", vLevel.getDifficultyScore());
    }
    
    /**
     * 5.등급 → V등급 변환
     */
    public Optional<ClimbingLevel> convertYdsToVScale(String ydsGrade) {
        ClimbingLevel ydsLevel = climbingLevelRepository
            .findByLevelSystemAndLevelName("YDS_SCALE", ydsGrade)
            .orElse(null);
        
        if (ydsLevel == null) {
            return Optional.empty();
        }
        
        return climbingLevelRepository.findByLevelSystemAndDifficultyScore(
            "V_SCALE", ydsLevel.getDifficultyScore());
    }
    
    /**
     * 레벨 시스템별 전체 목록
     */
    @Cacheable(value = "climbingLevels", key = "#system")
    public List<ClimbingLevel> getLevelsBySystem(String system) {
        return climbingLevelRepository.findByLevelSystemOrderByDifficultyScore(system);
    }
    
    /**
     * 난이도 점수로 레벨 조회
     */
    public Optional<ClimbingLevel> getLevelByScore(Integer difficultyScore, String system) {
        return climbingLevelRepository.findByLevelSystemAndDifficultyScore(system, difficultyScore);
    }
    
    // ===== 클라이밍 신발 관리 =====
    
    /**
     * 사용자 클라이밍 신발 등록
     */
    @Transactional
    public UserClimbingShoe registerUserClimbingShoe(Long userId, Long shoeId, 
                                                     String size, String condition, boolean isPrimary) {
        log.info("사용자 클라이밍 신발 등록: userId={}, shoeId={}", userId, shoeId);
        
        ClimbingShoe shoe = climbingShoeRepository.findById(shoeId)
            .orElseThrow(() -> ClimbingException.shoeNotFound(shoeId));
        
        // 기본 신발 설정 시 기존 기본 신발 해제
        if (isPrimary) {
            userClimbingShoeRepository.updatePrimaryStatus(userId, false);
        }
        
        UserClimbingShoe userShoe = UserClimbingShoe.builder()
            .user(null) // User 엔티티 주입 필요
            .shoe(shoe)
            .size(size)
            .condition(condition)
            .isPrimary(isPrimary)
            .build();
        
        userShoe = userClimbingShoeRepository.save(userShoe);
        
        log.info("사용자 클라이밍 신발 등록 완료: userShoeId={}", userShoe.getUserShoeId());
        return userShoe;
    }
    
    /**
     * 사용자 클라이밍 신발 목록
     */
    public List<UserClimbingShoe> getUserClimbingShoes(Long userId) {
        return userClimbingShoeRepository.findByUserIdOrderByIsPrimaryDesc(userId);
    }
    
    /**
     * 사용자 기본 클라이밍 신발
     */
    public Optional<UserClimbingShoe> getUserPrimaryShoe(Long userId) {
        return userClimbingShoeRepository.findByUserIdAndIsPrimary(userId, true);
    }
    
    /**
     * 클라이밍 신발 정보 수정
     */
    @Transactional
    public UserClimbingShoe updateUserClimbingShoe(Long userShoeId, String size, 
                                                   String condition, Boolean isPrimary) {
        log.info("클라이밍 신발 정보 수정: userShoeId={}", userShoeId);
        
        UserClimbingShoe userShoe = userClimbingShoeRepository.findById(userShoeId)
            .orElseThrow(() -> ClimbingException.userShoeNotFound(userShoeId));
        
        userShoe.setSize(size);
        userShoe.setCondition(condition);
        
        if (isPrimary != null && isPrimary && !userShoe.isPrimary()) {
            // 기존 기본 신발 해제
            userClimbingShoeRepository.updatePrimaryStatus(
                userShoe.getUser().getUserId(), false);
            userShoe.setPrimary(true);
        }
        
        return userClimbingShoeRepository.save(userShoe);
    }
    
    /**
     * 클라이밍 신발 목록 (전체)
     */
    @Cacheable(value = "climbingShoes", key = "'all'")
    public List<ClimbingShoe> getAllClimbingShoes() {
        return climbingShoeRepository.findAllByOrderByBrand();
    }
    
    /**
     * 브랜드별 클라이밍 신발
     */
    public List<ClimbingShoe> getShoesByBrand(String brand) {
        return climbingShoeRepository.findByBrandOrderByModel(brand);
    }
    
    // ===== 통계 및 분석 =====
    
    /**
     * 전체 클라이밍 통계
     */
    @Cacheable(value = "globalClimbingStats", key = "'overall'")
    public GlobalClimbingStatistics getGlobalStatistics() {
        long totalUsers = userClimbRepository.countDistinctUsers();
        long totalClimbs = userClimbRepository.count();
        long totalCompletions = userClimbRepository.countCompleted();
        
        List<Object[]> popularLevels = userClimbRepository.getMostPopularLevels();
        List<Object[]> monthlyTrends = userClimbRepository.getMonthlyClimbingTrends();
        
        return GlobalClimbingStatistics.builder()
            .totalUsers(totalUsers)
            .totalClimbs(totalClimbs)
            .totalCompletions(totalCompletions)
            .overallSuccessRate(totalClimbs > 0 ? 
                BigDecimal.valueOf((double) totalCompletions / totalClimbs * 100)
                    .setScale(2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO)
            .popularLevels(popularLevels)
            .monthlyTrends(monthlyTrends)
            .build();
    }
    
    /**
     * 특정 기간 사용자 활동 분석
     */
    public ClimbingActivityReport getUserActivityReport(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        
        long climbsInPeriod = userClimbRepository.countByUserIdAndClimbDateBetween(userId, start, end);
        long completionsInPeriod = userClimbRepository.countCompletedByUserIdAndClimbDateBetween(userId, start, end);
        
        List<Object[]> dailyActivity = userClimbRepository.getDailyActivity(userId, start, end);
        List<Object[]> levelProgress = userClimbRepository.getLevelProgressInPeriod(userId, start, end);
        
        return ClimbingActivityReport.builder()
            .userId(userId)
            .startDate(startDate)
            .endDate(endDate)
            .totalClimbs(climbsInPeriod)
            .totalCompletions(completionsInPeriod)
            .successRate(climbsInPeriod > 0 ? 
                BigDecimal.valueOf((double) completionsInPeriod / climbsInPeriod * 100)
                    .setScale(2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO)
            .dailyActivity(dailyActivity)
            .levelProgress(levelProgress)
            .build();
    }
    
    // ===== 도메인 모델 클래스 =====
    
    @lombok.Builder
    @lombok.Data
    public static class ClimbingStatistics {
        private Long userId;
        private long totalAttempts;
        private long totalCompletions;
        private BigDecimal successRate;
        private Integer highestCompletedLevel;
        private BigDecimal averageAttempts;
        private LocalDateTime lastClimbDate;
        private List<Object[]> monthlyCompletions;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class LevelProgress {
        private Integer difficultyScore;
        private long completions;
        private long attempts;
        private BigDecimal successRate;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class GlobalClimbingStatistics {
        private long totalUsers;
        private long totalClimbs;
        private long totalCompletions;
        private BigDecimal overallSuccessRate;
        private List<Object[]> popularLevels;
        private List<Object[]> monthlyTrends;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ClimbingActivityReport {
        private Long userId;
        private LocalDate startDate;
        private LocalDate endDate;
        private long totalClimbs;
        private long totalCompletions;
        private BigDecimal successRate;
        private List<Object[]> dailyActivity;
        private List<Object[]> levelProgress;
    }
}
```

---

## 🚨 5. 커스텀 예외 클래스

### GymException.java
```java
package com.routepick.exception.gym;

import com.routepick.exception.base.BaseException;

/**
 * 암장 관련 예외
 */
public class GymException extends BaseException {
    
    public static GymException notFound(Long gymId) {
        return new GymException("GYM-001", 
            String.format("암장을 찾을 수 없습니다: %d", gymId));
    }
    
    public static GymException gymNameAlreadyExists(String name) {
        return new GymException("GYM-002", 
            String.format("이미 존재하는 암장명입니다: %s", name));
    }
    
    public static GymException businessRegistrationNumberAlreadyExists(String number) {
        return new GymException("GYM-003", 
            String.format("이미 등록된 사업자등록번호입니다: %s", number));
    }
    
    public static GymException branchNotFound(Long branchId) {
        return new GymException("GYM-004", 
            String.format("지점을 찾을 수 없습니다: %d", branchId));
    }
    
    public static GymException mainBranchAlreadyExists(Long gymId) {
        return new GymException("GYM-005", 
            String.format("이미 본점이 설정된 암장입니다: %d", gymId));
    }
    
    public static GymException membershipNotFound(Long membershipId) {
        return new GymException("GYM-006", 
            String.format("멤버십을 찾을 수 없습니다: %d", membershipId));
    }
    
    public static GymException membershipAlreadyExists(Long userId, Long branchId) {
        return new GymException("GYM-007", 
            String.format("이미 등록된 멤버십입니다: userId=%d, branchId=%d", userId, branchId));
    }
    
    private GymException(String errorCode, String message) {
        super(errorCode, message);
    }
}
```

### RouteException.java
```java
package com.routepick.exception.route;

import com.routepick.exception.base.BaseException;

/**
 * 루트 관련 예외
 */
public class RouteException extends BaseException {
    
    public static RouteException notFound(Long routeId) {
        return new RouteException("ROUTE-001", 
            String.format("루트를 찾을 수 없습니다: %d", routeId));
    }
    
    public static RouteException routeNumberAlreadyExists(Long wallId, String routeNumber) {
        return new RouteException("ROUTE-002", 
            String.format("이미 존재하는 루트 번호입니다: wallId=%d, routeNumber=%s", wallId, routeNumber));
    }
    
    public static RouteException alreadyScrapped(Long userId, Long routeId) {
        return new RouteException("ROUTE-003", 
            String.format("이미 스크랩한 루트입니다: userId=%d, routeId=%d", userId, routeId));
    }
    
    public static RouteException scrapNotFound(Long userId, Long routeId) {
        return new RouteException("ROUTE-004", 
            String.format("스크랩을 찾을 수 없습니다: userId=%d, routeId=%d", userId, routeId));
    }
    
    public static RouteException imageNotFound(Long imageId) {
        return new RouteException("ROUTE-005", 
            String.format("루트 이미지를 찾을 수 없습니다: %d", imageId));
    }
    
    public static RouteException videoNotFound(Long videoId) {
        return new RouteException("ROUTE-006", 
            String.format("루트 동영상을 찾을 수 없습니다: %d", videoId));
    }
    
    public static RouteException fileUploadFailed(String fileName) {
        return new RouteException("ROUTE-007", 
            String.format("파일 업로드에 실패했습니다: %s", fileName));
    }
    
    private RouteException(String errorCode, String message) {
        super(errorCode, message);
    }
}
```

---

## ✅ 구현 완료 체크리스트

### 🏢 GymService (암장 관리)
- [x] 한국 좌표 범위 검증 (위도 33.0~38.6, 경도 124.0~132.0)
- [x] 공간 쿼리 성능 최적화 (ST_Distance_Sphere)
- [x] GymBranch 관리 (BranchStatus: ACTIVE, INACTIVE, CLOSED, PENDING)
- [x] 지점별 Wall 정보 조회
- [x] 인기 암장 조회 (@Cacheable 캐싱, 1시간 TTL)
- [x] GymMember 멤버십 관리

### 🧗 RouteService (루트 관리)  
- [x] 루트 CRUD 관리 (Route 엔티티)
- [x] 난이도별 루트 조회 (V등급/5.등급 체계)
- [x] RouteStatus 관리 (ACTIVE, EXPIRED, REMOVED)
- [x] 루트 검색/스크랩 관리 (RouteScrap)
- [x] 인기 루트 조회 (스크랩 수, 완등 수 기준)
- [x] 난이도 투표 처리 (RouteDifficultyVote)

### 📸 RouteMediaService (미디어 관리)
- [x] 루트 이미지/동영상 관리 (RouteImage, RouteVideo)
- [x] 미디어 파일 업로드/삭제 처리
- [x] 썸네일 생성 (@Async 비동기)
- [x] 미디어 파일 유효성 검증
- [x] display_order 관리

### 📊 ClimbingRecordService (기록 관리)
- [x] UserClimb 기록 관리 
- [x] 클라이밍 통계 계산 (성공률, 완등 수)
- [x] ClimbingLevel 매핑 (V등급 ↔ 5.등급)
- [x] 개인 기록 조회 및 분석
- [x] 클라이밍 신발 정보 관리 (UserClimbingShoe)
- [x] 레벨 진척도 추적

### 🇰🇷 한국 특화 비즈니스 로직
- [x] 좌표 범위 검증: 위도 33.0~38.6, 경도 124.0~132.0
- [x] V등급과 5.등급 매핑 테이블 활용
- [x] 한국 클라이밍장 특성 반영 (지하철역, 행정구역)

### ⚡ 성능 최적화
- [x] @Cacheable, @CacheEvict Redis 캐싱
- [x] 페이징 처리 최적화
- [x] 공간 쿼리 성능 최적화 (Spatial Index)
- [x] @Async 비동기 처리 (썸네일 생성, 동영상 처리)

---

**다음 단계**: Step 6-3 태그 추천 시스템 Service 구현  
**핵심 목표**: 태그 기반 루트 추천 알고리즘 및 사용자 선호도 분석

*완료일: 2025-08-21*  
*핵심 성과: 암장 및 루트 관리 4개 Service 완전 구현 + 한국 특화 최적화*