# Step 4-4b-1: 결제 및 알림 엔티티 설계

> 결제 시스템(4개), 알림 시스템(4개) 엔티티 완전 설계  
> 생성일: 2025-08-19  
> 기반: step4-4a_community_entities.md, 한국 결제 시스템 특화

---

## 🎯 설계 목표

- **한국 특화 결제**: 이니시스, 토스, 카카오페이, 네이버페이 연동
- **트랜잭션 보안**: 결제 상태 추적, 환불 관리, 로깅
- **실시간 알림**: FCM 푸시, 인앱 알림, 배너, 팝업 시스템
- **성능 최적화**: 결제 조회, 알림 읽음 상태, 대량 알림 처리

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
    
    // ===== 환불 일시 =====
    
    @Column(name = "refund_date")
    private LocalDateTime refundDate; // 환불 완료일
    
    @Column(name = "requested_at")
    private LocalDateTime requestedAt; // 환불 요청일
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 환불 승인일
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt; // 환불 처리일
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt; // 환불 실패일
    
    // ===== 요청자 정보 =====
    
    @Column(name = "requested_by")
    private Long requestedBy; // 환불 요청자 ID
    
    @Column(name = "approved_by")
    private Long approvedBy; // 환불 승인자 ID (관리자)
    
    @Column(name = "requester_type", length = 20)
    private String requesterType = "USER"; // USER, ADMIN, SYSTEM
    
    // ===== PG사 환불 정보 =====
    
    @Column(name = "gateway_refund_id", length = 100)
    private String gatewayRefundId; // PG사 환불 ID
    
    @Column(name = "gateway_refund_status", length = 20)
    private String gatewayRefundStatus; // PG사 환불 상태
    
    @Column(name = "gateway_refund_message", length = 500)
    private String gatewayRefundMessage; // PG사 환불 메시지
    
    @Column(name = "expected_refund_date")
    private LocalDateTime expectedRefundDate; // 예상 환불일
    
    // ===== 실패 정보 =====
    
    @Column(name = "failure_reason", length = 500)
    private String failureReason; // 실패 사유
    
    @Column(name = "error_code", length = 20)
    private String errorCode; // 오류 코드
    
    @Column(name = "retry_count")
    private Integer retryCount = 0; // 재시도 횟수
    
    @Column(name = "max_retry_count")
    private Integer maxRetryCount = 3; // 최대 재시도 횟수
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt; // 다음 재시도 시간
    
    // ===== 은행 계좌 정보 (계좌 환불 시) =====
    
    @Column(name = "refund_bank_code", length = 10)
    private String refundBankCode; // 환불 은행 코드
    
    @Column(name = "refund_bank_name", length = 30)
    private String refundBankName; // 환불 은행명
    
    @Column(name = "refund_account_number", length = 30)
    private String refundAccountNumber; // 환불 계좌번호
    
    @Column(name = "refund_account_holder", length = 50)
    private String refundAccountHolder; // 환불 계좌 예금주
    
    // ===== 관리 정보 =====
    
    @Column(name = "admin_memo", columnDefinition = "TEXT")
    private String adminMemo; // 관리자 메모
    
    @Column(name = "auto_processed", nullable = false)
    private boolean autoProcessed = false; // 자동 처리 여부
    
    @Column(name = "notification_sent", nullable = false)
    private boolean notificationSent = false; // 알림 발송 여부
    
    @Column(name = "receipt_url", length = 500)
    private String receiptUrl; // 환불 영수증 URL
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 환불 상태 한글 표시
     */
    @Transient
    public String getRefundStatusKorean() {
        if (refundStatus == null) return "대기중";
        
        return switch (refundStatus) {
            case "PENDING" -> "대기중";
            case "APPROVED" -> "승인됨";
            case "PROCESSING" -> "처리중";
            case "COMPLETED" -> "완료";
            case "FAILED" -> "실패";
            case "CANCELLED" -> "취소";
            default -> refundStatus;
        };
    }
    
    /**
     * 환불 타입 한글 표시
     */
    @Transient
    public String getRefundTypeKorean() {
        if (refundType == null) return "부분 환불";
        
        return switch (refundType) {
            case "FULL" -> "전액 환불";
            case "PARTIAL" -> "부분 환불";
            default -> refundType;
        };
    }
    
    /**
     * 요청자 타입 한글 표시
     */
    @Transient
    public String getRequesterTypeKorean() {
        if (requesterType == null) return "사용자";
        
        return switch (requesterType) {
            case "USER" -> "사용자";
            case "ADMIN" -> "관리자";
            case "SYSTEM" -> "시스템";
            default -> requesterType;
        };
    }
    
    /**
     * 재시도 가능 여부 확인
     */
    @Transient
    public boolean canRetry() {
        return "FAILED".equals(refundStatus) && 
               (retryCount == null || retryCount < maxRetryCount) &&
               (nextRetryAt == null || nextRetryAt.isBefore(LocalDateTime.now()));
    }
    
    /**
     * 환불 승인
     */
    public void approve(Long adminId) {
        this.refundStatus = "APPROVED";
        this.approvedBy = adminId;
        this.approvedAt = LocalDateTime.now();
    }
    
    /**
     * 환불 처리 시작
     */
    public void startProcessing() {
        this.refundStatus = "PROCESSING";
        this.processedAt = LocalDateTime.now();
    }
    
    /**
     * 환불 완료
     */
    public void complete(String gatewayRefundId, LocalDateTime completedAt) {
        this.refundStatus = "COMPLETED";
        this.gatewayRefundId = gatewayRefundId;
        this.refundDate = completedAt;
        this.processedAt = completedAt;
    }
    
    /**
     * 환불 실패
     */
    public void fail(String reason, String errorCode) {
        this.refundStatus = "FAILED";
        this.failureReason = reason;
        this.errorCode = errorCode;
        this.failedAt = LocalDateTime.now();
        
        // 재시도 설정
        this.retryCount = (retryCount == null ? 0 : retryCount) + 1;
        if (retryCount < maxRetryCount) {
            this.nextRetryAt = LocalDateTime.now().plusHours(1); // 1시간 후 재시도
        }
    }
    
    /**
     * 환불 취소
     */
    public void cancel(String reason) {
        this.refundStatus = "CANCELLED";
        this.failureReason = reason;
        this.failedAt = LocalDateTime.now();
    }
    
    /**
     * 알림 발송 완료 표시
     */
    public void markNotificationSent() {
        this.notificationSent = true;
    }
    
    /**
     * 예상 환불일 계산
     */
    public void calculateExpectedRefundDate() {
        if (processedAt != null) {
            // 카드는 3-5일, 계좌이체는 1-2일
            String paymentMethod = paymentRecord.getPaymentMethod();
            int daysToAdd = switch (paymentMethod) {
                case "CARD" -> 5;
                case "BANK_TRANSFER", "VIRTUAL_ACCOUNT" -> 2;
                default -> 3;
            };
            
            this.expectedRefundDate = processedAt.plusDays(daysToAdd);
        }
    }
    
    @Override
    public Long getId() {
        return refundId;
    }
}
```

---

## 🔔 5. Notification 엔티티 - 개인 알림

```java
package com.routepick.domain.notification.entity;

