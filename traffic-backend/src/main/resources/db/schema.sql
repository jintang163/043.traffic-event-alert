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
