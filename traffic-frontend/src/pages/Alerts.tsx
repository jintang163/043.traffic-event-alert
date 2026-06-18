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
import { alertApi, workOrderApi, departmentApi, globalTrackApi } from '@/services/api';
import { wsService } from '@/services/websocket';
import { useAlertStore } from '@/store/alertStore';
import {
  EVENT_TYPE_LABELS,
  EVENT_LEVEL_LABELS,
  EVENT_LEVEL_COLORS,
  ALERT_STATUS_LABELS,
  DEBRIS_CATEGORY_LABELS,
  DEBRIS_CATEGORY_COLORS,
  type AlertEvent,
  type Department,
  type WorkOrder,
  type GlobalTrack,
  type DebrisCategoryOption,
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
  const [falseModal, setFalseModal] = useState(false);
  const [handleModal, setHandleModal] = useState(false);
  const [orderModal, setOrderModal] = useState(false);
  const [falseForm] = Form.useForm();
  const [handleForm] = Form.useForm();
  const [orderForm] = Form.useForm();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [debrisCategories, setDebrisCategories] = useState<DebrisCategoryOption[]>([]);

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
    });

    return () => unsub();
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
    setDetailDrawer(true);
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

  const getAlertStats = () => {
    const today = new Date().toDateString();
    const todayAlerts = data.filter((a) => new Date(a.eventTime).toDateString() === today);
    const pending = data.filter((a) => a.alertStatus === 0);
    const accident = data.filter((a) => a.eventType === 'ACCIDENT');
    const debris = data.filter((a) => a.eventType === 'DEBRIS');
    return { today: todayAlerts.length, pending: pending.length, accident: accident.length, debris: debris.length };
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
          color={text === 'ACCIDENT' ? 'red' : text === 'REVERSE' ? 'orange' : text === 'DEBRIS' ? 'purple' : 'blue'}
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
      render: (text) => (
        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <VideoCameraOutlined />
          {text}
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
      width: 280,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record)} />
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
        <Col xs={12} md={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="今日告警" value={stats.today} valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="待处理" value={stats.pending} valueStyle={{ color: '#fa8c16' }} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="事故类" value={stats.accident} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
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
                <Tag color={currentAlert.eventType === 'ACCIDENT' ? 'red' : currentAlert.eventType === 'REVERSE' ? 'orange' : 'purple'}>
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
              <Descriptions.Item label="摄像头">{currentAlert.cameraName}</Descriptions.Item>
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
              <h4 style={{ marginBottom: 8 }}>
                🚗 关联轨迹 ({linkedTracks.length})
              </h4>
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
                        <Button
                          type="link"
                          size="small"
                          icon={<CarOutlined />}
                          onClick={() => handleViewTrack(r.id)}
                        >
                          查看轨迹
                        </Button>
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
    </div>
  );
};

export default Alerts;
