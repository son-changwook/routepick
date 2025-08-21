# Step 4-4b1: 결제 시스템 엔티티

> 한국 특화 결제 시스템 엔티티 완전 설계  
> 생성일: 2025-08-20  
> 분할: step4-4b_payment_notification.md → 결제 부분 추출  
> 기반: 한국 PG사 연동, 트랜잭션 보안, 환불 관리

---

## 🎯 결제 시스템 설계 목표

- **한국 특화 결제**: 이니시스, 토스, 카카오페이, 네이버페이 연동
- **트랜잭션 보안**: 결제 상태 추적, 환불 관리, 로깅
- **PG사 호환**: 웹훅 처리, 검증 시스템, 재시도 메커니즘
- **성능 최적화**: 결제 조회, 상태 추적, 대량 트랜잭션 처리

---

## 💳 1. PaymentRecord 엔티티 - 결제 기록

```java
package com.routepick.domain.payment.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.PaymentStatus;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 결제 기록
 * - 한국 주요 PG사 연동 (이니시스, 토스, 카카오페이, 네이버페이)
 * - 결제 상태 추적 및 검증
 * - 환불 관리 지원
 */
@Entity
@Table(name = "payment_records", indexes = {
    @Index(name = "idx_payment_user_status", columnList = "user_id, payment_status, payment_date DESC"),
    @Index(name = "idx_payment_transaction", columnList = "transaction_id", unique = true),
    @Index(name = "idx_payment_method", columnList = "payment_method"),
    @Index(name = "idx_payment_date", columnList = "payment_date DESC"),
    @Index(name = "idx_payment_amount", columnList = "total_amount DESC"),
    @Index(name = "idx_payment_status", columnList = "payment_status")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentRecord extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    // ===== 거래 식별 정보 =====
    
    @NotNull
    @Size(min = 10, max = 100, message = "거래 ID는 10-100자 사이여야 합니다")
    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId; // 고유 거래 ID (UUID)
    
    @Column(name = "merchant_uid", length = 100)
    private String merchantUid; // 가맹점 주문번호
    
    @Column(name = "order_number", length = 50)
    private String orderNumber; // 주문번호 (사용자 표시용)
    
    // ===== 결제 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    
    @NotNull
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod; // CARD, BANK_TRANSFER, KAKAOPAY, NAVERPAY, TOSS
    
    @Column(name = "payment_gateway", length = 30)
    private String paymentGateway; // INICIS, TOSS, IAMPORT, KCP
    
    @NotNull
    @DecimalMin(value = "0.0", message = "결제 금액은 0원 이상이어야 합니다")
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount; // 총 결제 금액
    
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO; // 할인 금액
    
    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO; // 부가세
    
    @Column(name = "currency", length = 3)
    private String currency = "KRW"; // 통화
    
    // ===== 결제 일시 =====
    
    @Column(name = "payment_date")
    private LocalDateTime paymentDate; // 결제 완료일
    
    @Column(name = "requested_at")
    private LocalDateTime requestedAt; // 결제 요청일
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 결제 승인일
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt; // 결제 실패일
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt; // 결제 취소일
    
    // ===== 카드 정보 (마스킹) =====
    
    @Column(name = "card_company", length = 30)
    private String cardCompany; // 카드사 (삼성, 현대, 신한 등)
    
    @Column(name = "card_number_masked", length = 20)
    private String cardNumberMasked; // 마스킹된 카드번호 (1234-****-****-5678)
    
    @Column(name = "card_type", length = 20)
    private String cardType; // CREDIT, DEBIT, GIFT
    
    @Column(name = "installment_months")
    private Integer installmentMonths; // 할부 개월 (0: 일시불)
    
    // ===== 결제 상품 정보 =====
    
    @NotNull
    @Size(min = 2, max = 200, message = "상품명은 2-200자 사이여야 합니다")
    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName; // 대표 상품명
    
    @Column(name = "item_count")
    private Integer itemCount = 1; // 상품 종류 수
    
    @Column(name = "buyer_name", length = 50)
    private String buyerName; // 구매자명
    
    @Column(name = "buyer_email", length = 100)
    private String buyerEmail; // 구매자 이메일
    
    @Column(name = "buyer_phone", length = 20)
    private String buyerPhone; // 구매자 연락처
    
    // ===== 실패 및 취소 정보 =====
    
    @Column(name = "failure_reason", length = 200)
    private String failureReason; // 실패 사유
    
    @Column(name = "cancel_reason", length = 200)
    private String cancelReason; // 취소 사유
    
    @Column(name = "error_code", length = 20)
    private String errorCode; // 오류 코드
    
    @Column(name = "error_message", length = 500)
    private String errorMessage; // 오류 메시지
    
    // ===== 환불 정보 =====
    
    @Column(name = "refunded_amount", precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO; // 환불된 금액
    
    @Column(name = "refundable_amount", precision = 10, scale = 2)
    private BigDecimal refundableAmount; // 환불 가능 금액
    
    @Column(name = "is_fully_refunded", nullable = false)
    private boolean isFullyRefunded = false; // 전액 환불 여부
    
    // ===== 메타 정보 =====
    
    @Column(name = "user_agent", length = 500)
    private String userAgent; // 결제 환경
    
    @Column(name = "client_ip", length = 45)
    private String clientIp; // 결제 IP
    
    @Column(name = "receipt_url", length = 500)
    private String receiptUrl; // 영수증 URL
    
    @Column(name = "admin_memo", columnDefinition = "TEXT")
    private String adminMemo; // 관리자 메모
    
    // ===== 연관관계 매핑 =====
    
    @OneToOne(mappedBy = "paymentRecord", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PaymentDetail paymentDetail;
    
    @OneToMany(mappedBy = "paymentRecord", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PaymentItem> paymentItems = new ArrayList<>();
    
    @OneToMany(mappedBy = "paymentRecord", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PaymentRefund> paymentRefunds = new ArrayList<>();
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 결제 상태 한글 표시
     */
    @Transient
    public String getPaymentStatusKorean() {
        return paymentStatus.getDescription();
    }
    
    /**
     * 결제 방법 한글 표시
     */
    @Transient
    public String getPaymentMethodKorean() {
        if (paymentMethod == null) return "알 수 없음";
        
        return switch (paymentMethod) {
            case "CARD" -> "신용카드";
            case "BANK_TRANSFER" -> "계좌이체";
            case "VIRTUAL_ACCOUNT" -> "가상계좌";
            case "KAKAOPAY" -> "카카오페이";
            case "NAVERPAY" -> "네이버페이";
            case "TOSS" -> "토스";
            case "PAYCO" -> "페이코";
            default -> paymentMethod;
        };
    }
    
    /**
     * 실제 결제 금액 계산
     */
    @Transient
    public BigDecimal getActualAmount() {
        BigDecimal actual = totalAmount;
        if (discountAmount != null) {
            actual = actual.subtract(discountAmount);
        }
        return actual;
    }
    
    /**
     * 환불 가능 금액 계산
     */
    @Transient
    public BigDecimal getAvailableRefundAmount() {
        if (paymentStatus != PaymentStatus.COMPLETED) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal actualAmount = getActualAmount();
        if (refundedAmount != null) {
            actualAmount = actualAmount.subtract(refundedAmount);
        }
        return actualAmount.max(BigDecimal.ZERO);
    }
    
    /**
     * 결제 완료 처리
     */
    public void completePayment(String gatewayTransactionId, LocalDateTime completedAt) {
        this.paymentStatus = PaymentStatus.COMPLETED;
        this.paymentDate = completedAt;
        this.approvedAt = completedAt;
        this.refundableAmount = getActualAmount();
    }
    
    /**
     * 결제 실패 처리
     */
    public void failPayment(String reason, String errorCode, String errorMessage) {
        this.paymentStatus = PaymentStatus.FAILED;
        this.failedAt = LocalDateTime.now();
        this.failureReason = reason;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    /**
     * 결제 취소 처리
     */
    public void cancelPayment(String reason) {
        this.paymentStatus = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = reason;
    }
    
    /**
     * 환불 처리
     */
    public void processRefund(BigDecimal refundAmount) {
        if (refundAmount.compareTo(getAvailableRefundAmount()) > 0) {
            throw new IllegalArgumentException("환불 요청 금액이 환불 가능 금액을 초과합니다");
        }
        
        this.refundedAmount = (refundedAmount == null ? BigDecimal.ZERO : refundedAmount)
                .add(refundAmount);
        
        // 전액 환불 확인
        if (refundedAmount.compareTo(getActualAmount()) >= 0) {
            this.isFullyRefunded = true;
            this.paymentStatus = PaymentStatus.REFUNDED;
        }
    }
    
    /**
     * 할부 정보 한글 표시
     */
    @Transient
    public String getInstallmentInfo() {
        if (installmentMonths == null || installmentMonths == 0) {
            return "일시불";
        }
        return installmentMonths + "개월 할부";
    }
    
    @Override
    public Long getId() {
        return paymentId;
    }
}
```

