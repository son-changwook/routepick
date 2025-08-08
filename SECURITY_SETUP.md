# RoutePick 보안 설정 가이드

## 🔐 보안 설정 개요

RoutePick 프로젝트의 보안 설정을 위한 가이드입니다. 프로덕션 환경 배포 전 반드시 확인하세요.

## 📋 필수 환경변수 설정

### 1. JWT 설정
```bash
# JWT Secret (최소 32자, 영문/숫자/특수문자 포함)
JWT_SECRET=your-super-secure-jwt-secret-key-here-minimum-32-characters-with-special-chars

# JWT 토큰 만료 시간 (밀리초)
JWT_EXPIRATION=3600000          # 액세스 토큰: 1시간
JWT_REFRESH_EXPIRATION=2592000000  # 리프레시 토큰: 30일

# JWT 발급자
JWT_ISSUER=routepick-api
```

### 2. 데이터베이스 설정
```bash
# 데이터베이스 연결 정보
DB_HOST=localhost
DB_PORT=3306
DB_NAME=routepick
DB_USERNAME=routepick
DB_PASSWORD=your-secure-database-password
```

### 3. Redis 설정
```bash
# Redis 연결 정보
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379
SPRING_REDIS_PASSWORD=your-secure-redis-password
```

### 4. 이메일 설정
```bash
# 이메일 서비스 설정
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=routepick@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM=noreply@routepick.com
```

## 🚀 환경별 설정

### 개발 환경 (dev)
- 기본값 사용 가능
- 로깅 레벨: DEBUG
- 보안 검증: 경고만 출력

### 프로덕션 환경 (prod)
- 모든 환경변수 필수 설정
- 로깅 레벨: INFO/WARN
- 보안 검증: 오류 시 애플리케이션 중단

## 🔧 설정 적용 방법

### 1. 환경변수 파일 생성
```bash
# .env 파일 복사
cp env.example .env

# 환경변수 편집
nano .env
```

### 2. 보안 키 생성
```bash
# JWT Secret 생성
openssl rand -base64 32

# 데이터베이스 비밀번호 생성
openssl rand -base64 16

# Redis 비밀번호 생성
openssl rand -base64 16
```

### 3. 애플리케이션 실행
```bash
# 개발 환경
./gradlew :routepick-api:bootRun

# 프로덕션 환경
SPRING_PROFILES_ACTIVE=prod ./gradlew :routepick-api:bootRun
```

## ⚠️ 보안 주의사항

### 1. JWT Secret
- ✅ 최소 32자 이상
- ✅ 영문, 숫자, 특수문자 포함
- ❌ 하드코딩 금지
- ❌ Git에 커밋 금지

### 2. 데이터베이스 비밀번호
- ✅ 최소 8자 이상
- ✅ 복잡한 문자 조합
- ❌ 기본값 사용 금지 (프로덕션)

### 3. Redis 비밀번호
- ✅ 최소 8자 이상
- ✅ 복잡한 문자 조합
- ❌ 기본값 사용 금지 (프로덕션)

### 4. 이메일 비밀번호
- ✅ 앱 비밀번호 사용 (Gmail)
- ✅ 2단계 인증 활성화
- ❌ 일반 비밀번호 사용 금지

## 🔍 설정 검증

### 자동 검증
애플리케이션 시작 시 자동으로 다음을 검증합니다:

1. **프로덕션 환경**
   - JWT_SECRET 설정 여부
   - 데이터베이스 비밀번호 설정 여부
   - Redis 비밀번호 설정 여부

2. **개발 환경**
   - 기본값 사용 시 경고 출력
   - 애플리케이션 중단 없음

### 수동 검증
```bash
# 설정 검증 실행
curl -X GET http://localhost:8080/api/health

# 로그 확인
tail -f logs/application.log
```

## 📝 체크리스트

### 배포 전 확인사항
- [ ] JWT_SECRET 설정 (32자 이상)
- [ ] DB_PASSWORD 설정 (8자 이상)
- [ ] REDIS_PASSWORD 설정 (8자 이상)
- [ ] MAIL_PASSWORD 설정 (Gmail 앱 비밀번호)
- [ ] 환경변수 파일(.env) 생성
- [ ] .env 파일을 .gitignore에 추가
- [ ] 프로덕션 환경변수 설정
- [ ] 보안 로그 확인

### 런타임 확인사항
- [ ] 애플리케이션 정상 시작
- [ ] 데이터베이스 연결 성공
- [ ] Redis 연결 성공
- [ ] JWT 토큰 생성/검증 성공
- [ ] 이메일 발송 테스트
- [ ] 보안 경고 없음

## 🆘 문제 해결

### 일반적인 오류
1. **JWT_SECRET 미설정**
   ```
   Error: JWT_SECRET environment variable is required
   Solution: .env 파일에 JWT_SECRET 설정
   ```

2. **데이터베이스 연결 실패**
   ```
   Error: Access denied for user 'routepick'@'localhost'
   Solution: DB_PASSWORD 환경변수 확인
   ```

3. **Redis 연결 실패**
   ```
   Error: NOAUTH Authentication required
   Solution: REDIS_PASSWORD 환경변수 확인
   ```

### 로그 확인
```bash
# 애플리케이션 로그
tail -f logs/application.log

# 보안 이벤트 로그
grep "SECURITY" logs/application.log
```

## 📞 지원

보안 설정 관련 문의사항이 있으시면 개발팀에 연락하세요.

---

**⚠️ 중요**: 이 문서의 내용은 보안과 관련된 민감한 정보를 포함하고 있습니다. 
프로덕션 환경에서는 반드시 강력한 비밀번호와 보안 키를 사용하세요.
