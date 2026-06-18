export interface User {
  id: number;
  username: string;
  nickname: string;
  email?: string;
  phone?: string;
  role: number;
  status: number;
  avatar?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  nickname: string;
  role: number;
  avatar?: string;
}

export interface Camera {
  id: number;
  cameraCode: string;
  cameraName: string;
  protocol: string;
  streamUrl: string;
  gbDeviceId?: string;
  manufacturer?: string;
  location?: string;
  longitude?: number;
  latitude?: number;
  roadName?: string;
  direction?: number;
  laneCount?: number;
  status: number;
  onlineStatus: number;
  ptzEnabled: number;
  ptzPresets?: string;
  description?: string;
  createTime?: string;
}

export interface BBox {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

export interface TrackedObject {
  trackId: number;
  classId: number;
  className: string;
  bbox: BBox;
  confidence: number;
  age: number;
  hits: number;
  velocity?: number[];
}

export interface AlertEvent {
  id: number;
  eventNo: string;
  eventType: 'ACCIDENT' | 'REVERSE' | 'DEBRIS' | string;
  debrisCategory?: string;
  eventLevel: number;
  cameraId: number;
  cameraName: string;
  location?: string;
  longitude?: number;
  latitude?: number;
  eventTime: string;
  confidence: number;
  eventSnapshot?: string;
  eventVideo?: string;
  description?: string;
  alertStatus: number;
  isFalsePositive: number;
  falsePositiveReason?: string;
  handleUserId?: number;
  handleTime?: string;
  handleRemark?: string;
  createTime?: string;
  accidentVehicles?: number;
  accidentDeformationLevel?: number;
  accidentRollover?: number;
  accidentFire?: number;
  accidentCasualty?: number;
  accidentImpactSpeed?: number;
  accidentSeverity?: 'SLIGHT' | 'GENERAL' | 'MAJOR' | string;
  accidentSeverityLabel?: string;
  accidentPriority?: number;
}

export const ACCIDENT_SEVERITY_OPTIONS = [
  { value: 'SLIGHT', label: '轻微事故', color: '#52c41a', priority: 1 },
  { value: 'GENERAL', label: '一般事故', color: '#faad14', priority: 2 },
  { value: 'MAJOR', label: '重大事故', color: '#ff4d4f', priority: 3 },
];

export const ACCIDENT_DEFORMATION_LEVELS = [
  { value: 0, label: '无变形' },
  { value: 1, label: '轻微变形' },
  { value: 2, label: '中度变形' },
  { value: 3, label: '重度变形' },
  { value: 4, label: '严重报废' },
];

export interface DebrisCategoryOption {
  code: string;
  label: string;
  defaultLevel: number;
}

export interface WorkOrder {
  id: number;
  orderNo: string;
  alertEventId: number;
  eventType: string;
  orderLevel: number;
  title: string;
  description?: string;
  assignDeptId?: number;
  assignDeptName?: string;
  assignUserId?: number;
  assignUserName?: string;
  orderStatus: number;
  planStartTime?: string;
  planEndTime?: string;
  actualStartTime?: string;
  actualEndTime?: string;
  handleContent?: string;
  handleImages?: string;
  remark?: string;
  createTime?: string;
}

export interface Department {
  id: number;
  deptCode: string;
  deptName: string;
  deptType: number;
  longitude?: number;
  latitude?: number;
  contactPerson?: string;
  contactPhone?: string;
  status: number;
  description?: string;
}

export interface GeoFence {
  id: number;
  fenceCode: string;
  fenceName: string;
  fenceType: number;
  cameraId?: number;
  cameraName?: string;
  polygonPoints?: string;
  polygonPointsPixel: string;
  centerLongitude?: number;
  centerLatitude?: number;
  area?: number;
  alertEnabled: number;
  alertLevel: number;
  detectTargetTypes?: string;
  staySeconds: number;
  cooldownSeconds: number;
  notifyEnabled: number;
  notifyDeptIds?: string;
  linkWorkOrder: number;
  color: string;
  description?: string;
  sortOrder: number;
  status: number;
  createTime?: string;
}

export interface GeoFenceQuery extends PageQuery {
  keyword?: string;
  fenceType?: number;
  cameraId?: number;
  alertEnabled?: number;
  status?: number;
}

export interface GlobalTrack {
  id: number;
  trackNo: string;
  targetClass?: string;
  licensePlate?: string;
  plateConfidence?: number;
  color?: string;
  vehicleType?: string;
  reidFeature?: string;
  firstCameraId?: number;
  firstCameraName?: string;
  lastCameraId?: number;
  lastCameraName?: string;
  firstLongitude?: number;
  firstLatitude?: number;
  lastLongitude?: number;
  lastLatitude?: number;
  firstSeenTime?: string;
  lastSeenTime?: string;
  cameraCount?: number;
  pointCount?: number;
  totalDistance?: number;
  avgSpeed?: number;
  trackStatus?: number;
  isEventTarget?: number;
  linkedEventCount?: number;
  snapshotUrl?: string;
  description?: string;
  createTime?: string;
}

export interface TrackPoint {
  id: number;
  trackId: number;
  cameraId: number;
  cameraName?: string;
  frameNo?: number;
  frameTime: string;
  bboxX1?: number;
  bboxY1?: number;
  bboxX2?: number;
  bboxY2?: number;
  bboxConfidence?: number;
  longitude?: number;
  latitude?: number;
  pixelX?: number;
  pixelY?: number;
  velocityX?: number;
  velocityY?: number;
  speed?: number;
  direction?: number;
  reidFeature?: string;
  snapshotUrl?: string;
  isKeyPoint?: number;
  keyPointType?: number;
  createTime?: string;
}

export interface GlobalTrackQuery extends PageQuery {
  keyword?: string;
  trackNo?: string;
  targetClass?: string;
  licensePlate?: string;
  cameraId?: number;
  trackStatus?: number;
  isEventTarget?: number;
  startTime?: string;
  endTime?: string;
}

export const TRACK_STATUS_LABELS: Record<number, string> = {
  1: '跟踪中',
  2: '已丢失',
  3: '已完成',
};

export const TRACK_STATUS_COLORS: Record<number, string> = {
  1: 'processing',
  2: 'warning',
  3: 'default',
};

export const TARGET_CLASS_OPTIONS = [
  { value: 'person', label: '行人' },
  { value: 'car', label: '轿车' },
  { value: 'truck', label: '卡车' },
  { value: 'bus', label: '公交车' },
  { value: 'motorcycle', label: '摩托车' },
  { value: 'bicycle', label: '自行车' },
];

export interface Result<T> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

export interface PageResult<T> {
  total: number;
  records: T[];
  current: number;
  size: number;
}

export interface PageQuery {
  current?: number;
  size?: number;
  keyword?: string;
}

export interface CameraQuery extends PageQuery {
  protocol?: string;
  manufacturer?: string;
  roadName?: string;
  status?: number;
  onlineStatus?: number;
}

export interface AlertEventQuery extends PageQuery {
  eventType?: string;
  eventLevel?: number;
  alertStatus?: number;
  cameraId?: number;
  startTime?: string;
  endTime?: string;
  isFalsePositive?: number;
}

export interface WorkOrderQuery extends PageQuery {
  eventType?: string;
  orderLevel?: number;
  orderStatus?: number;
  assignDeptId?: number;
  assignUserId?: number;
}

export interface WebSocketMessage {
  type: string;
  data?: any;
  message?: string;
  timestamp?: number;
  onlineCount?: number;
}

export interface StatisticsOverview {
  camera: CameraStats;
  alert: AlertStats;
  workOrder: WorkOrderStats;
}

export interface CameraStats {
  total: number;
  online: number;
  offline: number;
  ptzEnabled: number;
}

export interface AlertStats {
  todayCount: number;
  weekCount: number;
  totalCount: number;
  accidentCount: number;
  reverseCount: number;
  debrisCount: number;
  pendingCount: number;
  falsePositiveCount: number;
}

export interface WorkOrderStats {
  total: number;
  pending: number;
  processing: number;
  completed: number;
  todayCreated: number;
  todayCompleted: number;
}

export const EVENT_TYPE_LABELS: Record<string, string> = {
  ACCIDENT: '交通事故',
  REVERSE: '车辆逆行',
  DEBRIS: '路面抛洒物',
  INTRUSION: '区域入侵',
};

export const FENCE_TYPE_LABELS: Record<number, string> = {
  1: '施工区',
  2: '应急车道',
  3: '禁入区',
  4: '自定义',
};

export const FENCE_TYPE_COLORS: Record<number, string> = {
  1: '#faad14',
  2: '#ff4d4f',
  3: '#52c41a',
  4: '#1890ff',
};

export const DETECT_TARGET_OPTIONS = [
  { value: 'person', label: '行人' },
  { value: 'car', label: '轿车' },
  { value: 'truck', label: '卡车' },
  { value: 'bus', label: '公交车' },
  { value: 'motorcycle', label: '摩托车' },
  { value: 'bicycle', label: '自行车' },
];

export const EVENT_LEVEL_LABELS: Record<number, string> = {
  1: '一般',
  2: '严重',
  3: '紧急',
  4: '特急',
};

export const EVENT_LEVEL_COLORS: Record<number, string> = {
  1: 'default',
  2: 'warning',
  3: 'error',
  4: 'magenta',
};

export const ALERT_STATUS_LABELS: Record<number, string> = {
  0: '待处理',
  1: '已处理',
  2: '误报',
};

export const DEBRIS_CATEGORY_LABELS: Record<string, string> = {
  TIRE: '轮胎掉落',
  CARGO: '货物掉落',
  CARDBOARD: '纸箱',
  ANIMAL: '动物闯入',
  DEBRIS_BAG: '杂物袋/包裹',
  CONSTRUCTION: '建筑材料',
  METAL: '金属部件',
  PLASTIC: '塑料杂物',
  PAPER: '纸张/纸片',
  GLASS: '玻璃碎片',
  OTHER: '其他杂物',
};

export const DEBRIS_CATEGORY_COLORS: Record<string, string> = {
  TIRE: 'volcano',
  CARGO: 'red',
  CARDBOARD: 'orange',
  ANIMAL: 'magenta',
  DEBRIS_BAG: 'geekblue',
  CONSTRUCTION: 'gold',
  METAL: 'volcano',
  PLASTIC: 'cyan',
  PAPER: 'default',
  GLASS: 'purple',
  OTHER: 'default',
};

export const ORDER_STATUS_LABELS: Record<number, string> = {
  0: '待派发',
  1: '处理中',
  2: '已完成',
  3: '已取消',
};

export const CAMERA_STATUS_LABELS: Record<number, string> = {
  0: '禁用',
  1: '启用',
};

export const ONLINE_STATUS_LABELS: Record<number, string> = {
  0: '离线',
  1: '在线',
};

export interface RuleSet {
  id?: number;
  ruleCode: string;
  ruleName: string;
  gatewayType: number;
  description?: string;
  status: number;
  defaultBranch?: string;
  createTime?: string;
  updateTime?: string;
}

export interface RuleBranch {
  id?: number;
  ruleSetId: number;
  branchCode: string;
  branchName: string;
  expression: string;
  actionType?: string;
  actionTarget?: string;
  actionParams?: string;
  priority?: number;
  sortOrder?: number;
  createTime?: string;
}

export interface RuleExecuteRequest {
  ruleCode?: string;
  ruleSetId?: number;
  formData?: Record<string, any>;
  systemVariables?: Record<string, any>;
  context?: Record<string, any>;
}

export interface RuleExecuteResult {
  executionId: string;
  ruleSetId?: number;
  ruleCode?: string;
  ruleName?: string;
  gatewayType?: number;
  gatewayTypeName?: string;
  matchedBranches?: RuleBranch[];
  allBranches?: RuleBranch[];
  inputContext?: Record<string, any>;
  executionTime?: number;
  success: boolean;
  errorMessage?: string;
}

export interface RuleExecutionLog {
  id: number;
  executionId: string;
  ruleSetId?: number;
  ruleCode?: string;
  ruleName?: string;
  gatewayType?: number;
  matchedBranches?: string;
  inputContext?: string;
  executionResult?: string;
  executionTime?: number;
  errorMessage?: string;
  success: number;
  createTime: string;
}

export interface FieldDefinition {
  category: string;
  field: string;
  label: string;
  type: string;
  description?: string;
  options?: Array<{ value: any; label: string }>;
  sampleValue?: any;
}

export interface DecisionTable {
  id?: number;
  tableCode: string;
  tableName: string;
  tableData: string;
  hitPolicy: string;
  description?: string;
  status: number;
  createTime?: string;
}

export interface DecisionRule {
  ruleIndex: number;
  description: string;
  conditionExpression: string;
  actions: Record<string, any>;
  matched?: boolean;
}

export const GATEWAY_TYPE_OPTIONS = [
  { value: 1, label: '排他网关', color: 'blue' },
  { value: 2, label: '并行网关', color: 'purple' },
  { value: 3, label: '包容网关', color: 'green' },
];

export const GATEWAY_TYPE_LABELS: Record<number, string> = {
  1: '排他网关',
  2: '并行网关',
  3: '包容网关',
};

export const ACTION_TYPE_OPTIONS = [
  { value: 'APPROVAL', label: '审批' },
  { value: 'ASSIGN', label: '分派' },
  { value: 'NOTIFY', label: '通知' },
  { value: 'CUSTOM', label: '自定义' },
];

export const HIT_POLICY_OPTIONS = [
  { value: 'FIRST', label: '命中首条(FIRST)' },
  { value: 'RULE_ORDER', label: '命中全部(RULE_ORDER)' },
];

export interface TrafficStatisticsVO {
  id?: number;
  cameraId: number;
  cameraName?: string;
  roadName?: string;
  laneNo?: number;
  laneName?: string;
  targetClass?: string;
  targetClassName?: string;
  statTime: string;
  startTime?: string;
  endTime?: string;
  flowVolume?: number;
  avgSpeed?: number;
  minSpeed?: number;
  maxSpeed?: number;
  speedStandardDeviation?: number;
  occupancy?: number;
  density?: number;
  avgHeadway?: number;
  vehicleCount?: number;
  aggregateType?: string;
  createTime?: string;
}

export interface TrafficRealtimeVO {
  cameraId: number;
  cameraName?: string;
  roadName?: string;
  laneNo?: number;
  laneName?: string;
  timestamp: string;
  flowVolume?: number;
  avgSpeed?: number;
  occupancy?: number;
  density?: number;
  level?: string;
  levelName?: string;
}

export interface TrafficOverview {
  totalFlow: number;
  avgSpeed: number;
  congestedLanes: number;
  slowLanes: number;
  smoothLanes: number;
  totalLanes: number;
  activeCameras: number;
  realtimeList: TrafficRealtimeVO[];
}

export const TRAFFIC_LEVEL_LABELS: Record<string, string> = {
  SMOOTH: '畅通',
  SLOW: '缓行',
  CONGESTED: '拥堵',
  NO_DATA: '无数据',
};

export const TRAFFIC_LEVEL_COLORS: Record<string, string> = {
  SMOOTH: '#52c41a',
  SLOW: '#faad14',
  CONGESTED: '#ff4d4f',
  NO_DATA: '#d9d9d9',
};

export const AGGREGATE_TYPE_OPTIONS = [
  { value: 'minute', label: '分钟' },
  { value: 'hour', label: '小时' },
  { value: 'day', label: '日' },
];
