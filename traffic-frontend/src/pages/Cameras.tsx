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
  Switch,
  message,
  Row,
  Col,
  Popconfirm,
  Dropdown,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  VideoCameraOutlined,
  EyeOutlined,
  UpOutlined,
  DownOutlined,
  LeftOutlined,
  RightOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  PauseOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import VideoPlayer from '@/components/VideoPlayer';
import PtzPanel from '@/components/PtzPanel';
import PtzCruisePanel from '@/components/PtzCruisePanel';
import { cameraApi } from '@/services/api';
import {
  CAMERA_STATUS_LABELS,
  ONLINE_STATUS_LABELS,
  type Camera,
  type PageResult,
} from '@/types';

const { Option } = Select;

const Cameras: React.FC = () => {
  const [data, setData] = useState<Camera[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [editModal, setEditModal] = useState(false);
  const [viewModal, setViewModal] = useState(false);
  const [editForm] = Form.useForm();
  const [editingId, setEditingId] = useState<number | null>(null);
  const [viewCamera, setViewCamera] = useState<Camera | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const res: any = await cameraApi.page({
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

  useEffect(() => {
    loadData();
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
      ptzEnabled: 0,
      protocol: 'RTSP',
      direction: 1,
      laneCount: 2,
    });
    setEditModal(true);
  };

  const handleEdit = (record: Camera) => {
    setEditingId(record.id);
    editForm.setFieldsValue(record);
    setEditModal(true);
  };

  const handleDelete = async (id: number) => {
    const res: any = await cameraApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
    }
  };

  const handleView = (record: Camera) => {
    setViewCamera(record);
    setViewModal(true);
  };

  const handlePtz = async (cameraId: number, command: string) => {
    try {
      const res: any = await cameraApi.ptzControl(cameraId, { command, speed: 1 });
      if (res.code === 200) {
        message.success(`云台命令 ${command} 已发送`);
      }
    } catch (e) {
      message.error('云台控制失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await editForm.validateFields();
      const res: any = await cameraApi.save(values);
      if (res.code === 200) {
        message.success(editingId ? '编辑成功' : '添加成功');
        setEditModal(false);
        loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const ptzMenu = (record: Camera) => ({
    items: [
      { key: 'left', icon: <LeftOutlined />, label: '向左', onClick: () => handlePtz(record.id, 'LEFT') },
      { key: 'right', icon: <RightOutlined />, label: '向右', onClick: () => handlePtz(record.id, 'RIGHT') },
      { key: 'up', icon: <UpOutlined />, label: '向上', onClick: () => handlePtz(record.id, 'UP') },
      { key: 'down', icon: <DownOutlined />, label: '向下', onClick: () => handlePtz(record.id, 'DOWN') },
      { type: 'divider' as any },
      { key: 'zoomin', icon: <ZoomInOutlined />, label: '放大', onClick: () => handlePtz(record.id, 'ZOOM_IN') },
      { key: 'zoomout', icon: <ZoomOutOutlined />, label: '缩小', onClick: () => handlePtz(record.id, 'ZOOM_OUT') },
      { type: 'divider' as any },
      { key: 'stop', icon: <PauseOutlined />, label: '停止', onClick: () => handlePtz(record.id, 'STOP') },
    ],
  });

  const columns: ColumnsType<Camera> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
      align: 'center',
    },
    {
      title: '摄像头名称',
      dataIndex: 'cameraName',
      width: 180,
      render: (text, record) => (
        <a onClick={() => handleView(record)} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <VideoCameraOutlined />
          {text}
        </a>
      ),
    },
    {
      title: '设备编号',
      dataIndex: 'cameraCode',
      width: 140,
    },
    {
      title: '协议',
      dataIndex: 'protocol',
      width: 90,
      render: (text) => <Tag color="blue">{text}</Tag>,
    },
    {
      title: '厂商',
      dataIndex: 'manufacturer',
      width: 100,
    },
    {
      title: '道路',
      dataIndex: 'roadName',
      width: 120,
    },
    {
      title: '位置',
      dataIndex: 'location',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      align: 'center',
      render: (val) => (
        <Tag color={val === 1 ? 'green' : 'default'}>
          {CAMERA_STATUS_LABELS[val]}
        </Tag>
      ),
    },
    {
      title: '在线',
      dataIndex: 'onlineStatus',
      width: 80,
      align: 'center',
      render: (val) => (
        <Tag color={val === 1 ? 'green' : 'red'}>
          {ONLINE_STATUS_LABELS[val]}
        </Tag>
      ),
    },
    {
      title: '云台',
      dataIndex: 'ptzEnabled',
      width: 80,
      align: 'center',
      render: (val) => (val === 1 ? <Tag color="purple">支持</Tag> : '—'),
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
          <Tooltip title="查看视频">
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record)} />
          </Tooltip>
          {record.ptzEnabled === 1 && (
            <Dropdown menu={ptzMenu(record)} placement="bottomLeft">
              <Button type="link" size="small">云台</Button>
            </Dropdown>
          )}
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
            <Input placeholder="名称/编号" style={{ width: 180 }} allowClear />
          </Form.Item>
          <Form.Item name="protocol" label="协议">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              <Option value="RTSP">RTSP</Option>
              <Option value="RTMP">RTMP</Option>
              <Option value="GB28181">GB28181</Option>
            </Select>
          </Form.Item>
          <Form.Item name="onlineStatus" label="在线">
            <Select placeholder="全部" style={{ width: 100 }} allowClear>
              <Option value={1}>在线</Option>
              <Option value={0}>离线</Option>
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
        title="摄像头列表"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增摄像头
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
          scroll={{ x: 1400 }}
        />
      </Card>

      <Modal
        title={editingId ? '编辑摄像头' : '新增摄像头'}
        open={editModal}
        onOk={handleSubmit}
        onCancel={() => setEditModal(false)}
        okText="保存"
        width={720}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="cameraCode" label="设备编号" rules={[{ required: true }]}>
                <Input placeholder="请输入设备编号" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="cameraName" label="摄像头名称" rules={[{ required: true }]}>
                <Input placeholder="请输入名称" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="protocol" label="接入协议" rules={[{ required: true }]}>
                <Select>
                  <Option value="RTSP">RTSP</Option>
                  <Option value="RTMP">RTMP</Option>
                  <Option value="GB28181">GB/T 28181</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="manufacturer" label="厂商">
                <Select allowClear>
                  <Option value="海康">海康威视</Option>
                  <Option value="大华">大华</Option>
                  <Option value="宇视">宇视</Option>
                  <Option value="其他">其他</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="gbDeviceId" label="国标设备ID">
                <Input placeholder="GB28181时填写" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="streamUrl" label="流地址" rules={[{ required: true }]}>
            <Input placeholder="rtsp://xxx/stream1 或 rtmp://..." />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="roadName" label="道路名称">
                <Input placeholder="如：G4京港澳高速" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="direction" label="方向">
                <Select>
                  <Option value={1}>双向</Option>
                  <Option value={2}>北向/上行</Option>
                  <Option value={3}>南向/下行</Option>
                  <Option value={4}>东向</Option>
                  <Option value={5}>西向</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="location" label="位置描述">
            <Input placeholder="如：K123+450处" />
          </Form.Item>
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
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="laneCount" label="车道数">
                <InputNumber min={1} max={10} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="启用状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="ptzEnabled" label="云台控制" valuePropName="checked">
                <Switch checkedChildren="支持" unCheckedChildren="不支持" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={viewCamera?.cameraName}
        open={viewModal}
        onCancel={() => setViewModal(false)}
        footer={null}
        width={1200}
        destroyOnClose
      >
        {viewCamera && (
          <Row gutter={16}>
            <Col span={viewCamera.ptzEnabled === 1 ? 16 : 24}>
              <VideoPlayer
                url={viewCamera.streamUrl}
                cameraId={viewCamera.id}
                cameraName={viewCamera.cameraName}
                height={450}
                showControls
              />
              <Card size="small" style={{ marginTop: 12 }}>
                <Row gutter={16}>
                  <Col span={8}>
                    <div>
                      <strong>编号：</strong>{viewCamera.cameraCode}
                    </div>
                  </Col>
                  <Col span={8}>
                    <div>
                      <strong>协议：</strong>{viewCamera.protocol}
                    </div>
                  </Col>
                  <Col span={8}>
                    <div>
                      <strong>厂商：</strong>{viewCamera.manufacturer || '-'}
                    </div>
                  </Col>
                  <Col span={24} style={{ marginTop: 8 }}>
                    <div>
                      <strong>位置：</strong>{viewCamera.location || viewCamera.roadName || '-'}
                    </div>
                  </Col>
                  {viewCamera.longitude && viewCamera.latitude && (
                    <Col span={24} style={{ marginTop: 8 }}>
                      <div>
                        <strong>坐标：</strong>{viewCamera.longitude}, {viewCamera.latitude}
                      </div>
                    </Col>
                  )}
                </Row>
              </Card>
            </Col>
            {viewCamera.ptzEnabled === 1 && (
              <Col span={8}>
                <PtzPanel cameraId={viewCamera.id} ptzEnabled={viewCamera.ptzEnabled} />
                <div style={{ height: 12 }} />
                <PtzCruisePanel cameraId={viewCamera.id} ptzEnabled={viewCamera.ptzEnabled} />
              </Col>
            )}
          </Row>
        )}
      </Modal>
    </div>
  );
};

export default Cameras;
