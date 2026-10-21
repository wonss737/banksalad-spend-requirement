package com.wonhy.cardperf.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.wonhy.cardperf.parse.PerfParser
import com.wonhy.cardperf.policy.LastResult
import com.wonhy.cardperf.policy.ReadStabilizer
import com.wonhy.cardperf.store.PerfStore

/** One refresh run: launch banksalad, poll the accessibility tree, store, then clean up. */
class RefreshSession(
    private val service: AccessibilityService,
    private val store: PerfStore,
    private val manual: Boolean,
    private val onFinished: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val overlay = Overlay(service)
    private val stabilizer = ReadStabilizer()
    private var startedAt = 0L
    private var perfScreenSeen = false
    private var tabClicked = false
    private var finished = false

    fun start() {
        startedAt = SystemClock.elapsedRealtime()
        store.lastAttemptAt = System.currentTimeMillis()
        Log.i(TAG, "start manual=$manual")
        try {
            if (!manual) overlay.show { cancel() }
            service.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(DEEP_LINK))
                    .setPackage(BANKSALAD)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "launch failed", e)
            finish(LastResult.FAILED, "launch")
            return
        }
        handler.postDelayed(::poll, FIRST_READ_DELAY_MS)
    }

    fun cancel() = finish(LastResult.CANCELED, "user")

    private fun poll() {
        if (finished) return
        try {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val texts = readBanksaladTexts()
            if (PerfParser.isPerfScreen(texts)) {
                perfScreenSeen = true
                val snapshot = PerfParser.parse(texts, System.currentTimeMillis())
                if (stabilizer.offer(snapshot.cards)) {
                    store.snapshot = snapshot
                    store.lastSuccessAt = snapshot.readAt
                    Log.i(TAG, "SUCCESS cards=${snapshot.cards.size} period=${snapshot.period} " +
                        snapshot.cards.joinToString { "${it.name}:${it.used}/${it.goalText}" })
                    finish(LastResult.SUCCESS, null)
                    return
                }
            } else if (!tabClicked && elapsed > TAB_FALLBACK_MS) {
                tabClicked = clickBenefitTab()
                Log.i(TAG, "perf screen not found; clicked benefit tab=$tabClicked")
            }
            if (elapsed > TIMEOUT_MS) {
                finish(LastResult.FAILED, if (perfScreenSeen) "parse" else "timeout")
                return
            }
            handler.postDelayed(::poll, POLL_MS)
        } catch (e: Exception) {
            Log.w(TAG, "poll failed", e)
            finish(LastResult.FAILED, "error")
        }
    }

    /** Single exit path: the overlay is always removed and the widget redrawn. */
    private fun finish(result: LastResult, reason: String?) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "finish $result reason=$reason")
        try {
            store.lastResult = result
            store.failReason = reason
            val keepScreen = manual && result == LastResult.SUCCESS
            if (!keepScreen) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                handler.postDelayed({
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    overlay.hide()
                }, HOME_DELAY_MS)
                // A launch still in flight (e.g. cancelled within a second) can surface banksalad
                // after we went home; send it home again, but only while banksalad is in front.
                for (i in 1..LATE_LAUNCH_CHECKS) {
                    handler.postDelayed({
                        if (service.rootInActiveWindow?.packageName == BANKSALAD) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        }
                    }, HOME_DELAY_MS + i * POLL_MS)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "finish cleanup failed", e)
            overlay.hide()
        } finally {
            // Guarantee: the overlay never outlives the session even if the delayed hide is lost.
            handler.postDelayed({ overlay.hide() }, HOME_DELAY_MS * 3)
            onFinished()
        }
    }

    private fun banksaladRoots(): List<AccessibilityNodeInfo> =
        service.windows.mapNotNull { it.root }.filter { it.packageName == BANKSALAD }
            .ifEmpty { listOfNotNull(service.rootInActiveWindow?.takeIf { it.packageName == BANKSALAD }) }

    private fun readBanksaladTexts(): List<String> {
        val out = mutableListOf<String>()
        banksaladRoots().forEach { collect(it, out) }
        return out
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) collect(node.getChild(i), out)
    }

    private fun clickBenefitTab(): Boolean {
        var node = banksaladRoots().firstNotNullOfOrNull { root ->
            root.findAccessibilityNodeInfosByText(BENEFIT_TAB).firstOrNull { it.text?.toString() == BENEFIT_TAB }
        }
        while (node != null && !node.isClickable) node = node.parent
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    companion object {
        const val TAG = "CardPerf"
        const val BANKSALAD = "com.rainist.banksalad2"
        const val DEEP_LINK =
            "banksalad://webview?url=https%3A%2F%2Fwebview.banksalad.com%2Fcard%3Ftab%3Dbenefit"
        private const val BENEFIT_TAB = "카드 혜택"
        const val TIMEOUT_MS = 45_000L
        const val POLL_MS = 500L
        const val FIRST_READ_DELAY_MS = 1_500L
        const val TAB_FALLBACK_MS = 8_000L
        const val HOME_DELAY_MS = 300L
        private const val LATE_LAUNCH_CHECKS = 6
    }
}
