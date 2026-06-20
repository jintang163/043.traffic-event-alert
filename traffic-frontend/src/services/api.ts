import axios, { AxiosInstance, AxiosResponse } from 'axios';
import { message } from 'antd';
import type { Result, WeatherData, CameraWeatherInfo } from '@/types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const request: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

request.interceptors.response.use(
  (response: AxiosResponse<Result<any>>) => {
    const { data } = response;
    if (data.code !== 200) {
      message.error(data.message || '请求失败');
      if (data.code === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.href = '/login';
      }
      return Promise.reject(new Error(data.message));
    }
    return data;
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    message.error(error.message || '网络错误');
    return Promise.reject(error);
  }
);

export const authApi = {
  login: (data: { username: string; password: string }) =>
    request.post<any, Result<any>>('/api/auth/login', data),
  getCurrentUser: () =>
    request.get<any, Result<any>>('/api/auth/me'),
};

export const cameraApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/cameras/page', { params }),
  list: () =>
    request.get<any, Result<any>>('/api/cameras/list'),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/cameras/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/cameras', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/cameras/${id}`),
  getStream: (id: number) =>
    request.get<any, Result<any>>(`/api/cameras/${id}/stream`),
  ptzControl: (id: number, data: any) =>
    request.post<any, Result<any>>(`/api/cameras/${id}/ptz`, data),
  statistics: () =>
    request.get<any, Result<any>>('/api/cameras/statistics'),
};

export const alertApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/alerts/page', { params }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/alerts/${id}`),
  handle: (id: number, remark?: string) =>
    request.post<any, Result<any>>(`/api/alerts/${id}/handle`, null, { params: { remark } }),
  markFalsePositive: (id: number, data: { reason: string }) =>
    request.post<any, Result<any>>(`/api/alerts/${id}/false-positive`, data),
  statistics: () =>
    request.get<any, Result<any>>('/api/alerts/statistics'),
  recent: (limit = 10) =>
    request.get<any, Result<any>>('/api/alerts/recent', { params: { limit } }),
  uploadSnapshot: (id: number, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post<any, Result<any>>(`/api/alerts/${id}/snapshot`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  debrisCategories: () =>
    request.get<any, Result<any>>('/api/alerts/debris-categories'),
  classifyDebris: (data: { className?: string; description?: string; [k: string]: any }) =>
    request.post<any, Result<any>>('/api/alerts/debris-classify', data),
};

export const workOrderApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/work-orders/page', { params }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/work-orders/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/work-orders', data),
  handle: (id: number, data: any) =>
    request.post<any, Result<any>>(`/api/work-orders/${id}/handle`, data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/work-orders/${id}`),
  listByAlert: (alertEventId: number) =>
    request.get<any, Result<any>>(`/api/work-orders/alert/${alertEventId}`),
  statistics: () =>
    request.get<any, Result<any>>('/api/work-orders/statistics'),
};

export const departmentApi = {
  list: (deptType?: number) =>
    request.get<any, Result<any>>('/api/departments/list', { params: { deptType } }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/departments/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/departments', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/departments/${id}`),
  findNearest: (longitude: number, latitude: number, deptType?: number) =>
    request.get<any, Result<any>>('/api/departments/nearest', { params: { longitude, latitude, deptType } }),
};

export const statisticsApi = {
  overview: () =>
    request.get<any, Result<any>>('/api/statistics/overview'),
  trafficOverview: () =>
    request.get<any, Result<any>>('/api/statistics/traffic/overview'),
  influxDbStatus: () =>
    request.get<any, Result<any>>('/api/statistics/traffic/influxdb-status'),
  trafficHistory: (params: {
    cameraId?: number;
    laneNo?: number;
    startTime?: string;
    endTime?: string;
    aggregateType?: string;
    dataSource?: string;
  }) =>
    request.get<any, Result<any>>('/api/statistics/traffic/history', { params }),
  trafficRealtime: (cameraId: number) =>
    request.get<any, Result<any>>(`/api/statistics/traffic/realtime/${cameraId}`),
  trafficAggregate: (params: {
    cameraId?: number;
    startTime?: string;
    endTime?: string;
    aggregateType?: string;
  }) =>
    request.post<any, Result<any>>('/api/statistics/traffic/aggregate', null, { params }),
};

