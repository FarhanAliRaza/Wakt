package com.example.wakt.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.wakt.data.database.dao.BlockedItemDao
import com.example.wakt.data.database.dao.GoalBlockDao
import com.example.wakt.data.database.dao.GoalBlockItemDao
import com.example.wakt.data.database.dao.PhoneBrickSessionDao
import com.example.wakt.data.database.dao.EssentialAppDao
import com.example.wakt.data.database.dao.BrickSessionLogDao
import com.example.wakt.data.database.entity.BlockedItem
import com.example.wakt.data.database.entity.GoalBlock
import com.example.wakt.data.database.entity.GoalBlockItem
import com.example.wakt.data.database.entity.PhoneBrickSession
import com.example.wakt.data.database.entity.EssentialApp
import com.example.wakt.data.database.entity.BrickSessionLog

@Database(
    entities = [
        BlockedItem::class,
        GoalBlock::class,
        GoalBlockItem::class,
        PhoneBrickSession::class,
        EssentialApp::class,
        BrickSessionLog::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WaktDatabase : RoomDatabase() {
    abstract fun blockedItemDao(): BlockedItemDao
    abstract fun goalBlockDao(): GoalBlockDao
    abstract fun goalBlockItemDao(): GoalBlockItemDao
    abstract fun phoneBrickSessionDao(): PhoneBrickSessionDao
    abstract fun essentialAppDao(): EssentialAppDao
    abstract fun brickSessionLogDao(): BrickSessionLogDao
    
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS goal_blocks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        packageNameOrUrl TEXT NOT NULL,
                        goalDurationDays INTEGER NOT NULL,
                        goalStartTime INTEGER NOT NULL,
                        goalEndTime INTEGER NOT NULL,
                        challengeType TEXT NOT NULL,
                        challengeData TEXT NOT NULL,
                        isActive INTEGER NOT NULL,
                        completedAt INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }
        
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new goal_block_items table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS goal_block_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        itemName TEXT NOT NULL,
                        itemType TEXT NOT NULL,
                        packageOrUrl TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        FOREIGN KEY (goalId) REFERENCES goal_blocks(id) ON DELETE CASCADE
                    )
                """)
                
                // Migrate existing goal_blocks data to the new structure
                // For each existing goal, create a corresponding goal_block_item
                database.execSQL("""
                    INSERT INTO goal_block_items (goalId, itemName, itemType, packageOrUrl, addedAt)
                    SELECT id, packageNameOrUrl, type, packageNameOrUrl, createdAt
                    FROM goal_blocks
                    WHERE packageNameOrUrl IS NOT NULL AND packageNameOrUrl != ''
                """)
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create phone_brick_sessions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS phone_brick_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        sessionType TEXT NOT NULL,
                        durationMinutes INTEGER,
                        startHour INTEGER,
                        startMinute INTEGER,
                        endHour INTEGER,
                        endMinute INTEGER,
                        activeDaysOfWeek TEXT NOT NULL DEFAULT '1234567',
                        isActive INTEGER NOT NULL DEFAULT 1,
                        isCurrentlyBricked INTEGER NOT NULL DEFAULT 0,
                        currentSessionStartTime INTEGER,
                        currentSessionEndTime INTEGER,
                        totalSessionsCompleted INTEGER NOT NULL DEFAULT 0,
                        totalSessionsBroken INTEGER NOT NULL DEFAULT 0,
                        lastCompletedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        challengeType TEXT NOT NULL DEFAULT 'WAIT',
                        challengeData TEXT NOT NULL DEFAULT '5',
                        allowEmergencyOverride INTEGER NOT NULL DEFAULT 1
                    )
                """)

                // Create essential_apps table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS essential_apps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        appName TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        isSystemEssential INTEGER NOT NULL DEFAULT 0,
                        isUserAdded INTEGER NOT NULL DEFAULT 1,
                        allowedSessionTypes TEXT NOT NULL DEFAULT 'FOCUS_SESSION,SLEEP_SCHEDULE,DIGITAL_DETOX',
                        addedAt INTEGER NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1
                    )
                """)

                // Create brick_session_logs table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS brick_session_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        sessionStartTime INTEGER NOT NULL,
                        sessionEndTime INTEGER,
                        scheduledDurationMinutes INTEGER NOT NULL,
                        actualDurationMinutes INTEGER,
                        completionStatus TEXT NOT NULL,
                        emergencyOverrideUsed INTEGER NOT NULL DEFAULT 0,
                        emergencyOverrideTime INTEGER,
                        emergencyOverrideReason TEXT,
                        bypassAttempts INTEGER NOT NULL DEFAULT 0,
                        appsAccessedDuringSession TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add allowedApps column to phone_brick_sessions table
                database.execSQL("""
                    ALTER TABLE phone_brick_sessions ADD COLUMN allowedApps TEXT NOT NULL DEFAULT ''
                """)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add schedule-specific columns to phone_brick_sessions table
                database.execSQL("""
                    ALTER TABLE phone_brick_sessions ADD COLUMN scheduleTargetType TEXT NOT NULL DEFAULT 'PHONE'
                """)
                database.execSQL("""
                    ALTER TABLE phone_brick_sessions ADD COLUMN targetPackages TEXT NOT NULL DEFAULT ''
                """)
                database.execSQL("""
                    ALTER TABLE phone_brick_sessions ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0
                """)
                database.execSQL("""
                    ALTER TABLE phone_brick_sessions ADD COLUMN reminderMinutesBefore INTEGER NOT NULL DEFAULT 15
                """)
                database.execSQL("""
                    ALTER TABLE phone_brick_sessions ADD COLUMN vibrate INTEGER NOT NULL DEFAULT 1
                """)
            }
        }
    }
}