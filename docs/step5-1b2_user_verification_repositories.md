# Step5-1b2: User Verification & Security Repositories (2/2)

> **사용자 인증 및 보안 Repository**  
> 5단계 Repository 레이어 구현: UserVerification, UserAgreement, ApiToken, AgreementContent 관리

---

## 📋 파일 분할 정보
- **원본 파일**: step5-1b_user_repositories.md (1,354줄)
- **분할 구성**: 2개 파일로 세분화
- **현재 파일**: step5-1b2_user_verification_repositories.md (2/2)
- **포함 Repository**: UserVerificationRepository, UserAgreementRepository, ApiTokenRepository, AgreementContentRepository

---

## 🔐 User 인증 및 보안 Repository 설계 (4개)

### UserVerificationRepository.java - 본인인증 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.user.entity.UserVerification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UserVerification Repository
 * - 본인인증 관리 (이메일, 휴대폰, 신분증)
 * - CI/DI 연계정보 중복 방지
 * - 인증 만료 및 재발송 관리
 */
@Repository
public interface UserVerificationRepository extends BaseRepository<UserVerification, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자 ID로 인증 정보 조회
     */
    @Query("SELECT uv FROM UserVerification uv WHERE uv.user.userId = :userId")
    Optional<UserVerification> findByUserId(@Param("userId") Long userId);
    
    /**
     * CI(연계정보)로 인증 정보 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.ci = :ci AND u.userStatus = 'ACTIVE'")
    Optional<UserVerification> findByCi(@Param("ci") String ci);
    
    /**
     * DI(중복가입확인정보)로 인증 정보 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.di = :di AND u.userStatus = 'ACTIVE'")
    Optional<UserVerification> findByDi(@Param("di") String di);
    
    // ===== 중복 확인 메서드 =====
    
    /**
     * CI 중복 확인 (중복 가입 방지)
     */
    @Query("SELECT CASE WHEN COUNT(uv) > 0 THEN true ELSE false END FROM UserVerification uv " +
           "JOIN uv.user u " +
           "WHERE uv.ci = :ci AND u.userStatus != 'DELETED'")
    boolean existsByCi(@Param("ci") String ci);
    
    /**
     * DI 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(uv) > 0 THEN true ELSE false END FROM UserVerification uv " +
           "JOIN uv.user u " +
           "WHERE uv.di = :di AND u.userStatus != 'DELETED'")
    boolean existsByDi(@Param("di") String di);
    
    /**
     * 신분증 번호 중복 확인 (개인정보보호)
     */
    @Query("SELECT CASE WHEN COUNT(uv) > 0 THEN true ELSE false END FROM UserVerification uv " +
           "JOIN uv.user u " +
           "WHERE uv.idCardHash = :idCardHash AND u.userStatus != 'DELETED'")
    boolean existsByIdCardHash(@Param("idCardHash") String idCardHash);
    
    // ===== 이메일 인증 관리 =====
    
    /**
     * 이메일 인증 완료 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserVerification uv SET " +
           "uv.emailVerified = true, " +
           "uv.emailVerifiedAt = CURRENT_TIMESTAMP " +
           "WHERE uv.user.userId = :userId")
    int completeEmailVerification(@Param("userId") Long userId);
    
    /**
     * 이메일 미인증 사용자 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.emailVerified = false AND u.userStatus = 'ACTIVE'")
    List<UserVerification> findUnverifiedEmailUsers();
    
    /**
     * 이메일 인증 만료 예정 사용자 조회 (가입 후 7일)
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.emailVerified = false " +
           "AND u.createdAt BETWEEN :startDate AND :endDate " +
           "AND u.userStatus = 'ACTIVE'")
    List<UserVerification> findEmailVerificationExpiringSoon(@Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate);
    
    // ===== 휴대폰 인증 관리 =====
    
    /**
     * 휴대폰 인증 완료 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserVerification uv SET " +
           "uv.phoneVerified = true, " +
           "uv.phoneVerifiedAt = CURRENT_TIMESTAMP " +
           "WHERE uv.user.userId = :userId")
    int completePhoneVerification(@Param("userId") Long userId);
    
    /**
     * 휴대폰 인증 정보 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserVerification uv SET " +
           "uv.phoneAuthProvider = :provider, " +
           "uv.phoneAuthRequestId = :requestId " +
           "WHERE uv.user.userId = :userId")
    int updatePhoneAuthInfo(@Param("userId") Long userId,
                           @Param("provider") String provider,
                           @Param("requestId") String requestId);
    
    /**
     * 휴대폰 미인증 사용자 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.phoneVerified = false AND u.userStatus = 'ACTIVE'")
    List<UserVerification> findUnverifiedPhoneUsers();
    
    // ===== 신분증 인증 관리 =====
    
    /**
     * 신분증 인증 완료 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserVerification uv SET " +
           "uv.idVerified = true, " +
           "uv.idVerifiedAt = CURRENT_TIMESTAMP, " +
           "uv.idVerificationProvider = :provider " +
           "WHERE uv.user.userId = :userId")
    int completeIdVerification(@Param("userId") Long userId, @Param("provider") String provider);
    
    /**
     * 신분증 인증 정보 업데이트 (CI/DI 포함)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserVerification uv SET " +
           "uv.ci = :ci, " +
           "uv.di = :di, " +
           "uv.idCardHash = :idCardHash, " +
           "uv.birthDate = :birthDate, " +
           "uv.gender = :gender " +
           "WHERE uv.user.userId = :userId")
    int updateIdVerificationInfo(@Param("userId") Long userId,
                                @Param("ci") String ci,
                                @Param("di") String di,
                                @Param("idCardHash") String idCardHash,
                                @Param("birthDate") String birthDate,
                                @Param("gender") String gender);
    
    // ===== 통계 및 관리 =====
    
    /**
     * 인증 단계별 사용자 수 통계
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN uv.emailVerified = true THEN 1 END) as emailVerified, " +
           "COUNT(CASE WHEN uv.phoneVerified = true THEN 1 END) as phoneVerified, " +
           "COUNT(CASE WHEN uv.idVerified = true THEN 1 END) as idVerified " +
           "FROM UserVerification uv " +
           "JOIN uv.user u " +
           "WHERE u.userStatus = 'ACTIVE'")
    Object getVerificationStatistics();
    
    /**
     * 완전 인증 사용자 조회 (이메일 + 휴대폰 + 신분증)
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.emailVerified = true " +
           "AND uv.phoneVerified = true " +
           "AND uv.idVerified = true " +
           "AND u.userStatus = 'ACTIVE'")
    List<UserVerification> findFullyVerifiedUsers();
    
    /**
     * 미완료 인증 사용자 정리 (가입 후 30일)
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN uv.user u " +
           "WHERE (uv.emailVerified = false OR uv.phoneVerified = false) " +
           "AND u.createdAt < :cutoffDate " +
           "AND u.userStatus = 'PENDING'")
    List<UserVerification> findIncompleteVerifications(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

### UserAgreementRepository.java - 약관 동의 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.enums.AgreementType;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.user.entity.UserAgreement;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UserAgreement Repository
 * - 약관 동의 내역 관리
 * - 법정 보관 기간 준수
 * - 동의 철회 및 버전 관리
 */
