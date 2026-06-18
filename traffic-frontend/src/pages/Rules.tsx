import React, { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  Space,
  Card,
  Tag,
  Typography,
  message,
  Drawer,
  InputNumber,
  Popconfirm,
  Divider,
  Row,
  Col,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  BranchesOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { ruleApi } from '@/services/api';
import {
  RuleSet,
  RuleBranch,
  RuleExecuteResult,
  GATEWAY_TYPE_OPTIONS,
  GATEWAY_TYPE_LABELS,
  ACTION_TYPE_OPTIONS,
} from '@/types';
import ExpressionBuilder from '@/components/ExpressionBuilder';

const { Title, Text, Paragraph } = Typography;
const { Option } = Select;

const Rules: React.FC = () => {
  const [ruleSets, setRuleSets] = useState<RuleSet[]>([]);
  const [loading, setLoading] = useState(false);
  const [setModalOpen, setSetModalOpen] = useState(false);
  const [editingSet, setEditingSet] = useState<RuleSet | null>(null);
  const [setForm] = Form.useForm();

  const [branches, setBranches] = useState<RuleBranch[]>([]);
  const [branchDrawerOpen, setBranchDrawerOpen] = useState(false);
  const [currentSet, setCurrentSet] = useState<RuleSet | null>(null);
  const [branchModalOpen, setBranchModalOpen] = useState(false);
  const [editingBranch, setEditingBranch] = useState<RuleBranch | null>(null);
  const [branchForm] = Form.useForm();

  const [simulateModalOpen, setSimulateModalOpen] = useState(false);
  const [simulateForm] = Form.useForm();
  const [simulateResult, setSimulateResult] = useState<RuleExecuteResult | null>(null);

  useEffect(() => {
    loadRuleSets();
  }, []);

  const loadRuleSets = async () => {
    setLoading(true);
    try {
      const res = await ruleApi.listSets();
      setRuleSets(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  const loadBranches = async (ruleSetId: number) => {
    try {
      const res = await ruleApi.getBranches(ruleSetId);
      setBranches(res.data || []);
    } catch (e) {
      message.error('加载分支失败');
    }
  };

  const handleAddSet = () => {
    setEditingSet(null);
    setForm.resetFields();
    setSetModalOpen(true);
  };

  const handleEditSet = (record: RuleSet) => {
    setEditingSet(record);
    setForm.setFieldsValue(record);
    setSetModalOpen(true);
  };

  const handleDeleteSet = async (id: number) => {
    try {
      await ruleApi.deleteSet(id);
      message.success('删除成功');
      loadRuleSets();
    } catch (e) {
      message.error('删除失败');
    }
  };

  const handleSaveSet = async () => {
    try {
      const values = await setForm.validateFields();
      const payload = { ...editingSet, ...values };
      await ruleApi.saveSet(payload);
      message.success('保存成功');
      setSetModalOpen(false);
      loadRuleSets();
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error('保存失败');
    }
  };

  const handleOpenBranches = async (record: RuleSet) => {
    setCurrentSet(record);
    setBranchDrawerOpen(true);
    loadBranches(record.id!);
  };

  const handleAddBranch = () => {
    setEditingBranch(null);
    branchForm.resetFields();
    branchForm.setFieldsValue({
      ruleSetId: currentSet?.id,
      priority: 0,
      sortOrder: branches.length + 1,
    });
    setBranchModalOpen(true);
  };

  const handleEditBranch = (record: RuleBranch) => {
    setEditingBranch(record);
    branchForm.setFieldsValue(record);
    setBranchModalOpen(true);
  };

  const handleDeleteBranch = async (id: number) => {
    try {
      await ruleApi.deleteBranch(id);
      message.success('删除成功');
      loadBranches(currentSet!.id!);
    } catch (e) {
      message.error('删除失败');
    }
  };

  const handleSaveBranch = async () => {
    try {
      const values = await branchForm.validateFields();
      const payload = { ...editingBranch, ...values, ruleSetId: currentSet?.id };
      await ruleApi.saveBranch(payload);
      message.success('保存成功');
      setBranchModalOpen(false);
      loadBranches(currentSet!.id!);
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error('保存失败');
    }
  };

  const handleOpenSimulate = (record: RuleSet) => {
    setCurrentSet(record);
    setSimulateResult(null);
    simulateForm.resetFields();
    simulateForm.setFieldsValue({
      formData: JSON.stringify({ amount: 150000, title: '测试申请', category: '采购', urgency: 2 }, null, 2),
      systemVariables: JSON.stringify({ deptName: '销售部', userId: 1001, nickname: '张三' }, null, 2),
      businessData: JSON.stringify({ orderLevel: 2, eventType: 'ACCIDENT' }, null, 2),
    });
    setSimulateModalOpen(true);
  };

  const handleSimulate = async () => {
    try {
      const values = await simulateForm.validateFields();
      const req = {
        ruleCode: currentSet?.ruleCode,
        ruleSetId: currentSet?.id,
      } as any;
      if (values.formData) {
        try { req.formData = JSON.parse(values.formData); } catch { req.formData = values.formData; }
      }
      if (values.systemVariables) {
        try { req.systemVariables = JSON.parse(values.systemVariables); } catch { req.systemVariables = values.systemVariables; }
      }
      if (values.businessData) {
        const ctx: any = {};
        try { ctx.business = JSON.parse(values.businessData); } catch { ctx.business = values.businessData; }
        req.context = ctx;
      }
      const res = await ruleApi.simulate(req);
      setSimulateResult(res.data);
      message.success(res.data?.success ? '执行成功' : '规则执行完成');
    } catch (e: any) {
      if (e?.errorFields) return;
    }
  };

  const setColumns = [
    { title: '规则编码', dataIndex: 'ruleCode', key: 'ruleCode', width: 180 },
    { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 200 },
    {
      title: '网关类型',
      dataIndex: 'gatewayType',
      key: 'gatewayType',
      width: 120,
      render: (v: number) => {
        const opt = GATEWAY_TYPE_OPTIONS.find(o => o.value === v);
        return <Tag color={opt?.color as any}>{GATEWAY_TYPE_LABELS[v] || '未知'}</Tag>;
      },
    },
    { title: '默认分支', dataIndex: 'defaultBranch', key: 'defaultBranch', width: 140 },
    { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (v: number) => (v === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">禁用</Tag>),
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_: any, record: RuleSet) => (
        <Space size="small">
          <Button size="small" icon={<BranchesOutlined />} onClick={() => handleOpenBranches(record)}>分支</Button>
          <Button size="small" icon={<PlayCircleOutlined />} onClick={() => handleOpenSimulate(record)}>试算</Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEditSet(record)}>编辑</Button>
          <Popconfirm title="确认删除此规则集?" onConfirm={() => handleDeleteSet(record.id!)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const branchColumns = [
    { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 60 },
    { title: '分支编码', dataIndex: 'branchCode', key: 'branchCode', width: 120 },
    { title: '分支名称', dataIndex: 'branchName', key: 'branchName', width: 140 },
    {
      title: '表达式',
      dataIndex: 'expression',
      key: 'expression',
      ellipsis: true,
      render: (v: string) => (
        <Text code style={{ background: '#f5f5f5', padding: '2px 6px', borderRadius: 4, fontSize: 12 }}>
          {v || '-'}
        </Text>
      ),
    },
    { title: '动作类型', dataIndex: 'actionType', key: 'actionType', width: 100 },
    { title: '动作目标', dataIndex: 'actionTarget', key: 'actionTarget', width: 100 },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 80 },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: any, record: RuleBranch) => (
        <Space size="small">
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEditBranch(record)}>编辑</Button>
          <Popconfirm title="确认删除此分支?" onConfirm={() => handleDeleteBranch(record.id!)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 16 }}>
      <Card
        title={
          <Space>
            <span>规则引擎配置</span>
            <Tag color="blue">网关路由</Tag>
            <Tag color="purple">条件分支</Tag>
          </Space>
        }
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAddSet}>
            新增规则集
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={setColumns}
          dataSource={ruleSets}
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Modal
        title={editingSet ? '编辑规则集' : '新增规则集'}
        open={setModalOpen}
        onOk={handleSaveSet}
        onCancel={() => setSetModalOpen(false)}
        width={560}
        destroyOnClose
      >
        <Form form={setForm} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="ruleCode" label="规则编码" rules={[{ required: true }]}>
                <Input placeholder="如: APPROVAL_FLOW_001" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="ruleName" label="规则名称" rules={[{ required: true }]}>
                <Input placeholder="如: 审批金额路由" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="gatewayType" label="网关类型" rules={[{ required: true }]} initialValue={1}>
                <Select>
                  {GATEWAY_TYPE_OPTIONS.map(opt => (
                    <Option key={opt.value} value={opt.value}>
                      {opt.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="状态" initialValue={1}>
                <Select>
                  <Option value={1}>启用</Option>
                  <Option value={0}>禁用</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="defaultBranch" label="默认分支编码">
            <Input placeholder="当无匹配分支时的兜底分支，如: BRANCH_NORMAL" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <Space>
            <span>分支管理 - {currentSet?.ruleName}</span>
            <Tag color={GATEWAY_TYPE_OPTIONS.find(o => o.value === currentSet?.gatewayType)?.color as any}>
              {GATEWAY_TYPE_LABELS[currentSet?.gatewayType || 1]}
            </Tag>
          </Space>
        }
        width={960}
        open={branchDrawerOpen}
        onClose={() => setBranchDrawerOpen(false)}
        extra={
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={handleAddBranch}>
            新增分支
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={branchColumns}
          dataSource={branches}
          pagination={false}
          size="small"
        />

        <Divider orientation="left">说明</Divider>
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          • <Text strong>排他网关</Text>: 按优先级匹配第一个满足条件的分支<br />
          • <Text strong>并行网关</Text>: 所有分支同时执行<br />
          • <Text strong>包容网关</Text>: 执行所有满足条件的分支<br />
          • 表达式使用 Aviator 语法，可引用 <Text code>system.*</Text> <Text code>form.*</Text> <Text code>business.*</Text> 中的字段
        </Paragraph>
      </Drawer>

      <Modal
        title={editingBranch ? '编辑分支' : '新增分支'}
        open={branchModalOpen}
        onOk={handleSaveBranch}
        onCancel={() => setBranchModalOpen(false)}
        width={880}
        destroyOnClose
      >
        <Form form={branchForm} layout="vertical">
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="branchCode" label="分支编码" rules={[{ required: true }]}>
                <Input placeholder="如: BRANCH_DIRECTOR" />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item name="branchName" label="分支名称" rules={[{ required: true }]}>
                <Input placeholder="如: 总监审批" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="sortOrder" label="排序" initialValue={1}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="expression" label="条件表达式" rules={[{ required: true }]}>
            <Input.TextArea
              rows={3}
              placeholder='form.amount > 100000 && system.deptName == "销售部"'
              style={{ fontFamily: 'Consolas, Monaco, monospace' }}
            />
          </Form.Item>

          <ExpressionBuilder
            value={branchForm.getFieldValue('expression')}
            onChange={(v) => branchForm.setFieldsValue({ expression: v })}
          />

          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="actionType" label="动作类型">
                <Select allowClear>
                  {ACTION_TYPE_OPTIONS.map(o => (
                    <Option key={o.value} value={o.value}>{o.label}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="actionTarget" label="动作目标">
                <Input placeholder="如: director / manager / team_lead" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="priority" label="优先级" initialValue={0}>
                <InputNumber min={0} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="actionParams" label="动作参数 (JSON)">
            <Input.TextArea rows={2} placeholder='{"deptId": 1, "userName": "张三"}' />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={
          <Space>
            <span>规则试算 - {currentSet?.ruleName}</span>
            {simulateResult?.success !== undefined && (
              simulateResult.success
                ? <Tag color="success">执行成功</Tag>
                : <Tag color="error">执行异常</Tag>
            )}
          </Space>
        }
        open={simulateModalOpen}
        onOk={handleSimulate}
        okText="执行试算"
        onCancel={() => setSimulateModalOpen(false)}
        width={920}
        destroyOnClose
      >
        <Form form={simulateForm} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="formData" label="表单数据 (form.*)">
                <Input.TextArea rows={5} style={{ fontFamily: 'Consolas, monospace', fontSize: 12 }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="systemVariables" label="系统变量 (system.*)">
                <Input.TextArea rows={5} style={{ fontFamily: 'Consolas, monospace', fontSize: 12 }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="businessData" label="业务数据 (business.*)">
            <Input.TextArea rows={4} style={{ fontFamily: 'Consolas, monospace', fontSize: 12 }} />
          </Form.Item>
        </Form>

        {simulateResult && (
          <>
            <Divider />
            <Title level={5} style={{ marginTop: 0 }}>
              试算结果
              {simulateResult.executionTime && (
                <Tag color="blue" style={{ marginLeft: 8 }}>耗时: {simulateResult.executionTime}ms</Tag>
              )}
              <Tag style={{ marginLeft: 8 }}>网关: {simulateResult.gatewayTypeName}</Tag>
            </Title>
            <Card size="small" title={`命中分支 (${simulateResult.matchedBranches?.length || 0})`} style={{ marginBottom: 12 }}>
              {simulateResult.matchedBranches?.length ? (
                simulateResult.matchedBranches.map((b, i) => (
                  <Tag key={i} color="green" style={{ fontSize: 13 }}>
                    {b.branchName} <Text type="secondary">({b.branchCode})</Text>
                    {b.actionTarget && <Tag color="blue" style={{ marginLeft: 4 }}>{b.actionType} → {b.actionTarget}</Tag>}
                  </Tag>
                ))
              ) : (
                <Tag color="default">未命中任何分支</Tag>
              )}
            </Card>
            {simulateResult.allBranches && (
              <Card size="small" title={`全部分支评估 (${simulateResult.allBranches.length})`}>
                <ul style={{ paddingLeft: 20, margin: 0 }}>
                  {simulateResult.allBranches.map((b, i) => {
                    const matched = simulateResult.matchedBranches?.some(m => m.id === b.id);
                    return (
                      <li key={i} style={{ marginBottom: 4 }}>
                        <Tag color={matched ? 'green' : 'default'} style={{ marginRight: 8 }}>
                          {matched ? '✓ 命中' : '✗ 未命中'}
                        </Tag>
                        <Text strong>{b.branchName}</Text>
                        <Text code style={{ marginLeft: 8, fontSize: 12 }}>{b.expression}</Text>
                      </li>
                    );
                  })}
                </ul>
              </Card>
            )}
          </>
        )}
      </Modal>
    </div>
  );
};

export default Rules;
