# Step 5-4d: 결제 시스템 Repository 생성

## 개요
- **목적**: 결제 시스템 Repository 생성 (보안 및 트랜잭션 무결성 특화)
- **대상**: PaymentRecordRepository, PaymentDetailRepository, PaymentItemRepository, PaymentRefundRepository
- **최적화**: PCI DSS 준수, 결제 보안 강화, 환불 처리 효율화

## 1. PaymentRecordRepository (결제 기록 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.payment;

import com.routepick.backend.domain.entity.payment.PaymentRecord;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 기록 Repository
 * - 결제 트랜잭션 무결성 보장
 * - 결제 보안 강화 및 민감정보 보호
 * - 매출 분석 및 통계 지원
 */
@Repository
public interface PaymentRecordRepository extends BaseRepository<PaymentRecord, Long> {
    
    // 기본 조회 메서드
    List<PaymentRecord> findByUserIdAndPaymentStatusOrderByPaymentDateDesc(
        Long userId, String paymentStatus);
    
    Optional<PaymentRecord> findByTransactionIdAndPaymentStatus(
        String transactionId, String paymentStatus);
    
    List<PaymentRecord> findByPaymentDateBetweenAndPaymentStatus(
        LocalDateTime startDate, LocalDateTime endDate, String paymentStatus);
    
    List<PaymentRecord> findByPaymentMethodAndCreatedAtAfter(
        String paymentMethod, LocalDateTime after);
    
