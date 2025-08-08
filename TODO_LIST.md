# RoutePick 프로젝트 TODO 리스트

## 📋 개요

이 문서는 RoutePick 프로젝트에서 향후 구현 예정인 기능들과 개선이 필요한 부분들을 정리한 리스트입니다.

---

## 🔧 **개발 환경용 임시 처리 (프로덕션 배포 시 구현 필요)**

### **1. 이메일 발송 시스템**
**현재 상태**: 개발 환경에서는 콘솔 출력만 사용
**위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/email/EmailService.java`

```java
// 현재: 개발 환경 시뮬레이션
log.info("=== 개발 환경 이메일 발송 시뮬레이션 ===");

// 향후: 실제 이메일 발송 구현
SimpleMailMessage message = new SimpleMailMessage();
message.setFrom(fromEmail);
message.setTo(toEmail);
message.setSubject(subject);
message.setText(emailContent);
mailSender.send(message);
```

**구현 필요 사항**:
- [ ] Gmail SMTP 설정 완료
- [ ] 실제 이메일 발송 로직 활성화
- [ ] 이메일 템플릿 개선
- [ ] 발송 실패 시 재시도 로직

### **2. 인증 코드 응답 제거**
**현재 상태**: 개발용으로 인증 코드를 응답에 포함
**위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/email/EmailVerificationService.java`

```java
// 현재: 개발용 (보안 위험)
.verificationCode(verificationCode) // 개발용, 실제로는 제거

// 향후: 제거 필요
// .verificationCode(verificationCode) // 제거
```

**구현 필요 사항**:
- [ ] `EmailVerificationResponse`에서 `verificationCode` 필드 제거
- [ ] 실제 이메일 발송 확인 후 응답 수정

### **3. 세션 저장소 개선**
**현재 상태**: 메모리 기반 세션 저장소 사용
**위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/email/SignupSessionService.java`

```java
// 현재: 메모리 기반 (서버 재시작 시 데이터 손실)
private final ConcurrentHashMap<String, SignupSession> sessions = new ConcurrentHashMap<>();

// 향후: Redis 기반으로 개선
// RedisTemplate<String, Object> redisTemplate 사용
```

**구현 필요 사항**:
- [ ] Redis 기반 세션 저장소 구현
- [ ] 세션 만료 자동 정리 로직
- [ ] 분산 환경 지원

---

## 🏗️ **비즈니스 로직 구현 예정**

### **1. 약관 동의 시스템**
**현재 상태**: 로그만 출력
**위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/auth/AuthService.java`

```java
// 현재: 로그만 출력
log.info("사용자 {} 약관 동의 저장:", userId);

// 향후: 실제 DB 저장 로직 구현
// 1. UserAgreementMapper 주입
// 2. 각 약관별 동의 정보를 DB에 저장
// 3. 트랜잭션 처리
// 4. 예외 처리
```

**구현 필요 사항**:
- [ ] `UserAgreementMapper` 생성
- [ ] 약관 동의 테이블 설계
- [ ] 트랜잭션 처리 로직
- [ ] 약관 동의 이력 관리

### **2. 클라이밍장 관리 시스템**
**현재 상태**: 미구현
**구현 필요 사항**:
- [ ] 클라이밍장 CRUD API
- [ ] 클라이밍장 검색/필터링
- [ ] 위치 기반 검색
- [ ] 관리자 승인 시스템

### **3. 클라이밍 루트 관리 시스템**
**현재 상태**: 미구현
**구현 필요 사항**:
- [ ] 루트 CRUD API
- [ ] 난이도별 분류
- [ ] 사용자 완등 기록
- [ ] 루트 평가 시스템

### **4. 사용자 활동 관리**
**현재 상태**: 미구현
**구현 필요 사항**:
- [ ] 사용자 활동 기록
- [ ] 완등 통계
- [ ] 친구 시스템
- [ ] 소셜 기능

---

## 🔒 **보안 강화 필요 사항**

