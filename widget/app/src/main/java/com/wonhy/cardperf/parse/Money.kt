package com.wonhy.cardperf.parse

import java.math.BigDecimal

object Money {
    private val pattern = Regex("""^([\d,.]+)(만)?원$""")

    /** "30만원" → 300000, "1,000,000원" → 1000000, anything else → null. */
    fun parse(text: String): Long? {
        val m = pattern.matchEntire(text.trim()) ?: return null
        val number = m.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
        val scaled = if (m.groupValues[2].isNotEmpty()) number * BigDecimal(10_000) else number
        return scaled.toLong()
    }
}
