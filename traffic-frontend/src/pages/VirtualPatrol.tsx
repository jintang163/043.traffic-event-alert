import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Input, Select, Card, Modal, Form,
  InputNumber, Switch, message, Row, Col, Popconfirm, Tooltip,
  Divider, Statistic, Progress, Slider, Badge, Empty, List, Alert,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined,
  DeleteOutlined, EnvironmentOutlined, PlayCircleOutlined,
  PauseCircleOutlined, StepForwardOutlined, StepBackwardOutlined,
  CarOutlined, VideoCameraOutlined, ClockCircleOutlined,
  CheckCircleOutlined, ExclamationCircleOutlined, ArrowUpOutlined,
  ArrowDownOutlined, ClearOutlined, SaveOutlined,
} from '@ant-design/icons';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { patrolRouteApi, cameraApi } from '@/services/api';
import { wsService, type DetectionItem } from '@/services/websocket';
import VideoPlayer from '@/components/VideoPlayer';
import {
  PATROL_STATUS_LABELS, PATROL_EXECUTION_STATUS_LABELS,
  PATROL_EXECUTION_STATUS_COLORS,
  type PatrolRoute, type PatrolRoutePoint, type PatrolExecutionLog, type Camera, type AlertEvent,
} from '@/types';

const { Option } = Select;

interface PatrolState {
  isRunning: boolean;
  isPaused: boolean;
  currentIndex: number;
  currentPoint: PatrolRoutePoint | null;
  executionLogId: number | null;
  detectedEvents: AlertEvent[];
  countdown: number;
  loopMode: number;
  staySeconds: number;
}

