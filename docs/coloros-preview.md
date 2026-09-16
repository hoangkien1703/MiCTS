# MiCTS Preview for OnePlus / ColorOS

Download **MiCTS-Preview-ColorOS.apk** from this release's Assets.

- Installed name: **MiCTS Preview** (including its Quick Settings tile).
- Package: `com.parallelc.micts.preview`. The original `com.parallelc.micts` can stay installed; its settings and data are separate.
- This is a test build for OnePlus Ace 5 / ColorOS 16.0.10. No root or LSPosed is needed for the new trigger path. Keep Google selected as the default digital assistant.

## What changed

The original invokes `showSessionFromSession` with a null session token. It also invokes from `onCreate` and normally finishes immediately. The report that opening Google restores a single invocation suggests an assistant/session lifecycle problem; it does not prove the exact cause.

With **Fresh assistant session** enabled (the preview default), the app waits until its transparent activity has window focus, then calls Android's public `Activity.showAssist` with Circle to Search arguments. This asks the system to start the active assistant session without depending on an existing session token. A rejected request gets one bounded fallback to the legacy method; accepted requests are never blindly repeated. The activity allows a short interval for asynchronous assist capture, and cancels pending work when stopped.

Also removes Xiaomi-specific attribution on non-Xiaomi devices and includes the OS-configured contextual search key when available. Legacy/module invocation remains selectable in Settings.

Android can accept a request while Google chooses not to display Circle to Search (or displays the normal assistant instead). This preview cannot override Google's availability checks or ColorOS policy. A successful build is not a confirmed device fix.

## Compare on the phone

1. Install the preview alongside the original. Use the **MiCTS Preview** icon or its separate tile.
2. Try it several times, dismissing Circle to Search between attempts, without opening Google in between.
3. Repeat after locking/unlocking and after leaving the phone idle. Check that the captured screen is the app you intended to search.
4. Compare with the original MiCTS under the same conditions.
5. If the preview still fails or opens the regular assistant, long-press **MiCTS Preview → Settings**. Try **Fresh assistant session** off and report which mode works.
6. Use **Copy diagnostics** after a failure and include whether nothing appeared, the normal assistant appeared, or the wrong screen was captured. The report is stored locally and contains only device/app versions, assistant component, trigger method, and request results. No screenshots or screen text are collected by MiCTS.

## Validation

The publishing workflow runs JVM regression tests for request acceptance, rejection/fallback, cancellation, and prevention of duplicate invocations. It builds a signed APK and checks its signature, separate package, launcher label, and Settings shortcut target before publishing. Actual Google/ColorOS behavior still needs testing on the OnePlus device.

The preview uses a debug signing key cached separately from production signing. Keep this APK to reinstall if needed; a future build made after the signing cache expires may require reinstalling the preview only.

References: [Android Activity.showAssist](https://developer.android.com/reference/android/app/Activity#showAssist(android.os.Bundle)), [AOSP ActivityClientController](https://android.googlesource.com/platform/frameworks/base/+/12f5992e4df6/services/core/java/com/android/server/wm/ActivityClientController.java).
