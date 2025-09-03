# Step 2-3a: AWS Infrastructure 및 Terraform 설정

> RoutePickr AWS Infrastructure 완전 구현  
> 생성일: 2025-08-16  
> 기반 분석: step2-1_backend_structure.md, step2-2_frontend_structure.md

---

## 🎯 AWS 인프라 개요

### 생성된 AWS 인프라스트럭처 구성
- **routepick-infrastructure**: Terraform AWS 설정
- **Multi-AZ 고가용성**: Seoul 리전 기반
- **보안 최적화**: WAF + Security Groups
- **한국 특화**: 시간대, 좌표, 소셜 로그인

---

## ☁️ routepick-infrastructure (Terraform AWS)

### 🏗️ 인프라 구성

```
routepick-infrastructure/
├── terraform/
│   ├── main.tf                   # 메인 Terraform 설정
│   ├── variables.tf              # 변수 정의
│   ├── outputs.tf                # 출력값 정의
│   ├── vpc.tf                    # VPC 네트워크 설정
│   ├── security.tf               # 보안 그룹 + WAF
│   └── rds.tf                    # MySQL RDS 설정
├── environments/
│   ├── dev.tfvars               # 개발 환경 변수
│   ├── staging.tfvars           # 스테이징 환경 변수
│   └── prod.tfvars              # 운영 환경 변수
└── modules/                     # 재사용 모듈
    ├── vpc/
    ├── rds/
    └── security/
```

### 🌐 AWS 리소스 구성

#### VPC 네트워크 (Multi-AZ)
- **VPC**: 10.0.0.0/16 CIDR
- **Public Subnets**: 2개 AZ (10.0.1.0/24, 10.0.2.0/24)
- **Private Subnets**: 2개 AZ (10.0.10.0/24, 10.0.11.0/24)
- **NAT Gateway**: 각 AZ별 1개 (고가용성)
- **Internet Gateway**: 1개
- **VPC Endpoint**: S3 (비용 최적화)

#### 보안 설정
- **ALB Security Group**: HTTP(80), HTTPS(443) 허용
- **App Security Group**: ALB에서만 8080 포트 허용
- **RDS Security Group**: App에서만 3306 포트 허용
- **Redis Security Group**: App에서만 6379 포트 허용
- **WAF**: DDoS 보호 + 한국 트래픽 최적화

#### 데이터베이스 (RDS MySQL 8.0)
- **인스턴스**: Multi-AZ (운영) / Single-AZ (개발)
- **백업**: 7일 보관 (운영) / 3일 보관 (개발)
- **모니터링**: Enhanced Monitoring + Performance Insights
- **한국 최적화**: Asia/Seoul 시간대, utf8mb4 문자셋
- **Read Replica**: 운영 환경에만 생성

#### 캐시 (ElastiCache Redis)
- **엔진**: Redis 7.0
- **인스턴스**: cache.t3.micro (개발) / cache.r7g.large (운영)
- **백업**: 자동 백업 활성화
- **보안**: VPC 내부 접근만 허용

### 🇰🇷 한국 특화 설정

```hcl
variable "korea_settings" {
  default = {
    timezone               = "Asia/Seoul"
    default_language       = "ko"
    social_login_providers = ["GOOGLE", "KAKAO", "NAVER", "FACEBOOK"]
    gps_bounds = {
      min_latitude  = 33.0
      max_latitude  = 38.6
      min_longitude = 124.0
      max_longitude = 132.0
    }
  }
}
```

### 📊 모니터링 및 알림
- **CloudWatch Alarms**: CPU, 메모리, 연결 수
- **SNS Topics**: 알림 발송
- **Auto Scaling**: 트래픽 기반 자동 확장

---

## 📦 routepick-common (Java 공통 라이브러리)

### 🏗️ 라이브러리 구조