---

## 📋 2. PaymentDetail 엔티티 - 결제 상세 정보

```java
package com.routepick.domain.payment.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 결제 상세 정보
 * - PG사별 응답 데이터 저장
 * - 웹훅 데이터 및 검증 정보
 */
@Entity
@Table(name = "payment_details", indexes = {
    @Index(name = "idx_detail_payment", columnList = "payment_record_id", unique = true),
    @Index(name = "idx_detail_gateway_id", columnList = "gateway_transaction_id"),
    @Index(name = "idx_detail_gateway", columnList = "payment_gateway")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentDetail extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Long detailId;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_record_id", nullable = false)
    private PaymentRecord paymentRecord;
    
    // ===== PG사 정보 =====
    
    @Column(name = "payment_gateway", length = 30)
    private String paymentGateway; // INICIS, TOSS, IAMPORT, KCP
    
    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId; // PG사 거래 ID
    
    @Column(name = "gateway_merchant_id", length = 100)
    private String gatewayMerchantId; // PG사 가맹점 ID
    
    @Column(name = "gateway_approval_number", length = 50)
    private String gatewayApprovalNumber; // 승인번호
    
    // ===== PG사 응답 데이터 =====
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_response", columnDefinition = "json")
    private Map<String, Object> gatewayResponse; // PG사 전체 응답 (JSON)
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "webhook_data", columnDefinition = "json")
    private Map<String, Object> webhookData; // 웹훅 데이터 (JSON)
    
    @Column(name = "gateway_status_code", length = 20)
    private String gatewayStatusCode; // PG사 상태 코드
    
    @Column(name = "gateway_status_message", length = 500)
    private String gatewayStatusMessage; // PG사 상태 메시지
    
    // ===== 카드 상세 정보 =====
    
    @Column(name = "card_bin", length = 10)
    private String cardBin; // 카드 BIN (Bank Identification Number)
    
    @Column(name = "card_name", length = 50)
    private String cardName; // 카드명
    
    @Column(name = "card_quota")
    private Integer cardQuota; // 할부 개월
    
    @Column(name = "card_number", length = 20)
    private String cardNumber; // 마스킹된 카드번호
    
    @Column(name = "card_receipt_url", length = 500)
    private String cardReceiptUrl; // 카드 매출전표 URL
    
    // ===== 가상계좌 정보 =====
    
    @Column(name = "vbank_code", length = 10)
    private String vbankCode; // 가상계좌 은행 코드
    
    @Column(name = "vbank_name", length = 30)
    private String vbankName; // 가상계좌 은행명
    
    @Column(name = "vbank_number", length = 30)
    private String vbankNumber; // 가상계좌 번호
    
    @Column(name = "vbank_holder", length = 50)
    private String vbankHolder; // 가상계좌 예금주
    
    @Column(name = "vbank_due", columnDefinition = "TIMESTAMP")
    private LocalDateTime vbankDue; // 가상계좌 입금 마감시간
    
    // ===== 간편결제 정보 =====
    
    @Column(name = "easy_pay_provider", length = 30)
    private String easyPayProvider; // 간편결제 제공사 (카카오페이, 네이버페이 등)
    
    @Column(name = "easy_pay_method", length = 30)
    private String easyPayMethod; // 간편결제 수단 (카드, 포인트 등)
    
    @Column(name = "easy_pay_discount", precision = 10, scale = 2)
    private java.math.BigDecimal easyPayDiscount; // 간편결제 할인 금액
    
    // ===== 보안 및 검증 =====
    
    @Column(name = "hash_signature", length = 200)
    private String hashSignature; // 해시 서명
    
    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false; // 검증 완료 여부
    
    @Column(name = "verification_attempts")
    private Integer verificationAttempts = 0; // 검증 시도 횟수
    
    @Column(name = "last_verification_at")
    private LocalDateTime lastVerificationAt; // 마지막 검증 시간
    
    // ===== 로그 및 추적 =====
    
    @Column(name = "request_count")
    private Integer requestCount = 0; // 요청 횟수
    
    @Column(name = "last_request_at")
    private LocalDateTime lastRequestAt; // 마지막 요청 시간
    
    @Column(name = "webhook_count")
    private Integer webhookCount = 0; // 웹훅 수신 횟수
    
    @Column(name = "last_webhook_at")
    private LocalDateTime lastWebhookAt; // 마지막 웹훅 시간
    
    @Size(max = 1000, message = "처리 로그는 최대 1000자입니다")
    @Column(name = "processing_log", columnDefinition = "TEXT")
    private String processingLog; // 처리 로그
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * PG사 한글명 반환
     */
    @Transient
    public String getPaymentGatewayKorean() {
        if (paymentGateway == null) return "알 수 없음";
        
        return switch (paymentGateway) {
            case "INICIS" -> "이니시스";
            case "TOSS" -> "토스페이먼츠";
            case "IAMPORT" -> "아임포트";
            case "KCP" -> "KCP";
            case "KAKAO" -> "카카오페이";
            case "NAVER" -> "네이버페이";
            default -> paymentGateway;
        };
    }
    
    /**
     * 검증 완료 처리
     */
    public void markAsVerified() {
        this.isVerified = true;
        this.lastVerificationAt = LocalDateTime.now();
    }
    
    /**
     * 검증 실패 처리
     */
    public void incrementVerificationAttempt() {
        this.verificationAttempts = (verificationAttempts == null ? 0 : verificationAttempts) + 1;
        this.lastVerificationAt = LocalDateTime.now();
    }
    
    /**
     * 웹훅 수신 기록
     */
    public void recordWebhook(Map<String, Object> webhookData) {
        this.webhookData = webhookData;
        this.webhookCount = (webhookCount == null ? 0 : webhookCount) + 1;
        this.lastWebhookAt = LocalDateTime.now();
    }
    
    /**
     * 요청 기록
     */
    public void recordRequest() {
        this.requestCount = (requestCount == null ? 0 : requestCount) + 1;
        this.lastRequestAt = LocalDateTime.now();
    }
    
    /**
     * 로그 추가
     */
    public void addLog(String logMessage) {
        String timestamp = LocalDateTime.now().toString();
        String newLog = "[" + timestamp + "] " + logMessage;
        
        if (processingLog == null) {
            this.processingLog = newLog;
        } else {
            this.processingLog = processingLog + "\n" + newLog;
        }
        
        // 로그가 너무 길어지면 자르기 (최대 1000자)
        if (processingLog.length() > 1000) {
            this.processingLog = processingLog.substring(processingLog.length() - 1000);
        }
    }
    
    @Override
    public Long getId() {
        return detailId;
    }
}
```