### **1. 파일 업로드 보안**
**현재 상태**: 로컬 파일 시스템 사용
**위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/file/FileService.java`

**개선 필요 사항**:
- [ ] 클라우드 스토리지 (AWS S3) 도입
- [ ] 파일 업로드 URL 보안 강화
- [ ] CDN 연동
- [ ] 파일 압축/최적화

### **2. 에러 메시지 보안**
**현재 상태**: 개발 환경에서 상세 정보 노출
**위치**: `backend/routepick-api/src/main/java/com/routepick/api/exception/DefaultExceptionHandler.java`

**개선 필요 사항**:
- [ ] 프로덕션 환경에서 민감한 정보 숨김
- [ ] 일반화된 에러 메시지 사용
- [ ] 스택 트레이스 제거

### **3. 로그 보안**
**현재 상태**: 일부 민감한 정보 로깅
**개선 필요 사항**:
- [ ] 민감한 정보 마스킹 강화
- [ ] 로그 레벨 최적화
- [ ] 로그 로테이션 설정

---

## 🚀 **성능 최적화 필요 사항**

### **1. 데이터베이스 최적화**
- [ ] 인덱스 최적화
- [ ] 쿼리 성능 개선
- [ ] 커넥션 풀 설정
- [ ] 읽기 전용 복제본 설정

### **2. 캐싱 전략**
- [ ] Redis 캐싱 전략 수립
- [ ] 자주 조회되는 데이터 캐싱
- [ ] 캐시 무효화 전략

### **3. API 성능**
- [ ] 페이징 처리
- [ ] 응답 데이터 최적화
- [ ] API 버전 관리

---

## 📱 **프론트엔드 구현 필요 사항**

### **1. 웹 프론트엔드**
- [ ] React/Vue.js 기반 웹 애플리케이션
- [ ] 반응형 디자인
- [ ] PWA 지원
- [ ] SEO 최적화

### **2. 모바일 앱**
- [ ] React Native 앱 개발
- [ ] 푸시 알림 구현
- [ ] 오프라인 지원
- [ ] 네이티브 기능 연동

---

## 🧪 **테스트 구현 필요 사항**

### **1. 단위 테스트**
- [ ] 서비스 레이어 테스트
- [ ] 컨트롤러 테스트
- [ ] 리포지토리 테스트

### **2. 통합 테스트**
- [ ] API 엔드포인트 테스트
- [ ] 데이터베이스 통합 테스트
- [ ] 외부 서비스 연동 테스트

### **3. 성능 테스트**
- [ ] 부하 테스트
- [ ] 스트레스 테스트
- [ ] 메모리 누수 테스트

---

## 📊 **모니터링 및 로깅**

### **1. 애플리케이션 모니터링**
- [ ] Spring Boot Actuator 설정
- [ ] 메트릭 수집
- [ ] 헬스 체크 엔드포인트

### **2. 로그 관리**
- [ ] 중앙화된 로그 수집
- [ ] 로그 분석 도구 연동
- [ ] 알림 시스템 구축

---

## 🚀 **배포 및 인프라**

### **1. CI/CD 파이프라인**
- [ ] GitHub Actions 설정
- [ ] 자동 테스트 및 배포
- [ ] 환경별 배포 전략

### **2. 인프라 자동화**
- [ ] Terraform/CloudFormation
- [ ] Docker 컨테이너 최적화
- [ ] 쿠버네티스 연동

---

## 📅 **우선순위**

### **🔥 높은 우선순위 (즉시 구현 필요)**
1. **이메일 발송 시스템** - 프로덕션 배포 필수
2. **약관 동의 시스템** - 법적 요구사항
3. **파일 업로드 보안** - 보안 강화
4. **에러 메시지 보안** - 보안 강화

### **⚡ 중간 우선순위 (1-2개월 내)**
1. **클라이밍장 관리 시스템** - 핵심 기능
2. **클라이밍 루트 관리** - 핵심 기능
3. **사용자 활동 관리** - 핵심 기능
4. **성능 최적화** - 사용자 경험

### **📈 낮은 우선순위 (3-6개월 내)**
1. **소셜 기능** - 추가 기능
2. **모바일 앱** - 플랫폼 확장
3. **고급 분석** - 비즈니스 인사이트
4. **AI 기능** - 차별화 요소

---

## 📝 **완료된 항목**

### **✅ 이미 완료된 개선사항**
- [x] JWT 기반 회원가입 토큰 생성
- [x] 모듈별 독립적인 Redis 설정
- [x] Spring Security 사용자 정보 구분
- [x] 파일 업로드 보안 강화
- [x] 입력값 검증 및 정제
- [x] Rate Limiting 구현
- [x] 토큰 블랙리스트 관리

---

**마지막 업데이트**: 2024년 8월 8일
**담당자**: 개발팀
**상태**: 진행 중
