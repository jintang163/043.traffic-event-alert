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
  InputNumber,
  message,
  Popconfirm,
  Tooltip,
  Row,
  Col,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  PhoneOutlined,
  EnvironmentOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
  CarOutlined,
} from '@ant-design/icons';
import { departmentApi } from '@/services/api';
import { type Department } from '@/types';

const { Option } = Select;

const Departments: React.FC = () => {
  const [data, setData] = useState<Department[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [editModal, setEditModal] = useState(false);
  const [editForm] = Form.useForm();
  const [editingId, setEditingId] = useState<number | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const res: any = await departmentApi.list(values.deptType);
      if (res.code === 200) {
        setData(res.data);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSearch = () => loadData();
  const handleReset = () => {
    searchForm.resetFields();
    loadData();
  };

  const handleAdd = () => {
    setEditingId(null);
    editForm.resetFields();
    editForm.setFieldsValue({ status: 1, deptType: 1 });
    setEditModal(true);
  };

  const handleEdit = (record: Department) => {
    setEditingId(record.id);
    editForm.setFieldsValue(record);
    setEditModal(true);
  };

  const handleDelete = async (id: number) => {
    const res: any = await departmentApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await editForm.validateFields();
      const res: any = await departmentApi.save(values);
      if (res.code === 200) {
        message.success(editingId ? '编辑成功' : '添加成功');
        setEditModal(false);
        loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const getDeptTypeLabel = (type: number) => {
    const labels: Record<number, { text: string; color: string; icon: any }> = {
      1: { text: '养护部门', color: 'blue', icon: <CarOutlined /> },
      2: { text: '交警部门', color: 'red', icon: <SafetyCertificateOutlined /> },
    };
    return labels[type] || { text: '其他', color: 'default', icon: <TeamOutlined /> };
  };

  const columns: ColumnsType<Department> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
      align: 'center',
    },
    {
      title: '部门编号',
      dataIndex: 'deptCode',
      width: 140,
    },
    {
      title: '部门名称',
      dataIndex: 'deptName',
      width: 220,
      render: (text) => (
        <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <TeamOutlined />
          {text}
        </span>
      ),
    },
    {
      title: '部门类型',
      dataIndex: 'deptType',
      width: 120,
      align: 'center',
      render: (val) => {
        const type = getDeptTypeLabel(val);
        return (
          <Tag icon={type.icon} color={type.color}>
            {type.text}
          </Tag>
        );
      },
    },
    {
      title: '联系人',
      dataIndex: 'contactPerson',
      width: 120,
      render: (text) => text || '-',
    },
    {
      title: '联系电话',
      dataIndex: 'contactPhone',
      width: 140,
      render: (text) => (
        text ? (
          <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <PhoneOutlined />
            {text}
          </span>
        ) : '-'
      ),
    },
    {
      title: '坐标位置',
      dataIndex: 'longitude',
      width: 200,
      render: (_, record) => (
        record.longitude && record.latitude ? (
          <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <EnvironmentOutlined />
            {record.longitude.toFixed(6)}, {record.latitude.toFixed(6)}
          </span>
        ) : '-'
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      align: 'center',
      render: (val) => (
        <Tag color={val === 1 ? 'green' : 'default'}>
          {val === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
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
      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="keyword" label="关键词">
            <Input placeholder="编号/名称/联系人" style={{ width: 180 }} allowClear />
          </Form.Item>
          <Form.Item name="deptType" label="类型">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              <Option value={1}>养护部门</Option>
              <Option value={2}>交警部门</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="全部" style={{ width: 100 }} allowClear>
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
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
        title="部门列表"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增部门
          </Button>
        }
      >
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
          }}
          scroll={{ x: 1400 }}
        />
      </Card>

      <Modal
        title={editingId ? '编辑部门' : '新增部门'}
        open={editModal}
        onOk={handleSubmit}
        onCancel={() => setEditModal(false)}
        okText="保存"
        width={600}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="deptCode" label="部门编号" rules={[{ required: true }]}>
                <Input placeholder="请输入部门编号" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="deptName" label="部门名称" rules={[{ required: true }]}>
                <Input placeholder="请输入部门名称" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="deptType" label="部门类型" rules={[{ required: true }]}>
                <Select>
                  <Option value={1}>养护部门</Option>
                  <Option value={2}>交警部门</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="状态">
                <Select>
                  <Option value={1}>启用</Option>
                  <Option value={0}>禁用</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="contactPerson" label="联系人">
                <Input placeholder="请输入联系人姓名" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="contactPhone" label="联系电话">
                <Input placeholder="请输入联系电话" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="longitude" label="经度">
                <InputNumber
                  style={{ width: '100%' }}
                  precision={6}
                  step={0.000001}
                  placeholder="如：113.264385"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="latitude" label="纬度">
                <InputNumber
                  style={{ width: '100%' }}
                  precision={6}
                  step={0.000001}
                  placeholder="如：23.12908"
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="请输入部门描述信息" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Departments;
