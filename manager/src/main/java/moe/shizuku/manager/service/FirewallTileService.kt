package moe.shizuku.manager.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.manage.FirewallChain

/**
 * The whole firewall as one tile, for the moment a person wants it off without opening anything.
 *
 * The state shown is the platform's, asked when the shade is drawn rather than remembered, so a
 * change made anywhere else - the widget, the app, another firewall on the same chain - is what
 * the tile says. Unavailable is an answer of its own: without a running server neither the
 * command nor the interface behind it can be asked, and a tile that guessed would be wrong in one
 * direction or the other.
 *
 * The click does its shell work off the main thread: a tile service has a few seconds to start
 * and a shell round trip is not the place to spend them.
 */
class FirewallTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()

        scope.launch {
            val current = FirewallChain.isOn()
            if (current == null) {
                // Nothing can be switched: the chain cannot be read, which means the server is not
                // there to do it. Opening the app is where that is fixed; the tile says so by
                // itself, and a tap that did nothing at all would read as a broken tile.
                withContext(Dispatchers.Main) { openApp() }
                return@launch
            }

            FirewallChain.setOn(!current)
            FirewallChain.notifyChanged(this@FirewallTileService)
            show(FirewallChain.isOn())
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refresh() {
        scope.launch { show(FirewallChain.isOn()) }
    }

    /** Draws the tile for [on], on the main thread, which is the only one allowed to. */
    private suspend fun show(on: Boolean?) {
        withContext(Dispatchers.Main) {
            val tile = qsTile ?: return@withContext
            tile.state = when (on) {
                true -> Tile.STATE_ACTIVE
                false -> Tile.STATE_INACTIVE
                null -> Tile.STATE_UNAVAILABLE
            }
            tile.label = getString(R.string.firewall_tile_label)
            tile.icon = Icon.createWithResource(this@FirewallTileService, R.drawable.ic_firewall_tile)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(
                    when (on) {
                        true -> R.string.firewall_state_on
                        false -> R.string.firewall_state_off
                        null -> R.string.firewall_state_unavailable
                    }
                )
            }
            tile.updateTile()
        }
    }

    /** Opens the manager, collapsing the shade the way a tile click is allowed to. */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
