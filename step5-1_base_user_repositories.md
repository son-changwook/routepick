# Step 5-1: 기본 Repository 및 User 도메인 Repository 설계

> 공통 Repository 인터페이스, QueryDSL 설정, User 도메인 Repository 완전 설계  
> 생성일: 2025-08-20  
> 기반: step4-1_base_user_entities.md, 이메일 기반 로그인 최적화

---

## 🎯 설계 목표

- **BaseRepository 설계**: JpaRepository + QuerydslPredicateExecutor 통합
- **QueryDSL 설정**: 동적 쿼리 빌더 패턴, JPAQueryFactory 활용
- **Projection 인터페이스**: 성능 최적화를 위한 필요 데이터만 조회
- **User 도메인 Repository**: 이메일 기반 로그인, 소셜 인증 최적화
- **성능 최적화**: N+1 문제 해결, 인덱스 활용, @EntityGraph 설계

---

## 📋 1. 공통 Repository 기본 설계

### BaseRepository.java - 기본 Repository 인터페이스
```java
package com.routepick.common.repository;

import com.routepick.common.entity.BaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 기본 Repository 인터페이스
 * - JpaRepository + QuerydslPredicateExecutor 통합
 * - 공통 조회 메서드 제공
 * - 감사(Auditing) 기반 조회 메서드
 */
@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity, ID extends Serializable> 
        extends JpaRepository<T, ID>, QuerydslPredicateExecutor<T> {
    
    // ===== 공통 조회 메서드 =====
    
    /**
     * 특정 날짜 범위로 생성된 엔티티 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.createdAt BETWEEN :startDate AND :endDate ORDER BY e.createdAt DESC")
    List<T> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * 특정 날짜 범위로 생성된 엔티티 페이징 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.createdAt BETWEEN :startDate AND :endDate ORDER BY e.createdAt DESC")
    Page<T> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                           @Param("endDate") LocalDateTime endDate, 
                           Pageable pageable);
    
    /**
     * 특정 사용자가 생성한 엔티티 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.createdBy = :userId ORDER BY e.createdAt DESC")
    List<T> findByCreatedBy(@Param("userId") Long userId);
    
    /**
     * 특정 사용자가 생성한 엔티티 페이징 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.createdBy = :userId ORDER BY e.createdAt DESC")
    Page<T> findByCreatedBy(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 최근 수정된 엔티티 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.updatedAt >= :since ORDER BY e.updatedAt DESC")
    List<T> findRecentlyModified(@Param("since") LocalDateTime since);
    
    /**
     * 최근 생성된 엔티티 조회 (Top N)
     */
    @Query("SELECT e FROM #{#entityName} e ORDER BY e.createdAt DESC")
    List<T> findRecentlyCreated(Pageable pageable);
    
    // ===== 통계 관련 메서드 =====
    
    /**
     * 날짜 범위별 생성 개수 조회
     */
    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.createdAt BETWEEN :startDate AND :endDate")
    long countByDateRange(@Param("startDate") LocalDateTime startDate, 
                         @Param("endDate") LocalDateTime endDate);
    
    /**
     * 특정 사용자의 생성 개수 조회
     */
    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.createdBy = :userId")
    long countByCreatedBy(@Param("userId") Long userId);
    
    // ===== 배치 작업 메서드 =====
    
    /**
     * 벌크 업데이트: 특정 조건의 엔티티 lastModifiedBy 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE #{#entityName} e SET e.lastModifiedBy = :modifiedBy, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id IN :ids")
    int bulkUpdateModifiedBy(@Param("ids") List<ID> ids, @Param("modifiedBy") Long modifiedBy);
    
    /**
     * 벌크 삭제: 특정 날짜 이전 생성된 엔티티 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM #{#entityName} e WHERE e.createdAt < :cutoffDate")
    int bulkDeleteOldEntities(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

### SoftDeleteRepository.java - 소프트 삭제 Repository
```java
package com.routepick.common.repository;

import com.routepick.common.entity.SoftDeleteEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 소프트 삭제 Repository 인터페이스
 * - 삭제되지 않은 엔티티만 조회
 * - 소프트 삭제 관련 메서드 제공
 */
@NoRepositoryBean
public interface SoftDeleteRepository<T extends SoftDeleteEntity, ID extends Serializable> 
        extends BaseRepository<T, ID> {
    
    // ===== 활성 엔티티 조회 =====
    
    /**
     * 삭제되지 않은 모든 엔티티 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.isDeleted = false ORDER BY e.createdAt DESC")
    List<T> findAllActive();
    
    /**
     * 삭제되지 않은 엔티티 페이징 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.isDeleted = false ORDER BY e.createdAt DESC")
    Page<T> findAllActive(Pageable pageable);
    
    /**
     * ID로 활성 엔티티 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.isDeleted = false")
    Optional<T> findActiveById(@Param("id") ID id);
    
    /**
     * 삭제된 엔티티 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.isDeleted = true ORDER BY e.deletedAt DESC")
    List<T> findAllDeleted();
    
    /**
     * 삭제된 엔티티 페이징 조회
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.isDeleted = true ORDER BY e.deletedAt DESC")
    Page<T> findAllDeleted(Pageable pageable);
    
    // ===== 소프트 삭제 작업 =====
    
    /**
     * ID로 소프트 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE #{#entityName} e SET e.isDeleted = true, e.deletedAt = CURRENT_TIMESTAMP, e.deletedBy = :deletedBy WHERE e.id = :id AND e.isDeleted = false")
    int softDeleteById(@Param("id") ID id, @Param("deletedBy") Long deletedBy);
    
    /**
     * 여러 ID로 소프트 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE #{#entityName} e SET e.isDeleted = true, e.deletedAt = CURRENT_TIMESTAMP, e.deletedBy = :deletedBy WHERE e.id IN :ids AND e.isDeleted = false")
    int softDeleteByIds(@Param("ids") List<ID> ids, @Param("deletedBy") Long deletedBy);
    
    /**
     * 소프트 삭제 복구
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE #{#entityName} e SET e.isDeleted = false, e.deletedAt = null, e.deletedBy = null WHERE e.id = :id AND e.isDeleted = true")
    int restoreById(@Param("id") ID id);
    
    // ===== 정리 작업 =====
    
    /**
     * 오래된 소프트 삭제 엔티티 물리 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM #{#entityName} e WHERE e.isDeleted = true AND e.deletedAt < :cutoffDate")
    int permanentDeleteOldEntities(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 삭제된 엔티티 개수 조회
     */
    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.isDeleted = true")
    long countDeleted();
    
    /**
     * 활성 엔티티 개수 조회
     */
    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.isDeleted = false")
    long countActive();
}
```

---

## ⚙️ 2. QueryDSL 설정 기본 구조

### QueryDslConfig.java - QueryDSL 설정
```java
package com.routepick.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 설정
 * - JPAQueryFactory Bean 등록
 * - 동적 쿼리 지원
 */
@Configuration
public class QueryDslConfig {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
```

### BaseQueryDslRepository.java - QueryDSL 기본 Repository
```java
package com.routepick.common.repository;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.routepick.common.entity.BaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * QueryDSL 기본 Repository 구현체
 * - 동적 쿼리 빌더 패턴 제공
 * - 공통 조건 빌더 메서드
 */
public abstract class BaseQueryDslRepository<T extends BaseEntity> extends QuerydslRepositorySupport {
    
    protected final JPAQueryFactory queryFactory;
    
    protected BaseQueryDslRepository(Class<T> domainClass, JPAQueryFactory queryFactory) {
        super(domainClass);
        this.queryFactory = queryFactory;
    }
    
    // ===== 공통 조건 빌더 =====
    
    /**
     * 날짜 범위 조건
     */
    protected BooleanExpression dateRangePredicate(com.querydsl.core.types.dsl.DateTimePath<LocalDateTime> dateField,
                                                  LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null && endDate == null) return null;
        if (startDate == null) return dateField.loe(endDate);
        if (endDate == null) return dateField.goe(startDate);
        return dateField.between(startDate, endDate);
    }
    
