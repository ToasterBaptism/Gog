#!/bin/bash
set -euo pipefail  # Enable strict mode to fail fast on errors

# Version Update Script for RL Sideswipe Access
# Usage: ./update-version.sh <version_name> [description]
# Example: ./update-version.sh "2.7-COORDINATE-FIX" "Fixed coordinate system mismatch"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <version_name> [description]"
    echo "Example: $0 '2.7-COORDINATE-FIX' 'Fixed coordinate system mismatch'"
    exit 1
fi

VERSION_NAME="$1"
DESCRIPTION="${2:-Updated version}"

# Ensure we're in the script's directory
cd "$(dirname "$0")"

# Extract version number for versionCode (increment from current)
CURRENT_VERSION_CODE=$(grep "versionCode" app/build.gradle | sed 's/.*versionCode \([0-9]*\).*/\1/')
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

echo "Updating version from $CURRENT_VERSION_CODE to $NEW_VERSION_CODE"
echo "Version name: $VERSION_NAME"
echo "Description: $DESCRIPTION"

# Update build.gradle
sed -i "s/versionCode $CURRENT_VERSION_CODE/versionCode $NEW_VERSION_CODE/" app/build.gradle
sed -i "s/versionName \".*\"/versionName \"$VERSION_NAME\"/" app/build.gradle

echo "✅ Updated app/build.gradle"
echo "   - versionCode: $NEW_VERSION_CODE"
echo "   - versionName: $VERSION_NAME"

# Build the APK
echo "🔨 Building APK..."
./gradlew assembleRelease

if [ $? -eq 0 ]; then
    # Ensure target directory exists
    mkdir -p "/tmp/webserver"
    
    # Verify APK exists before copying
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
    if [ ! -f "$APK_PATH" ]; then
        echo "❌ Error: APK not found at $APK_PATH"
        exit 1
    fi
    
    # Copy to webserver with versioned name
    APK_NAME="app-release-v$VERSION_NAME.apk"
    cp "$APK_PATH" "/tmp/webserver/$APK_NAME"
    echo "✅ Built and copied: $APK_NAME"
    
    # Show file size
    SIZE=$(ls -lh "/tmp/webserver/$APK_NAME" | awk '{print $5}')
    echo "📦 File size: $SIZE"
    
    echo ""
    echo "🎉 Version $VERSION_NAME ready!"
    echo "📁 File: $APK_NAME"
    echo "📝 Description: $DESCRIPTION"
    echo ""
    echo "Next steps:"
    echo "1. Update web pages with new version"
    echo "2. Test the APK"
    echo "3. Commit changes: git add -A && git commit -m 'Release v$VERSION_NAME: $DESCRIPTION'"
else
    echo "❌ Build failed!"
    exit 1
fi