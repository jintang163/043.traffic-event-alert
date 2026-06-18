import React, { useState, useEffect } from 'react';
import {
  Table,
  Card,
  Tag,
  Space,
  Typography,
  Button,
  DatePicker,
  Input,
  Drawer,
  Descriptions,
  Empty,
} from 'antd';
import { SearchOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import { ruleApi } from '@/services/api';
import type { RuleExecutionLog } from '@/types';
import { GATEWAY_TYPE_LABELS } from '@/types';
import dayjs from 'dayjs';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;
const { TextArea } = Input;

const RuleExecutionLogs: React.FC = () => {
  const [logs, setLogs] = useState<RuleExecutionLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [ruleCodeFilter, setRuleCodeFilter] = useState('');
  const [dateRange, setDateRange] = useState<any>(null);

  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false);
  const [currentLog, setCurrentLog] = useState<RuleExecutionLog | null>(null);

  useEffect(() => {
    loadLogs();
  }, []);

  const loadLogs = async () => {
    setLoading(true);
    try {
      const params: any = {};
      if (ruleCodeFilter) params.ruleCode = ruleCodeFilter;
      if (dateRange && dateRange.length === 2) {
        params.startTime = dateRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = dateRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const res = await ruleApi.executionLogs(params);
      setLogs(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  const handleViewDetail = (record: RuleExecutionLog) => {
    setCurrentLog(record);
    setDetailDrawerOpen(true);
  };

  const tryParseJson = (str?: string) => {
    if (!str) return null;
    try {
      return JSON.parse(str);
    } catch {
      return str;
    }
  };

  const formatJsonPretty = (obj: any) => {
    if (obj == null) return '-';
    if (typeof obj === 'string') return obj;
    try {
      return JSON.stringify(obj, null, 2);
    } catch {
      return String(obj);
    }
  };

  const columns = [
    {
      title: '执行ID',
      dataIndex: 'executionId',
      key: 'executionId',
      width: 220,
      render: (v: string) => (
        <Text copyable code style={{ fontSize: 12 }}>
          {v}
        </Text>
      ),
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
      sorter: (a: RuleExecutionLog, b: RuleExecutionLog) =>
        new Date(a.createTime).getTime() - new Date(b.createTime).getTime(),
    },
    { title: '规则编码', dataIndex: 'ruleCode', key: 'ruleCode', width: 180 },
    { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 180 },
    {
      title: '网关类型',
      dataIndex: 'gatewayType',
      key: 'gatewayType',
      width: 100,
      render: (v: number) => <Tag color="blue">{GATEWAY_TYPE_LABELS[v] || '未知'}</Tag>,
    },
    {
      title: '命中分支',
      dataIndex: 'matchedBranches',
      key: 'matchedBranches',
      width: 240,
      render: (v: string) => {
        if (!v) return <Tag color="default">未命中</Tag>;
        return v.split(',').map((code, i) => (
          <Tag key={i} color="green" style={{ marginBottom: 4 }}>
            {code}
          </Tag>
        ));
      },
    },
    {
      title: '状态',
      dataIndex: 'success',
      key: 'success',
      width: 90,
      render: (v: number) => (
        v === 1
          ? <Tag color="success">成功</Tag>
          : <Tag color="error">失败</Tag>
      ),
    },
    {
      title: '耗时',
      dataIndex: 'executionTime',
      key: 'executionTime',
      width: 90,
      render: (v?: number) => v != null ? `${v} ms` : '-',
      sorter: (a: RuleExecutionLog, b: RuleExecutionLog) => (a.executionTime || 0) - (b.executionTime || 0),
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      ellipsis: true,
      render: (v?: string) => (
        v ? <Text type="danger">{v}</Text> : <Text type="secondary">-</Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: any, record: RuleExecutionLog) => (
        <Button size="small" icon={<EyeOutlined />} onClick={() => handleViewDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const inputCtx = tryParseJson(currentLog?.inputContext);
  const execResult = tryParseJson(currentLog?.executionResult);

  return (
    <div style={{ padding: 16 }}>
      <Card
        title={
          <Space>
            <span>规则执行日志</span>
            <Tag color="purple">{logs.length} 条记录</Tag>
          </Space>
        }
        extra={
          <Space>
            <Input
              allowClear
              placeholder="按规则编码筛选"
              prefix={<SearchOutlined />}
              style={{ width: 220 }}
              value={ruleCodeFilter}
              onChange={e => setRuleCodeFilter(e.target.value)}
              onPressEnter={loadLogs}
            />
            <RangePicker
              showTime
              format="YYYY-MM-DD HH:mm"
              value={dateRange}
              onChange={setDateRange}
              style={{ width: 360 }}
            />
            <Button icon={<SearchOutlined />} onClick={loadLogs} type="primary">
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => {
              setRuleCodeFilter('');
              setDateRange(null);
              setTimeout(loadLogs, 50);
            }}>
              重置
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={logs}
          loading={loading}
          pagination={{ pageSize: 15, showSizeChanger: true }}
          locale={{ emptyText: <Empty description="暂无执行日志" /> }}
        />
      </Card>

      <Drawer
        title={
          <Space>
            <span>执行详情</span>
            {currentLog?.success === 1 ? <Tag color="success">成功</Tag> : <Tag color="error">失败</Tag>}
          </Space>
        }
        width={720}
        open={detailDrawerOpen}
        onClose={() => setDetailDrawerOpen(false)}
      >
        {currentLog ? (
          <>
            <Descriptions column={2} bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="执行ID" span={2}>
                <Text copyable code>{currentLog.executionId}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="规则编码">{currentLog.ruleCode || '-'}</Descriptions.Item>
              <Descriptions.Item label="规则名称">{currentLog.ruleName || '-'}</Descriptions.Item>
              <Descriptions.Item label="网关类型">
                <Tag color="blue">{GATEWAY_TYPE_LABELS[currentLog.gatewayType || 1]}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="命中分支">
                {currentLog.matchedBranches
                  ? currentLog.matchedBranches.split(',').map((c, i) => (
                      <Tag key={i} color="green">{c}</Tag>
                    ))
                  : <Tag>无</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="执行时间">
                {dayjs(currentLog.createTime).format('YYYY-MM-DD HH:mm:ss.SSS')}
              </Descriptions.Item>
              <Descriptions.Item label="耗时">{currentLog.executionTime} ms</Descriptions.Item>
            </Descriptions>

            <Card
              size="small"
              title="输入上下文 (context)"
              style={{ marginBottom: 12 }}
              type="inner"
            >
              <TextArea
                value={formatJsonPretty(inputCtx)}
                readOnly
                rows={10}
                style={{ fontFamily: 'Consolas, monospace', fontSize: 12 }}
              />
            </Card>

            <Card
              size="small"
              title="执行结果 (matchedBranches)"
              style={{ marginBottom: 12 }}
              type="inner"
            >
              <TextArea
                value={formatJsonPretty(execResult)}
                readOnly
                rows={10}
                style={{ fontFamily: 'Consolas, monospace', fontSize: 12 }}
              />
            </Card>

            {currentLog.errorMessage && (
              <Card size="small" title="错误信息" type="inner" style={{ borderLeft: '4px solid #ff4d4f' }}>
                <Text type="danger">{currentLog.errorMessage}</Text>
              </Card>
            )}
          </>
        ) : null}
      </Drawer>
    </div>
  );
};

export default RuleExecutionLogs;
