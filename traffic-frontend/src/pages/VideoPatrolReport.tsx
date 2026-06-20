import React, { useEffect, useState, useMemo } from 'react';
import {
  Row,
  Col,
  Card,
  Statistic,
  Table,
  DatePicker,
  Radio,
  Button,
  Space,
  Spin,
  Alert,
  Empty,
  message,
  Tag,
  Select,
  Input,
  Tooltip,
  Progress,
} from 'antd';
import {
  FileTextOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  ExclamationCircleOutlined,
  DownloadOutlined,
  ReloadOutlined,
  BarChartOutlined,
  SafetyOutlined,
  ScanOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Column } from '@ant-design/charts';
import axios from 'axios';
import dayjs, { Dayjs } from 'dayjs';
import {
  VIDEO_HEALTH_LEVEL_LABELS,
  VIDEO_HEALTH_LEVEL_COLORS,
  ABNORMAL_TYPE_LABELS,
  type Camera,
} from '@/types';

const { RangePicker } = DatePicker;
const { Option } = Select;

interface DailyStat {
  date: string;
  avgOverallScore: number;
  avgHealthScore: number;
  totalDetectionCount: number;
  totalAbnormalCount: number;
  normalRate: number;
  totalAlertCount: number;
  abnormalBreakdown: {
    blackScreen: number;
    freeze: number;
    occlusion: number;
    blur: number;
    lowBrightness: number;
    lowContrast: number;
  };
  healthLevelBreakdown: {
    healthy: number;
    subhealthy: number;
    abnormal: number;
    critical: number;
    faulty: number;
  };
}

interface CameraReportItem {
  cameraId: number;
  cameraName: string;
  cameraCode: string;
  roadName?: string;
  location?: string;
  manufacturer?: string;
  reportStatus: 'OK' | 'NO_DATA';
  latestHealthScore?: number;
  latestHealthLevel?: number;
  latestHealthLevelLabel?: string;
  periodAvgHealthScore?: number;
  totalDetectionCount?: number;
  totalAbnormalCount?: number;
  abnormalRate?: number;
  totalAlertCount?: number;
  maintenanceStatus?: number;
  recommendation?: string;
}

interface PatrolReportData {
  startDate: string;
  endDate: string;
  periodType: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  totalCameras: number;
  dailyStats: DailyStat[];
  perCameraReport: CameraReportItem[];
}

interface PeriodOption {
  value: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  label: string;
}

const PERIOD_OPTIONS: PeriodOption[] = [
  { value: 'DAILY', label: '日' },
  { value: 'WEEKLY', label: '周' },
  { value: 'MONTHLY', label: '月' },
];

