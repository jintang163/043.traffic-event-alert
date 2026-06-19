import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, Card, Modal, Form, Input, Select, InputNumber, Switch, message, Popconfirm,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { notifyRuleApi, notifyChannelApi, notifyTemplateApi } from '@/services/api';
import { NotifyRule, NotifyChannel, NotifyTemplate, CHANNEL_TYPE_LABELS, EVENT_TYPE_LABELS, EVENT_LEVEL_LABELS, RECIPIENT_TYPE_OPTIONS } from '@/types';

const { Option } = Select;

const NotifyRules: React.FC = () => {
  const [data, setData] = useState<NotifyRule[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [modal, setModal] = useState(false);
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<NotifyRule | null>(null);
  const [channels, setChannels] = useState<NotifyChannel[]>([]);
  const [templates, setTemplates] = useState<NotifyTemplate[]>([]);

  const loadData = async () => {
    setLoading(true);
    try {
      const res: any = await notifyRuleApi.page({ current, size: pageSize });
      if (res.code === 200) {
        setData(res.data.records);
        setTotal(res.data.total);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadOptions = async () => {
    try {
      const chRes: any = await notifyChannelApi.list();
      if (chRes.code === 200) setChannels(chRes.data);
      const tplRes: any = await notifyTemplateApi.list();
      if (tplRes.code === 200) setTemplates(tplRes.data);
    } catch (e) { console.error(e); }
  };

  useEffect(() => { loadData(); loadOptions(); }, [current, pageSize]);

  const handleAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ enabled: 1, recipientType: 1, atAll: 0, priority: 0, sortOrder: 0 });
    setModal(true);
  };

  const handleEdit = (record: NotifyRule) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModal(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const payload = { ...editing, ...values };
      const res: any = await notifyRuleApi.save(payload);
      if (res.code === 200) {
        message.success(editing ? '更新成功' : '创建成功');
        setModal(false);
        loadData();
      }
    } catch (e) { console.error(e); }
  };

  const handleDelete = async (id: number) => {
    const res: any = await notifyRuleApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
    }
  };

  const getChannelName = (channelId?: number) => {
    const ch = channels.find(c => c.id === channelId);
    return ch ? `${ch.channelName} (${CHANNEL_TYPE_LABELS[ch.channelType] || ch.channelType})` : '-';
  };

  const getTemplateName = (templateId?: number) => {
    const tpl = templates.find(t => t.id === templateId);
    return tpl ? tpl.templateName : '-';
  };

  const columns: ColumnsType<NotifyRule> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '规则名称', dataIndex: 'ruleName', width: 150 },
    {
      title: '事件类型', dataIndex: 'eventType', width: 110,
      render: (val) => val ? <Tag>{EVENT_TYPE_LABELS[val] || val}</Tag> : <Tag color="blue">全部</Tag>,
    },
    {
      title: '事件等级', dataIndex: 'eventLevel', width: 90, align: 'center',
      render: (val) => val ? <Tag color={val >= 3 ? 'red' : val === 2 ? 'orange' : 'default'}>{EVENT_LEVEL_LABELS[val]}</Tag> : <Tag color="blue">全部</Tag>,
    },
    { title: '通知渠道', dataIndex: 'channelId', width: 150, render: (val) => getChannelName(val) },
    { title: '通知模板', dataIndex: 'templateId', width: 130, render: (val) => getTemplateName(val) },
    {
      title: '接收人类型', dataIndex: 'recipientType', width: 100,
      render: (val) => {
        const opt = RECIPIENT_TYPE_OPTIONS.find(o => o.value === val);
        return opt?.label || '-';
      },
    },
    { title: '优先级', dataIndex: 'priority', width: 80, align: 'center' },
    {
      title: '状态', dataIndex: 'enabled', width: 80, align: 'center',
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
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增规则</Button>
          <Button icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>
        </Space>
      </Card>
      <Card style={{ borderRadius: 8 }} title="推送规则">
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
        title={editing ? '编辑规则' : '新增规则'} open={modal}
        onOk={handleSave} onCancel={() => setModal(false)} width={600} destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="ruleName" label="规则名称" rules={[{ required: true }]}>
            <Input placeholder="如 紧急事件-钉钉@所有" />
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
          <Form.Item name="channelId" label="通知渠道" rules={[{ required: true }]}>
            <Select placeholder="选择通知渠道">
              {channels.map(c => <Option key={c.id} value={c.id!}>{c.channelName} ({CHANNEL_TYPE_LABELS[c.channelType]})</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="templateId" label="通知模板">
            <Select placeholder="自动匹配" allowClear>
              {templates.map(t => <Option key={t.id} value={t.id!}>{t.templateName}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="recipientType" label="接收人类型" rules={[{ required: true }]}>
            <Select>
              {RECIPIENT_TYPE_OPTIONS.map(o => <Option key={o.value} value={o.value}>{o.label}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="recipientIds" label="接收人ID列表">
            <Input placeholder="逗号分隔，如部门ID或用户ID" />
          </Form.Item>
          <Form.Item name="atAll" label="@所有人" valuePropName="checked" getValueFromEvent={(v) => v ? 1 : 0} getValueProps={(v) => ({ checked: v === 1 })}>
            <Switch />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <InputNumber min={0} placeholder="数值越小越优先" />
          </Form.Item>
          <Form.Item name="enabled" label="是否启用" valuePropName="checked" getValueFromEvent={(v) => v ? 1 : 0} getValueProps={(v) => ({ checked: v === 1 })}>
            <Switch />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="规则描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default NotifyRules;
