# Step 7-5b: Payment Controller 구현

> 결제, 환불, 웹훅 처리 Controller - 한국 PG사 연동, 결제 보안, 웹훅 검증
> 생성일: 2025-08-25
> 단계: 7-5b (Controller 레이어 - 결제 시스템)
> 참고: step6-5a, step6-5b, step6-5c, step4-4b1, step5-4d

---

## 🎯 설계 목표

- **결제 처리**: 토스, 카카오페이, 네이버페이 연동
- **환불 관리**: 자동환불, 부분환불, 승인 워크플로우
- **웹훅 처리**: PG사 콜백, 서명 검증, 재시도 로직
- **결제 보안**: PCI DSS 준수, 민감정보 암호화
- **거래 무결성**: SERIALIZABLE 트랜잭션 처리

---

## 💳 PaymentController 구현

### PaymentController.java
```java
package com.routepick.controller.api.v1.payment;

import com.routepick.common.annotation.RateLimited;
import com.routepick.common.annotation.SecureTransaction;
import com.routepick.common.dto.ApiResponse;
import com.routepick.common.enums.PaymentMethod;
import com.routepick.common.enums.PaymentStatus;
import com.routepick.service.payment.PaymentService;
import com.routepick.service.payment.PaymentRefundService;
import com.routepick.service.payment.WebhookService;
import com.routepick.dto.payment.request.*;
import com.routepick.dto.payment.response.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 결제 처리 Controller
 * - 결제 요청/취소/환불 처리
 * - 결제 내역 조회
 * - PG사 웹훅 처리
 * - 한국 PG사 연동 (토스, 카카오페이, 네이버페이)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
@Tag(name = "Payment", description = "결제 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRefundService paymentRefundService;
    private final WebhookService webhookService;

    /**
     * 결제 요청 처리
     * POST /api/v1/payments/process
     */
    @PostMapping("/process")
    @Operation(summary = "결제 처리", description = "새로운 결제를 요청합니다.")
    @PreAuthorize("isAuthenticated()")
    @SecureTransaction
    @RateLimits({
        @RateLimited(requests = 5, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 20, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP)
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentProcessRequest request,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("결제 처리 요청: userId={}, amount={}, method={}", 
                    userId, request.getAmount(), request.getPaymentMethod());
            
            // 결제 요청 검증 및 처리
            PaymentResponse response = paymentService.processPayment(userId, request);
            
            log.info("결제 처리 완료: paymentId={}, status={}", 
                    response.getPaymentId(), response.getStatus());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("결제 처리 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 결제 환불 요청
     * POST /api/v1/payments/refund
     */
    @PostMapping("/refund")
    @Operation(summary = "결제 환불", description = "결제 환불을 요청합니다.")
    @PreAuthorize("isAuthenticated()")
    @SecureTransaction
    @RateLimits({
        @RateLimited(requests = 3, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 10, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP)
    })
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @Valid @RequestBody RefundRequest request,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("환불 요청: userId={}, paymentId={}, amount={}", 
                    userId, request.getPaymentId(), request.getRefundAmount());
            
            // 환불 권한 검증
            paymentService.validateRefundPermission(userId, request.getPaymentId());
            
            // 환불 처리
            RefundResponse response = paymentRefundService.processRefund(userId, request);
            
            log.info("환불 처리 완료: refundId={}, status={}", 
                    response.getRefundId(), response.getStatus());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("환불 처리 실패: userId={}, paymentId={}, error={}", 
                    userId, request.getPaymentId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 결제 내역 조회
     * GET /api/v1/payments/history
     */
    @GetMapping("/history")
    @Operation(summary = "결제 내역 조회", description = "사용자의 결제 내역을 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 100, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<Page<PaymentHistoryResponse>>> getPaymentHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        try {
            log.info("결제 내역 조회: userId={}, status={}, method={}", 
                    userId, status, method);
            
            // 결제 내역 조회
            Page<PaymentHistoryResponse> response = paymentService.getPaymentHistory(
                    userId, status, method, startDate, endDate, pageable);
            
            log.info("결제 내역 조회 완료: userId={}, totalElements={}", 
                    userId, response.getTotalElements());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("결제 내역 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * PG사 웹훅 처리
     * POST /api/v1/payments/webhook
     */
    @PostMapping("/webhook")
    @Operation(summary = "PG 웹훅 처리", description = "PG사에서 전송하는 웹훅을 처리합니다.")
    @RateLimited(requests = 1000, period = 60, keyStrategy = RateLimited.KeyStrategy.IP_ADDRESS)
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {
        
        try {
            String clientIp = getClientIpAddress(request);
            String pgProvider = headers.get("X-PG-Provider");
            String signature = headers.get("X-PG-Signature");
            
            log.info("웹훅 수신: provider={}, ip={}, signature present={}", 
                    pgProvider, clientIp, signature != null);
            
            // 웹훅 검증 및 처리
            webhookService.processWebhook(pgProvider, payload, signature, headers);
            
            log.info("웹훅 처리 완료: provider={}", pgProvider);
            
            return ResponseEntity.ok(ApiResponse.success(null));
            
        } catch (Exception e) {
            log.error("웹훅 처리 실패: payload={}, error={}", payload, e.getMessage(), e);
            // 웹훅은 실패해도 200 OK 반환 (재시도 방지)
            return ResponseEntity.ok(ApiResponse.success(null));
        }
    }

    /**
     * 결제 상세 조회 (개별 결제)
     * GET /api/v1/payments/{paymentId}
     */
    @GetMapping("/{paymentId}")
    @Operation(summary = "결제 상세 조회", description = "특정 결제의 상세 정보를 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 60, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getPaymentDetail(
            @PathVariable @NotNull Long paymentId,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("결제 상세 조회: userId={}, paymentId={}", userId, paymentId);
            
            // 결제 권한 검증 및 상세 조회
            PaymentDetailResponse response = paymentService.getPaymentDetail(userId, paymentId);
            
            log.info("결제 상세 조회 완료: paymentId={}, status={}", 
                    paymentId, response.getStatus());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("결제 상세 조회 실패: userId={}, paymentId={}, error={}", 
                    userId, paymentId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 결제 수단 목록 조회
     * GET /api/v1/payments/methods
     */
    @GetMapping("/methods")
    @Operation(summary = "결제 수단 조회", description = "사용 가능한 결제 수단 목록을 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> getPaymentMethods(
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("결제 수단 조회: userId={}", userId);
            
            // 결제 수단 목록 조회
            List<PaymentMethodResponse> response = paymentService.getAvailablePaymentMethods(userId);
            
            log.info("결제 수단 조회 완료: userId={}, methodCount={}", 
                    userId, response.size());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("결제 수단 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 결제 취소
     * POST /api/v1/payments/{paymentId}/cancel
     */
    @PostMapping("/{paymentId}/cancel")
    @Operation(summary = "결제 취소", description = "진행 중인 결제를 취소합니다.")
    @PreAuthorize("isAuthenticated()")
    @SecureTransaction
    @RateLimits({
        @RateLimited(requests = 5, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID),
        @RateLimited(requests = 15, period = 3600, keyStrategy = RateLimited.KeyStrategy.USER_AND_IP)
    })
    public ResponseEntity<ApiResponse<PaymentCancelResponse>> cancelPayment(
            @PathVariable @NotNull Long paymentId,
            @Valid @RequestBody PaymentCancelRequest request,
            @AuthenticationPrincipal Long userId) {
        
        try {
            log.info("결제 취소 요청: userId={}, paymentId={}, reason={}", 
                    userId, paymentId, request.getCancelReason());
            
            // 결제 취소 권한 검증
            paymentService.validateCancelPermission(userId, paymentId);
            
            // 결제 취소 처리
            PaymentCancelResponse response = paymentService.cancelPayment(userId, paymentId, request);
            
            log.info("결제 취소 완료: paymentId={}, status={}", 
                    paymentId, response.getStatus());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("결제 취소 실패: userId={}, paymentId={}, error={}", 
                    userId, paymentId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 관리자 - 결제 통계 조회
     * GET /api/v1/payments/admin/stats
     */
    @GetMapping("/admin/stats")
    @Operation(summary = "[관리자] 결제 통계", description = "결제 통계 정보를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(requests = 30, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<PaymentStatsResponse>> getPaymentStats(
            @RequestParam(required = false, defaultValue = "7") int days) {
        
        try {
            log.info("결제 통계 조회: days={}", days);
            
            // 결제 통계 조회
            PaymentStatsResponse response = paymentService.getPaymentStats(days);
            
            log.info("결제 통계 조회 완료: totalAmount={}, totalCount={}", 
                    response.getTotalAmount(), response.getTotalCount());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("결제 통계 조회 실패: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 관리자 - 환불 승인/거절
     * POST /api/v1/payments/admin/refunds/{refundId}/approve
     */
    @PostMapping("/admin/refunds/{refundId}/approve")
    @Operation(summary = "[관리자] 환불 승인", description = "환불 요청을 승인하거나 거절합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @SecureTransaction
    @RateLimited(requests = 100, period = 300, keyStrategy = RateLimited.KeyStrategy.USER_ID)
    public ResponseEntity<ApiResponse<RefundApprovalResponse>> approveRefund(
            @PathVariable @NotNull Long refundId,
            @Valid @RequestBody RefundApprovalRequest request) {
        
        try {
            log.info("환불 승인 처리: refundId={}, approved={}, reason={}", 
                    refundId, request.isApproved(), request.getReason());
            
            // 환불 승인 처리
            RefundApprovalResponse response = paymentRefundService.approveRefund(refundId, request);
            
            log.info("환불 승인 완료: refundId={}, status={}", 
                    refundId, response.getStatus());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("환불 승인 실패: refundId={}, error={}", refundId, e.getMessage(), e);
            throw e;
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
```

