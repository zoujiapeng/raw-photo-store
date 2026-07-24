# AI Agent 设计

## 1. Upload Integrity Agent

输入：文件名、MIME、大小、hash、EXIF。输出：文件是否可处理、缺失项、相机与时间信息。它不能把“扩展名正确”表述成“真实性已证明”。

## 2. RAW Consistency Agent

输入：RAW 派生预览、最终成片、允许的后期边界。输出：几何一致性、主体一致性、局部替换风险、生成式编辑概率与人工复核建议。

## 3. Safety Moderation Agent

输入：图像、标题、说明、部署地区规则。输出：风险类别、证据位置、公开/仅自己可见/复核/拒绝状态与用户下一步。政治观点本身不应由审美 Agent 判分；地区法规或平台规则风险必须单独记录。

## 4. Quality Scoring Agent

输出固定维度：构图、光线、色彩、叙事、技术完成度、原创性。AI 只提供初始先验，不直接决定长期分区。

## 5. Comment Quality Agent

输出：相关性、专业性、技术性、客观性、建设性、是否折叠、主要类型。不得仅按字数判断质量。

## 6. Anti-Abuse Agent

输入：账号年龄、设备/IP 信号、评分速度、关系链、偏差、互评模式。输出权重倍率和复核理由。任何自动降权都必须可审计。

## 7. Appeal Agent

总结初审、证据、作者申诉和分歧点，交给高信誉评审或人工审核；它不应自行删除原审计记录。

## 8. Licensing Agent

根据作者选择生成不可变授权快照，明确个人使用、RAW 学习、商用、二改、署名和 AI 训练权限。

## 结构化输出

后端的可选 OpenAI Provider 使用 Responses API 的 `text.format` JSON Schema。生产环境应为每个 Agent 定义独立 schema、超时、重试、成本上限、模型版本和评测集。
