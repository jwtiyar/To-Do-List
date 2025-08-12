# 📱 SimplerTask - Production Build Guide

A modern, feature-rich task management app for Android.

**Package:** `io.github.jwtiyar.simplertask`  
**Min SDK:** 26 (Android 8.0)  
**Target SDK:** 36 (Android 14)

## 🚀 Building for Production

### 1. Generate Release Keystore

```bash
cd keystore/
./generate-keystore.sh
```

Follow the prompts to create your signing certificate.

### 2. Set Environment Variables

```bash
export KEYSTORE_PASSWORD='your_keystore_password'
export KEY_ALIAS='simplertask'
export KEY_PASSWORD='your_key_password'
```

### 3. Enable Signing in build.gradle

Uncomment the `signingConfigs` section in `app/build.gradle` and add signing to release build.

### 4. Build Release APK

```bash
./gradlew assembleRelease
```

The signed APK will be in: `app/build/outputs/apk/release/`

## 📦 Build Variants

- **Debug:** `io.github.jwtiyar.simplertask.debug` - For development
- **Release:** `io.github.jwtiyar.simplertask` - For production

## 🔧 Features

- ✅ Complete task management (CRUD operations)
- ✅ Task reminders with notifications
- ✅ Backup & restore functionality
- ✅ Multiple themes (Light/Dark/Auto)
- ✅ Multi-language support
- ✅ Search and filtering
- ✅ Auto-refresh
- ✅ Material Design 3

## 🏗️ Architecture

- **MVVM** with Repository pattern
- **Room** database for local storage
- **Paging 3** for efficient list handling
- **Coroutines** for async operations
- **StateFlow** for reactive UI

## 📋 Production Checklist

- ✅ Package name: `io.github.jwtiyar.simplertask`
- ✅ Release build optimization enabled
- ✅ ProGuard rules configured
- ✅ Code minification enabled
- ✅ Resource shrinking enabled
- ⚠️ Generate release keystore
- ⚠️ Configure app signing
- ⚠️ Test release build
- ⚠️ Create privacy policy

## 🔐 Security

- App uses only necessary permissions
- Local data encryption (Room database)
- No network requests (fully offline)
- Secure backup file format

## 📱 Minimum Requirements

- Android 8.0 (API 26) or higher
- 50MB storage space
- Notification permissions (for reminders)

---

**Ready for Google Play Store deployment!** 🎉
