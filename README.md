# Wakt

Wakt is a distraction blocker for Android. It helps you stay focused by blocking apps and websites and adding intentional friction (wait timers or challenges) before you can access them.

## What it does

- Block installed apps by package name
- Block websites by domain across major browsers (Chrome, Firefox, Edge, Brave, Samsung Internet, etc.)
- Show a full-screen overlay with a challenge when you open blocked content
- Challenges:
  - Wait timer (10–30 minutes, persists across app restarts)
  - 500-click challenge
  - (Placeholder) Question/answer
- Long-term goal blocks (1–90 days) that can include multiple apps/websites
- Persisted state and cooldowns to avoid loops and excessive blocking

## How it works (high level)

- Accessibility-based detection (`AppBlockingService`):

  - Monitors foreground apps and browser windows
  - Extracts active browser tab URL from the view hierarchy and normalizes to a domain
  - Checks Room database for active blocks (regular or goal-based)
  - Closes offending browser tabs when possible and launches a blocking overlay

- Blocking overlay (`BlockingOverlayActivity`):

  - Compose UI that presents the configured challenge
  - Wait timers continue even if the app is closed; access is only granted after completion

- DNS-only VPN (optional, currently disabled in Manifest):
  - `WebsiteBlockingVpnService` creates a lightweight local VPN that only handles DNS
  - Intercepts DNS queries from browser apps and returns NXDOMAIN for blocked domains
  - Designed for lower battery impact by handling DNS only and scoping to browser packages

## Tech stack

- Kotlin, Jetpack Compose (Material 3)
- Hilt for dependency injection
- Room for local persistence
- Coroutines/Flow
- Gradle Kotlin DSL, Version Catalogs

## Requirements

- Android Studio with JDK 11
- Android device/emulator with minSdk 24+

## Build and run

```bash
# Clean build
./gradlew clean build

# Install debug build on a connected device/emulator
./gradlew installDebug

# Run tests
./gradlew test            # unit tests
./gradlew connectedAndroidTest  # instrumented tests (device/emulator required)

# Assemble APKs
./gradlew assembleDebug
./gradlew assembleRelease
```

SDK configuration (from `app/build.gradle.kts`):

- compileSdk: 35
- targetSdk: 35
- minSdk: 24

## App permissions you must grant

- Accessibility Service: required for app detection and browser URL detection
- Display over other apps (SYSTEM_ALERT_WINDOW): required for the blocking overlay
- Foreground service and Internet: used by services and telemetry required by Android
- Device Admin (optional): used for uninstall protection flows

If you choose to enable the DNS-only VPN (disabled by default), you must also grant VPN permission.

## Enabling DNS-only VPN (optional)

The app ships with a DNS-only VPN implementation for website blocking, but it is commented out in the default Manifest to prioritize battery life.

To enable:

1. In `app/src/main/AndroidManifest.xml`, uncomment:
   - `<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />`
   - The `<service>` declaration for `.services.WebsiteBlockingVpnService`
2. Rebuild and reinstall the app.
3. Optionally re-enable the VPN permission request flow in `MainActivity` (the code is present but commented out).

## Core modules and key classes

- UI

  - `MainActivity` — hosts Compose navigation
  - `AddBlockScreen` — add apps/websites and configure challenges
  - `BlockingOverlayActivity` — full-screen overlay with challenges

- Services

  - `AppBlockingService` — AccessibilityService for app/browser detection and tab closing
  - `WebsiteBlockingVpnService` — DNS-only VPN for domain blocking (optional)

- Data (Room)
  - `BlockedItem`, `GoalBlock`, `GoalBlockItem` — entities for regular blocks and long-term goals

## Usage

1. Launch the app and grant required permissions when prompted (Accessibility, overlay).
2. Tap the action to add a block:
   - Apps: search and select installed apps; choose a challenge and (for wait) a duration
   - Websites: enter a domain (e.g., `facebook.com`); choose a challenge and duration
3. Open a blocked app or site. The overlay will appear and enforce the configured challenge.
4. After completing the challenge, access is granted (for goal blocks, access may remain restricted until the goal ends).

## Testing

- Unit tests: `./gradlew test`
- Instrumented tests: `./gradlew connectedAndroidTest` (device/emulator required)
- Compose UI tests included via Compose Test Libraries

## Notes on performance and battery

- Accessibility processing is rate-limited and scopes checks to active tabs to reduce overhead
- Browser tab closing is best-effort and may vary by browser/version
- DNS-only VPN mode further reduces overhead by handling only DNS and only for browser apps

## Roadmap (selected)

- Expand challenge types (e.g., Q&A)
- More robust cross-browser tab management
- Optional analytics/crash reporting (off by default)

## License

TBD
