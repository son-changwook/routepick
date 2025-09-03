# Step 6-5b2: Refund Automation & Statistics

**파일들**: 환불 자동화, 통계 분석, 스케줄링 시스템 구현

이 파일은 `step6-5b1_refund_processing_core.md`와 연계된 환불 자동화 및 통계 시스템입니다.

## 📊 환불 통계 및 자동화 서비스

```java
package com.routepick.service.payment;

import com.routepick.common.enums.RefundStatus;
import com.routepick.common.enums.RefundReason;
import com.routepick.common.enums.PaymentStatus;
import com.routepick.domain.payment.entity.PaymentRefund;
import com.routepick.domain.payment.repository.PaymentRefundRepository;
import com.routepick.domain.payment.repository.PaymentRecordRepository;
import com.routepick.dto.payment.RefundRequest;
import com.routepick.dto.statistics.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 환불 자동화 및 통계 서비스
 * 
 * 주요 기능:
 * 1. 자동 환불 규칙 처리
 * 2. 환불 통계 분석
 * 3. 환불 패턴 모니터링
 * 4. 스케줄링 기반 자동 처리
 * 5. 환불 성능 분석
 * 
 * @author RoutePickr Team
 * @since 2025.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundAutomationService {
    
    private final PaymentRefundRepository refundRepository;
    private final PaymentRecordRepository paymentRepository;
    private final PaymentRefundService refundService;
    private final NotificationService notificationService;
    
    // 캐시 이름
    private static final String CACHE_REFUND_STATS = "refundStats";
    private static final String CACHE_REFUND_ANALYSIS = "refundAnalysis";
    
    // 설정값
    private static final int AUTO_REFUND_THRESHOLD_HOURS = 24; // 자동 승인 임계시간
    private static final BigDecimal AUTO_APPROVAL_MAX_AMOUNT = new BigDecimal("50000"); // 자동 승인 최대 금액
    
    // ===================== 자동 환불 처리 =====================
    
    /**
     * 자동 환불 규칙 적용 (스케줄링)
     */
    @Scheduled(cron = "0 0 */6 * * ?") // 6시간마다 실행
    @Transactional
    public void processAutoRefunds() {
        log.info("Processing auto refunds");
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(AUTO_REFUND_THRESHOLD_HOURS);
        
        // 자동 환불 대상 조회
        List<PaymentRefund> pendingRefunds = refundRepository
            .findPendingRefundsForAutoProcessing(cutoffTime);
        
        int approvedCount = 0;
        int rejectedCount = 0;
        
        for (PaymentRefund refund : pendingRefunds) {
            try {
                AutoRefundDecision decision = evaluateAutoRefund(refund);
                
                switch (decision.getAction()) {
                    case APPROVE:
                        refundService.approveRefund(
                            refund.getRefundId(), 
                            null, 
                            "자동 승인: " + decision.getReason()
                        );
                        approvedCount++;
                        break;
                        
                    case REJECT:
                        refundService.rejectRefund(
                            refund.getRefundId(),
                            null,
                            "자동 거부: " + decision.getReason()
                        );
                        rejectedCount++;
                        break;
                        
                    case ESCALATE:
                        // 관리자에게 에스컬레이션
                        notificationService.sendRefundEscalationNotification(refund, decision.getReason());
                        break;
                }
                
            } catch (Exception e) {
                log.error("Failed to process auto refund: refundId={}, error={}", 
                         refund.getRefundId(), e.getMessage());
            }
        }
        
        log.info("Auto refund processing completed: approved={}, rejected={}, total={}", 
                approvedCount, rejectedCount, pendingRefunds.size());
    }
    
    /**
     * 자동 환불 결정 평가
     */
    private AutoRefundDecision evaluateAutoRefund(PaymentRefund refund) {
        List<String> approvalReasons = new ArrayList<>();
        List<String> rejectionReasons = new ArrayList<>();
        
        // 1. 금액 기준 평가
        if (refund.getRefundAmount().compareTo(new BigDecimal("5000")) <= 0) {
            approvalReasons.add("소액 환불 (5천원 이하)");
        } else if (refund.getRefundAmount().compareTo(AUTO_APPROVAL_MAX_AMOUNT) > 0) {
            return AutoRefundDecision.escalate("고액 환불로 수동 검토 필요");
        }
        
        // 2. 환불 사유 기준 평가
        switch (refund.getRefundReason()) {
            case SYSTEM_ERROR:
            case DUPLICATE_PAYMENT:
                approvalReasons.add("시스템 오류/중복 결제");
                break;
                
            case DEFECTIVE_PRODUCT:
            case SERVICE_ISSUE:
                approvalReasons.add("상품 불량/서비스 문제");
                break;
                
            case CHANGE_OF_MIND:
                // 변심의 경우 추가 검토
                if (isEarlyRefundRequest(refund)) {
                    approvalReasons.add("24시간 이내 변심");
                } else {
                    rejectionReasons.add("변심 (기간 초과)");
                }
                break;
                
            case UNAUTHORIZED_USE:
                return AutoRefundDecision.escalate("무단 사용 신고로 수동 검토 필요");
                
            default:
                return AutoRefundDecision.escalate("알 수 없는 환불 사유");
        }
        
        // 3. 사용자 환불 이력 평가
        UserRefundHistory history = getUserRefundHistory(refund.getPayment().getUser().getUserId());
        
        if (history.getMonthlyRefundCount() > 3) {
            return AutoRefundDecision.escalate("월 환불 횟수 초과 (3회 이상)");
        }
        
        if (history.getMonthlyRefundAmount().compareTo(new BigDecimal("100000")) > 0) {
            return AutoRefundDecision.escalate("월 환불 금액 초과 (10만원 이상)");
        }
        
        // 4. 결제 후 경과 시간 평가
        long hoursFromPayment = ChronoUnit.HOURS.between(
            refund.getPayment().getApprovedAt(), 
            refund.getRequestedAt()
        );
        
        if (hoursFromPayment <= 1) {
            approvalReasons.add("즉시 환불 요청 (1시간 이내)");
        } else if (hoursFromPayment > 168) { // 7일
            if (!isExtendedRefundAllowed(refund.getRefundReason())) {
                rejectionReasons.add("환불 기간 초과 (7일)");
            }
        }
        
        // 5. 최종 결정
        if (!rejectionReasons.isEmpty()) {
            return AutoRefundDecision.reject(String.join(", ", rejectionReasons));
        } else if (!approvalReasons.isEmpty()) {
            return AutoRefundDecision.approve(String.join(", ", approvalReasons));
        } else {
            return AutoRefundDecision.escalate("자동 처리 조건 불충족");
        }
    }
    
    /**
     * 조기 환불 요청 여부 확인
     */
    private boolean isEarlyRefundRequest(PaymentRefund refund) {
        if (refund.getPayment().getApprovedAt() == null) {
            return false;
        }
        
        return refund.getRequestedAt().isBefore(
            refund.getPayment().getApprovedAt().plusHours(24)
        );
    }
    
    /**
     * 연장 환불 허용 여부
     */
    private boolean isExtendedRefundAllowed(RefundReason reason) {
        return reason == RefundReason.DEFECTIVE_PRODUCT ||
               reason == RefundReason.SYSTEM_ERROR ||
               reason == RefundReason.SERVICE_ISSUE ||
               reason == RefundReason.UNAUTHORIZED_USE;
    }
    
    // ===================== 환불 통계 분석 =====================
    
    /**
     * 환불 통계 조회
     */
    @Cacheable(value = CACHE_REFUND_STATS,
              key = "#startDate + '_' + #endDate + '_' + #refundReason")
    public RefundStatistics getRefundStatistics(LocalDateTime startDate, LocalDateTime endDate,
                                              RefundReason refundReason) {
        log.debug("Getting refund statistics: startDate={}, endDate={}, reason={}", 
                 startDate, endDate, refundReason);
        
        // 총 환불 건수 및 금액
        Long totalCount = refundRepository.countByDateRangeAndStatus(
            startDate, endDate, RefundStatus.COMPLETED
        );
        
        BigDecimal totalAmount = refundRepository.sumAmountByDateRangeAndStatus(
            startDate, endDate, RefundStatus.COMPLETED
        );
        
        // 환불 사유별 통계
        List<RefundReasonStatistics> reasonStats = refundRepository
            .getRefundReasonStatistics(startDate, endDate);
        
        // 환불 처리 시간 통계
        RefundProcessingTimeStats processingTimeStats = getProcessingTimeStatistics(startDate, endDate);
        
        // 환불율 계산
        Long totalPayments = paymentRepository.countByDateRangeAndStatus(
            startDate, endDate, PaymentStatus.COMPLETED
        );
        
        BigDecimal refundRate = totalPayments > 0 ?
            BigDecimal.valueOf(totalCount).divide(BigDecimal.valueOf(totalPayments), 4, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;
        
        // 자동 승인율
        Long autoApprovedCount = refundRepository.countAutoApprovedRefunds(startDate, endDate);
        BigDecimal autoApprovalRate = totalCount > 0 ?
            BigDecimal.valueOf(autoApprovedCount).divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;
        
        return RefundStatistics.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalCount(totalCount)
            .totalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
            .averageAmount(totalCount > 0 ? 
                totalAmount.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO)
            .refundRate(refundRate)
            .autoApprovalRate(autoApprovalRate)
            .processingTimeStats(processingTimeStats)
            .reasonStatistics(reasonStats)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 환불 성능 분석
     */
    @Cacheable(value = CACHE_REFUND_ANALYSIS, key = "#startDate + '_' + #endDate")
    public RefundPerformanceAnalysis getRefundPerformanceAnalysis(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating refund performance analysis: startDate={}, endDate={}", startDate, endDate);
        
        // 처리 시간 분포
        List<RefundProcessingTimeDistribution> processingTimeDistribution = 
            refundRepository.getProcessingTimeDistribution(startDate, endDate);
        
        // 환불 사유별 승인율
        List<RefundApprovalRateByReason> approvalRateByReason = 
            refundRepository.getApprovalRateByReason(startDate, endDate);
        
        // 월별/일별 환불 추이
        List<RefundTrendData> monthlyTrend = getRefundTrend(startDate, endDate, ChronoUnit.MONTHS);
        List<RefundTrendData> dailyTrend = getRefundTrend(startDate, endDate, ChronoUnit.DAYS);
        
        // 사용자별 환불 패턴
        List<UserRefundPattern> userPatterns = getUserRefundPatterns(startDate, endDate);
        
        // 환불 금액 분포
        RefundAmountDistribution amountDistribution = getRefundAmountDistribution(startDate, endDate);
        
        return RefundPerformanceAnalysis.builder()
            .startDate(startDate)
            .endDate(endDate)
            .processingTimeDistribution(processingTimeDistribution)
            .approvalRateByReason(approvalRateByReason)
            .monthlyTrend(monthlyTrend)
            .dailyTrend(dailyTrend)
            .userPatterns(userPatterns)
            .amountDistribution(amountDistribution)
            .analyzedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 환불 대시보드 데이터
     */
    public RefundDashboardData getRefundDashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterday = today.minusDays(1);
        LocalDateTime thisMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime lastMonth = thisMonth.minusMonths(1);
        
        // 오늘 환불 현황
        RefundDailyStats todayStats = getDailyStats(today, now);
        RefundDailyStats yesterdayStats = getDailyStats(yesterday, today);
        
        // 이번 달 환불 현황
        RefundMonthlyStats thisMonthStats = getMonthlyStats(thisMonth, now);
        RefundMonthlyStats lastMonthStats = getMonthlyStats(lastMonth, thisMonth);
        
        // 대기 중인 환불
        List<PaymentRefund> pendingRefunds = refundRepository.findPendingRefunds();
        
        // 최근 환불 내역
        List<PaymentRefund> recentRefunds = refundRepository.findRecentRefunds(10);
        
        return RefundDashboardData.builder()
            .todayStats(todayStats)
            .yesterdayStats(yesterdayStats)
            .thisMonthStats(thisMonthStats)
            .lastMonthStats(lastMonthStats)
            .pendingRefundsCount(pendingRefunds.size())
            .pendingRefundsAmount(calculateTotalAmount(pendingRefunds))
            .recentRefunds(recentRefunds.stream()
                .map(this::convertToRefundSummary)
                .collect(Collectors.toList()))
            .generatedAt(now)
            .build();
    }
    
    // ===================== Helper 메서드 =====================
    
    private UserRefundHistory getUserRefundHistory(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        
        List<PaymentRefund> monthlyRefunds = refundRepository.findUserRefundsByMonth(userId, monthStart);
        
        Long monthlyCount = (long) monthlyRefunds.size();
        BigDecimal monthlyAmount = monthlyRefunds.stream()
            .map(PaymentRefund::getRefundAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return UserRefundHistory.builder()
            .userId(userId)
            .monthlyRefundCount(monthlyCount)
            .monthlyRefundAmount(monthlyAmount)
            .build();
    }
    
    private RefundProcessingTimeStats getProcessingTimeStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        List<PaymentRefund> completedRefunds = refundRepository
            .findCompletedRefundsByDateRange(startDate, endDate);
        
        if (completedRefunds.isEmpty()) {
            return RefundProcessingTimeStats.builder()
                .averageHours(0.0)
                .medianHours(0.0)
                .minHours(0.0)
                .maxHours(0.0)
                .build();
        }
        
        List<Double> processingTimes = completedRefunds.stream()
            .map(refund -> {
                if (refund.getCompletedAt() != null && refund.getRequestedAt() != null) {
                    return (double) ChronoUnit.HOURS.between(refund.getRequestedAt(), refund.getCompletedAt());
                }
                return 0.0;
            })
            .sorted()
            .collect(Collectors.toList());
        
        double average = processingTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double median = processingTimes.size() % 2 == 0 ?
            (processingTimes.get(processingTimes.size() / 2 - 1) + processingTimes.get(processingTimes.size() / 2)) / 2 :
            processingTimes.get(processingTimes.size() / 2);
        
        return RefundProcessingTimeStats.builder()
            .averageHours(average)
            .medianHours(median)
            .minHours(processingTimes.get(0))
            .maxHours(processingTimes.get(processingTimes.size() - 1))
            .build();
    }
    
    private List<RefundTrendData> getRefundTrend(LocalDateTime startDate, LocalDateTime endDate, ChronoUnit unit) {
        List<RefundTrendData> trend = new ArrayList<>();
        
        LocalDateTime current = startDate.truncatedTo(unit);
        while (current.isBefore(endDate)) {
            LocalDateTime next = current.plus(1, unit);
            
            Long count = refundRepository.countByDateRangeAndStatus(current, next, RefundStatus.COMPLETED);
            BigDecimal amount = refundRepository.sumAmountByDateRangeAndStatus(current, next, RefundStatus.COMPLETED);
            
            trend.add(RefundTrendData.builder()
                .period(current)
                .refundCount(count)
                .refundAmount(amount != null ? amount : BigDecimal.ZERO)
                .build());
            
            current = next;
        }
        
        return trend;
    }
    
    private List<UserRefundPattern> getUserRefundPatterns(LocalDateTime startDate, LocalDateTime endDate) {
        return refundRepository.getUserRefundPatterns(startDate, endDate).stream()
            .limit(20) // 상위 20명만
            .collect(Collectors.toList());
    }
    
    private RefundAmountDistribution getRefundAmountDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        List<PaymentRefund> refunds = refundRepository.findCompletedRefundsByDateRange(startDate, endDate);
        
        long under10k = refunds.stream().mapToLong(r -> 
            r.getRefundAmount().compareTo(new BigDecimal("10000")) < 0 ? 1 : 0).sum();
        long range10kTo50k = refunds.stream().mapToLong(r -> 
            r.getRefundAmount().compareTo(new BigDecimal("10000")) >= 0 && 
            r.getRefundAmount().compareTo(new BigDecimal("50000")) < 0 ? 1 : 0).sum();
        long range50kTo100k = refunds.stream().mapToLong(r -> 
            r.getRefundAmount().compareTo(new BigDecimal("50000")) >= 0 && 
            r.getRefundAmount().compareTo(new BigDecimal("100000")) < 0 ? 1 : 0).sum();
        long over100k = refunds.stream().mapToLong(r -> 
            r.getRefundAmount().compareTo(new BigDecimal("100000")) >= 0 ? 1 : 0).sum();
        
        return RefundAmountDistribution.builder()
            .under10k(under10k)
            .range10kTo50k(range10kTo50k)
            .range50kTo100k(range50kTo100k)
            .over100k(over100k)
            .build();
    }
    
    private RefundDailyStats getDailyStats(LocalDateTime startDate, LocalDateTime endDate) {
        Long totalCount = refundRepository.countByDateRange(startDate, endDate, null);
        Long approvedCount = refundRepository.countByDateRangeAndStatus(startDate, endDate, RefundStatus.APPROVED);
        Long rejectedCount = refundRepository.countByDateRangeAndStatus(startDate, endDate, RefundStatus.REJECTED);
        BigDecimal totalAmount = refundRepository.sumAmountByDateRange(startDate, endDate);
        
        return RefundDailyStats.builder()
            .date(startDate.toLocalDate())
            .totalCount(totalCount)
            .approvedCount(approvedCount)
            .rejectedCount(rejectedCount)
            .totalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
            .build();
    }
    
    private RefundMonthlyStats getMonthlyStats(LocalDateTime startDate, LocalDateTime endDate) {
        Long totalCount = refundRepository.countByDateRange(startDate, endDate, null);
        BigDecimal totalAmount = refundRepository.sumAmountByDateRange(startDate, endDate);
        Long autoApprovedCount = refundRepository.countAutoApprovedRefunds(startDate, endDate);
        
        return RefundMonthlyStats.builder()
            .month(startDate.toLocalDate().withDayOfMonth(1))
            .totalCount(totalCount)
            .totalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
            .autoApprovedCount(autoApprovedCount)
            .build();
    }
    
    private BigDecimal calculateTotalAmount(List<PaymentRefund> refunds) {
        return refunds.stream()
            .map(PaymentRefund::getRefundAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private RefundSummary convertToRefundSummary(PaymentRefund refund) {
        return RefundSummary.builder()
            .refundId(refund.getRefundId())
            .paymentId(refund.getPayment().getPaymentId())
            .userId(refund.getPayment().getUser().getUserId())
            .refundAmount(refund.getRefundAmount())
            .refundStatus(refund.getRefundStatus())
            .refundReason(refund.getRefundReason())
            .requestedAt(refund.getRequestedAt())
            .build();
    }
}

/**
 * 자동 환불 결정 클래스
 */
@Getter
@Builder
public class AutoRefundDecision {
    private final AutoRefundAction action;
    private final String reason;
    
    public static AutoRefundDecision approve(String reason) {
        return AutoRefundDecision.builder()
            .action(AutoRefundAction.APPROVE)
            .reason(reason)
            .build();
    }
    
    public static AutoRefundDecision reject(String reason) {
        return AutoRefundDecision.builder()
            .action(AutoRefundAction.REJECT)
            .reason(reason)
            .build();
    }
    
    public static AutoRefundDecision escalate(String reason) {
        return AutoRefundDecision.builder()
            .action(AutoRefundAction.ESCALATE)
            .reason(reason)
            .build();
    }
}

enum AutoRefundAction {
    APPROVE, REJECT, ESCALATE
}
```

