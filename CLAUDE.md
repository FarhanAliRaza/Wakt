# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Wakt is an Android digital wellness app that blocks distracting apps and websites. It uses a multi-layered enforcement system combining AccessibilityService monitoring, foreground services, and persistent overlays.

### Tech Stack
- Language: Kotlin
- Build System: Gradle with Kotlin DSL
- Min SDK: 24 (Android 7.0), Target SDK: 35
- UI: Jetpack Compose (screens) + Android Views (overlays)
- DI: Hilt
- Database: Room with migrations (currently v6)
- Architecture: MVVM with ViewModels

## Build Commands

```bash
./gradlew build                    # Build project
./gradlew clean build              # Clean build
./gradlew installDebug             # Install debug APK
./gradlew test                     # Run all tests
./gradlew testDebugUnitTest        # Unit tests only
./gradlew connectedAndroidTest     # Instrumented tests (requires device)
./gradlew test --tests "com.example.wakt.ExampleUnitTest"  # Specific test
./gradlew lint                     # Lint checks
./gradlew assembleDebug            # Generate debug APK
./gradlew assembleRelease          # Generate release APK
```

## Architecture

### Core Services (app/src/main/java/com/example/wakt/services/)

**AppBlockingService** (AccessibilityService)
- Monitors app/website launches via accessibility events
- Detects foreground app using AccessibilityService (primary) with UsageStatsManager fallback
- Updates `lastKnownForegroundPackage` on every `TYPE_WINDOW_STATE_CHANGED` event
- Triggers BlockingOverlayActivity when blocked app/site detected
- Handles browser-specific tab closing (Chrome, Firefox, Edge, Samsung)
- Rate limits: 5s cooldown for apps, 2s for websites

**BrickEnforcementService** (Foreground Service)
- Background monitor for "brick mode" sessions
- Checks foreground app every 3 seconds
- Shows/hides BrickOverlayService based on allowed apps
- Handles session completion and cleanup

**BrickOverlayService** (Overlay Service)
- Persistent overlay during brick sessions (uses Views, not Compose)
- Displays session timer, allowed apps grid, emergency buttons
- FLAG_NOT_FOCUSABLE for touch pass-through

### Data Layer (app/src/main/java/com/example/wakt/data/)

**Key Entities:**
- `BlockedItem`: Simple app/website blocks with challenge type
- `PhoneBrickSession`: Focus/sleep/detox sessions with allowed apps
- `GoalBlock` + `GoalBlockItem`: Long-term multi-item goals (1-90 days)
- `EssentialApp`: System/user essential apps during sessions
- `BrickSessionLog`: Session history and analytics

**Database Migrations:** Manual migrations v1→v6, defined in WaktDatabase.kt

### Manager/Utility Layer (app/src/main/java/com/example/wakt/utils/)

**BrickSessionManager**: Core orchestrator for brick sessions
- Starts/completes sessions, handles emergency overrides
- Auto-monitors scheduled sessions, resumes on app restart

**EssentialAppsManager**: Essential apps with 5-min memory cache

**TemporaryUnlock**: SharedPreferences-based temporary unlock after challenges

**PermissionHelper**: Checks accessibility and overlay permissions

### Presentation Layer (app/src/main/java/com/example/wakt/presentation/)

**Compose Screens:**
- HomeScreen: Blocked items list with permission warnings
- PhoneBrickScreen: Session management, quick-start buttons
- AddBlockScreen: App selector, website input, challenge picker
- GoalBlockScreen: Long-term goal creation

**Overlay Activities (Views, not Compose):**
- BlockingOverlayActivity: Challenge overlay for blocked apps
- EmergencyOverrideActivity: Emergency unlock confirmation

## Key Data Flows

### App Blocking
```
User launches blocked app
  → AppBlockingService.onAccessibilityEvent
  → checkIfAppIsBlocked() queries DB
  → BlockingOverlayActivity with challenge
  → Challenge completed → TemporaryUnlock created
```

### Brick Mode
```
User starts session → BrickSessionManager.startDurationSession()
  → BrickEnforcementService monitors foreground app
  → Non-allowed app detected → notification + performGlobalAction(BACK)
  → Session expires → completeCurrentSession() + log
```

## Foreground App Detection

Detection uses a priority-based approach for reliability:

### Priority Order (in `AppBlockingService.getForegroundPackageReliably()`)
1. **AccessibilityService `lastKnownForegroundPackage`** (REAL-TIME)
   - Updated on every `TYPE_WINDOW_STATE_CHANGED` event
   - Most reliable - fires immediately when user switches apps or presses home
   - Detects launcher correctly when home button pressed

2. **AccessibilityService `rootInActiveWindow`**
   - Fallback if `lastKnownForegroundPackage` is null
   - Direct query to accessibility service

3. **UsageStatsManager** (DELAYED - last resort)
   - Has inherent delays (data not real-time)
   - `lastTimeUsed` doesn't update when app is CLOSED, only when OPENED
   - Pressing home button may not update launcher timestamp
   - Only use for fallback when AccessibilityService unavailable

