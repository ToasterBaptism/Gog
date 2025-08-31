#!/bin/bash
set -euo pipefail  # Enable strict mode to fail fast on errors

# React Native Android Build Script
# This script sets up the environment and builds the Android app

echo "🚀 React Native Android Build Script"
echo "===================================="

# Set environment variables
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

echo "📋 Environment Variables:"
echo "JAVA_HOME: $JAVA_HOME"
echo "ANDROID_HOME: $ANDROID_HOME"
echo "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
echo ""

# Run relative to the script's directory to avoid "wrong working dir" failures
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Check if we're in the right directory
if [ ! -f "package.json" ]; then
    echo "❌ Error: package.json not found. Please run this script from the project root directory."
    exit 1
fi

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    yarn install
fi

# Build options
echo "🔨 Build Options:"
echo "1. Debug build (yarn android)"
echo "2. Release build (yarn build:android:release)"
echo "3. Both debug and release"
echo ""

read -p "Choose build option (1-3): " choice

case $choice in
    1)
        echo "🔨 Building debug version..."
        yarn android
        ;;
    2)
        echo "🔨 Building release version..."
        yarn build:android:release
        ;;
    3)
        echo "🔨 Building debug version..."
        cd android && ./gradlew assembleDebug
        echo "🔨 Building release version..."
        ./gradlew assembleRelease
        cd ..
        ;;
    *)
        echo "❌ Invalid option. Building release version by default..."
        yarn build:android:release
        ;;
esac

echo ""
echo "✅ Build completed!"
echo "📱 APK files can be found in:"
echo "   - Debug: android/app/build/outputs/apk/debug/app-debug.apk"
echo "   - Release: android/app/build/outputs/apk/release/app-release.apk"