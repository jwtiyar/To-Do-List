# Project Context: To-Do: Task List & Reminder

## Project Overview
"To-Do: Task List & Reminder" is a professional-grade, privacy-focused task management application for Android. It is built using modern Android development standards and is primarily distributed as FOSS via F-Droid.

### Key Technologies & Architecture
*   **Language:** Kotlin
*   **Architecture:** MVVM (Model-View-ViewModel)
*   **Dependency Injection:** Hilt
*   **Local Database:** Room
*   **Encrypted Backups:** AES-256-GCM (PBKDF2-SHA256) for secure import/export.
*   **Data Handling:** Paging 3, Kotlin Coroutines, Flow
*   **UI:** Material Design 3 (M3), ViewBinding, Navigation Component (Single Activity Architecture)
*   **Flavors:**
    *   `fdroid`: Completely FOSS, no proprietary dependencies. (Primary distribution target)
    *   `googlePlay`: Includes Firebase Analytics/Crashlytics. (Legacy/Secondary, removed from CI/CD)

## Building and Running

The project uses Gradle. Key commands:

### Build Types
*   **F-Droid Release (Recommended):**
    ```bash
    ./gradlew assembleFdroidRelease
    ```
*   **F-Droid Debug:**
    ```bash
    ./gradlew assembleFdroidDebug
    ```

### Testing
*   **Unit Tests (F-Droid):**
    ```bash
    ./gradlew testFdroidDebugUnitTest
    ```
*   **Instrumented Tests:**
    ```bash
    ./gradlew connectedAndroidTest
    ```

## Development Conventions

*   **Code Style:** Follow standard Kotlin coding conventions.
*   **Security:** Always use `java.util.Base64` (not `android.util.Base64`) in encryption logic for JVM testability. All backups must support AES-256 encryption.
*   **Dependency Injection:** Always use Hilt for injecting dependencies (ViewModels, Repositories, DAOs).
*   **Asynchrony:** Use Coroutines and Flow for all background operations (database access, I/O).
*   **UI Components:** Use Material 3 components. Layouts are XML-based using ViewBinding.
*   **Database:** Modifications to the database schema require a Room migration strategy.
*   **Versioning:** Version code and name are defined in `app/build.gradle`. Ensure `io.github.jwtiyar.simplertask.yml` (F-Droid metadata) is kept in sync for F-Droid releases.

## Key Files & Directories

*   `app/build.gradle`: Main build configuration, including dependencies and flavor definitions.
*   `io.github.jwtiyar.simplertask.yml`: Metadata file for F-Droid build and release info.
*   `app/src/main/java/io/github/jwtiyar/simplertask/data/backup/BackupManager.kt`: Core logic for encrypted backup/restore.
*   `app/src/main/java/io/github/jwtiyar/simplertask`: Source code root.
*   `app/src/main/res`: Resources (layouts, values, drawables).
*   `.github/workflows/android-ci.yml`: CI/CD workflow for building and testing the F-Droid flavor.
