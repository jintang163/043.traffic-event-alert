import React, { useEffect, useState } from 'react';
import {
  Table, Button, Space, Tag, Card, Form, Select, DatePicker, Input, message, Drawer, Descriptions, Popconfirm, Row, Col, Statistic,
} from 'antd';
import { ReloadOutlined, SearchOutlined, RedoOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { notifyLogApi } from '@/services/api';
import { NotifyLog, CHANNEL_TYPE_LABELS, SEND_STATUS_LABELS } from '@/types';

const { Option } = Select;
const { RangePicker } = DatePicker;

const NotifyLogs: React.FC = () => {
  const [data, setData] = useState<NotifyLog[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();
  const [detailDrawer, setDetailDrawer] = useState(false);
  const [currentLog, setCurrentLog] = useState<NotifyLog | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const params: any = { ...values, current, size: pageSize };
      if (values.timeRange && values.timeRange.length === 2) {
        params.startTime = values.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = values.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      delete params.timeRange;
      const res: any = await notifyLogApi.page(params);
      if (res.code === 200) {
        setData(res.data.records);
        setTotal(res.data.total);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, [current, pageSize]);

  const handleView = (record: NotifyLog) => {
    setCurrentLog(record);
    setDetailDrawer(true);
  };

  const handleRetry = async (id: number) => {
    try {
      const res: any = await notifyLogApi.retry(id);
      if (res.code === 200) {
        message.success('重试已触发');
        loadData();
      }
    } catch (e) { console.error(e); }
  };

  const getStats = () => {
    const success = data.filter(d => d.sendStatus === 2).length;
    const failed = data.filter(d => d.sendStatus === 3).length;
    const pending = data.filter(d => d.sendStatus === 0 || d.sendStatus === 1).length;
    return { success, failed, pending };
  };

  const stats = getStats();

  const columns: ColumnsType<NotifyLog> = [
    { title: 'ID', dataIndex: 'id', width: 60, align: 'center' },
    { title: '日志编号', dataIndex: 'logNo', width: 160 },
    { title: '事件编号', dataIndex: 'eventNo', width: 160 },
    {
      title: '渠道', dataIndex: 'channelType', width: 110,
      render: (val) => <Tag>{CHANNEL_TYPE_LABELS[val] || val}</Tag>,
    },
    {
      title: '接收人', dataIndex: 'recipientInfo', width: 140, ellipsis: true,
    },
    {
      title: '标题', dataIndex: 'title', width: 150, ellipsis: true,
    },
    {
      title: '状态', dataIndex: 'sendStatus', width: 90, align: 'center',
      render: (val) => {
        const s = SEND_STATUS_LABELS[val];
        return s ? <Tag color={s.color}>{s.label}</Tag> : <Tag>{val}</Tag>;
      },
    },
    { title: '重试次数', dataIndex: 'retryCount', width: 80, align: 'center' },
    {
      title: '耗时', dataIndex: 'costMs', width: 90, align: 'center',
      render: (val) => val != null ? `${val}ms` : '-',
    },
    { title: '发送时间', dataIndex: 'sendTime', width: 160 },
    {
      title: '操作', key: 'action', width: 140, fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record)} />
          {record.sendStatus === 3 && (
            <Popconfirm title="确认重试?" onConfirm={() => handleRetry(record.id!)} okText="确认" cancelText="取消">
              <Button type="link" size="small" icon={<RedoOutlined />} danger>重试</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={8}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="发送成功" value={stats.success} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col xs={8}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="发送失败" value={stats.failed} valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
        <Col xs={8}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Statistic title="待发送" value={stats.pending} valueStyle={{ color: '#faad14' }} />
          </Card>
        </Col>
      </Row>

      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Form form={searchForm} layout="inline">
          <Form.Item name="eventNo" label="事件编号">
            <Input placeholder="事件编号" style={{ width: 150 }} allowClear />
          </Form.Item>
          <Form.Item name="channelType" label="渠道类型">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              {Object.entries(CHANNEL_TYPE_LABELS).map(([k, v]) => <Option key={k} value={k}>{v}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="sendStatus" label="状态">
            <Select placeholder="全部" style={{ width: 100 }} allowClear>
              {Object.entries(SEND_STATUS_LABELS).map(([k, v]) => <Option key={k} value={Number(k)}>{v.label}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="timeRange" label="时间">
            <RangePicker showTime />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={() => { setCurrent(1); loadData(); }}>搜索</Button>
              <Button icon={<ReloadOutlined />} onClick={() => { searchForm.resetFields(); setCurrent(1); loadData(); }}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card style={{ borderRadius: 8 }} title="推送日志">
        <Table
          columns={columns} dataSource={data} rowKey="id" loading={loading}
          pagination={{
            current, pageSize, total, showSizeChanger: true, showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (c, s) => { setCurrent(c); setPageSize(s); },
          }}
          scroll={{ x: 1400 }}
        />
      </Card>

      <Drawer
        title="推送日志详情" width={640} open={detailDrawer}
        onClose={() => setDetailDrawer(false)}
      >
        {currentLog && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="日志编号">{currentLog.logNo}</Descriptions.Item>
            <Descriptions.Item label="事件编号">{currentLog.eventNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="渠道类型">{CHANNEL_TYPE_LABELS[currentLog.channelType || ''] || currentLog.channelType}</Descriptions.Item>
            <Descriptions.Item label="接收人">{currentLog.recipientInfo || '-'}</Descriptions.Item>
            <Descriptions.Item label="标题">{currentLog.title || '-'}</Descriptions.Item>
            <Descriptions.Item label="内容">
              <div style={{ whiteSpace: 'pre-wrap', maxHeight: 200, overflow: 'auto' }}>{currentLog.content || '-'}</div>
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              {(() => {
                const s = SEND_STATUS_LABELS[currentLog.sendStatus];
                return s ? <Tag color={s.color}>{s.label}</Tag> : '-';
              })()}
            </Descriptions.Item>
            <Descriptions.Item label="重试次数">{currentLog.retryCount || 0} / {currentLog.maxRetry || 3}</Descriptions.Item>
            <Descriptions.Item label="下次重试">{currentLog.nextRetryTime || '-'}</Descriptions.Item>
            <Descriptions.Item label="耗时">{currentLog.costMs != null ? `${currentLog.costMs}ms` : '-'}</Descriptions.Item>
            <Descriptions.Item label="发送时间">{currentLog.sendTime || '-'}</Descriptions.Item>
            <Descriptions.Item label="成功时间">{currentLog.successTime || '-'}</Descriptions.Item>
            {currentLog.errorMessage && (
              <Descriptions.Item label="错误信息">
                <div style={{ color: '#ff4d4f', whiteSpace: 'pre-wrap' }}>{currentLog.errorMessage}</div>
              </Descriptions.Item>
            )}
            {currentLog.responseBody && (
              <Descriptions.Item label="响应内容">
                <div style={{ whiteSpace: 'pre-wrap', maxHeight: 200, overflow: 'auto', fontSize: 12, color: '#8c8c8c' }}>{currentLog.responseBody}</div>
              </Descriptions.Item>
            )}
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default NotifyLogs;
