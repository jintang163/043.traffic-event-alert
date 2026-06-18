import React, { useEffect, useRef, useState } from 'react';
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
  Tooltip,
  Divider,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  EnvironmentOutlined,
  SketchOutlined,
  UndoOutlined,
  RedoOutlined,
  ClearOutlined,
} from '@ant-design/icons';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { geoFenceApi, cameraApi } from '@/services/api';
import {
  FENCE_TYPE_LABELS,
  FENCE_TYPE_COLORS,
  DETECT_TARGET_OPTIONS,
  EVENT_LEVEL_LABELS,
  type GeoFence,
  type PageResult,
  type Camera,
} from '@/types';

const { Option } = Select;

interface PolygonPoint {
  lat: number;
  lng: number;
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

  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const drawLayerRef = useRef<L.Polygon | null>(null);
  const tempMarkersRef = useRef<L.CircleMarker[]>([]);
  const [drawingPoints, setDrawingPoints] = useState<PolygonPoint[]>([]);
  const [isDrawing, setIsDrawing] = useState(false);
  const [history, setHistory] = useState<PolygonPoint[][]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const res: any = await geoFenceApi.page({
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

  const loadCameras = async () => {
    try {
      const res: any = await cameraApi.list();
      if (res.code === 200) {
        setCameras(res.data);
      }
    } catch (e) {
      console.error('加载摄像头列表失败', e);
    }
  };

  useEffect(() => {
    loadData();
    loadCameras();
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

  const initMap = () => {
    if (!mapRef.current || mapInstanceRef.current) return;

    const map = L.map(mapRef.current, {
      center: [39.9042, 116.3974],
      zoom: 15,
      zoomControl: true,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map);

    mapInstanceRef.current = map;

    map.on('click', (e: L.LeafletMouseEvent) => {
      if (isDrawing) {
        addPoint(e.latlng.lat, e.latlng.lng);
      }
    });
  };

  const destroyMap = () => {
    if (mapInstanceRef.current) {
      mapInstanceRef.current.remove();
      mapInstanceRef.current = null;
    }
    clearDrawing();
  };

  const addPoint = (lat: number, lng: number) => {
    const newPoints = [...drawingPoints, { lat, lng }];

    const newHistory = history.slice(0, historyIndex + 1);
    newHistory.push(newPoints);
    setHistory(newHistory);
    setHistoryIndex(newHistory.length - 1);

    setDrawingPoints(newPoints);
    updateDrawPolygon(newPoints);
  };

  const undo = () => {
    if (historyIndex > 0) {
      const newIndex = historyIndex - 1;
      setHistoryIndex(newIndex);
      const points = history[newIndex];
      setDrawingPoints(points);
      updateDrawPolygon(points);
    }
  };

  const redo = () => {
    if (historyIndex < history.length - 1) {
      const newIndex = historyIndex + 1;
      setHistoryIndex(newIndex);
      const points = history[newIndex];
      setDrawingPoints(points);
      updateDrawPolygon(points);
    }
  };

  const clearDrawing = () => {
    if (drawLayerRef.current && mapInstanceRef.current) {
      mapInstanceRef.current.removeLayer(drawLayerRef.current);
      drawLayerRef.current = null;
    }

    tempMarkersRef.current.forEach((marker) => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.removeLayer(marker);
      }
    });
    tempMarkersRef.current = [];

    setDrawingPoints([]);
    setHistory([]);
    setHistoryIndex(-1);
  };

  const updateDrawPolygon = (points: PolygonPoint[]) => {
    if (!mapInstanceRef.current) return;

    if (drawLayerRef.current) {
      mapInstanceRef.current.removeLayer(drawLayerRef.current);
      drawLayerRef.current = null;
    }

    tempMarkersRef.current.forEach((marker) => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.removeLayer(marker);
      }
    });
    tempMarkersRef.current = [];

    if (points.length >= 3) {
      const color = editForm.getFieldValue('color') || '#ff4d4f';
      const polygon = L.polygon(
        points.map((p) => [p.lat, p.lng]),
        {
          color: color,
          weight: 2,
          fillColor: color,
          fillOpacity: 0.3,
          dashArray: '10, 10',
        }
      );
      polygon.addTo(mapInstanceRef.current);
      drawLayerRef.current = polygon;
    }

    points.forEach((point, index) => {
      if (mapInstanceRef.current) {
        const marker = L.circleMarker([point.lat, point.lng], {
          radius: 6,
          color: '#fff',
          weight: 2,
          fillColor: '#1890ff',
          fillOpacity: 1,
        }).addTo(mapInstanceRef.current);
        tempMarkersRef.current.push(marker);
      }
    });
  };

  const startDrawing = () => {
    setIsDrawing(true);
    clearDrawing();
    if (mapRef.current) {
      mapRef.current.style.cursor = 'crosshair';
    }
  };

  const finishDrawing = () => {
    setIsDrawing(false);
    if (mapRef.current) {
      mapRef.current.style.cursor = '';
    }
    if (drawingPoints.length >= 3) {
      const pointsJson = JSON.stringify(
        drawingPoints.map((p) => [p.lng, p.lat])
      );
      editForm.setFieldsValue({ polygonPoints: pointsJson });
      calculateAreaInfo();
      message.success(`已绘制 ${drawingPoints.length} 个顶点的多边形`);
    } else {
      message.warning('请至少绘制 3 个顶点');
    }
  };

  const cancelDrawing = () => {
    setIsDrawing(false);
    clearDrawing();
    if (mapRef.current) {
      mapRef.current.style.cursor = '';
    }
  };

  const calculateAreaInfo = async () => {
    if (drawingPoints.length < 3) return;
    try {
      const points = drawingPoints.map((p) => [p.lng, p.lat]);
      const res: any = await geoFenceApi.calculateArea(points);
      if (res.code === 200) {
        editForm.setFieldsValue({
          area: res.data.area,
          centerLongitude: res.data.centerLng,
          centerLatitude: res.data.centerLat,
        });
      }
    } catch (e) {
      console.error('计算面积失败', e);
    }
  };

  const handleAdd = () => {
    setEditingId(null);
    editForm.resetFields();
    editForm.setFieldsValue({
      fenceType: 1,
      alertEnabled: 1,
      alertLevel: 2,
      staySeconds: 0,
      cooldownSeconds: 60,
      notifyEnabled: 1,
      linkWorkOrder: 0,
      color: '#faad14',
      status: 1,
      sortOrder: 0,
      detectTargetTypes: ['person', 'car', 'truck'],
    });
    setEditModal(true);
    setTimeout(() => {
      initMap();
    }, 100);
  };

  const handleEdit = (record: GeoFence) => {
    setEditingId(record.id);
    editForm.setFieldsValue({
      ...record,
      detectTargetTypes: record.detectTargetTypes
        ? record.detectTargetTypes.split(',').filter(Boolean)
        : [],
    });
    setEditModal(true);

    setTimeout(() => {
      initMap();
      loadFenceToMap(record);
    }, 100);
  };

  const loadFenceToMap = (fence: GeoFence) => {
    if (!mapInstanceRef.current || !fence.polygonPoints) return;

    try {
      const points: number[][] = JSON.parse(fence.polygonPoints);
      const polygonPoints: PolygonPoint[] = points.map((p) => ({
        lat: p[1],
        lng: p[0],
      }));

      setDrawingPoints(polygonPoints);
      setHistory([polygonPoints]);
      setHistoryIndex(0);
      updateDrawPolygon(polygonPoints);

      if (polygonPoints.length > 0) {
        const center = polygonPoints[0];
        mapInstanceRef.current.setView([center.lat, center.lng], 16);
      }
    } catch (e) {
      console.error('解析围栏坐标失败', e);
    }
  };

  const handleDelete = async (id: number) => {
    const res: any = await geoFenceApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
    }
  };

