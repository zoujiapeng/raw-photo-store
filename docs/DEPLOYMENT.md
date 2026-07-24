# 部署

## 本地

`docker compose up --build` 启动 API。SQLite 和上传文件保存在 Docker volume。

## 生产建议

- Postgres 作为事务数据库。
- S3/R2/OSS 等对象存储保存成片、RAW、缩略图和派生预览。
- Redis + Celery/Arq/队列处理 EXIF、缩略图、AI 审核和一致性计算。
- CDN 只分发经过授权的展示图，不直接暴露 RAW 地址。
- 原始文件使用短期签名 URL、下载次数限制和授权快照校验。
- 后端密钥不得进入 Android APK。

## 发布 Android

推送 `v*` 标签会触发 `release.yml`：运行测试、构建 Debug APK 并创建 GitHub Release。正式 Play 发布必须改为签名 Release AAB，并把 keystore 放入 GitHub Encrypted Secrets。
