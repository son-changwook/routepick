# Step 6-5b: PaymentRefundService 구현

> 환불 처리 서비스 - 환불 승인/거부, 부분/전체 환불, 자동 환불 규칙
> 생성일: 2025-08-22
> 단계: 6-5b (Service 레이어 - 환불 시스템)
> 참고: step4-4b1, step5-4d

---

## 🎯 설계 목표

- **환불 처리**: PaymentRefund 엔티티 관리
- **승인 프로세스**: 환불 승인/거부 워크플로우
- **부분/전체 환불**: 유연한 환불 금액 처리
- **환불 사유**: 상세한 사유 분류 및 관리
- **자동 환불**: 규칙 기반 자동 환불 처리

---

## 💰 PaymentRefundService 구현

### PaymentRefundService.java
```java
package com.routepick.service.payment;

import com.routepick.common.enums.RefundStatus;
import com.routepick.common.enums.RefundType;
import com.routepick.common.enums.RefundReason;
import com.routepick.common.enums.PaymentStatus;
import com.routepick.domain.payment.entity.PaymentRecord;
import com.routepick.domain.payment.entity.PaymentRefund;
import com.routepick.domain.payment.repository.PaymentRecordRepository;
import com.routepick.domain.payment.repository.PaymentRefundRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.payment.PaymentException;
import com.routepick.exception.user.UserException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 환불 처리 서비스
 * - 환불 요청 및 처리
 * - 환불 승인/거부 프로세스
 * - 부분/전체 환불 관리
 * - 환불 사유 분류
 * - 자동 환불 규칙 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRefundService {
    
    private final PaymentRefundRepository refundRepository;
    private final PaymentRecordRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PGServiceManager pgServiceManager;
    
    // 캐시 이름
    private static final String CACHE_REFUND = "refund";
    private static final String CACHE_USER_REFUNDS = "userRefunds";
    private static final String CACHE_REFUND_STATS = "refundStats";
    
    // 설정값
    private static final BigDecimal MIN_REFUND_AMOUNT = new BigDecimal("100");
    private static final int AUTO_REFUND_THRESHOLD_HOURS = 24; // 자동 환불 임계시간
    private static final int REFUND_PROCESSING_DAYS = 7; // 환불 처리 기간
    private static final BigDecimal REFUND_FEE_RATE = new BigDecimal("0.03"); // 환불 수수료 3%
    
    /**
     * 환불 요청
     * @param paymentId 결제 ID
     * @param userId 요청자 ID
     * @param refundRequest 환불 요청 정보
     * @return 생성된 환불 기록
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CacheEvict(value = {CACHE_USER_REFUNDS, CACHE_REFUND_STATS}, allEntries = true)
    public PaymentRefund requestRefund(Long paymentId, Long userId, RefundRequest refundRequest) {
        log.info("Requesting refund: paymentId={}, userId={}, amount={}", 
                paymentId, userId, refundRequest.getRefundAmount());
        
        // 결제 기록 확인
        PaymentRecord payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentException("결제 기록을 찾을 수 없습니다: " + paymentId));
            
        // 사용자 권한 확인
        if (!payment.getUser().getUserId().equals(userId)) {
            throw new PaymentException("환불 요청 권한이 없습니다");
        }
        
        // 환불 가능성 검증
        validateRefundRequest(payment, refundRequest);
        
        // 환불 기록 생성
        PaymentRefund refund = PaymentRefund.builder()
            .payment(payment)
            .refundAmount(refundRequest.getRefundAmount())
            .refundReason(refundRequest.getRefundReason())
            .refundDescription(refundRequest.getDescription())
            .refundType(determineRefundType(payment, refundRequest.getRefundAmount()))
            .refundStatus(RefundStatus.PENDING)
            .requestedBy(userId)
            .refundFee(calculateRefundFee(refundRequest.getRefundAmount(), refundRequest.getRefundReason()))
            .actualRefundAmount(refundRequest.getRefundAmount().subtract(
                calculateRefundFee(refundRequest.getRefundAmount(), refundRequest.getRefundReason())
            ))
            .expectedProcessingDate(LocalDateTime.now().plusDays(REFUND_PROCESSING_DAYS))
            .build();
            
        PaymentRefund savedRefund = refundRepository.save(refund);
        
        // 자동 승인 규칙 확인
        if (shouldAutoApprove(payment, refundRequest)) {
            return approveRefund(savedRefund.getRefundId(), null, "자동 승인");
        }
        
        // 환불 요청 알림
        notificationService.sendRefundRequestNotification(userId, savedRefund);
        
        // 관리자에게 승인 요청 알림
        notificationService.sendRefundApprovalRequestToAdmin(savedRefund);
        
        // 이벤트 발행
        eventPublisher.publishEvent(new RefundRequestedEvent(savedRefund));
        
        log.info("Refund requested successfully: refundId={}", savedRefund.getRefundId());
        return savedRefund;
    }
    
    /**
     * 환불 승인
     * @param refundId 환불 ID
     * @param approverId 승인자 ID (null이면 자동 승인)
     * @param approvalNote 승인 메모
     * @return 승인된 환불 기록
     */
    @Transactional
    @CacheEvict(value = CACHE_REFUND, key = "#refundId")
    public PaymentRefund approveRefund(Long refundId, Long approverId, String approvalNote) {
        log.info("Approving refund: refundId={}, approverId={}", refundId, approverId);
        
        PaymentRefund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new PaymentException("환불 기록을 찾을 수 없습니다: " + refundId));
            
        // 환불 상태 확인
        if (refund.getRefundStatus() != RefundStatus.PENDING) {
            throw new PaymentException("승인 가능한 환불 상태가 아닙니다: " + refund.getRefundStatus());
        }
        
        // PG사 환불 처리
        try {
            PGRefundResponse pgResponse = pgServiceManager.processRefund(
                refund.getPayment().getPaymentGateway(),
                refund.getPayment().getPgTransactionId(),
                refund.getRefundAmount(),
                refund.getRefundDescription()
            );
            
            // 환불 승인 처리
            refund.setRefundStatus(RefundStatus.APPROVED);
            refund.setApprovedBy(approverId);
            refund.setApprovedAt(LocalDateTime.now());
            refund.setApprovalNote(approvalNote);
            refund.setPgRefundId(pgResponse.getPgRefundId());
            refund.setProcessingDate(LocalDateTime.now().plusDays(REFUND_PROCESSING_DAYS));
            
            // 결제 상태 업데이트
            updatePaymentRefundStatus(refund.getPayment(), refund);
            
        } catch (Exception e) {
            log.error("PG refund processing failed: refundId={}, error={}", refundId, e.getMessage());
            
            // 환불 실패 처리
            refund.setRefundStatus(RefundStatus.FAILED);
            refund.setFailureReason("PG사 환불 처리 실패: " + e.getMessage());
            refund.setFailedAt(LocalDateTime.now());
            
            throw new PaymentException("PG사 환불 처리 실패: " + e.getMessage());
        }
        
        PaymentRefund approvedRefund = refundRepository.save(refund);
        
        // 환불 승인 알림
        notificationService.sendRefundApprovalNotification(
            refund.getPayment().getUser().getUserId(), approvedRefund
        );
        
        // 이벤트 발행
        eventPublisher.publishEvent(new RefundApprovedEvent(approvedRefund));
        
        log.info("Refund approved successfully: refundId={}", refundId);
        return approvedRefund;
    }
    
    /**
     * 환불 거부
     * @param refundId 환불 ID
     * @param rejectorId 거부자 ID
     * @param rejectionReason 거부 사유
     * @return 거부된 환불 기록
     */
    @Transactional
    @CacheEvict(value = CACHE_REFUND, key = "#refundId")
    public PaymentRefund rejectRefund(Long refundId, Long rejectorId, String rejectionReason) {
        log.info("Rejecting refund: refundId={}, rejectorId={}", refundId, rejectorId);
        
        PaymentRefund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new PaymentException("환불 기록을 찾을 수 없습니다: " + refundId));
            
        // 환불 상태 확인
        if (refund.getRefundStatus() != RefundStatus.PENDING) {
            throw new PaymentException("거부 가능한 환불 상태가 아닙니다: " + refund.getRefundStatus());
        }
        
        // 환불 거부 처리
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setRejectedBy(rejectorId);
        refund.setRejectedAt(LocalDateTime.now());
        refund.setRejectionReason(rejectionReason);
        
        PaymentRefund rejectedRefund = refundRepository.save(refund);
        
        // 환불 거부 알림
        notificationService.sendRefundRejectionNotification(
            refund.getPayment().getUser().getUserId(), rejectedRefund, rejectionReason
        );
        
        // 이벤트 발행
        eventPublisher.publishEvent(new RefundRejectedEvent(rejectedRefund, rejectionReason));
        
        log.info("Refund rejected: refundId={}", refundId);
        return rejectedRefund;
    }
    
    /**
     * 환불 완료 처리
     * @param refundId 환불 ID
     * @param completedAmount 실제 환불된 금액
     * @return 완료된 환불 기록
     */
    @Transactional
    @CacheEvict(value = CACHE_REFUND, key = "#refundId")
    public PaymentRefund completeRefund(Long refundId, BigDecimal completedAmount) {
        log.info("Completing refund: refundId={}, completedAmount={}", refundId, completedAmount);
        
        PaymentRefund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new PaymentException("환불 기록을 찾을 수 없습니다: " + refundId));
            
        // 환불 상태 확인
        if (refund.getRefundStatus() != RefundStatus.APPROVED) {
            throw new PaymentException("완료 가능한 환불 상태가 아닙니다: " + refund.getRefundStatus());
        }
        
        // 환불 완료 처리
        refund.setRefundStatus(RefundStatus.COMPLETED);
        refund.setCompletedAt(LocalDateTime.now());
        refund.setActualRefundAmount(completedAmount);
        
        PaymentRefund completedRefund = refundRepository.save(refund);
        
        // 환불 완료 알림
        notificationService.sendRefundCompletionNotification(
            refund.getPayment().getUser().getUserId(), completedRefund
        );
        
        // 이벤트 발행
        eventPublisher.publishEvent(new RefundCompletedEvent(completedRefund));
        
        log.info("Refund completed successfully: refundId={}", refundId);
        return completedRefund;
    }
    
    /**
     * 환불 조회
     * @param refundId 환불 ID
     * @param userId 조회자 ID
     * @return 환불 기록
     */
    @Cacheable(value = CACHE_REFUND, key = "#refundId")
    public PaymentRefund getRefund(Long refundId, Long userId) {
        log.debug("Getting refund: refundId={}, userId={}", refundId, userId);
        
        PaymentRefund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new PaymentException("환불 기록을 찾을 수 없습니다: " + refundId));
            
        // 권한 확인
        if (!refund.getPayment().getUser().getUserId().equals(userId)) {
            throw new PaymentException("환불 조회 권한이 없습니다");
        }
        
        return refund;
    }
    
    /**
     * 사용자 환불 내역 조회
     * @param userId 사용자 ID
     * @param status 환불 상태 (선택사항)
     * @param pageable 페이징
     * @return 환불 내역 페이지
     */
    @Cacheable(value = CACHE_USER_REFUNDS,
              key = "#userId + '_' + #status + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<PaymentRefund> getUserRefunds(Long userId, RefundStatus status, Pageable pageable) {
        log.debug("Getting user refunds: userId={}, status={}", userId, status);
        
        if (status != null) {
            return refundRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            return refundRepository.findByUserId(userId, pageable);
        }
    }
    
    /**
     * 환불 통계 조회
     * @param startDate 시작일
     * @param endDate 종료일
     * @param refundReason 환불 사유 (선택사항)
     * @return 환불 통계
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
            
        // 환불율 계산
        Long totalPayments = paymentRepository.countByDateRangeAndStatus(
            startDate, endDate, PaymentStatus.COMPLETED
        );
        
        BigDecimal refundRate = totalPayments > 0 ?
            BigDecimal.valueOf(totalCount).divide(BigDecimal.valueOf(totalPayments), 4, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;
        
        return RefundStatistics.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalCount(totalCount)
            .totalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
            .averageAmount(totalCount > 0 ? totalAmount.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
            .refundRate(refundRate)
            .reasonStatistics(reasonStats)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
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
            
        for (PaymentRefund refund : pendingRefunds) {
            try {
                if (shouldAutoApprove(refund.getPayment(), 
                    RefundRequest.builder()
                        .refundAmount(refund.getRefundAmount())
                        .refundReason(refund.getRefundReason())
                        .build())) {
                    
                    approveRefund(refund.getRefundId(), null, "자동 승인 (시간 초과)");
                    log.info("Auto approved refund: refundId={}", refund.getRefundId());
                }
            } catch (Exception e) {
                log.error("Failed to auto approve refund: refundId={}, error={}", 
                         refund.getRefundId(), e.getMessage());
            }
        }
    }
    
    /**
     * 환불 요청 검증
     * @param payment 결제 기록
     * @param request 환불 요청
     */
    private void validateRefundRequest(PaymentRecord payment, RefundRequest request) {
        // 결제 상태 확인
        if (payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentException("완료된 결제만 환불 가능합니다");
        }
        
        // 환불 금액 확인
        if (request.getRefundAmount() == null || 
            request.getRefundAmount().compareTo(MIN_REFUND_AMOUNT) < 0) {
            throw new PaymentException("최소 환불 금액은 " + MIN_REFUND_AMOUNT + "원입니다");
        }
        
        // 이미 환불된 금액 확인
        BigDecimal alreadyRefunded = refundRepository
            .getTotalRefundedAmount(payment.getPaymentId());
            
        BigDecimal availableAmount = payment.getTotalAmount().subtract(
            alreadyRefunded != null ? alreadyRefunded : BigDecimal.ZERO
        );
        
        if (request.getRefundAmount().compareTo(availableAmount) > 0) {
            throw new PaymentException("환불 가능 금액을 초과했습니다. 가능 금액: " + availableAmount);
        }
        
        // 환불 사유 확인
        if (request.getRefundReason() == null) {
            throw new PaymentException("환불 사유를 선택해주세요");
        }
        
        // 환불 기간 확인 (일반적으로 결제 후 7일 이내)
        if (payment.getApprovedAt() != null && 
            payment.getApprovedAt().isBefore(LocalDateTime.now().minusDays(7))) {
            
            // 특정 사유에 대해서만 7일 이후 환불 허용
            if (!isExtendedRefundAllowed(request.getRefundReason())) {
                throw new PaymentException("환불 신청 기간이 만료되었습니다 (결제 후 7일)");
            }
        }
    }
    
    /**
     * 환불 타입 결정
     * @param payment 결제 기록
     * @param refundAmount 환불 금액
     * @return 환불 타입
     */
    private RefundType determineRefundType(PaymentRecord payment, BigDecimal refundAmount) {
        return refundAmount.compareTo(payment.getTotalAmount()) == 0 ?
            RefundType.FULL : RefundType.PARTIAL;
    }
    
    /**
     * 환불 수수료 계산
     * @param refundAmount 환불 금액
     * @param refundReason 환불 사유
     * @return 환불 수수료
     */
    private BigDecimal calculateRefundFee(BigDecimal refundAmount, RefundReason refundReason) {
        // 특정 사유는 수수료 면제
        if (isRefundFeeExempt(refundReason)) {
            return BigDecimal.ZERO;
        }
        
        return refundAmount.multiply(REFUND_FEE_RATE).setScale(0, RoundingMode.HALF_UP);
    }
    
    /**
     * 자동 승인 여부 확인
     * @param payment 결제 기록
     * @param request 환불 요청
     * @return 자동 승인 여부
     */
    private boolean shouldAutoApprove(PaymentRecord payment, RefundRequest request) {
        // 소액 환불은 자동 승인
        if (request.getRefundAmount().compareTo(new BigDecimal("10000")) <= 0) {
            return true;
        }
        
        // 특정 사유는 자동 승인
        if (request.getRefundReason() == RefundReason.SYSTEM_ERROR ||
            request.getRefundReason() == RefundReason.DUPLICATE_PAYMENT) {
            return true;
        }
        
        // 결제 후 24시간 이내 전액 환불
        if (payment.getApprovedAt() != null &&
            payment.getApprovedAt().isAfter(LocalDateTime.now().minusHours(24)) &&
            request.getRefundAmount().compareTo(payment.getTotalAmount()) == 0) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 연장 환불 허용 여부
     * @param refundReason 환불 사유
     * @return 연장 환불 허용 여부
     */
    private boolean isExtendedRefundAllowed(RefundReason refundReason) {
        return refundReason == RefundReason.DEFECTIVE_PRODUCT ||
               refundReason == RefundReason.SYSTEM_ERROR ||
               refundReason == RefundReason.SERVICE_ISSUE;
    }
    
    /**
     * 환불 수수료 면제 여부
     * @param refundReason 환불 사유
     * @return 수수료 면제 여부
     */
    private boolean isRefundFeeExempt(RefundReason refundReason) {
        return refundReason == RefundReason.SYSTEM_ERROR ||
               refundReason == RefundReason.DEFECTIVE_PRODUCT ||
               refundReason == RefundReason.SERVICE_ISSUE;
    }
    
    /**
     * 결제의 환불 상태 업데이트
     * @param payment 결제 기록
     * @param refund 환불 기록
     */
    private void updatePaymentRefundStatus(PaymentRecord payment, PaymentRefund refund) {
        BigDecimal totalRefunded = refundRepository.getTotalRefundedAmount(payment.getPaymentId());
        
        if (totalRefunded.compareTo(payment.getTotalAmount()) >= 0) {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        
        paymentRepository.save(payment);
    }
    
    // 이벤트 클래스들
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RefundRequestedEvent {
        private final PaymentRefund refund;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RefundApprovedEvent {
        private final PaymentRefund refund;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RefundRejectedEvent {
        private final PaymentRefund refund;
        private final String reason;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RefundCompletedEvent {
        private final PaymentRefund refund;
    }
    
    // DTO 클래스들
    @lombok.Builder
    @lombok.Getter
    public static class RefundStatistics {
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;
        private final Long totalCount;
        private final BigDecimal totalAmount;
        private final BigDecimal averageAmount;
        private final BigDecimal refundRate;
        private final List<RefundReasonStatistics> reasonStatistics;
        private final LocalDateTime generatedAt;
    }
    
    @lombok.Builder
    @lombok.Getter
    public static class RefundReasonStatistics {
        private final RefundReason reason;
        private final Long count;
        private final BigDecimal totalAmount;
        private final BigDecimal averageAmount;
    }
}
```

