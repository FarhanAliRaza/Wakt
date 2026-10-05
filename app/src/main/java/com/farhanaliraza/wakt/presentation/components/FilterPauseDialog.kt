package com.farhanaliraza.wakt.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.farhanaliraza.wakt.utils.FilterPauseManager

/**
 * Pick one app and a short duration to pause the website filter for. The pause
 * locks the phone to that app, so the dialog spells that out before confirming.
 */
@Composable
fun FilterPauseDialog(
    apps: List<FilterPauseManager.PausableApp>,
    loading: Boolean,
    onConfirm: (packageName: String, minutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FilterPauseManager.PausableApp?>(null) }
    var minutes by remember { mutableIntStateOf(FilterPauseManager.DURATION_OPTIONS.first()) }

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) || it.packageName.contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause filter for one app") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "For banking apps that refuse to run with a VPN. The website filter turns " +
                        "off and your phone locks to the app you pick. The filter comes back the moment " +
                        "the lock ends, or earlier if you leave the lock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Duration", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPauseManager.DURATION_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = minutes == option,
                            onClick = { minutes = option },
                            label = { Text("$option min") }
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )

                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else if (filtered.isEmpty()) {
                    Text(
                        text = "No app matches. Browsers and blocked apps are never offered here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            val isSelected = selected?.packageName == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = app }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = { selected = app })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selected?.let { onConfirm(it.packageName, minutes) } },
                enabled = selected != null
            ) {
                Text(selected?.let { "Lock to ${it.label} for $minutes min" } ?: "Pick an app")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
