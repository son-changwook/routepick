# 🔄 Step 6-6b2b: Circuit Breaker 및 백오프 전략

> API 장애 대응 시스템 및 재시도 전략  
> 생성일: 2025-09-01  
> 분할 기준: Circuit Breaker 패턴 및 백오프 전략

---

## 🎯 설계 목표

- **Circuit Breaker**: 장애 API 자동 차단 및 복구
- **백오프 전략**: 지수 백오프를 통한 재시도
- **자동 복구**: 상태 전환 및 자가 치유
- **모니터링**: Circuit Breaker 상태 실시간 추적

---

## ✅ Circuit Breaker 및 백오프 전략 (계속)

```java
    // ===== Circuit Breaker 패턴 =====
    
    private final Map<Long, CircuitBreakerState> circuitBreakerStates = new HashMap<>();
    
    /**
     * Circuit Breaker 상태 체크
     */
    public boolean isCircuitOpen(Long configId) {
        CircuitBreakerState state = circuitBreakerStates.get(configId);
        
        if (state == null) {
            return false; // 초기 상태는 CLOSED
        }
        
        return state.isOpen();
    }
    
    /**
     * API 호출 성공 기록
     */
    public void recordSuccess(Long configId) {
        CircuitBreakerState state = getOrCreateCircuitBreakerState(configId);
        state.recordSuccess();
        
        if (state.shouldTransitionToHalfOpen()) {
            state.transitionToHalfOpen();
            log.info("Circuit Breaker HALF_OPEN으로 전환: configId={}", configId);
        } else if (state.shouldTransitionToClosed()) {
            state.transitionToClosed();
            log.info("Circuit Breaker CLOSED로 전환: configId={}", configId);
        }
    }
    
    /**
     * API 호출 실패 기록
     */
    public void recordFailure(Long configId) {
        CircuitBreakerState state = getOrCreateCircuitBreakerState(configId);
        state.recordFailure();
        
        if (state.shouldTransitionToOpen()) {
            state.transitionToOpen();
            log.warn("Circuit Breaker OPEN으로 전환: configId={}", configId);
            
            // 알림 발송
            notifyCircuitBreakerOpen(configId);
        }
    }
    
    /**
     * Circuit Breaker 상태 조회
     */
    public String getCircuitBreakerStatus(Long configId) {
        CircuitBreakerState state = circuitBreakerStates.get(configId);
        return state != null ? state.getState().name() : "CLOSED";
    }
    
    /**
     * Circuit Breaker 수동 리셋
     */
    public void resetCircuitBreaker(Long configId) {
        CircuitBreakerState state = circuitBreakerStates.get(configId);
        if (state != null) {
            state.reset();
            log.info("Circuit Breaker 수동 리셋: configId={}", configId);
        }
    }
    
    /**
     * Circuit Breaker 상태 점검 (5분마다)
     */
    @Scheduled(fixedRate = 300000)
    public void checkCircuitBreakerStates() {
        try {
            circuitBreakerStates.forEach((configId, state) -> {
                if (state.shouldTransitionToHalfOpen()) {
                    state.transitionToHalfOpen();
                    log.info("Circuit Breaker 자동 HALF_OPEN 전환: configId={}", configId);
                }
            });
            
        } catch (Exception e) {
            log.error("Circuit Breaker 상태 점검 실패", e);
        }
    }
    
    // ===== 백오프 전략 =====
    
    /**
     * 지수 백오프 계산
     */
    public long calculateBackoffDelay(int attemptCount) {
        // 기본: 1초, 최대: 5분
        long baseDelay = 1000; // 1초
        long maxDelay = 300000; // 5분
        
        long delay = (long) (baseDelay * Math.pow(2, attemptCount - 1));
        return Math.min(delay, maxDelay);
    }
    
    /**
     * 재시도 가능 여부 체크
     */
    public boolean shouldRetry(Long configId, int attemptCount) {
        final int maxRetries = 3;
        
        if (attemptCount >= maxRetries) {
            return false;
        }
        
        // Circuit Breaker가 열려있으면 재시도 안함
        if (isCircuitOpen(configId)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 재시도 지연 시간 계산 (지터 추가)
     */
    public long calculateBackoffDelayWithJitter(int attemptCount) {
        long baseDelay = calculateBackoffDelay(attemptCount);
        
        // ±25% 지터 추가
        double jitterPercent = 0.25;
        double jitter = (Math.random() - 0.5) * 2 * jitterPercent;
        
        return (long) (baseDelay * (1 + jitter));
    }
    
    /**
     * 특정 예외에 대한 재시도 여부 판단
     */
    public boolean isRetryableException(Exception exception) {
        // 네트워크 관련 예외는 재시도 가능
        if (exception instanceof java.net.ConnectException ||
            exception instanceof java.net.SocketTimeoutException ||
            exception instanceof java.io.IOException) {
            return true;
        }
        
        // HTTP 5xx 에러는 재시도 가능
        if (exception.getMessage() != null && 
            exception.getMessage().contains("5")) {
            return true;
        }
        
        // 4xx 에러는 재시도 불가
        return false;
    }
    
    // ===== 유틸리티 메서드 =====
    
    private CircuitBreakerState getOrCreateCircuitBreakerState(Long configId) {
        return circuitBreakerStates.computeIfAbsent(configId, 
                k -> new CircuitBreakerState());
    }
    
    // ===== 알림 메서드 =====
    
    private void notifyCircuitBreakerOpen(Long configId) {
        try {
            String message = String.format(
                "Circuit Breaker 열림: ConfigId=%d - API 호출이 일시적으로 차단됩니다.",
                configId
            );
            
            notificationService.sendSystemAlert("CIRCUIT_BREAKER_OPEN", message, Map.of(
                "configId", configId,
                "timestamp", LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Circuit Breaker 열림 알림 발송 실패: configId={}", configId, e);
        }
    }
    
    // ===== Circuit Breaker 상태 클래스 =====
    
    private static class CircuitBreakerState {
        private enum State { CLOSED, OPEN, HALF_OPEN }
        
        private State state = State.CLOSED;
        private int failureCount = 0;
        private int successCount = 0;
        private LocalDateTime lastFailureTime;
        private LocalDateTime stateChangeTime = LocalDateTime.now();
        
        private static final int FAILURE_THRESHOLD = 5;
        private static final int SUCCESS_THRESHOLD = 3;
        private static final int TIMEOUT_MINUTES = 5;
        
        public boolean isOpen() {
            return state == State.OPEN;
        }
        
        public State getState() {
            return state;
        }
        
        public void recordSuccess() {
            if (state == State.HALF_OPEN) {
                successCount++;
            } else {
                failureCount = 0;
                successCount++;
            }
        }
        
        public void recordFailure() {
            failureCount++;
            lastFailureTime = LocalDateTime.now();
            
            if (state == State.HALF_OPEN) {
                // Half-open 상태에서 실패하면 다시 Open
                transitionToOpen();
            }
        }
        
        public boolean shouldTransitionToOpen() {
            return state == State.CLOSED && failureCount >= FAILURE_THRESHOLD;
        }
        
        public boolean shouldTransitionToHalfOpen() {
            return state == State.OPEN && 
                   stateChangeTime.isBefore(LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES));
        }
        
        public boolean shouldTransitionToClosed() {
            return state == State.HALF_OPEN && successCount >= SUCCESS_THRESHOLD;
        }
        
        public void transitionToOpen() {
            state = State.OPEN;
            stateChangeTime = LocalDateTime.now();
        }
        
        public void transitionToHalfOpen() {
            state = State.HALF_OPEN;
            successCount = 0;
            stateChangeTime = LocalDateTime.now();
        }
        
        public void transitionToClosed() {
            state = State.CLOSED;
            failureCount = 0;
            successCount = 0;
            stateChangeTime = LocalDateTime.now();
        }
        
        public void reset() {
            state = State.CLOSED;
            failureCount = 0;
            successCount = 0;
            stateChangeTime = LocalDateTime.now();
        }
        
        // ===== 통계 및 정보 메서드 =====
        
        public int getFailureCount() {
            return failureCount;
        }
        
        public int getSuccessCount() {
            return successCount;
        }
        
        public LocalDateTime getLastFailureTime() {
            return lastFailureTime;
        }
        
        public LocalDateTime getStateChangeTime() {
            return stateChangeTime;
        }
        
        /**
         * Circuit Breaker 상태 정보 조회
         */
        public Map<String, Object> getStateInfo() {
            Map<String, Object> info = new HashMap<>();
            info.put("state", state.name());
            info.put("failureCount", failureCount);
            info.put("successCount", successCount);
            info.put("lastFailureTime", lastFailureTime);
            info.put("stateChangeTime", stateChangeTime);
            info.put("failureThreshold", FAILURE_THRESHOLD);
            info.put("successThreshold", SUCCESS_THRESHOLD);
            info.put("timeoutMinutes", TIMEOUT_MINUTES);
            
            if (state == State.OPEN) {
                LocalDateTime nextRetryTime = stateChangeTime.plusMinutes(TIMEOUT_MINUTES);
                info.put("nextRetryTime", nextRetryTime);
                info.put("remainingTimeoutSeconds", 
                        java.time.Duration.between(LocalDateTime.now(), nextRetryTime).getSeconds());
            }
            
            return info;
        }
    }
    
    // ===== 고급 백오프 전략 =====
    
    /**
     * 적응형 백오프 계산 (성공률 기반)
     */
    public long calculateAdaptiveBackoffDelay(Long configId, int attemptCount) {
        CircuitBreakerState state = circuitBreakerStates.get(configId);
        
        if (state == null) {
            return calculateBackoffDelay(attemptCount);
        }
        
        // 실패율 기반 백오프 조정
        int totalAttempts = state.getFailureCount() + state.getSuccessCount();
        if (totalAttempts > 0) {
            double failureRate = (double) state.getFailureCount() / totalAttempts;
            
            // 실패율이 높을수록 더 긴 백오프
            double multiplier = 1.0 + failureRate * 2.0; // 최대 3배
            long baseDelay = calculateBackoffDelay(attemptCount);
            
            return (long) (baseDelay * multiplier);
        }
        
        return calculateBackoffDelay(attemptCount);
    }
    
    /**
     * 선형 백오프 계산
     */
    public long calculateLinearBackoffDelay(int attemptCount) {
        long baseDelay = 2000; // 2초
        long maxDelay = 60000; // 1분
        
        long delay = baseDelay * attemptCount;
        return Math.min(delay, maxDelay);
    }
    
    /**
     * 피보나치 백오프 계산
     */
    public long calculateFibonacciBackoffDelay(int attemptCount) {
        long baseDelay = 1000; // 1초
        long maxDelay = 300000; // 5분
        
        int fibValue = fibonacci(attemptCount);
        long delay = baseDelay * fibValue;
        
        return Math.min(delay, maxDelay);
    }
    
    private int fibonacci(int n) {
        if (n <= 1) return 1;
        if (n == 2) return 2;
        
        int a = 1, b = 2;
        for (int i = 3; i <= n; i++) {
            int temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }
}
```