import com.routepick.common.entity.BaseEntity;
import com.routepick.common.enums.NotificationType;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 개인 알림
 * - FCM 푸시, 인앱 알림 관리
 * - 읽음 상태 추적
 * - 다양한 알림 타입 지원
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user_read", columnList = "user_id, is_read, created_at DESC"),
    @Index(name = "idx_notification_type", columnList = "notification_type"),
    @Index(name = "idx_notification_important", columnList = "is_important, created_at DESC"),
    @Index(name = "idx_notification_sent", columnList = "is_push_sent"),
    @Index(name = "idx_notification_date", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notification extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    // ===== 알림 기본 정보 =====
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;
    
    @NotNull
    @Size(min = 1, max = 200, message = "알림 제목은 1-200자 사이여야 합니다")
    @Column(name = "title", nullable = false, length = 200)
    private String title; // 알림 제목
    
    @NotNull
    @Size(min = 1, max = 1000, message = "알림 내용은 1-1000자 사이여야 합니다")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content; // 알림 내용
    
    @Column(name = "sub_content", length = 500)
    private String subContent; // 부제목/요약
    
    @Column(name = "action_url", length = 500)
    private String actionUrl; // 클릭 시 이동할 URL
    
    @Column(name = "action_type", length = 30)
    private String actionType; // ROUTE, POST, USER, PAYMENT, EXTERNAL
    
    @Column(name = "action_data", length = 200)
    private String actionData; // 액션 관련 데이터 (ID 등)
    
    // ===== 알림 상태 =====
    
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false; // 읽음 여부
    
    @Column(name = "read_at")
    private LocalDateTime readAt; // 읽은 시간
    
    @Column(name = "is_important", nullable = false)
    private boolean isImportant = false; // 중요 알림
    
    @Column(name = "is_urgent", nullable = false)
    private boolean isUrgent = false; // 긴급 알림
    
    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false; // 시스템 알림
    
    // ===== 푸시 알림 정보 =====
    
    @Column(name = "is_push_sent", nullable = false)
    private boolean isPushSent = false; // 푸시 발송 여부
    
    @Column(name = "push_sent_at")
    private LocalDateTime pushSentAt; // 푸시 발송 시간
    
    @Column(name = "fcm_message_id", length = 200)
    private String fcmMessageId; // FCM 메시지 ID
    
    @Column(name = "push_success", nullable = false)
    private boolean pushSuccess = false; // 푸시 성공 여부
    
    @Column(name = "push_error_message", length = 500)
    private String pushErrorMessage; // 푸시 오류 메시지
    
    @Column(name = "push_retry_count")
    private Integer pushRetryCount = 0; // 푸시 재시도 횟수
    
    // ===== 발송자 정보 =====
    
    @Column(name = "sender_id")
    private Long senderId; // 발송자 ID (사용자 간 알림)
    
    @Column(name = "sender_type", length = 20)
    private String senderType = "SYSTEM"; // SYSTEM, USER, ADMIN
    
    @Column(name = "sender_name", length = 100)
    private String senderName; // 발송자명 (표시용)
    
    // ===== 이미지 및 아이콘 =====
    
    @Column(name = "icon_url", length = 500)
    private String iconUrl; // 알림 아이콘
    
    @Column(name = "image_url", length = 500)
    private String imageUrl; // 알림 이미지
    
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl; // 발송자 아바타
    
    // ===== 스케줄링 =====
    
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt; // 예약 발송 시간
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // 만료 시간
    
    @Column(name = "priority_level")
    private Integer priorityLevel = 3; // 우선순위 (1: 높음, 5: 낮음)
    
    // ===== 그룹 및 배치 =====
    
    @Column(name = "group_id", length = 100)
    private String groupId; // 그룹 알림 ID (같은 이벤트의 알림들)
    
    @Column(name = "batch_id", length = 100)
    private String batchId; // 배치 발송 ID
    
    @Column(name = "is_grouped", nullable = false)
    private boolean isGrouped = false; // 그룹 알림 여부
    
    // ===== 통계 정보 =====
    
    @Column(name = "click_count")
    private Integer clickCount = 0; // 클릭 횟수
    
    @Column(name = "first_clicked_at")
    private LocalDateTime firstClickedAt; // 첫 클릭 시간
    
    @Column(name = "last_clicked_at")
    private LocalDateTime lastClickedAt; // 마지막 클릭 시간
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 알림 타입 한글 표시
     */
    @Transient
    public String getNotificationTypeKorean() {
        return notificationType.getTitle();
    }
    
    /**
     * 발송자 타입 한글 표시
     */
    @Transient
    public String getSenderTypeKorean() {
        if (senderType == null) return "시스템";
        
        return switch (senderType) {
            case "SYSTEM" -> "시스템";
            case "USER" -> "사용자";
            case "ADMIN" -> "관리자";
            default -> senderType;
        };
    }
    
    /**
     * 우선순위 한글 표시
     */
    @Transient
    public String getPriorityLevelKorean() {
        if (priorityLevel == null) return "보통";
        
        return switch (priorityLevel) {
            case 1 -> "매우 높음";
            case 2 -> "높음";
            case 3 -> "보통";
            case 4 -> "낮음";
            case 5 -> "매우 낮음";
            default -> "보통";
        };
    }
    
    /**
     * 읽음 처리
     */
    public void markAsRead() {
        if (!isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }
    
    /**
     * 읽지 않음으로 표시
     */
    public void markAsUnread() {
        this.isRead = false;
        this.readAt = null;
    }
    
    /**
     * 클릭 기록
     */
    public void recordClick() {
        this.clickCount = (clickCount == null ? 0 : clickCount) + 1;
        this.lastClickedAt = LocalDateTime.now();
        
        if (firstClickedAt == null) {
            this.firstClickedAt = LocalDateTime.now();
        }
        
        // 클릭 시 자동으로 읽음 처리
        markAsRead();
    }
    
    /**
     * 푸시 발송 성공 처리
     */
    public void markPushSent(String fcmMessageId) {
        this.isPushSent = true;
        this.pushSuccess = true;
        this.pushSentAt = LocalDateTime.now();
        this.fcmMessageId = fcmMessageId;
    }
    
    /**
     * 푸시 발송 실패 처리
     */
    public void markPushFailed(String errorMessage) {
        this.isPushSent = true;
        this.pushSuccess = false;
        this.pushSentAt = LocalDateTime.now();
        this.pushErrorMessage = errorMessage;
        this.pushRetryCount = (pushRetryCount == null ? 0 : pushRetryCount) + 1;
    }
    
    /**
     * 만료 여부 확인
     */
    @Transient
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * 예약 알림 여부 확인
     */
    @Transient
    public boolean isScheduled() {
        return scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now());
    }
    
    /**
     * 발송 가능 여부 확인
     */
    @Transient
    public boolean canSend() {
        return !isExpired() && !isScheduled() && !isPushSent;
    }
    
    @Override
    public Long getId() {
        return notificationId;
    }
}
```

---

## 📢 6. Notice 엔티티 - 공지사항

```java
package com.routepick.domain.notification.entity;

