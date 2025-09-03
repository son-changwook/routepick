# Step 3-2c: 태그 시스템 및 결제 예외 클래스

> TagException, PaymentException 도메인별 예외 클래스 구현  
> 생성일: 2025-08-20  
> 분할: step3-2_domain_exceptions.md → 태그/결제 도메인 추출  
> 기반 분석: step3-1_exception_base.md

---

## 🎯 태그 시스템 및 결제 예외 클래스 개요

### 구현 원칙
- **BaseException 상속**: 공통 기능 활용 (로깅, 마스킹, 추적)
- **도메인 특화**: 각 도메인 비즈니스 로직에 특화된 생성자 및 메서드
- **팩토리 메서드**: 자주 사용되는 예외의 간편 생성
- **컨텍스트 정보**: 도메인별 추가 정보 포함
- **보안 강화**: 민감정보 보호 및 적절한 로깅 레벨

### 2개 도메인 예외 클래스
```
TagException         # 태그 시스템 (추천, 분류, 검증)
PaymentException     # 결제 시스템 (결제, 환불, 검증)
```

---

## 🏷️ TagException (태그 시스템 관련)

### 클래스 구조
```java
package com.routepick.exception.tag;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 태그 시스템 관련 예외 클래스
 * 
 * 주요 기능:
 * - 태그 관리 예외 (8가지 TagType)
 * - 사용자 선호 태그 예외
 * - 루트 태그 예외
 * - 추천 시스템 예외
 * - 태그 검증 예외
 */
@Getter
public class TagException extends BaseException {
    
    private final Long tagId;               // 관련 태그 ID
    private final String tagName;          // 관련 태그명
    private final String tagType;          // 관련 태그 타입 (8가지)
    private final String preferenceLevel;  // 선호도 레벨 (LOW, MEDIUM, HIGH)
    private final String skillLevel;       // 숙련도 레벨 (BEGINNER, INTERMEDIATE, ADVANCED, EXPERT)
    private final Double relevanceScore;   // 연관성 점수 (0.0-1.0)
    private final Long userId;             // 관련 사용자 ID (추천 시)
    
    // 기본 생성자
    public TagException(ErrorCode errorCode) {
        super(errorCode);
        this.tagId = null;
        this.tagName = null;
        this.tagType = null;
        this.preferenceLevel = null;
        this.skillLevel = null;
        this.relevanceScore = null;
        this.userId = null;
    }
    
    // 태그 ID 포함 생성자
    public TagException(ErrorCode errorCode, Long tagId) {
        super(errorCode);
        this.tagId = tagId;
        this.tagName = null;
        this.tagType = null;
        this.preferenceLevel = null;
        this.skillLevel = null;
        this.relevanceScore = null;
        this.userId = null;
    }
    
    // 파라미터화된 메시지 생성자
    public TagException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
        this.tagId = null;
        this.tagName = null;
        this.tagType = null;
        this.preferenceLevel = null;
        this.skillLevel = null;
        this.relevanceScore = null;
        this.userId = null;
    }
    
    // 원인 예외 포함 생성자
    public TagException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
        this.tagId = null;
        this.tagName = null;
        this.tagType = null;
        this.preferenceLevel = null;
        this.skillLevel = null;
        this.relevanceScore = null;
        this.userId = null;
    }
    
    // 상세 정보 포함 생성자
    private TagException(ErrorCode errorCode, Long tagId, String tagName, String tagType, 
                        String preferenceLevel, String skillLevel, Double relevanceScore, Long userId) {
        super(errorCode);
        this.tagId = tagId;
        this.tagName = tagName;
        this.tagType = tagType;
        this.preferenceLevel = preferenceLevel;
        this.skillLevel = skillLevel;
        this.relevanceScore = relevanceScore;
        this.userId = userId;
    }
    
    // ========== 팩토리 메서드 (태그 관리) ==========
    
    /**
     * 태그를 찾을 수 없음
     */
    public static TagException tagNotFound(Long tagId) {
        return new TagException(ErrorCode.TAG_NOT_FOUND, tagId, null, null, null, null, null, null);
    }
    
    /**
     * 태그명으로 찾을 수 없음
     */
    public static TagException tagNotFoundByName(String tagName) {
        return new TagException(ErrorCode.TAG_NOT_FOUND, null, tagName, null, null, null, null, null);
    }
    
    /**
     * 이미 존재하는 태그
     */
    public static TagException tagAlreadyExists(String tagName) {
        return new TagException(ErrorCode.TAG_ALREADY_EXISTS, null, tagName, null, null, null, null, null);
    }
    
    /**
     * 올바르지 않은 태그 타입
     */
    public static TagException invalidTagType(String tagType) {
        return new TagException(ErrorCode.TAG_TYPE_INVALID, null, null, tagType, null, null, null, null);
    }
    
    /**
     * 사용자가 선택할 수 없는 태그
     */
    public static TagException tagNotUserSelectable(Long tagId) {
        return new TagException(ErrorCode.TAG_NOT_USER_SELECTABLE, tagId, null, null, null, null, null, null);
    }
    
    /**
     * 루트에 사용할 수 없는 태그
     */
    public static TagException tagNotRouteTaggable(Long tagId) {
        return new TagException(ErrorCode.TAG_NOT_ROUTE_TAGGABLE, tagId, null, null, null, null, null, null);
    }
    
    // ========== 선호도/숙련도 관련 팩토리 메서드 ==========
    
    /**
     * 올바르지 않은 선호도 레벨
     */
    public static TagException invalidPreferenceLevel(String preferenceLevel) {
        return new TagException(ErrorCode.INVALID_PREFERENCE_LEVEL, null, null, null, preferenceLevel, null, null, null);
    }
    
    /**
     * 올바르지 않은 숙련도 레벨
     */
    public static TagException invalidSkillLevel(String skillLevel) {
        return new TagException(ErrorCode.INVALID_SKILL_LEVEL, null, null, null, null, skillLevel, null, null);
    }
    
    // ========== 추천 시스템 관련 팩토리 메서드 ==========
    
    /**
     * 추천 결과를 찾을 수 없음
     */
    public static TagException recommendationNotFound(Long userId) {
        return new TagException(ErrorCode.RECOMMENDATION_NOT_FOUND, null, null, null, null, null, null, userId);
    }
    
    /**
     * 추천 계산 실패
     */
    public static TagException recommendationCalculationFailed(Long userId, Throwable cause) {
        TagException exception = new TagException(ErrorCode.RECOMMENDATION_CALCULATION_FAILED, cause);
        exception.userId = userId;
        return exception;
    }
    
    /**
     * 사용자 선호도 미설정
     */
    public static TagException insufficientUserPreferences(Long userId) {
        return new TagException(ErrorCode.INSUFFICIENT_USER_PREFERENCES, null, null, null, null, null, null, userId);
    }
    
    // ========== 8가지 TagType 검증 메서드 ==========
    
    /**
     * 유효한 태그 타입 검증
     */
    public static void validateTagType(String tagType) {
        if (tagType == null || tagType.trim().isEmpty()) {
            throw new TagException(ErrorCode.REQUIRED_FIELD_MISSING, "tagType");
        }
        
        if (!isValidTagType(tagType)) {
            throw invalidTagType(tagType);
        }
    }
    
    /**
     * 태그 타입 유효성 확인 (8가지)
     */
    public static boolean isValidTagType(String tagType) {
        if (tagType == null) return false;
        
        return tagType.matches("^(STYLE|FEATURE|TECHNIQUE|DIFFICULTY|MOVEMENT|HOLD_TYPE|WALL_ANGLE|OTHER)$");
    }
    
    /**
     * 선호도 레벨 검증
     */
    public static void validatePreferenceLevel(String preferenceLevel) {
        if (preferenceLevel == null || preferenceLevel.trim().isEmpty()) {
            throw new TagException(ErrorCode.REQUIRED_FIELD_MISSING, "preferenceLevel");
        }
        
        if (!preferenceLevel.matches("^(LOW|MEDIUM|HIGH)$")) {
            throw invalidPreferenceLevel(preferenceLevel);
        }
    }
    
    /**
     * 숙련도 레벨 검증
     */
    public static void validateSkillLevel(String skillLevel) {
        if (skillLevel == null || skillLevel.trim().isEmpty()) {
            throw new TagException(ErrorCode.REQUIRED_FIELD_MISSING, "skillLevel");
        }
        
        if (!skillLevel.matches("^(BEGINNER|INTERMEDIATE|ADVANCED|EXPERT)$")) {
            throw invalidSkillLevel(skillLevel);
        }
    }
    
    /**
     * 연관성 점수 검증 (0.0-1.0)
     */
    public static void validateRelevanceScore(Double relevanceScore) {
        if (relevanceScore == null) {
            throw new TagException(ErrorCode.REQUIRED_FIELD_MISSING, "relevanceScore");
        }
        
        if (relevanceScore < 0.0 || relevanceScore > 1.0) {
            throw new TagException(ErrorCode.INVALID_INPUT_FORMAT, "relevanceScore must be between 0.0 and 1.0");
        }
    }
    
    // ========== 추천 알고리즘 관련 메서드 ==========
    
    /**
     * 선호도 레벨을 가중치로 변환
     */
    public static double preferenceToWeight(String preferenceLevel) {
        return switch (preferenceLevel.toUpperCase()) {
            case "HIGH" -> 1.0;
            case "MEDIUM" -> 0.7;
            case "LOW" -> 0.3;
            default -> 0.0;
        };
    }
    
    /**
     * 숙련도 레벨을 점수로 변환
     */
    public static int skillToScore(String skillLevel) {
        return switch (skillLevel.toUpperCase()) {
            case "BEGINNER" -> 1;
            case "INTERMEDIATE" -> 2;
            case "ADVANCED" -> 3;
            case "EXPERT" -> 4;
            default -> 0;
        };
    }
    
    /**
     * 태그 매칭 점수 계산
     */
    public static double calculateTagMatchScore(String userPreferenceLevel, Double routeRelevanceScore) {
        if (userPreferenceLevel == null || routeRelevanceScore == null) {
            return 0.0;
        }
        
        double weight = preferenceToWeight(userPreferenceLevel);
        return routeRelevanceScore * weight * 100; // 0-100 점수로 변환
    }
    
    /**
     * 태그 타입별 카테고리 확인
     */
    public static String getTagCategory(String tagType) {
        return switch (tagType.toUpperCase()) {
            case "STYLE", "TECHNIQUE", "MOVEMENT" -> "CLIMBING_STYLE";
            case "HOLD_TYPE", "WALL_ANGLE", "FEATURE" -> "PHYSICAL_CHARACTERISTICS";
            case "DIFFICULTY" -> "DIFFICULTY_ASSESSMENT";
            case "OTHER" -> "MISCELLANEOUS";
            default -> "UNKNOWN";
        };
    }
}
```

