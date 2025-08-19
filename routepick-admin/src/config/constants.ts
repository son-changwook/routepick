// API 설정
export const API_CONFIG = {
  BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  WS_BASE_URL: import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:8080',
  TIMEOUT: 30000,
  RETRY_COUNT: 3,
} as const;

// 앱 설정
export const APP_CONFIG = {
  NAME: 'RoutePickr Admin',
  VERSION: '1.0.0',
  DESCRIPTION: 'RoutePickr 관리자 웹 애플리케이션',
  COPYRIGHT: '© 2025 RoutePickr. All rights reserved.',
} as const;

// 라우트 경로
export const ROUTES = {
  // Auth
  LOGIN: '/login',
  
  // Dashboard
  DASHBOARD: '/dashboard',
  
  // Gym Management
  GYM_LIST: '/gym',
  GYM_DETAIL: '/gym/:id',
  GYM_CREATE: '/gym/create',
  GYM_EDIT: '/gym/:id/edit',
  
  // Route Management
  ROUTE_LIST: '/route',
  ROUTE_DETAIL: '/route/:id',
  ROUTE_CREATE: '/route/create',
  ROUTE_EDIT: '/route/:id/edit',
  
  // Tag Management
  TAG_LIST: '/tags',
  TAG_CREATE: '/tags/create',
  TAG_EDIT: '/tags/:id/edit',
  
  // User Management
  USER_LIST: '/users',
  USER_DETAIL: '/users/:id',
  
  // Payment Management
  PAYMENT_LIST: '/payments',
  PAYMENT_DETAIL: '/payments/:id',
  
  // Settings
  SETTINGS: '/settings',
  PROFILE: '/profile',
} as const;

// 저장소 키
export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'routepick_admin_access_token',
  REFRESH_TOKEN: 'routepick_admin_refresh_token',
  USER_INFO: 'routepick_admin_user_info',
  THEME: 'routepick_admin_theme',
  SIDEBAR_COLLAPSED: 'routepick_admin_sidebar_collapsed',
  TABLE_SETTINGS: 'routepick_admin_table_settings',
  DASHBOARD_LAYOUT: 'routepick_admin_dashboard_layout',
} as const;

// 페이지네이션 설정
export const PAGINATION_CONFIG = {
  DEFAULT_PAGE_SIZE: 20,
  PAGE_SIZE_OPTIONS: [10, 20, 50, 100],
  SHOW_QUICK_JUMPER: true,
  SHOW_SIZE_CHANGER: true,
  SHOW_TOTAL: (total: number, range: [number, number]) =>
    `${range[0]}-${range[1]} of ${total} items`,
} as const;

// 테이블 설정
export const TABLE_CONFIG = {
  SCROLL_Y: 'calc(100vh - 320px)',
  ROW_SELECTION_TYPE: 'checkbox',
  SIZE: 'middle',
  BORDERED: true,
} as const;

// 차트 색상
export const CHART_COLORS = {
  PRIMARY: '#1890ff',
  SUCCESS: '#52c41a',
  WARNING: '#faad14',
  ERROR: '#ff4d4f',
  INFO: '#13c2c2',
  PURPLE: '#722ed1',
  MAGENTA: '#eb2f96',
  VOLCANO: '#fa541c',
  ORANGE: '#fa8c16',
  GOLD: '#fadb14',
  LIME: '#a0d911',
  GREEN: '#52c41a',
  CYAN: '#13c2c2',
  BLUE: '#1890ff',
  GEEK_BLUE: '#2f54eb',
  PURPLE_2: '#722ed1',
} as const;

