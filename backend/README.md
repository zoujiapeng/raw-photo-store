# RAWJudge API

FastAPI 服务负责身份、作品所有权、RAW 硬规则、公开预览、评论质量、盲评、申诉、举报、授权和审计。客户端提交的作者、评审者、购买者或管理员字段不会被信任。

## 启动

```bash
python -m venv .venv
.venv/bin/pip install -e '.[dev]'
.venv/bin/pytest
.venv/bin/uvicorn app.main:app --reload
```

从仓库根目录启动时可使用：

```bash
make backend
```

默认数据库：`./data/rawjudge.db`。默认上传目录：`./uploads`。

## 关键规则

- `POST /v1/auth/anonymous` 生成匿名 Bearer 会话，数据库只保存加 pepper 的 token hash。
- 只有 RAW 扩展名与文件头匹配的作品才能进入公开评审状态。
- 公共预览由后端重新编码为 JPEG，不携带原始 EXIF。
- 原始成片与 RAW 仅作者、管理员或持有相应授权的用户可下载。
- 作者自评不计权；低信息或无依据极端评分不计权；重复盲评作品对 24 小时内权重归零。
- 收藏按会话幂等；重复举报和待处理申诉会被拒绝。
- 未配置支付提供方时，价格大于 0 的授权返回 HTTP 402，不生成虚假购买。

生产环境应使用 Postgres、私有对象存储、恶意文件扫描、签名 URL、限流和异步任务队列。
