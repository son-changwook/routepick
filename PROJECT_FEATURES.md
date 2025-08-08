# RoutePick 프로젝트 사용 기능 목록

## 📋 개요

이 문서는 RoutePick 프로젝트에서 현재 구현되어 사용 중인 기능들을 체계적으로 정리한 목록입니다.

---

## 🔐 **보안 및 인증 기능**

### **1. JWT 토큰 기반 인증**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/auth/JwtService.java`
- **기능**: 액세스 토큰, 리프레시 토큰, 회원가입 토큰 생성 및 검증
- **특징**: 
  - JWT 표준 준수 (sub 필드에 userId 저장)
  - 커스텀 claims 지원 (email, userName, nickName, profileImageUrl 등)
  - 토큰 타입별 만료 시간 설정
  - 토큰 블랙리스트 관리

### **2. Spring Security 설정**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/security/ApiSecurityConfig.java`
- **기능**: 
  - JWT 인증 필터 (`ApiJwtAuthenticationFilter`)
  - CORS 설정
  - CSRF 비활성화 (JWT 사용)
  - 세션 관리 (STATELESS)
  - 엔드포인트별 권한 설정

### **3. Rate Limiting (요청 제한)**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/security/RedisRateLimitService.java`
- **기능**:
  - IP 기반 Rate Limiting (1분에 100회)
  - 이메일 기반 Rate Limiting (1시간에 10회)
  - 전역 Rate Limiting (1분에 1000회)
  - 엔드포인트별 Rate Limiting
  - Sliding Window 알고리즘 사용

### **4. 비밀번호 보안**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/auth/AuthService.java`
- **기능**:
  - BCrypt 해싱
  - 비밀번호 정책 검증 (8자 이상, 대문자/소문자/숫자 포함)
  - 비밀번호 강도 검사

---

## 📧 **이메일 및 인증 기능**

### **1. 이메일 인증 시스템**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/email/EmailVerificationService.java`
- **기능**:
  - 이메일 중복 확인
  - 인증 코드 생성 및 발송
  - 인증 코드 검증
  - JWT 기반 회원가입 토큰 생성

### **2. 세션 관리**
- **구현 위치**: 
  - `backend/routepick-api/src/main/java/com/routepick/api/service/email/SignupSessionService.java` (메모리 기반)
  - `backend/routepick-api/src/main/java/com/routepick/api/service/email/RedisSignupSessionService.java` (Redis 기반)
- **기능**:
  - 회원가입 세션 생성/관리
  - 인증 코드 저장
  - 세션 만료 처리
  - 등록 토큰 검증

### **3. 이메일 서비스**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/email/EmailService.java`
- **기능**:
  - 인증 코드 이메일 발송
  - 이메일 템플릿 관리
  - 개발 환경 시뮬레이션

---

## 🗄️ **데이터베이스 및 영속성**

### **1. MyBatis ORM**
- **구현 위치**: `backend/routepick-api/src/main/resources/mapper/UserMapper.xml`
- **기능**:
  - 사용자 정보 CRUD
  - 이메일/닉네임 중복 확인
  - 사용자 검색 및 조회

### **2. Redis 캐싱**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/config/ApiRedisConfig.java`
- **기능**:
  - 토큰 블랙리스트 관리
  - 세션 저장소
  - Rate Limiting 데이터 저장
  - 캐시 관리

### **3. 데이터베이스 설정**
- **구현 위치**: `backend/routepick-api/src/main/resources/application.yml`
- **기능**:
  - MySQL 연결 설정
  - MyBatis 설정
  - Redis 연결 설정

---

## 📁 **파일 업로드 및 보안**

### **1. 파일 업로드 보안**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/util/FileSecurityUtil.java`
- **기능**:
  - 파일 타입 검증 (MIME 타입)
  - 파일 크기 제한 (5MB)
  - 악성 파일 시그니처 검증
  - 파일명 보안 검증
  - Path Traversal 공격 방지

### **2. 파일 서비스**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/file/FileService.java`
- **기능**:
  - 프로필 이미지 업로드
  - 파일 해시 계산
  - 안전한 파일 경로 생성
  - 파일 무결성 검증