    /**
     * 생성자 조건
     */
    protected BooleanExpression createdByPredicate(com.querydsl.core.types.dsl.NumberPath<Long> createdByField,
                                                  Long userId) {
        return userId != null ? createdByField.eq(userId) : null;
    }
    
    /**
     * 키워드 검색 조건 (대소문자 무시)
     */
    protected BooleanExpression keywordPredicate(com.querydsl.core.types.dsl.StringPath field, String keyword) {
        return keyword != null && !keyword.trim().isEmpty() ? 
               field.containsIgnoreCase(keyword.trim()) : null;
    }
    
    /**
     * 한글 검색 조건 (초성 검색 지원)
     */
    protected BooleanExpression koreanKeywordPredicate(com.querydsl.core.types.dsl.StringPath field, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return null;
        
        // 한글 초성 검색 지원 로직 (예: 'ㄱㅁㅅ' -> '김민수')
        String trimmedKeyword = keyword.trim();
        
        // 일반 문자열 검색
        BooleanExpression normalSearch = field.containsIgnoreCase(trimmedKeyword);
        
        // 초성 검색이 필요한 경우 추가 로직 구현
        // 현재는 기본 검색만 제공
        return normalSearch;
    }
    
    // ===== 페이징 조회 헬퍼 =====
    
    /**
     * 페이징 조회 실행
     */
    protected Page<T> executePagingQuery(JPAQuery<T> query, Pageable pageable) {
        long total = query.fetchCount();
        
        List<T> content = getQuerydsl()
                .applyPagination(pageable, query)
                .fetch();
        
        return new PageImpl<>(content, pageable, total);
    }
    
    /**
     * 정렬 적용된 페이징 조회
     */
    protected Page<T> executePagingQueryWithSort(JPAQuery<T> baseQuery, Pageable pageable) {
        JPAQuery<T> query = getQuerydsl().applySorting(pageable.getSort(), baseQuery);
        return executePagingQuery(query, pageable);
    }
}
```

---

## 📊 3. Projection 인터페이스 설계

### UserSummaryProjection.java - 사용자 기본 정보 Projection
```java
package com.routepick.domain.user.projection;

import com.routepick.common.enums.UserStatus;
import com.routepick.common.enums.UserType;

import java.time.LocalDateTime;

/**
 * 사용자 요약 정보 Projection
 * - 목록 조회 시 사용
 * - 민감정보 제외
 */
public interface UserSummaryProjection {
    
    Long getUserId();
    String getEmail();
    String getUserName();
    String getNickName();
    UserType getUserType();
    UserStatus getUserStatus();
    LocalDateTime getLastLoginAt();
    Integer getLoginCount();
    LocalDateTime getCreatedAt();
    
    // UserProfile 정보
    String getGender();
    String getProfileImageUrl();
    Integer getClimbingYears();
    Integer getTotalClimbCount();
    Integer getFollowerCount();
    Integer getFollowingCount();
    Boolean getIsPublic();
    
    // ClimbingLevel 정보
    String getLevelName();
    String getLevelColor();
}
```

### GymBranchLocationProjection.java - 지점 위치 정보 Projection
```java
package com.routepick.domain.gym.projection;

import com.routepick.common.enums.BranchStatus;

import java.math.BigDecimal;

/**
 * 체육관 지점 위치 정보 Projection
 * - 지도 표시용
 * - 위치 정보만 포함
 */
public interface GymBranchLocationProjection {
    
    Long getBranchId();
    String getBranchName();
    BigDecimal getLatitude();
    BigDecimal getLongitude();
    String getAddress();
    String getDetailAddress();
    BranchStatus getBranchStatus();
    
    // Gym 정보
    String getGymName();
    String getGymPhone();
    
    // 통계 정보
    Integer getActiveRouteCount();
    Integer getTotalMemberCount();
}
```

### RouteBasicProjection.java - 루트 기본 정보 Projection
```java
package com.routepick.domain.route.projection;

import com.routepick.common.enums.RouteStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 루트 기본 정보 Projection
 * - 루트 목록 조회용
 * - 추천 시스템용
 */
public interface RouteBasicProjection {
    
    Long getRouteId();
    String getRouteName();
    String getRouteColor();
    String getGrade();
    RouteStatus getRouteStatus();
    LocalDate getSetDate();
    LocalDate getExpectedRemovalDate();
    LocalDateTime getCreatedAt();
    
    // Wall 정보
    String getWallName();
    String getWallType();
    Integer getWallAngle();
    Integer getWallHeight();
    
    // GymBranch 정보
    String getBranchName();
    String getGymName();
    
    // ClimbingLevel 정보
    String getLevelName();
    Integer getDifficultyScore();
    
    // 통계 정보
    Integer getClimbCount();
    Integer getCommentCount();
    Integer getScrapCount();
    BigDecimal getAverageRating();
    
    // 태그 정보 (JSON)
    String getTagNames();
}
```

### TagStatisticsProjection.java - 태그 통계 Projection
```java
package com.routepick.domain.tag.projection;

import com.routepick.common.enums.TagType;

/**
 * 태그 통계 정보 Projection
 * - 대시보드용
 * - 추천 시스템 통계용
 */
public interface TagStatisticsProjection {
    
    Long getTagId();
    String getTagName();
    TagType getTagType();
    String getTagColor();
    
    // 사용 통계
    Integer getRouteCount();
    Integer getUserPreferenceCount();
    Integer getRecommendationCount();
    
    // 인기도 지표
    Integer getPopularityScore();
    Double getAverageRecommendationScore();
    
