package com.wonhy.cardperf.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGuardsTest {
    private val bank = "com.rainist.banksalad2"
    private val launcher = "com.sec.android.app.launcher"

    @Test fun manualSessionNeverSendsGlobalActions() {
        assertFalse(SessionGuards.shouldSendGlobalActions(manual = true, foregroundPackage = bank))
    }

    @Test fun autoSessionSendsGlobalActionsOnlyOverBanksalad() {
        assertTrue(SessionGuards.shouldSendGlobalActions(manual = false, foregroundPackage = bank))
        assertFalse(SessionGuards.shouldSendGlobalActions(false, "com.kakao.talk"))
        assertFalse(SessionGuards.shouldSendGlobalActions(false, "com.samsung.android.incallui"))
        assertFalse(SessionGuards.shouldSendGlobalActions(false, null))
    }

    @Test fun autoStartsOnlyFromLauncher() {
        assertTrue(SessionGuards.canStartAuto(launcher, launcher, inCall = false, keyguardLocked = false))
        assertFalse(SessionGuards.canStartAuto("com.kakao.talk", launcher, false, false))
        assertFalse(SessionGuards.canStartAuto(null, launcher, false, false))
        assertFalse(SessionGuards.canStartAuto(launcher, null, false, false))
    }

    @Test fun autoSkipsInCallOrLocked() {
        assertFalse(SessionGuards.canStartAuto(launcher, launcher, inCall = true, keyguardLocked = false))
        assertFalse(SessionGuards.canStartAuto(launcher, launcher, inCall = false, keyguardLocked = true))
    }
}
