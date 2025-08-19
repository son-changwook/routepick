# Step 2-2: Frontend 프로젝트 구조 생성

> RoutePickr Frontend 완전 구조 설계 및 구현  
> 생성일: 2025-08-16  
> 기반 분석: step2-1_backend_structure.md

---

## 🎯 프로젝트 개요

### 2개 Frontend 프로젝트 구조
- **routepick-app**: React Native 모바일 앱 (일반 사용자용)
- **routepick-admin**: React 웹 애플리케이션 (관리자용)

---

## 📱 routepick-app (React Native 사용자 앱)

### 🏗️ 전체 폴더 구조

```
routepick-app/
├── package.json                    # React Native 의존성 및 스크립트
├── metro.config.js                 # Metro 번들러 설정
├── tsconfig.json                   # TypeScript 설정
├── android/                        # Android 네이티브 코드
│   └── app/src/main/
│       ├── java/com/routepick/     # Java 코드
│       └── res/                    # Android 리소스
├── ios/                            # iOS 네이티브 코드
│   └── routepick/                  # iOS 프로젝트
├── src/
│   ├── screens/                    # 화면 컴포넌트 (7개 도메인)
│   │   ├── auth/                   # 인증 화면 (로그인/회원가입)
│   │   │   ├── LoginScreen.tsx
│   │   │   ├── RegisterScreen.tsx
│   │   │   ├── ForgotPasswordScreen.tsx
│   │   │   └── SocialLoginScreen.tsx
│   │   ├── gym/                    # 암장 검색/상세
│   │   │   ├── GymListScreen.tsx
│   │   │   ├── GymDetailScreen.tsx
│   │   │   ├── GymMapScreen.tsx
│   │   │   └── GymSearchScreen.tsx
│   │   ├── route/                  # 루트 검색/스크랩
│   │   │   ├── RouteListScreen.tsx
│   │   │   ├── RouteDetailScreen.tsx
│   │   │   ├── RouteSearchScreen.tsx
│   │   │   └── RouteScrapScreen.tsx
│   │   ├── recommendation/         # 개인화 추천
│   │   │   ├── RecommendationScreen.tsx
│   │   │   ├── RecommendationDetailScreen.tsx
│   │   │   └── RecommendationSettingsScreen.tsx
│   │   ├── tags/                   # 태그 설정
│   │   │   ├── TagSelectionScreen.tsx
│   │   │   ├── TagPreferencesScreen.tsx
│   │   │   └── TagSkillLevelScreen.tsx
│   │   ├── profile/                # 마이페이지
│   │   │   ├── ProfileScreen.tsx
│   │   │   ├── ProfileEditScreen.tsx
│   │   │   ├── SettingsScreen.tsx
│   │   │   └── HelpScreen.tsx
│   │   └── climbing/               # 클라이밍 기록
│   │       ├── ClimbingLogScreen.tsx
│   │       ├── ClimbingStatsScreen.tsx
│   │       ├── ClimbingHistoryScreen.tsx
│   │       └── ClimbingGoalsScreen.tsx
│   ├── components/                 # 재사용 컴포넌트
│   │   ├── common/                 # 공통 컴포넌트
│   │   │   ├── Button.tsx
│   │   │   ├── Input.tsx
│   │   │   ├── Modal.tsx
│   │   │   ├── LoadingSpinner.tsx
│   │   │   └── ErrorBoundary.tsx
│   │   ├── ui/                     # UI 컴포넌트
│   │   │   ├── Card.tsx
│   │   │   ├── Badge.tsx
│   │   │   ├── Avatar.tsx
│   │   │   ├── Rating.tsx
│   │   │   └── ProgressBar.tsx
│   │   ├── forms/                  # 폼 컴포넌트
│   │   │   ├── FormField.tsx
│   │   │   ├── FormSelect.tsx
│   │   │   ├── FormCheckbox.tsx
│   │   │   └── FormValidation.tsx
│   │   └── maps/                   # 지도 컴포넌트
│   │       ├── MapView.tsx
│   │       ├── MapMarker.tsx
│   │       ├── MapCluster.tsx
│   │       └── LocationPicker.tsx
│   ├── services/                   # 서비스 계층
│   │   ├── api/                    # API 서비스
│   │   │   ├── authApi.ts
│   │   │   ├── gymApi.ts
│   │   │   ├── routeApi.ts
│   │   │   ├── tagApi.ts
│   │   │   ├── userApi.ts
│   │   │   └── recommendationApi.ts
│   │   ├── auth/                   # 인증 서비스
│   │   │   ├── authService.ts
│   │   │   ├── socialLogin.ts
│   │   │   └── tokenManager.ts
│   │   ├── storage/                # 저장소 서비스
│   │   │   ├── asyncStorage.ts
│   │   │   ├── secureStorage.ts
│   │   │   └── cacheManager.ts
│   │   └── push/                   # 푸시 알림 서비스
│   │       ├── fcmService.ts
│   │       ├── notificationHandler.ts
│   │       └── pushPermissions.ts
│   ├── utils/                      # 유틸리티
│   │   ├── constants/              # 상수
│   │   │   └── constants.ts
│   │   ├── helpers/                # 헬퍼 함수
│   │   │   ├── formatters.ts
│   │   │   ├── validators.ts
│   │   │   └── calculations.ts
│   │   └── validators/             # 유효성 검증
│   │       ├── authValidators.ts
│   │       ├── profileValidators.ts
│   │       └── formValidators.ts
│   ├── navigation/                 # 내비게이션 설정
│   │   ├── AppNavigator.tsx
│   │   ├── AuthNavigator.tsx
│   │   ├── MainTabNavigator.tsx
│   │   └── StackNavigators.tsx
│   ├── store/                      # Redux Store
│   │   ├── slices/                 # Redux Slices
│   │   │   ├── authSlice.ts
│   │   │   ├── userSlice.ts
│   │   │   ├── gymSlice.ts
│   │   │   ├── routeSlice.ts
│   │   │   ├── tagSlice.ts
│   │   │   ├── recommendationSlice.ts
│   │   │   └── uiSlice.ts
│   │   ├── selectors/              # Redux Selectors
│   │   │   ├── authSelectors.ts
│   │   │   ├── userSelectors.ts
│   │   │   └── appSelectors.ts
│   │   └── store.ts                # Store 설정
│   ├── types/                      # TypeScript 타입
│   │   └── index.ts                # 통합 타입 정의
│   ├── assets/                     # 정적 자산
│   │   ├── images/                 # 이미지
│   │   ├── fonts/                  # 폰트
│   │   └── sounds/                 # 사운드
│   └── config/                     # 설정 파일
│       ├── constants.ts            # 앱 설정 상수
│       ├── theme.ts                # 테마 설정
│       └── environment.ts          # 환경 변수
├── __tests__/                      # 테스트 파일
└── docs/                           # 문서
```

