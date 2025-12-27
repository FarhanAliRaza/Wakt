package com.example.wakt.presentation.activities

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wakt.data.database.entity.BrickSessionType
import com.example.wakt.presentation.activities.viewmodel.BrickLauncherViewModel
import com.example.wakt.presentation.ui.theme.WaktTheme
import com.example.wakt.utils.BrickSessionManager
import com.example.wakt.utils.EssentialAppsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class BrickLauncherActivity : ComponentActivity() {
    
    @Inject
    lateinit var brickSessionManager: BrickSessionManager
    
    @Inject
    lateinit var essentialAppsManager: EssentialAppsManager
    
    private var isActivityVisible = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make this a launcher activity
        setTaskDescription(
            android.app.ActivityManager.TaskDescription(
                "Focus Mode",
                null,
                android.graphics.Color.BLACK // Use a fixed color instead of theme
            )
        )
        
        // Handle back button - prevent going back during session
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Don't allow back navigation during brick mode - just stay here
            }
        })
        
        // No longer use screen pinning - AccessibilityService handles blocking
        
        setContent {
            WaktTheme {
                BrickLauncherScreen(
                    brickSessionManager = brickSessionManager,
                    essentialAppsManager = essentialAppsManager,
                    onLaunchApp = { packageName ->
                        launchApp(packageName)
                    },
                    onEmergencyOverride = {
                        // Launch emergency override activity
                        val intent = Intent(this@BrickLauncherActivity, EmergencyOverrideActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        
        // Check if session is still active
        if (!brickSessionManager.isPhoneBricked()) {
            // Session ended, close this activity
            finish()
        }
    }
    
    override fun onPause() {
        super.onPause()
        isActivityVisible = false
    }
    
    override fun onStop() {
        super.onStop()
        isActivityVisible = false
    }
    
    
    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                
                // Log app access
                CoroutineScope(Dispatchers.IO).launch {
                    brickSessionManager.logEssentialAppAccess(packageName)
                }
            }
        } catch (e: Exception) {
            // Silently fail - user shouldn't know about errors
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up
        
        // No longer use screen pinning - AccessibilityService handles blocking
    }
    
    
    companion object {
        private const val TAG = "BrickLauncherActivity"
    }
}

@Composable
fun BrickLauncherScreen(
    brickSessionManager: BrickSessionManager,
    essentialAppsManager: EssentialAppsManager,
    onLaunchApp: (String) -> Unit,
    onEmergencyOverride: () -> Unit,
    viewModel: BrickLauncherViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Current time for clock display
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000) // Update every second
        }
    }
    
    // Get current session info
    val currentSession = brickSessionManager.getCurrentSession()
    val remainingMinutes = brickSessionManager.getCurrentSessionRemainingMinutes()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Large clock display
            ClockDisplay(currentTime)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Session info card
            if (currentSession != null) {
                SessionInfoCard(
                    sessionName = currentSession.name,
                    sessionType = currentSession.sessionType,
                    remainingMinutes = remainingMinutes ?: 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Essential apps grid
            if (uiState.essentialApps.isNotEmpty()) {
                Text(
                    text = "Essential Apps",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.essentialApps) { app ->
                        EssentialAppIcon(
                            appName = app.appName,
                            packageName = app.packageName,
                            onClick = { onLaunchApp(app.packageName) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Focus Mode Active\\n\\nOnly emergency functions available",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Emergency access button (if allowed)
            if (currentSession?.allowEmergencyOverride == true) {
                OutlinedButton(
                    onClick = onEmergencyOverride,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Emergency Access")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Always show emergency call button
            Button(
                onClick = { 
                    // Launch emergency dialer
                    try {
                        val dialerIntent = Intent(Intent.ACTION_DIAL)
                        dialerIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(dialerIntent)
                    } catch (e: Exception) {
                        // Silently fail if no dialer available
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Emergency Call")
            }
        }
    }
}

@Composable
private fun ClockDisplay(currentTimeMillis: Long) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatter.format(Date(currentTimeMillis)),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = dateFormatter.format(Date(currentTimeMillis)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionInfoCard(
    sessionName: String,
    sessionType: BrickSessionType,
    remainingMinutes: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when (sessionType) {
                BrickSessionType.FOCUS_SESSION -> MaterialTheme.colorScheme.primaryContainer
                BrickSessionType.SLEEP_SCHEDULE -> MaterialTheme.colorScheme.secondaryContainer
                BrickSessionType.DIGITAL_DETOX -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = sessionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (sessionType) {
                    BrickSessionType.FOCUS_SESSION -> MaterialTheme.colorScheme.onPrimaryContainer
                    BrickSessionType.SLEEP_SCHEDULE -> MaterialTheme.colorScheme.onSecondaryContainer
                    BrickSessionType.DIGITAL_DETOX -> MaterialTheme.colorScheme.onErrorContainer
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val timeText = when {
                remainingMinutes >= 60 -> {
                    val hours = remainingMinutes / 60
                    val mins = remainingMinutes % 60
                    if (mins > 0) "${hours}h ${mins}m remaining" else "${hours}h remaining"
                }
                remainingMinutes > 0 -> "${remainingMinutes}m remaining"
                else -> "Ending soon..."
            }
            
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyLarge,
                color = when (sessionType) {
                    BrickSessionType.FOCUS_SESSION -> MaterialTheme.colorScheme.onPrimaryContainer
                    BrickSessionType.SLEEP_SCHEDULE -> MaterialTheme.colorScheme.onSecondaryContainer
                    BrickSessionType.DIGITAL_DETOX -> MaterialTheme.colorScheme.onErrorContainer
                }.copy(alpha = 0.8f)
            )
            
            when (sessionType) {
                BrickSessionType.FOCUS_SESSION -> Text(
                    text = "🎯 Stay focused!",
                    style = MaterialTheme.typography.bodyMedium
                )
                BrickSessionType.SLEEP_SCHEDULE -> Text(
                    text = "😴 Time to rest",
                    style = MaterialTheme.typography.bodyMedium
                )
                BrickSessionType.DIGITAL_DETOX -> Text(
                    text = "🧘 Digital detox in progress",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun EssentialAppIcon(
    appName: String,
    packageName: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Get app icon (simplified for now, could be enhanced with actual icon loading)
    val appIcon = remember {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // For now, show first letter of app name
            // In a real implementation, you'd load the actual app icon
            Text(
                text = appName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = appName,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}