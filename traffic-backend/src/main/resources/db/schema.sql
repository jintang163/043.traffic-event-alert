CREATE DATABASE IF NOT EXISTS traffic_alert DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE traffic_alert;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    dept_id BIGINT COMMENT '所属部门ID',
    role INT DEFAULT 1,
    status INT DEFAULT 1,
    avatar VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_username (username),
    INDEX idx_role (role),
    INDEX idx_dept_id (dept_id)
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
    debris_category VARCHAR(32) DEFAULT NULL COMMENT '抛洒物子分类：TIRE/CARGO/CARDBOARD/ANIMAL/DEBRIS_BAG/CONSTRUCTION/METAL/PLASTIC/PAPER/GLASS/OTHER',
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
    INDEX idx_debris_category (debris_category),
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
    debris_category VARCHAR(32) DEFAULT NULL COMMENT '抛洒物子分类，来源alert_event.debris_category',
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
    INDEX idx_debris_category (debris_category),
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

INSERT INTO sys_user (username, password, nickname, phone, dept_id, role, status, create_time) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', '13800138000', 2, 0, 1, NOW()),
('operator', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '值班员', '13800138001', 1, 1, 1, NOW()),
('duty1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '白班值班员-王磊', '13800138003', 1, 1, 1, NOW()),
('duty2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '夜班值班员-李娜', '13800138004', 2, 1, 1, NOW());

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
    geom POINT COMMENT '空间坐标字段(MySQL GIS)',
    geom_pixel POINT COMMENT '像素坐标空间字段(归一化0~1)',
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
    INDEX idx_key_point (is_key_point),
    INDEX idx_track_time (track_id, frame_time) COMMENT '轨迹+时间复合索引，加速时间窗查询',
    SPATIAL INDEX idx_geom (geom) COMMENT '空间索引，加速空间范围查询',
    SPATIAL INDEX idx_geom_pixel (geom_pixel) COMMENT '像素空间索引'
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

INSERT INTO track_point (track_id, camera_id, camera_name, frame_no, frame_time,
    bbox_x1, bbox_y1, bbox_x2, bbox_y2, bbox_confidence,
    longitude, latitude, pixel_x, pixel_y,
    velocity_x, velocity_y, speed, direction,
    is_key_point, key_point_type, create_time) VALUES
(1, 1, '京港澳高速K100+500北', 1, '2025-06-18 10:19:55',
    100, 200, 180, 260, 0.95,
    116.397400, 39.904250, 0.15, 0.10,
    2.5, 0.0, 80.5, 180.0,
    1, 1, NOW()),
(1, 1, '京港澳高速K100+500北', 30, '2025-06-18 10:20:05',
    150, 250, 230, 310, 0.96,
    116.397400, 39.904000, 0.25, 0.35,
    2.8, 0.0, 82.3, 180.0,
    0, NULL, NOW()),
(1, 1, '京港澳高速K100+500北', 60, '2025-06-18 10:20:15',
    200, 300, 280, 360, 0.97,
    116.397400, 39.903750, 0.35, 0.55,
    3.0, 0.0, 85.1, 180.0,
    0, NULL, NOW()),
(1, 1, '京港澳高速K100+500北', 90, '2025-06-18 10:20:25',
    250, 350, 330, 410, 0.94,
    116.397400, 39.903500, 0.45, 0.75,
    2.7, 0.0, 81.6, 180.0,
    0, NULL, NOW()),
(1, 1, '京港澳高速K100+500北', 120, '2025-06-18 10:20:35',
    300, 400, 380, 460, 0.93,
    116.397400, 39.903250, 0.55, 0.90,
    2.6, 0.0, 78.9, 180.0,
    1, 3, NOW()),
(1, 2, '京港澳高速K100+500南', 130, '2025-06-18 10:20:40',
    100, 150, 180, 210, 0.92,
    116.397400, 39.903200, 0.10, 0.15,
    2.4, 0.0, 75.2, 180.0,
    1, 1, NOW()),
(1, 2, '京港澳高速K100+500南', 160, '2025-06-18 10:20:55',
    150, 200, 230, 260, 0.91,
    116.397400, 39.903000, 0.20, 0.40,
    2.3, 0.0, 72.8, 180.0,
    0, NULL, NOW()),
(1, 2, '京港澳高速K100+500南', 190, '2025-06-18 10:21:10',
    200, 250, 280, 310, 0.90,
    116.397400, 39.903300, 0.30, 0.65,
    2.2, 0.0, 70.5, 180.0,
    0, NULL, NOW()),
(1, 2, '京港澳高速K100+500南', 220, '2025-06-18 10:21:25',
    250, 300, 330, 360, 0.89,
    116.397400, 39.903150, 0.40, 0.85,
    2.1, 0.0, 68.3, 180.0,
    1, 2, NOW());

INSERT INTO alert_event (event_no, event_type, event_level, camera_id, camera_name,
    location, longitude, latitude, event_time, confidence,
    event_snapshot, description, alert_status,
    accident_vehicles, accident_deformation_level, accident_severity, accident_severity_label,
    accident_priority, create_time) VALUES
('EVT202506180001', 'ACCIDENT', 3, 1, '京港澳高速K100+500北',
    '京港澳高速K100+500北方向', 116.397400, 39.904200, '2025-06-18 10:20:35', 0.92,
    NULL, '车辆追尾事故，疑似有人受伤', 0,
    2, 2, 'GENERAL', '一般事故',
    2, NOW());

INSERT INTO event_track_link (event_id, event_no, track_id, track_no,
    link_type, link_confidence, camera_id, track_point_id, description, create_time) VALUES
(1, 'EVT202506180001', 1, 'TRK202506180001',
    1, 0.95, 1, 5, '事件发生时前方车辆', NOW());

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

ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS debris_category VARCHAR(32) DEFAULT NULL COMMENT '抛洒物子分类：TIRE/CARGO/CARDBOARD/ANIMAL/…' AFTER event_type;
ALTER TABLE alert_event ADD INDEX IF NOT EXISTS idx_debris_category (debris_category);

ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_vehicles INT DEFAULT NULL COMMENT '事故涉事车辆数' AFTER handle_remark;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_deformation_level INT DEFAULT NULL COMMENT '事故变形程度(0-4)' AFTER accident_vehicles;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_rollover INT DEFAULT NULL COMMENT '是否翻滚(0否1是)' AFTER accident_deformation_level;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_fire INT DEFAULT NULL COMMENT '是否起火(0否1是)' AFTER accident_rollover;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_casualty INT DEFAULT NULL COMMENT '伤亡人数' AFTER accident_fire;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_impact_speed DECIMAL(8,2) DEFAULT NULL COMMENT '碰撞车速km/h' AFTER accident_casualty;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_severity VARCHAR(16) DEFAULT NULL COMMENT '事故严重程度:SLIGHT/GENERAL/MAJOR' AFTER accident_impact_speed;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_severity_label VARCHAR(32) DEFAULT NULL COMMENT '事故严重程度标签' AFTER accident_severity;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_priority INT DEFAULT NULL COMMENT '事故响应优先级' AFTER accident_severity_label;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS accident_evaluation_reasons TEXT DEFAULT NULL COMMENT '事故评估理由' AFTER accident_priority;
ALTER TABLE alert_event ADD INDEX IF NOT EXISTS idx_accident_severity (accident_severity);

CREATE TABLE IF NOT EXISTS traffic_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT,
    camera_name VARCHAR(128),
    road_name VARCHAR(128),
    lane_no INT,
    lane_name VARCHAR(32),
    target_class VARCHAR(32),
    stat_time DATETIME NOT NULL,
    start_time DATETIME,
    end_time DATETIME,
    flow_volume INT DEFAULT 0,
    avg_speed DECIMAL(8,2) DEFAULT 0,
    min_speed DECIMAL(8,2) DEFAULT 0,
    max_speed DECIMAL(8,2) DEFAULT 0,
    speed_standard_deviation DECIMAL(8,2) DEFAULT 0,
    occupancy DECIMAL(8,4) DEFAULT 0,
    density DECIMAL(8,2) DEFAULT 0,
    avg_headway DECIMAL(8,2) DEFAULT 0,
    vehicle_count INT DEFAULT 0,
    aggregate_type VARCHAR(16) DEFAULT 'minute',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_camera_id (camera_id),
    INDEX idx_stat_time (stat_time),
    INDEX idx_lane_no (lane_no),
    INDEX idx_aggregate_type (aggregate_type),
    UNIQUE KEY uk_cam_lane_time (camera_id, lane_no, stat_time, aggregate_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_clip (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT,
    camera_name VARCHAR(128),
    clip_type VARCHAR(32) COMMENT '事件类型:ACCIDENT/DEBRIS/PARKING/PEDESTRIAN等',
    alert_event_id BIGINT,
    event_no VARCHAR(32),
    file_name VARCHAR(255),
    file_path VARCHAR(512) COMMENT 'MinIO对象路径(MP4)',
    file_url VARCHAR(1024) COMMENT 'MP4访问URL(含预签名)',
    hls_playlist_path VARCHAR(512) COMMENT 'MinIO HLS m3u8对象路径',
    hls_playlist_url VARCHAR(1024) COMMENT 'HLS播放URL(含预签名)',
    file_size BIGINT,
    duration INT COMMENT '时长(秒)',
    start_time DATETIME,
    end_time DATETIME,
    thumbnail_url VARCHAR(1024) COMMENT '缩略图URL',
    record_status INT DEFAULT 0 COMMENT '0未开始1录制中2成功-1失败',
    fail_reason VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_camera_id (camera_id),
    INDEX idx_event_id (alert_event_id),
    INDEX idx_clip_type (clip_type),
    INDEX idx_start_time (start_time),
    INDEX idx_event_no (event_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notify_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(64) NOT NULL UNIQUE COMMENT '渠道编码: DINGTALK/SMS/VOICE/WECHAT',
    channel_name VARCHAR(128) NOT NULL COMMENT '渠道名称',
    channel_type VARCHAR(32) NOT NULL COMMENT '渠道类型: DINGTALK/SMS/VOICE/WECHAT',
    enabled INT DEFAULT 1 COMMENT '是否启用 0禁用 1启用',
    config_json TEXT COMMENT '渠道配置JSON(如webhook/apiKey/templateId等)',
    description VARCHAR(512),
    sort_order INT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_channel_type (channel_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知渠道配置';

INSERT INTO notify_channel (channel_code, channel_name, channel_type, enabled, config_json, sort_order, create_time) VALUES
('DINGTALK_DEFAULT', '默认钉钉机器人', 'DINGTALK', 1, '{"webhook":"https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN","secret":"YOUR_SECRET","remark":"配置真实access_token和secret后即可推送"}', 1, NOW()),
('SMS_ALIYUN', '阿里云短信', 'SMS', 1, '{"accessKeyId":"YOUR_KEY","accessKeySecret":"YOUR_SECRET","signName":"交通告警","templateCode":"SMS_000000000","regionId":"cn-hangzhou","remark":"配置真实AK/SK即启用真实发送，否则本地模拟"}', 2, NOW()),
('VOICE_TTS', '阿里云语音TTS外呼', 'VOICE', 1, '{"accessKeyId":"YOUR_KEY","accessKeySecret":"YOUR_SECRET","ttsTemplateCode":"TTS_000000000","calledShowNumber":"4008000000","regionId":"cn-hangzhou","remark":"配置真实AK/SK即启用真实外呼，否则本地模拟"}', 3, NOW()),
('WECHAT_DEFAULT', '默认企业微信', 'WECHAT', 0, '{"webhook":"https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=YOUR_KEY","remark":"配置真实webhook后启用"}', 4, NOW());

CREATE TABLE IF NOT EXISTS notify_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(64) NOT NULL UNIQUE COMMENT '模板编码',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    channel_type VARCHAR(32) NOT NULL COMMENT '适用渠道类型: DINGTALK/SMS/VOICE/WECHAT',
    event_type VARCHAR(32) COMMENT '事件类型: ACCIDENT/REVERSE/DEBRIS/ALL',
    event_level INT COMMENT '事件等级: 1一般/2严重/3紧急/NULL全部',
    title_template VARCHAR(256) COMMENT '标题模板',
    content_template TEXT NOT NULL COMMENT '内容模板，支持变量: ${eventType}/${eventLevel}/${cameraName}/${location}/${eventTime}/${description}/${debrisCategory}/${accidentSeverity}',
    status INT DEFAULT 1 COMMENT '状态 0禁用 1启用',
    description VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_template_code (template_code),
    INDEX idx_channel_type (channel_type),
    INDEX idx_event_type (event_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知模板';

INSERT INTO notify_template (template_code, template_name, channel_type, event_type, event_level, title_template, content_template, status, create_time) VALUES
('TPL_DINGTALK_DEFAULT', '钉钉默认模板', 'DINGTALK', NULL, NULL, '交通事件告警', '【${levelText}】交通事件告警\n事件类型: ${eventTypeText}\n摄像头: ${cameraName}\n位置: ${location}\n时间: ${eventTime}\n置信度: ${confidence}%\n描述: ${description}', 1, NOW()),
('TPL_SMS_URGENT', '短信紧急模板', 'SMS', NULL, 3, '紧急告警', '【交通告警】紧急!${eventTypeText},${location},${eventTime},请立即处置', 1, NOW()),
('TPL_SMS_NORMAL', '短信普通模板', 'SMS', NULL, NULL, '交通告警', '【交通告警】${eventTypeText},${location},${eventTime}', 1, NOW()),
('TPL_VOICE_URGENT', '语音紧急外呼模板', 'VOICE', NULL, 3, '紧急语音告警', '紧急告警,${location}发生${eventTypeText},请立即处理', 1, NOW()),
('TPL_WECHAT_DEFAULT', '企微默认模板', 'WECHAT', NULL, NULL, '交通事件告警', '**【${levelText}】交通事件告警**\n> 事件类型: ${eventTypeText}\n> 摄像头: ${cameraName}\n> 位置: ${location}\n> 时间: ${eventTime}\n> 描述: ${description}', 1, NOW());

CREATE TABLE IF NOT EXISTS notify_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    event_type VARCHAR(32) COMMENT '事件类型: ACCIDENT/REVERSE/DEBRIS/NULL全部',
    event_level INT COMMENT '事件等级: 1/2/3/NULL全部',
    channel_id BIGINT NOT NULL COMMENT '通知渠道ID',
    template_id BIGINT COMMENT '通知模板ID',
    recipient_type INT DEFAULT 1 COMMENT '接收人类型: 1值班人员 2指定部门 3指定用户 4全部',
    recipient_ids VARCHAR(512) COMMENT '接收人ID列表，逗号分隔(部门ID/用户ID)',
    at_all INT DEFAULT 0 COMMENT '是否@所有人(钉钉)',
    enabled INT DEFAULT 1 COMMENT '是否启用',
    priority INT DEFAULT 0 COMMENT '优先级，数值越小越优先',
    sort_order INT DEFAULT 0,
    description VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_event_type (event_type),
    INDEX idx_event_level (event_level),
    INDEX idx_channel_id (channel_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知推送规则';

INSERT INTO notify_rule (rule_name, event_type, event_level, channel_id, template_id, recipient_type, recipient_ids, at_all, enabled, priority, sort_order, create_time) VALUES
('紧急事件-钉钉@所有', NULL, 3, 1, 1, 4, NULL, 1, 1, 0, 1, NOW()),
('紧急事件-短信通知值班', NULL, 3, 2, 2, 1, NULL, 0, 1, 1, 2, NOW()),
('紧急事件-语音外呼值班', NULL, 3, 3, 4, 1, NULL, 0, 1, 2, 3, NOW()),
('紧急事件-短信通知交警部门', NULL, 3, 2, 2, 2, '2', 0, 1, 2, 4, NOW()),
('严重事件-钉钉通知值班', NULL, 2, 1, 1, 1, NULL, 0, 1, 3, 5, NOW()),
('严重事件-短信通知值班', NULL, 2, 2, 3, 1, NULL, 0, 1, 4, 6, NOW()),
('一般事件-钉钉通知值班', NULL, 1, 1, 1, 1, NULL, 0, 1, 5, 7, NOW()),
('所有事件-通知管理员用户', NULL, NULL, 2, 3, 3, '1', 0, 1, 10, 8, NOW());

CREATE TABLE IF NOT EXISTS on_duty (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '值班人员用户ID',
    user_name VARCHAR(64) COMMENT '值班人员姓名',
    phone VARCHAR(32) COMMENT '值班手机号',
    dept_id BIGINT COMMENT '部门ID',
    dept_name VARCHAR(128) COMMENT '部门名称',
    duty_date DATE NOT NULL COMMENT '值班日期',
    duty_type INT DEFAULT 1 COMMENT '值班类型: 1白班 2夜班 3全天',
    start_time DATETIME COMMENT '值班开始时间',
    end_time DATETIME COMMENT '值班结束时间',
    status INT DEFAULT 1 COMMENT '状态: 0无效 1有效',
    remark VARCHAR(256),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_user_id (user_id),
    INDEX idx_duty_date (duty_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='值班人员排班';

INSERT INTO on_duty (user_id, user_name, phone, dept_id, dept_name, duty_date, duty_type, start_time, end_time, status, create_time) VALUES
(3, '白班值班员-王磊', '13800138003', 1, '高速养护一队', CURDATE(), 1, CONCAT(CURDATE(), ' 08:00:00'), CONCAT(CURDATE(), ' 20:00:00'), 1, NOW()),
(4, '夜班值班员-李娜', '13800138004', 2, '高速交警一大队', CURDATE(), 2, CONCAT(CURDATE(), ' 20:00:00'), CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 08:00:00'), 1, NOW()),
(3, '白班值班员-王磊', '13800138003', 1, '高速养护一队', DATE_ADD(CURDATE(), INTERVAL 1 DAY), 1, CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 08:00:00'), CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 20:00:00'), 1, NOW()),
(4, '夜班值班员-李娜', '13800138004', 2, '高速交警一大队', DATE_ADD(CURDATE(), INTERVAL 1 DAY), 2, CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 20:00:00'), CONCAT(DATE_ADD(CURDATE(), INTERVAL 2 DAY), ' 08:00:00'), 1, NOW());

CREATE TABLE IF NOT EXISTS notify_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_no VARCHAR(64) NOT NULL UNIQUE COMMENT '日志编号',
    alert_event_id BIGINT COMMENT '关联告警事件ID',
    event_no VARCHAR(64) COMMENT '事件编号',
    channel_id BIGINT COMMENT '通知渠道ID',
    channel_type VARCHAR(32) COMMENT '渠道类型',
    template_id BIGINT COMMENT '模板ID',
    recipient_type INT COMMENT '接收人类型',
    recipient_info VARCHAR(512) COMMENT '接收人信息(手机号/用户名等)',
    title VARCHAR(256) COMMENT '通知标题',
    content TEXT COMMENT '通知内容',
    send_status INT DEFAULT 0 COMMENT '发送状态: 0待发送 1发送中 2成功 3失败',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    max_retry INT DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    response_body TEXT COMMENT '渠道响应内容',
    error_message VARCHAR(1024) COMMENT '错误信息',
    send_time DATETIME COMMENT '实际发送时间',
    success_time DATETIME COMMENT '成功时间',
    cost_ms BIGINT COMMENT '耗时毫秒',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_log_no (log_no),
    INDEX idx_alert_event_id (alert_event_id),
    INDEX idx_channel_type (channel_type),
    INDEX idx_send_status (send_status),
    INDEX idx_next_retry (next_retry_time),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知推送日志';

CREATE TABLE IF NOT EXISTS plate_recognition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recognize_no VARCHAR(64) NOT NULL UNIQUE COMMENT '识别编号',
    alert_event_id BIGINT COMMENT '关联告警事件ID',
    event_no VARCHAR(64) COMMENT '事件编号',
    camera_id BIGINT COMMENT '摄像头ID',
    camera_name VARCHAR(128) COMMENT '摄像头名称',
    plate_number VARCHAR(32) COMMENT '车牌号',
    plate_color VARCHAR(16) COMMENT '车牌颜色',
    vehicle_color VARCHAR(32) COMMENT '车身颜色',
    vehicle_type VARCHAR(32) COMMENT '车辆类型',
    confidence DECIMAL(6,4) COMMENT '识别置信度',
    scene_type VARCHAR(16) COMMENT '场景类型 normal/night/backlight',
    enhance_gain DECIMAL(6,2) COMMENT '图像增强增益',
    track_id INT COMMENT '本地追踪ID',
    bbox_x1 INT,
    bbox_y1 INT,
    bbox_x2 INT,
    bbox_y2 INT,
    plate_image_url VARCHAR(512) COMMENT '车牌截取图',
    full_image_url VARCHAR(512) COMMENT '全景图',
    recognize_time DATETIME COMMENT '识别时间',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_recognize_no (recognize_no),
    INDEX idx_alert_event_id (alert_event_id),
    INDEX idx_event_no (event_no),
    INDEX idx_plate_number (plate_number),
    INDEX idx_camera_id (camera_id),
    INDEX idx_recognize_time (recognize_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车牌识别记录';

CREATE TABLE IF NOT EXISTS police_push (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    push_no VARCHAR(64) NOT NULL UNIQUE COMMENT '推送编号',
    alert_event_id BIGINT COMMENT '关联告警事件ID',
    event_no VARCHAR(64) COMMENT '事件编号',
    plate_recognition_id BIGINT COMMENT '车牌识别ID',
    event_type VARCHAR(32) COMMENT '事件类型',
    event_level INT COMMENT '事件等级',
    plate_number VARCHAR(32) COMMENT '车牌号',
    plate_color VARCHAR(16) COMMENT '车牌颜色',
    vehicle_type VARCHAR(32) COMMENT '车辆类型',
    location VARCHAR(512) COMMENT '事件位置',
    camera_id BIGINT COMMENT '摄像头ID',
    camera_name VARCHAR(128) COMMENT '摄像头名称',
    longitude DECIMAL(12,8),
    latitude DECIMAL(12,8),
    event_time DATETIME COMMENT '事件时间',
    push_status INT DEFAULT 0 COMMENT '推送状态 0待推 1推送中 2成功 3失败',
    retry_count INT DEFAULT 0,
    max_retry INT DEFAULT 5,
    next_retry_time DATETIME,
    push_target VARCHAR(64) COMMENT '推送目标标识(webhook url或系统代号)',
    push_body TEXT COMMENT '推送请求体(JSON)',
    response_body TEXT COMMENT '响应内容',
    error_message VARCHAR(1024),
    cost_ms BIGINT,
    push_time DATETIME,
    success_time DATETIME,
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_push_no (push_no),
    INDEX idx_alert_event_id (alert_event_id),
    INDEX idx_event_no (event_no),
    INDEX idx_plate_number (plate_number),
    INDEX idx_push_status (push_status),
    INDEX idx_next_retry (next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交警系统推送记录';

CREATE TABLE IF NOT EXISTS police_system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_code VARCHAR(64) NOT NULL UNIQUE COMMENT '系统代号',
    system_name VARCHAR(128) COMMENT '系统名称',
    push_url VARCHAR(512) COMMENT '推送接口地址',
    auth_type VARCHAR(32) DEFAULT 'NONE' COMMENT '认证方式 NONE/TOKEN/BASIC',
    auth_token VARCHAR(512) COMMENT '认证Token',
    basic_username VARCHAR(128) COMMENT 'Basic认证用户名',
    basic_password VARCHAR(256) COMMENT 'Basic认证密码',
    enabled INT DEFAULT 1 COMMENT '是否启用',
    retry_max INT DEFAULT 5 COMMENT '最大重试次数',
    retry_initial_seconds INT DEFAULT 10 COMMENT '初始重试间隔(秒)',
    retry_multiplier DECIMAL(4,2) DEFAULT 2.00,
    retry_max_seconds INT DEFAULT 300,
    timeout_seconds INT DEFAULT 10 COMMENT '请求超时(秒)',
    remark VARCHAR(512),
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_system_code (system_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交警系统对接配置';

INSERT INTO police_system_config (system_code, system_name, push_url, auth_type, enabled, retry_max, retry_initial_seconds, retry_multiplier, retry_max_seconds, timeout_seconds, remark, create_time) VALUES
('POLICE_SANDBOX', '沙箱测试-本地Mock交警系统', 'http://localhost:8080/api/mock/police/webhook', 'NONE', 1, 5, 30, 2.00, 900, 10, '开箱即用的沙箱测试，指向自身Mock接口，用于验证逆行链路推送', NOW()),
('TRAFFIC_POLICE_API', '省交警总队违法受理系统', 'https://police.example.com/api/v1/traffic-event/report', 'TOKEN', 0, 5, 10, 2.00, 300, 15, '默认配置，需填入真实push_url和auth_token后启用', NOW()),
('LOCAL_POLICE_BUREAU', '属地交警大队平台', 'https://local-police.example.com/api/event/upload', 'NONE', 0, 3, 30, 1.50, 600, 15, '备用推送目标', NOW());

INSERT INTO notify_template (template_code, template_name, channel_type, event_type, event_level, title_template, content_template, status, create_time) VALUES
('TPL_DINGTALK_REVERSE', '逆行-钉钉模板', 'DINGTALK', 'REVERSE', NULL, '逆行车辆告警', '【${levelText}】逆行车辆已识别车牌\n车牌号: ${plateNumber}(${plateColor})\n车辆类型: ${vehicleType}\n位置: ${location}\n摄像头: ${cameraName}\n时间: ${eventTime}\n置信度: ${confidence}%', 1, NOW()),
('TPL_SMS_REVERSE', '逆行-短信模板', 'SMS', 'REVERSE', NULL, '逆行告警', '【交通告警】逆行:${plateNumber},${location},${eventTime}', 1, NOW()),
('TPL_VOICE_REVERSE', '逆行-语音外呼模板', 'VOICE', 'REVERSE', 3, '逆行语音告警', '紧急告警:${location}发生逆行,车牌号${plateNumber},请立即处置', 1, NOW());

INSERT INTO notify_rule (rule_name, event_type, event_level, channel_id, template_id, recipient_type, recipient_ids, at_all, enabled, priority, sort_order, create_time) VALUES
('逆行-钉钉@值班', 'REVERSE', NULL, 1, (SELECT id FROM notify_template WHERE template_code='TPL_DINGTALK_REVERSE'), 1, NULL, 0, 1, 2, 9, NOW()),
('逆行-短信值班', 'REVERSE', NULL, 2, (SELECT id FROM notify_template WHERE template_code='TPL_SMS_REVERSE'), 1, NULL, 0, 1, 2, 10, NOW()),
('逆行-紧急语音外呼', 'REVERSE', 3, 3, (SELECT id FROM notify_template WHERE template_code='TPL_VOICE_REVERSE'), 1, NULL, 0, 1, 1, 11, NOW());

CREATE TABLE IF NOT EXISTS weather_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_time DATETIME NOT NULL COMMENT '记录时间',
    location_code VARCHAR(32) DEFAULT 'DEFAULT' COMMENT '区域编码',
    location_name VARCHAR(64) COMMENT '区域名称',
    longitude DECIMAL(10,6) COMMENT '中心经度',
    latitude DECIMAL(10,6) COMMENT '中心纬度',
    weather_type VARCHAR(16) NOT NULL COMMENT '天气类型: SUNNY/CLOUDY/RAIN/SNOW/FOG/HAZE',
    temperature DECIMAL(5,2) COMMENT '气温(摄氏度)',
    humidity DECIMAL(5,2) COMMENT '相对湿度(%)',
    wind_speed DECIMAL(6,2) COMMENT '风速(m/s)',
    wind_direction INT COMMENT '风向(0-360度)',
    visibility DECIMAL(6,2) COMMENT '能见度(km)',
    precipitation DECIMAL(6,2) COMMENT '降水量(mm)',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_record_time (record_time),
    INDEX idx_location (location_code),
    INDEX idx_weather_type (weather_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='天气数据';

CREATE TABLE IF NOT EXISTS event_prediction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prediction_no VARCHAR(64) NOT NULL UNIQUE COMMENT '预测编号',
    prediction_time DATETIME NOT NULL COMMENT '预测生成时间',
    target_start_time DATETIME NOT NULL COMMENT '预测窗口开始时间',
    target_end_time DATETIME NOT NULL COMMENT '预测窗口结束时间',
    target_hours INT DEFAULT 1 COMMENT '预测窗口小时数',
    camera_id BIGINT COMMENT '摄像头ID(路段标识)',
    camera_name VARCHAR(128) COMMENT '摄像头名称',
    road_name VARCHAR(128) COMMENT '路段名称',
    longitude DECIMAL(10,6) NOT NULL COMMENT '经度',
    latitude DECIMAL(10,6) NOT NULL COMMENT '纬度',
    geom POINT COMMENT '空间坐标字段(MySQL GIS)',
    risk_score DECIMAL(8,4) NOT NULL COMMENT '风险评分 0-100',
    risk_level INT NOT NULL COMMENT '风险等级: 1-低 2-中 3-高 4-极高',
    risk_level_label VARCHAR(16) COMMENT '风险等级标签',
    event_type VARCHAR(32) COMMENT '预测事件类型: ACCIDENT/DEBRIS/CONGESTION',
    event_type_label VARCHAR(32) COMMENT '事件类型标签',
    probability DECIMAL(6,4) COMMENT '事件发生概率',
    historical_event_count INT COMMENT '同条件历史事件数',
    weather_factor DECIMAL(6,4) COMMENT '天气影响因子',
    time_factor DECIMAL(6,4) COMMENT '时段影响因子',
    holiday_factor DECIMAL(6,4) COMMENT '节假日影响因子',
    feature_json TEXT COMMENT '特征向量JSON',
    confidence DECIMAL(6,4) COMMENT '预测置信度',
    status INT DEFAULT 1 COMMENT '状态: 0-无效 1-有效 2-已验证',
    actual_event_count INT DEFAULT 0 COMMENT '实际发生事件数',
    prediction_accuracy DECIMAL(6,4) COMMENT '预测准确率',
    description VARCHAR(512) COMMENT '预测说明',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_prediction_no (prediction_no),
    INDEX idx_prediction_time (prediction_time),
    INDEX idx_target_time (target_start_time, target_end_time),
    INDEX idx_camera_id (camera_id),
    INDEX idx_risk_level (risk_level),
    INDEX idx_status (status),
    SPATIAL INDEX idx_geom (geom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件预测结果';

INSERT INTO weather_data (record_time, location_code, location_name, longitude, latitude, weather_type, temperature, humidity, wind_speed, visibility, precipitation, create_time) VALUES
(NOW(), 'DEFAULT', '北京海淀区', 116.407400, 39.904200, 'SUNNY', 26.5, 45.0, 2.5, 15.0, 0.0, NOW()),
(DATE_SUB(NOW(), INTERVAL 1 HOUR), 'DEFAULT', '北京海淀区', 116.407400, 39.904200, 'SUNNY', 25.8, 48.0, 2.3, 15.0, 0.0, NOW()),
(DATE_SUB(NOW(), INTERVAL 2 HOUR), 'DEFAULT', '北京海淀区', 116.407400, 39.904200, 'SUNNY', 24.2, 52.0, 2.0, 12.0, 0.0, NOW());

CREATE TABLE IF NOT EXISTS edge_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_code VARCHAR(64) NOT NULL UNIQUE COMMENT '节点编码',
    node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
    hardware_model VARCHAR(128) COMMENT '硬件型号 如Jetson Xavier NX',
    gpu_info VARCHAR(256) COMMENT 'GPU信息',
    cpu_cores INT COMMENT 'CPU核心数',
    memory_gb INT COMMENT '内存GB',
    storage_gb INT COMMENT '存储GB',
    os_info VARCHAR(256) COMMENT '操作系统信息',
    ip_address VARCHAR(64) COMMENT 'IP地址',
    mac_address VARCHAR(64) COMMENT 'MAC地址',
    longitude DECIMAL(10,6) COMMENT '经度',
    latitude DECIMAL(10,6) COMMENT '纬度',
    location VARCHAR(512) COMMENT '位置描述',
    status INT DEFAULT 1 COMMENT '状态 0禁用 1启用',
    online_status INT DEFAULT 0 COMMENT '在线状态 0离线 1在线',
    last_heartbeat DATETIME COMMENT '最后心跳时间',
    heartbeat_interval INT DEFAULT 30 COMMENT '心跳间隔秒',
    cpu_usage DECIMAL(5,2) COMMENT 'CPU使用率%',
    memory_usage DECIMAL(5,2) COMMENT '内存使用率%',
    gpu_usage DECIMAL(5,2) COMMENT 'GPU使用率%',
    temperature DECIMAL(5,2) COMMENT '温度℃',
    camera_count INT DEFAULT 0 COMMENT '接入摄像头数量',
    event_count_today INT DEFAULT 0 COMMENT '今日事件数',
    description VARCHAR(512) COMMENT '描述',
    dept_id BIGINT COMMENT '所属部门ID',
    config_json TEXT COMMENT '节点配置JSON',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_node_code (node_code),
    INDEX idx_status (status),
    INDEX idx_online_status (online_status),
    INDEX idx_dept_id (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='边缘计算节点';

CREATE TABLE IF NOT EXISTS edge_node_heartbeat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    edge_node_id BIGINT NOT NULL COMMENT '边缘节点ID',
    node_code VARCHAR(64) NOT NULL COMMENT '节点编码',
    cpu_usage DECIMAL(5,2) COMMENT 'CPU使用率%',
    memory_usage DECIMAL(5,2) COMMENT '内存使用率%',
    gpu_usage DECIMAL(5,2) COMMENT 'GPU使用率%',
    temperature DECIMAL(5,2) COMMENT '温度℃',
    network_status INT DEFAULT 1 COMMENT '网络状态 0断网 1正常',
    disk_usage DECIMAL(5,2) COMMENT '磁盘使用率%',
    process_count INT COMMENT '运行进程数',
    camera_online_count INT COMMENT '在线摄像头数',
    event_queue_size INT COMMENT '待上传事件队列大小',
    extra_info TEXT COMMENT '扩展信息JSON',
    create_time DATETIME,
    INDEX idx_edge_node_id (edge_node_id),
    INDEX idx_node_code (node_code),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='边缘节点心跳日志';

CREATE TABLE IF NOT EXISTS edge_offline_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    edge_node_id BIGINT COMMENT '边缘节点ID',
    node_code VARCHAR(64) NOT NULL COMMENT '节点编码',
    event_uuid VARCHAR(64) NOT NULL UNIQUE COMMENT '事件唯一ID(边缘端生成)',
    event_data TEXT COMMENT '事件数据JSON',
    event_type VARCHAR(32) COMMENT '事件类型 ACCIDENT/REVERSE/DEBRIS等',
    event_time DATETIME NOT NULL COMMENT '事件发生时间',
    snapshot_path VARCHAR(512) COMMENT '本地截图路径',
    video_path VARCHAR(512) COMMENT '本地视频路径',
    upload_status INT DEFAULT 0 COMMENT '上传状态 0待上传 1上传中 2成功 3失败',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    max_retry INT DEFAULT 5 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    upload_time DATETIME COMMENT '上传成功时间',
    error_message VARCHAR(1024) COMMENT '错误信息',
    create_time DATETIME,
    update_time DATETIME,
    deleted INT DEFAULT 0,
    INDEX idx_edge_node_id (edge_node_id),
    INDEX idx_node_code (node_code),
    INDEX idx_event_uuid (event_uuid),
    INDEX idx_upload_status (upload_status),
    INDEX idx_event_time (event_time),
    INDEX idx_next_retry (next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='边缘节点离线事件(补传)';

INSERT INTO edge_node (node_code, node_name, hardware_model, gpu_info, cpu_cores, memory_gb, storage_gb, os_info,
    ip_address, mac_address, longitude, latitude, location, status, online_status, heartbeat_interval,
    camera_count, description, dept_id, create_time) VALUES
('EDGE-001', '京港澳高速K100边缘节点', 'NVIDIA Jetson Xavier NX', 'Xavier NX 384-core NVIDIA Volta GPU', 6, 8, 64, 'Ubuntu 20.04 LTS (JetPack 5.1)',
    '192.168.1.101', '00:04:4B:1A:2B:3C', 116.397400, 39.904200, '京港澳高速K100+500北方向机柜', 1, 1, 30,
    2, '负责K100段南北双向摄像头AI分析', 2, NOW()),
('EDGE-002', '京藏高速K50边缘节点', 'NVIDIA Jetson AGX Orin', 'Orin 2048-core NVIDIA Ampere GPU', 12, 32, 128, 'Ubuntu 20.04 LTS (JetPack 5.1.2)',
    '192.168.1.102', '00:04:4B:1A:2B:3D', 116.407400, 39.914200, '京藏高速K50+200东方向机柜', 1, 0, 30,
    2, '负责K50段东西双向摄像头AI分析', 2, NOW());

ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS source_node_code VARCHAR(64) DEFAULT NULL COMMENT '来源边缘节点编码' AFTER accident_evaluation_reasons;
ALTER TABLE alert_event ADD INDEX IF NOT EXISTS idx_source_node_code (source_node_code);

ALTER TABLE edge_offline_event ADD COLUMN IF NOT EXISTS alert_event_id BIGINT DEFAULT NULL COMMENT '关联告警事件ID' AFTER event_uuid;
ALTER TABLE edge_offline_event ADD COLUMN IF NOT EXISTS snapshot_url VARCHAR(512) DEFAULT NULL COMMENT '截图URL(MinIO)' AFTER video_path;
ALTER TABLE edge_offline_event ADD COLUMN IF NOT EXISTS video_url VARCHAR(512) DEFAULT NULL COMMENT '视频URL(MinIO)' AFTER snapshot_url;
ALTER TABLE edge_offline_event ADD INDEX IF NOT EXISTS idx_alert_event_id (alert_event_id);

CREATE TABLE IF NOT EXISTS patrol_route (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    route_name VARCHAR(128) NOT NULL COMMENT '路线名称',
    route_code VARCHAR(64) NOT NULL COMMENT '路线编码',
    description VARCHAR(512) DEFAULT NULL COMMENT '路线描述',
    status TINYINT DEFAULT 1 COMMENT '状态 0-停用 1-启用',
    stay_seconds INT DEFAULT 30 COMMENT '每个摄像头停留秒数',
    loop_mode TINYINT DEFAULT 0 COMMENT '循环模式 0-不循环 1-循环',
    create_user_id BIGINT DEFAULT NULL COMMENT '创建用户ID',
    create_user_name VARCHAR(64) DEFAULT NULL COMMENT '创建用户名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_route_code (route_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡逻路线表';

CREATE TABLE IF NOT EXISTS patrol_route_point (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    route_id BIGINT NOT NULL COMMENT '路线ID',
    camera_id BIGINT NOT NULL COMMENT '摄像头ID',
    camera_name VARCHAR(128) DEFAULT NULL COMMENT '摄像头名称',
    camera_code VARCHAR(64) DEFAULT NULL COMMENT '摄像头编码',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    stay_seconds INT DEFAULT 30 COMMENT '停留秒数',
    longitude DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
    latitude DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
    location VARCHAR(256) DEFAULT NULL COMMENT '位置描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_route_id (route_id),
    INDEX idx_camera_id (camera_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡逻路线点位表';

CREATE TABLE IF NOT EXISTS patrol_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    route_id BIGINT NOT NULL COMMENT '路线ID',
    route_name VARCHAR(128) DEFAULT NULL COMMENT '路线名称',
    start_user_id BIGINT DEFAULT NULL COMMENT '启动用户ID',
    start_user_name VARCHAR(64) DEFAULT NULL COMMENT '启动用户名称',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    execution_status TINYINT DEFAULT 0 COMMENT '执行状态 0-待执行 1-执行中 2-已完成 3-已中断',
    total_points INT DEFAULT 0 COMMENT '总点位数量',
    completed_points INT DEFAULT 0 COMMENT '已完成点位数量',
    detected_events TEXT DEFAULT NULL COMMENT '检测到的事件JSON',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_route_id (route_id),
    INDEX idx_execution_status (execution_status),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡逻执行日志表';

