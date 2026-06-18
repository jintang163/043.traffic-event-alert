import React, { useState, useEffect } from 'react';
import {
  Button,
  Form,
  Input,
  Select,
  Space,
  Card,
  Tag,
  Tooltip,
  Divider,
  Typography,
  message,
  Row,
  Col,
  InputNumber,
} from 'antd';
import {
  PlusOutlined,
  MinusCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { expressionApi, ruleApi } from '@/services/api';
import type { FieldDefinition } from '@/types';

const { Text, Title, Paragraph } = Typography;
const { TextArea } = Input;
const { Option } = Select;

export interface ExpressionBuilderProps {
  value?: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
  showValidate?: boolean;
  showSimulate?: boolean;
  height?: number;
}

interface ConditionRow {
  id: string;
  field: string;
  operator: string;
  value: string;
  logic: 'AND' | 'OR';
}

const OPERATOR_OPTIONS = [
  { value: '==', label: '等于 (==)' },
  { value: '!=', label: '不等于 (!=)' },
  { value: '>', label: '大于 (>)' },
  { value: '>=', label: '大于等于 (>=)' },
  { value: '<', label: '小于 (<)' },
  { value: '<=', label: '小于等于 (<=)' },
  { value: 'contains', label: '包含 (contains)' },
  { value: 'in', label: '在...中 (in)' },
  { value: 'between', label: '在...之间 (between)' },
];

const ExpressionBuilder: React.FC<ExpressionBuilderProps> = ({
  value = '',
  onChange,
  readOnly = false,
  showValidate = true,
  showSimulate = true,
  height = 200,
}) => {
  const [expression, setExpression] = useState(value);
  const [conditions, setConditions] = useState<ConditionRow[]>([]);
  const [fieldDefinitions, setFieldDefinitions] = useState<FieldDefinition[]>([]);
  const [validateResult, setValidateResult] = useState<{ valid: boolean; message: string } | null>(null);
  const [simulateResult, setSimulateResult] = useState<any>(null);
  const [sampleContext, setSampleContext] = useState<Record<string, any>>({});

  useEffect(() => {
    setExpression(value);
  }, [value]);

  useEffect(() => {
    loadFieldDefinitions();
    loadSampleContext();
  }, []);

  const loadFieldDefinitions = async () => {
    try {
      const res = await ruleApi.fieldDefinitions();
      setFieldDefinitions(res.data || []);
    } catch (e) {
      console.error('加载字段定义失败', e);
    }
  };

  const loadSampleContext = async () => {
    try {
      const res = await ruleApi.sampleContext();
      setSampleContext(res.data || {});
    } catch (e) {
      console.error('加载示例上下文失败', e);
    }
  };

  const groupedFields = fieldDefinitions.reduce((acc, field) => {
    if (!acc[field.category]) acc[field.category] = [];
    acc[field.category].push(field);
    return acc;
  }, {} as Record<string, FieldDefinition[]>);

  const CATEGORY_LABELS: Record<string, string> = {
    system: '系统变量',
    form: '表单字段',
    business: '业务数据',
    custom: '自定义',
  };

  const addCondition = () => {
    const newRow: ConditionRow = {
      id: Date.now().toString() + Math.random().toString(36).slice(2, 6),
      field: '',
      operator: '==',
      value: '',
      logic: conditions.length > 0 ? 'AND' : 'AND',
    };
    setConditions([...conditions, newRow]);
  };

  const removeCondition = (id: string) => {
    setConditions(conditions.filter(c => c.id !== id));
  };

  const updateCondition = (id: string, key: keyof ConditionRow, value: any) => {
    setConditions(conditions.map(c => (c.id === id ? { ...c, [key]: value } : c)));
  };

  const buildExpressionFromConditions = () => {
    let expr = '';
    conditions.forEach((cond, idx) => {
      if (!cond.field || !cond.operator) return;
      let part = '';
      const fullField = `${cond.field}`;
      const fieldType = fieldDefinitions.find(f => f.field === cond.field.split('.').pop())?.type || 'string';

      switch (cond.operator) {
        case '==':
        case '!=':
          if (fieldType === 'string') {
            part = `${fullField} ${cond.operator} '${cond.value.replace(/'/g, "\\'")}'`;
          } else {
            part = `${fullField} ${cond.operator} ${cond.value}`;
          }
          break;
        case '>':
        case '>=':
        case '<':
        case '<=':
          part = `${fullField} ${cond.operator} ${cond.value}`;
          break;
        case 'contains':
          part = `string.contains(${fullField}, '${cond.value.replace(/'/g, "\\'")}')`;
          break;
        case 'in':
          const items = cond.value.split(',').map(s => s.trim()).filter(Boolean);
          const quotedItems = fieldType === 'string'
            ? items.map(i => `'${i}'`).join(', ')
            : items.join(', ');
          part = `seq.contains(seq.list(${quotedItems}), ${fullField})`;
          break;
        case 'between':
          const range = cond.value.split(',').map(s => s.trim()).filter(Boolean);
          if (range.length === 2) {
            part = `${fullField} >= ${range[0]} && ${fullField} <= ${range[1]}`;
          } else {
            part = `${fullField} == ${cond.value}`;
          }
          break;
        default:
          part = `${fullField} ${cond.operator} ${cond.value}`;
      }

      if (idx > 0 && part) {
        const prevLogic = conditions[idx - 1].logic;
        expr += ` ${prevLogic === 'AND' ? '&&' : '||'} `;
      }
      expr += part;
    });

    setExpression(expr);
    onChange?.(expr);
  };

  const handleExpressionChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const v = e.target.value;
    setExpression(v);
    onChange?.(v);
    setValidateResult(null);
  };

  const handleValidate = async () => {
    if (!expression.trim()) {
      message.warning('请先输入表达式');
      return;
    }
    try {
      const res = await expressionApi.validate({ expression });
      setValidateResult(res.data);
      if (res.data?.valid) {
        message.success('表达式语法正确');
      } else {
        message.error(res.data?.message || '表达式验证失败');
      }
    } catch (e: any) {
      setValidateResult({ valid: false, message: e.message || '验证失败' });
    }
  };

  const handleSimulate = async () => {
    if (!expression.trim()) {
      message.warning('请先输入表达式');
      return;
    }
    try {
      const res = await expressionApi.executeBoolean({
        expression,
        context: sampleContext,
      });
      setSimulateResult(res.data);
      message.info(`试算结果: ${res.data ? '✅ 条件满足 (true)' : '❌ 条件不满足 (false)'}`);
    } catch (e: any) {
      setSimulateResult({ error: e.message });
      message.error('试算失败');
    }
  };

  const insertField = (fieldPath: string) => {
    setExpression(prev => prev + fieldPath);
  };

  return (
    <div className="expression-builder">
      <Card
        size="small"
        title={
          <Space>
            <span>可视化表达式构建器</span>
            {expression && validateResult && (
              validateResult.valid ? (
                <Tag icon={<CheckCircleOutlined />} color="success">语法正确</Tag>
              ) : (
                <Tag icon={<CloseCircleOutlined />} color="error">语法错误</Tag>
              )
            )}
          </Space>
        }
        extra={
          !readOnly && (
            <Space size="small">
              <Button size="small" icon={<PlusOutlined />} onClick={addCondition}>
                添加条件
              </Button>
            </Space>
          )
        }
      >
        {!readOnly && conditions.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <Title level={5} style={{ marginTop: 0, marginBottom: 8 }}>条件构建</Title>
            {conditions.map((cond, idx) => (
              <Space key={cond.id} style={{ display: 'flex', marginBottom: 8 }} wrap>
                {idx > 0 && (
                  <Select
                    value={cond.logic}
                    onChange={(v) => updateCondition(cond.id, 'logic', v)}
                    style={{ width: 80 }}
                    size="small"
                  >
                    <Option value="AND">并且</Option>
                    <Option value="OR">或者</Option>
                  </Select>
                )}
                {idx === 0 && <div style={{ width: 80 }} />}
                <Select
                  value={cond.field}
                  onChange={(v) => updateCondition(cond.id, 'field', v)}
                  placeholder="选择字段"
                  style={{ width: 220 }}
                  size="small"
                  showSearch
                  optionFilterProp="label"
                >
                  {Object.entries(groupedFields).map(([cat, fields]) => (
                    <Select.OptGroup key={cat} label={CATEGORY_LABELS[cat] || cat}>
                      {fields.map((f) => (
                        <Option key={f.category + '.' + f.field} value={`${f.category}.${f.field}`} label={f.label}>
                          <Space>
                            <Text>{f.label}</Text>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {f.category}.{f.field}
                            </Text>
                            <Tag color="blue" style={{ fontSize: 10 }}>{f.type}</Tag>
                          </Space>
                        </Option>
                      ))}
                    </Select.OptGroup>
                  ))}
                </Select>
                <Select
                  value={cond.operator}
                  onChange={(v) => updateCondition(cond.id, 'operator', v)}
                  style={{ width: 140 }}
                  size="small"
                >
                  {OPERATOR_OPTIONS.map((op) => (
                    <Option key={op.value} value={op.value}>{op.label}</Option>
                  ))}
                </Select>
                <Input
                  value={cond.value}
                  onChange={(e) => updateCondition(cond.id, 'value', e.target.value)}
                  placeholder="输入值 (between用逗号分隔)"
                  style={{ width: 200 }}
                  size="small"
                />
                <Button
                  size="small"
                  type="text"
                  danger
                  icon={<MinusCircleOutlined />}
                  onClick={() => removeCondition(cond.id)}
                />
              </Space>
            ))}
            <Button type="primary" size="small" onClick={buildExpressionFromConditions}>
              生成表达式
            </Button>
            <Divider style={{ margin: '12px 0' }} />
          </div>
        )}

        <Title level={5} style={{ marginTop: 0, marginBottom: 8 }}>
          表达式文本
          <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal', marginLeft: 8 }}>
            Aviator 表达式语法
          </Text>
        </Title>

        <TextArea
          value={expression}
          onChange={handleExpressionChange}
          readOnly={readOnly}
          rows={Math.max(4, Math.floor(height / 24))}
          placeholder='示例: form.amount > 100000 && system.deptName == "销售部"'
          style={{ fontFamily: 'Consolas, Monaco, monospace', marginBottom: 12 }}
        />

        <Row gutter={12} align="top">
          <Col span={14}>
            <Title level={5} style={{ marginTop: 0, marginBottom: 8 }}>可用字段（点击插入）</Title>
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              {Object.entries(groupedFields).map(([cat, fields]) => (
                <div key={cat}>
                  <Tag color="blue" style={{ marginBottom: 4 }}>{CATEGORY_LABELS[cat] || cat}</Tag>
                  <div>
                    {fields.map((f) => (
                      <Tooltip key={f.field} title={f.description || f.label}>
                        <Tag
                          style={{ cursor: 'pointer', marginBottom: 4 }}
                          onClick={() => !readOnly && insertField(`${f.category}.${f.field}`)}
                        >
                          <Text strong>{f.label}</Text>
                          <Text type="secondary" style={{ marginLeft: 4, fontSize: 11 }}>
                            {f.category}.{f.field}
                          </Text>
                          {f.sampleValue !== undefined && (
                            <Text type="warning" style={{ marginLeft: 4, fontSize: 10 }}>
                              例:{JSON.stringify(f.sampleValue)}
                            </Text>
                          )}
                        </Tag>
                      </Tooltip>
                    ))}
                  </div>
                </div>
              ))}
            </Space>
          </Col>
          <Col span={10}>
            <Title level={5} style={{ marginTop: 0, marginBottom: 8 }}>操作</Title>
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              {showValidate && (
                <Button
                  block
                  icon={<CheckCircleOutlined />}
                  onClick={handleValidate}
                  type={validateResult?.valid ? 'primary' : 'default'}
                >
                  验证表达式
                </Button>
              )}
              {showSimulate && (
                <Button
                  block
                  icon={<PlayCircleOutlined />}
                  onClick={handleSimulate}
                >
                  试算（使用示例数据）
                </Button>
              )}

              {validateResult && (
                <Card size="small" title="验证结果">
                  <Paragraph style={{ margin: 0 }} type={validateResult.valid ? 'success' : 'danger'}>
                    {validateResult.message}
                  </Paragraph>
                </Card>
              )}
              {simulateResult !== null && (
                <Card size="small" title="试算结果">
                  <Tag color={simulateResult ? 'green' : 'red'} style={{ fontSize: 14 }}>
                    {simulateResult ? 'TRUE - 条件满足' : 'FALSE - 条件不满足'}
                  </Tag>
                </Card>
              )}
            </Space>
          </Col>
        </Row>
      </Card>
    </div>
  );
};

export default ExpressionBuilder;
