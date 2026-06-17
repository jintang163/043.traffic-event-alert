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
  Tooltip,
  Statistic,
  Upload,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadProps } from 'antd';
import {
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  CheckCircleOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  FileTextOutlined,
  ExclamationCircleOutlined,
  UploadOutlined,
  AlertOutlined,
} from '@ant-design/icons';
import { workOrderApi, departmentApi, alertApi } from '@/services/api';
import {
  EVENT_TYPE_LABELS,
  EVENT_LEVEL_LABELS,
  ORDER_STATUS_LABELS,
  type WorkOrder,
  type Department,
  type AlertEvent,
} from '@/types';

const { RangePicker } = DatePicker;
const { Option } = Select;
const { TextArea } = Input;

const WorkOrders: React.FC = () => {
  const [data, setData] = useState<WorkOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [editModal, setEditModal] = useState(false);
  const [detailDrawer, setDetailDrawer] = useState(false);
  const [handleModal, setHandleModal] = useState(false);
  const [editForm] = Form.useForm();
  const [handleForm] = Form.useForm();
  const [editingId, setEditingId] = useState<number | null>(null);
  const [currentOrder, setCurrentOrder] = useState<WorkOrder | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [alerts, setAlerts] = useState<AlertEvent[]>([]);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const params: any = {
        ...values,
        current,
        size: pageSize,
      };
      const res: any = await workOrderApi.page(params);
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
    departmentApi.list().then((res: any) => {
      if (res.code === 200) setDepartments(res.data);
    });
    alertApi.recent(50).then((res: any) => {
      if (res.code === 200) setAlerts(res.data);
    });
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
      orderLevel: 2,
      orderStatus: 0,
    });
    setEditModal(true);
  };

  const handleEdit = (record: WorkOrder) => {
    setEditingId(record.id);
    editForm.setFieldsValue(record);
    setEditModal(true);
  };

  const handleDelete = async (id: number) => {
    const res: any = await workOrderApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
    }
  };

  const handleView = (record: WorkOrder) => {
    setCurrentOrder(record);
    setDetailDrawer(true);
  };

  const handleHandle = (record: WorkOrder) => {
    setCurrentOrder(record);
    handleForm.resetFields();
    setHandleModal(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await editForm.validateFields();
      const res: any = await workOrderApi.save(values);
      if (res.code === 200) {
        message.success(editingId ? '编辑成功' : '创建成功');
        setEditModal(false);
        loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const confirmHandle = async () => {
    if (!currentOrder) return;
    try {
      const values = await handleForm.validateFields();
      const res: any = await workOrderApi.handle(currentOrder.id, values);
      if (res.code === 200) {
        message.success('工单处理完成');
        setHandleModal(false);
        loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const getLevelColor = (level: number) => {
    const colors: Record<number, string> = { 1: 'default', 2: 'gold', 3: 'red', 4: 'magenta' };
    return colors[level] || 'default';
  };

  const getStatusColor = (status: number) => {
    const colors: Record<number, string> = { 0: 'default', 1: 'processing', 2: 'success', 3: 'default' };
    return colors[status] || 'default';
  };

  const getStats = () => {
    const today = new Date().toDateString();
    return {
      total: data.length,
      pending: data.filter((o) => o.orderStatus === 0).length,
      processing: data.filter((o) => o.orderStatus === 1).length,
      completed: data.filter((o) => o.orderStatus === 2).length,
    };
  };

  const stats = getStats();

  const columns: ColumnsType<WorkOrder> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
      align: 'center',
    },
    {
      title: '工单编号',
      dataIndex: 'orderNo',
      width: 170,
      render: (text, record) => <a onClick={() => handleView(record)}>{text}</a>,
    },
    {
      title: '标题',
      dataIndex: 'title',
      width: 200,
      ellipsis: true,
    },
    {
      title: '事件类型',
      dataIndex: 'eventType',
      width: 110,
      render: (text) => (
        <Tag color={text === 'ACCIDENT' ? 'red' : text === 'REVERSE' ? 'orange' : 'purple'}>
          {EVENT_TYPE_LABELS[text] || text}
        </Tag>
      ),
    },
    {
      title: '等级',
      dataIndex: 'orderLevel',
      width: 80,
      align: 'center',
      render: (val) => (
        <Tag color={getLevelColor(val)} style={{ fontWeight: 600 }}>
          {EVENT_LEVEL_LABELS[val]}
        </Tag>
      ),
    },
    {
      title: '指派部门',
      dataIndex: 'assignDeptName',
      width: 140,
    },
    {
      title: '状态',
      dataIndex: 'orderStatus',
      width: 90,
      align: 'center',
      render: (val) => <Tag color={getStatusColor(val)}>{ORDER_STATUS_LABELS[val]}</Tag>,
    },
    {
      title: '计划开始',
      dataIndex: 'planStartTime',
      width: 160,
    },
    {
      title: '实际结束',
      dataIndex: 'actualEndTime',
      width: 160,
      render: (text) => text || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record)} />
          </Tooltip>
          {record.orderStatus !== 2 && record.orderStatus !== 3 && (
            <Tooltip title="处理工单">
              <Button type="link" size="small" icon={<CheckCircleOutlined />} onClick={() => handleHandle(record)} />
            </Tooltip>
          )}
          {record.orderStatus === 0 && (
            <Tooltip title="编辑">
              <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
            </Tooltip>
          )}
          {record.orderStatus === 0 && (
            <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
              <Tooltip title="删除">
                <Button type="link" size="small" danger icon={<DeleteOutlined />} />
              </Tooltip>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const uploadProps: UploadProps = {
    name: 'file',
    action: '#',
    listType: 'picture',
  };

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="工单总数" value={stats.total} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="待派发" value={stats.pending} valueStyle={{ color: '#fa8c16' }} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="处理中" value={stats.processing} valueStyle={{ color: '#1890ff' }} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="已完成" value={stats.completed} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
      </Row>

      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="keyword" label="关键词">
            <Input placeholder="编号/标题" style={{ width: 160 }} allowClear />
          </Form.Item>
          <Form.Item name="eventType" label="类型">
            <Select placeholder="全部" style={{ width: 110 }} allowClear>
              {Object.entries(EVENT_TYPE_LABELS).map(([k, v]) => (
                <Option key={k} value={k}>{v}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="orderLevel" label="等级">
            <Select placeholder="全部" style={{ width: 100 }} allowClear>
              {Object.entries(EVENT_LEVEL_LABELS).map(([k, v]) => (
                <Option key={k} value={Number(k)}>{v}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="orderStatus" label="状态">
            <Select placeholder="全部" style={{ width: 100 }} allowClear>
              {Object.entries(ORDER_STATUS_LABELS).map(([k, v]) => (
                <Option key={k} value={Number(k)}>{v}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="assignDeptId" label="部门">
            <Select placeholder="全部" style={{ width: 140 }} allowClear>
              {departments.map((d) => (
                <Option key={d.id} value={d.id}>{d.deptName}</Option>
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
        title="工单列表"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新建工单
          </Button>
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
          scroll={{ x: 1700 }}
        />
      </Card>

      <Drawer
        title="工单详情"
        width={720}
        open={detailDrawer}
        onClose={() => setDetailDrawer(false)}
        extra={
          currentOrder && currentOrder.orderStatus !== 2 && currentOrder.orderStatus !== 3 && (
            <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => handleHandle(currentOrder)}>
              处理工单
            </Button>
          )
        }
      >
        {currentOrder && (
          <div>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="工单编号">{currentOrder.orderNo}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={getStatusColor(currentOrder.orderStatus)} style={{ fontWeight: 600 }}>
                  {ORDER_STATUS_LABELS[currentOrder.orderStatus]}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="标题" span={2}>{currentOrder.title}</Descriptions.Item>
              <Descriptions.Item label="事件类型">
                <Tag color={currentOrder.eventType === 'ACCIDENT' ? 'red' : currentOrder.eventType === 'REVERSE' ? 'orange' : 'purple'}>
                  {EVENT_TYPE_LABELS[currentOrder.eventType] || currentOrder.eventType}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="工单等级">
                <Tag color={getLevelColor(currentOrder.orderLevel)}>
                  {EVENT_LEVEL_LABELS[currentOrder.orderLevel]}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="指派部门">{currentOrder.assignDeptName || '-'}</Descriptions.Item>
              <Descriptions.Item label="指派人员">{currentOrder.assignUserName || '-'}</Descriptions.Item>
              <Descriptions.Item label="计划开始">{currentOrder.planStartTime || '-'}</Descriptions.Item>
              <Descriptions.Item label="计划结束">{currentOrder.planEndTime || '-'}</Descriptions.Item>
              <Descriptions.Item label="实际开始">{currentOrder.actualStartTime || '-'}</Descriptions.Item>
              <Descriptions.Item label="实际结束">{currentOrder.actualEndTime || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>{currentOrder.createTime}</Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>{currentOrder.description || '-'}</Descriptions.Item>
              <Descriptions.Item label="处理内容" span={2}>{currentOrder.handleContent || '-'}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>{currentOrder.remark || '-'}</Descriptions.Item>
            </Descriptions>
          </div>
        )}
      </Drawer>

      <Modal
        title={editingId ? '编辑工单' : '新建工单'}
        open={editModal}
        onOk={handleSubmit}
        onCancel={() => setEditModal(false)}
        okText="保存"
        width={700}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="alertEventId" label="关联告警">
            <Select allowClear placeholder="选择关联告警（可选）">
              {alerts.map((a) => (
                <Option key={a.id} value={a.id}>
                  {a.eventNo} - {EVENT_TYPE_LABELS[a.eventType] || a.eventType}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Row gutter={16}>
            <Col span={16}>
              <Form.Item name="title" label="工单标题" rules={[{ required: true }]}>
                <Input placeholder="请输入工单标题" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="eventType" label="事件类型" rules={[{ required: true }]}>
                <Select>
                  {Object.entries(EVENT_TYPE_LABELS).map(([k, v]) => (
                    <Option key={k} value={k}>{v}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="orderLevel" label="工单等级" rules={[{ required: true }]}>
                <Select>
                  {Object.entries(EVENT_LEVEL_LABELS).map(([k, v]) => (
                    <Option key={k} value={Number(k)}>{v}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="assignDeptId" label="指派部门">
                <Select placeholder="选择部门">
                  {departments.map((d) => (
                    <Option key={d.id} value={d.id}>{d.deptName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="orderStatus" label="状态">
                <Select>
                  {Object.entries(ORDER_STATUS_LABELS).map(([k, v]) => (
                    <Option key={k} value={Number(k)}>{v}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="planStartTime" label="计划开始时间">
                <DatePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="planEndTime" label="计划结束时间">
                <DatePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="工单描述">
            <TextArea rows={3} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="处理工单"
        open={handleModal}
        onOk={confirmHandle}
        onCancel={() => setHandleModal(false)}
        okText="提交处理"
        width={600}
      >
        <Form form={handleForm} layout="vertical">
          <Form.Item
            name="handleContent"
            label="处理内容"
            rules={[{ required: true, message: '请填写处理内容' }]}
          >
            <TextArea rows={4} placeholder="请详细描述处理过程和结果" />
          </Form.Item>
          <Form.Item name="handleImages" label="现场照片">
            <Upload {...uploadProps} multiple>
              <Button icon={<UploadOutlined />}>上传图片</Button>
            </Upload>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default WorkOrders;
