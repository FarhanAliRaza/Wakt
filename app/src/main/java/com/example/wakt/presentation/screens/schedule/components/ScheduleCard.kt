package com.example.wakt.presentation.screens.schedule.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakt.data.database.entity.PhoneBrickSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleCard(
    schedule: PhoneBrickSession,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    remainingLockDays: Int? = null,
    onLockClick: (() -> Unit)? = null,
    onUnlockClick: (() -> Unit)? = null
) {
    Card(
        onClick = { if (!isLocked) onClick() },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Time range with lock icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatTimeRange(schedule),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isLocked) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

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

                    // Lock status
                    if (isLocked && remainingLockDays != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val expiryDate = schedule.lockExpiresAt?.let {
                            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it))
                        } ?: ""
                        Text(
                            text = "Locked until $expiryDate ($remainingLockDays days)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Switch(
                    checked = schedule.isActive,
                    onCheckedChange = { if (!isLocked) onToggle(it) },
                    enabled = !isLocked,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            // Lock/Unlock buttons
            if (onLockClick != null || onUnlockClick != null) {
                Spacer(modifier = Modifier.height(12.dp))

                if (isLocked && onUnlockClick != null) {
                    OutlinedButton(
                        onClick = onUnlockClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Early")
                    }
                } else if (!isLocked && onLockClick != null) {
                    OutlinedButton(
                        onClick = onLockClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lock Schedule")
                    }
                }
            }
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
