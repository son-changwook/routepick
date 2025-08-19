# GitHub Actions CI/CD 트러블슈팅 가이드

> GitHub Actions 워크플로우에서 발생한 오류들과 해결 방법 정리  
> 작성일: 2025-08-19  
> 프로젝트: RoutePickr Backend CI/CD Pipeline

---

## 🚨 발생한 오류 목록 및 해결책

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
gradle wrapper --gradle-version=8.5

# Git에 추가
git add routepick-common/gradlew* routepick-common/gradle/
git add routepick-backend/gradlew* routepick-backend/gradle/
git commit -m "Add Gradle Wrapper files"
```

**교훈:** Gradle 프로젝트는 항상 Wrapper 파일을 포함해야 함

---

### 2. gradle-wrapper.jar 누락 오류
**오류 메시지:**
```bash
Unable to access jarfile /home/runner/work/routepick/routepick/routepick-common/gradle/wrapper/gradle-wrapper.jar
Error: Process completed with exit code 1
```

**원인:** `.gitignore`의 `*.jar` 규칙으로 인해 `gradle-wrapper.jar`가 제외됨

**해결책:**
```gitignore
# .gitignore 수정
*.jar
!gradle-wrapper.jar  # 예외 규칙 추가
```

```bash
# 강제로 Git에 추가
git add -f routepick-common/gradle/wrapper/gradle-wrapper.jar
git add -f routepick-backend/gradle/wrapper/gradle-wrapper.jar
```

**교훈:** Gradle Wrapper JAR 파일은 반드시 Git에 포함되어야 함

---

### 3. 정의되지 않은 Gradle 태스크 오류
**오류 메시지:**
```bash
Task 'checkstyleMain' not found in root project 'routepick-backend'
Task 'spotbugsMain' not found in root project 'routepick-backend'
Task 'dependencyCheckAnalyze' not found in root project 'routepick-backend'
Task 'integrationTest' not found in root project 'routepick-backend'
Task 'jacocoTestReport' not found in root project 'routepick-backend'
```

**원인:** CI 워크플로우에서 사용하는 태스크들이 `build.gradle`에 정의되지 않음

**해결책:**
- **방법 1:** 플러그인 추가
- **방법 2 (선택):** 워크플로우에서 해당 태스크 제거

```yaml
# CI 워크플로우 단순화
- name: 코드 컴파일
  run: |
    cd routepick-backend
    ./gradlew compileJava compileTestJava
```

**교훈:** CI 워크플로우와 빌드 스크립트의 일관성 유지 필요

---

### 4. QueryDSL 중복 생성 오류
**오류 메시지:**
```bash
Attempt to recreate a file for type com.routepick.common.QBaseEntity
Compilation failed
```

**원인:** QueryDSL 플러그인이 `BaseEntity`에 대한 Q 클래스를 중복 생성

**해결책:**
```gradle
// build.gradle에서 QueryDSL 플러그인 제거
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.1'
    id 'io.spring.dependency-management' version '1.1.4'
    // id 'com.ewerk.gradle.plugins.querydsl' version '1.0.10' // 제거
}

// 단순화된 QueryDSL 설정
def querydslDir = "$buildDir/generated/querydsl"

sourceSets {
    main.java.srcDir querydslDir
}

tasks.withType(JavaCompile) {
    options.getGeneratedSourceOutputDirectory().set(file(querydslDir))
}
```

**교훈:** `@MappedSuperclass`를 사용하는 BaseEntity는 QueryDSL Q 클래스 생성 대상에서 제외

---

### 5. 데이터베이스 권한 오류
**오류 메시지:**
```bash
ERROR 1044 (42000) at line 18: Access denied for user 'test'@'%' to database 'routepick'
```

**원인:** CI 환경의 'test' 사용자가 새 데이터베이스 생성 권한이 없음

**해결책:**
```yaml
# CI 워크플로우에서 CREATE DATABASE 문 제거
- name: 테스트 환경 준비
  run: |
    sed '18,19d' database/routepick.sql | mysql -h 127.0.0.1 -u test -ptest routepick_test
```

**교훈:** CI 환경에서는 이미 생성된 테스트 데이터베이스 사용

---

### 6. CodeQL Action 버전 Deprecated 오류
**오류 메시지:**
```bash
CodeQL Action major versions v1 and v2 have been deprecated
Resource not accessible by integration
```

**원인:** 
1. `github/codeql-action/upload-sarif@v2`가 deprecated
2. SARIF 업로드에 필요한 권한 없음

**해결책:**
```yaml
# SARIF 업로드 대신 아티팩트로 저장
- name: 보안 스캔 결과 보관
  uses: actions/upload-artifact@v4
  if: always()
  with:
    name: trivy-security-scan
    path: 'trivy-results.sarif'
```

**교훈:** GitHub Security 탭 업로드는 특별한 권한이 필요하므로 아티팩트 사용 고려

---

### 7. AWS 자격 증명 누락 오류
**오류 메시지:**
```bash
Credentials could not be loaded, please check your action inputs: Could not load credentials from any providers
```

**원인:** GitHub Secrets에 AWS 자격 증명이 설정되지 않음

**해결책:**
```yaml
# AWS 관련 작업을 조건부로 실행
- name: Docker 이미지 빌드
  if: github.event_name == 'push' && vars.AWS_ENABLED == 'true'
