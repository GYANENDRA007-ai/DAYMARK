# KAAM v49 — Cloud Build Path

## Purpose
This checkpoint adds a reproducible GitHub Actions build job so the Android project can be compiled on a clean hosted Linux runner rather than depending on the ChatGPT execution container's unavailable network and missing Android SDK.

## What the workflow does
- Runs on a manual workflow dispatch, pushes to `main`/`master`, and pull requests.
- Uses JDK 21 and sets up Android SDK command-line tooling.
- Installs Android SDK Platform 37 and Build Tools 36.0.0.
- Runs the repository's offline source/asset/pure-JVM checks.
- Runs `./gradlew :app:assembleDebug`.
- If the build succeeds, publishes `app-debug.apk` as an artifact named `kaam-debug-apk`.
- Uploads available build reports/diagnostics even if the build fails.

## Important limitation
The workflow file is prepared and locally inspected, but it has **not been executed** because this session has no outbound DNS/network access and no GitHub repository credentials or connected GitHub runner. Creating the workflow does not itself run it. A GitHub repository must receive this project and the workflow must run there before an APK can be claimed.

## Local execution
From the `ReminderApp` root, the same build command is:

```bash
./gradlew :app:assembleDebug --stacktrace --no-daemon --console=plain
```

For Android SDK package setup, follow the official Android command-line tools guide: https://developer.android.com/tools.
For the Gradle Wrapper behavior, see: https://docs.gradle.org/current/userguide/gradle_wrapper.html.
