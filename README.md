# ShakeToLight

Android flashlight shake toggle built with Jetpack Compose.

## Build variants

- `play`: uses the user-started foreground service, with its ongoing notification while shake detection is enabled.
- `sideload`: private experiment using an Android AccessibilityService to keep motion detection active without that foreground-service notification. Android requires the user to grant camera access and manually enable the service in Accessibility settings. The service does not retrieve window content. Disabling it is done in Android Accessibility settings.

The app does not add a Quick Settings tile. Shake detection is enabled from the app, then runs through the selected build's background mechanism.

The AccessibilityService variant is only for sideload evaluation. It is not represented as a Google Play compliant way to avoid foreground-service notifications, and shake detection reliability may vary by device and power management settings.

Build the APKs with:

```sh
./gradlew assemblePlayDebug assembleSideloadDebug
```

The sideload APK is at `app/build/outputs/apk/sideload/debug/app-sideload-debug.apk`.

