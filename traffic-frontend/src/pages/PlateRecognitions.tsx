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
  Image,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SearchOutlined, ReloadOutlined, EyeOutlined, CarOutlined } from '@ant-design/icons';
import { plateRecognitionApi } from '@/services/api';
import {
  type PlateRecognition,
  type PlateRecognitionQuery,
  SCENE_TYPE_COLORS,
} from '@/types';

const { RangePicker } = DatePicker;
const { Option } = Select;

const PlateRecognitions: React.FC = () => {
  const [data, setData] = useState<PlateRecognition[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const values = searchForm.getFieldsValue();
      const params: PlateRecognitionQuery & Record<string, any> = {
        ...values,
        current,
        size: pageSize,
      };
      if (values.timeRange && values.timeRange.length === 2) {
        params.startTime = values.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
        params.endTime = values.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const res: any = await plateRecognitionApi.page(params);
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

  const handleSearch = () => {
    setCurrent(1);
    loadData();
  };

  const handleReset = () => {
    searchForm.resetFields();
    setCurrent(1);
    loadData();
  };

  const columns: ColumnsType<PlateRecognition> = [
    {
      title: '识别编号',
      dataIndex: 'recognizeNo',
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
      title: '车牌号',
      dataIndex: 'plateNumber',
      width: 130,
      render: (v) => (v ? <Tag color="blue" style={{ fontSize: 13, fontWeight: 600 }}>{v}</Tag> : '-'),
    },
    { title: '车牌颜色', dataIndex: 'plateColor', width: 100, render: (v) => v || '-' },
    { title: '车辆类型', dataIndex: 'vehicleType', width: 100, render: (v) => v || '-' },
    { title: '车身颜色', dataIndex: 'vehicleColor', width: 100, render: (v) => v || '-' },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 100,
      align: 'center',
      render: (v) => (v != null ? `${(v * 100).toFixed(1)}%` : '-'),
    },
    {
      title: '场景',
      dataIndex: 'sceneType',
      width: 110,
      render: (v) =>
        v ? <Tag color={SCENE_TYPE_COLORS[v] || 'default'}>{v}</Tag> : '-',
    },
    { title: '摄像头', dataIndex: 'cameraName', width: 140, render: (v) => v || '-' },
    {
      title: '识别时间',
      dataIndex: 'recognizeTime',
      width: 170,
      render: (v) => v || '-',
    },
    {
      title: '车牌图',
      dataIndex: 'plateImageUrl',
      width: 120,
      align: 'center',
      render: (v) =>
        v ? <Image src={v} width={96} height={34} style={{ objectFit: 'cover', borderRadius: 4 }} /> : '-',
    },
    {
      title: '操作',
      width: 100,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.fullImageUrl && (
            <Tooltip title="查看大图">
              <Image src={r.fullImageUrl} width={0} height={0} style={{ display: 'none' }} />
              <Button type="link" size="small" icon={<EyeOutlined />} />
            </Tooltip>
          )}
          <Button type="link" size="small" icon={<CarOutlined />}>
            详情
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Form layout="inline" form={searchForm} onFinish={handleSearch}>
          <Form.Item name="plateNumber" label="车牌号">
            <Input placeholder="请输入车牌号" allowClear style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="eventNo" label="事件编号">
            <Input placeholder="请输入事件编号" allowClear style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="vehicleType" label="车辆类型">
            <Select placeholder="全部" allowClear style={{ width: 140 }}>
              <Option value="car">轿车</Option>
              <Option value="truck">卡车</Option>
              <Option value="bus">公交车</Option>
              <Option value="motorcycle">摩托车</Option>
            </Select>
          </Form.Item>
          <Form.Item name="sceneType" label="场景">
            <Select placeholder="全部" allowClear style={{ width: 140 }}>
              <Option value="normal">正常</Option>
              <Option value="night">夜间</Option>
              <Option value="backlight">逆光</Option>
              <Option value="night_backlight">夜间逆光</Option>
            </Select>
          </Form.Item>
          <Form.Item name="timeRange" label="识别时间">
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
          <span style={{ fontSize: 15, fontWeight: 600 }}>车牌识别记录 ({total})</span>
          <Button icon={<ReloadOutlined />} onClick={loadData}>
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
          scroll={{ x: 1500 }}
        />
      </Card>
    </div>
  );
};

export default PlateRecognitions;
