# Step 9-5a: PaymentController 실제 설계

## 📋 구현 목표
- **결제 처리 API**: 한국 PG사 연동 결제 시스템
- **환불 관리 API**: 자동/수동 환불 처리
- **웹훅 처리**: PG사 결제 상태 알림 수신
- **보안 강화**: 결제 보안, 권한 검증, Rate Limiting

## 💳 PaymentController 구현

### PaymentController.java
```java
package com.routepick.backend.controller.payment;

import com.routepick.backend.common.response.ApiResponse;
import com.routepick.backend.dto.payment.request.PaymentProcessRequestDto;
import com.routepick.backend.dto.payment.request.PaymentRefundRequestDto;
import com.routepick.backend.dto.payment.response.PaymentDetailResponseDto;
import com.routepick.backend.dto.payment.response.PaymentProcessResponseDto;
import com.routepick.backend.dto.payment.response.PaymentHistoryResponseDto;
import com.routepick.backend.security.annotation.RateLimit;
import com.routepick.backend.security.annotation.AuditLog;
import com.routepick.backend.security.enums.AuditEventType;
import com.routepick.backend.service.payment.PaymentService;
import com.routepick.backend.service.payment.PaymentRefundService;
import com.routepick.backend.service.payment.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.Map;

/**
 * 결제 관리 컨트롤러
 * - 한국 PG사 연동 결제 처리
 * - 환불 관리 및 웹훅 처리
 * - 결제 보안 및 감사 로깅
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Payment", description = "결제 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {
    
    private final PaymentService paymentService;
    private final PaymentRefundService refundService;
    private final PaymentWebhookService webhookService;
    
    /**
     * 결제 처리
     */
    @PostMapping("/process")
    @Operation(
        summary = "결제 처리",
        description = """
            한국 PG사를 통한 결제 처리
            
            ## 지원 PG사
            - 토스페이먼츠 (TOSS)
            - 카카오페이 (KAKAO) 
            - 네이버페이 (NAVER)
            
            ## 보안 기능
            - Rate Limiting: 10회/시간 제한
            - 결제 금액 검증 및 한도 확인
            - PCI DSS 보안 표준 준수
            - 결제 시도 감사 로깅
            """
    )
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "결제 처리 성공"),
        @SwaggerApiResponse(responseCode = "400", description = "잘못된 결제 요청"),
        @SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        @SwaggerApiResponse(responseCode = "429", description = "결제 시도 횟수 초과"),
        @SwaggerApiResponse(responseCode = "500", description = "PG사 통신 오류")
    })
    @PreAuthorize("hasRole('USER')")
    @RateLimit(type = "PAYMENT", limit = 10, window = "1h")
    @AuditLog(type = AuditEventType.PAYMENT_ATTEMPT)
    public ResponseEntity<ApiResponse<PaymentProcessResponseDto>> processPayment(
            @Valid @RequestBody PaymentProcessRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        
        log.info("결제 요청 시작 - 사용자: {}, 금액: {}원", 
                userDetails.getUsername(), request.getTotalAmount());
        
        try {
            Long userId = extractUserId(userDetails);
            String clientIp = getClientIpAddress(httpRequest);
            
            PaymentProcessResponseDto response = paymentService.processPayment(
                    request, userId, clientIp);
            
            log.info("결제 요청 처리 완료 - 결제ID: {}, 상태: {}", 
                    response.getPaymentId(), response.getPaymentStatus());
            
            return ResponseEntity.ok(ApiResponse.success(
                    "결제 요청이 처리되었습니다", response));
                    
        } catch (Exception e) {
            log.error("결제 처리 실패 - 사용자: {}, 오류: {}", 
                    userDetails.getUsername(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 결제 상세 조회
     */
    @GetMapping("/{paymentId}")
    @Operation(summary = "결제 상세 조회", description = "결제 ID로 결제 상세 정보 조회")
    @PreAuthorize("hasRole('USER')")
    @AuditLog(type = AuditEventType.SENSITIVE_DATA_ACCESS)
    public ResponseEntity<ApiResponse<PaymentDetailResponseDto>> getPaymentDetail(
            @Parameter(description = "결제 ID", required = true)
            @PathVariable @NotNull @Positive Long paymentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Long userId = extractUserId(userDetails);
        
        PaymentDetailResponseDto payment = paymentService.getPaymentDetail(paymentId, userId);
        
        return ResponseEntity.ok(ApiResponse.success(
                "결제 정보 조회 성공", payment));
    }
    
    /**
     * 사용자 결제 내역 조회
     */
    @GetMapping("/history")
    @Operation(summary = "결제 내역 조회", description = "사용자의 결제 내역 페이징 조회")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Page<PaymentHistoryResponseDto>>> getPaymentHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        
        Long userId = extractUserId(userDetails);
        
        Page<PaymentHistoryResponseDto> paymentHistory = 
                paymentService.getPaymentHistory(userId, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
                "결제 내역 조회 성공", paymentHistory));
    }
    
    /**
     * 결제 환불 요청
     */
    @PostMapping("/{paymentId}/refund")
    @Operation(
        summary = "결제 환불",
        description = """
            결제 환불 처리
            
            ## 환불 정책
            - 전액 환불: 결제 후 24시간 이내
            - 부분 환불: 관리자 승인 필요
            - 자동 환불: 시스템 오류 시 자동 처리
            
            ## 환불 처리 시간
            - 신용카드: 3-5 영업일
            - 계좌이체: 1-2 영업일
            - 간편결제: 즉시 처리
            """
    )
    @PreAuthorize("hasRole('USER')")
    @RateLimit(type = "REFUND", limit = 5, window = "1h")
    @AuditLog(type = AuditEventType.PAYMENT_REFUND)
    public ResponseEntity<ApiResponse<Void>> refundPayment(
            @PathVariable @NotNull @Positive Long paymentId,
            @Valid @RequestBody PaymentRefundRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Long userId = extractUserId(userDetails);
        
        log.info("환불 요청 시작 - 결제ID: {}, 사용자: {}, 환불금액: {}원", 
                paymentId, userDetails.getUsername(), request.getRefundAmount());
        
        refundService.processRefund(paymentId, userId, request);
        
        log.info("환불 요청 처리 완료 - 결제ID: {}", paymentId);
        
        return ResponseEntity.ok(ApiResponse.success("환불 요청이 처리되었습니다"));
    }
    
    /**
     * PG사 웹훅 수신
     */
    @PostMapping("/webhook")
    @Operation(
        summary = "PG사 웹훅 수신",
        description = """
            PG사로부터 결제 상태 변경 알림 수신
            
            ## 웹훅 이벤트
            - PAYMENT_COMPLETED: 결제 완료
            - PAYMENT_CANCELLED: 결제 취소
            - PAYMENT_FAILED: 결제 실패
            - REFUND_COMPLETED: 환불 완료
            
            ## 보안 검증
            - 서명 검증 (HMAC-SHA256)
            - IP 화이트리스트 검증
            - 중복 요청 방지
            """
    )
    @AuditLog(type = AuditEventType.WEBHOOK_RECEIVED)
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader("X-Signature") String signature,
            HttpServletRequest request) {
        
        String clientIp = getClientIpAddress(request);
        
        log.info("웹훅 수신 - IP: {}, Signature: {}...", clientIp, signature.substring(0, 10));
        
        try {
            webhookService.processWebhook(payload, signature, clientIp);
            
            log.info("웹훅 처리 완료 - Type: {}", payload.get("eventType"));
            
            return ResponseEntity.ok(ApiResponse.success("웹훅 처리 완료"));
            
        } catch (Exception e) {
            log.error("웹훅 처리 실패 - IP: {}, 오류: {}", clientIp, e.getMessage());
            throw e;
        }
    }
    
    /**
     * 결제 취소 (사용자 요청)
     */
    @PostMapping("/{paymentId}/cancel")
    @Operation(summary = "결제 취소", description = "결제 완료 전 사용자 요청에 의한 결제 취소")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(type = "CANCEL", limit = 10, window = "1h")
    @AuditLog(type = AuditEventType.PAYMENT_CANCEL)
    public ResponseEntity<ApiResponse<Void>> cancelPayment(
            @PathVariable @NotNull @Positive Long paymentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Long userId = extractUserId(userDetails);
        
        log.info("결제 취소 요청 - 결제ID: {}, 사용자: {}", paymentId, userDetails.getUsername());
        
        paymentService.cancelPayment(paymentId, userId);
        
        return ResponseEntity.ok(ApiResponse.success("결제가 취소되었습니다"));
    }
    
    /**
     * 결제 상태 확인
     */
    @GetMapping("/{paymentId}/status")
    @Operation(summary = "결제 상태 확인", description = "PG사에서 실시간 결제 상태 조회")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkPaymentStatus(
            @PathVariable @NotNull @Positive Long paymentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Long userId = extractUserId(userDetails);
        
        Map<String, Object> status = paymentService.checkPaymentStatus(paymentId, userId);
        
        return ResponseEntity.ok(ApiResponse.success("결제 상태 조회 성공", status));
    }
    
    // ===== 관리자 전용 API =====
    
    /**
     * 모든 결제 내역 조회 (관리자)
     */
    @GetMapping("/admin/all")
    @Operation(summary = "[관리자] 전체 결제 내역", description = "관리자용 전체 결제 내역 조회")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(type = AuditEventType.ADMIN_ACTIVITY)
    public ResponseEntity<ApiResponse<Page<PaymentDetailResponseDto>>> getAllPayments(
            Pageable pageable) {
        
        Page<PaymentDetailResponseDto> payments = paymentService.getAllPayments(pageable);
        
        return ResponseEntity.ok(ApiResponse.success("전체 결제 내역 조회 성공", payments));
    }
    
    /**
     * 환불 승인/거부 (관리자)
     */
    @PostMapping("/admin/{paymentId}/refund/{action}")
    @Operation(summary = "[관리자] 환불 승인/거부", description = "관리자의 환불 요청 승인 또는 거부")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(type = AuditEventType.ADMIN_ACTIVITY)
    public ResponseEntity<ApiResponse<Void>> approveRefund(
            @PathVariable @NotNull @Positive Long paymentId,
            @Parameter(description = "승인(approve) 또는 거부(reject)")
            @PathVariable String action,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        String adminUsername = userDetails.getUsername();
        
        log.info("환불 {}요청 - 결제ID: {}, 관리자: {}", 
                action.equals("approve") ? "승인 " : "거부 ", paymentId, adminUsername);
        
        if ("approve".equals(action)) {
            refundService.approveRefund(paymentId, adminUsername);
            return ResponseEntity.ok(ApiResponse.success("환불이 승인되었습니다"));
        } else if ("reject".equals(action)) {
            refundService.rejectRefund(paymentId, adminUsername);
            return ResponseEntity.ok(ApiResponse.success("환불이 거부되었습니다"));
        } else {
            throw new IllegalArgumentException("유효하지 않은 액션입니다: " + action);
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * UserDetails에서 사용자 ID 추출
     */
    private Long extractUserId(UserDetails userDetails) {
        // 실제 구현에서는 CustomUserDetails에서 userId 추출
        try {
            return Long.valueOf(userDetails.getUsername());
        } catch (NumberFormatException e) {
            // username이 이메일인 경우 별도 처리 필요
            log.debug("사용자 ID 추출 실패, 이메일로 사용자 조회: {}", userDetails.getUsername());
            // UserService를 통해 이메일로 사용자 ID 조회
            throw new IllegalStateException("사용자 ID를 추출할 수 없습니다");
        }
    }
    
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

## 📋 Request/Response DTO

### PaymentProcessRequestDto.java
```java
package com.routepick.backend.dto.payment.request;