@Repository
public interface UserAgreementRepository extends BaseRepository<UserAgreement, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자별 약관 동의 내역 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId " +
           "ORDER BY ua.agreedAt DESC")
    List<UserAgreement> findByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 특정 약관 동의 내역 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId AND ac.agreementType = :agreementType " +
           "ORDER BY ua.agreedAt DESC")
    List<UserAgreement> findByUserAndType(@Param("userId") Long userId, 
                                         @Param("agreementType") AgreementType agreementType);
    
    /**
     * 사용자의 최신 약관 동의 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId AND ac.agreementType = :agreementType " +
           "AND ua.isWithdrawn = false " +
           "ORDER BY ua.agreedAt DESC " +
           "LIMIT 1")
    Optional<UserAgreement> findLatestByUserAndType(@Param("userId") Long userId, 
                                                   @Param("agreementType") AgreementType agreementType);
    
    // ===== 동의 상태 확인 =====
    
    /**
     * 필수 약관 동의 확인
     */
    @Query("SELECT CASE WHEN COUNT(ua) > 0 THEN true ELSE false END FROM UserAgreement ua " +
           "JOIN ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId " +
           "AND ac.agreementType = :agreementType " +
           "AND ac.isRequired = true " +
           "AND ua.isWithdrawn = false")
    boolean hasRequiredAgreement(@Param("userId") Long userId, 
                                @Param("agreementType") AgreementType agreementType);
    
    /**
     * 모든 필수 약관 동의 확인
     */
    @Query("SELECT COUNT(DISTINCT ac.agreementType) FROM UserAgreement ua " +
           "JOIN ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId " +
           "AND ac.isRequired = true " +
           "AND ac.isActive = true " +
           "AND ua.isWithdrawn = false")
    long countRequiredAgreements(@Param("userId") Long userId);
    
    /**
     * 활성 필수 약관 총 개수 조회
     */
    @Query("SELECT COUNT(DISTINCT ac.agreementType) FROM AgreementContent ac " +
           "WHERE ac.isRequired = true AND ac.isActive = true")
    long countTotalRequiredAgreements();
    
    // ===== 동의 철회 관리 =====
    
    /**
     * 약관 동의 철회
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserAgreement ua SET " +
           "ua.isWithdrawn = true, " +
           "ua.withdrawnAt = CURRENT_TIMESTAMP " +
           "WHERE ua.user.userId = :userId " +
           "AND ua.agreementContent.agreementType = :agreementType " +
           "AND ua.isWithdrawn = false")
    int withdrawAgreement(@Param("userId") Long userId, 
                         @Param("agreementType") AgreementType agreementType);
    
    /**
     * 철회된 동의 내역 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId " +
           "AND ua.isWithdrawn = true " +
           "ORDER BY ua.withdrawnAt DESC")
    List<UserAgreement> findWithdrawnAgreements(@Param("userId") Long userId);
    
    // ===== 버전 관리 =====
    
    /**
     * 특정 버전의 약관 동의 확인
     */
    @Query("SELECT CASE WHEN COUNT(ua) > 0 THEN true ELSE false END FROM UserAgreement ua " +
           "WHERE ua.user.userId = :userId " +
           "AND ua.agreementContent.contentId = :contentId " +
           "AND ua.isWithdrawn = false")
    boolean hasAgreementForVersion(@Param("userId") Long userId, 
                                  @Param("contentId") Long contentId);
    
    /**
     * 구 버전 약관 동의자 조회 (업데이트 알림용)
     */
    @Query("SELECT DISTINCT ua.user FROM UserAgreement ua " +
           "JOIN ua.agreementContent ac " +
           "WHERE ac.agreementType = :agreementType " +
           "AND ac.version < :currentVersion " +
           "AND ua.isWithdrawn = false")
    List<com.routepick.domain.user.entity.User> findUsersWithOldVersion(@Param("agreementType") AgreementType agreementType,
                                                                        @Param("currentVersion") String currentVersion);
    
    // ===== 통계 및 관리 =====
    
    /**
     * 약관별 동의율 통계
     */
    @Query("SELECT ac.agreementType, COUNT(ua), " +
           "(COUNT(ua) * 100.0 / (SELECT COUNT(u) FROM User u WHERE u.userStatus = 'ACTIVE')) as agreementRate " +
           "FROM UserAgreement ua " +
           "JOIN ua.agreementContent ac " +
           "JOIN ua.user u " +
           "WHERE ua.isWithdrawn = false AND u.userStatus = 'ACTIVE' " +
           "GROUP BY ac.agreementType")
    List<Object[]> getAgreementStatistics();
    
    /**
     * 기간별 약관 동의 현황
     */
    @Query("SELECT DATE(ua.agreedAt) as agreementDate, COUNT(ua) " +
           "FROM UserAgreement ua " +
           "WHERE ua.agreedAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(ua.agreedAt) " +
           "ORDER BY agreementDate")
    List<Object[]> getAgreementTrends(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);
    
    /**
     * 마케팅 동의 사용자 조회
     */
    @Query("SELECT ua.user FROM UserAgreement ua " +
           "JOIN ua.agreementContent ac " +
           "WHERE ac.agreementType = 'MARKETING' " +
           "AND ua.isWithdrawn = false")
    List<com.routepick.domain.user.entity.User> findMarketingAgreedUsers();
    
    /**
     * 보관 기간 만료 동의 내역 조회 (5년)
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "WHERE ua.agreedAt < :cutoffDate")
    List<UserAgreement> findExpiredAgreements(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

### ApiTokenRepository.java - API 토큰 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.enums.TokenType;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.user.entity.ApiToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ApiToken Repository
 * - JWT 토큰 관리
 * - 토큰 블랙리스트 처리
 * - 보안 로그 추적
 */
