# 9-5단계: 결제 및 알림 테스트 (트랜잭션 중심) - 참고파일 정리

> 9-5단계 구현을 위한 종합 참고 자료 정리
> 생성일: 2025-08-27
> 단계: 9-5 (결제 및 알림 테스트 설계 준비)
> 목적: 트랜잭션 중심 결제 및 알림 시스템 테스트 코드 작성

---

## 📋 9-5단계 구현 목표

### 🎯 테스트 범위
- **결제 시스템**: 트랜잭션 무결성, PG사 연동, 환불 처리
- **알림 시스템**: FCM 푸시, 개인 알림, 공지사항, 배너
- **통합 테스트**: 결제-알림 연동, 이벤트 기반 알림 발송
- **보안 테스트**: 결제 보안, 개인정보 보호, 트랜잭션 검증

### 📊 예상 테스트 케이스 수
- **결제 테스트**: ~60개 (기본 35개 + 보안 25개)
- **알림 테스트**: ~45개 (개인 20개 + 시스템 25개)
- **통합 테스트**: ~25개 (이벤트 연동)
- **총합**: ~130개 테스트 케이스

---

## 💳 결제 시스템 참고 파일

### 1. Entity 설계 참고

#### step4-4b1_payment_entities.md
**핵심 엔티티 4개**:
- `PaymentRecord`: 결제 기록 마스터 (결제 상태, 금액, PG 정보)
- `PaymentDetail`: 결제 상세 (PG사 응답, 검증 정보, 보안)  
- `PaymentItem`: 결제 항목 (상품별 세부 정보, 환불 관리)
- `PaymentRefund`: 환불 처리 (환불 상태, 트랜잭션 추적)

**한국 특화 기능**:
```java
// 결제 방법 한글 표시
public String getPaymentMethodKorean() {
    return switch (paymentMethod) {
        case "CARD" -> "신용카드";
        case "KAKAOPAY" -> "카카오페이";
        case "NAVERPAY" -> "네이버페이";
        case "TOSS" -> "토스";
        // ...
    };
}

// 실제 결제 금액 계산
public BigDecimal getActualAmount() {
    BigDecimal actual = totalAmount;
    if (discountAmount != null) {
        actual = actual.subtract(discountAmount);
    }
    return actual;
}
```

### 2. Repository 참고

#### step5-4d_payment_repositories.md
**주요 Repository 4개**:
- `PaymentRecordRepository`: 결제 기록 조회, 통계, 검색
- `PaymentDetailRepository`: PG사 정보, 보안 감사
- `PaymentItemRepository`: 상품 판매 통계, 인기 항목
- `PaymentRefundRepository`: 환불 처리, 환불률 분석

**핵심 쿼리**:
```java
// 결제 동향 분석
@Query("SELECT new PaymentTrendProjection(" +
       "DATE(pr.paymentDate), COUNT(pr), SUM(pr.totalAmount), AVG(pr.totalAmount)) " +
       "FROM PaymentRecord pr " +
       "WHERE pr.paymentDate BETWEEN :startDate AND :endDate " +
       "AND pr.paymentStatus = 'COMPLETED' " +
       "GROUP BY DATE(pr.paymentDate)")
List<PaymentTrendProjection> findPaymentTrends(...);

// 의심 거래 탐지
@Query("SELECT pd FROM PaymentDetail pd " +
       "WHERE pd.fraudScore > :fraudThreshold " +
       "OR pr.totalAmount > :amountThreshold")
List<PaymentDetail> findSuspiciousTransactions(...);
```

### 3. Service 참고

#### step6-5a_payment_service.md (일부)
**PaymentService 핵심 기능**:
```java
@Service
@Transactional(readOnly = true)
public class PaymentService {
    
    // 결제 요청 처리
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentRecord processPayment(Long userId, PaymentRequest request) {
        // 1. 사용자 확인
        // 2. 결제 요청 검증
        // 3. 거래 ID 생성
        // 4. 결제 기록 생성
        // 5. PG사 결제 요청
        // 6. 이벤트 발행
    }
    
    // 결제 승인 처리
    @Transactional
    public PaymentRecord approvePayment(String transactionId, 
                                      String pgTransactionId, 
                                      String approvalNumber) {
        // PG사 검증 + 상태 업데이트
    }
}
```