## 📋 환불 통계 DTO 클래스

```java
/**
 * 환불 통계 응답
 */
@Data
@Builder
public class RefundStatistics {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long totalCount;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal refundRate;
    private BigDecimal autoApprovalRate;
    private RefundProcessingTimeStats processingTimeStats;
    private List<RefundReasonStatistics> reasonStatistics;
    private LocalDateTime generatedAt;
}

/**
 * 환불 사유별 통계
 */
@Data
@Builder
public class RefundReasonStatistics {
    private RefundReason reason;
    private Long count;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal approvalRate;
}

/**
 * 환불 성능 분석
 */
@Data
@Builder
public class RefundPerformanceAnalysis {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private List<RefundProcessingTimeDistribution> processingTimeDistribution;
    private List<RefundApprovalRateByReason> approvalRateByReason;
    private List<RefundTrendData> monthlyTrend;
    private List<RefundTrendData> dailyTrend;
    private List<UserRefundPattern> userPatterns;
    private RefundAmountDistribution amountDistribution;
    private LocalDateTime analyzedAt;
}

/**
 * 환불 대시보드 데이터
 */
@Data
@Builder
public class RefundDashboardData {
    private RefundDailyStats todayStats;
    private RefundDailyStats yesterdayStats;
    private RefundMonthlyStats thisMonthStats;
    private RefundMonthlyStats lastMonthStats;
    private Integer pendingRefundsCount;
    private BigDecimal pendingRefundsAmount;
    private List<RefundSummary> recentRefunds;
    private LocalDateTime generatedAt;
}

/**
 * 사용자 환불 이력
 */
@Data
@Builder
public class UserRefundHistory {
    private Long userId;
    private Long monthlyRefundCount;
    private BigDecimal monthlyRefundAmount;
}

/**
 * 환불 처리 시간 통계
 */
@Data
@Builder
public class RefundProcessingTimeStats {
    private Double averageHours;
    private Double medianHours;
    private Double minHours;
    private Double maxHours;
}
```