export const weatherApi = {
  getLatest: (locationCode?: string) =>
    request.get<Result<WeatherData>>(`/api/weather/latest`, {
      params: locationCode ? { locationCode } : {},
    }),

  getLatestForCamera: (cameraId: number) =>
    request.get<Result<CameraWeatherInfo>>(`/api/weather/camera/${cameraId}/latest`),

  getLatestAll: () =>
    request.get<Result<WeatherData[]>>('/api/weather/latest/all'),

  getLocationCodes: () =>
    request.get<Result<string[]>>('/api/weather/locations'),

  getByTimeRange: (params: {
    locationCode?: string;
    startTime: string;
    endTime: string;
  }) =>
    request.get<Result<WeatherData[]>>('/api/weather/range', { params }),

  saveWeatherData: (data: Partial<WeatherData>) =>
    request.post<Result<WeatherData>>('/api/weather', data),
};

export const aiApi = {
  detectImage: (file: File) => {
    const formData = new FormData();
    formData.append('image', file);
    return request.post<any, Result<any>>('/api/ai/detect/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  detectImageWithEnhancement: (
    file: File,
    params: {
      enableEnhancement?: boolean;
      enhancementAlgorithm?: string;
      brightness?: number;
      contrast?: number;
      externalWeather?: string;
    } = {}
  ) => {
    const formData = new FormData();
    formData.append('file', file);
    const queryParams = new URLSearchParams();
    if (params.enableEnhancement) queryParams.append('enable_enhancement', 'true');
    if (params.enhancementAlgorithm) queryParams.append('enhancement_algorithm', params.enhancementAlgorithm);
    if (params.brightness !== undefined) queryParams.append('brightness', params.brightness.toString());
    if (params.contrast !== undefined) queryParams.append('contrast', params.contrast.toString());
    if (params.externalWeather) queryParams.append('external_weather', params.externalWeather);
    const queryString = queryParams.toString();
    return request.post<any, Result<any>>(
      `/api/ai/detect/image${queryString ? `?${queryString}` : ''}`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
  },
  startStreamDetection: (data: { 
    cameraId: number; 
    streamUrl?: string;
    enableEnhancement?: boolean;
    enhancementAlgorithm?: string;
    brightness?: number;
    contrast?: number;
  }) =>
    request.post<any, Result<any>>('/api/ai/detect/stream/start', data),
  stopStreamDetection: (cameraId: number) =>
    request.post<any, Result<any>>(`/api/ai/detect/stream/${cameraId}/stop`),
  eventCallback: (data: any) =>
    request.post<any, Result<any>>('/api/ai/event/callback', data),
};

export const enhanceApi = {
  enhanceImage: (
    file: File,
    params: {
      algorithm?: string;
      brightness?: number;
      contrast?: number;
      externalWeather?: string;
    } = {}
  ) => {
    const formData = new FormData();
    formData.append('file', file);
    const queryParams = new URLSearchParams();
    if (params.algorithm) queryParams.append('algorithm', params.algorithm);
    if (params.brightness !== undefined) queryParams.append('brightness', params.brightness.toString());
    if (params.contrast !== undefined) queryParams.append('contrast', params.contrast.toString());
    if (params.externalWeather) queryParams.append('external_weather', params.externalWeather);
    const queryString = queryParams.toString();
    return request.post<any, Result<any>>(
      `/api/ai/enhance/image${queryString ? `?${queryString}` : ''}`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
  },
  analyzeWeather: (file: File, externalWeather?: string) => {
    const formData = new FormData();
    formData.append('file', file);
    const queryString = externalWeather ? `?external_weather=${encodeURIComponent(externalWeather)}` : '';
    return request.post<any, Result<any>>(
      `/api/ai/enhance/analyze${queryString}`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
  },
  getAlgorithms: () =>
    request.get<any, Result<any>>('/api/ai/enhance/algorithms'),
  getGlobalConfig: () =>
    request.get<any, Result<any>>('/api/ai/enhance/config'),
  updateStreamConfig: (
    cameraId: number,
    params: {
      enableEnhancement?: boolean;
      autoTrigger?: boolean;
      algorithm?: string;
      brightness?: number;
      contrast?: number;
    }
  ) =>
    request.post<any, Result<any>>(`/api/ai/enhance/stream/${cameraId}/config`, params),
  getStreamStatus: (cameraId: number) =>
    request.get<any, Result<any>>(`/api/ai/enhance/stream/${cameraId}/status`),
};

export const ptzPresetApi = {
  listByCamera: (cameraId: number) =>
    request.get<any, Result<any>>(`/api/ptz/preset/camera/${cameraId}`),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/ptz/preset/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/ptz/preset', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/ptz/preset/${id}`),
  nextIndex: (cameraId: number) =>
    request.get<any, Result<any>>(`/api/ptz/preset/next-index/${cameraId}`),
};

export const ptzCruiseApi = {
  listByCamera: (cameraId: number) =>
    request.get<any, Result<any>>(`/api/ptz/cruise/camera/${cameraId}`),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/ptz/cruise/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/ptz/cruise', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/ptz/cruise/${id}`),
  start: (id: number) =>
    request.post<any, Result<any>>(`/api/ptz/cruise/${id}/start`),
  stop: (id: number) =>
    request.post<any, Result<any>>(`/api/ptz/cruise/${id}/stop`),
  status: (cameraId: number) =>
    request.get<any, Result<any>>(`/api/ptz/cruise/status/${cameraId}`),
};

export const geoFenceApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/geo-fences/page', { params }),
  list: (cameraId?: number) =>
    request.get<any, Result<any>>('/api/geo-fences/enabled', { params: { cameraId } }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/geo-fences/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/geo-fences', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/geo-fences/${id}`),
  toggleAlert: (id: number, enabled: boolean) =>
    request.post<any, Result<any>>(`/api/geo-fences/${id}/alert`, null, { params: { enabled } }),
  toggleStatus: (id: number, status: number) =>
    request.post<any, Result<any>>(`/api/geo-fences/${id}/status`, null, { params: { status } }),
  listByCamera: (cameraId: number) =>
    request.get<any, Result<any>>(`/api/geo-fences/camera/${cameraId}`),
  checkPoint: (id: number, lng: number, lat: number) =>
    request.get<any, Result<any>>(`/api/geo-fences/${id}/check-point`, { params: { lng, lat } }),
  containingPoint: (lng: number, lat: number, cameraId?: number) =>
    request.get<any, Result<any>>('/api/geo-fences/containing-point', { params: { lng, lat, cameraId } }),
  calculateArea: (points: number[][]) =>
    request.post<any, Result<any>>('/api/geo-fences/calculate-area', { points }),
};

export const globalTrackApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/tracks/page', { params }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/tracks/${id}`),
  points: (id: number) =>
    request.get<any, Result<any>>(`/api/tracks/${id}/points`),
  keyPoints: (id: number) =>
    request.get<any, Result<any>>(`/api/tracks/${id}/key-points`),
  timeline: (id: number) =>
    request.get<any, Result<any>>(`/api/tracks/${id}/timeline`),
  active: () =>
    request.get<any, Result<any>>('/api/tracks/active'),
  create: (data: any) =>
    request.post<any, Result<any>>('/api/tracks', data),
  update: (data: any) =>
    request.put<any, Result<any>>('/api/tracks', data),
  addPoint: (data: any) =>
    request.post<any, Result<any>>('/api/tracks/point', data),
  match: (data: any) =>
    request.post<any, Result<any>>('/api/tracks/match', data),
  findMatch: (data: any) =>
    request.post<any, Result<any>>('/api/tracks/find-match', data),
  updateStatus: (id: number, status: number) =>
    request.post<any, Result<any>>(`/api/tracks/${id}/status`, null, { params: { status } }),
  batchAddPoints: (points: any[]) =>
    request.post<any, Result<any>>('/api/tracks/points/batch', points),
  listByEvent: (eventId: number) =>
    request.get<any, Result<any>>(`/api/tracks/by-event/${eventId}`),
  eventLinks: (eventId: number) =>
    request.get<any, Result<any>>(`/api/tracks/event-links/${eventId}`),
  linkEvent: (data: any) =>
    request.post<any, Result<any>>('/api/tracks/link-event', data),
  eventReplay: (eventId: number, beforeMinutes = 5) =>
    request.get<any, Result<any>>(`/api/tracks/event-replay/${eventId}`, { params: { beforeMinutes } }),
};

export const ledSignApi = {
  getStatus: (cameraId: number) =>
    request.get<any, Result<any>>(`/api/led-sign/status/${cameraId}`),
  displayPedestrianWarning: (cameraId: number) =>
    request.post<any, Result<any>>(`/api/led-sign/pedestrian-warning/${cameraId}`),
  displayMessage: (cameraId: number, data: any) =>
    request.post<any, Result<any>>(`/api/led-sign/display/${cameraId}`, data),
  restoreDefault: (cameraId: number) =>
    request.post<any, Result<any>>(`/api/led-sign/restore/${cameraId}`),
  setDefaultMessage: (cameraId: number, message: string) =>
    request.put<any, Result<any>>(`/api/led-sign/default-message/${cameraId}`, { message }),
  setBrightness: (cameraId: number, brightness: number) =>
    request.put<any, Result<any>>(`/api/led-sign/brightness/${cameraId}`, { brightness }),
  refreshStatus: (cameraId: number) =>
    request.post<any, Result<any>>(`/api/led-sign/refresh/${cameraId}`),
  getProtocols: () =>
    request.get<any, Result<any>>('/api/led-sign/protocols'),
};

export const alertDedupApi = {
  getStatus: () =>
    request.get<any, Result<any>>('/api/alert-deduplication/status'),
  releaseSuppression: (cameraId: number) =>
    request.post<any, Result<boolean>>(`/api/alert-deduplication/release/${cameraId}`),
  releaseAllSuppression: () =>
    request.post<any, Result<void>>('/api/alert-deduplication/release-all'),
};

export const ruleApi = {
  fieldDefinitions: () =>
    request.get<any, Result<any>>('/api/rules/field-definitions'),
  sampleContext: () =>
    request.get<any, Result<any>>('/api/rules/sample-context'),
  execute: (data: any) =>
    request.post<any, Result<any>>('/api/rules/execute', data),
  simulate: (data: any) =>
    request.post<any, Result<any>>('/api/rules/simulate', data),
  convertDecisionTable: (data: { tableData: string }, ruleSetId?: number, replaceExisting = true) =>
    request.post<any, Result<any>>('/api/rules/convert/decision-table', data, { params: { ruleSetId, replaceExisting } }),
  listSets: () =>
    request.get<any, Result<any>>('/api/rules/sets'),
  saveSet: (data: any) =>
    request.post<any, Result<any>>('/api/rules/sets', data),
  getSet: (id: number) =>
    request.get<any, Result<any>>(`/api/rules/sets/${id}`),
  getSetByCode: (code: string) =>
    request.get<any, Result<any>>(`/api/rules/sets/code/${code}`),
  deleteSet: (id: number) =>
    request.delete<any, Result<any>>(`/api/rules/sets/${id}`),
  getBranches: (ruleSetId: number) =>
    request.get<any, Result<any>>(`/api/rules/sets/${ruleSetId}/branches`),
  saveBranch: (data: any) =>
    request.post<any, Result<any>>('/api/rules/branches', data),
  deleteBranch: (id: number) =>
    request.delete<any, Result<any>>(`/api/rules/branches/${id}`),
  executionLogs: (params?: any) =>
    request.get<any, Result<any>>('/api/rules/logs', { params }),
  executionLog: (executionId: string) =>
    request.get<any, Result<any>>(`/api/rules/logs/${executionId}`),
};

export const expressionApi = {
  validate: (data: { expression: string }) =>
    request.post<any, Result<any>>('/api/expressions/validate', data),
  execute: (data: { expression: string; context?: Record<string, any> }) =>
    request.post<any, Result<any>>('/api/expressions/execute', data),
  executeBoolean: (data: { expression: string; context?: Record<string, any> }) =>
    request.post<any, Result<any>>('/api/expressions/execute-boolean', data),
};

export const decisionTableApi = {
  list: () =>
    request.get<any, Result<any>>('/api/decision-tables'),
  parse: (data: { tableData: string }) =>
    request.post<any, Result<any>>('/api/decision-tables/parse', data),
  evaluate: (data: any) =>
    request.post<any, Result<any>>('/api/decision-tables/evaluate', data),
  evaluateResult: (data: any) =>
    request.post<any, Result<any>>('/api/decision-tables/evaluate-result', data),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/decision-tables', data),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/decision-tables/${id}`),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/decision-tables/${id}`),
};

export const videoApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/videos/page', { params }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/videos/${id}`),
  listByEvent: (eventId: number) =>
    request.get<any, Result<any>>(`/api/videos/by-event/${eventId}`),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/videos/${id}`),
  ffmpegStatus: () =>
    request.get<any, Result<any>>('/api/videos/ffmpeg-status'),
  stats: () =>
    request.get<any, Result<any>>('/api/videos/stats'),
};

export const notifyChannelApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/notify/channels/page', { params }),
  list: () =>
    request.get<any, Result<any>>('/api/notify/channels/list'),
  enabled: () =>
    request.get<any, Result<any>>('/api/notify/channels/enabled'),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/notify/channels/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/notify/channels', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/notify/channels/${id}`),
};

export const notifyTemplateApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/notify/templates/page', { params }),
  list: () =>
    request.get<any, Result<any>>('/api/notify/templates/list'),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/notify/templates/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/notify/templates', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/notify/templates/${id}`),
};

