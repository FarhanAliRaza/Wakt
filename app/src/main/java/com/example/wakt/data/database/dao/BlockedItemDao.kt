package com.example.wakt.data.database.dao

import androidx.room.*
import com.example.wakt.data.database.entity.BlockedItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedItemDao {
    
    @Query("SELECT * FROM blocked_items")
    fun getAllBlockedItems(): Flow<List<BlockedItem>>
    
    @Query("SELECT * FROM blocked_items")
    suspend fun getAllBlockedItemsList(): List<BlockedItem>
    
    @Query("SELECT * FROM blocked_items WHERE id = :id")
    suspend fun getBlockedItemById(id: Long): BlockedItem?
    
    @Query("SELECT * FROM blocked_items WHERE packageNameOrUrl = :packageNameOrUrl")
    suspend fun getBlockedItemByPackageOrUrl(packageNameOrUrl: String): BlockedItem?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedItem(item: BlockedItem): Long
    
    @Update
    suspend fun updateBlockedItem(item: BlockedItem)
    
    @Delete
    suspend fun deleteBlockedItem(item: BlockedItem)
    
    @Query("DELETE FROM blocked_items WHERE id = :id")
    suspend fun deleteBlockedItemById(id: Long)
    
    @Query("SELECT * FROM blocked_items WHERE packageNameOrUrl = :packageNameOrUrl AND (blockEndTime IS NULL OR blockEndTime > :currentTime)")
    suspend fun getActiveBlockedItem(packageNameOrUrl: String, currentTime: Long = System.currentTimeMillis()): BlockedItem?
    
    @Query("DELETE FROM blocked_items WHERE blockEndTime IS NOT NULL AND blockEndTime <= :currentTime")
    suspend fun deleteExpiredBlocks(currentTime: Long = System.currentTimeMillis()): Int
}