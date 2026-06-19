import React, { useEffect, useState } from 'react';
import { Modal, Tabs, Empty, Spin, Card, Descriptions, Tag, Space, Row, Col, Statistic, Select } from 'antd';
import {
  EnvironmentOutlined,
  ClockCircleOutlined,
  CarOutlined,
  VideoCameraOutlined,
  WarningOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { globalTrackApi } from '@/services/api';
import TrackReplayMap from './TrackReplayMap';
import type { AlertEvent, GlobalTrack, TrackPoint } from '@/types';

const { TabPane } = Tabs;
const { Option } = Select;

interface EventReplayModalProps {
  open: boolean;
  event: AlertEvent | null;
  onClose: () => void;
}

interface ReplayData {
  event: AlertEvent;
  tracks: GlobalTrack[];
  trackPointsMap: Record<number, TrackPoint[]>;
  beforeMinutes: number;
  startTime?: string;
  endTime?: string;
}

const EventReplayModal: React.FC<EventReplayModalProps> = ({ open, event, onClose }) => {
  const [loading, setLoading] = useState(false);
  const [replayData, setReplayData] = useState<ReplayData | null>(null);
  const [beforeMinutes, setBeforeMinutes] = useState(5);
  const [activeTab, setActiveTab] = useState('map');

  const loadReplayData = async (eventId: number, minutes: number) => {
    setLoading(true);
    try {
      const res: any = await globalTrackApi.eventReplay(eventId, minutes);
      if (res.code === 200 && res.data) {
        const { event: evt, tracks, trackPointsMap } = res.data;

        const pointsMap: Record<number, TrackPoint[]> = {};
        if (trackPointsMap) {
          Object.keys(trackPointsMap).forEach((key) => {
            pointsMap[Number(key)] = trackPointsMap[key];
          });
        }

        setReplayData({
          event: evt,
          tracks: tracks || [],
          trackPointsMap: pointsMap,
          beforeMinutes: minutes,
          startTime: res.data.startTime,
          endTime: res.data.endTime,
        });
      }
    } catch (e) {
      console.error('加载轨迹回放数据失败:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open && event) {
      loadReplayData(event.id, beforeMinutes);
    } else {
      setReplayData(null);
    }
  }, [open, event?.id]);

  const handleMinutesChange = (minutes: number) => {
    setBeforeMinutes(minutes);
    if (event) {
      loadReplayData(event.id, minutes);
    }
  };

  const getTargetClassLabel = (cls?: string) => {
    const labels: Record<string, string> = {
      car: '轿车',
      truck: '卡车',
      bus: '公交车',
      person: '行人',
      motorcycle: '摩托车',
      bicycle: '自行车',
    };
    return labels[cls || ''] || cls || '-';
  };

  const getTotalPoints = () => {
    if (!replayData) return 0;
    return Object.values(replayData.trackPointsMap).reduce(
      (sum, points) => sum + points.length,
      0
    );
  };

  const hasGpsData = () => {
    if (!replayData) return false;
    return Object.values(replayData.trackPointsMap).some((points) =>
      points.some((p) => p.longitude != null && p.latitude != null)
    );
  };

  return (
    <Modal
      title={
        <Space>
          <PlayCircleOutlined style={{ color: '#1890ff' }} />
          <span>事件复盘 - 轨迹回放</span>
          {event && (
            <Tag color="red" style={{ marginLeft: 8 }}>
              {event.eventNo}
            </Tag>
          )}
        </Space>
      }
      open={open}
      onCancel={onClose}
      width={1080}
      footer={null}
      destroyOnClose
    >
      <Spin spinning={loading}>
        {event && (
          <div>
            <Card size="small" style={{ marginBottom: 12, borderRadius: 8 }}>
              <Row gutter={[16, 8]} align="middle">
                <Col flex="auto">
                  <Descriptions size="small" column={3} bordered={false}>
                    <Descriptions.Item
                      label={
                        <Space size={4}>
                          <WarningOutlined style={{ color: '#ff4d4f' }} />
                          事件类型
                        </Space>
                      }
                    >
                      <Tag color={event.eventType === 'ACCIDENT' ? 'red' : 'orange'}>
                        {eventTypeText(event.eventType)}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item
                      label={
                        <Space size={4}>
                          <VideoCameraOutlined style={{ color: '#1890ff' }} />
                          摄像头
                        </Space>
                      }
                    >
                      {event.cameraName}
                    </Descriptions.Item>
                    <Descriptions.Item
                      label={
                        <Space size={4}>
                          <ClockCircleOutlined style={{ color: '#52c41a' }} />
                          事件时间
                        </Space>
                      }
                    >
                      {event.eventTime}
                    </Descriptions.Item>
                  </Descriptions>
                </Col>
                <Col flex="none">
                  <Space>
                    <span style={{ fontSize: 13, color: '#666' }}>回放时长:</span>
                    <Select
                      value={beforeMinutes}
                      onChange={handleMinutesChange}
                      style={{ width: 110 }}
                      size="small"
                    >
                      <Option value={1}>前 1 分钟</Option>
                      <Option value={2}>前 2 分钟</Option>
                      <Option value={5}>前 5 分钟</Option>
                      <Option value={10}>前 10 分钟</Option>
                      <Option value={15}>前 15 分钟</Option>
                      <Option value={30}>前 30 分钟</Option>
                    </Select>
                  </Space>
                </Col>
              </Row>
            </Card>

            {replayData && replayData.tracks.length > 0 ? (
              <>
                {hasGpsData() ? (
                  <TrackReplayMap
                    event={replayData.event}
                    tracks={replayData.tracks}
                    trackPointsMap={replayData.trackPointsMap}
                    beforeMinutes={replayData.beforeMinutes}
                    height={420}
                  />
                ) : (
                  <Card
                    size="small"
                    style={{ height: 420, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                  >
                    <Empty description="暂无GPS轨迹坐标数据，仅像素轨迹可用" />
                  </Card>
                )}

                <Card size="small" style={{ marginTop: 12, borderRadius: 8 }}>
                  <Tabs activeKey={activeTab} onChange={setActiveTab} size="small">
                    <TabPane tab="轨迹信息" key="tracks">
                      <Row gutter={[12, 12]}>
                        {replayData.tracks.map((track, idx) => {
                          const points = replayData.trackPointsMap[track.id] || [];
                          return (
                            <Col span={12} key={track.id}>
                              <Card
                                size="small"
                                title={
                                  <Space>
                                    <CarOutlined
                                      style={{
                                        color: [
                                          '#1890ff',
                                          '#52c41a',
                                          '#faad14',
                                          '#eb2f96',
                                        ][idx % 4],
                                      }}
                                    />
                                    <span style={{ fontFamily: 'monospace', fontSize: 13 }}>
                                      {track.trackNo}
                                    </span>
                                    <Tag color="blue" style={{ marginLeft: 4 }}>
                                      {getTargetClassLabel(track.targetClass)}
                                    </Tag>
                                  </Space>
                                }
                                style={{ borderRadius: 6 }}
                              >
                                <Row gutter={[12, 8]}>
                                  <Col span={12}>
                                    <Statistic
                                      title="轨迹点数"
                                      value={points.length}
                                      suffix="点"
                                      valueStyle={{ fontSize: 16 }}
                                    />
                                  </Col>
                                  <Col span={12}>
                                    <Statistic
                                      title="平均速度"
                                      value={track.avgSpeed || 0}
                                      suffix="km/h"
                                      valueStyle={{ fontSize: 16 }}
                                    />
                                  </Col>
                                  <Col span={12}>
                                    <Statistic
                                      title="行驶里程"
                                      value={track.totalDistance || 0}
                                      suffix="m"
                                      valueStyle={{ fontSize: 16 }}
                                    />
                                  </Col>
                                  <Col span={12}>
                                    <Statistic
                                      title="车牌号"
                                      value={track.licensePlate || '未识别'}
                                      valueStyle={{ fontSize: 14, fontFamily: 'monospace' }}
                                    />
                                  </Col>
                                </Row>
                                {track.snapshotUrl && (
                                  <div style={{ marginTop: 8 }}>
                                    <img
                                      src={track.snapshotUrl}
                                      alt="snapshot"
                                      style={{
                                        width: '100%',
                                        height: 80,
                                        objectFit: 'cover',
                                        borderRadius: 4,
                                      }}
                                    />
                                  </div>
                                )}
                              </Card>
                            </Col>
                          );
                        })}
                      </Row>
                    </TabPane>

                    <TabPane tab="事件详情" key="event">
                      <Descriptions column={2} bordered size="small">
                        <Descriptions.Item label="事件编号">
                          {replayData.event.eventNo}
                        </Descriptions.Item>
                        <Descriptions.Item label="事件等级">
                          <Tag color={levelColor(replayData.event.eventLevel)}>
                            {levelText(replayData.event.eventLevel)}
                          </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="置信度">
                          {(replayData.event.confidence * 100).toFixed(1)}%
                        </Descriptions.Item>
                        <Descriptions.Item label="状态">
                          {statusText(replayData.event.alertStatus)}
                        </Descriptions.Item>
                        <Descriptions.Item label="位置" span={2}>
                          <Space>
                            <EnvironmentOutlined />
                            {replayData.event.location ||
                              (replayData.event.longitude
                                ? `${replayData.event.longitude}, ${replayData.event.latitude}`
                                : '-')}
                          </Space>
                        </Descriptions.Item>
                        {replayData.event.description && (
                          <Descriptions.Item label="描述" span={2}>
                            {replayData.event.description}
                          </Descriptions.Item>
                        )}
                      </Descriptions>
                    </TabPane>

                    <TabPane tab={`统计 (${getTotalPoints()}个点)`} key="stats">
                      <Row gutter={[16, 12]}>
                        <Col span={6}>
                          <Card size="small" style={{ borderRadius: 6 }}>
                            <Statistic
                              title="关联目标"
                              value={replayData.tracks.length}
                              suffix="个"
                              valueStyle={{ color: '#1890ff' }}
                            />
                          </Card>
                        </Col>
                        <Col span={6}>
                          <Card size="small" style={{ borderRadius: 6 }}>
                            <Statistic
                              title="轨迹点数"
                              value={getTotalPoints()}
                              suffix="点"
                              valueStyle={{ color: '#52c41a' }}
                            />
                          </Card>
                        </Col>
                        <Col span={6}>
                          <Card size="small" style={{ borderRadius: 6 }}>
                            <Statistic
                              title="回放时长"
                              value={replayData.beforeMinutes}
                              suffix="分钟"
                              valueStyle={{ color: '#faad14' }}
                            />
                          </Card>
                        </Col>
                        <Col span={6}>
                          <Card size="small" style={{ borderRadius: 6 }}>
                            <Statistic
                              title="摄像头数"
                              value={
                                new Set(
                                  Object.values(replayData.trackPointsMap).flat().map((p) => p.cameraId)
                                ).size
                              }
                              suffix="个"
                              valueStyle={{ color: '#722ed1' }}
                            />
                          </Card>
                        </Col>
                      </Row>
                    </TabPane>
                  </Tabs>
                </Card>
              </>
            ) : (
              <Card size="small" style={{ height: 400, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Empty
                  description={
                    <div style={{ textAlign: 'center' }}>
                      <p>该事件暂未关联轨迹数据</p>
                      <p style={{ fontSize: 12, color: '#999' }}>
                        事件发生时未检测到可关联的车辆/行人目标
                      </p>
                    </div>
                  }
                />
              </Card>
            )}
          </div>
        )}
      </Spin>
    </Modal>
  );
};

function eventTypeText(type?: string): string {
  const map: Record<string, string> = {
    ACCIDENT: '交通事故',
    REVERSE: '车辆逆行',
    DEBRIS: '路面抛洒物',
    INTRUSION: '区域入侵',
    PARKING: '违规停车',
    PEDESTRIAN: '行人闯入',
  };
  return map[type || ''] || type || '未知';
}

function levelColor(level?: number): string {
  const colors: Record<number, string> = {
    1: 'default',
    2: 'gold',
    3: 'red',
    4: 'magenta',
  };
  return colors[level || 1] || 'default';
}

function levelText(level?: number): string {
  const map: Record<number, string> = {
    1: '一般',
    2: '严重',
    3: '紧急',
    4: '特急',
  };
  return map[level || 1] || '一般';
}

function statusText(status?: number): string {
  const map: Record<number, string> = {
    0: '待处理',
    1: '已处理',
    2: '误报',
  };
  return map[status || 0] || '未知';
}

export default EventReplayModal;
