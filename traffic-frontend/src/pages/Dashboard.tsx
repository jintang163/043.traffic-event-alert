import React, { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, List, Tag, Button, Modal, notification, Alert } from 'antd';
import {
  CarOutlined,
  WarningOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  VideoCameraOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  SafetyOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { statisticsApi, alertApi, cameraApi, alertDedupApi } from '@/services/api';
import { wsService } from '@/services/websocket';
import { useAlertStore, type StormSuppressedCamera } from '@/store/alertStore';
import VideoPlayer from '@/components/VideoPlayer';
import PtzPanel from '@/components/PtzPanel';
import PtzCruisePanel from '@/components/PtzCruisePanel';
import {
  EVENT_TYPE_LABELS,
  EVENT_LEVEL_LABELS,
  EVENT_LEVEL_COLORS,
  ALERT_STATUS_LABELS,
  type AlertEvent,
  type Camera,
  type StatisticsOverview,
} from '@/types';

const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const { addAlert, addStormSuppressed, removeStormSuppressed, setStormSuppressedList, stormSuppressedCameras } = useAlertStore();
  const [stats, setStats] = useState<StatisticsOverview | null>(null);
  const [recentAlerts, setRecentAlerts] = useState<AlertEvent[]>([]);
  const [cameras, setCameras] = useState<Camera[]>([]);
  const [selectedCamera, setSelectedCamera] = useState<Camera | null>(null);
  const [videoModal, setVideoModal] = useState(false);
  const [loading, setLoading] = useState(false);
  const [suppressedList, setSuppressedList] = useState<StormSuppressedCamera[]>([]);
  const [api, contextHolder] = notification.useNotification();

  const loadData = async () => {
    setLoading(true);
    try {
      const [overviewRes, recentRes, camerasRes]: any = await Promise.all([
        statisticsApi.overview(),
        alertApi.recent(10),
        cameraApi.list(),
      ]);
      if (overviewRes.code === 200) setStats(overviewRes.data);
      if (recentRes.code === 200) setRecentAlerts(recentRes.data);
      if (camerasRes.code === 200) setCameras(camerasRes.data.slice(0, 4));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    loadStormStatus();

    const unsub = wsService.onAlert((alert) => {
      api.open({
        message: `🚨 ${EVENT_TYPE_LABELS[alert.eventType] || alert.eventType}`,
        description: `摄像头: ${alert.cameraName}\n时间: ${new Date(alert.eventTime).toLocaleString()}`,
        duration: 8,
        type: alert.eventLevel >= 3 ? 'error' : 'warning',
        placement: 'topRight',
      });
      setRecentAlerts((prev) => [alert as any, ...prev].slice(0, 10));
    });

    const unsubStormAlert = wsService.onStormAlert((data) => {
      console.warn('[STORM] 大屏告警风暴触发:', data);
      addStormSuppressed(data);
      loadStormStatus();
      api.open({
        message: `⚡ 告警风暴检测: ${data.cameraName || '摄像头' + data.cameraId}`,
        description: data.message || '已自动屏蔽该摄像头告警',
        duration: 0,
        type: 'error',
        placement: 'topLeft',
      });
    });

    const unsubStormRecovery = wsService.onStormRecovery((data) => {
      console.info('[STORM] 大屏告警风暴恢复:', data);
      removeStormSuppressed(Number(data.cameraId));
      loadStormStatus();
      api.open({
        message: `✅ 风暴恢复: ${data.cameraName || '摄像头' + data.cameraId}`,
        description: data.message || '告警风暴抑制已解除',
        duration: 8,
        type: 'success',
        placement: 'topLeft',
      });
    });

    return () => { unsub(); unsubStormAlert(); unsubStormRecovery(); };
  }, []);

  const loadStormStatus = async () => {
    try {
      const res: any = await alertDedupApi.getStatus();
      if (res.code === 200 && res.data?.suppressedCameras) {
        setSuppressedList(res.data.suppressedCameras);
        setStormSuppressedList(res.data.suppressedCameras);
      }
    } catch (e) {
      console.warn('加载风暴抑制状态失败:', e);
    }
  };

  const handleReleaseSuppression = async (cameraId: number) => {
    try {
      const res: any = await alertDedupApi.releaseSuppression(cameraId);
      if (res.code === 200) {
        removeStormSuppressed(cameraId);
        loadStormStatus();
        notification.success({ message: '已手动解除风暴抑制' });
      }
    } catch (e) {
      notification.error({ message: '解除失败' });
    }
  };

  const getColorByLevel = (level: number) => {
    const colors: Record<number, string> = {
      1: 'default',
      2: 'gold',
      3: 'red',
      4: 'magenta',
    };
    return colors[level] || 'default';
  };

  return (
    <div>
      {contextHolder}

      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ margin: 0, fontSize: 20 }}>监控大屏</h2>
        <Button icon={<ReloadOutlined />} onClick={() => { loadData(); loadStormStatus(); }} loading={loading}>
          刷新
        </Button>
      </div>

      {suppressedList.length > 0 && (
        <Alert
          type="error"
          showIcon
          icon={<ThunderboltOutlined />}
          style={{ marginBottom: 16, borderRadius: 8 }}
          message={`告警风暴抑制中（${suppressedList.length}个摄像头）`}
          description={
            <div>
              {suppressedList.map((s) => (
                <div key={s.cameraId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px 0' }}>
                  <span>
                    <strong>{s.cameraName}</strong>（ID: {s.cameraId}）- 有效期至 {new Date(s.suppressedUntil).toLocaleString()}，已屏蔽 {s.suppressedCount} 条
                  </span>
                  <Button size="small" type="primary" danger onClick={() => handleReleaseSuppression(s.cameraId)}>
                    手动解除
                  </Button>
                </div>
              ))}
            </div>
          }
          closable
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%)' }}>
            <Statistic
              title="在线摄像头"
              value={stats?.camera?.online || 0}
              suffix={`/ ${stats?.camera?.total || 0}`}
              prefix={<VideoCameraOutlined style={{ color: '#1890ff' }} />}
              valueStyle={{ color: '#1890ff', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #fff1f0 0%, #ffa39e 100%)' }}>
            <Statistic
              title="今日告警"
              value={stats?.alert?.todayCount || 0}
              prefix={<WarningOutlined style={{ color: '#ff4d4f' }} />}
              valueStyle={{ color: '#ff4d4f', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #fff7e6 0%, #ffd591 100%)' }}>
            <Statistic
              title="待处理告警"
              value={stats?.alert?.pendingCount || 0}
              prefix={<ExclamationCircleOutlined style={{ color: '#fa8c16' }} />}
              valueStyle={{ color: '#fa8c16', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #f6ffed 0%, #b7eb8f 100%)' }}>
            <Statistic
              title="处理中工单"
              value={stats?.workOrder?.processing || 0}
              prefix={<FileTextOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a', fontWeight: 700 }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={16}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <CarOutlined /> 实时视频监控
              </span>
            }
            extra={
              <Button type="link" onClick={() => navigate('/cameras')}>
                查看全部摄像头
              </Button>
            }
            style={{ borderRadius: 8 }}
          >
            <Row gutter={[12, 12]}>
              {cameras.length === 0 ? (
                <Col span={24} style={{ textAlign: 'center', padding: 40, color: '#999' }}>
                  <VideoCameraOutlined style={{ fontSize: 48, marginBottom: 12 }} />
                  <div>暂无摄像头数据</div>
                </Col>
              ) : (
                cameras.map((camera) => (
                  <Col xs={24} md={12} key={camera.id}>
                    <div onClick={() => {
                      setSelectedCamera(camera);
                      setVideoModal(true);
                    }} style={{ cursor: 'pointer' }}>
                      <VideoPlayer
                        url={camera.streamUrl}
                        cameraId={camera.id}
                        cameraName={`${camera.cameraName}${camera.onlineStatus === 0 ? ' (离线)' : ''}`}
                        height={200}
                        showControls={false}
                      />
                    </div>
                  </Col>
                ))
              )}
            </Row>
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <SafetyOutlined /> 最新告警
              </span>
            }
            extra={
              <Button type="link" onClick={() => navigate('/alerts')}>
                全部告警
              </Button>
            }
            style={{ borderRadius: 8, height: '100%' }}
            bodyStyle={{ padding: 0 }}
          >
            <List
              itemLayout="horizontal"
              dataSource={recentAlerts}
              locale={{ emptyText: '暂无告警' }}
              renderItem={(item) => (
                <List.Item
                  style={{ padding: '12px 16px', cursor: 'pointer' }}
                  onClick={() => navigate('/alerts')}
                >
                  <List.Item.Meta
                    avatar={
                      <Tag
                        color={getColorByLevel(item.eventLevel)}
                        style={{ minWidth: 60, textAlign: 'center', padding: '4px 8px', borderRadius: 4 }}
                      >
                        {EVENT_LEVEL_LABELS[item.eventLevel]}
                      </Tag>
                    }
                    title={
                      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span style={{ fontWeight: 500 }}>
                          {EVENT_TYPE_LABELS[item.eventType] || item.eventType}
                        </span>
                        <span style={{ color: '#999', fontSize: 12 }}>
                          {new Date(item.eventTime).toLocaleTimeString()}
                        </span>
                      </div>
                    }
                    description={
                      <div style={{ fontSize: 12, color: '#666' }}>
                        <div>📍 {item.cameraName}</div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                          <Tag color={item.alertStatus === 0 ? 'processing' : 'success'}>
                            {ALERT_STATUS_LABELS[item.alertStatus]}
                          </Tag>
                          <span>置信度: {(item.confidence * 100).toFixed(0)}%</span>
                        </div>
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
        title={selectedCamera?.cameraName}
        open={videoModal}
        onCancel={() => setVideoModal(false)}
        footer={null}
        width={1200}
      >
        {selectedCamera && (
          <Row gutter={16}>
            <Col span={selectedCamera.ptzEnabled === 1 ? 16 : 24}>
              <VideoPlayer
                url={selectedCamera.streamUrl}
                cameraId={selectedCamera.id}
                cameraName={selectedCamera.cameraName}
                height={500}
                showControls
              />
            </Col>
            {selectedCamera.ptzEnabled === 1 && (
              <Col span={8}>
                <PtzPanel cameraId={selectedCamera.id} ptzEnabled={selectedCamera.ptzEnabled} />
                <div style={{ height: 12 }} />
                <PtzCruisePanel cameraId={selectedCamera.id} ptzEnabled={selectedCamera.ptzEnabled} />
              </Col>
            )}
          </Row>
        )}
      </Modal>
    </div>
  );
};

export default Dashboard;