import com.routepick.backend.common.enums.PaymentMethod;
import com.routepick.backend.common.enums.PaymentGateway;
import com.routepick.backend.security.annotation.SafeText;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "결제 처리 요청")
public class PaymentProcessRequestDto {
    
    @NotNull(message = "결제 금액을 입력해주세요")
    @DecimalMin(value = "100", message = "최소 결제 금액은 100원입니다")
    @DecimalMax(value = "10000000", message = "최대 결제 금액은 천만원입니다")
    @Schema(description = "결제 금액", example = "10000")
    private BigDecimal totalAmount;
    
    @NotNull(message = "결제 방법을 선택해주세요")
    @Schema(description = "결제 방법", example = "CARD")
    private PaymentMethod paymentMethod;
    
    @NotNull(message = "결제 게이트웨이를 선택해주세요")
    @Schema(description = "PG사", example = "TOSS")
    private PaymentGateway paymentGateway;
    
    @NotBlank(message = "상품명을 입력해주세요")
    @Size(max = 100, message = "상품명은 100자 이내여야 합니다")
    @SafeText
    @Schema(description = "상품명", example = "클라이밍 월 회원권")
    private String itemName;
    
    @NotBlank(message = "구매자명을 입력해주세요")
    @Size(max = 50, message = "구매자명은 50자 이내여야 합니다")
    @SafeText
    @Schema(description = "구매자명", example = "홍길동")
    private String buyerName;
    