    // 최근 활동
    java.time.LocalDateTime getLastUsedAt();
    Integer getWeeklyUsageCount();
    Integer getMonthlyUsageCount();
}
```

---

## 👤 4. User 도메인 Repository 설계 (7개)

### UserRepository.java - 사용자 기본 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.enums.UserStatus;
import com.routepick.common.enums.UserType;
import com.routepick.common.repository.SoftDeleteRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.projection.UserSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * User Repository
 * - 이메일 기반 로그인 최적화
 * - Spring Security UserDetailsService 지원
 * - 사용자 통계 및 관리 기능
 */
@Repository
public interface UserRepository extends SoftDeleteRepository<User, Long> {
    
    // ===== 기본 조회 메서드 (이메일 기반 로그인) =====
    
    /**
     * 이메일로 사용자 조회 (로그인용)
     */
    @EntityGraph(attributePaths = {"userProfile", "socialAccounts"})
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.userStatus != 'DELETED'")
    Optional<User> findByEmail(@Param("email") String email);
    
    /**
     * 이메일과 상태로 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.userStatus = :status")
    Optional<User> findByEmailAndUserStatus(@Param("email") String email, @Param("status") UserStatus status);
    
    /**
     * 활성 사용자만 이메일로 조회
     */
    @EntityGraph(attributePaths = {"userProfile", "userVerification"})
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.userStatus = 'ACTIVE'")
    Optional<User> findActiveByEmail(@Param("email") String email);
    
    // ===== 중복 확인 메서드 =====
    
    /**
     * 이메일 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email AND u.userStatus != 'DELETED'")
    boolean existsByEmail(@Param("email") String email);
    
    /**
     * 닉네임 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.nickName = :nickName AND u.userStatus != 'DELETED'")
    boolean existsByNickName(@Param("nickName") String nickName);
    
    /**
     * 휴대폰 번호 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.phone = :phone AND u.userStatus != 'DELETED'")
    boolean existsByPhone(@Param("phone") String phone);
    
    // ===== 닉네임 및 검색 메서드 =====
    
    /**
     * 닉네임으로 사용자 조회
     */
    @EntityGraph(attributePaths = {"userProfile"})
    @Query("SELECT u FROM User u WHERE u.nickName = :nickName AND u.userStatus = 'ACTIVE'")
    Optional<User> findByNickName(@Param("nickName") String nickName);
    
    /**
     * 한글 닉네임 부분 검색
     */
    @Query("SELECT u FROM User u WHERE u.nickName LIKE %:keyword% AND u.userStatus = 'ACTIVE' ORDER BY u.nickName")
    List<UserSummaryProjection> findByNickNameContaining(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 사용자명 부분 검색
     */
    @Query("SELECT u FROM User u WHERE u.userName LIKE %:keyword% AND u.userStatus = 'ACTIVE' ORDER BY u.userName")
    List<UserSummaryProjection> findByUserNameContaining(@Param("keyword") String keyword, Pageable pageable);
    
    // ===== 사용자 타입별 조회 =====
    
    /**
     * 사용자 타입별 조회
     */
    @Query("SELECT u FROM User u WHERE u.userType = :userType AND u.userStatus = 'ACTIVE' ORDER BY u.createdAt DESC")
    Page<UserSummaryProjection> findByUserType(@Param("userType") UserType userType, Pageable pageable);
    
    /**
     * 관리자 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.userType IN ('ADMIN', 'GYM_ADMIN') AND u.userStatus = 'ACTIVE' ORDER BY u.lastLoginAt DESC")
    List<UserSummaryProjection> findAllAdmins();
    
    // ===== 로그인 관련 메서드 =====
    
    /**
     * 로그인 성공 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.lastLoginAt = CURRENT_TIMESTAMP, " +
           "u.loginCount = COALESCE(u.loginCount, 0) + 1, " +
           "u.failedLoginCount = 0, " +
           "u.lastFailedLoginAt = null " +
           "WHERE u.userId = :userId")
    int updateLoginSuccess(@Param("userId") Long userId);
    
    /**
     * 로그인 실패 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.failedLoginCount = COALESCE(u.failedLoginCount, 0) + 1, " +
           "u.lastFailedLoginAt = CURRENT_TIMESTAMP " +
           "WHERE u.userId = :userId")
    int updateLoginFailure(@Param("userId") Long userId);
    
    /**
     * 계정 잠금 처리 (로그인 실패 5회 이상)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.userStatus = 'SUSPENDED' " +
           "WHERE u.userId = :userId AND u.failedLoginCount >= 5")
    int lockAccount(@Param("userId") Long userId);
    
    // ===== 통계 및 관리 메서드 =====
    
    /**
     * 기간별 가입자 수 조회
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate AND u.userStatus != 'DELETED'")
    long countByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * 상태별 사용자 수 조회
     */
    @Query("SELECT u.userStatus, COUNT(u) FROM User u WHERE u.userStatus != 'DELETED' GROUP BY u.userStatus")
    List<Object[]> countByUserStatus();
    
    /**
     * 최근 활성 사용자 조회 (30일 이내 로그인)
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt >= :since AND u.userStatus = 'ACTIVE' ORDER BY u.lastLoginAt DESC")
    Page<UserSummaryProjection> findRecentActiveUsers(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * 미인증 사용자 조회 (이메일 미인증)
     */
    @Query("SELECT u FROM User u JOIN u.userVerification uv " +
           "WHERE uv.emailVerified = false AND u.createdAt >= :since AND u.userStatus = 'ACTIVE'")
    List<User> findUnverifiedUsers(@Param("since") LocalDateTime since);
    
    /**
     * 장기 미접속 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt < :cutoffDate AND u.userStatus = 'ACTIVE' ORDER BY u.lastLoginAt")
    Page<UserSummaryProjection> findInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);
    
    // ===== 비밀번호 관리 =====
    
    /**
     * 비밀번호 변경
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.passwordHash = :passwordHash, u.passwordChangedAt = CURRENT_TIMESTAMP " +
           "WHERE u.userId = :userId")
    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
    
    /**
     * 비밀번호 만료 예정 사용자 조회 (90일 기준)
     */
    @Query("SELECT u FROM User u WHERE u.passwordChangedAt < :expiryDate AND u.userStatus = 'ACTIVE'")
    List<User> findUsersWithExpiringPasswords(@Param("expiryDate") LocalDateTime expiryDate);
}
```

### UserProfileRepository.java - 사용자 프로필 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.user.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * UserProfile Repository
 * - 사용자 프로필 관리
 * - 완등 통계 업데이트
 * - 팔로워/팔로잉 통계 관리
 */
