# To-Do: Task List & Reminder

A professional-grade, privacy-focused task management application for Android.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

## Features

### Task Management

- **Create, Edit, Delete** - Simple and efficient CRUD operations
- **Priority Levels** - Organize tasks with Low, Medium, and High priorities
- **Task Descriptions** - Add optional notes and details to tasks
- **Search** - Quickly find tasks with real-time search functionality

### Organization

- **Home Screen Widget** - A beautiful, scrollable widget for tracking Pending tasks directly from your home screen (fully supports Day/Night Dynamic Theming)
- **Expandable Tasks** - Tap-to-expand any task to reveal long descriptions inline
- **Tabs** - View tasks by status: Pending, Completed, Saved, Archive, Recurring
- **Sorting** - Sort tasks by date, priority, or name
- **Swipe Actions** - Delete or complete tasks instantly with a simple intuitive swipe gesture

### Reminders & Recurring Tasks

- **Local Notifications** - Set reminders for individual tasks
- **Recurring Tasks** - Create tasks that repeat daily, weekly, or monthly
- **Custom Recurrence** - Set end dates for recurring tasks

### Backup & Restore

- **AES-256 Encryption** - Securely encrypt your task backups with a password.
- **Export Backup** - Save your tasks to an encrypted backup file.
- **Import Backup** - Restore tasks from an encrypted backup file (v2) or legacy plaintext (v1).
- **Import Modes** - Add to existing tasks or replace all tasks.

## Requirements

- Android 8.0 (API 26) or higher
- Notification permission for reminders (Android 13+)

## Build

The app uses standard Gradle. You can build it using:

```bash
# Debug build (APK)
./gradlew assembleDebug

# Release build (App Bundle for Google Play)
./gradlew bundleRelease
```

### Build Variants

This branch (`google-play`) is configured specifically for the Google Play Store release. It contains a single default build configuration with Firebase Analytics integrated for crash reporting and app diagnostics.

For the completely Free and Open Source (FOSS) version without proprietary dependencies, switch to the `f-droid` branch.

## Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM with Repository Pattern
- **Database**: Room
- **Dependency Injection**: Hilt
- **UI**: Material Design 3, ViewBinding
- **Async**: Coroutines, Flow
- **Paging**: Jetpack Paging 3

## Download

You can download the app from Google Play:

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=io.github.jwtiyar.simplertask)

*Note: The FOSS build is also available on [F-Droid](https://f-droid.org/packages/io.github.jwtiyar.simplertask/).*

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
