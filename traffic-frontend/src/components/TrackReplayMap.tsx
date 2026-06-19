import React, { useEffect, useRef, useState, useCallback } from 'react';
import { Card, Button, Space, Slider, Select, Tag, Tooltip, Statistic, Row, Col, Empty, Segmented, Alert } from 'antd';
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  ReloadOutlined,
  FastForwardOutlined,
  EnvironmentOutlined,
  WarningOutlined,
  CarOutlined,
  ClockCircleOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { TrackPoint, GlobalTrack, AlertEvent } from '@/types';

const { Option } = Select;

interface TrackReplayMapProps {
  event: AlertEvent;
  tracks: GlobalTrack[];
  trackPointsMap: Record<number, TrackPoint[]>;
  beforeMinutes?: number;
  startTimeStr?: string;
  endTimeStr?: string;
  height?: number;
  pixelMode?: boolean;
}

interface PlayState {
  isPlaying: boolean;
  currentTime: number;
  speed: number;
  progress: number;
}

const TRACK_COLORS = ['#1890ff', '#52c41a', '#faad14', '#eb2f96', '#722ed1', '#13c2c2'];

const parseTime = (t?: string | number | Date): number => {
  if (!t) return 0;
  return new Date(t).getTime();
};

const TrackReplayMap: React.FC<TrackReplayMapProps> = ({
  event,
  tracks,
  trackPointsMap,
  beforeMinutes = 5,
  startTimeStr,
  endTimeStr,
  height = 480,
  pixelMode: initialPixelMode = false,
}) => {
  const mapRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const polylineRefs = useRef<Record<number, L.Polyline>>({});
  const markerRefs = useRef<Record<number, L.Marker>>({});
  const eventMarkerRef = useRef<L.Marker | null>(null);
  const animationRef = useRef<number | null>(null);
  const lastTimeRef = useRef<number>(0);
  const canvasCtxRef = useRef<CanvasRenderingContext2D | null>(null);

  const [pixelMode, setPixelMode] = useState(initialPixelMode);
  const [playState, setPlayState] = useState<PlayState>({
    isPlaying: false,
    currentTime: 0,
    speed: 1,
    progress: 0,
  });
  const [selectedTrackId, setSelectedTrackId] = useState<number | null>(null);
  const [currentPointInfo, setCurrentPointInfo] = useState<Record<number, TrackPoint>>({});

  const allPoints = Object.values(trackPointsMap).flat();

  const windowStartTime = parseTime(startTimeStr);
  const windowEndTime = parseTime(endTimeStr);
  const hasValidWindow = windowStartTime > 0 && windowEndTime > 0 && windowEndTime > windowStartTime;

  const fallbackTimeRange = useCallback(() => {
    if (allPoints.length === 0) return { startTime: 0, endTime: 0, duration: 0 };
    const times = allPoints.filter((p) => p.frameTime).map((p) => parseTime(p.frameTime));
    if (times.length === 0) return { startTime: 0, endTime: 0, duration: 0 };
    return {
      startTime: Math.min(...times),
      endTime: Math.max(...times),
      duration: Math.max(...times) - Math.min(...times),
    };
  }, [allPoints]);

  const getStrictTimeRange = useCallback(() => {
    if (hasValidWindow) {
      return {
        startTime: windowStartTime,
        endTime: windowEndTime,
        duration: windowEndTime - windowStartTime,
      };
    }
    return fallbackTimeRange();
  }, [hasValidWindow, windowStartTime, windowEndTime, fallbackTimeRange]);

  const timeRange = getStrictTimeRange();

  const hasValidGpsPoints = allPoints.some((p) => p.longitude != null && p.latitude != null);
  const hasValidPixelPoints = allPoints.some((p) => p.pixelX != null && p.pixelY != null);

  const interpolatePoint = useCallback(
    (
      points: TrackPoint[],
      targetTime: number,
      useGps: boolean
    ): { x: number; y: number; point: TrackPoint | null } => {
      const validPoints = points.filter((p) => {
        if (!p.frameTime) return false;
        if (useGps) return p.longitude != null && p.latitude != null;
        return p.pixelX != null && p.pixelY != null;
      });
      if (validPoints.length === 0) return { x: 0, y: 0, point: null };

      for (let i = 0; i < validPoints.length; i++) {
        const pointTime = parseTime(validPoints[i].frameTime);
        if (pointTime >= targetTime) {
          if (i === 0) {
            const p = validPoints[0];
            return useGps
              ? { x: p.longitude as number, y: p.latitude as number, point: p }
              : { x: p.pixelX as number, y: p.pixelY as number, point: p };
          }

          const prevPoint = validPoints[i - 1];
          const prevTime = parseTime(prevPoint.frameTime);
          const currTime = pointTime;
          const span = currTime - prevTime;
          const ratio = span > 0 ? (targetTime - prevTime) / span : 0;

          if (useGps) {
            return {
              x: (prevPoint.longitude as number) + ((validPoints[i].longitude as number) - (prevPoint.longitude as number)) * ratio,
              y: (prevPoint.latitude as number) + ((validPoints[i].latitude as number) - (prevPoint.latitude as number)) * ratio,
              point: {
                ...prevPoint,
                speed: prevPoint.speed != null && validPoints[i].speed != null
                  ? prevPoint.speed + (validPoints[i].speed! - prevPoint.speed) * ratio
                  : prevPoint.speed,
              } as TrackPoint,
            };
          } else {
            return {
              x: (prevPoint.pixelX as number) + ((validPoints[i].pixelX as number) - (prevPoint.pixelX as number)) * ratio,
              y: (prevPoint.pixelY as number) + ((validPoints[i].pixelY as number) - (prevPoint.pixelY as number)) * ratio,
              point: {
                ...prevPoint,
                speed: prevPoint.speed != null && validPoints[i].speed != null
                  ? prevPoint.speed + (validPoints[i].speed! - prevPoint.speed) * ratio
                  : prevPoint.speed,
              } as TrackPoint,
            };
          }
        }
      }

      const last = validPoints[validPoints.length - 1];
      return useGps
        ? { x: last.longitude as number, y: last.latitude as number, point: last }
        : { x: last.pixelX as number, y: last.pixelY as number, point: last };
    },
    []
  );

  const initLeafletMap = useCallback(() => {
    if (!mapRef.current || mapInstanceRef.current) return;

    const map = L.map(mapRef.current, {
      zoomControl: true,
      attributionControl: false,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
    }).addTo(map);

    mapInstanceRef.current = map;
  }, []);

  const fitMapToBounds = useCallback(() => {
    if (!mapInstanceRef.current || allPoints.length === 0) return;

    const validPoints = allPoints.filter((p) => p.longitude != null && p.latitude != null);
    if (validPoints.length === 0) return;

    const bounds = L.latLngBounds(validPoints.map((p) => [p.latitude!, p.longitude!]));

    if (event.latitude != null && event.longitude != null) {
      bounds.extend([event.latitude, event.longitude]);
    }

    mapInstanceRef.current.fitBounds(bounds.pad(0.15));
  }, [allPoints, event]);

  const createEventMarker = useCallback(() => {
    if (!mapInstanceRef.current) return;
    if (event.longitude == null || event.latitude == null) return;

    if (eventMarkerRef.current) {
      eventMarkerRef.current.remove();
    }

    const eventIcon = L.divIcon({
      className: 'event-marker',
      html: `
        <div style="
          position: relative;
          width: 32px;
          height: 32px;
          transform: translate(-50%, -100%);
        ">
          <div style="
            position: absolute;
            bottom: 0;
            left: 50%;
            transform: translateX(-50%);
            width: 24px;
            height: 24px;
            background: #ff4d4f;
            border: 2px solid #fff;
            border-radius: 50% 50% 50% 0;
            transform: rotate(-45deg) translateX(-50%);
            box-shadow: 0 2px 8px rgba(0,0,0,0.3);
          "></div>
          <div style="
            position: absolute;
            bottom: 6px;
            left: 50%;
            transform: translateX(-50%);
            color: #fff;
            font-size: 12px;
            font-weight: bold;
          ">!</div>
          <div style="
            position: absolute;
            top: -8px;
            left: 50%;
            transform: translateX(-50%);
            width: 16px;
            height: 16px;
            background: rgba(255,77,79,0.4);
            border-radius: 50%;
            animation: pulse 2s infinite;
          "></div>
        </div>
      `,
      iconSize: [32, 32],
      iconAnchor: [16, 32],
    });

    const marker = L.marker([event.latitude, event.longitude], {
      icon: eventIcon,
    }).addTo(mapInstanceRef.current);

    marker.bindPopup(`
      <div style="min-width: 160px;">
        <div style="font-weight: 600; margin-bottom: 4px; color: #ff4d4f;">
          事件发生点
        </div>
        <div style="font-size: 12px; color: #666;">
          ${event.eventTime || ''}
        </div>
        <div style="font-size: 12px; margin-top: 4px;">
          ${event.location || ''}
        </div>
      </div>
    `);

    eventMarkerRef.current = marker;
  }, [event]);

  const drawTrackPolylines = useCallback(() => {
    if (!mapInstanceRef.current) return;

    Object.values(polylineRefs.current).forEach((line) => line.remove());
    polylineRefs.current = {};

    tracks.forEach((track, idx) => {
      const points = trackPointsMap[track.id] || [];
      const validPoints = points.filter((p) => p.longitude != null && p.latitude != null);
      if (validPoints.length < 2) return;

      const color = TRACK_COLORS[idx % TRACK_COLORS.length];
      const latLngs = validPoints.map((p) => [p.latitude!, p.longitude!]) as L.LatLngTuple[];

      const polyline = L.polyline(latLngs, {
        color,
        weight: 3,
        opacity: 0.6,
        lineJoin: 'round',
        lineCap: 'round',
        dashArray: '1, 0',
      }).addTo(mapInstanceRef.current!);

      polylineRefs.current[track.id] = polyline;
    });
  }, [tracks, trackPointsMap]);

  const createTrackMarkers = useCallback(() => {
    if (!mapInstanceRef.current) return;

    Object.values(markerRefs.current).forEach((m) => m.remove());
    markerRefs.current = {};

    tracks.forEach((track, idx) => {
      const points = trackPointsMap[track.id] || [];
      const firstPoint = points.find((p) => p.longitude != null && p.latitude != null);
      if (!firstPoint) return;

      const color = TRACK_COLORS[idx % TRACK_COLORS.length];

      const carIcon = L.divIcon({
        className: 'track-marker',
        html: `
          <div style="
            position: relative;
            width: 28px;
            height: 28px;
            transform: translate(-50%, -50%);
          ">
            <div style="
              width: 28px;
              height: 28px;
              background: ${color};
              border: 3px solid #fff;
              border-radius: 50%;
              box-shadow: 0 2px 8px rgba(0,0,0,0.4);
              display: flex;
              align-items: center;
              justify-content: center;
            ">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="#fff">
                <path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.22.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z"/>
              </svg>
            </div>
            <div style="
              position: absolute;
              top: -4px;
              left: -4px;
              right: -4px;
              bottom: -4px;
              border: 2px solid ${color};
              border-radius: 50%;
              opacity: 0.4;
              animation: pulse-ring 1.5s infinite;
            "></div>
          </div>
        `,
        iconSize: [28, 28],
        iconAnchor: [14, 14],
      });

      const marker = L.marker([firstPoint.latitude!, firstPoint.longitude!], {
        icon: carIcon,
        zIndexOffset: 1000,
      }).addTo(mapInstanceRef.current!);

      markerRefs.current[track.id] = marker;
    });
  }, [tracks, trackPointsMap]);

  const updateMarkerPositions = useCallback(
    (currentTimeMs: number) => {
      const newPointInfo: Record<number, TrackPoint> = {};

      if (!pixelMode) {
        tracks.forEach((track) => {
          const points = trackPointsMap[track.id] || [];
          const marker = markerRefs.current[track.id];
          if (!marker || points.length === 0) return;

          const { x: lng, y: lat, point } = interpolatePoint(points, currentTimeMs, true);
          if (point && lng != null && lat != null) {
            marker.setLatLng([lat, lng]);
            newPointInfo[track.id] = point;
          }
        });
      }

      setCurrentPointInfo(newPointInfo);
    },
    [tracks, trackPointsMap, interpolatePoint, pixelMode]
  );

  const drawPixelCanvas = useCallback(
    (currentTimeMs: number) => {
      if (!canvasRef.current) return;
      const ctx = canvasRef.current.getContext('2d');
      if (!ctx) return;
      canvasCtxRef.current = ctx;

      const w = canvasRef.current.width;
      const h = canvasRef.current.height;

      ctx.clearRect(0, 0, w, h);

      ctx.strokeStyle = '#f0f0f0';
      ctx.lineWidth = 1;
      const gridStep = 20;
      for (let x = 0; x <= w; x += gridStep) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, h);
        ctx.stroke();
      }
      for (let y = 0; y <= h; y += gridStep) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(w, y);
        ctx.stroke();
      }

      const padding = 24;
      const plotW = w - padding * 2;
      const plotH = h - padding * 2;

      tracks.forEach((track, idx) => {
        const points = trackPointsMap[track.id] || [];
        const validPoints = points.filter((p) => p.pixelX != null && p.pixelY != null);
        if (validPoints.length === 0) return;

        const color = TRACK_COLORS[idx % TRACK_COLORS.length];

        ctx.strokeStyle = color;
        ctx.lineWidth = 3;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.globalAlpha = 0.5;
        ctx.beginPath();
        validPoints.forEach((p, i) => {
          const px = padding + (p.pixelX as number) * plotW;
          const py = padding + (p.pixelY as number) * plotH;
          if (i === 0) ctx.moveTo(px, py);
          else ctx.lineTo(px, py);
        });
        ctx.stroke();
        ctx.globalAlpha = 1;

        const { x: curX, y: curY } = interpolatePoint(points, currentTimeMs, false);
        const markerX = padding + curX * plotW;
        const markerY = padding + curY * plotH;

        ctx.beginPath();
        ctx.arc(markerX, markerY, 4, 0, Math.PI * 2);
        ctx.fillStyle = color;
        ctx.fill();
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 2;
        ctx.stroke();

        ctx.beginPath();
        ctx.arc(markerX, markerY, 12, 0, Math.PI * 2);
        ctx.strokeStyle = color;
        ctx.globalAlpha = 0.3;
        ctx.lineWidth = 2;
        ctx.stroke();
        ctx.globalAlpha = 1;
      });

      ctx.fillStyle = '#ff4d4f';
      ctx.font = 'bold 11px sans-serif';
      ctx.fillText('起点 (0,0)', 4, h - 4);
      ctx.fillText('终点 (1,1)', w - 70, 14);
      ctx.fillStyle = '#666';
      ctx.font = '10px sans-serif';
      ctx.fillText('像素坐标系 (归一化)', w / 2 - 50, 14);
    },
    [tracks, trackPointsMap, interpolatePoint]
  );

  const animate = useCallback(
    (timestamp: number) => {
      if (!playState.isPlaying) return;

      if (lastTimeRef.current === 0) {
        lastTimeRef.current = timestamp;
        animationRef.current = requestAnimationFrame(animate);
        return;
      }

      const delta = timestamp - lastTimeRef.current;
      lastTimeRef.current = timestamp;

      const newTime = playState.currentTime + delta * playState.speed;

      if (newTime >= timeRange.endTime) {
        setPlayState((prev) => ({
          ...prev,
          isPlaying: false,
          currentTime: timeRange.endTime,
          progress: 100,
        }));
        updateMarkerPositions(timeRange.endTime);
        if (pixelMode) drawPixelCanvas(timeRange.endTime);
        return;
      }

      if (newTime < timeRange.startTime) {
        setPlayState((prev) => ({
          ...prev,
          currentTime: timeRange.startTime,
          progress: 0,
        }));
        updateMarkerPositions(timeRange.startTime);
        if (pixelMode) drawPixelCanvas(timeRange.startTime);
        animationRef.current = requestAnimationFrame(animate);
        return;
      }

      const progress = timeRange.duration > 0
        ? ((newTime - timeRange.startTime) / timeRange.duration) * 100
        : 0;

      setPlayState((prev) => ({
        ...prev,
        currentTime: newTime,
        progress,
      }));
      updateMarkerPositions(newTime);
      if (pixelMode) drawPixelCanvas(newTime);

      animationRef.current = requestAnimationFrame(animate);
    },
    [playState.isPlaying, playState.currentTime, playState.speed, timeRange, updateMarkerPositions, pixelMode, drawPixelCanvas]
  );

  useEffect(() => {
    if (playState.isPlaying) {
      lastTimeRef.current = 0;
      animationRef.current = requestAnimationFrame(animate);
    } else if (animationRef.current) {
      cancelAnimationFrame(animationRef.current);
    }

    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, [playState.isPlaying, animate]);

  useEffect(() => {
    if (!pixelMode) {
      initLeafletMap();
    } else {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
        eventMarkerRef.current = null;
        polylineRefs.current = {};
        markerRefs.current = {};
      }
    }
  }, [initLeafletMap, pixelMode]);

  useEffect(() => {
    if (pixelMode) {
      if (timeRange.duration > 0) {
        const startT = Math.max(timeRange.startTime, parseTime(allPoints[0]?.frameTime));
        setPlayState((prev) => ({
          ...prev,
          currentTime: startT,
        }));
        drawPixelCanvas(startT);
      }
    } else if (mapInstanceRef.current) {
      setTimeout(() => {
        mapInstanceRef.current?.invalidateSize();
        drawTrackPolylines();
        createTrackMarkers();
        createEventMarker();
        fitMapToBounds();

        if (timeRange.duration > 0) {
          setPlayState((prev) => ({
            ...prev,
            currentTime: timeRange.startTime,
          }));
          updateMarkerPositions(timeRange.startTime);
        }
      }, 100);
    }
  }, [
    drawTrackPolylines,
    createTrackMarkers,
    createEventMarker,
    fitMapToBounds,
    timeRange.startTime,
    timeRange.duration,
    updateMarkerPositions,
    pixelMode,
    drawPixelCanvas,
    allPoints,
  ]);

  const handlePlayPause = () => {
    if (playState.progress >= 100) {
      setPlayState((prev) => ({
        ...prev,
        currentTime: timeRange.startTime,
        progress: 0,
        isPlaying: true,
      }));
      return;
    }
    setPlayState((prev) => ({ ...prev, isPlaying: !prev.isPlaying }));
  };

  const handleReset = () => {
    setPlayState({
      isPlaying: false,
      currentTime: timeRange.startTime,
      speed: playState.speed,
      progress: 0,
    });
    updateMarkerPositions(timeRange.startTime);
    if (pixelMode) drawPixelCanvas(timeRange.startTime);
  };

  const handleSpeedChange = (speed: number) => {
    setPlayState((prev) => ({ ...prev, speed }));
  };

  const handleProgressChange = (value: number) => {
    const newTime = timeRange.startTime + (value / 100) * timeRange.duration;
    setPlayState((prev) => ({
      ...prev,
      progress: value,
      currentTime: newTime,
    }));
    updateMarkerPositions(newTime);
    if (pixelMode) drawPixelCanvas(newTime);
  };

  const formatTime = (ms: number) => {
    if (!ms) return '--:--:--';
    const date = new Date(ms);
    return date.toLocaleTimeString('zh-CN', { hour12: false });
  };

  const getTrackColor = (index: number) => TRACK_COLORS[index % TRACK_COLORS.length];

  const canUseGps = hasValidGpsPoints;
  const canUsePixel = hasValidPixelPoints;
  const showModeSwitch = canUseGps && canUsePixel;

  if (!canUseGps && !canUsePixel) {
    return (
      <Card size="small" style={{ height }}>
        <Empty description="暂无轨迹数据" style={{ marginTop: height / 4 }} />
      </Card>
    );
  }

  const renderMainArea = () => {
    if (pixelMode) {
      return (
        <div
          style={{
            width: '100%',
            height,
            borderRadius: 8,
            overflow: 'hidden',
            border: '1px solid #e8e8e8',
            marginBottom: 12,
            background: '#fafafa',
            position: 'relative',
          }}
        >
          <canvas
            ref={canvasRef}
            width={800}
            height={height - 0}
            style={{ width: '100%', height: '100%', display: 'block' }}
          />
          <div
            style={{
              position: 'absolute',
              top: 8,
              right: 8,
              background: 'rgba(255,255,255,0.9)',
              padding: '4px 10px',
              borderRadius: 4,
              fontSize: 12,
              color: '#666',
              display: 'flex',
              alignItems: 'center',
              gap: 4,
            }}
          >
            <VideoCameraOutlined /> 像素轨迹回放模式
          </div>
        </div>
      );
    }

    return (
      <div
        ref={mapRef}
        style={{
          width: '100%',
          height,
          borderRadius: 8,
          overflow: 'hidden',
          border: '1px solid #e8e8e8',
          marginBottom: 12,
        }}
      />
    );
  };

  return (
    <div>
      {showModeSwitch && (
        <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'flex-end' }}>
          <Segmented
            value={pixelMode ? 'pixel' : 'gps'}
            onChange={(v) => {
              setPixelMode(v === 'pixel');
              setPlayState((prev) => ({ ...prev, isPlaying: false }));
            }}
            options={[
              { label: (
                <Space size={4}>
                  <EnvironmentOutlined /> GPS地图
                </Space>
              ), value: 'gps', disabled: !canUseGps },
              { label: (
                <Space size={4}>
                  <VideoCameraOutlined /> 像素坐标
                </Space>
              ), value: 'pixel', disabled: !canUsePixel },
            ]}
          />
        </div>
      )}

      {!canUseGps && pixelMode && hasValidWindow && (
        <Alert
          type="info"
          showIcon
          message="当前为像素轨迹回放"
          description="事件关联轨迹未包含GPS坐标，使用视频像素坐标系进行轨迹回放。回放窗口严格限定为事件前5分钟。"
          style={{ marginBottom: 8 }}
        />
      )}

      {renderMainArea()}

      <Card size="small" style={{ borderRadius: 8 }}>
        <Row gutter={16} align="middle">
          <Col flex="none">
            <Space>
              <Button
                type="primary"
                shape="circle"
                size="large"
                icon={playState.isPlaying ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                onClick={handlePlayPause}
              />
              <Button
                shape="circle"
                size="large"
                icon={<ReloadOutlined />}
                onClick={handleReset}
              />
            </Space>
          </Col>

          <Col flex="auto">
            <Slider
              min={0}
              max={100}
              step={0.1}
              value={playState.progress}
              onChange={handleProgressChange}
              tooltip={{
                formatter: (v) => {
                  const t = timeRange.startTime + (v! / 100) * timeRange.duration;
                  return formatTime(t);
                },
              }}
            />
          </Col>

          <Col flex="none">
            <Space>
              <Select
                value={playState.speed}
                onChange={handleSpeedChange}
                style={{ width: 100 }}
                size="small"
              >
                <Option value={0.5}>0.5x</Option>
                <Option value={1}>1x</Option>
                <Option value={2}>2x</Option>
                <Option value={4}>4x</Option>
                <Option value={8}>8x</Option>
              </Select>
              <FastForwardOutlined style={{ color: '#999' }} />
            </Space>
          </Col>
        </Row>

        <Row gutter={[16, 8]} style={{ marginTop: 8 }}>
          <Col span={8}>
            <Statistic
              title={
                <Space size={4}>
                  <ClockCircleOutlined style={{ color: '#1890ff' }} />
                  当前时间
                </Space>
              }
              value={formatTime(playState.currentTime)}
              valueStyle={{ fontSize: 14, fontWeight: 600 }}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title={
                <Space size={4}>
                  <EnvironmentOutlined style={{ color: '#52c41a' }} />
                  回放窗口
                </Space>
              }
              value={`前 ${beforeMinutes} 分钟`}
              valueStyle={{ fontSize: 14, fontWeight: 600 }}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title={
                <Space size={4}>
                  <CarOutlined style={{ color: '#faad14' }} />
                  目标数量
                </Space>
              }
              value={tracks.length}
              valueStyle={{ fontSize: 14, fontWeight: 600 }}
            />
          </Col>
        </Row>

        {tracks.length > 0 && (
          <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid #f0f0f0' }}>
            <div style={{ fontSize: 12, color: '#999', marginBottom: 8 }}>轨迹列表</div>
            <Space wrap size={[8, 8]}>
              {tracks.map((track, idx) => {
                const point = currentPointInfo[track.id];
                const speed = point?.speed;
                return (
                  <Tag
                    key={track.id}
                    color={getTrackColor(idx)}
                    style={{
                      padding: '4px 12px',
                      borderRadius: 4,
                      cursor: 'pointer',
                      opacity: selectedTrackId && selectedTrackId !== track.id ? 0.5 : 1,
                    }}
                    onClick={() => setSelectedTrackId(selectedTrackId === track.id ? null : track.id)}
                  >
                    <Space size={4}>
                      <CarOutlined />
                      <span style={{ fontFamily: 'monospace', fontSize: 12 }}>
                        {track.trackNo?.slice(-6)}
                      </span>
                      {speed != null && (
                        <Tooltip title="当前速度">
                          <span style={{ fontSize: 11, opacity: 0.8 }}>
                            {Number(speed).toFixed(1)} km/h
                          </span>
                        </Tooltip>
                      )}
                    </Space>
                  </Tag>
                );
              })}
            </Space>
          </div>
        )}
      </Card>

      <style>{`
        @keyframes pulse {
          0% { transform: translateX(-50%) scale(0.5); opacity: 1; }
          100% { transform: translateX(-50%) scale(2); opacity: 0; }
        }
        @keyframes pulse-ring {
          0% { transform: scale(1); opacity: 0.6; }
          100% { transform: scale(1.5); opacity: 0; }
        }
      `}</style>
    </div>
  );
};

export default TrackReplayMap;