import com.routepick.common.entity.SoftDeleteEntity;
import com.routepick.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 공지사항
 * - 전체 사용자 대상 공지
 * - 중요도별 분류
 * - 게시 기간 관리
 */
@Entity
@Table(name = "notices", indexes = {
    @Index(name = "idx_notice_date", columnList = "is_important, start_date DESC"),
    @Index(name = "idx_notice_active", columnList = "is_active, end_date DESC"),
    @Index(name = "idx_notice_type", columnList = "notice_type"),
    @Index(name = "idx_notice_author", columnList = "author_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notice extends SoftDeleteEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author; // 작성자 (관리자)
    
    // ===== 공지 기본 정보 =====
    
    @NotNull
    @Size(min = 2, max = 200, message = "공지 제목은 2-200자 사이여야 합니다")
    @Column(name = "title", nullable = false, length = 200)
    private String title; // 공지 제목
    
    @NotNull
    @Size(min = 10, message = "공지 내용은 최소 10자 이상이어야 합니다")
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content; // 공지 내용
    
    @Column(name = "summary", length = 500)
    private String summary; // 요약
    
    @Column(name = "notice_type", length = 30)
    private String noticeType = "GENERAL"; // GENERAL, MAINTENANCE, EVENT, UPDATE, URGENT
    
    // ===== 중요도 및 표시 =====
    
    @Column(name = "is_important", nullable = false)
    private boolean isImportant = false; // 중요 공지
    
    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false; // 상단 고정
    
    @Column(name = "is_popup", nullable = false)
    private boolean isPopup = false; // 팝업 표시
    
    @Column(name = "is_push", nullable = false)
    private boolean isPush = false; // 푸시 알림 발송
    
    @Column(name = "importance_level")
    private Integer importanceLevel = 3; // 중요도 (1: 매우 높음, 5: 낮음)
    
    // ===== 게시 기간 =====
    
    @Column(name = "start_date")
    private LocalDateTime startDate; // 게시 시작일
    
    @Column(name = "end_date")
    private LocalDateTime endDate; // 게시 종료일
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt; // 게시일
    
    // ===== 대상 설정 =====
    
    @Column(name = "target_user_type", length = 20)
    private String targetUserType = "ALL"; // ALL, NORMAL, ADMIN, GYM_ADMIN
    
    @Column(name = "target_app_version", length = 20)
    private String targetAppVersion; // 특정 앱 버전 대상
    
    @Column(name = "target_platform", length = 20)
    private String targetPlatform; // ANDROID, IOS, WEB, ALL
    
    @Column(name = "target_region", length = 100)
    private String targetRegion; // 특정 지역 대상
    
    // ===== 이미지 및 첨부 파일 =====
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl; // 썸네일 이미지
    
    @Column(name = "banner_image_url", length = 500)
    private String bannerImageUrl; // 배너 이미지
    
    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl; // 첨부 파일
    
    @Column(name = "attachment_name", length = 200)
    private String attachmentName; // 첨부 파일명
    
    // ===== 링크 정보 =====
    
    @Column(name = "link_url", length = 500)
    private String linkUrl; // 관련 링크
    
    @Column(name = "link_text", length = 100)
    private String linkText; // 링크 텍스트
    
    @Column(name = "external_link", nullable = false)
    private boolean externalLink = false; // 외부 링크 여부
    
    // ===== 통계 정보 =====
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 조회 수
    
    @Column(name = "click_count")
    private Integer clickCount = 0; // 클릭 수
    
    @Column(name = "push_sent_count")
    private Integer pushSentCount = 0; // 푸시 발송 수
    
    @Column(name = "push_success_count")
    private Integer pushSuccessCount = 0; // 푸시 성공 수
    
    // ===== 관리 정보 =====
    
    @Column(name = "approved_by")
    private Long approvedBy; // 승인자 ID
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 승인일
    
    @Column(name = "admin_memo", columnDefinition = "TEXT")
    private String adminMemo; // 관리자 메모
    
    @Column(name = "tags", length = 500)
    private String tags; // 태그 (쉼표 구분)
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 공지 타입 한글 표시
     */
    @Transient
    public String getNoticeTypeKorean() {
        if (noticeType == null) return "일반";
        
        return switch (noticeType) {
            case "GENERAL" -> "일반";
            case "MAINTENANCE" -> "점검";
            case "EVENT" -> "이벤트";
            case "UPDATE" -> "업데이트";
            case "URGENT" -> "긴급";
            default -> noticeType;
        };
    }
    
    /**
     * 중요도 한글 표시
     */
    @Transient
    public String getImportanceLevelKorean() {
        if (importanceLevel == null) return "보통";
        
        return switch (importanceLevel) {
            case 1 -> "매우 높음";
            case 2 -> "높음";
            case 3 -> "보통";
            case 4 -> "낮음";
            case 5 -> "매우 낮음";
            default -> "보통";
        };
    }
    
    /**
     * 대상 사용자 타입 한글 표시
     */
    @Transient
    public String getTargetUserTypeKorean() {
        if (targetUserType == null) return "전체";
        
        return switch (targetUserType) {
            case "ALL" -> "전체 사용자";
            case "NORMAL" -> "일반 사용자";
            case "ADMIN" -> "관리자";
            case "GYM_ADMIN" -> "암장 관리자";
            default -> targetUserType;
        };
    }
    
    /**
     * 현재 게시 중인지 확인
     */
    @Transient
    public boolean isCurrentlyActive() {
        if (!isActive) return false;
        
        LocalDateTime now = LocalDateTime.now();
        
        boolean afterStart = startDate == null || !startDate.isAfter(now);
        boolean beforeEnd = endDate == null || !endDate.isBefore(now);
        
        return afterStart && beforeEnd;
    }
    
    /**
     * 조회수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
    }
    
    /**
     * 클릭 수 증가
     */
    public void increaseClickCount() {
        this.clickCount = (clickCount == null ? 0 : clickCount) + 1;
    }
    
    /**
     * 게시 승인
     */
    public void approve(Long adminId) {
        this.approvedBy = adminId;
        this.approvedAt = LocalDateTime.now();
        this.isActive = true;
        
        if (publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 게시 중단
     */
    public void deactivate() {
        this.isActive = false;
        this.endDate = LocalDateTime.now();
    }
    
    /**
     * 고정/해제
     */
    public void pin() {
        this.isPinned = true;
    }
    
    public void unpin() {
        this.isPinned = false;
    }
    
    /**
     * 푸시 발송 통계 업데이트
     */
    public void updatePushStats(int sentCount, int successCount) {
        this.pushSentCount = sentCount;
        this.pushSuccessCount = successCount;
    }
    
    /**
     * 태그 목록 반환
     */
    @Transient
    public java.util.List<String> getTagList() {
        if (tags == null || tags.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return java.util.Arrays.asList(tags.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public Long getId() {
        return noticeId;
    }
}
```

---

## 🎨 7. Banner 엔티티 - 배너 관리

```java
package com.routepick.domain.notification.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 배너 관리
 * - 앱 메인 화면 배너
 * - 표시 순서 및 기간 관리
 * - 클릭 통계 추적
 */
@Entity
@Table(name = "banners", indexes = {
    @Index(name = "idx_banner_active_order", columnList = "is_active, display_order, start_date DESC"),
    @Index(name = "idx_banner_position", columnList = "banner_position"),
    @Index(name = "idx_banner_date", columnList = "start_date DESC, end_date DESC"),
    @Index(name = "idx_banner_type", columnList = "banner_type")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Banner extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "banner_id")
    private Long bannerId;
    
    // ===== 배너 기본 정보 =====
    
    @NotNull
    @Size(min = 2, max = 200, message = "배너 제목은 2-200자 사이여야 합니다")
    @Column(name = "banner_title", nullable = false, length = 200)
    private String bannerTitle; // 배너 제목
    
    @Column(name = "banner_subtitle", length = 300)
    private String bannerSubtitle; // 부제목
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 배너 설명
    
    @Column(name = "banner_type", length = 30)
    private String bannerType = "PROMOTION"; // PROMOTION, EVENT, NOTICE, AD, FEATURE
    
    // ===== 이미지 정보 =====
    
    @NotNull
    @Size(min = 10, max = 500, message = "배너 이미지 URL은 10-500자 사이여야 합니다")
    @Column(name = "banner_image_url", nullable = false, length = 500)
    private String bannerImageUrl; // 배너 이미지
    
    @Column(name = "mobile_image_url", length = 500)
    private String mobileImageUrl; // 모바일용 이미지
    
    @Column(name = "tablet_image_url", length = 500)
    private String tabletImageUrl; // 태블릿용 이미지
    
    @Column(name = "background_color", length = 7)
    private String backgroundColor; // 배경색 (#FFFFFF)
    
    @Column(name = "text_color", length = 7)
    private String textColor; // 텍스트 색상
    
    // ===== 링크 정보 =====
    
    @Column(name = "link_url", length = 500)
    private String linkUrl; // 클릭 시 이동할 URL
    
    @Column(name = "link_type", length = 30)
    private String linkType = "INTERNAL"; // INTERNAL, EXTERNAL, DEEPLINK
    
    @Column(name = "link_target", length = 30)
    private String linkTarget = "_self"; // _self, _blank, _parent
    
    @Column(name = "action_type", length = 30)
    private String actionType; // ROUTE, POST, USER, PRODUCT, EXTERNAL
    
    @Column(name = "action_data", length = 200)
    private String actionData; // 액션 관련 데이터
    
    // ===== 표시 설정 =====
    
    @NotNull
    @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다")
    @Max(value = 999, message = "표시 순서는 999 이하여야 합니다")
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1; // 표시 순서
    
    @Column(name = "banner_position", length = 30)
    private String bannerPosition = "MAIN_TOP"; // MAIN_TOP, MAIN_MIDDLE, MAIN_BOTTOM, DETAIL_TOP
    
    @Column(name = "banner_size", length = 20)
    private String bannerSize = "LARGE"; // SMALL, MEDIUM, LARGE, FULL
    
    @Column(name = "auto_play", nullable = false)
    private boolean autoPlay = true; // 자동 재생 (슬라이드)
    
    @Column(name = "play_duration")
    private Integer playDuration = 5; // 재생 시간 (초)
    
    // ===== 게시 기간 =====
    
    @Column(name = "start_date")
    private LocalDateTime startDate; // 게시 시작일
    
    @Column(name = "end_date")
    private LocalDateTime endDate; // 게시 종료일
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "is_always_show", nullable = false)
    private boolean isAlwaysShow = false; // 항상 표시
    
    // ===== 대상 설정 =====
    
    @Column(name = "target_user_type", length = 20)
    private String targetUserType = "ALL"; // ALL, NORMAL, ADMIN, GYM_ADMIN
    
    @Column(name = "target_platform", length = 20)
    private String targetPlatform = "ALL"; // ANDROID, IOS, WEB, ALL
    
    @Column(name = "target_app_version", length = 20)
    private String targetAppVersion; // 특정 앱 버전
    
    @Column(name = "target_region", length = 100)
    private String targetRegion; // 특정 지역
    
    // ===== 통계 정보 =====
    
    @Column(name = "view_count")
    private Long viewCount = 0L; // 노출 수
    
    @Column(name = "click_count")
    private Integer clickCount = 0; // 클릭 수
    
    @Column(name = "ctr")
    private Float ctr = 0.0f; // 클릭률 (Click Through Rate)
    
    @Column(name = "conversion_count")
    private Integer conversionCount = 0; // 전환 수
    
    @Column(name = "first_shown_at")
    private LocalDateTime firstShownAt; // 첫 노출일
    
    @Column(name = "last_clicked_at")
    private LocalDateTime lastClickedAt; // 마지막 클릭일
    
    // ===== 관리 정보 =====
    
    @Column(name = "created_by")
    private Long createdBy; // 생성자 ID
    
    @Column(name = "approved_by")
    private Long approvedBy; // 승인자 ID
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 승인일
    
    @Column(name = "admin_memo", columnDefinition = "TEXT")
    private String adminMemo; // 관리자 메모
    
    @Column(name = "priority_score")
    private Integer priorityScore = 0; // 우선순위 점수
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 배너 타입 한글 표시
     */
    @Transient
    public String getBannerTypeKorean() {
        if (bannerType == null) return "일반";
        
        return switch (bannerType) {
            case "PROMOTION" -> "프로모션";
            case "EVENT" -> "이벤트";
            case "NOTICE" -> "공지";
            case "AD" -> "광고";
            case "FEATURE" -> "기능 소개";
            default -> bannerType;
        };
    }
    
    /**
     * 배너 위치 한글 표시
     */
    @Transient
    public String getBannerPositionKorean() {
        if (bannerPosition == null) return "메인 상단";
        
        return switch (bannerPosition) {
            case "MAIN_TOP" -> "메인 상단";
            case "MAIN_MIDDLE" -> "메인 중간";
            case "MAIN_BOTTOM" -> "메인 하단";
            case "DETAIL_TOP" -> "상세 페이지 상단";
            case "CATEGORY_TOP" -> "카테고리 상단";
            default -> bannerPosition;
        };
    }
    
    /**
     * 현재 표시 중인지 확인
     */
    @Transient
    public boolean isCurrentlyVisible() {
        if (!isActive) return false;
        if (isAlwaysShow) return true;
        
        LocalDateTime now = LocalDateTime.now();
        
        boolean afterStart = startDate == null || !startDate.isAfter(now);
        boolean beforeEnd = endDate == null || !endDate.isBefore(now);
        
        return afterStart && beforeEnd;
    }
    
    /**
     * 노출 수 증가
     */
    public void increaseViewCount() {
        this.viewCount = (viewCount == null ? 0L : viewCount) + 1;
        
        if (firstShownAt == null) {
            this.firstShownAt = LocalDateTime.now();
        }
        
        updateCtr();
    }
    
    /**
     * 클릭 수 증가
     */
    public void increaseClickCount() {
        this.clickCount = (clickCount == null ? 0 : clickCount) + 1;
        this.lastClickedAt = LocalDateTime.now();
        
        updateCtr();
    }
    
    /**
     * CTR 업데이트
     */
    private void updateCtr() {
        if (viewCount != null && viewCount > 0) {
            this.ctr = ((float) (clickCount == null ? 0 : clickCount) / viewCount) * 100;
        }
    }
    
    /**
     * 전환 수 증가
     */
    public void increaseConversionCount() {
        this.conversionCount = (conversionCount == null ? 0 : conversionCount) + 1;
    }
    
    /**
     * 배너 승인
     */
    public void approve(Long adminId) {
        this.approvedBy = adminId;
        this.approvedAt = LocalDateTime.now();
        this.isActive = true;
    }
    
    /**
     * 배너 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        this.endDate = LocalDateTime.now();
    }
    
    /**
     * 우선순위 점수 계산
     */
    public void calculatePriorityScore() {
        int score = 0;
        
        // 표시 순서 기반 점수 (낮을수록 높은 점수)
        score += Math.max(0, 100 - displayOrder);
        
        // CTR 기반 점수
        if (ctr != null) {
            score += (int) (ctr * 10);
        }
        
        // 최신성 점수 (최근 생성일수록 높은 점수)
        if (getCreatedAt() != null) {
            long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(
                getCreatedAt().toLocalDate(), LocalDateTime.now().toLocalDate());
            score += Math.max(0, 30 - (int) daysAgo);
        }
        
        this.priorityScore = score;
    }
    
    @Override
    public Long getId() {
        return bannerId;
    }
}
```

---

## 📱 8. AppPopup 엔티티 - 앱 팝업

```java
package com.routepick.domain.notification.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 앱 팝업
 * - 앱 실행 시 표시되는 팝업
 * - 공지, 이벤트, 업데이트 안내 등
 * - 표시 조건 및 통계 관리
 */
@Entity
@Table(name = "app_popups", indexes = {
    @Index(name = "idx_popup_active_date", columnList = "is_active, start_date DESC, end_date DESC"),
    @Index(name = "idx_popup_priority", columnList = "priority_level, created_at DESC"),
    @Index(name = "idx_popup_type", columnList = "popup_type"),
    @Index(name = "idx_popup_trigger", columnList = "trigger_type")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AppPopup extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popup_id")
    private Long popupId;
    
    // ===== 팝업 기본 정보 =====
    
    @NotNull
    @Size(min = 2, max = 200, message = "팝업 제목은 2-200자 사이여야 합니다")
    @Column(name = "popup_title", nullable = false, length = 200)
    private String popupTitle; // 팝업 제목
    
    @NotNull
    @Size(min = 1, max = 1000, message = "팝업 내용은 1-1000자 사이여야 합니다")
    @Column(name = "popup_content", nullable = false, columnDefinition = "TEXT")
    private String popupContent; // 팝업 내용
    
    @Column(name = "popup_type", length = 30)
    private String popupType = "NOTICE"; // NOTICE, EVENT, UPDATE, AD, SURVEY, WELCOME
    
    @Column(name = "popup_style", length = 30)
    private String popupStyle = "MODAL"; // MODAL, FULLSCREEN, BANNER, ALERT
    
    // ===== 이미지 및 디자인 =====
    
    @Column(name = "popup_image_url", length = 500)
    private String popupImageUrl; // 팝업 이미지
    
    @Column(name = "background_image_url", length = 500)
    private String backgroundImageUrl; // 배경 이미지
    
    @Column(name = "icon_url", length = 500)
    private String iconUrl; // 아이콘
    
    @Column(name = "background_color", length = 7)
    private String backgroundColor = "#FFFFFF"; // 배경색
    
    @Column(name = "text_color", length = 7)
    private String textColor = "#000000"; // 텍스트 색상
    
    @Column(name = "button_color", length = 7)
    private String buttonColor = "#007AFF"; // 버튼 색상
    
    // ===== 버튼 설정 =====
    
    @Column(name = "primary_button_text", length = 50)
    private String primaryButtonText = "확인"; // 주 버튼 텍스트
    
    @Column(name = "primary_button_action", length = 200)
    private String primaryButtonAction; // 주 버튼 액션 (URL, 딥링크 등)
    
    @Column(name = "secondary_button_text", length = 50)
    private String secondaryButtonText; // 보조 버튼 텍스트
    
    @Column(name = "secondary_button_action", length = 200)
    private String secondaryButtonAction; // 보조 버튼 액션
    
    @Column(name = "close_button_visible", nullable = false)
    private boolean closeButtonVisible = true; // 닫기 버튼 표시
    
    @Column(name = "auto_close_seconds")
    private Integer autoCloseSeconds; // 자동 닫기 시간 (초)
    
    // ===== 표시 조건 =====
    
    @Column(name = "trigger_type", length = 30)
    private String triggerType = "APP_LAUNCH"; // APP_LAUNCH, LOGIN, FIRST_TIME, INTERVAL
    
    @Column(name = "show_frequency", length = 30)
    private String showFrequency = "ONCE"; // ONCE, DAILY, WEEKLY, ALWAYS
    
    @Column(name = "min_app_version", length = 20)
    private String minAppVersion; // 최소 앱 버전
    
    @Column(name = "max_app_version", length = 20)
    private String maxAppVersion; // 최대 앱 버전
    
    @Column(name = "delay_seconds")
    private Integer delaySeconds = 0; // 표시 지연 시간 (초)
    
    // ===== 우선순위 및 순서 =====
    
    @NotNull
    @Min(value = 1, message = "우선순위는 1 이상이어야 합니다")
    @Max(value = 10, message = "우선순위는 10 이하여야 합니다")
    @Column(name = "priority_level", nullable = false)
    private Integer priorityLevel = 5; // 우선순위 (1: 높음, 10: 낮음)
    
    @Column(name = "display_order")
    private Integer displayOrder = 1; // 같은 우선순위 내 표시 순서
    
    @Column(name = "max_daily_shows")
    private Integer maxDailyShows = 1; // 일일 최대 표시 횟수
    
    @Column(name = "cooldown_hours")
    private Integer cooldownHours = 24; // 재표시 간격 (시간)
    
    // ===== 게시 기간 =====
    
    @Column(name = "start_date")
    private LocalDateTime startDate; // 표시 시작일
    
    @Column(name = "end_date")
    private LocalDateTime endDate; // 표시 종료일
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "is_test_mode", nullable = false)
    private boolean isTestMode = false; // 테스트 모드
    
    // ===== 대상 설정 =====
    
    @Column(name = "target_user_type", length = 20)
    private String targetUserType = "ALL"; // ALL, NEW, RETURNING, VIP
    
    @Column(name = "target_platform", length = 20)
    private String targetPlatform = "ALL"; // ANDROID, IOS, WEB, ALL
    
    @Column(name = "target_region", length = 100)
    private String targetRegion; // 특정 지역
    
    @Column(name = "target_user_count_min")
    private Integer targetUserCountMin; // 최소 사용 횟수
    
    @Column(name = "target_user_count_max")
    private Integer targetUserCountMax; // 최대 사용 횟수
    
    // ===== 통계 정보 =====
    
    @Column(name = "total_shows")
    private Long totalShows = 0L; // 총 표시 횟수
    
    @Column(name = "today_shows")
    private Integer todayShows = 0; // 오늘 표시 횟수
    
    @Column(name = "unique_users_shown")
    private Integer uniqueUsersShown = 0; // 고유 사용자 표시 수
    
    @Column(name = "primary_button_clicks")
    private Integer primaryButtonClicks = 0; // 주 버튼 클릭 수
    
    @Column(name = "secondary_button_clicks")
    private Integer secondaryButtonClicks = 0; // 보조 버튼 클릭 수
    
    @Column(name = "close_button_clicks")
    private Integer closeButtonClicks = 0; // 닫기 버튼 클릭 수
    
    @Column(name = "conversion_count")
    private Integer conversionCount = 0; // 전환 수
    
    @Column(name = "avg_display_duration")
    private Float avgDisplayDuration = 0.0f; // 평균 표시 시간 (초)
    
    // ===== 관리 정보 =====
    
    @Column(name = "created_by")
    private Long createdBy; // 생성자 ID
    
    @Column(name = "approved_by")
    private Long approvedBy; // 승인자 ID
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 승인일
    
    @Column(name = "admin_memo", columnDefinition = "TEXT")
    private String adminMemo; // 관리자 메모
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 팝업 타입 한글 표시
     */
    @Transient
    public String getPopupTypeKorean() {
        if (popupType == null) return "공지";
        
        return switch (popupType) {
            case "NOTICE" -> "공지";
            case "EVENT" -> "이벤트";
            case "UPDATE" -> "업데이트";
            case "AD" -> "광고";
            case "SURVEY" -> "설문";
            case "WELCOME" -> "환영";
            default -> popupType;
        };
    }
    
    /**
     * 트리거 타입 한글 표시
     */
    @Transient
    public String getTriggerTypeKorean() {
        if (triggerType == null) return "앱 실행";
        
        return switch (triggerType) {
            case "APP_LAUNCH" -> "앱 실행";
            case "LOGIN" -> "로그인";
            case "FIRST_TIME" -> "첫 실행";
            case "INTERVAL" -> "주기적";
            case "SPECIFIC_PAGE" -> "특정 페이지";
            default -> triggerType;
        };
    }
    
    /**
     * 표시 빈도 한글 표시
     */
    @Transient
    public String getShowFrequencyKorean() {
        if (showFrequency == null) return "한 번만";
        
        return switch (showFrequency) {
            case "ONCE" -> "한 번만";
            case "DAILY" -> "매일";
            case "WEEKLY" -> "매주";
            case "ALWAYS" -> "항상";
            default -> showFrequency;
        };
    }
    
    /**
     * 현재 표시 가능한지 확인
     */
    @Transient
    public boolean isCurrentlyDisplayable() {
        if (!isActive || isTestMode) return false;
        
        LocalDateTime now = LocalDateTime.now();
        
        boolean afterStart = startDate == null || !startDate.isAfter(now);
        boolean beforeEnd = endDate == null || !endDate.isBefore(now);
        
        return afterStart && beforeEnd;
    }
    
    /**
     * 일일 표시 제한 확인
     */
    @Transient
    public boolean canShowToday() {
        return maxDailyShows == null || todayShows < maxDailyShows;
    }
    
    /**
     * 표시 횟수 증가
     */
    public void incrementShowCount() {
        this.totalShows = (totalShows == null ? 0L : totalShows) + 1;
        this.todayShows = (todayShows == null ? 0 : todayShows) + 1;
    }
    
    /**
     * 고유 사용자 수 증가
     */
    public void incrementUniqueUserCount() {
        this.uniqueUsersShown = (uniqueUsersShown == null ? 0 : uniqueUsersShown) + 1;
    }
    
    /**
     * 주 버튼 클릭
     */
    public void clickPrimaryButton() {
        this.primaryButtonClicks = (primaryButtonClicks == null ? 0 : primaryButtonClicks) + 1;
    }
    
    /**
     * 보조 버튼 클릭
     */
    public void clickSecondaryButton() {
        this.secondaryButtonClicks = (secondaryButtonClicks == null ? 0 : secondaryButtonClicks) + 1;
    }
    
    /**
     * 닫기 버튼 클릭
     */
    public void clickCloseButton() {
        this.closeButtonClicks = (closeButtonClicks == null ? 0 : closeButtonClicks) + 1;
    }
    
    /**
     * 전환율 계산
     */
    @Transient
    public float getConversionRate() {
        if (totalShows == null || totalShows == 0) return 0.0f;
        return ((float) (conversionCount == null ? 0 : conversionCount) / totalShows) * 100;
    }
    
    /**
     * 클릭률 계산
     */
    @Transient
    public float getClickThroughRate() {
        if (totalShows == null || totalShows == 0) return 0.0f;
        
        int totalClicks = (primaryButtonClicks == null ? 0 : primaryButtonClicks) +
                         (secondaryButtonClicks == null ? 0 : secondaryButtonClicks);
        
        return ((float) totalClicks / totalShows) * 100;
    }
    
    /**
     * 팝업 승인
     */
    public void approve(Long adminId) {
        this.approvedBy = adminId;
        this.approvedAt = LocalDateTime.now();
        this.isActive = true;
    }
    
    /**
     * 오늘 표시 횟수 리셋
     */
    public void resetTodayShows() {
        this.todayShows = 0;
    }
    
    @Override
    public Long getId() {
        return popupId;
    }
}
```

---

## ⚡ 9. 성능 최적화 전략

### 복합 인덱스 DDL 추가
```sql
-- 결제 트랜잭션 조회 최적화
CREATE INDEX idx_payment_user_method_status 
ON payment_records(user_id, payment_method, payment_status, payment_date DESC);

-- 환불 상태별 조회
CREATE INDEX idx_refund_status_date 
ON payment_refunds(refund_status, requested_at DESC, refund_amount DESC);

-- 알림 읽지 않은 것 우선 조회
CREATE INDEX idx_notification_unread_priority 
ON notifications(user_id, is_read, priority_level, created_at DESC);

-- 활성 공지사항 조회
CREATE INDEX idx_notice_active_importance 
ON notices(is_active, is_important, start_date DESC, end_date DESC);

-- 현재 표시 가능한 배너 조회
CREATE INDEX idx_banner_displayable 
ON banners(is_active, start_date, end_date, display_order);

-- 팝업 우선순위별 조회
CREATE INDEX idx_popup_priority_active 
ON app_popups(is_active, priority_level, start_date DESC);
```

### 알림 대량 처리 쿼리 예시
```java
// Repository에서 배치 알림 처리
@Query("SELECT n FROM Notification n " +
       "WHERE n.user.id = :userId " +
       "AND n.isRead = false " +
       "ORDER BY n.priorityLevel ASC, n.createdAt DESC")
List<Notification> findUnreadNotificationsByUser(@Param("userId") Long userId);

@Modifying
@Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt " +
       "WHERE n.user.id = :userId AND n.isRead = false")
int markAllAsReadByUser(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
```

---

## ✅ 설계 완료 체크리스트

### 결제 시스템 엔티티 (4개)
- [x] **PaymentRecord** - 결제 기록 (한국 PG사 연동, 상태 추적)
- [x] **PaymentDetail** - 결제 상세 (PG사 응답, 웹훅 데이터)
- [x] **PaymentItem** - 결제 항목 (상품별 세부 정보)
- [x] **PaymentRefund** - 환불 처리 (전체/부분 환불, 재시도)

### 알림 시스템 엔티티 (4개)
- [x] **Notification** - 개인 알림 (FCM 푸시, 읽음 상태)
- [x] **Notice** - 공지사항 (전체 대상, 중요도별 분류)
- [x] **Banner** - 배너 관리 (표시 위치, 클릭 통계)
- [x] **AppPopup** - 앱 팝업 (트리거 조건, 우선순위)

### 한국 특화 기능
- [x] 주요 PG사 연동 (이니시스, 토스, 카카오페이, 네이버페이)
- [x] 가상계좌, 카드 할부, 간편결제 지원
- [x] 환불 프로세스 자동화
- [x] FCM 푸시 알림 완전 지원

### 성능 최적화
- [x] 결제 상태별 조회 인덱스
- [x] 읽지 않은 알림 우선 조회
- [x] 배너/팝업 활성 상태 조회
- [x] 대량 알림 배치 처리

### 비즈니스 로직
- [x] 결제 상태 전환 관리
- [x] 환불 가능 금액 계산
- [x] 알림 우선순위 처리
- [x] 팝업 표시 조건 검증

---

**다음 단계**: Step 4-4b-2 시스템 관리 엔티티 설계  
**완료일**: 2025-08-19  
**핵심 성과**: 8개 결제/알림 엔티티 + 한국 특화 + 실시간 알림 완성