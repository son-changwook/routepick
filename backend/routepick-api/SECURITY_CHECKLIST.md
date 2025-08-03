# RoutePick API 보안 체크리스트

## 🔒 프로덕션 배포 전 보안 체크리스트

### 1. 환경변수 설정 ✅
- [ ] JWT_SECRET 설정 (최소 256비트 이상)
- [ ] DB_PASSWORD 설정
- [ ] MAIL_PASSWORD 설정
- [ ] CORS_ALLOWED_ORIGINS 설정

### 2. 데이터베이스 보안 ✅
- [ ] SSL 연결 활성화
- [ ] 강력한 데이터베이스 비밀번호 설정
- [ ] 데이터베이스 사용자 권한 최소화
- [ ] SQL 로깅 비활성화

### 3. JWT 보안 ✅
- [ ] 강력한 시크릿 키 사용
- [ ] 토큰 만료 시간 설정
- [ ] 토큰 검증 로직 구현

### 4. Rate Limiting ✅
- [ ] IP 기반 요청 제한
- [ ] 이메일 기반 요청 제한
- [ ] 전역 요청 제한

### 5. CORS 설정 ✅
- [ ] 허용된 도메인만 설정
- [ ] credentials 설정
- [ ] 적절한 헤더 설정

### 6. HTTP 보안 헤더 ✅
- [ ] HSTS 설정
- [ ] X-Frame-Options 설정
- [ ] X-Content-Type-Options 설정

### 7. 로깅 및 모니터링 ✅
- [ ] 민감한 정보 로깅 제외
- [ ] 적절한 로그 레벨 설정
- [ ] 에러 로깅 설정

### 8. 이메일 보안 ✅
- [ ] SMTP SSL/TLS 설정
- [ ] 앱 비밀번호 사용
- [ ] 이메일 템플릿 보안

## 🚀 배포 명령어

```bash
# 1. 환경변수 설정
source env-example.sh

# 2. 프로덕션 프로필로 실행
./gradlew :routepick-api:bootRun --args='--spring.profiles.active=prod'
```

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

## 📝 보안 모니터링

### 로그 모니터링
- Rate Limiting 위반 로그
- 인증 실패 로그
- SQL 인젝션 시도 로그
- CORS 위반 로그

### 성능 모니터링
- 응답 시간 모니터링
- 메모리 사용량 모니터링
- 데이터베이스 연결 풀 모니터링

## 🔧 보안 업데이트

### 정기 업데이트 항목
- [ ] Spring Boot 버전 업데이트
- [ ] 의존성 라이브러리 업데이트
- [ ] JWT 시크릿 키 정기 변경
- [ ] 데이터베이스 비밀번호 정기 변경 