@Repository
public interface UserProfileRepository extends BaseRepository<UserProfile, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자 ID로 프로필 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "LEFT JOIN FETCH up.climbingLevel " +
           "LEFT JOIN FETCH up.homeBranch " +
           "WHERE up.user.userId = :userId")
    Optional<UserProfile> findByUserId(@Param("userId") Long userId);
    
    /**
     * 공개 프로필만 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findAllPublicProfiles(Pageable pageable);
    
    // ===== 프로필 이미지 관리 =====
    
    /**
     * 프로필 이미지 URL 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.profileImageUrl = :imageUrl " +
           "WHERE up.user.userId = :userId")
    int updateProfileImage(@Param("userId") Long userId, @Param("imageUrl") String imageUrl);
    
    /**
     * 프로필 이미지 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.profileImageUrl = null " +
           "WHERE up.user.userId = :userId")
    int deleteProfileImage(@Param("userId") Long userId);
    
    // ===== 검색 및 필터링 =====
    
    /**
     * 클라이밍 레벨별 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.climbingLevel.levelId = :levelId AND up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findByClimbingLevel(@Param("levelId") Long levelId, Pageable pageable);
    
    /**
     * 홈 지점별 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.homeBranch.branchId = :branchId AND up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findByHomeBranch(@Param("branchId") Long branchId, Pageable pageable);
    
    /**
     * 클라이밍 경력별 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.climbingYears >= :minYears AND up.climbingYears <= :maxYears " +
           "AND up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findByClimbingYearsRange(@Param("minYears") Integer minYears, 
                                              @Param("maxYears") Integer maxYears, 
                                              Pageable pageable);
    
    /**
     * 성별별 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.gender = :gender AND up.isPublic = true AND u.userStatus = 'ACTIVE'")
    Page<UserProfile> findByGender(@Param("gender") String gender, Pageable pageable);
    
    // ===== 완등 통계 관리 =====
    
    /**
     * 완등 카운트 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET " +
           "up.totalClimbCount = COALESCE(up.totalClimbCount, 0) + 1, " +
           "up.monthlyClimbCount = COALESCE(up.monthlyClimbCount, 0) + 1 " +
           "WHERE up.user.userId = :userId")
    int incrementClimbCount(@Param("userId") Long userId);
    
    /**
     * 월간 완등 카운트 리셋
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.monthlyClimbCount = 0")
    int resetMonthlyClimbCount();
    
    /**
     * 특정 사용자의 월간 완등 카운트 리셋
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.monthlyClimbCount = 0 WHERE up.user.userId = :userId")
    int resetUserMonthlyClimbCount(@Param("userId") Long userId);
    
    // ===== 팔로워/팔로잉 통계 관리 =====
    
    /**
     * 팔로워 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.followerCount = COALESCE(up.followerCount, 0) + 1 " +
           "WHERE up.user.userId = :userId")
    int incrementFollowerCount(@Param("userId") Long userId);
    
    /**
     * 팔로워 수 감소
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.followerCount = GREATEST(COALESCE(up.followerCount, 0) - 1, 0) " +
           "WHERE up.user.userId = :userId")
    int decrementFollowerCount(@Param("userId") Long userId);
    
    /**
     * 팔로잉 수 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.followingCount = COALESCE(up.followingCount, 0) + 1 " +
           "WHERE up.user.userId = :userId")
    int incrementFollowingCount(@Param("userId") Long userId);
    
    /**
     * 팔로잉 수 감소
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserProfile up SET up.followingCount = GREATEST(COALESCE(up.followingCount, 0) - 1, 0) " +
           "WHERE up.user.userId = :userId")
    int decrementFollowingCount(@Param("userId") Long userId);
    
    // ===== 통계 조회 =====
    
    /**
     * 인기 사용자 조회 (팔로워 수 기준)
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.isPublic = true AND u.userStatus = 'ACTIVE' " +
           "ORDER BY up.followerCount DESC, up.totalClimbCount DESC")
    Page<UserProfile> findPopularUsers(Pageable pageable);
    
    /**
     * 활발한 클라이머 조회 (월간 완등 수 기준)
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.isPublic = true AND u.userStatus = 'ACTIVE' AND up.monthlyClimbCount > 0 " +
           "ORDER BY up.monthlyClimbCount DESC")
    Page<UserProfile> findActiveClimbers(Pageable pageable);
    
    /**
     * 프로필 완성도 높은 사용자 조회
     */
    @Query("SELECT up FROM UserProfile up " +
           "JOIN FETCH up.user u " +
           "WHERE up.isPublic = true AND u.userStatus = 'ACTIVE' " +
           "AND up.profileImageUrl IS NOT NULL " +
           "AND up.bio IS NOT NULL AND up.bio != '' " +
           "AND up.climbingLevel IS NOT NULL " +
           "AND up.homeBranch IS NOT NULL")
    Page<UserProfile> findCompleteProfiles(Pageable pageable);
}
```

### SocialAccountRepository.java - 소셜 계정 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.enums.SocialProvider;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.user.entity.SocialAccount;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SocialAccount Repository
 * - 소셜 로그인 핵심 Repository
 * - 4개 Provider 지원 (Google, Kakao, Naver, Facebook)
 * - 토큰 관리 및 중복 확인
 */
@Repository
public interface SocialAccountRepository extends BaseRepository<SocialAccount, Long> {
    
    // ===== 소셜 로그인 핵심 메서드 =====
    
    /**
     * Provider와 소셜 ID로 계정 조회 (로그인 핵심)
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "JOIN FETCH sa.user u " +
           "WHERE sa.provider = :provider AND sa.socialId = :socialId AND u.userStatus = 'ACTIVE'")
    Optional<SocialAccount> findByProviderAndSocialId(@Param("provider") SocialProvider provider, 
                                                      @Param("socialId") String socialId);
    
    /**
     * Provider와 소셜 ID 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(sa) > 0 THEN true ELSE false END FROM SocialAccount sa " +
           "JOIN sa.user u " +
           "WHERE sa.provider = :provider AND sa.socialId = :socialId AND u.userStatus != 'DELETED'")
    boolean existsByProviderAndSocialId(@Param("provider") SocialProvider provider, 
                                       @Param("socialId") String socialId);
    
    // ===== 사용자별 소셜 계정 관리 =====
    
    /**
     * 사용자의 모든 소셜 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.user.userId = :userId " +
           "ORDER BY sa.isPrimary DESC, sa.lastLoginAt DESC")
    List<SocialAccount> findByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 특정 Provider 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.user.userId = :userId AND sa.provider = :provider")
    Optional<SocialAccount> findByUserAndProvider(@Param("userId") Long userId, 
                                                 @Param("provider") SocialProvider provider);
    
    /**
     * 사용자의 Primary 소셜 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.user.userId = :userId AND sa.isPrimary = true")
    Optional<SocialAccount> findPrimaryByUserId(@Param("userId") Long userId);
    
    // ===== Primary 계정 관리 =====
    
    /**
     * 사용자의 모든 소셜 계정 Primary 해제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SocialAccount sa SET sa.isPrimary = false WHERE sa.user.userId = :userId")
    int clearAllPrimaryByUserId(@Param("userId") Long userId);
    
    /**
     * 특정 소셜 계정을 Primary로 설정
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SocialAccount sa SET sa.isPrimary = true WHERE sa.socialAccountId = :socialAccountId")
    int setPrimaryAccount(@Param("socialAccountId") Long socialAccountId);
    
    // ===== 토큰 관리 =====
    
    /**
     * 토큰 정보 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SocialAccount sa SET " +
           "sa.accessToken = :accessToken, " +
           "sa.refreshToken = :refreshToken, " +
           "sa.tokenExpiresAt = :expiresAt " +
           "WHERE sa.socialAccountId = :socialAccountId")
    int updateTokens(@Param("socialAccountId") Long socialAccountId,
                    @Param("accessToken") String accessToken,
                    @Param("refreshToken") String refreshToken,
                    @Param("expiresAt") LocalDateTime expiresAt);
    
    /**
     * 마지막 로그인 시간 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE SocialAccount sa SET sa.lastLoginAt = CURRENT_TIMESTAMP " +
           "WHERE sa.socialAccountId = :socialAccountId")
    int updateLastLoginAt(@Param("socialAccountId") Long socialAccountId);
    
    /**
     * 만료된 토큰을 가진 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.tokenExpiresAt < CURRENT_TIMESTAMP")
    List<SocialAccount> findExpiredTokenAccounts();
    
    // ===== Provider별 통계 =====
    
    /**
     * Provider별 계정 수 조회
     */
    @Query("SELECT sa.provider, COUNT(sa) FROM SocialAccount sa " +
           "JOIN sa.user u " +
           "WHERE u.userStatus = 'ACTIVE' " +
           "GROUP BY sa.provider")
    List<Object[]> countByProvider();
    
    /**
     * 특정 Provider의 활성 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "JOIN FETCH sa.user u " +
           "WHERE sa.provider = :provider AND u.userStatus = 'ACTIVE' " +
           "ORDER BY sa.lastLoginAt DESC")
    List<SocialAccount> findActiveByProvider(@Param("provider") SocialProvider provider);
    
    /**
     * 한국 Provider (카카오, 네이버) 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "JOIN FETCH sa.user u " +
           "WHERE sa.provider IN ('KAKAO', 'NAVER') AND u.userStatus = 'ACTIVE' " +
           "ORDER BY sa.lastLoginAt DESC")
    List<SocialAccount> findKoreanProviderAccounts();
    
    // ===== 중복 계정 관리 =====
    
    /**
     * 동일한 소셜 이메일을 가진 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.socialEmail = :socialEmail AND sa.provider != :excludeProvider")
    List<SocialAccount> findBySocialEmailExcludeProvider(@Param("socialEmail") String socialEmail,
                                                        @Param("excludeProvider") SocialProvider excludeProvider);
    
    /**
     * 사용자별 소셜 계정 수 조회
     */
    @Query("SELECT COUNT(sa) FROM SocialAccount sa WHERE sa.user.userId = :userId")
    int countByUserId(@Param("userId") Long userId);
    
    // ===== 정리 작업 =====
    
    /**
     * 비활성 사용자의 소셜 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "JOIN sa.user u " +
           "WHERE u.userStatus IN ('INACTIVE', 'SUSPENDED', 'DELETED')")
    List<SocialAccount> findByInactiveUsers();
    
    /**
     * 오랫동안 사용하지 않은 소셜 계정 조회
     */
    @Query("SELECT sa FROM SocialAccount sa " +
           "WHERE sa.lastLoginAt < :cutoffDate " +
           "ORDER BY sa.lastLoginAt")
    List<SocialAccount> findUnusedAccounts(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

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
 * - 한국 본인인증 시스템 지원
 * - 이메일/휴대폰 인증 관리
 * - CI/DI 기반 중복 가입 방지
 */
@Repository
public interface UserVerificationRepository extends BaseRepository<UserVerification, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자 ID로 인증 정보 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "WHERE uv.user.userId = :userId")
    Optional<UserVerification> findByUserId(@Param("userId") Long userId);
    
    /**
     * CI로 기존 인증 정보 조회 (중복 가입 방지)
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.ci = :ci AND u.userStatus != 'DELETED'")
    Optional<UserVerification> findByCi(@Param("ci") String ci);
    
    /**
     * DI로 기존 인증 정보 조회 (중복 가입 방지)
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.di = :di AND u.userStatus != 'DELETED'")
    Optional<UserVerification> findByDi(@Param("di") String di);
    
    // ===== 이메일 인증 관리 =====
    
    /**
     * 이메일과 토큰으로 인증 정보 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE u.email = :email AND uv.verificationToken = :token " +
           "AND uv.tokenExpiresAt > CURRENT_TIMESTAMP")
    Optional<UserVerification> findByEmailAndToken(@Param("email") String email, 
                                                  @Param("token") String token);
    
    /**
     * 이메일 인증 완료 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserVerification uv SET " +
           "uv.emailVerified = true, " +
           "uv.verificationToken = null, " +
           "uv.tokenExpiresAt = null " +
           "WHERE uv.verificationId = :verificationId")
    int markEmailVerified(@Param("verificationId") Long verificationId);
    
    /**
     * 이메일 인증 토큰 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserVerification uv SET " +
           "uv.verificationToken = :token, " +
           "uv.tokenExpiresAt = :expiresAt " +
           "WHERE uv.user.userId = :userId")
    int updateEmailVerificationToken(@Param("userId") Long userId,
                                   @Param("token") String token,
                                   @Param("expiresAt") LocalDateTime expiresAt);
    
    // ===== 휴대폰 인증 관리 =====
    
    /**
     * 휴대폰 번호로 인증 정보 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.phoneNumber = :phoneNumber AND u.userStatus != 'DELETED'")
    List<UserVerification> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);
    
    /**
     * 휴대폰 번호 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(uv) > 0 THEN true ELSE false END FROM UserVerification uv " +
           "JOIN uv.user u " +
           "WHERE uv.phoneNumber = :phoneNumber AND u.userStatus != 'DELETED'")
    boolean existsByPhoneNumber(@Param("phoneNumber") String phoneNumber);
    
    // ===== 인증 상태별 조회 =====
    
    /**
     * 완전 인증된 사용자 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.phoneVerified = true AND uv.emailVerified = true AND u.userStatus = 'ACTIVE'")
    List<UserVerification> findFullyVerifiedUsers();
    
    /**
     * 이메일 미인증 사용자 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.emailVerified = false AND u.userStatus = 'ACTIVE'")
    List<UserVerification> findEmailUnverifiedUsers();
    
    /**
     * 휴대폰 미인증 사용자 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.phoneVerified = false AND u.userStatus = 'ACTIVE'")
    List<UserVerification> findPhoneUnverifiedUsers();
    
    /**
     * 성인 인증된 사용자 조회
     */
    @Query("SELECT uv FROM UserVerification uv " +
           "JOIN FETCH uv.user u " +
           "WHERE uv.adultVerified = true AND u.userStatus = 'ACTIVE'")
    List<UserVerification> findAdultVerifiedUsers();
    
    // ===== 토큰 정리 작업 =====
    
    /**
     * 만료된 이메일 인증 토큰 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserVerification uv SET " +
           "uv.verificationToken = null, " +
           "uv.tokenExpiresAt = null " +
           "WHERE uv.tokenExpiresAt < CURRENT_TIMESTAMP AND uv.emailVerified = false")
    int deleteExpiredTokens();
    
    /**
     * 이미 인증 완료된 토큰 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserVerification uv SET " +
           "uv.verificationToken = null, " +
           "uv.tokenExpiresAt = null " +
           "WHERE uv.emailVerified = true AND uv.verificationToken IS NOT NULL")
    int deleteVerifiedUserTokens();
    
    // ===== 인증 시도 제한 =====
    
    /**
     * 특정 이메일의 최근 인증 토큰 생성 횟수 조회 (1시간 이내)
     */
    @Query("SELECT COUNT(uv) FROM UserVerification uv " +
           "JOIN uv.user u " +
           "WHERE u.email = :email AND uv.updatedAt >= :since")
    int countRecentVerificationAttempts(@Param("email") String email, 
                                       @Param("since") LocalDateTime since);
    
    /**
     * 특정 IP의 최근 인증 시도 횟수 조회 (스팸 방지)
     */
    @Query("SELECT COUNT(uv) FROM UserVerification uv " +
           "WHERE uv.updatedAt >= :since")
    int countRecentVerificationsByIP(@Param("since") LocalDateTime since);
    
    // ===== 통계 조회 =====
    
    /**
     * 인증 방법별 통계
     */
    @Query("SELECT uv.verificationMethod, COUNT(uv) FROM UserVerification uv " +
           "WHERE uv.verificationMethod IS NOT NULL " +
           "GROUP BY uv.verificationMethod")
    List<Object[]> countByVerificationMethod();
    
    /**
     * 통신사별 통계
     */
    @Query("SELECT uv.telecom, COUNT(uv) FROM UserVerification uv " +
           "WHERE uv.telecom IS NOT NULL " +
           "GROUP BY uv.telecom")
    List<Object[]> countByTelecom();
    
    /**
     * 연령대별 통계 (성인 인증 기준)
     */
    @Query("SELECT " +
           "CASE " +
           "WHEN uv.adultVerified = false THEN '미성년자' " +
           "ELSE '성인' " +
           "END, COUNT(uv) " +
           "FROM UserVerification uv " +
           "GROUP BY uv.adultVerified")
    List<Object[]> countByAgeGroup();
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
 * - 사용자 약관 동의 이력 관리
 * - 법적 증빙을 위한 동의 기록
 * - 약관 버전별 동의 상태 추적
 */
@Repository
public interface UserAgreementRepository extends BaseRepository<UserAgreement, Long> {
    
    // ===== 기본 조회 메서드 =====
    
    /**
     * 사용자의 모든 약관 동의 이력 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId " +
           "ORDER BY ua.agreementType, ua.agreedAt DESC")
    List<UserAgreement> findByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 특정 약관 동의 정보 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId AND ua.agreementType = :agreementType " +
           "ORDER BY ua.agreedAt DESC")
    List<UserAgreement> findByUserIdAndAgreementType(@Param("userId") Long userId, 
                                                    @Param("agreementType") AgreementType agreementType);
    
    /**
     * 사용자의 최신 약관 동의 정보 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId AND ua.agreementType = :agreementType " +
           "ORDER BY ua.agreedAt DESC LIMIT 1")
    Optional<UserAgreement> findLatestByUserIdAndAgreementType(@Param("userId") Long userId, 
                                                              @Param("agreementType") AgreementType agreementType);
    
    // ===== 동의 상태 조회 =====
    
    /**
     * 사용자가 동의한 약관들만 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.agreementContent ac " +
           "WHERE ua.user.userId = :userId AND ua.isAgreed = true " +
           "ORDER BY ua.agreementType")
    List<UserAgreement> findAgreedByUserId(@Param("userId") Long userId);
    
    /**
     * 특정 약관에 동의한 사용자 수 조회
     */
    @Query("SELECT COUNT(DISTINCT ua.user.userId) FROM UserAgreement ua " +
           "WHERE ua.agreementType = :agreementType AND ua.isAgreed = true")
    long countAgreedUsers(@Param("agreementType") AgreementType agreementType);
    
    /**
     * 필수 약관 모두 동의한 사용자인지 확인
     */
    @Query("SELECT CASE WHEN COUNT(ua) = " +
           "(SELECT COUNT(at) FROM AgreementType at WHERE at.required = true) " +
           "THEN true ELSE false END " +
           "FROM UserAgreement ua " +
           "WHERE ua.user.userId = :userId AND ua.isAgreed = true " +
           "AND ua.agreementType IN (SELECT at FROM AgreementType at WHERE at.required = true)")
    boolean hasAllRequiredAgreements(@Param("userId") Long userId);
    
    // ===== 약관 버전 관리 =====
    
    /**
     * 특정 버전의 약관에 동의한 사용자 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.user u " +
           "WHERE ua.agreementType = :agreementType AND ua.agreedVersion = :version AND ua.isAgreed = true")
    List<UserAgreement> findByAgreementTypeAndVersion(@Param("agreementType") AgreementType agreementType,
                                                     @Param("version") String version);
    
    /**
     * 구버전 약관에 동의한 사용자 조회 (재동의 필요)
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN FETCH ua.user u " +
           "JOIN FETCH ua.agreementContent ac " +
           "WHERE ua.agreementType = :agreementType AND ua.isAgreed = true " +
           "AND ua.agreedVersion != ac.version AND ac.isActive = true")
    List<UserAgreement> findOutdatedAgreements(@Param("agreementType") AgreementType agreementType);
    
    /**
     * 최신 버전 약관에 동의하지 않은 사용자 조회
     */
    @Query("SELECT DISTINCT u.userId FROM User u " +
           "WHERE u.userId NOT IN (" +
           "    SELECT ua.user.userId FROM UserAgreement ua " +
           "    JOIN ua.agreementContent ac " +
           "    WHERE ua.agreementType = :agreementType AND ua.isAgreed = true " +
           "    AND ua.agreedVersion = ac.version AND ac.isActive = true" +
           ") AND u.userStatus = 'ACTIVE'")
    List<Long> findUsersWithoutLatestAgreement(@Param("agreementType") AgreementType agreementType);
    
    // ===== 동의 상태 변경 =====
    
    /**
     * 약관 동의 상태 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserAgreement ua SET " +
           "ua.isAgreed = :isAgreed, " +
           "ua.agreedAt = CASE WHEN :isAgreed = true THEN CURRENT_TIMESTAMP ELSE ua.agreedAt END, " +
           "ua.disagreedAt = CASE WHEN :isAgreed = false THEN CURRENT_TIMESTAMP ELSE null END " +
           "WHERE ua.agreementId = :agreementId")
    int updateAgreementStatus(@Param("agreementId") Long agreementId, @Param("isAgreed") boolean isAgreed);
    
    /**
     * IP 주소와 User-Agent 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserAgreement ua SET " +
           "ua.ipAddress = :ipAddress, " +
           "ua.userAgent = :userAgent " +
           "WHERE ua.agreementId = :agreementId")
    int updateAgreementMetadata(@Param("agreementId") Long agreementId,
                              @Param("ipAddress") String ipAddress,
                              @Param("userAgent") String userAgent);
    
    // ===== 통계 및 분석 =====
    
    /**
     * 약관별 동의율 조회
     */
    @Query("SELECT ua.agreementType, " +
           "COUNT(CASE WHEN ua.isAgreed = true THEN 1 END) as agreedCount, " +
           "COUNT(ua) as totalCount " +
           "FROM UserAgreement ua " +
           "GROUP BY ua.agreementType")
    List<Object[]> getAgreementStatistics();
    
    /**
     * 기간별 약관 동의 현황
     */
    @Query("SELECT DATE(ua.agreedAt), COUNT(ua) FROM UserAgreement ua " +
           "WHERE ua.agreedAt BETWEEN :startDate AND :endDate AND ua.isAgreed = true " +
           "GROUP BY DATE(ua.agreedAt) " +
           "ORDER BY DATE(ua.agreedAt)")
    List<Object[]> getAgreementTrendByDate(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
    
    /**
     * 마케팅 수신 동의한 사용자 수
     */
    @Query("SELECT COUNT(DISTINCT ua.user.userId) FROM UserAgreement ua " +
           "WHERE ua.agreementType = 'MARKETING' AND ua.isAgreed = true")
    long countMarketingAgreedUsers();
    
    /**
     * 위치정보 이용 동의한 사용자 수
     */
    @Query("SELECT COUNT(DISTINCT ua.user.userId) FROM UserAgreement ua " +
           "WHERE ua.agreementType = 'LOCATION' AND ua.isAgreed = true")
    long countLocationAgreedUsers();
    
    // ===== 정리 작업 =====
    
    /**
     * 탈퇴한 사용자의 약관 동의 이력 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserAgreement ua " +
           "WHERE ua.user.userId IN (" +
           "    SELECT u.userId FROM User u WHERE u.userStatus = 'DELETED'" +
           ") AND ua.createdAt < :cutoffDate")
    int deleteAgreementsOfDeletedUsers(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 비활성 약관 내용과 연결된 동의 이력 조회
     */
    @Query("SELECT ua FROM UserAgreement ua " +
           "JOIN ua.agreementContent ac " +
           "WHERE ac.isActive = false")
    List<UserAgreement> findAgreementsWithInactiveContent();
}
```

