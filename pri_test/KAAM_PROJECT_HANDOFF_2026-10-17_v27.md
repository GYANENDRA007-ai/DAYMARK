# KAAM project handoff — v27 (17 October 2026)

## Latest checkpoint
- Archive: `KAAM_Updated_Project_2026-10-17_v27.zip`
- Source root: `ReminderApp/`
- Main app source: `app/src/main/java/com/example/reminderapp/MainActivity.kt`
- Pure timing/scheduling helpers: `app/src/main/java/com/example/reminderapp/ReminderTiming.kt`
- Build helper: `tools/build_debug_windows.ps1`
- Build guide: `BUILDING_KAAM_WINDOWS.md`
- Test report: `KAAM_TEST_REPORT_2026-10-17_v27.md`
- Device checklist: `KAAM_DEVICE_ACCEPTANCE_CHECKLIST_v27.md`

## v27 changes
- Fixed a real scheduling-order bug: persisted snooze restoration now occurs before the no-regular-reminder (`ANYTIME` + `NONE`) early return. This preserves a deliberate one-off snooze if a task's schedule has changed and is later rescheduled after edit/reboot.
- Added `shouldSkipNoReminderSchedule` pure helper and regression tests.
- Pointed the Gradle wrapper directly at `downloads.gradle.org` while retaining the pinned SHA-256, to avoid dependence on the `services.gradle.org` redirect endpoint when a network blocks that host specifically.

## Validation
- Static checks: **136/136 passed**.
- Timing/recurrence/window/snooze smoke checks: **11,577 assertions passed**.
- Notification-copy smoke checks: **3,002 assertions passed**.
- Lightweight JVM test runner: **35 test methods passed, 0 failed** (not the Gradle/JUnit engine).
- WAV validation: **4/4 passed**; ZIP integrity passed.
- Actual Android build remains blocked before Gradle starts because DNS cannot resolve `downloads.gradle.org` in this environment. Android SDK is also unavailable here. No APK, Android compilation, or device testing is claimed.

## Exact next action
On Windows with internet access, install Android SDK Platform 37 and Build-Tools 36.0.0, then run:

`powershell -ExecutionPolicy Bypass -File .\tools\build_debug_windows.ps1`

Inspect the timestamped log in `build-diagnostics/`, fix the first actual compiler/build error, then install the APK and execute the device checklist.

## Requirements to preserve
- KAAM is the internal codename; consumer-facing label remains `Reminders`; Home wordmark `YOUR DAY`.
- Dark Tactile Premium + Selective Liquid Glass; deep charcoal, restrained teal, premium typography, subtle hanging wind-chime motif.
- Task, Timer, Alarm, Alert remain distinct; notifications are the delivery mechanism, not a mode.
- Home is the command center; retain Active / Completed / History and status history.
- Flexible schedules; optional same-day interval windows; limits Unlimited/3/5/10; snoozes don't consume regular recurrence counts; Done/Snooze 10m notification actions.
- Keep subject-aware creative notification copy with occasional natural Hinglish/slang, distinct sound identities, and no false claims about build/device verification.

## Known gaps
- Full Android/Compose compilation, APK generation, Gradle/JUnit engine, and device acceptance remain outstanding.
- Current app is still a single-activity Compose app using SharedPreferences JSON, not the full Clean Architecture/MVVM + Room/Repository target.
- Finer intervals, due/end dates, overnight windows, and real-device testing remain future work.
