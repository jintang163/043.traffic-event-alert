export interface WsAlertEvent {
  id: number;
  eventNo: string;
  eventType: string;
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
}

export interface DetectionBBox {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

export interface DetectionItem {
  trackId: number;
  classId: number;
  className: string;
  confidence: number;
  bbox: DetectionBBox;
  velocity?: number[];
}

export interface DetectionMessage {
  cameraId: number;
  timestamp: string;
  frameWidth: number;
  frameHeight: number;
  detections: DetectionItem[];
}

export interface WebSocketMessage {
  type: string;
  data?: any;
  message?: string;
  timestamp?: number;
  onlineCount?: number;
  cameraId?: number;
}

type MessageHandler = (message: WebSocketMessage) => void;
type AlertHandler = (alert: WsAlertEvent) => void;
type DetectionHandler = (cameraId: number, data: DetectionMessage) => void;
type LedStatusHandler = (data: any) => void;
type TrackUpdateHandler = (data: any) => void;

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:8080';

class WebSocketService {
  private ws: WebSocket | null = null;
  private connected = false;
  private userId: number | null = null;
  private messageHandlers: Set<MessageHandler> = new Set();
  private alertHandlers: Set<AlertHandler> = new Set();
  private majorAlertHandlers: Set<AlertHandler> = new Set();
  private detectionHandlers: Map<number, Set<DetectionHandler>> = new Map();
  private ledStatusHandlers: Map<number, Set<LedStatusHandler>> = new Map();
  private trackUpdateHandlers: Set<TrackUpdateHandler> = new Set();
  private subscribedCameras: Set<number> = new Set();
  private reconnectTimer: number | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private heartbeatTimer: number | null = null;

  connect(userId: number) {
    if (this.connected && this.userId === userId) {
      return;
    }

    this.userId = userId;
    this.disconnect();

    const wsUrl = `${WS_BASE_URL.replace('http://', 'ws://').replace('https://', 'wss://')}/ws/alert/${userId}`;

    try {
      this.ws = new WebSocket(wsUrl);
      this.setupEventHandlers();
    } catch (e) {
      console.error('[WS] Failed to create WebSocket:', e);
      this.scheduleReconnect();
    }
  }