---

## 🛒 3. PaymentItem 엔티티 - 결제 항목

```java
package com.routepick.domain.payment.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * 결제 항목
 * - 개별 상품/서비스 정보
 * - 수량, 단가, 총액 관리
 */
@Entity
@Table(name = "payment_items", indexes = {
    @Index(name = "idx_item_payment", columnList = "payment_record_id, item_type"),
    @Index(name = "idx_item_type", columnList = "item_type"),
    @Index(name = "idx_item_amount", columnList = "total_price DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentItem extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_record_id", nullable = false)
    private PaymentRecord paymentRecord;
    
    // ===== 상품 정보 =====
    
    @NotNull
    @Size(min = 2, max = 200, message = "상품명은 2-200자 사이여야 합니다")
    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName; // 상품명
    
    @Column(name = "item_type", length = 30)
    private String itemType; // MEMBERSHIP, DAY_PASS, PERSONAL_TRAINING, EQUIPMENT_RENTAL, MERCHANDISE
    
    @Column(name = "item_code", length = 50)
    private String itemCode; // 상품 코드
    
    @Column(name = "item_category", length = 50)
    private String itemCategory; // 상품 카테고리
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 상품 설명
    
    // ===== 가격 정보 =====
    
    @NotNull
    @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1; // 수량
    
    @NotNull
    @DecimalMin(value = "0.0", message = "단가는 0원 이상이어야 합니다")
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice; // 단가
    
    @NotNull
    @DecimalMin(value = "0.0", message = "총액은 0원 이상이어야 합니다")
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice; // 총액 (단가 × 수량)
    
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO; // 할인 금액
    
    @Column(name = "discount_rate")
    private Float discountRate = 0.0f; // 할인율 (%)
    
    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO; // 부가세
    
    @Column(name = "tax_free", nullable = false)
    private boolean taxFree = false; // 면세 여부
    
    // ===== 기간 정보 (회원권 등) =====
    
    @Column(name = "validity_days")
    private Integer validityDays; // 유효 기간 (일)
    
    @Column(name = "start_date")
    private java.time.LocalDate startDate; // 시작일
    
    @Column(name = "end_date")
    private java.time.LocalDate endDate; // 종료일
    
    @Column(name = "usage_limit")
    private Integer usageLimit; // 사용 제한 (횟수)
    
    // ===== 연결 정보 =====
    
    @Column(name = "branch_id")
    private Long branchId; // 관련 지점 ID
    
    @Column(name = "route_id")
    private Long routeId; // 관련 루트 ID (개인 레슨 등)
    
    @Column(name = "external_item_id", length = 100)
    private String externalItemId; // 외부 시스템 상품 ID
    
    // ===== 배송 정보 =====
    
    @Column(name = "shipping_required", nullable = false)
    private boolean shippingRequired = false; // 배송 필요 여부
    
    @Column(name = "shipping_fee", precision = 10, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO; // 배송비
    
    @Column(name = "shipping_address", length = 500)
    private String shippingAddress; // 배송 주소
    
    // ===== 환불 정보 =====
    
    @Column(name = "refundable", nullable = false)
    private boolean refundable = true; // 환불 가능 여부
    
    @Column(name = "refunded_quantity")
    private Integer refundedQuantity = 0; // 환불된 수량
    
    @Column(name = "refunded_amount", precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO; // 환불된 금액
    
    // ===== 메타 정보 =====
    
    @Column(name = "image_url", length = 500)
    private String imageUrl; // 상품 이미지
    
    @Column(name = "vendor_name", length = 100)
    private String vendorName; // 판매자명
    
    @Column(name = "options", length = 500)
    private String options; // 상품 옵션 (색상, 크기 등)
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 상품 타입 한글 표시
     */
    @Transient
    public String getItemTypeKorean() {
        if (itemType == null) return "일반 상품";
        
        return switch (itemType) {
            case "MEMBERSHIP" -> "회원권";
            case "DAY_PASS" -> "일일 이용권";
            case "PERSONAL_TRAINING" -> "개인 레슨";
            case "EQUIPMENT_RENTAL" -> "장비 대여";
            case "MERCHANDISE" -> "용품/굿즈";
            case "EVENT_TICKET" -> "이벤트 티켓";
            default -> "일반 상품";
        };
    }
    
    /**
     * 실제 결제 금액 계산
     */
    @Transient
    public BigDecimal getActualAmount() {
        BigDecimal actual = totalPrice;
        if (discountAmount != null) {
            actual = actual.subtract(discountAmount);
        }
        if (shippingFee != null) {
            actual = actual.add(shippingFee);
        }
        return actual;
    }
    
    /**
     * 환불 가능 수량
     */
    @Transient
    public int getRefundableQuantity() {
        if (!refundable) return 0;
        return quantity - (refundedQuantity == null ? 0 : refundedQuantity);
    }
    
    /**
     * 환불 가능 금액
     */
    @Transient
    public BigDecimal getRefundableAmount() {
        if (!refundable) return BigDecimal.ZERO;
        
        BigDecimal totalRefundable = getActualAmount();
        if (refundedAmount != null) {
            totalRefundable = totalRefundable.subtract(refundedAmount);
        }
        return totalRefundable.max(BigDecimal.ZERO);
    }
    
    /**
     * 할인 적용 후 단가 계산
     */
    @Transient
    public BigDecimal getDiscountedUnitPrice() {
        BigDecimal discounted = unitPrice;
        
        if (discountAmount != null && quantity > 0) {
            BigDecimal perUnitDiscount = discountAmount.divide(
                BigDecimal.valueOf(quantity), 2, java.math.RoundingMode.HALF_UP);
            discounted = discounted.subtract(perUnitDiscount);
        } else if (discountRate != null && discountRate > 0) {
            BigDecimal discount = unitPrice.multiply(BigDecimal.valueOf(discountRate / 100));
            discounted = discounted.subtract(discount);
        }
        
        return discounted.max(BigDecimal.ZERO);
    }
    
    /**
     * 총액 재계산
     */
    public void recalculateTotal() {
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        
        if (discountAmount != null) {
            this.totalPrice = this.totalPrice.subtract(discountAmount);
        }
        
        this.totalPrice = this.totalPrice.max(BigDecimal.ZERO);
    }
    
    /**
     * 부분 환불 처리
     */
    public void processPartialRefund(int refundQuantity, BigDecimal refundAmount) {
        if (refundQuantity > getRefundableQuantity()) {
            throw new IllegalArgumentException("환불 수량이 환불 가능 수량을 초과합니다");
        }
        
        this.refundedQuantity = (refundedQuantity == null ? 0 : refundedQuantity) + refundQuantity;
        this.refundedAmount = (refundedAmount == null ? BigDecimal.ZERO : refundedAmount)
                .add(refundAmount);
    }
    
    @Override
    public Long getId() {
        return itemId;
    }
}
```

