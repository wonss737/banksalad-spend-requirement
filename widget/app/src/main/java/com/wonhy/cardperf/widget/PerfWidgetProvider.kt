package com.wonhy.cardperf.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.wonhy.cardperf.R
import com.wonhy.cardperf.model.CardPerf
import com.wonhy.cardperf.policy.LastResult
import com.wonhy.cardperf.policy.WidgetStatus
import com.wonhy.cardperf.store.PerfStore
import java.text.NumberFormat
import java.time.ZoneId
import java.util.Locale

class PerfWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(ctx)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private const val MAX_ROWS = 6
        private const val GREEN = "#4CD964"
        private const val GREY = "#9A9BA3"
        private const val ORANGE = "#FFB74D"

        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, PerfWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(ctx: Context): RemoteViews {
            val store = PerfStore(ctx)
            val now = System.currentTimeMillis()
            val snapshot = store.snapshot
            val views = RemoteViews(ctx.packageName, R.layout.widget_perf)

            val previous = WidgetStatus.isPreviousPeriod(snapshot?.period, now, ZoneId.systemDefault())
            views.setTextViewText(R.id.period, snapshot?.period?.let { if (previous) "$it · 지난달" else it } ?: "")
            views.setTextColor(R.id.period, Color.parseColor(if (previous) ORANGE else GREY))

            views.removeAllViews(R.id.rows)
            val cards = snapshot?.cards.orEmpty()
            cards.take(MAX_ROWS).forEach { views.addView(R.id.rows, row(ctx, it)) }
            if (cards.size > MAX_ROWS) {
                val more = RemoteViews(ctx.packageName, R.layout.widget_row)
                more.setTextViewText(R.id.name, "외 ${cards.size - MAX_ROWS}개")
                more.setViewVisibility(R.id.bar, View.GONE)
                views.addView(R.id.rows, more)
            }
            views.setViewVisibility(R.id.rows, if (cards.isEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.empty, if (cards.isEmpty()) View.VISIBLE else View.GONE)

            val result = store.lastResult
            val warn = WidgetStatus.isStale(now, store.lastSuccessAt) ||
                result == LastResult.FAILED || result == LastResult.CANCELED
            views.setTextViewText(
                R.id.status,
                WidgetStatus.statusLine(now, store.lastSuccessAt, result, store.failReason),
            )
            views.setTextColor(R.id.status, Color.parseColor(if (warn) ORANGE else GREY))

            val tap = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, RefreshTrampolineActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, tap)
            return views
        }

        private fun row(ctx: Context, card: CardPerf): RemoteViews =
            RemoteViews(ctx.packageName, R.layout.widget_row).apply {
                setTextViewText(R.id.name, card.name)
                if (card.done) {
                    setTextViewText(R.id.state, "완료")
                    setTextColor(R.id.state, Color.parseColor(GREEN))
                    setTextColor(R.id.name, Color.parseColor(GREEN))
                } else {
                    setTextViewText(R.id.state, "${won(card.remaining)} 남음")
                    setTextColor(R.id.state, Color.parseColor(GREY))
                }
                setProgressBar(R.id.bar, 1000, (card.progress * 1000).toInt(), false)
                setTextViewText(R.id.amounts, "${won(card.used)} / ${card.goalText}")
            }

        private fun won(v: Long) = NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원"
    }
}
