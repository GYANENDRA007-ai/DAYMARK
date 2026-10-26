# KAAM project handoff — v26 (17 October 2026)

## Latest checkpoint
- Archive: `KAAM_Updated_Project_2026-10-17_v26.zip`
- Source root: `ReminderApp/`
- Main app source: `app/src/main/java/com/example/reminderapp/MainActivity.kt`
- Pure timing helpers: `app/src/main/java/com/example/reminderapp/ReminderTiming.kt`
- Build helper: `tools/build_debug_windows.ps1`
- Build guide: `BUILDING_KAAM_WINDOWS.md`
- Test report: `KAAM_TEST_REPORT_2026-10-17_v26.md`

## Requirements to preserve
- KAAM is an internal codename. Public app label remains `Reminders`; Home wordmark remains `YOUR DAY` until naming is decided.
- Dark Tactile Premium + Selective Liquid Glass; deep charcoal, restrained teal, premium typography, subtle hanging wind-chime motif. Avoid cartoonish/full-app claymorphism.
- Task, Timer, Alarm, and Alert remain distinct; notifications are delivery mechanism, not a separate mode.
- Home is a command center; preserve Active / Completed / History and old status/history.
- Flexible schedules, subject-aware notification copy with occasional natural Hinglish/slang, distinct sound identities, Done and Snooze 10m actions.
- Execute work directly; don't ask the user to repeat specs or claim unverified builds/device tests.

## v26 changes
- Removed the generated Gradle daemon criteria forcing JDK 25. AGP 9.4's documented minimum JDK is 17; current Android Studio's configured JDK should be used rather than forcing a separate daemon-JDK download.
- Removed the Foojay resolver convention plugin because the project declares no Java toolchain and no longer requires automatic daemon JDK provisioning.
- Added a Windows PowerShell build script that prefers Android Studio's bundled JBR, checks Java/SDK prerequisites, invokes `:app:assembleDebug`, and captures full output under `build-diagnostics/`.
- Added a Windows build guide with SDK versions, command, and APK path.
- Existing v25 recurrence-window/limit/snooze features are unchanged.

## Validation
- Static checks: **132/132 passed**.
- Timing/recurrence/window smoke checks: **11,572 assertions passed**.
- Notification-copy smoke checks: **3,002 assertions passed**.
- Lightweight JVM test runner: **35 test methods passed, 0 failed** (not the Gradle/JUnit engine).
- WAV validation: **4/4 passed**; ZIP integrity passed.
- Full Gradle build attempt remains blocked before Gradle starts because DNS resolution of `services.gradle.org` fails in this environment. No APK/Android compilation/device test is claimed. The PowerShell helper has not been run on Windows.

## Exact next action
On the user's Windows machine, open the project in Android Studio, ensure Android SDK Platform 37 and Build-Tools 36.0.0 are installed, then run:

`powershell -ExecutionPolicy Bypass -File .\tools\build_debug_windows.ps1`

Use the resulting `build-diagnostics/assembleDebug-<timestamp>.log` to fix the first actual compiler/build error. After successful build, install `app/build/outputs/apk/debug/app-debug.apk` and execute `KAAM_DEVICE_ACCEPTANCE_CHECKLIST_v26.md`.

## Known product/architecture gaps
- Finer intervals (15/30 minutes), due/end-date modeling, overnight windows, and real-device acceptance remain outstanding.
- Current implementation is still a single-activity Compose app using SharedPreferences JSON, not the full target Clean Architecture/MVVM + Room/Repository architecture.
