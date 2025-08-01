# Task Manager Android App

A simple Android application built with Kotlin that allows users to create and manage their own tasks. Users can add custom tasks, mark them as completed, and remove completed tasks from the list.

## Features

- Add custom tasks with title and optional description
- Mark tasks as completed with checkboxes
- Visual feedback when tasks are completed (dimmed appearance)
- Remove completed tasks from the list
- Reset all tasks to uncompleted state
- Modern Material Design UI with dialog for task creation

## Requirements

- JDK 24 (ensure `/usr/lib/jvm/java-24-openjdk` is installed and set)
- Java 17 compatibility (project uses Java 17 for Android)
- Android Studio (Hedgehog or later recommended)
- Android SDK 26 (API level 26) or higher
- Kotlin 1.9.10 or later
- Gradle 8.14 or later

## Building the APK

### From the command line:
1. Open a terminal in the project root (where `gradlew` is located).
2. Run:
   ```sh
   ./gradlew assembleDebug
   ```
   The APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

For a release APK:
   ```sh
   ./gradlew assembleRelease
   ```
   (You must configure signing for release builds.)

### From Android Studio:
1. Open the project in Android Studio.
2. Build → Build Bundle(s) / APK(s) → Build APK(s).
3. Find the APK in the output directory as above.

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/taskmanager/
│   │   ├── MainActivity.kt
│   │   ├── Task.kt
│   │   ├── TaskAdapter.kt
│   ├── res/
│   │   ├── layout/
│   │   ├── values/
│   │   ├── drawable/
│   │   └── xml/
│   └── AndroidManifest.xml
└── build.gradle
```

## Troubleshooting

- **JDK errors:** Make sure only JavaSE-24 is referenced in your settings and `/usr/lib/jvm/java-24-openjdk` exists and contains `javac`.
- **Gradle errors:** Use Gradle 8.14 or later. Update deprecated Groovy DSL syntax to assignment (`property = value`).
- **Resource errors:** Ensure all referenced drawables and colors exist in `res/`.
- **APK not generated:** Check the build output for errors and resolve any missing dependencies or misconfigurations.

## License

This project is open source and available under the Apache License 2.0. 