---

## 📋 Payment Request DTOs

### PaymentProcessRequest.java
```java
package com.routepick.dto.payment.request;

import com.routepick.common.enums.PaymentMethod;
import com.routepick.common.enums.PaymentGateway;
import com.routepick.common.validation.korean.KoreanPhoneNumber;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * 결제 처리 요청 DTO
 */
@Data
@Schema(description = "결제 처리 요청")
public class PaymentProcessRequest {

    @NotNull(message = "결제 금액은 필수입니다")
    @DecimalMin(value = "100", message = "최소 결제 금액은 100원입니다")
    @DecimalMax(value = "10000000", message = "최대 결제 금액은 10,000,000원입니다")
    @Schema(description = "결제 금액", example = "5000")
    private BigDecimal amount;

    @NotNull(message = "결제 수단은 필수입니다")
    @Schema(description = "결제 수단", example = "CARD")
    private PaymentMethod paymentMethod;

    @NotNull(message = "PG사는 필수입니다")
    @Schema(description = "PG사", example = "TOSS")
    private PaymentGateway paymentGateway;

    @NotBlank(message = "주문명은 필수입니다")
    @Size(max = 100, message = "주문명은 100자 이하여야 합니다")
    @Schema(description = "주문명", example = "루트픽 프리미엄 결제")
    private String orderName;

    @Valid
    @NotEmpty(message = "결제 항목은 필수입니다")
    @Size(max = 10, message = "결제 항목은 최대 10개까지 가능합니다")
    @Schema(description = "결제 항목 목록")
    private List<PaymentItemRequest> items;

    @Schema(description = "성공 콜백 URL")
    private String successCallbackUrl;

    @Schema(description = "실패 콜백 URL")
    private String failureCallbackUrl;

    // 카드 결제 관련
    @Schema(description = "카드사 코드 (카드 결제시)")
    private String cardCompany;

    @Schema(description = "할부 개월 (카드 결제시)", example = "0")
    private Integer installmentMonths;

    // 가상계좌 결제 관련
    @Schema(description = "은행 코드 (가상계좌시)")
    private String bankCode;

    @Schema(description = "입금자명 (가상계좌시)")
    private String depositorName;

    // 휴대폰 결제 관련
    @KoreanPhoneNumber(message = "올바른 휴대폰 번호를 입력하세요")
    @Schema(description = "휴대폰 번호 (휴대폰 결제시)", example = "010-1234-5678")
    private String phoneNumber;

    // 기타
    @Schema(description = "결제 메모")
    private String memo;

    @Schema(description = "현금영수증 발급 여부", example = "false")
    private boolean cashReceiptRequested;

    @Schema(description = "현금영수증 번호")
    private String cashReceiptNumber;
}
```