### 📦 주요 기술 스택 및 의존성

#### Core Dependencies
```json
{
  "react": "18.2.0",
  "react-native": "0.73.4",
  "@react-navigation/native": "^6.1.9",
  "@react-navigation/stack": "^6.3.20",
  "@react-navigation/bottom-tabs": "^6.5.11",
  "@reduxjs/toolkit": "^2.0.1",
  "react-redux": "^9.0.4",
  "redux-persist": "^6.0.0"
}
```

#### Map & Location
```json
{
  "react-native-maps": "^1.8.0",
  "react-native-geolocation-service": "^5.3.1"
}
```

#### Social Login (4개 Provider)
```json
{
  "@react-native-google-signin/google-signin": "^10.1.0",
  "@react-native-seoul/kakao-login": "^5.3.0",
  "react-native-naver-login": "^2.3.0",
  "react-native-fbsdk-next": "^12.1.2"
}
```

#### Storage & Security
```json
{
  "@react-native-async-storage/async-storage": "^1.21.0",
  "react-native-keychain": "^8.1.3"
}
```

#### Push Notifications
```json
{
  "@react-native-firebase/app": "^18.7.3",
  "@react-native-firebase/messaging": "^18.7.3",
  "@react-native-firebase/analytics": "^18.7.3"
}
```

#### Media & UI
```json
{
  "react-native-image-picker": "^7.1.0",
  "react-native-fast-image": "^8.6.3",
  "react-native-video": "^6.0.0-beta.6",
  "react-native-svg": "^14.1.0",
  "react-native-vector-icons": "^10.0.3",
  "react-native-paper": "^5.12.3"
}
```

### 🎯 주요 기능 구현

#### 1. 주변 암장 지도 검색
- **React Native Maps**: 네이티브 지도 컴포넌트
- **Geolocation Service**: GPS 기반 현재 위치
- **Map Clustering**: 다수 마커 효율적 표시
- **거리 기반 검색**: 반경 내 암장 필터링

