package com.farhanaliraza.wakt.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RepeatSelector(
    selectedDays: Set<Int>, // 1-7 for Mon-Sun
    onDaysChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Preset options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                label = "Every day",
                selected = selectedDays == setOf(1, 2, 3, 4, 5, 6, 7),
                onClick = { onDaysChange(setOf(1, 2, 3, 4, 5, 6, 7)) },
                modifier = Modifier.weight(1f)
            )
            PresetChip(
                label = "Weekdays",
                selected = selectedDays == setOf(1, 2, 3, 4, 5),
                onClick = { onDaysChange(setOf(1, 2, 3, 4, 5)) },
                modifier = Modifier.weight(1f)
            )
            PresetChip(
                label = "Weekend",
                selected = selectedDays == setOf(6, 7),
                onClick = { onDaysChange(setOf(6, 7)) },
                modifier = Modifier.weight(1f)
            )
        }

        // Individual day selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val days = listOf("M" to 1, "T" to 2, "W" to 3, "T" to 4, "F" to 5, "S" to 6, "S" to 7)

            days.forEach { (label, dayIndex) ->
                DayChip(
                    label = label,
                    selected = selectedDays.contains(dayIndex),
                    onClick = {
                        val newDays = if (selectedDays.contains(dayIndex)) {
                            selectedDays - dayIndex
                        } else {
                            selectedDays + dayIndex
                        }
                        onDaysChange(newDays)
                    }
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    val textColor = if (selected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    val textColor = if (selected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// Helper function to convert Set<Int> to activeDaysOfWeek string
fun daysSetToString(days: Set<Int>): String {
    return days.sorted().joinToString("")
}

// Helper function to convert activeDaysOfWeek string to Set<Int>
fun stringToDaysSet(daysString: String): Set<Int> {
    return daysString.mapNotNull { it.toString().toIntOrNull() }.toSet()
}
