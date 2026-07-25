# 部署

## 本地封闭测试

```bash
cp .env.example .env
docker compose up --build
```

SQLite 与上传文件保存在 Docker volume。检查：

```bash
curl http://localhost:8000/health
curl http://localhost:8000/ready
```

Android 模拟器默认连接 `10.0.2.2:8000`。真机使用电脑局域网地址重新构建 APK：

```bash
bash android/gradlew \
  -PRAWJUDGE_API_BASE_URL=http://192.168.1.10:8000 \
  :app:assembleDebug
```

## 开发数据迁移

0.3.0 增加用户、会话、所有权、私有资源和公开预览字段。旧原型数据库没有这些字段，SQLAlchemy `create_all` 不会自动迁移已有表。无真实数据的开发环境执行：

```bash
bash scripts/reset-dev-data.sh
```

需要保留数据时，应先建立 Alembic 迁移，并为旧作品绑定真实用户；不要直接重置。

## 生产环境变量

至少配置：

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

生产模式会拒绝默认或过短的 token pepper、通配 Host/CORS 和非 HTTPS 的公网地址。

## 推荐生产架构

- Postgres 保存用户、作品、评分、评论、授权和审计元数据。
- S3/R2/OSS 等私有对象存储保存成片、RAW 和派生预览。
- Redis + Celery/Arq/队列处理 EXIF、缩略图、病毒扫描、AI 审核和 RAW 一致性计算。
- CDN 仅分发公开派生预览；原始成片和 RAW 使用短期签名 URL。
- API Gateway/WAF 提供 TLS、限流、请求体限制、IP/设备风险信号和集中日志。
- 支付服务通过签名 webhook 确认付款后再创建授权快照；客户端不得声称付款成功。
- 数据库和对象存储应同时备份，并定期演练恢复。

当前 SQLite + 文件系统模式适合单机封闭测试，不适合多实例部署。

## Android 发布

推送 `v*` 标签会触发 `release.yml`：验证后端、构建 Docker 镜像、运行 Android 测试、生成 Debug Beta APK 和 SHA-256，并创建 GitHub prerelease。

可在仓库 Actions Variables 中设置：

```text
RAWJUDGE_API_BASE_URL=https://api.example.com
```

正式 Play 发布还需要签名 Release AAB、受保护的 keystore、混淆、崩溃报告、隐私政策、内容举报/屏蔽、数字内容支付规则和数据删除流程。
