import React, { useEffect, useRef, useState } from 'react';
import {
  Table, Button, Space, Tag, Input, Select, Card, Modal, Form,
  InputNumber, Switch, message, Row, Col, Popconfirm, Tooltip, Divider,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined,
  DeleteOutlined, EnvironmentOutlined, SketchOutlined, UndoOutlined,
  RedoOutlined, ClearOutlined,
} from '@ant-design/icons';
import { geoFenceApi, cameraApi } from '@/services/api';
import {
  FENCE_TYPE_LABELS, FENCE_TYPE_COLORS, DETECT_TARGET_OPTIONS,
  EVENT_LEVEL_LABELS, type GeoFence, type Camera,
} from '@/types';

const { Option } = Select;

interface PixelPoint {
  nx: number;
  ny: number;
}

const GeoFences: React.FC = () => {
  const [data, setData] = useState<GeoFence[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [editModal, setEditModal] = useState(false);
  const [editForm] = Form.useForm();
  const [editingId, setEditingId] = useState<number | null>(null);
  const [cameras, setCameras] = useState<Camera[]>([]);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [canvasSize, setCanvasSize] = useState({ width: 640, height: 360 });
  const [drawingPoints, setDrawingPoints] = useState<PixelPoint[]>([]);
  const [isDrawing, setIsDrawing] = useState(false);
  const [history, setHistory] = useState<PixelPoint[][]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  const CANVAS_W = 640;
  const CANVAS_H = 360;

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const res: any = await geoFenceApi.page({ ...values, current, size: pageSize });
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

  const drawCanvas = (points: PixelPoint[]) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);

    ctx.fillStyle = '#1a1a2e';
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    ctx.strokeStyle = '#333';
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= 10; i++) {
      const x = (CANVAS_W / 10) * i;
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, CANVAS_H); ctx.stroke();
      const y = (CANVAS_H / 10) * i;
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(CANVAS_W, y); ctx.stroke();
    }

    ctx.font = '11px monospace';
    ctx.fillStyle = '#555';
    ctx.fillText('0,0', 4, 12);
    ctx.fillText('1,0', CANVAS_W - 24, 12);
    ctx.fillText('0,1', 4, CANVAS_H - 4);
    ctx.fillText('1,1', CANVAS_W - 24, CANVAS_H - 4);

    if (points.length === 0) {
      ctx.font = '14px sans-serif';
      ctx.fillStyle = '#666';
      ctx.textAlign = 'center';
      ctx.fillText('点击「开始绘制」后在画面上点击添加围栏顶点', CANVAS_W / 2, CANVAS_H / 2);
      ctx.textAlign = 'start';
      return;
    }

    const color = editForm.getFieldValue('color') || '#ff4d4f';

    if (points.length >= 3) {
      ctx.beginPath();
      ctx.moveTo(points[0].nx * CANVAS_W, points[0].ny * CANVAS_H);
      for (let i = 1; i < points.length; i++) {
        ctx.lineTo(points[i].nx * CANVAS_W, points[i].ny * CANVAS_H);
      }
      ctx.closePath();

      ctx.globalAlpha = 0.25;
      ctx.fillStyle = color;
      ctx.fill();
      ctx.globalAlpha = 1;

      ctx.setLineDash([8, 4]);
      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.setLineDash([]);
    }

    for (let i = 0; i < points.length; i++) {
      const px = points[i].nx * CANVAS_W;
      const py = points[i].ny * CANVAS_H;

      ctx.beginPath();
      ctx.arc(px, py, 5, 0, Math.PI * 2);
      ctx.fillStyle = '#1890ff';
      ctx.fill();
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 1.5;
      ctx.stroke();

      if (points.length >= 3 || i < points.length - 1) {
        const nextIdx = (i + 1) % points.length;
        const nx = points[nextIdx].nx * CANVAS_W;
        const ny = points[nextIdx].ny * CANVAS_H;
        ctx.beginPath();
        ctx.moveTo(px, py);
        ctx.lineTo(nx, ny);
        ctx.strokeStyle = color;
        ctx.lineWidth = 1.5;
        ctx.setLineDash([4, 3]);
        ctx.stroke();
        ctx.setLineDash([]);
      }

      ctx.font = '10px monospace';
      ctx.fillStyle = '#aaa';
      ctx.fillText(`(${points[i].nx.toFixed(2)},${points[i].ny.toFixed(2)})`, px + 8, py - 6);
    }
  };

  useEffect(() => {
    if (editModal) {
      drawCanvas(drawingPoints);
    }
  }, [drawingPoints, editModal]);

  const addPoint = (nx: number, ny: number) => {
    const newPoints = [...drawingPoints, { nx, ny }];
    const newHistory = history.slice(0, historyIndex + 1);
    newHistory.push(newPoints);
    setHistory(newHistory);
    setHistoryIndex(newHistory.length - 1);
    setDrawingPoints(newPoints);
  };

  const undo = () => {
    if (historyIndex > 0) {
      const newIndex = historyIndex - 1;
      setHistoryIndex(newIndex);
      setDrawingPoints(history[newIndex]);
    }
  };

  const redo = () => {
    if (historyIndex < history.length - 1) {
      const newIndex = historyIndex + 1;
      setHistoryIndex(newIndex);
      setDrawingPoints(history[newIndex]);
    }
  };

  const clearDrawing = () => {
    setDrawingPoints([]);
    setHistory([]);
    setHistoryIndex(-1);
  };

  const handleCanvasClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!isDrawing) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const nx = Math.max(0, Math.min(1, x / rect.width));
    const ny = Math.max(0, Math.min(1, y / rect.height));
    addPoint(nx, ny);
  };

  const startDrawing = () => { setIsDrawing(true); clearDrawing(); };
  const finishDrawing = () => {
    setIsDrawing(false);
    if (drawingPoints.length >= 3) {
      const pixelJson = JSON.stringify(drawingPoints.map((p) => [p.nx, p.ny]));
      editForm.setFieldsValue({ polygonPointsPixel: pixelJson });
      message.success(`已绘制 ${drawingPoints.length} 个顶点`);
    } else {
      message.warning('请至少绘制 3 个顶点');
    }
  };

  const handleAdd = () => {
    setEditingId(null);
    editForm.resetFields();
    editForm.setFieldsValue({
      fenceType: 1, alertEnabled: 1, alertLevel: 2,
      staySeconds: 0, cooldownSeconds: 60, notifyEnabled: 1,
      linkWorkOrder: 0, color: '#faad14', status: 1, sortOrder: 0,
      detectTargetTypes: ['person', 'car', 'truck'],
    });
    setDrawingPoints([]); setHistory([]); setHistoryIndex(-1); setIsDrawing(false);
    setEditModal(true);
  };

  const handleEdit = (record: GeoFence) => {
    setEditingId(record.id);
    editForm.setFieldsValue({
      ...record,
      detectTargetTypes: record.detectTargetTypes ? record.detectTargetTypes.split(',').filter(Boolean) : [],
    });

    let pts: PixelPoint[] = [];
    if (record.polygonPointsPixel) {
      try {
        const coords: number[][] = JSON.parse(record.polygonPointsPixel);
        pts = coords.map((p) => ({ nx: p[0], ny: p[1] }));
      } catch (e) { console.error('解析像素坐标失败', e); }
    }
    setDrawingPoints(pts);
    setHistory(pts.length > 0 ? [pts] : []);
    setHistoryIndex(pts.length > 0 ? 0 : -1);
    setIsDrawing(false);
    setEditModal(true);
  };

  const handleDelete = async (id: number) => {
    const res: any = await geoFenceApi.delete(id);
    if (res.code === 200) { message.success('删除成功'); loadData(); }
  };

  const handleToggleAlert = async (record: GeoFence, enabled: boolean) => {
    const res: any = await geoFenceApi.toggleAlert(record.id, enabled);
    if (res.code === 200) { message.success(enabled ? '已启用告警' : '已禁用告警'); loadData(); }
  };

  const handleSubmit = async () => {
    try {
      const values = await editForm.validateFields();
      if (drawingPoints.length < 3) {
        message.warning('请在画面上绘制至少 3 个顶点的围栏');
        return;
      }
      const submitData = {
        ...values,
        detectTargetTypes: Array.isArray(values.detectTargetTypes)
          ? values.detectTargetTypes.join(',') : values.detectTargetTypes,
        polygonPointsPixel: JSON.stringify(drawingPoints.map((p) => [p.nx, p.ny])),
      };
      const res: any = await geoFenceApi.save(submitData);
      if (res.code === 200) {
        message.success(editingId ? '编辑成功' : '添加成功');
        setEditModal(false); loadData();
      }
    } catch (e) { console.error(e); }
  };

  const handleFenceTypeChange = (type: number) => {
    editForm.setFieldsValue({ color: FENCE_TYPE_COLORS[type] || '#ff4d4f' });
  };

  const columns: ColumnsType<GeoFence> = [
    { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
    {
      title: '围栏名称', dataIndex: 'fenceName', width: 180,
      render: (text, record) => <Space><EnvironmentOutlined style={{ color: record.color }} />{text}</Space>,
    },
    { title: '围栏编号', dataIndex: 'fenceCode', width: 160 },
    {
      title: '类型', dataIndex: 'fenceType', width: 100,
      render: (val) => <Tag color={FENCE_TYPE_COLORS[val]}>{FENCE_TYPE_LABELS[val]}</Tag>,
    },
    { title: '关联摄像头', dataIndex: 'cameraName', width: 180, render: (text) => text || '-' },
    {
      title: '告警级别', dataIndex: 'alertLevel', width: 100, align: 'center',
      render: (val) => EVENT_LEVEL_LABELS[val] || '-',
    },
    {
      title: '检测目标', dataIndex: 'detectTargetTypes', width: 150,
      render: (val) => {
        if (!val) return '-';
        const types = val.split(',').filter(Boolean);
        return (
          <Space size={[4, 4]} wrap>
            {types.map((t: string) => {
              const opt = DETECT_TARGET_OPTIONS.find((o) => o.value === t);
              return <Tag key={t} color="blue">{opt?.label || t}</Tag>;
            })}
          </Space>
        );
      },
    },
    {
      title: '面积(㎡)', dataIndex: 'area', width: 100, align: 'right',
      render: (val) => (val ? Number(val).toFixed(0) : '-'),
    },
    {
      title: '告警状态', dataIndex: 'alertEnabled', width: 100, align: 'center',
      render: (val, record) => (
        <Switch checked={val === 1} onChange={(checked) => handleToggleAlert(record, checked)} size="small" />
      ),
    },
    {
      title: '状态', dataIndex: 'status', width: 80, align: 'center',
      render: (val) => <Tag color={val === 1 ? 'green' : 'default'}>{val === 1 ? '启用' : '禁用'}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作', key: 'action', width: 180, fixed: 'right',
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
            <Input placeholder="名称/编号" style={{ width: 180 }} allowClear />
          </Form.Item>
          <Form.Item name="fenceType" label="类型">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              <Option value={1}>施工区</Option><Option value={2}>应急车道</Option>
              <Option value={3}>禁入区</Option><Option value={4}>自定义</Option>
            </Select>
          </Form.Item>
          <Form.Item name="cameraId" label="摄像头">
            <Select placeholder="全部" style={{ width: 160 }} allowClear>
              {cameras.map((cam) => (<Option key={cam.id} value={cam.id}>{cam.cameraName}</Option>))}
            </Select>
          </Form.Item>
          <Form.Item name="alertEnabled" label="告警">
            <Select placeholder="全部" style={{ width: 100 }} allowClear>
              <Option value={1}>已启用</Option><Option value={0}>已禁用</Option>
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

      <Card style={{ borderRadius: 8 }} title="电子围栏列表"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新增围栏</Button>}
      >
        <Table columns={columns} dataSource={data} rowKey="id" loading={loading}
          pagination={{
            current, pageSize, total, showSizeChanger: true, showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`, onChange: (c, s) => { setCurrent(c); setPageSize(s); },
          }}
          scroll={{ x: 1400 }}
        />
      </Card>

      <Modal title={editingId ? '编辑电子围栏' : '新增电子围栏'} open={editModal}
        onOk={handleSubmit} onCancel={() => setEditModal(false)} okText="保存" width={1100} destroyOnClose
      >
        <Row gutter={16}>
          <Col span={10}>
            <Form form={editForm} layout="vertical">
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="fenceCode" label="围栏编号" rules={[{ required: true, message: '请输入围栏编号' }]}>
                    <Input placeholder="自动生成或手动输入" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="fenceName" label="围栏名称" rules={[{ required: true, message: '请输入围栏名称' }]}>
                    <Input placeholder="请输入围栏名称" />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="fenceType" label="围栏类型" rules={[{ required: true }]}>
                    <Select onChange={handleFenceTypeChange}>
                      <Option value={1}>施工区</Option><Option value={2}>应急车道</Option>
                      <Option value={3}>禁入区</Option><Option value={4}>自定义</Option>
                    </Select>
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="cameraId" label="关联摄像头">
                    <Select allowClear placeholder="选择关联摄像头">
                      {cameras.map((cam) => (<Option key={cam.id} value={cam.id}>{cam.cameraName}</Option>))}
                    </Select>
                  </Form.Item>
                </Col>
              </Row>

              <Divider style={{ margin: '12px 0' }} />

              <Form.Item name="detectTargetTypes" label="检测目标类型" rules={[{ required: true, message: '请选择检测目标' }]}>
                <Select mode="multiple" placeholder="选择检测目标类型">
                  {DETECT_TARGET_OPTIONS.map((opt) => (<Option key={opt.value} value={opt.value}>{opt.label}</Option>))}
                </Select>
              </Form.Item>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="alertLevel" label="告警级别">
                    <Select><Option value={1}>一般</Option><Option value={2}>严重</Option>
                      <Option value={3}>紧急</Option><Option value={4}>特急</Option></Select>
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="color" label="围栏颜色">
                    <Input type="color" style={{ height: 32, padding: 2 }} />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="staySeconds" label="停留触发(秒)">
                    <InputNumber min={0} max={3600} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="cooldownSeconds" label="冷却时间(秒)">
                    <InputNumber min={0} max={3600} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={12}>
                <Col span={8}>
                  <Form.Item name="alertEnabled" label="启用告警" valuePropName="checked"><Switch /></Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="notifyEnabled" label="启用通知" valuePropName="checked"><Switch /></Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="linkWorkOrder" label="联动工单" valuePropName="checked"><Switch /></Form.Item>
                </Col>
              </Row>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="sortOrder" label="排序"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="status" label="状态" valuePropName="checked">
                    <Switch checkedChildren="启用" unCheckedChildren="禁用" />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item name="description" label="描述">
                <Input.TextArea rows={3} placeholder="请输入描述信息" />
              </Form.Item>
            </Form>
          </Col>

          <Col span={14}>
            <div style={{ marginBottom: 8 }}>
              <Space>
                <Button type={isDrawing ? 'primary' : 'default'} icon={<SketchOutlined />}
                  onClick={isDrawing ? finishDrawing : startDrawing}>
                  {isDrawing ? '完成绘制' : '开始绘制'}
                </Button>
                {isDrawing && (
                  <Button icon={<UndoOutlined />} onClick={undo} disabled={historyIndex <= 0}>撤销</Button>
                )}
                {isDrawing && (
                  <Button icon={<RedoOutlined />} onClick={redo} disabled={historyIndex >= history.length - 1}>重做</Button>
                )}
                <Button icon={<ClearOutlined />} onClick={clearDrawing} danger>清除</Button>
              </Space>
              <span style={{ marginLeft: 12, color: '#666', fontSize: 12 }}>
                {isDrawing
                  ? `在画面上点击添加顶点，已添加 ${drawingPoints.length} 个点`
                  : '点击「开始绘制」后在画面上点击添加围栏顶点（归一化坐标 0~1）'}
              </span>
            </div>
            <canvas
              ref={canvasRef}
              width={CANVAS_W}
              height={CANVAS_H}
              onClick={handleCanvasClick}
              style={{
                width: '100%',
                height: 'auto',
                aspectRatio: `${CANVAS_W}/${CANVAS_H}`,
                borderRadius: 8,
                border: '1px solid #d9d9d9',
                cursor: isDrawing ? 'crosshair' : 'default',
                display: 'block',
              }}
            />
            {drawingPoints.length >= 3 && (
              <div style={{ marginTop: 8, fontSize: 12, color: '#666' }}>
                已绘制 {drawingPoints.length} 个顶点 | 围栏覆盖画面区域
              </div>
            )}
          </Col>
        </Row>
      </Modal>
    </div>
  );
};

export default GeoFences;
