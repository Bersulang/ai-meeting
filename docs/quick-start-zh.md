# 快速启动指南

本文档提供两条路径：

- Docker 演示链路：适合仓库展示和快速验证服务可启动
- 本地开发链路：适合调试后端代码、连接现有数据库和联调前端

## 1. 先决条件

- JDK 17+
- Docker / Docker Compose（若走容器化）
- MySQL 8+
- MongoDB 7+
- Redis 7+

## 2. Docker 演示链路

在仓库根目录执行：

```bash
docker compose up -d --build
curl http://localhost:8002/actuator/health
```

成功后应返回：

```json
{"status":"UP"}
```

说明：

- `docker-compose.yml` 会拉起 MySQL、MongoDB、Redis 和 backend
- 仓库已挂载 `admin/src/main/resources/sql/` 作为增量 SQL 初始化目录
- 如果你需要完整跑通全部业务功能，仍需要补齐你自己的基础库结构和业务种子数据

## 3. 本地开发链路

### 3.1 启动依赖服务

确保以下服务已运行：

- MySQL：`127.0.0.1:3306`
- MongoDB：`127.0.0.1:27017`
- Redis：`127.0.0.1:6379`

### 3.2 构建与启动

```bash
./mvnw -B -ntp clean verify
./mvnw -B -ntp -pl admin -am spring-boot:run
```

### 3.3 健康检查

```bash
curl http://localhost:8002/actuator/health
```

## 4. 常用环境变量

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `SERVER_PORT` | 服务端口 | `8002` |
| `MYSQL_HOST` | MySQL 主机 | `127.0.0.1` |
| `MYSQL_PORT` | MySQL 端口 | `3306` |
| `MYSQL_DATABASE` | MySQL 库名 | `mainshi_agent` |
| `MYSQL_USERNAME` | MySQL 用户名 | `root` |
| `MYSQL_PASSWORD` | MySQL 密码 | `123456` |
| `MONGODB_HOST` | MongoDB 主机 | `127.0.0.1` |
| `MONGODB_PORT` | MongoDB 端口 | `27017` |
| `MONGODB_DATABASE` | MongoDB 库名 | `xunzhi_agent` |
| `REDIS_HOST` | Redis 主机 | `127.0.0.1` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `XUNZHI_STORAGE_BASE_DIR` | 运行时目录根路径 | `${user.home}/.xunzhi-agent` |
| `XUNZHI_LOG_DIR` | 日志目录 | `${XUNZHI_STORAGE_BASE_DIR}/logs` |

## 5. 运行时目录

后端运行时会自动创建以下目录：

- 上传临时目录：`${XUNZHI_STORAGE_BASE_DIR}/temp/upload`
- 音频临时目录：`${XUNZHI_STORAGE_BASE_DIR}/temp/audio`
- 日志目录：`${XUNZHI_STORAGE_BASE_DIR}/logs`

这样做的目的，是避免把临时文件和日志写回源码仓库。
