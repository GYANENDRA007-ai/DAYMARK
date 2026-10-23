# KAAM verification report — v24 (17 October 2026)

## Changes
- Hardened persisted repeat counts against negative/corrupt values.
- Added overflow-safe regular occurrence counting so `Int.MAX_VALUE + 1` cannot wrap to a negative count and accidentally bypass finite recurrence limits.
- Added regression tests for malformed negative and maximum integer counts, plus direct pure-function coverage proving snoozes do not consume a regular occurrence.
- Corrected the recurrence-limit editor so it shows zero sent when the user has changed the schedule, matching the reset that occurs on save.
- Fixed an edit-path snooze loss: editing an existing item now preserves its deliberate persisted snooze while replacing the regular alarm.
- Separated Sounds and Settings destinations. Settings now explains interval/daily recurrence, finite occurrence limits, snooze behavior, and the fact that an item remains active after its configured reminder count is reached.
- Rewrote project README/handoff to reflect current capabilities and clearly state architecture/runtime limitations.

## Checks run
- Static structural checks: **126/126 passed**.
- Compiled and invoked the pure Kotlin JVM test source methods using a temporary lightweight JUnit-compatible runner: **30 passed, 0 failed**. This is useful host-side execution of the test methods, but it is not a substitute for the actual Gradle/JUnit engine or Android compilation.
- Standalone Kotlin/JVM timing/recurrence smoke runner: **41 assertions passed**.
- Standalone notification-copy smoke runner: **3,002 assertions passed** across 1,000 pool-membership selections, 1,000 no-immediate-repeat selections, one-item fallback, and empty-pool rejection.
- Gradle/JUnit attempted with `./gradlew --offline testDebugUnitTest`, but the wrapper attempted to fetch Gradle 9.6.0 and failed with `UnknownHostException: services.gradle.org`.

## Not verified
- Full Android/Compose compilation, JUnit execution, APK generation/install, notification behavior on a device, exact-alarm permissions, reboot restoration, and UI visual/runtime behavior.
- The static checker checks source structure and delimiter balance; it is not a substitute for compilation.

## Honest release status
This is a development source checkpoint, not a release-verified APK. Run Android Studio/Gradle sync and real-device acceptance tests when the Gradle distribution and Android SDK are available.
