package com.wonhy.cardperf.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.wonhy.cardperf.policy.RefreshPolicy
import com.wonhy.cardperf.store.PerfStore
import com.wonhy.cardperf.widget.PerfWidgetProvider

/**
 * Owns every refresh. Being a system-bound accessibility service exempts it from background
 * activity-start limits, and USER_PRESENT can only be received by a dynamic registration.
 */
class PerfService : AccessibilityService() {
    private lateinit var store: PerfStore
    private val handler = Handler(Looper.getMainLooper())
    private var session: RefreshSession? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return
            val now = System.currentTimeMillis()
            if (!RefreshPolicy.shouldAutoRefresh(now, store.lastAttemptAt)) {
                Log.i(RefreshSession.TAG, "skip: cooldown")
                return
            }
            if (inCall()) {
                Log.i(RefreshSession.TAG, "skip: in call")
                return
            }
            // Let the launcher settle after unlock before taking the screen.
            handler.postDelayed({ requestRefresh(manual = false) }, UNLOCK_DELAY_MS)
        }
    }

    override fun onServiceConnected() {
        store = PerfStore(this)
        instance = this
        // onServiceConnected can run again on rebind; never hold two registrations.
        runCatching { unregisterReceiver(unlockReceiver) }
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        Log.i(RefreshSession.TAG, "service connected")
        PerfWidgetProvider.updateAll(this)
    }

    fun requestRefresh(manual: Boolean) {
        if (session != null) {
            Log.i(RefreshSession.TAG, "skip: refresh already running")
            return
        }
        session = RefreshSession(this, store, manual) {
            session = null
            PerfWidgetProvider.updateAll(this)
        }.also { it.start() }
    }

    private fun inCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        session?.cancel()
        runCatching { unregisterReceiver(unlockReceiver) }
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: PerfService? = null
            private set

        private const val UNLOCK_DELAY_MS = 700L
    }
}
