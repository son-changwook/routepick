# RoutePick 설정 파일 관리 가이드

## 📋 개요

이 문서는 RoutePick 프로젝트의 설정 파일들을 체계적으로 관리하기 위한 가이드입니다.

---

## 🗂️ **설정 파일 구조**

### **📁 API 모듈 (`backend/routepick-api/src/main/resources/`)**

#### **1. `application.yml` (기본 설정)**
- **목적**: 모든 환경에서 공통으로 사용되는 기본 설정
- **내용**:
  - Spring Boot 기본 설정
  - 데이터베이스 연결 설정
  - Redis 설정
  - 이메일 설정
  - 로깅 설정
  - 서버 설정
  - MyBatis 설정
  - Swagger 설정
  - JWT 설정

#### **2. `application-dev.yml` (개발 환경)**
- **목적**: 개발 환경에서만 사용되는 설정
- **내용**:
  - 개발용 데이터베이스 연결 정보
  - 개발용 Redis 설정
  - 개발용 로깅 레벨 (DEBUG)
  - 개발용 JWT 설정
  - 개발용 이메일 설정

#### **3. `application-prod.yml` (프로덕션 환경)**
- **목적**: 프로덕션 환경에서만 사용되는 설정
- **내용**:
  - 프로덕션 데이터베이스 연결 정보
  - 프로덕션 Redis 설정
  - 프로덕션 로깅 레벨 (INFO/WARN)
  - 프로덕션 JWT 설정
  - 프로덕션 이메일 설정

### **📁 Admin 모듈 (`backend/routepick-admin/src/main/resources/`)**

#### **1. `application.yml` (Admin 기본 설정)**
- **목적**: Admin 모듈의 기본 설정
- **내용**:
  - Admin 전용 설정
  - 관리자 인증 설정

### **📁 Common 모듈 (`backend/routepick-common/src/main/resources/`)**

#### **1. `application.yml` (공통 설정)**
- **목적**: 모든 모듈에서 공통으로 사용되는 설정
- **내용**:
  - 공통 상수
  - 공통 유틸리티 설정
  - 공통 예외 처리 설정

---

## 🔧 **설정 우선순위**

Spring Boot는 다음 순서로 설정을 로드합니다:

1. **`application.yml`** (기본 설정)
2. **`application-{profile}.yml`** (프로필별 설정)
3. **환경변수** (가장 높은 우선순위)

### **📝 설정 병합 규칙:**

```yaml
# application.yml (기본)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/routepick
    username: routepick

# application-dev.yml (개발 환경)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/routepick_dev  # 덮어쓰기
    password: dev_password  # 추가

# 최종 결과
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/routepick_dev
    username: routepick
    password: dev_password
```

---

## ⚠️ **주의사항 및 모범 사례**

### **1. 중복 키 방지**
```yaml
# ❌ 잘못된 예시 (중복)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db1

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db2

# ✅ 올바른 예시
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db1
```

### **2. 들여쓰기 주의**
```yaml
# ❌ 잘못된 예시 (잘못된 들여쓰기)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db1
  mail:  # 이렇게 하면 datasource 하위에 mail이 들어감
    host: smtp.gmail.com

# ✅ 올바른 예시
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db1
  mail:  # spring 하위에 mail이 들어감
    host: smtp.gmail.com
```

### **3. 환경변수 사용**
```yaml
# ✅ 권장: 환경변수 사용
spring:
  datasource:
    password: ${DB_PASSWORD:default_password}

# ❌ 비권장: 하드코딩
spring:
  datasource:
    password: mypassword123
```

---

## 🔍 **설정 검증 방법**

### **1. YAML 문법 검증**
```bash
# YAML 문법 검증
python -c "import yaml; yaml.safe_load(open('application.yml'))"
```

### **2. Spring Boot 설정 검증**
```bash
# 설정 로드 테스트
./gradlew :routepick-api:bootRun --args="--debug"
```

### **3. 중복 키 검증**
```bash
# 중복 키 검색
grep -n "map-underscore-to-camel-case" *.yml
```

---

## 🛠️ **문제 해결 가이드**

### **1. YAML 중복 키 오류**
```
found duplicate key map-underscore-to-camel-case
```

**해결 방법:**
1. 모든 설정 파일에서 중복된 키 검색
2. 중복된 설정 제거
3. 들여쓰기 확인

### **2. 포트 충돌 오류**
```
Web server failed to start. Port 8080 was already in use.
```

**해결 방법:**
```bash
# 포트 사용 중인 프로세스 확인
lsof -i :8080

# 프로세스 종료
kill -9 $(lsof -ti:8080)
```

### **3. 설정 로드 실패**
```
Could not resolve placeholder 'DB_PASSWORD' in value "${DB_PASSWORD}"
```

**해결 방법:**
1. 환경변수 설정 확인
2. 기본값 제공
3. `.env` 파일 확인

---

## 📊 **설정 파일 체크리스트**

### **✅ API 모듈**
- [ ] `application.yml` - 기본 설정 완료
- [ ] `application-dev.yml` - 개발 환경 설정 완료
- [ ] `application-prod.yml` - 프로덕션 환경 설정 완료
- [ ] 중복 키 없음
- [ ] 들여쓰기 정확함
- [ ] 환경변수 사용

### **✅ Admin 모듈**
- [ ] `application.yml` - Admin 설정 완료
- [ ] 중복 키 없음
- [ ] 들여쓰기 정확함

### **✅ Common 모듈**
- [ ] `application.yml` - 공통 설정 완료
- [ ] 중복 키 없음
- [ ] 들여쓰기 정확함

---

## 🔄 **설정 변경 시 주의사항**

### **1. 변경 전**
- [ ] 현재 설정 백업
- [ ] 변경 영향 범위 분석
- [ ] 테스트 환경에서 검증

### **2. 변경 중**
- [ ] YAML 문법 검증
- [ ] 중복 키 확인
- [ ] 들여쓰기 확인

### **3. 변경 후**
- [ ] 서버 재시작
- [ ] 기능 테스트
- [ ] 로그 확인

---

## 📝 **설정 파일 템플릿**

### **기본 application.yml 템플릿**
```yaml
spring:
  profiles:
    active: dev
  application:
    name: routepick-api
  
  # 데이터베이스 설정
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:routepick}
    username: ${DB_USERNAME:routepick}
    password: ${DB_PASSWORD:routepick}
  
  # Redis 설정
  redis:
    host: ${SPRING_REDIS_HOST:localhost}
    port: ${SPRING_REDIS_PORT:6379}
    password: ${SPRING_REDIS_PASSWORD:}
    database: 0

# MyBatis 설정
mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: com.routepick.common.domain
  configuration:
    map-underscore-to-camel-case: true

# 로깅 설정
logging:
  level:
    root: INFO
    com.routepick.api: INFO

# 서버 설정
server:
  port: 8080
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: never
```

---

*이 가이드를 따라 설정 파일을 관리하면 중복 키 오류와 포트 충돌 문제를 예방할 수 있습니다.*
