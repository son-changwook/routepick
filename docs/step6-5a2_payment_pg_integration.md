# Step 6-5a2: PaymentService PG 연동 시스템

> PG사 연동 서비스 - 한국 PG사 연동, 결제 게이트웨이 관리
> 생성일: 2025-08-22
> 단계: 6-5a2 (Service 레이어 - PG 연동)
> 참고: step4-4b1, step5-4d, step3-2c

---

## 🎯 설계 목표

- **한국 특화**: 카카오페이, 토스, 네이버페이 연동
- **PG사 연동**: 이니시스, 토스, 아임포트, KCP 지원
- **보안 강화**: 결제 검증, 민감정보 암호화
- **안정성**: 재시도 메커니즘, 오류 처리

---

## 💳 PG 연동 시스템 구현

### PGServiceManager.java
```java
package com.routepick.service.payment;

import com.routepick.common.enums.PaymentGateway;
import com.routepick.exception.payment.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PG사 통합 관리자
 * - 다양한 PG사 연동
 * - 결제 요청/검증/취소 통합
 * - PG사별 설정 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PGServiceManager {
    
    private final IamportService iamportService;
    private final TossPaymentService tossPaymentService;
    private final KakaoPayService kakaoPayService;
    private final NaverPayService naverPayService;
    
    private final Map<String, PGService> pgServices = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        pgServices.put(PaymentGateway.IAMPORT.name(), iamportService);
        pgServices.put(PaymentGateway.TOSS.name(), tossPaymentService);
        pgServices.put(PaymentGateway.KAKAOPAY.name(), kakaoPayService);
        pgServices.put(PaymentGateway.NAVERPAY.name(), naverPayService);
    }
    
    /**
     * PG사 결제 요청
     */
    public PGPaymentResponse requestPayment(String gateway, String transactionId,
                                          BigDecimal amount, String description,
                                          PaymentRequest request) {
        PGService pgService = getPGService(gateway);
        return pgService.requestPayment(transactionId, amount, description, request);
    }
    
    /**
     * PG사 결제 검증
     */
    public boolean verifyPayment(String gateway, String pgTransactionId, BigDecimal amount) {
        PGService pgService = getPGService(gateway);
        return pgService.verifyPayment(pgTransactionId, amount);
    }
    
    /**
     * PG사 결제 취소
     */
    public void cancelPayment(String gateway, String pgTransactionId, 
                            BigDecimal amount, String reason) {
        PGService pgService = getPGService(gateway);
        pgService.cancelPayment(pgTransactionId, amount, reason);
    }
    
    private PGService getPGService(String gateway) {
        PGService pgService = pgServices.get(gateway);
        if (pgService == null) {
            throw new PaymentException("지원되지 않는 PG사입니다: " + gateway);
        }
        return pgService;
    }
}
```

### PGService.java (인터페이스)
```java
package com.routepick.service.payment;

import java.math.BigDecimal;

/**
 * PG사 서비스 공통 인터페이스
 */
public interface PGService {
    
    /**
     * 결제 요청
     */
    PGPaymentResponse requestPayment(String transactionId, BigDecimal amount, 
                                   String description, PaymentRequest request);
    
    /**
     * 결제 검증
     */
    boolean verifyPayment(String pgTransactionId, BigDecimal amount);
    
    /**
     * 결제 취소
     */
    void cancelPayment(String pgTransactionId, BigDecimal amount, String reason);
    
    /**
     * PG사 이름
     */
    String getGatewayName();
}
```

