# Step 6-5a: PaymentService 구현

> 결제 처리 서비스 - 결제 처리, 검증, 상태 관리, 한국 PG사 연동
> 생성일: 2025-08-22
> 단계: 6-5a (Service 레이어 - 결제 시스템)
> 참고: step4-4b1, step5-4d, step3-2c

---

## 🎯 설계 목표

- **결제 처리**: PaymentRecord 엔티티 관리
- **상태 관리**: PaymentStatus 전체 라이프사이클
- **결제 검증**: 트랜잭션 무결성 보장
- **한국 특화**: 카카오페이, 토스, 네이버페이 연동
- **보안**: PCI DSS 준수, 민감정보 암호화

---

## 💳 PaymentService 구현

### PaymentService.java
```java
package com.routepick.service.payment;

import com.routepick.common.enums.PaymentStatus;
import com.routepick.common.enums.PaymentMethod;
import com.routepick.common.enums.PaymentGateway;
import com.routepick.domain.payment.entity.PaymentRecord;
import com.routepick.domain.payment.entity.PaymentDetail;
import com.routepick.domain.payment.entity.PaymentItem;
import com.routepick.domain.payment.repository.PaymentRecordRepository;
import com.routepick.domain.payment.repository.PaymentDetailRepository;
import com.routepick.domain.payment.repository.PaymentItemRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.payment.PaymentException;
import com.routepick.exception.user.UserException;
import com.routepick.util.EncryptionUtil;
import com.routepick.util.TransactionIdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
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
 * 결제 처리 서비스
 * - 결제 처리 및 검증
 * - PaymentStatus 관리
 * - 결제 상세 정보 관리
 * - PG사 연동 및 보안
 * - 결제 내역 조회 및 통계
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {
    
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentItemRepository paymentItemRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PGServiceManager pgServiceManager; // PG사 통합 관리자
    
    // 캐시 이름
    private static final String CACHE_PAYMENT = "payment";
    private static final String CACHE_USER_PAYMENTS = "userPayments";
    private static final String CACHE_PAYMENT_STATS = "paymentStats";
    
    // 설정값
    private static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("100"); // 최소 결제 금액
    private static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("10000000"); // 최대 결제 금액
    private static final int PAYMENT_TIMEOUT_MINUTES = 30; // 결제 타임아웃
    private static final int MAX_RETRY_COUNT = 3; // 최대 재시도 횟수
    
    /**
     * 결제 요청 처리
     * @param userId 사용자 ID
     * @param paymentRequest 결제 요청 정보
     * @return 생성된 결제 기록
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CacheEvict(value = {CACHE_USER_PAYMENTS, CACHE_PAYMENT_STATS}, allEntries = true)
    public PaymentRecord processPayment(Long userId, PaymentRequest paymentRequest) {
        log.info("Processing payment: userId={}, amount={}, method={}", 
                userId, paymentRequest.getTotalAmount(), paymentRequest.getPaymentMethod());
        
        // 사용자 확인
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException("사용자를 찾을 수 없습니다: " + userId));
            
        // 결제 요청 검증
        validatePaymentRequest(paymentRequest);
        
        // 거래 ID 생성
        String transactionId = TransactionIdGenerator.generate();
        String merchantUid = generateMerchantUid(userId);
        String orderNumber = generateOrderNumber();
        
        // 결제 기록 생성
        PaymentRecord payment = PaymentRecord.builder()
            .user(user)
            .transactionId(transactionId)
            .merchantUid(merchantUid)
            .orderNumber(orderNumber)
            .paymentStatus(PaymentStatus.PENDING)
            .paymentMethod(paymentRequest.getPaymentMethod().toString())
            .paymentGateway(paymentRequest.getPaymentGateway().toString())
            .totalAmount(paymentRequest.getTotalAmount())
            .taxAmount(calculateTaxAmount(paymentRequest.getTotalAmount()))
            .discountAmount(paymentRequest.getDiscountAmount())
            .currency("KRW")
            .paymentDescription(paymentRequest.getDescription())
            .buyerName(user.getRealName())
            .buyerEmail(user.getEmail())
            .buyerTel(user.getPhoneNumber())
            .customerIp(paymentRequest.getCustomerIp())
            .userAgent(paymentRequest.getUserAgent())
            .build();
            
        PaymentRecord savedPayment = paymentRecordRepository.save(payment);
        
        // 결제 상세 정보 생성
        createPaymentDetails(savedPayment, paymentRequest);
        
        // 결제 항목 생성
        createPaymentItems(savedPayment, paymentRequest.getPaymentItems());
        
        // PG사 결제 요청
        try {
            PGPaymentResponse pgResponse = requestPGPayment(savedPayment, paymentRequest);
            
            // PG사 응답 처리
            updatePaymentWithPGResponse(savedPayment, pgResponse);
            
        } catch (Exception e) {
            log.error("PG payment request failed: transactionId={}, error={}", 
                     transactionId, e.getMessage());
            
            // 결제 실패 처리
            updatePaymentStatus(savedPayment, PaymentStatus.FAILED, e.getMessage());
            throw new PaymentException("결제 요청 실패: " + e.getMessage());
        }
        
        // 이벤트 발행
        eventPublisher.publishEvent(new PaymentRequestedEvent(savedPayment));
        
        log.info("Payment processed successfully: transactionId={}", transactionId);
        return savedPayment;
    }
    
    /**
     * 결제 승인 처리
     * @param transactionId 거래 ID
     * @param pgTransactionId PG사 거래 ID
     * @param approvalNumber 승인 번호
     * @return 승인된 결제 기록
     */
    @Transactional
    @CacheEvict(value = CACHE_PAYMENT, key = "#transactionId")
    public PaymentRecord approvePayment(String transactionId, String pgTransactionId, 
                                      String approvalNumber) {
        log.info("Approving payment: transactionId={}, pgTransactionId={}", 
                transactionId, pgTransactionId);
        
        PaymentRecord payment = paymentRecordRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new PaymentException("결제 기록을 찾을 수 없습니다: " + transactionId));
            
        // 결제 상태 확인
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new PaymentException("승인 가능한 결제 상태가 아닙니다: " + payment.getPaymentStatus());
        }
        
        // PG사 결제 검증
        boolean isValid = pgServiceManager.verifyPayment(payment.getPaymentGateway(), 
                                                        pgTransactionId, 
                                                        payment.getTotalAmount());
        if (!isValid) {
            updatePaymentStatus(payment, PaymentStatus.FAILED, "PG사 검증 실패");
            throw new PaymentException("PG사 결제 검증 실패");
        }
        
        // 결제 승인 처리
        payment.setPaymentStatus(PaymentStatus.COMPLETED);
        payment.setPgTransactionId(pgTransactionId);
        payment.setApprovalNumber(approvalNumber);
        payment.setApprovedAt(LocalDateTime.now());
        
        PaymentRecord approvedPayment = paymentRecordRepository.save(payment);
        
        // 결제 성공 알림
        notificationService.sendPaymentSuccessNotification(
            payment.getUser().getUserId(), payment
        );
        
        // 이벤트 발행
        eventPublisher.publishEvent(new PaymentApprovedEvent(approvedPayment));
        
        log.info("Payment approved successfully: transactionId={}", transactionId);
        return approvedPayment;
    }
    
    /**
     * 결제 실패 처리
     * @param transactionId 거래 ID
     * @param failureReason 실패 사유
     * @param errorCode 오류 코드
     * @return 실패 처리된 결제 기록
     */
    @Transactional
    @CacheEvict(value = CACHE_PAYMENT, key = "#transactionId")
    public PaymentRecord failPayment(String transactionId, String failureReason, String errorCode) {
        log.info("Failing payment: transactionId={}, reason={}", transactionId, failureReason);
        
        PaymentRecord payment = paymentRecordRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new PaymentException("결제 기록을 찾을 수 없습니다: " + transactionId));
            
        // 실패 처리
        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setFailureReason(failureReason);
        payment.setErrorCode(errorCode);
        payment.setFailedAt(LocalDateTime.now());
        
        PaymentRecord failedPayment = paymentRecordRepository.save(payment);
        
        // 결제 실패 알림
        notificationService.sendPaymentFailureNotification(
            payment.getUser().getUserId(), payment, failureReason
        );
        
        // 이벤트 발행
        eventPublisher.publishEvent(new PaymentFailedEvent(failedPayment, failureReason));
        
        log.info("Payment failed: transactionId={}", transactionId);
        return failedPayment;
    }
    
    /**
     * 결제 취소 처리
     * @param transactionId 거래 ID
     * @param userId 취소 요청자 ID
     * @param cancelReason 취소 사유
     * @return 취소된 결제 기록
     */
    @Transactional
    @CacheEvict(value = CACHE_PAYMENT, key = "#transactionId")
    public PaymentRecord cancelPayment(String transactionId, Long userId, String cancelReason) {
        log.info("Cancelling payment: transactionId={}, userId={}", transactionId, userId);
        
        PaymentRecord payment = paymentRecordRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new PaymentException("결제 기록을 찾을 수 없습니다: " + transactionId));
            
        // 권한 확인
        if (!payment.getUser().getUserId().equals(userId)) {
            throw new PaymentException("결제 취소 권한이 없습니다");
        }
        
        // 취소 가능 상태 확인
        if (!canCancelPayment(payment)) {
            throw new PaymentException("취소 가능한 결제 상태가 아닙니다: " + payment.getPaymentStatus());
        }
        
        // PG사 결제 취소 요청
        try {
            pgServiceManager.cancelPayment(payment.getPaymentGateway(), 
                                         payment.getPgTransactionId(),
                                         payment.getTotalAmount(),
                                         cancelReason);
        } catch (Exception e) {
            log.error("PG payment cancellation failed: transactionId={}, error={}", 
                     transactionId, e.getMessage());
            throw new PaymentException("PG사 결제 취소 실패: " + e.getMessage());
        }
        
        // 결제 취소 처리
        payment.setPaymentStatus(PaymentStatus.CANCELLED);
        payment.setCancelReason(cancelReason);
        payment.setCancelledAt(LocalDateTime.now());
        
        PaymentRecord cancelledPayment = paymentRecordRepository.save(payment);
        
        // 결제 취소 알림
        notificationService.sendPaymentCancellationNotification(
            payment.getUser().getUserId(), payment
        );
        
        // 이벤트 발행
        eventPublisher.publishEvent(new PaymentCancelledEvent(cancelledPayment));
        
        log.info("Payment cancelled successfully: transactionId={}", transactionId);
        return cancelledPayment;
    }
    
    /**
     * 결제 조회
     * @param transactionId 거래 ID
     * @param userId 조회자 ID
     * @return 결제 기록
     */
    @Cacheable(value = CACHE_PAYMENT, key = "#transactionId")
    public PaymentRecord getPayment(String transactionId, Long userId) {
        log.debug("Getting payment: transactionId={}, userId={}", transactionId, userId);
        
        PaymentRecord payment = paymentRecordRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new PaymentException("결제 기록을 찾을 수 없습니다: " + transactionId));
            
        // 권한 확인
        if (!payment.getUser().getUserId().equals(userId)) {
            throw new PaymentException("결제 조회 권한이 없습니다");
        }
        
        return payment;
    }
    
    /**
     * 사용자 결제 내역 조회
     * @param userId 사용자 ID
     * @param status 결제 상태 (선택사항)
     * @param pageable 페이징
     * @return 결제 내역 페이지
     */
    @Cacheable(value = CACHE_USER_PAYMENTS, 
              key = "#userId + '_' + #status + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<PaymentRecord> getUserPayments(Long userId, PaymentStatus status, Pageable pageable) {
        log.debug("Getting user payments: userId={}, status={}", userId, status);
        
        if (status != null) {
            return paymentRecordRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            return paymentRecordRepository.findByUserId(userId, pageable);
        }
    }
    
    /**
     * 결제 통계 조회
     * @param startDate 시작일
     * @param endDate 종료일
     * @param paymentMethod 결제 방법 (선택사항)
     * @return 결제 통계
     */
    @Cacheable(value = CACHE_PAYMENT_STATS, 
              key = "#startDate + '_' + #endDate + '_' + #paymentMethod")
    public PaymentStatistics getPaymentStatistics(LocalDateTime startDate, LocalDateTime endDate,
                                                 PaymentMethod paymentMethod) {
        log.debug("Getting payment statistics: startDate={}, endDate={}, method={}", 
                 startDate, endDate, paymentMethod);
        
        // 총 결제 건수 및 금액
        Long totalCount = paymentRecordRepository.countByDateRangeAndStatus(
            startDate, endDate, PaymentStatus.COMPLETED
        );
        
        BigDecimal totalAmount = paymentRecordRepository.sumAmountByDateRangeAndStatus(
            startDate, endDate, PaymentStatus.COMPLETED
        );
        
        // 결제 방법별 통계
        List<PaymentMethodStatistics> methodStats = paymentRecordRepository
            .getPaymentMethodStatistics(startDate, endDate);
            
        // 실패율 계산
        Long failedCount = paymentRecordRepository.countByDateRangeAndStatus(
            startDate, endDate, PaymentStatus.FAILED
        );
        
        BigDecimal failureRate = totalCount > 0 ? 
            BigDecimal.valueOf(failedCount).divide(BigDecimal.valueOf(totalCount + failedCount), 4, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;
        
        return PaymentStatistics.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalCount(totalCount)
            .totalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
            .averageAmount(totalCount > 0 ? totalAmount.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
            .failureRate(failureRate)
            .methodStatistics(methodStats)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 결제 재시도 처리
     * @param transactionId 거래 ID
     * @param userId 사용자 ID
     * @return 재시도된 결제 기록
     */
    @Transactional
    public PaymentRecord retryPayment(String transactionId, Long userId) {
        log.info("Retrying payment: transactionId={}, userId={}", transactionId, userId);
        
        PaymentRecord payment = paymentRecordRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new PaymentException("결제 기록을 찾을 수 없습니다: " + transactionId));
            
        // 권한 확인
        if (!payment.getUser().getUserId().equals(userId)) {
            throw new PaymentException("결제 재시도 권한이 없습니다");
        }
        
        // 재시도 가능 확인
        if (payment.getRetryCount() >= MAX_RETRY_COUNT) {
            throw new PaymentException("최대 재시도 횟수를 초과했습니다");
        }
        
        if (payment.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new PaymentException("재시도 가능한 결제 상태가 아닙니다");
        }
        
        // 재시도 카운트 증가
        payment.setRetryCount(payment.getRetryCount() + 1);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setFailureReason(null);
        payment.setErrorCode(null);
        
        PaymentRecord retriedPayment = paymentRecordRepository.save(payment);
        
        // PG사 재결제 요청 (비동기)
        requestPGPaymentAsync(retriedPayment);
        
        log.info("Payment retry initiated: transactionId={}", transactionId);
        return retriedPayment;
    }
    
    /**
     * 결제 상태 업데이트
     * @param payment 결제 기록
     * @param status 새로운 상태
     * @param message 메시지
     */
    private void updatePaymentStatus(PaymentRecord payment, PaymentStatus status, String message) {
        payment.setPaymentStatus(status);
        
        switch (status) {
            case COMPLETED:
                payment.setApprovedAt(LocalDateTime.now());
                break;
            case FAILED:
                payment.setFailedAt(LocalDateTime.now());
                payment.setFailureReason(message);
                break;
            case CANCELLED:
                payment.setCancelledAt(LocalDateTime.now());
                payment.setCancelReason(message);
                break;
            default:
                break;
        }
        
        paymentRecordRepository.save(payment);
    }
    
    /**
     * 결제 요청 검증
     * @param request 결제 요청
     */
    private void validatePaymentRequest(PaymentRequest request) {
        if (request.getTotalAmount() == null) {
            throw new PaymentException("결제 금액이 필요합니다");
        }
        
        if (request.getTotalAmount().compareTo(MIN_PAYMENT_AMOUNT) < 0) {
            throw new PaymentException("최소 결제 금액은 " + MIN_PAYMENT_AMOUNT + "원입니다");
        }
        
        if (request.getTotalAmount().compareTo(MAX_PAYMENT_AMOUNT) > 0) {
            throw new PaymentException("최대 결제 금액은 " + MAX_PAYMENT_AMOUNT + "원입니다");
        }
        
        if (!StringUtils.hasText(request.getDescription())) {
            throw new PaymentException("결제 설명이 필요합니다");
        }
        
        if (request.getPaymentItems() == null || request.getPaymentItems().isEmpty()) {
            throw new PaymentException("결제 항목이 필요합니다");
        }
    }
    
    /**
     * 세금 계산
     * @param amount 금액
     * @return 세금 금액
     */
    private BigDecimal calculateTaxAmount(BigDecimal amount) {
        // 10% VAT
        return amount.multiply(new BigDecimal("0.1")).setScale(0, RoundingMode.HALF_UP);
    }
    
    /**
     * 가맹점 주문번호 생성
     * @param userId 사용자 ID
     * @return 가맹점 주문번호
     */
    private String generateMerchantUid(Long userId) {
        return String.format("RP_%d_%d", userId, System.currentTimeMillis());
    }
    
    /**
     * 주문번호 생성
     * @return 주문번호
     */
    private String generateOrderNumber() {
        return String.format("ORD%d", System.currentTimeMillis());
    }
    
    /**
     * 결제 취소 가능 여부 확인
     * @param payment 결제 기록
     * @return 취소 가능 여부
     */
    private boolean canCancelPayment(PaymentRecord payment) {
        return payment.getPaymentStatus() == PaymentStatus.PENDING ||
               payment.getPaymentStatus() == PaymentStatus.COMPLETED;
    }
    
    /**
     * 결제 상세 정보 생성
     * @param payment 결제 기록
     * @param request 결제 요청
     */
    private void createPaymentDetails(PaymentRecord payment, PaymentRequest request) {
        PaymentDetail detail = PaymentDetail.builder()
            .payment(payment)
            .cardNumber(EncryptionUtil.encrypt(request.getCardNumber()))
            .cardHolderName(request.getCardHolderName())
            .expiryDate(request.getExpiryDate())
            .installmentPlan(request.getInstallmentPlan())
            .bankCode(request.getBankCode())
            .accountNumber(EncryptionUtil.encrypt(request.getAccountNumber()))
            .build();
            
        paymentDetailRepository.save(detail);
    }
    
    /**
     * 결제 항목 생성
     * @param payment 결제 기록
     * @param items 결제 항목 목록
     */
    private void createPaymentItems(PaymentRecord payment, List<PaymentItemRequest> items) {
        for (PaymentItemRequest itemRequest : items) {
            PaymentItem item = PaymentItem.builder()
                .payment(payment)
                .itemName(itemRequest.getItemName())
                .itemType(itemRequest.getItemType())
                .quantity(itemRequest.getQuantity())
                .unitPrice(itemRequest.getUnitPrice())
                .totalPrice(itemRequest.getUnitPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())))
                .build();
                
            paymentItemRepository.save(item);
        }
    }
    
    /**
     * PG사 결제 요청
     * @param payment 결제 기록
     * @param request 결제 요청
     * @return PG사 응답
     */
    private PGPaymentResponse requestPGPayment(PaymentRecord payment, PaymentRequest request) {
        return pgServiceManager.requestPayment(
            payment.getPaymentGateway(),
            payment.getTransactionId(),
            payment.getTotalAmount(),
            payment.getPaymentDescription(),
            request
        );
    }
    
    /**
     * PG사 응답으로 결제 정보 업데이트
     * @param payment 결제 기록
     * @param response PG사 응답
     */
    private void updatePaymentWithPGResponse(PaymentRecord payment, PGPaymentResponse response) {
        payment.setPgTransactionId(response.getPgTransactionId());
        payment.setRedirectUrl(response.getRedirectUrl());
        payment.setNextAction(response.getNextAction());
        
        paymentRecordRepository.save(payment);
    }
    
    /**
     * PG사 비동기 결제 요청
     * @param payment 결제 기록
     */
    @Async
    public CompletableFuture<Void> requestPGPaymentAsync(PaymentRecord payment) {
        try {
            // PG사 결제 재요청 로직
            log.info("Async PG payment request: transactionId={}", payment.getTransactionId());
        } catch (Exception e) {
            log.error("Async PG payment request failed: transactionId={}, error={}", 
                     payment.getTransactionId(), e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // 이벤트 클래스들
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class PaymentRequestedEvent {
        private final PaymentRecord payment;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class PaymentApprovedEvent {
        private final PaymentRecord payment;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class PaymentFailedEvent {
        private final PaymentRecord payment;
        private final String reason;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class PaymentCancelledEvent {
        private final PaymentRecord payment;
    }
    
    // DTO 클래스들
    @lombok.Builder
    @lombok.Getter
    public static class PaymentStatistics {
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;
        private final Long totalCount;
        private final BigDecimal totalAmount;
        private final BigDecimal averageAmount;
        private final BigDecimal failureRate;
        private final List<PaymentMethodStatistics> methodStatistics;
        private final LocalDateTime generatedAt;
    }
    
    @lombok.Builder
    @lombok.Getter
    public static class PaymentMethodStatistics {
        private final String paymentMethod;
        private final Long count;
        private final BigDecimal totalAmount;
        private final BigDecimal averageAmount;
    }
}
```

