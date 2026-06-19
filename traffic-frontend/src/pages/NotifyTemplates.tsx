import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, Card, Modal, Form, Input, Select, InputNumber, message, Popconfirm, Tooltip,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { notifyTemplateApi } from '@/services/api';
import { NotifyTemplate, CHANNEL_TYPE_OPTIONS, CHANNEL_TYPE_LABELS, EVENT_TYPE_LABELS, EVENT_LEVEL_LABELS } from '@/types';

const { Option } = Select;
const { TextArea } = Input;

const TEMPLATE_VARS = [
  '${eventType}', '${eventTypeText}', '${eventLevel}', '${levelText}',
  '${cameraName}', '${location}', '${eventTime}', '${description}',
  '${confidence}', '${eventNo}', '${debrisCategory}', '${debrisCategoryText}',
  '${accidentSeverity}', '${accidentSeverityText}',
];

const NotifyTemplates: React.FC = () => {
  const [data, setData] = useState<NotifyTemplate[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState(false);
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<NotifyTemplate | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const res: any = await notifyTemplateApi.page({ current, size: pageSize });
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
    form.setFieldsValue({ status: 1 });
    setModal(true);
  };

  const handleEdit = (record: NotifyTemplate) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModal(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const payload = { ...editing, ...values };
      const res: any = await notifyTemplateApi.save(payload);
      if (res.code === 200) {
        message.success(editing ? '更新成功' : '创建成功');
        setModal(false);
        loadData();
      }
    } catch (e) { console.error(e); }
  };

  const handleDelete = async (id: number) => {
    const res: any = await notifyTemplateApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
    }
  };

  const columns: ColumnsType<NotifyTemplate> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '模板编码', dataIndex: 'templateCode', width: 160 },
    { title: '模板名称', dataIndex: 'templateName', width: 140 },
    {
      title: '渠道类型', dataIndex: 'channelType', width: 120,
      render: (val) => {
        const opt = CHANNEL_TYPE_OPTIONS.find(o => o.value === val);
        return <Tag color={opt?.color}>{CHANNEL_TYPE_LABELS[val] || val}</Tag>;
      },
    },
    {
      title: '事件类型', dataIndex: 'eventType', width: 110,
      render: (val) => val ? <Tag>{EVENT_TYPE_LABELS[val] || val}</Tag> : <Tag color="blue">全部</Tag>,
    },
    {
      title: '事件等级', dataIndex: 'eventLevel', width: 90, align: 'center',
      render: (val) => val ? <Tag color={val >= 3 ? 'red' : val === 2 ? 'orange' : 'default'}>{EVENT_LEVEL_LABELS[val]}</Tag> : <Tag color="blue">全部</Tag>,
    },
    {
      title: '标题模板', dataIndex: 'titleTemplate', width: 140, ellipsis: true,
    },
    {
      title: '内容模板', dataIndex: 'contentTemplate', width: 220, ellipsis: true,
    },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (val) => <Tag color={val === 1 ? 'success' : 'default'}>{val === 1 ? '启用' : '禁用'}</Tag>,
    },
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
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增模板</Button>
          <Button icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>
        </Space>
      </Card>
      <Card style={{ borderRadius: 8 }} title="通知模板">
        <Table
          columns={columns} dataSource={data} rowKey="id" loading={loading}
          pagination={{
            current, pageSize, total, showSizeChanger: true, showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (c, s) => { setCurrent(c); setPageSize(s); },
          }}
          scroll={{ x: 1300 }}
        />
      </Card>
      <Modal
        title={editing ? '编辑模板' : '新增模板'} open={modal}
        onOk={handleSave} onCancel={() => setModal(false)} width={700} destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="templateCode" label="模板编码" rules={[{ required: true }]}>
            <Input disabled={!!editing} placeholder="如 TPL_DINGTALK_DEFAULT" />
          </Form.Item>
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true }]}>
            <Input placeholder="如 钉钉默认模板" />
          </Form.Item>
          <Form.Item name="channelType" label="适用渠道类型" rules={[{ required: true }]}>
            <Select placeholder="选择渠道类型">
              {CHANNEL_TYPE_OPTIONS.map(o => <Option key={o.value} value={o.value}>{o.label}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="eventType" label="事件类型">
            <Select placeholder="全部" allowClear>
              {Object.entries(EVENT_TYPE_LABELS).map(([k, v]) => <Option key={k} value={k}>{v}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="eventLevel" label="事件等级">
            <Select placeholder="全部" allowClear>
              {Object.entries(EVENT_LEVEL_LABELS).map(([k, v]) => <Option key={k} value={Number(k)}>{v}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="titleTemplate" label="标题模板">
            <Input placeholder="通知标题" />
          </Form.Item>
          <Form.Item
            name="contentTemplate" label="内容模板" rules={[{ required: true }]}
            extra={<div style={{ marginTop: 4, color: '#8c8c8c', fontSize: 12 }}>可用变量: {TEMPLATE_VARS.join(', ')}</div>}
          >
            <TextArea rows={6} placeholder="支持变量: ${eventType}, ${cameraName}, ${location}..." />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select>
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
            </Select>
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="模板描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default NotifyTemplates;
