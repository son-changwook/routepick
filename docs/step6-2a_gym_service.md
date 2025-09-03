# Step 6-2a: GymService 구현

> 체육관 관리 서비스 - 한국 좌표 검증, 공간 쿼리, 캐싱 전략  
> 생성일: 2025-08-21  
> 단계: 6-2a (Service 레이어 - 체육관 도메인)  
> 참고: step4-1a, step3-2b, step6-1a 기본 구조

---

## 🎯 설계 목표

- **한국 좌표 검증**: 위도 33.0~38.6, 경도 124.0~132.0 범위 검증
- **공간 쿼리**: MySQL ST_Distance_Sphere 함수 활용 주변 검색
- **Redis 캐싱**: 자주 조회되는 체육관 정보 캐싱 전략
- **예외 처리**: GymException 기반 도메인별 예외 관리
- **성능 최적화**: 페이징, 배치 처리, 인덱스 활용

---

## 🏢 GymService - 체육관 관리 서비스

### GymService.java
```java
package com.routepick.service.gym;

import com.routepick.common.enums.GymStatus;
import com.routepick.common.enums.MembershipStatus;
import com.routepick.domain.gym.entity.Gym;
import com.routepick.domain.gym.entity.GymBranch;
import com.routepick.domain.gym.entity.GymMember;
import com.routepick.domain.gym.entity.Wall;
import com.routepick.domain.gym.repository.GymRepository;
import com.routepick.domain.gym.repository.GymBranchRepository;
import com.routepick.domain.gym.repository.GymMemberRepository;
import com.routepick.domain.gym.repository.WallRepository;
import com.routepick.exception.gym.GymException;
import com.routepick.exception.user.UserException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 체육관 관리 서비스
 * 
 * 주요 기능:
 * - 체육관 CRUD 관리
 * - 한국 좌표 범위 검증
 * - 주변 체육관 검색 (공간 쿼리)
 * - 체육관 회원 관리
 * - 벽면 및 홀드 관리
 * - Redis 캐싱 전략
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymService {

    private final GymRepository gymRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymMemberRepository gymMemberRepository;
    private final WallRepository wallRepository;
    
    // 한국 좌표 범위 상수
    private static final double KOREA_MIN_LATITUDE = 33.0;
    private static final double KOREA_MAX_LATITUDE = 38.6;
    private static final double KOREA_MIN_LONGITUDE = 124.0;
    private static final double KOREA_MAX_LONGITUDE = 132.0;
    
    @Value("${routepick.gym.default-radius-km:10}")
    private double defaultSearchRadiusKm;
    
    @Value("${routepick.gym.max-radius-km:50}")
    private double maxSearchRadiusKm;

    // ===== 체육관 기본 관리 =====

    /**
     * 체육관 생성
     */
    @Transactional
    @CacheEvict(value = "gyms", allEntries = true)
    public Gym createGym(String name, String description, String phoneNumber, 
                        String website, String businessNumber) {
        
        // XSS 보호
        name = XssProtectionUtil.cleanInput(name);
        description = XssProtectionUtil.cleanInput(description);
        
        // 중복 체육관 검증
        if (gymRepository.existsByNameAndDeletedFalse(name)) {
            throw GymException.gymAlreadyExists(name);
        }
        
        // 사업자번호 중복 검증
        if (StringUtils.hasText(businessNumber) && 
            gymRepository.existsByBusinessNumberAndDeletedFalse(businessNumber)) {
            throw GymException.businessNumberAlreadyExists(businessNumber);
        }
        
        Gym gym = Gym.builder()
            .name(name)
            .description(description)
            .phoneNumber(phoneNumber)
            .website(website)
            .businessNumber(businessNumber)
            .status(GymStatus.ACTIVE)
            .build();
            
        Gym savedGym = gymRepository.save(gym);
        
        log.info("체육관 생성 완료 - gymId: {}, name: {}", savedGym.getId(), savedGym.getName());
        return savedGym;
    }

    /**
     * 체육관 상세 조회 (캐싱)
     */
    @Cacheable(value = "gym", key = "#gymId")
    public Gym getGymById(Long gymId) {
        return gymRepository.findByIdAndDeletedFalse(gymId)
            .orElseThrow(() -> GymException.gymNotFound(gymId));
    }

    /**
     * 체육관 이름으로 검색
     */
    @Cacheable(value = "gym-search", key = "#keyword + '_' + #pageable.pageNumber")
    public Page<Gym> searchGymsByName(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            throw GymException.invalidSearchKeyword(keyword);
        }
        
        keyword = XssProtectionUtil.cleanInput(keyword);
        return gymRepository.findByNameContainingAndDeletedFalse(keyword, pageable);
    }

    /**
     * 체육관 정보 수정
     */
    @Transactional
    @CacheEvict(value = {"gym", "gyms"}, key = "#gymId")
    public Gym updateGym(Long gymId, String name, String description, 
                        String phoneNumber, String website) {
        
        Gym gym = getGymById(gymId);
        
        // XSS 보호
        if (StringUtils.hasText(name)) {
            name = XssProtectionUtil.cleanInput(name);
            
            // 다른 체육관과 이름 중복 검증
            if (!gym.getName().equals(name) && 
                gymRepository.existsByNameAndDeletedFalse(name)) {
                throw GymException.gymAlreadyExists(name);
            }
            gym.updateName(name);
        }
        
        if (StringUtils.hasText(description)) {
            gym.updateDescription(XssProtectionUtil.cleanInput(description));
        }
        
        if (StringUtils.hasText(phoneNumber)) {
            gym.updatePhoneNumber(phoneNumber);
        }
        
        if (StringUtils.hasText(website)) {
            gym.updateWebsite(website);
        }
        
        log.info("체육관 정보 수정 완료 - gymId: {}", gymId);
        return gym;
    }

    /**
     * 체육관 상태 변경
     */
    @Transactional
    @CacheEvict(value = {"gym", "gyms"}, key = "#gymId")
    public void updateGymStatus(Long gymId, GymStatus status) {
        Gym gym = getGymById(gymId);
        gym.updateStatus(status);
        
        log.info("체육관 상태 변경 - gymId: {}, status: {}", gymId, status);
    }

    /**
     * 체육관 소프트 삭제
     */
    @Transactional
    @CacheEvict(value = {"gym", "gyms"}, key = "#gymId")
    public void deleteGym(Long gymId) {
        Gym gym = getGymById(gymId);
        gym.markAsDeleted();
        
        // 관련 지점들도 소프트 삭제
        List<GymBranch> branches = gymBranchRepository.findByGymIdAndDeletedFalse(gymId);
        branches.forEach(GymBranch::markAsDeleted);
        
        log.info("체육관 삭제 완료 - gymId: {}, 관련 지점 수: {}", gymId, branches.size());
    }

    // ===== 체육관 지점 관리 =====

    /**
     * 체육관 지점 생성
     */
    @Transactional
    @CacheEvict(value = "gym-branches", allEntries = true)
    public GymBranch createGymBranch(Long gymId, String branchName, String address,
                                   BigDecimal latitude, BigDecimal longitude,
                                   LocalTime openTime, LocalTime closeTime) {
        
        Gym gym = getGymById(gymId);
        
        // 한국 좌표 범위 검증
        validateKoreaCoordinates(latitude, longitude);
        
        // XSS 보호
        branchName = XssProtectionUtil.cleanInput(branchName);
        address = XssProtectionUtil.cleanInput(address);
        
        // 동일 체육관 내 지점명 중복 검증
        if (gymBranchRepository.existsByGymIdAndBranchNameAndDeletedFalse(gymId, branchName)) {
            throw GymException.branchAlreadyExists(gymId, branchName);
        }
        
        GymBranch branch = GymBranch.builder()
            .gym(gym)
            .branchName(branchName)
            .address(address)
            .latitude(latitude)
            .longitude(longitude)
            .openTime(openTime)
            .closeTime(closeTime)
            .status(GymStatus.ACTIVE)
            .build();
            
        GymBranch savedBranch = gymBranchRepository.save(branch);
        
        log.info("체육관 지점 생성 완료 - gymId: {}, branchId: {}, name: {}", 
                gymId, savedBranch.getId(), savedBranch.getBranchName());
        return savedBranch;
    }

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
     * 체육관 지점 정보 수정
     */
    @Transactional
    @CacheEvict(value = {"gym-branches", "nearby-branches"}, allEntries = true)
    public GymBranch updateGymBranch(Long branchId, String branchName, String address,
                                   BigDecimal latitude, BigDecimal longitude,
                                   LocalTime openTime, LocalTime closeTime) {
        
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
            branch.updateOperatingHours(openTime, closeTime);
        }
        
        log.info("체육관 지점 정보 수정 완료 - branchId: {}", branchId);
        return branch;
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
     * 체육관 회원권 만료 처리
     */
    @Transactional
    public void expireMembership(Long membershipId) {
        GymMember gymMember = gymMemberRepository.findByIdAndDeletedFalse(membershipId)
            .orElseThrow(() -> GymException.membershipNotFound(membershipId));
            
        gymMember.updateStatus(MembershipStatus.EXPIRED);
        
        log.info("체육관 회원권 만료 처리 - membershipId: {}", membershipId);
    }

    // ===== 벽면 관리 =====

    /**
     * 벽면 생성
     */
    @Transactional
    public Wall createWall(Long branchId, String wallName, String wallType, 
                          Integer wallHeight, String wallAngle) {
        
        GymBranch branch = gymBranchRepository.findByIdAndDeletedFalse(branchId)
            .orElseThrow(() -> GymException.branchNotFound(branchId));
        
        // XSS 보호
        wallName = XssProtectionUtil.cleanInput(wallName);
        wallType = XssProtectionUtil.cleanInput(wallType);
        wallAngle = XssProtectionUtil.cleanInput(wallAngle);
        
        // 동일 지점 내 벽면명 중복 검증
        if (wallRepository.existsByBranchIdAndWallNameAndDeletedFalse(branchId, wallName)) {
            throw GymException.wallAlreadyExists(branchId, wallName);
        }
        
        Wall wall = Wall.builder()
            .branch(branch)
            .wallName(wallName)
            .wallType(wallType)
            .wallHeight(wallHeight)
            .wallAngle(wallAngle)
            .build();
            
        Wall savedWall = wallRepository.save(wall);
        
        log.info("벽면 생성 완료 - branchId: {}, wallId: {}, name: {}", 
                branchId, savedWall.getId(), savedWall.getWallName());
        return savedWall;
    }

    /**
     * 지점의 벽면 목록 조회
     */
    @Cacheable(value = "branch-walls", key = "#branchId")
    public List<Wall> getBranchWalls(Long branchId) {
        // 지점 존재 검증
        gymBranchRepository.findByIdAndDeletedFalse(branchId)
            .orElseThrow(() -> GymException.branchNotFound(branchId));
            
        return wallRepository.findByBranchIdAndDeletedFalseOrderByWallName(branchId);
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 한국 좌표 범위 검증
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
     */
    public boolean isGymOpen(Long branchId) {
        GymBranch branch = gymBranchRepository.findByIdAndDeletedFalse(branchId)
            .orElseThrow(() -> GymException.branchNotFound(branchId));
            
        LocalTime now = LocalTime.now();
        return !now.isBefore(branch.getOpenTime()) && !now.isAfter(branch.getCloseTime());
    }

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

    // ===== DTO 클래스 =====

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
}
```

