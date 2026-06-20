import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, Input, Select, Card, Modal, Form,
  InputNumber, Switch, message, Row, Col, Popconfirm, Tooltip, Divider,
  DatePicker, Statistic, Progress, List, Badge,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined,
  DeleteOutlined, PlayCircleOutlined, StopOutlined,
  SafetyOutlined, AlertOutlined, CarOutlined,
  EyeOutlined, BarChartOutlined,
} from '@ant-design/icons';
import { constructionApi, cameraApi, geoFenceApi } from '@/services/api';
import { type Camera } from '@/types';
import ConstructionLayer from '@/components/ConstructionLayer';
import ConstructionMapLayer from '@/components/ConstructionMapLayer';

const { Option } = Select;
const { RangePicker } = DatePicker;

interface ConstructionPlan {
  id: number;
  planCode: string;
  planName: string;
  constructionType: number;
  constructionTypeLabel: string;
  cameraId: number;
  cameraName: string;
  fenceId: number;
  bufferFenceId: number;
  roadName: string;
  location: string;
  planStartTime: string;
  planEndTime: string;
  actualStartTime: string;
  actualEndTime: string;
  planStatus: number;
  planStatusLabel: string;
  speedLimit: number;
  standardConeCount: number;
  bufferDistance: number;
  polygonPoints?: string;
  polygonPointsPixel?: string;
  alertEnabled: number;
  alertLevel: number;
  eventCount: number;
  coneAlertCount: number;
  intrusionAlertCount: number;
  speedingAlertCount: number;
  description: string;
  createTime: string;
}

interface ConeDetectionRecord {
  id: number;
  recordNo: string;
  planId: number;
  planName: string;
  cameraId: number;
  cameraName: string;
  detectionTime: string;
  detectedConeCount: number;
  standardConeCount: number;
  missingConeCount: number;
  isCompliant: number;
  complianceRate: number;
  avgConfidence: number;
  alertTriggered: number;
  alertLevel: number;
  description: string;
}

