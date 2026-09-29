#!/bin/sh
# Installs the APK on the connected device or emulator and checks that it really runs.
# Used by CI (.github/workflows/build.yml); works the same on a phone with USB debugging:
#   tools/install-check.sh app/build/outputs/apk/release/app-release.apk
set -eu
APK="$1"
PKG=com.kishore.brainrot
SERVICE="$PKG/com.reeltracker.service.ReelAccessibilityService"

adb install -r "$APK"
adb shell dumpsys package "$PKG" | grep -E "versionName|versionCode" | head -2

# The app opens and keeps running.
adb shell am start -W -n "$PKG/com.reeltracker.ui.MainActivity"
sleep 5
adb shell pidof "$PKG" || { echo "The app is not running after launch"; adb logcat -d | grep -iE "AndroidRuntime|$PKG" | tail -40; exit 1; }

# The accessibility service starts in its own process, ":bg".
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
for i in 1 2 3 4 5 6 7 8 9 10; do
  if adb shell pidof "$PKG:bg" >/dev/null; then echo "OK: installed, opened, and the service runs in $PKG:bg"; exit 0; fi
  sleep 2
done
echo "The service did not start"; adb logcat -d | grep -iE "AndroidRuntime|$PKG|Accessibility" | tail -40; exit 1
