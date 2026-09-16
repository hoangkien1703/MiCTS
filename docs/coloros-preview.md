# MiCTS Preview 2 — sidebar screen capture

Download **MiCTS-Preview-ColorOS.apk** from Assets. This installs **MiCTS Preview 2** (`com.parallelc.micts.preview2`, version code 1002) alongside both the original MiCTS and the first preview. The first preview signing key was not retained, so this uses a separate package instead of requiring removal of that preview. A dedicated preview-only signing key is now created at an explicit path, cached, and checked against the APK certificate.

## Fix being tested

Preview 1 opened Google's Circle to Search UI but showed a blank screen when launched from the ColorOS sidebar. It called `Activity.showAssist()` while MiCTS was the foreground activity and kept that activity alive for 1.2 seconds. Android passes the caller's activity token on that route and limits assist data selection to that activity.

Preview 2 uses a no-display launcher that finishes immediately. Only after its destruction does an application-context request wait briefly (at least 250 ms) for the underlying app/sidebar transition to settle, then invoke the existing voice-interaction entry point with assist data, screenshot, and application-source flags. This retains the source flag used by preview 1, without passing MiCTS as the activity to capture. A rejected request gets one gesture-source fallback. An accepted request is not retried.

There is no `Activity.showAssist()` call in the new path. No accessibility, root, screen-recording, or persistent background service permission is added. Android and Google handle the screenshot; MiCTS does not read or save it. The app does not bypass protected screens.

Newer taps cancel pending requests; duplicate destruction callbacks cannot queue duplicates. Locked screens, screen-off states, and requests older than three seconds are rejected. The short handoff cannot inspect which unrelated app the user switches to during the delay, so remain on the screen being searched.

## Test on OnePlus Ace 5 / ColorOS 16.0.10

1. Install **MiCTS Preview 2** as a separate app. Keep the original MiCTS and preview 1 for comparison.
2. Long-press **MiCTS Preview 2 → Settings** and leave **Sidebar compatibility mode** enabled (on by default).
3. Add **MiCTS Preview 2** to the ColorOS sidebar, open a normal webpage or another app, and launch it from the sidebar.
4. Check that the underlying screen appears in Circle to Search and can be selected. Repeat several times without opening Google in between, then after locking/unlocking and idle time.
5. If the sidebar is captured or the transition has not finished, try **Default trigger delay** around 500–800 ms in Preview Settings.
6. If it is still blank or the original intermittent failure returns, use **Copy diagnostics** after a failure and include the app you were searching. Turn **Sidebar compatibility mode** off only to compare with the original invocation behavior.

Keep Google selected as the default digital assistant. A successful request result only means Android accepted the invocation; Google may still decline Circle to Search or return no screen content. The sidebar fix and long-term reliability need real-device confirmation.

## Validation

The workflow runs 11 JVM regression tests covering bounded fallback, cancellation, duplicate handoffs, replacement by a newer request, expiry, and screen locking. It builds the APK and verifies its signature, preview package and label, version code, Settings shortcut, and use of the dedicated preview signing key before publishing. These checks do not reproduce ColorOS or Google's UI.

References: [Android Activity.showAssist](https://developer.android.com/reference/android/app/Activity#showAssist(android.os.Bundle)), [AOSP activity-token filtering](https://android.googlesource.com/platform/frameworks/base/+/master/services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerServiceImpl.java), [AOSP voice-interaction entry points](https://android.googlesource.com/platform/frameworks/base/+/master/services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerService.java).
