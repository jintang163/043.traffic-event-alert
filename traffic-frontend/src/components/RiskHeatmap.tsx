import React, { useEffect, useRef, useState, useCallback } from 'react';
import L from 'leaflet';
import { Card, Empty, Spin } from 'antd';
import type { EventPrediction } from '@/types';
import { RISK_HEATMAP_COLORS, RISK_LEVEL_COLORS } from '@/types';

interface RiskHeatmapProps {
  predictions: EventPrediction[];
  loading?: boolean;
  height?: number | string;
  onPointClick?: (prediction: EventPrediction) => void;
  center?: [number, number];
  zoom?: number;
}

interface HeatmapPoint {
  lat: number;
  lng: number;
  riskLevel: number;
  riskScore: number;
  radius: number;
}

const RiskHeatmap: React.FC<RiskHeatmapProps> = ({
  predictions,
  loading = false,
  height = 600,
  onPointClick,
  center = [39.9042, 116.4074],
  zoom = 12,
}) => {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const heatmapLayerRef = useRef<L.Layer | null>(null);
  const markerLayerRef = useRef<L.LayerGroup | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [ready, setReady] = useState(false);

  const scoreToRadius = useCallback((score: number) => {
    const minRadius = 30;
    const maxRadius = 120;
    return minRadius + (score / 100) * (maxRadius - minRadius);
  }, []);

  const getColorForScore = useCallback((score: number, alpha: number) => {
    if (score >= 75) return `rgba(255, 77, 79, ${alpha})`;
    if (score >= 50) return `rgba(250, 140, 22, ${alpha})`;
    if (score >= 25) return `rgba(250, 173, 20, ${alpha})`;
    return `rgba(82, 196, 26, ${alpha})`;
  }, []);

  const drawHeatmap = useCallback((points: HeatmapPoint[], map: L.Map) => {
    if (!canvasRef.current) return;

    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const bounds = map.getBounds();
    const topLeft = map.latLngToContainerPoint(bounds.getNorthWest());
    const bottomRight = map.latLngToContainerPoint(bounds.getSouthEast());

    canvas.width = bottomRight.x - topLeft.x;
    canvas.height = bottomRight.y - topLeft.y;
    canvas.style.left = `${topLeft.x}px`;
    canvas.style.top = `${topLeft.y}px`;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const sortedPoints = [...points].sort((a, b) => a.riskScore - b.riskScore);

    sortedPoints.forEach((point) => {
      const containerPoint = map.latLngToContainerPoint([point.lat, point.lng]);
      const x = containerPoint.x - topLeft.x;
      const y = containerPoint.y - topLeft.y;

      const radius = point.radius * (1 / Math.pow(2, map.getZoom() - 12));
      const adjustedRadius = Math.max(15, Math.min(radius, 150));

      const gradient = ctx.createRadialGradient(x, y, 0, x, y, adjustedRadius);
      const baseColor = getColorForScore(point.riskScore, 0.8);
      const edgeColor = getColorForScore(point.riskScore, 0);

      gradient.addColorStop(0, baseColor);
      gradient.addColorStop(0.4, getColorForScore(point.riskScore, 0.5));
      gradient.addColorStop(0.7, getColorForScore(point.riskScore, 0.2));
      gradient.addColorStop(1, edgeColor);

      ctx.beginPath();
      ctx.arc(x, y, adjustedRadius, 0, Math.PI * 2);
      ctx.fillStyle = gradient;
      ctx.fill();
    });
  }, [getColorForScore]);

  const createMarkers = useCallback((predictions: EventPrediction[], map: L.Map) => {
    if (markerLayerRef.current) {
      map.removeLayer(markerLayerRef.current);
    }

    const markerGroup = L.layerGroup();

    predictions.forEach((prediction) => {
      const iconColor = RISK_LEVEL_COLORS[prediction.riskLevel] || '#52c41a';
      const scorePercent = Math.round(prediction.riskScore);

      const icon = L.divIcon({
        className: 'risk-marker',
        html: `
          <div style="
            width: 32px;
            height: 32px;
            border-radius: 50%;
            background: ${iconColor};
            border: 2px solid white;
            box-shadow: 0 2px 8px rgba(0,0,0,0.3);
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            font-weight: 700;
            font-size: 11px;
            transform: translate(-50%, -50%);
          ">
            ${scorePercent}
          </div>
        `,
        iconSize: [0, 0],
        iconAnchor: [0, 0],
      });

      const marker = L.marker([prediction.latitude, prediction.longitude], { icon })
        .bindPopup(`
          <div style="min-width: 200px;">
            <div style="font-weight: 600; margin-bottom: 8px; font-size: 14px;">
              ${prediction.cameraName || prediction.roadName || '未知位置'}
            </div>
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">
              <span style="
                display: inline-block;
                padding: 2px 8px;
                border-radius: 4px;
                background: ${iconColor};
                color: white;
                font-size: 12px;
                font-weight: 500;
              ">
                ${prediction.riskLevelLabel}
              </span>
              <span style="color: #666; font-size: 12px;">
                风险评分: <strong style="color: ${iconColor}">${prediction.riskScore.toFixed(1)}</strong>
              </span>
            </div>
            ${prediction.eventTypeLabel ? `
            <div style="font-size: 12px; color: #666; margin-bottom: 4px;">
              预测事件: <span style="color: #333;">${prediction.eventTypeLabel}</span>
            </div>
            ` : ''}
            ${prediction.probability ? `
            <div style="font-size: 12px; color: #666; margin-bottom: 4px;">
              发生概率: <span style="color: #333;">${(prediction.probability * 100).toFixed(1)}%</span>
            </div>
            ` : ''}
            ${prediction.confidence ? `
            <div style="font-size: 12px; color: #666;">
              预测置信度: <span style="color: #333;">${(prediction.confidence * 100).toFixed(0)}%</span>
            </div>
            ` : ''}
            ${prediction.description ? `
            <div style="font-size: 11px; color: #999; margin-top: 8px; padding-top: 8px; border-top: 1px solid #f0f0f0;">
              ${prediction.description}
            </div>
            ` : ''}
          </div>
        `, { maxWidth: 300 });

      if (onPointClick) {
        marker.on('click', () => onPointClick(prediction));
      }

      marker.addTo(markerGroup);
    });

    markerGroup.addTo(map);
    markerLayerRef.current = markerGroup;
  }, [onPointClick]);

  const updateHeatmap = useCallback(() => {
    if (!mapInstanceRef.current || predictions.length === 0) return;

    const map = mapInstanceRef.current;
    const points: HeatmapPoint[] = predictions.map((p) => ({
      lat: p.latitude,
      lng: p.longitude,
      riskLevel: p.riskLevel,
      riskScore: p.riskScore,
      radius: scoreToRadius(p.riskScore),
    }));

    drawHeatmap(points, map);
    createMarkers(predictions, map);
  }, [predictions, drawHeatmap, createMarkers, scoreToRadius]);

  useEffect(() => {
    if (!mapRef.current || mapInstanceRef.current) return;

    const map = L.map(mapRef.current, {
      center,
      zoom,
      zoomControl: true,
      attributionControl: false,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
    }).addTo(map);

    const canvasPane = map.createPane('heatmap');
    canvasPane.style.zIndex = '450';
    canvasPane.style.pointerEvents = 'none';

    const canvas = document.createElement('canvas');
    canvas.style.position = 'absolute';
    canvasRef.current = canvas;
    canvasPane.appendChild(canvas);

    map.on('moveend zoomend resize', updateHeatmap);

    mapInstanceRef.current = map;
    setReady(true);

    return () => {
      if (canvas && canvas.parentNode) {
        canvas.parentNode.removeChild(canvas);
      }
      map.remove();
      mapInstanceRef.current = null;
    };
  }, [center, zoom, updateHeatmap]);

  useEffect(() => {
    if (ready && predictions.length > 0 && mapInstanceRef.current) {
      updateHeatmap();

      const bounds = L.latLngBounds(
        predictions.map((p) => [p.latitude, p.longitude])
      );
      mapInstanceRef.current.fitBounds(bounds, { padding: [50, 50] });
    }
  }, [ready, predictions, updateHeatmap]);

  if (loading) {
    return (
      <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" tip="加载预测数据中..." />
      </div>
    );
  }

  if (predictions.length === 0) {
    return (
      <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Empty description="暂无预测数据" />
      </div>
    );
  }

  return (
    <div ref={mapRef} style={{ width: '100%', height, borderRadius: 8, overflow: 'hidden' }} />
  );
};

export default RiskHeatmap;