const VideoPatrolReport: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<PatrolReportData | null>(null);
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(7, 'day'),
    dayjs(),
  ]);
  const [periodType, setPeriodType] = useState<'DAILY' | 'WEEKLY' | 'MONTHLY'>('DAILY');
  const [keyword, setKeyword] = useState('');
  const [healthLevelFilter, setHealthLevelFilter] = useState<number | null>(null);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });
  const [exporting, setExporting] = useState(false);
  const [cameras, setCameras] = useState<Camera[]>([]);

  const loadCameras = async () => {
    try {
      const token = localStorage.getItem('token');
      const response = await axios.get('/api/cameras/list', {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        timeout: 15000,
      });
      if (response.data.code === 200) {
        setCameras(response.data.data || []);
      }
    } catch (err) {
      console.warn('加载摄像头列表失败:', err);
    }
  };

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const token = localStorage.getItem('token');
      const response = await axios.get('/api/video-quality/patrol-report', {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        timeout: 30000,
        params: {
          startDate: dateRange[0].format('YYYY-MM-DD'),
          endDate: dateRange[1].format('YYYY-MM-DD'),
          periodType,
        },
      });
      const result = response.data;
      if (result.code === 200) {
        setData(result.data);
        setPagination((prev) => ({
          ...prev,
          current: 1,
          total: result.data?.perCameraReport?.length || 0,
        }));
      } else {
        setError(result.message || '获取数据失败');
        message.error(result.message || '获取数据失败');
      }
    } catch (err: any) {
      setError(err.message || '网络错误');
      message.error(err.message || '网络错误');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCameras();
  }, []);

  useEffect(() => {
    loadData();
  }, [dateRange, periodType]);

  const handleDateChange = (dates: any) => {
    if (dates && dates.length === 2) {
      setDateRange(dates);
    }
  };

  const handlePeriodChange = (e: any) => {
    setPeriodType(e.target.value);
  };

  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    setKeyword('');
    setHealthLevelFilter(null);
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const filteredCameraReport = useMemo(() => {
    if (!data?.perCameraReport) return [];
    return data.perCameraReport.filter((item) => {
      const matchKeyword =
        !keyword ||
        item.cameraName?.toLowerCase().includes(keyword.toLowerCase()) ||
        item.cameraCode?.toLowerCase().includes(keyword.toLowerCase()) ||
        item.roadName?.toLowerCase().includes(keyword.toLowerCase());
      const matchLevel =
        healthLevelFilter === null || item.latestHealthLevel === healthLevelFilter;
      return matchKeyword && matchLevel;
    });
  }, [data?.perCameraReport, keyword, healthLevelFilter]);

  const paginatedData = useMemo(() => {
    const start = (pagination.current - 1) * pagination.pageSize;
    const end = start + pagination.pageSize;
    return filteredCameraReport.slice(start, end);
  }, [filteredCameraReport, pagination.current, pagination.pageSize]);

  const summaryStats = useMemo(() => {
    if (!data) return null;
    const report = data.perCameraReport || [];
    const totalDetection = report.reduce(
      (sum, item) => sum + (item.totalDetectionCount || 0),
      0
    );
    const totalAbnormal = report.reduce(
      (sum, item) => sum + (item.totalAbnormalCount || 0),
      0
    );
    const totalAlerts = report.reduce(
      (sum, item) => sum + (item.totalAlertCount || 0),
      0
    );
    const healthScoreList = report
      .filter((item) => item.periodAvgHealthScore !== undefined)
      .map((item) => item.periodAvgHealthScore!);
    const avgHealthScore =
      healthScoreList.length > 0
        ? healthScoreList.reduce((a, b) => a + b, 0) / healthScoreList.length
        : 0;
    const healthyCount = report.filter(
      (item) => item.latestHealthLevel && item.latestHealthLevel <= 2
    ).length;
    const needMaintenance = report.filter(
      (item) => item.maintenanceStatus && item.maintenanceStatus >= 1
    ).length;

    return {
      totalCameras: data.totalCameras,
      totalDetection,
      totalAbnormal,
      totalAlerts,
      avgHealthScore,
      healthyCount,
      needMaintenance,
      abnormalRate: totalDetection > 0 ? (totalAbnormal / totalDetection) * 100 : 0,
    };
  }, [data]);

  const healthComparisonData = useMemo(() => {
    if (!data?.perCameraReport) return [];
    return data.perCameraReport
      .filter((item) => item.reportStatus === 'OK' && item.periodAvgHealthScore !== undefined)
      .sort((a, b) => (b.periodAvgHealthScore || 0) - (a.periodAvgHealthScore || 0))
      .slice(0, 15)
      .map((item) => ({
        camera: item.cameraName || `摄像头#${item.cameraId}`,
        healthScore: item.periodAvgHealthScore || 0,
        latestScore: item.latestHealthScore || 0,
        color: item.latestHealthLevel
          ? VIDEO_HEALTH_LEVEL_COLORS[item.latestHealthLevel]
          : '#1890ff',
      }));
  }, [data?.perCameraReport]);

  const handleTableChange = (page: number, pageSize: number) => {
    setPagination((prev) => ({
      ...prev,
      current: page,
      pageSize,
    }));
  };

  const exportToCSV = () => {
    if (filteredCameraReport.length === 0) {
      message.warning('没有可导出的数据');
      return;
    }

    setExporting(true);
    try {
      const headers = [
        '摄像头ID',
        '摄像头名称',
        '摄像头编号',
        '路段',
        '位置',
        '当前健康分',
        '当前健康状态',
        '周期平均健康分',
        '检测次数',
        '异常次数',
        '异常率(%)',
        '告警次数',
        '维护状态',
        '建议',
      ];

      const rows = filteredCameraReport.map((item) => [
        item.cameraId,
        item.cameraName,
        item.cameraCode,
        item.roadName || '',
        item.location || '',
        item.latestHealthScore?.toFixed(2) || '',
        item.latestHealthLevelLabel || (item.latestHealthLevel ? VIDEO_HEALTH_LEVEL_LABELS[item.latestHealthLevel] : ''),
        item.periodAvgHealthScore?.toFixed(2) || '',
        item.totalDetectionCount || 0,
        item.totalAbnormalCount || 0,
        item.abnormalRate?.toFixed(2) || '0.00',
        item.totalAlertCount || 0,
        item.maintenanceStatus === 2
          ? '需立即维护'
          : item.maintenanceStatus === 1
          ? '建议维护'
          : '正常',
        item.recommendation || '',
      ]);

      const csvContent =
        '\uFEFF' +
        [headers.join(','), ...rows.map((row) => row.map((cell) => `"${cell}"`).join(','))].join(
          '\n'
        );

      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      const url = URL.createObjectURL(blob);
      link.setAttribute('href', url);
      link.setAttribute(
        'download',
        `设备巡检报表_${dateRange[0].format('YYYYMMDD')}-${dateRange[1].format('YYYYMMDD')}.csv`
      );
      link.style.visibility = 'hidden';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      message.success('导出成功');
    } catch (err: any) {
      message.error('导出失败: ' + (err.message || '未知错误'));
    } finally {
      setExporting(false);
    }
  };

  const tableColumns = [
    {
      title: '摄像头名称',
      dataIndex: 'cameraName',
      key: 'cameraName',
      width: 160,
      fixed: 'left' as const,
      ellipsis: true,
      render: (text: string, record: CameraReportItem) => (
        <Tooltip title={text}>
          <span style={{ fontWeight: 500 }}>
            {text || `摄像头#${record.cameraId}`}
          </span>
        </Tooltip>
      ),
    },
    {
      title: '摄像头编号',
      dataIndex: 'cameraCode',
      key: 'cameraCode',
      width: 140,
      ellipsis: true,
    },
    {
      title: '路段',
      dataIndex: 'roadName',
      key: 'roadName',
      width: 140,
      ellipsis: true,
      render: (text: string) => text || <span style={{ color: '#999' }}>-</span>,
    },
    {
      title: '当前健康分',
      dataIndex: 'latestHealthScore',
      key: 'latestHealthScore',
      width: 200,
      render: (score: number, record: CameraReportItem) => {
        if (score === undefined || score === null) {
          return <span style={{ color: '#999' }}>-</span>;
        }
        const level = record.latestHealthLevel || 3;
        return (
          <Progress
            percent={score}
            size="small"
            strokeColor={VIDEO_HEALTH_LEVEL_COLORS[level]}
            format={(p) => `${p?.toFixed(1)}分`}
          />
        );
      },
      sorter: (a: CameraReportItem, b: CameraReportItem) =>
        (a.latestHealthScore || 0) - (b.latestHealthScore || 0),
    },
    {
      title: '当前状态',
      dataIndex: 'latestHealthLevel',
      key: 'latestHealthLevel',
      width: 100,
      render: (level: number) => {
        if (level === undefined || level === null) {
          return <span style={{ color: '#999' }}>-</span>;
        }
        return (
          <Tag color={VIDEO_HEALTH_LEVEL_COLORS[level]}>
            {VIDEO_HEALTH_LEVEL_LABELS[level] || '未知'}
          </Tag>
        );
      },
      filters: Object.entries(VIDEO_HEALTH_LEVEL_LABELS).map(([value, label]) => ({
        text: label,
        value: Number(value),
      })),
      onFilter: (value: number, record: CameraReportItem) =>
        record.latestHealthLevel === value,
    },
    {
      title: '周期平均',
      dataIndex: 'periodAvgHealthScore',
      key: 'periodAvgHealthScore',
      width: 110,
      render: (score: number) =>
        score !== undefined ? `${score.toFixed(1)}分` : <span style={{ color: '#999' }}>-</span>,
      sorter: (a: CameraReportItem, b: CameraReportItem) =>
        (a.periodAvgHealthScore || 0) - (b.periodAvgHealthScore || 0),
    },
    {
      title: '检测次数',
      dataIndex: 'totalDetectionCount',
      key: 'totalDetectionCount',
      width: 100,
      render: (count: number) => count || 0,
      sorter: (a: CameraReportItem, b: CameraReportItem) =>
        (a.totalDetectionCount || 0) - (b.totalDetectionCount || 0),
    },
    {
      title: '异常次数',
      dataIndex: 'totalAbnormalCount',
      key: 'totalAbnormalCount',
      width: 100,
      render: (count: number, record: CameraReportItem) => {
        const c = count || 0;
        return (
          <span style={{ color: c > 0 ? '#ff4d4f' : '#52c41a', fontWeight: c > 0 ? 500 : 400 }}>
            {c}
          </span>
        );
      },
      sorter: (a: CameraReportItem, b: CameraReportItem) =>
        (a.totalAbnormalCount || 0) - (b.totalAbnormalCount || 0),
    },
    {
      title: '异常率',
      dataIndex: 'abnormalRate',
      key: 'abnormalRate',
      width: 100,
      render: (rate: number) => {
        if (rate === undefined || rate === null) {
          return <span style={{ color: '#999' }}>-</span>;
        }
        return (
          <span
            style={{
              color: rate > 10 ? '#ff4d4f' : rate > 5 ? '#fa8c16' : '#52c41a',
              fontWeight: rate > 5 ? 500 : 400,
            }}
          >
            {rate.toFixed(1)}%
          </span>
        );
      },
      sorter: (a: CameraReportItem, b: CameraReportItem) =>
        (a.abnormalRate || 0) - (b.abnormalRate || 0),
    },
    {
      title: '告警次数',
      dataIndex: 'totalAlertCount',
      key: 'totalAlertCount',
      width: 100,
      render: (count: number) => count || 0,
      sorter: (a: CameraReportItem, b: CameraReportItem) =>
        (a.totalAlertCount || 0) - (b.totalAlertCount || 0),
    },
    {
      title: '维护状态',
      dataIndex: 'maintenanceStatus',
      key: 'maintenanceStatus',
      width: 110,
      render: (status: number) => {
        if (status === 2) {
          return <Tag color="error">需立即维护</Tag>;
        } else if (status === 1) {
          return <Tag color="warning">建议维护</Tag>;
        }
        return <Tag color="success">正常</Tag>;
      },
      filters: [
        { text: '正常', value: 0 },
        { text: '建议维护', value: 1 },
        { text: '需立即维护', value: 2 },
      ],
      onFilter: (value: number, record: CameraReportItem) =>
        (record.maintenanceStatus || 0) === value,
    },
    {
      title: '建议',
      dataIndex: 'recommendation',
      key: 'recommendation',
      width: 200,
      ellipsis: true,
      render: (text: string) =>
        text ? (
          <Tooltip title={text}>
            <span>{text}</span>
          </Tooltip>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: '数据状态',
      dataIndex: 'reportStatus',
      key: 'reportStatus',
      width: 100,
      render: (status: string) =>
        status === 'OK' ? (
          <Tag color="success">正常</Tag>
        ) : (
          <Tag color="default">无数据</Tag>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right' as const,
      render: (_: any, record: CameraReportItem) => (
        <Tooltip title="查看详情">
          <Button type="link" size="small" icon={<FileTextOutlined />}>
            详情
          </Button>
        </Tooltip>
      ),
    },
  ];

  if (loading && !data) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <Spin size="large" tip="加载中..." />
      </div>
    );
  }

  if (error && !data) {
    return (
      <div style={{ padding: 40 }}>
        <Alert
          type="error"
          message="加载失败"
          description={error}
          showIcon
          action={
            <Button size="small" type="primary" onClick={loadData}>
              重试
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <h2 style={{ margin: 0, fontSize: 20 }}>设备巡检报表</h2>
        <Space>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            onClick={exportToCSV}
            loading={exporting}
          >
            导出CSV
          </Button>
          <Button icon={<ReloadOutlined />} onClick={loadData} loading={loading}>
            刷新
          </Button>
        </Space>
      </div>

      <Card style={{ borderRadius: 8, marginBottom: 16 }}>
        <Row gutter={[16, 12]} align="middle">
          <Col xs={24} sm={8} md={6}>
            <div style={{ fontWeight: 500, marginBottom: 8 }}>
              <CalendarOutlined style={{ marginRight: 4 }} /> 日期范围
            </div>
            <RangePicker
              value={dateRange}
              onChange={handleDateChange}
              style={{ width: '100%' }}
              allowClear={false}
            />
          </Col>
          <Col xs={24} sm={6} md={4}>
            <div style={{ fontWeight: 500, marginBottom: 8 }}>
              <BarChartOutlined style={{ marginRight: 4 }} /> 统计周期
            </div>
            <Radio.Group
              value={periodType}
              onChange={handlePeriodChange}
              options={PERIOD_OPTIONS}
              optionType="button"
              buttonStyle="solid"
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={24} sm={10} md={8}>
            <div style={{ fontWeight: 500, marginBottom: 8 }}>
              <SearchOutlined style={{ marginRight: 4 }} /> 搜索筛选
            </div>
            <Space.Compact style={{ width: '100%' }}>
              <Input
                placeholder="搜索摄像头名称/编号/路段"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onPressEnter={handleSearch}
                allowClear
              />
              <Select
                placeholder="健康度"
                value={healthLevelFilter}
                onChange={(value) => setHealthLevelFilter(value)}
                style={{ minWidth: 120 }}
                allowClear
              >
                {Object.entries(VIDEO_HEALTH_LEVEL_LABELS).map(([value, label]) => (
                  <Option key={value} value={Number(value)}>
                    {label}
                  </Option>
                ))}
              </Select>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                搜索
              </Button>
              <Button onClick={handleReset}>重置</Button>
            </Space.Compact>
          </Col>
        </Row>
      </Card>

      {summaryStats?.needMaintenance && summaryStats.needMaintenance > 0 && (
        <Alert
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          style={{ marginBottom: 16, borderRadius: 8 }}
          message={`${summaryStats.needMaintenance} 个摄像头需要维护`}
          description="请查看下方巡检详情表格，及时安排维护工作"
          closable
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%)',
            }}
          >
            <Statistic
              title="巡检摄像头"
              value={summaryStats?.totalCameras || 0}
              prefix={<ScanOutlined style={{ color: '#1890ff' }} />}
              valueStyle={{ color: '#1890ff', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #f6ffed 0%, #b7eb8f 100%)',
            }}
          >
            <Statistic
              title="健康设备"
              value={summaryStats?.healthyCount || 0}
              suffix={`/ ${summaryStats?.totalCameras || 0}`}
              prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #fffbe6 0%, #ffe58f 100%)',
            }}
          >
            <Statistic
              title="检测总次数"
              value={summaryStats?.totalDetection || 0}
              prefix={<FileTextOutlined style={{ color: '#faad14' }} />}
              valueStyle={{ color: '#faad14', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #fff1f0 0%, #ffa39e 100%)',
            }}
          >
            <Statistic
              title="异常总次数"
              value={summaryStats?.totalAbnormal || 0}
              prefix={<ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />}
              valueStyle={{ color: '#ff4d4f', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #f9f0ff 0%, #d3adf7 100%)',
            }}
          >
            <Statistic
              title="平均健康分"
              value={summaryStats?.avgHealthScore || 0}
              precision={1}
              suffix="/ 100"
              prefix={<SafetyOutlined style={{ color: '#722ed1' }} />}
              valueStyle={{ color: '#722ed1', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            style={{
              borderRadius: 8,
              background: 'linear-gradient(135deg, #e6fffb 0%, #87e8de 100%)',
            }}
          >
            <Statistic
              title="异常率"
              value={summaryStats?.abnormalRate || 0}
              precision={1}
              suffix="%"
              prefix={<WarningOutlined style={{ color: '#13c2c2' }} />}
              valueStyle={{
                color:
                  (summaryStats?.abnormalRate || 0) > 10
                    ? '#ff4d4f'
                    : '#13c2c2',
                fontWeight: 700,
              }}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <BarChartOutlined /> 各摄像头健康度对比（Top 15）
          </span>
        }
        style={{ borderRadius: 8, marginBottom: 16 }}
      >
        {healthComparisonData.length > 0 ? (
          <Column
            data={healthComparisonData}
            xField="camera"
            yField={['healthScore', 'latestScore']}
            isGroup
            color={['#1890ff', '#52c41a']}
            columnStyle={{
              radius: [4, 4, 0, 0],
            }}
            label={{
              position: 'middle',
              layout: [{ type: 'interval-adjust-position' }],
            }}
            yAxis={{
              min: 0,
              max: 100,
              label: {
                formatter: '{value}分',
              },
            }}
            legend={{
              position: 'top',
              itemName: {
                formatter: (text: string) => {
                  if (text === 'healthScore') return '周期平均';
                  if (text === 'latestScore') return '最新得分';
                  return text;
                },
              },
            }}
            tooltip={{
              formatter: (datum: any) => {
                const label =
                  datum.type === 'healthScore' ? '周期平均' : '最新得分';
                return {
                  name: label,
                  value: `${datum[datum.type]?.toFixed(1)}分`,
                };
              },
            }}
            height={350}
          />
        ) : (
          <Empty description="暂无对比数据" style={{ padding: 40 }} />
        )}
      </Card>

      <Card
        title={
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <FileTextOutlined /> 巡检详情
          </span>
        }
        extra={
          <span style={{ color: '#999', fontSize: 12 }}>
            共 {filteredCameraReport.length} 条记录
            {keyword && ` · 已筛选 "${keyword}"`}
          </span>
        }
        style={{ borderRadius: 8 }}
      >
        <Table<CameraReportItem>
          rowKey="cameraId"
          dataSource={paginatedData}
          columns={tableColumns}
          loading={loading}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: filteredCameraReport.length,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) =>
              `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
            pageSizeOptions: ['10', '20', '50', '100'],
            onChange: handleTableChange,
          }}
          scroll={{ x: 1400 }}
          locale={{
            emptyText: <Empty description="暂无巡检数据" />,
          }}
          size="middle"
        />
      </Card>
    </div>
  );
};

export default VideoPatrolReport;