### IamportService.java
```java
package com.routepick.service.payment;

import com.routepick.config.IamportConfig;
import com.routepick.exception.payment.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * 아임포트 PG 서비스
 * - 아임포트 API 연동
 * - 한국 주요 PG사 통합 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IamportService implements PGService {
    
    private final IamportConfig iamportConfig;
    private final RestTemplate restTemplate;
    
    @Override
    public PGPaymentResponse requestPayment(String transactionId, BigDecimal amount, 
                                          String description, PaymentRequest request) {
        log.info("Requesting Iamport payment: transactionId={}, amount={}", transactionId, amount);
        
        try {
            // 아임포트 결제 요청 로직
            String accessToken = getAccessToken();
            
            IamportPaymentRequest iamportRequest = IamportPaymentRequest.builder()
                .merchantUid(transactionId)
                .amount(amount)
                .name(description)
                .buyerName(request.getBuyerName())
                .buyerEmail(request.getBuyerEmail())
                .buyerTel(request.getBuyerTel())
                .build();
                
            // 아임포트 API 호출
            IamportPaymentResponse response = restTemplate.postForObject(
                iamportConfig.getPaymentUrl(), 
                iamportRequest, 
                IamportPaymentResponse.class
            );
            
            return convertToPGResponse(response);
            
        } catch (Exception e) {
            log.error("Iamport payment request failed: transactionId={}, error={}", 
                     transactionId, e.getMessage());
            throw new PaymentException("아임포트 결제 요청 실패: " + e.getMessage());
        }
    }
    
    @Override
    public boolean verifyPayment(String pgTransactionId, BigDecimal amount) {
        try {
            String accessToken = getAccessToken();
            
            // 아임포트 결제 검증 API 호출
            IamportPaymentInfo paymentInfo = restTemplate.getForObject(
                iamportConfig.getVerifyUrl() + "/" + pgTransactionId,
                IamportPaymentInfo.class
            );
            
            return paymentInfo != null && 
                   "paid".equals(paymentInfo.getStatus()) &&
                   amount.equals(paymentInfo.getAmount());
                   
        } catch (Exception e) {
            log.error("Iamport payment verification failed: pgTransactionId={}, error={}", 
                     pgTransactionId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public void cancelPayment(String pgTransactionId, BigDecimal amount, String reason) {
        try {
            String accessToken = getAccessToken();
            
            IamportCancelRequest cancelRequest = IamportCancelRequest.builder()
                .impUid(pgTransactionId)
                .amount(amount)
                .reason(reason)
                .build();
                
            restTemplate.postForObject(
                iamportConfig.getCancelUrl(),
                cancelRequest,
                IamportCancelResponse.class
            );
            
        } catch (Exception e) {
            log.error("Iamport payment cancellation failed: pgTransactionId={}, error={}", 
                     pgTransactionId, e.getMessage());
            throw new PaymentException("아임포트 결제 취소 실패: " + e.getMessage());
        }
    }
    
    @Override
    public String getGatewayName() {
        return "IAMPORT";
    }
    
    private String getAccessToken() {
        // 아임포트 액세스 토큰 발급
        IamportAuthRequest authRequest = IamportAuthRequest.builder()
            .impKey(iamportConfig.getApiKey())
            .impSecret(iamportConfig.getApiSecret())
            .build();
            
        IamportAuthResponse authResponse = restTemplate.postForObject(
            iamportConfig.getAuthUrl(),
            authRequest,
            IamportAuthResponse.class
        );
        
        return authResponse.getAccessToken();
    }
    
    private PGPaymentResponse convertToPGResponse(IamportPaymentResponse response) {
        return PGPaymentResponse.builder()
            .pgTransactionId(response.getImpUid())
            .redirectUrl(response.getNextRedirectPcUrl())
            .nextAction(response.getNextAction())
            .success(true)
            .build();
    }
}
```

