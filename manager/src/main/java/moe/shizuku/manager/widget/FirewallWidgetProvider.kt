package moe.shizuku.manager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.manage.FirewallChain

/**
 * The whole firewall as a home screen widget: the same switch as the tile, in the place somebody
 * looks at without pulling anything down.
 *
 * The state is read when the widget is drawn, never kept, so what it shows is the chain's state
 * rather than the last thing this app did with it. Off is drawn as the shield dimmed and the
 * word for it: the blocked list is still there and comes back untouched when it is switched on
 * again, which is exactly the difference between turning the firewall off and emptying it.
 *
 * The click is answered asynchronously. A widget provider that did its shell work on the main
 * thread would be a launcher waiting on a shell, and [goAsync] is how a broadcast says it is not
 * finished yet without holding the thread it arrived on.
 */
class FirewallWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        draw(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> toggle(context)
            FirewallChain.ACTION_CHANGED -> draw(context, widgetIds(context))
            else -> super.onReceive(context, intent)
        }
    }

    /** Switches the chain, then draws whatever it ended up as. */
    private fun toggle(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirewallChain.toggle()
                drawNow(context, widgetIds(context), FirewallChain.isOn())
            } finally {
                pending.finish()
            }
        }
    }

    /** Reads the chain state off the main thread, then draws every instance. */
    private fun draw(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                drawNow(context, ids, FirewallChain.isOn())
            } finally {
                pending.finish()
            }
        }
    }

    private fun drawNow(context: Context, ids: IntArray, on: Boolean?) {
        if (ids.isEmpty()) return

        val manager = AppWidgetManager.getInstance(context)
        for (id in ids) {
            manager.updateAppWidget(id, views(context, on))
        }
    }

    private fun views(context: Context, on: Boolean?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_firewall)

        views.setTextViewText(
            R.id.widget_state,
            context.getString(
                when (on) {
                    true -> R.string.firewall_state_on
                    false -> R.string.firewall_state_off
                    null -> R.string.firewall_state_unavailable
                }
            )
        )

        // Drawn rather than described: the same shield, dimmed when the chain is off, so the two
        // states are told apart at a glance from across the room.
        views.setInt(
            R.id.widget_icon,
            "setColorFilter",
            if (on == true) OPAQUE else DIMMED
        )

        views.setOnClickPendingIntent(R.id.widget_root, toggleIntent(context))
        return views
    }

    private fun toggleIntent(context: Context): PendingIntent {
        val intent = Intent(context, FirewallWidgetProvider::class.java).setAction(ACTION_TOGGLE)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, FirewallWidgetProvider::class.java))

    companion object {
        /** A click, from any instance. */
        const val ACTION_TOGGLE = "moe.shizuku.manager.action.FIREWALL_WIDGET_TOGGLE"

        private const val OPAQUE = 0xFFFFFFFF.toInt()
        private const val DIMMED = 0x66FFFFFF
    }
}
