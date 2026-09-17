# AppLingo 1.0 — Material 3 Expressive

Choose a different language for each Android app with **Root or Shizuku**. Requires Android 13+.

## Features

- Predictive back is explicitly enabled. Material's native bottom-sheet back handling animates gesture progress, restores a cancelled gesture, and dismisses only the top sheet when committed. No root-screen callback consumes Android's back-to-home animation.
- The welcome and app-list screens show the phone's system language name and BCP 47 tag. Multiple system languages are listed in preference order.
- Follow system shows those preferences in the editor and known app rows. The target app still chooses a translation it supports.
- System locales come from `LocaleManager.getSystemLocales()`, independent of AppLingo's own language, and refresh on return to the app.
- The APK retains the existing signing certificate and uses an increased Android version code for updates.

Predictive sheet progress requires Android 14+. Android 15+ supports opted-in back-to-home animations without the older developer toggle; the appearance also depends on system gesture navigation and animations. Android 13 retains normal back behavior. See [Android's predictive-back guide](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture) and [Material's bottom-sheet documentation](https://github.com/material-components/material-components-android/blob/1.14.0/docs/components/BottomSheet.md).

## Material 3 Expressive interface

- Actual **Material Components 1.14.0** and `Theme.Material3Expressive.DayNight.NoActionBar`.
- Expressive rounded controls, large headings, contained app rows, adaptive launcher icon, and language-selection bottom sheets.
- Wallpaper-based dynamic colours on supported devices; coordinated lavender light and dark fallback palettes.
- A dedicated startup screen for selecting and granting Root or Shizuku access.
- Successful access is verified by reading Android's locale service. A saved preference alone never unlocks the app list.
- Previously authorized Shizuku reconnects automatically when available. Root reconnects automatically after a successful setup in this version, with a fresh `su` check.
- Denied/revoked access, stopped Shizuku, and connection timeouts return to setup. Old asynchronous callbacks cannot unlock a newer connection attempt.
- Access is retained across activity recreation with a ViewModel and checked when returning from the background.

## Install and use

1. Install `AppLingo-v1.0.apk`.
2. Choose **Shizuku** or **Root** on the welcome screen.
3. For Shizuku, use **Open Shizuku**, start its service, return, and tap **Allow Shizuku access**. For Root, tap **Allow root access** and approve the superuser prompt.
4. Once verified, the app list opens. Search for an app and tap it.
5. Pick a language (or enter a BCP 47 tag) and tap **Apply language**.
6. **Follow system** removes an app's language override.

The connection badge on the app-list screen lets you change the access method. Enable **Include system apps** to see non-launchable/system packages in the current Android profile.

### Updating an existing installation

The public release and the app both use version **1.0**. Android versionCode is **4**, so this build updates earlier builds signed with the retained certificate. The first experimental build used a different temporary signing key; only that build requires uninstalling before installing this release. Android keeps per-app language overrides independently.

The signing key is backed up privately and excluded from the source archive. A locally rebuilt APK signed with a different key requires uninstalling the supplied APK first.

## Language support

You can request per-app locales even for apps absent from Android's built-in language picker. This does not add missing translations or override apps that enforce their own language setting. Only the current Android profile is managed. Some system components do not respond to per-app locales.

Common languages, including Traditional Chinese (Hong Kong), appear first in the searchable picker. A custom fallback list such as `zh-Hant-HK,en` is accepted. Every write is read back and compared before a success message is shown. Reopen the target app if needed.

## Privacy

No internet permission, ads or analytics. Installed-package visibility is used for the picker. AppLingo does not read private app data or change the global device language. Root/shell commands are restricted to identity verification and get/set locale requests; inputs are validated. Shizuku exposes a dedicated UserService with no general shell-execution method.

## Build and verify

JDK 17, Android SDK 36, Build Tools 36.0.0, Gradle 8.13 and AGP 8.11.1. The runtime requirement remains Android 13+ (minimum API 33); target API 35.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

On Windows use `gradlew.bat`. Set `ANDROID_HOME` or create a local `local.properties` pointing to your Android SDK. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Unit tests cover locale validation and the access state machine. Robolectric tests exercise the actual activity's startup visibility, backend selection, loading state, ready/lost transitions and light/dark/compact rendering. Test previews are written to `app/build/ui-previews/`.

This is a debug-signed sideload build, not a store release. Physical-device root/Shizuku integration still needs testing. See `VALIDATION.md`.

## Credits

- [Material Components for Android](https://github.com/material-components/material-components-android), Google, Apache 2.0.
- [AndroidX](https://developer.android.com/jetpack/androidx), Apache 2.0.
- [Shizuku API](https://github.com/RikkaApps/Shizuku-API), RikkaApps, MIT.
- [Android locale commands](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/locales/LocaleManagerShellCommand.java).

License notices are in `app/src/main/assets/`. Created with OpenAI Codex.
