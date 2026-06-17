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

export interface WebSocketMessage {
  type: string;
  data?: any;
  message?: string;
  timestamp?: number;
  onlineCount?: number;
}

type MessageHandler = (message: WebSocketMessage) => void;
type AlertHandler = (alert: WsAlertEvent) => void;

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:8080';

class WebSocketService {
  private ws: WebSocket | null = null;
  private connected = false;
  private userId: number | null = null;
  private messageHandlers: Set<MessageHandler> = new Set();
  private alertHandlers: Set<AlertHandler> = new Set();
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
      if (message.type !== 'PONG' && import.meta.env.DEV) {
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
    } catch (e) {
      console.error('[WS] Parse message error:', e, body);
    }
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

  isConnected() {
    return this.connected;
  }
}

export const wsService = new WebSocketService();
export default wsService;
