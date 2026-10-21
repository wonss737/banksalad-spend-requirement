package com.wonhy.cardperf

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.wonhy.cardperf.policy.WidgetStatus
import com.wonhy.cardperf.service.PerfService
import com.wonhy.cardperf.store.PerfStore

/** Setup screen: accessibility state, last refresh status, and manual controls. */
class MainActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        info = TextView(this).apply { textSize = 15f; setTextColor(Color.WHITE) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(Color.parseColor("#1E1F24"))
            addView(info)
            addView(Button(this@MainActivity).apply {
                text = "접근성 설정 열기"
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
            addView(Button(this@MainActivity).apply {
                text = "지금 갱신"
                setOnClickListener {
                    PerfService.instance?.requestRefresh(manual = true) ?: render()
                }
            })
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val store = PerfStore(this)
        val enabled = PerfService.instance != null
        val snapshot = store.snapshot
        info.text = buildString {
            appendLine(if (enabled) "접근성 서비스: 켜짐" else "접근성 서비스: 꺼짐 — 설정 > 접근성 > 설치된 앱 > '카드 실적 위젯'을 켜 주세요.")
            appendLine()
            appendLine(WidgetStatus.statusLine(System.currentTimeMillis(), store.lastSuccessAt, store.lastResult, store.failReason))
            store.failReason?.let { appendLine("실패 사유: $it") }
            snapshot?.period?.let { appendLine("실적 기간: $it") }
            snapshot?.cards?.forEach { c ->
                appendLine("· ${c.name}: ${c.used}원 / ${c.goalText} — ${if (c.done) "완료" else "${c.remaining}원 남음"}")
            }
            appendLine()
            appendLine("홈 화면을 길게 눌러 '위젯'에서 '카드 실적 위젯'을 추가할 수 있어요.")
        }
    }
}
