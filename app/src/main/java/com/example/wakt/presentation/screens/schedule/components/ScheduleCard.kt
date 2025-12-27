package com.example.wakt.presentation.screens.schedule.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakt.data.database.entity.PhoneBrickSession

@Composable
fun ScheduleCard(
    schedule: PhoneBrickSession,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Time range
                Text(
                    text = formatTimeRange(schedule),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Repeat pattern
                Text(
                    text = formatRepeatPattern(schedule.activeDaysOfWeek),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Name if exists
                if (schedule.name.isNotBlank() && schedule.name != "Schedule") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = schedule.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = schedule.isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

private fun formatTimeRange(schedule: PhoneBrickSession): String {
    val startHour = schedule.startHour ?: 0
    val startMinute = schedule.startMinute ?: 0
    val endHour = schedule.endHour ?: 0
    val endMinute = schedule.endMinute ?: 0

    return "${formatTime(startHour, startMinute)} - ${formatTime(endHour, endMinute)}"
}

private fun formatTime(hour: Int, minute: Int): String {
    return String.format("%02d:%02d", hour, minute)
}

private fun formatRepeatPattern(activeDays: String): String {
    return when {
        activeDays == "1234567" -> "Every day"
        activeDays == "12345" -> "Weekdays"
        activeDays == "67" -> "Weekend"
        activeDays.length == 1 -> {
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val dayIndex = activeDays.toIntOrNull()?.minus(1) ?: 0
            if (dayIndex in 0..6) dayNames[dayIndex] else "One-time"
        }
        activeDays.isEmpty() -> "One-time"
        else -> {
            val dayAbbreviations = listOf("M", "T", "W", "T", "F", "S", "S")
            activeDays.mapNotNull { char ->
                val index = char.toString().toIntOrNull()?.minus(1)
                if (index != null && index in 0..6) dayAbbreviations[index] else null
            }.joinToString(", ")
        }
    }
}