### ApiTokenRepository.java - API 토큰 Repository
```java
package com.routepick.domain.user.repository;

import com.routepick.common.enums.TokenType;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.user.entity.ApiToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * - 토큰 블랙리스트 관리
 * - 보안 및 세션 관리
 */
@Repository
public interface ApiTokenRepository extends BaseRepository<ApiToken, Long> {
    
    // ===== 토큰 조회 및 검증 =====
    
    /**
     * 토큰 값으로 유효한 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "JOIN FETCH at.user u " +
           "WHERE at.token = :token AND at.isActive = true AND at.isBlacklisted = false " +
           "AND at.expiresAt > CURRENT_TIMESTAMP AND u.userStatus = 'ACTIVE'")
    Optional<ApiToken> findValidToken(@Param("token") String token);
    
    /**
     * 토큰과 타입으로 유효한 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "JOIN FETCH at.user u " +
           "WHERE at.token = :token AND at.tokenType = :tokenType " +
           "AND at.isActive = true AND at.isBlacklisted = false " +
           "AND at.expiresAt > CURRENT_TIMESTAMP AND u.userStatus = 'ACTIVE'")
    Optional<ApiToken> findValidTokenByTypeAndToken(@Param("token") String token, 
                                                   @Param("tokenType") TokenType tokenType);
    
    /**
     * 토큰 존재 여부 확인 (중복 방지)
     */
    @Query("SELECT CASE WHEN COUNT(at) > 0 THEN true ELSE false END FROM ApiToken at " +
           "WHERE at.token = :token")
    boolean existsByToken(@Param("token") String token);
    
    // ===== 사용자별 토큰 관리 =====
    
    /**
     * 사용자의 활성 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "WHERE at.user.userId = :userId AND at.isActive = true AND at.isBlacklisted = false " +
           "ORDER BY at.createdAt DESC")
    List<ApiToken> findActiveTokensByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 특정 타입 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "WHERE at.user.userId = :userId AND at.tokenType = :tokenType " +
           "AND at.isActive = true AND at.isBlacklisted = false " +
           "ORDER BY at.createdAt DESC")
    List<ApiToken> findTokensByUserIdAndType(@Param("userId") Long userId, 
                                           @Param("tokenType") TokenType tokenType);
    
    /**
     * 사용자의 특정 타입 토큰 수 조회
     */
    @Query("SELECT COUNT(at) FROM ApiToken at " +
           "WHERE at.user.userId = :userId AND at.tokenType = :tokenType " +
           "AND at.isActive = true AND at.isBlacklisted = false")
    int countTokensByUserIdAndType(@Param("userId") Long userId, 
                                  @Param("tokenType") TokenType tokenType);
    
    /**
     * 사용자의 모든 토큰 조회 (관리자용)
     */
    @Query("SELECT at FROM ApiToken at " +
           "WHERE at.user.userId = :userId " +
           "ORDER BY at.createdAt DESC")
    Page<ApiToken> findAllTokensByUserId(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 토큰 폐기 및 블랙리스트 =====
    
    /**
     * 특정 토큰 폐기
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET " +
           "at.isActive = false, " +
           "at.isBlacklisted = true, " +
           "at.revokedAt = CURRENT_TIMESTAMP, " +
           "at.revokedReason = :reason " +
           "WHERE at.token = :token")
    int revokeToken(@Param("token") String token, @Param("reason") String reason);
    
    /**
     * 사용자의 모든 토큰 폐기
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET " +
           "at.isActive = false, " +
           "at.isBlacklisted = true, " +
           "at.revokedAt = CURRENT_TIMESTAMP, " +
           "at.revokedReason = :reason " +
           "WHERE at.user.userId = :userId AND at.isActive = true")
    int revokeAllUserTokens(@Param("userId") Long userId, @Param("reason") String reason);
    
    /**
     * 특정 타입의 사용자 토큰 폐기
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET " +
           "at.isActive = false, " +
           "at.isBlacklisted = true, " +
           "at.revokedAt = CURRENT_TIMESTAMP, " +
           "at.revokedReason = :reason " +
           "WHERE at.user.userId = :userId AND at.tokenType = :tokenType AND at.isActive = true")
    int revokeUserTokensByType(@Param("userId") Long userId, 
                              @Param("tokenType") TokenType tokenType, 
                              @Param("reason") String reason);
    
    // ===== 토큰 사용 기록 =====
    
    /**
     * 토큰 사용 시간 업데이트
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET at.lastUsedAt = CURRENT_TIMESTAMP " +
           "WHERE at.tokenId = :tokenId")
    int updateLastUsedAt(@Param("tokenId") Long tokenId);
    
    /**
     * 리프레시 카운트 증가
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiToken at SET at.refreshCount = COALESCE(at.refreshCount, 0) + 1 " +
           "WHERE at.tokenId = :tokenId")
    int incrementRefreshCount(@Param("tokenId") Long tokenId);
    
    // ===== 만료 및 정리 작업 =====
    
    /**
     * 만료된 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "WHERE at.expiresAt < CURRENT_TIMESTAMP")
    List<ApiToken> findExpiredTokens();
    
    /**
     * 만료된 토큰 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ApiToken at " +
           "WHERE at.expiresAt < :cutoffDate")
    int deleteExpiredTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 폐기된 토큰 삭제 (30일 이후)
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ApiToken at " +
           "WHERE at.isBlacklisted = true AND at.revokedAt < :cutoffDate")
    int deleteRevokedTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 비활성 사용자의 토큰 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ApiToken at " +
           "WHERE at.user.userId IN (" +
           "    SELECT u.userId FROM User u WHERE u.userStatus IN ('SUSPENDED', 'DELETED')" +
           ")")
    int deleteInactiveUserTokens();
    
    // ===== 보안 및 모니터링 =====
    
    /**
     * 특정 IP에서 생성된 토큰 조회
     */
    @Query("SELECT at FROM ApiToken at " +
           "WHERE at.ipAddress = :ipAddress " +
           "ORDER BY at.createdAt DESC")
    List<ApiToken> findTokensByIpAddress(@Param("ipAddress") String ipAddress);
    
    /**
     * 의심스러운 활동 감지 (같은 IP에서 많은 토큰 생성)
     */
    @Query("SELECT at.ipAddress, COUNT(at) FROM ApiToken at " +
           "WHERE at.createdAt >= :since " +
           "GROUP BY at.ipAddress " +
           "HAVING COUNT(at) > :threshold " +
           "ORDER BY COUNT(at) DESC")
    List<Object[]> findSuspiciousIpActivities(@Param("since") LocalDateTime since, 
                                            @Param("threshold") int threshold);
    
    /**
     * 리프레시 횟수가 많은 토큰 조회 (의심스러운 활동)
     */
    @Query("SELECT at FROM ApiToken at " +
           "WHERE at.refreshCount > :threshold " +
           "ORDER BY at.refreshCount DESC")
    List<ApiToken> findHighRefreshTokens(@Param("threshold") int threshold);
    
    // ===== 통계 조회 =====
    
    /**
     * 토큰 타입별 통계
     */
    @Query("SELECT at.tokenType, COUNT(at) FROM ApiToken at " +
           "WHERE at.isActive = true " +
           "GROUP BY at.tokenType")
    List<Object[]> countActiveTokensByType();
    
    /**
     * 일별 토큰 생성 통계
     */
    @Query("SELECT DATE(at.createdAt), COUNT(at) FROM ApiToken at " +
           "WHERE at.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(at.createdAt) " +
           "ORDER BY DATE(at.createdAt)")
    List<Object[]> getTokenCreationTrend(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
    
    /**
     * 활성 토큰 총 개수
     */
    @Query("SELECT COUNT(at) FROM ApiToken at " +
           "WHERE at.isActive = true AND at.isBlacklisted = false " +
           "AND at.expiresAt > CURRENT_TIMESTAMP")
    long countActiveTokens();
}
```

