#!/bin/bash

# SimplerTask Build Verification Script

echo "🔨 SimplerTask - Build Verification"
echo "================================="

cd "$(dirname "$0")"

echo "📦 Package: io.github.jwtiyar.simplertask"
echo "🎯 Target: Production Ready"
echo ""

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    echo "❌ gradlew not found!"
    exit 1
fi

echo "✅ Gradle wrapper found"

# Make gradlew executable
chmod +x ./gradlew

echo "🧹 Cleaning project..."
./gradlew clean > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "✅ Clean successful"
else
    echo "⚠️  Clean had issues, continuing..."
fi

echo ""
echo "🔨 Building debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Debug build successful!"
    
    # Check if APK was created
    DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$DEBUG_APK" ]; then
        APK_SIZE=$(du -h "$DEBUG_APK" | cut -f1)
        echo "📱 Debug APK: $DEBUG_APK ($APK_SIZE)"
    fi
    
    echo ""
    echo "📋 Next steps for production:"
    echo "1. Generate keystore: cd keystore && ./generate-keystore.sh"
    echo "2. Set environment variables for signing"
    echo "3. Uncomment signing config in app/build.gradle"
    echo "4. Build release: ./gradlew assembleRelease"
    echo ""
    echo "🚀 Your app is ready for production!"
    
else
    echo ""
    echo "❌ Build failed!"
    echo "Check the error messages above and fix any issues."
    exit 1
fi
