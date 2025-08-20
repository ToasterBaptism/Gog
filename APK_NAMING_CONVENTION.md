# 📱 APK Naming Convention - IMPORTANT

## ⚠️ MANDATORY NAMING RULE

**APK files MUST match the version number in their filename**

### ✅ Correct Examples:
- `RL-Sideswipe-Access-v2.17-ENHANCED-debug.apk`
- `RL-Sideswipe-Access-v2.17-ENHANCED-release.apk`
- `RL-Sideswipe-Access-v2.18-debug.apk`
- `RL-Sideswipe-Access-v2.18-release.apk`

### ❌ Incorrect Examples:
- `app-debug.apk` (generic name, no version)
- `app-release.apk` (generic name, no version)
- `RL-Sideswipe-Access-v2.16-debug.apk` (when actual version is v2.17)

## 🔧 Implementation Notes

### Current Status (v2.17):
- **Current APK names**: `app-debug.apk`, `app-release.apk` (INCORRECT)
- **Should be**: `RL-Sideswipe-Access-v2.17-ENHANCED-debug.apk`, `RL-Sideswipe-Access-v2.17-ENHANCED-release.apk`
- **Action**: Fix in next build, do NOT change current files

### For Future Builds:
1. **Update build.gradle** to include version in APK filename
2. **Modify build scripts** to use proper naming convention
3. **Update download links** to reflect new filenames
4. **Test download functionality** with new names

### Build Configuration:
```gradle
android {
    ...
    applicationVariants.all { variant ->
        variant.outputs.all {
            def versionName = variant.versionName
            def buildType = variant.buildType.name
            outputFileName = "RL-Sideswipe-Access-v${versionName}-${buildType}.apk"
        }
    }
}
```

## 📋 Checklist for Next Build:
- [ ] Update build.gradle with proper APK naming
- [ ] Test build process with new naming
- [ ] Update download page with correct filenames
- [ ] Update documentation with new naming convention
- [ ] Verify download links work with new filenames

**Remember**: This applies to ALL future builds. Version numbers in filenames help with:
- Version tracking
- Download organization  
- User clarity
- Build management