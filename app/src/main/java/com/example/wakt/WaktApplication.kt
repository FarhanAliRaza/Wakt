package com.example.wakt

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WaktApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}