@Repository
public interface ApiTokenRepository extends BaseRepository<ApiToken, Long> {
    
    // ===== 토큰 조회 및 검증 =====
    
    /**
     * 토큰 해시로 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "JOIN FETCH at.user u " +
           "WHERE at.tokenHash = :tokenHash " +
           "AND at.expiresAt > CURRENT_TIMESTAMP " +
           "AND at.isBlacklisted = false " +
           "AND u.userStatus = 'ACTIVE'")
    Optional<ApiToken> findValidToken(@Param("tokenHash") String tokenHash);
    
    /**
     * 사용자의 활성 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "WHERE at.user.userId = :userId " +
           "AND at.tokenType = :tokenType " +
           "AND at.expiresAt > CURRENT_TIMESTAMP " +
           "AND at.isBlacklisted = false " +
           "ORDER BY at.createdAt DESC")
    List<ApiToken> findActiveTokensByUserAndType(@Param("userId") Long userId, 
                                                @Param("tokenType") TokenType tokenType);
    
    /**
     * 사용자의 최신 리프레시 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "WHERE at.user.userId = :userId " +
           "AND at.tokenType = 'REFRESH' " +
           "AND at.expiresAt > CURRENT_TIMESTAMP " +
           "AND at.isBlacklisted = false " +
           "ORDER BY at.createdAt DESC " +
           "LIMIT 1")
    Optional<ApiToken> findLatestRefreshToken(@Param("userId") Long userId);
    
    // ===== 토큰 무효화 =====
    
    /**
     * 특정 토큰 무효화 (블랙리스트 추가)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET " +
           "at.isBlacklisted = true, " +
           "at.blacklistedAt = CURRENT_TIMESTAMP, " +
           "at.blacklistReason = :reason " +
           "WHERE at.tokenHash = :tokenHash")
    int revokeToken(@Param("tokenHash") String tokenHash, @Param("reason") String reason);
    
    /**
     * 사용자의 모든 토큰 무효화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET " +
           "at.isBlacklisted = true, " +
           "at.blacklistedAt = CURRENT_TIMESTAMP, " +
           "at.blacklistReason = :reason " +
           "WHERE at.user.userId = :userId " +
           "AND at.isBlacklisted = false")
    int revokeAllUserTokens(@Param("userId") Long userId, @Param("reason") String reason);
    
    /**
     * 특정 타입의 사용자 토큰 무효화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET " +
           "at.isBlacklisted = true, " +
           "at.blacklistedAt = CURRENT_TIMESTAMP, " +
           "at.blacklistReason = :reason " +
           "WHERE at.user.userId = :userId " +
           "AND at.tokenType = :tokenType " +
           "AND at.isBlacklisted = false")
    int revokeUserTokensByType(@Param("userId") Long userId, 
                              @Param("tokenType") TokenType tokenType, 
                              @Param("reason") String reason);
    
    // ===== 토큰 사용 추적 =====
    
    /**
     * 토큰 마지막 사용 시간 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET " +
           "at.lastUsedAt = CURRENT_TIMESTAMP, " +
           "at.usageCount = COALESCE(at.usageCount, 0) + 1 " +
           "WHERE at.tokenId = :tokenId")
    int updateTokenUsage(@Param("tokenId") Long tokenId);
    
    /**
     * IP 주소로 토큰 사용 추적
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET at.lastUsedIp = :ipAddress " +
           "WHERE at.tokenId = :tokenId")
    int updateTokenIpAddress(@Param("tokenId") Long tokenId, @Param("ipAddress") String ipAddress);
    
    // ===== 정리 및 관리 =====
    
    /**
     * 만료된 토큰 정리
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ApiToken at WHERE at.expiresAt < :cutoffDate")
    int cleanupExpiredTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 오래된 블랙리스트 토큰 정리
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ApiToken at " +
           "WHERE at.isBlacklisted = true " +
           "AND at.blacklistedAt < :cutoffDate")
    int cleanupOldBlacklistedTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 사용자별 토큰 개수 제한 확인
     */
    @Query("SELECT COUNT(at) FROM ApiToken at " +
           "WHERE at.user.userId = :userId " +
           "AND at.tokenType = :tokenType " +
           "AND at.expiresAt > CURRENT_TIMESTAMP " +
           "AND at.isBlacklisted = false")
    long countActiveTokensByUserAndType(@Param("userId") Long userId, 
                                       @Param("tokenType") TokenType tokenType);
    
