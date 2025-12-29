package com.example.wakt.presentation.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wakt.presentation.ui.theme.ShadcnColors
import com.example.wakt.presentation.ui.theme.WaktTheme

/**
 * Full-screen overlay activity that shows a reminder before a scheduled session starts.
 * This cannot be ignored like a regular notification - user must tap "Got it" to dismiss.
 */
class ScheduleReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionName = intent.getStringExtra("sessionName") ?: "Scheduled Lock"
        val minutesUntilStart = intent.getIntExtra("minutesUntilStart", 15)

        setContent {
            WaktTheme {
                ScheduleReminderScreen(
                    sessionName = sessionName,
                    minutesUntilStart = minutesUntilStart,
                    onDismiss = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_NAME = "sessionName"
        const val EXTRA_MINUTES_UNTIL_START = "minutesUntilStart"
    }
}

@Composable
fun ScheduleReminderScreen(
    sessionName: String,
    minutesUntilStart: Int,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Bell icon
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = ShadcnColors.Blue500
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Reminder title
            Text(
                text = "Upcoming Lock",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Session name
            Text(
                text = sessionName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Time until start
            val timeText = when {
                minutesUntilStart >= 60 -> {
                    val hours = minutesUntilStart / 60
                    val mins = minutesUntilStart % 60
                    if (mins > 0) "Starts in ${hours}h ${mins}m" else "Starts in ${hours}h"
                }
                minutesUntilStart > 1 -> "Starts in $minutesUntilStart minutes"
                minutesUntilStart == 1 -> "Starts in 1 minute"
                else -> "Starting now..."
            }

            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Helpful message
            Text(
                text = "Finish what you're doing and prepare to disconnect",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Dismiss button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ShadcnColors.Blue500
                )
            ) {
                Text(
                    text = "Got it",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
