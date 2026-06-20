import React, { useEffect, useState } from 'react';
import { Card, Tag, Button, Space, InputNumber, Switch, Input, message } from 'antd';
import { BulbOutlined, WarningOutlined, CheckCircleOutlined } from '@ant-design/icons';

interface LedSignDisplayProps {
  cameraId?: number;
  message?: string;
  isAlert?: boolean;
  color?: string;
  brightness?: number;
  autoRefresh?: boolean;
  refreshInterval?: number;
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
}

const LedSignDisplay: React.FC<LedSignDisplayProps> = ({
  cameraId,
  message,
  isAlert = false,
  color = 'GREEN',
  brightness = 100,
  autoRefresh = false,
  refreshInterval = 2000,
  onStatusChange,
}) => {
  const [displayMessage, setDisplayMessage] = useState(message || '注意交通安全');
  const [alertMode, setAlertMode] = useState(isAlert);
  const [displayColor, setDisplayColor] = useState(color);
  const [brightnessLevel, setBrightnessLevel] = useState(brightness);
  const [blinkPhase, setBlinkPhase] = useState(0);

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
          background: '#0a0a0a',
          borderRadius: 8,
          padding: '20px 24px',
          border: '3px solid #333',
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
            <BulbOutlined style={{ color: colorStyle.text, fontSize: 16, opacity }} />
            <span style={{ color: '#666', fontSize: 12 }}>
              {alertMode ? '告警模式' : '正常模式'}
            </span>
          </Space>
          <Tag
            icon={alertMode ? <WarningOutlined /> : <CheckCircleOutlined />}
            color={alertMode ? 'red' : 'green'}
            style={{ margin: 0 }}
          >
            {alertMode ? '告警中' : '正常'}
          </Tag>
        </div>

        <div
          style={{
            fontFamily: 'monospace',
            fontSize: 28,
            fontWeight: 'bold',
            textAlign: 'center',
            color: colorStyle.text,
            textShadow: colorStyle.shadow,
            padding: '16px 12px',
            background: colorStyle.bg,
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
          {displayMessage || '...'}
        </div>

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
      </div>
    </div>
  );
};

export default LedSignDisplay;
