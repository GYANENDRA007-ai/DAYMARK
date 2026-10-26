# KAAM QA report — v42 (20 October 2026)

## Change under test

Hardened task-storage recovery against invalid and duplicate task IDs:
- Records with IDs `<= 0` are skipped during JSON recovery because notification receivers intentionally ignore those IDs.
- Duplicate IDs are skipped deterministically after the first valid record. Task IDs identify Android `PendingIntent`s, so duplicate IDs could cause separate tasks to overwrite or cancel one another's alarms.
- Added a static regression check for both behaviors.

## Offline verification

- Static source/resource checks: **154/154 passed**.
- Bundled WAV assets: **4/4 valid**.
- Timing/scheduling/recurrence/window/snooze smoke assertions: **11,588 passed**.
- Notification-copy invariants: **3,002 passed**.
- Pure JVM test methods: **45 passed, 0 failed**.
- Complete offline QA runner: **passed**.

## Release limitations

These checks do not compile the Android/Compose application. Android SDK configuration, a successful Gradle build, APK generation, emulator/device execution, and the device acceptance checklist remain outstanding. No APK or device success is claimed.
