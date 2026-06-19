import React, { useEffect, useRef, useState, useCallback } from 'react';
import Hls from 'hls.js';
import { Card, Spin, Switch, message, Slider, Select, Tag, Space, Tooltip, Button, Collapse } from 'antd';
import {
  CameraOutlined,
  ExclamationCircleOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  BulbOutlined,
  ContrastOutlined,
  ThunderboltOutlined,
  CloudOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { wsService, type DetectionItem } from '@/services/websocket';
import { enhanceApi } from '@/services/api';
import type { StreamEnhancementStatus } from '@/types';
import {
  ENHANCEMENT_ALGORITHM_OPTIONS,
  SCENE_TYPE_LABELS,
  SCENE_TYPE_COLORS,
} from '@/types';

interface VideoPlayerProps {
  url: string;
  cameraId?: number;
  cameraName?: string;
  autoPlay?: boolean;
  muted?: boolean;
  showControls?: boolean;
  height?: number;
  detections?: DetectionBox[];
  className?: string;
  enableDetectionOverlay?: boolean;
  enableEnhancementControls?: boolean;
}

export interface DetectionBox {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  className: string;
  confidence: number;
  color?: string;
  trackId?: number;
}

const CLASS_COLORS: Record<string, string> = {
  person: '#ff4d4f',
  car: '#1890ff',
  truck: '#722ed1',
  bus: '#fa8c16',
  motorcycle: '#52c41a',
  bicycle: '#13c2c2',
  debris: '#eb2f96',
  backpack: '#eb2f96',
  handbag: '#eb2f96',
  suitcase: '#eb2f96',
  bottle: '#eb2f96',
  cup: '#eb2f96',
  default: '#faad14',
};

const VideoPlayer: React.FC<VideoPlayerProps> = ({
  url,
  cameraId,
  cameraName,
  autoPlay = true,
  muted = true,
  showControls = true,
  height = 240,
  detections: externalDetections,
  className = '',
  enableDetectionOverlay = true,
  enableEnhancementControls = true,
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [videoSize, setVideoSize] = useState({ width: 0, height: 0 });
  const [liveDetections, setLiveDetections] = useState<DetectionItem[]>([]);
  const [showDetections, setShowDetections] = useState(true);
  const [subscribed, setSubscribed] = useState(false);
  
  const [enhancementEnabled, setEnhancementEnabled] = useState(false);
  const [autoTrigger, setAutoTrigger] = useState(true);
  const [algorithm, setAlgorithm] = useState('auto');
  const [brightness, setBrightness] = useState(1.0);
  const [contrast, setContrast] = useState(1.0);
  const [enhancementStatus, setEnhancementStatus] = useState<StreamEnhancementStatus | null>(null);
  const [showEnhancementPanel, setShowEnhancementPanel] = useState(false);
  const [previewBrightness, setPreviewBrightness] = useState(1.0);
  const [previewContrast, setPreviewContrast] = useState(1.0);
  const [syncingConfig, setSyncingConfig] = useState(false);

  useEffect(() => {
    if (!cameraId || !enableDetectionOverlay) return;
    setSubscribed(true);
    const unsub = wsService.onCameraDetection(cameraId, (_camId, data) => {
      if (data && data.detections) {
        setLiveDetections(data.detections);
      }
    });
    return () => {
      unsub();
      setLiveDetections([]);
      setSubscribed(false);
    };
  }, [cameraId, enableDetectionOverlay]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !url) {
      setLoading(false);
      setError('无视频地址');
      return;
    }

    setLoading(true);
    setError(null);

    const cleanup = () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };

    if (Hls.isSupported() && (url.includes('.m3u8') || url.includes('hls'))) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
        liveSyncDurationCount: 3,
      });
      hlsRef.current = hls;

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        setLoading(false);
        if (autoPlay) {
          video.play().catch(() => {});
        }
      });

      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          setError('视频加载失败');
          setLoading(false);
          cleanup();
        }
      });

      hls.loadSource(url);
      hls.attachMedia(video);
    } else if (video.canPlayType('application/vnd.apple.mpegurl') || url.startsWith('rtmp') || url.startsWith('rtsp') || url.startsWith('http')) {
      video.src = url;
      video.onloadeddata = () => {
        setLoading(false);
        setVideoSize({ width: video.videoWidth, height: video.videoHeight });
        if (autoPlay) {
          video.play().catch(() => {});
        }
      };
      video.onerror = () => {
        setError('视频加载失败');
        setLoading(false);
      };
    } else {
      setError('不支持的视频格式');
      setLoading(false);
    }

    return cleanup;
  }, [url, autoPlay]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    const handleResize = () => {
      setVideoSize({ width: video.videoWidth, height: video.videoHeight });
    };
    video.addEventListener('resize', handleResize);
    return () => video.removeEventListener('resize', handleResize);
  }, []);

  const fetchEnhancementStatus = useCallback(async () => {
    if (!cameraId || !enableEnhancementControls) return;
    try {
      const result = await enhanceApi.getStreamStatus(cameraId);
      if (result.code === 200 && result.data) {
        const status = result.data as StreamEnhancementStatus;
        setEnhancementStatus(status);
        setEnhancementEnabled(status.enabled);
        setAutoTrigger(status.autoTrigger);
        setAlgorithm(status.algorithm);
        setBrightness(status.brightness);
        setContrast(status.contrast);
        setPreviewBrightness(status.brightness);
        setPreviewContrast(status.contrast);
      }
    } catch (e) {
    }
  }, [cameraId, enableEnhancementControls]);

  useEffect(() => {
    fetchEnhancementStatus();
  }, [fetchEnhancementStatus]);

  const syncEnhancementConfig = useCallback(async (params: {
    enableEnhancement?: boolean;
    autoTrigger?: boolean;
    algorithm?: string;
    brightness?: number;
    contrast?: number;
  }) => {
    if (!cameraId) return;
    setSyncingConfig(true);
    try {
      const result = await enhanceApi.updateStreamConfig(cameraId, params);
      if (result.code === 200 && result.data) {
        const status = result.data.enhancement as StreamEnhancementStatus;
        setEnhancementStatus(status);
        message.success('配置已同步到后端');
      }
    } catch (e) {
      message.error('同步配置失败');
    } finally {
      setSyncingConfig(false);
    }
  }, [cameraId]);

  const handleToggleEnhancement = (checked: boolean) => {
    setEnhancementEnabled(checked);
    if (cameraId) {
      syncEnhancementConfig({ enableEnhancement: checked });
    }
  };

  const handleToggleAutoTrigger = (checked: boolean) => {
    setAutoTrigger(checked);
    if (cameraId) {
      syncEnhancementConfig({ autoTrigger: checked });
    }
  };

  const handleAlgorithmChange = (value: string) => {
    setAlgorithm(value);
    if (cameraId) {
      syncEnhancementConfig({ algorithm: value });
    }
  };

  const handleBrightnessChange = (value: number) => {
    setPreviewBrightness(value);
  };

  const handleBrightnessAfterChange = (value: number) => {
    setBrightness(value);
    if (cameraId) {
      syncEnhancementConfig({ brightness: value });
    }
  };

  const handleContrastChange = (value: number) => {
    setPreviewContrast(value);
  };

  const handleContrastAfterChange = (value: number) => {
    setContrast(value);
    if (cameraId) {
      syncEnhancementConfig({ contrast: value });
    }
  };

  const handleReset = () => {
    setPreviewBrightness(1.0);
    setPreviewContrast(1.0);
    setBrightness(1.0);
    setContrast(1.0);
    setAlgorithm('auto');
    setAutoTrigger(true);
    if (cameraId) {
      syncEnhancementConfig({
        brightness: 1.0,
        contrast: 1.0,
        algorithm: 'auto',
        autoTrigger: true,
      });
    }
  };

  const getVideoFilterStyle = () => {
    if (previewBrightness === 1.0 && previewContrast === 1.0) {
      return {};
    }
    return {
      filter: `brightness(${previewBrightness}) contrast(${previewContrast})`,
    };
  };

  const allDetections: DetectionBox[] = [];

  if (externalDetections && externalDetections.length > 0) {
    allDetections.push(...externalDetections);
  }

  if (showDetections && liveDetections.length > 0) {
    liveDetections.forEach((d) => {
      allDetections.push({
        x1: d.bbox.x1,
        y1: d.bbox.y1,
        x2: d.bbox.x2,
        y2: d.bbox.y2,
        className: d.className,
        confidence: d.confidence,
        trackId: d.trackId,
      });
    });
  }

  const renderDetections = () => {
    if (allDetections.length === 0) return null;

    const container = containerRef.current;
    if (!container) return null;

    const containerWidth = container.clientWidth;
    const containerHeight = container.clientHeight || height;

    let scaleX = 1, scaleY = 1;
    if (videoSize.width > 0 && videoSize.height > 0) {
      scaleX = containerWidth / videoSize.width;
      scaleY = containerHeight / videoSize.height;
    } else {
      scaleX = containerWidth / 1280;
      scaleY = containerHeight / 720;
    }

    return allDetections.map((det, idx) => {
      const lowerName = det.className.toLowerCase();
      const color = det.color || CLASS_COLORS[lowerName] || CLASS_COLORS.default;
      const x = det.x1 * scaleX;
      const y = det.y1 * scaleY;
      const width = (det.x2 - det.x1) * scaleX;
      const h = (det.y2 - det.y1) * scaleY;

      if (width < 3 || h < 3) return null;

      return (
        <div
          key={idx}
          style={{
            position: 'absolute',
            left: `${Math.max(0, x)}px`,
            top: `${Math.max(0, y)}px`,
            width: `${Math.max(3, width)}px`,
            height: `${Math.max(3, h)}px`,
            border: `2px solid ${color}`,
            borderRadius: 4,
            boxShadow: `0 0 0 1px rgba(0,0,0,0.3), inset 0 0 0 1px rgba(0,0,0,0.1)`,
            pointerEvents: 'none',
          }}
        >
          <span
            style={{
              position: 'absolute',
              top: -22,
              left: 0,
              background: color,
              color: '#fff',
              padding: '2px 6px',
              borderRadius: 4,
              fontSize: 11,
              whiteSpace: 'nowrap',
            }}
          >
            {det.trackId !== undefined ? `#${det.trackId} ` : ''}
            {det.className} {(det.confidence * 100).toFixed(0)}%
          </span>
        </div>
      );
    });
  };

  const renderSceneBadge = () => {
    if (!enhancementStatus) return null;
    const sceneType = enhancementStatus.sceneType;
    const label = SCENE_TYPE_LABELS[sceneType] || sceneType;
    const color = SCENE_TYPE_COLORS[sceneType] || 'default';
    
    let icon = <CameraOutlined />;
    if (sceneType === 'night' || sceneType === 'backlight') {
      icon = <BulbOutlined />;
    } else if (['rain', 'fog', 'snow'].includes(sceneType)) {
      icon = <CloudOutlined />;
    }
    
    return (
      <Tag color={color} icon={icon} style={{ marginLeft: 8 }}>
        {label}
        {enhancementStatus.active && (
          <ThunderboltOutlined style={{ marginLeft: 4, color: '#faad14' }} />
        )}
      </Tag>
    );
  };

  const titleContent = cameraName ? (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
      <span style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        <CameraOutlined />
        {cameraName}
        {enableEnhancementControls && renderSceneBadge()}
      </span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 12, color: '#666' }}>
        {cameraId && enableDetectionOverlay && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Switch
              size="small"
              checked={showDetections}
              onChange={setShowDetections}
              checkedChildren={<EyeOutlined />}
              unCheckedChildren={<EyeInvisibleOutlined />}
            />
            <span>检测框</span>
            {subscribed && liveDetections.length > 0 && (
              <span style={{ color: '#52c41a' }}>({liveDetections.length}个目标)</span>
            )}
          </div>
        )}
        {enableEnhancementControls && cameraId && (
          <Tooltip title={showEnhancementPanel ? '隐藏图像增强控制' : '图像增强控制'}>
            <Button
              type={enhancementEnabled ? 'primary' : 'default'}
              size="small"
              icon={<SettingOutlined />}
              onClick={() => setShowEnhancementPanel(!showEnhancementPanel)}
              style={{ padding: '0 8px' }}
            >
              增强
            </Button>
          </Tooltip>
        )}
      </div>
    </div>
  ) : null;

  const renderEnhancementPanel = () => {
    if (!showEnhancementPanel || !enableEnhancementControls) return null;
    
    const items = [
      {
        key: 'enhancement',
        label: (
          <Space>
            <SettingOutlined />
            <span>图像增强控制</span>
          </Space>
        ),
        children: (
          <div style={{ padding: '8px 0' }}>
            <div style={{ marginBottom: 16 }}>
              <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }}>
                <Space>
                  <Switch
                    size="small"
                    checked={enhancementEnabled}
                    onChange={handleToggleEnhancement}
                    loading={syncingConfig}
                  />
                  <span style={{ fontSize: 13, fontWeight: 500 }}>启用图像增强</span>
                </Space>
                {enhancementStatus?.active && (
                  <Tag color="success" icon={<ThunderboltOutlined />}>
                    增强中
                  </Tag>
                )}
              </Space>
            </div>

            <div style={{ marginBottom: 16 }}>
              <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }}>
                <Space>
                  <Switch
                    size="small"
                    checked={autoTrigger}
                    onChange={handleToggleAutoTrigger}
                    disabled={!enhancementEnabled}
                    loading={syncingConfig}
                  />
                  <span style={{ fontSize: 13 }}>自动触发 (根据场景)</span>
                </Space>
              </Space>
            </div>

            <div style={{ marginBottom: 16 }}>
              <div style={{ fontSize: 13, marginBottom: 8, color: '#666' }}>
                增强算法
              </div>
              <Select
                size="small"
                value={algorithm}
                onChange={handleAlgorithmChange}
                style={{ width: '100%' }}
                disabled={!enhancementEnabled || autoTrigger}
                loading={syncingConfig}
                options={ENHANCEMENT_ALGORITHM_OPTIONS}
              />
            </div>

            <div style={{ marginBottom: 16 }}>
              <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }}>
                <Space>
                  <BulbOutlined style={{ color: '#faad14' }} />
                  <span style={{ fontSize: 13 }}>亮度调节</span>
                </Space>
                <span style={{ fontSize: 12, color: '#999', fontFamily: 'monospace' }}>
                  {previewBrightness.toFixed(2)}x
                </span>
              </Space>
              <Slider
                min={0.5}
                max={2.0}
                step={0.05}
                value={previewBrightness}
                onChange={handleBrightnessChange}
                onChangeComplete={handleBrightnessAfterChange}
                disabled={!enhancementEnabled}
                tooltip={{ formatter: (v) => `${v?.toFixed(2)}x` }}
              />
            </div>

            <div style={{ marginBottom: 16 }}>
              <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }}>
                <Space>
                  <ContrastOutlined style={{ color: '#1890ff' }} />
                  <span style={{ fontSize: 13 }}>对比度调节</span>
                </Space>
                <span style={{ fontSize: 12, color: '#999', fontFamily: 'monospace' }}>
                  {previewContrast.toFixed(2)}x
                </span>
              </Space>
              <Slider
                min={0.5}
                max={2.0}
                step={0.05}
                value={previewContrast}
                onChange={handleContrastChange}
                onChangeComplete={handleContrastAfterChange}
                disabled={!enhancementEnabled}
                tooltip={{ formatter: (v) => `${v?.toFixed(2)}x` }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Space size={8}>
                {enhancementStatus && (
                  <Tag color={SCENE_TYPE_COLORS[enhancementStatus.sceneType] || 'default'}>
                    当前场景: {SCENE_TYPE_LABELS[enhancementStatus.sceneType] || enhancementStatus.sceneType}
                  </Tag>
                )}
              </Space>
              <Button
                size="small"
                onClick={handleReset}
                disabled={syncingConfig}
              >
                重置默认
              </Button>
            </div>
          </div>
        ),
      },
    ];

    return (
      <div style={{ borderTop: '1px solid #f0f0f0' }}>
        <Collapse
          items={items}
          defaultActiveKey={['enhancement']}
          ghost
          size="small"
        />
      </div>
    );
  };

  return (
    <Card
      className={className}
      bodyStyle={{ padding: 0, position: 'relative', overflow: 'hidden' }}
      style={{ borderRadius: 8 }}
      title={titleContent}
    >
      <div
        ref={containerRef}
        style={{
          position: 'relative',
          width: '100%',
          height,
          background: '#000',
        }}
      >
        <video
          ref={videoRef}
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'contain',
            background: '#000',
            transition: 'filter 0.3s ease',
            ...getVideoFilterStyle(),
          }}
          muted={muted}
          controls={showControls}
          playsInline
          crossOrigin="anonymous"
        />
        {renderDetections()}
        {loading && (
          <div
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: 'rgba(0,0,0,0.5)',
            }}
          >
            <Spin tip="视频加载中..." />
          </div>
        )}
        {error && !loading && (
          <div
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              background: 'rgba(0,0,0,0.7)',
              color: '#fff',
              gap: 8,
            }}
          >
            <ExclamationCircleOutlined style={{ fontSize: 40, color: '#ff4d4f' }} />
            <span>{error}</span>
          </div>
        )}
      </div>
      {renderEnhancementPanel()}
    </Card>
  );
};

export default VideoPlayer;
