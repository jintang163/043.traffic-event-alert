import React, { useState, useEffect } from 'react';
import { Layout as AntLayout, Menu, Avatar, Dropdown, Badge, Button } from 'antd';
import {
  DashboardOutlined,
  VideoCameraOutlined,
  BellOutlined,
  FileTextOutlined,
  TeamOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
  EnvironmentOutlined,
  CarOutlined,
  BranchesOutlined,
  TableOutlined,
  FileSearchOutlined,
  LineChartOutlined,
  PlayCircleOutlined,
  NotificationOutlined,
  MessageOutlined,
  ScheduleOutlined,
  CarOutlined,
  SendOutlined,
  SafetyOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { useAlertStore } from '@/store/alertStore';
import { wsService } from '@/services/websocket';
import type { MenuProps } from 'antd';

const { Header, Sider, Content } = AntLayout;

interface LayoutProps {
  children: React.ReactNode;
}

const Layout: React.FC<LayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuthStore();
  const { unreadCount, addAlert, markAllRead } = useAlertStore();
  const [collapsed, setCollapsed] = useState(false);

  useEffect(() => {
    if (user) {
      wsService.connect(user.id);
      const unsub = wsService.onAlert((alert) => {
        addAlert(alert as any);
      });
      return () => {
        unsub();
        wsService.disconnect();
      };
    }
  }, [user, addAlert]);

  const menuItems: MenuProps['items'] = [
    {
      key: '/',
      icon: <DashboardOutlined />,
      label: '监控大屏',
    },
    {
      key: '/cameras',
      icon: <VideoCameraOutlined />,
      label: '摄像头管理',
    },
    {
      key: '/edge-nodes',
      icon: <ThunderboltOutlined />,
      label: '边缘节点',
    },
    {
      key: '/alerts',
      icon: (
        <Badge count={unreadCount} size="small" offset={[8, -2]}>
          <BellOutlined />
        </Badge>
      ),
      label: '告警中心',
    },
    {
      key: '/prediction',
      icon: <ThunderboltOutlined />,
      label: '预测预警',
    },
    {
      key: '/work-orders',
      icon: <FileTextOutlined />,
      label: '工单管理',
    },
    {
      key: '/departments',
      icon: <TeamOutlined />,
      label: '部门管理',
    },
    {
      key: '/geo-fences',
      icon: <EnvironmentOutlined />,
      label: '电子围栏',
    },
    {
      key: '/tracks',
      icon: <CarOutlined />,
      label: '目标轨迹',
    },
    {
      key: '/traffic-statistics',
      icon: <LineChartOutlined />,
      label: '交通统计',
    },
    {
      key: '/event-videos',
      icon: <PlayCircleOutlined />,
      label: '事件视频',
    },
    {
      key: 'sub_rule_engine',
      icon: <BranchesOutlined />,
      label: '规则引擎',
      children: [
        {
          key: '/rules',
          icon: <SettingOutlined />,
          label: '规则配置',
        },
        {
          key: '/decision-tables',
          icon: <TableOutlined />,
          label: '决策表配置',
        },
        {
          key: '/rule-logs',
          icon: <FileSearchOutlined />,
          label: '执行日志',
        },
      ],
    },
    {
      key: 'sub_notify',
      icon: <NotificationOutlined />,
      label: '告警推送',
      children: [
        {
          key: '/notify-channels',
          icon: <MessageOutlined />,
          label: '推送渠道',
        },
        {
          key: '/notify-templates',
          icon: <SettingOutlined />,
          label: '推送模板',
        },
        {
          key: '/notify-rules',
          icon: <BranchesOutlined />,
          label: '推送规则',
        },
        {
          key: '/on-duty',
          icon: <ScheduleOutlined />,
          label: '值班排班',
        },
        {
          key: '/notify-logs',
          icon: <FileSearchOutlined />,
          label: '推送日志',
        },
      ],
    },
    {
      key: 'sub_plate',
      icon: <CarOutlined />,
      label: '车牌识别',
      children: [
        {
          key: '/plate-recognitions',
          icon: <CarOutlined />,
          label: '识别记录',
        },
        {
          key: '/police-pushes',
          icon: <SendOutlined />,
          label: '交警推送日志',
        },
        {
          key: '/police-system-configs',
          icon: <SafetyOutlined />,
          label: '交警系统配置',
        },
      ],
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
    if (key === '/alerts') {
      markAllRead();
    }
  };

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: () => {
        logout();
        wsService.disconnect();
        navigate('/login');
      },
    },
  ];

  return (
    <AntLayout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        theme="dark"
        width={220}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontSize: collapsed ? 16 : 18,
            fontWeight: 600,
            background: 'rgba(24, 144, 255, 0.2)',
            margin: 12,
            borderRadius: 8,
          }}
        >
          {collapsed ? '🚦' : '🚦 交通事件检测'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={handleMenuClick}
          style={{ borderRight: 0 }}
        />
      </Sider>
      <AntLayout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 4px rgba(0,21,41,.08)',
          }}
        >
          <div style={{ fontSize: 16, fontWeight: 500, color: '#1f2937' }}>
            高速公路交通事件智能检测平台
          </div>
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar icon={<UserOutlined />} style={{ background: '#1890ff' }}>
                {user?.nickname?.[0]}
              </Avatar>
              <span style={{ color: '#333' }}>{user?.nickname || user?.username}</span>
            </div>
          </Dropdown>
        </Header>
        <Content
          style={{
            margin: 16,
            padding: 24,
            background: '#f5f7fa',
            minHeight: 'calc(100vh - 96px)',
            borderRadius: 8,
          }}
        >
          {children}
        </Content>
      </AntLayout>
    </AntLayout>
  );
};

export default Layout;
