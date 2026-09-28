# MiCTS Preview 2 — Google preparation update (1003)

Download **MiCTS-Preview-ColorOS.apk** from Assets. Install it as an **update to MiCTS Preview 2**. The package remains `com.parallelc.micts.preview2`, the app label remains **MiCTS Preview 2**, and the version is `1.0-coloros-preview.3` (1003). The original MiCTS and first preview stay separate. The workflow checks the APK against the certificate used for version 1002 before publishing.

## Why this update

On OnePlus Ace 5 / ColorOS 16.0.10 with Google 17.56.15, the user confirmed that Preview 2 captures the correct underlying app when it works. During a failure, diagnostics show `visible-screen application-source accepted=true`, but no search interface appears until Google is opened manually. The Boolean therefore cannot be used to decide whether the UI appeared.

This update tests whether briefly starting and connecting to Google's exported public search service before invoking Circle to Search resolves that accepted-but-invisible request. This is a targeted workaround for a suspected Google process/readiness issue, not a proven diagnosis of Google's internal failure.

## Changes

- Keep the working no-display sidebar launcher and current-screen capture path.
- Add **Prepare Google before searching**, enabled by default in this preview. Bind only the public search service advertised by the installed Google app, wait up to 1.5 seconds for a connection, and allow 200 ms for initialization. This does not open a Google activity or send any Binder commands, search queries, audio, or screen content to the preparation service.
- Hold a successful connection through the request and for 500 ms after acceptance, then unbind. Cancellation, a timeout, a null binding, a rejected bind, and a newer tap all clean up the connection. There is no permanent service or keep-alive.
- If the service is unavailable or restricted, record that result and use the normal trigger. Never bypass a service permission. Never repeat a request that Android already accepted.
- Recheck screen/lock state and request generation after preparation. Requests older than five seconds are discarded; the larger window accommodates preparation plus the existing configurable delay.
- Add preparation results to **Copy diagnostics**. A service connection still does not prove that Circle to Search is ready or visible.

No additional Android permissions, root, accessibility, or screen-recording setup is required. The Google service's availability and effect on ColorOS need device testing.

## Test

1. Install this APK over **MiCTS Preview 2**. Long-press its app icon → **Settings**.
2. Keep **Sidebar compatibility mode** and **Prepare Google before searching** enabled.
3. Open an ordinary webpage and launch **MiCTS Preview 2** from the sidebar repeatedly without opening Google. Repeat after leaving the phone idle and after locking/unlocking it.
4. If a failure recurs, use **Copy diagnostics** before opening Google and share the report. It now distinguishes successful preparation from timeout, unavailable service, or binding rejection.
5. To compare with version 1002 behavior, turn **Prepare Google before searching** off. Keep sidebar compatibility enabled to preserve the working capture handoff.

## Validation

The workflow runs JVM regression tests for preparation sequencing, bounded waits, cleanup on cancellation, unavailable services, screen locking, superseded requests, accepted-request deduplication, and the existing sidebar handoff. It builds and verifies package, version, label, shortcut target, signature, and compatibility with the installed Preview 2 certificate. These checks do not emulate Google's proprietary service or ColorOS; real-device reliability remains unverified.

API references: [Android bound services](https://developer.android.com/develop/background-work/services/bound-services), [ServiceConnection callbacks](https://developer.android.com/reference/android/content/ServiceConnection).
