# Step 6-5c1: Webhook Processing Core Service

**파일**: `routepick-backend/src/main/java/com/routepick/service/webhook/WebhookService.java`

이 파일은 웹훅 처리의 핵심 기능을 구현합니다.

## 🔗 웹훅 처리 핵심 서비스 구현

```java
package com.routepick.service.webhook;

import com.routepick.common.enums.WebhookStatus;
import com.routepick.common.enums.WebhookType;
import com.routepick.common.enums.ApiProvider;
import com.routepick.domain.system.entity.WebhookLog;
import com.routepick.domain.system.entity.ExternalApiConfig;
import com.routepick.domain.system.repository.WebhookLogRepository;
import com.routepick.domain.system.repository.ExternalApiConfigRepository;
import com.routepick.domain.payment.entity.PaymentRecord;
import com.routepick.exception.system.SystemException;
import com.routepick.service.payment.PaymentService;
import com.routepick.service.payment.PaymentRefundService;
import com.routepick.util.WebhookSignatureValidator;
import com.routepick.util.JsonUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 웹훅 처리 핵심 서비스
 * 
 * 주요 기능:
 * 1. 웹훅 수신 및 검증
 * 2. 중복 처리 방지
 * 3. 웹훅 재시도 로직
 * 4. 타입별 웹훅 처리
 * 5. 외부 API 연동 관리
 * 
 * @author RoutePickr Team
 * @since 2025.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookService {
    
    private final WebhookLogRepository webhookLogRepository;
    private final ExternalApiConfigRepository apiConfigRepository;
    private final PaymentService paymentService;
    private final PaymentRefundService refundService;
    private final ApplicationEventPublisher eventPublisher;
    private final WebhookSignatureValidator signatureValidator;
    
    // 웹훅 중복 처리 방지를 위한 캐시
    private final Map<String, Long> processingWebhooks = new ConcurrentHashMap<>();
    
    // 캐시 이름
    private static final String CACHE_WEBHOOK_CONFIG = "webhookConfig";
    
    // 설정값
    private static final int MAX_RETRY_COUNT = 5;
    private static final int WEBHOOK_TIMEOUT_MINUTES = 5;
    private static final long DUPLICATE_WINDOW_MINUTES = 10; // 중복 확인 윈도우
    
    // ===================== 웹훅 처리 =====================
    
    /**
     * 웹훅 처리 메인 메서드
     */
    @Transactional
    public WebhookLog processWebhook(WebhookType webhookType, ApiProvider provider,
                                   String payload, String signature, Map<String, String> headers) {
        log.info("Processing webhook: type={}, provider={}", webhookType, provider);
        
        String webhookId = extractWebhookId(payload, provider);
        
        // 중복 처리 방지
        if (isDuplicateWebhook(webhookId, provider)) {
            log.warn("Duplicate webhook detected: webhookId={}, provider={}", webhookId, provider);
            throw new SystemException("중복된 웹훅입니다: " + webhookId);
        }
        
        // 웹훅 로그 생성
        WebhookLog webhookLog = WebhookLog.builder()
            .webhookId(webhookId)
            .webhookType(webhookType)
            .provider(provider)
            .payload(payload)
            .signature(signature)
            .headers(JsonUtil.toJson(headers))
            .status(WebhookStatus.RECEIVED)
            .retryCount(0)
            .build();
            
        WebhookLog savedLog = webhookLogRepository.save(webhookLog);
        
        try {
            // 중복 처리 마킹
            markWebhookAsProcessing(webhookId, savedLog.getLogId());
            
            // 서명 검증
            validateWebhookSignature(provider, payload, signature);
            
            // 웹훅 타입별 처리
            processWebhookByType(savedLog, payload);
            
            // 처리 완료
            updateWebhookStatus(savedLog, WebhookStatus.PROCESSED, null);
            
        } catch (Exception e) {
            log.error("Webhook processing failed: webhookId={}, error={}", webhookId, e.getMessage());
            
            // 처리 실패
            updateWebhookStatus(savedLog, WebhookStatus.FAILED, e.getMessage());
            
            // 재시도 스케줄링
            scheduleWebhookRetry(savedLog);
            
            throw new SystemException("웹훅 처리 실패: " + e.getMessage());
            
        } finally {
            // 중복 처리 마킹 해제
            unmarkWebhookAsProcessing(webhookId);
        }
        
        log.info("Webhook processed successfully: webhookId={}", webhookId);
        return savedLog;
    }
    
    /**
     * 웹훅 재시도 처리
     */
    @Transactional
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public WebhookLog retryWebhook(Long webhookLogId) {
        log.info("Retrying webhook: webhookLogId={}", webhookLogId);
        
        WebhookLog webhookLog = webhookLogRepository.findById(webhookLogId)
            .orElseThrow(() -> new SystemException("웹훅 로그를 찾을 수 없습니다: " + webhookLogId));
            
        // 재시도 가능 여부 확인
        if (webhookLog.getRetryCount() >= MAX_RETRY_COUNT) {
            throw new SystemException("최대 재시도 횟수를 초과했습니다: " + webhookLog.getRetryCount());
        }
        
        if (webhookLog.getStatus() == WebhookStatus.PROCESSED) {
            throw new SystemException("이미 처리된 웹훅입니다");
        }
        
        try {
            // 재시도 카운트 증가
            webhookLog.setRetryCount(webhookLog.getRetryCount() + 1);
            webhookLog.setStatus(WebhookStatus.RETRYING);
            webhookLog.setLastRetryAt(LocalDateTime.now());
            
            // 웹훅 재처리
            processWebhookByType(webhookLog, webhookLog.getPayload());
            
            // 처리 완료
            updateWebhookStatus(webhookLog, WebhookStatus.PROCESSED, null);
            
        } catch (Exception e) {
            log.error("Webhook retry failed: webhookLogId={}, retryCount={}, error={}", 
                     webhookLogId, webhookLog.getRetryCount(), e.getMessage());
            
            // 재시도 실패
            updateWebhookStatus(webhookLog, WebhookStatus.FAILED, e.getMessage());
            
            // 최대 재시도 횟수 도달시 포기
            if (webhookLog.getRetryCount() >= MAX_RETRY_COUNT) {
                webhookLog.setStatus(WebhookStatus.ABANDONED);
                log.error("Webhook abandoned after {} retries: webhookLogId={}", 
                         MAX_RETRY_COUNT, webhookLogId);
            }
            
            throw e;
        }
        
        WebhookLog retriedLog = webhookLogRepository.save(webhookLog);
        
        log.info("Webhook retried successfully: webhookLogId={}, retryCount={}", 
                webhookLogId, retriedLog.getRetryCount());
        return retriedLog;
    }
    
    // ===================== 웹훅 타입별 처리 =====================
    
    /**
     * 웹훅 타입별 처리 분기
     */
    private void processWebhookByType(WebhookLog webhookLog, String payload) {
        switch (webhookLog.getWebhookType()) {
            case PAYMENT_COMPLETED:
                processPaymentCompletedWebhook(payload, webhookLog.getProvider());
                break;
                
            case PAYMENT_FAILED:
                processPaymentFailedWebhook(payload, webhookLog.getProvider());
                break;
                
            case PAYMENT_CANCELLED:
                processPaymentCancelledWebhook(payload, webhookLog.getProvider());
                break;
                
            case REFUND_COMPLETED:
                processRefundCompletedWebhook(payload, webhookLog.getProvider());
                break;
                
            case REFUND_FAILED:
                processRefundFailedWebhook(payload, webhookLog.getProvider());
                break;
                
            default:
                log.warn("Unknown webhook type: {}", webhookLog.getWebhookType());
                throw new SystemException("지원하지 않는 웹훅 타입입니다: " + webhookLog.getWebhookType());
        }
    }
    
    /**
     * 결제 완료 웹훅 처리
     */
    private void processPaymentCompletedWebhook(String payload, ApiProvider provider) {
        PaymentWebhookData data = parsePaymentWebhookData(payload, provider);
        
        try {
            paymentService.approvePayment(
                data.getTransactionId(),
                data.getPgTransactionId(),
                data.getApprovalNumber()
            );
            
            log.info("Payment approved via webhook: transactionId={}", data.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to approve payment via webhook: transactionId={}, error={}", 
                     data.getTransactionId(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 결제 실패 웹훅 처리
     */
    private void processPaymentFailedWebhook(String payload, ApiProvider provider) {
        PaymentWebhookData data = parsePaymentWebhookData(payload, provider);
        
        try {
            paymentService.failPayment(
                data.getTransactionId(),
                data.getFailureReason(),
                data.getErrorCode()
            );
            
            log.info("Payment failed via webhook: transactionId={}", data.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to process payment failure via webhook: transactionId={}, error={}", 
                     data.getTransactionId(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 결제 취소 웹훅 처리
     */
    private void processPaymentCancelledWebhook(String payload, ApiProvider provider) {
        PaymentWebhookData data = parsePaymentWebhookData(payload, provider);
        
        try {
            // 웹훅을 통한 결제 취소는 시스템에서 자동 처리
            PaymentRecord payment = paymentService.getPaymentByPgTransactionId(data.getPgTransactionId());
            
            paymentService.cancelPayment(
                payment.getTransactionId(),
                payment.getUser().getUserId(),
                "PG사 취소: " + data.getCancelReason()
            );
            
            log.info("Payment cancelled via webhook: transactionId={}", data.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to cancel payment via webhook: transactionId={}, error={}", 
                     data.getTransactionId(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 환불 완료 웹훅 처리
     */
    private void processRefundCompletedWebhook(String payload, ApiProvider provider) {
        RefundWebhookData data = parseRefundWebhookData(payload, provider);
        
        try {
            refundService.completeRefund(data.getRefundId(), data.getRefundedAmount());
            
            log.info("Refund completed via webhook: refundId={}", data.getRefundId());
            
        } catch (Exception e) {
            log.error("Failed to complete refund via webhook: refundId={}, error={}", 
                     data.getRefundId(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 환불 실패 웹훅 처리
     */
    private void processRefundFailedWebhook(String payload, ApiProvider provider) {
        RefundWebhookData data = parseRefundWebhookData(payload, provider);
        
        try {
            // 환불 실패 처리 로직
            refundService.handleRefundFailure(data.getRefundId(), data.getFailureReason());
            
            log.info("Refund failed via webhook: refundId={}, reason={}", 
                    data.getRefundId(), data.getFailureReason());
                    
        } catch (Exception e) {
            log.error("Failed to process refund failure via webhook: refundId={}, error={}", 
                     data.getRefundId(), e.getMessage());
            throw e;
        }
    }
    
    // ===================== 웹훅 검증 및 보안 =====================
    
    /**
     * 웹훅 서명 검증
     */
    private void validateWebhookSignature(ApiProvider provider, String payload, String signature) {
        ExternalApiConfig config = getApiConfig(provider);
        
        if (!signatureValidator.validateSignature(provider, payload, signature, config.getSecretKey())) {
            throw new SystemException("웹훅 서명 검증 실패");
        }
    }
    
    /**
     * 웹훅 ID 추출
     */
    private String extractWebhookId(String payload, ApiProvider provider) {
        try {
            Map<String, Object> data = JsonUtil.fromJson(payload, Map.class);
            
            switch (provider) {
                case IAMPORT:
                    return (String) data.get("imp_uid");
                case TOSS:
                    return (String) data.get("eventId");
                case KAKAO:
                    return (String) data.get("aid");
                case NAVER:
                    return (String) data.get("paymentId");
                default:
                    return UUID.randomUUID().toString();
            }
        } catch (Exception e) {
            log.error("Failed to extract webhook ID from payload: {}", e.getMessage());
            return UUID.randomUUID().toString();
        }
    }
    
    /**
     * 중복 웹훅 확인
     */
    private boolean isDuplicateWebhook(String webhookId, ApiProvider provider) {
        // 메모리 캐시 확인
        if (processingWebhooks.containsKey(webhookId)) {
            return true;
        }
        
        // DB 확인 (최근 10분 내)
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(DUPLICATE_WINDOW_MINUTES);
        return webhookLogRepository.existsByWebhookIdAndProviderAndCreatedAtAfter(
            webhookId, provider, cutoff
        );
    }
    
    /**
     * 웹훅 처리 중 마킹
     */
    private void markWebhookAsProcessing(String webhookId, Long logId) {
        processingWebhooks.put(webhookId, logId);
        
        // 5분 후 자동 해제 (메모리 누수 방지)
        CompletableFuture.delayedExecutor(WEBHOOK_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .execute(() -> unmarkWebhookAsProcessing(webhookId));
    }
    
    /**
     * 웹훅 처리 중 마킹 해제
     */
    private void unmarkWebhookAsProcessing(String webhookId) {
        processingWebhooks.remove(webhookId);
    }
    
    /**
     * 웹훅 상태 업데이트
     */
    private void updateWebhookStatus(WebhookLog webhookLog, WebhookStatus status, String errorMessage) {
        webhookLog.setStatus(status);
        webhookLog.setErrorMessage(errorMessage);
        
        if (status == WebhookStatus.PROCESSED) {
            webhookLog.setProcessedAt(LocalDateTime.now());
        } else if (status == WebhookStatus.FAILED || status == WebhookStatus.ABANDONED) {
            webhookLog.setErrorMessage(errorMessage);
        }
        
        webhookLogRepository.save(webhookLog);
    }
    
    /**
     * 웹훅 재시도 스케줄링
     */
    @Async
    public void scheduleWebhookRetry(WebhookLog webhookLog) {
        if (webhookLog.getRetryCount() < MAX_RETRY_COUNT) {
            try {
                // 지수 백오프로 재시도 지연 (1분, 2분, 4분, 8분, 16분)
                long delaySeconds = (long) Math.pow(2, webhookLog.getRetryCount()) * 60;
                Thread.sleep(delaySeconds * 1000);
                
                retryWebhook(webhookLog.getLogId());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Webhook retry scheduling interrupted: webhookLogId={}", webhookLog.getLogId());
            } catch (Exception e) {
                log.error("Failed to schedule webhook retry: webhookLogId={}, error={}", 
                         webhookLog.getLogId(), e.getMessage());
            }
        } else {
            log.warn("Max retry count reached, abandoning webhook: webhookLogId={}", 
                    webhookLog.getLogId());
        }
    }
    
    // ===================== 설정 및 유틸리티 =====================
    
    /**
     * API 설정 조회
     */
    @Cacheable(value = CACHE_WEBHOOK_CONFIG, key = "#provider")
    public ExternalApiConfig getApiConfig(ApiProvider provider) {
        return apiConfigRepository.findByProvider(provider)
            .orElseThrow(() -> new SystemException("API 설정을 찾을 수 없습니다: " + provider));
    }
    
    /**
     * 결제 웹훅 데이터 파싱
     */
    private PaymentWebhookData parsePaymentWebhookData(String payload, ApiProvider provider) {
        return PaymentWebhookDataParser.parse(payload, provider);
    }
    
    /**
     * 환불 웹훅 데이터 파싱
     */
    private RefundWebhookData parseRefundWebhookData(String payload, ApiProvider provider) {
        return RefundWebhookDataParser.parse(payload, provider);
    }
    
    /**
     * 웹훅 처리 가능 여부 확인
     */
    public boolean canProcessWebhook(WebhookType webhookType, ApiProvider provider) {
        try {
            // API 설정 존재 여부 확인
            ExternalApiConfig config = getApiConfig(provider);
            
            // 활성화된 설정인지 확인
            if (!config.isEnabled()) {
                log.warn("API provider is disabled: {}", provider);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Cannot process webhook: type={}, provider={}, error={}", 
                     webhookType, provider, e.getMessage());
            return false;
        }
    }
    
    /**
     * 처리 중인 웹훅 개수 조회
     */
    public int getProcessingWebhookCount() {
        return processingWebhooks.size();
    }
    
    /**
     * 특정 웹훅이 처리 중인지 확인
     */
    public boolean isWebhookProcessing(String webhookId) {
        return processingWebhooks.containsKey(webhookId);
    }
}
```

