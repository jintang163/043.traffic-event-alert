import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Table,
  Button,
  Form,
  Input,
  Select,
  DatePicker,
  Tag,
  Modal,
  Space,
  Row,
  Col,
  Statistic,
  Divider,
  Image,
  Tooltip,
  Popconfirm,
  message,
  Slider,
  Switch,
  DatePicker,
} from 'antd';
import {
  SearchOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  DownloadOutlined,
  InfoCircleOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import Hls from 'hls.js';
import { videoApi, cameraApi } from '@/services/api';
import {
  CLIP_TYPE_OPTIONS,
  RECORD_STATUS_LABELS,
  EVENT_TYPE_LABELS,
  type VideoClip,
} from '@/types';

const { Option } = Select;

const PLAYBACK_RATES = [0.5, 0.75, 1, 1.25, 1.5, 2];

const EventVideos: React.FC = () => {
  const [searchForm] = Form.useForm();
  const [data, setData] = useState<VideoClip[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [cameras, setCameras] = useState<Array<{ id: number; cameraName: string }>>([]);
  const [stats, setStats] = useState<{ todayCount?: number; storageMB?: number }>({});
  const [ffmpegAvailable, setFfmpegAvailable] = useState<boolean | null>(null);

  const [playModal, setPlayModal] = useState(false);
  const [playClip, setPlayClip] = useState<VideoClip | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [playbackRate, setPlaybackRate] = useState(1);
  const [videoLoading, setVideoLoading] = useState(true);
  const [videoError, setVideoError] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const params: any = {
        ...values,
        current,
        size: pageSize,
      };
      if (values.timeRange && values.timeRange.length === 2) {
        params.startTime = values.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = values.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const res: any = await videoApi.page(params);
      if (res.code === 200) {
        setData(res.data.records || []);
        setTotal(res.data.total || 0);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [current, pageSize]);

  useEffect(() => {
    cameraApi.page({ current: 1, size: 500 }).then((res: any) => {
      if (res.code === 200) {
        setCameras(res.data.records || []);
      }
    });
    videoApi.stats().then((res: any) => {
      if (res.code === 200) setStats(res.data);
    });
    videoApi.ffmpegStatus().then((res: any) => {
      if (res.code === 200) setFfmpegAvailable(res.data.ffmpegAvailable);
    });
  }, []);

  const handleSearch = () => {
    setCurrent(1);
    loadData();
  };

  const handleReset = () => {
    searchForm.resetFields();
    setCurrent(1);
    loadData();
  };

  const openPlayModal = async (clip: VideoClip) => {
    const res: any = await videoApi.get(clip.id);
    if (res.code === 200) {
      setPlayClip(res.data);
    } else {
      setPlayClip(clip);
    }
    setPlayModal(true);
  };

  useEffect(() => {
    if (!playModal || !playClip || !videoRef.current) return;
    const video = videoRef.current;
    const url = playClip.hlsPlaylistUrl || playClip.fileUrl || '';

    setVideoLoading(true);
    setVideoError(null);
    setPlaybackRate(1);

    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }

    const cleanup = () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };

    if (Hls.isSupported() && (url.includes('.m3u8') || url.includes('hls'))) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
        backBufferLength: 30,
      });
      hlsRef.current = hls;

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        setVideoLoading(false);
        video.play().catch(() => {});
      });

      hls.on(Hls.Events.ERROR, (_e: any, data: any) => {
        if (data.fatal) {
          setVideoLoading(false);
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              setVideoError('网络错误，正在重试...');
              hls.startLoad();
              setTimeout(() => setVideoError(null), 2000);
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              setVideoError('解码错误，正在恢复...');
              hls.recoverMediaError();
              setTimeout(() => setVideoError(null), 2000);
              break;
            default:
              setVideoError('视频播放失败');
              cleanup();
              break;
          }
        }
      });

      hls.loadSource(url);
      hls.attachMedia(video);
    } else if (video.canPlayType('application/vnd.apple.mpegurl') || url.startsWith('http')) {
      video.src = url;
      video.onloadeddata = () => {
        setVideoLoading(false);
        video.play().catch(() => {});
      };
      video.onerror = () => {
        setVideoLoading(false);
        setVideoError('视频加载失败');
      };
    } else {
      setVideoLoading(false);
      setVideoError('不支持的视频格式');
    }

    return cleanup;
  }, [playModal, playClip]);

  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.playbackRate = playbackRate;
    }
  }, [playbackRate, playModal]);

  const handleDelete = async (id: number) => {
    const res: any = await videoApi.delete(id);
    if (res.code === 200) {
      message.success('已删除');
      loadData();
    } else {
      message.error('删除失败');
    }
  };

  const getClipTypeInfo = (type?: string) => {
    if (!type) return { label: '未知', color: '#8c8c8c' };
    const match = CLIP_TYPE_OPTIONS.find((c) => c.value === type);
    if (match) return match;
    const eventLabel = EVENT_TYPE_LABELS[type as keyof typeof EVENT_TYPE_LABELS];
    return { label: eventLabel || type, color: '#8c8c8c' };
  };

  const formatSize = (bytes?: number) => {
    if (!bytes) return '-';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
  };

  const columns: ColumnsType<VideoClip> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
    },
    {
      title: '缩略图',
      dataIndex: 'thumbnailUrl',
      width: 140,
      render: (url, record) =>
        url ? (
          <Image
            src={url}
            alt="thumbnail"
            width={120}
            height={68}
            style={{ objectFit: 'cover', borderRadius: 4, border: '1px solid #eee' }}
            preview={{ mask: '查看大图' }}
            fallback="data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='120' height='68'><rect fill='%23f5f5f5' width='120' height='68'/><text x='60' y='36' text-anchor='middle' fill='%23ccc' font-size='12'>无缩略图</text></svg>"
          />
        ) : (
          <span style={{ color: '#ccc' }}>无</span>
        ),
    },
    {
      title: '摄像头',
      dataIndex: 'cameraName',
      width: 150,
      ellipsis: true,
      render: (name, record) => (
        <span>
          {name || `摄像头#${record.cameraId}`}
        </span>
      ),
    },
    {
      title: '事件类型',
      dataIndex: 'clipType',
      width: 110,
      render: (type) => {
        const info = getClipTypeInfo(type);
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: '关联事件',
      dataIndex: 'eventNo',
      width: 160,
      render: (no, record) => (
        <Space direction="vertical" size={2}>
          <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{no || '-'}</span>
          {record.alertEventId && (
            <span style={{ color: '#8c8c8c', fontSize: 11 }}>
              eventId: {record.alertEventId}
            </span>
          )}
        </Space>
      ),
    },
    {
      title: '时间范围',
      dataIndex: 'startTime',
      width: 180,
      render: (_t, record) => (
        <Space direction="vertical" size={0}>
          <span style={{ fontSize: 12 }}>起: {record.startTime?.replace('T', ' ') || '-'}</span>
          <span style={{ fontSize: 12 }}>止: {record.endTime?.replace('T', ' ') || '-'}</span>
        </Space>
      ),
    },
    {
      title: '时长',
      dataIndex: 'duration',
      width: 80,
      align: 'right',
      render: (sec) => (sec != null ? `${sec}s` : '-'),
    },
    {
      title: '文件大小',
      dataIndex: 'fileSize',
      width: 90,
      align: 'right',
      render: (size) => formatSize(size),
    },
    {
      title: '录制状态',
      dataIndex: 'recordStatus',
      width: 90,
      render: (status) => {
        const info = RECORD_STATUS_LABELS[status as number] || RECORD_STATUS_LABELS[0];
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: '操作',
      width: 170,
      fixed: 'right',
      render: (_v, record) => (
        <Space size="small">
          <Tooltip title="播放回放">
            <Button
              type="link"
              size="small"
              icon={<PlayCircleOutlined />}
              disabled={record.recordStatus !== 2}
              onClick={() => openPlayModal(record)}
            >
              播放
            </Button>
          </Tooltip>
          {record.recordStatus === -1 && (
            <Tooltip title={record.failReason}>
              <InfoCircleOutlined style={{ color: '#ff4d4f' }} />
            </Tooltip>
          )}
          <Popconfirm title="确定删除此视频？" onConfirm={() => handleDelete(record.id!)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8 }}>
            <Statistic
              title="今日视频数"
              value={stats.todayCount ?? 0}
              suffix="条"
              prefix={<FileTextOutlined style={{ color: '#1890ff' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8 }}>
            <Statistic
              title="存储用量"
              value={stats.storageMB ?? 0}
              precision={1}
              suffix="MB"
              prefix={<InfoCircleOutlined style={{ color: '#722ed1' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8 }}>
            <Statistic
              title="ffmpeg可用"
              value={ffmpegAvailable === null ? '检测中' : ffmpegAvailable ? '已就绪' : '未安装'}
              valueStyle={{ color: ffmpegAvailable === true ? '#52c41a' : ffmpegAvailable === false ? '#ff4d4f' : '#8c8c8c' }}
              prefix={
                ffmpegAvailable === true ? (
                  <PlayCircleOutlined />
                ) : ffmpegAvailable === false ? (
                  <InfoCircleOutlined />
                ) : null
              }
            />
            {ffmpegAvailable === false && (
              <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 4 }}>
                请安装ffmpeg并加入PATH
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={{ borderRadius: 8 }}>
            <Statistic
              title="总视频数"
              value={total}
              suffix="条"
              prefix={<PlayCircleOutlined style={{ color: '#fa8c16' }} />}
            />
          </Card>
        </Col>
      </Row>

      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="keyword" label="关键词">
            <Input placeholder="事件号/摄像头名" style={{ width: 160 }} allowClear />
          </Form.Item>
          <Form.Item name="cameraId" label="摄像头">
            <Select placeholder="全部" style={{ width: 160 }} allowClear showSearch optionFilterProp="children">
              {cameras.map((c) => (
                <Option key={c.id} value={c.id}>
                  {c.cameraName}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="clipType" label="事件类型">
            <Select placeholder="全部" style={{ width: 140 }} allowClear>
              {CLIP_TYPE_OPTIONS.map((opt) => (
                <Option key={opt.value} value={opt.value}>
                  {opt.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="recordStatus" label="录制状态">
            <Select placeholder="全部" style={{ width: 110 }} allowClear>
              {Object.entries(RECORD_STATUS_LABELS).map(([k, v]) => (
                <Option key={k} value={Number(k)}>
                  {v.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="timeRange" label="时间">
            <DatePicker.RangePicker showTime style={{ width: 360 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                搜索
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
              <Button icon={<ReloadOutlined />} onClick={() => loadData()}>
                刷新
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card style={{ borderRadius: 8 }} title="视频片段列表">
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1280 }}
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
        />
      </Card>

      <Modal
        title={
          playClip ? (
            <Space>
              <PlayCircleOutlined style={{ color: '#1890ff' }} />
              <span>{playClip.cameraName || `摄像头#${playClip.cameraId}`}</span>
              {playClip.eventNo && (
                <Tag color="blue">{playClip.eventNo}</Tag>
              )}
            </Space>
          ) : '视频回放'
        }
        open={playModal}
        onCancel={() => setPlayModal(false)}
        width={900}
        footer={null}
        destroyOnClose
      >
        {playClip && (
          <div>
            <div
              style={{
                background: '#000',
                borderRadius: 8,
                position: 'relative',
                overflow: 'hidden',
              }}
            >
              <video
                ref={videoRef}
                controls
                playsInline
                crossOrigin="anonymous"
                style={{
                  width: '100%',
                  maxHeight: 520,
                  background: '#000',
                  display: 'block',
                }}
              />
              {videoLoading && (
                <div
                  style={{
                    position: 'absolute',
                    inset: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: 'rgba(0,0,0,0.7)',
                    color: '#fff',
                  }}
                >
                  <Space direction="vertical" align="center">
                    <div className="ant-spin ant-spin-spinning">
                      <span className="ant-spin-dot ant-spin-dot-spin">
                        <i></i>
                        <i></i>
                        <i></i>
                        <i></i>
                      </span>
                    </div>
                    <span>视频加载中...</span>
                  </Space>
                </div>
              )}
              {videoError && !videoLoading && (
                <div
                  style={{
                    position: 'absolute',
                    inset: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: 'rgba(0,0,0,0.85)',
                    color: '#ff4d4f',
                    flexDirection: 'column',
                    gap: 8,
                  }}
                >
                  <InfoCircleOutlined style={{ fontSize: 40 }} />
                  <span>{videoError}</span>
                </div>
              )}
            </div>

            <Row gutter={16} style={{ marginTop: 20 }}>
              <Col span={12}>
                <div style={{ marginBottom: 8, fontWeight: 600 }}>播放倍速</div>
                <Space wrap>
                  {PLAYBACK_RATES.map((r) => (
                    <Button
                      key={r}
                      type={playbackRate === r ? 'primary' : 'default'}
                      size="small"
                      onClick={() => setPlaybackRate(r)}
                    >
                      {r}x
                    </Button>
                  ))}
                </Space>
                <div style={{ marginTop: 12, color: '#595959', fontSize: 12 }}>
                  当前: {playbackRate}x 倍速
                </div>
              </Col>
              <Col span={12}>
                <div style={{ marginBottom: 8, fontWeight: 600 }}>视频信息</div>
                <Space direction="vertical" size={4} style={{ fontSize: 13 }}>
                  {playClip.startTime && (
                    <div style={{ color: '#595959' }}>录制时间: {playClip.startTime.replace('T', ' ')} ~ {playClip.endTime?.replace('T', ' ')}</div>
                  )}
                  {playClip.duration != null && (
                    <div style={{ color: '#595959' }}>时长: {playClip.duration} 秒</div>
                  )}
                  {playClip.fileSize != null && (
                    <div style={{ color: '#595959' }}>大小: {formatSize(playClip.fileSize)}</div>
                  )}
                  {playClip.failReason && (
                    <div style={{ color: '#ff4d4f' }}>失败原因: {playClip.failReason}</div>
                  )}
                </Space>
                <Divider style={{ margin: '12px 0' }} />
                <Space>
                  {playClip.fileUrl && (
                    <Button
                      icon={<DownloadOutlined />}
                      size="small"
                      onClick={() => window.open(playClip.fileUrl, '_blank')}
                    >
                      下载MP4
                    </Button>
                  )}
                  {playClip.hlsPlaylistUrl && (
                    <Tooltip title="HLS m3u8 播放地址">
                      <Button
                        icon={<PlayCircleOutlined />}
                        size="small"
                        onClick={() => {
                          navigator.clipboard?.writeText(playClip.hlsPlaylistUrl!);
                          message.success('播放地址已复制');
                        }}
                      >
                        复制HLS地址
                      </Button>
                    </Tooltip>
                  )}
                </Space>
              </Col>
            </Row>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default EventVideos;
