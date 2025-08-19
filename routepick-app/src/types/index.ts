// API Response Types
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data?: T;
  errorCode?: string;
  timestamp: string;
}

// User Types
export interface User {
  userId: number;
  email: string;
  nickName: string;
  userType: 'REGULAR' | 'GYM_ADMIN' | 'ADMIN';
  createdAt: string;
  updatedAt: string;
}

export interface UserProfile {
  profileId: number;
  userId: number;
  realName?: string;
  nickName: string;
  gender?: 'MALE' | 'FEMALE' | 'OTHER';
  height?: number;
  weight?: number;
  levelId?: number;
  branchId?: number;
  profileImageUrl?: string;
  bio?: string;
  createdAt: string;
  updatedAt: string;
}

// Auth Types
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
  profile: UserProfile;
}

export interface SocialLoginRequest {
  provider: 'GOOGLE' | 'KAKAO' | 'NAVER' | 'FACEBOOK';
  socialId: string;
  accessToken: string;
  email?: string;
  name?: string;
  profileImage?: string;
}

// Gym Types
export interface Gym {
  gymId: number;
  name: string;
  description?: string;
  gymAdminId: number;
  createdAt: string;
  updatedAt: string;
}

export interface GymBranch {
  branchId: number;
  gymId: number;
  branchName: string;
  address: string;
  detailAddress?: string;
  contactPhone?: string;
  latitude: number;
  longitude: number;
  businessHours?: string; // JSON string
  amenities?: string; // JSON string
  createdAt: string;
  updatedAt: string;
}

// Route Types
export interface Route {
  routeId: number;
  branchId: number;
  wallId: number;
  name: string;
  description?: string;
  levelId: number;
  color?: string;
  angle?: number;
  setterId?: number;
  setDate?: string;
  retireDate?: string;
  routeStatus: 'ACTIVE' | 'RETIRED' | 'MAINTENANCE';
  createdAt: string;
  updatedAt: string;
}

export interface RouteImage {
  imageId: number;
  routeId: number;
  imageUrl: string;
  isMain: boolean;
  displayOrder: number;
  createdAt: string;
}

export interface RouteVideo {
  videoId: number;
  routeId: number;
  videoUrl: string;
  thumbnailUrl?: string;
  duration?: number;
  createdAt: string;
}

// Tag Types
export interface Tag {
  tagId: number;
  tagName: string;
  tagType: 'STYLE' | 'FEATURE' | 'TECHNIQUE' | 'DIFFICULTY' | 'MOVEMENT' | 'HOLD_TYPE' | 'WALL_ANGLE' | 'OTHER';
  tagCategory?: string;
  description?: string;
  isUserSelectable: boolean;
  isRouteTaggable: boolean;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface UserPreferredTag {
  userTagId: number;
  userId: number;
  tagId: number;
  preferenceLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  skillLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
  createdAt: string;
  updatedAt: string;
  tag: Tag;
}

export interface RouteTag {
  routeTagId: number;
  routeId: number;
  tagId: number;
  relevanceScore: number; // 0.0 - 1.0
  createdBy?: number;
  createdAt: string;
  tag: Tag;
}

// Recommendation Types
export interface RouteRecommendation {
  recommendationId: number;
  userId: number;
  routeId: number;
  recommendationScore: number;
  tagMatchScore?: number;
  levelMatchScore?: number;
  calculatedAt: string;
  isActive: boolean;
  route: Route;
  routeImages: RouteImage[];
  routeTags: RouteTag[];
}

// Climbing Types
export interface ClimbingLevel {
  levelId: number;
  levelName: string; // V0, V1, 5.10a, etc.
  difficulty: number;
  category: 'BOULDERING' | 'SPORT' | 'TRAD';
  displayOrder: number;
}

export interface UserClimb {
  climbId: number;
  userId: number;
  routeId: number;
  climbDate: string;
  attempts: number;
  isCompleted: boolean;
  rating?: number;
  notes?: string;
  createdAt: string;
  route: Route;
}

// Location Types
export interface Location {
  latitude: number;
  longitude: number;
}

export interface Region extends Location {
  latitudeDelta: number;
  longitudeDelta: number;
}

// Navigation Types
export type RootStackParamList = {
  Auth: undefined;
  Main: undefined;
  GymDetail: { gymId: number; branchId: number };
  RouteDetail: { routeId: number };
  TagSelection: { selectedTags?: number[] };
  ClimbingLog: { routeId?: number };
};

export type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
  ForgotPassword: undefined;
  SocialLogin: { provider: string };
};

export type MainTabParamList = {
  Home: undefined;
  Search: undefined;
  Recommendations: undefined;
  Profile: undefined;
};

// Form Types
export interface RegisterFormData {
  email: string;
  password: string;
  confirmPassword: string;
  nickName: string;
  agreeToTerms: boolean;
  agreeToPrivacy: boolean;
  agreeToMarketing?: boolean;
}

export interface ProfileUpdateFormData {
  nickName: string;
  bio?: string;
  height?: number;
  weight?: number;
  levelId?: number;
}

// Search Types
export interface SearchFilters {
  query?: string;
  branchIds?: number[];
  levelIds?: number[];
  tagIds?: number[];
  difficulty?: {
    min: number;
    max: number;
  };
  location?: {
    center: Location;
    radius: number; // km
  };
}

export interface SearchResult {
  type: 'gym' | 'route';
  item: Gym | Route;
  distance?: number;
  matchScore?: number;
}

// Notification Types
export interface PushNotification {
  notificationId: number;
  userId: number;
  type: string;
  title: string;
  body: string;
  data?: any;
  isRead: boolean;
  createdAt: string;
}

// Error Types
export interface AppError {
  code: string;
  message: string;
  details?: any;
}

// State Types
export interface AppState {
  isLoading: boolean;
  error: AppError | null;
  user: User | null;
  profile: UserProfile | null;
  isAuthenticated: boolean;
}

export interface LocationState {
  currentLocation: Location | null;
  selectedRegion: Region;
  nearbyGyms: GymBranch[];
  isLocationPermissionGranted: boolean;
}

export interface SearchState {
  query: string;
  filters: SearchFilters;
  results: SearchResult[];
  recentSearches: string[];
  isSearching: boolean;
}

// Utility Types
export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

export type PickRequired<T, K extends keyof T> = T & Required<Pick<T, K>>;

export type OmitStrict<T, K extends keyof T> = Pick<T, Exclude<keyof T, K>>;