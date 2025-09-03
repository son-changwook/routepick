# step2-2a_frontend_app_structure.md

> RoutePickr Frontend App 구조 설계 (React Native 사용자 앱)
> 생성일: 2025-08-16  
> 기반 분석: step2-1_backend_structure.md

---

## 🎯 프로젝트 개요

### React Native 모바일 앱 구조
- **routepick-app**: React Native 모바일 앱 (일반 사용자용)

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
│   └── routepick/
│       ├── Info.plist              # iOS 앱 설정
│       └── AppDelegate.swift       # iOS 앱 델리게이트
├── src/                            # 메인 소스 코드
│   ├── components/                 # 재사용 가능한 컴포넌트들
│   ├── screens/                    # 화면별 컴포넌트들
│   ├── navigation/                 # 네비게이션 설정
│   ├── services/                   # API 서비스와 비즈니스 로직
│   ├── store/                      # 상태 관리 (Redux/Zustand)
│   ├── hooks/                      # 커스텀 훅들
│   ├── utils/                      # 유틸리티 함수들
│   ├── types/                      # TypeScript 타입 정의
│   ├── constants/                  # 상수들
│   ├── assets/                     # 이미지, 폰트 등의 정적 자원
│   └── App.tsx                     # 앱의 진입점
├── __tests__/                      # 테스트 파일들
├── .env                            # 환경 변수
└── .gitignore                      # Git 무시 파일들
```

### 📁 src/ 상세 구조

#### 🧩 components/ - 재사용 컴포넌트

```
src/components/
├── common/                         # 공통 컴포넌트
│   ├── Button/
│   │   ├── index.tsx
│   │   └── Button.styles.ts
│   ├── Input/
│   │   ├── index.tsx
│   │   └── Input.styles.ts
│   ├── Modal/
│   ├── Loading/
│   ├── Toast/
│   └── ErrorBoundary/
├── layout/                         # 레이아웃 컴포넌트
│   ├── Header/
│   ├── TabBar/
│   ├── Sidebar/
│   └── Container/
├── ui/                             # UI 전용 컴포넌트
│   ├── Typography/
│   ├── Card/
│   ├── Badge/
│   ├── Avatar/
│   ├── Icon/
│   └── Divider/
├── forms/                          # 폼 관련 컴포넌트
│   ├── LoginForm/
│   ├── RegisterForm/
│   ├── ProfileForm/
│   └── SearchForm/
├── gym/                            # 체육관 관련 컴포넌트
│   ├── GymCard/
│   ├── GymList/
│   ├── GymMap/
│   └── GymInfo/
├── route/                          # 루트 관련 컴포넌트
│   ├── RouteCard/
│   ├── RouteList/
│   ├── RouteDetail/
│   ├── RouteDifficulty/
│   └── RouteFilter/
├── climbing/                       # 클라이밍 관련 컴포넌트
│   ├── ClimbingRecord/
│   ├── ClimbingStats/
│   ├── ClimbingChart/
│   └── ClimbingLevel/
├── community/                      # 커뮤니티 컴포넌트
│   ├── PostCard/
│   ├── PostList/
│   ├── CommentSection/
│   └── LikeButton/
└── media/                          # 미디어 컴포넌트
    ├── ImagePicker/
    ├── VideoPlayer/
    ├── Camera/
    └── Gallery/
