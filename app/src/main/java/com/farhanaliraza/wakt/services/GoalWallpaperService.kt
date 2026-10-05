package com.farhanaliraza.wakt.services

import android.graphics.Canvas
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.farhanaliraza.wakt.data.database.dao.DailyGoalDao
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import com.farhanaliraza.wakt.utils.GoalWallpaperRenderer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject

/**
 * Live wallpaper showing goal streaks. Draws only when it becomes visible or
 * when the data changes, so it costs nothing while the screen is off, and it
 * never replaces the wallpaper image (which made some launchers jump home).
 */
@AndroidEntryPoint
class GoalWallpaperService : WallpaperService() {

    @Inject lateinit var dailyGoalDao: DailyGoalDao
    @Inject lateinit var globalSettingsManager: GlobalSettingsManager

    companion object {
        private const val TAG = "GoalWallpaper"
        private val listeners = CopyOnWriteArraySet<() -> Unit>()

        /** Ask every live engine to redraw (after a check-in, a new goal, midnight). */
        fun notifyChanged() {
            for (listener in listeners) listener()
        }
    }

    override fun onCreateEngine(): Engine = GoalEngine()

    private inner class GoalEngine : Engine() {
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var visible = false
        private val listener: () -> Unit = { if (visible) requestDraw() }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            listeners.add(listener)
        }

        override fun onDestroy() {
            listeners.remove(listener)
            scope.cancel()
            super.onDestroy()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) requestDraw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            requestDraw()
        }

        private fun requestDraw() {
            scope.launch {
                val data = try {
                    withContext(Dispatchers.IO) {
                        val goals = dailyGoalDao.getActiveGoalsList().filter { it.showOnWallpaper }
                        val byGoal = dailyGoalDao.getAllCheckInsList().groupBy { it.goalId }
                        goals.map { goal ->
                            GoalWallpaperRenderer.GoalData(
                                goal = goal,
                                checkIns = byGoal[goal.id]?.associate { it.day to it.success } ?: emptyMap()
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load goals for wallpaper", e)
                    emptyList()
                }
                draw(data)
            }
        }

        private fun draw(data: List<GoalWallpaperRenderer.GoalData>) {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    GoalWallpaperRenderer.render(
                        canvas, canvas.width, canvas.height, data,
                        showTitles = globalSettingsManager.isGoalWallpaperShowTitles()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to draw wallpaper", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
