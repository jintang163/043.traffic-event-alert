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
  roadRegionPixel?: string;
  ledConfig?: string;
  locationCode?: string;
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
  accidentEvaluationReasons?: string;
  sourceNodeCode?: string;
  constructionPlanId?: number;
  metadata?: Record<string, any>;
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

export interface VideoClip {
  id: number;
  cameraId: number;
  cameraName?: string;
  clipType?: string;
  alertEventId?: number;
  eventNo?: string;
  fileName?: string;
  filePath?: string;
  fileUrl?: string;
  hlsPlaylistPath?: string;
  hlsPlaylistUrl?: string;
  fileSize?: number;
  duration?: number;
  startTime?: string;
  endTime?: string;
  thumbnailUrl?: string;
  recordStatus?: number;
  failReason?: string;
  createTime?: string;
}

export const RECORD_STATUS_LABELS: Record<number, { label: string; color: string }> = {
  [-1]: { label: '失败', color: '#ff4d4f' },
  0: { label: '待录制', color: '#8c8c8c' },
  1: { label: '录制中', color: '#faad14' },
  2: { label: '成功', color: '#52c41a' },
};

export const CLIP_TYPE_OPTIONS = [
  { value: 'ACCIDENT', label: '交通事故', color: '#ff4d4f' },
  { value: 'DEBRIS', label: '抛洒物', color: '#faad14' },
  { value: 'PARKING', label: '违规停车', color: '#722ed1' },
  { value: 'PEDESTRIAN', label: '行人闯入', color: '#1890ff' },
  { value: 'CONGESTION', label: '交通拥堵', color: '#eb2f96' },
  { value: 'REVERSE', label: '逆行', color: '#13c2c2' },
  { value: 'OVERSPEED', label: '超速', color: '#fa8c16' },
  { value: 'OTHER', label: '其他事件', color: '#8c8c8c' },
];

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
  PEDESTRIAN_INTRUSION: '行人闯入',
  CONE_MISSING: '锥桶缺失',
  CONSTRUCTION_SPEEDING: '施工区超速',
  CONSTRUCTION_INTRUSION: '施工区闯入',
  STOPPED_VEHICLE: '车辆停留',
  CONGESTION: '交通拥堵',
  SPEEDING: '超速行驶',
  RED_LIGHT: '闯红灯',
  WRONG_WAY: '逆向行驶',
  ILLEGAL_PARKING: '违章停车',
  HORN: '长时间鸣笛',
  COLLISION_SOUND: '碰撞声',
  SIREN: '警笛声',
  ABNORMAL_NOISE: '异常噪音',
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

export const EVENT_TYPE_COLORS: Record<string, string> = {
  ACCIDENT: 'red',
  REVERSE: 'orange',
  DEBRIS: 'purple',
  INTRUSION: 'blue',
  PEDESTRIAN_INTRUSION: 'geekblue',
  CONE_MISSING: 'gold',
  CONSTRUCTION_SPEEDING: 'volcano',
  CONSTRUCTION_INTRUSION: 'orange',
  STOPPED_VEHICLE: 'magenta',
  CONGESTION: 'geekblue',
  SPEEDING: 'volcano',
  HORN: 'orange',
  COLLISION_SOUND: 'red',
  SIREN: 'blue',
  ABNORMAL_NOISE: 'default',
};

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

export interface NotifyChannel {
  id?: number;
  channelCode: string;
  channelName: string;
  channelType: string;
  enabled: number;
  configJson?: string;
  description?: string;
  sortOrder?: number;
  createTime?: string;
}

export interface NotifyTemplate {
  id?: number;
  templateCode: string;
  templateName: string;
  channelType: string;
  eventType?: string;
  eventLevel?: number;
  titleTemplate?: string;
  contentTemplate: string;
  status: number;
  description?: string;
  createTime?: string;
}

export interface NotifyRule {
  id?: number;
  ruleName: string;
  eventType?: string;
  eventLevel?: number;
  channelId: number;
  templateId?: number;
  recipientType: number;
  recipientIds?: string;
  atAll?: number;
  enabled: number;
  priority?: number;
  sortOrder?: number;
  description?: string;
  createTime?: string;
}

