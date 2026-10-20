package moe.shizuku.manager.receiver

/** What an unattended start does with the moment it has been asked at; see [decideStartNow]. */
enum class StartNowDecision {
    /** Go ahead and ask for the start. */
    START,

    /** The user has not unlocked the phone yet; ask again when they do. */
    DEFER_UNTIL_UNLOCK,

    /** Nothing asked for a start: start on boot is off and no server has run in this boot. */
    SKIP_NOT_WANTED,

    /** Shizuku was stopped on purpose, and that stays in force until a start is asked for. */
    SKIP_STOPPED
}

/**
 * Whether an unattended start (the unlock hook, the watchdog) may be asked for right now.
 *
 * A deliberate stop and a start nobody wanted are answered before the unlock, so that a phone
 * with Shizuku switched off does not log a deferral and then start it the moment it is unlocked.
 *
 * The unlock matters because the start goes through WorkManager, whose database lives in
 * credential-encrypted storage: before the first unlock there is nothing for it to read, and a
 * request made then is lost or fails rather than waiting its turn.
 */
internal fun decideStartNow(
    userUnlocked: Boolean,
    startWanted: Boolean,
    manuallyStopped: Boolean
): StartNowDecision = when {
    manuallyStopped -> StartNowDecision.SKIP_STOPPED
    !startWanted -> StartNowDecision.SKIP_NOT_WANTED
    !userUnlocked -> StartNowDecision.DEFER_UNTIL_UNLOCK
    else -> StartNowDecision.START
}
