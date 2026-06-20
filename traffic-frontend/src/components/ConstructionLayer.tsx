import React, { useEffect, useRef, useState } from 'react';
import { Card, Space, Button, Tag, Statistic, Row, Col, Switch, Tooltip, message, Divider } from 'antd';
import {
  SafetyOutlined, AlertOutlined, CarOutlined,
  EyeOutlined, EyeInvisibleOutlined,
} from '@ant-design/icons';
import { constructionApi } from '@/services/api';

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
  polygonPoints: string;
  polygonPointsPixel: string;
  planStatus: number;
  planStatusLabel: string;
}

interface ConePosition {
  x: number;
  y: number;
  confidence: number;
  lng?: number;
  lat?: number;
}

interface ConstructionLayerProps {
  plan: ConstructionPlan;
  width?: number;
  height?: number;
}

const ConstructionLayer: React.FC<ConstructionLayerProps> = ({
  plan,
  width = 800,
  height = 450,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [showZone, setShowZone] = useState(true);
  const [showBuffer, setShowBuffer] = useState(true);
  const [showCones, setShowCones] = useState(true);
  const [showSpeedLimit, setShowSpeedLimit] = useState(true);
  const [conePositions, setConePositions] = useState<ConePosition[]>([]);
  const [latestConeRecord, setLatestConeRecord] = useState<any>(null);
  const [isDrawing, setIsDrawing] = useState(false);
  const [zonePoints, setZonePoints] = useState<{ x: number; y: number }[]>([]);

  const CANVAS_W = width;
  const CANVAS_H = height;

  useEffect(() => {
    loadLatestConeData();
    loadZonePoints();
  }, [plan.id]);

  useEffect(() => {
    drawLayer();
  }, [conePositions, zonePoints, showZone, showBuffer, showCones, showSpeedLimit]);

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

  const loadZonePoints = () => {
    if (plan.polygonPointsPixel) {
      try {
        const points: number[][] = JSON.parse(plan.polygonPointsPixel);
        const formatted = points.map((p) => ({ x: p[0] * CANVAS_W, y: p[1] * CANVAS_H }));
        setZonePoints(formatted);
      } catch (e) {
        console.error('解析施工区坐标失败', e);
        generateDefaultZone();
      }
    } else {
      generateDefaultZone();
    }
  };

  const generateDefaultZone = () => {
    const defaultPoints = [
      { x: CANVAS_W * 0.15, y: CANVAS_H * 0.2 },
      { x: CANVAS_W * 0.85, y: CANVAS_H * 0.2 },
      { x: CANVAS_W * 0.85, y: CANVAS_H * 0.8 },
      { x: CANVAS_W * 0.15, y: CANVAS_H * 0.8 },
    ];
    setZonePoints(defaultPoints);
  };

  const drawLayer = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);

    drawRoadBackground(ctx);

    if (showBuffer && zonePoints.length >= 3) {
      drawBufferZone(ctx);
    }

    if (showZone && zonePoints.length >= 3) {
      drawConstructionZone(ctx);
    }

    if (showCones && conePositions.length > 0) {
      drawCones(ctx);
    }

    if (showSpeedLimit) {
      drawSpeedLimitSign(ctx);
    }

    drawLegend(ctx);
  };

  const drawRoadBackground = (ctx: CanvasRenderingContext2D) => {
    ctx.fillStyle = '#1e3a5f';
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    ctx.fillStyle = '#2d5a87';
    ctx.fillRect(CANVAS_W * 0.1, CANVAS_H * 0.15, CANVAS_W * 0.8, CANVAS_H * 0.7);

    ctx.strokeStyle = '#ffd700';
    ctx.lineWidth = 2;
    ctx.setLineDash([20, 15]);
    ctx.beginPath();
    ctx.moveTo(CANVAS_W * 0.1, CANVAS_H * 0.5);
    ctx.lineTo(CANVAS_W * 0.9, CANVAS_H * 0.5);
    ctx.stroke();
    ctx.setLineDash([]);

    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(CANVAS_W * 0.1, CANVAS_H * 0.15);
    ctx.lineTo(CANVAS_W * 0.9, CANVAS_H * 0.15);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(CANVAS_W * 0.1, CANVAS_H * 0.85);
    ctx.lineTo(CANVAS_W * 0.9, CANVAS_H * 0.85);
    ctx.stroke();
  };

  const drawConstructionZone = (ctx: CanvasRenderingContext2D) => {
    ctx.save();

    ctx.beginPath();
    ctx.moveTo(zonePoints[0].x, zonePoints[0].y);
    for (let i = 1; i < zonePoints.length; i++) {
      ctx.lineTo(zonePoints[i].x, zonePoints[i].y);
    }
    ctx.closePath();

    ctx.globalAlpha = 0.3;
    ctx.fillStyle = '#faad14';
    ctx.fill();
    ctx.globalAlpha = 1;

    ctx.strokeStyle = '#faad14';
    ctx.lineWidth = 3;
    ctx.setLineDash([10, 5]);
    ctx.stroke();
    ctx.setLineDash([]);

    for (let i = 0; i < zonePoints.length; i++) {
      const p = zonePoints[i];
      ctx.beginPath();
      ctx.arc(p.x, p.y, 6, 0, Math.PI * 2);
      ctx.fillStyle = '#fff';
      ctx.fill();
      ctx.strokeStyle = '#faad14';
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    const centerX = zonePoints.reduce((sum, p) => sum + p.x, 0) / zonePoints.length;
    const centerY = zonePoints.reduce((sum, p) => sum + p.y, 0) / zonePoints.length;

    ctx.fillStyle = '#faad14';
    ctx.fillRect(centerX - 60, centerY - 50, 120, 30);
    ctx.fillStyle = '#fff';
    ctx.font = 'bold 14px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('施工区域', centerX, centerY - 30);

    ctx.restore();
  };

  const drawBufferZone = (ctx: CanvasRenderingContext2D) => {
    if (zonePoints.length < 3) return;

    ctx.save();

    const bufferAmount = 25;
    const expandedPoints = expandPolygon(zonePoints, bufferAmount);

    ctx.beginPath();
    ctx.moveTo(expandedPoints[0].x, expandedPoints[0].y);
    for (let i = 1; i < expandedPoints.length; i++) {
      ctx.lineTo(expandedPoints[i].x, expandedPoints[i].y);
    }
    ctx.closePath();

    ctx.globalAlpha = 0.15;
    ctx.fillStyle = '#ff4d4f';
    ctx.fill();
    ctx.globalAlpha = 1;

    ctx.strokeStyle = '#ff4d4f';
    ctx.lineWidth = 2;
    ctx.setLineDash([5, 5]);
    ctx.stroke();
    ctx.setLineDash([]);

    const topCenterX = (expandedPoints[0].x + expandedPoints[1]?.x) / 2 || expandedPoints[0].x;
    const topCenterY = Math.min(...expandedPoints.map((p) => p.y)) - 10;

    ctx.fillStyle = '#ff4d4f';
    ctx.font = '12px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(`缓冲区 (${plan.bufferDistance || 50}m)`, topCenterX, topCenterY);

    ctx.restore();
  };

  const expandPolygon = (points: { x: number; y: number }[], amount: number) => {
    const expanded: { x: number; y: number }[] = [];
    const cx = points.reduce((sum, p) => sum + p.x, 0) / points.length;
    const cy = points.reduce((sum, p) => sum + p.y, 0) / points.length;

    for (const p of points) {
      const dx = p.x - cx;
      const dy = p.y - cy;
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist > 0) {
        const scale = (dist + amount) / dist;
        expanded.push({
          x: cx + dx * scale,
          y: cy + dy * scale,
        });
      } else {
        expanded.push({ ...p });
      }
    }

    return expanded;
  };

  const drawCones = (ctx: CanvasRenderingContext2D) => {
    ctx.save();

    for (const cone of conePositions) {
      const x = cone.x * CANVAS_W;
      const y = cone.y * CANVAS_H;

      ctx.beginPath();
      ctx.moveTo(x, y - 15);
      ctx.lineTo(x - 10, y + 10);
      ctx.lineTo(x + 10, y + 10);
      ctx.closePath();

      const gradient = ctx.createLinearGradient(x - 10, y - 15, x + 10, y + 10);
      gradient.addColorStop(0, '#ff6b00');
      gradient.addColorStop(0.5, '#ff8c00');
      gradient.addColorStop(1, '#ff4500');
      ctx.fillStyle = gradient;
      ctx.fill();

      ctx.fillStyle = '#fff';
      ctx.fillRect(x - 9, y - 5, 18, 3);
      ctx.fillRect(x - 8, y + 3, 16, 3);

      ctx.beginPath();
      ctx.ellipse(x, y + 10, 10, 3, 0, 0, Math.PI * 2);
      ctx.fillStyle = '#333';
      ctx.fill();

      if (cone.confidence && cone.confidence < 0.7) {
        ctx.fillStyle = 'rgba(0,0,0,0.6)';
        ctx.font = '10px monospace';
        ctx.textAlign = 'center';
        ctx.fillText(`${(cone.confidence * 100).toFixed(0)}%`, x, y - 18);
      }
    }

    ctx.restore();
  };

  const drawSpeedLimitSign = (ctx: CanvasRenderingContext2D) => {
    const signX = CANVAS_W - 80;
    const signY = 60;
    const radius = 35;

    ctx.save();

    ctx.beginPath();
    ctx.arc(signX, signY, radius, 0, Math.PI * 2);
    ctx.fillStyle = '#fff';
    ctx.fill();
    ctx.strokeStyle = '#ff0000';
    ctx.lineWidth = 5;
    ctx.stroke();

    ctx.fillStyle = '#000';
    ctx.font = 'bold 22px Arial';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(`${plan.speedLimit || 60}`, signX, signY - 3);

    ctx.font = '10px Arial';
    ctx.fillText('km/h', signX, signY + 12);

    ctx.restore();
  };

  const drawLegend = (ctx: CanvasRenderingContext2D) => {
    const legendX = 15;
    const legendY = 15;

    ctx.save();

    ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
    ctx.fillRect(legendX, legendY, 160, 90);
    ctx.strokeStyle = '#d9d9d9';
    ctx.lineWidth = 1;
    ctx.strokeRect(legendX, legendY, 160, 90);

    ctx.fillStyle = '#000';
    ctx.font = 'bold 12px sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText('图例', legendX + 10, legendY + 20);

    ctx.fillStyle = '#faad14';
    ctx.fillRect(legendX + 10, legendY + 32, 20, 12);
    ctx.fillStyle = '#000';
    ctx.font = '11px sans-serif';
    ctx.fillText('施工区域', legendX + 40, legendY + 42);

    ctx.fillStyle = 'rgba(255, 77, 79, 0.5)';
    ctx.fillRect(legendX + 10, legendY + 50, 20, 12);
    ctx.strokeStyle = '#ff4d4f';
    ctx.lineWidth = 1;
    ctx.setLineDash([3, 2]);
    ctx.strokeRect(legendX + 10, legendY + 50, 20, 12);
    ctx.setLineDash([]);
    ctx.fillStyle = '#000';
    ctx.fillText('缓冲区', legendX + 40, legendY + 60);

    ctx.fillStyle = '#ff8c00';
    ctx.beginPath();
    ctx.moveTo(legendX + 20, legendY + 72);
    ctx.lineTo(legendX + 13, legendY + 85);
    ctx.lineTo(legendX + 27, legendY + 85);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = '#000';
    ctx.fillText('交通锥', legendX + 40, legendY + 82);

    ctx.restore();
  };

  const handleCanvasClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!isDrawing) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const nx = Math.max(0, Math.min(1, x / rect.width));
    const ny = Math.max(0, Math.min(1, y / rect.height));

    const newPoints = [...zonePoints, { x: nx * CANVAS_W, y: ny * CANVAS_H }];
    setZonePoints(newPoints);
  };

  const startDrawing = () => {
    setIsDrawing(true);
    setZonePoints([]);
    message.info('在画布上点击添加施工区顶点');
  };

  const finishDrawing = () => {
    setIsDrawing(false);
    if (zonePoints.length >= 3) {
      const pixelJson = JSON.stringify(zonePoints.map((p) => [p.x / CANVAS_W, p.y / CANVAS_H]));
      message.success(`已绘制 ${zonePoints.length} 个顶点的施工区`);
    } else {
      message.warning('请至少绘制 3 个顶点');
    }
  };

  const clearDrawing = () => {
    setZonePoints([]);
    setConePositions([]);
  };

  return (
    <div>
      <Card
        size="small"
        style={{ marginBottom: 12 }}
        bodyStyle={{ padding: '12px' }}
      >
        <Space wrap>
          <Space>
            <SafetyOutlined style={{ color: '#faad14' }} />
            <strong>{plan.planName}</strong>
            <Tag color="blue">{plan.planCode}</Tag>
            <Tag color={plan.planStatus === 2 ? 'green' : 'default'}>
              {plan.planStatusLabel}
            </Tag>
          </Space>

          <Divider type="vertical" />

          <Space size="small">
            <Tooltip title="显示施工区">
              <Switch
                size="small"
                checked={showZone}
                onChange={setShowZone}
                checkedChildren={<EyeOutlined />}
                unCheckedChildren={<EyeInvisibleOutlined />}
              />
              <span style={{ fontSize: 12 }}>施工区</span>
            </Tooltip>

            <Tooltip title="显示缓冲区">
              <Switch
                size="small"
                checked={showBuffer}
                onChange={setShowBuffer}
                checkedChildren={<EyeOutlined />}
                unCheckedChildren={<EyeInvisibleOutlined />}
              />
              <span style={{ fontSize: 12 }}>缓冲区</span>
            </Tooltip>

            <Tooltip title="显示锥桶">
              <Switch
                size="small"
                checked={showCones}
                onChange={setShowCones}
                checkedChildren={<EyeOutlined />}
                unCheckedChildren={<EyeInvisibleOutlined />}
              />
              <span style={{ fontSize: 12 }}>锥桶</span>
            </Tooltip>

            <Tooltip title="显示限速标志">
              <Switch
                size="small"
                checked={showSpeedLimit}
                onChange={setShowSpeedLimit}
                checkedChildren={<EyeOutlined />}
                unCheckedChildren={<EyeInvisibleOutlined />}
              />
              <span style={{ fontSize: 12 }}>限速</span>
            </Tooltip>
          </Space>

          <Divider type="vertical" />

          <Space size="small">
            {isDrawing ? (
              <Button size="small" type="primary" onClick={finishDrawing}>
                完成绘制
              </Button>
            ) : (
              <Button size="small" onClick={startDrawing}>
                绘制施工区
              </Button>
            )}
            <Button size="small" danger onClick={clearDrawing}>
              清除
            </Button>
          </Space>
        </Space>
      </Card>

      <canvas
        ref={canvasRef}
        width={CANVAS_W}
        height={CANVAS_H}
        onClick={handleCanvasClick}
        style={{
          width: '100%',
          height: 'auto',
          borderRadius: 8,
          border: '1px solid #d9d9d9',
          cursor: isDrawing ? 'crosshair' : 'default',
          display: 'block',
        }}
      />

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={8}>
          <Card size="small">
            <Statistic
              title="锥桶检测"
              value={latestConeRecord?.detectedConeCount || 0}
              suffix={`/ ${plan.standardConeCount}`}
              prefix={<AlertOutlined />}
              valueStyle={{
                color: latestConeRecord?.isCompliant === 0 ? '#ff4d4f' : '#52c41a',
              }}
            />
            <div style={{ marginTop: 8 }}>
              <Tag color={latestConeRecord?.isCompliant === 1 ? 'green' : 'red'}>
                {latestConeRecord?.isCompliant === 1 ? '摆放合规' : '摆放不合规'}
              </Tag>
              {latestConeRecord && (
                <span style={{ fontSize: 12, color: '#666' }}>
                  合规率: {latestConeRecord.complianceRate}%
                </span>
              )}
            </div>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small">
            <Statistic
              title="限速"
              value={plan.speedLimit || 60}
              suffix="km/h"
              prefix={<CarOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
            <div style={{ marginTop: 8, fontSize: 12, color: '#666' }}>
              施工区域车辆限速
            </div>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small">
            <Statistic
              title="缓冲区"
              value={plan.bufferDistance || 50}
              suffix="米"
              prefix={<SafetyOutlined />}
              valueStyle={{ color: '#722ed1' }}
            />
            <div style={{ marginTop: 8, fontSize: 12, color: '#666' }}>
              施工区外围缓冲距离
            </div>
          </Card>
        </Col>
      </Row>

      <Card size="small" style={{ marginTop: 16 }} title="施工标注说明">
        <ul style={{ margin: 0, paddingLeft: 20, fontSize: 12, color: '#666' }}>
          <li>黄色虚线区域为施工区域，橙色填充表示施工范围</li>
          <li>红色虚线区域为虚拟缓冲区，用于检测车辆提前减速</li>
          <li>橙色三角为检测到的交通锥，缺失时会触发告警</li>
          <li>限速标志显示当前施工区的车辆限速要求</li>
          <li>点击「绘制施工区」可手动调整施工区域范围</li>
        </ul>
      </Card>
    </div>
  );
};

export default ConstructionLayer;