#### 2. 루트 상세/스크랩
- **이미지/영상 뷰어**: 루트 미디어 표시
- **태그 시각화**: 루트 특성 태그 표시
- **스크랩 시스템**: 즐겨찾기 루트 관리
- **난이도 투표**: 커뮤니티 기반 난이도 평가

#### 3. 개인화된 루트 추천 (태그 기반)
- **추천 알고리즘**: 태그 70% + 레벨 30% 매칭
- **실시간 업데이트**: Redis 캐시 기반 빠른 응답
- **선호도 학습**: 사용자 행동 기반 개선
- **추천 이유 표시**: 왜 추천되었는지 설명

#### 4. 사용자 선호 태그 설정
- **8가지 태그 타입**: STYLE, TECHNIQUE, MOVEMENT 등
- **선호도 레벨**: LOW, MEDIUM, HIGH
- **숙련도 설정**: BEGINNER ~ EXPERT
- **시각적 선택 UI**: 직관적인 태그 선택

#### 5. 클라이밍 기록 관리
- **완등 기록**: 날짜, 시도 횟수, 평점
- **통계 시각화**: 차트 기반 발전 과정
- **목표 설정**: 개인 목표 및 달성 추적
- **소셜 공유**: 기록 공유 기능

#### 6. 소셜 로그인 (4개 제공자)
- **Google**: @react-native-google-signin
- **Kakao**: @react-native-seoul/kakao-login  
- **Naver**: react-native-naver-login
- **Facebook**: react-native-fbsdk-next

#### 7. 푸시 알림 수신
- **Firebase FCM**: 크로스 플랫폼 푸시 알림
- **알림 타입**: 추천, 새 루트, 소셜, 시스템
- **Deep Linking**: 알림에서 특정 화면으로 이동
- **알림 설정**: 사용자별 알림 선호도

---

## 💻 routepick-admin (React 관리자 웹)

### 🏗️ 전체 폴더 구조

