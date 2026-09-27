# To-Do: Task List & Reminder

A professional-grade, privacy-focused task management application for Android built with modern Material 3 and Kotlin.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Latest Release](https://img.shields.io/github/v/release/jwtiyar/To-Do-List?label=Release&color=blue)](https://github.com/jwtiyar/To-Do-List/releases/latest)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-blueviolet.svg)](https://developer.android.com/about/versions/16)

## Features

### 🎨 Modern Design & Experience
- **Material 3 Interface** - Compact, friendly, and clean layout with standardized corner curves and elevation.
- **Polished Dark Mode** - Thoughtfully tuned dark theme with high-contrast surfaces and comfortable typography.
- **Android 15 & 16 Ready** - Full native Edge-to-Edge display with display cutout (notch) and gesture navigation insets.

### 📝 Task Management
- **Create, Edit, Delete** - Fast, responsive task management.
- **Priority Levels** - Color-coded Low, Medium, and High priorities.
- **Task Descriptions** - Add optional notes and details to any task.
- **Real-Time Search** - Instant filtering across all your tasks.

### 📂 Organization
- **Home Screen Widget** - Scrollable widget tracking Pending tasks directly from your home screen (supports Dynamic Theming).
- **Expandable Tasks** - Tap-to-expand any task to read notes inline.
- **Navigation Categories** - Pending, Completed, Saved Tasks, Archive, Recurring, and Trash.
- **Sorting** - Organize by due date, priority, or title.
- **Swipe Gestures** - Complete or delete tasks quickly with intuitive swipe actions.

### ⏰ Reminders & Recurring Tasks
- **Exact Alarms & Notifications** - Reliable reminders scheduled via `AlarmManager.setAlarmClock()`, waking device even in Doze mode.
- **Recurring Schedules** - Repeat tasks daily, weekdays, weekly, monthly, or yearly with customizable end dates.

### 🔒 Backup & Security
- **AES-256 Encryption** - Encrypt and password-protect your backups.
- **Export & Import** - Save to JSON files and restore anytime (supports additive and replace modes).
- **100% Offline & Private** - Your tasks never leave your device without your explicit export.

## Requirements

- Android 8.0 (API 26) or higher
- Notification permission for reminders (Android 13+)

## Download

Get the latest version:

- **Google Play:**
  [<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="60">](https://play.google.com/store/apps/details?id=io.github.jwtiyar.simplertask)

- **Direct APK:** Download from [GitHub Releases](https://github.com/jwtiyar/To-Do-List/releases/latest)
- **F-Droid:** Available on [F-Droid](https://f-droid.org/packages/io.github.jwtiyar.simplertask/)

## Build

Built with standard Gradle. You can compile using:

```bash
# Debug build (APK)
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Release App Bundle (for Google Play)
./gradlew bundleRelease
```

### Build Variants

- **`google-play` (this branch):** Configured for Google Play release, incorporating Firebase Crashlytics & Analytics diagnostics.
- **`f-droid` branch:** 100% Free and Open Source (FLOSS) build without proprietary services.

## Tech Stack

- **Language**: Kotlin 2.0
- **Architecture**: MVVM with Repository Pattern
- **UI**: Material Design 3, ViewBinding, Dynamic Color
- **Database**: Room (with KSP)
- **Dependency Injection**: Dagger Hilt
- **Async & Concurrency**: Coroutines, Kotlin Flow
- **Paging**: Jetpack Paging 3

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Author

**Jwtyar Nariman**

- Email: <jwtiyar@gmail.com>
- GitHub: [@jwtiyar](https://github.com/jwtiyar)

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