---

## 💳 PaymentException (결제 관련)

### 클래스 구조
```java
package com.routepick.exception.payment;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 결제 관련 예외 클래스
 * 
 * 주요 기능:
 * - 결제 처리 예외
 * - 환불 처리 예외
 * - 한국 결제 시스템 예외 (카드/가상계좌)
 * - 결제 검증 예외
 * - 금액 관련 예외
 */
@Getter
public class PaymentException extends BaseException {
    
    private final Long paymentId;        // 관련 결제 ID
    private final Long userId;           // 관련 사용자 ID
    private final Long amount;           // 관련 금액 (원)
    private final String paymentMethod;  // 결제 방법 (CARD, VIRTUAL_ACCOUNT, BANK_TRANSFER)
    private final String transactionId;  // 거래 ID
    
    // 기본 생성자
    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
        this.paymentId = null;
        this.userId = null;
        this.amount = null;
        this.paymentMethod = null;
        this.transactionId = null;
    }
    
    // 결제 ID 포함 생성자
    public PaymentException(ErrorCode errorCode, Long paymentId) {
        super(errorCode);
        this.paymentId = paymentId;
        this.userId = null;
        this.amount = null;
        this.paymentMethod = null;
        this.transactionId = null;
    }
    
    // 금액 포함 생성자
    public PaymentException(ErrorCode errorCode, Long amount, String paymentMethod) {
        super(errorCode);
        this.paymentId = null;
        this.userId = null;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.transactionId = null;
    }
    
    // 파라미터화된 메시지 생성자
    public PaymentException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
        this.paymentId = null;
        this.userId = null;
        this.amount = null;
        this.paymentMethod = null;
        this.transactionId = null;
    }
    
    // 원인 예외 포함 생성자
    public PaymentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
        this.paymentId = null;
        this.userId = null;
        this.amount = null;
        this.paymentMethod = null;
        this.transactionId = null;
    }
    
    // 상세 정보 포함 생성자
    private PaymentException(ErrorCode errorCode, Long paymentId, Long userId, Long amount, 
                           String paymentMethod, String transactionId) {
        super(errorCode);
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;
    }
    
    // ========== 팩토리 메서드 (결제 관리) ==========
    
    /**
     * 결제 정보를 찾을 수 없음
     */
    public static PaymentException paymentNotFound(Long paymentId) {
        return new PaymentException(ErrorCode.PAYMENT_NOT_FOUND, paymentId, null, null, null, null);
    }
    
    /**
     * 이미 처리된 결제
     */
    public static PaymentException paymentAlreadyProcessed(Long paymentId) {
        return new PaymentException(ErrorCode.PAYMENT_ALREADY_PROCESSED, paymentId, null, null, null, null);
    }
    
    /**
     * 결제 실패
     */
    public static PaymentException paymentFailed(Long userId, Long amount, String paymentMethod, Throwable cause) {
        PaymentException exception = new PaymentException(ErrorCode.PAYMENT_FAILED, cause);
        exception.userId = userId;
        exception.amount = amount;
        exception.paymentMethod = paymentMethod;
        return exception;
    }
    
    /**
     * 결제 취소
     */
    public static PaymentException paymentCancelled(Long paymentId, String reason) {
        return new PaymentException(ErrorCode.PAYMENT_CANCELLED, paymentId, reason);
    }
    
    /**
     * 유효하지 않은 결제 방법
     */
    public static PaymentException invalidPaymentMethod(String paymentMethod) {
        return new PaymentException(ErrorCode.INVALID_PAYMENT_METHOD, null, null, null, paymentMethod, null);
    }
    
    /**
     * 결제 금액 불일치
     */
    public static PaymentException amountMismatch(Long expectedAmount, Long actualAmount) {
        return new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, expectedAmount, actualAmount);
    }
    
    // ========== 환불 관련 팩토리 메서드 ==========
    
    /**
     * 환불 불가능
     */
    public static PaymentException refundNotAvailable(Long paymentId) {
        return new PaymentException(ErrorCode.REFUND_NOT_AVAILABLE, paymentId, null, null, null, null);
    }
    
    /**
     * 환불 기간 만료
     */
    public static PaymentException refundPeriodExpired(Long paymentId) {
        return new PaymentException(ErrorCode.REFUND_PERIOD_EXPIRED, paymentId, null, null, null, null);
    }
    
    /**
     * 환불 가능 금액 초과
     */
    public static PaymentException refundAmountExceeded(Long paymentId, Long requestedAmount, Long availableAmount) {
        return new PaymentException(ErrorCode.REFUND_AMOUNT_EXCEEDED, paymentId, requestedAmount, availableAmount);
    }
    
    // ========== 한국 결제 시스템 검증 메서드 ==========
    
    /**
     * 유효한 결제 방법 검증
     */
    public static void validatePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new PaymentException(ErrorCode.REQUIRED_FIELD_MISSING, "paymentMethod");
        }
        
        if (!isValidKoreanPaymentMethod(paymentMethod)) {
            throw invalidPaymentMethod(paymentMethod);
        }
    }
    
    /**
     * 한국 결제 방법 유효성 확인
     */
    public static boolean isValidKoreanPaymentMethod(String paymentMethod) {
        if (paymentMethod == null) return false;
        
        return paymentMethod.matches("^(CARD|VIRTUAL_ACCOUNT|BANK_TRANSFER|MOBILE_PAYMENT)$");
    }
    
    /**
     * 결제 금액 검증 (최소/최대 금액)
     */
    public static void validatePaymentAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new PaymentException(ErrorCode.REQUIRED_FIELD_MISSING, "amount");
        }
        
        // 최소 결제 금액: 100원
        if (amount < 100) {
            throw new PaymentException(ErrorCode.INVALID_INPUT_FORMAT, "Minimum payment amount is 100 KRW");
        }
        
        // 최대 결제 금액: 10,000,000원 (1천만원)
        if (amount > 10_000_000) {
            throw new PaymentException(ErrorCode.INVALID_INPUT_FORMAT, "Maximum payment amount is 10,000,000 KRW");
        }
    }
    
    /**
     * 카드번호 형식 검증 (마스킹된 카드번호)
     */
    public static boolean isValidMaskedCardNumber(String maskedCardNumber) {
        if (maskedCardNumber == null) return false;
        
        // 마스킹된 카드번호 형식: 1234-****-****-5678
        return maskedCardNumber.matches("^\\d{4}-\\*{4}-\\*{4}-\\d{4}$");
    }
    
    /**
     * 가상계좌 번호 형식 검증
     */
    public static boolean isValidVirtualAccountNumber(String accountNumber) {
        if (accountNumber == null) return false;
        
        // 한국 은행 계좌번호 형식 (10-14자리 숫자)
        return accountNumber.matches("^\\d{10,14}$");
    }
    
    // ========== 편의 메서드 ==========
    
    /**
     * 금액을 원화 형식으로 포맷팅
     */
    public static String formatKoreanWon(Long amount) {
        if (amount == null) return "0원";
        
        return String.format("%,d원", amount);
    }
    
    /**
     * 결제 상태 확인
     */
    public static boolean isValidPaymentStatus(String status) {
        if (status == null) return false;
        
        return status.matches("^(PENDING|PROCESSING|COMPLETED|FAILED|CANCELLED|REFUNDED)$");
    }
    
    /**
     * 환불 가능 여부 확인 (결제 후 7일 이내)
     */
    public static boolean isRefundable(java.time.LocalDateTime paymentDate) {
        if (paymentDate == null) return false;
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.Duration duration = java.time.Duration.between(paymentDate, now);
        
        return duration.toDays() <= 7; // 7일 이내 환불 가능
    }
    
    /**
     * 결제 수수료 계산 (한국 기준)
     */
    public static long calculatePaymentFee(long amount, String paymentMethod) {
        return switch (paymentMethod.toUpperCase()) {
            case "CARD" -> (long) (amount * 0.03); // 카드 3%
            case "VIRTUAL_ACCOUNT" -> 500L; // 가상계좌 500원 고정
            case "BANK_TRANSFER" -> 1000L; // 계좌이체 1000원 고정
            case "MOBILE_PAYMENT" -> (long) (amount * 0.025); // 모바일 결제 2.5%
            default -> 0L;
        };
    }
}
```

