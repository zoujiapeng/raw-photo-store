from dataclasses import dataclass
from pathlib import Path
from .ai_provider import ai_provider
RAW_EXT={'.dng','.cr2','.cr3','.nef','.arw','.rw2','.orf','.raf','.pef'}
@dataclass(frozen=True)
class Moderation: status:str; raw_verified:bool; score:float; confidence:float; summary:str; labels:list[str]
async def moderate(title,description,image_name,raw_name,image_hash,raw_hash):
    raw_ok=bool(raw_name and Path(raw_name).suffix.lower() in RAW_EXT and raw_hash); text=f'{title} {description} {image_name} {raw_name or ""}'.lower(); gen=any(k in text for k in ('midjourney','stable diffusion','ai生成','comfyui','prompt'))
    if not raw_ok:return Moderation('needs_raw',False,35,.25,'未确认受支持 RAW；请补充原始文件。',['raw_missing_or_unsupported'])
    if gen:return Moderation('rejected',False,30,.35,'检测到生成式处理线索，不能进入 RAW 认证分区。',['generated_image_suspected'])
    score=68+(int(image_hash[:4],16)%1800)/100; conf=.48; labels=[]; summary='RAW 文件证据存在，进入盲评；这不等于真实性已最终证明。'
    enrichment=await ai_provider.assess_metadata(title,description,raw_name)
    if enrichment: score=max(1,min(99,enrichment.quality_score)); conf=max(conf,min(.65,enrichment.confidence*.65)); summary+=f' AI 元数据建议：{enrichment.summary}'; labels+=enrichment.labels
    return Moderation('reviewing',True,score,conf,summary,labels)
