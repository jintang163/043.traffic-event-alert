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
  Popconfirm,
  Divider,
  Row,
  Col,
  Radio,
  InputNumber,
  Tabs,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  TableOutlined,
  ThunderboltOutlined,
  ArrowLeftOutlined,
  CheckCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { decisionTableApi, ruleApi } from '@/services/api';
import type {
  DecisionTable,
  DecisionRule,
  FieldDefinition,
  RuleBranch,
} from '@/types';
import { HIT_POLICY_OPTIONS, GATEWAY_TYPE_OPTIONS } from '@/types';

const { Title, Text, Paragraph } = Typography;
const { Option } = Select;
const { TextArea } = Input;

interface TableColumn {
  field: string;
  label: string;
  type: string;
  columnType: 'condition' | 'action';
  operator?: string;
}

interface TableRow {
  _description?: string;
  [key: string]: any;
}

const COLUMN_TYPE_OPTIONS: Array<'condition' | 'action'> = ['condition', 'action'];

const TYPE_OPTIONS = [
  { value: 'string', label: '字符串' },
  { value: 'number', label: '数值' },
  { value: 'decimal', label: '金额' },
  { value: 'enum', label: '枚举' },
  { value: 'boolean', label: '布尔' },
];

const OPERATOR_OPTIONS = [
  { value: '==', label: '等于 (==)' },
  { value: '!=', label: '不等于 (!=)' },
  { value: '>', label: '大于 (>)' },
  { value: '>=', label: '大于等于 (>=)' },
  { value: '<', label: '小于 (<)' },
  { value: '<=', label: '小于等于 (<=)' },
  { value: 'contains', label: '包含' },
  { value: 'in', label: '在集合中' },
  { value: 'between', label: '区间' },
];