---

## 🔧 설정 및 통합

### application.yml 추가 설정
```yaml
# 환불 시스템 설정
app:
  payment:
    refund:
      cache-ttl: 1h  # 환불 캐시 TTL
      min-amount: 100  # 최소 환불 금액
      processing-days: 7  # 환불 처리 기간
      fee-rate: 0.03  # 환불 수수료 3%
      auto-approval:
        enabled: true
        threshold-hours: 24  # 자동 승인 임계시간
        max-amount: 10000  # 자동 승인 최대 금액
      schedule:
        auto-process: "0 0 */6 * * ?"  # 6시간마다
```

---

## 📊 주요 기능 요약

### 1. 환불 프로세스
- **환불 요청**: 검증, 수수료 계산, 자동 승인 판단
- **환불 승인**: PG사 연동, 상태 업데이트
- **환불 거부**: 사유 기록, 알림 발송
- **환불 완료**: 실제 환불 금액 확인

### 2. 환불 타입 관리
- **전체 환불**: 결제 금액 100% 환불
- **부분 환불**: 일부 금액만 환불
- **환불 수수료**: 사유별 차등 적용
- **환불 기간**: 결제 후 7일 원칙

### 3. 자동 환불 규칙
- **소액 환불**: 1만원 이하 자동 승인
- **시스템 오류**: 즉시 자동 승인
- **24시간 내 전액**: 자동 승인
- **스케줄링**: 6시간마다 자동 처리

### 4. 환불 사유 분류
- **단순 변심**: 일반 수수료 적용
- **시스템 오류**: 수수료 면제
- **상품 불량**: 수수료 면제, 연장 환불
- **서비스 문제**: 우선 처리

---

## ✅ 완료 사항
- ✅ 환불 처리 (PaymentRefund 엔티티)
- ✅ 환불 승인/거부 프로세스
- ✅ 부분 환불/전체 환불 처리
- ✅ 환불 사유 관리
- ✅ 환불 내역 조회
- ✅ 자동 환불 규칙 적용
- ✅ 환불 수수료 계산
- ✅ PG사 연동
- ✅ 이벤트 기반 알림 연동
- ✅ 스케줄링 기반 자동 처리

---

*PaymentRefundService 구현 완료: 환불 처리 및 승인 시스템*