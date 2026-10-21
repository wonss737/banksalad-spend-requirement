package com.wonhy.cardperf.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.wonhy.cardperf.policy.RefreshPolicy
import com.wonhy.cardperf.policy.SessionGuards
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
            // Keeps "N시간 전", 오래됨 and 지난달 current even when no refresh runs.
            PerfWidgetProvider.updateAll(this@PerfService)
            if (!RefreshPolicy.shouldAutoRefresh(System.currentTimeMillis(), store.lastAttemptAt)) {
                Log.i(RefreshSession.TAG, "skip: cooldown")
                return
            }
            // Let the launcher settle after unlock, then take the screen only from home.
            handler.postDelayed(::maybeStartAuto, UNLOCK_DELAY_MS)
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

    private fun maybeStartAuto() {
        val foreground = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            ?.root?.packageName?.toString()
        val launcher = launcherPackage()
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!SessionGuards.canStartAuto(foreground, launcher, inCall(), locked)) {
            Log.i(RefreshSession.TAG, "skip: foreground=$foreground launcher=$launcher locked=$locked")
            return
        }
        requestRefresh(manual = false)
    }

    fun requestRefresh(manual: Boolean) {
        session?.let {
            if (it.isRunning) {
                Log.i(RefreshSession.TAG, "skip: refresh already running")
                return
            }
            // A finished session may still have cleanup HOME checks queued; they must not
            // act on the session we are about to start.
            it.dispose()
        }
        session = RefreshSession(this, store, manual, ::inCall) {
            PerfWidgetProvider.updateAll(this)
        }.also { it.start() }
    }

    private fun launcherPackage(): String? =
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName

    private fun inCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        session?.cancel()
        session?.dispose()
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