  const handleToggleAlert = async (record: GeoFence, enabled: boolean) => {
    const res: any = await geoFenceApi.toggleAlert(record.id, enabled);
    if (res.code === 200) {
      message.success(enabled ? '已启用告警' : '已禁用告警');
      loadData();
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await editForm.validateFields();

      if (drawingPoints.length < 3) {
        message.warning('请在地图上绘制至少 3 个顶点的多边形围栏');
        return;
      }

      const submitData = {
        ...values,
        detectTargetTypes: Array.isArray(values.detectTargetTypes)
          ? values.detectTargetTypes.join(',')
          : values.detectTargetTypes,
        polygonPoints: JSON.stringify(
          drawingPoints.map((p) => [p.lng, p.lat])
        ),
      };

      const res: any = await geoFenceApi.save(submitData);
      if (res.code === 200) {
        message.success(editingId ? '编辑成功' : '添加成功');
        setEditModal(false);
        destroyMap();
        loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleModalClose = () => {
    setEditModal(false);
    destroyMap();
  };

  const handleFenceTypeChange = (type: number) => {
    const color = FENCE_TYPE_COLORS[type] || '#ff4d4f';
    editForm.setFieldsValue({ color });
    updateDrawPolygon(drawingPoints);
  };

  const columns: ColumnsType<GeoFence> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
      align: 'center',
    },
    {
      title: '围栏名称',
      dataIndex: 'fenceName',
      width: 180,
      render: (text, record) => (
        <Space>
          <EnvironmentOutlined style={{ color: record.color }} />
          {text}
        </Space>
      ),
    },
    {
      title: '围栏编号',
      dataIndex: 'fenceCode',
      width: 160,
    },
    {
      title: '类型',
      dataIndex: 'fenceType',
      width: 100,
      render: (val) => (
        <Tag color={FENCE_TYPE_COLORS[val]}>{FENCE_TYPE_LABELS[val]}</Tag>
      ),
    },
    {
      title: '关联摄像头',
      dataIndex: 'cameraName',
      width: 180,
      render: (text) => text || '-',
    },
    {
      title: '告警级别',
      dataIndex: 'alertLevel',
      width: 100,
      align: 'center',
      render: (val) => EVENT_LEVEL_LABELS[val] || '-',
    },
    {
      title: '检测目标',
      dataIndex: 'detectTargetTypes',
      width: 150,
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
      title: '面积(㎡)',
      dataIndex: 'area',
      width: 100,
      align: 'right',
      render: (val) => (val ? Number(val).toFixed(0) : '-'),
    },
    {
      title: '告警状态',
      dataIndex: 'alertEnabled',
      width: 100,
      align: 'center',
      render: (val, record) => (
        <Switch
          checked={val === 1}
          onChange={(checked) => handleToggleAlert(record, checked)}
          size="small"
        />
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      align: 'center',
      render: (val) => (
        <Tag color={val === 1 ? 'green' : 'default'}>
          {val === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
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
          <Form.Item name="fenceType" label="类型">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              <Option value={1}>施工区</Option>
              <Option value={2}>应急车道</Option>
              <Option value={3}>禁入区</Option>
              <Option value={4}>自定义</Option>
            </Select>
          </Form.Item>
          <Form.Item name="cameraId" label="摄像头">
            <Select placeholder="全部" style={{ width: 160 }} allowClear>
              {cameras.map((cam) => (
                <Option key={cam.id} value={cam.id}>
                  {cam.cameraName}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="alertEnabled" label="告警">
            <Select placeholder="全部" style={{ width: 100 }} allowClear>
              <Option value={1}>已启用</Option>
              <Option value={0}>已禁用</Option>
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
        title="电子围栏列表"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增围栏
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
        title={editingId ? '编辑电子围栏' : '新增电子围栏'}
        open={editModal}
        onOk={handleSubmit}
        onCancel={handleModalClose}
        okText="保存"
        width={1200}
        destroyOnClose
      >
        <Row gutter={16}>
          <Col span={10}>
            <Form form={editForm} layout="vertical">
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item
                    name="fenceCode"
                    label="围栏编号"
                    rules={[{ required: true, message: '请输入围栏编号' }]}
                  >
                    <Input placeholder="自动生成或手动输入" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="fenceName"
                    label="围栏名称"
                    rules={[{ required: true, message: '请输入围栏名称' }]}
                  >
                    <Input placeholder="请输入围栏名称" />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item
                    name="fenceType"
                    label="围栏类型"
                    rules={[{ required: true }]}
                  >
                    <Select onChange={handleFenceTypeChange}>
                      <Option value={1}>施工区</Option>
                      <Option value={2}>应急车道</Option>
                      <Option value={3}>禁入区</Option>
                      <Option value={4}>自定义</Option>
                    </Select>
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="cameraId" label="关联摄像头">
                    <Select allowClear placeholder="选择关联摄像头">
                      {cameras.map((cam) => (
                        <Option key={cam.id} value={cam.id}>
                          {cam.cameraName}
                        </Option>
                      ))}
                    </Select>
                  </Form.Item>
                </Col>
              </Row>

              <Divider style={{ margin: '12px 0' }} />

              <Form.Item
                name="detectTargetTypes"
                label="检测目标类型"
                rules={[{ required: true, message: '请选择检测目标' }]}
              >
                <Select mode="multiple" placeholder="选择检测目标类型">
                  {DETECT_TARGET_OPTIONS.map((opt) => (
                    <Option key={opt.value} value={opt.value}>
                      {opt.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>

              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="alertLevel" label="告警级别">
                    <Select>
                      <Option value={1}>一般</Option>
                      <Option value={2}>严重</Option>
                      <Option value={3}>紧急</Option>
                      <Option value={4}>特急</Option>
                    </Select>
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
                    <InputNumber
                      min={0}
                      max={3600}
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="cooldownSeconds" label="冷却时间(秒)">
                    <InputNumber
                      min={0}
                      max={3600}
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={12}>
                <Col span={8}>
                  <Form.Item
                    name="alertEnabled"
                    label="启用告警"
                    valuePropName="checked"
                  >
                    <Switch />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item
                    name="notifyEnabled"
                    label="启用通知"
                    valuePropName="checked"
                  >
                    <Switch />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item
                    name="linkWorkOrder"
                    label="联动工单"
                    valuePropName="checked"
                  >
                    <Switch />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="sortOrder" label="排序">
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="status"
                    label="状态"
                    valuePropName="checked"
                  >
                    <Switch checkedChildren="启用" unCheckedChildren="禁用" />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item name="area" label="面积(平方米)">
                <InputNumber
                  style={{ width: '100%' }}
                  disabled
                  precision={2}
                />
              </Form.Item>

              <Form.Item name="description" label="描述">
                <Input.TextArea rows={3} placeholder="请输入描述信息" />
              </Form.Item>
            </Form>
          </Col>

          <Col span={14}>
            <div style={{ marginBottom: 8 }}>
              <Space>
                <Button
                  type={isDrawing ? 'primary' : 'default'}
                  icon={<SketchOutlined />}
                  onClick={isDrawing ? finishDrawing : startDrawing}
                >
                  {isDrawing ? '完成绘制' : '开始绘制'}
                </Button>
                {isDrawing && (
                  <Button icon={<UndoOutlined />} onClick={undo} disabled={historyIndex <= 0}>
                    撤销
                  </Button>
                )}
                {isDrawing && (
                  <Button icon={<RedoOutlined />} onClick={redo} disabled={historyIndex >= history.length - 1}>
                    重做
                  </Button>
                )}
                <Button
                  icon={<ClearOutlined />}
                  onClick={clearDrawing}
                  danger
                >
                  清除
                </Button>
              </Space>
              <span style={{ marginLeft: 12, color: '#666', fontSize: 12 }}>
                {isDrawing
                  ? `点击地图添加顶点，已添加 ${drawingPoints.length} 个点`
                  : '点击「开始绘制」后在地图上点击添加顶点'}
              </span>
            </div>
            <div
              ref={mapRef}
              style={{
                width: '100%',
                height: 450,
                borderRadius: 8,
                border: '1px solid #d9d9d9',
              }}
            />
          </Col>
        </Row>
      </Modal>
    </div>
  );
};

export default GeoFences;
