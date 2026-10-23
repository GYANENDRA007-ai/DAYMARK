# KAAM project handoff — v38 (19 October 2026)

## Archive
- `KAAM_Updated_Project_2026-10-19_v38.zip`
- Archive root: `ReminderApp/`
- Temporary consumer-facing label: `Reminders`; Home wordmark: `YOUR DAY`.

## v38 changes
- Added schedule recovery for Android exact-alarm permission grant, wall-clock changes, and time-zone changes, retaining reboot and package-replacement recovery.
- Exact-alarm permission recovery checks `canScheduleExactAlarms()` before rescheduling.
- Wall-clock/time-zone recovery reschedules clock-based tasks while leaving `whenType == TIMER` out of this particular path.
- Added regression checks for the new manifest and receiver behavior.
- Added per-task error logging so one malformed/unsupported schedule cannot block recovery of subsequent active tasks.

## Offline QA results
- Static checks: **150/150 passed**.
- Sound assets: **4/4 valid**.
- Timer/scheduling/recurrence/window/snooze assertions: **11,588 passed**.
- Notification-copy invariants: **3,002 passed**.
- Pure JVM test methods: **45 passed, 0 failed**.
- Full offline QA orchestration: passed.
- Archive integrity: verified; SHA-256 recorded below.

## Build honesty
Android/Compose compilation, APK generation, emulator/physical-device behavior, and TalkBack acceptance remain unverified. No APK is included or claimed. This environment lacks a configured Android SDK and dependency-host DNS has failed. Offline checks do not replace an actual Android build/device pass.

## Architecture gap
The app remains a single-activity Compose app using SharedPreferences JSON. The intended Clean Architecture/MVVM + Room/Repository migration remains incomplete and must not be represented as finished.

## Delivery priorities
1. Obtain an Android build environment and resolve compile/runtime issues without transferring environment setup to the user.
2. Build an APK and run the device checklist for permission grant/denial, fixed-time alarms, intervals, timers, snooze, reboot, time-zone change, notification actions/deep links, sound channels, TalkBack, and font scaling.
3. Continue regression-safe reliability work while preserving legacy task JSON.

Target: quality-verified handoff by 24 October if possible; 26 October remains the hard fallback.
