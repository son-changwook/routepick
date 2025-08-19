// Backend API 응답 타입들과 동일하게 유지
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data?: T;
  errorCode?: string;
  timestamp: string;
}

// 페이지네이션 응답
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// 기본 엔티티 타입
export interface BaseEntity {
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  modifiedBy?: string;
}

// User Types
export interface User extends BaseEntity {
  userId: number;
  email: string;
  nickName: string;
  userType: 'REGULAR' | 'GYM_ADMIN' | 'ADMIN';
  userStatus: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
  lastLoginAt?: string;
}

export interface UserProfile extends BaseEntity {
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
}

// Gym Types
export interface Gym extends BaseEntity {
  gymId: number;
  name: string;
  description?: string;
  gymAdminId: number;
  gymStatus: 'ACTIVE' | 'INACTIVE';
  branches: GymBranch[];
  gymAdmin: User;
}

export interface GymBranch extends BaseEntity {
  branchId: number;
  gymId: number;
  branchName: string;
  address: string;
  detailAddress?: string;
  contactPhone?: string;
  latitude: number;
  longitude: number;
  businessHours?: any; // JSON
  amenities?: any; // JSON
  branchStatus: 'ACTIVE' | 'INACTIVE';
  walls: Wall[];
  gym: Gym;
}

export interface Wall extends BaseEntity {
  wallId: number;
  branchId: number;
  wallName: string;
  setDate?: string;
  wallStatus: 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE';
  routes: Route[];
  branch: GymBranch;
}

// Route Types
export interface Route extends BaseEntity {
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
  wall: Wall;
  level: ClimbingLevel;
  setter?: RouteSetter;
  routeImages: RouteImage[];
  routeVideos: RouteVideo[];
  routeTags: RouteTag[];
}

export interface RouteSetter extends BaseEntity {
  setterId: number;
  name: string;
  setterType: 'INTERNAL' | 'EXTERNAL' | 'GUEST';
  bio?: string;
  profileImageUrl?: string;
}

export interface RouteImage extends BaseEntity {
  imageId: number;
  routeId: number;
  imageUrl: string;
  isMain: boolean;
  displayOrder: number;
}

export interface RouteVideo extends BaseEntity {
  videoId: number;
  routeId: number;
  videoUrl: string;
  thumbnailUrl?: string;
  duration?: number;
}

// Tag Types
export interface Tag extends BaseEntity {
  tagId: number;
  tagName: string;
  tagType: 'STYLE' | 'FEATURE' | 'TECHNIQUE' | 'DIFFICULTY' | 'MOVEMENT' | 'HOLD_TYPE' | 'WALL_ANGLE' | 'OTHER';
  tagCategory?: string;
  description?: string;
  isUserSelectable: boolean;
  isRouteTaggable: boolean;
  displayOrder: number;
  usageCount?: number; // 사용 빈도
}

export interface RouteTag extends BaseEntity {
  routeTagId: number;
  routeId: number;
  tagId: number;
  relevanceScore: number; // 0.0 - 1.0
  createdBy?: number;
  tag: Tag;
}

export interface UserPreferredTag extends BaseEntity {
  userTagId: number;
  userId: number;
  tagId: number;
  preferenceLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  skillLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
  tag: Tag;
}

// Climbing Types
export interface ClimbingLevel {
  levelId: number;
  levelName: string; // V0, V1, 5.10a, etc.
  difficulty: number;
  category: 'BOULDERING' | 'SPORT' | 'TRAD';
  displayOrder: number;
}

export interface UserClimb extends BaseEntity {
  climbId: number;
  userId: number;
  routeId: number;
  climbDate: string;
  attempts: number;
  isCompleted: boolean;
  rating?: number;
  notes?: string;
  user: User;
  route: Route;
}

// Payment Types
export interface PaymentRecord extends BaseEntity {
  paymentId: number;
  userId: number;
  amount: number;
  paymentStatus: 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'REFUNDED';
  paymentMethod: 'CARD' | 'VIRTUAL_ACCOUNT' | 'BANK_TRANSFER';
  transactionId?: string;
  user: User;
  paymentDetails: PaymentDetail[];
  paymentItems: PaymentItem[];
  paymentRefunds: PaymentRefund[];
}

export interface PaymentDetail extends BaseEntity {
  detailId: number;
  paymentId: number;
  cardName?: string;
  cardNumber?: string;
  cardQuota?: number;
  vbankName?: string;
  vbankNumber?: string;
  vbankHolder?: string;
  vbankDate?: string;
}

export interface PaymentItem extends BaseEntity {
  itemId: number;
  paymentId: number;
  itemName: string;
  itemAmount: number;
  quantity: number;
}