export const notifyRuleApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/notify/rules/page', { params }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/notify/rules/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/notify/rules', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/notify/rules/${id}`),
};

export const onDutyApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/on-duty/page', { params }),
  current: () =>
    request.get<any, Result<any>>('/api/on-duty/current'),
  getByDate: (date: string) =>
    request.get<any, Result<any>>(`/api/on-duty/date/${date}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/on-duty', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/on-duty/${id}`),
};

export const notifyLogApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/notify/logs/page', { params }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/notify/logs/${id}`),
  retry: (id: number) =>
    request.post<any, Result<any>>(`/api/notify/logs/${id}/retry`),
};

export const plateRecognitionApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/plate-recognitions', { params }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/plate-recognitions/${id}`),
  listByEvent: (alertEventId: number) =>
    request.get<any, Result<any>>(`/api/plate-recognitions/event/${alertEventId}`),
  listByEventNo: (eventNo: string) =>
    request.get<any, Result<any>>(`/api/plate-recognitions/event-no/${eventNo}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/plate-recognitions', data),
  update: (id: number, data: any) =>
    request.put<any, Result<any>>(`/api/plate-recognitions/${id}`, data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/plate-recognitions/${id}`),
};

export const policeSystemConfigApi = {
  list: () =>
    request.get<any, Result<any>>('/api/police-system-configs'),
  enabled: () =>
    request.get<any, Result<any>>('/api/police-system-configs/enabled'),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/police-system-configs/${id}`),
  getByCode: (systemCode: string) =>
    request.get<any, Result<any>>(`/api/police-system-configs/code/${systemCode}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/police-system-configs', data),
  update: (id: number, data: any) =>
    request.put<any, Result<any>>(`/api/police-system-configs/${id}`, data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/police-system-configs/${id}`),
};

export const policePushApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/police-pushes', { params }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/police-pushes/${id}`),
  listByEvent: (alertEventId: number) =>
    request.get<any, Result<any>>(`/api/police-pushes/event/${alertEventId}`),
  retry: (id: number) =>
    request.post<any, Result<any>>(`/api/police-pushes/${id}/retry`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/police-pushes', data),
  update: (id: number, data: any) =>
    request.put<any, Result<any>>(`/api/police-pushes/${id}`, data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/police-pushes/${id}`),
  statistics: () =>
    request.get<any, Result<any>>('/api/police-pushes/statistics/summary'),
};

export const predictionApi = {
  page: (params: {
    current?: number;
    size?: number;
    status?: string;
    riskLevel?: number;
  }) =>
    request.get<any, Result<any>>('/api/predictions/page', { params }),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/predictions/${id}`),
  range: (params: { startTime: string; endTime: string }) =>
    request.get<any, Result<any>>('/api/predictions/range', { params }),
  nextHour: () =>
    request.get<any, Result<any>>('/api/predictions/next-hour'),
  generate: (targetHours = 1) =>
    request.post<any, Result<any>>('/api/predictions/generate', null, { params: { targetHours } }),
  summary: () =>
    request.get<any, Result<any>>('/api/predictions/summary'),
};

export const edgeNodeApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/edge-nodes/page', { params }),
  list: () =>
    request.get<any, Result<any>>('/api/edge-nodes/list'),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/edge-nodes/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/edge-nodes', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/edge-nodes/${id}`),
  statistics: () =>
    request.get<any, Result<any>>('/api/edge-nodes/statistics'),
  getConfig: (id: number) =>
    request.get<any, Result<any>>(`/api/edge-nodes/${id}/config`),
  updateConfig: (id: number, data: any) =>
    request.post<any, Result<any>>(`/api/edge-nodes/${id}/config`, data),
  listOfflineEvents: (id: number, uploadStatus?: number) =>
    request.get<any, Result<any>>(`/api/edge-nodes/${id}/offline-events`, { params: { uploadStatus } }),
};

