# RAWJudge API

FastAPI 服务，提供上传、RAW 硬规则、AI 元数据评审、评论质量、盲评、申诉、举报、授权和审计接口。

```bash
pip install -e '.[dev]'
pytest
uvicorn app.main:app --reload
```

默认数据库：`./data/rawjudge.db`。默认上传目录：`./uploads`。生产环境应使用 Postgres、对象存储、恶意文件扫描、签名 URL 和异步任务队列。


反作弊与授权：低信息极端分不计权；重复盲评作品对 24 小时内归零；收藏按用户幂等；RAW 下载需要已授予的 `raw_study` 授权。
