# Privacy Policy

**Last Updated: May 24, 2026**

This Privacy Policy describes how your information is collected, used, and safeguarded when you use **To-Do: Task List & Reminder** (the "Application"), an Android application developed by Jwtyar Nariman.

We are committed to protecting your privacy. The Application is built with a privacy-first approach: it is designed to operate primarily offline, storing your task data locally on your device.

---

## 1. Information Collection and Use

### Local Task Data
*   **No Cloud Syncing:** All tasks, lists, categories, categories configurations, and reminder preferences you create are stored locally on your device in a secure SQL database (Room).
*   **No Server Transfers:** We do not host, store, or transmit your task data to any external server. 

### Encrypted Backups
*   The Application provides an import/export feature to backup your tasks.
*   Backups are encrypted locally on your device using industry-standard **AES-256-GCM** encryption with a password of your choice.
*   Your backup password and the encrypted files are stored only where you choose to save them on your device or your personal storage providers; we never have access to your backups or passwords.

### Diagnostics and Telemetry (Google Play Version Only)
To help us improve the Application's stability and performance, the version distributed via the Google Play Store includes Firebase SDKs:
*   **Firebase Crashlytics:** Collects anonymized crash reports and stack traces when the Application encounters an error or crash.
*   **Firebase Analytics:** Collects anonymous telemetry (such as screen views, session duration, and app opens) to understand how the Application is used. This includes the collection of the **Android Advertising ID (AD_ID)** to analyze demographic details, user retention, and feature usage.
*   **Usage Limitations:** The Advertising ID is used solely for analytics and app optimization. The Application does not contain advertisements, and the Advertising ID is not used for targeted or personalized advertising campaigns.

*(Note: The F-Droid version of this Application is completely FOSS and does not contain any Firebase SDKs, crash reporting, or analytics).*

---

## 2. Device Permissions

The Application requests the following permissions to support core features:
*   **Schedule Exact Alarm (`SCHEDULE_EXACT_ALARM`):** Used to trigger task reminders at the exact time you configure.
*   **Post Notifications (`POST_NOTIFICATIONS`):** Used to display task reminders on your device.
*   **Receive Boot Completed (`RECEIVE_BOOT_COMPLETED`):** Used to reschedule alarms automatically after your device reboots so you do not miss any reminders.
*   **Vibrate (`VIBRATE`):** Used to vibrate the device when a reminder notification is delivered.
*   **Advertising ID (`com.google.android.gms.permission.AD_ID` / `ACCESS_ADSERVICES_AD_ID`):** Used in the Google Play version only by Firebase SDKs to facilitate anonymous analytics (such as demographics and usage attribution).

---

## 3. Data Sharing and Third-Party Services

We do not sell, trade, or otherwise transfer your personal information to outside parties. 

For the Google Play version, anonymous crash logs and telemetry are processed by Google Firebase in accordance with Google's Privacy Policy. You can read more about how Google uses data here:
*   [Google Privacy & Terms](https://policies.google.com/privacy)
*   [Firebase Privacy and Security](https://firebase.google.com/support/privacy)

---

## 4. Children's Privacy

The Application does not address anyone under the age of 13. We do not knowingly collect personally identifiable information from children. If we discover that any such information has been collected, it is immediately deleted from our records.

---

## 5. Changes to This Privacy Policy

We may update our Privacy Policy from time to time. You are advised to review this page periodically for any changes. We will notify you of any changes by posting the new Privacy Policy on this page and updating the "Last Updated" date at the top.

---

## 6. Contact Us

If you have any questions or suggestions about this Privacy Policy, do not hesitate to contact us:

*   **Email:** jwtiyar@gmail.com
*   **GitHub:** [@jwtiyar](https://github.com/jwtiyar)
