# Task Manager Android App

A Kotlin Android application to create, organize and track tasks using an MVVM architecture with Room, Coroutines & StateFlow.

## Features

- Create tasks (title, optional description, priority, due date)
- Mark complete / saved (favorite) / archive
- Filter tabs: Pending, Completed, Saved, Archived, AllAdd a helper to invoke private sortTasks directly without reflection (or make it internal + @VisibleForTesting).
- Full text search (title & description)
- Bulk actions: clear completed, reset all to pending
- Local notifications & (future) exact alarm scheduling
- Multi‑language support (runtime language change dialog)
- Dark / Light theme toggle

## Tech Stack

- Kotlin, Coroutines, StateFlow
- MVVM (ViewModel + Repository + Room DAO)
- Room (KSP) for persistence
- Paging 3 (Room PagingSource) for scalable task list loading
- Material Components, RecyclerView + DiffUtil

## Architecture (Quick Glance)

Layer | Responsibility
----- | --------------
UI (Activity/Adapters/Dialogs) | Render state, delegate user intents
ViewModel | Holds UI state (StateFlow), business logic, exposes filtered/sorted/search flows
Repository | Single source of truth, wraps DAO, business toggles, IO dispatching
DAO / Room | Database access, Flow queries

See ARCHITECTURE.md (was MVVM_REFACTORING.md) for full diagrams & rationale.

## Requirements

- JDK 24 (ensure `/usr/lib/jvm/java-24-openjdk` available)
- Android Gradle Plugin compatible with Gradle 8.14+
- Android SDK 26+ (compile / target per `build.gradle`)
- Kotlin 1.9.10+

## Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Release (requires signing):

```bash
./gradlew assembleRelease
```

## Testing (Quick)

- Unit tests (ViewModel, logic):
```bash
./gradlew :app:test
```
- Instrumentation (Room DAO) tests (emulator/device required):
```bash
./gradlew :app:connectedAndroidTest
```
Outputs:
- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Instrumentation report: `app/build/reports/androidTests/connected/index.html`


## Run / Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or use Android Studio Run configuration.

## Tests

Unit tests cover ViewModel behaviors (sorting, search debounce, toggle actions, error events). Room DAO integration tests live under `app/src/androidTest/java/com/example/simplertask/data/local/TaskDaoTest.kt`.

Commands:

```bash
./gradlew testDebugUnitTest              # run unit tests
./gradlew connectedDebugAndroidTest      # run instrumentation tests (requires emulator/device)
```

## Project Structure (simplified)

```
app/src/main/java/com/example/simplertask/
  MainActivity.kt
  dialogs/...
  repository/TaskRepository.kt
  viewmodel/TaskViewModel.kt
  utils/LocaleManager.kt
  Task.kt (entity)
  TaskDao.kt / TaskDatabase.kt
```

## Permissions

- POST_NOTIFICATIONS (Android 13+) requested at runtime for notifications.
- SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM (where applicable) for precise reminders (future feature).

If denied, related features degrade gracefully (no crash).

## Localization

Language selection dialog updates resources via a custom `LocaleManager` and restarts the activity for changes to apply.

## Common Issues

Issue | Fix
----- | ---
Room schema / migration errors | Verify migrations in `TaskDatabaseKt`; clear app data if dev only
Missing notifications on Android 13+ | Ensure notification permission granted in system settings
Search not returning expected results | Query normalizes wildcards: repository wraps input with `%` automatically

## Future Enhancements

- Hilt DI
- Navigation Component
- Jetpack Compose migration
- WorkManager based scheduled reminders

## License

Apache License 2.0