export interface PaymentRefund extends BaseEntity {
  refundId: number;
  paymentId: number;
  refundAmount: number;
  refundReason: string;
  refundStatus: 'PENDING' | 'COMPLETED' | 'FAILED';
  refundDate?: string;
}

// Analytics Types
export interface DashboardStats {
  totalUsers: number;
  activeUsers: number;
  totalGyms: number;
  totalRoutes: number;
  totalClimbs: number;
  revenueThisMonth: number;
  userGrowthRate: number;
  routeGrowthRate: number;
}

export interface ChartData {
  labels: string[];
  datasets: {
    label: string;
    data: number[];
    backgroundColor?: string | string[];
    borderColor?: string;
    borderWidth?: number;
  }[];
}

export interface TimeSeriesData {
  date: string;
  value: number;
  category?: string;
}

// Form Types
export interface GymFormData {
  name: string;
  description?: string;
  gymAdminId: number;
}

export interface GymBranchFormData {
  branchName: string;
  address: string;
  detailAddress?: string;
  contactPhone?: string;
  latitude: number;
  longitude: number;
  businessHours?: any;
  amenities?: string[];
}

export interface RouteFormData {
  name: string;
  description?: string;
  levelId: number;
  color?: string;
  angle?: number;
  setterId?: number;
  setDate?: string;
  tagIds: number[];
  images?: File[];
  videos?: File[];
}

export interface TagFormData {
  tagName: string;
  tagType: string;
  tagCategory?: string;
  description?: string;
  isUserSelectable: boolean;
  isRouteTaggable: boolean;
  displayOrder: number;
}

export interface UserFormData {
  email: string;
  nickName: string;
  userType: string;
  password?: string;
}

// Table Column Types
export interface TableColumn<T = any> {
  key: string;
  title: string;
  dataIndex?: keyof T;
  width?: number;
  fixed?: 'left' | 'right';
  sorter?: boolean | ((a: T, b: T) => number);
  filters?: { text: string; value: any }[];
  render?: (value: any, record: T, index: number) => React.ReactNode;
}

// Filter Types
export interface BaseFilter {
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'ASC' | 'DESC';
}

export interface GymFilter extends BaseFilter {
  name?: string;
  gymAdminId?: number;
  status?: string;
}

export interface RouteFilter extends BaseFilter {
  name?: string;
  branchId?: number;
  levelId?: number;
  status?: string;
  tagIds?: number[];
  setterId?: number;
}

export interface UserFilter extends BaseFilter {
  email?: string;
  nickName?: string;
  userType?: string;
  status?: string;
}

export interface PaymentFilter extends BaseFilter {
  userId?: number;
  status?: string;
  paymentMethod?: string;
  startDate?: string;
  endDate?: string;
}

// State Types
export interface AppState {
  user: User | null;
  permissions: string[];
  theme: 'light' | 'dark';
  sidebarCollapsed: boolean;
  loading: boolean;
  error: string | null;
}

export interface UIState {
  sidebarCollapsed: boolean;
  theme: 'light' | 'dark';
  language: 'ko' | 'en';
  notifications: Notification[];
}

export interface Notification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
  timestamp: string;
  read: boolean;
}

// WebSocket Types
export interface WSMessage {
  type: string;
  payload: any;
  timestamp: string;
}

export interface RealtimeUpdate {
  type: 'USER_ACTIVITY' | 'ROUTE_UPDATE' | 'PAYMENT_UPDATE' | 'SYSTEM_ALERT';
  data: any;
}

// Export/Import Types
export interface ExportOptions {
  format: 'csv' | 'xlsx' | 'pdf';
  columns: string[];
  filters?: any;
  includeHeaders: boolean;
}

export interface ImportResult {
  totalRows: number;
  successRows: number;
  errorRows: number;
  errors: Array<{
    row: number;
    column: string;
    message: string;
  }>;
}

// Utility Types
export type KeysOfUnion<T> = T extends T ? keyof T : never;
export type PartialBy<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>;
export type RequiredBy<T, K extends keyof T> = T & Required<Pick<T, K>>;

// Component Props Types
export interface PageHeaderProps {
  title: string;
  subtitle?: string;
  breadcrumb?: Array<{ title: string; path?: string }>;
  extra?: React.ReactNode;
}

export interface TableActionProps<T> {
  record: T;
  onEdit?: (record: T) => void;
  onDelete?: (record: T) => void;
  onView?: (record: T) => void;
  customActions?: Array<{
    label: string;
    icon?: React.ReactNode;
    onClick: (record: T) => void;
    disabled?: boolean;
  }>;
}