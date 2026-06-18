import React, { useEffect, useState, useRef } from 'react';
import {
  Row,
  Col,
  Card,
  Statistic,
  Form,
  Select,
  DatePicker,
  Button,
  Table,
  Tag,
  Space,
  Tabs,
  message,
  Spin,
  Empty,
  Switch,
  Tooltip,
} from 'antd';
import {
  CarOutlined,
  ThunderboltOutlined,
  DashboardOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  LineChartOutlined,
  TableOutlined,
  DatabaseOutlined,
  CloudServerOutlined,
} from '@ant-design/icons';
import { Line, Column } from '@ant-design/charts';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { statisticsApi, cameraApi } from '@/services/api';
import type { Camera, TrafficStatisticsVO, TrafficRealtimeVO, TrafficOverview } from '@/types';
import {
  TRAFFIC_LEVEL_LABELS,
  TRAFFIC_LEVEL_COLORS,
  AGGREGATE_TYPE_OPTIONS,
} from '@/types';

const { RangePicker } = DatePicker;

const TrafficStatistics: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [overview, setOverview] = useState<TrafficOverview | null>(null);
  const [cameras, setCameras] = useState<Camera[]>([]);
  const [historyData, setHistoryData] = useState<TrafficStatisticsVO[]>([]);
  const [realtimeData, setRealtimeData] = useState<TrafficRealtimeVO[]>([]);
  const [activeTab, setActiveTab] = useState('chart');
  const [influxDbAvailable, setInfluxDbAvailable] = useState(false);
  const [useInfluxDb, setUseInfluxDb] = useState(false);
  const realtimeTimerRef = useRef<number | null>(null);

  const loadCameras = async () => {
    try {
      const res: any = await cameraApi.list();
      if (res.code === 200) {
        setCameras(res.data || []);
      }
    } catch (e) {
      console.error('Load cameras failed:', e);
    }
  };

  const checkInfluxDb = async () => {
    try {
      const res: any = await statisticsApi.influxDbStatus();
      if (res.code === 200 && res.data) {
        setInfluxDbAvailable(res.data.available === true);
        if (!res.data.available) {
          setUseInfluxDb(false);
        }
      }
    } catch (e) {
      setInfluxDbAvailable(false);
      setUseInfluxDb(false);
    }
  };

  const loadOverview = async () => {
    try {
      const res: any = await statisticsApi.trafficOverview();
      if (res.code === 200) {
        setOverview(res.data);
        if (res.data?.realtimeList) {
          setRealtimeData(res.data.realtimeList);
        }
      }
    } catch (e) {
      console.error('Load traffic overview failed:', e);
    }
  };

  const loadHistory = async (values?: any) => {
    setLoading(true);
    try {
      const params = { ...values };
      if (params.timeRange && params.timeRange.length === 2) {
        params.startTime = params.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = params.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
        delete params.timeRange;
      } else {
        const now = dayjs();
        params.startTime = now.subtract(2, 'hour').format('YYYY-MM-DD HH:mm:ss');
        params.endTime = now.format('YYYY-MM-DD HH:mm:ss');
      }
      const res: any = await statisticsApi.trafficHistory({
        ...params,
        dataSource: useInfluxDb ? 'influxdb' : 'mysql',
      });
      if (res.code === 200) {
        setHistoryData(res.data || []);
      }
    } catch (e: any) {
      message.error(e.message || '加载历史数据失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    form.validateFields().then((values) => {
      loadHistory(values);
    });
  };

  const handleAggregate = async () => {
    try {
      const values = await form.validateFields();
      const params: any = { aggregateType: values.aggregateType };
      if (values.cameraId) params.cameraId = values.cameraId;
      if (values.timeRange && values.timeRange.length === 2) {
        params.startTime = values.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = values.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const res: any = await statisticsApi.trafficAggregate(params);
      if (res.code === 200) {
        message.success('聚合任务已触发');
        loadHistory(values);
        loadOverview();
      }
    } catch (e: any) {
      message.error(e.message || '触发聚合失败');
    }
  };

  const loadSelectedCameraRealtime = async (cameraId?: number) => {
    if (!cameraId) return;
    try {
      const res: any = await statisticsApi.trafficRealtime(cameraId);
      if (res.code === 200) {
        setRealtimeData(res.data || []);
      }
    } catch (e) {
      console.error('Load realtime data failed:', e);
    }
  };

  useEffect(() => {
    loadCameras();
    checkInfluxDb();
    loadOverview();
    loadHistory();

    realtimeTimerRef.current = window.setInterval(() => {
      loadOverview();
      const selectedCamera = form.getFieldValue('cameraId');
      if (selectedCamera) {
        loadSelectedCameraRealtime(selectedCamera);
      }
    }, 30000);

    return () => {
      if (realtimeTimerRef.current) {
        clearInterval(realtimeTimerRef.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const getLevelColor = (level?: string) => {
    return TRAFFIC_LEVEL_COLORS[level || 'SMOOTH'] || '#52c41a';
  };

  const getLevelTag = (level?: string) => {
    const color = getLevelColor(level);
    return <Tag color={color}>{TRAFFIC_LEVEL_LABELS[level || 'SMOOTH'] || '未知'}</Tag>;
  };

  const buildFlowChartData = () => {
    if (historyData.length === 0) return [];
    return historyData.map((item) => ({
      time: dayjs(item.statTime).format('MM-DD HH:mm'),
      流量: item.flowVolume || 0,
      车道: item.laneName || `${item.laneNo || 1}号车道`,
    }));
  };

  const buildSpeedChartData = () => {
    if (historyData.length === 0) return [];
    return historyData.map((item) => ({
      time: dayjs(item.statTime).format('MM-DD HH:mm'),
      速度: Number((item.avgSpeed || 0).toFixed(2)),
      车道: item.laneName || `${item.laneNo || 1}号车道`,
    }));
  };

  const buildOccupancyChartData = () => {
    if (historyData.length === 0) return [];
    return historyData.map((item) => ({
      time: dayjs(item.statTime).format('MM-DD HH:mm'),
      时间占有率: Number((item.occupancy || 0).toFixed(2)),
      车道: item.laneName || `${item.laneNo || 1}号车道`,
    }));
  };

  const buildDensityChartData = () => {
    if (historyData.length === 0) return [];
    const laneGroups = new Map<string, Array<{ time: string; 密度: number }>>();
    historyData.forEach((item) => {
      const lane = item.laneName || `${item.laneNo || 1}号车道`;
      const arr = laneGroups.get(lane) || [];
      arr.push({
        time: dayjs(item.statTime).format('MM-DD HH:mm'),
        密度: Number((item.density || 0).toFixed(2)),
      });
      laneGroups.set(lane, arr);
    });
    return Array.from(laneGroups.entries()).map(([lane, data]) => ({
      name: lane,
      data,
    }));
  };

  const commonLineConfig = {
    xField: 'time',
    yField: 'value',
    seriesField: 'lane',
    smooth: true,
    point: { size: 3 },
    legend: { position: 'top' },
    height: 300,
    color: ['#1890ff', '#52c41a', '#faad14', '#eb2f96', '#722ed1', '#13c2c2'],
  };

  const columns = [
    {
      title: '时间',
      dataIndex: 'statTime',
      key: 'statTime',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
      width: 170,
    },
    {
      title: '摄像头',
      dataIndex: 'cameraName',
      key: 'cameraName',
      width: 200,
    },
    {
      title: '车道',
      dataIndex: 'laneName',
      key: 'laneName',
      render: (_: any, record: TrafficStatisticsVO) =>
        record.laneName || `${record.laneNo || 1}号车道`,
      width: 100,
    },
    {
      title: '车流量(辆)',
      dataIndex: 'flowVolume',
      key: 'flowVolume',
      render: (v: number) => <strong style={{ color: '#1890ff' }}>{v || 0}</strong>,
      sorter: (a: TrafficStatisticsVO, b: TrafficStatisticsVO) =>
        (a.flowVolume || 0) - (b.flowVolume || 0),
      width: 110,
    },
    {
      title: '平均速度(km/h)',
      dataIndex: 'avgSpeed',
      key: 'avgSpeed',
      render: (v: number) => <strong style={{ color: '#52c41a' }}>{(v || 0).toFixed(2)}</strong>,
      sorter: (a: TrafficStatisticsVO, b: TrafficStatisticsVO) =>
        (a.avgSpeed || 0) - (b.avgSpeed || 0),
      width: 140,
    },
    {
      title: '速度范围',
      key: 'speedRange',
      render: (_: any, r: TrafficStatisticsVO) =>
        `${(r.minSpeed || 0).toFixed(0)} ~ ${(r.maxSpeed || 0).toFixed(0)}`,
      width: 110,
    },
    {
      title: '时间占有率(%)',
      dataIndex: 'occupancy',
      key: 'occupancy',
      render: (v: number) => <strong style={{ color: '#faad14' }}>{(v || 0).toFixed(2)}%</strong>,
      sorter: (a: TrafficStatisticsVO, b: TrafficStatisticsVO) =>
        (a.occupancy || 0) - (b.occupancy || 0),
      width: 130,
    },
    {
      title: '密度(辆/km)',
      dataIndex: 'density',
      key: 'density',
      render: (v: number) => <strong style={{ color: '#eb2f96' }}>{(v || 0).toFixed(2)}</strong>,
      sorter: (a: TrafficStatisticsVO, b: TrafficStatisticsVO) =>
        (a.density || 0) - (b.density || 0),
      width: 120,
    },
    {
      title: '平均车头时距(s)',
      dataIndex: 'avgHeadway',
      key: 'avgHeadway',
      render: (v: number) => (v ? v.toFixed(2) : '-'),
      width: 140,
    },
  ];

  const realtimeColumns = [
    {
      title: '摄像头',
      dataIndex: 'cameraName',
      key: 'cameraName',
      width: 220,
    },
    {
      title: '车道',
      dataIndex: 'laneName',
      key: 'laneName',
      width: 100,
    },
    {
      title: '更新时间',
      dataIndex: 'timestamp',
      key: 'timestamp',
      render: (v: string) => v ? dayjs(v).format('HH:mm:ss') : '-',
      width: 100,
    },
    {
      title: '5分钟流量(辆)',
      dataIndex: 'flowVolume',
      key: 'flowVolume',
      render: (v: number, r: TrafficRealtimeVO) =>
        r.level === 'NO_DATA' ? '-' : <strong style={{ color: '#1890ff' }}>{v || 0}</strong>,
      width: 130,
    },
    {
      title: '平均速度(km/h)',
      dataIndex: 'avgSpeed',
      key: 'avgSpeed',
      render: (v: number, r: TrafficRealtimeVO) =>
        r.level === 'NO_DATA' ? '-' : <strong style={{ color: '#52c41a' }}>{(v || 0).toFixed(1)}</strong>,
      width: 130,
    },
    {
      title: '时间占有率(%)',
      dataIndex: 'occupancy',
      key: 'occupancy',
      render: (v: number, r: TrafficRealtimeVO) =>
        r.level === 'NO_DATA' ? '-' : <strong style={{ color: '#faad14' }}>{(v || 0).toFixed(1)}%</strong>,
      width: 130,
    },
    {
      title: '密度(辆/km)',
      dataIndex: 'density',
      key: 'density',
      render: (v: number, r: TrafficRealtimeVO) =>
        r.level === 'NO_DATA' ? '-' : <strong style={{ color: '#eb2f96' }}>{(v || 0).toFixed(1)}</strong>,
      width: 120,
    },
    {
      title: '交通状态',
      key: 'level',
      render: (_: any, r: TrafficRealtimeVO) => getLevelTag(r.level),
      width: 100,
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ margin: 0, fontSize: 20 }}>
          <LineChartOutlined style={{ marginRight: 8, color: '#1890ff' }} />
          交通参数统计
        </h2>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => { loadOverview(); loadHistory(form.getFieldsValue()); }}>
            刷新
          </Button>
        </Space>
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%)' }}>
            <Statistic
              title="5分钟累计流量"
              value={overview?.totalFlow || 0}
              suffix="辆"
              prefix={<CarOutlined style={{ color: '#1890ff' }} />}
              valueStyle={{ color: '#1890ff', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #f6ffed 0%, #b7eb8f 100%)' }}>
            <Statistic
              title="整体平均速度"
              value={overview?.avgSpeed || 0}
              precision={1}
              suffix="km/h"
              prefix={<DashboardOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #fff1f0 0%, #ffa39e 100%)' }}>
            <Statistic
              title="拥堵/缓行车道"
              value={(overview?.congestedLanes || 0) + (overview?.slowLanes || 0)}
              suffix={`/ ${overview?.totalLanes || 0}`}
              prefix={<ThunderboltOutlined style={{ color: '#ff4d4f' }} />}
              valueStyle={{ color: '#ff4d4f', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #f9f0ff 0%, #d3adf7 100%)' }}>
            <Statistic
              title="在线监控点"
              value={overview?.activeCameras || 0}
              suffix="个"
              prefix={<PlayCircleOutlined style={{ color: '#722ed1' }} />}
              valueStyle={{ color: '#722ed1', fontWeight: 700 }}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="查询条件"
        style={{ borderRadius: 8, marginBottom: 16 }}
        bodyStyle={{ padding: 16 }}
      >
        <Form
          form={form}
          layout="inline"
          onFinish={handleSearch}
          initialValues={{
            aggregateType: 'minute',
            timeRange: [dayjs().subtract(2, 'hour'), dayjs()],
          }}
        >
          <Form.Item name="cameraId" label="摄像头">
            <Select
              allowClear
              placeholder="全部摄像头"
              style={{ width: 220 }}
              options={cameras.map((c) => ({ label: c.cameraName, value: c.id }))}
              onChange={(val) => loadSelectedCameraRealtime(val)}
            />
          </Form.Item>
          <Form.Item name="laneNo" label="车道号">
            <Select
              allowClear
              placeholder="全部车道"
              style={{ width: 120 }}
              options={[1, 2, 3, 4, 5, 6].map((n) => ({ label: `${n}号车道`, value: n }))}
            />
          </Form.Item>
          <Form.Item name="aggregateType" label="聚合类型">
            <Select style={{ width: 100 }} options={AGGREGATE_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="timeRange" label="时间范围">
            <RangePicker showTime format="YYYY-MM-DD HH:mm" />
          </Form.Item>
          <Form.Item label="数据源">
            <Tooltip title={influxDbAvailable ? '切换InfluxDB时序库查询' : 'InfluxDB不可用（未启用或连接失败）'}>
              <Space>
                <Switch
                  checked={useInfluxDb}
                  onChange={(checked) => {
                    if (checked && !influxDbAvailable) {
                      message.warning('InfluxDB不可用，请检查服务状态');
                      return;
                    }
                    setUseInfluxDb(checked);
                  }}
                  disabled={!influxDbAvailable}
                  checkedChildren={<CloudServerOutlined />}
                  unCheckedChildren={<DatabaseOutlined />}
                />
                <span style={{ fontSize: 12, color: influxDbAvailable ? '#52c41a' : '#999' }}>
                  {useInfluxDb ? 'InfluxDB' : 'MySQL'}
                </span>
              </Space>
            </Tooltip>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={loading} icon={<LineChartOutlined />}>
                查询
              </Button>
              <Button onClick={handleAggregate} icon={<PlayCircleOutlined />}>
                手动聚合
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        title={
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <ThunderboltOutlined style={{ color: '#faad14' }} />
            实时交通态势
          </span>
        }
        style={{ borderRadius: 8, marginBottom: 16 }}
        bodyStyle={{ padding: 0 }}
      >
        {realtimeData.length > 0 ? (
          <Table
            size="small"
            dataSource={realtimeData}
            columns={realtimeColumns}
            rowKey={(r) => `${r.cameraId}-${r.laneNo}`}
            pagination={false}
            scroll={{ x: 1000 }}
          />
        ) : (
          <Empty description="暂无实时数据" style={{ padding: 40 }} />
        )}
      </Card>

      <Card
        style={{ borderRadius: 8 }}
        tabBarExtraContent={<Tag color="blue">数据点: {historyData.length}</Tag>}
      >
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'chart',
              label: (
                <span>
                  <LineChartOutlined /> 图表分析
                </span>
              ),
              children: (
                <Spin spinning={loading}>
                  {historyData.length > 0 ? (
                    <Space direction="vertical" size={24} style={{ width: '100%' }}>
                      <Card title="🚗 车流量趋势（按车道）" size="small" style={{ borderRadius: 8 }}>
                        <Line
                          {...commonLineConfig}
                          data={buildFlowChartData()}
                          xField="time"
                          yField="流量"
                          seriesField="车道"
                          yAxis={{ title: { text: '辆' } }}
                        />
                      </Card>
                      <Card title="🏎️ 平均速度趋势（按车道）" size="small" style={{ borderRadius: 8 }}>
                        <Line
                          {...commonLineConfig}
                          data={buildSpeedChartData()}
                          xField="time"
                          yField="速度"
                          seriesField="车道"
                          yAxis={{ title: { text: 'km/h' } }}
                        />
                      </Card>
                      <Card title="⏱️ 时间占有率趋势（按车道）" size="small" style={{ borderRadius: 8 }}>
                        <Column
                          height={300}
                          data={buildOccupancyChartData()}
                          xField="time"
                          yField="时间占有率"
                          seriesField="车道"
                          isGroup
                          legend={{ position: 'top' }}
                          yAxis={{ title: { text: '%' } }}
                          color={['#1890ff', '#52c41a', '#faad14', '#eb2f96', '#722ed1', '#13c2c2']}
                        />
                      </Card>
                      <Card title="📊 交通密度趋势（按车道）" size="small" style={{ borderRadius: 8 }}>
                        <Line
                          {...commonLineConfig}
                          data={buildDensityChartData().flatMap((g) =>
                            g.data.map((d) => ({ ...d, 车道: g.name })),
                          )}
                          xField="time"
                          yField="密度"
                          seriesField="车道"
                          yAxis={{ title: { text: '辆/km' } }}
                        />
                      </Card>
                    </Space>
                  ) : (
                    <Empty description="暂无历史数据，请选择更大的时间范围或触发手动聚合" style={{ padding: 60 }} />
                  )}
                </Spin>
              ),
            },
            {
              key: 'table',
              label: (
                <span>
                  <TableOutlined /> 明细数据
                </span>
              ),
              children: (
                <Spin spinning={loading}>
                  <Table
                    size="small"
                    dataSource={historyData}
                    columns={columns}
                    rowKey={(r) => `${r.cameraId}-${r.laneNo}-${r.statTime}`}
                    pagination={{ pageSize: 20, showSizeChanger: true, showQuickJumper: true }}
                    scroll={{ x: 1200 }}
                  />
                </Spin>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
};

export default TrafficStatistics;
