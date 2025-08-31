# RL Sideswipe Access APK Downloads

## 🚀 Quick Download Links

### Stub Version (Lightweight - 26MB)
- **Direct Download:** [RL-Sideswipe-Access-v2.21-OPENCV-ENHANCED-stub-release.apk](./RL-Sideswipe-Access-v2.21-OPENCV-ENHANCED-stub-release.apk)
- **Features:** Core functionality without TensorFlow Lite ML features
- **Best for:** Testing, devices with limited storage

### TensorFlow Lite Version (Full Features - 56MB)
- **Direct Download:** [RL-Sideswipe-Access-v2.21-OPENCV-ENHANCED-tflite-release.apk](./RL-Sideswipe-Access-v2.21-OPENCV-ENHANCED-tflite-release.apk)
- **Features:** Complete app with machine learning capabilities
- **Best for:** Full functionality, advanced ball prediction

## 📱 Installation Instructions

1. **Enable Unknown Sources:**
   - Go to Settings > Security > Unknown Sources
   - Enable "Allow installation of apps from unknown sources"

2. **Download APK:**
   - Choose either Stub or TensorFlow Lite version above
   - Download to your Android device

3. **Install:**
   - Open the downloaded APK file
   - Follow installation prompts
   - Grant required permissions

4. **Setup:**
   - Launch RL Sideswipe Access
   - Grant screen capture and overlay permissions
   - Configure settings as needed

## ⚙️ System Requirements

- **Android Version:** 11 (API 30) or higher
- **RAM:** 4GB+ recommended
- **Storage:** 100MB+ free space
- **Dependencies:** Rocket League Sideswipe installed

## 🔧 Build Details

- **Version:** 2.21 OPENCV-ENHANCED
- **Build Date:** August 31, 2025
- **Target SDK:** Android 14 (API 34)
- **Min SDK:** Android 11 (API 30)
- **NDK Version:** 26.3.11579264
- **Gradle Version:** 8.7
- **Java Version:** OpenJDK 17

## 🛡️ Security Verification

### 📋 Build Verification
- **Source Repository**: [ToasterBaptism/Gog](https://github.com/ToasterBaptism/Gog)
- **Latest Commit**: `517213b` (main branch)
- **Release Tag**: v2.25 (versionCode 18)

### 🔍 Integrity Verification
```bash
# Verify APK SHA-256 checksum
sha256sum RL-Sideswipe-Access-v*.apk

# Verify model file integrity
sha256sum rl-sideswipe-access/android/app/src/tflite/assets/rl_sideswipe_ball_v1.tflite

# Expected checksums: [To be updated with actual values]
```

### 🏗️ Build Reproduction
```bash
# Clone and build from source
git clone https://github.com/ToasterBaptism/Gog.git
cd Gog/rl-sideswipe-access
git checkout 91b8a23

# Install dependencies and build
npm install
cd android && ./gradlew assembleStubRelease

# Verify build output matches distributed APK
sha256sum app/build/outputs/apk/stub/release/*.apk
```

### 🔍 Dependency Audit
```bash
# Audit npm dependencies for vulnerabilities
npm audit

# Check for high-severity issues
npm audit --audit-level high

# Review Android dependencies
cd android && ./gradlew dependencies > dependencies.txt

# Expected: No critical vulnerabilities in production dependencies
```

### 🔐 Code Review Verification
- **PR #1**: [Merged](https://github.com/ToasterBaptism/Gog/pull/1) - All reviewer feedback addressed
- **Review Status**: Check latest commit comments for verification
- **Security Scan**: Run static analysis tools on source code before deployment

## 📋 Permissions Required

- **Screen Capture:** For analyzing Rocket League Sideswipe gameplay
- **System Alert Window:** For overlay display
- **Foreground Service:** For background processing
- **Internet:** For potential future features (currently minimal usage)

## 🐛 Troubleshooting

### Installation Issues
- Ensure "Unknown Sources" is enabled
- Check available storage space
- Try restarting device if installation fails

### Runtime Issues
- Grant all requested permissions
- Ensure Rocket League Sideswipe is installed
- Check Android version compatibility

### Performance Issues
- Close other apps to free RAM
- Consider using Stub version on older devices
- Restart the app if overlay becomes unresponsive

## 📞 Support

For issues, bugs, or feature requests, please visit the [GitHub repository](https://github.com/ToasterBaptism/Gog) and create an issue.

---

**⚠️ Disclaimer:** This app is for educational and enhancement purposes. Use responsibly and in accordance with Rocket League Sideswipe's terms of service.