export interface NotifyLog {
  id?: number;
  logNo: string;
  alertEventId?: number;
  eventNo?: string;
  channelId?: number;
  channelType?: string;
  templateId?: number;
  recipientType?: number;
  recipientInfo?: string;
  title?: string;
  content?: string;
  sendStatus: number;
  retryCount?: number;
  maxRetry?: number;
  nextRetryTime?: string;
  responseBody?: string;
  errorMessage?: string;
  sendTime?: string;
  successTime?: string;
  costMs?: number;
  createTime?: string;
}

export interface OnDuty {
  id?: number;
  userId: number;
  userName?: string;
  phone?: string;
  deptId?: number;
  deptName?: string;
  dutyDate: string;
  dutyType: number;
  startTime?: string;
  endTime?: string;
  status: number;
  remark?: string;
  createTime?: string;
}

export const CHANNEL_TYPE_OPTIONS = [
  { value: 'DINGTALK', label: '钉钉机器人', color: '#1890ff' },
  { value: 'SMS', label: '阿里云短信', color: '#52c41a' },
  { value: 'VOICE', label: '语音TTS外呼', color: '#ff4d4f' },
  { value: 'WECHAT', label: '企业微信', color: '#722ed1' },
];

export const CHANNEL_TYPE_LABELS: Record<string, string> = {
  DINGTALK: '钉钉机器人',
  SMS: '阿里云短信',
  VOICE: '语音TTS外呼',
  WECHAT: '企业微信',
};

export const SEND_STATUS_LABELS: Record<number, { label: string; color: string }> = {
  0: { label: '待发送', color: 'default' },
  1: { label: '发送中', color: 'processing' },
  2: { label: '成功', color: 'success' },
  3: { label: '失败', color: 'error' },
};

export const RECIPIENT_TYPE_OPTIONS = [
  { value: 1, label: '值班人员' },
  { value: 2, label: '指定部门' },
  { value: 3, label: '指定用户' },
  { value: 4, label: '全部人员' },
];

export const DUTY_TYPE_OPTIONS = [
  { value: 1, label: '白班' },
  { value: 2, label: '夜班' },
  { value: 3, label: '全天' },
];

export interface PlateRecognition {
  id?: number;
  recognizeNo?: string;
  alertEventId?: number;
  eventNo?: string;
  cameraId?: number;
  cameraName?: string;
  plateNumber?: string;
  plateColor?: string;
  vehicleColor?: string;
  vehicleType?: string;
  confidence?: number;
  sceneType?: string;
  enhanceGain?: number;
  trackId?: number;
  bboxX1?: number;
  bboxY1?: number;
  bboxX2?: number;
  bboxY2?: number;
  plateImageUrl?: string;
  fullImageUrl?: string;
  recognizeTime?: string;
  status?: number;
  remark?: string;
  createTime?: string;
}

export interface PlateRecognitionQuery extends PageQuery {
  plateNumber?: string;
  eventNo?: string;
  alertEventId?: number;
  cameraId?: number;
  vehicleType?: string;
  sceneType?: string;
  startTime?: string;
  endTime?: string;
}

export const SCENE_TYPE_OPTIONS = [
  { value: 'normal', label: '正常' },
  { value: 'night', label: '夜间' },
  { value: 'backlight', label: '逆光' },
  { value: 'night_backlight', label: '夜间逆光' },
];

export const SCENE_TYPE_COLORS: Record<string, string> = {
  normal: 'default',
  night: 'geekblue',
  backlight: 'orange',
  night_backlight: 'magenta',
};

