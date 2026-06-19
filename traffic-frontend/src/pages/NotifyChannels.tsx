import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, Card, Modal, Form, Input, Select, Switch, InputNumber, message, Popconfirm,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { notifyChannelApi } from '@/services/api';
import { NotifyChannel, CHANNEL_TYPE_OPTIONS, CHANNEL_TYPE_LABELS } from '@/types';

const { Option } = Select;
const { TextArea } = Input;

const NotifyChannels: React.FC = () => {
  const [data, setData] = useState<NotifyChannel[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState(false);
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<NotifyChannel | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const res: any = await notifyChannelApi.page({ current, size: pageSize });
      if (res.code === 200) {
        setData(res.data.records);
        setTotal(res.data.total);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [current, pageSize]);

  const handleAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ enabled: 1, sortOrder: 0 });
    setModal(true);
  };

  const handleEdit = (record: NotifyChannel) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModal(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const payload = { ...editing, ...values };
      const res: any = await notifyChannelApi.save(payload);
      if (res.code === 200) {
        message.success(editing ? '更新成功' : '创建成功');
        setModal(false);
        loadData();
      }
    } catch (e) { console.error(e); }
  };

  const handleDelete = async (id: number) => {
    const res: any = await notifyChannelApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
    }
  };

  const columns: ColumnsType<NotifyChannel> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '渠道编码', dataIndex: 'channelCode', width: 130 },
    { title: '渠道名称', dataIndex: 'channelName', width: 130 },
    {
      title: '渠道类型', dataIndex: 'channelType', width: 130,
      render: (val) => {
        const opt = CHANNEL_TYPE_OPTIONS.find(o => o.value === val);
        return <Tag color={opt?.color}>{CHANNEL_TYPE_LABELS[val] || val}</Tag>;
      },
    },
    {
      title: '状态', dataIndex: 'enabled', width: 80, align: 'center',
      render: (val) => <Tag color={val === 1 ? 'success' : 'default'}>{val === 1 ? '启用' : '禁用'}</Tag>,
    },
    { title: '排序', dataIndex: 'sortOrder', width: 70, align: 'center' },
    {
      title: '配置', dataIndex: 'configJson', width: 200, ellipsis: true,
      render: (val) => <span style={{ color: '#8c8c8c', fontSize: 12 }}>{val || '-'}</span>,
    },
    { title: '描述', dataIndex: 'description', width: 150, ellipsis: true },
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
      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增渠道</Button>
          <Button icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>
        </Space>
      </Card>
      <Card style={{ borderRadius: 8 }} title="通知渠道">
        <Table
          columns={columns} dataSource={data} rowKey="id" loading={loading}
          pagination={{
            current, pageSize, total, showSizeChanger: true, showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (c, s) => { setCurrent(c); setPageSize(s); },
          }}
          scroll={{ x: 1100 }}
        />
      </Card>
      <Modal
        title={editing ? '编辑渠道' : '新增渠道'} open={modal}
        onOk={handleSave} onCancel={() => setModal(false)} width={600} destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="channelCode" label="渠道编码" rules={[{ required: true }]}>
            <Input disabled={!!editing} placeholder="如 DINGTALK, SMS_ALIYUN" />
          </Form.Item>
          <Form.Item name="channelName" label="渠道名称" rules={[{ required: true }]}>
            <Input placeholder="如 钉钉机器人" />
          </Form.Item>
          <Form.Item name="channelType" label="渠道类型" rules={[{ required: true }]}>
            <Select placeholder="选择渠道类型">
              {CHANNEL_TYPE_OPTIONS.map(o => <Option key={o.value} value={o.value}>{o.label}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="enabled" label="是否启用" valuePropName="checked" getValueFromEvent={(v) => v ? 1 : 0} getValueProps={(v) => ({ checked: v === 1 })}>
            <Switch />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber min={0} />
          </Form.Item>
          <Form.Item name="configJson" label="渠道配置JSON">
            <TextArea rows={6} placeholder='{"webhook":"...","secret":"..."}' />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="渠道描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default NotifyChannels;
