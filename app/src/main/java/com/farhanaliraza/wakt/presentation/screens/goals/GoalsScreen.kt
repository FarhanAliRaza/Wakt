package com.farhanaliraza.wakt.presentation.screens.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.farhanaliraza.wakt.data.database.entity.DailyGoal
import com.farhanaliraza.wakt.utils.GoalWallpaperUpdater

/**
 * Daily promises with streak counters. Each goal is answered once a day, and
 * the streaks can be painted onto the wallpaper so they stay in view.
 */
@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val wallpaperEnabled by viewModel.wallpaperEnabled.collectAsStateWithLifecycle()
    val wallpaperTarget by viewModel.wallpaperTarget.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DailyGoal?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Daily Goals",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Promises you check in on once a day. Keep the streak alive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                WallpaperCard(
                    enabled = wallpaperEnabled,
                    target = wallpaperTarget,
                    onEnabledChange = { viewModel.setWallpaperEnabled(it) },
                    onTargetChange = { viewModel.setWallpaperTarget(it) }
                )
            }

            if (rows.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No goals yet",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to add one, for example \"No reels\" or \"No porn\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(rows, key = { it.goal.id }) { row ->
                    GoalCard(
                        row = row,
                        onKept = { viewModel.checkIn(row.goal, true) },
                        onSlipped = { viewModel.checkIn(row.goal, false) },
                        onUndo = { viewModel.clearToday(row.goal) },
                        onDelete = { deleteTarget = row.goal }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add goal")
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onConfirm = { title ->
                viewModel.addGoal(title)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    deleteTarget?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${goal.title}\"?") },
            text = { Text("Its whole check-in history goes with it.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGoal(goal)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun WallpaperCard(
    enabled: Boolean,
    target: String,
    onEnabledChange: (Boolean) -> Unit,
    onTargetChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show streaks on wallpaper",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Paints \"Day N\" for each goal onto your wallpaper and repaints it after midnight, so you see it without opening Wakt. Replaces your current wallpaper.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        GoalWallpaperUpdater.TARGET_LOCK to "Lock screen",
                        GoalWallpaperUpdater.TARGET_HOME to "Home",
                        GoalWallpaperUpdater.TARGET_BOTH to "Both"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = target == value,
                            onClick = { onTargetChange(value) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    row: GoalRow,
    onKept: () -> Unit,
    onSlipped: () -> Unit,
    onUndo: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete goal")
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${row.currentStreak}",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = if (row.currentStreak == 1) "day streak" else "days streak",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "best ${row.bestStreak}, kept ${row.totalSuccessDays} days total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (row.todayState) {
                true -> StatusLine(text = "Today: kept it", positive = true, onUndo = onUndo)
                false -> StatusLine(text = "Today: slipped. Tomorrow is a new day.", positive = false, onUndo = onUndo)
                null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onKept, modifier = Modifier.weight(1f)) { Text("I kept it") }
                    OutlinedButton(onClick = onSlipped, modifier = Modifier.weight(1f)) { Text("I slipped") }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, positive: Boolean, onUndo: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (positive) androidx.compose.ui.graphics.Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onUndo) { Text("Undo") }
    }
}

@Composable
private fun AddGoalDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New daily goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Phrase it as the promise you check every day.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 40) title = it },
                    label = { Text("Goal") },
                    placeholder = { Text("e.g. No reels") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title) }, enabled = title.trim().isNotEmpty()) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
