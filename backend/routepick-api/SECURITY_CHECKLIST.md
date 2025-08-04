# RoutePick API 보안 체크리스트

## 📊 현재 구현 상태 요약

### ✅ 이미 구현된 보안 기능 (8개)
- [x] Rate Limiting (IP, 이메일, 전역) - SimpleRateLimitService 구현됨
- [x] 비밀번호 해싱 (BCrypt) - PasswordEncoder 사용
- [x] JWT 토큰 기반 인증 - JwtService 구현됨
- [x] 입력값 검증 (@Valid) - 모든 API에 적용됨
- [x] 파일 업로드 보안 (크기 제한, 확장자 검증) - FileService 구현됨
- [x] 이메일 인증 토큰 검증 - SignupSessionService 구현됨
- [x] 로깅 구현 - 모든 API에 로깅 적용됨
- [x] 비밀번호 정책 (8자 이상, 대문자/소문자/숫자 포함) - AuthService에 구현됨

### ⚠️ 배포 전 수정 필요한 보안 항목 (6개)
- [ ] **파일 업로드 경로 보안**: 현재 로컬 파일 시스템 사용, 클라우드 스토리지로 변경 필요
- [ ] **파일 업로드 URL 보안**: 현재 `/api/files/profiles/` 경로 노출, 보안 강화 필요
- [ ] **에러 메시지 보안**: 민감한 정보가 에러 메시지에 노출될 수 있음
- [ ] **로그 보안**: 민감한 정보 로깅 제외 필요
- [ ] **세션 관리**: 현재 JWT만 사용, 추가 보안 필요
- [ ] **API 응답 보안**: 사용자 정보 노출 최소화 필요

---

## 🔒 프로덕션 배포 전 보안 체크리스트

### 1. 환경변수 설정
- [ ] JWT_SECRET 설정 (최소 256비트 이상)
- [ ] DB_PASSWORD 설정
- [ ] MAIL_PASSWORD 설정
- [ ] CORS_ALLOWED_ORIGINS 설정

### 2. 데이터베이스 보안
- [ ] SSL 연결 활성화
- [ ] 강력한 데이터베이스 비밀번호 설정
- [ ] 데이터베이스 사용자 권한 최소화
- [ ] SQL 로깅 비활성화

### 3. JWT 보안
- [ ] 강력한 시크릿 키 사용
- [ ] 토큰 만료 시간 설정
- [ ] 토큰 검증 로직 구현

### 4. Rate Limiting
- [ ] IP 기반 요청 제한
- [ ] 이메일 기반 요청 제한
- [ ] 전역 요청 제한

### 5. CORS 설정
- [ ] 허용된 도메인만 설정
- [ ] credentials 설정
- [ ] 적절한 헤더 설정

### 6. HTTP 보안 헤더
- [ ] HSTS 설정
- [ ] X-Frame-Options 설정
- [ ] X-Content-Type-Options 설정

### 7. 로깅 및 모니터링
- [ ] 민감한 정보 로깅 제외
- [ ] 적절한 로그 레벨 설정
- [ ] 에러 로깅 설정

### 8. 이메일 보안
- [ ] SMTP SSL/TLS 설정
- [ ] 앱 비밀번호 사용
- [ ] 이메일 템플릿 보안

### 9. API 보안
- [ ] 입력값 검증 (Validation)
- [ ] SQL 인젝션 방지
- [ ] XSS 방지
- [ ] CSRF 방지 (JWT 사용 시 비활성화)

### 10. 파일 업로드 보안
- [ ] 파일 크기 제한
- [ ] 파일 타입 검증
- [ ] 파일 확장자 화이트리스트
- [ ] 업로드 경로 보안

### 11. 세션 관리
- [ ] 세션 타임아웃 설정
- [ ] 세션 고정 공격 방지
- [ ] 동시 세션 제한

### 12. 에러 처리
- [ ] 민감한 정보 노출 방지
- [ ] 적절한 에러 메시지
- [ ] 에러 로깅 설정

---

## 🎯 API별 보안 개선 사항

### 회원가입 API (`/api/auth/signup`)
- [ ] **파일 업로드 보안 강화**: AWS S3 또는 클라우드 스토리지 사용
- [ ] **에러 메시지 일반화**: "회원가입에 실패했습니다"로 통일
- [ ] **로그 보안**: 비밀번호, 토큰 등 민감 정보 로깅 제외
- [ ] **응답 데이터 필터링**: 비밀번호 해시 등 민감 정보 제외

### 로그인 API (`/api/auth/login`)
- [ ] **실패 횟수 제한**: 계정 잠금 기능 추가
- [ ] **로그인 시도 로깅**: 보안 모니터링 강화
- [ ] **토큰 보안 강화**: Refresh Token Rotation 구현
- [ ] **응답 데이터 최소화**: 필요한 정보만 반환

