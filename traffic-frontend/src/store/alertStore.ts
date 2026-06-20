import { create } from 'zustand';
import type { AlertEvent } from '@/types';

export interface StormSuppressedCamera {
  cameraId: number;
  cameraName: string;
  suppressedAt: string;
  suppressedUntil: string;
  triggerAlertCount: number;
  suppressedCount: number;
  remainingSeconds: number;
}

interface AlertState {
  alerts: AlertEvent[];
  unreadCount: number;
  stormSuppressedCameras: Map<number, StormSuppressedCamera>;
  addAlert: (alert: AlertEvent) => void;
  setAlerts: (alerts: AlertEvent[]) => void;
  markAllRead: () => void;
  clearAlerts: () => void;
  updateAlert: (id: number, updates: Partial<AlertEvent>) => void;
  addStormSuppressed: (data: any) => void;
  removeStormSuppressed: (cameraId: number) => void;
  setStormSuppressedList: (list: StormSuppressedCamera[]) => void;
}

export const useAlertStore = create<AlertState>((set, get) => ({
  alerts: [],
  unreadCount: 0,
  stormSuppressedCameras: new Map(),
  addAlert: (alert) => {
    const { alerts } = get();
    const exists = alerts.some((a) => a.id === alert.id || a.eventNo === alert.eventNo);
    if (!exists) {
      set({
        alerts: [alert, ...alerts].slice(0, 100),
        unreadCount: get().unreadCount + 1,
      });
    }
  },
  setAlerts: (alerts) => set({ alerts }),
  markAllRead: () => set({ unreadCount: 0 }),
  clearAlerts: () => set({ alerts: [], unreadCount: 0 }),
  updateAlert: (id, updates) => {
    const { alerts } = get();
    set({
      alerts: alerts.map((a) => (a.id === id ? { ...a, ...updates } : a)),
    });
  },
  addStormSuppressed: (data) => {
    const cameraId = Number(data.cameraId);
    const current = get().stormSuppressedCameras;
    const next = new Map(current);
    next.set(cameraId, {
      cameraId,
      cameraName: data.cameraName || `摄像头${cameraId}`,
      suppressedAt: data.suppressedAt || new Date().toISOString(),
      suppressedUntil: data.suppressedUntil,
      triggerAlertCount: data.threshold || data.triggerAlertCount || 10,
      suppressedCount: data.suppressedCount || 0,
      remainingSeconds: data.suppressionMinutes ? data.suppressionMinutes * 60 : 300,
    });
    set({ stormSuppressedCameras: next });
  },
  removeStormSuppressed: (cameraId) => {
    const current = get().stormSuppressedCameras;
    if (current.has(cameraId)) {
      const next = new Map(current);
      next.delete(cameraId);
      set({ stormSuppressedCameras: next });
    }
  },
  setStormSuppressedList: (list) => {
    const next = new Map<number, StormSuppressedCamera>();
    list.forEach((item) => {
      next.set(item.cameraId, item);
    });
    set({ stormSuppressedCameras: next });
  },
}));
