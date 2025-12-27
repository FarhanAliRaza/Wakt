package com.example.wakt.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.wakt.data.database.entity.BlockType
import com.example.wakt.presentation.screens.addblock.AppInfo

data class SelectedItem(
    val name: String,
    val type: BlockType,
    val packageOrUrl: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap? = null
)

@Composable
fun MultiItemSelector(
    selectedItems: List<SelectedItem>,
    onAddApp: () -> Unit,
    onAddWebsite: () -> Unit,
    onRemoveItem: (SelectedItem) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Selected Items",
    showAddButtons: Boolean = true,
    showRemoveButtons: Boolean = true
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        
        if (selectedItems.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No items selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedItems) { item ->
                    SelectedItemCard(
                        item = item,
                        onRemove = { onRemoveItem(item) },
                        showRemoveButton = showRemoveButtons
                    )
                }
            }
        }
        
        if (showAddButtons) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAddApp,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("App", maxLines = 1)
                }
                
                OutlinedButton(
                    onClick = onAddWebsite,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Website", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SelectedItemCard(
    item: SelectedItem,
    onRemove: () -> Unit,
    showRemoveButton: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            item.icon?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } ?: Icon(
                imageVector = if (item.type == BlockType.APP) Icons.Default.Phone else Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.type.name}: ${item.packageOrUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (showRemoveButton) {
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// Helper function to convert AppInfo to SelectedItem
fun AppInfo.toSelectedItem(): SelectedItem {
    val bitmap = try {
        icon?.toBitmap()?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
    
    return SelectedItem(
        name = name,
        type = BlockType.APP,
        packageOrUrl = packageName,
        icon = bitmap
    )
}

// Helper function to create SelectedItem for website
fun createWebsiteSelectedItem(url: String): SelectedItem {
    return SelectedItem(
        name = url,
        type = BlockType.WEBSITE,
        packageOrUrl = url,
        icon = null
    )
}