```

#### 📱 screens/ - 화면별 컴포넌트

```
src/screens/
├── auth/                           # 인증 관련 화면
│   ├── LoginScreen.tsx
│   ├── RegisterScreen.tsx
│   ├── ForgotPasswordScreen.tsx
│   └── SocialLoginScreen.tsx
├── onboarding/                     # 온보딩 화면
│   ├── WelcomeScreen.tsx
│   ├── TutorialScreen.tsx
│   └── PermissionScreen.tsx
├── home/                           # 홈 화면
│   ├── HomeScreen.tsx
│   ├── RecommendationSection.tsx
│   └── PopularRoutesSection.tsx
├── gym/                            # 체육관 관련 화면
│   ├── GymListScreen.tsx
│   ├── GymDetailScreen.tsx
│   ├── GymMapScreen.tsx
│   └── GymSearchScreen.tsx
├── route/                          # 루트 관련 화면
│   ├── RouteListScreen.tsx
│   ├── RouteDetailScreen.tsx
│   ├── RouteSearchScreen.tsx
│   ├── RouteFilterScreen.tsx
│   └── RouteCompareScreen.tsx
├── climbing/                       # 클라이밍 기록 화면
│   ├── ClimbingRecordScreen.tsx
│   ├── ClimbingStatsScreen.tsx
│   ├── ClimbingHistoryScreen.tsx
│   └── AchievementScreen.tsx
├── profile/                        # 프로필 관련 화면
│   ├── ProfileScreen.tsx
│   ├── EditProfileScreen.tsx
│   ├── PreferencesScreen.tsx
│   └── FollowersScreen.tsx
├── community/                      # 커뮤니티 화면
│   ├── CommunityScreen.tsx
│   ├── PostDetailScreen.tsx
│   ├── CreatePostScreen.tsx
│   └── EditPostScreen.tsx
├── message/                        # 메시지 화면
│   ├── MessageListScreen.tsx
│   ├── MessageDetailScreen.tsx
│   └── CreateMessageScreen.tsx
└── settings/                       # 설정 화면
    ├── SettingsScreen.tsx
    ├── NotificationSettingsScreen.tsx
    ├── PrivacySettingsScreen.tsx
    └── AccountSettingsScreen.tsx
```

#### 🧭 navigation/ - 네비게이션 설정

```
src/navigation/
├── AppNavigator.tsx                # 메인 네비게이터
├── AuthNavigator.tsx               # 인증 관련 네비게이션
├── TabNavigator.tsx                # 하단 탭 네비게이션
├── StackNavigator.tsx              # 스택 네비게이션
├── DrawerNavigator.tsx             # 사이드 드로어 네비게이션
├── types.ts                        # 네비게이션 타입 정의
└── LinkingConfiguration.ts         # 딥링크 설정
```

#### 🔗 services/ - API 서비스

```
src/services/
├── api/                            # API 관련
│   ├── client.ts                   # HTTP 클라이언트 설정
│   ├── auth.ts                     # 인증 API
│   ├── user.ts                     # 사용자 API
│   ├── gym.ts                      # 체육관 API
│   ├── route.ts                    # 루트 API
│   ├── climbing.ts                 # 클라이밍 API
│   ├── community.ts                # 커뮤니티 API
│   └── message.ts                  # 메시지 API
├── storage/                        # 로컬 저장소
│   ├── AsyncStorage.ts
│   ├── SecureStorage.ts
│   └── Cache.ts
├── push/                           # 푸시 알림
│   ├── PushNotification.ts
│   └── FCM.ts
├── location/                       # 위치 서비스
│   ├── LocationService.ts
│   └── GPS.ts
├── media/                          # 미디어 처리
│   ├── ImageService.ts
│   ├── CameraService.ts
│   └── VideoService.ts
└── analytics/                      # 분석
    ├── Analytics.ts
    └── Crashlytics.ts
```

#### 🗄️ store/ - 상태 관리

```
src/store/
├── index.ts                        # 스토어 설정
├── slices/                         # Redux Toolkit 슬라이스들
│   ├── authSlice.ts               # 인증 상태
│   ├── userSlice.ts               # 사용자 상태
│   ├── gymSlice.ts                # 체육관 상태
│   ├── routeSlice.ts              # 루트 상태
│   ├── climbingSlice.ts           # 클라이밍 상태
│   ├── communitySlice.ts          # 커뮤니티 상태
│   └── settingsSlice.ts           # 설정 상태
├── middleware/                     # 미들웨어
│   ├── authMiddleware.ts
│   └── loggingMiddleware.ts
└── selectors/                      # 셀렉터들
    ├── authSelectors.ts
    ├── userSelectors.ts
    └── gymSelectors.ts