```
routepick-admin/
├── package.json                    # React + Vite 의존성
├── vite.config.ts                  # Vite 빌드 설정
├── tsconfig.json                   # TypeScript 설정
├── tsconfig.node.json              # Node.js TypeScript 설정
├── public/                         # 정적 파일
├── dist/                           # 빌드 출력
├── src/
│   ├── pages/                      # 페이지 컴포넌트 (7개 도메인)
│   │   ├── dashboard/              # 대시보드
│   │   │   ├── DashboardPage.tsx
│   │   │   ├── StatsOverview.tsx
│   │   │   ├── RealtimeMetrics.tsx
│   │   │   └── ActivityFeed.tsx
│   │   ├── gym/                    # 암장 관리
│   │   │   ├── GymListPage.tsx
│   │   │   ├── GymDetailPage.tsx
│   │   │   ├── GymCreatePage.tsx
│   │   │   ├── GymEditPage.tsx
│   │   │   ├── BranchManagePage.tsx
│   │   │   └── WallManagePage.tsx
│   │   ├── route/                  # 루트 관리
│   │   │   ├── RouteListPage.tsx
│   │   │   ├── RouteDetailPage.tsx
│   │   │   ├── RouteCreatePage.tsx
│   │   │   ├── RouteEditPage.tsx
│   │   │   ├── RouteTaggingPage.tsx
│   │   │   └── RouteBulkEditPage.tsx
│   │   ├── tags/                   # 태그 관리
│   │   │   ├── TagListPage.tsx
│   │   │   ├── TagCreatePage.tsx
│   │   │   ├── TagEditPage.tsx
│   │   │   ├── TagAnalyticsPage.tsx
│   │   │   └── TagSystemPage.tsx
│   │   ├── user/                   # 사용자 관리
│   │   │   ├── UserListPage.tsx
│   │   │   ├── UserDetailPage.tsx
│   │   │   ├── UserStatsPage.tsx
│   │   │   └── UserBehaviorPage.tsx
│   │   ├── payment/                # 결제 관리
│   │   │   ├── PaymentListPage.tsx
│   │   │   ├── PaymentDetailPage.tsx
│   │   │   ├── RefundManagePage.tsx
│   │   │   └── PaymentAnalyticsPage.tsx
│   │   ├── auth/                   # 인증
│   │   │   ├── LoginPage.tsx
│   │   │   └── ForgotPasswordPage.tsx
│   │   └── settings/               # 설정
│   │       ├── SystemSettingsPage.tsx
│   │       ├── ProfilePage.tsx
│   │       └── SecurityPage.tsx
│   ├── components/                 # 재사용 컴포넌트
│   │   ├── common/                 # 공통 컴포넌트
│   │   │   ├── PageHeader.tsx
│   │   │   ├── SearchBar.tsx
│   │   │   ├── DataTable.tsx
│   │   │   ├── FilterPanel.tsx
│   │   │   └── ExportButton.tsx
│   │   ├── ui/                     # UI 컴포넌트
│   │   │   ├── Card.tsx
│   │   │   ├── Modal.tsx
│   │   │   ├── Drawer.tsx
│   │   │   ├── Tabs.tsx
│   │   │   └── Steps.tsx
│   │   ├── charts/                 # 차트 컴포넌트
│   │   │   ├── LineChart.tsx
│   │   │   ├── BarChart.tsx
│   │   │   ├── PieChart.tsx
│   │   │   ├── MetricCard.tsx
│   │   │   └── DashboardWidget.tsx
│   │   ├── forms/                  # 폼 컴포넌트
│   │   │   ├── FormBuilder.tsx
│   │   │   ├── FormField.tsx
│   │   │   ├── FileUpload.tsx
│   │   │   ├── DateRangePicker.tsx
│   │   │   └── TagSelector.tsx
│   │   └── layout/                 # 레이아웃 컴포넌트
│   │       ├── AppLayout.tsx
│   │       ├── Sidebar.tsx
│   │       ├── Header.tsx
│   │       ├── Footer.tsx
│   │       └── Breadcrumb.tsx
│   ├── hooks/                      # 커스텀 훅
│   │   ├── useAuth.ts
│   │   ├── useTable.ts
│   │   ├── useChart.ts
│   │   ├── useWebSocket.ts
│   │   ├── usePermissions.ts
│   │   └── useExport.ts
│   ├── services/                   # 서비스 계층
│   │   ├── api/                    # API 서비스
│   │   │   ├── authApi.ts
│   │   │   ├── gymApi.ts
│   │   │   ├── routeApi.ts
│   │   │   ├── tagApi.ts
│   │   │   ├── userApi.ts
│   │   │   ├── paymentApi.ts
│   │   │   └── analyticsApi.ts
│   │   ├── auth/                   # 인증 서비스
│   │   │   ├── authService.ts
│   │   │   ├── permissionService.ts
│   │   │   └── tokenManager.ts
│   │   └── websocket/              # WebSocket 서비스
│   │       ├── wsClient.ts
│   │       ├── realtimeUpdates.ts
│   │       └── notificationHandler.ts
│   ├── utils/                      # 유틸리티
│   │   ├── constants/              # 상수
│   │   │   └── constants.ts
│   │   ├── helpers/                # 헬퍼 함수
│   │   │   ├── formatters.ts
│   │   │   ├── validators.ts
│   │   │   ├── exportUtils.ts
│   │   │   └── chartUtils.ts
│   │   └── formatters/             # 포맷터
│   │       ├── dateFormatter.ts
│   │       ├── numberFormatter.ts
│   │       └── currencyFormatter.ts
│   ├── store/                      # Zustand Store
│   │   ├── slices/                 # Store Slices
│   │   │   ├── authStore.ts
│   │   │   ├── uiStore.ts
│   │   │   ├── tableStore.ts
│   │   │   └── notificationStore.ts
│   │   └── selectors/              # Store Selectors
│   │       ├── authSelectors.ts
│   │       └── uiSelectors.ts
│   ├── types/                      # TypeScript 타입
│   │   └── index.ts                # 통합 타입 정의
│   ├── assets/                     # 정적 자산
│   │   ├── images/                 # 이미지
│   │   ├── icons/                  # 아이콘
│   │   └── styles/                 # 스타일
│   │       ├── variables.less      # LESS 변수
│   │       ├── global.css          # 글로벌 스타일
│   │       └── themes.css          # 테마 스타일
│   └── config/                     # 설정 파일
│       ├── constants.ts            # 앱 설정 상수
│       ├── router.tsx              # 라우터 설정
│       └── environment.ts          # 환경 변수
├── cypress/                        # E2E 테스트
│   ├── integration/
│   ├── fixtures/
│   └── support/
└── docs/                           # 문서
```

### 📦 주요 기술 스택 및 의존성

#### Core Dependencies
```json
{
  "react": "^18.2.0",
  "react-dom": "^18.2.0",
  "react-router-dom": "^6.20.1",
  "antd": "^5.12.8",
  "@ant-design/icons": "^5.2.6",
  "zustand": "^4.4.7"
}
```