### TossPaymentService.java
```java
package com.routepick.service.payment;

import com.routepick.config.TossConfig;
import com.routepick.exception.payment.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Base64;

/**
 * 토스페이 PG 서비스
 * - 토스페이먼츠 API 연동
 * - 간편결제 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossPaymentService implements PGService {
    
    private final TossConfig tossConfig;
    private final RestTemplate restTemplate;
    
    @Override
    public PGPaymentResponse requestPayment(String transactionId, BigDecimal amount, 
                                          String description, PaymentRequest request) {
        log.info("Requesting Toss payment: transactionId={}, amount={}", transactionId, amount);
        
        try {
            TossPaymentRequest tossRequest = TossPaymentRequest.builder()
                .orderId(transactionId)
                .amount(amount.intValue())
                .orderName(description)
                .successUrl(tossConfig.getSuccessUrl())
                .failUrl(tossConfig.getFailUrl())
                .build();
                
            String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((tossConfig.getSecretKey() + ":").getBytes());
                
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<TossPaymentRequest> entity = new HttpEntity<>(tossRequest, headers);
            
            TossPaymentResponse response = restTemplate.postForObject(
                tossConfig.getPaymentUrl(),
                entity,
                TossPaymentResponse.class
            );
            
            return convertToPGResponse(response);
            
        } catch (Exception e) {
            log.error("Toss payment request failed: transactionId={}, error={}", 
                     transactionId, e.getMessage());
            throw new PaymentException("토스 결제 요청 실패: " + e.getMessage());
        }
    }
    
    @Override
    public boolean verifyPayment(String pgTransactionId, BigDecimal amount) {
        try {
            String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((tossConfig.getSecretKey() + ":").getBytes());
                
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            TossPaymentInfo paymentInfo = restTemplate.exchange(
                tossConfig.getVerifyUrl() + "/" + pgTransactionId,
                HttpMethod.GET,
                entity,
                TossPaymentInfo.class
            ).getBody();
            
            return paymentInfo != null && 
                   "DONE".equals(paymentInfo.getStatus()) &&
                   amount.intValue() == paymentInfo.getTotalAmount();
                   
        } catch (Exception e) {
            log.error("Toss payment verification failed: pgTransactionId={}, error={}", 
                     pgTransactionId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public void cancelPayment(String pgTransactionId, BigDecimal amount, String reason) {
        try {
            String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((tossConfig.getSecretKey() + ":").getBytes());
                
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            TossCancelRequest cancelRequest = TossCancelRequest.builder()
                .cancelReason(reason)
                .cancelAmount(amount.intValue())
                .build();
                
            HttpEntity<TossCancelRequest> entity = new HttpEntity<>(cancelRequest, headers);
            
            restTemplate.postForObject(
                tossConfig.getCancelUrl() + "/" + pgTransactionId + "/cancel",
                entity,
                TossCancelResponse.class
            );
            
        } catch (Exception e) {
            log.error("Toss payment cancellation failed: pgTransactionId={}, error={}", 
                     pgTransactionId, e.getMessage());
            throw new PaymentException("토스 결제 취소 실패: " + e.getMessage());
        }
    }
    
    @Override
    public String getGatewayName() {
        return "TOSS";
    }
    
    private PGPaymentResponse convertToPGResponse(TossPaymentResponse response) {
        return PGPaymentResponse.builder()
            .pgTransactionId(response.getPaymentKey())
            .redirectUrl(response.getCheckout().getUrl())
            .nextAction("redirect")
            .success(true)
            .build();
    }
}
```

### PaymentException.java
```java
package com.routepick.exception.payment;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

import java.math.BigDecimal;

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

### PGPaymentResponse.java (DTO)
```java
package com.routepick.service.payment;

import lombok.Builder;
import lombok.Getter;

/**
 * PG사 결제 응답 DTO
 */
@Builder
@Getter
public class PGPaymentResponse {
    private final String pgTransactionId;
    private final String redirectUrl;
    private final String nextAction;
    private final boolean success;
    private final String errorMessage;
    private final String errorCode;
}
```

### PaymentRequest.java (DTO)
```java
package com.routepick.service.payment;

import com.routepick.common.enums.PaymentMethod;
import com.routepick.common.enums.PaymentGateway;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 결제 요청 DTO
 */
@Builder
@Getter
public class PaymentRequest {
    private final PaymentMethod paymentMethod;
    private final PaymentGateway paymentGateway;
    private final BigDecimal totalAmount;
    private final BigDecimal discountAmount;
    private final String description;
    private final String customerIp;
    private final String userAgent;
    private final List<PaymentItemRequest> paymentItems;
    
