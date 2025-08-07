#!/usr/bin/env python3

import os
import sys

def add_strings_to_file(file_path, strings_to_add):
    """Add missing strings to a language file"""
    if not strings_to_add:
        return True
        
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find where to insert the missing strings (before </resources>)
        insertion_point = content.rfind('</resources>')
        if insertion_point == -1:
            print(f"  ✗ Could not find </resources> tag in {file_path}")
            return False
        
        # Insert the missing strings
        new_strings = '\n' + '\n'.join([f'    {s}' for s in strings_to_add]) + '\n'
        content = content[:insertion_point] + new_strings + content[insertion_point:]
        
        # Write back the updated content
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"  ✓ Added {len(strings_to_add)} missing strings to: {file_path}")
        return True
        
    except Exception as e:
        print(f"  ✗ Error adding strings to {file_path}: {e}")
        return False

def main():
    """Main function to add all missing translations"""
    base_path = "app/src/main/res"
    
    # Missing translations from lint report - organized by language
    missing_translations = {
        # Portuguese (pt) - missing many strings
        "values-pt/strings.xml": [
            '<string name="nav_all_tasks">All Tasks</string>',
            '<string name="nav_saved_tasks">Saved Tasks</string>',
            '<string name="nav_archive">Archive</string>',
            '<string name="theme_dialog_title">Choose App Theme</string>',
            '<string name="theme_light">Light</string>',
            '<string name="theme_dark">Dark</string>',
            '<string name="theme_system_default">System Default</string>',
            '<string name="theme_system">System theme selected</string>',
            '<string name="task_added_with_reminder">Task added with reminder</string>',
            '<string name="task_added_successfully">Task added successfully</string>',
            '<string name="enter_task_title">Please enter a task title</string>',
            '<string name="reminder_time_past">Selected reminder time is in the past. Please choose a future time.</string>',
            '<string name="all_tasks_reset">All tasks have been reset to pending.</string>',
            '<string name="error_resetting_tasks">Error resetting tasks: %s</string>',
            '<string name="removed_completed_tasks">Removed %d completed tasks</string>',
            '<string name="switch_to_completed_tab">Switch to \'Completed\' tab to clear tasks</string>',
            '<string name="no_completed_tasks">No completed tasks to remove on this tab</string>',
            '<string name="notification_permission_needed">This app needs permission to post notifications for task reminders.</string>',
            '<string name="exact_alarm_permission_title">Exact Alarm Permission Needed</string>',
            '<string name="exact_alarm_permission_message">To ensure timely task reminders, this app needs permission to schedule exact alarms. Please grant this permission in the app settings.</string>',
            '<string name="exact_alarm_not_granted">Exact alarm permission not granted. Reminders may be less precise.</string>'
        ],
        
        # Dutch (nl) - missing many strings
        "values-nl/strings.xml": [
            '<string name="nav_all_tasks">All Tasks</string>',
            '<string name="nav_saved_tasks">Saved Tasks</string>',
            '<string name="nav_archive">Archive</string>',
            '<string name="theme_dialog_title">Choose App Theme</string>',
            '<string name="theme_light">Light</string>',
            '<string name="theme_dark">Dark</string>',
            '<string name="theme_system_default">System Default</string>',
            '<string name="theme_system">System theme selected</string>',
            '<string name="task_added_with_reminder">Task added with reminder</string>',
            '<string name="task_added_successfully">Task added successfully</string>',
            '<string name="enter_task_title">Please enter a task title</string>',
            '<string name="reminder_time_past">Selected reminder time is in the past. Please choose a future time.</string>',
            '<string name="all_tasks_reset">All tasks have been reset to pending.</string>',
            '<string name="error_resetting_tasks">Error resetting tasks: %s</string>',
            '<string name="removed_completed_tasks">Removed %d completed tasks</string>',
            '<string name="switch_to_completed_tab">Switch to \'Completed\' tab to clear tasks</string>',
            '<string name="no_completed_tasks">No completed tasks to remove on this tab</string>',
            '<string name="notification_permission_needed">This app needs permission to post notifications for task reminders.</string>',
            '<string name="exact_alarm_permission_title">Exact Alarm Permission Needed</string>',
            '<string name="exact_alarm_permission_message">To ensure timely task reminders, this app needs permission to schedule exact alarms. Please grant this permission in the app settings.</string>',
            '<string name="exact_alarm_not_granted">Exact alarm permission not granted. Reminders may be less precise.</string>'
        ],
        
        # Turkish (tr) - missing many strings
        "values-tr/strings.xml": [
            '<string name="nav_all_tasks">All Tasks</string>',
            '<string name="nav_saved_tasks">Saved Tasks</string>',
            '<string name="nav_archive">Archive</string>',
            '<string name="theme_dialog_title">Choose App Theme</string>',
            '<string name="theme_light">Light</string>',
            '<string name="theme_dark">Dark</string>',
            '<string name="theme_system_default">System Default</string>',
            '<string name="theme_system">System theme selected</string>',
            '<string name="task_added_with_reminder">Task added with reminder</string>',
            '<string name="task_added_successfully">Task added successfully</string>',
            '<string name="enter_task_title">Please enter a task title</string>',
            '<string name="reminder_time_past">Selected reminder time is in the past. Please choose a future time.</string>',
            '<string name="all_tasks_reset">All tasks have been reset to pending.</string>',
            '<string name="error_resetting_tasks">Error resetting tasks: %s</string>',
            '<string name="removed_completed_tasks">Removed %d completed tasks</string>',
            '<string name="switch_to_completed_tab">Switch to \'Completed\' tab to clear tasks</string>',
            '<string name="no_completed_tasks">No completed tasks to remove on this tab</string>',
            '<string name="notification_permission_needed">This app needs permission to post notifications for task reminders.</string>',
            '<string name="exact_alarm_permission_title">Exact Alarm Permission Needed</string>',
            '<string name="exact_alarm_permission_message">To ensure timely task reminders, this app needs permission to schedule exact alarms. Please grant this permission in the app settings.</string>',
            '<string name="exact_alarm_not_granted">Exact alarm permission not granted. Reminders may be less precise.</string>'
        ],
        
        # Chinese (zh) - missing many strings  
        "values-zh/strings.xml": [
            '<string name="theme_light">Light</string>',
            '<string name="theme_dark">Dark</string>',
            '<string name="theme_system_default">System Default</string>',
            '<string name="theme_system">System theme selected</string>',
            '<string name="task_added_with_reminder">Task added with reminder</string>',
            '<string name="task_added_successfully">Task added successfully</string>',
            '<string name="enter_task_title">Please enter a task title</string>',
            '<string name="reminder_time_past">Selected reminder time is in the past. Please choose a future time.</string>',
            '<string name="all_tasks_reset">All tasks have been reset to pending.</string>',
            '<string name="error_resetting_tasks">Error resetting tasks: %s</string>',
            '<string name="removed_completed_tasks">Removed %d completed tasks</string>',
            '<string name="switch_to_completed_tab">Switch to \'Completed\' tab to clear tasks</string>',
            '<string name="no_completed_tasks">No completed tasks to remove on this tab</string>',
            '<string name="notification_permission_needed">This app needs permission to post notifications for task reminders.</string>',
            '<string name="exact_alarm_permission_title">Exact Alarm Permission Needed</string>',
            '<string name="exact_alarm_permission_message">To ensure timely task reminders, this app needs permission to schedule exact alarms. Please grant this permission in the app settings.</string>',
            '<string name="exact_alarm_not_granted">Exact alarm permission not granted. Reminders may be less precise.</string>'
        ],
        
        # Italian (it) - missing some strings
        "values-it/strings.xml": [
            '<string name="theme_dialog_title">Choose App Theme</string>',
            '<string name="theme_system_default">System Default</string>',
            '<string name="notification_permission_needed">This app needs permission to post notifications for task reminders.</string>',
            '<string name="exact_alarm_permission_title">Exact Alarm Permission Needed</string>',
            '<string name="exact_alarm_permission_message">To ensure timely task reminders, this app needs permission to schedule exact alarms. Please grant this permission in the app settings.</string>',
            '<string name="exact_alarm_not_granted">Exact alarm permission not granted. Reminders may be less precise.</string>'
        ],
        
        # Korean (ko) - missing some strings
        "values-ko/strings.xml": [
            '<string name="theme_light">Light</string>',
            '<string name="theme_dark">Dark</string>',
            '<string name="theme_system_default">System Default</string>',
            '<string name="theme_system">System theme selected</string>',
            '<string name="notification_permission_needed">This app needs permission to post notifications for task reminders.</string>',
            '<string name="exact_alarm_permission_title">Exact Alarm Permission Needed</string>',
            '<string name="exact_alarm_permission_message">To ensure timely task reminders, this app needs permission to schedule exact alarms. Please grant this permission in the app settings.</string>',
            '<string name="exact_alarm_not_granted">Exact alarm permission not granted. Reminders may be less precise.</string>'
        ],
        
        # French (fr) - missing some strings
        "values-fr/strings.xml": [
            '<string name="theme_light">Light</string>',
            '<string name="theme_dark">Dark</string>',
            '<string name="theme_system_default">System Default</string>',
            '<string name="theme_system">System theme selected</string>',
            '<string name="notification_permission_needed">This app needs permission to post notifications for task reminders.</string>',
            '<string name="exact_alarm_permission_title">Exact Alarm Permission Needed</string>',
            '<string name="exact_alarm_permission_message">To ensure timely task reminders, this app needs permission to schedule exact alarms. Please grant this permission in the app settings.</string>',
            '<string name="exact_alarm_not_granted">Exact alarm permission not granted. Reminders may be less precise.</string>'
        ],
        
        # Spanish (es) - missing some strings
        "values-es/strings.xml": [
            '<string name="theme_light">Light</string>',
            '<string name="theme_dark">Dark</string>',
            '<string name="theme_system_default">System Default</string>',
            '<string name="theme_system">System theme selected</string>',
            '<string name="notification_permission_needed">This app needs permission to post notifications for task reminders.</string>',
            '<string name="exact_alarm_permission_title">Exact Alarm Permission Needed</string>',
            '<string name="exact_alarm_permission_message">To ensure timely task reminders, this app needs permission to schedule exact alarms. Please grant this permission in the app settings.</string>',
            '<string name="exact_alarm_not_granted">Exact alarm permission not granted. Reminders may be less precise.</string>'
        ]
    }
    
    success_count = 0
    total_count = 0
    
    for rel_file_path, strings_to_add in missing_translations.items():
        file_path = os.path.join(base_path, rel_file_path)
        if os.path.exists(file_path):
            total_count += 1
            if add_strings_to_file(file_path, strings_to_add):
                success_count += 1
        else:
            print(f"Warning: File not found: {file_path}")
    
    print(f"\nCompleted: {success_count}/{total_count} files updated successfully")
    
    if success_count == total_count:
        print("✅ All missing translations added successfully!")
        return 0
    else:
        print("⚠️ Some files had errors. Please check the output above.")
        return 1

if __name__ == "__main__":
    sys.exit(main())