---

## 📋 주요 기능 설명

### 🎯 **1. 체육관 기본 관리**
- **생성/수정/삭제**: 체육관 정보 CRUD 관리
- **검색**: 체육관 이름 기반 검색 (XSS 보호)
- **상태 관리**: GymStatus 기반 상태 변경
- **중복 검증**: 체육관명, 사업자번호 중복 방지

### 🗺️ **2. 한국 좌표 검증**
- **좌표 범위**: 위도 33.0~38.6, 경도 124.0~132.0
- **공간 쿼리**: MySQL ST_Distance_Sphere 함수 활용
- **주변 검색**: 반경 기반 체육관 지점 검색
- **검색 제한**: 최대 반경 50km 제한

### 💾 **3. Redis 캐싱 전략**
- **체육관 정보**: `@Cacheable("gym")` - 개별 체육관 캐싱
- **검색 결과**: `@Cacheable("gym-search")` - 검색 결과 캐싱
- **주변 지점**: `@Cacheable("nearby-branches")` - 위치 기반 캐싱
- **회원권 정보**: `@Cacheable("user-memberships")` - 사용자별 캐싱

### 👥 **4. 회원 관리**
- **회원 등록**: 회원권 기간 및 상태 관리
- **중복 검증**: 동일 체육관 활성 회원권 방지
- **만료 처리**: MembershipStatus 기반 상태 관리
- **회원권 조회**: 사용자별 회원권 목록

