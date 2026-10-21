package com.wonhy.cardperf.policy

import com.wonhy.cardperf.model.CardPerf

/** Accepts a read only after the same non-empty card list is seen [required] times in a row
 * (4 reads at 500ms polling = the list held steady for at least 1.5s). */
class ReadStabilizer(private val required: Int = 4) {
    private var last: List<CardPerf>? = null
    private var count = 0

    fun offer(cards: List<CardPerf>): Boolean {
        if (cards.isEmpty()) {
            last = null
            count = 0
            return false
        }
        count = if (cards == last) count + 1 else 1
        last = cards
        return count >= required
    }
}
