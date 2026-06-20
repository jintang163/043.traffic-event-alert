import React, { useEffect, useState, useCallback } from 'react';
import { Card, Tag, Button, Space, InputNumber, Switch, Input, Select, message } from 'antd';
import { BulbOutlined, WarningOutlined, CheckCircleOutlined, DisconnectOutlined, ReloadOutlined } from '@ant-design/icons';
import { wsService } from '@/services/websocket';
import { ledSignApi } from '@/services/api';

interface LedSignDisplayProps {
  cameraId?: number;
  message?: string;
  isAlert?: boolean;
  color?: string;
  brightness?: number;
  autoRefresh?: boolean;
  refreshInterval?: number;
  autoSyncWebSocket?: boolean;
  showControls?: boolean;
  onStatusChange?: (status: LedSignStatus) => void;
}

export interface LedSignStatus {
  cameraId: number;
  currentMessage: string;
  defaultMessage: string;
  lastUpdateTime: string;
  isAlertMode: boolean;
  brightness: number;
  color: string;
  connected: boolean;
  protocol: string;
  errorMsg?: string;
}

const LedSignDisplay: React.FC<LedSignDisplayProps> = ({
  cameraId,
  message,
  isAlert = false,
  color = 'GREEN',
  brightness = 100,
  autoRefresh = false,
  refreshInterval = 5000,
  autoSyncWebSocket = true,
  showControls = false,
  onStatusChange,
}) => {
  const [displayMessage, setDisplayMessage] = useState(message || '注意交通安全');
  const [alertMode, setAlertMode] = useState(isAlert);
  const [displayColor, setDisplayColor] = useState(color);
  const [brightnessLevel, setBrightnessLevel] = useState(brightness);
  const [blinkPhase, setBlinkPhase] = useState(0);
  const [connected, setConnected] = useState(true);
  const [protocol, setProtocol] = useState('MOCK');
  const [errorMsg, setErrorMsg] = useState<string | undefined>();
  const [refreshing, setRefreshing] = useState(false);
  const [customMessage, setCustomMessage] = useState('');

  useEffect(() => {
    if (message !== undefined) setDisplayMessage(message);
  }, [message]);

  useEffect(() => {
    setAlertMode(isAlert);
  }, [isAlert]);

  useEffect(() => {
    setDisplayColor(color);
  }, [color]);

  useEffect(() => {
    setBrightnessLevel(brightness);
  }, [brightness]);

  useEffect(() => {
    if (!alertMode) return;
    const interval = setInterval(() => {
      setBlinkPhase((p) => (p + 1) % 2);
    }, 500);
    return () => clearInterval(interval);
  }, [alertMode]);

  const handleLedStatusUpdate = useCallback((data: any) => {
    if (!cameraId || !data || Number(data.cameraId) !== cameraId) return;
    const status = data.data || data;
    if (status.currentMessage !== undefined) setDisplayMessage(status.currentMessage);
    if (status.alertMode !== undefined) setAlertMode(status.alertMode);
    if (status.color !== undefined) setDisplayColor(status.color);
    if (status.brightness !== undefined) setBrightnessLevel(status.brightness);
    if (status.connected !== undefined) setConnected(status.connected);
    if (status.protocol !== undefined) setProtocol(status.protocol);
    if (status.errorMsg !== undefined) setErrorMsg(status.errorMsg);

    if (onStatusChange) {
      onStatusChange({
        cameraId: cameraId,
        currentMessage: status.currentMessage,
        defaultMessage: status.defaultMessage,
        lastUpdateTime: status.lastUpdateTime,
        isAlertMode: status.alertMode,
        brightness: status.brightness,
        color: status.color,
        connected: status.connected,
        protocol: status.protocol,
        errorMsg: status.errorMsg,
      });
    }
  }, [cameraId, onStatusChange]);

  useEffect(() => {
    if (!autoSyncWebSocket || !cameraId) return;

    const unsub = wsService.onLedStatusUpdate(cameraId, (data) => {
      handleLedStatusUpdate(data);
    });

    loadCurrentStatus();

    return () => { unsub(); };
  }, [autoSyncWebSocket, cameraId, handleLedStatusUpdate]);

  useEffect(() => {
    if (!autoRefresh || !cameraId) return;
    const interval = setInterval(loadCurrentStatus, refreshInterval);
    return () => clearInterval(interval);
  }, [autoRefresh, refreshInterval, cameraId]);

  const loadCurrentStatus = async () => {
    if (!cameraId) return;
    try {
      const res: any = await ledSignApi.getStatus(cameraId);
      if (res.code === 200 && res.data) {
        const s = res.data;
        setDisplayMessage(s.currentMessage || '注意交通安全');
        setAlertMode(s.alertMode || false);
        setDisplayColor(s.color || 'GREEN');
        setBrightnessLevel(s.brightness || 100);
        setConnected(s.connected !== false);
        setProtocol(s.protocol || 'MOCK');
        setErrorMsg(s.errorMsg);
      }
    } catch (_) {}
  };

  const handleRefresh = async () => {
    if (!cameraId) return;
    setRefreshing(true);
    try {
      const res: any = await ledSignApi.refreshStatus(cameraId);
      if (res.code === 200) {
        message.success('LED状态已刷新');
        await loadCurrentStatus();
      } else {
        message.error('刷新失败');
      }
    } catch (_) {
      message.error('刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  const handleRestore = async () => {
    if (!cameraId) return;
    try {
      const res: any = await ledSignApi.restoreDefault(cameraId);
      if (res.code === 200) message.success('已恢复默认显示');
    } catch (_) {
      message.error('操作失败');
    }
  };

  const handleDisplayMessage = async () => {
    if (!cameraId || !customMessage.trim()) {
      message.warning('请输入显示内容');
      return;
    }
    try {
      const res: any = await ledSignApi.displayMessage(cameraId, {
        message: customMessage,
        color: displayColor,
        isAlert: alertMode,
        displaySeconds: 60,
      });
      if (res.code === 200) {
        message.success('已发送显示指令');
        setCustomMessage('');
      }
    } catch (_) {
      message.error('发送失败');
    }
  };

  const handleBrightnessChange = async (val: number | null) => {
    if (!cameraId || val === null) return;
    setBrightnessLevel(val);
    try {
      await ledSignApi.setBrightness(cameraId, val);
    } catch (_) {}
  };

  const getColorStyle = () => {
    const colorMap: Record<string, { text: string; bg: string; shadow: string }> = {
      RED: {
        text: '#ff4d4f',
        bg: 'rgba(255, 77, 79, 0.1)',
        shadow: '0 0 20px rgba(255, 77, 79, 0.6)',
      },
      YELLOW: {
        text: '#faad14',
        bg: 'rgba(250, 173, 20, 0.1)',
        shadow: '0 0 20px rgba(250, 173, 20, 0.6)',
      },
      GREEN: {
        text: '#52c41a',
        bg: 'rgba(82, 196, 26, 0.1)',
        shadow: '0 0 20px rgba(82, 196, 26, 0.5)',
      },
      BLUE: {
        text: '#1890ff',
        bg: 'rgba(24, 144, 255, 0.1)',
        shadow: '0 0 20px rgba(24, 144, 255, 0.5)',
      },
    };
    return colorMap[displayColor] || colorMap.GREEN;
  };

  const colorStyle = getColorStyle();
  const opacity = alertMode ? (blinkPhase === 0 ? 1 : 0.7) : 1;
  const filterBrightness = brightnessLevel / 100;

  return (
    <div className="led-sign-display" style={{ width: '100%' }}>
      <div
        style={{
          background: connected ? '#0a0a0a' : '#1a0505',
          borderRadius: 8,
          padding: '20px 24px',
          border: `3px solid ${connected ? '#333' : '#8b0000'}`,
          boxShadow: 'inset 0 2px 10px rgba(0,0,0,0.8), 0 4px 12px rgba(0,0,0,0.3)',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundImage: 'repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(0,0,0,0.3) 2px, rgba(0,0,0,0.3) 4px)',
            pointerEvents: 'none',
            zIndex: 1,
          }}
        />

        <div
          style={{
            position: 'relative',
            zIndex: 2,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 12,
          }}
        >
          <Space size="small">
            {connected ? (
              <BulbOutlined style={{ color: colorStyle.text, fontSize: 16, opacity }} />
            ) : (
              <DisconnectOutlined style={{ color: '#ff4d4f', fontSize: 16 }} />
            )}
            <span style={{ color: '#666', fontSize: 12 }}>
              {connected ? (alertMode ? '告警模式' : '正常模式') : '设备离线'}
            </span>
            <Tag color="default" style={{ fontSize: 10, padding: '0 4px', margin: 0 }}>
              {protocol}
            </Tag>
          </Space>
          <Space size="small">
            <Tag
              icon={connected ? (alertMode ? <WarningOutlined /> : <CheckCircleOutlined />) : <DisconnectOutlined />}
              color={connected ? (alertMode ? 'red' : 'green') : 'default'}
              style={{ margin: 0 }}
            >
              {connected ? (alertMode ? '告警中' : '正常') : '离线'}
            </Tag>
            {cameraId && (
              <Button
                type="text"
                size="small"
                icon={<ReloadOutlined />}
                loading={refreshing}
                onClick={handleRefresh}
                style={{ color: '#888', padding: '0 4px' }}
              />
            )}
          </Space>
        </div>

        <div
          style={{
            fontFamily: 'monospace',
            fontSize: 28,
            fontWeight: 'bold',
            textAlign: 'center',
            color: connected ? colorStyle.text : '#666',
            textShadow: connected ? colorStyle.shadow : 'none',
            padding: '16px 12px',
            background: connected ? colorStyle.bg : 'rgba(100,0,0,0.1)',
            borderRadius: 6,
            minHeight: 60,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            opacity,
            filter: `brightness(${filterBrightness})`,
            letterSpacing: 2,
            transition: 'all 0.3s ease',
            wordBreak: 'break-all',
          }}
        >
          {displayMessage || (connected ? '...' : '设备离线')}
        </div>

        {errorMsg && (
          <div style={{ color: '#ff7875', fontSize: 11, textAlign: 'center', marginTop: 6 }}>
            ⚠️ {errorMsg}
          </div>
        )}

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: 12,
            fontSize: 11,
            color: '#555',
          }}
        >
          <span>亮度: {brightnessLevel}%</span>
          {cameraId && <span>摄像头: {cameraId}</span>}
        </div>

        {showControls && cameraId && (
          <div style={{ marginTop: 16, paddingTop: 12, borderTop: '1px solid #333' }}>
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              <Space.Compact style={{ width: '100%' }}>
                <Input
                  placeholder="输入显示内容"
                  value={customMessage}
                  onChange={(e) => setCustomMessage(e.target.value)}
                  onPressEnter={handleDisplayMessage}
                  style={{ background: '#1a1a1a', color: '#fff', borderColor: '#444' }}
                />
                <Button type="primary" onClick={handleDisplayMessage}>发送</Button>
              </Space.Compact>
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Space size="small">
                  <span style={{ color: '#888', fontSize: 12 }}>亮度:</span>
                  <InputNumber
                    min={0}
                    max={100}
                    value={brightnessLevel}
                    onChange={handleBrightnessChange}
                    size="small"
                    style={{ width: 70 }}
                  />
                </Space>
                <Space size="small">
                  <Switch
                    checkedChildren="告警"
                    unCheckedChildren="正常"
                    checked={alertMode}
                    onChange={(c) => setAlertMode(c)}
                    size="small"
                  />
                  <Select
                    value={displayColor}
                    onChange={setDisplayColor}
                    size="small"
                    style={{ width: 80 }}
                    options={[
                      { value: 'RED', label: '红色' },
                      { value: 'YELLOW', label: '黄色' },
                      { value: 'GREEN', label: '绿色' },
                      { value: 'BLUE', label: '蓝色' },
                    ]}
                  />
                  <Button size="small" onClick={handleRestore}>恢复默认</Button>
                </Space>
              </Space>
            </Space>
          </div>
        )}
      </div>
    </div>
  );
};

export default LedSignDisplay;