### Launcher Detection
Launcher packages are explicitly tracked to detect home screen:
```kotlin
val launcherPackages = setOf(
    "com.google.android.apps.nexuslauncher",  // Pixel
    "com.sec.android.app.launcher",           // Samsung
    "com.android.launcher3",                  // AOSP
    "com.miui.home",                          // Xiaomi
    "com.huawei.android.launcher",            // Huawei
    "com.oppo.launcher",                      // Oppo
    "com.vivo.launcher"                       // Vivo
)
```

### Why Not UsageStatsManager First?
- UsageStatsManager returns stale data after closing apps
- Example: Close dialer, go to home → UsageStatsManager still shows dialer as "recent" for 5+ seconds
- AccessibilityService detects launcher immediately via `TYPE_WINDOW_STATE_CHANGED`

## Required Permissions

Two setups exist because many banking apps refuse to run while any third-party
accessibility service is enabled (see `PermissionHelper`):

- **Standard (default):** PACKAGE_USAGE_STATS (foreground detection via
  `ForegroundAppDetector`, reading usage events) + SYSTEM_ALERT_WINDOW (lock
  screen via `BrickOverlayService` with TYPE_APPLICATION_OVERLAY, challenge
  screen via `BlockingOverlayActivity`). `BrickEnforcementService` polls every
  second and uses `AppBlockChecker` for block decisions.
- **Enhanced (optional):** BIND_ACCESSIBILITY_SERVICE. `AppBlockingService`
  reacts to window events, draws the same lock screen (`BrickOverlayController`)
  as TYPE_ACCESSIBILITY_OVERLAY and adds URL-bar website blocking in browsers.
  When connected, the enforcement service hands app blocks over to it.
- Always: QUERY_ALL_PACKAGES (app selector), FOREGROUND_SERVICE*, RECEIVE_BOOT_COMPLETED
  (`BootReceiver` restores services), BIND_VPN_SERVICE (DNS website filter).

`ServiceOptimizer.optimizeServices()` is the single place that decides which
background services should run; call it after anything that changes blocks or
permissions.

## Challenge Types

- WAIT: Wait X minutes (5-30min timer)
- QUESTION: Q&A challenge (placeholder)
- CLICK_500: Click button 500 times

## Development Notes

- Overlays use Views (not Compose) because Compose doesn't render properly in overlay contexts
- WebsiteBlockingVpnService blocks websites via a DNS-only VPN: it advertises a local DNS server (10.0.0.1) on the TUN with no default route, so only DNS queries are processed in userspace and browsing traffic/battery are unaffected. It adds explicit /32 routes for 10.0.0.1 and for every detected upstream DNS server (DNS66-style) so apps that query the network's resolvers directly are intercepted as well, and it excludes apps listed in `GlobalSettingsManager.vpnExcludedApps`. Queries are forwarded asynchronously (transaction-ID remapping + receiver thread), answered from a TTL cache when possible, and blocked domains get NXDOMAIN. Covers all apps except Wakt itself, so DNS blocks also stop the matching native apps. Requires one-time VPN consent (requested in AddBlockScreen); ServiceOptimizer starts/stops the service based on active website blocks. Caveat: strict Private DNS (DoT) bypasses it.
- Filter pause (`FilterPauseManager`): the VPN can be paused for 5–30 min for one VPN-averse app (banking). The pause is stored in `GlobalSettingsManager.vpnPause`, `ServiceOptimizer` keeps the VPN off while it is set, and it is tied to a brick session whose `allowedApps` is just that app. `BrickSessionManager.exitBrickMode()` clears the pause and re-optimizes, and `VpnResumeReceiver` is an alarm backup. Browsers (generic http VIEW handlers) and blocked apps are never offered.
- Allowed apps stored as comma-separated string in DB
- Services use SupervisorJob for coroutine scope management
- Database operations always on Dispatchers.IO

## Theme / Design System

App uses a Shadcn-inspired Blue color palette. Colors defined in `presentation/ui/theme/Color.kt`.

**Primary Colors:**
- Primary: Blue500 `#3B82F6` - buttons, progress indicators, active states
- PrimaryContainer: Blue900 `#1E3A8A` - selected backgrounds

**Background/Surface (Dark Theme):**
- Background: Slate950 `#020617`
- Surface: Slate900 `#0F172A`
- SurfaceVariant: Slate800 `#1E293B`
- Outline: Slate700 `#334155`

**Text Colors:**
- OnSurface: Slate100 `#F1F5F9`
- OnSurfaceVariant: Slate400 `#94A3B8`
- OnPrimary: White

**Semantic:**
- Destructive: `#EF4444`
- Success: `#22C55E`
- Warning: `#F59E0B`

**Usage Notes:**
- Always use Blue500 for progress bars, active indicators, primary buttons
- For overlay services (BrickOverlayService), use hardcoded hex colors since no Compose theme access
- Background arcs/tracks use Slate700 `#334155`

## Debugging

Filter Logcat by tags:
- `AppBlockingService`: App/website blocking events
- `BrickEnforcementService`: Brick mode monitoring
- `BrickSessionManager`: Session lifecycle