### PaymentItemRequest.java
```java
package com.routepick.dto.payment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;

/**
 * 결제 항목 요청 DTO
 */
@Data
@Schema(description = "결제 항목")
public class PaymentItemRequest {

    @NotBlank(message = "상품명은 필수입니다")
    @Size(max = 100, message = "상품명은 100자 이하여야 합니다")
    @Schema(description = "상품명", example = "프리미엄 멤버십")
    private String itemName;

    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
    @Max(value = 100, message = "수량은 100개 이하여야 합니다")
    @Schema(description = "수량", example = "1")
    private Integer quantity;

    @NotNull(message = "단가는 필수입니다")
    @DecimalMin(value = "100", message = "최소 단가는 100원입니다")
    @Schema(description = "단가", example = "5000")
    private BigDecimal unitPrice;

    @Schema(description = "상품 설명")
    private String description;

    @Schema(description = "상품 카테고리")
    private String category;
}
```

### RefundRequest.java
```java
package com.routepick.dto.payment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;

/**
 * 환불 요청 DTO
 */
@Data
@Schema(description = "환불 요청")
public class RefundRequest {

    @NotNull(message = "결제 ID는 필수입니다")
    @Schema(description = "결제 ID", example = "123")
    private Long paymentId;

    @NotNull(message = "환불 금액은 필수입니다")
    @DecimalMin(value = "100", message = "최소 환불 금액은 100원입니다")
    @Schema(description = "환불 금액", example = "5000")
    private BigDecimal refundAmount;

    @NotBlank(message = "환불 사유는 필수입니다")
    @Size(max = 500, message = "환불 사유는 500자 이하여야 합니다")
    @Schema(description = "환불 사유", example = "서비스 불만족")
    private String refundReason;

    @Schema(description = "환불 계좌 은행 코드")
    private String refundBankCode;

    @Schema(description = "환불 계좌번호")
    private String refundAccountNumber;

    @Schema(description = "환불 계좌 예금주")
    private String refundAccountHolder;
}
```

