package moe.shizuku.manager.manage

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.service.HidingWatchService
import moe.shizuku.manager.start.stopShizuku
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

/**
 * Every switch in this app that is about a whole feature rather than one app.
 *
 * The Lab screen is built out of two kinds of thing: lists, where each row is an app, and modes,
 * where there is one state for the lot. The second kind is what this is: the firewall's chain, the
 * autostart list's gate, each hiding setting's mode, force dark, the watchdog, and the service
 * itself. All of them exist as a single on or off, which is what a tile in the shade and a widget
 * on the home screen can offer without a screen of their own.
 *
 * The rules stay where they already were - [FirewallChain], [AutoStartGate], [Hiding], [ForceDark],
 * [ShizukuSettings] and the start and stop paths - and this is only the name and the two calls a
 * tile and a widget need. That is deliberate: a switch outside the app must do exactly what the
 * switch inside it does, and the way to make that true is to have one implementation.
 *
 * Nothing here remembers a state. Every read asks the thing that owns it: the platform for the
 * chain, this app's settings for the gates, the server for whether it is running. Anything else
 * would describe the last tap rather than the phone.
 */
enum class LabSwitch(@StringRes val label: Int, val image: ImageVector) {

    /** The service itself: starting it, or stopping it the way the app's own stop does. */
    SERVICE(R.string.lab_switch_service, Icons.Outlined.PowerSettingsNew),

    FIREWALL(R.string.firewall_tile_label, Icons.Outlined.Shield),

    AUTOSTART(R.string.tab_autostart, Icons.Outlined.RestartAlt),

    /** All six hiding settings at once, for a phone that should stop hiding everything. */
    HIDING(R.string.lab_switch_hiding, Icons.Outlined.VisibilityOff),

    HIDE_DEVELOPER_OPTIONS(R.string.hiding_signal_developer_options, Icons.Outlined.DeveloperMode),
    HIDE_USB_DEBUGGING(R.string.hiding_signal_usb_debugging, Icons.Outlined.Usb),
    HIDE_WIRELESS_DEBUGGING(R.string.hiding_signal_wireless_debugging, Icons.Outlined.WifiOff),
    HIDE_ACCESSIBILITY(R.string.hiding_signal_accessibility, Icons.Outlined.AccessibilityNew),
    HIDE_PRIVATE_DNS(R.string.hiding_signal_private_dns, Icons.Outlined.Dns),
    HIDE_VPN(R.string.hiding_signal_vpn, Icons.Outlined.VpnKey),

    FORCE_DARK(R.string.tab_force_dark, Icons.Outlined.DarkMode),

    WATCHDOG(R.string.lab_switch_watchdog, Icons.Outlined.MonitorHeart);

    /** The one hiding setting this switch is, or null for every other kind of switch. */
    private val signal: Signal?
        get() = when (this) {
            HIDE_DEVELOPER_OPTIONS -> Signal.DEVELOPER_OPTIONS
            HIDE_USB_DEBUGGING -> Signal.USB_DEBUGGING
            HIDE_WIRELESS_DEBUGGING -> Signal.WIRELESS_DEBUGGING
            HIDE_ACCESSIBILITY -> Signal.ACCESSIBILITY
            HIDE_PRIVATE_DNS -> Signal.PRIVATE_DNS
            HIDE_VPN -> Signal.VPN
            else -> null
        }

    /**
     * Whether the feature is on, or null when nothing could be asked.
     *
     * A start that is under way reads as on and a stop that is under way reads as off: what the
     * switch is showing is what the phone is doing about it, and a service caught mid-change is
     * not in the state it is leaving.
     */
    fun isOn(): Boolean? = when (this) {
        // The binder first, because a process that has just started has not heard anything about
        // the server yet and a tile in the shade must not say Off about one that is running. A
        // start still under way counts as on, which is the state it is heading for.
        SERVICE -> Shizuku.pingBinder() || ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING

        FIREWALL -> FirewallChain.isOn()

        AUTOSTART -> AutoStartGate.isOn()

        // Any of the six being on counts, because the switch is about the feature doing
        // something: turning it off is how a phone stops hiding, and that is the direction
        // somebody reaches for this in a hurry.
        HIDING -> Signal.entries.any { Hiding.isSignalEnabled(it) }

        FORCE_DARK -> ForceDark.isEnabled()

        WATCHDOG -> ShizukuSettings.getWatchdog()

        else -> signal?.let { Hiding.isSignalEnabled(it) }
    }

    /**
     * Puts the feature in [on], answering the state to show afterwards, or null when it could not
     * be told.
     *
     * Called off the main thread: the firewall and autostart switches run shell commands, and the
     * service one can run a whole start.
     */
    fun apply(context: Context, on: Boolean): Boolean? = when (this) {
        // The start is a request rather than a result: what it answers is the request, and the
        // switch shows on as soon as the state machine does, which is inside this call.
        SERVICE -> if (on) {
            ShizukuReceiverStarter.start(context, userInitiated = true, reason = "switch")
            true
        } else if (stopShizuku(context)) {
            false
        } else {
            isOn()
        }

        FIREWALL -> if (FirewallChain.setOn(on)) on else FirewallChain.isOn()

        AUTOSTART -> if (AutoStartGate.setOn(context, on)) on else isOn()

        HIDING -> {
            Signal.entries.forEach { Hiding.setSignalEnabled(it, on) }
            HidingWatchService.refresh(context)
            on
        }

        FORCE_DARK -> {
            ForceDark.setEnabled(on)
            // The watch starts and stops with the mode, the same way it does from the screen.
            HidingWatchService.refresh(context)
            on
        }

        WATCHDOG -> {
            // The setting starts and stops the service and arms the alarm that brings it back.
            ShizukuSettings.setWatchdog(context, on)
            on
        }

        else -> {
            val signal = signal ?: return null
            Hiding.setSignalEnabled(signal, on)
            HidingWatchService.refresh(context)
            on
        }
    }

    /** Switches the feature to its other state, answering the new one, or null when it could not. */
    fun toggle(context: Context): Boolean? {
        val current = isOn() ?: return null
        return apply(context, !current)
    }

    /**
     * What the two surfaces outside the app show for a state.
     *
     * One mapping rather than one per surface: the tile and the widget sit on the same screen a
     * few inches apart, and the quickest way to make them contradict each other is to spell the
     * three answers out twice. Unknown is the third answer, and it has a label of its own, because
     * a state that could not be read is not the same thing as a feature that is off.
     */
    enum class Look(@StringRes val label: Int) {
        ON(R.string.lab_state_on),
        OFF(R.string.lab_state_off),
        UNKNOWN(R.string.lab_state_unavailable);

        companion object {
            fun of(on: Boolean?): Look = when (on) {
                true -> ON
                false -> OFF
                null -> UNKNOWN
            }
        }
    }

    companion object {
        /** Sent when a switch changed, so the widget for it shows what the tile just did. */
        const val ACTION_CHANGED = "moe.shizuku.manager.action.LAB_SWITCH_CHANGED"

        /** Which switch changed, as [LabSwitch.name]. */
        const val EXTRA_SWITCH = "moe.shizuku.manager.extra.LAB_SWITCH"

        /** Tells the widget for [switch] that the tile changed it. */
        fun notifyChanged(context: Context, switch: LabSwitch) {
            context.sendBroadcast(
                android.content.Intent(ACTION_CHANGED)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_SWITCH, switch.name)
            )
        }
    }
}