    // 결제 금액 계산
    @Query("SELECT SUM(pr.totalAmount) FROM PaymentRecord pr " +
           "WHERE pr.userId = :userId AND pr.paymentStatus = 'COMPLETED' " +
           "AND pr.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal calculateTotalAmountByUserAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 결제 동향 분석
    @Query("SELECT new com.routepick.backend.application.dto.projection.PaymentTrendProjection(" +
           "DATE(pr.paymentDate), COUNT(pr), SUM(pr.totalAmount), AVG(pr.totalAmount)) " +
           "FROM PaymentRecord pr " +
           "WHERE pr.paymentDate BETWEEN :startDate AND :endDate " +
           "AND pr.paymentStatus = 'COMPLETED' " +
           "GROUP BY DATE(pr.paymentDate) " +
           "ORDER BY DATE(pr.paymentDate) DESC")
    List<PaymentTrendProjection> findPaymentTrends(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 실패 결제 분석
    @Query("SELECT pr FROM PaymentRecord pr " +
           "WHERE pr.paymentStatus IN ('FAILED', 'CANCELLED') " +
           "AND pr.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY pr.createdAt DESC")
    List<PaymentRecord> findFailedPayments(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 매출 통계
    @Query("SELECT new com.routepick.backend.application.dto.projection.RevenueStatisticsProjection(" +
           "pr.paymentMethod, COUNT(pr), SUM(pr.totalAmount), AVG(pr.totalAmount), " +
           "MIN(pr.totalAmount), MAX(pr.totalAmount)) " +
           "FROM PaymentRecord pr " +
           "WHERE pr.paymentStatus = 'COMPLETED' " +
           "AND pr.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY pr.paymentMethod " +
           "ORDER BY SUM(pr.totalAmount) DESC")
    List<RevenueStatisticsProjection> calculateRevenueStatistics(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 결제 상태 업데이트 (보안 강화)
    @Transactional
    @Modifying
    @Query("UPDATE PaymentRecord pr SET pr.paymentStatus = :newStatus, " +
           "pr.updatedAt = CURRENT_TIMESTAMP, pr.approvedAt = CASE WHEN :newStatus = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE pr.approvedAt END " +
           "WHERE pr.transactionId = :transactionId AND pr.paymentStatus = :currentStatus")
    int updatePaymentStatus(
        @Param("transactionId") String transactionId,
        @Param("currentStatus") String currentStatus,
        @Param("newStatus") String newStatus);
    
    // 중복 결제 체크
    @Query("SELECT CASE WHEN COUNT(pr) > 0 THEN true ELSE false END " +
           "FROM PaymentRecord pr " +
           "WHERE pr.userId = :userId AND pr.merchantUid = :merchantUid " +
           "AND pr.paymentStatus = 'COMPLETED' " +
           "AND pr.paymentDate > :checkTime")
    boolean existsDuplicatePayment(
        @Param("userId") Long userId,
        @Param("merchantUid") String merchantUid,
        @Param("checkTime") LocalDateTime checkTime);
    
    // 고액 결제 조회
    @Query("SELECT pr FROM PaymentRecord pr " +
           "WHERE pr.totalAmount >= :threshold " +
           "AND pr.paymentStatus = 'COMPLETED' " +
           "AND pr.paymentDate BETWEEN :startDate AND :endDate " +
           "ORDER BY pr.totalAmount DESC")
    List<PaymentRecord> findHighValuePayments(
        @Param("threshold") BigDecimal threshold,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 할부 결제 조회
    @Query("SELECT pr FROM PaymentRecord pr " +
           "WHERE pr.installmentMonths > 0 " +
           "AND pr.paymentStatus = 'COMPLETED' " +
           "ORDER BY pr.paymentDate DESC")
    List<PaymentRecord> findInstallmentPayments();
    
    // 사용자별 최근 결제
    @Query("SELECT pr FROM PaymentRecord pr " +
           "WHERE pr.userId = :userId " +
           "AND pr.paymentStatus = 'COMPLETED' " +
           "ORDER BY pr.paymentDate DESC")
    List<PaymentRecord> findRecentPaymentsByUser(
        @Param("userId") Long userId, Pageable pageable);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.payment.custom;

import com.routepick.backend.application.dto.payment.PaymentSearchCriteria;
import com.routepick.backend.application.dto.projection.PaymentAnalyticsProjection;
import com.routepick.backend.application.dto.projection.UserPaymentSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 결제 기록 커스텀 Repository
 */
public interface PaymentRecordRepositoryCustom {
    
    // 고급 검색
    Page<PaymentRecord> searchPayments(PaymentSearchCriteria criteria, Pageable pageable);
    
    // 결제 분석
    List<PaymentAnalyticsProjection> analyzePaymentPatterns(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 사용자별 결제 요약
    UserPaymentSummaryProjection getUserPaymentSummary(
        Long userId, LocalDateTime startDate, LocalDateTime endDate);
    
    // 결제 방법별 통계
    Map<String, BigDecimal> getPaymentMethodStatistics(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 시간대별 결제 분석
    List<PaymentAnalyticsProjection> analyzePaymentsByTimeOfDay(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 이상 거래 탐지
    List<PaymentRecord> detectAnomalousTransactions(
        BigDecimal thresholdAmount, int maxAttempts, LocalDateTime timeWindow);
    
    // 결제 성공률 분석
    Map<String, Double> calculatePaymentSuccessRates(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 배치 상태 업데이트
    void batchUpdatePaymentStatus(List<String> transactionIds, String newStatus);
}
```

### Custom Repository 구현
```java
package com.routepick.backend.infrastructure.persistence.repository.payment.custom;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.routepick.backend.domain.entity.payment.PaymentRecord;
import com.routepick.backend.domain.entity.payment.QPaymentRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 결제 기록 커스텀 Repository 구현
 */
@Repository
@RequiredArgsConstructor
public class PaymentRecordRepositoryCustomImpl implements PaymentRecordRepositoryCustom {
    
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    
    private static final QPaymentRecord paymentRecord = QPaymentRecord.paymentRecord;
    
    @Override
    public Page<PaymentRecord> searchPayments(PaymentSearchCriteria criteria, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        
        if (criteria.getUserId() != null) {
            builder.and(paymentRecord.userId.eq(criteria.getUserId()));
        }
        
        if (criteria.getPaymentStatus() != null) {
            builder.and(paymentRecord.paymentStatus.eq(criteria.getPaymentStatus()));
        }
        
        if (criteria.getPaymentMethod() != null) {
            builder.and(paymentRecord.paymentMethod.eq(criteria.getPaymentMethod()));
        }
        
        if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
            builder.and(paymentRecord.paymentDate.between(
                criteria.getStartDate(), criteria.getEndDate()));
        }
        
        if (criteria.getMinAmount() != null) {
            builder.and(paymentRecord.totalAmount.goe(criteria.getMinAmount()));
        }
        
        if (criteria.getMaxAmount() != null) {
            builder.and(paymentRecord.totalAmount.loe(criteria.getMaxAmount()));
        }
        
        List<PaymentRecord> content = queryFactory
            .selectFrom(paymentRecord)
            .where(builder)
            .orderBy(paymentRecord.paymentDate.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        
        Long total = queryFactory
            .select(paymentRecord.count())
            .from(paymentRecord)
            .where(builder)
            .fetchOne();
        
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
    
    @Override
    public List<PaymentAnalyticsProjection> analyzePaymentPatterns(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        return queryFactory
            .select(Projections.constructor(PaymentAnalyticsProjection.class,
                paymentRecord.paymentMethod,
                paymentRecord.count(),
                paymentRecord.totalAmount.sum(),
                paymentRecord.totalAmount.avg(),
                paymentRecord.totalAmount.min(),
                paymentRecord.totalAmount.max()
            ))
            .from(paymentRecord)
            .where(paymentRecord.paymentDate.between(startDate, endDate)
                .and(paymentRecord.paymentStatus.eq("COMPLETED")))
            .groupBy(paymentRecord.paymentMethod)
            .orderBy(paymentRecord.totalAmount.sum().desc())
            .fetch();
    }
    
    @Override
    public UserPaymentSummaryProjection getUserPaymentSummary(
            Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        
        return queryFactory
            .select(Projections.constructor(UserPaymentSummaryProjection.class,
                paymentRecord.userId,
                paymentRecord.count(),
                paymentRecord.totalAmount.sum(),
                paymentRecord.totalAmount.avg(),
                paymentRecord.paymentDate.max()
            ))
            .from(paymentRecord)
            .where(paymentRecord.userId.eq(userId)
                .and(paymentRecord.paymentStatus.eq("COMPLETED"))
                .and(paymentRecord.paymentDate.between(startDate, endDate)))
            .groupBy(paymentRecord.userId)
            .fetchOne();
    }
    
    @Override
    public Map<String, BigDecimal> getPaymentMethodStatistics(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        List<Object[]> results = queryFactory
            .select(paymentRecord.paymentMethod, paymentRecord.totalAmount.sum())
            .from(paymentRecord)
            .where(paymentRecord.paymentDate.between(startDate, endDate)
                .and(paymentRecord.paymentStatus.eq("COMPLETED")))
            .groupBy(paymentRecord.paymentMethod)
            .fetch();
        
        Map<String, BigDecimal> statistics = new HashMap<>();
        for (Object[] result : results) {
            statistics.put((String) result[0], (BigDecimal) result[1]);
        }
        return statistics;
    }
    
    @Override
    public List<PaymentAnalyticsProjection> analyzePaymentsByTimeOfDay(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        return queryFactory
            .select(Projections.constructor(PaymentAnalyticsProjection.class,
                paymentRecord.paymentDate.hour(),
                paymentRecord.count(),
                paymentRecord.totalAmount.sum(),
                paymentRecord.totalAmount.avg()
            ))
            .from(paymentRecord)
            .where(paymentRecord.paymentDate.between(startDate, endDate)
                .and(paymentRecord.paymentStatus.eq("COMPLETED")))
            .groupBy(paymentRecord.paymentDate.hour())
            .orderBy(paymentRecord.paymentDate.hour().asc())
            .fetch();
    }
    
    @Override
    public List<PaymentRecord> detectAnomalousTransactions(
            BigDecimal thresholdAmount, int maxAttempts, LocalDateTime timeWindow) {
        
        // 비정상 거래 탐지: 고액 결제, 짧은 시간 내 다수 시도 등
        return queryFactory
            .selectFrom(paymentRecord)
            .where(paymentRecord.totalAmount.gt(thresholdAmount)
                .or(paymentRecord.failureReason.isNotNull()
                    .and(paymentRecord.createdAt.after(timeWindow))))
            .orderBy(paymentRecord.createdAt.desc())
            .fetch();
    }
    
    @Override
    public Map<String, Double> calculatePaymentSuccessRates(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        // 전체 결제 시도
        List<Object[]> attempts = queryFactory
            .select(paymentRecord.paymentMethod, paymentRecord.count())
            .from(paymentRecord)
            .where(paymentRecord.createdAt.between(startDate, endDate))
            .groupBy(paymentRecord.paymentMethod)
            .fetch();
        
        // 성공한 결제
        List<Object[]> successes = queryFactory
            .select(paymentRecord.paymentMethod, paymentRecord.count())
            .from(paymentRecord)
            .where(paymentRecord.createdAt.between(startDate, endDate)
                .and(paymentRecord.paymentStatus.eq("COMPLETED")))
            .groupBy(paymentRecord.paymentMethod)
            .fetch();
        
        Map<String, Double> successRates = new HashMap<>();
        Map<String, Long> attemptMap = new HashMap<>();
        Map<String, Long> successMap = new HashMap<>();
        
        for (Object[] attempt : attempts) {
            attemptMap.put((String) attempt[0], (Long) attempt[1]);
        }
        
        for (Object[] success : successes) {
            successMap.put((String) success[0], (Long) success[1]);
        }
        
        for (String method : attemptMap.keySet()) {
            Long attemptCount = attemptMap.get(method);
            Long successCount = successMap.getOrDefault(method, 0L);
            double rate = attemptCount > 0 ? (double) successCount / attemptCount * 100 : 0.0;
            successRates.put(method, rate);
        }
        
        return successRates;
    }
    
    @Override
    public void batchUpdatePaymentStatus(List<String> transactionIds, String newStatus) {
        queryFactory
            .update(paymentRecord)
            .set(paymentRecord.paymentStatus, newStatus)
            .set(paymentRecord.updatedAt, LocalDateTime.now())
            .where(paymentRecord.transactionId.in(transactionIds))
            .execute();
        
        entityManager.flush();
        entityManager.clear();
    }
}
```

## 2. PaymentDetailRepository (결제 상세 정보 보안 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.payment;

import com.routepick.backend.domain.entity.payment.PaymentDetail;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 상세 정보 Repository
 * - PG사 연동 정보 관리
 * - 보안 감사 및 모니터링
 * - 의심 거래 탐지
 */
@Repository
public interface PaymentDetailRepository extends BaseRepository<PaymentDetail, Long> {
    
    // 기본 조회
    Optional<PaymentDetail> findByPaymentRecordId(Long paymentRecordId);
    
    Optional<PaymentDetail> findByGatewayTransactionId(String gatewayTransactionId);
    
    List<PaymentDetail> findByPaymentGatewayAndCreatedAtBetween(
        String paymentGateway, LocalDateTime startDate, LocalDateTime endDate);
    
    // 실패 결제 상세 분석
    @Query("SELECT pd FROM PaymentDetail pd " +
           "JOIN pd.paymentRecord pr " +
           "WHERE pr.paymentStatus IN ('FAILED', 'CANCELLED') " +
           "AND pd.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY pd.createdAt DESC")
    List<PaymentDetail> findFailedPaymentDetails(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 의심 거래 탐지
    @Query("SELECT pd FROM PaymentDetail pd " +
           "JOIN pd.paymentRecord pr " +
           "WHERE (pd.verificationResult = 'FAILED' " +
           "OR pd.fraudScore > :fraudThreshold " +
           "OR pr.totalAmount > :amountThreshold) " +
           "AND pd.createdAt >= :checkTime " +
           "ORDER BY pd.fraudScore DESC, pr.totalAmount DESC")
    List<PaymentDetail> findSuspiciousTransactions(
        @Param("fraudThreshold") Integer fraudThreshold,
        @Param("amountThreshold") BigDecimal amountThreshold,
        @Param("checkTime") LocalDateTime checkTime);
    
    // 게이트웨이 성능 분석
    @Query("SELECT pd.paymentGateway, " +
           "COUNT(pd) as totalCount, " +
           "SUM(CASE WHEN pr.paymentStatus = 'COMPLETED' THEN 1 ELSE 0 END) as successCount, " +
           "AVG(pd.responseTime) as avgResponseTime " +
           "FROM PaymentDetail pd " +
           "JOIN pd.paymentRecord pr " +
           "WHERE pd.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY pd.paymentGateway")
    List<Object[]> findGatewayPerformance(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 결제 보안 감사
    @Query("SELECT pd FROM PaymentDetail pd " +
           "WHERE pd.securityCheckStatus IN ('PENDING', 'SUSPICIOUS') " +
           "OR pd.fraudScore > :threshold " +
           "ORDER BY pd.createdAt DESC")
    List<PaymentDetail> auditPaymentSecurity(@Param("threshold") Integer threshold);
    
    // 3D Secure 인증 결제 조회
    @Query("SELECT pd FROM PaymentDetail pd " +
           "WHERE pd.is3DSecure = true " +
           "AND pd.createdAt BETWEEN :startDate AND :endDate")
    List<PaymentDetail> find3DSecurePayments(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 웹훅 데이터 존재 여부 확인
    @Query("SELECT CASE WHEN pd.webhookData IS NOT NULL THEN true ELSE false END " +
           "FROM PaymentDetail pd WHERE pd.paymentRecordId = :paymentRecordId")
    boolean hasWebhookData(@Param("paymentRecordId") Long paymentRecordId);
    
    // 검증 실패 건수
    @Query("SELECT COUNT(pd) FROM PaymentDetail pd " +
           "WHERE pd.verificationResult = 'FAILED' " +
           "AND pd.createdAt BETWEEN :startDate AND :endDate")
    long countVerificationFailures(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}
```

## 3. PaymentItemRepository (결제 항목 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.payment;

import com.routepick.backend.domain.entity.payment.PaymentItem;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 항목 Repository
 * - 결제 상품/서비스 항목 관리
 * - 판매 통계 및 수익성 분석
 * - 인기 항목 추적
 */
@Repository
public interface PaymentItemRepository extends BaseRepository<PaymentItem, Long> {
    
    // 기본 조회
    List<PaymentItem> findByPaymentRecordIdOrderByItemType(Long paymentRecordId);
    
    List<PaymentItem> findByItemTypeAndCreatedAtBetween(
        String itemType, LocalDateTime startDate, LocalDateTime endDate);
    
    // 결제별 총액 계산
    @Query("SELECT SUM(pi.itemPrice * pi.quantity) FROM PaymentItem pi " +
           "WHERE pi.paymentRecordId = :paymentRecordId")
    BigDecimal calculateTotalByPaymentRecord(@Param("paymentRecordId") Long paymentRecordId);
    
    // 인기 결제 항목
    @Query("SELECT pi.itemName, pi.itemType, COUNT(pi) as purchaseCount, " +
           "SUM(pi.quantity) as totalQuantity, SUM(pi.itemPrice * pi.quantity) as totalRevenue " +
           "FROM PaymentItem pi " +
           "JOIN PaymentRecord pr ON pi.paymentRecordId = pr.id " +
           "WHERE pr.paymentStatus = 'COMPLETED' " +
           "AND pr.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY pi.itemName, pi.itemType " +
           "ORDER BY purchaseCount DESC")
    List<Object[]> findPopularItems(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 항목별 판매 통계
    @Query("SELECT new com.routepick.backend.application.dto.projection.ItemSalesProjection(" +
           "pi.itemType, pi.itemName, COUNT(pi), SUM(pi.quantity), " +
           "SUM(pi.itemPrice * pi.quantity), AVG(pi.itemPrice)) " +
           "FROM PaymentItem pi " +
           "JOIN PaymentRecord pr ON pi.paymentRecordId = pr.id " +
           "WHERE pr.paymentStatus = 'COMPLETED' " +
           "AND pr.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY pi.itemType, pi.itemName " +
           "ORDER BY SUM(pi.itemPrice * pi.quantity) DESC")
    List<ItemSalesProjection> findItemSalesStatistics(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 항목 타입별 매출
    @Query("SELECT pi.itemType, SUM(pi.itemPrice * pi.quantity) as revenue " +
           "FROM PaymentItem pi " +
           "JOIN PaymentRecord pr ON pi.paymentRecordId = pr.id " +
           "WHERE pr.paymentStatus = 'COMPLETED' " +
           "AND pr.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY pi.itemType " +
           "ORDER BY revenue DESC")
    List<Object[]> findRevenueByItemType(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 항목별 수익성 분석
    @Query("SELECT pi.itemName, " +
           "SUM(pi.itemPrice * pi.quantity) as revenue, " +
           "SUM(pi.itemCost * pi.quantity) as cost, " +
           "SUM((pi.itemPrice - pi.itemCost) * pi.quantity) as profit, " +
           "AVG((pi.itemPrice - pi.itemCost) / pi.itemPrice * 100) as profitMargin " +
           "FROM PaymentItem pi " +
           "JOIN PaymentRecord pr ON pi.paymentRecordId = pr.id " +
           "WHERE pr.paymentStatus = 'COMPLETED' " +
           "AND pr.paymentDate BETWEEN :startDate AND :endDate " +
           "GROUP BY pi.itemName " +
           "HAVING SUM(pi.quantity) > 0 " +
           "ORDER BY profit DESC")
    List<Object[]> calculateItemProfitability(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 할인 적용 항목
    @Query("SELECT pi FROM PaymentItem pi " +
           "WHERE pi.discountAmount > 0 " +
           "AND pi.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY pi.discountAmount DESC")
    List<PaymentItem> findDiscountedItems(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 번들 상품 조회
    @Query("SELECT pi FROM PaymentItem pi " +
           "WHERE pi.isBundled = true " +
           "AND pi.createdAt BETWEEN :startDate AND :endDate")
    List<PaymentItem> findBundledItems(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 재고 부족 예상 항목
    @Query("SELECT pi.itemName, SUM(pi.quantity) as totalSold " +
           "FROM PaymentItem pi " +
           "JOIN PaymentRecord pr ON pi.paymentRecordId = pr.id " +
           "WHERE pr.paymentStatus = 'COMPLETED' " +
           "AND pr.paymentDate >= :checkDate " +
           "GROUP BY pi.itemName " +
           "ORDER BY totalSold DESC")
    List<Object[]> findHighDemandItems(@Param("checkDate") LocalDateTime checkDate);
}
```

## 4. PaymentRefundRepository (환불 처리 관리)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.payment;

import com.routepick.backend.domain.entity.payment.PaymentRefund;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 환불 처리 Repository
 * - 환불 트랜잭션 관리
 * - 환불 동향 및 패턴 분석
 * - 고환불률 항목 추적
 */
@Repository
public interface PaymentRefundRepository extends BaseRepository<PaymentRefund, Long> {
    
    // 기본 조회
    List<PaymentRefund> findByPaymentRecordIdOrderByRefundDateDesc(Long paymentRecordId);
    
    List<PaymentRefund> findByRefundStatusAndCreatedAtBetween(
        String refundStatus, LocalDateTime startDate, LocalDateTime endDate);
    
    // 총 환불액 계산
    @Query("SELECT SUM(pr.refundAmount) FROM PaymentRefund pr " +
           "WHERE pr.paymentRecordId = :paymentRecordId " +
           "AND pr.refundStatus = 'COMPLETED'")
    BigDecimal calculateTotalRefundAmount(@Param("paymentRecordId") Long paymentRecordId);
    
    // 처리 대기 환불
    @Query("SELECT pr FROM PaymentRefund pr " +
           "WHERE pr.refundStatus = 'PENDING' " +
           "ORDER BY pr.requestedAt ASC")
    List<PaymentRefund> findPendingRefunds();
    
    // 기간별 환불 내역
    @Query("SELECT pr FROM PaymentRefund pr " +
           "WHERE pr.refundDate BETWEEN :startDate AND :endDate " +
           "AND pr.refundStatus = 'COMPLETED' " +
           "ORDER BY pr.refundDate DESC")
    List<PaymentRefund> findRefundsByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 환불 동향 분석
    @Query("SELECT DATE(pr.refundDate) as refundDay, " +
           "COUNT(pr) as refundCount, " +
           "SUM(pr.refundAmount) as totalRefunded, " +
           "AVG(pr.refundAmount) as avgRefund " +
           "FROM PaymentRefund pr " +
           "WHERE pr.refundDate BETWEEN :startDate AND :endDate " +
           "AND pr.refundStatus = 'COMPLETED' " +
           "GROUP BY DATE(pr.refundDate) " +
           "ORDER BY refundDay DESC")
    List<Object[]> findRefundTrends(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 고환불률 항목 (결제 항목과 조인)
    @Query("SELECT pi.itemName, pi.itemType, " +
           "COUNT(DISTINCT pr.id) as refundCount, " +
           "SUM(pr.refundAmount) as totalRefunded " +
           "FROM PaymentRefund pr " +
           "JOIN PaymentRecord p ON pr.paymentRecordId = p.id " +
           "JOIN PaymentItem pi ON pi.paymentRecordId = p.id " +
           "WHERE pr.refundStatus = 'COMPLETED' " +
           "AND pr.refundDate BETWEEN :startDate AND :endDate " +
           "GROUP BY pi.itemName, pi.itemType " +
           "HAVING COUNT(DISTINCT pr.id) >= :minRefundCount " +
           "ORDER BY refundCount DESC")
    List<Object[]> findHighRefundRateItems(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("minRefundCount") Long minRefundCount);
    
    // 환불 통계
    @Query("SELECT new com.routepick.backend.application.dto.projection.RefundStatisticsProjection(" +
           "pr.refundReason, COUNT(pr), SUM(pr.refundAmount), AVG(pr.refundAmount), " +
           "MIN(pr.refundAmount), MAX(pr.refundAmount)) " +
           "FROM PaymentRefund pr " +
           "WHERE pr.refundStatus = 'COMPLETED' " +
           "AND pr.refundDate BETWEEN :startDate AND :endDate " +
           "GROUP BY pr.refundReason " +
           "ORDER BY COUNT(pr) DESC")
    List<RefundStatisticsProjection> calculateRefundStatistics(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 부분 환불 조회
    @Query("SELECT pr FROM PaymentRefund pr " +
           "WHERE pr.isPartialRefund = true " +
           "AND pr.refundStatus = 'COMPLETED' " +
           "AND pr.refundDate BETWEEN :startDate AND :endDate")
    List<PaymentRefund> findPartialRefunds(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 환불 처리 시간 분석
    @Query("SELECT AVG(TIMESTAMPDIFF(HOUR, pr.requestedAt, pr.processedAt)) as avgProcessingHours, " +
           "MIN(TIMESTAMPDIFF(HOUR, pr.requestedAt, pr.processedAt)) as minProcessingHours, " +
           "MAX(TIMESTAMPDIFF(HOUR, pr.requestedAt, pr.processedAt)) as maxProcessingHours " +
           "FROM PaymentRefund pr " +
           "WHERE pr.refundStatus = 'COMPLETED' " +
           "AND pr.processedAt IS NOT NULL " +
           "AND pr.refundDate BETWEEN :startDate AND :endDate")
    Object[] analyzeRefundProcessingTime(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // 환불 상태 업데이트
    @Transactional
    @Modifying
    @Query("UPDATE PaymentRefund pr SET pr.refundStatus = :newStatus, " +
           "pr.processedAt = CURRENT_TIMESTAMP, pr.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE pr.id = :refundId AND pr.refundStatus = :currentStatus")
    int updateRefundStatus(
        @Param("refundId") Long refundId,
        @Param("currentStatus") String currentStatus,
        @Param("newStatus") String newStatus);
    
    // 자동 환불 대상
    @Query("SELECT pr FROM PaymentRefund pr " +
           "WHERE pr.refundStatus = 'PENDING' " +
           "AND pr.isAutoRefundEligible = true " +
           "AND pr.requestedAt <= :cutoffTime " +
           "ORDER BY pr.requestedAt ASC")
    List<PaymentRefund> findAutoRefundEligible(@Param("cutoffTime") LocalDateTime cutoffTime);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.payment.custom;

import com.routepick.backend.application.dto.payment.RefundSearchCriteria;
import com.routepick.backend.application.dto.projection.RefundAnalyticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 환불 처리 커스텀 Repository
 */
public interface PaymentRefundRepositoryCustom {
    
    // 고급 검색
    Page<PaymentRefund> searchRefunds(RefundSearchCriteria criteria, Pageable pageable);
    
    // 환불 분석
    List<RefundAnalyticsProjection> analyzeRefundPatterns(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 환불 사유별 통계
    Map<String, RefundAnalyticsProjection> getRefundReasonStatistics(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 사용자별 환불 이력
    List<PaymentRefund> getUserRefundHistory(Long userId, int limit);
    
    // 환불률 계산
    Double calculateRefundRate(LocalDateTime startDate, LocalDateTime endDate);
    
    // 배치 환불 처리
    void batchProcessRefunds(List<Long> refundIds, String status);
    
    // 환불 가능 금액 검증
    boolean validateRefundAmount(Long paymentRecordId, BigDecimal requestedAmount);
}
```

## Projection 인터페이스들

### PaymentTrendProjection
```java
package com.routepick.backend.application.dto.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 결제 동향 Projection
 */
public class PaymentTrendProjection {
    private LocalDate paymentDate;
    private Long paymentCount;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    
    public PaymentTrendProjection(LocalDate paymentDate, Long paymentCount, 
                                 BigDecimal totalAmount, BigDecimal averageAmount) {
        this.paymentDate = paymentDate;
        this.paymentCount = paymentCount;
        this.totalAmount = totalAmount;
        this.averageAmount = averageAmount;
    }
    
    // Getters
    public LocalDate getPaymentDate() { return paymentDate; }
    public Long getPaymentCount() { return paymentCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getAverageAmount() { return averageAmount; }
}
```

### RefundStatisticsProjection
```java
package com.routepick.backend.application.dto.projection;

import java.math.BigDecimal;

/**
 * 환불 통계 Projection
 */
public class RefundStatisticsProjection {
    private String refundReason;
    private Long refundCount;
    private BigDecimal totalRefunded;
    private BigDecimal averageRefund;
    private BigDecimal minRefund;
    private BigDecimal maxRefund;
    
    public RefundStatisticsProjection(String refundReason, Long refundCount,
                                     BigDecimal totalRefunded, BigDecimal averageRefund,
                                     BigDecimal minRefund, BigDecimal maxRefund) {
        this.refundReason = refundReason;
        this.refundCount = refundCount;
        this.totalRefunded = totalRefunded;
        this.averageRefund = averageRefund;
        this.minRefund = minRefund;
        this.maxRefund = maxRefund;
    }
    
    // Getters
    public String getRefundReason() { return refundReason; }
    public Long getRefundCount() { return refundCount; }
    public BigDecimal getTotalRefunded() { return totalRefunded; }
    public BigDecimal getAverageRefund() { return averageRefund; }
    public BigDecimal getMinRefund() { return minRefund; }
    public BigDecimal getMaxRefund() { return maxRefund; }
}
```

## 📈 성능 및 보안 최적화

### 1. 인덱스 최적화
```sql
-- 결제 기록
CREATE INDEX idx_payment_user_status_date ON payment_records(user_id, payment_status, payment_date DESC);
CREATE UNIQUE INDEX idx_payment_transaction ON payment_records(transaction_id);
CREATE INDEX idx_payment_amount_date ON payment_records(total_amount DESC, payment_date DESC);

-- 결제 상세
CREATE UNIQUE INDEX idx_detail_payment ON payment_details(payment_record_id);
CREATE INDEX idx_detail_gateway_tx ON payment_details(gateway_transaction_id);
CREATE INDEX idx_detail_fraud_score ON payment_details(fraud_score DESC);

-- 결제 항목
CREATE INDEX idx_item_payment ON payment_items(payment_record_id);
CREATE INDEX idx_item_type_date ON payment_items(item_type, created_at DESC);

-- 환불
CREATE INDEX idx_refund_payment ON payment_refunds(payment_record_id, refund_date DESC);
CREATE INDEX idx_refund_status_date ON payment_refunds(refund_status, requested_at);
```

### 2. 보안 강화
- **PCI DSS 준수**: 카드 정보 마스킹, 민감정보 암호화
- **이상 거래 탐지**: 실시간 모니터링, 패턴 분석
- **감사 로그**: 모든 결제 활동 추적
- **접근 제어**: 결제 데이터 접근 권한 관리

### 3. 캐싱 전략
- **Redis 캐싱**: 결제 상태, 통계 데이터
- **결제 방법별 캐싱**: 자주 사용하는 결제 수단
- **환불 정책 캐싱**: 환불 규칙 및 조건

## 🎯 다음 단계 예고
- **5-5단계**: Message Repository (메시지 시스템)
- **5-6단계**: Notification & System Repository (알림, 시스템)
- **Repository 레이어 완료** 후 **Service 레이어** 진행

---
*Step 5-4d 완료: 결제 시스템 Repository 4개 생성 완료*  
*PCI DSS 준수 및 보안 강화 적용*  
*다음: 5-5단계 Message Repository 대기 중*