package com.zoujiapeng.rawjudge.domain
object ReviewAnalyzer {
    private val professionalTerms = listOf("构图", "美学", "色彩", "叙事", "寓意", "风格", "影调", "空间", "视觉", "节奏", "层次")
    private val technicalTerms = listOf("raw", "exif", "镜头", "焦段", "快门", "iso", "后期", "曝光", "器材", "光圈", "降噪", "锐化")
    private val objectiveTerms = listOf("因为", "画面", "边缘", "主体", "背景", "高光", "暗部", "细节", "证据", "左侧", "右侧")
    private val constructiveTerms = listOf("建议", "可以", "改进", "如果", "下次", "减少", "增加", "尝试", "调整", "避免")
    fun analyze(text: String): ReviewMetrics { val n = text.trim().lowercase(); val l = when { n.length >= 80 -> .92f; n.length >= 40 -> .78f; n.length >= 20 -> .60f; n.length >= 8 -> .38f; else -> .15f }; return ReviewMetrics((l + hit(n, objectiveTerms)*.20f).coerceAtMost(.98f), (.18f+hit(n,professionalTerms)*.72f).coerceAtMost(.98f), (.16f+hit(n,technicalTerms)*.78f).coerceAtMost(.98f), (.20f+hit(n,objectiveTerms)*.72f).coerceAtMost(.98f), (.18f+hit(n,constructiveTerms)*.76f).coerceAtMost(.98f)) }
    private fun hit(text:String, terms:List<String>)=(terms.count{text.contains(it)}/3f).coerceIn(0f,1f)
}
