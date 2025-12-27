package com.example.wakt.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.wakt.data.database.WaktDatabase
import com.example.wakt.data.database.dao.BlockedItemDao
import com.example.wakt.data.database.dao.GoalBlockDao
import com.example.wakt.data.database.dao.GoalBlockItemDao
import com.example.wakt.data.database.dao.PhoneBrickSessionDao
import com.example.wakt.data.database.dao.EssentialAppDao
import com.example.wakt.data.database.dao.BrickSessionLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE blocked_items ADD COLUMN blockDurationDays INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE blocked_items ADD COLUMN blockStartTime INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
            database.execSQL("ALTER TABLE blocked_items ADD COLUMN blockEndTime INTEGER DEFAULT NULL")
        }
    }
    
    @Provides
    @Singleton
    fun provideWaktDatabase(@ApplicationContext context: Context): WaktDatabase {
        return Room.databaseBuilder(
            context,
            WaktDatabase::class.java,
            "wakt_database"
        )
        .addMigrations(MIGRATION_1_2, WaktDatabase.MIGRATION_2_3, WaktDatabase.MIGRATION_3_4, WaktDatabase.MIGRATION_4_5, WaktDatabase.MIGRATION_5_6, WaktDatabase.MIGRATION_6_7)
        .fallbackToDestructiveMigration() // For development, remove in production
        .build()
    }
    
    @Provides
    fun provideBlockedItemDao(database: WaktDatabase): BlockedItemDao {
        return database.blockedItemDao()
    }
    
    @Provides
    fun provideGoalBlockDao(database: WaktDatabase): GoalBlockDao {
        return database.goalBlockDao()
    }
    
    @Provides
    fun provideGoalBlockItemDao(database: WaktDatabase): GoalBlockItemDao {
        return database.goalBlockItemDao()
    }
    
    @Provides
    fun providePhoneBrickSessionDao(database: WaktDatabase): PhoneBrickSessionDao {
        return database.phoneBrickSessionDao()
    }
    
    @Provides
    fun provideEssentialAppDao(database: WaktDatabase): EssentialAppDao {
        return database.essentialAppDao()
    }
    
    @Provides
    fun provideBrickSessionLogDao(database: WaktDatabase): BrickSessionLogDao {
        return database.brickSessionLogDao()
    }
}