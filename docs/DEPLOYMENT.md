# 部署指南

## 本地/封闭测试

```bash
cp .env.example .env
docker compose up --build
```

Docker volume 保存 SQLite 数据库和上传文件。健康检查：

```bash
curl http://localhost:8000/health
curl http://localhost:8000/ready
```

Android 模拟器默认连接宿主机 `10.0.2.2:8000`。真机使用宿主机局域网 IP，并确保防火墙允许 8000 端口。

## 从 0.1 原型升级

0.2 引入真实用户身份、所有权、私有资源和公开预览字段。旧原型数据库没有这些不可空字段，不会被 `create_all` 自动迁移。开发环境执行：

```bash
./scripts/reset-dev-data.sh
```

需要保留真实数据时，不要重置；应先建立 Alembic 迁移并为旧记录绑定真实所有者。

## 生产环境变量

最少配置：

```dotenv
APP_ENV=production
DATABASE_URL=postgresql+psycopg://...
UPLOAD_DIR=/private/uploads
PUBLIC_BASE_URL=https://api.example.com
TRUSTED_HOSTS=api.example.com
CORS_ORIGINS=https://admin.example.com
TOKEN_PEPPER=<至少 32 位随机密钥>
ALLOW_REGISTRATION=true
SEED_DEMO_DATA=false
ADMIN_TOKEN=<单独生成的管理员 Bearer token>
CONTENT_POLICY_PROFILE=global
```

Docker 镜像已包含 Psycopg 3 PostgreSQL 驱动。非 Docker 部署若使用 Postgres，请安装 `pip install -e './backend[postgres]'`。

生产模式会拒绝默认/过短的 token pepper、通配 trusted hosts、通配 CORS 和非 HTTPS 公网地址。

## 推荐生产架构

- Postgres 保存用户、作品、评分、评论、授权和审计元数据。
- S3/R2/OSS 等私有对象存储保存原始成片、RAW 和派生预览。
- Redis + Celery/Arq/队列处理 EXIF、缩略图、病毒扫描、AI 审核和 RAW 一致性计算。
- CDN 只分发公开派生预览；原始成片和 RAW 使用短期签名 URL。
- API Gateway/WAF 提供 TLS、限流、请求体限制、IP/设备风险信号和集中日志。
- 支付服务以签名 webhook 确认付款后再写入授权快照；不要由客户端声称付款完成。
- 备份同时覆盖数据库与对象存储，并定期演练恢复。

当前仓库的 SQLite + 文件系统模式是单机测试方案，不适合多实例部署。

## Android 发布

推送 `v*` 标签会运行后端测试、Android JVM 测试、构建可安装 Debug Beta APK，并创建 GitHub Release。正式上架需要另外配置：

- Release keystore 与 GitHub Encrypted Secrets。
- `assembleRelease`/`bundleRelease` 签名配置。
- HTTPS API 地址。
- Play Console Data Safety、隐私政策、内容举报/屏蔽和数字内容支付规则。
- 混淆、崩溃报告、版本升级和数据删除流程。
