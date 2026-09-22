# AppLingo 1.0 — build 5

Change per-app language preferences on Android 13+ using **Root or Shizuku**. Material 3 Expressive styling, dynamic colours, dark mode, predictive back, and the phone’s actual language preferences are included.

## What’s new

- Search apps by name or package; pin favorites with the star and filter to favorites.
- Read an app’s current override by tapping it, or use **Actions → Refresh shown languages** to check the filtered list. Rows say **Last checked**, so a previous read is not presented as live monitoring. Follow system includes the phone language.
- Multi-select apps (or long-press an app), choose a language once, and apply it to the selection. Selection survives rotation and searches; the selection count includes hidden selected apps.
- Persistent **Undo last change** restores each app’s previous setting, including Follow system. Previous values are saved before each write. An interrupted operation stops further changes and retains undo history. Undo skips apps whose settings changed elsewhere. Starting another operation replaces the prior undo history after confirmation.
- Declared-language hints use Android’s `LocaleConfig`, where available, and place those languages first in the picker. Undeclared support is labelled unknown. Choosing an undeclared language warns before applying. A declared language is a hint, not proof every screen is translated.
- Reconnect guidance for stopped Shizuku, lost authorization, expired bindings, and failed privileged requests. Root access is rechecked on return and requests fail back to setup when access is lost.
- Results explain when to reopen an app and include **Open app** for a single-app result.
- Larger touch targets, descriptive favorite/selection labels, live progress text, expandable row text, compact headings at large font sizes, and respect for disabled system animations.

## Install and use

Download `AppLingo-v1.0.apk` from the latest release. Enable installation from your browser/file manager if Android asks, install, and choose Root or Shizuku. With Shizuku, start its service first, then grant AppLingo access.

Choose an app, select a language, and confirm. The language must exist in the target app; AppLingo cannot add translations or override every app’s own language engine. Changes apply only to the current Android profile. App data is never cleared. No ads, analytics, or Internet permission.

The public version remains **1.0**, with Android **versionCode 5**. The retained certificate supports upgrading over earlier builds signed with it. This is a debug-signed sideload build. The first experimental build used a different temporary key and cannot be upgraded with this signature.

Favorites and the latest undo history are stored locally and are excluded from backups. Uninstalling AppLingo removes that history but does not reset Android’s per-app language preferences.

## Build

The repository now contains normal source directories. Open the repository root in Android Studio, or use JDK 17, Android SDK Platform 36, Build Tools 36.0.0, and the included Gradle 8.13 wrapper:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

On Windows use `gradlew.bat`. Set `ANDROID_HOME` or create an untracked `local.properties` with `sdk.dir` pointing to your SDK. The output is `app/build/outputs/apk/debug/app-debug.apk`. A local build uses your own debug certificate and cannot replace the distributed APK unless signed with the original private key. The private key is deliberately absent from this repository and the source ZIP.

See [VALIDATION.md](VALIDATION.md) for automated checks, limitations, and the physical-device test checklist. Generated UI previews use Robolectric fixtures, not a real phone. Root/Shizuku integration and OEM behavior still need device testing.

Dependencies include Material Components 1.14.0, AndroidX, and Shizuku API 13.1.5. Third-party license notices are included in the app assets. Android language configuration reference: https://developer.android.com/reference/android/app/LocaleConfig
