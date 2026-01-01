package com.farhanaliraza.wakt.data.database.entity

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
 * Note: Phone and Messages are handled via dynamic intent detection,
 * so they don't need hardcoded package variants here.
 */
object DefaultEssentialApps {
    val SYSTEM_ESSENTIALS = listOf(
        // Emergency SOS apps
        EssentialApp(
            appName = "Emergency SOS",
            packageName = "com.android.emergency",
            isSystemEssential = true,
            isUserAdded = false
        ),
        // Settings
        EssentialApp(
            appName = "Settings",
            packageName = "com.android.settings",
            isSystemEssential = true,
            isUserAdded = false,
            allowedSessionTypes = "SLEEP_SCHEDULE" // Only allowed during sleep schedule
        ),
        // Clock apps
        EssentialApp(
            appName = "Clock",
            packageName = "com.android.deskclock",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Clock",
            packageName = "com.google.android.deskclock",
            isSystemEssential = true,
            isUserAdded = false
        ),
        // Calculator apps
        EssentialApp(
            appName = "Calculator",
            packageName = "com.android.calculator2",
            isSystemEssential = true,
            isUserAdded = false,
            allowedSessionTypes = "FOCUS_SESSION,DIGITAL_DETOX"
        ),
        EssentialApp(
            appName = "Calculator",
            packageName = "com.google.android.calculator",
            isSystemEssential = true,
            isUserAdded = false,
            allowedSessionTypes = "FOCUS_SESSION,DIGITAL_DETOX"
        )
    )
}