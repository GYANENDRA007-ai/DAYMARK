# KAAM verification report — v25 (17 October 2026)

## Implementation changes
- Added optional same-day start/end windows for interval reminders, persisted as separate `repeatWindowStart` / `repeatWindowEnd` fields so existing `repeatValue` (hours) and `repeatLimit` retain their meanings.
- Windowed interval scheduling now targets the first window start when the window is still ahead, uses slots anchored to that start while inside the window, and resumes at the next day's start after the window closes.
- Added 1-, 2-, 3-, and 4-hour interval choices to support more flexible window behavior.
- Editor validates both times, malformed times, identical times, and overnight/reversed windows. Leaving both blank preserves all-day interval behavior.
- Recurrence summaries show the selected window and finite limit. Settings explains window semantics. Old saved tasks load with empty window fields.
- Editing the window is treated as a schedule change and resets regular occurrence count; title/details-only changes keep the existing count.

## Checks run
- Static structural checks: **129/129 passed**.
- Standalone Kotlin/JVM timing, recurrence, and window smoke runner: **11,572 assertions passed**, including 11,520 exhaustive minute/frequency checks over a full day and all four supported interval values.
- Standalone notification-copy smoke runner: **3,002 assertions passed**.
- Pure Kotlin JVM test methods: **34 passed, 0 failed** using a temporary lightweight JUnit-compatible runner. This is not the real Gradle/JUnit engine.
- Bundled WAV assets: **4/4 passed** the standard-library validator.
- ZIP integrity passed.

## Not verified
- Full Android/Compose compilation, real Gradle/JUnit execution, APK generation/install, actual Android UI behavior, notification delivery, reboot recovery, and on-device time-window behavior.
- Gradle wrapper distribution download remains blocked by DNS/network access to `services.gradle.org`. No APK or device-level success is claimed.

## Release status
This is a development source checkpoint, not a release-verified APK. Compile in Android Studio/Gradle and run the updated device checklist before relying on reminders for important events.