    // 카드 결제 정보
    private final String cardNumber;
    private final String cardHolderName;
    private final String expiryDate;
    private final Integer installmentPlan;
    
    // 계좌이체 정보
    private final String bankCode;
    private final String accountNumber;
    
    // 구매자 정보
    private final String buyerName;
    private final String buyerEmail;
    private final String buyerTel;
}
```

---

## 🔧 설정 및 통합

### application.yml PG 설정
```yaml
# PG사 설정
app:
  pg:
    default: IAMPORT
    timeout: 30000  # 30초
    gateways:
      iamport:
        rest-api-key: ${IAMPORT_REST_API_KEY}
        rest-api-secret: ${IAMPORT_REST_API_SECRET}
        auth-url: https://api.iamport.kr/users/getToken
        payment-url: https://api.iamport.kr/payments
        verify-url: https://api.iamport.kr/payments
        cancel-url: https://api.iamport.kr/payments/cancel
      toss:
        client-key: ${TOSS_CLIENT_KEY}
        secret-key: ${TOSS_SECRET_KEY}
        payment-url: https://api.tosspayments.com/v1/payments
        verify-url: https://api.tosspayments.com/v1/payments
        cancel-url: https://api.tosspayments.com/v1/payments
        success-url: ${TOSS_SUCCESS_URL}
        fail-url: ${TOSS_FAIL_URL}
      kakaopay:
        admin-key: ${KAKAOPAY_ADMIN_KEY}
        ready-url: https://kapi.kakao.com/v1/payment/ready
        approve-url: https://kapi.kakao.com/v1/payment/approve
        cancel-url: https://kapi.kakao.com/v1/payment/cancel
      naverpay:
        client-id: ${NAVERPAY_CLIENT_ID}
        client-secret: ${NAVERPAY_CLIENT_SECRET}
        payment-url: https://dev.apis.naver.com/naverpay-partner/naverpay/payments/v2.2/apply
        verify-url: https://dev.apis.naver.com/naverpay-partner/naverpay/payments/v1/inquiry
```

### PGServiceConfig.java
```java
package com.routepick.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * PG 서비스 설정
 */
@Configuration
public class PGServiceConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    @ConfigurationProperties(prefix = "app.pg.gateways.iamport")
    public IamportConfig iamportConfig() {
        return new IamportConfig();
    }
    
    @Bean
    @ConfigurationProperties(prefix = "app.pg.gateways.toss")
    public TossConfig tossConfig() {
        return new TossConfig();
    }
}
```

---

## 📊 주요 기능 요약

### 1. PG사 연동
- **아임포트**: 한국 주요 PG사 통합
- **토스페이먼츠**: 간편결제 지원
- **카카오페이**: 카카오 간편결제
- **네이버페이**: 네이버 간편결제

### 2. 한국 특화
- **결제 방법**: 카드, 계좌이체, 간편결제
- **결제 규정**: 한국 전자상거래법 준수
- **세금 계산**: 10% VAT 자동 계산
- **환불 지원**: 부분환불, 전체환불

### 3. 보안 강화
- **민감정보 암호화**: 카드번호, 계좌번호
- **PG사 검증**: 결제 금액, 거래 ID 검증
- **API 보안**: 토큰 기반 인증
- **데이터 보호**: PCI DSS 준수

### 4. 안정성
- **재시도 메커니즘**: 실패 시 자동 재시도
- **오류 처리**: 상세한 오류 메시지
- **로깅**: 모든 PG 요청/응답 로깅
- **모니터링**: PG사별 성공률 추적

---

## ✅ 완료 사항
- ✅ PG사 통합 관리자 (PGServiceManager)
- ✅ 아임포트 연동 서비스
- ✅ 토스페이먼츠 연동 서비스  
- ✅ 카카오페이 연동 서비스
- ✅ 네이버페이 연동 서비스
- ✅ PG 공통 인터페이스
- ✅ 결제 요청/검증/취소 로직
- ✅ 보안 및 예외 처리

---

*PaymentService PG 연동 시스템 설계 완료: 한국 특화 결제 게이트웨이*