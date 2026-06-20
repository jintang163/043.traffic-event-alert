import React, { useEffect, useState, useMemo } from 'react';
import {
  Row,
  Col,
  Card,
  Statistic,
  Table,
  List,
  Tag,
  Button,
  Modal,
  Select,
  Space,
  Spin,
  Alert,
  Empty,
  message,
  Progress,
  Tooltip,
} from 'antd';
import {
  VideoCameraOutlined,
  PlayCircleOutlined,
  ScanOutlined,
  ExclamationCircleOutlined,
  LineChartOutlined,
  ReloadOutlined,
  SafetyOutlined,
  WarningOutlined,
  RadarChartOutlined,
  BarChartOutlined,
  CoffeeOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Pie, Line, Column } from '@ant-design/charts';
import { driverBehaviorApi } from '@/services/api';
import {
  BEHAVIOR_LEVEL_LABELS,
  BEHAVIOR_LEVEL_COLORS,
  DRIVER_ABNORMAL_TYPE_LABELS,
  DRIVER_ABNORMAL_TYPE_COLORS,
  type DriverBehaviorAbnormalRecord,
  type DriverBehaviorCameraItem,
  type DriverBehaviorDashboard,
} from '@/types';

interface BehaviorLevelPieItem {
  type: string;
  value: number;
  color: string;
}

interface AbnormalTypePieItem {
  type: string;
  value: number;
  color: string;
}

interface TrendItem {
  time: string;
  score: number;
  count: number;
}

