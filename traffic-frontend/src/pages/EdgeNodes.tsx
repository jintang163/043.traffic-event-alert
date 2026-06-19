import React, { useCallback, useEffect, useRef, useState } from 'react';
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
  InputNumber,
  Switch,
  message,
  Row,
  Col,
  Popconfirm,
  Tooltip,
  Statistic,
  Progress,
  Tabs,
  Descriptions,
  Divider,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  ThunderboltOutlined,
  GlobalOutlined,
  EnvironmentOutlined,
  DashboardOutlined,
  CloudServerOutlined,
} from '@ant-design/icons';
import { edgeNodeApi, departmentApi } from '@/services/api';
import {
  EDGE_NODE_STATUS_LABELS,
  EDGE_ONLINE_STATUS_LABELS,
  EDGE_UPLOAD_STATUS_LABELS,
  HARDWARE_MODEL_OPTIONS,
  EVENT_TYPE_LABELS,
  type EdgeNode,
  type EdgeOfflineEvent,
  type Department,
} from '@/types';

const { Option } = Select;

interface EdgeNodeStats {
  total: number;
  online: number;
  offline: number;
  todayEventCount: number;
}

const EdgeNodes: React.FC = () => {
  const [data, setData] = useState<EdgeNode[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [editModal, setEditModal] = useState(false);
  const [viewModal, setViewModal] = useState(false);
  const [editForm] = Form.useForm();
  const [editingId, setEditingId] = useState<number | null>(null);
  const [viewNode, setViewNode] = useState<EdgeNode | null>(null);
  const [stats, setStats] = useState<EdgeNodeStats>({
    total: 0,
    online: 0,
    offline: 0,
    todayEventCount: 0,
  });
  const [departments, setDepartments] = useState<Department[]>([]);
  const [offlineEvents, setOfflineEvents] = useState<EdgeOfflineEvent[]>([]);
  const [offlineEventsLoading, setOfflineEventsLoading] = useState(false);
  const refreshTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const startAutoRefresh = useCallback(() => {
    if (refreshTimerRef.current) clearInterval(refreshTimerRef.current);
    refreshTimerRef.current = setInterval(() => {
      loadData();
      loadStats();
    }, 30000);
  }, [current, pageSize]);

  useEffect(() => {
    return () => {
      if (refreshTimerRef.current) clearInterval(refreshTimerRef.current);
    };
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const res: any = await edgeNodeApi.page({
        ...values,
        current,
        size: pageSize,
      });
      if (res.code === 200) {
        setData(res.data.records);
        setTotal(res.data.total);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadStats = async () => {
    try {
      const res: any = await edgeNodeApi.statistics();
      if (res.code === 200) {
        setStats(res.data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const loadDepartments = async () => {
    try {
      const res: any = await departmentApi.list();
      if (res.code === 200) {
        setDepartments(res.data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    loadData();
    loadStats();
    loadDepartments();
    startAutoRefresh();
  }, [current, pageSize]);

  const handleSearch = () => {
    setCurrent(1);
    loadData();
  };

  const handleReset = () => {
    searchForm.resetFields();
    setCurrent(1);
    loadData();
  };

  const handleAdd = () => {
    setEditingId(null);
    editForm.resetFields();
    editForm.setFieldsValue({
      status: 1,
      onlineStatus: 0,
      heartbeatInterval: 30,
      cpuCores: 4,
      memoryGB: 8,
      storageGB: 128,
    });
    setEditModal(true);
  };

  const handleEdit = (record: EdgeNode) => {
    setEditingId(record.id);
    editForm.setFieldsValue(record);
    setEditModal(true);
  };

  const handleDelete = async (id: number) => {
    const res: any = await edgeNodeApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
      loadStats();
    }
  };

  const handleView = async (record: EdgeNode) => {
    setViewNode(record);
    setOfflineEventsLoading(true);
    try {
      const res: any = await edgeNodeApi.listOfflineEvents(record.id);
      if (res.code === 200) {
        setOfflineEvents(res.data || []);
      }
    } finally {
      setOfflineEventsLoading(false);
    }
    setViewModal(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await editForm.validateFields();
      const res: any = await edgeNodeApi.save(values);
      if (res.code === 200) {
        message.success(editingId ? '编辑成功' : '添加成功');
        setEditModal(false);
        loadData();
        loadStats();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const renderUsage = (value?: number) => {
    if (value === undefined || value === null) return '—';
    const color = value >= 90 ? 'error' : value >= 70 ? 'warning' : 'success';
    return (
      <Progress
        percent={Number(value.toFixed(1))}
        size="small"
        status={value >= 90 ? 'exception' : undefined}
        strokeColor={{
          '0%': color === 'success' ? '#52c41a' : color === 'warning' ? '#faad14' : '#ff4d4f',
          '100%': color === 'success' ? '#73d13d' : color === 'warning' ? '#ffc53d' : '#ff7875',
        }}
      />
    );
  };

  const renderTemp = (value?: number) => {
    if (value === undefined || value === null) return '—';
    const color = value >= 80 ? '#ff4d4f' : value >= 60 ? '#faad14' : '#52c41a';
    return <span style={{ color, fontWeight: 500 }}>{value.toFixed(1)}°C</span>;
  };

  const eventColumns: ColumnsType<EdgeOfflineEvent> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
      align: 'center',
    },
    {
      title: '事件类型',
      dataIndex: 'eventType',
      width: 120,
      render: (text) => (
        <Tag color="blue">{EVENT_TYPE_LABELS[text] || text}</Tag>
      ),
    },
    {
      title: '事件UUID',
      dataIndex: 'eventUuid',
      width: 200,
      ellipsis: true,
    },
    {
      title: '事件时间',
      dataIndex: 'eventTime',
      width: 170,
    },
    {
      title: '上传状态',
      dataIndex: 'uploadStatus',
      width: 100,
      align: 'center',
      render: (val) => {
        const status = EDGE_UPLOAD_STATUS_LABELS[val];
        return <Tag color={status.color}>{status.label}</Tag>;
      },
    },
    {
      title: '重试次数',
      dataIndex: 'retryCount',
      width: 90,
      align: 'center',
      render: (val, record) => `${val || 0} / ${record.maxRetry || '-'}`,
    },
    {
      title: '上传时间',
      dataIndex: 'uploadTime',
      width: 170,
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      ellipsis: true,
    },
  ];

  const columns: ColumnsType<EdgeNode> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
      align: 'center',
    },
    {
      title: '节点名称',
      dataIndex: 'nodeName',
      width: 180,
      render: (text, record) => (
        <a onClick={() => handleView(record)} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <ThunderboltOutlined style={{ color: '#1890ff' }} />
          {text}
        </a>
      ),
    },
    {
      title: '节点编码',
      dataIndex: 'nodeCode',
      width: 140,
    },
    {
      title: '硬件型号',
      dataIndex: 'hardwareModel',
      width: 180,
      render: (text) => text || '—',
    },
    {
      title: 'IP地址',
      dataIndex: 'ipAddress',
      width: 140,
      render: (text) => text || '—',
    },
    {
      title: '在线状态',
      dataIndex: 'onlineStatus',
      width: 90,
      align: 'center',
      render: (val) => (
        <Tag color={val === 1 ? 'green' : 'red'}>
          {EDGE_ONLINE_STATUS_LABELS[val]}
        </Tag>
      ),
    },
    {
      title: '启用状态',
      dataIndex: 'status',
      width: 90,
      align: 'center',
      render: (val) => (
        <Tag color={val === 1 ? 'green' : 'default'}>
          {EDGE_NODE_STATUS_LABELS[val]}
        </Tag>
      ),
    },
    {
      title: 'CPU使用率',
      dataIndex: 'cpuUsage',
      width: 130,
      render: (val) => renderUsage(val),
    },
    {
      title: '内存使用率',
      dataIndex: 'memoryUsage',
      width: 130,
      render: (val) => renderUsage(val),
    },
    {
      title: 'GPU使用率',
      dataIndex: 'gpuUsage',
      width: 130,
      render: (val) => renderUsage(val),
    },
    {
      title: '温度',
      dataIndex: 'temperature',
      width: 90,
      align: 'center',
      render: (val) => renderTemp(val),
    },
    {
      title: '今日事件数',
      dataIndex: 'eventCountToday',
      width: 100,
      align: 'center',
      render: (val) => (
        <span style={{ fontWeight: 500, color: val && val > 0 ? '#ff4d4f' : '#666' }}>
          {val || 0}
        </span>
      ),
    },
    {
      title: '最后心跳',
      dataIndex: 'lastHeartbeat',
      width: 170,
      render: (text) => text || '—',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record)} />
          </Tooltip>
          <Tooltip title="编辑">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          </Tooltip>
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
            <Tooltip title="删除">
              <Button type="link" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%)' }}>
            <Statistic
              title="节点总数"
              value={stats.total}
              prefix={<CloudServerOutlined style={{ color: '#1890ff' }} />}
              valueStyle={{ color: '#1890ff', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #f6ffed 0%, #b7eb8f 100%)' }}>
            <Statistic
              title="在线节点"
              value={stats.online}
              prefix={<GlobalOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #fff1f0 0%, #ffa39e 100%)' }}>
            <Statistic
              title="离线节点"
              value={stats.offline}
              prefix={<DashboardOutlined style={{ color: '#ff4d4f' }} />}
              valueStyle={{ color: '#ff4d4f', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius:  8, background: 'linear-gradient(135deg, #fff7e6 0%, #ffd591 100%)' }}>
            <Statistic
              title="今日事件数"
              value={stats.todayEventCount}
              prefix={<ThunderboltOutlined style={{ color: '#fa8c16' }} />}
              valueStyle={{ color: '#fa8c16', fontWeight: 700 }}
            />
          </Card>
        </Col>
      </Row>

      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="keyword" label="关键词">
            <Input placeholder="名称/编码" style={{ width: 180 }} allowClear />
          </Form.Item>
          <Form.Item name="onlineStatus" label="在线状态">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              <Option value={1}>在线</Option>
              <Option value={0}>离线</Option>
            </Select>
          </Form.Item>
          <Form.Item name="hardwareModel" label="硬件型号">
            <Select placeholder="全部" style={{ width: 200 }} allowClear>
              {HARDWARE_MODEL_OPTIONS.map((opt) => (
                <Option key={opt.value} value={opt.value}>{opt.label}</Option>
              ))}
            </Select>
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

      <Card
        style={{ borderRadius: 8 }}
        title="边缘节点列表"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => { loadData(); loadStats(); }}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
              新增节点
            </Button>
          </Space>
        }
      >
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
          scroll={{ x: 2000 }}
        />
      </Card>

      <Modal
        title={editingId ? '编辑边缘节点' : '新增边缘节点'}
        open={editModal}
        onOk={handleSubmit}
        onCancel={() => setEditModal(false)}
        okText="保存"
        width={800}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="nodeCode" label="节点编码" rules={[{ required: true, message: '请输入节点编码' }]}>
                <Input placeholder="请输入节点编码" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="nodeName" label="节点名称" rules={[{ required: true, message: '请输入节点名称' }]}>
                <Input placeholder="请输入节点名称" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="hardwareModel" label="硬件型号">
                <Select placeholder="请选择硬件型号" allowClear>
                  {HARDWARE_MODEL_OPTIONS.map((opt) => (
                    <Option key={opt.value} value={opt.value}>{opt.label}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="gpuInfo" label="GPU信息">
                <Input placeholder="如：NVIDIA Orin" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="ipAddress" label="IP地址">
                <Input placeholder="如：192.168.1.100" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="macAddress" label="MAC地址">
                <Input placeholder="如：00:1A:2B:3C:4D:5E" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="cpuCores" label="CPU核心数">
                <InputNumber min={1} max={256} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="memoryGB" label="内存(GB)">
                <InputNumber min={1} max={1024} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="storageGB" label="存储(GB)">
                <InputNumber min={1} max={100000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item name="osInfo" label="操作系统信息">
                <Input placeholder="如：Ubuntu 20.04 LTS, JetPack 5.1" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="longitude" label="经度">
                <InputNumber style={{ width: '100%' }} precision={6} step={0.000001} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="latitude" label="纬度">
                <InputNumber style={{ width: '100%' }} precision={6} step={0.000001} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="location" label="位置描述">
            <Input placeholder="如：G4京港澳高速K123+450机房" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="heartbeatInterval" label="心跳间隔(秒)">
                <InputNumber min={5} max={3600} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="cameraCount" label="接入摄像头数">
                <InputNumber min={0} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="deptId" label="所属部门">
                <Select placeholder="请选择部门" allowClear>
                  {departments.map((dept) => (
                    <Option key={dept.id} value={dept.id}>{dept.deptName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="status" label="启用状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="请输入节点描述信息" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <ThunderboltOutlined style={{ color: '#1890ff' }} />
            {viewNode?.nodeName}
          </span>
        }
        open={viewModal}
        onCancel={() => setViewModal(false)}
        footer={null}
        width={1000}
        destroyOnClose
      >
        {viewNode && (
          <Tabs
            defaultActiveKey="hardware"
            items={[
              {
                key: 'hardware',
                label: (
                  <span>
                    <CloudServerOutlined /> 硬件配置
                  </span>
                ),
                children: (
                  <div>
                    <Descriptions bordered column={2} size="small">
                      <Descriptions.Item label="节点编码">{viewNode.nodeCode}</Descriptions.Item>
                      <Descriptions.Item label="节点名称">{viewNode.nodeName}</Descriptions.Item>
                      <Descriptions.Item label="硬件型号">{viewNode.hardwareModel || '—'}</Descriptions.Item>
                      <Descriptions.Item label="GPU信息">{viewNode.gpuInfo || '—'}</Descriptions.Item>
                      <Descriptions.Item label="CPU核心数">{viewNode.cpuCores || '—'}</Descriptions.Item>
                      <Descriptions.Item label="内存">{viewNode.memoryGB ? `${viewNode.memoryGB} GB` : '—'}</Descriptions.Item>
                      <Descriptions.Item label="存储">{viewNode.storageGB ? `${viewNode.storageGB} GB` : '—'}</Descriptions.Item>
                      <Descriptions.Item label="操作系统">{viewNode.osInfo || '—'}</Descriptions.Item>
                      <Descriptions.Item label="IP地址">{viewNode.ipAddress || '—'}</Descriptions.Item>
                      <Descriptions.Item label="MAC地址">{viewNode.macAddress || '—'}</Descriptions.Item>
                      <Descriptions.Item label="心跳间隔">{viewNode.heartbeatInterval ? `${viewNode.heartbeatInterval} 秒` : '—'}</Descriptions.Item>
                      <Descriptions.Item label="接入摄像头">{viewNode.cameraCount || 0}</Descriptions.Item>
                      <Descriptions.Item label="启用状态">
                        <Tag color={viewNode.status === 1 ? 'green' : 'default'}>
                          {EDGE_NODE_STATUS_LABELS[viewNode.status]}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="在线状态">
                        <Tag color={viewNode.onlineStatus === 1 ? 'green' : 'red'}>
                          {EDGE_ONLINE_STATUS_LABELS[viewNode.onlineStatus]}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="最后心跳">{viewNode.lastHeartbeat || '—'}</Descriptions.Item>
                      <Descriptions.Item label="创建时间">{viewNode.createTime || '—'}</Descriptions.Item>
                    </Descriptions>

                    <Divider orientation="left" orientationMargin={0}>实时状态</Divider>
                    <Row gutter={[16, 16]}>
                      <Col xs={24} sm={12} md={6}>
                        <Card size="small">
                          <div style={{ fontSize: 12, color: '#666', marginBottom: 8 }}>CPU使用率</div>
                          {renderUsage(viewNode.cpuUsage)}
                        </Card>
                      </Col>
                      <Col xs={24} sm={12} md={6}>
                        <Card size="small">
                          <div style={{ fontSize: 12, color: '#666', marginBottom: 8 }}>内存使用率</div>
                          {renderUsage(viewNode.memoryUsage)}
                        </Card>
                      </Col>
                      <Col xs={24} sm={12} md={6}>
                        <Card size="small">
                          <div style={{ fontSize: 12, color: '#666', marginBottom: 8 }}>GPU使用率</div>
                          {renderUsage(viewNode.gpuUsage)}
                        </Card>
                      </Col>
                      <Col xs={24} sm={12} md={6}>
                        <Card size="small">
                          <div style={{ fontSize: 12, color: '#666', marginBottom: 8 }}>温度</div>
                          {renderTemp(viewNode.temperature)}
                        </Card>
                      </Col>
                    </Row>

                    {viewNode.description && (
                      <>
                        <Divider orientation="left" orientationMargin={0}>描述</Divider>
                        <div style={{ padding: '8px 12px', background: '#fafafa', borderRadius: 4 }}>
                          {viewNode.description}
                        </div>
                      </>
                    )}
                  </div>
                ),
              },
              {
                key: 'location',
                label: (
                  <span>
                    <EnvironmentOutlined /> 地理位置
                  </span>
                ),
                children: (
                  <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label="经度" span={2}>
                      {viewNode.longitude !== undefined && viewNode.longitude !== null
                        ? viewNode.longitude
                        : '—'}
                    </Descriptions.Item>
                    <Descriptions.Item label="纬度" span={2}>
                      {viewNode.latitude !== undefined && viewNode.latitude !== null
                        ? viewNode.latitude
                        : '—'}
                    </Descriptions.Item>
                    <Descriptions.Item label="位置描述" span={2}>
                      {viewNode.location || '—'}
                    </Descriptions.Item>
                  </Descriptions>
                ),
              },
              {
                key: 'events',
                label: (
                  <span>
                    <ThunderboltOutlined /> 离线事件 ({offlineEvents.length})
                  </span>
                ),
                children: (
                  <Table
                    columns={eventColumns}
                    dataSource={offlineEvents}
                    rowKey="id"
                    loading={offlineEventsLoading}
                    pagination={false}
                    size="small"
                    scroll={{ x: 1000 }}
                    locale={{ emptyText: '暂无离线事件' }}
                  />
                ),
              },
            ]}
          />
        )}
      </Modal>
    </div>
  );
};

export default EdgeNodes;
