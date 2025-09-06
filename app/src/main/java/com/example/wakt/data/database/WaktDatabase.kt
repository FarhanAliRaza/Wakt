package com.example.wakt.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.wakt.data.database.dao.BlockedItemDao
import com.example.wakt.data.database.dao.GoalBlockDao
import com.example.wakt.data.database.dao.GoalBlockItemDao
import com.example.wakt.data.database.entity.BlockedItem
import com.example.wakt.data.database.entity.GoalBlock
import com.example.wakt.data.database.entity.GoalBlockItem

@Database(
    entities = [BlockedItem::class, GoalBlock::class, GoalBlockItem::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WaktDatabase : RoomDatabase() {
    abstract fun blockedItemDao(): BlockedItemDao
    abstract fun goalBlockDao(): GoalBlockDao
    abstract fun goalBlockItemDao(): GoalBlockItemDao
    
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
    }
}