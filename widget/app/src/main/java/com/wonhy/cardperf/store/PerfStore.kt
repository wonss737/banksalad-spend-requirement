package com.wonhy.cardperf.store

import android.content.Context
import com.wonhy.cardperf.model.PerfSnapshot
import com.wonhy.cardperf.policy.LastResult

class PerfStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("perf_store", Context.MODE_PRIVATE)

    var snapshot: PerfSnapshot?
        get() = prefs.getString(KEY_SNAPSHOT, null)?.let { PerfSnapshot.fromJson(it) }
        set(v) = prefs.edit().putString(KEY_SNAPSHOT, v?.toJson()).apply()

    var lastSuccessAt: Long?
        get() = getLong(KEY_SUCCESS)
        set(v) = putLong(KEY_SUCCESS, v)

    var lastAttemptAt: Long?
        get() = getLong(KEY_ATTEMPT)
        set(v) = putLong(KEY_ATTEMPT, v)

    var lastResult: LastResult?
        get() = prefs.getString(KEY_RESULT, null)?.let { runCatching { LastResult.valueOf(it) }.getOrNull() }
        set(v) = prefs.edit().putString(KEY_RESULT, v?.name).apply()

    var failReason: String?
        get() = prefs.getString(KEY_REASON, null)
        set(v) = prefs.edit().putString(KEY_REASON, v).apply()

    private fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null

    private fun putLong(key: String, v: Long?) {
        prefs.edit().apply { if (v == null) remove(key) else putLong(key, v) }.apply()
    }

    private companion object {
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_SUCCESS = "last_success_at"
        const val KEY_ATTEMPT = "last_attempt_at"
        const val KEY_RESULT = "last_result"
        const val KEY_REASON = "fail_reason"
    }
}