### 이메일 인증 API들
- [ ] **인증 코드 만료 시간 단축**: 5분 → 3분
- [ ] **재발송 제한**: 시간당 최대 3회로 제한
- [ ] **세션 토큰 보안**: 암호화된 세션 토큰 사용
- [ ] **로깅 보안**: 인증 코드 로깅 제외

### 토큰 갱신 API (`/api/auth/refresh`)
- [ ] **토큰 검증 강화**: Refresh Token 재사용 방지
- [ ] **토큰 블랙리스트**: 만료된 토큰 관리
- [ ] **동시 사용 제한**: 한 계정당 하나의 Refresh Token만 유효

### 파일 업로드 보안
- [ ] **클라우드 스토리지**: AWS S3 또는 Google Cloud Storage 사용
- [ ] **파일 스캔**: 바이러스/멀웨어 스캔 추가
- [ ] **접근 제어**: 서명된 URL 또는 임시 토큰 사용
- [ ] **백업 및 복구**: 파일 손실 방지

---

## 🚀 배포 명령어

```bash
# 1. 환경변수 설정
source env-example.sh

# 2. 프로덕션 프로필로 실행
./gradlew :routepick-api:bootRun --args='--spring.profiles.active=prod'
```

---

## 🔍 보안 테스트

### 1. JWT 토큰 테스트
```bash
# 토큰 생성 테스트
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Password123!"}'
```

### 2. Rate Limiting 테스트
```bash
# 빠른 요청으로 Rate Limiting 테스트
for i in {1..15}; do
  curl -X POST http://localhost:8080/api/auth/email/verification \
    -H "Content-Type: application/json" \
    -d '{"email":"test@example.com"}'
done
```

### 3. CORS 테스트
```bash
# CORS 헤더 확인
curl -H "Origin: https://malicious-site.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: X-Requested-With" \
  -X OPTIONS http://localhost:8080/api/auth/login
```

### 4. 파일 업로드 보안 테스트
```bash
# 허용되지 않은 파일 타입 업로드 테스트
curl -X POST http://localhost:8080/api/auth/signup \
  -F 'userData={"email":"test@example.com","password":"Password123!","userName":"test","phone":"010-1234-5678","registrationToken":"test","agreeTerms":true,"agreePrivacy":true,"agreeMarketing":false,"agreeLocation":false,"requiredAgreementValid":true,"marketingAgreed":true,"locationAgreed":true};type=application/json' \
  -F 'profileImage=@malicious.exe'
```

### 5. 입력값 검증 테스트
```bash
# SQL 인젝션 시도
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com; DROP TABLE users; --","password":"Password123!"}'

# XSS 시도
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Password123!","userName":"<script>alert(\"XSS\")</script>","phone":"010-1234-5678","registrationToken":"test","agreeTerms":true,"agreePrivacy":true,"agreeMarketing":false,"agreeLocation":false,"requiredAgreementValid":true,"marketingAgreed":true,"locationAgreed":true}'
```

---

## 📝 보안 모니터링

### 로그 모니터링
- Rate Limiting 위반 로그
- 인증 실패 로그
- SQL 인젝션 시도 로그
- CORS 위반 로그
- 파일 업로드 실패 로그
- 입력값 검증 실패 로그

### 성능 모니터링
- 응답 시간 모니터링
- 메모리 사용량 모니터링
- 데이터베이스 연결 풀 모니터링
- 파일 업로드 처리 시간 모니터링

---

## 🔧 보안 업데이트

### 정기 업데이트 항목
- [ ] Spring Boot 버전 업데이트
- [ ] 의존성 라이브러리 업데이트
- [ ] JWT 시크릿 키 정기 변경
- [ ] 데이터베이스 비밀번호 정기 변경
- [ ] 보안 패치 적용
- [ ] 취약점 스캔

---

## 🚨 보안 사고 대응

### 1. 즉시 조치사항
- [ ] 서비스 중단
- [ ] 로그 분석
- [ ] 취약점 패치
- [ ] 사용자 알림

### 2. 사후 조치사항
- [ ] 보안 감사
- [ ] 재발 방지 대책 수립
- [ ] 보안 정책 업데이트
- [ ] 직원 보안 교육

---

## 📋 보안 점검 체크리스트

### 매일 점검
- [ ] 로그 확인
- [ ] 시스템 상태 확인
- [ ] 백업 상태 확인

### 매주 점검
- [ ] 보안 업데이트 확인
- [ ] 성능 모니터링
- [ ] 사용자 활동 분석

### 매월 점검
- [ ] 보안 감사
- [ ] 백업 복구 테스트
- [ ] 재해 복구 계획 점검 