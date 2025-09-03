# 🚨 GitHub Actions CI/CD 트러블슈팅 가이드

> RoutePickr 프로젝트의 CI/CD 파이프라인 구축 시 발생 가능한 문제와 해결책  
> 최종 업데이트: 2025-09-02  
> 프로젝트 구조: Multi-module Spring Boot + React Native

---

## 📋 목차
1. [Gradle 관련 오류](#gradle-관련-오류)
2. [Docker 관련 오류](#docker-관련-오류)
3. [Spring Boot 관련 오류](#spring-boot-관련-오류)
4. [React Native 관련 오류](#react-native-관련-오류)
5. [AWS 배포 관련 오류](#aws-배포-관련-오류)
6. [보안 및 시크릿 관리](#보안-및-시크릿-관리)

---

## 🔧 Gradle 관련 오류

### 1. Gradle Wrapper 누락 오류
**오류 메시지:**
```bash
./gradlew: No such file or directory
Error: Process completed with exit code 127
```

**원인:** `gradlew` 파일이 프로젝트에 없음

**해결책:**
```bash
# 각 Gradle 프로젝트 디렉토리에서 실행
cd routepick-backend
gradle wrapper --gradle-version=8.5

cd ../routepick-common
gradle wrapper --gradle-version=8.5

# Git에 추가
git add */gradlew* */gradle/
git commit -m "Add Gradle Wrapper files for CI/CD"
```

### 2. gradle-wrapper.jar 누락 오류
**오류 메시지:**
```bash
Unable to access jarfile gradle/wrapper/gradle-wrapper.jar
Error: Process completed with exit code 1
```

**원인:** `.gitignore`의 `*.jar` 규칙으로 인해 제외됨

**해결책:**
```gitignore
# .gitignore 수정
*.jar
!gradle-wrapper.jar  # 예외 규칙 추가
!gradle/wrapper/gradle-wrapper.jar  # 명시적 경로 추가
```

### 3. Multi-module 프로젝트 빌드 순서 문제
**오류 메시지:**
```bash
Could not resolve all dependencies for configuration ':routepick-backend:implementation'.
> Could not find com.routepick:routepick-common:0.0.1-SNAPSHOT
```

**원인:** routepick-common이 먼저 빌드되지 않음

**해결책:**
```yaml
# .github/workflows/backend-ci.yml
- name: Build Common Module First
  run: |
    cd routepick-common
    ./gradlew clean build publishToMavenLocal
    
- name: Build Backend Module
  run: |
    cd routepick-backend
    ./gradlew clean build
```

---

## 🐳 Docker 관련 오류

### 1. Multi-stage Build 실패
**오류 메시지:**
```bash
failed to solve: failed to compute cache key: "/app/build/libs/*.jar" not found
```

**원인:** JAR 파일 경로가 잘못됨

**해결책:**
```dockerfile
# Dockerfile 수정
FROM openjdk:17-jdk-slim AS builder
WORKDIR /app
COPY . .
RUN ./gradlew clean bootJar

FROM openjdk:17-jdk-slim
WORKDIR /app
# 정확한 JAR 파일 경로 지정
COPY --from=builder /app/build/libs/routepick-backend-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2. Docker Hub Rate Limit
**오류 메시지:**
```bash
toomanyrequests: You have reached your pull rate limit
```

**해결책:**
```yaml
# Docker Hub 인증 추가
- name: Login to Docker Hub
  uses: docker/login-action@v2
  with:
    username: ${{ secrets.DOCKER_USERNAME }}
    password: ${{ secrets.DOCKER_PASSWORD }}
```

---

## 🌱 Spring Boot 관련 오류

### 1. 테스트 실행 시 데이터베이스 연결 실패
**오류 메시지:**
```bash
Failed to configure a DataSource: 'url' attribute is not specified
```

**해결책:**
```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
```

### 2. Redis 연결 실패 (테스트 환경)
**해결책:**
```java
// 테스트용 Embedded Redis 설정
@TestConfiguration
public class EmbeddedRedisConfig {
    @Bean
    @ConditionalOnMissingBean
    public RedisServer redisServer() throws IOException {
        return new RedisServer(6370);
    }
}
```

---

## 📱 React Native 관련 오류

### 1. Metro Bundler 캐시 문제
**오류 메시지:**
```bash
error: bundling failed: Error: Unable to resolve module
```

**해결책:**
```yaml
- name: Clear Metro Cache
  run: |
    cd routepick-app
    rm -rf node_modules
    npm cache clean --force
    npm install
    npx react-native start --reset-cache
```

### 2. iOS 빌드 실패 (CocoaPods)
**해결책:**
```yaml
- name: Install CocoaPods
  run: |
    cd routepick-app/ios
    pod install --repo-update
```

---

## ☁️ AWS 배포 관련 오류

### 1. ECR 푸시 권한 오류
**오류 메시지:**
```bash
denied: Your authorization token has expired
```

**해결책:**
```yaml
- name: Configure AWS credentials
  uses: aws-actions/configure-aws-credentials@v2
  with:
    aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
    aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
    aws-region: ap-northeast-2

- name: Login to Amazon ECR
  id: login-ecr
  uses: aws-actions/amazon-ecr-login@v1
```

### 2. ECS 배포 실패
**해결책:**
```yaml
- name: Deploy to ECS
  uses: aws-actions/amazon-ecs-deploy-task-definition@v1
  with:
    task-definition: task-definition.json
    service: routepick-service
    cluster: routepick-cluster
    wait-for-service-stability: true
```

---

## 🔐 보안 및 시크릿 관리

### 1. GitHub Secrets 설정 체크리스트
```yaml
필수 Secrets:
- DOCKER_USERNAME
- DOCKER_PASSWORD
- AWS_ACCESS_KEY_ID
- AWS_SECRET_ACCESS_KEY
- JWT_SECRET
- DB_PASSWORD
- REDIS_PASSWORD
- SMTP_PASSWORD
- KAKAO_CLIENT_SECRET
- NAVER_CLIENT_SECRET
- GOOGLE_CLIENT_SECRET
- FACEBOOK_CLIENT_SECRET
```

### 2. 환경별 설정 분리
```yaml
# GitHub Actions 환경 변수 활용
- name: Set up environment
  run: |
    echo "SPRING_PROFILES_ACTIVE=${{ github.ref == 'refs/heads/main' && 'prod' || 'dev' }}" >> $GITHUB_ENV
```

---

## 📋 권장 워크플로우 구조

### Backend CI/CD 전체 구조
```yaml
name: Backend CI/CD

on:
  push:
    branches: [ main, develop ]
    paths:
      - 'routepick-backend/**'
      - 'routepick-common/**'
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: test
          MYSQL_DATABASE: routepick_test
        options: >-
          --health-cmd="mysqladmin ping"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5
        ports:
          - 3306:3306
      redis:
        image: redis:7
        options: >-
          --health-cmd="redis-cli ping"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5
        ports:
          - 6379:6379
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-
    
    - name: Build and Test Common Module
      run: |
        cd routepick-common
        ./gradlew clean build publishToMavenLocal
    
    - name: Build and Test Backend Module
      run: |
        cd routepick-backend
        ./gradlew clean build test
    
    - name: Generate Test Report
      if: always()
      uses: dorny/test-reporter@v1
      with:
        name: Backend Tests
        path: routepick-backend/build/test-results/test/*.xml
        reporter: java-junit
    
  build-and-push:
    needs: test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ap-northeast-2
    
    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1
    
    - name: Build and push Docker image
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: routepick-backend
        IMAGE_TAG: ${{ github.sha }}
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
        docker tag $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG $ECR_REGISTRY/$ECR_REPOSITORY:latest
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest
```

---

## 🎯 트러블슈팅 팁

### 1. 디버깅 모드 활성화
```yaml
- name: Enable debug logging
  run: |
    echo "ACTIONS_STEP_DEBUG=true" >> $GITHUB_ENV
    echo "ACTIONS_RUNNER_DEBUG=true" >> $GITHUB_ENV
```

### 2. 실패 시 아티팩트 업로드
```yaml
- name: Upload failure logs
  if: failure()
  uses: actions/upload-artifact@v3
  with:
    name: failure-logs
    path: |
      **/build/reports/
      **/build/test-results/
```

### 3. 슬랙 알림 설정
```yaml
- name: Slack Notification
  if: always()
  uses: 8398a7/action-slack@v3
  with:
    status: ${{ job.status }}
    text: 'CI/CD ${{ job.status }} for ${{ github.ref }}'
    webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

---

## 📚 참고 자료
- [GitHub Actions 공식 문서](https://docs.github.com/en/actions)
- [Spring Boot with GitHub Actions](https://spring.io/guides/gs/github-actions/)
- [Docker Best Practices for GitHub Actions](https://docs.docker.com/ci-cd/github-actions/)
- [AWS ECS Deployment Guide](https://aws.amazon.com/blogs/containers/deploy-applications-on-amazon-ecs-using-github-actions/)

---

*Last Updated: 2025-09-02*  
*RoutePickr CI/CD Pipeline Troubleshooting Guide v2.0*