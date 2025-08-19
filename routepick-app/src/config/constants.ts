// API 설정
export const API_CONFIG = {
  BASE_URL: __DEV__ ? 'http://localhost:8080' : 'https://api.routepick.com',
  TIMEOUT: 10000,
  RETRY_COUNT: 3,
} as const;

// 앱 설정
export const APP_CONFIG = {
  NAME: 'RoutePickr',
  VERSION: '1.0.0',
  BUILD_NUMBER: '1',
  BUNDLE_ID: 'com.routepick.app',
} as const;

// 소셜 로그인 설정
export const SOCIAL_LOGIN = {
  GOOGLE: {
    WEB_CLIENT_ID: 'YOUR_GOOGLE_WEB_CLIENT_ID',
    IOS_CLIENT_ID: 'YOUR_GOOGLE_IOS_CLIENT_ID',
    ANDROID_CLIENT_ID: 'YOUR_GOOGLE_ANDROID_CLIENT_ID',
  },
  KAKAO: {
    NATIVE_APP_KEY: 'YOUR_KAKAO_NATIVE_APP_KEY',
    JAVASCRIPT_KEY: 'YOUR_KAKAO_JAVASCRIPT_KEY',
  },
  NAVER: {
    CLIENT_ID: 'YOUR_NAVER_CLIENT_ID',
    CLIENT_SECRET: 'YOUR_NAVER_CLIENT_SECRET',
    SERVICE_URL_SCHEME: 'naverlogin',
  },
  FACEBOOK: {
    APP_ID: 'YOUR_FACEBOOK_APP_ID',
    APP_NAME: 'RoutePickr',
  },
} as const;

// 지도 설정
export const MAP_CONFIG = {
  DEFAULT_REGION: {
    latitude: 37.5665, // 서울시 중심
    longitude: 126.9780,
    latitudeDelta: 0.0922,
    longitudeDelta: 0.0421,
  },
  KOREA_BOUNDS: {
    northeast: { latitude: 38.6, longitude: 132.0 },
    southwest: { latitude: 33.0, longitude: 124.0 },
  },
  ZOOM_LEVELS: {
    CITY: 0.1,
    DISTRICT: 0.05,
    DETAIL: 0.01,
  },
} as const;

// 저장소 키
export const STORAGE_KEYS = {
  USER_TOKEN: '@RoutePickr:userToken',
  USER_PROFILE: '@RoutePickr:userProfile',
  USER_PREFERENCES: '@RoutePickr:userPreferences',
  SELECTED_TAGS: '@RoutePickr:selectedTags',
  RECENT_SEARCHES: '@RoutePickr:recentSearches',
  APP_SETTINGS: '@RoutePickr:appSettings',
  ONBOARDING_COMPLETED: '@RoutePickr:onboardingCompleted',
} as const;

// 추천 시스템 설정
export const RECOMMENDATION_CONFIG = {
  TAG_WEIGHT: 0.7,
  LEVEL_WEIGHT: 0.3,
  MIN_SCORE_THRESHOLD: 20,
  MAX_RECOMMENDATIONS: 50,
  CACHE_DURATION: 24 * 60 * 60 * 1000, // 24시간
} as const;

// 태그 타입
export const TAG_TYPES = {
  STYLE: 'STYLE',
  FEATURE: 'FEATURE',
  TECHNIQUE: 'TECHNIQUE',
  DIFFICULTY: 'DIFFICULTY',
  MOVEMENT: 'MOVEMENT',
  HOLD_TYPE: 'HOLD_TYPE',
  WALL_ANGLE: 'WALL_ANGLE',
  OTHER: 'OTHER',
} as const;

// 사용자 타입
export const USER_TYPES = {
  REGULAR: 'REGULAR',
  GYM_ADMIN: 'GYM_ADMIN',
  ADMIN: 'ADMIN',
} as const;

// 푸시 알림 타입
export const NOTIFICATION_TYPES = {
  ROUTE_RECOMMENDATION: 'ROUTE_RECOMMENDATION',
  NEW_ROUTE: 'NEW_ROUTE',
  CLIMB_REMINDER: 'CLIMB_REMINDER',
  SOCIAL_FOLLOW: 'SOCIAL_FOLLOW',
  SYSTEM_NOTICE: 'SYSTEM_NOTICE',
} as const;

// 에러 코드
export const ERROR_CODES = {
  NETWORK_ERROR: 'NETWORK_ERROR',
  UNAUTHORIZED: 'UNAUTHORIZED',
  FORBIDDEN: 'FORBIDDEN',
  NOT_FOUND: 'NOT_FOUND',
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  LOCATION_PERMISSION_DENIED: 'LOCATION_PERMISSION_DENIED',
  CAMERA_PERMISSION_DENIED: 'CAMERA_PERMISSION_DENIED',
} as const;

// 화면 이름
export const SCREEN_NAMES = {
  // Auth
  LOGIN: 'Login',
  REGISTER: 'Register',
  FORGOT_PASSWORD: 'ForgotPassword',
  
  // Main
  HOME: 'Home',
  SEARCH: 'Search',
  PROFILE: 'Profile',
  SETTINGS: 'Settings',
  
  // Gym
  GYM_LIST: 'GymList',
  GYM_DETAIL: 'GymDetail',
  GYM_MAP: 'GymMap',
  
  // Route
  ROUTE_LIST: 'RouteList',
  ROUTE_DETAIL: 'RouteDetail',
  ROUTE_SEARCH: 'RouteSearch',
  
  // Recommendation
  RECOMMENDATIONS: 'Recommendations',
  
  // Tags
  TAG_SELECTION: 'TagSelection',
  TAG_PREFERENCES: 'TagPreferences',
  
  // Climbing
  CLIMBING_LOG: 'ClimbingLog',
  CLIMBING_STATS: 'ClimbingStats',
} as const;

// 디바이스 설정
export const DEVICE_CONFIG = {
  MIN_IOS_VERSION: '12.0',
  MIN_ANDROID_SDK: 21,
  SUPPORTED_ORIENTATIONS: ['portrait', 'portrait-upside-down'],
} as const;