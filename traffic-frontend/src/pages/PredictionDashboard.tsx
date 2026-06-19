import React, { useEffect, useState } from 'react';
import {
  Row,
  Col,
  Card,
  Statistic,
  Button,
  Space,
  Table,
  Tag,
  Tooltip,
  Modal,
  Descriptions,
  List,
  Progress,
} from 'antd';
import {
  ReloadOutlined,
  ThunderboltOutlined,
  WarningOutlined,
  SafetyOutlined,
  DashboardOutlined,
  LineChartOutlined,
  ClockCircleOutlined,
  CloudOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { predictionApi } from '@/services/api';
import RiskHeatmap from '@/components/RiskHeatmap';
import {
  type EventPrediction,
  type PredictionSummary,
  RISK_LEVEL_LABELS,
  RISK_LEVEL_COLORS,
  PREDICTION_EVENT_TYPE_LABELS,
  WEATHER_TYPE_LABELS,
  WEATHER_TYPE_COLORS,
} from '@/types';

const PredictionDashboard: React.FC = () => {
  const [predictions, setPredictions] = useState<EventPrediction[]>([]);
  const [summary, setSummary] = useState<PredictionSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [selectedPrediction, setSelectedPrediction] = useState<EventPrediction | null>(null);
  const [detailModal, setDetailModal] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<string>('');

  const loadData = async () => {
    setLoading(true);
    try {
      const [predictionRes, summaryRes] = await Promise.all([
        predictionApi.nextHour(),
        predictionApi.summary(),
      ]);
      if (predictionRes.code === 200) {
        setPredictions(predictionRes.data as EventPrediction[]);
      }
      if (summaryRes.code === 200) {
        setSummary(summaryRes.data as PredictionSummary);
      }
      setLastUpdate(new Date().toLocaleString());
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      const res = await predictionApi.generate(1);
      if (res.code === 200) {
        setPredictions(res.data as EventPrediction[]);
        await loadData();
      }
    } finally {
      setGenerating(false);
    }
  };

  const showDetail = (prediction: EventPrediction) => {
    setSelectedPrediction(prediction);
    setDetailModal(true);
  };

  useEffect(() => {
    loadData();

    const interval = setInterval(() => {
      loadData();
    }, 5 * 60 * 1000);

    return () => clearInterval(interval);
  }, []);

  const columns = [
    {
      title: '风险等级',
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      width: 100,
      render: (level: number, record: EventPrediction) => (
        <Tag color={RISK_LEVEL_COLORS[level]} style={{ width: '100%', textAlign: 'center' }}>
          {RISK_LEVEL_LABELS[level]}
        </Tag>
      ),
    },
    {
      title: '风险评分',
      dataIndex: 'riskScore',
      key: 'riskScore',
      width: 120,
      render: (score: number, record: EventPrediction) => (
        <div>
          <Progress
            percent={Math.round(score)}
            strokeColor={RISK_LEVEL_COLORS[record.riskLevel]}
            size="small"
            showInfo={true}
          />
        </div>
      ),
    },
    {
      title: '位置',
      dataIndex: 'cameraName',
      key: 'cameraName',
      render: (text: string, record: EventPrediction) => (
        <Tooltip title={record.description}>
          <span style={{ cursor: 'pointer', color: '#1890ff' }} onClick={() => showDetail(record)}>
            {text || record.roadName || '未知位置'}
          </span>
        </Tooltip>
      ),
    },
    {
      title: '预测事件',
      dataIndex: 'eventTypeLabel',
      key: 'eventTypeLabel',
      width: 120,
      render: (label: string, record: EventPrediction) => (
        <Tag color="blue">{label || PREDICTION_EVENT_TYPE_LABELS[record.eventType!] || '交通事件'}</Tag>
      ),
    },
    {
      title: '发生概率',
      dataIndex: 'probability',
      key: 'probability',
      width: 100,
      render: (prob: number) => prob ? `${(prob * 100).toFixed(1)}%` : '-',
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      key: 'confidence',
      width: 100,
      render: (conf: number) => conf ? `${(conf * 100).toFixed(0)}%` : '-',
    },
    {
      title: '历史事件数',
      dataIndex: 'historicalEventCount',
      key: 'historicalEventCount',
      width: 100,
      align: 'center' as const,
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: any, record: EventPrediction) => (
        <Button type="link" size="small" onClick={() => showDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const sortedPredictions = [...predictions].sort((a, b) => b.riskScore - a.riskScore);
  const topRisks = sortedPredictions.slice(0, 5);

  const getStatColor = (level: number) => {
    if (level >= 3) return '#ff4d4f';
    if (level >= 2) return '#faad14';
    return '#52c41a';
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ margin: 0, fontSize: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
          <ThunderboltOutlined style={{ color: '#722ed1' }} />
          交通事件预测预警
        </h2>
        <Space>
          <span style={{ color: '#999', fontSize: 12 }}>
            <ClockCircleOutlined /> 最后更新: {lastUpdate || '-'}
          </span>
          <Button
            icon={<PlayCircleOutlined />}
            type="primary"
            onClick={handleGenerate}
            loading={generating}
          >
            生成预测
          </Button>
          <Button
            icon={<ReloadOutlined />}
            onClick={loadData}
            loading={loading}
          >
            刷新
          </Button>
        </Space>
      </div>

      {summary && (
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} sm={12} md={6}>
            <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #f9f0ff 0%, #d3adf7 100%)' }}>
              <Statistic
                title="预测监测点"
                value={summary.totalPoints}
                prefix={<DashboardOutlined style={{ color: '#722ed1' }} />}
                valueStyle={{ color: '#722ed1', fontWeight: 700 }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #fff1f0 0%, #ffa39e 100%)' }}>
              <Statistic
                title="高/极高风险"
                value={summary.level3Count + summary.level4Count}
                prefix={<WarningOutlined style={{ color: '#ff4d4f' }} />}
                valueStyle={{ color: '#ff4d4f', fontWeight: 700 }}
                suffix={`/ ${summary.totalPoints}`}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #fff7e6 0%, #ffd591 100%)' }}>
              <Statistic
                title="平均风险评分"
                value={summary.avgScore}
                precision={1}
                prefix={<LineChartOutlined style={{ color: '#faad14' }} />}
                valueStyle={{ color: getStatColor(summary.avgScore >= 50 ? 3 : summary.avgScore >= 25 ? 2 : 1), fontWeight: 700 }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card style={{ borderRadius: 8, background: 'linear-gradient(135deg, #f6ffed 0%, #b7eb8f 100%)' }}>
              <Statistic
                title="最高风险评分"
                value={summary.maxScore}
                precision={1}
                prefix={<SafetyOutlined style={{ color: summary.maxScore >= 50 ? '#ff4d4f' : '#52c41a' }} />}
                valueStyle={{ color: getStatColor(summary.maxScore >= 50 ? 3 : summary.maxScore >= 25 ? 2 : 1), fontWeight: 700 }}
              />
            </Card>
          </Col>
        </Row>
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={16}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <ThunderboltOutlined />
                风险热力图
              </span>
            }
            extra={
              <Space size="small">
                {Object.entries(RISK_LEVEL_LABELS).map(([level, label]) => (
                  <Tag key={level} color={RISK_LEVEL_COLORS[Number(level)]}>
                    {label}
                  </Tag>
                ))}
              </Space>
            }
            style={{ borderRadius: 8 }}
          >
            <RiskHeatmap
              predictions={predictions}
              loading={loading}
              height={520}
              onPointClick={showDetail}
            />
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card
            title={
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <WarningOutlined />
                高风险预警 TOP 5
              </span>
            }
            style={{ borderRadius: 8, height: '100%' }}
            bodyStyle={{ padding: 0 }}
          >
            <List
              dataSource={topRisks}
              locale={{ emptyText: '暂无高风险预警' }}
              renderItem={(item, index) => (
                <List.Item
                  style={{ padding: '12px 16px', cursor: 'pointer' }}
                  onClick={() => showDetail(item)}
                >
                  <List.Item.Meta
                    avatar={
                      <div
                        style={{
                          width: 36,
                          height: 36,
                          borderRadius: '50%',
                          background: RISK_LEVEL_COLORS[item.riskLevel],
                          color: 'white',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: 700,
                          fontSize: 14,
                        }}
                      >
                        {index + 1}
                      </div>
                    }
                    title={
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontWeight: 500 }}>
                          {item.cameraName || item.roadName || '未知位置'}
                        </span>
                        <Tag color={RISK_LEVEL_COLORS[item.riskLevel]}>
                          {item.riskLevelLabel}
                        </Tag>
                      </div>
                    }
                    description={
                      <div style={{ fontSize: 12, color: '#666' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                          <span>
                            {PREDICTION_EVENT_TYPE_LABELS[item.eventType!] || '交通事件'} · 概率 {(item.probability! * 100).toFixed(0)}%
                          </span>
                          <span style={{ color: RISK_LEVEL_COLORS[item.riskLevel], fontWeight: 600 }}>
                            {item.riskScore.toFixed(1)} 分
                          </span>
                        </div>
                        <Progress
                          percent={Math.round(item.riskScore)}
                          strokeColor={RISK_LEVEL_COLORS[item.riskLevel]}
                          size="small"
                          showInfo={false}
                          style={{ marginTop: 4 }}
                        />
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <LineChartOutlined />
            预测详情列表
          </span>
        }
        style={{ borderRadius: 8, marginTop: 16 }}
      >
        <Table
          columns={columns}
          dataSource={sortedPredictions}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条预测记录`,
          }}
        />
      </Card>

      <Modal
        title="预测详情"
        open={detailModal}
        onCancel={() => setDetailModal(false)}
        footer={null}
        width={700}
      >
        {selectedPrediction && (
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="预测编号" span={2}>
              {selectedPrediction.predictionNo}
            </Descriptions.Item>
            <Descriptions.Item label="风险等级">
              <Tag color={RISK_LEVEL_COLORS[selectedPrediction.riskLevel]}>
                {selectedPrediction.riskLevelLabel}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="风险评分">
              <span style={{ color: RISK_LEVEL_COLORS[selectedPrediction.riskLevel], fontWeight: 600, fontSize: 16 }}>
                {selectedPrediction.riskScore.toFixed(1)}
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="位置" span={2}>
              {selectedPrediction.cameraName || selectedPrediction.roadName}
            </Descriptions.Item>
            <Descriptions.Item label="预测事件">
              {PREDICTION_EVENT_TYPE_LABELS[selectedPrediction.eventType!] || '交通事件'}
            </Descriptions.Item>
            <Descriptions.Item label="发生概率">
              {(selectedPrediction.probability! * 100).toFixed(1)}%
            </Descriptions.Item>
            <Descriptions.Item label="预测置信度">
              {(selectedPrediction.confidence! * 100).toFixed(0)}%
            </Descriptions.Item>
            <Descriptions.Item label="同条件历史事件数">
              {selectedPrediction.historicalEventCount || 0} 起
            </Descriptions.Item>
            <Descriptions.Item label="预测时间" span={2}>
              {selectedPrediction.predictionTime ? new Date(selectedPrediction.predictionTime).toLocaleString() : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="预测窗口" span={2}>
              {selectedPrediction.targetStartTime ? new Date(selectedPrediction.targetStartTime).toLocaleString() : '-'}
              {' → '}
              {selectedPrediction.targetEndTime ? new Date(selectedPrediction.targetEndTime).toLocaleString() : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="天气影响因子">
              {selectedPrediction.weatherFactor?.toFixed(2) || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="时段影响因子">
              {selectedPrediction.timeFactor?.toFixed(2) || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="节假日影响因子">
              {selectedPrediction.holidayFactor?.toFixed(2) || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="坐标">
              {selectedPrediction.longitude.toFixed(6)}, {selectedPrediction.latitude.toFixed(6)}
            </Descriptions.Item>
            <Descriptions.Item label="预测说明" span={2}>
              {selectedPrediction.description}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default PredictionDashboard;
