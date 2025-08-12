#!/bin/bash

# SimplerTask Keystore Generation Script
# Run this script to generate a release keystore for your app

echo "🔐 SimplerTask - Keystore Generation"
echo "=================================="

# Set keystore details
KEYSTORE_FILE="simplertask-release.jks"
KEY_ALIAS="simplertask"
VALIDITY_DAYS=10000

echo "📱 App: SimplerTask"
echo "📦 Package: io.github.jwtiyar.simplertask"
echo "🔑 Keystore: $KEYSTORE_FILE"
echo "🏷️  Alias: $KEY_ALIAS"
echo ""

# Check if keystore already exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️  Keystore already exists!"
    echo "If you want to regenerate, please delete the existing file first:"
    echo "rm $KEYSTORE_FILE"
    exit 1
fi

echo "Creating release keystore..."
echo "You'll be prompted for:"
echo "1. Keystore password (remember this!)"
echo "2. Key password (can be same as keystore password)"
echo "3. Certificate details (name, organization, etc.)"
echo ""

# Generate the keystore
keytool -genkey -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity $VALIDITY_DAYS \
    -storetype JKS

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Keystore generated successfully!"
    echo ""
    echo "📝 Next steps:"
    echo "1. Keep the keystore file safe and backed up"
    echo "2. Remember your passwords!"
    echo "3. Set environment variables for signing:"
    echo "   export KEYSTORE_PASSWORD='your_keystore_password'"
    echo "   export KEY_ALIAS='$KEY_ALIAS'"
    echo "   export KEY_PASSWORD='your_key_password'"
    echo ""
    echo "4. Uncomment the signingConfigs section in app/build.gradle"
    echo "5. Add 'signingConfig signingConfigs.release' to the release buildType"
    echo ""
    echo "🚀 Then you can build release APK with: ./gradlew assembleRelease"
else
    echo "❌ Failed to generate keystore"
    exit 1
fi