const ConstructionZones: React.FC = () => {
  const [data, setData] = useState<ConstructionPlan[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [editModal, setEditModal] = useState(false);
  const [editForm] = Form.useForm();
  const [editingId, setEditingId] = useState<number | null>(null);
  const [cameras, setCameras] = useState<Camera[]>([]);

  const [detailModal, setDetailModal] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState<ConstructionPlan | null>(null);
  const [planSummary, setPlanSummary] = useState<any>(null);
  const [coneRecords, setConeRecords] = useState<ConeDetectionRecord[]>([]);

  const [layerVisible, setLayerVisible] = useState(false);
  const [layerPlan, setLayerPlan] = useState<ConstructionPlan | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const params = {
        ...values,
        current,
        size: pageSize,
      };
      const res: any = await constructionApi.pagePlans(params);
      if (res.code === 200) {
        setData(res.data.records);
        setTotal(res.data.total);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadCameras = async () => {
    try {
      const res: any = await cameraApi.list();
      if (res.code === 200) setCameras(res.data);
    } catch (e) {
      console.error('加载摄像头列表失败', e);
    }
  };

  useEffect(() => { loadData(); loadCameras(); }, [current, pageSize]);

  const handleSearch = () => { setCurrent(1); loadData(); };
  const handleReset = () => { searchForm.resetFields(); setCurrent(1); loadData(); };

  const handleAdd = () => {
    setEditingId(null);
    editForm.resetFields();
    editForm.setFieldsValue({
      constructionType: 1,
      planStatus: 0,
      alertEnabled: 1,
      alertLevel: 2,
      speedLimit: 60,
      bufferDistance: 50,
      standardConeCount: 0,
      coneSpacing: 5,
      workerCount: 0,
      notifyEnabled: 1,
      linkWorkOrder: 1,
      ledReminderEnabled: 1,
      ledDefaultMessage: '前方施工 减速慢行',
      sortOrder: 0,
    });
    setEditModal(true);
  };

  const handleEdit = (record: ConstructionPlan) => {
    setEditingId(record.id);
    editForm.setFieldsValue(record);
    setEditModal(true);
  };

  const handleDelete = async (id: number) => {
    const res: any = await constructionApi.deletePlan(id);
    if (res.code === 200) { message.success('删除成功'); loadData(); }
  };

  const handleStart = async (record: ConstructionPlan) => {
    const res: any = await constructionApi.startConstruction(record.id);
    if (res.code === 200) { message.success('施工已开始'); loadData(); }
  };

  const handleComplete = async (record: ConstructionPlan) => {
    const res: any = await constructionApi.completeConstruction(record.id);
    if (res.code === 200) { message.success('施工已完成'); loadData(); }
  };

  const handleToggleAlert = async (record: ConstructionPlan, enabled: boolean) => {
    const res: any = await constructionApi.togglePlanAlert(record.id, enabled);
    if (res.code === 200) {
      message.success(enabled ? '已启用告警' : '已禁用告警');
      loadData();
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await editForm.validateFields();
      const res: any = await constructionApi.savePlan(values);
      if (res.code === 200) {
        message.success(editingId ? '编辑成功' : '添加成功');
        setEditModal(false);
        loadData();
      }
    } catch (e) { console.error(e); }
  };

  const handleViewDetail = async (record: ConstructionPlan) => {
    setSelectedPlan(record);
    try {
      const [summaryRes, conesRes] = await Promise.all([
        constructionApi.getPlanSummary(record.id),
        constructionApi.listConeRecordsByPlan(record.id),
      ]);
      if (summaryRes.code === 200) setPlanSummary(summaryRes.data);
      if (conesRes.code === 200) setConeRecords(conesRes.data || []);
    } catch (e) {
      console.error('加载详情失败', e);
    }
    setDetailModal(true);
  };

  const handleViewLayer = (record: ConstructionPlan) => {
    setLayerPlan(record);
    setLayerVisible(true);
  };

  const getStatusColor = (status: number) => {
    const colors: Record<number, string> = {
      0: 'default',
      1: 'processing',
      2: 'success',
      3: 'default',
      4: 'error',
    };
    return colors[status] || 'default';
  };

  const getTypeColor = (type: number) => {
    const colors: Record<number, string> = {
      1: 'blue',
      2: 'orange',
      3: 'red',
      4: 'purple',
    };
    return colors[type] || 'default';
  };

  const columns: ColumnsType<ConstructionPlan> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    {
      title: '计划名称', dataIndex: 'planName', width: 220,
      render: (text, record) => (
        <Space>
          <SafetyOutlined style={{ color: '#faad14' }} />
          <span style={{ fontWeight: 500 }}>{text}</span>
        </Space>
      ),
    },
    { title: '计划编号', dataIndex: 'planCode', width: 180 },
    {
      title: '类型', dataIndex: 'constructionType', width: 100,
      render: (val, record) => (
        <Tag color={getTypeColor(val)}>{record.constructionTypeLabel || '-'}</Tag>
      ),
    },
    { title: '关联摄像头', dataIndex: 'cameraName', width: 180, render: (text) => text || '-' },
    { title: '路段', dataIndex: 'roadName', width: 140, render: (text) => text || '-' },
    {
      title: '计划时间', width: 320,
      render: (_, record) => (
        <div style={{ fontSize: 12 }}>
          <div>计划: {record.planStartTime} ~ {record.planEndTime}</div>
          {record.actualStartTime && (
            <div style={{ color: '#52c41a' }}>
              实际: {record.actualStartTime}
              {record.actualEndTime ? ` ~ ${record.actualEndTime}` : ' (进行中)'}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '限速', dataIndex: 'speedLimit', width: 90, align: 'right',
      render: (val) => val ? `${val} km/h` : '-',
    },
    {
      title: '锥桶标准数', dataIndex: 'standardConeCount', width: 100, align: 'center',
      render: (val) => val || 0,
    },
    {
      title: '状态', dataIndex: 'planStatus', width: 90, align: 'center',
      render: (val, record) => (
        <Tag color={getStatusColor(val)}>{record.planStatusLabel || '-'}</Tag>
      ),
    },
    {
      title: '告警', dataIndex: 'alertEnabled', width: 80, align: 'center',
      render: (val, record) => (
        <Switch
          checked={val === 1}
          onChange={(checked) => handleToggleAlert(record, checked)}
          size="small"
        />
      ),
    },
    {
      title: '事件统计', width: 150, align: 'center',
      render: (_, record) => (
        <Space size={[8, 4]} wrap>
          <Badge count={record.eventCount || 0} size="small" style={{ backgroundColor: '#1890ff' }}>
            <span style={{ fontSize: 12 }}>总事件</span>
          </Badge>
          <Badge count={record.coneAlertCount || 0} size="small" style={{ backgroundColor: '#faad14' }}>
            <span style={{ fontSize: 12 }}>锥桶</span>
          </Badge>
          <Badge count={record.intrusionAlertCount || 0} size="small" style={{ backgroundColor: '#ff4d4f' }}>
            <span style={{ fontSize: 12 }}>闯入</span>
          </Badge>
        </Space>
      ),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作', key: 'action', width: 260, fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="查看标注">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleViewLayer(record)}
            />
          </Tooltip>
          <Tooltip title="详情">
            <Button
              type="link"
              size="small"
              icon={<BarChartOutlined />}
              onClick={() => handleViewDetail(record)}
            />
          </Tooltip>
          {record.planStatus === 1 && (
            <Tooltip title="开始施工">
              <Button
                type="link"
                size="small"
                icon={<PlayCircleOutlined />}
                style={{ color: '#52c41a' }}
                onClick={() => handleStart(record)}
              />
            </Tooltip>
          )}
          {record.planStatus === 2 && (
            <Tooltip title="完成施工">
              <Button
                type="link"
                size="small"
                icon={<StopOutlined />}
                style={{ color: '#faad14' }}
                onClick={() => handleComplete(record)}
              />
            </Tooltip>
          )}
          <Tooltip title="编辑">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
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
          <Form.Item name="constructionType" label="类型">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              <Option value={1}>日常养护</Option>
              <Option value={2}>道路维修</Option>
              <Option value={3}>应急抢修</Option>
              <Option value={4}>改扩建</Option>
            </Select>
          </Form.Item>
          <Form.Item name="planStatus" label="状态">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              <Option value={0}>草稿</Option>
              <Option value={1}>待执行</Option>
              <Option value={2}>进行中</Option>
              <Option value={3}>已完成</Option>
              <Option value={4}>已取消</Option>
            </Select>
          </Form.Item>
          <Form.Item name="cameraId" label="摄像头">
            <Select placeholder="全部" style={{ width: 160 }} allowClear>
              {cameras.map((cam) => (
                <Option key={cam.id} value={cam.id}>{cam.cameraName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        style={{ borderRadius: 8 }}
        title="施工计划列表"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增施工计划
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
            onChange: (c, s) => { setCurrent(c); setPageSize(s); },
          }}
          scroll={{ x: 1800 }}
        />
      </Card>

      <Modal
        title={editingId ? '编辑施工计划' : '新增施工计划'}
        open={editModal}
        onOk={handleSubmit}
        onCancel={() => setEditModal(false)}
        okText="保存"
        width={900}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="planCode" label="计划编号">
                <Input placeholder="自动生成或手动输入" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="planName"
                label="计划名称"
                rules={[{ required: true, message: '请输入计划名称' }]}
              >
                <Input placeholder="请输入施工计划名称" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="constructionType"
                label="施工类型"
                rules={[{ required: true }]}
              >
                <Select>
                  <Option value={1}>日常养护</Option>
                  <Option value={2}>道路维修</Option>
                  <Option value={3}>应急抢修</Option>
                  <Option value={4}>改扩建</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="planStatus" label="计划状态">
                <Select>
                  <Option value={0}>草稿</Option>
                  <Option value={1}>待执行</Option>
                  <Option value={2}>进行中</Option>
                  <Option value={3}>已完成</Option>
                  <Option value={4}>已取消</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="cameraId" label="关联摄像头">
                <Select allowClear placeholder="选择关联摄像头">
                  {cameras.map((cam) => (
                    <Option key={cam.id} value={cam.id}>{cam.cameraName}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="roadName" label="路段名称">
                <Input placeholder="如：京港澳高速" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="location" label="施工位置">
                <Input placeholder="施工位置描述" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="startStake" label="起始桩号">
                <Input placeholder="如：K100+300" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endStake" label="结束桩号">
                <Input placeholder="如：K100+700" />
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
          <Divider style={{ margin: '12px 0' }} />
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="speedLimit" label="施工区限速(km/h)">
                <InputNumber min={10} max={120} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="bufferDistance" label="缓冲区距离(米)">
                <InputNumber min={0} max={500} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="standardConeCount" label="标准锥桶数量">
                <InputNumber min={0} max={500} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="fenceId" label="施工区围栏ID">
                <InputNumber style={{ width: '100%' }} placeholder="关联geo_fence的ID" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="bufferFenceId" label="缓冲区围栏ID">
                <InputNumber style={{ width: '100%' }} placeholder="关联geo_fence的ID" />
              </Form.Item>
            </Col>
          </Row>
          <Divider style={{ margin: '12px 0' }} />
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="constructionUnit" label="施工单位">
                <Input placeholder="施工单位名称" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="responsiblePerson" label="负责人">
                <Input placeholder="负责人姓名" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="contactPhone" label="联系电话">
                <Input placeholder="联系电话" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="workerCount" label="施工人员数量">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="alertLevel" label="告警级别">
                <Select>
                  <Option value={1}>一般</Option>
                  <Option value={2}>严重</Option>
                  <Option value={3}>紧急</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="alertEnabled" label="启用告警" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="notifyEnabled" label="启用通知" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="linkWorkOrder" label="联动工单" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="ledReminderEnabled" label="LED提醒" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="ledDefaultMessage" label="LED默认显示内容">
            <Input placeholder="LED情报板显示的提示信息" maxLength={100} />
          </Form.Item>
          <Form.Item name="trafficControlMeasures" label="交通管控措施">
            <Input.TextArea rows={2} placeholder="描述采取的交通管控措施" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="施工计划描述信息" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="施工计划详情"
        open={detailModal}
        onCancel={() => setDetailModal(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModal(false)}>关闭</Button>,
        ]}
        width={1000}
        destroyOnClose
      >
        {selectedPlan && (
          <div>
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card size="small">
                  <Statistic title="总事件数" value={selectedPlan.eventCount || 0} prefix={<AlertOutlined />} />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic title="锥桶告警" value={selectedPlan.coneAlertCount || 0} valueStyle={{ color: '#faad14' }} />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic title="闯入告警" value={selectedPlan.intrusionAlertCount || 0} valueStyle={{ color: '#ff4d4f' }} />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic title="超速告警" value={selectedPlan.speedingAlertCount || 0} valueStyle={{ color: '#722ed1' }} />
                </Card>
              </Col>
            </Row>

            <Divider orientation="left">基本信息</Divider>
            <Row gutter={16}>
              <Col span={8}>
                <p><strong>计划名称:</strong> {selectedPlan.planName}</p>
                <p><strong>计划编号:</strong> {selectedPlan.planCode}</p>
                <p><strong>施工类型:</strong> {selectedPlan.constructionTypeLabel}</p>
              </Col>
              <Col span={8}>
                <p><strong>关联摄像头:</strong> {selectedPlan.cameraName || '-'}</p>
                <p><strong>路段:</strong> {selectedPlan.roadName || '-'}</p>
                <p><strong>位置:</strong> {selectedPlan.location || '-'}</p>
              </Col>
              <Col span={8}>
                <p><strong>限速:</strong> {selectedPlan.speedLimit} km/h</p>
                <p><strong>标准锥桶数:</strong> {selectedPlan.standardConeCount} 个</p>
                <p><strong>状态:</strong> {selectedPlan.planStatusLabel}</p>
              </Col>
            </Row>

            <Divider orientation="left">锥桶检测记录</Divider>
            {coneRecords.length > 0 ? (
              <List
                size="small"
                dataSource={coneRecords.slice(0, 5)}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta
                      title={
                        <Space>
                          <span>{item.detectionTime}</span>
                          <Tag color={item.isCompliant === 1 ? 'green' : 'red'}>
                            {item.isCompliant === 1 ? '合规' : '不合规'}
                          </Tag>
                          {item.alertTriggered === 1 && (
                            <Tag color="orange">已告警</Tag>
                          )}
                        </Space>
                      }
                      description={
                        <div>
                          <span>检测数量: {item.detectedConeCount} / {item.standardConeCount}</span>
                          <span style={{ marginLeft: 16 }}>合规率: {item.complianceRate}%</span>
                          {item.missingConeCount > 0 && (
                            <span style={{ marginLeft: 16, color: '#ff4d4f' }}>
                              缺失: {item.missingConeCount} 个
                            </span>
                          )}
                          <Progress
                            percent={item.complianceRate}
                            size="small"
                            style={{ width: 200, marginTop: 8 }}
                            status={item.isCompliant === 1 ? 'success' : 'exception'}
                          />
                        </div>
                      }
                    />
                  </List.Item>
                )}
              />
            ) : (
              <p style={{ color: '#999', textAlign: 'center', padding: '20px 0' }}>暂无锥桶检测记录</p>
            )}
          </div>
        )}
      </Modal>

      <Modal
        title="施工标注图层"
        open={layerVisible}
        onCancel={() => setLayerVisible(false)}
        footer={[
          <Button key="close" onClick={() => setLayerVisible(false)}>关闭</Button>,
        ]}
        width={1100}
        destroyOnClose
      >
        {layerPlan && (
          <ConstructionMapLayer
            plan={layerPlan}
            camera={cameras.find(c => c.id === layerPlan.cameraId)}
            height={480}
          />
        )}
      </Modal>
    </div>
  );
};

export default ConstructionZones;
