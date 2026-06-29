---
name: release-build
description: "A comprehensive workflow for creating a production-ready release build of RollaMusicPlayer, including testing, optimization, and preparation for distribution."
---

# Skill: Release Build

## Overview
A comprehensive workflow for creating a production-ready release build of RollaMusicPlayer, including testing, optimization, and preparation for distribution.

## When to Use
- Preparing for app store release
- Creating beta builds for testing
- Generating signed APK/AAB for distribution
- Before major version releases

## Prerequisites
- All features are complete and tested
- Code review is complete
- Version number is decided
- Signing keys are available
- Release notes are prepared

## Workflow Steps

### Step 1: Pre-Release Checklist
**Goal**: Ensure everything is ready for release

**Actions**:
1. Verify all features are complete
2. Check all tests pass
3. Review open issues and bugs
4. Confirm version number
5. Update changelog
6. Review app permissions

**Checklist**:
- [ ] All planned features implemented
- [ ] All unit tests passing
- [ ] All UI tests passing
- [ ] No critical bugs
- [ ] No TODO comments in production code
- [ ] Version number updated
- [ ] Changelog updated
- [ ] Privacy policy updated if needed
- [ ] **No internet permission in manifest**
- [ ] **No network dependencies in code**
- [ ] **All features work offline verified**

**Output**: Confirmed readiness for release

---

### Step 2: Update Version Information
**Goal**: Set correct version for release

**Actions**:
1. Update version code in build.gradle
2. Update version name
3. Update app name if needed
4. Verify package name
5. Update release notes

**Files to Update**:
- `app/build.gradle.kts`: versionCode and versionName
- `CHANGELOG.md`: Add release notes
- `README.md`: Update version references

**Version Naming**:
- Use semantic versioning: MAJOR.MINOR.PATCH
- Increment versionCode by 1
- Example: 1.2.0 (versionCode 12)

**Output**: Version information updated

---

### Step 3: Code Quality Check
**Goal**: Ensure code meets quality standards

**Actions**:
1. Run ktlint or detekt
2. Fix any code style issues
3. Run static analysis
4. Check for security vulnerabilities
5. Review ProGuard/R8 rules

**Commands**:
```
./gradlew ktlintCheck
./gradlew detekt
./gradlew lint
```

**Review**:
- No critical lint warnings
- Code style is consistent
- No security issues
- ProGuard rules are correct

**Output**: Code quality verified

---

### Step 4: Run Full Test Suite
**Goal**: Verify all functionality works

**Actions**:
1. Run all unit tests
2. Run all integration tests
3. Run UI tests
4. Check test coverage
5. Fix any failing tests

**Commands**:
```
./gradlew test
./gradlew connectedAndroidTest
./gradlew jacocoTestReport
```

**Coverage Goals**:
- Unit tests: 80%+ coverage
- Critical paths: 100% coverage
- No flaky tests

**Output**: All tests passing

---

### Step 5: Manual Testing
**Goal**: Verify user experience

**Actions**:
1. Test on multiple devices
2. Test different Android versions
3. Test different screen sizes
4. Test dark mode
5. Test edge cases
6. Test offline functionality

**Test Scenarios**:
- Fresh install
- Upgrade from previous version
- Different device configurations
- Low memory conditions
- **Airplane mode / No connectivity**
- Permission denials (storage only)
- **Verify no network calls attempted**

**Devices to Test**:
- Phone (small, medium, large)
- Tablet
- Android 7.0 (minimum)
- Android 14 (target)

**Output**: Manual testing complete

---

### Step 6: Optimize Resources
**Goal**: Reduce app size and improve performance

**Actions**:
1. Optimize images and assets
2. Remove unused resources
3. Enable resource shrinking
4. Enable code shrinking
5. Review dependencies

