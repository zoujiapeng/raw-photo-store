# RAWJudge

RAWJudge 是一个 **必须附带可读取 RAW 文件才能进入公开分区** 的 Android 摄影社区和公开评审系统。作品不会因为粉丝、点赞或流量获得更高质量分；公开分区由 RAW 证据、内容审核、盲评、评语质量、评审信誉和置信度共同决定。

当前版本：**0.2.0 可自托管测试版**。仓库包含可安装的 Android 客户端、FastAPI 后端、SQLite 开发存储、身份与权限控制、测试、Docker 配置和 GitHub Actions。它可以直接用于个人部署、封闭测试、作品集演示和 Agent 开发面试；公开商业运营仍需要支付、对象存储、人工审核、法务与生产风控。

## 核心能力

### Android 客户端

- Kotlin + Jetpack Compose，最低 Android 8.0（API 26）。
- 极简作品流、亮/暗主题、无文字控件和 98% 作品模式。
- 展厅、评审、工坊、归档四分区；社交数字不进入质量分。
- Android 文档选择器上传成片与 RAW，不申请整个相册权限。
- 后端真实匿名会话：上传、收藏、评论、盲评、举报、申诉、授权和下载均由服务端确认。
- 离线时只显示缓存或明确标注的官方样例，不伪造操作成功。
- 评论按相关性、专业性、技术性、客观性、建设性展示质量扇形图。
- 高质量评语放大、低信息评语折叠；无依据极端分可展示但不计入质量分。
- 用户可编辑显示名称、用户名和其他平台 `https://` 入口。

### FastAPI 后端

- 匿名 Bearer 会话，客户端不能自报作者、购买者或评审身份。
- 成片与 RAW 流式上传、大小限制、SHA-256、图片解码、像素上限和 RAW 文件头验证。
- 公共流只返回 `RAW 已验证 + reviewing/published` 的作品；未通过作品只对作者和管理员可见。
- 公共展示图由后端生成，去除 EXIF；原始成片和 RAW 只能由作者、管理员或获授权用户下载。
- 可选 OpenAI Responses 图像初评；没有 API Key 时使用确定性硬规则。
- 盲评隐藏作者、分数、粉丝和收藏；重复比较、快速批量操作和低信誉评审自动降权。
- 收藏幂等、重复举报/申诉拦截、所有权检查、审计轨迹和管理员复核 API。
- 开发环境可生成测试授权；生产环境中的付费授权在支付提供方确认前返回 HTTP 402，不会伪造付款。

官方样例始终带有 `official_sample=true`，用于空仓库冷启动，不代表真实用户投稿或真实相机文件，也不制造假评论、假购买和假账号。

## 目录

```text
raw-photo-store/
├── android/                  # Android Compose 客户端
├── backend/                  # FastAPI、SQLAlchemy、AI/规则服务与测试
├── docs/                     # 架构、部署、内容规则、隐私和授权草案
├── scripts/                  # 验证、开发数据重置、发布脚本
├── .github/workflows/        # 后端测试、Android APK 构建和 Release
├── docker-compose.yml
└── Makefile
```

## 五分钟启动

### 1. 启动后端

最省事的方式：

```bash
cp .env.example .env
docker compose up --build
```

服务启动后：

```bash
curl http://localhost:8000/health
curl http://localhost:8000/ready
```

交互式 API 文档位于 `http://localhost:8000/docs`。

没有 Docker 时：

```bash
make setup
make backend
```

### 2. 运行 Android

Android 模拟器默认连接 `http://10.0.2.2:8000`，因此后端运行后可直接：

```bash
./android/gradlew :app:installDebug
```

也可以用 Android Studio 打开 `android/`。需要 JDK 17+ 和 Android SDK 36。仓库中的 Gradle 启动器首次运行会下载 Gradle 9.3.1，并校验官方发行包的 SHA-256。

真机连接电脑上的后端时，将地址改成电脑的局域网 IP：

```bash
./android/gradlew \
  -PRAWJUDGE_API_BASE_URL=http://192.168.1.10:8000 \
  :app:assembleDebug
```

Release 构建只允许 HTTPS：

```bash
./android/gradlew \
  -PRAWJUDGE_API_BASE_URL=https://api.example.com \
  :app:assembleRelease
```

## 完整验证

```bash
make verify
```

该命令会执行 Ruff、后端 22 项测试、覆盖率门槛、Python 编译检查、Android JVM 测试并构建 Debug APK。APK 输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## API 最小示例

注册匿名会话：

```bash
curl -sS -X POST http://localhost:8000/v1/auth/anonymous \
  -H 'Content-Type: application/json' \
  -d '{"display_name":"摄影者","handle":"@photographer"}'
```

响应中的 `access_token` 是身份凭证。后续操作必须使用：

```bash
TOKEN='rj_...'

curl -X POST http://localhost:8000/v1/works \
  -H "Authorization: Bearer $TOKEN" \
  -F 'title=雨夜地铁口' \
  -F 'description=自然光，说明后期边界' \
  -F 'allow_preview=true' \
  -F 'allow_raw=true' \
  -F 'preview_price=0' \
  -F 'raw_price=0' \
  -F 'image=@photo.jpg' \
  -F 'raw=@photo.dng'
```

支持的展示图：JPEG、PNG、WebP。支持的 RAW 扩展名：DNG、CR2、CR3、NEF、ARW、RW2、ORF、RAF、PEF；扩展名和文件头必须匹配。此检查证明“存在可读取的 RAW 证据”，不等于证明作品绝对未经过生成式修改。

## AI 模式

默认不需要密钥。硬规则始终先执行；配置以下变量后，图像初评会调用 OpenAI Responses API：

```dotenv
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-5.6-luna
```

AI 只能提供风险标签、质量初值和下一步建议，不能绕过“必须存在 RAW”的服务端规则，也不能直接完成封禁、付款或版权裁决。

## 生产部署约束

设置 `APP_ENV=production` 后，服务会拒绝以下不安全配置：默认/过短的 `TOKEN_PEPPER`、通配 `TRUSTED_HOSTS`、通配 CORS、非 HTTPS 的 `PUBLIC_BASE_URL`。生产部署至少还应加入：

- Postgres、对象存储/CDN、异步任务队列和恶意文件扫描。
- 短期签名 URL、速率限制、设备/IP 风险信号和集中日志。
- 支付回调、作者结算、退款、税务和不可变授权快照。
- 人工审核后台、申诉 SLA、地区化内容政策和版权处理流程。
- Android Release 签名、AAB、隐私政策、商店合规与数据删除机制。

详见 [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) 和 [`docs/ROADMAP.md`](docs/ROADMAP.md)。

## License

Apache-2.0。用户上传的摄影作品、RAW 和授权条款不因本仓库许可证而改变权属。
