package com.farhanaliraza.wakt.utils

import com.farhanaliraza.wakt.data.database.dao.BlockedItemDao
import com.farhanaliraza.wakt.data.database.dao.GoalBlockDao
import com.farhanaliraza.wakt.data.database.dao.GoalBlockItemDao
import com.farhanaliraza.wakt.data.database.dao.PhoneBrickSessionDao
import com.farhanaliraza.wakt.data.database.entity.BlockType
import com.farhanaliraza.wakt.data.database.entity.ChallengeType
import com.farhanaliraza.wakt.data.database.entity.ScheduleTargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether an app should be blocked right now, independent of how the
 * foreground app was detected. Used by the enforcement service when the
 * accessibility service is not enabled.
 */
@Singleton
class AppBlockChecker @Inject constructor(
    private val blockedItemDao: BlockedItemDao,
    private val goalBlockDao: GoalBlockDao,
    private val goalBlockItemDao: GoalBlockItemDao,
    private val phoneBrickSessionDao: PhoneBrickSessionDao,
    private val temporaryUnlock: TemporaryUnlock
) {

    data class Decision(
        val name: String,
        val challengeType: ChallengeType,
        val challengeData: String,
        val isGoalBlock: Boolean,
        val isScheduledBlock: Boolean,
        val scheduleEndTime: Long
    )

    /** Returns how to block [packageName], or null when it is allowed right now. */
    suspend fun check(packageName: String): Decision? = withContext(Dispatchers.IO) {
        blockedItemDao.deleteExpiredBlocks()
        goalBlockDao.markExpiredGoalsAsCompleted()

        val blockedApp = blockedItemDao.getActiveBlockedItem(packageName)

        val goalBlock = if (blockedApp == null) {
            val goalItem = goalBlockItemDao.getActiveGoalItemForPackageOrUrl(packageName)
            if (goalItem != null) goalBlockDao.getGoalById(goalItem.goalId)
            else goalBlockDao.getActiveGoalBlock(packageName)
        } else null

        val scheduledBlock = if (blockedApp == null && goalBlock == null) {
            phoneBrickSessionDao.getActiveAppSchedulesForPackage(packageName)
                .find { ScheduleWindow.isNowInWindow(it) }
        } else null

        if (blockedApp == null && goalBlock == null && scheduledBlock == null) return@withContext null
        if (temporaryUnlock.isTemporarilyUnlocked(packageName)) return@withContext null

        when {
            blockedApp != null -> Decision(
                name = blockedApp.name,
                challengeType = blockedApp.challengeType,
                challengeData = blockedApp.challengeData,
                isGoalBlock = false,
                isScheduledBlock = false,
                scheduleEndTime = 0L
            )
            goalBlock != null -> Decision(
                name = goalBlock.name,
                challengeType = goalBlock.challengeType,
                challengeData = goalBlock.challengeData,
                isGoalBlock = true,
                isScheduledBlock = false,
                scheduleEndTime = 0L
            )
            else -> Decision(
                name = scheduledBlock!!.name,
                challengeType = scheduledBlock.challengeType,
                challengeData = scheduledBlock.challengeData,
                isGoalBlock = false,
                isScheduledBlock = true,
                scheduleEndTime = ScheduleWindow.endTimeMillis(scheduledBlock)
            )
        }
    }

    /** Whether anything exists that could block an app, so enforcement needs to run at all. */
    suspend fun hasAnyAppBlocks(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val hasItems = blockedItemDao.getAllBlockedItemsList().any {
            it.type == BlockType.APP && (it.blockEndTime == null || it.blockEndTime!! > now)
        }
        if (hasItems) return@withContext true
        if (goalBlockItemDao.getAllActiveGoalItems(now).any { it.itemType == BlockType.APP }) {
            return@withContext true
        }
        if (goalBlockDao.getAllActiveGoalsList().any { it.type == BlockType.APP }) {
            return@withContext true
        }
        phoneBrickSessionDao.getAllSessions().first().any {
            it.isActive && it.scheduleTargetType == ScheduleTargetType.APPS && it.targetPackages.isNotBlank()
        }
    }
}