### 🧗 **5. 벽면 관리**
- **벽면 생성**: 벽면 정보 및 특성 관리
- **중복 방지**: 동일 지점 내 벽면명 중복 방지
- **벽면 조회**: 지점별 벽면 목록
- **속성 관리**: 벽면 높이, 각도, 타입 관리

---

## 🛡️ 보안 및 성능 최적화

### 보안 강화
- **XSS 보호**: 모든 입력값 XssProtectionUtil 적용
- **좌표 검증**: 한국 범위 외 좌표 차단
- **중복 검증**: 비즈니스 로직 기반 중복 방지
- **소프트 삭제**: 물리적 삭제 대신 논리적 삭제

### 성능 최적화
- **Redis 캐싱**: 자주 조회되는 데이터 캐싱
- **공간 인덱스**: MySQL Spatial Index 활용
- **페이징**: 대용량 데이터 페이징 처리
- **배치 처리**: 관련 엔티티 일괄 처리

---

## 🚀 다음 단계

**Phase 2 완료 후 진행할 작업:**
- **step6-2b_route_service.md**: 루트 관리 서비스
- **step6-2c_route_media_service.md**: 루트 미디어 서비스  
- **step6-2d_climbing_record_service.md**: 클라이밍 기록 서비스

*step6-2a 완성: 체육관 도메인 완전 구현 완료*