---

## 🔔 알림 시스템 참고 파일

### 1. Entity 설계 참고

#### step4-4b2a_personal_notification_entities.md
**Notification 엔티티 핵심**:
```java
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {
    
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;
    
    private String title;
    private String content;
    private String actionUrl;
    private String actionData;
    
    // 읽음 상태
    private boolean isRead = false;
    private LocalDateTime readAt;
    
    // 푸시 알림 정보
    private boolean isPushSent = false;
    private String fcmMessageId;
    private boolean pushSuccess = false;
    
    // 통계 정보
    private Integer clickCount = 0;
    private LocalDateTime firstClickedAt;
    
    // 비즈니스 메서드
    public void markAsRead() { ... }
    public void recordClick() { ... }
    public void markPushSent(String fcmMessageId) { ... }
}
```

### 2. Repository 참고

#### step5-4e_notification_repositories.md
**NotificationRepository 주요 기능**:
```java
// 사용자별 미읽음 알림
@Query("SELECT n FROM Notification n " +
       "WHERE n.userId = :userId AND n.isRead = false " +
       "ORDER BY n.createdAt DESC")
List<Notification> findUnreadByUserId(@Param("userId") Long userId);

// 알림 통계
@Query("SELECT n.notificationType, COUNT(n), " +
       "SUM(CASE WHEN n.isRead = true THEN 1 ELSE 0 END) as readCount " +
       "FROM Notification n " +
       "WHERE n.createdAt BETWEEN :startDate AND :endDate " +
       "GROUP BY n.notificationType")
List<Object[]> findNotificationStatistics(...);
```

### 3. Service 참고

#### step6-5d_notification_service.md (일부)
**NotificationService 핵심 기능**:
```java
@Service
@Transactional(readOnly = true)
public class NotificationService {
    
    // 개인 알림 발송
    @Transactional
    public Notification sendNotification(Long userId, NotificationType type,
                                       String title, String content, 
                                       String actionUrl, String actionData) {
        // 1. 사용자 확인
        // 2. 알림 생성
        // 3. 푸시 알림 발송 (비동기)
        // 4. 이메일 알림 (중요 알림만)
        // 5. 이벤트 발행
    }
    
    // 배치 알림 발송
    @Async
    public CompletableFuture<Integer> sendBatchNotifications(
            List<Long> userIds, NotificationType type,
            String title, String content, String actionUrl) {
        // 배치 크기로 분할 처리
    }
}
```

---

## 🧪 테스트 설계 가이드

### 1. 결제 테스트 구조

#### 기본 결제 테스트 (35개)
```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    // 1. 결제 요청 테스트 (10개)
    @Test
    void 결제_요청_성공() { ... }
    @Test 
    void 결제_요청_실패_사용자없음() { ... }
    @Test
    void 결제_요청_실패_금액부족() { ... }
    
    // 2. 결제 승인 테스트 (8개)
    @Test
    void 결제_승인_성공() { ... }
    @Test
    void 결제_승인_실패_PG검증실패() { ... }
    
    // 3. 결제 상태 관리 테스트 (7개)
    @Test
    void 결제_상태_변경_성공() { ... }
    
    // 4. 환불 처리 테스트 (10개)
    @Test
    void 환불_요청_성공() { ... }
    @Test
    void 부분_환불_처리() { ... }
}
```

#### 결제 보안 테스트 (25개)
```java
@ExtendWith(MockitoExtension.class)
class PaymentSecurityTest {

    // 1. 중복 결제 방지 (5개)
    @Test
    void 중복_결제_차단() { ... }
    
    // 2. 금액 변조 탐지 (8개) 
    @Test
    void 결제_금액_변조_탐지() { ... }
    
    // 3. PG사 검증 (7개)
    @Test
    void PG사_응답_검증() { ... }
    
    // 4. 개인정보 보호 (5개)
    @Test
    void 카드정보_마스킹() { ... }
}
```

### 2. 알림 테스트 구조

#### 개인 알림 테스트 (20개)
```java
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    // 1. 알림 발송 테스트 (8개)
    @Test
    void 개인_알림_발송_성공() { ... }
    @Test
    void 알림_템플릿_렌더링() { ... }
    
    // 2. 읽음 상태 관리 (6개)
    @Test
    void 알림_읽음_처리() { ... }
    
    // 3. 푸시 알림 (6개)
    @Test
    void FCM_푸시_발송() { ... }
}
```

