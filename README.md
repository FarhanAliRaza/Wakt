<p align="center">
  <img src="logo-app.png" alt="Wakt Logo" width="128" height="128">
</p>

<h1 align="center">Wakt</h1>

<p align="center">
  <strong>Digital wellness app for Android that helps you stay focused</strong>
</p>

<p align="center">
  Block distracting apps and websites with intentional friction - wait timers, challenges, and scheduled lock sessions.
</p>

---

## Download

Get the latest APK from [Releases](../../releases).

## Features

- **App Blocking** - Block any installed app with challenges
- **Website Blocking** - Block domains across all major browsers
- **Phone Lock** - Lock your entire phone for a set duration
- **Scheduled Locks** - Set recurring schedules (e.g., bedtime, work hours)
- **Challenges** - Wait timer or 500-click challenge before access
- **Focus Sessions** - Quick-start timed focus modes

## Requirements

- Android 7.0+ (API 24)
- Accessibility Service permission
- Display over other apps permission

## Build

```bash
./gradlew installDebug
```

## Permissions

The app requires these permissions to function:

| Permission | Purpose |
|------------|---------|
| Accessibility Service | Detect foreground apps and browser URLs |
| Display over other apps | Show blocking overlay |
| Usage Stats | Backup app detection method |

## Tech Stack

- Kotlin + Jetpack Compose
- Hilt (DI)
- Room (Database)
- Material 3

## License

MIT