### AgreementContentRepository.java - 약관 내용 Repository
```java
package com.routepick.domain.system.repository;

import com.routepick.common.enums.AgreementType;
import com.routepick.common.repository.BaseRepository;
import com.routepick.domain.system.entity.AgreementContent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AgreementContent Repository
 * - 약관 내용 버전 관리
 * - 현재 유효한 약관 조회
 * - 약관 이력 관리
 */
@Repository
public interface AgreementContentRepository extends BaseRepository<AgreementContent, Long> {
    
    // ===== 현재 유효한 약관 조회 =====
    
    /**
     * 특정 타입의 현재 활성 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType AND ac.isActive = true " +
           "AND ac.effectiveDate <= CURRENT_TIMESTAMP " +
           "AND (ac.expiryDate IS NULL OR ac.expiryDate > CURRENT_TIMESTAMP) " +
           "ORDER BY ac.effectiveDate DESC")
    Optional<AgreementContent> findActiveByAgreementType(@Param("agreementType") AgreementType agreementType);
    
    /**
     * 모든 현재 활성 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.isActive = true " +
           "AND ac.effectiveDate <= CURRENT_TIMESTAMP " +
           "AND (ac.expiryDate IS NULL OR ac.expiryDate > CURRENT_TIMESTAMP) " +
           "ORDER BY ac.displayOrder, ac.agreementType")
    List<AgreementContent> findAllActive();
    
    /**
     * 필수 약관만 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.isActive = true AND ac.isRequired = true " +
           "AND ac.effectiveDate <= CURRENT_TIMESTAMP " +
           "AND (ac.expiryDate IS NULL OR ac.expiryDate > CURRENT_TIMESTAMP) " +
           "ORDER BY ac.displayOrder")
    List<AgreementContent> findAllRequiredActive();
    
    /**
     * 선택 약관만 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.isActive = true AND ac.isRequired = false " +
           "AND ac.effectiveDate <= CURRENT_TIMESTAMP " +
           "AND (ac.expiryDate IS NULL OR ac.expiryDate > CURRENT_TIMESTAMP) " +
           "ORDER BY ac.displayOrder")
    List<AgreementContent> findAllOptionalActive();
    
    // ===== 버전 관리 =====
    
    /**
     * 특정 타입의 모든 버전 조회 (최신순)
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType " +
           "ORDER BY ac.version DESC, ac.effectiveDate DESC")
    List<AgreementContent> findAllVersionsByType(@Param("agreementType") AgreementType agreementType);
    
    /**
     * 특정 타입의 최신 버전 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType " +
           "ORDER BY ac.version DESC, ac.effectiveDate DESC " +
           "LIMIT 1")
    Optional<AgreementContent> findLatestVersionByType(@Param("agreementType") AgreementType agreementType);
    
    /**
     * 특정 타입과 버전으로 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType AND ac.version = :version")
    Optional<AgreementContent> findByTypeAndVersion(@Param("agreementType") AgreementType agreementType,
                                                   @Param("version") String version);
    
    /**
     * 버전 중복 확인
     */
    @Query("SELECT CASE WHEN COUNT(ac) > 0 THEN true ELSE false END FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType AND ac.version = :version")
    boolean existsByTypeAndVersion(@Param("agreementType") AgreementType agreementType,
                                  @Param("version") String version);
    
    // ===== 약관 활성화 관리 =====
    
    /**
     * 특정 타입의 모든 약관 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AgreementContent ac SET ac.isActive = false " +
           "WHERE ac.agreementType = :agreementType")
    int deactivateAllByType(@Param("agreementType") AgreementType agreementType);
    
    /**
     * 특정 약관 활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AgreementContent ac SET " +
           "ac.isActive = true, " +
           "ac.approvedAdminId = :adminId, " +
           "ac.approvedAt = CURRENT_TIMESTAMP " +
           "WHERE ac.contentId = :contentId")
    int activateAgreement(@Param("contentId") Long contentId, @Param("adminId") Long adminId);
    
    /**
     * 특정 약관 비활성화
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AgreementContent ac SET ac.isActive = false " +
           "WHERE ac.contentId = :contentId")
    int deactivateAgreement(@Param("contentId") Long contentId);
    
    // ===== 시간 기반 조회 =====
    
    /**
     * 특정 날짜에 유효했던 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.agreementType = :agreementType " +
           "AND ac.effectiveDate <= :targetDate " +
           "AND (ac.expiryDate IS NULL OR ac.expiryDate > :targetDate) " +
           "ORDER BY ac.effectiveDate DESC " +
           "LIMIT 1")
    Optional<AgreementContent> findEffectiveAtDate(@Param("agreementType") AgreementType agreementType,
                                                  @Param("targetDate") LocalDateTime targetDate);
    
    /**
     * 곧 시행될 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.isActive = true " +
           "AND ac.effectiveDate > CURRENT_TIMESTAMP " +
           "AND ac.effectiveDate <= :futureDate " +
           "ORDER BY ac.effectiveDate")
    List<AgreementContent> findUpcomingAgreements(@Param("futureDate") LocalDateTime futureDate);
    
    /**
     * 곧 만료될 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.isActive = true " +
           "AND ac.expiryDate IS NOT NULL " +
           "AND ac.expiryDate > CURRENT_TIMESTAMP " +
           "AND ac.expiryDate <= :futureDate " +
           "ORDER BY ac.expiryDate")
    List<AgreementContent> findExpiringAgreements(@Param("futureDate") LocalDateTime futureDate);
    
    // ===== 관리자 및 승인 관련 =====
    
    /**
     * 특정 관리자가 생성한 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.createdAdminId = :adminId " +
           "ORDER BY ac.createdAt DESC")
    List<AgreementContent> findByCreatedAdmin(@Param("adminId") Long adminId);
    
    /**
     * 특정 관리자가 승인한 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.approvedAdminId = :adminId " +
           "ORDER BY ac.approvedAt DESC")
    List<AgreementContent> findByApprovedAdmin(@Param("adminId") Long adminId);
    
    /**
     * 승인 대기 중인 약관 조회
     */
    @Query("SELECT ac FROM AgreementContent ac " +
           "WHERE ac.isActive = false AND ac.approvedAt IS NULL " +
           "ORDER BY ac.createdAt")
    List<AgreementContent> findPendingApproval();
    
    // ===== 통계 및 분석 =====
    
    /**
     * 약관 타입별 버전 수 통계
     */
    @Query("SELECT ac.agreementType, COUNT(ac) FROM AgreementContent ac " +
           "GROUP BY ac.agreementType")
    List<Object[]> countVersionsByType();
    
    /**
     * 월별 약관 생성 통계
     */
    @Query("SELECT YEAR(ac.createdAt), MONTH(ac.createdAt), COUNT(ac) FROM AgreementContent ac " +
           "WHERE ac.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(ac.createdAt), MONTH(ac.createdAt) " +
           "ORDER BY YEAR(ac.createdAt), MONTH(ac.createdAt)")
    List<Object[]> getCreationTrendByMonth(@Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);
    
    /**
     * 현재 활성 약관 수
     */
    @Query("SELECT COUNT(ac) FROM AgreementContent ac " +
           "WHERE ac.isActive = true " +
           "AND ac.effectiveDate <= CURRENT_TIMESTAMP " +
           "AND (ac.expiryDate IS NULL OR ac.expiryDate > CURRENT_TIMESTAMP)")
    long countCurrentActive();
    
    // ===== 정리 작업 =====
    
    /**
     * 오래된 비활성 약관 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AgreementContent ac " +
           "WHERE ac.isActive = false " +
           "AND ac.createdAt < :cutoffDate " +
           "AND ac.contentId NOT IN (" +
           "    SELECT ua.agreementContent.contentId FROM UserAgreement ua" +
           ")")
    int deleteOldUnusedAgreements(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

---

## 🔒 5. 성능 최적화 원칙

### N+1 문제 해결
```java
// @EntityGraph 사용
@EntityGraph(attributePaths = {"userProfile", "socialAccounts"})
Optional<User> findByEmail(String email);

