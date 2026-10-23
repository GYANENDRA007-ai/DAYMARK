# KAAM Test Report — v48 (2026-10-21)

| Verification suite | Result |
|---|---:|
| Static/source and XML checks | 159 / 159 passed |
| Bundled audio assets | 4 / 4 passed |
| Timer/scheduling/repeat/fixed-time/window/snooze assertions | 11,588 passed |
| Notification-copy assertions | 3,002 passed |
| Pure JVM unit tests | 47 passed, 0 failed |
| ZIP integrity | Passed after packaging |

## Changes covered
- Task snapshots now use synchronous `commit()` for durable ordering before alarm operations; a static regression check guards the implementation.
- Completing or deleting an item in the app now dismisses an already-delivered notification. A static regression check covers both flows.

## Android build status
`./gradlew :app:assembleDebug --stacktrace --no-daemon --console=plain` was attempted in this environment and failed before Gradle startup with `java.net.UnknownHostException: downloads.gradle.org`. Android SDK Platform 37 is also unavailable here. This is an environment blocker; it is not evidence of a successful Android compile. No APK/device test is claimed.
