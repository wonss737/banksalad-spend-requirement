package com.wonhy.cardperf.parse

import com.wonhy.cardperf.model.CardPerf
import com.wonhy.cardperf.model.PerfSnapshot

/**
 * Parses banksalad's "실적 관리" node texts, e.g.
 * "그린 체크카드 258,500원 30만원 실적 달성 41,500원 남음" or
 * "샐러드 신용카드 412,300원 30만원 실적 달성 완료 축하" (trailing words allowed).
 * Unlinked cards ("실적 달성 ??원 남음") do not match and are skipped.
 */
object PerfParser {
    private val cardPattern =
        Regex("""^(.+?)\s+([\d,]+)원\s+(\S+원)\s+실적 달성\s+(완료|([\d,]+)원 남음)""")
    private val periodPattern = Regex("""실적 기간\s*:\s*(\S+)""")

    fun isPerfScreen(texts: List<String>): Boolean = texts.any { it.contains("실적 기간") }

    fun parse(texts: List<String>, now: Long): PerfSnapshot {
        val period = texts.firstNotNullOfOrNull { periodPattern.find(it)?.groupValues?.get(1) }
        val cards = LinkedHashMap<String, CardPerf>()
        for (text in texts) {
            val m = cardPattern.find(text.trim()) ?: continue
            val name = m.groupValues[1].trim()
            if (name in cards) continue
            val done = m.groupValues[4] == "완료"
            cards[name] = CardPerf(
                name = name,
                used = m.groupValues[2].replace(",", "").toLong(),
                goalText = m.groupValues[3],
                goal = Money.parse(m.groupValues[3]),
                done = done,
                remaining = if (done) 0L else m.groupValues[5].replace(",", "").toLong(),
            )
        }
        return PerfSnapshot(period, cards.values.toList(), now)
    }
}
