#!/bin/bash

# SimplerTask Release Build Script
# This script helps you build a signed release APK

echo "🚀 SimplerTask - Release Build"
echo "============================="

cd "$(dirname "$0")"

# Check if keystore exists
KEYSTORE_FILE="keystore/simplertask-release.jks"
if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "❌ Keystore not found at $KEYSTORE_FILE"
    echo "Please run: cd keystore && ./generate-keystore.sh"
    exit 1
fi

echo "✅ Keystore found: $KEYSTORE_FILE"
echo ""

# Prompt for passwords if not set
if [ -z "$KEYSTORE_PASSWORD" ]; then
    echo "🔐 Enter keystore password:"
    read -s KEYSTORE_PASSWORD
    export KEYSTORE_PASSWORD
fi

if [ -z "$KEY_PASSWORD" ]; then
    echo "🔑 Enter key password:"
    read -s KEY_PASSWORD
    export KEY_PASSWORD
fi

# Set key alias
export KEY_ALIAS="simplertask"

echo ""
echo "📦 Building signed release APK..."
echo "Package: io.github.jwtiyar.simplertask"
echo "Version: 1.0"
echo ""

# Clean and build release
./gradlew clean assembleRelease

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Release build successful!"
    
    # Find the APK
    RELEASE_APK="app/build/outputs/apk/release/SimplerTask-v1.0-release.apk"
    if [ -f "$RELEASE_APK" ]; then
        APK_SIZE=$(du -h "$RELEASE_APK" | cut -f1)
        echo "📱 Signed APK: $RELEASE_APK ($APK_SIZE)"
        echo ""
        echo "🎉 Ready for Google Play Store!"
        echo ""
        echo "Next steps:"
        echo "1. Test the release APK on a device"
        echo "2. Create store listing and screenshots"
        echo "3. Upload to Google Play Console"
    else
        echo "⚠️  APK file not found at expected location"
        echo "Check: app/build/outputs/apk/release/"
    fi
else
    echo ""
    echo "❌ Build failed!"
    echo "Check the error messages above."
    exit 1
fi
