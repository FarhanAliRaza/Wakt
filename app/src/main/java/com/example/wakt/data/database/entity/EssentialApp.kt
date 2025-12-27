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
 * Includes multiple package variants for different device manufacturers
 */
object DefaultEssentialApps {
    val SYSTEM_ESSENTIALS = listOf(
        // Phone/Dialer apps - multiple variants
        EssentialApp(
            appName = "Phone",
            packageName = "com.android.dialer",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Phone",
            packageName = "com.google.android.dialer",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Phone",
            packageName = "com.samsung.android.dialer",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Phone",
            packageName = "com.android.phone",
            isSystemEssential = true,
            isUserAdded = false
        ),
        // Emergency SOS
        EssentialApp(
            appName = "Emergency SOS",
            packageName = "com.android.emergency",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Emergency SOS",
            packageName = "com.samsung.android.emergencysos",
            isSystemEssential = true,
            isUserAdded = false
        ),
        // Messages apps - multiple variants
        EssentialApp(
            appName = "Messages",
            packageName = "com.android.messaging",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Messages",
            packageName = "com.google.android.apps.messaging",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Messages",
            packageName = "com.samsung.android.messaging",
            isSystemEssential = true,
            isUserAdded = false
        ),
        EssentialApp(
            appName = "Messages",
            packageName = "com.android.mms",
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