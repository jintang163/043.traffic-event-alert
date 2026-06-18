import axios, { AxiosInstance, AxiosResponse } from 'axios';
import { message } from 'antd';
import type { Result } from '@/types';

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
};

export const aiApi = {
  detectImage: (file: File) => {
    const formData = new FormData();
    formData.append('image', file);
    return request.post<any, Result<any>>('/api/ai/detect/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  startStreamDetection: (data: { cameraId: number; streamUrl?: string }) =>
    request.post<any, Result<any>>('/api/ai/detect/stream/start', data),
  stopStreamDetection: (cameraId: number) =>
    request.post<any, Result<any>>(`/api/ai/detect/stream/${cameraId}/stop`),
  eventCallback: (data: any) =>
    request.post<any, Result<any>>('/api/ai/event/callback', data),
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

export default request;