export const patrolRouteApi = {
  page: (params: any) =>
    request.get<any, Result<any>>('/api/patrol/routes/page', { params }),
  list: () =>
    request.get<any, Result<any>>('/api/patrol/routes/list'),
  get: (id: number) =>
    request.get<any, Result<any>>(`/api/patrol/routes/${id}`),
  save: (data: any) =>
    request.post<any, Result<any>>('/api/patrol/routes', data),
  delete: (id: number) =>
    request.delete<any, Result<any>>(`/api/patrol/routes/${id}`),
  start: (id: number, data?: any) =>
    request.post<any, Result<any>>(`/api/patrol/routes/${id}/start`, data || {}),
  complete: (logId: number, detectedEvents?: string, remark?: string) =>
    request.post<any, Result<any>>(`/api/patrol/routes/execution/${logId}/complete`, null, { params: { detectedEvents, remark } }),
  updateProgress: (logId: number, completedPoints: number, detectedEvents?: string) =>
    request.post<any, Result<any>>(`/api/patrol/routes/execution/${logId}/progress`, null, { params: { completedPoints, detectedEvents } }),
  listExecutionLogs: (params: any) =>
    request.get<any, Result<any>>('/api/patrol/routes/execution/logs', { params }),
};

export const constructionApi = {
  pagePlans: (params: any) =>
    request.get<any, Result<any>>('/api/construction/plans/page', { params }),
  getPlan: (id: number) =>
    request.get<any, Result<any>>(`/api/construction/plans/${id}`),
  listActivePlans: () =>
    request.get<any, Result<any>>('/api/construction/plans/active'),
  listPlansByCamera: (cameraId: number) =>
    request.get<any, Result<any>>(`/api/construction/plans/camera/${cameraId}`),
  savePlan: (data: any) =>
    request.post<any, Result<any>>('/api/construction/plans', data),
  deletePlan: (id: number) =>
    request.delete<any, Result<any>>(`/api/construction/plans/${id}`),
  updatePlanStatus: (id: number, status: number) =>
    request.post<any, Result<any>>(`/api/construction/plans/${id}/status`, null, { params: { status } }),
  togglePlanAlert: (id: number, enabled: boolean) =>
    request.post<any, Result<any>>(`/api/construction/plans/${id}/alert`, null, { params: { enabled } }),
  startConstruction: (id: number) =>
    request.post<any, Result<any>>(`/api/construction/plans/${id}/start`),
  completeConstruction: (id: number) =>
    request.post<any, Result<any>>(`/api/construction/plans/${id}/complete`),
  getPlanSummary: (id: number) =>
    request.get<any, Result<any>>(`/api/construction/plans/${id}/summary`),

  pageConeRecords: (params: any) =>
    request.get<any, Result<any>>('/api/construction/cones/page', { params }),
  getConeRecord: (id: number) =>
    request.get<any, Result<any>>(`/api/construction/cones/${id}`),
  listConeRecordsByPlan: (planId: number) =>
    request.get<any, Result<any>>(`/api/construction/cones/plan/${planId}`),
  getLatestConeRecord: (planId: number) =>
    request.get<any, Result<any>>(`/api/construction/cones/plan/${planId}/latest`),
  saveConeRecord: (data: any) =>
    request.post<any, Result<any>>('/api/construction/cones', data),
  deleteConeRecord: (id: number) =>
    request.delete<any, Result<any>>(`/api/construction/cones/${id}`),
};

export default request;
