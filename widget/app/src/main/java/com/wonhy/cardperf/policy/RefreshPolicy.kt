package com.wonhy.cardperf.policy

object RefreshPolicy {
    const val COOLDOWN_MS = 6 * 3_600_000L

    /** Cooldown counts from the last attempt, so failures don't retry on every unlock. */
    fun shouldAutoRefresh(now: Long, lastAttemptAt: Long?): Boolean {
        if (lastAttemptAt == null || now < lastAttemptAt) return true
        return now - lastAttemptAt >= COOLDOWN_MS
    }
}