```

#### 🪝 hooks/ - 커스텀 훅

```
src/hooks/
├── auth/                           # 인증 관련 훅
│   ├── useAuth.ts
│   ├── useLogin.ts
│   └── useLogout.ts
├── api/                            # API 관련 훅
│   ├── useQuery.ts                # React Query 래퍼
│   ├── useMutation.ts
│   └── useInfiniteQuery.ts
├── navigation/                     # 네비게이션 훅
│   ├── useNavigation.ts
│   └── useRoute.ts
├── form/                           # 폼 관련 훅
│   ├── useForm.ts
│   └── useValidation.ts
├── device/                         # 디바이스 관련 훅
│   ├── useLocation.ts
│   ├── useCamera.ts
│   ├── usePermissions.ts
│   └── useNetInfo.ts
└── common/                         # 공통 훅
    ├── useDebounce.ts
    ├── useThrottle.ts
    ├── useAsync.ts
    └── usePrevious.ts
```

#### 🛠️ utils/ - 유틸리티

```
src/utils/
├── validation/                     # 유효성 검사
│   ├── validators.ts
│   └── schemas.ts
├── formatting/                     # 포맷팅
│   ├── date.ts
│   ├── currency.ts
│   ├── text.ts
│   └── number.ts
├── conversion/                     # 변환
│   ├── difficulty.ts              # V등급 ↔ YDS 변환
│   ├── coordinate.ts              # 좌표계 변환
│   └── unit.ts                    # 단위 변환
├── security/                       # 보안
│   ├── encryption.ts
│   └── sanitization.ts
├── location/                       # 위치 관련
│   ├── gps.ts                     # GPS 유틸리티
│   ├── distance.ts                # 거리 계산
│   └── korean-bounds.ts           # 한국 영토 검증
└── error/                          # 에러 처리
    ├── errorHandler.ts
    └── errorReporting.ts
```

#### 📝 types/ - TypeScript 타입

```
src/types/
├── api/                            # API 타입
│   ├── auth.ts
│   ├── user.ts
│   ├── gym.ts
│   ├── route.ts
│   ├── climbing.ts
│   ├── community.ts
│   └── message.ts
├── navigation/                     # 네비게이션 타입
│   └── index.ts
├── store/                          # 스토어 타입
│   └── index.ts
├── common/                         # 공통 타입
│   ├── index.ts
│   ├── enums.ts
│   └── interfaces.ts
└── external/                       # 외부 라이브러리 타입 확장
    └── declarations.d.ts
```

### ⚙️ 주요 설정 파일

#### package.json 
```json
{
  "name": "routepick-app",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "android": "react-native run-android",
    "ios": "react-native run-ios",
    "start": "react-native start",
    "test": "jest",
    "lint": "eslint .",
    "build:android": "cd android && ./gradlew assembleRelease",
    "build:ios": "xcodebuild -workspace ios/routepick.xcworkspace -scheme routepick archive"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-native": "^0.72.0",
    "@react-navigation/native": "^6.1.0",
    "@react-navigation/stack": "^6.3.0",
    "@react-navigation/bottom-tabs": "^6.5.0",
    "@reduxjs/toolkit": "^1.9.0",
    "react-redux": "^8.1.0",
    "@tanstack/react-query": "^4.29.0",
    "react-native-maps": "^1.7.0",
    "react-native-image-picker": "^5.6.0",
    "react-native-push-notification": "^8.1.0",
    "@react-native-async-storage/async-storage": "^1.19.0",
    "react-native-keychain": "^8.1.0",
    "react-native-permissions": "^3.8.0",
    "react-native-vector-icons": "^10.0.0"
  }
}
```

### 🎨 스타일링 및 테마

#### 스타일링 전략
```
src/styles/
├── theme/                          # 테마 설정
│   ├── colors.ts                  # 컬러 팔레트
│   ├── typography.ts              # 폰트 스타일
│   ├── spacing.ts                 # 간격 설정
│   └── index.ts                   # 테마 통합
├── components/                     # 컴포넌트별 스타일
└── screens/                        # 화면별 스타일
```

#### 컬러 팔레트
```typescript
// src/styles/theme/colors.ts
export const colors = {
  primary: {
    50: '#f0f9ff',
    500: '#3b82f6',
    900: '#1e3a8a'
  },
  gray: {
    50: '#f9fafb',
    500: '#6b7280',
    900: '#111827'
  },
  danger: '#ef4444',
  success: '#10b981',
  warning: '#f59e0b'
}
```