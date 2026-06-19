import React, { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Tag,
  Card,
  Modal,
  Form,
  Input,
  InputNumber,
  Switch,
  Select,
  message,
  Popconfirm,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { policeSystemConfigApi } from '@/services/api';
import {
  type PoliceSystemConfig,
  AUTH_TYPE_OPTIONS,
} from '@/types';

const { Option } = Select;
const { TextArea } = Input;

const PoliceSystemConfigs: React.FC = () => {
  const [data, setData] = useState<PoliceSystemConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<PoliceSystemConfig | null>(null);
  const [form] = Form.useForm<PoliceSystemConfig>();

  const loadData = async () => {
    setLoading(true);
    try {
      const res: any = await policeSystemConfigApi.list();
      if (res.code === 200) setData(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      authType: 'NONE',
      enabled: 1,
      retryMax: 5,
      retryInitialSeconds: 30,
      retryMultiplier: 2,
      retryMaxSeconds: 900,
      timeoutSeconds: 10,
    });
    setModalOpen(true);
  };

  const handleEdit = (record: PoliceSystemConfig) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      const res: any = await policeSystemConfigApi.delete(id);
      if (res.code === 200) {
        message.success('删除成功');
        loadData();
      }
    } catch (_) {}
  };

  const handleToggle = async (record: PoliceSystemConfig) => {
    try {
      const next: PoliceSystemConfig = { ...record, enabled: record.enabled === 1 ? 0 : 1 };
      const res: any = record.id
        ? await policeSystemConfigApi.update(record.id, next)
        : await policeSystemConfigApi.save(next);
      if (res.code === 200) {
        message.success(next.enabled === 1 ? '已启用' : '已停用');
        loadData();
      }
    } catch (_) {}
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const res: any = editing?.id
        ? await policeSystemConfigApi.update(editing.id, values)
        : await policeSystemConfigApi.save(values);
      if (res.code === 200) {
        message.success(editing?.id ? '更新成功' : '创建成功');
        setModalOpen(false);
        loadData();
      }
    } catch (_) {}
  };

  const columns: ColumnsType<PoliceSystemConfig> = [
    {
      title: '系统代号',
      dataIndex: 'systemCode',
      width: 160,
      render: (v) => v || '-',
    },
    { title: '系统名称', dataIndex: 'systemName', width: 180, render: (v) => v || '-' },
    {
      title: '推送URL',
      dataIndex: 'pushUrl',
      ellipsis: true,
      render: (v) => v || '-',
    },
    {
      title: '认证方式',
      dataIndex: 'authType',
      width: 120,
      render: (v) => {
        const opt = AUTH_TYPE_OPTIONS.find((o) => o.value === v);
        return opt ? opt.label : v || '-';
      },
    },
    {
      title: '重试',
      width: 100,
      align: 'center',
      render: (_, r) => `最多${r.retryMax ?? '-'}次`,
    },
    {
      title: '超时',
      dataIndex: 'timeoutSeconds',
      width: 90,
      align: 'center',
      render: (v) => (v != null ? `${v}s` : '-'),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      align: 'center',
      render: (v, r) => (
        <Tag color={v === 1 ? 'success' : 'default'}>
          <Switch size="small" checked={v === 1} onChange={() => handleToggle(r)} />
          <span style={{ marginLeft: 6 }}>{v === 1 ? '启用' : '停用'}</span>
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v) => v || '-' },
    {
      title: '操作',
      width: 150,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(r)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该配置？" onConfirm={() => handleDelete(r.id!)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card>
        <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 15, fontWeight: 600 }}>交警系统配置</span>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadData}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
              新增配置
            </Button>
          </Space>
        </div>
        <Table
          size="small"
          rowKey="id"
          loading={loading}
          dataSource={data}
          columns={columns}
          pagination={false}
          scroll={{ x: 1200 }}
        />
      </Card>

      <Modal
        title={editing?.id ? '编辑交警系统' : '新增交警系统'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        width={600}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            label="系统代号"
            name="systemCode"
            rules={[{ required: true, message: '请输入系统代号' }]}
          >
            <Input placeholder="如: TRAFFIC_POLICE_PRIMARY" disabled={!!editing?.id} />
          </Form.Item>
          <Form.Item
            label="系统名称"
            name="systemName"
            rules={[{ required: true, message: '请输入系统名称' }]}
          >
            <Input placeholder="如: 市交警支队系统" />
          </Form.Item>
          <Form.Item
            label="推送URL (Webhook)"
            name="pushUrl"
            rules={[
              { required: true, message: '请输入推送URL' },
              { type: 'url', message: '请输入合法的URL' },
            ]}
          >
            <Input placeholder="https://police.example.com/api/event/reverse" />
          </Form.Item>
          <Form.Item
            label="认证方式"
            name="authType"
            rules={[{ required: true, message: '请选择认证方式' }]}
          >
            <Select>
              {AUTH_TYPE_OPTIONS.map((o) => (
                <Option key={o.value} value={o.value}>
                  {o.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, n) => p.authType !== n.authType}>
            {({ getFieldValue }) => {
              const authType = getFieldValue('authType');
              if (authType === 'TOKEN') {
                return (
                  <Form.Item label="Token" name="authToken" rules={[{ required: true, message: '请输入Token' }]}>
                    <Input.Password placeholder="Authorization: Bearer xxx" />
                  </Form.Item>
                );
              }
              if (authType === 'BASIC') {
                return (
                  <Space.Compact style={{ width: '100%' }}>
                    <Form.Item
                      label="用户名"
                      name="basicUsername"
                      rules={[{ required: true, message: '请输入用户名' }]}
                      style={{ flex: 1, marginRight: 8 }}
                    >
                      <Input placeholder="用户名" />
                    </Form.Item>
                    <Form.Item
                      label="密码"
                      name="basicPassword"
                      rules={[{ required: true, message: '请输入密码' }]}
                      style={{ flex: 1 }}
                    >
                      <Input.Password placeholder="密码" />
                    </Form.Item>
                  </Space.Compact>
                );
              }
              return null;
            }}
          </Form.Item>
          <Form.Item label="启用状态" name="enabled" valuePropName="checked" getValueFromEvent={(v) => (v ? 1 : 0)}>
            <Switch />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item label="最大重试次数" name="retryMax" style={{ flex: 1 }}>
              <InputNumber min={0} max={50} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="初始等待(秒)" name="retryInitialSeconds" style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="退避乘数" name="retryMultiplier" style={{ flex: 1 }}>
              <InputNumber min={1} step={0.5} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item label="最大等待(秒)" name="retryMaxSeconds" style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="请求超时(秒)" name="timeoutSeconds" style={{ flex: 1 }}>
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item label="备注" name="remark">
            <TextArea rows={2} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default PoliceSystemConfigs;
