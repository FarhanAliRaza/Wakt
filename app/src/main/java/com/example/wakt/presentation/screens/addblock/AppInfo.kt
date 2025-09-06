package com.example.wakt.presentation.screens.addblock

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable?,
        val iconBitmap: ImageBitmap? = null
) {
    // Override equals and hashCode to be based only on package name to avoid duplicates
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AppInfo
        return packageName == other.packageName
    }

    override fun hashCode(): Int {
        return packageName.hashCode()
    }
}