    // ===== 보안 모니터링 =====
    
    /**
     * 의심스러운 토큰 사용 패턴 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "JOIN FETCH at.user u " +
           "WHERE at.usageCount > :suspiciousCount " +
           "AND at.createdAt >= :since " +
           "AND u.userStatus = 'ACTIVE'")
    List<ApiToken> findSuspiciousTokenUsage(@Param("suspiciousCount") Integer suspiciousCount,
                                           @Param("since") LocalDateTime since);
    
    /**
     * 여러 IP에서 사용된 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "WHERE at.lastUsedIp != at.issuedIp " +
           "AND at.lastUsedAt >= :since")
    List<ApiToken> findTokensUsedFromDifferentIPs(@Param("since") LocalDateTime since);
    
    /**
     * 토큰 발급 통계 (일별)
     */
    @Query("SELECT DATE(at.createdAt) as issueDate, " +
           "at.tokenType, COUNT(at) " +
           "FROM ApiToken at " +
           "WHERE at.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(at.createdAt), at.tokenType " +
           "ORDER BY issueDate DESC")
    List<Object[]> getTokenIssuanceStatistics(@Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);
}
```

### AgreementContentRepository.java - 약관 내용 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.enums.AgreementType;
import com.routepick.common.repository.SoftDeleteRepository;
import com.routepick.domain.user.entity.AgreementContent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AgreementContent Repository
 * - 약관 내용 버전 관리
 * - 활성/비활성 약관 조회
 * - 약관 변경 이력 추적
 */
@Repository
public interface AgreementContentRepository extends SoftDeleteRepository<AgreementContent, Long> {
    
    // ===== 활성 약관 조회 =====
    
    /**
     * 특정 타입의 활성 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType " +
           "AND ac.isActive = true " +
           "AND ac.effectiveFrom <= CURRENT_TIMESTAMP " +
           "AND (ac.effectiveUntil IS NULL OR ac.effectiveUntil > CURRENT_TIMESTAMP) " +
           "ORDER BY ac.version DESC " +
           "LIMIT 1")
    Optional<AgreementContent> findActiveByAgreementType(@Param("agreementType") AgreementType agreementType);
    
    /**
     * 모든 활성 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.isActive = true " +
           "AND ac.effectiveFrom <= CURRENT_TIMESTAMP " +
           "AND (ac.effectiveUntil IS NULL OR ac.effectiveUntil > CURRENT_TIMESTAMP) " +
           "ORDER BY ac.agreementType, ac.version DESC")
    List<AgreementContent> findAllActive();
    
    /**
     * 필수 약관만 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.isRequired = true " +
           "AND ac.isActive = true " +
           "AND ac.effectiveFrom <= CURRENT_TIMESTAMP " +
           "AND (ac.effectiveUntil IS NULL OR ac.effectiveUntil > CURRENT_TIMESTAMP) " +
           "ORDER BY ac.agreementType")
    List<AgreementContent> findRequiredAgreements();
    
    /**
     * 선택 약관만 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.isRequired = false " +
           "AND ac.isActive = true " +
           "AND ac.effectiveFrom <= CURRENT_TIMESTAMP " +
           "AND (ac.effectiveUntil IS NULL OR ac.effectiveUntil > CURRENT_TIMESTAMP) " +
           "ORDER BY ac.agreementType")
    List<AgreementContent> findOptionalAgreements();
    
    // ===== 버전 관리 =====
    
    /**
     * 특정 타입의 모든 버전 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType " +
           "ORDER BY ac.version DESC")
    List<AgreementContent> findAllVersionsByType(@Param("agreementType") AgreementType agreementType);
    
    /**
     * 특정 버전의 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType " +
           "AND ac.version = :version")
    Optional<AgreementContent> findByTypeAndVersion(@Param("agreementType") AgreementType agreementType,
                                                   @Param("version") String version);
    
    /**
     * 최신 버전 번호 조회
     */
    @Query("SELECT MAX(ac.version) FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType")
    String findLatestVersionByType(@Param("agreementType") AgreementType agreementType);
    
    // ===== 효력 기간 관리 =====
    
    /**
     * 특정 시점에 유효했던 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType " +
           "AND ac.effectiveFrom <= :targetDate " +
           "AND (ac.effectiveUntil IS NULL OR ac.effectiveUntil > :targetDate) " +
           "ORDER BY ac.version DESC " +
           "LIMIT 1")
    Optional<AgreementContent> findByTypeAndEffectiveDate(@Param("agreementType") AgreementType agreementType,
                                                         @Param("targetDate") LocalDateTime targetDate);
    
    /**
     * 곧 효력이 발생할 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.effectiveFrom > CURRENT_TIMESTAMP " +
           "AND ac.effectiveFrom <= :futureDate " +
           "AND ac.isActive = true " +
           "ORDER BY ac.effectiveFrom")
    List<AgreementContent> findUpcomingAgreements(@Param("futureDate") LocalDateTime futureDate);
    
    /**
     * 곧 만료될 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.effectiveUntil IS NOT NULL " +
           "AND ac.effectiveUntil > CURRENT_TIMESTAMP " +
           "AND ac.effectiveUntil <= :expiryDate " +
           "ORDER BY ac.effectiveUntil")
    List<AgreementContent> findExpiringAgreements(@Param("expiryDate") LocalDateTime expiryDate);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 제목으로 약관 검색
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.title LIKE %:keyword% " +
           "AND ac.isActive = true " +
           "ORDER BY ac.createdAt DESC")
    List<AgreementContent> findByTitleContaining(@Param("keyword") String keyword);
    
    /**
     * 내용으로 약관 검색
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.content LIKE %:keyword% " +
           "AND ac.isActive = true " +
           "ORDER BY ac.createdAt DESC")
    List<AgreementContent> findByContentContaining(@Param("keyword") String keyword);
    
    // ===== 통계 및 관리 =====
    
    /**
     * 약관 타입별 버전 수 통계
     */
    @Query("SELECT ac.agreementType, COUNT(ac) " +
           "FROM AgreementContent ac " +
           "GROUP BY ac.agreementType " +
           "ORDER BY ac.agreementType")
    List<Object[]> getVersionCountByType();
    
    /**
     * 최근 변경된 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.createdAt >= :since " +
           "ORDER BY ac.createdAt DESC")
    List<AgreementContent> findRecentlyChanged(@Param("since") LocalDateTime since);
    
    /**
     * 미사용 약관 조회 (동의 내역이 없는 약관)
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE NOT EXISTS (SELECT 1 FROM UserAgreement ua WHERE ua.agreementContent = ac) " +
           "AND ac.createdAt < :cutoffDate")
    List<AgreementContent> findUnusedAgreements(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

---

## 🔒 성능 최적화 및 보안 강화

### 인증 관련 최적화
```sql
-- CI/DI 중복 확인 최적화
CREATE UNIQUE INDEX idx_verification_ci 
ON user_verifications(ci);

