# 架构

## 核心闭环

1. Android 通过文档选择器选择成片与 RAW。
2. Bearer 会话确定真实服务端用户，客户端不能提交作者 ID。
3. 上传服务流式保存文件、限制字节数、验证图片像素/解码、检查 RAW 扩展名与文件头并记录 SHA-256。
4. 后端生成去 EXIF 的公开 JPEG；原始成片和 RAW 保持私有。
5. 硬规则先决定 RAW 缺失、文件不匹配和地区内容风险；可选图像 Agent 只补充风险标签与质量初值。
6. 只有 `raw_verified=true` 且状态为 `reviewing/published` 的作品进入公开流。
7. 盲评隐藏作者、分数、粉丝、收藏和历史结果。
8. 评论服务计算相关性、专业性、技术性、客观性、建设性；低质量或无依据极端分不计权。
9. 评分服务更新质量分 `Q` 与置信度 `C`，再由分区策略决定展厅、评审、工坊或归档。
10. 收藏、评论、盲评、举报、申诉、管理员复核和授权写入审计轨迹。

## Android

单 Activity + Compose。`RawJudgeViewModel` 负责远端同步、会话、离线只读缓存和操作状态；UI 不直接决定后端质量分。

- `RawJudgeApi` 使用 `HttpURLConnection`，避免额外网络框架依赖。
- `SessionStore` 保存匿名 Bearer token；不进入云备份。
- `LocalStateStore` 仅缓存可展示状态，恢复后默认离线。
- `PhotoCanvas` 加载本地 URI 或后端派生预览；失败时显示明确的官方生成占位图。
- Release 禁止明文 HTTP；Debug 允许连接本地开发 API。

下一阶段可加入 Room、WorkManager 断点上传、分页、推送和账号恢复。

## 后端

当前为可测试的 FastAPI 单体：

- Auth
- Upload & Preview
- Moderation Orchestrator
- Review & Scoring
- Blind Review / Anti-abuse
- Appeal & Report
- Licensing & Private Download
- Audit
- Admin Moderation

数据库表：`users`、`works`、`reviews`、`favorites`、`audit_events`、`vote_events`、`license_grants`、`reports`、`appeals`。

## 信任边界

服务端是所有权、可见性、评分权重和授权的唯一权威。Android 只能请求操作，不能直接提交：作者 ID、评审信誉、是否已付款、RAW 是否验证、作品分区或管理员状态。

RAW 文件头和哈希只是证据完整性检查，不证明照片必然来自某台相机，也不证明后期没有生成式替换。生产版需要 RAW 解码、派生预览对齐、EXIF/时间线分析和人工复核。