const DecisionTables: React.FC = () => {
  const [tables, setTables] = useState<DecisionTable[]>([]);
  const [loading, setLoading] = useState(false);
  const [tableModalOpen, setTableModalOpen] = useState(false);
  const [editingTable, setEditingTable] = useState<DecisionTable | null>(null);
  const [tableForm] = Form.useForm();

  const [editorDrawerOpen, setEditorDrawerOpen] = useState(false);
  const [currentTable, setCurrentTable] = useState<DecisionTable | null>(null);
  const [columns, setColumns] = useState<TableColumn[]>([
    { field: 'business.orderLevel', label: '工单级别', type: 'number', columnType: 'condition', operator: '>=' },
    { field: 'business.eventType', label: '事件类型', type: 'enum', columnType: 'condition', operator: '==' },
    { field: 'system.deptName', label: '部门名称', type: 'string', columnType: 'condition', operator: '==' },
    { field: 'approver', label: '审批人', type: 'string', columnType: 'action' },
  ]);
  const [rows, setRows] = useState<TableRow[]>([
    { 'business.orderLevel': '3', 'business.eventType': 'ACCIDENT', 'system.deptName': '-', approver: 'director', _description: '紧急交通事故' },
    { 'business.orderLevel': '2', 'business.eventType': 'REVERSE', 'system.deptName': '-', approver: 'manager', _description: '严重逆行' },
    { 'business.orderLevel': '-', 'business.eventType': '-', 'system.deptName': '-', approver: 'team_lead', _description: '普通工单' },
  ]);

  const [fieldDefinitions, setFieldDefinitions] = useState<FieldDefinition[]>([]);
  const [parsedRules, setParsedRules] = useState<DecisionRule[]>([]);
  const [evalResult, setEvalResult] = useState<DecisionRule[]>([]);
  const [evalContext, setEvalContext] = useState<string>('{\n  "business": { "orderLevel": 3, "eventType": "ACCIDENT" },\n  "system": { "deptName": "销售部" }\n}');
  const [convertTargetRuleSet, setConvertTargetRuleSet] = useState<number | null>(null);
  const [convertReplace, setConvertReplace] = useState(true);
  const [convertResult, setConvertResult] = useState<RuleBranch[]>([]);

  useEffect(() => {
    loadTables();
    loadFieldDefinitions();
  }, []);

  const loadTables = async () => {
    setLoading(true);
    try {
      const res = await decisionTableApi.list();
      setTables(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  const loadFieldDefinitions = async () => {
    try {
      const res = await ruleApi.fieldDefinitions();
      setFieldDefinitions(res.data || []);
    } catch (e) {
      console.error(e);
    }
  };

  const handleAddTable = () => {
    setEditingTable(null);
    tableForm.resetFields();
    tableModalOpen || setTableModalOpen(true);
  };

  const handleEditTable = (record: DecisionTable) => {
    setEditingTable(record);
    tableForm.setFieldsValue(record);
    setTableModalOpen(true);
  };

  const handleDeleteTable = async (id: number) => {
    try {
      await decisionTableApi.delete(id);
      message.success('删除成功');
      loadTables();
    } catch (e) {
      message.error('删除失败');
    }
  };

  const handleSaveTable = async () => {
    try {
      const values = await tableForm.validateFields();
      const payload = { ...editingTable, ...values, tableData: currentTableData() };
      const res = await decisionTableApi.save(payload);
      message.success('保存成功');
      setTableModalOpen(false);
      loadTables();
      if (res.data?.id) {
        setCurrentTable(res.data);
      }
    } catch (e: any) {
      if (e?.errorFields) return;
    }
  };

  const currentTableData = (): string => {
    return JSON.stringify({ columns, rows }, null, 2);
  };

  const loadTableData = (data: string) => {
    try {
      const parsed = JSON.parse(data);
      if (parsed.columns) setColumns(parsed.columns);
      if (parsed.rows) setRows(parsed.rows);
    } catch (e) {
      console.error('加载决策表数据失败', e);
    }
  };

  const handleOpenEditor = (record: DecisionTable) => {
    setCurrentTable(record);
    if (record.tableData) {
      loadTableData(record.tableData);
    }
    setParsedRules([]);
    setEvalResult([]);
    setConvertResult([]);
    setEditorDrawerOpen(true);
  };

  const addColumn = (columnType: 'condition' | 'action') => {
    setColumns([...columns, {
      field: '',
      label: '',
      type: 'string',
      columnType,
      operator: columnType === 'condition' ? '==' : undefined,
    }]);
  };

  const removeColumn = (index: number) => {
    setColumns(columns.filter((_, i) => i !== index));
  };

  const updateColumn = (index: number, key: keyof TableColumn, value: any) => {
    setColumns(columns.map((c, i) => i === index ? { ...c, [key]: value } : c));
  };

  const addRow = () => {
    const newRow: TableRow = {};
    columns.forEach(col => { newRow[col.field] = ''; });
    newRow._description = `规则${rows.length + 1}`;
    setRows([...rows, newRow]);
  };

  const removeRow = (index: number) => {
    setRows(rows.filter((_, i) => i !== index));
  };

  const updateRowCell = (rowIdx: number, field: string, value: any) => {
    setRows(rows.map((r, i) => i === rowIdx ? { ...r, [field]: value } : r));
  };

  const handleParse = async () => {
    try {
      const tableData = currentTableData();
      const res = await decisionTableApi.parse({ tableData });
      setParsedRules(res.data || []);
      message.success(`解析成功，共 ${res.data?.length || 0} 条规则`);
    } catch (e) {
      message.error('解析失败');
    }
  };

  const handleEvaluate = async () => {
    try {
      let ctx = {};
      try { ctx = JSON.parse(evalContext); } catch { ctx = {}; }
      const tableData = currentTableData();
      const res = await decisionTableApi.evaluate({
        tableData,
        context: ctx,
        hitPolicy: currentTable?.hitPolicy || 'FIRST',
      });
      setEvalResult(res.data || []);
      const matched = res.data?.filter((r: DecisionRule) => r.matched).length || 0;
      message.success(`评估完成，命中 ${matched} 条规则`);
    } catch (e) {
      message.error('评估失败');
    }
  };

  const handleConvertToBranches = async () => {
    if (!convertTargetRuleSet) {
      message.warning('请选择目标规则集');
      return;
    }
    try {
      const tableData = currentTableData();
      const res = await ruleApi.convertDecisionTable(
        { tableData },
        convertTargetRuleSet,
        convertReplace,
      );
      setConvertResult(res.data || []);
      message.success(`转换成功，生成 ${res.data?.length || 0} 条规则分支`);
    } catch (e) {
      message.error('转换失败');
    }
  };

  const saveCurrent = async () => {
    if (!currentTable) {
      message.warning('请先创建决策表');
      return;
    }
    try {
      const payload: DecisionTable = {
        ...currentTable,
        tableData: currentTableData(),
      } as DecisionTable;
      await decisionTableApi.save(payload);
      message.success('保存成功');
      loadTables();
    } catch (e) {
      message.error('保存失败');
    }
  };

  const loadRuleSetsForSelect = async () => {
    try {
      const res = await ruleApi.listSets();
      return res.data || [];
    } catch { return []; }
  };

  const conditionColumns = columns.filter(c => c.columnType === 'condition');
  const actionColumns = columns.filter(c => c.columnType === 'action');

  const tableColumns = [
    { title: '决策表编码', dataIndex: 'tableCode', key: 'tableCode', width: 180 },
    { title: '决策表名称', dataIndex: 'tableName', key: 'tableName', width: 200 },
    {
      title: '命中策略',
      dataIndex: 'hitPolicy',
      key: 'hitPolicy',
      width: 160,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
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
      width: 240,
      render: (_: any, record: DecisionTable) => (
        <Space size="small">
          <Button size="small" icon={<TableOutlined />} onClick={() => handleOpenEditor(record)}>编辑表格</Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEditTable(record)}>基本信息</Button>
          <Popconfirm title="确认删除此决策表?" onConfirm={() => handleDeleteTable(record.id!)}>
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
            <span>决策表管理</span>
            <Tag color="purple">二维表格自动转规则</Tag>
            <Tag color="green">FIRST / RULE_ORDER</Tag>
          </Space>
        }
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAddTable}>
            新建决策表
          </Button>
        }
      >
        <Table
          rowKey="id"
          columns={tableColumns}
          dataSource={tables}
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Modal
        title={editingTable ? '编辑决策表' : '新建决策表'}
        open={tableModalOpen}
        onOk={handleSaveTable}
        onCancel={() => setTableModalOpen(false)}
        width={560}
      >
        <Form form={tableForm} layout="vertical">
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="tableCode" label="决策表编码" rules={[{ required: true }]}>
                <Input placeholder="如: DECISION_APPROVAL_001" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="tableName" label="决策表名称" rules={[{ required: true }]}>
                <Input placeholder="如: 工单分派决策表" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="hitPolicy" label="命中策略" initialValue="FIRST">
            <Select>
              {HIT_POLICY_OPTIONS.map(o => (
                <Option key={o.value} value={o.value}>{o.label}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select>
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
            </Select>
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <Space>
            <Button type="text" size="small" icon={<ArrowLeftOutlined />} onClick={() => setEditorDrawerOpen(false)} />
            <span>决策表编辑器 - {currentTable?.tableName || '未命名'}</span>
            {currentTable?.hitPolicy && <Tag color="blue">{currentTable.hitPolicy}</Tag>}
          </Space>
        }
        width={1280}
        open={editorDrawerOpen}
        onClose={() => setEditorDrawerOpen(false)}
        extra={
          <Space>
            <Button icon={<CheckCircleOutlined />} onClick={saveCurrent}>保存</Button>
          </Space>
        }
      >
        <Tabs
          items={[
            {
              key: 'design',
              label: '📊 表格设计',
              children: (
                <div>
                  <Card
                    size="small"
                    title="列定义"
                    extra={
                      <Space size="small">
                        <Button size="small" onClick={() => addColumn('condition')}>+ 条件列</Button>
                        <Button size="small" type="primary" onClick={() => addColumn('action')}>+ 动作列</Button>
                      </Space>
                    }
                    style={{ marginBottom: 12 }}
                  >
                    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                      <thead>
                        <tr style={{ background: '#f5f5f5' }}>
                          <th style={{ padding: 8, border: '1px solid #ddd', width: 80 }}>类型</th>
                          <th style={{ padding: 8, border: '1px solid #ddd' }}>字段</th>
                          <th style={{ padding: 8, border: '1px solid #ddd', width: 140 }}>显示标签</th>
                          <th style={{ padding: 8, border: '1px solid #ddd', width: 120 }}>数据类型</th>
                          {conditionColumns.length > 0 && (
                            <th style={{ padding: 8, border: '1px solid #ddd', width: 140 }}>比较操作符</th>
                          )}
                          <th style={{ padding: 8, border: '1px solid #ddd', width: 60 }}>操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        {columns.map((col, idx) => (
                          <tr key={idx}>
                            <td style={{ padding: 6, border: '1px solid #ddd' }}>
                              <Tag color={col.columnType === 'condition' ? 'blue' : 'green'}>
                                {col.columnType === 'condition' ? '条件' : '动作'}
                              </Tag>
                            </td>
                            <td style={{ padding: 6, border: '1px solid #ddd' }}>
                              <Select
                                showSearch
                                size="small"
                                value={col.field}
                                style={{ width: '100%' }}
                                onChange={(v) => updateColumn(idx, 'field', v)}
                                optionFilterProp="label"
                                allowClear
                              >
                                {fieldDefinitions.map(f => (
                                  <Option
                                    key={f.category + '.' + f.field}
                                    value={`${f.category}.${f.field}`}
                                    label={`${f.label} (${f.category}.${f.field})`}
                                  >
                                    <Space>
                                      <Tag color="blue" style={{ fontSize: 10 }}>{f.category}</Tag>
                                      <Text strong>{f.label}</Text>
                                      <Text type="secondary" style={{ fontSize: 11 }}>{f.category}.{f.field}</Text>
                                    </Space>
                                  </Option>
                                ))}
                              </Select>
                            </td>
                            <td style={{ padding: 6, border: '1px solid #ddd' }}>
                              <Input size="small" value={col.label} onChange={e => updateColumn(idx, 'label', e.target.value)} />
                            </td>
                            <td style={{ padding: 6, border: '1px solid #ddd' }}>
                              <Select size="small" value={col.type} onChange={v => updateColumn(idx, 'type', v)}>
                                {TYPE_OPTIONS.map(t => (
                                  <Option key={t.value} value={t.value}>{t.label}</Option>
                                ))}
                              </Select>
                            </td>
                            {conditionColumns.length > 0 && (
                              <td style={{ padding: 6, border: '1px solid #ddd' }}>
                                {col.columnType === 'condition' ? (
                                  <Select size="small" value={col.operator} onChange={v => updateColumn(idx, 'operator', v)}>
                                    {OPERATOR_OPTIONS.map(op => (
                                      <Option key={op.value} value={op.value}>{op.label}</Option>
                                    ))}
                                  </Select>
                                ) : <Text type="secondary">—</Text>}
                              </td>
                            )}
                            <td style={{ padding: 6, border: '1px solid #ddd', textAlign: 'center' }}>
                              <Button type="text" size="small" danger onClick={() => removeColumn(idx)}>✕</Button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </Card>

                  <Card
                    size="small"
                    title={
                      <Space>
                        <span>规则行数据</span>
                        <Tag color="blue">{rows.length} 条</Tag>
                        <Text type="secondary">（"-" 表示忽略该条件，留空表示此条规则不启用该字段）</Text>
                      </Space>
                    }
                    extra={
                      <Button size="small" type="primary" icon={<PlusOutlined />} onClick={addRow}>
                        新增规则行
                      </Button>
                    }
                  >
                    <div style={{ overflowX: 'auto' }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 800 }}>
                        <thead>
                          <tr style={{ background: '#fafafa' }}>
                            <th style={{ padding: 6, border: '1px solid #ddd', width: 50, textAlign: 'center' }}>#</th>
                            {columns.map((col, idx) => (
                              <th
                                key={idx}
                                style={{
                                  padding: 6,
                                  border: '1px solid #ddd',
                                  background: col.columnType === 'condition' ? '#e6f4ff' : '#f6ffed',
                                  minWidth: 160,
                                }}
                              >
                                <Tooltip title={col.field}>
                                  <Space>
                                    <Tag color={col.columnType === 'condition' ? 'blue' : 'green'} style={{ fontSize: 10 }}>
                                      {col.columnType === 'condition' ? 'IF' : 'THEN'}
                                    </Tag>
                                    <Text strong>{col.label || col.field}</Text>
                                  </Space>
                                </Tooltip>
                              </th>
                            ))}
                            <th style={{ padding: 6, border: '1px solid #ddd', width: 180 }}>规则描述</th>
                            <th style={{ padding: 6, border: '1px solid #ddd', width: 60 }}>操作</th>
                          </tr>
                        </thead>
                        <tbody>
                          {rows.map((row, rIdx) => (
                            <tr key={rIdx}>
                              <td style={{ padding: 6, border: '1px solid #ddd', textAlign: 'center', fontWeight: 'bold' }}>
                                R{rIdx + 1}
                              </td>
                              {columns.map((col, cIdx) => (
                                <td key={cIdx} style={{ padding: 4, border: '1px solid #ddd' }}>
                                  {col.type === 'number' ? (
                                    <InputNumber
                                      size="small"
                                      style={{ width: '100%' }}
                                      value={row[col.field] !== '' && row[col.field] != null ? Number(row[col.field]) : undefined}
                                      onChange={v => updateRowCell(rIdx, col.field, v ?? '')}
                                      placeholder="-"
                                    />
                                  ) : (
                                    <Input
                                      size="small"
                                      value={row[col.field] ?? ''}
                                      onChange={e => updateRowCell(rIdx, col.field, e.target.value)}
                                      placeholder="输入值或'-'跳过"
                                    />
                                  )}
                                </td>
                              ))}
                              <td style={{ padding: 4, border: '1px solid #ddd' }}>
                                <Input
                                  size="small"
                                  value={row._description ?? ''}
                                  onChange={e => updateRowCell(rIdx, '_description', e.target.value)}
                                  placeholder="规则说明"
                                />
                              </td>
                              <td style={{ padding: 4, border: '1px solid #ddd', textAlign: 'center' }}>
                                <Button type="text" size="small" danger onClick={() => removeRow(rIdx)}>✕</Button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </Card>
                </div>
              ),
            },
            {
              key: 'parse',
              label: '🔍 解析规则',
              children: (
                <div>
                  <Card
                    size="small"
                    style={{ marginBottom: 12 }}
                    extra={
                      <Button type="primary" icon={<SyncOutlined />} onClick={handleParse}>
                        解析决策表
                      </Button>
                    }
                  >
                    <Paragraph type="secondary" style={{ margin: 0 }}>
                      点击上方按钮将二维决策表解析为 Aviator 表达式规则列表
                    </Paragraph>
                  </Card>

                  {parsedRules.length > 0 && (
                    <Card size="small" title={`解析结果（${parsedRules.length} 条规则）`}>
                      <div style={{ maxHeight: 400, overflowY: 'auto' }}>
                        {parsedRules.map((rule, idx) => (
                          <Card
                            key={idx}
                            size="small"
                            type="inner"
                            title={
                              <Space>
                                <Tag color="blue">规则 R{rule.ruleIndex}</Tag>
                                <Text strong>{rule.description}</Text>
                              </Space>
                            }
                            style={{ marginBottom: 8 }}
                          >
                            <div>
                              <Text strong style={{ color: '#1677ff' }}>IF: </Text>
                              <Text code style={{ background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>
                                {rule.conditionExpression || '（无条件）'}
                              </Text>
                            </div>
                            <div style={{ marginTop: 6 }}>
                              <Text strong style={{ color: '#52c41a' }}>THEN: </Text>
                              {Object.keys(rule.actions || {}).length > 0 ? (
                                Object.entries(rule.actions!).map(([k, v]) => (
                                  <Tag key={k} color="green">
                                    {k} = {JSON.stringify(v)}
                                  </Tag>
                                ))
                              ) : (
                                <Text type="secondary">（无动作）</Text>
                              )}
                            </div>
                          </Card>
                        ))}
                      </div>
                    </Card>
                  )}
                </div>
              ),
            },
            {
              key: 'evaluate',
              label: '🧪 试算评估',
              children: (
                <div>
                  <Row gutter={12}>
                    <Col span={10}>
                      <Card size="small" title="输入上下文 (JSON)" style={{ marginBottom: 12 }}>
                        <TextArea
                          value={evalContext}
                          onChange={e => setEvalContext(e.target.value)}
                          rows={14}
                          style={{ fontFamily: 'Consolas, monospace', fontSize: 12, marginBottom: 12 }}
                        />
                        <Button
                          type="primary"
                          block
                          icon={<ThunderboltOutlined />}
                          onClick={handleEvaluate}
                        >
                          执行评估
                        </Button>
                      </Card>
                    </Col>
                    <Col span={14}>
                      <Card size="small" title="评估结果">
                        {evalResult.length === 0 ? (
                          <Text type="secondary">请输入上下文并点击"执行评估"</Text>
                        ) : (
                          <div>
                            <Space style={{ marginBottom: 12 }}>
                              <Tag color="green">命中: {evalResult.filter(r => r.matched).length}</Tag>
                              <Tag color="default">未命中: {evalResult.filter(r => !r.matched).length}</Tag>
                              <Tag color="blue">策略: {currentTable?.hitPolicy || 'FIRST'}</Tag>
                            </Space>
                            <div style={{ maxHeight: 420, overflowY: 'auto' }}>
                              {evalResult.map((rule, idx) => (
                                <Card
                                  key={idx}
                                  size="small"
                                  type="inner"
                                  style={{
                                    marginBottom: 8,
                                    borderLeft: rule.matched ? '4px solid #52c41a' : '4px solid #d9d9d9',
                                  }}
                                >
                                  <Space style={{ marginBottom: 6 }}>
                                    {rule.matched
                                      ? <Tag color="success">✓ 命中</Tag>
                                      : <Tag>✗ 未命中</Tag>}
                                    <Tag color="blue">R{rule.ruleIndex}</Tag>
                                    <Text strong>{rule.description}</Text>
                                  </Space>
                                  <Text code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>
                                    {rule.conditionExpression || '（无条件）'}
                                  </Text>
                                  {Object.keys(rule.actions || {}).length > 0 && (
                                    <div style={{ marginTop: 6 }}>
                                      {Object.entries(rule.actions!).map(([k, v]) => (
                                        <Tag key={k} color="green" style={{ fontSize: 12 }}>
                                          {k} = {JSON.stringify(v)}
                                        </Tag>
                                      ))}
                                    </div>
                                  )}
                                </Card>
                              ))}
                            </div>
                          </div>
                        )}
                      </Card>
                    </Col>
                  </Row>
                </div>
              ),
            },
            {
              key: 'convert',
              label: '⚙️ 转换为规则分支',
              children: (
                <div>
                  <Card size="small" style={{ marginBottom: 12 }} title="转换配置">
                    <Form layout="vertical">
                      <Row gutter={12}>
                        <Col span={10}>
                          <Form.Item label="目标规则集" required>
                            <Select
                              showSearch
                              placeholder="选择目标规则集"
                              value={convertTargetRuleSet}
                              onChange={setConvertTargetRuleSet}
                              optionFilterProp="children"
                            >
                              {tables.map(t => (
                                <Option key={t.id} value={t.id}>
                                  {t.tableName} ({t.tableCode})
                                </Option>
                              ))}
                            </Select>
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item label="覆盖现有分支">
                            <Radio.Group value={convertReplace} onChange={e => setConvertReplace(e.target.value)}>
                              <Radio value={true}>是（删除旧分支）</Radio>
                              <Radio value={false}>否（追加）</Radio>
                            </Radio.Group>
                          </Form.Item>
                        </Col>
                        <Col span={8}>
                          <Form.Item label="&nbsp;">
                            <Button type="primary" block icon={<SyncOutlined />} onClick={handleConvertToBranches}>
                              转换为规则分支
                            </Button>
                          </Form.Item>
                        </Col>
                      </Row>
                      <Paragraph type="secondary" style={{ margin: 0 }}>
                        此操作会将当前决策表解析后，批量写入 rule_branch 表，可直接用于规则引擎执行
                      </Paragraph>
                    </Form>
                  </Card>

                  {convertResult.length > 0 && (
                    <Card size="small" title={`转换完成 - 生成 ${convertResult.length} 条分支`}>
                      <Table
                        rowKey="branchCode"
                        size="small"
                        pagination={false}
                        dataSource={convertResult}
                        columns={[
                          { title: '排序', dataIndex: 'sortOrder', width: 60 },
                          { title: '分支编码', dataIndex: 'branchCode', width: 140 },
                          { title: '分支名称', dataIndex: 'branchName', width: 160 },
                          {
                            title: '表达式',
                            dataIndex: 'expression',
                            render: (v: string) => (
                              <Text code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>
                                {v}
                              </Text>
                            ),
                          },
                          { title: '动作类型', dataIndex: 'actionType', width: 100 },
                          { title: '动作目标', dataIndex: 'actionTarget', width: 120 },
                          { title: '优先级', dataIndex: 'priority', width: 80 },
                        ]}
                      />
                    </Card>
                  )}
                </div>
              ),
            },
          ]}
        />
      </Drawer>
    </div>
  );
};

export default DecisionTables;