// 파일 업로드 설정
export const UPLOAD_CONFIG = {
  MAX_FILE_SIZE: 50 * 1024 * 1024, // 50MB
  ALLOWED_IMAGE_TYPES: ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
  ALLOWED_VIDEO_TYPES: ['video/mp4', 'video/webm', 'video/ogg'],
  ALLOWED_DOCUMENT_TYPES: [
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  ],
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

// 태그 타입 라벨
export const TAG_TYPE_LABELS = {
  STYLE: '스타일',
  FEATURE: '특징',
  TECHNIQUE: '테크닉',
  DIFFICULTY: '난이도',
  MOVEMENT: '무브먼트',
  HOLD_TYPE: '홀드 타입',
  WALL_ANGLE: '벽 각도',
  OTHER: '기타',
} as const;

// 사용자 타입
export const USER_TYPES = {
  REGULAR: 'REGULAR',
  GYM_ADMIN: 'GYM_ADMIN',
  ADMIN: 'ADMIN',
} as const;

// 사용자 타입 라벨
export const USER_TYPE_LABELS = {
  REGULAR: '일반 사용자',
  GYM_ADMIN: '체육관 관리자',
  ADMIN: '시스템 관리자',
} as const;

// 루트 상태
export const ROUTE_STATUS = {
  ACTIVE: 'ACTIVE',
  RETIRED: 'RETIRED',
  MAINTENANCE: 'MAINTENANCE',
} as const;

// 루트 상태 라벨
export const ROUTE_STATUS_LABELS = {
  ACTIVE: '활성',
  RETIRED: '폐기',
  MAINTENANCE: '점검중',
} as const;

// 결제 상태
export const PAYMENT_STATUS = {
  PENDING: 'PENDING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED',
  REFUNDED: 'REFUNDED',
} as const;

// 결제 상태 라벨
export const PAYMENT_STATUS_LABELS = {
  PENDING: '대기중',
  COMPLETED: '완료',
  FAILED: '실패',
  CANCELLED: '취소',
  REFUNDED: '환불',
} as const;

// 알림 타입
export const NOTIFICATION_TYPES = {
  SYSTEM: 'SYSTEM',
  USER_ACTION: 'USER_ACTION',
  PAYMENT: 'PAYMENT',
  ROUTE_UPDATE: 'ROUTE_UPDATE',
  GYM_UPDATE: 'GYM_UPDATE',
} as const;

// 대시보드 위젯 타입
export const WIDGET_TYPES = {
  STAT_CARD: 'STAT_CARD',
  LINE_CHART: 'LINE_CHART',
  BAR_CHART: 'BAR_CHART',
  PIE_CHART: 'PIE_CHART',
  TABLE: 'TABLE',
  MAP: 'MAP',
  ACTIVITY_FEED: 'ACTIVITY_FEED',
} as const;

// 차트 타입
export const CHART_TYPES = {
  LINE: 'line',
  BAR: 'bar',
  PIE: 'pie',
  DOUGHNUT: 'doughnut',
  AREA: 'area',
  SCATTER: 'scatter',
} as const;

// 날짜 형식
export const DATE_FORMATS = {
  DATE: 'YYYY-MM-DD',
  DATETIME: 'YYYY-MM-DD HH:mm:ss',
  TIME: 'HH:mm:ss',
  MONTH: 'YYYY-MM',
  YEAR: 'YYYY',
  DISPLAY_DATE: 'YYYY년 MM월 DD일',
  DISPLAY_DATETIME: 'YYYY년 MM월 DD일 HH:mm',
} as const;

// 에러 코드
export const ERROR_CODES = {
  UNAUTHORIZED: 'UNAUTHORIZED',
  FORBIDDEN: 'FORBIDDEN',
  NOT_FOUND: 'NOT_FOUND',
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  NETWORK_ERROR: 'NETWORK_ERROR',
  SERVER_ERROR: 'SERVER_ERROR',
  FILE_UPLOAD_ERROR: 'FILE_UPLOAD_ERROR',
} as const;

// 권한
export const PERMISSIONS = {
  // Gym Management
  GYM_VIEW: 'gym:view',
  GYM_CREATE: 'gym:create',
  GYM_UPDATE: 'gym:update',
  GYM_DELETE: 'gym:delete',
  
  // Route Management
  ROUTE_VIEW: 'route:view',
  ROUTE_CREATE: 'route:create',
  ROUTE_UPDATE: 'route:update',
  ROUTE_DELETE: 'route:delete',
  
  // Tag Management
  TAG_VIEW: 'tag:view',
  TAG_CREATE: 'tag:create',
  TAG_UPDATE: 'tag:update',
  TAG_DELETE: 'tag:delete',
  
  // User Management
  USER_VIEW: 'user:view',
  USER_CREATE: 'user:create',
  USER_UPDATE: 'user:update',
  USER_DELETE: 'user:delete',
  
  // Payment Management
  PAYMENT_VIEW: 'payment:view',
  PAYMENT_REFUND: 'payment:refund',
  
  // System Management
  SYSTEM_SETTINGS: 'system:settings',
  SYSTEM_LOGS: 'system:logs',
} as const;

// 테마 설정
export const THEME_CONFIG = {
  LIGHT: 'light',
  DARK: 'dark',
  AUTO: 'auto',
} as const;

// 언어 설정
export const LANGUAGE_CONFIG = {
  KO: 'ko',
  EN: 'en',
} as const;