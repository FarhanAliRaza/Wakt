# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Wakt is an Android application built with Kotlin, using Android's Navigation Component for navigation between fragments. The app follows standard Android architecture with Material Design components.

### Tech Stack
- Language: Kotlin
- Build System: Gradle with Kotlin DSL
- Min SDK: 24 (Android 7.0)
- Target/Compile SDK: 36
- UI: View Binding, Material Design Components
- Navigation: Android Navigation Component
- Testing: JUnit 4, Espresso

## Build Commands

```bash
# Build the project
./gradlew build

# Clean build
./gradlew clean build

# Install debug APK on connected device/emulator
./gradlew installDebug

# Run all tests
./gradlew test

# Run unit tests only
./gradlew testDebugUnitTest

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "com.example.wakt.ExampleUnitTest"

# Lint checks
./gradlew lint

# Generate APK
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK
```

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/wakt/
│   │   │   ├── MainActivity.kt        # Main activity with toolbar and FAB
│   │   │   ├── FirstFragment.kt       # First navigation destination
│   │   │   └── SecondFragment.kt      # Second navigation destination
│   │   ├── res/
│   │   │   ├── layout/               # XML layouts
│   │   │   ├── navigation/           # Navigation graph
│   │   │   └── values/              # Colors, strings, themes
│   │   └── AndroidManifest.xml
│   ├── test/                         # Unit tests
│   └── androidTest/                  # Instrumented tests
└── build.gradle.kts
```

## Architecture Patterns

### Navigation
- Uses Android Navigation Component with a NavHostFragment
- Navigation graph defined in `res/navigation/nav_graph.xml`
- Fragments handle navigation through `findNavController()`

### View Binding
- All UI components use View Binding (enabled in build.gradle.kts)
- Binding instances are properly managed with null safety in Fragments

### Fragment Lifecycle Management
- Fragments properly handle binding lifecycle:
  - Create binding in `onCreateView()`
  - Access views in `onViewCreated()`
  - Clear binding reference in `onDestroyView()`

## Key Components

### MainActivity
- Entry point of the application
- Sets up the app bar with Navigation Component
- Contains a Floating Action Button (FAB) with placeholder functionality
- Handles options menu

### Fragments
- **FirstFragment**: Initial screen with navigation to SecondFragment
- **SecondFragment**: Secondary screen with back navigation to FirstFragment
- Both use View Binding pattern with proper lifecycle management

## Dependencies Management

Dependencies are managed through version catalogs in `gradle/libs.versions.toml`:
- AndroidX Core, AppCompat, ConstraintLayout
- Material Design Components
- Navigation Component (Fragment & UI)
- Testing: JUnit, Espresso

## Testing Approach

- **Unit Tests**: Located in `app/src/test/`, run on JVM
- **Instrumented Tests**: Located in `app/src/androidTest/`, run on device/emulator
- Test runner: AndroidJUnitRunner
- Basic example tests provided for both unit and instrumented testing

## Development TODO List

### Phase 1: App Blocking Foundation ✅ COMPLETED
- [x] Set up Compose project with Hilt dependency injection
- [x] Create Room database with blocked_items table schema
- [x] Build app selector UI to show installed apps list (fixed to show all apps including YouTube)
- [x] Implement AccessibilityService for app launch detection
- [x] Create blocking overlay with wait timer challenge
- [x] Fix timer persistence - timer now saves state and only starts when user requests access

### Phase 2: Website Blocking ✅ COMPLETED
- [x] Implement Local VPN Service for network-level blocking
- [x] Add DNS request interception logic
- [x] Browser URL detection via Accessibility Service
- [x] Add website input UI in AddBlockScreen
- [x] Fix VPN permission request flow with proper ActivityResult handling
- [x] Fix browser loop issue - automatically close blocked website tabs

### Phase 3: Challenge System ✅ COMPLETED
- [x] Implement wait timer challenge (10-30 minutes)
- [x] Build custom Q&A challenge system (placeholder implementation)
- [x] Make blocking persistent (survive app kill/restart) using SharedPreferences
- [x] Add cooldown system (30s for websites, 5s for apps) to prevent repeated blocking

### UI Components ✅ COMPLETED
- [x] Build MainActivity with blocked items list and FAB
- [x] Create AddBlockScreen with Apps/Websites tabs
- [x] Design BlockingOverlay full-screen activity
- [x] Add comprehensive permission management UI with warning banners and dialogs

### Supporting Features ✅ MOSTLY COMPLETED
- [x] Set up permission request flow (Accessibility, VPN, Overlay, Notifications)
- [x] Implement browser-specific tab closing (Chrome, Firefox, Edge, generic browsers)
- [x] Add TimerPersistence utility for saving timer state across app restarts
- [ ] Write unit tests for challenge logic
- [ ] Create instrumented tests for blocking functionality
- [ ] Ensure < 3% battery usage per day optimization
- [ ] Test complete flow - add block, trigger block, complete challenge across different browsers

### Testing & Optimization ✅ COMPLETED
- [x] Test website blocking across different browsers (Chrome, Firefox, Edge, Samsung Internet)
- [x] Test app blocking with timer persistence across app restarts
- [x] Performance testing and battery usage optimization
- [x] Edge case testing (network changes, permission revocation, etc.)
- [x] Fix duplicate apps in app selector

### Critical Bug Fixes (NEXT PHASE - HIGH PRIORITY)
- [x] **Fix internet speed degradation** - VPN service causing overall network slowdown
- [x] **Resolve browser loop issue** - Site open in old tab causes infinite close/block loop
- [x] **Fix app blocking bypass** - Apps can run after dismissing block screen once
- [x] **Improve blocking persistence** - Make blocks harder to bypass
- [x] **Enhanced error handling** - Better recovery from stuck states

### Performance & Reliability Improvements (FUTURE)
- [ ] Optimize VPN service to only process DNS traffic (reduce network impact)
- [ ] Implement selective packet filtering for better performance  
- [ ] Add persistent blocking overlays that can't be easily dismissed
- [ ] Improve background app state monitoring and detection
- [ ] Enhanced browser compatibility and tab management
- [ ] Add network performance monitoring and metrics
- [ ] Implement crash reporting and usage analytics