    @NotBlank(message = "구매자 이메일을 입력해주세요")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Schema(description = "구매자 이메일", example = "buyer@example.com")
    private String buyerEmail;
    
    @Pattern(regexp = "^01[0-9]-[0-9]{3,4}-[0-9]{4}$", 
             message = "올바른 휴대폰 번호 형식이 아닙니다")
    @Schema(description = "구매자 휴대폰", example = "010-1234-5678")
    private String buyerTel;
    
    @Size(max = 200, message = "결제 설명은 200자 이내여야 합니다")
    @SafeText
    @Schema(description = "결제 설명", example = "월 회원권 결제")
    private String description;
    
    @Schema(description = "할인 쿠폰 ID")
    private Long couponId;
    
    @Schema(description = "포인트 사용 금액", example = "0")
    private BigDecimal pointAmount = BigDecimal.ZERO;
}
```

### PaymentProcessResponseDto.java
```java
package com.routepick.backend.dto.payment.response;

import com.routepick.backend.common.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "결제 처리 응답")
public class PaymentProcessResponseDto {
    
    @Schema(description = "결제 ID", example = "1")
    private Long paymentId;
    
    @Schema(description = "거래 번호", example = "TXN20250902001")
    private String transactionId;
    
    @Schema(description = "결제 상태", example = "PENDING")
    private PaymentStatus paymentStatus;
    
