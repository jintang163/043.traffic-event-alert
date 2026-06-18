CREATE DATABASE IF NOT EXISTS traffic_alert DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE traffic_alert;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    role INT DEFAULT 1,
    status INT DEFAULT 1,
    avatar VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dept_code VARCHAR(64) NOT NULL UNIQUE,
    dept_name VARCHAR(128) NOT NULL,
    dept_type INT,
    longitude DECIMAL(10,6),
    latitude DECIMAL(10,6),
    contact_person VARCHAR(64),
    contact_phone VARCHAR(32),
    status INT DEFAULT 1,
    description VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_dept_type (dept_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS camera (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_code VARCHAR(64) NOT NULL UNIQUE,
    camera_name VARCHAR(128) NOT NULL,
    protocol VARCHAR(32),
    stream_url VARCHAR(512),
    gb_device_id VARCHAR(64),
    manufacturer VARCHAR(64),
    location VARCHAR(512),
    longitude DECIMAL(10,6),
    latitude DECIMAL(10,6),
    road_name VARCHAR(128),
    direction INT,
    lane_count INT,
    status INT DEFAULT 1,
    online_status INT DEFAULT 0,
    ptz_enabled INT DEFAULT 0,
    ptz_presets TEXT,
    description VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_camera_code (camera_code),
    INDEX idx_status (status),
    INDEX idx_online_status (online_status),
    INDEX idx_road_name (road_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alert_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_no VARCHAR(64) NOT NULL UNIQUE,
    event_type VARCHAR(32) NOT NULL,
    event_level INT DEFAULT 1,
    camera_id BIGINT,
    camera_name VARCHAR(128),
    location VARCHAR(512),
    longitude DECIMAL(10,6),
    latitude DECIMAL(10,6),
    event_time DATETIME,
    confidence DECIMAL(5,4),
    event_snapshot VARCHAR(512),
    event_video VARCHAR(512),
    description VARCHAR(1024),
    alert_status INT DEFAULT 0,
    is_false_positive INT DEFAULT 0,
    false_positive_reason VARCHAR(512),
    handle_user_id BIGINT,
    handle_time DATETIME,
    handle_remark VARCHAR(1024),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_event_no (event_no),
    INDEX idx_event_type (event_type),
    INDEX idx_event_level (event_level),
    INDEX idx_camera_id (camera_id),
    INDEX idx_event_time (event_time),
    INDEX idx_alert_status (alert_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS work_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    alert_event_id BIGINT,
    event_type VARCHAR(32),
    order_level INT DEFAULT 1,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    assign_dept_id BIGINT,
    assign_dept_name VARCHAR(128),
    assign_user_id BIGINT,
    assign_user_name VARCHAR(64),
    order_status INT DEFAULT 0,
    plan_start_time DATETIME,
    plan_end_time DATETIME,
    actual_start_time DATETIME,
    actual_end_time DATETIME,
    handle_content TEXT,
    handle_images TEXT,
    remark VARCHAR(1024),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_order_no (order_no),
    INDEX idx_alert_event_id (alert_event_id),
    INDEX idx_order_status (order_status),
    INDEX idx_assign_dept_id (assign_dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_clip (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT,
    clip_type VARCHAR(32),
    alert_event_id BIGINT,
    file_name VARCHAR(256),
    file_path VARCHAR(512),
    file_url VARCHAR(512),
    file_size BIGINT,
    duration INT,
    start_time DATETIME,
    end_time DATETIME,
    thumbnail_url VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_camera_id (camera_id),
    INDEX idx_alert_event_id (alert_event_id),
    INDEX idx_clip_type (clip_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS detection_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT NOT NULL,
    task_type VARCHAR(32),
    status INT DEFAULT 0,
    fps INT DEFAULT 2,
    ai_server VARCHAR(128),
    gpu_device VARCHAR(64),
    last_heartbeat DATETIME,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_camera_id (camera_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ptz_preset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT NOT NULL,
    preset_index INT NOT NULL,
    preset_name VARCHAR(128) NOT NULL,
    pan DECIMAL(10,4),
    tilt DECIMAL(10,4),
    zoom DECIMAL(10,4),
    thumbnail_url VARCHAR(512),
    sort_order INT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_camera_id (camera_id),
    INDEX idx_preset_index (preset_index),
    UNIQUE KEY uk_camera_preset (camera_id, preset_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ptz_cruise (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT NOT NULL,
    cruise_name VARCHAR(128) NOT NULL,
    cruise_type INT DEFAULT 1,
    status INT DEFAULT 0,
    stay_seconds INT DEFAULT 10,
    speed INT DEFAULT 5,
    loop_count INT DEFAULT 0,
    event_linkage INT DEFAULT 0,
    event_return_seconds INT DEFAULT 30,
    description VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_camera_id (camera_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ptz_cruise_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cruise_id BIGINT NOT NULL,
    preset_id BIGINT NOT NULL,
    preset_index INT,
    preset_name VARCHAR(128),
    stay_seconds INT,
    sort_order INT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_cruise_id (cruise_id),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_user (username, password, nickname, role, status, create_time) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 0, 1, NOW()),
('operator', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '值班员', 1, 1, NOW());

INSERT INTO sys_department (dept_code, dept_name, dept_type, longitude, latitude, contact_person, contact_phone, status, create_time) VALUES
('MAINT_001', '高速养护一队', 1, 116.407400, 39.904200, '张队长', '13800138001', 1, NOW()),
('TRAFFIC_001', '高速交警一大队', 2, 116.417400, 39.914200, '李队长', '13800138002', 1, NOW());

INSERT INTO camera (camera_code, camera_name, protocol, stream_url, manufacturer, location, longitude, latitude, road_name, direction, lane_count, status, online_status, ptz_enabled, create_time) VALUES
('CAM001', '京港澳高速K100+500北', 'RTSP', 'rtsp://192.168.1.101:554/stream1', '海康威视', '京港澳高速K100+500北方向', 116.397400, 39.904200, '京港澳高速', 1, 4, 1, 1, 1, NOW()),
('CAM002', '京港澳高速K100+500南', 'RTSP', 'rtsp://192.168.1.102:554/stream1', '海康威视', '京港澳高速K100+500南方向', 116.397400, 39.903200, '京港澳高速', 2, 4, 1, 1, 1, NOW()),
('CAM003', '京藏高速K50+200东', 'RTSP', 'rtsp://192.168.1.103:554/stream1', '大华', '京藏高速K50+200东方向', 116.407400, 39.914200, '京藏高速', 1, 3, 1, 0, 0, NOW()),
('CAM004', '京藏高速K50+200西', 'RTSP', 'rtsp://192.168.1.104:554/stream1', '大华', '京藏高速K50+200西方向', 116.408400, 39.914200, '京藏高速', 2, 3, 1, 1, 0, NOW());

CREATE TABLE IF NOT EXISTS geo_fence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fence_code VARCHAR(64) NOT NULL UNIQUE,
    fence_name VARCHAR(128) NOT NULL,
    fence_type INT DEFAULT 1 COMMENT '围栏类型：1-施工区 2-应急车道 3-禁入区 4-自定义',
    camera_id BIGINT,
    camera_name VARCHAR(128),
    polygon_points TEXT COMMENT 'GIS多边形顶点坐标JSON [[lng,lat],...]用于地图展示',
    polygon_points_pixel TEXT COMMENT '归一化像素坐标JSON [[nx,ny],...] 值0~1 用于AI检测',
    center_longitude DECIMAL(10,6),
    center_latitude DECIMAL(10,6),
    area DECIMAL(12,2) COMMENT '面积 平方米',
    alert_enabled INT DEFAULT 1 COMMENT '是否启用告警',
    alert_level INT DEFAULT 2 COMMENT '告警级别 1-一般 2-严重 3-紧急',
    detect_target_types VARCHAR(256) COMMENT '检测目标类型，逗号分隔：person,car,truck,bus',
    stay_seconds INT DEFAULT 0 COMMENT '停留多久触发告警，0表示立即触发',
    cooldown_seconds INT DEFAULT 60 COMMENT '告警冷却时间 秒',
    notify_enabled INT DEFAULT 1 COMMENT '是否启用通知',
    notify_dept_ids VARCHAR(512) COMMENT '通知部门ID列表，逗号分隔',
    link_work_order INT DEFAULT 0 COMMENT '是否自动联动工单',
    color VARCHAR(16) DEFAULT '#ff4d4f' COMMENT '围栏显示颜色',
    description VARCHAR(512),
    sort_order INT DEFAULT 0,
    status INT DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_fence_code (fence_code),
    INDEX idx_fence_type (fence_type),
    INDEX idx_camera_id (camera_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO geo_fence (fence_code, fence_name, fence_type, camera_id, camera_name, polygon_points, polygon_points_pixel, center_longitude, center_latitude, area, alert_enabled, alert_level, detect_target_types, stay_seconds, cooldown_seconds, notify_enabled, link_work_order, color, description, sort_order, status, create_time) VALUES
('FENCE001', 'K100+500施工区', 1, 1, '京港澳高速K100+500北', '[[116.3970,39.9045],[116.3978,39.9045],[116.3978,39.9040],[116.3970,39.9040]]', '[[0.1,0.1],[0.9,0.1],[0.9,0.8],[0.1,0.8]]', 116.397400, 39.904250, 2500.00, 1, 2, 'person,car,truck', 5, 60, 1, 1, '#faad14', 'K100+500处道路施工区域', 1, 1, NOW()),
('FENCE002', 'K100+500应急车道', 2, 1, '京港澳高速K100+500北', '[[116.3965,39.9043],[116.3969,39.9043],[116.3969,39.9038],[116.3965,39.9038]]', '[[0.0,0.1],[0.1,0.1],[0.1,0.9],[0.0,0.9]]', 116.396700, 39.904050, 1200.00, 1, 3, 'car,truck,bus', 3, 120, 1, 1, '#ff4d4f', '应急车道禁入区域', 2, 1, NOW()),
('FENCE003', 'K50+200禁入区', 3, 3, '京藏高速K50+200东', '[[116.4070,39.9145],[116.4078,39.9145],[116.4078,39.9140],[116.4070,39.9140]]', '[[0.2,0.05],[0.8,0.05],[0.8,0.95],[0.2,0.95]]', 116.407400, 39.914250, 1800.00, 1, 2, 'person', 0, 60, 1, 0, '#52c41a', '行人禁入区域', 3, 1, NOW());

CREATE TABLE IF NOT EXISTS global_track (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    track_no VARCHAR(64) NOT NULL UNIQUE COMMENT '全局轨迹编号 TRK202506180001',
    target_class VARCHAR(32) COMMENT '目标类别 car/truck/bus/person',
    license_plate VARCHAR(16) COMMENT '车牌号',
    plate_confidence DECIMAL(5,4) COMMENT '车牌识别置信度',
    color VARCHAR(32) COMMENT '车身颜色',
    vehicle_type VARCHAR(32) COMMENT '车辆类型 小车/货车/客车',
    reid_feature TEXT COMMENT 'ReID特征向量(JSON数组)',
    first_camera_id BIGINT COMMENT '首次出现摄像头ID',
    first_camera_name VARCHAR(128),
    last_camera_id BIGINT COMMENT '最后出现摄像头ID',
    last_camera_name VARCHAR(128),
    first_longitude DECIMAL(10,6),
    first_latitude DECIMAL(10,6),
    last_longitude DECIMAL(10,6),
    last_latitude DECIMAL(10,6),
    first_seen_time DATETIME COMMENT '首次出现时间',
    last_seen_time DATETIME COMMENT '最后出现时间',
    camera_count INT DEFAULT 1 COMMENT '经过摄像头数量',
    point_count INT DEFAULT 0 COMMENT '轨迹点数量',
    total_distance DECIMAL(12,2) DEFAULT 0 COMMENT '累计距离 米',
    avg_speed DECIMAL(8,2) COMMENT '平均速度 km/h',
    track_status INT DEFAULT 1 COMMENT '1-跟踪中 2-已丢失 3-已完成',
    is_event_target INT DEFAULT 0 COMMENT '是否为关联告警事件目标',
    linked_event_count INT DEFAULT 0 COMMENT '关联告警事件数',
    snapshot_url VARCHAR(512) COMMENT '最佳抓拍图',
    description VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_track_no (track_no),
    INDEX idx_target_class (target_class),
    INDEX idx_license_plate (license_plate),
    INDEX idx_first_camera (first_camera_id),
    INDEX idx_last_camera (last_camera_id),
    INDEX idx_track_status (track_status),
    INDEX idx_first_seen (first_seen_time),
    INDEX idx_last_seen (last_seen_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS track_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    track_id BIGINT NOT NULL COMMENT '关联global_track.id',
    camera_id BIGINT NOT NULL,
    camera_name VARCHAR(128),
    frame_no BIGINT COMMENT '帧号',
    frame_time DATETIME NOT NULL COMMENT '帧时间戳',
    bbox_x1 INT COMMENT '检测框',
    bbox_y1 INT,
    bbox_x2 INT,
    bbox_y2 INT,
    bbox_confidence DECIMAL(5,4),
    longitude DECIMAL(10,6) COMMENT 'GPS坐标(可选)',
    latitude DECIMAL(10,6),
    pixel_x DECIMAL(6,4) COMMENT '归一化像素x 0~1',
    pixel_y DECIMAL(6,4) COMMENT '归一化像素y 0~1',
    velocity_x DECIMAL(8,2) COMMENT '速度向量',
    velocity_y DECIMAL(8,2),
    speed DECIMAL(8,2) COMMENT '瞬时速度 km/h',
    direction DECIMAL(6,2) COMMENT '运动方向角 0~360度',
    reid_feature TEXT COMMENT '该帧ReID特征(与global_track最佳特征)',
    snapshot_url VARCHAR(512),
    is_key_point INT DEFAULT 0 COMMENT '是否关键点(进入/离开/事件发生点)',
    key_point_type INT COMMENT '1-进入 2-离开 3-事件点 4-方向变化',
    create_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_track_id (track_id),
    INDEX idx_camera_id (camera_id),
    INDEX idx_frame_time (frame_time),
    INDEX idx_key_point (is_key_point)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS track_match_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_time DATETIME NOT NULL,
    source_camera_id BIGINT NOT NULL,
    target_camera_id BIGINT NOT NULL,
    source_track_id BIGINT,
    source_track_no VARCHAR(64),
    target_track_id BIGINT,
    target_track_no VARCHAR(64),
    global_track_id BIGINT,
    match_method INT COMMENT '1-车牌 2-ReID 3-联合匹配',
    match_score DECIMAL(5,4) COMMENT '匹配置信度',
    plate_match_score DECIMAL(5,4),
    reid_match_score DECIMAL(5,4),
    travel_seconds INT COMMENT '跨摄像头实际耗时 秒',
    expected_seconds INT COMMENT '预期耗时 秒',
    is_success INT DEFAULT 0 COMMENT '1-匹配成功',
    reason VARCHAR(512) COMMENT '失败原因',
    create_time DATETIME,
    INDEX idx_global_track (global_track_id),
    INDEX idx_source_camera (source_camera_id),
    INDEX idx_target_camera (target_camera_id),
    INDEX idx_match_time (match_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_track_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL COMMENT 'alert_event.id',
    event_no VARCHAR(64),
    track_id BIGINT NOT NULL COMMENT 'global_track.id',
    track_no VARCHAR(64),
    link_type INT DEFAULT 1 COMMENT '1-事件发生时目标 2-事件关联目标',
    link_confidence DECIMAL(5,4) COMMENT '关联置信度',
    camera_id BIGINT COMMENT '事件发生时所在摄像头',
    track_point_id BIGINT COMMENT '对应轨迹点ID',
    description VARCHAR(256),
    create_time DATETIME,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_event_track (event_id, track_id, link_type),
    INDEX idx_event (event_id),
    INDEX idx_track (track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS camera_neighbor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT NOT NULL COMMENT '当前摄像头ID',
    neighbor_camera_id BIGINT NOT NULL COMMENT '相邻摄像头ID',
    neighbor_camera_name VARCHAR(128),
    direction INT DEFAULT 0 COMMENT '相对方向: 0-同向 1-反向 2-交叉',
    distance DECIMAL(10,2) COMMENT '距离(米)',
    travel_time_seconds INT COMMENT '预计行程时间(秒)',
    priority INT DEFAULT 0 COMMENT '优先级, 数值越小越优先',
    description VARCHAR(256),
    create_time DATETIME,
    UNIQUE KEY uk_cam_neighbor (camera_id, neighbor_camera_id),
    INDEX idx_camera (camera_id),
    INDEX idx_neighbor (neighbor_camera_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO global_track (track_no, target_class, license_plate, plate_confidence, color, vehicle_type,
    first_camera_id, first_camera_name, last_camera_id, last_camera_name,
    first_longitude, first_latitude, last_longitude, last_latitude,
    first_seen_time, last_seen_time, camera_count, point_count,
    total_distance, avg_speed, track_status, is_event_target, linked_event_count,
    description, create_time) VALUES
('TRK202506180001', 'car', '京A12345', 0.9850, '白色', '小车',
    1, '京港澳高速K100+500北', 2, '京港澳高速K100+500南',
    116.397400, 39.904200, 116.397400, 39.903200,
    '2025-06-18 10:20:00', '2025-06-18 10:21:30', 2, 128,
    350.00, 84.20, 3, 1, 1, '跨K100+500南北两摄像头', NOW()),
('TRK202506180002', 'truck', '京B88888', 0.9500, '红色', '货车',
    3, '京藏高速K50+200东', 4, '京藏高速K50+200西',
    116.407400, 39.914200, 116.408400, 39.914200,
    '2025-06-18 10:25:00', '2025-06-18 10:26:15', 2, 96,
    220.00, 66.80, 2, 0, 0, '追踪中', NOW());

CREATE TABLE IF NOT EXISTS rule_set (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL UNIQUE COMMENT '规则编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    gateway_type INT DEFAULT 1 COMMENT '网关类型：1-排他 2-并行 3-包容',
    description VARCHAR(512) COMMENT '描述',
    status INT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    default_branch VARCHAR(64) COMMENT '默认分支编码',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_rule_code (rule_code),
    INDEX idx_gateway_type (gateway_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则集表';

CREATE TABLE IF NOT EXISTS rule_branch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_set_id BIGINT NOT NULL COMMENT '规则集ID',
    branch_code VARCHAR(64) NOT NULL COMMENT '分支编码',
    branch_name VARCHAR(128) NOT NULL COMMENT '分支名称',
    expression TEXT COMMENT '表达式',
    action_type VARCHAR(32) COMMENT '动作类型',
    action_target VARCHAR(128) COMMENT '动作目标',
    action_params TEXT COMMENT '动作参数JSON',
    priority DECIMAL(8,2) DEFAULT 0 COMMENT '优先级',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_rule_set_id (rule_set_id),
    INDEX idx_branch_code (branch_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则分支表';

CREATE TABLE IF NOT EXISTS decision_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_code VARCHAR(64) NOT NULL UNIQUE COMMENT '决策表编码',
    table_name VARCHAR(128) NOT NULL COMMENT '决策表名称',
    table_data MEDIUMTEXT COMMENT '决策表JSON数据',
    hit_policy VARCHAR(16) DEFAULT 'FIRST' COMMENT '命中策略：FIRST/RULE_ORDER',
    description VARCHAR(512) COMMENT '描述',
    status INT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_table_code (table_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策表';

CREATE TABLE IF NOT EXISTS rule_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL UNIQUE COMMENT '执行ID',
    rule_set_id BIGINT COMMENT '规则集ID',
    rule_code VARCHAR(64) COMMENT '规则编码',
    rule_name VARCHAR(128) COMMENT '规则名称',
    gateway_type INT COMMENT '网关类型',
    matched_branches VARCHAR(512) COMMENT '命中分支编码，逗号分隔',
    input_context MEDIUMTEXT COMMENT '输入上下文JSON',
    execution_result MEDIUMTEXT COMMENT '执行结果JSON',
    execution_time BIGINT COMMENT '执行耗时ms',
    error_message TEXT COMMENT '错误信息',
    success INT DEFAULT 1 COMMENT '是否成功：0-失败 1-成功',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_execution_id (execution_id),
    INDEX idx_rule_set_id (rule_set_id),
    INDEX idx_rule_code (rule_code),
    INDEX idx_create_time (create_time),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则执行日志';

INSERT INTO rule_set (rule_code, rule_name, gateway_type, description, status, default_branch, create_time) VALUES
('APPROVAL_FLOW_001', '审批金额路由规则', 1, '根据金额和发起人部门决定审批路径', 1, 'BRANCH_NORMAL', NOW()),
('WORK_ORDER_ASSIGN', '工单分派路由规则', 1, '根据工单级别、事件类型自动分派部门', 1, 'BRANCH_LOW', NOW());

INSERT INTO rule_branch (rule_set_id, branch_code, branch_name, expression, action_type, action_target, action_params, priority, sort_order, create_time) VALUES
(1, 'BRANCH_DIRECTOR', '总监审批', "form.amount > 100000 && system.deptName == '销售部'", 'APPROVAL', 'director', NULL, 100, 1, NOW()),
(1, 'BRANCH_MANAGER', '经理审批', "form.amount > 50000 && form.amount <= 100000", 'APPROVAL', 'manager', NULL, 50, 2, NOW()),
(1, 'BRANCH_NORMAL', '普通审批', "form.amount <= 50000", 'APPROVAL', 'team_lead', NULL, 0, 3, NOW()),
(2, 'BRANCH_URGENT', '紧急工单-总监办', "business.orderLevel >= 3 || business.eventType == 'ACCIDENT'", 'ASSIGN', 'director', NULL, 100, 1, NOW()),
(2, 'BRANCH_HIGH', '高级别-交警大队', "business.orderLevel == 2 || business.eventType == 'REVERSE'", 'ASSIGN', 'manager', NULL, 50, 2, NOW()),
(2, 'BRANCH_LOW', '普通-养护队', "business.orderLevel == 1 || business.orderLevel == 0", 'ASSIGN', 'team_lead', NULL, 0, 3, NOW());

