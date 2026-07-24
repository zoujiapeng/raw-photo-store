from __future__ import annotations
from dataclasses import dataclass
@dataclass(frozen=True)
class Metrics:
    relevance:float; professional:float; technical:float; objective:float; constructive:float
    @property
    def average(self): return (self.relevance+self.professional+self.technical+self.objective+self.constructive)/5
    @property
    def strongest(self): return max({'relevance':self.relevance,'professional':self.professional,'technical':self.technical,'objective':self.objective,'constructive':self.constructive},key=lambda k:getattr(self,k))
PROFESSIONAL=("构图","美学","色彩","叙事","寓意","风格","影调","空间","视觉","层次")
TECHNICAL=("raw","exif","镜头","焦段","快门","iso","后期","曝光","器材","光圈","降噪","锐化")
OBJECTIVE=("因为","画面","边缘","主体","背景","高光","暗部","细节","证据","左侧","右侧")
CONSTRUCTIVE=("建议","可以","改进","如果","下次","减少","增加","尝试","调整","避免")
def analyze_review(text:str)->Metrics:
    n=text.strip().lower(); l=.92 if len(n)>=80 else .78 if len(n)>=40 else .60 if len(n)>=20 else .38 if len(n)>=8 else .15
    h=lambda terms:min(1.0,sum(t in n for t in terms)/3)
    return Metrics(min(.98,l+h(OBJECTIVE)*.20),min(.98,.18+h(PROFESSIONAL)*.72),min(.98,.16+h(TECHNICAL)*.78),min(.98,.20+h(OBJECTIVE)*.72),min(.98,.18+h(CONSTRUCTIVE)*.76))