```

**설정 방법:**
1. GitHub Repository → Settings → Secrets and variables → Actions
2. Variables 탭에서 `AWS_ENABLED` = `true` 추가
3. Secrets 탭에서 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` 추가

**교훈:** 개발 단계에서는 배포 관련 단계를 조건부로 실행

---

## 8. QBaseEntity 컴파일 오류 (로컬 개발환경)
**오류 현상:**
- IDE에서 QBaseEntity.java 파일에 빨간 줄 오류 표시
- BaseEntity 필드 변경 후 생성된 Q 클래스와 불일치

**원인:** 
1. BaseEntity.java 필드명 변경 (`lastModifiedBy` → `modifiedBy`)
2. 기존 생성된 QBaseEntity.java가 이전 필드명 기반으로 생성됨
3. QueryDSL이 `@MappedSuperclass`인 BaseEntity에 대해서도 Q 클래스 생성

**해결책:**
```bash
# 1. 생성된 QBaseEntity 파일들 삭제
rm -rf routepick-backend/bin/generated-sources/annotations/com/routepick/common/QBaseEntity.java
rm -rf routepick-backend/build/generated/querydsl/com/routepick/common/QBaseEntity.java

# 2. clean 빌드로 재생성
./gradlew clean compileJava
```

**.gitignore 업데이트:**
```gitignore
# QueryDSL generated files
**/generated-sources/
**/generated/querydsl/
**/QBaseEntity.java
```

**교훈:** 
- QueryDSL 생성 파일은 Git에서 제외하여 충돌 방지
- BaseEntity 변경 시 기존 생성 파일 정리 필요
- `@MappedSuperclass`도 QueryDSL 생성 대상이 될 수 있음

---

## 🛠️ actions/upload-artifact v4 업데이트

### 변경사항
```yaml
# Before (v3)
- name: 테스트 결과 업로드
  uses: actions/upload-artifact@v3

# After (v4)
- name: 테스트 결과 업로드
  uses: actions/upload-artifact@v4
```

### 업데이트된 위치
- `ci-backend.yml`: 2개 위치
- `ci-frontend.yml`: 3개 위치

---

## 📋 CI/CD 파이프라인 최종 구조

### Backend CI/CD 단계
1. **code-quality**: 코드 컴파일 검사
2. **test**: 단위 테스트 실행 (MySQL + Redis)
3. **security-scan**: Trivy 보안 스캔
4. **build-image**: Docker 이미지 빌드 (조건부)
5. **deploy-dev**: 개발 환경 배포 (조건부)
6. **deploy-prod**: 운영 환경 배포 (조건부)
7. **performance-test**: 성능 테스트 (조건부)

### 조건부 실행 조건
- AWS 관련 작업: `vars.AWS_ENABLED == 'true'`
- 개발 배포: `github.ref == 'refs/heads/develop'`
- 운영 배포: `github.ref == 'refs/heads/main'`

---

## 🔧 권장 설정

### 1. Repository Variables 설정
```
AWS_ENABLED = false  # 개발 중에는 false
```

### 2. Repository Secrets (배포 시에만 필요)
```
AWS_ACCESS_KEY_ID = your_access_key
AWS_SECRET_ACCESS_KEY = your_secret_key
SLACK_WEBHOOK_URL = your_webhook_url
DEV_API_URL = https://api-dev.routepick.com
```

### 3. Gradle 프로젝트 체크리스트
- [ ] `gradlew`, `gradlew.bat` 파일 존재
- [ ] `gradle/wrapper/gradle-wrapper.jar` 파일 존재
- [ ] `.gitignore`에 `!gradle-wrapper.jar` 예외 규칙
- [ ] CI에서 사용하는 모든 태스크가 `build.gradle`에 정의됨

### 4. CI/CD 워크플로우 체크리스트
- [ ] 모든 `actions/upload-artifact`가 v4 사용
- [ ] 민감한 단계는 조건부 실행 설정
- [ ] 데이터베이스 스키마 파일은 CI 환경에 맞게 조정
- [ ] 보안 스캔 결과는 아티팩트로 저장

---

## 🚀 향후 개선 사항

### 단기 (1-2주)
1. **테스트 커버리지 리포트 추가**
   - JaCoCo 플러그인 설정
   - Codecov 연동

2. **코드 품질 도구 추가**
   - Checkstyle 설정
   - SpotBugs 설정
   - PMD 설정

### 중기 (1개월)
1. **Docker 멀티스테이지 빌드**
   - 빌드 시간 단축
   - 이미지 크기 최적화

2. **테스트 환경 개선**
   - Testcontainers 활용
   - 통합 테스트 추가

### 장기 (2-3개월)
1. **배포 자동화 고도화**
   - Blue/Green 배포
   - 카나리 배포
   - 자동 롤백

2. **모니터링 연동**
   - 배포 상태 모니터링
   - 성능 지표 추적

---

**참고 문서:**
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Gradle User Manual](https://docs.gradle.org/current/userguide/userguide.html)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)

**마지막 업데이트:** 2025-08-19  
**다음 리뷰 예정:** 2025-09-19