  private setupEventHandlers() {
    if (!this.ws) return;

    this.ws.onopen = () => {
      console.log('[WS] Connected');
      this.connected = true;
      this.reconnectAttempts = 0;
      this.startHeartbeat();
      if (this.subscribedCameras.size > 0) {
        this.sendSubscribeMessage();
      }
    };

    this.ws.onmessage = (event) => {
      this.handleMessage(event.data);
    };

    this.ws.onclose = (event) => {
      console.log('[WS] Disconnected:', event.code, event.reason);
      this.connected = false;
      this.stopHeartbeat();
      if (event.code !== 1000) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error('[WS] Error:', error);
    };
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = window.setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'PING' }));
      }
    }, 30000);
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private scheduleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('[WS] Max reconnect attempts reached');
      return;
    }

    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    this.reconnectAttempts++;

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }

    this.reconnectTimer = window.setTimeout(() => {
      console.log(`[WS] Reconnecting... attempt ${this.reconnectAttempts}`);
      if (this.userId) {
        this.connect(this.userId);
      }
    }, delay);
  }

  private handleMessage(body: string) {
    try {
      const message: WebSocketMessage = JSON.parse(body);
      if (message.type !== 'PONG' && message.type !== 'DETECTION' && import.meta.env.DEV) {
        console.log('[WS] Received message:', message.type);
      }

      this.messageHandlers.forEach((handler) => {
        try {
          handler(message);
        } catch (e) {
          console.error('[WS] Handler error:', e);
        }
      });

      if (message.type === 'ALERT' && message.data) {
        this.alertHandlers.forEach((handler) => {
          try {
            handler(message.data as WsAlertEvent);
          } catch (e) {
            console.error('[WS] Alert handler error:', e);
          }
        });
      }

      if (message.type === 'MAJOR_ALERT' && message.data) {
        this.alertHandlers.forEach((handler) => {
          try {
            handler(message.data as WsAlertEvent);
          } catch (e) {
            console.error('[WS] Alert handler error:', e);
          }
        });
        this.majorAlertHandlers.forEach((handler) => {
          try {
            handler(message.data as WsAlertEvent);
          } catch (e) {
            console.error('[WS] Major alert handler error:', e);
          }
        });
      }

      if (message.type === 'DETECTION' && message.cameraId !== undefined && message.data) {
        const camId = message.cameraId;
        const handlers = this.detectionHandlers.get(camId);
        if (handlers) {
          handlers.forEach((handler) => {
            try {
              handler(camId, message.data as DetectionMessage);
            } catch (e) {
              console.error('[WS] Detection handler error:', e);
            }
          });
        }
      }

      if (message.type === 'LED_STATUS_UPDATE' && message.cameraId !== undefined && message.data) {
        const camId = Number(message.cameraId);
        const handlers = this.ledStatusHandlers.get(camId);
        if (handlers) {
          handlers.forEach((handler) => {
            try {
              handler(message);
            } catch (e) {
              console.error('[WS] LED status handler error:', e);
            }
          });
        }
      }

      if (message.type === 'TRACK_UPDATE' && message.data) {
        this.trackUpdateHandlers.forEach((handler) => {
          try {
            handler(message.data);
          } catch (e) {
            console.error('[WS] Track update handler error:', e);
          }
        });
      }
    } catch (e) {
      console.error('[WS] Parse message error:', e, body);
    }
  }

  private sendSubscribeMessage() {
    if (this.subscribedCameras.size === 0) {
      this.send({ type: 'UNSUBSCRIBE_CAMERAS' });
      return;
    }
    this.send({
      type: 'SUBSCRIBE_CAMERAS',
      cameraIds: Array.from(this.subscribedCameras),
    });
  }

  subscribeCamera(cameraId: number) {
    if (!this.subscribedCameras.has(cameraId)) {
      this.subscribedCameras.add(cameraId);
      if (this.connected) {
        this.sendSubscribeMessage();
      }
    }
  }

  unsubscribeCamera(cameraId: number) {
    if (this.subscribedCameras.has(cameraId)) {
      this.subscribedCameras.delete(cameraId);
      if (this.connected) {
        this.sendSubscribeMessage();
      }
    }
  }

  onCameraDetection(cameraId: number, handler: DetectionHandler) {
    if (!this.detectionHandlers.has(cameraId)) {
      this.detectionHandlers.set(cameraId, new Set());
    }
    this.detectionHandlers.get(cameraId)!.add(handler);
    this.subscribeCamera(cameraId);
    return () => {
      const handlers = this.detectionHandlers.get(cameraId);
      if (handlers) {
        handlers.delete(handler);
        if (handlers.size === 0) {
          this.detectionHandlers.delete(cameraId);
          this.unsubscribeCamera(cameraId);
        }
      }
    };
  }

  send(message: any) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
      return true;
    }
    return false;
  }

  disconnect() {
    this.stopHeartbeat();
    if (this.ws) {
      try {
        this.ws.close(1000, 'Client disconnect');
      } catch (e) {
        console.error('[WS] Disconnect error:', e);
      }
      this.ws = null;
    }
    this.connected = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  onMessage(handler: MessageHandler) {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  onAlert(handler: AlertHandler) {
    this.alertHandlers.add(handler);
    return () => this.alertHandlers.delete(handler);
  }

  onMajorAlert(handler: AlertHandler) {
    this.majorAlertHandlers.add(handler);
    return () => this.majorAlertHandlers.delete(handler);
  }

  onLedStatusUpdate(cameraId: number, handler: LedStatusHandler) {
    if (!this.ledStatusHandlers.has(cameraId)) {
      this.ledStatusHandlers.set(cameraId, new Set());
    }
    this.ledStatusHandlers.get(cameraId)!.add(handler);
    return () => {
      const handlers = this.ledStatusHandlers.get(cameraId);
      if (handlers) {
        handlers.delete(handler);
        if (handlers.size === 0) {
          this.ledStatusHandlers.delete(cameraId);
        }
      }
    };
  }

  onTrackUpdate(handler: TrackUpdateHandler) {
    this.trackUpdateHandlers.add(handler);
    return () => this.trackUpdateHandlers.delete(handler);
  }

  isConnected() {
    return this.connected;
  }
}

export const wsService = new WebSocketService();
export default wsService;