// Fetch Join 사용
@Query("SELECT u FROM User u " +
       "LEFT JOIN FETCH u.userProfile " +
       "LEFT JOIN FETCH u.userVerification " +
       "WHERE u.userId = :userId")
Optional<User> findWithDetails(@Param("userId") Long userId);
```

### 인덱스 활용 최적화
```java
// 복합 인덱스 활용
@Index(name = "idx_social_provider_id", columnList = "provider, social_id")

// 정렬 인덱스 활용
@Index(name = "idx_users_created", columnList = "created_at DESC")

// 유니크 제약조건
@Index(name = "idx_users_email", columnList = "email", unique = true)
```

### 배치 작업 최적화
```java
// 벌크 업데이트
@Modifying(clearAutomatically = true)
@Query("UPDATE User u SET u.loginCount = u.loginCount + 1 WHERE u.userId IN :userIds")
int bulkUpdateLoginCount(@Param("userIds") List<Long> userIds);

// 페이징 처리
Page<UserSummaryProjection> findByNickNameContaining(String keyword, Pageable pageable);
```

---

## 🛡️ 6. 보안 강화 사항

### SQL Injection 방지
```java
// 파라미터 바인딩 사용
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// JPQL 사용으로 안전성 확보
@Query("SELECT COUNT(u) FROM User u WHERE u.nickName LIKE %:keyword%")
long countByNickNameContaining(@Param("keyword") String keyword);
```

### 민감정보 보호
```java
// Projection으로 필요한 정보만 조회
UserSummaryProjection // 패스워드 필드 제외

