# KAAM Test Report — v47 (2026-10-21)

## Offline verification
| Suite | Result |
|---|---:|
| Static/source and XML checks | 158 / 158 passed |
| Bundled audio validation | 4 / 4 passed |
| Timer, scheduling, recurrence, fixed-time, window and snooze assertions | 11,588 passed |
| Notification-copy invariant assertions | 3,002 passed |
| Pure JVM unit tests | 47 passed, 0 failed |
| ZIP integrity | Passed after packaging |

## Change under test
Task snapshots and their recovery mirror now use `commit()` instead of `apply()` before subsequent alarm scheduling/cancellation can rely on that state. A static regression check verifies the durability choice and failure logging.

## Android build attempt
Command: `./gradlew :app:assembleDebug --stacktrace --no-daemon --console=plain`

Result: failed before Gradle startup. `downloads.gradle.org` did not resolve (`java.net.UnknownHostException`). No Android SDK Platform 37 is configured in this execution environment. This is an environment/network blocker, not a confirmed Kotlin/Android compile error.

## Important limitation
The full Android app has not yet been compiled; an installable APK and real-device acceptance are still outstanding. Do not describe v47 as release-ready.
