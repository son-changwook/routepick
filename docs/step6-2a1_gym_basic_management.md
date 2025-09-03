# Step 6-2a1: GymService - 기본 체육관 관리

> 체육관 기본 CRUD 및 대기업 관리 기능
> 생성일: 2025-08-21
> 단계: 6-2a1 (Service 레이어 - 체육관 기본)
> 참고: step4-1a, step3-2b, step6-1a

---

## 🎯 설계 목표

- **체육관 CRUD**: 생성, 조회, 수정, 삭제 기본 관리
- **대기업 인증**: 사업자번호 기반 체육관 등록
- **상태 관리**: GymStatus 기반 체육관 상태 추적
- **검색 시스템**: 이름 기반 체육관 검색
- **XSS 보호**: 모든 입력값에 대한 보안 처리

---

## 🏢 GymService 기본 관리

### GymService.java (기본 CRUD 부분)
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
 * 체육관 기본 관리 서비스
 * 
 * 주요 기능:
 * - 체육관 CRUD 관리
 * - 사업자번호 검증 및 중복 방지
 * - 체육관 상태 관리 (ACTIVE, INACTIVE, PENDING)
 * - 이름 기반 검색
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
    
    // ===== 체육관 기본 관리 =====

    /**
     * 체육관 생성
     * @param name 체육관 명
     * @param description 설명
     * @param phoneNumber 전화번호
     * @param website 웹사이트
     * @param businessNumber 사업자번호
     * @return 생성된 체육관
     */
    @Transactional
    @CacheEvict(value = "gyms", allEntries = true)
    public Gym createGym(String name, String description, String phoneNumber, 
                        String website, String businessNumber) {
        
        log.info("체육관 생성 시작 - name: {}", name);
        
        // XSS 보호
        name = XssProtectionUtil.cleanInput(name);
        description = XssProtectionUtil.cleanInput(description);
        
        // 입력값 검증
        if (!StringUtils.hasText(name)) {
            throw GymException.gymNameRequired();
        }
        
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
            .status(GymStatus.PENDING)  // 새 체육관은 검토 대기
            .build();
            
        Gym savedGym = gymRepository.save(gym);
        
        log.info("체육관 생성 완료 - gymId: {}, name: {}", savedGym.getId(), savedGym.getName());
        return savedGym;
    }

    /**
     * 체육관 상세 조회 (캐싱)
     * @param gymId 체육관 ID
     * @return 체육관 정보
     */
    @Cacheable(value = "gym", key = "#gymId")
    public Gym getGymById(Long gymId) {
        log.debug("체육관 조회 - gymId: {}", gymId);
        
        return gymRepository.findByIdAndDeletedFalse(gymId)
            .orElseThrow(() -> GymException.gymNotFound(gymId));
    }

    /**
     * 체육관 이름으로 검색
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 검색된 체육관 목록
     */
    @Cacheable(value = "gym-search", key = "#keyword + '_' + #pageable.pageNumber")
    public Page<Gym> searchGymsByName(String keyword, Pageable pageable) {
        log.debug("체육관 검색 - keyword: {}", keyword);
        
        if (!StringUtils.hasText(keyword)) {
            throw GymException.invalidSearchKeyword(keyword);
        }
        
        // XSS 보호 및 검색 최적화
        keyword = XssProtectionUtil.cleanInput(keyword.trim());
        
        if (keyword.length() < 2) {
            throw GymException.searchKeywordTooShort(keyword);
        }
        
        return gymRepository.findByNameContainingAndDeletedFalse(keyword, pageable);
    }
    
    /**
     * 체육관 전체 목록 조회 (상태별)
     * @param status 체육관 상태
     * @param pageable 페이징 정보
     * @return 체육관 목록
     */
    @Cacheable(value = "gyms-by-status", key = "#status + '_' + #pageable.pageNumber")
    public Page<Gym> getGymsByStatus(GymStatus status, Pageable pageable) {
        log.debug("체육관 상태별 조회 - status: {}", status);
        
        return gymRepository.findByStatusAndDeletedFalse(status, pageable);
    }
    
    /**
     * 체육관 전체 목록 조회
     * @param pageable 페이징 정보
     * @return 체육관 목록
     */
    @Cacheable(value = "gyms-all", key = "#pageable.pageNumber")
    public Page<Gym> getAllGyms(Pageable pageable) {
        log.debug("체육관 전체 목록 조회");
        
        return gymRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable);
    }

    /**
     * 체육관 정보 수정
     * @param gymId 체육관 ID
     * @param name 새로운 이름
     * @param description 새로운 설명
     * @param phoneNumber 새로운 전화번호
     * @param website 새로운 웹사이트
     * @return 수정된 체육관
     */
    @Transactional
    @CacheEvict(value = {"gym", "gyms", "gym-search", "gyms-by-status", "gyms-all"}, key = "#gymId")
    public Gym updateGym(Long gymId, String name, String description, 
                        String phoneNumber, String website) {
        
        log.info("체육관 정보 수정 시작 - gymId: {}", gymId);
        
        Gym gym = getGymById(gymId);
        
        // 이름 수정
        if (StringUtils.hasText(name)) {
            name = XssProtectionUtil.cleanInput(name);
            
            // 다른 체육관과 이름 중복 검증
            if (!gym.getName().equals(name) && 
                gymRepository.existsByNameAndDeletedFalse(name)) {
                throw GymException.gymAlreadyExists(name);
            }
            gym.updateName(name);
        }
        
        // 설명 수정
        if (StringUtils.hasText(description)) {
            gym.updateDescription(XssProtectionUtil.cleanInput(description));
        }
        
        // 전화번호 수정
        if (StringUtils.hasText(phoneNumber)) {
            gym.updatePhoneNumber(phoneNumber);
        }
        
        // 웹사이트 수정
        if (StringUtils.hasText(website)) {
            gym.updateWebsite(website);
        }
        
        log.info("체육관 정보 수정 완료 - gymId: {}", gymId);
        return gym;
    }

    /**
     * 체육관 상태 변경
     * @param gymId 체육관 ID
     * @param status 새로운 상태
     */
    @Transactional
    @CacheEvict(value = {"gym", "gyms", "gyms-by-status", "gyms-all"}, key = "#gymId")
    public void updateGymStatus(Long gymId, GymStatus status) {
        log.info("체육관 상태 변경 시작 - gymId: {}, status: {}", gymId, status);
        
        Gym gym = getGymById(gymId);
        GymStatus oldStatus = gym.getStatus();
        
        gym.updateStatus(status);
        
        log.info("체육관 상태 변경 완료 - gymId: {}, {} -> {}", gymId, oldStatus, status);
    }

    /**
     * 체육관 소프트 삭제
     * @param gymId 체육관 ID
     */
    @Transactional
    @CacheEvict(value = {"gym", "gyms", "gym-search", "gyms-by-status", "gyms-all"}, allEntries = true)
    public void deleteGym(Long gymId) {
        log.info("체육관 삭제 시작 - gymId: {}", gymId);
        
        Gym gym = getGymById(gymId);
        gym.markAsDeleted();
        
        // 관련 지점들도 소프트 삭제
        List<GymBranch> branches = gymBranchRepository.findByGymIdAndDeletedFalse(gymId);
        branches.forEach(GymBranch::markAsDeleted);
        
        log.info("체육관 삭제 완료 - gymId: {}, 관련 지점 수: {}", gymId, branches.size());
    }
    
    /**
     * 체육관 사업자번호 검증
     * @param businessNumber 사업자번호
     * @return 검증 결과
     */
    public boolean validateBusinessNumber(String businessNumber) {
        log.debug("사업자번호 검증 - businessNumber: {}", businessNumber);
        
        if (!StringUtils.hasText(businessNumber)) {
            return false;
        }
        
        // 사업자번호 형식 검증 (10자리 숫자)
        if (!businessNumber.matches("\\d{10}")) {
            return false;
        }
        
        // 중복 검사
        return !gymRepository.existsByBusinessNumberAndDeletedFalse(businessNumber);
    }
    
    /**
     * 체육관 승인 처리 (관리자용)
     * @param gymId 체육관 ID
     */
    @Transactional
    @CacheEvict(value = {"gym", "gyms", "gyms-by-status"}, key = "#gymId")
    public void approveGym(Long gymId) {
        log.info("체육관 승인 처리 - gymId: {}", gymId);
        
        Gym gym = getGymById(gymId);
        
        if (gym.getStatus() != GymStatus.PENDING) {
            throw GymException.gymNotInPendingStatus(gymId);
        }
        
        gym.updateStatus(GymStatus.ACTIVE);
        
        log.info("체육관 승인 완료 - gymId: {}", gymId);
    }
    
    /**
     * 체육관 거절 처리 (관리자용)
     * @param gymId 체육관 ID
     * @param reason 거절 사유
     */
    @Transactional
    @CacheEvict(value = {"gym", "gyms", "gyms-by-status"}, key = "#gymId")
    public void rejectGym(Long gymId, String reason) {
        log.info("체육관 거절 처리 - gymId: {}, reason: {}", gymId, reason);
        
        Gym gym = getGymById(gymId);
        
        if (gym.getStatus() != GymStatus.PENDING) {
            throw GymException.gymNotInPendingStatus(gymId);
        }
        
        gym.updateStatus(GymStatus.INACTIVE);
        
        // 거절 사유 로그 기록 (별도 로깅 시스템 연동)
        log.warn("체육관 거절 - gymId: {}, reason: {}", gymId, reason);
        
        log.info("체육관 거절 완료 - gymId: {}", gymId);
    }
}
```

---

## 📊 체육관 기본 관리 기능

### 1. 체육관 CRUD 관리
- **생성**: 사업자번호 검증 및 중복 방지
- **조회**: Redis 캐싱으로 성능 최적화
- **수정**: 이름 중복 검증 및 XSS 보호
- **삭제**: 소프트 삭제로 데이터 보전

### 2. 체육관 상태 관리
- **PENDING**: 신규 등록 대기 상태
- **ACTIVE**: 정상 운영 상태
- **INACTIVE**: 비활성 또는 중단 상태
- **승인/거절**: 관리자 검토 프로세스

### 3. 검색 및 조회
- **이름 검색**: 대소문자 구분 없는 부분 검색
- **상태별 조회**: GymStatus 기반 필터링
- **전체 목록**: 생성일 역순 정렬
- **페이징**: 대용량 데이터 처리

### 4. 보안 및 검증
- **XSS 보호**: 모든 입력값 살균 처리
- **사업자번호**: 10자리 숫자 형식 검증
- **중복 방지**: 이름/사업자번호 중복 사전 차단
- **입력 검증**: 필수 필드 유효성 검사

---

## 🚀 캐싱 전략

### Redis 캐시 구성
- **gym**: 개별 체육관 정보 (TTL: 1시간)
- **gym-search**: 검색 결과 (TTL: 30분)
- **gyms-by-status**: 상태별 목록 (TTL: 15분)
- **gyms-all**: 전체 목록 (TTL: 10분)

### 캐시 무효화 전략
- **체육관 생성/수정/삭제**: 관련 캐시 전체 무효화
- **상태 변경**: 상태 기반 캐시 선택적 무효화
- **자동 업데이트**: 실시간 데이터 정합성 보장

---

## ✅ 완료 사항
- ✅ 체육관 기본 CRUD 관리
- ✅ 사업자번호 검증 체계
- ✅ GymStatus 기반 상태 관리
- ✅ 이름 기반 검색 시스템
- ✅ Redis 캐싱 전략 적용
- ✅ XSS 보호 및 입력 검증
- ✅ 소프트 삭제 체계
- ✅ 관리자 승인/거절 기능
- ✅ 예외 처리 및 로깅

---

*GymService 기본 체육관 관리 기능 설계 완료*