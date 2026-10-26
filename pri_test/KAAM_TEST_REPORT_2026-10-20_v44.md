# KAAM QA report — v44 (20 October 2026)

## Change under test

Made the deferred exact-alarm retry queue concurrency-safe. Previously, foreground recovery could read the queued IDs, then remove the preference key while a receiver concurrently added another ID; that new retry request could be lost. A shared lock now protects updates and the foreground path atomically claims/removes the current batch before retrying each task. Failed retries can safely add themselves back after the batch is claimed.

The collision-resistant PendingIntent URI identities from v43 remain in place.

## Offline verification

- Static source/resource checks: **156/156 passed**.
- Bundled WAV assets: **4/4 valid**.
- Timing/scheduling/recurrence/window/snooze smoke assertions: **11,588 passed**.
- Notification-copy invariants: **3,002 passed**.
- Pure JVM test methods: **45 passed, 0 failed**.
- Complete offline QA runner: **passed**.

## Release limitations

These checks do not compile the Android/Compose application. Android SDK configuration, successful Gradle build, APK generation, emulator/device execution, and device acceptance remain outstanding. No APK or device success is claimed.
