package com.zoujiapeng.rawjudge.domain
object PartitionPolicy {
    fun place(work: Work): Partition = when { work.moderationStatus in setOf(ModerationStatus.NEEDS_RAW, ModerationStatus.REJECTED, ModerationStatus.APPEALING) -> Partition.ARCHIVE; work.rawVerified && work.score >= 84.0 && work.confidence >= 0.70 -> Partition.GALLERY; work.rawVerified && work.score >= 64.0 -> Partition.REVIEW; work.score >= 45.0 -> Partition.WORKSHOP; else -> Partition.ARCHIVE }
    fun weightedScore(work: Work, review: Review): Double { if (!review.scoreCounted) return work.score; val trust = review.reviewerTrust.coerceIn(0.05f, 0.95f); val influence = (0.004 + trust * review.quality * 0.045).coerceIn(0.004, 0.045); return (work.score * (1 - influence) + review.score * influence).coerceIn(1.0, 99.9) }
}
