# ShakeToLight

Android flashlight shake toggle built with Jetpack Compose.

## How it works

ShakeToLight uses an Android AccessibilityService to keep motion detection active and toggle the flashlight on device shakes without requiring an ongoing foreground notification.

- Requires Camera permission to control the flashlight.
- Requires enabling the shake detection service in Android Accessibility settings.
- The service only listens to device motion (accelerometer) and does not read window content or user inputs.

## Build

Build the debug APK with:

```sh
./gradlew assembleDebug
```

The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.
