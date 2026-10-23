# KAAM v27 test and build report — 17 October 2026

## Changes in this checkpoint
- Fixed a scheduling-order bug: `scheduleReminder` previously returned early for an `ANYTIME` + `NONE` item before restoring a persisted snooze. This could lose the deliberate one-off snooze after editing/reboot rescheduling if the item's regular schedule had been changed away.
- Extracted the no-regular-reminder gate into `shouldSkipNoReminderSchedule`, which treats a positive persisted snooze as a valid one-off trigger and preserves normal `ANYTIME` + `NONE` behavior when no snooze exists.
- Added a regression test covering no-snooze, positive-snooze, interval, clock-based, and invalid-negative deadline cases.
- Changed the Gradle wrapper distribution URL to the official direct `downloads.gradle.org` host, while preserving the pinned SHA-256. The Windows build helper can temporarily fall back to `services.gradle.org` if DNS resolves only that host, and restores the original wrapper file afterward.

## Checks run
- Static structural checks: **136/136 passed** (`python tools/verify_kaam_static.py`).
- Standalone Kotlin timing/recurrence/window/snooze runner: **11,577 assertions passed**, including 11,520 exhaustive minute/frequency window checks.
- Standalone notification-copy runner: **3,002 assertions passed**.
- Pure Kotlin JVM test methods: **35 passed, 0 failed** using a temporary lightweight JUnit-compatible runner for the timing and notification-copy test classes. This is not the Gradle/JUnit engine.
- Bundled WAV validation: **4/4 passed**.
- ZIP integrity: verified after packaging.

## Full Android build attempt
Command attempted:

`./gradlew :app:assembleDebug --stacktrace --no-daemon --console=plain`

The wrapper now tries the direct official distribution host, but this environment cannot resolve `downloads.gradle.org` either (`java.net.UnknownHostException`). The build still fails before Gradle starts. There is no configured Android SDK in this environment. Therefore no Android compilation, APK, or device-test result is claimed.

## Build compatibility note
The project versions match the official Android Gradle Plugin 9.4 compatibility table: Gradle 9.6.0, SDK Build-Tools 36.0.0, JDK 17, and API 37 support. The direct distribution URL is only a network-path improvement; it cannot fix a general DNS/network outage. The Windows build helper has not been executed on Windows.

## Next action
Run `powershell -ExecutionPolicy Bypass -File .\tools\build_debug_windows.ps1` on the Windows development machine with internet access and Android SDK Platform 37 / Build-Tools 36.0.0 installed. If DNS for `downloads.gradle.org` also fails there, restore the standard `services.gradle.org` wrapper URL or download the same Gradle 9.6.0 binary from the official Gradle distribution page on a working network, verify its checksum, and place it in the Gradle wrapper cache. Then fix the first real Gradle/compiler error and execute `KAAM_DEVICE_ACCEPTANCE_CHECKLIST_v27.md`.