---

## ✅ 태그/결제 예외 완료 체크리스트

### 🏷️ TagException 구현
- [x] 8가지 태그 타입 검증 (STYLE, FEATURE, TECHNIQUE 등)
- [x] 선호도 레벨 검증 (LOW, MEDIUM, HIGH)
- [x] 숙련도 레벨 검증 (BEGINNER~EXPERT)
- [x] 연관성 점수 검증 (0.0-1.0)
- [x] 추천 시스템 예외 처리
- [x] 태그 매칭 점수 계산 알고리즘
- [x] 태그 카테고리 분류 시스템

### 💳 PaymentException 구현
- [x] 한국 결제 시스템 지원 (카드/가상계좌/계좌이체/모바일)
- [x] 결제 금액 검증 (100원~1천만원)
- [x] 환불 처리 예외 (7일 이내 환불 가능)
- [x] 마스킹된 카드번호 검증
- [x] 가상계좌 번호 검증
- [x] 한국 원화 포맷팅
- [x] 결제 수수료 계산 (결제 방법별 차등)

### 한국 특화 기능
- [x] 태그 시스템: 8가지 카테고리별 분류
- [x] 결제 시스템: 한국 결제 방법 지원
- [x] 금액 검증: 원화 기준 최소/최대 금액
- [x] 환불 정책: 7일 이내 환불 가능

### 보안 강화 사항
- [x] 태그 추천 알고리즘 보안 예외 처리
- [x] 결제 정보 마스킹 (카드번호, 거래ID)
- [x] 금액 검증으로 부정 결제 차단
- [x] 환불 기간 및 금액 검증

---

*분할 작업 3/4 완료: TagException + PaymentException*  
*다음 파일: step3-2d_validation_system_exceptions.md*