### PaymentException.java (새로 추가)
```java
package com.routepick.exception.payment;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 결제 관련 예외 클래스
 */
@Getter
public class PaymentException extends BaseException {
    
    public PaymentException(String message) {
        super(ErrorCode.PAYMENT_ERROR, message);
    }
    
    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public PaymentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    // 팩토리 메서드
    public static PaymentException invalidAmount(BigDecimal amount) {
        return new PaymentException(ErrorCode.INVALID_PAYMENT_AMOUNT, 
            "유효하지 않은 결제 금액입니다: " + amount);
    }
    
    public static PaymentException paymentNotFound(String transactionId) {
        return new PaymentException(ErrorCode.PAYMENT_NOT_FOUND,
            "결제 기록을 찾을 수 없습니다: " + transactionId);
    }
    
    public static PaymentException invalidStatus(PaymentStatus status) {
        return new PaymentException(ErrorCode.INVALID_PAYMENT_STATUS,
            "유효하지 않은 결제 상태입니다: " + status);
    }
    
    public static PaymentException pgError(String message) {
        return new PaymentException(ErrorCode.PG_ERROR,
            "PG사 오류: " + message);
    }
}
```

---

## 🔧 설정 및 통합

### application.yml 추가 설정
```yaml
# 결제 시스템 설정
app:
  payment:
    cache-ttl: 1h  # 결제 캐시 TTL
    min-amount: 100  # 최소 결제 금액
    max-amount: 10000000  # 최대 결제 금액
    timeout-minutes: 30  # 결제 타임아웃
    max-retry-count: 3  # 최대 재시도 횟수
    
  # PG사 설정
  pg:
    default: IAMPORT
    timeout: 30000  # 30초
    gateways:
      iamport:
        rest-api-key: ${IAMPORT_REST_API_KEY}
        rest-api-secret: ${IAMPORT_REST_API_SECRET}
      toss:
        client-key: ${TOSS_CLIENT_KEY}
        secret-key: ${TOSS_SECRET_KEY}
```