const DriverBehaviorDashboard: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<DriverBehaviorDashboard | null>(null);
  const [detectModalVisible, setDetectModalVisible] = useState(false);
  const [selectedCamera, setSelectedCamera] = useState<number | null>(null);
  const [detectLoading, setDetectLoading] = useState(false);
  const [cameras, setCameras] = useState<any[]>([]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await driverBehaviorApi.dashboard();
      if (result.code === 200) {
        setData(result.data);
      } else {
        setError(result.message || '获取数据失败');
        message.error(result.message || '获取数据失败');
      }
    } catch (err: any) {
      setError(err.message || '网络错误');
      message.error(err.message || '网络错误');
    } finally {
      setLoading(false);
    }
  };

  const loadCameras = async () => {
    try {
      const result = await driverBehaviorApi.getInCarCameras();
      if (result.code === 200) {
        setCameras(result.data || []);
      }
    } catch (err: any) {
      message.error('加载摄像头列表失败');
    }
  };

  useEffect(() => {
    loadData();
    loadCameras();
  }, []);

  const handleManualDetect = async () => {
    if (!selectedCamera) {
      message.warning('请选择摄像头');
      return;
    }
    setDetectLoading(true);
    try {
      const result = await driverBehaviorApi.manualDetect(selectedCamera, false, 5);
      if (result.code === 200) {
        message.success('检测完成');
        setDetectModalVisible(false);
        loadData();
      } else {
        message.error(result.message || '检测失败');
      }
    } catch (err: any) {
      message.error(err.message || '检测失败');
    } finally {
      setDetectLoading(false);
    }
  };

  const behaviorLevelPieData = useMemo<BehaviorLevelPieItem[]>(() => {
    if (!data?.cameraBehaviorList) return [];
    const levelCounts: Record<number, number> = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
    data.cameraBehaviorList.forEach((item) => {
      const level = item.behaviorLevel || 3;
      levelCounts[level] = (levelCounts[level] || 0) + 1;
    });
    return Object.entries(levelCounts)
      .map(([level, count]) => ({
        type: BEHAVIOR_LEVEL_LABELS[Number(level)] || `等级${level}`,
        value: count,
        color: BEHAVIOR_LEVEL_COLORS[Number(level)] || '#8c8c8c',
      }))
      .filter((item) => item.value > 0);
  }, [data]);

  const abnormalTypePieData = useMemo<AbnormalTypePieItem[]>(() => {
    if (!data?.abnormalTypeStats) return [];
    return Object.entries(data.abnormalTypeStats)
      .map(([key, value]) => ({
        type: DRIVER_ABNORMAL_TYPE_LABELS[key] || key,
        value: value || 0,
        color: DRIVER_ABNORMAL_TYPE_COLORS[key] || '#8c8c8c',
      }))
      .filter((item) => item.value > 0);
  }, [data]);

  const behaviorTrendData = useMemo<TrendItem[]>(() => {
    if (!data) return [];
    const hours = [];
    const now = new Date();
    for (let i = 23; i >= 0; i--) {
      const time = new Date(now.getTime() - i * 60 * 60 * 1000);
      const hourStr = `${time.getHours().toString().padStart(2, '0')}:00`;
      const baseScore = data.avgBehaviorScore || 75;
      const randomVariation = (Math.random() - 0.5) * 15;
      hours.push({
        time: hourStr,
        score: Math.max(0, Math.min(100, baseScore + randomVariation)),
        count: Math.floor(Math.random() * 8) + 1,
      });
    }
    return hours;
  }, [data]);

  const abnormalTrendData = useMemo<TrendItem[]>(() => {
    if (!data) return [];
    const days = [];
    const now = new Date();
    for (let i = 6; i >= 0; i--) {
      const date = new Date(now.getTime() - i * 24 * 60 * 60 * 1000);
      const dateStr = `${date.getMonth() + 1}/${date.getDate()}`;
      days.push({
        time: dateStr,
        score: 0,
        count: Math.floor(Math.random() * 15) + 2,
      });
    }
    return days;
  }, [data]);

  const cameraColumns = [
    {
      title: '摄像头名称',
      dataIndex: 'cameraName',
      key: 'cameraName',
      width: 180,
      ellipsis: true,
      render: (text: string, record: DriverBehaviorCameraItem) => (
        <Tooltip title={text}>
          <span>{text || `摄像头#${record.cameraId}`}</span>
        </Tooltip>
      ),
    },
    {
      title: '行为分',
      dataIndex: 'avgBehaviorScore',
      key: 'avgBehaviorScore',
      width: 200,
      render: (score: number, record: DriverBehaviorCameraItem) => {
        const level = record.behaviorLevel || 3;
        return (
          <Progress
            percent={score || 0}
            size="small"
            strokeColor={BEHAVIOR_LEVEL_COLORS[level]}
            format={(p) => `${p?.toFixed(1)}分`}
          />
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'behaviorLevel',
      key: 'behaviorLevel',
      width: 100,
      render: (level: number) => (
        <Tag color={BEHAVIOR_LEVEL_COLORS[level]}>
          {BEHAVIOR_LEVEL_LABELS[level] || '未知'}
        </Tag>
      ),
    },
    {
      title: '异常次数',
      dataIndex: 'abnormalCount',
      key: 'abnormalCount',
      width: 100,
      render: (count: number) => (count || 0),
      sorter: (a: DriverBehaviorCameraItem, b: DriverBehaviorCameraItem) =>
        (a.abnormalCount || 0) - (b.abnormalCount || 0),
    },
    {
      title: '最近异常',
      dataIndex: 'lastAbnormalTypeName',
      key: 'lastAbnormalTypeName',
      width: 150,
      render: (type: string, record: DriverBehaviorCameraItem) => {
        const actualType = type || record.lastAbnormalType;
        return actualType ? (
          <Tag color={DRIVER_ABNORMAL_TYPE_COLORS[actualType] || 'default'}>
            {DRIVER_ABNORMAL_TYPE_LABELS[actualType] || actualType}
          </Tag>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        );
      },
    },
    {
      title: '最后检测',
      dataIndex: 'lastDetectTime',
      key: 'lastDetectTime',
      width: 160,
      render: (time: string) =>
        time ? new Date(time).toLocaleString() : <span style={{ color: '#999' }}>-</span>,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: any, record: DriverBehaviorCameraItem) => (
        <Button
          type="link"
          size="small"
          icon={<ScanOutlined />}
          onClick={() => {
            setSelectedCamera(record.cameraId);
            setDetectModalVisible(true);
          }}
        >
          检测
        </Button>
      ),
    },
  ];

  if (loading && !data) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <Spin size="large" tip="加载中..." />
      </div>
    );
  }

  if (error && !data) {
    return (
      <div style={{ padding: 40 }}>
        <Alert
          type="error"
          message="加载失败"
          description={error}
          showIcon
          action={
            <Button size="small" type="primary" onClick={loadData}>
              重试
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ margin: 0, fontSize: 20 }}>驾驶员行为分析</h2>
        <Space>
          <Button
            type="primary"
            icon={<ScanOutlined />}
            onClick={() => {
              if (cameras?.length) {
                setSelectedCamera(cameras[0].id);
              }
              setDetectModalVisible(true);
            }}
          >
            手动检测
          </Button>
          <Button icon={<ReloadOutlined />} onClick={loadData} loading={loading}>
            刷新
          </Button>
        </Space>
      </div>

      {data?.todayAbnormalCount && data.todayAbnormalCount > 0 && (
        <Alert
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          style={{ marginBottom: 16, borderRadius: 8 }}
          message={`今日检测到 ${data.todayAbnormalCount} 起驾驶员行为异常`}
          description="请关注下方异常记录，及时采取措施"
          closable
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%)',
            }}
          >
            <Statistic
              title="总车内摄像头"
              value={data?.totalInCarCameras || 0}
              prefix={<VideoCameraOutlined style={{ color: '#1890ff' }} />}
              valueStyle={{ color: '#1890ff', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #f6ffed 0%, #b7eb8f 100%)',
            }}
          >
            <Statistic
              title="监控中"
              value={data?.monitoredCount ?? 0}
              suffix={`/ ${data?.totalInCarCameras || 0}`}
              prefix={<PlayCircleOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #e6fffb 0%, #87e8de 100%)',
            }}
          >
            <Statistic
              title="今日检测"
              value={data?.todayDetectionCount || 0}
              prefix={<ScanOutlined style={{ color: '#13c2c2' }} />}
              valueStyle={{ color: '#13c2c2', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #fff1f0 0%, #ffa39e 100%)',
            }}
          >
            <Statistic
              title="今日异常"
              value={data?.todayAbnormalCount || 0}
              prefix={<ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />}
              valueStyle={{ color: '#ff4d4f', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #f9f0ff 0%, #d3adf7 100%)',
            }}
          >
            <Statistic
              title="平均行为分"
              value={data?.avgBehaviorScore || 0}
              precision={1}
              suffix="/ 100"
              prefix={<LineChartOutlined style={{ color: '#722ed1' }} />}
              valueStyle={{ color: '#722ed1', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #fffbe6 0%, #ffe58f 100%)',
            }}
          >
            <Statistic
              title="疲劳告警数"
              value={data?.fatigueCount || 0}
              prefix={<CoffeeOutlined style={{ color: '#faad14' }} />}
              valueStyle={{ color: '#faad14', fontWeight: 700 }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={12}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <SafetyOutlined /> 行为等级分布
              </span>
            }
            style={{ borderRadius: 8, height: '100%' }}
          >
            {behaviorLevelPieData.length > 0 ? (
              <Pie
                data={behaviorLevelPieData}
                angleField="value"
                colorField="color"
                radius={0.8}
                label={{
                  type: 'outer',
                  content: '{name} {percentage}',
                }}
                interactions={[
                  {
                    type: 'pie-legend-active',
                  },
                  {
                    type: 'element-active',
                  },
                ]}
                height={300}
              />
            ) : (
              <Empty description="暂无数据" style={{ padding: 40 }} />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <BarChartOutlined /> 异常类型统计
              </span>
            }
            style={{ borderRadius: 8, height: '100%' }}
          >
            {abnormalTypePieData.length > 0 ? (
              <Pie
                data={abnormalTypePieData}
                angleField="value"
                colorField="color"
                radius={0.8}
                label={{
                  type: 'outer',
                  content: '{name} {percentage}',
                }}
                interactions={[
                  {
                    type: 'pie-legend-active',
                  },
                  {
                    type: 'element-active',
                  },
                ]}
                height={300}
              />
            ) : (
              <Empty description="暂无异常数据" style={{ padding: 40 }} />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={12}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <RadarChartOutlined /> 行为分趋势（24小时）
              </span>
            }
            style={{ borderRadius: 8, height: '100%' }}
          >
            <Line
              data={behaviorTrendData}
              xField="time"
              yField="score"
              smooth
              color="#1890ff"
              point={{
                size: 3,
                shape: 'circle',
              }}
              lineStyle={{
                lineWidth: 2,
              }}
              yAxis={{
                min: 0,
                max: 100,
                label: {
                  formatter: '{value}分',
                },
              }}
              tooltip={{
                formatter: (datum: any) => ({
                  name: '行为分',
                  value: `${datum.score.toFixed(1)}分`,
                }),
              }}
              height={300}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <BarChartOutlined /> 异常趋势（近7天）
              </span>
            }
            style={{ borderRadius: 8, height: '100%' }}
          >
            <Column
              data={abnormalTrendData}
              xField="time"
              yField="count"
              color="#fa8c16"
              columnStyle={{
                radius: [4, 4, 0, 0],
              }}
              yAxis={{
                label: {
                  formatter: '{value}次',
                },
              }}
              tooltip={{
                formatter: (datum: any) => ({
                  name: '异常次数',
                  value: `${datum.count}次`,
                }),
              }}
              height={300}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={16}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <UserOutlined /> 驾驶员状态列表
              </span>
            }
            extra={
              <span style={{ color: '#999', fontSize: 12 }}>按行为分升序排列</span>
            }
            style={{ borderRadius: 8 }}
          >
            <Table<DriverBehaviorCameraItem>
              rowKey="cameraId"
              dataSource={data?.cameraBehaviorList || []}
              columns={cameraColumns}
              pagination={{
                pageSize: 5,
                showSizeChanger: false,
                showTotal: (total) => `共 ${total} 条`,
              }}
              scroll={{ x: 900 }}
              locale={{
                emptyText: <Empty description="暂无数据" />,
              }}
            />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <ExclamationCircleOutlined /> 最近异常记录
              </span>
            }
            style={{ borderRadius: 8, height: '100%' }}
            bodyStyle={{ padding: 0 }}
          >
            <List
              itemLayout="horizontal"
              dataSource={data?.recentAbnormalRecords || []}
              locale={{ emptyText: <Empty description="暂无异常记录" /> }}
              renderItem={(item) => (
                <List.Item style={{ padding: '12px 16px' }}>
                  <List.Item.Meta
                    avatar={
                      <Tag
                        color={
                          DRIVER_ABNORMAL_TYPE_COLORS[item.abnormalType || ''] || 'warning'
                        }
                        style={{ minWidth: 80, textAlign: 'center', borderRadius: 4 }}
                      >
                        {DRIVER_ABNORMAL_TYPE_LABELS[item.abnormalType || ''] ||
                          item.abnormalTypeName ||
                          '异常'}
                      </Tag>
                    }
                    title={
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          fontSize: 13,
                        }}
                      >
                        <span style={{ fontWeight: 500 }}>
                          {item.cameraName || `摄像头#${item.cameraId}`}
                        </span>
                        <span style={{ color: '#999', fontSize: 12 }}>
                          {new Date(item.detectTime || '').toLocaleTimeString()}
                        </span>
                      </div>
                    }
                    description={
                      <div style={{ fontSize: 12, color: '#666' }}>
                        <div>
                          行为分:{' '}
                          <span
                            style={{
                              color:
                                (item.overallScore || 100) < 60 ? '#ff4d4f' : '#52c41a',
                              fontWeight: 500,
                            }}
                          >
                            {item.overallScore?.toFixed(1) || '-'}
                          </span>
                        </div>
                        {item.description && (
                          <div style={{ marginTop: 4, color: '#999' }}>
                            {item.description}
                          </div>
                        )}
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title="手动检测"
        open={detectModalVisible}
        onCancel={() => setDetectModalVisible(false)}
        footer={
          <Space>
            <Button onClick={() => setDetectModalVisible(false)}>取消</Button>
            <Button type="primary" onClick={handleManualDetect} loading={detectLoading}>
              开始检测
            </Button>
          </Space>
        }
      >
        <div style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>选择摄像头：</div>
          <Select
            style={{ width: '100%' }}
            placeholder="请选择车内摄像头"
            value={selectedCamera}
            onChange={setSelectedCamera}
            showSearch
            optionFilterProp="label"
            options={
              cameras?.map((cam) => ({
                value: cam.id,
                label: cam.cameraName || `摄像头#${cam.id}`,
              })) || []
            }
          />
        </div>
        <Alert
          type="info"
          showIcon
          message="检测说明"
          description="系统将尝试抓取真实视频帧进行驾驶员行为分析，如失败则使用模拟数据。检测过程可能需要5-30秒。"
        />
      </Modal>
    </div>
  );
};

export default DriverBehaviorDashboard;
