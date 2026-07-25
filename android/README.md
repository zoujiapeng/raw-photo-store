# RAWJudge Android

原生 Kotlin + Jetpack Compose 客户端。首次启动会向后端创建匿名 Bearer 会话，之后上传、收藏、评论评分、盲评、举报、申诉、授权与下载均由服务端确认。后端不可用时进入明确的离线只读状态，不会本地伪造成功。

## 核心代码

- `data/RawJudgeApi.kt`：Bearer 会话、JSON、multipart 上传和私有文件下载。
- `data/SessionStore.kt`：本地会话凭证。
- `data/LocalStateStore.kt`：只读离线显示缓存。
- `data/DemoData.kt`：明确标注、社交数字为零的离线官方占位作品。
- `RawJudgeViewModel.kt`：远端同步、错误状态与所有真实写操作。
- `ui/`：作品流、98% 模式、上传、评论、盲评、个人资料、授权和下载。

## 运行

Android 模拟器默认连接 `http://10.0.2.2:8000`：

```bash
bash gradlew :app:installDebug
```

真机构建：

```bash
bash gradlew \
  -PRAWJUDGE_API_BASE_URL=http://192.168.1.10:8000 \
  :app:assembleDebug
```

正式 Release 必须使用 HTTPS API 地址。后端位于仓库根目录的 `backend/`。
