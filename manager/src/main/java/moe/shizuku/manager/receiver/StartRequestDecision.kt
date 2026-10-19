package moe.shizuku.manager.receiver

/** What [ShizukuReceiverStarter.start] does with a request, decided before anything is touched. */
enum class StartDecision {
    /** Go ahead and start. */
    START,

    /** The server is up and nothing asked to replace it. */
    IGNORE_RUNNING,

    /** A start is already under way; this request adds nothing to it. */
    JOIN_IN_FLIGHT
}

/**
 * The order of the checks matters: a running server answers a plain request first, and only then
 * is a start in flight asked about.
 *
 * Only a Restart somebody tapped may replace a start that is under way. The watchdog's forced
 * starts and a boot's start arrive in the same few seconds after a boot, and each queued work
 * that cancelled the last - but cancelling a start does not stop the command it has already sent,
 * so the two ran side by side and the second one's starter killed the first one's server.
 */
internal fun decideStart(
    binderAlive: Boolean,
    forceStart: Boolean,
    userInitiated: Boolean,
    startInFlight: Boolean
): StartDecision = when {
    binderAlive && !forceStart -> StartDecision.IGNORE_RUNNING
    startInFlight && !(forceStart && userInitiated) -> StartDecision.JOIN_IN_FLIGHT
    else -> StartDecision.START
}
