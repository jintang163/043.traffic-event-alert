# Traffic Edge Node SDK

交通事件预警系统 - 边缘计算节点 Python SDK

## 功能特性

- **边缘节点注册**: 自动注册到服务端，上报节点信息
- **事件提交与缓存**: 在线时直接上传，离线时本地缓存，网络恢复后自动补传
- **本地SQLite缓存**: 持久化存储待上传事件，支持失败重试与过期清理
- **心跳上报**: 定时上报节点状态（CPU、内存、GPU、温度、网络状态、事件队列大小）
- **网络状态监控**: 后台线程检测网络连通性，状态变化时触发回调
- **智能重试**: 指数退避重试策略，避免服务端压力
- **线程安全**: 所有模块均考虑多线程并发访问安全
- **完善日志**: 详细的日志输出，便于问题排查

## 目录结构

```
traffic-edge-node/
├── edge_sdk/
│   ├── __init__.py          # 模块导出
│   ├── client.py            # 核心客户端，整合所有模块
│   ├── cache.py             # 本地SQLite事件缓存
│   ├── heartbeat.py         # 心跳管理线程
│   ├── uploader.py          # 事件上传与补传线程
│   ├── network_monitor.py   # 网络状态监控
│   └── config.py            # 配置管理
├── config.example.json      # 示例配置文件
├── requirements.txt         # 依赖列表
├── example.py               # 使用示例
└── README.md                # 项目说明
```

## 安装

### 环境要求

- Python 3.8+

### 安装依赖

```bash
pip install -r requirements.txt
```

## 快速开始

### 1. 配置文件

复制 `config.example.json` 为 `config.json` 并根据实际情况修改：

```json
{
  "server_url": "http://your-server:8080",
  "node_code": "EDGE-001",
  "node_name": "京港澳高速K100边缘节点",
  "heartbeat_interval": 30,
  "cache_db_path": "./edge_cache.db",
  "retry_max": 5,
  "retry_initial_delay": 10
}
```

### 2. 基础使用

```python
import json
import time
import logging
from edge_sdk import EdgeNodeClient, EdgeNodeConfig

logging.basicConfig(level=logging.INFO)

config = EdgeNodeConfig.from_json_file("config.json")
client = EdgeNodeClient(config=config)

client.register()
client.start()

event_data = {
    "cameraId": 1001,
    "description": "交通事故检测",
    "confidence": 0.92,
    "severity": "high",
}

result = client.submit_event(
    event_type="accident",
    event_data=event_data,
    snapshot_path="/path/to/snapshot.jpg",
    video_path="/path/to/event.mp4",
)

print(f"Event: {result['event_uuid']}, uploaded: {result['uploaded']}")

time.sleep(60)
client.stop()
```

### 3. 运行示例

```bash
python example.py
```

## 配置说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| server_url | str | http://localhost:8080 | 服务端地址 |
| node_code | str | EDGE-001 | 节点唯一编码 |
| node_name | str | 边缘节点 | 节点显示名称 |
| heartbeat_interval | int | 30 | 心跳间隔（秒） |
| cache_db_path | str | ./edge_cache.db | SQLite缓存数据库路径 |
| retry_max | int | 5 | 最大重试次数 |
| retry_initial_delay | int | 10 | 初始重试延迟（秒，指数退避） |

## 服务端API约定

SDK默认调用以下服务端接口，可在 `EventUploader` 中配置：

| 接口 | 方法 | 说明 |
|------|------|------|
| /health | GET | 健康检查（网络监控用） |
| /api/edge/nodes/register | POST | 节点注册 |
| /api/edge/nodes/heartbeat | POST | 心跳上报 |
| /api/edge/events | POST | 事件上传（支持multipart文件） |

### 事件上传请求格式

```json
{
  "nodeCode": "EDGE-001",
  "eventUuid": "uuid-hex-string",
  "eventType": "accident",
  "eventTime": "2024-01-01T12:00:00",
  "eventData": "{...json string...}"
}
```

上传图片/视频时使用 multipart/form-data：
- `data`: 上述JSON字符串
- `snapshot`: 截图文件（可选）
- `video`: 视频文件（可选）

### 心跳数据格式

```json
{
  "nodeCode": "EDGE-001",
  "nodeName": "京港澳高速K100边缘节点",
  "timestamp": "2024-01-01T12:00:00",
  "networkStatus": "online",
  "eventQueueSize": 0,
  "cpu_percent": 25.5,
  "memory_percent": 60.2,
  "memory_used_mb": 4096.0,
  "memory_total_mb": 8192.0,
  "gpu_percent": 0.0,
  "gpu_memory_percent": 0.0,
  "cpu_temp_c": 45.0,
  "disk_percent": 35.0
}
```

## 模块说明

### EdgeNodeClient (client.py)

核心客户端，整合所有模块，提供统一的对外接口。

主要方法：
- `register()`: 注册节点到服务端
- `submit_event()`: 提交事件（在线直接上传，离线写入缓存）
- `start()`: 启动所有后台线程
- `stop()`: 优雅停止所有线程
- `get_status()`: 获取节点运行状态

### EventCache (cache.py)

基于 SQLite 的本地事件缓存，线程安全。

主要方法：
- `add_event()`: 插入待上传事件
- `get_pending_events()`: 获取待上传事件
- `mark_uploading()`: 标记为上传中
- `mark_success()`: 标记上传成功
- `mark_failed()`: 标记失败并更新重试计数
- `get_queue_size()`: 获取待上传队列大小
- `cleanup_old_events()`: 清理已上传的历史记录

### NetworkMonitor (network_monitor.py)

网络状态监控，通过 HTTP 请求检测与服务端的连通性。

主要方法：
- `is_online()`: 检查当前网络是否可用
- `start_monitoring(callback)`: 启动后台监控，状态变化时调用回调
- `stop()`: 停止监控

### HeartbeatManager (heartbeat.py)

心跳管理，定时采集系统指标并上报。

采集的指标：
- CPU 使用率
- 内存使用率/用量/总量
- GPU 使用率/显存使用率（通过 nvidia-smi）
- CPU 温度
- 磁盘使用率
- 网络状态
- 事件队列大小

### EventUploader (uploader.py)

事件上传管理，负责：
- 直接上传新事件
- 从缓存批量补传待上传事件
- 指数退避重试策略
- 注册节点与心跳上报

## 异常处理与可靠性

1. **网络异常**: 自动检测网络状态，离线时写入本地缓存
2. **上传失败**: 标记失败并记录重试次数，按指数退避重试
3. **进程重启**: 缓存持久化在 SQLite，重启后继续补传
4. **线程安全**: 所有共享资源均通过锁保护
5. **优雅停止**: 收到停止信号后等待当前任务完成

## License

Internal use only.
