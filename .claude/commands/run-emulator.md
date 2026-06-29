# Command: Run Emulator

## Description
Quick command to start the Android emulator for testing RollaMusicPlayer.

## Usage
When you need to test the app on an emulator, use this command to start it quickly.

## Prerequisites
- Android Studio installed
- At least one AVD (Android Virtual Device) created
- Emulator in PATH or Android SDK configured

## Command

### List Available Emulators
```bash
emulator -list-avds
```

### Start Default Emulator
```bash
# Windows
emulator -avd Pixel_5_API_34

# macOS/Linux
$ANDROID_HOME/emulator/emulator -avd Pixel_5_API_34
```

### Start Emulator with Options
```bash
# Start with specific options
emulator -avd Pixel_5_API_34 -no-snapshot-load -wipe-data

# Start with GPU acceleration
emulator -avd Pixel_5_API_34 -gpu host

# Start with specific memory
emulator -avd Pixel_5_API_34 -memory 2048
```

## Common AVD Names
- `Pixel_5_API_34` - Pixel 5 with Android 14
- `Pixel_7_API_33` - Pixel 7 with Android 13
- `Pixel_4_API_30` - Pixel 4 with Android 11
- `Tablet_API_34` - Tablet with Android 14

## Useful Options

### Performance
- `-gpu host` - Use host GPU for better performance
- `-memory 2048` - Set RAM to 2GB
- `-cores 4` - Use 4 CPU cores

### Testing
- `-no-snapshot-load` - Start fresh without saved state
- `-wipe-data` - Reset to factory settings
- `-no-audio` - Disable audio (faster startup)

### Display
- `-skin 1080x1920` - Custom screen resolution
- `-scale 0.5` - Scale display to 50%

## After Starting Emulator

### Install App
```bash
# Install debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Install and replace existing
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Launch App
```bash
# Launch main activity
adb shell am start -n com.rolla.musicplayer/.MainActivity
```

### View Logs
```bash
# View all logs
adb logcat

# Filter for app logs
adb logcat | grep RollaMusicPlayer

# Clear and view logs
adb logcat -c && adb logcat
```

## Troubleshooting

### Emulator Won't Start
1. Check if HAXM/KVM is installed
2. Verify Android SDK path
3. Try creating a new AVD
4. Check available disk space

### Slow Performance
1. Enable GPU acceleration: `-gpu host`
2. Increase memory: `-memory 2048`
3. Use x86_64 system image
4. Close other applications

### Can't Connect with ADB
```bash
# Restart ADB server
adb kill-server
adb start-server

# List connected devices
adb devices
```

## Quick Test Workflow

1. **Start Emulator**
   ```bash
   emulator -avd Pixel_5_API_34 -gpu host
   ```

2. **Wait for Boot** (check with)
   ```bash
   adb wait-for-device
   ```

3. **Install App**
   ```bash
   ./gradlew installDebug
   ```

4. **Launch App**
   ```bash
   adb shell am start -n com.rolla.musicplayer/.MainActivity
   ```

5. **View Logs**
   ```bash
   adb logcat | grep RollaMusicPlayer
   ```

## Alternative: Use Gradle Task

### Run on Emulator
```bash
# Build and install
./gradlew installDebug

# Run tests on emulator
./gradlew connectedAndroidTest
```

## Tips

- Create multiple AVDs for different Android versions
- Use snapshots to save emulator state
- Keep emulator running during development
- Use physical device when possible for better performance
- Test on both phone and tablet emulators

## Related Commands
- Build app: `./gradlew assembleDebug`
- Run tests: `./gradlew test`
- Install app: `./gradlew installDebug`
- Uninstall app: `adb uninstall com.rolla.musicplayer`