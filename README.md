# To-Do: Task List & Reminder

An offline, privacy-first task and reminder app for Android.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Latest Release](https://img.shields.io/github/v/release/jwtiyar/To-Do-List?label=Release&color=blue)](https://github.com/jwtiyar/To-Do-List/releases/latest)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-blueviolet.svg)](https://developer.android.com/about/versions/16)

## Features

### Design and layout
- **Material 3 layout.** Standardized 12dp and 16dp corners, low surface elevation, and dynamic color on Android 12+.
- **Dark theme.** Dedicated container tones and high-contrast text.
- **Android 15 and 16 edge to edge.** Draws behind the status bar and navigation pill, padding toolbars and buttons around camera cutouts.

### Task management
- **Task tracking.** Create, edit, and delete tasks with low, medium, or high priority.
- **Notes and details.** Add optional descriptions to tasks. Tap any card to expand notes inline.
- **Search.** Filter tasks across all lists as you type.

### Organization
- **Home screen widget.** Scrollable widget shows pending tasks directly on your home screen.
- **Status lists.** Pending, Completed, Saved, Archive, Recurring, and Trash.
- **Sorting.** Order tasks by due date, priority, or title.
- **Gestures.** Swipe right to complete a task, or swipe left to delete it.

### Reminders and recurring tasks
- **Exact alarms.** Schedules notifications through `AlarmManager.setAlarmClock()`, delivering alerts during Doze mode.
- **Repetition rules.** Repeat tasks daily, on weekdays, weekly, monthly, or yearly with optional end dates.

### Backup and security
- **AES-256 encryption.** Password-protect backup files.
- **Local export and import.** Save to JSON files and restore anytime using additive or replacement import.
- **Offline storage.** Task data stays on your device.

## Requirements

- Android 8.0 (API 26) or higher
- Notification permission for reminders on Android 13+

## Download

- **Google Play:**
  [<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="60">](https://play.google.com/store/apps/details?id=io.github.jwtiyar.simplertask)

- **Direct APK:** Download from [GitHub Releases](https://github.com/jwtiyar/To-Do-List/releases/latest)

## Build

Compile with Gradle:

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Release App Bundle for Google Play
./gradlew bundleRelease
```

## Tech stack

- **Language:** Kotlin 2.0
- **Architecture:** MVVM with repository pattern
- **UI:** Material Design 3, ViewBinding, dynamic color
- **Database:** Room with KSP
- **Dependency injection:** Dagger Hilt
- **Concurrency:** Coroutines, Kotlin Flow
- **Paging:** Jetpack Paging 3

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

## Author

**Jwtyar Nariman**

- Email: <jwtiyar@gmail.com>
- GitHub: [@jwtiyar](https://github.com/jwtiyar)

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.
