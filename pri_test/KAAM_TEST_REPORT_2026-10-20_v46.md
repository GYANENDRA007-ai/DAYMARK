# KAAM Offline Test Report — v46

## Change under test

Hardened pure timer arithmetic against malformed timestamps and `Long` overflow:

- `timerDeadlineAt` rejects negative start timestamps and refuses a deadline addition that would overflow `Long.MAX_VALUE`.
- `timerRemainingMinutes` now rounds up using quotient/remainder rather than `remainingMillis + 59_999`, avoiding overflow when a valid deadline is near `Long.MAX_VALUE`.
- Added regression tests for negative/overflowing starts, the largest valid deadline, and remaining-minute ceiling near the numeric boundary.

## Results

- Static source checks: **157/157 passed**.
- Bundled sound assets: **4/4 valid**.
- Scheduling/timing/repeat/window/snooze assertions: **11,588 passed**.
- Notification-copy invariant assertions: **3,002 passed**.
- Pure JVM unit tests: **47 passed, 0 failed**.
- Archive integrity: verified after packaging.

## Limitation

This is offline/source-level and pure-JVM verification. It does not compile the Android/Compose app or produce an APK. The prior Android build attempt could not download Gradle because `downloads.gradle.org` did not resolve, and no Android SDK was configured in the build environment. Android build and device acceptance remain outstanding.
