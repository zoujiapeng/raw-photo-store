# RAWJudge

RAWJudge 是一个 **必须附带 RAW 才能进入公开分区** 的摄影社区与公开评审系统。它不以粉丝、点赞或流量决定作品质量，而是通过文件证据、AI 初审、盲评、评语质量和可追溯审计共同形成质量分与置信度。

> 当前仓库是可运行的 0.1 版：Android 原生交互客户端 + 经过测试的 FastAPI 后端。官方占位作品全部明确标注；不会制造假用户、假评论、假购买或假点赞。

## 已实现

### Android

- Kotlin + Jetpack Compose 原生 Android 客户端。
- 极简沉浸式作品流，亮/暗主题、无文字控件、98% 作品模式。
- 四个质量分区：展厅、评审、工坊、归档。
- 成片 + RAW 文件选择；不申请整个相册的广泛读取权限。
- 本地 AI 审核代理：RAW 格式、疑似生成式处理线索、地区内容规则提示。
- 盲评：隐藏作者、粉丝、收藏和历史排名，采用小权重更新。
- 评论扇形质量图：相关性、专业性、技术性、客观性、建设性。
- 高质量评语放大，低信息评论折叠。
- 收藏、申诉、举报、授权快照和公开审计轨迹；收藏请求按用户幂等处理。
- 官方样例与真实测试账号明确区分。
- 投稿、评论、收藏、设置、申诉和授权状态保存在应用私有目录，重启后继续存在。

### 后端

- FastAPI + SQLAlchemy + SQLite 默认配置，可切换外部数据库。
- 成片与 RAW multipart 上传、SHA-256 记录、文件大小限制。
- RAW 格式硬规则和可替换的 AI Agent 审核层。
- 作品列表、详情、评论、收藏、盲评、举报、申诉、授权与审计 API。
- 评论质量分析和评审信誉权重。
- 快速批量盲评自动降权；同一账号 24 小时内重复比较同一作品对权重归零。
- 无依据的极端评分仍可作为评论保留/折叠，但不进入质量分。
- 授权后的 RAW 下载校验与重复购买保护。
- 可选 OpenAI Responses API 结构化输出；未配置密钥时自动使用确定性本地代理。
- 10 项 Pytest API 测试覆盖上传、RAW 规则、评论质量、幂等收藏、重复盲评、申诉、举报、授权与受控 RAW 下载。

## 仓库结构

```text
rawjudge/
├── android/                 # Android 原生客户端
├── backend/                 # FastAPI 服务
├── docs/                    # 产品、架构、Agent 与部署文档
├── .github/workflows/       # CI 与 GitHub Release
├── docker-compose.yml
└── README.md
```

## 快速运行后端

```bash
cp .env.example .env
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'
pytest
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

接口文档：启动后打开 `/docs`。展示图通过 `/v1/works/{id}/image` 返回；RAW 必须先获得 `raw_study` 授权，再以 buyer_id 访问 `/v1/works/{id}/raw`。

Docker：

```bash
docker compose up --build
```

## 运行 Android

用 Android Studio 打开 `android/`，使用 JDK 17 或更高版本，安装 Android SDK 36 后运行 `app`。

命令行（已安装 Gradle 9.3.1）：

```bash
gradle -p android :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions 会自动安装 Gradle、运行 Android 单元测试并生成 Debug APK，因此仓库本身不依赖本机已有 Gradle Wrapper 二进制。

## API 示例

上传成片与 RAW：

```bash
curl -X POST http://localhost:8000/v1/works \
  -F 'title=雨夜地铁口' \
  -F 'description=自然光，轻度色彩调整' \
  -F 'author_name=摄影者' \
  -F 'handle=@photographer' \
  -F 'allow_raw=true' \
  -F 'image=@photo.jpg' \
  -F 'raw=@photo.dng'
```

提交具体评语：

```bash
curl -X POST http://localhost:8000/v1/works/1/reviews \
  -H 'Content-Type: application/json' \
  -d '{
    "reviewer_id":"reviewer-1",
    "author_name":"评审者",
    "body":"主体与右侧高光重叠，建议降低曝光并保留 RAW 暗部层次。",
    "score":82
  }'
```

## AI 模式

默认不需要任何密钥，使用确定性本地代理完成演示和测试。配置以下环境变量后，后端会调用 OpenAI Responses API，并要求模型按照 JSON Schema 返回结构化结果：

```bash
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-5.6-luna
```

硬规则仍由后端决定：模型不能仅凭文件名宣称 RAW 真实性已经得到证明。

## 质量与反作弊原则

质量分不是点赞平均值，也不是所有评分的简单平均值。首版权重由评语质量、评审信誉、行为频率和盲评比较共同决定。新账号权重低；短时间大量操作降权；极端分数需要具体评论；社交关系数据不进入质量分。

## 当前边界

这不是已经具备小红书规模的生产平台。0.1 版完成了核心产品闭环和真实状态变化，但正式公开运营前仍需补充：账号认证、对象存储/CDN、成片与 RAW 像素级一致性检测、人工审核后台、设备/IP 风控、支付结算、版权合同模板、地区化合规、推送、搜索和生产监控。详见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。 内容治理、隐私与授权草案见 [`docs/CONTENT_POLICY.md`](docs/CONTENT_POLICY.md)、[`docs/PRIVACY_DRAFT.md`](docs/PRIVACY_DRAFT.md) 和 [`docs/LICENSE_TERMS.md`](docs/LICENSE_TERMS.md)。

## License

Apache-2.0
