package com.ventoux.netlens.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ventoux.netlens.R
import com.ventoux.netlens.core.data.preferences.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PostureTileService : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileEntryPoint {
        fun userPreferencesRepository(): UserPreferencesRepository
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            TileEntryPoint::class.java,
        )
        val score = runBlocking {
            entryPoint.userPreferencesRepository().postureScore.first()
        }
        if (score != null) {
            tile.label = "Score: ${score.grade}"
            tile.subtitle = when {
                score.issueCount == 0 -> "No issues"
                score.issueCount == 1 -> "1 issue"
                else -> "${score.issueCount} issues"
            }
            tile.state = when (score.grade) {
                "A", "B" -> Tile.STATE_ACTIVE
                "C" -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_shield)
        } else {
            tile.label = "Security"
            tile.subtitle = "Tap to scan"
            tile.state = Tile.STATE_INACTIVE
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_shield)
        }
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("netlens://feature/posture")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
