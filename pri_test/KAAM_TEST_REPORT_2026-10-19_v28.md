# KAAM v28 offline QA and build report — 19 October 2026

## v28 change
- Added `tools/verify_kaam_offline.py`, a one-command offline regression runner for static source/XML checks, bundled sound validation, pure Kotlin timing/scheduling + notification-copy smoke suites, and the actual pure JVM test methods through a temporary JUnit-compatible shim.
- Documented the runner in `BUILDING_KAAM_WINDOWS.md` and added a static regression check to keep the runner wired into the project.
- No production app behavior was changed in v28; this checkpoint improves repeatable QA while the full Android build is blocked by the current environment's network/SDK limitations.

## Checks run in this session
- Static structural checks: **137/137 passed**.
- Bundled WAV validation: **4/4 passed**.
- Timing/recurrence/window/snooze smoke checks: **11,577 assertions passed**, including 11,520 exhaustive minute/frequency checks.
- Notification-copy smoke checks: **3,002 assertions passed**.
- Pure JVM unit test methods: **36 passed, 0 failed** using a temporary JUnit-compatible shim (not the Gradle/JUnit engine).
- ZIP/archive integrity: verified after packaging.

## Full Android build attempt
Command attempted:

`./gradlew :app:assembleDebug --stacktrace --no-daemon --console=plain`

It fails before Gradle starts because this environment cannot resolve `downloads.gradle.org` (`java.net.UnknownHostException`). No Android SDK Platform installation is available in this environment. Consequently, no APK, Android/Compose compilation, Gradle/JUnit test-engine result, or device-test result is claimed.

## Exact next action on Windows
1. Open the project in Android Studio and install Android SDK Platform 37 and Build-Tools 36.0.0; use Android Studio's bundled JDK 17+.
2. Run `python .\tools\verify_kaam_offline.py` for quick regression checks.
3. Run `powershell -ExecutionPolicy Bypass -File .\tools\build_debug_windows.ps1` from the `ReminderApp` folder.
4. If it fails, share the timestamped `build-diagnostics\assembleDebug-*.log`; fix the first actual Gradle/compiler error, then install the APK and run `KAAM_DEVICE_ACCEPTANCE_CHECKLIST_v27.md`.

## Important limits still open
- Full Android/Compose compilation and APK generation.
- Real-device acceptance: exact alarm access, notification permission, sound/vibration channels, Done/Snooze actions, reboot persistence, recurrence limits, and history.
- Larger architecture target (MVVM/Room/repository) remains a future refactor; current app persists JSON in SharedPreferences.
