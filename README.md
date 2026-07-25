# RAWJudge

RAWJudge 是一个 **必须附带可读取 RAW 文件才能进入公开分区** 的 Android 摄影社区与公开评审系统。作品不会因为粉丝、点赞或流量获得更高质量分；公开分区由 RAW 证据、内容审核、盲评、评语质量、评审信誉和置信度共同决定。

当前版本：**0.3.0 可自托管测试版**。仓库包含 Android 客户端、FastAPI 后端、SQLite 开发存储、Bearer 会话、权限控制、Docker 配置、自动测试和 APK 构建工作流。它可直接用于个人部署、封闭测试、作品集演示和 Agent 开发面试；公开商业运营仍需支付服务、对象存储、人工审核、法务与生产风控。

## 已实现

### Android 客户端

- Kotlin + Jetpack Compose，最低 Android 8.0（API 26）。
- 极简作品流、亮/暗主题、无文字控件和 98% 作品模式。
- 展厅、评审、工坊、归档四分区；社交数字不进入质量分。
- Android 文档选择器上传 JPEG/PNG/WebP 成片与 RAW，不申请整个相册权限。
- 首次启动自动创建匿名 Bearer 会话，之后恢复同一会话。
- 上传、收藏、评论评分、盲评、举报、申诉、授权和下载均等待服务端真实响应。
- 离线时只显示缓存或明确标注的官方占位作品，不伪造操作成功。
- 高质量评语放大，低信息评语折叠；无依据极端分和作者自评不计入质量分。
- 用户可编辑显示名称、用户名和其他平台 HTTPS 入口。

### FastAPI 后端

- 匿名 Bearer 会话；客户端不能自报作者、购买者、举报者或评审身份。
- 成片与 RAW 流式上传、大小限制、SHA-256、图片解码、像素上限和 RAW 文件头验证。
- 公共流只返回官方样例或 `RAW 已验证 + reviewing/published` 的作品；未通过作品仅作者和管理员可见。
- 后端生成去 EXIF 的公开 JPEG 预览；原始成片与 RAW 仅作者、管理员或获授权用户可下载。
- 盲评只返回作品 ID、预览、RAW 状态与置信度，不返回作者、标题、分数或社交数据。
- 收藏幂等，重复盲评权重归零，重复举报/申诉拦截，作者不能给自己的作品计权。
- 免费授权可以真实生成授权快照；未配置支付服务时，付费授权返回 HTTP 402，不制造假付款。
- 可选 OpenAI 初评；没有 API Key 时使用确定性硬规则。AI 不可绕过 RAW、权限、支付或人工复核规则。

官方样例始终标注 `official_sample=true`，所有社交数字为零，AI 样例评语不计入真实评分。

## 目录

```text
raw-photo-store/
├── android/                  # Android Compose 客户端
├── backend/                  # FastAPI、SQLAlchemy、AI/规则服务与测试
├── docs/                     # 架构、部署、内容规则、隐私和授权草案
├── scripts/                  # 验证、开发数据重置、发布脚本
├── .github/workflows/        # CI、APK 构建和 Beta Release
├── docker-compose.yml
└── Makefile
```

## 本地启动

### 1. 启动后端

```bash
cp .env.example .env
docker compose up --build
```

检查服务：

```bash
curl http://localhost:8000/health
curl http://localhost:8000/ready
```

API 文档：`http://localhost:8000/docs`

没有 Docker 时：

```bash
make setup
make backend
```

### 2. 构建 Android

Android 模拟器默认连接宿主机 `http://10.0.2.2:8000`：

```bash
bash android/gradlew :app:installDebug
```

首次执行会下载 Gradle 9.3.1，并校验发行包 SHA-256。也可以使用 Android Studio 打开 `android/`。需要 JDK 17+ 和 Android SDK 36。

真机连接电脑上的后端时，编译时传入电脑的局域网地址：

```bash
bash android/gradlew \
  -PRAWJUDGE_API_BASE_URL=http://192.168.1.10:8000 \
  :app:assembleDebug
```

正式 Release 必须使用 HTTPS：

```bash
bash android/gradlew \
  -PRAWJUDGE_API_BASE_URL=https://api.example.com \
  :app:assembleRelease
```

## 完整验证

```bash
make verify
```

验证内容：

- 11 项后端身份、权限、上传、RAW、盲评、授权和文件访问测试。
- Python 源码编译检查。
- Android JVM 测试任务。
- Android Debug APK 构建。

APK 输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

每次 PR 的 GitHub Actions 也会上传 `rawjudge-debug-apk` 构建产物。

## API 示例

注册匿名会话：

```bash
curl -sS -X POST http://localhost:8000/v1/auth/anonymous \
  -H 'Content-Type: application/json' \
  -d '{"display_name":"摄影者","handle":"@photographer"}'
```

后续请求使用响应中的 token：

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

支持的展示图：JPEG、PNG、WebP。支持的 RAW 扩展名：DNG、CR2、CR3、NEF、ARW、RW2、ORF、RAF、PEF；扩展名和文件头必须匹配。当前检查证明存在格式合理的 RAW 证据，不等于最终证明作品完全未经过生成式修改。

## AI 模式

默认不需要密钥。配置后，AI 可补充风险标签、质量初值和审核提示：

```dotenv
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-5.6-luna
```

## 生产部署边界

`APP_ENV=production` 会拒绝默认/过短的 `TOKEN_PEPPER`、通配 `TRUSTED_HOSTS`、通配 CORS 和非 HTTPS 的 `PUBLIC_BASE_URL`。公开运营还应加入：

- Postgres、私有对象存储/CDN、异步任务队列和恶意文件扫描。
- 限流、设备/IP 风险信号、集中日志、备份与恢复演练。
- 支付 webhook、作者结算、退款、税务和不可变授权记录。
- 人工审核后台、地区化内容政策、版权处理与数据删除流程。
- Android Release 签名、AAB、崩溃报告、隐私政策和应用商店合规。

详见 [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) 与 [`docs/ROADMAP.md`](docs/ROADMAP.md)。

## License

Apache-2.0。用户上传的摄影作品、RAW 和授权条款不因本仓库许可证而改变权属。
