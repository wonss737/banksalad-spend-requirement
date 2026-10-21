package com.wonhy.cardperf.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {
    private val h = 3_600_000L

    @Test fun firstRunRefreshes() = assertTrue(RefreshPolicy.shouldAutoRefresh(10 * h, null))
    @Test fun withinCooldownSkips() = assertFalse(RefreshPolicy.shouldAutoRefresh(10 * h, 5 * h))
    @Test fun exactlySixHoursRefreshes() = assertTrue(RefreshPolicy.shouldAutoRefresh(11 * h, 5 * h))
    @Test fun clockWentBackwardsRefreshes() = assertTrue(RefreshPolicy.shouldAutoRefresh(1 * h, 5 * h))
}