---

## 📤 Payment Response DTOs

### PaymentResponse.java
```java
package com.routepick.dto.payment.response;

import com.routepick.common.enums.PaymentStatus;
import com.routepick.common.enums.PaymentMethod;
import com.routepick.common.enums.PaymentGateway;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 처리 응답 DTO
 */
@Data
@Schema(description = "결제 처리 응답")
public class PaymentResponse {

    @Schema(description = "결제 ID", example = "123")
    private Long paymentId;

    @Schema(description = "거래번호", example = "TXN_20250825_001")
    private String transactionId;

    @Schema(description = "결제 상태", example = "PENDING")
    private PaymentStatus status;

    @Schema(description = "결제 금액", example = "5000")
    private BigDecimal amount;

    @Schema(description = "결제 수단", example = "CARD")
    private PaymentMethod paymentMethod;

    @Schema(description = "PG사", example = "TOSS")
    private PaymentGateway paymentGateway;

    @Schema(description = "주문명", example = "루트픽 프리미엄 결제")
    private String orderName;

    @Schema(description = "결제 URL (리다이렉션용)")
    private String paymentUrl;

    @Schema(description = "QR 코드 URL")
    private String qrCodeUrl;

    @Schema(description = "가상계좌 정보")
    private VirtualAccountInfo virtualAccountInfo;

    @Schema(description = "결제 항목 목록")
    private List<PaymentItemResponse> items;

    @Schema(description = "결제 요청 시간")
    private LocalDateTime requestedAt;

    @Schema(description = "결제 만료 시간")
    private LocalDateTime expiresAt;

    @Schema(description = "결제 메모")
    private String memo;
}
```

