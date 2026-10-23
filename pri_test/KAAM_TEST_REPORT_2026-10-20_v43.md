# KAAM QA report — v43 (20 October 2026)

## Change under test

Hardened Android PendingIntent identity. Reminder alarms, notification Done/Snooze actions, and notification content/open intents now include a URI identity containing the full task ID (and action for action intents), instead of relying only on a 32-bit hash-derived request code. This prevents hash collisions from causing two different tasks/actions to share a PendingIntent. The offline delimiter checker was also fixed to strip strings before `//` comments so URI literals are handled correctly.

## Offline verification

- Static source/resource checks: **155/155 passed**.
- Bundled WAV assets: **4/4 valid**.
- Timing/scheduling/recurrence/window/snooze smoke assertions: **11,588 passed**.
- Notification-copy invariants: **3,002 passed**.
- Pure JVM test methods: **45 passed, 0 failed**.
- Complete offline QA runner: **passed**.

## Release limitations

These checks do not compile the Android/Compose application. Android SDK configuration, successful Gradle build, APK generation, emulator/device execution, and device acceptance remain outstanding. No APK or device success is claimed.