### **3. 파일 업로드 설정**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/config/FileUploadConfig.java`
- **기능**:
  - 파일 크기 제한 설정
  - MultipartResolver 설정
  - 임시 파일 관리

---

## 🛡️ **예외 처리 및 로깅**

### **1. 전역 예외 처리**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/exception/GlobalExceptionHandler.java`
- **기능**:
  - 커스텀 예외 처리
  - Spring Validation 예외 처리
  - 보안 예외 처리
  - 일관된 API 응답 형식

### **2. 커스텀 예외 클래스**
- **구현 위치**: `backend/routepick-common/src/main/java/com/routepick/common/exception/`
- **기능**:
  - `BaseException`: 모든 커스텀 예외의 기본 클래스
  - `BusinessException`: 비즈니스 로직 예외
  - `ValidationException`: 검증 예외
  - `SecurityException`: 보안 관련 예외
  - `FileException`: 파일 관련 예외

### **3. 보안 로깅**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/util/SecureLogger.java`
- **기능**:
  - 민감한 정보 마스킹 (이메일, IP 주소)
  - 보안 이벤트 로깅
  - 인증 이벤트 로깅
  - Rate Limit 이벤트 로깅

---

## 🔧 **설정 및 구성**

### **1. 모듈별 설정**
- **API 모듈**: `backend/routepick-api/src/main/resources/application.yml`
- **Admin 모듈**: `backend/routepick-admin/src/main/resources/application.yml`
- **Common 모듈**: `backend/routepick-common/src/main/resources/application.yml`

### **2. 환경별 설정**
- **개발 환경**: `backend/routepick-api/src/main/resources/application-dev.yml`
- **프로덕션 환경**: `backend/routepick-api/src/main/resources/application-prod.yml`

### **3. Bean 충돌 방지**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/config/BeanConflictPreventionConfig.java`
- **기능**:
  - 모듈별 Redis 설정 분리
  - 고유한 Bean 이름 사용
  - 조건부 Bean 생성

---

## 📚 **API 문서화**

### **1. Swagger/OpenAPI**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/config/SwaggerConfig.java`
- **기능**:
  - API 문서 자동 생성
  - API 테스트 인터페이스
  - 요청/응답 예시 제공

### **2. API 응답 표준화**
- **구현 위치**: `backend/routepick-common/src/main/java/com/routepick/common/dto/ApiResponse.java`
- **기능**:
  - 일관된 응답 형식
  - 성공/실패 상태 관리
  - 에러 코드 표준화

---

## 🚀 **배포 및 인프라**

### **1. Docker 컨테이너화**
- **구현 위치**: `docker-compose.yml`
- **기능**:
  - MySQL 데이터베이스 컨테이너
  - Redis 캐시 컨테이너
  - API 서버 컨테이너
  - Admin 서버 컨테이너

### **2. 환경변수 관리**
- **구현 위치**: `.env`, `env.example`
- **기능**:
  - 민감한 정보 외부화
  - 환경별 설정 분리
  - 보안 키 관리

---

## 📊 **모니터링 및 관리**

### **1. 스케줄링 작업**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/service/auth/TokenCleanupService.java`
- **기능**:
  - 만료된 토큰 정리
  - 세션 정리
  - Rate Limit 데이터 정리

### **2. 설정 검증**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/config/ConfigurationValidator.java`
- **기능**:
  - 필수 환경변수 검증
  - 보안 설정 강도 검증
  - 애플리케이션 시작 시 검증

---

## 🔄 **개발 도구 및 유틸리티**

### **1. 입력값 검증**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/util/InputSanitizer.java`
- **기능**:
  - 이메일 정제
  - XSS 방지
  - SQL Injection 방지

### **2. Rate Limit 헬퍼**
- **구현 위치**: `backend/routepick-api/src/main/java/com/routepick/api/util/RateLimitHelper.java`
- **기능**:
  - IP 기반 Rate Limit 체크
  - 이메일 기반 Rate Limit 체크
  - 복합 Rate Limit 체크

