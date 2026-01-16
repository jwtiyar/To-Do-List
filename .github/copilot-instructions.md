# Copilot Instructions for To-Do List Codebase

## Project Overview
This is a **privacy-focused Android task management app** (minSdk 26, targetSdk 36) built with:
- **Kotlin** + **Jetpack Compose/Material Design 3** for UI
- **Room** (SQLite) for offline-first local persistence
- **Hilt** for dependency injection
- **Kotlin Coroutines + Flow** for reactive state management
- **Paging 3** for infinite scrolling support

**Key principle**: Fully offline, no cloud—all data encrypted locally with JSON backup/restore.

---

## Architecture Pattern: MVVM + Single Repository

### Data Flow
```
UI (Fragment/Activity)
  ↓
ViewModel (exposes StateFlow<UiState>, manages filters/sorting)
  ↓
Repository (TaskRepository - single source of truth)
  ↓
DAO (TaskDao - Room queries)
  ↓
Room Database (SQLite, auto-migrations)
```

### Critical Design Decisions

1. **Single Repository Pattern** (`data/repository/TaskRepository.kt`)
   - All data access flows through `TaskRepository`—never call DAO directly from UI
   - Repository switches threads: read operations stay on `Dispatchers.Default` (Room handles), writes use `Dispatchers.IO`
   - Exposes both **cold Flow streams** (read queries) and **Paging 3** flows for lists

2. **StateFlow for UI State** (`viewmodel/TaskViewModel.kt`)
   - `TaskUiState` data class holds all screen state: tasks, filters, search query, sorting
   - Filters: `PENDING, COMPLETED, SAVED, ARCHIVED, ALL`
   - Sorts: `DATE, NAME, PRIORITY`
   - Search is reactive—flows through `searchQueryFlow`

3. **Task State Model** (`data/local/entity/Task.kt`)
   - Key fields: `isCompleted`, `isSaved` (favorited), `isArchived`, `dueDateMillis`, `priority`
   - Indices optimized for filter queries; composite index on `(isArchived, isCompleted, id)` for speed
   - **Custom equals()**: ignores `notificationId` (internal-only tracking)

---

## Build & Development Workflow

### Build Commands
```bash
# Debug build (installs alongside release, adds ".dev" suffix)
./gradlew assembleDebug

# Release build (minified, shrunk resources, signed if keystore.properties exists)
./gradlew assembleRelease

# Run tests
./gradlew test

# Clean
./gradlew clean
```

### Key Build Configuration
- **Signing**: Reads from `keystore.properties` (git-ignored); falls back to `keystore/simplertask-release.jks`
- **ProGuard**: Enabled in release; rules in `app/proguard-rules.pro`
- **Debug suffix**: Debug builds use `.dev` app ID to co-exist with Play release
- **Kotlin 2.0.0**, **AGP 8.12.0**, **Gradle wrapper** available

---

## Module Structure & Key Files

```
app/src/main/java/io/github/jwtiyar/simplertask/
├── TaskApp.kt                       # @HiltAndroidApp, locale setup
├── MainActivity.kt                  # Hosts fragments, backup/restore dialogs, search
├── data/
│   ├── local/
│   │   ├── TaskDatabase.kt         # Room database, migrations (v1→v4)
│   │   ├── entity/Task.kt          # Task model, custom equals, indices
│   │   └── dao/TaskDao.kt          # Queries (Flow + Paging 3 sources)
│   ├── repository/TaskRepository.kt # Single source of truth
│   ├── backup/BackupManager.kt      # JSON export/import (versioned v1)
│   ├── model/TaskAction.kt          # Enum: SAVE, UNSAVE, ARCHIVE, UNARCHIVE
│   └── model/TaskFilter.kt          # Filter enums (used in ViewModel)
├── di/
│   └── AppModule.kt                 # Hilt providers: DB, DAO, Repository
├── service/
│   ├── NotificationHelper.kt        # AlarmManager scheduling, local notifications
│   └── NotificationReceiver.kt      # BroadcastReceiver for alarm triggers
├── ui/
│   ├── MainActivityBackupDelegate.kt # Backup/restore dialog handlers
│   ├── UiEvent.kt                  # Event sealed class for dialog/action results
│   ├── adapters/TaskAdapter.kt     # RecyclerView adapter (non-paging)
│   ├── adapters/TaskPagingAdapter.kt # Paging 3 adapter
│   ├── dialogs/TaskDialogManager.kt # Create/edit task dialogs
│   ├── actions/                    # Action handlers (quick actions)
│   └── fragments/TaskListFragment.kt # List display fragment
├── viewmodel/TaskViewModel.kt       # MVVM state manager
└── utils/LocaleManager.kt           # i18n locale handling
```

---

## Common Development Tasks

### Adding a New Task Field
1. Add to `Task` entity (`data/local/entity/Task.kt`)
2. Create **Room migration** in `TaskDatabase.kt` (e.g., `MIGRATION_4_5`)
3. Update `TaskDao` queries if filtering affects the field
4. Update `TaskRepository` methods if new business logic needed
5. Update `BackupManager` (JSON export/import keys)
6. Bind UI in fragment/adapter

### Implementing a New Filter
1. Add to `TaskViewModel.TaskFilter` enum
2. Add DAO query method in `TaskDao.kt`
3. Add repository wrapper in `TaskRepository`
4. Update `filterFlow` logic in `TaskViewModel`
5. Bind UI (tab layout, menu item, etc.)

### Adding Reminders/Notifications
- Handled by `NotificationHelper` + `NotificationReceiver`
- Schedule via `AlarmManager.setExactAndAllowWhileIdle()` (respects `SCHEDULE_EXACT_ALARM` permission on Android 12+)
- Falls back to inexact if exact not allowed
- Stores `notificationId` in Task for cancellation tracking

### Backup/Restore Flow
- `BackupManager.exportTasks()` → JSON string (includes version, timestamp)
- `BackupManager.importTasks()` → parses and reconstructs Tasks
- Full task state preserved (completion, saved, archived, priority, due date)
- Called from `MainActivityBackupDelegate`

---

## Testing & Debugging

- **Unit tests**: `app/src/test/java/`
- **Android tests**: `app/src/androidTest/`
- **Test runner**: AndroidJUnitRunner configured in `build.gradle`
- **Debug mode**: `BuildConfig.DEBUG_MODE` set per build type; use for conditional logging

---

## Important Patterns & Gotchas

1. **Thread Safety**: Use `withContext(Dispatchers.IO)` in Repository for writes; don't block on Flow emissions
2. **Filter + Archive Logic**: Archived tasks always excluded from main lists; saving a task auto-unarchives on toggle
3. **Paging Config**: Page size = 20, placeholders disabled
4. **Notification IDs**: Match Task ID for easy tracking/cancellation
5. **Migration Chain**: Always increment database version; register new migrations in `getDatabase()`
6. **Locale**: Set via `LocaleManager` in `TaskApp.attachBaseContext()`—affects all UI strings

---

## Dependencies at a Glance

- **Androidx**: appcompat, fragment, lifecycle, paging, room, work, preference, security-crypto
- **Hilt**: android, compiler
- **Jetpack Compose** (Material 3): material3, compose-ui, compose-foundation
- **Kotlin**: coroutines, serialization
- **Google Play Services**: optional (if Play billing added)
- **ProGuard**: enabled in release builds

---

## When Adding External Code or Libraries

- Keep offline-first principle—avoid cloud dependencies
- Test on minSdk 26 (API 26, Android 8.0)
- Thread safety required for UI state flows
- Prefer Coroutines + Flow over RxJava or LiveData
- Use Hilt for any new services/repositories
