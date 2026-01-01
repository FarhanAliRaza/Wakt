package com.farhanaliraza.wakt.data.database.dao

import androidx.room.*
import com.farhanaliraza.wakt.data.database.entity.EssentialApp
import com.farhanaliraza.wakt.data.database.entity.BrickSessionType
import kotlinx.coroutines.flow.Flow

@Dao
interface EssentialAppDao {
    
    @Query("SELECT * FROM essential_apps WHERE isActive = 1 ORDER BY isSystemEssential DESC, appName ASC")
    fun getAllEssentialApps(): Flow<List<EssentialApp>>
    
    @Query("SELECT * FROM essential_apps WHERE isActive = 1 ORDER BY isSystemEssential DESC, appName ASC")
    suspend fun getAllEssentialAppsList(): List<EssentialApp>
    
    @Query("SELECT * FROM essential_apps WHERE isSystemEssential = 1 AND isActive = 1")
    suspend fun getSystemEssentialApps(): List<EssentialApp>
    
    @Query("SELECT * FROM essential_apps WHERE isUserAdded = 1 AND isActive = 1")
    suspend fun getUserEssentialApps(): List<EssentialApp>
    
    @Query("SELECT * FROM essential_apps WHERE packageName = :packageName AND isActive = 1 LIMIT 1")
    suspend fun getEssentialAppByPackage(packageName: String): EssentialApp?
    
    // Get essential apps that are allowed for a specific session type
    @Query("""
        SELECT * FROM essential_apps 
        WHERE isActive = 1 
        AND (allowedSessionTypes LIKE '%' || :sessionType || '%' OR allowedSessionTypes = '')
        ORDER BY isSystemEssential DESC, appName ASC
    """)
    suspend fun getEssentialAppsForSessionType(sessionType: String): List<EssentialApp>
    
    // Check if an app is essential for a specific session type
    @Query("""
        SELECT COUNT(*) > 0 FROM essential_apps 
        WHERE packageName = :packageName 
        AND isActive = 1 
        AND (allowedSessionTypes LIKE '%' || :sessionType || '%' OR allowedSessionTypes = '')
    """)
    suspend fun isAppEssentialForSessionType(packageName: String, sessionType: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEssentialApp(app: EssentialApp): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE) 
    suspend fun insertEssentialApps(apps: List<EssentialApp>)
    
    @Update
    suspend fun updateEssentialApp(app: EssentialApp)
    
    @Delete
    suspend fun deleteEssentialApp(app: EssentialApp)
    
    @Query("DELETE FROM essential_apps WHERE id = :id")
    suspend fun deleteEssentialAppById(id: Long)
    
    @Query("DELETE FROM essential_apps WHERE packageName = :packageName")
    suspend fun deleteEssentialAppByPackage(packageName: String)
    
    @Query("UPDATE essential_apps SET isActive = 0 WHERE id = :id")
    suspend fun deactivateEssentialApp(id: Long)
    
    @Query("UPDATE essential_apps SET isActive = 1 WHERE id = :id")
    suspend fun activateEssentialApp(id: Long)
    
    // Initialize system essential apps if they don't exist
    @Query("SELECT COUNT(*) FROM essential_apps WHERE isSystemEssential = 1")
    suspend fun getSystemEssentialAppsCount(): Int
}