package com.farhanaliraza.wakt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.farhanaliraza.wakt.presentation.MainScaffold
import com.farhanaliraza.wakt.presentation.onboarding.OnboardingScreen
import com.farhanaliraza.wakt.presentation.ui.theme.WaktTheme
import com.farhanaliraza.wakt.utils.GlobalSettingsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var globalSettingsManager: GlobalSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaktTheme {
                val onboardingDone by globalSettingsManager.onboardingCompleted.collectAsState()
                if (onboardingDone) {
                    MainScaffold()
                } else {
                    OnboardingScreen(onFinished = { globalSettingsManager.setOnboardingCompleted(true) })
                }
            }
        }
    }
}
