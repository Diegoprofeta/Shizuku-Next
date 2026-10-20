package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.start.reapplyAdbWithoutDeveloperOptionsIfEnabled
import moe.shizuku.manager.utils.BootTimeline
import moe.shizuku.manager.utils.Diag

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> onLockedBoot(context)
            Intent.ACTION_BOOT_COMPLETED -> onBootCompleted(context)
        }
    }

    /**
     * The first signal of a boot, delivered before the user has unlocked.
     *
     * Nothing here asks for a start: that goes through WorkManager, whose database is in storage
     * that is still locked. What can be done is to get the process and the watchdog up as early as
     * possible, so that both are already waiting when the unlock comes (see [UserUnlockedStarter])
     * rather than being woken by BOOT_COMPLETED some seconds after it.
     */
    private fun onLockedBoot(context: Context) {
        Diag.info(TAG, "Locked boot completed received${BootTimeline.suffix()}")

        // Settings.Global writes go to the system, not to this app's storage, so they do not
        // depend on the unlock, and the toggles they put back are what a start needs first.
        reapplyAdbWithoutDeveloperOptionsIfEnabled(context)

        if (ShizukuSettings.getWatchdog()) WatchdogService.start(context)
        WatchdogAlarmReceiver.schedule(context)
    }

    private fun onBootCompleted(context: Context) {
        Diag.info(TAG, "Boot completed received${BootTimeline.suffix()}")

        // A reboot clears the ADB toggles, so the setting that keeps them on has to be put
        // back before anything tries to use them.
        reapplyAdbWithoutDeveloperOptionsIfEnabled(context)

        // The receiver is enabled by either start on boot or ADB without Developer options,
        // so its presence says nothing about this setting; ask the setting itself.
        if (ShizukuSettings.getStartOnBoot(context)) ShizukuReceiverStarter.start(context, reason = "boot")
        if (ShizukuSettings.getWatchdog()) WatchdogService.start(context)
        // Alarms do not survive a reboot, so the watchdog's backstop has to be armed again here
        // or the first process death after a restart is the one nothing comes back from. The
        // call checks the setting itself, so it is safe to make either way.
        WatchdogAlarmReceiver.schedule(context)
    }

    private companion object {
        const val TAG = "BootCompleteReceiver"
    }
}