**Build Configuration**:
```
buildTypes {
    release {
        minifyEnabled = true
        shrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

**Optimization Checks**:
- [ ] Images are compressed
- [ ] Unused resources removed
- [ ] R8 optimization enabled
- [ ] App size is acceptable
- [ ] No unnecessary dependencies

**Output**: Resources optimized

---

### Step 7: Configure Signing
**Goal**: Set up release signing

**Actions**:
1. Verify keystore exists
2. Configure signing in build.gradle
3. Set up environment variables
4. Test signing configuration
5. Secure keystore file

**Signing Configuration**:
```
signingConfigs {
    release {
        storeFile = file(System.getenv("KEYSTORE_FILE"))
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
```

**Security**:
- Never commit keystore to git
- Use environment variables
- Store keystore securely
- Document signing process

**Output**: Signing configured

---

### Step 8: Build Release APK/AAB
**Goal**: Generate signed release build

**Actions**:
1. Clean previous builds
2. Build release APK
3. Build release AAB (for Play Store)
4. Verify signatures
5. Test installation

**Commands**:
```
./gradlew clean
./gradlew assembleRelease
./gradlew bundleRelease
```

**Verification**:
```
# Verify APK signature
apksigner verify --verbose app-release.apk

# Check APK contents
aapt dump badging app-release.apk
```

**Output**: Signed release builds

---

### Step 9: Test Release Build
**Goal**: Verify release build works correctly

**Actions**:
1. Install release APK on test device
2. Test all critical features
3. Check for crashes
4. Verify ProGuard didn't break anything
5. Test upgrade from previous version

**Test Focus**:
- App launches correctly
- All features work
- No crashes
- Performance is good
- Upgrade works smoothly

**Output**: Release build verified

---

### Step 10: Prepare Store Listing
**Goal**: Get ready for distribution

**Actions**:
1. Prepare app description
2. Create screenshots
3. Design feature graphic
4. Write release notes
5. Set up store listing

**Store Assets**:
- App icon (512x512)
- Feature graphic (1024x500)
- Screenshots (phone and tablet)
- App description (emphasize offline & privacy)
- Short description (mention "fully offline")
- Release notes

**Privacy Messaging**:
- Emphasize "100% offline operation"
- Highlight "No data collection"
- Mention "Privacy-focused design"
- State "No internet required"

**Languages**:
- Prepare translations if needed
- Localize store listing
- Translate release notes

**Output**: Store listing ready

---

### Step 11: Create Release Tag
**Goal**: Mark release in version control

**Actions**:
1. Commit all changes
2. Create git tag
3. Push tag to remote
4. Create GitHub release
5. Attach release builds

**Git Commands**:
```
git add .
git commit -m "Release version 1.2.0"
git tag -a v1.2.0 -m "Version 1.2.0"
git push origin main
git push origin v1.2.0
```

**GitHub Release**:
- Create release from tag
- Add release notes
- Attach APK/AAB files
- Mark as pre-release if beta

**Output**: Release tagged

---

### Step 12: Distribution
**Goal**: Publish the release

**Actions**:
1. Upload to Play Store Console
2. Fill in release details
3. Set rollout percentage
4. Submit for review
5. Monitor for issues

**Play Store Steps**:
1. Create new release
2. Upload AAB file
3. Add release notes
4. Set rollout (e.g., 10% initially)
5. Review and publish

**Post-Release**:
- Monitor crash reports
- Watch user reviews
- Track analytics
- Prepare hotfix if needed

**Output**: App published

---

## Build Configuration Example

### build.gradle.kts (app level)
```kotlin
android {
    namespace = "com.rolla.musicplayer"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.rolla.musicplayer"
        minSdk = 24
        targetSdk = 34
        versionCode = 12
        versionName = "1.2.0"
    }
    
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### ProGuard Rules Example
```
# Keep data classes
-keep class com.rolla.musicplayer.data.model.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Keep ExoPlayer classes
-keep class com.google.android.exoplayer2.** { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
```

## Common Issues & Solutions

### Issue: ProGuard Breaks App
**Symptoms**: Crashes in release build, works in debug
**Solution**:
- Add ProGuard rules for affected classes
- Test release build thoroughly
- Use `-keep` rules for reflection-based code

### Issue: App Size Too Large
**Symptoms**: APK/AAB exceeds size limits
**Solution**:
- Enable resource shrinking
- Remove unused dependencies
- Use vector drawables
- Enable app bundle splits

### Issue: Signing Fails
**Symptoms**: Build fails during signing
**Solution**:
- Verify keystore path
- Check environment variables
- Validate keystore password
- Ensure keystore is not corrupted

### Issue: Tests Fail in CI
**Symptoms**: Tests pass locally but fail in CI
**Solution**:
- Check CI environment configuration
- Verify test dependencies
- Review test timeouts
- Check for flaky tests

## Rollback Plan

If issues are discovered after release:

1. **Immediate Actions**:
   - Halt rollout in Play Store
   - Assess severity of issue
   - Communicate with users

2. **Quick Fix**:
   - Create hotfix branch
   - Fix critical issue
   - Fast-track testing
   - Release patch version

3. **Major Issues**:
   - Roll back to previous version
   - Investigate root cause
   - Plan proper fix
   - Schedule new release

## Success Criteria

Release is complete when:
- [ ] All tests pass
- [ ] Code quality checks pass
- [ ] Manual testing complete
- [ ] **Offline functionality verified in airplane mode**
- [ ] **No internet permission in manifest confirmed**
- [ ] **Privacy requirements validated**
- [ ] Release build created and signed
- [ ] Store listing prepared (with offline/privacy messaging)
- [ ] Release tagged in git
- [ ] App uploaded to Play Store
- [ ] Release notes published
- [ ] Monitoring in place
- [ ] Team notified

## Post-Release Monitoring

### First 24 Hours
- Monitor crash reports
- Watch user reviews
- Check analytics
- Respond to issues quickly

### First Week
- Track adoption rate
- Monitor performance metrics
- Gather user feedback
- Plan hotfix if needed

### Ongoing
- Track key metrics
- Plan next release
- Address user feedback
- Update documentation

## Related Skills
- [Add New Screen](../add-new-screen/SKILL.md)
- [Debug Playback Issue](../debug-playback-issue/SKILL.md)

## Related Agents
- [Code Reviewer](../../agents/code-reviewer.md) - For final code review
- [Test Writer](../../agents/test-writer.md) - For test verification