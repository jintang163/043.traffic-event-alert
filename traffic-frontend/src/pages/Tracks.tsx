import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, Input, Select, Card, Form, DatePicker,
  message, Drawer, Descriptions, Row, Col, Timeline, Tooltip, Image, Statistic,
  Empty, Tabs, Divider, Badge,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined, ReloadOutlined, EyeOutlined, CarOutlined,
  EnvironmentOutlined, ClockCircleOutlined, WarningOutlined,
  ArrowRightOutlined,
} from '@ant-design/icons';
import { globalTrackApi, cameraApi } from '@/services/api';
import {
  TRACK_STATUS_LABELS,
  TRACK_STATUS_COLORS,
  TARGET_CLASS_OPTIONS,
  type GlobalTrack,
  type TrackPoint,
  type Camera,
} from '@/types';

const { RangePicker } = DatePicker;
const { Option } = Select;

interface TrackSegment {
  cameraId: number;
  cameraName: string;
  startTime: string;
  endTime: string;
  pointCount: number;
  points: TrackPoint[];
}

const Tracks: React.FC = () => {
  const [data, setData] = useState<GlobalTrack[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [detailDrawer, setDetailDrawer] = useState(false);
  const [currentTrack, setCurrentTrack] = useState<GlobalTrack | null>(null);
  const [timeline, setTimeline] = useState<any>(null);
  const [segments, setSegments] = useState<TrackSegment[]>([]);
  const [cameras, setCameras] = useState<Camera[]>([]);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [activeTab, setActiveTab] = useState('timeline');

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const params: any = { ...values, current, size: pageSize };
      if (values.timeRange && values.timeRange.length === 2) {
        params.startTime = values.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = values.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const res: any = await globalTrackApi.page(params);
      if (res.code === 200) {
        setData(res.data.records);
        setTotal(res.data.total);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadCameras = async () => {
    try {
      const res: any = await cameraApi.list();
      if (res.code === 200) setCameras(res.data);
    } catch (e) { console.error(e); }
  };

  useEffect(() => { loadData(); loadCameras(); }, [current, pageSize]);

  const handleSearch = () => { setCurrent(1); loadData(); };
  const handleReset = () => { searchForm.resetFields(); setCurrent(1); loadData(); };

  const handleViewDetail = async (record: GlobalTrack) => {
    setCurrentTrack(record);
    setDetailDrawer(true);
    setLoadingDetail(true);
    setActiveTab('timeline');
    try {
      const [res1, res2] = await Promise.all([
        globalTrackApi.timeline(record.id),
        globalTrackApi.points(record.id),
      ]);
      if (res1.code === 200) {
        setTimeline(res1.data);
        setSegments(res1.data.segments || []);
      }
    } finally {
      setLoadingDetail(false);
    }
  };

  const getTargetClassLabel = (cls: string) => {
    const opt = TARGET_CLASS_OPTIONS.find((o) => o.value === cls);
    return opt?.label || cls;
  };

  const columns: ColumnsType<GlobalTrack> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    {
      title: '轨迹编号', dataIndex: 'trackNo', width: 200,
      render: (t) => (
        <Space>
          <CarOutlined />
          <span style={{ fontFamily: 'monospace' }}>{t}</span>
        </Space>
      ),
    },
    {
      title: '目标类别', dataIndex: 'targetClass', width: 100,
      render: (v) => <Tag color="blue">{getTargetClassLabel(v)}</Tag>,
    },
    {
      title: '车牌号', dataIndex: 'licensePlate', width: 120,
      render: (v) => v ? (
        <Tag color="geekblue" style={{ fontFamily: 'monospace', fontSize: 14, padding: '2px 10px' }}>
          {v}
        </Tag>
      ) : <span style={{ color: '#999' }}>未识别</span>,
    },
    {
      title: '车辆信息', width: 160,
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          {r.color && <span>颜色：<Tag>{r.color}</Tag></span>}
          {r.vehicleType && <span>类型：{r.vehicleType}</span>}
        </Space>
      ),
    },
    {
      title: '经过摄像头', width: 220,
      render: (_, r) => (
        <Space direction="vertical" size={2}>
          <div style={{ fontSize: 12, color: '#999' }}>
            首次：{r.firstCameraName || '-'}
          </div>
          <div style={{ fontSize: 12, color: '#999' }}>
            最后：{r.lastCameraName || '-'}
          </div>
          <Tag color="cyan">共 {r.cameraCount || 1} 个</Tag>
        </Space>
      ),
    },
    {
      title: '时间范围', width: 200,
      render: (_, r) => (
        <Space direction="vertical" size={2}>
          <div style={{ fontSize: 12, color: '#666' }}>
            <ClockCircleOutlined /> {r.firstSeenTime?.slice(11, 19)}
          </div>
          <div style={{ fontSize: 12, color: '#666' }}>
            <ArrowRightOutlined /> {r.lastSeenTime?.slice(11, 19)}
          </div>
        </Space>
      ),
    },
    {
      title: '统计', width: 150,
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <span>轨迹点：{r.pointCount || 0}</span>
          {r.totalDistance && <span>里程：{r.totalDistance} m</span>}
          {r.avgSpeed && <span>平均：{r.avgSpeed} km/h</span>}
        </Space>
      ),
    },
    {
      title: '状态', dataIndex: 'trackStatus', width: 100, align: 'center',
      render: (v) => (
        <Badge status={TRACK_STATUS_COLORS[v] || 'default'}
          text={TRACK_STATUS_LABELS[v] || '-'} />
      ),
    },
    {
      title: '关联事件', dataIndex: 'linkedEventCount', width: 90, align: 'center',
      render: (v) => v && v > 0 ? (
        <Tag color="red" icon={<WarningOutlined />}>{v} 起</Tag>
      ) : <span style={{ color: '#999' }}>无</span>,
    },
    {
      title: '操作', key: 'action', width: 100, fixed: 'right',
      render: (_, record) => (
        <Tooltip title="查看轨迹详情">
          <Button type="link" size="small" icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}>
            详情
          </Button>
        </Tooltip>
      ),
    },
  ];

  const renderSegmentTimeline = () => {
    if (!segments || segments.length === 0) {
      return <Empty description="暂无轨迹数据" />;
    }

    return (
      <Timeline
        mode="left"
        items={segments.map((seg, idx) => ({
          color: idx === segments.length - 1 ? 'green' : 'blue',
          label: (
            <div style={{ minWidth: 180 }}>
              <div style={{ fontWeight: 600, color: '#1677ff' }}>
                {seg.cameraName}
              </div>
              <div style={{ fontSize: 12, color: '#999', marginTop: 2 }}>
                摄像头 #{seg.cameraId}
              </div>
            </div>
          ),
          children: (
            <Card size="small" style={{ borderRadius: 8, marginBottom: 8 }}>
              <Row gutter={16}>
                <Col span={8}>
                  <Statistic title="进入时间"
                    value={seg.startTime?.replace('T', ' ').slice(0, 19)}
                    valueStyle={{ fontSize: 13 }} />
                </Col>
                <Col span={8}>
                  <Statistic title="离开时间"
                    value={seg.endTime?.replace('T', ' ').slice(0, 19)}
                    valueStyle={{ fontSize: 13 }} />
                </Col>
                <Col span={8}>
                  <Statistic title="采集点数" value={seg.pointCount} suffix="帧" />
                </Col>
              </Row>
              <Divider style={{ margin: '8px 0' }} />
              <div style={{ fontSize: 12, color: '#666' }}>
                <EnvironmentOutlined /> 覆盖区域：视频画面内该目标出现的完整区域
              </div>
            </Card>
          ),
        }))}
      />
    );
  };

  const renderSegmentMiniMaps = () => {
    if (!segments || segments.length === 0) {
      return <Empty description="暂无轨迹片段" />;
    }

    return (
      <Row gutter={[12, 12]}>
        {segments.map((seg, idx) => (
          <Col span={idx === segments.length - 1 ? 24 : 12} key={idx}>
            <Card
              size="small"
              title={
                <Space>
                  <Tag color={idx === segments.length - 1 ? 'green' : 'blue'}>
                    {idx + 1}
                  </Tag>
                  {seg.cameraName}
                </Space>
              }
              extra={<span style={{ fontSize: 12, color: '#999' }}>
                {seg.pointCount} 帧
              </span>}
            >
              <div style={{
                position: 'relative', width: '100%', aspectRatio: '16/9',
                background: '#1a1a2e', borderRadius: 6, overflow: 'hidden',
              }}>
                <svg
                  viewBox="0 0 100 56.25"
                  preserveAspectRatio="none"
                  style={{ position: 'absolute', width: '100%', height: '100%' }}
                >
                  <defs>
                    <linearGradient id={`path-grad-${idx}`} x1="0" x2="1" y1="0" y2="0">
                      <stop offset="0%" stopColor="#1890ff" stopOpacity="0.3" />
                      <stop offset="100%" stopColor="#52c41a" stopOpacity="1" />
                    </linearGradient>
                  </defs>
                  {seg.points && seg.points.length > 1 && (
                    <polyline
                      fill="none"
                      stroke={`url(#path-grad-${idx})`}
                      strokeWidth="0.8"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      points={seg.points.map((p) =>
                        `${(p.pixelX || 0.5) * 100},${(p.pixelY || 0.5) * 56.25}`
                      ).join(' ')}
                    />
                  )}
                  {seg.points && seg.points.length > 0 && (
                    <>
                      <circle
                        cx={(seg.points[0].pixelX || 0.5) * 100}
                        cy={(seg.points[0].pixelY || 0.5) * 56.25}
                        r="1.5" fill="#1890ff" stroke="#fff" strokeWidth="0.4"
                      />
                      <circle
                        cx={(seg.points[seg.points.length - 1].pixelX || 0.5) * 100}
                        cy={(seg.points[seg.points.length - 1].pixelY || 0.5) * 56.25}
                        r="1.5" fill="#52c41a" stroke="#fff" strokeWidth="0.4"
                      />
                    </>
                  )}
                </svg>
                <div style={{
                  position: 'absolute', top: 4, left: 6, fontSize: 10,
                  color: '#1890ff', fontWeight: 600,
                }}>● 进入</div>
                <div style={{
                  position: 'absolute', bottom: 4, right: 6, fontSize: 10,
                  color: '#52c41a', fontWeight: 600,
                }}>● 离开</div>
              </div>
              <div style={{ marginTop: 6, fontSize: 12, color: '#666' }}>
                {seg.startTime?.slice(11, 19)} → {seg.endTime?.slice(11, 19)}
              </div>
            </Card>
          </Col>
        ))}
      </Row>
    );
  };

  const renderTrackMap = () => {
    if (!currentTrack) return <Empty />;
    return (
      <Card size="small" title="整体轨迹（按摄像头）">
        <div style={{
          position: 'relative', width: '100%', aspectRatio: '2/1',
          background: 'linear-gradient(135deg,#0a1628,#1a2a4a)',
          borderRadius: 8, overflow: 'hidden',
        }}>
          <svg viewBox="0 0 200 100" preserveAspectRatio="xMidYMid meet"
            style={{ position: 'absolute', width: '100%', height: '100%' }}>
            <defs>
              <linearGradient id="track-grad" x1="0" x2="1" y1="0" y2="0">
                <stop offset="0%" stopColor="#1890ff" />
                <stop offset="50%" stopColor="#faad14" />
                <stop offset="100%" stopColor="#52c41a" />
              </linearGradient>
              <marker id="arrow" markerWidth="6" markerHeight="6"
                refX="3" refY="3" orient="auto">
                <path d="M0,0 L6,3 L0,6 Z" fill="#52c41a" />
              </marker>
            </defs>

            {segments.map((_, idx) => {
              const x1 = 20 + idx * 70;
              const y1 = 50 + Math.sin(idx * 1.5) * 20;
              const x2 = x1 + 60;
              const y2 = 50 + Math.sin((idx + 1) * 1.5) * 20;
              return (
                <g key={idx}>
                  <path
                    d={`M${x1},${y1} Q${(x1 + x2) / 2},${(y1 + y2) / 2 - 15} ${x2},${y2}`}
                    fill="none" stroke="url(#track-grad)" strokeWidth="3"
                    strokeLinecap="round" markerEnd="url(#arrow)"
                    strokeDasharray={idx === segments.length - 1 ? '0' : '8,4'}
                  />
                  <circle cx={x1} cy={y1} r="6"
                    fill={idx === 0 ? '#1890ff' : '#faad14'} stroke="#fff" strokeWidth="2" />
                  <text x={x1} y={y1 + 20} fontSize="9" fill="#fff"
                    textAnchor="middle">
                    {segments[idx]?.cameraName?.slice(-4) || ''}
                  </text>
                </g>
              );
            })}
            {segments.length > 0 && (
              <circle cx={20 + (segments.length - 1) * 70 + 60}
                cy={50 + Math.sin(segments.length * 1.5) * 20} r="6"
                fill="#52c41a" stroke="#fff" strokeWidth="2" />
            )}
          </svg>
          <div style={{
            position: 'absolute', top: 8, left: 12, color: '#fff',
            fontSize: 12, fontWeight: 600,
          }}>
            经过 {segments.length} 个摄像头 · 共 {currentTrack.pointCount || 0} 个轨迹点
          </div>
        </div>
      </Card>
    );
  };

  return (
    <div>
      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="keyword" label="关键词">
            <Input placeholder="轨迹编号/车牌号" style={{ width: 180 }} allowClear />
          </Form.Item>
          <Form.Item name="targetClass" label="目标类别">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              {TARGET_CLASS_OPTIONS.map((o) => (
                <Option key={o.value} value={o.value}>{o.label}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="cameraId" label="摄像头">
            <Select placeholder="全部" style={{ width: 160 }} allowClear>
              {cameras.map((c) => (
                <Option key={c.id} value={c.id}>{c.cameraName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="trackStatus" label="状态">
            <Select placeholder="全部" style={{ width: 110 }} allowClear>
              <Option value={1}>跟踪中</Option>
              <Option value={2}>已丢失</Option>
              <Option value={3}>已完成</Option>
            </Select>
          </Form.Item>
          <Form.Item name="timeRange" label="时间">
            <RangePicker showTime />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                搜索
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card style={{ borderRadius: 8 }} title="全局轨迹列表"
        extra={
          <Space>
            <Tag color="blue">共 {total} 条轨迹</Tag>
            <Tag color="cyan">
              跟踪中 {data.filter((d) => d.trackStatus === 1).length}
            </Tag>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          pagination={{
            current, pageSize, total,
            showSizeChanger: true, showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (c, s) => { setCurrent(c); setPageSize(s); },
          }}
          scroll={{ x: 1600 }}
        />
      </Card>

      <Drawer
        title={
          <Space>
            <CarOutlined />
            <span>轨迹详情 - {currentTrack?.trackNo}</span>
            {currentTrack && (
              <Badge
                status={TRACK_STATUS_COLORS[currentTrack.trackStatus || 1] || 'default'}
                text={TRACK_STATUS_LABELS[currentTrack.trackStatus || 1]}
              />
            )}
          </Space>
        }
        placement="right"
        width={960}
        open={detailDrawer}
        onClose={() => setDetailDrawer(false)}
        loading={loadingDetail}
      >
        {currentTrack && (
          <div>
            <Descriptions size="small" bordered column={2} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="轨迹编号">
                <span style={{ fontFamily: 'monospace' }}>{currentTrack.trackNo}</span>
              </Descriptions.Item>
              <Descriptions.Item label="目标类别">
                <Tag color="blue">{getTargetClassLabel(currentTrack.targetClass || '')}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="车牌号">
                {currentTrack.licensePlate ? (
                  <Tag color="geekblue" style={{ fontFamily: 'monospace', padding: '2px 10px' }}>
                    {currentTrack.licensePlate}
                  </Tag>
                ) : <span style={{ color: '#999' }}>未识别</span>}
              </Descriptions.Item>
              <Descriptions.Item label="车辆信息">
                <Space>
                  {currentTrack.color && <Tag>{currentTrack.color}</Tag>}
                  {currentTrack.vehicleType && <span>{currentTrack.vehicleType}</span>}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="首次出现">
                {currentTrack.firstCameraName || '-'}
                <div style={{ fontSize: 12, color: '#999' }}>
                  {currentTrack.firstSeenTime?.replace('T', ' ')}
                </div>
              </Descriptions.Item>
              <Descriptions.Item label="最后出现">
                {currentTrack.lastCameraName || '-'}
                <div style={{ fontSize: 12, color: '#999' }}>
                  {currentTrack.lastSeenTime?.replace('T', ' ')}
                </div>
              </Descriptions.Item>
              <Descriptions.Item label="经过摄像头">
                <Tag color="cyan">{currentTrack.cameraCount || 1} 个</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="轨迹点数">
                {currentTrack.pointCount || 0} 帧
              </Descriptions.Item>
              {currentTrack.totalDistance && (
                <Descriptions.Item label="累计里程">
                  {currentTrack.totalDistance} m
                </Descriptions.Item>
              )}
              {currentTrack.avgSpeed && (
                <Descriptions.Item label="平均速度">
                  {currentTrack.avgSpeed} km/h
                </Descriptions.Item>
              )}
              <Descriptions.Item label="关联事件" span={2}>
                {currentTrack.linkedEventCount && currentTrack.linkedEventCount > 0 ? (
                  <Tag color="red" icon={<WarningOutlined />}>
                    关联 {currentTrack.linkedEventCount} 起告警事件
                  </Tag>
                ) : <span style={{ color: '#999' }}>无关联事件</span>}
              </Descriptions.Item>
            </Descriptions>

            {renderTrackMap()}

            <Divider />

            <Tabs activeKey={activeTab} onChange={setActiveTab}
              items={[
                {
                  key: 'timeline',
                  label: <Space><ClockCircleOutlined />时间线</Space>,
                  children: renderSegmentTimeline(),
                },
                {
                  key: 'segments',
                  label: <Space><EnvironmentOutlined />轨迹片段</Space>,
                  children: renderSegmentMiniMaps(),
                },
              ]}
            />
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default Tracks;
