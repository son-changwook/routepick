# RoutePick API

RoutePick 클라이밍 서비스의 API 서버입니다.

## 환경 설정

### 1. 이메일 설정 (Gmail SMTP)

이메일 인증 기능을 사용하려면 Gmail SMTP 설정이 필요합니다.

#### Gmail 앱 비밀번호 생성
1. Google 계정 설정 → 보안
2. 2단계 인증 활성화
3. 앱 비밀번호 생성
4. 생성된 16자리 비밀번호 사용

#### 환경 변수 설정
```bash
# 이메일 설정
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-16-digit-app-password
export MAIL_FROM=noreply@routepick.com

# 데이터베이스 설정
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=routepick
export DB_USERNAME=routepick
export DB_PASSWORD=routepick

# JWT 설정
export JWT_SECRET=your-jwt-secret-key-here
export JWT_EXPIRATION=86400000
```

### 2. 데이터베이스 설정

MySQL 데이터베이스가 필요합니다.

```sql
CREATE DATABASE routepick;
CREATE USER 'routepick'@'localhost' IDENTIFIED BY 'routepick';
GRANT ALL PRIVILEGES ON routepick.* TO 'routepick'@'localhost';
FLUSH PRIVILEGES;
```

## 실행 방법

### 1. 개발 환경
```bash
cd backend/routepick-api
./gradlew bootRun
```

### 2. Docker 환경
```bash
docker-compose up routepick-api
```

## API 엔드포인트

### 인증 관련
- `POST /api/auth/signup` - 회원가입 (이메일 인증 토큰 필수)
- `POST /api/auth/login` - 로그인
- `POST /api/auth/refresh` - 토큰 갱신

### 이메일 인증 (회원가입 전 필수)
- `POST /api/auth/email/check` - 이메일 중복 확인
- `POST /api/auth/email/verification` - 인증 코드 발송
- `POST /api/auth/email/verify` - 인증 코드 검증 (registrationToken 발급)

## 회원가입 플로우

### 1단계: 이메일 인증 (필수)
```bash
# 1. 이메일 중복 확인
curl -X POST http://localhost:8080/api/auth/email/check \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'

# 2. 인증 코드 발송
curl -X POST http://localhost:8080/api/auth/email/verification \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'

# 3. 인증 코드 검증 (registrationToken 발급)
curl -X POST http://localhost:8080/api/auth/email/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "verificationCode": "123456", "sessionToken": "session-id"}'
```

### 2단계: 회원가입 (인증 토큰 필수)
```bash
# 4. 회원가입 (registrationToken 필수)
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: multipart/form-data" \
  -F "userData={\"email\":\"test@example.com\",\"password\":\"password123!\",\"userName\":\"testuser\",\"phone\":\"010-1234-5678\",\"registrationToken\":\"registration-token-here\",\"agreeTerms\":true,\"agreePrivacy\":true}" \
  -F "profileImage=@profile.jpg"
```

## 테스트

### 이메일 인증 테스트
```bash
# 1. 이메일 중복 확인
curl -X POST http://localhost:8080/api/auth/email/check \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'

# 2. 인증 코드 발송
curl -X POST http://localhost:8080/api/auth/email/verification \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'

# 3. 인증 코드 검증
curl -X POST http://localhost:8080/api/auth/email/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "verificationCode": "123456", "sessionToken": "session-id"}'
```

## 문제 해결

### 이메일 발송 실패
1. Gmail 앱 비밀번호가 올바른지 확인
2. 2단계 인증이 활성화되어 있는지 확인
3. 환경 변수가 올바르게 설정되어 있는지 확인

### 데이터베이스 연결 실패
1. MySQL 서비스가 실행 중인지 확인
2. 데이터베이스와 사용자가 생성되어 있는지 확인
3. 환경 변수가 올바르게 설정되어 있는지 확인

### 회원가입 실패
1. 이메일 인증이 완료되었는지 확인
2. registrationToken이 유효한지 확인
3. 필수 약관에 동의했는지 확인 