### PaymentHistoryResponse.java
```java
package com.routepick.dto.payment.response;

import com.routepick.common.enums.PaymentStatus;
import com.routepick.common.enums.PaymentMethod;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 내역 응답 DTO
 */
@Data
@Schema(description = "결제 내역")
public class PaymentHistoryResponse {

    @Schema(description = "결제 ID", example = "123")
    private Long paymentId;

    @Schema(description = "거래번호", example = "TXN_20250825_001")
    private String transactionId;

    @Schema(description = "결제 상태", example = "COMPLETED")
    private PaymentStatus status;

    @Schema(description = "결제 금액", example = "5000")
    private BigDecimal amount;

    @Schema(description = "결제 수단", example = "CARD")
    private PaymentMethod paymentMethod;

    @Schema(description = "주문명", example = "루트픽 프리미엄 결제")
    private String orderName;

    @Schema(description = "결제 완료 시간")
    private LocalDateTime completedAt;

    @Schema(description = "환불 가능 여부")
    private boolean refundable;

    @Schema(description = "환불 가능 금액")
    private BigDecimal refundableAmount;

    @Schema(description = "카드사명 (카드 결제시)")
    private String cardCompany;

    @Schema(description = "카드 마지막 4자리 (마스킹)")
    private String maskedCardNumber;
}
```

### RefundResponse.java
```java
package com.routepick.dto.payment.response;

import com.routepick.common.enums.RefundStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 환불 처리 응답 DTO
 */
@Data
@Schema(description = "환불 처리 응답")
public class RefundResponse {

    @Schema(description = "환불 ID", example = "456")
    private Long refundId;

    @Schema(description = "원본 결제 ID", example = "123")
    private Long paymentId;

    @Schema(description = "환불 상태", example = "PENDING")
    private RefundStatus status;

    @Schema(description = "환불 금액", example = "5000")
    private BigDecimal refundAmount;

    @Schema(description = "환불 사유", example = "서비스 불만족")
    private String refundReason;

    @Schema(description = "환불 요청 시간")
    private LocalDateTime requestedAt;

    @Schema(description = "환불 예상 완료 시간")
    private LocalDateTime estimatedCompletionAt;

    @Schema(description = "환불 방법 안내")
    private String refundMethodDescription;
}
```

---

## 🔒 보안 강화 기능

### 1. PG사 IP 허용 목록 검증
```java
/**
 * PG사 IP 허용 목록 검증
 */
@Component
public class PGIpWhitelistValidator {
    
    private final Set<String> allowedIpRanges = Set.of(
        "52.78.100.19",    // 토스페이먼츠
        "52.78.48.223",    // 토스페이먼츠
        "110.76.143.1",    // 카카오페이
        "110.76.143.2",    // 카카오페이
        "211.33.136.1",    // 네이버페이
        "211.33.136.2"     // 네이버페이
    );
    
    public boolean isAllowedIp(String clientIp, String pgProvider) {
        return allowedIpRanges.contains(clientIp);
    }
}
```

### 2. 웹훅 서명 검증
```java
/**
 * 웹훅 서명 검증 서비스
 */
@Service
public class WebhookSignatureValidator {
    
    public boolean validateSignature(String payload, String signature, String secret) {
        try {
            String expectedSignature = calculateHmacSha256(payload, secret);
            return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("웹훅 서명 검증 실패", e);
            return false;
        }
    }
}
```

### 3. 결제 데이터 암호화
```java
/**
 * 결제 민감정보 암호화 서비스
 */
@Service
public class PaymentDataEncryption {
    
    public String encryptCardNumber(String cardNumber) {
        // 카드번호 암호화 (AES-256)
        return AESUtil.encrypt(cardNumber, getCardEncryptionKey());
    }
    
    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return "****-****-****-****";
        }
        return cardNumber.substring(0, 4) + "-****-****-" + 
               cardNumber.substring(cardNumber.length() - 4);
    }
}
```

---

*PaymentController 설계 완료일: 2025-08-25*
*구현 항목: 결제 처리, 환불 관리, 웹훅 처리, 결제 내역 조회*
*보안 기능: PCI DSS 준수, IP 허용목록, 서명검증, 데이터 암호화*
*다음 단계: NotificationController 구현*