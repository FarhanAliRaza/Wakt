package com.farhanaliraza.wakt.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.farhanaliraza.wakt.R
import com.farhanaliraza.wakt.utils.PermissionHelper
import kotlinx.coroutines.launch

/** What a permission page needs to show and do. */
private enum class PermissionKind { USAGE_ACCESS, OVERLAY, BATTERY, ACCESSIBILITY }

private data class OnboardingPage(
    val illustration: Int,
    val title: String,
    val body: String,
    val permission: PermissionKind? = null,
    val optional: Boolean = false,
    val why: String? = null,
    val bullets: List<String> = emptyList()
)

private val pages = listOf(
    OnboardingPage(
        illustration = R.drawable.onb_welcome,
        title = "Take your focus back",
        body = "Wakt blocks the apps and websites that pull you in, locks your phone for focus sessions, and keeps you honest with daily goals.",
        bullets = listOf("Block apps and sites", "Lock the whole phone on a schedule", "Daily goals with streaks")
    ),
    OnboardingPage(
        illustration = R.drawable.onb_privacy,
        title = "No account. No tracking.",
        body = "There is nothing to sign up for. Everything you block stays in a database on this phone. Wakt has no servers and sends nothing anywhere.",
        bullets = listOf("No login, ever", "No analytics, no crash reporting", "Website filtering runs on the device")
    ),
    OnboardingPage(
        illustration = R.drawable.onb_free,
        title = "Free. Fully. Forever.",
        body = "No ads, no premium tier, no features behind a paywall. Wakt is open source, built by one person who needed it.",
        bullets = listOf("Open source on GitHub", "No subscriptions", "No ads")
    ),
    OnboardingPage(
        illustration = R.drawable.onb_usage_access,
        title = "Usage Access",
        body = "Lets Wakt see which app is on screen right now, so it knows when a blocked app opens.",
        permission = PermissionKind.USAGE_ACCESS,
        why = "Android only tells apps what is in the foreground through this permission. Wakt reads the current app name and nothing else: no screen content, no typing, no history leaves the device."
    ),
    OnboardingPage(
        illustration = R.drawable.onb_overlay,
        title = "Display over other apps",
        body = "Lets Wakt put its lock screen and challenge screen on top of a blocked app.",
        permission = PermissionKind.OVERLAY,
        why = "Without it a blocked app would open normally and Wakt could only watch. Wakt never draws anything when nothing is blocked."
    ),
    OnboardingPage(
        illustration = R.drawable.onb_battery,
        title = "Keep running in the background",
        body = "Battery optimization can stop Wakt's blocking a few minutes after you leave the app. Exempting it keeps blocks working all day.",
        permission = PermissionKind.BATTERY,
        why = "Wakt's background work is tiny: a once-a-second check of the current app during sessions, and a local DNS filter for websites that handles only DNS. It does not drain the battery."
    ),
    OnboardingPage(
        illustration = R.drawable.onb_accessibility,
        title = "Accessibility Service (optional)",
        body = "Turns blocking from \"within a second\" into instant, and adds website blocking inside browsers. You can skip this.",
        permission = PermissionKind.ACCESSIBILITY,
        optional = true,
        why = "Many banking apps refuse to run while any accessibility service is enabled. If you use one, leave this off. Everything still works through the two permissions above."
    ),
    OnboardingPage(
        illustration = R.drawable.onb_goal,
        title = "You're set",
        body = "Add your first block from the Lock tab. When you block a website, Android will ask once to allow Wakt's local DNS filter, which runs as a VPN that only ever handles DNS.",
        bullets = listOf("Lock tab: block apps and sites", "Goals tab: daily promises on your wallpaper", "Settings: commitment locks and a PIN for your site list")
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    // Permission status, refreshed every time the user comes back from system settings
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val granted: (PermissionKind) -> Boolean = remember(refresh) {
        { kind: PermissionKind ->
            when (kind) {
                PermissionKind.USAGE_ACCESS -> PermissionHelper.isUsageAccessGranted(context)
                PermissionKind.OVERLAY -> PermissionHelper.isOverlayPermissionGranted(context)
                PermissionKind.BATTERY -> PermissionHelper.isBatteryOptimizationDisabled(context)
                PermissionKind.ACCESSIBILITY -> PermissionHelper.isAccessibilityServiceEnabled(context)
            }
        }
    }

    val isLast = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { index ->
            val page = pages[index]
            OnboardingPageContent(
                page = page,
                granted = page.permission?.let { granted(it) },
                onGrant = {
                    when (page.permission) {
                        PermissionKind.USAGE_ACCESS -> PermissionHelper.requestUsageAccessPermission(context)
                        PermissionKind.OVERLAY -> PermissionHelper.requestOverlayPermission(context)
                        PermissionKind.BATTERY -> PermissionHelper.requestBatteryOptimizationExemption(context)
                        PermissionKind.ACCESSIBILITY -> PermissionHelper.requestAccessibilityPermission(context)
                        null -> {}
                    }
                }
            )
        }

        // Dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { i ->
                val selected = pagerState.currentPage == i
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    label = "dot"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (selected) 20.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        // Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val page = pages[pagerState.currentPage]
            if (!isLast) {
                TextButton(onClick = onFinished) {
                    Text(if (page.permission != null) "Skip for now" else "Skip intro")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (isLast) onFinished()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = when {
                        isLast -> "Get started"
                        page.permission != null && granted(page.permission) -> "Next"
                        page.permission != null && page.optional -> "Skip"
                        page.permission != null -> "Later"
                        else -> "Next"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    granted: Boolean?,
    onGrant: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Image(
            painter = painterResource(id = page.illustration),
            contentDescription = null,
            modifier = Modifier.size(220.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (page.bullets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                page.bullets.forEach { bullet ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (page.permission != null) {
            Spacer(modifier = Modifier.height(20.dp))
            val isGranted = granted == true
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGranted) Color(0xFF14532D) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isGranted) "Granted" else if (page.optional) "Off (optional)" else "Not granted yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isGranted) Color(0xFF86EFAC) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isGranted) {
                            Button(onClick = onGrant, shape = RoundedCornerShape(10.dp)) {
                                Text(if (page.optional) "Turn on" else "Grant")
                            }
                        }
                    }
                    page.why?.let { why ->
                        Text(
                            text = "Why: $why",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isGranted) Color(0xFFBBF7D0) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (page.permission == PermissionKind.OVERLAY) {
                        PermissionHelper.getBackgroundPopupInstructions()?.let { extra ->
                            Text(
                                text = "On this phone also allow: $extra",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
