import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, Card, Modal, Form, Input, Select, DatePicker, InputNumber, Switch, message, Popconfirm, Row, Col, Statistic,
} from 'antd';
import { PlusOutlined, ReloadOutlined, PhoneOutlined, UserOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { onDutyApi } from '@/services/api';
import { OnDuty, DUTY_TYPE_OPTIONS } from '@/types';

const { Option } = Select;

const OnDutyPage: React.FC = () => {
  const [data, setData] = useState<OnDuty[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState(false);
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<OnDuty | null>(null);
  const [currentDuty, setCurrentDuty] = useState<OnDuty[]>([]);

  const loadData = async () => {
    setLoading(true);
    try {
      const res: any = await onDutyApi.page({ current, size: pageSize, status: 1 });
      if (res.code === 200) {
        setData(res.data.records);
        setTotal(res.data.total);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadCurrentDuty = async () => {
    try {
      const res: any = await onDutyApi.current();
      if (res.code === 200) setCurrentDuty(res.data || []);
    } catch (e) { console.error(e); }
  };

  useEffect(() => { loadData(); loadCurrentDuty(); }, [current, pageSize]);

  const handleAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ dutyType: 1, status: 1 });
    setModal(true);
  };

  const handleEdit = (record: OnDuty) => {
    setEditing(record);
    form.setFieldsValue({
      ...record,
      dutyDate: record.dutyDate ? window.dayjs?.(record.dutyDate) : undefined,
    });
    setModal(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        ...editing,
        ...values,
        dutyDate: values.dutyDate ? values.dutyDate.format('YYYY-MM-DD') : undefined,
      };
      const res: any = await onDutyApi.save(payload);
      if (res.code === 200) {
        message.success(editing ? '更新成功' : '创建成功');
        setModal(false);
        loadData();
        loadCurrentDuty();
      }
    } catch (e) { console.error(e); }
  };

  const handleDelete = async (id: number) => {
    const res: any = await onDutyApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
      loadCurrentDuty();
    }
  };

  const getDutyTypeLabel = (type?: number) => {
    const opt = DUTY_TYPE_OPTIONS.find(o => o.value === type);
    return opt?.label || '-';
  };

  const columns: ColumnsType<OnDuty> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    {
      title: '值班人', dataIndex: 'userName', width: 100,
      render: (val) => <span><UserOutlined /> {val || '-'}</span>,
    },
    {
      title: '手机号', dataIndex: 'phone', width: 130,
      render: (val) => val ? <span><PhoneOutlined /> {val}</span> : <span style={{ color: '#bbb' }}>-</span>,
    },
    { title: '部门', dataIndex: 'deptName', width: 130 },
    { title: '值班日期', dataIndex: 'dutyDate', width: 110 },
    {
      title: '班次', dataIndex: 'dutyType', width: 80, align: 'center',
      render: (val) => <Tag color={val === 3 ? 'blue' : val === 2 ? 'purple' : 'orange'}>{getDutyTypeLabel(val)}</Tag>,
    },
    { title: '开始时间', dataIndex: 'startTime', width: 160 },
    { title: '结束时间', dataIndex: 'endTime', width: 160 },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (val) => <Tag color={val === 1 ? 'success' : 'default'}>{val === 1 ? '有效' : '无效'}</Tag>,
    },
    { title: '备注', dataIndex: 'remark', width: 120, ellipsis: true },
    {
      title: '操作', key: 'action', width: 140, fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDelete(record.id!)} okText="确认" cancelText="取消">
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={8}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="当前值班" value={currentDuty.length} suffix="人"
              valueStyle={{ color: '#1890ff' }} prefix={<UserOutlined />} />
          </Card>
        </Col>
        <Col xs={12} sm={8}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="白班" value={currentDuty.filter(d => d.dutyType === 1 || d.dutyType === 3).length} suffix="人"
              valueStyle={{ color: '#faad14' }} />
          </Card>
        </Col>
        <Col xs={12} sm={8}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="夜班" value={currentDuty.filter(d => d.dutyType === 2 || d.dutyType === 3).length} suffix="人"
              valueStyle={{ color: '#722ed1' }} />
          </Card>
        </Col>
      </Row>

      {currentDuty.length > 0 && (
        <Card style={{ borderRadius: 8, marginBottom: 16 }} title="当前值班人员" size="small">
          <Space wrap>
            {currentDuty.map(d => (
              <Tag key={d.id} color="blue" style={{ fontSize: 13, padding: '4px 12px' }}>
                <UserOutlined /> {d.userName}
                {d.phone && <span style={{ marginLeft: 6, color: '#fff' }}><PhoneOutlined /> {d.phone}</span>}
                <span style={{ marginLeft: 6 }}>({getDutyTypeLabel(d.dutyType)})</span>
              </Tag>
            ))}
          </Space>
        </Card>
      )}

      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增排班</Button>
          <Button icon={<ReloadOutlined />} onClick={() => { loadData(); loadCurrentDuty(); }}>刷新</Button>
        </Space>
      </Card>

      <Card style={{ borderRadius: 8 }} title="值班排班">
        <Table
          columns={columns} dataSource={data} rowKey="id" loading={loading}
          pagination={{
            current, pageSize, total, showSizeChanger: true, showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (c, s) => { setCurrent(c); setPageSize(s); },
          }}
          scroll={{ x: 1200 }}
        />
      </Card>
      <Modal
        title={editing ? '编辑排班' : '新增排班'} open={modal}
        onOk={handleSave} onCancel={() => setModal(false)} width={600} destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="userId" label="用户ID" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} placeholder="系统用户ID" />
          </Form.Item>
          <Form.Item name="userName" label="姓名">
            <Input placeholder="值班人员姓名" />
          </Form.Item>
          <Form.Item name="phone" label="值班手机号">
            <Input placeholder="接收语音/短信的手机号" />
          </Form.Item>
          <Form.Item name="deptId" label="部门ID">
            <InputNumber style={{ width: '100%' }} placeholder="部门ID" />
          </Form.Item>
          <Form.Item name="deptName" label="部门名称">
            <Input placeholder="部门名称" />
          </Form.Item>
          <Form.Item name="dutyDate" label="值班日期" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="dutyType" label="班次" rules={[{ required: true }]}>
            <Select>
              {DUTY_TYPE_OPTIONS.map(o => <Option key={o.value} value={o.value}>{o.label}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态" valuePropName="checked" getValueFromEvent={(v) => v ? 1 : 0} getValueProps={(v) => ({ checked: v === 1 })}>
            <Switch />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input placeholder="备注" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default OnDutyPage;
