import React, { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Button,
  Form,
  Input,
  InputNumber,
  Switch,
  Select,
  Space,
  Tag,
  Modal,
  message,
  List,
  Tooltip,
  Popconfirm,
  Divider,
  Row,
  Col,
} from 'antd';
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { ptzCruiseApi, ptzPresetApi } from '@/services/api';

interface PtzCruisePanelProps {
  cameraId: number;
  ptzEnabled?: number;
}

interface CruiseItem {
  id: number;
  cameraId: number;
  cruiseName: string;
  cruiseType?: number;
  status: number;
  staySeconds?: number;
  speed?: number;
  loopCount?: number;
  eventLinkage?: number;
  eventReturnSeconds?: number;
  description?: string;
}

interface PresetItem {
  id: number;
  presetIndex: number;
  presetName: string;
}

const PtzCruisePanel: React.FC<PtzCruisePanelProps> = ({ cameraId, ptzEnabled = 1 }) => {
  const [cruises, setCruises] = useState<CruiseItem[]>([]);
  const [presets, setPresets] = useState<PresetItem[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const loadCruises = useCallback(async () => {
    if (!cameraId) return;
    try {
      const res = await ptzCruiseApi.listByCamera(cameraId);
      if (res.code === 200) {
        setCruises(res.data || []);
      }
    } catch (e) {
      console.error('加载巡航列表失败', e);
    }
  }, [cameraId]);

  const loadPresets = useCallback(async () => {
    if (!cameraId) return;
    try {
      const res = await ptzPresetApi.listByCamera(cameraId);
      if (res.code === 200) {
        setPresets(res.data || []);
      }
    } catch (e) {
      console.error('加载预置位失败', e);
    }
  }, [cameraId]);

  useEffect(() => {
    loadCruises();
    loadPresets();
  }, [loadCruises, loadPresets]);

  const handleAdd = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({
      cruiseType: 1,
      staySeconds: 10,
      speed: 5,
      loopCount: 0,
      eventLinkage: 0,
      eventReturnSeconds: 30,
      presetIds: [],
    });
    setModalVisible(true);
  };

  const handleEdit = async (item: CruiseItem) => {
    setEditingId(item.id);
    try {
      const res = await ptzCruiseApi.get(item.id);
      if (res.code === 200) {
        const detail = res.data;
        form.setFieldsValue({
          ...detail,
          presetIds: (detail.points || []).map((p: any) => p.presetId),
        });
        setModalVisible(true);
      }
    } catch (e) {
      message.error('加载巡航详情失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const payload = {
        ...values,
        id: editingId || undefined,
        cameraId,
      };

      const res = await ptzCruiseApi.save(payload);
      if (res.code === 200) {
        message.success(editingId ? '更新成功' : '创建成功');
        setModalVisible(false);
        loadCruises();
      }
    } catch (e: any) {
      if (e.errorFields) return;
      message.error(editingId ? '更新失败' : '创建失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await ptzCruiseApi.delete(id);
      message.success('删除成功');
      loadCruises();
    } catch (e) {
      message.error('删除失败');
    }
  };

  const handleStart = async (id: number) => {
    try {
      await ptzCruiseApi.start(id);
      message.success('巡航已启动');
      loadCruises();
    } catch (e) {
      message.error('启动巡航失败');
    }
  };

  const handleStop = async (id: number) => {
    try {
      await ptzCruiseApi.stop(id);
      message.success('巡航已停止');
      loadCruises();
    } catch (e) {
      message.error('停止巡航失败');
    }
  };

  if (ptzEnabled !== 1) {
    return null;
  }

  return (
    <Card
      size="small"
      title={
        <Space>
          <EyeOutlined />
          <span>巡航管理</span>
        </Space>
      }
      extra={
        <Button
          type="primary"
          size="small"
          icon={<PlusOutlined />}
          onClick={handleAdd}
        >
          新建
        </Button>
      }
    >
      <List
        size="small"
        dataSource={cruises}
        locale={{ emptyText: '暂无巡航路线，请先添加预置位再创建巡航' }}
        renderItem={(item) => (
          <List.Item
            key={item.id}
            actions={[
              item.status === 1 ? (
                <Tooltip key="stop" title="停止巡航">
                  <Button
                    type="link"
                    size="small"
                    danger
                    icon={<PauseCircleOutlined />}
                    onClick={() => handleStop(item.id)}
                  />
                </Tooltip>
              ) : (
                <Tooltip key="start" title="开始巡航">
                  <Button
                    type="link"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    onClick={() => handleStart(item.id)}
                  />
                </Tooltip>
              ),
              <Tooltip key="edit" title="编辑">
                <Button
                  type="link"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => handleEdit(item)}
                />
              </Tooltip>,
              <Popconfirm
                key="del"
                title="确定删除该巡航路线？"
                onConfirm={() => handleDelete(item.id)}
                disabled={item.status === 1}
              >
                <Button
                  type="link"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  disabled={item.status === 1}
                />
              </Popconfirm>,
            ]}
          >
            <List.Item.Meta
              title={
                <Space size={4}>
                  <span style={{ fontSize: 13, fontWeight: 500 }}>{item.cruiseName}</span>
                  {item.status === 1 ? (
                    <Tag color="green" size="small">
                      <PlayCircleOutlined /> 巡航中
                    </Tag>
                  ) : (
                    <Tag color="default" size="small">已停止</Tag>
                  )}
                  {item.eventLinkage === 1 && (
                    <Tag color="orange" size="small" icon={<ThunderboltOutlined />}>
                      事件联动
                    </Tag>
                  )}
                </Space>
              }
              description={
                <Space size={12} style={{ fontSize: 12, color: '#999' }}>
                  <span><ClockCircleOutlined /> 停留{item.staySeconds || 10}秒</span>
                  <span>速度{item.speed || 5}</span>
                  <span>
                    {item.loopCount === 0 ? '无限循环' : `循环${item.loopCount}次`}
                  </span>
                </Space>
              }
            />
          </List.Item>
        )}
      />

      <Modal
        title={editingId ? '编辑巡航路线' : '新建巡航路线'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        confirmLoading={loading}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="cruiseName"
            label="巡航名称"
            rules={[{ required: true, message: '请输入巡航名称' }]}
          >
            <Input placeholder="请输入巡航路线名称" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="staySeconds" label="停留时间(秒)">
                <InputNumber min={1} max={300} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="speed" label="巡航速度">
                <InputNumber min={1} max={10} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="loopCount" label="循环次数">
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider style={{ margin: '8px 0' }} />

          <Form.Item name="eventLinkage" label="事件联动">
            <Switch
              checkedChildren={<ThunderboltOutlined />}
              unCheckedChildren="关闭"
            />
            <span style={{ marginLeft: 8, fontSize: 12, color: '#666' }}>
              检测到事件时暂停巡航，处理完恢复
            </span>
          </Form.Item>

          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.eventLinkage !== curr.eventLinkage}
          >
            {({ getFieldValue }) =>
              getFieldValue('eventLinkage') === 1 ? (
                <Form.Item name="eventReturnSeconds" label="事件后恢复时间(秒)">
                  <InputNumber min={10} max={300} style={{ width: 160 }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>

          <Divider style={{ margin: '8px 0' }} />

          <Form.Item
            name="presetIds"
            label="巡航预置位（按顺序选择）"
            rules={[{ required: true, message: '请选择至少一个预置位' }]}
          >
            <Select
              mode="multiple"
              placeholder="请选择巡航要经过的预置位"
              style={{ width: '100%' }}
            >
              {presets.map((p) => (
                <Select.Option key={p.id} value={p.id}>
                  [{p.presetIndex}] {p.presetName}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item name="description" label="备注">
            <Input.TextArea rows={2} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default PtzCruisePanel;
