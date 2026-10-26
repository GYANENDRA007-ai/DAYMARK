# KAAM project handoff — v28 (19 October 2026)

## Latest checkpoint
- Archive: `KAAM_Updated_Project_2026-10-19_v28.zip`
- Main app source: `app/src/main/java/com/example/reminderapp/MainActivity.kt`
- Pure timing/scheduling helpers: `app/src/main/java/com/example/reminderapp/ReminderTiming.kt`
- New offline QA runner: `tools/verify_kaam_offline.py`
- Windows build helper: `tools/build_debug_windows.ps1`
- Build guide: `BUILDING_KAAM_WINDOWS.md`
- Test report: `KAAM_TEST_REPORT_2026-10-19_v28.md`

## v28 changes
- Added a one-command offline QA runner: `python tools/verify_kaam_offline.py`. It runs the actual pure JVM test methods (36 pass) through a temporary compatibility shim when JDK/Kotlin tools are available; this is not the Gradle/JUnit engine.
- Runner invokes static/XML checks and sound asset validation; if `kotlinc` + `java` are on PATH, it compiles/runs the pure Kotlin scheduling and notification-copy smoke suites.
- Added documentation and a static check so the QA runner remains part of the project.
- No production app behavior changed in this checkpoint.

## Verified
- Static checks: **137/137 passed**.
- WAV assets: **4/4 valid**.
- Timing/scheduling/window/snooze smoke suite: **11,577 assertions passed**.
- Notification-copy smoke suite: **3,002 assertions passed**.
- Pure JVM unit test methods: **36 passed, 0 failed** using a temporary JUnit-compatible shim (not the Gradle/JUnit engine).
- ZIP integrity: verified after packaging.

## Blocking issue
The full Android build still cannot start in this environment because `downloads.gradle.org` fails DNS resolution (`UnknownHostException`) and Android SDK Platform 37 is unavailable. No APK or device testing is claimed.

## Next action
On Windows, install Android SDK Platform 37 + Build-Tools 36.0.0 in Android Studio, run `python .\tools\verify_kaam_offline.py`, then run `powershell -ExecutionPolicy Bypass -File .\tools\build_debug_windows.ps1`. Use the generated timestamped build log to address the first real compiler/build error. After a successful APK build, follow `KAAM_DEVICE_ACCEPTANCE_CHECKLIST_v27.md`.

## Requirements to preserve
- Internal codename KAAM; consumer label `Reminders`; Home wordmark `YOUR DAY`.
- Dark Tactile Premium + Selective Liquid Glass; deep charcoal, restrained teal, premium typography, subtle hanging wind-chime motif.
- Task, Timer, Alarm, Alert remain distinct; notifications are delivery, not a mode.
- Home as command center; retain Active / Completed / History and status history.
- Flexible schedules, optional same-day interval windows, limits Unlimited/3/5/10, snoozes do not consume recurrence counts, notification Done + Snooze 10m actions.
- Subject-aware notification copy with occasional natural Hinglish/slang and distinct sound identities.
- Do not claim APK compilation or device verification until actually completed.
