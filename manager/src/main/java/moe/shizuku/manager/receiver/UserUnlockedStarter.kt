package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ktx.isUserUnlockedCompat
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.utils.BootTimeline
import moe.shizuku.manager.utils.Diag

/**
 * Begins the boot start the moment the user unlocks, instead of when the system gets round to
 * BOOT_COMPLETED.
 *
 * The app is direct-boot aware, so the system can wake the process well before the first unlock
 * (a client asking for the binder, the locked-boot broadcast). Everything that starts a server goes
 * through WorkManager, whose database cannot be read until then, so a request made earlier is
 * deferred rather than lost, and this is what makes the request again. BOOT_COMPLETED follows some
 * seconds later and finds the start already in flight, which is what [decideStart] is for.
 *
 * ACTION_USER_UNLOCKED cannot be declared in the manifest, which is why this is a receiver
 * registered by the running process. A process that comes up after the unlock has nothing to
 * wait for and registers nothing; the watchdog and the boot receiver cover it as they always did.
 */
object UserUnlockedStarter {

    private const val TAG = "UserUnlockedStarter"

    private var registered: BroadcastReceiver? = null

    /** Waits for the unlock when there is one to wait for, and something that would want it. */
    @Synchronized
    fun registerIfLocked(context: Context) {
        if (registered != null || context.isUserUnlockedCompat()) return
        if (!ShizukuSettings.getStartOnBoot(context) && !ShizukuSettings.getWatchdog()) return

        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_UNLOCKED) return
                finish(app)
            }
        }
        registered = receiver
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(Intent.ACTION_USER_UNLOCKED),
            // A protected broadcast: only the system can send it, so nothing else needs to reach
            // this receiver, and the flag is required from Android 14 for a receiver like it.
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Diag.info(TAG, "Waiting for the user to unlock${BootTimeline.suffix()}")
        // Unlocked between the check above and the registration: the broadcast has already gone.
        if (app.isUserUnlockedCompat()) finish(app)
    }

    @Synchronized
    private fun finish(context: Context) {
        val receiver = registered ?: return
        registered = null
        runCatching { context.unregisterReceiver(receiver) }
        onUnlocked(context)
    }

    private fun onUnlocked(context: Context) {
        Diag.info(TAG, "User unlocked${BootTimeline.suffix()}")

        // Anything that may start a server reaches WorkManager, which the system could not set up
        // while the process was running without credential storage.
        ensureWorkManager(context)

        val startOnBoot = ShizukuSettings.getStartOnBoot(context)
        when (decideStartNow(true, startOnBoot, ShizukuSettings.getManuallyStopped())) {
            StartNowDecision.START ->
                ShizukuReceiverStarter.start(context, reason = "user-unlocked")

            StartNowDecision.SKIP_STOPPED ->
                Diag.debug(TAG, "Start at unlock skipped: the service was stopped deliberately")

            StartNowDecision.SKIP_NOT_WANTED, StartNowDecision.DEFER_UNTIL_UNLOCK -> Unit
        }

        // The watchdog put a restart off until now ("Restart deferred until the user is unlocked").
        // Starting it again re-runs its probe, which joins the start above if there is one.
        if (ShizukuSettings.getWatchdog()) WatchdogService.start(context)
    }

    /**
     * A process that started before the unlock may have no WorkManager: its initializer is a
     * content provider, and one that is not direct-boot aware is not installed in such a process,
     * nor installed later when the unlock comes. Asking the process for it then fails for good
     * rather than for now, so it is set up here, where the unlock is known.
     */
    private fun ensureWorkManager(context: Context) {
        try {
            WorkManager.getInstance(context)
        } catch (e: IllegalStateException) {
            Diag.warn(TAG, "WorkManager was not set up before the unlock; setting it up now", e)
            runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
                .onFailure { Diag.error(TAG, "Could not set WorkManager up", it) }
        }
    }
}
