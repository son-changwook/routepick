# Step 5-1a: 공통 Repository 인터페이스 설계

> BaseRepository, SoftDeleteRepository, QueryDSL 설정, Projection 인터페이스 완전 설계  
> 생성일: 2025-08-20  
> 분할: step5-1_base_user_repositories.md → 공통 부분 추출

---

## 🎯 설계 목표

- **BaseRepository 설계**: JpaRepository + QuerydslPredicateExecutor 통합
- **QueryDSL 설정**: 동적 쿼리 빌더 패턴, JPAQueryFactory 활용
- **Projection 인터페이스**: 성능 최적화를 위한 필요 데이터만 조회
- **SoftDelete 지원**: 논리적 삭제 및 복구 기능
- **성능 최적화**: N+1 문제 해결, 인덱스 활용, 동적 쿼리 지원

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

## ✅ 설계 완료 체크리스트

### ✅ 공통 Repository 인터페이스
- [x] BaseRepository 기본 메서드 (날짜, 생성자, 통계, 벌크 작업)
- [x] SoftDeleteRepository 소프트 삭제 메서드
- [x] QuerydslPredicateExecutor 통합
- [x] @NoRepositoryBean 어노테이션

### ✅ QueryDSL 설정
- [x] QueryDslConfig JPAQueryFactory Bean 등록
- [x] BaseQueryDslRepository 동적 쿼리 헬퍼
- [x] 공통 조건 빌더 (날짜, 키워드, 한글 검색)
- [x] 페이징 조회 헬퍼 메서드

### ✅ Projection 인터페이스
- [x] UserSummaryProjection 사용자 요약 정보
- [x] GymBranchLocationProjection 지점 위치 정보
- [x] RouteBasicProjection 루트 기본 정보
- [x] TagStatisticsProjection 태그 통계 정보

---

*분할 작업 완료: step5-1_base_user_repositories.md → step5-1a_common_repositories.md*  
*다음 파일: step5-1b1_user_core_repositories.md (User 핵심 Repository), step5-1b2_user_verification_repositories.md (User 인증/보안 Repository)*