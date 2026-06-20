import React, { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Tag,
  Input,
  Select,
  Card,
  Modal,
  Form,
  DatePicker,
  message,
  Drawer,
  Descriptions,
  Row,
  Col,
  Popconfirm,
  Image,
  Tooltip,
  Statistic,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  VideoCameraOutlined,
  WarningOutlined,
  SafetyOutlined,
  FileTextOutlined,
  EnvironmentOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  CarOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { alertApi, workOrderApi, departmentApi, globalTrackApi, plateRecognitionApi, policePushApi } from '@/services/api';
import { wsService } from '@/services/websocket';
import { useAlertStore } from '@/store/alertStore';
import EventReplayModal from '@/components/EventReplayModal';
import LedSignDisplay from '@/components/LedSignDisplay';
import {
  EVENT_TYPE_LABELS,
  EVENT_TYPE_COLORS,
  EVENT_LEVEL_LABELS,
  EVENT_LEVEL_COLORS,
  ALERT_STATUS_LABELS,
  DEBRIS_CATEGORY_LABELS,
  DEBRIS_CATEGORY_COLORS,
  ACCIDENT_SEVERITY_OPTIONS,
  ACCIDENT_DEFORMATION_LEVELS,
  type AlertEvent,
  type Department,
  type WorkOrder,
  type GlobalTrack,
  type DebrisCategoryOption,
  type PlateRecognition,
  type PolicePush,
  SCENE_TYPE_COLORS,
  POLICE_PUSH_STATUS_LABELS,
} from '@/types';

const { RangePicker } = DatePicker;
const { Option } = Select;
const { TextArea } = Input;

const Alerts: React.FC = () => {
  const navigate = useNavigate();
  const { addAlert, markAllRead, updateAlert } = useAlertStore();
  const [data, setData] = useState<AlertEvent[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [detailDrawer, setDetailDrawer] = useState(false);
  const [currentAlert, setCurrentAlert] = useState<AlertEvent | null>(null);
  const [workOrders, setWorkOrders] = useState<WorkOrder[]>([]);
  const [linkedTracks, setLinkedTracks] = useState<GlobalTrack[]>([]);
  const [plateRecognitions, setPlateRecognitions] = useState<PlateRecognition[]>([]);
  const [policePushes, setPolicePushes] = useState<PolicePush[]>([]);
  const [falseModal, setFalseModal] = useState(false);
  const [handleModal, setHandleModal] = useState(false);
  const [orderModal, setOrderModal] = useState(false);
  const [falseForm] = Form.useForm();
  const [handleForm] = Form.useForm();
  const [orderForm] = Form.useForm();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [debrisCategories, setDebrisCategories] = useState<DebrisCategoryOption[]>([]);
  const [majorAlertModal, setMajorAlertModal] = useState(false);
  const [majorAlertInfo, setMajorAlertInfo] = useState<AlertEvent | null>(null);
  const [pedestrianAlertModal, setPedestrianAlertModal] = useState(false);
  const [pedestrianAlertInfo, setPedestrianAlertInfo] = useState<AlertEvent | null>(null);
  const [replayModal, setReplayModal] = useState(false);

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
      const res: any = await alertApi.page(params);
      if (res.code === 200) {
        setData(res.data.records);
        setTotal(res.data.total);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    markAllRead();

    const unsub = wsService.onAlert((alert) => {
      addAlert(alert as any);
      loadData();
      if (alert.eventType === 'PEDESTRIAN_INTRUSION') {
        setPedestrianAlertInfo(alert as any);
        setPedestrianAlertModal(true);
      }
    });

    const unsubMajor = wsService.onMajorAlert((alert) => {
      addAlert(alert as any);
      setMajorAlertInfo(alert as any);
      setMajorAlertModal(true);
      loadData();
      try {
        const ctx = new AudioContext();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.frequency.value = 880;
        osc.type = 'square';
        gain.gain.value = 0.3;
        osc.start();
        setTimeout(() => { osc.stop(); ctx.close(); }, 600);
      } catch (_) {}
    });

    return () => { unsub(); unsubMajor(); };
  }, [current, pageSize]);

  useEffect(() => {
    departmentApi.list().then((res: any) => {
      if (res.code === 200) setDepartments(res.data);
    });
    alertApi.debrisCategories().then((res: any) => {
      if (res.code === 200) setDebrisCategories(res.data);
    });
  }, []);

  const handleSearch = () => {
    setCurrent(1);
    loadData();
  };

  const handleReset = () => {
    searchForm.resetFields();
    setCurrent(1);
    loadData();
  };

  const handleView = async (record: AlertEvent) => {
    setCurrentAlert(record);
    try {
      const res: any = await workOrderApi.listByAlert(record.id);
      if (res.code === 200) setWorkOrders(res.data);
    } catch (e) {
      setWorkOrders([]);
    }
    try {
      const res: any = await globalTrackApi.listByEvent(record.id);
      if (res.code === 200) setLinkedTracks(res.data || []);
      else setLinkedTracks([]);
    } catch (e) {
      setLinkedTracks([]);
    }
    try {
      const res: any = await plateRecognitionApi.listByEvent(record.id);
      if (res.code === 200) setPlateRecognitions(res.data || []);
      else setPlateRecognitions([]);
    } catch (e) {
      setPlateRecognitions([]);
    }
    try {
      const res: any = await policePushApi.listByEvent(record.id);
      if (res.code === 200) setPolicePushes(res.data || []);
      else setPolicePushes([]);
    } catch (e) {
      setPolicePushes([]);
    }
    setDetailDrawer(true);
  };

  const handleRetryPolicePush = async (id: number) => {
    try {
      const res: any = await policePushApi.retry(id);
      if (res.code === 200) {
        message.success('已触发重试');
        if (currentAlert) {
          const pr: any = await policePushApi.listByEvent(currentAlert.id);
          if (pr.code === 200) setPolicePushes(pr.data || []);
        }
      }
    } catch (e) {
      // ignore
    }
  };

  const handleMarkHandled = async (record: AlertEvent) => {
    setCurrentAlert(record);
    handleForm.resetFields();
    setHandleModal(true);
  };

  const confirmHandle = async () => {
    if (!currentAlert) return;
    try {
      const values = await handleForm.validateFields();
      const res: any = await alertApi.handle(currentAlert.id, values.remark);
      if (res.code === 200) {
        message.success('已标记为已处理');
        updateAlert(currentAlert.id, { alertStatus: 1 });
        setHandleModal(false);
        loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleMarkFalse = (record: AlertEvent) => {
    setCurrentAlert(record);
    falseForm.resetFields();
    setFalseModal(true);
  };

  const confirmFalse = async () => {
    if (!currentAlert) return;
    try {
      const values = await falseForm.validateFields();
      const res: any = await alertApi.markFalsePositive(currentAlert.id, values);
      if (res.code === 200) {
        message.success('已标记为误报，感谢反馈');
        updateAlert(currentAlert.id, { alertStatus: 2, isFalsePositive: 1 });
        setFalseModal(false);
        loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleCreateOrder = (record: AlertEvent) => {
    setCurrentAlert(record);
    orderForm.resetFields();
    orderForm.setFieldsValue({
      title: `${EVENT_TYPE_LABELS[record.eventType] || record.eventType}-${record.cameraName}`,
      eventType: record.eventType,
      orderLevel: record.eventLevel,
      description: record.description,
      alertEventId: record.id,
    });
    setOrderModal(true);
  };

  const confirmCreateOrder = async () => {
    try {
      const values = await orderForm.validateFields();
      const res: any = await workOrderApi.save(values);
      if (res.code === 200) {
        message.success('工单创建成功');
        setOrderModal(false);
        loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleViewTrack = (trackId: number) => {
    navigate(`/tracks?id=${trackId}`);
  };

  const getLevelColor = (level: number) => {
    const colors: Record<number, string> = {
      1: 'default',
      2: 'gold',
      3: 'red',
      4: 'magenta',
    };
    return colors[level] || 'default';
  };

  const getStatusColor = (status: number) => {
    const colors: Record<number, string> = {
      0: 'processing',
      1: 'success',
      2: 'default',
    };
    return colors[status] || 'default';
  };

  const getAccidentSeverityColor = (severity?: string) => {
    const opt = ACCIDENT_SEVERITY_OPTIONS.find((s) => s.value === severity);
    return opt?.color || '#d9d9d9';
  };

  const getAccidentSeverityLabel = (severity?: string) => {
    const opt = ACCIDENT_SEVERITY_OPTIONS.find((s) => s.value === severity);
    return opt?.label || '-';
  };

  const getDeformationLabel = (lv?: number) => {
    const opt = ACCIDENT_DEFORMATION_LEVELS.find((d) => d.value === lv);
    return opt?.label || '-';
  };

  const getAlertStats = () => {
    const today = new Date().toDateString();
    const todayAlerts = data.filter((a) => new Date(a.eventTime).toDateString() === today);
    const pending = data.filter((a) => a.alertStatus === 0);
    const accident = data.filter((a) => a.eventType === 'ACCIDENT');
    const major = data.filter((a) => a.accidentSeverity === 'MAJOR');
    const debris = data.filter((a) => a.eventType === 'DEBRIS');
    return { today: todayAlerts.length, pending: pending.length, accident: accident.length, major: major.length, debris: debris.length };
  };

  const stats = getAlertStats();

  const columns: ColumnsType<AlertEvent> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
      align: 'center',
    },
    {
      title: '事件编号',
      dataIndex: 'eventNo',
      width: 160,
      render: (text, record) => (
        <a onClick={() => handleView(record)}>{text}</a>
      ),
    },
    {
      title: '事件类型',
      dataIndex: 'eventType',
      width: 110,
      render: (text, record) => (
        <Tag
          icon={<WarningOutlined />}
          color={EVENT_TYPE_COLORS[text] || 'blue'}
        >
          {EVENT_TYPE_LABELS[text] || text}
        </Tag>
      ),
    },
    {
      title: '抛洒物子类',
      dataIndex: 'debrisCategory',
      width: 120,
      render: (val: string, record: AlertEvent) => {
        if (record.eventType !== 'DEBRIS' || !val) return <span style={{ color: '#bbb' }}>-</span>;
        return (
          <Tag color={DEBRIS_CATEGORY_COLORS[val] || 'default'}>
            {DEBRIS_CATEGORY_LABELS[val] || val}
          </Tag>
        );
      },
    },
    {
      title: '事故严重程度',
      dataIndex: 'accidentSeverity',
      width: 110,
      align: 'center',
      render: (val: string, record: AlertEvent) => {
        if (record.eventType !== 'ACCIDENT' || !val) return <span style={{ color: '#bbb' }}>-</span>;
        return (
          <Tag
            color={getAccidentSeverityColor(val)}
            style={{
              fontWeight: val === 'MAJOR' ? 700 : 600,
              border: val === 'MAJOR' ? '1px solid #ff4d4f' : undefined,
            }}
          >
            {val === 'MAJOR' && '🚨 '}
            {record.accidentSeverityLabel || getAccidentSeverityLabel(val)}
          </Tag>
        );
      },
    },
    {
      title: '等级',
      dataIndex: 'eventLevel',
      width: 80,
      align: 'center',
      render: (val) => (
        <Tag color={getLevelColor(val)} style={{ fontWeight: 600 }}>
          {EVENT_LEVEL_LABELS[val]}
        </Tag>
      ),
    },
    {
      title: '摄像头',
      dataIndex: 'cameraName',
      width: 160,
      render: (text, record) => (
        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <VideoCameraOutlined />
          {text}
          {record.sourceNodeCode && (
            <Tooltip title={`边缘节点: ${record.sourceNodeCode}`}>
              <Tag color="blue" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px', marginLeft: 2 }}>边缘</Tag>
            </Tooltip>
          )}
        </span>
      ),
    },
    {
      title: '位置',
      dataIndex: 'location',
      width: 180,
      ellipsis: true,
      render: (text, record) => (
        <Tooltip title={text || `${record.longitude}, ${record.latitude}`}>
          <span>
            <EnvironmentOutlined /> {text || '-'}
          </span>
        </Tooltip>
      ),
    },
    {
      title: '事件时间',
      dataIndex: 'eventTime',
      width: 170,
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 90,
      align: 'center',
      render: (val) => `${(val * 100).toFixed(0)}%`,
    },
    {
      title: '状态',
      dataIndex: 'alertStatus',
      width: 90,
      align: 'center',
      render: (val, record) => (
        <Tag color={getStatusColor(val)}>
          {record.isFalsePositive === 1 ? '误报' : ALERT_STATUS_LABELS[val]}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 320,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record)} />
          </Tooltip>
          <Tooltip title="轨迹回放">
            <Button
              type="link"
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => {
                setCurrentAlert(record);
                setReplayModal(true);
              }}
            />
          </Tooltip>
          {record.alertStatus === 0 && (
            <>
              <Tooltip title="标记已处理">
                <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => handleMarkHandled(record)} />
              </Tooltip>
              <Tooltip title="标记误报">
                <Popconfirm title="确认标记为误报?" onConfirm={() => handleMarkFalse(record)} okText="确认" cancelText="取消">
                  <Button type="link" size="small" icon={<CloseCircleOutlined />} />
                </Popconfirm>
              </Tooltip>
              <Tooltip title="创建工单">
                <Button type="link" size="small" icon={<FileTextOutlined />} onClick={() => handleCreateOrder(record)} />
              </Tooltip>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={12} md={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="今日告警" value={stats.today} valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="待处理" value={stats.pending} valueStyle={{ color: '#fa8c16' }} />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="事故类" value={stats.accident} />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={4}>
          <Card size="small" style={{ borderRadius: 8, background: 'linear-gradient(135deg,#fff1f0,#ffa39e)' }}>
            <Statistic
              title="🚨 重大事故"
              value={stats.major}
              valueStyle={{ color: '#ff4d4f', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={4}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="抛洒物" value={stats.debris} />
          </Card>
        </Col>
      </Row>

      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="keyword" label="关键词">
            <Input placeholder="事件号/描述" style={{ width: 160 }} allowClear />
          </Form.Item>
          <Form.Item name="eventType" label="类型">
            <Select placeholder="全部" style={{ width: 110 }} allowClear>
              {Object.entries(EVENT_TYPE_LABELS).map(([k, v]) => (
                <Option key={k} value={k}>{v}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="debrisCategory" label="抛洒物类别">
            <Select placeholder="全部" style={{ width: 140 }} allowClear>
              {debrisCategories.map((c) => (
                <Option key={c.code} value={c.code}>
                  {c.label} (L{c.defaultLevel})
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="accidentSeverity" label="事故等级">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              {ACCIDENT_SEVERITY_OPTIONS.map((opt) => (
                <Option key={opt.value} value={opt.value}>{opt.label}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="eventLevel" label="等级">
            <Select placeholder="全部" style={{ width: 100 }} allowClear>
              {Object.entries(EVENT_LEVEL_LABELS).map(([k, v]) => (
                <Option key={k} value={Number(k)}>{v}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="alertStatus" label="状态">
            <Select placeholder="全部" style={{ width: 100 }} allowClear>
              {Object.entries(ALERT_STATUS_LABELS).map(([k, v]) => (
                <Option key={k} value={Number(k)}>{v}</Option>
              ))}
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
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card style={{ borderRadius: 8 }} title="告警列表">
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          pagination={{
            current,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (c, s) => {
              setCurrent(c);
              setPageSize(s);
            },
          }}
          scroll={{ x: 1500 }}
        />
      </Card>

      <Drawer
        title="告警详情"
        width={720}
        open={detailDrawer}
        onClose={() => setDetailDrawer(false)}
        extra={
          <Space>
            {currentAlert && currentAlert.alertStatus === 0 && (
              <>
                <Button type="primary" onClick={() => handleMarkHandled(currentAlert)}>
                  标记已处理
                </Button>
                <Button onClick={() => handleCreateOrder(currentAlert)} icon={<FileTextOutlined />}>
                  创建工单
                </Button>
              </>
            )}
          </Space>
        }
      >
        {currentAlert && (
          <div>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="事件编号">{currentAlert.eventNo}</Descriptions.Item>
              <Descriptions.Item label="事件类型">
                <Tag color={EVENT_TYPE_COLORS[currentAlert.eventType] || 'default'}>
                  {EVENT_TYPE_LABELS[currentAlert.eventType] || currentAlert.eventType}
                </Tag>
              </Descriptions.Item>
              {currentAlert.eventType === 'DEBRIS' && currentAlert.debrisCategory && (
                <Descriptions.Item label="抛洒物类别">
                  <Tag color={DEBRIS_CATEGORY_COLORS[currentAlert.debrisCategory] || 'default'}>
                    {DEBRIS_CATEGORY_LABELS[currentAlert.debrisCategory] || currentAlert.debrisCategory}
                  </Tag>
                </Descriptions.Item>
              )}
              <Descriptions.Item label="事件等级">
                <Tag color={getLevelColor(currentAlert.eventLevel)} style={{ fontWeight: 600 }}>
                  {EVENT_LEVEL_LABELS[currentAlert.eventLevel]}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="置信度">{(currentAlert.confidence * 100).toFixed(1)}%</Descriptions.Item>
              <Descriptions.Item label="摄像头">
                {currentAlert.cameraName}
                {currentAlert.sourceNodeCode && (
                  <Tag color="blue" style={{ marginLeft: 8 }}>边缘: {currentAlert.sourceNodeCode}</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={getStatusColor(currentAlert.alertStatus)}>
                  {currentAlert.isFalsePositive === 1 ? '误报' : ALERT_STATUS_LABELS[currentAlert.alertStatus]}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="事件时间" span={2}>{currentAlert.eventTime}</Descriptions.Item>
              <Descriptions.Item label="位置" span={2}>
                {currentAlert.location || (currentAlert.longitude ? `${currentAlert.longitude}, ${currentAlert.latitude}` : '-')}
              </Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>
                {currentAlert.description || '-'}
              </Descriptions.Item>
              {currentAlert.eventType === 'ACCIDENT' && (
                <>
                  <Descriptions.Item label="事故严重程度">
                    {currentAlert.accidentSeverity ? (
                      <Tag
                        color={getAccidentSeverityColor(currentAlert.accidentSeverity)}
                        style={{ fontWeight: currentAlert.accidentSeverity === 'MAJOR' ? 700 : 600 }}
                      >
                        {currentAlert.accidentSeverity === 'MAJOR' && '🚨 '}
                        {currentAlert.accidentSeverityLabel || getAccidentSeverityLabel(currentAlert.accidentSeverity)}
                      </Tag>
                    ) : (
                      <span style={{ color: '#bbb' }}>-</span>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="优先级">
                    {currentAlert.accidentPriority != null ? `P${currentAlert.accidentPriority}` : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="涉事车辆">
                    {currentAlert.accidentVehicles != null ? `${currentAlert.accidentVehicles} 辆` : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="变形程度">
                    {currentAlert.accidentDeformationLevel != null
                      ? getDeformationLabel(currentAlert.accidentDeformationLevel)
                      : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="是否翻滚">
                    {currentAlert.accidentRollover === 1 ? (
                      <Tag color="red">是</Tag>
                    ) : currentAlert.accidentRollover === 0 ? (
                      <Tag color="green">否</Tag>
                    ) : (
                      '-'
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="是否起火">
                    {currentAlert.accidentFire === 1 ? (
                      <Tag color="red">是</Tag>
                    ) : currentAlert.accidentFire === 0 ? (
                      <Tag color="green">否</Tag>
                    ) : (
                      '-'
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="人员伤亡">
                    {currentAlert.accidentCasualty != null && currentAlert.accidentCasualty > 0
                      ? <Tag color="red">{currentAlert.accidentCasualty} 人</Tag>
                      : currentAlert.accidentCasualty === 0
                        ? <Tag color="green">0 人</Tag>
                        : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="碰撞车速">
                    {currentAlert.accidentImpactSpeed != null
                      ? `${currentAlert.accidentImpactSpeed.toFixed(1)} km/h`
                      : '-'}
                  </Descriptions.Item>
                  {currentAlert.accidentEvaluationReasons && (
                    <Descriptions.Item label="评估理由" span={3}>
                      <div style={{ whiteSpace: 'pre-wrap', color: '#595959', fontSize: 13 }}>
                        {currentAlert.accidentEvaluationReasons}
                      </div>
                    </Descriptions.Item>
                  )}
                </>
              )}
              {currentAlert.isFalsePositive === 1 && (
                <Descriptions.Item label="误报原因" span={2}>
                  {currentAlert.falsePositiveReason || '-'}
                </Descriptions.Item>
              )}
              {currentAlert.handleTime && (
                <>
                  <Descriptions.Item label="处理时间">{currentAlert.handleTime}</Descriptions.Item>
                  <Descriptions.Item label="处理备注">{currentAlert.handleRemark || '-'}</Descriptions.Item>
                </>
              )}
            </Descriptions>

            {currentAlert.eventSnapshot && (
              <div style={{ marginTop: 16 }}>
                <h4 style={{ marginBottom: 8 }}>📸 事件快照</h4>
                <Image src={currentAlert.eventSnapshot} width={400} />
              </div>
            )}

            {currentAlert.eventVideo && (
              <div style={{ marginTop: 16 }}>
                <h4 style={{ marginBottom: 8 }}>🎬 事件视频</h4>
                <video src={currentAlert.eventVideo} controls width="100%" style={{ borderRadius: 8 }} />
              </div>
            )}

            {workOrders.length > 0 && (
              <div style={{ marginTop: 24 }}>
                <h4 style={{ marginBottom: 8 }}>📋 关联工单 ({workOrders.length})</h4>
                <Table
                  size="small"
                  dataSource={workOrders}
                  rowKey="id"
                  pagination={false}
                  columns={[
                    { title: '工单编号', dataIndex: 'orderNo' },
                    { title: '标题', dataIndex: 'title' },
                    {
                      title: '状态',
                      dataIndex: 'orderStatus',
                      render: (val) => {
                        const labels: Record<number, string> = { 0: '待派发', 1: '处理中', 2: '已完成', 3: '已取消' };
                        const colors: Record<number, string> = { 0: 'default', 1: 'processing', 2: 'success', 3: 'default' };
                        return <Tag color={colors[val]}>{labels[val]}</Tag>;
                      },
                    },
                    { title: '指派部门', dataIndex: 'assignDeptName' },
                    {
                      title: '操作',
                      render: (_, r) => (
                        <Button type="link" size="small" onClick={() => navigate('/work-orders')}>
                          查看
                        </Button>
                      ),
                    },
                  ]}
                />
              </div>
            )}

            <div style={{ marginTop: 24 }}>
              <h4 style={{ marginBottom: 8 }}>🚙 车牌识别结果 ({plateRecognitions.length})</h4>
              {plateRecognitions.length === 0 ? (
                <div style={{ color: '#999', fontSize: 13, padding: '12px 0' }}>暂无识别记录</div>
              ) : (
                <Table
                  size="small"
                  dataSource={plateRecognitions}
                  rowKey="id"
                  pagination={false}
                  scroll={{ x: 1000 }}
                  columns={[
                    { title: '车牌号', dataIndex: 'plateNumber', render: (v) => v || '-', width: 120 },
                    {
                      title: '车牌颜色',
                      dataIndex: 'plateColor',
                      width: 90,
                      render: (v) => v || '-',
                    },
                    {
                      title: '车辆类型',
                      dataIndex: 'vehicleType',
                      width: 100,
                      render: (v) => v || '-',
                    },
                    {
                      title: '车身颜色',
                      dataIndex: 'vehicleColor',
                      width: 90,
                      render: (v) => v || '-',
                    },
                    {
                      title: '置信度',
                      dataIndex: 'confidence',
                      width: 90,
                      render: (v) => (v != null ? `${(v * 100).toFixed(1)}%` : '-'),
                    },
                    {
                      title: '场景',
                      dataIndex: 'sceneType',
                      width: 100,
                      render: (v) =>
                        v ? <Tag color={SCENE_TYPE_COLORS[v] || 'default'}>{v}</Tag> : '-',
                    },
                    {
                      title: '识别时间',
                      dataIndex: 'recognizeTime',
                      width: 170,
                      render: (v) => v || '-',
                    },
                    {
                      title: '车牌图',
                      dataIndex: 'plateImageUrl',
                      width: 110,
                      render: (v) =>
                        v ? <Image src={v} width={90} height={34} style={{ objectFit: 'cover', borderRadius: 4 }} /> : '-',
                    },
                  ]}
                />
              )}
            </div>

            <div style={{ marginTop: 24 }}>
              <h4 style={{ marginBottom: 8 }}>🚓 交警系统推送 ({policePushes.length})</h4>
              {policePushes.length === 0 ? (
                <div style={{ color: '#999', fontSize: 13, padding: '12px 0' }}>暂无推送记录</div>
              ) : (
                <Table
                  size="small"
                  dataSource={policePushes}
                  rowKey="id"
                  pagination={false}
                  scroll={{ x: 1100 }}
                  columns={[
                    { title: '推送编号', dataIndex: 'pushNo', width: 160, render: (v) => v || '-' },
                    { title: '车牌', dataIndex: 'plateNumber', width: 100, render: (v) => v || '-' },
                    { title: '推送目标', dataIndex: 'pushTarget', width: 150, render: (v) => v || '-' },
                    {
                      title: '状态',
                      dataIndex: 'pushStatus',
                      width: 100,
                      render: (val) => {
                        const s = POLICE_PUSH_STATUS_LABELS[val] || { label: `${val}`, color: 'default' };
                        return <Tag color={s.color}>{s.label}</Tag>;
                      },
                    },
                    {
                      title: '重试',
                      width: 80,
                      align: 'center',
                      render: (_, r: any) => `${r.retryCount || 0}/${r.maxRetry || '-'}`,
                    },
                    { title: '耗时(ms)', dataIndex: 'costMs', width: 90, align: 'center', render: (v) => v ?? '-' },
                    {
                      title: '推送时间',
                      dataIndex: 'pushTime',
                      width: 170,
                      render: (v) => v || '-',
                    },
                    {
                      title: '错误',
                      dataIndex: 'errorMessage',
                      width: 140,
                      ellipsis: true,
                      render: (v) => (v ? <Tooltip title={v}>{v}</Tooltip> : '-'),
                    },
                    {
                      title: '操作',
                      width: 80,
                      fixed: 'right',
                      render: (_, r: any) =>
                        r.pushStatus === 3 ? (
                          <Button type="link" size="small" onClick={() => handleRetryPolicePush(r.id)}>
                            重试
                          </Button>
                        ) : null,
                    },
                  ]}
                />
              )}
            </div>

            <div style={{ marginTop: 24 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <h4 style={{ marginBottom: 0 }}>
                  🚗 关联轨迹 ({linkedTracks.length})
                </h4>
                {linkedTracks.length > 0 && currentAlert && (
                  <Button
                    type="primary"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    onClick={() => setReplayModal(true)}
                  >
                    轨迹回放
                  </Button>
                )}
              </div>
              {linkedTracks.length === 0 ? (
                <div style={{ color: '#999', fontSize: 13, padding: '12px 0' }}>
                  暂无关联轨迹
                </div>
              ) : (
                <Table
                  size="small"
                  dataSource={linkedTracks}
                  rowKey="id"
                  pagination={false}
                  columns={[
                    { title: '轨迹编号', dataIndex: 'trackNo' },
                    { title: '车牌号', dataIndex: 'licensePlate', render: (v) => v || '-' },
                    { title: '车辆类型', dataIndex: 'vehicleType', render: (v) => v || '-' },
                    {
                      title: '状态',
                      dataIndex: 'trackStatus',
                      render: (val) => {
                        const labels: Record<number, string> = { 1: '跟踪中', 2: '已丢失', 3: '已完成' };
                        const colors: Record<number, string> = { 1: 'processing', 2: 'warning', 3: 'default' };
                        return <Tag color={colors[val]}>{labels[val]}</Tag>;
                      },
                    },
                    {
                      title: '摄像头数',
                      dataIndex: 'cameraCount',
                      width: 90,
                      align: 'center',
                    },
                    {
                      title: '操作',
                      render: (_, r: any) => (
                        <Space size={4}>
                          <Button
                            type="link"
                            size="small"
                            icon={<CarOutlined />}
                            onClick={() => handleViewTrack(r.id)}
                          >
                            详情
                          </Button>
                        </Space>
                      ),
                    },
                  ]}
                />
              )}
            </div>
          </div>
        )}
      </Drawer>

      <Modal
        title="标记已处理"
        open={handleModal}
        onOk={confirmHandle}
        onCancel={() => setHandleModal(false)}
        okText="确认处理"
      >
        <Form form={handleForm} layout="vertical">
          <Form.Item name="remark" label="处理备注">
            <TextArea rows={3} placeholder="请输入处理备注（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="标记为误报"
        open={falseModal}
        onOk={confirmFalse}
        onCancel={() => setFalseModal(false)}
        okText="确认提交"
      >
        <Form form={falseForm} layout="vertical">
          <Form.Item
            name="reason"
            label="误报原因"
            rules={[{ required: true, message: '请填写误报原因' }]}
          >
            <TextArea rows={4} placeholder="请描述误报原因，帮助我们优化模型" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="创建工单"
        open={orderModal}
        onOk={confirmCreateOrder}
        onCancel={() => setOrderModal(false)}
        okText="创建"
        width={600}
      >
        <Form form={orderForm} layout="vertical">
          <Form.Item name="alertEventId" hidden>
            <Input />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="title" label="工单标题" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="eventType" label="事件类型" rules={[{ required: true }]}>
                <Select disabled>
                  {Object.entries(EVENT_TYPE_LABELS).map(([k, v]) => (
                    <Option key={k} value={k}>{v}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="orderLevel" label="工单等级" rules={[{ required: true }]}>
                <Select>
                  {Object.entries(EVENT_LEVEL_LABELS).map(([k, v]) => (
                    <Option key={k} value={Number(k)}>{v}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="assignDeptId" label="指派部门">
                <Select placeholder="选择部门">
                  {departments.map((d) => (
                    <Option key={d.id} value={d.id}>{d.deptName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="描述">
            <TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={majorAlertModal}
        onCancel={() => setMajorAlertModal(false)}
        footer={[
          <Button key="close" onClick={() => setMajorAlertModal(false)}>关闭</Button>,
          <Button key="view" type="primary" danger onClick={() => {
            setMajorAlertModal(false);
            if (majorAlertInfo) handleView(majorAlertInfo);
          }}>查看详情</Button>,
        ]}
        width={520}
        closable={false}
        styles={{ body: { padding: 24 } }}
      >
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 48, marginBottom: 12 }}>🚨</div>
          <h2 style={{ color: '#ff4d4f', marginBottom: 8 }}>重大事故紧急告警</h2>
          {majorAlertInfo && (
            <div style={{ textAlign: 'left', background: '#fff1f0', padding: 16, borderRadius: 8, marginTop: 16 }}>
              <p><strong>事件编号:</strong> {majorAlertInfo.eventNo}</p>
              <p><strong>摄像头:</strong> {majorAlertInfo.cameraName}</p>
              <p><strong>位置:</strong> {majorAlertInfo.location || '-'}</p>
              <p><strong>时间:</strong> {majorAlertInfo.eventTime}</p>
              <p><strong>事故等级:</strong> <Tag color="#ff4d4f" style={{ fontWeight: 700 }}>{majorAlertInfo.accidentSeverityLabel || '重大事故'}</Tag></p>
              {majorAlertInfo.accidentVehicles != null && <p><strong>涉事车辆:</strong> {majorAlertInfo.accidentVehicles} 辆</p>}
              {(majorAlertInfo.accidentFire === 1 || majorAlertInfo.accidentRollover === 1) && (
                <p><strong>特征:</strong>
                  {majorAlertInfo.accidentFire === 1 && <Tag color="red">起火</Tag>}
                  {majorAlertInfo.accidentRollover === 1 && <Tag color="red">翻车</Tag>}
                </p>
              )}
              {majorAlertInfo.description && <p><strong>描述:</strong> {majorAlertInfo.description}</p>}
            </div>
          )}
        </div>
      </Modal>

      <Modal
        open={pedestrianAlertModal}
        onCancel={() => setPedestrianAlertModal(false)}
        footer={[
          <Button key="close" onClick={() => setPedestrianAlertModal(false)}>关闭</Button>,
          <Button key="view" type="primary" onClick={() => {
            setPedestrianAlertModal(false);
            if (pedestrianAlertInfo) handleView(pedestrianAlertInfo);
          }}>查看详情</Button>,
        ]}
        width={560}
        closable={false}
        styles={{ body: { padding: 24 } }}
      >
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 40, marginBottom: 8 }}>🚶‍♂️⚠️</div>
          <h2 style={{ color: '#1890ff', marginBottom: 8 }}>行人闯入预警</h2>
          <p style={{ color: '#666', marginBottom: 16 }}>检测到行人进入行车道区域</p>

          {pedestrianAlertInfo && (
            <>
              <div style={{ textAlign: 'left', background: '#e6f7ff', padding: 16, borderRadius: 8, marginBottom: 16 }}>
                <p><strong>事件编号:</strong> {pedestrianAlertInfo.eventNo}</p>
                <p><strong>摄像头:</strong> {pedestrianAlertInfo.cameraName}</p>
                <p><strong>位置:</strong> {pedestrianAlertInfo.location || '-'}</p>
                <p><strong>时间:</strong> {pedestrianAlertInfo.eventTime}</p>
                <p><strong>告警级别:</strong>
                  <Tag color="geekblue" style={{ marginLeft: 8, fontWeight: 600 }}>
                    {EVENT_LEVEL_LABELS[pedestrianAlertInfo.eventLevel || 2] || '紧急'}
                  </Tag>
                </p>
                {pedestrianAlertInfo.description && <p><strong>描述:</strong> {pedestrianAlertInfo.description}</p>}
              </div>

              <div style={{ textAlign: 'left' }}>
                <p style={{ marginBottom: 8, fontWeight: 600, color: '#333' }}>📺 路侧情报板联动</p>
                <LedSignDisplay
                  cameraId={Number(pedestrianAlertInfo.cameraId)}
                  message="行人请离开"
                  isAlert={true}
                  color="RED"
                  brightness={100}
                />
              </div>
            </>
          )}
        </div>
      </Modal>

      <EventReplayModal
        open={replayModal}
        event={currentAlert}
        onClose={() => setReplayModal(false)}
      />
    </div>
  );
};

export default Alerts;