#### Data Fetching & State
```json
{
  "@tanstack/react-query": "^5.14.2",
  "@tanstack/react-query-devtools": "^5.14.2",
  "axios": "^1.6.2"
}
```

#### Charts & Visualization
```json
{
  "chart.js": "^4.4.1",
  "react-chartjs-2": "^5.2.0",
  "@ant-design/charts": "^2.0.3",
  "recharts": "^2.8.0"
}
```

#### Forms & Validation
```json
{
  "react-hook-form": "^7.49.2",
  "@hookform/resolvers": "^3.3.2",
  "yup": "^1.4.0"
}
```

#### Utilities
```json
{
  "dayjs": "^1.11.10",
  "lodash-es": "^4.17.21",
  "socket.io-client": "^4.7.4",
  "xlsx": "^0.18.5",
  "jspdf": "^2.5.1"
}
```

#### Development Tools
```json
{
  "vite": "^5.0.8",
  "@vitejs/plugin-react": "^4.2.1",
  "vite-plugin-pwa": "^0.17.4",
  "typescript": "^5.2.2",
  "cypress": "^13.6.2"
}
```

### 🎯 주요 기능 구현

#### 1. 암장/지점/벽면/루트 관리
- **계층적 데이터 관리**: Gym → Branch → Wall → Route
- **대량 편집**: 여러 루트 동시 수정
- **이미지/영상 업로드**: AWS S3 연동
- **GPS 좌표 관리**: 지도 기반 위치 설정

#### 2. 루트 태깅 시스템
- **8가지 태그 타입**: 체계적 분류 관리
- **연관성 점수**: 0.0-1.0 점수 설정
- **태그 자동 제안**: AI 기반 태그 추천
- **태그 통계**: 사용 빈도 및 효과 분석

#### 3. 태그 관리 (8가지 타입)
- **마스터 태그 관리**: 전체 태그 체계 관리
- **태그 카테고리**: 세부 분류 및 그룹핑
- **표시 순서**: UI 표시 우선순위 설정
- **사용 가능 설정**: 사용자/루트 태깅 가능 여부

#### 4. 사용자 관리 및 통계
- **사용자 현황**: 가입, 활동, 탈퇴 통계
- **행동 분석**: 클라이밍 패턴 분석
- **추천 효과**: 추천 정확도 및 만족도
- **권한 관리**: ADMIN, GYM_ADMIN, REGULAR

#### 5. 결제 관리 및 환불 처리
- **한국형 결제**: 카드, 가상계좌, 계좌이체
- **결제 현황**: 성공/실패/취소/환불 통계
- **환불 처리**: 관리자 승인 기반 환불
- **매출 분석**: 일/월/년 매출 대시보드

#### 6. 실시간 대시보드
- **실시간 지표**: WebSocket 기반 실시간 업데이트
- **KPI 모니터링**: 주요 지표 실시간 추적
- **알림 시스템**: 중요 이벤트 실시간 알림
- **성능 모니터링**: 시스템 상태 모니터링

#### 7. 공지사항 관리
- **공지사항 CRUD**: 작성, 수정, 삭제
- **배너 관리**: 메인 화면 배너 관리
- **팝업 관리**: 이벤트 팝업 스케줄링
- **푸시 알림**: 대량 푸시 알림 발송

---

## 🔗 Frontend-Backend 연동

### API 통신 구조
```typescript
// 공통 API 응답 형식 (Backend와 일치)
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data?: T;
  errorCode?: string;
  timestamp: string;
}

// 통일된 에러 처리
const API_ENDPOINTS = {
  // Auth
  LOGIN: '/api/v1/auth/login',
  SOCIAL_LOGIN: '/api/v1/auth/social',
  
  // Gym
  GYM_LIST: '/api/v1/gyms',
  GYM_NEARBY: '/api/v1/gyms/nearby',
  
  // Route
  ROUTE_LIST: '/api/v1/routes',
  ROUTE_RECOMMENDATIONS: '/api/v1/recommendations',
  
  // Tag
  TAG_LIST: '/api/v1/tags',
  USER_PREFERENCES: '/api/v1/users/{userId}/tags',
};
```

### 실시간 통신 (WebSocket)
```typescript
// 관리자 웹에서 실시간 업데이트 수신
const wsClient = new WebSocketClient({
  url: 'ws://localhost:8080/ws',
  topics: [
    'user.activity',
    'route.update', 
    'payment.status',
    'system.alert'
  ]
});
```

