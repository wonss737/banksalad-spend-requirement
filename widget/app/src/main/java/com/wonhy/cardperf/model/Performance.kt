package com.wonhy.cardperf.model

import org.json.JSONArray
import org.json.JSONObject

data class CardPerf(
    val name: String,
    val used: Long,
    val goalText: String,
    val goal: Long?,
    val done: Boolean,
    val remaining: Long,
) {
    /** 0..1. Uses the goal when known, otherwise used/(used+remaining). */
    val progress: Float
        get() {
            if (done) return 1f
            val denominator = goal ?: (used + remaining)
            if (denominator <= 0L) return 0f
            return (used.toFloat() / denominator).coerceIn(0f, 1f)
        }
}

data class PerfSnapshot(
    val period: String?,
    val cards: List<CardPerf>,
    val readAt: Long,
) {
    fun toJson(): String {
        val arr = JSONArray()
        cards.forEach { c ->
            arr.put(
                JSONObject()
                    .put("name", c.name)
                    .put("used", c.used)
                    .put("goalText", c.goalText)
                    .put("goal", c.goal ?: JSONObject.NULL)
                    .put("done", c.done)
                    .put("remaining", c.remaining)
            )
        }
        return JSONObject()
            .put("period", period ?: JSONObject.NULL)
            .put("readAt", readAt)
            .put("cards", arr)
            .toString()
    }

    companion object {
        fun fromJson(s: String): PerfSnapshot? = try {
            val o = JSONObject(s)
            val arr = o.getJSONArray("cards")
            val cards = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                CardPerf(
                    name = c.getString("name"),
                    used = c.getLong("used"),
                    goalText = c.getString("goalText"),
                    goal = if (c.isNull("goal")) null else c.getLong("goal"),
                    done = c.getBoolean("done"),
                    remaining = c.getLong("remaining"),
                )
            }
            PerfSnapshot(
                period = if (o.isNull("period")) null else o.getString("period"),
                cards = cards,
                readAt = o.getLong("readAt"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
