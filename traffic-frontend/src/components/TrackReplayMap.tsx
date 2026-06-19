import React, { useEffect, useRef, useState, useCallback } from 'react';
import { Card, Button, Space, Slider, Select, Tag, Tooltip, Statistic, Row, Col, Empty } from 'antd';
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  ReloadOutlined,
  FastForwardOutlined,
  EnvironmentOutlined,
  WarningOutlined,
  CarOutlined,
  ClockCircleOutlined,
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
  height?: number;
}

interface PlayState {
  isPlaying: boolean;
  currentTime: number;
  speed: number;
  progress: number;
}

const TRACK_COLORS = ['#1890ff', '#52c41a', '#faad14', '#eb2f96', '#722ed1', '#13c2c2'];

const TrackReplayMap: React.FC<TrackReplayMapProps> = ({
  event,
  tracks,
  trackPointsMap,
  beforeMinutes = 5,
  height = 480,
}) => {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const polylineRefs = useRef<Record<number, L.Polyline>>({});
  const markerRefs = useRef<Record<number, L.Marker>>({});
  const eventMarkerRef = useRef<L.Marker | null>(null);
  const animationRef = useRef<number | null>(null);
  const lastTimeRef = useRef<number>(0);

  const [playState, setPlayState] = useState<PlayState>({
    isPlaying: false,
    currentTime: 0,
    speed: 1,
    progress: 0,
  });

  const [selectedTrackId, setSelectedTrackId] = useState<number | null>(null);
  const [currentPointInfo, setCurrentPointInfo] = useState<Record<number, TrackPoint>>({});

  const allPoints = Object.values(trackPointsMap).flat();
  const hasValidPoints = allPoints.some((p) => p.longitude != null && p.latitude != null);

  const getTimeRange = useCallback(() => {
    if (allPoints.length === 0) return { startTime: 0, endTime: 0, duration: 0 };

    const times = allPoints
      .filter((p) => p.frameTime)
      .map((p) => new Date(p.frameTime).getTime());

    if (times.length === 0) return { startTime: 0, endTime: 0, duration: 0 };

    const startTime = Math.min(...times);
    const endTime = Math.max(...times);

    return {
      startTime,
      endTime,
      duration: endTime - startTime,
    };
  }, [allPoints]);

  const timeRange = getTimeRange();

  const getPointAtTime = useCallback(
    (points: TrackPoint[], targetTime: number): TrackPoint | null => {
      if (points.length === 0) return null;

      const validPoints = points.filter(
        (p) => p.frameTime && p.longitude != null && p.latitude != null
      );
      if (validPoints.length === 0) return null;

      for (let i = 0; i < validPoints.length; i++) {
        const pointTime = new Date(validPoints[i].frameTime).getTime();
        if (pointTime >= targetTime) {
          if (i === 0) return validPoints[0];

          const prevPoint = validPoints[i - 1];
          const prevTime = new Date(prevPoint.frameTime).getTime();
          const currTime = pointTime;
          const ratio = (targetTime - prevTime) / (currTime - prevTime);

          return {
            ...prevPoint,
            longitude: prevPoint.longitude! + (validPoints[i].longitude! - prevPoint.longitude!) * ratio,
            latitude: prevPoint.latitude! + (validPoints[i].latitude! - prevPoint.latitude!) * ratio,
          } as TrackPoint;
        }
      }

      return validPoints[validPoints.length - 1];
    },
    []
  );

  const initMap = useCallback(() => {
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

    const validPoints = allPoints.filter(
      (p) => p.longitude != null && p.latitude != null
    );
    if (validPoints.length === 0) return;

    const bounds = L.latLngBounds(
      validPoints.map((p) => [p.latitude!, p.longitude!])
    );

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
          <WarningOutlined /> 事件发生点
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
      const validPoints = points.filter(
        (p) => p.longitude != null && p.latitude != null
      );
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
      const firstPoint = points.find(
        (p) => p.longitude != null && p.latitude != null
      );
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

      tracks.forEach((track) => {
        const points = trackPointsMap[track.id] || [];
        const marker = markerRefs.current[track.id];
        if (!marker || points.length === 0) return;

        const point = getPointAtTime(points, currentTimeMs);
        if (point && point.longitude != null && point.latitude != null) {
          marker.setLatLng([point.latitude, point.longitude]);
          newPointInfo[track.id] = point;
        }
      });

      setCurrentPointInfo(newPointInfo);
    },
    [tracks, trackPointsMap, getPointAtTime]
  );

  const animate = useCallback(
    (timestamp: number) => {
      if (!playState.isPlaying) return;

      if (lastTimeRef.current === 0) {
        lastTimeRef.current = timestamp;
      }

      const delta = timestamp - lastTimeRef.current;
      lastTimeRef.current = timestamp;

      const speedMultiplier = playState.speed * 1000;
      const newTime = playState.currentTime + delta * speedMultiplier;

      if (newTime >= timeRange.endTime) {
        setPlayState((prev) => ({
          ...prev,
          isPlaying: false,
          currentTime: timeRange.endTime,
          progress: 100,
        }));
        updateMarkerPositions(timeRange.endTime);
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

      animationRef.current = requestAnimationFrame(animate);
    },
    [playState.isPlaying, playState.currentTime, playState.speed, timeRange, updateMarkerPositions]
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
    initMap();
  }, [initMap]);

  useEffect(() => {
    if (mapInstanceRef.current) {
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
  };

  const formatTime = (ms: number) => {
    if (!ms) return '--:--:--';
    const date = new Date(ms);
    return date.toLocaleTimeString('zh-CN', { hour12: false });
  };

  const getTrackColor = (index: number) => TRACK_COLORS[index % TRACK_COLORS.length];

  if (!hasValidPoints) {
    return (
      <Card size="small" style={{ height }}>
        <Empty
          description="暂无GPS轨迹数据"
          style={{ marginTop: height / 4 }}
        />
      </Card>
    );
  }

  return (
    <div>
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
                  回放时长
                </Space>
              }
              value={`${beforeMinutes} 分钟`}
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
                            {speed.toFixed(1)} km/h
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
