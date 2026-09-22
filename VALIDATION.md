# Validation — AppLingo 1.0, build 5

## Automated verification

- Clean `assembleDebug testDebugUnitTest lintDebug` succeeded using JDK 17, Gradle 8.13, and AGP 8.11.1.
- **40 tests passed**, zero failures/errors: 10 command, 9 access-state, 14 Android UI/storage, and 7 bulk/undo tests.
- Lint: **0 errors, 26 warnings**, primarily adapter refresh efficiency, existing unused resources, and untranslated text; also target-SDK/deprecation/allocation cleanup.
- Reviewed generated light-mode, large-text, and editor screens. Fixed the app-count wrapping at 200% font size and strengthened the regression assertion.
- Packaged APK: `dev.applingo`, versionName **1.0**, versionCode **5**, minSdk 33, targetSdk 35, compileSdk 36. No INTERNET permission.
- APK signature verified. Certificate SHA-256: `36641d01bf7cedfb75f62a216f8915dbf2aa00ec64664af85cfa80092487197c` (same as the preceding distributed build).
- APK SHA-256: `6572fd3ee2fe64c840b088dd8c2fcf3e01cbbb690312f6d903c674b6cc39ed83`.
- `adb devices` reported no attached devices.

Regression tests include command validation and shell escaping, access-state transitions and stale callbacks, activity/editor/predictive-back behavior, phone-language refresh, favorites persistence/filtering, selection after rotation, large-text layout, persistent undo storage, partial bulk failures, conflicting external changes, interrupted undo retries, read-back mismatches, and storage failures before privileged writes.

## Device testing status

No physical Android device is connected. Automated tests do not establish live Root/Shizuku integration, actual translations, OEM compatibility, TalkBack behavior, or system gesture animation on a physical device. OnePlus/ColorOS compatibility is not claimed as tested.

Suggested device checks on the OnePlus 13T:

1. Install over the previous signed APK and verify version 1.0; connect through KernelSU/root.
2. Change one multilingual app, reopen it, and verify its translation. Reset to Follow system and compare the displayed phone language.
3. Star two apps, search/filter, restart AppLingo, and confirm the stars remain.
4. Select two apps with different existing languages, apply another language, and undo. Both should return to their own previous settings.
5. Apply a change, close/reopen AppLingo, and undo. Change one target manually in Android Settings before undoing again; that target should be skipped.
6. Repeat in Shizuku mode. Stop Shizuku during a batch; reconnect and check that completed changes can be restored. Revoke Root/Shizuku permission and verify reconnection guidance.
7. Compare an app that declares supported languages with one that does not. Undeclared support must be labelled unknown.
8. Enable gesture navigation. Cancel and commit back gestures in the language picker, editor, and home screen.
9. Test TalkBack, 200% font size, dark mode, landscape, and disabled animations. Buttons should remain reachable and labeled.

## Implementation limits

A language preference cannot install missing translations. `LocaleConfig` may be absent, incomplete, or differ from an app’s own language settings. A related regional tag might still work even if the exact tag is undeclared.

Rows show last-checked settings; use Refresh shown languages after changing a target elsewhere. Refreshes and bulk operations are serial to avoid concurrent privileged writes. Bulk changes stop at the first error. Undo keeps entries for uncertain writes and external-change conflicts; a new confirmed operation replaces the previous history. The journal is local and excluded from device backups.

## Source and signing

The release tag for build 5 points to the expanded source used to build its APK. The earlier v1.0 tag remains historical. Gradle-generated debug signing is used with the retained private certificate; private signing material is excluded from source and public assets.
