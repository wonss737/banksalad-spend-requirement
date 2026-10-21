package com.wonhy.cardperf.policy

import java.time.Instant
import java.time.ZoneId

enum class LastResult { SUCCESS, FAILED, CANCELED }

object WidgetStatus {
    const val STALE_MS = 24 * 3_600_000L

    private val periodEnd = Regex("""~\s*(\d{1,2})\.(\d{1,2})""")

    fun isStale(now: Long, lastSuccessAt: Long?): Boolean =
        lastSuccessAt == null || now - lastSuccessAt > STALE_MS

    fun statusLine(now: Long, lastSuccessAt: Long?, lastResult: LastResult?, failReason: String?): String {
        val parts = mutableListOf(
            if (lastSuccessAt == null) "아직 갱신 안 됨" else "${relative(now - lastSuccessAt)} 갱신"
        )
        when (lastResult) {
            LastResult.FAILED -> parts += "최근 갱신 실패"
            LastResult.CANCELED -> parts += "갱신 중단됨"
            else -> Unit
        }
        if (lastSuccessAt != null && isStale(now, lastSuccessAt)) parts += "오래됨"
        return parts.joinToString(" · ")
    }

    /** True when the period ("10.1~10.31") ends in a month other than the current one. */
    fun isPreviousPeriod(period: String?, now: Long, zone: ZoneId): Boolean {
        val m = period?.let { periodEnd.find(it) } ?: return false
        val endMonth = m.groupValues[1].toInt()
        val currentMonth = Instant.ofEpochMilli(now).atZone(zone).monthValue
        return endMonth != currentMonth
    }

    private fun relative(elapsed: Long): String {
        val minutes = elapsed.coerceAtLeast(0) / 60_000
        return when {
            minutes < 1 -> "방금"
            minutes < 60 -> "${minutes}분 전"
            minutes < 24 * 60 -> "${minutes / 60}시간 전"
            else -> "${minutes / (24 * 60)}일 전"
        }
    }
}
