package com.example.wakt.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "essential_apps")
data class EssentialApp(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appName: String,
    val packageName: String,
    val isSystemEssential: Boolean = false, // Pre-defined essential apps (phone, emergency)
    val isUserAdded: Boolean = true, // User-configured essential apps
    val allowedSessionTypes: String = "FOCUS_SESSION,SLEEP_SCHEDULE,DIGITAL_DETOX", // Comma-separated
    val addedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * Default system essential apps that should always be available
 */
object DefaultEssentialApps {
    val SYSTEM_ESSENTIALS = listOf(
        EssentialApp(
            appName = "Phone",
            packageName = "com.android.dialer",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Emergency SOS",
            packageName = "com.android.emergency",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Messages",
            packageName = "com.android.messaging",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Settings",
            packageName = "com.android.settings",
            isSystemEssential = true,
            isUserAdded = false,
            allowedSessionTypes = "SLEEP_SCHEDULE" // Only allowed during sleep schedule
        ),
        EssentialApp(
            appName = "Clock",
            packageName = "com.android.deskclock",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Calculator",
            packageName = "com.android.calculator2",
            isSystemEssential = true,
            isUserAdded = false,
            allowedSessionTypes = "FOCUS_SESSION,DIGITAL_DETOX" // Not allowed during sleep
        )
    )
}