---

## 📈 **성능 최적화**

### **1. Redis 기반 캐싱**
- **기능**: 세션, 토큰, Rate Limit 데이터 캐싱
- **장점**: 빠른 접근, 자동 만료, 분산 환경 지원

### **2. 비동기 처리**
- **기능**: 이메일 발송, 로깅 등 비동기 처리
- **장점**: 응답 시간 단축, 시스템 부하 분산

---

## 🎯 **특별한 기능들**

### **1. 사용자 식별자 구분**
- **구현 위치**: `backend/routepick-common/src/main/java/com/routepick/common/domain/user/User.java`
- **기능**:
  - `getUsername()`: Spring Security용 userId
  - `getUserName()`: 실제 사용자 이름
  - `getNickName()`: 사용자 닉네임

### **2. JWT 기반 회원가입 토큰**
- **기능**: 이메일 인증 후 JWT 토큰으로 회원가입 검증
- **장점**: 보안성 향상, 토큰 재사용 방지

### **3. 민감한 정보 마스킹**
- **기능**: 로깅 시 이메일, IP 주소 등 민감한 정보 마스킹
- **형식**: `user@example.com` → `u***@example.com`

---

## 📝 **문서화**

### **1. API 설계 가이드**
- **위치**: `API_DESIGN_GUIDELINES.md`
- **내용**: RESTful API 설계 원칙, 응답 형식, 에러 처리

### **2. 보안 체크리스트**
- **위치**: `backend/routepick-api/SECURITY_CHECKLIST.md`
- **내용**: 구현된 보안 기능, 배포 전 체크리스트

### **3. TODO 리스트**
- **위치**: `TODO_LIST.md`
- **내용**: 향후 구현 예정 기능, 개선 필요 사항

---

## 🔧 **개발 환경**

### **1. 기술 스택**
- **백엔드**: Spring Boot 3.2.3, Java 17, Gradle
- **데이터베이스**: MySQL 8.0, Redis 7.2
- **보안**: Spring Security, JWT
- **문서화**: Swagger/OpenAPI 3

### **2. 개발 도구**
- **에디터**: Cursor Editor
- **버전 관리**: Git, GitHub
- **컨테이너**: Docker, Docker Compose
- **클라우드**: AWS (EC2, S3, RDS, SES 등)

---

## 📊 **현재 구현 상태 요약**

### ✅ **완료된 기능 (15개)**
1. JWT 토큰 기반 인증
2. Spring Security 설정
3. Rate Limiting (Redis 기반)
4. 이메일 인증 시스템
5. 파일 업로드 보안
6. 전역 예외 처리
7. 커스텀 예외 클래스
8. 보안 로깅
9. API 문서화 (Swagger)
10. Docker 컨테이너화
11. 환경변수 관리
12. 스케줄링 작업
13. 설정 검증
14. 입력값 검증
15. 민감한 정보 마스킹

### ⚠️ **개발 환경용 임시 처리 (3개)**
1. 이메일 발송 시스템 (콘솔 출력만 사용)
2. 인증 코드 응답 포함 (보안 위험)
3. 메모리 기반 세션 저장소

### 🔄 **진행 중인 기능 (1개)**
1. API 테스트 및 디버깅

---

## 🎯 **프로젝트 특징**

### **1. 모듈화 아키텍처**
- API 모듈: 일반 사용자 서비스
- Admin 모듈: 관리자 서비스
- Common 모듈: 공통 기능

### **2. 보안 우선 설계**
- 모든 API에 보안 검증 적용
- 민감한 정보 마스킹
- Rate Limiting으로 DDoS 방지

### **3. 확장 가능한 구조**
- 모듈별 독립적인 설정
- Bean 충돌 방지
- 환경별 설정 분리

### **4. 개발자 친화적**
- 상세한 문서화
- Swagger UI로 API 테스트
- 체계적인 예외 처리

---

*이 문서는 프로젝트의 현재 상태를 반영하며, 지속적으로 업데이트됩니다.*
