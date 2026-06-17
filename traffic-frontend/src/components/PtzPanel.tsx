import React, { useState, useEffect, useCallback } from 'react';
import {
  Button,
  Card,
  Slider,
  Space,
  Tag,
  Input,
  Popconfirm,
  message,
  List,
  Tooltip,
  Divider,
} from 'antd';
import {
  ArrowUpOutlined,
  ArrowDownOutlined,
  ArrowLeftOutlined,
  ArrowRightOutlined,
  PlusOutlined,
  MinusOutlined,
  PauseOutlined,
  SettingOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  CameraOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { cameraApi, ptzPresetApi } from '@/services/api';

interface PtzPanelProps {
  cameraId: number;
  ptzEnabled?: number;
  onPresetChange?: () => void;
}

interface PresetItem {
  id: number;
  cameraId: number;
  presetIndex: number;
  presetName: string;
  thumbnailUrl?: string;
  sortOrder?: number;
}

const PtzPanel: React.FC<PtzPanelProps> = ({ cameraId, ptzEnabled = 1, onPresetChange }) => {
  const [speed, setSpeed] = useState<number>(5);
  const [presets, setPresets] = useState<PresetItem[]>([]);
  const [newPresetName, setNewPresetName] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);

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
    loadPresets();
  }, [loadPresets]);

  const sendPtzCommand = async (command: string, action: string = 'start') => {
    if (!cameraId || ptzEnabled !== 1) return;
    try {
      await cameraApi.ptzControl(cameraId, {
        command,
        speed,
        action,
      });
    } catch (e) {
      console.error('云台控制失败', e);
    }
  };

  const handleDirectionStart = (direction: string) => {
    sendPtzCommand(direction, 'start');
  };

  const handleDirectionStop = () => {
    sendPtzCommand('stop', 'stop');
  };

  const handleZoomStart = (zoom: string) => {
    sendPtzCommand(zoom, 'start');
  };

  const handleZoomStop = () => {
    sendPtzCommand('zoomStop', 'stop');
  };

  const handleGotoPreset = async (presetIndex: number) => {
    if (!cameraId) return;
    try {
      await cameraApi.ptzControl(cameraId, {
        command: 'gotoPreset',
        presetIndex,
      });
      message.success(`已转到预置位 ${presetIndex}`);
    } catch (e) {
      message.error('调用预置位失败');
    }
  };

  const handleSetPreset = async () => {
    if (!cameraId) return;
    if (!newPresetName.trim()) {
      message.warning('请输入预置位名称');
      return;
    }
    setLoading(true);
    try {
      const nextRes = await ptzPresetApi.nextIndex(cameraId);
      const nextIndex = nextRes.data || 1;

      await cameraApi.ptzControl(cameraId, {
        command: 'setPreset',
        presetIndex: nextIndex,
        presetName: newPresetName.trim(),
      });

      await ptzPresetApi.save({
        cameraId,
        presetIndex: nextIndex,
        presetName: newPresetName.trim(),
      });

      setNewPresetName('');
      message.success('预置位设置成功');
      loadPresets();
      onPresetChange?.();
    } catch (e) {
      message.error('设置预置位失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDeletePreset = async (id: number, presetIndex: number) => {
    try {
      await ptzPresetApi.delete(id);
      message.success('预置位已删除');
      loadPresets();
      onPresetChange?.();
    } catch (e) {
      message.error('删除预置位失败');
    }
  };

  if (ptzEnabled !== 1) {
    return (
      <Card size="small" title={<span><RobotOutlined /> 云台控制</span>}>
        <div style={{ textAlign: 'center', color: '#999', padding: '20px 0' }}>
          <RobotOutlined style={{ fontSize: 32, marginBottom: 8 }} />
          <div>该摄像头不支持云台控制</div>
        </div>
      </Card>
    );
  }

  const directionBtnStyle = { width: 50, height: 50, fontSize: 18 };

  return (
    <Card
      size="small"
      title={
        <Space>
          <RobotOutlined />
          <span>云台控制</span>
          <Tag color="green" size="small">在线</Tag>
        </Space>
      }
    >
      <div style={{ textAlign: 'center', marginBottom: 12 }}>
        <div style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>
          云台速度: {speed}
        </div>
        <Slider
          min={1}
          max={10}
          value={speed}
          onChange={setSpeed}
          style={{ width: '80%', margin: '0 auto' }}
        />
      </div>

      <Divider style={{ margin: '8px 0' }} />

      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 12 }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 50px)',
            gridTemplateRows: 'repeat(3, 50px)',
            gap: 4,
            alignItems: 'center',
            justifyItems: 'center',
          }}
        >
          <Tooltip title="左上">
            <Button
              type="default"
              shape="circle"
              icon={<ArrowUpOutlined style={{ transform: 'rotate(-45deg)' }} />}
              style={directionBtnStyle}
              onMouseDown={() => handleDirectionStart('upLeft')}
              onMouseUp={handleDirectionStop}
              onMouseLeave={handleDirectionStop}
              onTouchStart={() => handleDirectionStart('upLeft')}
              onTouchEnd={handleDirectionStop}
            />
          </Tooltip>
          <Tooltip title="上">
            <Button
              type="primary"
              shape="circle"
              icon={<ArrowUpOutlined />}
              style={directionBtnStyle}
              onMouseDown={() => handleDirectionStart('up')}
              onMouseUp={handleDirectionStop}
              onMouseLeave={handleDirectionStop}
              onTouchStart={() => handleDirectionStart('up')}
              onTouchEnd={handleDirectionStop}
            />
          </Tooltip>
          <Tooltip title="右上">
            <Button
              type="default"
              shape="circle"
              icon={<ArrowUpOutlined style={{ transform: 'rotate(45deg)' }} />}
              style={directionBtnStyle}
              onMouseDown={() => handleDirectionStart('upRight')}
              onMouseUp={handleDirectionStop}
              onMouseLeave={handleDirectionStop}
              onTouchStart={() => handleDirectionStart('upRight')}
              onTouchEnd={handleDirectionStop}
            />
          </Tooltip>

          <Tooltip title="左">
            <Button
              type="primary"
              shape="circle"
              icon={<ArrowLeftOutlined />}
              style={directionBtnStyle}
              onMouseDown={() => handleDirectionStart('left')}
              onMouseUp={handleDirectionStop}
              onMouseLeave={handleDirectionStop}
              onTouchStart={() => handleDirectionStart('left')}
              onTouchEnd={handleDirectionStop}
            />
          </Tooltip>
          <Tooltip title="停止">
            <Button
              type="default"
              danger
              shape="circle"
              icon={<PauseOutlined />}
              style={directionBtnStyle}
              onClick={handleDirectionStop}
            />
          </Tooltip>
          <Tooltip title="右">
            <Button
              type="primary"
              shape="circle"
              icon={<ArrowRightOutlined />}
              style={directionBtnStyle}
              onMouseDown={() => handleDirectionStart('right')}
              onMouseUp={handleDirectionStop}
              onMouseLeave={handleDirectionStop}
              onTouchStart={() => handleDirectionStart('right')}
              onTouchEnd={handleDirectionStop}
            />
          </Tooltip>

          <Tooltip title="左下">
            <Button
              type="default"
              shape="circle"
              icon={<ArrowDownOutlined style={{ transform: 'rotate(45deg)' }} />}
              style={directionBtnStyle}
              onMouseDown={() => handleDirectionStart('downLeft')}
              onMouseUp={handleDirectionStop}
              onMouseLeave={handleDirectionStop}
              onTouchStart={() => handleDirectionStart('downLeft')}
              onTouchEnd={handleDirectionStop}
            />
          </Tooltip>
          <Tooltip title="下">
            <Button
              type="primary"
              shape="circle"
              icon={<ArrowDownOutlined />}
              style={directionBtnStyle}
              onMouseDown={() => handleDirectionStart('down')}
              onMouseUp={handleDirectionStop}
              onMouseLeave={handleDirectionStop}
              onTouchStart={() => handleDirectionStart('down')}
              onTouchEnd={handleDirectionStop}
            />
          </Tooltip>
          <Tooltip title="右下">
            <Button
              type="default"
              shape="circle"
              icon={<ArrowDownOutlined style={{ transform: 'rotate(-45deg)' }} />}
              style={directionBtnStyle}
              onMouseDown={() => handleDirectionStart('downRight')}
              onMouseUp={handleDirectionStop}
              onMouseLeave={handleDirectionStop}
              onTouchStart={() => handleDirectionStart('downRight')}
              onTouchEnd={handleDirectionStop}
            />
          </Tooltip>
        </div>
      </div>

      <Divider style={{ margin: '8px 0' }} />

      <div style={{ display: 'flex', justifyContent: 'center', gap: 12, marginBottom: 12 }}>
        <Tooltip title="放大">
          <Button
            type="default"
            icon={<PlusOutlined />}
            onMouseDown={() => handleZoomStart('zoomIn')}
            onMouseUp={handleZoomStop}
            onMouseLeave={handleZoomStop}
            onTouchStart={() => handleZoomStart('zoomIn')}
            onTouchEnd={handleZoomStop}
          >
            放大
          </Button>
        </Tooltip>
        <Tooltip title="缩小">
          <Button
            type="default"
            icon={<MinusOutlined />}
            onMouseDown={() => handleZoomStart('zoomOut')}
            onMouseUp={handleZoomStop}
            onMouseLeave={handleZoomStop}
            onTouchStart={() => handleZoomStart('zoomOut')}
            onTouchEnd={handleZoomStop}
          >
            缩小
          </Button>
        </Tooltip>
      </div>

      <Divider style={{ margin: '8px 0' }} />

      <div>
        <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 8, display: 'flex', alignItems: 'center', gap: 4 }}>
          <SettingOutlined /> 预置位管理
        </div>

        <Space.Compact style={{ width: '100%', marginBottom: 8 }}>
          <Input
            placeholder="输入预置位名称"
            value={newPresetName}
            onChange={(e) => setNewPresetName(e.target.value)}
            onPressEnter={handleSetPreset}
          />
          <Button
            type="primary"
            icon={<CameraOutlined />}
            loading={loading}
            onClick={handleSetPreset}
          >
            设置
          </Button>
        </Space.Compact>

        <List
          size="small"
          dataSource={presets}
          locale={{ emptyText: '暂无预置位' }}
          style={{ maxHeight: 180, overflowY: 'auto' }}
          renderItem={(item) => (
            <List.Item
              key={item.id}
              actions={[
                <Tooltip key="goto" title="转到">
                  <Button
                    type="link"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    onClick={() => handleGotoPreset(item.presetIndex)}
                  />
                </Tooltip>,
                <Popconfirm
                  key="del"
                  title="确定删除该预置位？"
                  onConfirm={() => handleDeletePreset(item.id, item.presetIndex)}
                >
                  <Button type="link" size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space size={4}>
                    <Tag color="blue">{item.presetIndex}</Tag>
                    <span style={{ fontSize: 13 }}>{item.presetName}</span>
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </div>
    </Card>
  );
};

export default PtzPanel;
