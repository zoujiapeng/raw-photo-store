# Security

不要在公开 Issue 中提交 API Key、Bearer token、管理员 token、RAW 原文件、个人身份信息或未公开漏洞细节。

## 已实施

- 服务端 Bearer 身份；数据库只保存加 pepper 的 token hash。
- 所有权和管理员权限由服务端判断。
- 公共展示图去 EXIF；原始成片与 RAW 默认私有。
- 上传字节数、图片像素、图片解码、RAW 扩展名和文件头限制。
- 私有下载 `Cache-Control: no-store`，公开预览 `nosniff`。
- 生产模式拒绝默认密钥、通配 Host/CORS 和非 HTTPS 公网地址。
- AI 输出不能直接授予权限、确认付款或绕过 RAW 硬规则。

## 生产前仍需

- 速率限制、WAF、恶意文件扫描、对象存储签名 URL 和密钥轮换。
- 短期/可撤销登录会话、账号恢复、多因素管理员认证。
- 支付 webhook 验签、退款和授权撤销策略。
- 集中安全日志、异常告警、备份和漏洞响应流程。
- 依赖与容器扫描、SBOM、Android 签名密钥保护。

安全问题请通过仓库 Security Advisory 私下报告。
