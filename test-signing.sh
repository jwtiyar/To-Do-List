#!/bin/bash

# Quick test to verify signing configuration

echo "🔍 Testing Build Configuration"
echo "=============================="

cd "$(dirname "$0")"

echo "📋 Configuration Summary:"
echo "Package: io.github.jwtiyar.simplertask"
echo "Version: 1.0 (versionCode: 1)"
echo "Keystore: keystore/simplertask-release.jks"
echo "Key Alias: simplertask"
echo ""

# Test environment variables
echo "🔐 Checking environment variables..."

if [ -z "$KEYSTORE_PASSWORD" ]; then
    echo "⚠️  KEYSTORE_PASSWORD not set"
    echo "   Run: export KEYSTORE_PASSWORD='your_password'"
else
    echo "✅ KEYSTORE_PASSWORD is set"
fi

if [ -z "$KEY_PASSWORD" ]; then
    echo "⚠️  KEY_PASSWORD not set"
    echo "   Run: export KEY_PASSWORD='your_password'"
else
    echo "✅ KEY_PASSWORD is set"
fi

echo "✅ KEY_ALIAS will use default: simplertask"
echo ""

# Check keystore file
if [ -f "keystore/simplertask-release.jks" ]; then
    echo "✅ Keystore file exists"
    
    # Try to list keystore contents (will prompt for password)
    echo ""
    echo "🔑 Keystore information:"
    keytool -list -keystore keystore/simplertask-release.jks -alias simplertask
else
    echo "❌ Keystore file not found!"
    echo "   Run: cd keystore && ./generate-keystore.sh"
fi

echo ""
echo "📝 To build release APK:"
echo "1. Set environment variables:"
echo "   export KEYSTORE_PASSWORD='your_password'"
echo "   export KEY_PASSWORD='your_password'"
echo ""
echo "2. Run build script:"
echo "   ./build-release.sh"
echo ""
echo "3. Or manual gradle command:"
echo "   ./gradlew assembleRelease"
