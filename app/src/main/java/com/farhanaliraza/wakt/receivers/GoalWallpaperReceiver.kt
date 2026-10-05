package com.farhanaliraza.wakt.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.farhanaliraza.wakt.utils.GoalWallpaperUpdater
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fired shortly after midnight to repaint the streak wallpaper for the new day. */
@AndroidEntryPoint
class GoalWallpaperReceiver : BroadcastReceiver() {

    @Inject lateinit var goalWallpaperUpdater: GoalWallpaperUpdater

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) return
        goalWallpaperUpdater.refresh()
        goalWallpaperUpdater.scheduleMidnightRefresh()
    }

    companion object {
        const val ACTION_REFRESH = "com.farhanaliraza.wakt.action.REFRESH_GOAL_WALLPAPER"
    }
}
