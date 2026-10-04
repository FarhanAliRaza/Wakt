package com.farhanaliraza.wakt.presentation.screens.dnslog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.farhanaliraza.wakt.services.DnsLogEntry
import com.farhanaliraza.wakt.services.WebsiteBlockingVpnService
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live view of every DNS query the website filter handled: domain, record
 * type, what happened to it, which app asked, and which server it addressed.
 * "Copy" puts the whole log on the clipboard for sharing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLogScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val entries by WebsiteBlockingVpnService.dnsLog.collectAsStateWithLifecycle()

    var running by remember { mutableStateOf(WebsiteBlockingVpnService.isServiceRunning) }
    var diagnostics by remember { mutableStateOf(WebsiteBlockingVpnService.diagnostics) }
    LaunchedEffect(Unit) {
        while (true) {
            running = WebsiteBlockingVpnService.isServiceRunning
            diagnostics = WebsiteBlockingVpnService.diagnostics
            delay(1000)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { copyLog(context, diagnostics, entries, timeFormat) }) {
                        Text("Copy")
                    }
                    TextButton(onClick = { WebsiteBlockingVpnService.clearDnsLog() }) {
                        Text("Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (running) "Filter running" else "Filter not running",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (running) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = diagnostics,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${entries.size} entries, newest first. Blocked names answer NXDOMAIN. " +
                            "If a site still loads, look for its name here: missing means the app " +
                            "resolved it without system DNS (own DoH, cached IP, or an open connection).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No DNS queries seen yet.\nOpen a website and come back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries) { entry ->
                        DnsLogRow(entry = entry, timeFormat = timeFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsLogRow(entry: DnsLogEntry, timeFormat: SimpleDateFormat) {
    val actionColor = when (entry.action) {
        "BLOCKED", "BLOCKED-DOH" -> Color(0xFFEF4444)
        "CACHED" -> Color(0xFF3B82F6)
        "TIMEOUT" -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.domain,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = entry.action,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = actionColor
            )
        }
        Text(
            text = "${timeFormat.format(Date(entry.timeMillis))}  ${entry.queryType}  " +
                "${entry.app ?: "unknown app"}  → ${entry.server}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun copyLog(
    context: Context,
    diagnostics: String,
    entries: List<DnsLogEntry>,
    timeFormat: SimpleDateFormat
) {
    val text = buildString {
        appendLine("Wakt DNS log")
        appendLine(diagnostics)
        appendLine()
        for (e in entries) {
            appendLine(
                "${timeFormat.format(Date(e.timeMillis))} ${e.action.padEnd(9)} ${e.queryType.padEnd(5)} " +
                    "${e.domain}  app=${e.app ?: "?"}  server=${e.server}"
            )
        }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Wakt DNS log", text))
    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
}