---

## 📈 Circuit Breaker 특징

### 1. **상태 관리**
- **CLOSED**: 정상 상태, 모든 호출 허용
- **OPEN**: 장애 상태, 모든 호출 차단
- **HALF_OPEN**: 복구 시도, 제한적 호출 허용

### 2. **자동 전환**
- **실패 임계치**: 5회 연속 실패 시 OPEN
- **성공 임계치**: HALF_OPEN에서 3회 성공 시 CLOSED
- **시간 기반**: 5분 후 자동 HALF_OPEN 전환

### 3. **모니터링**
- **상태 추적**: 실시간 Circuit Breaker 상태
- **통계 정보**: 성공/실패 카운트, 상태 변경 시간
- **알림**: Circuit Breaker OPEN 시 자동 알림

---

## 🔄 백오프 전략

### 1. **지수 백오프**
- **기본**: 1초부터 시작
- **증가**: 매번 2배씩 증가
- **최대**: 5분까지 제한

### 2. **백오프 변형**
- **지터 추가**: ±25% 랜덤 변동
- **적응형**: 실패율 기반 조정
- **선형/피보나치**: 다양한 증가 패턴

### 3. **재시도 제어**
- **최대 횟수**: 3회까지 재시도
- **Circuit Breaker 연동**: OPEN 시 재시도 중단
- **예외 기반**: 재시도 가능한 예외만 재시도

---

**📝 연관 파일**: 
- step6-6b2a_api_rate_limiting_core.md (Rate Limiting 핵심)
- step6-6b1_external_api_core.md (API 핵심)
- step6-6b3_api_monitoring.md (모니터링)