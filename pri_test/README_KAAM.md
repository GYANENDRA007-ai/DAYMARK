# Reminders — Android source project

**Internal codename:** KAAM  
**Working product label:** Reminders  
**Home wordmark:** YOUR DAY

A local-first Android reminder and accountability app built with Kotlin and Jetpack Compose Material 3. The visual direction is **Dark Tactile Premium / Selective Liquid Glass**: restrained depth, deep charcoal surfaces, a muted teal accent, and distinct Task, Timer, Alarm, and Alert flows.

## Product capabilities

- **Four intent types:** Task, Timer, Alarm, and Alert, with separate creation and scheduling choices.
- **Task lifecycle:** Active, Completed, and History views; completion timestamps; progress and period reports.
- **Deadlines:** optional date-only deadline, clear/change support, overdue labels, and overdue-first ordering. Deadlines are metadata and do not silently alter notification schedules.
- **Flexible recurrence:** one-off, daily, 15-minute/30-minute/hourly intervals, optional same-day windows, and finite or unlimited occurrence counts.
- **Countdown consistency:** timer start time persists across process/device restart; text-only edits preserve the countdown, while duration edits restart it.
- **Notification actions:** task-aware notification copy, Done and Snooze 10m actions, per-kind sound profiles, bundled original tones, and persisted snooze recovery.
- **Resilience:** JSON task storage with a last-known-good backup, tolerant loading of individual malformed records, deferred exact-alarm retries, notification deep links, and boot/package-replacement, clock-change, time-zone-change, and exact-alarm-permission-grant schedule recovery.
- **Settings:** reminder voice, per-kind sounds and previews, notification/exact-alarm settings shortcuts, and plain-language schedule behavior.
- **Accessibility polish (v34):** primary actions and choices expose Compose semantics; back/delete/complete actions have meaningful labels; key actions have minimum 48dp hit targets.

## Verification

Run the offline QA suite from this directory:

```powershell
python tools/verify_kaam_offline.py
```

It runs static source/resource checks, WAV validation, pure Kotlin timing/recurrence smoke tests, notification-copy invariants, and JVM test-source methods through a lightweight local runner. It does **not** compile the Android/Compose application and is not a substitute for Gradle/JUnit or device acceptance.

The v40 checkpoint includes:
- Static structural and accessibility regression checks.
- Timing, interval, window, timer-edit, deadline and snooze regression checks.
- Notification-copy pool invariants.
- Validation of all four bundled WAV files.
- Exact-alarm foreground retry fault isolation: one failing reminder is requeued without blocking other deferred reminders.
- System recovery receiver is non-exported to prevent spoofed time-change/package-replacement broadcasts from resetting schedules.

See `KAAM_TEST_REPORT_2026-10-19_v38.md` for exact observed results.

## Android build and device testing

The Android build/APK and device-level behavior are **not currently verified in this environment**. The environment has no configured Android SDK and cannot resolve the official Gradle/Google Maven download hosts. Do not interpret offline QA as proof of an installable APK.

When a build host is available, the provided Windows build helper is:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build_debug_windows.ps1
```

It checks prerequisites, runs `:app:assembleDebug`, and saves diagnostics. The expected APK path is `app\build\outputs\apk\debug\app-debug.apk` if the build succeeds.

## Architecture and known limitations

This checkpoint is still a **single-activity Compose app using SharedPreferences JSON**. It has not yet been migrated to the originally envisioned Clean Architecture/MVVM + Room/Repository design. Android compilation, Compose runtime, Gradle/JUnit, emulator/physical-device acceptance, TalkBack traversal, OEM battery restrictions, exact-alarm permission flows, and actual notification sound/channel behavior still require verification in a functioning Android build environment.

## Official Android guidance used for v34/v38 review

- [Schedule alarms — Android Developers](https://developer.android.com/develop/background-work/services/alarms): exact alarms are for genuinely time-critical user-facing behavior; check `canScheduleExactAlarms()` and recover after permission changes.
- [Compose accessibility — Android Developers](https://developer.android.com/guide/topics/ui/accessibility/composables): give interactive components meaningful semantics and labels.
- [Compose API defaults — Android Developers](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): interactive targets should be at least 48dp.

These references informed targeted accessibility and scheduling-recovery passes; they do not imply runtime accessibility or Android alarm behavior has been tested.
