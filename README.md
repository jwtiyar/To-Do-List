# Task Manager Android App

A simple Android application built with Kotlin that allows users to create and manage their own tasks. Users can add custom tasks, mark them as completed, and remove completed tasks from the list.

## Features

- Add custom tasks with title and optional description
- Mark tasks as completed with checkboxes
- Visual feedback when tasks are completed (dimmed appearance)
- Remove completed tasks from the list
- Reset all tasks to uncompleted state
- Modern Material Design UI with dialog for task creation

## How to Use

1. **Add Tasks:** Tap the "Add Task" button to create a new task
2. **Enter Task Details:** Fill in the task title (required) and description (optional)
3. **Complete Tasks:** Tap on a task or its checkbox to mark it as completed
4. **Visual Feedback:** Completed tasks appear dimmed to show progress
5. **Remove Completed:** Use the "Clear Completed" button to remove finished tasks
6. **Reset All:** Use the "Reset All" button to mark all tasks as uncompleted

## Requirements

- Android Studio Arctic Fox or later
- Android SDK 24 (API level 24) or higher
- Kotlin 1.9.10 or later
- Gradle 8.4 or later

## Building and Running

1. **Open the project in Android Studio:**
   - Launch Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the project directory and select it

2. **Sync the project:**
   - Android Studio will automatically sync the project with Gradle
   - Wait for the sync to complete

3. **Build the project:**
   - Go to Build → Make Project (or press Ctrl+F9 / Cmd+F9)
   - Wait for the build to complete successfully

4. **Run the app:**
   - Connect an Android device or start an emulator
   - Click the "Run" button (green play icon) or press Shift+F10
   - Select your target device and click "OK"

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/taskmanager/
│   │   ├── MainActivity.kt          # Main activity handling UI logic
│   │   ├── Task.kt                  # Data class for task objects
│   │   └── TaskAdapter.kt           # RecyclerView adapter for task list
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml    # Main activity layout
│   │   │   ├── item_task.xml        # Individual task item layout
│   │   │   └── dialog_add_task.xml  # Dialog for adding new tasks
│   │   ├── values/
│   │   │   ├── colors.xml           # Color definitions
│   │   │   ├── strings.xml          # String resources
│   │   │   └── themes.xml           # App theme
│   │   └── xml/
│   │       ├── backup_rules.xml     # Backup configuration
│   │       └── data_extraction_rules.xml # Data extraction rules
│   └── AndroidManifest.xml          # App manifest
└── build.gradle                     # App module build configuration
```

## User Interface

- **Add Task Button:** Prominent button to create new tasks
- **Task Dialog:** Clean dialog with Material Design input fields
- **Task List:** RecyclerView displaying all user-created tasks
- **Action Buttons:** Clear completed tasks and reset all functionality
- **Visual Feedback:** Completed tasks are visually distinguished

## Technologies Used

- **Kotlin** - Primary programming language
- **Android SDK** - Android development framework
- **RecyclerView** - For displaying the list of tasks
- **ViewBinding** - For type-safe view access
- **Material Design** - For modern UI components including dialogs
- **AlertDialog** - For task creation interface
- **TextInputLayout** - For enhanced input fields

## Customization

The app is designed to be flexible - users can add any tasks they want. The task creation dialog includes:

- **Title field** (required) - for the main task name
- **Description field** (optional) - for additional details
- **Validation** - ensures at least a title is provided
- **User feedback** - toast messages for successful actions

## Troubleshooting

- **Build errors:** Make sure you have the correct Android SDK and Kotlin version installed
- **Runtime errors:** Ensure your device/emulator is running API level 24 or higher
- **Sync issues:** Try File → Invalidate Caches and Restart
- **Dialog not appearing:** Check that Material Design components are properly included

## License

This project is open source and available under the Apache License 2.0. 