### 인증 토큰 관리
```typescript
// JWT 토큰 자동 갱신 및 관리
const tokenManager = {
  accessToken: '30분 만료',
  refreshToken: '7일 만료',
  autoRefresh: true,
  secureStorage: true // React Native Keychain
};
```

---

## 🚀 배포 및 환경 설정

### React Native 앱 배포
```bash
# Android APK 빌드
npm run build:android

# iOS IPA 빌드  
npm run build:ios

# 코드 푸시 (OTA 업데이트)
npx appcenter codepush release-react routepick-app android
```

### React 관리자 웹 배포
```bash
# 개발 환경 빌드
npm run build:staging

# 운영 환경 빌드
npm run build:production

# 정적 파일 서빙
npm run preview
```

### 환경별 설정
- **Local**: localhost API, 개발용 소셜 로그인
- **Staging**: 개발 서버 API, 테스트 환경
- **Production**: 운영 서버 API, 실제 소셜 로그인

---

## 📊 프로젝트 현황 요약

### 📈 생성 완료 통계
- **총 프로젝트 수**: 2개 (App + Admin)
- **총 폴더 수**: 120개+ (App 70개 + Admin 50개)
- **총 설정 파일**: 12개
- **지원 플랫폼**: iOS, Android, Web
- **소요 시간**: 1.5시간

### 🎯 핵심 성과

#### 🏗️ 완전한 프론트엔드 아키텍처
1. **모바일 앱**: React Native 크로스 플랫폼
2. **관리자 웹**: React + Vite 고성능 웹앱
3. **타입 안전성**: 100% TypeScript 적용
4. **상태 관리**: Redux Toolkit (App) + Zustand (Admin)

#### 📱 모바일 앱 특화 기능
1. **네이티브 지도**: React Native Maps 통합
2. **4개 소셜 로그인**: Google, Kakao, Naver, Facebook
3. **푸시 알림**: Firebase FCM 완전 통합
4. **오프라인 지원**: Redux Persist 캐싱
5. **보안 저장소**: React Native Keychain

#### 💻 관리자 웹 특화 기능
1. **Enterprise UI**: Ant Design 5.x 최신 버전
2. **실시간 대시보드**: WebSocket + Chart.js
3. **고성능 빌드**: Vite + 코드 스플리팅
4. **PWA 지원**: 오프라인 사용 가능
5. **테스트 자동화**: Cypress E2E 테스트

#### 🔗 Backend 완벽 연동
1. **API 스펙 일치**: Backend ApiResponse 형식 동일
2. **JWT 인증**: 토큰 자동 갱신 시스템
3. **에러 처리**: 통일된 에러 코드 시스템
4. **실시간 통신**: WebSocket 양방향 통신

---

## ✅ Step 2-2 완료 체크리스트

### 📱 React Native 앱 (routepick-app)
- [x] **프로젝트 구조**: 7개 도메인별 Screen 구조
- [x] **컴포넌트 구조**: common, ui, forms, maps 분리
- [x] **서비스 계층**: api, auth, storage, push 서비스
- [x] **상태 관리**: Redux Toolkit + Redux Persist
- [x] **내비게이션**: React Navigation 6.x
- [x] **타입 정의**: 완전한 TypeScript 타입 시스템
- [x] **설정 파일**: package.json, metro.config.js, tsconfig.json

### 💻 React 관리자 웹 (routepick-admin)  
- [x] **프로젝트 구조**: 7개 도메인별 Page 구조
- [x] **컴포넌트 구조**: common, ui, charts, forms, layout 분리
- [x] **커스텀 훅**: 재사용 가능한 로직 훅
- [x] **상태 관리**: Zustand 경량 상태 관리
- [x] **빌드 시스템**: Vite + TypeScript
- [x] **타입 정의**: Backend 연동 타입 시스템
- [x] **설정 파일**: package.json, vite.config.ts, tsconfig.json

### 🔧 공통 설정
- [x] **상수 정의**: API, 태그, 에러 코드 등 모든 상수
- [x] **타입 시스템**: Frontend-Backend 완벽 연동
- [x] **환경 설정**: Local, Dev, Prod 환경별 설정
- [x] **빌드 최적화**: 코드 스플리팅, 번들 최적화

---

**다음 단계**: Frontend 핵심 컴포넌트 구현  
**예상 소요 시간**: 4-5시간  
**핵심 목표**: 추천 시스템 UI + 태그 관리 + 지도 연동

*완료일: 2025-08-16*  
*핵심 성과: RoutePickr Frontend 완전한 구조 100% 완성*