const VirtualPatrol: React.FC = () => {
  const [data, setData] = useState<PatrolRoute[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [editModal, setEditModal] = useState(false);
  const [editForm] = Form.useForm();
  const [editingId, setEditingId] = useState<number | null>(null);
  const [cameras, setCameras] = useState<Camera[]>([]);

  const [selectedRoute, setSelectedRoute] = useState<PatrolRoute | null>(null);
  const [routePoints, setRoutePoints] = useState<PatrolRoutePoint[]>([]);
  const [selectedCameras, setSelectedCameras] = useState<CameraForRoute[]>([]);

  const [showMapModal, setShowMapModal] = useState(false);
  const [showExecutionModal, setShowExecutionModal] = useState(false);
  const [showLogModal, setShowLogModal] = useState(false);
  const [executionLogs, setExecutionLogs] = useState<PatrolExecutionLog[]>([]);

  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const cameraMarkersRef = useRef<Record<number, L.Marker>>({});
  const routeMarkersRef = useRef<L.Marker[]>([]);
  const routePolylineRef = useRef<L.Polyline | null>(null);

  const patrolTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const countdownTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const [patrolState, setPatrolState] = useState<PatrolState>({
    isRunning: false,
    isPaused: false,
    currentIndex: -1,
    currentPoint: null,
    executionLogId: null,
    detectedEvents: [],
    countdown: 0,
    loopMode: 0,
    staySeconds: 30,
  });

  const [liveDetections, setLiveDetections] = useState<DetectionItem[]>([]);

  interface CameraForRoute extends Camera {
    sortOrder: number;
    staySeconds: number;
  }

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const res: any = await patrolRouteApi.page({ ...values, current, size: pageSize });
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
        const enabledCameras = res.data.filter((c: Camera) => c.onlineStatus === 1);
        setCameras(enabledCameras);
      }
    } catch (e) {
      console.error('加载摄像头列表失败', e);
    }
  };

  useEffect(() => {
    loadData();
    loadCameras();
  }, [current, pageSize]);

  const initLeafletMap = useCallback(() => {
    if (!mapRef.current || mapInstanceRef.current) return;

    const map = L.map(mapRef.current, {
      zoomControl: true,
      attributionControl: false,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
    }).addTo(map);

    map.setView([39.9042, 116.3974], 12);
    mapInstanceRef.current = map;

    const defaultIcon = L.icon({
      iconUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
    });

    const selectedIcon = L.icon({
      iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-green.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
    });

    cameras.forEach((cam) => {
      if (cam.longitude && cam.latitude) {
        const isSelected = selectedCameras.some((sc) => sc.id === cam.id);
        const marker = L.marker([cam.latitude, cam.longitude], {
          icon: isSelected ? selectedIcon : defaultIcon,
        }).addTo(map);

        const order = selectedCameras.findIndex((sc) => sc.id === cam.id);
        const label = order >= 0 ? `#${order + 1} ${cam.cameraName}` : cam.cameraName;

        marker.bindPopup(`
          <div style="font-weight: 600;">${label}</div>
          <div style="font-size: 12px; color: #666; margin-top: 4px;">${cam.location || '未知位置'}</div>
          <div style="margin-top: 8px;">
            <button class="ant-btn ant-btn-primary ant-btn-sm" 
              onclick="window.selectCameraForPatrol(${cam.id})"
              style="width: 100%;">
              ${isSelected ? '取消选择' : '添加到路线'}
            </button>
          </div>
        `);

        marker.on('click', () => {
          toggleCameraSelection(cam.id);
        });

        cameraMarkersRef.current[cam.id] = marker;
      }
    });

    (window as any).selectCameraForPatrol = (cameraId: number) => {
      toggleCameraSelection(cameraId);
    };

    if (selectedCameras.length > 1) {
      const latlngs = selectedCameras
        .filter((c) => c.longitude && c.latitude)
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((c) => [c.latitude!, c.longitude!]);

      if (latlngs.length > 1) {
        if (routePolylineRef.current) {
          map.removeLayer(routePolylineRef.current);
        }
        routePolylineRef.current = L.polyline(latlngs, {
          color: '#1890ff',
          weight: 3,
          opacity: 0.8,
          dashArray: '10, 10',
        }).addTo(map);

        map.fitBounds(L.latLngBounds(latlngs).pad(0.2));
      }
    }
  }, [cameras, selectedCameras]);

  const toggleCameraSelection = (cameraId: number) => {
    const cam = cameras.find((c) => c.id === cameraId);
    if (!cam) return;

    const existingIndex = selectedCameras.findIndex((sc) => sc.id === cameraId);

    if (existingIndex >= 0) {
      const newSelected = selectedCameras
        .filter((sc) => sc.id !== cameraId)
        .map((sc, idx) => ({ ...sc, sortOrder: idx }));
      setSelectedCameras(newSelected);
    } else {
      setSelectedCameras([
        ...selectedCameras,
        { ...cam, sortOrder: selectedCameras.length, staySeconds: 30 },
      ]);
    }
  };

  const clearMapMarkers = () => {
    if (mapInstanceRef.current) {
      Object.values(cameraMarkersRef.current).forEach((m) => m.remove());
      cameraMarkersRef.current = {};
      routeMarkersRef.current.forEach((m) => m.remove());
      routeMarkersRef.current = [];
      if (routePolylineRef.current) {
        mapInstanceRef.current.removeLayer(routePolylineRef.current);
        routePolylineRef.current = null;
      }
    }
  };

  useEffect(() => {
    if (showMapModal) {
      setTimeout(() => initLeafletMap(), 100);
    } else {
      clearMapMarkers();
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    }
    return () => {
      clearMapMarkers();
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, [showMapModal, initLeafletMap]);

  useEffect(() => {
    if (mapInstanceRef.current) {
      clearMapMarkers();
      initLeafletMap();
    }
  }, [selectedCameras]);

  const handleSearch = () => { setCurrent(1); loadData(); };
  const handleReset = () => { searchForm.resetFields(); setCurrent(1); loadData(); };

  const handleAdd = () => {
    setEditingId(null);
    setSelectedCameras([]);
    editForm.resetFields();
    editForm.setFieldsValue({
      staySeconds: 30,
      loopMode: 0,
      status: 1,
    });
    setEditModal(true);
  };

  const handleEdit = async (record: PatrolRoute) => {
    setEditingId(record.id);
    const res: any = await patrolRouteApi.get(record.id);
    if (res.code === 200) {
      const points = res.data.points || [];
      const cams = points
        .sort((a: PatrolRoutePoint, b: PatrolRoutePoint) => a.sortOrder - b.sortOrder)
        .map((p: PatrolRoutePoint) => {
          const cam = cameras.find((c) => c.id === p.cameraId);
          return cam ? {
            ...cam,
            sortOrder: p.sortOrder,
            staySeconds: p.staySeconds,
          } : null;
        })
        .filter(Boolean);

      setSelectedCameras(cams);
      editForm.setFieldsValue({
        routeName: record.routeName,
        routeCode: record.routeCode,
        description: record.description,
        staySeconds: record.staySeconds,
        loopMode: record.loopMode,
        status: record.status,
      });
      setEditModal(true);
    }
  };

  const handleDelete = async (id: number) => {
    const res: any = await patrolRouteApi.delete(id);
    if (res.code === 200) {
      message.success('删除成功');
      loadData();
    }
  };

  const handleSave = async () => {
    try {
      const values = await editForm.validateFields();

      if (selectedCameras.length < 2) {
        message.warning('请至少选择2个摄像头作为巡逻点位');
        return;
      }

      const points = selectedCameras.map((cam, idx) => ({
        cameraId: cam.id,
        cameraName: cam.cameraName,
        cameraCode: cam.cameraCode,
        sortOrder: idx,
        staySeconds: cam.staySeconds || values.staySeconds || 30,
        longitude: cam.longitude,
        latitude: cam.latitude,
        location: cam.location,
      }));

      const saveData = {
        ...values,
        id: editingId,
        points,
      };

      const res: any = await patrolRouteApi.save(saveData);
      if (res.code === 200) {
        message.success(editingId ? '更新成功' : '创建成功');
        setEditModal(false);
        loadData();
      }
    } catch (e: any) {
      console.error(e);
    }
  };

  const moveCamera = (index: number, direction: 'up' | 'down') => {
    const newSelected = [...selectedCameras];
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= newSelected.length) return;

    [newSelected[index], newSelected[targetIndex]] = [newSelected[targetIndex], newSelected[index]];
    newSelected.forEach((cam, idx) => (cam.sortOrder = idx));
    setSelectedCameras(newSelected);
  };

  const removeCamera = (index: number) => {
    const newSelected = selectedCameras
      .filter((_, i) => i !== index)
      .map((cam, idx) => ({ ...cam, sortOrder: idx }));
    setSelectedCameras(newSelected);
  };

  const updateCameraStaySeconds = (index: number, seconds: number) => {
    const newSelected = [...selectedCameras];
    newSelected[index] = { ...newSelected[index], staySeconds: seconds };
    setSelectedCameras(newSelected);
  };

  const startPatrol = async (record: PatrolRoute) => {
    const res: any = await patrolRouteApi.get(record.id);
    if (res.code !== 200) return;

    const routeDetail = res.data;
    if (!routeDetail.points || routeDetail.points.length < 2) {
      message.warning('该路线没有足够的点位');
      return;
    }

    const sortedPoints = [...routeDetail.points].sort((a, b) => a.sortOrder - b.sortOrder);
    setSelectedRoute(routeDetail);
    setRoutePoints(sortedPoints);

    const startRes: any = await patrolRouteApi.start(record.id);
    if (startRes.code !== 200) return;

    setPatrolState({
      isRunning: true,
      isPaused: false,
      currentIndex: 0,
      currentPoint: sortedPoints[0],
      executionLogId: startRes.data,
      detectedEvents: [],
      countdown: sortedPoints[0].staySeconds,
      loopMode: routeDetail.loopMode,
      staySeconds: routeDetail.staySeconds,
    });

    setShowExecutionModal(true);
    startPatrolTimer(sortedPoints, 0, startRes.data);
  };

  const startPatrolTimer = (points: PatrolRoutePoint[], startIndex: number, logId: number) => {
    if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
    if (patrolTimerRef.current) clearInterval(patrolTimerRef.current);

    let currentIdx = startIndex;

    const nextPoint = () => {
      currentIdx++;
      if (currentIdx >= points.length) {
        if (patrolState.loopMode === 1) {
          currentIdx = 0;
        } else {
          stopPatrol(logId, '巡逻完成');
          return;
        }
      }

      const point = points[currentIdx];
      setPatrolState((prev) => ({
        ...prev,
        currentIndex: currentIdx,
        currentPoint: point,
        countdown: point.staySeconds,
      }));

      patrolRouteApi.updateProgress(logId, currentIdx + 1);
      startCountdown(point.staySeconds);
    };

    const startCountdown = (seconds: number) => {
      if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);

      let remaining = seconds;
      countdownTimerRef.current = setInterval(() => {
        setPatrolState((prev) => {
          if (prev.isPaused) return prev;
          remaining--;
          if (remaining <= 0) {
            if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
            nextPoint();
            return { ...prev, countdown: 0 };
          }
          return { ...prev, countdown: remaining };
        });
      }, 1000);
    };

    startCountdown(points[startIndex].staySeconds);
  };

  const stopPatrol = (logId: number, remark: string) => {
    if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
    if (patrolTimerRef.current) clearInterval(patrolTimerRef.current);

    patrolRouteApi.complete(logId, JSON.stringify(patrolState.detectedEvents), remark);

    setPatrolState((prev) => ({
      ...prev,
      isRunning: false,
      isPaused: false,
      countdown: 0,
    }));

    message.success(remark);
  };

  const togglePause = () => {
    setPatrolState((prev) => ({
      ...prev,
      isPaused: !prev.isPaused,
    }));
  };

  const stepToPoint = (direction: 'prev' | 'next') => {
    if (!routePoints.length) return;

    let newIndex = patrolState.currentIndex + (direction === 'next' ? 1 : -1);
    if (newIndex < 0) newIndex = routePoints.length - 1;
    if (newIndex >= routePoints.length) newIndex = 0;

    setPatrolState((prev) => ({
      ...prev,
      currentIndex: newIndex,
      currentPoint: routePoints[newIndex],
      countdown: routePoints[newIndex].staySeconds,
    }));

    if (patrolState.executionLogId) {
      patrolRouteApi.updateProgress(patrolState.executionLogId, newIndex + 1);
    }
  };

  useEffect(() => {
    return () => {
      if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
      if (patrolTimerRef.current) clearInterval(patrolTimerRef.current);
    };
  }, []);

  const viewLogs = async (record: PatrolRoute) => {
    const res: any = await patrolRouteApi.listExecutionLogs({ current: 1, size: 20, routeId: record.id });
    if (res.code === 200) {
      setExecutionLogs(res.data.records);
      setShowLogModal(true);
    }
  };

  const columns: ColumnsType<PatrolRoute> = [
    {
      title: '路线名称',
      dataIndex: 'routeName',
      width: 180,
      render: (text, record) => (
        <Space>
          <CarOutlined style={{ color: '#1890ff' }} />
          <span>{text}</span>
        </Space>
      ),
    },
    {
      title: '路线编码',
      dataIndex: 'routeCode',
      width: 140,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (val) => (
        <Tag color={val === 1 ? 'success' : 'default'}>
          {PATROL_STATUS_LABELS[val]}
        </Tag>
      ),
    },
    {
      title: '点位数量',
      width: 100,
      render: (_, record) => (
        <Space>
          <VideoCameraOutlined />
          <span>{record.points?.length || 0} 个</span>
        </Space>
      ),
    },
    {
      title: '停留时间',
      dataIndex: 'staySeconds',
      width: 100,
      render: (val) => `${val} 秒`,
    },
    {
      title: '循环模式',
      dataIndex: 'loopMode',
      width: 100,
      render: (val) => (val === 1 ? <Tag color="processing">循环</Tag> : '单次'),
    },
    {
      title: '创建人',
      dataIndex: 'createUserName',
      width: 100,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
    },
    {
      title: '操作',
      width: 240,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="primary"
            size="small"
            icon={<PlayCircleOutlined />}
            onClick={() => startPatrol(record)}
          >
            开始巡逻
          </Button>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button
            size="small"
            icon={<ClockCircleOutlined />}
            onClick={() => viewLogs(record)}
          >
            日志
          </Button>
          <Popconfirm title="确定删除该路线？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="keyword" label="搜索">
            <Input placeholder="路线名称/编码" prefix={<SearchOutlined />} allowClear style={{ width: 200 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">查询</Button>
              <Button onClick={handleReset}>重置</Button>
              <Button onClick={loadData} icon={<ReloadOutlined />}>刷新</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新建路线</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        <Table
          columns={columns}
          dataSource={data}
          loading={loading}
          rowKey="id"
          pagination={{
            current,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (c, s) => { setCurrent(c); setPageSize(s); },
          }}
        />
      </Card>

      <Modal
        title={editingId ? '编辑巡逻路线' : '新建巡逻路线'}
        open={editModal}
        width={900}
        onOk={handleSave}
        onCancel={() => setEditModal(false)}
        okText="保存"
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="routeName"
                label="路线名称"
                rules={[{ required: true, message: '请输入路线名称' }]}
              >
                <Input placeholder="例如：京港澳高速K100-K150南" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="routeCode"
                label="路线编码"
                rules={[{ required: true, message: '请输入路线编码' }]}
              >
                <Input placeholder="例如：PATROL-001" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="staySeconds" label="默认停留时间(秒)">
                <InputNumber min={5} max={300} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="loopMode" label="循环模式">
                <Select>
                  <Option value={0}>单次执行</Option>
                  <Option value={1}>循环执行</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="路线描述">
            <Input.TextArea rows={2} placeholder="请输入路线描述" />
          </Form.Item>

          <Divider orientation="left">
            <Space>
              <EnvironmentOutlined />
              巡逻点位（{selectedCameras.length} 个）
            </Space>
          </Divider>

          <Button
            block
            icon={<EnvironmentOutlined />}
            onClick={() => setShowMapModal(true)}
            style={{ marginBottom: 12 }}
          >
            在地图上选择摄像头
          </Button>

          {selectedCameras.length > 0 ? (
            <List
              bordered
              dataSource={selectedCameras}
              renderItem={(item, index) => (
                <List.Item
                  actions={[
                    <Tooltip key="up" title="上移">
                      <Button
                        type="text"
                        size="small"
                        icon={<ArrowUpOutlined />}
                        disabled={index === 0}
                        onClick={() => moveCamera(index, 'up')}
                      />
                    </Tooltip>,
                    <Tooltip key="down" title="下移">
                      <Button
                        type="text"
                        size="small"
                        icon={<ArrowDownOutlined />}
                        disabled={index === selectedCameras.length - 1}
                        onClick={() => moveCamera(index, 'down')}
                      />
                    </Tooltip>,
                    <Tooltip key="delete" title="移除">
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => removeCamera(index)}
                      />
                    </Tooltip>,
                  ]}
                >
                  <Space style={{ width: '100%' }}>
                    <Tag color="blue">#{index + 1}</Tag>
                    <VideoCameraOutlined />
                    <span style={{ minWidth: 150 }}>{item.cameraName}</span>
                    <span style={{ color: '#888', fontSize: 12 }}>
                      {item.location || '未知位置'}
                    </span>
                    <span style={{ marginLeft: 'auto' }}>
                      停留:
                      <InputNumber
                        size="small"
                        min={5}
                        max={300}
                        value={item.staySeconds}
                        onChange={(v) => updateCameraStaySeconds(index, v as number)}
                        style={{ width: 80, marginLeft: 8 }}
                      />
                      秒
                    </span>
                  </Space>
                </List.Item>
              )}
            />
          ) : (
            <Empty description="点击上方按钮在地图上选择摄像头" />
          )}
        </Form>
      </Modal>

      <Modal
        title="地图选点"
        open={showMapModal}
        width={1000}
        onOk={() => setShowMapModal(false)}
        onCancel={() => setShowMapModal(false)}
        okText="完成选择"
        destroyOnClose
      >
        <Alert
          message="点击地图上的摄像头标记添加到巡逻路线，再次点击取消"
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
        />
        <div ref={mapRef} style={{ height: 500, borderRadius: 8 }} />
        <div style={{ marginTop: 12, textAlign: 'right' }}>
          <Button icon={<ClearOutlined />} onClick={() => setSelectedCameras([])}>
            清空选择
          </Button>
        </div>
      </Modal>

      <Modal
        title={
          <Space>
            <CarOutlined />
            虚拟巡逻中
            {patrolState.isRunning && (
              <Badge status="processing" text="运行中" />
            )}
          </Space>
        }
        open={showExecutionModal}
        width={1200}
        onCancel={() => {
          if (patrolState.isRunning) {
            Modal.confirm({
              title: '确定停止巡逻？',
              content: '当前巡逻任务尚未完成',
              onOk: () => {
                if (patrolState.executionLogId) {
                  stopPatrol(patrolState.executionLogId, '用户手动停止');
                }
                setShowExecutionModal(false);
              },
            });
          } else {
            setShowExecutionModal(false);
          }
        }}
        footer={null}
        destroyOnClose
      >
        {patrolState.currentPoint ? (
          <Row gutter={16}>
            <Col span={17}>
              <Card
                title={
                  <Space>
                    <Tag color="blue">
                      #{patrolState.currentIndex + 1}/{routePoints.length}
                    </Tag>
                    <VideoCameraOutlined />
                    {patrolState.currentPoint.cameraName}
                    {patrolState.isPaused && <Tag color="orange">已暂停</Tag>}
                  </Space>
                }
                extra={
                  <Space>
                    <Statistic
                      title="倒计时"
                      value={patrolState.countdown}
                      suffix="秒"
                      valueStyle={{ fontSize: 18, color: patrolState.countdown <= 5 ? '#ff4d4f' : undefined }}
                    />
                  </Space>
                }
              >
                <VideoPlayer
                  url={`/api/cameras/${patrolState.currentPoint.cameraId}/stream`}
                  cameraId={patrolState.currentPoint.cameraId}
                  height={420}
                  enableDetectionOverlay
                />

                <div style={{ marginTop: 16, textAlign: 'center' }}>
                  <Space size="large">
                    <Button
                      size="large"
                      icon={<StepBackwardOutlined />}
                      onClick={() => stepToPoint('prev')}
                    >
                      上一个
                    </Button>
                    <Button
                      type="primary"
                      size="large"
                      icon={patrolState.isPaused ? <PlayCircleOutlined /> : <PauseCircleOutlined />}
                      onClick={togglePause}
                    >
                      {patrolState.isPaused ? '继续' : '暂停'}
                    </Button>
                    <Button
                      size="large"
                      icon={<StepForwardOutlined />}
                      onClick={() => stepToPoint('next')}
                    >
                      下一个
                    </Button>
                    {!patrolState.isRunning && (
                      <Button
                        type="primary"
                        size="large"
                        icon={<CheckCircleOutlined />}
                        onClick={() => setShowExecutionModal(false)}
                      >
                        完成
                      </Button>
                    )}
                  </Space>
                </div>

                <div style={{ marginTop: 16 }}>
                  <Progress
                    percent={Math.round(((patrolState.currentIndex + 1) / routePoints.length) * 100)}
                    showInfo
                    strokeColor={{
                      '0%': '#1890ff',
                      '100%': '#52c41a',
                    }}
                  />
                </div>
              </Card>
            </Col>

            <Col span={7}>
              <Card title="巡逻点位" size="small" style={{ marginBottom: 12 }}>
                <List
                  size="small"
                  dataSource={routePoints}
                  renderItem={(item, index) => (
                    <List.Item
                      style={{
                        background: index === patrolState.currentIndex ? '#e6f7ff' : undefined,
                        borderRadius: 4,
                        padding: '8px 12px',
                      }}
                    >
                      <Space>
                        <Tag color={index === patrolState.currentIndex ? 'blue' : 'default'}>
                          #{index + 1}
                        </Tag>
                        <VideoCameraOutlined />
                        <span>{item.cameraName}</span>
                        {index === patrolState.currentIndex && (
                          <Badge status="processing" />
                        )}
                      </Space>
                    </List.Item>
                  )}
                />
              </Card>

              <Card
                title={
                  <Space>
                    <ExclamationCircleOutlined />
                    检测事件
                    <Tag color="red">{patrolState.detectedEvents.length}</Tag>
                  </Space>
                }
                size="small"
              >
                {patrolState.detectedEvents.length > 0 ? (
                  <List
                    size="small"
                    dataSource={patrolState.detectedEvents}
                    renderItem={(event) => (
                      <List.Item>
                        <Space direction="vertical" size={0} style={{ width: '100%' }}>
                          <Space>
                            <Tag color="red">{event.eventType}</Tag>
                            <span style={{ fontSize: 12, color: '#888' }}>
                              {event.eventTime}
                            </span>
                          </Space>
                          <span style={{ fontSize: 12 }}>{event.description || event.location}</span>
                        </Space>
                      </List.Item>
                    )}
                  />
                ) : (
                  <Empty description="暂无检测事件" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )}
              </Card>
            </Col>
          </Row>
        ) : (
          <Empty description="无巡逻数据" />
        )}
      </Modal>

      <Modal
        title="巡逻执行日志"
        open={showLogModal}
        width={900}
        onCancel={() => setShowLogModal(false)}
        footer={null}
      >
        <Table
          dataSource={executionLogs}
          rowKey="id"
          pagination={false}
          columns={[
            {
              title: '执行状态',
              dataIndex: 'executionStatus',
              width: 100,
              render: (val) => (
                <Tag color={PATROL_EXECUTION_STATUS_COLORS[val]}>
                  {PATROL_EXECUTION_STATUS_LABELS[val]}
                </Tag>
              ),
            },
            {
              title: '进度',
              width: 150,
              render: (_, record) => (
                <Progress
                  percent={Math.round((record.completedPoints / (record.totalPoints || 1)) * 100)}
                  size="small"
                />
              ),
            },
            { title: '启动人', dataIndex: 'startUserName', width: 100 },
            { title: '开始时间', dataIndex: 'startTime', width: 170 },
            { title: '结束时间', dataIndex: 'endTime', width: 170 },
            { title: '备注', dataIndex: 'remark' },
          ]}
        />
      </Modal>
    </div>
  );
};

export default VirtualPatrol;
