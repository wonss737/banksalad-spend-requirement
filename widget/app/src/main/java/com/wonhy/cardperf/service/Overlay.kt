package com.wonhy.cardperf.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opaque cover shown during an automatic refresh. Uses TYPE_ACCESSIBILITY_OVERLAY, so it needs
 * no SYSTEM_ALERT_WINDOW grant, and accessibility still reports the banksalad window beneath it.
 */
class Overlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show(onTap: () -> Unit) {
        if (view != null) return
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E1F24"))
            addView(TextView(ctx).apply {
                text = "실적 갱신 중…"
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(TextView(ctx).apply {
                text = "누르면 중단하고 바로 사용할 수 있어요"
                textSize = 13f
                setTextColor(Color.parseColor("#9A9BA3"))
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 0)
            })
            setOnClickListener { onTap() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        wm.addView(root, lp)
        view = root
    }

    fun hide() {
        val v = view ?: return
        view = null
        runCatching { wm.removeView(v) }
    }
}
