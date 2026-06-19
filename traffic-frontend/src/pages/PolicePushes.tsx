import React, { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Space,
  Tag,
  Input,
  Select,
  Card,
  Form,
  DatePicker,
  Tooltip,
  Modal,
  Descriptions,
  message,
  Statistic,
  Row,
  Col,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined, ReloadOutlined, RetweetOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { policePushApi } from '@/services/api';
import {
  type PolicePush,
  type PolicePushQuery,
  POLICE_PUSH_STATUS_LABELS,
  EVENT_TYPE_LABELS,
} from '@/types';

const { RangePicker } = DatePicker;
const { Option } = Select;

const PolicePushes: React.FC = () => {
  const [data, setData] = useState<PolicePush[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [detailModal, setDetailModal] = useState(false);
  const [detail, setDetail] = useState<PolicePush | null>(null);
  const [stats, setStats] = useState<Record<string, any>>({});

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const params: PolicePushQuery & Record<string, any> = {
        ...values,
        current,
        size: pageSize,
      };
      if (values.timeRange && values.timeRange.length === 2) {
        params.startTime = values.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = values.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const res: any = await policePushApi.page(params);
      if (res.code === 200) {
        setData(res.data.records || []);
        setTotal(res.data.total || 0);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadStats = async () => {
    try {
      const res: any = await policePushApi.statistics();
      if (res.code === 200) setStats(res.data || {});
    } catch (_) {}
  };

  useEffect(() => {
    loadData();
    loadStats();
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

  const handleRetry = async (id: number) => {
    try {
      const res: any = await policePushApi.retry(id);
      if (res.code === 200) {
        message.success('已触发重试');
        loadData();
      }
    } catch (_) {}
  };

  const handleViewDetail = async (id: number) => {
    try {
      const res: any = await policePushApi.get(id);
      if (res.code === 200) {
        setDetail(res.data);
        setDetailModal(true);
      }
    } catch (_) {}
  };

  const columns: ColumnsType<PolicePush> = [
    {
      title: '推送编号',
      dataIndex: 'pushNo',
      width: 180,
      render: (v) => v || '-',
    },
    {
      title: '事件编号',
      dataIndex: 'eventNo',
      width: 180,
      render: (v) => v || '-',
    },
    {
      title: '事件类型',
      dataIndex: 'eventType',
      width: 110,
      render: (v) => (v ? EVENT_TYPE_LABELS[v] || v : '-'),
    },
    {
      title: '车牌号',
      dataIndex: 'plateNumber',
      width: 120,
      render: (v) => (v ? <Tag color="blue" style={{ fontSize: 13, fontWeight: 600 }}>{v}</Tag> : '-'),
    },
    { title: '推送目标', dataIndex: 'pushTarget', width: 150, render: (v) => v || '-' },
    {
      title: '状态',
      dataIndex: 'pushStatus',
      width: 110,
      render: (val) => {
        const s = POLICE_PUSH_STATUS_LABELS[val] || { label: `${val}`, color: 'default' };
        return <Tag color={s.color}>{s.label}</Tag>;
      },
    },
    {
      title: '重试',
      width: 80,
      align: 'center',
      render: (_, r) => `${r.retryCount || 0}/${r.maxRetry || '-'}`,
    },
    { title: '耗时(ms)', dataIndex: 'costMs', width: 90, align: 'center', render: (v) => v ?? '-' },
    {
      title: '下次重试',
      dataIndex: 'nextRetryTime',
      width: 170,
      render: (v) => v || '-',
    },
    {
      title: '推送时间',
      dataIndex: 'pushTime',
      width: 170,
      render: (v) => v || '-',
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      width: 180,
      ellipsis: true,
      render: (v) => (v ? <Tooltip title={v}>{v}</Tooltip> : '-'),
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<InfoCircleOutlined />} onClick={() => handleViewDetail(r.id!)}>
            详情
          </Button>
          {r.pushStatus === 3 && (
            <Button type="link" size="small" icon={<RetweetOutlined />} onClick={() => handleRetry(r.id!)}>
              重试
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Row gutter={12} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="推送总数" value={stats.totalCount ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="成功" value={stats.successCount ?? 0} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="失败" value={stats.failCount ?? 0} valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="待重试" value={stats.pendingRetryCount ?? 0} valueStyle={{ color: '#faad14' }} />
          </Card>
        </Col>
      </Row>

      <Card style={{ marginBottom: 16 }}>
        <Form layout="inline" form={searchForm} onFinish={handleSearch}>
          <Form.Item name="plateNumber" label="车牌号">
            <Input placeholder="请输入车牌号" allowClear style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="eventNo" label="事件编号">
            <Input placeholder="请输入事件编号" allowClear style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="pushStatus" label="推送状态">
            <Select placeholder="全部" allowClear style={{ width: 140 }}>
              <Option value={0}>待推送</Option>
              <Option value={1}>推送中</Option>
              <Option value={2}>推送成功</Option>
              <Option value={3}>推送失败</Option>
            </Select>
          </Form.Item>
          <Form.Item name="pushTarget" label="推送目标">
            <Input placeholder="请输入推送目标" allowClear style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="timeRange" label="推送时间">
            <RangePicker showTime style={{ width: 360 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} htmlType="submit">
                查询
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 15, fontWeight: 600 }}>交警系统推送日志 ({total})</span>
          <Button icon={<ReloadOutlined />} onClick={() => { loadData(); loadStats(); }}>
            刷新
          </Button>
        </div>
        <Table
          size="small"
          rowKey="id"
          loading={loading}
          dataSource={data}
          columns={columns}
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
          scroll={{ x: 1700 }}
        />
      </Card>

      <Modal
        title="推送详情"
        open={detailModal}
        onCancel={() => setDetailModal(false)}
        footer={null}
        width={720}
      >
        {detail && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="推送编号">{detail.pushNo}</Descriptions.Item>
            <Descriptions.Item label="事件编号">{detail.eventNo}</Descriptions.Item>
            <Descriptions.Item label="事件类型">{detail.eventType ? EVENT_TYPE_LABELS[detail.eventType] || detail.eventType : '-'}</Descriptions.Item>
            <Descriptions.Item label="车牌号">{detail.plateNumber || '-'}</Descriptions.Item>
            <Descriptions.Item label="推送目标">{detail.pushTarget || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">
              {POLICE_PUSH_STATUS_LABELS[detail.pushStatus]?.label || detail.pushStatus}
            </Descriptions.Item>
            <Descriptions.Item label="重试">{`${detail.retryCount || 0}/${detail.maxRetry || '-'}`}</Descriptions.Item>
            <Descriptions.Item label="耗时">{detail.costMs != null ? `${detail.costMs} ms` : '-'}</Descriptions.Item>
            <Descriptions.Item label="事件时间">{detail.eventTime || '-'}</Descriptions.Item>
            <Descriptions.Item label="推送时间">{detail.pushTime || '-'}</Descriptions.Item>
            <Descriptions.Item label="下次重试">{detail.nextRetryTime || '-'}</Descriptions.Item>
            <Descriptions.Item label="成功时间">{detail.successTime || '-'}</Descriptions.Item>
            <Descriptions.Item label="位置" span={2}>{detail.location || '-'}</Descriptions.Item>
            <Descriptions.Item label="推送内容" span={2}>
              <div style={{ whiteSpace: 'pre-wrap', fontSize: 12, maxHeight: 160, overflow: 'auto' }}>
                {detail.pushBody || '-'}
              </div>
            </Descriptions.Item>
            <Descriptions.Item label="响应内容" span={2}>
              <div style={{ whiteSpace: 'pre-wrap', fontSize: 12, maxHeight: 160, overflow: 'auto' }}>
                {detail.responseBody || '-'}
              </div>
            </Descriptions.Item>
            <Descriptions.Item label="错误信息" span={2}>
              <div style={{ color: '#ff4d4f', whiteSpace: 'pre-wrap', fontSize: 12 }}>
                {detail.errorMessage || '-'}
              </div>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default PolicePushes;
