# KAAM v50 — Cloud Build Path

## Purpose
The project now includes a GitHub Actions workflow that can compile the Android app on a clean hosted Linux runner, avoiding dependence on this ChatGPT execution container's unavailable network and missing Android SDK.

## What the workflow does
- Runs on manual dispatch, pushes to `main`/`master`, and pull requests.
- Uses JDK 21 and sets up Android SDK command-line tooling.
- Installs Android SDK Platform 37 and Build Tools 36.0.0.
- Runs offline source, audio, scheduling and notification regression checks first.
- Runs `./gradlew :app:assembleDebug`.
- If the build succeeds, publishes `app-debug.apk` as an artifact named `kaam-debug-apk`.
- Uploads available build reports/diagnostics even if the build fails.

## Pinned toolchain
- Android Gradle Plugin: 9.4.0
- Gradle Wrapper: 9.6.0
- Compile SDK: Android API 37
- Build Tools: 36.0.0
- Kotlin Compose compiler plugin: 2.4.10
- Compose BOM: 2026.08.00

These versions were checked against the current official AGP compatibility and Compose setup documentation. See the official [AGP 9.4.0 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) and [Compose compiler setup guide](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).

## Important limitation
The workflow has been added and structurally validated but has **not been executed**. This session has no outbound DNS/network access, no installed Android SDK Platform 37, and no connected GitHub repository or runner credentials. Creating the workflow does not itself run it. A connected GitHub repository must receive this project and the workflow must run there before an APK can be claimed.

## Local execution
From the `ReminderApp` root:

```bash
./gradlew :app:assembleDebug --stacktrace --no-daemon --console=plain
```

On Windows, the provided `tools/build_debug_windows.ps1` performs a preflight and writes a complete build log under `build-diagnostics`.
