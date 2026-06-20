import React, { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Tag,
  Input,
  Select,
  Card,
  Form,
  DatePicker,
  message,
  Drawer,
  Descriptions,
  Row,
  Col,
  Statistic,
  Modal,
  InputNumber,
  Switch,
  Divider,
  Tooltip,
  Alert as AntdAlert,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  SoundOutlined,
  SettingOutlined,
  SyncOutlined,
  AudioOutlined,
  BellOutlined,
  SafetyOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import { audioEventApi } from '@/services/api';
import {
  AUDIO_EVENT_TYPE_LABELS,
  AUDIO_EVENT_TYPE_COLORS,
  type AudioEvent,
} from '@/types';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;
const { Option } = Select;
const { TextArea } = Input;

interface AudioStatistics {
  totalCount: number;
  todayCount: number;
  hornCount: number;
  collisionCount: number;
  sirenCount: number;
  abnormalCount: number;
  linkedCount: number;
  standaloneCount: number;
}

const AudioEvents: React.FC = () => {
  const [data, setData] = useState<AudioEvent[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [detailDrawer, setDetailDrawer] = useState(false);
  const [currentRecord, setCurrentRecord] = useState<AudioEvent | null>(null);
  const [statistics, setStatistics] = useState<AudioStatistics | null>(null);
  const [configModal, setConfigModal] = useState(false);
  const [configForm] = Form.useForm();
  const [configLoading, setConfigLoading] = useState(false);
  const [currentConfig, setCurrentConfig] = useState<any>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const params: any = {
        ...values,
        current,
        size: pageSize,
      };
      if (values.timeRange && values.timeRange.length === 2) {
        params.startTime = values.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = values.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      delete params.timeRange;
      const res: any = await audioEventApi.page(params);
      if (res.code === 200) {
        setData(res.data.records || []);
        setTotal(res.data.total || 0);
      }
    } catch (e: any) {
      message.error(`加载失败: ${e.message || '未知错误'}`);
    } finally {
      setLoading(false);
    }
  };

  const loadStatistics = async () => {
    try {
      const res: any = await audioEventApi.statistics();
      if (res.code === 200) {
        setStatistics(res.data);
      }
    } catch (e) {
      // ignore
    }
  };

  useEffect(() => {
    loadData();
    loadStatistics();
  }, [current, pageSize]);

  const handleSearch = () => {
    setCurrent(1);
    setTimeout(() => loadData(), 0);
  };

  const handleReset = () => {
    searchForm.resetFields();
    setCurrent(1);
    setTimeout(() => loadData(), 0);
  };

  const handleViewDetail = (record: AudioEvent) => {
    setCurrentRecord(record);
    setDetailDrawer(true);
  };

  const handleOpenConfig = async () => {
    setConfigModal(true);
    try {
      setConfigLoading(true);
      const res: any = await audioEventApi.getConfig();
      if (res.code === 200 && res.data) {
        const d: any = res.data;
        const th: any = d.thresholds || {};
        configForm.setFieldsValue({
          audioDetectionEnabled: d.enabled,
          micArrayStrategy: d.micArrayStrategy,
          channels: d.channels,
          sampleRate: d.sampleRate,
          hornMinDb: th.horn?.minDb ?? d.hornMinDb,
          hornDbAboveAmbient: th.horn?.dbAboveAmbient ?? d.hornDbAboveAmbient,
          hornMinDuration: th.horn?.minDuration ?? d.hornMinDuration,
          hornBandRatio: th.horn?.bandRatio ?? d.hornBandRatio,
          collisionMinDb: th.collision?.minDb ?? d.collisionMinDb,
          collisionDbAboveAmbient: th.collision?.dbAboveAmbient ?? d.collisionDbAboveAmbient,
          collisionImpulseMaxRise: th.collision?.impulseMaxRise ?? d.collisionImpulseMaxRise,
          collisionRiseFallRatio: th.collision?.riseFallRatio ?? d.collisionRiseFallRatio,
          sirenMinDuration: th.siren?.minDuration ?? d.sirenMinDuration,
          sirenDbAboveAmbient: th.siren?.dbAboveAmbient ?? d.sirenDbAboveAmbient,
          sirenBandRatio: th.siren?.bandRatio ?? d.sirenBandRatio,
          eventCooldown: th.general?.cooldownSeconds ?? d.eventCooldown,
          ambientUpdateAlpha: th.general?.ambientUpdateAlpha ?? d.ambientUpdateAlpha,
        });
        setCurrentConfig(d);
        if (d.fromFallback === true || d.aiEngineAvailable === false) {
          message.warning('AI引擎音频检测未就绪，展示默认参数（启动AI引擎后可实时生效）');
        }
      }
    } catch (e: any) {
      message.error(`加载配置失败: ${e.message || '未知错误'}`);
    } finally {
      setConfigLoading(false);
    }
  };

  const columns: ColumnsType<AudioEvent> = [
    {
      title: '事件编号',
      dataIndex: 'eventNo',
      key: 'eventNo',
      width: 180,
      ellipsis: true,
    },
    {
      title: '事件类型',
      dataIndex: 'eventType',
      key: 'eventType',
      width: 120,
      render: (type: string) => (
        <Tag color={AUDIO_EVENT_TYPE_COLORS[type as keyof typeof AUDIO_EVENT_TYPE_COLORS] || '#8c8c8c'}>
          <SoundOutlined />{' '}
          {AUDIO_EVENT_TYPE_LABELS[type as keyof typeof AUDIO_EVENT_TYPE_LABELS] || type}
        </Tag>
      ),
    },
    {
      title: '摄像头',
      dataIndex: 'cameraName',
      key: 'cameraName',
      width: 150,
      ellipsis: true,
      render: (name, record) => name || `摄像头#${record.cameraId || '-'}`,
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      key: 'confidence',
      width: 90,
      render: (val: number) => (
        <span style={{ color: val >= 0.85 ? '#cf1322' : val >= 0.7 ? '#fa8c16' : '#52c41a', fontWeight: 500 }}>
          {val ? `${(val * 100).toFixed(1)}%` : '-'}
        </span>
      ),
    },
    {
      title: '时长',
      dataIndex: 'duration',
      key: 'duration',
      width: 90,
      render: (val: number) => (val != null ? `${val.toFixed(1)}s` : '-'),
    },
    {
      title: '峰值分贝',
      dataIndex: 'peakDb',
      key: 'peakDb',
      width: 100,
      render: (val: number, record: AudioEvent) => (
        <Space>
          <span>{val != null ? `${val.toFixed(1)} dB` : '-'}</span>
          {record.ambientDb != null && (
            <Tag color="blue" style={{ fontSize: 11 }}>
              底噪 {Number(record.ambientDb).toFixed(0)}
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: '主频',
      dataIndex: 'dominantFreq',
      key: 'dominantFreq',
      width: 100,
      render: (val: number) => (val != null ? `${val.toFixed(0)} Hz` : '-'),
    },
    {
      title: '关联告警',
      dataIndex: 'linkedAlertEventId',
      key: 'linkedAlertEventId',
      width: 100,
      render: (val: any, record: AudioEvent) =>
        val ? (
          <Tag icon={<LinkOutlined />} color="green">
            已关联 #{val}
          </Tag>
        ) : (
          <Tag color="default">独立事件</Tag>
        ),
    },
    {
      title: '发生时间',
      dataIndex: 'eventTime',
      key: 'eventTime',
      width: 170,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-'),
      sorter: (a, b) => dayjs(a.eventTime).valueOf() - dayjs(b.eventTime).valueOf(),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      fixed: 'right',
      render: (_: any, record: AudioEvent) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleViewDetail(record)}>
            详情
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="音频事件总数"
              value={statistics?.totalCount || 0}
              prefix={<SoundOutlined style={{ color: '#1890ff' }} />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="今日检测"
              value={statistics?.todayCount || 0}
              prefix={<AudioOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="碰撞声(辅助事故)"
              value={statistics?.collisionCount || 0}
              prefix={<SafetyOutlined style={{ color: '#cf1322' }} />}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="关联视频告警"
              value={statistics?.linkedCount || 0}
              prefix={<BellOutlined style={{ color: '#722ed1' }} />}
              valueStyle={{ color: '#722ed1' }}
              suffix={
                <span style={{ fontSize: 14, color: '#8c8c8c' }}>
                  / {statistics?.totalCount || 0}
                </span>
              }
            />
          </Card>
        </Col>
      </Row>

      <Card>
        <Form
          form={searchForm}
          layout="inline"
          onFinish={handleSearch}
          style={{ marginBottom: 16, gap: 12, rowGap: 12 }}
        >
          <Form.Item name="keyword" label="关键字">
            <Input placeholder="事件编号/描述" allowClear style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="eventType" label="事件类型">
            <Select placeholder="全部" allowClear style={{ width: 150 }}>
              {Object.entries(AUDIO_EVENT_TYPE_LABELS).map(([k, v]) => (
                <Option key={k} value={k}>
                  {v}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="cameraId" label="摄像头ID">
            <Input placeholder="摄像头ID" allowClear style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="timeRange" label="时间范围">
            <RangePicker showTime style={{ width: 350 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} htmlType="submit">
                查询
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
              <Tooltip title="音频检测参数配置">
                <Button icon={<SettingOutlined />} onClick={handleOpenConfig} type="dashed">
                  参数配置
                </Button>
              </Tooltip>
            </Space>
          </Form.Item>
        </Form>

        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1200 }}
          pagination={{
            current,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setCurrent(p);
              setPageSize(s);
            },
          }}
        />
      </Card>

      <Drawer
        title={
          <Space>
            <AudioOutlined />
            音频事件详情
            {currentRecord && (
              <Tag color={AUDIO_EVENT_TYPE_COLORS[currentRecord.eventType as keyof typeof AUDIO_EVENT_TYPE_COLORS]}>
                {AUDIO_EVENT_TYPE_LABELS[currentRecord.eventType as keyof typeof AUDIO_EVENT_TYPE_LABELS]}
              </Tag>
            )}
          </Space>
        }
        width={640}
        open={detailDrawer}
        onClose={() => setDetailDrawer(false)}
        extra={
          <Button type="primary" icon={<SyncOutlined />} onClick={() => loadData()}>
            刷新
          </Button>
        }
      >
        {currentRecord && (
          <>
            <Descriptions bordered column={1} size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="事件编号">{currentRecord.eventNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="事件类型">
                <Tag color={AUDIO_EVENT_TYPE_COLORS[currentRecord.eventType as keyof typeof AUDIO_EVENT_TYPE_COLORS]}>
                  {AUDIO_EVENT_TYPE_LABELS[currentRecord.eventType as keyof typeof AUDIO_EVENT_TYPE_LABELS]}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="摄像头">
                {currentRecord.cameraName || `摄像头#${currentRecord.cameraId || '-'}`}
              </Descriptions.Item>
              <Descriptions.Item label="发生时间">
                {currentRecord.eventTime ? dayjs(currentRecord.eventTime).format('YYYY-MM-DD HH:mm:ss.SSS') : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="位置/路段">{currentRecord.location || '-'}</Descriptions.Item>
            </Descriptions>

            <Divider orientation="left">音频分析参数</Divider>
            <Descriptions bordered column={2} size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="置信度">
                <span
                  style={{
                    color:
                      (currentRecord.confidence || 0) >= 0.85
                        ? '#cf1322'
                        : (currentRecord.confidence || 0) >= 0.7
                          ? '#fa8c16'
                          : '#52c41a',
                    fontWeight: 600,
                  }}
                >
                  {currentRecord.confidence != null ? `${(currentRecord.confidence * 100).toFixed(1)}%` : '-'}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="持续时长">
                {currentRecord.duration != null ? `${currentRecord.duration.toFixed(2)} 秒` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="峰值分贝">
                {currentRecord.peakDb != null ? `${currentRecord.peakDb.toFixed(1)} dB` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="平均分贝">
                {currentRecord.avgDb != null ? `${currentRecord.avgDb.toFixed(1)} dB` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="环境底噪">
                {currentRecord.ambientDb != null ? `${Number(currentRecord.ambientDb).toFixed(1)} dB` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="主频">
                {currentRecord.dominantFreq != null ? `${currentRecord.dominantFreq.toFixed(0)} Hz` : '-'}
              </Descriptions.Item>
            </Descriptions>

            <Divider orientation="left">事件关联</Divider>
            <Descriptions bordered column={1} size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="关联视频告警">
                {currentRecord.linkedAlertEventId ? (
                  <Tag icon={<LinkOutlined />} color="green">
                    已关联告警 #${currentRecord.linkedAlertEventId}
                  </Tag>
                ) : (
                  <Tag color="default">未关联（独立事件）</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="告警状态">
                {currentRecord.alertStatus === 0 || currentRecord.alertStatus == null ? (
                  <Tag color="blue">待处理</Tag>
                ) : currentRecord.alertStatus === 1 ? (
                  <Tag color="orange">处理中</Tag>
                ) : currentRecord.alertStatus === 2 ? (
                  <Tag color="green">已处理</Tag>
                ) : (
                  <Tag color="red">误报/忽略</Tag>
                )}
              </Descriptions.Item>
            </Descriptions>

            <Divider orientation="left">事件描述</Divider>
            <Card size="small" style={{ background: '#fafafa', marginBottom: 16 }}>
              <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>
                {currentRecord.description || '暂无描述'}
              </div>
            </Card>

            {currentRecord.metadata && Object.keys(currentRecord.metadata).length > 0 && (
              <>
                <Divider orientation="left">原始分析数据 (metadata)</Divider>
                <pre
                  style={{
                    background: '#001529',
                    color: '#d6e4ff',
                    padding: 12,
                    borderRadius: 6,
                    fontSize: 12,
                    overflowX: 'auto',
                  }}
                >
                  {JSON.stringify(currentRecord.metadata, null, 2)}
                </pre>
              </>
            )}
          </>
        )}
      </Drawer>

      <Modal
        title={
          <Space>
            <SettingOutlined />
            音频检测参数配置
          </Space>
        }
        open={configModal}
        onCancel={() => setConfigModal(false)}
        width={820}
        footer={null}
      >
        <AntdAlert
          message="配置说明"
          description={
            <div>
              <div>1. 所有参数均可通过环境变量覆盖（以 AUDIO_ 前缀）</div>
              <div>2. 分贝阈值支持双模式：绝对阈值 / 相对环境底噪的增量值（取两者较高者生效）</div>
              <div>3. 修改后立即生效，无需重启服务</div>
            </div>
          }
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />
        <Form
          form={configForm}
          layout="vertical"
          initialValues={{
            audioDetectionEnabled: false,
            micArrayStrategy: 'max_energy',
            channels: 1,
            sampleRate: 16000,
            hornMinDb: 75,
            hornDbAboveAmbient: 15,
            hornMinDuration: 1.5,
            hornBandRatio: 0.3,
            collisionMinDb: 85,
            collisionDbAboveAmbient: 25,
            collisionImpulseMaxRise: 0.5,
            collisionRiseFallRatio: 0.3,
            sirenMinDuration: 2.0,
            sirenDbAboveAmbient: 15,
            sirenBandRatio: 0.4,
            eventCooldown: 30,
            ambientUpdateAlpha: 0.005,
          }}
          onFinish={async (values: any) => {
            try {
              setConfigLoading(true);
              const payload: any = {
                enabled: values.audioDetectionEnabled,
                sampleRate: values.sampleRate,
                channels: values.channels,
                micArrayStrategy: values.micArrayStrategy,
                hornMinDuration: values.hornMinDuration,
                hornMinDb: values.hornMinDb,
                hornDbAboveAmbient: values.hornDbAboveAmbient,
                hornBandRatio: values.hornBandRatio,
                collisionMinDb: values.collisionMinDb,
                collisionDbAboveAmbient: values.collisionDbAboveAmbient,
                collisionImpulseMaxRise: values.collisionImpulseMaxRise,
                collisionRiseFallRatio: values.collisionRiseFallRatio,
                sirenMinDuration: values.sirenMinDuration,
                sirenDbAboveAmbient: values.sirenDbAboveAmbient,
                sirenBandRatio: values.sirenBandRatio,
                eventCooldown: values.eventCooldown,
                ambientUpdateAlpha: values.ambientUpdateAlpha,
              };
              const res: any = await audioEventApi.updateConfig(payload);
              if (res.code === 200) {
                const info = res.data || {};
                if (info.success !== false) {
                  message.success(
                    info.message || `配置已成功应用，更新 ${Object.keys(info.updated || {}).length} 个参数`
                  );
                  setConfigModal(false);
                  loadStatistics();
                } else {
                  message.error(
                    `配置更新失败：${info.error || info.detail || 'AI引擎未响应'}`
                  );
                }
              } else {
                message.error(`配置更新失败：${res.message || '请求错误'}`);
              }
            } catch (e: any) {
              message.error(`配置更新异常：${e.message || '未知错误'}`);
            } finally {
              setConfigLoading(false);
            }
          }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Divider orientation="left">总开关与采样</Divider>
              <Form.Item label="启用音频检测" name="audioDetectionEnabled" valuePropName="checked">
                <Switch checkedChildren="已启用" unCheckedChildren="已关闭" />
              </Form.Item>
              <Form.Item label="麦克风阵列混合策略" name="micArrayStrategy">
                <Select>
                  <Option value="max_energy">最大能量通道选择 (推荐)</Option>
                  <Option value="average">多通道平均</Option>
                  <Option value="delay_and_sum">延迟波束求和 (Delay-and-Sum)</Option>
                </Select>
              </Form.Item>
              <Form.Item label="声道数 (麦克风数)" name="channels">
                <InputNumber min={1} max={32} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="采样率 (Hz)" name="sampleRate">
                <InputNumber min={8000} max={48000} step={8000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>

            <Col span={12}>
              <Divider orientation="left">鸣笛检测阈值</Divider>
              <Form.Item label="鸣笛持续最小时间 (秒)" name="hornMinDuration">
                <InputNumber min={0.3} max={30} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item label="最小分贝 (绝对阈值)" name="hornMinDb">
                    <InputNumber min={40} max={120} step={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item label="高于环境底噪 (dB)" name="hornDbAboveAmbient">
                    <InputNumber min={5} max={60} step={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item label="400-3500Hz频段能量占比 ≥" name="hornBandRatio">
                <InputNumber min={0.1} max={1.0} step={0.05} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider />
          <Row gutter={16}>
            <Col span={12}>
              <Divider orientation="left">碰撞声检测阈值</Divider>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item label="最小分贝 (绝对阈值)" name="collisionMinDb">
                    <InputNumber min={40} max={140} step={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item label="高于环境底噪 (dB)" name="collisionDbAboveAmbient">
                    <InputNumber min={10} max={80} step={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item label="脉冲最大上升时间 (秒)" name="collisionImpulseMaxRise">
                    <InputNumber min={0.05} max={2.0} step={0.05} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item label="上升/下降比 <" name="collisionRiseFallRatio">
                    <InputNumber min={0.1} max={1.0} step={0.05} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
            </Col>

            <Col span={12}>
              <Divider orientation="left">警笛声检测阈值</Divider>
              <Form.Item label="警笛持续最小时间 (秒)" name="sirenMinDuration">
                <InputNumber min={0.5} max={60} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="高于环境底噪 (dB)" name="sirenDbAboveAmbient">
                <InputNumber min={5} max={60} step={1} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="500-2500Hz频段能量占比 ≥" name="sirenBandRatio">
                <InputNumber min={0.1} max={1.0} step={0.05} style={{ width: '100%' }} />
              </Form.Item>

              <Divider orientation="left">通用参数</Divider>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item label="事件冷却时间 (秒)" name="eventCooldown">
                    <InputNumber min={5} max={300} step={5} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item label="环境底噪平滑系数" name="ambientUpdateAlpha">
                    <InputNumber min={0.001} max={0.1} step={0.001} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
            </Col>
          </Row>

          <Divider />
          <Space style={{ float: 'right' }}>
            <Button onClick={() => setConfigModal(false)}>取消</Button>
            <Button icon={<ReloadOutlined />} onClick={() => configForm.resetFields()}>
              恢复默认
            </Button>
            <Button type="primary" htmlType="submit" loading={configLoading}>
              应用配置
            </Button>
          </Space>
        </Form>
      </Modal>
    </div>
  );
};

export default AudioEvents;