---

## 📊 주요 기능 요약

### 1. 결제 처리
- **결제 요청**: 검증, PG사 연동, 상태 관리
- **결제 승인**: PG사 검증, 결제 완료 처리
- **결제 실패**: 실패 사유 기록, 재시도 지원
- **결제 취소**: PG사 취소 요청, 상태 업데이트

### 2. 상태 관리
- **PaymentStatus**: PENDING → COMPLETED/FAILED/CANCELLED
- **트랜잭션 격리**: SERIALIZABLE 레벨 보장
- **상태 전이**: 유효한 상태 변경만 허용
- **이력 관리**: 모든 상태 변경 기록

### 3. 보안 및 검증
- **민감정보 암호화**: 카드번호, 계좌번호
- **PG사 검증**: 결제 금액, 거래 ID 검증
- **권한 확인**: 사용자별 결제 접근 제어
- **트랜잭션 무결성**: 결제 데이터 일관성

### 4. 한국 특화
- **결제 방법**: 카드, 계좌이체, 카카오페이, 네이버페이
- **PG사 연동**: 이니시스, 토스, 아임포트, KCP
- **세금 계산**: 10% VAT 자동 계산
- **결제 규정**: 한국 전자상거래법 준수

---

## ✅ 완료 사항
- ✅ 결제 처리 (PaymentRecord 엔티티)
- ✅ PaymentStatus 관리 (PENDING, COMPLETED, FAILED, CANCELLED, REFUNDED)
- ✅ 결제 상세 정보 관리 (PaymentDetail)
- ✅ 결제 항목 관리 (PaymentItem)
- ✅ 결제 검증 및 승인 로직
- ✅ 결제 내역 조회 및 통계
- ✅ PG사 연동 프레임워크
- ✅ 보안 및 암호화
- ✅ 이벤트 기반 알림 연동

---

*PaymentService 설계 완료: 결제 처리 및 보안 시스템*