    @Schema(description = "결제 금액", example = "10000")
    private BigDecimal totalAmount;
    
    @Schema(description = "PG사 결제 페이지 URL")
    private String paymentUrl;
    
    @Schema(description = "결제 만료 시간")
    private LocalDateTime expiresAt;
    
    @Schema(description = "QR 코드 URL (간편결제용)")
    private String qrCodeUrl;
    
    @Schema(description = "결제 처리 메시지")
    private String message;
}
```

## 🧪 테스트 코드

### PaymentControllerTest.java
```java
package com.routepick.backend.controller.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routepick.backend.common.enums.PaymentStatus;
import com.routepick.backend.common.enums.PaymentMethod;
import com.routepick.backend.common.enums.PaymentGateway;
import com.routepick.backend.dto.payment.request.PaymentProcessRequestDto;
import com.routepick.backend.dto.payment.request.PaymentRefundRequestDto;
import com.routepick.backend.dto.payment.response.PaymentProcessResponseDto;
import com.routepick.backend.service.payment.PaymentService;
import com.routepick.backend.service.payment.PaymentRefundService;
import com.routepick.backend.service.payment.PaymentWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController 테스트")
class PaymentControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private PaymentService paymentService;
    
    @MockBean
    private PaymentRefundService refundService;
    
    @MockBean
    private PaymentWebhookService webhookService;
    
    private PaymentProcessRequestDto validPaymentRequest;
    
    @BeforeEach
    void setUp() {
        validPaymentRequest = PaymentProcessRequestDto.builder()
                .totalAmount(new BigDecimal("10000"))
                .paymentMethod(PaymentMethod.CARD)
                .paymentGateway(PaymentGateway.TOSS)
                .itemName("클라이밍 회원권")
                .buyerName("홍길동")
                .buyerEmail("test@example.com")
                .buyerTel("010-1234-5678")
                .description("월 회원권 결제")
                .build();
    }
    
    @Nested
    @DisplayName("결제 처리 API")
    class PaymentProcessTests {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("결제 요청 성공")
        void shouldProcessPaymentSuccessfully() throws Exception {
            // given
            PaymentProcessResponseDto response = PaymentProcessResponseDto.builder()
                    .paymentId(1L)
                    .transactionId("TXN20250902001")
                    .paymentStatus(PaymentStatus.PENDING)
                    .totalAmount(new BigDecimal("10000"))
                    .paymentUrl("https://pay.toss.im/test")
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .message("결제 요청이 처리되었습니다")
                    .build();
            
            given(paymentService.processPayment(any(), any(), any())).willReturn(response);
            
            // when & then
            mockMvc.perform(post("/api/v1/payments/process")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPaymentRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.paymentId").value(1))
                    .andExpect(jsonPath("$.data.transactionId").value("TXN20250902001"))
                    .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));
        }
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("결제 금액 유효성 검증 실패")
        void shouldFailValidationForInvalidAmount() throws Exception {
            // given
            PaymentProcessRequestDto invalidRequest = PaymentProcessRequestDto.builder()
                    .totalAmount(new BigDecimal("50")) // 최소 금액 100원 미만
                    .paymentMethod(PaymentMethod.CARD)
                    .paymentGateway(PaymentGateway.TOSS)
                    .itemName("클라이밍 회원권")
                    .buyerName("홍길동")
                    .buyerEmail("test@example.com")
                    .build();
            
            // when & then
            mockMvc.perform(post("/api/v1/payments/process")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
    }
    
    @Nested
    @DisplayName("결제 조회 API")
    class PaymentQueryTests {
        
        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("결제 상세 조회 성공")
        void shouldGetPaymentDetailSuccessfully() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/payments/1"))
                    .andExpect(status().isOk());
            
            verify(paymentService).getPaymentDetail(eq(1L), eq(1L));
        }
    }
}
```

---

**다음 단계**: step9-5b_payment_service_implementation.md (PaymentService 설계)  
**연관 시스템**: step8 보안 시스템 (Rate Limiting, Audit Log) 완전 통합  
**성능 목표**: 결제 API 응답시간 3초 이내, 처리량 100 TPS  

*생성일: 2025-09-02*  
*RoutePickr 9-5a: 한국 특화 결제 시스템 API 완성*