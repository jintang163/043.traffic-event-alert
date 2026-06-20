import React, { useEffect, useRef, useState } from 'react';
import { Card, Space, Button, Tag, Statistic, Row, Col, Switch, Tooltip, Empty, Alert } from 'antd';
import {
  SafetyOutlined, AlertOutlined, CarOutlined,
  EyeOutlined, EyeInvisibleOutlined,
} from '@ant-design/icons';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { constructionApi } from '@/services/api';
import type { Camera } from '@/types';

interface ConstructionPlan {
  id: number;
  planCode: string;
  planName: string;
  constructionType: number;
  constructionTypeLabel: string;
  cameraId: number;
  cameraName: string;
  fenceId: number;
  bufferFenceId: number;
  speedLimit: number;
  standardConeCount: number;
  bufferDistance: number;
  polygonPoints?: string;
  polygonPointsPixel?: string;
  planStatus: number;
  planStatusLabel: string;
  roadName?: string;
  location?: string;
}

interface ConePosition {
  x: number;
  y: number;
  confidence: number;
  lng?: number;
  lat?: number;
}

interface ConstructionMapLayerProps {
  plan: ConstructionPlan;
  camera?: Camera;
  height?: number;
}

const ConstructionMapLayer: React.FC<ConstructionMapLayerProps> = ({
  plan,
  camera,
  height = 500,
}) => {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const zonePolygonRef = useRef<L.Polygon | null>(null);
  const bufferPolygonRef = useRef<L.Polygon | null>(null);
  const coneMarkersRef = useRef<L.CircleMarker[]>([]);
  const speedLimitMarkerRef = useRef<L.Marker | null>(null);

  const [showZone, setShowZone] = useState(true);
  const [showBuffer, setShowBuffer] = useState(true);
  const [showCones, setShowCones] = useState(true);
  const [showSpeedLimit, setShowSpeedLimit] = useState(true);
  const [conePositions, setConePositions] = useState<ConePosition[]>([]);
  const [latestConeRecord, setLatestConeRecord] = useState<any>(null);
  const [hasGeoData, setHasGeoData] = useState(false);

  const getCenterLocation = (): [number, number] | null => {
    if (camera?.longitude && camera?.latitude) {
      return [camera.latitude, camera.longitude];
    }
    if (plan.polygonPoints) {
      try {
        const points: number[][] = JSON.parse(plan.polygonPoints);
        if (points.length > 0) {
          const lats = points.map(p => p[1]);
          const lngs = points.map(p => p[0]);
          return [
            (Math.min(...lats) + Math.max(...lats)) / 2,
            (Math.min(...lngs) + Math.max(...lngs)) / 2,
          ];
        }
      } catch (e) {
        console.error('解析施工区坐标失败', e);
      }
    }
    return null;
  };

  const parsePolygonPoints = (): L.LatLngTuple[] | null => {
    if (!plan.polygonPoints) return null;
    try {
      const points: number[][] = JSON.parse(plan.polygonPoints);
      return points.map(p => [p[1], p[0]] as L.LatLngTuple);
    } catch (e) {
      console.error('解析施工区坐标失败', e);
      return null;
    }
  };

  const calculateBufferZone = (zonePoints: L.LatLngTuple[], distanceMeters: number): L.LatLngTuple[] => {
    if (zonePoints.length < 3) return zonePoints;
    const center = getCenterLocation();
    if (!center) return zonePoints;

    const factor = distanceMeters / 111000;
    return zonePoints.map(([lat, lng]) => {
      const dx = lng - center[1];
      const dy = lat - center[0];
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist === 0) return [lat, lng];
      const newDist = dist + factor;
      return [
        center[0] + (dy / dist) * newDist,
        center[1] + (dx / dist) * newDist,
      ] as L.LatLngTuple;
    });
  };

  useEffect(() => {
    if (!mapRef.current) return;

    const center = getCenterLocation();
    if (!center) {
      setHasGeoData(false);
      return;
    }

    setHasGeoData(true);

    if (mapInstanceRef.current) {
      mapInstanceRef.current.remove();
    }

    const map = L.map(mapRef.current, {
      center: center,
      zoom: 16,
      zoomControl: true,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map);

    mapInstanceRef.current = map;

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, [plan.id, camera?.id]);

  useEffect(() => {
    if (!mapInstanceRef.current || !hasGeoData) return;

    if (zonePolygonRef.current) {
      zonePolygonRef.current.remove();
      zonePolygonRef.current = null;
    }
    if (bufferPolygonRef.current) {
      bufferPolygonRef.current.remove();
      bufferPolygonRef.current = null;
    }

    const zonePoints = parsePolygonPoints();
    if (!zonePoints || zonePoints.length < 3) return;

    if (showZone) {
      zonePolygonRef.current = L.polygon(zonePoints, {
        color: '#faad14',
        weight: 3,
        opacity: 0.8,
        fillColor: '#faad14',
        fillOpacity: 0.2,
      }).addTo(mapInstanceRef.current);

      zonePolygonRef.current.bindPopup(`
        <div style="min-width: 160px;">
          <div style="font-weight: 600; margin-bottom: 4px; color: #faad14;">
            施工区
          </div>
          <div style="font-size: 12px; color: #666; margin-bottom: 4px;">
            ${plan.planName || '-'}
          </div>
          <div style="font-size: 12px; color: #666;">
            限速: ${plan.speedLimit || '-'} km/h
          </div>
        </div>
      `);
    }

    if (showBuffer && plan.bufferDistance && plan.bufferDistance > 0) {
      const bufferPoints = calculateBufferZone(zonePoints, plan.bufferDistance);
      bufferPolygonRef.current = L.polygon(bufferPoints, {
        color: '#ff4d4f',
        weight: 2,
        opacity: 0.6,
        fillColor: '#ff4d4f',
        fillOpacity: 0.1,
        dashArray: '10, 10',
      }).addTo(mapInstanceRef.current);

      bufferPolygonRef.current.bindPopup(`
        <div style="min-width: 160px;">
          <div style="font-weight: 600; margin-bottom: 4px; color: #ff4d4f;">
            缓冲区
          </div>
          <div style="font-size: 12px; color: #666;">
            距离: ${plan.bufferDistance} 米
          </div>
        </div>
      `);
    }

    if (showSpeedLimit && plan.speedLimit) {
      if (speedLimitMarkerRef.current) {
        speedLimitMarkerRef.current.remove();
      }

      const speedLimitIcon = L.divIcon({
        className: 'speed-limit-marker',
        html: `
          <div style="
            position: relative;
            width: 48px;
            height: 48px;
            transform: translate(-50%, -50%);
          ">
            <div style="
              width: 48px;
              height: 48px;
              background: #fff;
              border: 3px solid #ff4d4f;
              border-radius: 50%;
              display: flex;
              align-items: center;
              justify-content: center;
              font-weight: bold;
              font-size: 14px;
              color: #ff4d4f;
              box-shadow: 0 2px 8px rgba(0,0,0,0.2);
            ">
              ${plan.speedLimit}
            </div>
            <div style="
              position: absolute;
              bottom: -6px;
              left: 50%;
              transform: translateX(-50%);
              font-size: 10px;
              color: #666;
              background: #fff;
              padding: 0 4px;
              white-space: nowrap;
            ">
              km/h
            </div>
          </div>
        `,
        iconSize: [48, 48],
        iconAnchor: [24, 24],
      });

      const center = getCenterLocation();
      if (center) {
        speedLimitMarkerRef.current = L.marker(center, {
          icon: speedLimitIcon,
          interactive: false,
        }).addTo(mapInstanceRef.current);
      }
    }

    const allPolygons = [zonePolygonRef.current, bufferPolygonRef.current].filter(Boolean) as L.Polygon[];
    if (allPolygons.length > 0) {
      const bounds = L.latLngBounds([]);
      allPolygons.forEach(p => {
        bounds.extend(p.getBounds());
      });
      mapInstanceRef.current.fitBounds(bounds, { padding: [50, 50] });
    }
  }, [hasGeoData, showZone, showBuffer, showCones, showSpeedLimit, plan.id, plan.speedLimit, plan.bufferDistance, plan.planName]);

  useEffect(() => {
    if (!mapInstanceRef.current || !showCones) return;

    coneMarkersRef.current.forEach(marker => marker.remove());
    coneMarkersRef.current = [];

    conePositions.forEach((cone, idx) => {
      let latLng: L.LatLngTuple | null = null;

      if (cone.lng != null && cone.lat != null) {
        latLng = [cone.lat, cone.lng];
      } else if (plan.polygonPoints && camera?.longitude && camera?.latitude) {
        try {
          const points: number[][] = JSON.parse(plan.polygonPoints);
          if (points.length >= 3) {
            const center = getCenterLocation();
            if (center) {
              latLng = [
                center[0] + (cone.y - 0.5) * 0.002,
                center[1] + (cone.x - 0.5) * 0.002,
              ];
            }
          }
        } catch (e) {
          console.error('计算锥桶位置失败', e);
        }
      }

      if (latLng) {
        const color = cone.confidence > 0.7 ? '#52c41a' : cone.confidence > 0.5 ? '#faad14' : '#ff4d4f';
        const marker = L.circleMarker(latLng, {
          radius: 6,
          fillColor: color,
          color: '#fff',
          weight: 2,
          opacity: 1,
          fillOpacity: 0.8,
        }).addTo(mapInstanceRef.current!);

        marker.bindPopup(`
          <div style="min-width: 120px;">
            <div style="font-weight: 600; margin-bottom: 4px;">
              锥桶 #${idx + 1}
            </div>
            <div style="font-size: 12px; color: #666;">
              置信度: ${(cone.confidence * 100).toFixed(1)}%
            </div>
          </div>
        `);

        coneMarkersRef.current.push(marker);
      }
    });
  }, [conePositions, showCones, hasGeoData]);

  useEffect(() => {
    loadLatestConeData();
  }, [plan.id]);

  const loadLatestConeData = async () => {
    try {
      const res: any = await constructionApi.getLatestConeRecord(plan.id);
      if (res.code === 200 && res.data) {
        setLatestConeRecord(res.data);
        if (res.data.conePositions) {
          try {
            const positions: ConePosition[] = JSON.parse(res.data.conePositions);
            setConePositions(positions);
          } catch (e) {
            console.error('解析锥桶位置失败', e);
          }
        }
      }
    } catch (e) {
      console.error('加载锥桶数据失败', e);
    }
  };

  if (!hasGeoData) {
    return (
      <Card size="small">
        <Empty
          description="暂无地理坐标数据"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        >
          <Alert
            type="warning"
            showIcon
            message="需要配置摄像头经纬度或施工区经纬度坐标才能在地图上显示"
            style={{ marginTop: 16 }}
          />
        </Empty>
      </Card>
    );
  }

  const missingConeCount = latestConeRecord
    ? Math.max(0, (plan.standardConeCount || 0) - (latestConeRecord.detectedConeCount || 0))
    : 0;

  const complianceRate = latestConeRecord && plan.standardConeCount > 0
    ? Math.min(100, (latestConeRecord.detectedConeCount / plan.standardConeCount) * 100)
    : 100;

  return (
    <div>
      <Card size="small" style={{ marginBottom: 8, borderRadius: '8px 8px 0 0' }}>
        <Row gutter={16} align="middle">
          <Col span={6}>
            <Statistic
              title="检测锥桶数"
              value={latestConeRecord?.detectedConeCount || 0}
              prefix={<SafetyOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title="缺失锥桶数"
              value={missingConeCount}
              prefix={<AlertOutlined />}
              valueStyle={{ color: missingConeCount > 0 ? '#ff4d4f' : '#52c41a' }}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title="合规率"
              value={complianceRate}
              suffix="%"
              valueStyle={{ color: complianceRate >= 80 ? '#52c41a' : complianceRate >= 50 ? '#faad14' : '#ff4d4f' }}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title="车辆闯入"
              value={latestConeRecord?.vehicleIntrusionCount || 0}
              prefix={<CarOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Col>
        </Row>
      </Card>

      <Card
        size="small"
        style={{ borderRadius: '0 0 8px 8px', borderTop: 'none' }}
        extra={
          <Space size="middle">
            <Space size="small">
              <Tooltip title="显示施工区">
                <Switch
                  checked={showZone}
                  onChange={setShowZone}
                  size="small"
                  checkedChildren={<EyeOutlined />}
                  unCheckedChildren={<EyeInvisibleOutlined />}
                />
              </Tooltip>
              <Tag color="#faad14" style={{ margin: 0 }}>施工区</Tag>
            </Space>
            <Space size="small">
              <Tooltip title="显示缓冲区">
                <Switch
                  checked={showBuffer}
                  onChange={setShowBuffer}
                  size="small"
                  checkedChildren={<EyeOutlined />}
                  unCheckedChildren={<EyeInvisibleOutlined />}
                />
              </Tooltip>
              <Tag color="#ff4d4f" style={{ margin: 0 }}>缓冲区</Tag>
            </Space>
            <Space size="small">
              <Tooltip title="显示锥桶">
                <Switch
                  checked={showCones}
                  onChange={setShowCones}
                  size="small"
                  checkedChildren={<EyeOutlined />}
                  unCheckedChildren={<EyeInvisibleOutlined />}
                />
              </Tooltip>
              <Tag color="#52c41a" style={{ margin: 0 }}>锥桶</Tag>
            </Space>
            <Space size="small">
              <Tooltip title="显示限速">
                <Switch
                  checked={showSpeedLimit}
                  onChange={setShowSpeedLimit}
                  size="small"
                  checkedChildren={<EyeOutlined />}
                  unCheckedChildren={<EyeInvisibleOutlined />}
                />
              </Tooltip>
              <Tag color="#1890ff" style={{ margin: 0 }}>限速</Tag>
            </Space>
            <Button size="small" onClick={loadLatestConeData}>刷新数据</Button>
          </Space>
        }
      >
        <div
          ref={mapRef}
          style={{
            width: '100%',
            height: `${height}px`,
            borderRadius: 8,
            border: '1px solid #f0f0f0',
          }}
        />
        {latestConeRecord?.detectionTime && (
          <div style={{ marginTop: 8, textAlign: 'right', color: '#8c8c8c', fontSize: 12 }}>
            检测时间: {latestConeRecord.detectionTime}
          </div>
        )}
      </Card>
    </div>
  );
};

export default ConstructionMapLayer;