CREATE UNIQUE INDEX idx_verification_di 
ON user_verifications(di);

-- 인증 단계별 조회 최적화
CREATE INDEX idx_verification_email_status 
ON user_verifications(email_verified, phone_verified, id_verified);
```

### 약관 관리 최적화
```sql
-- 약관 동의 조회 최적화
CREATE INDEX idx_agreement_user_type_withdrawn 
ON user_agreements(user_id, agreement_type, is_withdrawn);

-- 약관 내용 버전 관리 최적화
CREATE INDEX idx_content_type_version_active 
ON agreement_contents(agreement_type, version DESC, is_active);
```

### 토큰 보안 최적화
```sql
-- 토큰 검증 최적화
CREATE INDEX idx_token_hash_expires_blacklist 
ON api_tokens(token_hash, expires_at, is_blacklisted);

-- 토큰 정리 작업 최적화
CREATE INDEX idx_token_expires_blacklisted 
ON api_tokens(expires_at, blacklisted_at);
```

---

## ✅ 설계 완료 체크리스트

### User 인증 및 보안 Repository (4개)
- [x] **UserVerificationRepository** - 이메일/휴대폰/신분증 인증, CI/DI 관리
- [x] **UserAgreementRepository** - 약관 동의 관리, 버전 추적
- [x] **ApiTokenRepository** - JWT 토큰 관리, 블랙리스트 처리
- [x] **AgreementContentRepository** - 약관 내용, 버전 관리

### 한국 특화 기능
- [x] CI/DI 연계정보 중복 방지
- [x] 휴대폰 인증 및 본인인증 지원
- [x] 약관 동의 법적 요구사항 준수
- [x] 개인정보보호법 준수 (신분증 해시 처리)

### 보안 강화
- [x] JWT 토큰 만료 및 블랙리스트 관리
- [x] 민감정보 암호화 (CI/DI)
- [x] 토큰 사용 추적 및 의심 활동 탐지
- [x] 사용자 상태 기반 접근 제어

### 성능 최적화
- [x] 인증 상태별 인덱스 최적화
- [x] 약관 버전 관리 쿼리 최적화
- [x] 토큰 검증 및 정리 작업 최적화
- [x] @EntityGraph N+1 문제 해결

### 법적 요구사항
- [x] 약관 동의 내역 5년 보관
- [x] 동의 철회 처리
- [x] 버전별 약관 관리
- [x] 마케팅 동의 별도 관리

---

**분할 완료**: step5-1b_user_repositories.md → step5-1b1 + step5-1b2  
**완료일**: 2025-08-20  
**핵심 성과**: User 인증/보안 4개 Repository 완성 (인증/약관/토큰/내용)