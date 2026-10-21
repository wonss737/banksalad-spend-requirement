package com.wonhy.cardperf.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.wonhy.cardperf.MainActivity
import com.wonhy.cardperf.service.PerfService

/** Widget tap target: starts a manual refresh, or opens setup when the service is off. */
class RefreshTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = PerfService.instance
        if (service != null) {
            service.requestRefresh(manual = true)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