// 암호화된 필드 처리
@Convert(converter = AESCryptoConverter.class)
private String ci; // 연계정보 암호화
```

### 토큰 보안
```java
// 만료 시간 자동 검증
@Query("WHERE at.expiresAt > CURRENT_TIMESTAMP")

// 블랙리스트 관리
@Query("WHERE at.isBlacklisted = false")
```

---

## ✅ 설계 완료 체크리스트

### 공통 Repository 설계
- [x] BaseRepository 인터페이스 (공통 메서드)
- [x] SoftDeleteRepository 인터페이스 (소프트 삭제)
- [x] QueryDSL 설정 및 기본 Repository

### Projection 설계
- [x] UserSummaryProjection (사용자 요약)
- [x] GymBranchLocationProjection (지점 위치)
- [x] RouteBasicProjection (루트 기본)
- [x] TagStatisticsProjection (태그 통계)

### User 도메인 Repository (7개)
- [x] UserRepository (이메일 기반 로그인 최적화)
- [x] UserProfileRepository (프로필 관리)
- [x] SocialAccountRepository (소셜 로그인 핵심)
- [x] UserVerificationRepository (본인인증)
- [x] UserAgreementRepository (약관 동의)
- [x] ApiTokenRepository (JWT 토큰 관리)
- [x] AgreementContentRepository (약관 내용)

### 성능 최적화
- [x] @EntityGraph로 N+1 문제 해결
- [x] 복합 인덱스 활용
- [x] 배치 작업 최적화
- [x] Projection으로 조회 최적화

### 보안 강화
- [x] 파라미터 바인딩으로 SQL Injection 방지
- [x] 민감정보 Projection 제외
- [x] 토큰 만료 및 블랙리스트 관리
- [x] 이메일 기반 로그인 보안

---

**다음 단계**: Step 5-2 Gym, Route, Tag 도메인 Repository 설계  
**완료일**: 2025-08-20  
**핵심 성과**: BaseRepository + QueryDSL + User 도메인 7개 Repository 완전 설계