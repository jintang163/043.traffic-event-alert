import { create } from 'zustand';
import type { AlertEvent } from '@/types';

interface AlertState {
  alerts: AlertEvent[];
  unreadCount: number;
  addAlert: (alert: AlertEvent) => void;
  setAlerts: (alerts: AlertEvent[]) => void;
  markAllRead: () => void;
  clearAlerts: () => void;
  updateAlert: (id: number, updates: Partial<AlertEvent>) => void;
}

export const useAlertStore = create<AlertState>((set, get) => ({
  alerts: [],
  unreadCount: 0,
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
}));