## 🔄 환불 자동화 설정

```yaml
# 환불 자동화 설정
app:
  payment:
    refund:
      automation:
        enabled: true
        schedule: "0 0 */6 * * ?"  # 6시간마다
        max-auto-amount: 50000  # 자동 승인 최대 금액
        threshold-hours: 24  # 자동 처리 임계시간
        
        rules:
          small-amount: 5000  # 소액 자동 승인 기준
          early-refund-hours: 24  # 조기 환불 기준
          monthly-limit:
            count: 3  # 월 환불 횟수 제한
            amount: 100000  # 월 환불 금액 제한
            
      statistics:
        cache-ttl: 3600s  # 1시간
        batch-size: 1000  # 배치 처리 크기
        
      monitoring:
        alert-threshold:
          daily-refund-rate: 0.1  # 일일 환불율 10% 초과시 알림
          pending-count: 50  # 대기 환불 50건 초과시 알림
```

## 📊 환불 모니터링 API

```java
/**
 * 환불 통계 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/refunds/analytics")
@RequiredArgsConstructor
public class RefundAnalyticsController {
    
    private final RefundAutomationService automationService;
    
    @GetMapping("/statistics")
    public ResponseEntity<RefundStatistics> getStatistics(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            @RequestParam(required = false) RefundReason reason) {
        return ResponseEntity.ok(automationService.getRefundStatistics(startDate, endDate, reason));
    }
    
    @GetMapping("/performance")
    public ResponseEntity<RefundPerformanceAnalysis> getPerformanceAnalysis(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {
        return ResponseEntity.ok(automationService.getRefundPerformanceAnalysis(startDate, endDate));
    }
    
    @GetMapping("/dashboard")
    public ResponseEntity<RefundDashboardData> getDashboard() {
        return ResponseEntity.ok(automationService.getRefundDashboard());
    }
    
    @PostMapping("/auto-process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> triggerAutoProcess() {
        automationService.processAutoRefunds();
        return ResponseEntity.ok().build();
    }
}
```

## 📊 연동 참고사항

### step6-5b1_refund_processing_core.md 연동점
1. **자동 처리**: 핵심 서비스의 승인/거부 메서드 활용
2. **통계 수집**: 환불 처리 결과 통계화
3. **스케줄링**: 자동 환불 규칙 적용
4. **모니터링**: 환불 성능 및 패턴 분석

### 성능 최적화
1. **배치 처리**: 대량 환불 데이터 배치 처리
2. **캐싱**: 통계 데이터 캐싱으로 응답 속도 향상
3. **인덱싱**: 통계 쿼리 최적화
4. **비동기 처리**: 대용량 분석 작업 비동기 처리

### 확장 계획
1. **머신러닝**: 환불 패턴 학습 기반 자동 승인
2. **실시간 모니터링**: 실시간 환불 상태 추적
3. **예측 분석**: 환불 예측 모델 구축
4. **개인화**: 사용자별 환불 규칙 개인화

---
**연관 파일**: `step6-5b1_refund_processing_core.md`
**구현 우선순위**: MEDIUM (자동화 시스템)
**예상 개발 기간**: 2-3일