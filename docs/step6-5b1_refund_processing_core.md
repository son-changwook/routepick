# Step 6-5b1: Refund Processing Core Service

**파일**: `routepick-backend/src/main/java/com/routepick/service/payment/PaymentRefundService.java`

이 파일은 환불 처리의 핵심 기능을 구현합니다.

## 💰 환불 처리 핵심 서비스 구현

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
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.payment.PaymentException;
import com.routepick.dto.payment.RefundRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 환불 처리 핵심 서비스
 * 
 * 주요 기능:
 * 1. 환불 요청 및 검증
 * 2. 환불 승인/거부 처리
 * 3. PG사 환불 연동
 * 4. 환불 상태 관리
 * 5. 환불 수수료 계산
 * 
 * @author RoutePickr Team
 * @since 2025.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRefundService {
    
    private final PaymentRefundRepository refundRepository;
    private final PaymentRecordRepository paymentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PGServiceManager pgServiceManager;
    
    // 캐시 이름
    private static final String CACHE_REFUND = "refund";
    private static final String CACHE_USER_REFUNDS = "userRefunds";
    
    // 설정값
    private static final BigDecimal MIN_REFUND_AMOUNT = new BigDecimal("100");
    private static final int REFUND_PROCESSING_DAYS = 7; // 환불 처리 기간
    private static final BigDecimal REFUND_FEE_RATE = new BigDecimal("0.03"); // 환불 수수료 3%
    
    // ===================== 환불 요청 =====================
    
    /**
     * 환불 요청
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CacheEvict(value = CACHE_USER_REFUNDS, allEntries = true)
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
    
    // ===================== 환불 승인/거부 =====================
    
    /**
     * 환불 승인
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
        
        // 결제 상태 최종 업데이트
        updatePaymentRefundStatus(refund.getPayment(), refund);
        
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
     * 환불 실패 처리 (웹훅용)
     */
    @Transactional
    @CacheEvict(value = CACHE_REFUND, key = "#refundId")
    public PaymentRefund handleRefundFailure(Long refundId, String failureReason) {
        log.info("Handling refund failure: refundId={}, reason={}", refundId, failureReason);
        
        PaymentRefund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new PaymentException("환불 기록을 찾을 수 없습니다: " + refundId));
        
        refund.setRefundStatus(RefundStatus.FAILED);
        refund.setFailureReason(failureReason);
        refund.setFailedAt(LocalDateTime.now());
        
        PaymentRefund failedRefund = refundRepository.save(refund);
        
        // 환불 실패 알림
        notificationService.sendRefundFailureNotification(
            refund.getPayment().getUser().getUserId(), failedRefund, failureReason
        );
        
        // 이벤트 발행
        eventPublisher.publishEvent(new RefundFailedEvent(failedRefund, failureReason));
        
        return failedRefund;
    }
    
    // ===================== 환불 조회 =====================
    
    /**
     * 환불 조회
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
     * 관리자용 환불 내역 조회
     */
    public Page<PaymentRefund> getAdminRefunds(RefundStatus status, RefundReason reason, 
                                             LocalDateTime startDate, LocalDateTime endDate,
                                             Pageable pageable) {
        log.debug("Getting admin refunds: status={}, reason={}, startDate={}, endDate={}", 
                status, reason, startDate, endDate);
        
        return refundRepository.findAdminRefunds(status, reason, startDate, endDate, pageable);
    }
    
    // ===================== 환불 검증 및 계산 =====================
    
    /**
     * 환불 요청 검증
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
        
        // 중복 환불 신청 확인
        boolean hasPendingRefund = refundRepository.existsByPaymentIdAndStatus(
            payment.getPaymentId(), RefundStatus.PENDING
        );
        
        if (hasPendingRefund) {
            throw new PaymentException("이미 처리 중인 환불 요청이 있습니다");
        }
    }
    
    /**
     * 환불 타입 결정
     */
    private RefundType determineRefundType(PaymentRecord payment, BigDecimal refundAmount) {
        // 기존 환불된 금액 고려
        BigDecimal alreadyRefunded = refundRepository
            .getTotalRefundedAmount(payment.getPaymentId());
        
        BigDecimal totalRefundAmount = refundAmount.add(
            alreadyRefunded != null ? alreadyRefunded : BigDecimal.ZERO
        );
        
        return totalRefundAmount.compareTo(payment.getTotalAmount()) == 0 ?
            RefundType.FULL : RefundType.PARTIAL;
    }
    
    /**
     * 환불 수수료 계산
     */
    private BigDecimal calculateRefundFee(BigDecimal refundAmount, RefundReason refundReason) {
        // 특정 사유는 수수료 면제
        if (isRefundFeeExempt(refundReason)) {
            return BigDecimal.ZERO;
        }
        
        // 소액은 수수료 면제 (1만원 이하)
        if (refundAmount.compareTo(new BigDecimal("10000")) <= 0) {
            return BigDecimal.ZERO;
        }
        
        return refundAmount.multiply(REFUND_FEE_RATE).setScale(0, RoundingMode.HALF_UP);
    }
    
    /**
     * 자동 승인 여부 확인
     */
    private boolean shouldAutoApprove(PaymentRecord payment, RefundRequest request) {
        // 소액 환불은 자동 승인 (1만원 이하)
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
     */
    private boolean isExtendedRefundAllowed(RefundReason refundReason) {
        return refundReason == RefundReason.DEFECTIVE_PRODUCT ||
               refundReason == RefundReason.SYSTEM_ERROR ||
               refundReason == RefundReason.SERVICE_ISSUE;
    }
    
    /**
     * 환불 수수료 면제 여부
     */
    private boolean isRefundFeeExempt(RefundReason refundReason) {
        return refundReason == RefundReason.SYSTEM_ERROR ||
               refundReason == RefundReason.DEFECTIVE_PRODUCT ||
               refundReason == RefundReason.SERVICE_ISSUE ||
               refundReason == RefundReason.DUPLICATE_PAYMENT;
    }
    
    /**
     * 결제의 환불 상태 업데이트
     */
    private void updatePaymentRefundStatus(PaymentRecord payment, PaymentRefund refund) {
        BigDecimal totalRefunded = refundRepository.getTotalApprovedRefundedAmount(payment.getPaymentId());
        
        if (totalRefunded.compareTo(payment.getTotalAmount()) >= 0) {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
        } else if (totalRefunded.compareTo(BigDecimal.ZERO) > 0) {
            payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        
        paymentRepository.save(payment);
    }
    
    /**
     * 환불 가능 여부 확인
     */
    public boolean isRefundable(Long paymentId, Long userId) {
        try {
            PaymentRecord payment = paymentRepository.findById(paymentId)
                .orElse(null);
            
            if (payment == null || !payment.getUser().getUserId().equals(userId)) {
                return false;
            }
            
            // 결제 완료 상태 확인
            if (payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
                return false;
            }
            
            // 이미 전액 환불된 경우
            BigDecimal totalRefunded = refundRepository.getTotalRefundedAmount(paymentId);
            if (totalRefunded != null && totalRefunded.compareTo(payment.getTotalAmount()) >= 0) {
                return false;
            }
            
            // 진행 중인 환불이 있는 경우
            if (refundRepository.existsByPaymentIdAndStatus(paymentId, RefundStatus.PENDING)) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error checking refundability: paymentId={}, userId={}, error={}", 
                     paymentId, userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 환불 가능 금액 조회
     */
    public BigDecimal getRefundableAmount(Long paymentId, Long userId) {
        PaymentRecord payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentException("결제 기록을 찾을 수 없습니다: " + paymentId));
        
        // 권한 확인
        if (!payment.getUser().getUserId().equals(userId)) {
            throw new PaymentException("환불 조회 권한이 없습니다");
        }
        
        BigDecimal alreadyRefunded = refundRepository.getTotalRefundedAmount(paymentId);
        
        return payment.getTotalAmount().subtract(
            alreadyRefunded != null ? alreadyRefunded : BigDecimal.ZERO
        );
    }
}
```

## 📋 환불 이벤트 클래스

```java
/**
 * 환불 관련 이벤트 클래스들
 */
@Getter
@AllArgsConstructor
public class RefundRequestedEvent {
    private final PaymentRefund refund;
}

@Getter
@AllArgsConstructor
public class RefundApprovedEvent {
    private final PaymentRefund refund;
}

@Getter
@AllArgsConstructor
public class RefundRejectedEvent {
    private final PaymentRefund refund;
    private final String reason;
}

@Getter
@AllArgsConstructor
public class RefundCompletedEvent {
    private final PaymentRefund refund;
}

@Getter
@AllArgsConstructor
public class RefundFailedEvent {
    private final PaymentRefund refund;
    private final String reason;
}
```

## 🔧 환불 요청 DTO

```java
/**
 * 환불 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    
    @NotNull(message = "환불 금액은 필수입니다")
    @DecimalMin(value = "100", message = "최소 환불 금액은 100원입니다")
    private BigDecimal refundAmount;
    
    @NotNull(message = "환불 사유는 필수입니다")
    private RefundReason refundReason;
    
    @Size(max = 500, message = "환불 사유 설명은 500자를 초과할 수 없습니다")
    private String description;
    
    // 고객 계좌 정보 (필요시)
    private String refundAccountBank;
    private String refundAccountNumber;
    private String refundAccountHolder;
}

/**
 * 환불 응답 DTO
 */
@Data
@Builder
public class RefundResponse {
    private Long refundId;
    private Long paymentId;
    private BigDecimal refundAmount;
    private BigDecimal refundFee;
    private BigDecimal actualRefundAmount;
    private RefundType refundType;
    private RefundStatus refundStatus;
    private RefundReason refundReason;
    private String refundDescription;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime expectedProcessingDate;
    private LocalDateTime completedAt;
    private String approvalNote;
    private String rejectionReason;
}
```

## 📊 연동 참고사항

### step6-5b2_refund_automation_statistics.md 연동점
1. **자동 환불**: 규칙 기반 자동 승인 시스템
2. **통계 분석**: 환불 패턴 및 사유 분석
3. **스케줄링**: 자동 환불 처리 스케줄러
4. **모니터링**: 환불 처리 성능 모니터링

### 주요 의존성
- **PaymentRefundRepository**: 환불 데이터 관리
- **PaymentRecordRepository**: 결제 정보 조회
- **PGServiceManager**: PG사 환불 처리 연동
- **NotificationService**: 환불 상태 알림

### 보안 고려사항
1. **권한 검증**: 환불 요청자 권한 확인
2. **중복 방지**: 동일 결제 중복 환불 방지
3. **금액 검증**: 환불 가능 금액 엄격 검증
4. **트랜잭션**: SERIALIZABLE 격리 수준 적용

---
**연관 파일**: `step6-5b2_refund_automation_statistics.md`
**구현 우선순위**: HIGH (결제 시스템 핵심)
**예상 개발 기간**: 3-4일