```
routepick-common/
├── build.gradle                  # Gradle Library 설정
└── src/main/java/com/routepick/common/
    ├── dto/
    │   └── ApiResponse.java       # 통일된 API 응답 포맷
    ├── enums/
    │   ├── UserType.java          # 사용자 유형 (3개)
    │   ├── TagType.java           # 태그 유형 (8개)
    │   ├── SocialProvider.java    # 소셜 로그인 (4개)
    │   ├── PreferenceLevel.java   # 선호도 레벨
    │   └── SkillLevel.java        # 숙련도 레벨
    └── constants/
        └── Constants.java         # 전역 상수 정의
```

### 📋 주요 Enum 정의

#### TagType (8가지 카테고리)
```java
public enum TagType {
    STYLE("스타일", "클라이밍 스타일 관련 태그"),
    FEATURE("특징", "루트의 물리적 특징"),
    TECHNIQUE("기술", "필요한 클라이밍 기술"),
    DIFFICULTY("난이도", "체감 난이도 관련"),
    MOVEMENT("동작", "특정 동작이나 무브"),
    HOLD_TYPE("홀드 타입", "홀드의 종류나 형태"),
    WALL_ANGLE("벽면 각도", "벽의 기울기나 각도"),
    OTHER("기타", "기타 분류되지 않는 태그");
}
```

#### SocialProvider (4개 - APPLE 제외)
```java
public enum SocialProvider {
    GOOGLE("구글", "google"),
    KAKAO("카카오", "kakao"),
    NAVER("네이버", "naver"),
    FACEBOOK("페이스북", "facebook");
}
```

#### PreferenceLevel (추천 가중치)
```java
public enum PreferenceLevel {
    LOW("낮음", 0.3),
    MEDIUM("보통", 0.7),
    HIGH("높음", 1.0);
}
```

### 🔧 공통 상수 정의

```java
// 추천 알고리즘 상수
public static final double TAG_WEIGHT = 0.7;        // 태그 매칭 70%
public static final double LEVEL_WEIGHT = 0.3;      // 레벨 매칭 30%
public static final int MIN_RECOMMENDATION_SCORE = 20;

// 한국 GPS 좌표 범위
public static final double KOREA_MIN_LATITUDE = 33.0;
public static final double KOREA_MAX_LATITUDE = 38.6;

// JWT 설정
public static final long JWT_ACCESS_TOKEN_EXPIRATION = 1800000L;   // 30분
public static final long JWT_REFRESH_TOKEN_EXPIRATION = 604800000L; // 7일

// Redis 캐시 TTL
public static final long CACHE_TTL_USER_RECOMMENDATIONS = 86400; // 24시간
public static final long CACHE_TTL_ROUTE_TAGS = 3600;           // 1시간
```

---

## 🔐 보안 및 모니터링

### 🛡️ 보안 설정

#### WAF (Web Application Firewall)
```hcl
# Rate Limiting
rate_based_statement {
  limit              = 2000
  aggregate_key_type = "IP"
}

# 지역 기반 접근 제어 (한국 최적화)
geo_match_statement {
  country_codes = ["KR", "US", "JP", "CN"]
}
```

#### Secrets 관리
- **AWS Secrets Manager**: 데이터베이스 비밀번호
- **GitHub Secrets**: CI/CD 환경 변수
- **환경 변수 분리**: .env.example 템플릿 제공

### 📊 모니터링 대시보드

#### Grafana 대시보드
- **시스템 메트릭**: CPU, 메모리, 디스크, 네트워크
- **애플리케이션 메트릭**: 응답 시간, 처리량, 에러율
- **비즈니스 메트릭**: 사용자 활동, 추천 성능
- **한국 특화**: KST 시간대, 한국어 레이블

#### 알림 설정
- **Slack 통합**: 배포 상태, 에러 알림
- **CloudWatch Alarms**: 임계값 초과 시 SNS 발송
- **PagerDuty 연동**: 중요 장애 시 호출

---

## 🌏 한국 최적화 설정

### 🗾 지역화 설정
- **시간대**: Asia/Seoul (모든 서비스)
- **언어**: 한국어 우선 (에러 메시지, 로그)
- **문자셋**: UTF-8 (MySQL utf8mb4_unicode_ci)