export interface PoliceSystemConfig {
  id?: number;
  systemCode: string;
  systemName: string;
  pushUrl: string;
  authType: 'NONE' | 'TOKEN' | 'BASIC' | string;
  authToken?: string;
  basicUsername?: string;
  basicPassword?: string;
  enabled: number;
  retryMax?: number;
  retryInitialSeconds?: number;
  retryMultiplier?: number;
  retryMaxSeconds?: number;
  timeoutSeconds?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export const AUTH_TYPE_OPTIONS = [
  { value: 'NONE', label: '无认证' },
  { value: 'TOKEN', label: 'Token认证' },
  { value: 'BASIC', label: 'Basic认证' },
];

export interface PolicePush {
  id?: number;
  pushNo?: string;
  alertEventId?: number;
  eventNo?: string;
  plateRecognitionId?: number;
  eventType?: string;
  eventLevel?: number;
  plateNumber?: string;
  location?: string;
  cameraId?: number;
  cameraName?: string;
  longitude?: number;
  latitude?: number;
  eventTime?: string;
  pushTarget?: string;
  pushStatus: number;
  retryCount?: number;
  maxRetry?: number;
  nextRetryTime?: string;
  pushBody?: string;
  responseBody?: string;
  errorMessage?: string;
  costMs?: number;
  pushTime?: string;
  successTime?: string;
  createTime?: string;
}

export interface PolicePushQuery extends PageQuery {
  pushStatus?: number;
  eventNo?: string;
  alertEventId?: number;
  plateNumber?: string;
  pushTarget?: string;
  eventType?: string;
  startTime?: string;
  endTime?: string;
}

export const POLICE_PUSH_STATUS_LABELS: Record<number, { label: string; color: string }> = {
  0: { label: '待推送', color: 'default' },
  1: { label: '推送中', color: 'processing' },
  2: { label: '推送成功', color: 'success' },
  3: { label: '推送失败', color: 'error' },
};

export interface WeatherData {
  id: number;
  recordTime: string;
  locationCode?: string;
  locationName?: string;
  longitude?: number;
  latitude?: number;
  weatherType: 'SUNNY' | 'CLOUDY' | 'RAIN' | 'SNOW' | 'FOG' | 'HAZE' | string;
  temperature?: number;
  humidity?: number;
  windSpeed?: number;
  windDirection?: number;
  visibility?: number;
  precipitation?: number;
  createTime?: string;
}

export interface EventPrediction {
  id: number;
  predictionNo: string;
  predictionTime: string;
  targetStartTime: string;
  targetEndTime: string;
  targetHours: number;
  cameraId?: number;
  cameraName?: string;
  roadName?: string;
  longitude: number;
  latitude: number;
  geomWkt?: string;
  riskScore: number;
  riskLevel: 1 | 2 | 3 | 4;
  riskLevelLabel: string;
  eventType?: 'ACCIDENT' | 'DEBRIS' | 'CONGESTION' | string;
  eventTypeLabel?: string;
  probability?: number;
  historicalEventCount?: number;
  weatherFactor?: number;
  timeFactor?: number;
  holidayFactor?: number;
  featureJson?: string;
  confidence?: number;
  status?: number;
  actualEventCount?: number;
  predictionAccuracy?: number;
  description?: string;
  createTime?: string;
}

export interface PredictionSummary {
  totalPoints: number;
  predictionTime?: string;
  targetStartTime?: string;
  targetEndTime?: string;
  level1Count: number;
  level2Count: number;
  level3Count: number;
  level4Count: number;
  avgScore: number;
  maxScore: number;
  highestRisk?: EventPrediction;
}

export const WEATHER_TYPE_LABELS: Record<string, string> = {
  SUNNY: '晴',
  CLOUDY: '多云',
  RAIN: '雨',
  SNOW: '雪',
  FOG: '雾',
  HAZE: '霾',
};

export const WEATHER_TYPE_COLORS: Record<string, string> = {
  SUNNY: '#fadb14',
  CLOUDY: '#8c8c8c',
  RAIN: '#1890ff',
  SNOW: '#bae7ff',
  FOG: '#d9d9d9',
  HAZE: '#bfbfbf',
};

export const RISK_LEVEL_LABELS: Record<number, string> = {
  1: '低风险',
  2: '中风险',
  3: '高风险',
  4: '极高风险',
};

export const RISK_LEVEL_COLORS: Record<number, string> = {
  1: '#52c41a',
  2: '#faad14',
  3: '#fa8c16',
  4: '#ff4d4f',
};

export const RISK_HEATMAP_COLORS: Record<number, string> = {
  1: 'rgba(82, 196, 26, 0.6)',
  2: 'rgba(250, 173, 20, 0.6)',
  3: 'rgba(250, 140, 22, 0.7)',
  4: 'rgba(255, 77, 79, 0.8)',
};

export const PREDICTION_EVENT_TYPE_LABELS: Record<string, string> = {
  ACCIDENT: '交通事故',
  DEBRIS: '路面抛洒物',
  CONGESTION: '交通拥堵',
};

export interface EdgeNode {
  id: number;
  nodeCode: string;
  nodeName: string;
  hardwareModel?: string;
  gpuInfo?: string;
  cpuCores?: number;
  memoryGB?: number;
  storageGB?: number;
  osInfo?: string;
  ipAddress?: string;
  macAddress?: string;
  longitude?: number;
  latitude?: number;
  location?: string;
  status: number;
  onlineStatus: number;
  lastHeartbeat?: string;
  heartbeatInterval?: number;
  cpuUsage?: number;
  memoryUsage?: number;
  gpuUsage?: number;
  temperature?: number;
  cameraCount?: number;
  eventCountToday?: number;
  description?: string;
  deptId?: number;
  configJson?: string;
  createTime?: string;
}

export interface EdgeNodeQuery extends PageQuery {
  status?: number;
  onlineStatus?: number;
  hardwareModel?: string;
  deptId?: number;
}

export interface EdgeOfflineEvent {
  id: number;
  edgeNodeId?: number;
  nodeCode: string;
  eventUuid: string;
  eventData?: string;
  eventType: string;
  eventTime: string;
  snapshotPath?: string;
  videoPath?: string;
  uploadStatus: number;
  retryCount?: number;
  maxRetry?: number;
  uploadTime?: string;
  errorMessage?: string;
  createTime?: string;
}

export const EDGE_NODE_STATUS_LABELS: Record<number, string> = {
  0: '禁用',
  1: '启用',
};

export const EDGE_ONLINE_STATUS_LABELS: Record<number, string> = {
  0: '离线',
  1: '在线',
};

export const EDGE_UPLOAD_STATUS_LABELS: Record<number, { label: string; color: string }> = {
  0: { label: '待上传', color: 'default' },
  1: { label: '上传中', color: 'processing' },
  2: { label: '成功', color: 'success' },
  3: { label: '失败', color: 'error' },
};

export const HARDWARE_MODEL_OPTIONS = [
  { value: 'NVIDIA Jetson Nano', label: 'NVIDIA Jetson Nano' },
  { value: 'NVIDIA Jetson Xavier NX', label: 'NVIDIA Jetson Xavier NX' },
  { value: 'NVIDIA Jetson AGX Xavier', label: 'NVIDIA Jetson AGX Xavier' },
  { value: 'NVIDIA Jetson AGX Orin', label: 'NVIDIA Jetson AGX Orin' },
  { value: 'NVIDIA Jetson Orin NX', label: 'NVIDIA Jetson Orin NX' },
  { value: 'NVIDIA Jetson Orin Nano', label: 'NVIDIA Jetson Orin Nano' },
  { value: 'Other', label: '其他' },
];

export interface PatrolRoute {
  id: number;
  routeName: string;
  routeCode: string;
  description?: string;
  status: number;
  staySeconds: number;
  loopMode: number;
  createUserId?: number;
  createUserName?: string;
  createTime?: string;
  updateTime?: string;
  points?: PatrolRoutePoint[];
}

export interface PatrolRoutePoint {
  id: number;
  routeId: number;
  cameraId: number;
  cameraName?: string;
  cameraCode?: string;
  sortOrder: number;
  staySeconds: number;
  longitude?: number;
  latitude?: number;
  location?: string;
}

export interface PatrolExecutionLog {
  id: number;
  routeId: number;
  routeName: string;
  startUserId?: number;
  startUserName?: string;
  startTime?: string;
  endTime?: string;
  executionStatus: number;
  totalPoints: number;
  completedPoints: number;
  detectedEvents?: string;
  remark?: string;
  createTime?: string;
}

export const PATROL_STATUS_LABELS: Record<number, string> = {
  0: '停用',
  1: '启用',
};

export const PATROL_EXECUTION_STATUS_LABELS: Record<number, string> = {
  0: '待执行',
  1: '执行中',
  2: '已完成',
  3: '已中断',
};

export const PATROL_EXECUTION_STATUS_COLORS: Record<number, string> = {
  0: 'default',
  1: 'processing',
  2: 'success',
  3: 'error',
};

export interface EnhancementAlgorithm {
  name: string;
  description: string;
  scenarios: string[];
}

export interface SceneType {
  type: string;
  description: string;
}

export interface EnhancementAlgorithmsResponse {
  algorithms: EnhancementAlgorithm[];
  scene_types: SceneType[];
}

export interface EnhanceResponse {
  success: boolean;
  scene_type: string;
  algorithm_used: string;
  needs_enhancement: boolean;
  score_gain: number;
  brightness: number;
  contrast: number;
  processing_time: number;
  enhanced_image_base64?: string;
}

export interface WeatherAnalysisResponse {
  scene_type: string;
  needs_enhancement: boolean;
  recommended_algorithm: string;
  brightness: number;
  contrast: number;
  processing_time: number;
}

export interface StreamEnhancementStatus {
  enabled: boolean;
  active: boolean;
  autoTrigger: boolean;
  algorithm: string;
  brightness: number;
  contrast: number;
  sceneType: string;
}

export interface GlobalEnhancementConfig {
  enabled: boolean;
  autoTrigger: boolean;
  defaultAlgorithm: string;
  defaultBrightness: number;
  defaultContrast: number;
  minBrightness: number;
  weatherDataEnabled: boolean;
}

export const ENHANCEMENT_ALGORITHM_OPTIONS = [
  { value: 'auto', label: '自动选择' },
  { value: 'retinex', label: 'Retinex增强(夜间)' },
  { value: 'defog', label: '去雾增强(雨雾)' },
  { value: 'clahe_gamma', label: 'CLAHE+伽马校正' },
  { value: 'clahe_whitebalance', label: 'CLAHE+白平衡(逆光)' },
];

export const SCENE_TYPE_LABELS: Record<string, string> = {
  normal: '正常光照',
  night: '夜间/低光照',
  backlight: '逆光',
  rain: '雨天',
  fog: '雾天',
  snow: '雪天',
};

export const SCENE_TYPE_COLORS: Record<string, string> = {
  normal: 'default',
  night: 'geekblue',
  backlight: 'orange',
  rain: 'blue',
  fog: 'default',
  snow: 'cyan',
};

export interface CameraWeatherInfo {
  weatherType: string;
  weatherTypeLabel: string;
  temperature?: number;
  humidity?: number;
  visibility: number;
  windSpeed?: number;
  precipitation?: number;
  brightnessFactor: number;
  needsEnhancement: boolean;
  recordTime?: string;
  locationName?: string;
}

export interface AudioEvent {
  id: number;
  eventNo: string;
  cameraId: number;
  cameraName?: string;
  eventType: 'HORN' | 'COLLISION_SOUND' | 'SIREN' | 'ABNORMAL_NOISE' | string;
  confidence: number;
  duration: number;
  peakDb?: number;
  avgDb?: number;
  dominantFreq?: number;
  eventTime: string;
  description?: string;
  longitude?: number;
  latitude?: number;
  location?: string;
  alertStatus: number;
  linkedAlertEventId?: number;
  ambientDb?: number;
  audioClipUrl?: string;
  createTime?: string;
}

export const AUDIO_EVENT_TYPE_LABELS: Record<string, string> = {
  HORN: '长时间鸣笛',
  COLLISION_SOUND: '碰撞声',
  SIREN: '警笛声',
  ABNORMAL_NOISE: '异常噪音',
};

export const AUDIO_EVENT_TYPE_COLORS: Record<string, string> = {
  HORN: '#fa8c16',
  COLLISION_SOUND: '#ff4d4f',
  SIREN: '#1890ff',
  ABNORMAL_NOISE: '#8c8c8c',
};
