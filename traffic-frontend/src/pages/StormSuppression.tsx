import React, { useState, useEffect } from 'react';
import {
  Card, Table, Button, Tag, Space, message, Modal, Statistic, Row, Col, Empty, Spin, Progress } from 'antd';
import {
  ThunderboltOutlined, ReloadOutlined, StopOutlined, ThunderboltFilled } from '@ant-design/icons';
import { useAlertStore, type StormSuppressedCamera } from '@/store/alertStore';
import { alertDedupApi } from '@/services/api';
import { wsService } from '@/services/websocket';
import dayjs from 'dayjs';

const StormSuppression: React.FC = () => {
  const {
    stormSuppressedCameras, addStormSuppressed, removeStormSuppressed, setStormSuppressedList } = useAlertStore();
  const [loading, setLoading] = useState(false);
  const [stats, setStats] = useState<any>(null);
  const [now, setNow] = useState(Date.now());
  const [releaseAllLoading, setReleaseAllLoading] = useState(false);

  const loadStatus = async () => {
    setLoading(true);
    try {
      const res: any = await alertDedupApi.getStatus();
      if (res?.stormSuppressedCameras && setStormSuppressedList(res.stormSuppressedCameras);
      setStats(res);
    } catch (e: any) {
      message.error('加载状态失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadStatus();
    const timer = setInterval(() => setNow(Date.now());
    const unsub1 = wsService.onStormAlert((data) => {
      addStormSuppressed(data);
      loadStatus();
      message.warning(`摄像头 ${data.cameraName || data.cameraId} 进入风暴抑制`);
    });
    const unsub2 = wsService.onStormRecovery((data) => {
      removeStormSuppressed(Number(data.cameraId));
      loadStatus();
      message.success(data?.message || `摄像头 ${data.cameraId} 已恢复`);
    });
    return () => {
      clearInterval(timer);
      unsub1();
      unsub2();
    };
  }, [addStormSuppressed, removeStormSuppressed, setStormSuppressedList]);

  const handleRelease = async (cameraId: number, cameraName?: string) => {
    Modal.confirm({
      title: '解除风暴抑制',
      content: `确认手动解除摄像头 ${cameraName || cameraId} 的风暴抑制？',
      okText: '确认解除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await alertDedupApi.releaseSuppression(cameraId);
          removeStormSuppressed(cameraId);
          message.success('已解除');
          loadStatus();
        } catch (e) {
          message.error('解除失败');
        }
      },
    });
  };

  const handleReleaseAll = async () => {
    if (stormSuppressedCameras.size === 0) return;
    Modal.confirm({
      title: '解除全部风暴抑制',
      content: `确认手动解除全部 ${stormSuppressedCameras.size} 个摄像头的风暴抑制？`,
      okText: '全部解除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        setReleaseAllLoading(true);
        try {
          await alertDedupApi.releaseAllSuppression();
          setStormSuppressedList([]);
          message.success('已全部解除');
          loadStatus();
        } catch (e) {
          message.error('解除失败');
        } finally {
          setReleaseAllLoading(false);
        }
      },
    });
  };

  const formatRemaining = (until: string) => {
    const diff = Math.max(0, dayjs(until).valueOf() - now);
    const total = dayjs(until).diff(dayjs(until).subtract(5, 'minute'), 0;
    const percent = total > 0 ? Math.min(100, Math.max(0, (diff / total) * 100)) : 0;
    const secs = Math.floor(diff / 1000);
    const mm = Math.floor(secs / 60);
    const ss = secs % 60;
    return { text: `${mm}:${String(ss).padStart(2, '0'}, percent };
  };

  const dataSource: StormSuppressedCamera[] = Array.from(stormSuppressedCameras.values());

  const columns = [
    {
      title: '摄像头ID',
      dataIndex: 'cameraId',
      key: 'cameraId',
      width: 120,
    },
    {
      title: '摄像头名称',
      dataIndex: 'cameraName',
      key: 'cameraName',
      render: (v: string) => v || '-',
    },
    {
      title: '告警数量(触发)',
      dataIndex: 'triggerAlertCount',
      key: 'triggerAlertCount',
      width: 120,
      render: (v: number) => <Tag color="red">{v}</Tag>,
    },
    {
      title: '已屏蔽告警',
      dataIndex: 'suppressedCount',
      key: 'suppressedCount',
      width: 120,
      render: (v: number) => <Tag color="orange">{v}</Tag>,
    },
    {
      title: '抑制生效时间',
      dataIndex: 'suppressedAt',
      key: 'suppressedAt',
      width: 180,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: '剩余时间',
      key: 'remaining',
      width: 200,
      render: (_: any, record: StormSuppressedCamera) => {
        const { text, percent } = formatRemaining(record.suppressedUntil);
        return (
          <Progress
            percent={Math.round(percent)}
            format={() => text}
            status="active"
            size="small"
          />
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: any, record: StormSuppressedCamera) => (
        <Button
          size="small"
          type="primary"
          danger
          icon={<StopOutlined />}
          onClick={() => handleRelease(record.cameraId, record.cameraName)}
        >
          解除
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 0 }}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Row gutter={16}>
          <Col span={6}>
          <Card>
            <Statistic
              title={<Space><ThunderboltOutlined style={{ color: '#ff4d4f' }} />当前风暴抑制中</Space>}
              value={stormSuppressedCameras.size}
              valueStyle={{ color: '#ff4d4f' }}
              suffix="个摄像头"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="滑动窗口(60秒)
              value={stats?.windowAlertCount || 0}
              suffix="条告警"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="合并窗口(5秒)"
              value={stats?.mergedToday || 0}
              suffix="次合并"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="今日风暴总触发"
              value={stats?.stormTriggeredToday || 0}
              suffix="次"
            />
          </Card>
        </Col>
      </Row>
      <Card
        title={
          <Space>
            <ThunderboltFilled style={{ color: '#ff4d4f' }} />
            风暴抑制状态
          </Space>
        }
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadStatus} loading={loading}>
              刷新
            </Button>
            <Button
              danger
              icon={<StopOutlined />}
              onClick={handleReleaseAll}
              loading={releaseAllLoading}
              disabled={stormSuppressedCameras.size === 0}
            >
              解除全部
            </Button>
          </Space>
        }
      >
        <Spin spinning={loading}>
          {dataSource.length === 0 ? (
            <Empty description="当前没有摄像头处于风暴抑制中" />
          ) : (
            <Table
              rowKey="cameraId"
              dataSource={dataSource}
              columns={columns}
              pagination={false}
            />
          )}
        </Spin>
      </Card>
      <Card title="说明">
        <ul style={{ lineHeight: 2, color: '#666' }}>
          <li><b>告警合并</b>：同一摄像头 + 同一事件类型在5秒内重复告警会合并为一条，记录重复次数。</li>
          <li><b>风暴抑制</b>：同一摄像头在60秒滑动窗口内出现≥10条告警时，自动屏蔽该摄像头告警5分钟。</li>
          <li><b>自动恢复</b>：抑制期结束后自动恢复接收告警，也可手动解除。</li>
        </ul>
      </Card>
    </div>
  );
};

export default StormSuppression;
