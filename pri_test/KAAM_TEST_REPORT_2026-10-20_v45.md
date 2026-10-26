# KAAM Test Report — v45

Date: 2026-10-20

## Changes in v45

- Optimized recovered-task duplicate-ID filtering from repeated linear scans to a `HashSet<Long>` membership check.
- Preserves deterministic recovery behavior: the first valid task record with a positive ID wins; later duplicate IDs are skipped.
- This keeps duplicate detection approximately O(n) rather than O(n²) for large/corrupt task payloads, without changing the persisted JSON format or scheduling behavior.

## Offline verification

- Static source checks: 156/156 passed.
- Bundled WAV assets: 4/4 valid.
- Timer/scheduling/recurrence/window/snooze smoke assertions: 11,588 passed.
- Notification-copy invariant assertions: 3,002 passed.
- Pure JVM test methods: 45 passed, 0 failed.
- Archive integrity: verified after packaging.

## Important limitation

This offline QA does not compile the Android/Compose application and does not produce or validate an APK. A fresh v45 Gradle build attempt was also blocked before Gradle startup: `java.net.UnknownHostException: downloads.gradle.org`. No Android SDK was configured (`ANDROID_HOME` and `ANDROID_SDK_ROOT` unset), so emulator/device acceptance remains outstanding.
