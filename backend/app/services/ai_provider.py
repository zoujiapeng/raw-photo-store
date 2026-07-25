from __future__ import annotations
import json
from dataclasses import dataclass
import httpx
from ..config import settings
@dataclass(frozen=True)
class AiAssessment: quality_score:float; confidence:float; summary:str; labels:list[str]
class OpenAIProvider:
    @property
    def enabled(self): return bool(settings.openai_api_key)
    async def assess_metadata(self,title,description,raw_name):
        if not self.enabled:return None
        payload={'model':settings.openai_model,'input':[{'role':'system','content':'Return conservative photography metadata assessment as JSON. Never claim RAW authenticity is proven from filename.'},{'role':'user','content':json.dumps({'title':title,'description':description,'raw_filename':raw_name},ensure_ascii=False)}],'text':{'format':{'type':'json_schema','name':'photography_assessment','strict':True,'schema':{'type':'object','properties':{'quality_score':{'type':'number'},'confidence':{'type':'number'},'summary':{'type':'string'},'labels':{'type':'array','items':{'type':'string'}}},'required':['quality_score','confidence','summary','labels'],'additionalProperties':False}}},'max_output_tokens':500}
        try:
            async with httpx.AsyncClient(timeout=30) as client:r=await client.post(f"{settings.openai_base_url.rstrip('/')}/responses",headers={'Authorization':f'Bearer {settings.openai_api_key}'},json=payload);r.raise_for_status();d=r.json()
            text=d.get('output_text') or d['output'][0]['content'][0]['text'];p=json.loads(text);return AiAssessment(float(p['quality_score']),float(p['confidence']),str(p['summary']),list(p['labels']))
        except Exception:return None
ai_provider=OpenAIProvider()