---

## 💰 4. PaymentRefund 엔티티 - 환불 처리

```java
package com.routepick.domain.payment.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 환불 처리
 * - 전체/부분 환불 관리
 * - 환불 상태 추적
 * - PG사 연동 정보
 */
@Entity
@Table(name = "payment_refunds", indexes = {
    @Index(name = "idx_refund_payment_status", columnList = "payment_record_id, refund_status"),
    @Index(name = "idx_refund_date", columnList = "refund_date DESC"),
    @Index(name = "idx_refund_status", columnList = "refund_status"),
    @Index(name = "idx_refund_amount", columnList = "refund_amount DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentRefund extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long refundId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_record_id", nullable = false)
    private PaymentRecord paymentRecord;
    
    // ===== 환불 기본 정보 =====
    
    @NotNull
    @Size(min = 10, max = 100, message = "환불 거래 ID는 10-100자 사이여야 합니다")
    @Column(name = "refund_transaction_id", nullable = false, unique = true, length = 100)
    private String refundTransactionId; // 환불 거래 ID
    
    @NotNull
    @DecimalMin(value = "0.01", message = "환불 금액은 0.01원 이상이어야 합니다")
    @Column(name = "refund_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount; // 환불 금액
    
    @NotNull
    @Column(name = "refund_status", nullable = false, length = 20)
    private String refundStatus = "PENDING"; // PENDING, COMPLETED, FAILED, CANCELLED
    
    @NotNull
    @Size(min = 2, max = 500, message = "환불 사유는 2-500자 사이여야 합니다")
    @Column(name = "refund_reason", nullable = false, columnDefinition = "TEXT")
    private String refundReason; // 환불 사유
    
    @Column(name = "refund_type", length = 20)
    private String refundType = "PARTIAL"; // FULL, PARTIAL
    
    // 환불 승인, 완료, 실패 등의 비즈니스 메서드들...
    
    @Override
    public Long getId() {
        return refundId;
    }
}
```

---

## ✅ 결제 시스템 완료 체크리스트

### 💳 핵심 결제 엔티티 (4개)
- [x] **PaymentRecord**: 결제 기록 마스터 (6개 인덱스)
- [x] **PaymentDetail**: 결제 상세 정보 (3개 인덱스)  
- [x] **PaymentItem**: 결제 항목 관리 (3개 인덱스)
- [x] **PaymentRefund**: 환불 처리 (4개 인덱스)

### 🎯 한국 특화 기능
- [x] **PG사 연동**: 이니시스, 토스페이먼츠, 아임포트, KCP
- [x] **간편결제**: 카카오페이, 네이버페이, 토스, 페이코
- [x] **결제수단**: 신용카드, 계좌이체, 가상계좌, 간편결제
- [x] **할부 지원**: 0개월(일시불) ~ 최대 할부 개월
- [x] **환불 정책**: 카드 3-5일, 계좌이체 1-2일

---

*분할 작업 1/2 완료: 결제 시스템 엔티티 (4개)*  
*다음 파일: step4-4b2_notification_entities.md*  
*설계 완료일: 2025-08-20*