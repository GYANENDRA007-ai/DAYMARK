# Building KAAM on Windows

## One-command debug build

1. Extract the entire `ReminderApp` folder.
2. Open it once in Android Studio and install **Android SDK Platform 37** and **Android SDK Build-Tools 36.0.0** through **Tools → SDK Manager**. Keep the Android Studio bundled JDK (17 or newer; current Android Studio JBR is suitable).
3. Open PowerShell in the `ReminderApp` folder and run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\tools\build_debug_windows.ps1
   ```

The script selects Android Studio's bundled JBR when found, checks the SDK, runs `:app:assembleDebug`, and saves the complete console output under `build-diagnostics\assembleDebug-<timestamp>.log`. On success the APK is `app\build\outputs\apk\debug\app-debug.apk`.

The wrapper points directly to the official `downloads.gradle.org` distribution host instead of the `services.gradle.org` redirect endpoint. The Windows build helper automatically falls back to `services.gradle.org` for a single build attempt if the direct host fails DNS but the service host resolves, then restores the original wrapper file. This may help networks where only one host is blocked, but it does not bypass a generally unavailable internet/DNS connection. The first build still requires internet access for the Gradle distribution and Maven dependencies. If it fails, keep and share the generated log rather than only the last error line.

## Why the build configuration changed in v26

The previous checkpoint forced Gradle's own daemon to use JDK 25 through `gradle/gradle-daemon-jvm.properties`, which can trigger an unnecessary JDK download on a new machine. This app does not use Java 25-only APIs. The v26 checkpoint removes that forced daemon toolchain and the now-unneeded Foojay resolver plugin, so Gradle uses the configured JDK (Android Studio's JDK is preferred). This does not remove the need for the Gradle distribution, Android SDK, or dependencies.

## Run offline regression checks

Before a full Android build, you can run the source-level and pure timing/notification checks without downloading Gradle:

```powershell
python .\tools\verify_kaam_offline.py
```

This runs static/XML checks and validates the bundled sound files. If `kotlinc`, `java`, and `javac` are available on `PATH`, it also compiles and runs the standalone Kotlin timing/scheduling and notification-copy smoke suites, plus the actual pure JVM test methods (using a temporary JUnit-compatible shim). It is a fast regression screen, **not** a substitute for `assembleDebug`, Android/JUnit compilation, or device acceptance.
