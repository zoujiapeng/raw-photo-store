# 架构

## 核心闭环

1. 用户选择成片与 RAW。
2. 上传服务流式保存文件并记录 SHA-256。
3. Upload Integrity Agent 检查 MIME、扩展名、大小、hash 和可用 EXIF。
4. 内容安全与地区规则 Agent 给出风险标签，不允许只返回模糊的“违规”。
5. RAW Consistency Agent 对 RAW 派生预览与成片做一致性比较。
6. 作品进入盲评队列，作者和社交数字被隐藏。
7. 评论 Agent 分析相关性、专业性、技术性、客观性和建设性。
8. Scoring Service 使用评审信誉和评论质量的小权重更新质量分与置信度。
9. Partition Policy 决定展厅、评审、工坊或归档。
10. 每一步写入公开审计轨迹；举报、申诉和授权同样留痕。

## Android

Android 客户端采用单 Activity + Compose。`RawJudgeViewModel` 是 0.1 版状态容器，领域规则位于 `domain/`，UI 不直接计算质量分。首版将完整 UI 状态序列化到应用私有目录，保证重启后仍保留投稿与交互；这只是单设备方案。生产版本建议：

- Repository 接口分离本地缓存与远端 API。
- Room 保存作品摘要、评论和审计缓存。
- WorkManager 处理大 RAW 的断点上传。
- Android Photo Picker 选择成片，Storage Access Framework 选择 RAW。
- Play Integrity/App Check 只作为风控信号，不作为唯一封禁依据。

## 后端

当前实现是单体 FastAPI 服务，便于开发和面试展示。生产拆分建议：

- API Gateway / Auth
- Upload Service
- Moderation Orchestrator
- Review & Scoring Service
- Anti-Abuse Service
- Licensing Service
- Audit Service
- Notification Service

数据库保存元数据、hash、评分、评论、授权与审计；大文件进入对象存储。AI 调用放入异步队列，避免阻塞上传请求。

## 评分

作品有质量分 `Q` 与置信度 `C`。`Q` 决定相对质量，`C` 决定结果是否稳定。社交数据不进入 Q。首版使用受限加权更新：低信息评论与无依据极端分不计入质量分；同一账号 24 小时重复比较同一作品对权重归零；频繁操作继续降权。生产版本可替换为成对比较的 Bradley–Terry / Elo / TrueSkill 模型。

## 数据表

- `works`
- `reviews`（含 score_counted，用于公开说明评分是否进入质量分）
- `favorites`（work/user 唯一约束，保证收藏幂等）
- `audit_events`
- `vote_events`
- `license_grants`
- `reports`
- `appeals`

生产环境还应增加 users、assets、moderation_cases、review_votes、partition_events、purchases、devices、risk_signals 和 notifications。