## 🔧 웹훅 데이터 파서 클래스

```java
/**
 * 결제 웹훅 데이터 파서
 */
public class PaymentWebhookDataParser {
    
    public static PaymentWebhookData parse(String payload, ApiProvider provider) {
        try {
            Map<String, Object> data = JsonUtil.fromJson(payload, Map.class);
            
            switch (provider) {
                case IAMPORT:
                    return parseIamportPaymentData(data);
                case TOSS:
                    return parseTossPaymentData(data);
                case KAKAO:
                    return parseKakaoPaymentData(data);
                case NAVER:
                    return parseNaverPaymentData(data);
                default:
                    throw new SystemException("지원하지 않는 결제 제공자입니다: " + provider);
            }
        } catch (Exception e) {
            throw new SystemException("결제 웹훅 데이터 파싱 실패: " + e.getMessage());
        }
    }
    
    private static PaymentWebhookData parseIamportPaymentData(Map<String, Object> data) {
        return PaymentWebhookData.builder()
            .transactionId((String) data.get("merchant_uid"))
            .pgTransactionId((String) data.get("imp_uid"))
            .approvalNumber((String) data.get("apply_num"))
            .amount(((Number) data.get("amount")).longValue())
            .status((String) data.get("status"))
            .failureReason((String) data.get("fail_reason"))
            .errorCode((String) data.get("error_code"))
            .cancelReason((String) data.get("cancel_reason"))
            .build();
    }
    
    private static PaymentWebhookData parseTossPaymentData(Map<String, Object> data) {
        return PaymentWebhookData.builder()
            .transactionId((String) data.get("orderId"))
            .pgTransactionId((String) data.get("paymentKey"))
            .approvalNumber((String) data.get("approvalNo"))
            .amount(((Number) data.get("totalAmount")).longValue())
            .status((String) data.get("status"))
            .failureReason((String) data.get("failReason"))
            .errorCode((String) data.get("errorCode"))
            .cancelReason((String) data.get("cancelReason"))
            .build();
    }
    
    private static PaymentWebhookData parseKakaoPaymentData(Map<String, Object> data) {
        return PaymentWebhookData.builder()
            .transactionId((String) data.get("partner_order_id"))
            .pgTransactionId((String) data.get("tid"))
            .approvalNumber((String) data.get("aid"))
            .amount(((Number) data.get("amount")).longValue())
            .status((String) data.get("status"))
            .failureReason((String) data.get("fail_reason"))
            .errorCode((String) data.get("error_code"))
            .build();
    }
    
    private static PaymentWebhookData parseNaverPaymentData(Map<String, Object> data) {
        return PaymentWebhookData.builder()
            .transactionId((String) data.get("paymentId"))
            .pgTransactionId((String) data.get("paymentId"))
            .approvalNumber((String) data.get("approvalNo"))
            .amount(((Number) data.get("totalPayAmount")).longValue())
            .status((String) data.get("detail"))
            .build();
    }
}

/**
 * 환불 웹훅 데이터 파서
 */
public class RefundWebhookDataParser {
    
    public static RefundWebhookData parse(String payload, ApiProvider provider) {
        try {
            Map<String, Object> data = JsonUtil.fromJson(payload, Map.class);
            
            switch (provider) {
                case IAMPORT:
                    return parseIamportRefundData(data);
                case TOSS:
                    return parseTossRefundData(data);
                case KAKAO:
                    return parseKakaoRefundData(data);
                default:
                    throw new SystemException("지원하지 않는 환불 제공자입니다: " + provider);
            }
        } catch (Exception e) {
            throw new SystemException("환불 웹훅 데이터 파싱 실패: " + e.getMessage());
        }
    }
    
    // 각 제공자별 파싱 로직 구현...
}
```

## 📊 연동 참고사항

### step6-5c2_webhook_monitoring_statistics.md 연동점
1. **통계 수집**: 웹훅 처리 결과 통계화
2. **모니터링**: 실시간 웹훅 상태 추적
3. **로그 관리**: 웹훅 로그 정리 및 분석
4. **알림**: 웹훅 실패 시 알림 발송

### 주요 의존성
- **WebhookLogRepository**: 웹훅 로그 관리
- **ExternalApiConfigRepository**: 외부 API 설정
- **PaymentService**: 결제 처리 연동
- **PaymentRefundService**: 환불 처리 연동

### 보안 고려사항
1. **서명 검증**: 각 제공자별 서명 알고리즘 적용
2. **중복 방지**: 메모리 + DB 기반 중복 확인
3. **타임아웃**: 처리 시간 제한으로 리소스 보호
4. **재시도**: 지수 백오프로 안정성 확보

---
**연관 파일**: `step6-5c2_webhook_monitoring_statistics.md`
**구현 우선순위**: HIGH (결제 시스템 핵심)
**예상 개발 기간**: 3-4일