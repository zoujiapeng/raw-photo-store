# RAWJudge Android

原生 Kotlin + Jetpack Compose 客户端。默认使用本地状态实现完整交互闭环，适合 Android Studio 直接运行和产品演示。

核心代码：

- `domain/`：质量分区、评论分析、RAW 审核规则和数据模型。
- `data/DemoData.kt`：明确标注的官方样例。
- `RawJudgeViewModel.kt`：真实状态变化、盲评、评论、申诉、举报和授权。
- `ui/`：沉浸作品流、上传、盲评、个人页和设置。

后端位于仓库根目录的 `backend/`。生产版本应将 ViewModel 的本地操作替换为 API Repository，并保留离线缓存。