#### 시스템 알림 테스트 (25개)
```java
@ExtendWith(MockitoExtension.class)
class SystemNotificationTest {

    // 1. 공지사항 관리 (10개)
    @Test
    void 공지사항_생성() { ... }
    
    // 2. 배너 관리 (8개)
    @Test
    void 배너_노출_조건() { ... }
    
    // 3. 팝업 관리 (7개)
    @Test
    void 팝업_스케줄링() { ... }
}
```

### 3. 통합 테스트 구조

#### 결제-알림 연동 테스트 (25개)
```java
@SpringBootTest
@Transactional
class PaymentNotificationIntegrationTest {

    // 1. 결제 성공 알림 (8개)
    @Test
    void 결제_성공시_알림_발송() {
        // Given: 결제 요청
        // When: 결제 처리
        // Then: 성공 알림 발송 확인
    }
    
    // 2. 결제 실패 알림 (7개)
    @Test
    void 결제_실패시_알림_발송() { ... }
    
    // 3. 환불 알림 (10개)
    @Test
    void 환불_처리시_알림_발송() { ... }
}
```

---

## 🔧 테스트 유틸리티

### 1. 결제 테스트 헬퍼
```java
@Component
public class PaymentTestHelper {
    
    public PaymentRequest createValidPaymentRequest() {
        return PaymentRequest.builder()
            .totalAmount(new BigDecimal("10000"))
            .paymentMethod(PaymentMethod.CARD)
            .paymentGateway(PaymentGateway.TOSS)
            .description("테스트 결제")
            .build();
    }
    
    public PaymentRecord createCompletedPayment() { ... }
    
    public void mockPGResponse(PaymentGateway gateway, boolean success) { ... }
}
```

### 2. 알림 테스트 헬퍼
```java
@Component
public class NotificationTestHelper {
    
    public NotificationTemplate createPaymentSuccessTemplate() {
        return NotificationTemplate.builder()
            .type(NotificationType.PAYMENT_SUCCESS)
            .titleTemplate("결제가 완료되었습니다")
            .contentTemplate("{{amount}}원이 결제되었습니다")
            .build();
    }
    
    public void verifyPushNotificationSent(String fcmToken) { ... }
    
    public void verifyNotificationCount(Long userId, int expected) { ... }
}
```

---

## 📊 예상 파일 구조

### 생성될 파일 목록
1. **step9-5a_payment_basic_test.md** (35개) - 기본 결제 테스트
2. **step9-5b_payment_security_test.md** (25개) - 결제 보안 테스트  
3. **step9-5c_notification_personal_test.md** (20개) - 개인 알림 테스트
4. **step9-5d_notification_system_test.md** (25개) - 시스템 알림 테스트
5. **step9-5e_payment_notification_integration_test.md** (25개) - 통합 테스트

### 총 예상 라인 수
- **테스트 코드**: ~8,000 라인
- **테스트 헬퍼**: ~1,500 라인
- **문서화**: ~2,000 라인
- **총합**: ~11,500 라인

---

## 🎯 핵심 테스트 시나리오

### 결제 트랜잭션 테스트
1. **정상 결제 플로우**: 요청 → 검증 → PG 연동 → 승인 → 알림
2. **결제 실패 복구**: 네트워크 오류 → 재시도 → 상태 복구
3. **동시성 제어**: 동일 사용자 중복 결제 방지
4. **환불 처리**: 전체/부분 환불 → 상태 변경 → 알림

### 알림 시스템 테스트  
1. **실시간 알림**: FCM 푸시 → 수신 확인 → 읽음 처리
2. **배치 알림**: 대량 발송 → 실패 재시도 → 통계 집계
3. **템플릿 시스템**: 동적 템플릿 → 데이터 바인딩 → 렌더링
4. **알림 설정**: 사용자 설정 → 필터링 → 개인화

---

**참고파일 정리 완료**: 9-5단계 구현을 위한 종합 참고 자료 준비됨
**다음 단계**: 실제 테스트 케이스 설계 시작
**예상 완료**: 130개 테스트 케이스 + 통합 테스트 완료