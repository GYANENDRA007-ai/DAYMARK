# KAAM v29 QA report — 19 October 2026

## Changes in v29
- Reworked `tools/verify_kaam_offline.py` to compile both pure Kotlin helpers, both smoke runners, the JVM test classes, and the test runner in **one bounded `kotlinc` invocation** instead of launching the compiler separately for each suite.
- Added per-command timeouts and a clear failure message so a stalled compiler cannot leave the QA run apparently frozen forever.
- Added a static regression check that protects the one-compile QA flow.
- Removed Python bytecode cache files from the deliverable.
- No production app behavior was changed in this QA-maintenance checkpoint.

## Checks run in this session
- Static source / XML checks: **138/138 passed**.
- Bundled WAV assets: **4/4 valid**.
- Timer, recurrence, fixed-time, window, and snooze smoke checks: **11,577 assertions passed**.
- Notification-copy smoke checks: **3,002 assertions passed**.
- Pure JVM unit test methods: **36 passed, 0 failed**, using a temporary JUnit-compatible shim. This is not the Gradle/JUnit engine.
- Offline QA runner: **passed end to end** after the runner change.
- ZIP integrity: checked after packaging.

## Android build status — still unverified
This environment has no Android SDK Platform 37 and cannot resolve the Gradle distribution host, so `:app:assembleDebug` cannot currently run here. No APK, Android/Compose compilation, Gradle/JUnit-engine result, or device acceptance result is claimed. This is the remaining release-critical blocker; source/static checks are not a substitute for a real Android build.

## Known product / architecture gaps
- The app still uses a single large Compose activity and SharedPreferences JSON persistence; the planned Clean Architecture / MVVM / repository / Room migration is not complete.
- Runtime acceptance remains outstanding for notification delivery, exact-alarm permission changes, notification sounds/vibration, Done/Snooze actions, reboot restoration, recurrence limits, and small-screen/accessibility behavior.
