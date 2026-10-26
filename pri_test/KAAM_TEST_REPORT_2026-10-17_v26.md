# KAAM v26 test and build report — 17 October 2026

## Changes in this checkpoint
- Removed `gradle/gradle-daemon-jvm.properties`, which forced the Gradle daemon to use JDK 25 and could trigger a needless JDK download on a clean machine. The project does not use Java 25-only APIs.
- Removed the Foojay resolver convention plugin because the project does not declare a Java toolchain and no longer needs remote daemon-JDK provisioning.
- Added `tools/build_debug_windows.ps1`: checks Java, prefers Android Studio's bundled JBR when found, locates/checks the Android SDK, runs `:app:assembleDebug`, and saves the complete log to `build-diagnostics`.
- Added `BUILDING_KAAM_WINDOWS.md` with the exact build command, SDK prerequisites, APK output path, and diagnostics workflow.

## Checks run
- Static structural checks: **132/132 passed** (`python tools/verify_kaam_static.py`).
- Standalone Kotlin timing/recurrence/window runner: **11,572 assertions passed**.
- Standalone notification-copy runner: **3,002 assertions passed**.
- Pure Kotlin JVM test methods: **35 passed, 0 failed** using a temporary lightweight JUnit-compatible runner. This is not the Gradle/JUnit engine.
- Bundled WAV validation: **4/4 passed**.
- ZIP integrity: passed after packaging.

## Full Android build attempt
Command attempted in the build environment:

`./gradlew :app:assembleDebug --stacktrace --no-daemon --console=plain`

Result: **blocked before Gradle starts**. The wrapper cannot resolve `services.gradle.org` and throws `java.net.UnknownHostException`. A direct distribution download attempt also failed. This environment has no configured Android SDK and no cached Gradle distribution, so no honest Android compilation or APK result is available.

## Important distinction
The v26 toolchain cleanup removes an unnecessary JDK 25 provisioning dependency, but it cannot bypass the separate need to download Gradle 9.6.0, resolve Android Gradle Plugin/dependencies, and install Android SDK Platform 37 / Build-Tools 36.0.0. The Windows build helper has been inspected and its content statically checked, but has **not** been executed on Windows. APK generation, Android/Compose compilation, and device tests remain unverified.

## Next action
Extract this checkpoint on the Windows development machine, open it in Android Studio, install the SDK components if missing, and run `powershell -ExecutionPolicy Bypass -File .\tools\build_debug_windows.ps1`. Use the captured log to fix the first real build error. Then install the APK and execute `KAAM_DEVICE_ACCEPTANCE_CHECKLIST_v26.md`.