### 🔍 GPS 좌표 검증
```java
// 한국 GPS 좌표 범위 검증
public static boolean isValidKoreaCoordinate(double lat, double lng) {
    return lat >= KOREA_MIN_LATITUDE && lat <= KOREA_MAX_LATITUDE &&
           lng >= KOREA_MIN_LONGITUDE && lng <= KOREA_MAX_LONGITUDE;
}
```

### 📱 소셜 로그인 4개 Provider
1. **Google**: 글로벌 표준 OAuth2
2. **Kakao**: 한국 1위 메신저 (커스텀 Provider)
3. **Naver**: 한국 1위 포털 (커스텀 Provider)
4. **Facebook**: 글로벌 소셜 네트워크

### 🚀 CDN 최적화
- **CloudFront**: 서울 리전 최적화
- **S3 Transfer Acceleration**: 업로드 성능 향상
- **이미지 최적화**: WebP 포맷 지원

---

## ✅ AWS Infrastructure 완료 체크리스트

### 📦 공통 라이브러리 (routepick-common)
- [x] **Gradle 라이브러리 설정**: Java 17, Maven 발행
- [x] **공통 DTO**: ApiResponse 통일된 응답 포맷
- [x] **핵심 Enum 정의**: TagType(8개), SocialProvider(4개), PreferenceLevel, SkillLevel
- [x] **전역 상수**: 추천 알고리즘, GPS 범위, JWT, 캐시 TTL
- [x] **한국 특화**: 소셜 로그인 4개, 시간대, 좌표 범위

### ☁️ AWS 인프라 (Terraform)
- [x] **VPC 네트워크**: Multi-AZ, Public/Private Subnets, NAT Gateway
- [x] **보안 설정**: Security Groups, WAF, DDoS 보호
- [x] **RDS MySQL**: Multi-AZ, Enhanced Monitoring, 한국 시간대
- [x] **ElastiCache Redis**: 캐시 클러스터, VPC 보안
- [x] **한국 최적화**: Seoul 리전, 지역 기반 접근 제어
- [x] **모니터링**: CloudWatch Alarms, SNS 알림

### 🔐 보안 및 모니터링
- [x] **Secrets 관리**: AWS Secrets Manager, GitHub Secrets
- [x] **모니터링**: Grafana 대시보드, Prometheus 메트릭
- [x] **로그 관리**: ELK Stack, 중앙화된 로그 수집
- [x] **알림 시스템**: Slack, PagerDuty 연동

---

## 📊 AWS Infrastructure 현황 요약

### 📈 생성 완료 통계
- **AWS 리소스**: 20개+ (VPC, EC2, RDS, ElastiCache, S3, CloudFront 등)
- **Terraform 모듈**: 3개 (VPC, RDS, Security)
- **환경 분리**: 3개 (dev, staging, prod)
- **공통 라이브러리**: 1개 (routepick-common)

### 🎯 핵심 성과

#### 🏗️ 완전한 AWS 인프라스트럭처
1. **AWS 클라우드**: Terraform IaC로 완전 자동화
2. **Multi-AZ 고가용성**: Seoul 리전 기반
3. **보안 최적화**: WAF + Security Groups
4. **모니터링**: CloudWatch + Grafana 완전 구성
5. **비용 최적화**: 환경별 리소스 분리

#### 📦 공통 라이브러리 완성
1. **Java Library**: Gradle 기반 공통 컴포넌트
2. **DTO 통일**: Frontend-Backend 완벽 연동
3. **Enum 체계**: 8개 TagType, 4개 SocialProvider
4. **상수 관리**: 추천 알고리즘, 한국 특화 설정
5. **Maven 발행**: 모든 프로젝트에서 공통 사용

#### 🇰🇷 한국 특화 최적화
1. **지역 설정**: Asia/Seoul 시간대, 한국어 우선
2. **GPS 범위**: 한국 좌표계 검증 (33.0-38.6N)
3. **소셜 로그인**: APPLE 제외, 4개 Provider
4. **CDN 최적화**: 서울 리전 CloudFront
5. **WAF 설정**: 한국 트래픽 우선 허용

---

*완료일: 2025-08-16*  
*핵심 성과: RoutePickr AWS Infrastructure 100% 완성*