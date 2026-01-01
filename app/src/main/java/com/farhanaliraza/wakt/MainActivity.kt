package com.farhanaliraza.wakt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.farhanaliraza.wakt.presentation.MainScaffold
import com.farhanaliraza.wakt.presentation.ui.theme.WaktTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaktTheme {
                MainScaffold()
            }
        }
    }
}
