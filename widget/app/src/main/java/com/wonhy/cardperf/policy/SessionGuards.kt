package com.wonhy.cardperf.policy

/** Rules that keep a refresh from acting on whatever app the user is actually using. */
object SessionGuards {
    const val BANKSALAD = "com.rainist.banksalad2"

    /**
     * BACK/HOME are only sent by automatic sessions, and only while banksalad is in front.
     * A manual session leaves the screen to the user.
     */
    fun shouldSendGlobalActions(manual: Boolean, foregroundPackage: String?): Boolean =
        !manual && foregroundPackage == BANKSALAD

    /** Auto refresh may take the screen only from the home launcher, unlocked, not in a call. */
    fun canStartAuto(
        foregroundPackage: String?,
        launcherPackage: String?,
        inCall: Boolean,
        keyguardLocked: Boolean,
    ): Boolean =
        !inCall && !keyguardLocked && launcherPackage != null && foregroundPackage == launcherPackage
}
