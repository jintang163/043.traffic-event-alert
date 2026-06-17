import React, { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import { Card, Spin, message } from 'antd';
import { CameraOutlined, ExclamationCircleOutlined } from '@ant-design/icons';

interface VideoPlayerProps {
  url: string;
  cameraName?: string;
  autoPlay?: boolean;
  muted?: boolean;
  showControls?: boolean;
  height?: number;
  detections?: DetectionBox[];
  className?: string;
}

interface DetectionBox {
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
  default: '#faad14',
};

const VideoPlayer: React.FC<VideoPlayerProps> = ({
  url,
  cameraName,
  autoPlay = true,
  muted = true,
  showControls = true,
  height = 240,
  detections = [],
  className = '',
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [videoSize, setVideoSize] = useState({ width: 0, height: 0 });

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

  const renderDetections = () => {
    if (detections.length === 0 || !videoSize.width) return null;

    const container = containerRef.current;
    if (!container) return null;

    const containerWidth = container.clientWidth;
    const containerHeight = container.clientHeight || height;

    const scaleX = containerWidth / videoSize.width || 1;
    const scaleY = containerHeight / videoSize.height || 1;

    return detections.map((det, idx) => {
      const color = det.color || CLASS_COLORS[det.className.toLowerCase()] || CLASS_COLORS.default;
      const x = det.x1 * scaleX;
      const y = det.y1 * scaleY;
      const width = (det.x2 - det.x1) * scaleX;
      const h = (det.y2 - det.y1) * scaleY;

      return (
        <div
          key={idx}
          style={{
            position: 'absolute',
            left: `${x}px`,
            top: `${y}px`,
            width: `${width}px`,
            height: `${h}px`,
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

  return (
    <Card
      className={className}
      bodyStyle={{ padding: 0, position: 'relative', overflow: 'hidden' }}
      style={{ borderRadius: 8 }}
      title={
        cameraName ? (
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <CameraOutlined />
            {cameraName}
          </span>
        ) : null
      }
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
    </Card>
  );
};

export default VideoPlayer;
