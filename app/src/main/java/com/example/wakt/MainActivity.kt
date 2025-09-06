package com.example.wakt

import android.app.Activity
import android.content.Intent
// import android.net.VpnService // VPN service disabled for battery optimization
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.wakt.presentation.ui.theme.WaktTheme
import com.example.wakt.utils.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        // VPN permission disabled for battery optimization
        // var vpnPermissionCallback: (() -> Unit)? = null
    }
    
    // VPN permission launcher disabled - keeping for future use
    /*
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "VPN permission granted")
            vpnPermissionCallback?.invoke()
        } else {
            Log.d(TAG, "VPN permission denied")
        }
        vpnPermissionCallback = null
    }
    */
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            
            WaktTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WaktNavHost(
                        modifier = Modifier.padding(innerPadding)
                        // VPN permission request removed for battery optimization
                        // onVpnPermissionRequest = { callback ->
                        //     requestVpnPermission(callback)
                        // }
                    )
                }
            }
        }
    }
    
    // VPN permission function disabled - keeping for future use
    /*
    fun requestVpnPermission(callback: () -> Unit) {
        vpnPermissionCallback = callback
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.d(TAG, "Requesting VPN permission")
            vpnPermissionLauncher.launch(intent)
        } else {
            Log.d(TAG, "VPN permission already granted")
            callback()
        }
    }
    */
}